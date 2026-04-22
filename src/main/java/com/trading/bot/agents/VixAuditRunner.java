package com.trading.bot.agents;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.ai.AIPredictor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

/**
 * VixAuditRunner — Side-by-side 60-day audit comparison:
 *   Baseline (no filter)  vs  VIX-filtered (skip high/low vol days)
 *
 * VIX Proxy = 10-day rolling annualised realised volatility of NIFTY returns.
 *   High VIX  → proxy > 20  → choppy/event-driven → skip signals
 *   Low  VIX  → proxy < 10  → no movement        → skip signals
 *   Normal    → 10–20       → ideal trading zone  → allow signals
 */
public class VixAuditRunner {

    // ── Same gates as live bot ─────────────────────────────────────────────
    private static final double MIN_CONFIDENCE = 85.0;
    private static final Map<String, Double> MIN_POINTS = Map.of(
        "NIFTY50", 25.0, "SENSEX", 60.0, "BANKNIFTY", 70.0
    );
    private static final long COOLDOWN_MS         = 10L * 60 * 1000;
    private static final int  LOOKAHEAD           = 24;

    // ── VIX thresholds ────────────────────────────────────────────────────
    private static final double VIX_HIGH = 20.0;  // skip if above
    private static final double VIX_LOW  = 10.0;  // skip if below

    // ── Per-run accumulator ───────────────────────────────────────────────
    static class RunResult {
        int    totalTrades, fullWins, partialWins, losses;
        double netPoints;
        int    skippedDays;

        double winRate() {
            return totalTrades > 0
                ? (double)(fullWins + partialWins) / totalTrades * 100.0 : 0;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        System.out.println("🔬 VIX-FILTER IMPACT ANALYSIS — 60-Day Comparison");
        System.out.println("=======================================================================");

        MarketDataAgent mdAgent   = new MarketDataAgent(HonestMarketDataFetcher.getInstance());
        PredictionAgent predAgent = new PredictionAgent();

        // ── Build VIX proxy map from NIFTY OHLCV ──────────────────────
        System.out.println("\n⏳ Loading NIFTY data to compute VIX proxy...");
        List<SimpleMarketData> niftyFull;
        try { niftyFull = mdAgent.getHistoricalData("NIFTY50", 120); }
        catch (Exception e) { System.err.println("Failed to load NIFTY data: " + e.getMessage()); return; }
        Map<LocalDate, Double> vixProxy  = computeVixProxy(niftyFull);

        printVixStats(vixProxy);

        // ── Run audit for each symbol ──────────────────────────────────
        String[]  symbols   = {"NIFTY50", "SENSEX"};
        RunResult baseTotal = new RunResult();
        RunResult vixTotal  = new RunResult();

        for (String symbol : symbols) {
            List<SimpleMarketData> data;
            try { data = mdAgent.getHistoricalData(symbol, 60); }
            catch (Exception e) { System.err.println("Failed: " + symbol); continue; }
            if (data == null || data.size() < 200) {
                System.err.println("⚠️  Not enough data for " + symbol);
                continue;
            }

            RunResult base = run(symbol, data, predAgent, vixProxy, false);
            RunResult vixF = run(symbol, data, predAgent, vixProxy, true);

            System.out.printf("%n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━%n");
            System.out.printf("  📊 %-10s%n", symbol);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.printf("  %-26s : %s%n", "Baseline (no filter)",  fmt(base));
            System.out.printf("  %-26s : %s%n", "With VIX Filter",       fmt(vixF));
            System.out.printf("  %-26s : calls %+d  |  WR %+.1f%%  |  Net %+.1f pts%n",
                "Change",
                vixF.totalTrades - base.totalTrades,
                vixF.winRate()   - base.winRate(),
                vixF.netPoints   - base.netPoints);
            System.out.printf("  High-VIX days skipped  : %d%n", vixF.skippedDays);

            addTo(baseTotal, base);
            addTo(vixTotal,  vixF);
        }

        // ── Combined summary ───────────────────────────────────────────
        System.out.println("\n=======================================================================");
        System.out.println("  📋 COMBINED — All 3 Indices — 60 Days");
        System.out.println("=======================================================================");
        System.out.printf("  Baseline (no filter)   : %s%n", fmt(baseTotal));
        System.out.printf("  With VIX Filter        : %s%n", fmt(vixTotal));
        System.out.println("-----------------------------------------------------------------------");
        int removedCalls = baseTotal.totalTrades - vixTotal.totalTrades;
        double pctRemoved = baseTotal.totalTrades > 0
            ? (double) removedCalls / baseTotal.totalTrades * 100 : 0;
        System.out.printf("  Calls removed          : %d  (%.1f%% fewer calls)%n", removedCalls, pctRemoved);
        System.out.printf("  WR improvement         : %+.1f%%%n",  vixTotal.winRate() - baseTotal.winRate());
        System.out.printf("  Net points change      : %+.1f pts%n", vixTotal.netPoints - baseTotal.netPoints);
        System.out.printf("  High-VIX days skipped  : %d total%n", vixTotal.skippedDays);
        System.out.println("=======================================================================");
    }

