# Настройка HTTPS для Mindustry Cloud Save API

## Вариант 1: Caddy (рекомендуется — автоматический SSL)

### 1. Установка Caddy
```bash
# Ubuntu/Debian
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install caddy
```

### 2. Конфигурация Caddy
```bash
sudo nano /etc/caddy/Caddyfile
```

Содержимое:
```
onex01.ru {
    reverse_proxy localhost:3000
}
```

### 3. Запуск
```bash
sudo systemctl restart caddy
sudo systemctl enable caddy
```

Caddy автоматически получит SSL сертификат от Let's Encrypt.

---

## Вариант 2: Nginx + Certbot

### 1. Установка
```bash
sudo apt update
sudo apt install nginx certbot python3-certbot-nginx -y
```

### 2. Конфигурация Nginx
```bash
sudo nano /etc/nginx/sites-available/cloudsave
```

```nginx
server {
    listen 80;
    server_name onex01.ru;

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 100M;
    }
}
```

### 3. Активация и SSL
```bash
sudo ln -s /etc/nginx/sites-available/cloudsave /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx

# Получение SSL сертификата
sudo certbot --nginx -d onex01.ru
sudo systemctl restart nginx
```

---

## Обновление мода

После настройки HTTPS, измените URL сервера в моде:
```
https://onex01.ru
```
(без порта 3000, Caddy/Nginx слушает порт 443)
