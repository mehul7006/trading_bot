package com.trading.bot.prediction;

import java.util.*;
import java.time.LocalDateTime;

/**
 * VOLUME ANALYSIS ENGINE FOR INSTITUTIONAL ACTIVITY DETECTION
 * Detects volume spikes, order flow patterns, and institutional movements
 */
public class VolumeAnalyzer {
    
    /**
     * VOLUME ANALYSIS RESULT
     */
    public static class VolumeAnalysisResult {
        public final long currentVolume;
        public final long avgVolume20;
        public final double volumeRatio; // Current/Average
        public final boolean volumeSpike; // >3x average
        public final boolean institutionalActivity; // Large order detection
        public final VolumePattern pattern;
        public final double volumeScore; // 0.0 to 1.0
        public final String signal; // BULLISH_VOLUME, BEARISH_VOLUME, NEUTRAL
        public final String reasoning;
        
        public VolumeAnalysisResult(long currentVolume, long avgVolume20, double volumeRatio,
                                  boolean volumeSpike, boolean institutionalActivity, 
                                  VolumePattern pattern, double volumeScore, String signal, String reasoning) {
            this.currentVolume = currentVolume;
            this.avgVolume20 = avgVolume20;
            this.volumeRatio = volumeRatio;
            this.volumeSpike = volumeSpike;
            this.institutionalActivity = institutionalActivity;
            this.pattern = pattern;
            this.volumeScore = volumeScore;
            this.signal = signal;
            this.reasoning = reasoning;
        }
    }
    
    /**
     * VOLUME PATTERN TYPES
     */
    public static class VolumePattern {
        public final String type; // ACCUMULATION, DISTRIBUTION, BREAKOUT, CONSOLIDATION
        public final double strength; // 0.0 to 1.0
        public final int duration; // Number of periods
        public final String description;
        
        public VolumePattern(String type, double strength, int duration, String description) {
            this.type = type;
            this.strength = strength;
            this.duration = duration;
            this.description = description;
        }
    }
    
    /**
     * PRICE-VOLUME RELATIONSHIP
     */
    public static class PriceVolumeRelation {
        public final boolean priceVolumeConfirmation; // Price and volume in same direction
        public final double divergenceStrength; // If diverging, how strong (0.0 to 1.0)
        public final String relationship; // CONFIRMING, DIVERGING, NEUTRAL
        
        public PriceVolumeRelation(boolean confirmation, double divergence, String relationship) {
            this.priceVolumeConfirmation = confirmation;
            this.divergenceStrength = divergence;
            this.relationship = relationship;
        }
    }
    
    /**
     * ANALYZE VOLUME FOR MOVEMENT PREDICTION
     */
    public VolumeAnalysisResult analyzeVolume(String index, List<IndexMovementPredictor.RealMarketData> marketHistory) {
        if (marketHistory.size() < 20) {
            return new VolumeAnalysisResult(1000000, 1000000, 1.0, false, false,
                new VolumePattern("INSUFFICIENT_DATA", 0.0, 0, "Not enough data"),
                0.5, "NEUTRAL", "Insufficient volume data for analysis");
        }
        
        System.out.println("📈 Analyzing " + index + " volume patterns...");
        
        // Extract volume and price data
        long[] volumes = marketHistory.stream().mapToLong(d -> d.volume).toArray();
        double[] prices = marketHistory.stream().mapToDouble(d -> d.price).toArray();
        
        // Current volume analysis
        long currentVolume = volumes[volumes.length - 1];
        long avgVolume20 = calculateAverageVolume(volumes, 20);
        double volumeRatio = (double) currentVolume / avgVolume20;
        
        // Volume spike detection
        boolean volumeSpike = detectVolumeSpike(volumes, 3.0); // 3x threshold
        
        // Institutional activity detection
        boolean institutionalActivity = detectInstitutionalActivity(volumes, prices);
        
        // Volume pattern analysis
        VolumePattern pattern = analyzeVolumePattern(volumes, prices);
        
        // Price-volume relationship
        PriceVolumeRelation pvRelation = analyzePriceVolumeRelationship(volumes, prices);
        
        // Calculate volume score (30% weight in overall prediction)
        double volumeScore = calculateVolumeScore(volumeRatio, volumeSpike, institutionalActivity, pattern, pvRelation);
        
        // Determine volume signal
        String signal = determineVolumeSignal(volumeScore, pattern, pvRelation, volumeSpike);
        
        // Generate reasoning
        String reasoning = generateVolumeReasoning(volumeRatio, volumeSpike, institutionalActivity, pattern, pvRelation, signal);
        
        System.out.println("   📊 Current Volume: " + formatVolume(currentVolume));
        System.out.println("   📊 Average Volume: " + formatVolume(avgVolume20));
        System.out.println("   📊 Volume Ratio: " + String.format("%.2f", volumeRatio) + "x");
        System.out.println("   📊 Volume Spike: " + volumeSpike);
        System.out.println("   📊 Institutional Activity: " + institutionalActivity);
        System.out.println("   📊 Pattern: " + pattern.type);
        System.out.println("   📊 Volume Score: " + String.format("%.1f%%", volumeScore * 100));
        System.out.println("   🎯 Signal: " + signal);
        
        return new VolumeAnalysisResult(currentVolume, avgVolume20, volumeRatio, volumeSpike,
                                      institutionalActivity, pattern, volumeScore, signal, reasoning);
    }
    
