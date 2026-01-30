package com.trading.bot.honest;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HONEST ACCURACY TRACKER
 * Tracks REAL performance with NO inflated claims
 * Measures actual prediction accuracy against market outcomes
 */
public class HonestAccuracyTracker {
    
    private final Map<String, List<TrackedPrediction>> predictionHistory;
    private final Map<String, HonestStats> indexStats;
    
    public HonestAccuracyTracker() {
        this.predictionHistory = new ConcurrentHashMap<>();
        this.indexStats = new ConcurrentHashMap<>();
        
        System.out.println("📊 Honest Accuracy Tracker initialized");
        System.out.println("✅ Real performance measurement");
        System.out.println("✅ No inflated accuracy claims");
    }
    
    /**
     * Record a prediction for honest tracking
     */
    public void recordPrediction(HonestOptionsPrediction prediction) {
        String index = prediction.index;
        
        TrackedPrediction tracked = new TrackedPrediction(
            prediction.id,
            index,
            prediction.direction,
            prediction.optionType,
            prediction.recommendedStrike,
            prediction.confidence,
            prediction.timestamp,
            prediction.currentPrice,
            false, // Not resolved yet
            0.0,   // No outcome yet
            false  // Not profitable yet
        );
        
        predictionHistory.computeIfAbsent(index, k -> new ArrayList<>()).add(tracked);
        
        System.out.printf("📝 Tracked prediction: %s %s %.1f%% confidence%n", 
            index, prediction.direction, prediction.confidence * 100);
    }
    
    /**
     * Update predictions with real market outcomes
     */
    public void updateWithRealOutcome(String index, double currentPrice) {
        List<TrackedPrediction> predictions = predictionHistory.get(index);
        if (predictions == null) return;
        
        LocalDateTime now = LocalDateTime.now();
        boolean updated = false;
        
        for (TrackedPrediction prediction : predictions) {
            // Update predictions that are 30+ minutes old and not yet resolved
            if (!prediction.resolved && 
                prediction.timestamp.isBefore(now.minusMinutes(30))) {
                
                // Calculate real outcome
                boolean wasCorrect = calculateRealOutcome(prediction, currentPrice);
                double actualMove = (currentPrice - prediction.entryPrice) / prediction.entryPrice;
                
                // Update prediction with real result
                prediction.resolved = true;
                prediction.actualOutcome = actualMove;
                prediction.wasCorrect = wasCorrect;
                
                updateIndexStats(index, wasCorrect, prediction.confidence);
                
                System.out.printf("✅ Updated %s: %s (%.1f%% confidence) = %s%n",
                    index, prediction.direction, prediction.confidence * 100,
                    wasCorrect ? "CORRECT" : "INCORRECT");
                
                updated = true;
            }
        }
        
        if (updated) {
            calculateAndPrintStats(index);
        }
    }
    
    private boolean calculateRealOutcome(TrackedPrediction prediction, double currentPrice) {
        double priceMove = currentPrice - prediction.entryPrice;
        double movePercent = priceMove / prediction.entryPrice;
        
        // Define success criteria based on prediction direction
        if (prediction.direction.equals("BULLISH")) {
            // For bullish predictions, success if price moved up by at least 0.2%
            return movePercent > 0.002;
        } else if (prediction.direction.equals("BEARISH")) {
            // For bearish predictions, success if price moved down by at least 0.2%
            return movePercent < -0.002;
        } else {
            // For neutral predictions, success if price stayed within 0.3% range
            return Math.abs(movePercent) < 0.003;
        }
    }
    
    private void updateIndexStats(String index, boolean wasCorrect, double confidence) {
        HonestStats stats = indexStats.computeIfAbsent(index, k -> new HonestStats());
        
        stats.totalPredictions++;
        if (wasCorrect) {
            stats.correctPredictions++;
        }
        stats.totalConfidence += confidence;
        stats.lastUpdated = LocalDateTime.now();
        
        // Update running accuracy
        stats.accuracy = (double) stats.correctPredictions / stats.totalPredictions;
        stats.avgConfidence = stats.totalConfidence / stats.totalPredictions;
    }
    
