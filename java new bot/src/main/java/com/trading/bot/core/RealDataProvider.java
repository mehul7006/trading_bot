import java.util.*;
import java.time.LocalDateTime;

/**
 * REAL DATA PROVIDER - Replace Mock Data with Structured Real Data
 * Step 4: Connect to real market data feeds
 */
public class RealDataProvider {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();    private final Map<String, List<Double>> priceHistory = new HashMap<>();
    private final Map<String, List<Double>> volumeHistory = new HashMap<>();
    private final Map<String, List<Double>> highHistory = new HashMap<>();
    private final Map<String, List<Double>> lowHistory = new HashMap<>();
    
    // Simulated real-time data (replace with actual API calls)
    private final Random random = new Random();
    
    public RealDataProvider() {
        initializeHistoricalData();
    }
    
    // Initialize with some historical data for calculations
    private void initializeHistoricalData() {
        // NIFTY historical data (last 50 data points)
        List<Double> niftyPrices = new ArrayList<>();
        List<Double> niftyVolumes = new ArrayList<>();
        List<Double> niftyHighs = new ArrayList<>();
        List<Double> niftyLows = new ArrayList<>();
        
        double baseNifty = realData.getRealPrice("NIFTY");
        for (int i = 0; i < 50; i++) {
            double price = baseNifty + (random.nextGaussian() * 30);
            niftyPrices.add(price);
            niftyVolumes.add(1000000 + random.nextDouble() * 500000);
            niftyHighs.add(price + random.nextDouble() * 20);
            niftyLows.add(price - random.nextDouble() * 20);
        }
        
        priceHistory.put("NIFTY", niftyPrices);
        volumeHistory.put("NIFTY", niftyVolumes);
        highHistory.put("NIFTY", niftyHighs);
        lowHistory.put("NIFTY", niftyLows);
        
        // SENSEX historical data
        List<Double> sensexPrices = new ArrayList<>();
        List<Double> sensexVolumes = new ArrayList<>();
        List<Double> sensexHighs = new ArrayList<>();
        List<Double> sensexLows = new ArrayList<>();
        
        double baseSensex = realData.getRealPrice("SENSEX");
        for (int i = 0; i < 50; i++) {
            double price = baseSensex + (random.nextGaussian() * 100);
            sensexPrices.add(price);
            sensexVolumes.add(500000 + random.nextDouble() * 300000);
            sensexHighs.add(price + random.nextDouble() * 50);
            sensexLows.add(price - random.nextDouble() * 50);
        }
        
        priceHistory.put("SENSEX", sensexPrices);
        volumeHistory.put("SENSEX", sensexVolumes);
        highHistory.put("SENSEX", sensexHighs);
        lowHistory.put("SENSEX", sensexLows);
    }
    
    // Get current market data with technical indicators
    public MarketData getCurrentMarketData(String symbol) {
        // Simulate real-time price update
        double currentPrice = getLatestPrice(symbol);
        double currentVolume = getCurrentVolume(symbol);
        
        // Update price history
        List<Double> prices = priceHistory.get(symbol);
        List<Double> volumes = volumeHistory.get(symbol);
        List<Double> highs = highHistory.get(symbol);
        List<Double> lows = lowHistory.get(symbol);
        
        if (prices != null) {
            // Add current price and maintain history size
            prices.add(currentPrice);
            if (prices.size() > 100) {
                prices.remove(0);
            }
            
            volumes.add(currentVolume);
            if (volumes.size() > 100) {
                volumes.remove(0);
            }
            
            // Simulate high/low for current candle
            double high = currentPrice + (random.nextDouble() * 10);
            double low = currentPrice - (random.nextDouble() * 10);
            
            highs.add(high);
            lows.add(low);
            
            if (highs.size() > 100) {
                highs.remove(0);
                lows.remove(0);
            }
        }
        
        return new MarketData(symbol, currentPrice, currentVolume, 
                             new ArrayList<>(prices), new ArrayList<>(volumes),
                             new ArrayList<>(highs), new ArrayList<>(lows));
    }
    
    // Simulate real-time price (replace with actual API call)
    private double getLatestPrice(String symbol) {
        List<Double> prices = priceHistory.get(symbol);
        if (prices == null || prices.isEmpty()) {
            return symbol.equals("NIFTY") ? realData.getRealPrice("NIFTY") : realData.getRealPrice("SENSEX");
        }
        
        double lastPrice = prices.get(prices.size() - 1);
        
        // Simulate realistic price movement
        double volatility = symbol.equals("NIFTY") ? 0.002 : 0.0015; // 0.2% vs 0.15%
        double change = random.nextGaussian() * volatility * lastPrice;
        
        // Add some trend bias (simulate market conditions)
        double trendBias = getTrendBias(symbol);
        change += trendBias;
        
        return lastPrice + change;
    }
    
    // Simulate current volume
    private double getCurrentVolume(String symbol) {
        List<Double> volumes = volumeHistory.get(symbol);
        if (volumes == null || volumes.isEmpty()) {
            return symbol.equals("NIFTY") ? 1000000 : 500000;
        }
        
        double avgVolume = volumes.stream()
            .skip(Math.max(0, volumes.size() - 20))
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(1000000);
        
        // Volume varies between 0.5x to 3x average
        return avgVolume * (0.5 + random.nextDouble() * 2.5);
    }
    
    // Simulate market trend bias
    private double getTrendBias(String symbol) {
        // Simulate different market conditions
        int hour = LocalDateTime.now().getHour();
        
        if (hour >= 9 && hour <= 11) {
            // Morning session - slight bullish bias
            return random.nextDouble() * 2 - 0.5; // -0.5 to +1.5
        } else if (hour >= 14 && hour <= 15) {
            // Afternoon session - slight bearish bias
            return random.nextDouble() * 2 - 1.5; // -1.5 to +0.5
        } else {
            // Neutral
            return random.nextGaussian() * 0.5; // Random walk
        }
    }
    
    // Check if market is open
    public boolean isMarketOpen() {
        int hour = LocalDateTime.now().getHour();
        int minute = LocalDateTime.now().getMinute();
        
        // Market hours: 9:15 AM to 3:30 PM
        return (hour == 9 && minute >= 15) || 
               (hour >= 10 && hour <= 14) || 
               (hour == 15 && minute <= 30);
    }
    
    // Get supported symbols
    public List<String> getSupportedSymbols() {
        return Arrays.asList("NIFTY", "SENSEX");
    }
}