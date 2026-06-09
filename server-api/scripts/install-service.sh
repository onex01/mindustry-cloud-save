#!/bin/bash

# Скрипт для установки systemd сервиса

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Проверка прав root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}❌ Этот скрипт нужно запускать с правами root (sudo)${NC}"
    echo "Использование: sudo ./install-service.sh"
    exit 1
fi

# Получение абсолютного пути к директории сервера
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SERVER_DIR="$(dirname "$SCRIPT_DIR")"
SERVICE_NAME="mindustry-cloud-save"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

# Получение текущего пользователя (не root)
REAL_USER="${SUDO_USER:-$USER}"

echo -e "${GREEN}🔧 Установка systemd сервиса для Mindustry Cloud Save API...${NC}"
echo -e "${YELLOW}Директория сервера: $SERVER_DIR${NC}"
echo -e "${YELLOW}Пользователь: $REAL_USER${NC}"

# Создание systemd unit файла
cat > "$SERVICE_FILE" << EOF
[Unit]
Description=Mindustry Cloud Save API Server
After=network.target

[Service]
Type=simple
User=$REAL_USER
WorkingDirectory=$SERVER_DIR
ExecStart=/usr/bin/node $SERVER_DIR/src/server.js
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=$SERVICE_NAME

# Переменные окружения
Environment=NODE_ENV=production
EnvironmentFile=$SERVER_DIR/.env

# Безопасность
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

echo -e "${GREEN}✅ Service файл создан: $SERVICE_FILE${NC}"

# Перезагрузка systemd
echo -e "${YELLOW}🔄 Перезагрузка systemd...${NC}"
systemctl daemon-reload

# Включение автозапуска
echo -e "${YELLOW}🔧 Включение автозапуска...${NC}"
systemctl enable "$SERVICE_NAME"

# Запуск сервиса
echo -e "${YELLOW}🚀 Запуск сервиса...${NC}"
systemctl start "$SERVICE_NAME"

# Проверка статуса
sleep 2
if systemctl is-active --quiet "$SERVICE_NAME"; then
    echo -e "${GREEN}✅ Сервис успешно установлен и запущен!${NC}"
    echo ""
    echo "Полезные команды:"
    echo "  systemctl status $SERVICE_NAME    - Проверить статус"
    echo "  systemctl stop $SERVICE_NAME      - Остановить"
    echo "  systemctl restart $SERVICE_NAME   - Перезапустить"
    echo "  journalctl -u $SERVICE_NAME -f    - Просмотр логов"
else
    echo -e "${RED}❌ Ошибка при запуске сервиса${NC}"
    echo "Проверьте логи: journalctl -u $SERVICE_NAME -n 50"
    exit 1
fi