package com.trading.bot.market;

public class MarketDataEvent {
    private final String symbol;
    private final MarketData data;

    public MarketDataEvent(String symbol, MarketData data) {
        this.symbol = symbol;
        this.data = data;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }

    public String getSymbol() {
        return symbol;
    }

    public MarketData getData() {
        return data;
    }
}