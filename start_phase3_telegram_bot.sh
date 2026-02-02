#!/bin/bash

echo "🏦 STARTING PHASE 3 INSTITUTIONAL TELEGRAM BOT"
echo "==============================================="
echo

# Set colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check if we're in the right directory
if [ ! -f "target/classes/com/trading/bot/telegram/Phase3TelegramBot.class" ]; then
    print_warning "Phase3TelegramBot not compiled. Compiling now..."
    
    # Ensure all dependencies are compiled first
    echo "Compiling dependencies..."
    javac -cp "lib/*" -d target/classes src/main/java/com/trading/bot/market/SimpleMarketData.java 2>/dev/null
    javac -cp "lib/*:target/classes" -d target/classes src/main/java/com/trading/bot/smartmoney/*.java 2>/dev/null
    javac -cp "lib/*:target/classes" -d target/classes src/main/java/com/trading/bot/core/Phase3IntegratedBot.java 2>/dev/null
    javac -cp "lib/*:target/classes" -d target/classes src/main/java/com/trading/bot/telegram/Phase3TelegramBot.java 2>/dev/null
    
    if [ ! -f "target/classes/com/trading/bot/telegram/Phase3TelegramBot.class" ]; then
        print_error "Failed to compile Phase3TelegramBot. Please check dependencies."
        exit 1
    fi
fi

print_status "Phase 3 Telegram Bot compiled and ready"

# Display bot information
echo
print_info "Bot Features:"
echo "   🧠 Phase 1: Enhanced Technical + Machine Learning"
echo "   📊 Phase 2: Multi-Timeframe + Advanced Indicators" 
echo "   🏦 Phase 3: Smart Money Concepts + Institutional Analysis"
echo
echo "📱 Smart Money Features:"
echo "   • Order Block Detection"
echo "   • Fair Value Gap Analysis"
echo "   • Liquidity Analysis"
echo "   • Institutional Grade Classification"
echo

# Display commands
print_info "Available Telegram Commands:"
echo "   /start - Activate bot and show welcome message"
echo "   /analyze - Full institutional market analysis"
echo "   /nifty - NIFTY50 Smart Money analysis"
echo "   /sensex - SENSEX institutional analysis" 
echo "   /banknifty - BANKNIFTY liquidity analysis"
echo "   /auto_on - Start continuous analysis"
echo "   /auto_off - Stop continuous analysis"
echo "   /status - Check bot status"
echo "   /help - Show all commands"
echo

# Bot token information
print_info "Bot Configuration:"
echo "   📱 Bot Token: 7921964521:AAG... (configured)"
echo "   🔄 Polling: Every 2 seconds"
echo "   💬 Response: Immediate to /start command"
echo "   🏦 Engine: Phase 3 Integrated Bot"
echo

print_warning "IMPORTANT: Make sure to send /start command in Telegram to activate the bot!"
echo

# Start the bot
print_status "Starting Phase 3 Telegram Bot..."
echo
echo "🔄 Bot is starting... Press Ctrl+C to stop"
echo "📱 Go to your Telegram bot and send /start command"
echo "🏦 The bot will respond with Phase 3 institutional trading features"
echo
echo "===================================================================================="

# Run the bot
java -cp "lib/*:target/classes" com.trading.bot.telegram.Phase3TelegramBot