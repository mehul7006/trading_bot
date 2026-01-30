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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Professional Shoonya API Implementation
 * Real authentication and market data fetching for Finvasia/Shoonya
 */
public class ProfessionalShoonyaAPI {
    
    private static final Logger logger = LoggerFactory.getLogger(ProfessionalShoonyaAPI.class);
    
    // API Configuration
    private static final String BASE_URL = "https://api.shoonya.com/NorenWClientTP";
    private static final String VENDOR_CODE = "FN144243_U";
    private static final String API_KEY = "6eeeccb6db3e623da775b94df5fec2fd";
    private static final String USER_ID = "36B2ZX";
    private static final String PASSWORD = "Monu@123"; // From .env file
    private static final String IMEI = "abc1234";
    
    // NSE Instrument Tokens
    private static final Map<String, String> INSTRUMENT_TOKENS = new HashMap<>();
    
    static {
        INSTRUMENT_TOKENS.put("NIFTY", "26000");
        INSTRUMENT_TOKENS.put("SENSEX", "1");
        INSTRUMENT_TOKENS.put("BANKNIFTY", "26009");
        INSTRUMENT_TOKENS.put("FINNIFTY", "26037");
        INSTRUMENT_TOKENS.put("MIDCPNIFTY", "26074");
    }
    
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Map<String, ShoonyaQuote> latestQuotes = new ConcurrentHashMap<>();
    
    private String sessionToken;
    private boolean isAuthenticated = false;
    private LocalDateTime tokenExpiry;
    
    public ProfessionalShoonyaAPI() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
        this.mapper = new ObjectMapper();
        
