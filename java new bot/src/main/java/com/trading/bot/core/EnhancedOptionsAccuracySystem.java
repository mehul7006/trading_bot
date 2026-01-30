import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import java.time.format.DateTimeFormatter;

/**
 * ENHANCED OPTIONS ACCURACY SYSTEM
 * Focused on high-probability options trades with:
 * - Multi-timeframe analysis
 * - Support/Resistance validation
 * - Volume profile confirmation
 * - Options Greeks analysis
 * - Real-time target adjustment
 */
public class EnhancedOptionsAccuracySystem {
    
    // Enhanced confidence thresholds for higher accuracy
    private static final double MIN_CONFIDENCE_THRESHOLD = 85.0;  // Increased from 80%
    private static final double STRONG_TREND_THRESHOLD = 90.0;    // Increased from 85%
    private static final double VOLUME_CONFIRMATION_THRESHOLD = 1.5; // 150% of average volume
    
    // Optimized target management
    private static final double QUICK_TARGET_PERCENT = 20.0;  // Increased to 20% quick target
    private static final double FULL_TARGET_PERCENT = 35.0;   // Increased to 35% full target
    private static final double STOP_LOSS_PERCENT = 10.0;     // Reduced to 10% for better risk:reward
    
    // Multi-timeframe confirmation thresholds
    private static final double SHORT_TERM_ALIGNMENT = 0.85;  // 85% alignment required
    private static final double MEDIUM_TERM_ALIGNMENT = 0.80; // 80% alignment required
    
    // Options analysis components
    private final ImpliedVolatilityAnalyzer ivAnalyzer;
    private final OptionsGreeksCalculator greeksCalc;
    private final MarketDataManager marketData;
    
    // Performance tracking
    private final Map<String, Double> successRates = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> returnsHistory = new ConcurrentHashMap<>();
    
    public EnhancedOptionsAccuracySystem() {
        this.ivAnalyzer = new ImpliedVolatilityAnalyzer();
        this.greeksCalc = new OptionsGreeksCalculator();
        this.marketData = new RealMarketDataProvider();
        System.out.println("Enhanced Options Accuracy System Initialized");
        System.out.println("Using real market data only");
        System.out.println("Minimum confidence: " + MIN_CONFIDENCE_THRESHOLD + "%");
        System.out.println("Target 1: " + QUICK_TARGET_PERCENT + "% | Target 2: " + FULL_TARGET_PERCENT + "%");
    }
    
    /**
     * Generate high-accuracy options call with optimized targets
     */
    public OptionsSignal generateOptionsCall(String symbol) {
        // 1. Multi-timeframe trend analysis
        TrendAnalysis trend = analyzeTrend(symbol);
        if (!trend.isConfirmed || trend.strength < STRONG_TREND_THRESHOLD) {
            return null; // Skip if trend is not strong enough
        }
        
        // 2. Volume confirmation
        VolumeProfile volume = analyzeVolume(symbol);
        if (!volume.isValid || volume.ratio < VOLUME_CONFIRMATION_THRESHOLD) {
            return null; // Skip if volume is insufficient
        }
        
        // 3. Support/Resistance validation
        List<PriceLevel> levels = findKeyLevels(symbol);
        if (!validatePriceLevels(levels, marketData.getCurrentPrice(symbol))) {
            return null; // Skip if price levels are not reliable
        }
        
        // 4. Options strike selection
        double currentPrice = marketData.getCurrentPrice(symbol);
        double optimalStrike = selectOptimalStrike(currentPrice, trend.direction);
        
        // 5. IV Analysis for timing
        IVAnalysis ivProfile = ivAnalyzer.analyzeIV(symbol, optimalStrike);
        if (!ivProfile.isFavorable()) {
            return null; // Skip if IV conditions are not optimal
        }
        
        // 6. Calculate targets and stop loss
        OptionPricing pricing = calculateOptionPricing(symbol, optimalStrike, trend.direction);
        
        // 7. Final confidence calculation
        double confidence = calculateConfidence(trend, volume, levels, ivProfile);
        if (confidence < MIN_CONFIDENCE_THRESHOLD) {
            return null; // Skip if final confidence is too low
        }
        
        return new OptionsSignal(
            symbol,
            trend.direction == TrendDirection.UP ? "CE" : "PE",
            optimalStrike,
            pricing.entryPrice,
            pricing.target1,
            pricing.target2,
            pricing.stopLoss,
            confidence
        );
    }
    
