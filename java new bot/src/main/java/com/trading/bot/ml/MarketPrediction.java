package com.trading.bot.ml;

public class MarketPrediction {
    public final double priceSignal;
    public final double volumeSignal; 
    public final double momentumSignal;
    public final MLMarketAnalyzer.TrendDirection trend;
    public final double confidence;
    public final String analysis;

    public MarketPrediction(double priceSignal, double volumeSignal, double momentumSignal,
                           MLMarketAnalyzer.TrendDirection trend, double confidence, 
                           String analysis) {
        this.priceSignal = priceSignal;
        this.volumeSignal = volumeSignal;
        this.momentumSignal = momentumSignal;
        this.trend = trend;
        this.confidence = confidence;
        this.analysis = analysis;
    }

    public double getPriceSignal() {
        return priceSignal;
    }

    public double getVolumeSignal() {
        return volumeSignal;
    }

    public double getMomentumSignal() {
        return momentumSignal;
    }

    public MLMarketAnalyzer.TrendDirection getTrend() {
        return trend;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getAnalysis() {
        return analysis;
    }
}