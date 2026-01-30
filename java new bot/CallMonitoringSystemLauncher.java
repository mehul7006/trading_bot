import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * COMPREHENSIVE CALL MONITORING SYSTEM LAUNCHER
 * 
 * Point 4 Implementation - Complete Solution:
 * ✅ Real-time monitoring of generated calls
 * ✅ Movement tracking as per prediction
 * ✅ Target achievement notifications (50%, 100%)
 * ✅ Wrong direction detection with cost analysis
 * ✅ Progressive notifications and alerts
 * ✅ Automatic closure recommendations
 */
public class CallMonitoringSystemLauncher {
    
    public static void main(String[] args) {
        System.out.println("🚀 COMPREHENSIVE CALL MONITORING SYSTEM");
        System.out.println("=========================================");
        System.out.println();
        System.out.println("📋 FEATURES INCLUDED:");
        System.out.println("1. ✅ Real-time call monitoring every 30 seconds");
        System.out.println("2. ✅ Movement tracking as per prediction direction");
        System.out.println("3. ✅ Target achievement checking (50% & 100%)");
        System.out.println("4. ✅ Wrong direction detection with instant alerts");
        System.out.println("5. ✅ Cost-to-close calculation for failed calls");
        System.out.println("6. ✅ Progressive notifications throughout call lifecycle");
        System.out.println("7. ✅ Automatic closure recommendations");
        System.out.println("8. ✅ Performance tracking and historical records");
        System.out.println();
        
        // Initialize components
        NotificationSystem notificationSystem = new NotificationSystem();
        ComprehensiveCallMonitoringSystem monitoringSystem = 
            new ComprehensiveCallMonitoringSystem(notificationSystem);
        EnhancedCallGenerator callGenerator = 
            new EnhancedCallGenerator(monitoringSystem);
        
        // Start systems
        notificationSystem.start();
        monitoringSystem.startMonitoring();
        
        System.out.println("✅ All systems initialized and started!");
        System.out.println();
        
        // Interactive menu
        runInteractiveMenu(callGenerator, monitoringSystem, notificationSystem);
        
        // Cleanup
        System.out.println("\n🛑 Shutting down systems...");
        monitoringSystem.stopMonitoring();
        notificationSystem.stop();
        System.out.println("✅ Shutdown complete!");
    }
    
    private static void runInteractiveMenu(EnhancedCallGenerator callGenerator,
                                         ComprehensiveCallMonitoringSystem monitoringSystem,
                                         NotificationSystem notificationSystem) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        
        while (running) {
            printMenu();
            System.out.print("Enter your choice: ");
            
            try {
                int choice = scanner.nextInt();
                scanner.nextLine(); // consume newline
                
                switch (choice) {
                    case 1 -> generateNiftyCall(callGenerator);
                    case 2 -> generateBankNiftyCall(callGenerator);
                    case 3 -> generateMultipleCalls(callGenerator);
                    case 4 -> showActiveCallsStatus(monitoringSystem);
                    case 5 -> showPerformanceSummary(monitoringSystem);
                    case 6 -> runDemoScenario(callGenerator);
                    case 7 -> showRecentNotifications(notificationSystem);
                    case 8 -> {
                        running = false;
                        System.out.println("👋 Exiting...");
                    }
                    default -> System.out.println("❌ Invalid choice. Please try again.");
                }
                
                if (running) {
                    System.out.println("\nPress Enter to continue...");
                    scanner.nextLine();
                }
                
            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
                scanner.nextLine(); // consume invalid input
            }
        }
        