    private static class TrendAnalysis {
        final boolean isConfirmed;
        final double strength;
        final TrendDirection direction;
        
        TrendAnalysis(boolean isConfirmed, double strength, TrendDirection direction) {
            this.isConfirmed = isConfirmed;
            this.strength = strength;
            this.direction = direction;
        }
    }
    
    private static class VolumeProfile {
        final boolean isValid;
        final double ratio;
        final double averageVolume;
        
        VolumeProfile(boolean isValid, double ratio, double averageVolume) {
            this.isValid = isValid;
            this.ratio = ratio;
            this.averageVolume = averageVolume;
        }
    }
    
    private static class OptionPricing {
        final double entryPrice;
        final double target1;
        final double target2;
        final double stopLoss;
        
        OptionPricing(double entryPrice, double target1, double target2, double stopLoss) {
            this.entryPrice = entryPrice;
            this.target1 = target1;
            this.target2 = target2;
            this.stopLoss = stopLoss;
        }
    }
    
    private enum TrendDirection {
    
    /**
     * Analyze trend across multiple timeframes
     */
    private TrendAnalysis analyzeTrend(String symbol) {
        // Get price data for multiple timeframes
        double[] prices5min = marketData.getClosePrices(symbol, 60);  // 5-minute data
        double[] prices15min = marketData.getClosePrices(symbol, 40); // 15-minute data
        double[] prices1hour = marketData.getClosePrices(symbol, 24); // 1-hour data
        
        // Calculate EMAs for each timeframe
        double[] ema5min = calculateEMA(prices5min, 20);
        double[] ema15min = calculateEMA(prices15min, 20);
        double[] ema1hour = calculateEMA(prices1hour, 20);
        
        // Determine trend direction
        boolean shortTermUp = isUptrend(ema5min);
        boolean mediumTermUp = isUptrend(ema15min);
        boolean longTermUp = isUptrend(ema1hour);
        
        // Calculate trend strength and direction
        double strength = calculateTrendStrength(shortTermUp, mediumTermUp, longTermUp);
        TrendDirection direction = determineTrendDirection(shortTermUp, mediumTermUp, longTermUp);
        
        return new TrendAnalysis(strength >= SHORT_TERM_ALIGNMENT, strength, direction);
    }
    
    /**
     * Analyze volume profile
     */
    private VolumeProfile analyzeVolume(String symbol) {
        double currentVolume = marketData.getVolume(symbol);
        double[] historicalVolume = marketData.getVolumeHistory(symbol, 20);
        double avgVolume = Arrays.stream(historicalVolume).average().orElse(0.0);
        
        double volumeRatio = currentVolume / avgVolume;
        boolean isValid = currentVolume > avgVolume * VOLUME_CONFIRMATION_THRESHOLD;
        
        return new VolumeProfile(isValid, volumeRatio, avgVolume);
    }
    
    /**
     * Find key price levels
     */
    private List<PriceLevel> findKeyLevels(String symbol) {
        List<PriceLevel> levels = new ArrayList<>();
        double[] highs = marketData.getHighPrices(symbol, 20);
        double[] lows = marketData.getLowPrices(symbol, 20);
        double currentPrice = marketData.getCurrentPrice(symbol);
        
        // Calculate pivot points
        double[] pivots = calculatePivotPoints(
            highs[highs.length - 1],
            lows[lows.length - 1],
            currentPrice
        );
        
        // Add support and resistance levels
        for (double pivot : pivots) {
            levels.add(new PriceLevel(pivot, PriceLevelType.PIVOT));
        }
        
        return levels;
    }
    
    /**
     * Helper method to calculate EMAs
     */
    private double[] calculateEMA(double[] prices, int period) {
        double[] ema = new double[prices.length];
        double multiplier = 2.0 / (period + 1.0);
        
        ema[0] = prices[0];
        for (int i = 1; i < prices.length; i++) {
            ema[i] = (prices[i] - ema[i-1]) * multiplier + ema[i-1];
        }
        
        return ema;
    }
    
