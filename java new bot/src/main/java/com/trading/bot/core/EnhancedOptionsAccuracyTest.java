public class EnhancedOptionsAccuracyTest {
    public static void main(String[] args) {
        System.out.println("Testing Enhanced Options Accuracy System");
        System.out.println("=======================================");
        
        EnhancedOptionsAccuracySystem system = new EnhancedOptionsAccuracySystem();
        
        // Test with NIFTY
        testSymbol(system, "NIFTY");
        
        // Test with BANKNIFTY
        testSymbol(system, "BANKNIFTY");
    }
    
    private static void testSymbol(EnhancedOptionsAccuracySystem system, String symbol) {
        System.out.println("\nTesting " + symbol + " Options Analysis");
        System.out.println("-----------------------------");
        
        OptionsSignal signal = system.generateOptionsCall(symbol);
        if (signal != null) {
            System.out.println(signal.toString());
        } else {
            System.out.println("No valid signal generated for " + symbol);
            System.out.println("Conditions not met for high-accuracy trade");
        }
    }
}