package com.trading.bot;
import com.trading.bot.agents.AuditAgent;
import java.util.List;
import java.util.ArrayList;
public class RunAudit30 {
    public static void main(String[] args) {
        String[] symbols = {"NIFTY50", "BANKNIFTY", "SENSEX"};
        List<AuditAgent.AuditResult> results = new ArrayList<>();
        AuditAgent agent = new AuditAgent();
        int days = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        System.out.println("Running " + days + "-day audit (V27 High Win-Rate Strategy)...\n");
        for (String sym : symbols) {
            System.out.println("Auditing " + sym + " ...");
            results.add(agent.runAudit(sym, days));
        }
        int totalTrades=0,totalWins=0,totalPartial=0,totalLoss=0; double totalNet=0;
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║  " + days + "-DAY BACKTEST — V27 High Win-Rate Strategy");
        System.out.println("╠════════════╦═══════╦════════╦═════════╦══════════╦═══════════════╣");
        System.out.println("║ Symbol     ║Trades ║  Wins  ║ Partial ║ Win Rate ║   Net Points  ║");
        System.out.println("╠════════════╬═══════╬════════╬═════════╬══════════╬═══════════════╣");
        for (AuditAgent.AuditResult r : results) {
            System.out.printf("║ %-10s ║  %3d  ║  %3d   ║   %3d   ║  %5.1f%%  ║  %+10.1f   ║%n",
                r.symbol, r.totalTrades, r.fullWins, r.partialWins, r.winRate, r.netPoints);
            totalTrades+=r.totalTrades; totalWins+=r.fullWins; totalPartial+=r.partialWins; totalLoss+=r.losses; totalNet+=r.netPoints;
        }
        double cwr = totalTrades>0?(double)(totalWins+totalPartial)/totalTrades*100:0;
        System.out.println("╠════════════╬═══════╬════════╬═════════╬══════════╬═══════════════╣");
        System.out.printf("║ COMBINED   ║  %3d  ║  %3d   ║   %3d   ║  %5.1f%%  ║  %+10.1f   ║%n",
            totalTrades, totalWins, totalPartial, cwr, totalNet);
        System.out.println("╚════════════╩═══════╩════════╩═════════╩══════════╩═══════════════╝");
        System.out.println("\nPer-symbol detail:");
        for (AuditAgent.AuditResult r : results) {
            System.out.printf("%n  %s: %d trades | WR=%.1f%% | Net=%+.1f | Days=%d%n",
                r.symbol, r.totalTrades, r.winRate, r.netPoints, r.tradingDays);
            System.out.printf("    9:15-11: %d t / %.0f%% WR / %+.1f pts%n",
                r.trades_9_11, r.trades_9_11>0?(double)(r.fullWins_9_11+r.partialWins_9_11)/r.trades_9_11*100:0, r.netPoints_9_11);
            System.out.printf("    11-13:   %d t / %.0f%% WR / %+.1f pts%n",
                r.trades_11_13, r.trades_11_13>0?(double)(r.fullWins_11_13+r.partialWins_11_13)/r.trades_11_13*100:0, r.netPoints_11_13);
            System.out.printf("    13-15:30 %d t / %.0f%% WR / %+.1f pts%n",
                r.trades_13_1530, r.trades_13_1530>0?(double)(r.fullWins_13_1530+r.partialWins_13_1530)/r.trades_13_1530*100:0, r.netPoints_13_1530);
        }
    }
}
