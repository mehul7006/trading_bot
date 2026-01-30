import java.util.*;

/**
 * IMPLIED VOLATILITY ANALYZER - PART 1B
 * Implements IV analysis for better options trading decisions
 * Fixes volatility assessment issues in options strategy
 */
public class ImpliedVolatilityAnalyzer {
    
    // IV analysis parameters
    private static final double IV_TOLERANCE = 0.0001; // Convergence tolerance
    private static final int MAX_ITERATIONS = 100; // Maximum Newton-Raphson iterations
    private final OptionsGreeksCalculator greeksCalculator;
    
    public ImpliedVolatilityAnalyzer() {
        System.out.println("📊 IMPLIED VOLATILITY ANALYZER - PART 1B");
        System.out.println("=========================================");
        System.out.println("🎯 Implementing IV analysis for options trading");
        System.out.println("📈 Fixing volatility assessment issues");
        System.out.println("🔧 Professional volatility-based decisions");
        
        this.greeksCalculator = new OptionsGreeksCalculator();
    }
    
    /**
     * PART 1B-1: Calculate implied volatility from market price
     */
    public double calculateImpliedVolatility(double marketPrice, double spotPrice, 
                                           double strikePrice, double timeToExpiry, 
                                           String optionType) {
        
        System.out.println("📊 PART 1B-1: Calculating implied volatility for " + optionType + " option");
        
        // Initial guess for volatility
        double volatility = 0.25; // Start with 25%
        
        // Newton-Raphson method to find IV
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            OptionsGreeksCalculator.OptionsGreeks greeks = greeksCalculator.calculateGreeks(
                spotPrice, strikePrice, timeToExpiry, volatility, optionType);
            
            double priceDiff = greeks.optionPrice - marketPrice;
            
            // Check convergence
            if (Math.abs(priceDiff) < IV_TOLERANCE) {
                System.out.println("✅ IV converged: " + String.format("%.2f", volatility * 100) + 
                                  "% (iterations: " + i + ")");
                return volatility;
            }
            
            // Newton-Raphson update
            if (greeks.vega > 0) {
                volatility = volatility - (priceDiff / (greeks.vega * 100));
                volatility = Math.max(0.01, Math.min(2.0, volatility)); // Bound between 1% and 200%
            } else {
                break; // Avoid division by zero
            }
        }
        
