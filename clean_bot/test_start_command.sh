#!/bin/bash
# Test the /start command response

echo "🧪 TESTING /start COMMAND RESPONSE"
echo "=================================="

cd clean_bot

echo "✅ Creating test input with /start command..."
cat > start_test_input.txt << 'EOF'
/start
/status
/help
quit
EOF

echo ""
echo "🚀 Testing Fresh Master Bot with /start command..."
echo "================================================="

# Run bot with test input (timeout after 15 seconds)
timeout 15s java -cp "lib/*:classes" com.trading.bot.master.FreshMasterBot < start_test_input.txt

echo ""
echo "✅ /start command test completed!"
echo "💡 The bot should have responded to /start with full initialization"

# Cleanup
rm -f start_test_input.txt