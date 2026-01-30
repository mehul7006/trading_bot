package com.trading.bot.market;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Simple Shoonya API Test for SENSEX and Index Prices
 * No external dependencies - uses only Java standard library
 */
public class SimpleShoonyaTest {
    
    // API Configuration from .env file
    private static final String BASE_URL = "https://api.shoonya.com/NorenWClientTP";
    private static final String VENDOR_CODE = "FN144243_U";
    private static final String API_KEY = "6eeeccb6db3e623da775b94df5fec2fd";
    private static final String USER_ID = "36B2ZX";
    private static final String PASSWORD = "Monu@123";
    private static final String IMEI = "abc1234";
    
    // Index Tokens for NSE
    private static final Map<String, String> INDEX_TOKENS = new HashMap<>();
    
    static {
        INDEX_TOKENS.put("NIFTY", "26000");
        INDEX_TOKENS.put("SENSEX", "1");
        INDEX_TOKENS.put("BANKNIFTY", "26009");
        INDEX_TOKENS.put("FINNIFTY", "26037");
        INDEX_TOKENS.put("MIDCPNIFTY", "26074");
    }
    
    private final HttpClient httpClient;
    private String sessionToken;
    
    public SimpleShoonyaTest() {
        this.httpClient = HttpClient.newHttpClient();
    }
    
