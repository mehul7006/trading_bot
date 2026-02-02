package com.trading.bot.smartmoney;

import com.trading.bot.market.SimpleMarketData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * PHASE 3 - STEP 3.2: Fair Value Gap (FVG) Detection
 * Identifies price imbalances where smart money created gaps
 * that need to be filled for market equilibrium
 */
public class FairValueGapDetector {
    private static final Logger logger = LoggerFactory.getLogger(FairValueGapDetector.class);
    
    public enum FVGType {
        BULLISH_FVG,    // Upward imbalance - expect price to return and bounce
        BEARISH_FVG,    // Downward imbalance - expect price to return and reject
        BALANCED_FVG    // Neutral imbalance
    }
    
    public enum FVGStatus {
        UNFILLED,       // Gap still open
        PARTIALLY_FILLED, // Gap partially closed
        FILLED,         // Gap completely filled
        INVALIDATED     // Gap no longer relevant
    }
    
    public static class FairValueGap {
        public final double upperLevel;
        public final double lowerLevel;
        public final double midLevel;
        public final FVGType type;
        public final FVGStatus status;
        public final LocalDateTime formationTime;
        public final double volumeImbalance;
        public final double priceImbalance;
        public final double fillPercentage;
        public final String reasoning;
        
        public FairValueGap(double upperLevel, double lowerLevel, FVGType type, FVGStatus status,
                           LocalDateTime formationTime, double volumeImbalance, double priceImbalance,
                           double fillPercentage, String reasoning) {
            this.upperLevel = upperLevel;
            this.lowerLevel = lowerLevel;
            this.midLevel = (upperLevel + lowerLevel) / 2;
            this.type = type;
            this.status = status;
            this.formationTime = formationTime;
            this.volumeImbalance = volumeImbalance;
            this.priceImbalance = priceImbalance;
            this.fillPercentage = fillPercentage;
            this.reasoning = reasoning;
        }
        
        @Override
        public String toString() {
            return String.format("%s FVG: %.2f-%.2f (%s, %.1f%% filled) - %s",
                type, lowerLevel, upperLevel, status, fillPercentage, reasoning);
        }
    }
    
    public static class FVGAnalysis {
        public final List<FairValueGap> detectedGaps;
        public final FairValueGap nearestUnfilledGap;
        public final double currentPrice;
        public final String overallImbalance;
        public final double imbalanceStrength;
        public final String tradingRecommendation;
        public final List<Double> keyLevels;
        
        public FVGAnalysis(List<FairValueGap> detectedGaps, FairValueGap nearestUnfilledGap,
                          double currentPrice, String overallImbalance, double imbalanceStrength,
                          String tradingRecommendation, List<Double> keyLevels) {
            this.detectedGaps = new ArrayList<>(detectedGaps);
            this.nearestUnfilledGap = nearestUnfilledGap;
            this.currentPrice = currentPrice;
            this.overallImbalance = overallImbalance;
            this.imbalanceStrength = imbalanceStrength;
            this.tradingRecommendation = tradingRecommendation;
            this.keyLevels = new ArrayList<>(keyLevels);
        }
    }
    
    /**
     * PHASE 3 - STEP 3.2: Detect Fair Value Gaps
     */
    public FVGAnalysis detectFairValueGaps(List<SimpleMarketData> priceHistory) {
        logger.info("Detecting Fair Value Gaps from {} data points", priceHistory.size());
        
        if (priceHistory.size() < 20) {
            return createDefaultFVGAnalysis(priceHistory);
        }
        
        try {
            List<FairValueGap> detectedGaps = new ArrayList<>();
            
            // Step 1: Identify price imbalances (3-candle pattern)
            for (int i = 1; i < priceHistory.size() - 1; i++) {
                FairValueGap gap = identifyFVGPattern(priceHistory, i);
                if (gap != null) {
                    detectedGaps.add(gap);
                }
            }
            
            // Step 2: Update gap statuses based on subsequent price action
            List<FairValueGap> updatedGaps = updateGapStatuses(detectedGaps, priceHistory);
            
            // Step 3: Find nearest unfilled gap
            double currentPrice = priceHistory.get(priceHistory.size() - 1).price;
            FairValueGap nearestGap = findNearestUnfilledGap(updatedGaps, currentPrice);
            
            // Step 4: Analyze overall market imbalance
            String overallImbalance = analyzeOverallImbalance(updatedGaps, currentPrice);
            double imbalanceStrength = calculateImbalanceStrength(updatedGaps, currentPrice);
            
            // Step 5: Generate trading recommendation
            String recommendation = generateFVGRecommendation(nearestGap, overallImbalance, imbalanceStrength, currentPrice);
            
            // Step 6: Extract key levels
            List<Double> keyLevels = extractKeyLevels(updatedGaps);
            
            FVGAnalysis analysis = new FVGAnalysis(
                updatedGaps, nearestGap, currentPrice, overallImbalance,
                imbalanceStrength, recommendation, keyLevels);
            
            logger.info("FVG analysis completed: {} gaps detected, {} unfilled, strength: {:.1f}%",
                detectedGaps.size(), 
                updatedGaps.stream().filter(g -> g.status == FVGStatus.UNFILLED).count(),
                imbalanceStrength);
            
            return analysis;
            
        } catch (Exception e) {
            logger.error("Error in Fair Value Gap detection: {}", e.getMessage(), e);
            return createDefaultFVGAnalysis(priceHistory);
        }
    }
    
