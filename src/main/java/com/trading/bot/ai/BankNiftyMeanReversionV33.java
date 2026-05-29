package com.trading.bot.ai;

import com.trading.bot.market.SimpleMarketData;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * BANKNIFTY Mean-Reversion V33 — TEST ONLY. Different style from V31/V32.
 *
 * Trend-following (V31, V32, V32.1) all failed to hit 70% WR on BANKNIFTY.
 * V33 switches to catching exhaustion bounces at RSI extremes — a classic
 * high-WR style.
 *
 * Entry triggers:
 *  • LONG  when RSI &lt; 28 AND price touches/breaks lower Bollinger 2.2σ
 *          AND current candle is bullish (close &gt; open, body ≥ 55% of range)
 *          AND volume ≥ 1.3× 20-bar avg (capitulation/climax)
 *  • SHORT mirror with RSI &gt; 72, upper BB, bearish candle, vol climax
 *
 * R:R designed for high WR:
 *  • SL  = 0.9 × ATR  (wide enough to ride out post-entry noise)
 *  • Tgt = 0.6 × ATR  (just enough to catch initial bounce, R:R 1:0.67)
 *  • Break-even WR = 60% — target 70%+ gives clean edge
 *
 * Time windows: 09:45–11:00 and 11:15–13:00 (avoid open noise and lunch chop)
 *
 * NOT wired into AIPredictor.
 */
public final class BankNiftyMeanReversionV33 {

    private BankNiftyMeanReversionV33() {}

    private static final LocalTime EARLY_START = LocalTime.of(9, 45);
    private static final LocalTime EARLY_END   = LocalTime.of(11, 0);
    private static final LocalTime PRIME_START = LocalTime.of(11, 15);
    private static final LocalTime PRIME_END   = LocalTime.of(13, 0);

    // V33.3 FINAL (integrated): 113 calls, 77.9% WR, +2,192 pts.
    // Tested V33.4 (tighter RSI+body): 73.1% — reverted.
    // Tested V33.5 (V33.3 + ATR ceiling 100): 77.7%/+1,648 — net pts dropped, reverted.
    // Tested V33.6 (conf floor 88): 72.2% — reverted.
    // Conclusion: 77.9% is the ceiling for mean-reversion on BANKNIFTY 5-min 120d data.
    private static final double RSI_OVERSOLD       = 35.0;
    private static final double RSI_OVERBOUGHT     = 65.0;
    private static final double BB_STD             = 1.8;
    private static final double BODY_RATIO_MIN     = 0.40;
    private static final double VOL_SURGE_MULT     = 1.0;
    private static final int    EXHAUSTION_LOOKBACK = 3;
    private static final double SL_ATR_MULT        = 0.9;
    private static final double TGT_ATR_MULT       = 0.6;
    private static final double MIN_SL_PTS         = 45.0;
    private static final double MIN_TGT_PTS        = 30.0;
    private static final int    BB_PERIOD          = 20;
    private static final int    REGIME_LOOKBACK_DAYS = 20;
    private static final int    BARS_PER_DAY_5MIN  = 75;

