package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.ai.BankNiftyStrategyV32;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Backtest120DayBankNiftyV32 — STANDALONE. Tests {@link BankNiftyStrategyV32}.
 * NOT integrated into AIPredictor — user permission required after seeing WR.
 *
 * Adds runner-side 1-call-per-direction-per-day cap on top of strategy filters.
 */
public class Backtest120DayBankNiftyV32 {

    private static final int    BACKTEST_DAYS    = 120;
    private static final int    LOAD_DAYS        = 140;
    private static final int    LOOKAHEAD        = 24;
    private static final long   COOLDOWN_MS      = 15L * 60 * 1000;  // 15 min between any signals
    private static final double MIN_POINTS       = 35.0;
    private static final double MIN_CONFIDENCE   = 80.0;
    private static final LocalTime SCAN_START   = LocalTime.of(9, 15);
    private static final LocalTime SCAN_END     = LocalTime.of(13, 30);

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    static class Rec {
        String date, time, direction, outcome, reason;
        double entry, tgtPts, slPts, pts, confidence;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║   BANKNIFTY V32 STRICT 120-DAY BACKTEST  —  push to 70%+ WR  (test only)    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");

        MarketDataAgent md = new MarketDataAgent();
        List<SimpleMarketData> bnf = md.getHistoricalData("BANKNIFTY", LOAD_DAYS);
        if (bnf == null || bnf.size() < 500) {
            System.err.println("Insufficient BANKNIFTY data");
            return;
        }
        System.out.println("Loaded " + bnf.size() + " candles for BANKNIFTY.\n");

        List<Rec> signals = run(bnf);
        printReport(signals);
    }

    private static List<Rec> run(List<SimpleMarketData> data) {
        List<Rec> out = new ArrayList<>();
        Set<LocalDate> datesSet = new LinkedHashSet<>();
        for (SimpleMarketData d : data) if (d.timestamp != null) datesSet.add(d.timestamp.toLocalDate());
        List<LocalDate> dates = new ArrayList<>(datesSet);
        Collections.sort(dates);
        LocalDate cutoff = dates.size() > BACKTEST_DAYS
                ? dates.get(dates.size() - BACKTEST_DAYS)
                : dates.get(0);

        System.out.printf("Scanning %s → %s (%d trading days)%n%n",
                cutoff, dates.get(dates.size() - 1),
                dates.size() - dates.indexOf(cutoff));

        long lastSigMs = 0, lastUpLossMs = 0, lastDownLossMs = 0;
        final long LOSS_COOLDOWN_MS = 120L * 60 * 1000; // 2 hours
        boolean upTakenToday = false, downTakenToday = false;
        LocalDate lastDate = null;

        for (int i = 250; i < data.size(); i++) {
            SimpleMarketData c = data.get(i);
            if (c.timestamp == null) continue;
            LocalDate cDate = c.timestamp.toLocalDate();
            if (cDate.isBefore(cutoff)) continue;
            LocalTime cTime = c.timestamp.toLocalTime();
            if (cTime.isBefore(SCAN_START) || cTime.isAfter(SCAN_END)) continue;

            if (!cDate.equals(lastDate)) {
                lastSigMs = 0; lastUpLossMs = 0; lastDownLossMs = 0;
                upTakenToday = false; downTakenToday = false;
                lastDate = cDate;
            }
            long cMs = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
            if (cMs - lastSigMs < COOLDOWN_MS) continue;

            int from = Math.max(0, i - 499);
            List<SimpleMarketData> hist = data.subList(from, i + 1);

            double price = c.price;
            double ema20  = ema(hist, 20);
            double ema50  = ema(hist, 50);
            double ema200 = ema(hist, 200);
            double rsi    = rsi(hist, 14);
            double atr    = atr(hist, 14);
            double adx    = adxSimple(hist, 14);

            AIPredictor.AIPrediction p = BankNiftyStrategyV32.predict(
                    "BANKNIFTY", hist, price, ema20, ema50, ema200, rsi, adx, atr, c);

            if ("NEUTRAL".equals(p.predictedDirection)) continue;
            if (p.confidence < MIN_CONFIDENCE) continue;
            if (p.estimatedMovePoints < MIN_POINTS) continue;

            // ── 1 call per direction per day (V32 strict)
            if ("UP".equals(p.predictedDirection)   && upTakenToday)   continue;
            if ("DOWN".equals(p.predictedDirection) && downTakenToday) continue;

            // ── Loss cooldown
            if ("UP".equals(p.predictedDirection)   && cMs - lastUpLossMs   < LOSS_COOLDOWN_MS) continue;
            if ("DOWN".equals(p.predictedDirection) && cMs - lastDownLossMs < LOSS_COOLDOWN_MS) continue;

            lastSigMs = cMs;
            if ("UP".equals(p.predictedDirection))   upTakenToday = true;
            if ("DOWN".equals(p.predictedDirection)) downTakenToday = true;

            double tgtPts = p.estimatedMovePoints;
            double slPts  = p.suggestedStopLoss;

            int outcome = checkOutcome(data, i, p.predictedDirection, tgtPts, slPts);
            String label; double pts;
            switch (outcome) {
                case FULL_WIN    -> { label = "WIN";         pts =  tgtPts; }
                case PARTIAL_WIN -> { label = "PARTIAL WIN"; pts =  tgtPts * 0.3; }
                default          -> {
                    label = "LOSS"; pts = -slPts;
                    if ("UP".equals(p.predictedDirection))   lastUpLossMs   = cMs;
                    if ("DOWN".equals(p.predictedDirection)) lastDownLossMs = cMs;
                }
            }

            Rec r = new Rec();
            r.date = cDate.toString();
            r.time = c.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
            r.direction = p.predictedDirection;
            r.entry = price; r.tgtPts = tgtPts; r.slPts = slPts;
            r.outcome = label; r.pts = pts; r.confidence = p.confidence;
            r.reason = p.predictionReasoning;
            out.add(r);
        }
        return out;
    }

