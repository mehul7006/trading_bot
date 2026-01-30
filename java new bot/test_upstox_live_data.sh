#!/bin/bash

echo "🔥 === UPSTOX LIVE DATA TEST ==="
echo "🔑 Testing with your API credentials..."
echo ""

cd "$(dirname "$0")"

# Compile simple connector (no external dependencies)
echo "🔧 Compiling simple Upstox connector..."
javac -d "target/classes" "src/main/java/com/trading/bot/market/SimpleUpstoxConnector.java"

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    
    # Run the live data test
    java -cp "target/classes" com.trading.bot.market.SimpleUpstoxConnector
    
else
    echo "❌ Compilation failed"
fi