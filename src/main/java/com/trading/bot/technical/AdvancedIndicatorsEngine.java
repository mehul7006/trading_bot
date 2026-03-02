package com.trading.bot.technical;

import com.trading.bot.market.SimpleMarketData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

/**
 * PHASE 2 - STEP 2.2: Advanced Indicators Engine
 * Implements Stochastic, Williams %R, ADX, and other sophisticated indicators
 */
public class AdvancedIndicatorsEngine {
    private static final Logger logger = LoggerFactory.getLogger(AdvancedIndicatorsEngine.class);
    
    public static class StochasticResult {
        public final double percentK;
        public final double percentD;
        public final String signal;
        public final double strength;
        
        public StochasticResult(double percentK, double percentD, String signal, double strength) {
            this.percentK = percentK;
            this.percentD = percentD;
            this.signal = signal;
            this.strength = strength;
        }
        
        @Override
        public String toString() {
            return String.format("Stoch: K=%.2f, D=%.2f, Signal=%s (%.1f%%)", percentK, percentD, signal, strength);
        }
    }
    
    public static class WilliamsRResult {
        public final double williamsR;
        public final String signal;
        public final double strength;
        
        public WilliamsRResult(double williamsR, String signal, double strength) {
            this.williamsR = williamsR;
            this.signal = signal;
            this.strength = strength;
        }
        
        @Override
        public String toString() {
            return String.format("Williams%%R: %.2f, Signal=%s (%.1f%%)", williamsR, signal, strength);
        }
    }
    
    public static class ADXResult {
        public final double adx;
        public final double plusDI;
        public final double minusDI;
        public final String trendStrength;
        public final String direction;
        
        public ADXResult(double adx, double plusDI, double minusDI, String trendStrength, String direction) {
            this.adx = adx;
            this.plusDI = plusDI;
            this.minusDI = minusDI;
            this.trendStrength = trendStrength;
            this.direction = direction;
        }
        
        @Override
        public String toString() {
            return String.format("ADX: %.2f (%s trend), +DI=%.2f, -DI=%.2f, Direction=%s", 
                adx, trendStrength, plusDI, minusDI, direction);
        }
    }
    
    public static class AdvancedIndicatorsResult {
        public final StochasticResult stochastic;
        public final WilliamsRResult williamsR;
        public final ADXResult adx;
        public final double confluenceScore;
        public final String overallSignal;
        public final String reasoning;
        
        public AdvancedIndicatorsResult(StochasticResult stochastic, WilliamsRResult williamsR, 
                                     ADXResult adx, double confluenceScore, String overallSignal, String reasoning) {
            this.stochastic = stochastic;
            this.williamsR = williamsR;
            this.adx = adx;
            this.confluenceScore = confluenceScore;
            this.overallSignal = overallSignal;
            this.reasoning = reasoning;
        }
    }
    
    /**
     * PHASE 2 - STEP 2.2: Comprehensive Advanced Indicators Analysis
     */
    public AdvancedIndicatorsResult analyzeAdvancedIndicators(List<SimpleMarketData> priceHistory) {
        logger.info("Performing advanced indicators analysis on {} data points", priceHistory.size());
        
        if (priceHistory.size() < 20) {
            return createDefaultResult("Insufficient data for advanced indicators");
        }
        
        try {
            // Calculate individual indicators
            StochasticResult stochastic = calculateStochastic(priceHistory, 14, 3);
            WilliamsRResult williamsR = calculateWilliamsR(priceHistory, 14);
            ADXResult adx = calculateADX(priceHistory, 14);
            
            // Calculate confluence and overall signal
            double confluenceScore = calculateConfluence(stochastic, williamsR, adx);
            String overallSignal = determineOverallSignal(stochastic, williamsR, adx, confluenceScore);
            
            String reasoning = String.format("Stoch: %s, Williams: %s, ADX: %s, Confluence: %.1f%%",
                stochastic.signal, williamsR.signal, adx.direction, confluenceScore);
            
            AdvancedIndicatorsResult result = new AdvancedIndicatorsResult(
                stochastic, williamsR, adx, confluenceScore, overallSignal, reasoning);
            
            logger.info("Advanced indicators result: {} with {}% confluence", overallSignal, confluenceScore);
            return result;
            
        } catch (Exception e) {
            logger.error("Error in advanced indicators analysis: {}", e.getMessage(), e);
            return createDefaultResult("Analysis error: " + e.getMessage());
        }
    }
    
