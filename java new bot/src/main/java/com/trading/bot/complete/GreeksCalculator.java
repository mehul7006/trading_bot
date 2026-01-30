package com.trading.bot.complete;

import java.util.Map;
import java.util.HashMap;

/**
 * GREEKS CALCULATOR - Black-Scholes Model Implementation
 * Professional-grade options Greeks calculation
 * Used by institutional traders and market makers
 */
public class GreeksCalculator {
    
    /**
     * Calculate option premium using Black-Scholes model
     */
    public double calculatePremium(double spot, double strike, double timeToExpiry, 
                                 double riskFreeRate, double volatility, String optionType) {
        
        if (timeToExpiry <= 0) return calculateIntrinsicValue(spot, strike, optionType);
        
        double d1 = calculateD1(spot, strike, timeToExpiry, riskFreeRate, volatility);
        double d2 = d1 - volatility * Math.sqrt(timeToExpiry);
        
        double nd1 = normalCDF(d1);
        double nd2 = normalCDF(d2);
        double nMinusD1 = normalCDF(-d1);
        double nMinusD2 = normalCDF(-d2);
        
        if (optionType.equals("CE")) {
            return spot * nd1 - strike * Math.exp(-riskFreeRate * timeToExpiry) * nd2;
        } else { // PE
            return strike * Math.exp(-riskFreeRate * timeToExpiry) * nMinusD2 - spot * nMinusD1;
        }
    }
    
    /**
     * Calculate all Greeks for an option
     */
    public GreeksData calculateGreeks(double spot, double strike, double timeToExpiry,
                                    double riskFreeRate, double volatility, String optionType) {
        
        if (timeToExpiry <= 0) {
            // At expiry, most Greeks are zero except delta
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
        } else { // PE
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
            return (term1 - term2) / 365.0; // Convert to daily theta
        } else { // PE
            double term2 = riskFreeRate * strike * Math.exp(-riskFreeRate * timeToExpiry) * normalCDF(-d2);
            return (term1 + term2) / 365.0; // Convert to daily theta
        }
    }
    
    private double calculateVega(double spot, double d1, double timeToExpiry) {
        return (spot * normalPDF(d1) * Math.sqrt(timeToExpiry)) / 100.0; // Per 1% change in volatility
    }
    
    private double calculateRho(double strike, double d2, double timeToExpiry, 
                              double riskFreeRate, String optionType) {
        
        if (optionType.equals("CE")) {
            return (strike * timeToExpiry * Math.exp(-riskFreeRate * timeToExpiry) * normalCDF(d2)) / 100.0;
        } else { // PE
            return (-strike * timeToExpiry * Math.exp(-riskFreeRate * timeToExpiry) * normalCDF(-d2)) / 100.0;
        }
    }
    
    private double calculateIntrinsicValue(double spot, double strike, String optionType) {
        if (optionType.equals("CE")) {
            return Math.max(0, spot - strike);
        } else { // PE
            return Math.max(0, strike - spot);
        }
    }
    
    private double calculateExpiryDelta(double spot, double strike, String optionType) {
        if (optionType.equals("CE")) {
            return spot > strike ? 1.0 : 0.0;
        } else { // PE
            return spot < strike ? -1.0 : 0.0;
        }
    }
    
    /**
     * Standard normal cumulative distribution function
     */
    private double normalCDF(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }
    