    private void calculateAndPrintStats(String index) {
        HonestStats stats = indexStats.get(index);
        if (stats == null || stats.totalPredictions < 3) return; // Wait for meaningful sample
        
        System.out.printf("\n📊 %s HONEST STATS: %.1f%% accuracy (%d/%d predictions)%n",
            index, stats.accuracy * 100, stats.correctPredictions, stats.totalPredictions);
        
        if (stats.accuracy >= 0.75) {
            System.out.printf("🎯 %s: TARGET ACHIEVED (≥75%% accuracy)%n", index);
        } else {
            System.out.printf("⚠️ %s: Below target (need %.1f%% improvement)%n", 
                index, (0.75 - stats.accuracy) * 100);
        }
    }
    
    /**
     * Get comprehensive honest statistics
     */
    public HonestAccuracyStats getHonestStats() {
        int totalPredictions = 0;
        int totalCorrect = 0;
        double totalConfidence = 0;
        
        Map<String, Double> perIndexAccuracy = new HashMap<>();
        
        for (Map.Entry<String, HonestStats> entry : indexStats.entrySet()) {
            String index = entry.getKey();
            HonestStats stats = entry.getValue();
            
            totalPredictions += stats.totalPredictions;
            totalCorrect += stats.correctPredictions;
            totalConfidence += stats.totalConfidence;
            
            perIndexAccuracy.put(index, stats.accuracy);
        }
        
        double overallAccuracy = totalPredictions > 0 ? (double) totalCorrect / totalPredictions : 0;
        double avgConfidence = totalPredictions > 0 ? totalConfidence / totalPredictions : 0;
        
        return new HonestAccuracyStats(
            totalPredictions,
            totalCorrect,
            overallAccuracy,
            avgConfidence,
            perIndexAccuracy
        );
    }
    
    /**
     * Generate detailed honest performance report
     */
    public String generateHonestReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("📊 HONEST ACCURACY REPORT\n");
        report.append("=" .repeat(50)).append("\n");
        report.append("Generated: ").append(LocalDateTime.now()).append("\n\n");
        
        HonestAccuracyStats stats = getHonestStats();
        
        report.append("OVERALL PERFORMANCE:\n");
        report.append(String.format("Total Predictions: %d\n", stats.totalPredictions));
        report.append(String.format("Correct Predictions: %d\n", stats.successfulPredictions));
        report.append(String.format("HONEST ACCURACY: %.1f%%\n", stats.honestAccuracy * 100));
        report.append(String.format("Average Confidence: %.1f%%\n\n", stats.avgConfidence * 100));
        
        if (stats.honestAccuracy >= 0.75) {
            report.append("🎯 TARGET ACHIEVED: Accuracy >= 75%\n");
        } else {
            report.append(String.format("⚠️ Below target: Need %.1f%% improvement\n", 
                (0.75 - stats.honestAccuracy) * 100));
        }
        
        report.append("\nPER-INDEX PERFORMANCE:\n");
        report.append("-" .repeat(30)).append("\n");
        
        for (Map.Entry<String, Double> entry : stats.perIndexAccuracy.entrySet()) {
            String index = entry.getKey();
            double accuracy = entry.getValue();
            HonestStats indexStats = this.indexStats.get(index);
            
            report.append(String.format("%s: %.1f%% (%d predictions)\n", 
                index, accuracy * 100, indexStats != null ? indexStats.totalPredictions : 0));
        }
        
        report.append("\nRECENT PREDICTIONS:\n");
        report.append("-" .repeat(30)).append("\n");
        
        // Show last 5 resolved predictions
        List<TrackedPrediction> allPredictions = new ArrayList<>();
        for (List<TrackedPrediction> predictions : predictionHistory.values()) {
            allPredictions.addAll(predictions.stream()
                .filter(p -> p.resolved)
                .toList());
        }
        
        allPredictions.sort((a, b) -> b.timestamp.compareTo(a.timestamp));
        
        for (int i = 0; i < Math.min(5, allPredictions.size()); i++) {
            TrackedPrediction pred = allPredictions.get(i);
            report.append(String.format("%s %s %.1f%% -> %s\n",
                pred.index, pred.direction, pred.confidence * 100,
                pred.wasCorrect ? "✅" : "❌"));
        }
        
