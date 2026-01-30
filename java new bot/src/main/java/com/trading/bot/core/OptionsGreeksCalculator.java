import java.util.*;

/**
 * OPTIONS GREEKS CALCULATOR - PART 1A
 * Implements Delta, Gamma, Theta, Vega analysis for options trading
 * Fixes the fundamental options strategy flaws
 */
public class OptionsGreeksCalculator {
    
    // Constants for Black-Scholes calculations
    private static final double RISK_FREE_RATE = 0.06; // 6% risk-free rate
    private static final double TRADING_DAYS_PER_YEAR = 252.0;
    
    public OptionsGreeksCalculator() {
        System.out.println("📊 OPTIONS GREEKS CALCULATOR - PART 1A");
        System.out.println("======================================");
        System.out.println("🎯 Implementing Delta, Gamma, Theta, Vega analysis");
        System.out.println("📈 Fixing fundamental options strategy flaws");
        System.out.println("🔧 Professional options pricing and risk analysis");
    }
    
    /**
     * PART 1A-1: Calculate all Greeks for an option
     */
    public OptionsGreeks calculateGreeks(double spotPrice, double strikePrice, 
                                       double timeToExpiry, double volatility, 
                                       String optionType) {
        
        System.out.println("📊 PART 1A-1: Calculating Greeks for " + optionType + " option");
        
        // Calculate d1 and d2 for Black-Scholes
        double d1 = calculateD1(spotPrice, strikePrice, timeToExpiry, volatility);
        double d2 = calculateD2(d1, volatility, timeToExpiry);
        
        // Calculate individual Greeks
        double delta = calculateDelta(d1, optionType);
        double gamma = calculateGamma(spotPrice, d1, volatility, timeToExpiry);
        double theta = calculateTheta(spotPrice, strikePrice, d1, d2, volatility, timeToExpiry, optionType);
        double vega = calculateVega(spotPrice, d1, timeToExpiry);
        double rho = calculateRho(strikePrice, d2, timeToExpiry, optionType);
        
        // Calculate option price
        double optionPrice = calculateOptionPrice(spotPrice, strikePrice, d1, d2, timeToExpiry, optionType);
        
        OptionsGreeks greeks = new OptionsGreeks(
            optionPrice, delta, gamma, theta, vega, rho, d1, d2
        );
        
        System.out.println("✅ Greeks calculated: Delta=" + String.format("%.4f", delta) + 
                          ", Gamma=" + String.format("%.4f", gamma) + 
                          ", Theta=" + String.format("%.4f", theta));
        
        return greeks;
    }
    
    /**
     * PART 1A-2: Calculate Delta (price sensitivity)
     */
    private double calculateDelta(double d1, String optionType) {
        if (optionType.equals("CE")) {
            return normalCDF(d1); // Call delta
        } else {
            return normalCDF(d1) - 1.0; // Put delta
        }
    }
    
    /**
     * PART 1A-3: Calculate Gamma (delta sensitivity)
     */
    private double calculateGamma(double spotPrice, double d1, double volatility, double timeToExpiry) {
        double numerator = normalPDF(d1);
        double denominator = spotPrice * volatility * Math.sqrt(timeToExpiry);
        return numerator / denominator;
    }
    
    /**
     * PART 1A-4: Calculate Theta (time decay)
     */
    private double calculateTheta(double spotPrice, double strikePrice, double d1, double d2, 
                                double volatility, double timeToExpiry, String optionType) {
        
        double term1 = -(spotPrice * normalPDF(d1) * volatility) / (2 * Math.sqrt(timeToExpiry));
        double term2 = RISK_FREE_RATE * strikePrice * Math.exp(-RISK_FREE_RATE * timeToExpiry);
        
        if (optionType.equals("CE")) {
            return (term1 - term2 * normalCDF(d2)) / 365.0; // Daily theta
        } else {
            return (term1 + term2 * normalCDF(-d2)) / 365.0; // Daily theta
        }
    }
    
    /**
     * PART 1A-5: Calculate Vega (volatility sensitivity)
     */
    private double calculateVega(double spotPrice, double d1, double timeToExpiry) {
        return spotPrice * normalPDF(d1) * Math.sqrt(timeToExpiry) / 100.0; // Per 1% volatility change
    }
    
    /**
     * PART 1A-6: Calculate Rho (interest rate sensitivity)
     */
    private double calculateRho(double strikePrice, double d2, double timeToExpiry, String optionType) {
        double factor = strikePrice * timeToExpiry * Math.exp(-RISK_FREE_RATE * timeToExpiry);
        
        if (optionType.equals("CE")) {
            return factor * normalCDF(d2) / 100.0; // Per 1% rate change
        } else {
            return -factor * normalCDF(-d2) / 100.0; // Per 1% rate change
        }
    }
    
