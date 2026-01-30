#!/bin/bash

# WEEKLY MARKET DATA FETCHER - OFFICIAL SOURCES
# Fetches last week's market movement data from NSE, BSE, and other official APIs

echo "============================================================"
echo "WEEKLY MARKET DATA FETCHER - OFFICIAL SOURCES"
echo "============================================================"
echo "Starting weekly market data collection from official sources..."
echo "• NSE India (National Stock Exchange)"
echo "• BSE India (Bombay Stock Exchange)" 
echo "• Yahoo Finance (Backup)"
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
CLASSPATH="src/main/java:lib/*:target/classes"

# Add Jackson libraries for JSON parsing
if [ ! -f "lib/jackson-databind-2.13.4.jar" ]; then
    echo "📦 Downloading required Jackson JSON libraries..."
    wget -q -O lib/jackson-core-2.13.4.jar "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.13.4/jackson-core-2.13.4.jar"
    wget -q -O lib/jackson-databind-2.13.4.jar "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.13.4/jackson-databind-2.13.4.jar"
    wget -q -O lib/jackson-annotations-2.13.4.jar "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.13.4/jackson-annotations-2.13.4.jar"
    echo "✅ Jackson libraries downloaded"
fi

# Compile the Java source
echo "🔧 Compiling WeeklyMarketDataFetcher..."
javac -cp "$CLASSPATH" src/main/java/com/trading/bot/market/WeeklyMarketDataFetcher.java

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
LOG_FILE="logs/weekly_market_fetch_$(date +%Y%m%d_%H%M%S).log"

# Run the Java application and capture output
java -cp "$CLASSPATH" com.trading.bot.market.WeeklyMarketDataFetcher 2>&1 | tee "$LOG_FILE"

if [ $? -eq 0 ]; then
    echo "============================================================"
    echo "✅ WEEKLY MARKET DATA COLLECTION COMPLETED SUCCESSFULLY"
    echo "============================================================"
    
    # Move generated files to market_data directory
    if [ -f "weekly_market_data_$(date +%Y-%m-%d).csv" ]; then
        mv weekly_market_data_$(date +%Y-%m-%d).csv market_data/
        echo "📊 Market data CSV moved to market_data/ directory"
    fi
    
    if [ -f "weekly_market_analysis_$(date +%Y-%m-%d).csv" ]; then
        mv weekly_market_analysis_$(date +%Y-%m-%d).csv market_data/
        echo "📈 Market analysis CSV moved to market_data/ directory"
    fi
    
    # Display file information
    echo ""
    echo "📁 Generated Files:"
    ls -la market_data/weekly_market_*$(date +%Y-%m-%d).csv 2>/dev/null || echo "No CSV files found"
    
    echo ""
    echo "📋 Log File: $LOG_FILE"
    
    # Quick summary from log
    echo ""
    echo "📊 QUICK SUMMARY:"
    echo "============================================================"
    grep -E "(WEEKLY ANALYSIS|Successfully fetched|Market Sentiment)" "$LOG_FILE" | tail -20
    
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
echo "• Run ./analyze_weekly_market_trends.py for advanced analysis"
echo "============================================================"