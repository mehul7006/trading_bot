package com.trading.bot.ai;

import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.market.OptionData;
import com.trading.bot.technical.AdvancedIndicatorsEngine;
import com.trading.bot.technical.AdvancedCandlestickDetector;

import com.trading.bot.smartmoney.FairValueGapDetector;
import com.trading.bot.smartmoney.LiquidityAnalyzer;
import com.trading.bot.smartmoney.OrderBlockDetector;
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
    private final OrderBlockDetector obDetector = new OrderBlockDetector();
    private final FairValueGapDetector fvgDetector = new FairValueGapDetector();
    private final LiquidityAnalyzer liquidityAnalyzer = new LiquidityAnalyzer();
    
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
    
    public AIPrediction generatePrediction(String symbol, List<SimpleMarketData> data, OptionData optionData, 
                                          Map<String, Object> smcData, Map<String, Double> greeksData) {
        if (data.size() < 100) return new AIPrediction("NEUTRAL", 0, 0, 0, 0, 0, 0, "MODEL_1", "Insufficient data", 0, 0, false);

        // Auto-generate SMC if not provided
        if (smcData == null) {
            smcData = new HashMap<>();
            OrderBlockDetector.OrderBlockAnalysis ob = obDetector.detectOrderBlocks(data);
            FairValueGapDetector.FVGAnalysis fvg = fvgDetector.detectFairValueGaps(data);
            LiquidityAnalyzer.LiquidityAnalysis liq = liquidityAnalyzer.analyzeLiquidity(data);
            double smcScore = calculateSMCScoreInternal(ob, fvg, liq);
            smcData.put("smcScore", smcScore);
            smcData.put("bias", smcScore > 15 ? "BULLISH" : (smcScore < -15 ? "BEARISH" : "NEUTRAL"));
        }

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

        // SYMBOL-SPECIFIC STRATEGY ROUTING (V24.0 - Institutional Mode)
        AIPrediction prediction;
        if ("NIFTY50".equals(symbol)) {
            prediction = predictNiftyStrategy(symbol, data, currentPrice, ema50, rsi, adx, atr, latest, avgVol, optionData, smcData, greeksData);
        } else if ("SENSEX".equals(symbol)) {
            prediction = predictSensexStrategy(symbol, data, currentPrice, ema50, rsi, adx, atr, latest, avgVol, optionData, smcData, greeksData);
        } else if ("BANKNIFTY".equals(symbol)) {
            prediction = predictBankNiftyStrategy(symbol, data, currentPrice, ema50, rsi, adx, atr, latest, avgVol, optionData, smcData, greeksData);
        } else {
            prediction = predictDefaultStrategy(symbol, data, currentPrice, ema50, rsi, adx, atr, latest, avgVol, optionData, smcData, greeksData);
        }

        // Apply institutional boosts
        return applyInstitutionalBoosts(prediction, smcData, greeksData);
    }
    
    public AIPrediction generatePrediction(String symbol, List<SimpleMarketData> data, OptionData optionData) {
        return generatePrediction(symbol, data, optionData, null, null);
    }

    private double calculateSMCScoreInternal(OrderBlockDetector.OrderBlockAnalysis ob, 
                                           FairValueGapDetector.FVGAnalysis fvg, 
                                           LiquidityAnalyzer.LiquidityAnalysis liq) {
        double score = 0;
        score += ob.institutionalBias * 0.4;
        score += fvg.imbalanceStrength * 0.3;
        score += liq.liquidityScore * 0.3;
        return Math.max(-100, Math.min(100, score));
    }

    private AIPrediction applyInstitutionalBoosts(AIPrediction pred, Map<String, Object> smc, Map<String, Double> greeks) {
        if (pred == null || pred.predictedDirection.equals("NEUTRAL")) return pred;
        if (smc == null) return pred;

        double smcScore = (double) smc.getOrDefault("smcScore", 0.0);
        double confidence = pred.confidence;
        String reasoning = pred.predictionReasoning;

        // Boost if SMC aligns with Technicals
        if (pred.predictedDirection.equals("UP") && smcScore > 15) {
            confidence += 10;
            reasoning += " | SMC Bullish Boost: " + smcScore;
        } else if (pred.predictedDirection.equals("DOWN") && smcScore < -15) {
            confidence += 10;
            reasoning += " | SMC Bearish Boost: " + Math.abs(smcScore);
        }

        // Penalty if SMC conflicts with Technicals
        if (pred.predictedDirection.equals("UP") && smcScore < -30) {
            confidence -= 30;
            reasoning += " | SMC CONFLICT: Bearish Bias (" + smcScore + ")";
        } else if (pred.predictedDirection.equals("DOWN") && smcScore > 30) {
            confidence -= 30;
            reasoning += " | SMC CONFLICT: Bullish Bias (" + smcScore + ")";
        }

        // Greeks Delta confirmation
        if (greeks != null && greeks.containsKey("delta")) {
            double delta = greeks.get("delta");
            if (pred.predictedDirection.equals("UP") && delta > 0.6) {
                confidence += 5;
                reasoning += " | High Delta Confirmation";
            } else if (pred.predictedDirection.equals("DOWN") && delta < 0.4) {
                confidence += 5;
                reasoning += " | High Gamma Confirmation";
            }
        }

        // Final normalization
        confidence = Math.min(99, Math.max(0, confidence));
        
        // Final filter: If confidence dropped below 85, neutralize
        if (confidence < 85) {
            return new AIPrediction("NEUTRAL", 0, 0, pred.neuralNetworkScore, pred.marketRegimePrediction, 
                                  pred.volatilityForecast, pred.liquidityPrediction, pred.aiModel, 
                                  "Institutional Filter: Low Confidence (" + String.format("%.1f", confidence) + ") | " + reasoning, 
                                  0, 0, false);
        }

        return new AIPrediction(pred.predictedDirection, confidence, confidence/100.0, 
                              pred.neuralNetworkScore, pred.marketRegimePrediction, pred.volatilityForecast, 
                              pred.liquidityPrediction, pred.aiModel, reasoning, 
                              pred.estimatedMovePoints, pred.suggestedStopLoss, pred.isBreakout);
    }

    private AIPrediction predictBankNiftyStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, SimpleMarketData latest, double avgVol, OptionData optionData, Map<String, Object> smcData, Map<String, Double> greeksData) {
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
        
        java.time.LocalTime time = latest.timestamp.toLocalTime();
        boolean isSafeTime = time.isAfter(java.time.LocalTime.of(9, 15)) && time.isBefore(java.time.LocalTime.of(15, 15));

        // V24.0 BANKNIFTY: Balanced Confluence with 1-2 calls target
        if (isSafeTime && (bullishCount >= 1 && adx > 15 && rsi > 45)) {
            finalDirection = "UP";
            finalConfidence = 85 + (bullishCount * 2);
        } else if (isSafeTime && (bearishCount >= 1 && adx > 15 && rsi < 55)) {
            finalDirection = "DOWN";
            finalConfidence = 85 + (bearishCount * 2);
        }

        if (finalConfidence < 85) {
            finalDirection = "NEUTRAL";
            finalConfidence = 0;
        }

        return new AIPrediction(finalDirection, finalConfidence, finalConfidence/100.0, adx, rsi, atr/currentPrice, 80, "BANKNIFTY_V24", "BankNifty V24.0: " + String.join(" | ", reasoningList), atr * 1.5, atr * 1.0, false);
    }

    private AIPrediction predictNiftyStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, SimpleMarketData latest, double avgVol, OptionData optionData, Map<String, Object> smcData, Map<String, Double> greeksData) {
        AIPrediction trend = niftyTrendStrategy(symbol, data, currentPrice, ema50, rsi, adx, atr, latest, optionData);
        AIPrediction reversion = niftyMeanReversionStrategy(symbol, data, currentPrice, rsi, atr, latest, optionData);
        AIPrediction breakout = niftyBreakoutStrategy(symbol, data, currentPrice, adx, atr, latest, optionData);
        AIPrediction scalper = niftyOptionScalperStrategy(symbol, data, currentPrice, rsi, adx, atr, optionData);
        
        int bullishCount = 0;
        int bearishCount = 0;
        List<String> reasoningList = new ArrayList<>();

        AIPrediction[] all = {trend, reversion, breakout, scalper};
        for (AIPrediction p : all) {
            if (p.predictedDirection.equals("UP")) { bullishCount++; reasoningList.add(p.predictionReasoning); }
            else if (p.predictedDirection.equals("DOWN")) { bearishCount++; reasoningList.add(p.predictionReasoning); }
        }

        String finalDirection = "NEUTRAL";
        double finalConfidence = 0;
        
        java.time.LocalTime time = latest.timestamp.toLocalTime();
        boolean isSafeTime = time.isAfter(java.time.LocalTime.of(9, 15)) && time.isBefore(java.time.LocalTime.of(15, 15));

        if (isSafeTime && (bullishCount >= 1 && adx > 15 && rsi > 48)) {
            finalDirection = "UP";
            finalConfidence = 85 + (bullishCount * 2);
        } else if (isSafeTime && (bearishCount >= 1 && adx > 15 && rsi < 52)) {
            finalDirection = "DOWN";
            finalConfidence = 85 + (bearishCount * 2);
        }

        if (finalConfidence < 85) {
            finalDirection = "NEUTRAL";
            finalConfidence = 0;
        }

        return new AIPrediction(finalDirection, finalConfidence, finalConfidence/100.0, adx, rsi, atr/currentPrice, 80, "NIFTY_V24", "Nifty V24.0: " + String.join(" | ", reasoningList), atr * 1.5, atr * 1.0, false);
    }

    private AIPrediction predictSensexStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, SimpleMarketData latest, double avgVol, OptionData optionData, Map<String, Object> smcData, Map<String, Double> greeksData) {
        AIPrediction trend = sensexTrendStrategy(symbol, data, currentPrice, ema50, rsi, adx, atr, optionData);
        AIPrediction reversion = sensexMeanReversionStrategy(symbol, data, currentPrice, rsi, atr, optionData);
        AIPrediction breakout = sensexBreakoutStrategy(symbol, data, currentPrice, adx, atr, latest, optionData);
        AIPrediction scalper = sensexOptionScalperStrategy(symbol, data, currentPrice, rsi, adx, atr, optionData);
        
        int bullishCount = 0;
        int bearishCount = 0;
        List<String> reasoningList = new ArrayList<>();

        AIPrediction[] all = {trend, reversion, breakout, scalper};
        for (AIPrediction p : all) {
            if (p.predictedDirection.equals("UP")) { bullishCount++; reasoningList.add(p.predictionReasoning); }
            else if (p.predictedDirection.equals("DOWN")) { bearishCount++; reasoningList.add(p.predictionReasoning); }
        }

        String finalDirection = "NEUTRAL";
        double finalConfidence = 0;
        java.time.LocalTime time = latest.timestamp.toLocalTime();
        boolean isSafeTime = time.isAfter(java.time.LocalTime.of(9, 15)) && time.isBefore(java.time.LocalTime.of(15, 15));

        if (isSafeTime && (bullishCount >= 1 && adx > 15 && rsi > 48)) {
            finalDirection = "UP";
            finalConfidence = 85 + (bullishCount * 2);
        } else if (isSafeTime && (bearishCount >= 1 && adx > 15 && rsi < 52)) {
            finalDirection = "DOWN";
            finalConfidence = 85 + (bearishCount * 2);
        }

        if (finalConfidence < 85) {
            finalDirection = "NEUTRAL";
            finalConfidence = 0;
        }

        return new AIPrediction(finalDirection, finalConfidence, finalConfidence/100.0, adx, rsi, atr/currentPrice, 80, "SENSEX_V24", "Sensex V24.0: " + String.join(" | ", reasoningList), atr * 1.8, atr * 1.0, false);
    }

    private AIPrediction predictDefaultStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, SimpleMarketData latest, double avgVol, OptionData optionData, Map<String, Object> smcData, Map<String, Double> greeksData) {
        boolean up = currentPrice > ema50 && adx > 20 && rsi > 55;
        boolean down = currentPrice < ema50 && adx > 20 && rsi < 45;
        String dir = up ? "UP" : (down ? "DOWN" : "NEUTRAL");
        double score = up || down ? 85 : 0;
        return new AIPrediction(dir, score, score/100.0, adx, rsi, atr/currentPrice, 80, "DEFAULT_V24", "Trend Following", atr * 1.0, atr * 1.0, false);
    }

    private AIPrediction bankNiftyTrendStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, OptionData optionData) {
        double ema10 = calculateEMA(data, 10);
        double ema20 = calculateEMA(data, 20);
        boolean up = currentPrice > ema10 && adx > 15 && rsi > 50;
        boolean down = currentPrice < ema10 && adx > 15 && rsi < 50;
        
        String dir = up ? "UP" : (down ? "DOWN" : "NEUTRAL");
        double score = up || down ? 82 : 0;
        return new AIPrediction(dir, score, score/100.0, adx, rsi, atr/currentPrice, 80, "BN_TREND", "BankNifty Trend", atr * 1.3, atr * 1.2, false);
    }

    private AIPrediction bankNiftyVWAPStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double atr) {
        double vwap = new AdvancedIndicatorsEngine().calculateVWAP(data);
        String dir = "NEUTRAL";
        double score = 0;
        
        if (currentPrice > vwap && currentPrice < vwap + (atr * 0.5)) {
            dir = "UP";
            score = 80;
        } else if (currentPrice < vwap && currentPrice > vwap - (atr * 0.5)) {
            dir = "DOWN";
            score = 80;
        }
        return new AIPrediction(dir, score, score/100.0, 0, 0, 0, 80, "BN_VWAP", "VWAP Zone Support", atr * 1.5, atr * 1.0, false);
    }

    private AIPrediction bankNiftyOptionScalperStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double rsi, double adx, double atr, OptionData optionData) {
        if (optionData == null) {
            // Technical-only scalper for Audit mode
            if (adx > 25 && rsi > 60) return new AIPrediction("UP", 82, 0.82, adx, rsi, 0, 80, "BN_INST", "Tech Scalp UP", atr, atr, true);
            if (adx > 25 && rsi < 40) return new AIPrediction("DOWN", 82, 0.82, adx, rsi, 0, 80, "BN_INST", "Tech Scalp DOWN", atr, atr, true);
            return createDefaultAIPrediction("No Option Data");
        }
        String dir = "NEUTRAL";
        double score = 0;
        if (optionData.putOIChange > optionData.callOIChange * 1.2 && rsi > 50) { dir = "UP"; score = 85; }
        else if (optionData.callOIChange > optionData.putOIChange * 1.2 && rsi < 50) { dir = "DOWN"; score = 85; }
        return new AIPrediction(dir, score, score/100.0, adx, rsi, atr/currentPrice, 80, "BN_INST", "Institutional Flow Shift", atr * 1.2, atr * 1.2, true);
    }

    private AIPrediction niftyTrendStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, SimpleMarketData latest, OptionData optionData) {
        double ema10 = calculateEMA(data, 10);
        double ema20 = calculateEMA(data, 20);
        boolean isBullishTrend = currentPrice > ema20 && ema10 > ema20;
        boolean isBearishTrend = currentPrice < ema20 && ema10 < ema20;
        
        boolean rsiOk = (rsi > 50 && isBullishTrend) || (rsi < 50 && isBearishTrend); 
        boolean adxOk = adx > 15; 

        String direction = "NEUTRAL";
        double score = 0;
        if ((isBullishTrend || isBearishTrend) && rsiOk && adxOk) {
            direction = isBullishTrend ? "UP" : "DOWN";
            score = 80 + (adx / 5);
        }

        return new AIPrediction(direction, score, score/100.0, adx, rsi, atr/currentPrice, 80, "NIFTY_TREND_V9", "Trend Confirmation", atr * 1.2, atr * 1.0, false);
    }

    private AIPrediction niftyOptionScalperStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double rsi, double adx, double atr, OptionData optionData) {
        if (optionData == null) {
            if (adx > 22 && rsi > 58) return new AIPrediction("UP", 82, 0.82, adx, rsi, 0, 80, "NIFTY_INST", "Tech Scalp UP", atr, atr, true);
            if (adx > 22 && rsi < 42) return new AIPrediction("DOWN", 82, 0.82, adx, rsi, 0, 80, "NIFTY_INST", "Tech Scalp DOWN", atr, atr, true);
            return createDefaultAIPrediction("No Option Data");
        }
        String direction = "NEUTRAL";
        double score = 0;
        if (optionData.putOIChange > optionData.callOIChange * 1.3 && rsi > 50) { direction = "UP"; score = 82; }
        else if (optionData.callOIChange > optionData.putOIChange * 1.3 && rsi < 50) { direction = "DOWN"; score = 82; }
        return new AIPrediction(direction, score, score/100.0, adx, rsi, atr/currentPrice, 80, "NIFTY_INST", "Institutional Shift", atr * 1.0, atr * 1.0, true);
    }

    private AIPrediction niftyMeanReversionStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double rsi, double atr, SimpleMarketData latest, OptionData optionData) {
        double[] bb = calculateBollingerBands(data, 20, 2.0);
        String direction = "NEUTRAL";
        double score = 0;
        if (rsi < 35 && currentPrice <= bb[1]) { direction = "UP"; score = 78; }
        else if (rsi > 65 && currentPrice >= bb[0]) { direction = "DOWN"; score = 78; }
        return new AIPrediction(direction, score, score/100.0, 0, rsi, atr/currentPrice, 80, "NIFTY_REVERSION_V1", "Mean Reversal", atr * 1.2, atr * 1.0, false);
    }

    private AIPrediction niftyBreakoutStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double adx, double atr, SimpleMarketData latest, OptionData optionData) {
        double[] bb = calculateBollingerBands(data, 20, 2.0);
        String direction = "NEUTRAL";
        double score = 0;
        if (currentPrice > bb[0] && adx > 20) { direction = "UP"; score = 85; }
        else if (currentPrice < bb[1] && adx > 20) { direction = "DOWN"; score = 85; }
        return new AIPrediction(direction, score, score/100.0, adx, 0, atr/currentPrice, 80, "NIFTY_BREAKOUT_V1", "BB Breakout", atr * 1.5, atr * 1.2, true);
    }

    private AIPrediction sensexTrendStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, OptionData optionData) {
        double ema10 = calculateEMA(data, 10);
        boolean trendUp = currentPrice > ema10 && adx > 15;
        boolean trendDown = currentPrice < ema10 && adx > 15;
        String direction = trendUp ? "UP" : (trendDown ? "DOWN" : "NEUTRAL");
        double score = trendUp || trendDown ? 82 : 0;
        return new AIPrediction(direction, score, score/100.0, adx, rsi, atr/currentPrice, 80, "SENSEX_TREND", "Sensex Trend", atr * 2.0, atr * 1.2, false);
    }

    private AIPrediction sensexBreakoutStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double adx, double atr, SimpleMarketData latest, OptionData optionData) {
        double[] bb = calculateBollingerBands(data, 20, 2.0);
        String direction = "NEUTRAL";
        double score = 0;
        if (currentPrice > bb[0] && adx > 20) { direction = "UP"; score = 85; }
        else if (currentPrice < bb[1] && adx > 20) { direction = "DOWN"; score = 85; }
        return new AIPrediction(direction, score, score/100.0, adx, 0, atr/currentPrice, 80, "SENSEX_BREAKOUT", "Sensex Breakout", atr * 2.0, atr * 1.5, true);
    }

    private AIPrediction sensexOptionScalperStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double rsi, double adx, double atr, OptionData optionData) {
        if (optionData == null) {
            if (adx > 22 && rsi > 58) return new AIPrediction("UP", 82, 0.82, adx, rsi, 0, 80, "SENSEX_INST", "Tech Scalp UP", atr, atr, true);
            if (adx > 22 && rsi < 42) return new AIPrediction("DOWN", 82, 0.82, adx, rsi, 0, 80, "SENSEX_INST", "Tech Scalp DOWN", atr, atr, true);
            return createDefaultAIPrediction("No Option Data");
        }
        String direction = "NEUTRAL";
        double score = 0;
        if (optionData.putOIChange > optionData.callOIChange * 1.3 && rsi > 50) { direction = "UP"; score = 82; }
        else if (optionData.callOIChange > optionData.putOIChange * 1.3 && rsi < 50) { direction = "DOWN"; score = 82; }
        return new AIPrediction(direction, score, score/100.0, adx, rsi, atr/currentPrice, 80, "SENSEX_INST", "Institutional Shift", atr * 1.2, atr * 1.2, true);
    }

    private AIPrediction sensexMeanReversionStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double rsi, double atr, OptionData optionData) {
        double[] bb = calculateBollingerBands(data, 20, 2.0);
        String direction = "NEUTRAL";
        double score = 0;
        if (rsi < 35 && currentPrice <= bb[1]) { direction = "UP"; score = 80; }
        else if (rsi > 65 && currentPrice >= bb[0]) { direction = "DOWN"; score = 80; }
        return new AIPrediction(direction, score, score/100.0, 0, rsi, atr/currentPrice, 80, "SENSEX_REVERSION", "Mean Reversal", atr * 1.5, atr * 1.5, false);
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
    
    private double calculateATR(List<SimpleMarketData> data, int period) {
        if (data.size() < period + 1) return 10.0;
        double trSum = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            SimpleMarketData curr = data.get(i);
            SimpleMarketData prev = data.get(i-1);
            double hl = curr.high - curr.low;
            double hcp = Math.abs(curr.high - prev.price);
            double lcp = Math.abs(curr.low - prev.price);
            trSum += Math.max(hl, Math.max(hcp, lcp));
        }
        return trSum / period;
    }

    private double[] calculateBollingerBands(List<SimpleMarketData> data, int period, double stdDevMultiplier) {
        double sma = calculateSMA(data, period);
        double sumSqDiff = 0;
        List<SimpleMarketData> periodData = data.subList(data.size() - period, data.size());
        for (SimpleMarketData d : periodData) {
            sumSqDiff += Math.pow(d.price - sma, 2);
        }
        double stdDev = Math.sqrt(sumSqDiff / period);
        return new double[]{sma + (stdDev * stdDevMultiplier), sma - (stdDev * stdDevMultiplier), sma};
    }
    
    private double calculateEMA(List<SimpleMarketData> data, int period) {
        List<Double> prices = data.stream().map(d -> d.price).toList();
        return calculateEMAFromValues(prices, period);
    }

    private AIPrediction createDefaultAIPrediction(String reason) {
        return new AIPrediction("NEUTRAL", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "NONE", reason, 0.0, 0.0, false);
    }
}
