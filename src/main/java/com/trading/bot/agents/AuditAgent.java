package com.trading.bot.agents;

import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.ai.AIPredictor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;

/**
 * AuditAgent: Orchestrates the audit process for symbols.
 * It uses MarketDataAgent and PredictionAgent to run honest backtests.
 */
public class AuditAgent implements Agent {
    private final MarketDataAgent marketDataAgent;
    private final PredictionAgent predictionAgent;

    public static class AuditResult {
        public String symbol;
        public int totalTrades;
        public int fullWins;
        public int partialWins;
        public int losses;
        public double winRate;
        public double netPoints;
        public int tradingDays;
        public int nonTradingDays;
        
        // Time Bifurcation
        public int trades_9_11, fullWins_9_11, partialWins_9_11, losses_9_11;
        public double netPoints_9_11;
        public int trades_11_13, fullWins_11_13, partialWins_11_13, losses_11_13;
        public double netPoints_11_13;
        public int trades_13_1530, fullWins_13_1530, partialWins_13_1530, losses_13_1530;
        public double netPoints_13_1530;

        public String toString() {
            return String.format("| %-10s | %5d | %5d | %7d | %5d | %7.1f%% | %8.1f |", 
                symbol, totalTrades, fullWins, partialWins, losses, winRate, netPoints);
        }
    }

    public AuditAgent() {
        this.marketDataAgent = new MarketDataAgent();
        this.predictionAgent = new PredictionAgent();
    }

