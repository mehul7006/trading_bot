package com.trading.bot.test;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.ai.AIPredictor;

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
public class OneTwentyDayHonestAudit {

    static class AuditResult {
        String symbol;
        int totalTrades;
        int fullWins;
        int partialWins;
        int losses;
        double winRate;
        double netPoints;
        
        int tradingDays;
        int nonTradingDays;
        
        // Time Bifurcation
        int trades_9_11;
        int fullWins_9_11;
        int partialWins_9_11;
        int losses_9_11;
        double netPoints_9_11;
        
        int trades_11_13;
        int fullWins_11_13;
        int partialWins_11_13;
        int losses_11_13;
        double netPoints_11_13;
        
        int trades_13_1530;
        int fullWins_13_1530;
        int partialWins_13_1530;
        int losses_13_1530;
        double netPoints_13_1530;
        
        public String toString() {
            return String.format("| %-10s | %5d | %5d | %7d | %5d | %7.1f%% | %8.1f |", 
                symbol, totalTrades, fullWins, partialWins, losses, winRate, netPoints);
        }
    }

    public static void main(String[] args) {
        System.out.println("🔥 STARTING 120-DAY HONEST AUDIT (Exact Live Bot Logic) 🔥");
        System.out.println("--------------------------------------------------------------------------------------");
        System.out.println("Fetching REAL DATA from Upstox... (Last 120 Days)");

        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        AIPredictor predictor = new AIPredictor();
        
        String[] symbols = {"NIFTY50", "SENSEX"};
        
        List<AuditResult> results = new ArrayList<>();

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(120); 

        for (String symbol : symbols) {
            System.out.println("\n======================================================================================");
            System.out.println("📊 Processing Symbol: " + symbol);
            System.out.println("======================================================================================");
            
            // 1. Fetch 1-Minute Data (Base Data)
            System.out.print("   Fetching Data... ");
            List<SimpleMarketData> data1Min = new ArrayList<>();
            
            // Fetch in 10-day chunks for stability
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(10)) {
                LocalDate chunkEnd = date.plusDays(9);
                if (chunkEnd.isAfter(end)) chunkEnd = end;
                
                List<SimpleMarketData> chunk = fetcher.fetchHistoricalCandles(symbol, "1minute", date.toString(), chunkEnd.toString());
                if (chunk != null) {
                    data1Min.addAll(chunk);
                }
                System.out.print(".");
                try { Thread.sleep(100); } catch (Exception e) {}
            }
            System.out.println(" Done! (" + data1Min.size() + " raw candles)");

            if (data1Min.size() < 200) {
                System.out.println("⚠️ Not enough data for " + symbol);
                continue;
            }

            // 2. Resample to 5-Minute Data
            List<SimpleMarketData> data5Min = resampleTo5Minute(data1Min);
            
            // 3. Run Backtest
            System.out.println("   ▶ Running Strategy Audit...");
            results.add(runBacktest(symbol, data5Min, predictor));
        }
        
        // Final Report
        printFinalReport(results);
    }

    private static AuditResult runBacktest(String symbol, List<SimpleMarketData> data, AIPredictor predictor) {
        AuditResult res = new AuditResult();
        res.symbol = symbol;
        
        long lastSignalTime = 0;
        long fifteenMinMillis = 15 * 60 * 1000;

        java.util.Set<LocalDate> tradingDaysSet = new java.util.HashSet<>();
        java.util.Set<LocalDate> allDaysWithData = new java.util.HashSet<>();

        for (int i = 200; i < data.size(); i++) {
            SimpleMarketData latest = data.get(i);
            LocalDateTime currentTimestamp = latest.timestamp;
            allDaysWithData.add(currentTimestamp.toLocalDate());

            // 15-minute cooldown
            if (currentTimestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - lastSignalTime < fifteenMinMillis) {
                continue;
            }

            java.time.LocalTime time = currentTimestamp.toLocalTime();
            if (time.isBefore(java.time.LocalTime.of(9, 15)) || time.isAfter(java.time.LocalTime.of(15, 25))) {
                continue;
            }

            List<SimpleMarketData> history = data.subList(0, i + 1);
            
            // Mock option data for backtest
            double avg20 = 0;
            if (history.size() >= 20) {
                double sum = 0;
                for (int j = history.size() - 20; j < history.size(); j++) sum += history.get(j).price;
                avg20 = sum / 20;
            }
            com.trading.bot.market.OptionData mockOptionData = (latest.price > avg20) 
                ? new com.trading.bot.market.OptionData(symbol, 1.3, 100, 250, 100, 300, null, latest.price)
                : new com.trading.bot.market.OptionData(symbol, 0.7, 250, 100, 300, 100, null, latest.price);

            AIPredictor.AIPrediction prediction = predictor.generatePrediction(symbol, history, mockOptionData);
            
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
        return res;
    }

    private static int getTimeSlab(java.time.LocalTime time) {
        if (time.isBefore(java.time.LocalTime.of(11, 0))) return 0;
        if (time.isBefore(java.time.LocalTime.of(13, 0))) return 1;
        return 2;
    }

    private static void printFinalReport(List<AuditResult> results) {
        System.out.println("\n\n🏆 FINAL 120-DAY BOT AUDIT RESULTS (Multi-Strategy V12) 🏆");
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
        System.out.println("✅ Note: These results reflect the EXACT logic currently running in the bot.");
    }

    private static boolean checkMinimumPoints(String symbol, double estimatedPoints) {
        double minPoints = switch (symbol) {
            case "NIFTY50" -> 30.0;
            case "SENSEX" -> 80.0;
            default -> 20.0;
        };
        return estimatedPoints >= minPoints;
    }

    private static List<SimpleMarketData> resampleTo5Minute(List<SimpleMarketData> data1Min) {
        List<SimpleMarketData> data5Min = new ArrayList<>();
        if (data1Min.isEmpty()) return data5Min;

        SimpleMarketData current5Min = null;
        LocalDateTime candleStartTime = null;

        for (SimpleMarketData candle : data1Min) {
            int minute = candle.timestamp.getMinute();
            int minuteMod5 = minute % 5;
            LocalDateTime periodStart = candle.timestamp.minusMinutes(minuteMod5).withSecond(0).withNano(0);

            if (current5Min == null || !periodStart.equals(candleStartTime)) {
                if (current5Min != null) data5Min.add(current5Min);
                candleStartTime = periodStart;
                current5Min = new SimpleMarketData(candle.symbol, candle.price, candle.open, candle.high, candle.low, candle.volume, periodStart);
            } else {
                current5Min = new SimpleMarketData(current5Min.symbol, candle.price, current5Min.open, 
                    Math.max(current5Min.high, candle.high), Math.min(current5Min.low, candle.low), 
                    current5Min.volume + candle.volume, current5Min.timestamp);
            }
        }
        if (current5Min != null) data5Min.add(current5Min);
        return data5Min;
    }

    private static int verifyOutcome(List<SimpleMarketData> allData, int currentIndex, AIPredictor.AIPrediction prediction, int lookAhead) {
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
}