    /**
     * Calculate Stochastic Oscillator (%K and %D)
     */
    public StochasticResult calculateStochastic(List<SimpleMarketData> priceHistory, int kPeriod, int dPeriod) {
        try {
            if (priceHistory.size() < kPeriod + dPeriod) {
                return new StochasticResult(50.0, 50.0, "NEUTRAL", 0.0);
            }
            
            List<Double> kValues = new ArrayList<>();
            
            // Calculate %K values
            for (int i = kPeriod - 1; i < priceHistory.size(); i++) {
                double currentClose = priceHistory.get(i).price;
                double lowestLow = Double.MAX_VALUE;
                double highestHigh = Double.MIN_VALUE;
                
                // Find highest high and lowest low in the period
                for (int j = i - kPeriod + 1; j <= i; j++) {
                    double price = priceHistory.get(j).price;
                    if (price > highestHigh) highestHigh = price;
                    if (price < lowestLow) lowestLow = price;
                }
                
                double percentK = ((currentClose - lowestLow) / (highestHigh - lowestLow)) * 100;
                kValues.add(percentK);
            }
            
            // Calculate current %K
            double currentK = kValues.get(kValues.size() - 1);
            
            // Calculate %D (simple moving average of %K)
            double percentD = kValues.size() >= dPeriod ? 
                kValues.subList(kValues.size() - dPeriod, kValues.size()).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(50.0) : currentK;
            
            // Determine signal
            String signal = determineStochasticSignal(currentK, percentD);
            double strength = calculateStochasticStrength(currentK, percentD);
            
            return new StochasticResult(currentK, percentD, signal, strength);
            
        } catch (Exception e) {
            logger.error("Error calculating Stochastic: {}", e.getMessage());
            return new StochasticResult(50.0, 50.0, "NEUTRAL", 0.0);
        }
    }
    
    /**
     * Calculate Williams %R
     */
    public WilliamsRResult calculateWilliamsR(List<SimpleMarketData> priceHistory, int period) {
        try {
            if (priceHistory.size() < period) {
                return new WilliamsRResult(-50.0, "NEUTRAL", 0.0);
            }
            
            double currentClose = priceHistory.get(priceHistory.size() - 1).price;
            double highestHigh = Double.MIN_VALUE;
            double lowestLow = Double.MAX_VALUE;
            
            // Find highest high and lowest low in the period
            int start = priceHistory.size() - period;
            for (int i = start; i < priceHistory.size(); i++) {
                double price = priceHistory.get(i).price;
                if (price > highestHigh) highestHigh = price;
                if (price < lowestLow) lowestLow = price;
            }
            
            double williamsR = ((highestHigh - currentClose) / (highestHigh - lowestLow)) * -100;
            
            String signal = determineWilliamsRSignal(williamsR);
            double strength = calculateWilliamsRStrength(williamsR);
            
            return new WilliamsRResult(williamsR, signal, strength);
            
        } catch (Exception e) {
            logger.error("Error calculating Williams %R: {}", e.getMessage());
            return new WilliamsRResult(-50.0, "NEUTRAL", 0.0);
        }
    }
    
    /**
     * Calculate ADX (Average Directional Index)
     */
    public ADXResult calculateADX(List<SimpleMarketData> priceHistory, int period) {
        try {
            if (priceHistory.size() < period + 5) {
                return new ADXResult(25.0, 25.0, 25.0, "WEAK", "NEUTRAL");
            }
            
            // Simplified ADX calculation
            List<Double> trueRanges = new ArrayList<>();
            List<Double> plusDMs = new ArrayList<>();
            List<Double> minusDMs = new ArrayList<>();
            
            // Calculate True Range and Directional Movements
            for (int i = 1; i < priceHistory.size(); i++) {
                double currentHigh = priceHistory.get(i).price;
                double currentLow = priceHistory.get(i).price * 0.995; // Simplified: assume low is 0.5% below price
                double previousClose = priceHistory.get(i - 1).price;
                
                // True Range
                double tr1 = currentHigh - currentLow;
                double tr2 = Math.abs(currentHigh - previousClose);
                double tr3 = Math.abs(currentLow - previousClose);
                double trueRange = Math.max(tr1, Math.max(tr2, tr3));
                trueRanges.add(trueRange);
                
                // Directional Movements
                double plusDM = currentHigh - priceHistory.get(i - 1).price;
                double minusDM = priceHistory.get(i - 1).price - currentLow;
                
                plusDMs.add(Math.max(0, plusDM > minusDM ? plusDM : 0));
                minusDMs.add(Math.max(0, minusDM > plusDM ? minusDM : 0));
            }
            
            // Calculate smoothed values (simplified)
            double avgTR = trueRanges.subList(Math.max(0, trueRanges.size() - period), trueRanges.size())
                .stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
            
            double avgPlusDM = plusDMs.subList(Math.max(0, plusDMs.size() - period), plusDMs.size())
                .stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                
            double avgMinusDM = minusDMs.subList(Math.max(0, minusDMs.size() - period), minusDMs.size())
                .stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            
            // Calculate DI values
            double plusDI = (avgPlusDM / avgTR) * 100;
            double minusDI = (avgMinusDM / avgTR) * 100;
            
            // Calculate ADX
            double dx = Math.abs(plusDI - minusDI) / (plusDI + minusDI) * 100;
            double adx = Math.min(75, Math.max(0, dx)); // Simplified ADX
            
            String trendStrength = determineTrendStrength(adx);
            String direction = plusDI > minusDI ? "BULLISH" : "BEARISH";
            
            return new ADXResult(adx, plusDI, minusDI, trendStrength, direction);
            
        } catch (Exception e) {
            logger.error("Error calculating ADX: {}", e.getMessage());
            return new ADXResult(25.0, 25.0, 25.0, "WEAK", "NEUTRAL");
        }
    }
    
