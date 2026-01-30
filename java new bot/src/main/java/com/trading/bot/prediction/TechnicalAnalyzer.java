package com.trading.bot.prediction;

import java.util.*;
import java.time.LocalDateTime;

/**
 * TECHNICAL ANALYSIS ENGINE FOR INDEX MOVEMENT PREDICTION
 * Implements RSI, MACD, Bollinger Bands, Volume Analysis, Support/Resistance
 */
public class TechnicalAnalyzer {
    
    /**
     * TECHNICAL INDICATORS RESULT
     */
    public static class TechnicalResult {
        public final double rsi14;
        public final double rsi21;
        public final MACDResult macd;
        public final BollingerResult bollinger;
        public final VolumeResult volume;
        public final SupportResistanceResult sr;
        public final double overallScore; // 0.0 to 1.0
        public final String signal; // BULLISH, BEARISH, NEUTRAL
        public final String reasoning;
        
        public TechnicalResult(double rsi14, double rsi21, MACDResult macd, BollingerResult bollinger,
                             VolumeResult volume, SupportResistanceResult sr, double overallScore, 
                             String signal, String reasoning) {
            this.rsi14 = rsi14;
            this.rsi21 = rsi21;
            this.macd = macd;
            this.bollinger = bollinger;
            this.volume = volume;
            this.sr = sr;
            this.overallScore = overallScore;
            this.signal = signal;
            this.reasoning = reasoning;
        }
    }
    
    /**
     * MACD ANALYSIS RESULT
     */
    public static class MACDResult {
        public final double macdLine;
        public final double signalLine;
        public final double histogram;
        public final String crossover; // BULLISH_CROSS, BEARISH_CROSS, NO_CROSS
        public final double momentum; // -1.0 to 1.0
        
        public MACDResult(double macdLine, double signalLine, double histogram, String crossover, double momentum) {
            this.macdLine = macdLine;
            this.signalLine = signalLine;
            this.histogram = histogram;
            this.crossover = crossover;
            this.momentum = momentum;
        }
    }
    
    /**
     * BOLLINGER BANDS RESULT
     */
    public static class BollingerResult {
        public final double upperBand;
        public final double middleBand;
        public final double lowerBand;
        public final double position; // 0.0 to 1.0 (position within bands)
        public final double squeeze; // Band width relative to average
        public final String signal; // BREAKOUT_UP, BREAKOUT_DOWN, SQUEEZE, NORMAL
        
        public BollingerResult(double upperBand, double middleBand, double lowerBand, 
                             double position, double squeeze, String signal) {
            this.upperBand = upperBand;
            this.middleBand = middleBand;
            this.lowerBand = lowerBand;
            this.position = position;
            this.squeeze = squeeze;
            this.signal = signal;
        }
    }
    
    /**
     * VOLUME ANALYSIS RESULT
     */
    public static class VolumeResult {
        public final long currentVolume;
        public final long averageVolume;
        public final double volumeRatio; // Current / Average
        public final boolean volumeSpike; // > 2.5x average
        public final String trend; // INCREASING, DECREASING, STABLE
        
        public VolumeResult(long currentVolume, long averageVolume, double volumeRatio, 
                          boolean volumeSpike, String trend) {
            this.currentVolume = currentVolume;
            this.averageVolume = averageVolume;
            this.volumeRatio = volumeRatio;
            this.volumeSpike = volumeSpike;
            this.trend = trend;
        }
    }
    
    /**
     * SUPPORT/RESISTANCE RESULT
     */
    public static class SupportResistanceResult {
        public final double nearestSupport;
        public final double nearestResistance;
        public final double supportStrength; // 0.0 to 1.0
        public final double resistanceStrength; // 0.0 to 1.0
        public final String position; // NEAR_SUPPORT, NEAR_RESISTANCE, BETWEEN
        
        public SupportResistanceResult(double nearestSupport, double nearestResistance,
                                     double supportStrength, double resistanceStrength, String position) {
            this.nearestSupport = nearestSupport;
            this.nearestResistance = nearestResistance;
            this.supportStrength = supportStrength;
            this.resistanceStrength = resistanceStrength;
            this.position = position;
        }
    }
    
