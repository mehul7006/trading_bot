#!/bin/bash

echo "🚀 STARTING LATEST COMPLETE TRADING BOT - 2025"
echo "═══════════════════════════════════════════════"
echo "📊 All Latest Features + Advanced Analytics + Risk Management"
echo ""

cd "$(dirname "$0")"

echo "🔧 Compiling Latest Trading Bot..."
javac LatestCompleteTradingBot.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🎬 Starting Latest Trading Bot with all features..."
    echo "═" | head -c 60; echo ""
    
    # Run the latest trading bot
    java LatestCompleteTradingBot
    
    echo ""
    echo "═" | head -c 60; echo ""
    echo "🏁 Latest Trading Bot session completed!"
    
else
    echo "❌ Compilation failed"
    echo "Please check the Java code for errors"
fi