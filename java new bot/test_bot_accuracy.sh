#!/bin/bash

echo "🧪 === BOT ACCURACY TESTING LAUNCHER ==="
echo "🎯 Test your 6-phase bot with real BSE/NSE data"
echo ""

cd "$(dirname "$0")"

# Find the latest data capture directory
DATA_DIR=$(find . -name "market_data_capture_*" -type d | sort | tail -1)

if [ -z "$DATA_DIR" ]; then
    echo "❌ No market data capture directory found!"
    echo "📊 First run the data capture system:"
    echo "java -cp \"target/classes\" com.trading.bot.data.RealBSENSEDataCapture"
    exit 1
fi

echo "📁 Using data directory: $DATA_DIR"
echo ""

# Compile the accuracy tester
echo "🔧 Compiling accuracy tester..."
javac -cp "target/classes" -d "target/classes" "src/main/java/com/trading/bot/testing/BotAccuracyTester.java"

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed!"
    exit 1
fi

echo "✅ Compilation successful!"
echo ""

echo "🚀 Starting bot accuracy test..."
echo "⏳ This may take a few minutes..."
echo ""

# Run the accuracy test
java -cp "target/classes" com.trading.bot.testing.BotAccuracyTester "$DATA_DIR"

echo ""
echo "✅ Bot accuracy testing completed!"
echo "📊 Check the generated report file for detailed results"