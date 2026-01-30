#!/bin/bash

echo "🚀 Starting Simple Bot Manager - Unified Command Handler"
echo "📊 Managing 153 Java Trading Functions"
echo "🔧 Clean Architecture - Zero Dependency Conflicts"
echo ""

cd "$(dirname "$0")"

# Quick compile
echo "Compiling..."
mvn clean compile -q

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
    echo ""
    echo "🎯 Starting Unified Bot Command Handler..."
    echo "Type 'help' for all commands, 'exit' to quit"
    echo ""
    
    # Start the simple bot manager
    java -cp "target/classes:lib/*" com.trading.bot.core.SimpleBotManager "$@"
else
    echo "❌ Compilation failed"
    mvn clean compile
fi