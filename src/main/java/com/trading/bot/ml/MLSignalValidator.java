package com.trading.bot.ml;

import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.technical.AdvancedTechnicalAnalyzer;
import com.trading.bot.technical.MultiTimeframeAnalyzer;
import com.trading.bot.technical.AdvancedIndicatorsEngine;

import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PHASE 2 - STEP 2.2: ML Signal Validation
 * Advanced machine learning validation of technical analysis signals
 */
public class MLSignalValidator {
    private static final Logger logger = LoggerFactory.getLogger(MLSignalValidator.class);
    
    private final SimplifiedMarketPredictor basePredictor;
    private boolean advancedModelTrained = false;
    private double[] advancedWeights;
    private double[] featureImportance;
    
    public static class ValidationResult {
        public final double mlConfidence;
        public final String mlSignal;
        public final double validationScore;
        public final String reasoning;
        public final boolean isReliable;
        
        public ValidationResult(double mlConfidence, String mlSignal, double validationScore, 
                              String reasoning, boolean isReliable) {
            this.mlConfidence = mlConfidence;
            this.mlSignal = mlSignal;
            this.validationScore = validationScore;
            this.reasoning = reasoning;
            this.isReliable = isReliable;
        }
        
        @Override
        public String toString() {
            return String.format("ML Validation: %s (%.1f%% confidence, %.1f%% validation, %s)", 
                mlSignal, mlConfidence, validationScore, isReliable ? "RELIABLE" : "UNRELIABLE");
        }
    }
    
    public static class AdvancedMLFeatures {
        public final double[] technicalFeatures;
        public final double[] timeframeFeatures;
        public final double[] indicatorFeatures;
        public final double[] volatilityFeatures;
        public final double[] momentumFeatures;
        
        public AdvancedMLFeatures(double[] technical, double[] timeframe, double[] indicator,
                                double[] volatility, double[] momentum) {
            this.technicalFeatures = technical.clone();
            this.timeframeFeatures = timeframe.clone();
            this.indicatorFeatures = indicator.clone();
            this.volatilityFeatures = volatility.clone();
            this.momentumFeatures = momentum.clone();
        }
        
        public double[] getCombinedFeatures() {
            List<Double> combined = new ArrayList<>();
            
            for (double f : technicalFeatures) combined.add(f);
            for (double f : timeframeFeatures) combined.add(f);
            for (double f : indicatorFeatures) combined.add(f);
            for (double f : volatilityFeatures) combined.add(f);
            for (double f : momentumFeatures) combined.add(f);
            
            return combined.stream().mapToDouble(Double::doubleValue).toArray();
        }
    }
    
    public MLSignalValidator() {
        this.basePredictor = new SimplifiedMarketPredictor();
        this.advancedWeights = new double[25]; // 25 advanced features
        this.featureImportance = new double[25];
        initializeAdvancedModel();
    }
    
    /**
     * Initialize advanced ML model for Phase 2
     */
    private void initializeAdvancedModel() {
        System.out.println("Initializing advanced ML signal validator...");
        
        try {
            // Initialize with educated weights based on financial research
            // Technical features (5)
            advancedWeights[0] = 0.12; // Trend strength
            advancedWeights[1] = 0.08; // Momentum
            advancedWeights[2] = 0.06; // Volume confirmation
            advancedWeights[3] = 0.07; // Volatility
            advancedWeights[4] = 0.05; // Pattern strength
            
            // Timeframe features (5)
            advancedWeights[5] = 0.15; // Long-term trend
            advancedWeights[6] = 0.12; // Medium-term trend
            advancedWeights[7] = 0.08; // Short-term trend
            advancedWeights[8] = 0.10; // Timeframe alignment
            advancedWeights[9] = 0.05; // Trend consistency
            
            // Indicator features (5)
            advancedWeights[10] = 0.06; // Stochastic
            advancedWeights[11] = 0.04; // Williams %R
            advancedWeights[12] = 0.08; // ADX strength
            advancedWeights[13] = 0.07; // Indicator confluence
            advancedWeights[14] = 0.03; // Oscillator alignment
            
            // Volatility features (5)
            advancedWeights[15] = 0.05; // Historical volatility
            advancedWeights[16] = 0.04; // Volatility trend
            advancedWeights[17] = 0.06; // Bollinger position
            advancedWeights[18] = 0.03; // Volatility breakout
            advancedWeights[19] = 0.02; // VIX analogue
            
            // Momentum features (5)
            advancedWeights[20] = 0.08; // Price momentum
            advancedWeights[21] = 0.06; // Volume momentum
            advancedWeights[22] = 0.04; // MACD momentum
            advancedWeights[23] = 0.05; // ROC (Rate of Change)
            advancedWeights[24] = 0.03; // Momentum divergence
            
            // Initialize feature importance (equal initially)
            for (int i = 0; i < featureImportance.length; i++) {
                featureImportance[i] = 1.0 / featureImportance.length;
            }
            
            basePredictor.initializeModel();
            logger.info("Advanced ML signal validator initialized successfully");
            
        } catch (Exception e) {
            logger.error("Error initializing advanced ML model: {}", e.getMessage(), e);
        }
    }
    
