package com.trading.bot.market;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Robust Price Failover System
 * 1. Try Upstox API first
 * 2. If Upstox fails, try Shoonya API
 * 3. If both fail, show clear error - NO FAKE/MOCK DATA
 */
public class RobustPriceFailoverSystem {
    
    // API Credentials
    private static final String UPSTOX_API_KEY = "768a303b-80f1-46d6-af16-f847f9341213";
    private static final String UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTEwYjVjNjAwMGE4YzY0YWM5OGZjYzgiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MjcwMjc5MCwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYyNzI1NjAwfQ.DtaAeJFxSiIYyh8ZC9BV8vo8UczBu6DY8u3aWOsSdMY";
    
    private static final String SHOONYA_VENDOR_CODE = "FN144243_U";
    private static final String SHOONYA_IMEI = "abc123";
    private static final String SHOONYA_API_KEY = "aa27c122b1641b7d0547904269303a2e";
    
    private static final String UPSTOX_BASE_URL = "https://api.upstox.com/v2";
    private static final String SHOONYA_BASE_URL = "https://api.shoonya.com/NorenWClientTP";
    
    private final HttpClient httpClient;
    private final Map<String, String> upstoxInstruments = new HashMap<>();
    private final Map<String, String> shoonyaTokens = new HashMap<>();
    
    // Failover status tracking
    private boolean upstoxAvailable = false;
    private boolean shoonyaAvailable = false;
    private String lastError = "";
    
    public RobustPriceFailoverSystem() {
        this.httpClient = HttpClient.newHttpClient();
        initializeInstrumentMappings();
        System.out.println("🛡️ Robust Price Failover System initialized");
        System.out.println("📡 Primary: Upstox | Backup: Shoonya | No Mock Data Policy");
    }
    
    private void initializeInstrumentMappings() {
        // Upstox instrument keys
        upstoxInstruments.put("NIFTY", "NSE_INDEX%7CNifty%2050");
        upstoxInstruments.put("SENSEX", "BSE_INDEX%7CSENSEX");
        upstoxInstruments.put("BANKNIFTY", "NSE_INDEX%7CNifty%20Bank");
        upstoxInstruments.put("FINNIFTY", "NSE_INDEX%7CNifty%20Fin%20Services");
        
        // Shoonya tokens
        shoonyaTokens.put("NIFTY", "26000");
        shoonyaTokens.put("SENSEX", "1");
        shoonyaTokens.put("BANKNIFTY", "26009");
        shoonyaTokens.put("FINNIFTY", "26037");
    }
    
