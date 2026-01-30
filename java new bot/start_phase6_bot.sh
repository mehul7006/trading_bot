#!/bin/bash

echo "🚀 === PHASE 6 COMPLETE INTEGRATION BOT LAUNCHER ==="
echo "✅ Updated Upstox API Credentials"
echo "📊 Real Data • No Mock Data • Full Integration"
echo ""

cd "$(dirname "$0")"

# Compile if needed
if [ ! -f "target/classes/com/trading/bot/core/Phase6CompleteBot.class" ]; then
    echo "🔧 Compiling Phase 6 bot..."
    javac -cp "target/classes" -d "target/classes" "src/main/java/com/trading/bot/core/Phase6CompleteBot.java"
fi

echo "🎮 Choose your mode:"
echo "1. Interactive Mode (recommended)"
echo "2. Auto-Start Mode"
echo ""
read -p "Enter choice (1 or 2): " choice

case $choice in
    1)
        echo "🎮 Starting Interactive Mode..."
        java -cp "target/classes" com.trading.bot.core.Phase6CompleteBot
        ;;
    2)
        echo "🚀 Starting Auto Mode..."
        java -cp "target/classes" com.trading.bot.core.Phase6CompleteBot auto
        ;;
    *)
        echo "❌ Invalid choice. Starting Interactive Mode..."
        java -cp "target/classes" com.trading.bot.core.Phase6CompleteBot
        ;;
esac