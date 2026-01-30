package com.trading.bot.core;

import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.technical.AdvancedTechnicalAnalyzer;
import com.trading.bot.technical.MultiTimeframeAnalyzer;
import com.trading.bot.technical.AdvancedIndicatorsEngine;
import com.trading.bot.ml.MLSignalValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

/**
 * PHASE 2: Advanced Integrated Trading Bot
 * Combines multi-timeframe analysis, advanced indicators, and ML validation
 * Target: 85-90% → 90-93% accuracy improvement
 */
public class Phase2IntegratedBot {
    private static final Logger logger = LoggerFactory.getLogger(Phase2IntegratedBot.class);
    
    // Core components
    private final AdvancedTechnicalAnalyzer technicalAnalyzer;
    private final MultiTimeframeAnalyzer timeframeAnalyzer;
    private final AdvancedIndicatorsEngine indicatorsEngine;
    private final MLSignalValidator mlValidator;
    
    // Bot state
    private final List<AdvancedTradingCall> tradingHistory;
    private boolean isInitialized = false;
    private int totalCallsGenerated = 0;
    private double cumulativeAccuracy = 0.0;
    
    public static class AdvancedTradingCall {
        public final String symbol;
        public final String signal;
        public final double confidence;
        public final double price;
        public final LocalDateTime timestamp;
        
        // Phase 2 enhanced reasoning
        public final String technicalReasoning;
        public final String timeframeReasoning;
        public final String indicatorReasoning;
        public final String mlValidationReasoning;
        public final String combinedReasoning;
        
        // Phase 2 advanced metrics
        public final double timeframeAlignment;
        public final double indicatorConfluence;
        public final double mlValidationScore;
        public final boolean isHighConfidence;
        public final String qualityRating;
        