    /**
     * CALCULATE AVERAGE VOLUME
     */
    private long calculateAverageVolume(long[] volumes, int periods) {
        if (volumes.length < periods) periods = volumes.length;
        
        long sum = 0;
        for (int i = volumes.length - periods; i < volumes.length; i++) {
            sum += volumes[i];
        }
        return sum / periods;
    }
    
    /**
     * DETECT VOLUME SPIKE (Unusual activity)
     */
    private boolean detectVolumeSpike(long[] volumes, double threshold) {
        if (volumes.length < 20) return false;
        
        long currentVolume = volumes[volumes.length - 1];
        long avgVolume = calculateAverageVolume(volumes, 20);
        
        return (double) currentVolume / avgVolume > threshold;
    }
    
    /**
     * DETECT INSTITUTIONAL ACTIVITY
     */
    private boolean detectInstitutionalActivity(long[] volumes, double[] prices) {
        if (volumes.length < 10) return false;
        
        // Look for sustained high volume with price movement
        int highVolumeCount = 0;
        long avgVolume = calculateAverageVolume(volumes, 20);
        
        // Check last 5 periods for sustained high volume
        for (int i = Math.max(0, volumes.length - 5); i < volumes.length; i++) {
            if (volumes[i] > avgVolume * 1.5) {
                highVolumeCount++;
            }
        }
        
        // Check for volume with directional price movement
        boolean directionalMovement = false;
        if (prices.length >= 5) {
            double priceChange = prices[prices.length - 1] - prices[prices.length - 5];
            double priceChangePercent = Math.abs(priceChange / prices[prices.length - 5]) * 100;
            
            if (priceChangePercent > 0.5) { // >0.5% move with volume
                directionalMovement = true;
            }
        }
        
        return highVolumeCount >= 3 && directionalMovement;
    }
    
    /**
     * ANALYZE VOLUME PATTERN
     */
    private VolumePattern analyzeVolumePattern(long[] volumes, double[] prices) {
        if (volumes.length < 15) {
            return new VolumePattern("INSUFFICIENT_DATA", 0.0, 0, "Not enough data");
        }
        
        // Analyze recent 15 periods
        int lookback = Math.min(15, volumes.length);
        long[] recentVolumes = Arrays.copyOfRange(volumes, volumes.length - lookback, volumes.length);
        double[] recentPrices = Arrays.copyOfRange(prices, prices.length - lookback, prices.length);
        
        // Calculate volume trend
        double volumeTrend = calculateVolumeTrend(recentVolumes);
        double priceTrend = calculatePriceTrend(recentPrices);
        
        // Determine pattern type
        String patternType = "CONSOLIDATION";
        double strength = 0.5;
        String description = "Normal trading activity";
        
        if (volumeTrend > 0.2 && priceTrend > 0.1) {
            patternType = "ACCUMULATION";
            strength = Math.min(1.0, volumeTrend + priceTrend);
            description = "Institutional buying accumulation detected";
        } else if (volumeTrend > 0.2 && priceTrend < -0.1) {
            patternType = "DISTRIBUTION";
            strength = Math.min(1.0, volumeTrend - priceTrend);
            description = "Institutional selling distribution detected";
        } else if (volumeTrend > 0.3) {
            patternType = "BREAKOUT";
            strength = Math.min(1.0, volumeTrend * 1.5);
            description = "High volume breakout pattern";
        }
        
        return new VolumePattern(patternType, strength, lookback, description);
    }
    
