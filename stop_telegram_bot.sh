#!/bin/bash

echo "🛑 STOPPING TELEGRAM TRADING BOT"

# Find and kill Telegram bot processes
echo "🔍 Finding Telegram bot processes..."
PIDS=$(ps aux | grep "TelegramTradingBot" | grep -v grep | awk '{print $2}')

if [ -z "$PIDS" ]; then
    echo "ℹ️  No Telegram bot processes found"
else
    echo "🛑 Stopping Telegram bot processes: $PIDS"
    for PID in $PIDS; do
        kill -15 $PID 2>/dev/null
        echo "   Stopped process $PID"
    done
    
    # Wait for graceful shutdown
    sleep 3
    
    # Force kill if still running
    REMAINING=$(ps aux | grep "TelegramTradingBot" | grep -v grep | awk '{print $2}')
    if [ ! -z "$REMAINING" ]; then
        echo "🔥 Force stopping remaining processes..."
        for PID in $REMAINING; do
            kill -9 $PID 2>/dev/null
            echo "   Force stopped process $PID"
        done
    fi
fi

# Also stop any other trading bot processes
pkill -f "trading.bot" 2>/dev/null || true

echo "✅ All Telegram bot processes stopped"
echo "🚀 You can restart with: ./start_telegram_bot.sh"