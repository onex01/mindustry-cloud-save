# Mindustry Cloud Save Sync

Мод для игры [Mindustry](https://github.com/Anuken/Mindustry), позволяющий сохранять игровые сохранения в облако и продолжать игру с любого устройства.

## Возможности

- Загрузка и скачивание сохранений с сервера
- Переименование сохранений
- Определение устройства (Android/PC/Linux/macOS)
- Автоматическое определение HTTP/HTTPS
- Debug режим для диагностики проблем

## Структура проекта

```
mindustry-cloud-save/
├── client-mod/          # Mindustry мод (Java/Gradle)
├── server-api/          # API сервер (Node.js/Express)
├── web-panel/           # Веб-панель (в разработке)
├── docs/                # Документация
│   └── ui-preview.html  # Web-превью UI
├── icon.png             # Иконка мода
└── README.md
```

## Сборка мода

```bash
cd client-mod
./gradlew universalJar
```

Результат: `client-mod/build/libs/cloud-save-mod.jar`

## Запуск сервера

```bash
cd server-api
npm install
npm start
```

Сервер запускается на порту 3000.

## Установка мода

1. Скопируйте `cloud-save-mod.jar` в папку модов Mindustry:
   - **Linux**: `~/.local/share/Mindustry/mods/`
   - **Windows**: `%APPDATA%/Mindustry/mods/`
   - **macOS**: `~/Library/Application Support/Mindustry/mods/`
   - **Android**: Внутренняя память → `Mindustry/mods/`

2. Перезапустите Mindustry

3. В главном меню нажмите "Cloud Saves"

## Настройка сервера

### Требования

- Node.js 14+
- SQLite3
- Статический IP или домен

### Установка

```bash
cd server-api
npm install
```

### Конфигурация

Создайте файл `.env`:
```
PORT=3000
JWT_SECRET=your_secret_key
UPLOAD_DIR=./uploads
```

### Запуск как сервис (Linux)

```bash
sudo bash scripts/install-service.sh
sudo systemctl start mindustry-cloud-save
```

## HTTPS настройка

Для работы на Android required HTTPS. Используйте nginx или caddy как reverse proxy:

### Nginx

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;

    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    location /api/ {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        client_max_body_size 100M;
    }
}
```

### Caddy

```
your-domain.com {
    reverse_proxy localhost:3000
}
```

## Использование

1. Введите адрес сервера (например `your-domain.com`)
2. Зарегистрируйтесь или войдите
3. Нажмите "Загрузить" для отправки сохранений на сервер
4. На другом устройстве войдите и нажмите "Скачать"
5. Перезапустите игру для применения сохранения

## API Endpoints

| Метод | Описание |
|-------|----------|
| `POST /api/auth/register` | Регистрация |
| `POST /api/auth/login` | Вход |
| `POST /api/saves` | Загрузка сохранения |
| `GET /api/saves` | Список сохранений |
| `GET /api/saves/:id/download` | Скачивание |
| `PUT /api/saves/:id` | Переименование |
| `DELETE /api/saves/:id` | Удаление |
| `GET /api/folders` | Список папок |

## Лицензия

MIT