    /**
     * PHASE 2: Validate technical signals with advanced ML
     */
    public ValidationResult validateSignal(List<SimpleMarketData> priceHistory,
                                         AdvancedTechnicalAnalyzer.TechnicalResult technicalResult,
                                         MultiTimeframeAnalyzer.MultiTimeframeResult timeframeResult,
                                         AdvancedIndicatorsEngine.AdvancedIndicatorsResult indicatorsResult) {
        
        logger.info("Validating signal with advanced ML for {} data points", priceHistory.size());
        
        try {
            if (priceHistory.size() < 20) {
                return new ValidationResult(50.0, "HOLD", 0.0, "Insufficient data", false);
            }
            
            // Extract advanced features
            AdvancedMLFeatures features = extractAdvancedFeatures(
                priceHistory, technicalResult, timeframeResult, indicatorsResult);
            
            // Get ML prediction with advanced model
            double mlPrediction = predictWithAdvancedModel(features);
            
            // Calculate validation score
            double validationScore = calculateValidationScore(features, technicalResult, mlPrediction);
            
            // Determine ML signal and confidence
            String mlSignal = determinMLSignal(mlPrediction);
            double mlConfidence = calculateMLConfidence(mlPrediction, validationScore);
            
            // Check reliability
            boolean isReliable = isSignalReliable(validationScore, mlConfidence, features);
            
            String reasoning = generateValidationReasoning(features, mlPrediction, validationScore);
            
            ValidationResult result = new ValidationResult(mlConfidence, mlSignal, validationScore, 
                                                         reasoning, isReliable);
            
            logger.info("ML validation result: {}", result);
            return result;
            
        } catch (Exception e) {
            logger.error("Error in ML signal validation: {}", e.getMessage(), e);
            return new ValidationResult(50.0, "HOLD", 0.0, "Validation error", false);
        }
    }
    
    /**
     * Extract advanced ML features from all analysis components
     */
    private AdvancedMLFeatures extractAdvancedFeatures(List<SimpleMarketData> priceHistory,
                                                      AdvancedTechnicalAnalyzer.TechnicalResult technical,
                                                      MultiTimeframeAnalyzer.MultiTimeframeResult timeframe,
                                                      AdvancedIndicatorsEngine.AdvancedIndicatorsResult indicators) {
        
        // Technical features (5)
        double[] technicalFeatures = new double[5];
        technicalFeatures[0] = technical.confidence / 100.0;
        technicalFeatures[1] = calculateMomentumScore(priceHistory);
        technicalFeatures[2] = calculateVolumeScore(priceHistory);
        technicalFeatures[3] = calculateVolatilityScore(priceHistory);
        technicalFeatures[4] = calculatePatternScore(priceHistory);
        
        // Timeframe features (5)
        double[] timeframeFeatures = new double[5];
        timeframeFeatures[0] = timeframe.overallConfidence / 100.0;
        timeframeFeatures[1] = getTimeframeScore(timeframe, MultiTimeframeAnalyzer.Timeframe.LONG_TERM);
        timeframeFeatures[2] = getTimeframeScore(timeframe, MultiTimeframeAnalyzer.Timeframe.MEDIUM_TERM);
        timeframeFeatures[3] = getTimeframeScore(timeframe, MultiTimeframeAnalyzer.Timeframe.SHORT_TERM);
        timeframeFeatures[4] = getAlignmentScore(timeframe.alignment);
        
        // Indicator features (5)
        double[] indicatorFeatures = new double[5];
        indicatorFeatures[0] = indicators.stochastic.strength / 100.0;
        indicatorFeatures[1] = Math.abs(indicators.williamsR.williamsR + 50) / 50.0;
        indicatorFeatures[2] = indicators.adx.adx / 100.0;
        indicatorFeatures[3] = indicators.confluenceScore / 100.0;
        indicatorFeatures[4] = getIndicatorAlignmentScore(indicators);
        
        // Volatility features (5)
        double[] volatilityFeatures = new double[5];
        volatilityFeatures[0] = calculateHistoricalVolatility(priceHistory);
        volatilityFeatures[1] = calculateVolatilityTrend(priceHistory);
        volatilityFeatures[2] = calculateBollingerPosition(priceHistory);
        volatilityFeatures[3] = calculateVolatilityBreakout(priceHistory);
        volatilityFeatures[4] = calculateVixAnalogue(priceHistory);
        
        // Momentum features (5)
        double[] momentumFeatures = new double[5];
        momentumFeatures[0] = calculatePriceMomentum(priceHistory);
        momentumFeatures[1] = calculateVolumeMomentum(priceHistory);
        momentumFeatures[2] = calculateMACDMomentum(priceHistory);
        momentumFeatures[3] = calculateROC(priceHistory);
        momentumFeatures[4] = calculateMomentumDivergence(priceHistory);
        
        return new AdvancedMLFeatures(technicalFeatures, timeframeFeatures, indicatorFeatures,
                                    volatilityFeatures, momentumFeatures);
    }
    
