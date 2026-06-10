const express = require('express');
const fs = require('fs');
const path = require('path');
const app = express();

console.log('🚀 Запуск тестового сервера...');

app.use(express.json({ limit: '100mb' }));

// Базовая папка для загрузок
const uploadsDir = path.join(__dirname, 'uploads');
if (!fs.existsSync(uploadsDir)) {
    fs.mkdirSync(uploadsDir, { recursive: true });
}

// Простое хранилище пользователей (для теста)
const users = new Map();
let nextUserId = 1;

app.use((req, res, next) => {
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
    next();
});

app.get('/', (req, res) => {
    res.json({ status: 'ok', message: 'Сервер работает!' });
});

// Регистрация
app.post('/api/auth/register', (req, res) => {
    const { username, password } = req.body;
    
    if (!username || !password) {
        return res.status(400).json({ error: 'Логин и пароль обязательны' });
    }
    
    if (users.has(username)) {
        return res.status(409).json({ error: 'Пользователь уже существует' });
    }
    
    const userId = nextUserId++;
    const token = `token-${userId}-${Date.now()}`;
    
    users.set(username, { id: userId, password, token });
    
    // Создаем папку для пользователя
    const userDir = path.join(uploadsDir, token);
    if (!fs.existsSync(userDir)) {
        fs.mkdirSync(userDir, { recursive: true });
    }
    
    console.log(`✅ Пользователь зарегистрирован: ${username}, токен: ${token}`);
    
    res.json({ 
        message: 'Пользователь зарегистрирован', 
        userId: userId,
        token: token
    });
});

// Вход
app.post('/api/auth/login', (req, res) => {
    const { username, password } = req.body;
    
    const user = users.get(username);
    
    if (!user || user.password !== password) {
        return res.status(401).json({ error: 'Неверный логин или пароль' });
    }
    
    console.log(`✅ Пользователь вошел: ${username}`);
    
    res.json({ 
        message: 'Успешный вход', 
        token: user.token,
        username: username 
    });
});

// Список сохранений пользователя
app.get('/api/saves', (req, res) => {
    const token = req.headers['authorization']?.replace('Bearer ', '');
    
    if (!token) {
        return res.status(401).json({ error: 'Токен не предоставлен' });
    }
    
    const userDir = path.join(uploadsDir, token);
    
    if (!fs.existsSync(userDir)) {
        return res.json({ saves: [] });
    }
    
    const saves = [];
    const folders = fs.readdirSync(userDir);
    
    folders.forEach(folder => {
        const folderPath = path.join(userDir, folder);
        if (fs.statSync(folderPath).isDirectory()) {
            const files = fs.readdirSync(folderPath);
            const zipFile = files.find(f => f.endsWith('.zip'));
            
            if (zipFile) {
                const stats = fs.statSync(path.join(folderPath, zipFile));
                saves.push({
                    id: folder,
                    name: folder,
                    file_size: stats.size,
                    created_at: stats.mtime.toISOString()
                });
            }
        }
    });
    
    // Сортируем по дате (новые сначала)
    saves.sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
    
    console.log(`📋 Найдено сохранений: ${saves.length}`);
    res.json({ saves });
});

// Загрузка сохранения
app.post('/api/saves', (req, res) => {
    const token = req.headers['authorization']?.replace('Bearer ', '');
    
    if (!token) {
        return res.status(401).json({ error: 'Токен не предоставлен' });
    }
    
    if (!req.body.content) {
        return res.status(400).json({ error: 'Поле content обязательно' });
    }
    
    try {
        const { name, content } = req.body;
        
        // Создаем уникальную папку с датой и временем
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const saveFolder = `${timestamp}-${name || 'save'}`;
        const saveDir = path.join(uploadsDir, token, saveFolder);
        
        if (!fs.existsSync(saveDir)) {
            fs.mkdirSync(saveDir, { recursive: true });
        }
        
        // Декодируем base64
        const fileBuffer = Buffer.from(content, 'base64');
        const filePath = path.join(saveDir, 'save.zip');
        
        fs.writeFileSync(filePath, fileBuffer);
        
        console.log(`✅ Сохранение загружено: ${saveFolder}, размер: ${fileBuffer.length} байт`);
        
        res.status(201).json({ 
            message: 'Сохранение загружено', 
            saveId: saveFolder,
            filename: 'save.zip' 
        });
        
    } catch (error) {
        console.error('❌ Ошибка загрузки:', error);
        res.status(400).json({ error: 'Ошибка: ' + error.message });
    }
});

// Скачивание сохранения
app.get('/api/saves/:id/download', (req, res) => {
    const token = req.headers['authorization']?.replace('Bearer ', '');
    const saveId = req.params.id;
    
    if (!token) {
        return res.status(401).json({ error: 'Токен не предоставлен' });
    }
    
    const filePath = path.join(uploadsDir, token, saveId, 'save.zip');
    
    if (!fs.existsSync(filePath)) {
        return res.status(404).json({ error: 'Сохранение не найдено' });
    }
    
    console.log(`📥 Отправка файла: ${filePath}`);
    res.download(filePath, `${saveId}.zip`);
});

// Удаление сохранения
app.delete('/api/saves/:id', (req, res) => {
    const token = req.headers['authorization']?.replace('Bearer ', '');
    const saveId = req.params.id;
    
    if (!token) {
        return res.status(401).json({ error: 'Токен не предоставлен' });
    }
    
    const saveDir = path.join(uploadsDir, token, saveId);
    
    if (!fs.existsSync(saveDir)) {
        return res.status(404).json({ error: 'Сохранение не найдено' });
    }
    
    // Удаляем папку рекурсивно
    fs.rmSync(saveDir, { recursive: true, force: true });
    
    console.log(`🗑️ Сохранение удалено: ${saveId}`);
    res.json({ message: 'Сохранение удалено' });
});

const PORT = 3000;
app.listen(PORT, () => {
    console.log(`✅ Тестовый сервер запущен на http://localhost:${PORT}`);
    console.log(`📁 Файлы сохраняются в: ${uploadsDir}`);
    console.log('Ожидание запросов...');
});

process.on('SIGINT', () => {
    console.log('\n🛑 Сервер остановлен');
    process.exit(0);
});

process.on('uncaughtException', (err) => {
    console.error('❌ Необрабатываемая ошибка:', err);
});