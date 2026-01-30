import java.time.LocalDateTime;
import java.util.*;

public class SimpleBot {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();    private static int callsGenerated = 0;
    private static int profitTrades = 0;
    private static int stopLosses = 0;
    
    public static void main(String[] args) {
        System.out.println("🚀 SIMPLE TRADING BOT STARTED");
        System.out.println("📊 Generating trading calls...");
        
        Random random = new Random();
        
        // Generate 10 sample trading calls
        for (int i = 0; i < 10; i++) {
            callsGenerated++;
            String symbol = random.nextBoolean() ? "NIFTY" : "SENSEX";
            String direction = random.nextBoolean() ? "BUY" : "SELL";
            double price = symbol.equals("NIFTY") ? realData.getRealPrice("NIFTY") + random.nextInt(100) : realData.getRealPrice("SENSEX") + random.nextInt(500);
            
            System.out.println("📞 CALL " + callsGenerated + ": " + symbol + " " + direction + " at " + price);
            
            // Simulate trade result
            if (random.nextDouble() > 0.4) { // 60% win rate
                profitTrades++;
                System.out.println("✅ PROFIT: +" + (10 + random.nextInt(40)) + " points");
            } else {
                stopLosses++;
                System.out.println("❌ STOP LOSS: -" + (5 + random.nextInt(25)) + " points");
            }
            
            try { Thread.sleep(2000); } catch (Exception e) {}
        }
        
        // Final report
        System.out.println("\n📊 FINAL REPORT:");
        System.out.println("📞 Calls Generated: " + callsGenerated);
        System.out.println("✅ Profit Trades: " + profitTrades);
        System.out.println("❌ Stop Losses: " + stopLosses);
        System.out.println("🎯 Win Rate: " + (profitTrades * 100 / callsGenerated) + "%");
        System.out.println("✅ BOT TEST COMPLETED!");
    }
}
