package com.trading.bot.test;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.OptionData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 120-Day Honest Audit of the CURRENT Bot Implementation (5-Minute Timeframe)
 * Matches Live Bot Logic: 
 * - 5-Minute Resampled Data
 * - >80% Confidence
 * - Minimum Point Thresholds (Nifty 30, BankNifty 60, Sensex 100)
 * - 15 Minute Cooldown
 */
import com.trading.bot.agents.AuditAgent;
import java.util.stream.Collectors;
import java.util.Arrays;

public class OneTwentyDayHonestAudit {

    public static void main(String[] args) {
        System.out.println("🔥 STARTING AGENT-BASED 120-DAY HONEST AUDIT 🔥");
        System.out.println("--------------------------------------------------------------------------------------");
        
        AuditAgent auditAgent = new AuditAgent();
        String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};
        
        // Parallel execution for speed
        List<AuditAgent.AuditResult> results = Arrays.stream(symbols)
            .parallel()
            .map(symbol -> {
                System.out.println("🚀 Agent starting audit for: " + symbol);
                return auditAgent.runAudit(symbol, 120);
            })
            .collect(Collectors.toList());
        
        auditAgent.printFinalReport(results);
    }
}
