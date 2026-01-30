#!/bin/bash

echo "🛑 STOPPING TRADING BOT SERVER"

# Find and kill all trading bot processes
echo "🔍 Finding trading bot processes..."
PIDS=$(ps aux | grep "trading.bot.server.TradingBotServer" | grep -v grep | awk '{print $2}')

if [ -z "$PIDS" ]; then
    echo "ℹ️  No trading bot server processes found"
else
    echo "🛑 Stopping trading bot server processes: $PIDS"
    for PID in $PIDS; do
        kill -15 $PID 2>/dev/null
        echo "   Stopped process $PID"
    done
    
    # Wait a bit for graceful shutdown
    sleep 3
    
    # Force kill if still running
    REMAINING=$(ps aux | grep "trading.bot.server.TradingBotServer" | grep -v grep | awk '{print $2}')
    if [ ! -z "$REMAINING" ]; then
        echo "🔥 Force stopping remaining processes..."
        for PID in $REMAINING; do
            kill -9 $PID 2>/dev/null
            echo "   Force stopped process $PID"
        done
    fi
fi

# Also kill any other trading bot processes
pkill -f "trading.bot" 2>/dev/null || true

echo "✅ All trading bot processes stopped"
echo "🚀 You can restart with: ./start_bot_server.sh"