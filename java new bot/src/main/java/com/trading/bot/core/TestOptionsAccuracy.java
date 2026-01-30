public class TestOptionsAccuracy {
    public static void main(String[] args) {
        System.out.println("Testing Enhanced Options Accuracy System V2");
        System.out.println("==========================================");
        
        EnhancedOptionsAccuracySystemV2 system = new EnhancedOptionsAccuracySystemV2();
        
        // Test for NIFTY
        System.out.println("\nTesting NIFTY Options Analysis:");
        testSymbol(system, "NIFTY");
        
        // Test for BANKNIFTY
        System.out.println("\nTesting BANKNIFTY Options Analysis:");
        testSymbol(system, "BANKNIFTY");
    }
    
    private static void testSymbol(EnhancedOptionsAccuracySystemV2 system, String symbol) {
        System.out.println("----------------------------------------");
        OptionsSignal signal = system.generateOptionsCall(symbol);
        
        if (signal != null) {
            System.out.println(signal.toString());
        } else {
            System.out.println("No signal generated for " + symbol);
            System.out.println("Conditions not suitable for high-accuracy trade");
        }
        System.out.println("----------------------------------------");
    }
}