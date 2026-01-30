#!/bin/bash

echo "🔄 Starting Bot Setup and Launch Script..."

# Directory setup
BOT_DIR="/Users/hada/Mehul Bot/java websocket new/Trail/java new bot"
cd "$BOT_DIR"

echo "📂 Setting up directories..."
mkdir -p src/main/java/com/trading/bot/{core,market,ml,options,data,utils}
mkdir -p src/main/resources
mkdir -p src/test/java/com/trading/bot
mkdir -p src/test/resources
mkdir -p logs
mkdir -p config

# Clean up old files
echo "🧹 Cleaning up old files..."
rm -f *.class
rm -rf target/

# Download dependencies
echo "📚 Downloading dependencies..."
mkdir -p lib
cd lib
curl -O https://repo1.maven.org/maven2/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar
curl -O https://repo1.maven.org/maven2/org/java-websocket/Java-WebSocket/1.5.3/Java-WebSocket-1.5.3.jar
curl -O https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.13.4/jackson-databind-2.13.4.jar
curl -O https://repo1.maven.org/maven2/ch/qos/logback/logback-classic/1.2.11/logback-classic-1.2.11.jar
cd ..

# Compile all Java files
echo "🔨 Compiling Java files..."
javac -cp "lib/*:." -d target src/main/java/com/trading/bot/*/*.java

# Create JAR file
echo "📦 Creating JAR file..."
cd target
jar cvfe trading-bot.jar com.trading.bot.core.BotLauncher .
cd ..

echo "✅ Setup complete! Use the following commands:"
echo "📊 Run Bot Test:        ./run_bot.sh --test"
echo "🚀 Start Trading Bot:   ./run_bot.sh --start"
echo "📈 View Performance:    ./run_bot.sh --stats"