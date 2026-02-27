#!/bin/bash

echo "🏦 STARTING PHASE 3 INSTITUTIONAL TRADING BOT"
echo "============================================="
echo

# Check if Phase 3 is compiled
if [ ! -f "target/classes/com/trading/bot/core/Phase3IntegratedBot.class" ]; then
    echo "⚠️  Phase 3 not compiled. Running deployment first..."
    ./deploy_phase3_production.sh
    if [ $? -ne 0 ]; then
        echo "❌ Deployment failed. Exiting."
        exit 1
    fi
fi

echo "🚀 Launching Phase 3 Institutional Trading Bot..."
echo
echo "📊 Smart Money Features Active:"
echo "   ✅ Order Block Detection"
echo "   ✅ Fair Value Gap Analysis"
echo "   ✅ Liquidity Analysis"
echo "   ✅ Institutional Grade Classification"
echo
echo "Starting bot in 3 seconds..."
sleep 1
echo "2..."
sleep 1  
echo "1..."
sleep 1

# Launch Phase 3 bot
java -cp "lib/*:target/classes" com.trading.bot.test.Phase3TestRunner

echo
echo "🏦 Phase 3 Institutional Trading Bot session completed."