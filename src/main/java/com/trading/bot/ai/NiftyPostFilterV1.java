package com.trading.bot.ai;

import com.trading.bot.market.SimpleMarketData;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * NIFTY Post-Filter V1 — TEST ONLY, NOT wired into AIPredictor.
 *
 * Wraps the output of {@link AIPredictor#generatePrediction} for NIFTY50 and either
 * passes it through unchanged or returns NEUTRAL. Does NOT modify
 * {@link AIPredictor}'s predictNiftyStrategy() method — that logic is frozen per
 * user direction.
 *
 * Use only inside backtests (e.g., {@code Backtest120DayNiftyFiltered}) to measure
 * whether these reject rules improve the live 70%-WR NIFTY baseline without
 * costing too many calls.
 *
 * Filter rules (rejection causes):
 *  1. Volatility regime: ATR / 20-day-avg-ATR &lt; 0.4 OR &gt; 2.5
 *  2. Dead time window: 12:30–14:00 unless ADX &gt; 30
 *  3. Overextended trend: ADX &gt; 50
 *  4. Volume floor: current vol &lt; 0.7 × 20-bar avg
 *  5. Same-direction loss cooldown (tracked externally by the runner)
 */
public final class NiftyPostFilterV1 {

    private NiftyPostFilterV1() {}

    private static final double VOL_REGIME_MIN     = 0.4;
    private static final double VOL_REGIME_MAX     = 2.5;
    private static final double MAX_ADX            = 50.0;
    private static final double DEAD_TIME_ADX_MIN  = 30.0;
    private static final LocalTime DEAD_START      = LocalTime.of(12, 30);
    private static final LocalTime DEAD_END        = LocalTime.of(14, 0);
    private static final double VOL_FLOOR_MULT     = 0.7;
    private static final int    REGIME_LOOKBACK_DAYS = 20;
    private static final int    BARS_PER_DAY_5MIN    = 75;

    /**
     * @return original prediction if it passes all filters, or NEUTRAL if rejected.
     *         The "why" is appended to the reasoning for debugging.
     */
    public static AIPredictor.AIPrediction filter(
            AIPredictor.AIPrediction p,
            List<SimpleMarketData> data,
            SimpleMarketData latest,
            double atr,
            double adx) {

        if (p == null || "NEUTRAL".equals(p.predictedDirection)) return p;
        if (data == null || data.isEmpty() || latest == null) return p;

        // ── Rule 1: volatility regime
        double atrAvg = computeAtrAvg20Day(data);
        if (atrAvg > 0) {
            double ratio = atr / atrAvg;
            if (ratio < VOL_REGIME_MIN) return reject(p, atr, adx, "NIFTY_PF_VOLDEAD");
            if (ratio > VOL_REGIME_MAX) return reject(p, atr, adx, "NIFTY_PF_VOLEXTREME");
        }

        // ── Rule 2: dead-time window
        LocalTime now = (latest.timestamp != null)
                ? latest.timestamp.toLocalTime()
                : LocalTime.now(ZoneId.of("Asia/Kolkata"));
        if (!now.isBefore(DEAD_START) && now.isBefore(DEAD_END) && adx < DEAD_TIME_ADX_MIN) {
            return reject(p, atr, adx, "NIFTY_PF_DEADTIME");
        }

        // ── Rule 3: overextended ADX
        if (adx > MAX_ADX) return reject(p, atr, adx, "NIFTY_PF_OVERX");

        // ── Rule 4: volume floor
        double volAvg = avgVolume(data, 20);
        if (volAvg > 0 && latest.volume < volAvg * VOL_FLOOR_MULT) {
            return reject(p, atr, adx, "NIFTY_PF_LOWVOL");
        }

        return p; // pass
    }

    private static AIPredictor.AIPrediction reject(AIPredictor.AIPrediction p, double atr, double adx, String tag) {
        return new AIPredictor.AIPrediction(
                "NEUTRAL", 0, 0,
                p.neuralNetworkScore, p.marketRegimePrediction,
                p.volatilityForecast, p.liquidityPrediction,
                p.aiModel, tag + " | original: " + p.predictionReasoning,
                p.estimatedMovePoints, p.suggestedStopLoss, false);
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

    private static double avgVolume(List<SimpleMarketData> data, int period) {
        int n = data.size();
        if (n < period) return 0;
        long sum = 0;
        for (int i = n - period; i < n; i++) sum += data.get(i).volume;
        return (double) sum / period;
    }
}
