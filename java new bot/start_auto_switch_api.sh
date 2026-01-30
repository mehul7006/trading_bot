#!/bin/bash

echo "🔄 === STARTING ENHANCED AUTO-SWITCH API SYSTEM ==="
echo "📡 6 API Sources: Upstox → Yahoo Finance → AlphaVantage → NSE → Finnhub → Polygon"
echo "🎯 Only Real Market Data - Auto Failover Enabled"
echo

cd "java new bot"

echo "🔧 Compiling Enhanced Auto-Switch API..."
javac -cp "lib/*:src/main/java" src/main/java/com/trading/bot/market/EnhancedAutoSwitchAPI.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
    echo
    
    echo "🚀 Running Enhanced Auto-Switch API Test..."
    java -cp "lib/*:src/main/java:." com.trading.bot.market.EnhancedAutoSwitchAPI
    
    echo
    echo "🎯 Auto-Switch API system is now ready!"
    echo "✅ Use this system in your trading bot for reliable data"
    echo "✅ Automatic failover across 6 different API sources"
    echo "✅ No mock/fake data - only real market prices"
else
    echo "❌ Compilation failed"
    echo "💡 Trying simple compilation without external dependencies..."
    
    echo "🔧 Compiling with basic Java..."
    javac src/main/java/com/trading/bot/market/EnhancedAutoSwitchAPI.java
    
    if [ $? -eq 0 ]; then
        echo "✅ Basic compilation successful"
        echo "🚀 Running test..."
        java -cp "src/main/java" com.trading.bot.market.EnhancedAutoSwitchAPI
    else
        echo "❌ Compilation still failed - check Java installation"
    fi
fi

echo
echo "📋 === API SETUP INSTRUCTIONS ==="
echo "1. Get FREE API keys from:"
echo "   • Alpha Vantage: https://www.alphavantage.co/support/#api-key"
echo "   • Finnhub: https://finnhub.io/register"
echo "   • Polygon: https://polygon.io/"
echo
echo "2. Update the API keys in EnhancedAutoSwitchAPI.java"
echo "3. System will automatically failover when APIs are unavailable"
echo
echo "🎯 Ready to integrate with your trading system!"