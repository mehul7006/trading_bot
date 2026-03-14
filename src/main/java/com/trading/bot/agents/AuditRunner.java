package com.trading.bot.agents;

import java.util.List;

public class AuditRunner {
    public static void main(String[] args) {
        System.out.println("🚀 Starting 10-Day Institutional Audit (V24.0 Smart Money Mode)");
        System.out.println("--------------------------------------------------");
        
        AuditAgent auditAgent = new AuditAgent();
        String[] symbols = {"NIFTY50", "BANKNIFTY", "SENSEX"};
        
        System.out.println("| Symbol     | Trades | Wins  | PartWins | Losses | Win Rate | Net Points | Calls/Day |");
        System.out.println("|------------|--------|-------|----------|--------|----------|------------|-----------|");
        
        for (String symbol : symbols) {
            AuditAgent.AuditResult res = auditAgent.runAudit(symbol, 10);
            double callsPerDay = res.totalTrades > 0 ? (double)res.totalTrades / (res.tradingDays + res.nonTradingDays) : 0;
            
            System.out.printf("| %-10s | %6d | %5d | %8d | %6d | %8.1f%% | %10.1f | %9.2f |%n",
                symbol, res.totalTrades, res.fullWins, res.partialWins, res.losses, res.winRate, res.netPoints, callsPerDay);
        }
        
        System.out.println("--------------------------------------------------");
        System.out.println("✅ Audit Completed. Logic: V24.0 (Institutional Smart Money + Technical Confluence)");
    }
}
