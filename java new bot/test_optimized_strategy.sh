#!/bin/bash

# OPTIMIZED TRADING STRATEGY TEST SCRIPT
# Tests the enhanced bot with improved success rate features

echo "🚀 TESTING OPTIMIZED TRADING STRATEGY"
echo "═══════════════════════════════════════════════════════════"

# Set environment
export JAVA_HOME="/usr/lib/jvm/java-11-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"

# Navigate to bot directory
cd "$(dirname "$0")"

# Clean and compile
echo "🔧 Compiling optimized trading system..."
rm -rf target/classes/com/trading/bot/strategy/
rm -rf target/classes/com/trading/bot/core/OptimizedTradingBot.class

# Create necessary directories
mkdir -p target/classes/com/trading/bot/strategy
mkdir -p target/classes/com/trading/bot/core

# Compile the strategy
echo "📊 Compiling OptimizedTradingStrategy..."
javac -cp "lib/*:target/classes" \
    -d target/classes \
    src/main/java/com/trading/bot/strategy/OptimizedTradingStrategy.java

if [ $? -ne 0 ]; then
    echo "❌ Failed to compile OptimizedTradingStrategy"
    exit 1
fi

# Compile the bot
echo "🤖 Compiling OptimizedTradingBot..."
javac -cp "lib/*:target/classes" \
    -d target/classes \
    src/main/java/com/trading/bot/core/OptimizedTradingBot.java

if [ $? -ne 0 ]; then
    echo "❌ Failed to compile OptimizedTradingBot"
    exit 1
fi

echo "✅ Compilation successful!"

# Create test configuration
cat > test_config.properties << EOF
# Optimized Strategy Test Configuration
min.confidence.threshold=75.0
high.confidence.threshold=85.0
max.daily.risk=0.02
position.size.percent=0.05
starting.capital=100000.0

# Test instruments
test.instruments=NIFTY,BANKNIFTY,FINNIFTY,SENSEX

# Test duration (minutes)
test.duration=5

# Market hours simulation
market.open=09:15
market.close=15:30
EOF

# Run the optimized bot for testing
echo "🎯 Starting optimized trading bot test..."
echo "⏱️ Test duration: 5 minutes"
echo "📊 Monitoring: NIFTY, BANKNIFTY, FINNIFTY, SENSEX"
echo "🔥 Enhanced features active:"
echo "   ✅ Multi-indicator confluence"
echo "   ✅ Market regime detection"
echo "   ✅ Dynamic position sizing"
echo "   ✅ Risk management protocols"
echo "   ✅ Time-based filtering"
echo ""

# Start the bot with timeout
timeout 300s java -cp "lib/*:target/classes" \
    -Djava.util.logging.config.file=logging.properties \
    -Xmx1024m \
    com.trading.bot.core.OptimizedTradingBot &

BOT_PID=$!
echo "🤖 Bot started with PID: $BOT_PID"

# Monitor the bot
sleep 10
echo ""
echo "📈 MONITORING OPTIMIZED BOT PERFORMANCE..."
echo "═══════════════════════════════════════════"

# Check if logs are being created
for i in {1..30}; do
    if [ -f "optimized_signals.log" ] || [ -f "optimized_trades.log" ]; then
        echo "✅ Bot is generating signals and trades"
        break
    fi
    sleep 5
    if [ $i -eq 30 ]; then
        echo "⚠️ No activity detected after 150 seconds"
    fi
done

# Wait for test completion
sleep 180

# Stop the bot gracefully
echo ""
echo "🛑 Stopping optimized bot..."
kill -TERM $BOT_PID 2>/dev/null
sleep 5
kill -KILL $BOT_PID 2>/dev/null

echo ""
echo "📊 OPTIMIZED STRATEGY TEST RESULTS"
echo "═══════════════════════════════════════════"

# Analyze signals
if [ -f "optimized_signals.log" ]; then
    TOTAL_SIGNALS=$(wc -l < optimized_signals.log)
    BUY_SIGNALS=$(grep ",BUY," optimized_signals.log | wc -l)
    SELL_SIGNALS=$(grep ",SELL," optimized_signals.log | wc -l)
    HOLD_SIGNALS=$(grep ",HOLD," optimized_signals.log | wc -l)
    
    echo "📊 SIGNAL ANALYSIS:"
    echo "   Total Signals: $TOTAL_SIGNALS"
    echo "   BUY Signals: $BUY_SIGNALS"
    echo "   SELL Signals: $SELL_SIGNALS"
    echo "   HOLD Signals: $HOLD_SIGNALS"
    
    # Calculate average confidence
    if [ $TOTAL_SIGNALS -gt 0 ]; then
        AVG_CONFIDENCE=$(awk -F',' '{sum+=$5; count++} END {if(count>0) printf "%.1f", sum/count}' optimized_signals.log)
        echo "   Average Confidence: ${AVG_CONFIDENCE}%"
    fi
    
    echo ""
else
    echo "⚠️ No signals log found"
fi

