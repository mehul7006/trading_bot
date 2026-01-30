import java.util.List;

public class TestRealSystem {
    public static void main(String[] args) {
        System.out.println("🚀 Testing RealUpstoxTradingSystem...");
        
        try {
            RealUpstoxTradingSystem system = new RealUpstoxTradingSystem();
            
            // Test 1: Real Current Price
            System.out.println("\n1️⃣ Testing Real Current Price (NIFTY)...");
            double price = system.getRealCurrentPrice("NIFTY");
            if (price > 0) {
                System.out.println("✅ Success! Real NIFTY Price: " + price);
            } else {
                System.out.println("❌ Failed to fetch real price.");
            }
            
            // Test 2: Real Historical Data (via generateRealCall which calls getRealHistoricalData)
            System.out.println("\n2️⃣ Testing Real Call Generation (NIFTY)...");
            Object call = system.generateRealCall("NIFTY");
            
            if (call != null) {
                System.out.println("✅ Success! Real Trading Call Generated.");
            } else {
                System.out.println("⚠️ No call generated (could be due to market closed or strategy conditions).");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Exception during test:");
            e.printStackTrace();
        }
    }
}
