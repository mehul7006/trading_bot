package com.trading.bot.ai;

import com.trading.bot.market.SimpleMarketData;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * SENSEX Strategy V31 — high-WR, trend-pullback, multi-timeframe.
 *
 * Replaces the V29 SENSEX block (AIPredictor.predictSensexStrategy lines 373-511) which
 * produced 33% WR over 60 days due to (a) counter-trend ORB requirement,
 * (b) structurally bad R:R via min-points gate, (c) uncalibrated 99% confidence.
 *
 * V31 design:
 *  • Trade WITH EMA200/EMA50 trend (no counter-trend ORB)
 *  • Require 5-min + 15-min + 60-min bias alignment
 *  • Pullback entries (price retests EMA20) preferred over breakout chase
 *  • Volatility regime filter: skip ATR ratio &lt; 0.4× or &gt; 2.5× of 20-day avg
 *  • Time windows: 09:45–11:00 (momentum) + 11:15–13:00 (continuation) + 14:30–15:00 (close, ADX&gt;30 only)
 *  • R:R fixed 1:1.5 — SL = max(0.6×ATR, 50 pts), Target = 1.5 × SL
 *  • Confidence calibrated by confluence count (4/5 = 80, 5/5 = 90, +window bonus)
 *  • Skip Thursday after 13:30 (expiry strategy owns that slot)
 *  • Skip RSI extremes (UP at RSI&gt;65, DOWN at RSI&lt;35) to avoid mean-reversion traps
 */
public final class SensexStrategyV31 {

    private SensexStrategyV31() {}

    // ── Time windows (IST)
    private static final LocalTime EARLY_START = LocalTime.of(9, 45);
    private static final LocalTime EARLY_END   = LocalTime.of(11, 0);
    private static final LocalTime PRIME_START = LocalTime.of(11, 15);
    private static final LocalTime PRIME_END   = LocalTime.of(13, 0);
    private static final LocalTime CLOSE_START = LocalTime.of(14, 30);
    private static final LocalTime CLOSE_END   = LocalTime.of(15, 0);
    private static final LocalTime THURSDAY_CUTOFF = LocalTime.of(13, 30);

    // ── Strategy params
    private static final double MIN_ADX_PRIME     = 22.0;
    private static final double MIN_ADX_EARLY     = 25.0;
    private static final double MIN_ADX_CLOSE     = 30.0;
    /** Above this, trend is overextended — historical 30% WR on iter1. */
    private static final double MAX_ADX           = 45.0;
    private static final double SL_ATR_MULT       = 0.6;
    private static final double TGT_RR_MULT       = 1.5;  // Target = 1.5 × SL
    private static final double MIN_SL_PTS        = 50.0;
    private static final double MIN_TGT_PTS       = 75.0;
    private static final double MAX_RSI_UP        = 65.0;
    private static final double MIN_RSI_UP        = 45.0;
    private static final double MAX_RSI_DOWN      = 55.0;
    private static final double MIN_RSI_DOWN      = 35.0;
    private static final double VOL_REGIME_MIN    = 0.4;
    private static final double VOL_REGIME_MAX    = 2.5;
    private static final double PULLBACK_TOL_PCT  = 0.003; // within 0.3% of EMA20
    private static final double TREND_SEPARATION  = 1.0005;
    private static final int    REGIME_LOOKBACK_DAYS = 20;
    private static final int    BARS_PER_DAY_5MIN = 75;

