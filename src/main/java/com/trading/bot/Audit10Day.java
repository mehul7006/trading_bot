package com.trading.bot;

import com.trading.bot.agents.AuditAgent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Honest 10-trading-day audit, live-bot-matched.
 *
 * Uses AuditAgent.runAuditDetailed (same PredictionAgent + live gates + cooldown +
 * outcome verification as the deployed bot) on real Upstox 5-min candles.
 * Prints a prediction-wise list plus accuracy / win-rate / net-point summary.
 */
public class Audit10Day {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static void main(String[] args) {
        int tradingDays = (args.length > 0) ? Integer.parseInt(args[0].trim()) : 10;
        int fetchDays   = Math.max(45, tradingDays * 3 + 20); // ensure full 200-candle warm-up before window

        System.out.println("=======================================================================");
        System.out.println("  HONEST " + tradingDays + "-TRADING-DAY AUDIT  (live-bot-matched logic)");
        System.out.println("  Data: real Upstox 1-min -> 5-min candles | gates = live thresholds");
        System.out.println("  Live constraints applied: 2-min cooldown + max 3 calls/symbol/day");
        System.out.println("=======================================================================");

        AuditAgent agent = new AuditAgent();
        String[] symbols = {"NIFTY50", "BANKNIFTY", "SENSEX"};

        List<AuditAgent.CallRecord> allCalls = new ArrayList<>();
        int gFull = 0, gPartial = 0, gLoss = 0, gTrades = 0;
        double gNet = 0;

        for (String symbol : symbols) {
            AuditAgent.AuditResult r = agent.runAuditDetailed(symbol, fetchDays, tradingDays);

            System.out.println();
            System.out.println("-----------------------------------------------------------------------");
            System.out.println(" " + symbol + " — last " + tradingDays + " trading days");
            System.out.println("-----------------------------------------------------------------------");

            if (r.calls.isEmpty()) {
                System.out.println("   (no calls passed the live gates in this window)");
            } else {
                System.out.printf("   %-16s %-5s %9s %7s %5s %5s %5s %9s %-9s %s%n",
                        "TIME", "DIR", "ENTRY", "TARGET", "SL", "RSI", "ADX", "PTS", "OUTCOME", "MODEL");
                for (AuditAgent.CallRecord c : r.calls) {
                    System.out.printf("   %-16s %-5s %9.2f %7.0f %5.0f %5.0f %5.0f %+9.1f %-9s %s%n",
                            c.time.format(FMT), c.direction, c.entry, c.target, c.stopLoss,
                            c.rsi, c.adx, c.points, c.outcome, c.model);
                    allCalls.add(c);
                }
            }

            double acc = r.totalTrades > 0 ? (double) r.fullWins / r.totalTrades * 100 : 0;
            System.out.println("   .................................................................");
            System.out.printf("   Calls: %d | Full: %d | Partial: %d | Loss: %d%n",
                    r.totalTrades, r.fullWins, r.partialWins, r.losses);
            System.out.printf("   Win rate (full+partial): %.1f%%  | Strict accuracy (full only): %.1f%%%n",
                    r.winRate, acc);
            System.out.printf("   Net points: %+.1f | Trading days w/ signals: %d | Calls/day: %.2f%n",
                    r.netPoints, r.tradingDays,
                    r.tradingDays > 0 ? (double) r.totalTrades / r.tradingDays : 0.0);

            gFull += r.fullWins; gPartial += r.partialWins; gLoss += r.losses;
            gTrades += r.totalTrades; gNet += r.netPoints;
        }

        double overallWR  = gTrades > 0 ? (double) (gFull + gPartial) / gTrades * 100 : 0;
        double overallAcc = gTrades > 0 ? (double) gFull / gTrades * 100 : 0;

        System.out.println();
        System.out.println("=======================================================================");
        System.out.println("  OVERALL — all 3 indices — last " + tradingDays + " trading days");
        System.out.println("=======================================================================");
        System.out.printf("   Total calls        : %d%n", gTrades);
        System.out.printf("   Full wins          : %d%n", gFull);
        System.out.printf("   Partial wins       : %d%n", gPartial);
        System.out.printf("   Losses (SL hit)    : %d%n", gLoss);
        System.out.printf("   WIN RATE (W+P)     : %.1f%%%n", overallWR);
        System.out.printf("   ACCURACY (full)    : %.1f%%%n", overallAcc);
        System.out.printf("   TOTAL NET POINTS   : %+.1f pts%n", gNet);
        System.out.println("=======================================================================");

        // ── Per-model breakdown (which sub-strategy carries / drags the WR) ──
        java.util.Map<String, int[]> byModel = new java.util.TreeMap<>(); // [calls, wins(full+partial), full, loss]
        java.util.Map<String, Double> ptsByModel = new java.util.TreeMap<>();
        for (AuditAgent.CallRecord c : allCalls) {
            int[] a = byModel.computeIfAbsent(c.model, k -> new int[4]);
            a[0]++;
            if (!"LOSS".equals(c.outcome)) a[1]++;
            if ("FULL WIN".equals(c.outcome)) a[2]++;
            if ("LOSS".equals(c.outcome)) a[3]++;
            ptsByModel.merge(c.model, c.points, Double::sum);
        }
        System.out.println("  Per-model breakdown (model = sub-strategy that fired):");
        System.out.printf("   %-16s %6s %6s %6s %6s %8s %8s%n", "MODEL", "CALLS", "W+P", "FULL", "LOSS", "WR%", "NETPTS");
        for (var e : byModel.entrySet()) {
            int[] a = e.getValue();
            double wr = a[0] > 0 ? (double) a[1] / a[0] * 100 : 0;
            System.out.printf("   %-16s %6d %6d %6d %6d %7.1f%% %+8.1f%n",
                    e.getKey(), a[0], a[1], a[2], a[3], wr, ptsByModel.get(e.getKey()));
        }
        System.out.println("=======================================================================");
        System.out.println("  Honesty notes:");
        System.out.println("   - Outcome from REAL forward candles (target vs SL, 24x5min lookahead).");
        System.out.println("   - Partial = +30% of target; Loss = -full SL. Same math as live audit.");
        System.out.println("   - Warm-up candles before the window are NOT scored (no look-ahead).");
        System.out.println("=======================================================================");
    }
}
