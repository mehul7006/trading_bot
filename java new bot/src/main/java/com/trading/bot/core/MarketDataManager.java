import java.util.*;
import java.time.*;

public class MarketDataManager {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();    private final Map<String, double[]> priceCache;
    private final Map<String, double[]> volumeCache;
    private final double[] defaultStrikes;
    
    public MarketDataManager() {
        this.priceCache = new HashMap<>();
        this.volumeCache = new HashMap<>();
        this.defaultStrikes = generateDefaultStrikes();
        initializeCaches();
    }
    
    private void initializeCaches() {
        // Initialize with some sample data for testing
        priceCache.put("NIFTY", generatePriceData(realData.getRealPrice("NIFTY"), 50));
        priceCache.put("BANKNIFTY", generatePriceData(43500, 100));
        volumeCache.put("NIFTY", generateVolumeData(1000000));
        volumeCache.put("BANKNIFTY", generateVolumeData(500000));
    }
    
    private double[] generateDefaultStrikes() {
        double[] strikes = new double[21];
        double baseStrike = 19000;
        for (int i = 0; i < strikes.length; i++) {
            strikes[i] = baseStrike + (i * 100);
        }
        return strikes;
    }
    
    private double[] generatePriceData(double basePrice, double variation) {
        double[] prices = new double[100];
        Random random = new Random();
        prices[0] = basePrice;
        for (int i = 1; i < prices.length; i++) {
            prices[i] = prices[i-1] + (random.nextDouble() * 2 - 1) * variation;
        }
        return prices;
    }
    
    private double[] generateVolumeData(double baseVolume) {
        double[] volumes = new double[100];
        Random random = new Random();
        for (int i = 0; i < volumes.length; i++) {
            volumes[i] = baseVolume * (0.5 + random.nextDouble());
        }
        return volumes;
    }
    
    public double getCurrentPrice(String symbol) {
        double[] prices = priceCache.get(symbol);
        return prices != null ? prices[prices.length - 1] : 0.0;
    }
    
    public double[] getClosePrices(String symbol, int lookback) {
        double[] prices = priceCache.get(symbol);
        if (prices == null || lookback > prices.length) {
            return new double[0];
        }
        int start = prices.length - lookback;
        return Arrays.copyOfRange(prices, start, prices.length);
    }
    
    public double getVolume(String symbol) {
        double[] volumes = volumeCache.get(symbol);
        return volumes != null ? volumes[volumes.length - 1] : 0.0;
    }
    
    public double[] getVolumeHistory(String symbol, int lookback) {
        double[] volumes = volumeCache.get(symbol);
        if (volumes == null || lookback > volumes.length) {
            return new double[0];
        }
        int start = volumes.length - lookback;
        return Arrays.copyOfRange(volumes, start, volumes.length);
    }
    
    public double[] getHighPrices(String symbol, int lookback) {
        return getClosePrices(symbol, lookback);  // Simplified for testing
    }
    
    public double[] getLowPrices(String symbol, int lookback) {
        return getClosePrices(symbol, lookback);  // Simplified for testing
    }
    
    public double[] getAvailableStrikes() {
        return defaultStrikes.clone();
    }
    
    public double getOptionPrice(String symbol, double strike, String optionType) {
        // Simplified option pricing for testing
        double spotPrice = getCurrentPrice(symbol);
        double timePremium = 50.0;
        
        if (optionType.equals("CE")) {
            return Math.max(0, spotPrice - strike) + timePremium;
        } else {
            return Math.max(0, strike - spotPrice) + timePremium;
        }
    }
}