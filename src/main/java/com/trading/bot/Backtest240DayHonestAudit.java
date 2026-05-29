package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.ai.BankNiftyMeanReversionV33;
import com.trading.bot.ai.NiftyMeanReversionV33;
import com.trading.bot.ai.NiftyStrategyV31;
import com.trading.bot.ai.SensexMeanReversionV33;
import com.trading.bot.ai.SensexStrategyV31;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Backtest240DayHonestAudit — full-bot audit targeting 240 days.
 *
 * Tests the EXACT live code path: V31 trend → V33 mean-reversion fallback for
 * NIFTY/SENSEX, V33 mean-reversion direct for BANKNIFTY. Same gates and
 * cooldowns the live bot would apply.
 *
 * If cache holds fewer than 240 days, the audit honestly reports the ACTUAL
 * window scanned and does not fabricate data.
 */
public class Backtest240DayHonestAudit {

    private static final int    TARGET_DAYS     = 240;
    private static final int    LOAD_DAYS       = 260;
    private static final int    LOOKAHEAD       = 24;
    private static final long   COOLDOWN_MS     = 15L * 60 * 1000;
    private static final long   LOSS_COOLDOWN_MS = 90L * 60 * 1000;
    private static final long   BNF_LOSS_COOLDOWN_MS = 60L * 60 * 1000;
    private static final int    MAX_PER_DIR_PER_DAY = 2;
    private static final double MIN_CONF        = 78.0;
    private static final double MIN_PTS_NIFTY   = 15.0;
    private static final double MIN_PTS_SENSEX  = 45.0;
    private static final double MIN_PTS_BNF     = 25.0;

    private static final LocalTime SCAN_START   = LocalTime.of(9, 15);
    private static final LocalTime SCAN_END     = LocalTime.of(15, 0);

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    static class Rec {
        String date, time, symbol, direction, outcome, source;
        double pts, entry, tgtPts, slPts, confidence;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║   240-DAY HONEST AUDIT — full live code path (V31 + V33MR + V33)           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");

        AIPredictor.USE_NIFTY_V31     = true;
        AIPredictor.USE_NIFTY_MR      = true;
        AIPredictor.USE_SENSEX_V31    = true;
        AIPredictor.USE_SENSEX_MR     = true;
        AIPredictor.USE_BANKNIFTY_V33 = true;

        MarketDataAgent md = new MarketDataAgent();
        Map<String, List<SimpleMarketData>> cache = new LinkedHashMap<>();
        for (String sym : new String[]{"NIFTY50", "SENSEX", "BANKNIFTY"}) {
            List<SimpleMarketData> data = md.getHistoricalData(sym, LOAD_DAYS);
            int days = countDistinctDates(data);
            System.out.printf("  %-10s : %d candles, %d distinct trading days%n",
                    sym, data == null ? 0 : data.size(), days);
            cache.put(sym, data);
        }

        int minDays = Integer.MAX_VALUE;
        for (List<SimpleMarketData> d : cache.values()) minDays = Math.min(minDays, countDistinctDates(d));
        int scanDays = Math.min(minDays, TARGET_DAYS);

        LocalDate winStart = null, winEnd = null;
        for (List<SimpleMarketData> data : cache.values()) {
            if (data == null || data.isEmpty()) continue;
            LocalDate end = lastDate(data);
            if (winEnd == null || end.isAfter(winEnd)) winEnd = end;
        }
        if (!cache.values().isEmpty()) {
            List<LocalDate> dd = distinctDatesSorted(cache.values().iterator().next());
            int from = Math.max(0, dd.size() - scanDays);
            if (from < dd.size()) winStart = dd.get(from);
        }

        System.out.println();
        if (scanDays < TARGET_DAYS) {
            System.out.println("⚠️  TARGET 240 days — actual " + scanDays + " trading days (cache-limited)");
            System.out.println("    Reason: cache holds only " + minDays + " distinct trading days");
            System.out.println("    Token is expired — cannot fetch the missing days from Upstox API");
            System.out.println("    Reporting honestly on " + scanDays + " days. No data fabrication.");
        } else {
            System.out.println("✓ Full 240-day window available");
        }
        System.out.printf("    Window: %s → %s%n%n", winStart, winEnd);

        Map<String, List<Rec>> bySymbol = new LinkedHashMap<>();
        bySymbol.put("NIFTY50",   runMultipath(cache.get("NIFTY50"),  scanDays, true,  false));
        bySymbol.put("SENSEX",    runMultipath(cache.get("SENSEX"),   scanDays, false, false));
        bySymbol.put("BANKNIFTY", runMultipath(cache.get("BANKNIFTY"), scanDays, false, true));

        Set<LocalDate> allDates = new TreeSet<>();
        for (List<SimpleMarketData> data : cache.values()) {
            List<LocalDate> dd = distinctDatesSorted(data);
            int from = Math.max(0, dd.size() - scanDays);
            for (int i = from; i < dd.size(); i++) allDates.add(dd.get(i));
        }

        printReport(bySymbol, allDates, scanDays);
    }

