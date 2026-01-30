package com.trading.bot.options;

import java.time.*;
import java.util.*;
import com.trading.bot.ml.*;
import com.trading.bot.market.*;

/**
 * Enhanced Options Call Generator
 * Combines ML market analysis with options-specific calculations
 */
public class EnhancedOptionsCallGenerator {
    public static class Greeks {
        private final double delta;
        private final double gamma;
        private final double theta;
        private final double vega;
        private final double rho;

        public Greeks(double delta, double gamma, double theta, double vega, double rho) {
            this.delta = delta;
            this.gamma = gamma;
            this.theta = theta;
            this.vega = vega;
            this.rho = rho;
        }

        public double getDelta() {
            return delta;
        }

        public double getGamma() {
            return gamma;
        }

        public double getTheta() {
            return theta;
        }

        public double getVega() {
            return vega;
        }

        public double getRho() {
            return rho;
        }
    }

    public static class OptionTradeSignal {
        public final String symbol;
        public final OptionType type;
        public final double confidence;
        public final double entryPrice;
        public final double targetPrice;
        public final double stopLoss;

        public OptionTradeSignal(String symbol, OptionType type, double confidence,
                               double entryPrice, double targetPrice, double stopLoss) {
            this.symbol = symbol;
            this.type = type;
            this.confidence = confidence;
            this.entryPrice = entryPrice;
            this.targetPrice = targetPrice;
            this.stopLoss = stopLoss;
        }
    }

    public enum OptionType {
        CALL,
        PUT
    }

    private final MLMarketAnalyzer mlAnalyzer;
    private final RealTimeDataCollector dataCollector;
    private final OptionsCalculator optionsCalculator;
    private final double MIN_OPTION_CONFIDENCE = 88.0; // Higher threshold for options
    
    public EnhancedOptionsCallGenerator() {
        this.mlAnalyzer = new MLMarketAnalyzer();
        this.dataCollector = new RealTimeDataCollector();
        this.optionsCalculator = new OptionsCalculator();
        System.out.println("Enhanced Options Call Generator Initialized");
    }
    
    public OptionTradeSignal generateOptionsCall(String symbol, OptionType type) {
        // 1. Get ML-based market prediction
        MarketPrediction marketPred = mlAnalyzer.analyzeMarket(symbol);
        if (marketPred == null || marketPred.confidence < MIN_OPTION_CONFIDENCE) {
            return null; // Skip if market prediction confidence is too low
        }
        
        // 2. Get real-time options data
        MarketData marketData = dataCollector.getRealTimeData(symbol);
        if (marketData == null || marketData.isMocked()) {
            return null; // Skip if no real data available
        }
        
        // 3. Calculate options-specific metrics
        OptionsMetrics metrics = optionsCalculator.calculateMetrics(
            marketData,
            type
        );
        
        // 4. Validate trade conditions
        if (!validateTradeConditions(marketPred, metrics, type)) {
            return null; // Skip if conditions aren't ideal
        }
        
        // 5. Generate specific option strike and expiry
        OptionContract contract = selectOptionContract(
            marketData,
            marketPred,
            metrics,
            type
        );
        
        // 6. Calculate final trade confidence
        double finalConfidence = calculateFinalConfidence(
            marketPred,
            metrics,
            contract
        );
        
        if (finalConfidence < MIN_OPTION_CONFIDENCE) {
            return null; // Skip if final confidence is too low
        }
        
        // 7. Generate complete trade signal
        return new OptionTradeSignal(
            symbol,
            type,
            contract,
            marketPred.trend,
            finalConfidence,
            calculateEntryPrice(contract, metrics),
            calculateTargets(contract, metrics, marketPred),
            calculateStopLoss(contract, metrics, marketPred),
            LocalDateTime.now()
        );
    }
    
