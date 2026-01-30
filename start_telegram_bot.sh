#!/bin/bash

echo "🤖 STARTING TELEGRAM TRADING BOT"
echo ""

# Stop any existing web server
echo "🧹 Stopping any existing servers..."
./stop_bot_server.sh 2>/dev/null || true
pkill -f "TradingBotServer" 2>/dev/null || true
sleep 2

cd clean_bot

echo "📦 Compiling Telegram Trading Bot..."
mvn compile -q

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi

echo ""

# Check if bot token is configured
if grep -q "YOUR_BOT_TOKEN_HERE" src/main/java/com/trading/bot/telegram/TelegramTradingBot.java; then
    echo "❌ Bot token not configured!"
    echo ""
    echo "🔧 Please run setup first:"
    echo "   ./setup_telegram_bot.sh"
    echo ""
    echo "📋 Or manually edit:"
    echo "   clean_bot/src/main/java/com/trading/bot/telegram/TelegramTradingBot.java"
    echo "   Replace YOUR_BOT_TOKEN_HERE with your actual token"
    exit 1
fi

echo "🚀 Starting Telegram Trading Bot..."
echo "📱 Bot Features:"
echo "   • Phase 1: Enhanced Technical Analysis + Basic ML"
echo "   • Phase 2: Multi-Timeframe + Advanced Indicators + Enhanced ML"
echo "   • Real-time market analysis for NIFTY, SENSEX, BANKNIFTY"
echo ""
echo "🤖 Available Commands:"
echo "   /start - Welcome message"
echo "   /analyze - Comprehensive analysis"
echo "   /phase1 - Technical analysis only"
echo "   /phase2 - Advanced analysis only"
echo "   /nifty - NIFTY analysis"
echo "   /sensex - SENSEX analysis"  
echo "   /banknifty - BANKNIFTY analysis"
echo "   /status - Bot status"
echo "   /help - All commands"
echo ""
echo "🔄 Bot is now running and listening for Telegram messages..."
echo "🛑 To stop: Press Ctrl+C or run ./stop_telegram_bot.sh"
echo ""

# Start the Telegram bot
java -cp "target/classes:lib/*" com.trading.bot.telegram.TelegramTradingBot