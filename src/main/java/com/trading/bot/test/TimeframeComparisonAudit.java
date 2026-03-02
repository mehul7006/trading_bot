package com.trading.bot.test;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.ai.AIPredictor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 120-Day Honest Comparison: 1-Minute vs 5-Minute Timeframes
 */
public class TimeframeComparisonAudit {

    static class ComparisonResult {
        String symbol;
        String timeframe;
        int totalTrades;
        int fullWins;
        int partialWins;
        int losses;
        double winRate;
        double netPoints; // Rough estimate
        
        public String toString() {
            return String.format("| %-10s | %-8s | %5d | %5d | %7d | %5d | %7.1f%% | %8.1f |", 
                symbol, timeframe, totalTrades, fullWins, partialWins, losses, winRate, netPoints);
        }
    }

    public static void main(String[] args) {
        System.out.println("🔥 STARTING 120-DAY TIMEFRAME COMPARISON (1-Min vs 5-Min) 🔥");
        System.out.println("--------------------------------------------------------------------------------------");
        System.out.println("Fetching REAL DATA from Upstox... This may take a few minutes due to data volume.");

        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        AIPredictor predictor = new AIPredictor();
        
        String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};
        
        List<ComparisonResult> results = new ArrayList<>();

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(120); // 120 Days

        for (String symbol : symbols) {
            System.out.println("\n======================================================================================");
            System.out.println("📊 Processing Symbol: " + symbol);
            System.out.println("======================================================================================");
            
            // 1. Fetch 1-Minute Data (Base Data)
            System.out.print("   Fetching 1-Minute Data... ");
            List<SimpleMarketData> data1Min = new ArrayList<>();
            
            // Fetch in 5-day chunks
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(5)) {
                LocalDate chunkEnd = date.plusDays(4);
                if (chunkEnd.isAfter(end)) chunkEnd = end;
                
                List<SimpleMarketData> chunk = fetcher.fetchHistoricalCandles(symbol, "1minute", date.toString(), chunkEnd.toString());
                if (chunk != null) {
                    data1Min.addAll(chunk);
                } else {
                     System.out.print("X"); // Mark failure
                }
                System.out.print(".");
                try { Thread.sleep(200); } catch (Exception e) {} // Rate limit prevention
            }
            System.out.println(" Done! (" + data1Min.size() + " candles)");

            if (data1Min.size() < 200) {
                System.out.println("⚠️ Not enough data for " + symbol + " (Got " + data1Min.size() + ")");
                continue;
            }

            // 2. Resample to 5-Minute Data
            System.out.print("   Resampling to 5-Minute Data... ");
            List<SimpleMarketData> data5Min = resampleTo5Minute(data1Min);
            System.out.println(" Done! (" + data5Min.size() + " candles)");

            // 3. Run Backtest for 1-Minute
            System.out.println("   ▶ Running 1-Minute Strategy...");
            results.add(runBacktest(symbol, "1minute", data1Min, predictor));

