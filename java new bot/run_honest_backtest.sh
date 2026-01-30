#!/bin/bash

# HONEST BACKTESTING - REAL MARKET DATA VALIDATION
# Fetches real market data from official sources and tests bot accuracy

echo "🔍 HONEST BACKTESTING - REAL MARKET DATA VALIDATION"
echo "================================================================"
echo "📊 Step 1: Fetching real market data from official sources"
echo "🤖 Step 2: Testing bot predictions against actual movements" 
echo "📈 Step 3: Calculating genuine accuracy percentages"
echo "================================================================"

# Set working directory
cd "$(dirname "$0")"

# Create required directories
mkdir -p honest_backtest
mkdir -p logs

# Check prerequisites
echo "🔧 Checking prerequisites..."

# Check Java
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed"
    exit 1
fi
echo "✅ Java available"

# Check Python
if ! command -v python3 &> /dev/null; then
    echo "❌ Python3 is not installed"
    exit 1
fi
echo "✅ Python3 available"

# Install required Python packages
echo "📦 Installing required Python packages..."
pip3 install requests pandas numpy >/dev/null 2>&1
echo "✅ Python packages ready"

# Set log file
LOG_FILE="logs/honest_backtest_$(date +%Y%m%d_%H%M%S).log"

echo ""
echo "🌐 STEP 1: FETCHING REAL MARKET DATA FROM OFFICIAL SOURCES"
echo "================================================================"

# Run Python script to fetch real market data
echo "📊 Fetching real market data for last week..."
python3 real_data_fetcher.py 2>&1 | tee -a "$LOG_FILE"

PYTHON_EXIT_CODE=${PIPESTATUS[0]}

if [ $PYTHON_EXIT_CODE -eq 0 ]; then
    echo "✅ Real market data fetched successfully"
else
    echo "⚠️ Some issues with data fetching, but continuing with available data"
fi

echo ""
echo "🤖 STEP 2: COMPILING AND RUNNING HONEST BACKTESTER"
echo "================================================================"

# Set classpath
CLASSPATH="src/main/java:lib/*:target/classes"

# Compile the honest backtester
echo "🔧 Compiling HonestBacktester..."
javac -cp "$CLASSPATH" src/main/java/com/trading/bot/backtest/HonestBacktester.java 2>&1 | tee -a "$LOG_FILE"

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    echo "Check log: $LOG_FILE"
    exit 1
fi

echo ""
echo "🎯 STEP 3: RUNNING HONEST ACCURACY TESTING"
echo "================================================================"

# Run the honest backtester
echo "🔍 Testing bot predictions against real market movements..."
java -cp "$CLASSPATH" com.trading.bot.backtest.HonestBacktester 2>&1 | tee -a "$LOG_FILE"

JAVA_EXIT_CODE=$?

echo ""
echo "================================================================"

