package com.trading.bot.test;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.ai.AIPredictor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Audit Tool to Check Calls Generated TODAY (Specific Date)
 */
public class TodayAudit {

    public static void main(String[] args) {
        System.out.println("📅 STARTING TODAY'S MARKET AUDIT");
        System.out.println("--------------------------------------------------");
        System.out.println("Fetching REAL DATA from Upstox for TODAY...");

        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        AIPredictor predictor = new AIPredictor();
        
        String[] symbols = {"NIFTY50", "SENSEX"};
        LocalDate today = LocalDate.now();
        // today = LocalDate.of(2025, 10, 27); // Uncomment to test specific past date if market is closed today
        
        // Fetch a bit of history (2 days) to allow indicators to warm up, but only output TODAY's calls
        LocalDate start = today.minusDays(2); 

        for (String symbol : symbols) {
            System.out.println("\n🔍 Analyzing Symbol: " + symbol);
            
            // 1. Fetch Data
            // Fetch until tomorrow to include all of today's data
            List<SimpleMarketData> data1Min = fetcher.fetchHistoricalCandles(symbol, "1minute", start.toString(), today.plusDays(1).toString());
            
            if (data1Min == null || data1Min.isEmpty()) {
                System.out.println("   ⚠️ No data found for today.");
                continue;
            }
            
            System.out.println("   📊 Data Range: " + data1Min.get(0).timestamp + " to " + data1Min.get(data1Min.size() - 1).timestamp);

            // 2. Resample to 5-Minute
            List<SimpleMarketData> data5Min = resampleTo5Minute(data1Min);
            
            // 3. Run Simulation
            int callsToday = 0;
            int cooldown = 0;
            
            // Start iterating. We need at least 200 candles for indicators
            for (int i = 200; i < data5Min.size(); i++) {
                SimpleMarketData candle = data5Min.get(i);
                
                // Only look at TODAY's candles for output
                boolean isToday = candle.timestamp.toLocalDate().isEqual(today);
                
                if (cooldown > 0) {
                    cooldown--;
                    continue;
                }

                // Predict
                List<SimpleMarketData> history = data5Min.subList(0, i + 1);
                AIPredictor.AIPrediction prediction = predictor.generatePrediction(symbol, history);
                
                // Check Filters (Same as Live Bot)
                boolean isSignificant = checkMinimumPoints(symbol, prediction.estimatedMovePoints);
                
                if (isSignificant && prediction.confidence >= 80) {
                    if (isToday) {
                        callsToday++;
                        System.out.println("   ⏰ " + candle.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")) + 
                                           " | " + (prediction.predictedDirection.equals("UP") ? "🟢 BUY " : "🔴 SELL") + 
                                           " | Conf: " + String.format("%.1f%%", prediction.confidence) + 
                                           " | Target: " + String.format("%.0f", prediction.estimatedMovePoints) + " pts");
                    }
                    cooldown = 3; // 15 min cooldown
                }
            }
            
            if (callsToday == 0) {
                System.out.println("   ℹ️ No confirmed calls generated today.");
            } else {
                System.out.println("   ✅ Total Calls Today: " + callsToday);
            }
        }
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
                current5Min = new SimpleMarketData(current5Min.symbol, candle.price, current5Min.open, Math.max(current5Min.high, candle.high), Math.min(current5Min.low, candle.low), current5Min.volume + candle.volume, current5Min.timestamp);
            }
        }
        if (current5Min != null) data5Min.add(current5Min);
        return data5Min;
    }
}
