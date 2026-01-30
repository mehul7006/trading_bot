#!/bin/bash

# SIMPLE WEEKLY MARKET DATA FETCHER
# No external dependencies - uses built-in Java HTTP client and Yahoo Finance CSV API

echo "============================================================"
echo "SIMPLE WEEKLY MARKET DATA FETCHER"
echo "============================================================"
echo "Fetching last week's market data from Yahoo Finance CSV API"
echo "No external dependencies required!"
echo "============================================================"

# Set working directory
cd "$(dirname "$0")"

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed or not in PATH"
    echo "Please install Java 11 or higher to run this script"
    exit 1
fi

# Create required directories
mkdir -p market_data
mkdir -p logs

# Set classpath
CLASSPATH="src/main/java:target/classes"

# Compile the Java source
echo "🔧 Compiling SimpleWeeklyMarketDataFetcher..."
javac -cp "$CLASSPATH" src/main/java/com/trading/bot/market/SimpleWeeklyMarketDataFetcher.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi

# Run the market data fetcher
echo "🚀 Starting market data collection..."
echo "============================================================"

# Set log file with timestamp
LOG_FILE="logs/simple_weekly_market_fetch_$(date +%Y%m%d_%H%M%S).log"

# Run the Java application and capture output
java -cp "$CLASSPATH" com.trading.bot.market.SimpleWeeklyMarketDataFetcher 2>&1 | tee "$LOG_FILE"

if [ $? -eq 0 ]; then
    echo "============================================================"
    echo "✅ WEEKLY MARKET DATA COLLECTION COMPLETED SUCCESSFULLY"
    echo "============================================================"
    
    # Display file information
    echo ""
    echo "📁 Generated Files:"
    ls -la market_data/weekly_market_*$(date +%Y-%m-%d).csv 2>/dev/null || echo "No CSV files found"
    
    echo ""
    echo "📋 Log File: $LOG_FILE"
    
    # Show sample data
    echo ""
    echo "📊 SAMPLE DATA (first 5 lines):"
    echo "============================================================"
    if [ -f "market_data/weekly_market_data_$(date +%Y-%m-%d).csv" ]; then
        head -6 "market_data/weekly_market_data_$(date +%Y-%m-%d).csv"
    fi
    
    echo ""
    echo "📈 WEEKLY ANALYSIS:"
    echo "============================================================"
    if [ -f "market_data/weekly_market_analysis_$(date +%Y-%m-%d).csv" ]; then
        cat "market_data/weekly_market_analysis_$(date +%Y-%m-%d).csv"
    fi
    
else
    echo "❌ WEEKLY MARKET DATA COLLECTION FAILED"
    echo "Check log file for details: $LOG_FILE"
    exit 1
fi

echo ""
echo "============================================================"
echo "🎯 NEXT STEPS:"
echo "• Review market_data/weekly_market_data_$(date +%Y-%m-%d).csv for raw data"
echo "• Review market_data/weekly_market_analysis_$(date +%Y-%m-%d).csv for analysis"
echo "• Check $LOG_FILE for detailed execution log"
echo "• Run python3 analyze_weekly_market_trends.py for advanced analysis"
echo "============================================================"