    /**
     * Standard normal probability density function
     */
    private double normalPDF(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2.0 * Math.PI);
    }
    
    /**
     * Error function approximation
     */
    private double erf(double x) {
        // Abramowitz and Stegun approximation
        double a1 =  0.254829592;
        double a2 = -0.284496736;
        double a3 =  1.421413741;
        double a4 = -1.453152027;
        double a5 =  1.061405429;
        double p  =  0.3275911;
        
        int sign = x < 0 ? -1 : 1;
        x = Math.abs(x);
        
        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        
        return sign * y;
    }
    
    /**
     * Calculate implied volatility using Newton-Raphson method
     */
    public double calculateImpliedVolatility(double marketPrice, double spot, double strike,
                                           double timeToExpiry, double riskFreeRate, String optionType) {
        
        if (timeToExpiry <= 0) return 0.0;
        
        double intrinsicValue = calculateIntrinsicValue(spot, strike, optionType);
        if (marketPrice <= intrinsicValue) return 0.01; // Minimum volatility
        
        double volatility = 0.2; // Initial guess
        double tolerance = 0.0001;
        int maxIterations = 100;
        
        for (int i = 0; i < maxIterations; i++) {
            double price = calculatePremium(spot, strike, timeToExpiry, riskFreeRate, volatility, optionType);
            double vega = calculateVega(spot, calculateD1(spot, strike, timeToExpiry, riskFreeRate, volatility), timeToExpiry) * 100;
            
            double priceDiff = price - marketPrice;
            
            if (Math.abs(priceDiff) < tolerance) {
                return volatility;
            }
            
            if (vega == 0) break;
            
            volatility = volatility - priceDiff / vega;
            volatility = Math.max(0.001, Math.min(5.0, volatility)); // Bound volatility
        }
        
        return volatility;
    }
    
    /**
     * Calculate option sensitivities for risk management
     */
    public Map<String, Double> calculateSensitivities(double spot, double strike, double timeToExpiry,
                                                     double riskFreeRate, double volatility, String optionType) {
        
        Map<String, Double> sensitivities = new HashMap<>();
        
        GreeksData greeks = calculateGreeks(spot, strike, timeToExpiry, riskFreeRate, volatility, optionType);
        double premium = calculatePremium(spot, strike, timeToExpiry, riskFreeRate, volatility, optionType);
        
        // Price sensitivity (Delta exposure for 1% move)
        sensitivities.put("price_sensitivity_1pct", greeks.delta * spot * 0.01);
        
        // Volatility sensitivity (Vega exposure for 1% vol change)
        sensitivities.put("vol_sensitivity_1pct", greeks.vega);
        
        // Time decay (1 day theta)
        sensitivities.put("time_decay_1day", greeks.theta);
        
        // Gamma risk (convexity)
        sensitivities.put("gamma_risk", greeks.gamma * spot * spot * 0.01 * 0.01);
        
        // Percentage Greeks
        sensitivities.put("delta_percentage", greeks.delta * spot / premium);
        sensitivities.put("vega_percentage", greeks.vega / premium);
        
        return sensitivities;
    }
    
    /**
     * Determine if option is good value based on theoretical vs market price
     */
    public String analyzeOptionValue(double marketPrice, double theoreticalPrice) {
        double difference = (marketPrice - theoreticalPrice) / theoreticalPrice;
        
        if (difference < -0.05) return "UNDERVALUED";
        else if (difference > 0.05) return "OVERVALUED";
        else return "FAIR_VALUE";
    }
    
    /**
     * Calculate break-even points for option strategies
     */
    public double calculateBreakeven(double strike, double premium, String optionType, String action) {
        if (action.equals("BUY")) {
            if (optionType.equals("CE")) {
                return strike + premium;
            } else { // PE
                return strike - premium;
            }
        } else { // SELL
            if (optionType.equals("CE")) {
                return strike + premium;
            } else { // PE
                return strike - premium;
            }
        }
    }
    
    /**
     * Calculate maximum profit/loss for option positions
     */
    public Map<String, Double> calculateProfitLoss(double strike, double premium, String optionType, String action) {
        Map<String, Double> profitLoss = new HashMap<>();
        
        if (action.equals("BUY")) {
            profitLoss.put("max_loss", premium);
            if (optionType.equals("CE")) {
                profitLoss.put("max_profit", Double.POSITIVE_INFINITY);
            } else { // PE
                profitLoss.put("max_profit", strike - premium);
            }
        } else { // SELL
            profitLoss.put("max_profit", premium);
            if (optionType.equals("CE")) {
                profitLoss.put("max_loss", Double.POSITIVE_INFINITY);
            } else { // PE
                profitLoss.put("max_loss", strike - premium);
            }
        }
        
        return profitLoss;
    }
}

