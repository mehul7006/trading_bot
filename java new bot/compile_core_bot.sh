#!/bin/bash

# Compile only the core bot functionality
echo "🔧 COMPILING CORE BOT ONLY"
echo "=========================="

# Create a temporary directory for core classes only
mkdir -p target/core-classes

echo "📋 Compiling core classes only..."

# Compile only the essential classes for basic bot functionality
javac -cp "$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" \
    -d target/core-classes \
    src/main/java/com/stockbot/StockData.java \
    src/main/java/com/stockbot/StockAnalysis.java \
    src/main/java/com/stockbot/MovementPrediction.java \
    src/main/java/com/stockbot/MonitoringStats.java \
    src/main/java/com/stockbot/IndexData.java \
    src/main/java/com/stockbot/OptionRecommendation.java \
    src/main/java/com/stockbot/UpstoxApiService.java \
    src/main/java/com/stockbot/MockUpstoxApiService.java \
    src/main/java/com/stockbot/CacheManager.java \
    src/main/java/com/stockbot/BulkStockService.java \
    src/main/java/com/stockbot/StockAnalysisEngine.java \
    src/main/java/com/stockbot/OptionsAnalyzer.java \
    src/main/java/com/stockbot/IndexMovementPredictor.java \
    src/main/java/com/stockbot/AutomatedMonitoringSystem.java \
    src/main/java/com/stockbot/ShoonyaApiService.java

if [ $? -eq 0 ]; then
    echo "✅ Core classes compiled successfully!"
    
    echo "📋 Compiling main bot..."
    javac -cp "target/core-classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" \
        -d target/core-classes \
        src/main/java/com/stockbot/TelegramStockBot.java
    
    if [ $? -eq 0 ]; then
        echo "✅ Main bot compiled successfully!"
        echo "🚀 Ready to run with: ./start_core_bot.sh"
    else
        echo "❌ Main bot compilation failed"
        echo "🔧 Checking for missing dependencies..."
    fi
else
    echo "❌ Core classes compilation failed"
    echo "📋 Some classes may have dependencies on missing classes"
fi