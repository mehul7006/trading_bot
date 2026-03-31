package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.agents.PredictionAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * BacktestCombined120Days — unified 120-day backtest
 *
 * Runs TWO independent strategies back-to-back:
 *
 *  [A] REGULAR strategy  (V29.0)
 *      • Symbols  : NIFTY50, BANKNIFTY, SENSEX
 *      • Window   : all day 09:15–15:25 (AIPredictor gate: conf ≥ 85%, min pts)
 *      • Cooldown : 10 min (BANKNIFTY prime slab: 12 min)
 *
 *  [B] EXPIRY SESSION strategy  (RSI Exhaustion Reversal V2)
 *      • Symbols  : NIFTY50 (Tuesday), SENSEX (Thursday)  — BANKNIFTY excluded
 *      • Window   : 14:00–15:10 IST on expiry day
 *      • Signal   : RSI ≥ 68 → SHORT | RSI ≤ 32 → LONG
 *                   + BB 1.8σ touch + bearish/bullish candle + volume ≥ 1.4×
 *      • Target   : 2.0 × ATR14 | SL : 0.5 × ATR14  (4:1 R:R)
 *      • Cooldown : 20 min per symbol (independent of regular cooldown)
 *
 * Shows per-strategy stats and a unified combined P&L summary.
 */
public class BacktestCombined120Days {

    // ── Regular strategy constants ──────────────────────────────────────────
    private static final double  REG_MIN_CONFIDENCE = 85.0;
    private static final Map<String, Double> REG_MIN_POINTS = Map.of(
        "NIFTY50",   25.0,
        "SENSEX",    60.0,
        "BANKNIFTY", 70.0
    );
    private static final long REG_COOLDOWN_MS     = 10L * 60 * 1000;
    private static final long BNF_PRIME_COOLDOWN  = 12L * 60 * 1000;
    private static final int  LOOKAHEAD           = 24;
    private static final int  BACKTEST_DAYS       = 120;
    private static final int  LOAD_DAYS           = 140; // warmup + 120-day window

    // ── Expiry strategy constants ──────────────────────────────────────────
    private static final Map<String, DayOfWeek> EXPIRY_DOW = Map.of(
        "NIFTY50", DayOfWeek.TUESDAY,
        "SENSEX",  DayOfWeek.THURSDAY
    );
    private static final LocalTime EXP_START      = LocalTime.of(14, 0);
    private static final LocalTime EXP_END        = LocalTime.of(15, 10);
    private static final double    RSI_OVERBOUGHT = 68.0;
    private static final double    RSI_OVERSOLD   = 32.0;
    private static final double    BB_STD         = 1.8;
    private static final double    VOL_MULT       = 1.4;
    private static final double    BODY_RATIO     = 0.55;
    private static final double    ATR_TGT        = 2.0;
    private static final double    ATR_SL         = 0.5;
    private static final long      EXP_COOLDOWN   = 20L * 60 * 1000;

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    // ── Shared signal record ────────────────────────────────────────────────
    static class Rec {
        String date, time, symbol, direction, outcome, source; // source = "REGULAR" | "EXPIRY"
        double entry, tgtPts, slPts, tgtPx, slPx, pts, confidence;
        String reason;
    }

    // ────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        System.out.println("Loading 140 days of market data for 120-day combined backtest...");
        MarketDataAgent  md   = new MarketDataAgent();
        PredictionAgent  pred = new PredictionAgent();

        List<Rec> regular = new ArrayList<>();
        List<Rec> expiry  = new ArrayList<>();

        String[] regSymbols = {"NIFTY50", "BANKNIFTY", "SENSEX"};
        String[] expSymbols = {"NIFTY50", "SENSEX"};

        // Cache market data (avoid double-fetching for NIFTY50/SENSEX)
        Map<String, List<SimpleMarketData>> cache = new HashMap<>();
        for (String sym : regSymbols) {
            System.out.printf("  Fetching %s...%n", sym);
            cache.put(sym, md.getHistoricalData(sym, LOAD_DAYS));
        }

        // [A] Regular strategy
        System.out.println("\nRunning [A] Regular V29.0 strategy...");
        for (String sym : regSymbols) {
            regular.addAll(runRegular(sym, cache.get(sym), pred));
        }

        // [B] Expiry strategy
        System.out.println("Running [B] Expiry RSI Exhaustion Reversal...");
        for (String sym : expSymbols) {
            expiry.addAll(runExpiry(sym, cache.get(sym)));
        }