    /**
     * ANALYZE PRICE-VOLUME RELATIONSHIP
     */
    private PriceVolumeRelation analyzePriceVolumeRelationship(long[] volumes, double[] prices) {
        if (volumes.length < 10 || prices.length < 10) {
            return new PriceVolumeRelation(true, 0.0, "NEUTRAL");
        }
        
        // Get recent data
        int periods = Math.min(10, volumes.length);
        
        // Calculate correlation between volume and price changes
        double volumeChange = calculateVolumeChange(volumes, periods);
        double priceChange = calculatePriceChange(prices, periods);
        
        // Determine relationship
        boolean confirmation = (volumeChange > 0 && priceChange > 0) || (volumeChange < 0 && priceChange < 0);
        
        double divergenceStrength = 0.0;
        String relationship = "NEUTRAL";
        
        if (confirmation) {
            relationship = "CONFIRMING";
        } else {
            divergenceStrength = Math.abs(volumeChange - priceChange) / 2.0;
            relationship = "DIVERGING";
        }
        
        return new PriceVolumeRelation(confirmation, divergenceStrength, relationship);
    }
    
    /**
     * CALCULATE VOLUME TREND
     */
    private double calculateVolumeTrend(long[] volumes) {
        if (volumes.length < 5) return 0.0;
        
        // Compare recent 5 vs previous 5
        int half = volumes.length / 2;
        
        long recentAvg = 0;
        for (int i = half; i < volumes.length; i++) {
            recentAvg += volumes[i];
        }
        recentAvg /= (volumes.length - half);
        
        long previousAvg = 0;
        for (int i = 0; i < half; i++) {
            previousAvg += volumes[i];
        }
        previousAvg /= half;
        
        return (double) (recentAvg - previousAvg) / previousAvg;
    }
    
    /**
     * CALCULATE PRICE TREND
     */
    private double calculatePriceTrend(double[] prices) {
        if (prices.length < 2) return 0.0;
        
        return (prices[prices.length - 1] - prices[0]) / prices[0];
    }
    
    /**
     * CALCULATE VOLUME CHANGE
     */
    private double calculateVolumeChange(long[] volumes, int periods) {
        if (volumes.length < periods) return 0.0;
        
        long currentAvg = 0;
        for (int i = volumes.length - periods; i < volumes.length; i++) {
            currentAvg += volumes[i];
        }
        currentAvg /= periods;
        
        long previousAvg = 0;
        for (int i = volumes.length - periods * 2; i < volumes.length - periods; i++) {
            previousAvg += volumes[i];
        }
        previousAvg /= periods;
        
        return (double) (currentAvg - previousAvg) / previousAvg;
    }
    
    /**
     * CALCULATE PRICE CHANGE
     */
    private double calculatePriceChange(double[] prices, int periods) {
        if (prices.length < periods) return 0.0;
        
        double recent = prices[prices.length - 1];
        double previous = prices[prices.length - periods];
        
        return (recent - previous) / previous;
    }
    
