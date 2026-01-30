package com.trading.bot.market;

import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.util.MarketHours;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * HONEST Market Data Fetcher - NO FAKE PRICES, ONLY REAL DATA OR ERRORS
 * If API fails, shows honest error messages instead of fake prices
 */
public class HonestMarketDataFetcher {
    
    private final HttpClient httpClient;
    private final Map<String, Double> lastValidPrices;
    private final Map<String, LocalDateTime> lastValidTimes;
    
    // Upstox API configuration
    private static String UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTc2ZmU1Y2M0YjUzNzUwMGYwMWVkOGYiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2OTQwNjA0NCwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzY5NDY0ODAwfQ.ojGoQS7fTKK4rtOYmBa1qhS7RgbaGNQWRCGpFSKVN10";
    
    // Token File Path
    private static final String TOKEN_FILE_PATH = "upstox_token.txt";
    
    // CORRECT Symbol mappings (tested and verified)
    private static final Map<String, String> SYMBOL_MAPPING = Map.of(
        "NIFTY50", "NSE_INDEX|Nifty 50",
        "SENSEX", "BSE_INDEX|SENSEX", 
        "BANKNIFTY", "NSE_INDEX|Nifty Bank"
    );
    
    public HonestMarketDataFetcher() {
        this.httpClient = HttpClient.newHttpClient();
        this.lastValidPrices = new HashMap<>();
        this.lastValidTimes = new HashMap<>();
        loadTokenFromFile();
    }
    
    /**
     * Update Access Token dynamically
     */
    public static void setAccessToken(String token) {
        if (token != null && !token.trim().isEmpty()) {
            UPSTOX_ACCESS_TOKEN = token.trim();
            saveTokenToFile(token.trim());
            System.out.println("✅ Upstox Access Token updated successfully");
        }
    }
    
    public static String getAccessToken() {
        return UPSTOX_ACCESS_TOKEN;
    }
    
    private void loadTokenFromFile() {
        try {
            java.io.File file = new java.io.File(TOKEN_FILE_PATH);
            if (file.exists()) {
                String token = java.nio.file.Files.readString(file.toPath()).trim();
                if (!token.isEmpty()) {
                    UPSTOX_ACCESS_TOKEN = token;
                    System.out.println("✅ Loaded Upstox Access Token from file");
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Could not load token from file: " + e.getMessage());
        }
    }
    
    private static void saveTokenToFile(String token) {
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(TOKEN_FILE_PATH), token);
            System.out.println("✅ Saved Upstox Access Token to file");
        } catch (Exception e) {
            System.err.println("⚠️ Could not save token to file: " + e.getMessage());
        }
    }
    
    /**
     * Get REAL market data - NO FAKE PRICES
     */
    public List<SimpleMarketData> getRealMarketData(String symbol) throws Exception {
        System.out.println("🔍 Fetching HONEST market data for: " + symbol);
        
        // 0. Check Market Hours
        if (!MarketHours.isMarketOpen()) {
            String error = "⛔ " + MarketHours.getMarketStatusMessage();
            System.err.println(error);
            throw new Exception(error);
        }

        // 1. Fetch historical candles (which include current price at the end)
        List<SimpleMarketData> historicalData = fetchHistoricalCandles(symbol, "1minute");
        
        if (!historicalData.isEmpty()) {
            // SUCCESS - Got real data
            SimpleMarketData latest = historicalData.get(historicalData.size() - 1);
            
            // 2. Check Data Freshness (Must be within last 15 minutes)
            LocalDateTime now = LocalDateTime.now();
            if (latest.timestamp.isBefore(now.minusMinutes(15))) {
                String error = "⚠️ Data is stale! Latest data from: " + latest.timestamp + ". Market might be closed or API delayed.";
                System.err.println(error);
                throw new Exception(error);
            }
            
            lastValidPrices.put(symbol, latest.price);
            lastValidTimes.put(symbol, LocalDateTime.now());
            
            return historicalData;
        } else {
            // FAILED - Show honest error
            String error = "❌ Failed to get real historical data for " + symbol + " from Upstox API";
            System.err.println(error);
            throw new Exception(error);
        }
    }

