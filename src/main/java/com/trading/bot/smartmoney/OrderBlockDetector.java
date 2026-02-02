package com.trading.bot.smartmoney;

import com.trading.bot.market.SimpleMarketData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * PHASE 3 - STEP 3.1: Order Block Detection
 * Identifies institutional order blocks - key support/resistance levels
 * where smart money has significant positions
 */
public class OrderBlockDetector {
    private static final Logger logger = LoggerFactory.getLogger(OrderBlockDetector.class);
    
    public enum OrderBlockType {
        BULLISH_OB,     // Demand zone - institutional buying
        BEARISH_OB,     // Supply zone - institutional selling
        NEUTRAL_OB      // Balanced zone
    }
    
    public enum OrderBlockStrength {
        VERY_STRONG,    // 90%+ probability
        STRONG,         // 75-90% probability
        MODERATE,       // 60-75% probability
        WEAK,           // 45-60% probability
        INVALID         // Below 45%
    }
    
    public static class OrderBlock {
        public final double highPrice;
        public final double lowPrice;
        public final double midPrice;
        public final OrderBlockType type;
        public final OrderBlockStrength strength;
        public final LocalDateTime formationTime;
        public final int volumeConfirmation;
        public final double rejectionCount;
        public final String reasoning;
        
        public OrderBlock(double highPrice, double lowPrice, OrderBlockType type, 
                         OrderBlockStrength strength, LocalDateTime formationTime,
                         int volumeConfirmation, double rejectionCount, String reasoning) {
            this.highPrice = highPrice;
            this.lowPrice = lowPrice;
            this.midPrice = (highPrice + lowPrice) / 2;
            this.type = type;
            this.strength = strength;
            this.formationTime = formationTime;
            this.volumeConfirmation = volumeConfirmation;
            this.rejectionCount = rejectionCount;
            this.reasoning = reasoning;
        }
        
        @Override
        public String toString() {
            return String.format("%s OB: %.2f-%.2f (%s) - %s | Vol: %d, Rejections: %.1f",
                type, lowPrice, highPrice, strength, reasoning, volumeConfirmation, rejectionCount);
        }
    }
    
    public static class OrderBlockAnalysis {
        public final List<OrderBlock> detectedBlocks;
        public final OrderBlock nearestSupport;
        public final OrderBlock nearestResistance;
        public final double currentPrice;
        public final String marketStructure;
        public final double institutionalBias;
        public final String recommendation;
        
        public OrderBlockAnalysis(List<OrderBlock> detectedBlocks, OrderBlock nearestSupport,
                                OrderBlock nearestResistance, double currentPrice,
                                String marketStructure, double institutionalBias, String recommendation) {
            this.detectedBlocks = new ArrayList<>(detectedBlocks);
            this.nearestSupport = nearestSupport;
            this.nearestResistance = nearestResistance;
            this.currentPrice = currentPrice;
            this.marketStructure = marketStructure;
            this.institutionalBias = institutionalBias;
            this.recommendation = recommendation;
        }
    }
    
    /**
     * PHASE 3 - STEP 3.1: Detect institutional order blocks
     */
    public OrderBlockAnalysis detectOrderBlocks(List<SimpleMarketData> priceHistory) {
        logger.info("Detecting order blocks from {} data points", priceHistory.size());
        
        if (priceHistory.size() < 50) {
            return createDefaultAnalysis(priceHistory);
        }
        
        try {
            List<OrderBlock> detectedBlocks = new ArrayList<>();
            
            // Step 1: Identify potential order block zones
            List<OrderBlock> candidateBlocks = identifyOrderBlockCandidates(priceHistory);
            
            // Step 2: Validate blocks with volume and price action
            for (OrderBlock candidate : candidateBlocks) {
                OrderBlock validatedBlock = validateOrderBlock(candidate, priceHistory);
                if (validatedBlock.strength != OrderBlockStrength.INVALID) {
                    detectedBlocks.add(validatedBlock);
                }
            }
            
            // Step 3: Find nearest support and resistance
            double currentPrice = priceHistory.get(priceHistory.size() - 1).price;
            OrderBlock nearestSupport = findNearestSupport(detectedBlocks, currentPrice);
            OrderBlock nearestResistance = findNearestResistance(detectedBlocks, currentPrice);
            
            // Step 4: Determine market structure and institutional bias
            String marketStructure = analyzeMarketStructure(priceHistory, detectedBlocks);
            double institutionalBias = calculateInstitutionalBias(detectedBlocks, currentPrice);
            
            // Step 5: Generate trading recommendation
            String recommendation = generateOrderBlockRecommendation(
                nearestSupport, nearestResistance, currentPrice, marketStructure, institutionalBias);
            
            OrderBlockAnalysis analysis = new OrderBlockAnalysis(
                detectedBlocks, nearestSupport, nearestResistance, currentPrice,
                marketStructure, institutionalBias, recommendation);
            
            logger.info("Order block analysis completed: {} blocks detected, bias: {:.1f}%, structure: {}",
                detectedBlocks.size(), institutionalBias, marketStructure);
            
            return analysis;
            
        } catch (Exception e) {
            logger.error("Error in order block detection: {}", e.getMessage(), e);
            return createDefaultAnalysis(priceHistory);
        }
    }
    
