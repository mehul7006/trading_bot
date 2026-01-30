#!/bin/bash

echo "🚀 STARTING TRADING BOT SERVER"
echo "Server will run continuously on port 8080"
echo ""

# Kill any existing Java processes related to trading bot
echo "🧹 Cleaning up any existing bot processes..."
pkill -f "trading.bot" 2>/dev/null || true
sleep 2

cd clean_bot

echo "📦 Compiling Trading Bot Server..."
mvn compile -q

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi

echo ""
echo "🚀 Starting Trading Bot Server on port 8080..."
echo "📊 Server will be available at: http://localhost:8080"
echo "🔗 Available endpoints:"
echo "   • http://localhost:8080/start   - Start bot analysis"
echo "   • http://localhost:8080/test    - Run test analysis" 
echo "   • http://localhost:8080/phase1  - Phase 1 only"
echo "   • http://localhost:8080/phase2  - Phase 2 only"
echo "   • http://localhost:8080/status  - System status"
echo "   • http://localhost:8080/help    - Help page"
echo ""
echo "🛑 To stop the server: Press Ctrl+C or run ./stop_bot_server.sh"
echo ""

# Start the server
java -cp "target/classes:lib/*" com.trading.bot.server.TradingBotServer 8080