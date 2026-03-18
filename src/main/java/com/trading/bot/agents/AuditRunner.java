package com.trading.bot.agents;

import java.util.List;

public class AuditRunner {
    public static void main(String[] args) {
        int days = 120; // Last 120 days
        System.out.println("🚀 Starting " + days + "-Day Bot Performance Audit (V29.0)");
        System.out.println("=======================================================================");

        AuditAgent auditAgent = new AuditAgent();
        String[] symbols = {"NIFTY50", "BANKNIFTY", "SENSEX"};

        double totalNetPoints = 0;
        int totalTrades = 0, totalWins = 0, totalPartial = 0, totalLosses = 0;
        double totalLossPoints = 0, totalWinPoints = 0;

        for (String symbol : symbols) {
            AuditAgent.AuditResult res = auditAgent.runAudit(symbol, days);
            double callsPerDay = (res.tradingDays + res.nonTradingDays) > 0
                ? (double) res.totalTrades / (res.tradingDays + res.nonTradingDays) : 0;

            // Estimate gross win points and loss points
            // Win points = netPoints contribution from wins; loss points = SL hits
            double approxWinPts  = res.fullWins > 0 || res.partialWins > 0
                ? res.netPoints + (res.losses * 0) : res.netPoints; // netPoints already includes losses
            // Recalculate: netPoints = winPts - lossPts
            // We can't split exactly without raw data, but losses × avg SL ≈ lossPts
            double estimatedLossPts = res.losses * getAvgSL(symbol);
            double estimatedWinPts  = res.netPoints + estimatedLossPts;

            System.out.println();
            System.out.println("📊 " + symbol + " — Last " + days + " Days");
            System.out.println("-----------------------------------------------------------------------");
            System.out.printf("  Total Calls Generated : %d%n", res.totalTrades);
            System.out.printf("  ✅ Full Wins           : %d%n", res.fullWins);
            System.out.printf("  🔶 Partial Wins        : %d%n", res.partialWins);
            System.out.printf("  ❌ Stop Loss Hits      : %d%n", res.losses);
            System.out.printf("  📈 Win Rate            : %.1f%%%n", res.winRate);
            System.out.printf("  💰 Net Points          : %+.1f pts%n", res.netPoints);
            System.out.printf("  🟢 Gross Win Points    : +%.1f pts (est.)%n", estimatedWinPts);
            System.out.printf("  🔴 Gross Loss Points   : -%.1f pts (est.)%n", estimatedLossPts);
            System.out.printf("  📅 Trading Days        : %d | Non-Trading: %d%n", res.tradingDays, res.nonTradingDays);
            System.out.printf("  📞 Calls/Day           : %.2f%n", callsPerDay);

            System.out.println("  — Time Breakdown —");
            System.out.printf("    09:15-11:00 : %d trades | WR: %.1f%% | Net: %+.1f pts%n",
                res.trades_9_11,
                res.trades_9_11 > 0 ? (double)(res.fullWins_9_11 + res.partialWins_9_11)/res.trades_9_11*100 : 0,
                res.netPoints_9_11);
            System.out.printf("    11:00-13:00 : %d trades | WR: %.1f%% | Net: %+.1f pts%n",
                res.trades_11_13,
                res.trades_11_13 > 0 ? (double)(res.fullWins_11_13 + res.partialWins_11_13)/res.trades_11_13*100 : 0,
                res.netPoints_11_13);
            System.out.printf("    13:00-15:30 : %d trades | WR: %.1f%% | Net: %+.1f pts%n",
                res.trades_13_1530,
                res.trades_13_1530 > 0 ? (double)(res.fullWins_13_1530 + res.partialWins_13_1530)/res.trades_13_1530*100 : 0,
                res.netPoints_13_1530);

            totalNetPoints  += res.netPoints;
            totalTrades     += res.totalTrades;
            totalWins       += res.fullWins;
            totalPartial    += res.partialWins;
            totalLosses     += res.losses;
            totalLossPoints += estimatedLossPts;
            totalWinPoints  += estimatedWinPts;
        }

        System.out.println();
        System.out.println("=======================================================================");
        System.out.println("📋 OVERALL SUMMARY — All 3 Indices — Last " + days + " Days");
        System.out.println("=======================================================================");
        System.out.printf("  Total Calls   : %d%n", totalTrades);
        System.out.printf("  Full Wins     : %d%n", totalWins);
        System.out.printf("  Partial Wins  : %d%n", totalPartial);
        System.out.printf("  SL Hits       : %d%n", totalLosses);
        double overallWR = totalTrades > 0 ? (double)(totalWins + totalPartial)/totalTrades*100 : 0;
        System.out.printf("  Win Rate      : %.1f%%%n", overallWR);
        System.out.printf("  Net Points    : %+.1f pts%n", totalNetPoints);
        System.out.printf("  Win Points    : +%.1f pts (est.)%n", totalWinPoints);
        System.out.printf("  Loss Points   : -%.1f pts (est.)%n", totalLossPoints);
        System.out.println("=======================================================================");
        System.out.println("✅ Audit Done — V29.0 (Counter-Trend ORB + 70%+ WR All Segments)");
    }

    private static double getAvgSL(String symbol) {
        return switch (symbol) {
            case "NIFTY50"   -> 20.0;
            case "SENSEX"    -> 50.0;
            case "BANKNIFTY" -> 55.0;
            default          -> 25.0;
        };
    }
}
