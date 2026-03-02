package com.trading.bot.test;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.OptionData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

    /**
     * 1-Month Honest Audit of the CURRENT Bot Implementation (Dual Timeframe Logic)
     * Matches Updated Live Bot Logic:
     * - Real 1-Minute Data from Upstox, Resampled to 5-Minute
     * - 5-Min Signal: confidence >= 80% AND minimum point thresholds
     * - 1-Min Signal: confidence >= 85% AND minimum point thresholds
     * - Trigger if EITHER 5-Min OR 1-Min condition passes (any one)
     * - 5-Minute Cooldown between calls per symbol
     * - Market Hours (9:00 - 15:30)
     */
public class LastWeekHonestAudit {

    static class AuditResult {
        String symbol;
        int totalTrades;
        int fullWins;
        int partialWins;
        int losses;
        double winRate;
        double netPoints;
        
        public String toString() {
            return String.format("| %-10s | %5d | %5d | %7d | %5d | %7.1f%% | %8.1f |", 
                symbol, totalTrades, fullWins, partialWins, losses, winRate, netPoints);
        }
    }

    public static void main(String[] args) {
        System.out.println("🔥 STARTING LAST 1 MONTH HONEST AUDIT (Dual Timeframe Live Logic) 🔥");
        System.out.println("--------------------------------------------------------------------------------------");
        System.out.println("Fetching REAL DATA from Upstox... (Last 30 Days)");

        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        AIPredictor predictor = new AIPredictor();
        
        String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};
        
        List<AuditResult> results3Weeks = new ArrayList<>();
        List<AuditResult> resultsToday = new ArrayList<>();

        LocalDate end = LocalDate.now();
        LocalDate start3Weeks = end.minusDays(30); 

        for (String symbol : symbols) {
            System.out.println("\n======================================================================================");
            System.out.println("📊 Processing Symbol: " + symbol);
            System.out.println("======================================================================================");
            
            // 1. Fetch 1-Minute Data (Base Data)
            System.out.print("   Fetching 1-Month Data... ");
            List<SimpleMarketData> data1Min3Weeks = new ArrayList<>();
            for (LocalDate date = start3Weeks; !date.isAfter(end); date = date.plusDays(1)) {
                List<SimpleMarketData> chunk = fetcher.fetchHistoricalCandles(symbol, "1minute", date.toString(), date.toString());
                if (chunk != null) data1Min3Weeks.addAll(chunk);
                System.out.print(".");
                try { Thread.sleep(50); } catch (Exception e) {}
            }
            System.out.println(" Done! (" + data1Min3Weeks.size() + " candles)");

            if (data1Min3Weeks.size() < 100) continue;

            List<SimpleMarketData> data5Min3Weeks = resampleTo5Minute(data1Min3Weeks);
            results3Weeks.add(runBacktest(symbol, data5Min3Weeks, data1Min3Weeks, predictor, fetcher, false));

            // Today's specific check
            System.out.print("   Checking Today's Calls... ");
            List<SimpleMarketData> data1MinToday = new ArrayList<>();
            List<SimpleMarketData> todayChunk = fetcher.fetchHistoricalCandles(symbol, "1minute", end.toString(), end.toString());
            if (todayChunk != null) data1MinToday.addAll(todayChunk);
            
            if (data1MinToday.size() > 50) {
                List<SimpleMarketData> data5MinToday = resampleTo5Minute(data1MinToday);
                resultsToday.add(runBacktest(symbol, data5MinToday, data1MinToday, predictor, fetcher, true));
                System.out.println(" Done!");
            } else {
                System.out.println(" Not enough data for today yet.");
            }
        }
        
        System.out.println("\n\n📊 --- [ 1-MONTH HONEST AUDIT SUMMARY ] ---");
        printFinalReport(results3Weeks, "LAST 1 MONTH");
        
