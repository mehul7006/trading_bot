package com.trading.bot.strategy;

import java.util.Arrays;

/**
 * OptionsSignalEngine v4 — Opening Range Breakout (ORB) + Trend Confirmation.
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │  Strategy  : "ORB + Session Bias Confirmation"                          │
 * │  Target WR : 65–70% on NIFTY50, BANKNIFTY, SENSEX                      │
 * │  Signal    : BUY CE (CALL) or BUY PE (PUT) options                     │
 * │                                                                          │
 * │  ROOT CAUSE of v3 failure (25% WR):                                    │
 * │  By the time EMA stack + RSI (33-47 for PUT) + MACD all align          │
 * │  perfectly in BEAR direction, the price has ALREADY fallen deep         │
 * │  and is primed to BOUNCE → we were entering at the END of the move,    │
 * │  not the beginning. Late entries = high loss rate.                      │
 * │                                                                          │
 * │  v4 FIX: Opening Range Breakout                                         │
 * │  The first 30 min (9:15–9:44) sets the day's range. A BREAK of this   │
 * │  range signals the day's TRUE direction — before price extends too far. │
 * │  Entry is at the START of the trend move, not after it's established.  │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ORB STRATEGY RULES:
 *  1. Opening Range   : High/Low of 9:15–9:44 candles (first 6 bars)
 *  2. Session bias    : Determined from opening direction (>0.20% move)
 *                       NEUTRAL = skip day (market unclear)
 *  3. Time window     : 10:00 AM – 1:00 PM (prime ORB breakout window)
 *  4. Breakout signal :
 *       CALL: 2 consecutive 5-min closes ABOVE opening range HIGH
 *             + session bias BULLISH (or price > VWAP if session is mild)
 *             + EMA20 > EMA50 (intermediate trend up)
 *             + RSI between 48–65 (not oversold, not overbought)
 *             + ADX ≥ 22 (some trending)
 *       PUT : 2 consecutive 5-min closes BELOW opening range LOW
 *             + session bias BEARISH (or price < VWAP if session is mild)
 *             + EMA20 < EMA50 (intermediate trend down)
 *             + RSI between 35–52 (not overbought, not oversold)
 *             + ADX ≥ 22 (some trending)
 *  5. SL / Target     : Defined per symbol in the audit (2:1 R:R)
 *  6. Max 1 per symbol per day, max 3 total, 60-min cooldown
 */
public class OptionsSignalEngine {

    // ── Signal types ──────────────────────────────────────────────────────────
    public enum Direction { CALL, PUT, NONE }

    /** Session opening bias — calculated externally from first 30-min candles. */
    public enum SessionBias { BULLISH, BEARISH, NEUTRAL }

    public static class SignalResult {
        public final Direction direction;
        public final double    confidence;
        public final String    reason;

        public SignalResult(Direction d, double conf, String reason) {
            this.direction  = d;
            this.confidence = conf;
            this.reason     = reason;
        }

        public boolean hasSignal() { return direction != Direction.NONE; }

        public static SignalResult none(String reason) {
            return new SignalResult(Direction.NONE, 0, reason);
        }
    }

    // ── ORB parameters ────────────────────────────────────────────────────────
    private static final double BIAS_THRESHOLD  = 0.20;  // % move for strong session (was 0.08)
    private static final int    EMA_MED         = 20;
    private static final int    EMA_SLOW        = 50;
    private static final int    RSI_P           = 14;
    private static final int    ADX_P           = 14;
    private static final double ADX_MIN         = 22.0;
    private static final double CALL_RSI_LO     = 48.0;
    private static final double CALL_RSI_HI     = 65.0;
    private static final double PUT_RSI_LO      = 35.0;
    private static final double PUT_RSI_HI      = 52.0;
    private static final double CONF_BASE       = 75.0;
    private static final double CONF_GATE       = 82.0;

    // ── Session Bias Calculation ──────────────────────────────────────────────

    /**
     * Calculates the session opening bias from the first 30 minutes of trading.
     *
     * @param openingCloses closing prices of 9:15–9:44 candles (up to 6)
     * @param openingOpen   opening price of the first candle at 9:15
     * @return BULLISH, BEARISH, or NEUTRAL
     */
    public static SessionBias calcSessionBias(double[] openingCloses, double openingOpen) {
        if (openingCloses == null || openingCloses.length < 3 || openingOpen <= 0) {
            return SessionBias.NEUTRAL;
        }
        double lastClose = openingCloses[openingCloses.length - 1];
        double pctMove   = (lastClose - openingOpen) / openingOpen * 100.0;

        if (pctMove > +BIAS_THRESHOLD) return SessionBias.BULLISH;
        if (pctMove < -BIAS_THRESHOLD) return SessionBias.BEARISH;
        return SessionBias.NEUTRAL;
    }

