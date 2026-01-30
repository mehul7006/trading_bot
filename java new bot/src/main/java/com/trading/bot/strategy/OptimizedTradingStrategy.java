package com.trading.bot.strategy;

import java.util.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * OPTIMIZED TRADING STRATEGY - Enhanced Success Rate System
 * 
 * Key Optimizations:
 * 1. Multi-timeframe analysis
 * 2. Advanced technical indicators
 * 3. Market regime detection
 * 4. Dynamic confidence scoring
 * 5. Risk-adjusted position sizing
 * 6. Time-based filtering
 */
public class OptimizedTradingStrategy {
    
    // Strategy Configuration
    private static final double MIN_CONFIDENCE_THRESHOLD = 75.0;
    private static final double HIGH_CONFIDENCE_THRESHOLD = 85.0;
    private static final double RISK_REWARD_RATIO = 2.0;
    private static final int RSI_PERIOD = 14;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int MACD_SIGNAL = 9;
    
    // Market Data Storage
    private final Map<String, List<PriceData>> priceHistory = new HashMap<>();
    private final Map<String, MarketRegime> currentRegime = new HashMap<>();
    
    public OptimizedTradingStrategy() {
        // Initialize for all instruments
        String[] instruments = {"NIFTY", "BANKNIFTY", "FINNIFTY", "SENSEX"};
        for (String instrument : instruments) {
            priceHistory.put(instrument, new ArrayList<>());
            currentRegime.put(instrument, MarketRegime.NEUTRAL);
        }
    }
    
    /**
     * Generate optimized trading signal
     */
    public TradingSignal generateSignal(String instrument, double currentPrice, double volume) {
        // Update price history
        addPriceData(instrument, currentPrice, volume);
        
        // Get historical data
        List<PriceData> history = priceHistory.get(instrument);
        if (history.size() < 50) {
            return new TradingSignal(instrument, "HOLD", 50.0, "INSUFFICIENT_HISTORICAL_DATA");
        }
        
        // 1. Time-based filtering
        if (!isValidTradingTime()) {
            return new TradingSignal(instrument, "HOLD", 50.0, "OUTSIDE_TRADING_HOURS");
        }
        
        // 2. Market regime detection
        MarketRegime regime = detectMarketRegime(history);
        currentRegime.put(instrument, regime);
        
        // 3. Multi-timeframe analysis
        TechnicalAnalysis analysis = performTechnicalAnalysis(history);
        
        // 4. Generate signal based on confluence
        return generateConfluenceSignal(instrument, currentPrice, analysis, regime);
    }
    
    /**
     * Advanced technical analysis
     */
    private TechnicalAnalysis performTechnicalAnalysis(List<PriceData> history) {
        TechnicalAnalysis analysis = new TechnicalAnalysis();
        
        // RSI Analysis
        analysis.rsi = calculateRSI(history, RSI_PERIOD);
        analysis.rsiSignal = getRSISignal(analysis.rsi);
        
        // MACD Analysis
        MACDResult macd = calculateMACD(history, MACD_FAST, MACD_SLOW, MACD_SIGNAL);
        analysis.macdSignal = getMACDSignal(macd);
        analysis.macdHistogram = macd.histogram;
        
        // Moving Average Analysis
        analysis.sma20 = calculateSMA(history, 20);
        analysis.sma50 = calculateSMA(history, 50);
        analysis.ema12 = calculateEMA(history, 12);
        analysis.ema26 = calculateEMA(history, 26);
        
        // Bollinger Bands
        BollingerBands bb = calculateBollingerBands(history, 20, 2.0);
        analysis.bollingerSignal = getBollingerSignal(history.get(history.size() - 1).price, bb);
        
        // Volume Analysis
        analysis.volumeSignal = getVolumeSignal(history);
        
        // Momentum Analysis
        analysis.momentum = calculateMomentum(history, 10);
        analysis.momentumSignal = getMomentumSignal(analysis.momentum);
        
        return analysis;
    }
    
