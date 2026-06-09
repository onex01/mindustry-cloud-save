#!/bin/bash

# Скрипт для остановки сервера

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}🛑 Остановка сервера Mindustry Cloud Save API...${NC}"

# Поиск процесса node с server.js
PID=$(pgrep -f "node.*server.js")

if [ -z "$PID" ]; then
    echo -e "${RED}❌ Сервер не запущен${NC}"
    exit 0
fi

echo -e "${GREEN}Найден процесс с PID: $PID${NC}"
kill $PID

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Сервер успешно остановлен${NC}"
else
    echo -e "${RED}❌ Ошибка при остановке сервера${NC}"
    exit 1
fi