package com.trading.bot.test;

import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class StabilityAudit {

    static class MonthStats {
        int total = 0;
        int strictWins = 0;
        int flexibleWins = 0;
    }

    public static void main(String[] args) {
        System.out.println("🕵️ STARTING STABILITY & CONSISTENCY CHECK (120 Days)...");
        System.out.println("==========================================================");

        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        AIPredictor predictor = new AIPredictor();
        predictor.initialize();

        String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};
        
        // 120 Days Range
        String toDate = LocalDate.now().plusDays(1).toString();
        // Go back a bit more to ensure full months coverage
        String fromDate = LocalDate.now().minusDays(130).toString();

        for (String symbol : symbols) {
            System.out.println("\n📊 Auditing Symbol: " + symbol);
            
            List<SimpleMarketData> allData = new ArrayList<>();
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(120);
            
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
            
            if (allData.size() < 200) {
                System.out.println("   ❌ Insufficient data for audit.");
                continue;
            }

            runStabilityBacktest(symbol, allData, predictor);
        }
        
        System.out.println("\n✅ STABILITY CHECK COMPLETE.");
    }

    private static void runStabilityBacktest(String symbol, List<SimpleMarketData> allData, AIPredictor predictor) {
        Map<String, MonthStats> monthlyStats = new TreeMap<>();
        int totalCalls = 0;
        
        int cooldown = 0;

        for (int i = 200; i < allData.size(); i++) {
            if (cooldown > 0) {
                cooldown--;
                continue;
            }

            List<SimpleMarketData> currentHistory = allData.subList(0, i + 1);
            SimpleMarketData currentCandle = allData.get(i);
            AIPredictor.AIPrediction prediction = predictor.generatePrediction(symbol, currentHistory);

            if (prediction.confidence >= 80) {
                totalCalls++;
                
                String monthKey = currentCandle.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                monthlyStats.putIfAbsent(monthKey, new MonthStats());
                MonthStats stats = monthlyStats.get(monthKey);
                
                stats.total++;

                int outcome = verifyOutcomeDetailed(allData, i, prediction.predictedDirection, prediction.estimatedMovePoints, prediction.suggestedStopLoss);
                
                if (outcome == 2) { // Full Win
                    stats.strictWins++;
                    stats.flexibleWins++;
                } else if (outcome == 1) { // Partial Win
                    stats.flexibleWins++;
                }
                
                cooldown = 5; 
            }
        }

        // Report Table
        System.out.println("\n   📅 MONTHLY PERFORMANCE BREAKDOWN:");
        System.out.println("   +---------+-------+----------------+------------------+");
        System.out.println("   |  MONTH  | CALLS | STRICT WIN (%) | FLEXIBLE WIN (%) |");
        System.out.println("   +---------+-------+----------------+------------------+");
        
        for (Map.Entry<String, MonthStats> entry : monthlyStats.entrySet()) {
            String month = entry.getKey();
            MonthStats s = entry.getValue();
            
            double strictRate = s.total > 0 ? (double) s.strictWins / s.total * 100 : 0;
            double flexibleRate = s.total > 0 ? (double) s.flexibleWins / s.total * 100 : 0;
            
            System.out.println(String.format("   | %s |  %3d  |     %5.1f%%     |      %5.1f%%      |", 
                month, s.total, strictRate, flexibleRate));
        }
        System.out.println("   +---------+-------+----------------+------------------+");
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
                if (futureCandle.high >= targetPrice) return 2; 
                if (futureCandle.high >= partialTargetPrice) reachedPartial = true;
                if (futureCandle.low <= stopLossPrice) return reachedPartial ? 1 : -1;
            } else {
                if (futureCandle.low <= targetPrice) return 2;
                if (futureCandle.low <= partialTargetPrice) reachedPartial = true;
                if (futureCandle.high >= stopLossPrice) return reachedPartial ? 1 : -1;
            }
        }
        return reachedPartial ? 1 : -1;
    }
}
