#!/bin/bash

echo "🚀 === STARTING FIXED REAL-TIME TRADING BOT ==="
echo "✅ Real market data only"
echo "✅ Market hours validation" 
echo "✅ No duplicate messages"
echo "✅ 30-point movement detection"
echo "✅ Accurate options pricing"
echo ""

# Set Java classpath
export CLASSPATH="src/main/java:lib/*:."

# Compile the bot
echo "📦 Compiling bot..."
cd "java new bot"
javac -cp "$CLASSPATH" src/main/java/com/trading/bot/core/FixedRealTimeBot.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
    echo ""
    echo "🔥 Starting Fixed Real-Time Bot..."
    echo "📱 Send /start to your Telegram bot to begin"
    echo "🛑 Press Ctrl+C to stop"
    echo ""
    
    # Run the bot
    java -cp "$CLASSPATH" com.trading.bot.core.FixedRealTimeBot
else
    echo "❌ Compilation failed"
    exit 1
fi