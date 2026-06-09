#!/bin/bash

# Скрипт для запуска сервера Mindustry Cloud Save API

# Цвета для вывода
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Переход в директорию сервера
cd "$(dirname "$0")/.."

echo -e "${GREEN}🚀 Запуск Mindustry Cloud Save API...${NC}"
echo ""

# Проверка наличия Node.js
if ! command -v node &> /dev/null; then
    echo -e "${RED}❌ Ошибка: Node.js не установлен!${NC}"
    echo "Установите Node.js: https://nodejs.org/"
    exit 1
fi

# Проверка версии Node.js (требуется 14+)
NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 14 ]; then
    echo -e "${RED}❌ Ошибка: Требуется Node.js версии 14 или выше${NC}"
    echo "Текущая версия: $(node -v)"
    exit 1
fi

echo -e "${BLUE}📦 Node.js версия: $(node -v)${NC}"

# Проверка наличия package.json
if [ ! -f "package.json" ]; then
    echo -e "${RED}❌ Ошибка: package.json не найден!${NC}"
    exit 1
fi

# Проверка и установка зависимостей
if [ ! -d "node_modules" ]; then
    echo -e "${YELLOW}⚠️  Зависимости не установлены.${NC}"
    echo -e "${YELLOW}📥 Выполняется npm install...${NC}"
    echo ""
    npm install
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Ошибка при установке зависимостей!${NC}"
        exit 1
    fi
    
    echo ""
    echo -e "${GREEN}✅ Зависимости успешно установлены${NC}"
else
    echo -e "${GREEN}✅ Зависимости уже установлены${NC}"
    
    # Опциональная проверка обновлений (можно раскомментировать)
    # echo -e "${YELLOW}🔍 Проверка обновлений зависимостей...${NC}"
    # npm outdated
fi

# Создание папки uploads если её нет
if [ ! -d "uploads" ]; then
    echo -e "${YELLOW}📁 Создание папки uploads...${NC}"
    mkdir -p uploads
fi

# Создание .env если его нет
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}📝 Создание .env файла...${NC}"
    cat > .env << EOF
PORT=3000
JWT_SECRET=change_this_to_a_random_secret_key_$(openssl rand -hex 32)
UPLOAD_DIR=./uploads
EOF
    echo -e "${GREEN}✅ .env файл создан с случайным JWT_SECRET${NC}"
fi

echo ""
echo -e "${GREEN}✅ Сервер запускается на порту 3000...${NC}"
echo -e "${YELLOW}💡 Для остановки нажмите Ctrl+C${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Запуск сервера
npm run dev