package com.trading.bot.test;

import com.trading.bot.ai.AIPredictor;
import com.trading.bot.ai.AIPredictor.AIPrediction;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BacktestRunner {

    public static void main(String[] args) {
        System.out.println("🚀 STARTING COMPREHENSIVE BACKTEST (Last 120 Days)...");
        
        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};

        for (String symbol : symbols) {
            runBacktestForSymbol(symbol, fetcher);
            try { Thread.sleep(2000); } catch (InterruptedException e) {} // Pause to allow API cooldown
        }
    }

    private static void runBacktestForSymbol(String symbol, HonestMarketDataFetcher fetcher) {
        AIPredictor predictor = new AIPredictor();
        
        // 1. Fetch Data (Enough history for indicators)
        System.out.println("\n📥 Fetching data for " + symbol + " (History needed for indicators)...");
        
        List<SimpleMarketData> allData = new ArrayList<>();
        
        // Chunk 1: Last 150 Days (Covers 120 day test + history)
        String toDate1 = LocalDate.now().plusDays(1).toString();
        String fromDate1 = LocalDate.now().minusDays(150).toString();
        List<SimpleMarketData> chunk1 = fetcher.fetchHistoricalCandles(symbol, "30minute", fromDate1, toDate1);
        allData.addAll(chunk1);
        
        // Sort by timestamp
        allData.sort((d1, d2) -> d1.timestamp.compareTo(d2.timestamp));
        
        // Target Test Start Date: Last 120 Days
        LocalDate testStartDate = LocalDate.now().minusDays(120);
        
        if (allData.isEmpty()) {
            System.err.println("❌ Failed to fetch data for " + symbol + ". Skipping.");
            return;
        }
        
        System.out.println("📊 Total Candles Fetched: " + allData.size());
        if (!allData.isEmpty()) {
            System.out.println("🗓️ Data Range: " + allData.get(0).timestamp + " to " + allData.get(allData.size()-1).timestamp);
        }
        
        // 2. Run Simulation
        int watchlistSignals = 0;
        int strongSignals = 0;
        int strongWins = 0;
        int strongLosses = 0;
        double totalPointsCaptured = 0;
        
        // Minimum history required for indicators (200 EMA needs 200 candles)
        int minHistory = 200;
        
        // Cooldown to avoid duplicate signals for the same move
        int cooldown = 0;
        
        // Determine Min Points based on Symbol
        double minPointsThreshold = switch(symbol) {
            case "SENSEX" -> 100.0;
            case "BANKNIFTY" -> 60.0;
            default -> 20.0; // NIFTY
        };

        
        for (int i = minHistory; i < allData.size(); i++) { // Check ALL candles
            SimpleMarketData currentCandle = allData.get(i);
            
            // FILTER: Only check signals from testStartDate
            if (currentCandle.timestamp.toLocalDate().isBefore(testStartDate)) {
                continue;
            }
            
            if (cooldown > 0) {
                cooldown--;
                continue;
            }
            
            // Snapshot of data available at time 'i'
            List<SimpleMarketData> currentData = allData.subList(0, i + 1);
            
            try {
                // Generate Prediction
                AIPrediction prediction = predictor.generatePrediction(symbol, currentData);
                
                boolean isSignificantMove = prediction.estimatedMovePoints > minPointsThreshold;
                
                if (!isSignificantMove || prediction.predictedDirection.equals("NEUTRAL")) {
                    continue;
                }
                
                // 1. WATCHLIST SIGNAL (65% - 84%)
                if (prediction.confidence > 65 && prediction.confidence < 85) {
                    watchlistSignals++;
                    // System.out.printf("👀 [%s] WATCHLIST: %s | Conf: %.1f%% | Reason: %s%n", 
                    //    currentCandle.timestamp, prediction.predictedDirection, prediction.confidence, prediction.predictionReasoning);
                }
                
                // 2. STRONG SIGNAL (>= 85%)
                if (prediction.confidence >= 85) {
                    strongSignals++;
                    
                    // Verify Outcome using AIPredictor's suggested values
                    double targetPts = prediction.estimatedMovePoints; 
                    double stopLossPts = prediction.suggestedStopLoss;
                    
                    int result = verifyOutcome(allData, i, prediction.predictedDirection, targetPts, stopLossPts);
                    
                    if (result == 1) {
                        strongWins++;
                        totalPointsCaptured += targetPts;
                        System.out.printf("✅ [%s] STRONG SIGNAL: %s | Conf: %.1f%% | Target: %.1f | SL: %.1f | Result: WIN | Reason: %s%n", 
                            currentCandle.timestamp, prediction.predictedDirection, prediction.confidence, targetPts, stopLossPts, prediction.predictionReasoning);
                    } else if (result == -1) {
                        strongLosses++;
                        totalPointsCaptured -= stopLossPts;
                        System.out.printf("❌ [%s] STRONG SIGNAL: %s | Conf: %.1f%% | Target: %.1f | SL: %.1f | Result: LOSS | Reason: %s%n", 
                            currentCandle.timestamp, prediction.predictedDirection, prediction.confidence, targetPts, stopLossPts, prediction.predictionReasoning);
                    } else {
                        // Breakeven / Time Expired
                        // System.out.printf("➖ [%s] STRONG SIGNAL: %s | Conf: %.1f%% | Result: EXPIRED | Reason: %s%n", 
                        //    currentCandle.timestamp, prediction.predictedDirection, prediction.confidence, prediction.predictionReasoning);
                    }
                    
                    // Set cooldown for strong signals
                    cooldown = 6; 
                }
                
            } catch (Exception e) {
                // Ignore errors in loop
            }
        }
        
        // 3. Report Results
        System.out.println("===========================================");
        System.out.println("🚀 120-DAY PERFORMANCE AUDIT: " + symbol);
        System.out.println("===========================================");
        System.out.println("👀 Watchlist Opportunities: " + watchlistSignals);
        System.out.println("🚀 Strong Signals (Tradeable): " + strongSignals);
        System.out.println("✅ Wins: " + strongWins);
        System.out.println("❌ Losses: " + strongLosses);
        
        int definedOutcomes = strongWins + strongLosses;
        double accuracy = definedOutcomes > 0 ? (double) strongWins / definedOutcomes * 100 : 0;
        System.out.printf("🎯 REAL WIN RATE: %.2f%%%n", accuracy);
        System.out.printf("💰 Total Points Captured: %.2f%n", totalPointsCaptured);
        System.out.println("===========================================");
    }
    
    // Returns 1 for WIN, -1 for LOSS, 0 for NEUTRAL
    private static int verifyOutcome(List<SimpleMarketData> allData, int currentIndex, String direction, double targetPoints, double stopLossPoints) {
        SimpleMarketData entryCandle = allData.get(currentIndex);
        double entryPrice = entryCandle.price; // Close price
        double targetPrice = direction.equals("UP") ? entryPrice + targetPoints : entryPrice - targetPoints;
        double stopLossPrice = direction.equals("UP") ? entryPrice - stopLossPoints : entryPrice + stopLossPoints;
        
        // Check next 12 candles (1 hour)
        for (int j = 1; j <= 12 && (currentIndex + j) < allData.size(); j++) {
            SimpleMarketData futureCandle = allData.get(currentIndex + j);
            
            if (direction.equals("UP")) {
                if (futureCandle.high >= targetPrice) return 1; // Target Hit
                if (futureCandle.low <= stopLossPrice) return -1;    // Stop Loss Hit
            } else { // DOWN
                if (futureCandle.low <= targetPrice) return 1;  // Target Hit
                if (futureCandle.high >= stopLossPrice) return -1;   // Stop Loss Hit
            }
        }
        
        return 0; // Time expired without hitting target or SL
    }
}
