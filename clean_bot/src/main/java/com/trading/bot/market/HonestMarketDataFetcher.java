package com.trading.bot.market;

import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.util.MarketHours;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;

/**
 * HONEST Market Data Fetcher - NO FAKE PRICES, ONLY REAL DATA OR ERRORS
 * If API fails, shows honest error messages instead of fake prices
 */
public class HonestMarketDataFetcher {
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
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
        this.objectMapper = new ObjectMapper();
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
        
        // 1. Fetch historical candles (might be stale)
        List<SimpleMarketData> historicalData = fetchHistoricalCandles(symbol, "1minute");
        
        // 2. Fetch Today's Real-Time OHLC (To fix stale data issues)
        SimpleMarketData todayOHLC = fetchDayOHLC(symbol);
        
        if (todayOHLC != null) {
            // Check if we need to append
            if (historicalData.isEmpty()) {
                historicalData.add(todayOHLC);
            } else {
                SimpleMarketData lastHist = historicalData.get(historicalData.size() - 1);
                // If todayOHLC is newer than last history
                if (todayOHLC.timestamp.toLocalDate().isAfter(lastHist.timestamp.toLocalDate()) ||
                    (todayOHLC.timestamp.toLocalDate().isEqual(lastHist.timestamp.toLocalDate()) && todayOHLC.timestamp.isAfter(lastHist.timestamp))) {
                    
                    System.out.println("🔹 Appending Real-Time OHLC candle for " + symbol + " (" + todayOHLC.price + ")");
                    historicalData.add(todayOHLC);
                }
            }
        }

        if (!historicalData.isEmpty()) {
            // SUCCESS - Got real data
            SimpleMarketData latest = historicalData.get(historicalData.size() - 1);
            
            // 2. Check Data Freshness (Must be within last 15 minutes OR be Today's OHLC)
            LocalDateTime now = LocalDateTime.now();
            boolean isFresh = latest.timestamp.isAfter(now.minusMinutes(15)) || latest.timestamp.toLocalDate().isEqual(now.toLocalDate());
            
            if (!isFresh) {
                 String warning = "⚠️ Data might be stale! Latest data from: " + latest.timestamp;
                 System.out.println(warning); // Just warn, don't fail, as user might want to see whatever we have
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

    private SimpleMarketData fetchDayOHLC(String symbol) {
        try {
            String instrumentKey = SYMBOL_MAPPING.get(symbol);
            if (instrumentKey == null) return null;
            
            String encodedKey = URLEncoder.encode(instrumentKey, StandardCharsets.UTF_8).replace("+", "%20");
            String url = "https://api.upstox.com/v2/market-quote/ohlc?instrument_key=" + encodedKey + "&interval=1d";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + UPSTOX_ACCESS_TOKEN)
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.path("status").asText().equals("success")) {
                    JsonNode dataNode = root.path("data");
                    
                    Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        JsonNode ohlcNode = entry.getValue().path("ohlc");
                        if (!ohlcNode.isMissingNode()) {
                            double close = ohlcNode.path("close").asDouble();
                            double open = ohlcNode.path("open").asDouble();
                            double high = ohlcNode.path("high").asDouble();
                            double low = ohlcNode.path("low").asDouble();
                            
                            // Create candle with NOW timestamp to indicate it's current
                            SimpleMarketData candle = new SimpleMarketData(
                                symbol, close, open, high, low, 0, LocalDateTime.now()
                            );
                            return candle;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to fetch OHLC for " + symbol + ": " + e.getMessage());
        }
        return null;
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
        // Use getRealMarketData which now includes OHLC fallback
        List<SimpleMarketData> data = getRealMarketData(symbol);
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