    /**
     * Calculates the Opening Range (high and low) from first 30-min candles.
     *
     * @param highs  high prices of opening candles
     * @param lows   low prices of opening candles
     * @return [openingHigh, openingLow]
     */
    public static double[] calcOpeningRange(double[] highs, double[] lows) {
        if (highs == null || lows == null || highs.length == 0) return new double[]{0, 0};
        double rangeHigh = Arrays.stream(highs).max().orElse(0);
        double rangeLow  = Arrays.stream(lows).min().orElse(0);
        return new double[]{rangeHigh, rangeLow};
    }

    // ── VWAP ─────────────────────────────────────────────────────────────────

    /**
     * Calculates session VWAP using typical price (volume-neutral approximation).
     *
     * @param closes closing prices from session start
     * @param highs  high prices from session start
     * @param lows   low prices from session start
     * @return VWAP value
     */
    public static double calcVWAP(double[] closes, double[] highs, double[] lows) {
        if (closes == null || closes.length == 0) return 0;
        double sumTP = 0, count = 0;
        for (int i = 0; i < closes.length; i++) {
            double tp = (closes[i] + highs[i] + lows[i]) / 3.0;
            sumTP += tp;
            count++;
        }
        return count > 0 ? sumTP / count : closes[closes.length - 1];
    }

    // ── Main Analysis (ORB + Confirmation) ───────────────────────────────────

