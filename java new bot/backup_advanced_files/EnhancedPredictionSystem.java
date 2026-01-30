package com.stockbot;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDateTime;

/**
 * Enhanced Prediction System with improved accuracy factors
 * Addresses the key weaknesses identified in bot performance analysis
 */
public class EnhancedPredictionSystem {
    
    // Accuracy improvement factors
    private static final double TRENDING_MARKET_BOOST = 1.15;
    private static final double SIDEWAYS_MARKET_PENALTY = 0.85;
    private static final double HIGH_VOLUME_BOOST = 1.10;
    private static final double LOW_VOLUME_PENALTY = 0.90;
    
    /**
     * Enhanced prediction with multi-factor accuracy improvements
     */
    public static EnhancedPrediction generateEnhancedPrediction(String symbol, StockData currentData, 
                                                               List<StockData> historicalData) {
        
        // Get base prediction from real predictor
        RealStockPredictor.StockPrediction basePrediction = RealStockPredictor.predictStock(
            symbol, currentData, historicalData
        );
        
        // Extract price data for analysis
        List<Double> prices = extractPrices(historicalData);
        List<Long> volumes = extractVolumes(historicalData);
        
        // Analyze market conditions
        MarketCondition marketCondition = analyzeMarketCondition(prices, volumes);
        
        // Enhance prediction based on market conditions
        double enhancedConfidence = enhanceConfidence(basePrediction.confidence, marketCondition);
        
        // Improve entry/exit points
        EntryExitPoints entryExit = calculateOptimalEntryExit(currentData, prices, marketCondition);
        
        // Calculate enhanced targets and stop loss
        TargetLevels targets = calculateEnhancedTargets(currentData.getLastPrice(), 
                                                       basePrediction.direction, 
                                                       marketCondition, entryExit);
        
        // Generate enhanced recommendation
        String enhancedRecommendation = generateEnhancedRecommendation(
            basePrediction.direction, enhancedConfidence, marketCondition, targets
        );
        
        return new EnhancedPrediction(
            symbol, basePrediction.direction, enhancedConfidence, basePrediction.expectedReturn,
            basePrediction.riskLevel, entryExit, targets, enhancedRecommendation, 
            marketCondition, LocalDateTime.now()
        );
    }
    
    /**
     * Analyze market condition to improve accuracy
     */
    private static MarketCondition analyzeMarketCondition(List<Double> prices, List<Long> volumes) {
        if (prices.size() < 20) {
            return new MarketCondition("UNKNOWN", 0.5, 1.0, "Insufficient data");
        }
        
        // Calculate trend strength
        double currentPrice = prices.get(prices.size() - 1);
        double sma20 = RealTechnicalAnalysis.calculateSMA(prices, 20);
        double trendStrength = Math.abs(currentPrice - sma20) / sma20;
        
        // Calculate volatility
        double volatility = RealTechnicalAnalysis.calculateVolatility(prices);
        
        // Calculate volume trend
        double avgVolume = volumes.stream().mapToLong(Long::longValue).average().orElse(1.0);
        double currentVolume = volumes.get(volumes.size() - 1);
        double volumeRatio = currentVolume / avgVolume;
        
        // Determine market condition
        String condition;
        double confidence;
        String description;
        
        if (trendStrength > 0.03 && volatility > 0.25) {
            condition = "TRENDING_VOLATILE";
            confidence = 0.85;
            description = "Strong trend with high volatility";
        } else if (trendStrength > 0.02) {
            condition = "TRENDING";
            confidence = 0.90;
            description = "Clear directional trend";
        } else if (volatility < 0.15) {
            condition = "SIDEWAYS_LOW_VOL";
            confidence = 0.60;
            description = "Range-bound with low volatility";
        } else {
            condition = "SIDEWAYS";
            confidence = 0.65;
            description = "Range-bound market";
        }
        
        return new MarketCondition(condition, confidence, volumeRatio, description);
    }
    
