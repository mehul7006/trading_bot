package com.trading.bot.agents;

import com.trading.bot.market.HonestMarketDataFetcher;
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

    /** One audited prediction with its real outcome — for prediction-wise listing. */
    public static class CallRecord {
        public LocalDateTime time;
        public String symbol;
        public String direction;
        public double entry;
        public double target;       // estimatedMovePoints
        public double stopLoss;
        public double confidence;
        public String outcome;      // "FULL WIN" / "PARTIAL" / "LOSS"
        public double points;       // realized points (signed)
        public String model;        // aiModel source (e.g. NIFTY_V31, SENSEX_V33_MR)
        public double rsi;          // RSI at entry (prediction.marketRegimePrediction)
        public double adx;          // trend strength at entry (prediction.neuralNetworkScore)
    }

    public static class AuditResult {
        public String symbol;
        public final List<CallRecord> calls = new ArrayList<>();
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
        this.marketDataAgent = new MarketDataAgent(HonestMarketDataFetcher.getInstance());
        this.predictionAgent = new PredictionAgent();
    }

    public AuditResult runAudit(String symbol, int days) {
        AuditResult res = new AuditResult();
        res.symbol = symbol;

        try {
            List<SimpleMarketData> data = marketDataAgent.getHistoricalData(symbol, days);
            if (data == null || data.size() < 200) return res;

            long lastSignalTime = 0;
            Set<LocalDate> tradingDaysSet = new HashSet<>();
            Set<LocalDate> allDaysWithData = new HashSet<>();

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData latest = data.get(i);
                LocalDateTime currentTimestamp = latest.timestamp;
                allDaysWithData.add(currentTimestamp.toLocalDate());

                java.time.LocalTime time = currentTimestamp.toLocalTime();
                if (time.isBefore(java.time.LocalTime.of(9, 15)) || time.isAfter(java.time.LocalTime.of(15, 25))) continue;
                int slab = getTimeSlab(time);
                long cooldownMillis = getCooldownMillis(symbol, slab);
                if (currentTimestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - lastSignalTime < cooldownMillis) continue;

                // Full history — faithful to live bot (predictor uses deep SMC/liquidity context).
                // History truncation materially changes signals, so no cap is used here.
                List<SimpleMarketData> history = data.subList(0, i + 1);
                AIPredictor.AIPrediction prediction = predictionAgent.generateSignal(symbol, history);

                if (passesGates(symbol, slab, prediction)) {
                    res.totalTrades++;
                    tradingDaysSet.add(currentTimestamp.toLocalDate());
                    lastSignalTime = currentTimestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

                    int outcome = verifyOutcome(data, i, prediction, 24);

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

    /**
     * Detailed honest audit over the LAST N trading days.
     * Identical signal logic, gates, cooldown and outcome verification as runAudit(),
     * but: (a) only counts calls whose date falls in the last {@code lastNTradingDays}
     * trading sessions present in the data, while still using all earlier candles for
     * indicator warm-up, and (b) records every counted prediction in res.calls.
     *
     * @param fetchDays           calendar days of data to pull (must comfortably exceed
     *                            the trading window + 200-candle warm-up)
     * @param lastNTradingDays    number of most-recent trading sessions to score
     */
    public AuditResult runAuditDetailed(String symbol, int fetchDays, int lastNTradingDays) {
        AuditResult res = new AuditResult();
        res.symbol = symbol;

        try {
            List<SimpleMarketData> data = marketDataAgent.getHistoricalData(symbol, fetchDays);
            if (data == null || data.size() < 200) return res;

            // Determine the window: last N distinct trading dates in the data.
            java.util.TreeSet<LocalDate> distinctDates = new java.util.TreeSet<>();
            for (SimpleMarketData d : data) {
                if (d.timestamp != null) distinctDates.add(d.timestamp.toLocalDate());
            }
            List<LocalDate> sorted = new ArrayList<>(distinctDates);
            int fromIdx = Math.max(0, sorted.size() - lastNTradingDays);
            Set<LocalDate> windowDays = new HashSet<>(sorted.subList(fromIdx, sorted.size()));

            long lastSignalTime = 0;
            Set<LocalDate> tradingDaysSet = new HashSet<>();
            // LIVE-MATCHED daily cap (Phase3TelegramBot.scanEquitySymbol). NIFTY uses a tighter
            // cap (2) because its trend-pullback edge is choppier — the 3rd marginal call/day
            // historically loses. SENSEX/BANKNIFTY keep 3.
            int dailyCap = "NIFTY50".equals(symbol) ? 2 : 3;
            java.util.Map<LocalDate, Integer> callsPerDay = new java.util.HashMap<>();

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData latest = data.get(i);
                LocalDateTime currentTimestamp = latest.timestamp;
                LocalDate day = currentTimestamp.toLocalDate();
                if (!windowDays.contains(day)) continue; // outside scored window (warm-up only)
                if (callsPerDay.getOrDefault(day, 0) >= dailyCap) continue; // daily cap reached (live behaviour)

                java.time.LocalTime time = currentTimestamp.toLocalTime();
                if (time.isBefore(java.time.LocalTime.of(9, 15)) || time.isAfter(java.time.LocalTime.of(15, 25))) continue;
                int slab = getTimeSlab(time);
                long cooldownMillis = getCooldownMillis(symbol, slab);
                if (currentTimestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - lastSignalTime < cooldownMillis) continue;

                List<SimpleMarketData> history = data.subList(0, i + 1);
                AIPredictor.AIPrediction prediction = predictionAgent.generateSignal(symbol, history);

                if (passesGates(symbol, slab, prediction)) {
                    res.totalTrades++;
                    tradingDaysSet.add(day);
                    callsPerDay.merge(day, 1, Integer::sum);
                    lastSignalTime = currentTimestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

                    int outcome = verifyOutcome(data, i, prediction, 24);

                    CallRecord cr = new CallRecord();
                    cr.time = currentTimestamp;
                    cr.symbol = symbol;
                    cr.direction = prediction.predictedDirection;
                    cr.entry = latest.price;
                    cr.target = prediction.estimatedMovePoints;
                    cr.stopLoss = prediction.suggestedStopLoss;
                    cr.confidence = prediction.confidence;
                    cr.model = prediction.aiModel;
                    cr.rsi = prediction.marketRegimePrediction;
                    cr.adx = prediction.neuralNetworkScore;

                    double pts;
                    if (outcome == 2) {
                        res.fullWins++;
                        pts = prediction.estimatedMovePoints;
                        cr.outcome = "FULL WIN";
                        if (slab == 0) { res.fullWins_9_11++; res.trades_9_11++; res.netPoints_9_11 += pts; }
                        else if (slab == 1) { res.fullWins_11_13++; res.trades_11_13++; res.netPoints_11_13 += pts; }
                        else { res.fullWins_13_1530++; res.trades_13_1530++; res.netPoints_13_1530 += pts; }
                    } else if (outcome == 1) {
                        res.partialWins++;
                        pts = prediction.estimatedMovePoints * 0.3;
                        cr.outcome = "PARTIAL";
                        if (slab == 0) { res.partialWins_9_11++; res.trades_9_11++; res.netPoints_9_11 += pts; }
                        else if (slab == 1) { res.partialWins_11_13++; res.trades_11_13++; res.netPoints_11_13 += pts; }
                        else { res.partialWins_13_1530++; res.trades_13_1530++; res.netPoints_13_1530 += pts; }
                    } else {
                        res.losses++;
                        pts = -prediction.suggestedStopLoss;
                        cr.outcome = "LOSS";
                        if (slab == 0) { res.losses_9_11++; res.trades_9_11++; res.netPoints_9_11 += pts; }
                        else if (slab == 1) { res.losses_11_13++; res.trades_11_13++; res.netPoints_11_13 += pts; }
                        else { res.losses_13_1530++; res.trades_13_1530++; res.netPoints_13_1530 += pts; }
                    }
                    cr.points = pts;
                    res.netPoints += pts;
                    res.calls.add(cr);
                }
            }
            res.tradingDays = tradingDaysSet.size();
            res.nonTradingDays = windowDays.size() - res.tradingDays;
            res.winRate = res.totalTrades > 0 ? ((double)(res.fullWins + res.partialWins) / res.totalTrades) * 100 : 0;
        } catch (Exception e) {
            System.err.println("❌ Detailed audit error for " + symbol + ": " + e.getMessage());
        }
        return res;
    }

    private long getCooldownMillis(String symbol, int slab) {
        // Match live bot: 2-min cooldown per symbol (Phase3TelegramBot line 893)
        return 2L * 60 * 1000;
    }

    private boolean passesGates(String symbol, int slab, AIPredictor.AIPrediction p) {
        // LIVE-MATCHED gates: mirror Phase3TelegramBot main-scan thresholds
        // so the audit call volume matches what users actually receive (~2-3/day).
        double minConf = 70;
        double minPts = switch (symbol) {
            case "NIFTY50"   -> 25.0;
            case "SENSEX"    -> 60.0;
            case "BANKNIFTY" -> 70.0;
            default          -> 20.0;
        };

        if (p.predictedDirection.equals("NEUTRAL")) return false;

        return p.confidence >= minConf && p.estimatedMovePoints >= minPts;
    }

    private boolean checkMinimumPoints(String symbol, double estimatedPoints) {
        double minPoints = switch (symbol) {
            case "NIFTY50"   -> 25.0;
            case "SENSEX"    -> 60.0;
            case "BANKNIFTY" -> 70.0;
            default          -> 20.0;
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
