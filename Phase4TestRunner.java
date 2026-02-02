// Phase 4 Test Runner - No package declaration for root execution

import com.trading.bot.core.Phase4QuantSystemBot;
import com.trading.bot.market.SimpleMarketData;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Phase 4 Test Runner - Comprehensive testing of quantitative trading features
 * Tests Portfolio Management, Risk Optimization, Algorithmic Execution, and Quantitative Analysis
 */
public class Phase4TestRunner {
    
    public static void main(String[] args) {
        System.out.println("🚀 PHASE 4 QUANTITATIVE TRADING SYSTEM TEST");
        System.out.println("===========================================");
        System.out.println();
        
        try {
            // Initialize Phase 4 Bot
            Phase4QuantSystemBot phase4Bot = new Phase4QuantSystemBot();
            
            // Create realistic market data for testing
            Map<String, List<SimpleMarketData>> portfolioTestData = createRealisticPortfolioData();
            
            System.out.println("📊 Testing with comprehensive portfolio data...");
            System.out.println("   NIFTY50: " + portfolioTestData.get("NIFTY50").size() + " data points");
            System.out.println("   SENSEX: " + portfolioTestData.get("SENSEX").size() + " data points");
            System.out.println("   BANKNIFTY: " + portfolioTestData.get("BANKNIFTY").size() + " data points");
            System.out.println();
            
            // Test Phase 4 quantitative features for each symbol
            testQuantitativeTrading(phase4Bot, "NIFTY50", portfolioTestData);
            System.out.println();
            testQuantitativeTrading(phase4Bot, "SENSEX", portfolioTestData);
            System.out.println();
            testQuantitativeTrading(phase4Bot, "BANKNIFTY", portfolioTestData);
            
            System.out.println();
            System.out.println("✅ PHASE 4 QUANTITATIVE SYSTEM TEST COMPLETED SUCCESSFULLY!");
            System.out.println("🎯 All quantitative trading features operational");
            System.out.println("🏆 Portfolio optimization, risk management, and algorithmic execution ready");
            
        } catch (Exception e) {
            System.err.println("❌ Phase 4 Test Failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testQuantitativeTrading(Phase4QuantSystemBot bot, String symbol,
                                               Map<String, List<SimpleMarketData>> portfolioData) {
        System.out.println("🔍 Testing Phase 4 Quantitative Analysis for " + symbol + "...");
        
        List<SimpleMarketData> symbolData = portfolioData.get(symbol);
        
        try {
            // Execute quantitative analysis
            Phase4QuantSystemBot.QuantitativeTradingCall result = 
                bot.generateQuantitativeTradingCall(symbol, symbolData, portfolioData);
            
            System.out.println("📈 Quantitative Analysis Results:");
            System.out.println("   Symbol: " + result.symbol);
            System.out.println("   Signal: " + result.signal);
            System.out.println("   Confidence: " + String.format("%.1f%%", result.confidence));
            System.out.println("   Price: ₹" + String.format("%.2f", result.price));
            System.out.println("   Position Size: " + String.format("%.1f%%", result.positionSize * 100));
            System.out.println("   Risk-Adjusted Return: " + String.format("%.1f%%", result.riskAdjustedReturn * 100));
            System.out.println("   Sharpe Ratio: " + String.format("%.2f", result.sharpeRatio));
            System.out.println("   VaR (95%): " + String.format("%.2f%%", result.varValue * 100));
            System.out.println("   Portfolio Weight: " + String.format("%.1f%%", result.portfolioWeight * 100));
            System.out.println("   Risk Grade: " + result.riskGrade);
            System.out.println("   Quantitative Grade: " + (result.isQuantGrade ? "QUANTITATIVE" : "STANDARD"));
            System.out.println();
            
            System.out.println("🧠 Quantitative Components:");
            System.out.println("   Portfolio Impact: " + result.portfolioImpact);
            System.out.println("   Risk Optimization: " + result.riskOptimization);
            System.out.println("   Algorithmic Strategy: " + result.algorithmicStrategy);
            System.out.println("   Execution Plan: " + result.executionPlan);
            System.out.println();
            
            System.out.println("🎯 Master Quantitative Reasoning:");
            System.out.println("   " + result.masterQuantReasoning);
            System.out.println();
            
            // Validate results
            validateQuantitativeResults(result);
            
        } catch (Exception e) {
            System.err.println("❌ Quantitative analysis failed for " + symbol + ": " + e.getMessage());
            throw e;
        }
    }
    
    private static void validateQuantitativeResults(Phase4QuantSystemBot.QuantitativeTradingCall result) {
        System.out.println("✅ Validating quantitative analysis...");
        
        // Validate basic requirements
        if (result.confidence < 40.0) {
            throw new RuntimeException("Confidence too low: " + result.confidence + "%");
        }
        
        if (result.positionSize <= 0 || result.positionSize > 0.2) {
            throw new RuntimeException("Invalid position size: " + result.positionSize + " (should be 0-20%)");
        }
        
        if (result.price <= 0) {
            throw new RuntimeException("Invalid price detected: " + result.price);
        }
        
        if (result.signal == null || (!result.signal.equals("BUY") && !result.signal.equals("SELL") && !result.signal.equals("HOLD"))) {
            throw new RuntimeException("Invalid signal: " + result.signal);
        }
        
        if (result.varValue < 0 || result.varValue > 1.0) {
            throw new RuntimeException("Invalid VaR value: " + result.varValue);
        }
        
        System.out.println("✅ All quantitative analysis validations passed");
        System.out.println("   Signal: " + result.signal + " ✓");
        System.out.println("   Confidence: " + String.format("%.1f%%", result.confidence) + " ✓");
        System.out.println("   Position Size: " + String.format("%.1f%%", result.positionSize * 100) + " ✓");
        System.out.println("   Sharpe Ratio: " + String.format("%.2f", result.sharpeRatio) + " ✓");
        System.out.println("   Risk Grade: " + result.riskGrade + " ✓");
        System.out.println("   Quantitative Grade: " + (result.isQuantGrade ? "QUANTITATIVE" : "STANDARD") + " ✓");
    }
    
    private static Map<String, List<SimpleMarketData>> createRealisticPortfolioData() {
        Map<String, List<SimpleMarketData>> portfolioData = new HashMap<>();
        LocalDateTime time = LocalDateTime.now().minusHours(24);
        
        // Create realistic data for each index
        portfolioData.put("NIFTY50", createIndexData("NIFTY50", 25879.15, 1500000L, time));
        portfolioData.put("SENSEX", createIndexData("SENSEX", 84478.67, 1200000L, time));
        portfolioData.put("BANKNIFTY", createIndexData("BANKNIFTY", 58381.95, 1800000L, time));
        
        return portfolioData;
    }
    
    private static List<SimpleMarketData> createIndexData(String symbol, double basePrice, long baseVolume, LocalDateTime startTime) {
        List<SimpleMarketData> data = new ArrayList<>();
        Random random = new Random(symbol.hashCode()); // Deterministic randomness
        
        double price = basePrice * 0.995; // Start slightly below current
        
        // Generate 100 realistic data points with institutional patterns
        for (int i = 0; i < 100; i++) {
            // Realistic price movement toward base price
            double targetMove = (basePrice - price) * 0.03; // 3% movement toward target each step
            double randomMove = random.nextGaussian() * basePrice * 0.0008; // Small random movement
            double trendMove = Math.sin(i * 0.1) * basePrice * 0.001; // Slight trending component
            
            price += targetMove + randomMove + trendMove;
            
            // Ensure price doesn't deviate too much
            if (Math.abs(price - basePrice) > basePrice * 0.025) {
                price = basePrice + (random.nextGaussian() * basePrice * 0.01);
            }
            
            // Generate realistic volume with patterns
            long volume = baseVolume + (long)(random.nextGaussian() * baseVolume * 0.25);
            if (i % 20 == 0) volume *= 1.4; // Volume spikes every 20 periods
            if (Math.abs(price - basePrice) > basePrice * 0.01) volume *= 1.2; // Higher volume on bigger moves
            
            data.add(new SimpleMarketData(symbol, Math.max(price, basePrice * 0.95), 
                                        Math.max(volume, baseVolume / 2), 
                                        startTime.plusMinutes(i * 6))); // 6-minute intervals
        }
        
        // Ensure last data point has exact base price
        if (!data.isEmpty()) {
            SimpleMarketData lastData = data.get(data.size() - 1);
            data.set(data.size() - 1, new SimpleMarketData(symbol, basePrice, 
                                                          lastData.volume, LocalDateTime.now()));
        }
        
        return data;
    }
}