#!/bin/bash

# REAL UPSTOX TRADING BOT - FIXED ALL CRITICAL FAILURES
# ✅ ONLY Upstox real market data
# ❌ NO Yahoo Finance
# ❌ NO simulation fallback
# ❌ NO Math.sin() tricks

echo "🎯 REAL UPSTOX TRADING BOT - ALL CRITICAL FAILURES FIXED"
echo "=" | tr ' ' '=' | head -c 60 && echo
echo "✅ ONLY Upstox real market data"
echo "❌ NO Yahoo Finance"
echo "❌ NO simulation fallback" 
echo "❌ NO Math.sin() tricks"
echo "🎯 Real data or complete failure"
echo "=" | tr ' ' '=' | head -c 60 && echo

cd "$(dirname "$0")"

# Compile the real Upstox bot
echo "🔧 Compiling real Upstox bot..."
javac -cp ".:lib/*" -d classes src/main/java/com/trading/bot/core/RealUpstoxTradingBot.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
    echo ""
    echo "🔐 UPSTOX AUTHENTICATION REQUIRED"
    echo "Before starting, you need:"
    echo "1. Upstox API Key"
    echo "2. Upstox API Secret"
    echo "3. Redirect URI (e.g., http://localhost:8080/callback)"
    echo ""
    echo "🚀 Starting REAL Upstox bot (no simulation fallback)..."
    echo "📊 Will use ONLY real Upstox market data"
    echo "❌ Will FAIL if no real data available"
    echo ""
    
    # Run the real Upstox bot
    java -cp ".:lib/*:classes" com.trading.bot.core.RealUpstoxTradingBot
    
else
    echo "❌ Compilation failed"
    exit 1
fi