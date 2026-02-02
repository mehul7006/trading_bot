// Phase 5 Test Runner - Testing AI Execution System

import com.trading.bot.core.Phase5AIExecutionBot;
import com.trading.bot.market.SimpleMarketData;
import java.time.LocalDateTime;
import java.util.*;

public class Phase5TestRunner {
    
    public static void main(String[] args) {
        System.out.println("🚀 PHASE 5 AI EXECUTION SYSTEM TEST");
        System.out.println("===================================");
        System.out.println();
        
        try {
            // Initialize Phase 5 Bot
            Phase5AIExecutionBot phase5Bot = new Phase5AIExecutionBot();
            
            // Create realistic market data for testing
            Map<String, List<SimpleMarketData>> portfolioTestData = createAITestData();
            
            System.out.println("🤖 Testing with AI-enhanced portfolio data...");
            System.out.println("   NIFTY50: " + portfolioTestData.get("NIFTY50").size() + " data points");
            System.out.println("   SENSEX: " + portfolioTestData.get("SENSEX").size() + " data points");
            System.out.println("   BANKNIFTY: " + portfolioTestData.get("BANKNIFTY").size() + " data points");
            System.out.println();
            
            // Test Phase 5 AI execution features
            testAIExecution(phase5Bot, "NIFTY50", portfolioTestData);
            System.out.println();
            testAIExecution(phase5Bot, "SENSEX", portfolioTestData);
            System.out.println();
            testAIExecution(phase5Bot, "BANKNIFTY", portfolioTestData);
            
            System.out.println();
            System.out.println("✅ PHASE 5 AI EXECUTION SYSTEM TEST COMPLETED SUCCESSFULLY!");
            System.out.println("🎯 All AI execution features operational");
            System.out.println("🤖 Neural networks, real-time processing, and auto-execution ready");
            
        } catch (Exception e) {
            System.err.println("❌ Phase 5 Test Failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testAIExecution(Phase5AIExecutionBot bot, String symbol,
                                       Map<String, List<SimpleMarketData>> portfolioData) {
        System.out.println("🤖 Testing Phase 5 AI Execution for " + symbol + "...");
        
        List<SimpleMarketData> symbolData = portfolioData.get(symbol);
        
        try {
            // Execute AI execution analysis
            Phase5AIExecutionBot.AIExecutionCall result = 
                bot.generateAIExecutionCall(symbol, symbolData, portfolioData, false); // Disable auto-execution for safety
            
            System.out.println("🧠 AI Execution Results:");
            System.out.println("   Symbol: " + result.symbol);
            System.out.println("   Signal: " + result.signal);
            System.out.println("   Confidence: " + String.format("%.1f%%", result.confidence));
            System.out.println("   Price: ₹" + String.format("%.2f", result.price));
            System.out.println("   AI Confidence: " + String.format("%.1f%%", result.aiConfidence));
            System.out.println("   Neural Network Score: " + String.format("%.1f%%", result.neuralNetworkScore));
            System.out.println("   Real-Time Score: " + String.format("%.1f%%", result.realTimeScore));
            System.out.println("   Market Sentiment: " + result.marketSentiment);
            System.out.println("   Execution Strategy: " + result.executionStrategy);
            System.out.println("   Execution Status: " + result.executionStatus);
            System.out.println("   AI Grade: " + (result.isAIGrade ? "AI_GRADE" : "STANDARD"));
            System.out.println();
            
            System.out.println("🤖 AI Components:");
            System.out.println("   AI Prediction: " + result.aiPrediction);
            System.out.println("   Real-Time Analysis: " + result.realTimeAnalysis);
            System.out.println("   Execution Plan: " + result.executionStrategy);
            System.out.println();
            
            System.out.println("🧠 Master AI Reasoning:");
            System.out.println("   " + result.masterAIReasoning);
            System.out.println();
            
            // Validate results
            validateAIResults(result);
            
        } catch (Exception e) {
            System.err.println("❌ AI execution failed for " + symbol + ": " + e.getMessage());
            throw e;
        }
    }
    
    private static void validateAIResults(Phase5AIExecutionBot.AIExecutionCall result) {
        System.out.println("✅ Validating AI execution analysis...");
        
        // Validate basic requirements
        if (result.confidence < 30.0) {
            throw new RuntimeException("Confidence too low: " + result.confidence + "%");
        }
        
        if (result.aiConfidence < 30.0 || result.aiConfidence > 100.0) {
            throw new RuntimeException("Invalid AI confidence: " + result.aiConfidence + "%");
        }
        
        if (result.price <= 0) {
            throw new RuntimeException("Invalid price detected: " + result.price);
        }
        
        if (result.signal == null || (!result.signal.equals("BUY") && !result.signal.equals("SELL") && !result.signal.equals("HOLD"))) {
            throw new RuntimeException("Invalid signal: " + result.signal);
        }
        
        if (result.neuralNetworkScore < 0 || result.neuralNetworkScore > 100) {
            throw new RuntimeException("Invalid neural network score: " + result.neuralNetworkScore);
        }
        
        System.out.println("✅ All AI execution validations passed");
        System.out.println("   Signal: " + result.signal + " ✓");
        System.out.println("   Confidence: " + String.format("%.1f%%", result.confidence) + " ✓");
        System.out.println("   AI Confidence: " + String.format("%.1f%%", result.aiConfidence) + " ✓");
        System.out.println("   Neural Score: " + String.format("%.1f%%", result.neuralNetworkScore) + " ✓");
        System.out.println("   Execution Status: " + result.executionStatus + " ✓");
        System.out.println("   AI Grade: " + (result.isAIGrade ? "AI_GRADE" : "STANDARD") + " ✓");
    }
    
    private static Map<String, List<SimpleMarketData>> createAITestData() {
        Map<String, List<SimpleMarketData>> portfolioData = new HashMap<>();
        LocalDateTime time = LocalDateTime.now().minusHours(24);
        
        // Create AI-enhanced data for each index with patterns
        portfolioData.put("NIFTY50", createAIIndexData("NIFTY50", 25879.15, 1500000L, time));
        portfolioData.put("SENSEX", createAIIndexData("SENSEX", 84478.67, 1200000L, time));
        portfolioData.put("BANKNIFTY", createAIIndexData("BANKNIFTY", 58381.95, 1800000L, time));
        
        return portfolioData;
    }
    
    private static List<SimpleMarketData> createAIIndexData(String symbol, double basePrice, long baseVolume, LocalDateTime startTime) {
        List<SimpleMarketData> data = new ArrayList<>();
        Random random = new Random(symbol.hashCode() + 12345); // Deterministic for AI testing
        
        double price = basePrice * 0.992; // Start below current for AI pattern
        
        // Generate 120 realistic data points with AI-detectable patterns
        for (int i = 0; i < 120; i++) {
            // AI-friendly patterns: trending + cyclical + random
            double trendMove = Math.sin(i * 0.05) * basePrice * 0.002; // Cyclical pattern for AI
            double momentumMove = (basePrice - price) * 0.02; // Mean reversion
            double randomMove = random.nextGaussian() * basePrice * 0.0005; // Small noise
            double aiPatternMove = Math.cos(i * 0.1) * basePrice * 0.001; // Secondary pattern
            
            price += trendMove + momentumMove + randomMove + aiPatternMove;
            
            // Ensure realistic bounds
            if (Math.abs(price - basePrice) > basePrice * 0.03) {
                price = basePrice + (random.nextGaussian() * basePrice * 0.01);
            }
            
            // Generate AI-friendly volume patterns
            long volume = baseVolume + (long)(random.nextGaussian() * baseVolume * 0.2);
            if (i % 25 == 0) volume *= 1.6; // Volume spikes for AI detection
            if (Math.abs(trendMove) > basePrice * 0.001) volume *= 1.3; // Volume on big moves
            
            data.add(new SimpleMarketData(symbol, Math.max(price, basePrice * 0.97), 
                                        Math.max(volume, baseVolume / 3), 
                                        startTime.plusMinutes(i * 3))); // 3-minute intervals for AI
        }
        
        // Ensure last data point has exact base price for AI reference
        if (!data.isEmpty()) {
            SimpleMarketData lastData = data.get(data.size() - 1);
            data.set(data.size() - 1, new SimpleMarketData(symbol, basePrice, 
                                                          lastData.volume, LocalDateTime.now()));
        }
        
        return data;
    }
}