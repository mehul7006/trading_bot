package com.trading.bot.test;

import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

public class ManualScanRunner {

    public static void main(String[] args) {
        System.out.println("🚀 STARTING MANUAL MARKET SCAN FOR SPECIAL SESSION");
        System.out.println("==================================================");
        System.out.println("📅 Date: " + LocalDateTime.now());
        System.out.println("🔍 Scanning NIFTY50, BANKNIFTY, SENSEX...");
        System.out.println("==================================================\n");

        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        AIPredictor predictor = new AIPredictor();
        predictor.initialize();

        String[] symbols = {"NIFTY50", "BANKNIFTY", "SENSEX"};

        for (String symbol : symbols) {
            System.out.println("👉 Scanning " + symbol + "...");
            try {
                // Fetch Data
                List<SimpleMarketData> data = fetcher.getRealMarketData(symbol);
                
                if (data.isEmpty()) {
                    System.out.println("❌ No data found for " + symbol);
                    continue;
                }
                
                SimpleMarketData latest = data.get(data.size() - 1);
                System.out.println("   📊 Price: " + latest.price + " (Time: " + latest.timestamp + ")");
                
                // Predict
                AIPredictor.AIPrediction prediction = predictor.generatePrediction(symbol, data);
                
                // Display Result
                System.out.println("   🔮 Prediction: " + prediction.predictedDirection);
                System.out.println("   🎯 Confidence: " + prediction.confidence + "%");
                System.out.println("   🌊 Trend: " + (prediction.neuralNetworkScore > 50 ? "BULLISH" : "BEARISH") + " (Strength: " + prediction.neuralNetworkScore + ")");
                System.out.println("   📏 Est. Move: " + String.format("%.2f", prediction.estimatedMovePoints) + " pts");
                System.out.println("   💡 Reasoning: " + prediction.predictionReasoning);
                
                if (prediction.confidence > 60) {
                    System.out.println("   🔥 SIGNAL DETECTED!");
                } else {
                    System.out.println("   ⚠️ No High Confidence Signal");
                }
                System.out.println("--------------------------------------------------");

            } catch (Exception e) {
                System.out.println("❌ Error analyzing " + symbol + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\n✅ Scan Complete.");
    }
}
