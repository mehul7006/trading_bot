#!/bin/bash

echo "🎯 STARTING IMPROVED TRADING BOT - AUDIT COMPLIANT"
echo "="
echo "✅ IMPROVEMENTS IMPLEMENTED:"
echo "   • Removed all random number generators"
echo "   • Real technical analysis (RSI, EMA, Volume)"
echo "   • Proper entry/exit logic with targets"
echo "   • Risk management (1% risk per trade)"
echo "   • Position sizing and stop losses"
echo "   • Real market data structure"
echo "   • Portfolio protection"
echo "="
echo "🚀 Starting the improved bot..."
echo ""

# Compile all Java files
echo "🔨 Compiling improved bot components..."
javac -cp . *.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo "🚀 Starting Improved Trading Bot..."
    java ImprovedTradingBot
else
    echo "❌ Compilation failed. Please check the errors above."
    exit 1
fi