if [ $JAVA_EXIT_CODE -eq 0 ]; then
    echo "✅ HONEST BACKTESTING COMPLETED SUCCESSFULLY"
    echo "================================================================"
    
    # Display results
    echo ""
    echo "📁 GENERATED FILES:"
    echo "🔹 Real Market Data:"
    ls -la honest_backtest/real_market_data_*.csv 2>/dev/null | tail -3
    
    echo ""
    echo "🔹 Accuracy Results:"
    ls -la honest_backtest/accuracy_results_*.csv 2>/dev/null | tail -3
    
    echo ""
    echo "🔹 Detailed Analysis:"
    ls -la honest_backtest/detailed_analysis_*.csv 2>/dev/null | tail -3
    
    # Show quick summary from the latest results
    echo ""
    echo "📊 QUICK ACCURACY SUMMARY:"
    echo "================================================================"
    
    LATEST_ANALYSIS=$(ls -t honest_backtest/detailed_analysis_*.csv 2>/dev/null | head -1)
    if [ -f "$LATEST_ANALYSIS" ]; then
        echo "📈 Bot Accuracy Results by Index:"
        echo ""
        printf "%-12s %-15s %-20s\n" "INDEX" "ACCURACY" "ASSESSMENT"
        echo "------------------------------------------------"
        
        # Read and display accuracy results
        while IFS=',' read -r symbol total correct accuracy avg_conf best worst; do
            if [ "$symbol" != "Symbol" ]; then  # Skip header
                # Determine assessment
                if (( $(echo "$accuracy >= 75" | bc -l 2>/dev/null || echo "0") )); then
                    assessment="EXCELLENT ✅"
                elif (( $(echo "$accuracy >= 65" | bc -l 2>/dev/null || echo "0") )); then
                    assessment="GOOD 👍"
                elif (( $(echo "$accuracy >= 50" | bc -l 2>/dev/null || echo "0") )); then
                    assessment="AVERAGE 😐"
                else
                    assessment="NEEDS WORK ⚠️"
                fi
                
                printf "%-12s %-15s %-20s\n" "$symbol" "${accuracy}%" "$assessment"
            fi
        done < "$LATEST_ANALYSIS"
        
        # Calculate overall accuracy
        OVERALL_ACCURACY=$(tail -n +2 "$LATEST_ANALYSIS" | awk -F',' '{sum+=$4; count++} END {if(count>0) print sum/count; else print 0}')
        
        echo "------------------------------------------------"
        printf "%-12s %-15s %-20s\n" "OVERALL" "${OVERALL_ACCURACY}%" \
            "$(if (( $(echo "$OVERALL_ACCURACY >= 75" | bc -l 2>/dev/null || echo "0") )); then echo "TARGET ACHIEVED ✅"; 
               elif (( $(echo "$OVERALL_ACCURACY >= 65" | bc -l 2>/dev/null || echo "0") )); then echo "CLOSE TO TARGET 👍"; 
               else echo "BELOW TARGET ⚠️"; fi)"
    else
        echo "⚠️ Detailed analysis file not found"
    fi
    
    # Show sample predictions vs actual
    echo ""
    echo "📋 SAMPLE PREDICTIONS VS ACTUAL RESULTS:"
    echo "================================================================"
    
    LATEST_ACCURACY=$(ls -t honest_backtest/accuracy_results_*.csv 2>/dev/null | head -1)
    if [ -f "$LATEST_ACCURACY" ]; then
        echo "Date       | Symbol     | Predicted      | Actual         | Result | Confidence"
        echo "-----------|------------|----------------|----------------|--------|------------"
        head -10 "$LATEST_ACCURACY" | tail -9 | while IFS=',' read -r symbol date predicted actual result confidence type actual_change; do
            printf "%-10s | %-10s | %-14s | %-14s | %-6s | %s%%\n" \
                "$date" "$symbol" "$predicted" "$actual" "$result" "$confidence"
        done
    fi
    
    echo ""
    echo "🎯 HONEST ASSESSMENT SUMMARY:"
    echo "================================================================"
    
    if [ -f "$LATEST_ANALYSIS" ]; then
        TOTAL_PREDICTIONS=$(tail -n +2 "$LATEST_ANALYSIS" | awk -F',' '{sum+=$2} END {print sum}')
        TOTAL_CORRECT=$(tail -n +2 "$LATEST_ANALYSIS" | awk -F',' '{sum+=$3} END {print sum}')
        
        if [ "$TOTAL_PREDICTIONS" -gt 0 ]; then
            echo "📊 Total Predictions Tested: $TOTAL_PREDICTIONS"
            echo "✅ Correct Predictions: $TOTAL_CORRECT"
            echo "🎯 Overall Accuracy: ${OVERALL_ACCURACY}%"
            echo ""
            
            if (( $(echo "$OVERALL_ACCURACY >= 75" | bc -l 2>/dev/null || echo "0") )); then
                echo "🎉 EXCELLENT! Bot achieves 75%+ accuracy target"
                echo "✅ Bot is performing at professional trading level"
                echo "🚀 Ready for live trading consideration"
            elif (( $(echo "$OVERALL_ACCURACY >= 65" | bc -l 2>/dev/null || echo "0") )); then
                echo "👍 GOOD performance - Close to 75% target"
                echo "🔧 Minor optimizations could push it to target"
                echo "📈 Strong foundation for live trading"
            elif (( $(echo "$OVERALL_ACCURACY >= 50" | bc -l 2>/dev/null || echo "0") )); then
                echo "😐 AVERAGE performance - Better than random"
                echo "🔧 Significant improvements needed for 75% target"
                echo "⚠️ Not ready for live trading yet"
            else
                echo "⚠️ BELOW EXPECTATIONS - Needs major improvements"
                echo "🔧 Algorithm requires substantial revision"
                echo "❌ Not suitable for live trading"
            fi
        fi
    fi
    
    echo ""
    echo "📋 COMPLETE LOG: $LOG_FILE"
    echo "================================================================"
    
else
    echo "❌ HONEST BACKTESTING FAILED"
    echo "================================================================"
    echo "Check detailed log: $LOG_FILE"
    echo ""
    echo "🔧 TROUBLESHOOTING TIPS:"
    echo "• Ensure Java is properly installed (version 11+)"
    echo "• Check internet connectivity for data fetching"
    echo "• Verify Python packages are installed"
    echo "• Check for compilation errors in the log"
    exit 1
fi

echo ""
echo "🎯 NEXT STEPS BASED ON RESULTS:"
echo "================================================================"
echo "• Review detailed files in honest_backtest/ directory"
echo "• Analyze prediction patterns and failure cases"
echo "• If accuracy >= 75%: Consider live paper trading"
echo "• If accuracy < 75%: Focus on model improvements"
echo "• Use insights to enhance prediction algorithms"
echo "================================================================"