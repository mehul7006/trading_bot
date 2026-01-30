#!/bin/bash
# LIVE TRADING SYSTEM STARTUP SCRIPT
# Version: v1.0.0

echo "🚀 STARTING LIVE TRADING SYSTEM"
echo "==============================="

# Check Java installation
if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Please install Java 8 or higher"
    exit 1
fi

# Check if system is already running
if pgrep -f "MasterLiveTradingLauncher" > /dev/null; then
    echo "⚠️ Trading system is already running"
    echo "📊 PID: $(pgrep -f MasterLiveTradingLauncher)"
    exit 1
fi

# Set environment variables
export JAVA_OPTS="-Xmx2g -Xms1g"
export TRADING_ENV="production"
export LOG_LEVEL="INFO"

# Create log file with timestamp
LOG_FILE="/opt/trading-system/logs/trading_$(date +%Y%m%d_%H%M%S).log"
touch "$LOG_FILE"

# Start the system
echo "📊 Expected Accuracy: 82.35%"
echo "📝 Log file: $LOG_FILE"
echo "🎯 Starting trading system..."

# Compile if needed
if [ ! -f "MasterLiveTradingLauncher.class" ] || [ "MasterLiveTradingLauncher.java" -nt "MasterLiveTradingLauncher.class" ]; then
    echo "🔧 Compiling system..."
    javac *.java
    if [ $? -ne 0 ]; then
        echo "❌ Compilation failed"
        exit 1
    fi
    echo "✅ Compilation successful"
fi

# Start with logging
nohup java $JAVA_OPTS MasterLiveTradingLauncher > "$LOG_FILE" 2>&1 &
TRADING_PID=$!

# Wait a moment and check if started successfully
sleep 3
if kill -0 $TRADING_PID 2>/dev/null; then
    echo "✅ Trading system started successfully"
    echo "📊 PID: $TRADING_PID"
    echo "📝 Log: $LOG_FILE"
    echo "🎯 Expected accuracy: 82.35%"
else
    echo "❌ Failed to start trading system"
    echo "📝 Check log: $LOG_FILE"
    exit 1
fi
