#!/bin/bash
# Start Fresh Master Bot with Phase 1-5 Integration and /start Command Support

echo "🚀 STARTING FRESH MASTER BOT"
echo "==========================="
echo "🎯 Complete Phase 1-5 Integration"
echo "💬 /start Command Ready"
echo "✅ Fresh Start - No Previous Issues"
echo ""

cd clean_bot

echo "🔧 Compiling Fresh Master Bot..."
# Ensure CompleteIntegratedTradingBot is compiled first
javac -cp "lib/*:classes" -d classes CompleteIntegratedTradingBot.java 2>/dev/null
# Compile the Fresh Master Bot
javac -cp "lib/*:classes" -d classes src/main/java/com/trading/bot/master/FreshMasterBot.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🤖 LAUNCHING FRESH MASTER BOT..."
    echo "==============================="
    echo "💡 Type '/start' to initialize the bot"
    echo "💡 Type '/help' for all commands"
    echo ""
    
    # Start the Fresh Master Bot
    java -cp "lib/*:classes" com.trading.bot.master.FreshMasterBot
    
else
    echo "❌ Compilation failed"
    echo "🔍 Checking what's available..."
    find classes -name "*.class" | grep -E "(Complete|Fresh|Master)" | head -5
fi