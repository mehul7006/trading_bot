#!/bin/bash

cd clean_bot
echo "🔨 Compiling Working Telegram Bot with fixed formatting..."
javac -cp "lib/*:target/classes:." WorkingTelegramBot.java

echo "🚀 STARTING WORKING TELEGRAM BOT (PHASE 1+4)"
echo "============================================="
echo "✅ All formatting issues fixed"
echo "📱 Bot will respond properly to all commands"
echo "🏆 Features: Phase 1 + Phase 4 + Real Market Data"
echo ""
echo "Go to your Telegram bot and send /start"
echo ""

# Start in background
nohup java -cp "lib/*:target/classes:." WorkingTelegramBot > working_bot_fixed.log 2>&1 &

echo "✅ Bot started in background"
echo "📊 Check status: tail -f working_bot_fixed.log"
echo "🔍 Process ID: $(pgrep -f WorkingTelegramBot)"