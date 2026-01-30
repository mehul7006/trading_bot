package com.trading.bot.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarketPriceChecker {
    private static final Logger logger = LoggerFactory.getLogger(MarketPriceChecker.class);
    
    public static void main(String[] args) {
        try {
            // Initialize the real-time data collector with market data URL
            String marketDataUrl = "wss://your-market-data-feed.com/ws";  // Replace with your actual URL
            RealTimeDataCollector collector = new RealTimeDataCollector(marketDataUrl);
            
            // Subscribe to Nifty and Sensex
            collector.subscribeSymbol("NIFTY");
            collector.subscribeSymbol("SENSEX");
            
            // Wait for initial data
            Thread.sleep(2000);
            
            // Get current prices
            MarketData niftyData = collector.getRealTimeData("NIFTY");
            MarketData sensexData = collector.getRealTimeData("SENSEX");
            
            if (niftyData != null) {
                System.out.println("Current NIFTY: " + niftyData.getPrice());
                System.out.println("NIFTY Day Move: " + niftyData.getDayMove());
            }
            
            if (sensexData != null) {
                System.out.println("Current SENSEX: " + sensexData.getPrice());
                System.out.println("SENSEX Day Move: " + sensexData.getDayMove());
            }
            
            // Cleanup
            collector.shutdown();
            
        } catch (Exception e) {
            logger.error("Error checking market prices: " + e.getMessage());
        }
    }
}