        System.out.println("\n\n📅 --- [ TODAY'S CALLS SUMMARY ] ---");
        if (resultsToday.isEmpty()) {
            System.out.println("No calls generated today so far.");
        } else {
            printFinalReport(resultsToday, "TODAY");
        }
    }

    private static AuditResult runBacktest(String symbol, List<SimpleMarketData> data5, List<SimpleMarketData> data1, AIPredictor predictor, HonestMarketDataFetcher fetcher, boolean verbose) {
        AuditResult res = new AuditResult();
        res.symbol = symbol;
        
        java.time.LocalDateTime lastSignalTime = null;

        // Fetch option data once per symbol for performance
        OptionData optionData = fetcher.fetchOptionData(symbol);
        // Indicators need warm-up
        for (int i = 50; i < data5.size(); i++) {
            // Time Check (9:00 - 15:30)
            LocalTime time = data5.get(i).timestamp.toLocalTime();
            if (time.isBefore(LocalTime.of(9, 0)) || time.isAfter(LocalTime.of(15, 30))) {
                continue;
            }

            java.time.LocalDateTime currentTimestamp = data5.get(i).timestamp;

            if (lastSignalTime != null) {
                long minutesSinceLast = java.time.Duration.between(lastSignalTime, currentTimestamp).toMinutes();
                if (minutesSinceLast < 5) {
                    continue;
                }
            }

            List<SimpleMarketData> history5 = data5.subList(0, i + 1);
            AIPredictor.AIPrediction prediction5 = predictor.generatePrediction(symbol, history5, optionData);

            List<SimpleMarketData> history1 = new ArrayList<>();
            for (SimpleMarketData candle : data1) {
                if (!candle.timestamp.isAfter(currentTimestamp)) {
                    history1.add(candle);
                } else {
                    break;
                }
            }
            if (history1.size() < 50) {
                continue;
            }
            AIPredictor.AIPrediction prediction1 = predictor.generatePrediction(symbol, history1, optionData);
            
            boolean isSignificant5 = checkMinimumPoints(symbol, prediction5.estimatedMovePoints);
            boolean isSignificant1 = checkMinimumPoints(symbol, prediction1.estimatedMovePoints);
            
            boolean fiveMinEligible = isSignificant5 && prediction5.confidence >= 80;
            boolean oneMinEligible = isSignificant1 && prediction1.confidence >= 85;
            
            if (!fiveMinEligible && !oneMinEligible) {
                continue;
            }
            
            AIPredictor.AIPrediction chosenPrediction;
            String timeframeLabel;
            double entryPrice;
            
            if (fiveMinEligible) {
                chosenPrediction = prediction5;
                timeframeLabel = "5-min";
                entryPrice = data5.get(i).price;
            } else {
                chosenPrediction = prediction1;
                timeframeLabel = "1-min";
                entryPrice = history1.get(history1.size() - 1).price;
            }
            
            res.totalTrades++;
            lastSignalTime = currentTimestamp;

            double target = chosenPrediction.estimatedMovePoints;
            double sl = chosenPrediction.suggestedStopLoss;
            String direction = chosenPrediction.predictedDirection;
            double targetPrice = direction.equals("UP") ? entryPrice + target : entryPrice - target;
            double slPrice = direction.equals("UP") ? entryPrice - sl : entryPrice + sl;

            if (verbose) {
                System.out.println(String.format("   🚨 [SIGNAL FOUND] Time: %s | TF: %s | Entry: %.2f | Target: %.2f (Pts: %.0f) | SL: %.2f (Pts: %.0f)", 
                    currentTimestamp, timeframeLabel, entryPrice, targetPrice, target, slPrice, sl));
            }

            double maxFavorable = 0;
            double maxAdverse = 0;
            int outcome = 0;
            boolean reachedPartial = false;
            double partialTargetPrice = direction.equals("UP") ? entryPrice + (target * 0.5) : entryPrice - (target * 0.5);

            for (int j = 1; j <= 24 && (i + j) < data5.size(); j++) {
                SimpleMarketData future = data5.get(i + j);
                
                if (direction.equals("UP")) {
                    maxFavorable = Math.max(maxFavorable, future.high - entryPrice);
                    maxAdverse = Math.max(maxAdverse, entryPrice - future.low);
                    
                    if (future.low <= slPrice) {
                        outcome = reachedPartial ? 1 : 0;
                        break;
                    }
                    if (future.high >= targetPrice) {
                        outcome = 2;
                        break;
                    }
                    if (future.high >= partialTargetPrice) reachedPartial = true;
                } else {
                    maxFavorable = Math.max(maxFavorable, entryPrice - future.low);
                    maxAdverse = Math.max(maxAdverse, future.high - entryPrice);
                    
                    if (future.high >= slPrice) {
                        outcome = reachedPartial ? 1 : 0;
                        break;
                    }
                    if (future.low <= targetPrice) {
                        outcome = 2;
                        break;
                    }
                    if (future.low <= partialTargetPrice) reachedPartial = true;
                }
            }
            
            if (outcome == 0 && reachedPartial) outcome = 1;

            if (verbose) {
                String outcomeStr = outcome == 2 ? "✅ FULL WIN" : (outcome == 1 ? "⚠️ PARTIAL WIN" : "❌ LOSS");
                System.out.println(String.format("      └─ Movement: Max Fav: +%.2f pts | Max Adv: -%.2f pts", maxFavorable, maxAdverse));
                System.out.println("      └─ Final Result: " + outcomeStr);

                System.out.println("\n   --- [TELEGRAM NOTIFICATION MESSAGE] ---");
                System.out.println("   🔔 *NEW SIGNAL DETECTED*");
                System.out.println("   ⏱️ Timeframe: " + timeframeLabel);
                System.out.println("   📈 Symbol: " + symbol);
                System.out.println("   🚀 Direction: " + (direction.equals("UP") ? "BUY ⬆️" : "SELL ⬇️"));
                System.out.println("   💰 Entry Price: " + String.format("%.2f", entryPrice));
                System.out.println("   🎯 Target Price: " + String.format("%.2f", targetPrice));
                System.out.println("   🛡️ Stop Loss: " + String.format("%.2f", slPrice));
                System.out.println("   🤖 Confidence: " + String.format("%.1f", chosenPrediction.confidence) + "%");
                System.out.println("   🕒 Analysis Time: " + currentTimestamp.toLocalTime());
                System.out.println("   ---------------------------------------\n");
            }

            if (outcome == 0 && verbose) {
                boolean hitLater = false;
                LocalDateTime hitTime = null;
                for (int k = i + 1; k < data5.size(); k++) {
                    SimpleMarketData future = data5.get(k);
                    if (future.timestamp.toLocalDate().isAfter(currentTimestamp.toLocalDate())) break;
                    
                    if (direction.equals("UP")) {
                        if (future.high >= targetPrice) {
                            hitLater = true;
                            hitTime = future.timestamp;
                            break;
                        }
                    } else {
                        if (future.low <= targetPrice) {
                            hitLater = true;
                            hitTime = future.timestamp;
                            break;
                        }
                    }
                }
                if (hitLater) {
                    System.out.println("      └─ ♻️ RECOVERY: Target was eventually hit at " + hitTime.toLocalTime());
                } else {
                    System.out.println("      └─ ❌ NO RECOVERY: Target was NEVER hit for the rest of the day.");
                }
            }
            
            if (outcome == 2) {
                res.fullWins++;
                res.netPoints += chosenPrediction.estimatedMovePoints;
            } else if (outcome == 1) {
                res.partialWins++;
                res.netPoints += (chosenPrediction.estimatedMovePoints * 0.3);
            } else {
                res.losses++;
                res.netPoints -= chosenPrediction.suggestedStopLoss;
            }
        }
        
        res.winRate = res.totalTrades > 0 ? ((double)(res.fullWins + res.partialWins) / res.totalTrades) * 100 : 0;
        return res;
    }
    
    private static boolean checkMinimumPoints(String symbol, double estimatedPoints) {
        double minPoints = switch (symbol) {
            case "NIFTY50" -> 30.0;
            case "SENSEX" -> 80.0;
            case "BANKNIFTY" -> 50.0;
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

    private static void printFinalReport(List<AuditResult> results, String periodLabel) {
        System.out.println("\n\n🏆 FINAL " + periodLabel + " BOT AUDIT RESULTS (Dual Timeframe Live Strategy) 🏆");
        System.out.println("-------------------------------------------------------------------------");
        System.out.println("| Symbol     | Trades| Full  | Partial | Loss  | Win Rate | Net Pts  |");
        System.out.println("|------------|-------|-------|---------|-------|----------|----------|");
        
        double totalWinRate = 0;
        int count = 0;
        
        for (AuditResult r : results) {
            System.out.println(r.toString());
            if (r.totalTrades > 0) {
                totalWinRate += r.winRate;
                count++;
            }
        }
        System.out.println("-------------------------------------------------------------------------");
        
        if (count > 0) {
            System.out.printf("🌟 AVERAGE WIN RATE: %.1f%%%n", totalWinRate / count);
        }
        System.out.println("✅ Note: Results reflect EXACT bot logic (Dual TF: 5-min>=80%% OR 1-min>=85%%, 5-min cooldown).");
    }
}
