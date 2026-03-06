package com.trading.bot.test;

import com.trading.bot.ai.AIPredictor;
import com.trading.bot.ai.AIPredictor.AIPrediction;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class OneOffBacktest {

    public static void main(String[] args) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("audit_results.txt"))) {
            writer.println("🚀 STARTING COMPREHENSIVE BACKTEST (Last 120 Days)...");
            System.out.println("🚀 STARTING COMPREHENSIVE BACKTEST (Last 120 Days)...");
            
            HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
            String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};

            for (String symbol : symbols) {
                runBacktestForSymbol(symbol, fetcher, writer);
                try { Thread.sleep(2000); } catch (InterruptedException e) {} 
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runBacktestForSymbol(String symbol, HonestMarketDataFetcher fetcher, PrintWriter writer) {
        AIPredictor predictor = new AIPredictor();
        
        writer.println("\n📥 Fetching data for " + symbol + " (History needed for indicators)...");
        System.out.println("📥 Fetching data for " + symbol);
        
        List<SimpleMarketData> allData = new ArrayList<>();
        
        // Fetch data in 30-day chunks to avoid API limits
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(150);
        
        LocalDate currentStart = startDate;
        while (currentStart.isBefore(endDate)) {
            LocalDate currentEnd = currentStart.plusDays(30);
            if (currentEnd.isAfter(endDate)) currentEnd = endDate;
            
            System.out.println("   Fetching chunk: " + currentStart + " to " + currentEnd);
            List<SimpleMarketData> chunk = fetcher.fetchHistoricalCandles(symbol, "30minute", currentStart.toString(), currentEnd.toString());
            if (chunk != null) {
                allData.addAll(chunk);
            }
            try { Thread.sleep(500); } catch (InterruptedException e) {} // Rate limit safety
            currentStart = currentEnd.plusDays(1);
        }
        
        allData.sort((d1, d2) -> d1.timestamp.compareTo(d2.timestamp));
        
        LocalDate testStartDate = LocalDate.now().minusDays(120);
        
        if (allData.isEmpty()) {
            System.out.println("❌ No data fetched for " + symbol);
            writer.println("❌ " + symbol + ": Failed to fetch historical data (Upstox API Error)");
            return;
        }
        
        writer.println("📊 Total Candles Fetched: " + allData.size());
        if (!allData.isEmpty()) {
            writer.println("🗓️ Data Range: " + allData.get(0).timestamp + " to " + allData.get(allData.size()-1).timestamp);
        }
        
        int watchlistSignals = 0;
        int strongSignals = 0;
        int strongWins = 0;
        int strongLosses = 0;
        double totalPointsCaptured = 0;
        
        int minHistory = 200;
        int cooldown = 0;
        
        double minPointsThreshold = switch(symbol) {
            case "SENSEX" -> 100.0;
            case "BANKNIFTY" -> 60.0;
            default -> 20.0; 
        };

        for (int i = minHistory; i < allData.size(); i++) {
            SimpleMarketData currentCandle = allData.get(i);
            
            if (currentCandle.timestamp.toLocalDate().isBefore(testStartDate)) {
                continue;
            }
            
            if (cooldown > 0) {
                cooldown--;
                continue;
            }
            
            List<SimpleMarketData> currentData = allData.subList(0, i + 1);
            
            try {
                AIPrediction prediction = predictor.generatePrediction(symbol, currentData);
                boolean isSignificantMove = prediction.estimatedMovePoints > minPointsThreshold;
                
                if (!isSignificantMove || prediction.predictedDirection.equals("NEUTRAL")) {
                    continue;
                }
                
                if (prediction.confidence > 65 && prediction.confidence < 85) {
                    watchlistSignals++;
                }
                
                if (prediction.confidence >= 85) {
                    strongSignals++;
                    
                    double targetPts = prediction.estimatedMovePoints; 
                    double stopLossPts = prediction.suggestedStopLoss;
                    
                    int result = verifyOutcome(allData, i, prediction.predictedDirection, targetPts, stopLossPts);
                    
                    if (result == 1) {
                        strongWins++;
                        totalPointsCaptured += targetPts;
                        writer.printf("✅ [%s] STRONG SIGNAL: %s | Conf: %.1f%% | Target: %.1f | SL: %.1f | Result: WIN | Reason: %s%n", 
                            currentCandle.timestamp, prediction.predictedDirection, prediction.confidence, targetPts, stopLossPts, prediction.predictionReasoning);
                    } else if (result == -1) {
                        strongLosses++;
                        totalPointsCaptured -= stopLossPts;
                        writer.printf("❌ [%s] STRONG SIGNAL: %s | Conf: %.1f%% | Target: %.1f | SL: %.1f | Result: LOSS | Reason: %s%n", 
                            currentCandle.timestamp, prediction.predictedDirection, prediction.confidence, targetPts, stopLossPts, prediction.predictionReasoning);
                    }
                    
                    cooldown = 6; 
                }
                
            } catch (Exception e) {
            }
        }
        
        writer.println("===========================================");
        writer.println("🚀 120-DAY PERFORMANCE AUDIT: " + symbol);
        writer.println("===========================================");
        writer.println("👀 Watchlist Opportunities: " + watchlistSignals);
        writer.println("🚀 Strong Signals (Tradeable): " + strongSignals);
        writer.println("✅ Wins: " + strongWins);
        writer.println("❌ Losses: " + strongLosses);
        
        int definedOutcomes = strongWins + strongLosses;
        double accuracy = definedOutcomes > 0 ? (double) strongWins / definedOutcomes * 100 : 0;
        writer.printf("🎯 REAL WIN RATE: %.2f%%%n", accuracy);
        writer.printf("💰 Total Points Captured: %.2f pts%n", totalPointsCaptured);
        writer.println("===========================================");
        writer.flush();
    }
    
    private static int verifyOutcome(List<SimpleMarketData> allData, int currentIndex, String direction, double targetPoints, double stopLossPoints) {
        SimpleMarketData entryCandle = allData.get(currentIndex);
        double entryPrice = entryCandle.price; 
        double targetPrice = direction.equals("UP") ? entryPrice + targetPoints : entryPrice - targetPoints;
        double stopLossPrice = direction.equals("UP") ? entryPrice - stopLossPoints : entryPrice + stopLossPoints;
        
        for (int j = 1; j <= 12 && (currentIndex + j) < allData.size(); j++) {
            SimpleMarketData futureCandle = allData.get(currentIndex + j);
            
            if (direction.equals("UP")) {
                if (futureCandle.high >= targetPrice) return 1; 
                if (futureCandle.low <= stopLossPrice) return -1;    
            } else { 
                if (futureCandle.low <= targetPrice) return 1;  
                if (futureCandle.high >= stopLossPrice) return -1;   
            }
        }
        return 0; 
    }
}