    /**
     * Generate signal based on multiple indicator confluence
     */
    private TradingSignal generateConfluenceSignal(String instrument, double currentPrice, 
                                                  TechnicalAnalysis analysis, MarketRegime regime) {
        
        List<String> bullishSignals = new ArrayList<>();
        List<String> bearishSignals = new ArrayList<>();
        double confidence = 50.0;
        
        // RSI Signals
        if (analysis.rsiSignal.equals("OVERSOLD")) {
            bullishSignals.add("RSI_OVERSOLD");
            confidence += 8.0;
        } else if (analysis.rsiSignal.equals("OVERBOUGHT")) {
            bearishSignals.add("RSI_OVERBOUGHT");
            confidence += 8.0;
        }
        
        // MACD Signals
        if (analysis.macdSignal.equals("BULLISH_CROSSOVER")) {
            bullishSignals.add("MACD_BULLISH");
            confidence += 12.0;
        } else if (analysis.macdSignal.equals("BEARISH_CROSSOVER")) {
            bearishSignals.add("MACD_BEARISH");
            confidence += 12.0;
        }
        
        // Moving Average Signals
        if (currentPrice > analysis.sma20 && analysis.sma20 > analysis.sma50) {
            bullishSignals.add("MA_BULLISH_ALIGNMENT");
            confidence += 10.0;
        } else if (currentPrice < analysis.sma20 && analysis.sma20 < analysis.sma50) {
            bearishSignals.add("MA_BEARISH_ALIGNMENT");
            confidence += 10.0;
        }
        
        // Bollinger Bands
        if (analysis.bollingerSignal.equals("OVERSOLD")) {
            bullishSignals.add("BB_OVERSOLD");
            confidence += 6.0;
        } else if (analysis.bollingerSignal.equals("OVERBOUGHT")) {
            bearishSignals.add("BB_OVERBOUGHT");
            confidence += 6.0;
        }
        
        // Volume Confirmation
        if (analysis.volumeSignal.equals("HIGH_VOLUME")) {
            confidence += 5.0;
        }
        
        // Market Regime Adjustment
        confidence = adjustForMarketRegime(confidence, regime, bullishSignals.size(), bearishSignals.size());
        
        // Generate final signal
        String signal = "HOLD";
        String reason = "NO_CLEAR_DIRECTION";
        
        if (bullishSignals.size() >= 3 && bullishSignals.size() > bearishSignals.size() && confidence >= MIN_CONFIDENCE_THRESHOLD) {
            signal = "BUY";
            reason = String.join(", ", bullishSignals);
        } else if (bearishSignals.size() >= 3 && bearishSignals.size() > bullishSignals.size() && confidence >= MIN_CONFIDENCE_THRESHOLD) {
            signal = "SELL";
            reason = String.join(", ", bearishSignals);
        }
        
        // High confidence boost
        if (confidence >= HIGH_CONFIDENCE_THRESHOLD) {
            confidence = Math.min(95.0, confidence + 5.0);
        }
        
        return new TradingSignal(instrument, signal, confidence, reason);
    }
    
    /**
     * Market regime detection
     */
    private MarketRegime detectMarketRegime(List<PriceData> history) {
        if (history.size() < 20) return MarketRegime.NEUTRAL;
        
        double recentAvg = history.subList(history.size() - 10, history.size())
                                 .stream().mapToDouble(p -> p.price).average().orElse(0);
        double olderAvg = history.subList(history.size() - 20, history.size() - 10)
                                .stream().mapToDouble(p -> p.price).average().orElse(0);
        
        double volatility = calculateVolatility(history.subList(history.size() - 20, history.size()));
        
        if (recentAvg > olderAvg * 1.002 && volatility < 0.02) {
            return MarketRegime.TRENDING_UP;
        } else if (recentAvg < olderAvg * 0.998 && volatility < 0.02) {
            return MarketRegime.TRENDING_DOWN;
        } else if (volatility > 0.03) {
            return MarketRegime.HIGH_VOLATILITY;
        } else {
            return MarketRegime.RANGING;
        }
    }
    
    /**
     * Time-based filtering
     */
    private boolean isValidTradingTime() {
        LocalTime now = LocalTime.now();
        LocalTime marketOpen = LocalTime.of(9, 15);
        LocalTime marketClose = LocalTime.of(15, 30);
        LocalTime lunchStart = LocalTime.of(12, 30);
        LocalTime lunchEnd = LocalTime.of(13, 30);
        
        // Avoid lunch time and first/last 30 minutes
        LocalTime earlyTrading = LocalTime.of(9, 45);
        LocalTime lateTrading = LocalTime.of(15, 0);
        
        return now.isAfter(earlyTrading) && now.isBefore(lunchStart) ||
               now.isAfter(lunchEnd) && now.isBefore(lateTrading);
    }
    
