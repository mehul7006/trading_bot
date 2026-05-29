package com.trading.bot.ai;

import com.trading.bot.market.SimpleMarketData;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * NIFTY Mean-Reversion V33 — TEST ONLY. NIFTY-tuned mirror of {@link BankNiftyMeanReversionV33}.
 *
 * Designed as a SECONDARY entry path that fires when {@link NiftyStrategyV31} returns
 * NEUTRAL (no trend-pullback setup). Catches RSI-extreme exhaustion bounces on
 * ranging/choppy days — same approach that delivered 77.9% WR on BANKNIFTY.
 *
 * Constants vs BANKNIFTY V33.3 (BANKNIFTY ~52k vs NIFTY ~24k → ~0.45× absolute scale):
 *  • MIN_SL_PTS  = 22  (BANKNIFTY: 45)
 *  • MIN_TGT_PTS = 15  (BANKNIFTY: 30)
 *  • All other parameters identical
 *
 * NOT wired into AIPredictor. Run via {@code Backtest180DayMultipath} to validate WR
 * before integration.
 */
public final class NiftyMeanReversionV33 {

    private NiftyMeanReversionV33() {}

    private static final LocalTime EARLY_START = LocalTime.of(9, 45);
    private static final LocalTime EARLY_END   = LocalTime.of(11, 0);
    private static final LocalTime PRIME_START = LocalTime.of(11, 15);
    private static final LocalTime PRIME_END   = LocalTime.of(13, 0);

    private static final double RSI_OVERSOLD       = 35.0;
    private static final double RSI_OVERBOUGHT     = 65.0;
    private static final double BB_STD             = 1.8;
    private static final double BODY_RATIO_MIN     = 0.40;
    private static final double SL_ATR_MULT        = 0.9;
    private static final double TGT_ATR_MULT       = 0.6;
    private static final double MIN_SL_PTS         = 22.0;
    private static final double MIN_TGT_PTS        = 15.0;
    private static final int    BB_PERIOD          = 20;
    private static final int    EXHAUSTION_LOOKBACK = 3;