    /**
     * ANALYZE TECHNICAL INDICATORS FOR INDEX
     */
    public TechnicalResult analyzeTechnicals(String index, List<IndexMovementPredictor.RealMarketData> priceHistory) {
        if (priceHistory.size() < 30) {
            // Not enough data for reliable analysis
            return new TechnicalResult(50, 50, 
                new MACDResult(0, 0, 0, "NO_CROSS", 0), 
                new BollingerResult(0, 0, 0, 0.5, 1, "NORMAL"),
                new VolumeResult(1000000, 1000000, 1.0, false, "STABLE"),
                new SupportResistanceResult(0, 0, 0.5, 0.5, "BETWEEN"),
                0.5, "NEUTRAL", "Insufficient data for analysis");
        }
        
        System.out.println("🔬 Analyzing " + index + " technical indicators...");
        
        // Extract price arrays
        double[] prices = priceHistory.stream().mapToDouble(d -> d.price).toArray();
        long[] volumes = priceHistory.stream().mapToLong(d -> d.volume).toArray();
        
        // Calculate RSI (14 and 21 periods)
        double rsi14 = calculateRSI(prices, 14);
        double rsi21 = calculateRSI(prices, 21);
        
        // Calculate MACD
        MACDResult macd = calculateMACD(prices);
        
        // Calculate Bollinger Bands
        BollingerResult bollinger = calculateBollingerBands(prices, 20, 2.0);
        
        // Analyze Volume
        VolumeResult volume = analyzeVolume(volumes);
        
        // Calculate Support/Resistance
        SupportResistanceResult sr = calculateSupportResistance(prices, prices[prices.length - 1]);
        
        // Calculate overall technical score
        double overallScore = calculateTechnicalScore(rsi14, rsi21, macd, bollinger, volume, sr);
        
        // Determine signal and reasoning
        String signal = determineTechnicalSignal(overallScore, rsi14, macd, bollinger);
        String reasoning = generateTechnicalReasoning(rsi14, rsi21, macd, bollinger, volume, sr, signal);
        
        System.out.println("   📊 RSI(14): " + String.format("%.1f", rsi14));
        System.out.println("   📊 MACD: " + macd.crossover);
        System.out.println("   📊 Bollinger: " + bollinger.signal);
        System.out.println("   📊 Volume Spike: " + volume.volumeSpike);
        System.out.println("   📊 Overall Score: " + String.format("%.1f%%", overallScore * 100));
        System.out.println("   🎯 Signal: " + signal);
        
        return new TechnicalResult(rsi14, rsi21, macd, bollinger, volume, sr, overallScore, signal, reasoning);
    }
    
    /**
     * CALCULATE RSI (RELATIVE STRENGTH INDEX)
     */
    private double calculateRSI(double[] prices, int period) {
        if (prices.length < period + 1) return 50.0;
        
        double avgGain = 0.0;
        double avgLoss = 0.0;
        
        // Calculate initial gains and losses
        for (int i = 1; i <= period; i++) {
            double change = prices[i] - prices[i - 1];
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss += Math.abs(change);
            }
        }
        
        avgGain /= period;
        avgLoss /= period;
        
        // Calculate RSI for remaining periods
        for (int i = period + 1; i < prices.length; i++) {
            double change = prices[i] - prices[i - 1];
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? Math.abs(change) : 0;
            
            avgGain = ((avgGain * (period - 1)) + gain) / period;
            avgLoss = ((avgLoss * (period - 1)) + loss) / period;
        }
        
        if (avgLoss == 0) return 100.0;
        
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
    
