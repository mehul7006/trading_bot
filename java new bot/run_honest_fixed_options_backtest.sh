#!/bin/bash

echo "🔍 HONEST FIXED OPTIONS BACKTESTER"
echo "=================================="
echo "✅ Tests FIXED CE/PE options bot with all improvements"
echo "✅ Greeks + IV Analysis + PE Generation integrated"
echo "✅ 75%+ confidence threshold strictly enforced"
echo "✅ NIFTY & SENSEX predictions tested honestly"
echo "=================================="

echo "🔨 Compiling honest fixed options backtester..."

# Compile all dependencies first
echo "📊 Compiling dependencies..."
javac OptionsGreeksCalculator.java 2>/dev/null
javac ImpliedVolatilityAnalyzer.java 2>/dev/null
javac EnhancedPECallGenerator.java 2>/dev/null
javac MasterOptionsTrader.java 2>/dev/null

# Compile the honest fixed backtester
javac HonestFixedOptionsBacktester.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🚀 Starting honest FIXED options backtesting..."
    echo "📅 Date: $(date +%d-%m-%Y)"
    echo "📊 Data: Real market data (5-second intervals)"
    echo "🎯 Confidence: 75%+ threshold STRICTLY enforced"
    echo "🔧 System: FIXED with all improvements"
    echo ""
    echo "Expected Improvements vs Original:"
    echo "  📈 Win Rate: 38.10% → 60%+ target"
    echo "  💰 P&L: ₹-49.94 → Positive per call"
    echo "  📊 Call Balance: CE only → Balanced CE/PE"
    echo "  🎯 Analysis: Basic → Greeks + IV + PE"
    echo ""
    echo "🔧 FIXED System Features:"
    echo "  ✅ Professional Greeks analysis (Delta, Gamma, Theta, Vega)"
    echo "  ✅ Implied Volatility assessment and percentile ranking"
    echo "  ✅ Enhanced PE call generation for bearish opportunities"
    echo "  ✅ Master integration with comprehensive analysis"
    echo ""
    echo "Running comprehensive FIXED options backtesting..."
    echo "================================================="
    
    # Run the honest fixed options backtester
    java HonestFixedOptionsBacktester
    
    echo ""
    echo "✅ Honest FIXED options backtesting completed!"
    echo "📊 Check the detailed report file for complete analysis"
    echo "🎯 Honest win rate and performance results with all fixes!"
    
else
    echo "❌ Compilation failed"
    echo "💡 Please check the error messages above"
fi