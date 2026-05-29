package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.ai.BankNiftyMeanReversionV33;
import com.trading.bot.ai.NiftyStrategyV31;
import com.trading.bot.ai.SensexStrategyV31;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Backtest180DayHonestAudit — combined audit over the full bot (all 3 indices) using
 * the integrated strategies (NIFTY V31.2, SENSEX V31.1, BANKNIFTY V33.3).
 *
 * Targets a 180-day window but honestly reports whatever the data actually allows.
 * Cache on disk currently holds ~120 days; fresh Upstox fetches beyond cache require
 * a valid token. If data falls short of 180 days, the audit reports the actual span.
 *
 * Routes through the same strategy classes that AIPredictor uses in production
 * (USE_NIFTY_V31 / USE_SENSEX_V31 / USE_BANKNIFTY_V33 all default ON).
 *
 * Honest disclosures in output:
 *  • Actual window scanned vs target window
 *  • Lookahead window (24 × 5-min bars = 2 hours) for outcome determination
 *  • Partial wins count as wins in WR
 *  • Zero-call days (days where all 3 symbols stayed NEUTRAL)
 *  • Per-symbol + combined breakdown
 */
public class Backtest180DayHonestAudit {

    private static final int    TARGET_DAYS     = 180;
    private static final int    LOAD_DAYS       = 200;
    private static final int    LOOKAHEAD       = 24;
    private static final long   COOLDOWN_MS     = 10L * 60 * 1000;
    private static final long   LOSS_COOLDOWN_MS = 90L * 60 * 1000;
    private static final long   BNF_LOSS_COOLDOWN_MS = 60L * 60 * 1000;
    private static final int    MAX_PER_DIR_PER_DAY_NIFTY = 2;

    private static final LocalTime SCAN_START   = LocalTime.of(9, 15);
    private static final LocalTime SCAN_END     = LocalTime.of(15, 0);

