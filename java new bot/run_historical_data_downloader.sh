#!/bin/bash

echo "📊 HISTORICAL MARKET DATA DOWNLOADER"
echo "===================================="
echo "🎯 Downloads TODAY'S complete SENSEX & NIFTY data"
echo "⏰ Trading session: 9:00 AM to 3:30 PM"
echo "📈 Frequency: Minute-by-minute historical data"
echo "🌐 Sources: Yahoo Finance, Alpha Vantage, Simulated"
echo "===================================="

# Create historical data directory
mkdir -p historical_data_$(date +%Y-%m-%d)

echo "🔨 Compiling historical data downloader..."
javac HistoricalMarketDataDownloader.java

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🚀 Starting historical data download..."
    echo "📅 Date: $(date +%d-%m-%Y)"
    echo "⏰ Session: 9:00 AM to 3:30 PM (6.5 hours)"
    echo "📊 Expected: ~390 data points per index"
    echo ""
    echo "💾 Files will be created:"
    echo "   - nifty_historical_$(date +%Y-%m-%d).csv"
    echo "   - sensex_historical_$(date +%Y-%m-%d).csv"
    echo "   - market_historical_combined_$(date +%Y-%m-%d).csv"
    echo ""
    echo "📈 Downloading complete trading session data..."
    echo "===================================="
    
    # Run the historical downloader
    java HistoricalMarketDataDownloader
    
    echo ""
    echo "✅ Historical data download completed!"
    echo "📊 Check the CSV files for today's complete market data"
    
else
    echo "❌ Compilation failed"
    echo "💡 Please check the error messages above"
fi