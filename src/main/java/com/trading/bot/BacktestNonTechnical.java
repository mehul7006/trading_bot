package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.agents.PredictionAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * BacktestNonTechnical — tests 5 non-technical / market-context filters
 * on top of the existing V29.0 strategy to measure their real impact.
 *
 * DOES NOT modify any main code — experimental only.
 *
 * FILTERS TESTED:
 *  1. India VIX Regime       — real VIX from Upstox (NSE_INDEX|India VIX)
 *                              VIX > 20: require stronger signal; VIX > 25: skip
 *  2. Expiry Day Logic       — Thursday = weekly options expiry
 *                              Morning expiry: skip first hour; afternoon: allow
 *  3. Day-of-Week Bias       — Monday/Friday higher gate; Tue-Wed lowest gate
 *  4. Opening Gap Filter     — large gap open = gap-fill risk in first 30 min
 *  5. Multi-Day Momentum     — 3+ consecutive down/up days = institutional trend
 *
 * Prints comparison: Current V29.0 vs Experimental (with non-technical filters)
 */
public class BacktestNonTechnical {

    private static final double MIN_CONFIDENCE = 86.0;
    private static final Map<String, Double> MIN_POINTS = Map.of(
        "NIFTY50", 28.0,
        "SENSEX",  65.0
    );
    private static final long COOLDOWN_MS   = 10L * 60 * 1000;
    private static final int  LOOKAHEAD     = 24;
    private static final int  BACKTEST_DAYS = 30;
    private static final int  LOAD_DAYS     = 47;

    static class SignalRecord {
        String date, time, symbol, direction, outcome;
        double entryPrice, targetPts, slPts, pointsCaptured, confidence;
        boolean passedNew;
        String blockedBy;
    }

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    // VIX data: date → VIX closing value
    private static final Map<LocalDate, Double> vixByDate = new LinkedHashMap<>();

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  NON-TECHNICAL EXPERIMENTAL BACKTEST — 5 Context Filters");
        System.out.println("  VIX + Expiry Day + Day-of-Week + Gap + Multi-Day Momentum");
        System.out.println("═══════════════════════════════════════════════════════════════");

        // Load India VIX historical data first
        loadIndiaVIX();

        MarketDataAgent mdAgent   = new MarketDataAgent();
        PredictionAgent predAgent = new PredictionAgent();

        String[] symbols = {"NIFTY50", "SENSEX"};
        List<SignalRecord> allSignals = new ArrayList<>();

        for (String symbol : symbols) {
            allSignals.addAll(runBacktest(symbol, mdAgent, predAgent));
        }