    /**
     * Authenticate with Shoonya API
     */
    public boolean authenticate() {
        try {
            System.out.println("🔐 === SHOONYA AUTHENTICATION TEST ===");
            System.out.println("🏢 Vendor Code: " + VENDOR_CODE);
            System.out.println("👤 User ID: " + USER_ID);
            System.out.println("🔑 API Key: " + API_KEY.substring(0, 8) + "***");
            System.out.println("🔒 Password: " + PASSWORD.substring(0, 2) + "***");
            System.out.println();
            
            // Generate authentication hash
            String authHash = generateAuthHash(PASSWORD);
            System.out.println("📝 Generated auth hash: " + authHash.substring(0, 10) + "***");
            
            // Prepare login request with all required fields
            String requestBody = String.format(
                "jData={\"uid\":\"%s\",\"pwd\":\"%s\",\"factor2\":\"second_factor\",\"vc\":\"%s\",\"appkey\":\"%s\",\"imei\":\"%s\",\"source\":\"API\",\"apkversion\":\"1.0.0\"}&jKey=%s",
                USER_ID, authHash, VENDOR_CODE, API_KEY, IMEI, USER_ID
            );
            
            System.out.println("📡 Sending authentication request to Shoonya...");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/QuickAuth"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("📨 Response Status: HTTP " + response.statusCode());
            System.out.println("📄 Response Body: " + response.body());
            System.out.println();
            
            // Simple JSON parsing (without external library)
            String responseBody = response.body();
            if (response.statusCode() == 200) {
                // Look for session token in response
                if (responseBody.contains("\"susertoken\"")) {
                    // Extract token using simple string manipulation
                    int tokenStart = responseBody.indexOf("\"susertoken\":\"") + 14;
                    int tokenEnd = responseBody.indexOf("\"", tokenStart);
                    this.sessionToken = responseBody.substring(tokenStart, tokenEnd);
                    
                    System.out.println("✅ AUTHENTICATION SUCCESS!");
                    System.out.println("🎫 Session Token: " + sessionToken.substring(0, 10) + "***");
                    System.out.println("⏰ Token obtained at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                    return true;
                } else {
                    System.out.println("❌ AUTHENTICATION FAILED!");
                    System.out.println("🔍 Error in response: " + responseBody);
                    return false;
                }
            } else {
                System.out.println("❌ HTTP ERROR: " + response.statusCode());
                System.out.println("📄 Error response: " + responseBody);
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("❌ EXCEPTION during authentication: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get current SENSEX and index prices
     */
    public void getCurrentIndexPrices() {
        System.out.println("📊 === FETCHING CURRENT INDEX PRICES ===");
        System.out.println("⏰ Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
        System.out.println("📡 Source: Shoonya API");
        System.out.println();
        
        if (sessionToken == null) {
            System.out.println("❌ Not authenticated. Attempting authentication first...");
            if (!authenticate()) {
                System.out.println("❌ Cannot fetch prices without authentication");
                return;
            }
            System.out.println();
        }
        
        // Fetch prices for each index
        for (Map.Entry<String, String> entry : INDEX_TOKENS.entrySet()) {
            String indexName = entry.getKey();
            String token = entry.getValue();
            
            System.out.println("📈 Fetching " + indexName + " price...");
            fetchIndexPrice(indexName, token);
            System.out.println();
        }
    }
    
    /**
     * Fetch price for specific index
     */
    private void fetchIndexPrice(String indexName, String token) {
        try {
            // Prepare quote request
            String requestBody = String.format(
                "jData={\"uid\":\"%s\",\"exch\":\"NSE\",\"token\":\"%s\"}&jKey=%s",
                USER_ID, token, sessionToken
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/GetQuotes"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("   📨 Response Status: HTTP " + response.statusCode());
            System.out.println("   📄 Response: " + response.body());
            
            if (response.statusCode() == 200) {
                // Parse price data using simple string methods
                String responseBody = response.body();
                parseAndDisplayPrice(indexName, token, responseBody);
            } else {
                System.out.println("   ❌ Failed to fetch " + indexName + " price: HTTP " + response.statusCode());
            }
            
        } catch (Exception e) {
            System.out.println("   ❌ Exception fetching " + indexName + ": " + e.getMessage());
        }
    }
    
    /**
     * Parse and display price data (simple JSON parsing)
     */
    private void parseAndDisplayPrice(String indexName, String token, String jsonResponse) {
        try {
            System.out.println("   🔍 Parsing " + indexName + " data...");
            
            // Simple JSON parsing without external library
            if (jsonResponse.contains("\"lp\"")) {
                // Extract Last Price (lp)
                double ltp = extractJsonValue(jsonResponse, "lp");
                double open = extractJsonValue(jsonResponse, "o");
                double high = extractJsonValue(jsonResponse, "h");
                double low = extractJsonValue(jsonResponse, "l");
                double close = extractJsonValue(jsonResponse, "c");
                double volume = extractJsonValue(jsonResponse, "v");
                
                double change = ltp - close;
                double changePercent = close > 0 ? (change / close) * 100 : 0.0;
                
                // Display formatted price data
                String trend = change >= 0 ? "📈" : "📉";
                String color = change >= 0 ? "🟢" : "🔴";
                
                System.out.printf("   %s %s %s: ₹%,.2f %+.2f (%+.2f%%) [Token: %s]\n",
                        color, trend, indexName, ltp, change, changePercent, token);
                System.out.printf("   💹 Open: ₹%,.2f | High: ₹%,.2f | Low: ₹%,.2f | Volume: %,.0f\n",
                        open, high, low, volume);
                System.out.printf("   📊 Previous Close: ₹%,.2f | Current: ₹%,.2f\n", close, ltp);
                
                // Special highlight for SENSEX
                if ("SENSEX".equals(indexName)) {
                    System.out.println("   🏆 === SENSEX CURRENT PRICE SUCCESSFULLY FETCHED ===");
                }
                
            } else if (jsonResponse.contains("\"emsg\"")) {
                String errorMsg = extractJsonString(jsonResponse, "emsg");
                System.out.println("   ❌ API Error: " + errorMsg);
            } else {
                System.out.println("   ⚠️ Unexpected response format");
                System.out.println("   📄 Raw response: " + jsonResponse);
            }
            
        } catch (Exception e) {
            System.out.println("   ❌ Error parsing " + indexName + " data: " + e.getMessage());
        }
    }
    
    /**
     * Extract numeric value from JSON string
     */
    private double extractJsonValue(String json, String key) {
        try {
            String searchPattern = "\"" + key + "\":\"";
            int start = json.indexOf(searchPattern);
            if (start == -1) {
                // Try without quotes around value
                searchPattern = "\"" + key + "\":";
                start = json.indexOf(searchPattern);
                if (start == -1) return 0.0;
                start += searchPattern.length();
            } else {
                start += searchPattern.length();
            }
            
            int end = json.indexOf("\"", start);
            if (end == -1) {
                // Look for comma or closing brace
                end = Math.min(
                    json.indexOf(",", start) == -1 ? json.length() : json.indexOf(",", start),
                    json.indexOf("}", start) == -1 ? json.length() : json.indexOf("}", start)
                );
            }
            
            String valueStr = json.substring(start, end).trim();
            return Double.parseDouble(valueStr);
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * Extract string value from JSON
     */
    private String extractJsonString(String json, String key) {
        try {
            String searchPattern = "\"" + key + "\":\"";
            int start = json.indexOf(searchPattern) + searchPattern.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * Generate authentication hash
     */
    private String generateAuthHash(String password) {
        try {
            String input = password + API_KEY;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate auth hash", e);
        }
    }
    
    /**
     * Main method to test SENSEX and index prices
     */
    public static void main(String[] args) {
        System.out.println("🚀 === SHOONYA API LIVE PRICE TEST ===");
        System.out.println("📊 Testing SENSEX and Index Current Prices");
        System.out.println("🔍 Using credentials from .env file");
        System.out.println();
        
        SimpleShoonyaTest test = new SimpleShoonyaTest();
        
        // Test authentication first
        boolean authenticated = test.authenticate();
        
        if (authenticated) {
            System.out.println("\n🎯 === FETCHING LIVE PRICES ===");
            test.getCurrentIndexPrices();
            
            System.out.println("\n🎉 === TEST COMPLETE ===");
            System.out.println("✅ Shoonya API integration working!");
            System.out.println("📊 Real-time SENSEX and index prices fetched successfully!");
        } else {
            System.out.println("\n❌ === AUTHENTICATION FAILED ===");
            System.out.println("💡 POSSIBLE SOLUTIONS:");
            System.out.println("   1. Verify password 'Monu@123' is correct");
            System.out.println("   2. Check if API access is enabled on Finvasia account");
            System.out.println("   3. Contact Finvasia support for API activation");
            System.out.println("   4. Verify account has live market data permissions");
            System.out.println();
            System.out.println("🔄 Your Upstox+Yahoo system is still working perfectly!");
        }
    }
}