    // ── Core audit loop ───────────────────────────────────────────────────
    private static RunResult run(String symbol,
                                  List<SimpleMarketData> data,
                                  PredictionAgent predAgent,
                                  Map<LocalDate, Double> vixProxy,
                                  boolean applyVixFilter) {
        RunResult res = new RunResult();
        long lastSignalMs = 0;
        Set<LocalDate> skippedSet = new HashSet<>();

        for (int i = 200; i < data.size(); i++) {
            SimpleMarketData candle = data.get(i);
            if (candle.timestamp == null) continue;

            LocalDate date = candle.timestamp.toLocalDate();
            LocalTime time = candle.timestamp.toLocalTime();

            // Market hours gate
            if (time.isBefore(LocalTime.of(9, 15)) || time.isAfter(LocalTime.of(15, 25))) continue;

            // ── VIX filter ───────────────────────────────────────────
            if (applyVixFilter) {
                Double vix = vixProxy.get(date);
                if (vix != null && (vix > VIX_HIGH || vix < VIX_LOW)) {
                    skippedSet.add(date);
                    continue;
                }
            }

            // Cooldown
            int slab = getSlab(time);
            long cooldown = ("BANKNIFTY".equals(symbol) && slab == 1) ? BNF_PRIME_COOLDOWN : COOLDOWN_MS;
            long candleMs = candle.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
            if (candleMs - lastSignalMs < cooldown) continue;

            // Generate signal
            List<SimpleMarketData> history = data.subList(0, i + 1);
            AIPredictor.AIPrediction pred  = predAgent.generateSignal(symbol, history);

            if (!passesGates(symbol, pred)) continue;

            // Signal fires
            lastSignalMs = candleMs;
            res.totalTrades++;

            int outcome = verifyOutcome(data, i, pred);
            if (outcome == 2) {
                res.fullWins++;
                res.netPoints += pred.estimatedMovePoints;
            } else if (outcome == 1) {
                res.partialWins++;
                res.netPoints += pred.estimatedMovePoints * 0.3;
            } else {
                res.losses++;
                res.netPoints -= pred.suggestedStopLoss;
            }
        }

        res.skippedDays = skippedSet.size();
        return res;
    }

    // ── VIX proxy: 10-day rolling annualised realised vol from NIFTY ─────
    static Map<LocalDate, Double> computeVixProxy(List<SimpleMarketData> niftyData) {
        // Collect last close per day
        Map<LocalDate, Double> dailyClose = new TreeMap<>();
        for (SimpleMarketData c : niftyData) {
            if (c.timestamp == null) continue;
            LocalDate d = c.timestamp.toLocalDate();
            dailyClose.merge(d, c.price, (a, b) -> b); // keep last candle = close
        }

        List<LocalDate>  dates   = new ArrayList<>(dailyClose.keySet());
        List<Double>     closes  = new ArrayList<>();
        for (LocalDate d : dates) closes.add(dailyClose.get(d));

        // Daily log returns
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            returns.add(Math.log(closes.get(i) / closes.get(i - 1)));
        }