    /**
     * Identify potential order block candidates
     */
    private List<OrderBlock> identifyOrderBlockCandidates(List<SimpleMarketData> priceHistory) {
        List<OrderBlock> candidates = new ArrayList<>();
        
        // Look for significant price moves with volume confirmation
        for (int i = 10; i < priceHistory.size() - 10; i++) {
            SimpleMarketData current = priceHistory.get(i);
            
            // Check for bullish order block formation
            OrderBlock bullishOB = checkBullishOrderBlock(priceHistory, i);
            if (bullishOB != null) {
                candidates.add(bullishOB);
            }
            
            // Check for bearish order block formation
            OrderBlock bearishOB = checkBearishOrderBlock(priceHistory, i);
            if (bearishOB != null) {
                candidates.add(bearishOB);
            }
        }
        
        logger.debug("Identified {} order block candidates", candidates.size());
        return candidates;
    }
    
    /**
     * Check for bullish order block formation
     */
    private OrderBlock checkBullishOrderBlock(List<SimpleMarketData> priceHistory, int index) {
        try {
            SimpleMarketData current = priceHistory.get(index);
            
            // Look for strong upward movement after consolidation
            double priceChange = 0;
            long avgVolume = 0;
            
            // Calculate price movement and volume in next 5 periods
            for (int i = 1; i <= Math.min(5, priceHistory.size() - index - 1); i++) {
                SimpleMarketData future = priceHistory.get(index + i);
                priceChange += (future.price - current.price) / current.price;
                avgVolume += future.volume;
            }
            
            avgVolume /= Math.min(5, priceHistory.size() - index - 1);
            
            // Criteria for bullish order block:
            // 1. Significant upward price movement (>1.5%)
            // 2. High volume confirmation
            // 3. Previous consolidation or downtrend
            
            if (priceChange > 0.015 && avgVolume > current.volume * 1.2) {
                // Find the consolidation zone before the move
                double highPrice = current.price * 1.005; // 0.5% above current
                double lowPrice = current.price * 0.995;  // 0.5% below current
                
                return new OrderBlock(
                    highPrice, lowPrice, OrderBlockType.BULLISH_OB,
                    OrderBlockStrength.MODERATE, current.timestamp,
                    (int)(avgVolume / 1000000), 0.0,
                    String.format("Strong upward move: %.1f%%, Volume: %d", priceChange * 100, avgVolume)
                );
            }
            
        } catch (Exception e) {
            logger.debug("Error checking bullish order block at index {}: {}", index, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Check for bearish order block formation
     */
    private OrderBlock checkBearishOrderBlock(List<SimpleMarketData> priceHistory, int index) {
        try {
            SimpleMarketData current = priceHistory.get(index);
            
            // Look for strong downward movement after consolidation
            double priceChange = 0;
            long avgVolume = 0;
            
            // Calculate price movement and volume in next 5 periods
            for (int i = 1; i <= Math.min(5, priceHistory.size() - index - 1); i++) {
                SimpleMarketData future = priceHistory.get(index + i);
                priceChange += (future.price - current.price) / current.price;
                avgVolume += future.volume;
            }
            
            avgVolume /= Math.min(5, priceHistory.size() - index - 1);
            
            // Criteria for bearish order block:
            // 1. Significant downward price movement (<-1.5%)
            // 2. High volume confirmation
            // 3. Previous consolidation or uptrend
            
            if (priceChange < -0.015 && avgVolume > current.volume * 1.2) {
                // Find the consolidation zone before the move
                double highPrice = current.price * 1.005; // 0.5% above current
                double lowPrice = current.price * 0.995;  // 0.5% below current
                
                return new OrderBlock(
                    highPrice, lowPrice, OrderBlockType.BEARISH_OB,
                    OrderBlockStrength.MODERATE, current.timestamp,
                    (int)(avgVolume / 1000000), 0.0,
                    String.format("Strong downward move: %.1f%%, Volume: %d", Math.abs(priceChange) * 100, avgVolume)
                );
            }
            
        } catch (Exception e) {
            logger.debug("Error checking bearish order block at index {}: {}", index, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Validate order block with additional criteria
     */
    private OrderBlock validateOrderBlock(OrderBlock candidate, List<SimpleMarketData> priceHistory) {
        try {
            // Count how many times price has respected this level
            int respects = 0;
            int violations = 0;
            
            for (SimpleMarketData data : priceHistory) {
                if (data.timestamp.isAfter(candidate.formationTime)) {
                    if (candidate.type == OrderBlockType.BULLISH_OB) {
                        if (data.price >= candidate.lowPrice && data.price <= candidate.highPrice) {
                            respects++; // Price found support in the zone
                        } else if (data.price < candidate.lowPrice * 0.995) {
                            violations++; // Clean break below
                        }
                    } else if (candidate.type == OrderBlockType.BEARISH_OB) {
                        if (data.price >= candidate.lowPrice && data.price <= candidate.highPrice) {
                            respects++; // Price found resistance in the zone
                        } else if (data.price > candidate.highPrice * 1.005) {
                            violations++; // Clean break above
                        }
                    }
                }
            }
            
            // Calculate strength based on respects vs violations
            double respectRatio = violations > 0 ? (double) respects / violations : respects;
            OrderBlockStrength strength = determineBlockStrength(respectRatio, candidate.volumeConfirmation);
            
            String updatedReasoning = candidate.reasoning + 
                String.format(" | Respects: %d, Violations: %d, Ratio: %.1f", respects, violations, respectRatio);
            
            return new OrderBlock(
                candidate.highPrice, candidate.lowPrice, candidate.type,
                strength, candidate.formationTime, candidate.volumeConfirmation,
                respects, updatedReasoning
            );
            
        } catch (Exception e) {
            logger.error("Error validating order block: {}", e.getMessage());
            return candidate; // Return original if validation fails
        }
    }
    
    /**
     * Determine order block strength
     */
    private OrderBlockStrength determineBlockStrength(double respectRatio, int volumeConfirmation) {
        double score = respectRatio * 20 + volumeConfirmation * 5; // Weighted scoring
        
        if (score >= 90) return OrderBlockStrength.VERY_STRONG;
        if (score >= 75) return OrderBlockStrength.STRONG;
        if (score >= 60) return OrderBlockStrength.MODERATE;
        if (score >= 45) return OrderBlockStrength.WEAK;
        return OrderBlockStrength.INVALID;
    }
    
    /**
     * Find nearest support order block
     */
    private OrderBlock findNearestSupport(List<OrderBlock> blocks, double currentPrice) {
        OrderBlock nearest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (OrderBlock block : blocks) {
            if (block.type == OrderBlockType.BULLISH_OB && block.highPrice < currentPrice) {
                double distance = currentPrice - block.highPrice;
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = block;
                }
            }
        }
        
        return nearest;
    }
    
    /**
     * Find nearest resistance order block
     */
    private OrderBlock findNearestResistance(List<OrderBlock> blocks, double currentPrice) {
        OrderBlock nearest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (OrderBlock block : blocks) {
            if (block.type == OrderBlockType.BEARISH_OB && block.lowPrice > currentPrice) {
                double distance = block.lowPrice - currentPrice;
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = block;
                }
            }
        }
        
        return nearest;
    }
    
    /**
     * Analyze overall market structure
     */
    private String analyzeMarketStructure(List<SimpleMarketData> priceHistory, List<OrderBlock> blocks) {
        try {
            // Count bullish vs bearish order blocks
            long bullishBlocks = blocks.stream().filter(b -> b.type == OrderBlockType.BULLISH_OB).count();
            long bearishBlocks = blocks.stream().filter(b -> b.type == OrderBlockType.BEARISH_OB).count();
            
            // Analyze recent price action
            double recentChange = 0;
            if (priceHistory.size() >= 10) {
                double oldPrice = priceHistory.get(priceHistory.size() - 10).price;
                double newPrice = priceHistory.get(priceHistory.size() - 1).price;
                recentChange = (newPrice - oldPrice) / oldPrice;
            }
            
            // Determine structure
            if (bullishBlocks > bearishBlocks && recentChange > 0.01) {
                return "BULLISH_STRUCTURE";
            } else if (bearishBlocks > bullishBlocks && recentChange < -0.01) {
                return "BEARISH_STRUCTURE";
            } else if (Math.abs(recentChange) < 0.005) {
                return "RANGING_STRUCTURE";
            } else {
                return "TRANSITIONAL_STRUCTURE";
            }
            
        } catch (Exception e) {
            logger.error("Error analyzing market structure: {}", e.getMessage());
            return "UNKNOWN_STRUCTURE";
        }
    }
    
    /**
     * Calculate institutional bias percentage
     */
    private double calculateInstitutionalBias(List<OrderBlock> blocks, double currentPrice) {
        if (blocks.isEmpty()) return 50.0; // Neutral
        
        double bullishWeight = 0;
        double bearishWeight = 0;
        
        for (OrderBlock block : blocks) {
            double weight = getBlockWeight(block);
            
            if (block.type == OrderBlockType.BULLISH_OB) {
                // Stronger weight if price is near the block
                double distance = Math.abs(currentPrice - block.midPrice) / currentPrice;
                bullishWeight += weight * (1 - Math.min(distance * 10, 0.8)); // Closer = stronger influence
            } else if (block.type == OrderBlockType.BEARISH_OB) {
                double distance = Math.abs(currentPrice - block.midPrice) / currentPrice;
                bearishWeight += weight * (1 - Math.min(distance * 10, 0.8));
            }
        }
        
        double totalWeight = bullishWeight + bearishWeight;
        if (totalWeight == 0) return 50.0;
        
        return (bullishWeight / totalWeight) * 100; // 0-100% (0=bearish, 100=bullish)
    }
    
    /**
     * Get weight for order block based on strength
     */
    private double getBlockWeight(OrderBlock block) {
        return switch (block.strength) {
            case VERY_STRONG -> 4.0;
            case STRONG -> 3.0;
            case MODERATE -> 2.0;
            case WEAK -> 1.0;
            case INVALID -> 0.0;
        };
    }
    
    /**
     * Generate trading recommendation based on order blocks
     */
    private String generateOrderBlockRecommendation(OrderBlock nearestSupport, OrderBlock nearestResistance,
                                                   double currentPrice, String marketStructure, double institutionalBias) {
        
        StringBuilder recommendation = new StringBuilder();
        
        // Primary recommendation based on institutional bias
        if (institutionalBias > 70) {
            recommendation.append("BULLISH - Strong institutional buying interest. ");
        } else if (institutionalBias < 30) {
            recommendation.append("BEARISH - Strong institutional selling pressure. ");
        } else {
            recommendation.append("NEUTRAL - Balanced institutional sentiment. ");
        }
        
        // Add support/resistance guidance
        if (nearestSupport != null) {
            double supportDistance = ((currentPrice - nearestSupport.highPrice) / currentPrice) * 100;
            recommendation.append(String.format("Support at %.2f (%.1f%% below). ", 
                nearestSupport.highPrice, supportDistance));
        }
        
        if (nearestResistance != null) {
            double resistanceDistance = ((nearestResistance.lowPrice - currentPrice) / currentPrice) * 100;
            recommendation.append(String.format("Resistance at %.2f (%.1f%% above). ", 
                nearestResistance.lowPrice, resistanceDistance));
        }
        
        // Add structure guidance
        recommendation.append("Market Structure: ").append(marketStructure.replace("_", " "));
        
        return recommendation.toString();
    }
    
    /**
     * Create default analysis for insufficient data
     */
    private OrderBlockAnalysis createDefaultAnalysis(List<SimpleMarketData> priceHistory) {
        double currentPrice = priceHistory.isEmpty() ? 0 : priceHistory.get(priceHistory.size() - 1).price;
        
        return new OrderBlockAnalysis(
            new ArrayList<>(), null, null, currentPrice,
            "INSUFFICIENT_DATA", 50.0,
            "Insufficient data for order block analysis. Need at least 50 data points."
        );
    }
    
    /**
     * Get confidence boost for order block analysis
     */
    public double getOrderBlockConfidenceBoost(OrderBlockAnalysis analysis) {
        if (analysis.detectedBlocks.isEmpty()) return 0.0;
        
        double boost = 0.0;
        
        // Boost for strong institutional bias
        double biasStrength = Math.abs(analysis.institutionalBias - 50); // Distance from neutral
        boost += biasStrength * 0.3; // Up to 15% boost
        
        // Boost for clear market structure
        switch (analysis.marketStructure) {
            case "BULLISH_STRUCTURE", "BEARISH_STRUCTURE" -> boost += 10.0;
            case "RANGING_STRUCTURE" -> boost += 5.0;
            case "TRANSITIONAL_STRUCTURE" -> boost += 2.0;
        }
        
        // Boost for nearby strong order blocks
        if (analysis.nearestSupport != null && 
            analysis.nearestSupport.strength == OrderBlockStrength.VERY_STRONG) {
            boost += 8.0;
        }
        
        if (analysis.nearestResistance != null && 
            analysis.nearestResistance.strength == OrderBlockStrength.VERY_STRONG) {
            boost += 8.0;
        }
        
        return Math.min(25.0, boost); // Cap at 25% boost
    }
}