    /**
     * CALCULATE VOLUME SCORE (30% weight in prediction)
     */
    private double calculateVolumeScore(double volumeRatio, boolean volumeSpike, boolean institutionalActivity,
                                      VolumePattern pattern, PriceVolumeRelation pvRelation) {
        double score = 0.0;
        
        // Volume ratio scoring (40% of volume score)
        double ratioScore = 0.5; // Neutral
        if (volumeRatio > 3.0) ratioScore = 1.0; // Very high
        else if (volumeRatio > 2.0) ratioScore = 0.8; // High
        else if (volumeRatio > 1.5) ratioScore = 0.7; // Above average
        else if (volumeRatio < 0.5) ratioScore = 0.2; // Very low
        score += ratioScore * 0.4;
        
        // Volume spike bonus (20% of volume score)
        if (volumeSpike) score += 0.2;
        
        // Institutional activity bonus (20% of volume score)
        if (institutionalActivity) score += 0.2;
        
        // Pattern scoring (15% of volume score)
        double patternScore = 0.5;
        if (pattern.type.equals("ACCUMULATION") || pattern.type.equals("BREAKOUT")) {
            patternScore = 0.5 + (pattern.strength * 0.5);
        } else if (pattern.type.equals("DISTRIBUTION")) {
            patternScore = 0.5 - (pattern.strength * 0.3);
        }
        score += patternScore * 0.15;
        
        // Price-volume relationship (5% of volume score)
        double pvScore = 0.5;
        if (pvRelation.relationship.equals("CONFIRMING")) {
            pvScore = 0.8;
        } else if (pvRelation.relationship.equals("DIVERGING")) {
            pvScore = 0.3;
        }
        score += pvScore * 0.05;
        
        return Math.max(0.0, Math.min(1.0, score));
    }
    
    /**
     * DETERMINE VOLUME SIGNAL
     */
    private String determineVolumeSignal(double volumeScore, VolumePattern pattern, 
                                       PriceVolumeRelation pvRelation, boolean volumeSpike) {
        if (volumeScore > 0.75 && (pattern.type.equals("ACCUMULATION") || pattern.type.equals("BREAKOUT"))) {
            return "BULLISH_VOLUME";
        } else if (volumeScore < 0.25 || pattern.type.equals("DISTRIBUTION")) {
            return "BEARISH_VOLUME";
        } else {
            return "NEUTRAL";
        }
    }
    
    /**
     * GENERATE VOLUME REASONING
     */
    private String generateVolumeReasoning(double volumeRatio, boolean volumeSpike, boolean institutionalActivity,
                                         VolumePattern pattern, PriceVolumeRelation pvRelation, String signal) {
        StringBuilder reasoning = new StringBuilder();
        reasoning.append("📈 Volume Analysis: ");
        
        if (signal.equals("BULLISH_VOLUME")) {
            reasoning.append("Strong bullish volume signals\n");
            if (volumeSpike) reasoning.append("• Volume spike ").append(String.format("%.1f", volumeRatio)).append("x average\n");
            if (institutionalActivity) reasoning.append("• Institutional buying activity detected\n");
            if (pattern.type.equals("ACCUMULATION")) reasoning.append("• Accumulation pattern confirmed\n");
            if (pvRelation.relationship.equals("CONFIRMING")) reasoning.append("• Price-volume confirmation strong\n");
        } else if (signal.equals("BEARISH_VOLUME")) {
            reasoning.append("Concerning volume patterns\n");
            if (pattern.type.equals("DISTRIBUTION")) reasoning.append("• Distribution pattern detected\n");
            if (institutionalActivity) reasoning.append("• Institutional selling pressure\n");
            if (pvRelation.relationship.equals("DIVERGING")) reasoning.append("• Price-volume divergence warning\n");
        } else {
            reasoning.append("Normal volume activity\n");
            reasoning.append("• Volume ratio: ").append(String.format("%.2f", volumeRatio)).append("x\n");
            reasoning.append("• Pattern: ").append(pattern.description).append("\n");
        }
        
        return reasoning.toString();
    }
    
    /**
     * FORMAT VOLUME FOR DISPLAY
     */
    private String formatVolume(long volume) {
        if (volume >= 1_000_000) {
            return String.format("%.1fM", volume / 1_000_000.0);
        } else if (volume >= 1_000) {
            return String.format("%.1fK", volume / 1_000.0);
        } else {
            return String.valueOf(volume);
        }
    }
}