        scanner.close();
    }
    
    private static void printMenu() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("🎯 CALL MONITORING SYSTEM - MAIN MENU");
        System.out.println("=".repeat(50));
        System.out.println("1. 📈 Generate NIFTY Call");
        System.out.println("2. 🏦 Generate BANKNIFTY Call");
        System.out.println("3. 🔄 Generate Multiple Calls");
        System.out.println("4. 👁️  Show Active Calls Status");
        System.out.println("5. 📊 Show Performance Summary");
        System.out.println("6. 🎬 Run Demo Scenario");
        System.out.println("7. 📢 Show Recent Notifications");
        System.out.println("8. 🚪 Exit");
        System.out.println("=".repeat(50));
    }
    
    private static void generateNiftyCall(EnhancedCallGenerator callGenerator) {
        System.out.println("\n📈 Generating NIFTY Call...");
        System.out.println("-".repeat(30));
        
        GeneratedCall call = callGenerator.generateCall("NIFTY");
        
        if (call != null) {
            System.out.println("✅ NIFTY Call Generated Successfully!");
            System.out.println(call.getDetailedString());
            System.out.println("\n🔍 Call is now being monitored in real-time.");
            System.out.println("📢 You will receive notifications for:");
            System.out.println("   • Movement started as predicted");
            System.out.println("   • 50% target achievement");
            System.out.println("   • 100% target achievement");
            System.out.println("   • Wrong direction warnings");
            System.out.println("   • Closure recommendations");
        } else {
            System.out.println("⚠️ Could not generate NIFTY call at this time.");
            System.out.println("   Reason: Market conditions not suitable");
        }
    }
    
    private static void generateBankNiftyCall(EnhancedCallGenerator callGenerator) {
        System.out.println("\n🏦 Generating BANKNIFTY Call...");
        System.out.println("-".repeat(35));
        
        GeneratedCall call = callGenerator.generateCall("BANKNIFTY");
        
        if (call != null) {
            System.out.println("✅ BANKNIFTY Call Generated Successfully!");
            System.out.println(call.getDetailedString());
            System.out.println("\n🔍 Call is now being monitored in real-time.");
        } else {
            System.out.println("⚠️ Could not generate BANKNIFTY call at this time.");
        }
    }
    
    private static void generateMultipleCalls(EnhancedCallGenerator callGenerator) {
        System.out.println("\n🔄 Generating Multiple Calls...");
        System.out.println("-".repeat(35));
        
        String[] symbols = {"NIFTY", "BANKNIFTY", "FINNIFTY"};
        List<GeneratedCall> calls = callGenerator.generateMultipleCalls(symbols);
        
        System.out.println(String.format("✅ Generated %d calls:", calls.size()));
        
        for (GeneratedCall call : calls) {
            System.out.println(String.format("   📊 %s: %s %s @ ₹%.2f → ₹%.2f", 
                call.getSymbol(), 
                call.getStrategy(),
                call.getExpectedDirection(),
                call.getEntryPrice(),
                call.getTargetPrice()
            ));
        }
        
        System.out.println("\n🔍 All calls are now being monitored in real-time.");
    }
    
    private static void showActiveCallsStatus(ComprehensiveCallMonitoringSystem monitoringSystem) {
        System.out.println("\n👁️ Active Calls Status");
        System.out.println("-".repeat(25));
        
        Map<String, ActiveCall> activeCalls = monitoringSystem.getActiveCallsMap();
        
        if (activeCalls.isEmpty()) {
            System.out.println("📭 No active calls being monitored.");
            return;
        }
        
        System.out.println(String.format("📊 Currently monitoring %d active calls:\n", activeCalls.size()));
        
        int index = 1;
        for (ActiveCall call : activeCalls.values()) {
            MonitoringResult latestResult = call.getLatestResult();
            
            System.out.println(String.format("%d. %s", index++, call.toString()));
            
            if (latestResult != null) {
                System.out.println(String.format("   📈 Current: ₹%.2f (%.2f%%)", 
                    latestResult.getCurrentPrice(),
                    latestResult.getPercentageMovement()
                ));
                System.out.println(String.format("   🎯 Target Progress: %.1f%%", 
                    latestResult.getTargetAchievement() * 100
                ));
                System.out.println(String.format("   💰 P&L: ₹%.2f", 
                    latestResult.getProfitLoss()
                ));
                System.out.println(String.format("   ⏰ Duration: %d minutes", 
                    call.getDurationInMinutes()
                ));
            }
            
            System.out.println();
        }
    }
    
    private static void showPerformanceSummary(ComprehensiveCallMonitoringSystem monitoringSystem) {
        System.out.println("\n📊 Performance Summary");
        System.out.println("-".repeat(25));
        
        List<CallPerformanceRecord> records = monitoringSystem.getHistoricalRecords();
        Map<String, ActiveCall> activeCalls = monitoringSystem.getActiveCallsMap();
        
        int totalCalls = records.size();
        int activeCalls_count = activeCalls.size();
        
        if (totalCalls == 0 && activeCalls_count == 0) {
            System.out.println("📭 No calls have been generated yet.");
            return;
        }
        
        // Calculate statistics
        long successfulCalls = records.stream().mapToLong(r -> r.wasSuccessful() ? 1 : 0).sum();
        double totalPnL = records.stream().mapToDouble(CallPerformanceRecord::getFinalProfitLoss).sum();
        double successRate = totalCalls > 0 ? (successfulCalls * 100.0 / totalCalls) : 0;
        
        System.out.println(String.format("📈 Total Calls Generated: %d", totalCalls + activeCalls_count));
        System.out.println(String.format("🔍 Currently Active: %d", activeCalls_count));
        System.out.println(String.format("🏁 Completed: %d", totalCalls));
        System.out.println(String.format("✅ Successful: %d (%.1f%%)", successfulCalls, successRate));
        System.out.println(String.format("💰 Total P&L: ₹%.2f", totalPnL));
        
        if (!records.isEmpty()) {
            double avgDuration = records.stream().mapToLong(CallPerformanceRecord::getDurationMinutes).average().orElse(0);
            System.out.println(String.format("⏱️ Average Duration: %.0f minutes", avgDuration));
            
            System.out.println("\n🏆 Recent Completed Calls:");
            records.stream().skip(Math.max(0, records.size() - 3)).forEach(record -> {
                System.out.println(String.format("   %s: %s (%.2f%% return)", 
                    record.getSymbol(),
                    record.wasSuccessful() ? "SUCCESS ✅" : "LOSS ❌",
                    record.getReturnPercentage()
                ));
            });
        }
    }
    
    private static void runDemoScenario(EnhancedCallGenerator callGenerator) {
        System.out.println("\n🎬 Running Demo Scenario...");
        System.out.println("-".repeat(30));
        System.out.println("This will generate sample calls to demonstrate monitoring features.");
        System.out.println("Watch for real-time notifications showing:");
        System.out.println("• Movement detection");
        System.out.println("• Target progress (50%, 100%)");
        System.out.println("• Wrong direction alerts");
        System.out.println("• Closure recommendations");
        System.out.println();
        
        // Generate demo calls
        System.out.println("📊 Generating demo calls...");
        callGenerator.generateMultipleCalls("NIFTY", "BANKNIFTY");
        
        System.out.println("✅ Demo calls generated! Monitor the notifications.");
        System.out.println("🔍 The monitoring system will provide real-time updates.");
    }
    
    private static void showRecentNotifications(NotificationSystem notificationSystem) {
        System.out.println("\n📢 Recent Notifications");
        System.out.println("-".repeat(25));
        
        List<NotificationSystem.NotificationMessage> recent = 
            notificationSystem.getRecentNotifications(10);
        
        if (recent.isEmpty()) {
            System.out.println("📭 No recent notifications.");
            return;
        }
        
        System.out.println(String.format("📱 Last %d notifications:\n", recent.size()));
        
        for (NotificationSystem.NotificationMessage notification : recent) {
            System.out.println(String.format("%s %s", 
                notification.getPriority().getEmoji(),
                notification.getFormattedMessage()
            ));
        }
        
        System.out.println(String.format("\n📊 Pending notifications: %d", 
            notificationSystem.getPendingNotificationCount()));
    }
    
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}