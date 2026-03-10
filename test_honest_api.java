import com.trading.bot.market.HonestMarketDataFetcher;
import java.util.Map;

public class test_honest_api {
    public static void main(String[] args) {
        System.out.println("🧪 TESTING HONEST MARKET DATA FETCHER");
        System.out.println("=====================================");
        
        HonestMarketDataFetcher fetcher = HonestMarketDataFetcher.getInstance();
        
        try {
            System.out.println("📊 Testing honest market snapshot...");
            Map<String, Double> prices = fetcher.getHonestMarketSnapshot();
            
            System.out.println("✅ HONEST TEST RESULTS:");
            for (Map.Entry<String, Double> entry : prices.entrySet()) {
                System.out.println("📈 " + entry.getKey() + ": ₹" + String.format("%.2f", entry.getValue()));
            }
            
        } catch (Exception e) {
            System.err.println("❌ HONEST TEST FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}