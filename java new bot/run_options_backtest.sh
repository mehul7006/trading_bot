#!/bin/bash

echo "📊 HONEST CE/PE OPTIONS BACKTESTER"
echo "=================================="
echo "🎯 Tests CE/PE options calls with 75%+ confidence only"
echo "📈 Uses real market data for honest win rate analysis"
echo "💹 Covers NIFTY & SENSEX options trading"
echo "🔍 Provides honest performance evaluation"
echo "=================================="

echo "🔨 Compiling honest CE/PE options backtester..."
javac HonestCEPEOptionsBacktester.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🚀 Starting honest CE/PE options backtesting..."
    echo "📅 Date: $(date +%d-%m-%Y)"
    echo "📊 Data: Real market data (5-second intervals)"
    echo "🎯 Confidence: 75%+ threshold enforced"
    echo "💹 Options: NIFTY & SENSEX CE/PE calls"
    echo ""
    echo "Expected Analysis:"
    echo "  📈 CE/PE call generation with high confidence"
    echo "  🎯 Win rate analysis for options trading"
    echo "  💰 P&L calculation for each trade"
    echo "  📊 Performance breakdown by call type"
    echo ""
    echo "Running comprehensive options backtesting..."
    echo "============================================"
    
    # Run the honest CE/PE options backtester
    java HonestCEPEOptionsBacktester
    
    echo ""
    echo "✅ Honest CE/PE options backtesting completed!"
    echo "📊 Check the detailed report file for complete analysis"
    echo "🎯 Honest win rate and performance results generated!"
    
else
    echo "❌ Compilation failed"
    echo "💡 Please check the error messages above"
fi