    /**
     * Enhance confidence based on market conditions and historical performance
     */
    private static double enhanceConfidence(double baseConfidence, MarketCondition marketCondition) {
        double enhanced = baseConfidence;
        
        // Adjust based on market condition
        switch (marketCondition.condition) {
            case "TRENDING":
                enhanced *= TRENDING_MARKET_BOOST;
                break;
            case "TRENDING_VOLATILE":
                enhanced *= 1.05; // Slight boost for trending but reduce for volatility
                break;
            case "SIDEWAYS":
            case "SIDEWAYS_LOW_VOL":
                enhanced *= SIDEWAYS_MARKET_PENALTY;
                break;
        }
        
        // Adjust based on volume
        if (marketCondition.volumeRatio > 1.5) {
            enhanced *= HIGH_VOLUME_BOOST;
        } else if (marketCondition.volumeRatio < 0.8) {
            enhanced *= LOW_VOLUME_PENALTY;
        }
        
        // Ensure confidence stays within bounds
        return Math.max(0.1, Math.min(0.95, enhanced));
    }
    
    /**
     * Calculate optimal entry and exit points
     */
    private static EntryExitPoints calculateOptimalEntryExit(StockData currentData, 
                                                           List<Double> prices, 
                                                           MarketCondition marketCondition) {
        
        double currentPrice = currentData.getLastPrice();
        
        // Calculate support and resistance
        RealTechnicalAnalysis.SupportResistance sr = RealTechnicalAnalysis.calculateSupportResistance(
            prices, extractHighs(prices), extractLows(prices)
        );
        
        // Calculate ATR for dynamic levels
        double atr = calculateATR(prices, 14);
        
        // Entry point calculation
        double entryPrice;
        if (marketCondition.condition.contains("TRENDING")) {
            // For trending markets, enter on pullbacks
            entryPrice = currentPrice; // Immediate entry in strong trends
        } else {
            // For sideways markets, wait for breakouts
            entryPrice = currentPrice > sr.resistance * 0.99 ? sr.resistance : sr.support;
        }
        
        // Exit point calculation (initial target)
        double exitPrice;
        if (marketCondition.condition.contains("TRENDING")) {
            exitPrice = currentPrice + (atr * 2.0); // 2 ATR target in trending markets
        } else {
            exitPrice = currentPrice + (atr * 1.5); // 1.5 ATR target in sideways markets
        }
        
        return new EntryExitPoints(entryPrice, exitPrice, sr.support, sr.resistance, atr);
    }
    
    /**
     * Calculate enhanced target levels with improved accuracy
     */
    private static TargetLevels calculateEnhancedTargets(double currentPrice, String direction, 
                                                        MarketCondition marketCondition, 
                                                        EntryExitPoints entryExit) {
        
        double atr = entryExit.atr;
        double multiplier = marketCondition.condition.contains("TRENDING") ? 1.2 : 0.8;
        
        double target1, target2, target3, stopLoss;
        
        if (direction.contains("BUY") || direction.equals("UP")) {
            // BUY targets (improved based on 100% BUY accuracy)
            target1 = currentPrice + (atr * 1.0 * multiplier);
            target2 = currentPrice + (atr * 2.0 * multiplier);
            target3 = currentPrice + (atr * 3.0 * multiplier);
            
            // Dynamic stop loss
            if (marketCondition.condition.contains("TRENDING")) {
                stopLoss = currentPrice - (atr * 1.5); // Wider stop in trending markets
            } else {
                stopLoss = Math.max(currentPrice - (atr * 1.0), entryExit.support * 0.99);
            }
            
        } else {
            // SELL targets (needs improvement - currently 71.4% accuracy)
            target1 = currentPrice - (atr * 1.0 * multiplier);
            target2 = currentPrice - (atr * 2.0 * multiplier);
            target3 = currentPrice - (atr * 3.0 * multiplier);
            
            // Improved stop loss for SELL signals
            if (marketCondition.condition.contains("TRENDING")) {
                stopLoss = currentPrice + (atr * 1.2); // Tighter stop for better SELL accuracy
            } else {
                stopLoss = Math.min(currentPrice + (atr * 0.8), entryExit.resistance * 1.01);
            }
        }
        
        // Risk-reward calculation
        double risk = Math.abs(currentPrice - stopLoss);
        double reward = Math.abs(target2 - currentPrice);
        double riskReward = reward / risk;
        
        return new TargetLevels(target1, target2, target3, stopLoss, riskReward);
    }
    
