#!/bin/bash

# Скрипт для удаления systemd сервиса

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Проверка прав root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}❌ Этот скрипт нужно запускать с правами root (sudo)${NC}"
    echo "Использование: sudo ./uninstall-service.sh"
    exit 1
fi

SERVICE_NAME="mindustry-cloud-save"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

echo -e "${YELLOW}🗑️  Удаление systemd сервиса $SERVICE_NAME...${NC}"

# Остановка сервиса
if systemctl is-active --quiet "$SERVICE_NAME"; then
    echo -e "${YELLOW}🛑 Остановка сервиса...${NC}"
    systemctl stop "$SERVICE_NAME"
fi

# Отключение автозапуска
if systemctl is-enabled --quiet "$SERVICE_NAME"; then
    echo -e "${YELLOW}🔧 Отключение автозапуска...${NC}"
    systemctl disable "$SERVICE_NAME"
fi

# Удаление файла сервиса
if [ -f "$SERVICE_FILE" ]; then
    echo -e "${YELLOW}🗑️  Удаление файла сервиса...${NC}"
    rm "$SERVICE_FILE"
    systemctl daemon-reload
fi

echo -e "${GREEN}✅ Сервис успешно удален${NC}"