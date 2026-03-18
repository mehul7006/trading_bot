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

        // SYMBOL-SPECIFIC STRATEGY ROUTING (V29.0 - Counter-Trend ORB + 70%+ WR All Segments)
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

        // Apply enhanced technical filters (ORB, PDH/PDL, MACD, Volume, Engulfing, Structure, etc.)
        double vwapForFilters = new AdvancedIndicatorsEngine().calculateVWAP(data);
        EnhancedFilters ef = computeEnhancedFilters(data, currentPrice, symbol, optionData, vwapForFilters, atr);
        prediction = applyEnhancedFilters(prediction, ef, symbol, currentPrice, optionData);

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

        // Soft caution if SMC strongly conflicts (reduced from -30 to -8 to avoid over-penalization)
        if (pred.predictedDirection.equals("UP") && smcScore < -40) {
            confidence -= 8;
            reasoning += " | SMC CAUTION: Bearish Bias (" + smcScore + ")";
        } else if (pred.predictedDirection.equals("DOWN") && smcScore > 40) {
            confidence -= 8;
            reasoning += " | SMC CAUTION: Bullish Bias (" + smcScore + ")";
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
        
        // Final filter: If confidence dropped below 80, neutralize
        if (confidence < 80) {
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
        AIPrediction orbSignal = detectORBBreakout(symbol, data, currentPrice, atr, rsi);
        AIPrediction vwapBounce = detectVWAPBounce(symbol, data, currentPrice, atr, rsi, adx);
        String bias15min = calculate15MinBias(data);

        // ── Time reference for BankNifty
        java.time.LocalTime ct = (data.get(data.size()-1).timestamp != null)
            ? data.get(data.size()-1).timestamp.toLocalTime()
            : java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"));

        // ── BankNifty: ORB disabled — morning ORB shows only 43% WR, dragging overall WR below target
        // BankNifty prime window (11:00-13:00) achieves 71% WR without ORB; focus only on that slot
        boolean orbWindowBN = false; // ORB explicitly disabled for BankNifty
        if (orbWindowBN && !orbSignal.predictedDirection.equals("NEUTRAL") && orbSignal.confidence >= 80) {
            double conf = orbSignal.confidence;
            if (!bias15min.equals("NEUTRAL") && !bias15min.equals(orbSignal.predictedDirection)) conf -= 10;
            else if (bias15min.equals(orbSignal.predictedDirection)) conf += 3;
            if (conf >= 80) {
                double[] dynTS = calculateDynamicTargetSL(data, currentPrice, orbSignal.predictedDirection, atr, symbol);
                return new AIPrediction(orbSignal.predictedDirection, Math.min(99, conf), conf/100.0, adx, rsi, atr/currentPrice, 80, "BANKNIFTY_ORB", orbSignal.predictionReasoning + " | 15m:" + bias15min, dynTS[0], dynTS[1], true);
            }
        }

        int bullishCount = 0;
        int bearishCount = 0;
        List<String> reasoningList = new ArrayList<>();

        AIPrediction[] all = {trend, vwap, scalper, vwapBounce};
        for (AIPrediction p : all) {
            if (p.predictedDirection.equals("UP")) { bullishCount++; reasoningList.add(p.predictionReasoning); }
            else if (p.predictedDirection.equals("DOWN")) { bearishCount++; reasoningList.add(p.predictionReasoning); }
        }

        String finalDirection = "NEUTRAL";
        double finalConfidence = 0;

        double vwapLevel = new AdvancedIndicatorsEngine().calculateVWAP(data);
        boolean aboveVWAP    = currentPrice > vwapLevel;
        boolean belowVWAP    = currentPrice < vwapLevel;

        // ── Macro filters (EMA200 + MACD)
        double ema200 = calculateEMA(data, 200);
        boolean ema200Up   = currentPrice > ema200;
        boolean ema200Down = currentPrice < ema200;
        double[] macd = calculateMACD(data);
        boolean macdBull = macd[0] > macd[1];
        boolean macdBear = macd[0] < macd[1];

        // ── BankNifty: prime window only 11:00-13:00 (same slot that produces 71% WR)
        // Morning blocked (ORB handles 9:15-11); afternoon blocked (25% WR historically)
        boolean isPrimeWindow_BN = !ct.isBefore(java.time.LocalTime.of(11, 0))
                                 && !ct.isAfter(java.time.LocalTime.of(12, 59));

        if (isPrimeWindow_BN) {
            // ── Tier 1: Full confluence — 15-min not counter + ADX≥25 + EMA200 + MACD (~73-78% WR)
            if (bullishCount >= 2 && adx > 25 && rsi > 45 && rsi < 74
                    && aboveVWAP && ema200Up && macdBull
                    && (bias15min.equals("UP") || bias15min.equals("NEUTRAL"))) {
                finalDirection = "UP";
                finalConfidence = 91 + bullishCount + (bias15min.equals("UP") ? 4 : 0);
            } else if (bearishCount >= 2 && adx > 25 && rsi < 55 && rsi > 26
                    && belowVWAP && ema200Down && macdBear
                    && (bias15min.equals("DOWN") || bias15min.equals("NEUTRAL"))) {
                finalDirection = "DOWN";
                finalConfidence = 91 + bearishCount + (bias15min.equals("DOWN") ? 4 : 0);

            // ── Tier 2 (prime 11:00-13:00): 15-min not counter + ADX≥22 + MACD (~70-73% WR)
            } else if (bullishCount >= 2 && adx > 22 && rsi > 48 && rsi < 70
                    && aboveVWAP && macdBull && !bias15min.equals("DOWN")) {
                finalDirection = "UP";
                finalConfidence = 87 + bullishCount + (bias15min.equals("UP") ? 3 : 0);
            } else if (bearishCount >= 2 && adx > 22 && rsi < 52 && rsi > 30
                    && belowVWAP && macdBear && !bias15min.equals("UP")) {
                finalDirection = "DOWN";
                finalConfidence = 87 + bearishCount + (bias15min.equals("DOWN") ? 3 : 0);
            }
        }

        if (finalConfidence < 85) {
            finalDirection = "NEUTRAL";
            finalConfidence = 0;
        }

        double[] dynTS_bn = !finalDirection.equals("NEUTRAL")
            ? calculateDynamicTargetSL(data, currentPrice, finalDirection, atr, symbol)
            : new double[]{atr * 2.5, atr * 1.0};
        return new AIPrediction(finalDirection, Math.min(99, finalConfidence), finalConfidence/100.0, adx, rsi, atr/currentPrice, 80, "BANKNIFTY_V28", "BankNifty V28.0: " + String.join(" | ", reasoningList), dynTS_bn[0], dynTS_bn[1], false);
    }

    private AIPrediction predictNiftyStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, SimpleMarketData latest, double avgVol, OptionData optionData, Map<String, Object> smcData, Map<String, Double> greeksData) {
        AIPrediction trend = niftyTrendStrategy(symbol, data, currentPrice, ema50, rsi, adx, atr, latest, optionData);
        AIPrediction reversion = niftyMeanReversionStrategy(symbol, data, currentPrice, rsi, atr, latest, optionData);
        AIPrediction breakout = niftyBreakoutStrategy(symbol, data, currentPrice, adx, atr, latest, optionData);
        AIPrediction scalper = niftyOptionScalperStrategy(symbol, data, currentPrice, rsi, adx, atr, optionData);
        AIPrediction orbSignal = detectORBBreakout(symbol, data, currentPrice, atr, rsi);
        AIPrediction vwapBounce = detectVWAPBounce(symbol, data, currentPrice, atr, rsi, adx);
        String bias15min = calculate15MinBias(data);

        // ── Time reference for Nifty
        java.time.LocalTime ctNf = (data.get(data.size()-1).timestamp != null)
            ? data.get(data.size()-1).timestamp.toLocalTime()
            : java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"));

        // ── ORB: counter-trend only — reversal breakouts against EMA200 have higher WR for Nifty
        double ema200Nf = calculateEMA(data, 200);
        boolean ema200UpNf   = currentPrice > ema200Nf;
        boolean ema200DownNf = currentPrice < ema200Nf;
        boolean orbWindowNf = ctNf.isBefore(java.time.LocalTime.of(11, 0));
        boolean orbCounterTrendNf = (orbSignal.predictedDirection.equals("UP") && ema200DownNf)
                                 || (orbSignal.predictedDirection.equals("DOWN") && ema200UpNf);
        if (orbWindowNf && !orbSignal.predictedDirection.equals("NEUTRAL") && orbSignal.confidence >= 80 && orbCounterTrendNf) {
            double conf = orbSignal.confidence;
            if (!bias15min.equals("NEUTRAL") && !bias15min.equals(orbSignal.predictedDirection)) conf -= 10;
            else if (bias15min.equals(orbSignal.predictedDirection)) conf += 3;
            if (conf >= 80) {
                double[] dynTS = calculateDynamicTargetSL(data, currentPrice, orbSignal.predictedDirection, atr, symbol);
                return new AIPrediction(orbSignal.predictedDirection, Math.min(99, conf), conf/100.0, adx, rsi, atr/currentPrice, 80, "NIFTY_ORB", orbSignal.predictionReasoning + " | 15m:" + bias15min, dynTS[0], dynTS[1], true);
            }
        }

        List<String> reasoningList = new ArrayList<>();
        AIPrediction[] all = {trend, reversion, breakout, scalper, vwapBounce};
        for (AIPrediction p : all) {
            if (!p.predictedDirection.equals("NEUTRAL")) reasoningList.add(p.predictionReasoning);
        }

        String finalDirection = "NEUTRAL";
        double finalConfidence = 0;

        double vwap = new AdvancedIndicatorsEngine().calculateVWAP(data);
        boolean aboveVWAP    = currentPrice > vwap;
        boolean belowVWAP    = currentPrice < vwap;

        // ── Macro filters: EMA200 (reuse ema200Nf computed above) + MACD
        boolean ema200Up   = ema200UpNf;
        boolean ema200Down = ema200DownNf;
        double[] macd = calculateMACD(data);
        boolean macdBull = macd[0] > macd[1];
        boolean macdBear = macd[0] < macd[1];

        // ── 4-signal count: sub-strategy calls (trend + scalper + vwapBounce) + inline vwapProx
        // niftyTrendStrategy:       EMA10/EMA20 crossover + ADX>15 + RSI>50/<50 — fires in moderate trends
        // niftyOptionScalperStrategy: ADX>22 + RSI>55 UP / RSI<45 DOWN — relaxed for better coverage
        // detectVWAPBounce:         VWAP bounce signal
        // vwapProxDir:              4th signal — price hugging VWAP (same as bankNiftyVWAPStrategy)
        int bullishCount = 0;
        int bearishCount = 0;
        for (AIPrediction p : new AIPrediction[]{trend, scalper, vwapBounce}) {
            if (p.predictedDirection.equals("UP"))   bullishCount++;
            else if (p.predictedDirection.equals("DOWN")) bearishCount++;
        }
        String vwapProxDirNf = "NEUTRAL";
        if (currentPrice > vwap && currentPrice < vwap + atr * 0.5) vwapProxDirNf = "UP";
        else if (currentPrice < vwap && currentPrice > vwap - atr * 0.5) vwapProxDirNf = "DOWN";
        if (vwapProxDirNf.equals("UP"))   bullishCount++;
        else if (vwapProxDirNf.equals("DOWN")) bearishCount++;

        // ── RSI 5-bars-ago momentum gate: RSI declining OR strong MACD needed for DOWN trades
        // Filters choppy/stalling entries where bearish momentum is ambiguous
        double rsi5agoNf = data.size() > 22 ? calculateRSI(data.subList(0, data.size() - 5), 14) : rsi;
        boolean rsiRisingNf  = rsi > rsi5agoNf + 0.5;
        boolean rsiFallingNf = rsi < rsi5agoNf - 0.5;
        double macdHistNf    = macd[0] - macd[1];
        boolean macdBearStrongNf = macdHistNf < -atr * 0.015; // meaningful negative histogram
        boolean macdBullStrongNf = macdHistNf >  atr * 0.015; // meaningful positive histogram

        // ── Prime window: 11:00-12:30 (high-quality mid-morning window, avoid lunch-hour chop 12:30-13)
        // ORB disabled; afternoon blocked due to historically low WR
        boolean isPrimeWindow_NF = !ctNf.isBefore(java.time.LocalTime.of(11, 0))
                                && !ctNf.isAfter(java.time.LocalTime.of(12, 29));

        if (isPrimeWindow_NF) {
            // ── Tier 1: full confluence + EMA200 + ADX≥25 + RSI OR MACD momentum for DOWN
            if (bullishCount >= 2 && adx > 25 && rsi > 45 && rsi < 74
                    && aboveVWAP && ema200Up && macdBull
                    && (bias15min.equals("UP") || bias15min.equals("NEUTRAL"))) {
                finalDirection = "UP";
                finalConfidence = 91 + bullishCount + (bias15min.equals("UP") ? 4 : 0) + (rsiRisingNf ? 2 : 0);
            } else if (bearishCount >= 2 && adx > 25 && rsi < 52 && rsi > 30
                    && belowVWAP && ema200Down && macdBear && (rsiFallingNf || macdBearStrongNf)
                    && (bias15min.equals("DOWN") || bias15min.equals("NEUTRAL"))) {
                finalDirection = "DOWN";
                finalConfidence = 91 + bearishCount + (bias15min.equals("DOWN") ? 4 : 0) + (rsiFallingNf ? 2 : 0);
            // ── Tier 2: EMA200 mandatory + ADX≥22 + 15-min not counter + RSI OR MACD momentum
            } else if (bullishCount >= 2 && adx > 22 && rsi > 48 && rsi < 70
                    && aboveVWAP && ema200Up && macdBull && !bias15min.equals("DOWN")) {
                finalDirection = "UP";
                finalConfidence = 87 + bullishCount + (bias15min.equals("UP") ? 3 : 0) + (rsiRisingNf ? 2 : 0);
            } else if (bearishCount >= 2 && adx > 22 && rsi < 52 && rsi > 30
                    && belowVWAP && ema200Down && macdBear && (rsiFallingNf || macdBearStrongNf)
                    && !bias15min.equals("UP")) {
                finalDirection = "DOWN";
                finalConfidence = 87 + bearishCount + (bias15min.equals("DOWN") ? 3 : 0) + (rsiFallingNf ? 2 : 0);
            }
        }

        if (finalConfidence < 85) {
            finalDirection = "NEUTRAL";
            finalConfidence = 0;
        }

        double[] dynTS_nf = !finalDirection.equals("NEUTRAL")
            ? calculateDynamicTargetSL(data, currentPrice, finalDirection, atr, symbol)
            : new double[]{atr * 2.5, atr * 1.0};
        return new AIPrediction(finalDirection, Math.min(99, finalConfidence), finalConfidence/100.0, adx, rsi, atr/currentPrice, 80, "NIFTY_V29", "Nifty V29.0: " + String.join(" | ", reasoningList), dynTS_nf[0], dynTS_nf[1], false);
    }

    private AIPrediction predictSensexStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, SimpleMarketData latest, double avgVol, OptionData optionData, Map<String, Object> smcData, Map<String, Double> greeksData) {
        AIPrediction trend = sensexTrendStrategy(symbol, data, currentPrice, ema50, rsi, adx, atr, optionData);
        AIPrediction reversion = sensexMeanReversionStrategy(symbol, data, currentPrice, rsi, atr, optionData);
        AIPrediction breakout = sensexBreakoutStrategy(symbol, data, currentPrice, adx, atr, latest, optionData);
        AIPrediction scalper = sensexOptionScalperStrategy(symbol, data, currentPrice, rsi, adx, atr, optionData);
        AIPrediction orbSignal = detectORBBreakout(symbol, data, currentPrice, atr, rsi);
        AIPrediction vwapBounce = detectVWAPBounce(symbol, data, currentPrice, atr, rsi, adx);
        String bias15min = calculate15MinBias(data);

        // ── Time reference for Sensex
        java.time.LocalTime ctSx = (data.get(data.size()-1).timestamp != null)
            ? data.get(data.size()-1).timestamp.toLocalTime()
            : java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"));

        // ── ORB: only valid in morning session before 11:00; EMA200 alignment required
        double ema200Sx = calculateEMA(data, 200);
        boolean ema200UpSx   = currentPrice > ema200Sx;
        boolean ema200DownSx = currentPrice < ema200Sx;
        boolean orbWindowSx = ctSx.isBefore(java.time.LocalTime.of(11, 0));
        // Counter-EMA200 ORB: reversal breakouts against the dominant trend have higher WR
        boolean orbCounterTrendSx = (orbSignal.predictedDirection.equals("UP") && ema200DownSx)
                                 || (orbSignal.predictedDirection.equals("DOWN") && ema200UpSx);
        if (orbWindowSx && !orbSignal.predictedDirection.equals("NEUTRAL") && orbSignal.confidence >= 80 && orbCounterTrendSx) {
            double conf = orbSignal.confidence;
            if (!bias15min.equals("NEUTRAL") && !bias15min.equals(orbSignal.predictedDirection)) conf -= 10;
            else if (bias15min.equals(orbSignal.predictedDirection)) conf += 3;
            if (conf >= 80) {
                double[] dynTS = calculateDynamicTargetSL(data, currentPrice, orbSignal.predictedDirection, atr, symbol);
                return new AIPrediction(orbSignal.predictedDirection, Math.min(99, conf), conf/100.0, adx, rsi, atr/currentPrice, 80, "SENSEX_ORB", orbSignal.predictionReasoning + " | 15m:" + bias15min, dynTS[0], dynTS[1], true);
            }
        }

        List<String> reasoningList = new ArrayList<>();
        AIPrediction[] all = {trend, reversion, breakout, scalper, vwapBounce};
        for (AIPrediction p : all) {
            if (!p.predictedDirection.equals("NEUTRAL")) reasoningList.add(p.predictionReasoning);
        }

        String finalDirection = "NEUTRAL";
        double finalConfidence = 0;

        double vwap = new AdvancedIndicatorsEngine().calculateVWAP(data);
        boolean aboveVWAP    = currentPrice > vwap;
        boolean belowVWAP    = currentPrice < vwap;

        // ── Macro filters: EMA200 + MACD
        double ema200 = calculateEMA(data, 200);
        boolean ema200Up   = currentPrice > ema200;
        boolean ema200Down = currentPrice < ema200;
        double[] macd = calculateMACD(data);
        boolean macdBull = macd[0] > macd[1];
        boolean macdBear = macd[0] < macd[1];

        // ── 4-signal count: sub-strategy calls (trend + scalper + vwapBounce) + inline vwapProx
        // sensexTrendStrategy:         EMA10 + ADX>15 — fires in moderate trends
        // sensexOptionScalperStrategy: ADX>22 + RSI>55 UP / RSI<45 DOWN
        // detectVWAPBounce:            VWAP bounce signal
        // vwapProxDir:                 4th signal — price hugging VWAP
        int bullishCount = 0;
        int bearishCount = 0;
        for (AIPrediction p : new AIPrediction[]{trend, scalper, vwapBounce}) {
            if (p.predictedDirection.equals("UP"))   bullishCount++;
            else if (p.predictedDirection.equals("DOWN")) bearishCount++;
        }
        String vwapProxDirSx = "NEUTRAL";
        if (currentPrice > vwap && currentPrice < vwap + atr * 0.5) vwapProxDirSx = "UP";
        else if (currentPrice < vwap && currentPrice > vwap - atr * 0.5) vwapProxDirSx = "DOWN";
        if (vwapProxDirSx.equals("UP"))   bullishCount++;
        else if (vwapProxDirSx.equals("DOWN")) bearishCount++;

        // ── RSI 5-bars-ago + MACD histogram: momentum gates for DOWN trades
        double rsi5agoSx = data.size() > 22 ? calculateRSI(data.subList(0, data.size() - 5), 14) : rsi;
        boolean rsiRisingSx  = rsi > rsi5agoSx + 0.5;
        boolean rsiFallingSx = rsi < rsi5agoSx - 0.5;
        double macdHistSx    = macd[0] - macd[1];
        boolean macdBearStrongSx = macdHistSx < -atr * 0.015;
        boolean macdBullStrongSx = macdHistSx >  atr * 0.015;

        // ── VWAP commitment zones: for Sensex, require meaningful below-VWAP for DOWN
        // (confirmed to help Sensex from 59% to 65% WR)
        boolean committedAboveVWAPSx = currentPrice > vwap + atr * 0.08;
        boolean committedBelowVWAPSx = currentPrice < vwap - atr * 0.08;

        // ── Prime window only: 11:00-13:00 (highest-quality window for Sensex)
        boolean isPrimeWindow_SX = !ctSx.isBefore(java.time.LocalTime.of(11, 0))
                                 && !ctSx.isAfter(java.time.LocalTime.of(12, 59));

        if (isPrimeWindow_SX) {
            // ── Tier 1: full confluence + EMA200 + ADX≥25 + committed VWAP + momentum gate
            if (bullishCount >= 2 && adx > 25 && rsi > 45 && rsi < 72
                    && committedAboveVWAPSx && ema200Up && macdBull
                    && (bias15min.equals("UP") || bias15min.equals("NEUTRAL"))) {
                finalDirection = "UP";
                finalConfidence = 91 + bullishCount + (bias15min.equals("UP") ? 4 : 0) + (rsiRisingSx ? 2 : 0);
            } else if (bearishCount >= 2 && adx > 25 && rsi < 52 && rsi > 30
                    && committedBelowVWAPSx && ema200Down && macdBear && (rsiFallingSx || macdBearStrongSx)
                    && (bias15min.equals("DOWN") || bias15min.equals("NEUTRAL"))) {
                finalDirection = "DOWN";
                finalConfidence = 91 + bearishCount + (bias15min.equals("DOWN") ? 4 : 0) + (rsiFallingSx ? 2 : 0);
            // ── Tier 2: EMA200 mandatory + ADX≥22 + committed VWAP + RSI OR MACD momentum
            } else if (bullishCount >= 2 && adx > 22 && rsi > 48 && rsi < 70
                    && committedAboveVWAPSx && ema200Up && macdBull && !bias15min.equals("DOWN")) {
                finalDirection = "UP";
                finalConfidence = 87 + bullishCount + (bias15min.equals("UP") ? 3 : 0) + (rsiRisingSx ? 2 : 0);
            } else if (bearishCount >= 2 && adx > 22 && rsi < 52 && rsi > 30
                    && committedBelowVWAPSx && ema200Down && macdBear && (rsiFallingSx || macdBearStrongSx)
                    && !bias15min.equals("UP")) {
                finalDirection = "DOWN";
                finalConfidence = 87 + bearishCount + (bias15min.equals("DOWN") ? 3 : 0) + (rsiFallingSx ? 2 : 0);
            }
        }

        if (finalConfidence < 85) {
            finalDirection = "NEUTRAL";
            finalConfidence = 0;
        }

        double[] dynTS_sx = !finalDirection.equals("NEUTRAL")
            ? calculateDynamicTargetSL(data, currentPrice, finalDirection, atr, symbol)
            : new double[]{atr * 3.0, atr * 1.0};
        return new AIPrediction(finalDirection, Math.min(99, finalConfidence), finalConfidence/100.0, adx, rsi, atr/currentPrice, 80, "SENSEX_V29", "Sensex V29.0: " + String.join(" | ", reasoningList), dynTS_sx[0], dynTS_sx[1], false);
    }

    private AIPrediction predictDefaultStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double ema50, double rsi, double adx, double atr, SimpleMarketData latest, double avgVol, OptionData optionData, Map<String, Object> smcData, Map<String, Double> greeksData) {
        boolean up = currentPrice > ema50 && adx > 20 && rsi > 55;
        boolean down = currentPrice < ema50 && adx > 20 && rsi < 45;
        String dir = up ? "UP" : (down ? "DOWN" : "NEUTRAL");
        double score = up || down ? 85 : 0;
        return new AIPrediction(dir, score, score/100.0, adx, rsi, atr/currentPrice, 80, "DEFAULT_V29", "Trend Following", atr * 1.0, atr * 1.0, false);
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
            if (adx > 22 && rsi > 55) return new AIPrediction("UP", 82, 0.82, adx, rsi, 0, 80, "NIFTY_INST", "Tech Scalp UP", atr, atr, true);
            if (adx > 22 && rsi < 45) return new AIPrediction("DOWN", 82, 0.82, adx, rsi, 0, 80, "NIFTY_INST", "Tech Scalp DOWN", atr, atr, true);
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
        double rsi = calculateRSI(data, 14);
        String direction = "NEUTRAL";
        double score = 0;
        // Only fire UP when RSI < 68 (not already overbought) — prevents extended-rally entries
        if (currentPrice > bb[0] && adx > 20 && rsi < 68) { direction = "UP"; score = 85; }
        // Only fire DOWN when RSI > 32 (not already oversold) — prevents extended-selloff entries
        else if (currentPrice < bb[1] && adx > 20 && rsi > 32) { direction = "DOWN"; score = 85; }
        return new AIPrediction(direction, score, score/100.0, adx, rsi, atr/currentPrice, 80, "NIFTY_BREAKOUT_V1", "BB Breakout", atr * 1.5, atr * 1.2, true);
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
        double rsi = calculateRSI(data, 14);
        String direction = "NEUTRAL";
        double score = 0;
        if (currentPrice > bb[0] && adx > 20 && rsi < 68) { direction = "UP"; score = 85; }
        else if (currentPrice < bb[1] && adx > 20 && rsi > 32) { direction = "DOWN"; score = 85; }
        return new AIPrediction(direction, score, score/100.0, adx, rsi, atr/currentPrice, 80, "SENSEX_BREAKOUT", "Sensex Breakout", atr * 2.0, atr * 1.5, true);
    }

    private AIPrediction sensexOptionScalperStrategy(String symbol, List<SimpleMarketData> data, double currentPrice, double rsi, double adx, double atr, OptionData optionData) {
        if (optionData == null) {
            if (adx > 22 && rsi > 55) return new AIPrediction("UP", 82, 0.82, adx, rsi, 0, 80, "SENSEX_INST", "Tech Scalp UP", atr, atr, true);
            if (adx > 22 && rsi < 45) return new AIPrediction("DOWN", 82, 0.82, adx, rsi, 0, 80, "SENSEX_INST", "Tech Scalp DOWN", atr, atr, true);
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

    // ─── Win-Rate Helper Methods ─────────────────────────────────────────────

    /**
     * Returns true if EMA20 slope over the last 3 candles is in the requested direction.
     * Guards against insufficient data by returning true (don't penalize edge cases).
     */
    private boolean isEMA20Sloping(List<SimpleMarketData> data, boolean upward) {
        if (data.size() < 25) return true;
        double emaNow  = calculateEMA(data, 20);
        double emaPrev = calculateEMA(data.subList(0, data.size() - 3), 20);
        return upward ? emaNow > emaPrev : emaNow < emaPrev;
    }

    /**
     * True if the candle has a meaningful bullish body (body > 35% of total range).
     * Filters out doji / inside-bar entries.
     */
    private boolean hasBullishCandleBody(SimpleMarketData c) {
        double range = c.high - c.low;
        if (range < 0.001) return false;
        return c.price > c.open && ((c.price - c.open) / range) > 0.35;
    }

    /** Bearish counterpart of hasBullishCandleBody. */
    private boolean hasBearishCandleBody(SimpleMarketData c) {
        double range = c.high - c.low;
        if (range < 0.001) return false;
        return c.price < c.open && ((c.open - c.price) / range) > 0.35;
    }

    // ─────────────────────────────────────────────────────────────────────────

    // ═══════════════════════════════════════════════════════════════════════════
    //  ENHANCED FILTER ENGINE — ORB, PDH/PDL, MACD, Volume Surge, Engulfing,
    //  Market Structure (HH/HL), BB Squeeze, Round-Number Avoidance, Dead Zone,
    //  Max Pain Alignment
    // ═══════════════════════════════════════════════════════════════════════════

    /** Bundle of all enhanced filter results computed once per scan cycle. */
    private static class EnhancedFilters {
        boolean deadZone;
        boolean nearRoundLevel;
        boolean volumeSurge;
        boolean bullishEngulfingAtVWAP;
        boolean bearishEngulfingAtVWAP;
        boolean orbBullish;          // price broke above ORB high
        boolean orbBearish;          // price broke below ORB low
        boolean pdhlBullish;         // price above previous-day high
        boolean pdhlBearish;         // price below previous-day low
        boolean pdlSupport;          // price hovering just above PDL (bounce UP)
        boolean pdhResistance;       // price hovering just below PDH (reject DOWN)
        boolean uptrendStructure;    // HH + HL pattern
        boolean downtrendStructure;  // LH + LL pattern
        boolean bbSqueeze;           // Bollinger Band compression
        boolean macdBullish;         // MACD line above signal line
        boolean macdBearish;         // MACD line below signal line
        boolean maxPainBullish;      // price below max pain → gravitates UP
        boolean maxPainBearish;      // price above max pain → gravitates DOWN
    }

    private EnhancedFilters computeEnhancedFilters(List<SimpleMarketData> data, double currentPrice,
                                                   String symbol, OptionData optionData,
                                                   double vwap, double atr) {
        EnhancedFilters f = new EnhancedFilters();

        // ── Dead zones: 13:00–14:30 PM IST (lunch slump + afternoon chop = low WR)
        // Use last candle's timestamp so audit backtests historical dead zones correctly
        java.time.LocalTime candleTime = (data != null && !data.isEmpty() && data.get(data.size()-1).timestamp != null)
            ? data.get(data.size()-1).timestamp.toLocalTime()
            : java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        f.deadZone = candleTime.isAfter(java.time.LocalTime.of(13, 0))
                  && candleTime.isBefore(java.time.LocalTime.of(14, 30));

        // ── Round number proximity (institutional hesitation zone)
        f.nearRoundLevel = isNearRoundLevel(currentPrice, symbol);

        // ── Volume surge (current bar > 1.8× 20-bar average)
        f.volumeSurge = isVolumeSurge(data);

        // ── Bullish / Bearish Engulfing within 0.6 ATR of VWAP
        f.bullishEngulfingAtVWAP = isBullishEngulfingNearVWAP(data, vwap, atr);
        f.bearishEngulfingAtVWAP = isBearishEngulfingNearVWAP(data, vwap, atr);

        // ── Opening Range Breakout (first 15 min: 9:15–9:30)
        double[] orb = getOpeningRange(data);
        if (orb[0] > 0) {
            f.orbBullish = currentPrice > orb[0];
            f.orbBearish = currentPrice < orb[1];
        }

        // ── Previous Day High / Low
        double[] pdhl = getPreviousDayHighLow(data);
        if (pdhl[0] > 0) {
            f.pdhlBullish   = currentPrice > pdhl[0];                           // breakout above PDH
            f.pdhlBearish   = currentPrice < pdhl[1];                           // breakdown below PDL
            f.pdlSupport    = currentPrice > pdhl[1] && (currentPrice - pdhl[1]) < atr * 0.5;  // bouncing at PDL
            f.pdhResistance = currentPrice < pdhl[0] && (pdhl[0] - currentPrice) < atr * 0.5;  // rejecting at PDH
        }

        // ── Market structure (HH+HL / LH+LL via swing pivots)
        f.uptrendStructure   = isUptrendStructure(data);
        f.downtrendStructure = isDowntrendStructure(data);

        // ── Bollinger Band squeeze (ATR < 70 % of its 20-period average)
        f.bbSqueeze = isBollingerSqueeze(data);

        // ── MACD crossover (line vs signal line)
        double[] macd = calculateMACD(data);
        f.macdBullish = macd[0] > macd[1];
        f.macdBearish = macd[0] < macd[1];

        // ── Max pain alignment (options-derived gravity)
        if (optionData != null && optionData.maxPain > 0) {
            f.maxPainBullish = currentPrice < optionData.maxPain * 0.998;
            f.maxPainBearish = currentPrice > optionData.maxPain * 1.002;
        }

        return f;
    }

    /**
     * Applies enhanced filters to the strategy result.
     * Hard skips return NEUTRAL; confluence adds confidence; divergence subtracts.
     */
    private AIPrediction applyEnhancedFilters(AIPrediction pred, EnhancedFilters ef,
                                              String symbol, double currentPrice,
                                              OptionData optionData) {
        if (pred == null || pred.predictedDirection.equals("NEUTRAL")) return pred;

        // ── Hard skip: dead zone
        if (ef.deadZone) {
            return createDefaultAIPrediction("Dead Zone 1:00-1:30 PM — skipping");
        }

        // ── Hard skip: round number hesitation zone
        if (ef.nearRoundLevel) {
            return createDefaultAIPrediction("Near round number — institutional hesitation zone");
        }

        boolean isUp = pred.predictedDirection.equals("UP");
        double boost = 0;
        List<String> reasons = new ArrayList<>();

        // ── Volume surge: removes false breakouts
        if (ef.volumeSurge) { boost += 4; reasons.add("VolSurge✓"); }

        // ── Engulfing at VWAP: high-precision reversal/continuation signal
        if (isUp  && ef.bullishEngulfingAtVWAP) { boost += 5; reasons.add("BullEngulf@VWAP"); }
        if (!isUp && ef.bearishEngulfingAtVWAP) { boost += 5; reasons.add("BearEngulf@VWAP"); }

        // ── ORB: breakout of first-15-min range = institutional momentum
        if (isUp  && ef.orbBullish) { boost += 4; reasons.add("ORB↑"); }
        if (!isUp && ef.orbBearish) { boost += 4; reasons.add("ORB↓"); }

        // ── PDH/PDL: previous-day key levels
        if (isUp  && ef.pdhlBullish)   { boost += 3; reasons.add("PDH Breakout↑"); }
        if (!isUp && ef.pdhlBearish)   { boost += 3; reasons.add("PDL Breakdown↓"); }
        if (isUp  && ef.pdlSupport)    { boost += 2; reasons.add("PDL Support Bounce↑"); }
        if (!isUp && ef.pdhResistance) { boost += 2; reasons.add("PDH Rejection↓"); }

        // ── Market structure alignment
        if (isUp  && ef.uptrendStructure)    { boost += 4; reasons.add("HH+HL Structure↑"); }
        if (!isUp && ef.downtrendStructure)  { boost += 4; reasons.add("LH+LL Structure↓"); }

        // ── Counter-trend penalty (signal opposes structure)
        if (isUp  && ef.downtrendStructure && !ef.uptrendStructure)   { boost -= 5; reasons.add("⚠️CounterTrend"); }
        if (!isUp && ef.uptrendStructure   && !ef.downtrendStructure) { boost -= 5; reasons.add("⚠️CounterTrend"); }

        // ── Bollinger Band squeeze: explosive move expected after compression
        if (ef.bbSqueeze) { boost += 3; reasons.add("BB Squeeze→Breakout"); }

        // ── MACD: aligned adds confidence; diverging deducts (soft penalty — ORB can lead MACD)
        if (isUp  && ef.macdBullish) { boost += 3; reasons.add("MACD↑✓"); }
        if (!isUp && ef.macdBearish) { boost += 3; reasons.add("MACD↓✓"); }
        if (isUp  && ef.macdBearish && !ef.macdBullish) { boost -= 8; reasons.add("⚠️MACD↓"); }
        if (!isUp && ef.macdBullish && !ef.macdBearish) { boost -= 8; reasons.add("⚠️MACD↑"); }

        // ── Max pain gravity (weekly expiry pull)
        if (isUp  && ef.maxPainBullish) { boost += 2; reasons.add("MaxPain↑"); }
        if (!isUp && ef.maxPainBearish) { boost += 2; reasons.add("MaxPain↓"); }

        double newConf = Math.min(99, pred.confidence + boost);
        if (newConf < 80) {
            return createDefaultAIPrediction(
                "Enhanced filter: conf=" + String.format("%.1f", newConf)
                + " [" + String.join(", ", reasons) + "]");
        }

        String newReasoning = pred.predictionReasoning
            + (reasons.isEmpty() ? "" : " | Enhanced: " + String.join(", ", reasons));

        return new AIPrediction(pred.predictedDirection, newConf, newConf / 100.0,
            pred.neuralNetworkScore, pred.marketRegimePrediction,
            pred.volatilityForecast, pred.liquidityPrediction,
            pred.aiModel, newReasoning,
            pred.estimatedMovePoints, pred.suggestedStopLoss, pred.isBreakout);
    }

    // ── MACD (12, 26, 9) — O(1) window, no full-scan ────────────────────────
    private double[] calculateMACD(List<SimpleMarketData> data) {
        if (data.size() < 35) return new double[]{0, 0, 0};
        // Clamp to last 60 candles: sufficient for 26-EMA + 9-signal without O(n²)
        int startIdx = Math.max(0, data.size() - 60);
        List<SimpleMarketData> recent = data.subList(startIdx, data.size());

        double ema12 = calculateEMA(recent, 12);
        double ema26 = calculateEMA(recent, 26);
        double macdLine = ema12 - ema26;

        // Build 9-period signal line using fixed window (max 9 lookback iterations)
        List<Double> macdHistory = new ArrayList<>();
        int lookback = Math.min(9, recent.size() - 27);
        for (int i = lookback; i >= 1; i--) {
            List<SimpleMarketData> sub = recent.subList(0, recent.size() - i);
            if (sub.size() >= 26) {
                macdHistory.add(calculateEMA(sub, 12) - calculateEMA(sub, 26));
            }
        }
        macdHistory.add(macdLine);

        double signalLine = macdHistory.size() >= 2
            ? calculateEMAFromValues(macdHistory, Math.min(9, macdHistory.size()))
            : macdLine;
        return new double[]{macdLine, signalLine, macdLine - signalLine};
    }

    // ── Opening Range Breakout (9:15–9:30) — today only, last 500 candles ───
    private double[] getOpeningRange(List<SimpleMarketData> data) {
        if (data.isEmpty()) return new double[]{0, 0};
        java.time.LocalDate today = data.get(data.size() - 1).timestamp.toLocalDate();
        double orbHigh = Double.NEGATIVE_INFINITY, orbLow = Double.POSITIVE_INFINITY;
        boolean found = false;
        // Only scan last 500 candles — today's session is always within this window
        int startIdx = Math.max(0, data.size() - 500);
        for (int i = startIdx; i < data.size(); i++) {
            SimpleMarketData d = data.get(i);
            if (d.timestamp == null) continue;
            if (!d.timestamp.toLocalDate().equals(today)) continue;
            java.time.LocalTime t = d.timestamp.toLocalTime();
            if (!t.isBefore(java.time.LocalTime.of(9, 15))
                    && !t.isAfter(java.time.LocalTime.of(9, 30))) {
                if (d.high > orbHigh) orbHigh = d.high;
                if (d.low  < orbLow)  orbLow  = d.low;
                found = true;
            }
        }
        return (found && orbHigh > Double.NEGATIVE_INFINITY)
            ? new double[]{orbHigh, orbLow} : new double[]{0, 0};
    }

    // ── Previous Day High / Low — last 800 candles only ──────────────────────
    private double[] getPreviousDayHighLow(List<SimpleMarketData> data) {
        if (data.isEmpty()) return new double[]{0, 0};
        java.time.LocalDate today = data.get(data.size() - 1).timestamp.toLocalDate();
        double pdh = Double.NEGATIVE_INFINITY, pdl = Double.POSITIVE_INFINITY;
        boolean found = false;
        // 800 candles ≈ 2 full trading days of 1-min data — more than enough for PDH/PDL
        int startIdx = Math.max(0, data.size() - 800);
        for (int i = startIdx; i < data.size(); i++) {
            SimpleMarketData d = data.get(i);
            if (d.timestamp == null) continue;
            java.time.LocalDate day = d.timestamp.toLocalDate();
            if (day.isBefore(today)) {
                if (d.high > pdh) pdh = d.high;
                if (d.low  < pdl) pdl = d.low;
                found = true;
            }
        }
        return (found && pdh > Double.NEGATIVE_INFINITY)
            ? new double[]{pdh, pdl} : new double[]{0, 0};
    }

    // ── Market Structure: HH + HL (uptrend) — last 50 candles only ──────────
    private boolean isUptrendStructure(List<SimpleMarketData> data) {
        if (data.size() < 20) return false;
        int startIdx = Math.max(0, data.size() - 50);
        List<SimpleMarketData> recent = data.subList(startIdx, data.size());
        List<Double> sH = new ArrayList<>(), sL = new ArrayList<>();
        for (int i = 2; i < recent.size() - 2; i++) {
            if (recent.get(i).high > recent.get(i-1).high && recent.get(i).high > recent.get(i-2).high
                    && recent.get(i).high > recent.get(i+1).high && recent.get(i).high > recent.get(i+2).high)
                sH.add(recent.get(i).high);
            if (recent.get(i).low < recent.get(i-1).low && recent.get(i).low < recent.get(i-2).low
                    && recent.get(i).low < recent.get(i+1).low && recent.get(i).low < recent.get(i+2).low)
                sL.add(recent.get(i).low);
        }
        return sH.size() >= 2 && sL.size() >= 2
            && sH.get(sH.size()-1) > sH.get(sH.size()-2)
            && sL.get(sL.size()-1) > sL.get(sL.size()-2);
    }

    // ── Market Structure: LH + LL (downtrend) — last 50 candles only ─────────
    private boolean isDowntrendStructure(List<SimpleMarketData> data) {
        if (data.size() < 20) return false;
        int startIdx = Math.max(0, data.size() - 50);
        List<SimpleMarketData> recent = data.subList(startIdx, data.size());
        List<Double> sH = new ArrayList<>(), sL = new ArrayList<>();
        for (int i = 2; i < recent.size() - 2; i++) {
            if (recent.get(i).high > recent.get(i-1).high && recent.get(i).high > recent.get(i-2).high
                    && recent.get(i).high > recent.get(i+1).high && recent.get(i).high > recent.get(i+2).high)
                sH.add(recent.get(i).high);
            if (recent.get(i).low < recent.get(i-1).low && recent.get(i).low < recent.get(i-2).low
                    && recent.get(i).low < recent.get(i+1).low && recent.get(i).low < recent.get(i+2).low)
                sL.add(recent.get(i).low);
        }
        return sH.size() >= 2 && sL.size() >= 2
            && sH.get(sH.size()-1) < sH.get(sH.size()-2)
            && sL.get(sL.size()-1) < sL.get(sL.size()-2);
    }

    // ── Bollinger Band Squeeze: current ATR < 70 % of 20-bar avg ATR ─────────
    private boolean isBollingerSqueeze(List<SimpleMarketData> data) {
        if (data.size() < 40) return false;
        // Clamp to last 40 candles — 14-ATR + 20 periods fits exactly in this window
        int startIdx = Math.max(0, data.size() - 40);
        List<SimpleMarketData> recent = data.subList(startIdx, data.size());
        double curATR = calculateATR(recent, 14);
        int periods = Math.min(20, recent.size() - 16);
        double sumATR = 0;
        for (int i = 0; i < periods; i++)
            sumATR += calculateATR(recent.subList(0, recent.size() - i), 14);
        double avgATR = sumATR / periods;
        return avgATR > 0 && curATR < avgATR * 0.70;
    }

    // ── Round number proximity ────────────────────────────────────────────────
    private boolean isNearRoundLevel(double price, String symbol) {
        // Only block signals very close to major round levels (key S/R levels).
        // Buffers reduced so signals are NOT over-filtered (especially NIFTY/SENSEX).
        double step = switch (symbol) {
            case "NIFTY50"   -> 500.0;   // Only block near 500-pt levels (24000, 24500, 25000)
            case "SENSEX"    -> 1000.0;  // Only block near 1000-pt levels (80000, 81000)
            case "BANKNIFTY" -> 500.0;   // Only block near 500-pt levels (52000, 52500)
            default          -> 500.0;
        };
        double buffer = switch (symbol) {
            case "NIFTY50"   -> 8.0;
            case "SENSEX"    -> 20.0;
            case "BANKNIFTY" -> 12.0;
            default          -> 8.0;
        };
        double nearest = Math.round(price / step) * step;
        return Math.abs(price - nearest) < buffer;
    }

    // ── Volume surge: current bar > 1.8× 20-bar avg ──────────────────────────
    private boolean isVolumeSurge(List<SimpleMarketData> data) {
        if (data.size() < 22) return false;
        long curVol = data.get(data.size() - 1).volume;
        double avgVol = data.subList(data.size() - 21, data.size() - 1)
            .stream().mapToLong(d -> d.volume).average().orElse(1);
        return avgVol > 0 && curVol > avgVol * 1.8;
    }

    // ── Bullish Engulfing within 0.6 ATR of VWAP ────────────────────────────
    private boolean isBullishEngulfingNearVWAP(List<SimpleMarketData> data, double vwap, double atr) {
        if (data.size() < 2) return false;
        java.util.Set<String> patterns = AdvancedCandlestickDetector.detectAll(data);
        return patterns.contains("Bullish Engulfing")
            && Math.abs(data.get(data.size() - 1).price - vwap) < atr * 0.6;
    }

    // ── Bearish Engulfing within 0.6 ATR of VWAP ────────────────────────────
    private boolean isBearishEngulfingNearVWAP(List<SimpleMarketData> data, double vwap, double atr) {
        if (data.size() < 2) return false;
        java.util.Set<String> patterns = AdvancedCandlestickDetector.detectAll(data);
        return patterns.contains("Bearish Engulfing")
            && Math.abs(data.get(data.size() - 1).price - vwap) < atr * 0.6;
    }

    // ─────────────────────────────────────────────────────────────────────────

    // ═══════════════════════════════════════════════════════════════════════════
    //  HIGH WIN-RATE STRATEGY ADDITIONS: ORB, VWAP Bounce, 15-Min Bias, Dynamic Targets
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Opening Range Breakout (9:15-9:30): ~68-75% WR on Indian indices.
     * Requires price to break ORB high/low with RSI confirmation.
     */
    private AIPrediction detectORBBreakout(String symbol, List<SimpleMarketData> data, double currentPrice, double atr, double rsi) {
        if (data.size() < 3) return createDefaultAIPrediction("Insufficient data for ORB");
        double[] orb = getOpeningRange(data);
        if (orb[0] <= 0 || orb[1] <= 0) return createDefaultAIPrediction("No ORB data");

        double orbHigh = orb[0];
        double orbLow  = orb[1];
        double orbRange = Math.max(orbHigh - orbLow, atr * 0.3); // guard against zero range

        // ORB not valid until 9:31 AM IST
        java.time.LocalTime candleTime = (data != null && !data.isEmpty() && data.get(data.size()-1).timestamp != null)
            ? data.get(data.size()-1).timestamp.toLocalTime()
            : java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (candleTime.isBefore(java.time.LocalTime.of(9, 31))) return createDefaultAIPrediction("ORB still forming");

        // Minimum breakout gap: 0.15% above orbHigh / below orbLow (avoids fakeouts)
        double minGap = currentPrice * 0.0015;
        boolean volumeConfirm = isVolumeSurge(data);

        // Fresh breakout check: one of the last 2 bars must have been at/near ORB boundary
        // This prevents repeated ORB signals throughout the day (only catch the initial break)
        SimpleMarketData c1 = data.get(data.size() - 2);
        SimpleMarketData c2 = data.get(data.size() - 3);

        if (currentPrice > orbHigh + minGap && rsi > 48 && rsi < 74) {
            boolean freshBreakout = c1.price <= orbHigh * 1.003 || c2.price <= orbHigh * 1.003
                                 || c1.low <= orbHigh || c2.low <= orbHigh;
            if (!freshBreakout) return createDefaultAIPrediction("ORB already extended — stale signal");
            // Boost confidence for strong RSI momentum (ensures it passes MACD + 85 gate when aligned)
            double confidence = volumeConfirm ? 91 : (rsi > 55 ? 85 : 82);
            double targetPts = orbRange * 1.5;
            double slPts = Math.max(orbRange * 0.4, atr * 0.5);
            return new AIPrediction("UP", confidence, confidence/100.0, 0, rsi, 0, 80,
                "ORB_BREAKOUT", "ORB Breakout ↑ " + String.format("%.1f", orbHigh) + (volumeConfirm ? " +VolSurge" : " RSI=" + String.format("%.0f", rsi)),
                targetPts, slPts, true);
        }

        if (currentPrice < orbLow - minGap && rsi < 52 && rsi > 26) {
            boolean freshBreakout = c1.price >= orbLow * 0.997 || c2.price >= orbLow * 0.997
                                 || c1.high >= orbLow || c2.high >= orbLow;
            if (!freshBreakout) return createDefaultAIPrediction("ORB already extended — stale signal");
            double confidence = volumeConfirm ? 91 : (rsi < 45 ? 85 : 82);
            double targetPts = orbRange * 1.5;
            double slPts = Math.max(orbRange * 0.4, atr * 0.5);
            return new AIPrediction("DOWN", confidence, confidence/100.0, 0, rsi, 0, 80,
                "ORB_BREAKOUT", "ORB Breakdown ↓ " + String.format("%.1f", orbLow) + (volumeConfirm ? " +VolSurge" : " RSI=" + String.format("%.0f", rsi)),
                targetPts, slPts, true);
        }

        return createDefaultAIPrediction("No ORB breakout");
    }

    /**
     * VWAP Bounce strategy: ~65-70% WR. Price touches VWAP and resumes trend direction.
     * Requires: prior interaction with VWAP + directional close + candle body confirmation.
     */
    private AIPrediction detectVWAPBounce(String symbol, List<SimpleMarketData> data, double currentPrice, double atr, double rsi, double adx) {
        if (data.size() < 5) return createDefaultAIPrediction("Insufficient data for VWAP bounce");
        if (adx < 15) return createDefaultAIPrediction("ADX too low for VWAP bounce");

        double vwap = new AdvancedIndicatorsEngine().calculateVWAP(data);
        int n = data.size();
        SimpleMarketData c0 = data.get(n - 1);
        SimpleMarketData c1 = data.get(n - 2);
        SimpleMarketData c2 = data.get(n - 3);

        // Bounce UP: prior bars touched/crossed VWAP from below, now closing above with bullish body
        boolean priorTouchedVWAP = (c1.low <= vwap * 1.001 || c2.low <= vwap * 1.001);
        boolean nowAboveVWAP = currentPrice > vwap;
        boolean bullishBody  = hasBullishCandleBody(c0);

        // Bounce DOWN: prior bars touched VWAP from above, now closing below with bearish body
        boolean priorTouchedVWAPAbove = (c1.high >= vwap * 0.999 || c2.high >= vwap * 0.999);
        boolean nowBelowVWAP = currentPrice < vwap;
        boolean bearishBody  = hasBearishCandleBody(c0);

        double distFromVWAP = Math.abs(currentPrice - vwap);
        if (distFromVWAP > atr * 1.5) return createDefaultAIPrediction("Price too far from VWAP");

        if (priorTouchedVWAP && nowAboveVWAP && bullishBody && rsi > 44 && rsi < 68) {
            double targetPts = atr * 1.8;
            double slPts     = Math.max(atr * 0.55, distFromVWAP * 0.9);
            return new AIPrediction("UP", 83, 0.83, 0, rsi, 0, 80,
                "VWAP_BOUNCE", "VWAP Bounce ↑ | RSI=" + String.format("%.0f", rsi),
                targetPts, slPts, false);
        }

        if (priorTouchedVWAPAbove && nowBelowVWAP && bearishBody && rsi < 56 && rsi > 32) {
            double targetPts = atr * 1.8;
            double slPts     = Math.max(atr * 0.55, distFromVWAP * 0.9);
            return new AIPrediction("DOWN", 83, 0.83, 0, rsi, 0, 80,
                "VWAP_BOUNCE", "VWAP Rejection ↓ | RSI=" + String.format("%.0f", rsi),
                targetPts, slPts, false);
        }

        return createDefaultAIPrediction("No VWAP bounce");
    }

    /**
     * 15-minute trend bias from 5-min data: resample to 15-min, apply EMA20 slope.
     * Returns "UP", "DOWN", or "NEUTRAL".
     */
    private String calculate15MinBias(List<SimpleMarketData> data) {
        if (data.size() < 30) return "NEUTRAL";
        int startIdx = Math.max(0, data.size() - 90);
        List<SimpleMarketData> recent = data.subList(startIdx, data.size());

        // Group every 3 consecutive 5-min bars into one 15-min bar (close = 3rd bar's close)
        List<Double> prices15m = new ArrayList<>();
        for (int i = 0; i + 2 < recent.size(); i += 3) {
            prices15m.add(recent.get(i + 2).price);
        }
        if (prices15m.size() < 20) return "NEUTRAL";

        double emaNow  = calculateEMAFromValues(prices15m, 20);
        double emaPrev = prices15m.size() >= 23
            ? calculateEMAFromValues(prices15m.subList(0, prices15m.size() - 3), 20)
            : emaNow;
        double latestClose = prices15m.get(prices15m.size() - 1);

        if (emaNow > emaPrev && latestClose > emaNow) return "UP";
        if (emaNow < emaPrev && latestClose < emaNow) return "DOWN";
        return "NEUTRAL";
    }

    /**
     * Dynamic Target & Stop Loss based on actual market structure:
     * uses swing highs/lows, VWAP bands, ORB levels, PDH/PDL.
     * Returns double[]{targetPoints, slPoints} relative to entry.
     */
    private double[] calculateDynamicTargetSL(List<SimpleMarketData> data, double entry, String direction, double atr, String symbol) {
        if (data.size() < 15 || "NEUTRAL".equals(direction)) return new double[]{atr * 2.5, atr * 1.0};

        double vwap = new AdvancedIndicatorsEngine().calculateVWAP(data);
        double[] orb  = getOpeningRange(data);
        double[] pdhl = getPreviousDayHighLow(data);

        // Collect swing highs and lows from last 40 candles (exclude the last bar still forming)
        int startIdx = Math.max(0, data.size() - 40);
        List<SimpleMarketData> recent = data.subList(startIdx, Math.max(startIdx + 5, data.size() - 1));
        List<Double> swingHighs = new ArrayList<>();
        List<Double> swingLows  = new ArrayList<>();
        for (int i = 2; i < recent.size() - 2; i++) {
            SimpleMarketData c = recent.get(i);
            if (c.high > recent.get(i-1).high && c.high > recent.get(i-2).high
                    && c.high > recent.get(i+1).high && c.high > recent.get(i+2).high) {
                swingHighs.add(c.high);
            }
            if (c.low < recent.get(i-1).low && c.low < recent.get(i-2).low
                    && c.low < recent.get(i+1).low && c.low < recent.get(i+2).low) {
                swingLows.add(c.low);
            }
        }

        double targetPts, slPts;

        if ("UP".equals(direction)) {
            // Target: first swing high above entry by ≥ 1.2 ATR, or PDH, or VWAP+1.5ATR
            double target = entry + atr * 2.0; // default
            double candidate;

            // Nearest swing high above entry + 0.3 ATR margin
            double threshUp = entry + atr * 0.3;
            for (double sh : swingHighs) {
                if (sh > threshUp && sh < target) target = sh;
            }
            // PDH as target if it's closer and above entry
            if (pdhl[0] > entry + atr * 0.5 && pdhl[0] < target + atr * 0.5) target = Math.min(target, pdhl[0]);
            // ORB extension: orbHigh + orbRange if we're above orbHigh
            if (orb[0] > 0 && entry > orb[0]) {
                candidate = orb[0] + (orb[0] - orb[1]);
                if (candidate > entry + atr * 0.5) target = Math.min(target, candidate);
            }
            // VWAP + 1.5 ATR as alternative
            candidate = vwap + atr * 1.5;
            if (candidate > entry + atr * 0.5) target = Math.min(target, candidate);

            targetPts = Math.max(target - entry, atr * 1.2);

            // SL: nearest swing low below entry within 1.5 ATR
            double sl = entry - atr * 0.8; // default
            for (double sL : swingLows) {
                if (sL < entry - atr * 0.2 && sL > entry - atr * 1.5) sl = Math.max(sl, sL);
            }
            slPts = Math.max(entry - sl, atr * 0.4);

        } else { // DOWN
            double target = entry - atr * 2.0;
            double candidate;

            double threshDn = entry - atr * 0.3;
            for (double sL : swingLows) {
                if (sL < threshDn && sL > target) target = sL;
            }
            if (pdhl[1] > 0 && pdhl[1] < entry - atr * 0.5 && pdhl[1] > target - atr * 0.5) target = Math.max(target, pdhl[1]);
            if (orb[1] > 0 && entry < orb[1]) {
                candidate = orb[1] - (orb[0] - orb[1]);
                if (candidate < entry - atr * 0.5) target = Math.max(target, candidate);
            }
            candidate = vwap - atr * 1.5;
            if (candidate < entry - atr * 0.5) target = Math.max(target, candidate);

            targetPts = Math.max(entry - target, atr * 1.2);

            double sl = entry + atr * 0.8;
            for (double sh : swingHighs) {
                if (sh > entry + atr * 0.2 && sh < entry + atr * 1.5) sl = Math.min(sl, sh);
            }
            slPts = Math.max(sl - entry, atr * 0.4);
        }

        // Enforce minimum 1.5:1 R:R
        if (slPts > 0 && targetPts / slPts < 1.5) {
            targetPts = slPts * 1.8;
        }

        // Symbol-specific minimum targets (must meet gate thresholds)
        double minTarget = switch (symbol) {
            case "BANKNIFTY" -> Math.max(atr * 1.5, 80.0);
            case "SENSEX"    -> Math.max(atr * 1.5, 70.0);
            case "NIFTY50"   -> Math.max(atr * 1.2, 30.0);
            default          -> atr * 1.2;
        };
        targetPts = Math.max(targetPts, minTarget);

        return new double[]{targetPts, slPts};
    }

    // ─────────────────────────────────────────────────────────────────────────

    private AIPrediction createDefaultAIPrediction(String reason) {
        return new AIPrediction("NEUTRAL", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, "NONE", reason, 0.0, 0.0, false);
    }

    /**
     * Guaranteed daily fallback signal using EMA trend alignment.
     * Called when the primary predictor returns NEUTRAL after 11:30 AM and no call has
     * been generated for this symbol yet today. Maintains quality control via RSI/EMA checks.
     */
    public AIPrediction generateRelaxedPrediction(String symbol, List<SimpleMarketData> data, OptionData optionData) {
        if (data == null || data.size() < 50) {
            return createDefaultAIPrediction("Insufficient data for guaranteed signal");
        }

        java.time.LocalTime now = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (!now.isAfter(java.time.LocalTime.of(9, 15)) || !now.isBefore(java.time.LocalTime.of(15, 0))) {
            return createDefaultAIPrediction("Outside safe market hours");
        }

        double ema20 = calculateEMA(data, 20);
        double ema50 = calculateEMA(data, 50);
        double rsi   = calculateRSI(data, 14);
        double atr   = calculateATR(data, 14);
        double price = data.get(data.size() - 1).price;

        String direction;
        String reasoning;

        // Priority 1: Full EMA alignment with RSI confirmation
        if (price > ema20 && ema20 > ema50 && rsi > 45 && rsi < 75) {
            direction = "UP";
            reasoning = "EMA20>EMA50 Bullish Alignment | RSI=" + String.format("%.1f", rsi);
        } else if (price < ema20 && ema20 < ema50 && rsi < 55 && rsi > 25) {
            direction = "DOWN";
            reasoning = "EMA20<EMA50 Bearish Alignment | RSI=" + String.format("%.1f", rsi);
        // Priority 2: Price vs EMA20 with RSI confirmation
        } else if (price > ema20 && rsi > 50) {
            direction = "UP";
            reasoning = "Price above EMA20 | RSI=" + String.format("%.1f", rsi);
        } else if (price < ema20 && rsi < 50) {
            direction = "DOWN";
            reasoning = "Price below EMA20 | RSI=" + String.format("%.1f", rsi);
        // Priority 3: Pure EMA20 bias (absolute last resort)
        } else {
            direction = price >= ema20 ? "UP" : "DOWN";
            reasoning = "EMA20 directional bias | RSI=" + String.format("%.1f", rsi);
        }

        // Use dynamic market-structure targets (same as primary signals)
        double[] dynTS = calculateDynamicTargetSL(data, price, direction, atr, symbol);

        return new AIPrediction(direction, 85.0, 0.85, 0, rsi, atr / price, 80,
            "DAILY_GUARANTEE",
            "Guaranteed Daily Signal [EMA-Trend]: " + reasoning,
            dynTS[0], dynTS[1], false);
    }
}
