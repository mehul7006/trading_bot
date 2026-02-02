package com.trading.bot.market;

import java.time.LocalDateTime;

/**
 * Simplified MarketData for Phase 1 Implementation
 * Contains only essential fields needed for Phase 1 testing
 */
public class SimpleMarketData {
    public final String symbol;
    public final double price; // Close price
    public final double open;
    public final double high;
    public final double low;
    public final long volume;
    public final LocalDateTime timestamp;
    
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