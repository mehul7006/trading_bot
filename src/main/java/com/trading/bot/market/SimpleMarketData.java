package com.trading.bot.market;

import java.time.LocalDateTime;

/**
 * Simplified MarketData for Phase 1 Implementation
 * Contains only essential fields needed for Phase 1 testing
 */
public class SimpleMarketData {
    public String symbol;
    public double price; // Close price
    public double open;
    public double high;
    public double low;
    public long volume;
    public LocalDateTime timestamp;
    
    public SimpleMarketData() {
        // Default constructor for JSON deserialization
    }

    public SimpleMarketData(String symbol, double price, double open, double high, double low, long volume, LocalDateTime timestamp) {
        this.symbol = symbol;
        this.price = price;
        this.open = open;
        this.high = high;
        this.low = low;
        this.volume = volume;
        this.timestamp = timestamp;
    }
    
    // Legacy constructor compatibility
    public SimpleMarketData(String symbol, double price, long volume, LocalDateTime timestamp) {
        this(symbol, price, price, price, price, volume, timestamp);
    }
    
    public SimpleMarketData(String symbol, double price, long volume) {
        this(symbol, price, price, price, price, volume, LocalDateTime.now());
    }
    
    @Override
    public String toString() {
        return String.format("%s: %.2f (Vol: %d) at %s", symbol, price, volume, timestamp);
    }
}