    /**
     * Analyzes a candle using the ORB strategy.
     *
     * @param closes       recent 60+ candle closes (for EMA/RSI/ADX)
     * @param highs        recent 60+ candle highs
     * @param lows         recent 60+ candle lows
     * @param hour         current candle hour
     * @param minute       current candle minute
     * @param sessionBias  today's opening bias (BULLISH/BEARISH/NEUTRAL)
     * @param sessionVWAP  current session VWAP
     * @param orbHigh      opening range high (9:15–9:44)
     * @param orbLow       opening range low  (9:15–9:44)
     * @return SignalResult with CALL/PUT/NONE
     */
    public static SignalResult analyze(double[] closes, double[] highs, double[] lows,
                                       int hour, int minute,
                                       SessionBias sessionBias, double sessionVWAP,
                                       double orbHigh, double orbLow) {

        if (closes == null || closes.length < 55) return SignalResult.none("Insufficient data");

        int n = closes.length;

        // ── Gate 1: Time window 10:00 – 13:00 ────────────────────────────────
        boolean inWindow = (hour == 10) ||
                           (hour == 11) ||
                           (hour == 12) ||
                           (hour == 13 && minute == 0);
        if (!inWindow) return SignalResult.none("Outside time window 10:00-13:00");

        // ── Gate 2: Session bias must be clear (not NEUTRAL) ─────────────────
        if (sessionBias == SessionBias.NEUTRAL) {
            return SignalResult.none("NEUTRAL session - skipping day");
        }

        // ── Gate 3: Opening range must be meaningful ──────────────────────────
        if (orbHigh <= 0 || orbLow <= 0 || orbHigh <= orbLow) {
            return SignalResult.none("Invalid ORB");
        }
        double orbSize = orbHigh - orbLow;
        double curPrice = closes[n - 1];

        // ── Gate 4: Require 2 consecutive closes beyond ORB ──────────────────
        double prevClose  = closes[n - 2];
        double prevClose2 = closes[n - 3];

        boolean callBreakout = prevClose > orbHigh && prevClose2 > orbHigh;
        boolean putBreakout  = prevClose < orbLow  && prevClose2 < orbLow;

        if (!callBreakout && !putBreakout) {
            return SignalResult.none("No ORB breakout (need 2 consecutive closes beyond range)");
        }

        // ── Gate 5: Breakout direction must match session bias ────────────────
        boolean callAllowed = sessionBias == SessionBias.BULLISH;
        boolean putAllowed  = sessionBias == SessionBias.BEARISH;

        if (callBreakout && !callAllowed) {
            return SignalResult.none("CALL breakout but session is BEARISH");
        }
        if (putBreakout && !putAllowed) {
            return SignalResult.none("PUT breakout but session is BULLISH");
        }

        // ── Gate 6: VWAP alignment ────────────────────────────────────────────
        if (sessionVWAP > 0) {
            if (callBreakout && curPrice < sessionVWAP * 0.9985) {
                return SignalResult.none("CALL but price below VWAP");
            }
            if (putBreakout && curPrice > sessionVWAP * 1.0015) {
                return SignalResult.none("PUT but price above VWAP");
            }
        }

        // ── Gate 7: EMA20 > EMA50 for CALL, EMA20 < EMA50 for PUT ───────────
        double ema20 = ema(closes, EMA_MED);
        double ema50 = ema(closes, EMA_SLOW);

        // Need at least moderate trend alignment
        boolean emaCallOk = ema20 > ema50 * 0.9995;  // EMA20 >= EMA50 (or very close)
        boolean emaPutOk  = ema20 < ema50 * 1.0005;  // EMA20 <= EMA50 (or very close)

        if (callBreakout && !emaCallOk) {
            return SignalResult.none("CALL: EMA20 below EMA50 (bearish EMA)");
        }
        if (putBreakout && !emaPutOk) {
            return SignalResult.none("PUT: EMA20 above EMA50 (bullish EMA)");
        }

        // ── Gate 8: RSI in reasonable zone (not extreme) ─────────────────────
        double rsiVal = rsi(closes, RSI_P);
        if (callBreakout && (rsiVal < CALL_RSI_LO || rsiVal > CALL_RSI_HI)) {
            return SignalResult.none(String.format("RSI %.1f outside CALL zone [%.0f-%.0f]",
                rsiVal, CALL_RSI_LO, CALL_RSI_HI));
        }
        if (putBreakout && (rsiVal < PUT_RSI_LO || rsiVal > PUT_RSI_HI)) {
            return SignalResult.none(String.format("RSI %.1f outside PUT zone [%.0f-%.0f]",
                rsiVal, PUT_RSI_LO, PUT_RSI_HI));
        }

        // ── Gate 9: ADX ≥ 22 (trending market) ───────────────────────────────
        double adxVal = adx(closes, highs, lows, ADX_P);
        if (adxVal < ADX_MIN) {
            return SignalResult.none(String.format("ADX %.1f < %.0f (choppy market)", adxVal, ADX_MIN));
        }

        // ── All gates passed: compute confidence ─────────────────────────────
        double conf = CONF_BASE;

        // Session strength bonus
        if (sessionBias == SessionBias.BULLISH && callBreakout) conf += 4.0;
        if (sessionBias == SessionBias.BEARISH && putBreakout)  conf += 4.0;

        // ORB size bonus (larger range = more conviction)
        double orbPct = orbSize / closes[0] * 100;
        if (orbPct > 0.3) conf += 3.0;
        if (orbPct > 0.5) conf += 2.0;

        // RSI momentum bonus
        if (callBreakout && rsiVal >= 55.0 && rsiVal <= 62.0) conf += 3.0;
        if (putBreakout  && rsiVal >= 38.0 && rsiVal <= 45.0) conf += 3.0;

        // ADX strength bonus
        if (adxVal >= 30) conf += 4.0;
        if (adxVal >= 35) conf += 3.0;

        // Price extension beyond ORB (further = stronger breakout)
        if (callBreakout) {
            double ext = (curPrice - orbHigh) / orbSize;
            if (ext > 0.3 && ext < 1.5) conf += 3.0;  // extended but not too far
        }
        if (putBreakout) {
            double ext = (orbLow - curPrice) / orbSize;
            if (ext > 0.3 && ext < 1.5) conf += 3.0;
        }

        // EMA quality bonus
        if (callBreakout && ema20 > ema50 * 1.001) conf += 2.0;  // clear bullish EMA
        if (putBreakout  && ema20 < ema50 * 0.999) conf += 2.0;  // clear bearish EMA

        if (conf < CONF_GATE) {
            return SignalResult.none(String.format("Confidence %.1f < %.0f gate", conf, CONF_GATE));
        }

        Direction dir = callBreakout ? Direction.CALL : Direction.PUT;
        String reason = String.format(
            "ORB %s | Session:%s | ORB:%.0f-%.0f | Price:%.0f | VWAP:%.0f | EMA20:%.0f EMA50:%.0f | RSI:%.1f | ADX:%.1f",
            dir, sessionBias,
            orbLow, orbHigh, curPrice, sessionVWAP,
            ema20, ema50, rsiVal, adxVal);

        return new SignalResult(dir, conf, reason);
    }

    // ── Backward-compatible overload (no ORB, uses VWAP+session only) ────────

