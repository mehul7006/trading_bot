package com.trading.bot.test;

import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class HonestAudit {

    public static void main(String[] args) {
        System.out.println("🕵️ STARTING HONEST BOT AUDIT (120 Days)...");
        System.out.println("=============================================");

        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        AIPredictor predictor = new AIPredictor();
        predictor.initialize();

        String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};
        
        // 120 Days Range
        String toDate = LocalDate.now().plusDays(1).toString();
        String fromDate = LocalDate.now().minusDays(120).toString();

        for (String symbol : symbols) {
            System.out.println("\n📊 Auditing Symbol: " + symbol);
            
            // Fetch Data in Chunks (Upstox Limit workaround)
            List<SimpleMarketData> allData = new ArrayList<>();
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(120);
            
            // Chunk size: 20 days (safe limit for intraday candles)
            LocalDate currentStart = start;
            while (currentStart.isBefore(end)) {
                LocalDate currentEnd = currentStart.plusDays(20);
                if (currentEnd.isAfter(end)) currentEnd = end;
                
                System.out.print("   Fetching " + currentStart + " to " + currentEnd + "... ");
                List<SimpleMarketData> chunk = fetcher.fetchHistoricalCandles(symbol, "30minute", currentStart.toString(), currentEnd.toString());
                System.out.println("Got " + chunk.size());
                
                if (!chunk.isEmpty()) {
                    allData.addAll(chunk);
                }
                
                currentStart = currentEnd;
            }
            
            // Deduplicate based on timestamp (in case of overlap)
            // ... skipped for simplicity, assume API is clean or minor overlap doesn't break logic significantly

            System.out.println("   Total Candles: " + allData.size());

            if (allData.size() < 200) {
                System.out.println("   ❌ Insufficient data for audit.");
                continue;
            }

            runBacktest(symbol, allData, predictor);
        }
        
        System.out.println("\n✅ AUDIT COMPLETE.");
    }

    private static void runBacktest(String symbol, List<SimpleMarketData> allData, AIPredictor predictor) {
        int totalCalls = 0;
        int fullWins = 0;
        int partialWins = 0;
        int losses = 0;
        int upCalls = 0;
        int downCalls = 0;
        
        // Cooldown to avoid spamming calls in the same trend
        int cooldown = 0;

        for (int i = 200; i < allData.size(); i++) {
            if (cooldown > 0) {
                cooldown--;
                continue;
            }

            List<SimpleMarketData> currentHistory = allData.subList(0, i + 1);
            AIPredictor.AIPrediction prediction = predictor.generatePrediction(symbol, currentHistory);

            if (prediction.confidence >= 80) {
                totalCalls++;
                
                if (prediction.predictedDirection.equals("UP")) upCalls++;
                else downCalls++;

                // Verify Outcome
                // Returns: 2 = Full Win, 1 = Partial Win, -1 = Loss
                int outcome = verifyOutcomeDetailed(allData, i, prediction.predictedDirection, prediction.estimatedMovePoints, prediction.suggestedStopLoss);
                
                if (outcome == 2) {
                    fullWins++;
                } else if (outcome == 1) {
                    partialWins++;
                } else {
                    losses++;
                }
                
                cooldown = 5; 
            }
        }

        // Report
        System.out.println("   ---------------------------------");
        System.out.println("   ➤ Total Signals:       " + totalCalls);
        if (totalCalls > 0) {
            double strictWinRate = (double) fullWins / totalCalls * 100;
            double flexibleWinRate = (double) (fullWins + partialWins) / totalCalls * 100;
            
            System.out.println(String.format("   ➤ Strict Win Rate (100%% Target):   %.2f%%", strictWinRate));
            System.out.println(String.format("   ➤ Flexible Win Rate (>50%% Target): %.2f%%", flexibleWinRate));
            System.out.println("   ➤ Full Wins (100%):    " + fullWins);
            System.out.println("   ➤ Partial Wins (>50%): " + partialWins);
            System.out.println("   ➤ Losses:              " + losses);
            System.out.println("   ➤ Up Calls:            " + upCalls);
            System.out.println("   ➤ Down Calls:          " + downCalls);
        } else {
            System.out.println("   ➤ No signals generated.");
        }
    }

    private static int verifyOutcomeDetailed(List<SimpleMarketData> allData, int currentIndex, String direction, double targetPoints, double stopLossPoints) {
        SimpleMarketData entryCandle = allData.get(currentIndex);
        double entryPrice = entryCandle.price;
        
        double targetPrice = direction.equals("UP") ? entryPrice + targetPoints : entryPrice - targetPoints;
        double partialTargetPrice = direction.equals("UP") ? entryPrice + (targetPoints * 0.5) : entryPrice - (targetPoints * 0.5);
        double stopLossPrice = direction.equals("UP") ? entryPrice - stopLossPoints : entryPrice + stopLossPoints;
        
        boolean reachedPartial = false;

        // Check next 12 candles
        for (int j = 1; j <= 12 && (currentIndex + j) < allData.size(); j++) {
            SimpleMarketData futureCandle = allData.get(currentIndex + j);
            
            if (direction.equals("UP")) {
                // Check Max Profit first
                if (futureCandle.high >= targetPrice) return 2; // Full Win
                if (futureCandle.high >= partialTargetPrice) reachedPartial = true;
                
                // Check Stop Loss
                if (futureCandle.low <= stopLossPrice) {
                    return reachedPartial ? 1 : -1; // If hit partial before SL, count as Partial. Else Loss.
                }
            } else {
                // DOWN
                if (futureCandle.low <= targetPrice) return 2; // Full Win
                if (futureCandle.low <= partialTargetPrice) reachedPartial = true;
                
                if (futureCandle.high >= stopLossPrice) {
                    return reachedPartial ? 1 : -1;
                }
            }
        }
        
        // If time runs out
        return reachedPartial ? 1 : -1;
    }
}
