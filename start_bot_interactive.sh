#!/bin/bash

echo "🚀 STARTING MASTER TRADING BOT IN INTERACTIVE MODE"
echo ""

cd clean_bot

echo "Compiling..."
mvn compile -q

echo ""
echo "=== MASTER TRADING BOT INTERACTIVE MODE ==="
echo ""
echo "Available Commands:"
echo "  help     - Show all commands"
echo "  mode     - Set mode (PHASE1/PHASE2/BOTH)"
echo "  status   - Show system status"
echo "  test     - Run analysis with sample data"
echo "  exit     - Shutdown bot"
echo ""
echo "Starting interactive session..."
echo ""

# Start the bot in interactive mode
java -cp "target/classes:lib/*" com.trading.bot.core.MasterTradingBot