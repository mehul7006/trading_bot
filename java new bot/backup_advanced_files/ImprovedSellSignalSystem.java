package com.stockbot;

import java.util.List;
import java.util.ArrayList;

/**
 * Improved SELL Signal System
 * Addresses the 71.4% vs 100% BUY signal accuracy gap
 * Implements advanced exit strategies for better SELL performance
 */
public class ImprovedSellSignalSystem {
    
    /**
     * Generate improved SELL signals with enhanced accuracy
     * Target: Improve from 71.4% to 85%+ accuracy
     */
    public static SellSignalResult generateImprovedSellSignal(String symbol, StockData currentData, 
                                                             List<StockData> historicalData,
                                                             EnhancedPredictionSystem.MarketCondition marketCondition) {
        
        List<Double> prices = extractPrices(historicalData);
        List<Long> volumes = extractVolumes(historicalData);
        
        // Multi-factor SELL signal analysis
        SellFactorAnalysis analysis = analyzeSellFactors(currentData, prices, volumes, marketCondition);
        
        // Calculate enhanced SELL confidence
        double sellConfidence = calculateEnhancedSellConfidence(analysis, marketCondition);
        
        // Determine optimal SELL strategy
        SellStrategy strategy = determineSellStrategy(analysis, marketCondition);
        
        // Calculate precise exit levels
        ExitLevels exitLevels = calculatePreciseExitLevels(currentData, analysis, strategy);
        
        // Generate SELL recommendation
        String recommendation = generateSellRecommendation(sellConfidence, strategy, exitLevels);
        
        return new SellSignalResult(symbol, sellConfidence, strategy, exitLevels, 
                                   recommendation, analysis, java.time.LocalDateTime.now());
    }
    
    /**
     * Analyze multiple factors affecting SELL signal accuracy
     */
    private static SellFactorAnalysis analyzeSellFactors(StockData currentData, List<Double> prices, 
                                                        List<Long> volumes, 
                                                        EnhancedPredictionSystem.MarketCondition marketCondition) {
        
        double currentPrice = currentData.getLastPrice();
        
        // 1. Technical Reversal Signals
        double rsi = RealTechnicalAnalysis.calculateRSI(prices, 14);
        RealTechnicalAnalysis.MACDResult macd = RealTechnicalAnalysis.calculateMACD(prices);
        RealTechnicalAnalysis.BollingerBands bb = RealTechnicalAnalysis.calculateBollingerBands(prices, 20, 2.0);
        
        // 2. Volume Analysis for Distribution
        double avgVolume = volumes.stream().mapToLong(Long::longValue).average().orElse(1.0);
        double volumeRatio = currentData.getVolume() / avgVolume;
        boolean distributionVolume = volumeRatio > 1.3 && currentData.getNetChange() < 0;
        
        // 3. Price Action Analysis
        double dayRange = currentData.getDayHigh() - currentData.getDayLow();
        double pricePosition = (currentPrice - currentData.getDayLow()) / dayRange;
        boolean weakClose = pricePosition < 0.3; // Closing in lower 30% of range
        
        // 4. Support/Resistance Analysis
        RealTechnicalAnalysis.SupportResistance sr = RealTechnicalAnalysis.calculateSupportResistance(
            prices, prices, prices // Simplified for now
        );
        boolean nearResistance = currentPrice > sr.resistance * 0.98;
        boolean belowSupport = currentPrice < sr.support * 1.02;
        
        // 5. Momentum Divergence
        boolean momentumDivergence = analyzeMomentumDivergence(prices, volumes);
        
        // 6. Market Structure Analysis
        boolean downtrendConfirmed = analyzeDowntrend(prices);
        
        // 7. Volatility Analysis
        double volatility = RealTechnicalAnalysis.calculateVolatility(prices);
        boolean highVolatility = volatility > 0.3;
        
        return new SellFactorAnalysis(
            rsi, macd, bb, distributionVolume, weakClose, nearResistance, belowSupport,
            momentumDivergence, downtrendConfirmed, highVolatility, volumeRatio, pricePosition
        );
    }
    
