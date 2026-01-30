#!/bin/bash

# Script to organize bot files and set up proper structure

# Create directory structure
mkdir -p src/main/java/com/trading/bot/{core,market,ml,options,data,utils}
mkdir -p src/main/resources
mkdir -p src/test/java/com/trading/bot
mkdir -p src/test/resources

# Define source directories
CORE_DIR="src/main/java/com/trading/bot/core"
MARKET_DIR="src/main/java/com/trading/bot/market"
ML_DIR="src/main/java/com/trading/bot/ml"
OPTIONS_DIR="src/main/java/com/trading/bot/options"
DATA_DIR="src/main/java/com/trading/bot/data"
UTILS_DIR="src/main/java/com/trading/bot/utils"

# Move and organize existing files
mv MarketData.java $DATA_DIR/
mv MLMarketAnalyzer.java $ML_DIR/
mv OptionsCalculator.java $OPTIONS_DIR/
mv RealTimeDataCollector.java $MARKET_DIR/
mv EnhancedOptionsCallGenerator.java $OPTIONS_DIR/
mv HonestBotTester.java $CORE_DIR/

# Create log directory
mkdir -p logs

# Set proper permissions
chmod 755 src/main/java/com/trading/bot
find . -type d -exec chmod 755 {} \;
find . -type f -name "*.java" -exec chmod 644 {} \;

# Create configuration directory
mkdir -p config

echo "Bot file structure organized successfully!"