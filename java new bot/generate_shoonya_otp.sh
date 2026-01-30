#!/bin/bash

echo "🔐 === SHOONYA OTP GENERATOR ==="
echo "📱 Step-by-step authentication process"
echo "🎯 First asks credentials, then generates OTP from official site"
echo ""

cd "$(dirname "$0")"

echo "🔧 Compiling Shoonya OTP Generator..."
javac -d "target/classes" "src/main/java/com/trading/bot/market/ShoonyaOTPGenerator.java"

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    
    echo "📝 === AUTHENTICATION PROCESS ==="
    echo "🔐 Step 1: You'll enter your Client Code"
    echo "🔒 Step 2: You'll enter your Trading Password"  
    echo "📱 Step 3: OTP will be generated from official Finvasia site"
    echo "📞 Step 4: OTP will be sent to your registered mobile"
    echo "🔢 Step 5: You'll enter the received OTP"
    echo "✅ Step 6: Authentication completes with session token"
    echo ""
    
    echo "🎯 READY TO START STEP-BY-STEP AUTHENTICATION"
    echo "📱 Keep your mobile ready to receive OTP"
    echo ""
    
    read -p "🚀 Press Enter to start the authentication process..."
    echo ""
    
    echo "=" | head -c 80; echo ""
    echo "🔐 STARTING STEP-BY-STEP OTP AUTHENTICATION"
    echo "=" | head -c 80; echo ""
    
    # Run the OTP generator
    java -cp "target/classes" com.trading.bot.market.ShoonyaOTPGenerator
    
    echo ""
    echo "=" | head -c 80; echo ""
    
    echo "🎯 === OTP GENERATOR RESULTS ==="
    echo ""
    echo "✅ IF AUTHENTICATION SUCCEEDED:"
    echo "   • Your credentials are correct and verified"
    echo "   • OTP was successfully generated from official site"
    echo "   • Session token received for live market data"
    echo "   • Ready to fetch real SENSEX, NIFTY prices"
    echo ""
    echo "❌ IF AUTHENTICATION FAILED:"
    echo "   • Check if Client Code is correct (e.g., fn144243)"
    echo "   • Verify Trading Password is accurate"  
    echo "   • Ensure OTP was entered within 5 minutes"
    echo "   • Try generating fresh OTP if expired"
    echo ""
    echo "📱 OTP TROUBLESHOOTING:"
    echo "   • OTP sent to registered mobile number only"
    echo "   • Check SMS/WhatsApp from Finvasia"
    echo "   • OTP is 6-digit number"
    echo "   • Valid for 5 minutes from generation"
    echo ""
    echo "💡 NEXT STEPS IF SUCCESSFUL:"
    echo "   • Session token can be used for live price fetching"
    echo "   • Integrate with your trading bot"
    echo "   • Start getting real market data"
    echo "   • No more mock/fake data needed"
    
else
    echo "❌ Compilation failed"
    echo "Please check Java installation and try again"
fi

echo ""
echo "🎉 Shoonya OTP Generator ready!"
echo "🔐 Step-by-step authentication implemented"
echo "📱 Official site OTP generation"