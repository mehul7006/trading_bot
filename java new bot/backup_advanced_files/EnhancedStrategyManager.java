package com.stockbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Enhanced Strategy Manager - Multi-Strategy Trading System
 * Implements 5 different trading strategies with dynamic selection
 */
public class EnhancedStrategyManager {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedStrategyManager.class);
    
    private final SuperiorPredictionEngine predictionEngine;
    private final Map<String, TradingStrategy> strategies;
    private final Map<String, StrategyPerformance> strategyPerformance;
    private final ScheduledExecutorService scheduler;
    
    // Strategy weights (dynamically adjusted based on performance)
    private final Map<String, Double> strategyWeights;
    
    public EnhancedStrategyManager() {
        this.predictionEngine = new SuperiorPredictionEngine();
        this.strategies = initializeStrategies();
        this.strategyPerformance = new ConcurrentHashMap<>();
        this.strategyWeights = initializeStrategyWeights();
        this.scheduler = Executors.newScheduledThreadPool(3);
    }
    
    /**
     * Main strategy execution method
     */
    public List<TradingSignal> generateTradingSignals(List<IndexData> historicalData, IndexData currentData, String indexSymbol) {
        List<TradingSignal> signals = new ArrayList<>();
        
        try {
            // Get enhanced prediction
            SuperiorPredictionEngine.EnhancedPrediction prediction = 
                predictionEngine.predictMovement(historicalData, currentData, indexSymbol);
            
            // Run all strategies and collect signals
            for (Map.Entry<String, TradingStrategy> entry : strategies.entrySet()) {
                String strategyName = entry.getKey();
                TradingStrategy strategy = entry.getValue();
                
                try {
                    TradingSignal signal = strategy.generateSignal(historicalData, currentData, prediction, indexSymbol);
                    if (signal != null && signal.getConfidence() > 0.6) {
                        // Weight signal by strategy performance
                        double weight = strategyWeights.get(strategyName);
                        signal.adjustConfidence(weight);
                        signals.add(signal);
                        
                        logger.info("Strategy {} generated signal: {} with confidence {}", 
                                  strategyName, signal.getAction(), signal.getConfidence());
                    }
                } catch (Exception e) {
                    logger.error("Error in strategy {}", strategyName, e);
                }
            }
            
            // Filter and rank signals
            signals = filterAndRankSignals(signals, prediction);
            
        } catch (Exception e) {
            logger.error("Error in strategy manager", e);
        }
        
        return signals;
    }
    
    /**
     * Initialize all trading strategies
     */
    private Map<String, TradingStrategy> initializeStrategies() {
        Map<String, TradingStrategy> strategies = new HashMap<>();
        
        strategies.put("MOMENTUM_BREAKOUT", new MomentumBreakoutStrategy());
        strategies.put("MEAN_REVERSION", new MeanReversionStrategy());
        strategies.put("VOLATILITY_BREAKOUT", new VolatilityBreakoutStrategy());
        strategies.put("TREND_FOLLOWING", new TrendFollowingStrategy());
        strategies.put("SCALPING", new ScalpingStrategy());
        
        return strategies;
    }
    
    /**
     * Filter and rank signals by quality and market conditions
     */
    private List<TradingSignal> filterAndRankSignals(List<TradingSignal> signals, 
                                                    SuperiorPredictionEngine.EnhancedPrediction prediction) {
        return signals.stream()
            .filter(signal -> signal.getConfidence() > 0.7) // High confidence only
            .filter(signal -> isSignalValidForMarketCondition(signal, prediction))
            .sorted((s1, s2) -> Double.compare(s2.getConfidence(), s1.getConfidence()))
            .limit(3) // Top 3 signals only
            .collect(ArrayList::new, (list, signal) -> list.add(signal), ArrayList::addAll);
    }
    
    private boolean isSignalValidForMarketCondition(TradingSignal signal, 
                                                   SuperiorPredictionEngine.EnhancedPrediction prediction) {
        // Check if signal aligns with overall market prediction
        if (prediction.getPrediction() > 15 && signal.getAction().equals("SELL")) {
            return false; // Don't sell in strong bullish prediction
        }
        if (prediction.getPrediction() < -15 && signal.getAction().equals("BUY")) {
            return false; // Don't buy in strong bearish prediction
        }
        
        // Check time-based validity
        LocalTime now = LocalTime.now();
        if (signal.getStrategy().equals("SCALPING") && 
            (now.isBefore(LocalTime.of(9, 30)) || now.isAfter(LocalTime.of(15, 15)))) {
            return false; // Scalping only during active hours
        }
        
        return true;
    }
    
    private Map<String, Double> initializeStrategyWeights() {
        Map<String, Double> weights = new HashMap<>();
        weights.put("MOMENTUM_BREAKOUT", 1.0);
        weights.put("MEAN_REVERSION", 1.0);
        weights.put("VOLATILITY_BREAKOUT", 1.0);
        weights.put("TREND_FOLLOWING", 1.0);
        weights.put("SCALPING", 1.0);
        return weights;
    }
    
    // ==================== TRADING STRATEGIES ====================
    
    /**
     * Momentum Breakout Strategy - Catches strong directional moves
     */
    private class MomentumBreakoutStrategy implements TradingStrategy {
        @Override
        public TradingSignal generateSignal(List<IndexData> data, IndexData current, 
                                          SuperiorPredictionEngine.EnhancedPrediction prediction, String symbol) {
            
            if (data.size() < 20) return null;
            
            // Calculate momentum indicators
            double momentum5 = calculateMomentum(data, 5);
            double momentum10 = calculateMomentum(data, 10);
            double volumeRatio = calculateVolumeRatio(data, current);
            double rsi = calculateRSI(data, 14);
            
            // Breakout conditions
            boolean bullishBreakout = momentum5 > 2.0 && momentum10 > 1.0 && 
                                    volumeRatio > 1.5 && rsi < 70 && prediction.getPrediction() > 10;
            
            boolean bearishBreakout = momentum5 < -2.0 && momentum10 < -1.0 && 
                                    volumeRatio > 1.5 && rsi > 30 && prediction.getPrediction() < -10;
            
            if (bullishBreakout) {
                double target = current.getLastPrice() * 1.015; // 1.5% target
                double stopLoss = current.getLastPrice() * 0.992; // 0.8% stop
                double confidence = Math.min(0.95, (momentum5 + volumeRatio + prediction.getConfidence()) / 3.0);
                
                return new TradingSignal("BUY", current.getLastPrice(), target, stopLoss, 
                                       confidence, "MOMENTUM_BREAKOUT", 
                                       "Strong bullish momentum with volume confirmation");
            }
            
            if (bearishBreakout) {
                double target = current.getLastPrice() * 0.985; // 1.5% target
                double stopLoss = current.getLastPrice() * 1.008; // 0.8% stop
                double confidence = Math.min(0.95, (Math.abs(momentum5) + volumeRatio + prediction.getConfidence()) / 3.0);
                
                return new TradingSignal("SELL", current.getLastPrice(), target, stopLoss, 
                                       confidence, "MOMENTUM_BREAKOUT", 
                                       "Strong bearish momentum with volume confirmation");
            }
            
            return null;
        }
    }
    
    /**
     * Mean Reversion Strategy - Profits from price returning to mean
     */
    private class MeanReversionStrategy implements TradingStrategy {
        @Override
        public TradingSignal generateSignal(List<IndexData> data, IndexData current, 
                                          SuperiorPredictionEngine.EnhancedPrediction prediction, String symbol) {
            
            if (data.size() < 20) return null;
            
            double sma20 = calculateSMA(data, 20);
            double bollinger_upper = sma20 + (2 * calculateStdDev(data, 20));
            double bollinger_lower = sma20 - (2 * calculateStdDev(data, 20));
            double rsi = calculateRSI(data, 14);
            double currentPrice = current.getLastPrice();
            
            // Oversold condition - expect reversion up
            boolean oversold = currentPrice < bollinger_lower && rsi < 30 && 
                             prediction.getConfidence() > 0.7;
            
            // Overbought condition - expect reversion down
            boolean overbought = currentPrice > bollinger_upper && rsi > 70 && 
                               prediction.getConfidence() > 0.7;
            
            if (oversold) {
                double target = sma20; // Target mean
                double stopLoss = currentPrice * 0.995; // Tight stop
                double confidence = Math.min(0.9, (100 - rsi) / 100.0 + prediction.getConfidence()) / 2.0;
                
                return new TradingSignal("BUY", currentPrice, target, stopLoss, 
                                       confidence, "MEAN_REVERSION", 
                                       "Oversold condition - expecting reversion to mean");
            }
            
            if (overbought) {
                double target = sma20; // Target mean
                double stopLoss = currentPrice * 1.005; // Tight stop
                double confidence = Math.min(0.9, (rsi - 50) / 50.0 + prediction.getConfidence()) / 2.0;
                
                return new TradingSignal("SELL", currentPrice, target, stopLoss, 
                                       confidence, "MEAN_REVERSION", 
                                       "Overbought condition - expecting reversion to mean");
            }
            
            return null;
        }
    }
    
    /**
     * Volatility Breakout Strategy - Trades volatility expansions
     */
    private class VolatilityBreakoutStrategy implements TradingStrategy {
        @Override
        public TradingSignal generateSignal(List<IndexData> data, IndexData current, 
                                          SuperiorPredictionEngine.EnhancedPrediction prediction, String symbol) {
            
            if (data.size() < 30) return null;
            
            double atr = calculateATR(data, 14);
            double volatility = calculateVolatility(data, 20);
            double avgVolatility = calculateAverageVolatility(data, 30);
            double currentPrice = current.getLastPrice();
            
            // Volatility expansion condition
            boolean volExpansion = volatility > avgVolatility * 1.5 && atr > avgVolatility * 0.8;
            
            if (volExpansion && prediction.getPrediction() > 20) {
                // Bullish volatility breakout
                double target = currentPrice + (atr * 2); // 2x ATR target
                double stopLoss = currentPrice - (atr * 0.8); // 0.8x ATR stop
                double confidence = Math.min(0.9, volatility / avgVolatility * prediction.getConfidence());
                
                return new TradingSignal("BUY", currentPrice, target, stopLoss, 
                                       confidence, "VOLATILITY_BREAKOUT", 
                                       "High volatility expansion with bullish bias");
            }
            
            if (volExpansion && prediction.getPrediction() < -20) {
                // Bearish volatility breakout
                double target = currentPrice - (atr * 2); // 2x ATR target
                double stopLoss = currentPrice + (atr * 0.8); // 0.8x ATR stop
                double confidence = Math.min(0.9, volatility / avgVolatility * prediction.getConfidence());
                
                return new TradingSignal("SELL", currentPrice, target, stopLoss, 
                                       confidence, "VOLATILITY_BREAKOUT", 
                                       "High volatility expansion with bearish bias");
            }
            
            return null;
        }
    }
    
    /**
     * Trend Following Strategy - Rides established trends
     */
    private class TrendFollowingStrategy implements TradingStrategy {
        @Override
        public TradingSignal generateSignal(List<IndexData> data, IndexData current, 
                                          SuperiorPredictionEngine.EnhancedPrediction prediction, String symbol) {
            
            if (data.size() < 50) return null;
            
            double sma20 = calculateSMA(data, 20);
            double sma50 = calculateSMA(data, 50);
            double ema12 = calculateEMA(data, 12);
            double ema26 = calculateEMA(data, 26);
            double macd = ema12 - ema26;
            double currentPrice = current.getLastPrice();
            
            // Strong uptrend
            boolean uptrend = currentPrice > sma20 && sma20 > sma50 && 
                            macd > 0 && prediction.getPrediction() > 15;
            
            // Strong downtrend
            boolean downtrend = currentPrice < sma20 && sma20 < sma50 && 
                              macd < 0 && prediction.getPrediction() < -15;
            
            if (uptrend) {
                double target = currentPrice * 1.02; // 2% target
                double stopLoss = sma20 * 0.998; // Stop below SMA20
                double confidence = Math.min(0.92, prediction.getConfidence() * 1.1);
                
                return new TradingSignal("BUY", currentPrice, target, stopLoss, 
                                       confidence, "TREND_FOLLOWING", 
                                       "Strong uptrend continuation signal");
            }
            
            if (downtrend) {
                double target = currentPrice * 0.98; // 2% target
                double stopLoss = sma20 * 1.002; // Stop above SMA20
                double confidence = Math.min(0.92, prediction.getConfidence() * 1.1);
                
                return new TradingSignal("SELL", currentPrice, target, stopLoss, 
                                       confidence, "TREND_FOLLOWING", 
                                       "Strong downtrend continuation signal");
            }
            
            return null;
        }
    }
    
    /**
     * Scalping Strategy - Quick small profits
     */
    private class ScalpingStrategy implements TradingStrategy {
        @Override
        public TradingSignal generateSignal(List<IndexData> data, IndexData current, 
                                          SuperiorPredictionEngine.EnhancedPrediction prediction, String symbol) {
            
            // Only during active market hours
            LocalTime now = LocalTime.now();
            if (now.isBefore(LocalTime.of(9, 30)) || now.isAfter(LocalTime.of(15, 15))) {
                return null;
            }
            
            if (data.size() < 10) return null;
            
            double momentum1 = calculateMomentum(data, 1);
            double momentum3 = calculateMomentum(data, 3);
            double volumeRatio = calculateVolumeRatio(data, current);
            double currentPrice = current.getLastPrice();
            
            // Quick scalp up
            boolean scalpUp = momentum1 > 0.3 && momentum3 > 0.2 && 
                            volumeRatio > 1.2 && prediction.getPrediction() > 5;
            
            // Quick scalp down
            boolean scalpDown = momentum1 < -0.3 && momentum3 < -0.2 && 
                              volumeRatio > 1.2 && prediction.getPrediction() < -5;
            
            if (scalpUp) {
                double target = currentPrice * 1.003; // 0.3% target
                double stopLoss = currentPrice * 0.998; // 0.2% stop
                double confidence = Math.min(0.85, (momentum1 + volumeRatio + prediction.getConfidence()) / 3.0);
                
                return new TradingSignal("BUY", currentPrice, target, stopLoss, 
                                       confidence, "SCALPING", 
                                       "Quick scalp opportunity - bullish momentum");
            }
            
            if (scalpDown) {
                double target = currentPrice * 0.997; // 0.3% target
                double stopLoss = currentPrice * 1.002; // 0.2% stop
                double confidence = Math.min(0.85, (Math.abs(momentum1) + volumeRatio + prediction.getConfidence()) / 3.0);
                
                return new TradingSignal("SELL", currentPrice, target, stopLoss, 
                                       confidence, "SCALPING", 
                                       "Quick scalp opportunity - bearish momentum");
            }
            
            return null;
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private double calculateMomentum(List<IndexData> data, int period) {
        if (data.size() < period + 1) return 0.0;
        
        double current = data.get(data.size() - 1).getLastPrice();
        double past = data.get(data.size() - period - 1).getLastPrice();
        
        return (current - past) / past * 100;
    }
    
    private double calculateVolumeRatio(List<IndexData> data, IndexData current) {
        if (data.size() < 10) return 1.0;
        
        double avgVolume = data.stream()
            .skip(Math.max(0, data.size() - 10))
            .mapToLong(IndexData::getVolume)
            .average().orElse(1.0);
        
        return avgVolume == 0 ? 1.0 : current.getVolume() / avgVolume;
    }
    
    private double calculateRSI(List<IndexData> data, int period) {
        if (data.size() < period + 1) return 50.0;
        
        double gains = 0.0, losses = 0.0;
        
        for (int i = data.size() - period; i < data.size(); i++) {
            double change = data.get(i).getLastPrice() - data.get(i - 1).getLastPrice();
            if (change > 0) gains += change;
            else losses -= change;
        }
        
        double avgGain = gains / period;
        double avgLoss = losses / period;
        
        if (avgLoss == 0) return 100.0;
        
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }
    
    private double calculateSMA(List<IndexData> data, int period) {
        if (data.size() < period) return data.get(data.size() - 1).getLastPrice();
        
        return data.stream()
            .skip(Math.max(0, data.size() - period))
            .mapToDouble(IndexData::getLastPrice)
            .average().orElse(0.0);
    }
    
    private double calculateEMA(List<IndexData> data, int period) {
        if (data.size() < period) return data.get(data.size() - 1).getLastPrice();
        
        double multiplier = 2.0 / (period + 1);
        double ema = data.get(data.size() - period).getLastPrice();
        
        for (int i = data.size() - period + 1; i < data.size(); i++) {
            ema = (data.get(i).getLastPrice() * multiplier) + (ema * (1 - multiplier));
        }
        
        return ema;
    }
    
    private double calculateStdDev(List<IndexData> data, int period) {
        if (data.size() < period) return 0.0;
        
        double mean = calculateSMA(data, period);
        double variance = data.stream()
            .skip(Math.max(0, data.size() - period))
            .mapToDouble(d -> Math.pow(d.getLastPrice() - mean, 2))
            .average().orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    private double calculateATR(List<IndexData> data, int period) {
        if (data.size() < period) return 0.0;
        
        double atr = 0.0;
        for (int i = data.size() - period; i < data.size(); i++) {
            IndexData current = data.get(i);
            if (i > 0) {
                IndexData previous = data.get(i - 1);
                double tr = Math.max(
                    current.getHighPrice() - current.getLowPrice(),
                    Math.max(
                        Math.abs(current.getHighPrice() - previous.getLastPrice()),
                        Math.abs(current.getLowPrice() - previous.getLastPrice())
                    )
                );
                atr += tr;
            }
        }
        
        return atr / period;
    }
    
    private double calculateVolatility(List<IndexData> data, int period) {
        if (data.size() < period) return 0.02;
        
        List<Double> returns = new ArrayList<>();
        for (int i = data.size() - period; i < data.size() - 1; i++) {
            double ret = (data.get(i + 1).getLastPrice() - data.get(i).getLastPrice()) / data.get(i).getLastPrice();
            returns.add(ret);
        }
        
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
            .mapToDouble(ret -> Math.pow(ret - mean, 2))
            .average().orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    private double calculateAverageVolatility(List<IndexData> data, int period) {
        if (data.size() < period) return 0.02;
        
        double totalVol = 0.0;
        int count = 0;
        
        for (int i = 10; i <= period && i < data.size(); i += 5) {
            totalVol += calculateVolatility(data.subList(data.size() - i, data.size()), Math.min(i, 10));
            count++;
        }
        
        return count > 0 ? totalVol / count : 0.02;
    }
    
    // ==================== INTERFACES AND CLASSES ====================
    
    private interface TradingStrategy {
        TradingSignal generateSignal(List<IndexData> data, IndexData current, 
                                   SuperiorPredictionEngine.EnhancedPrediction prediction, String symbol);
    }
    
    public static class TradingSignal {
        private final String action;
        private final double entryPrice;
        private final double targetPrice;
        private final double stopLossPrice;
        private double confidence;
        private final String strategy;
        private final String reason;
        private final LocalDateTime timestamp;
        
        public TradingSignal(String action, double entryPrice, double targetPrice, 
                           double stopLossPrice, double confidence, String strategy, String reason) {
            this.action = action;
            this.entryPrice = entryPrice;
            this.targetPrice = targetPrice;
            this.stopLossPrice = stopLossPrice;
            this.confidence = confidence;
            this.strategy = strategy;
            this.reason = reason;
            this.timestamp = LocalDateTime.now();
        }
        
        public void adjustConfidence(double weight) {
            this.confidence = Math.min(0.98, this.confidence * weight);
        }
        
        // Getters
        public String getAction() { return action; }
        public double getEntryPrice() { return entryPrice; }
        public double getTargetPrice() { return targetPrice; }
        public double getStopLossPrice() { return stopLossPrice; }
        public double getConfidence() { return confidence; }
        public String getStrategy() { return strategy; }
        public String getReason() { return reason; }
        public LocalDateTime getTimestamp() { return timestamp; }
        
        public double getRiskRewardRatio() {
            double risk = Math.abs(entryPrice - stopLossPrice);
            double reward = Math.abs(targetPrice - entryPrice);
            return risk > 0 ? reward / risk : 0.0;
        }
    }
    
    private static class StrategyPerformance {
        private double totalTrades = 0;
        private double winningTrades = 0;
        private double totalProfit = 0;
        
        public void addTrade(boolean isWin, double profit) {
            totalTrades++;
            if (isWin) winningTrades++;
            totalProfit += profit;
        }
        
        public double getWinRate() {
            return totalTrades > 0 ? winningTrades / totalTrades : 0.5;
        }
        
        public double getAverageProfit() {
            return totalTrades > 0 ? totalProfit / totalTrades : 0.0;
        }
    }
    
    public void shutdown() {
        scheduler.shutdown();
        logger.info("Enhanced Strategy Manager shutdown");
    }
}