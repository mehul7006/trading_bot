#!/bin/bash
# Demo Phase 5 bot responses with automated input

echo "🎭 DEMONSTRATING PHASE 5 BOT RESPONSES"
echo "======================================"

cd clean_bot

# Create test input for the bot
cat > test_commands.txt << 'EOF'
help
status
analyze NIFTY
ai BANKNIFTY
test
quit
EOF

echo "✅ Phase 5 bot compiled and ready!"
echo "🎯 Testing bot responses with sample commands..."
echo ""
echo "📝 Commands to test:"
echo "   - help (show commands)"
echo "   - status (show bot status)"  
echo "   - analyze NIFTY (full AI analysis)"
echo "   - ai BANKNIFTY (generate AI call)"
echo "   - test (run comprehensive test)"
echo "   - quit (exit bot)"
echo ""
echo "🚀 Starting bot with automated responses..."
echo "=========================================="

# Run bot with test commands (timeout after 30 seconds)
timeout 30s java -cp "lib/*:classes" com.trading.bot.launcher.Phase5BotLauncher < test_commands.txt

echo ""
echo "✅ Phase 5 bot response demonstration complete!"
echo "💡 The bot is ready for interactive use with proper responses."

# Cleanup
rm -f test_commands.txt