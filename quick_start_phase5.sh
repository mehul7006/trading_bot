#!/bin/bash
# Quick Start Fresh Phase 5 AI Trading Bot

echo "🚀 QUICK START - FRESH PHASE 5 AI BOT"
echo "====================================="

cd clean_bot

echo "🔧 Final compilation check..."
javac -cp "lib/*:classes" -d classes src/main/java/com/trading/bot/launcher/Phase5BotLauncher.java

if [ $? -eq 0 ]; then
    echo "✅ Ready to launch!"
    echo ""
    echo "🤖 Starting Fresh Phase 5 AI Trading Bot..."
    echo "=========================================="
    java -cp "lib/*:classes" com.trading.bot.launcher.Phase5BotLauncher
else
    echo "❌ Quick compilation check failed"
    echo "🔍 Available components:"
    find classes -name "*Phase5*" 2>/dev/null | head -5
fi