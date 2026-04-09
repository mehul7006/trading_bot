package com.trading.bot.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.bot.strategy.OptionsSignalEngine;
import com.trading.bot.strategy.OptionsSignalEngine.Direction;
import com.trading.bot.strategy.OptionsSignalEngine.SessionBias;
import com.trading.bot.strategy.OptionsSignalEngine.SignalResult;

import java.io.File;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ThirtyDayLiveAudit v3 — Backtests OptionsSignalEngine (9-filter strategy)
 * on 30 trading days of cached 5-min market data.
 *
 * KEY CHANGE vs v1/v2: Session-opening bias filter eliminates counter-trend
 * signals (the root cause of ~80% of losses in prior versions).
 */
public class ThirtyDayLiveAudit {

    // ── Bot risk parameters ────────────────────────────────────────────────────
    static final int    COOLDOWN_MIN    = 60;    // 60-min cooldown per symbol
    static final int    MAX_PER_SYMBOL  = 1;     // max 1 signal per symbol per day
    static final int    MAX_DAILY_TOTAL = 3;     // max 3 signals per day across all symbols

    // ── Symbol targets: [target pts, SL pts] ──────────────────────────────────
    static final Map<String, double[]> TARGETS = Map.of(
        "NIFTY50",   new double[]{50.0, 22.0},   // R:R ≈ 2.3:1
        "BANKNIFTY", new double[]{150.0, 55.0},  // R:R ≈ 2.7:1
        "SENSEX",    new double[]{160.0, 60.0}   // R:R ≈ 2.7:1
    );

    // ── Data model ─────────────────────────────────────────────────────────────
    static class Candle {
        String symbol;
        double close, open, high, low;
        LocalDateTime ts;
    }

    static class Signal {
        String    symbol, result;
        Direction direction;
        double    entry, target, sl, pts, confidence;
        LocalDateTime ts;
        String    reason;
    }

