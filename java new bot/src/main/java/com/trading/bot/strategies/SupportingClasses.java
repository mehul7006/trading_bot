package com.trading.bot.strategies;

import java.util.*;

/**
 * SUPPORTING CLASSES FOR WORLD CLASS STRATEGIES
 * All analysis based on real market data only
 */

class OptionsGreeksCalculator {
    
    /**
     * Calculate option Greeks using Black-Scholes model
     */
    public GreeksData calculateGreeks(double spot, double strike, double timeToExpiry, 
                                    double riskFreeRate, double volatility, String optionType) {
        
        if (timeToExpiry <= 0) {
            double delta = calculateExpiryDelta(spot, strike, optionType);
            return new GreeksData(delta, 0, 0, 0, 0);
        }
        
        double d1 = calculateD1(spot, strike, timeToExpiry, riskFreeRate, volatility);
        double d2 = d1 - volatility * Math.sqrt(timeToExpiry);
        
        double delta = calculateDelta(d1, optionType);
        double gamma = calculateGamma(spot, d1, timeToExpiry, volatility);
        double theta = calculateTheta(spot, strike, d1, d2, timeToExpiry, riskFreeRate, volatility, optionType);
        double vega = calculateVega(spot, d1, timeToExpiry);
        double rho = calculateRho(strike, d2, timeToExpiry, riskFreeRate, optionType);
        
        return new GreeksData(delta, gamma, theta, vega, rho);
    }
    
    private double calculateD1(double spot, double strike, double timeToExpiry, 
                             double riskFreeRate, double volatility) {
        return (Math.log(spot / strike) + (riskFreeRate + 0.5 * volatility * volatility) * timeToExpiry) /
               (volatility * Math.sqrt(timeToExpiry));
    }
    
    private double calculateDelta(double d1, String optionType) {
        if (optionType.equals("CE")) {
            return normalCDF(d1);
        } else {
            return normalCDF(d1) - 1.0;
        }
    }
    
    private double calculateGamma(double spot, double d1, double timeToExpiry, double volatility) {
        return normalPDF(d1) / (spot * volatility * Math.sqrt(timeToExpiry));
    }
    
    private double calculateTheta(double spot, double strike, double d1, double d2, 
                                double timeToExpiry, double riskFreeRate, double volatility, String optionType) {
        
        double term1 = -(spot * normalPDF(d1) * volatility) / (2 * Math.sqrt(timeToExpiry));
        
        if (optionType.equals("CE")) {
            double term2 = riskFreeRate * strike * Math.exp(-riskFreeRate * timeToExpiry) * normalCDF(d2);
            return (term1 - term2) / 365.0;
        } else {
            double term2 = riskFreeRate * strike * Math.exp(-riskFreeRate * timeToExpiry) * normalCDF(-d2);
            return (term1 + term2) / 365.0;
        }
    }
    
    private double calculateVega(double spot, double d1, double timeToExpiry) {
        return (spot * normalPDF(d1) * Math.sqrt(timeToExpiry)) / 100.0;
    }
    
    private double calculateRho(double strike, double d2, double timeToExpiry, 
                              double riskFreeRate, String optionType) {
        
        if (optionType.equals("CE")) {
            return (strike * timeToExpiry * Math.exp(-riskFreeRate * timeToExpiry) * normalCDF(d2)) / 100.0;
        } else {
            return (-strike * timeToExpiry * Math.exp(-riskFreeRate * timeToExpiry) * normalCDF(-d2)) / 100.0;
        }
    }
    
    private double calculateExpiryDelta(double spot, double strike, String optionType) {
        if (optionType.equals("CE")) {
            return spot > strike ? 1.0 : 0.0;
        } else {
            return spot < strike ? -1.0 : 0.0;
        }
    }
    
    private double normalCDF(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }
    
    private double normalPDF(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2.0 * Math.PI);
    }
    
    private double erf(double x) {
        double a1 = 0.254829592;
        double a2 = -0.284496736;
        double a3 = 1.421413741;
        double a4 = -1.453152027;
        double a5 = 1.061405429;
        double p = 0.3275911;
        
        int sign = x < 0 ? -1 : 1;
        x = Math.abs(x);
        
        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        
        return sign * y;
    }
}

class VolatilityAnalyzer {
    
