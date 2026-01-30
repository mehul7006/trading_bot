#!/bin/bash

# Start Core Bot with Real Data
echo "🚀 STARTING CORE BOT WITH REAL DATA"
echo "===================================="

# Load environment variables
if [ -f .env ]; then
    echo "✅ Loading environment variables..."
    export $(cat .env | grep -v '^#' | xargs)
else
    echo "❌ .env file not found!"
    exit 1
fi

# Verify critical variables
if [ -z "$TELEGRAM_BOT_TOKEN" ] || [ -z "$UPSTOX_ACCESS_TOKEN" ]; then
    echo "❌ Missing critical environment variables"
    exit 1
fi

echo "✅ Environment variables loaded"
echo "🔑 Bot Token: ${TELEGRAM_BOT_TOKEN:0:10}..."
echo "🔑 Access Token: ${UPSTOX_ACCESS_TOKEN:0:20}..."

# Compile core bot if needed
if [ ! -d "target/core-classes" ]; then
    echo "🔧 Compiling core bot first..."
    ./compile_core_bot.sh
fi

echo "🚀 Starting core bot..."
echo "📱 Bot will be available on Telegram"
echo "🛑 Press Ctrl+C to stop"
echo ""

# Start the core bot
java -cp "target/core-classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" \
    com.stockbot.TelegramStockBot

echo ""
echo "🛑 Core bot stopped."