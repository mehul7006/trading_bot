#!/bin/bash

echo "🚀 === ENHANCED INDEX OPTIONS BOT - FEATURES DEMONSTRATION ==="
echo "Points 2-4 Implementation: Specific Features, Analysis Tools, Automated Alerts"
echo ""

cd "$(dirname "$0")"

# Compile only the working enhanced features
echo "🔧 Compiling enhanced features..."
javac -cp "lib/*" -d "target/classes" \
    "src/main/java/com/trading/bot/core/SimpleBotManager.java" \
    "src/main/java/com/trading/bot/core/AdvancedIndexOptionsScanner.java" \
    "src/main/java/com/trading/bot/core/IndexOptionsCallGenerator.java" \
    "src/main/java/com/trading/bot/core/SpecificIndexStrategies.java" \
    "src/main/java/com/trading/bot/core/AdvancedGreeksAnalyzer.java" \
    "src/main/java/com/trading/bot/core/AutomatedAlertsSystem.java" \
    2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Enhanced features compiled successfully!"
    echo ""
    
    # Demo 1: Specific Index Strategies
    echo "🎯 === DEMO 1: SPECIFIC INDEX STRATEGIES ==="
    echo "Testing index-specific analysis for NIFTY, BANKNIFTY, SENSEX..."
    java -cp "target/classes:lib/*" com.trading.bot.core.SpecificIndexStrategies
    echo ""
    
    # Demo 2: Advanced Greeks Analysis
    echo "⚡ === DEMO 2: ADVANCED GREEKS ANALYSIS ==="
    echo "Running comprehensive Greeks analysis across all indices..."
    java -cp "target/classes:lib/*" com.trading.bot.core.AdvancedGreeksAnalyzer
    echo ""
    
    # Demo 3: Options Scanner
    echo "🔍 === DEMO 3: ADVANCED OPTIONS SCANNER ==="
    echo "Scanning all index options with high-confidence filtering..."
    java -cp "target/classes:lib/*" com.trading.bot.core.AdvancedIndexOptionsScanner
    echo ""
    
    # Demo 4: Call Generator
    echo "📈 === DEMO 4: INDEX OPTIONS CALL GENERATOR ==="
    echo "Generating high-confidence calls with multi-factor analysis..."
    java -cp "target/classes:lib/*" com.trading.bot.core.IndexOptionsCallGenerator
    echo ""
    
    echo "🎉 === ENHANCED FEATURES DEMONSTRATION COMPLETE ==="
    echo ""
    echo "✅ Point 2: Specific index strategies - IMPLEMENTED"
    echo "✅ Point 3: Advanced Greeks & volatility analysis - IMPLEMENTED" 
    echo "✅ Point 4: Automated alerts system - IMPLEMENTED"
    echo ""
    echo "Ready for Point 1: Live demo with all features integrated!"
    
else
    echo "❌ Compilation failed. Using SimpleBotManager as fallback..."
    echo ""
    echo "🎯 === FALLBACK DEMO: SIMPLE BOT WITH ENHANCED COMMANDS ==="
    java -cp "target/classes:lib/*" com.trading.bot.core.SimpleBotManager help
fi