    /**
     * CALCULATE MACD (MOVING AVERAGE CONVERGENCE DIVERGENCE)
     */
    private MACDResult calculateMACD(double[] prices) {
        if (prices.length < 26) {
            return new MACDResult(0, 0, 0, "NO_CROSS", 0);
        }
        
        // Calculate EMAs
        double[] ema12 = calculateEMA(prices, 12);
        double[] ema26 = calculateEMA(prices, 26);
        
        // MACD Line = EMA12 - EMA26
        double[] macdLine = new double[prices.length];
        for (int i = 25; i < prices.length; i++) {
            macdLine[i] = ema12[i] - ema26[i];
        }
        
        // Signal Line = EMA9 of MACD
        double[] signalLine = calculateEMA(Arrays.copyOfRange(macdLine, 25, macdLine.length), 9);
        
        int lastIndex = prices.length - 1;
        double currentMACD = macdLine[lastIndex];
        double currentSignal = signalLine[signalLine.length - 1];
        double histogram = currentMACD - currentSignal;
        
        // Determine crossover
        String crossover = "NO_CROSS";
        if (prices.length > 26) {
            double prevMACD = macdLine[lastIndex - 1];
            double prevSignal = signalLine[signalLine.length - 2];
            
            if (prevMACD <= prevSignal && currentMACD > currentSignal) {
                crossover = "BULLISH_CROSS";
            } else if (prevMACD >= prevSignal && currentMACD < currentSignal) {
                crossover = "BEARISH_CROSS";
            }
        }
        
        // Calculate momentum (-1.0 to 1.0)
        double momentum = Math.tanh(histogram / 10.0);
        
        return new MACDResult(currentMACD, currentSignal, histogram, crossover, momentum);
    }
    
    /**
     * CALCULATE EMA (EXPONENTIAL MOVING AVERAGE)
     */
    private double[] calculateEMA(double[] prices, int period) {
        double[] ema = new double[prices.length];
        double multiplier = 2.0 / (period + 1);
        
        // First EMA is simple moving average
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += prices[i];
        }
        ema[period - 1] = sum / period;
        
        // Calculate remaining EMAs
        for (int i = period; i < prices.length; i++) {
            ema[i] = (prices[i] * multiplier) + (ema[i - 1] * (1 - multiplier));
        }
        
