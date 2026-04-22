package com.trading.bot.agents;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.ai.AIPredictor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class CallExtractor {
    public static void main(String[] args) {
        LocalDate targetDate = LocalDate.now();
        System.out.println("🔍 Extracting all calls generated on: " + targetDate);
        System.out.println("--------------------------------------------------");

        AuditAgent auditAgent = new AuditAgent();
        MarketDataAgent marketDataAgent = new MarketDataAgent(HonestMarketDataFetcher.getInstance());
        PredictionAgent predictionAgent = new PredictionAgent();
        String[] symbols = {"NIFTY50", "BANKNIFTY", "SENSEX"};

        for (String symbol : symbols) {
            System.out.println("\n📊 Symbol: " + symbol);
            try {
                // Fetch enough data to include the target date
                List<SimpleMarketData> data = marketDataAgent.getHistoricalData(symbol, 10);
                if (data == null || data.isEmpty()) {
                    System.out.println("   ⚠️ No data found for " + symbol);
                    continue;
                }

                long lastSignalTime = 0;
                int callsCount = 0;

                for (int i = 200; i < data.size(); i++) {
                    SimpleMarketData latest = data.get(i);
                    LocalDateTime currentTimestamp = latest.timestamp;

                    // Only process target date
                    if (!currentTimestamp.toLocalDate().equals(targetDate)) continue;

                    java.time.LocalTime time = currentTimestamp.toLocalTime();
                    if (time.isBefore(java.time.LocalTime.of(9, 15)) || time.isAfter(java.time.LocalTime.of(15, 25))) continue;

                    // Reuse the exact same logic as AuditAgent for cooldown and gating
                    int slab = getTimeSlab(time);
                    long cooldownMillis = getCooldownMillis(symbol, slab);
                    long currentMillis = currentTimestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    
                    if (currentMillis - lastSignalTime < cooldownMillis) continue;

                    List<SimpleMarketData> history = data.subList(0, i + 1);
                    AIPredictor.AIPrediction prediction = predictionAgent.generateSignal(symbol, history);

                    if (passesGates(symbol, slab, prediction)) {
                        callsCount++;
                        lastSignalTime = currentMillis;
                        
                        double entryPrice = latest.price;
                        double targetPoints = prediction.estimatedMovePoints;
                        double targetPrice = prediction.predictedDirection.equals("UP") ? entryPrice + targetPoints : entryPrice - targetPoints;
                        String arrow = prediction.predictedDirection.equals("UP") ? "⬆️" : "⬇️";
                        String signalEmoji = prediction.predictedDirection.equals("UP") ? "🟢" : "🔴";

                        System.out.printf("%s **CALL DETECTED** [%s]\n", signalEmoji, currentTimestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                        System.out.println("   🚀 Direction: " + prediction.predictedDirection + " " + arrow);
                        System.out.println("   💰 Current Market Price (LTP): " + String.format("%.2f", entryPrice));
                        System.out.println("   💰 Entry Price (Signal): " + String.format("%.2f", entryPrice));
                        System.out.println("   🎯 Target: " + String.format("%.2f", targetPrice) + " (+" + String.format("%.0f", targetPoints) + " pts)");
                        System.out.println("   🛡️ SL: " + String.format("%.0f", prediction.suggestedStopLoss) + " pts");
                        System.out.println("   🤖 Confidence: " + String.format("%.1f%%", prediction.confidence));
                        System.out.println("   📝 Reasoning: " + prediction.predictionReasoning);
                        System.out.println("   -------------------------------------------");
                    }
                }
                if (callsCount == 0) System.out.println("   ℹ️ No calls generated for " + symbol + " on this date.");
            } catch (Exception e) {
                System.err.println("   ❌ Error processing " + symbol + ": " + e.getMessage());
            }
        }
    }

    // Helper methods copied from AuditAgent to ensure EXACT same logic
    private static long getCooldownMillis(String symbol, int slab) {
        return 10L * 60 * 1000;
    }

    private static boolean passesGates(String symbol, int slab, AIPredictor.AIPrediction p) {
        double minConf = 85;
        double minPts = switch (symbol) {
            case "NIFTY50" -> 35.0;
            case "SENSEX" -> 100.0;
            default -> 20.0;
        };
        if (p.predictedDirection.equals("NEUTRAL")) return false;
        return p.confidence >= minConf && p.estimatedMovePoints >= minPts;
    }

    private static int getTimeSlab(java.time.LocalTime time) {
        if (time.isBefore(java.time.LocalTime.of(11, 0))) return 0;
        if (time.isBefore(java.time.LocalTime.of(13, 0))) return 1;
        return 2;
    }
}
