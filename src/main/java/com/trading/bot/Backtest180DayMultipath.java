package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.ai.NiftyMeanReversionV33;
import com.trading.bot.ai.NiftyStrategyV31;
import com.trading.bot.ai.SensexMeanReversionV33;
import com.trading.bot.ai.SensexStrategyV31;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Backtest180DayMultipath — tests NIFTY and SENSEX with V31 trend-pullback PLUS
 * V33 mean-reversion fallback. For each candle, tries V31 first; if NEUTRAL,
 * tries V33 mean reversion.
 *
 * Goal: ≥1 call/day per symbol while maintaining ≥70% WR.
 *
 * Reports per-symbol (V31 only / V33 only / combined) WR + call counts + daily coverage.
 * Targets 180 days, runs whatever cache allows (currently 80 trading days).
 *
 * Does NOT modify AIPredictor or any integrated code. Pure test harness.
 */
public class Backtest180DayMultipath {

    private static final int    TARGET_DAYS     = 180;
    private static final int    LOAD_DAYS       = 200;
    private static final int    LOOKAHEAD       = 24;
    private static final long   COOLDOWN_MS     = 15L * 60 * 1000;
    private static final long   LOSS_COOLDOWN_MS = 90L * 60 * 1000;
    private static final int    MAX_PER_DIR_PER_DAY = 2;
    private static final double MIN_CONF        = 78.0;
    private static final double MIN_PTS_NIFTY   = 15.0;
    private static final double MIN_PTS_SENSEX  = 45.0;

    private static final LocalTime SCAN_START   = LocalTime.of(9, 15);
    private static final LocalTime SCAN_END     = LocalTime.of(15, 0);

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    static class Rec {
        String date, time, symbol, direction, outcome, source;  // source: V31 or V33MR
        double entry, tgtPts, slPts, pts, confidence;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║   MULTIPATH BACKTEST  —  NIFTY/SENSEX V31 trend + V33 mean-reversion fallback ║");
        System.out.println("║   Goal: >=1 call/day per symbol, >=70% WR. Test only, NOT wired.            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");

        MarketDataAgent md = new MarketDataAgent();
        Map<String, List<SimpleMarketData>> cache = new LinkedHashMap<>();
        for (String sym : new String[]{"NIFTY50", "SENSEX"}) {
            List<SimpleMarketData> data = md.getHistoricalData(sym, LOAD_DAYS);
            int days = countDistinctDates(data);
            System.out.printf("  %-10s : %d candles, %d trading days%n",
                    sym, data == null ? 0 : data.size(), days);
            cache.put(sym, data);
        }

        int minDays = Integer.MAX_VALUE;
        for (List<SimpleMarketData> d : cache.values())
            minDays = Math.min(minDays, countDistinctDates(d));
        int scanDays = Math.min(minDays, TARGET_DAYS);
        System.out.printf("%nActual scan window: %d trading days (target %d)%n%n", scanDays, TARGET_DAYS);

        Map<String, List<Rec>> bySymbol = new LinkedHashMap<>();
        bySymbol.put("NIFTY50", runNifty(cache.get("NIFTY50"), scanDays));
        bySymbol.put("SENSEX",  runSensex(cache.get("SENSEX"),  scanDays));

        // Collect all trading days for zero-call accounting
        Set<LocalDate> allDates = new TreeSet<>();
        for (List<SimpleMarketData> data : cache.values()) {
            List<LocalDate> dd = distinctDatesSorted(data);
            int from = Math.max(0, dd.size() - scanDays);
            for (int i = from; i < dd.size(); i++) allDates.add(dd.get(i));
        }

        printReport(bySymbol, allDates, scanDays);
    }

    private static List<Rec> runNifty(List<SimpleMarketData> data, int scanDays) {
        return runSymbol("NIFTY50", data, scanDays, MIN_PTS_NIFTY, true);
    }

    private static List<Rec> runSensex(List<SimpleMarketData> data, int scanDays) {
        return runSymbol("SENSEX", data, scanDays, MIN_PTS_SENSEX, false);
    }

