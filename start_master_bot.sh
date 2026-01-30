#!/bin/bash

echo "🚀 MASTER TRADING BOT - PHASE 1 + PHASE 2 INTEGRATION"
echo "Enhanced Technical Analysis + Multi-Timeframe + Advanced Indicators + ML Validation"
echo ""

cd clean_bot

echo "Step 1: Compiling Master Trading Bot..."
mvn compile -q

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi

echo ""
echo "=== MASTER TRADING BOT READY ==="
echo ""
echo "🎯 Available Modes:"
echo "  • PHASE1: Enhanced Technical Analysis + Basic ML"
echo "  • PHASE2: Multi-Timeframe + Advanced Indicators + Enhanced ML"
echo "  • BOTH: Comprehensive analysis with comparison (RECOMMENDED)"
echo ""
echo "💡 Usage:"
echo "  Interactive Mode: ./start_master_bot.sh"
echo "  Specific Mode: ./start_master_bot.sh [PHASE1|PHASE2|BOTH]"
echo ""

# Check if mode argument provided
if [ $# -eq 0 ]; then
    echo "🔄 Starting Interactive Mode..."
    echo ""
    echo "Available Commands:"
    echo "  help     - Show available commands"
    echo "  mode     - Set operating mode (PHASE1/PHASE2/BOTH)"
    echo "  status   - Show system status"
    echo "  test     - Run test analysis with sample data"
    echo "  exit     - Shutdown the bot"
    echo ""
    echo "Type 'help' for detailed command information"
    echo ""
    java -cp "target/classes:lib/*" com.trading.bot.core.MasterTradingBot
else
    MODE=$1
    echo "🎯 Running in $MODE mode with test analysis..."
    echo ""
    java -cp "target/classes:lib/*" com.trading.bot.core.MasterTradingBot $MODE
fi

echo ""
echo "🎉 Master Trading Bot session completed"