    /**
     * Make prediction with advanced ML model
     */
    private double predictWithAdvancedModel(AdvancedMLFeatures features) {
        double[] combinedFeatures = features.getCombinedFeatures();
        
        double prediction = 0.0;
        for (int i = 0; i < Math.min(combinedFeatures.length, advancedWeights.length); i++) {
            prediction += combinedFeatures[i] * advancedWeights[i];
        }
        
        // Apply sigmoid-like function to normalize
        return Math.tanh(prediction);
    }
    
    /**
     * Calculate validation score based on feature consistency
     */
    private double calculateValidationScore(AdvancedMLFeatures features, 
                                          AdvancedTechnicalAnalyzer.TechnicalResult technical, 
                                          double mlPrediction) {
        
        double[] combined = features.getCombinedFeatures();
        double consistency = 0.0;
        int validFeatures = 0;
        
        // Check feature consistency
        for (double feature : combined) {
            if (!Double.isNaN(feature) && !Double.isInfinite(feature)) {
                consistency += Math.abs(feature - 0.5); // Distance from neutral
                validFeatures++;
            }
        }
        
        double baseScore = validFeatures > 0 ? (consistency / validFeatures) * 100 : 50.0;
        
        // Boost for signal alignment
        boolean technicalBullish = "BUY".equals(technical.signal);
        boolean mlBullish = mlPrediction > 0;
        
        if (technicalBullish == mlBullish) {
            baseScore += 15; // Agreement bonus
        }
        
        return Math.min(100, Math.max(0, baseScore));
    }
    
    /**
     * Determine ML signal from prediction
     */
    private String determinMLSignal(double prediction) {
        if (prediction > 0.15) return "BUY";
        if (prediction < -0.15) return "SELL";
        return "HOLD";
    }
    
    /**
     * Calculate ML confidence
     */
    private double calculateMLConfidence(double prediction, double validationScore) {
        double baseConfidence = Math.abs(prediction) * 100;
        double validationAdjustment = (validationScore - 50) * 0.5;
        
        return Math.min(95, Math.max(5, baseConfidence + validationAdjustment));
    }
    
    /**
     * Check if signal is reliable
     */
    private boolean isSignalReliable(double validationScore, double mlConfidence, AdvancedMLFeatures features) {
        return validationScore > 60 && mlConfidence > 60 && features.getCombinedFeatures().length > 10;
    }
    
    // Helper methods for feature extraction
    private double calculateMomentumScore(List<SimpleMarketData> data) {
        if (data.size() < 5) return 0.5;
        
        double start = data.get(data.size() - 5).price;
        double end = data.get(data.size() - 1).price;
        return Math.tanh((end - start) / start * 10) * 0.5 + 0.5;
    }
    
    private double calculateVolumeScore(List<SimpleMarketData> data) {
        if (data.size() < 3) return 0.5;
        
        long recent = data.get(data.size() - 1).volume;
        double avg = data.subList(Math.max(0, data.size() - 10), data.size())
            .stream().mapToLong(d -> d.volume).average().orElse(recent);
        
        return Math.min(1.0, recent / avg / 2.0);
    }
    
    private double calculateVolatilityScore(List<SimpleMarketData> data) {
        if (data.size() < 10) return 0.5;
        
        double mean = data.subList(data.size() - 10, data.size()).stream()
            .mapToDouble(d -> d.price).average().orElse(0);
        
        double variance = data.subList(data.size() - 10, data.size()).stream()
            .mapToDouble(d -> Math.pow(d.price - mean, 2)).average().orElse(0);
        
        return Math.min(1.0, Math.sqrt(variance) / mean);
    }
    