    static class DaySummary {
        LocalDate date;
        int calls, wins, losses;
        double netPts;
        SessionBias bias;
        List<Signal> signals = new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {

        System.out.println("\n╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  📊 30-DAY AUDIT — v4 Strategy (Opening Range Breakout + Confirm)║");
        System.out.println("║  Data: Cached 5-min real historical data                         ║");
        System.out.println("║  Key change: ORB breakout entry (early, not late-stage trend)    ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝\n");

        ObjectMapper mapper  = new ObjectMapper();
        String[]    symbols  = {"NIFTY50", "BANKNIFTY", "SENSEX"};

        LocalDate cutoff   = LocalDate.of(2026, 4, 2);
        LocalDate fromDate = cutoff.minusDays(44);

        Map<LocalDate, DaySummary> combinedByDay = new TreeMap<>();
        List<Signal>               allSignals    = new ArrayList<>();
        Map<String, int[]>         symStats      = new LinkedHashMap<>();
        for (String s : symbols) symStats.put(s, new int[3]);   // [calls, wins, losses]

        // Track session biases per day (across all symbols — same day = same market)
        Map<LocalDate, SessionBias>  dayBias  = new TreeMap<>();
        Map<LocalDate, Integer>      dayCalls = new TreeMap<>();  // total calls per day

        // ── Process each symbol ───────────────────────────────────────────────
        for (String symbol : symbols) {

            File f = new File("market_data_cache/" + symbol + "_120days.json");
            if (!f.exists()) { System.out.println("⚠️  " + symbol + ": cache not found."); continue; }

            JsonNode arr = mapper.readTree(f);
            List<Candle> all = new ArrayList<>();

            for (JsonNode n : arr) {
                JsonNode ts = n.path("timestamp");
                if (!ts.isArray() || ts.size() < 5) continue;
                LocalDateTime ldt = LocalDateTime.of(
                    ts.get(0).asInt(), ts.get(1).asInt(), ts.get(2).asInt(),
                    ts.get(3).asInt(), ts.get(4).asInt());

                Candle c = new Candle();
                c.symbol = symbol;
                c.close  = n.path("price").asDouble();
                c.open   = n.path("open").asDouble();
                c.high   = n.path("high").asDouble(c.close + 5);
                c.low    = n.path("low").asDouble(c.close - 5);
                c.ts     = ldt;
                all.add(c);
            }

            all.sort(Comparator.comparing(c -> c.ts));

            // Extended window (need warm-up candles for EMA50)
            List<Candle> window = all.stream()
                .filter(c -> !c.ts.toLocalDate().isBefore(fromDate.minusDays(20)))
                .filter(c -> !c.ts.toLocalDate().isAfter(cutoff))
                .filter(c -> { DayOfWeek d = c.ts.getDayOfWeek();
                    return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY; })
                .collect(Collectors.toList());

            if (window.size() < 100) { System.out.println("⚠️  " + symbol + ": insufficient data."); continue; }

            // Group by day (only within target range)
            Map<LocalDate, List<Candle>> byDay = window.stream()
                .filter(c -> !c.ts.toLocalDate().isBefore(fromDate))
                .collect(Collectors.groupingBy(c -> c.ts.toLocalDate(), TreeMap::new, Collectors.toList()));

            // ── Simulate strategy day by day ──────────────────────────────────
            for (Map.Entry<LocalDate, List<Candle>> dayEntry : byDay.entrySet()) {
                LocalDate    day  = dayEntry.getKey();
                List<Candle> dayC = dayEntry.getValue();
                dayC.sort(Comparator.comparing(c -> c.ts));

                // ── Calculate session opening bias (9:15–9:44) ───────────────
                List<Candle> opening = dayC.stream()
                    .filter(c -> c.ts.getHour() == 9 && c.ts.getMinute() <= 44)
                    .limit(6)
                    .collect(Collectors.toList());

                SessionBias bias;
                double orbHigh = 0, orbLow = 0;

                if (opening.size() >= 4) {
                    double[] oCloses   = opening.stream().mapToDouble(c -> c.close).toArray();
                    double[] oHighs    = opening.stream().mapToDouble(c -> c.high).toArray();
                    double[] oLows     = opening.stream().mapToDouble(c -> c.low).toArray();
                    double   firstOpen = opening.get(0).open > 0 ? opening.get(0).open : opening.get(0).close;

                    bias = dayBias.computeIfAbsent(day, d ->
                        OptionsSignalEngine.calcSessionBias(oCloses, firstOpen));

                    // Calculate Opening Range (ORB) high and low
                    double[] orb = OptionsSignalEngine.calcOpeningRange(oHighs, oLows);
                    orbHigh = orb[0];
                    orbLow  = orb[1];
                } else {
                    bias = SessionBias.NEUTRAL;
                    dayBias.putIfAbsent(day, SessionBias.NEUTRAL);
                }

                int     symbolCalls = 0;
                long    cooldown    = 0;

                for (int i = 0; i < dayC.size(); i++) {
                    Candle cur = dayC.get(i);

                    // Per-symbol limit
                    if (symbolCalls >= MAX_PER_SYMBOL) break;

                    // Per-day total limit (BEARISH sessions capped at 1 to prevent
                    // correlated losses when a "false breakdown" occurs on all 3 symbols)
                    int maxToday  = (bias == SessionBias.BEARISH) ? 1 : MAX_DAILY_TOTAL;
                    int totalToday = dayCalls.getOrDefault(day, 0);
                    if (totalToday >= maxToday) break;

                    // Cooldown
                    long epoch = cur.ts.toEpochSecond(ZoneOffset.ofHoursMinutes(5, 30));
                    if (cooldown > 0 && epoch - cooldown < COOLDOWN_MIN * 60L) continue;

                    // Build 62-candle lookback window from global list
                    int globalIdx = window.indexOf(cur);
                    if (globalIdx < 64) continue;

                    List<Candle> win = window.subList(Math.max(0, globalIdx - 63), globalIdx + 1);
                    if (win.size() < 62) continue;

                    double[] closes = win.stream().mapToDouble(c -> c.close).toArray();
                    double[] highs  = win.stream().mapToDouble(c -> c.high).toArray();
                    double[] lows   = win.stream().mapToDouble(c -> c.low).toArray();

                    // Calculate session VWAP from today's candles up to this bar
                    List<Candle> todaySoFar = dayC.subList(0, i + 1);
                    double[] vCloses = todaySoFar.stream().mapToDouble(c -> c.close).toArray();
                    double[] vHighs  = todaySoFar.stream().mapToDouble(c -> c.high).toArray();
                    double[] vLows   = todaySoFar.stream().mapToDouble(c -> c.low).toArray();
                    double vwap = OptionsSignalEngine.calcVWAP(vCloses, vHighs, vLows);

                    int h = cur.ts.getHour();
                    int m = cur.ts.getMinute();

                    // Run the ORB strategy engine (v4)
                    SignalResult sig = OptionsSignalEngine.analyze(closes, highs, lows, h, m,
                                                                    bias, vwap, orbHigh, orbLow);

                    if (!sig.hasSignal()) continue;

                    // ── Verify outcome ────────────────────────────────────────
                    double[] tgtSl = TARGETS.getOrDefault(symbol, new double[]{50.0, 22.0});
                    double tgt = tgtSl[0], sl = tgtSl[1];
                    double tgtPrice = sig.direction == Direction.CALL ? cur.close + tgt : cur.close - tgt;
                    double slPrice  = sig.direction == Direction.CALL ? cur.close - sl  : cur.close + sl;

                    String result = "OPEN";
                    double pts    = 0;

                    for (int j = i + 1; j < Math.min(i + 36, dayC.size()); j++) {
                        Candle fut = dayC.get(j);
                        if (sig.direction == Direction.CALL) {
                            if (fut.low  <= slPrice)  { result = "LOSS"; pts = -sl;  break; }
                            if (fut.high >= tgtPrice) { result = "WIN";  pts = +tgt; break; }
                        } else {
                            if (fut.high >= slPrice)  { result = "LOSS"; pts = -sl;  break; }
                            if (fut.low  <= tgtPrice) { result = "WIN";  pts = +tgt; break; }
                        }
                    }

                    // ── Record ────────────────────────────────────────────────
                    Signal s = new Signal();
                    s.symbol     = symbol;  s.direction  = sig.direction;
                    s.entry      = cur.close; s.target   = tgt;  s.sl  = sl;
                    s.result     = result;   s.pts        = pts;
                    s.ts         = cur.ts;   s.confidence = sig.confidence;
                    s.reason     = sig.reason;
                    allSignals.add(s);

                    DaySummary ds = combinedByDay.computeIfAbsent(day, k -> {
                        DaySummary d = new DaySummary(); d.date = k; d.bias = bias; return d;
                    });
                    ds.calls++;  ds.signals.add(s);  ds.netPts += pts;
                    if ("WIN".equals(result))  { ds.wins++;   symStats.get(symbol)[1]++; }
                    else if ("LOSS".equals(result)) { ds.losses++; symStats.get(symbol)[2]++; }
                    symStats.get(symbol)[0]++;

                    dayCalls.merge(day, 1, Integer::sum);
                    symbolCalls++;
                    cooldown = epoch;
                }
            }
        }

        // ── Trim to last 30 trading days ──────────────────────────────────────
        List<LocalDate> tradingDays = new ArrayList<>(combinedByDay.keySet());
        if (tradingDays.size() > 30) {
            List<LocalDate> trimmed = tradingDays.subList(tradingDays.size() - 30, tradingDays.size());
            Map<LocalDate, DaySummary> tm  = new TreeMap<>();
            Map<String, int[]>         ts2 = new LinkedHashMap<>();
            for (String s : symbols) ts2.put(s, new int[3]);
            for (LocalDate d : trimmed) {
                DaySummary ds = combinedByDay.get(d);
                if (ds == null) continue;
                tm.put(d, ds);
                for (Signal sig : ds.signals) {
                    int[] ss = ts2.get(sig.symbol);
                    if (ss == null) continue;
                    ss[0]++;
                    if ("WIN".equals(sig.result))  ss[1]++;
                    else if ("LOSS".equals(sig.result)) ss[2]++;
                }
            }
            combinedByDay = tm;
            symStats      = ts2;
            tradingDays   = trimmed;
        }

        int    totalCalls = 0, totalWins = 0, totalLosses = 0, totalOpen = 0;
        double totalNet   = 0;
        for (DaySummary ds : combinedByDay.values()) {
            totalCalls  += ds.calls;
            totalWins   += ds.wins;
            totalLosses += ds.losses;
            totalOpen   += ds.calls - ds.wins - ds.losses;
            totalNet    += ds.netPts;
        }

        int    dCount     = tradingDays.size();
        double winRate    = totalCalls > 0 ? (double) totalWins / totalCalls * 100 : 0;
        double avgPerDay  = dCount > 0 ? (double) totalCalls / dCount : 0;

        // Count session bias breakdown
        final Map<LocalDate, DaySummary> finalCombined = combinedByDay;
        final Map<LocalDate, SessionBias> finalDayBias  = dayBias;
        long bullDays = finalCombined.values().stream().filter(d -> d.bias == SessionBias.BULLISH).count();
        long bearDays = finalCombined.values().stream().filter(d -> d.bias == SessionBias.BEARISH).count();
        long noTradeDays = tradingDays.stream()
            .filter(d -> !finalCombined.containsKey(d) ||
                (finalDayBias.containsKey(d) && finalDayBias.get(d) == SessionBias.NEUTRAL))
            .count();

        // ── Print results ─────────────────────────────────────────────────────
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║           📈 30-DAY AGGREGATE SUMMARY (v4 — ORB Strategy)           ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Period         : %-53s║%n",
            tradingDays.isEmpty() ? "N/A" : tradingDays.get(0) + "  →  " + tradingDays.get(tradingDays.size()-1));
        System.out.printf( "║  Total Days     : %-53s║%n", dCount + " trading days");
        System.out.printf( "║  Session Bias   : %-53s║%n",
            bullDays + " bullish | " + bearDays + " bearish | " + noTradeDays + " neutral (no trade)");
        System.out.printf( "║  Total Signals  : %-53s║%n", totalCalls + " signals  (v1=53, v2=106)");
        System.out.printf( "║  Signals/Day    : %-53s║%n", String.format("%.1f avg per trading day", avgPerDay));
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  ✅ Wins         : %-53s║%n",
            totalWins + String.format("  (%.1f%% win rate)", winRate));
        System.out.printf( "║  ❌ Losses       : %-53s║%n", totalLosses);
        System.out.printf( "║  ⏳ Open         : %-53s║%n", totalOpen);
        System.out.printf( "║  💰 Net Points   : %-53s║%n",
            String.format("%+.1f pts  (%s)", totalNet, totalNet >= 0 ? "PROFITABLE ✅" : "LOSS ❌"));
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");

