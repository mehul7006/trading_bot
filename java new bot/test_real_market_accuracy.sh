#!/bin/bash

# REAL MARKET ACCURACY TEST - NSE & BSE DATA
# Tests bot accuracy against live market data from official sources

echo "🎯 REAL MARKET ACCURACY TEST"
echo "Testing against NSE & BSE live data"
echo "═══════════════════════════════════════════════════════════"

# Set environment
export JAVA_HOME="/usr/lib/jvm/java-11-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"

# Navigate to bot directory
cd "$(dirname "$0")"

echo "📊 Current market session: $(date '+%Y-%m-%d %H:%M:%S')"
echo "🕐 Market hours: 09:15 - 15:30 IST"

# Check if market is open
CURRENT_HOUR=$(date +%H)
CURRENT_MIN=$(date +%M)
CURRENT_TIME=$((CURRENT_HOUR * 60 + CURRENT_MIN))
MARKET_OPEN=$((9 * 60 + 15))  # 09:15
MARKET_CLOSE=$((15 * 60 + 30)) # 15:30

if [ $CURRENT_TIME -lt $MARKET_OPEN ] || [ $CURRENT_TIME -gt $MARKET_CLOSE ]; then
    echo "⚠️ WARNING: Market is currently closed"
    echo "   Market opens at 09:15 IST and closes at 15:30 IST"
    echo "   Running test with available data..."
else
    echo "✅ Market is OPEN - Perfect for live testing!"
fi

echo ""
echo "🔧 Preparing test environment..."

# Clean and compile all components
echo "📝 Compiling required components..."

# Create directories
mkdir -p target/classes/com/trading/bot/{strategy,core,validation}

# Compile OptimizedTradingStrategy
echo "🧮 Compiling OptimizedTradingStrategy..."
javac -cp "lib/*:target/classes" \
    -d target/classes \
    src/main/java/com/trading/bot/strategy/OptimizedTradingStrategy.java

if [ $? -ne 0 ]; then
    echo "❌ Failed to compile OptimizedTradingStrategy"
    exit 1
fi

# Compile RealMarketAccuracyTester
echo "🎯 Compiling RealMarketAccuracyTester..."
javac -cp "lib/*:target/classes" \
    -d target/classes \
    src/main/java/com/trading/bot/validation/RealMarketAccuracyTester.java

if [ $? -ne 0 ]; then
    echo "❌ Failed to compile RealMarketAccuracyTester"
    exit 1
fi

echo "✅ Compilation successful!"

# Create test configuration
echo ""
echo "⚙️ Creating test configuration..."
cat > real_market_test.properties << EOF
# Real Market Accuracy Test Configuration
test.date=$(date '+%Y-%m-%d')
test.start.time=$(date '+%H:%M:%S')

# Data sources (in order of preference)
nse.api.enabled=true
bse.api.enabled=true
alternative.api.enabled=true

# Test parameters
test.instruments=NIFTY,BANKNIFTY,FINNIFTY,SENSEX
validation.intervals=5,10,15,30
min.confidence.for.test=70.0

# Network settings
http.timeout=10
retry.attempts=3
fallback.data.enabled=true

# Logging
log.api.calls=true
log.validation.results=true
save.detailed.report=true
EOF

echo "✅ Configuration created"

# Display test plan
echo ""
echo "📋 TEST PLAN:"
echo "═══════════════════════════════════════"
echo "🎯 Testing Strategy: Optimized Multi-indicator Confluence"
echo "📊 Data Sources: NSE Official API → BSE API → Yahoo Finance"
echo "📈 Instruments: NIFTY, BANKNIFTY, FINNIFTY, SENSEX"
echo "⏱️ Validation Intervals: 5, 10, 15, 30 minutes"
echo "🎲 Minimum Confidence: 70% for signal generation"
echo ""

echo "📊 CURRENT MARKET LEVELS (Live Fetch):"
echo "════════════════════════════════════════"

# Try to fetch current market levels
echo "🔄 Fetching live data from NSE/BSE..."

# Create a simple Java class to fetch current prices
cat > QuickPriceFetch.java << 'EOF'
import java.net.http.*;
import java.net.*;
import java.io.*;

public class QuickPriceFetch {
    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();
        
        // Try to fetch NIFTY from NSE
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.nseindia.com/api/option-chain-indices?symbol=NIFTY"))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                if (body.contains("underlyingValue")) {
                    int start = body.indexOf("\"underlyingValue\":");
                    if (start != -1) {
                        start = body.indexOf(":", start) + 1;
                        int end = body.indexOf(",", start);
                        if (end == -1) end = body.indexOf("}", start);
                        String price = body.substring(start, end).trim();
                        System.out.println("NIFTY: ₹" + price + " (NSE Live)");
                    }
                }
            } else {
                System.out.println("NIFTY: API unavailable (Status: " + response.statusCode() + ")");
            }
        } catch (Exception e) {
            System.out.println("NIFTY: ₹24,200 (Fallback - API error: " + e.getMessage() + ")");
        }
        
        // Add other indices with fallback values
        System.out.println("BANKNIFTY: ₹52,000 (Approximate)");
        System.out.println("FINNIFTY: ₹23,500 (Approximate)");
        System.out.println("SENSEX: ₹79,800 (Approximate)");
    }
}
EOF

# Compile and run quick fetch
javac QuickPriceFetch.java 2>/dev/null
if [ $? -eq 0 ]; then
    java QuickPriceFetch
    rm QuickPriceFetch.class QuickPriceFetch.java