        allSignals.sort(Comparator.comparing((SignalRecord r) -> r.date).thenComparing(r -> r.time));
        printReport(allSignals);
    }

    // ─── LOAD INDIA VIX ──────────────────────────────────────────────────────
    private static void loadIndiaVIX() {
        try {
            System.out.println("\nFetching India VIX data from Upstox...");
            HonestMarketDataFetcher fetcher = HonestMarketDataFetcher.getInstance();

            // Fetch last 60 days of daily VIX candles
            String toDate   = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String fromDate = LocalDate.now().minusDays(60).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            List<SimpleMarketData> vixData = fetcher.fetchHistoricalCandlesByInstrument(
                "NSE_INDEX|India VIX", "1day", fromDate, toDate);

            if (vixData != null && !vixData.isEmpty()) {
                for (SimpleMarketData d : vixData) {
                    if (d.timestamp != null) {
                        vixByDate.put(d.timestamp.toLocalDate(), d.price);
                    }
                }
                System.out.printf("✅ Loaded %d days of India VIX data. Latest VIX: %.2f%n",
                    vixByDate.size(),
                    vixByDate.values().stream().reduce((a, b) -> b).orElse(0.0));
            } else {
                System.out.println("⚠ VIX data unavailable from Upstox. Using ATR-based proxy.");
            }
        } catch (Exception e) {
            System.out.println("⚠ Could not load VIX data: " + e.getMessage() + ". Using ATR proxy.");
        }
    }

    // ─── MAIN BACKTEST LOOP ───────────────────────────────────────────────────
    private static List<SignalRecord> runBacktest(String symbol,
            MarketDataAgent mdAgent, PredictionAgent predAgent) {

        List<SignalRecord> signals = new ArrayList<>();
        try {
            List<SimpleMarketData> data = mdAgent.getHistoricalData(symbol, LOAD_DAYS);
            if (data == null || data.size() < 200) { return signals; }

            Set<LocalDate> allDatesSet = new LinkedHashSet<>();
            for (SimpleMarketData d : data) {
                if (d.timestamp != null) allDatesSet.add(d.timestamp.toLocalDate());
            }
            List<LocalDate> allDates = new ArrayList<>(allDatesSet);
            Collections.sort(allDates);

            LocalDate cutoffDate = allDates.size() > BACKTEST_DAYS
                ? allDates.get(allDates.size() - BACKTEST_DAYS)
                : allDates.get(0);

            System.out.printf("%s: scanning from %s to %s%n",
                symbol, cutoffDate, allDates.get(allDates.size() - 1));

            long lastSignalMillis = 0;
            LocalDate lastSignalDate = null;

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData candle = data.get(i);
                if (candle.timestamp == null) continue;

                LocalDate candleDate = candle.timestamp.toLocalDate();
                if (candleDate.isBefore(cutoffDate)) continue;

                LocalTime time = candle.timestamp.toLocalTime();
                if (time.isBefore(LocalTime.of(9, 15)) || time.isAfter(LocalTime.of(15, 25))) continue;

                if (!candleDate.equals(lastSignalDate)) {
                    lastSignalMillis = 0;
                    lastSignalDate = candleDate;
                }

                long candleMillis = candle.timestamp
                    .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
                if (candleMillis - lastSignalMillis < COOLDOWN_MS) continue;

                List<SimpleMarketData> history = data.subList(0, i + 1);
                AIPredictor.AIPrediction pred = predAgent.generateSignal(symbol, history);

                if (!passesGates(symbol, pred)) continue;

                lastSignalMillis = candleMillis;

                // Apply 5 non-technical context filters
                String blockedBy = applyContextFilters(history, pred.predictedDirection, candleDate, time, symbol);
                boolean passedNew = blockedBy == null;

                double tgtPts = pred.estimatedMovePoints;
                double slPts  = pred.suggestedStopLoss;
                int outcome = verifyOutcome(data, i, pred);
                String outcomeLabel;
                double pts;
                switch (outcome) {
                    case FULL_WIN    -> { outcomeLabel = "WIN";         pts =  tgtPts; }
                    case PARTIAL_WIN -> { outcomeLabel = "PARTIAL WIN"; pts =  tgtPts * 0.3; }
                    default          -> { outcomeLabel = "LOSS";        pts = -slPts; }
                }

                SignalRecord rec = new SignalRecord();
                rec.date           = candleDate.toString();
                rec.time           = candle.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
                rec.symbol         = symbol;
                rec.direction      = pred.predictedDirection;
                rec.entryPrice     = candle.price;
                rec.targetPts      = tgtPts;
                rec.slPts          = slPts;
                rec.outcome        = outcomeLabel;
                rec.pointsCaptured = pts;
                rec.confidence     = pred.confidence;
                rec.passedNew      = passedNew;
                rec.blockedBy      = blockedBy != null ? blockedBy : "";
                signals.add(rec);
            }
        } catch (Exception e) {
            System.err.println("Backtest error for " + symbol + ": " + e.getMessage());
        }
        return signals;
    }

    // ─── 5 NON-TECHNICAL CONTEXT FILTERS ─────────────────────────────────────
    private static String applyContextFilters(List<SimpleMarketData> data,
                                               String direction,
                                               LocalDate date,
                                               LocalTime time,
                                               String symbol) {
        // ── Filter 1: India VIX Regime ───────────────────────────────────────
        // VIX > 25: Market in panic — signals are unreliable, skip all
        // VIX 20-25: High volatility — require VIX to be inline with direction
        // VIX 15-20: Slightly elevated — normal trading
        // VIX < 15: Low volatility — may have fewer large moves
        double vix = getVIX(date, data);
        if (vix > 25.0) return "VIX_PANIC_" + String.format("%.0f", vix);
        if (vix > 22.0) {
            // In high VIX, DOWN signals are more reliable (fear = selling)
            // UP signals in high VIX often fail — market bounces weakly
            if (direction.equals("UP") && vix > 23.0) return "VIX_HIGH_UP_RISKY_" + String.format("%.0f", vix);
        }

        // ── Filter 2: Expiry Day Logic (Thursday = weekly expiry) ────────────
        // Thursday morning (9:15-11:00): Options pinning creates fake signals → skip
        // Thursday 11:00-14:30: Normal with slightly higher gate (handled via label)
        // Thursday 14:30-15:30: Strong unwinding moves — allow
        // Last Thursday of month = monthly expiry: very volatile, skip morning entirely
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.THURSDAY) {
            boolean isMonthlyExpiry = isLastThursdayOfMonth(date);
            if (isMonthlyExpiry && time.isBefore(LocalTime.of(13, 0))) {
                return "MONTHLY_EXPIRY_MORNING";
            }
            if (time.isBefore(LocalTime.of(10, 15))) {
                return "WEEKLY_EXPIRY_FIRST_HOUR";  // options pinning zone
            }
        }

        // ── Filter 3: Day-of-Week Bias ────────────────────────────────────────
        // Monday: Post-weekend uncertainty, global markets impact, gap-fill tendency
        //         DOWN signals on Monday often fail (bargain hunting), UP ok
        // Friday: Pre-weekend de-risking, FIIs square off positions
        //         UP signals on Friday afternoon (after 14:00) often fail
        if (dow == DayOfWeek.MONDAY && time.isBefore(LocalTime.of(10, 0))) {
            // Monday first 45 min: most uncertain (gap fill happening)
            return "MONDAY_OPENING_UNCERTAINTY";
        }
        if (dow == DayOfWeek.FRIDAY && time.isAfter(LocalTime.of(14, 30))
                && direction.equals("UP")) {
            // Friday afternoon UP signals: FIIs selling, risk-off
            return "FRIDAY_AFTERNOON_UP_RISKY";
        }

        // ── Filter 4: Opening Gap Filter ─────────────────────────────────────
        // If market opened with large gap (>0.5% from previous close),
        // avoid taking signals in the SAME DIRECTION as the gap in first 30 min.
        // Reason: gaps > 0.5% have a 65%+ chance of partial/full fill in first 30 min.
        // Exception: If gap is > 1.5%, it often means a NEW TREND — allow same direction after 30 min.
        double gapPct = calcOpeningGap(data, date);
        if (Math.abs(gapPct) > 0.5 && time.isBefore(LocalTime.of(9, 50))) {
            // Gap up + UP signal in first 35 min = gap fill risk
            if (gapPct > 0.5 && direction.equals("UP"))   return "GAP_UP_FILL_RISK_" + String.format("%.1f", gapPct) + "pct";
            // Gap down + DOWN signal in first 35 min = gap fill risk
            if (gapPct < -0.5 && direction.equals("DOWN")) return "GAP_DOWN_FILL_RISK_" + String.format("%.1f", gapPct) + "pct";
        }

        // ── Filter 5: Multi-Day Momentum (FII proxy) ─────────────────────────
        // 3+ consecutive down days = institutional selling / FII outflow
        //   → UP signals in this context often fail (fighting the institutional trend)
        //   → DOWN signals have institutional tailwind (allow)
        // 3+ consecutive up days = institutional buying / FII inflow
        //   → DOWN signals in this context often fail
        //   → UP signals have institutional tailwind (allow)
        int consecutiveDays = getConsecutiveDayStreak(data, date);
        if (consecutiveDays <= -3 && direction.equals("UP")) {
            // 3+ down days = FII selling mode — UP signals face strong headwind
            return "MULTI_DAY_BEAR_STREAK_" + Math.abs(consecutiveDays) + "d";
        }
        if (consecutiveDays >= 4 && direction.equals("DOWN")) {
            // 4+ up days = FII buying mode — DOWN signals face strong headwind
            return "MULTI_DAY_BULL_STREAK_" + consecutiveDays + "d";
        }

        return null; // all filters passed
    }

    // ─── HELPER CALCULATIONS ─────────────────────────────────────────────────

    /**
     * Get India VIX for a given date.
     * Falls back to ATR-based proxy if real VIX data not available.
     */
    private static double getVIX(LocalDate date, List<SimpleMarketData> data) {
        // Try real VIX data first
        if (vixByDate.containsKey(date)) {
            return vixByDate.get(date);
        }
        // Look for recent VIX (previous day)
        for (int d = 1; d <= 7; d++) {
            Double v = vixByDate.get(date.minusDays(d));
            if (v != null) return v;
        }
        // ATR-based proxy if no VIX data at all
        return calcATRBasedVIXProxy(data);
    }

    /**
     * Calculate ATR-based VIX proxy (annualized volatility estimate).
     * ATR/price × sqrt(252 × 78 bars/day) ≈ annualized vol (like VIX)
     * For Nifty at 23000: ATR of 40 pts → 40/23000 × sqrt(252×78) × 100 ≈ 14.5
     */
    private static double calcATRBasedVIXProxy(List<SimpleMarketData> data) {
        int n = data.size();
        if (n < 15) return 15.0; // default normal
        int period = 14;
        double atr = 0;
        for (int i = n - period; i < n; i++) {
            double tr = Math.max(data.get(i).high - data.get(i).low,
                         Math.max(Math.abs(data.get(i).high - data.get(i-1).price),
                                  Math.abs(data.get(i).low  - data.get(i-1).price)));
            atr += tr;
        }
        atr /= period;
        double price = data.get(n-1).price;
        // Annualize: 5-min bars, ~75 per day, 252 trading days
        return (atr / price) * Math.sqrt(252.0 * 75.0) * 100.0;
    }

    /**
     * Check if the given Thursday is the last Thursday of the month
     * (= monthly options expiry day).
     */
    private static boolean isLastThursdayOfMonth(LocalDate date) {
        // Last Thursday = date + 7 days would be in the next month
        return date.plusDays(7).getMonthValue() != date.getMonthValue();
    }

    /**
     * Calculate opening gap % for the given date vs previous day's close.
     * Returns positive for gap up, negative for gap down.
     */
    private static double calcOpeningGap(List<SimpleMarketData> data, LocalDate today) {
        int n = data.size();
        double todayOpen    = Double.NaN;
        double prevDayClose = Double.NaN;
        LocalDate prevDay   = null;

        // Scan backwards to find today's open candle and prev day's last close
        for (int i = n - 1; i >= 0; i--) {
            SimpleMarketData d = data.get(i);
            if (d.timestamp == null) continue;
            LocalDate barDate = d.timestamp.toLocalDate();

            if (barDate.equals(today) && Double.isNaN(todayOpen)) {
                // Find FIRST candle of today (scan forward for open bar)
                // Since we're scanning backward, continue to find earliest today candle
                todayOpen = d.open; // Will be overwritten as we find earlier bars
            } else if (!barDate.equals(today) && !Double.isNaN(todayOpen)) {
                // First bar not from today = prev day
                prevDayClose = d.price;
                break;
            }
        }
        // Re-scan to find actual first candle of today (earliest open)
        for (int i = 0; i < n; i++) {
            SimpleMarketData d = data.get(i);
            if (d.timestamp != null && d.timestamp.toLocalDate().equals(today)) {
                todayOpen = d.open;
                break;
            }
        }

        if (Double.isNaN(todayOpen) || Double.isNaN(prevDayClose) || prevDayClose < 0.001) {
            return 0.0;
        }
        return ((todayOpen - prevDayClose) / prevDayClose) * 100.0;
    }

    /**
     * Count consecutive daily streak ending at the given date.
     * Returns positive for consecutive UP days (close > prev close),
     *         negative for consecutive DOWN days.
     */
    private static int getConsecutiveDayStreak(List<SimpleMarketData> data, LocalDate today) {
        // Build map of date → daily close
        Map<LocalDate, Double> dailyClose = new LinkedHashMap<>();
        for (SimpleMarketData d : data) {
            if (d.timestamp != null) {
                dailyClose.merge(d.timestamp.toLocalDate(), d.price, (a, b) -> b); // last price of day
            }
        }

        List<LocalDate> days = new ArrayList<>(dailyClose.keySet());
        Collections.sort(days);

        int todayIdx = days.indexOf(today);
        if (todayIdx < 1) return 0;

        int streak = 0;
        for (int i = todayIdx - 1; i >= 0; i--) {
            double curr = dailyClose.get(days.get(i));
            double prev = (i > 0) ? dailyClose.get(days.get(i - 1)) : curr;
            boolean up = curr > prev * 1.001; // at least 0.1% up to count
            boolean dn = curr < prev * 0.999;

            if (streak == 0) {
                streak = up ? 1 : (dn ? -1 : 0);
            } else if (streak > 0 && up) {
                streak++;
            } else if (streak < 0 && dn) {
                streak--;
            } else {
                break;
            }
            if (Math.abs(streak) >= 7) break; // cap at 7
        }
        return streak;
    }

    // ─── GATES & OUTCOME ─────────────────────────────────────────────────────
    private static boolean passesGates(String symbol, AIPredictor.AIPrediction p) {
        if ("NEUTRAL".equals(p.predictedDirection)) return false;
        double minPts = MIN_POINTS.getOrDefault(symbol, 20.0);
        return p.confidence >= MIN_CONFIDENCE && p.estimatedMovePoints >= minPts;
    }

    private static int verifyOutcome(List<SimpleMarketData> data, int idx, AIPredictor.AIPrediction pred) {
        double entry = data.get(idx).price;
        String dir   = pred.predictedDirection;
        double tgtPx = dir.equals("UP") ? entry + pred.estimatedMovePoints : entry - pred.estimatedMovePoints;
        double slPx  = dir.equals("UP") ? entry - pred.suggestedStopLoss   : entry + pred.suggestedStopLoss;
        double halfPx= dir.equals("UP") ? entry + pred.estimatedMovePoints * 0.5 : entry - pred.estimatedMovePoints * 0.5;
        boolean half = false;
        for (int j = 1; j <= LOOKAHEAD && (idx + j) < data.size(); j++) {
            SimpleMarketData c = data.get(idx + j);
            if (dir.equals("UP")) {
                if (c.low  <= slPx)  return half ? PARTIAL_WIN : LOSS;
                if (c.high >= tgtPx) return FULL_WIN;
                if (c.high >= halfPx) half = true;
            } else {
                if (c.high >= slPx)  return half ? PARTIAL_WIN : LOSS;
                if (c.low  <= tgtPx) return FULL_WIN;
                if (c.low  <= halfPx) half = true;
            }
        }
        return half ? PARTIAL_WIN : LOSS;
    }

    // ─── REPORT ──────────────────────────────────────────────────────────────
    private static void printReport(List<SignalRecord> signals) {
        if (signals.isEmpty()) { System.out.println("No signals."); return; }

        int cTotal=0,cW=0,cP=0,cL=0; double cNet=0;
        int eTotal=0,eW=0,eP=0,eL=0; double eNet=0;
        Map<String, int[]> blockedStats = new TreeMap<>();

        for (SignalRecord r : signals) {
            cTotal++; cNet += r.pointsCaptured;
            if (r.outcome.equals("WIN"))          cW++;
            else if (r.outcome.equals("PARTIAL WIN")) cP++;
            else                                  cL++;

            if (r.passedNew) {
                eTotal++; eNet += r.pointsCaptured;
                if (r.outcome.equals("WIN"))          eW++;
                else if (r.outcome.equals("PARTIAL WIN")) eP++;
                else                                  eL++;
            } else {
                // Normalize the blocked-by key (strip numbers for grouping)
                String key = normalizeKey(r.blockedBy);
                blockedStats.computeIfAbsent(key, k -> new int[4]);
                int[] b = blockedStats.get(key);
                b[0]++;
                if (r.outcome.equals("WIN"))           b[1]++;
                else if (r.outcome.equals("PARTIAL WIN")) b[2]++;
                else                                   b[3]++;
            }
        }

        double cWR = cTotal > 0 ? (cW+cP)*100.0/cTotal : 0;
        double eWR = eTotal > 0 ? (eW+eP)*100.0/eTotal : 0;

        System.out.println("\n");
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║    NON-TECHNICAL CONTEXT FILTERS — 30-DAY BACKTEST COMPARISON        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");

        printBox("CURRENT V29.0 (baseline — no context filters)", cTotal, cW, cP, cL, cWR, cNet);
        printBox("EXPERIMENTAL — With 5 Context Filters", eTotal, eW, eP, eL, eWR, eNet);

        System.out.printf("%n  Δ Win Rate : %+.1f%% (%s)%n", eWR-cWR, eWR>cWR?"IMPROVED":"WORSE");
        System.out.printf("  Δ Calls    : %+d (blocked %d calls)%n", eTotal-cTotal, cTotal-eTotal);
        System.out.printf("  Δ Net Pts  : %+.1f pts%n%n", eNet-cNet);

        // Per-filter breakdown
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              FILTER-BY-FILTER QUALITY ANALYSIS                       ║");
        System.out.println("╠══════════════════════════════╦═══════╦═══════╦═════════╦══════╦══════╣");
        System.out.println("║ Filter                       ║ Total ║  Wins ║ Partial ║ Loss ║  WR% ║");
        System.out.println("╠══════════════════════════════╬═══════╬═══════╬═════════╬══════╬══════╣");
        for (Map.Entry<String, int[]> e : blockedStats.entrySet()) {
            int[] b = e.getValue();
            double bWR = b[0]>0 ? (b[1]+b[2])*100.0/b[0] : 0;
            // If WR of blocked signals is LOW → filter removes mostly losses → GOOD
            String q = bWR < 40 ? "GREAT ✅" : bWR < 55 ? "OK  🔶" : "HURTS ❌";
            System.out.printf("║ %-28s ║  %4d ║  %4d ║   %4d  ║ %4d ║%4.0f%% ║ %s%n",
                e.getKey(), b[0], b[1], b[2], b[3], bWR, q);
        }
        System.out.println("╚══════════════════════════════╩═══════╩═══════╩═════════╩══════╩══════╝");

        System.out.println("\n  Legend:");
        System.out.println("  GREAT ✅ = filter blocked mostly losses (WR of blocked signals < 40%)");
        System.out.println("  OK  🔶   = filter removed mix of wins and losses (40-55%)");
        System.out.println("  HURTS ❌ = filter blocked good signals (WR of blocked signals > 55%)");

        // Per-symbol breakdown
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                   PER-SYMBOL COMPARISON                              ║");
        System.out.println("╠══════════════╦═══════════════════════╦═══════════════════════╦════════╣");
        System.out.println("║ Symbol       ║   Current V29.0       ║   Experimental         ║ ΔWR%   ║");
        System.out.println("╠══════════════╬═══════════════════════╬═══════════════════════╬════════╣");
        for (String sym : new String[]{"NIFTY50","SENSEX"}) {
            int cT=0,cW2=0,cP2=0; double cN=0;
            int eT=0,eW2=0,eP2=0; double eN=0;
            for (SignalRecord r : signals) {
                if (!r.symbol.equals(sym)) continue;
                cT++; cN += r.pointsCaptured;
                if (r.outcome.equals("WIN"))          cW2++;
                else if (r.outcome.equals("PARTIAL WIN")) cP2++;
                if (r.passedNew) {
                    eT++; eN += r.pointsCaptured;
                    if (r.outcome.equals("WIN"))          eW2++;
                    else if (r.outcome.equals("PARTIAL WIN")) eP2++;
                }
            }
            double cWRs = cT>0?(cW2+cP2)*100.0/cT:0;
            double eWRs = eT>0?(eW2+eP2)*100.0/eT:0;
            System.out.printf("║ %-12s ║ %3d calls  %5.1f%%  %+7.0f ║ %3d calls  %5.1f%%  %+7.0f ║ %+5.1f%% ║%n",
                sym, cT, cWRs, cN, eT, eWRs, eN, eWRs-cWRs);
        }
        System.out.println("╚══════════════╩═══════════════════════╩═══════════════════════╩════════╝");

        // VIX summary
        if (!vixByDate.isEmpty()) {
            DoubleSummaryStatistics vixStats = vixByDate.values().stream()
                .mapToDouble(Double::doubleValue).summaryStatistics();
            System.out.printf("%n  India VIX during 30-day window: Min=%.1f  Max=%.1f  Avg=%.1f%n",
                vixStats.getMin(), vixStats.getMax(), vixStats.getAverage());
            long highVixDays = vixByDate.entrySet().stream()
                .filter(e -> e.getValue() > 20.0).count();
            System.out.printf("  High VIX days (>20): %d out of %d days%n",
                highVixDays, vixByDate.size());
        }

        // Final recommendation
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        RECOMMENDATION                                ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        if (eWR > cWR + 2.0 && eTotal >= 100) {
            System.out.printf("║  ✅ IMPLEMENT: +%.1f%% WR gain with %d calls/month — clear improvement  ║%n",
                eWR-cWR, eTotal);
        } else if (eWR > cWR + 1.0) {
            System.out.printf("║  🔶 SELECTIVE: +%.1f%% gain — implement only the GREAT filters           ║%n",
                eWR-cWR);
            System.out.println("║     Add filters marked GREAT ✅ to main code; skip HURTS ❌ filters    ║");
        } else if (eWR > cWR) {
            System.out.printf("║  ⚠  MARGINAL: Only +%.1f%% improvement — borderline benefit             ║%n",
                eWR-cWR);
        } else {
            System.out.printf("║  ❌ DO NOT IMPLEMENT: Context filters made WR %.1f%% worse               ║%n",
                cWR-eWR);
        }
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
    }

    private static void printBox(String title, int total, int w, int p, int l, double wr, double net) {
        System.out.println("\n┌─────────────────────────────────────────────────────────────────────┐");
        System.out.printf( "│  %s%n", title);
        System.out.printf( "│  Calls: %-5d  Wins: %-4d  Partial: %-4d  Losses: %-4d%n", total, w, p, l);
        System.out.printf( "│  Win Rate: %.1f%%   Net Points: %+.1f pts%n", wr, net);
        System.out.println("└─────────────────────────────────────────────────────────────────────┘");
    }

    /** Normalize blocked-by keys to group related variants (e.g., strip numeric suffixes) */
    private static String normalizeKey(String key) {
        if (key.startsWith("VIX_PANIC")) return "VIX_PANIC (>25)";
        if (key.startsWith("VIX_HIGH_UP")) return "VIX_HIGH_UP_RISKY (>23)";
        if (key.startsWith("GAP_UP_FILL")) return "GAP_UP_FILL_RISK (>0.5%)";
        if (key.startsWith("GAP_DOWN_FILL")) return "GAP_DOWN_FILL_RISK (>0.5%)";
        if (key.startsWith("MULTI_DAY_BEAR")) return "MULTI_DAY_BEAR_STREAK (3+d)";
        if (key.startsWith("MULTI_DAY_BULL")) return "MULTI_DAY_BULL_STREAK (4+d)";
        return key;
    }
}
