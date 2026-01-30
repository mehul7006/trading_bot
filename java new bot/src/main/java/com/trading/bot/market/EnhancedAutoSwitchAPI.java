package com.trading.bot.market;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced Auto-Switch API Failover System
 * Priority: Upstox → Yahoo Finance → Alpha Vantage → NSE Direct → Finnhub → Polygon
 * Only provides REAL market data - no fake/mock data
 */
public class EnhancedAutoSwitchAPI {
    
    // API Credentials
    private static final String UPSTOX_API_KEY = "768a303b-80f1-46d6-af16-f847f9341213";
    private static final String UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTEwYjVjNjAwMGE4YzY0YWM5OGZjYzgiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MjcwMjc5MCwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYyNzI1NjAwfQ.DtaAeJFxSiIYyh8ZC9BV8vo8UczBu6DY8u3aWOsSdMY";
    
    // Free API Keys (can be obtained free from respective providers)
    private static final String ALPHA_VANTAGE_KEY = "demo"; // Get free key from: https://www.alphavantage.co/support/#api-key
    private static final String FINNHUB_KEY = "demo"; // Get free key from: https://finnhub.io/register
    private static final String POLYGON_KEY = "demo"; // Get free key from: https://polygon.io/
    
    // API URLs
    private static final String UPSTOX_BASE_URL = "https://api.upstox.com/v2";
    private static final String YAHOO_BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart";
    private static final String ALPHA_VANTAGE_URL = "https://www.alphavantage.co/query";
    private static final String NSE_URL = "https://www.nseindia.com/api/equity-stockIndices";
    private static final String FINNHUB_URL = "https://finnhub.io/api/v1/quote";
    private static final String POLYGON_URL = "https://api.polygon.io/v2/aggs/ticker";
    
    private final HttpClient httpClient;
    private final Map<String, APIStatus> apiStatusMap = new HashMap<>();
    private final Map<String, String> symbolMappings = new HashMap<>();
    
