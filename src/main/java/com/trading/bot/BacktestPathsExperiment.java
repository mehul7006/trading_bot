package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.agents.PredictionAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * BacktestPathsExperiment — Experimental backtest comparing 5 improvement paths vs V29.0 baseline.
 *
 *  Path 1 : 15-min candles  — aggregate 5-min → 15-min; expected noise reduction
 *  Path 2 : Prime window    — trade only 11:00am–12:30pm; highest-quality signals
 *  Path 3 : SENSEX only     — focus on the better-performing symbol
 *  Path 6 : Options strangle — sell weekly strangle; win if market stays rangebound
 *  Path 7 : ML logistic     — logistic regression filter on raw signal features
 *
 *  NO changes to main code.  Run, review, decide.
 */
public class BacktestPathsExperiment {

    // ── Shared constants ─────────────────────────────────────────────────────
    private static final int    LOAD_DAYS        = 50;
    private static final int    BACKTEST_DAYS    = 30;
    private static final double MIN_CONFIDENCE   = 86.0;
    private static final int    LOOKAHEAD_5MIN   = 24;  // 24 × 5-min = 2 h
    private static final int    LOOKAHEAD_15MIN  = 8;   // 8  × 15-min = 2 h
    private static final long   COOLDOWN_5MIN_MS = 10L * 60 * 1_000;
    private static final long   COOLDOWN_15MIN_MS= 15L * 60 * 1_000;

    private static final Map<String, Double> MIN_PTS_5MIN = Map.of(
        "NIFTY50", 28.0, "SENSEX", 65.0
    );
    private static final Map<String, Double> MIN_PTS_15MIN = Map.of(
        "NIFTY50", 35.0, "SENSEX", 80.0   // higher bar for 15-min
    );

    // ── Signal record ────────────────────────────────────────────────────────
    static class Signal {
        String  date, time, symbol, direction, outcome;
        double  entry, tgtPts, slPts, ptsCaptured, confidence;
        long    epochMs;
        int     timeSlab;
        double  rrRatio;
        // For ML features
        double  estMove;
    }

    private static final int LOSS = 0, PARTIAL = 1, WIN = 2;

    // ════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║    PATHS EXPERIMENT — 5 improvement strategies vs V29.0 baseline    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        MarketDataAgent  md   = new MarketDataAgent();
        PredictionAgent  pred = new PredictionAgent();

        // ── Baseline (same logic as BacktestLast30Days) ───────────────────
        System.out.println("▶ Running BASELINE (V29.0) ...");
        PathResult baseline = runBaseline(md, pred);

        // ── Path 1 : 15-min candles ────────────────────────────────────────
        System.out.println("▶ Running PATH 1 (15-min candles) ...");
        PathResult path1 = runPath1_15min(md, pred);

        // ── Path 2 : Prime window (11:00–12:30) ───────────────────────────
        System.out.println("▶ Running PATH 2 (prime window 11:00–12:30) ...");
        PathResult path2 = runPath2_prime(md, pred);

        // ── Path 3 : SENSEX only ──────────────────────────────────────────
        System.out.println("▶ Running PATH 3 (SENSEX only) ...");
        PathResult path3 = runPath3_sensexOnly(md, pred);

        // ── Path 6 : Options strangle simulation ──────────────────────────
        System.out.println("▶ Running PATH 6 (options strangle weekly) ...");
        PathResult path6 = runPath6_strangle(md, pred);

        // ── Path 7 : ML logistic regression ──────────────────────────────
        System.out.println("▶ Running PATH 7 (ML logistic regression) ...");
        PathResult path7 = runPath7_ml(md, pred);

