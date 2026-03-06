package com.trading.bot.market;

import java.util.Map;

public class OptionStrikeMetrics {
    public final String symbol;
    public final double strike;
    public final String type;
    public final Double ltp;
    public final Double oi;
    public final Double oiChange;
    public final Map<String, Double> greeks;
    public final Double iv;
    
    public OptionStrikeMetrics(String symbol, double strike, String type, Double ltp, Double oi, Double oiChange, Map<String, Double> greeks, Double iv) {
        this.symbol = symbol;
        this.strike = strike;
        this.type = type;
        this.ltp = ltp;
        this.oi = oi;
        this.oiChange = oiChange;
        this.greeks = greeks;
        this.iv = iv;
    }
}
