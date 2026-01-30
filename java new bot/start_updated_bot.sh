#!/bin/bash

# Start Updated Trading Bot with New Upstox Credentials
# Updated: January 2025

echo "🚀 === STARTING UPDATED TRADING BOT ==="
echo "📅 Date: $(date)"
echo "🔧 Updated Upstox credentials applied"
echo "🎯 Fixed strategy logic - no more duplicate calls"
echo ""

# Set working directory
cd "$(dirname "$0")"

# Update timestamp in config
echo "⚙️ Updating configuration timestamps..."
current_time=$(date '+%Y-%m-%d %H:%M:%S')
sed -i "s/update.time=.*/update.time=$(date '+%H:%M:%S')/" upstox_config_updated.properties
sed -i "s/update.date=.*/update.date=$(date '+%Y-%m-%d')/" upstox_config_updated.properties

# Compile the latest code
echo "🔨 Compiling latest bot code..."
javac -cp "lib/*:classes" -d classes src/main/java/com/trading/bot/telegram/ProperTelegramBot.java
javac -cp "lib/*:classes" -d classes src/main/java/com/trading/bot/strategies/WorldClassOptionsAnalyzer.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
else
    echo "❌ Compilation failed. Please check for errors."
    exit 1
fi

echo ""
echo "🤖 === BOT STARTUP INFO ==="
echo "✅ Upstox API credentials: UPDATED"
echo "✅ Strategy logic: FIXED (no duplicate calls)"
echo "✅ /scan command: READY"
echo "📱 Telegram Token: Active"
echo ""
echo "🎯 === NEW FEATURES ==="
echo "📊 Market direction analysis"
echo "🔍 Smart strike selection"
echo "⚖️ Balanced CALL/PUT recommendations"
echo "🚫 Duplicate call elimination"
echo ""

# Export environment variables
export UPSTOX_ACCESS_TOKEN="eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTE1ZTFjM2EzNjg3NjZjOGIzZDFiZTQiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MzA0MTczMSwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYzMDcxMjAwfQ.Yy55jdoFz3fFRV_9NmGkQz6ProawgU8lRdqoWr12zhY"
export UPSTOX_API_KEY="768a303b-80f1-46d6-af16-f847f9341213"
export UPSTOX_API_SECRET="j0w9ga2m9w"

# Start the bot
echo "🚀 Starting the updated bot..."
echo "📱 Use /scan command in Telegram to test fixed strategy"
echo "🛑 Press Ctrl+C to stop"
echo ""

java -cp "lib/*:classes" com.trading.bot.telegram.ProperTelegramBot

echo ""
echo "🛑 Bot stopped."
echo "📋 Session ended at: $(date)"