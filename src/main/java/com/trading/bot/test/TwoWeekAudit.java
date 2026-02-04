package com.trading.bot.test;

import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class TwoWeekAudit {

    static class TradeResult {
        String date;
        String symbol;
        String time;
        String direction;
        double confidence;
        String outcome; // "Full Win", "Partial Win", "Loss"
        double entry;
        double target;
        double stopLoss;
    }

    public static void main(String[] args) {
        System.out.println("🗓️ STARTING 2-WEEK DAILY AUDIT...");
        System.out.println("=============================================");

        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        AIPredictor predictor = new AIPredictor();
        predictor.initialize();

        String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};
        
        // Last 14 Days
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(14);
        
        List<TradeResult> allTrades = new ArrayList<>();

        for (String symbol : symbols) {
            System.out.println("\n📊 Fetching Data for: " + symbol);
            
            List<SimpleMarketData> marketData = new ArrayList<>();
            
            // Fetch day by day to handle 1-minute data volume
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                List<SimpleMarketData> dailyData = fetcher.fetchHistoricalCandles(symbol, "1minute", date.toString(), date.toString());
                if (dailyData != null) {
                    marketData.addAll(dailyData);
                }
            }
            
            System.out.println("   Analyzed " + marketData.size() + " candles (1-minute timeframe).");
            
            if (marketData.size() < 50) continue;

            runBacktest(symbol, marketData, predictor, allTrades);
        }
        
        printDailyReport(allTrades);
    }

    private static void runBacktest(String symbol, List<SimpleMarketData> allData, AIPredictor predictor, List<TradeResult> allTrades) {
        int cooldown = 0;

        // Need enough history for indicators (approx 50 candles)
        for (int i = 50; i < allData.size(); i++) {
            if (cooldown > 0) {
                cooldown--;
                continue;
            }

            List<SimpleMarketData> currentHistory = allData.subList(0, i + 1);
            AIPredictor.AIPrediction prediction = predictor.generatePrediction(symbol, currentHistory);

            if (prediction.confidence >= 80) {
                SimpleMarketData currentCandle = allData.get(i);
                
                TradeResult trade = new TradeResult();
                trade.date = currentCandle.timestamp.toLocalDate().toString();
                trade.time = currentCandle.timestamp.toLocalTime().toString();
                trade.symbol = symbol;
                trade.direction = prediction.predictedDirection;
                trade.confidence = prediction.confidence;
                trade.entry = currentCandle.price; // signal at close of candle
                
                // Verify Outcome (Check next 120 candles = 2 hours)
                int outcomeCode = verifyOutcomeDetailed(allData, i, prediction.predictedDirection, prediction.estimatedMovePoints, prediction.suggestedStopLoss);
                
                if (outcomeCode == 2) trade.outcome = "✅ Full Win";
                else if (outcomeCode == 1) trade.outcome = "⚠️ Partial Win";
                else trade.outcome = "❌ Loss";
                
                allTrades.add(trade);
                
                cooldown = 15; // 15-minute cooldown (same as live bot)
            }
        }
    }

    private static int verifyOutcomeDetailed(List<SimpleMarketData> allData, int currentIndex, String direction, double targetPoints, double stopLossPoints) {
        SimpleMarketData entryCandle = allData.get(currentIndex);
        double entryPrice = entryCandle.price; // Entry at close of signal candle
        
        double targetPrice = direction.equals("UP") ? entryPrice + targetPoints : entryPrice - targetPoints;
        double partialTargetPrice = direction.equals("UP") ? entryPrice + (targetPoints * 0.5) : entryPrice - (targetPoints * 0.5);
        double stopLossPrice = direction.equals("UP") ? entryPrice - stopLossPoints : entryPrice + stopLossPoints;
        
        boolean reachedPartial = false;

        // Check next 120 candles (approx 2 hours)
        for (int j = 1; j <= 120 && (currentIndex + j) < allData.size(); j++) {
            SimpleMarketData futureCandle = allData.get(currentIndex + j);
            
            if (direction.equals("UP")) {
                if (futureCandle.high >= targetPrice) return 2; // Full Win
                if (futureCandle.high >= partialTargetPrice) reachedPartial = true;
                if (futureCandle.low <= stopLossPrice) return reachedPartial ? 1 : -1;
            } else {
                // DOWN
                if (futureCandle.low <= targetPrice) return 2; // Full Win
                if (futureCandle.low <= partialTargetPrice) reachedPartial = true;
                if (futureCandle.high >= stopLossPrice) return reachedPartial ? 1 : -1;
            }
        }
        return reachedPartial ? 1 : -1;
    }

    private static void printDailyReport(List<TradeResult> trades) {
        System.out.println("\n📊 DAILY BREAKDOWN REPORT (Last 14 Days)");
        System.out.println("==========================================================================================");
        System.out.println(String.format("%-12s | %-10s | %-6s | %-4s | %-12s | %-15s", "DATE", "SYMBOL", "DIR", "CONF", "RESULT", "TIME"));
        System.out.println("------------------------------------------------------------------------------------------");

        Map<String, List<TradeResult>> tradeByDate = trades.stream()
                .collect(Collectors.groupingBy(t -> t.date, TreeMap::new, Collectors.toList()));

        int grandTotal = 0;
        int grandWins = 0;

        for (Map.Entry<String, List<TradeResult>> entry : tradeByDate.entrySet()) {
            String date = entry.getKey();
            List<TradeResult> dailyTrades = entry.getValue();
            
            int dayWins = 0;
            
            for (TradeResult t : dailyTrades) {
                String dirIcon = t.direction.equals("UP") ? "🟢" : "🔴";
                System.out.println(String.format("%-12s | %-10s | %-6s | %.0f%% | %-12s | %s", 
                    date, t.symbol, dirIcon + " " + t.direction.substring(0,1), t.confidence, t.outcome, t.time));
                
                if (t.outcome.contains("Win")) dayWins++;
            }
            
            double dayWinRate = (double) dayWins / dailyTrades.size() * 100;
            System.out.println(String.format("   👉 Daily Summary: %d Trades, Win Rate: %.1f%%", dailyTrades.size(), dayWinRate));
            System.out.println("------------------------------------------------------------------------------------------");
            
            grandTotal += dailyTrades.size();
            grandWins += dayWins;
        }
        
        if (grandTotal > 0) {
            double grandWinRate = (double) grandWins / grandTotal * 100;
            System.out.println("\n🏆 GRAND TOTAL: " + grandTotal + " Trades");
            System.out.println("🏆 OVERALL WIN RATE: " + String.format("%.2f%%", grandWinRate));
        } else {
            System.out.println("\n❌ No high-confidence signals found in the last 14 days.");
        }
    }
}