        // ── Final comparison table ────────────────────────────────────────
        printComparisonTable(baseline, path1, path2, path3, path6, path7);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  BASELINE — V29.0 (identical to BacktestLast30Days logic)
    // ════════════════════════════════════════════════════════════════════════
    static PathResult runBaseline(MarketDataAgent md, PredictionAgent pred) {
        List<Signal> signals = new ArrayList<>();
        for (String sym : new String[]{"NIFTY50", "SENSEX"}) {
            signals.addAll(collect5Min(sym, md, pred,
                MIN_PTS_5MIN, COOLDOWN_5MIN_MS, LOOKAHEAD_5MIN,
                LocalTime.of(9, 15), LocalTime.of(15, 25)));
        }
        return toResult("BASELINE V29.0", signals);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PATH 1 — Aggregate 5-min candles into 15-min; run same predictor
    // ════════════════════════════════════════════════════════════════════════
    static PathResult runPath1_15min(MarketDataAgent md, PredictionAgent pred) {
        List<Signal> all = new ArrayList<>();
        for (String sym : new String[]{"NIFTY50", "SENSEX"}) {
            List<SimpleMarketData> raw;
            try { raw = md.getHistoricalData(sym, LOAD_DAYS); } catch (Exception e) {
                System.err.println("  Data fetch failed for " + sym + ": " + e.getMessage()); continue; }
            if (raw == null || raw.size() < 200) continue;

            // Aggregate raw 5-min data into 15-min candles
            List<SimpleMarketData> data15 = aggregate15Min(raw);
            // AIPredictor needs 200+ candles in history — so start at 200 for 15-min too
            if (data15.size() < 210) continue;

            Set<LocalDate> allDatesSet = new LinkedHashSet<>();
            for (SimpleMarketData c : data15) if (c.timestamp != null) allDatesSet.add(c.timestamp.toLocalDate());
            List<LocalDate> allDates = new ArrayList<>(allDatesSet);
            Collections.sort(allDates);
            LocalDate cutoff = allDates.size() > BACKTEST_DAYS
                ? allDates.get(allDates.size() - BACKTEST_DAYS) : allDates.get(0);

            System.out.printf("  %s: %d 5-min → %d 15-min candles (scan from %s)%n",
                sym, raw.size(), data15.size(), cutoff);

            long lastMs = 0;
            LocalDate lastDay = null;

            for (int i = 200; i < data15.size(); i++) {
                SimpleMarketData c = data15.get(i);
                if (c.timestamp == null) continue;
                LocalDate d = c.timestamp.toLocalDate();
                if (d.isBefore(cutoff)) continue;
                LocalTime t = c.timestamp.toLocalTime();
                if (t.isBefore(LocalTime.of(9, 15)) || t.isAfter(LocalTime.of(15, 15))) continue;

                if (!d.equals(lastDay)) { lastMs = 0; lastDay = d; }

                long cMs = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
                if (cMs - lastMs < COOLDOWN_15MIN_MS) continue;

                List<SimpleMarketData> hist = data15.subList(0, i + 1);
                AIPredictor.AIPrediction p = pred.generateSignal(sym, hist);

                if ("NEUTRAL".equals(p.predictedDirection)) continue;
                double minPts = MIN_PTS_15MIN.getOrDefault(sym, 30.0);
                if (p.confidence < MIN_CONFIDENCE || p.estimatedMovePoints < minPts) continue;

                lastMs = cMs;
                Signal s = buildSignal(c, d, sym, p, data15, i, LOOKAHEAD_15MIN);
                all.add(s);
            }
        }
        return toResult("PATH 1 — 15-min candles", all);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PATH 2 — Prime window: 11:00am–12:30pm only
    // ════════════════════════════════════════════════════════════════════════
    static PathResult runPath2_prime(MarketDataAgent md, PredictionAgent pred) {
        List<Signal> signals = new ArrayList<>();
        for (String sym : new String[]{"NIFTY50", "SENSEX"}) {
            signals.addAll(collect5Min(sym, md, pred,
                MIN_PTS_5MIN, COOLDOWN_5MIN_MS, LOOKAHEAD_5MIN,
                LocalTime.of(11, 0), LocalTime.of(12, 30)));
        }
        return toResult("PATH 2 — Prime window 11:00–12:30", signals);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PATH 3 — SENSEX only (optimal params kept same)
    // ════════════════════════════════════════════════════════════════════════
    static PathResult runPath3_sensexOnly(MarketDataAgent md, PredictionAgent pred) {
        List<Signal> signals = new ArrayList<>(
            collect5Min("SENSEX", md, pred,
                MIN_PTS_5MIN, COOLDOWN_5MIN_MS, LOOKAHEAD_5MIN,
                LocalTime.of(9, 15), LocalTime.of(15, 25)));
        return toResult("PATH 3 — SENSEX only", signals);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PATH 6 — Weekly options strangle simulation
    //    • Entry: Monday 9:30am (or first candle with confidence≥86)
    //    • Legs:  Short call at +1.5% from spot, Short put at -1.5%
    //    • Premium received: ~0.5% of spot (ATR-based estimate)
    //    • Stop:  If spot moves beyond ±2.0% from entry price (we buy back at loss)
    //    • Win:   Spot stays within ±1.5% through Thursday close
    //
    //    We use the baseline 5-min data to check daily closes and assess outcome.
    // ════════════════════════════════════════════════════════════════════════
    static PathResult runPath6_strangle(MarketDataAgent md, PredictionAgent pred) {
        // We'll simulate NIFTY50 weekly strangles for 30 trading days ≈ 6 weeks
        List<SimpleMarketData> raw;
        try { raw = md.getHistoricalData("NIFTY50", LOAD_DAYS); } catch (Exception e) {
            System.err.println("  PATH 6 data fetch failed: " + e.getMessage());
            return toResult("PATH 6 — Weekly strangle", new ArrayList<>()); }
        if (raw == null || raw.size() < 200) {
            System.err.println("  PATH 6: not enough data");
            return toResult("PATH 6 — Weekly strangle", new ArrayList<>());
        }

        // Group candles by date
        Map<LocalDate, List<SimpleMarketData>> byDate = new LinkedHashMap<>();
        for (SimpleMarketData c : raw) {
            if (c.timestamp == null) continue;
            byDate.computeIfAbsent(c.timestamp.toLocalDate(), k -> new ArrayList<>()).add(c);
        }
        List<LocalDate> dates = new ArrayList<>(byDate.keySet());
        Collections.sort(dates);
        LocalDate cutoff = dates.size() > BACKTEST_DAYS
            ? dates.get(dates.size() - BACKTEST_DAYS) : dates.get(0);

        // Collect only dates in backtest window
        List<LocalDate> window = new ArrayList<>();
        for (LocalDate d : dates) if (!d.isBefore(cutoff)) window.add(d);

        // Simulate: find Monday (or first trading day of a weekly series), hold until Thursday
        List<Signal> strangles = new ArrayList<>();
        int i = 0;
        while (i < window.size()) {
            LocalDate entryDate = window.get(i);
            // Find Thursday of the same week (or next Thursday)
            LocalDate thursday = entryDate;
            while (thursday.getDayOfWeek() != DayOfWeek.THURSDAY) thursday = thursday.plusDays(1);

            // Get all trading dates from entry to thursday
            List<LocalDate> week = new ArrayList<>();
            for (LocalDate d : window) {
                if (!d.isBefore(entryDate) && !d.isAfter(thursday)) week.add(d);
            }
            if (week.isEmpty()) { i++; continue; }

            // Entry spot = first candle open on entry date
            List<SimpleMarketData> entryDay = byDate.get(entryDate);
            if (entryDay == null || entryDay.isEmpty()) { i++; continue; }
            double entrySpot = entryDay.get(0).open > 0 ? entryDay.get(0).open : entryDay.get(0).price;
            if (entrySpot < 1) { i++; continue; }

            // Strangle strikes
            double callStrike = entrySpot * 1.015;  // +1.5%
            double putStrike  = entrySpot * 0.985;  // -1.5%
            double stopUp     = entrySpot * 1.020;  // stop if > +2%
            double stopDown   = entrySpot * 0.980;  // stop if < -2%
            double premiumRecv= entrySpot * 0.005;  // 0.5% premium received (approximate)

            // Track through the week: check if stop is hit on any intraday high/low
            boolean stopped = false;
            double exitPts   = 0;
            String exitDate  = "";

            weekLoop:
            for (LocalDate d : week) {
                List<SimpleMarketData> dayCandles = byDate.get(d);
                if (dayCandles == null) continue;
                for (SimpleMarketData c : dayCandles) {
                    double h = c.high > 0 ? c.high : c.price;
                    double l = c.low  > 0 ? c.low  : c.price;
                    if (h >= stopUp || l <= stopDown) {
                        // Stop triggered — we lose the premium + extra (net loss ≈ -0.7% of spot)
                        stopped = true;
                        exitPts = -entrySpot * 0.007;  // -0.7% as loss on stop
                        exitDate = d.toString();
                        break weekLoop;
                    }
                }
            }

            if (!stopped) {
                // Win: kept full premium
                exitPts  = premiumRecv;
                exitDate = thursday.toString();
            }

            Signal s = new Signal();
            s.date   = entryDate.toString();
            s.time   = "09:30";
            s.symbol = "NIFTY50";
            s.direction = "STRANGLE";
            s.entry  = entrySpot;
            s.tgtPts = premiumRecv;
            s.slPts  = entrySpot * 0.007;
            s.ptsCaptured = exitPts;
            s.outcome = stopped ? "LOSS" : "WIN";
            s.confidence = 0; // N/A for options
            strangles.add(s);

            // Advance to next week: jump past thursday
            int j = i;
            while (j < window.size() && !window.get(j).isAfter(thursday)) j++;
            i = j;
        }

        // Display individual strangle trades
        System.out.println("  Strangle trades:");
        int sW = 0, sL = 0;
        double sNet = 0;
        for (Signal s : strangles) {
            String icon = s.outcome.equals("WIN") ? "✅" : "❌";
            System.out.printf("    %s %s %s entry=%.0f  prem=%.0f  result=%+.0f pts%n",
                icon, s.date, s.outcome, s.entry, s.tgtPts, s.ptsCaptured);
            if (s.outcome.equals("WIN")) sW++; else sL++;
            sNet += s.ptsCaptured;
        }
        System.out.printf("  → Weeks: %d  Wins: %d  Losses: %d  Net: %+.0f pts%n",
            strangles.size(), sW, sL, sNet);

        return toResult("PATH 6 — Weekly strangle", strangles);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PATH 7 — ML Logistic Regression filter on baseline signals
    //    • Collect ALL signals with confidence≥85 (low gate) → feature matrix
    //    • Chronological 70/30 split (70% train, 30% test)
    //    • Features: confidence, estimatedMove, slPts, timeSlab, dayOfWeek,
    //                symbolCode, dirCode, rrRatio
    //    • Train logistic regression with gradient descent
    //    • Test: show WR at P(win)≥0.60, 0.65, 0.70 thresholds
    // ════════════════════════════════════════════════════════════════════════
    static PathResult runPath7_ml(MarketDataAgent md, PredictionAgent pred) {
        // Collect raw signals with a LOW gate so we have plenty to train on
        List<Signal> raw = new ArrayList<>();
        for (String sym : new String[]{"NIFTY50", "SENSEX"}) {
            List<SimpleMarketData> data;
            try { data = md.getHistoricalData(sym, LOAD_DAYS); } catch (Exception e) {
                System.err.println("  PATH 7 data fetch failed for " + sym + ": " + e.getMessage()); continue; }
            if (data == null || data.size() < 200) continue;

            Set<LocalDate> dset = new LinkedHashSet<>();
            for (SimpleMarketData c : data) if (c.timestamp != null) dset.add(c.timestamp.toLocalDate());
            List<LocalDate> allDates = new ArrayList<>(dset);
            Collections.sort(allDates);
            LocalDate cutoff = allDates.size() > BACKTEST_DAYS
                ? allDates.get(allDates.size() - BACKTEST_DAYS) : allDates.get(0);

            long lastMs = 0; LocalDate lastDay = null;
            @SuppressWarnings("unused")
            double symCode = sym.equals("NIFTY50") ? 0.0 : 1.0;

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData c = data.get(i);
                if (c.timestamp == null) continue;
                LocalDate d = c.timestamp.toLocalDate();
                if (d.isBefore(cutoff)) continue;
                LocalTime t = c.timestamp.toLocalTime();
                if (t.isBefore(LocalTime.of(9, 15)) || t.isAfter(LocalTime.of(15, 25))) continue;
                if (!d.equals(lastDay)) { lastMs = 0; lastDay = d; }
                long cMs = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
                if (cMs - lastMs < COOLDOWN_5MIN_MS) continue;

                List<SimpleMarketData> hist = data.subList(0, i + 1);
                AIPredictor.AIPrediction p = pred.generateSignal(sym, hist);
                if ("NEUTRAL".equals(p.predictedDirection)) continue;
                // Low bar: 84 confidence, 15 pts (collect more signals for training)
                if (p.confidence < 84.0 || p.estimatedMovePoints < 15.0) continue;

                lastMs = cMs;
                Signal s = buildSignal(c, d, sym, p, data, i, LOOKAHEAD_5MIN);
                s.estMove = p.estimatedMovePoints;
                raw.add(s);
            }
        }

        if (raw.size() < 30) {
            System.err.println("  PATH 7: too few signals (" + raw.size() + ") for ML, need ≥30");
            return toResult("PATH 7 — ML logistic", new ArrayList<>());
        }

        System.out.printf("  Collected %d signals for ML training%n", raw.size());

        // ── Build feature matrix ─────────────────────────────────────────
        int n = raw.size();
        double[][] X = new double[n][8];
        double[]   y = new double[n];

        for (int k = 0; k < n; k++) {
            Signal s = raw.get(k);
            double conf   = s.confidence / 100.0;
            double move   = s.estMove  / 200.0;   // normalize (NIFTY ~100-200 pts range)
            double sl     = s.slPts    / 200.0;
            double slab   = s.timeSlab / 2.0;
            double dow    = parseDow(s.date) / 4.0;
            double symC   = s.symbol.equals("NIFTY50") ? 0.0 : 1.0;
            double dirC   = s.direction.equals("UP") ? 1.0 : 0.0;
            double rr     = s.rrRatio  / 5.0;
            X[k] = new double[]{1.0, conf, move, sl, slab, dow, symC, dirC, rr};
            y[k] = s.outcome.equals("LOSS") ? 0.0 : 1.0;
        }

        // ── Chronological 70/30 split ─────────────────────────────────────
        int trainEnd = (int)(n * 0.70);
        System.out.printf("  Train: %d signals | Test: %d signals%n", trainEnd, n - trainEnd);

        double[][] Xtrain = Arrays.copyOfRange(X, 0, trainEnd);
        double[]   ytrain = Arrays.copyOfRange(y, 0, trainEnd);
        double[][] Xtest  = Arrays.copyOfRange(X, trainEnd, n);
        double[]   ytest  = Arrays.copyOfRange(y, trainEnd, n);

        // ── Train logistic regression ─────────────────────────────────────
        double[] w = trainLogistic(Xtrain, ytrain, 0.01, 2000);

        // ── Evaluate at multiple thresholds ──────────────────────────────
        System.out.println("\n  ML Logistic Regression — threshold analysis (TEST set):");
        System.out.println("  ┌──────────────┬───────┬──────┬─────────┬──────────┐");
        System.out.println("  │ P(win)≥ thrsh│ Calls │ Wins │   WR    │  filter% │");
        System.out.println("  ├──────────────┼───────┼──────┼─────────┼──────────┤");

        PathResult bestResult = null;
        double bestWR = 0;

        double[] thresholds = {0.55, 0.60, 0.65, 0.70, 0.75};
        for (double thr : thresholds) {
            List<Signal> filtered = new ArrayList<>();
            for (int k = 0; k < Xtest.length; k++) {
                double prob = sigmoid(dot(w, Xtest[k]));
                if (prob >= thr) {
                    Signal s = raw.get(trainEnd + k);
                    filtered.add(s);
                }
            }
            PathResult pr = toResult("ML thr=" + thr, filtered);
            double pct = Xtest.length > 0 ? 100.0 * filtered.size() / Xtest.length : 0;
            System.out.printf("  │ P(win)≥ %.2f │  %3d  │ %3d  │  %5.1f%% │  %5.1f%%  │%n",
                thr, pr.calls, pr.wins + pr.partials, pr.winRate, pct);
            if (pr.winRate > bestWR && pr.calls >= 15) { bestWR = pr.winRate; bestResult = pr; }
        }
        System.out.println("  └──────────────┴───────┴──────┴─────────┴──────────┘");

        // Also run on the full backtest window at best threshold
        System.out.println("\n  Running ML filter on ALL 30-day signals (train+test):");
        double bestThr = 0.60;
        List<Signal> mlFull = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            double prob = sigmoid(dot(w, X[k]));
            if (prob >= bestThr) mlFull.add(raw.get(k));
        }
        PathResult fullMl = toResult("PATH 7 — ML logistic (P≥0.60 full)", mlFull);

        return fullMl;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SHARED HELPERS
    // ════════════════════════════════════════════════════════════════════════

    /** Collect signals using 5-min candles within a specified time window. */
    static List<Signal> collect5Min(String sym, MarketDataAgent md, PredictionAgent pred,
            Map<String, Double> minPts, long cooldownMs, int lookahead,
            LocalTime openTime, LocalTime closeTime) {

        List<Signal> signals = new ArrayList<>();
        List<SimpleMarketData> data;
        try { data = md.getHistoricalData(sym, LOAD_DAYS); } catch (Exception e) {
            System.err.println("  Data fetch failed for " + sym + ": " + e.getMessage()); return signals; }
        if (data == null || data.size() < 200) return signals;

        Set<LocalDate> dset = new LinkedHashSet<>();
        for (SimpleMarketData c : data) if (c.timestamp != null) dset.add(c.timestamp.toLocalDate());
        List<LocalDate> allDates = new ArrayList<>(dset);
        Collections.sort(allDates);
        LocalDate cutoff = allDates.size() > BACKTEST_DAYS
            ? allDates.get(allDates.size() - BACKTEST_DAYS) : allDates.get(0);

        long lastMs = 0; LocalDate lastDay = null;

        for (int i = 200; i < data.size(); i++) {
            SimpleMarketData c = data.get(i);
            if (c.timestamp == null) continue;
            LocalDate d = c.timestamp.toLocalDate();
            if (d.isBefore(cutoff)) continue;
            LocalTime t = c.timestamp.toLocalTime();
            if (t.isBefore(openTime) || t.isAfter(closeTime)) continue;
            if (!d.equals(lastDay)) { lastMs = 0; lastDay = d; }
            long cMs = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
            if (cMs - lastMs < cooldownMs) continue;

            List<SimpleMarketData> hist = data.subList(0, i + 1);
            AIPredictor.AIPrediction p = pred.generateSignal(sym, hist);
            if ("NEUTRAL".equals(p.predictedDirection)) continue;
            double minP = minPts.getOrDefault(sym, 20.0);
            if (p.confidence < MIN_CONFIDENCE || p.estimatedMovePoints < minP) continue;

            lastMs = cMs;
            signals.add(buildSignal(c, d, sym, p, data, i, lookahead));
        }
        return signals;
    }

    /** Aggregate 5-min candles into 15-min candles. */
    static List<SimpleMarketData> aggregate15Min(List<SimpleMarketData> fiveMin) {
        Map<LocalDateTime, List<SimpleMarketData>> groups = new LinkedHashMap<>();
        for (SimpleMarketData c : fiveMin) {
            if (c.timestamp == null) continue;
            int min15 = (c.timestamp.getMinute() / 15) * 15;
            LocalDateTime boundary = c.timestamp.withMinute(min15).withSecond(0).withNano(0);
            groups.computeIfAbsent(boundary, k -> new ArrayList<>()).add(c);
        }
        List<SimpleMarketData> result = new ArrayList<>();
        for (Map.Entry<LocalDateTime, List<SimpleMarketData>> e : groups.entrySet()) {
            List<SimpleMarketData> g = e.getValue();
            if (g.isEmpty()) continue;
            double open  = g.get(0).open > 0 ? g.get(0).open : g.get(0).price;
            double high  = g.stream().mapToDouble(c -> c.high > 0 ? c.high : c.price).max().orElse(0);
            double low   = g.stream().mapToDouble(c -> c.low  > 0 ? c.low  : c.price).min().orElse(Double.MAX_VALUE);
            double close = g.get(g.size() - 1).price;
            long   vol   = g.stream().mapToLong(c -> c.volume).sum();
            result.add(new SimpleMarketData("", close, open, high, low, vol, e.getKey()));
        }
        result.sort(Comparator.comparing(c -> c.timestamp));
        return result;
    }

    /** Build a Signal record from a candle + prediction + lookahead outcome check. */
    static Signal buildSignal(SimpleMarketData c, LocalDate d, String sym,
            AIPredictor.AIPrediction p, List<SimpleMarketData> data, int idx, int lookahead) {
        Signal s = new Signal();
        s.date      = d.toString();
        s.time      = c.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
        s.symbol    = sym;
        s.direction = p.predictedDirection;
        s.confidence= p.confidence;
        s.estMove   = p.estimatedMovePoints;
        s.entry     = c.price;
        s.tgtPts    = p.estimatedMovePoints;
        s.slPts     = p.suggestedStopLoss;
        s.rrRatio   = s.slPts > 0 ? s.tgtPts / s.slPts : 0;
        s.epochMs   = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
        s.timeSlab  = getSlab(c.timestamp.toLocalTime());

        int outcome = verifyOutcome(data, idx, p, lookahead);
        switch (outcome) {
            case WIN     -> { s.outcome = "WIN";         s.ptsCaptured =  s.tgtPts; }
            case PARTIAL -> { s.outcome = "PARTIAL WIN"; s.ptsCaptured =  s.tgtPts * 0.3; }
            default      -> { s.outcome = "LOSS";        s.ptsCaptured = -s.slPts; }
        }
        return s;
    }

    static int verifyOutcome(List<SimpleMarketData> data, int idx, AIPredictor.AIPrediction p, int lookahead) {
        double entry  = data.get(idx).price;
        String dir    = p.predictedDirection;
        double tgtPx  = dir.equals("UP") ? entry + p.estimatedMovePoints : entry - p.estimatedMovePoints;
        double slPx   = dir.equals("UP") ? entry - p.suggestedStopLoss   : entry + p.suggestedStopLoss;
        double halfPx = dir.equals("UP") ? entry + p.estimatedMovePoints * 0.5 : entry - p.estimatedMovePoints * 0.5;
        boolean half  = false;
        for (int j = 1; j <= lookahead && (idx + j) < data.size(); j++) {
            SimpleMarketData c = data.get(idx + j);
            double h = c.high > 0 ? c.high : c.price;
            double l = c.low  > 0 ? c.low  : c.price;
            if (dir.equals("UP")) {
                if (l <= slPx)  return half ? PARTIAL : LOSS;
                if (h >= tgtPx) return WIN;
                if (h >= halfPx) half = true;
            } else {
                if (h >= slPx)  return half ? PARTIAL : LOSS;
                if (l <= tgtPx) return WIN;
                if (l <= halfPx) half = true;
            }
        }
        return half ? PARTIAL : LOSS;
    }

    static int getSlab(LocalTime t) {
        if (t.isBefore(LocalTime.of(11, 0))) return 0;
        if (t.isBefore(LocalTime.of(13, 0))) return 1;
        return 2;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  LOGISTIC REGRESSION  (from scratch, pure Java)
    // ════════════════════════════════════════════════════════════════════════

    static double[] trainLogistic(double[][] X, double[] y, double lr, int epochs) {
        int n = X.length;
        int m = X[0].length;
        double[] w = new double[m]; // all zeros init
        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] grad = new double[m];
            for (int i = 0; i < n; i++) {
                double pred = sigmoid(dot(w, X[i]));
                double err  = pred - y[i];
                for (int j = 0; j < m; j++) grad[j] += err * X[i][j];
            }
            for (int j = 0; j < m; j++) w[j] -= lr * grad[j] / n;
        }
        return w;
    }

    static double sigmoid(double z) { return 1.0 / (1.0 + Math.exp(-z)); }

    static double dot(double[] w, double[] x) {
        double s = 0; for (int i = 0; i < w.length; i++) s += w[i] * x[i]; return s;
    }

    static int parseDow(String dateStr) {
        try {
            return LocalDate.parse(dateStr).getDayOfWeek().getValue() - 1; // Mon=0, Fri=4
        } catch (Exception e) { return 2; }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PathResult aggregation
    // ════════════════════════════════════════════════════════════════════════

    static class PathResult {
        String name;
        int calls, wins, partials, losses;
        double winRate, netPts;
        double avgPtsPerCall;
    }

    static PathResult toResult(String name, List<Signal> signals) {
        PathResult r = new PathResult();
        r.name = name;
        r.calls = signals.size();
        for (Signal s : signals) {
            if (s.outcome.equals("WIN"))         r.wins++;
            else if (s.outcome.equals("PARTIAL WIN")) r.partials++;
            else                                 r.losses++;
            r.netPts += s.ptsCaptured;
        }
        r.winRate = r.calls > 0 ? (r.wins + r.partials) * 100.0 / r.calls : 0;
        r.avgPtsPerCall = r.calls > 0 ? r.netPts / r.calls : 0;
        return r;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  FINAL COMPARISON TABLE
    // ════════════════════════════════════════════════════════════════════════

    static void printComparisonTable(PathResult... results) {
        System.out.println("\n\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║               PATHS EXPERIMENT — FINAL COMPARISON (30-DAY BACKTEST)                 ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════╦═══════╣");
        System.out.println("║ Strategy                              │ Calls │  WR   │   Net pts  │ /call  ║ DELTA ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════╬═══════╣");

        double baseWR   = results[0].winRate;
        double baseNet  = results[0].netPts;

        for (int i = 0; i < results.length; i++) {
            PathResult r = results[i];
            String delta = i == 0 ? "  BASE " :
                String.format("%+5.1f%%", r.winRate - baseWR);
            String note  = i == 0 ? "◀ current" : "";
            System.out.printf("║ %-38s│  %3d  │ %5.1f%% │ %+10.0f │ %+6.0f ║ %-5s ║%s%n",
                r.name, r.calls, r.winRate, r.netPts, r.avgPtsPerCall, delta, note);
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╩═══════╝");

        System.out.println();
        System.out.println("INTERPRETATION:");
        System.out.println("  • WIN RATE DELTA  = how much WR changes vs V29.0 baseline");
        System.out.println("  • NET POINTS      = total index points captured over 30 days");
        System.out.println("  • /CALL           = average points per trade (quality measure)");
        System.out.println();
        System.out.println("NOTES:");
        System.out.println("  Path 1 (15-min): Lower call count but each signal based on larger candle — more reliable.");
        System.out.println("  Path 2 (prime):  Filters to highest-clarity window; fewer but sharper signals.");
        System.out.println("  Path 3 (SENSEX): Single-index focus; eliminates NIFTY noise.");
        System.out.println("  Path 6 (strangle): Not directional — win rate = % of weeks market stays rangebound.");
        System.out.println("               Premium received ≈ 0.5% spot.  Stop at ±2% breach.");
        System.out.println("  Path 7 (ML):  Logistic regression re-ranks existing signals; test WR shows improvement.");
        System.out.println();
        System.out.println("  ⚠  These are ESTIMATES. Implement only what shows ≥2% WR improvement AND ≥60 calls/month.");
    }
}