        return report.toString();
    }
    
    /**
     * Clear old predictions to prevent memory issues
     */
    public void cleanupOldPredictions() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        
        for (List<TrackedPrediction> predictions : predictionHistory.values()) {
            predictions.removeIf(p -> p.timestamp.isBefore(cutoff));
        }
        
        System.out.println("🧹 Cleaned up predictions older than 7 days");
    }
}

/**
 * Supporting data classes
 */

class TrackedPrediction {
    final String id;
    final String index;
    final String direction;
    final String optionType;
    final double strike;
    final double confidence;
    final LocalDateTime timestamp;
    final double entryPrice;
    
    boolean resolved;
    double actualOutcome;
    boolean wasCorrect;
    
    TrackedPrediction(String id, String index, String direction, String optionType,
                     double strike, double confidence, LocalDateTime timestamp, double entryPrice,
                     boolean resolved, double actualOutcome, boolean wasCorrect) {
        this.id = id;
        this.index = index;
        this.direction = direction;
        this.optionType = optionType;
        this.strike = strike;
        this.confidence = confidence;
        this.timestamp = timestamp;
        this.entryPrice = entryPrice;
        this.resolved = resolved;
        this.actualOutcome = actualOutcome;
        this.wasCorrect = wasCorrect;
    }
}

class HonestStats {
    int totalPredictions = 0;
    int correctPredictions = 0;
    double totalConfidence = 0;
    double accuracy = 0;
    double avgConfidence = 0;
    LocalDateTime lastUpdated = LocalDateTime.now();
}

class HonestAccuracyStats {
    final int totalPredictions;
    final int successfulPredictions;
    final double honestAccuracy;
    final double avgConfidence;
    final Map<String, Double> perIndexAccuracy;
    
    HonestAccuracyStats(int total, int successful, double accuracy, double avgConf,
                       Map<String, Double> perIndex) {
        this.totalPredictions = total;
        this.successfulPredictions = successful;
        this.honestAccuracy = accuracy;
        this.avgConfidence = avgConf;
        this.perIndexAccuracy = new HashMap<>(perIndex);
    }
}

// Complete supporting classes for the prediction system

class HonestOptionsPrediction {
    final String id;
    final String index;
    final String direction;
    final String optionType;
    final double recommendedStrike;
    final double confidence;
    final OptionsGreeks greeks;
    final RiskMetrics risk;
    final List<String> reasoning;
    final LocalDateTime timestamp;
    final String strategy;
    final double currentPrice;
    
    HonestOptionsPrediction(String index, String direction, String optionType, double strike,
                           double confidence, OptionsGreeks greeks, RiskMetrics risk,
                           List<String> reasoning, LocalDateTime timestamp, String strategy) {
        this.id = UUID.randomUUID().toString();
        this.index = index;
        this.direction = direction;
        this.optionType = optionType;
        this.recommendedStrike = strike;
        this.confidence = confidence;
        this.greeks = greeks;
        this.risk = risk;
        this.reasoning = new ArrayList<>(reasoning);
        this.timestamp = timestamp;
        this.strategy = strategy;
        this.currentPrice = 0; // Will be set from real data
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s %s %.0f %s - %.1f%% confidence | Risk: ₹%.0f | Reward: ₹%.0f",
            index, direction, optionType, recommendedStrike,
            timestamp.toLocalDate(), confidence * 100,
            risk.maxLoss, risk.expectedProfit);
    }
}

class OptionsGreeks {
    final double delta;
    final double gamma;
    final double theta;
    final double vega;
    final double rho;
    
    OptionsGreeks(double delta, double gamma, double theta, double vega, double rho) {
        this.delta = delta;
        this.gamma = gamma;
        this.theta = theta;
        this.vega = vega;
        this.rho = rho;
    }
}

class RiskMetrics {
    final double maxLoss;
    final double expectedProfit;
    final double breakeven;
    final double probabilityOfProfit;
    final String riskLevel;
    
    RiskMetrics(double maxLoss, double expectedProfit, double breakeven,
               double probabilityOfProfit, String riskLevel) {
        this.maxLoss = maxLoss;
        this.expectedProfit = expectedProfit;
        this.breakeven = breakeven;
        this.probabilityOfProfit = probabilityOfProfit;
        this.riskLevel = riskLevel;
    }
}