    private boolean validateTradeConditions(
            MarketPrediction marketPred,
            OptionsMetrics metrics,
            OptionType type) {
        
        // Check trend alignment
        boolean trendAligned = switch(type) {
            case CALL -> marketPred.trend == MLMarketAnalyzer.TrendDirection.UP;
            case PUT -> marketPred.trend == MLMarketAnalyzer.TrendDirection.DOWN;
        };
        
        if (!trendAligned) return false;
        
        // Validate volatility conditions
        if (metrics.impliedVolatility > 75.0) return false; // Too volatile
        if (metrics.impliedVolatility < 10.0) return false; // Too stable
        
        // Check volume and open interest
        if (metrics.volumeOI < 0.3) return false; // Insufficient liquidity
        
        // Verify premium is reasonable
        if (metrics.premium > metrics.maxPremium) return false;
        
        // Check time decay impact
        if (metrics.thetaImpact > metrics.maxTheta) return false;
        
        return true;
    }
    
    private OptionContract selectOptionContract(
            MarketData marketData,
            MarketPrediction marketPred,
            OptionsMetrics metrics,
            OptionType type) {
        
        double currentPrice = marketData.getPrice();
        LocalDate currentDate = LocalDate.now();
        
        // Calculate optimal strike price
        double strike = calculateOptimalStrike(
            currentPrice,
            marketPred,
            metrics,
            type
        );
        
        // Calculate optimal expiry
        LocalDate expiry = calculateOptimalExpiry(
            currentDate,
            metrics,
            marketPred
        );
        
        // Calculate Greeks
        Greeks greeks = optionsCalculator.calculateGreeks(
            currentPrice,
            strike,
            expiry,
            metrics.impliedVolatility
        );
        
        return new OptionContract(
            strike,
            expiry,
            type,
            greeks,
            metrics.premium
        );
    }
    
    private double calculateOptimalStrike(
            double currentPrice,
            MarketPrediction marketPred,
            OptionsMetrics metrics,
            OptionType type) {
        
        double volatilityAdjustment = metrics.impliedVolatility * 0.1;
        double trendAdjustment = Math.abs(marketPred.target1 - currentPrice) * 0.2;
        
        return switch(type) {
            case CALL -> {
                if (marketPred.trend == MLMarketAnalyzer.TrendDirection.UP) {
                    yield currentPrice * (1.0 + volatilityAdjustment);
                } else {
                    yield currentPrice * (1.0 - volatilityAdjustment);
                }
            }
            case PUT -> {
                if (marketPred.trend == MLMarketAnalyzer.TrendDirection.DOWN) {
                    yield currentPrice * (1.0 - volatilityAdjustment);
                } else {
                    yield currentPrice * (1.0 + volatilityAdjustment);
                }
            }
        };
    }
    
    private LocalDate calculateOptimalExpiry(
            LocalDate currentDate,
            OptionsMetrics metrics,
            MarketPrediction marketPred) {
        
        // Base expiry on trend strength and volatility
        int daysToAdd = switch(marketPred.trend) {
            case UP, DOWN -> {
                if (metrics.impliedVolatility > 40.0) {
                    yield 14; // Two weeks for high volatility
                } else {
                    yield 30; // One month for normal volatility
                }
            }
            case SIDEWAYS -> 45; // Longer duration for sideways market
        };
        
        return currentDate.plusDays(daysToAdd);
    }
    
    private double calculateFinalConfidence(
            MarketPrediction marketPred,
            OptionsMetrics metrics,
            OptionContract contract) {
        
        double confidence = marketPred.confidence * 0.6; // Base 60% on market prediction
        
        // Add options-specific confidence factors
        confidence += metrics.volumeOI * 10; // Up to 10% for liquidity
        confidence += (1.0 - metrics.thetaImpact) * 10; // Up to 10% for time decay
        confidence += (1.0 - contract.getGreeks().vega) * 10; // Up to 10% for volatility sensitivity
        confidence += metrics.putCallRatio * 10; // Up to 10% for sentiment
        
        return Math.min(confidence, 100.0);
    }
    
    private double calculateEntryPrice(
            OptionContract contract,
            OptionsMetrics metrics) {
        return contract.getPremium() * (1 + metrics.spread);
    }
    
