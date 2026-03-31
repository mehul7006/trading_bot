package com.trading.bot.core;

import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.smartmoney.OrderBlockDetector;
import com.trading.bot.smartmoney.FairValueGapDetector;
import com.trading.bot.smartmoney.LiquidityAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * PHASE 3: Smart Money Concepts Integrated Bot
 * Combines Order Blocks, Fair Value Gaps, and Liquidity Analysis
 * Target: 90-93% → 95%+ accuracy with institutional-level analysis
 */
public class Phase3IntegratedBot {
    private static final Logger logger = LoggerFactory.getLogger(Phase3IntegratedBot.class);
    
    // Smart Money Components
    private final OrderBlockDetector orderBlockDetector;
    private final FairValueGapDetector fvgDetector;
    private final LiquidityAnalyzer liquidityAnalyzer;
    
    // Previous phases integration
    private final Phase2IntegratedBot phase2Bot;
    
    // Bot state
    private final List<InstitutionalTradingCall> tradingHistory;
    private boolean isInitialized = false;
    private int totalCallsGenerated = 0;
    
    public static class InstitutionalTradingCall {
        public final String symbol;
        public final String signal;
        public final double confidence;
        public final double price;
        public final LocalDateTime timestamp;
        
        // Phase 3 Smart Money Analysis
        public final String orderBlockAnalysis;
        public final String fvgAnalysis;
        public final String liquidityAnalysis;
        public final String smartMoneyBias;
        public final String institutionalStrategy;
        
        // Combined reasoning
        public final String phase1Analysis;
        public final String phase2Analysis;
        public final String phase3Analysis;
        public final String masterReasoning;
        
        // Advanced metrics
        public final double orderBlockBoost;
        public final double fvgBoost;
        public final double liquidityBoost;
        public final double smartMoneyScore;
        public final boolean isInstitutionalGrade;
        public final String riskAssessment;
        
        public InstitutionalTradingCall(String symbol, String signal, double confidence, double price,
                                      String orderBlockAnalysis, String fvgAnalysis, String liquidityAnalysis,
                                      String smartMoneyBias, String institutionalStrategy,
                                      String phase1Analysis, String phase2Analysis, String phase3Analysis,
                                      String masterReasoning, double orderBlockBoost, double fvgBoost,
                                      double liquidityBoost, double smartMoneyScore,
                                      boolean isInstitutionalGrade, String riskAssessment) {
            this.symbol = symbol;
            this.signal = signal;
            this.confidence = confidence;
            this.price = price;
            this.timestamp = LocalDateTime.now();
            this.orderBlockAnalysis = orderBlockAnalysis;
            this.fvgAnalysis = fvgAnalysis;
            this.liquidityAnalysis = liquidityAnalysis;
            this.smartMoneyBias = smartMoneyBias;
            this.institutionalStrategy = institutionalStrategy;
            this.phase1Analysis = phase1Analysis;
            this.phase2Analysis = phase2Analysis;
            this.phase3Analysis = phase3Analysis;
            this.masterReasoning = masterReasoning;
            this.orderBlockBoost = orderBlockBoost;
            this.fvgBoost = fvgBoost;
            this.liquidityBoost = liquidityBoost;
            this.smartMoneyScore = smartMoneyScore;
            this.isInstitutionalGrade = isInstitutionalGrade;
            this.riskAssessment = riskAssessment;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s: %s (%.1f%%) at %.2f - Smart Money: %.1f%% (%s)\n" +
                               "  📊 Order Blocks: %s\n" +
                               "  🔄 Fair Value Gaps: %s\n" +
                               "  💧 Liquidity: %s\n" +
                               "  🏦 Institutional Strategy: %s\n" +
                               "  🎯 Master Analysis: %s",
                timestamp, symbol, signal, confidence, price, smartMoneyScore,
                isInstitutionalGrade ? "INSTITUTIONAL" : "RETAIL",
                orderBlockAnalysis, fvgAnalysis, liquidityAnalysis,
                institutionalStrategy, masterReasoning);
        }
        