    public AuditResult runAudit(String symbol, int days) {
        AuditResult res = new AuditResult();
        res.symbol = symbol;

        try {
            List<SimpleMarketData> data = marketDataAgent.getHistoricalData(symbol, days);
            if (data == null || data.size() < 200) return res;

            long lastSignalTime = 0;
            long fifteenMinMillis = 15 * 60 * 1000;
            Set<LocalDate> tradingDaysSet = new HashSet<>();
            Set<LocalDate> allDaysWithData = new HashSet<>();

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData latest = data.get(i);
                LocalDateTime currentTimestamp = latest.timestamp;
                allDaysWithData.add(currentTimestamp.toLocalDate());

                if (currentTimestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - lastSignalTime < fifteenMinMillis) continue;

                java.time.LocalTime time = currentTimestamp.toLocalTime();
                if (time.isBefore(java.time.LocalTime.of(9, 15)) || time.isAfter(java.time.LocalTime.of(15, 25))) continue;

                List<SimpleMarketData> history = data.subList(0, i + 1);
                AIPredictor.AIPrediction prediction = predictionAgent.generateSignal(symbol, history);

                boolean isSignificant = checkMinimumPoints(symbol, prediction.estimatedMovePoints);

                if (isSignificant && prediction.confidence >= 80) {
                    res.totalTrades++;
                    tradingDaysSet.add(currentTimestamp.toLocalDate());
                    lastSignalTime = currentTimestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

                    int outcome = verifyOutcome(data, i, prediction, 24);
                    int slab = getTimeSlab(time);

                    if (outcome == 2) {
                        res.fullWins++;
                        res.netPoints += prediction.estimatedMovePoints;
                        if (slab == 0) { res.fullWins_9_11++; res.trades_9_11++; res.netPoints_9_11 += prediction.estimatedMovePoints; }
                        else if (slab == 1) { res.fullWins_11_13++; res.trades_11_13++; res.netPoints_11_13 += prediction.estimatedMovePoints; }
                        else { res.fullWins_13_1530++; res.trades_13_1530++; res.netPoints_13_1530 += prediction.estimatedMovePoints; }
                    } else if (outcome == 1) {
                        res.partialWins++;
                        res.netPoints += (prediction.estimatedMovePoints * 0.3);
                        if (slab == 0) { res.partialWins_9_11++; res.trades_9_11++; res.netPoints_9_11 += (prediction.estimatedMovePoints * 0.3); }
                        else if (slab == 1) { res.partialWins_11_13++; res.trades_11_13++; res.netPoints_11_13 += (prediction.estimatedMovePoints * 0.3); }
                        else { res.partialWins_13_1530++; res.trades_13_1530++; res.netPoints_13_1530 += (prediction.estimatedMovePoints * 0.3); }
                    } else {
                        res.losses++;
                        res.netPoints -= prediction.suggestedStopLoss;
                        if (slab == 0) { res.losses_9_11++; res.trades_9_11++; res.netPoints_9_11 -= prediction.suggestedStopLoss; }
                        else if (slab == 1) { res.losses_11_13++; res.trades_11_13++; res.netPoints_11_13 -= prediction.suggestedStopLoss; }
                        else { res.losses_13_1530++; res.trades_13_1530++; res.netPoints_13_1530 -= prediction.suggestedStopLoss; }
                    }
                }
            }
            res.tradingDays = tradingDaysSet.size();
            res.nonTradingDays = allDaysWithData.size() - res.tradingDays;
            res.winRate = res.totalTrades > 0 ? ((double)(res.fullWins + res.partialWins) / res.totalTrades) * 100 : 0;
        } catch (Exception e) {
            System.err.println("❌ Audit error for " + symbol + ": " + e.getMessage());
        }
        return res;
    }

    private boolean checkMinimumPoints(String symbol, double estimatedPoints) {
        double minPoints = switch (symbol) {
            case "NIFTY50" -> 10.0;
            case "SENSEX" -> 40.0;
            case "BANKNIFTY" -> 25.0;
            default -> 5.0;
        };
        return estimatedPoints >= minPoints;
    }

    private int getTimeSlab(java.time.LocalTime time) {
        if (time.isBefore(java.time.LocalTime.of(11, 0))) return 0;
        if (time.isBefore(java.time.LocalTime.of(13, 0))) return 1;
        return 2;
    }

    private int verifyOutcome(List<SimpleMarketData> allData, int currentIndex, AIPredictor.AIPrediction prediction, int lookAhead) {
        SimpleMarketData entryCandle = allData.get(currentIndex);
        double entryPrice = entryCandle.price;
        String direction = prediction.predictedDirection;
        double target = prediction.estimatedMovePoints;
        double sl = prediction.suggestedStopLoss;
        
        double targetPrice = direction.equals("UP") ? entryPrice + target : entryPrice - target;
        double slPrice = direction.equals("UP") ? entryPrice - sl : entryPrice + sl;
        
        boolean reachedPartial = false;
        double partialTargetPrice = direction.equals("UP") ? entryPrice + (target * 0.5) : entryPrice - (target * 0.5);

        for (int j = 1; j <= lookAhead && (currentIndex + j) < allData.size(); j++) {
            SimpleMarketData future = allData.get(currentIndex + j);
            if (direction.equals("UP")) {
                if (future.low <= slPrice) return reachedPartial ? 1 : 0;
                if (future.high >= targetPrice) return 2;
                if (future.high >= partialTargetPrice) reachedPartial = true;
            } else {
                if (future.high >= slPrice) return reachedPartial ? 1 : 0;
                if (future.low <= targetPrice) return 2;
                if (future.low <= partialTargetPrice) reachedPartial = true;
            }
        }
        return reachedPartial ? 1 : 0;
    }

    public void printFinalReport(List<AuditResult> results) {
        System.out.println("\n\n🏆 AGENT-BASED 120-DAY AUDIT RESULTS 🏆");
        System.out.println("-------------------------------------------------------------------------");
        System.out.println("| Symbol     | Trades| Full  | Partial | Loss  | Win Rate | Net Pts  |");
        System.out.println("|------------|-------|-------|---------|-------|----------|----------|");
        
        for (AuditResult r : results) {
            System.out.println(r.toString());
            System.out.printf("  Activity  => Trading Days: %d | Non-Trading Days: %d | Ratio: %.1f calls/day%n", 
                r.tradingDays, r.nonTradingDays, (double)r.totalTrades / (r.tradingDays + r.nonTradingDays));
            
            System.out.println("  Time Bifurcation:");
            System.out.printf("    - 09:15-11:00: %d trades | Win Rate: %.1f%% | Net: %.1f%n", 
                r.trades_9_11, r.trades_9_11 > 0 ? (double)(r.fullWins_9_11 + r.partialWins_9_11)/r.trades_9_11*100 : 0, r.netPoints_9_11);
            System.out.printf("    - 11:00-13:00: %d trades | Win Rate: %.1f%% | Net: %.1f%n", 
                r.trades_11_13, r.trades_11_13 > 0 ? (double)(r.fullWins_11_13 + r.partialWins_11_13)/r.trades_11_13*100 : 0, r.netPoints_11_13);
            System.out.printf("    - 13:00-15:30: %d trades | Win Rate: %.1f%% | Net: %.1f%n", 
                r.trades_13_1530, r.trades_13_1530 > 0 ? (double)(r.fullWins_13_1530 + r.partialWins_13_1530)/r.trades_13_1530*100 : 0, r.netPoints_13_1530);
        }
        System.out.println("-------------------------------------------------------------------------");
    }

    @Override
    public Set<String> topics() {
        return Set.of("REQUEST_AUDIT");
    }

    @Override
    public void onMessage(AgentMessage message, AgentBus bus) {
        String symbol = (String) message.payload.get("symbol");
        int days = (int) message.payload.getOrDefault("days", 120);
        try {
            AuditResult result = runAudit(symbol, days);
            bus.publish(new AgentMessage("AUDIT_RESULT", Map.of("symbol", symbol, "result", result)));
        } catch (Exception e) {
            bus.publish(new AgentMessage("AUDIT_ERROR", Map.of("symbol", symbol, "error", e.getMessage())));
        }
    }

    @Override
    public String name() {
        return "AuditAgent";
    }
}
