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
app.use(express.json({ limit: '100mb' }));
app.use(express.urlencoded({ extended: true, limit: '100mb' }));

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
    const token = authHeader && authHeader.split(' ')[1];
    
    console.log('[AUTH] Проверка токена:', token ? 'присутствует' : 'отсутствует');
    console.log('[AUTH] Заголовок Authorization:', authHeader);

    if (!token) {
        console.log('[AUTH] Токен отсутствует, пропускаем для теста');
        req.user = { id: 1, username: 'test' }; // Для теста
        return next();
    }

    // Для теста принимаем любой токен
    if (token === 'test-token-12345') {
        req.user = { id: 1, username: 'test' };
        console.log('[AUTH] Тестовый токен принят');
        return next();
    }

    // Проверяем JWT
    jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
        if (err) {
            console.log('[AUTH] Ошибка проверки JWT:', err.message);
            // Для теста все равно пропускаем
            req.user = { id: 1, username: 'test' };
            return next();
        }
        req.user = user;
        console.log('[AUTH] JWT токен принят для пользователя:', user.username);
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
    
    // Генерируем структуру каталогов: uploads/<user_id>/<folder>/<YYYY-MM-DD>/<HH-mm-ss>.zip
    const now = new Date();
    const dateStr = now.toISOString().slice(0, 10);
    const timeStr = now.toTimeString().slice(0, 8).replace(/:/g, '-');
    
    const folder = req.body.folder || '';
    const uploadDir = process.env.UPLOAD_DIR || './uploads';
    const userDir = path.join(uploadDir, String(userId));
    const folderDir = folder ? path.join(userDir, folder) : userDir;
    const dateDir = path.join(folderDir, dateStr);
    
    if (!fs.existsSync(dateDir)) {
        fs.mkdirSync(dateDir, { recursive: true });
    }
    
    const savedFilename = `${timeStr}.zip`;
    const relativePath = folder 
        ? `${userId}/${folder}/${dateStr}/${savedFilename}`
        : `${userId}/${dateStr}/${savedFilename}`;
    
    if (req.file) {
        const { name } = req.body;
        const device = req.body.device || '';
        const filePath = path.join(dateDir, savedFilename);
        
        fs.renameSync(req.file.path, filePath);
        
        db.run(
            `INSERT INTO saves (user_id, name, filename, file_path, file_size, folder, device) VALUES (?, ?, ?, ?, ?, ?, ?)`,
            [userId, name || 'Без названия', savedFilename, relativePath, req.file.size, folder, device],
            function(err) {
                if (err) {
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
    } else if (req.body.content) {
        const { name, content } = req.body;
        const device = req.body.device || '';
        
        if (!content) {
            return res.status(400).json({ error: 'Поле content обязательно' });
        }
        
        try {
            const fileBuffer = Buffer.from(content, 'base64');
            const filePath = path.join(dateDir, savedFilename);
            
            fs.writeFileSync(filePath, fileBuffer);
            
            db.run(
                `INSERT INTO saves (user_id, name, filename, file_path, file_size, folder, device) VALUES (?, ?, ?, ?, ?, ?, ?)`,
                [userId, name || 'Без названия', savedFilename, relativePath, fileBuffer.length, folder, device],
                function(err) {
                    if (err) {
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

// 4. Получение списка папок пользователя
app.get('/api/folders', authenticateToken, (req, res) => {
    const userId = req.user.id;
    db.all(`SELECT DISTINCT folder FROM saves WHERE user_id = ? ORDER BY folder`, [userId], (err, rows) => {
        if (err) {
            return res.status(500).json({ error: 'Ошибка при получении списка папок' });
        }
        const folders = rows.map(r => r.folder || '').filter(f => f !== '');
        res.json({ folders });
    });
});

// 5. Получение списка сохранений пользователя (с фильтрацией по папке)
app.get('/api/saves', authenticateToken, (req, res) => {
    const userId = req.user.id;
    const folder = req.query.folder || '';
    
    let query = 'SELECT id, name, file_size, folder, device, created_at FROM saves WHERE user_id = ?';
    let params = [userId];
    
    if (folder) {
        query += ' AND folder = ?';
        params.push(folder);
    } else {
        query += " AND (folder = '' OR folder IS NULL)";
    }
    
    query += ' ORDER BY created_at DESC';
    
    db.all(query, params, (err, rows) => {
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

    db.get(`SELECT filename, name, file_path FROM saves WHERE id = ? AND user_id = ?`, [saveId, userId], (err, row) => {
        if (err || !row) {
            return res.status(404).json({ error: 'Сохранение не найдено или доступ запрещен' });
        }

        // Используем file_path если есть, иначе fallback на старую структуру
        let filePath;
        if (row.file_path) {
            filePath = path.join(process.env.UPLOAD_DIR || './uploads', row.file_path);
        } else {
            filePath = path.join(__dirname, '../uploads', row.filename);
        }
        
        if (!fs.existsSync(filePath)) {
            return res.status(404).json({ error: 'Файл был удален с сервера' });
        }

        res.download(filePath, `${row.name}.zip`, (err) => {
            if (err) {
                console.error('Ошибка при отправке файла:', err);
                res.status(500).json({ error: 'Ошибка при скачивании файла' });
            }
        });
    });
});

// 6. Переименование сохранения (Требует авторизации)
app.put('/api/saves/:id', authenticateToken, (req, res) => {
    const saveId = req.params.id;
    const userId = req.user.id;
    const { name } = req.body;
    
    if (!name || name.trim().length === 0) {
        return res.status(400).json({ error: 'Имя обязательно' });
    }
    
    db.run(`UPDATE saves SET name = ? WHERE id = ? AND user_id = ?`, [name.trim(), saveId, userId], function(err) {
        if (err) {
            return res.status(500).json({ error: 'Ошибка при переименовании' });
        }
        if (this.changes === 0) {
            return res.status(404).json({ error: 'Сохранение не найдено' });
        }
        res.json({ message: 'Сохранение переименовано' });
    });
});

// 7. Удаление сохранения (Требует авторизации)
app.delete('/api/saves/:id', authenticateToken, (req, res) => {
    const saveId = req.params.id;
    const userId = req.user.id;

    db.get(`SELECT filename, file_path FROM saves WHERE id = ? AND user_id = ?`, [saveId, userId], (err, row) => {
        if (err || !row) {
            return res.status(404).json({ error: 'Сохранение не найдено или доступ запрещен' });
        }

        // Удаляем физический файл
        let filePath;
        if (row.file_path) {
            filePath = path.join(process.env.UPLOAD_DIR || './uploads', row.file_path);
        } else {
            filePath = path.join(__dirname, '../uploads', row.filename);
        }

        if (fs.existsSync(filePath)) {
            fs.unlinkSync(filePath);
        }

        // Удаляем запись из БД
        db.run(`DELETE FROM saves WHERE id = ? AND user_id = ?`, [saveId, userId], function(err) {
            if (err) {
                return res.status(500).json({ error: 'Ошибка при удалении сохранения' });
            }
            res.json({ message: 'Сохранение успешно удалено' });
        });
    });
});

// Запуск сервера
app.listen(PORT, () => {
    console.log(`🚀 Сервер запущен на порту ${PORT}`);
    console.log(`📁 Файлы сохранений будут храниться в: ${path.resolve(__dirname, '../uploads')}`);
});