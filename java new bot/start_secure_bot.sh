#!/bin/bash

# Secure Bot Startup Script
# This script loads environment variables and starts the bot safely

echo "🚀 Starting Secure Stock Bot..."
echo "================================"

# Load environment variables from .env file
if [ -f .env ]; then
    echo "✅ Loading environment variables from .env file..."
    export $(cat .env | grep -v '^#' | xargs)
else
    echo "❌ .env file not found! Creating template..."
    echo "Please edit .env file with your credentials and run again."
    exit 1
fi

# Verify critical environment variables
echo "🔍 Verifying credentials..."

if [ -z "$TELEGRAM_BOT_TOKEN" ]; then
    echo "❌ TELEGRAM_BOT_TOKEN not set"
    exit 1
else
    echo "✅ Telegram Bot Token: ${TELEGRAM_BOT_TOKEN:0:10}..."
fi

if [ -z "$UPSTOX_ACCESS_TOKEN" ]; then
    echo "❌ UPSTOX_ACCESS_TOKEN not set"
    exit 1
else
    echo "✅ Upstox Access Token: ${UPSTOX_ACCESS_TOKEN:0:20}..."
fi

if [ -z "$UPSTOX_API_KEY" ]; then
    echo "❌ UPSTOX_API_KEY not set"
    exit 1
else
    echo "✅ Upstox API Key: ${UPSTOX_API_KEY:0:8}..."
fi

echo "🔧 Compiling bot..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed!"
    exit 1
fi

echo "✅ Compilation successful!"
echo "🚀 Starting bot with secure credentials..."
echo "📱 Bot will be available on Telegram shortly..."
echo "🛑 Press Ctrl+C to stop the bot"
echo ""

# Start the bot with all environment variables
java -cp "target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" com.stockbot.TelegramStockBot

echo ""
echo "🛑 Bot stopped."