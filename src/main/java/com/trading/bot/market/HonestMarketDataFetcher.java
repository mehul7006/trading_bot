package com.trading.bot.market;

import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.util.MarketHours;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.File;
import java.io.IOException;
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
    private final Map<String, List<SimpleMarketData>> dataCache; // Cache for 5-min resampled data
    
    // Upstox API configuration
    private static String UPSTOX_ACCESS_TOKEN = System.getenv("UPSTOX_ACCESS_TOKEN") != null ? System.getenv("UPSTOX_ACCESS_TOKEN") : "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTc2ZmU1Y2M0YjUzNzUwMGYwMWVkOGYiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2OTQwNjA0NCwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzY5NDY0ODAwfQ.ojGoQS7fTKK4rtOYmBa1qhS7RgbaGNQWRCGpFSKVN10";
    
    // Token File Path
    private static final String TOKEN_FILE_PATH = "upstox_token.txt";
    private static final String CACHE_DIR = "market_data_cache";
    
    // CORRECT Symbol mappings (tested and verified)
    private static final Map<String, String> SYMBOL_MAPPING = Map.of(
        "NIFTY50", "NSE_INDEX|Nifty 50",
        "SENSEX", "BSE_INDEX|SENSEX", 
        "BANKNIFTY", "NSE_INDEX|Nifty Bank"
    );
    
    public HonestMarketDataFetcher() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.lastValidPrices = new HashMap<>();
        this.lastValidTimes = new HashMap<>();
        this.dataCache = new java.util.concurrent.ConcurrentHashMap<>();
        
        // Create cache directory
        new File(CACHE_DIR).mkdirs();
        
        loadTokenFromFile();
    }
    
    /**
     * Update Access Token dynamically
     */
    public static void setAccessToken(String token) {
        if (token != null && !token.trim().isEmpty()) {
            UPSTOX_ACCESS_TOKEN = token.trim();
            saveTokenToFile(token.trim());
            // System.out.println("✅ Upstox Access Token updated successfully"); // Silenced
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
                    // System.out.println("✅ Loaded Upstox Access Token from file");
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Could not load token from file: " + e.getMessage());
        }
    }
    
    private static void saveTokenToFile(String token) {
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(TOKEN_FILE_PATH), token);
            // System.out.println("✅ Saved Upstox Access Token to file");
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
     * Get REAL market data resampled to 5-Minute Candles (Better Stability)
     * Includes Smart Caching & Persistence: Fetches 120 days history once, saves to disk, then appends live data with rollover.
     */
    public List<SimpleMarketData> getRealMarketData5Min(String symbol) throws Exception {
        System.out.println("🔍 Fetching HONEST market data (5-Min Resampled) for: " + symbol);
        
        // 1. Check Memory Cache
        List<SimpleMarketData> cachedData = dataCache.get(symbol);
        
        // 2. If Memory Cache Empty, Try Disk Cache
        if (cachedData == null) {
            cachedData = loadCacheFromDisk(symbol);
            if (cachedData != null && !cachedData.isEmpty()) {
                System.out.println("📂 Loaded " + cachedData.size() + " candles from disk cache for " + symbol);
                dataCache.put(symbol, cachedData);
            }
        }
        
        // 3. If Still Empty -> First Time Fetch (120 Days)
        if (cachedData == null || cachedData.isEmpty()) {
            System.out.println("📥 Initializing 120-Day History for " + symbol + " (First Run)...");
            List<SimpleMarketData> deepHistory = fetch120DaysData(symbol);
            if (deepHistory.isEmpty()) {
                throw new Exception("❌ Failed to fetch initial 120-day history for " + symbol);
            }
            // Resample to 5-min
            cachedData = resampleTo5Minute(deepHistory);
            dataCache.put(symbol, cachedData);
            saveCacheToDisk(symbol, cachedData); // SAVE TO DISK
            return cachedData;
        } 
        
        // 4. Update Existing Cache (Smart Update & Rollover)
        System.out.println("🔄 Updating live data for " + symbol + "...");
        
        // Find last timestamp in cache
        SimpleMarketData lastCandle = cachedData.get(cachedData.size() - 1);
        
        // Fetch data from last cached timestamp to NOW
        // We look back 1 day just to be safe and ensure overlap/no gaps
        int lookbackDays = 1;
        if (lastCandle.timestamp.isBefore(LocalDateTime.now().minusDays(1))) {
            lookbackDays = (int) java.time.temporal.ChronoUnit.DAYS.between(lastCandle.timestamp.toLocalDate(), LocalDate.now()) + 1;
        }
        
        List<SimpleMarketData> recentData = fetchRecentHistory(symbol, lookbackDays);
        
        if (!recentData.isEmpty()) {
             List<SimpleMarketData> recent5Min = resampleTo5Minute(recentData);
             
             // Smart Merge: Use Map to handle duplicates by timestamp
             java.util.TreeMap<LocalDateTime, SimpleMarketData> mergedMap = new java.util.TreeMap<>();
             for (SimpleMarketData c : cachedData) mergedMap.put(c.timestamp, c);
             for (SimpleMarketData c : recent5Min) mergedMap.put(c.timestamp, c); // Overwrite with newer data
             
             // Convert back to list
             cachedData = new ArrayList<>(mergedMap.values());
             
             // ROLLOVER: Keep only last 120 days
             LocalDateTime cutoff = LocalDateTime.now().minusDays(120);
             cachedData.removeIf(c -> c.timestamp.isBefore(cutoff));
             
             // Update Caches
             dataCache.put(symbol, cachedData);
             saveCacheToDisk(symbol, cachedData); // SAVE TO DISK
             System.out.println("✅ Cache updated & saved. Size: " + cachedData.size() + " candles.");
        } else {
             System.out.println("⚠️ No new data fetched. Using cached data.");
        }
        
        // Check Data Freshness
        if (!cachedData.isEmpty()) {
            SimpleMarketData latest = cachedData.get(cachedData.size() - 1);
            LocalDateTime now = LocalDateTime.now();
            boolean isFresh = latest.timestamp.isAfter(now.minusMinutes(15)) || latest.timestamp.toLocalDate().isEqual(now.toLocalDate());
            
            if (!isFresh) {
                 System.out.println("⚠️ 5-Min Data might be stale! Latest data from: " + latest.timestamp);
            }
            lastValidPrices.put(symbol, latest.price);
            lastValidTimes.put(symbol, LocalDateTime.now());
        }
        
        return cachedData;
    }

    private void saveCacheToDisk(String symbol, List<SimpleMarketData> data) {
        try {
            File file = new File(CACHE_DIR, symbol + "_120days.json");
            objectMapper.writeValue(file, data);
            // System.out.println("💾 Saved cache to disk: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ Failed to save cache to disk: " + e.getMessage());
        }
    }

    private List<SimpleMarketData> loadCacheFromDisk(String symbol) {
        try {
            File file = new File(CACHE_DIR, symbol + "_120days.json");
            if (file.exists()) {
                return objectMapper.readValue(file, new TypeReference<List<SimpleMarketData>>(){});
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to load cache from disk: " + e.getMessage());
        }
        return null;
    }

    /**
     * Fetch 120 Days of 1-Minute Data (Chunked)
     */
    private List<SimpleMarketData> fetch120DaysData(String symbol) {
        List<SimpleMarketData> allData = new ArrayList<>();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(120); 
        
        // Fetch in 5-day chunks to avoid limits
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(5)) {
            LocalDate chunkEnd = date.plusDays(4);
            if (chunkEnd.isAfter(end)) chunkEnd = end;
            
            List<SimpleMarketData> chunk = fetchHistoricalCandles(symbol, "1minute", date.toString(), chunkEnd.toString());
            if (chunk != null) {
                allData.addAll(chunk);
            }
            
            // Rate limit prevention
            try { Thread.sleep(200); } catch (Exception e) {} 
        }
        // Sort just in case
        allData.sort(Comparator.comparing(d -> d.timestamp));
        return allData;
    }
    
    /**
     * Fetch Recent History (e.g. last N days)
     */
    private List<SimpleMarketData> fetchRecentHistory(String symbol, int days) {
        String toDate = LocalDate.now().toString();
        String fromDate = LocalDate.now().minusDays(days).toString();
        return fetchHistoricalCandles(symbol, "1minute", fromDate, toDate);
    }

    private List<SimpleMarketData> resampleTo5Minute(List<SimpleMarketData> data1Min) {
        List<SimpleMarketData> data5Min = new ArrayList<>();
        if (data1Min.isEmpty()) return data5Min;

        SimpleMarketData current5Min = null;
        LocalDateTime candleStartTime = null;

        for (SimpleMarketData candle : data1Min) {
            // Round down to nearest 5 minutes
            int minute = candle.timestamp.getMinute();
            int minuteMod5 = minute % 5;
            LocalDateTime periodStart = candle.timestamp.minusMinutes(minuteMod5).withSecond(0).withNano(0);

            if (current5Min == null || !periodStart.equals(candleStartTime)) {
                // New 5-min candle
                if (current5Min != null) {
                    data5Min.add(current5Min);
                }
                candleStartTime = periodStart;
                current5Min = new SimpleMarketData(candle.symbol, candle.price, candle.open, candle.high, candle.low, candle.volume, periodStart);
            } else {
                // Update existing 5-min candle
                current5Min = new SimpleMarketData(
                    current5Min.symbol, 
                    candle.price, // Close updates to latest
                    current5Min.open, 
                    Math.max(current5Min.high, candle.high), 
                    Math.min(current5Min.low, candle.low), 
                    current5Min.volume + candle.volume, 
                    current5Min.timestamp
                );
            }
        }
        // Add last one
        if (current5Min != null) {
            data5Min.add(current5Min);
        }
        return data5Min;
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