        // 10-day rolling std → annualise
        Map<LocalDate, Double> vix = new TreeMap<>();
        int window = 10;
        for (int i = window; i < returns.size(); i++) {
            double mean = 0;
            for (int j = i - window; j < i; j++) mean += returns.get(j);
            mean /= window;
            double variance = 0;
            for (int j = i - window; j < i; j++) {
                double diff = returns.get(j) - mean;
                variance += diff * diff;
            }
            variance /= (window - 1);
            double annualVol = Math.sqrt(variance) * Math.sqrt(252) * 100.0;
            vix.put(dates.get(i + 1), annualVol); // +1 because returns lag closes by 1
        }
        return vix;
    }

    private static void printVixStats(Map<LocalDate, Double> vix) {
        if (vix.isEmpty()) return;
        long high   = vix.values().stream().filter(v -> v > VIX_HIGH).count();
        long low    = vix.values().stream().filter(v -> v < VIX_LOW).count();
        long normal = vix.values().stream().filter(v -> v >= VIX_LOW && v <= VIX_HIGH).count();
        OptionalDouble avgVix = vix.values().stream().mapToDouble(Double::doubleValue).average();
        OptionalDouble maxVix = vix.values().stream().mapToDouble(Double::doubleValue).max();
        System.out.printf("  Days analysed  : %d%n", vix.size());
        System.out.printf("  Avg VIX proxy  : %.1f%n", avgVix.orElse(0));
        System.out.printf("  Max VIX proxy  : %.1f%n", maxVix.orElse(0));
        System.out.printf("  High VIX days (>%.0f) : %d  → will be SKIPPED%n", VIX_HIGH, high);
        System.out.printf("  Low  VIX days (<%2.0f) : %d  → will be SKIPPED%n", VIX_LOW,  low);
        System.out.printf("  Normal VIX days       : %d  → will be TRADED%n",            normal);
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private static boolean passesGates(String symbol, AIPredictor.AIPrediction p) {
        if ("NEUTRAL".equals(p.predictedDirection)) return false;
        double minPts = MIN_POINTS.getOrDefault(symbol, 20.0);
        return p.confidence >= MIN_CONFIDENCE && p.estimatedMovePoints >= minPts;
    }

    private static int verifyOutcome(List<SimpleMarketData> data, int idx,
                                      AIPredictor.AIPrediction pred) {
        double entry = data.get(idx).price;
        String dir   = pred.predictedDirection;
        double tgt   = pred.estimatedMovePoints;
        double sl    = pred.suggestedStopLoss;
        double tgtPx = dir.equals("UP") ? entry + tgt  : entry - tgt;
        double slPx  = dir.equals("UP") ? entry - sl   : entry + sl;
        double halfPx= dir.equals("UP") ? entry + tgt*0.5 : entry - tgt*0.5;
        boolean half = false;
        for (int j = 1; j <= LOOKAHEAD && (idx + j) < data.size(); j++) {
            SimpleMarketData c = data.get(idx + j);
            if (dir.equals("UP")) {
                if (c.low  <= slPx)  return half ? 1 : 0;
                if (c.high >= tgtPx) return 2;
                if (c.high >= halfPx) half = true;
            } else {
                if (c.high >= slPx)  return half ? 1 : 0;
                if (c.low  <= tgtPx) return 2;
                if (c.low  <= halfPx) half = true;
            }
        }
        return half ? 1 : 0;
    }

    private static int getSlab(LocalTime t) {
        if (t.isBefore(LocalTime.of(11, 0))) return 0;
        if (t.isBefore(LocalTime.of(13, 0))) return 1;
        return 2;
    }

    private static String fmt(RunResult r) {
        return String.format("calls=%3d | full=%3d | partial=%3d | loss=%3d | WR=%5.1f%% | net=%+7.1f pts",
            r.totalTrades, r.fullWins, r.partialWins, r.losses, r.winRate(), r.netPoints);
    }

    private static void addTo(RunResult dst, RunResult src) {
        dst.totalTrades  += src.totalTrades;
        dst.fullWins     += src.fullWins;
        dst.partialWins  += src.partialWins;
        dst.losses       += src.losses;
        dst.netPoints    += src.netPoints;
        dst.skippedDays  += src.skippedDays;
    }
}