    /**
     * Main entry. Returns NEUTRAL prediction if any filter rejects.
     */
    public static AIPredictor.AIPrediction predict(
            String symbol,
            List<SimpleMarketData> data,
            double currentPrice,
            double ema20,
            double ema50,
            double ema200,
            double rsi,
            double adx,
            double atr,
            SimpleMarketData latest) {

        if (data == null || data.size() < 240 || latest == null) {
            return neutral(adx, rsi, atr, currentPrice, "insufficient data");
        }

        // ── Time gate
        LocalTime now = (latest.timestamp != null)
                ? latest.timestamp.toLocalTime()
                : LocalTime.now(ZoneId.of("Asia/Kolkata"));

        boolean inEarly = !now.isBefore(EARLY_START) && !now.isAfter(EARLY_END);
        boolean inPrime = !now.isBefore(PRIME_START) && !now.isAfter(PRIME_END);
        boolean inClose = !now.isBefore(CLOSE_START) && !now.isAfter(CLOSE_END);
        if (!inEarly && !inPrime && !inClose) {
            return neutral(adx, rsi, atr, currentPrice, "outside V31 windows");
        }
        if (inClose && adx < MIN_ADX_CLOSE) {
            return neutral(adx, rsi, atr, currentPrice, "close window needs ADX>30");
        }

        // ── Thursday expiry handover: skip after 13:30
        if (latest.timestamp != null
                && latest.timestamp.getDayOfWeek() == DayOfWeek.THURSDAY
                && now.isAfter(THURSDAY_CUTOFF)) {
            return neutral(adx, rsi, atr, currentPrice, "Thu post-13:30 expiry handover");
        }

        // ── Volatility regime
        double atrAvg = computeAtrAvg20Day(data);
        if (atrAvg > 0) {
            double ratio = atr / atrAvg;
            if (ratio < VOL_REGIME_MIN) return neutral(adx, rsi, atr, currentPrice, "dead vol");
            if (ratio > VOL_REGIME_MAX) return neutral(adx, rsi, atr, currentPrice, "extreme vol");
        }

        // ── ADX gate by window + ceiling for overextended trends
        double minAdx = inEarly ? MIN_ADX_EARLY : inClose ? MIN_ADX_CLOSE : MIN_ADX_PRIME;
        if (adx < minAdx) return neutral(adx, rsi, atr, currentPrice, "ADX<" + minAdx);
        if (adx > MAX_ADX) return neutral(adx, rsi, atr, currentPrice, "ADX>" + MAX_ADX + " overextended");

        // ── Trend filter (EMA200 + EMA50 must agree)
        boolean trendUp   = currentPrice > ema200 && ema50 > ema200;
        boolean trendDown = currentPrice < ema200 && ema50 < ema200;
        if (!trendUp && !trendDown) return neutral(adx, rsi, atr, currentPrice, "no EMA trend");

        // ── Multi-timeframe bias
        String bias5  = (currentPrice > ema20 * TREND_SEPARATION) ? "UP"
                      : (currentPrice < ema20 / TREND_SEPARATION) ? "DOWN" : "NEUTRAL";
        String bias15 = bucketBias(data, 3, 20);  // 3 × 5min = 15min, EMA20
        String bias60 = bucketBias(data, 12, 10); // 12 × 5min = 60min, EMA10

        boolean alignedUp   = "UP".equals(bias5) && "UP".equals(bias15) && !"DOWN".equals(bias60);
        boolean alignedDown = "DOWN".equals(bias5) && "DOWN".equals(bias15) && !"UP".equals(bias60);
        if (!alignedUp && !alignedDown) return neutral(adx, rsi, atr, currentPrice, "MTF mis-alignment");

        String trendDir = alignedUp ? "UP" : "DOWN";
        if ((trendDir.equals("UP") && !trendUp) || (trendDir.equals("DOWN") && !trendDown)) {
            return neutral(adx, rsi, atr, currentPrice, "MTF conflicts with EMA trend");
        }

        // ── RSI extremes filter
        if (trendDir.equals("UP")   && (rsi <= MIN_RSI_UP   || rsi >= MAX_RSI_UP))   {
            return neutral(adx, rsi, atr, currentPrice, "RSI out of range for UP");
        }
        if (trendDir.equals("DOWN") && (rsi <= MIN_RSI_DOWN || rsi >= MAX_RSI_DOWN)) {
            return neutral(adx, rsi, atr, currentPrice, "RSI out of range for DOWN");
        }

        // ── MACD confirmation
        double[] macd = computeMacd(data);
        boolean macdUp   = macd[0] > macd[1];
        boolean macdDown = macd[0] < macd[1];
        boolean macdOk = (trendDir.equals("UP") && macdUp) || (trendDir.equals("DOWN") && macdDown);
        if (!macdOk) return neutral(adx, rsi, atr, currentPrice, "MACD conflict");

        // ── Volume confirmation (current bar vs 20-bar avg, lenient floor)
        double volAvg = avgVolume(data, 20);
        boolean volOk = volAvg <= 0 || latest.volume >= volAvg * 0.7;
        if (!volOk) return neutral(adx, rsi, atr, currentPrice, "low volume");

        // ── Pullback REQUIRED (V31.1): iter1 showed 4/5 confluence had 40% WR vs 69% at 5/5
        double tol = currentPrice * PULLBACK_TOL_PCT;
        boolean pullback = (currentPrice >= ema20 - tol && currentPrice <= ema20 + tol);
        if (!pullback) return neutral(adx, rsi, atr, currentPrice, "no pullback to EMA20");

        // ── Confluence score (out of 5):
        //   1. MTF alignment   (gated)
        //   2. EMA200 trend    (gated)
        //   3. RSI in range    (gated)
        //   4. MACD agrees     (gated)
        //   5. Pullback @ EMA20 (gated above)
        int confluence = 5;

        // ── Calibrated confidence
        double confidence = 90;
        if (inEarly) confidence += 2;        // momentum window bonus
        if (inPrime) confidence += 1;        // prime continuation
        if (adx > 30) confidence += 2;       // strong trend bonus
        if (trendDir.equals("UP") && rsi >= 55) confidence += 1;
        if (trendDir.equals("DOWN") && rsi <= 45) confidence += 1;
        confidence = Math.min(95, confidence);

        // ── R:R 1:1.5 with floors
        double slPts  = Math.max(atr * SL_ATR_MULT, MIN_SL_PTS);
        double tgtPts = Math.max(slPts * TGT_RR_MULT, MIN_TGT_PTS);

        String reason = String.format(
                "SENSEX_V31 | confluence=%d/5 | bias=%s/%s/%s | ADX=%.1f | RSI=%.1f%s | window=%s",
                confluence, bias5, bias15, bias60, adx, rsi,
                pullback ? " | pullback@EMA20" : "",
                inEarly ? "EARLY" : inPrime ? "PRIME" : "CLOSE");

        return new AIPredictor.AIPrediction(
                trendDir, confidence, confidence / 100.0,
                adx, rsi, atr / currentPrice, 80,
                "SENSEX_V31", reason,
                tgtPts, slPts, pullback);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers (self-contained — no reach into AIPredictor private state)
    // ────────────────────────────────────────────────────────────────────────

    private static AIPredictor.AIPrediction neutral(double adx, double rsi, double atr, double price, String why) {
        return new AIPredictor.AIPrediction(
                "NEUTRAL", 0, 0, adx, rsi, atr / Math.max(price, 1), 80,
                "SENSEX_V31", "filtered: " + why,
                atr, atr, false);
    }

    /**
     * 20-day rolling ATR average — used to detect dead/extreme volatility regimes.
     * Samples ATR once per day at end-of-day cutoff to avoid intra-day bias.
     */
    private static double computeAtrAvg20Day(List<SimpleMarketData> data) {
        int totalBars = Math.min(data.size(), BARS_PER_DAY_5MIN * REGIME_LOOKBACK_DAYS);
        if (totalBars < BARS_PER_DAY_5MIN * 5) return 0;
        double sum = 0;
        int counted = 0;
        for (int i = data.size() - totalBars + BARS_PER_DAY_5MIN; i < data.size(); i += BARS_PER_DAY_5MIN) {
            double a = atrAt(data, i, 14);
            if (a > 0) { sum += a; counted++; }
        }
        return counted > 0 ? sum / counted : 0;
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

    /**
     * Aggregate 5-min data into N-min buckets and return UP/DOWN/NEUTRAL bias
     * based on close vs EMA(emaPeriod) of bucket closes.
     */
    private static String bucketBias(List<SimpleMarketData> data, int barsPerBucket, int emaPeriod) {
        List<Double> closes = bucketCloses(data, barsPerBucket);
        if (closes.size() < emaPeriod + 2) return "NEUTRAL";
        double ema = emaFromList(closes, emaPeriod);
        double last = closes.get(closes.size() - 1);
        double prev = closes.get(closes.size() - 2);
        boolean rising = last > prev;
        boolean falling = last < prev;
        if (last > ema * TREND_SEPARATION && rising) return "UP";
        if (last < ema / TREND_SEPARATION && falling) return "DOWN";
        return "NEUTRAL";
    }

    private static List<Double> bucketCloses(List<SimpleMarketData> data, int barsPerBucket) {
        List<Double> result = new ArrayList<>();
        int n = data.size();
        for (int i = barsPerBucket; i <= n; i += barsPerBucket) {
            result.add(data.get(i - 1).price);
        }
        return result;
    }

    private static double emaFromList(List<Double> values, int period) {
        if (values.isEmpty()) return 0;
        if (values.size() < period) return values.get(values.size() - 1);
        double mult = 2.0 / (period + 1);
        double ema = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            ema = ((values.get(i) - ema) * mult) + ema;
        }
        return ema;
    }

    /**
     * MACD(12,26,9) using EMA-of-closes. Returns [macd, signal].
     */
    private static double[] computeMacd(List<SimpleMarketData> data) {
        if (data.size() < 35) return new double[]{0, 0};
        List<Double> closes = new ArrayList<>();
        for (SimpleMarketData d : data) closes.add(d.price);
        double ema12 = emaFromList(closes, 12);
        double ema26 = emaFromList(closes, 26);
        double macd = ema12 - ema26;
        // 9-period EMA of MACD line — rebuild MACD series last 9 bars approximately
        List<Double> macdSeries = new ArrayList<>();
        for (int i = 26; i < closes.size(); i++) {
            List<Double> sub = closes.subList(0, i + 1);
            macdSeries.add(emaFromList(sub, 12) - emaFromList(sub, 26));
        }
        double signal = emaFromList(macdSeries.subList(Math.max(0, macdSeries.size() - 9), macdSeries.size()), 9);
        return new double[]{macd, signal};
    }

    private static double avgVolume(List<SimpleMarketData> data, int period) {
        int n = data.size();
        if (n < period) return 0;
        long sum = 0;
        for (int i = n - period; i < n; i++) sum += data.get(i).volume;
        return (double) sum / period;
    }
}