    /**
     * Generate enhanced recommendation based on improved analysis
     */
    private static String generateEnhancedRecommendation(String direction, double confidence, 
                                                        MarketCondition marketCondition, 
                                                        TargetLevels targets) {
        
        StringBuilder recommendation = new StringBuilder();
        
        // Base recommendation
        if (confidence >= 0.80) {
            recommendation.append("🔥 STRONG ").append(direction);
        } else if (confidence >= 0.70) {
            recommendation.append("✅ ").append(direction);
        } else if (confidence >= 0.60) {
            recommendation.append("👍 CONSIDER ").append(direction);
        } else {
            recommendation.append("⚠️ WEAK ").append(direction);
        }
        
        // Add confidence
        recommendation.append(String.format(" (%.1f%% confidence)", confidence * 100));
        
        // Add market condition context
        recommendation.append(" | ").append(marketCondition.description);
        
        // Add risk-reward info
        recommendation.append(String.format(" | R:R 1:%.1f", targets.riskReward));
        
        // Add specific advice based on market condition
        if (marketCondition.condition.contains("TRENDING")) {
            recommendation.append(" | Trend following strategy");
        } else {
            recommendation.append(" | Range trading strategy");
        }
        
        return recommendation.toString();
    }
    
    // Helper methods
    private static List<Double> extractPrices(List<StockData> data) {
        List<Double> prices = new ArrayList<>();
        for (StockData stock : data) {
            prices.add(stock.getLastPrice());
        }
        return prices;
    }
    
    private static List<Long> extractVolumes(List<StockData> data) {
        List<Long> volumes = new ArrayList<>();
        for (StockData stock : data) {
            volumes.add(stock.getVolume());
        }
        return volumes;
    }
    
    private static List<Double> extractHighs(List<Double> prices) {
        // Simplified - in real implementation, use actual high data
        return prices;
    }
    
    private static List<Double> extractLows(List<Double> prices) {
        // Simplified - in real implementation, use actual low data
        return prices;
    }
    
    private static double calculateATR(List<Double> prices, int period) {
        if (prices.size() < period + 1) return prices.get(prices.size() - 1) * 0.02;
        
        double sum = 0.0;
        for (int i = prices.size() - period; i < prices.size() - 1; i++) {
            double tr = Math.abs(prices.get(i + 1) - prices.get(i));
            sum += tr;
        }
        
        return sum / period;
    }
    
    // Supporting classes
    public static class MarketCondition {
        public final String condition;
        public final double confidence;
        public final double volumeRatio;
        public final String description;
        
        public MarketCondition(String condition, double confidence, double volumeRatio, String description) {
            this.condition = condition;
            this.confidence = confidence;
            this.volumeRatio = volumeRatio;
            this.description = description;
        }
    }
    
    public static class EntryExitPoints {
        public final double entryPrice;
        public final double exitPrice;
        public final double support;
        public final double resistance;
        public final double atr;
        
        public EntryExitPoints(double entryPrice, double exitPrice, double support, double resistance, double atr) {
            this.entryPrice = entryPrice;
            this.exitPrice = exitPrice;
            this.support = support;
            this.resistance = resistance;
            this.atr = atr;
        }
    }
    
    public static class TargetLevels {
        public final double target1;
        public final double target2;
        public final double target3;
        public final double stopLoss;
        public final double riskReward;
        
        public TargetLevels(double target1, double target2, double target3, double stopLoss, double riskReward) {
            this.target1 = target1;
            this.target2 = target2;
            this.target3 = target3;
            this.stopLoss = stopLoss;
            this.riskReward = riskReward;
        }
    }
    
    public static class EnhancedPrediction {
        public final String symbol;
        public final String direction;
        public final double confidence;
        public final double expectedReturn;
        public final String riskLevel;
        public final EntryExitPoints entryExit;
        public final TargetLevels targets;
        public final String recommendation;
        public final MarketCondition marketCondition;
        public final LocalDateTime predictionTime;
        
        public EnhancedPrediction(String symbol, String direction, double confidence, double expectedReturn,
                                String riskLevel, EntryExitPoints entryExit, TargetLevels targets,
                                String recommendation, MarketCondition marketCondition, LocalDateTime predictionTime) {
            this.symbol = symbol;
            this.direction = direction;
            this.confidence = confidence;
            this.expectedReturn = expectedReturn;
            this.riskLevel = riskLevel;
            this.entryExit = entryExit;
            this.targets = targets;
            this.recommendation = recommendation;
            this.marketCondition = marketCondition;
            this.predictionTime = predictionTime;
        }
        
        @Override
        public String toString() {
            return String.format("%s: %s (%.1f%% confidence) | Entry: %.2f | Targets: %.2f/%.2f/%.2f | SL: %.2f", 
                               symbol, direction, confidence * 100, entryExit.entryPrice,
                               targets.target1, targets.target2, targets.target3, targets.stopLoss);
        }
    }
}