        public AdvancedTradingCall(String symbol, String signal, double confidence, double price,
                                 String technicalReasoning, String timeframeReasoning, 
                                 String indicatorReasoning, String mlValidationReasoning,
                                 String combinedReasoning, double timeframeAlignment,
                                 double indicatorConfluence, double mlValidationScore,
                                 boolean isHighConfidence, String qualityRating) {
            this.symbol = symbol;
            this.signal = signal;
            this.confidence = confidence;
            this.price = price;
            this.timestamp = LocalDateTime.now();
            this.technicalReasoning = technicalReasoning;
            this.timeframeReasoning = timeframeReasoning;
            this.indicatorReasoning = indicatorReasoning;
            this.mlValidationReasoning = mlValidationReasoning;
            this.combinedReasoning = combinedReasoning;
            this.timeframeAlignment = timeframeAlignment;
            this.indicatorConfluence = indicatorConfluence;
            this.mlValidationScore = mlValidationScore;
            this.isHighConfidence = isHighConfidence;
            this.qualityRating = qualityRating;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] PHASE2 %s: %s (%.1f%%) at %.2f - Quality: %s\n" +
                               "  Technical: %s\n" +
                               "  Timeframe: %s (%.1f%% alignment)\n" +
                               "  Indicators: %s (%.1f%% confluence)\n" +
                               "  ML: %s (%.1f%% validation)\n" +
                               "  Combined: %s", 
                timestamp, symbol, signal, confidence, price, qualityRating,
                technicalReasoning, timeframeReasoning, timeframeAlignment,
                indicatorReasoning, indicatorConfluence,
                mlValidationReasoning, mlValidationScore,
                combinedReasoning);
        }
        
        public String getCompactString() {
            return String.format("[%s] %s: %s (%.1f%%) - %s", 
                timestamp, symbol, signal, confidence, qualityRating);
        }
    }
    
    public Phase2IntegratedBot() {
        this.technicalAnalyzer = new AdvancedTechnicalAnalyzer();
        this.timeframeAnalyzer = new MultiTimeframeAnalyzer();
        this.indicatorsEngine = new AdvancedIndicatorsEngine();
        this.mlValidator = new MLSignalValidator();
        this.tradingHistory = new ArrayList<>();
    }
    
    /**
     * Initialize Phase 2 bot with all advanced components
     */
    public void initialize() {
        logger.info("=== Initializing Phase 2 Advanced Integrated Bot ===");
        
        try {
            logger.info("Initializing technical analyzer...");
            // Technical analyzer is ready (stateless)
            
            logger.info("Initializing multi-timeframe analyzer...");
            // Timeframe analyzer is ready (stateless)
            
            logger.info("Initializing advanced indicators engine...");
            // Indicators engine is ready (stateless)
            
            logger.info("Initializing ML signal validator...");
            // ML validator initializes itself
            
            isInitialized = true;
            logger.info("✅ Phase 2 Advanced Integrated Bot initialized successfully");
            
        } catch (Exception e) {
            logger.error("❌ Failed to initialize Phase 2 bot: {}", e.getMessage(), e);
            isInitialized = false;
        }
    }
    
    /**
     * PHASE 2: Generate advanced trading call with all components
     */
    public AdvancedTradingCall generateAdvancedTradingCall(String symbol, List<SimpleMarketData> priceHistory) {
        if (!isInitialized) {
            logger.warn("Bot not initialized. Initializing now...");
            initialize();
        }
        
        logger.info("=== Generating Phase 2 Advanced Trading Call for {} ===", symbol);
        
        try {
            // PHASE 2 - STEP 1: Enhanced Technical Analysis (from Phase 1)
            AdvancedTechnicalAnalyzer.TechnicalResult technicalResult = 
                technicalAnalyzer.calculateEnhancedConfidence(priceHistory, symbol);
            logger.debug("Technical analysis: {}", technicalResult.reasoning);
            
            // PHASE 2 - STEP 2: Multi-Timeframe Analysis
            MultiTimeframeAnalyzer.MultiTimeframeResult timeframeResult = 
                timeframeAnalyzer.analyzeMultipleTimeframes(priceHistory);
            logger.debug("Timeframe analysis: {}", timeframeResult.reasoning);
            
            // PHASE 2 - STEP 3: Advanced Indicators Analysis
            AdvancedIndicatorsEngine.AdvancedIndicatorsResult indicatorsResult = 
                indicatorsEngine.analyzeAdvancedIndicators(priceHistory);
            logger.debug("Indicators analysis: {}", indicatorsResult.reasoning);
            
            // PHASE 2 - STEP 4: ML Signal Validation
            MLSignalValidator.ValidationResult mlValidation = 
                mlValidator.validateSignal(priceHistory, technicalResult, timeframeResult, indicatorsResult);
            logger.debug("ML validation: {}", mlValidation.reasoning);
            
            // PHASE 2 - STEP 5: Advanced Signal Combination
            AdvancedTradingCall finalCall = combineAdvancedSignals(
                symbol, priceHistory, technicalResult, timeframeResult, indicatorsResult, mlValidation);
            
            tradingHistory.add(finalCall);
            totalCallsGenerated++;
            
            logger.info("✅ Generated Phase 2 call: {}", finalCall.getCompactString());
            logger.debug("Full call details:\n{}", finalCall);
            
            return finalCall;
            
        } catch (Exception e) {
            logger.error("❌ Error generating Phase 2 trading call: {}", e.getMessage(), e);
            return createErrorCall(symbol, priceHistory);
        }
    }
    
    /**
     * PHASE 2: Advanced signal combination with sophisticated weighting
     */
    private AdvancedTradingCall combineAdvancedSignals(String symbol, 
                                                     List<SimpleMarketData> priceHistory,
                                                     AdvancedTechnicalAnalyzer.TechnicalResult technical,
                                                     MultiTimeframeAnalyzer.MultiTimeframeResult timeframe,
                                                     AdvancedIndicatorsEngine.AdvancedIndicatorsResult indicators,
                                                     MLSignalValidator.ValidationResult mlValidation) {
        
        logger.debug("Combining Phase 2 advanced signals...");
        
        // Phase 2 advanced weighting
        double technicalWeight = 0.25;  // Reduced from Phase 1 (was 0.7)
        double timeframeWeight = 0.30;  // NEW - Primary weight
        double indicatorWeight = 0.25;  // NEW - Significant weight
        double mlWeight = 0.20;         // Increased from Phase 1 (was 0.3)
        
        // Calculate base scores
        double technicalScore = technical.confidence;
        double timeframeScore = timeframe.overallConfidence;
        double indicatorScore = indicators.confluenceScore;
        double mlScore = mlValidation.mlConfidence;
        
        // Apply signal consistency bonuses
        double signalConsistency = calculateSignalConsistency(technical, timeframe, indicators, mlValidation);
        
        // Calculate weighted confidence
        double weightedConfidence = 
            (technicalScore * technicalWeight) +
            (timeframeScore * timeframeWeight) +
            (indicatorScore * indicatorWeight) +
            (mlScore * mlWeight);
        
        // Apply Phase 2 bonuses
        double alignmentBonus = timeframeAnalyzer.getAlignmentBonus(timeframe);
        double confluenceBonus = indicatorsEngine.getConfidenceBoost(indicators);
        double validationBonus = mlValidator.getValidationBoost(mlValidation);
        
        // Calculate final confidence
        double finalConfidence = weightedConfidence + alignmentBonus + confluenceBonus + validationBonus;
        finalConfidence += signalConsistency * 5; // Consistency bonus
        
        // Ensure bounds
        finalConfidence = Math.max(0, Math.min(100, finalConfidence));
        
        // Determine final signal using majority voting with confidence thresholds
        String finalSignal = determineAdvancedSignal(technical, timeframe, indicators, mlValidation, finalConfidence);
        
        // Calculate quality metrics
        boolean isHighConfidence = finalConfidence > 75 && signalConsistency > 0.6;
        String qualityRating = determineQualityRating(finalConfidence, signalConsistency, 
                                                    timeframe.overallConfidence, indicators.confluenceScore);
        
        double currentPrice = priceHistory.get(priceHistory.size() - 1).price;
        
        // Generate detailed reasoning
        String combinedReasoning = generateAdvancedReasoning(
            technicalScore, timeframeScore, indicatorScore, mlScore,
            alignmentBonus, confluenceBonus, validationBonus, signalConsistency, finalConfidence);
        
        return new AdvancedTradingCall(
            symbol, finalSignal, finalConfidence, currentPrice,
            technical.reasoning,
            timeframe.reasoning,
            indicators.reasoning,
            mlValidation.reasoning,
            combinedReasoning,
            timeframe.overallConfidence,
            indicators.confluenceScore,
            mlValidation.validationScore,
            isHighConfidence,
            qualityRating
        );
    }
    
    /**
     * Calculate signal consistency across all components
     */
    private double calculateSignalConsistency(AdvancedTechnicalAnalyzer.TechnicalResult technical,
                                            MultiTimeframeAnalyzer.MultiTimeframeResult timeframe,
                                            AdvancedIndicatorsEngine.AdvancedIndicatorsResult indicators,
                                            MLSignalValidator.ValidationResult mlValidation) {
        
        int bullishVotes = 0;
        int bearishVotes = 0;
        int neutralVotes = 0;
        int totalVotes = 4;
        
        // Technical vote
        if ("BUY".equals(technical.signal)) bullishVotes++;
        else if ("SELL".equals(technical.signal)) bearishVotes++;
        else neutralVotes++;
        
        // Timeframe vote
        if ("BULLISH".equals(timeframe.overallTrend)) bullishVotes++;
        else if ("BEARISH".equals(timeframe.overallTrend)) bearishVotes++;
        else neutralVotes++;
        
        // Indicators vote
        if ("BULLISH".equals(indicators.overallSignal)) bullishVotes++;
        else if ("BEARISH".equals(indicators.overallSignal)) bearishVotes++;
        else neutralVotes++;
        
        // ML vote
        if ("BUY".equals(mlValidation.mlSignal)) bullishVotes++;
        else if ("SELL".equals(mlValidation.mlSignal)) bearishVotes++;
        else neutralVotes++;
        
        // Calculate consistency (majority agreement)
        int maxVotes = Math.max(bullishVotes, Math.max(bearishVotes, neutralVotes));
        return (double) maxVotes / totalVotes;
    }
    
    /**
     * Determine final signal using advanced logic
     */
    private String determineAdvancedSignal(AdvancedTechnicalAnalyzer.TechnicalResult technical,
                                         MultiTimeframeAnalyzer.MultiTimeframeResult timeframe,
                                         AdvancedIndicatorsEngine.AdvancedIndicatorsResult indicators,
                                         MLSignalValidator.ValidationResult mlValidation,
                                         double finalConfidence) {
        
        // High confidence threshold
        if (finalConfidence < 65) {
            return "HOLD";
        }
        
        // Majority voting with hierarchy
        int bullishScore = 0;
        int bearishScore = 0;
        
        // Timeframe gets highest priority (Phase 2 focus)
        if ("BULLISH".equals(timeframe.overallTrend)) bullishScore += 3;
        else if ("BEARISH".equals(timeframe.overallTrend)) bearishScore += 3;
        
        // Indicators get second priority
        if ("BULLISH".equals(indicators.overallSignal)) bullishScore += 2;
        else if ("BEARISH".equals(indicators.overallSignal)) bearishScore += 2;
        
        // Technical analysis
        if ("BUY".equals(technical.signal)) bullishScore += 2;
        else if ("SELL".equals(technical.signal)) bearishScore += 2;
        
        // ML validation (lower weight but important for confirmation)
        if ("BUY".equals(mlValidation.mlSignal) && mlValidation.isReliable) bullishScore += 1;
        else if ("SELL".equals(mlValidation.mlSignal) && mlValidation.isReliable) bearishScore += 1;
        
        // Decision with margin requirement
        if (bullishScore > bearishScore + 1) return "BUY";
        if (bearishScore > bullishScore + 1) return "SELL";
        
        return "HOLD";
    }
    
    /**
     * Determine quality rating based on multiple factors
     */
    private String determineQualityRating(double finalConfidence, double signalConsistency,
                                        double timeframeConfidence, double indicatorConfluence) {
        
        double qualityScore = (finalConfidence + (signalConsistency * 100) + 
                             timeframeConfidence + indicatorConfluence) / 4;
        
        if (qualityScore > 85) return "EXCELLENT";
        if (qualityScore > 75) return "VERY_GOOD";
        if (qualityScore > 65) return "GOOD";
        if (qualityScore > 55) return "FAIR";
        return "POOR";
    }
    
    /**
     * Generate advanced reasoning explanation
     */
    private String generateAdvancedReasoning(double technical, double timeframe, double indicator, double ml,
                                           double alignmentBonus, double confluenceBonus, double validationBonus,
                                           double consistency, double finalConfidence) {
        
        return String.format("Phase2: Tech(%.1f) + Time(%.1f) + Ind(%.1f) + ML(%.1f) " +
                           "+ Align(%.1f) + Conf(%.1f) + Val(%.1f) + Consist(%.1f%%) = %.1f%%",
                           technical, timeframe, indicator, ml,
                           alignmentBonus, confluenceBonus, validationBonus,
                           consistency * 100, finalConfidence);
    }
    
    /**
     * Create error call for exception cases
     */
    private AdvancedTradingCall createErrorCall(String symbol, List<SimpleMarketData> priceHistory) {
        double currentPrice = priceHistory.isEmpty() ? 0 : priceHistory.get(priceHistory.size() - 1).price;
        
        return new AdvancedTradingCall(
            symbol, "HOLD", 50.0, currentPrice,
            "Error in technical analysis",
            "Error in timeframe analysis",
            "Error in indicator analysis",
            "Error in ML validation",
            "Analysis error occurred",
            0.0, 0.0, 0.0, false, "ERROR"
        );
    }
    
    /**
     * Train all ML components with historical data
     */
    public void trainAdvancedModel(List<SimpleMarketData> historicalData) {
        logger.info("Training Phase 2 advanced models with {} data points", historicalData.size());
        
        try {
            mlValidator.trainAdvancedModel(historicalData);
            logger.info("✅ Phase 2 model training completed");
        } catch (Exception e) {
            logger.error("❌ Error training Phase 2 models: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get Phase 2 performance metrics
     */
    public String getPhase2Status() {
        double avgAccuracy = totalCallsGenerated > 0 ? cumulativeAccuracy / totalCallsGenerated : 0;
        
        return String.format("Phase 2 Status: %s | Advanced Calls: %d | Avg Accuracy: %.1f%% | Initialized: %s",
            isInitialized ? "ACTIVE" : "INACTIVE",
            totalCallsGenerated,
            avgAccuracy,
            isInitialized);
    }
    
    /**
     * Get recent high-quality calls
     */
    public List<AdvancedTradingCall> getHighQualityCalls(int count) {
        return tradingHistory.stream()
            .filter(call -> call.isHighConfidence)
            .limit(count)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Run comprehensive Phase 2 test
     */
    public void runPhase2ComprehensiveTest(String symbol, List<SimpleMarketData> testData) {
        logger.info("=== Running Phase 2 Comprehensive Test for {} ===", symbol);
        
        try {
            // Test all individual components
            logger.info("Testing technical analysis...");
            AdvancedTechnicalAnalyzer.TechnicalResult tech = 
                technicalAnalyzer.calculateEnhancedConfidence(testData, symbol);
            logger.info("✅ Technical: {} ({}%)", tech.signal, tech.confidence);
            
            logger.info("Testing multi-timeframe analysis...");
            MultiTimeframeAnalyzer.MultiTimeframeResult timeframe = 
                timeframeAnalyzer.analyzeMultipleTimeframes(testData);
            logger.info("✅ Timeframe: {} ({}%, {})", timeframe.overallTrend, 
                timeframe.overallConfidence, timeframe.alignment);
            
            logger.info("Testing advanced indicators...");
            AdvancedIndicatorsEngine.AdvancedIndicatorsResult indicators = 
                indicatorsEngine.analyzeAdvancedIndicators(testData);
            logger.info("✅ Indicators: {} ({}% confluence)", indicators.overallSignal, 
                indicators.confluenceScore);
            
            logger.info("Testing ML validation...");
            MLSignalValidator.ValidationResult ml = 
                mlValidator.validateSignal(testData, tech, timeframe, indicators);
            logger.info("✅ ML: {} ({}% confidence, {} reliable)", ml.mlSignal, 
                ml.mlConfidence, ml.isReliable ? "YES" : "NO");
            
            // Test integrated system
            logger.info("Testing integrated Phase 2 system...");
            AdvancedTradingCall call = generateAdvancedTradingCall(symbol, testData);
            logger.info("✅ Integrated: {} ({}%, {})", call.signal, call.confidence, call.qualityRating);
            
            logger.info("=== Phase 2 Comprehensive Test COMPLETED ===");
            
        } catch (Exception e) {
            logger.error("❌ Phase 2 test failed: {}", e.getMessage(), e);
        }
    }
}