    /**
     * Determine Stochastic signal based on %K and %D values
     */
    private String determineStochasticSignal(double percentK, double percentD) {
        if (percentK < 20 && percentD < 20) {
            return "OVERSOLD"; // Potential buy signal
        } else if (percentK > 80 && percentD > 80) {
            return "OVERBOUGHT"; // Potential sell signal
        } else if (percentK > percentD && percentK > 50) {
            return "BULLISH";
        } else if (percentK < percentD && percentK < 50) {
            return "BEARISH";
        }
        return "NEUTRAL";
    }
    
    /**
     * Calculate Stochastic strength
     */
    private double calculateStochasticStrength(double percentK, double percentD) {
        double extremity = Math.max(
            Math.abs(percentK - 50), 
            Math.abs(percentD - 50)
        );
        return Math.min(100, extremity * 2);
    }
    
    /**
     * Determine Williams %R signal
     */
    private String determineWilliamsRSignal(double williamsR) {
        if (williamsR < -80) {
            return "OVERSOLD"; // Potential buy signal
        } else if (williamsR > -20) {
            return "OVERBOUGHT"; // Potential sell signal
        } else if (williamsR > -50) {
            return "BULLISH";
        } else {
            return "BEARISH";
        }
    }
    
    /**
     * Calculate Williams %R strength
     */
    private double calculateWilliamsRStrength(double williamsR) {
        double extremity = Math.abs(williamsR + 50); // Distance from center (-50)
        return Math.min(100, extremity * 2);
    }
    
    /**
     * Determine trend strength from ADX
     */
    private String determineTrendStrength(double adx) {
        if (adx > 50) return "VERY_STRONG";
        if (adx > 25) return "STRONG";
        if (adx > 15) return "MODERATE";
        return "WEAK";
    }
    
    /**
     * Calculate confluence between all indicators
     */
    private double calculateConfluence(StochasticResult stochastic, WilliamsRResult williamsR, ADXResult adx) {
        int agreements = 0;
        int totalComparisons = 0;
        
        // Compare Stochastic and Williams %R
        if (isBullishSignal(stochastic.signal) == isBullishSignal(williamsR.signal)) {
            agreements++;
        }
        totalComparisons++;
        
        // Compare with ADX direction
        if (isBullishSignal(stochastic.signal) == "BULLISH".equals(adx.direction)) {
            agreements++;
        }
        totalComparisons++;
        
        if (isBullishSignal(williamsR.signal) == "BULLISH".equals(adx.direction)) {
            agreements++;
        }
        totalComparisons++;
        
        double baseConfluence = (double) agreements / totalComparisons * 100;
        
        // Bonus for trend strength
        if ("STRONG".equals(adx.trendStrength) || "VERY_STRONG".equals(adx.trendStrength)) {
            baseConfluence += 10;
        }
        
        return Math.min(100, baseConfluence);
    }
    
    /**
     * Check if signal is bullish
     */
    private boolean isBullishSignal(String signal) {
        return "BULLISH".equals(signal) || "OVERSOLD".equals(signal);
    }
    
    /**
     * Determine overall signal from all indicators
     */
    private String determineOverallSignal(StochasticResult stochastic, WilliamsRResult williamsR, 
                                        ADXResult adx, double confluenceScore) {
        int bullishVotes = 0;
        int bearishVotes = 0;
        
        if (isBullishSignal(stochastic.signal)) bullishVotes++;
        else bearishVotes++;
        
        if (isBullishSignal(williamsR.signal)) bullishVotes++;
        else bearishVotes++;
        
        if ("BULLISH".equals(adx.direction)) bullishVotes++;
        else bearishVotes++;
        
        if (bullishVotes > bearishVotes && confluenceScore > 60) {
            return "BULLISH";
        } else if (bearishVotes > bullishVotes && confluenceScore > 60) {
            return "BEARISH";
        }
        
        return "NEUTRAL";
    }
    
    /**
     * Create default result for error cases
     */
    private AdvancedIndicatorsResult createDefaultResult(String reason) {
        StochasticResult defaultStoch = new StochasticResult(50.0, 50.0, "NEUTRAL", 0.0);
        WilliamsRResult defaultWilliams = new WilliamsRResult(-50.0, "NEUTRAL", 0.0);
        ADXResult defaultADX = new ADXResult(25.0, 25.0, 25.0, "WEAK", "NEUTRAL");
        
        return new AdvancedIndicatorsResult(defaultStoch, defaultWilliams, defaultADX, 
                                          50.0, "NEUTRAL", reason);
    }
    
    /**
     * Get confidence boost based on indicators confluence
     */
    public double getConfidenceBoost(AdvancedIndicatorsResult result) {
        if (result.confluenceScore > 80) return 20.0;
        if (result.confluenceScore > 70) return 15.0;
        if (result.confluenceScore > 60) return 10.0;
        return 0.0;
    }
}