        // Per-symbol
        System.out.println("\n┌──────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                   📊 PER-SYMBOL BREAKDOWN                               │");
        System.out.println("├──────────────┬───────┬───────┬────────┬──────────┬──────────────────────┤");
        System.out.println("│ Symbol       │ Calls │  Wins │ Losses │ Win Rate │  Net Pts             │");
        System.out.println("├──────────────┼───────┼───────┼────────┼──────────┼──────────────────────┤");
        for (String sym : symbols) {
            int[] ss = symStats.get(sym);
            if (ss == null || ss[0] == 0) continue;
            double wr  = (double) ss[1] / ss[0] * 100;
            double[] tgt = TARGETS.getOrDefault(sym, new double[]{50, 22});
            double net = ss[1] * tgt[0] - ss[2] * tgt[1];
            System.out.printf("│ %-12s │ %5d │ %5d │ %6d │ %7.1f%% │ %+18.1f pts  │%n",
                sym, ss[0], ss[1], ss[2], wr, net);
        }
        System.out.println("└──────────────┴───────┴───────┴────────┴──────────┴──────────────────────┘");

        // Day-by-day
        System.out.println("\n┌────────────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                    📅 DAY-BY-DAY PERFORMANCE (v3)                                  │");
        System.out.println("├────────────┬────────┬───────┬───────┬────────┬──────────┬──────────────────────────┤");
        System.out.println("│ Date       │ Bias   │ Calls │  Wins │ Losses │ Net Pts  │ Detail                   │");
        System.out.println("├────────────┼────────┼───────┼───────┼────────┼──────────┼──────────────────────────┤");
        for (Map.Entry<LocalDate, DaySummary> e : combinedByDay.entrySet()) {
            DaySummary ds = e.getValue();
            String bStr = ds.bias == SessionBias.BULLISH ? "BULL  " :
                          ds.bias == SessionBias.BEARISH  ? "BEAR  " : "NEUT  ";
            String detail = ds.signals.stream()
                .map(s -> s.symbol.replace("NIFTY50","NF").replace("BANKNIFTY","BNF").replace("SENSEX","SX")
                    + (s.direction == Direction.CALL ? "▲" : "▼")
                    + ("WIN".equals(s.result) ? "✅" : "LOSS".equals(s.result) ? "❌" : "⏳"))
                .collect(Collectors.joining(" "));
            System.out.printf("│ %s │ %s │ %5d │ %5d │ %6d │ %+8.1f │ %-24s │%n",
                e.getKey().format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                bStr, ds.calls, ds.wins, ds.losses, ds.netPts,
                detail.length() > 24 ? detail.substring(0, 21) + "..." : detail);
        }
        System.out.println("├────────────┴────────┴───────┴───────┴────────┴──────────┴──────────────────────────┤");
        System.out.printf("│  TOTAL: %d signals | %d wins (%.1f%%) | %d losses | Net: %+.1f pts%-21s│%n",
            totalCalls, totalWins, winRate, totalLosses, totalNet, "");
        System.out.println("└────────────────────────────────────────────────────────────────────────────────────┘");

