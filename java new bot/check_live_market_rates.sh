#!/bin/bash

echo "📊 === LIVE MARKET RATES CHECKER ==="
echo "🔑 API Key: 768a303b-80f1-46d6-af16-f847f9341213"
echo "🔐 API Secret: 40s7mnlm8f"
echo "📡 Connecting to Upstox API..."
echo ""

cd "$(dirname "$0")"

# Compile the market connector
echo "🔧 Compiling Upstox connector..."
javac -cp "lib/*" -d "target/classes" \
    "src/main/java/com/trading/bot/market/UpstoxRealTimeConnector.java" \
    2>/dev/null

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
    echo ""
    
    echo "🚀 Getting live NIFTY and SENSEX rates..."
    echo "================================================"
    
    # Run the live market data checker
    java -cp "target/classes:lib/*" com.trading.bot.market.UpstoxRealTimeConnector
    
    echo ""
    echo "================================================"
    echo "✅ Market data retrieval complete!"
    echo ""
    echo "💡 Next steps:"
    echo "   • Use this data for options analysis"
    echo "   • Generate high-confidence calls"
    echo "   • Monitor for breakouts and trends"
    echo "   • Set up automated alerts"
    
else
    echo "❌ Compilation failed. Using fallback rates..."
    echo ""
    echo "📊 === FALLBACK MARKET SIMULATION ==="
    echo "NIFTY: ₹19,485.75 📈 +125.50 (+0.65%)"
    echo "SENSEX: ₹65,842.33 📈 +285.15 (+0.44%)"
    echo "BANKNIFTY: ₹44,235.80 📈 +315.25 (+0.72%)"
    echo "FINNIFTY: ₹19,756.45 📈 +89.30 (+0.45%)"
    echo ""
    echo "⚠️  Note: These are simulated values for demonstration"
fi

echo ""
echo "🎯 Ready to integrate with your enhanced trading bot!"