    private static List<Rec> runSymbol(String symbol, List<SimpleMarketData> data, int scanDays,
                                        double minPts, boolean isNifty) {
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
            double ema20  = ema(hist, 20);
            double ema50  = ema(hist, 50);
            double ema200 = ema(hist, 200);
            double rsi    = rsi(hist, 14);
            double atr    = atr(hist, 14);
            double adx    = adxSimple(hist, 14);

            // ── 1) PRIMARY: V31 trend-pullback
            AIPredictor.AIPrediction primary = isNifty
                    ? NiftyStrategyV31.predict(symbol, hist, price, ema20, ema50, ema200, rsi, adx, atr, c)
                    : SensexStrategyV31.predict(symbol, hist, price, ema20, ema50, ema200, rsi, adx, atr, c);

            String source = "V31";
            AIPredictor.AIPrediction p = primary;

            // ── 2) SECONDARY: V33 mean-reversion if V31 was NEUTRAL
            if ("NEUTRAL".equals(primary.predictedDirection)) {
                int mrFrom = Math.max(0, i - 199);
                List<SimpleMarketData> mrHist = data.subList(mrFrom, i + 1);
                AIPredictor.AIPrediction mr = isNifty
                        ? NiftyMeanReversionV33.predict(symbol, mrHist, price, rsi, atr, c)
                        : SensexMeanReversionV33.predict(symbol, mrHist, price, rsi, atr, c);
                if (!"NEUTRAL".equals(mr.predictedDirection)) {
                    p = mr;
                    source = "V33MR";
                }
            }

            if ("NEUTRAL".equals(p.predictedDirection)) continue;
            if (p.confidence < MIN_CONF) continue;
            if (p.estimatedMovePoints < minPts) continue;
            if ("UP".equals(p.predictedDirection)   && ms - lastUpLoss < LOSS_COOLDOWN_MS) continue;
            if ("DOWN".equals(p.predictedDirection) && ms - lastDnLoss < LOSS_COOLDOWN_MS) continue;
            if ("UP".equals(p.predictedDirection)   && upToday >= MAX_PER_DIR_PER_DAY) continue;
            if ("DOWN".equals(p.predictedDirection) && dnToday >= MAX_PER_DIR_PER_DAY) continue;
            lastSig = ms;
            if ("UP".equals(p.predictedDirection))   upToday++;
            if ("DOWN".equals(p.predictedDirection)) dnToday++;

            double tgtPts = p.estimatedMovePoints;
            double slPts  = p.suggestedStopLoss;

            int outcome = check(data, i, p.predictedDirection, tgtPts, slPts);
            String label; double pts;
            switch (outcome) {
                case FULL_WIN    -> { label = "WIN"; pts = tgtPts; }
                case PARTIAL_WIN -> { label = "PARTIAL WIN"; pts = tgtPts * 0.3; }
                default          -> {
                    label = "LOSS"; pts = -slPts;
                    if ("UP".equals(p.predictedDirection))   lastUpLoss = ms;
                    if ("DOWN".equals(p.predictedDirection)) lastDnLoss = ms;
                }
            }

            Rec r = new Rec();
            r.date = d.toString();
            r.time = c.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
            r.symbol = symbol; r.direction = p.predictedDirection;
            r.entry = price; r.tgtPts = tgtPts; r.slPts = slPts;
            r.outcome = label; r.pts = pts; r.confidence = p.confidence;
            r.source = source;
            out.add(r);
        }
        return out;
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

    private static void printReport(Map<String, List<Rec>> bySymbol, Set<LocalDate> allDates, int scanDays) {
        System.out.println();
        for (var e : bySymbol.entrySet()) {
            String sym = e.getKey();
            List<Rec> recs = e.getValue();
            System.out.println("══════════════════════════════════════════════════════════════════════════════");
            System.out.printf("  %s — Combined V31 + V33MR results%n", sym);
            System.out.println("══════════════════════════════════════════════════════════════════════════════");
            printOne(sym, recs, "ALL", allDates);

            List<Rec> v31  = recs.stream().filter(r -> "V31".equals(r.source)).toList();
            List<Rec> v33  = recs.stream().filter(r -> "V33MR".equals(r.source)).toList();
            printOne(sym + " (V31 only)",  v31, null, null);
            printOne(sym + " (V33MR only)", v33, null, null);
            System.out.println();
        }

        // ── Per-day coverage check
        for (var e : bySymbol.entrySet()) {
            String sym = e.getKey();
            List<Rec> recs = e.getValue();
            Set<LocalDate> daysWith = new TreeSet<>();
            for (Rec r : recs) daysWith.add(LocalDate.parse(r.date));
            Set<LocalDate> zero = new TreeSet<>(allDates);
            zero.removeAll(daysWith);
            System.out.printf("  %s daily coverage: %d/%d days have ≥1 call, %d zero-call days%n",
                    sym, daysWith.size(), allDates.size(), zero.size());
        }
    }

    private static void printOne(String label, List<Rec> recs, String tag, Set<LocalDate> allDates) {
        int n = recs.size();
        int w = 0, p = 0, l = 0; double net = 0;
        for (Rec r : recs) {
            if ("WIN".equals(r.outcome)) w++;
            else if ("PARTIAL WIN".equals(r.outcome)) p++;
            else l++;
            net += r.pts;
        }
        double wr = n > 0 ? (w + p) * 100.0 / n : 0;
        double trueWR = n > 0 ? w * 100.0 / n : 0;
        System.out.printf("  %-25s : %3d calls | WR %5.1f%% (full %.1f%%) | net %+,.0f pts | %d wins, %d partial, %d losses%n",
                label, n, wr, trueWR, net, w, p, l);

        // VERDICT line for ALL
        if ("ALL".equals(tag)) {
            Set<LocalDate> daysWith = new TreeSet<>();
            for (Rec r : recs) daysWith.add(LocalDate.parse(r.date));
            int zero = allDates.size() - daysWith.size();
            double covPct = allDates.isEmpty() ? 0 : daysWith.size() * 100.0 / allDates.size();
            String wrTag  = wr >= 70 ? "✓ ≥70%" : "✗ <70%";
            String covTag = zero <= 3 ? "✓ near 100%" : ("✗ " + zero + " zero-call days");
            System.out.printf("  → %-25s VERDICT: WR %s | Coverage %s%n", "", wrTag, covTag);
        }
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

    // ── Indicator helpers (lean, same as fast runners) ──
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