    /**
     * Calculate trend strength across timeframes
     */
    private double calculateTrendStrength(boolean shortTerm, boolean mediumTerm, boolean longTerm) {
        int aligned = 0;
        if (shortTerm) aligned++;
        if (mediumTerm) aligned++;
        if (longTerm) aligned++;
        return (aligned / 3.0) * 100.0;
    }
    
    /**
     * Determine overall trend direction
     */
    private TrendDirection determineTrendDirection(boolean shortTerm, boolean mediumTerm, boolean longTerm) {
        if (shortTerm && mediumTerm && longTerm) {
            return TrendDirection.UP;
        } else if (!shortTerm && !mediumTerm && !longTerm) {
            return TrendDirection.DOWN;
        }
        return TrendDirection.UNCLEAR;
    }
    
    /**
     * Check if EMA indicates uptrend
     */
    private boolean isUptrend(double[] ema) {
        int len = ema.length;
        return ema[len - 1] > ema[len - 2] && ema[len - 2] > ema[len - 3];
    }
    
    /**
     * Calculate pivot points
     */
    private double[] calculatePivotPoints(double high, double low, double close) {
        double pivot = (high + low + close) / 3;
        double r1 = 2 * pivot - low;
        double s1 = 2 * pivot - high;
        double r2 = pivot + (high - low);
        double s2 = pivot - (high - low);
        
        return new double[]{s2, s1, pivot, r1, r2};
    }
    
    private void printInitializationMessage() {
        System.out.println("Enhanced Options Accuracy System");
        System.out.println("==============================");
        System.out.println("Multi-timeframe Analysis: Active");
        System.out.println("Support/Resistance Validation: Enabled");
        System.out.println("Volume Profile Confirmation: Enabled");
        System.out.println("Options Greeks Analysis: Active");
        System.out.println("Real-time Target Management: Enabled");
        System.out.println("Minimum Confidence: " + MIN_CONFIDENCE_THRESHOLD + "%");
        System.out.println("Quick Target: " + QUICK_TARGET_PERCENT + "%");
        System.out.println("Full Target: " + FULL_TARGET_PERCENT + "%");
        System.out.println("Stop Loss: " + STOP_LOSS_PERCENT + "%");
    }
        
        public EnhancedOptionsAccuracySystem() {
        this.ivAnalyzer = new ImpliedVolatilityAnalyzer();
        this.greeksCalc = new OptionsGreeksCalculator();
        this.marketData = new RealMarketDataProvider();
        printInitializationMessage();
    }
    
    /**
     * Generate high-probability options call
     */
    public OptionsCall generateOptionsCall(String symbol, double currentPrice, double iv) {
        // 1. Analyze market conditions
        MarketAnalysis analysis = analyzeMarketConditions(symbol, currentPrice);
        if (!analysis.isSuitable) {
            return null;
        }
        
        // 2. Validate with volume profile
        VolumeAnalysis volumeAnalysis = analyzeVolumeProfile(symbol);
        if (!volumeAnalysis.isConfirmed) {
            return null;
        }
        
        // 3. Calculate optimal strike selection
        List<Integer> strikes = calculateOptimalStrikes(currentPrice, analysis.trend);
        
        // 4. Analyze options Greeks
        // Calculate time to expiry (30 days standard)
        double timeToExpiry = 30.0 / 365.0;
        
        OptionsGreeks greeks = greeksCalc.calculateGreeks(
            currentPrice, 
            strikes.get(0), 
            timeToExpiry,
            iv,
            analysis.trend == TrendDirection.UP ? "CE" : "PE"
        );
        
        if (!validateGreeks(greeks)) {
            return null;
        }
        
        // 5. Set targets based on analysis
        TargetLevels targets = calculateTargets(currentPrice, analysis.trend, greeks);
        
        return new OptionsCall(
            symbol,
            LocalDateTime.now(),
            analysis.trend == TrendDirection.UP ? "CE" : "PE",
            strikes.get(0),
            currentPrice,
            targets.entryPrice,
            targets.quickTarget,
            targets.fullTarget,
            targets.stopLoss,
            analysis.confidence
        );
    }
    
