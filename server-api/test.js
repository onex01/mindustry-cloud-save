const express = require('express');
const app = express();

console.log('🚀 Запуск тестового сервера...');

app.use(express.json());

app.get('/', (req, res) => {
    console.log('GET / - получен запрос');
    res.json({ status: 'ok', message: 'Сервер работает!' });
});

app.post('/api/auth/login', (req, res) => {
    console.log('POST /api/auth/login - получен запрос:', req.body);
    res.json({ 
        message: 'Успешный вход', 
        token: 'test-token-12345',
        username: req.body.username 
    });
});

app.post('/api/auth/register', (req, res) => {
    console.log('POST /api/auth/register - получен запрос:', req.body);
    res.json({ message: 'Пользователь зарегистрирован', userId: 1 });
});

app.get('/api/saves', (req, res) => {
    console.log('GET /api/saves - получен запрос');
    res.json({ saves: [] });
});

const PORT = 3000;
app.listen(PORT, () => {
    console.log(`✅ Тестовый сервер запущен на http://localhost:${PORT}`);
    console.log('Ожидание запросов...');
});

// Обработка завершения
process.on('SIGINT', () => {
    console.log('\n🛑 Сервер остановлен');
    process.exit(0);
});

process.on('uncaughtException', (err) => {
    console.error('❌ Необрабатываемая ошибка:', err);
});