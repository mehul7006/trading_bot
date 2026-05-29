package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.agents.PredictionAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.ai.NiftyPostFilterV1;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Backtest120DayNiftyFiltered — runs NIFTY V29 (unchanged) and SIMULATES applying
 * {@link NiftyPostFilterV1} in one pass. Records BOTH outcomes per candle so we
 * see exactly which V29 calls the filter would have killed and what the resulting
 * WR + call count would be.
 *
 * Does NOT modify AIPredictor or any live code. Pure test harness.
 *
 * Same checkOutcome semantics as the other 120-day fast runners for apples-to-apples.
 */
public class Backtest120DayNiftyFiltered {

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
        String date, time, direction, outcome, postFilterTag;
        double entry, tgtPts, slPts, pts, confidence;
        boolean keptByFilter;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║   NIFTY V29 vs V29+PostFilterV1  —  120-DAY DUAL BACKTEST (test only)       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");

        AIPredictor.USE_SENSEX_V31 = false;  // irrelevant for NIFTY but defensive

        MarketDataAgent md = new MarketDataAgent();
        PredictionAgent pred = new PredictionAgent();

        List<SimpleMarketData> nifty = md.getHistoricalData("NIFTY50", LOAD_DAYS);
        if (nifty == null || nifty.size() < 500) {
            System.err.println("Insufficient NIFTY data");
            return;
        }
        System.out.println("Loaded " + nifty.size() + " candles for NIFTY50.\n");

        List<Rec> all = new ArrayList<>();
        Set<LocalDate> datesSet = new LinkedHashSet<>();
        for (SimpleMarketData d : nifty) if (d.timestamp != null) datesSet.add(d.timestamp.toLocalDate());
        List<LocalDate> dates = new ArrayList<>(datesSet);
        Collections.sort(dates);
        LocalDate cutoff = dates.size() > BACKTEST_DAYS
                ? dates.get(dates.size() - BACKTEST_DAYS)
                : dates.get(0);

        System.out.printf("Scanning %s → %s%n%n", cutoff, dates.get(dates.size() - 1));

        long lastSigMs = 0;
        long lastFilteredSigMs = 0;
        long lastUpLossMs = 0, lastDownLossMs = 0;
        final long LOSS_COOLDOWN_MS = 90L * 60 * 1000;
        LocalDate lastDate = null;
        int processed = 0, lastProgress = 0;

        for (int i = 200; i < nifty.size(); i++) {
            SimpleMarketData c = nifty.get(i);
            if (c.timestamp == null) continue;
            LocalDate cDate = c.timestamp.toLocalDate();
            if (cDate.isBefore(cutoff)) continue;
            LocalTime cTime = c.timestamp.toLocalTime();
            if (cTime.isBefore(SCAN_START) || cTime.isAfter(SCAN_END)) continue;

            if (!cDate.equals(lastDate)) {
                lastSigMs = 0; lastFilteredSigMs = 0;
                lastUpLossMs = 0; lastDownLossMs = 0;
                lastDate = cDate;
            }
            long cMs = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
            if (cMs - lastSigMs < COOLDOWN_MS) continue;

            processed++;
            if (processed - lastProgress >= 500) {
                System.out.printf("  progress: %d scanned, V29=%d kept by filter=%d, date=%s%n",
                        processed, all.size(),
                        (int) all.stream().filter(r -> r.keptByFilter).count(), cDate);
                lastProgress = processed;
            }

            List<SimpleMarketData> hist = nifty.subList(0, i + 1);
            AIPredictor.AIPrediction p = pred.generateSignal("NIFTY50", hist);

            if ("NEUTRAL".equals(p.predictedDirection)) continue;
            if (p.confidence < MIN_CONFIDENCE) continue;
            if (p.estimatedMovePoints < MIN_POINTS) continue;

            lastSigMs = cMs;

            // ── Simulate filter pass
            double atr14 = atr(hist, 14);
            double adx14 = adxSimple(hist, 14);
            AIPredictor.AIPrediction filtered = NiftyPostFilterV1.filter(p, hist, c, atr14, adx14);
            boolean keptByFilter = !"NEUTRAL".equals(filtered.predictedDirection);

            // Same-direction loss cooldown applies to filtered series too
            if (keptByFilter) {
                if ("UP".equals(filtered.predictedDirection)   && cMs - lastUpLossMs   < LOSS_COOLDOWN_MS) keptByFilter = false;
                if ("DOWN".equals(filtered.predictedDirection) && cMs - lastDownLossMs < LOSS_COOLDOWN_MS) keptByFilter = false;
            }
            if (keptByFilter && cMs - lastFilteredSigMs < COOLDOWN_MS) keptByFilter = false;
            if (keptByFilter) lastFilteredSigMs = cMs;

            double tgtPts = p.estimatedMovePoints;
            double slPts  = p.suggestedStopLoss;

            int outcome = checkOutcome(nifty, i, p.predictedDirection, tgtPts, slPts);
            String label; double pts;
            switch (outcome) {
                case FULL_WIN    -> { label = "WIN";         pts =  tgtPts; }
                case PARTIAL_WIN -> { label = "PARTIAL WIN"; pts =  tgtPts * 0.3; }
                default          -> {
                    label = "LOSS"; pts = -slPts;
                    if (keptByFilter) {
                        if ("UP".equals(p.predictedDirection))   lastUpLossMs   = cMs;
                        if ("DOWN".equals(p.predictedDirection)) lastDownLossMs = cMs;
                    }
                }
            }

            Rec r = new Rec();
            r.date = cDate.toString();
            r.time = c.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
            r.direction = p.predictedDirection;
            r.entry = c.price; r.tgtPts = tgtPts; r.slPts = slPts;
            r.outcome = label; r.pts = pts; r.confidence = p.confidence;
            r.keptByFilter = keptByFilter;
            r.postFilterTag = "NEUTRAL".equals(filtered.predictedDirection)
                    ? filtered.predictionReasoning.replaceFirst(" \\|.*", "")
                    : "KEPT";
            all.add(r);
        }
        printReport(all);
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
        if (sigs.isEmpty()) { System.out.println("  No signals."); return; }

