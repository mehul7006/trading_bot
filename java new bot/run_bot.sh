#!/bin/bash

BOT_DIR="/Users/hada/Mehul Bot/java websocket new/Trail/java new bot"
cd "$BOT_DIR"

function start_bot() {
    echo "🚀 Starting Trading Bot..."
    java -cp "lib/*:target/trading-bot.jar" com.trading.bot.core.BotLauncher
}

function run_test() {
    echo "📊 Running Bot Tests..."
    java -cp "lib/*:target/trading-bot.jar" com.trading.bot.core.BotLauncher --test
}

function show_stats() {
    echo "📈 Bot Performance Statistics"
    echo "----------------------------------------"
    if [ -f "logs/performance_summary.txt" ]; then
        cat logs/performance_summary.txt
    else
        echo "No performance data available yet."
    fi
}

case "$1" in
    --start)
        start_bot
        ;;
    --test)
        run_test
        ;;
    --stats)
        show_stats
        ;;
    *)
        echo "Usage: $0 [--start|--test|--stats]"
        echo "  --start : Start the trading bot"
        echo "  --test  : Run bot tests"
        echo "  --stats : Show performance statistics"
        exit 1
        ;;
esac