    /**
     * Identify FVG pattern using 3-candle analysis
     */
    private FairValueGap identifyFVGPattern(List<SimpleMarketData> priceHistory, int centerIndex) {
        try {
            if (centerIndex < 1 || centerIndex >= priceHistory.size() - 1) {
                return null;
            }
            
            SimpleMarketData previous = priceHistory.get(centerIndex - 1);
            SimpleMarketData center = priceHistory.get(centerIndex);
            SimpleMarketData next = priceHistory.get(centerIndex + 1);
            
            // Simulate high/low prices (simplified for demonstration)
            double prevHigh = previous.price * 1.002;
            double prevLow = previous.price * 0.998;
            double centerHigh = center.price * 1.002;
            double centerLow = center.price * 0.998;
            double nextHigh = next.price * 1.002;
            double nextLow = next.price * 0.998;
            
            // Check for Bullish FVG: gap between previous high and next low
            if (nextLow > prevHigh && center.price > previous.price && next.price > center.price) {
                // Strong upward move created imbalance
                double volumeImbalance = (center.volume - previous.volume) / (double) previous.volume;
                double priceImbalance = (next.price - previous.price) / previous.price;
                
                if (priceImbalance > 0.008 && volumeImbalance > 0.2) { // 0.8% price move, 20% volume increase
                    String reasoning = String.format("Bullish imbalance: %.1f%% price gap, %.1f%% volume spike",
                        priceImbalance * 100, volumeImbalance * 100);
                    
                    return new FairValueGap(
                        nextLow, prevHigh, FVGType.BULLISH_FVG, FVGStatus.UNFILLED,
                        center.timestamp, volumeImbalance, priceImbalance, 0.0, reasoning
                    );
                }
            }
            
            // Check for Bearish FVG: gap between previous low and next high
            if (nextHigh < prevLow && center.price < previous.price && next.price < center.price) {
                // Strong downward move created imbalance
                double volumeImbalance = (center.volume - previous.volume) / (double) previous.volume;
                double priceImbalance = (previous.price - next.price) / previous.price;
                
                if (priceImbalance > 0.008 && volumeImbalance > 0.2) {
                    String reasoning = String.format("Bearish imbalance: %.1f%% price gap, %.1f%% volume spike",
                        priceImbalance * 100, volumeImbalance * 100);
                    
                    return new FairValueGap(
                        prevLow, nextHigh, FVGType.BEARISH_FVG, FVGStatus.UNFILLED,
                        center.timestamp, volumeImbalance, priceImbalance, 0.0, reasoning
                    );
                }
            }
            
        } catch (Exception e) {
            logger.debug("Error identifying FVG pattern at index {}: {}", centerIndex, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Update gap statuses based on subsequent price action
     */
    private List<FairValueGap> updateGapStatuses(List<FairValueGap> gaps, List<SimpleMarketData> priceHistory) {
        List<FairValueGap> updatedGaps = new ArrayList<>();
        
        for (FairValueGap gap : gaps) {
            FairValueGap updatedGap = updateGapStatus(gap, priceHistory);
            updatedGaps.add(updatedGap);
        }
        
        return updatedGaps;
    }
    
    /**
     * Update individual gap status
     */
    private FairValueGap updateGapStatus(FairValueGap gap, List<SimpleMarketData> priceHistory) {
        try {
            double maxFill = 0.0;
            FVGStatus newStatus = gap.status;
            
            // Check how much of the gap has been filled
            for (SimpleMarketData data : priceHistory) {
                if (data.timestamp.isAfter(gap.formationTime)) {
                    double fillPercentage = calculateFillPercentage(gap, data.price);
                    maxFill = Math.max(maxFill, fillPercentage);
                }
            }
            
            // Determine new status
            if (maxFill >= 100.0) {
                newStatus = FVGStatus.FILLED;
            } else if (maxFill >= 50.0) {
                newStatus = FVGStatus.PARTIALLY_FILLED;
            } else if (maxFill > 0.0) {
                newStatus = FVGStatus.UNFILLED; // Still unfilled but touched
            }
            
            // Check for invalidation (significant move beyond the gap)
            double currentPrice = priceHistory.get(priceHistory.size() - 1).price;
            if (gap.type == FVGType.BULLISH_FVG && currentPrice < gap.lowerLevel * 0.98) {
                newStatus = FVGStatus.INVALIDATED;
            } else if (gap.type == FVGType.BEARISH_FVG && currentPrice > gap.upperLevel * 1.02) {
                newStatus = FVGStatus.INVALIDATED;
            }
            
            String updatedReasoning = gap.reasoning + String.format(" | Max fill: %.1f%%", maxFill);
            
            return new FairValueGap(
                gap.upperLevel, gap.lowerLevel, gap.type, newStatus,
                gap.formationTime, gap.volumeImbalance, gap.priceImbalance,
                maxFill, updatedReasoning
            );
            
        } catch (Exception e) {
            logger.error("Error updating gap status: {}", e.getMessage());
            return gap; // Return original if update fails
        }
    }
    
    /**
     * Calculate how much of a gap has been filled
     */
    private double calculateFillPercentage(FairValueGap gap, double price) {
        if (gap.type == FVGType.BULLISH_FVG) {
            if (price <= gap.lowerLevel) {
                return 100.0; // Completely filled
            } else if (price >= gap.upperLevel) {
                return 0.0; // Not filled at all
            } else {
                return ((gap.upperLevel - price) / (gap.upperLevel - gap.lowerLevel)) * 100.0;
            }
        } else if (gap.type == FVGType.BEARISH_FVG) {
            if (price >= gap.upperLevel) {
                return 100.0; // Completely filled
            } else if (price <= gap.lowerLevel) {
                return 0.0; // Not filled at all
            } else {
                return ((price - gap.lowerLevel) / (gap.upperLevel - gap.lowerLevel)) * 100.0;
            }
        }
        return 0.0;
    }
    
    /**
     * Find nearest unfilled gap
     */
    private FairValueGap findNearestUnfilledGap(List<FairValueGap> gaps, double currentPrice) {
        FairValueGap nearest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (FairValueGap gap : gaps) {
            if (gap.status == FVGStatus.UNFILLED || gap.status == FVGStatus.PARTIALLY_FILLED) {
                double distance = Math.abs(gap.midLevel - currentPrice);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = gap;
                }
            }
        }
        
        return nearest;
    }
    
    /**
     * Analyze overall market imbalance
     */
    private String analyzeOverallImbalance(List<FairValueGap> gaps, double currentPrice) {
        long bullishGaps = gaps.stream().filter(g -> 
            g.type == FVGType.BULLISH_FVG && 
            (g.status == FVGStatus.UNFILLED || g.status == FVGStatus.PARTIALLY_FILLED)
        ).count();
        
        long bearishGaps = gaps.stream().filter(g -> 
            g.type == FVGType.BEARISH_FVG && 
            (g.status == FVGStatus.UNFILLED || g.status == FVGStatus.PARTIALLY_FILLED)
        ).count();
        
        if (bullishGaps > bearishGaps * 1.5) {
            return "BULLISH_IMBALANCE";
        } else if (bearishGaps > bullishGaps * 1.5) {
            return "BEARISH_IMBALANCE";
        } else if (bullishGaps + bearishGaps > 0) {
            return "MIXED_IMBALANCE";
        } else {
            return "BALANCED";
        }
    }
    
    /**
     * Calculate imbalance strength
     */
    private double calculateImbalanceStrength(List<FairValueGap> gaps, double currentPrice) {
        if (gaps.isEmpty()) return 0.0;
        
        double bullishStrength = 0.0;
        double bearishStrength = 0.0;
        
        for (FairValueGap gap : gaps) {
            if (gap.status == FVGStatus.UNFILLED || gap.status == FVGStatus.PARTIALLY_FILLED) {
                double distance = Math.abs(gap.midLevel - currentPrice) / currentPrice;
                double proximity = Math.max(0, 1 - distance * 20); // Closer gaps have more influence
                
                double strength = gap.priceImbalance * gap.volumeImbalance * proximity * 100;
                
                if (gap.type == FVGType.BULLISH_FVG) {
                    bullishStrength += strength;
                } else {
                    bearishStrength += strength;
                }
            }
        }
        
        double totalStrength = bullishStrength + bearishStrength;
        return totalStrength == 0 ? 0.0 : Math.min(100.0, totalStrength);
    }
    
    /**
     * Generate FVG trading recommendation
     */
    private String generateFVGRecommendation(FairValueGap nearestGap, String overallImbalance, 
                                           double imbalanceStrength, double currentPrice) {
        StringBuilder recommendation = new StringBuilder();
        
        // Overall imbalance guidance
        switch (overallImbalance) {
            case "BULLISH_IMBALANCE" -> recommendation.append("BULLISH bias - Multiple unfilled bullish FVGs suggest upward pressure. ");
            case "BEARISH_IMBALANCE" -> recommendation.append("BEARISH bias - Multiple unfilled bearish FVGs suggest downward pressure. ");
            case "MIXED_IMBALANCE" -> recommendation.append("NEUTRAL bias - Mixed FVG signals require careful level watching. ");
            default -> recommendation.append("BALANCED - No significant FVG imbalances detected. ");
        }
        
        // Nearest gap guidance
        if (nearestGap != null) {
            double distance = Math.abs(nearestGap.midLevel - currentPrice) / currentPrice * 100;
            
            if (nearestGap.type == FVGType.BULLISH_FVG) {
                recommendation.append(String.format("Watch for support at %.2f-%.2f (%.1f%% away) - bullish FVG likely to provide bounce. ",
                    nearestGap.lowerLevel, nearestGap.upperLevel, distance));
            } else {
                recommendation.append(String.format("Watch for resistance at %.2f-%.2f (%.1f%% away) - bearish FVG likely to provide rejection. ",
                    nearestGap.lowerLevel, nearestGap.upperLevel, distance));
            }
        }
        
        // Strength guidance
        if (imbalanceStrength > 70) {
            recommendation.append("HIGH conviction - Strong imbalance suggests significant price movement likely.");
        } else if (imbalanceStrength > 40) {
            recommendation.append("MODERATE conviction - Reasonable imbalance suggests possible price reaction.");
        } else {
            recommendation.append("LOW conviction - Weak imbalance suggests limited price impact.");
        }
        
        return recommendation.toString();
    }
    
    /**
     * Extract key levels from FVGs
     */
    private List<Double> extractKeyLevels(List<FairValueGap> gaps) {
        List<Double> keyLevels = new ArrayList<>();
        
        for (FairValueGap gap : gaps) {
            if (gap.status == FVGStatus.UNFILLED || gap.status == FVGStatus.PARTIALLY_FILLED) {
                keyLevels.add(gap.upperLevel);
                keyLevels.add(gap.midLevel);
                keyLevels.add(gap.lowerLevel);
            }
        }
        
        return keyLevels.stream().distinct().sorted().collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Create default FVG analysis
     */
    private FVGAnalysis createDefaultFVGAnalysis(List<SimpleMarketData> priceHistory) {
        double currentPrice = priceHistory.isEmpty() ? 0 : priceHistory.get(priceHistory.size() - 1).price;
        
        return new FVGAnalysis(
            new ArrayList<>(), null, currentPrice, "INSUFFICIENT_DATA", 0.0,
            "Insufficient data for Fair Value Gap analysis. Need at least 20 data points.",
            new ArrayList<>()
        );
    }
    
    /**
     * Get confidence boost for FVG analysis
     */
    public double getFVGConfidenceBoost(FVGAnalysis analysis) {
        if (analysis.detectedGaps.isEmpty()) return 0.0;
        
        double boost = 0.0;
        
        // Boost for strong imbalance
        boost += analysis.imbalanceStrength * 0.2; // Up to 20% boost
        
        // Boost for nearby unfilled gaps
        if (analysis.nearestUnfilledGap != null) {
            double distance = Math.abs(analysis.nearestUnfilledGap.midLevel - analysis.currentPrice) / analysis.currentPrice;
            if (distance < 0.02) { // Within 2%
                boost += 15.0;
            } else if (distance < 0.05) { // Within 5%
                boost += 10.0;
            } else if (distance < 0.1) { // Within 10%
                boost += 5.0;
            }
        }
        
        // Boost for clear imbalance direction
        switch (analysis.overallImbalance) {
            case "BULLISH_IMBALANCE", "BEARISH_IMBALANCE" -> boost += 8.0;
            case "MIXED_IMBALANCE" -> boost += 3.0;
        }
        
        return Math.min(25.0, boost); // Cap at 25% boost
    }
}