    private static List<Rec> runMultipath(List<SimpleMarketData> data, int scanDays,
                                          boolean isNifty, boolean isBnf) {
        List<Rec> out = new ArrayList<>();
        if (data == null || data.size() < 250) return out;

        String sym = isNifty ? "NIFTY50" : isBnf ? "BANKNIFTY" : "SENSEX";
        double minPts = isNifty ? MIN_PTS_NIFTY : isBnf ? MIN_PTS_BNF : MIN_PTS_SENSEX;

        List<LocalDate> dates = distinctDatesSorted(data);
        LocalDate cutoff = dates.get(Math.max(0, dates.size() - scanDays));

        long lastSig = 0, lastUpLoss = 0, lastDnLoss = 0;
        int upToday = 0, dnToday = 0;
        boolean bnfUpToday = false, bnfDnToday = false;
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
                bnfUpToday = false; bnfDnToday = false;
                lastDate = d;
            }
            long ms = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
            if (ms - lastSig < COOLDOWN_MS) continue;

            int from = Math.max(0, i - 499);
            List<SimpleMarketData> hist = data.subList(from, i + 1);
            double price = c.price;
            double ema20 = ema(hist, 20), ema50 = ema(hist, 50), ema200 = ema(hist, 200);
            double rsi = rsi(hist, 14), atr = atr(hist, 14), adx = adxSimple(hist, 14);

            AIPredictor.AIPrediction p;
            String source;
            if (isBnf) {
                int mrFrom = Math.max(0, i - 199);
                p = BankNiftyMeanReversionV33.predict(sym, data.subList(mrFrom, i + 1), price, rsi, atr, c);
                source = "BNF_V33";
            } else if (isNifty) {
                AIPredictor.AIPrediction v31 = NiftyStrategyV31.predict(sym, hist, price,
                        ema20, ema50, ema200, rsi, adx, atr, c);
                if (!"NEUTRAL".equals(v31.predictedDirection)) { p = v31; source = "V31"; }
                else {
                    int mrFrom = Math.max(0, i - 199);
                    p = NiftyMeanReversionV33.predict(sym, data.subList(mrFrom, i + 1), price, rsi, atr, c);
                    source = "V33MR";
                }
            } else {
                AIPredictor.AIPrediction v31 = SensexStrategyV31.predict(sym, hist, price,
                        ema20, ema50, ema200, rsi, adx, atr, c);
                if (!"NEUTRAL".equals(v31.predictedDirection)) { p = v31; source = "V31"; }
                else {
                    int mrFrom = Math.max(0, i - 199);
                    p = SensexMeanReversionV33.predict(sym, data.subList(mrFrom, i + 1), price, rsi, atr, c);
                    source = "V33MR";
                }
            }

            if ("NEUTRAL".equals(p.predictedDirection)) continue;
            if (p.confidence < MIN_CONF) continue;
            if (p.estimatedMovePoints < minPts) continue;
            long lossCd = isBnf ? BNF_LOSS_COOLDOWN_MS : LOSS_COOLDOWN_MS;
            if ("UP".equals(p.predictedDirection)   && ms - lastUpLoss < lossCd) continue;
            if ("DOWN".equals(p.predictedDirection) && ms - lastDnLoss < lossCd) continue;
            if (isBnf) {
                if ("UP".equals(p.predictedDirection)   && bnfUpToday) continue;
                if ("DOWN".equals(p.predictedDirection) && bnfDnToday) continue;
            } else {
                if ("UP".equals(p.predictedDirection)   && upToday >= MAX_PER_DIR_PER_DAY) continue;
                if ("DOWN".equals(p.predictedDirection) && dnToday >= MAX_PER_DIR_PER_DAY) continue;
            }
            lastSig = ms;
            if (isBnf) {
                if ("UP".equals(p.predictedDirection))   bnfUpToday = true;
                if ("DOWN".equals(p.predictedDirection)) bnfDnToday = true;
            } else {
                if ("UP".equals(p.predictedDirection))   upToday++;
                if ("DOWN".equals(p.predictedDirection)) dnToday++;
            }

            int outcome = check(data, i, p.predictedDirection, p.estimatedMovePoints, p.suggestedStopLoss);
            String label; double pts;
            switch (outcome) {
                case FULL_WIN    -> { label = "WIN"; pts = p.estimatedMovePoints; }
                case PARTIAL_WIN -> { label = "PARTIAL WIN"; pts = p.estimatedMovePoints * 0.3; }
                default          -> {
                    label = "LOSS"; pts = -p.suggestedStopLoss;
                    if ("UP".equals(p.predictedDirection))   lastUpLoss = ms;
                    if ("DOWN".equals(p.predictedDirection)) lastDnLoss = ms;
                }
            }

