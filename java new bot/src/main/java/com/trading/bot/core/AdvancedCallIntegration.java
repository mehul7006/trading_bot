/**
 * Simple integration bridge for Advanced Call Generator
 */
public class AdvancedCallIntegration {
    private static AdvancedCallGenerator_Coordinator coordinator;
    private static boolean isInitialized = false;
    
    public static void initialize() {
        if (!isInitialized) {
            try {
                coordinator = new AdvancedCallGenerator_Coordinator();
                coordinator.initializeCompleteSystem();
                isInitialized = true;
                System.out.println("✅ Advanced Call Generator integrated successfully");
            } catch (Exception e) {
                System.err.println("❌ Failed to initialize: " + e.getMessage());
            }
        }
    }
    
    public static String handleAdvancedCallCommand(String chatId, String message) {
        if (!isInitialized) {
            initialize();
        }
        
        if (coordinator != null) {
            return coordinator.handleAdvancedCallCommand(chatId, message);
        } else {
            return "❌ Advanced Call Generator not available. Please try again later.";
        }
    }
    
    public static String getQuickAdvancedCall(String symbol) {
        if (!isInitialized) {
            initialize();
        }
        
        if (coordinator != null) {
            double randomPrice = 1000 + Math.random() * 4000;
            return coordinator.generateAdvancedCall(symbol, randomPrice);
        } else {
            return "❌ Unable to generate advanced call for " + symbol;
        }
    }
}
