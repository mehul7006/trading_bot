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
 * REAL SHOONYA API INTEGRATION WITH OTP AUTHENTICATION
 * Using your authentic credentials for live market data
 */
public class RealShoonyaAPIIntegration {
    
    // === YOUR REAL SHOONYA CREDENTIALS ===
    private static final String VENDOR_CODE = "FN144243_U";
    private static final String IMEI = "abc1234";
    private static final String API_KEY = "c25695ce7271993dceadfc6bdeecd601";
    private static final String CLIENT_CODE = "fn144243";
    private static final String PASSWORD = "rahUl@2412";
    
    // === API CONFIGURATION ===
    private static final String BASE_URL = "https://api.shoonya.com/NorenWClientTP";
    private static final String SOURCE = "API";
    private static final String APP_VERSION = "1.0.0";
    
    // === MAJOR INDICES TOKENS ===
    private static final Map<String, IndexInfo> INDICES = new LinkedHashMap<>();
    
    static {
        INDICES.put("NIFTY", new IndexInfo("26000", "NSE", "Nifty 50"));
        INDICES.put("SENSEX", new IndexInfo("1", "BSE", "BSE Sensex"));
        INDICES.put("BANKNIFTY", new IndexInfo("26009", "NSE", "Bank Nifty"));
        INDICES.put("FINNIFTY", new IndexInfo("26037", "NSE", "Fin Nifty"));
        INDICES.put("MIDCPNIFTY", new IndexInfo("26074", "NSE", "MidCap Nifty"));
        INDICES.put("NIFTYNEXT50", new IndexInfo("26013", "NSE", "Nifty Next 50"));
        INDICES.put("NIFTYIT", new IndexInfo("26017", "NSE", "Nifty IT"));
        INDICES.put("NIFTYPHARMA", new IndexInfo("26023", "NSE", "Nifty Pharma"));
    }
    
    private final HttpClient httpClient;
    private final Map<String, PriceData> latestPrices = new ConcurrentHashMap<>();
    private final Scanner scanner = new Scanner(System.in);
    
    private String sessionToken;
    private boolean isAuthenticated = false;
    private LocalDateTime tokenExpiry;
    
    public RealShoonyaAPIIntegration() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .build();
            
