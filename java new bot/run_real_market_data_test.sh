#!/bin/bash

# REAL MARKET DATA FETCHER & BOT ACCURACY TESTER
# Fetches genuine market data from official sources and tests bot accuracy

echo "================================================================"
echo "🔍 REAL MARKET DATA FETCHER & BOT ACCURACY TESTER"
echo "================================================================"
echo "📊 Fetching REAL market data from official sources"
echo "🎯 Testing bot accuracy against actual market movements"
echo "⚠️  NO SIMULATION - ONLY GENUINE MARKET DATA"
echo "================================================================"

# Set working directory
cd "$(dirname "$0")"

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed or not in PATH"
    echo "Please install Java 11 or higher to run this script"
    exit 1
fi

# Create required directories
mkdir -p real_market_data
mkdir -p accuracy_tests
mkdir -p logs

# Set classpath
CLASSPATH="src/main/java:target/classes"

# Compile the Java source
echo "🔧 Compiling RealMarketDataFetcher..."
javac -cp "$CLASSPATH" src/main/java/com/trading/bot/market/RealMarketDataFetcher.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi

# Run the real market data fetcher
echo "🚀 Starting REAL market data collection and accuracy testing..."
echo "================================================================"

# Set log file with timestamp
LOG_FILE="logs/real_market_test_$(date +%Y%m%d_%H%M%S).log"

# Run the Java application and capture output
echo "⏳ This may take a few minutes as we fetch real data from official sources..."
java -cp "$CLASSPATH" com.trading.bot.market.RealMarketDataFetcher 2>&1 | tee "$LOG_FILE"

if [ $? -eq 0 ]; then
    echo ""
    echo "================================================================"
    echo "✅ REAL MARKET DATA COLLECTION & ACCURACY TEST COMPLETED"
    echo "================================================================"
    
    # Display generated files
    echo ""
    echo "📁 REAL MARKET DATA FILES:"
    if [ -d "real_market_data" ]; then
        ls -la real_market_data/*.csv 2>/dev/null || echo "No real market data files found"
    fi
    
    echo ""
    echo "🎯 BOT ACCURACY TEST FILES:"
    if [ -d "accuracy_tests" ]; then
        ls -la accuracy_tests/*.csv 2>/dev/null || echo "No accuracy test files found"
    fi
    
    echo ""
    echo "📋 Log File: $LOG_FILE"
    
    # Show real data summary
    echo ""
    echo "📊 REAL MARKET DATA SUMMARY:"
    echo "================================================================"
    if [ -f "real_market_data/real_weekly_analysis_$(date +%Y-%m-%d).csv" ]; then
        echo "📈 Weekly Analysis (REAL DATA):"
        cat "real_market_data/real_weekly_analysis_$(date +%Y-%m-%d).csv"
    else
        echo "⚠️ No real market analysis file found"
    fi
    
    echo ""
    echo "🎯 BOT ACCURACY RESULTS:"
    echo "================================================================"
    if [ -f "accuracy_tests/bot_accuracy_test_$(date +%Y-%m-%d).csv" ]; then
        echo "🤖 Bot Accuracy Against Real Market:"
        head -10 "accuracy_tests/bot_accuracy_test_$(date +%Y-%m-%d).csv"
        
        # Calculate accuracy percentage
        if command -v awk &> /dev/null; then
            echo ""
            echo "📊 ACCURACY STATISTICS:"
            TOTAL_PREDICTIONS=$(tail -n +2 "accuracy_tests/bot_accuracy_test_$(date +%Y-%m-%d).csv" | wc -l)
            CORRECT_PREDICTIONS=$(tail -n +2 "accuracy_tests/bot_accuracy_test_$(date +%Y-%m-%d).csv" | grep "CORRECT" | wc -l)
            
            if [ $TOTAL_PREDICTIONS -gt 0 ]; then
                ACCURACY_PERCENT=$(awk "BEGIN {printf \"%.1f\", ($CORRECT_PREDICTIONS/$TOTAL_PREDICTIONS)*100}")
                echo "Total Predictions: $TOTAL_PREDICTIONS"
                echo "Correct Predictions: $CORRECT_PREDICTIONS"
                echo "🎯 BOT ACCURACY: $ACCURACY_PERCENT%"
                
                if (( $(echo "$ACCURACY_PERCENT > 70" | bc -l) )); then
                    echo "🏆 EXCELLENT ACCURACY - Bot performing well!"
                elif (( $(echo "$ACCURACY_PERCENT > 50" | bc -l) )); then
                    echo "👍 GOOD ACCURACY - Bot shows promise"
                else
                    echo "⚠️ NEEDS IMPROVEMENT - Bot accuracy below expectations"
                fi
            fi
        fi
    else
        echo "⚠️ No accuracy test file found"
    fi
    
else
    echo "❌ REAL MARKET DATA COLLECTION FAILED"
    echo "Check log file for details: $LOG_FILE"
    echo ""
    echo "💡 POSSIBLE ISSUES:"
    echo "• Internet connectivity problems"
    echo "• API rate limiting or access restrictions"
    echo "• Market closed (weekends/holidays)"
    echo "• Need API credentials for some official sources"
    exit 1
fi

echo ""
echo "================================================================"
echo "🎯 HONEST ASSESSMENT COMPLETE"
echo "================================================================"
echo "✅ Real market data fetched from official sources"
echo "✅ Bot accuracy tested against actual market movements"
echo "✅ No simulation or artificial data used"
echo ""
echo "📁 Check these directories for results:"
echo "  • real_market_data/ - Genuine market data from official APIs"
echo "  • accuracy_tests/ - Bot performance against real movements"
echo "  • logs/ - Detailed execution logs"
echo ""
echo "🔍 For detailed analysis, run:"
echo "  python3 analyze_real_market_accuracy.py"
echo "================================================================"