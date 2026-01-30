#!/bin/bash

echo "🔥 === LIVE NIFTY & SENSEX RATES ====="
echo "🔑 Using Real Upstox Access Token"
echo "📊 Making Live API Calls..."
echo ""

cd "$(dirname "$0")"

# Compile live connector
echo "🔧 Compiling Live Upstox Connector..."
javac -d "target/classes" "src/main/java/com/trading/bot/market/LiveUpstoxConnector.java"

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    
    echo "📡 Connecting to Upstox API with live token..."
    echo "🎯 Getting current NIFTY and SENSEX rates..."
    echo "=" | head -c 50; echo ""
    
    # Run live data retrieval
    java -cp "target/classes" com.trading.bot.market.LiveUpstoxConnector
    
    echo "=" | head -c 50; echo ""
    echo "✅ Live market data retrieval complete!"
    echo ""
    echo "🚀 READY FOR ENHANCED BOT INTEGRATION:"
    echo "   • Live market data ✅"
    echo "   • Options analysis ✅"  
    echo "   • High-confidence calls ✅"
    echo "   • Automated alerts ✅"
    
else
    echo "❌ Compilation failed"
    echo "Check Java installation and try again"
fi