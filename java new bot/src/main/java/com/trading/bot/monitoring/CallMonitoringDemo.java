package com.trading.bot.monitoring;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DEMO: Comprehensive Call Monitoring System
 * Shows all the features you requested in Point 4
 */
public class CallMonitoringDemo {
    
    public static void main(String[] args) {
        System.out.println("🚀 COMPREHENSIVE CALL MONITORING SYSTEM DEMO");
        System.out.println("===========================================");
        System.out.println("📋 Features Demonstrated:");
        System.out.println("1. Real-time call monitoring");
        System.out.println("2. Movement tracking as per prediction");
        System.out.println("3. Target achievement checking (50%, 100%)");
        System.out.println("4. Wrong call detection with cost analysis");
        System.out.println("5. Progressive notifications");
        System.out.println("6. Automatic closure recommendations");
        System.out.println();
        
        // Initialize notification system
        NotificationSystem notificationSystem = new NotificationSystem();
        notificationSystem.start();
        
        // Initialize monitoring system
        ComprehensiveCallMonitoringSystem monitoringSystem = 
            new ComprehensiveCallMonitoringSystem(notificationSystem);
        
        // Start monitoring
        monitoringSystem.startMonitoring();
        
        // Demo scenario 1: Successful NIFTY call
        demonstrateSuccessfulCall(monitoringSystem);
        
        // Wait a bit
        sleep(2000);
        
        // Demo scenario 2: Failed BANKNIFTY call
        demonstrateFailedCall(monitoringSystem);
        
        // Wait a bit
        sleep(2000);
        
        // Demo scenario 3: Call going wrong then recovering
        demonstrateRecoveringCall(monitoringSystem);
        
        // Let the system run for a while to show monitoring
        System.out.println("\n🔍 Letting monitoring system run for 30 seconds...");
        System.out.println("Watch for real-time notifications!\n");
        
        sleep(30000); // 30 seconds
        
        // Stop systems
        System.out.println("\n🛑 Stopping monitoring system...");
        monitoringSystem.stopMonitoring();
        notificationSystem.stop();
        
        System.out.println("✅ Demo completed!");
    }
    
    /**
     * Demonstrate successful NIFTY call reaching target
     */
    private static void demonstrateSuccessfulCall(ComprehensiveCallMonitoringSystem monitoringSystem) {
        System.out.println("📈 DEMO SCENARIO 1: Successful NIFTY Call");
        System.out.println("=========================================");
        
        // Create a NIFTY call that will be successful
        GeneratedCall niftyCall = GeneratedCall.builder()
            .symbol("NIFTY")
            .callType("25000CE")
            .expectedDirection("BULLISH")
            .entryPrice(23400.0)
            .targetPrice(23600.0)  // 200 point target
            .investmentAmount(10000.0)
            .confidence(0.85)
            .strategy("Long Call")
            .rationale("Strong bullish momentum with volume confirmation")
            .build();
        
        // Add to monitoring
        monitoringSystem.addCallForMonitoring(niftyCall);
        
        System.out.println("✅ Added successful NIFTY call to monitoring");
        System.out.println("   Entry: ₹23,400 → Target: ₹23,600");
        System.out.println("   Expected: Will reach 50% then 100% target");
        System.out.println();
    }
    
    /**
     * Demonstrate failed BANKNIFTY call
     */
    private static void demonstrateFailedCall(ComprehensiveCallMonitoringSystem monitoringSystem) {
        System.out.println("📉 DEMO SCENARIO 2: Failed BANKNIFTY Call");
        System.out.println("==========================================");
        
        // Create a BANKNIFTY call that will fail
        GeneratedCall bankNiftyCall = GeneratedCall.builder()
            .symbol("BANKNIFTY")
            .callType("50000PE")
            .expectedDirection("BEARISH")
            .entryPrice(50100.0)
            .targetPrice(49800.0)  // 300 point target down
            .investmentAmount(15000.0)
            .confidence(0.78)
            .strategy("Long Put")
            .rationale("Bearish divergence with high put-call ratio")
            .build();
        
        // Add to monitoring
        monitoringSystem.addCallForMonitoring(bankNiftyCall);
        
        System.out.println("✅ Added failing BANKNIFTY call to monitoring");
        System.out.println("   Entry: ₹50,100 → Target: ₹49,800");
        System.out.println("   Expected: Will go wrong direction and trigger alerts");
        System.out.println();
    }
    
    /**
     * Demonstrate call that goes wrong initially but then recovers
     */
    private static void demonstrateRecoveringCall(ComprehensiveCallMonitoringSystem monitoringSystem) {
        System.out.println("🔄 DEMO SCENARIO 3: Recovering NIFTY Call");
        System.out.println("==========================================");
        
        // Create a call that will recover after initial loss
        GeneratedCall recoveringCall = GeneratedCall.builder()
            .symbol("NIFTY")
            .callType("23500CE")
            .expectedDirection("BULLISH")
            .entryPrice(23450.0)
            .targetPrice(23650.0)  // 200 point target
            .investmentAmount(12000.0)
            .confidence(0.72)
            .strategy("Long Call")
            .rationale("Consolidation breakout with support confirmation")
            .build();
        
        // Add to monitoring
        monitoringSystem.addCallForMonitoring(recoveringCall);
        
        System.out.println("✅ Added recovering NIFTY call to monitoring");
        System.out.println("   Entry: ₹23,450 → Target: ₹23,650");
        System.out.println("   Expected: Will dip first, then recover to target");
        System.out.println();
    }
    
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}