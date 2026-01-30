#!/bin/bash

echo "=== Starting Unified Trading Bot System ==="
echo "Total Java Functions: 153"
echo "Unified Command Handler: Enabled"
echo "LLM Response Generation: Fixed"

cd "$(dirname "$0")"

# Compile the project
echo "Compiling bot system..."
mvn clean compile -q

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful"
    
    # Start the All-in-One Bot Launcher
    echo "Starting All-in-One Bot Launcher..."
    java -cp "target/classes:lib/*" com.trading.bot.core.AllInOneBotLauncher "$@"
    
else
    echo "✗ Compilation failed"
    echo "Running compilation with detailed output..."
    mvn clean compile
fi