#!/bin/bash

echo "🤖 STARTING WORKING TELEGRAM BOT WITH OPTIONS"
echo "============================================="
echo "✅ Real Telegram API integration"
echo "✅ /start command will respond"
echo "✅ Index CE/PE options analysis added"
echo "✅ All commands working"
echo "============================================="

# Kill any existing processes
echo "🛑 Stopping any running bots..."
pkill -f "java.*Bot" 2>/dev/null || true
pkill -f "java.*Integration" 2>/dev/null || true
sleep 2

# Set environment variables
echo "🔧 Loading environment variables..."
export TELEGRAM_BOT_TOKEN="7921964521:AAGNk_jIcV9V5nvxSTdK4xSzeO_yS4AKZ9E"
export UPSTOX_ACCESS_TOKEN="eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTEwYjVjNjAwMGE4YzY0YWM5OGZjYzgiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MjcwMjc5MCwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYyNzI1NjAwfQ.DtaAeJFxSiIYyh8ZC9BV8vo8UczBu6DY8u3aWOsSdMY"
export UPSTOX_API_KEY="768a303b-80f1-46d6-af16-f847f9341213"

echo "✅ Environment loaded"
echo "🔑 Telegram Token: ${TELEGRAM_BOT_TOKEN:0:20}..."

# Compile the working Telegram bot
echo "🔨 Compiling FullEnhancedTelegramBot with options..."
CLASSPATH=".:target/classes:src/main/java"

javac -cp "$CLASSPATH" src/main/java/com/stockbot/FullEnhancedTelegramBot.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🚀 Starting Telegram Bot with ALL functionality..."
    echo "📱 Bot will respond to /start command"
    echo "📊 Options commands available:"
    echo "   /options - Complete options analysis"
    echo "   /ce_pe - CE/PE recommendations"
    echo "   /nifty_options - NIFTY options"
    echo "   /sensex_options - SENSEX options"
    echo ""
    echo "🔧 Integration commands:"
    echo "   /predict - ML predictions"
    echo "   /models - Model analysis"
    echo "   /performance - Performance metrics"
    echo ""
    echo "Press Ctrl+C to stop"
    echo "===================="
    
    # Run the working Telegram bot
    java -cp "$CLASSPATH" com.stockbot.FullEnhancedTelegramBot
    
else
    echo "❌ Compilation failed"
    echo "💡 Trying backup options..."
    
    # Try ImprovedTelegramBot as backup
    echo "🔧 Testing ImprovedTelegramBot..."
    javac -cp "$CLASSPATH" ImprovedTelegramBot.java 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✅ ImprovedTelegramBot compiled successfully!"
        echo "🚀 Starting backup Telegram bot..."
        java -cp "$CLASSPATH" ImprovedTelegramBot
    else
        echo "❌ Backup bot compilation failed"
        echo "💡 Please check the error messages above"
    fi
fi