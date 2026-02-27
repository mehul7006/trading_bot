#!/bin/bash

echo "🛑 Stopping any existing bot processes..."
pkill -f "java.*Bot"
sleep 2

echo "🧹 Cleaning up..."
cd "/Users/hada/Mehul Bot/java websocket new/Trail/clean_bot"
mvn clean

echo "🏗️ Building the bot..."
mvn compile

echo "🚀 Starting the bot..."
java -cp target/classes com.trading.bot.main.TelegramBot

echo "✅ Bot restart complete"