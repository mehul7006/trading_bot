import java.util.*;
import java.time.LocalDateTime;

/**
 * ENHANCED CONFIDENCE CALIBRATOR - PART 3
 * Fixes high confidence trades losing money
 * Ensures only 75%+ confidence calls with real market validation
 */
public class EnhancedConfidenceCalibrator {
    
    // Confidence calibration parameters
    private Map<String, Double> indexAccuracyHistory = new HashMap<>();
    private Map<String, List<ConfidenceResult>> recentResults = new HashMap<>();
    private double minimumConfidenceThreshold = 75.0;
    private int calibrationWindow = 20; // Last 20 trades for calibration
    
    public EnhancedConfidenceCalibrator() {
        System.out.println("🎯 ENHANCED CONFIDENCE CALIBRATOR - PART 3");
        System.out.println("==========================================");
        System.out.println("🎯 Target: Fix high confidence trades losing money");
        System.out.println("📊 Method: Real market validation & calibration");
        System.out.println("🎯 Minimum threshold: 75% confidence");
        
        // Initialize accuracy tracking
        indexAccuracyHistory.put("NIFTY", 38.46); // Current poor accuracy
        indexAccuracyHistory.put("SENSEX", 73.08); // Current good accuracy
        recentResults.put("NIFTY", new ArrayList<>());
        recentResults.put("SENSEX", new ArrayList<>());
    }
    
    /**
     * PART 3A: Multi-factor confidence calculation
     */
    public CalibratedConfidence calculateCalibratedConfidence(String index, 
                                                             double technicalSignal,
                                                             double volumeConfirmation,
                                                             double marketRegimeScore,
                                                             double historicalAccuracy,
                                                             LocalDateTime timestamp) {
        System.out.println("🎯 PART 3A: Calculating calibrated confidence for " + index);
        
        // Base confidence from technical analysis
        double baseConfidence = technicalSignal * 100; // 0-1 scale to 0-100
        
        // Factor 1: Volume confirmation (0-20% boost)
        double volumeBoost = volumeConfirmation * 20;
        
        // Factor 2: Market regime alignment (0-15% boost)
        double regimeBoost = marketRegimeScore * 15;
        
        // Factor 3: Historical accuracy penalty/boost
        double accuracyMultiplier = getAccuracyMultiplier(index);
        
        // Factor 4: Time-of-day adjustment
        double timeAdjustment = getTimeOfDayAdjustment(timestamp);
        
        // Factor 5: Market volatility adjustment
        double volatilityAdjustment = getVolatilityAdjustment(index);
        
        // Calculate raw confidence
        double rawConfidence = (baseConfidence + volumeBoost + regimeBoost) * 
                              accuracyMultiplier * timeAdjustment * volatilityAdjustment;
        
        // Apply calibration based on recent performance
        double calibratedConfidence = applyPerformanceCalibration(index, rawConfidence);
        
        // Ensure confidence is within bounds
        calibratedConfidence = Math.max(0, Math.min(100, calibratedConfidence));
        
        // Check if meets minimum threshold
        boolean meetsThreshold = calibratedConfidence >= minimumConfidenceThreshold;
        
        // Generate confidence breakdown
        String breakdown = generateConfidenceBreakdown(baseConfidence, volumeBoost, regimeBoost, 
                                                      accuracyMultiplier, timeAdjustment, volatilityAdjustment);
        
        CalibratedConfidence result = new CalibratedConfidence(
            calibratedConfidence, meetsThreshold, breakdown, 
            Arrays.asList(baseConfidence, volumeBoost, regimeBoost)
        );
        
        System.out.println("✅ Calibrated confidence: " + String.format("%.1f", calibratedConfidence) + 
                          "% (Threshold: " + (meetsThreshold ? "✅ MET" : "❌ NOT MET") + ")");
        
        return result;
    }
    
