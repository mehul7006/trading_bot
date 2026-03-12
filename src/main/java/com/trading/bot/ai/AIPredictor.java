package com.trading.bot.ai;

import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.market.OptionData;
import com.trading.bot.technical.AdvancedIndicatorsEngine;
import com.trading.bot.technical.AdvancedCandlestickDetector;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashMap;

/**
 * Real Strategy Predictor - Uses honest SMA Crossover strategy on REAL data.
 * No fake neural networks or random number generation.
 */
public class AIPredictor {
    
    private boolean isInitialized = false;
    
    public static class AIPrediction {
        public final String predictedDirection;
        public final double confidence;
        public final double predictionAccuracy;
        public final double neuralNetworkScore; // Trend Strength
        public final double marketRegimePrediction; // RSI
        public final double volatilityForecast;
        public final double liquidityPrediction;
        public final String aiModel;
        public final String predictionReasoning;
        public final double estimatedMovePoints; // Target points
        public final double suggestedStopLoss;
        public final boolean isBreakout; // Early warning signal
        
        // Option Metrics
        public double pcr = 1.0;
        public double callOIChange = 0;
        public double putOIChange = 0;
        public Map<String, Double> greeks = null;

        public AIPrediction(String predictedDirection, double confidence, double predictionAccuracy,
                           double neuralNetworkScore, double marketRegimePrediction, double volatilityForecast,
                           double liquidityPrediction, String aiModel, String predictionReasoning,
                           double estimatedMovePoints, double suggestedStopLoss, boolean isBreakout) {
            this.predictedDirection = predictedDirection;
            this.confidence = confidence;
            this.predictionAccuracy = predictionAccuracy;
            this.neuralNetworkScore = neuralNetworkScore;
            this.marketRegimePrediction = marketRegimePrediction;
            this.volatilityForecast = volatilityForecast;
            this.liquidityPrediction = liquidityPrediction;
            this.aiModel = aiModel;
            this.predictionReasoning = predictionReasoning;
            this.estimatedMovePoints = estimatedMovePoints;
            this.suggestedStopLoss = suggestedStopLoss;
            this.isBreakout = isBreakout;
        }

        public void setOptionMetrics(double pcr, double callOIChange, double putOIChange, Map<String, Double> greeks) {
            this.pcr = pcr;
            this.callOIChange = callOIChange;
            this.putOIChange = putOIChange;
            this.greeks = greeks;
        }
    }
    
    public AIPredictor() {
    }
    
    public void initialize() {
        this.isInitialized = true;
        System.out.println("✅ Real Strategy Predictor initialized (Segment-Specific Optimization Mode)");
    }
    
    public AIPrediction generatePrediction(String symbol, List<SimpleMarketData> data) {
        return generatePrediction(symbol, data, null);
    }
    
    public AIPrediction generatePrediction(String symbol, List<SimpleMarketData> data, OptionData optionData) {
        if (data.size() < 200) return new AIPrediction("NEUTRAL", 0, 0, 0, 0, 0, 0, "MODEL_1", "Insufficient data", 0, 0, false);

        SimpleMarketData latest = data.get(data.size() - 1);
        double currentPrice = latest.price;

        // Common metrics used across strategies
        double ema20 = calculateEMA(data, 20);
        double ema50 = calculateEMA(data, 50);
        double ema200 = calculateEMA(data, 200);
        
        AdvancedIndicatorsEngine.AdvancedIndicatorsResult indicators = new AdvancedIndicatorsEngine().analyze50Plus(data);
        double rsi = indicators.values.getOrDefault("rsi14", 50.0);
        double adx = indicators.values.getOrDefault("adx", 25.0);
        double atr = calculateATR(data, 14);
        double avgVol = data.stream().skip(Math.max(0, data.size() - 20)).mapToLong(d -> d.volume).average().orElse(0);

        // SYMBOL-SPECIFIC STRATEGY ROUTING (V19.8 - Live Market Optimization)
        if ("NIFTY50".equals(symbol)) {
            return predictNiftyStrategy(symbol, data, currentPrice, ema50, rsi, adx, atr, latest, avgVol, optionData);
        } else if ("SENSEX".equals(symbol)) {
            return predictSensexStrategy(symbol, data, currentPrice, ema20, rsi, adx, atr, latest, avgVol, optionData);
        } else if ("BANKNIFTY".equals(symbol)) {
            return predictBankNiftyStrategy(symbol, data, currentPrice, ema50, rsi, adx, atr, latest, avgVol, optionData);
        }

        // Generic Strategy for other symbols
        return genericStrategy(symbol, data, currentPrice, ema200, indicators, rsi, adx, atr, optionData);
    }

