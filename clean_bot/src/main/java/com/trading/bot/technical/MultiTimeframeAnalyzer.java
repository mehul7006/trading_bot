package com.trading.bot.technical;

import com.trading.bot.market.SimpleMarketData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * PHASE 2 - STEP 2.1: Multi-Timeframe Analysis
 * Analyzes trends across multiple timeframes for better signal confirmation
 */
public class MultiTimeframeAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(MultiTimeframeAnalyzer.class);
    
    public enum Timeframe {
        SHORT_TERM(5),    // 5 periods
        MEDIUM_TERM(20),  // 20 periods  
        LONG_TERM(50);    // 50 periods
        
        public final int periods;
        
        Timeframe(int periods) {
            this.periods = periods;
        }
    }
    
    public static class TimeframeAnalysis {
        public final Timeframe timeframe;
        public final String trend;
        public final double strength;
        public final double confidence;
        public final String reasoning;
        
        public TimeframeAnalysis(Timeframe timeframe, String trend, double strength, double confidence, String reasoning) {
            this.timeframe = timeframe;
            this.trend = trend;
            this.strength = strength;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
        
        @Override
        public String toString() {
            return String.format("%s: %s (%.1f%% strength, %.1f%% confidence) - %s", 
                timeframe, trend, strength, confidence, reasoning);
        }
    }
    
    public static class MultiTimeframeResult {
        public final Map<Timeframe, TimeframeAnalysis> analyses;
        public final String overallTrend;
        public final double overallConfidence;
        public final String alignment;
        public final String reasoning;
        
        public MultiTimeframeResult(Map<Timeframe, TimeframeAnalysis> analyses, String overallTrend, 
                                  double overallConfidence, String alignment, String reasoning) {
            this.analyses = new HashMap<>(analyses);
            this.overallTrend = overallTrend;
            this.overallConfidence = overallConfidence;
            this.alignment = alignment;
            this.reasoning = reasoning;
        }
    }
    
    /**
     * PHASE 2 - STEP 2.1: Comprehensive Multi-Timeframe Analysis
     */
    public MultiTimeframeResult analyzeMultipleTimeframes(List<SimpleMarketData> priceHistory) {
        logger.info("Performing multi-timeframe analysis on {} data points", priceHistory.size());
        
        Map<Timeframe, TimeframeAnalysis> analyses = new HashMap<>();
        
        try {
            // Analyze each timeframe
            for (Timeframe timeframe : Timeframe.values()) {
                TimeframeAnalysis analysis = analyzeTimeframe(priceHistory, timeframe);
                analyses.put(timeframe, analysis);
                logger.debug("Timeframe analysis: {}", analysis);
            }
            
            // Determine overall trend and alignment
            MultiTimeframeResult result = combineTimeframeAnalyses(analyses);
            
            logger.info("Multi-timeframe result: {} trend with {}% confidence ({})", 
                result.overallTrend, result.overallConfidence, result.alignment);
                
            return result;
            
        } catch (Exception e) {
            logger.error("Error in multi-timeframe analysis: {}", e.getMessage(), e);
            return createDefaultResult();
        }
    }
    
    /**
     * Analyze single timeframe for trend and strength
     */
    private TimeframeAnalysis analyzeTimeframe(List<SimpleMarketData> priceHistory, Timeframe timeframe) {
        if (priceHistory.size() < timeframe.periods + 5) {
            return new TimeframeAnalysis(timeframe, "NEUTRAL", 0.0, 0.0, "Insufficient data");
        }
        
        try {
            List<Double> prices = extractRecentPrices(priceHistory, timeframe.periods);
            
            // Calculate moving averages for trend detection
            double shortMA = calculateMovingAverage(prices, Math.min(5, timeframe.periods / 4));
            double longMA = calculateMovingAverage(prices, timeframe.periods);
            
            // Determine trend direction
            String trend = determineTrend(shortMA, longMA, prices);
            
            // Calculate trend strength
            double strength = calculateTrendStrength(prices);
            
            // Calculate confidence based on consistency
            double confidence = calculateTrendConfidence(prices, trend);
            
            String reasoning = String.format("MA(%.2f) vs MA(%.2f), Strength: %.1f%%", 
                shortMA, longMA, strength);
            
            return new TimeframeAnalysis(timeframe, trend, strength, confidence, reasoning);
            
        } catch (Exception e) {
            logger.error("Error analyzing timeframe {}: {}", timeframe, e.getMessage());
            return new TimeframeAnalysis(timeframe, "NEUTRAL", 0.0, 0.0, "Analysis error");
        }
    }
    
    /**
     * Combine multiple timeframe analyses into overall result
     */
    private MultiTimeframeResult combineTimeframeAnalyses(Map<Timeframe, TimeframeAnalysis> analyses) {
        // Weight timeframes: Long-term (40%), Medium-term (35%), Short-term (25%)
        double longWeight = 0.40;
        double mediumWeight = 0.35;
        double shortWeight = 0.25;
        
        TimeframeAnalysis longTerm = analyses.get(Timeframe.LONG_TERM);
        TimeframeAnalysis mediumTerm = analyses.get(Timeframe.MEDIUM_TERM);
        TimeframeAnalysis shortTerm = analyses.get(Timeframe.SHORT_TERM);
        
        // Calculate weighted trend scores
        double bullishScore = 0;
        double bearishScore = 0;
        
        if ("BULLISH".equals(longTerm.trend)) bullishScore += longWeight * longTerm.confidence;
        else if ("BEARISH".equals(longTerm.trend)) bearishScore += longWeight * longTerm.confidence;
        
        if ("BULLISH".equals(mediumTerm.trend)) bullishScore += mediumWeight * mediumTerm.confidence;
        else if ("BEARISH".equals(mediumTerm.trend)) bearishScore += mediumWeight * mediumTerm.confidence;
        
        if ("BULLISH".equals(shortTerm.trend)) bullishScore += shortWeight * shortTerm.confidence;
        else if ("BEARISH".equals(shortTerm.trend)) bearishScore += shortWeight * shortTerm.confidence;
        
        // Determine overall trend
        String overallTrend;
        double overallConfidence;
        
        if (bullishScore > bearishScore + 10) {
            overallTrend = "BULLISH";
            overallConfidence = Math.min(95, bullishScore);
        } else if (bearishScore > bullishScore + 10) {
            overallTrend = "BEARISH";
            overallConfidence = Math.min(95, bearishScore);
        } else {
            overallTrend = "NEUTRAL";
            overallConfidence = 50 + Math.abs(bullishScore - bearishScore);
        }
        
        // Determine alignment quality
        String alignment = determineAlignment(analyses);
        
        String reasoning = String.format("Long: %s(%.1f), Medium: %s(%.1f), Short: %s(%.1f) -> %s",
            longTerm.trend, longTerm.confidence,
            mediumTerm.trend, mediumTerm.confidence,
            shortTerm.trend, shortTerm.confidence,
            alignment);
        
        return new MultiTimeframeResult(analyses, overallTrend, overallConfidence, alignment, reasoning);
    }
    
    /**
     * Determine alignment quality between timeframes
     */
    private String determineAlignment(Map<Timeframe, TimeframeAnalysis> analyses) {
        TimeframeAnalysis longTerm = analyses.get(Timeframe.LONG_TERM);
        TimeframeAnalysis mediumTerm = analyses.get(Timeframe.MEDIUM_TERM);
        TimeframeAnalysis shortTerm = analyses.get(Timeframe.SHORT_TERM);
        
        int agreements = 0;
        
        if (longTerm.trend.equals(mediumTerm.trend)) agreements++;
        if (mediumTerm.trend.equals(shortTerm.trend)) agreements++;
        if (longTerm.trend.equals(shortTerm.trend)) agreements++;
        
        switch (agreements) {
            case 3: return "STRONG_ALIGNMENT";
            case 2: return "PARTIAL_ALIGNMENT";
            case 1: return "WEAK_ALIGNMENT";
            default: return "DIVERGENCE";
        }
    }
    
    /**
     * Extract recent prices for analysis
     */
    private List<Double> extractRecentPrices(List<SimpleMarketData> priceHistory, int periods) {
        List<Double> prices = new ArrayList<>();
        int start = Math.max(0, priceHistory.size() - periods);
        
        for (int i = start; i < priceHistory.size(); i++) {
            prices.add(priceHistory.get(i).price);
        }
        
        return prices;
    }
    
    /**
     * Calculate simple moving average
     */
    private double calculateMovingAverage(List<Double> prices, int periods) {
        if (prices.size() < periods) {
            return prices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }
        
        int start = Math.max(0, prices.size() - periods);
        return prices.subList(start, prices.size()).stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);
    }
    
    /**
     * Determine trend direction based on moving averages and price action
     */
    private String determineTrend(double shortMA, double longMA, List<Double> prices) {
        double currentPrice = prices.get(prices.size() - 1);
        
        // Primary trend: short MA vs long MA
        if (shortMA > longMA * 1.005) { // 0.5% threshold
            return "BULLISH";
        } else if (shortMA < longMA * 0.995) {
            return "BEARISH";
        }
        
        // Secondary: current price vs moving averages
        if (currentPrice > Math.max(shortMA, longMA)) {
            return "BULLISH";
        } else if (currentPrice < Math.min(shortMA, longMA)) {
            return "BEARISH";
        }
        
        return "NEUTRAL";
    }
    
    /**
     * Calculate trend strength based on price consistency
     */
    private double calculateTrendStrength(List<Double> prices) {
        if (prices.size() < 3) return 0.0;
        
        int consistentMoves = 0;
        int totalMoves = prices.size() - 1;
        
        for (int i = 1; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (Math.abs(change) > prices.get(i - 1) * 0.001) { // 0.1% threshold
                if ((change > 0 && prices.get(i) > prices.get(0)) || 
                    (change < 0 && prices.get(i) < prices.get(0))) {
                    consistentMoves++;
                }
            }
        }
        
        return totalMoves > 0 ? (double) consistentMoves / totalMoves * 100 : 0.0;
    }
    
    /**
     * Calculate trend confidence based on various factors
     */
    private double calculateTrendConfidence(List<Double> prices, String trend) {
        if (prices.size() < 3) return 50.0;
        
        double confidence = 50.0; // Base confidence
        
        // Factor 1: Price momentum
        double firstPrice = prices.get(0);
        double lastPrice = prices.get(prices.size() - 1);
        double momentum = (lastPrice - firstPrice) / firstPrice;
        
        if (("BULLISH".equals(trend) && momentum > 0) || 
            ("BEARISH".equals(trend) && momentum < 0)) {
            confidence += Math.min(20, Math.abs(momentum) * 1000);
        }
        
        // Factor 2: Volatility (lower volatility = higher confidence)
        double avgPrice = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = prices.stream()
            .mapToDouble(p -> Math.pow(p - avgPrice, 2))
            .average()
            .orElse(0);
        double volatility = Math.sqrt(variance) / avgPrice;
        
        confidence -= Math.min(15, volatility * 1000);
        
        // Factor 3: Trend consistency
        double strength = calculateTrendStrength(prices);
        confidence += strength * 0.2;
        
        return Math.max(5, Math.min(95, confidence));
    }
    
    /**
     * Create default result for error cases
     */
    private MultiTimeframeResult createDefaultResult() {
        Map<Timeframe, TimeframeAnalysis> defaultAnalyses = new HashMap<>();
        
        for (Timeframe tf : Timeframe.values()) {
            defaultAnalyses.put(tf, new TimeframeAnalysis(tf, "NEUTRAL", 0.0, 50.0, "Default"));
        }
        
        return new MultiTimeframeResult(defaultAnalyses, "NEUTRAL", 50.0, "UNKNOWN", "Error in analysis");
    }
    
    /**
     * Get timeframe alignment bonus for confidence scoring
     */
    public double getAlignmentBonus(MultiTimeframeResult result) {
        switch (result.alignment) {
            case "STRONG_ALIGNMENT": return 15.0;
            case "PARTIAL_ALIGNMENT": return 10.0;
            case "WEAK_ALIGNMENT": return 5.0;
            default: return 0.0;
        }
    }
}