    public static AIPredictor.AIPrediction predict(
            String symbol, List<SimpleMarketData> data, double currentPrice,
            double rsi, double atr, SimpleMarketData latest) {

        if (data == null || data.size() < BB_PERIOD + 1 || latest == null) {
            return neutral(rsi, atr, currentPrice, "insufficient data");
        }

        LocalTime now = (latest.timestamp != null)
                ? latest.timestamp.toLocalTime()
                : LocalTime.now(ZoneId.of("Asia/Kolkata"));

        boolean inEarly = !now.isBefore(EARLY_START) && !now.isAfter(EARLY_END);
        boolean inPrime = !now.isBefore(PRIME_START) && !now.isAfter(PRIME_END);
        if (!inEarly && !inPrime) return neutral(rsi, atr, currentPrice, "outside MR windows");

        double[] bb = computeBB(data, BB_PERIOD, BB_STD);
        double bbUpper = bb[0], bbLower = bb[1];

        double open = latest.open, close = latest.price;
        double high = latest.high, low = latest.low;
        double range = high - low;
        if (range <= 0) return neutral(rsi, atr, currentPrice, "no candle range");
        double body = Math.abs(close - open);
        double bodyRatio = body / range;

        boolean bullishCandle = close > open;
        boolean bearishCandle = close < open;

        // ── Detect exhaustion in last 3 bars + reversal in CURRENT bar
        boolean recentLowerBBTouch = false, recentRSIOversold = false;
        boolean recentUpperBBTouch = false, recentRSIOverbought = false;
        int from = Math.max(0, data.size() - 1 - EXHAUSTION_LOOKBACK);
        for (int idx = from; idx < data.size(); idx++) {
            SimpleMarketData b = data.get(idx);
            if (b.low  <= bbLower * 1.001) recentLowerBBTouch = true;
            if (b.high >= bbUpper * 0.999) recentUpperBBTouch = true;
        }
        for (int back = 1; back <= EXHAUSTION_LOOKBACK && back < data.size(); back++) {
            int upTo = data.size() - back;
            if (upTo > 15) {
                double rsiBack = rsiAt(data, upTo, 14);
                if (rsiBack <= RSI_OVERSOLD)   recentRSIOversold   = true;
                if (rsiBack >= RSI_OVERBOUGHT) recentRSIOverbought = true;
            }
        }
        if (rsi <= RSI_OVERSOLD)   recentRSIOversold   = true;
        if (rsi >= RSI_OVERBOUGHT) recentRSIOverbought = true;

        // LONG: exhaustion (oversold + lower BB) recently + bullish reversal NOW
        if (recentRSIOversold && recentLowerBBTouch && bullishCandle && bodyRatio >= BODY_RATIO_MIN) {
            double slPts  = Math.max(atr * SL_ATR_MULT, MIN_SL_PTS);
            double tgtPts = Math.max(atr * TGT_ATR_MULT, MIN_TGT_PTS);

            double confidence = 85;
            if (rsi <= 30) confidence += 3;
            if (bodyRatio >= 0.55) confidence += 2;
            confidence = Math.min(95, confidence);

            String reason = String.format(
                    "NIFTY_V33_MR | LONG | RSI=%.1f | BBlower | bull body=%.0f%%",
                    rsi, bodyRatio * 100);
            return new AIPredictor.AIPrediction("UP", confidence, confidence / 100.0,
                    0, rsi, atr / currentPrice, 80, "NIFTY_V33_MR", reason, tgtPts, slPts, false);
        }

        // SHORT: exhaustion (overbought + upper BB) recently + bearish reversal NOW
        if (recentRSIOverbought && recentUpperBBTouch && bearishCandle && bodyRatio >= BODY_RATIO_MIN) {
            double slPts  = Math.max(atr * SL_ATR_MULT, MIN_SL_PTS);
            double tgtPts = Math.max(atr * TGT_ATR_MULT, MIN_TGT_PTS);

            double confidence = 85;
            if (rsi >= 70) confidence += 3;
            if (bodyRatio >= 0.55) confidence += 2;
            confidence = Math.min(95, confidence);

            String reason = String.format(
                    "NIFTY_V33_MR | SHORT | RSI=%.1f | BBupper | bear body=%.0f%%",
                    rsi, bodyRatio * 100);
            return new AIPredictor.AIPrediction("DOWN", confidence, confidence / 100.0,
                    0, rsi, atr / currentPrice, 80, "NIFTY_V33_MR", reason, tgtPts, slPts, false);
        }

        return neutral(rsi, atr, currentPrice, "no MR trigger");
    }

    private static AIPredictor.AIPrediction neutral(double rsi, double atr, double price, String why) {
        return new AIPredictor.AIPrediction("NEUTRAL", 0, 0, 0, rsi, atr / Math.max(price, 1), 80,
                "NIFTY_V33_MR", "filtered: " + why, atr, atr, false);
    }

    private static double[] computeBB(List<SimpleMarketData> data, int period, double mult) {
        int n = data.size();
        if (n < period) return new double[]{Double.MAX_VALUE, 0};
        double sum = 0;
        for (int i = n - period; i < n; i++) sum += data.get(i).price;
        double mean = sum / period;
        double var = 0;
        for (int i = n - period; i < n; i++) var += Math.pow(data.get(i).price - mean, 2);
        double sd = Math.sqrt(var / period);
        return new double[]{mean + mult * sd, mean - mult * sd};
    }

    private static double rsiAt(List<SimpleMarketData> data, int endExclusive, int period) {
        if (endExclusive < period + 1) return 50;
        double gain = 0, loss = 0;
        for (int i = endExclusive - period; i < endExclusive; i++) {
            double ch = data.get(i).price - data.get(i - 1).price;
            if (ch > 0) gain += ch; else loss -= ch;
        }
        if (loss == 0) return 100;
        double rs = (gain / period) / (loss / period);
        return 100 - 100 / (1 + rs);
    }
}