else
    echo "NIFTY: ₹24,200 (Approximate)"
    echo "BANKNIFTY: ₹52,000 (Approximate)" 
    echo "FINNIFTY: ₹23,500 (Approximate)"
    echo "SENSEX: ₹79,800 (Approximate)"
    rm QuickPriceFetch.java 2>/dev/null
fi

echo ""
echo "🚀 STARTING REAL MARKET ACCURACY TEST..."
echo "═══════════════════════════════════════════"

# Run the accuracy tester
echo "⏱️ This test will:"
echo "   1. Fetch live prices from NSE/BSE"
echo "   2. Generate signals using optimized strategy"
echo "   3. Wait and validate predictions at 5, 10, 15, 30 minute intervals"
echo "   4. Generate comprehensive accuracy report"
echo ""

read -p "🤔 Proceed with live accuracy test? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Test cancelled by user"
    exit 0
fi

echo ""
echo "▶️ Starting RealMarketAccuracyTester..."

# Run with enhanced JVM settings
java -cp "lib/*:target/classes" \
    -Djava.util.logging.config.file=logging.properties \
    -Xmx1024m \
    -Dhttp.agent="NSE-BSE-Tester/1.0" \
    com.trading.bot.validation.RealMarketAccuracyTester

TEST_RESULT=$?

echo ""
echo "📊 TEST COMPLETION STATUS: $TEST_RESULT"

# Analyze results
echo ""
echo "📈 ANALYZING TEST RESULTS..."
echo "═══════════════════════════════════════"

# Check for generated report files
REPORT_FILES=$(ls today_real_accuracy_report_*.txt 2>/dev/null)

if [ -n "$REPORT_FILES" ]; then
    echo "✅ Accuracy reports generated:"
    for file in $REPORT_FILES; do
        echo "   📄 $file"
        
        # Extract key metrics
        if [ -f "$file" ]; then
            echo "   📊 Quick Summary:"
            grep -E "(5-min Accuracy|10-min Accuracy|15-min Accuracy|30-min Accuracy)" "$file" | head -4 | sed 's/^/      /'
        fi
    done
    
    echo ""
    echo "🎯 ACCURACY BENCHMARK COMPARISON:"
    echo "═══════════════════════════════════════"
    echo "Current Bot (Previous): 54.7% (10-min)"
    echo "Market Average: ~50-55%"
    echo "Professional Traders: ~60-65%"
    echo "Top Algorithms: ~70-75%"
    echo ""
    
    LATEST_REPORT=$(ls -t today_real_accuracy_report_*.txt | head -1)
    if [ -f "$LATEST_REPORT" ]; then
        TEN_MIN_ACCURACY=$(grep "10-min Accuracy" "$LATEST_REPORT" | awk '{print $3}' | sed 's/%//')
        
        if [ -n "$TEN_MIN_ACCURACY" ]; then
            echo "🎯 TODAY'S RESULTS:"
            echo "   10-min Accuracy: ${TEN_MIN_ACCURACY}%"
            
            # Compare with benchmarks
            if (( $(echo "$TEN_MIN_ACCURACY >= 70" | bc -l 2>/dev/null || echo "0") )); then
                echo "   🏆 EXCELLENT! Above top algorithm level"
                echo "   💡 Strategy optimization successful!"
            elif (( $(echo "$TEN_MIN_ACCURACY >= 60" | bc -l 2>/dev/null || echo "0") )); then
                echo "   ✅ GOOD! Professional trader level achieved"
                echo "   💡 Continue optimization for top-tier performance"
            elif (( $(echo "$TEN_MIN_ACCURACY >= 55" | bc -l 2>/dev/null || echo "0") )); then
                echo "   ⚠️ AVERAGE! Above market but room for improvement"
                echo "   💡 Fine-tune confidence thresholds and add indicators"
            else
                echo "   ❌ BELOW AVERAGE! Strategy needs major optimization"
                echo "   💡 Review indicator weights and signal confluence requirements"
            fi
        fi
    fi
    
else
    echo "⚠️ No accuracy reports found"
    echo "   This could indicate:"
    echo "   - Market was closed during test"
    echo "   - No signals generated (all HOLD)"
    echo "   - API connectivity issues"
    echo "   - Compilation or runtime errors"
fi

echo ""
echo "🔍 NEXT STEPS BASED ON RESULTS:"
echo "═══════════════════════════════════════"
echo "📊 If accuracy ≥ 70%: Deploy to live trading with small positions"
echo "📊 If accuracy 60-69%: Paper trade for 1 week, then consider live"
echo "📊 If accuracy 55-59%: Optimize parameters, extend testing period"
echo "📊 If accuracy < 55%: Major strategy revision needed"
echo ""

# Save test metadata
cat > test_metadata_$(date +%Y%m%d_%H%M%S).json << EOF
{
  "test_date": "$(date '+%Y-%m-%d')",
  "test_time": "$(date '+%H:%M:%S')",
  "market_session": "$([[ $CURRENT_TIME -ge $MARKET_OPEN && $CURRENT_TIME -le $MARKET_CLOSE ]] && echo "OPEN" || echo "CLOSED")",
  "strategy_version": "OptimizedTradingStrategy v2.0",
  "data_sources": ["NSE", "BSE", "Yahoo Finance"],
  "test_instruments": ["NIFTY", "BANKNIFTY", "FINNIFTY", "SENSEX"],
  "validation_intervals": [5, 10, 15, 30],
  "test_result_code": $TEST_RESULT
}
EOF

echo "📝 Test metadata saved"
echo ""
echo "✅ REAL MARKET ACCURACY TEST COMPLETED"
echo "═══════════════════════════════════════════════════════════"
echo "📧 Review generated reports for detailed analysis"
echo "🔄 Run again during market hours for best results"
echo "📞 Contact: Trading Bot Support for assistance"