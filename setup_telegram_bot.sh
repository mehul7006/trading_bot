#!/bin/bash

echo "🤖 TELEGRAM TRADING BOT SETUP"
echo ""

echo "📋 STEP-BY-STEP SETUP INSTRUCTIONS:"
echo ""
echo "1️⃣ CREATE YOUR TELEGRAM BOT:"
echo "   • Open Telegram and search for: @BotFather"
echo "   • Send: /newbot"
echo "   • Choose a name: 'My Trading Bot'"
echo "   • Choose a username: 'YourTradingBot' (must end with 'bot')"
echo "   • Copy the bot token (looks like: 123456789:ABCdefGHIjklMNOpqrsTUVwxyz)"
echo ""

echo "2️⃣ CONFIGURE THE BOT TOKEN:"
echo "   • Edit the file: clean_bot/src/main/java/com/trading/bot/telegram/TelegramTradingBot.java"
echo "   • Find line: private static final String BOT_TOKEN = \"YOUR_BOT_TOKEN_HERE\";"
echo "   • Replace YOUR_BOT_TOKEN_HERE with your actual token"
echo ""

echo "3️⃣ COMPILE AND RUN:"
echo "   • Run: ./start_telegram_bot.sh"
echo ""

echo "4️⃣ TEST YOUR BOT:"
echo "   • Search for your bot in Telegram"
echo "   • Send: /start"
echo "   • Try commands like: /analyze, /nifty, /help"
echo ""

echo "🎯 AVAILABLE BOT COMMANDS:"
echo "   /start - Welcome and introduction"
echo "   /help - Show all commands"
echo "   /analyze - Comprehensive market analysis"
echo "   /phase1 - Enhanced technical analysis only"
echo "   /phase2 - Advanced multi-component analysis only"
echo "   /nifty - NIFTY 50 analysis"
echo "   /sensex - SENSEX analysis"
echo "   /banknifty - BANKNIFTY analysis"
echo "   /status - Bot status and health"
echo ""

read -p "🤖 Have you created your Telegram bot and got the token? (y/n): " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    read -p "🔑 Please enter your bot token: " BOT_TOKEN
    
    if [[ -n "$BOT_TOKEN" && "$BOT_TOKEN" != "YOUR_BOT_TOKEN_HERE" ]]; then
        echo ""
        echo "🔧 Updating bot token..."
        
        # Update the bot token in the Java file
        sed -i.backup "s/YOUR_BOT_TOKEN_HERE/$BOT_TOKEN/g" clean_bot/src/main/java/com/trading/bot/telegram/TelegramTradingBot.java
        
        if [ $? -eq 0 ]; then
            echo "✅ Bot token updated successfully!"
            echo ""
            echo "🚀 Ready to start your Telegram bot!"
            echo "   Run: ./start_telegram_bot.sh"
        else
            echo "❌ Failed to update bot token. Please edit the file manually."
        fi
    else
        echo "❌ Invalid token provided. Please run setup again."
    fi
else
    echo ""
    echo "📋 Please complete the Telegram bot creation first:"
    echo "   1. Open Telegram"
    echo "   2. Search: @BotFather"
    echo "   3. Send: /newbot"
    echo "   4. Follow instructions"
    echo "   5. Copy the token"
    echo "   6. Run this setup again"
fi

echo ""
echo "📖 For detailed instructions, check: TELEGRAM_BOT_GUIDE.md"