    private AIPrediction predictBankNiftyStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, SimpleMarketData latest, double avgVol, OptionData optionData) {
        // BankNifty is highly volatile and respects VWAP and Institutional Levels
        AIPrediction trend = bankNiftyTrendStrategy(symbol, data, currentPrice, ema50, rsi, adx, atr, optionData);
        AIPrediction vwap = bankNiftyVWAPStrategy(symbol, data, currentPrice, atr);
        AIPrediction scalper = bankNiftyOptionScalperStrategy(symbol, data, currentPrice, rsi, adx, atr, optionData);
        
        int bullishCount = 0;
        int bearishCount = 0;
        List<String> reasoningList = new ArrayList<>();

        AIPrediction[] all = {trend, vwap, scalper};
        for (AIPrediction p : all) {
            if (p.predictedDirection.equals("UP")) { bullishCount++; reasoningList.add(p.predictionReasoning); }
            else if (p.predictedDirection.equals("DOWN")) { bearishCount++; reasoningList.add(p.predictionReasoning); }
        }

        String finalDirection = "NEUTRAL";
        double finalConfidence = 0;
        
        // BankNifty needs confluence (2+) OR strong institutional delta
        if (bullishCount >= 2 || (scalper.predictedDirection.equals("UP") && scalper.confidence >= 80)) {
            finalDirection = "UP";
            finalConfidence = 80 + (bullishCount * 3);
        } else if (bearishCount >= 2 || (scalper.predictedDirection.equals("DOWN") && scalper.confidence >= 80)) {
            finalDirection = "DOWN";
            finalConfidence = 80 + (bearishCount * 3);
        }

        return new AIPrediction(finalDirection, finalConfidence, finalConfidence/100.0, adx, rsi, atr/currentPrice, 80, "BANKNIFTY_VOL_V1", "BankNifty Multi-Factor: " + String.join(" + ", reasoningList), atr * 1.5, atr * 1.5, false);
    }

    private AIPrediction bankNiftyTrendStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, OptionData optionData) {
        double ema20 = calculateEMA(data, 20);
        boolean up = currentPrice > ema20 && ema20 > ema50 && adx > 20 && rsi > 52;
        boolean down = currentPrice < ema20 && ema20 < ema50 && adx > 20 && rsi < 48;
        
        String dir = up ? "UP" : (down ? "DOWN" : "NEUTRAL");
        double score = up || down ? 80 : 0;
        return new AIPrediction(dir, score, score/100.0, adx, rsi, atr/currentPrice, 80, "BN_TREND", "BankNifty Trend Alignment", atr * 1.2, atr * 1.2, false);
    }

    private AIPrediction bankNiftyVWAPStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double atr) {
        double vwap = new AdvancedIndicatorsEngine().calculateVWAP(data);
        String dir = "NEUTRAL";
        double score = 0;
        
        // Buy if price pulls back to VWAP and holds (Mean Reversion / Trend Continuation)
        if (currentPrice > vwap && currentPrice < vwap + (atr * 0.3)) {
            dir = "UP";
            score = 78;
        } else if (currentPrice < vwap && currentPrice > vwap - (atr * 0.3)) {
            dir = "DOWN";
            score = 78;
        }
        return new AIPrediction(dir, score, score/100.0, 0, 0, 0, 80, "BN_VWAP", "VWAP Pullback/Support", atr * 1.2, atr * 1.0, false);
    }

    private AIPrediction bankNiftyOptionScalperStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double rsi, double adx, double atr, OptionData optionData) {
        if (optionData == null) return createDefaultAIPrediction("No Option Data");
        String dir = "NEUTRAL";
        double score = 0;
        if (optionData.putOIChange > optionData.callOIChange * 1.8 && rsi > 50) { dir = "UP"; score = 85; }
        else if (optionData.callOIChange > optionData.putOIChange * 1.8 && rsi < 50) { dir = "DOWN"; score = 85; }
        return new AIPrediction(dir, score, score/100.0, adx, rsi, atr/currentPrice, 80, "BN_INST", "Institutional Delta Shift", atr * 1.2, atr * 1.2, true);
    }

    private AIPrediction genericStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema200, AdvancedIndicatorsEngine.AdvancedIndicatorsResult indicators, double rsi, double adx, double atr, OptionData optionData) {
        boolean isMajorUptrend = currentPrice > ema200;
        boolean isMajorDowntrend = currentPrice < ema200;
        java.util.Set<String> patterns = com.trading.bot.technical.AdvancedCandlestickDetector.detectAll(data);
        boolean hasFVG = detectFVG(data);
        
        int bullishCount = 0;
        int bearishCount = 0;
        java.util.List<String> bullishReasoning = new java.util.ArrayList<>();
        java.util.List<String> bearishReasoning = new java.util.ArrayList<>();

        if (isMajorUptrend) { bullishCount++; bullishReasoning.add("Trend: Bullish (Above EMA200)"); }
        else if (isMajorDowntrend) { bearishCount++; bearishReasoning.add("Trend: Bearish (Below EMA200)"); }

        if (indicators.overallSignal.equals("BULLISH")) { bullishCount += 2; bullishReasoning.add("Ind: " + indicators.reasoning); }
        else if (indicators.overallSignal.equals("BEARISH")) { bearishCount += 2; bearishReasoning.add("Ind: " + indicators.reasoning); }

        for (String p : patterns) {
            if (p.contains("Bullish") || p.equals("Hammer") || p.equals("Morning Star") || p.equals("Bullish Engulfing")) { bullishCount++; bullishReasoning.add("Pattern: " + p); }
            else if (p.contains("Bearish") || p.equals("Shooting Star") || p.equals("Evening Star") || p.equals("Bearish Engulfing")) { bearishCount++; bearishReasoning.add("Pattern: " + p); }
        }

        if (hasFVG) { 
            if (isMajorUptrend) { bullishCount++; bullishReasoning.add("SMC: Bullish FVG"); }
            else if (isMajorDowntrend) { bearishCount++; bearishReasoning.add("SMC: Bearish FVG"); }
        }

        String finalDirection = "NEUTRAL";
        double finalConfidence = 0;
        String finalReasoning = "No strong signal";
        
        if (isMajorUptrend && bullishCount >= 4 && adx > 22 && rsi < 65) {
            finalDirection = "UP";
            finalConfidence = 85 + (bullishCount * 2);
            finalReasoning = String.join(" | ", bullishReasoning);
        } else if (isMajorDowntrend && bearishCount >= 4 && adx > 22 && rsi > 35) {
            finalDirection = "DOWN";
            finalConfidence = 85 + (bearishCount * 2);
            finalReasoning = String.join(" | ", bearishReasoning);
        }

        return new AIPrediction(finalDirection, finalConfidence, finalConfidence/100.0, adx, rsi, atr/currentPrice, 80, "GENERIC_V1", finalReasoning, atr * 1.0, atr * 1.0, false);
    }

    private boolean detectFVG(List<SimpleMarketData> data) {
        if (data.size() < 3) return false;
        SimpleMarketData c1 = data.get(data.size()-3);
        SimpleMarketData c3 = data.get(data.size()-1);
        return c1.high < c3.low; // Simplified Bullish FVG
    }

    // --- CANDLESTICK PATTERN DETECTION (Adaptive V19) ---
    private String detectCandlePattern(List<SimpleMarketData> data) {
        java.util.Set<String> patterns = com.trading.bot.technical.AdvancedCandlestickDetector.detectAll(data);
        if (patterns.isEmpty()) return "NONE";
        if (patterns.stream().anyMatch(p -> p.contains("Bullish") || p.contains("Morning") || p.contains("Rising") || p.contains("White") || p.contains("Ladder"))) return "BULLISH";
        if (patterns.stream().anyMatch(p -> p.contains("Bearish") || p.contains("Evening") || p.contains("Falling") || p.contains("Black") || p.contains("Dark"))) return "BEARISH";
        return patterns.iterator().next();
    }

    // --- VOLUME PRICE ACTION (VPA V19) ---
    private boolean isVolumeConfirmed(List<SimpleMarketData> data, String direction) {
        if (data.size() < 10) return false;
        SimpleMarketData latest = data.get(data.size() - 1);
        double avgVol = data.stream().skip(data.size() - 10).mapToLong(d -> d.volume).average().orElse(0);
        
        // V19 Optimization: 1.1x Volume is enough if price action is strong
        if (latest.volume > avgVol * 1.1) return true;
        
        // Or if volume is increasing over last 3 candles
        long v1 = data.get(data.size()-1).volume;
        long v2 = data.get(data.size()-2).volume;
        long v3 = data.get(data.size()-3).volume;
        if (v1 > v2 && v2 > v3) return true;
        
        return false;
    }

    // --- MARKET REGIME FILTER (V15) ---
    private String getMarketRegime(List<SimpleMarketData> data) {
        if (data.size() < 50) return "NORMAL";
        double adx = calculateADX(data, 14);
        double ema20 = calculateEMA(data, 20);
        double ema50 = calculateEMA(data, 50);
        
        // Strong Trend: ADX > 22 and EMA slope
        if (adx > 22 && Math.abs(ema20 - ema50) > (data.get(data.size()-1).price * 0.0005)) {
            return "TRENDING";
        }
        // Volatile: ATR relative to price is high
        double atr = calculateATR(data, 14);
        if (atr > (data.get(data.size()-1).price * 0.002)) {
            return "VOLATILE";
        }
        return "NORMAL";
    }

    private AIPrediction predictNiftyStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, SimpleMarketData latest, double avgVol, OptionData optionData) {
        // 1. Technical candidates
        AIPrediction trend = niftyTrendStrategy(symbol, data, currentPrice, ema50, rsi, adx, atr, latest, optionData);
        AIPrediction reversion = niftyMeanReversionStrategy(symbol, data, currentPrice, rsi, atr, latest, optionData);
        AIPrediction breakout = niftyBreakoutStrategy(symbol, data, currentPrice, adx, atr, latest, optionData);
        AIPrediction scalper = niftyOptionScalperStrategy(symbol, data, currentPrice, rsi, adx, atr, optionData);
        
        // 2. Pattern & Volume
        String pattern = detectCandlePattern(data);
        boolean volumeOk = isVolumeConfirmed(data, trend.predictedDirection);

        // CONFLUENCE ENGINE (V19): Optimized for 1 Call/Day
        int bullishCount = 0;
        int bearishCount = 0;
        double totalConfidence = 0;
        List<String> reasoningList = new ArrayList<>();

        AIPrediction[] all = {trend, reversion, breakout, scalper};
        for (AIPrediction p : all) {
            if (p.predictedDirection.equals("UP")) {
                bullishCount++;
                totalConfidence += p.confidence;
                reasoningList.add(p.predictionReasoning);
            } else if (p.predictedDirection.equals("DOWN")) {
                bearishCount++;
                totalConfidence += p.confidence;
                reasoningList.add(p.predictionReasoning);
            }
        }

        if (!pattern.equals("NONE")) {
            if (pattern.contains("BULLISH") || pattern.equals("BULLISH")) bullishCount++;
            if (pattern.contains("BEARISH") || pattern.equals("BEARISH")) bearishCount++;
        }

        String finalDirection = "NEUTRAL";
        double finalConfidence = 0;
        String finalReasoning = "No strong signal";
        String finalModel = "NIFTY_CONSISTENT_V19";

        // V19 RULE: 
        // 1. Confluence (2+) AND (Volume OR Pattern)
        // 2. OR Very Strong Scalper (Confidence > 85)
        
        // V19.2 Rule: Optimized for higher frequency (Aiming 1-2 calls/day)
        boolean strongBullish = (bullishCount >= 2 && adx > 20) || (scalper.predictedDirection.equals("UP") && scalper.confidence >= 80);
        boolean strongBearish = (bearishCount >= 2 && adx > 20) || (scalper.predictedDirection.equals("DOWN") && scalper.confidence >= 80);

        if (strongBullish) {
            finalDirection = "UP";
            finalConfidence = 75 + (bullishCount * 3);
            finalReasoning = "V19 CONFLUENCE: " + String.join(" + ", reasoningList);
        } else if (strongBearish) {
            finalDirection = "DOWN";
            finalConfidence = 75 + (bearishCount * 3);
            finalReasoning = "V19 CONFLUENCE: " + String.join(" + ", reasoningList);
        }

        if (optionData != null && !finalDirection.equals("NEUTRAL")) {
            boolean alignUp = optionData.pcr <= 1.1 && optionData.putOIChange > optionData.callOIChange * 1.1;
            boolean alignDown = optionData.pcr >= 0.9 && optionData.callOIChange > optionData.putOIChange * 1.1;
            if (finalDirection.equals("UP") && !alignUp) {
                finalConfidence -= 5;
            } else if (finalDirection.equals("DOWN") && !alignDown) {
                finalConfidence -= 5;
            }
        }
        if (finalConfidence < 70) {
            finalDirection = "NEUTRAL";
            finalConfidence = 0;
        } else {
            finalConfidence = Math.min(98.0, finalConfidence);
        }
        
        // V19.2 Exit: Target 1.2x ATR, SL 1.2x ATR (Better Risk/Reward)
        return new AIPrediction(finalDirection, finalConfidence, finalConfidence/100.0, adx, rsi, atr/currentPrice, 80, finalModel, finalReasoning, atr * 1.2, atr * 1.2, false);
    }

    private AIPrediction niftyTrendStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, SimpleMarketData latest, OptionData optionData) {
        double ema10 = calculateEMA(data, 10);
        double ema20 = calculateEMA(data, 20);
        
        String direction = "NEUTRAL";
        double score = 0;
        
        boolean isBullishTrend = currentPrice > ema20 && ema10 > ema20 && ema20 > ema50;
        boolean isBearishTrend = currentPrice < ema20 && ema10 < ema20 && ema20 < ema50;
        
        // Balanced RSI/ADX (V19.5 Optimization)
        boolean rsiOk = (rsi > 55 && isBullishTrend) || (rsi < 45 && isBearishTrend); 
        boolean adxOk = adx > 18; 

        boolean optionOk = true;
        if (optionData != null) {
            if (isBullishTrend && (optionData.putOIChange < optionData.callOIChange * 1.3)) optionOk = false;
            if (isBearishTrend && (optionData.callOIChange < optionData.putOIChange * 1.3)) optionOk = false;
        }

        if ((isBullishTrend || isBearishTrend) && rsiOk && adxOk && optionOk) {
            direction = isBullishTrend ? "UP" : "DOWN";
            score = 80 + (adx / 3);
        }

        return new AIPrediction(direction, score, score/100.0, adx, rsi, atr/currentPrice, 80, "NIFTY_TREND_V9", "Trend + Institutional Confirmation", atr * 1.2, atr * 1.0, false);
    }

    private AIPrediction niftyOptionScalperStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double rsi, double adx, double atr, OptionData optionData) {
        if (optionData == null) return createDefaultAIPrediction("No Option Data");
        
        String direction = "NEUTRAL";
        double score = 0;
        
        // Institutional "Delta" Scalp Logic (Optimized for frequency):
        if (optionData.putOIChange > optionData.callOIChange * 1.5 && rsi > 50) {
            direction = "UP";
            score = 82;
        }
        else if (optionData.callOIChange > optionData.putOIChange * 1.5 && rsi < 50) {
            direction = "DOWN";
            score = 82;
        }

        return new AIPrediction(direction, score, score/100.0, adx, rsi, atr/currentPrice, 80, "NIFTY_INSTITUTIONAL_SCALPER_V2", "Institutional Delta Shift", atr * 1.0, atr * 1.0, true);
    }

    private AIPrediction niftyMeanReversionStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double rsi, double atr, SimpleMarketData latest, OptionData optionData) {
        double[] bb = calculateBollingerBands(data, 20, 2.5); // Use 2.5 StdDev for mean reversion
        double upperBB = bb[0];
        double lowerBB = bb[1];
        
        String direction = "NEUTRAL";
        double score = 0;
        
        // Oversold + Bottom BB touch
        if (rsi < 28 && currentPrice <= lowerBB) {
            direction = "UP";
            score = 78;
            if (optionData != null && optionData.pcr > 1.3) score += 10; // Extra points for extreme sentiment
        } 
        // Overbought + Top BB touch
        else if (rsi > 72 && currentPrice >= upperBB) {
            direction = "DOWN";
            score = 78;
            if (optionData != null && optionData.pcr < 0.7) score += 10;
        }

        return new AIPrediction(direction, score, score/100.0, 0, rsi, atr/currentPrice, 80, "NIFTY_REVERSION_V1", "Extreme Oversold/Overbought Reversal", atr * 1.2, atr * 1.0, false);
    }

    private AIPrediction niftyBreakoutStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double adx, double atr, SimpleMarketData latest, OptionData optionData) {
        boolean isSqueeze = detectSqueeze(data, 20, 2.0);
        double[] bb = calculateBollingerBands(data, 20, 2.0);
        double upperBB = bb[0];
        double lowerBB = bb[1];
        
        String direction = "NEUTRAL";
        double score = 0;
        
        // Breakout from squeeze
        if (isSqueeze && currentPrice > upperBB) {
            direction = "UP";
            score = 85;
        } else if (isSqueeze && currentPrice < lowerBB) {
            direction = "DOWN";
            score = 85;
        }

        return new AIPrediction(direction, score, score/100.0, adx, 0, atr/currentPrice, 80, "NIFTY_BREAKOUT_V1", "Bollinger Squeeze Expansion", atr * 1.5, atr * 1.2, true);
    }

    // --- STRATEGY 2: SENSEX (High Momentum - Dynamic Optimization) ---
    private AIPrediction predictSensexStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema20, double rsi, double adx, double atr, SimpleMarketData latest, double avgVol, OptionData optionData) {
        // 1. Technical candidates
        AIPrediction trend = sensexTrendStrategy(symbol, data, currentPrice, ema20, rsi, adx, atr, optionData);
        AIPrediction reversion = sensexMeanReversionStrategy(symbol, data, currentPrice, rsi, atr, optionData);
        AIPrediction breakout = sensexBreakoutStrategy(symbol, data, currentPrice, adx, atr, latest, optionData);
        AIPrediction scalper = sensexOptionScalperStrategy(symbol, data, currentPrice, rsi, adx, atr, optionData);
        
        // 2. Pattern candidates
        String pattern = detectCandlePattern(data);
        boolean volumeOk = isVolumeConfirmed(data, trend.predictedDirection);

        // CONFLUENCE ENGINE (V19): Optimized for 1 Call/Day
        int bullishCount = 0;
        int bearishCount = 0;
        double totalConfidence = 0;
        List<String> reasoningList = new ArrayList<>();

        AIPrediction[] all = {trend, reversion, breakout, scalper};
        for (AIPrediction p : all) {
            if (p.predictedDirection.equals("UP")) {
                bullishCount++;
                totalConfidence += p.confidence;
                reasoningList.add(p.predictionReasoning);
            } else if (p.predictedDirection.equals("DOWN")) {
                bearishCount++;
                totalConfidence += p.confidence;
                reasoningList.add(p.predictionReasoning);
            }
        }

        if (!pattern.equals("NONE")) {
            if (pattern.contains("BULLISH")) bullishCount++;
            if (pattern.contains("BEARISH")) bearishCount++;
        }

        String finalDirection = "NEUTRAL";
        double finalConfidence = 0;
        String finalReasoning = "No strong signal";
        String finalModel = "SENSEX_CONSISTENT_V19";

        boolean strongBullish = (bullishCount >= 2 && (volumeOk || !pattern.equals("NONE"))) || (scalper.predictedDirection.equals("UP") && scalper.confidence >= 80);
        boolean strongBearish = (bearishCount >= 2 && (volumeOk || !pattern.equals("NONE"))) || (scalper.predictedDirection.equals("DOWN") && scalper.confidence >= 80);

        if (strongBullish) {
            finalDirection = "UP";
            finalConfidence = 78 + (bullishCount * 3);
            finalReasoning = "V19 CONFLUENCE: " + String.join(" + ", reasoningList);
        } else if (strongBearish) {
            finalDirection = "DOWN";
            finalConfidence = 78 + (bearishCount * 3);
            finalReasoning = "V19 CONFLUENCE: " + String.join(" + ", reasoningList);
        }

        if (optionData != null && !finalDirection.equals("NEUTRAL")) {
            if (finalDirection.equals("UP")) {
                if (optionData.pcr > 1.0 && optionData.putOIChange > optionData.callOIChange * 1.1) finalConfidence += 5;
            } else {
                if (optionData.pcr < 1.0 && optionData.callOIChange > optionData.putOIChange * 1.1) finalConfidence += 5;
            }
            Double d = optionData.greeks != null ? optionData.greeks.get("delta") : null;
            if (d != null && Math.abs(d) >= 0.35) finalConfidence += 3;
        }
        
        if (finalConfidence < 72) {
            finalDirection = "NEUTRAL";
            finalConfidence = 0;
        } else {
            finalConfidence = Math.min(98.0, finalConfidence);
        }
        
        return new AIPrediction(finalDirection, finalConfidence, finalConfidence/100.0, adx, rsi, atr/currentPrice, 80, finalModel, finalReasoning, atr * 1.5, atr * 1.5, false);
    }

    private AIPrediction sensexTrendStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema20, double rsi, double adx, double atr, OptionData optionData) {
        double ema10 = calculateEMA(data, 10);
        double ema50 = calculateEMA(data, 50);
        
        String direction = "NEUTRAL";
        double score = 0;
        
        boolean trendUp = currentPrice > ema20 && ema10 > ema20 && ema20 > ema50;
        boolean trendDown = currentPrice < ema20 && ema10 < ema20 && ema20 < ema50;
        
        // Balanced filtering for SENSEX
        if ((trendUp || trendDown) && adx > 22 && (rsi > 55 || rsi < 45)) {
            direction = trendUp ? "UP" : "DOWN";
            score = 82 + (adx / 4);
        }
        
        return new AIPrediction(direction, score, score/100.0, adx, rsi, atr/currentPrice, 80, "SENSEX_TREND_V7", "Sensex Multi-EMA Trend", atr * 2.0, atr * 1.2, false);
    }

    private AIPrediction sensexBreakoutStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double adx, double atr, SimpleMarketData latest, OptionData optionData) {
        boolean isSqueeze = detectSqueeze(data, 20, 2.0);
        double[] bb = calculateBollingerBands(data, 20, 2.0);
        double upperBB = bb[0];
        double lowerBB = bb[1];
        
        String direction = "NEUTRAL";
        double score = 0;
        
        if (isSqueeze && currentPrice > upperBB) {
            direction = "UP";
            score = 85;
        } else if (isSqueeze && currentPrice < lowerBB) {
            direction = "DOWN";
            score = 85;
        }

        return new AIPrediction(direction, score, score/100.0, adx, 0, atr/currentPrice, 80, "SENSEX_BREAKOUT_V1", "Volatility Expansion", atr * 2.0, atr * 1.5, true);
    }

    private AIPrediction sensexOptionScalperStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double rsi, double adx, double atr, OptionData optionData) {
        if (optionData == null) return createDefaultAIPrediction("No Option Data");
        
        String direction = "NEUTRAL";
        double score = 0;
        
        // Institutional "Delta" Scalp Logic (Optimized for Sensex):
        if (optionData.putOIChange > optionData.callOIChange * 1.8 && rsi > 50) {
            direction = "UP";
            score = 82;
        } else if (optionData.callOIChange > optionData.putOIChange * 1.8 && rsi < 50) {
            direction = "DOWN";
            score = 82;
        }

        return new AIPrediction(direction, score, score/100.0, adx, rsi, atr/currentPrice, 80, "SENSEX_INSTITUTIONAL_SCALPER_V2", "Institutional Delta Shift", atr * 1.2, atr * 1.2, true);
    }

    private AIPrediction sensexMeanReversionStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double rsi, double atr, OptionData optionData) {
        double[] bb = calculateBollingerBands(data, 20, 2.5);
        double upperBB = bb[0];
        double lowerBB = bb[1];
        
        String direction = "NEUTRAL";
        double score = 0;
        
        if (rsi < 25 && currentPrice <= lowerBB) {
            direction = "UP";
            score = 80;
        } else if (rsi > 75 && currentPrice >= upperBB) {
            direction = "DOWN";
            score = 80;
        }

        return new AIPrediction(direction, score, score/100.0, 0, rsi, atr/currentPrice, 80, "SENSEX_REVERSION_V1", "Overextended Mean Reversal", atr * 1.5, atr * 1.5, false);
    }
    
    // --- Indicators ---

    // Honest MACD Calculation (Returns: [MACD, Signal, Hist, PrevHist])
    private double[] calculateCorrectMACD(List<SimpleMarketData> data, int fast, int slow, int signal) {
        if (data.size() < slow + signal) return new double[]{0,0,0,0};
        
        List<Double> macdLineHistory = new ArrayList<>();
        
        // Calculate MACD Line History
        // We need enough history to calculate Signal Line EMA
        int startIdx = data.size() - (signal * 2); 
        if (startIdx < slow) startIdx = slow;

        for (int i = startIdx; i < data.size(); i++) {
            List<SimpleMarketData> subList = data.subList(0, i + 1);
            double emaFast = calculateEMA(subList, fast);
            double emaSlow = calculateEMA(subList, slow);
            macdLineHistory.add(emaFast - emaSlow);
        }
        
        if (macdLineHistory.isEmpty()) return new double[]{0,0,0,0};
        
        // Calculate Signal Line (EMA of MACD Line)
        double currentMACD = macdLineHistory.get(macdLineHistory.size() - 1);
        double prevMACD = macdLineHistory.size() > 1 ? macdLineHistory.get(macdLineHistory.size() - 2) : 0;
        
        double currentSignal = calculateEMAFromValues(macdLineHistory, signal);
        
        // Calculate Previous Signal (for Crossover detection)
        List<Double> prevMacdHistory = macdLineHistory.subList(0, macdLineHistory.size() - 1);
        double prevSignal = calculateEMAFromValues(prevMacdHistory, signal);
        
        double currentHist = currentMACD - currentSignal;
        double prevHist = prevMACD - prevSignal;
        
        return new double[]{currentMACD, currentSignal, currentHist, prevHist};
    }
    
    private double calculateEMAFromValues(List<Double> values, int period) {
        if (values.size() < period) return values.get(values.size()-1);
        double multiplier = 2.0 / (period + 1);
        double ema = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            ema = ((values.get(i) - ema) * multiplier) + ema;
        }
        return ema;
    }
    
    private double calculateSMA(List<SimpleMarketData> data, int period) {
        if (data.size() < period) return 0;
        return data.subList(data.size() - period, data.size()).stream()
            .mapToDouble(d -> d.price)
            .average().orElse(0);
    }
    
    private double calculateStdDev(List<SimpleMarketData> data, int period, double sma) {
        double sumSqDiff = 0;
        List<SimpleMarketData> periodData = data.subList(data.size() - period, data.size());
        for (SimpleMarketData d : periodData) {
            sumSqDiff += Math.pow(d.price - sma, 2);
        }
        return Math.sqrt(sumSqDiff / period);
    }
    
    private double calculateRSI(List<SimpleMarketData> data, int period) {
        if (data.size() < period + 1) return 50;
        
        double gain = 0, loss = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            double change = data.get(i).price - data.get(i - 1).price;
            if (change > 0) gain += change;
            else loss -= change;
        }
        
        if (loss == 0) return 100;
        double rs = gain / loss;
        return 100 - (100 / (1 + rs));
    }
    
    private double calculateVolatility(List<SimpleMarketData> data, int period) {
        if (data.size() < period) return 0;
        List<Double> prices = data.subList(data.size() - period, data.size()).stream()
            .map(d -> d.price).toList();
            
        double mean = prices.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = prices.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
        return Math.sqrt(variance) / mean; // Relative volatility
    }

    private double calculateAverageBBWidth(List<SimpleMarketData> data, int period) {
        if (data.size() < period * 2) return 0.01; // Default
        double totalWidth = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            double[] bb = calculateBollingerBands(data.subList(0, i + 1), 20, 2.0);
            totalWidth += (bb[0] - bb[1]) / bb[2];
        }
        return totalWidth / period;
    }

    private double calculateATR(List<SimpleMarketData> data, int period) {
        if (data.size() < period + 1) return 10.0;
        double trSum = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            SimpleMarketData curr = data.get(i);
            SimpleMarketData prev = data.get(i-1);
            
            // True Range = Max(High-Low, Abs(High-ClosePrev), Abs(Low-ClosePrev))
            double hl = curr.high - curr.low;
            double hcp = Math.abs(curr.high - prev.price); // prev.price is close
            double lcp = Math.abs(curr.low - prev.price);
            double tr = Math.max(hl, Math.max(hcp, lcp));
            
            trSum += tr;
        }
        return trSum / period;
    }

    private boolean detectSqueeze(List<SimpleMarketData> data, int period, double stdDevMultiplier) {
        if (data.size() < period) return false;
        
        double[] bb = calculateBollingerBands(data, period, stdDevMultiplier);
        double upper = bb[0];
        double lower = bb[1];
        double mid = bb[2];
        
        // Keltner Channels (using ATR)
        double atr = calculateATR(data, period);
        double keltnerUpper = mid + (atr * 1.5);
        double keltnerLower = mid - (atr * 1.5);
        
        // Squeeze is ON when Bollinger Bands are INSIDE Keltner Channels
        return (upper < keltnerUpper) && (lower > keltnerLower);
    }
    
    private double[] calculateBollingerBands(List<SimpleMarketData> data, int period, double stdDevMultiplier) {
        double sma = calculateSMA(data, period);
        
        double sumSqDiff = 0;
        List<SimpleMarketData> periodData = data.subList(data.size() - period, data.size());
        for (SimpleMarketData d : periodData) {
            sumSqDiff += Math.pow(d.price - sma, 2);
        }
        double stdDev = Math.sqrt(sumSqDiff / period);
        
        double upper = sma + (stdDev * stdDevMultiplier);
        double lower = sma - (stdDev * stdDevMultiplier);
        
        return new double[]{upper, lower, sma};
    }
    
    private double calculateEMA(List<SimpleMarketData> data, int period) {
        List<Double> prices = data.stream().map(d -> d.price).toList();
        return calculateEMAFromValues(prices, period);
    }

    private double calculateADX(List<SimpleMarketData> data, int period) {
        if (data.size() < period * 2) return 50.0; // Need enough data for smoothing
        
        // 1. Calculate TR, DM+, DM-
        double[] tr = new double[data.size()];
        double[] dmPlus = new double[data.size()];
        double[] dmMinus = new double[data.size()];
        
        for (int i = 1; i < data.size(); i++) {
            SimpleMarketData curr = data.get(i);
            SimpleMarketData prev = data.get(i-1);
            
            double hl = curr.high - curr.low;
            double hcp = Math.abs(curr.high - prev.price);
            double lcp = Math.abs(curr.low - prev.price);
            tr[i] = Math.max(hl, Math.max(hcp, lcp));
            
            double upMove = curr.high - prev.high;
            double downMove = prev.low - curr.low;
            
            if (upMove > downMove && upMove > 0) dmPlus[i] = upMove;
            else dmPlus[i] = 0;
            
            if (downMove > upMove && downMove > 0) dmMinus[i] = downMove;
            else dmMinus[i] = 0;
        }
        
        // 2. Smooth TR, DM+, DM- (Wilder's Smoothing)
        // First value is simple sum
        double trSmooth = 0, dmPlusSmooth = 0, dmMinusSmooth = 0;
        for (int i = 1; i <= period; i++) {
            trSmooth += tr[i];
            dmPlusSmooth += dmPlus[i];
            dmMinusSmooth += dmMinus[i];
        }
        
        // Subsequent values
        List<Double> dxList = new ArrayList<>();
        for (int i = period + 1; i < data.size(); i++) {
            trSmooth = trSmooth - (trSmooth/period) + tr[i];
            dmPlusSmooth = dmPlusSmooth - (dmPlusSmooth/period) + dmPlus[i];
            dmMinusSmooth = dmMinusSmooth - (dmMinusSmooth/period) + dmMinus[i];
            
            if (trSmooth == 0) continue;
            
            double diPlus = 100 * (dmPlusSmooth / trSmooth);
            double diMinus = 100 * (dmMinusSmooth / trSmooth);
            double sumDi = diPlus + diMinus;
            
            if (sumDi == 0) dxList.add(0.0);
            else dxList.add(100 * Math.abs(diPlus - diMinus) / sumDi);
        }
        
        // 3. ADX is SMA of DX
        if (dxList.isEmpty()) return 50.0;
        return dxList.stream().mapToDouble(d -> d).average().orElse(50.0);
    }
    
    private AIPrediction createDefaultAIPrediction(String reason) {
        return new AIPrediction("NEUTRAL", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "NONE", reason, 0.0, 0.0, false);
    }
}