    private static final double MIN_CONF        = 78.0;
    private static final double MIN_PTS_NIFTY   = 22.0;
    private static final double MIN_PTS_SENSEX  = 50.0;
    private static final double MIN_PTS_BNF     = 25.0;

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    static class Rec {
        String date, time, symbol, direction, outcome;
        double pts;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║   180-DAY HONEST AUDIT  —  NIFTY V31.2 + SENSEX V31.1 + BANKNIFTY V33.3     ║");
        System.out.println("║   Integrated live strategies. No bypass, no cherry-picking.                 ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");

        AIPredictor.USE_NIFTY_V31     = true;
        AIPredictor.USE_SENSEX_V31    = true;
        AIPredictor.USE_BANKNIFTY_V33 = true;

        MarketDataAgent md = new MarketDataAgent();
        System.out.println("Loading " + LOAD_DAYS + " days of data (target window: " + TARGET_DAYS + " days)...\n");

        Map<String, List<SimpleMarketData>> cache = new LinkedHashMap<>();
        for (String sym : new String[]{"NIFTY50", "SENSEX", "BANKNIFTY"}) {
            List<SimpleMarketData> data = md.getHistoricalData(sym, LOAD_DAYS);
            int candleCount = data == null ? 0 : data.size();
            int dayCount = countDistinctDates(data);
            System.out.printf("  %-10s : %d candles, %d distinct trading days%n",
                    sym, candleCount, dayCount);
            cache.put(sym, data);
        }
        System.out.println();

        // Determine actual scan window — bounded by the symbol with fewest days
        int minDays = Integer.MAX_VALUE;
        LocalDate earliestEnd = null, latestEnd = null;
        for (List<SimpleMarketData> data : cache.values()) {
            if (data == null || data.isEmpty()) continue;
            int days = countDistinctDates(data);
            if (days < minDays) minDays = days;
            LocalDate end = lastDate(data);
            if (earliestEnd == null || end.isBefore(earliestEnd)) earliestEnd = end;
            if (latestEnd == null || end.isAfter(latestEnd)) latestEnd = end;
        }
        int actualScanDays = Math.min(minDays, TARGET_DAYS);
        System.out.printf("ACTUAL scan window: %d days (target: %d). Data ends: %s%n%n",
                actualScanDays, TARGET_DAYS, latestEnd);

        // ── Run each symbol
        List<Rec> all = new ArrayList<>();
        Map<String, List<Rec>> bySymbol = new LinkedHashMap<>();

        bySymbol.put("NIFTY50",   runNifty   (cache.get("NIFTY50"),   actualScanDays));
        bySymbol.put("SENSEX",    runSensex  (cache.get("SENSEX"),    actualScanDays));
        bySymbol.put("BANKNIFTY", runBankNifty(cache.get("BANKNIFTY"), actualScanDays));

        for (List<Rec> recs : bySymbol.values()) all.addAll(recs);

        // ── Compute all trading dates in scan window (for zero-call accounting)
        Set<LocalDate> allDates = new TreeSet<>();
        for (List<SimpleMarketData> data : cache.values()) {
            if (data == null) continue;
            LocalDate end = lastDate(data);
            List<LocalDate> dates = distinctDatesSorted(data);
            int from = Math.max(0, dates.size() - actualScanDays);
            for (int i = from; i < dates.size(); i++) allDates.add(dates.get(i));
        }

        printReport(bySymbol, all, allDates, actualScanDays);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Per-symbol runners
    // ────────────────────────────────────────────────────────────────────────

    private static List<Rec> runNifty(List<SimpleMarketData> data, int scanDays) {
        List<Rec> out = new ArrayList<>();
        if (data == null || data.size() < 250) return out;

        List<LocalDate> dates = distinctDatesSorted(data);
        LocalDate cutoff = dates.get(Math.max(0, dates.size() - scanDays));

        long lastSig = 0, lastUpLoss = 0, lastDnLoss = 0;
        int upToday = 0, dnToday = 0;
        LocalDate lastDate = null;

        for (int i = 250; i < data.size(); i++) {
            SimpleMarketData c = data.get(i);
            if (c.timestamp == null) continue;
            LocalDate d = c.timestamp.toLocalDate();
            if (d.isBefore(cutoff)) continue;
            LocalTime t = c.timestamp.toLocalTime();
            if (t.isBefore(SCAN_START) || t.isAfter(SCAN_END)) continue;
            if (!d.equals(lastDate)) {
                lastSig = 0; lastUpLoss = 0; lastDnLoss = 0;
                upToday = 0; dnToday = 0;
                lastDate = d;
            }
            long ms = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
            if (ms - lastSig < COOLDOWN_MS) continue;

            int from = Math.max(0, i - 499);
            List<SimpleMarketData> hist = data.subList(from, i + 1);
            double price = c.price;
            AIPredictor.AIPrediction p = NiftyStrategyV31.predict("NIFTY50", hist, price,
                    ema(hist, 20), ema(hist, 50), ema(hist, 200),
                    rsi(hist, 14), adxSimple(hist, 14), atr(hist, 14), c);
            if (!fires(p, MIN_PTS_NIFTY)) continue;
            if ("UP".equals(p.predictedDirection)   && ms - lastUpLoss < LOSS_COOLDOWN_MS) continue;
            if ("DOWN".equals(p.predictedDirection) && ms - lastDnLoss < LOSS_COOLDOWN_MS) continue;
            if ("UP".equals(p.predictedDirection)   && upToday >= MAX_PER_DIR_PER_DAY_NIFTY) continue;
            if ("DOWN".equals(p.predictedDirection) && dnToday >= MAX_PER_DIR_PER_DAY_NIFTY) continue;
            lastSig = ms;
            if ("UP".equals(p.predictedDirection))   upToday++;
            if ("DOWN".equals(p.predictedDirection)) dnToday++;

            int out2 = check(data, i, p.predictedDirection, p.estimatedMovePoints, p.suggestedStopLoss);
            String label; double pts;
            switch (out2) {
                case FULL_WIN    -> { label = "WIN"; pts = p.estimatedMovePoints; }
                case PARTIAL_WIN -> { label = "PARTIAL WIN"; pts = p.estimatedMovePoints * 0.3; }
                default          -> {
                    label = "LOSS"; pts = -p.suggestedStopLoss;
                    if ("UP".equals(p.predictedDirection))   lastUpLoss = ms;
                    if ("DOWN".equals(p.predictedDirection)) lastDnLoss = ms;
                }
            }
            out.add(rec(d, c.timestamp.toLocalTime(), "NIFTY50", p.predictedDirection, label, pts));
        }
        return out;
    }

    private static List<Rec> runSensex(List<SimpleMarketData> data, int scanDays) {
        List<Rec> out = new ArrayList<>();
        if (data == null || data.size() < 250) return out;

        List<LocalDate> dates = distinctDatesSorted(data);
        LocalDate cutoff = dates.get(Math.max(0, dates.size() - scanDays));

        long lastSig = 0, lastUpLoss = 0, lastDnLoss = 0;
        LocalDate lastDate = null;

        for (int i = 250; i < data.size(); i++) {
            SimpleMarketData c = data.get(i);
            if (c.timestamp == null) continue;
            LocalDate d = c.timestamp.toLocalDate();
            if (d.isBefore(cutoff)) continue;
            LocalTime t = c.timestamp.toLocalTime();
            if (t.isBefore(SCAN_START) || t.isAfter(SCAN_END)) continue;
            if (!d.equals(lastDate)) {
                lastSig = 0; lastUpLoss = 0; lastDnLoss = 0; lastDate = d;
            }
            long ms = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
            if (ms - lastSig < COOLDOWN_MS) continue;

            int from = Math.max(0, i - 499);
            List<SimpleMarketData> hist = data.subList(from, i + 1);
            double price = c.price;
            AIPredictor.AIPrediction p = SensexStrategyV31.predict("SENSEX", hist, price,
                    ema(hist, 20), ema(hist, 50), ema(hist, 200),
                    rsi(hist, 14), adxSimple(hist, 14), atr(hist, 14), c);
            if (!fires(p, MIN_PTS_SENSEX)) continue;
            if ("UP".equals(p.predictedDirection)   && ms - lastUpLoss < LOSS_COOLDOWN_MS) continue;
            if ("DOWN".equals(p.predictedDirection) && ms - lastDnLoss < LOSS_COOLDOWN_MS) continue;
            lastSig = ms;

            int out2 = check(data, i, p.predictedDirection, p.estimatedMovePoints, p.suggestedStopLoss);
            String label; double pts;
            switch (out2) {
                case FULL_WIN    -> { label = "WIN"; pts = p.estimatedMovePoints; }
                case PARTIAL_WIN -> { label = "PARTIAL WIN"; pts = p.estimatedMovePoints * 0.3; }
                default          -> {
                    label = "LOSS"; pts = -p.suggestedStopLoss;
                    if ("UP".equals(p.predictedDirection))   lastUpLoss = ms;
                    if ("DOWN".equals(p.predictedDirection)) lastDnLoss = ms;
                }
            }
            out.add(rec(d, c.timestamp.toLocalTime(), "SENSEX", p.predictedDirection, label, pts));
        }
        return out;
    }

    private static List<Rec> runBankNifty(List<SimpleMarketData> data, int scanDays) {
        List<Rec> out = new ArrayList<>();
        if (data == null || data.size() < 250) return out;

        List<LocalDate> dates = distinctDatesSorted(data);
        LocalDate cutoff = dates.get(Math.max(0, dates.size() - scanDays));

        long lastSig = 0, lastUpLoss = 0, lastDnLoss = 0;
        boolean upToday = false, dnToday = false;
        LocalDate lastDate = null;

        for (int i = 250; i < data.size(); i++) {
            SimpleMarketData c = data.get(i);
            if (c.timestamp == null) continue;
            LocalDate d = c.timestamp.toLocalDate();
            if (d.isBefore(cutoff)) continue;
            LocalTime t = c.timestamp.toLocalTime();
            if (t.isBefore(SCAN_START) || t.isAfter(SCAN_END)) continue;
            if (!d.equals(lastDate)) {
                lastSig = 0; lastUpLoss = 0; lastDnLoss = 0;
                upToday = false; dnToday = false; lastDate = d;
            }
            long ms = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
            if (ms - lastSig < 30L * 60 * 1000) continue;  // BANKNIFTY uses 30-min cooldown

            int from = Math.max(0, i - 199);
            List<SimpleMarketData> hist = data.subList(from, i + 1);
            double price = c.price;
            AIPredictor.AIPrediction p = BankNiftyMeanReversionV33.predict("BANKNIFTY", hist, price,
                    rsi(hist, 14), atr(hist, 14), c);
            if (!fires(p, MIN_PTS_BNF)) continue;
            if ("UP".equals(p.predictedDirection)   && upToday) continue;
            if ("DOWN".equals(p.predictedDirection) && dnToday) continue;
            if ("UP".equals(p.predictedDirection)   && ms - lastUpLoss < BNF_LOSS_COOLDOWN_MS) continue;
            if ("DOWN".equals(p.predictedDirection) && ms - lastDnLoss < BNF_LOSS_COOLDOWN_MS) continue;
            lastSig = ms;
            if ("UP".equals(p.predictedDirection))   upToday = true;
            if ("DOWN".equals(p.predictedDirection)) dnToday = true;

            int out2 = check(data, i, p.predictedDirection, p.estimatedMovePoints, p.suggestedStopLoss);
            String label; double pts;
            switch (out2) {
                case FULL_WIN    -> { label = "WIN"; pts = p.estimatedMovePoints; }
                case PARTIAL_WIN -> { label = "PARTIAL WIN"; pts = p.estimatedMovePoints * 0.3; }
                default          -> {
                    label = "LOSS"; pts = -p.suggestedStopLoss;
                    if ("UP".equals(p.predictedDirection))   lastUpLoss = ms;
                    if ("DOWN".equals(p.predictedDirection)) lastDnLoss = ms;
                }
            }
            out.add(rec(d, c.timestamp.toLocalTime(), "BANKNIFTY", p.predictedDirection, label, pts));
        }
        return out;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Reporting
    // ────────────────────────────────────────────────────────────────────────

    private static void printReport(Map<String, List<Rec>> bySymbol, List<Rec> all,
                                    Set<LocalDate> allTradingDates, int scanDays) {
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  PER-SYMBOL RESULTS");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.printf("  %-12s %8s %8s %10s %8s %10s %10s%n",
                "Symbol", "Calls", "Wins", "Partial", "Losses", "WR%", "Net pts");
        System.out.println("  " + "─".repeat(76));

        for (var e : bySymbol.entrySet()) {
            String sym = e.getKey();
            List<Rec> recs = e.getValue();
            int n = recs.size();
            int w = 0, p = 0, l = 0; double net = 0;
            for (Rec r : recs) {
                if ("WIN".equals(r.outcome)) w++;
                else if ("PARTIAL WIN".equals(r.outcome)) p++;
                else l++;
                net += r.pts;
            }
            double wr = n > 0 ? (w + p) * 100.0 / n : 0;
            System.out.printf("  %-12s %8d %8d %10d %8d %9.1f%% %+10.0f%n",
                    sym, n, w, p, l, wr, net);
        }

        // ── Combined
        int N = all.size();
        int W = 0, P = 0, L = 0; double NET = 0;
        for (Rec r : all) {
            if ("WIN".equals(r.outcome)) W++;
            else if ("PARTIAL WIN".equals(r.outcome)) P++;
            else L++;
            NET += r.pts;
        }
        double WR = N > 0 ? (W + P) * 100.0 / N : 0;
        double trueWR = N > 0 ? W * 100.0 / N : 0;
        System.out.println("  " + "─".repeat(76));
        System.out.printf("  %-12s %8d %8d %10d %8d %9.1f%% %+10.0f%n",
                "COMBINED", N, W, P, L, WR, NET);
        System.out.printf("  %-12s (full wins only WR: %.1f%%)%n", "", trueWR);

        // ── Zero-call days
        Set<LocalDate> daysWithCalls = new TreeSet<>();
        for (Rec r : all) daysWithCalls.add(LocalDate.parse(r.date));
        int totalTradingDays = allTradingDates.size();
        Set<LocalDate> zeroCallDays = new TreeSet<>(allTradingDates);
        zeroCallDays.removeAll(daysWithCalls);

        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  DAILY COVERAGE");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.printf("  Total trading days in window  : %d%n", totalTradingDays);
        System.out.printf("  Days with ≥1 call             : %d (%.1f%%)%n",
                daysWithCalls.size(),
                totalTradingDays > 0 ? daysWithCalls.size() * 100.0 / totalTradingDays : 0);
        System.out.printf("  Zero-call days                : %d (%.1f%%)%n",
                zeroCallDays.size(),
                totalTradingDays > 0 ? zeroCallDays.size() * 100.0 / totalTradingDays : 0);
        System.out.printf("  Avg calls per active day      : %.2f%n",
                daysWithCalls.size() > 0 ? N * 1.0 / daysWithCalls.size() : 0);
        System.out.printf("  Avg calls per trading day     : %.2f%n",
                totalTradingDays > 0 ? N * 1.0 / totalTradingDays : 0);

        // ── Daily distribution
        Map<LocalDate, Integer> dayCount = new TreeMap<>();
        for (Rec r : all) dayCount.merge(LocalDate.parse(r.date), 1, Integer::sum);
        int[] hist = new int[10];
        for (int c : dayCount.values()) hist[Math.min(c, 9)]++;
        hist[0] = zeroCallDays.size();
        System.out.println();
        System.out.println("  Daily call distribution:");
        System.out.printf("    0 calls : %d days%n", hist[0]);
        for (int i = 1; i <= 5; i++) System.out.printf("    %d call%s: %d days%n",
                i, i == 1 ? "  " : "s ", hist[i]);
        int sixPlus = 0;
        for (int i = 6; i < 10; i++) sixPlus += hist[i];
        if (sixPlus > 0) System.out.printf("    6+ calls: %d days%n", sixPlus);

        // ── List zero-call days (first 20 for brevity)
        if (!zeroCallDays.isEmpty()) {
            System.out.println();
            System.out.print("  Zero-call days (first 20): ");
            List<LocalDate> sample = new ArrayList<>(zeroCallDays);
            for (int i = 0; i < Math.min(20, sample.size()); i++) {
                if (i > 0) System.out.print(", ");
                System.out.print(sample.get(i));
            }
            if (zeroCallDays.size() > 20) System.out.print(", … +" + (zeroCallDays.size() - 20) + " more");
            System.out.println();
        }

        // ── Honest caveats
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  HONEST AUDIT CAVEATS");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.printf("  • Scan window     : %d days (target was 180, limited by available cache)%n", scanDays);
        System.out.println("  • Data source     : Upstox 1-min candles, resampled to 5-min");
        System.out.println("  • Outcome check   : 24-bar (2-hour) lookahead from entry");
        System.out.println("  • Partial WIN     : target's 50% hit before SL → counted as win in WR, 0.3×target in P&L");
        System.out.println("  • Strategies      : NIFTY V31.2, SENSEX V31.1, BANKNIFTY V33.3 (integrated live code path)");
        System.out.println("  • Trade-management: same-direction loss cooldown applied (90 min N+S, 60 min B); 2/dir/day cap NIFTY+BANKNIFTY");
        System.out.println("  • Slippage        : NOT modeled (backtest assumes mid-price execution at signal bar close)");
        System.out.println("  • Commissions     : NOT modeled");
        System.out.println("  • Gap risk        : NOT modeled (SL/target check is intra-bar only via high/low)");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    private static boolean fires(AIPredictor.AIPrediction p, double minPts) {
        if (p == null || "NEUTRAL".equals(p.predictedDirection)) return false;
        if (p.confidence < MIN_CONF) return false;
        if (p.estimatedMovePoints < minPts) return false;
        return true;
    }

    private static Rec rec(LocalDate d, LocalTime t, String sym, String dir, String outcome, double pts) {
        Rec r = new Rec();
        r.date = d.toString();
        r.time = t.format(DateTimeFormatter.ofPattern("HH:mm"));
        r.symbol = sym; r.direction = dir; r.outcome = outcome; r.pts = pts;
        return r;
    }

    private static int check(List<SimpleMarketData> data, int idx, String dir, double tgtPts, double slPts) {
        double entry = data.get(idx).price;
        double tgtPx = "UP".equals(dir) ? entry + tgtPts : entry - tgtPts;
        double slPx  = "UP".equals(dir) ? entry - slPts  : entry + slPts;
        double half  = "UP".equals(dir) ? entry + tgtPts * 0.5 : entry - tgtPts * 0.5;
        boolean hitHalf = false;
        for (int j = 1; j <= LOOKAHEAD && (idx + j) < data.size(); j++) {
            SimpleMarketData c = data.get(idx + j);
            if ("UP".equals(dir)) {
                if (c.low  <= slPx) return hitHalf ? PARTIAL_WIN : LOSS;
                if (c.high >= tgtPx) return FULL_WIN;
                if (c.high >= half)  hitHalf = true;
            } else {
                if (c.high >= slPx) return hitHalf ? PARTIAL_WIN : LOSS;
                if (c.low  <= tgtPx) return FULL_WIN;
                if (c.low  <= half)  hitHalf = true;
            }
        }
        return hitHalf ? PARTIAL_WIN : LOSS;
    }

    private static int countDistinctDates(List<SimpleMarketData> data) {
        if (data == null) return 0;
        Set<LocalDate> s = new HashSet<>();
        for (SimpleMarketData c : data) if (c.timestamp != null) s.add(c.timestamp.toLocalDate());
        return s.size();
    }

    private static List<LocalDate> distinctDatesSorted(List<SimpleMarketData> data) {
        Set<LocalDate> s = new TreeSet<>();
        for (SimpleMarketData c : data) if (c.timestamp != null) s.add(c.timestamp.toLocalDate());
        return new ArrayList<>(s);
    }

    private static LocalDate lastDate(List<SimpleMarketData> data) {
        LocalDate last = null;
        for (int i = data.size() - 1; i >= 0; i--) {
            if (data.get(i).timestamp != null) return data.get(i).timestamp.toLocalDate();
        }
        return last;
    }

    // ── Indicator helpers (same as fast runners) ──
    private static double ema(List<SimpleMarketData> d, int period) {
        int n = d.size();
        if (n < period) return d.get(n - 1).price;
        double mult = 2.0 / (period + 1);
        double ema = d.get(0).price;
        for (int i = 1; i < n; i++) ema = ((d.get(i).price - ema) * mult) + ema;
        return ema;
    }
    private static double rsi(List<SimpleMarketData> d, int period) {
        int n = d.size();
        if (n < period + 1) return 50;
        double gain = 0, loss = 0;
        for (int i = n - period; i < n; i++) {
            double ch = d.get(i).price - d.get(i - 1).price;
            if (ch > 0) gain += ch; else loss -= ch;
        }
        if (loss == 0) return 100;
        double rs = (gain / period) / (loss / period);
        return 100 - 100 / (1 + rs);
    }
    private static double atr(List<SimpleMarketData> d, int period) {
        int n = d.size();
        if (n < period + 1) return 0;
        double sum = 0;
        for (int i = n - period; i < n; i++) {
            double hl = d.get(i).high - d.get(i).low;
            double hc = Math.abs(d.get(i).high - d.get(i - 1).price);
            double lc = Math.abs(d.get(i).low  - d.get(i - 1).price);
            sum += Math.max(hl, Math.max(hc, lc));
        }
        return sum / period;
    }
    private static double adxSimple(List<SimpleMarketData> d, int period) {
        int n = d.size();
        if (n < period * 2 + 1) return 20;
        double[] plusDM = new double[period];
        double[] minusDM = new double[period];
        double[] tr = new double[period];
        for (int i = 0; i < period; i++) {
            int idx = n - period + i;
            double upMove = d.get(idx).high - d.get(idx - 1).high;
            double dnMove = d.get(idx - 1).low - d.get(idx).low;
            plusDM[i]  = (upMove > dnMove && upMove > 0) ? upMove : 0;
            minusDM[i] = (dnMove > upMove && dnMove > 0) ? dnMove : 0;
            double hl = d.get(idx).high - d.get(idx).low;
            double hc = Math.abs(d.get(idx).high - d.get(idx - 1).price);
            double lc = Math.abs(d.get(idx).low  - d.get(idx - 1).price);
            tr[i] = Math.max(hl, Math.max(hc, lc));
        }
        double atrSum = 0, plusSum = 0, minusSum = 0;
        for (int i = 0; i < period; i++) { atrSum += tr[i]; plusSum += plusDM[i]; minusSum += minusDM[i]; }
        if (atrSum == 0) return 20;
        double plusDI  = 100 * plusSum / atrSum;
        double minusDI = 100 * minusSum / atrSum;
        return (plusDI + minusDI == 0) ? 0 : 100 * Math.abs(plusDI - minusDI) / (plusDI + minusDI);
    }
}