    /**
     * PART 3B: Real-time confidence validation
     */
    public ConfidenceValidation validateConfidenceInRealTime(String index, double predictedConfidence,
                                                           double currentPrice, double entryPrice,
                                                           String direction, int minutesElapsed) {
        System.out.println("🔍 PART 3B: Validating confidence in real-time for " + index);
        
        // Calculate actual performance so far
        double priceMove = currentPrice - entryPrice;
        double expectedMove = direction.equals("BULLISH") ? Math.abs(priceMove) : -Math.abs(priceMove);
        if (direction.equals("BEARISH")) {
            expectedMove = -priceMove;
        }
        
        // Performance score (0-1 scale)
        double performanceScore = 0.5; // Neutral start
        if (expectedMove > 0) {
            performanceScore = Math.min(1.0, 0.5 + (expectedMove / entryPrice) * 50); // Positive performance
        } else {
            performanceScore = Math.max(0.0, 0.5 + (expectedMove / entryPrice) * 50); // Negative performance
        }
        
        // Time decay factor (confidence should be validated quickly)
        double timeDecay = Math.max(0.5, 1.0 - (minutesElapsed / 60.0) * 0.5); // 50% decay over 1 hour
        
        // Adjusted confidence based on real performance
        double adjustedConfidence = predictedConfidence * performanceScore * timeDecay;
        
        // Validation status
        ValidationStatus status;
        String reason;
        
        if (performanceScore > 0.7 && minutesElapsed >= 5) {
            status = ValidationStatus.VALIDATED;
            reason = "Strong performance confirms confidence";
        } else if (performanceScore < 0.3 && minutesElapsed >= 10) {
            status = ValidationStatus.INVALIDATED;
            reason = "Poor performance invalidates confidence";
        } else if (minutesElapsed < 15) {
            status = ValidationStatus.PENDING;
            reason = "Insufficient time for validation";
        } else {
            status = ValidationStatus.UNCERTAIN;
            reason = "Mixed signals, continue monitoring";
        }
        
        ConfidenceValidation validation = new ConfidenceValidation(
            adjustedConfidence, status, reason, performanceScore, timeDecay
        );
        
        System.out.println("✅ Confidence validation: " + status + " (" + reason + ")");
        
        return validation;
    }
    
    /**
     * PART 3C: Adaptive confidence learning
     */
    public void updateConfidenceLearning(String index, double predictedConfidence, 
                                       boolean wasCorrect, double actualPnL) {
        System.out.println("📚 PART 3C: Updating confidence learning for " + index);
        
        // Record result
        ConfidenceResult result = new ConfidenceResult(
            predictedConfidence, wasCorrect, actualPnL, LocalDateTime.now()
        );
        
        List<ConfidenceResult> indexResults = recentResults.get(index);
        indexResults.add(result);
        
        // Keep only recent results
        if (indexResults.size() > calibrationWindow) {
            indexResults.remove(0);
        }
        
        // Update accuracy history
        double recentAccuracy = calculateRecentAccuracy(index);
        indexAccuracyHistory.put(index, recentAccuracy);
        
        // Analyze confidence calibration
        analyzeConfidenceCalibration(index);
        
        System.out.println("✅ Learning updated: " + index + " recent accuracy: " + 
                          String.format("%.1f", recentAccuracy) + "%");
    }
    
    /**
     * PART 3D: Confidence threshold optimization
     */
    public ThresholdOptimization optimizeConfidenceThreshold(String index) {
        System.out.println("⚙️ PART 3D: Optimizing confidence threshold for " + index);
        
        List<ConfidenceResult> results = recentResults.get(index);
        if (results.size() < 10) {
            return new ThresholdOptimization(minimumConfidenceThreshold, 
                "Insufficient data for optimization", false);
        }
        
        // Test different thresholds
        double bestThreshold = minimumConfidenceThreshold;
        double bestProfitability = Double.NEGATIVE_INFINITY;
        
        for (double threshold = 70.0; threshold <= 95.0; threshold += 2.5) {
            double profitability = calculateProfitabilityAtThreshold(results, threshold);
            if (profitability > bestProfitability) {
                bestProfitability = profitability;
                bestThreshold = threshold;
            }
        }
        
        // Only update if significantly better
        boolean shouldUpdate = bestThreshold != minimumConfidenceThreshold && 
                              bestProfitability > 0;
        
        String recommendation = generateThresholdRecommendation(bestThreshold, bestProfitability);
        
        ThresholdOptimization optimization = new ThresholdOptimization(
            bestThreshold, recommendation, shouldUpdate
        );
        
        System.out.println("✅ Threshold optimization: " + String.format("%.1f", bestThreshold) + 
                          "% (Profitability: " + String.format("%.2f", bestProfitability) + ")");
        
        return optimization;
    }
    