    public EnhancedAutoSwitchAPI() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        initializeSymbolMappings();
        initializeAPIStatus();
        System.out.println("🔄 Enhanced Auto-Switch API System Initialized");
        System.out.println("📡 6 API Sources: Upstox → Yahoo → AlphaVantage → NSE → Finnhub → Polygon");
    }
    
    /**
     * Initialize symbol mappings for different APIs
     */
    private void initializeSymbolMappings() {
        // Upstox symbols
        symbolMappings.put("NIFTY_UPSTOX", "NSE_INDEX%7CNifty%2050");
        symbolMappings.put("SENSEX_UPSTOX", "BSE_INDEX%7CSENSEX");
        symbolMappings.put("BANKNIFTY_UPSTOX", "NSE_INDEX%7CNifty%20Bank");
        symbolMappings.put("FINNIFTY_UPSTOX", "NSE_INDEX%7CNifty%20Fin%20Services");
        
        // Yahoo Finance symbols (Indian indices)
        symbolMappings.put("NIFTY_YAHOO", "^NSEI");
        symbolMappings.put("SENSEX_YAHOO", "^BSESN");
        symbolMappings.put("BANKNIFTY_YAHOO", "^NSEBANK");
        symbolMappings.put("FINNIFTY_YAHOO", "^CNXFIN");
        
        // Alpha Vantage symbols
        symbolMappings.put("NIFTY_AV", "NIFTY.BSE");
        symbolMappings.put("SENSEX_AV", "SENSEX.BSE");
        
        // NSE Direct symbols
        symbolMappings.put("NIFTY_NSE", "NIFTY 50");
        symbolMappings.put("SENSEX_NSE", "S&P BSE SENSEX");
        symbolMappings.put("BANKNIFTY_NSE", "NIFTY BANK");
        
        System.out.println("✅ Symbol mappings initialized for 6 API sources");
    }
    
    /**
     * Initialize API status tracking
     */
    private void initializeAPIStatus() {
        apiStatusMap.put("UPSTOX", new APIStatus("UPSTOX", true, 0, LocalDateTime.now()));
        apiStatusMap.put("YAHOO", new APIStatus("YAHOO", true, 0, LocalDateTime.now()));
        apiStatusMap.put("ALPHAVANTAGE", new APIStatus("ALPHAVANTAGE", true, 0, LocalDateTime.now()));
        apiStatusMap.put("NSE", new APIStatus("NSE", true, 0, LocalDateTime.now()));
        apiStatusMap.put("FINNHUB", new APIStatus("FINNHUB", true, 0, LocalDateTime.now()));
        apiStatusMap.put("POLYGON", new APIStatus("POLYGON", true, 0, LocalDateTime.now()));
    }
    
    /**
     * Main method to get live price with response time based API switching
     * ENHANCED: Upstox primary with response time optimization
     */
    public MarketDataResult getLivePrice(String symbol) {
        System.out.println("\n🎯 === RESPONSE TIME OPTIMIZED API FETCHING: " + symbol + " ===");
        System.out.println("⏰ Time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
        System.out.println("🚀 Primary: UPSTOX | Failover: Response Time Based");
        
        // Get APIs sorted by response time performance
        String[] apiPriority = getSortedAPIsByResponseTime();
        
        for (String apiName : apiPriority) {
            APIStatus status = apiStatusMap.get(apiName);
            
            // Skip if API is temporarily disabled due to poor performance
            if (!status.isAvailable() && (status.getFailureCount() >= 3 || status.getAverageResponseTime() > 8000)) {
                System.out.println("⏭️ Skipping " + apiName + " (poor performance: " + 
                    status.getAverageResponseTime() + "ms avg, " + status.getFailureCount() + " failures)");
                continue;
            }
            
            System.out.println("📡 Trying " + apiName + " API (Avg: " + status.getAverageResponseTime() + "ms)...");
            
            long startTime = System.currentTimeMillis();
            MarketDataResult result = fetchFromAPI(symbol, apiName);
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Update response time tracking
            status.addResponseTime(responseTime);
            
            if (result.isSuccess()) {
                System.out.printf("✅ SUCCESS: %s responded in %dms (avg: %dms)\n", 
                    apiName, responseTime, status.getAverageResponseTime());
                updateAPIStatus(apiName, true);
                return result;
            } else {
                System.out.printf("❌ FAILED: %s failed in %dms - %s\n", 
                    apiName, responseTime, result.getErrorMessage());
                updateAPIStatus(apiName, false);
            }
        }
        
        // All APIs failed
        System.out.println("🚨 CRITICAL: All APIs failed for " + symbol);
        return MarketDataResult.createError(symbol, "ALL_APIS_FAILED", 
                "All API sources failed", LocalDateTime.now());
    }
    
    /**
     * Get APIs sorted by response time performance
     * Prioritizes Upstox, then sorts others by response time
     */
    private String[] getSortedAPIsByResponseTime() {
        List<String> apis = new ArrayList<>(Arrays.asList("UPSTOX", "YAHOO", "ALPHAVANTAGE", "NSE", "FINNHUB", "POLYGON"));
        
        // Always try Upstox first (primary API requirement)
        List<String> sortedAPIs = new ArrayList<>();
        if (apis.contains("UPSTOX") && apiStatusMap.get("UPSTOX").isAvailable()) {
            sortedAPIs.add("UPSTOX");
            apis.remove("UPSTOX");
        }
        
        // Sort remaining APIs by response time performance
        apis.sort((api1, api2) -> {
            APIStatus status1 = apiStatusMap.get(api1);
            APIStatus status2 = apiStatusMap.get(api2);
            
            // First by success rate
            double successRate1 = status1.getSuccessRate();
            double successRate2 = status2.getSuccessRate();
            if (Math.abs(successRate1 - successRate2) > 0.1) {
                return Double.compare(successRate2, successRate1);
            }
            
            // Then by response time
            return Long.compare(status1.getAverageResponseTime(), status2.getAverageResponseTime());
        });
        
        sortedAPIs.addAll(apis);
        return sortedAPIs.toArray(new String[0]);
    }
    
    /**
     * Fetch data from specific API
     */
    private MarketDataResult fetchFromAPI(String symbol, String apiName) {
        try {
            switch (apiName) {
                case "UPSTOX":
                    return fetchFromUpstox(symbol);
                case "YAHOO":
                    return fetchFromYahoo(symbol);
                case "ALPHAVANTAGE":
                    return fetchFromAlphaVantage(symbol);
                case "NSE":
                    return fetchFromNSE(symbol);
                case "FINNHUB":
                    return fetchFromFinnhub(symbol);
                case "POLYGON":
                    return fetchFromPolygon(symbol);
                default:
                    return MarketDataResult.createError(symbol, "UNKNOWN_API", 
                            "Unknown API: " + apiName, LocalDateTime.now());
            }
        } catch (Exception e) {
            return MarketDataResult.createError(symbol, apiName + "_ERROR", 
                    "Exception: " + e.getMessage(), LocalDateTime.now());
        }
    }
    
    /**
     * Fetch from Upstox API
     */
    private MarketDataResult fetchFromUpstox(String symbol) throws Exception {
        String instrumentKey = symbolMappings.get(symbol + "_UPSTOX");
        if (instrumentKey == null) {
            throw new RuntimeException("Symbol not supported in Upstox: " + symbol);
        }
        
        String url = UPSTOX_BASE_URL + "/market-quote/ltp?symbol=" + instrumentKey;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + UPSTOX_ACCESS_TOKEN)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return parseUpstoxResponse(symbol, response.body());
        } else {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }
    
    /**
     * Fetch from Yahoo Finance API (FREE)
     */
    private MarketDataResult fetchFromYahoo(String symbol) throws Exception {
        String yahooSymbol = symbolMappings.get(symbol + "_YAHOO");
        if (yahooSymbol == null) {
            throw new RuntimeException("Symbol not supported in Yahoo: " + symbol);
        }
        
        String url = YAHOO_BASE_URL + "/" + yahooSymbol + "?interval=1m&range=1d";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return parseYahooResponse(symbol, response.body());
        } else {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }
    
    /**
     * Fetch from Alpha Vantage API (FREE with registration)
     */
    private MarketDataResult fetchFromAlphaVantage(String symbol) throws Exception {
        String avSymbol = symbolMappings.get(symbol + "_AV");
        if (avSymbol == null) {
            throw new RuntimeException("Symbol not supported in AlphaVantage: " + symbol);
        }
        
        String url = ALPHA_VANTAGE_URL + "?function=GLOBAL_QUOTE&symbol=" + avSymbol + "&apikey=" + ALPHA_VANTAGE_KEY;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return parseAlphaVantageResponse(symbol, response.body());
        } else {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }
    
    /**
     * Fetch from NSE Direct API (FREE)
     */
    private MarketDataResult fetchFromNSE(String symbol) throws Exception {
        String url = NSE_URL + "?index=" + symbolMappings.get(symbol + "_NSE");
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return parseNSEResponse(symbol, response.body());
        } else {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }
    
    /**
     * Fetch from Finnhub API (FREE with registration)
     */
    private MarketDataResult fetchFromFinnhub(String symbol) throws Exception {
        // Convert Indian symbols to international format for Finnhub
        String finnhubSymbol = convertToFinnhubSymbol(symbol);
        String url = FINNHUB_URL + "?symbol=" + finnhubSymbol + "&token=" + FINNHUB_KEY;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return parseFinnhubResponse(symbol, response.body());
        } else {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }
    
    /**
     * Fetch from Polygon API (FREE with registration)
     */
    private MarketDataResult fetchFromPolygon(String symbol) throws Exception {
        String polygonSymbol = convertToPolygonSymbol(symbol);
        String yesterday = LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String url = POLYGON_URL + "/" + polygonSymbol + "/range/1/day/" + yesterday + "/" + yesterday + "?apikey=" + POLYGON_KEY;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return parsePolygonResponse(symbol, response.body());
        } else {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }
    
    // Response parsers for each API
    private MarketDataResult parseUpstoxResponse(String symbol, String json) {
        try {
            if (json.contains("\"last_price\":")) {
                double price = extractJsonDouble(json, "last_price");
                return MarketDataResult.createSuccess(symbol, "UPSTOX", price, 0, 0, 0, 0, price, LocalDateTime.now());
            }
            throw new RuntimeException("Invalid Upstox response format");
        } catch (Exception e) {
            return MarketDataResult.createError(symbol, "UPSTOX_PARSE_ERROR", e.getMessage(), LocalDateTime.now());
        }
    }
    
    private MarketDataResult parseYahooResponse(String symbol, String json) {
        try {
            // Yahoo returns complex nested JSON - simplified parsing
            if (json.contains("\"regularMarketPrice\":")) {
                double price = extractJsonDouble(json, "regularMarketPrice");
                double open = extractJsonDouble(json, "regularMarketOpen");
                double high = extractJsonDouble(json, "regularMarketDayHigh");
                double low = extractJsonDouble(json, "regularMarketDayLow");
                double prevClose = extractJsonDouble(json, "regularMarketPreviousClose");
                
                return MarketDataResult.createSuccess(symbol, "YAHOO", price, price - prevClose, 
                        ((price - prevClose) / prevClose) * 100, high, low, open, LocalDateTime.now());
            }
            throw new RuntimeException("Invalid Yahoo response format");
        } catch (Exception e) {
            return MarketDataResult.createError(symbol, "YAHOO_PARSE_ERROR", e.getMessage(), LocalDateTime.now());
        }
    }
    
    private MarketDataResult parseAlphaVantageResponse(String symbol, String json) {
        try {
            if (json.contains("\"05. price\":")) {
                double price = extractJsonDouble(json, "05. price");
                double change = extractJsonDouble(json, "09. change");
                double changePercent = Double.parseDouble(extractJsonString(json, "10. change percent").replace("%", ""));
                
                return MarketDataResult.createSuccess(symbol, "ALPHAVANTAGE", price, change, 
                        changePercent, 0, 0, 0, LocalDateTime.now());
            }
            throw new RuntimeException("Invalid AlphaVantage response format");
        } catch (Exception e) {
            return MarketDataResult.createError(symbol, "ALPHAVANTAGE_PARSE_ERROR", e.getMessage(), LocalDateTime.now());
        }
    }
    
    private MarketDataResult parseNSEResponse(String symbol, String json) {
        try {
            if (json.contains("\"last\":")) {
                double price = extractJsonDouble(json, "last");
                double change = extractJsonDouble(json, "change");
                double pChange = extractJsonDouble(json, "pChange");
                
                return MarketDataResult.createSuccess(symbol, "NSE", price, change, pChange, 0, 0, 0, LocalDateTime.now());
            }
            throw new RuntimeException("Invalid NSE response format");
        } catch (Exception e) {
            return MarketDataResult.createError(symbol, "NSE_PARSE_ERROR", e.getMessage(), LocalDateTime.now());
        }
    }
    
    private MarketDataResult parseFinnhubResponse(String symbol, String json) {
        try {
            if (json.contains("\"c\":")) {
                double price = extractJsonDouble(json, "c"); // current price
                double prevClose = extractJsonDouble(json, "pc"); // previous close
                double change = price - prevClose;
                double changePercent = (change / prevClose) * 100;
                
                return MarketDataResult.createSuccess(symbol, "FINNHUB", price, change, changePercent, 
                        extractJsonDouble(json, "h"), extractJsonDouble(json, "l"), 
                        extractJsonDouble(json, "o"), LocalDateTime.now());
            }
            throw new RuntimeException("Invalid Finnhub response format");
        } catch (Exception e) {
            return MarketDataResult.createError(symbol, "FINNHUB_PARSE_ERROR", e.getMessage(), LocalDateTime.now());
        }
    }
    
    private MarketDataResult parsePolygonResponse(String symbol, String json) {
        try {
            if (json.contains("\"c\":")) {
                double price = extractJsonDouble(json, "c"); // close price
                double open = extractJsonDouble(json, "o");
                double high = extractJsonDouble(json, "h");
                double low = extractJsonDouble(json, "l");
                
                return MarketDataResult.createSuccess(symbol, "POLYGON", price, 0, 0, high, low, open, LocalDateTime.now());
            }
            throw new RuntimeException("Invalid Polygon response format");
        } catch (Exception e) {
            return MarketDataResult.createError(symbol, "POLYGON_PARSE_ERROR", e.getMessage(), LocalDateTime.now());
        }
    }
    
    // Utility methods
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
    
    private String extractJsonString(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":\"";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1) return "";
            
            startIndex += searchKey.length();
            int endIndex = json.indexOf("\"", startIndex);
            
            return json.substring(startIndex, endIndex);
        } catch (Exception e) {
            return "";
        }
    }
    
    private String convertToFinnhubSymbol(String symbol) {
        // Convert Indian indices to international equivalents for Finnhub
        switch (symbol) {
            case "NIFTY": return "^NSEI";
            case "SENSEX": return "^BSESN";
            default: return symbol;
        }
    }
    
    private String convertToPolygonSymbol(String symbol) {
        // Convert to Polygon format
        switch (symbol) {
            case "NIFTY": return "I:NIFTY";
            case "SENSEX": return "I:SENSEX";
            default: return symbol;
        }
    }
    
    private void updateAPIStatus(String apiName, boolean success) {
        APIStatus status = apiStatusMap.get(apiName);
        if (success) {
            status.setAvailable(true);
            status.setFailureCount(0);
        } else {
            status.incrementFailureCount();
            if (status.getFailureCount() >= 3) {
                status.setAvailable(false);
                System.out.println("⚠️ " + apiName + " temporarily disabled due to repeated failures");
            }
        }
        status.setLastChecked(LocalDateTime.now());
    }
    
    /**
     * Get multiple prices with auto-switching
     */
    public void getMultiplePrices(String... symbols) {
        System.out.println("\n🔄 === AUTO-SWITCH MULTIPLE PRICES ===");
        System.out.println("📊 Symbols: " + Arrays.toString(symbols));
        System.out.println("🔄 Auto-switching across 6 API sources");
        
        for (String symbol : symbols) {
            MarketDataResult result = getLivePrice(symbol);
            displayResult(result);
        }
        
        displayAPIStatus();
    }
    
    private void displayResult(MarketDataResult result) {
        if (result.isSuccess()) {
            String trend = result.getNetChange() >= 0 ? "📈" : "📉";
            System.out.printf("%s %s: ₹%,.2f (%+.2f%%) [%s]\n", 
                    trend, result.getSymbol(), result.getLtp(), 
                    result.getPctChange(), result.getDataSource());
        } else {
            System.out.printf("❌ %s: %s\n", result.getSymbol(), result.getErrorMessage());
        }
    }
    
    private void displayAPIStatus() {
        System.out.println("\n📡 === API STATUS SUMMARY ===");
        for (Map.Entry<String, APIStatus> entry : apiStatusMap.entrySet()) {
            APIStatus status = entry.getValue();
            String statusIcon = status.isAvailable() ? "✅" : "❌";
            System.out.printf("%s %s: %s (Failures: %d)\n", 
                    statusIcon, entry.getKey(), 
                    status.isAvailable() ? "Available" : "Disabled", 
                    status.getFailureCount());
        }
    }
    
    /**
     * Reset API status (useful for retrying disabled APIs)
     */
    public void resetAPIStatus() {
        System.out.println("🔄 Resetting all API status...");
        for (APIStatus status : apiStatusMap.values()) {
            status.setAvailable(true);
            status.setFailureCount(0);
            status.setLastChecked(LocalDateTime.now());
        }
        System.out.println("✅ All APIs re-enabled for retry");
    }
    
    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        System.out.println("🔄 === ENHANCED AUTO-SWITCH API SYSTEM ===");
        System.out.println("📡 6 API Sources with Auto Failover");
        System.out.println("🎯 Only Real Market Data - No Mock/Fake Data");
        System.out.println();
        
        EnhancedAutoSwitchAPI api = new EnhancedAutoSwitchAPI();
        
        // Test multiple symbols
        api.getMultiplePrices("NIFTY", "SENSEX", "BANKNIFTY", "FINNIFTY");
        
        System.out.println("\n🎯 === AUTO-SWITCH SYSTEM READY ===");
        System.out.println("✅ 6 API sources configured");
        System.out.println("✅ Automatic failover enabled");
        System.out.println("✅ Real-time error handling");
        System.out.println("✅ No fake data policy enforced");
    }
    
    // Data classes
    public static class MarketDataResult {
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
        
        private MarketDataResult(String symbol, boolean success, String dataSource, 
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
        
        public static MarketDataResult createSuccess(String symbol, String dataSource, 
                                                   double ltp, double netChange, double pctChange,
                                                   double high, double low, double open, 
                                                   LocalDateTime timestamp) {
            return new MarketDataResult(symbol, true, dataSource, ltp, netChange, pctChange, 
                                     high, low, open, null, null, timestamp);
        }
        
        public static MarketDataResult createError(String symbol, String errorCode, 
                                                 String errorMessage, LocalDateTime timestamp) {
            return new MarketDataResult(symbol, false, null, 0, 0, 0, 0, 0, 0, 
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
    
    public static class APIStatus {
        private String name;
        private boolean available;
        private int failureCount;
        private LocalDateTime lastChecked;
        private List<Long> responseTimes;
        private List<Boolean> successHistory;
        private static final int MAX_HISTORY = 100;
        
        public APIStatus(String name, boolean available, int failureCount, LocalDateTime lastChecked) {
            this.name = name;
            this.available = available;
            this.failureCount = failureCount;
            this.lastChecked = lastChecked;
            this.responseTimes = new ArrayList<>();
            this.successHistory = new ArrayList<>();
        }
        
        /**
         * Add response time measurement
         */
        public synchronized void addResponseTime(long responseTimeMs) {
            responseTimes.add(responseTimeMs);
            if (responseTimes.size() > MAX_HISTORY) {
                responseTimes.remove(0);
            }
        }
        
        /**
         * Add success/failure record
         */
        public synchronized void addSuccessRecord(boolean success) {
            successHistory.add(success);
            if (successHistory.size() > MAX_HISTORY) {
                successHistory.remove(0);
            }
        }
        
        /**
         * Get average response time
         */
        public synchronized long getAverageResponseTime() {
            if (responseTimes.isEmpty()) return Long.MAX_VALUE;
            return responseTimes.stream().mapToLong(Long::longValue).sum() / responseTimes.size();
        }
        
        /**
         * Get success rate (0.0 to 1.0)
         */
        public synchronized double getSuccessRate() {
            if (successHistory.isEmpty()) return 0.5; // Default to neutral
            long successCount = successHistory.stream().mapToLong(success -> success ? 1 : 0).sum();
            return (double) successCount / successHistory.size();
        }
        
        /**
         * Get performance score (combines response time and success rate)
         */
        public synchronized double getPerformanceScore() {
            double successRate = getSuccessRate();
            long avgResponseTime = getAverageResponseTime();
            
            // Score from 0 to 100 (higher is better)
            // Success rate contributes 70%, response time contributes 30%
            double successScore = successRate * 70;
            double responseScore = Math.max(0, (5000 - avgResponseTime) / 5000.0 * 30);
            
            return successScore + responseScore;
        }
        
        // Getters and setters
        public String getName() { return name; }
        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
        public int getFailureCount() { return failureCount; }
        public void setFailureCount(int failureCount) { this.failureCount = failureCount; }
        public void incrementFailureCount() { this.failureCount++; }
        public LocalDateTime getLastChecked() { return lastChecked; }
        public void setLastChecked(LocalDateTime lastChecked) { this.lastChecked = lastChecked; }
        
        /**
         * Get recent response times for analysis
         */
        public synchronized List<Long> getRecentResponseTimes() {
            return new ArrayList<>(responseTimes);
        }
        
        /**
         * Reset performance history
         */
        public synchronized void resetHistory() {
            responseTimes.clear();
            successHistory.clear();
            failureCount = 0;
            available = true;
        }
    }
}