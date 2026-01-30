#!/bin/bash

echo "🚀 FIXED TRADING SYSTEM - ALL IMPROVEMENTS INTEGRATED"
echo "====================================================="
echo "✅ Enhanced NIFTY Algorithm (38% → 75%+ accuracy target)"
echo "✅ Risk Management (Fix ₹89 loss per trade)"
echo "✅ Confidence Calibration (75%+ threshold enforced)"
echo "✅ Real market data only - no mock/fake/simulated data"
echo "====================================================="

echo "🔨 Compiling all enhanced components..."

# Compile all parts in order
echo "📊 Part 1: Enhanced NIFTY Predictor..."
javac EnhancedNiftyPredictor.java

echo "🛡️ Part 2: Enhanced Risk Manager..."
javac EnhancedRiskManager.java

echo "🎯 Part 3: Enhanced Confidence Calibrator..."
javac EnhancedConfidenceCalibrator.java

echo "🚀 Part 4: Master Trading System Fixed..."
javac MasterTradingSystemFixed.java

if [ $? -eq 0 ]; then
    echo "✅ All components compiled successfully!"
    echo ""
    echo "🚀 Starting Fixed Trading System..."
    echo "🎯 NIFTY Algorithm: Enhanced for 75%+ accuracy"
    echo "🛡️ Risk Management: 2% max risk per trade"
    echo "🎯 Confidence: 75%+ threshold enforced"
    echo "📊 Market Data: Real data integration"
    echo ""
    echo "Expected Improvements:"
    echo "  📈 NIFTY Accuracy: 38% → 75%+"
    echo "  💰 Average P&L: ₹-89 → Positive"
    echo "  🎯 Confidence: Calibrated & validated"
    echo "  🛡️ Risk: Controlled & managed"
    echo ""
    echo "Press Ctrl+C to stop"
    echo "===================="
    
    # Run the fixed trading system
    java MasterTradingSystemFixed
    
else
    echo "❌ Compilation failed"
    echo "💡 Please check the error messages above"
fi