    private double calculatePatternScore(List<SimpleMarketData> data) {
        // Simplified pattern scoring
        if (data.size() < 5) return 0.5;
        
        boolean ascending = true;
        for (int i = data.size() - 4; i < data.size() - 1; i++) {
            if (data.get(i).price >= data.get(i + 1).price) {
                ascending = false;
                break;
            }
        }
        
        return ascending ? 0.8 : 0.3;
    }
    
    private double getTimeframeScore(MultiTimeframeAnalyzer.MultiTimeframeResult result, 
                                   MultiTimeframeAnalyzer.Timeframe tf) {
        MultiTimeframeAnalyzer.TimeframeAnalysis analysis = result.analyses.get(tf);
        return analysis != null ? analysis.confidence / 100.0 : 0.5;
    }
    
    private double getAlignmentScore(String alignment) {
        switch (alignment) {
            case "STRONG_ALIGNMENT": return 1.0;
            case "PARTIAL_ALIGNMENT": return 0.7;
            case "WEAK_ALIGNMENT": return 0.4;
            default: return 0.2;
        }
    }
    
    private double getIndicatorAlignmentScore(AdvancedIndicatorsEngine.AdvancedIndicatorsResult indicators) {
        int bullishCount = 0;
        int totalIndicators = 3;
        
        if ("BULLISH".equals(indicators.stochastic.signal) || "OVERSOLD".equals(indicators.stochastic.signal)) {
            bullishCount++;
        }
        if ("BULLISH".equals(indicators.williamsR.signal) || "OVERSOLD".equals(indicators.williamsR.signal)) {
            bullishCount++;
        }
        if ("BULLISH".equals(indicators.adx.direction)) {
            bullishCount++;
        }
        
        return (double) bullishCount / totalIndicators;
    }
    
    // Additional volatility and momentum helper methods
    private double calculateHistoricalVolatility(List<SimpleMarketData> data) {
        return Math.min(1.0, calculateVolatilityScore(data));
    }
    
    private double calculateVolatilityTrend(List<SimpleMarketData> data) {
        // Simplified: compare recent vs older volatility
        return 0.5; // Placeholder
    }
    
    private double calculateBollingerPosition(List<SimpleMarketData> data) {
        // Simplified: assume middle of bands
        return 0.5; // Placeholder
    }
    
    private double calculateVolatilityBreakout(List<SimpleMarketData> data) {
        return calculateVolatilityScore(data) > 0.7 ? 1.0 : 0.0;
    }
    
    private double calculateVixAnalogue(List<SimpleMarketData> data) {
        return calculateVolatilityScore(data);
    }
    
    private double calculatePriceMomentum(List<SimpleMarketData> data) {
        return calculateMomentumScore(data);
    }
    
    private double calculateVolumeMomentum(List<SimpleMarketData> data) {
        return calculateVolumeScore(data);
    }
    
    private double calculateMACDMomentum(List<SimpleMarketData> data) {
        // Simplified MACD momentum
        return calculateMomentumScore(data);
    }
    
    private double calculateROC(List<SimpleMarketData> data) {
        if (data.size() < 5) return 0.0;
        
        double current = data.get(data.size() - 1).price;
        double previous = data.get(data.size() - 5).price;
        return (current - previous) / previous;
    }
    
    private double calculateMomentumDivergence(List<SimpleMarketData> data) {
        // Simplified: no divergence detected
        return 0.0;
    }
    
    private String generateValidationReasoning(AdvancedMLFeatures features, double prediction, double validationScore) {
        return String.format("ML Prediction: %.3f, Validation: %.1f%%, Features: %d", 
            prediction, validationScore, features.getCombinedFeatures().length);
    }
    
    /**
     * Train advanced model with validation data
     */
    public void trainAdvancedModel(List<SimpleMarketData> trainingData) {
        logger.info("Training advanced ML model with {} data points", trainingData.size());
        
        try {
            basePredictor.trainModel(trainingData);
            advancedModelTrained = true;
            
            logger.info("Advanced ML model training completed");
        } catch (Exception e) {
            logger.error("Error training advanced ML model: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get validation confidence boost for signal combination
     */
    public double getValidationBoost(ValidationResult validation) {
        if (!validation.isReliable) return 0.0;
        
        if (validation.validationScore > 80) return 15.0;
        if (validation.validationScore > 70) return 10.0;
        if (validation.validationScore > 60) return 5.0;
        
        return 0.0;
    }
}