        // ── V29 (unfiltered) totals
        int totV29 = sigs.size();
        int wV29=0,pV29=0,lV29=0; double netV29=0;
        for (Rec r : sigs) {
            if ("WIN".equals(r.outcome)) wV29++;
            else if ("PARTIAL WIN".equals(r.outcome)) pV29++;
            else lV29++;
            netV29 += r.pts;
        }
        double wrV29 = (wV29+pV29)*100.0/totV29;

        // ── Filtered totals
        List<Rec> kept = sigs.stream().filter(r -> r.keptByFilter).toList();
        int totF = kept.size();
        int wF=0,pF=0,lF=0; double netF=0;
        for (Rec r : kept) {
            if ("WIN".equals(r.outcome)) wF++;
            else if ("PARTIAL WIN".equals(r.outcome)) pF++;
            else lF++;
            netF += r.pts;
        }
        double wrF = totF > 0 ? (wF+pF)*100.0/totF : 0;

        System.out.println("═══════════════════ NIFTY V29 vs V29+PostFilter ═══════════════════");
        System.out.printf("%-26s %12s %12s %12s%n", "Metric", "V29 raw", "V29+filter", "delta");
        System.out.println("  " + "─".repeat(70));
        System.out.printf("  %-24s %12d %12d %+12d%n", "Total calls",    totV29, totF, totF - totV29);
        System.out.printf("  %-24s %12d %12d %+12d%n", "Full wins",      wV29,   wF,   wF - wV29);
        System.out.printf("  %-24s %12d %12d %+12d%n", "Partial wins",   pV29,   pF,   pF - pV29);
        System.out.printf("  %-24s %12d %12d %+12d%n", "Losses",         lV29,   lF,   lF - lV29);
        System.out.printf("  %-24s %11.1f%% %11.1f%% %+11.1fpp%n", "Win rate (W+P)", wrV29, wrF, wrF - wrV29);
        System.out.printf("  %-24s %+,12.0f %+,12.0f %+12.0f%n", "Net points",    netV29, netF, netF - netV29);

        // ── Rejection breakdown
        Map<String, int[]> rejByTag = new TreeMap<>();  // tag -> [count, wins, losses]
        for (Rec r : sigs) {
            if (r.keptByFilter) continue;
            int[] s = rejByTag.computeIfAbsent(r.postFilterTag, k -> new int[3]);
            s[0]++;
            if ("WIN".equals(r.outcome) || "PARTIAL WIN".equals(r.outcome)) s[1]++;
            else s[2]++;
        }
        System.out.println();
        System.out.println("  Filter rejection breakdown (lost signals: how many would have won vs lost?):");
        System.out.printf("    %-22s %6s %6s %6s %8s%n", "Tag", "count", "wins", "loss", "WR%");
        System.out.println("    " + "─".repeat(55));
        for (var e : rejByTag.entrySet()) {
            int[] s = e.getValue();
            double wr = s[0] > 0 ? s[1]*100.0/s[0] : 0;
            System.out.printf("    %-22s %6d %6d %6d %7.1f%%%n", e.getKey(), s[0], s[1], s[2], wr);
        }

        // ── Verdict
        double wrDelta = wrF - wrV29;
        double callDelta = totV29 > 0 ? (totF - totV29) * 100.0 / totV29 : 0;
        System.out.println();
        if (wrDelta >= 5 && callDelta > -30) {
            System.out.printf("  VERDICT: +%.1fpp WR / %.0f%% call change — ✓ RECOMMEND integrating (ask user)%n",
                    wrDelta, callDelta);
        } else if (wrDelta > 0) {
            System.out.printf("  VERDICT: +%.1fpp WR / %.0f%% call change — ⚠️ MARGINAL — user decides%n",
                    wrDelta, callDelta);
        } else {
            System.out.printf("  VERDICT: %.1fpp WR / %.0f%% call change — ✗ SKIP (no improvement)%n",
                    wrDelta, callDelta);
        }

        // ── Daily distribution comparison
        Map<String, Integer> v29Days = new TreeMap<>(), filteredDays = new TreeMap<>();
        for (Rec r : sigs) v29Days.merge(r.date, 1, Integer::sum);
        for (Rec r : kept)  filteredDays.merge(r.date, 1, Integer::sum);
        System.out.println();
        System.out.printf("  Trading days with calls: V29=%d, V29+filter=%d%n",
                v29Days.size(), filteredDays.size());
        System.out.printf("  Avg calls/active day:    V29=%.2f, V29+filter=%.2f%n",
                totV29 * 1.0 / Math.max(v29Days.size(), 1),
                totF * 1.0 / Math.max(filteredDays.size(), 1));
    }
}