    /**
     * Main function to get live prices with failover
     * Returns real data or clear error - NO FAKE DATA
     */
    public PriceResult getLivePrice(String symbol) {
        System.out.println("\n🔍 === GETTING LIVE PRICE FOR " + symbol + " ===");
        System.out.println("⏰ Request Time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        
        // Step 1: Try Upstox first
        System.out.println("📡 Step 1: Trying Upstox API (Primary)...");
        PriceResult upstoxResult = tryUpstoxAPI(symbol);
        
        if (upstoxResult.isSuccess()) {
            System.out.println("✅ SUCCESS: Got live data from Upstox");
            upstoxAvailable = true;
            return upstoxResult;
        } else {
            System.out.println("❌ FAILED: Upstox API failed - " + upstoxResult.getErrorMessage());
            upstoxAvailable = false;
        }
        
        // Step 2: Try Shoonya as backup
        System.out.println("📡 Step 2: Trying Shoonya API (Backup)...");
        PriceResult shoonyaResult = tryShoonyaAPI(symbol);
        
        if (shoonyaResult.isSuccess()) {
            System.out.println("✅ SUCCESS: Got live data from Shoonya (Backup)");
            shoonyaAvailable = true;
            return shoonyaResult;
        } else {
            System.out.println("❌ FAILED: Shoonya API also failed - " + shoonyaResult.getErrorMessage());
            shoonyaAvailable = false;
        }
        
        // Step 3: Both APIs failed - Return clear error
        System.out.println("🚨 CRITICAL: Both APIs failed!");
        lastError = "Both Upstox and Shoonya APIs failed";
        
        return PriceResult.createError(
            symbol,
            "BOTH_APIS_FAILED",
            "Unable to fetch live data from either Upstox or Shoonya. " +
            "Upstox Error: " + upstoxResult.getErrorMessage() + 
            " | Shoonya Error: " + shoonyaResult.getErrorMessage(),
            LocalDateTime.now()
        );
    }
    
    /**
     * Try to get price from Upstox API
     */
    private PriceResult tryUpstoxAPI(String symbol) {
        try {
            String instrumentKey = upstoxInstruments.get(symbol);
            if (instrumentKey == null) {
                return PriceResult.createError(symbol, "INVALID_SYMBOL", 
                    "Symbol " + symbol + " not supported in Upstox", LocalDateTime.now());
            }
            
            String url = UPSTOX_BASE_URL + "/market-quote/quotes?instrument_key=" + instrumentKey;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + UPSTOX_ACCESS_TOKEN)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseUpstoxResponse(symbol, response.body());
            } else {
                return PriceResult.createError(symbol, "UPSTOX_HTTP_ERROR", 
                    "HTTP " + response.statusCode() + ": " + response.body(), LocalDateTime.now());
            }
            
        } catch (Exception e) {
            return PriceResult.createError(symbol, "UPSTOX_NETWORK_ERROR", 
                "Network error: " + e.getMessage(), LocalDateTime.now());
        }
    }
    
    /**
     * Try to get price from Shoonya API
     */
    private PriceResult tryShoonyaAPI(String symbol) {
        try {
            String token = shoonyaTokens.get(symbol);
            if (token == null) {
                return PriceResult.createError(symbol, "INVALID_SYMBOL", 
                    "Symbol " + symbol + " not supported in Shoonya", LocalDateTime.now());
            }
            
            // First login to get session token
            String sessionToken = getShoonyaSessionToken();
            if (sessionToken == null) {
                return PriceResult.createError(symbol, "SHOONYA_LOGIN_FAILED", 
                    "Failed to get Shoonya session token", LocalDateTime.now());
            }
            
            // Get quote
            String url = SHOONYA_BASE_URL + "/GetQuotes";
            String formData = "jKey=" + sessionToken + "&exch=NSE&token=" + token;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseShoonyaResponse(symbol, response.body());
            } else {
                return PriceResult.createError(symbol, "SHOONYA_HTTP_ERROR", 
                    "HTTP " + response.statusCode() + ": " + response.body(), LocalDateTime.now());
            }
            
        } catch (Exception e) {
            return PriceResult.createError(symbol, "SHOONYA_NETWORK_ERROR", 
                "Network error: " + e.getMessage(), LocalDateTime.now());
        }
    }
    
    /**
     * Get Shoonya session token
     */
    private String getShoonyaSessionToken() {
        try {
            String url = SHOONYA_BASE_URL + "/QuickAuth";
            String formData = "jData={\"uid\":\"" + SHOONYA_VENDOR_CODE + 
                             "\",\"pwd\":\"your_password\",\"factor2\":\"" + SHOONYA_IMEI + 
                             "\",\"vc\":\"" + SHOONYA_VENDOR_CODE + 
                             "\",\"appkey\":\"" + SHOONYA_API_KEY + "\",\"imei\":\"" + SHOONYA_IMEI + "\"}";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                // Parse session token from response
                String responseBody = response.body();
                // Simple extraction (in production, use proper JSON parser)
                if (responseBody.contains("\"susertoken\":")) {
                    int start = responseBody.indexOf("\"susertoken\":\"") + 14;
                    int end = responseBody.indexOf("\"", start);
                    return responseBody.substring(start, end);
                }
            }
            
            return null;
            
        } catch (Exception e) {
            System.err.println("Error getting Shoonya session: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse Upstox API response - ONLY REAL DATA
     */
    private PriceResult parseUpstoxResponse(String symbol, String jsonResponse) {
        try {
            // Simple JSON parsing without external dependencies
            // In production, use proper JSON library
            
            if (!jsonResponse.contains("\"status\":\"success\"")) {
                return PriceResult.createError(symbol, "UPSTOX_API_ERROR", 
                    "API returned non-success status", LocalDateTime.now());
            }
            
            // Extract real values from JSON
            double ltp = extractJsonDouble(jsonResponse, "ltp");
            double netChange = extractJsonDouble(jsonResponse, "net_change");
            double pctChange = extractJsonDouble(jsonResponse, "pct_change");
            double open = extractJsonDouble(jsonResponse, "open");
            double high = extractJsonDouble(jsonResponse, "high");
            double low = extractJsonDouble(jsonResponse, "low");
            
            if (ltp == 0.0) {
                return PriceResult.createError(symbol, "UPSTOX_PARSE_ERROR", 
                    "Could not parse price data from response", LocalDateTime.now());
            }
            
            return PriceResult.createSuccess(
                symbol,
                "UPSTOX",
                ltp,
                netChange,
                pctChange,
                high,
                low,
                open,
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            return PriceResult.createError(symbol, "UPSTOX_PARSE_ERROR", 
                "Error parsing response: " + e.getMessage(), LocalDateTime.now());
        }
    }
    
    /**
     * Parse Shoonya API response - ONLY REAL DATA
     */
    private PriceResult parseShoonyaResponse(String symbol, String jsonResponse) {
        try {
            if (!jsonResponse.contains("\"stat\":\"Ok\"")) {
                return PriceResult.createError(symbol, "SHOONYA_API_ERROR", 
                    "API returned error status", LocalDateTime.now());
            }
            
            double ltp = extractJsonDouble(jsonResponse, "lp");
            double netChange = extractJsonDouble(jsonResponse, "c");
            double open = extractJsonDouble(jsonResponse, "o");
            double high = extractJsonDouble(jsonResponse, "h");
            double low = extractJsonDouble(jsonResponse, "l");
            
            if (ltp == 0.0) {
                return PriceResult.createError(symbol, "SHOONYA_PARSE_ERROR", 
                    "Could not parse price data from response", LocalDateTime.now());
            }
            
            double pctChange = netChange / (ltp - netChange) * 100;
            
            return PriceResult.createSuccess(
                symbol,
                "SHOONYA",
                ltp,
                netChange,
                pctChange,
                high,
                low,
                open,
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            return PriceResult.createError(symbol, "SHOONYA_PARSE_ERROR", 
                "Error parsing response: " + e.getMessage(), LocalDateTime.now());
        }
    }
    
    /**
     * Extract double value from JSON string
     */
    private double extractJsonDouble(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1) return 0.0;
            
            startIndex += searchKey.length();
            int endIndex = json.indexOf(",", startIndex);
            if (endIndex == -1) endIndex = json.indexOf("}", startIndex);
            
            String valueStr = json.substring(startIndex, endIndex).trim();
            valueStr = valueStr.replace("\"", "");
            
            return Double.parseDouble(valueStr);
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * Get multiple symbols with failover
     */
    public void getMultiplePrices(String... symbols) {
        System.out.println("\n🚀 === FETCHING MULTIPLE LIVE PRICES ===");
        System.out.println("📊 Symbols: " + Arrays.toString(symbols));
        System.out.println("🛡️ Failover: Upstox → Shoonya → Error (No Mock Data)");
        System.out.println();
        
        Map<String, PriceResult> results = new HashMap<>();
        int successCount = 0;
        int errorCount = 0;
        
        for (String symbol : symbols) {
            PriceResult result = getLivePrice(symbol);
            results.put(symbol, result);
            
            if (result.isSuccess()) {
                successCount++;
                displaySuccessResult(result);
            } else {
                errorCount++;
                displayErrorResult(result);
            }
        }
        
        // Summary
        System.out.println("\n📊 === FETCH SUMMARY ===");
        System.out.printf("✅ Successful: %d/%d\n", successCount, symbols.length);
        System.out.printf("❌ Failed: %d/%d\n", errorCount, symbols.length);
        System.out.println("📡 Upstox Status: " + (upstoxAvailable ? "✅ Available" : "❌ Unavailable"));
        System.out.println("🏢 Shoonya Status: " + (shoonyaAvailable ? "✅ Available" : "❌ Unavailable"));
        
        if (errorCount > 0) {
            System.out.println("⚠️  Some symbols failed - check network connectivity and API status");
        }
    }
    
    /**
     * Display successful result
     */
    private void displaySuccessResult(PriceResult result) {
        String trend = result.getNetChange() >= 0 ? "📈" : "📉";
        String color = result.getNetChange() >= 0 ? "🟢" : "🔴";
        
        System.out.printf("%s %s %s: ₹%,.2f (%+.2f%%) [%s]\n", 
                         color, trend, result.getSymbol(), result.getLtp(), 
                         result.getPctChange(), result.getDataSource());
        
        System.out.printf("   Open: ₹%,.2f | High: ₹%,.2f | Low: ₹%,.2f\n", 
                         result.getOpen(), result.getHigh(), result.getLow());
        System.out.printf("   Time: %s | Source: %s API\n", 
                         result.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                         result.getDataSource());
        System.out.println();
    }
    
    /**
     * Display error result
     */
    private void displayErrorResult(PriceResult result) {
        System.out.printf("❌ %s: FAILED\n", result.getSymbol());
        System.out.printf("   Error Type: %s\n", result.getErrorCode());
        System.out.printf("   Error Message: %s\n", result.getErrorMessage());
        System.out.printf("   Time: %s\n", 
                         result.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println("   📛 NO MOCK DATA - Only real market data displayed");
        System.out.println();
    }
    
    /**
     * System health check
     */
    public void systemHealthCheck() {
        System.out.println("\n🔍 === SYSTEM HEALTH CHECK ===");
        
        // Test both APIs with a simple symbol
        System.out.println("Testing API connectivity...");
        
        PriceResult upstoxTest = tryUpstoxAPI("NIFTY");
        PriceResult shoonyaTest = tryShoonyaAPI("NIFTY");
        
        System.out.println("📡 Upstox API: " + (upstoxTest.isSuccess() ? "✅ Working" : "❌ Failed - " + upstoxTest.getErrorMessage()));
        System.out.println("🏢 Shoonya API: " + (shoonyaTest.isSuccess() ? "✅ Working" : "❌ Failed - " + shoonyaTest.getErrorMessage()));
        
        if (upstoxTest.isSuccess() || shoonyaTest.isSuccess()) {
            System.out.println("🎯 System Status: ✅ OPERATIONAL (At least one API working)");
        } else {
            System.out.println("🚨 System Status: ❌ CRITICAL (Both APIs failed)");
            System.out.println("⚠️  Check network connectivity and API credentials");
        }
    }
    
    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        System.out.println("🛡️ === ROBUST PRICE FAILOVER SYSTEM ===");
        System.out.println("📡 Upstox → Shoonya → Clear Error (No Mock Data)");
        System.out.println();
        
        RobustPriceFailoverSystem system = new RobustPriceFailoverSystem();
        
        // System health check first
        system.systemHealthCheck();
        
        // Get multiple prices
        system.getMultiplePrices("NIFTY", "SENSEX", "BANKNIFTY", "FINNIFTY");
        
        System.out.println("🎯 === FAILOVER SYSTEM READY ===");
        System.out.println("✅ Professional-grade data reliability");
        System.out.println("✅ No fake/mock data policy enforced");
        System.out.println("✅ Clear error reporting when APIs fail");
    }
    
    /**
     * Price Result class - holds real data or clear error
     */
    public static class PriceResult {
        private final String symbol;
        private final boolean success;
        private final String dataSource;
        private final double ltp;
        private final double netChange;
        private final double pctChange;
        private final double high;
        private final double low;
        private final double open;
        private final String errorCode;
        private final String errorMessage;
        private final LocalDateTime timestamp;
        
        private PriceResult(String symbol, boolean success, String dataSource, 
                           double ltp, double netChange, double pctChange, 
                           double high, double low, double open,
                           String errorCode, String errorMessage, LocalDateTime timestamp) {
            this.symbol = symbol;
            this.success = success;
            this.dataSource = dataSource;
            this.ltp = ltp;
            this.netChange = netChange;
            this.pctChange = pctChange;
            this.high = high;
            this.low = low;
            this.open = open;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.timestamp = timestamp;
        }
        
        public static PriceResult createSuccess(String symbol, String dataSource, 
                                              double ltp, double netChange, double pctChange,
                                              double high, double low, double open, 
                                              LocalDateTime timestamp) {
            return new PriceResult(symbol, true, dataSource, ltp, netChange, pctChange, 
                                 high, low, open, null, null, timestamp);
        }
        
        public static PriceResult createError(String symbol, String errorCode, 
                                            String errorMessage, LocalDateTime timestamp) {
            return new PriceResult(symbol, false, null, 0, 0, 0, 0, 0, 0, 
                                 errorCode, errorMessage, timestamp);
        }
        
        // Getters
        public String getSymbol() { return symbol; }
        public boolean isSuccess() { return success; }
        public String getDataSource() { return dataSource; }
        public double getLtp() { return ltp; }
        public double getNetChange() { return netChange; }
        public double getPctChange() { return pctChange; }
        public double getHigh() { return high; }
        public double getLow() { return low; }
        public double getOpen() { return open; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}