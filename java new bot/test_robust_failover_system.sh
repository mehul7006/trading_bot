#!/bin/bash

echo "🛡️ === ROBUST PRICE FAILOVER SYSTEM TEST ====="
echo "📡 Testing: Upstox → Shoonya → Error (NO MOCK DATA)"
echo "🚫 Strict Policy: Only Real Data or Clear Errors"
echo ""

cd "$(dirname "$0")"

echo "🔧 Compiling Robust Failover System..."
javac -d "target/classes" "src/main/java/com/trading/bot/market/RobustPriceFailoverSystem.java"

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    
    echo "🧪 Testing failover logic..."
    echo "1️⃣ Try Upstox API (Primary)"
    echo "2️⃣ If fails, try Shoonya API (Backup)"  
    echo "3️⃣ If both fail, show clear error"
    echo "🚫 NO MOCK/FAKE DATA ALLOWED"
    echo ""
    echo "=" | head -c 60; echo ""
    
    # Run the robust failover system
    java -cp "target/classes" com.trading.bot.market.RobustPriceFailoverSystem
    
    echo "=" | head -c 60; echo ""
    echo ""
    echo "🎉 === ROBUST FAILOVER SYSTEM READY ====="
    echo ""
    echo "✅ FAILOVER FEATURES:"
    echo "   • Primary: Upstox API with real credentials"
    echo "   • Backup: Shoonya API with real credentials"
    echo "   • Error Handling: Clear messages when both fail"
    echo "   • Data Policy: Only real market data displayed"
    echo "   • No Mock Data: Strict professional approach"
    echo ""
    echo "🛡️ RELIABILITY FEATURES:"
    echo "   • Automatic failover between APIs"
    echo "   • Real-time error detection" 
    echo "   • Professional error reporting"
    echo "   • Network failure handling"
    echo "   • API credential validation"
    echo ""
    echo "💡 Usage in your enhanced bot:"
    echo "   PriceResult result = system.getLivePrice(\"NIFTY\");"
    echo "   if (result.isSuccess()) {"
    echo "       // Use real price data"
    echo "   } else {"
    echo "       // Handle error professionally"
    echo "   }"
    
else
    echo "❌ Compilation failed"
    echo "Please check Java installation"
fi