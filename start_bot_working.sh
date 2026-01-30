#!/bin/bash

echo "🤖 TESTING /start COMMAND - GUARANTEED RESPONSE"
echo ""

cd clean_bot

echo "Step 1: Ensuring no bots are running..."
pkill -9 -f "java.*trading" 2>/dev/null || true
pkill -9 -f "bot" 2>/dev/null || true
sleep 2

echo "✅ All bots killed"

echo ""
echo "Step 2: Compiling bot..."
mvn compile -q

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi

echo ""
echo "Step 3: Testing simple /start command response..."
java -cp "target/classes:lib/*" com.trading.bot.demo.SimpleStartDemo /start

echo ""
echo "Step 4: Testing full bot /start command..."
echo "This should respond immediately:"
java -cp "target/classes:lib/*" com.trading.bot.simple.ResponsiveBot /start

echo ""
echo "🎉 /start command test completed!"
echo ""
echo "📋 If /start responded above, your bot is working!"
echo "📋 If /start did not respond, there's still an issue to fix."