/**
 * Volatility Calculator for options pricing
 */
class VolatilityCalculator {
    
    private final Map<String, List<Double>> priceHistory = new HashMap<>();
    
    /**
     * Calculate implied volatility from market data
     */
    public double calculateImpliedVolatility(String symbol, RealMarketData marketData) {
        // Update price history
        priceHistory.computeIfAbsent(symbol, k -> new ArrayList<>()).add(marketData.price);
        
        List<Double> prices = priceHistory.get(symbol);
        
        if (prices.size() < 20) {
            // Use index-specific typical volatility
            return getTypicalVolatility(symbol);
        }
        
        // Calculate historical volatility
        double historicalVol = calculateHistoricalVolatility(prices, 20);
        
        // Adjust for current market conditions
        double adjustedVol = adjustVolatilityForMarketConditions(historicalVol, marketData);
        
        return Math.max(0.08, Math.min(0.60, adjustedVol)); // Bound between 8% and 60%
    }
    
    private double calculateHistoricalVolatility(List<Double> prices, int period) {
        if (prices.size() < period + 1) {
            return 0.20; // Default 20% volatility
        }
        
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < Math.min(prices.size(), period + 1); i++) {
            double dailyReturn = Math.log(prices.get(prices.size() - i) / prices.get(prices.size() - i - 1));
            returns.add(dailyReturn);
        }
        
        // Calculate standard deviation of returns
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
            .mapToDouble(r -> Math.pow(r - mean, 2))
            .average().orElse(0);
        
        // Annualize volatility (252 trading days)
        return Math.sqrt(variance * 252);
    }
    
    private double adjustVolatilityForMarketConditions(double historicalVol, RealMarketData marketData) {
        double adjustment = 1.0;
        
        // High volume increases volatility
        if (marketData.isHighVolume()) {
            adjustment *= 1.1;
        }
        
        // Large price moves increase volatility
        double absChange = Math.abs(marketData.changePercent);
        if (absChange > 2.0) {
            adjustment *= 1.2;
        } else if (absChange > 1.0) {
            adjustment *= 1.1;
        }
        
        return historicalVol * adjustment;
    }
    
    private double getTypicalVolatility(String symbol) {
        return switch (symbol) {
            case "NIFTY" -> 0.18;     // 18% typical volatility
            case "BANKNIFTY" -> 0.25; // 25% typical volatility
            case "FINNIFTY" -> 0.22;  // 22% typical volatility
            case "SENSEX" -> 0.18;    // 18% typical volatility
            default -> 0.20;
        };
    }
    
    /**
     * Calculate volatility smile for different strikes
     */
    public Map<Double, Double> calculateVolatilitySmile(String symbol, double spot, List<Double> strikes) {
        Map<Double, Double> volSmile = new HashMap<>();
        double atmVol = calculateImpliedVolatility(symbol, null);
        
        for (double strike : strikes) {
            double moneyness = strike / spot;
            double smileVol = atmVol * calculateVolatilitySmileMultiplier(moneyness);
            volSmile.put(strike, smileVol);
        }
        
        return volSmile;
    }
    
    private double calculateVolatilitySmileMultiplier(double moneyness) {
        // Typical volatility smile pattern - higher vol for OTM options
        if (moneyness < 0.95 || moneyness > 1.05) {
            return 1.1; // 10% higher vol for deep OTM
        } else if (moneyness < 0.98 || moneyness > 1.02) {
            return 1.05; // 5% higher vol for OTM
        } else {
            return 1.0; // ATM volatility
        }
    }
}