    /**
     * PART 1A-7: Calculate option price using Black-Scholes
     */
    private double calculateOptionPrice(double spotPrice, double strikePrice, double d1, double d2, 
                                      double timeToExpiry, String optionType) {
        
        if (optionType.equals("CE")) {
            // Call option price
            return spotPrice * normalCDF(d1) - 
                   strikePrice * Math.exp(-RISK_FREE_RATE * timeToExpiry) * normalCDF(d2);
        } else {
            // Put option price
            return strikePrice * Math.exp(-RISK_FREE_RATE * timeToExpiry) * normalCDF(-d2) - 
                   spotPrice * normalCDF(-d1);
        }
    }
    
    /**
     * Helper method: Calculate d1 for Black-Scholes
     */
    private double calculateD1(double spotPrice, double strikePrice, double timeToExpiry, double volatility) {
        double numerator = Math.log(spotPrice / strikePrice) + 
                          (RISK_FREE_RATE + 0.5 * volatility * volatility) * timeToExpiry;
        double denominator = volatility * Math.sqrt(timeToExpiry);
        return numerator / denominator;
    }
    
    /**
     * Helper method: Calculate d2 for Black-Scholes
     */
    private double calculateD2(double d1, double volatility, double timeToExpiry) {
        return d1 - volatility * Math.sqrt(timeToExpiry);
    }
    
    /**
     * PART 1A-8: Analyze Greeks for trading decisions
     */
    public GreeksAnalysis analyzeGreeksForTrading(OptionsGreeks greeks, String optionType, 
                                                 double spotPrice, double strikePrice) {
        
        System.out.println("🔍 PART 1A-8: Analyzing Greeks for trading decisions");
        
        List<String> signals = new ArrayList<>();
        double confidence = 50.0; // Base confidence
        
        // Delta analysis
        if (Math.abs(greeks.delta) > 0.5) {
            signals.add("High delta (" + String.format("%.3f", greeks.delta) + ") - sensitive to spot moves");
            confidence += 10;
        } else if (Math.abs(greeks.delta) < 0.3) {
            signals.add("Low delta (" + String.format("%.3f", greeks.delta) + ") - less sensitive to spot moves");
            confidence -= 5;
        }
        
        // Gamma analysis
        if (greeks.gamma > 0.01) {
            signals.add("High gamma (" + String.format("%.4f", greeks.gamma) + ") - delta will change rapidly");
            confidence += 5;
        }
        
        // Theta analysis
        if (Math.abs(greeks.theta) > 10) {
            signals.add("High time decay (" + String.format("%.2f", greeks.theta) + ") - avoid holding overnight");
            confidence -= 10;
        } else if (Math.abs(greeks.theta) < 5) {
            signals.add("Low time decay (" + String.format("%.2f", greeks.theta) + ") - can hold longer");
            confidence += 5;
        }
        
        // Vega analysis
        if (greeks.vega > 50) {
            signals.add("High vega (" + String.format("%.2f", greeks.vega) + ") - sensitive to volatility changes");
            confidence += 5;
        }
        
        // Moneyness analysis
        double moneyness = spotPrice / strikePrice;
        if (optionType.equals("CE")) {
            if (moneyness > 1.02) {
                signals.add("ITM Call - higher probability but expensive");
                confidence += 10;
            } else if (moneyness > 0.98 && moneyness <= 1.02) {
                signals.add("ATM Call - balanced risk-reward");
                confidence += 15;
            } else {
                signals.add("OTM Call - cheaper but lower probability");
                confidence += 5;
            }
        } else {
            if (moneyness < 0.98) {
                signals.add("ITM Put - higher probability but expensive");
                confidence += 10;
            } else if (moneyness >= 0.98 && moneyness < 1.02) {
                signals.add("ATM Put - balanced risk-reward");
                confidence += 15;
            } else {
                signals.add("OTM Put - cheaper but lower probability");
                confidence += 5;
            }
        }
        
        // Overall recommendation
        String recommendation = generateGreeksRecommendation(greeks, confidence);
        
        GreeksAnalysis analysis = new GreeksAnalysis(
            confidence, signals, recommendation, 
            Math.abs(greeks.delta) > 0.4, // High delta flag
            Math.abs(greeks.theta) < 8,   // Low theta flag
            greeks.vega > 30              // High vega flag
        );
        
        System.out.println("✅ Greeks analysis complete: " + String.format("%.1f", confidence) + "% confidence");
        
        return analysis;
    }
    