        // Sort all by date+time
        Comparator<Rec> byDateTime = Comparator.comparing((Rec r) -> r.date).thenComparing(r -> r.time);
        regular.sort(byDateTime);
        expiry.sort(byDateTime);

        printReport(regular, expiry);
    }

    // ────────────────────────────────────────────────────────────────────────
    // [A] REGULAR STRATEGY
    // ────────────────────────────────────────────────────────────────────────
    private static List<Rec> runRegular(String symbol,
            List<SimpleMarketData> data, PredictionAgent predAgent) {

        List<Rec> signals = new ArrayList<>();
        if (data == null || data.size() < 200) {
            System.err.println("  Not enough data for " + symbol);
            return signals;
        }

        // Collect unique trading dates
        Set<LocalDate> datesSet = new LinkedHashSet<>();
        for (SimpleMarketData d : data) {
            if (d.timestamp != null) datesSet.add(d.timestamp.toLocalDate());
        }
        List<LocalDate> dates = new ArrayList<>(datesSet);
        Collections.sort(dates);

        LocalDate cutoff = dates.size() > BACKTEST_DAYS
            ? dates.get(dates.size() - BACKTEST_DAYS)
            : dates.get(0);

        System.out.printf("  [Regular] %s: scanning %s → %s%n",
            symbol, cutoff, dates.get(dates.size() - 1));

        long   lastMs   = 0;
        LocalDate lastD = null;

        for (int i = 200; i < data.size(); i++) {
            SimpleMarketData c = data.get(i);
            if (c.timestamp == null) continue;

            LocalDate cDate = c.timestamp.toLocalDate();
            if (cDate.isBefore(cutoff)) continue;

            LocalTime cTime = c.timestamp.toLocalTime();
            // Scan 09:15–13:30 only (prime signal window; saves ~40% AIPredictor compute vs full-day scan)
            if (cTime.isBefore(LocalTime.of(9, 15)) || cTime.isAfter(LocalTime.of(13, 30))) continue;

            // Day reset
            if (!cDate.equals(lastD)) { lastMs = 0; lastD = cDate; }

            int  slab     = slab(cTime);
            long cooldown = ("BANKNIFTY".equals(symbol) && slab == 1) ? BNF_PRIME_COOLDOWN : REG_COOLDOWN_MS;

            long cMs = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
            if (cMs - lastMs < cooldown) continue;

            List<SimpleMarketData> hist = data.subList(0, i + 1);
            AIPredictor.AIPrediction p  = predAgent.generateSignal(symbol, hist);
            if (!regGate(symbol, p)) continue;

            lastMs = cMs;

            double entry  = c.price;
            double tgtPts = p.estimatedMovePoints;
            double slPts  = p.suggestedStopLoss;
            double tgtPx  = p.predictedDirection.equals("UP") ? entry + tgtPts : entry - tgtPts;
            double slPx   = p.predictedDirection.equals("UP") ? entry - slPts  : entry + slPts;

            int outcome = checkOutcome(data, i, p.predictedDirection, tgtPts, slPts);
            String label; double pts;
            switch (outcome) {
                case FULL_WIN    -> { label = "WIN";         pts =  tgtPts; }
                case PARTIAL_WIN -> { label = "PARTIAL WIN"; pts =  tgtPts * 0.3; }
                default          -> { label = "LOSS";        pts = -slPts; }
            }

            Rec r = new Rec();
            r.date   = cDate.toString();
            r.time   = c.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
            r.symbol = symbol; r.direction = p.predictedDirection;
            r.entry  = entry; r.tgtPts = tgtPts; r.slPts = slPts;
            r.tgtPx  = tgtPx; r.slPx   = slPx;
            r.outcome= label; r.pts    = pts;
            r.confidence = p.confidence;
            r.source = "REGULAR";
            r.reason = p.predictionReasoning != null ? p.predictionReasoning : "-";
            signals.add(r);
        }
        return signals;
    }

    private static boolean regGate(String symbol, AIPredictor.AIPrediction p) {
        if ("NEUTRAL".equals(p.predictedDirection)) return false;
        double min = REG_MIN_POINTS.getOrDefault(symbol, 20.0);
        return p.confidence >= REG_MIN_CONFIDENCE && p.estimatedMovePoints >= min;
    }

    // ────────────────────────────────────────────────────────────────────────
    // [B] EXPIRY STRATEGY — RSI Exhaustion Reversal
    // ────────────────────────────────────────────────────────────────────────
    private static List<Rec> runExpiry(String symbol, List<SimpleMarketData> data) {

        List<Rec> signals = new ArrayList<>();
        if (data == null || data.size() < 25) {
            System.err.println("  Not enough data for expiry scan: " + symbol);
            return signals;
        }

        DayOfWeek expiryDow = EXPIRY_DOW.get(symbol);

        Set<LocalDate> datesSet = new LinkedHashSet<>();
        for (SimpleMarketData d : data) {
            if (d.timestamp != null) datesSet.add(d.timestamp.toLocalDate());
        }
        List<LocalDate> dates = new ArrayList<>(datesSet);
        Collections.sort(dates);

        LocalDate cutoff = dates.size() > BACKTEST_DAYS
            ? dates.get(dates.size() - BACKTEST_DAYS)
            : dates.get(0);

        System.out.printf("  [Expiry]  %s (%s): scanning %s → %s%n",
            symbol, expiryDow, cutoff, dates.get(dates.size() - 1));

        Map<String, Long> lastSigMs = new HashMap<>(); // date-string → last signal ms

        for (int i = 25; i < data.size(); i++) {
            SimpleMarketData c = data.get(i);
            if (c.timestamp == null) continue;

            LocalDate cDate = c.timestamp.toLocalDate();
            if (cDate.isBefore(cutoff)) continue;
            if (cDate.getDayOfWeek() != expiryDow) continue;

            LocalTime cTime = c.timestamp.toLocalTime();
            if (cTime.isBefore(EXP_START) || !cTime.isBefore(EXP_END)) continue;

            long cMs = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
            String dk = cDate.toString();
            long lastMs = lastSigMs.getOrDefault(dk, 0L);
            if (cMs - lastMs < EXP_COOLDOWN) continue;

            // Need at least 20 candles of history for indicators
            if (i < 20) continue;
            List<SimpleMarketData> hist = data.subList(0, i + 1);

            double rsi   = calcRSI(hist, 14);
            double atr   = calcATR(hist, 14);
            double[] bb  = calcBB(hist, 20, BB_STD);
            double volMA = calcVolMA(hist, 20);

            if (atr <= 0) continue;

            double price = c.price, open = c.open, high = c.high, low = c.low;
            double range = high - low;
            double body  = Math.abs(price - open);
            double br    = range > 0 ? body / range : 0;
            double vol   = c.volume > 0 ? c.volume : 1;
            double bbU   = bb[0], bbL = bb[1];

            String dir = null, reason = null;

            // LONG: oversold + price at lower BB + bullish reversal candle + vol surge
            if (rsi <= RSI_OVERSOLD && price <= bbL * 1.002 && price > open
                    && br >= BODY_RATIO && vol >= volMA * VOL_MULT) {
                dir    = "UP";
                reason = String.format("Expiry RSI=%.1f (OVERSOLD) | BB lower touch | Bull candle %.0f%% body | Vol %.1fx",
                    rsi, br * 100, vol / volMA);
            }
            // SHORT: overbought + price at upper BB + bearish reversal candle + vol surge
            else if (rsi >= RSI_OVERBOUGHT && price >= bbU * 0.998 && price < open
                    && br >= BODY_RATIO && vol >= volMA * VOL_MULT) {
                dir    = "DOWN";
                reason = String.format("Expiry RSI=%.1f (OVERBOUGHT) | BB upper touch | Bear candle %.0f%% body | Vol %.1fx",
                    rsi, br * 100, vol / volMA);
            }

            if (dir == null) continue;

            double tgtPts = atr * ATR_TGT;
            double slPts  = atr * ATR_SL;
            double minPts = "NIFTY50".equals(symbol) ? 15.0 : 30.0;
            if (tgtPts < minPts) continue;

            double tgtPx = "UP".equals(dir) ? price + tgtPts : price - tgtPts;
            double slPx  = "UP".equals(dir) ? price - slPts  : price + slPts;

            lastSigMs.put(dk, cMs);

            int outcome = checkOutcome(data, i, dir, tgtPts, slPts);
            String label; double pts;
            switch (outcome) {
                case FULL_WIN    -> { label = "WIN";         pts =  tgtPts; }
                case PARTIAL_WIN -> { label = "PARTIAL WIN"; pts =  tgtPts * 0.3; }
                default          -> { label = "LOSS";        pts = -slPts; }
            }

            Rec r = new Rec();
            r.date   = cDate.toString();
            r.time   = c.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
            r.symbol = symbol; r.direction = dir;
            r.entry  = price; r.tgtPts = tgtPts; r.slPts = slPts;
            r.tgtPx  = tgtPx; r.slPx = slPx;
            r.outcome= label; r.pts   = pts;
            r.confidence = 0; // no AIPredictor — RSI-based
            r.source = "EXPIRY";
            r.reason = reason;
            signals.add(r);
        }
        return signals;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Shared outcome checker
    // ────────────────────────────────────────────────────────────────────────
    private static int checkOutcome(List<SimpleMarketData> data, int idx,
            String dir, double tgtPts, double slPts) {
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

    private static int slab(LocalTime t) {
        if (t.isBefore(LocalTime.of(11, 0))) return 0;
        if (t.isBefore(LocalTime.of(13, 0))) return 1;
        return 2;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Expiry indicator helpers (use .price for close, same as SimpleMarketData)
    // ────────────────────────────────────────────────────────────────────────
    private static double calcRSI(List<SimpleMarketData> h, int period) {
        int n = h.size();
        if (n < period + 1) return 50;
        double gain = 0, loss = 0;
        for (int i = n - period; i < n; i++) {
            double d = h.get(i).price - h.get(i - 1).price;
            if (d > 0) gain += d; else loss -= d;
        }
        gain /= period; loss /= period;
        if (loss == 0) return 100;
        double rs = gain / loss;
        return 100 - 100 / (1 + rs);
    }

    private static double calcATR(List<SimpleMarketData> h, int period) {
        int n = h.size();
        if (n < period + 1) return 0;
        double sum = 0;
        for (int i = n - period; i < n; i++) {
            double hl = h.get(i).high - h.get(i).low;
            double hc = Math.abs(h.get(i).high - h.get(i - 1).price);
            double lc = Math.abs(h.get(i).low  - h.get(i - 1).price);
            sum += Math.max(hl, Math.max(hc, lc));
        }
        return sum / period;
    }

    private static double[] calcBB(List<SimpleMarketData> h, int period, double mult) {
        int n = h.size();
        if (n < period) return new double[]{Double.MAX_VALUE, 0};
        double sum = 0;
        for (int i = n - period; i < n; i++) sum += h.get(i).price;
        double mean = sum / period;
        double var  = 0;
        for (int i = n - period; i < n; i++) var += Math.pow(h.get(i).price - mean, 2);
        double sd = Math.sqrt(var / period);
        return new double[]{mean + mult * sd, mean - mult * sd};
    }

    private static double calcVolMA(List<SimpleMarketData> h, int period) {
        int n = h.size();
        if (n < period) return 0;
        double sum = 0;
        for (int i = n - period; i < n; i++) sum += h.get(i).volume;
        return sum / period;
    }

    // ────────────────────────────────────────────────────────────────────────
    // REPORT
    // ────────────────────────────────────────────────────────────────────────
    private static void printReport(List<Rec> regular, List<Rec> expiry) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║       COMBINED 120-DAY BACKTEST  —  V29.0 Regular + Expiry Reversal         ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");

        // ── Section A: Regular ──
        printStrategySection("A — REGULAR V29.0 Strategy  (NIFTY50 + BANKNIFTY + SENSEX, all day)",
            regular, new String[]{"NIFTY50", "BANKNIFTY", "SENSEX"});

        // ── Section B: Expiry ──
        printStrategySection("B — EXPIRY RSI Exhaustion Reversal  (NIFTY50 Tue + SENSEX Thu, 14:00–15:10)",
            expiry, new String[]{"NIFTY50", "SENSEX"});

        // ── Combined totals ──
        List<Rec> all = new ArrayList<>();
        all.addAll(regular);
        all.addAll(expiry);

        int tot = all.size();
        int wins = 0, partial = 0, losses = 0;
        double net = 0;
        for (Rec r : all) {
            if ("WIN".equals(r.outcome))          wins++;
            else if ("PARTIAL WIN".equals(r.outcome)) partial++;
            else                                  losses++;
            net += r.pts;
        }
        double wr = tot > 0 ? (wins + partial) * 100.0 / tot : 0;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         COMBINED GRAND TOTAL (A + B)                        ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Total Calls   : %-5d                                                       ║%n", tot);
        System.out.printf( "║  Wins          : %-5d                                                       ║%n", wins);
        System.out.printf( "║  Partial Wins  : %-5d                                                       ║%n", partial);
        System.out.printf( "║  Losses        : %-5d                                                       ║%n", losses);
        System.out.printf( "║  Win Rate      : %5.1f%%                                                      ║%n", wr);
        System.out.printf( "║  Net Points    : %+,.0f pts                                                    ║%n", net);
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");

        // ── Per-day combined summary ──
        System.out.println();
        System.out.println("  PER-DAY COMBINED SUMMARY (Regular + Expiry):");
        System.out.printf("  %-12s  %5s  %4s  %4s  %4s  %5s  %10s%n",
            "Date", "Calls", "WIN", "PW", "LOSS", "WR%", "Net Pts");
        System.out.println("  " + "─".repeat(65));

        Map<String, List<Rec>> byDate = new TreeMap<>();
        for (Rec r : all) byDate.computeIfAbsent(r.date, k -> new ArrayList<>()).add(r);

        for (Map.Entry<String, List<Rec>> e : byDate.entrySet()) {
            List<Rec> day = e.getValue();
            int dW=0, dP=0, dL=0; double dN=0;
            for (Rec r : day) {
                if ("WIN".equals(r.outcome)) dW++;
                else if ("PARTIAL WIN".equals(r.outcome)) dP++;
                else dL++;
                dN += r.pts;
            }
            double dWR = day.size() > 0 ? (dW + dP) * 100.0 / day.size() : 0;
            // mark expiry days
            boolean hasExpiry = day.stream().anyMatch(r -> "EXPIRY".equals(r.source));
            String tag = hasExpiry ? " [EXP]" : "";
            System.out.printf("  %-12s  %5d  %4d  %4d  %4d  %4.0f%%  %+10.0f%s%n",
                e.getKey(), day.size(), dW, dP, dL, dWR, dN, tag);
        }

        System.out.println();
        System.out.println("  [EXP] = Expiry day — both Regular + Expiry signals may appear");
        System.out.println();
    }

    private static void printStrategySection(String title, List<Rec> sigs, String[] symbols) {
        System.out.println();
        System.out.println("  ┌─────────────────────────────────────────────────────────────────────────┐");
        System.out.printf( "  │  %s%n", title);
        System.out.println("  └─────────────────────────────────────────────────────────────────────────┘");

        if (sigs.isEmpty()) {
            System.out.println("  No signals generated.");
            return;
        }

        int tot = sigs.size(), wins = 0, partial = 0, losses = 0;
        double net = 0;
        for (Rec r : sigs) {
            if ("WIN".equals(r.outcome))          wins++;
            else if ("PARTIAL WIN".equals(r.outcome)) partial++;
            else                                  losses++;
            net += r.pts;
        }
        double wr = tot > 0 ? (wins + partial) * 100.0 / tot : 0;

        System.out.printf("  Total Calls: %d  |  Wins: %d  |  Partial: %d  |  Losses: %d  |  WR: %.1f%%  |  Net: %+,.0f pts%n",
            tot, wins, partial, losses, wr, net);

        // Per-symbol breakdown
        System.out.printf("  %-12s  %5s  %4s  %4s  %4s  %5s  %10s%n",
            "Symbol", "Calls", "WIN", "PW", "LOSS", "WR%", "Net Pts");
        System.out.println("  " + "─".repeat(60));
        for (String sym : symbols) {
            int sT=0, sW=0, sP=0, sL=0; double sN=0;
            for (Rec r : sigs) {
                if (!sym.equals(r.symbol)) continue;
                sT++;
                if ("WIN".equals(r.outcome)) sW++;
                else if ("PARTIAL WIN".equals(r.outcome)) sP++;
                else sL++;
                sN += r.pts;
            }
            if (sT == 0) continue;
            double sWR = (sW + sP) * 100.0 / sT;
            System.out.printf("  %-12s  %5d  %4d  %4d  %4d  %4.0f%%  %+10.0f%n",
                sym, sT, sW, sP, sL, sWR, sN);
        }

        // Detailed call list
        System.out.println();
        System.out.printf("  %-12s  %-6s  %-10s  %-5s  %10s  %6s  %5s  %-12s  %s%n",
            "Date", "Time", "Symbol", "Dir", "Entry", "+Tgt", "-SL", "Result", "P&L");
        System.out.println("  " + "─".repeat(90));
        for (Rec r : sigs) {
            String arrow = "UP".equals(r.direction) ? "UP " : "DWN";
            String icon  = "WIN".equals(r.outcome) ? "WIN" : "PARTIAL WIN".equals(r.outcome) ? "P/WIN" : "LOSS";
            System.out.printf("  %-12s  %-6s  %-10s  %-5s  %,10.0f  %6.0f  %5.0f  %-12s  %+.0f%n",
                r.date, r.time, r.symbol, arrow, r.entry, r.tgtPts, r.slPts, icon, r.pts);
        }
    }
}