    /**
     * Calculate enhanced SELL confidence using improved methodology
     */
    private static double calculateEnhancedSellConfidence(SellFactorAnalysis analysis, 
                                                         EnhancedPredictionSystem.MarketCondition marketCondition) {
        
        double confidence = 0.0;
        
        // Technical Indicators (30% weight)
        if (analysis.rsi > 70) confidence += 0.10; // Overbought
        if (analysis.rsi > 80) confidence += 0.05; // Extremely overbought
        if (!analysis.macd.bullish) confidence += 0.10; // MACD bearish
        if (analysis.macd.histogram < -5) confidence += 0.05; // Strong bearish momentum
        
        // Volume Analysis (20% weight)
        if (analysis.distributionVolume) confidence += 0.15; // High volume selling
        if (analysis.volumeRatio > 2.0) confidence += 0.05; // Extremely high volume
        
        // Price Action (20% weight)
        if (analysis.weakClose) confidence += 0.10; // Weak intraday close
        if (analysis.nearResistance) confidence += 0.10; // At resistance level
        
        // Market Structure (15% weight)
        if (analysis.downtrendConfirmed) confidence += 0.10; // Downtrend confirmed
        if (analysis.belowSupport) confidence += 0.05; // Support broken
        
        // Advanced Signals (15% weight)
        if (analysis.momentumDivergence) confidence += 0.10; // Momentum divergence
        if (analysis.highVolatility && marketCondition.condition.contains("VOLATILE")) {
            confidence += 0.05; // High volatility selling opportunity
        }
        
        // Market Condition Adjustments
        switch (marketCondition.condition) {
            case "TRENDING":
                if (analysis.downtrendConfirmed) {
                    confidence *= 1.20; // Boost in confirmed downtrend
                } else {
                    confidence *= 0.80; // Reduce in uptrend
                }
                break;
            case "SIDEWAYS":
                if (analysis.nearResistance) {
                    confidence *= 1.15; // Boost at resistance in sideways market
                }
                break;
            case "VOLATILE":
                confidence *= 0.90; // Slightly reduce in volatile conditions
                break;
        }
        
        // Ensure confidence bounds
        return Math.max(0.1, Math.min(0.95, confidence));
    }
    
    /**
     * Determine optimal SELL strategy based on analysis
     */
    private static SellStrategy determineSellStrategy(SellFactorAnalysis analysis, 
                                                     EnhancedPredictionSystem.MarketCondition marketCondition) {
        
        String strategyType;
        String reasoning;
        
        if (analysis.rsi > 80 && analysis.distributionVolume && analysis.nearResistance) {
            strategyType = "AGGRESSIVE_SELL";
            reasoning = "Multiple strong bearish signals - immediate exit recommended";
        } else if (analysis.downtrendConfirmed && analysis.belowSupport) {
            strategyType = "TREND_FOLLOWING_SELL";
            reasoning = "Downtrend confirmed with support break - follow trend";
        } else if (analysis.nearResistance && marketCondition.condition.equals("SIDEWAYS")) {
            strategyType = "RESISTANCE_SELL";
            reasoning = "At resistance in sideways market - range trading opportunity";
        } else if (analysis.momentumDivergence) {
            strategyType = "DIVERGENCE_SELL";
            reasoning = "Momentum divergence detected - early reversal signal";
        } else {
            strategyType = "CAUTIOUS_SELL";
            reasoning = "Moderate bearish signals - partial exit or tight stop";
        }
        
        return new SellStrategy(strategyType, reasoning);
    }
    