    /**
     * Generate recommendation based on Greeks
     */
    private String generateGreeksRecommendation(OptionsGreeks greeks, double confidence) {
        if (confidence >= 75) {
            return "STRONG BUY - Favorable Greeks profile";
        } else if (confidence >= 65) {
            return "BUY - Good Greeks profile with some concerns";
        } else if (confidence >= 55) {
            return "NEUTRAL - Mixed Greeks signals";
        } else {
            return "AVOID - Unfavorable Greeks profile";
        }
    }
    
    /**
     * Normal cumulative distribution function
     */
    private double normalCDF(double x) {
        return 0.5 * (1 + erf(x / Math.sqrt(2)));
    }
    
    /**
     * Normal probability density function
     */
    private double normalPDF(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
    }
    
    /**
     * Error function approximation
     */
    private double erf(double x) {
        // Abramowitz and Stegun approximation
        double a1 =  0.254829592;
        double a2 = -0.284496736;
        double a3 =  1.421413741;
        double a4 = -1.453152027;
        double a5 =  1.061405429;
        double p  =  0.3275911;
        
        int sign = x < 0 ? -1 : 1;
        x = Math.abs(x);
        
        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        
        return sign * y;
    }
    
    /**
     * Options Greeks data structure
     */
    public static class OptionsGreeks {
        public final double optionPrice;
        public final double delta;
        public final double gamma;
        public final double theta;
        public final double vega;
        public final double rho;
        public final double d1;
        public final double d2;
        
        public OptionsGreeks(double optionPrice, double delta, double gamma, double theta,
                           double vega, double rho, double d1, double d2) {
            this.optionPrice = optionPrice;
            this.delta = delta;
            this.gamma = gamma;
            this.theta = theta;
            this.vega = vega;
            this.rho = rho;
            this.d1 = d1;
            this.d2 = d2;
        }
        
        @Override
        public String toString() {
            return String.format("Greeks: Price=₹%.2f, Delta=%.4f, Gamma=%.4f, Theta=%.2f, Vega=%.2f",
                               optionPrice, delta, gamma, theta, vega);
        }
    }
    
    /**
     * Greeks analysis result
     */
    public static class GreeksAnalysis {
        public final double confidence;
        public final List<String> signals;
        public final String recommendation;
        public final boolean highDelta;
        public final boolean lowTheta;
        public final boolean highVega;
        
        public GreeksAnalysis(double confidence, List<String> signals, String recommendation,
                            boolean highDelta, boolean lowTheta, boolean highVega) {
            this.confidence = confidence;
            this.signals = signals;
            this.recommendation = recommendation;
            this.highDelta = highDelta;
            this.lowTheta = lowTheta;
            this.highVega = highVega;
        }
        
        @Override
        public String toString() {
            return String.format("Greeks Analysis: %.1f%% confidence, %s, %d signals",
                               confidence, recommendation, signals.size());
        }
    }
    
    public static void main(String[] args) {
        System.out.println("📊 TESTING OPTIONS GREEKS CALCULATOR - PART 1A");
        System.out.println("===============================================");
        
        OptionsGreeksCalculator calculator = new OptionsGreeksCalculator();
        
        // Test with NIFTY CE option
        double spotPrice = 24800.0;
        double strikePrice = 24850.0;
        double timeToExpiry = 7.0 / 365.0; // 7 days
        double volatility = 0.25; // 25% IV
        
        System.out.println("\n🧪 Testing NIFTY 24850 CE:");
        System.out.println("===========================");
        OptionsGreeks ceGreeks = calculator.calculateGreeks(spotPrice, strikePrice, timeToExpiry, volatility, "CE");
        System.out.println(ceGreeks);
        
        GreeksAnalysis ceAnalysis = calculator.analyzeGreeksForTrading(ceGreeks, "CE", spotPrice, strikePrice);
        System.out.println(ceAnalysis);
        
        System.out.println("\n🧪 Testing NIFTY 24750 PE:");
        System.out.println("===========================");
        OptionsGreeks peGreeks = calculator.calculateGreeks(spotPrice, 24750.0, timeToExpiry, volatility, "PE");
        System.out.println(peGreeks);
        
        GreeksAnalysis peAnalysis = calculator.analyzeGreeksForTrading(peGreeks, "PE", spotPrice, 24750.0);
        System.out.println(peAnalysis);
        
        System.out.println("\n✅ OPTIONS GREEKS CALCULATOR TEST COMPLETED!");
    }
}