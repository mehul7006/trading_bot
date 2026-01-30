#!/bin/bash

# COMPLETE WORLD CLASS TRADING BOT - SIMPLE EXECUTION
# Self-contained system with 80%+ accuracy target
# Uses ONLY real market data - NO fake or mock data

echo "🌟 COMPLETE WORLD CLASS TRADING BOT"
echo "==========================================="
echo "🎯 TARGET: 80%+ ACCURACY"
echo "💎 REAL DATA ONLY"
echo "📊 50+ PROFESSIONAL INDICATORS"
echo "🏛️ INSTITUTIONAL STRATEGIES"
echo "==========================================="

# Set Java options for optimal performance
export JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC"

# Create results directory
mkdir -p world_class_results
mkdir -p logs

# Set timestamp for logging
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_FILE="logs/world_class_execution_${TIMESTAMP}.log"

echo "📁 Results directory: world_class_results/"
echo "📄 Log file: ${LOG_FILE}"
echo ""

# Function to log with timestamp
log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

log_msg "🚀 Starting Complete World Class Trading Bot"

# Check Java installation
if ! command -v java &> /dev/null; then
    log_msg "❌ Java not found. Please install Java 11 or higher."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
log_msg "✅ Java version: $JAVA_VERSION"

# Compile the bot
log_msg "🔨 Compiling Complete World Class Trading Bot..."
javac -cp ".:lib/*" src/main/java/com/trading/bot/complete/CompleteWorldClassTradingBot.java 2>> "$LOG_FILE"

if [ $? -ne 0 ]; then
    log_msg "❌ Compilation failed. Check log for details."
    exit 1
fi

log_msg "✅ Compilation successful"

# Run the world-class analysis
log_msg "📈 Executing world-class trading analysis..."
echo ""

java -cp ".:lib/*:src/main/java" $JAVA_OPTS com.trading.bot.complete.CompleteWorldClassTradingBot 2>&1 | tee -a "$LOG_FILE"

EXECUTION_STATUS=$?

echo ""
if [ $EXECUTION_STATUS -eq 0 ]; then
    log_msg "✅ World-class analysis completed successfully"
    
    # Check if results were generated
    if ls world_class_results/complete_analysis_*.csv 1> /dev/null 2>&1; then
        LATEST_RESULT=$(ls -t world_class_results/complete_analysis_*.csv | head -1)
        SIGNAL_COUNT=$(tail -n +2 "$LATEST_RESULT" | wc -l)
        INSTITUTIONAL_COUNT=$(tail -n +2 "$LATEST_RESULT" | awk -F',' '$17 == "true"' | wc -l)
        
        log_msg "📊 Generated $SIGNAL_COUNT total signals"
        log_msg "🏛️ $INSTITUTIONAL_COUNT institutional-grade signals"
        log_msg "📄 Latest results: $LATEST_RESULT"
        
        if [ "$INSTITUTIONAL_COUNT" -gt 0 ]; then
            log_msg "🎉 INSTITUTIONAL-GRADE SIGNALS GENERATED!"
            log_msg "🏆 System operating at world-class level"
        fi
    else
        log_msg "⚠️ No result files found"
    fi
else
    log_msg "❌ Execution failed with status: $EXECUTION_STATUS"
fi

# Display summary
echo ""
echo "🎯 EXECUTION SUMMARY"
echo "===================="
echo "📅 Date: $(date '+%Y-%m-%d %H:%M:%S')"
echo "📄 Log: $LOG_FILE"
echo "📁 Results: world_class_results/"

if [ $EXECUTION_STATUS -eq 0 ]; then
    echo "✅ STATUS: SUCCESS"
    echo "🏆 World-class trading analysis completed"
else
    echo "❌ STATUS: FAILED"
    echo "🔍 Check log file for details"
fi

echo "===================="

log_msg "🏁 Complete World Class Trading Bot execution finished"