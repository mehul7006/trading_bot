import java.time.*;
import java.util.*;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import com.trading.bot.market.MarketData;
import com.trading.bot.market.RealTimeDataCollector;

/**
 * ML-Enhanced Market Analysis System
 * Uses real market data for machine learning predictions
 */
public class MLMarketAnalyzer {
    public enum TrendDirection {
        UP,
        DOWN,
        SIDEWAYS
    }

    public static class TechnicalIndicators {
        private final double rsi;
        private final double macd;
        private final double slowEma;
        private final double fastEma;
        private final double atr;
        private final double obv;

        public TechnicalIndicators(double rsi, double macd, double slowEma, 
                                 double fastEma, double atr, double obv) {
            this.rsi = rsi;
            this.macd = macd;
            this.slowEma = slowEma;
            this.fastEma = fastEma;
            this.atr = atr;
            this.obv = obv;
        }

        public double getRsi() {
            return rsi;
        }

        public double getMacd() {
            return macd;
        }

        public double getSlowEma() {
            return slowEma;
        }

        public double getFastEma() {
            return fastEma;
        }

        public double getAtr() {
            return atr;
        }

        public double getObv() {
            return obv;
        }
    }

    private final Map<String, double[]> featureVectors;
    private final Map<String, SimpleRegression> priceRegressions;
    private final Map<String, DescriptiveStatistics> volatilityStats;
    private final RealTimeDataCollector dataCollector;
    
    // ML Parameters
    private static final int TRAINING_DAYS = 60;
    private static final int MIN_CONFIDENCE_SCORE = 85;
    private static final double MIN_PREDICTION_STRENGTH = 0.75;
    
    public MLMarketAnalyzer() {
        this.featureVectors = new HashMap<>();
        this.priceRegressions = new HashMap<>();
        this.volatilityStats = new HashMap<>();
        this.dataCollector = new RealTimeDataCollector();
        System.out.println("ML Market Analyzer Initialized");
    }
    
    public MarketPrediction analyzeMarket(String symbol) {
        // 1. Collect real-time market data
        MarketData currentData = dataCollector.getRealTimeData(symbol);
        if (!isValidMarketData(currentData)) {
            return null; // Skip if data is not reliable
        }
        
        // 2. Generate feature vector
        double[] features = extractFeatures(currentData);
        featureVectors.put(symbol, features);
        
        // 3. Calculate technical indicators
        TechnicalIndicators indicators = calculateIndicators(symbol, currentData);
        
        // 4. Perform regression analysis
        RegressionResult regression = calculateRegressionResult(currentData);
        
        // 5. Calculate market sentiment
        MarketSentiment sentiment = calculateMarketSentiment(currentData, indicators, regression);
        
        // 6. Determine trend direction
        TrendDirection trend = determineTrend(currentData, indicators, regression, sentiment);
        
        // 7. Calculate signals
        double priceSignal = calculatePriceSignal(currentData, indicators, regression);
        double volumeSignal = calculateVolumeSignal(currentData, indicators);
        double momentumSignal = calculateMomentumSignal(currentData, indicators, sentiment);
        
        // 8. Calculate confidence
        double confidence = calculateConfidence(
            priceSignal, volumeSignal, momentumSignal,
            regression, sentiment, currentData
        );
        
        // 9. Generate analysis
        String analysis = generateAnalysis(trend, confidence, indicators, regression, sentiment);
        
        return new MarketPrediction(priceSignal, volumeSignal, momentumSignal, 
                                  trend, confidence, analysis);
        
        // 7. Generate ML-based prediction
        return generatePrediction(symbol, features, indicators, regression, volPattern, sentiment);
    }
    
    private boolean isValidMarketData(MarketData data) {
        return data != null && 
               data.getPrice() > 0 && 
               data.getVolume() > 0 && 
               !data.isMocked();
    }
    
    private double[] extractFeatures(MarketData data) {
        return new double[] {
            data.getPrice(),
            data.getVolume(),
            data.getVwap(),
            data.getVolatility(),
            calculateMomentum(data),
            calculateRSI(data),
            calculateMACD(data)
        };
    }
    