    private double[] calculateTargets(
            OptionContract contract,
            OptionsMetrics metrics,
            MarketPrediction marketPred) {
        
        double premium = contract.getPremium();
        double target1 = premium * (1 + metrics.impliedVolatility * 0.2);
        double target2 = premium * (1 + metrics.impliedVolatility * 0.4);
        
        return new double[] { target1, target2 };
    }
    
    private double calculateStopLoss(
            OptionContract contract,
            OptionsMetrics metrics,
            MarketPrediction marketPred) {
        
        return contract.getPremium() * (1 - metrics.impliedVolatility * 0.15);
    }
    
    // Inner classes for options-specific data
    public enum OptionType { CALL, PUT }
    
    public static class OptionTradeSignal {
        private final String symbol;
        private final OptionType type;
        private final OptionContract contract;
        private final MLMarketAnalyzer.TrendDirection trend;
        private final double confidence;
        private final double entryPrice;
        private final double[] targets;
        private final double stopLoss;
        private final LocalDateTime timestamp;
        
        public OptionTradeSignal(String symbol, OptionType type,
                                OptionContract contract,
                                MLMarketAnalyzer.TrendDirection trend,
                                double confidence, double entryPrice,
                                double[] targets, double stopLoss,
                                LocalDateTime timestamp) {
            this.symbol = symbol;
            this.type = type;
            this.contract = contract;
            this.trend = trend;
            this.confidence = confidence;
            this.entryPrice = entryPrice;
            this.targets = targets;
            this.stopLoss = stopLoss;
            this.timestamp = timestamp;
        }
        
        @Override
        public String toString() {
            return String.format("""
                Option Trade Signal:
                Symbol: %s
                Type: %s
                Strike: %.2f
                Expiry: %s
                Trend: %s
                Confidence: %.2f%%
                Entry: %.2f
                Target 1: %.2f
                Target 2: %.2f
                Stop Loss: %.2f
                Time: %s
                Greeks:
                %s
                """,
                symbol, type, contract.getStrike(),
                contract.getExpiry(), trend, confidence,
                entryPrice, targets[0], targets[1],
                stopLoss, timestamp,
                contract.getGreeks());
        }
    }
    
    private static class OptionContract {
        private final double strike;
        private final LocalDate expiry;
        private final OptionType type;
        private final Greeks greeks;
        private final double premium;
        
        public OptionContract(double strike, LocalDate expiry,
                            OptionType type, Greeks greeks,
                            double premium) {
            this.strike = strike;
            this.expiry = expiry;
            this.type = type;
            this.greeks = greeks;
            this.premium = premium;
        }
        
        public double getStrike() { return strike; }
        public LocalDate getExpiry() { return expiry; }
        public OptionType getType() { return type; }
        public Greeks getGreeks() { return greeks; }
        public double getPremium() { return premium; }
    }
    
    private static class Greeks {
        private final double delta;
        private final double gamma;
        private final double theta;
        private final double vega;
        private final double rho;
        
        public Greeks(double delta, double gamma,
                     double theta, double vega, double rho) {
            this.delta = delta;
            this.gamma = gamma;
            this.theta = theta;
            this.vega = vega;
            this.rho = rho;
        }
        
        @Override
        public String toString() {
            return String.format("""
                Delta: %.4f
                Gamma: %.4f
                Theta: %.4f
                Vega: %.4f
                Rho: %.4f""",
                delta, gamma, theta, vega, rho);
        }
    }
    
    private static class OptionsMetrics {
        public final double impliedVolatility;
        public final double volumeOI; // Volume/Open Interest ratio
        public final double putCallRatio;
        public final double premium;
        public final double maxPremium;
        public final double thetaImpact;
        public final double maxTheta;
        public final double spread;
        
        public OptionsMetrics(double impliedVolatility,
                            double volumeOI,
                            double putCallRatio,
                            double premium,
                            double maxPremium,
                            double thetaImpact,
                            double maxTheta,
                            double spread) {
            this.impliedVolatility = impliedVolatility;
            this.volumeOI = volumeOI;
            this.putCallRatio = putCallRatio;
            this.premium = premium;
            this.maxPremium = maxPremium;
            this.thetaImpact = thetaImpact;
            this.maxTheta = maxTheta;
            this.spread = spread;
        }
    }
}