# Документация по сборке и настройке

## Сборка мода

### Команда

```bash
cd client-mod
./gradlew universalJar
```

### Результат

```
client-mod/build/libs/cloud-save-mod.jar
```

Универсальный JAR содержит:
- `.class` файлы — для Desktop (Java)
- `classes.dex` — для Android (Dalvik/ART)
- `mod.hjson` — метаданные мода
- `bundles/` — локализация (EN/RU)
- `icon.png` — иконка мода

### Доступные задачи Gradle

```bash
./gradlew universalJar    # Сборка универсального JAR
./gradlew compileJava     # Только компиляция
./gradlew clean           # Очистка
```

---

## Установка мода

### Linux
```bash
mkdir -p ~/.local/share/Mindustry/mods/
cp client-mod/build/libs/cloud-save-mod.jar ~/.local/share/Mindustry/mods/
```

### Windows
```
%APPDATA%\Mindustry\mods\
```

### macOS
```
~/Library/Application Support/Mindustry/mods/
```

### Android
```
Внутренняя память/Mindustry/mods/
```

---

## Установка сервера

### Требования

- Node.js 14+ (рекомендуется 18+)
- npm или yarn

### Установка зависимостей

```bash
cd server-api
npm install
```

### Конфигурация

Создайте файл `server-api/.env`:

```env
PORT=3000
JWT_SECRET=your_super_secret_key_here
UPLOAD_DIR=./uploads
```

### Запуск

```bash
# Разработка (автоперезапуск)
npm run dev

# Продакшен
npm start
```

### Установка как systemd сервис (Linux)

```bash
cd server-api
sudo bash scripts/install-service.sh
sudo systemctl start mindustry-cloud-save
sudo systemctl enable mindustry-cloud-save
```

### Управление сервисом

```bash
sudo systemctl status mindustry-cloud-save   # Статус
sudo systemctl restart mindustry-cloud-save  # Перезапуск
sudo systemctl stop mindustry-cloud-save     # Остановка
sudo journalctl -u mindustry-cloud-save -f   # Логи
```

---

## Настройка HTTPS (обязательно для Android)

### Вариант 1: Caddy (рекомендуется)

```bash
# Установка
sudo apt install caddy

# Конфигурация /etc/caddy/Caddyfile
```

```
your-domain.com {
    reverse_proxy localhost:3000
}
```

```bash
sudo systemctl restart caddy
```

### Вариант 2: Nginx + Let's Encrypt

```bash
# Установка
sudo apt install nginx certbot python3-certbot-nginx

# Конфигурация /etc/nginx/sites-available/cloudsave
```

```nginx
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name your-domain.com;

    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    location /api/ {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 100M;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/cloudsave /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
sudo certbot --nginx -d your-domain.com
```

---

## Структура данных на сервере

```
server-api/
├── uploads/
│   └── <user_id>/
│       └── <YYYY-MM-DD>/
│           └── <HH-mm-ss>.zip
├── database.sqlite
├── src/
│   ├── server.js
│   └── database.js
├── .env
└── package.json
```

---

## Debug режим

В моде есть кнопка "Debug: ON/OFF" в меню входа.

При включении логирует:
- URL запросов
- Токен авторизации
- Коды ответов
- Размер файлов
- Список файлов после распаковки
- Все ошибки с стеком вызовов

Логи Mindustry:
- **Linux**: `~/.local/share/Mindustry/logs/`
- **Android**: `Logcat` с тегом `CloudSave`

---

## Решение проблем

### "Cleartext HTTP traffic not permitted"
Android 9+ блокирует HTTP. Настройте HTTPS на сервере.

### Мод не загружается на Android
Убедитесь что используете `universalJar`, а не отдельный desktop/android JAR.

### Сохранения не скачиваются
1. Включите Debug режим
2. Проверьте логи на сервере: `journalctl -u mindustry-cloud-save -f`
3. Убедитесь что порт 443开放 в файрволе

### "Connection refused"
- Проверьте что сервер запущен: `systemctl status mindustry-cloud-save`
- Проверьте порт: `curl http://localhost:3000/api/saves`

---

## API Endpoints

| Метод | Путь | Описание | Auth |
|-------|------|----------|------|
| POST | `/api/auth/register` | Регистрация | Нет |
| POST | `/api/auth/login` | Вход (возвращает JWT) | Нет |
| POST | `/api/saves` | Загрузка сохранения | Да |
| GET | `/api/saves` | Список сохранений | Да |
| GET | `/api/saves/:id/download` | Скачивание | Да |
| PUT | `/api/saves/:id` | Переименование | Да |
| DELETE | `/api/saves/:id` | Удаление | Да |
| GET | `/api/folders` | Список папок | Да |