    private TechnicalIndicators calculateIndicators(String symbol, MarketData data) {
        double[] prices = data.getPriceHistory();
        double[] volumes = data.getVolumeHistory();
        
        return new TechnicalIndicators(
            calculateEMA(prices, 20),
            calculateEMA(prices, 50),
            calculateBollingerBands(prices),
            calculateVolumeProfile(volumes),
            calculateSupportResistance(prices)
        );
    }
    
    private RegressionResult performRegression(String symbol, MarketData data) {
        SimpleRegression regression = priceRegressions.computeIfAbsent(symbol, k -> new SimpleRegression());
        double[] prices = data.getPriceHistory();
        
        // Add latest data points
        for (int i = 0; i < prices.length; i++) {
            regression.addData(i, prices[i]);
        }
        
        return new RegressionResult(
            regression.getSlope(),
            regression.getRSquare(),
            regression.predict(prices.length + 1)
        );
    }
    
    private VolatilityPattern analyzeVolatility(String symbol, MarketData data) {
        DescriptiveStatistics stats = volatilityStats.computeIfAbsent(symbol, k -> new DescriptiveStatistics());
        stats.addValue(data.getVolatility());
        
        return new VolatilityPattern(
            stats.getMean(),
            stats.getStandardDeviation(),
            classifyVolatilityRegime(stats)
        );
    }
    
    private MarketSentiment analyzeSentiment(MarketData data) {
        double priceStrength = calculatePriceStrength(data);
        double volumeStrength = calculateVolumeStrength(data);
        double momentumStrength = calculateMomentumStrength(data);
        
        return new MarketSentiment(
            priceStrength,
            volumeStrength,
            momentumStrength,
            (priceStrength + volumeStrength + momentumStrength) / 3.0
        );
    }
    
    private MarketPrediction generatePrediction(String symbol,
                                              double[] features,
                                              TechnicalIndicators indicators,
                                              RegressionResult regression,
                                              VolatilityPattern volPattern,
                                              MarketSentiment sentiment) {
                                                  
        // Calculate prediction confidence
        double confidence = calculateConfidence(indicators, regression, volPattern, sentiment);
        if (confidence < MIN_CONFIDENCE_SCORE) {
            return null; // Skip if confidence is too low
        }
        
        // Determine trend direction
        TrendDirection trend = determineTrend(indicators, regression, sentiment);
        
        // Calculate price targets
        double currentPrice = features[0];
        double target1 = calculateTarget1(currentPrice, trend, volPattern);
        double target2 = calculateTarget2(currentPrice, trend, volPattern);
        double stopLoss = calculateStopLoss(currentPrice, trend, volPattern);
        
        return new MarketPrediction(
            symbol,
            trend,
            confidence,
            currentPrice,
            target1,
            target2,
            stopLoss,
            LocalDateTime.now()
        );
    }
    
    // Helper classes
    public static class MarketPrediction {
        public final String symbol;
        public final TrendDirection trend;
        public final double confidence;
        public final double currentPrice;
        public final double target1;
        public final double target2;
        public final double stopLoss;
        public final LocalDateTime timestamp;
        
        public MarketPrediction(String symbol, TrendDirection trend,
                              double confidence, double currentPrice,
                              double target1, double target2,
                              double stopLoss, LocalDateTime timestamp) {
            this.symbol = symbol;
            this.trend = trend;
            this.confidence = confidence;
            this.currentPrice = currentPrice;
            this.target1 = target1;
            this.target2 = target2;
            this.stopLoss = stopLoss;
            this.timestamp = timestamp;
        }
        
        @Override
        public String toString() {
            return String.format("""
                ML Market Prediction:
                Symbol: %s
                Trend: %s
                Confidence: %.2f%%
                Current Price: %.2f
                Target 1: %.2f
                Target 2: %.2f
                Stop Loss: %.2f
                Time: %s
                """,
                symbol, trend, confidence, currentPrice,
                target1, target2, stopLoss, timestamp);
        }
    }
    