        // Key insights
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    💡 KEY INSIGHTS (v3 vs v1)                       ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");

        combinedByDay.entrySet().stream()
            .max(Comparator.comparingDouble(e -> e.getValue().netPts))
            .ifPresent(bd -> System.out.printf("║  Best Day     : %s  %+.1f pts  (%d signals)%-15s║%n",
                bd.getKey(), bd.getValue().netPts, bd.getValue().calls, ""));
        combinedByDay.entrySet().stream()
            .min(Comparator.comparingDouble(e -> e.getValue().netPts))
            .ifPresent(wd -> System.out.printf("║  Worst Day    : %s  %+.1f pts  (%d signals)%-15s║%n",
                wd.getKey(), wd.getValue().netPts, wd.getValue().calls, ""));

        int maxStreak = 0, streak = 0;
        for (Signal s : allSignals) {
            if ("WIN".equals(s.result)) { streak++; maxStreak = Math.max(maxStreak, streak); }
            else streak = 0;
        }
        System.out.printf("║  Max Win Streak: %-53s║%n", maxStreak + " consecutive wins");
        System.out.printf("║  No-Trade Days : %-53s║%n",
            noTradeDays + " neutral-open days skipped (capital protected)");

        double grossWin  = allSignals.stream().filter(s -> "WIN".equals(s.result)).mapToDouble(s -> s.pts).sum();
        double grossLoss = Math.abs(allSignals.stream().filter(s -> "LOSS".equals(s.result)).mapToDouble(s -> s.pts).sum());
        double pf = grossLoss > 0 ? grossWin / grossLoss : (grossWin > 0 ? 999 : 0);
        System.out.printf("║  Profit Factor  : %-53s║%n",
            String.format("%.2f  (v1=0.83, v2=0.88 — target >1.5)", pf));

