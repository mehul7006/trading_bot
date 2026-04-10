package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.agents.PredictionAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.ai.MLSignalFilter;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * BacktestCommitCompare — Apples-to-apples comparison of two bot versions
 * on identical 30-day market data.
 *
 *  ┌─────────────────────────────────────────────────────────────────────┐
 *  │  COMMIT bd3552e  (V29.0 baseline)                                   │
 *  │   • Full market hours: 9:15 – 15:25                                 │
 *  │   • Confidence gate : 86%                                           │
 *  │   • No ML filter    : every qualifying signal sent                  │
 *  │   • Symbols         : NIFTY50 + SENSEX                              │
 *  ├─────────────────────────────────────────────────────────────────────┤
 *  │  LATEST  (V29.0 + ML only, Path 7)                                  │
 *  │   • Full market hours: 9:15 – 15:25  (no prime window restriction)  │
 *  │   • Confidence gate : 86%                                           │
 *  │   • ML filter       : P(win) ≥ 0.60 (logistic regression, 45-day)  │
 *  │   • Prime window REMOVED — ML alone gives higher WR + more calls    │
 *  │   • Symbols         : NIFTY50 + SENSEX                              │
 *  └─────────────────────────────────────────────────────────────────────┘
 *
 *  ML training uses a walk-forward approach:
 *    – Train on candles BEFORE the backtest window (older data)
 *    – Test on the 30-day backtest window
 *  This avoids look-ahead bias.
 */
public class BacktestCommitCompare {

    // ── Shared constants ──────────────────────────────────────────────────────
    private static final int    LOAD_DAYS        = 60;   // load more: 30 train + 30 test
    private static final int    BACKTEST_DAYS    = 30;
    private static final double MIN_CONFIDENCE   = 86.0;
    private static final long   COOLDOWN_MS      = 10L * 60 * 1_000;
    private static final int    LOOKAHEAD        = 24;

    private static final Map<String, Double> MIN_PTS = Map.of(
        "NIFTY50", 28.0,
        "SENSEX",  65.0
    );

    // ── ML training constants (same as MLSignalFilter) ─────────────────────────
    private static final double ML_THRESHOLD     = 0.60;
    private static final double ML_CONF_MIN      = 84.0;
    private static final double ML_PTS_MIN       = 15.0;

    private static final int LOSS = 0, PARTIAL = 1, WIN = 2;

    // ── Signal record ──────────────────────────────────────────────────────────
    static class Sig {
        String date, time, symbol, direction, outcome;
        double entry, tgtPts, slPts, pts, confidence, estMove;
        long   epochMs;
        int    timeSlab;
    }

