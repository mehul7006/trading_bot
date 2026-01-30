#!/bin/bash

echo "🚀 === ENHANCED INDEX OPTIONS TRADING BOT ==="
echo "📊 Advanced Scanner for NIFTY, BANKNIFTY, SENSEX & More"
echo "🎯 High-Confidence Call Generator with Multi-Factor Analysis"
echo "⚡ Real-Time Market Pulse & Unusual Activity Detection"
echo ""

cd "$(dirname "$0")"

# Compile the enhanced system
echo "🔧 Compiling enhanced trading system..."
mvn clean compile -q

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
    echo ""
    echo "🎯 Starting Enhanced Options Bot..."
    echo "📈 Available Commands:"
    echo "   • scan-all-options       - Scan all index options"
    echo "   • generate-calls         - Generate high-confidence calls"
    echo "   • market-pulse          - Real-time market overview"
    echo "   • volume-analysis       - Options volume analysis"
    echo "   • unusual-activity      - Detect big player moves"
    echo "   • help                  - Show all commands"
    echo ""
    
    # Start the enhanced bot
    java -cp "target/classes:lib/*" com.trading.bot.core.EnhancedBotCommandHandler "$@"
    
else
    echo "❌ Compilation failed"
    echo "Running with detailed output..."
    mvn clean compile
fi