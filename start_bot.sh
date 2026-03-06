#!/bin/bash

echo "Starting Enhanced Trading Bot..."

# Set up environment variables
export JAVA_OPTS="-Xmx4g -XX:+UseG1GC"
export BOT_ENV="production"

# Create directories
mkdir -p classes
mkdir -p lib
mkdir -p logs

# Download dependencies if needed
if [ ! -f "lib/websocket-client.jar" ]; then
    echo "Downloading dependencies..."
    curl -L -o lib/websocket-client.jar https://repo1.maven.org/maven2/org/java-websocket/Java-WebSocket/1.5.3/Java-WebSocket-1.5.3.jar
    curl -L -o lib/slf4j-api.jar https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.7/slf4j-api-2.0.7.jar
    curl -L -o lib/logback-core.jar https://repo1.maven.org/maven2/ch/qos/logback/logback-core/1.4.11/logback-core-1.4.11.jar
    curl -L -o lib/logback-classic.jar https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.4.11/logback-classic-1.4.11.jar
    curl -L -o lib/commons-math3.jar https://repo1.maven.org/maven2/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar
fi

# Compile Java files
echo "Compiling Java sources..."
CLASSPATH="classes:$(find lib -name '*.jar' | tr '\n' ':')."
find src/main/java -name "*.java" > sources.txt
javac -cp "$CLASSPATH" -d classes @sources.txt
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

# Copy resources
mkdir -p classes
cp -r src/main/resources/* classes/

# Start ML service in background if needed
if [ -f "src/main/python/analyze_trading_performance.py" ]; then
    echo "Starting ML Analysis Service..."
    "/Users/hada/Mehul Bot/java websocket new/Trail/.venv/bin/python" src/main/python/analyze_trading_performance.py &
    ML_PID=$!
    # Wait for ML service to initialize
    sleep 5
fi

# Start main bot
echo "Starting main trading bot..."
java -cp "classes:lib/*" com.trading.bot.core.BotLauncher

# Cleanup
if [ ! -z "$ML_PID" ]; then
    kill $ML_PID
fi