    // ══════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║        COMMIT COMPARISON — 30-DAY BACKTEST (same market data)       ║");
        System.out.println("║   bd3552e  V29.0 baseline  vs  LATEST  V29.0 + ML only (Path 7)    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        MarketDataAgent md   = new MarketDataAgent();
        PredictionAgent pred = new PredictionAgent();

        // ── Step 1: Load data for both symbols ─────────────────────────────────
        System.out.println("Loading 60-day market data (30 train + 30 backtest)...");
        Map<String, List<SimpleMarketData>> allData = new LinkedHashMap<>();
        for (String sym : new String[]{"NIFTY50", "SENSEX"}) {
            try {
                List<SimpleMarketData> d = md.getHistoricalData(sym, LOAD_DAYS);
                if (d != null && d.size() >= 200) {
                    allData.put(sym, d);
                    System.out.printf("  %s: %d candles loaded%n", sym, d.size());
                } else {
                    System.err.println("  " + sym + ": insufficient data");
                }
            } catch (Exception e) {
                System.err.println("  " + sym + ": fetch failed — " + e.getMessage());
            }
        }
        System.out.println();

        // ── Step 2: Identify 30-day backtest window ────────────────────────────
        // All dates across both symbols
        Set<LocalDate> allDatesSet = new LinkedHashSet<>();
        for (List<SimpleMarketData> data : allData.values()) {
            for (SimpleMarketData c : data) if (c.timestamp != null) allDatesSet.add(c.timestamp.toLocalDate());
        }
        List<LocalDate> allDates = new ArrayList<>(allDatesSet);
        Collections.sort(allDates);
        LocalDate backtestCutoff = allDates.size() > BACKTEST_DAYS
            ? allDates.get(allDates.size() - BACKTEST_DAYS) : allDates.get(0);
        LocalDate trainCutoff   = allDates.size() > BACKTEST_DAYS
            ? allDates.get(allDates.size() - BACKTEST_DAYS) : allDates.get(0);

        System.out.printf("Backtest window : %s → %s%n", backtestCutoff, allDates.get(allDates.size() - 1));
        System.out.printf("ML train window : before %s%n%n", trainCutoff);

        // ── Step 3: Train ML model on PRE-backtest data (walk-forward, no lookahead) ─
        System.out.println("Training ML model on pre-backtest data (walk-forward)...");
        double[] mlWeights = trainML(allData, pred, trainCutoff);
        boolean  mlReady   = mlWeights != null;
        System.out.println(mlReady
            ? "  ✅ ML model trained successfully"
            : "  ⚠️  ML training failed — ML filter will be skipped");
        System.out.println();

        // ── Step 4: Collect ALL signals in backtest window (shared raw set) ────
        System.out.println("Generating signals for 30-day backtest window...");
        List<Sig> rawSignals = collectSignals(allData, pred, backtestCutoff,
            LocalTime.of(9, 15), LocalTime.of(15, 25));
        System.out.printf("  Raw signals generated: %d%n%n", rawSignals.size());

        // ── Step 5: Apply each commit's filter logic ───────────────────────────

        // bd3552e — V29.0 baseline: no ML, no window restriction
        List<Sig> v29 = new ArrayList<>(rawSignals); // all signals pass

        // 8e3022b — V29.0 + Path 2+7: prime window + ML filter
        List<Sig> v30 = applyPath2And7(rawSignals, mlWeights, mlReady);

        // ── Step 6: Print comparison report ───────────────────────────────────
        printDayByDay(v29, v30, backtestCutoff);
        printComparisonTable(v29, v30);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Signal collection (no time-window filter — collect everything)
    // ══════════════════════════════════════════════════════════════════════════
    static List<Sig> collectSignals(Map<String, List<SimpleMarketData>> allData,
            PredictionAgent pred, LocalDate cutoff,
            LocalTime open, LocalTime close) {

        List<Sig> out = new ArrayList<>();
        for (Map.Entry<String, List<SimpleMarketData>> e : allData.entrySet()) {
            String sym = e.getKey();
            List<SimpleMarketData> data = e.getValue();

            long lastMs = 0; LocalDate lastDay = null;

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData c = data.get(i);
                if (c.timestamp == null) continue;
                LocalDate d = c.timestamp.toLocalDate();
                if (d.isBefore(cutoff)) continue;
                LocalTime t = c.timestamp.toLocalTime();
                if (t.isBefore(open) || t.isAfter(close)) continue;
                if (!d.equals(lastDay)) { lastMs = 0; lastDay = d; }
                long cMs = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
                if (cMs - lastMs < COOLDOWN_MS) continue;

                AIPredictor.AIPrediction p;
                try { p = pred.generateSignal(sym, data.subList(0, i + 1)); }
                catch (Exception ex) { continue; }

                if ("NEUTRAL".equals(p.predictedDirection)) continue;
                double minP = MIN_PTS.getOrDefault(sym, 20.0);
                if (p.confidence < MIN_CONFIDENCE || p.estimatedMovePoints < minP) continue;

                lastMs = cMs;

                Sig s = new Sig();
                s.date      = d.toString();
                s.time      = c.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
                s.symbol    = sym;
                s.direction = p.predictedDirection;
                s.confidence= p.confidence;
                s.estMove   = p.estimatedMovePoints;
                s.entry     = c.price;
                s.tgtPts    = p.estimatedMovePoints;
                s.slPts     = p.suggestedStopLoss;
                s.epochMs   = cMs;
                s.timeSlab  = getSlab(t);

                int outcome = verifyOutcome(data, i, p);
                switch (outcome) {
                    case WIN     -> { s.outcome = "WIN";         s.pts =  s.tgtPts; }
                    case PARTIAL -> { s.outcome = "PARTIAL WIN"; s.pts =  s.tgtPts * 0.3; }
                    default      -> { s.outcome = "LOSS";        s.pts = -s.slPts; }
                }
                out.add(s);
            }
        }
        out.sort(Comparator.comparing((Sig sg) -> sg.date).thenComparing(sg -> sg.time));
        return out;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Apply ML-only filter (Path 7, no prime window restriction)
    //  Full market hours: 9:15 – 15:25.  Only gate = P(win) ≥ 0.60.
    // ══════════════════════════════════════════════════════════════════════════
    static List<Sig> applyPath2And7(List<Sig> raw, double[] w, boolean mlReady) {
        List<Sig> out = new ArrayList<>();
        for (Sig s : raw) {
            // ML filter: score every signal regardless of time
            if (mlReady) {
                int dow = LocalDate.parse(s.date).getDayOfWeek().getValue() - 1;
                double rr = s.slPts > 0 ? s.tgtPts / s.slPts : 0;
                double prob = sigmoid(dot(w, features(s.confidence, s.estMove, s.slPts,
                                                       s.timeSlab, dow, s.symbol, s.direction, rr)));
                if (prob < ML_THRESHOLD) continue;
            }
            out.add(s);
        }
        return out;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ML training: train on signals from BEFORE backtestCutoff
    // ══════════════════════════════════════════════════════════════════════════
    static double[] trainML(Map<String, List<SimpleMarketData>> allData,
                             PredictionAgent pred, LocalDate cutoff) {
        List<double[]> Xlist = new ArrayList<>();
        List<Double>   ylist = new ArrayList<>();

        for (Map.Entry<String, List<SimpleMarketData>> e : allData.entrySet()) {
            String sym = e.getKey();
            List<SimpleMarketData> data = e.getValue();
            long lastMs = 0; LocalDate lastDay = null;

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData c = data.get(i);
                if (c.timestamp == null) continue;
                LocalDate d = c.timestamp.toLocalDate();
                if (!d.isBefore(cutoff)) continue;   // train ONLY on pre-backtest data
                LocalTime t = c.timestamp.toLocalTime();
                if (t.isBefore(LocalTime.of(9, 15)) || t.isAfter(LocalTime.of(15, 25))) continue;
                if (!d.equals(lastDay)) { lastMs = 0; lastDay = d; }
                long cMs = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
                if (cMs - lastMs < COOLDOWN_MS) continue;

                AIPredictor.AIPrediction p;
                try { p = pred.generateSignal(sym, data.subList(0, i + 1)); }
                catch (Exception ex) { continue; }

                if ("NEUTRAL".equals(p.predictedDirection)) continue;
                if (p.confidence < ML_CONF_MIN || p.estimatedMovePoints < ML_PTS_MIN) continue;

                lastMs = cMs;
                int slab = getSlab(t);
                int dow  = d.getDayOfWeek().getValue() - 1;
                double rr= p.suggestedStopLoss > 0 ? p.estimatedMovePoints / p.suggestedStopLoss : 0;

                int outcome = verifyOutcome(data, i, p);
                Xlist.add(features(p.confidence, p.estimatedMovePoints, p.suggestedStopLoss,
                                   slab, dow, sym, p.predictedDirection, rr));
                ylist.add(outcome > 0 ? 1.0 : 0.0);
            }
        }

        if (Xlist.size() < 20) {
            System.out.printf("  ⚠️  Only %d pre-backtest signals — ML training skipped%n", Xlist.size());
            return null;
        }
        System.out.printf("  Training on %d pre-backtest signals...%n", Xlist.size());
        double[][] X = Xlist.toArray(new double[0][]);
        double[]   y = ylist.stream().mapToDouble(Double::doubleValue).toArray();
        return trainLogistic(X, y, 0.01, 2000);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Day-by-day comparison output
    // ══════════════════════════════════════════════════════════════════════════
    static void printDayByDay(List<Sig> v29, List<Sig> v30, LocalDate cutoff) {
        // Group by date
        Map<String, List<Sig>> byDateV29 = groupByDate(v29);
        Map<String, List<Sig>> byDateV30 = groupByDate(v30);

        Set<String> allDates = new TreeSet<>();
        allDates.addAll(byDateV29.keySet());
        allDates.addAll(byDateV30.keySet());

        System.out.println("┌─────────────┬────────────────────────────────┬────────────────────────────────┐");
        System.out.println("│    DATE     │   bd3552e  V29.0 BASELINE      │   8e3022b  ML + PRIME WINDOW   │");
        System.out.println("│             │  Calls  WR      Net pts         │  Calls  WR      Net pts         │");
        System.out.println("├─────────────┼────────────────────────────────┼────────────────────────────────┤");

        for (String date : allDates) {
            List<Sig> d29 = byDateV29.getOrDefault(date, List.of());
            List<Sig> d30 = byDateV30.getOrDefault(date, List.of());

            String row29 = fmtDay(d29);
            String row30 = fmtDay(d30);

            // Highlight improvement
            double wr29 = dayWR(d29);
            double wr30 = dayWR(d30);
            String flag = wr30 > wr29 + 5  ? " ▲" : wr30 < wr29 - 5 ? " ▼" : "  ";

            System.out.printf("│ %-11s │ %-30s │ %-30s│%s%n", date, row29, row30, flag);
        }
        System.out.println("└─────────────┴────────────────────────────────┴────────────────────────────────┘");
        System.out.println("  ▲ = new version better  ▼ = new version worse  (by >5% WR)\n");
    }

    static String fmtDay(List<Sig> sigs) {
        if (sigs.isEmpty()) return "  0 calls  —  no signal      ";
        int w = 0, p = 0, l = 0; double net = 0;
        for (Sig s : sigs) {
            if ("WIN".equals(s.outcome))          w++;
            else if ("PARTIAL WIN".equals(s.outcome)) p++;
            else l++;
            net += s.pts;
        }
        double wr = (w + p) * 100.0 / sigs.size();
        return String.format(" %2d calls  %5.1f%%  %+8.0f pts", sigs.size(), wr, net);
    }

    static double dayWR(List<Sig> sigs) {
        if (sigs.isEmpty()) return 0;
        long wp = sigs.stream().filter(s -> !"LOSS".equals(s.outcome)).count();
        return wp * 100.0 / sigs.size();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Final comparison table
    // ══════════════════════════════════════════════════════════════════════════
    static void printComparisonTable(List<Sig> v29, List<Sig> v30) {
        Result r29 = toResult(v29);
        Result r30 = toResult(v30);

        double wrDelta  = r30.wr  - r29.wr;
        double netDelta = r30.net - r29.net;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║       30-DAY HONEST BACKTEST — bd3552e (V29.0)  vs  LATEST (ML only)            ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Metric                │  bd3552e  V29.0 baseline  │  LATEST   ML only (P≥60%%) ║%n");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Total calls           │         %3d               │         %3d               ║%n", r29.calls, r30.calls);
        System.out.printf( "║  Full Wins (✅)         │         %3d               │         %3d               ║%n", r29.wins,  r30.wins);
        System.out.printf( "║  Partial Wins (🔶)      │         %3d               │         %3d               ║%n", r29.partials, r30.partials);
        System.out.printf( "║  Losses (❌)            │         %3d               │         %3d               ║%n", r29.losses, r30.losses);
        System.out.printf( "║  Win Rate              │       %5.1f%%              │       %5.1f%%  (%+.1f%%)   ║%n", r29.wr, r30.wr, wrDelta);
        System.out.printf( "║  Net Points (30 days)  │      %+7.0f pts           │      %+7.0f pts  (%+.0f)   ║%n", r29.net, r30.net, netDelta);
        System.out.printf( "║  Avg pts per call      │       %+6.0f pts           │       %+6.0f pts           ║%n", r29.perCall, r30.perCall);
        System.out.printf( "║  Best day net          │       %+6.0f pts           │       %+6.0f pts           ║%n", r29.bestDay, r30.bestDay);
        System.out.printf( "║  Worst day net         │       %+6.0f pts           │       %+6.0f pts           ║%n", r29.worstDay, r30.worstDay);
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");

        String verdict;
        if (wrDelta >= 5.0 && r30.calls >= 30)       verdict = "✅ CLEAR IMPROVEMENT — deploy recommended";
        else if (wrDelta >= 2.0 && r30.calls >= 20)   verdict = "🔶 MARGINAL IMPROVEMENT — monitor live";
        else if (wrDelta < 0 && r30.calls < r29.calls * 0.4) verdict = "❌ WR gain offset by too few calls";
        else if (wrDelta < -2.0)                      verdict = "❌ REGRESSION — revert recommended";
        else                                           verdict = "🔶 NEUTRAL — no significant change";

        System.out.printf( "║  VERDICT: %-71s║%n", verdict);
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════╝");

        // Per-symbol breakdown
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              PER-SYMBOL BREAKDOWN                                    ║");
        System.out.println("╠═══════════════╦════════════════════════╦════════════════════════════╣");
        System.out.println("║ Symbol        ║  bd3552e  (V29.0)      ║  LATEST   (ML only)        ║");
        System.out.println("╠═══════════════╬════════════════════════╬════════════════════════════╣");

        for (String sym : new String[]{"NIFTY50", "SENSEX"}) {
            Result rs29 = toResult(v29.stream().filter(s -> sym.equals(s.symbol)).collect(java.util.stream.Collectors.toList()));
            Result rs30 = toResult(v30.stream().filter(s -> sym.equals(s.symbol)).collect(java.util.stream.Collectors.toList()));
            System.out.printf("║ %-13s ║ %3d calls  %5.1f%%  %+6.0f ║ %3d calls  %5.1f%%  %+6.0f  ║%n",
                sym, rs29.calls, rs29.wr, rs29.net, rs30.calls, rs30.wr, rs30.net);
        }
        System.out.println("╚═══════════════╩════════════════════════╩════════════════════════════╝");

        System.out.println();
        System.out.println("NOTE: ML model trained on data BEFORE the 30-day backtest window (walk-forward).");
        System.out.println("      This prevents look-ahead bias. Live bot retrains at every startup.");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    static class Result {
        int calls, wins, partials, losses;
        double wr, net, perCall, bestDay, worstDay;
    }

    static Result toResult(List<Sig> sigs) {
        Result r = new Result();
        r.calls = sigs.size();
        r.bestDay  = Double.NEGATIVE_INFINITY;
        r.worstDay = Double.POSITIVE_INFINITY;

        Map<String, Double> dayNet = new LinkedHashMap<>();
        for (Sig s : sigs) {
            if ("WIN".equals(s.outcome))          r.wins++;
            else if ("PARTIAL WIN".equals(s.outcome)) r.partials++;
            else r.losses++;
            r.net += s.pts;
            dayNet.merge(s.date, s.pts, Double::sum);
        }
        r.wr      = r.calls > 0 ? (r.wins + r.partials) * 100.0 / r.calls : 0;
        r.perCall = r.calls > 0 ? r.net / r.calls : 0;

        for (double d : dayNet.values()) {
            if (d > r.bestDay)  r.bestDay  = d;
            if (d < r.worstDay) r.worstDay = d;
        }
        if (dayNet.isEmpty()) { r.bestDay = 0; r.worstDay = 0; }
        return r;
    }

    static Map<String, List<Sig>> groupByDate(List<Sig> sigs) {
        Map<String, List<Sig>> m = new LinkedHashMap<>();
        for (Sig s : sigs) m.computeIfAbsent(s.date, k -> new ArrayList<>()).add(s);
        return m;
    }

    static int verifyOutcome(List<SimpleMarketData> data, int idx, AIPredictor.AIPrediction p) {
        double entry  = data.get(idx).price;
        String dir    = p.predictedDirection;
        double tgtPx  = "UP".equals(dir) ? entry + p.estimatedMovePoints : entry - p.estimatedMovePoints;
        double slPx   = "UP".equals(dir) ? entry - p.suggestedStopLoss   : entry + p.suggestedStopLoss;
        double halfPx = "UP".equals(dir) ? entry + p.estimatedMovePoints * 0.5 : entry - p.estimatedMovePoints * 0.5;
        boolean half  = false;
        for (int j = 1; j <= LOOKAHEAD && (idx + j) < data.size(); j++) {
            SimpleMarketData c = data.get(idx + j);
            double h = c.high > 0 ? c.high : c.price;
            double l = c.low  > 0 ? c.low  : c.price;
            if ("UP".equals(dir)) {
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

    // Logistic regression
    static double[] trainLogistic(double[][] X, double[] y, double lr, int epochs) {
        int m = X[0].length;
        double[] w = new double[m];
        for (int e = 0; e < epochs; e++) {
            double[] g = new double[m];
            for (int i = 0; i < X.length; i++) {
                double err = sigmoid(dot(w, X[i])) - y[i];
                for (int j = 0; j < m; j++) g[j] += err * X[i][j];
            }
            for (int j = 0; j < m; j++) w[j] -= lr * g[j] / X.length;
        }
        return w;
    }
    static double sigmoid(double z) { return 1.0 / (1.0 + Math.exp(-z)); }
    static double dot(double[] w, double[] x) {
        double s = 0; for (int i = 0; i < w.length; i++) s += w[i] * x[i]; return s;
    }
    static double[] features(double conf, double move, double sl,
                               int slab, int dow, String sym, String dir, double rr) {
        return new double[]{1.0, conf/100.0, move/200.0, sl/200.0, slab/2.0,
                            dow/4.0, "NIFTY50".equals(sym)?0.0:1.0, "UP".equals(dir)?1.0:0.0, rr/5.0};
    }
}