    private static class TechnicalIndicators {
        final double ema20;
        final double ema50;
        final double[] bollingerBands;
        final double[] volumeProfile;
        final double[] supportResistance;
        
        TechnicalIndicators(double ema20, double ema50,
                          double[] bollingerBands,
                          double[] volumeProfile,
                          double[] supportResistance) {
            this.ema20 = ema20;
            this.ema50 = ema50;
            this.bollingerBands = bollingerBands;
            this.volumeProfile = volumeProfile;
            this.supportResistance = supportResistance;
        }
    }
    
    private static class RegressionResult {
        final double slope;
        final double rSquare;
        final double prediction;
        
        RegressionResult(double slope, double rSquare, double prediction) {
            this.slope = slope;
            this.rSquare = rSquare;
            this.prediction = prediction;
        }
    }
    
    private static class VolatilityPattern {
        final double mean;
        final double stdDev;
        final VolatilityRegime regime;
        
        VolatilityPattern(double mean, double stdDev, VolatilityRegime regime) {
            this.mean = mean;
            this.stdDev = stdDev;
            this.regime = regime;
        }
    }
    
    private static class MarketSentiment {
        final double priceStrength;
        final double volumeStrength;
        final double momentumStrength;
        final double overallSentiment;
        
        MarketSentiment(double priceStrength, double volumeStrength,
                       double momentumStrength, double overallSentiment) {
            this.priceStrength = priceStrength;
            this.volumeStrength = volumeStrength;
            this.momentumStrength = momentumStrength;
            this.overallSentiment = overallSentiment;
        }
    }
    
    public enum TrendDirection {
        UP, DOWN, SIDEWAYS
    }
    
    private enum VolatilityRegime {
        LOW, MEDIUM, HIGH, EXTREME
    }
    
    // Helper methods for calculations
    private double calculateMomentum(MarketData data) {
        double[] prices = data.getPriceHistory();
        return (prices[prices.length - 1] / prices[0] - 1) * 100;
    }
    
    private double calculateRSI(MarketData data) {
        double[] prices = data.getPriceHistory();
        double gains = 0, losses = 0;
        
        for (int i = 1; i < prices.length; i++) {
            double change = prices[i] - prices[i-1];
            if (change > 0) gains += change;
            else losses -= change;
        }
        
        if (losses == 0) return 100;
        double rs = gains / losses;
        return 100 - (100 / (1 + rs));
    }
    
    private double calculateMACD(MarketData data) {
        double[] prices = data.getPriceHistory();
        double ema12 = calculateEMA(prices, 12);
        double ema26 = calculateEMA(prices, 26);
        return ema12 - ema26;
    }
    
    private double calculateEMA(double[] prices, int period) {
        double multiplier = 2.0 / (period + 1.0);
        double[] ema = new double[prices.length];
        ema[0] = prices[0];
        
        for (int i = 1; i < prices.length; i++) {
            ema[i] = (prices[i] - ema[i-1]) * multiplier + ema[i-1];
        }
        
        return ema[ema.length - 1];
    }
    
    private double[] calculateBollingerBands(double[] prices) {
        double sma = Arrays.stream(prices).average().orElse(0.0);
        double stdDev = Math.sqrt(Arrays.stream(prices)
            .map(p -> Math.pow(p - sma, 2))
            .average().orElse(0.0));
            
        return new double[] {
            sma + (2 * stdDev),  // Upper band
            sma,                  // Middle band
            sma - (2 * stdDev)   // Lower band
        };
    }
    
    private double[] calculateVolumeProfile(double[] volumes) {
        double maxVol = Arrays.stream(volumes).max().orElse(0.0);
        double minVol = Arrays.stream(volumes).min().orElse(0.0);
        double avgVol = Arrays.stream(volumes).average().orElse(0.0);
        
        return new double[] {maxVol, avgVol, minVol};
    }
    