            // 4. Run Backtest for 5-Minute
            System.out.println("   ▶ Running 5-Minute Strategy...");
            results.add(runBacktest(symbol, "5minute", data5Min, predictor));
        }
        
        // Final Report
        printFinalReport(results);
    }

    private static ComparisonResult runBacktest(String symbol, String timeframe, List<SimpleMarketData> data, AIPredictor predictor) {
        ComparisonResult res = new ComparisonResult();
        res.symbol = symbol;
        res.timeframe = timeframe;
        
        int cooldown = 0;
        
        // Cooldown depends on timeframe. 
        // If 1-min chart, 15 mins cooldown = 15 candles.
        // If 5-min chart, 15 mins cooldown = 3 candles.
        int cooldownCandles = timeframe.equals("1minute") ? 15 : 3;

        // Start from index 200 to allow indicators to warm up
        for (int i = 200; i < data.size(); i++) {
            if (cooldown > 0) {
                cooldown--;
                continue;
            }

            // Slice data for prediction (simulate real-time)
            List<SimpleMarketData> history = data.subList(0, i + 1);
            AIPredictor.AIPrediction prediction = predictor.generatePrediction(symbol, history);
            
            // Filter: Only High Confidence > 80% (Same as Live Bot)
            if (prediction.confidence >= 80) {
                res.totalTrades++;
                
                // Verify Outcome
                // For 1-min chart, check next 120 candles (2 hours).
                // For 5-min chart, check next 24 candles (2 hours).
                int lookAhead = timeframe.equals("1minute") ? 120 : 24;
                
                int outcome = verifyOutcome(data, i, prediction, lookAhead);
                
                if (outcome == 2) {
                    res.fullWins++;
                    res.netPoints += prediction.estimatedMovePoints;
                } else if (outcome == 1) {
                    res.partialWins++;
                    res.netPoints += (prediction.estimatedMovePoints * 0.3); // Conservative partial gain
                } else {
                    res.losses++;
                    res.netPoints -= prediction.suggestedStopLoss;
                }
                
                cooldown = cooldownCandles;
            }
        }
        
        res.winRate = res.totalTrades > 0 ? ((double)(res.fullWins + res.partialWins) / res.totalTrades) * 100 : 0;
        return res;
    }

    private static List<SimpleMarketData> resampleTo5Minute(List<SimpleMarketData> data1Min) {
        List<SimpleMarketData> data5Min = new ArrayList<>();
        if (data1Min.isEmpty()) return data5Min;

        SimpleMarketData current5Min = null;
        LocalDateTime candleStartTime = null;

        for (SimpleMarketData candle : data1Min) {
            // Round down to nearest 5 minutes
            int minute = candle.timestamp.getMinute();
            int minuteMod5 = minute % 5;
            LocalDateTime periodStart = candle.timestamp.minusMinutes(minuteMod5).withSecond(0).withNano(0);

            if (current5Min == null || !periodStart.equals(candleStartTime)) {
                // New 5-min candle
                if (current5Min != null) {
                    data5Min.add(current5Min);
                }
                candleStartTime = periodStart;
                current5Min = new SimpleMarketData(candle.symbol, candle.price, candle.open, candle.high, candle.low, candle.volume, periodStart);
            } else {
                // Update existing 5-min candle
                // High
                if (candle.high > current5Min.high) {
                    // We need a setter or create new instance. SimpleMarketData fields are public final usually?
                    // Let's check SimpleMarketData definition. Assuming they are final, we reconstruct.
                    current5Min = new SimpleMarketData(
                        current5Min.symbol, 
                        candle.price, // Close updates to latest
                        current5Min.open, 
                        Math.max(current5Min.high, candle.high), 
                        Math.min(current5Min.low, candle.low), 
                        current5Min.volume + candle.volume, 
                        current5Min.timestamp
                    );
                } else {
                     current5Min = new SimpleMarketData(
                        current5Min.symbol, 
                        candle.price, // Close updates to latest
                        current5Min.open, 
                        current5Min.high, 
                        Math.min(current5Min.low, candle.low), 
                        current5Min.volume + candle.volume, 
                        current5Min.timestamp
                    );
                }
            }
        }
        // Add last one
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
        
        boolean reachedPartial = false; // Let's define partial as 50% of target
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
        
        return reachedPartial ? 1 : 0; // Time based exit (treat as loss or partial if lucky)
    }

    private static void printFinalReport(List<ComparisonResult> results) {
        System.out.println("\n\n🏆 FINAL 120-DAY COMPARISON RESULTS 🏆");
        System.out.println("--------------------------------------------------------------------------------------------------------");
        System.out.println("| Symbol     | Timeframe| Trades| Full  | Partial | Loss  | Win Rate | Est. Points |");
        System.out.println("|------------|----------|-------|-------|---------|-------|----------|-------------|");
        
        for (ComparisonResult r : results) {
            System.out.println(r.toString());
        }
        System.out.println("--------------------------------------------------------------------------------------------------------");
        
        // Recommendation Logic
        System.out.println("\n💡 RECOMMENDATION:");
        
        double avgWinRate1Min = results.stream().filter(r -> r.timeframe.equals("1minute")).mapToDouble(r -> r.winRate).average().orElse(0);
        double avgWinRate5Min = results.stream().filter(r -> r.timeframe.equals("5minute")).mapToDouble(r -> r.winRate).average().orElse(0);
        
        double totalPoints1Min = results.stream().filter(r -> r.timeframe.equals("1minute")).mapToDouble(r -> r.netPoints).sum();
        double totalPoints5Min = results.stream().filter(r -> r.timeframe.equals("5minute")).mapToDouble(r -> r.netPoints).sum();

        System.out.printf("🔹 1-Minute Avg Win Rate: %.1f%% | Total Net Points: %.0f%n", avgWinRate1Min, totalPoints1Min);
        System.out.printf("🔹 5-Minute Avg Win Rate: %.1f%% | Total Net Points: %.0f%n", avgWinRate5Min, totalPoints5Min);

        if (totalPoints5Min > totalPoints1Min && avgWinRate5Min > avgWinRate1Min) {
            System.out.println("✅ RESULT: The 5-MINUTE chart is SUPERIOR. It provides higher stability and profitability.");
        } else if (totalPoints1Min > totalPoints5Min) {
            System.out.println("✅ RESULT: The 1-MINUTE chart is SUPERIOR. It captures more opportunities and higher total profit.");
        } else {
            System.out.println("⚠️ RESULT: Mixed results. Choose 1-Min for frequency or 5-Min for stability.");
        }
    }
}