    /**
     * Market condition analysis
     */
    private MarketAnalysis analyzeMarketConditions(String symbol, double currentPrice) {
        double[] ema9 = calculateEMA(symbol, 9);
        double[] ema21 = calculateEMA(symbol, 21);
        double[] rsi = calculateRSI(symbol);
        
        TrendDirection trend = determineTrend(ema9, ema21, rsi);
        double confidence = calculateConfidence(ema9, ema21, rsi, currentPrice);
        
        return new MarketAnalysis(
            trend,
            confidence,
            confidence >= MIN_CONFIDENCE_THRESHOLD
        );
    }
    
    /**
     * Volume profile analysis
     */
    private VolumeAnalysis analyzeVolumeProfile(String symbol) {
        // Get real volume data
        double[] volumes = marketData.getVolumeData(symbol);
        double avgVolume = Arrays.stream(volumes).average().orElse(0.0);
        
        return new VolumeAnalysis(
            volumes[volumes.length - 1] > avgVolume * 1.2,  // 20% above average
            volumes[volumes.length - 1] / avgVolume
        );
    }
    
    /**
     * Calculate optimal strike prices
     */
    private List<Integer> calculateOptimalStrikes(double currentPrice, TrendDirection trend) {
        List<Integer> strikes = new ArrayList<>();
        int baseStrike = (int)(currentPrice / 100) * 100;
        
        if (trend == TrendDirection.UP) {
            strikes.add(baseStrike);
            strikes.add(baseStrike + 100);
            strikes.add(baseStrike + 200);
        } else {
            strikes.add(baseStrike);
            strikes.add(baseStrike - 100);
            strikes.add(baseStrike - 200);
        }
        
        return strikes;
    }
    
    /**
     * Validate options Greeks
     */
    private boolean validateGreeks(OptionsGreeks greeks) {
        return greeks.delta >= 0.4 && greeks.delta <= 0.6 &&  // Not too deep ITM/OTM
               greeks.theta > -0.5 &&                         // Not too much time decay
               greeks.vega < 0.5;                            // Not too sensitive to volatility
    }
    
    /**
     * Calculate target levels
     */
    private TargetLevels calculateTargets(double currentPrice, TrendDirection trend, OptionsGreeks greeks) {
        double entryPrice = trend == TrendDirection.UP ? 
            currentPrice * 1.002 : currentPrice * 0.998;  // 0.2% buffer
            
        double quickTarget = entryPrice * (1 + (QUICK_TARGET_PERCENT / 100.0) * (trend == TrendDirection.UP ? 1 : -1));
        double fullTarget = entryPrice * (1 + (FULL_TARGET_PERCENT / 100.0) * (trend == TrendDirection.UP ? 1 : -1));
        double stopLoss = entryPrice * (1 - (STOP_LOSS_PERCENT / 100.0) * (trend == TrendDirection.UP ? 1 : -1));
        
        return new TargetLevels(entryPrice, quickTarget, fullTarget, stopLoss);
    }
    
    // Helper classes
    private class OptionsGreeks {
        final double delta;
        final double gamma;
        final double theta;
        final double vega;
        final double rho;
        
        OptionsGreeks(double delta, double gamma, double theta, double vega, double rho) {
            this.delta = delta;
            this.gamma = gamma;
            this.theta = theta;
            this.vega = vega;
            this.rho = rho;
        }
    }
    
    private static class MarketAnalysis {
        final TrendDirection trend;
        final double confidence;
        final boolean isSuitable;
        
        MarketAnalysis(TrendDirection trend, double confidence, boolean isSuitable) {
            this.trend = trend;
            this.confidence = confidence;
            this.isSuitable = isSuitable;
        }
    }
    
    private static class VolumeAnalysis {
        final boolean isConfirmed;
        final double volumeRatio;
        
        VolumeAnalysis(boolean isConfirmed, double volumeRatio) {
            this.isConfirmed = isConfirmed;
            this.volumeRatio = volumeRatio;
        }
    }
    
    private static class TargetLevels {
        final double entryPrice;
        final double quickTarget;
        final double fullTarget;
        final double stopLoss;
        
        TargetLevels(double entry, double quick, double full, double stop) {
            this.entryPrice = entry;
            this.quickTarget = quick;
            this.fullTarget = full;
            this.stopLoss = stop;
        }
    }
    
