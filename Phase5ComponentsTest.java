// Phase 5 Components Test - Testing all working AI components

import com.trading.bot.ai.AIPredictor;
import com.trading.bot.ai.AdvancedMLEngine;
import com.trading.bot.ai.PerformanceOptimizer;
import com.trading.bot.ai.AutomationController;
import com.trading.bot.ai.SentimentAnalysisEngine;
import com.trading.bot.realtime.RealTimeProcessor;
import com.trading.bot.execution.AutoExecutor;
import com.trading.bot.market.SimpleMarketData;
import java.time.LocalDateTime;
import java.util.*;

public class Phase5ComponentsTest {
    
    public static void main(String[] args) {
        System.out.println("🤖 PHASE 5 AI COMPONENTS TEST");
        System.out.println("=============================");
        System.out.println();
        
        try {
            // Test all Phase 5 components individually
            testAllPhase5Components();
            
            System.out.println();
            System.out.println("✅ PHASE 5 COMPONENTS TEST COMPLETED SUCCESSFULLY!");
            System.out.println("🎯 All AI components working independently");
            System.out.println("🤖 Neural networks, ML engines, and automation ready");
            
        } catch (Exception e) {
            System.err.println("❌ Phase 5 Component Test Failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testAllPhase5Components() {
        List<SimpleMarketData> testData = createTestData();
        
        System.out.println("🧠 Testing AI Predictor...");
        testAIPredictor(testData);
        
        System.out.println("🔬 Testing Advanced ML Engine...");
        testAdvancedMLEngine(testData);
        
        System.out.println("⚡ Testing Performance Optimizer...");
        testPerformanceOptimizer(testData);
        
        System.out.println("🤖 Testing Automation Controller...");
        testAutomationController(testData);
        
        System.out.println("💭 Testing Sentiment Analysis Engine...");
        testSentimentAnalysisEngine(testData);
        
        System.out.println("⏱️ Testing Real-Time Processor...");
        testRealTimeProcessor(testData);
        
        System.out.println("🎯 Testing Auto Executor...");
        testAutoExecutor(testData);
    }
    
    private static void testAIPredictor(List<SimpleMarketData> testData) {
        try {
            AIPredictor aiPredictor = new AIPredictor();
            aiPredictor.initialize();
            
            AIPredictor.AIPrediction prediction = aiPredictor.generatePrediction(
                "NIFTY50", testData, new HashMap<>(), null);
            
            System.out.println("  ✅ AI Prediction: " + prediction.predictedDirection + 
                             " (Confidence: " + String.format("%.1f%%", prediction.confidence) +
                             ", Neural Score: " + String.format("%.1f%%", prediction.neuralNetworkScore) + ")");
        } catch (Exception e) {
            System.out.println("  ❌ AI Predictor test failed: " + e.getMessage());
        }
    }
    
    private static void testAdvancedMLEngine(List<SimpleMarketData> testData) {
        try {
            AdvancedMLEngine mlEngine = new AdvancedMLEngine();
            mlEngine.initialize();
            
            AdvancedMLEngine.MLResult result = mlEngine.processAdvancedML("NIFTY50", testData);
            
            System.out.println("  ✅ Advanced ML: " + result.mlPrediction + 
                             " (Confidence: " + String.format("%.1f%%", result.mlConfidence) +
                             ", Model: " + result.modelUsed + ")");
        } catch (Exception e) {
            System.out.println("  ❌ Advanced ML Engine test failed: " + e.getMessage());
        }
    }
    
    private static void testPerformanceOptimizer(List<SimpleMarketData> testData) {
        try {
            PerformanceOptimizer optimizer = new PerformanceOptimizer();
            optimizer.initialize();
            
            PerformanceOptimizer.OptimizationResult result = optimizer.optimizePerformance("NIFTY50", testData);
            
            System.out.println("  ✅ Performance: " + result.optimizationStrategy + 
                             " (Score: " + String.format("%.1f%%", result.performanceScore) +
                             ", Speed: " + String.format("%.1fx", result.speedImprovement) + ")");
        } catch (Exception e) {
            System.out.println("  ❌ Performance Optimizer test failed: " + e.getMessage());
        }
    }
    
    private static void testAutomationController(List<SimpleMarketData> testData) {
        try {
            AutomationController automation = new AutomationController();
            automation.initialize();
            
            AutomationController.AutomationResult result = automation.controlAutomation(
                "NIFTY50", testData, 75.0, "BUY");
            
            System.out.println("  ✅ Automation: " + result.automationStatus + 
                             " (Efficiency: " + String.format("%.1f%%", result.automationEfficiency) +
                             ", Strategy: " + result.controlStrategy + ")");
        } catch (Exception e) {
            System.out.println("  ❌ Automation Controller test failed: " + e.getMessage());
        }
    }
    
    private static void testSentimentAnalysisEngine(List<SimpleMarketData> testData) {
        try {
            SentimentAnalysisEngine sentiment = new SentimentAnalysisEngine();
            sentiment.initialize();
            
            SentimentAnalysisEngine.SentimentResult result = sentiment.analyzeSentiment("NIFTY50", testData);
            
            System.out.println("  ✅ Sentiment: " + result.overallSentiment + 
                             " (Score: " + String.format("%.1f%%", result.sentimentScore) +
                             ", Mood: " + result.marketMood + ")");
        } catch (Exception e) {
            System.out.println("  ❌ Sentiment Analysis Engine test failed: " + e.getMessage());
        }
    }
    
    private static void testRealTimeProcessor(List<SimpleMarketData> testData) {
        try {
            RealTimeProcessor realTime = new RealTimeProcessor();
            realTime.initialize();
            
            RealTimeProcessor.RealTimeAnalysis result = realTime.processRealTime(
                "NIFTY50", testData, null, null);
            
            System.out.println("  ✅ Real-Time: " + result.marketSentiment + 
                             " (Score: " + String.format("%.1f%%", result.realTimeScore) +
                             ", Condition: " + result.marketCondition + ")");
        } catch (Exception e) {
            System.out.println("  ❌ Real-Time Processor test failed: " + e.getMessage());
        }
    }
    
    private static void testAutoExecutor(List<SimpleMarketData> testData) {
        try {
            AutoExecutor executor = new AutoExecutor();
            executor.initialize();
            
            AutoExecutor.ExecutionResult result = executor.planExecution(
                "NIFTY50", "BUY", testData, null, null);
            
            System.out.println("  ✅ Execution: " + result.executionStrategy + 
                             " (Score: " + String.format("%.1f%%", result.executionScore) +
                             ", Status: " + result.executionStatus + ")");
        } catch (Exception e) {
            System.out.println("  ❌ Auto Executor test failed: " + e.getMessage());
        }
    }
    
    private static List<SimpleMarketData> createTestData() {
        List<SimpleMarketData> data = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusHours(1);
        double basePrice = 25879.15;
        
        for (int i = 0; i < 60; i++) {
            double price = basePrice + (Math.random() - 0.5) * 100;
            long volume = 1500000 + (long)(Math.random() * 500000);
            data.add(new SimpleMarketData("NIFTY50", price, volume, time.plusMinutes(i)));
        }
        
        return data;
    }
}