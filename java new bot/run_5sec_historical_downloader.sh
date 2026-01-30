#!/bin/bash

echo "📊 HIGH-FREQUENCY HISTORICAL MARKET DATA DOWNLOADER"
echo "==================================================="
echo "🎯 Downloads TODAY'S complete SENSEX & NIFTY data"
echo "⏰ Trading session: 9:00 AM to 3:30 PM"
echo "📈 Ultra-granular frequency: Every 5 seconds"
echo "🚀 Perfect for algorithmic trading and HFT analysis"
echo "==================================================="

# Create 5-second data directory
mkdir -p high_freq_data_$(date +%Y-%m-%d)

echo "🔨 Compiling 5-second interval data downloader..."
javac HistoricalMarketDataDownloader5Sec.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🚀 Starting 5-second interval data download..."
    echo "📅 Date: $(date +%d-%m-%Y)"
    echo "⏰ Session: 9:00 AM to 3:30 PM (6.5 hours)"
    echo "📊 Frequency: Every 5 seconds"
    echo "🎯 Expected: ~4,680 data points per index"
    echo "💾 Total data points: ~9,360 (both indices)"
    echo ""
    echo "💾 Files will be created:"
    echo "   - nifty_5sec_$(date +%Y-%m-%d).csv"
    echo "   - sensex_5sec_$(date +%Y-%m-%d).csv"
    echo "   - market_5sec_combined_$(date +%Y-%m-%d).csv"
    echo ""
    echo "📈 Downloading ultra-granular trading session data..."
    echo "⚠️  This may take a few minutes due to high data volume..."
    echo "==================================================="
    
    # Run the 5-second interval downloader
    java HistoricalMarketDataDownloader5Sec
    
    echo ""
    echo "✅ 5-second interval data download completed!"
    echo "📊 Check the CSV files for ultra-granular market data"
    echo "🎯 Perfect for high-frequency trading analysis!"
    
else
    echo "❌ Compilation failed"
    echo "💡 Please check the error messages above"
fi