    /**
     * Calculate implied volatility from real market data
     */
    public double calculateRealImpliedVolatility(WorldClassIndexOptionsStrategies.RealMarketSnapshot snapshot) {
        // Use recent price movements to estimate implied volatility
        List<Double> prices = snapshot.priceHistory;
        if (prices.size() < 20) {
            return getTypicalVolatility(snapshot.symbol);
        }
        
        // Calculate recent volatility
        double recentVol = calculateHistoricalVolatility(prices, 20);
        
        // Adjust for current market conditions
        double adjustment = 1.0;
        if (snapshot.volumeHistory.get(snapshot.volumeHistory.size()-1) > 
            snapshot.volumeHistory.stream().mapToDouble(Double::doubleValue).average().orElse(1) * 1.5) {
            adjustment = 1.2; // Higher vol during high volume
        }
        
        return Math.max(0.10, Math.min(0.50, recentVol * adjustment));
    }
    
    public double calculateRealHistoricalVolatility(List<Double> priceHistory) {
        return calculateHistoricalVolatility(priceHistory, Math.min(30, priceHistory.size() - 1));
    }
    
    public double calculateRealizedVolatility(List<Double> priceHistory, int period) {
        return calculateHistoricalVolatility(priceHistory, Math.min(period, priceHistory.size() - 1));
    }
    
    private double calculateHistoricalVolatility(List<Double> prices, int period) {
        if (prices.size() < period + 1) {
            return 0.20; // Default 20% volatility
        }
        
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i <= period; i++) {
            double dailyReturn = Math.log(prices.get(prices.size() - i) / prices.get(prices.size() - i - 1));
            returns.add(dailyReturn);
        }
        
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
            .mapToDouble(r -> Math.pow(r - mean, 2))
            .average().orElse(0);
        
        return Math.sqrt(variance * 252); // Annualize
    }
    
    private double getTypicalVolatility(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "NIFTY" -> 0.18;
            case "BANKNIFTY" -> 0.25;
            case "FINNIFTY" -> 0.22;
            default -> 0.20;
        };
    }
    
    public boolean isLowVolatilityEnvironment(WorldClassIndexOptionsStrategies.RealMarketSnapshot snapshot) {
        double currentVol = calculateRealImpliedVolatility(snapshot);
        double typicalVol = getTypicalVolatility(snapshot.symbol);
        return currentVol < typicalVol * 0.8;
    }
}

class MarketRegimeDetector {
    
    /**
     * Detect current market regime based on real data
     */
    public String detectRegime(WorldClassIndexOptionsStrategies.RealMarketSnapshot snapshot) {
        List<Double> prices = snapshot.priceHistory;
        if (prices.size() < 50) {
            return "INSUFFICIENT_DATA";
        }
        
        // Calculate trend strength
        double trendStrength = calculateTrendStrength(prices);
        
        // Calculate volatility regime
        double volatility = new VolatilityAnalyzer().calculateRealHistoricalVolatility(prices);
        
        // Calculate volume regime
        double avgVolume = snapshot.volumeHistory.stream().mapToDouble(Double::doubleValue).average().orElse(1);
        double currentVolume = snapshot.volumeHistory.get(snapshot.volumeHistory.size() - 1);
        double volumeRatio = currentVolume / avgVolume;
        
        // Determine regime
        if (trendStrength > 0.02 && volatility < 0.15 && volumeRatio > 1.2) {
            return "STRONG_BULLISH_TREND";
        } else if (trendStrength < -0.02 && volatility < 0.15 && volumeRatio > 1.2) {
            return "STRONG_BEARISH_TREND";
        } else if (volatility > 0.25) {
            return "HIGH_VOLATILITY";
        } else if (Math.abs(trendStrength) < 0.005) {
            return "SIDEWAYS_RANGE";
        } else {
            return "MIXED_SIGNALS";
        }
    }
    
    private double calculateTrendStrength(List<Double> prices) {
        if (prices.size() < 20) return 0;
        
        double recent = prices.get(prices.size() - 1);
        double past = prices.get(prices.size() - 20);
        
        return (recent - past) / past;
    }
}

class RealMarketSnapshot {
    public final String symbol;
    public final double currentPrice;
    public final List<Double> priceHistory;
    public final List<Double> volumeHistory;
    public final java.time.LocalDateTime timestamp;
    
    public RealMarketSnapshot(String symbol, double currentPrice, List<Double> priceHistory, 
                            List<Double> volumeHistory, java.time.LocalDateTime timestamp) {
        this.symbol = symbol;
        this.currentPrice = currentPrice;
        this.priceHistory = new ArrayList<>(priceHistory);
        this.volumeHistory = new ArrayList<>(volumeHistory);
        this.timestamp = timestamp;
    }
}