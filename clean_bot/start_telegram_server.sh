#!/bin/bash
# Start Real Telegram Bot Server

echo "🚀 STARTING REAL TELEGRAM BOT SERVER"
echo "===================================="
echo "📡 This will handle actual /start commands from Telegram"
echo "🌐 Server will run on localhost:8080"
echo ""

cd clean_bot

echo "🔧 Compiling Telegram bot server..."
javac TelegramBotServer.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🚀 Starting Telegram bot server..."
    echo "📱 Your Telegram bot will now respond to /start commands"
    echo ""
    
    java TelegramBotServer
else
    echo "❌ Compilation failed"
fi