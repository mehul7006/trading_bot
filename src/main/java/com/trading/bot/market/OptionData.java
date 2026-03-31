package com.trading.bot.market;

import java.util.Map;

/**
 * Data class to hold Option Chain metrics like PCR, OI, and Greeks.
 */
public class OptionData {
    public final String symbol;
    public final double pcr; // Put-Call Ratio
    public final double totalCallOI;
    public final double totalPutOI;
    public final double callOIChange;
    public final double putOIChange;
    public final Map<String, Double> greeks; // Delta, Gamma, Theta, Vega for ATM
    public final double maxPain;
    
    public OptionData(String symbol, double pcr, double totalCallOI, double totalPutOI, 
                      double callOIChange, double putOIChange, Map<String, Double> greeks, double maxPain) {
        this.symbol = symbol;
        this.pcr = pcr;
        this.totalCallOI = totalCallOI;
        this.totalPutOI = totalPutOI;
        this.callOIChange = callOIChange;
        this.putOIChange = putOIChange;
        this.greeks = greeks;
        this.maxPain = maxPain;
    }
}
