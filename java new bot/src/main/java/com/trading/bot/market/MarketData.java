package com.trading.bot.market;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

public class MarketData {
    private String symbol;
    private double open;
    private double high;
    private double low;
    private double close;
    private double price;
    private double volume;
    private double vwap;
    private double volatility;
    private String dayMove;
    private List<Double> priceHistory;
    private List<Double> volumeHistory;
    private List<Double> vwapHistory;
    private List<Double> volatilityHistory;
    private LocalDateTime timestamp;
    private boolean isMocked;

    public MarketData() {
        this.priceHistory = new ArrayList<>();
        this.volumeHistory = new ArrayList<>();
        this.vwapHistory = new ArrayList<>();
        this.volatilityHistory = new ArrayList<>();
        this.timestamp = LocalDateTime.now();
    }

    // Getters
    public String getSymbol() { return symbol; }
    public double getOpen() { return open; }
    public double getHigh() { return high; }
    public double getLow() { return low; }
    public double getClose() { return close; }
    public double getPrice() { return price; }
    public double getVolume() { return volume; }
    public double getVwap() { return vwap; }
    public double getVolatility() { return volatility; }
    public String getDayMove() { return dayMove; }
    public List<Double> getPriceHistory() { return priceHistory; }
    public List<Double> getVolumeHistory() { return volumeHistory; }
    public List<Double> getVwapHistory() { return vwapHistory; }
    public List<Double> getVolatilityHistory() { return volatilityHistory; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public boolean isMocked() { return isMocked; }

    // Setters
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setOpen(double open) { this.open = open; }
    public void setHigh(double high) { this.high = high; }
    public void setLow(double low) { this.low = low; }
    public void setClose(double close) { this.close = close; }
    public void setPrice(double price) { this.price = price; }
    public void setVolume(double volume) { this.volume = volume; }
    public void setVwap(double vwap) { this.vwap = vwap; }
    public void setVolatility(double volatility) { this.volatility = volatility; }
    public void setDayMove(String dayMove) { this.dayMove = dayMove; }
    public void setPriceHistory(List<Double> priceHistory) { this.priceHistory = priceHistory; }
    public void setVolumeHistory(List<Double> volumeHistory) { this.volumeHistory = volumeHistory; }
    public void setVwapHistory(List<Double> vwapHistory) { this.vwapHistory = vwapHistory; }
    public void setVolatilityHistory(List<Double> volatilityHistory) { this.volatilityHistory = volatilityHistory; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setMocked(boolean mocked) { this.isMocked = mocked; }

    public boolean isMocked() {
        return isMocked;
    }
}