    /**
     * Calculate precise exit levels for improved SELL accuracy
     */
    private static ExitLevels calculatePreciseExitLevels(StockData currentData, 
                                                        SellFactorAnalysis analysis, 
                                                        SellStrategy strategy) {
        
        double currentPrice = currentData.getLastPrice();
        double atr = calculateATR(currentData);
        
        double target1, target2, target3, stopLoss;
        
        switch (strategy.type) {
            case "AGGRESSIVE_SELL":
                target1 = currentPrice - (atr * 1.5);
                target2 = currentPrice - (atr * 3.0);
                target3 = currentPrice - (atr * 4.5);
                stopLoss = currentPrice + (atr * 0.8); // Tight stop for aggressive sells
                break;
                
            case "TREND_FOLLOWING_SELL":
                target1 = currentPrice - (atr * 2.0);
                target2 = currentPrice - (atr * 4.0);
                target3 = currentPrice - (atr * 6.0);
                stopLoss = currentPrice + (atr * 1.2); // Wider stop for trend following
                break;
                
            case "RESISTANCE_SELL":
                target1 = currentPrice - (atr * 1.0);
                target2 = currentPrice - (atr * 2.0);
                target3 = currentPrice - (atr * 3.0);
                stopLoss = currentPrice + (atr * 1.0); // Moderate stop at resistance
                break;
                
            case "DIVERGENCE_SELL":
                target1 = currentPrice - (atr * 1.2);
                target2 = currentPrice - (atr * 2.5);
                target3 = currentPrice - (atr * 4.0);
                stopLoss = currentPrice + (atr * 1.5); // Wider stop for divergence
                break;
                
            default: // CAUTIOUS_SELL
                target1 = currentPrice - (atr * 0.8);
                target2 = currentPrice - (atr * 1.5);
                target3 = currentPrice - (atr * 2.5);
                stopLoss = currentPrice + (atr * 1.8); // Wide stop for cautious approach
                break;
        }
        
        // Calculate risk-reward ratio
        double risk = Math.abs(currentPrice - stopLoss);
        double reward = Math.abs(target2 - currentPrice);
        double riskReward = reward / risk;
        
        return new ExitLevels(target1, target2, target3, stopLoss, riskReward);
    }
    
    /**
     * Generate improved SELL recommendation
     */
    private static String generateSellRecommendation(double confidence, SellStrategy strategy, ExitLevels exitLevels) {
        StringBuilder recommendation = new StringBuilder();
        
        if (confidence >= 0.85) {
            recommendation.append("🔥 STRONG SELL");
        } else if (confidence >= 0.75) {
            recommendation.append("✅ SELL");
        } else if (confidence >= 0.65) {
            recommendation.append("👍 CONSIDER SELL");
        } else {
            recommendation.append("⚠️ WEAK SELL");
        }
        
        recommendation.append(String.format(" (%.1f%% confidence)", confidence * 100));
        recommendation.append(" | ").append(strategy.reasoning);
        recommendation.append(String.format(" | R:R 1:%.1f", exitLevels.riskReward));
        
        // Add strategy-specific advice
        switch (strategy.type) {
            case "AGGRESSIVE_SELL":
                recommendation.append(" | Execute immediately");
                break;
            case "TREND_FOLLOWING_SELL":
                recommendation.append(" | Follow the trend");
                break;
            case "RESISTANCE_SELL":
                recommendation.append(" | Range trading opportunity");
                break;
            case "DIVERGENCE_SELL":
                recommendation.append(" | Early reversal signal");
                break;
            default:
                recommendation.append(" | Use partial exit strategy");
                break;
        }
        
        return recommendation.toString();
    }
    
    // Helper methods
    private static boolean analyzeMomentumDivergence(List<Double> prices, List<Long> volumes) {
        if (prices.size() < 10) return false;
        
        // Simplified momentum divergence analysis
        double recentPriceChange = prices.get(prices.size() - 1) - prices.get(prices.size() - 5);
        double recentVolumeAvg = volumes.subList(volumes.size() - 5, volumes.size())
                                       .stream().mapToLong(Long::longValue).average().orElse(1.0);
        double olderVolumeAvg = volumes.subList(volumes.size() - 10, volumes.size() - 5)
                                      .stream().mapToLong(Long::longValue).average().orElse(1.0);
        
        // Price going up but volume decreasing = bearish divergence
        return recentPriceChange > 0 && recentVolumeAvg < olderVolumeAvg * 0.8;
    }
    
