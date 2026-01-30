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
import java.util.concurrent.ConcurrentHashMap;

/**
 * BEST SHOONYA API PRICE FETCHER
 * Professional implementation with correct authentication
 * Real-time SENSEX, NIFTY, and all index prices
 */
public class BestShoonyaAPIFetcher {
    
    // === SHOONYA API CREDENTIALS ===
    // Update these with your correct Finvasia credentials
    private static final String VENDOR_CODE = "FN144243_U";           // Your vendor code
    private static final String USER_ID = "36B2ZX";                   // Your user ID
    private static final String PASSWORD = "Monu@123";                // Your trading password
    private static final String API_KEY = "6eeeccb6db3e623da775b94df5fec2fd"; // Your API key
    private static final String IMEI = "abc1234567890";               // Your IMEI
    
    // === API CONFIGURATION ===
    private static final String BASE_URL = "https://api.shoonya.com/NorenWClientTP";
    private static final String APP_VERSION = "1.0.0";
    private static final String SOURCE = "API";
    
    // === INSTRUMENT TOKENS FOR MAJOR INDICES ===
    private static final Map<String, IndexInfo> INDICES = new LinkedHashMap<>();
    
    static {
        // NSE Indices with correct tokens
        INDICES.put("NIFTY", new IndexInfo("26000", "NSE", "Nifty 50"));
        INDICES.put("SENSEX", new IndexInfo("1", "BSE", "BSE Sensex"));
        INDICES.put("BANKNIFTY", new IndexInfo("26009", "NSE", "Bank Nifty"));
        INDICES.put("FINNIFTY", new IndexInfo("26037", "NSE", "Fin Nifty"));
        INDICES.put("MIDCPNIFTY", new IndexInfo("26074", "NSE", "MidCap Nifty"));
        
        // Additional popular indices
        INDICES.put("NIFTYNEXT50", new IndexInfo("26013", "NSE", "Nifty Next 50"));
        INDICES.put("NIFTYIT", new IndexInfo("26017", "NSE", "Nifty IT"));
        INDICES.put("NIFTYPHARMA", new IndexInfo("26023", "NSE", "Nifty Pharma"));
        INDICES.put("NIFTYAUTO", new IndexInfo("26018", "NSE", "Nifty Auto"));
        INDICES.put("NIFTYMETAL", new IndexInfo("26021", "NSE", "Nifty Metal"));
    }
    
    private final HttpClient httpClient;
    private final Map<String, PriceData> latestPrices = new ConcurrentHashMap<>();
    
    private String sessionToken;
    private boolean isAuthenticated = false;
    private LocalDateTime tokenExpiry;
    private LocalDateTime lastSuccessfulAuth;
    
    public BestShoonyaAPIFetcher() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .build();
            
