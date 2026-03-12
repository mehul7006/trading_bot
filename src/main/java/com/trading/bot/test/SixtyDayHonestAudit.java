package com.trading.bot.test;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.ai.AIPredictor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SixtyDayHonestAudit {

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
        System.out.println("🔥 STARTING 120-DAY FULL BOT AUDIT (LATEST V6 LOGIC) 🔥");
        System.out.println("--------------------------------------------------------------------------------------");
        System.out.println("Fetching REAL DATA from Upstox... (Last 120 Days)");

        HonestMarketDataFetcher fetcher = HonestMarketDataFetcher.getInstance();
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
            
            // 3. Run Backtest with Latest Dual Timeframe Logic
            System.out.println("   ▶ Running Strategy Audit...");
            results.add(runBacktest(symbol, data5Min, data1Min, predictor));
        }
        
        // Final Report
        printFinalReport(results);
    }

    private static AuditResult runBacktest(String symbol, List<SimpleMarketData> data5, List<SimpleMarketData> data1, AIPredictor predictor) {
        AuditResult res = new AuditResult();
        res.symbol = symbol;
        
        long lastSignalTime = 0;
        long fiveMinMillis = 5 * 60 * 1000;

        java.util.Set<LocalDate> tradingDaysSet = new java.util.HashSet<>();
        java.util.Set<LocalDate> allDaysWithData = new java.util.HashSet<>();

        // Map to hold 1-min data grouped by timestamp for quick lookup
        java.util.Map<LocalDateTime, List<SimpleMarketData>> data1Map = new java.util.HashMap<>();
        for (SimpleMarketData d : data1) {
            allDaysWithData.add(d.timestamp.toLocalDate());
        }

        for (int i = 200; i < data5.size(); i++) {
            SimpleMarketData latest5 = data5.get(i);
            LocalDateTime currentTimestamp = latest5.timestamp;
            allDaysWithData.add(currentTimestamp.toLocalDate());

            // 5-minute cooldown
            if (currentTimestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - lastSignalTime < fiveMinMillis) {
                continue;
            }

            java.time.LocalTime time = currentTimestamp.toLocalTime();
            if (time.isBefore(java.time.LocalTime.of(9, 15)) || time.isAfter(java.time.LocalTime.of(15, 25))) {
                continue;
            }

            // Slice history for 5-min prediction
            List<SimpleMarketData> history5 = data5.subList(0, i + 1);
            
            AIPredictor.AIPrediction prediction5 = predictor.generatePrediction(symbol, history5, null);
            
            // Find corresponding 1-min history
            List<SimpleMarketData> history1 = new ArrayList<>();
            for (SimpleMarketData d : data1) {
                if (!d.timestamp.isAfter(currentTimestamp)) {
                    history1.add(d);
                } else {
                    break;
                }
            }
            
            if (history1.size() < 100) continue;
            AIPredictor.AIPrediction prediction1 = predictor.generatePrediction(symbol, history1, null);
            
            // DUAL TIMEFRAME LOGIC (Live Bot implementation)
            boolean fiveMinEligible = checkMinimumPoints(symbol, prediction5.estimatedMovePoints) && prediction5.confidence >= 70;
            boolean oneMinEligible = false;
            if (prediction1 != null) {
                oneMinEligible = checkMinimumPoints(symbol, prediction1.estimatedMovePoints) && prediction1.confidence >= 75;
            }

            AIPredictor.AIPrediction chosen = null;
            double entryPrice = 0;

            if (fiveMinEligible) {
                chosen = prediction5;
                entryPrice = latest5.price;
            } else if (oneMinEligible) {
                chosen = prediction1;
                entryPrice = history1.get(history1.size() - 1).price;
            }

            if (chosen != null) {
                res.totalTrades++;
                tradingDaysSet.add(currentTimestamp.toLocalDate());
                lastSignalTime = currentTimestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                
                int outcome = verifyOutcome(data5, i, chosen, 24);
                int slab = getTimeSlab(time);

                if (outcome == 2) {
                    res.fullWins++;
                    res.netPoints += chosen.estimatedMovePoints;
                    if (slab == 0) { res.fullWins_9_11++; res.trades_9_11++; res.netPoints_9_11 += chosen.estimatedMovePoints; }
                    else if (slab == 1) { res.fullWins_11_13++; res.trades_11_13++; res.netPoints_11_13 += chosen.estimatedMovePoints; }
                    else { res.fullWins_13_1530++; res.trades_13_1530++; res.netPoints_13_1530 += chosen.estimatedMovePoints; }
                } else if (outcome == 1) {
                    res.partialWins++;
                    res.netPoints += (chosen.estimatedMovePoints * 0.3);
                    if (slab == 0) { res.partialWins_9_11++; res.trades_9_11++; res.netPoints_9_11 += (chosen.estimatedMovePoints * 0.3); }
                    else if (slab == 1) { res.partialWins_11_13++; res.trades_11_13++; res.netPoints_11_13 += (chosen.estimatedMovePoints * 0.3); }
                    else { res.partialWins_13_1530++; res.trades_13_1530++; res.netPoints_13_1530 += (chosen.estimatedMovePoints * 0.3); }
                } else {
                    res.losses++;
                    res.netPoints -= chosen.suggestedStopLoss;
                    if (slab == 0) { res.losses_9_11++; res.trades_9_11++; res.netPoints_9_11 -= chosen.suggestedStopLoss; }
                    else if (slab == 1) { res.losses_11_13++; res.trades_11_13++; res.netPoints_11_13 -= chosen.suggestedStopLoss; }
                    else { res.losses_13_1530++; res.trades_13_1530++; res.netPoints_13_1530 -= chosen.suggestedStopLoss; }
                }
            }
        }
        
        res.tradingDays = tradingDaysSet.size();
        res.nonTradingDays = allDaysWithData.size() - res.tradingDays;
        res.winRate = res.totalTrades > 0 ? ((double)(res.fullWins + res.partialWins) / res.totalTrades) * 100 : 0;
        return res;
    }

    private static int getTimeSlab(java.time.LocalTime time) {
        if (time.isBefore(java.time.LocalTime.of(11, 0))) {
            return 0;
        }
        if (time.isBefore(java.time.LocalTime.of(13, 0))) {
            return 1;
        }
        return 2;
    }

    private static boolean checkMinimumPoints(String symbol, double estimatedPoints) {
        double minPoints = switch (symbol) {
            case "NIFTY50" -> 20.0;
            case "SENSEX" -> 60.0;
            default -> 15.0;
        };
        return estimatedPoints >= minPoints;
    }

    public static List<SimpleMarketData> resampleTo5Minute(List<SimpleMarketData> data1Min) {
        List<SimpleMarketData> data5Min = new ArrayList<>();
        if (data1Min.isEmpty()) return data5Min;

        SimpleMarketData current5Min = null;
        LocalDateTime candleStartTime = null;

        for (SimpleMarketData candle : data1Min) {
            int minute = candle.timestamp.getMinute();
            int minuteMod5 = minute % 5;
            LocalDateTime periodStart = candle.timestamp.minusMinutes(minuteMod5).withSecond(0).withNano(0);

            if (current5Min == null || !periodStart.equals(candleStartTime)) {
                if (current5Min != null) {
                    data5Min.add(current5Min);
                }
                candleStartTime = periodStart;
                current5Min = new SimpleMarketData(candle.symbol, candle.price, candle.open, candle.high, candle.low, candle.volume, periodStart);
            } else {
                current5Min = new SimpleMarketData(
                    current5Min.symbol, 
                    candle.price, 
                    current5Min.open, 
                    Math.max(current5Min.high, candle.high), 
                    Math.min(current5Min.low, candle.low), 
                    current5Min.volume + candle.volume, 
                    current5Min.timestamp
                );
            }
        }
        if (current5Min != null) {
            data5Min.add(current5Min);
        }
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
                if (future.low <= slPrice) return reachedPartial ? 1 : 0; // Hit SL
                if (future.high >= targetPrice) return 2; // Full Win
                if (future.high >= partialTargetPrice) reachedPartial = true;
            } else {
                if (future.high >= slPrice) return reachedPartial ? 1 : 0; // Hit SL
                if (future.low <= targetPrice) return 2; // Full Win
                if (future.low <= partialTargetPrice) reachedPartial = true;
            }
        }
        
        return reachedPartial ? 1 : 0;
    }

    private static void printFinalReport(List<AuditResult> results) {
        System.out.println("\n\n🏆 FINAL 120-DAY BOT AUDIT RESULTS (LATEST V6 DUAL LOGIC) 🏆");
        System.out.println("-------------------------------------------------------------------------");
        System.out.println("| Symbol     | Trades| Full  | Partial | Loss  | Win Rate | Net Pts  |");
        System.out.println("|------------|-------|-------|---------|-------|----------|----------|");
        
        double totalWinRate = 0;
        int count = 0;
        
        for (AuditResult r : results) {
            System.out.println(r.toString());
            System.out.printf("  Activity  => Trading Days: %d | Non-Trading Days: %d%n", r.tradingDays, r.nonTradingDays);
            
            if (r.trades_9_11 > 0 || r.trades_11_13 > 0 || r.trades_13_1530 > 0) {
                System.out.println("  Time-of-day breakdown (9-11 / 11-13 / 13-15:30):");
                
                if (r.trades_9_11 > 0) {
                    double winRate9_11 = ((double)(r.fullWins_9_11 + r.partialWins_9_11) / r.trades_9_11) * 100;
                    System.out.printf("    9-11    => Trades: %3d | Wins: %3d | Partial: %3d | Loss: %3d | WinRate: %5.1f%% | NetPts: %7.1f%n",
                            r.trades_9_11, r.fullWins_9_11, r.partialWins_9_11, r.losses_9_11, winRate9_11, r.netPoints_9_11);
                }
                
                if (r.trades_11_13 > 0) {
                    double winRate11_13 = ((double)(r.fullWins_11_13 + r.partialWins_11_13) / r.trades_11_13) * 100;
                    System.out.printf("    11-13   => Trades: %3d | Wins: %3d | Partial: %3d | Loss: %3d | WinRate: %5.1f%% | NetPts: %7.1f%n",
                            r.trades_11_13, r.fullWins_11_13, r.partialWins_11_13, r.losses_11_13, winRate11_13, r.netPoints_11_13);
                }
                
                if (r.trades_13_1530 > 0) {
                    double winRate13_1530 = ((double)(r.fullWins_13_1530 + r.partialWins_13_1530) / r.trades_13_1530) * 100;
                    System.out.printf("    13-15:30=> Trades: %3d | Wins: %3d | Partial: %3d | Loss: %3d | WinRate: %5.1f%% | NetPts: %7.1f%n",
                            r.trades_13_1530, r.fullWins_13_1530, r.partialWins_13_1530, r.losses_13_1530, winRate13_1530, r.netPoints_13_1530);
                }
            }
            
            if (r.totalTrades > 0) {
                totalWinRate += r.winRate;
                count++;
            }
        }
        System.out.println("-------------------------------------------------------------------------");
        
        if (count > 0) {
            System.out.printf("🌟 AVERAGE WIN RATE: %.1f%%%n", totalWinRate / count);
        }
        System.out.println("✅ Note: These results reflect the EXACT logic currently running in the bot.");
    }
}
