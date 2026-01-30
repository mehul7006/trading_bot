#!/bin/bash

# ===================================================================
# START RESPONSE TIME BASED API MANAGER
# ===================================================================

echo "🚀 === STARTING RESPONSE TIME BASED API MANAGER ==="
echo "📈 Primary: Upstox | Failover: Response Time Optimized"
echo "⚡ Features: Health Monitoring, Auto Recovery, Performance Stats"
echo ""

# Set environment variables
export JAVA_OPTS="-Xmx2G -Xms1G -XX:+UseG1GC"
export CLASSPATH=".:src/main/java:lib/*:classes"

# Create necessary directories
mkdir -p logs
mkdir -p config
mkdir -p performance_reports

# Compile the Response Time API Manager
echo "🔧 Compiling Response Time Based API Manager..."
javac -cp "$CLASSPATH" src/main/java/com/trading/bot/market/ResponseTimeBasedAPIManager.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi

# Start the API Manager
echo ""
echo "🚀 Starting Response Time Based API Manager..."
echo "📊 Real-time performance monitoring enabled"
echo "🔄 Auto-failover based on response times"
echo ""

# Log file with timestamp
LOG_FILE="logs/api_manager_$(date +%Y%m%d_%H%M%S).log"

# Start with comprehensive logging
java $JAVA_OPTS \
    -cp "$CLASSPATH" \
    -Djava.util.logging.config.file=src/main/resources/log4j2.xml \
    -Dapi.config.file=src/main/resources/api-config.properties \
    com.trading.bot.market.ResponseTimeBasedAPIManager \
    2>&1 | tee "$LOG_FILE"

echo ""
echo "📊 === API MANAGER SESSION COMPLETE ==="
echo "📝 Log file: $LOG_FILE"
echo "📈 Performance stats logged"