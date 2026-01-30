#!/bin/bash

echo "🧪 TESTING TRADING BOT SERVER"
echo ""

# Check if server is running
echo "🔍 Checking if server is running..."
if lsof -i :8080 >/dev/null 2>&1; then
    echo "✅ Server is running on port 8080"
    echo ""
    
    echo "🧪 Testing endpoints..."
    
    echo "1. Testing /status endpoint..."
    curl -s "http://localhost:8080/status" >/dev/null && echo "   ✅ /status works" || echo "   ❌ /status failed"
    
    echo "2. Testing /start endpoint..."
    curl -s "http://localhost:8080/start" >/dev/null && echo "   ✅ /start works" || echo "   ❌ /start failed"
    
    echo "3. Testing /help endpoint..."
    curl -s "http://localhost:8080/help" >/dev/null && echo "   ✅ /help works" || echo "   ❌ /help failed"
    
    echo ""
    echo "🌐 Open your browser and visit:"
    echo "   http://localhost:8080"
    echo ""
    echo "📊 Available endpoints:"
    echo "   • http://localhost:8080/start   - Start analysis"
    echo "   • http://localhost:8080/test    - Test analysis"
    echo "   • http://localhost:8080/phase1  - Phase 1 only" 
    echo "   • http://localhost:8080/phase2  - Phase 2 only"
    echo "   • http://localhost:8080/status  - System status"
    echo "   • http://localhost:8080/help    - Help page"
    
else
    echo "❌ Server is not running on port 8080"
    echo ""
    echo "🚀 Start the server with: ./start_bot_server.sh"
fi