# Analyze trades
if [ -f "optimized_trades.log" ]; then
    TOTAL_TRADES=$(wc -l < optimized_trades.log)
    WIN_TRADES=$(grep -E "(WIN|BIG_WIN)" optimized_trades.log | wc -l)
    LOSS_TRADES=$(grep -E "(LOSS|SMALL_LOSS|STOP_LOSS)" optimized_trades.log | wc -l)
    
    echo "💰 TRADE ANALYSIS:"
    echo "   Total Trades: $TOTAL_TRADES"
    echo "   Winning Trades: $WIN_TRADES"
    echo "   Losing Trades: $LOSS_TRADES"
    
    if [ $TOTAL_TRADES -gt 0 ]; then
        WIN_RATE=$(echo "scale=1; $WIN_TRADES * 100 / $TOTAL_TRADES" | bc -l)
        echo "   Win Rate: ${WIN_RATE}%"
        
        # Calculate total P&L
        TOTAL_PNL=$(awk -F',' '{sum+=$6} END {printf "%.2f", sum}' optimized_trades.log)
        echo "   Total P&L: ₹${TOTAL_PNL}"
        
        # Best and worst trades
        BEST_TRADE=$(awk -F',' 'BEGIN{max=-999999} {if($6>max) max=$6} END {printf "%.2f", max}' optimized_trades.log)
        WORST_TRADE=$(awk -F',' 'BEGIN{min=999999} {if($6<min) min=$6} END {printf "%.2f", min}' optimized_trades.log)
        echo "   Best Trade: ₹${BEST_TRADE}"
        echo "   Worst Trade: ₹${WORST_TRADE}"
        
        # Average confidence of executed trades
        AVG_TRADE_CONFIDENCE=$(awk -F',' '{sum+=$9; count++} END {if(count>0) printf "%.1f", sum/count}' optimized_trades.log)
        echo "   Avg Trade Confidence: ${AVG_TRADE_CONFIDENCE}%"
    fi
    
    echo ""
else
    echo "⚠️ No trades log found"
fi

# Strategy effectiveness analysis
echo "🔥 STRATEGY EFFECTIVENESS:"
if [ -f "optimized_trades.log" ] && [ $TOTAL_TRADES -gt 0 ]; then
    # High confidence trades analysis
    HIGH_CONF_TRADES=$(awk -F',' '$9 >= 85 {count++} END {printf "%d", count+0}' optimized_trades.log)
    HIGH_CONF_WINS=$(awk -F',' '$9 >= 85 && ($8 == "WIN" || $8 == "BIG_WIN") {count++} END {printf "%d", count+0}' optimized_trades.log)
    
    if [ $HIGH_CONF_TRADES -gt 0 ]; then
        HIGH_CONF_WIN_RATE=$(echo "scale=1; $HIGH_CONF_WINS * 100 / $HIGH_CONF_TRADES" | bc -l)
        echo "   High Confidence (≥85%) Win Rate: ${HIGH_CONF_WIN_RATE}% (${HIGH_CONF_WINS}/${HIGH_CONF_TRADES})"
    fi
    
    # Medium confidence trades analysis
    MED_CONF_TRADES=$(awk -F',' '$9 >= 75 && $9 < 85 {count++} END {printf "%d", count+0}' optimized_trades.log)
    MED_CONF_WINS=$(awk -F',' '$9 >= 75 && $9 < 85 && ($8 == "WIN" || $8 == "BIG_WIN") {count++} END {printf "%d", count+0}' optimized_trades.log)
    
    if [ $MED_CONF_TRADES -gt 0 ]; then
        MED_CONF_WIN_RATE=$(echo "scale=1; $MED_CONF_WINS * 100 / $MED_CONF_TRADES" | bc -l)
        echo "   Medium Confidence (75-84%) Win Rate: ${MED_CONF_WIN_RATE}% (${MED_CONF_WINS}/${MED_CONF_TRADES})"
    fi
    
    # Risk management effectiveness
    STOP_LOSSES=$(grep "STOP_LOSS" optimized_trades.log | wc -l)
    if [ $TOTAL_TRADES -gt 0 ]; then
        STOP_LOSS_RATE=$(echo "scale=1; $STOP_LOSSES * 100 / $TOTAL_TRADES" | bc -l)
        echo "   Stop Loss Rate: ${STOP_LOSS_RATE}% (${STOP_LOSSES}/${TOTAL_TRADES})"
    fi
fi

echo ""
echo "🎯 OPTIMIZATION RECOMMENDATIONS:"
if [ -f "optimized_trades.log" ] && [ $TOTAL_TRADES -gt 0 ]; then
    if (( $(echo "$WIN_RATE >= 70" | bc -l) )); then
        echo "   ✅ Excellent performance! Strategy is working well"
        echo "   💡 Consider increasing position sizes for high-confidence trades"
    elif (( $(echo "$WIN_RATE >= 60" | bc -l) )); then
        echo "   ✅ Good performance! Above market average"
        echo "   💡 Fine-tune confidence thresholds for better filtering"
    elif (( $(echo "$WIN_RATE >= 50" | bc -l) )); then
        echo "   ⚠️ Average performance. Room for improvement"
        echo "   💡 Review technical indicators and add more confluence factors"
    else
        echo "   ❌ Below average performance. Strategy needs optimization"
        echo "   💡 Increase minimum confidence threshold to 80%"
        echo "   💡 Add more technical indicators for better signal quality"
    fi
else
    echo "   📊 Insufficient data for recommendations"
    echo "   💡 Run longer tests to gather more performance data"
fi

echo ""
echo "🚀 NEXT STEPS:"
echo "   1. If win rate ≥ 65%: Deploy to paper trading"
echo "   2. If win rate 55-64%: Fine-tune parameters"
echo "   3. If win rate < 55%: Review and enhance strategy"
echo ""
echo "📝 Log files generated:"
echo "   - optimized_signals.log (all signals)"
echo "   - optimized_trades.log (executed trades)"
echo ""
echo "✅ OPTIMIZED STRATEGY TEST COMPLETED"
echo "═══════════════════════════════════════════════════════════"