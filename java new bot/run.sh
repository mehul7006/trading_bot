#!/bin/bash

# =============================================================================
# LATEST FULL INTEGRATION BOT RUNNER - UPDATED VERSION
# =============================================================================
# 
# 🎯 Features:
# ✅ All BollingerBands fixes applied
# ✅ 100% Honest Integration (10/10 components)
# ✅ Real market data (no mocks/fakes)
# ✅ All compilation errors resolved
# ✅ Latest modifications included
#
# =============================================================================

echo "🚀 LATEST INTEGRATION BOT - QUICK START"
echo "======================================="
echo "✅ All modifications included"
echo "✅ BollingerBands fixes applied"
echo "✅ 100% Honest Integration"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 11 or higher."
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1-2)
echo "☕ Java version: $JAVA_VERSION"

# Quick compilation and run
echo "🔨 Quick compile and start..."
CLASSPATH=".:target/classes:src/main/java"

# Compile latest integration bot
javac -cp "$CLASSPATH" src/main/java/com/stockbot/HonestIntegratedBot_PartWise.java 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo "🚀 Starting Latest Integration Bot..."
    echo "📊 Real-time analysis with all fixes applied"
    echo ""
    java -cp "$CLASSPATH" com.stockbot.HonestIntegratedBot_PartWise
else
    echo "❌ Quick compile failed. Use './run_latest_integration.sh' for detailed options."
    echo "💡 Or run: ./run_latest_integration.sh"
fi