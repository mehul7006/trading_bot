package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.agents.PredictionAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * BacktestGridSearch — Two-phase parameter optimizer.
 *
 * Phase 1 (one-time, ~4 min): collect every possible signal from 30-day window
 *          with minimum gates so nothing is excluded.
 *
 * Phase 2 (instant): replay collected signals through every parameter combination
 *          and rank by win rate, net points, and call volume.
 *
 * Parameters tested:
 *   MIN_CONFIDENCE : 86 → 93   (8 values)
 *   NIFTY MIN_POINTS: 20 → 35  (6 values)
 *   SENSEX MIN_POINTS: 50 → 80 (6 values)
 *   COOLDOWN       : 8 → 20 min (5 values)
 *   = 1,440 total combinations, runs in < 1 second after collection.
 *
 * DO NOT ADD TO MAIN CODE — for analysis only.
 */
public class BacktestGridSearch {

    // ── Collection gates (very permissive — capture everything AIPredictor can generate)
    private static final double COLLECT_MIN_CONF   = 85.0;
    private static final double COLLECT_NIFTY_PTS  = 10.0;
    private static final double COLLECT_SENSEX_PTS = 30.0;
    private static final int    LOAD_DAYS          = 47;
    private static final int    BACKTEST_DAYS       = 30;
    private static final int    FIXED_LOOKAHEAD     = 24;   // kept fixed — 2 hours of price action

    // ── Grid search ranges
    private static final int[]    CONF_RANGE    = {86, 87, 88, 89, 90, 91, 92, 93};
    private static final double[] NIFTY_RANGE   = {20, 23, 25, 28, 30, 35};
    private static final double[] SENSEX_RANGE  = {50, 55, 60, 65, 70, 80};
    private static final int[]    COOLDOWN_MINS = {8, 10, 12, 15, 20};

    // ── Current V29.0 config (benchmark)
    private static final int    V29_CONF    = 86;
    private static final double V29_NIFTY   = 28.0;
    private static final double V29_SENSEX  = 65.0;
    private static final int    V29_COOL    = 10;

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    // ════════════════════════════════════════════════════════════════════════════
    // Data structures
    // ════════════════════════════════════════════════════════════════════════════

    static class RawSignal {
        LocalDate date;
        LocalTime time;
        long      epochMs;
        String    symbol;
        String    direction;
        double    confidence;
        double    estimatedMovePoints;
        double    slPts;
        int       outcome;    // LOSS=0, PARTIAL=1, FULL_WIN=2
        double    tgtPts;
    }

    static class GSResult {
        int    minConf;
        double niftyPts, sensexPts;
        int    cooldownMin;
        int    calls, wins, partials, losses;
        double winRate, netPoints;