    public static class OptionsCall {
        public final String symbol;
        public final LocalDateTime timestamp;
        public final String optionType;  // CE or PE
        public final int strikePrice;
        public final double spotPrice;
        public final double entryPrice;
        public final double quickTarget;
        public final double fullTarget;
        public final double stopLoss;
        public final double confidence;
        
        public OptionsCall(String symbol, LocalDateTime timestamp, String optionType,
                         int strikePrice, double spotPrice, double entryPrice,
                         double quickTarget, double fullTarget, double stopLoss,
                         double confidence) {
            this.symbol = symbol;
            this.timestamp = timestamp;
            this.optionType = optionType;
            this.strikePrice = strikePrice;
            this.spotPrice = spotPrice;
            this.entryPrice = entryPrice;
            this.quickTarget = quickTarget;
            this.fullTarget = fullTarget;
            this.stopLoss = stopLoss;
            this.confidence = confidence;
        }
        
        @Override
        public String toString() {
            return String.format("""
                🎯 OPTIONS CALL GENERATED:
                Symbol: %s
                Type: %s
                Strike: %d
                Spot Price: %.2f
                Entry: %.2f
                Quick Target: %.2f
                Full Target: %.2f
                Stop Loss: %.2f
                Confidence: %.1f%%
                Time: %s
                """,
                symbol, optionType, strikePrice, spotPrice,
                entryPrice, quickTarget, fullTarget, stopLoss,
                confidence, timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            );
        }
    }
    
    private enum TrendDirection {
    
    // Technical Analysis Helpers
    private double[] calculateEMA(String symbol, int period) {
        double[] prices = marketData.getPriceHistory(symbol);
        double[] ema = new double[prices.length];
        double multiplier = 2.0 / (period + 1.0);
        
        ema[0] = prices[0];
        for (int i = 1; i < prices.length; i++) {
            ema[i] = (prices[i] - ema[i-1]) * multiplier + ema[i-1];
        }
        return ema;
    }
    
    private TrendDirection determineTrend(double[] ema9, double[] ema21, double[] rsi) {
        int last = ema9.length - 1;
        
        // Strong trend conditions
        boolean strongUptrend = ema9[last] > ema21[last] && 
                              rsi[last] > 50 && 
                              ema9[last] > ema9[last-1];
                              
        boolean strongDowntrend = ema9[last] < ema21[last] && 
                                rsi[last] < 50 && 
                                ema9[last] < ema9[last-1];
        
        return strongUptrend ? TrendDirection.UP : 
               strongDowntrend ? TrendDirection.DOWN : 
               ema9[last] > ema21[last] ? TrendDirection.UP : TrendDirection.DOWN;
    }
    
    private double[] calculateRSI(String symbol) {
        double[] prices = marketData.getPriceHistory(symbol);
        double[] rsi = new double[prices.length];
        int period = 14;
        
        double avgGain = 0;
        double avgLoss = 0;
        
        // Calculate first avg gain/loss
        for (int i = 1; i < period; i++) {
            double change = prices[i] - prices[i-1];
            if (change >= 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        
        avgGain /= period;
        avgLoss /= period;
        
        // Calculate RSI
        for (int i = period; i < prices.length; i++) {
            double change = prices[i] - prices[i-1];
            double gain = Math.max(0, change);
            double loss = Math.abs(Math.min(0, change));
            
            avgGain = ((avgGain * (period-1)) + gain) / period;
            avgLoss = ((avgLoss * (period-1)) + loss) / period;
            
            double rs = avgGain / avgLoss;
            rsi[i] = 100 - (100 / (1 + rs));
        }
        
        return rsi;
    }
    
    private double calculateConfidence(double[] ema9, double[] ema21, double[] rsi, double currentPrice) {
        double trendStrength = (ema9[ema9.length-1] - ema21[ema21.length-1]) / ema21[ema21.length-1] * 100;
        double rsiConfidence = rsi[rsi.length-1] > 70 ? 100 - rsi[rsi.length-1] : 
                             rsi[rsi.length-1] < 30 ? rsi[rsi.length-1] : 50;
        
        return (Math.abs(trendStrength) * 0.6 + rsiConfidence * 0.4);
    }
}