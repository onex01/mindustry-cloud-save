const sqlite3 = require('sqlite3').verbose();
const path = require('path');

// Путь к файлу базы данных в корневой папке server-api
const dbPath = path.resolve(__dirname, '../database.sqlite');
const db = new sqlite3.Database(dbPath, (err) => {
    if (err) {
        console.error('Ошибка подключения к SQLite:', err.message);
    } else {
        console.log('Подключено к базе данных SQLite.');
        initTables();
    }
});

function initTables() {
    db.serialize(() => {
        // Таблица пользователей
        db.run(`CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )`);

        // Таблица сохранений
        db.run(`CREATE TABLE IF NOT EXISTS saves (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            name TEXT NOT NULL,
            filename TEXT NOT NULL,
            file_path TEXT,
            file_size INTEGER,
            folder TEXT DEFAULT '',
            device TEXT DEFAULT '',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
        )`);
        console.log('Таблицы базы данных проверены/созданы.');
        
        // Миграция: добавляем колонки folder и device если их нет
        db.run(`ALTER TABLE saves ADD COLUMN folder TEXT DEFAULT ''`, [], (err) => {
            if (err && !err.message.includes('duplicate column')) {
                console.error('Ошибка миграции folder:', err.message);
            }
        });
        db.run(`ALTER TABLE saves ADD COLUMN device TEXT DEFAULT ''`, [], (err) => {
            if (err && !err.message.includes('duplicate column')) {
                console.error('Ошибка миграции device:', err.message);
            }
        });
    });
}

module.exports = db;