        boolean isCurrentConfig() {
            return minConf == V29_CONF
                && niftyPts == V29_NIFTY
                && sensexPts == V29_SENSEX
                && cooldownMin == V29_COOL;
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // MAIN
    // ════════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        line('═', 72);
        System.out.println("  PERMUTATION & COMBINATION GRID SEARCH — V29.0 OPTIMIZER");
        System.out.println("  Testing 1,440 parameter combinations on 30-day live data");
        line('═', 72);

        // ── Phase 1: collect ─────────────────────────────────────────────────────
        System.out.println("\n📡 Phase 1 — Collecting all raw signals (≈ 4 min)...");
        MarketDataAgent mdAgent   = new MarketDataAgent();
        PredictionAgent predAgent = new PredictionAgent();
        List<RawSignal> pool      = collectAllSignals(mdAgent, predAgent);
        System.out.printf("  ✅ %d raw signals collected from 30-day window%n", pool.size());

        // ── Phase 2: grid search ──────────────────────────────────────────────────
        System.out.println("\n⚡ Phase 2 — Running all 1,440 combinations...");
        List<GSResult> results = runGridSearch(pool);
        System.out.printf("  ✅ %d valid combinations evaluated%n", results.size());

        // ── Phase 3: report ────────────────────────────────────────────────────────
        printReport(results);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PHASE 1 — COLLECTION
    // ════════════════════════════════════════════════════════════════════════════

    private static List<RawSignal> collectAllSignals(MarketDataAgent mdAgent,
                                                      PredictionAgent predAgent) {
        List<RawSignal> pool = new ArrayList<>();
        String[] symbols = {"NIFTY50", "SENSEX"};

        for (String symbol : symbols) {
            try {
                List<SimpleMarketData> data = mdAgent.getHistoricalData(symbol, LOAD_DAYS);
                if (data == null || data.size() < 200) {
                    System.err.println("Insufficient data for " + symbol);
                    continue;
                }

                // Determine backtest start date
                Set<LocalDate> datesSet = new LinkedHashSet<>();
                for (SimpleMarketData d : data)
                    if (d.timestamp != null) datesSet.add(d.timestamp.toLocalDate());
                List<LocalDate> dates = new ArrayList<>(datesSet);
                Collections.sort(dates);
                LocalDate cutoff = dates.size() > BACKTEST_DAYS
                    ? dates.get(dates.size() - BACKTEST_DAYS)
                    : dates.get(0);

                System.out.printf("  %s: %s → %s%n", symbol, cutoff, dates.get(dates.size() - 1));

                // Collect WITHOUT cooldown — every 5-min candle is a candidate
                for (int i = 200; i < data.size(); i++) {
                    SimpleMarketData candle = data.get(i);
                    if (candle.timestamp == null) continue;

                    LocalDate date = candle.timestamp.toLocalDate();
                    LocalTime time = candle.timestamp.toLocalTime();
                    if (date.isBefore(cutoff)) continue;
                    if (time.isBefore(LocalTime.of(9, 15)) || time.isAfter(LocalTime.of(15, 25))) continue;

                    List<SimpleMarketData> history = data.subList(0, i + 1);
                    AIPredictor.AIPrediction pred = predAgent.generateSignal(symbol, history);

                    if ("NEUTRAL".equals(pred.predictedDirection)) continue;
                    if (pred.confidence < COLLECT_MIN_CONF)   continue;
                    double minPts = symbol.equals("NIFTY50") ? COLLECT_NIFTY_PTS : COLLECT_SENSEX_PTS;
                    if (pred.estimatedMovePoints < minPts)    continue;

                    int outcome = verifyOutcome(data, i, pred, FIXED_LOOKAHEAD);

                    RawSignal rs = new RawSignal();
                    rs.date    = date;
                    rs.time    = time;
                    rs.epochMs = candle.timestamp
                        .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
                    rs.symbol              = symbol;
                    rs.direction           = pred.predictedDirection;
                    rs.confidence          = pred.confidence;
                    rs.estimatedMovePoints = pred.estimatedMovePoints;
                    rs.slPts               = pred.suggestedStopLoss;
                    rs.tgtPts              = pred.estimatedMovePoints;
                    rs.outcome             = outcome;
                    pool.add(rs);
                }
            } catch (Exception e) {
                System.err.println("Collection error for " + symbol + ": " + e.getMessage());
            }
        }

        // Sort by epoch time for correct cooldown replay
        pool.sort(Comparator.comparingLong(s -> s.epochMs));
        return pool;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PHASE 2 — GRID SEARCH
    // ════════════════════════════════════════════════════════════════════════════

    private static List<GSResult> runGridSearch(List<RawSignal> pool) {
        List<GSResult> results = new ArrayList<>();

        for (int conf : CONF_RANGE) {
            for (double nPts : NIFTY_RANGE) {
                for (double sPts : SENSEX_RANGE) {
                    for (int coolMin : COOLDOWN_MINS) {
                        GSResult r = evaluate(pool, conf, nPts, sPts, coolMin);
                        if (r.calls >= 20) results.add(r); // ignore configs with <20 calls
                    }
                }
            }
        }
        return results;
    }

    private static GSResult evaluate(List<RawSignal> pool,
                                      int minConf, double niftyPts, double sensexPts,
                                      int cooldownMin) {
        long cooldownMs = cooldownMin * 60_000L;
        Map<String, Long> lastMs = new HashMap<>();

        int calls = 0, wins = 0, partials = 0, losses = 0;
        double net = 0;

        for (RawSignal s : pool) {
            // Confidence gate
            if (s.confidence < minConf) continue;
            // Min points gate
            double minPts = s.symbol.equals("NIFTY50") ? niftyPts : sensexPts;
            if (s.estimatedMovePoints < minPts) continue;
            // Cooldown gate (per symbol)
            long last = lastMs.getOrDefault(s.symbol, 0L);
            if (s.epochMs - last < cooldownMs) continue;
            lastMs.put(s.symbol, s.epochMs);

            // Take signal
            calls++;
            switch (s.outcome) {
                case FULL_WIN    -> { wins++;    net +=  s.tgtPts; }
                case PARTIAL_WIN -> { partials++; net +=  s.tgtPts * 0.3; }
                default          -> { losses++;   net += -s.slPts; }
            }
        }

        GSResult r       = new GSResult();
        r.minConf        = minConf;
        r.niftyPts       = niftyPts;
        r.sensexPts      = sensexPts;
        r.cooldownMin    = cooldownMin;
        r.calls          = calls;
        r.wins           = wins;
        r.partials       = partials;
        r.losses         = losses;
        r.winRate        = calls > 0 ? (wins + partials) * 100.0 / calls : 0;
        r.netPoints      = net;
        return r;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // OUTCOME VERIFICATION
    // ════════════════════════════════════════════════════════════════════════════

    private static int verifyOutcome(List<SimpleMarketData> data, int idx,
                                     AIPredictor.AIPrediction pred, int lookahead) {
        double entry = data.get(idx).price;
        String dir   = pred.predictedDirection;
        double tgt   = dir.equals("UP") ? entry + pred.estimatedMovePoints
                                        : entry - pred.estimatedMovePoints;
        double sl    = dir.equals("UP") ? entry - pred.suggestedStopLoss
                                        : entry + pred.suggestedStopLoss;
        double half  = dir.equals("UP") ? entry + pred.estimatedMovePoints * 0.5
                                        : entry - pred.estimatedMovePoints * 0.5;
        boolean hitHalf = false;
        for (int j = 1; j <= lookahead && (idx + j) < data.size(); j++) {
            SimpleMarketData c = data.get(idx + j);
            if (dir.equals("UP")) {
                if (c.low  <= sl)  return hitHalf ? PARTIAL_WIN : LOSS;
                if (c.high >= tgt) return FULL_WIN;
                if (c.high >= half) hitHalf = true;
            } else {
                if (c.high >= sl)  return hitHalf ? PARTIAL_WIN : LOSS;
                if (c.low  <= tgt) return FULL_WIN;
                if (c.low  <= half) hitHalf = true;
            }
        }
        return hitHalf ? PARTIAL_WIN : LOSS;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // PHASE 3 — REPORT
    // ════════════════════════════════════════════════════════════════════════════

    private static void printReport(List<GSResult> all) {

        // Find current V29.0 result
        GSResult current = all.stream().filter(GSResult::isCurrentConfig)
            .findFirst().orElse(null);

        line('═', 90);
        System.out.println("  GRID SEARCH RESULTS — TOP CONFIGURATIONS");
        line('═', 90);

        // ── Section 1: Top by WR (min 50 calls for reliability) ─────────────────
        System.out.println("\n  ► TOP 15 BY WIN RATE  (minimum 50 calls — avoids noise)");
        printTableHeader();
        List<GSResult> byWR = new ArrayList<>(all);
        byWR.removeIf(r -> r.calls < 50);
        byWR.sort(Comparator.comparingDouble((GSResult r) -> -r.winRate));
        int rank = 1;
        if (current != null && current.calls >= 50) printRow(rank++, current, "◄ V29.0");
        for (GSResult r : byWR) {
            if (r.isCurrentConfig()) continue;
            if (rank > 16) break;
            printRow(rank++, r, "");
        }

        // ── Section 2: Top by Net Points (min 50 calls) ──────────────────────────
        System.out.println("\n  ► TOP 15 BY NET POINTS  (minimum 50 calls)");
        printTableHeader();
        List<GSResult> byPts = new ArrayList<>(all);
        byPts.removeIf(r -> r.calls < 50);
        byPts.sort(Comparator.comparingDouble((GSResult r) -> -r.netPoints));
        rank = 1;
        if (current != null && current.calls >= 50) printRow(rank++, current, "◄ V29.0");
        for (GSResult r : byPts) {
            if (r.isCurrentConfig()) continue;
            if (rank > 16) break;
            printRow(rank++, r, "");
        }

        // ── Section 3: Best balanced (WR ≥ 67% AND calls ≥ 80) ─────────────────
        System.out.println("\n  ► BALANCED — WR ≥ 67%  AND  calls ≥ 80/month");
        printTableHeader();
        List<GSResult> balanced = new ArrayList<>(all);
        balanced.removeIf(r -> r.winRate < 67.0 || r.calls < 80);
        balanced.sort(Comparator.comparingDouble((GSResult r) -> -(r.winRate * r.netPoints)));
        if (balanced.isEmpty()) {
            System.out.println("  (no combination achieves both ≥ 67% WR AND ≥ 80 calls — see analysis below)");
        } else {
            rank = 1;
            for (GSResult r : balanced) {
                if (rank > 10) break;
                String tag = r.isCurrentConfig() ? "◄ V29.0" : "";
                printRow(rank++, r, tag);
            }
        }

        // ── Section 4: Confidence sweep (fixed other params at V29.0 values) ────
        System.out.println("\n  ► CONFIDENCE SWEEP  (only confidence varies, rest = V29.0)");
        line('-', 90);
        System.out.printf("  %-10s  %6s  %5s  %7s  %12s  %s%n",
            "MinConf", "Calls", "WR%", "Change", "Net Points", "Verdict");
        line('-', 90);
        for (int conf : CONF_RANGE) {
            GSResult r = all.stream()
                .filter(x -> x.minConf == conf && x.niftyPts == V29_NIFTY
                    && x.sensexPts == V29_SENSEX && x.cooldownMin == V29_COOL)
                .findFirst().orElse(null);
            if (r == null) continue;
            double wrDelta = current != null ? r.winRate - current.winRate : 0;
            String verdict = r.isCurrentConfig() ? "◄ current"
                : wrDelta > 0.5 ? "▲ better WR"
                : wrDelta < -0.5 ? "▼ worse WR"
                : "≈ same";
            System.out.printf("  %-10d  %6d  %4.1f%%  %+6.1f%%  %+12.0f  %s%n",
                conf, r.calls, r.winRate, wrDelta, r.netPoints, verdict);
        }

        // ── Section 5: Cooldown sweep (fixed other params at V29.0 values) ──────
        System.out.println("\n  ► COOLDOWN SWEEP  (only cooldown varies, rest = V29.0)");
        line('-', 90);
        System.out.printf("  %-12s  %6s  %5s  %7s  %12s  %s%n",
            "Cooldown", "Calls", "WR%", "Change", "Net Points", "Verdict");
        line('-', 90);
        for (int cd : COOLDOWN_MINS) {
            GSResult r = all.stream()
                .filter(x -> x.minConf == V29_CONF && x.niftyPts == V29_NIFTY
                    && x.sensexPts == V29_SENSEX && x.cooldownMin == cd)
                .findFirst().orElse(null);
            if (r == null) continue;
            double wrDelta = current != null ? r.winRate - current.winRate : 0;
            String verdict = r.isCurrentConfig() ? "◄ current"
                : wrDelta > 0.5 ? "▲ better WR"
                : wrDelta < -0.5 ? "▼ worse WR"
                : "≈ same";
            System.out.printf("  %-12s  %6d  %4.1f%%  %+6.1f%%  %+12.0f  %s%n",
                cd + " min", r.calls, r.winRate, wrDelta, r.netPoints, verdict);
        }

        // ── Section 6: Min-points sweep for NIFTY ────────────────────────────────
        System.out.println("\n  ► NIFTY MIN-POINTS SWEEP  (rest = V29.0)");
        line('-', 90);
        System.out.printf("  %-14s  %6s  %5s  %7s  %12s%n",
            "NIFTY minPts", "Calls", "WR%", "Change", "Net Points");
        line('-', 90);
        for (double np : NIFTY_RANGE) {
            GSResult r = all.stream()
                .filter(x -> x.minConf == V29_CONF && x.niftyPts == np
                    && x.sensexPts == V29_SENSEX && x.cooldownMin == V29_COOL)
                .findFirst().orElse(null);
            if (r == null) continue;
            double wrDelta = current != null ? r.winRate - current.winRate : 0;
            System.out.printf("  %-14.0f  %6d  %4.1f%%  %+6.1f%%  %+12.0f%s%n",
                np, r.calls, r.winRate, wrDelta, r.netPoints,
                r.isCurrentConfig() ? "  ◄ current" : "");
        }

        // ── Section 7: Min-points sweep for SENSEX ───────────────────────────────
        System.out.println("\n  ► SENSEX MIN-POINTS SWEEP  (rest = V29.0)");
        line('-', 90);
        System.out.printf("  %-16s  %6s  %5s  %7s  %12s%n",
            "SENSEX minPts", "Calls", "WR%", "Change", "Net Points");
        line('-', 90);
        for (double sp : SENSEX_RANGE) {
            GSResult r = all.stream()
                .filter(x -> x.minConf == V29_CONF && x.niftyPts == V29_NIFTY
                    && x.sensexPts == sp && x.cooldownMin == V29_COOL)
                .findFirst().orElse(null);
            if (r == null) continue;
            double wrDelta = current != null ? r.winRate - current.winRate : 0;
            System.out.printf("  %-16.0f  %6d  %4.1f%%  %+6.1f%%  %+12.0f%s%n",
                sp, r.calls, r.winRate, wrDelta, r.netPoints,
                r.isCurrentConfig() ? "  ◄ current" : "");
        }

        // ── Final summary ─────────────────────────────────────────────────────────
        line('═', 90);
        System.out.println("  FINAL RECOMMENDATION");
        line('-', 90);

        // Best single config by WR with ≥60 calls
        GSResult bestWR = all.stream()
            .filter(r -> r.calls >= 60)
            .max(Comparator.comparingDouble(r -> r.winRate))
            .orElse(null);

        // Best single config by net points with ≥60 calls
        GSResult bestPts = all.stream()
            .filter(r -> r.calls >= 60)
            .max(Comparator.comparingDouble(r -> r.netPoints))
            .orElse(null);

        if (current != null) {
            System.out.printf("  Current V29.0 : %d calls  |  %.1f%% WR  |  %+.0f pts/month%n",
                current.calls, current.winRate, current.netPoints);
        }
        if (bestWR != null && !bestWR.isCurrentConfig()) {
            System.out.printf("  Best WR found : %d calls  |  %.1f%% WR  |  %+.0f pts/month%n",
                bestWR.calls, bestWR.winRate, bestWR.netPoints);
            System.out.printf("    Config: MinConf=%d  NIFTY≥%.0f pts  SENSEX≥%.0f pts  Cooldown=%d min%n",
                bestWR.minConf, bestWR.niftyPts, bestWR.sensexPts, bestWR.cooldownMin);
        }
        if (bestPts != null && !bestPts.isCurrentConfig()) {
            System.out.printf("  Best pts found: %d calls  |  %.1f%% WR  |  %+.0f pts/month%n",
                bestPts.calls, bestPts.winRate, bestPts.netPoints);
            System.out.printf("    Config: MinConf=%d  NIFTY≥%.0f pts  SENSEX≥%.0f pts  Cooldown=%d min%n",
                bestPts.minConf, bestPts.niftyPts, bestPts.sensexPts, bestPts.cooldownMin);
        }
        System.out.println();
        System.out.println("  ⚠️  OVERFITTING WARNING: Parameters optimized on 30-day window");
        System.out.println("     may not generalize. Only adopt if improvement is ≥ 2% WR.");
        line('═', 90);
    }

    private static void printTableHeader() {
        line('-', 86);
        System.out.printf("  %4s  %7s  %5s  %8s  %8s  %4s  %5s  %12s  %s%n",
            "Rank", "MinConf", "Calls", "NIFTY≥", "SENSEX≥", "CD", "WR%", "Net Pts", "");
        line('-', 86);
    }

    private static void printRow(int rank, GSResult r, String tag) {
        System.out.printf("  %4d  %7d  %5d  %8.0f  %8.0f  %3dm  %4.1f%%  %+12.0f  %s%n",
            rank, r.minConf, r.calls, r.niftyPts, r.sensexPts,
            r.cooldownMin, r.winRate, r.netPoints, tag);
    }

    private static void line(char c, int n) {
        System.out.println(String.valueOf(c).repeat(n));
    }
}
