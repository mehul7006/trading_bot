#!/bin/bash

echo "🔧 STARTING FIXED REAL TRADING BOT"
echo "=================================="
echo "✅ CRITICAL ISSUES FIXED:"
echo "   • NO random number generators"
echo "   • Real technical analysis (RSI, EMA, Momentum)"
echo "   • Realistic call frequency (max 3/hour, min 20 min gap)"
echo "   • Proper confidence calculations (max 85%, not fake 95%)"
echo "   • Real market structure analysis"
echo "   • Proper risk-reward ratios (1:2)"
echo "   • No more 450 calls in 75 minutes!"
echo "=================================="
echo ""

# Compile the fixed bot
echo "🔨 Compiling fixed bot..."
javac FixedRealTradingBot.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo "🚀 Starting Fixed Real Trading Bot..."
    echo "📊 Expected: 2-3 calls per hour maximum"
    echo "⏰ Minimum 20 minutes between calls"
    echo ""
    java FixedRealTradingBot
else
    echo "❌ Compilation failed. Please check the errors above."
    exit 1
fi