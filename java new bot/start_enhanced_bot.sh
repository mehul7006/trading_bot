#!/bin/bash

echo "Starting Enhanced Trading Bot with ML Components..."

# Set up environment variables
export JAVA_OPTS="-Xmx4g -XX:+UseG1GC"
export BOT_ENV="production"

# Compile Java files
echo "Compiling Java sources..."
find . -name "*.java" > sources.txt
javac -cp "lib/*:." @sources.txt
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# Start Python ML service
echo "Starting ML Analysis Service..."
"/Users/hada/Mehul Bot/java websocket new/Trail/.venv/bin/python" analyze_trading_performance.py &
ML_PID=$!

# Wait for ML service to initialize
sleep 5

# Start main bot
echo "Starting main trading bot..."
java -cp "lib/*:." com.trading.bot.main.BotLauncher

# Cleanup
kill $ML_PID