    public static SignalResult analyze(double[] closes, double[] highs, double[] lows,
                                       int hour, int minute,
                                       SessionBias sessionBias, double sessionVWAP) {
        // Estimate ORB from the slice of data available
        // (used when ORB wasn't pre-calculated)
        if (closes == null || closes.length < 20) return SignalResult.none("Insufficient data");

        // Use first 6 candles of the full window as a proxy for ORB
        int orbLen = Math.min(6, closes.length / 10);
        double[] oH = Arrays.copyOf(highs, orbLen);
        double[] oL = Arrays.copyOf(lows,  orbLen);
        double[] orbRange = calcOpeningRange(oH, oL);
        return analyze(closes, highs, lows, hour, minute, sessionBias, sessionVWAP,
                       orbRange[0], orbRange[1]);
    }

    // Legacy overload (no session bias, no VWAP — for backward compat only)
    public static SignalResult analyze(double[] closes, double[] highs, double[] lows,
                                       int hour, int minute) {
        return SignalResult.none("Legacy overload disabled - use analyze() with sessionBias");
    }

    // ── Indicator Calculations ────────────────────────────────────────────────

    public static double ema(double[] c, int period) {
        if (c == null || c.length < period) return c != null && c.length > 0 ? c[c.length-1] : 0;
        double k = 2.0 / (period + 1);
        double e = c[0];
        for (int i = 1; i < c.length; i++) e = c[i] * k + e * (1 - k);
        return e;
    }

    public static double rsi(double[] c, int period) {
        if (c == null || c.length < period + 1) return 50;
        double gain = 0, loss = 0;
        for (int i = c.length - period; i < c.length; i++) {
            double diff = c[i] - c[i - 1];
            if (diff > 0) gain += diff; else loss -= diff;
        }
        if (loss == 0) return 100;
        double rs = (gain / period) / (loss / period);
        return 100.0 - (100.0 / (1.0 + rs));
    }

    public static double adx(double[] c, double[] h, double[] l, int period) {
        if (c == null || c.length < period + 1) return 0;
        double adxVal = 0;
        double[] tr = new double[c.length - 1];
        double[] dm_p = new double[c.length - 1];
        double[] dm_m = new double[c.length - 1];

        for (int i = 1; i < c.length; i++) {
            double hi = h[i] - l[i];
            double hc = Math.abs(h[i] - c[i - 1]);
            double lc = Math.abs(l[i] - c[i - 1]);
            tr[i-1] = Math.max(hi, Math.max(hc, lc));
            double upMove   = h[i] - h[i - 1];
            double downMove = l[i - 1] - l[i];
            dm_p[i-1] = (upMove > downMove && upMove > 0) ? upMove : 0;
            dm_m[i-1] = (downMove > upMove && downMove > 0) ? downMove : 0;
        }

        int start = Math.max(0, tr.length - period * 2);
        double atr14 = 0, dmp14 = 0, dmm14 = 0;
        for (int i = start; i < start + period && i < tr.length; i++) {
            atr14 += tr[i]; dmp14 += dm_p[i]; dmm14 += dm_m[i];
        }
        if (atr14 == 0) return 0;
        double diP = dmp14 / atr14 * 100;
        double diM = dmm14 / atr14 * 100;
        double dx  = Math.abs(diP - diM) / (diP + diM + 1e-9) * 100;
        return dx;
    }

    public static double atr(double[] c, double[] h, double[] l, int period) {
        if (c == null || c.length < 2) return 0;
        double sum = 0;
        int cnt = 0;
        for (int i = Math.max(1, c.length - period); i < c.length; i++) {
            sum += Math.max(h[i] - l[i], Math.max(Math.abs(h[i] - c[i-1]), Math.abs(l[i] - c[i-1])));
            cnt++;
        }
        return cnt > 0 ? sum / cnt : 0;
    }

    // ── Options chain enhancement (PCR/Max Pain boost) ────────────────────────

    public static SignalResult withOptionsData(SignalResult base, double pcr, double maxPainDiff) {
        if (!base.hasSignal()) return base;
        double boost = 0;
        // PCR > 1.2 → strong PUT buying → market likely to fall → favour PUT
        if (base.direction == Direction.PUT  && pcr > 1.2) boost += 2.0;
        // PCR < 0.7 → strong CALL buying → market likely to rise → favour CALL
        if (base.direction == Direction.CALL && pcr < 0.7) boost += 2.0;
        // Max pain within 0.5% of current → price gravitates to max pain
        if (Math.abs(maxPainDiff) < 0.5) boost += 1.5;
        return new SignalResult(base.direction, base.confidence + boost, base.reason + " | PCR:" + pcr);
    }
}