    private static int checkOutcome(List<SimpleMarketData> data, int idx, String dir, double tgtPts, double slPts) {
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

    private static void printReport(List<Rec> sigs) {
        System.out.println();
        System.out.println("══════════════════ BANKNIFTY V32 RESULTS ══════════════════");
        if (sigs.isEmpty()) {
            System.out.println("  No signals generated. V32 may be too strict.");
            return;
        }
        int tot = sigs.size(), wins = 0, partial = 0, losses = 0;
        double net = 0;
        for (Rec r : sigs) {
            if ("WIN".equals(r.outcome)) wins++;
            else if ("PARTIAL WIN".equals(r.outcome)) partial++;
            else losses++;
            net += r.pts;
        }
        double wr = (wins + partial) * 100.0 / tot;
        double trueWR = wins * 100.0 / tot;
        System.out.printf("  Total Calls   : %d%n", tot);
        System.out.printf("  Full Wins     : %d (%.1f%% true WR)%n", wins, trueWR);
        System.out.printf("  Partial Wins  : %d%n", partial);
        System.out.printf("  Losses        : %d%n", losses);
        System.out.printf("  Win Rate (W+P): %.1f%%%n", wr);
        System.out.printf("  Net Points    : %+,.0f%n", net);

        Map<String, List<Rec>> byDate = new TreeMap<>();
        for (Rec r : sigs) byDate.computeIfAbsent(r.date, k -> new ArrayList<>()).add(r);
        System.out.printf("  Trading days with signals: %d  |  Avg calls/active day: %.2f%n",
                byDate.size(), tot * 1.0 / Math.max(byDate.size(), 1));

        // ── Verdict
        System.out.println();
        if (wr >= 70) System.out.printf("  VERDICT: %.1f%% WR — ✓ HIT TARGET — ask user permission to integrate%n", wr);
        else if (wr >= 65) System.out.printf("  VERDICT: %.1f%% WR — ⚠️ CLOSE but below 70%% — user decides%n", wr);
        else              System.out.printf("  VERDICT: %.1f%% WR — ✗ Below 70%% — DO NOT INTEGRATE per user policy%n", wr);

        System.out.println();
        System.out.println("  Per-call detail:");
        System.out.printf("  %-12s %-6s %-5s %10s %6s %6s %5s %-12s %8s%n",
                "Date", "Time", "Dir", "Entry", "+Tgt", "-SL", "Conf", "Result", "P&L");
        System.out.println("  " + "─".repeat(85));
        for (Rec r : sigs) {
            System.out.printf("  %-12s %-6s %-5s %,10.0f %6.0f %6.0f %4.0f%% %-12s %+8.0f%n",
                    r.date, r.time, r.direction, r.entry, r.tgtPts, r.slPts, r.confidence,
                    r.outcome, r.pts);
        }
    }
}