        logger.info("🏢 Professional Shoonya API initialized");
        logger.info("🔑 Vendor Code: {}", VENDOR_CODE);
        logger.info("👤 User ID: {}", USER_ID);
        logger.info("🔐 API Key: {}***", API_KEY.substring(0, 8));
    }
    
    /**
     * Authenticate with Shoonya API using real credentials
     */
    public boolean authenticate() {
        try {
            logger.info("🔐 Attempting Shoonya authentication...");
            
            // Prepare authentication request
            String authHash = generateAuthHash(PASSWORD);
            String requestBody = String.format(
                "jData={\"uid\":\"%s\",\"pwd\":\"%s\",\"factor2\":\"second_factor\",\"vc\":\"%s\",\"appkey\":\"%s\",\"imei\":\"%s\",\"source\":\"API\"}&jKey=%s",
                USER_ID, authHash, VENDOR_CODE, API_KEY, IMEI, USER_ID
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/QuickAuth"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode responseJson = mapper.readTree(response.body());
                
                if (responseJson.has("susertoken")) {
                    this.sessionToken = responseJson.get("susertoken").asText();
                    this.isAuthenticated = true;
                    this.tokenExpiry = LocalDateTime.now().plusHours(8); // Typical session duration
                    
                    logger.info("✅ Shoonya authentication successful");
                    logger.info("🎫 Session token obtained: {}***", sessionToken.substring(0, 10));
                    logger.info("⏰ Token expires: {}", tokenExpiry.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                    
                    return true;
                } else {
                    String error = responseJson.path("emsg").asText("Unknown error");
                    logger.error("❌ Authentication failed: {}", error);
                    return false;
                }
            } else {
                logger.error("❌ Authentication request failed: HTTP {}", response.statusCode());
                logger.error("Response: {}", response.body());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("❌ Authentication exception: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get real-time market quotes for specified instruments
     */
    public Map<String, ShoonyaQuote> getRealTimeQuotes(List<String> symbols) {
        if (!isAuthenticated || isTokenExpired()) {
            logger.warn("⚠️ Not authenticated or token expired. Attempting re-authentication...");
            if (!authenticate()) {
                logger.error("❌ Failed to authenticate with Shoonya API");
                return Collections.emptyMap();
            }
        }
        
        Map<String, ShoonyaQuote> quotes = new HashMap<>();
        
        for (String symbol : symbols) {
            try {
                ShoonyaQuote quote = getRealTimeQuote(symbol);
                if (quote != null) {
                    quotes.put(symbol, quote);
                    latestQuotes.put(symbol, quote);
                }
            } catch (Exception e) {
                logger.error("❌ Error fetching quote for {}: {}", symbol, e.getMessage());
            }
        }
        
        return quotes;
    }
    
    /**
     * Get real-time quote for a single instrument
     */
    private ShoonyaQuote getRealTimeQuote(String symbol) throws Exception {
        String token = INSTRUMENT_TOKENS.get(symbol);
        if (token == null) {
            throw new IllegalArgumentException("Unknown symbol: " + symbol);
        }
        
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
        
        if (response.statusCode() == 200) {
            JsonNode responseJson = mapper.readTree(response.body());
            
            if (responseJson.has("lp")) {
                return parseQuoteResponse(symbol, token, responseJson);
            } else {
                String error = responseJson.path("emsg").asText("No quote data");
                logger.warn("⚠️ No quote data for {}: {}", symbol, error);
                return null;
            }
        } else {
            logger.error("❌ Quote request failed for {}: HTTP {}", symbol, response.statusCode());
            return null;
        }
    }
    
    /**
     * Parse Shoonya quote response
     */
    private ShoonyaQuote parseQuoteResponse(String symbol, String token, JsonNode json) {
        try {
            double ltp = json.path("lp").asDouble();
            double open = json.path("o").asDouble();
            double high = json.path("h").asDouble();
            double low = json.path("l").asDouble();
            double close = json.path("c").asDouble();
            double volume = json.path("v").asDouble();
            
            double change = ltp - close;
            double changePercent = close > 0 ? (change / close) * 100 : 0.0;
            
            return new ShoonyaQuote(
                symbol, token, ltp, change, changePercent,
                high, low, open, close, volume,
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            logger.error("❌ Error parsing quote for {}: {}", symbol, e.getMessage());
            return null;
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
     * Check if session token has expired
     */
    private boolean isTokenExpired() {
        return tokenExpiry == null || LocalDateTime.now().isAfter(tokenExpiry);
    }
    
    /**
     * Get live market data for major indices
     */
    public void displayLiveMarketData() {
        logger.info("📊 Fetching live market data from Shoonya...");
        
        List<String> symbols = Arrays.asList("NIFTY", "SENSEX", "BANKNIFTY", "FINNIFTY");
        Map<String, ShoonyaQuote> quotes = getRealTimeQuotes(symbols);
        
        if (quotes.isEmpty()) {
            logger.error("❌ No market data available from Shoonya API");
            return;
        }
        
        System.out.println("\n🏢 === SHOONYA LIVE MARKET DATA ===");
        System.out.println("⏰ Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
        System.out.println("📡 Source: Finvasia Shoonya API");
        System.out.println("🎫 Session: " + sessionToken.substring(0, 10) + "***");
        System.out.println();
        
        quotes.forEach((symbol, quote) -> displayQuote(quote));
        
        System.out.println("\n✅ Real-time data successfully fetched from Shoonya API");
    }
    
    /**
     * Display quote information
     */
    private void displayQuote(ShoonyaQuote quote) {
        String trend = quote.getChange() >= 0 ? "📈" : "📉";
        String color = quote.getChange() >= 0 ? "🟢" : "🔴";
        
        System.out.printf("%s %s %s: ₹%,.2f %+.2f (%+.2f%%) [Token: %s]\n",
                color, trend, quote.getSymbol(), quote.getLtp(), 
                quote.getChange(), quote.getChangePercent(), quote.getToken());
        
        System.out.printf("   Open: ₹%,.2f | High: ₹%,.2f | Low: ₹%,.2f | Volume: %,.0f\n",
                quote.getOpen(), quote.getHigh(), quote.getLow(), quote.getVolume());
        System.out.println();
    }
    
    /**
     * Test connectivity and authentication
     */
    public void testConnectivity() {
        System.out.println("🔍 === PROFESSIONAL SHOONYA API CONNECTIVITY TEST ===");
        System.out.println("🏢 Vendor: " + VENDOR_CODE);
        System.out.println("👤 User ID: " + USER_ID);
        System.out.println("🔐 API Key: " + API_KEY.substring(0, 8) + "***");
        System.out.println();
        
        System.out.println("🔐 Testing authentication...");
        boolean authSuccess = authenticate();
        
        if (authSuccess) {
            System.out.println("✅ Authentication: SUCCESS");
            System.out.println("🎫 Session Token: " + sessionToken.substring(0, 10) + "***");
            System.out.println();
            
            System.out.println("📊 Testing market data fetch...");
            displayLiveMarketData();
            
            System.out.println("🎉 === SHOONYA API FULLY OPERATIONAL ===");
        } else {
            System.out.println("❌ Authentication: FAILED");
            System.out.println();
            System.out.println("💡 TROUBLESHOOTING:");
            System.out.println("   1. Verify password is correct: " + PASSWORD);
            System.out.println("   2. Check if API access is enabled on account");
            System.out.println("   3. Confirm account has live trading permissions");
            System.out.println("   4. Contact Finvasia support if issues persist");
        }
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
                logger.info("🔓 Logged out from Shoonya API");
                
            } catch (Exception e) {
                logger.warn("⚠️ Error during logout: {}", e.getMessage());
            }
        }
        
        isAuthenticated = false;
        sessionToken = null;
        tokenExpiry = null;
    }
    
    /**
     * Get latest cached quotes
     */
    public Map<String, ShoonyaQuote> getLatestQuotes() {
        return new HashMap<>(latestQuotes);
    }
    
    /**
     * Check if API is ready for use
     */
    public boolean isReady() {
        return isAuthenticated && !isTokenExpired();
    }
    
    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        System.out.println("🚀 === PROFESSIONAL SHOONYA API INTEGRATION ===");
        System.out.println("🏢 Finvasia Real-Time Market Data System");
        System.out.println();
        
        ProfessionalShoonyaAPI api = new ProfessionalShoonyaAPI();
        
        try {
            // Test connectivity and authentication
            api.testConnectivity();
            
        } finally {
            // Cleanup
            api.logout();
        }
        
        System.out.println("\n🎯 === INTEGRATION COMPLETE ===");
        System.out.println("Use this implementation in your failover system for real Shoonya data!");
    }
    
    /**
     * Shoonya Quote data class
     */
    public static class ShoonyaQuote {
        private final String symbol;
        private final String token;
        private final double ltp;
        private final double change;
        private final double changePercent;
        private final double high;
        private final double low;
        private final double open;
        private final double previousClose;
        private final double volume;
        private final LocalDateTime timestamp;
        
        public ShoonyaQuote(String symbol, String token, double ltp, double change, 
                           double changePercent, double high, double low, double open, 
                           double previousClose, double volume, LocalDateTime timestamp) {
            this.symbol = symbol;
            this.token = token;
            this.ltp = ltp;
            this.change = change;
            this.changePercent = changePercent;
            this.high = high;
            this.low = low;
            this.open = open;
            this.previousClose = previousClose;
            this.volume = volume;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getSymbol() { return symbol; }
        public String getToken() { return token; }
        public double getLtp() { return ltp; }
        public double getChange() { return change; }
        public double getChangePercent() { return changePercent; }
        public double getHigh() { return high; }
        public double getLow() { return low; }
        public double getOpen() { return open; }
        public double getPreviousClose() { return previousClose; }
        public double getVolume() { return volume; }
        public LocalDateTime getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return String.format("%s: ₹%.2f (%+.2f%%)", symbol, ltp, changePercent);
        }
    }
}