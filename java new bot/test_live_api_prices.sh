#!/bin/bash

echo "🔍 === TESTING BOTH APIs FOR LIVE PRICES ====="
echo "📊 Will show NIFTY and SENSEX from both Upstox and Shoonya"
echo "🎯 You'll see exactly what each API returns"
echo ""

cd "$(dirname "$0")"

echo "🔧 Compiling Live API Tester..."
javac -d "target/classes" "src/main/java/com/trading/bot/market/LiveAPITester.java"

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    
    echo "🚀 Starting API tests..."
    echo "📡 This will test both APIs with your real credentials"
    echo "⏰ Test started at: $(date '+%H:%M:%S')"
    echo ""
    echo "=" | head -c 70; echo ""
    
    # Run the live API tester
    java -cp "target/classes" com.trading.bot.market.LiveAPITester
    
    echo "=" | head -c 70; echo ""
    echo ""
    echo "✅ API Testing Complete!"
    echo "📊 You now know the exact status of both APIs"
    echo "🎯 Your failover system will use whichever API is working"
    
else
    echo "❌ Compilation failed"
    echo "Please check Java installation"
fi