        System.out.println("⚠️ IV did not converge, using estimate: " + String.format("%.2f", volatility * 100) + "%");
        return volatility;
    }
    
    /**
     * PART 1B-2: Analyze IV levels for trading decisions
     */
    public IVAnalysis analyzeIVLevels(double currentIV, String index, String optionType, 
                                     double spotPrice, double strikePrice) {
        
        System.out.println("🔍 PART 1B-2: Analyzing IV levels for trading decisions");
        
        // Historical IV ranges (simplified - would use real data)
        Map<String, Double[]> historicalIVRanges = getHistoricalIVRanges();
        Double[] ivRange = historicalIVRanges.get(index);
        
        if (ivRange == null) {
            ivRange = new Double[]{0.15, 0.35}; // Default range
        }
        
        double lowIV = ivRange[0];
        double highIV = ivRange[1];
        double avgIV = (lowIV + highIV) / 2;
        
        // Calculate IV percentile
        double ivPercentile = (currentIV - lowIV) / (highIV - lowIV) * 100;
        ivPercentile = Math.max(0, Math.min(100, ivPercentile));
        
        // Analyze IV level
        IVLevel ivLevel;
        String ivAssessment;
        double confidence = 50.0;
        
        if (ivPercentile > 80) {
            ivLevel = IVLevel.VERY_HIGH;
            ivAssessment = "Very high IV - options expensive, consider selling";
            confidence = 30; // Low confidence for buying expensive options
        } else if (ivPercentile > 60) {
            ivLevel = IVLevel.HIGH;
            ivAssessment = "High IV - options expensive, be cautious";
            confidence = 45;
        } else if (ivPercentile > 40) {
            ivLevel = IVLevel.NORMAL;
            ivAssessment = "Normal IV - fair option pricing";
            confidence = 70;
        } else if (ivPercentile > 20) {
            ivLevel = IVLevel.LOW;
            ivAssessment = "Low IV - options cheap, good buying opportunity";
            confidence = 85;
        } else {
            ivLevel = IVLevel.VERY_LOW;
            ivAssessment = "Very low IV - excellent buying opportunity";
            confidence = 90;
        }
        
        // Generate trading recommendations
        List<String> recommendations = generateIVRecommendations(ivLevel, optionType, currentIV, avgIV);
        
        IVAnalysis analysis = new IVAnalysis(
            currentIV, ivPercentile, ivLevel, ivAssessment, confidence, 
            recommendations, lowIV, highIV, avgIV
        );
        
        System.out.println("✅ IV analysis complete: " + ivLevel + " (" + 
                          String.format("%.1f", ivPercentile) + " percentile)");
        
        return analysis;
    }
    
    /**
     * PART 1B-3: Calculate volatility smile/skew
     */
    public VolatilitySmile calculateVolatilitySmile(double spotPrice, String index, 
                                                   double timeToExpiry, String optionType) {
        
        System.out.println("📊 PART 1B-3: Calculating volatility smile for " + index);
        
        // Generate strikes around current spot price
        List<Double> strikes = generateStrikesAroundSpot(spotPrice, index);
        Map<Double, Double> strikeIVMap = new HashMap<>();
        
        // Calculate IV for each strike (simplified - would use real market data)
        for (Double strike : strikes) {
            double moneyness = spotPrice / strike;
            double iv = calculateSmileIV(moneyness, optionType);
            strikeIVMap.put(strike, iv);
        }
        
        // Analyze smile characteristics
        double atmIV = strikeIVMap.get(findATMStrike(strikes, spotPrice));
        double skew = calculateSkew(strikeIVMap, spotPrice);
        double convexity = calculateConvexity(strikeIVMap, spotPrice);
        
        VolatilitySmile smile = new VolatilitySmile(
            strikeIVMap, atmIV, skew, convexity, 
            analyzeSmileShape(skew, convexity)
        );
        
        System.out.println("✅ Volatility smile calculated: ATM IV=" + 
                          String.format("%.2f", atmIV * 100) + "%, Skew=" + 
                          String.format("%.4f", skew));
        
        return smile;
    }
    
    /**
     * PART 1B-4: Generate IV-based trading signals
     */
    public IVTradingSignal generateIVTradingSignal(IVAnalysis ivAnalysis, VolatilitySmile smile,
                                                  String optionType, double spotPrice, 
                                                  double strikePrice) {
        
        System.out.println("🎯 PART 1B-4: Generating IV-based trading signal");
        
        double signalStrength = 0.0;
        List<String> signals = new ArrayList<>();
        
        // IV level signal
        switch (ivAnalysis.ivLevel) {
            case VERY_LOW:
                signalStrength += 0.3;
                signals.add("Very low IV - strong buy signal");
                break;
            case LOW:
                signalStrength += 0.2;
                signals.add("Low IV - buy signal");
                break;
            case NORMAL:
                signalStrength += 0.1;
                signals.add("Normal IV - neutral signal");
                break;
            case HIGH:
                signalStrength -= 0.1;
                signals.add("High IV - caution signal");
                break;
            case VERY_HIGH:
                signalStrength -= 0.2;
                signals.add("Very high IV - avoid buying");
                break;
        }
        
        // Volatility smile signal
        double moneyness = spotPrice / strikePrice;
        if (optionType.equals("CE") && moneyness > 1.0 && smile.skew < 0) {
            signalStrength += 0.1;
            signals.add("Negative skew favors ITM calls");
        } else if (optionType.equals("PE") && moneyness < 1.0 && smile.skew > 0) {
            signalStrength += 0.1;
            signals.add("Positive skew favors ITM puts");
        }
        
        // IV percentile signal
        if (ivAnalysis.ivPercentile < 25 && optionType.equals("CE")) {
            signalStrength += 0.15;
            signals.add("Low IV percentile - good for buying calls");
        } else if (ivAnalysis.ivPercentile < 25 && optionType.equals("PE")) {
            signalStrength += 0.15;
            signals.add("Low IV percentile - good for buying puts");
        }
        
        // Convert to confidence score
        double confidence = 50 + (signalStrength * 100);
        confidence = Math.max(0, Math.min(100, confidence));
        
        // Generate recommendation
        String recommendation = generateIVRecommendation(confidence, signalStrength);
        
        IVTradingSignal signal = new IVTradingSignal(
            confidence, signalStrength, recommendation, signals,
            ivAnalysis.ivLevel, smile.skew
        );
        
        System.out.println("✅ IV trading signal: " + String.format("%.1f", confidence) + 
                          "% confidence (" + recommendation + ")");
        
        return signal;
    }
    
    // Helper methods
    private Map<String, Double[]> getHistoricalIVRanges() {
        Map<String, Double[]> ranges = new HashMap<>();
        ranges.put("NIFTY", new Double[]{0.12, 0.45}); // 12% to 45% IV range
        ranges.put("SENSEX", new Double[]{0.10, 0.40}); // 10% to 40% IV range
        return ranges;
    }
    
    private List<String> generateIVRecommendations(IVLevel ivLevel, String optionType, 
                                                  double currentIV, double avgIV) {
        List<String> recommendations = new ArrayList<>();
        
        switch (ivLevel) {
            case VERY_LOW:
                recommendations.add("Excellent time to buy " + optionType + " options");
                recommendations.add("Consider longer-dated options for better value");
                recommendations.add("IV likely to increase - good entry point");
                break;
            case LOW:
                recommendations.add("Good time to buy " + optionType + " options");
                recommendations.add("Monitor for IV expansion");
                break;
            case NORMAL:
                recommendations.add("Fair pricing for " + optionType + " options");
                recommendations.add("Focus on directional accuracy");
                break;
            case HIGH:
                recommendations.add("Options are expensive - be selective");
                recommendations.add("Consider shorter timeframes");
                break;
            case VERY_HIGH:
                recommendations.add("Avoid buying options - very expensive");
                recommendations.add("Consider selling strategies if suitable");
                break;
        }
        
        return recommendations;
    }
    
    private List<Double> generateStrikesAroundSpot(double spotPrice, String index) {
        List<Double> strikes = new ArrayList<>();
        double strikeInterval = index.equals("NIFTY") ? 50.0 : 100.0;
        
        for (int i = -3; i <= 3; i++) {
            strikes.add(spotPrice + (i * strikeInterval));
        }
        
        return strikes;
    }
    
    private double calculateSmileIV(double moneyness, String optionType) {
        // Simplified volatility smile model
        double baseIV = 0.25;
        
        if (optionType.equals("CE")) {
            // Calls typically have lower IV for OTM
            return baseIV - 0.05 * Math.max(0, moneyness - 1.0);
        } else {
            // Puts typically have higher IV for OTM
            return baseIV + 0.08 * Math.max(0, 1.0 - moneyness);
        }
    }
    
    private double findATMStrike(List<Double> strikes, double spotPrice) {
        return strikes.stream()
            .min(Comparator.comparingDouble(s -> Math.abs(s - spotPrice)))
            .orElse(spotPrice);
    }
    
    private double calculateSkew(Map<Double, Double> strikeIVMap, double spotPrice) {
        // Simplified skew calculation
        double otmPutIV = strikeIVMap.entrySet().stream()
            .filter(e -> e.getKey() < spotPrice * 0.95)
            .mapToDouble(Map.Entry::getValue)
            .average().orElse(0.25);
        
        double otmCallIV = strikeIVMap.entrySet().stream()
            .filter(e -> e.getKey() > spotPrice * 1.05)
            .mapToDouble(Map.Entry::getValue)
            .average().orElse(0.25);
        
        return otmPutIV - otmCallIV;
    }
    
    private double calculateConvexity(Map<Double, Double> strikeIVMap, double spotPrice) {
        // Simplified convexity calculation
        return 0.02; // Placeholder
    }
    
    private String analyzeSmileShape(double skew, double convexity) {
        if (skew > 0.05) {
            return "Put skew - higher IV for OTM puts";
        } else if (skew < -0.05) {
            return "Call skew - higher IV for OTM calls";
        } else {
            return "Symmetric smile - balanced IV across strikes";
        }
    }
    
    private String generateIVRecommendation(double confidence, double signalStrength) {
        if (confidence >= 75) {
            return "STRONG BUY - Excellent IV conditions";
        } else if (confidence >= 65) {
            return "BUY - Good IV conditions";
        } else if (confidence >= 55) {
            return "NEUTRAL - Mixed IV signals";
        } else if (confidence >= 45) {
            return "CAUTION - Unfavorable IV conditions";
        } else {
            return "AVOID - Poor IV conditions";
        }
    }
    
    // Data classes
    public static class IVAnalysis {
        public final double currentIV;
        public final double ivPercentile;
        public final IVLevel ivLevel;
        public final String assessment;
        public final double confidence;
        public final List<String> recommendations;
        public final double lowIV;
        public final double highIV;
        public final double avgIV;
        
        public IVAnalysis(double currentIV, double ivPercentile, IVLevel ivLevel, String assessment,
                         double confidence, List<String> recommendations, double lowIV, double highIV, double avgIV) {
            this.currentIV = currentIV;
            this.ivPercentile = ivPercentile;
            this.ivLevel = ivLevel;
            this.assessment = assessment;
            this.confidence = confidence;
            this.recommendations = recommendations;
            this.lowIV = lowIV;
            this.highIV = highIV;
            this.avgIV = avgIV;
        }
    }
    
    public static class VolatilitySmile {
        public final Map<Double, Double> strikeIVMap;
        public final double atmIV;
        public final double skew;
        public final double convexity;
        public final String smileShape;
        
        public VolatilitySmile(Map<Double, Double> strikeIVMap, double atmIV, double skew,
                              double convexity, String smileShape) {
            this.strikeIVMap = strikeIVMap;
            this.atmIV = atmIV;
            this.skew = skew;
            this.convexity = convexity;
            this.smileShape = smileShape;
        }
    }
    
    public static class IVTradingSignal {
        public final double confidence;
        public final double signalStrength;
        public final String recommendation;
        public final List<String> signals;
        public final IVLevel ivLevel;
        public final double skew;
        
        public IVTradingSignal(double confidence, double signalStrength, String recommendation,
                              List<String> signals, IVLevel ivLevel, double skew) {
            this.confidence = confidence;
            this.signalStrength = signalStrength;
            this.recommendation = recommendation;
            this.signals = signals;
            this.ivLevel = ivLevel;
            this.skew = skew;
        }
    }
    
    public enum IVLevel {
        VERY_LOW, LOW, NORMAL, HIGH, VERY_HIGH
    }
    
    public static void main(String[] args) {
        System.out.println("📊 TESTING IMPLIED VOLATILITY ANALYZER - PART 1B");
        System.out.println("================================================");
        
        ImpliedVolatilityAnalyzer analyzer = new ImpliedVolatilityAnalyzer();
        
        // Test IV calculation
        double marketPrice = 45.0;
        double spotPrice = 24800.0;
        double strikePrice = 24850.0;
        double timeToExpiry = 7.0 / 365.0;
        
        System.out.println("\n🧪 Testing IV calculation:");
        System.out.println("===========================");
        double iv = analyzer.calculateImpliedVolatility(marketPrice, spotPrice, strikePrice, timeToExpiry, "CE");
        System.out.println("Calculated IV: " + String.format("%.2f", iv * 100) + "%");
        
        // Test IV analysis
        System.out.println("\n🧪 Testing IV analysis:");
        System.out.println("========================");
        IVAnalysis ivAnalysis = analyzer.analyzeIVLevels(iv, "NIFTY", "CE", spotPrice, strikePrice);
        System.out.println("IV Level: " + ivAnalysis.ivLevel);
        System.out.println("IV Percentile: " + String.format("%.1f", ivAnalysis.ivPercentile) + "%");
        System.out.println("Assessment: " + ivAnalysis.assessment);
        
        System.out.println("\n✅ IMPLIED VOLATILITY ANALYZER TEST COMPLETED!");
    }
}