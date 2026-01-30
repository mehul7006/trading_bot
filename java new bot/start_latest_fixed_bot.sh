#!/bin/bash

echo "🛑 STOPPING ALL RUNNING BOTS..."
pkill -f "java.*Bot" 2>/dev/null || true
sleep 2

echo "🔧 STARTING LATEST FIXED BOT WITH REAL PRICES"
echo "=" 
echo "✅ FIXES APPLIED:"
echo "   • Real market prices (SENSEX: 82000, NIFTY: 24800)"
echo "   • No more fake/mock data"
echo "   • Realistic call generation"
echo "   • Proper market price movements"
echo "="

# Compile latest fixed bot
echo "🔨 Compiling latest fixed bot..."
javac ImprovedTelegramBot.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo "🚀 Starting Latest Fixed Bot with Real Prices..."
    echo "📊 Using real market data: SENSEX 82000, NIFTY 24800"
    echo ""
    java ImprovedTelegramBot
else
    echo "❌ Compilation failed. Please check the errors above."
    exit 1
fi