    // Helper methods
    private double getAccuracyMultiplier(String index) {
        double accuracy = indexAccuracyHistory.getOrDefault(index, 50.0);
        
        if (index.equals("NIFTY") && accuracy < 50.0) {
            return 0.7; // Penalty for poor NIFTY performance
        } else if (index.equals("SENSEX") && accuracy > 70.0) {
            return 1.2; // Boost for good SENSEX performance
        } else {
            return 1.0; // Neutral
        }
    }
    
    private double getTimeOfDayAdjustment(LocalDateTime timestamp) {
        int hour = timestamp.getHour();
        int minute = timestamp.getMinute();
        
        // Morning session (9:15-11:00) - historically best performance
        if (hour == 9 && minute >= 15 || hour == 10) {
            return 1.1; // 10% boost
        }
        // Mid-day (11:00-13:00) - historically worst performance
        else if (hour >= 11 && hour < 13) {
            return 0.9; // 10% penalty
        }
        // Afternoon (13:00-15:30) - decent performance
        else if (hour >= 13 && hour <= 15) {
            return 1.0; // Neutral
        }
        // Outside market hours
        else {
            return 0.5; // Major penalty
        }
    }
    
    private double getVolatilityAdjustment(String index) {
        // Simplified volatility adjustment
        // In real implementation, would use actual volatility data
        return 1.0; // Neutral for now
    }
    
    private double applyPerformanceCalibration(String index, double rawConfidence) {
        List<ConfidenceResult> results = recentResults.get(index);
        if (results.isEmpty()) return rawConfidence;
        
        // Calculate average overconfidence
        double totalOverconfidence = 0.0;
        int count = 0;
        
        for (ConfidenceResult result : results) {
            if (result.predictedConfidence >= 75.0) {
                double actualPerformance = result.wasCorrect ? 100.0 : 0.0;
                double overconfidence = result.predictedConfidence - actualPerformance;
                totalOverconfidence += overconfidence;
                count++;
            }
        }
        
        if (count > 0) {
            double avgOverconfidence = totalOverconfidence / count;
            // Apply calibration to reduce overconfidence
            return rawConfidence - (avgOverconfidence * 0.3);
        }
        
        return rawConfidence;
    }
    
    private double calculateRecentAccuracy(String index) {
        List<ConfidenceResult> results = recentResults.get(index);
        if (results.isEmpty()) return 50.0;
        
        long correctCount = results.stream().mapToInt(r -> r.wasCorrect ? 1 : 0).sum();
        return (double) correctCount / results.size() * 100;
    }
    
    private void analyzeConfidenceCalibration(String index) {
        List<ConfidenceResult> results = recentResults.get(index);
        if (results.size() < 5) return;
        
        // Analyze high confidence trades
        List<ConfidenceResult> highConfidenceTrades = results.stream()
            .filter(r -> r.predictedConfidence >= 85.0)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        if (!highConfidenceTrades.isEmpty()) {
            double highConfidenceAccuracy = highConfidenceTrades.stream()
                .mapToInt(r -> r.wasCorrect ? 1 : 0).sum() / (double) highConfidenceTrades.size() * 100;
            
            System.out.println("📊 " + index + " high confidence (85%+) accuracy: " + 
                              String.format("%.1f", highConfidenceAccuracy) + "%");
        }
    }
    
    private double calculateProfitabilityAtThreshold(List<ConfidenceResult> results, double threshold) {
        return results.stream()
            .filter(r -> r.predictedConfidence >= threshold)
            .mapToDouble(r -> r.actualPnL)
            .sum();
    }
    
