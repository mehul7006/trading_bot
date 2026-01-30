#!/bin/bash

echo "📊 REAL-TIME MARKET DATA DOWNLOADER"
echo "==================================="
echo "🎯 Downloads SENSEX & NIFTY every second"
echo "🌐 Sources: NSE Official, BSE Official, Yahoo Finance"
echo "💾 Output: CSV files with full day data"
echo "==================================="

# Create data directory
mkdir -p market_data_$(date +%Y-%m-%d)

echo "🔨 Compiling market data downloader..."
javac RealTimeMarketDataDownloader.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🚀 Starting real-time data collection..."
    echo "📊 NIFTY & SENSEX data every second"
    echo "💾 Files will be created:"
    echo "   - nifty_data_$(date +%Y-%m-%d).csv"
    echo "   - sensex_data_$(date +%Y-%m-%d).csv" 
    echo "   - market_data_combined_$(date +%Y-%m-%d).csv"
    echo ""
    echo "📈 Live data summary every 30 seconds"
    echo "🛑 Press Ctrl+C to stop and save data"
    echo "==================================="
    
    # Run the downloader
    java RealTimeMarketDataDownloader
    
else
    echo "❌ Compilation failed"
    echo "💡 Please check the error messages above"
fi