        System.out.println("🚀 === REAL SHOONYA API INTEGRATION ===");
        System.out.println("🔐 Using YOUR authentic credentials");
        System.out.println("🏢 Vendor Code: " + VENDOR_CODE);
        System.out.println("👤 Client Code: " + CLIENT_CODE);
        System.out.println("🔑 API Key: " + API_KEY.substring(0, 8) + "***");
        System.out.println("📱 IMEI: " + IMEI);
        System.out.println("⚡ Ready for OTP authentication!");
        System.out.println();
    }
    
    /**
     * STEP 1: AUTHENTICATE WITH REAL CREDENTIALS + OTP
     */
    public boolean authenticateWithOTP() {
        System.out.println("🔐 === SHOONYA OTP AUTHENTICATION ===");
        System.out.println("⏰ " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
        System.out.println("📱 OTP will be sent to your registered mobile number");
        System.out.println();
        
        try {
            // Step 1: Initial authentication request
            System.out.println("🔑 Step 1: Sending initial authentication request...");
            String passwordHash = generatePasswordHash(PASSWORD);
            
            String requestBody = String.format(
                "jData={\"uid\":\"%s\",\"pwd\":\"%s\",\"factor2\":\"second_factor\",\"vc\":\"%s\",\"appkey\":\"%s\",\"imei\":\"%s\",\"source\":\"%s\",\"apkversion\":\"%s\"}&jKey=%s",
                CLIENT_CODE, passwordHash, VENDOR_CODE, API_KEY, IMEI, SOURCE, APP_VERSION, CLIENT_CODE
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/QuickAuth"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (API Client)")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("   📨 Response Status: HTTP " + response.statusCode());
            System.out.println("   📄 Response: " + response.body());
            System.out.println();
            
            // Check if OTP is required
            if (response.body().contains("\"stat\":\"Not_Ok\"") && 
                (response.body().contains("OTP") || response.body().contains("second_factor"))) {
                
                System.out.println("📱 === OTP AUTHENTICATION REQUIRED ===");
                System.out.println("📞 OTP has been sent to your registered mobile number");
                System.out.print("🔢 Please enter the OTP you received: ");
                
                String otp = scanner.nextLine().trim();
                System.out.println("✅ OTP entered: " + otp);
                System.out.println();
                
                // Step 2: Send OTP for verification
                return authenticateWithOTPVerification(otp);
                
            } else if (response.body().contains("\"susertoken\"")) {
                // Direct authentication successful (no OTP required)
                return parseAuthResponse(response.body());
                
            } else {
                System.out.println("❌ Authentication failed: " + response.body());
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("❌ Authentication exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * STEP 2: VERIFY OTP AND COMPLETE AUTHENTICATION
     */
    private boolean authenticateWithOTPVerification(String otp) {
        try {
            System.out.println("🔐 Step 2: Verifying OTP and completing authentication...");
            
            String passwordHash = generatePasswordHash(PASSWORD);
            
            String requestBody = String.format(
                "jData={\"uid\":\"%s\",\"pwd\":\"%s\",\"factor2\":\"%s\",\"vc\":\"%s\",\"appkey\":\"%s\",\"imei\":\"%s\",\"source\":\"%s\",\"apkversion\":\"%s\"}&jKey=%s",
                CLIENT_CODE, passwordHash, otp, VENDOR_CODE, API_KEY, IMEI, SOURCE, APP_VERSION, CLIENT_CODE
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/QuickAuth"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (API Client)")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("   📨 OTP Verification Response: HTTP " + response.statusCode());
            System.out.println("   📄 Response: " + response.body());
            System.out.println();
            
            if (response.statusCode() == 200 && response.body().contains("\"susertoken\"")) {
                return parseAuthResponse(response.body());
            } else {
                System.out.println("❌ OTP verification failed: " + response.body());
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("❌ OTP verification exception: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Parse authentication response and extract session token
     */
    private boolean parseAuthResponse(String responseBody) {
        try {
            if (responseBody.contains("\"susertoken\"")) {
                int tokenStart = responseBody.indexOf("\"susertoken\":\"") + 14;
                int tokenEnd = responseBody.indexOf("\"", tokenStart);
                this.sessionToken = responseBody.substring(tokenStart, tokenEnd);
                
                this.isAuthenticated = true;
                this.tokenExpiry = LocalDateTime.now().plusHours(8);
                
                System.out.println("✅ === AUTHENTICATION SUCCESSFUL ===");
                System.out.println("🎫 Session Token: " + sessionToken.substring(0, 15) + "***");
                System.out.println("⏰ Token Expires: " + tokenExpiry.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                System.out.println("🚀 Ready to fetch live market data!");
                System.out.println();
                
                return true;
            } else {
                System.out.println("❌ No session token in response");
                return false;
            }
        } catch (Exception e) {
            System.out.println("❌ Failed to parse auth response: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * STEP 3: FETCH LIVE SENSEX AND INDEX PRICES
     */
    public Map<String, PriceData> fetchLiveIndexPrices() {
        System.out.println("📊 === FETCHING LIVE INDEX PRICES ===");
        System.out.println("⏰ " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
        System.out.println("📡 Source: Shoonya API - REAL LIVE DATA");
        System.out.println();
        
        if (!isAuthenticated || isTokenExpired()) {
            System.out.println("⚠️ Need to re-authenticate...");
            if (!authenticateWithOTP()) {
                System.out.println("❌ Cannot fetch prices without authentication");
                return Collections.emptyMap();
            }
        }
        
        Map<String, PriceData> allPrices = new HashMap<>();
        int successCount = 0;
        
        for (Map.Entry<String, IndexInfo> entry : INDICES.entrySet()) {
            String indexName = entry.getKey();
            IndexInfo info = entry.getValue();
            
            try {
                System.out.println("📈 Fetching " + indexName + " (" + info.description + ")...");
                PriceData price = fetchSinglePrice(indexName, info);
                
                if (price != null) {
                    allPrices.put(indexName, price);
                    latestPrices.put(indexName, price);
                    displayPriceData(price);
                    successCount++;
                } else {
                    System.out.println("   ❌ Failed to fetch " + indexName);
                }
                
                System.out.println();
                Thread.sleep(300); // Avoid rate limiting
                
            } catch (Exception e) {
                System.out.println("   ❌ Error: " + e.getMessage());
            }
        }
        
        System.out.println("📊 === PRICE FETCH SUMMARY ===");
        System.out.printf("✅ Success: %d/%d indices\n", successCount, INDICES.size());
        System.out.printf("📈 Success Rate: %.1f%%\n", (successCount * 100.0 / INDICES.size()));
        
        if (successCount > 0) {
            System.out.println("🎉 REAL LIVE DATA SUCCESSFULLY FETCHED!");
            System.out.println("💯 NO MOCK DATA - All prices are authentic market rates!");
        }
        
        return allPrices;
    }
    
    /**
     * Fetch single index price
     */
    private PriceData fetchSinglePrice(String indexName, IndexInfo info) {
        try {
            String requestBody = String.format(
                "jData={\"uid\":\"%s\",\"exch\":\"%s\",\"token\":\"%s\"}&jKey=%s",
                CLIENT_CODE, info.exchange, info.token, sessionToken
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
            System.out.println("   🏆 === BSE SENSEX LIVE PRICE FROM SHOONYA ===");
        } else if ("NIFTY".equals(price.symbol)) {
            System.out.println("   🎯 === NIFTY 50 LIVE PRICE FROM SHOONYA ===");
        }
    }
    
    /**
     * Start continuous monitoring with OTP re-authentication
     */
    public void startContinuousMonitoring(int intervalMinutes) {
        System.out.println("🔄 === STARTING CONTINUOUS PRICE MONITORING ===");
        System.out.printf("⏰ Refresh Interval: %d minutes\n", intervalMinutes);
        System.out.println("🔐 Will re-authenticate automatically when needed");
        System.out.println("🛑 Press Ctrl+C to stop monitoring");
        System.out.println();
        
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("🔄 Refreshing prices...");
                Map<String, PriceData> prices = fetchLiveIndexPrices();
                
                if (!prices.isEmpty()) {
                    System.out.println("✅ Price refresh successful - REAL LIVE DATA");
                } else {
                    System.out.println("⚠️ Price refresh failed - will retry");
                }
                
                System.out.println("=" + String.join("", Collections.nCopies(80, "=")) + "=");
                System.out.println();
            }
        }, 0, intervalMinutes * 60 * 1000L);
        
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            timer.cancel();
            System.out.println("🛑 Monitoring stopped");
        }
    }
    
    /**
     * Generate password hash
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
                    CLIENT_CODE, sessionToken
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
     * MAIN METHOD FOR TESTING
     */
    public static void main(String[] args) {
        System.out.println("🚀 === REAL SHOONYA API INTEGRATION ===");
        System.out.println("🔐 Using authentic credentials with OTP authentication");
        System.out.println("📊 Live SENSEX, NIFTY, BANKNIFTY prices");
        System.out.println("💯 ZERO mock data - only real market rates!");
        System.out.println();
        
        RealShoonyaAPIIntegration integration = new RealShoonyaAPIIntegration();
        
        try {
            // Step 1: Authenticate with OTP
            boolean authenticated = integration.authenticateWithOTP();
            
            if (authenticated) {
                // Step 2: Fetch live prices
                Map<String, PriceData> prices = integration.fetchLiveIndexPrices();
                
                if (!prices.isEmpty()) {
                    System.out.println("\n🎉 === SHOONYA API FULLY OPERATIONAL ===");
                    System.out.println("✅ Authentication with OTP successful");
                    System.out.printf("📊 %d live index prices fetched\n", prices.size());
                    System.out.println("💯 REAL market data confirmed");
                    System.out.println("🚀 Ready for professional trading integration!");
                    
                    // Option: Continuous monitoring
                    Scanner scanner = new Scanner(System.in);
                    System.out.print("\n💡 Start continuous monitoring? (y/n): ");
                    String response = scanner.nextLine();
                    
                    if ("y".equalsIgnoreCase(response.trim())) {
                        System.out.print("⏰ Enter refresh interval in minutes (default 5): ");
                        String intervalStr = scanner.nextLine().trim();
                        int interval = intervalStr.isEmpty() ? 5 : Integer.parseInt(intervalStr);
                        
                        integration.startContinuousMonitoring(interval);
                    }
                    
                } else {
                    System.out.println("\n⚠️ Authentication successful but no prices fetched");
                    System.out.println("💡 Check market hours and try again");
                }
                
            } else {
                System.out.println("\n❌ === AUTHENTICATION FAILED ===");
                System.out.println("🔧 Please verify your credentials and OTP");
                System.out.println("💡 Check if OTP was entered correctly");
            }
            
        } catch (Exception e) {
            System.out.println("\n❌ Unexpected error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            integration.logout();
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