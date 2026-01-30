#!/bin/bash

echo "🚀 TODAY'S HONEST OPTIONS BACKTESTING"
echo "====================================="

# Set up environment
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/default-java}
export PATH=$JAVA_HOME/bin:$PATH

# Navigate to bot directory
cd "java new bot"

echo "📊 Compiling Today's Honest Options Backtester..."

# Compile the backtester
javac -cp ".:*" TodayHonestOptionsBacktester.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🎯 Running Today's Honest Options Backtesting..."
    echo "================================================"
    
    # Run the backtester
    java -cp ".:*" TodayHonestOptionsBacktester
    
    echo ""
    echo "📊 Backtesting Results:"
    echo "======================"
    
    # Show the generated report if it exists
    if [ -f "today_options_backtest_$(date +%Y-%m-%d).txt" ]; then
        echo "📄 Report generated successfully!"
        echo "📂 File: today_options_backtest_$(date +%Y-%m-%d).txt"
        echo ""
        echo "📊 Report Summary:"
        head -20 "today_options_backtest_$(date +%Y-%m-%d).txt"
    else
        echo "⚠️ Report file not found"
    fi
    
else
    echo "❌ Compilation failed!"
    echo "Please check for compilation errors above."
fi

echo ""
echo "✅ Today's honest options backtesting script completed!"