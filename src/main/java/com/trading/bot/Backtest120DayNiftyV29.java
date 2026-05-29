package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.agents.PredictionAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Backtest120DayNiftyV29 — NIFTY-only 120-day backtest using the unchanged V29 logic
 * via PredictionAgent → AIPredictor (full pipeline including enhanced filters and
 * institutional boosts). User explicitly froze NIFTY logic, so this measures the
 * current live behavior over 120 days for the final combined report.
 *
 * Slower than the V31 fast runners (~3-5 min) because it runs the full AIPredictor
 * pipeline per candle, but only for one symbol.
 */
public class Backtest120DayNiftyV29 {

    private static final int    BACKTEST_DAYS    = 120;
    private static final int    LOAD_DAYS        = 140;
    private static final int    LOOKAHEAD        = 24;
    private static final long   COOLDOWN_MS      = 10L * 60 * 1000;
    private static final double MIN_POINTS       = 25.0;
    private static final double MIN_CONFIDENCE   = 85.0;
    private static final LocalTime SCAN_START   = LocalTime.of(9, 15);
    private static final LocalTime SCAN_END     = LocalTime.of(13, 30);

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    static class Rec {
        String date, time, direction, outcome;
        double entry, tgtPts, slPts, pts, confidence;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║   NIFTY V29 STANDALONE 120-DAY BACKTEST  (unchanged live logic)             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");

        // Force V31 OFF for SENSEX — does not matter for NIFTY symbol, but defensive
        AIPredictor.USE_SENSEX_V31 = false;

        MarketDataAgent md = new MarketDataAgent();
        PredictionAgent predAgent = new PredictionAgent();

        List<SimpleMarketData> nifty = md.getHistoricalData("NIFTY50", LOAD_DAYS);
        if (nifty == null || nifty.size() < 500) {
            System.err.println("Insufficient NIFTY data: " + (nifty == null ? 0 : nifty.size()));
            return;
        }
        System.out.println("Loaded " + nifty.size() + " candles for NIFTY50.\n");

        List<Rec> out = new ArrayList<>();
        Set<LocalDate> datesSet = new LinkedHashSet<>();
        for (SimpleMarketData d : nifty) if (d.timestamp != null) datesSet.add(d.timestamp.toLocalDate());
        List<LocalDate> dates = new ArrayList<>(datesSet);
        Collections.sort(dates);
        LocalDate cutoff = dates.size() > BACKTEST_DAYS
                ? dates.get(dates.size() - BACKTEST_DAYS)
                : dates.get(0);

        System.out.printf("Scanning %s → %s%n%n", cutoff, dates.get(dates.size() - 1));

        long lastSigMs = 0;
        LocalDate lastDate = null;
        int processed = 0, lastProgress = 0;

        for (int i = 200; i < nifty.size(); i++) {
            SimpleMarketData c = nifty.get(i);
            if (c.timestamp == null) continue;
            LocalDate cDate = c.timestamp.toLocalDate();
            if (cDate.isBefore(cutoff)) continue;
            LocalTime cTime = c.timestamp.toLocalTime();
            if (cTime.isBefore(SCAN_START) || cTime.isAfter(SCAN_END)) continue;

            if (!cDate.equals(lastDate)) { lastSigMs = 0; lastDate = cDate; }
            long cMs = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
            if (cMs - lastSigMs < COOLDOWN_MS) continue;

            processed++;
            if (processed - lastProgress >= 500) {
                System.out.printf("  progress: %d candles scanned, %d signals so far, current date %s%n",
                        processed, out.size(), cDate);
                lastProgress = processed;
            }

            List<SimpleMarketData> hist = nifty.subList(0, i + 1);
            AIPredictor.AIPrediction p = predAgent.generateSignal("NIFTY50", hist);

            if ("NEUTRAL".equals(p.predictedDirection)) continue;
            if (p.confidence < MIN_CONFIDENCE) continue;
            if (p.estimatedMovePoints < MIN_POINTS) continue;

            lastSigMs = cMs;
            double tgtPts = p.estimatedMovePoints;
            double slPts  = p.suggestedStopLoss;

            int outcome = checkOutcome(nifty, i, p.predictedDirection, tgtPts, slPts);
            String label; double pts;
            switch (outcome) {
                case FULL_WIN    -> { label = "WIN";         pts =  tgtPts; }
                case PARTIAL_WIN -> { label = "PARTIAL WIN"; pts =  tgtPts * 0.3; }
                default          -> { label = "LOSS";        pts = -slPts; }
            }

            Rec r = new Rec();
            r.date = cDate.toString();
            r.time = c.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
            r.direction = p.predictedDirection;
            r.entry = c.price; r.tgtPts = tgtPts; r.slPts = slPts;
            r.outcome = label; r.pts = pts; r.confidence = p.confidence;
            out.add(r);
        }
        printReport(out);
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

    private static void printReport(List<Rec> sigs) {
        System.out.println();
        System.out.println("══════════════════ NIFTY V29 RESULTS ══════════════════");
        if (sigs.isEmpty()) {
            System.out.println("  No signals generated.");
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
        System.out.printf("  Total Calls   : %d%n", tot);
        System.out.printf("  Full Wins     : %d%n", wins);
        System.out.printf("  Partial Wins  : %d%n", partial);
        System.out.printf("  Losses        : %d%n", losses);
        System.out.printf("  Win Rate (W+P): %.1f%%%n", wr);
        System.out.printf("  Net Points    : %+,.0f%n", net);

        Map<String, List<Rec>> byDate = new TreeMap<>();
        for (Rec r : sigs) byDate.computeIfAbsent(r.date, k -> new ArrayList<>()).add(r);
        System.out.printf("  Trading days with NIFTY signals: %d  |  Avg calls/active day: %.2f%n",
                byDate.size(), tot * 1.0 / Math.max(byDate.size(), 1));

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

        // Daily distribution
        int[] hist = new int[6];
        for (List<Rec> day : byDate.values()) hist[Math.min(day.size(), 5)]++;
        System.out.println();
        System.out.println("  Daily distribution:");
        System.out.printf("    1: %d days | 2: %d | 3: %d | 4: %d | 5+: %d%n",
                hist[1], hist[2], hist[3], hist[4], hist[5]);
    }
}
