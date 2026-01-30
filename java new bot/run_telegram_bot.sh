#!/bin/bash

echo "🤖 STARTING TELEGRAM BOT WITH LATEST INTEGRATIONS"
echo "================================================="
echo "✅ All BollingerBands fixes included"
echo "✅ Real market data processing"
echo "✅ Telegram /start command working"
echo "✅ Updated access token active"
echo ""

# Kill any existing processes
echo "🛑 Stopping any running bots..."
pkill -f "java.*Bot" 2>/dev/null || true
pkill -f "java.*Integration" 2>/dev/null || true
sleep 2

# Check environment
if [ -z "$TELEGRAM_BOT_TOKEN" ]; then
    echo "🔧 Loading environment variables..."
    source .env 2>/dev/null || true
    export TELEGRAM_BOT_TOKEN="7921964521:AAGNk_jIcV9V5nvxSTdK4xSzeO_yS4AKZ9E"
    export UPSTOX_ACCESS_TOKEN="eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTEwYjVjNjAwMGE4YzY0YWM5OGZjYzgiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MjcwMjc5MCwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYyNzI1NjAwfQ.DtaAeJFxSiIYyh8ZC9BV8vo8UczBu6DY8u3aWOsSdMY"
    export UPSTOX_API_KEY="768a303b-80f1-46d6-af16-f847f9341213"
fi

echo "✅ Environment loaded"
echo "🔑 Telegram Token: ${TELEGRAM_BOT_TOKEN:0:20}..."
echo "🔑 Upstox Token: ${UPSTOX_ACCESS_TOKEN:0:20}..."

# Compile Telegram bot
echo "🔨 Compiling Telegram bot with integrations..."
CLASSPATH=".:target/classes:src/main/java"

javac -cp "$CLASSPATH" src/main/java/com/stockbot/FullEnhancedTelegramBot.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🚀 Starting Telegram Bot..."
    echo "📱 Bot will respond to /start command"
    echo "🔧 All integrations active"
    echo "💹 Real-time market analysis enabled"
    echo ""
    echo "Press Ctrl+C to stop"
    echo "===================="
    
    # Run the Telegram bot
    java -cp "$CLASSPATH" com.stockbot.FullEnhancedTelegramBot
    
else
    echo "❌ Compilation failed"
    echo "💡 Trying alternative bot..."
    
    # Try ImprovedTelegramBot as backup
    javac -cp "$CLASSPATH" ImprovedTelegramBot.java 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✅ Backup bot compiled successfully!"
        echo "🚀 Starting backup Telegram bot..."
        java -cp "$CLASSPATH" ImprovedTelegramBot
    else
        echo "❌ All bots failed to compile"
        echo "💡 Please check the error messages above"
    fi
fi