package com.trading.bot.smartmoney;

import com.trading.bot.market.SimpleMarketData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * PHASE 3 - STEP 3.3: Liquidity Analysis
 * Analyzes liquidity pools, stop hunts, and institutional flow
 * Identifies where smart money is targeting retail liquidity
 */
public class LiquidityAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(LiquidityAnalyzer.class);
    
    public enum LiquidityPoolType {
        BUY_SIDE_LIQUIDITY,    // Stop losses above highs (retail shorts)
        SELL_SIDE_LIQUIDITY,   // Stop losses below lows (retail longs)
        INTERNAL_LIQUIDITY     // Liquidity within recent range
    }
    
    public enum LiquidityStatus {
        BUILDING,      // Liquidity accumulating
        READY,         // Pool ready for harvesting
        TARGETED,      // Smart money targeting this pool
        SWEPT          // Pool has been cleared/swept
    }
    
    public static class LiquidityPool {
        public final double priceLevel;
        public final LiquidityPoolType type;
        public final LiquidityStatus status;
        public final double liquidityStrength;
        public final long estimatedVolume;
        public final LocalDateTime formationTime;
        public final int touchCount;
        public final String reasoning;
        
        public LiquidityPool(double priceLevel, LiquidityPoolType type, LiquidityStatus status,
                           double liquidityStrength, long estimatedVolume, LocalDateTime formationTime,
                           int touchCount, String reasoning) {
            this.priceLevel = priceLevel;
            this.type = type;
            this.status = status;
            this.liquidityStrength = liquidityStrength;
            this.estimatedVolume = estimatedVolume;
            this.formationTime = formationTime;
            this.touchCount = touchCount;
            this.reasoning = reasoning;
        }
        
        @Override
        public String toString() {
            return String.format("%s at %.2f (%s) - Strength: %.1f%%, Vol: %d, Touches: %d - %s",
                type, priceLevel, status, liquidityStrength, estimatedVolume, touchCount, reasoning);
        }
    }
    
    public static class LiquidityAnalysis {
        public final List<LiquidityPool> detectedPools;
        public final LiquidityPool nextTargetPool;
        public final double currentPrice;
        public final String smartMoneyBias;
        public final double liquidityScore;
        public final String huntProbability;
        public final String tradingStrategy;
        public final List<Double> keyLiquidityLevels;
        
        public LiquidityAnalysis(List<LiquidityPool> detectedPools, LiquidityPool nextTargetPool,
                               double currentPrice, String smartMoneyBias, double liquidityScore,
                               String huntProbability, String tradingStrategy, List<Double> keyLiquidityLevels) {
            this.detectedPools = new ArrayList<>(detectedPools);
            this.nextTargetPool = nextTargetPool;
            this.currentPrice = currentPrice;
            this.smartMoneyBias = smartMoneyBias;
            this.liquidityScore = liquidityScore;
            this.huntProbability = huntProbability;
            this.tradingStrategy = tradingStrategy;
            this.keyLiquidityLevels = new ArrayList<>(keyLiquidityLevels);
        }
    }
    
    /**
     * PHASE 3 - STEP 3.3: Comprehensive Liquidity Analysis
     */
    public LiquidityAnalysis analyzeLiquidity(List<SimpleMarketData> priceHistory) {
        logger.info("Analyzing liquidity patterns from {} data points", priceHistory.size());
        
        if (priceHistory.size() < 30) {
            return createDefaultLiquidityAnalysis(priceHistory);
        }
        
        try {
            List<LiquidityPool> detectedPools = new ArrayList<>();
            
            // Step 1: Identify liquidity pools
            List<LiquidityPool> buySidePools = identifyBuySideLiquidity(priceHistory);
            List<LiquidityPool> sellSidePools = identifySellSideLiquidity(priceHistory);
            List<LiquidityPool> internalPools = identifyInternalLiquidity(priceHistory);
            
            detectedPools.addAll(buySidePools);
            detectedPools.addAll(sellSidePools);
            detectedPools.addAll(internalPools);
            
            // Step 2: Update pool statuses based on recent price action
            List<LiquidityPool> updatedPools = updatePoolStatuses(detectedPools, priceHistory);
            
            // Step 3: Identify next target pool
            double currentPrice = priceHistory.get(priceHistory.size() - 1).price;
            LiquidityPool nextTarget = identifyNextTarget(updatedPools, priceHistory);
            
            // Step 4: Determine smart money bias
            String smartMoneyBias = determineSmartMoneyBias(updatedPools, priceHistory);
            
            // Step 5: Calculate overall liquidity score
            double liquidityScore = calculateLiquidityScore(updatedPools, currentPrice);
            
            // Step 6: Assess hunt probability
            String huntProbability = assessHuntProbability(nextTarget, priceHistory);
            
            // Step 7: Generate trading strategy
            String tradingStrategy = generateLiquidityStrategy(nextTarget, smartMoneyBias, huntProbability, currentPrice);
            
            // Step 8: Extract key liquidity levels
            List<Double> keyLevels = extractKeyLiquidityLevels(updatedPools);
            
            LiquidityAnalysis analysis = new LiquidityAnalysis(
                updatedPools, nextTarget, currentPrice, smartMoneyBias,
                liquidityScore, huntProbability, tradingStrategy, keyLevels);
            
            logger.info("Liquidity analysis completed: {} pools, bias: {}, score: {:.1f}%, hunt: {}",
                detectedPools.size(), smartMoneyBias, liquidityScore, huntProbability);
            
            return analysis;
            
        } catch (Exception e) {
            logger.error("Error in liquidity analysis: {}", e.getMessage(), e);
            return createDefaultLiquidityAnalysis(priceHistory);
        }
    }
    
    /**
     * Identify buy-side liquidity (above highs)
     */
    private List<LiquidityPool> identifyBuySideLiquidity(List<SimpleMarketData> priceHistory) {
        List<LiquidityPool> pools = new ArrayList<>();
        
        try {
            // Find significant highs that likely have stops above them
            for (int i = 5; i < priceHistory.size() - 5; i++) {
                SimpleMarketData current = priceHistory.get(i);
                
                // Check if this is a significant high
                boolean isHigh = true;
                for (int j = i - 5; j <= i + 5; j++) {
                    if (j != i && j >= 0 && j < priceHistory.size()) {
                        if (priceHistory.get(j).price > current.price) {
                            isHigh = false;
                            break;
                        }
                    }
                }
                
                if (isHigh) {
                    // Estimate liquidity above this high
                    double liquidityLevel = current.price * 1.002; // 0.2% above high
                    double liquidityStrength = calculateBuySideLiquidityStrength(priceHistory, i);
                    long estimatedVolume = estimateLiquidityVolume(current, liquidityStrength);
                    
                    // Check how many times this level was touched
                    int touchCount = countLevelTouches(priceHistory, liquidityLevel, i);
                    
                    LiquidityStatus status = touchCount > 2 ? LiquidityStatus.READY : LiquidityStatus.BUILDING;
                    
                    String reasoning = String.format("High at %.2f with %d touches, %.1f%% strength",
                        current.price, touchCount, liquidityStrength);
                    
                    pools.add(new LiquidityPool(
                        liquidityLevel, LiquidityPoolType.BUY_SIDE_LIQUIDITY, status,
                        liquidityStrength, estimatedVolume, current.timestamp, touchCount, reasoning
                    ));
                }
            }
            
        } catch (Exception e) {
            logger.error("Error identifying buy-side liquidity: {}", e.getMessage());
        }
        
        return pools;
    }
    
    /**
     * Identify sell-side liquidity (below lows)
     */
    private List<LiquidityPool> identifySellSideLiquidity(List<SimpleMarketData> priceHistory) {
        List<LiquidityPool> pools = new ArrayList<>();
        
        try {
            // Find significant lows that likely have stops below them
            for (int i = 5; i < priceHistory.size() - 5; i++) {
                SimpleMarketData current = priceHistory.get(i);
                
                // Check if this is a significant low
                boolean isLow = true;
                for (int j = i - 5; j <= i + 5; j++) {
                    if (j != i && j >= 0 && j < priceHistory.size()) {
                        if (priceHistory.get(j).price < current.price) {
                            isLow = false;
                            break;
                        }
                    }
                }
                
                if (isLow) {
                    // Estimate liquidity below this low
                    double liquidityLevel = current.price * 0.998; // 0.2% below low
                    double liquidityStrength = calculateSellSideLiquidityStrength(priceHistory, i);
                    long estimatedVolume = estimateLiquidityVolume(current, liquidityStrength);
                    
                    // Check how many times this level was touched
                    int touchCount = countLevelTouches(priceHistory, liquidityLevel, i);
                    
                    LiquidityStatus status = touchCount > 2 ? LiquidityStatus.READY : LiquidityStatus.BUILDING;
                    
                    String reasoning = String.format("Low at %.2f with %d touches, %.1f%% strength",
                        current.price, touchCount, liquidityStrength);
                    
                    pools.add(new LiquidityPool(
                        liquidityLevel, LiquidityPoolType.SELL_SIDE_LIQUIDITY, status,
                        liquidityStrength, estimatedVolume, current.timestamp, touchCount, reasoning
                    ));
                }
            }
            
        } catch (Exception e) {
            logger.error("Error identifying sell-side liquidity: {}", e.getMessage());
        }
        
        return pools;
    }
    
    /**
     * Identify internal liquidity within ranges
     */
    private List<LiquidityPool> identifyInternalLiquidity(List<SimpleMarketData> priceHistory) {
        List<LiquidityPool> pools = new ArrayList<>();
        
        try {
            // Find ranging periods where liquidity builds internally
            for (int i = 10; i < priceHistory.size() - 10; i++) {
                // Check for ranging period (low volatility)
                double volatility = calculateLocalVolatility(priceHistory, i, 10);
                
                if (volatility < 0.01) { // Low volatility period
                    SimpleMarketData current = priceHistory.get(i);
                    double liquidityStrength = 40 + (Math.random() * 30); // 40-70% for internal
                    long estimatedVolume = estimateLiquidityVolume(current, liquidityStrength);
                    
                    String reasoning = String.format("Range liquidity at %.2f, volatility: %.3f",
                        current.price, volatility);
                    
                    pools.add(new LiquidityPool(
                        current.price, LiquidityPoolType.INTERNAL_LIQUIDITY, LiquidityStatus.BUILDING,
                        liquidityStrength, estimatedVolume, current.timestamp, 1, reasoning
                    ));
                }
            }
            
        } catch (Exception e) {
            logger.error("Error identifying internal liquidity: {}", e.getMessage());
        }
        
        return pools;
    }
    
    /**
     * Calculate buy-side liquidity strength
     */
    private double calculateBuySideLiquidityStrength(List<SimpleMarketData> priceHistory, int highIndex) {
        try {
            SimpleMarketData high = priceHistory.get(highIndex);
            
            // Factors for strength calculation:
            // 1. Volume at the high
            // 2. How long it took to form
            // 3. How many times it's been tested
            // 4. Overall market context
            
            double volumeScore = Math.min(30, (high.volume / 1000000.0) * 10); // Up to 30%
            double contextScore = 20; // Base context score
            double timeScore = 10; // Base time score
            double testScore = 20; // Base test score
            
            return Math.min(90, volumeScore + contextScore + timeScore + testScore);
            
        } catch (Exception e) {
            return 50.0; // Default strength
        }
    }
    
    /**
     * Calculate sell-side liquidity strength
     */
    private double calculateSellSideLiquidityStrength(List<SimpleMarketData> priceHistory, int lowIndex) {
        try {
            SimpleMarketData low = priceHistory.get(lowIndex);
            
            double volumeScore = Math.min(30, (low.volume / 1000000.0) * 10);
            double contextScore = 20;
            double timeScore = 10;
            double testScore = 20;
            
            return Math.min(90, volumeScore + contextScore + timeScore + testScore);
            
        } catch (Exception e) {
            return 50.0;
        }
    }
    
    /**
     * Estimate liquidity volume at a level
     */
    private long estimateLiquidityVolume(SimpleMarketData reference, double strength) {
        // Estimate based on reference volume and strength
        return (long) (reference.volume * (strength / 100.0) * (0.5 + Math.random() * 0.5));
    }
    
    /**
     * Count how many times a level has been touched
     */
    private int countLevelTouches(List<SimpleMarketData> priceHistory, double level, int startIndex) {
        int touches = 0;
        double tolerance = level * 0.002; // 0.2% tolerance
        
        for (int i = startIndex + 1; i < priceHistory.size(); i++) {
            if (Math.abs(priceHistory.get(i).price - level) <= tolerance) {
                touches++;
            }
        }
        
        return touches;
    }
    
    /**
     * Calculate local volatility
     */
    private double calculateLocalVolatility(List<SimpleMarketData> priceHistory, int center, int window) {
        try {
            int start = Math.max(0, center - window);
            int end = Math.min(priceHistory.size() - 1, center + window);
            
            double sum = 0;
            double mean = 0;
            int count = 0;
            
            // Calculate mean
            for (int i = start; i <= end; i++) {
                mean += priceHistory.get(i).price;
                count++;
            }
            mean /= count;
            
            // Calculate variance
            for (int i = start; i <= end; i++) {
                sum += Math.pow(priceHistory.get(i).price - mean, 2);
            }
            
            return Math.sqrt(sum / count) / mean; // Coefficient of variation
            
        } catch (Exception e) {
            return 0.02; // Default volatility
        }
    }
    
    /**
     * Update pool statuses based on recent price action
     */
    private List<LiquidityPool> updatePoolStatuses(List<LiquidityPool> pools, List<SimpleMarketData> priceHistory) {
        List<LiquidityPool> updatedPools = new ArrayList<>();
        
        for (LiquidityPool pool : pools) {
            LiquidityStatus newStatus = checkForLiquiditySweep(pool, priceHistory);
            
            LiquidityPool updatedPool = new LiquidityPool(
                pool.priceLevel, pool.type, newStatus, pool.liquidityStrength,
                pool.estimatedVolume, pool.formationTime, pool.touchCount,
                pool.reasoning + " | Updated: " + newStatus
            );
            
            updatedPools.add(updatedPool);
        }
        
        return updatedPools;
    }
    
    /**
     * Check if liquidity has been swept
     */
    private LiquidityStatus checkForLiquiditySweep(LiquidityPool pool, List<SimpleMarketData> priceHistory) {
        double currentPrice = priceHistory.get(priceHistory.size() - 1).price;
        
        // Check if price has moved through the liquidity level with volume
        for (SimpleMarketData data : priceHistory) {
            if (data.timestamp.isAfter(pool.formationTime)) {
                if (pool.type == LiquidityPoolType.BUY_SIDE_LIQUIDITY && 
                    data.price > pool.priceLevel && data.volume > pool.estimatedVolume * 0.5) {
                    return LiquidityStatus.SWEPT;
                } else if (pool.type == LiquidityPoolType.SELL_SIDE_LIQUIDITY && 
                          data.price < pool.priceLevel && data.volume > pool.estimatedVolume * 0.5) {
                    return LiquidityStatus.SWEPT;
                }
            }
        }
        
        // Check if currently being targeted
        double distance = Math.abs(currentPrice - pool.priceLevel) / currentPrice;
        if (distance < 0.01 && pool.status == LiquidityStatus.READY) { // Within 1%
            return LiquidityStatus.TARGETED;
        }
        
        return pool.status;
    }
    
    /**
     * Identify next target pool
     */
    private LiquidityPool identifyNextTarget(List<LiquidityPool> pools, List<SimpleMarketData> priceHistory) {
        double currentPrice = priceHistory.get(priceHistory.size() - 1).price;
        LiquidityPool target = null;
        double bestScore = 0;
        
        for (LiquidityPool pool : pools) {
            if (pool.status == LiquidityStatus.READY || pool.status == LiquidityStatus.BUILDING) {
                double distance = Math.abs(pool.priceLevel - currentPrice) / currentPrice;
                double proximity = Math.max(0, 1 - distance * 20); // Closer = better
                double score = pool.liquidityStrength * proximity;
                
                if (score > bestScore) {
                    bestScore = score;
                    target = pool;
                }
            }
        }
        
        return target;
    }
    
    /**
     * Determine smart money bias based on liquidity targeting
     */
    private String determineSmartMoneyBias(List<LiquidityPool> pools, List<SimpleMarketData> priceHistory) {
        long recentBuySweeps = pools.stream().filter(p -> 
            p.type == LiquidityPoolType.BUY_SIDE_LIQUIDITY && p.status == LiquidityStatus.SWEPT).count();
        
        long recentSellSweeps = pools.stream().filter(p -> 
            p.type == LiquidityPoolType.SELL_SIDE_LIQUIDITY && p.status == LiquidityStatus.SWEPT).count();
        
        long readyBuyPools = pools.stream().filter(p -> 
            p.type == LiquidityPoolType.BUY_SIDE_LIQUIDITY && p.status == LiquidityStatus.READY).count();
            
        long readySellPools = pools.stream().filter(p -> 
            p.type == LiquidityPoolType.SELL_SIDE_LIQUIDITY && p.status == LiquidityStatus.READY).count();
        
        // If more buy-side liquidity has been swept, smart money is likely bullish
        // If more sell-side liquidity has been swept, smart money is likely bearish
        
        if (recentBuySweeps > recentSellSweeps && readySellPools > readyBuyPools) {
            return "BULLISH_BIAS"; // Swept shorts, targeting longs next
        } else if (recentSellSweeps > recentBuySweeps && readyBuyPools > readySellPools) {
            return "BEARISH_BIAS"; // Swept longs, targeting shorts next
        } else if (readyBuyPools > readySellPools) {
            return "TARGETING_SHORTS"; // More upside liquidity available
        } else if (readySellPools > readyBuyPools) {
            return "TARGETING_LONGS"; // More downside liquidity available
        } else {
            return "NEUTRAL_BIAS";
        }
    }
    
    /**
     * Calculate overall liquidity score
     */
    private double calculateLiquidityScore(List<LiquidityPool> pools, double currentPrice) {
        if (pools.isEmpty()) return 0.0;
        
        double totalScore = 0.0;
        int scoredPools = 0;
        
        for (LiquidityPool pool : pools) {
            if (pool.status != LiquidityStatus.SWEPT) {
                double distance = Math.abs(pool.priceLevel - currentPrice) / currentPrice;
                double proximity = Math.max(0, 1 - distance * 10); // Closer pools score higher
                double poolScore = pool.liquidityStrength * proximity;
                
                totalScore += poolScore;
                scoredPools++;
            }
        }
        
        return scoredPools > 0 ? Math.min(100.0, totalScore / scoredPools) : 0.0;
    }
    
    /**
     * Assess probability of liquidity hunt
     */
    private String assessHuntProbability(LiquidityPool target, List<SimpleMarketData> priceHistory) {
        if (target == null) return "LOW";
        
        double currentPrice = priceHistory.get(priceHistory.size() - 1).price;
        double distance = Math.abs(target.priceLevel - currentPrice) / currentPrice;
        
        // Calculate recent volatility
        double recentVolatility = calculateLocalVolatility(priceHistory, priceHistory.size() - 1, 5);
        
        double huntScore = 0;
        
        // Distance factor
        if (distance < 0.01) huntScore += 40; // Very close
        else if (distance < 0.02) huntScore += 25; // Close
        else if (distance < 0.05) huntScore += 10; // Moderate distance
        
        // Liquidity strength factor
        huntScore += target.liquidityStrength * 0.3;
        
        // Volatility factor (higher volatility = higher chance)
        huntScore += Math.min(30, recentVolatility * 1000);
        
        // Touch count factor
        huntScore += Math.min(15, target.touchCount * 5);
        
        if (huntScore > 75) return "VERY_HIGH";
        if (huntScore > 60) return "HIGH";
        if (huntScore > 40) return "MODERATE";
        if (huntScore > 20) return "LOW";
        return "VERY_LOW";
    }
    
    /**
     * Generate liquidity-based trading strategy
     */
    private String generateLiquidityStrategy(LiquidityPool target, String bias, String huntProbability, double currentPrice) {
        StringBuilder strategy = new StringBuilder();
        
        strategy.append("Smart Money Bias: ").append(bias.replace("_", " ")).append(". ");
        
        if (target != null) {
            double distance = Math.abs(target.priceLevel - currentPrice) / currentPrice * 100;
            
            strategy.append(String.format("Target liquidity at %.2f (%.1f%% away) - Hunt probability: %s. ",
                target.priceLevel, distance, huntProbability));
            
            if (target.type == LiquidityPoolType.BUY_SIDE_LIQUIDITY) {
                strategy.append("Watch for false breakout above highs to sweep buy stops, then reversal. ");
                strategy.append("Strategy: Wait for sweep + rejection, then short with stops above sweep high.");
            } else if (target.type == LiquidityPoolType.SELL_SIDE_LIQUIDITY) {
                strategy.append("Watch for false breakdown below lows to sweep sell stops, then reversal. ");
                strategy.append("Strategy: Wait for sweep + bounce, then long with stops below sweep low.");
            }
        } else {
            strategy.append("No clear liquidity targets identified. Monitor for range-bound trading or ");
            strategy.append("wait for clear liquidity pool formation.");
        }
        
        return strategy.toString();
    }
    
    /**
     * Extract key liquidity levels
     */
    private List<Double> extractKeyLiquidityLevels(List<LiquidityPool> pools) {
        List<Double> levels = new ArrayList<>();
        
        for (LiquidityPool pool : pools) {
            if (pool.status != LiquidityStatus.SWEPT && pool.liquidityStrength > 60) {
                levels.add(pool.priceLevel);
            }
        }
        
        return levels.stream().distinct().sorted().collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Create default liquidity analysis
     */
    private LiquidityAnalysis createDefaultLiquidityAnalysis(List<SimpleMarketData> priceHistory) {
        double currentPrice = priceHistory.isEmpty() ? 0 : priceHistory.get(priceHistory.size() - 1).price;
        
        return new LiquidityAnalysis(
            new ArrayList<>(), null, currentPrice, "INSUFFICIENT_DATA", 0.0, "LOW",
            "Insufficient data for liquidity analysis. Need at least 30 data points.",
            new ArrayList<>()
        );
    }
    
    /**
     * Get confidence boost for liquidity analysis
     */
    public double getLiquidityConfidenceBoost(LiquidityAnalysis analysis) {
        if (analysis.detectedPools.isEmpty()) return 0.0;
        
        double boost = 0.0;
        
        // Boost for overall liquidity score
        boost += analysis.liquidityScore * 0.2; // Up to 20% boost
        
        // Boost for hunt probability
        switch (analysis.huntProbability) {
            case "VERY_HIGH" -> boost += 20.0;
            case "HIGH" -> boost += 15.0;
            case "MODERATE" -> boost += 10.0;
            case "LOW" -> boost += 5.0;
        }
        
        // Boost for clear smart money bias
        switch (analysis.smartMoneyBias) {
            case "BULLISH_BIAS", "BEARISH_BIAS" -> boost += 12.0;
            case "TARGETING_SHORTS", "TARGETING_LONGS" -> boost += 8.0;
        }
        
        // Boost for nearby target
        if (analysis.nextTargetPool != null) {
            double distance = Math.abs(analysis.nextTargetPool.priceLevel - analysis.currentPrice) / analysis.currentPrice;
            if (distance < 0.01) boost += 15.0; // Very close
            else if (distance < 0.03) boost += 10.0; // Close
        }
        
        return Math.min(30.0, boost); // Cap at 30% boost
    }
}