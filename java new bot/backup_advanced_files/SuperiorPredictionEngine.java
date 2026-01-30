package com.stockbot;

import java.util.*;

/**
 * Superior Prediction Engine - REAL LOGIC ONLY
 * Replaced fake AI with honest SMA Crossover Strategy.
 * 
 * AUDIT UPDATE:
 * - Removed simulated Random Forest (fake logic)
 * - Removed simulated Neural Network (fake logic)
 * - Implemented real Simple Moving Average (SMA) Crossover
 */
public class SuperiorPredictionEngine {

    // Simple Data Structure (Inner class to ensure compilation if missing)
    public static class IndexData {
        public double close;
        public long timestamp;
        
        public IndexData(double close, long timestamp) {
            this.close = close;
            this.timestamp = timestamp;
        }
    }

    public static class EnhancedPrediction {
        public double prediction;
        public double confidence;
        public String reason;
        public String technicals;

        public EnhancedPrediction(double prediction, double confidence, String reason, String technicals) {
            this.prediction = prediction;
            this.confidence = confidence;
            this.reason = reason;
            this.technicals = technicals;
        }
    }
    
    // Stub for ModelPrediction if used elsewhere, though we don't use it internally anymore
    public static class ModelPrediction {
        public double prediction;
        public double confidence;
        public String modelName;
        
        public ModelPrediction(double prediction, double confidence, String modelName) {
            this.prediction = prediction;
            this.confidence = confidence;
            this.modelName = modelName;
        }
    }

    /**
     * Main prediction method using REAL SMA Strategy
     */
    public EnhancedPrediction predictMovement(List<IndexData> historicalData, IndexData currentData, String indexSymbol) {
        if (historicalData == null || historicalData.size() < 20) {
            return new EnhancedPrediction(0.0, 0.0, "Insufficient Data", "Need at least 20 candles for SMA analysis");
        }

        // Calculate SMA 5 and SMA 20
        double sma5 = calculateSMA(historicalData, 5);
        double sma20 = calculateSMA(historicalData, 20);
        
        double currentPrice = currentData.close;
        double prediction = 0.0;
        double confidence = 0.0;
        String signal = "NEUTRAL";
        
        // Golden Cross (Bullish)
        if (sma5 > sma20) {
            signal = "BUY";
            prediction = 1.0;
            confidence = 0.70; // Base confidence
            
            // Confirmation: Price above SMA 5
            if (currentPrice > sma5) {
                confidence += 0.15;
            }
        } 
        // Death Cross (Bearish)
        else if (sma5 < sma20) {
            signal = "SELL";
            prediction = -1.0;
            confidence = 0.70;
            
             // Confirmation: Price below SMA 5
            if (currentPrice < sma5) {
                confidence += 0.15;
            }
        }

        String reason = String.format("Real SMA Strategy: SMA5(%.2f) %s SMA20(%.2f). Signal: %s", 
            sma5, (sma5 > sma20 ? ">" : "<"), sma20, signal);
            
        String technicals = String.format("Price: %.2f | SMA5: %.2f | SMA20: %.2f", currentPrice, sma5, sma20);
        
        System.out.println("✅ " + reason);
        
        return new EnhancedPrediction(prediction, confidence, reason, technicals);
    }

    private double calculateSMA(List<IndexData> data, int period) {
        if (data.size() < period) return 0.0;
        double sum = 0.0;
        // Take the last 'period' entries
        for (int i = data.size() - period; i < data.size(); i++) {
            sum += data.get(i).close;
        }
        return sum / period;
    }
}
