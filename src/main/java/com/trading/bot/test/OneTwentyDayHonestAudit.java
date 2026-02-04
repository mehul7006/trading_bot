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
        
        String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};
        
        List<AuditResult> results = new ArrayList<>();

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(120); // 120 Days

        for (String symbol : symbols) {
            System.out.println("\n======================================================================================");
            System.out.println("📊 Processing Symbol: " + symbol);
            System.out.println("======================================================================================");
            
            // 1. Fetch 1-Minute Data (Base Data)
            System.out.print("   Fetching Data... ");
            List<SimpleMarketData> data1Min = new ArrayList<>();
            
            // Fetch in 5-day chunks to avoid timeouts
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(5)) {
                LocalDate chunkEnd = date.plusDays(4);
                if (chunkEnd.isAfter(end)) chunkEnd = end;
                
                List<SimpleMarketData> chunk = fetcher.fetchHistoricalCandles(symbol, "1minute", date.toString(), chunkEnd.toString());
                if (chunk != null) {
                    data1Min.addAll(chunk);
                } else {
                     System.out.print("X");
                }
                System.out.print(".");
                try { Thread.sleep(200); } catch (Exception e) {}
            }
            System.out.println(" Done! (" + data1Min.size() + " raw candles)");

            if (data1Min.size() < 200) {
                System.out.println("⚠️ Not enough data for " + symbol + " (Got " + data1Min.size() + ")");
                continue;
            }

            // 2. Resample to 5-Minute Data (Exact logic used in Live Bot)
            System.out.print("   Resampling to 5-Minute Data... ");
            List<SimpleMarketData> data5Min = resampleTo5Minute(data1Min);
            System.out.println(" Done! (" + data5Min.size() + " candles)");

            // 3. Run Backtest on 5-Minute Data
            System.out.println("   ▶ Running Strategy Audit...");
            results.add(runBacktest(symbol, data5Min, predictor));
        }
        
        // Final Report
        printFinalReport(results);
    }

    private static AuditResult runBacktest(String symbol, List<SimpleMarketData> data, AIPredictor predictor) {
        AuditResult res = new AuditResult();
        res.symbol = symbol;
        
        int cooldown = 0;
        int cooldownCandles = 3; // 15 minutes on 5-min chart

        // Start from index 200 to allow indicators to warm up
        for (int i = 200; i < data.size(); i++) {
            if (cooldown > 0) {
                cooldown--;
                continue;
            }

            // Slice data for prediction (simulate real-time)
            List<SimpleMarketData> history = data.subList(0, i + 1);
            AIPredictor.AIPrediction prediction = predictor.generatePrediction(symbol, history);
            
            // Filter 1: Minimum Points (Same as Live Bot)
            boolean isSignificant = checkMinimumPoints(symbol, prediction.estimatedMovePoints);
            
            // Filter 2: High Confidence > 80% (Same as Live Bot)
            if (isSignificant && prediction.confidence >= 80) {
                res.totalTrades++;
                
                // Verify Outcome (Check next 24 candles = 2 hours)
                int lookAhead = 24; 
                int outcome = verifyOutcome(data, i, prediction, lookAhead);
                
                if (outcome == 2) {
                    res.fullWins++;
                    res.netPoints += prediction.estimatedMovePoints;
                } else if (outcome == 1) {
                    res.partialWins++;
                    res.netPoints += (prediction.estimatedMovePoints * 0.3);
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
    
    // Exact logic from Phase3TelegramBot.java
    private static boolean checkMinimumPoints(String symbol, double estimatedPoints) {
        double minPoints = switch (symbol) {
            case "NIFTY50" -> 30.0;
            case "SENSEX" -> 100.0;
            case "BANKNIFTY" -> 60.0;
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
                current5Min = new SimpleMarketData(
                    current5Min.symbol, 
                    candle.price, // Close updates to latest
                    current5Min.open, 
                    Math.max(current5Min.high, candle.high), 
                    Math.min(current5Min.low, candle.low), 
                    current5Min.volume + candle.volume, 
                    current5Min.timestamp
                );
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
        System.out.println("\n\n🏆 FINAL 120-DAY BOT AUDIT RESULTS (Live Strategy) 🏆");
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
        System.out.println("✅ Note: These results reflect the EXACT logic currently running in the bot.");
        System.out.println("   - Data: 120 Days of 5-Minute Resampled Data");
        System.out.println("   - Filter: >80% Confidence + Min Points (Nifty 30, etc.)");
        System.out.println("   - Cooldown: 15 Minutes after signal");
    }
}