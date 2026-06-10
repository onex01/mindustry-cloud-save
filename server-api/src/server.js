require('dotenv').config();
const express = require('express');
const multer = require('multer');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const path = require('path');
const fs = require('fs');
const db = require('./database');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Настройка Multer для загрузки файлов
const storage = multer.diskStorage({
    destination: function (req, file, cb) {
        const uploadDir = process.env.UPLOAD_DIR || './uploads';
        if (!fs.existsSync(uploadDir)) {
            fs.mkdirSync(uploadDir, { recursive: true });
        }
        cb(null, uploadDir);
    },
    filename: function (req, file, cb) {
        // Генерируем уникальное имя файла: timestamp_originalname
        const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
        cb(null, uniqueSuffix + '-' + file.originalname);
    }
});

const upload = multer({ 
    storage: storage,
    limits: { fileSize: 50 * 1024 * 1024 } // Лимит 50 МБ на файл сохранения
});

// --- Middleware для проверки JWT токена ---
const authenticateToken = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1]; // Формат: "Bearer TOKEN"

    if (!token) return res.status(401).json({ error: 'Доступ запрещен. Нет токена.' });

    jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
        if (err) return res.status(403).json({ error: 'Неверный или просроченный токен.' });
        req.user = user;
        next();
    });
};

// --- API ROUTES ---

// 1. Регистрация
app.post('/api/auth/register', async (req, res) => {
    const { username, password } = req.body;
    if (!username || !password) {
        return res.status(400).json({ error: 'Логин и пароль обязательны' });
    }

    try {
        const hashedPassword = await bcrypt.hash(password, 10);
        db.run(`INSERT INTO users (username, password_hash) VALUES (?, ?)`, [username, hashedPassword], function(err) {
            if (err) {
                if (err.message.includes('UNIQUE constraint failed')) {
                    return res.status(409).json({ error: 'Пользователь с таким именем уже существует' });
                }
                return res.status(500).json({ error: 'Ошибка сервера при регистрации' });
            }
            res.status(201).json({ message: 'Пользователь успешно зарегистрирован', userId: this.lastID });
        });
    } catch (error) {
        res.status(500).json({ error: 'Ошибка сервера' });
    }
});

// 2. Вход (Login)
app.post('/api/auth/login', (req, res) => {
    const { username, password } = req.body;

    db.get(`SELECT * FROM users WHERE username = ?`, [username], async (err, user) => {
        if (err || !user) {
            return res.status(401).json({ error: 'Неверный логин или пароль' });
        }

        const validPassword = await bcrypt.compare(password, user.password_hash);
        if (!validPassword) {
            return res.status(401).json({ error: 'Неверный логин или пароль' });
        }

        // Создаем JWT токен (действует 7 дней)
        const token = jwt.sign({ id: user.id, username: user.username }, process.env.JWT_SECRET, { expiresIn: '7d' });
        res.json({ message: 'Успешный вход', token, username: user.username });
    });
});

// 3. Загрузка сохранения (Требует авторизации)
app.post('/api/saves', authenticateToken, upload.single('saveFile'), (req, res) => {
    const userId = req.user.id;
    
    // Проверяем, какой формат данных пришёл
    if (req.file) {
        // Старый формат: multipart/form-data
        const { name } = req.body;
        const filename = req.file.filename;
        const fileSize = req.file.size;
        
        db.run(
            `INSERT INTO saves (user_id, name, filename, file_size) VALUES (?, ?, ?, ?)`,
            [userId, name || 'Без названия', filename, fileSize],
            function(err) {
                if (err) {
                    return res.status(500).json({ error: 'Ошибка при сохранении метаданных в БД' });
                }
                res.status(201).json({ 
                    message: 'Сохранение успешно загружено', 
                    saveId: this.lastID, 
                    filename: filename 
                });
            }
        );
    } else if (req.body.content) {
        // Новый формат: JSON с base64
        const { name, filename, content } = req.body;
        
        if (!content) {
            return res.status(400).json({ error: 'Поле content обязательно' });
        }
        
        try {
            // Декодируем base64
            const fileBuffer = Buffer.from(content, 'base64');
            
            // Генерируем уникальное имя файла
            const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
            const savedFilename = uniqueSuffix + '-' + (filename || 'save.msav');
            
            const uploadDir = process.env.UPLOAD_DIR || './uploads';
            if (!fs.existsSync(uploadDir)) {
                fs.mkdirSync(uploadDir, { recursive: true });
            }
            
            const filePath = path.join(uploadDir, savedFilename);
            
            // Сохраняем файл
            fs.writeFileSync(filePath, fileBuffer);
            
            db.run(
                `INSERT INTO saves (user_id, name, filename, file_size) VALUES (?, ?, ?, ?)`,
                [userId, name || 'Без названия', savedFilename, fileBuffer.length],
                function(err) {
                    if (err) {
                        // Удаляем файл, если не удалось записать в БД
                        fs.unlinkSync(filePath);
                        return res.status(500).json({ error: 'Ошибка при сохранении метаданных в БД' });
                    }
                    res.status(201).json({ 
                        message: 'Сохранение успешно загружено', 
                        saveId: this.lastID, 
                        filename: savedFilename 
                    });
                }
            );
        } catch (error) {
            return res.status(400).json({ error: 'Ошибка декодирования base64: ' + error.message });
        }
    } else {
        return res.status(400).json({ error: 'Файл сохранения не предоставлен' });
    }
});

// 4. Получение списка сохранений пользователя (Требует авторизации)
app.get('/api/saves', authenticateToken, (req, res) => {
    const userId = req.user.id;
    db.all(`SELECT id, name, file_size, created_at FROM saves WHERE user_id = ? ORDER BY created_at DESC`, [userId], (err, rows) => {
        if (err) {
            return res.status(500).json({ error: 'Ошибка при получении списка сохранений' });
        }
        res.json({ saves: rows });
    });
});

// 5. Скачивание конкретного сохранения (Требует авторизации)
app.get('/api/saves/:id/download', authenticateToken, (req, res) => {
    const saveId = req.params.id;
    const userId = req.user.id;

    db.get(`SELECT filename, name FROM saves WHERE id = ? AND user_id = ?`, [saveId, userId], (err, row) => {
        if (err || !row) {
            return res.status(404).json({ error: 'Сохранение не найдено или доступ запрещен' });
        }

        const filePath = path.join(__dirname, '../uploads', row.filename);
        
        // Проверяем, существует ли файл физически
        if (!fs.existsSync(filePath)) {
            return res.status(404).json({ error: 'Файл был удален с сервера' });
        }

        // Отправляем файл с правильным заголовком для скачивания
        res.download(filePath, `${row.name}.msav`, (err) => {
            if (err) {
                console.error('Ошибка при отправке файла:', err);
                res.status(500).json({ error: 'Ошибка при скачивании файла' });
            }
        });
    });
});

// Запуск сервера
app.listen(PORT, () => {
    console.log(`🚀 Сервер запущен на порту ${PORT}`);
    console.log(`📁 Файлы сохранений будут храниться в: ${path.resolve(__dirname, '../uploads')}`);
});