    private double[] calculateSupportResistance(double[] prices) {
        double max = Arrays.stream(prices).max().orElse(0.0);
        double min = Arrays.stream(prices).min().orElse(0.0);
        double mid = (max + min) / 2;
        
        return new double[] {max, mid, min};
    }
    
    private VolatilityRegime classifyVolatilityRegime(DescriptiveStatistics stats) {
        double currentVol = stats.getStandardDeviation();
        double meanVol = stats.getMean();
        double ratio = currentVol / meanVol;
        
        if (ratio <= 0.75) return VolatilityRegime.LOW;
        if (ratio <= 1.25) return VolatilityRegime.MEDIUM;
        if (ratio <= 2.00) return VolatilityRegime.HIGH;
        return VolatilityRegime.EXTREME;
    }
    
    private double calculateConfidence(TechnicalIndicators indicators,
                                     RegressionResult regression,
                                     VolatilityPattern volPattern,
                                     MarketSentiment sentiment) {
        double confidence = 50.0; // Base confidence
        
        // Add regression confidence (up to 15%)
        confidence += regression.rSquare * 15;
        
        // Add trend confidence (up to 15%)
        if (indicators.ema20 > indicators.ema50) {
            confidence += Math.min(15, (indicators.ema20/indicators.ema50 - 1) * 100);
        }
        
        // Add volatility confidence (up to 10%)
        switch (volPattern.regime) {
            case LOW -> confidence += 10;
            case MEDIUM -> confidence += 7;
            case HIGH -> confidence += 5;
            case EXTREME -> confidence += 0;
        }
        
        // Add sentiment confidence (up to 10%)
        confidence += sentiment.overallSentiment * 10;
        
        return Math.min(confidence, 100.0);
    }
    
    private TrendDirection determineTrend(TechnicalIndicators indicators,
                                        RegressionResult regression,
                                        MarketSentiment sentiment) {
        // Check regression slope
        if (Math.abs(regression.slope) < 0.0001) {
            return TrendDirection.SIDEWAYS;
        }
        
        // Combine multiple factors for trend determination
        boolean priceAboveEMA = indicators.ema20 > indicators.ema50;
        boolean positiveSlope = regression.slope > 0;
        boolean positiveSentiment = sentiment.overallSentiment > 0.6;
        
        if (priceAboveEMA && positiveSlope && positiveSentiment) {
            return TrendDirection.UP;
        } else if (!priceAboveEMA && !positiveSlope && !positiveSentiment) {
            return TrendDirection.DOWN;
        }
        
        return TrendDirection.SIDEWAYS;
    }
    
    private double calculateTarget1(double currentPrice,
                                  TrendDirection trend,
                                  VolatilityPattern volPattern) {
        double volatilityFactor = switch(volPattern.regime) {
            case LOW -> 1.5;
            case MEDIUM -> 2.0;
            case HIGH -> 2.5;
            case EXTREME -> 3.0;
        };
        
        return switch(trend) {
            case UP -> currentPrice * (1 + volPattern.stdDev * volatilityFactor);
            case DOWN -> currentPrice * (1 - volPattern.stdDev * volatilityFactor);
            case SIDEWAYS -> currentPrice;
        };
    }
    
    private double calculateTarget2(double currentPrice,
                                  TrendDirection trend,
                                  VolatilityPattern volPattern) {
        return calculateTarget1(currentPrice, trend, volPattern) * 
               (trend == TrendDirection.SIDEWAYS ? 1.0 : 1.5);
    }
    
    private double calculateStopLoss(double currentPrice,
                                   TrendDirection trend,
                                   VolatilityPattern volPattern) {
        double volatilityFactor = switch(volPattern.regime) {
            case LOW -> 0.5;
            case MEDIUM -> 0.75;
            case HIGH -> 1.0;
            case EXTREME -> 1.25;
        };
        
        return switch(trend) {
            case UP -> currentPrice * (1 - volPattern.stdDev * volatilityFactor);
            case DOWN -> currentPrice * (1 + volPattern.stdDev * volatilityFactor);
            case SIDEWAYS -> currentPrice * 0.99; // Tight stop for sideways
        };
    }
}