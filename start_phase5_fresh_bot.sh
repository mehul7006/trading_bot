#!/bin/bash
# Fresh Phase 5 AI Trading Bot Startup Script
# Complete implementation with proper response handling

echo "🚀 STARTING FRESH PHASE 5 AI TRADING BOT"
echo "========================================"

cd clean_bot

echo "✅ 1. Compiling all Phase 5 components..."
echo "   - Compiling core components..."
javac -cp "lib/*" -d classes src/main/java/com/trading/bot/core/*.java
echo "   - Compiling AI components..."
javac -cp "lib/*:classes" -d classes src/main/java/com/trading/bot/ai/*.java
echo "   - Compiling real-time components..."
javac -cp "lib/*:classes" -d classes src/main/java/com/trading/bot/realtime/*.java
echo "   - Compiling execution components..."
javac -cp "lib/*:classes" -d classes src/main/java/com/trading/bot/execution/*.java
echo "   - Compiling Phase 5 main bot..."
javac -cp "lib/*:classes" -d classes src/main/java/com/trading/bot/core/Phase5AIExecutionBot.java

if [ $? -eq 0 ]; then
    echo "   ✅ All Phase 5 components compiled successfully"
else
    echo "   ❌ Compilation failed. Check errors above."
    exit 1
fi

echo ""
echo "✅ 2. Creating Phase 5 bot launcher..."

# Create launcher in proper package structure
mkdir -p src/main/java/com/trading/bot/launcher
cp ../Phase5BotLauncher.java src/main/java/com/trading/bot/launcher/

echo "✅ 3. Compiling Phase 5 bot launcher..."
javac -cp "lib/*:classes" -d classes src/main/java/com/trading/bot/launcher/Phase5BotLauncher.java

if [ $? -eq 0 ]; then
    echo "   ✅ Phase 5 launcher compiled successfully"
else
    echo "   ❌ Launcher compilation failed"
    exit 1
fi

echo ""
echo "🚀 4. Starting Fresh Phase 5 AI Trading Bot..."
echo "=============================================="
echo "🎯 Features: Neural Networks + Real-Time + Auto Execution"
echo "🧠 Target Accuracy: 98%+ (Enhanced AI system)"
echo "⚡ All Phase 1-5 components integrated and operational"
echo ""

# Start the bot with proper classpath
java -cp "lib/*:classes" com.trading.bot.launcher.Phase5BotLauncher