    /**
     * Fetch REAL historical candles from Upstox API with custom dates
     */
    public List<SimpleMarketData> fetchHistoricalCandles(String symbol, String interval, String fromDate, String toDate) {
        List<SimpleMarketData> data = new ArrayList<>();
        try {
            String instrumentKey = SYMBOL_MAPPING.get(symbol);
            if (instrumentKey == null) {
                System.err.println("❌ No instrument mapping for symbol: " + symbol);
                return data;
            }
            
            // Encode instrument key - Upstox requires %20 for spaces, not +
            String encodedKey = URLEncoder.encode(instrumentKey, StandardCharsets.UTF_8).replace("+", "%20");
            
            // URL: /v2/historical-candle/{instrumentKey}/{interval}/{to_date}/{from_date}
            String url = String.format("https://api.upstox.com/v2/historical-candle/%s/%s/%s/%s",
                    encodedKey, interval, toDate, fromDate);
            
            System.out.println("🌐 Historical API URL: " + url);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + UPSTOX_ACCESS_TOKEN)
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                data = parseHistoricalCandles(response.body(), symbol);
                System.out.printf("✅ Fetched %d historical candles for %s%n", data.size(), symbol);
            } else {
                System.err.printf("❌ Upstox Historical API error for %s: HTTP %d%n", symbol, response.statusCode());
                System.err.printf("❌ Error response: %s%n", response.body());
            }
            
        } catch (Exception e) {
            System.err.printf("❌ Exception fetching historical data for %s: %s%n", symbol, e.getMessage());
            e.printStackTrace();
        }
        return data;
    }

    /**
     * Fetch REAL historical candles from Upstox API (Default 5 days)
     */
    private List<SimpleMarketData> fetchHistoricalCandles(String symbol, String interval) {
        String toDate = java.time.LocalDate.now().toString();
        String fromDate = java.time.LocalDate.now().minusDays(5).toString();
        return fetchHistoricalCandles(symbol, interval, fromDate, toDate);
    }
    
    /**
     * Parse historical candles from Upstox JSON response
     */
    private List<SimpleMarketData> parseHistoricalCandles(String responseBody, String symbol) {
        List<SimpleMarketData> candles = new ArrayList<>();
        try {
            // Expected format: {"status":"success","data":{"candles":[["timestamp",open,high,low,close,vol,oi],...]}}
            int dataIndex = responseBody.indexOf("\"candles\":[[");
            if (dataIndex == -1) return candles;
            
            String candlesData = responseBody.substring(dataIndex + 11); // Skip "candles":[
            // Remove closing brackets and braces roughly
            if (candlesData.endsWith("]}}")) candlesData = candlesData.substring(0, candlesData.length() - 3);
            
            // Split by "],["
            String[] rows = candlesData.split("\\],\\[");
            
            for (String row : rows) {
                // Clean up brackets
                row = row.replace("[", "").replace("]", "").replace("\"", "");
                String[] parts = row.split(",");
                
                if (parts.length >= 6) {
                    // Format: timestamp, open, high, low, close, volume, oi
                    String timestampStr = parts[0];
                    double open = Double.parseDouble(parts[1]);
                    double high = Double.parseDouble(parts[2]);
                    double low = Double.parseDouble(parts[3]);
                    double close = Double.parseDouble(parts[4]);
                    long volume = (long) Double.parseDouble(parts[5]);
                    
                    // Parse timestamp
                    LocalDateTime timestamp = LocalDateTime.parse(timestampStr, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    
                    candles.add(new SimpleMarketData(symbol, close, open, high, low, volume, timestamp));
                }
            }
            
            // Sort by timestamp ascending
            candles.sort(Comparator.comparing(d -> d.timestamp));
            
        } catch (Exception e) {
            System.err.printf("❌ Error parsing historical candles: %s%n", e.getMessage());
        }
        return candles;
    }
    
    /**
     * Helper to get single real price
     */
    private double fetchRealPriceFromUpstox(String symbol) throws Exception {
        // We reuse the historical candle fetcher to get the latest price
        List<SimpleMarketData> data = fetchHistoricalCandles(symbol, "1minute");
        if (data != null && !data.isEmpty()) {
            return data.get(data.size() - 1).price;
        }
        return 0.0;
    }

    /**
     * Get HONEST market snapshot - NO FAKE PRICES
     */
    public Map<String, Double> getHonestMarketSnapshot() throws Exception {
        Map<String, Double> snapshot = new HashMap<>();
        List<String> errors = new ArrayList<>();
        
        for (String symbol : Arrays.asList("NIFTY50", "SENSEX", "BANKNIFTY")) {
            try {
                double price = fetchRealPriceFromUpstox(symbol);
                if (price > 0) {
                    snapshot.put(symbol, price);
                    System.out.printf("✅ REAL price for %s: ₹%.2f%n", symbol, price);
                } else {
                    errors.add(symbol);
                    System.err.printf("❌ Failed to get REAL price for %s%n", symbol);
                }
                
                // Delay between API calls
                Thread.sleep(500);
                
            } catch (Exception e) {
                errors.add(symbol);
                System.err.printf("❌ Error getting price for %s: %s%n", symbol, e.getMessage());
            }
        }
        
        if (snapshot.isEmpty()) {
            throw new Exception("❌ FAILED TO GET ANY REAL PRICES - All APIs failed");
        }
        
        if (!errors.isEmpty()) {
            System.out.printf("⚠️ Failed to get prices for: %s%n", String.join(", ", errors));
        }
        
        return snapshot;
    }
    
    /**
     * Test API connectivity with HONEST results
     */
    public void testHonestConnectivity() {
        System.out.println("🧪 Testing HONEST market data connectivity...");
        
        try {
            Map<String, Double> prices = getHonestMarketSnapshot();
            
            System.out.println("✅ HONEST CONNECTIVITY TEST RESULTS:");
            for (Map.Entry<String, Double> entry : prices.entrySet()) {
                System.out.printf("📊 %s REAL PRICE: ₹%.2f%n", entry.getKey(), entry.getValue());
            }
            
        } catch (Exception e) {
            System.err.printf("❌ HONEST CONNECTIVITY TEST FAILED: %s%n", e.getMessage());
        }
    }
    
    /**
     * Get last valid price with timestamp (for error display)
     */
    public String getLastValidPriceInfo(String symbol) {
        Double price = lastValidPrices.get(symbol);
        LocalDateTime time = lastValidTimes.get(symbol);
        
        if (price != null && time != null) {
            return String.format("Last valid price: ₹%.2f at %s", price, 
                                time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
        } else {
            return "No valid price data available";
        }
    }
}