    private String generateConfidenceBreakdown(double base, double volume, double regime, 
                                             double accuracy, double time, double volatility) {
        return String.format("Base: %.1f + Volume: %.1f + Regime: %.1f × Accuracy: %.2f × Time: %.2f × Vol: %.2f",
                           base, volume, regime, accuracy, time, volatility);
    }
    
    private String generateThresholdRecommendation(double threshold, double profitability) {
        if (profitability > 1000) {
            return "Highly profitable threshold - strongly recommended";
        } else if (profitability > 0) {
            return "Profitable threshold - recommended";
        } else {
            return "Unprofitable threshold - not recommended";
        }
    }
    
    // Data classes
    public static class CalibratedConfidence {
        public final double confidence;
        public final boolean meetsThreshold;
        public final String breakdown;
        public final List<Double> factors;
        
        public CalibratedConfidence(double confidence, boolean meetsThreshold, String breakdown, List<Double> factors) {
            this.confidence = confidence;
            this.meetsThreshold = meetsThreshold;
            this.breakdown = breakdown;
            this.factors = factors;
        }
    }
    
    public static class ConfidenceValidation {
        public final double adjustedConfidence;
        public final ValidationStatus status;
        public final String reason;
        public final double performanceScore;
        public final double timeDecay;
        
        public ConfidenceValidation(double adjustedConfidence, ValidationStatus status, String reason,
                                  double performanceScore, double timeDecay) {
            this.adjustedConfidence = adjustedConfidence;
            this.status = status;
            this.reason = reason;
            this.performanceScore = performanceScore;
            this.timeDecay = timeDecay;
        }
    }
    
    public static class ConfidenceResult {
        public final double predictedConfidence;
        public final boolean wasCorrect;
        public final double actualPnL;
        public final LocalDateTime timestamp;
        
        public ConfidenceResult(double predictedConfidence, boolean wasCorrect, double actualPnL, LocalDateTime timestamp) {
            this.predictedConfidence = predictedConfidence;
            this.wasCorrect = wasCorrect;
            this.actualPnL = actualPnL;
            this.timestamp = timestamp;
        }
    }
    
    public static class ThresholdOptimization {
        public final double recommendedThreshold;
        public final String recommendation;
        public final boolean shouldUpdate;
        
        public ThresholdOptimization(double recommendedThreshold, String recommendation, boolean shouldUpdate) {
            this.recommendedThreshold = recommendedThreshold;
            this.recommendation = recommendation;
            this.shouldUpdate = shouldUpdate;
        }
    }
    
    public enum ValidationStatus {
        VALIDATED, INVALIDATED, PENDING, UNCERTAIN
    }
    
    public static void main(String[] args) {
        System.out.println("🎯 TESTING ENHANCED CONFIDENCE CALIBRATOR - PART 3");
        System.out.println("==================================================");
        
        EnhancedConfidenceCalibrator calibrator = new EnhancedConfidenceCalibrator();
        
        // Test confidence calculation
        CalibratedConfidence confidence = calibrator.calculateCalibratedConfidence(
            "NIFTY", 0.75, 0.8, 0.7, 38.46, LocalDateTime.now());
        
        System.out.println("\n🎯 CALIBRATED CONFIDENCE RESULT:");
        System.out.println("================================");
        System.out.println("Confidence: " + String.format("%.1f", confidence.confidence) + "%");
        System.out.println("Meets Threshold: " + (confidence.meetsThreshold ? "✅ YES" : "❌ NO"));
        System.out.println("Breakdown: " + confidence.breakdown);
        
        // Test threshold optimization
        ThresholdOptimization optimization = calibrator.optimizeConfidenceThreshold("NIFTY");
        
        System.out.println("\n⚙️ THRESHOLD OPTIMIZATION:");
        System.out.println("===========================");
        System.out.println("Recommended Threshold: " + String.format("%.1f", optimization.recommendedThreshold) + "%");
        System.out.println("Recommendation: " + optimization.recommendation);
        System.out.println("Should Update: " + (optimization.shouldUpdate ? "✅ YES" : "❌ NO"));
    }
}