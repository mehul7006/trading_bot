import java.io.*;
import java.net.*;
import java.net.http.*;
import java.util.*;

/**
 * SIMPLE UPSTOX API - No External Dependencies
 * Basic price fetching without Jackson
 */
public class SimpleUpstoxApiFixed {
    
    // Load from environment variables for security
    private static final String ACCESS_TOKEN = System.getenv("UPSTOX_ACCESS_TOKEN");
    private static final String API_KEY = System.getenv("UPSTOX_API_KEY");
    
    private static final String BASE_URL = "https://api.upstox.com/v2";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    
    public static double getPrice(String symbol) {
        try {
            String instrumentKey = getInstrumentKey(symbol);
            if (instrumentKey == null) {
                return -1.0;
            }
            
            String url = BASE_URL + "/market-quote/ltp?instrument_key=" + instrumentKey;
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .header("Accept", "application/json")
                .GET()
                .build();
                
            HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
                
            if (response.statusCode() == 200) {
                // Simple JSON parsing without Jackson
                String body = response.body();
                return parsePrice(body);
            }
            
        } catch (Exception e) {
            System.err.println("Error getting price for " + symbol + ": " + e.getMessage());
        }
        
        return -1.0;
    }
    
    private static String getInstrumentKey(String symbol) {
        Map<String, String> keys = new HashMap<>();
        keys.put("NIFTY", "NSE_INDEX|Nifty 50");
        keys.put("SENSEX", "BSE_INDEX|SENSEX");
        keys.put("BANKNIFTY", "NSE_INDEX|Nifty Bank");
        keys.put("TCS", "NSE_EQ|TCS");
        keys.put("RELIANCE", "NSE_EQ|RELIANCE");
        keys.put("HDFCBANK", "NSE_EQ|HDFCBANK");
        
        return keys.get(symbol.toUpperCase());
    }
    
    private static double parsePrice(String json) {
        try {
            // Simple JSON parsing - find "last_price"
            int start = json.indexOf("\"last_price\":");
            if (start == -1) return -1.0;
            
            start = json.indexOf(":", start) + 1;
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            
            String priceStr = json.substring(start, end).trim();
            return Double.parseDouble(priceStr);
            
        } catch (Exception e) {
            return -1.0;
        }
    }
    
    public static boolean isConnected() {
        return ACCESS_TOKEN != null && !ACCESS_TOKEN.isEmpty();
    }
    
    public static void main(String[] args) {
        System.out.println("🚀 Testing Simple Upstox API...");
        
        if (!isConnected()) {
            System.out.println("❌ No access token found in environment");
            System.out.println("💡 Set UPSTOX_ACCESS_TOKEN environment variable");
            return;
        }
        
        String[] testSymbols = {"NIFTY", "TCS", "RELIANCE"};
        
        for (String symbol : testSymbols) {
            double price = getPrice(symbol);
            if (price > 0) {
                System.out.printf("✅ %s: ₹%.2f%n", symbol, price);
            } else {
                System.out.printf("❌ %s: Failed to get price%n", symbol);
            }
        }
    }
}