        System.out.println("🚀 === BEST SHOONYA API PRICE FETCHER ===");
        System.out.println("🏢 Vendor: " + VENDOR_CODE);
        System.out.println("👤 User ID: " + USER_ID);
        System.out.println("🔑 API Key: " + API_KEY.substring(0, 8) + "***");
        System.out.println("📱 IMEI: " + IMEI);
        System.out.println("⚡ Ready for professional price fetching!");
        System.out.println();
    }
    
    /**
     * STEP 1: AUTHENTICATE WITH SHOONYA API
     * Multiple authentication methods for maximum compatibility
     */
    public boolean authenticateWithShoonya() {
        System.out.println("🔐 === SHOONYA AUTHENTICATION PROCESS ===");
        System.out.println("⏰ " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
        
        // Try multiple authentication methods
        boolean success = false;
        
        // Method 1: Standard QuickAuth
        System.out.println("🔑 Trying Method 1: Standard QuickAuth...");
        success = tryStandardAuth();
        
        if (!success) {
            // Method 2: Enhanced Auth with all fields
            System.out.println("🔑 Trying Method 2: Enhanced Authentication...");
            success = tryEnhancedAuth();
        }
        
        if (!success) {
            // Method 3: Alternative endpoint
            System.out.println("🔑 Trying Method 3: Alternative Login...");
            success = tryAlternativeAuth();
        }
        
        if (success) {
            this.isAuthenticated = true;
            this.lastSuccessfulAuth = LocalDateTime.now();
            this.tokenExpiry = LocalDateTime.now().plusHours(8); // Typical session duration
            
            System.out.println("✅ === AUTHENTICATION SUCCESSFUL ===");
            System.out.println("🎫 Session Token: " + sessionToken.substring(0, 15) + "***");
            System.out.println("⏰ Token Expires: " + tokenExpiry.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            System.out.println("🚀 Ready to fetch live prices!");
        } else {
            System.out.println("❌ === AUTHENTICATION FAILED ===");
            System.out.println("💡 All authentication methods attempted");
            diagnosePossibleIssues();
        }
        
        System.out.println();
        return success;
    }
    
    /**
     * Method 1: Standard QuickAuth
     */
    private boolean tryStandardAuth() {
        try {
            String passwordHash = generatePasswordHash(PASSWORD);
            
            String requestBody = String.format(
                "jData={\"uid\":\"%s\",\"pwd\":\"%s\",\"factor2\":\"second_factor\",\"vc\":\"%s\",\"appkey\":\"%s\",\"imei\":\"%s\",\"source\":\"%s\"}&jKey=%s",
                USER_ID, passwordHash, VENDOR_CODE, API_KEY, IMEI, SOURCE, USER_ID
            );
            
            return makeAuthRequest("/QuickAuth", requestBody, "Standard Auth");
            
        } catch (Exception e) {
            System.out.println("   ❌ Standard auth exception: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Method 2: Enhanced Auth with all required fields
     */
    private boolean tryEnhancedAuth() {
        try {
            String passwordHash = generatePasswordHash(PASSWORD);
            
            String requestBody = String.format(
                "jData={\"uid\":\"%s\",\"pwd\":\"%s\",\"factor2\":\"second_factor\",\"vc\":\"%s\",\"appkey\":\"%s\",\"imei\":\"%s\",\"source\":\"%s\",\"apkversion\":\"%s\"}&jKey=%s",
                USER_ID, passwordHash, VENDOR_CODE, API_KEY, IMEI, SOURCE, APP_VERSION, USER_ID
            );
            
            return makeAuthRequest("/QuickAuth", requestBody, "Enhanced Auth");
            
        } catch (Exception e) {
            System.out.println("   ❌ Enhanced auth exception: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Method 3: Alternative authentication
     */
    private boolean tryAlternativeAuth() {
        try {
            String passwordHash = generatePasswordHash(PASSWORD);
            
            // Try with different parameter format
            String requestBody = String.format(
                "jData={\"uid\":\"%s\",\"pwd\":\"%s\",\"vc\":\"%s\",\"appkey\":\"%s\",\"imei\":\"%s\",\"source\":\"%s\",\"apkversion\":\"%s\",\"addldivinf\":\"\",\"ipaddr\":\"\",\"macaddr\":\"\",\"factor2\":\"second_factor\"}&jKey=%s",
                USER_ID, passwordHash, VENDOR_CODE, API_KEY, IMEI, SOURCE, APP_VERSION, USER_ID
            );
            
            return makeAuthRequest("/QuickAuth", requestBody, "Alternative Auth");
            
        } catch (Exception e) {
            System.out.println("   ❌ Alternative auth exception: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Make authentication request
     */
    private boolean makeAuthRequest(String endpoint, String requestBody, String method) {
        try {
            System.out.println("   📡 Sending " + method + " request...");
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (API Client)")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("   📨 Response: HTTP " + response.statusCode());
            System.out.println("   📄 Body: " + response.body());
            
            if (response.statusCode() == 200) {
                return parseAuthResponse(response.body());
            } else {
                System.out.println("   ❌ HTTP Error: " + response.statusCode());
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("   ❌ Request failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Parse authentication response
     */
    private boolean parseAuthResponse(String responseBody) {
        try {
            // Simple JSON parsing for session token
            if (responseBody.contains("\"susertoken\"")) {
                int tokenStart = responseBody.indexOf("\"susertoken\":\"") + 14;
                int tokenEnd = responseBody.indexOf("\"", tokenStart);
                this.sessionToken = responseBody.substring(tokenStart, tokenEnd);
                return true;
            } else if (responseBody.contains("\"stat\":\"Ok\"") && responseBody.contains("\"token\":")) {
                int tokenStart = responseBody.indexOf("\"token\":\"") + 9;
                int tokenEnd = responseBody.indexOf("\"", tokenStart);
                this.sessionToken = responseBody.substring(tokenStart, tokenEnd);
                return true;
            } else {
                // Extract error message if available
                if (responseBody.contains("\"emsg\":")) {
                    int msgStart = responseBody.indexOf("\"emsg\":\"") + 8;
                    int msgEnd = responseBody.indexOf("\"", msgStart);
                    String errorMsg = responseBody.substring(msgStart, msgEnd);
                    System.out.println("   📋 Server Error: " + errorMsg);
                }
                return false;
            }
        } catch (Exception e) {
            System.out.println("   ❌ Failed to parse auth response: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * STEP 2: FETCH ALL INDEX PRICES
     */
    public Map<String, PriceData> fetchAllIndexPrices() {
        System.out.println("📊 === FETCHING ALL INDEX PRICES ===");
        System.out.println("⏰ " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
        System.out.println("📡 Source: Shoonya API Live Data");
        System.out.println();
        
        if (!isAuthenticated || isTokenExpired()) {
            System.out.println("⚠️ Not authenticated or token expired. Attempting authentication...");
            if (!authenticateWithShoonya()) {
                System.out.println("❌ Cannot fetch prices without authentication");
                return Collections.emptyMap();
            }
        }
        
        Map<String, PriceData> allPrices = new HashMap<>();
        int successCount = 0;
        int totalCount = INDICES.size();
        
        System.out.println("🎯 Fetching prices for " + totalCount + " indices...");
        System.out.println();
        
        for (Map.Entry<String, IndexInfo> entry : INDICES.entrySet()) {
            String indexName = entry.getKey();
            IndexInfo info = entry.getValue();
            
            try {
                System.out.println("📈 Fetching " + indexName + " (" + info.description + ")...");
                PriceData price = fetchSingleIndexPrice(indexName, info);
                
                if (price != null) {
                    allPrices.put(indexName, price);
                    latestPrices.put(indexName, price);
                    displayPriceData(price);
                    successCount++;
                } else {
                    System.out.println("   ❌ Failed to fetch " + indexName);
                }
                
                System.out.println();
                
                // Small delay to avoid rate limiting
                Thread.sleep(200);
                
            } catch (Exception e) {
                System.out.println("   ❌ Error fetching " + indexName + ": " + e.getMessage());
            }
        }
        
        System.out.println("📊 === PRICE FETCH SUMMARY ===");
        System.out.printf("✅ Success: %d/%d indices fetched\n", successCount, totalCount);
        System.out.printf("📈 Success Rate: %.1f%%\n", (successCount * 100.0 / totalCount));
        
        if (successCount > 0) {
            System.out.println("🎉 Live price data successfully obtained from Shoonya API!");
        } else {
            System.out.println("❌ No price data could be fetched. Check API credentials.");
        }
        
        return allPrices;
    }
    
    /**
     * Fetch single index price
     */
    private PriceData fetchSingleIndexPrice(String indexName, IndexInfo info) {
        try {
            String requestBody = String.format(
                "jData={\"uid\":\"%s\",\"exch\":\"%s\",\"token\":\"%s\"}&jKey=%s",
                USER_ID, info.exchange, info.token, sessionToken
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/GetQuotes"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parsePriceResponse(indexName, info, response.body());
            } else {
                System.out.println("   ❌ HTTP " + response.statusCode() + ": " + response.body());
                return null;
            }
            
        } catch (Exception e) {
            System.out.println("   ❌ Request failed: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse price response
     */
    private PriceData parsePriceResponse(String indexName, IndexInfo info, String responseBody) {
        try {
            // Check if response contains price data
            if (responseBody.contains("\"lp\"")) {
                double ltp = extractJsonDouble(responseBody, "lp");
                double open = extractJsonDouble(responseBody, "o");
                double high = extractJsonDouble(responseBody, "h");
                double low = extractJsonDouble(responseBody, "l");
                double close = extractJsonDouble(responseBody, "c");
                double volume = extractJsonDouble(responseBody, "v");
                
                double change = ltp - close;
                double changePercent = close > 0 ? (change / close) * 100 : 0.0;
                
                return new PriceData(
                    indexName, info.description, info.exchange,
                    ltp, open, high, low, close, volume,
                    change, changePercent, LocalDateTime.now()
                );
                
            } else if (responseBody.contains("\"emsg\"")) {
                String errorMsg = extractJsonString(responseBody, "emsg");
                System.out.println("   📋 API Error: " + errorMsg);
                return null;
            } else {
                System.out.println("   ⚠️ Unexpected response format");
                return null;
            }
            
        } catch (Exception e) {
            System.out.println("   ❌ Parse error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Display formatted price data
     */
    private void displayPriceData(PriceData price) {
        String trend = price.change >= 0 ? "📈" : "📉";
        String color = price.change >= 0 ? "🟢" : "🔴";
        
        System.out.printf("   %s %s %s: ₹%,.2f %+.2f (%+.2f%%)\n",
                color, trend, price.symbol, price.ltp, price.change, price.changePercent);
        
        System.out.printf("   💹 Open: ₹%,.2f | High: ₹%,.2f | Low: ₹%,.2f\n",
                price.open, price.high, price.low);
        
        System.out.printf("   📊 Previous: ₹%,.2f | Volume: %,.0f | Exchange: %s\n",
                price.previousClose, price.volume, price.exchange);
        
        // Special highlighting for major indices
        if ("SENSEX".equals(price.symbol)) {
            System.out.println("   🏆 === BSE SENSEX LIVE PRICE FETCHED ===");
        } else if ("NIFTY".equals(price.symbol)) {
            System.out.println("   🎯 === NIFTY 50 LIVE PRICE FETCHED ===");
        }
    }
    
    /**
     * STEP 3: CONTINUOUS PRICE MONITORING
     */
    public void startContinuousMonitoring(int intervalSeconds) {
        System.out.println("🔄 === STARTING CONTINUOUS PRICE MONITORING ===");
        System.out.printf("⏰ Refresh Interval: %d seconds\n", intervalSeconds);
        System.out.println("🛑 Press Ctrl+C to stop monitoring");
        System.out.println();
        
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("🔄 Refreshing prices...");
                Map<String, PriceData> prices = fetchAllIndexPrices();
                
                if (!prices.isEmpty()) {
                    System.out.println("✅ Price refresh successful");
                } else {
                    System.out.println("⚠️ Price refresh failed - will retry");
                }
                
                System.out.println("=" + String.join("", Collections.nCopies(60, "=")) + "=");
                System.out.println();
            }
        }, 0, intervalSeconds * 1000L);
        
        // Keep the program running
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            timer.cancel();
            System.out.println("🛑 Monitoring stopped");
        }
    }
    
    /**
     * Utility Methods
     */
    
    private String generatePasswordHash(String password) {
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
            throw new RuntimeException("Failed to generate password hash", e);
        }
    }
    
    private boolean isTokenExpired() {
        return tokenExpiry == null || LocalDateTime.now().isAfter(tokenExpiry);
    }
    
    private double extractJsonDouble(String json, String key) {
        try {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern);
            if (start == -1) {
                pattern = "\"" + key + "\":";
                start = json.indexOf(pattern);
                if (start == -1) return 0.0;
                start += pattern.length();
            } else {
                start += pattern.length();
            }
            
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            if (end == -1) end = json.indexOf("\"", start);
            
            String valueStr = json.substring(start, end).replace("\"", "").trim();
            return Double.parseDouble(valueStr);
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    private String extractJsonString(String json, String key) {
        try {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern) + pattern.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    private void diagnosePossibleIssues() {
        System.out.println("🔍 === DIAGNOSIS OF AUTHENTICATION ISSUES ===");
        System.out.println("💡 POSSIBLE PROBLEMS:");
        System.out.println("   1. ❌ API Key incorrect: " + API_KEY.substring(0, 8) + "***");
        System.out.println("   2. ❌ Password wrong: " + PASSWORD.substring(0, 2) + "***");
        System.out.println("   3. ❌ User ID invalid: " + USER_ID);
        System.out.println("   4. ❌ Vendor Code wrong: " + VENDOR_CODE);
        System.out.println("   5. ❌ API access not enabled on account");
        System.out.println("   6. ❌ Account suspended or inactive");
        System.out.println("   7. ❌ Network/firewall blocking requests");
        System.out.println();
        System.out.println("📞 CONTACT FINVASIA SUPPORT:");
        System.out.println("   Phone: +91-80-40402020");
        System.out.println("   Email: support@finvasia.com");
        System.out.println("   Account: " + USER_ID);
        System.out.println("   Issue: API authentication failing");
    }
    
    /**
     * Get latest cached prices
     */
    public Map<String, PriceData> getLatestPrices() {
        return new HashMap<>(latestPrices);
    }
    
    /**
     * Logout and cleanup
     */
    public void logout() {
        if (isAuthenticated && sessionToken != null) {
            try {
                String requestBody = String.format(
                    "jData={\"uid\":\"%s\"}&jKey=%s",
                    USER_ID, sessionToken
                );
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/Logout"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
                
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("🔓 Logged out from Shoonya API");
                
            } catch (Exception e) {
                System.out.println("⚠️ Logout error: " + e.getMessage());
            }
        }
        
        isAuthenticated = false;
        sessionToken = null;
        tokenExpiry = null;
    }
    
    /**
     * MAIN METHOD - COMPREHENSIVE TESTING
     */
    public static void main(String[] args) {
        System.out.println("🚀 === BEST SHOONYA API PRICE FETCHER ===");
        System.out.println("🎯 Professional Real-Time Index Price System");
        System.out.println("📊 SENSEX, NIFTY, BANKNIFTY and all major indices");
        System.out.println();
        
        BestShoonyaAPIFetcher fetcher = new BestShoonyaAPIFetcher();
        
        try {
            // Step 1: Authenticate
            boolean authenticated = fetcher.authenticateWithShoonya();
            
            if (authenticated) {
                // Step 2: Fetch all prices
                Map<String, PriceData> prices = fetcher.fetchAllIndexPrices();
                
                if (!prices.isEmpty()) {
                    System.out.println("\n🎉 === SUCCESS! SHOONYA API FULLY OPERATIONAL ===");
                    System.out.printf("✅ Successfully fetched %d index prices\n", prices.size());
                    System.out.println("📊 Real-time SENSEX, NIFTY data available");
                    System.out.println("🚀 Ready for professional trading integration!");
                    
                    // Option: Start continuous monitoring
                    Scanner scanner = new Scanner(System.in);
                    System.out.print("\n💡 Start continuous monitoring? (y/n): ");
                    String response = scanner.nextLine();
                    
                    if ("y".equalsIgnoreCase(response.trim())) {
                        System.out.print("⏰ Enter refresh interval in seconds (default 30): ");
                        String intervalStr = scanner.nextLine().trim();
                        int interval = intervalStr.isEmpty() ? 30 : Integer.parseInt(intervalStr);
                        
                        fetcher.startContinuousMonitoring(interval);
                    }
                    
                } else {
                    System.out.println("\n⚠️ Authentication successful but no prices fetched");
                    System.out.println("💡 Check market hours and instrument tokens");
                }
                
            } else {
                System.out.println("\n❌ === AUTHENTICATION FAILED ===");
                System.out.println("🔧 Please check credentials and contact Finvasia support");
                System.out.println("📞 Support: +91-80-40402020");
                System.out.println("💡 Your existing Upstox+Yahoo system still works!");
            }
            
        } catch (Exception e) {
            System.out.println("\n❌ Unexpected error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            fetcher.logout();
        }
    }
    
    /**
     * Data Classes
     */
    
    public static class IndexInfo {
        public final String token;
        public final String exchange;
        public final String description;
        
        public IndexInfo(String token, String exchange, String description) {
            this.token = token;
            this.exchange = exchange;
            this.description = description;
        }
    }
    
    public static class PriceData {
        public final String symbol;
        public final String description;
        public final String exchange;
        public final double ltp;
        public final double open;
        public final double high;
        public final double low;
        public final double previousClose;
        public final double volume;
        public final double change;
        public final double changePercent;
        public final LocalDateTime timestamp;
        
        public PriceData(String symbol, String description, String exchange,
                        double ltp, double open, double high, double low, double previousClose,
                        double volume, double change, double changePercent, LocalDateTime timestamp) {
            this.symbol = symbol;
            this.description = description;
            this.exchange = exchange;
            this.ltp = ltp;
            this.open = open;
            this.high = high;
            this.low = low;
            this.previousClose = previousClose;
            this.volume = volume;
            this.change = change;
            this.changePercent = changePercent;
            this.timestamp = timestamp;
        }
        
        @Override
        public String toString() {
            return String.format("%s: ₹%.2f (%+.2f%%)", symbol, ltp, changePercent);
        }
    }
}