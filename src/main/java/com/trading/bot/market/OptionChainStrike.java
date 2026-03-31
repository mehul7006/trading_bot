package com.trading.bot.market;

public class OptionChainStrike {
    public final String symbol;
    public final double strike;
    public final String callInstrumentKey;
    public final String putInstrumentKey;
    public final OptionStrikeMetrics callMetrics;
    public final OptionStrikeMetrics putMetrics;
    
    public OptionChainStrike(String symbol, double strike, String callInstrumentKey, String putInstrumentKey, OptionStrikeMetrics callMetrics, OptionStrikeMetrics putMetrics) {
        this.symbol = symbol;
        this.strike = strike;
        this.callInstrumentKey = callInstrumentKey;
        this.putInstrumentKey = putInstrumentKey;
        this.callMetrics = callMetrics;
        this.putMetrics = putMetrics;
    }
}