    private static boolean analyzeDowntrend(List<Double> prices) {
        if (prices.size() < 20) return false;
        
        double sma10 = RealTechnicalAnalysis.calculateSMA(prices, 10);
        double sma20 = RealTechnicalAnalysis.calculateSMA(prices, 20);
        double currentPrice = prices.get(prices.size() - 1);
        
        return currentPrice < sma10 && sma10 < sma20;
    }
    
    private static double calculateATR(StockData currentData) {
        double dayRange = currentData.getDayHigh() - currentData.getDayLow();
        return Math.max(dayRange, currentData.getLastPrice() * 0.02); // Minimum 2% ATR
    }
    
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
    
    // Supporting classes
    public static class SellFactorAnalysis {
        public final double rsi;
        public final RealTechnicalAnalysis.MACDResult macd;
        public final RealTechnicalAnalysis.BollingerBands bb;
        public final boolean distributionVolume;
        public final boolean weakClose;
        public final boolean nearResistance;
        public final boolean belowSupport;
        public final boolean momentumDivergence;
        public final boolean downtrendConfirmed;
        public final boolean highVolatility;
        public final double volumeRatio;
        public final double pricePosition;
        
        public SellFactorAnalysis(double rsi, RealTechnicalAnalysis.MACDResult macd, 
                                 RealTechnicalAnalysis.BollingerBands bb, boolean distributionVolume,
                                 boolean weakClose, boolean nearResistance, boolean belowSupport,
                                 boolean momentumDivergence, boolean downtrendConfirmed, 
                                 boolean highVolatility, double volumeRatio, double pricePosition) {
            this.rsi = rsi;
            this.macd = macd;
            this.bb = bb;
            this.distributionVolume = distributionVolume;
            this.weakClose = weakClose;
            this.nearResistance = nearResistance;
            this.belowSupport = belowSupport;
            this.momentumDivergence = momentumDivergence;
            this.downtrendConfirmed = downtrendConfirmed;
            this.highVolatility = highVolatility;
            this.volumeRatio = volumeRatio;
            this.pricePosition = pricePosition;
        }
    }
    
    public static class SellStrategy {
        public final String type;
        public final String reasoning;
        
        public SellStrategy(String type, String reasoning) {
            this.type = type;
            this.reasoning = reasoning;
        }
    }
    
    public static class ExitLevels {
        public final double target1;
        public final double target2;
        public final double target3;
        public final double stopLoss;
        public final double riskReward;
        
        public ExitLevels(double target1, double target2, double target3, double stopLoss, double riskReward) {
            this.target1 = target1;
            this.target2 = target2;
            this.target3 = target3;
            this.stopLoss = stopLoss;
            this.riskReward = riskReward;
        }
    }
    
    public static class SellSignalResult {
        public final String symbol;
        public final double confidence;
        public final SellStrategy strategy;
        public final ExitLevels exitLevels;
        public final String recommendation;
        public final SellFactorAnalysis analysis;
        public final java.time.LocalDateTime signalTime;
        
        public SellSignalResult(String symbol, double confidence, SellStrategy strategy,
                               ExitLevels exitLevels, String recommendation, 
                               SellFactorAnalysis analysis, java.time.LocalDateTime signalTime) {
            this.symbol = symbol;
            this.confidence = confidence;
            this.strategy = strategy;
            this.exitLevels = exitLevels;
            this.recommendation = recommendation;
            this.analysis = analysis;
            this.signalTime = signalTime;
        }
        
        @Override
        public String toString() {
            return String.format("%s: SELL (%.1f%% confidence) | Strategy: %s | Targets: %.2f/%.2f/%.2f | SL: %.2f", 
                               symbol, confidence * 100, strategy.type, 
                               exitLevels.target1, exitLevels.target2, exitLevels.target3, exitLevels.stopLoss);
        }
    }
}