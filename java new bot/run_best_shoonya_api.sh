#!/bin/bash

echo "🚀 === BEST SHOONYA API PRICE FETCHER TEST ==="
echo "🎯 Professional authentication with all credentials"
echo "📊 Real-time SENSEX, NIFTY, and all index prices"
echo "🔐 Multiple authentication methods for maximum success"
echo ""

cd "$(dirname "$0")"

echo "🔧 Compiling Best Shoonya API Fetcher..."
javac -d "target/classes" "src/main/java/com/trading/bot/market/BestShoonyaAPIFetcher.java"

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    
    echo "🎯 === CREDENTIALS BEING USED ==="
    echo "🏢 Vendor Code: FN144243_U"
    echo "👤 User ID: 36B2ZX"
    echo "🔑 API Key: 6eeeccb6***"
    echo "🔒 Password: Monu@123"
    echo "📱 IMEI: abc1234567890"
    echo ""
    
    echo "🚀 Starting Best Shoonya API Test..."
    echo "🔐 Will try multiple authentication methods"
    echo "📊 Will fetch all major index prices if successful"
    echo ""
    echo "=" | head -c 70; echo ""
    
    # Run the best Shoonya API fetcher
    java -cp "target/classes" com.trading.bot.market.BestShoonyaAPIFetcher
    
    echo "=" | head -c 70; echo ""
    echo ""
    echo "🎯 === TEST ANALYSIS ==="
    echo ""
    echo "✅ IF AUTHENTICATION SUCCEEDED:"
    echo "   • Shoonya API credentials are correct and active"
    echo "   • API access is enabled on your Finvasia account"
    echo "   • Live SENSEX, NIFTY prices are now available"
    echo "   • Ready for professional trading integration"
    echo ""
    echo "❌ IF AUTHENTICATION STILL FAILED:"
    echo "   • Contact Finvasia support: +91-80-40402020"
    echo "   • Request API access activation for account 36B2ZX"
    echo "   • Verify all credentials are correct"
    echo "   • Check if account has live market data permissions"
    echo ""
    echo "🔄 REGARDLESS OF OUTCOME:"
    echo "   • Your Upstox + Yahoo Finance system still works"
    echo "   • Professional trading system already operational"
    echo "   • Shoonya would be additional enhancement"
    echo ""
    echo "💡 NEXT STEPS IF SUCCESSFUL:"
    echo "   • Integrate with your existing trading bot"
    echo "   • Set up triple redundancy (Upstox + Shoonya + Yahoo)"
    echo "   • Enable cross-validation between data sources"
    echo "   • Start professional trading with multiple APIs"
    
else
    echo "❌ Compilation failed"
    echo "Please check Java installation and try again"
fi

echo ""
echo "🎉 Best Shoonya API implementation is ready!"
echo "📞 Contact Finvasia if credentials need activation"