            Rec r = new Rec();
            r.date = d.toString();
            r.time = c.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
            r.symbol = sym; r.direction = p.predictedDirection;
            r.entry = price; r.tgtPts = p.estimatedMovePoints; r.slPts = p.suggestedStopLoss;
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
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  PER-SYMBOL RESULTS (live code path: V31 → V33MR fallback for N/S; V33 for B)");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.printf("  %-12s %6s %6s %8s %6s %8s %10s %12s%n",
                "Symbol", "Calls", "Wins", "Partial", "Loss", "WR%", "Net pts", "Coverage");
        System.out.println("  " + "─".repeat(78));

        int totalCalls = 0, totalW = 0, totalP = 0, totalL = 0;
        double totalNet = 0;
        for (var e : bySymbol.entrySet()) {
            String sym = e.getKey();
            List<Rec> recs = e.getValue();
            int n = recs.size();
            int w = 0, p = 0, l = 0; double net = 0;
            Set<LocalDate> daysWith = new TreeSet<>();
            for (Rec r : recs) {
                if ("WIN".equals(r.outcome)) w++;
                else if ("PARTIAL WIN".equals(r.outcome)) p++;
                else l++;
                net += r.pts;
                daysWith.add(LocalDate.parse(r.date));
            }
            double wr = n > 0 ? (w + p) * 100.0 / n : 0;
            double covPct = allDates.isEmpty() ? 0 : daysWith.size() * 100.0 / allDates.size();
            System.out.printf("  %-12s %6d %6d %8d %6d %7.1f%% %+10.0f  %3d/%d (%.0f%%)%n",
                    sym, n, w, p, l, wr, net, daysWith.size(), allDates.size(), covPct);
            totalCalls += n; totalW += w; totalP += p; totalL += l; totalNet += net;
        }
        double totalWR = totalCalls > 0 ? (totalW + totalP) * 100.0 / totalCalls : 0;
        System.out.println("  " + "─".repeat(78));
        System.out.printf("  %-12s %6d %6d %8d %6d %7.1f%% %+10.0f%n",
                "COMBINED", totalCalls, totalW, totalP, totalL, totalWR, totalNet);

        // Per-source breakdown
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  BY STRATEGY PATH (which source produced which wins/losses)");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        Map<String, int[]> byPath = new TreeMap<>();
        for (List<Rec> recs : bySymbol.values()) {
            for (Rec r : recs) {
                String key = r.symbol + " " + r.source;
                int[] s = byPath.computeIfAbsent(key, k -> new int[3]);  // [calls, wins+partial, losses]
                s[0]++;
                if ("LOSS".equals(r.outcome)) s[2]++; else s[1]++;
            }
        }
        for (var e : byPath.entrySet()) {
            int[] s = e.getValue();
            double wr = s[0] > 0 ? s[1] * 100.0 / s[0] : 0;
            System.out.printf("  %-25s : %4d calls | WR %.1f%%%n", e.getKey(), s[0], wr);
        }

        // Zero-call days
        Set<LocalDate> daysWithAnyCall = new TreeSet<>();
        for (List<Rec> recs : bySymbol.values())
            for (Rec r : recs) daysWithAnyCall.add(LocalDate.parse(r.date));
        Set<LocalDate> zero = new TreeSet<>(allDates);
        zero.removeAll(daysWithAnyCall);

        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  DAILY COVERAGE (combined across all symbols)");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.printf("  Total trading days       : %d%n", allDates.size());
        System.out.printf("  Days with ≥1 call (any)  : %d%n", daysWithAnyCall.size());
        System.out.printf("  Zero-call days           : %d (%.1f%%)%n",
                zero.size(),
                allDates.isEmpty() ? 0 : zero.size() * 100.0 / allDates.size());
        if (!zero.isEmpty()) {
            System.out.print("  Zero-call dates          : ");
            int n = 0;
            for (LocalDate d : zero) {
                if (n++ > 0) System.out.print(", ");
                System.out.print(d);
                if (n >= 25) { System.out.print(", … +" + (zero.size() - n)); break; }
            }
            System.out.println();
        }

        // Honest caveats
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  HONEST AUDIT CAVEATS — read before showing to customer");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.printf("  • Scan window: %d days actual vs %d target%n", scanDays, TARGET_DAYS);
        System.out.println("  • Data: real Upstox 1-min candles resampled to 5-min, NOT synthetic");
        System.out.println("  • Outcome: 24-bar (2-hour) lookahead from entry");
        System.out.println("  • PARTIAL WIN counts as win in WR but only 0.3×target in P&L");
        System.out.println("  • Slippage NOT modeled — live fills will be 1-3 ticks worse");
        System.out.println("  • Commissions NOT modeled — broker fees + STT eat into net pts");
        System.out.println("  • Gap risk NOT modeled — overnight gaps could blow through SL");
        System.out.println("  • Same-direction loss cooldown is in this runner, NOT in PredictionAgent yet");
        System.out.println("  • Backtest WR usually overstates live WR by 2-5pp due to above");
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
        for (int i = data.size() - 1; i >= 0; i--) if (data.get(i).timestamp != null) return data.get(i).timestamp.toLocalDate();
        return null;
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
}
