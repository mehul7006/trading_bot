import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MINIMAL WORKING BOT - No Dependencies
 * Compiles and runs without external libraries
 */
public class MinimalWorkingBot {
    
    public static void main(String[] args) {
        System.out.println("🚀 MINIMAL TRADING BOT STARTED");
        System.out.println("================================");
        
        // Test basic functionality
        testBasicFunctions();
        
        // Simulate trading loop
        simulateTradingLoop();
        
        System.out.println("✅ Bot completed successfully!");
    }
    
    private static void testBasicFunctions() {
        System.out.println("📊 Testing basic functions...");
        
        // Test time formatting
        String timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("   Current time: " + timestamp);
        
        // Test simple calculations
        double rsi = calculateSimpleRSI();
        System.out.printf("   Sample RSI: %.2f%n", rsi);
        
        // Test market simulation
        double price = simulatePrice("TCS");
        System.out.printf("   Simulated TCS price: ₹%.2f%n", price);
    }
    
    private static void simulateTradingLoop() {
        System.out.println("🔄 Running trading simulation...");
        
        String[] symbols = {"TCS", "RELIANCE", "HDFCBANK", "INFY"};
        
        for (int i = 0; i < 3; i++) {
            System.out.printf("%n--- Trading Cycle %d ---%n", i + 1);
            
            for (String symbol : symbols) {
                analyzeStock(symbol);
            }
            
            try {
                Thread.sleep(1000); // 1 second delay
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    private static void analyzeStock(String symbol) {
        double price = simulatePrice(symbol);
        double rsi = calculateSimpleRSI();
        String signal = generateSignal(rsi);
        
        System.out.printf("📈 %s: ₹%.2f | RSI: %.1f | Signal: %s%n", 
            symbol, price, rsi, signal);
    }
    
    private static double simulatePrice(String symbol) {
        // Simulate realistic prices for different stocks
        double basePrice = switch (symbol) {
            case "TCS" -> 3500.0;
            case "RELIANCE" -> 2400.0;
            case "HDFCBANK" -> 1600.0;
            case "INFY" -> 1800.0;
            default -> 1000.0;
        };
        
        // Add some random variation (+/- 5%)
        double variation = (Math.random() - 0.5) * 0.1;
        return basePrice * (1 + variation);
    }
    
    private static double calculateSimpleRSI() {
        // Simulate RSI between 20-80
        return 20 + Math.random() * 60;
    }
    
    private static String generateSignal(double rsi) {
        if (rsi < 30) return "🟢 BUY";
        if (rsi > 70) return "🔴 SELL";
        return "🟡 HOLD";
    }
}
