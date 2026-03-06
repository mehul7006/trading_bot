#!/bin/bash

echo "🔧 RESTARTING BOT WITH FIXED FORMATTING"
echo "========================================"

# Stop any running bots
echo "🛑 Stopping any running bots..."
pkill -f "WorkingTelegramBot" 2>/dev/null
sleep 2

# Recompile with fixes
echo "🔨 Recompiling with formatting fixes..."
cd clean_bot
javac -cp "lib/*:target/classes:." WorkingTelegramBot.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi

# Start the fixed bot
echo "🚀 Starting fixed Telegram bot..."
echo "📱 Bot will now respond properly to /start commands"
echo "✅ All message formatting issues fixed"
echo ""

# Run the bot
java -cp "lib/*:target/classes:." WorkingTelegramBot