        double avgWin  = totalWins   > 0 ? grossWin  / totalWins   : 0;
        double avgLoss = totalLosses > 0 ? grossLoss / totalLosses : 0;
        System.out.printf("║  Avg Win        : +%.1f pts | Avg Loss: -%.1f pts%-27s║%n", avgWin, avgLoss, "");
        double rr = avgLoss > 0 ? avgWin / avgLoss : 0;
        System.out.printf("║  Risk:Reward    : 1 : %.2f%-50s║%n", rr, "");

        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  📊 VERSION COMPARISON                                               ║");
        System.out.printf( "║  v1 (old)  : 53 signals, 20.8%% WR, -225 pts, no session filter     ║%n");
        System.out.printf( "║  v2        : 106 signals, 24.5%% WR, -424 pts, EMA+RSI+MACD only    ║%n");
        System.out.printf( "║  v3 (this) : %-3d signals, %.1f%% WR, %+.0f pts, +Session+VWAP filter%-9s║%n",
            totalCalls, winRate, totalNet, "");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");

        String verdict;
        if      (winRate >= 65 && totalNet > 0)  verdict = "🟢 TARGET ACHIEVED — 65%+ WR, profitable!";
        else if (winRate >= 55 && totalNet > 0)  verdict = "🟡 GOOD (55%+ WR profitable) — fine-tune further";
        else if (winRate >= 50)                  verdict = "🟡 AT BREAKEVEN ZONE — adjust RSI/ADX thresholds";
        else if (totalNet > 0)                   verdict = "🟠 PROFITABLE but WR low — R:R carrying it";
        else                                     verdict = "🔴 BELOW TARGET — review session bias logic";
        System.out.printf( "║  Verdict      : %-53s║%n", verdict);
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝\n");

        // Sample signals
        if (totalCalls > 0) {
            System.out.println("── Sample Signals ────────────────────────────────────────────────────────");
            allSignals.stream().limit(8).forEach(s -> System.out.printf(
                "  %s %s%s | %s | Conf:%.0f%% | Entry:%.0f | Tgt:%.0f SL:%.0f | %s%n",
                s.ts.toLocalDate(), s.symbol,
                s.direction == Direction.CALL ? "▲CE" : "▼PE",
                "WIN".equals(s.result) ? "WIN ✅" : "LOSS❌",
                s.confidence, s.entry, s.target, s.sl,
                s.reason.length() > 55 ? s.reason.substring(0, 52) + "..." : s.reason));
        }
    }
}
