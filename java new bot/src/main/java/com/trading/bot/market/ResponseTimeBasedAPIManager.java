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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Response Time Based API Manager
 * Priority: Upstox (Primary) → Upstox Secondary → Response Time Based Failover
 * Features:
 * 1. Upstox as primary API with dual endpoints
 * 2. Response time monitoring for all APIs
 * 3. Dynamic failover based on response times
 * 4. Health monitoring and recovery
 * 5. Configuration management
 */
public class ResponseTimeBasedAPIManager {
    
    // API Configuration
    private static final String UPSTOX_API_KEY = "768a303b-80f1-46d6-af16-f847f9341213";
    private static final String UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTEwYjVjNjAwMGE4YzY0YWM5OGZjYzgiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MjcwMjc5MCwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYyNzI1NjAwfQ.DtaAeJFxSiIYyh8ZC9BV8vo8UczBu6DY8u3aWOsSdMY";
    
    // API Endpoints with priorities
    private final Map<String, APIEndpoint> apiEndpoints = new LinkedHashMap<>();
    private final Map<String, ResponseTimeStats> responseTimeStats = new HashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final HttpClient httpClient;
    
    // Configuration
    private static final long MAX_RESPONSE_TIME_MS = 5000; // 5 seconds
    private static final int MAX_RETRY_COUNT = 3;
    private static final int HEALTH_CHECK_INTERVAL_MINUTES = 5;
    
    public ResponseTimeBasedAPIManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        initializeAPIEndpoints();
        initializeResponseTimeMonitoring();
        System.out.println("🚀 Response Time Based API Manager Initialized");
        System.out.println("📈 Primary: Upstox | Failover: Response Time Based");
    }
    
    /**
     * Initialize API endpoints with Upstox as primary
     */
    private void initializeAPIEndpoints() {
        // Primary Upstox endpoint
        apiEndpoints.put("UPSTOX_PRIMARY", new APIEndpoint(
            "UPSTOX_PRIMARY", 
            "https://api.upstox.com/v2", 
            1, 
            true,
            Duration.ofSeconds(5),
            getUpstoxHeaders()
        ));
        
        // Secondary Upstox endpoint (backup)
        apiEndpoints.put("UPSTOX_SECONDARY", new APIEndpoint(
            "UPSTOX_SECONDARY", 
            "https://api-v2.upstox.com", 
            2, 
            true,
            Duration.ofSeconds(6),
            getUpstoxHeaders()
        ));
        
        // Yahoo Finance (fast and reliable)
        apiEndpoints.put("YAHOO_FINANCE", new APIEndpoint(
            "YAHOO_FINANCE", 
            "https://query1.finance.yahoo.com/v8/finance/chart", 
            3, 
            true,
            Duration.ofSeconds(8),
            getYahooHeaders()
        ));
        
        // Alpha Vantage
        apiEndpoints.put("ALPHA_VANTAGE", new APIEndpoint(
            "ALPHA_VANTAGE", 
            "https://www.alphavantage.co/query", 
            4, 
            true,
            Duration.ofSeconds(10),
            getDefaultHeaders()
        ));
        
        // NSE Direct
        apiEndpoints.put("NSE_DIRECT", new APIEndpoint(
            "NSE_DIRECT", 
            "https://www.nseindia.com/api", 
            5, 
            true,
            Duration.ofSeconds(12),
            getNSEHeaders()
        ));
        
        // Finnhub
        apiEndpoints.put("FINNHUB", new APIEndpoint(
            "FINNHUB", 
            "https://finnhub.io/api/v1", 
            6, 
            true,
            Duration.ofSeconds(15),
            getDefaultHeaders()
        ));
        
        System.out.println("✅ Initialized 6 API endpoints with Upstox as primary");
    }
    
    /**
     * Initialize response time monitoring for all APIs
     */
    private void initializeResponseTimeMonitoring() {
        for (String apiName : apiEndpoints.keySet()) {
            responseTimeStats.put(apiName, new ResponseTimeStats(apiName));
        }
        
        // Start background health monitoring
        startHealthMonitoring();
    }
    
    /**
     * Get market data with response time based failover
     */
    public APIResult getMarketData(String symbol) {
        System.out.println("\n🎯 === RESPONSE TIME BASED API FETCH ===");
        System.out.println("📊 Symbol: " + symbol);
        System.out.println("🕐 Time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
        
        // Sort APIs by current performance (response time + reliability)
        List<APIEndpoint> sortedAPIs = getSortedAPIsByPerformance();
        
        for (APIEndpoint api : sortedAPIs) {
            if (!api.isEnabled()) {
                System.out.println("⏭️ Skipping " + api.getName() + " (disabled)");
                continue;
            }
            
            System.out.println("🔄 Trying " + api.getName() + " (Priority: " + api.getPriority() + ")");
            
            long startTime = System.currentTimeMillis();
            APIResult result = fetchFromAPI(api, symbol);
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Update response time stats
            updateResponseTimeStats(api.getName(), responseTime, result.isSuccess());
            
            if (result.isSuccess()) {
                System.out.printf("✅ SUCCESS: %s responded in %dms\n", api.getName(), responseTime);
                return result;
            } else {
                System.out.printf("❌ FAILED: %s failed in %dms - %s\n", 
                    api.getName(), responseTime, result.getErrorMessage());
            }
        }
        
        // All APIs failed
        System.out.println("🚨 CRITICAL: All APIs failed for " + symbol);
        return APIResult.createError(symbol, "ALL_APIS_FAILED", 
            "All API sources failed", LocalDateTime.now());
    }
    
    /**
     * Get APIs sorted by current performance
     */
    private List<APIEndpoint> getSortedAPIsByPerformance() {
        List<APIEndpoint> sortedAPIs = new ArrayList<>(apiEndpoints.values());
        
        sortedAPIs.sort((api1, api2) -> {
            ResponseTimeStats stats1 = responseTimeStats.get(api1.getName());
            ResponseTimeStats stats2 = responseTimeStats.get(api2.getName());
            
            // Primary criteria: Upstox gets priority
            if (api1.getName().contains("UPSTOX") && !api2.getName().contains("UPSTOX")) {
                return -1;
            }
            if (!api1.getName().contains("UPSTOX") && api2.getName().contains("UPSTOX")) {
                return 1;
            }
            
            // Secondary criteria: Success rate
            double successRate1 = stats1.getSuccessRate();
            double successRate2 = stats2.getSuccessRate();
            if (Math.abs(successRate1 - successRate2) > 0.1) {
                return Double.compare(successRate2, successRate1);
            }
            
            // Tertiary criteria: Average response time
            long avgTime1 = stats1.getAverageResponseTime();
            long avgTime2 = stats2.getAverageResponseTime();
            return Long.compare(avgTime1, avgTime2);
        });
        
        return sortedAPIs;
    }
    
    /**
     * Fetch data from specific API endpoint
     */
    private APIResult fetchFromAPI(APIEndpoint api, String symbol) {
        try {
            switch (api.getName()) {
                case "UPSTOX_PRIMARY":
                case "UPSTOX_SECONDARY":
                    return fetchFromUpstox(api, symbol);
                case "YAHOO_FINANCE":
                    return fetchFromYahoo(api, symbol);
                case "ALPHA_VANTAGE":
                    return fetchFromAlphaVantage(api, symbol);
                case "NSE_DIRECT":
                    return fetchFromNSE(api, symbol);
                case "FINNHUB":
                    return fetchFromFinnhub(api, symbol);
                default:
                    return APIResult.createError(symbol, "UNKNOWN_API", 
                        "Unknown API: " + api.getName(), LocalDateTime.now());
            }
        } catch (Exception e) {
            return APIResult.createError(symbol, api.getName() + "_ERROR", 
                "Exception: " + e.getMessage(), LocalDateTime.now());
        }
    }
    
    /**
     * Fetch from Upstox API
     */
    private APIResult fetchFromUpstox(APIEndpoint api, String symbol) throws Exception {
        String instrumentKey = getUpstoxInstrument(symbol);
        if (instrumentKey == null) {
            throw new RuntimeException("Symbol not supported in Upstox: " + symbol);
        }
        
        String url = api.getBaseUrl() + "/market-quote/ltp?symbol=" + instrumentKey;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .headers(api.getHeaders())
                .timeout(api.getTimeout())
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return parseUpstoxResponse(symbol, response.body(), api.getName());
        } else {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }
    
    /**
     * Fetch from Yahoo Finance
     */
    private APIResult fetchFromYahoo(APIEndpoint api, String symbol) throws Exception {
        String yahooSymbol = getYahooSymbol(symbol);
        if (yahooSymbol == null) {
            throw new RuntimeException("Symbol not supported in Yahoo: " + symbol);
        }
        
        String url = api.getBaseUrl() + "/" + yahooSymbol + "?interval=1m&range=1d";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .headers(api.getHeaders())
                .timeout(api.getTimeout())
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return parseYahooResponse(symbol, response.body(), api.getName());
        } else {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }
    
    // Additional API implementations would go here...
    private APIResult fetchFromAlphaVantage(APIEndpoint api, String symbol) throws Exception {
        // Implementation for Alpha Vantage
        throw new RuntimeException("Alpha Vantage implementation pending");
    }
    
    private APIResult fetchFromNSE(APIEndpoint api, String symbol) throws Exception {
        // Implementation for NSE Direct
        throw new RuntimeException("NSE Direct implementation pending");
    }
    
    private APIResult fetchFromFinnhub(APIEndpoint api, String symbol) throws Exception {
        // Implementation for Finnhub
        throw new RuntimeException("Finnhub implementation pending");
    }
    
    /**
     * Update response time statistics
     */
    private void updateResponseTimeStats(String apiName, long responseTime, boolean success) {
        ResponseTimeStats stats = responseTimeStats.get(apiName);
        stats.addResponseTime(responseTime, success);
        
        // Disable API if consistently slow or failing
        APIEndpoint api = apiEndpoints.get(apiName);
        if (stats.getAverageResponseTime() > MAX_RESPONSE_TIME_MS || 
            stats.getSuccessRate() < 0.5) {
            api.setEnabled(false);
            System.out.println("⚠️ Temporarily disabling " + apiName + " due to poor performance");
        }
    }
    
    /**
     * Start background health monitoring
     */
    private void startHealthMonitoring() {
        executorService.scheduleAtFixedRate(() -> {
            System.out.println("\n🏥 === HEALTH CHECK ===");
            for (APIEndpoint api : apiEndpoints.values()) {
                if (!api.isEnabled()) {
                    // Try to re-enable disabled APIs
                    testAPIHealth(api);
                }
            }
            displayPerformanceStats();
        }, HEALTH_CHECK_INTERVAL_MINUTES, HEALTH_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }
    
    /**
     * Test individual API health
     */
    private void testAPIHealth(APIEndpoint api) {
        try {
            long startTime = System.currentTimeMillis();
            APIResult result = fetchFromAPI(api, "NIFTY");
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (result.isSuccess() && responseTime < MAX_RESPONSE_TIME_MS) {
                api.setEnabled(true);
                System.out.println("✅ Re-enabled " + api.getName());
            }
        } catch (Exception e) {
            System.out.println("❌ " + api.getName() + " still failing: " + e.getMessage());
        }
    }
    
    /**
     * Display current performance statistics
     */
    public void displayPerformanceStats() {
        System.out.println("\n📊 === API PERFORMANCE STATS ===");
        
        List<APIEndpoint> sortedAPIs = getSortedAPIsByPerformance();
        
        System.out.printf("%-20s %-10s %-15s %-15s %-10s\n", 
            "API", "Status", "Avg Response", "Success Rate", "Priority");
        System.out.println("=" .repeat(80));
        
        for (APIEndpoint api : sortedAPIs) {
            ResponseTimeStats stats = responseTimeStats.get(api.getName());
            String status = api.isEnabled() ? "✅ Active" : "❌ Disabled";
            String avgResponse = stats.getAverageResponseTime() + "ms";
            String successRate = String.format("%.1f%%", stats.getSuccessRate() * 100);
            
            System.out.printf("%-20s %-10s %-15s %-15s %-10d\n", 
                api.getName(), status, avgResponse, successRate, api.getPriority());
        }
    }
    
    // Helper methods for symbol mapping
    private String getUpstoxInstrument(String symbol) {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("NIFTY", "NSE_INDEX%7CNifty%2050");
        mapping.put("SENSEX", "BSE_INDEX%7CSENSEX");
        mapping.put("BANKNIFTY", "NSE_INDEX%7CNifty%20Bank");
        mapping.put("FINNIFTY", "NSE_INDEX%7CNifty%20Fin%20Services");
        return mapping.get(symbol);
    }
    
    private String getYahooSymbol(String symbol) {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("NIFTY", "^NSEI");
        mapping.put("SENSEX", "^BSESN");
        mapping.put("BANKNIFTY", "^NSEBANK");
        mapping.put("FINNIFTY", "^CNXFIN");
        return mapping.get(symbol);
    }
    
    // Header configurations
    private String[] getUpstoxHeaders() {
        return new String[]{
            "Authorization", "Bearer " + UPSTOX_ACCESS_TOKEN,
            "Accept", "application/json",
            "User-Agent", "TradingBot/1.0"
        };
    }
    
    private String[] getYahooHeaders() {
        return new String[]{
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Accept", "application/json"
        };
    }
    
    private String[] getNSEHeaders() {
        return new String[]{
            "User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36",
            "Accept", "application/json",
            "Accept-Language", "en-US,en;q=0.9"
        };
    }
    
    private String[] getDefaultHeaders() {
        return new String[]{
            "Accept", "application/json",
            "User-Agent", "TradingBot/1.0"
        };
    }
    
    // Response parsing methods
    private APIResult parseUpstoxResponse(String symbol, String json, String source) {
        try {
            if (json.contains("\"last_price\":")) {
                double price = extractJsonDouble(json, "last_price");
                return APIResult.createSuccess(symbol, source, price, 0, 0, 
                    0, 0, price, LocalDateTime.now());
            }
            throw new RuntimeException("Invalid Upstox response format");
        } catch (Exception e) {
            return APIResult.createError(symbol, "UPSTOX_PARSE_ERROR", 
                e.getMessage(), LocalDateTime.now());
        }
    }
    
    private APIResult parseYahooResponse(String symbol, String json, String source) {
        try {
            if (json.contains("\"regularMarketPrice\":")) {
                double price = extractJsonDouble(json, "regularMarketPrice");
                double open = extractJsonDouble(json, "regularMarketOpen");
                double high = extractJsonDouble(json, "regularMarketDayHigh");
                double low = extractJsonDouble(json, "regularMarketDayLow");
                double prevClose = extractJsonDouble(json, "regularMarketPreviousClose");
                
                return APIResult.createSuccess(symbol, source, price, price - prevClose, 
                    ((price - prevClose) / prevClose) * 100, high, low, open, LocalDateTime.now());
            }
            throw new RuntimeException("Invalid Yahoo response format");
        } catch (Exception e) {
            return APIResult.createError(symbol, "YAHOO_PARSE_ERROR", 
                e.getMessage(), LocalDateTime.now());
        }
    }
    
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
     * Main method for testing
     */
    public static void main(String[] args) {
        System.out.println("🚀 === RESPONSE TIME BASED API MANAGER ===");
        System.out.println("📈 Upstox Primary | Response Time Optimization");
        System.out.println("⚡ Dynamic Failover Based on Performance");
        
        ResponseTimeBasedAPIManager manager = new ResponseTimeBasedAPIManager();
        
        // Test multiple requests to see failover in action
        String[] symbols = {"NIFTY", "SENSEX", "BANKNIFTY"};
        
        for (String symbol : symbols) {
            APIResult result = manager.getMarketData(symbol);
            if (result.isSuccess()) {
                System.out.printf("✅ %s: ₹%,.2f [%s]\n", 
                    result.getSymbol(), result.getLtp(), result.getDataSource());
            } else {
                System.out.printf("❌ %s: %s\n", 
                    result.getSymbol(), result.getErrorMessage());
            }
        }
        
        manager.displayPerformanceStats();
    }
    
    // Data classes
    public static class APIEndpoint {
        private final String name;
        private final String baseUrl;
        private final int priority;
        private boolean enabled;
        private final Duration timeout;
        private final String[] headers;
        
        public APIEndpoint(String name, String baseUrl, int priority, 
                          boolean enabled, Duration timeout, String[] headers) {
            this.name = name;
            this.baseUrl = baseUrl;
            this.priority = priority;
            this.enabled = enabled;
            this.timeout = timeout;
            this.headers = headers;
        }
        
        // Getters and setters
        public String getName() { return name; }
        public String getBaseUrl() { return baseUrl; }
        public int getPriority() { return priority; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getTimeout() { return timeout; }
        public String[] getHeaders() { return headers; }
    }
    
    public static class ResponseTimeStats {
        private final String apiName;
        private final List<Long> responseTimes = new ArrayList<>();
        private final List<Boolean> successResults = new ArrayList<>();
        private static final int MAX_HISTORY = 100;
        
        public ResponseTimeStats(String apiName) {
            this.apiName = apiName;
        }
        
        public synchronized void addResponseTime(long responseTime, boolean success) {
            responseTimes.add(responseTime);
            successResults.add(success);
            
            // Keep only recent history
            if (responseTimes.size() > MAX_HISTORY) {
                responseTimes.remove(0);
                successResults.remove(0);
            }
        }
        
        public synchronized long getAverageResponseTime() {
            if (responseTimes.isEmpty()) return Long.MAX_VALUE;
            return responseTimes.stream().mapToLong(Long::longValue).sum() / responseTimes.size();
        }
        
        public synchronized double getSuccessRate() {
            if (successResults.isEmpty()) return 0.0;
            long successCount = successResults.stream().mapToLong(success -> success ? 1 : 0).sum();
            return (double) successCount / successResults.size();
        }
        
        public String getApiName() { return apiName; }
    }
    
    public static class APIResult {
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
        
        private APIResult(String symbol, boolean success, String dataSource, 
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
        
        public static APIResult createSuccess(String symbol, String dataSource, 
                                            double ltp, double netChange, double pctChange,
                                            double high, double low, double open, 
                                            LocalDateTime timestamp) {
            return new APIResult(symbol, true, dataSource, ltp, netChange, pctChange, 
                               high, low, open, null, null, timestamp);
        }
        
        public static APIResult createError(String symbol, String errorCode, 
                                          String errorMessage, LocalDateTime timestamp) {
            return new APIResult(symbol, false, null, 0, 0, 0, 0, 0, 0, 
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