        public String getCompactString() {
            return String.format("[%s] %s: %s (%.1f%%) - Smart Money: %.1f%% %s", 
                timestamp, symbol, signal, confidence, smartMoneyScore,
                isInstitutionalGrade ? "🏦" : "👤");
        }
    }
    
    public Phase3IntegratedBot() {
        this.orderBlockDetector = new OrderBlockDetector();
        this.fvgDetector = new FairValueGapDetector();
        this.liquidityAnalyzer = new LiquidityAnalyzer();
        this.phase2Bot = new Phase2IntegratedBot();
        this.tradingHistory = new ArrayList<>();
    }
    
    /**
     * Initialize Phase 3 with Smart Money Concepts
     */
    public void initialize() {
        logger.info("=== Initializing Phase 3 Smart Money Concepts Bot ===");
        
        try {
            logger.info("Initializing Phase 2 foundation...");
            phase2Bot.initialize();
            
            logger.info("Initializing Order Block Detector...");
            // Order block detector is stateless, ready to use
            
            logger.info("Initializing Fair Value Gap Detector...");
            // FVG detector is stateless, ready to use
            
            logger.info("Initializing Liquidity Analyzer...");
            // Liquidity analyzer is stateless, ready to use
            
            isInitialized = true;
            logger.info("✅ Phase 3 Smart Money Concepts Bot initialized successfully");
            
        } catch (Exception e) {
            logger.error("❌ Failed to initialize Phase 3 bot: {}", e.getMessage(), e);
            isInitialized = false;
        }
    }
    
    /**
     * PHASE 3: Generate institutional-grade trading call
     */
    public InstitutionalTradingCall generateInstitutionalTradingCall(String symbol, List<SimpleMarketData> priceHistory) {
        if (!isInitialized) {
            logger.warn("Bot not initialized. Initializing now...");
            initialize();
        }
        
        logger.info("=== Generating Phase 3 Institutional Trading Call for {} ===", symbol);
        
        try {
            // PHASE 3 - STEP 1: Run Phase 2 analysis as foundation
            Phase2IntegratedBot.AdvancedTradingCall phase2Call = 
                phase2Bot.generateAdvancedTradingCall(symbol, priceHistory);
            logger.debug("Phase 2 foundation: {}", phase2Call.getCompactString());
            
            // PHASE 3 - STEP 2: Order Block Analysis
            OrderBlockDetector.OrderBlockAnalysis orderBlockAnalysis = 
                orderBlockDetector.detectOrderBlocks(priceHistory);
            logger.debug("Order blocks: {} detected, bias: {:.1f}%", 
                orderBlockAnalysis.detectedBlocks.size(), orderBlockAnalysis.institutionalBias);
            
            // PHASE 3 - STEP 3: Fair Value Gap Analysis
            FairValueGapDetector.FVGAnalysis fvgAnalysis = 
                fvgDetector.detectFairValueGaps(priceHistory);
            logger.debug("Fair Value Gaps: {} detected, strength: {:.1f}%", 
                fvgAnalysis.detectedGaps.size(), fvgAnalysis.imbalanceStrength);
            
            // PHASE 3 - STEP 4: Liquidity Analysis
            LiquidityAnalyzer.LiquidityAnalysis liquidityAnalysis = 
                liquidityAnalyzer.analyzeLiquidity(priceHistory);
            logger.debug("Liquidity: {} pools, score: {:.1f}%, bias: {}", 
                liquidityAnalysis.detectedPools.size(), liquidityAnalysis.liquidityScore, 
                liquidityAnalysis.smartMoneyBias);
            
            // PHASE 3 - STEP 5: Smart Money Integration
            InstitutionalTradingCall finalCall = combineSmartMoneyAnalysis(
                symbol, priceHistory, phase2Call, orderBlockAnalysis, fvgAnalysis, liquidityAnalysis);
            
            tradingHistory.add(finalCall);
            totalCallsGenerated++;
            
            logger.info("✅ Generated Phase 3 institutional call: {}", finalCall.getCompactString());
            logger.debug("Full institutional analysis:\n{}", finalCall);
            
            return finalCall;
            
        } catch (Exception e) {
            logger.error("❌ Error generating Phase 3 institutional call: {}", e.getMessage(), e);
            return createErrorCall(symbol, priceHistory);
        }
    }
    
    /**
     * PHASE 3: Combine Smart Money analysis with previous phases
     */
    private InstitutionalTradingCall combineSmartMoneyAnalysis(String symbol, 
                                                             List<SimpleMarketData> priceHistory,
                                                             Phase2IntegratedBot.AdvancedTradingCall phase2Call,
                                                             OrderBlockDetector.OrderBlockAnalysis orderBlocks,
                                                             FairValueGapDetector.FVGAnalysis fvgs,
                                                             LiquidityAnalyzer.LiquidityAnalysis liquidity) {
        
        logger.debug("Combining Smart Money analysis...");
        
        // Calculate Smart Money confidence boosts
        double orderBlockBoost = orderBlockDetector.getOrderBlockConfidenceBoost(orderBlocks);
        double fvgBoost = fvgDetector.getFVGConfidenceBoost(fvgs);
        double liquidityBoost = liquidityAnalyzer.getLiquidityConfidenceBoost(liquidity);
        
        // Calculate base confidence from Phase 2
        double baseConfidence = phase2Call.confidence;
        
        // Apply Smart Money boosts with diminishing returns
        double enhancedConfidence = baseConfidence + 
            (orderBlockBoost * 0.4) + 
            (fvgBoost * 0.3) + 
            (liquidityBoost * 0.3);
        
        // Apply Smart Money signal validation
        String smartMoneySignal = validateWithSmartMoney(phase2Call.signal, orderBlocks, fvgs, liquidity);
        
        // Calculate Smart Money Score (0-100%)
        double smartMoneyScore = calculateSmartMoneyScore(orderBlocks, fvgs, liquidity);
        
        // Determine if this is institutional-grade
        boolean isInstitutionalGrade = isInstitutionalGradeSignal(smartMoneyScore, enhancedConfidence, orderBlocks, fvgs, liquidity);
        
        // Apply institutional-grade adjustments
        if (isInstitutionalGrade) {
            enhancedConfidence += 5; // Institutional bonus
        }
        
        // Ensure confidence bounds
        enhancedConfidence = Math.max(0, Math.min(100, enhancedConfidence));
        
        // Generate Smart Money bias and strategy
        String smartMoneyBias = determineSmartMoneyBias(orderBlocks, fvgs, liquidity);
        String institutionalStrategy = generateInstitutionalStrategy(orderBlocks, fvgs, liquidity, smartMoneySignal);
        
        // Create analysis summaries
        String orderBlockSummary = summarizeOrderBlocks(orderBlocks);
        String fvgSummary = summarizeFVGs(fvgs);
        String liquiditySummary = summarizeLiquidity(liquidity);
        
        // Generate Phase 3 analysis
        String phase3Analysis = String.format("Smart Money: OB(+%.1f) + FVG(+%.1f) + LIQ(+%.1f) = %.1f%% | %s",
            orderBlockBoost, fvgBoost, liquidityBoost, smartMoneyScore, smartMoneyBias);
        
        // Generate master reasoning
        String masterReasoning = generateMasterReasoning(
            phase2Call, smartMoneyScore, smartMoneyBias, isInstitutionalGrade, enhancedConfidence);
        
        // Risk assessment
        String riskAssessment = assessInstitutionalRisk(orderBlocks, fvgs, liquidity, enhancedConfidence);
        
        double currentPrice = priceHistory.get(priceHistory.size() - 1).price;
        
        return new InstitutionalTradingCall(
            symbol, smartMoneySignal, enhancedConfidence, currentPrice,
            orderBlockSummary, fvgSummary, liquiditySummary,
            smartMoneyBias, institutionalStrategy,
            "Technical Analysis + ML", 
            phase2Call.getCompactString(),
            phase3Analysis, masterReasoning,
            orderBlockBoost, fvgBoost, liquidityBoost, smartMoneyScore,
            isInstitutionalGrade, riskAssessment
        );
    }
    
    /**
     * Validate signal with Smart Money concepts
     */
    private String validateWithSmartMoney(String phase2Signal, 
                                        OrderBlockDetector.OrderBlockAnalysis orderBlocks,
                                        FairValueGapDetector.FVGAnalysis fvgs,
                                        LiquidityAnalyzer.LiquidityAnalysis liquidity) {
        
        int bullishVotes = 0;
        int bearishVotes = 0;
        int neutralVotes = 0;
        
        // Phase 2 vote
        switch (phase2Signal) {
            case "BUY" -> bullishVotes++;
            case "SELL" -> bearishVotes++;
            default -> neutralVotes++;
        }
        
        // Order Block vote
        if (orderBlocks.institutionalBias > 65) bullishVotes++;
        else if (orderBlocks.institutionalBias < 35) bearishVotes++;
        else neutralVotes++;
        
        // FVG vote
        switch (fvgs.overallImbalance) {
            case "BULLISH_IMBALANCE" -> bullishVotes++;
            case "BEARISH_IMBALANCE" -> bearishVotes++;
            default -> neutralVotes++;
        }
        
        // Liquidity vote
        switch (liquidity.smartMoneyBias) {
            case "BULLISH_BIAS", "TARGETING_LONGS" -> bullishVotes++;
            case "BEARISH_BIAS", "TARGETING_SHORTS" -> bearishVotes++;
            default -> neutralVotes++;
        }
        
        // Majority decision with Smart Money priority
        if (bullishVotes > bearishVotes + neutralVotes) return "BUY";
        if (bearishVotes > bullishVotes + neutralVotes) return "SELL";
        
        // If tied, trust Phase 2 signal
        return phase2Signal;
    }
    
    /**
     * Calculate Smart Money Score
     */
    private double calculateSmartMoneyScore(OrderBlockDetector.OrderBlockAnalysis orderBlocks,
                                          FairValueGapDetector.FVGAnalysis fvgs,
                                          LiquidityAnalyzer.LiquidityAnalysis liquidity) {
        
        double score = 0;
        
        // Order Block contribution (35%)
        double obScore = (orderBlocks.detectedBlocks.size() * 5) + 
                        (Math.abs(orderBlocks.institutionalBias - 50) * 0.3) +
                        (orderBlocks.marketStructure.contains("BULLISH") || orderBlocks.marketStructure.contains("BEARISH") ? 10 : 0);
        score += Math.min(35, obScore);
        
        // FVG contribution (30%)
        double fvgScore = (fvgs.detectedGaps.size() * 3) + 
                         (fvgs.imbalanceStrength * 0.25) +
                         (fvgs.nearestUnfilledGap != null ? 8 : 0);
        score += Math.min(30, fvgScore);
        
        // Liquidity contribution (35%)
        double liqScore = liquidity.liquidityScore * 0.35;
        score += Math.min(35, liqScore);
        
        return Math.min(100, score);
    }
    
    /**
     * Determine if signal is institutional-grade
     */
    private boolean isInstitutionalGradeSignal(double smartMoneyScore, double confidence,
                                             OrderBlockDetector.OrderBlockAnalysis orderBlocks,
                                             FairValueGapDetector.FVGAnalysis fvgs,
                                             LiquidityAnalyzer.LiquidityAnalysis liquidity) {
        
        // Criteria for institutional grade:
        // 1. Smart Money Score > 70%
        // 2. Confidence > 75%
        // 3. At least 2 Smart Money concepts showing strong signals
        // 4. Clear institutional bias
        
        if (smartMoneyScore < 70 || confidence < 75) return false;
        
        int strongSignals = 0;
        
        if (orderBlocks.detectedBlocks.size() >= 2 && Math.abs(orderBlocks.institutionalBias - 50) > 20) {
            strongSignals++;
        }
        
        if (fvgs.imbalanceStrength > 60 && fvgs.nearestUnfilledGap != null) {
            strongSignals++;
        }
        
        if (liquidity.liquidityScore > 70 && !liquidity.smartMoneyBias.equals("NEUTRAL_BIAS")) {
            strongSignals++;
        }
        
        return strongSignals >= 2;
    }
    
    /**
     * Determine overall Smart Money bias
     */
    private String determineSmartMoneyBias(OrderBlockDetector.OrderBlockAnalysis orderBlocks,
                                         FairValueGapDetector.FVGAnalysis fvgs,
                                         LiquidityAnalyzer.LiquidityAnalysis liquidity) {
        
        int bullishScore = 0;
        int bearishScore = 0;
        
        // Order Block bias
        if (orderBlocks.institutionalBias > 60) bullishScore += 2;
        else if (orderBlocks.institutionalBias < 40) bearishScore += 2;
        
        // FVG bias
        if ("BULLISH_IMBALANCE".equals(fvgs.overallImbalance)) bullishScore += 2;
        else if ("BEARISH_IMBALANCE".equals(fvgs.overallImbalance)) bearishScore += 2;
        
        // Liquidity bias
        if (liquidity.smartMoneyBias.contains("BULLISH")) bullishScore += 2;
        else if (liquidity.smartMoneyBias.contains("BEARISH")) bearishScore += 2;
        
        if (bullishScore > bearishScore + 1) return "INSTITUTIONAL_BULLISH";
        if (bearishScore > bullishScore + 1) return "INSTITUTIONAL_BEARISH";
        return "INSTITUTIONAL_NEUTRAL";
    }
    
    /**
     * Generate institutional strategy
     */
    private String generateInstitutionalStrategy(OrderBlockDetector.OrderBlockAnalysis orderBlocks,
                                                FairValueGapDetector.FVGAnalysis fvgs,
                                                LiquidityAnalyzer.LiquidityAnalysis liquidity,
                                                String signal) {
        
        StringBuilder strategy = new StringBuilder();
        
        strategy.append("INSTITUTIONAL STRATEGY: ");
        
        if ("BUY".equals(signal)) {
            strategy.append("Look for institutional accumulation. ");
            if (orderBlocks.nearestSupport != null) {
                strategy.append(String.format("Support at %.2f (Order Block). ", orderBlocks.nearestSupport.highPrice));
            }
            if (fvgs.nearestUnfilledGap != null && fvgs.nearestUnfilledGap.type == FairValueGapDetector.FVGType.BULLISH_FVG) {
                strategy.append("Bullish FVG provides entry opportunity. ");
            }
        } else if ("SELL".equals(signal)) {
            strategy.append("Look for institutional distribution. ");
            if (orderBlocks.nearestResistance != null) {
                strategy.append(String.format("Resistance at %.2f (Order Block). ", orderBlocks.nearestResistance.lowPrice));
            }
            if (fvgs.nearestUnfilledGap != null && fvgs.nearestUnfilledGap.type == FairValueGapDetector.FVGType.BEARISH_FVG) {
                strategy.append("Bearish FVG provides entry opportunity. ");
            }
        } else {
            strategy.append("Range-bound institutional activity. Wait for clear Smart Money direction. ");
        }
        
        // Add liquidity guidance
        if (liquidity.nextTargetPool != null) {
            strategy.append(String.format("Watch liquidity at %.2f - %s probability hunt. ",
                liquidity.nextTargetPool.priceLevel, liquidity.huntProbability));
        }
        
        return strategy.toString();
    }
    
    // Summary methods for analysis components
    private String summarizeOrderBlocks(OrderBlockDetector.OrderBlockAnalysis analysis) {
        if (analysis.detectedBlocks.isEmpty()) {
            return "No significant order blocks detected";
        }
        return String.format("%d blocks detected, %.1f%% institutional bias, %s structure",
            analysis.detectedBlocks.size(), analysis.institutionalBias, analysis.marketStructure);
    }
    
    private String summarizeFVGs(FairValueGapDetector.FVGAnalysis analysis) {
        if (analysis.detectedGaps.isEmpty()) {
            return "No significant fair value gaps detected";
        }
        return String.format("%d gaps detected, %.1f%% imbalance strength, %s bias",
            analysis.detectedGaps.size(), analysis.imbalanceStrength, analysis.overallImbalance);
    }
    
    private String summarizeLiquidity(LiquidityAnalyzer.LiquidityAnalysis analysis) {
        if (analysis.detectedPools.isEmpty()) {
            return "No significant liquidity pools detected";
        }
        return String.format("%d pools detected, %.1f%% score, %s, %s hunt probability",
            analysis.detectedPools.size(), analysis.liquidityScore, 
            analysis.smartMoneyBias, analysis.huntProbability);
    }
    
    /**
     * Generate master reasoning combining all phases
     */
    private String generateMasterReasoning(Phase2IntegratedBot.AdvancedTradingCall phase2Call,
                                         double smartMoneyScore, String smartMoneyBias,
                                         boolean isInstitutionalGrade, double finalConfidence) {
        
        return String.format("Phase3: %s + Smart Money(%.1f%%) = %s %.1f%% | %s | Grade: %s",
            phase2Call.signal, smartMoneyScore, 
            finalConfidence > phase2Call.confidence ? "ENHANCED" : "TEMPERED",
            finalConfidence, smartMoneyBias, 
            isInstitutionalGrade ? "INSTITUTIONAL" : "RETAIL");
    }
    
    /**
     * Assess institutional risk
     */
    private String assessInstitutionalRisk(OrderBlockDetector.OrderBlockAnalysis orderBlocks,
                                         FairValueGapDetector.FVGAnalysis fvgs,
                                         LiquidityAnalyzer.LiquidityAnalysis liquidity,
                                         double confidence) {
        
        int riskFactors = 0;
        
        // Risk factors
        if (orderBlocks.detectedBlocks.isEmpty()) riskFactors++;
        if (fvgs.detectedGaps.isEmpty()) riskFactors++;
        if (liquidity.detectedPools.isEmpty()) riskFactors++;
        if (confidence < 70) riskFactors++;
        if (liquidity.huntProbability.equals("VERY_HIGH")) riskFactors--; // Reduces risk
        
        switch (riskFactors) {
            case 0, 1 -> { return "LOW_RISK - Strong Smart Money confluence"; }
            case 2 -> { return "MODERATE_RISK - Some Smart Money support"; }
            case 3 -> { return "HIGH_RISK - Limited Smart Money validation"; }
            default -> { return "VERY_HIGH_RISK - Weak Smart Money signals"; }
        }
    }
    
    /**
     * Create error call
     */
    private InstitutionalTradingCall createErrorCall(String symbol, List<SimpleMarketData> priceHistory) {
        double currentPrice = priceHistory.isEmpty() ? 0 : priceHistory.get(priceHistory.size() - 1).price;
        
        return new InstitutionalTradingCall(
            symbol, "HOLD", 50.0, currentPrice,
            "Error in order block analysis",
            "Error in FVG analysis", 
            "Error in liquidity analysis",
            "ANALYSIS_ERROR", "Wait for system recovery",
            "Error", "Error", "Error", "Analysis error occurred",
            0.0, 0.0, 0.0, 0.0, false, "ERROR_STATE"
        );
    }
    
    /**
     * Train Phase 3 system
     */
    public void trainAdvancedModels(List<SimpleMarketData> historicalData) {
        logger.info("Training Phase 3 Smart Money models with {} data points", historicalData.size());
        
        try {
            // Train Phase 2 foundation
            phase2Bot.trainAdvancedModel(historicalData);
            
            // Smart Money components are pattern-based and don't require training
            logger.info("✅ Phase 3 Smart Money training completed");
            
        } catch (Exception e) {
            logger.error("❌ Error training Phase 3 models: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get Phase 3 status
     */
    public String getPhase3Status() {
        return String.format("Phase 3 Status: %s | Smart Money Calls: %d | Components: OB+FVG+LIQ | Initialized: %s",
            isInitialized ? "ACTIVE" : "INACTIVE",
            totalCallsGenerated,
            isInitialized);
    }
    
    /**
     * Get institutional-grade calls only
     */
    public List<InstitutionalTradingCall> getInstitutionalCalls(int count) {
        return tradingHistory.stream()
            .filter(call -> call.isInstitutionalGrade)
            .limit(count)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Run comprehensive Phase 3 test
     */
    public void runPhase3ComprehensiveTest(String symbol, List<SimpleMarketData> testData) {
        logger.info("=== Running Phase 3 Smart Money Comprehensive Test for {} ===", symbol);
        
        try {
            // Test individual Smart Money components
            logger.info("Testing Order Block Detection...");
            OrderBlockDetector.OrderBlockAnalysis ob = orderBlockDetector.detectOrderBlocks(testData);
            logger.info("✅ Order Blocks: {} detected, {:.1f}% institutional bias", 
                ob.detectedBlocks.size(), ob.institutionalBias);
            
            logger.info("Testing Fair Value Gap Detection...");
            FairValueGapDetector.FVGAnalysis fvg = fvgDetector.detectFairValueGaps(testData);
            logger.info("✅ FVGs: {} detected, {:.1f}% imbalance strength", 
                fvg.detectedGaps.size(), fvg.imbalanceStrength);
            
            logger.info("Testing Liquidity Analysis...");
            LiquidityAnalyzer.LiquidityAnalysis liq = liquidityAnalyzer.analyzeLiquidity(testData);
            logger.info("✅ Liquidity: {} pools, {:.1f}% score, {} bias", 
                liq.detectedPools.size(), liq.liquidityScore, liq.smartMoneyBias);
            
            // Test integrated Phase 3 system
            logger.info("Testing integrated Phase 3 Smart Money system...");
            InstitutionalTradingCall call = generateInstitutionalTradingCall(symbol, testData);
            logger.info("✅ Institutional Call: {} ({:.1f}%, Smart Money: {:.1f}%, Grade: {})",
                call.signal, call.confidence, call.smartMoneyScore, 
                call.isInstitutionalGrade ? "INSTITUTIONAL" : "RETAIL");
            
            logger.info("=== Phase 3 Smart Money Comprehensive Test COMPLETED ===");
            
        } catch (Exception e) {
            logger.error("❌ Phase 3 test failed: {}", e.getMessage(), e);
        }
    }
}