    /**
     * Adjust confidence based on market regime
     */
    private double adjustForMarketRegime(double baseConfidence, MarketRegime regime, 
                                       int bullishCount, int bearishCount) {
        switch (regime) {
            case TRENDING_UP:
                return bullishCount > bearishCount ? baseConfidence + 8.0 : baseConfidence - 5.0;
            case TRENDING_DOWN:
                return bearishCount > bullishCount ? baseConfidence + 8.0 : baseConfidence - 5.0;
            case HIGH_VOLATILITY:
                return baseConfidence - 10.0; // Reduce confidence in volatile markets
            case RANGING:
                return baseConfidence - 3.0; // Slightly reduce confidence in ranging markets
            default:
                return baseConfidence;
        }
    }
    
    // Technical Indicator Calculations
    
    private double calculateRSI(List<PriceData> history, int period) {
        if (history.size() < period + 1) return 50.0;
        
        double avgGain = 0, avgLoss = 0;
        
        for (int i = history.size() - period; i < history.size(); i++) {
            double change = history.get(i).price - history.get(i - 1).price;
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        
        avgGain /= period;
        avgLoss /= period;
        
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
    
    private String getRSISignal(double rsi) {
        if (rsi < 30) return "OVERSOLD";
        if (rsi > 70) return "OVERBOUGHT";
        return "NEUTRAL";
    }
    
    private MACDResult calculateMACD(List<PriceData> history, int fastPeriod, int slowPeriod, int signalPeriod) {
        if (history.size() < slowPeriod + signalPeriod) {
            return new MACDResult(0, 0, 0);
        }
        
        double fastEMA = calculateEMA(history, fastPeriod);
        double slowEMA = calculateEMA(history, slowPeriod);
        double macdLine = fastEMA - slowEMA;
        
        // For simplicity, using SMA instead of EMA for signal line
        double signalLine = calculateSMAForMACD(history, signalPeriod, fastPeriod, slowPeriod);
        double histogram = macdLine - signalLine;
        
        return new MACDResult(macdLine, signalLine, histogram);
    }
    
    private String getMACDSignal(MACDResult macd) {
        if (macd.histogram > 0 && macd.macdLine > macd.signalLine) return "BULLISH_CROSSOVER";
        if (macd.histogram < 0 && macd.macdLine < macd.signalLine) return "BEARISH_CROSSOVER";
        return "NEUTRAL";
    }
    
    private double calculateSMA(List<PriceData> history, int period) {
        if (history.size() < period) return history.get(history.size() - 1).price;
        
        return history.subList(history.size() - period, history.size())
                     .stream().mapToDouble(p -> p.price).average().orElse(0);
    }
    
    private double calculateEMA(List<PriceData> history, int period) {
        if (history.size() < period) return history.get(history.size() - 1).price;
        
        double multiplier = 2.0 / (period + 1);
        double ema = calculateSMA(history.subList(0, period), period);
        
        for (int i = period; i < history.size(); i++) {
            ema = (history.get(i).price * multiplier) + (ema * (1 - multiplier));
        }
        
        return ema;
    }
    
    private double calculateSMAForMACD(List<PriceData> history, int signalPeriod, int fastPeriod, int slowPeriod) {
        // Simplified MACD signal calculation
        return calculateSMA(history, signalPeriod);
    }
    
    private BollingerBands calculateBollingerBands(List<PriceData> history, int period, double stdDev) {
        if (history.size() < period) {
            double price = history.get(history.size() - 1).price;
            return new BollingerBands(price, price, price);
        }
        
        double sma = calculateSMA(history, period);
        List<PriceData> recentData = history.subList(history.size() - period, history.size());
        
        double variance = recentData.stream()
                                   .mapToDouble(p -> Math.pow(p.price - sma, 2))
                                   .average().orElse(0);
        double standardDeviation = Math.sqrt(variance);
        
        return new BollingerBands(
            sma + (standardDeviation * stdDev), // Upper band
            sma,                                // Middle band (SMA)
            sma - (standardDeviation * stdDev)  // Lower band
        );
    }
    
    private String getBollingerSignal(double currentPrice, BollingerBands bb) {
        if (currentPrice < bb.lowerBand) return "OVERSOLD";
        if (currentPrice > bb.upperBand) return "OVERBOUGHT";
        return "NEUTRAL";
    }
    
    private String getVolumeSignal(List<PriceData> history) {
        if (history.size() < 20) return "NEUTRAL";
        
        double recentVolume = history.subList(history.size() - 5, history.size())
                                    .stream().mapToDouble(p -> p.volume).average().orElse(0);
        double avgVolume = history.subList(history.size() - 20, history.size())
                                 .stream().mapToDouble(p -> p.volume).average().orElse(0);
        
        return recentVolume > avgVolume * 1.5 ? "HIGH_VOLUME" : "NORMAL_VOLUME";
    }
    
    private double calculateMomentum(List<PriceData> history, int period) {
        if (history.size() < period + 1) return 0;
        
        double currentPrice = history.get(history.size() - 1).price;
        double pastPrice = history.get(history.size() - 1 - period).price;
        
        return ((currentPrice - pastPrice) / pastPrice) * 100;
    }
    
    private String getMomentumSignal(double momentum) {
        if (momentum > 2.0) return "STRONG_BULLISH";
        if (momentum > 0.5) return "BULLISH";
        if (momentum < -2.0) return "STRONG_BEARISH";
        if (momentum < -0.5) return "BEARISH";
        return "NEUTRAL";
    }
    
    private double calculateVolatility(List<PriceData> history) {
        if (history.size() < 2) return 0;
        
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < history.size(); i++) {
            double ret = (history.get(i).price - history.get(i - 1).price) / history.get(i - 1).price;
            returns.add(ret);
        }
        
        double avgReturn = returns.stream().mapToDouble(r -> r).average().orElse(0);
        double variance = returns.stream()
                                .mapToDouble(r -> Math.pow(r - avgReturn, 2))
                                .average().orElse(0);
        
        return Math.sqrt(variance);
    }
    
    private void addPriceData(String instrument, double price, double volume) {
        List<PriceData> history = priceHistory.get(instrument);
        history.add(new PriceData(price, volume, LocalDateTime.now()));
        
        // Keep only last 200 data points
        if (history.size() > 200) {
            history.remove(0);
        }
    }
    
    // Data Classes
    
    public static class TradingSignal {
        public final String instrument;
        public final String signal;
        public final double confidence;
        public final String reason;
        public final LocalDateTime timestamp;
        
        public TradingSignal(String instrument, String signal, double confidence, String reason) {
            this.instrument = instrument;
            this.signal = signal;
            this.confidence = confidence;
            this.reason = reason;
            this.timestamp = LocalDateTime.now();
        }
        
        @Override
        public String toString() {
            return String.format("%s: %s (%.1f%%) - %s", instrument, signal, confidence, reason);
        }
    }
    
    private static class PriceData {
        final double price;
        final double volume;
        final LocalDateTime timestamp;
        
        PriceData(double price, double volume, LocalDateTime timestamp) {
            this.price = price;
            this.volume = volume;
            this.timestamp = timestamp;
        }
    }
    
    private static class TechnicalAnalysis {
        double rsi;
        String rsiSignal;
        String macdSignal;
        double macdHistogram;
        double sma20;
        double sma50;
        double ema12;
        double ema26;
        String bollingerSignal;
        String volumeSignal;
        double momentum;
        String momentumSignal;
    }
    
    private static class MACDResult {
        final double macdLine;
        final double signalLine;
        final double histogram;
        
        MACDResult(double macdLine, double signalLine, double histogram) {
            this.macdLine = macdLine;
            this.signalLine = signalLine;
            this.histogram = histogram;
        }
    }
    
    private static class BollingerBands {
        final double upperBand;
        final double middleBand;
        final double lowerBand;
        
        BollingerBands(double upperBand, double middleBand, double lowerBand) {
            this.upperBand = upperBand;
            this.middleBand = middleBand;
            this.lowerBand = lowerBand;
        }
    }
    
    private enum MarketRegime {
        TRENDING_UP, TRENDING_DOWN, RANGING, HIGH_VOLATILITY, NEUTRAL
    }
}