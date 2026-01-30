#!/bin/bash

# WORLD CLASS INDEX OPTIONS STRATEGIES LAUNCHER
# Uses ONLY real market data and institutional strategies

echo "🏆 WORLD CLASS INDEX OPTIONS STRATEGIES"
echo "=" | tr ' ' '=' | head -c 70 && echo
echo "✅ Based on ONLY real market data from NSE"
echo "✅ Institutional-grade strategies"
echo "✅ Professional Greeks calculation"
echo "✅ Advanced volatility analysis"
echo "✅ Market regime detection"
echo "✅ Risk management & position sizing"
echo "=" | tr ' ' '=' | head -c 70 && echo

cd "$(dirname "$0")"

# Create directories
mkdir -p classes/com/trading/bot/strategies
mkdir -p world_class_strategies_results

# Check Java installation
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 11 or higher."
    exit 1
fi

if ! command -v javac &> /dev/null; then
    echo "❌ Java compiler (javac) is not installed. Please install JDK 11 or higher."
    exit 1
fi

echo "🔧 Compiling world class strategies system..."

# Compile supporting classes first
echo "   Compiling supporting classes..."
javac -cp ".:lib/*" -d classes src/main/java/com/trading/bot/strategies/OptionsStrategy.java 2>/dev/null
javac -cp ".:lib/*" -d classes src/main/java/com/trading/bot/strategies/SupportingClasses.java 2>/dev/null
javac -cp ".:lib/*" -d classes src/main/java/com/trading/bot/strategies/RealNSEDataProvider.java 2>/dev/null

# Compile main strategies class
echo "   Compiling main strategies generator..."
javac -cp ".:lib/*:classes" -d classes src/main/java/com/trading/bot/strategies/WorldClassIndexOptionsStrategies.java

COMPILE_STATUS=$?

if [ $COMPILE_STATUS -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi

# Set JVM options for optimal performance
JVM_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC"

# Set timezone for Indian markets
export TZ=Asia/Kolkata

echo ""
echo "🚀 LAUNCHING WORLD CLASS STRATEGIES GENERATOR..."
echo "⏰ Market Timezone: Asia/Kolkata"
echo "📊 Analyzing: NIFTY, BANKNIFTY, FINNIFTY"
echo "🎯 Professional Strategy Generation"
echo ""

# Run the world class strategies generator
java $JVM_OPTS -cp ".:lib/*:classes" com.trading.bot.strategies.WorldClassIndexOptionsStrategies

JAVA_EXIT_CODE=$?

echo ""
echo "=" | tr ' ' '=' | head -c 70 && echo

if [ $JAVA_EXIT_CODE -eq 0 ]; then
    echo "✅ WORLD CLASS STRATEGIES GENERATION COMPLETED"
    echo ""
    echo "📁 Results saved in: world_class_strategies_results/"
    echo ""
    echo "🏆 GENERATED STRATEGY TYPES:"
    echo "   • Institutional Momentum Breakout Strategies"
    echo "   • Volatility Arbitrage Opportunities"
    echo "   • Delta-Neutral Income Generation"
    echo "   • Mean Reversion with Real Support/Resistance"
    echo "   • Event-Driven Options Plays"
    echo "   • Professional Arbitrage Opportunities"
    echo ""
    echo "📊 PROFESSIONAL ANALYSIS INCLUDED:"
    echo "   • Real NSE market data integration"
    echo "   • Black-Scholes Greeks calculation"
    echo "   • Advanced volatility analysis"
    echo "   • Market regime detection"
    echo "   • Risk-reward optimization"
    echo "   • Probability of profit calculation"
    echo ""
    echo "⚠️ PROFESSIONAL CRITERIA APPLIED:"
    echo "   • Minimum 60% probability of profit"
    echo "   • Minimum 1.5:1 risk-reward ratio"
    echo "   • Maximum 40% implied volatility"
    echo "   • 7-45 days to expiry range"
    echo "   • Valid Greeks requirements"
    echo ""
else
    echo "❌ WORLD CLASS STRATEGIES GENERATION FAILED"
    echo "🔍 Please check the error messages above"
    echo "💡 Common solutions:"
    echo "   • Ensure Java 11+ is installed"
    echo "   • Check internet connection for NSE data"
    echo "   • Verify market hours for live data"
    echo ""
fi

echo "=" | tr ' ' '=' | head -c 70 && echo

# Show results if available
if [ -d "world_class_strategies_results" ] && [ "$(ls -A world_class_strategies_results 2>/dev/null)" ]; then
    echo "📊 LATEST STRATEGIES SUMMARY:"
    LATEST_FILE=$(ls -t world_class_strategies_results/*.log 2>/dev/null | head -1)
    if [ -f "$LATEST_FILE" ]; then
        echo "📁 Latest file: $(basename "$LATEST_FILE")"
        STRATEGY_COUNT=$(grep -c "Strategy:" "$LATEST_FILE" 2>/dev/null || echo "Unknown")
        echo "📈 Strategies generated: $STRATEGY_COUNT"
        echo "⏰ Generated at: $(date)"
    fi
fi

echo ""
echo "🏆 WORLD CLASS OPTIONS STRATEGIES COMPLETE!"
echo "💼 Ready for professional options trading"

exit $JAVA_EXIT_CODE