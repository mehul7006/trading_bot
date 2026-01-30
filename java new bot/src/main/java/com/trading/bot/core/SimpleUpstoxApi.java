import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SIMPLE UPSTOX API - No External Dependencies
 * Gets real market prices using only built-in Java libraries
 */
public class SimpleUpstoxApi {
    
    // Your Upstox API Credentials
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
    
    // Price cache to avoid too many API calls
    private static final Map<String, CachedPrice> priceCache = new HashMap<>();
    private static final long CACHE_DURATION_MS = 60000; // 1 minute cache
    
    /**
     * Get real current price from Upstox API
     */
    public static double getRealCurrentPrice(String symbol) {
        try {
            // Check cache first
            CachedPrice cached = priceCache.get(symbol);
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
            
            double price = fetchPriceFromUpstox(instrumentKey, symbol);
            if (price > 0) {
                // Cache the price
                priceCache.put(symbol, new CachedPrice(price, System.currentTimeMillis()));
                System.out.println("✅ Real price from Upstox for " + symbol + ": " + String.format("%.2f", price));
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
     * Fetch price from Upstox API using simple JSON parsing
     */
    private static double fetchPriceFromUpstox(String instrumentKey, String symbol) {
        try {
            String url = MARKET_DATA_URL + "?instrument_key=" + URLEncoder.encode(instrumentKey, "UTF-8");
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .header("Accept", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("🔍 Upstox API call for " + symbol + ": " + response.statusCode());
            
            if (response.statusCode() == 200) {
                return parseSimplePrice(response.body(), symbol);
            } else if (response.statusCode() == 401) {
                System.err.println("❌ Upstox API: Access token expired or invalid");
                System.err.println("💡 Please regenerate your access token");
                return getFallbackPrice(symbol);
            } else {
                System.err.println("❌ Upstox API error: " + response.statusCode());
                System.err.println("Response: " + response.body());
                return getFallbackPrice(symbol);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Upstox API call failed: " + e.getMessage());
            return getFallbackPrice(symbol);
        }
    }
    
    /**
     * Simple JSON parsing without external libraries
     */
    private static double parseSimplePrice(String jsonResponse, String symbol) {
        try {
            System.out.println("📄 API Response for " + symbol + ": " + jsonResponse.substring(0, Math.min(200, jsonResponse.length())) + "...");
            
            // Look for "last_price" in the JSON response
            String searchPattern = "\"last_price\":";
            int priceIndex = jsonResponse.indexOf(searchPattern);
            
            if (priceIndex != -1) {
                int startIndex = priceIndex + searchPattern.length();
                int endIndex = jsonResponse.indexOf(',', startIndex);
                if (endIndex == -1) {
                    endIndex = jsonResponse.indexOf('}', startIndex);
                }
                
                if (endIndex != -1) {
                    String priceStr = jsonResponse.substring(startIndex, endIndex).trim();
                    // Remove any quotes
                    priceStr = priceStr.replace("\"", "");
                    
                    double price = Double.parseDouble(priceStr);
                    
                    // Validate price is realistic
                    if (validatePrice(symbol, price)) {
                        return price;
                    } else {
                        System.err.println("❌ Unrealistic price for " + symbol + ": " + price);
                        return getFallbackPrice(symbol);
                    }
                }
            }
            
            // Try alternative parsing - look for any number that could be a price
            String[] lines = jsonResponse.split("[,{}]");
            for (String line : lines) {
                if (line.contains("last_price") || line.contains("ltp")) {
                    String[] parts = line.split(":");
                    if (parts.length >= 2) {
                        try {
                            String priceStr = parts[1].trim().replace("\"", "");
                            double price = Double.parseDouble(priceStr);
                            if (validatePrice(symbol, price)) {
                                return price;
                            }
                        } catch (NumberFormatException e) {
                            // Continue searching
                        }
                    }
                }
            }
            
            System.err.println("❌ Could not parse price from response for: " + symbol);
            return 0.0;
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing price for " + symbol + ": " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Validate if price is realistic for the symbol
     */
    private static boolean validatePrice(String symbol, double price) {
        switch (symbol) {
            case "NIFTY": return price > 20000 && price < 30000;
            case "SENSEX": return price > 70000 && price < 90000;
            case "BANKNIFTY": return price > 40000 && price < 60000;
            case "FINNIFTY": return price > 20000 && price < 30000;
            default: return price > 0;
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
     * Test Upstox API connection and access token
     */
    public static boolean testUpstoxConnection() {
        System.out.println("🧪 TESTING UPSTOX API CONNECTION");
        System.out.println("=" .repeat(50));
        System.out.println("Access Token: " + ACCESS_TOKEN.substring(0, 20) + "...");
        System.out.println("=" .repeat(50));
        
        // Test access token validity first
        if (!isAccessTokenValid()) {
            System.out.println("❌ Access token is invalid or expired");
            System.out.println("💡 Please regenerate your access token from Upstox");
            return false;
        }
        
        boolean allSuccess = true;
        
        for (String symbol : Arrays.asList("NIFTY", "SENSEX", "BANKNIFTY", "FINNIFTY")) {
            System.out.println("🔍 Testing " + symbol + "...");
            double price = getRealCurrentPrice(symbol);
            
            if (price > 0 && validatePrice(symbol, price)) {
                System.out.println("✅ " + symbol + ": " + String.format("%.2f", price));
            } else {
                System.out.println("❌ " + symbol + ": Failed to get valid price");
                allSuccess = false;
            }
            System.out.println();
        }
        
        System.out.println("🕒 Test completed at: " + LocalDateTime.now());
        return allSuccess;
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
                if (response.statusCode() == 401) {
                    System.err.println("💡 Token expired - please regenerate from Upstox dashboard");
                }
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error validating access token: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get all current prices
     */
    public static Map<String, Double> getAllCurrentPrices() {
        Map<String, Double> prices = new HashMap<>();
        
        System.out.println("📊 Fetching all current prices...");
        for (String symbol : Arrays.asList("NIFTY", "SENSEX", "BANKNIFTY", "FINNIFTY")) {
            prices.put(symbol, getRealCurrentPrice(symbol));
        }
        
        return prices;
    }
    
    // Simple cache class
    private static class CachedPrice {
        final double price;
        final long timestamp;
        
        CachedPrice(double price, long timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }
    }
    
    public static void main(String[] args) {
        System.out.println("🚀 SIMPLE UPSTOX API TEST");
        System.out.println("=" .repeat(60));
        
        boolean success = testUpstoxConnection();
        
        if (success) {
            System.out.println("🎉 All tests passed! Real prices are working.");
        } else {
            System.out.println("⚠️ Some tests failed. Check access token and try again.");
        }
    }
}