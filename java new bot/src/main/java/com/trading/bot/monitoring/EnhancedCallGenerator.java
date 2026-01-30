package com.trading.bot.monitoring;

import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enhanced Call Generator with Monitoring Integration
 * Automatically adds generated calls to monitoring system
 */
public class EnhancedCallGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedCallGenerator.class);
    
    private final ComprehensiveCallMonitoringSystem monitoringSystem;
    private final Random random = new Random();
    
    // Market data simulation
    private final Map<String, Double> currentPrices = new HashMap<>();
    
    public EnhancedCallGenerator(ComprehensiveCallMonitoringSystem monitoringSystem) {
        this.monitoringSystem = monitoringSystem;
        initializeMarketData();
        logger.info("🎯 Enhanced Call Generator initialized with monitoring integration");
    }
    
    private void initializeMarketData() {
        currentPrices.put("NIFTY", 23450.0);
        currentPrices.put("BANKNIFTY", 50100.0);
        currentPrices.put("FINNIFTY", 19800.0);
        currentPrices.put("SENSEX", 77200.0);
    }
    
    /**
     * Generate call with automatic monitoring setup
     */
    public GeneratedCall generateCallWithMonitoring(String symbol, String strategy) {
        try {
            logger.info("🎯 Generating call for {} with strategy {}", symbol, strategy);
            
            // Analyze market conditions
            MarketAnalysis analysis = analyzeMarket(symbol);
            
            // Generate call based on analysis
            GeneratedCall call = createCall(symbol, strategy, analysis);
            
            if (call != null) {
                // Automatically add to monitoring
                monitoringSystem.addCallForMonitoring(call);
                
                logger.info("✅ Generated and added call to monitoring: {}", call.getCallId());
                return call;
            }
            
        } catch (Exception e) {
            logger.error("❌ Error generating call for {}: {}", symbol, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Analyze market conditions for call generation
     */
    private MarketAnalysis analyzeMarket(String symbol) {
        double currentPrice = getCurrentPrice(symbol);
        
        // Simulate technical analysis
        TechnicalIndicators indicators = calculateIndicators(symbol, currentPrice);
        
        // Determine market bias
        MarketBias bias = determineBias(indicators);
        
        // Calculate confidence
        double confidence = calculateConfidence(indicators, bias);
        
        return new MarketAnalysis(symbol, currentPrice, indicators, bias, confidence);
    }
    
    /**
     * Create call based on market analysis
     */
    private GeneratedCall createCall(String symbol, String strategy, MarketAnalysis analysis) {
        
        if (analysis.bias == MarketBias.NEUTRAL || analysis.confidence < 0.65) {
            logger.info("⚠️ Skipping call generation for {} - low confidence or neutral bias", symbol);
            return null;
        }
        
        double entryPrice = analysis.currentPrice;
        double targetPrice;
        String expectedDirection;
        String callType;
        
        // Calculate target based on volatility and bias
        double volatilityFactor = analysis.indicators.volatility;
        double baseMove = entryPrice * 0.015; // 1.5% base move
        double volatilityAdjustedMove = baseMove * (1 + volatilityFactor);
        
        if (analysis.bias == MarketBias.BULLISH) {
            targetPrice = entryPrice + volatilityAdjustedMove;
            expectedDirection = "BULLISH";
            callType = getNearestStrike(symbol, entryPrice, true) + "CE";
        } else {
            targetPrice = entryPrice - volatilityAdjustedMove;
            expectedDirection = "BEARISH";
            callType = getNearestStrike(symbol, entryPrice, false) + "PE";
        }
        
        // Generate rationale
        String rationale = generateRationale(analysis);
        
        // Create call
        return GeneratedCall.builder()
            .symbol(symbol)
            .callType(callType)
            .expectedDirection(expectedDirection)
            .entryPrice(entryPrice)
            .targetPrice(targetPrice)
            .stopLossPrice(calculateStopLoss(entryPrice, targetPrice, analysis.bias))
            .investmentAmount(10000.0)
            .confidence(analysis.confidence)
            .strategy(strategy)
            .rationale(rationale)
            .expiryTime(getNextExpiry())
            .build();
    }
    
    /**
     * Calculate technical indicators
     */
    private TechnicalIndicators calculateIndicators(String symbol, double currentPrice) {
        // Simulate technical analysis
        double rsi = 30 + (random.nextDouble() * 40); // RSI between 30-70
        double macd = (random.nextGaussian() * 2); // MACD signal
        double volume = 0.8 + (random.nextDouble() * 0.6); // Volume ratio
        double volatility = 0.15 + (random.nextDouble() * 0.25); // Volatility 15-40%
        
        // Trend analysis
        boolean uptrend = random.nextBoolean();
        boolean support = random.nextBoolean();
        boolean resistance = random.nextBoolean();
        
        return new TechnicalIndicators(rsi, macd, volume, volatility, uptrend, support, resistance);
    }
    
    /**
     * Determine market bias from indicators
     */
    private MarketBias determineBias(TechnicalIndicators indicators) {
        int bullishSignals = 0;
        int bearishSignals = 0;
        
        // RSI analysis
        if (indicators.rsi < 35) bullishSignals++; // Oversold
        else if (indicators.rsi > 65) bearishSignals++; // Overbought
        
        // MACD analysis
        if (indicators.macd > 0.5) bullishSignals++;
        else if (indicators.macd < -0.5) bearishSignals++;
        
        // Volume analysis
        if (indicators.volume > 1.2 && indicators.uptrend) bullishSignals++;
        else if (indicators.volume > 1.2 && !indicators.uptrend) bearishSignals++;
        
        // Support/Resistance
        if (indicators.support && !indicators.resistance) bullishSignals++;
        else if (indicators.resistance && !indicators.support) bearishSignals++;
        
        // Determine final bias
        if (bullishSignals > bearishSignals && bullishSignals >= 2) {
            return MarketBias.BULLISH;
        } else if (bearishSignals > bullishSignals && bearishSignals >= 2) {
            return MarketBias.BEARISH;
        } else {
            return MarketBias.NEUTRAL;
        }
    }
    
    /**
     * Calculate confidence based on signal strength
     */
    private double calculateConfidence(TechnicalIndicators indicators, MarketBias bias) {
        if (bias == MarketBias.NEUTRAL) return 0.5;
        
        double baseConfidence = 0.65;
        
        // Volume confirmation
        if (indicators.volume > 1.3) baseConfidence += 0.1;
        
        // Volatility adjustment (moderate volatility is good)
        if (indicators.volatility > 0.2 && indicators.volatility < 0.35) {
            baseConfidence += 0.08;
        }
        
        // Trend confirmation
        boolean trendAligned = (bias == MarketBias.BULLISH && indicators.uptrend) ||
                              (bias == MarketBias.BEARISH && !indicators.uptrend);
        if (trendAligned) baseConfidence += 0.1;
        
        // Technical level confirmation
        if (indicators.support || indicators.resistance) baseConfidence += 0.05;
        
        return Math.min(0.95, baseConfidence);
    }
    
    /**
     * Generate rationale for the call
     */
    private String generateRationale(MarketAnalysis analysis) {
        StringBuilder rationale = new StringBuilder();
        
        rationale.append(String.format("%s bias detected. ", analysis.bias));
        
        if (analysis.indicators.volume > 1.2) {
            rationale.append("Strong volume confirmation. ");
        }
        
        if (analysis.indicators.rsi < 35) {
            rationale.append("Oversold RSI suggesting bounce. ");
        } else if (analysis.indicators.rsi > 65) {
            rationale.append("Overbought RSI suggesting correction. ");
        }
        
        if (Math.abs(analysis.indicators.macd) > 0.5) {
            rationale.append("MACD momentum signal. ");
        }
        
        if (analysis.indicators.support) {
            rationale.append("Key support level holding. ");
        } else if (analysis.indicators.resistance) {
            rationale.append("Key resistance level tested. ");
        }
        
        rationale.append(String.format("Confidence: %.0f%%", analysis.confidence * 100));
        
        return rationale.toString();
    }
    
    // Helper methods
    private double getCurrentPrice(String symbol) {
        return currentPrices.getOrDefault(symbol, 23450.0);
    }
    
    private String getNearestStrike(String symbol, double price, boolean isCall) {
        int strike = ((int) (price / 50)) * 50; // Round to nearest 50
        if (isCall) strike += 50; // Slightly OTM for calls
        return String.valueOf(strike);
    }
    
    private double calculateStopLoss(double entry, double target, MarketBias bias) {
        double risk = Math.abs(target - entry) * 0.5; // 50% of reward as risk
        return bias == MarketBias.BULLISH ? entry - risk : entry + risk;
    }
    
    private LocalDateTime getNextExpiry() {
        // Next Thursday weekly expiry
        LocalDateTime now = LocalDateTime.now();
        int daysToThursday = (11 - now.getDayOfWeek().getValue()) % 7;
        if (daysToThursday == 0 && now.getHour() > 15) daysToThursday = 7;
        return now.plusDays(daysToThursday).withHour(15).withMinute(30);
    }
    
    // Supporting classes
    private enum MarketBias {
        BULLISH, BEARISH, NEUTRAL
    }
    
    private static class TechnicalIndicators {
        final double rsi, macd, volume, volatility;
        final boolean uptrend, support, resistance;
        
        TechnicalIndicators(double rsi, double macd, double volume, double volatility,
                           boolean uptrend, boolean support, boolean resistance) {
            this.rsi = rsi;
            this.macd = macd;
            this.volume = volume;
            this.volatility = volatility;
            this.uptrend = uptrend;
            this.support = support;
            this.resistance = resistance;
        }
    }
    
    private static class MarketAnalysis {
        final String symbol;
        final double currentPrice;
        final TechnicalIndicators indicators;
        final MarketBias bias;
        final double confidence;
        
        MarketAnalysis(String symbol, double currentPrice, TechnicalIndicators indicators,
                      MarketBias bias, double confidence) {
            this.symbol = symbol;
            this.currentPrice = currentPrice;
            this.indicators = indicators;
            this.bias = bias;
            this.confidence = confidence;
        }
    }
    
    /**
     * Generate call for specific symbol with default strategy
     */
    public GeneratedCall generateCall(String symbol) {
        return generateCallWithMonitoring(symbol, "Technical Analysis");
    }
    
    /**
     * Generate multiple calls for different symbols
     */
    public List<GeneratedCall> generateMultipleCalls(String... symbols) {
        List<GeneratedCall> calls = new ArrayList<>();
        
        for (String symbol : symbols) {
            GeneratedCall call = generateCall(symbol);
            if (call != null) {
                calls.add(call);
            }
        }
        
        return calls;
    }
}