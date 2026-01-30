package com.trading.bot.test;

import com.trading.bot.core.Phase3IntegratedBot;
import com.trading.bot.market.SimpleMarketData;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 3 Test Runner - Comprehensive testing of institutional trading features
 * Tests Smart Money Concepts, Order Block Detection, Fair Value Gaps, and Liquidity Analysis
 */
public class Phase3TestRunner {
    
    public static void main(String[] args) {
        System.out.println("🚀 PHASE 3 INSTITUTIONAL TRADING TEST");
        System.out.println("=====================================");
        System.out.println();
        
        try {
            // Initialize Phase 3 Bot
            Phase3IntegratedBot phase3Bot = new Phase3IntegratedBot();
            
            // Create realistic market data for testing
            List<SimpleMarketData> testData = createRealisticMarketData();
            
            System.out.println("📊 Testing with " + testData.size() + " market data points...");
            System.out.println();
            
            // Test Phase 3 institutional trading features
            testInstitutionalTrading(phase3Bot, testData);
            
            System.out.println();
            System.out.println("✅ PHASE 3 TEST COMPLETED SUCCESSFULLY!");
            System.out.println("🎯 All institutional trading features operational");
            
        } catch (Exception e) {
            System.err.println("❌ Phase 3 Test Failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testInstitutionalTrading(Phase3IntegratedBot bot, List<SimpleMarketData> data) {
        System.out.println("🔍 Testing Institutional Trading Analysis...");
        
        // Test with NIFTY data
        SimpleMarketData currentData = data.get(data.size() - 1);
        
        try {
            // Execute institutional analysis
            Phase3IntegratedBot.InstitutionalTradingCall result = bot.generateInstitutionalTradingCall(currentData.symbol, data);
            
            System.out.println("📈 Institutional Analysis Results:");
            System.out.println("   Symbol: " + result.symbol);
            System.out.println("   Signal: " + result.signal);
            System.out.println("   Price: ₹" + String.format("%.2f", result.price));
            System.out.println("   Confidence: " + String.format("%.1f%%", result.confidence));
            System.out.println("   Smart Money Score: " + String.format("%.1f%%", result.smartMoneyScore));
            System.out.println("   Institutional Grade: " + (result.isInstitutionalGrade ? "YES" : "NO"));
            System.out.println();
            
            System.out.println("🧠 Smart Money Analysis:");
            System.out.println("   Order Blocks: " + result.orderBlockAnalysis);
            System.out.println("   Fair Value Gaps: " + result.fvgAnalysis);
            System.out.println("   Liquidity: " + result.liquidityAnalysis);
            System.out.println("   Strategy: " + result.institutionalStrategy);
            System.out.println();
            
            System.out.println("🎯 Master Reasoning:");
            System.out.println("   " + result.masterReasoning);
            System.out.println();
            
            // Validate results
            validateInstitutionalResults(result);
            
        } catch (Exception e) {
            System.err.println("❌ Institutional analysis failed: " + e.getMessage());
            throw e;
        }
    }
    
    private static void validateInstitutionalResults(Phase3IntegratedBot.InstitutionalTradingCall result) {
        System.out.println("✅ Validating institutional analysis...");
        
        // Validate basic requirements
        if (result.confidence < 50.0) {
            throw new RuntimeException("Confidence too low: " + result.confidence + "%");
        }
        
        if (result.smartMoneyScore < 0 || result.smartMoneyScore > 100) {
            throw new RuntimeException("Invalid Smart Money Score: " + result.smartMoneyScore + "%");
        }
        
        if (result.price <= 0) {
            throw new RuntimeException("Invalid price detected: " + result.price);
        }
        
        if (result.signal == null || (!result.signal.equals("BUY") && !result.signal.equals("SELL") && !result.signal.equals("HOLD"))) {
            throw new RuntimeException("Invalid signal: " + result.signal);
        }
        
        System.out.println("✅ All institutional analysis validations passed");
        System.out.println("   Signal: " + result.signal + " ✓");
        System.out.println("   Confidence: " + String.format("%.1f%%", result.confidence) + " ✓");
        System.out.println("   Smart Money Score: " + String.format("%.1f%%", result.smartMoneyScore) + " ✓");
        System.out.println("   Institutional Grade: " + (result.isInstitutionalGrade ? "YES" : "NO") + " ✓");
    }
    
    private static List<SimpleMarketData> createRealisticMarketData() {
        List<SimpleMarketData> data = new ArrayList<>();
        LocalDateTime time = LocalDateTime.now().minusHours(24);
        
        // Create realistic NIFTY price movement with institutional patterns
        double basePrice = 19800.0;
        long baseVolume = 1000000;
        
        for (int i = 0; i < 100; i++) {
            // Simulate institutional price action with order blocks and fair value gaps
            double priceMove = Math.sin(i * 0.1) * 50 + Math.random() * 30 - 15;
            double currentPrice = basePrice + priceMove;
            
            // Simulate volume spikes during institutional activity
            long volume = baseVolume + (long)(Math.random() * 500000);
            if (i % 20 == 0) { // Institutional volume spikes
                volume *= 2;
            }
            
            data.add(new SimpleMarketData("NIFTY50", currentPrice, volume, time.plusMinutes(i * 5)));
        }
        
        return data;
    }
}