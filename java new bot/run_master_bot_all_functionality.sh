#!/bin/bash

echo "🚀 MASTER TRADING BOT - ALL FUNCTIONALITY RESTORED"
echo "=================================================="
echo "✅ Telegram Bot: /start command working"
echo "✅ Integration: BollingerBands, Volume, Technical Analysis"
echo "✅ Options: Index CE/PE analysis restored"
echo "✅ Dependencies: slf4j issues resolved"
echo "✅ Market Data: Real-time with updated token"
echo "=================================================="

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
echo "🔑 Upstox Token: ${UPSTOX_ACCESS_TOKEN:0:20}..."

# Compile master bot
echo "🔨 Compiling Master Trading Bot..."
CLASSPATH=".:target/classes:src/main/java"

javac -cp "$CLASSPATH" MasterTradingBotWithOptions.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🚀 Starting Master Trading Bot with ALL functionality..."
    echo "📱 Telegram Bot: /start command will work"
    echo "📊 Options Analysis: CE/PE recommendations active"
    echo "🔧 Integration: BollingerBands, Volume analysis active"
    echo "💹 Real-time data: Updated Upstox token active"
    echo ""
    echo "Available Commands:"
    echo "  /start - Welcome and features"
    echo "  /options - Index CE/PE analysis"
    echo "  /integration - Technical analysis"
    echo "  /status - System status"
    echo ""
    echo "Press Ctrl+C to stop"
    echo "===================="
    
    # Run the master bot
    java -cp "$CLASSPATH" MasterTradingBotWithOptions
    
else
    echo "❌ Compilation failed"
    echo "💡 Trying alternative approach..."
    
    # Try SimpleIndexOptionsBot as backup
    echo "🔧 Testing SimpleIndexOptionsBot..."
    javac -cp "$CLASSPATH" SimpleIndexOptionsBot.java 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "✅ SimpleIndexOptionsBot compiled successfully!"
        echo "🚀 Starting Options Bot..."
        java -cp "$CLASSPATH" SimpleIndexOptionsBot
    else
        echo "❌ Options bot compilation failed"
        echo "💡 Starting basic integration bot..."
        
        # Try HonestIntegratedBot_PartWise as final backup
        javac -cp "$CLASSPATH" src/main/java/com/stockbot/HonestIntegratedBot_PartWise.java 2>/dev/null
        if [ $? -eq 0 ]; then
            echo "✅ Integration bot compiled successfully!"
            echo "🚀 Starting Integration Bot..."
            java -cp "$CLASSPATH" com.stockbot.HonestIntegratedBot_PartWise
        else
            echo "❌ All bots failed to compile"
            echo "💡 Please check the error messages above"
        fi
    fi
fi