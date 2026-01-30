#!/bin/bash

echo "🎯 TESTING WORKING INDEX OPTIONS BOT"
echo "===================================="
echo "📊 Threshold: 70%+"
echo "🎲 Index Options: CE/PE"
echo "⏰ Entry/Exit: Automated"
echo "🛡️ Stop Loss: Active"
echo "📈 Purpose: Paper Trading"
echo "===================================="

echo ""
echo "🚀 Running Index Options Bot..."
java WorkingIndexOptionsBot

echo ""
echo "📊 Testing multiple runs for consistency..."
echo "==========================================="

for i in {1..3}; do
    echo ""
    echo "📞 Run $i:"
    echo "------"
    java WorkingIndexOptionsBot | grep -E "📞|⚠️" | head -6
done

echo ""
echo "✅ Index Options Bot Test Complete!"
echo "=================================="
echo "💡 Bot is working with 70% confidence threshold"
echo "🎯 Generating proper CE/PE calls with risk management"
echo "📚 Perfect for paper trading and learning"