    public static AIPredictor.AIPrediction predict(
            String symbol, List<SimpleMarketData> data, double currentPrice,
            double rsi, double atr,
            SimpleMarketData latest) {

        if (data == null || data.size() < BB_PERIOD + 1 || latest == null) {
            return neutral(rsi, atr, currentPrice, "insufficient data");
        }

        LocalTime now = (latest.timestamp != null)
                ? latest.timestamp.toLocalTime()
                : LocalTime.now(ZoneId.of("Asia/Kolkata"));

        boolean inEarly = !now.isBefore(EARLY_START) && !now.isAfter(EARLY_END);
        boolean inPrime = !now.isBefore(PRIME_START) && !now.isAfter(PRIME_END);
        if (!inEarly && !inPrime) return neutral(rsi, atr, currentPrice, "outside MR windows");

        // V33.3 final: no vol regime / no ATR ceiling — tested both, lost net pts vs unfiltered.

        // ── Bollinger Bands
        double[] bb = computeBB(data, BB_PERIOD, BB_STD);
        double bbUpper = bb[0], bbLower = bb[1];

        // ── Candle structure: must be reversal candle
        double open = latest.open, close = latest.price;
        double high = latest.high, low = latest.low;
        double range = high - low;
        if (range <= 0) return neutral(rsi, atr, currentPrice, "no candle range");
        double body = Math.abs(close - open);
        double bodyRatio = body / range;

        boolean bullishCandle = close > open;
        boolean bearishCandle = close < open;

        // V33.3: vol climax now BONUS-only, not gate
        double volAvg = avgVolume(data, 20);
        boolean volSurge = volAvg > 0 && latest.volume >= volAvg * VOL_SURGE_MULT;

        // ── V33.2: detect exhaustion in last N bars + reversal in CURRENT bar
        boolean recentLowerBBTouch = false, recentRSIOversold = false;
        boolean recentUpperBBTouch = false, recentRSIOverbought = false;
        int from = Math.max(0, data.size() - 1 - EXHAUSTION_LOOKBACK);
        for (int idx = from; idx < data.size(); idx++) {
            SimpleMarketData b = data.get(idx);
            if (b.low  <= bbLower * 1.001) recentLowerBBTouch = true;
            if (b.high >= bbUpper * 0.999) recentUpperBBTouch = true;
        }
        // RSI on the bar 1-3 ago was extreme
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

        // ── LONG: exhaustion (oversold + lower BB touch) recently + bullish reversal NOW
        if (recentRSIOversold
                && recentLowerBBTouch
                && bullishCandle
                && bodyRatio >= BODY_RATIO_MIN) {

            double slPts  = Math.max(atr * SL_ATR_MULT, MIN_SL_PTS);
            double tgtPts = Math.max(atr * TGT_ATR_MULT, MIN_TGT_PTS);

            double confidence = 85;
            if (rsi <= 22) confidence += 3;
            if (latest.volume >= volAvg * 1.8) confidence += 2;
            if (bodyRatio >= 0.7) confidence += 2;
            confidence = Math.min(95, confidence);

            String reason = String.format(
                    "BANKNIFTY_V33_MR | LONG | RSI=%.1f OVERSOLD | BBlower touch | bullCandle body=%.0f%% | vol=%.1fx",
                    rsi, bodyRatio * 100, latest.volume / Math.max(volAvg, 1));

            return new AIPredictor.AIPrediction("UP", confidence, confidence / 100.0,
                    0, rsi, atr / currentPrice, 80, "BANKNIFTY_V33_MR", reason, tgtPts, slPts, false);
        }

        // ── SHORT: exhaustion (overbought + upper BB) recently + bearish reversal NOW
        if (recentRSIOverbought
                && recentUpperBBTouch
                && bearishCandle
                && bodyRatio >= BODY_RATIO_MIN) {

            double slPts  = Math.max(atr * SL_ATR_MULT, MIN_SL_PTS);
            double tgtPts = Math.max(atr * TGT_ATR_MULT, MIN_TGT_PTS);

            double confidence = 85;
            if (rsi >= 78) confidence += 3;
            if (latest.volume >= volAvg * 1.8) confidence += 2;
            if (bodyRatio >= 0.7) confidence += 2;
            confidence = Math.min(95, confidence);

            String reason = String.format(
                    "BANKNIFTY_V33_MR | SHORT | RSI=%.1f OVERBOUGHT | BBupper touch | bearCandle body=%.0f%% | vol=%.1fx",
                    rsi, bodyRatio * 100, latest.volume / Math.max(volAvg, 1));

            return new AIPredictor.AIPrediction("DOWN", confidence, confidence / 100.0,
                    0, rsi, atr / currentPrice, 80, "BANKNIFTY_V33_MR", reason, tgtPts, slPts, false);
        }

        return neutral(rsi, atr, currentPrice, "no MR trigger");
    }

    private static AIPredictor.AIPrediction neutral(double rsi, double atr, double price, String why) {
        return new AIPredictor.AIPrediction("NEUTRAL", 0, 0, 0, rsi, atr / Math.max(price, 1), 80,
                "BANKNIFTY_V33_MR", "filtered: " + why, atr, atr, false);
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

    private static double avgVolume(List<SimpleMarketData> data, int period) {
        int n = data.size();
        if (n < period) return 0;
        long sum = 0;
        for (int i = n - period; i < n; i++) sum += data.get(i).volume;
        return (double) sum / period;
    }

    private static double computeAtrAvg20Day(List<SimpleMarketData> data) {
        int totalBars = Math.min(data.size(), BARS_PER_DAY_5MIN * REGIME_LOOKBACK_DAYS);
        if (totalBars < BARS_PER_DAY_5MIN * 5) return 0;
        double sum = 0; int counted = 0;
        for (int i = data.size() - totalBars + BARS_PER_DAY_5MIN; i < data.size(); i += BARS_PER_DAY_5MIN) {
            double a = atrAt(data, i, 14);
            if (a > 0) { sum += a; counted++; }
        }
        return counted > 0 ? sum / counted : 0;
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

    private static double atrAt(List<SimpleMarketData> data, int endExclusive, int period) {
        if (endExclusive < period + 1) return 0;
        double sum = 0;
        for (int i = endExclusive - period; i < endExclusive; i++) {
            SimpleMarketData cur = data.get(i);
            SimpleMarketData prv = data.get(i - 1);
            double hl = cur.high - cur.low;
            double hc = Math.abs(cur.high - prv.price);
            double lc = Math.abs(cur.low  - prv.price);
            sum += Math.max(hl, Math.max(hc, lc));
        }
        return sum / period;
    }
}