        return ema;
    }
    
    /**
     * CALCULATE BOLLINGER BANDS
     */
    private BollingerResult calculateBollingerBands(double[] prices, int period, double stdDev) {
        if (prices.length < period) {
            return new BollingerResult(0, 0, 0, 0.5, 1.0, "NORMAL");
        }
        
        // Calculate Simple Moving Average
        double sum = 0;
        for (int i = prices.length - period; i < prices.length; i++) {
            sum += prices[i];
        }
        double sma = sum / period;
        
        // Calculate Standard Deviation
        double variance = 0;
        for (int i = prices.length - period; i < prices.length; i++) {
            variance += Math.pow(prices[i] - sma, 2);
        }
        double sd = Math.sqrt(variance / period);
        
        double upperBand = sma + (stdDev * sd);
        double lowerBand = sma - (stdDev * sd);
        double currentPrice = prices[prices.length - 1];
        
        // Position within bands (0.0 = lower band, 1.0 = upper band)
        double position = (currentPrice - lowerBand) / (upperBand - lowerBand);
        position = Math.max(0, Math.min(1, position));
        
        // Band squeeze (current width / average width)
        double bandWidth = upperBand - lowerBand;
        double avgBandWidth = sma * 0.15; // Typical band width ~15% of price
        double squeeze = bandWidth / avgBandWidth;
        
        // Determine signal
        String signal = "NORMAL";
        if (currentPrice > upperBand) {
            signal = "BREAKOUT_UP";
        } else if (currentPrice < lowerBand) {
            signal = "BREAKOUT_DOWN";
        } else if (squeeze < 0.5) {
            signal = "SQUEEZE";
        }
        
        return new BollingerResult(upperBand, sma, lowerBand, position, squeeze, signal);
    }
    
    /**
     * ANALYZE VOLUME PATTERNS
     */
    private VolumeResult analyzeVolume(long[] volumes) {
        if (volumes.length < 10) {
            return new VolumeResult(1000000, 1000000, 1.0, false, "STABLE");
        }
        
        long currentVolume = volumes[volumes.length - 1];
        
        // Calculate average volume (last 20 periods)
        int lookback = Math.min(20, volumes.length - 1);
        long sum = 0;
        for (int i = volumes.length - 1 - lookback; i < volumes.length - 1; i++) {
            sum += volumes[i];
        }
        long averageVolume = sum / lookback;
        
        double volumeRatio = (double) currentVolume / averageVolume;
        boolean volumeSpike = volumeRatio > 2.5;
        
        // Determine volume trend
        String trend = "STABLE";
        if (volumes.length > 5) {
            long recent5Avg = 0;
            long previous5Avg = 0;
            
            for (int i = volumes.length - 5; i < volumes.length; i++) {
                recent5Avg += volumes[i];
            }
            recent5Avg /= 5;
            
            for (int i = volumes.length - 10; i < volumes.length - 5; i++) {
                previous5Avg += volumes[i];
            }
            previous5Avg /= 5;
            
            if (recent5Avg > previous5Avg * 1.2) {
                trend = "INCREASING";
            } else if (recent5Avg < previous5Avg * 0.8) {
                trend = "DECREASING";
            }
        }
        
        return new VolumeResult(currentVolume, averageVolume, volumeRatio, volumeSpike, trend);
    }
    
    /**
     * CALCULATE SUPPORT AND RESISTANCE LEVELS
     */
    private SupportResistanceResult calculateSupportResistance(double[] prices, double currentPrice) {
        if (prices.length < 20) {
            return new SupportResistanceResult(currentPrice * 0.98, currentPrice * 1.02, 0.5, 0.5, "BETWEEN");
        }
        
        // Find recent swing highs and lows
        List<Double> swingHighs = new ArrayList<>();
        List<Double> swingLows = new ArrayList<>();
        
        for (int i = 2; i < prices.length - 2; i++) {
            // Swing high: higher than 2 periods before and after
            if (prices[i] > prices[i-1] && prices[i] > prices[i-2] && 
                prices[i] > prices[i+1] && prices[i] > prices[i+2]) {
                swingHighs.add(prices[i]);
            }
            
            // Swing low: lower than 2 periods before and after
            if (prices[i] < prices[i-1] && prices[i] < prices[i-2] && 
                prices[i] < prices[i+1] && prices[i] < prices[i+2]) {
                swingLows.add(prices[i]);
            }
        }
        
        // Find nearest support (highest swing low below current price)
        double nearestSupport = currentPrice * 0.95; // Default 5% below
        double supportStrength = 0.3;
        
        for (double low : swingLows) {
            if (low < currentPrice && low > nearestSupport) {
                nearestSupport = low;
                supportStrength = 0.7; // Strong support found
            }
        }
        
        // Find nearest resistance (lowest swing high above current price)
        double nearestResistance = currentPrice * 1.05; // Default 5% above
        double resistanceStrength = 0.3;
        
        for (double high : swingHighs) {
            if (high > currentPrice && high < nearestResistance) {
                nearestResistance = high;
                resistanceStrength = 0.7; // Strong resistance found
            }
        }
        
        // Determine position
        String position = "BETWEEN";
        double supportDistance = currentPrice - nearestSupport;
        double resistanceDistance = nearestResistance - currentPrice;
        
        if (supportDistance < currentPrice * 0.01) { // Within 1% of support
            position = "NEAR_SUPPORT";
        } else if (resistanceDistance < currentPrice * 0.01) { // Within 1% of resistance
            position = "NEAR_RESISTANCE";
        }
        
        return new SupportResistanceResult(nearestSupport, nearestResistance, 
                                         supportStrength, resistanceStrength, position);
    }
    
    /**
     * CALCULATE OVERALL TECHNICAL SCORE
     */
    private double calculateTechnicalScore(double rsi14, double rsi21, MACDResult macd, 
                                         BollingerResult bollinger, VolumeResult volume, 
                                         SupportResistanceResult sr) {
        double score = 0.0;
        
        // RSI scoring (weight: 25%)
        double rsiScore = 0.0;
        if (rsi14 < 30) rsiScore = 0.8; // Oversold - bullish
        else if (rsi14 > 70) rsiScore = 0.2; // Overbought - bearish
        else rsiScore = 0.5; // Neutral
        score += rsiScore * 0.25;
        
        // MACD scoring (weight: 30%)
        double macdScore = 0.5;
        if (macd.crossover.equals("BULLISH_CROSS")) macdScore = 0.9;
        else if (macd.crossover.equals("BEARISH_CROSS")) macdScore = 0.1;
        else if (macd.momentum > 0) macdScore = 0.7;
        else if (macd.momentum < 0) macdScore = 0.3;
        score += macdScore * 0.30;
        
        // Bollinger Bands scoring (weight: 20%)
        double bollingerScore = 0.5;
        if (bollinger.signal.equals("BREAKOUT_UP")) bollingerScore = 0.8;
        else if (bollinger.signal.equals("BREAKOUT_DOWN")) bollingerScore = 0.2;
        else if (bollinger.signal.equals("SQUEEZE")) bollingerScore = 0.7; // Breakout potential
        score += bollingerScore * 0.20;
        
        // Volume scoring (weight: 15%)
        double volumeScore = 0.5;
        if (volume.volumeSpike && volume.trend.equals("INCREASING")) volumeScore = 0.9;
        else if (volume.volumeSpike) volumeScore = 0.7;
        else if (volume.trend.equals("INCREASING")) volumeScore = 0.6;
        score += volumeScore * 0.15;
        
        // Support/Resistance scoring (weight: 10%)
        double srScore = 0.5;
        if (sr.position.equals("NEAR_SUPPORT") && sr.supportStrength > 0.6) srScore = 0.8;
        else if (sr.position.equals("NEAR_RESISTANCE") && sr.resistanceStrength > 0.6) srScore = 0.2;
        score += srScore * 0.10;
        
        return Math.max(0.0, Math.min(1.0, score));
    }
    
    /**
     * DETERMINE TECHNICAL SIGNAL
     */
    private String determineTechnicalSignal(double overallScore, double rsi14, MACDResult macd, BollingerResult bollinger) {
        if (overallScore > 0.75) {
            return "BULLISH";
        } else if (overallScore < 0.25) {
            return "BEARISH";
        } else {
            return "NEUTRAL";
        }
    }
    
    /**
     * GENERATE TECHNICAL REASONING
     */
    private String generateTechnicalReasoning(double rsi14, double rsi21, MACDResult macd, 
                                            BollingerResult bollinger, VolumeResult volume, 
                                            SupportResistanceResult sr, String signal) {
        StringBuilder reasoning = new StringBuilder();
        
        reasoning.append("📊 Technical Analysis: ");
        
        if (signal.equals("BULLISH")) {
            reasoning.append("Strong bullish signals detected\n");
            if (rsi14 < 35) reasoning.append("• RSI oversold bounce expected\n");
            if (macd.crossover.equals("BULLISH_CROSS")) reasoning.append("• MACD bullish crossover confirmed\n");
            if (bollinger.signal.equals("BREAKOUT_UP")) reasoning.append("• Bollinger breakout upward\n");
            if (volume.volumeSpike) reasoning.append("• Volume surge supporting move\n");
        } else if (signal.equals("BEARISH")) {
            reasoning.append("Strong bearish signals detected\n");
            if (rsi14 > 65) reasoning.append("• RSI overbought correction likely\n");
            if (macd.crossover.equals("BEARISH_CROSS")) reasoning.append("• MACD bearish crossover confirmed\n");
            if (bollinger.signal.equals("BREAKOUT_DOWN")) reasoning.append("• Bollinger breakdown\n");
            if (volume.volumeSpike) reasoning.append("• Volume spike on selling\n");
        } else {
            reasoning.append("Mixed signals, neutral outlook\n");
            reasoning.append("• RSI: ").append(String.format("%.1f", rsi14)).append(" (neutral zone)\n");
            reasoning.append("• MACD: ").append(macd.crossover.toLowerCase()).append("\n");
        }
        
        return reasoning.toString();
    }
}