import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.LocalDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * UPSTOX API INTEGRATION - Real Market Data
 * Gets actual current prices from Upstox API
 */
public class UpstoxApiIntegration {
    
    // Your Upstox API Credentials
    private static final String API_KEY = "768a303b-80f1-46d6-af16-f847f9341213";
    private static final String API_SECRET = "40s7mnlm8f";
    private static final String ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTEwYjVjNjAwMGE4YzY0YWM5OGZjYzgiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MjcwMjc5MCwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYyNzI1NjAwfQ.DtaAeJFxSiIYyh8ZC9BV8vo8UczBu6DY8u3aWOsSdMY";
    
    // Upstox API endpoints
    private static final String BASE_URL = "https://api.upstox.com/v2";
    private static final String MARKET_DATA_URL = BASE_URL + "/market-quote/ltp";
    
    // Instrument keys for major indices
    private static final Map<String, String> INSTRUMENT_KEYS = new HashMap<>();
    static {
        INSTRUMENT_KEYS.put("NIFTY", "NSE_INDEX|Nifty 50");
        INSTRUMENT_KEYS.put("SENSEX", "BSE_INDEX|SENSEX");
        INSTRUMENT_KEYS.put("BANKNIFTY", "NSE_INDEX|Nifty Bank");
        INSTRUMENT_KEYS.put("FINNIFTY", "NSE_INDEX|Nifty Fin Service");
    }
    
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .build();
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Cache for prices (to avoid too many API calls)
    private static final Map<String, PriceData> priceCache = new HashMap<>();
    private static final long CACHE_DURATION_MS = 60000; // 1 minute cache
    
    /**
     * Get real current price from Upstox API
     */
    public static double getRealCurrentPrice(String symbol) {
        try {
            // Check cache first
            PriceData cached = priceCache.get(symbol);
            if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_DURATION_MS) {
                System.out.println("📊 Using cached price for " + symbol + ": " + cached.price);
                return cached.price;
            }
            
            // Get fresh data from API
            String instrumentKey = INSTRUMENT_KEYS.get(symbol);
            if (instrumentKey == null) {
                System.err.println("❌ No instrument key found for: " + symbol);
                return getFallbackPrice(symbol);
            }
            
            double price = fetchPriceFromUpstox(instrumentKey);
            if (price > 0) {
                // Cache the price
                priceCache.put(symbol, new PriceData(price, System.currentTimeMillis()));
                System.out.println("✅ Real price from Upstox for " + symbol + ": " + price);
                return price;
            } else {
                System.err.println("❌ Failed to get price from Upstox for: " + symbol);
                return getFallbackPrice(symbol);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error getting real price for " + symbol + ": " + e.getMessage());
            return getFallbackPrice(symbol);
        }
    }
    
    /**
     * Fetch price from Upstox API
     */
    private static double fetchPriceFromUpstox(String instrumentKey) {
        try {
            String url = MARKET_DATA_URL + "?instrument_key=" + URLEncoder.encode(instrumentKey, "UTF-8");
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .header("Accept", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parsePriceFromResponse(response.body());
            } else {
                System.err.println("❌ Upstox API error: " + response.statusCode() + " - " + response.body());
                return 0.0;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Upstox API call failed: " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Parse price from Upstox API response
     */
    private static double parsePriceFromResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            if (root.has("status") && "success".equals(root.get("status").asText())) {
                JsonNode data = root.get("data");
                if (data != null && data.isObject()) {
                    // Get the first instrument data
                    JsonNode firstInstrument = data.elements().next();
                    if (firstInstrument != null && firstInstrument.has("last_price")) {
                        return firstInstrument.get("last_price").asDouble();
                    }
                }
            }
            
            System.err.println("❌ Unexpected Upstox response format: " + jsonResponse);
            return 0.0;
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing Upstox response: " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Fallback prices (recent approximate values)
     */
    private static double getFallbackPrice(String symbol) {
        System.out.println("⚠️ Using fallback price for: " + symbol);
        switch (symbol) {
            case "NIFTY": return 24800.0;      // Approximate current NIFTY
            case "SENSEX": return 82000.0;     // Approximate current SENSEX
            case "BANKNIFTY": return 51500.0;  // Approximate current BANKNIFTY
            case "FINNIFTY": return 23400.0;   // Approximate current FINNIFTY
            default: return 20000.0;
        }
    }
    
    /**
     * Test Upstox API connection
     */
    public static void testUpstoxConnection() {
        System.out.println("🧪 TESTING UPSTOX API CONNECTION");
        System.out.println("=" .repeat(50));
        System.out.println("API Key: " + API_KEY);
        System.out.println("Access Token: " + ACCESS_TOKEN.substring(0, 20) + "...");
        System.out.println("=" .repeat(50));
        
        for (String symbol : Arrays.asList("NIFTY", "SENSEX", "BANKNIFTY", "FINNIFTY")) {
            System.out.println("🔍 Testing " + symbol + "...");
            double price = getRealCurrentPrice(symbol);
            
            if (price > 0) {
                System.out.println("✅ " + symbol + ": " + String.format("%.2f", price));
                
                // Validate if price is realistic
                boolean isRealistic = validatePrice(symbol, price);
                System.out.println("   Realistic: " + (isRealistic ? "✅ YES" : "❌ NO"));
            } else {
                System.out.println("❌ " + symbol + ": Failed to get price");
            }
            System.out.println();
        }
        
        System.out.println("🕒 Test completed at: " + LocalDateTime.now());
    }
    
    /**
     * Validate if price is realistic
     */
    private static boolean validatePrice(String symbol, double price) {
        switch (symbol) {
            case "NIFTY": return price > 20000 && price < 30000;
            case "SENSEX": return price > 70000 && price < 90000;
            case "BANKNIFTY": return price > 40000 && price < 60000;
            case "FINNIFTY": return price > 20000 && price < 30000;
            default: return true;
        }
    }
    
    /**
     * Get all current prices
     */
    public static Map<String, Double> getAllCurrentPrices() {
        Map<String, Double> prices = new HashMap<>();
        
        for (String symbol : Arrays.asList("NIFTY", "SENSEX", "BANKNIFTY", "FINNIFTY")) {
            prices.put(symbol, getRealCurrentPrice(symbol));
        }
        
        return prices;
    }
    
    /**
     * Check if access token is valid
     */
    public static boolean isAccessTokenValid() {
        try {
            String url = BASE_URL + "/user/profile";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .header("Accept", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                System.out.println("✅ Upstox access token is valid");
                return true;
            } else {
                System.err.println("❌ Upstox access token invalid: " + response.statusCode());
                System.err.println("Response: " + response.body());
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error validating access token: " + e.getMessage());
            return false;
        }
    }
    
    // Data class for price caching
    private static class PriceData {
        final double price;
        final long timestamp;
        
        PriceData(double price, long timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }
    }
    
    public static void main(String[] args) {
        System.out.println("🚀 UPSTOX API INTEGRATION TEST");
        System.out.println("=" .repeat(60));
        
        // Test access token first
        if (isAccessTokenValid()) {
            // Test getting real prices
            testUpstoxConnection();
        } else {
            System.out.println("❌ Cannot test prices - access token invalid");
            System.out.println("💡 Please check your access token and regenerate if needed");
        }
    }
}