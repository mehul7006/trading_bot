import java.time.LocalDateTime;
import java.util.*;

/**
 * ENHANCED PE CALL GENERATOR - PART 1C
 * Fixes the missing PE (Put) options calls issue
 * Generates balanced CE/PE signals based on market conditions
 */
public class EnhancedPECallGenerator {
    
    private final OptionsGreeksCalculator greeksCalculator;
    private final ImpliedVolatilityAnalyzer ivAnalyzer;
    
    // PE-specific parameters
    private final double peConfidenceThreshold = 70.0; // Lower threshold for PE calls
    private final Map<String, Integer[]> peStrikes;
    
    public EnhancedPECallGenerator() {
        System.out.println("📊 ENHANCED PE CALL GENERATOR - PART 1C");
        System.out.println("=======================================");
        System.out.println("🎯 Fixing missing PE (Put) options calls");
        System.out.println("📈 Generating balanced CE/PE signals");
        System.out.println("🔧 Bearish market opportunity capture");
        
        this.greeksCalculator = new OptionsGreeksCalculator();
        this.ivAnalyzer = new ImpliedVolatilityAnalyzer();
        
        // Initialize PE strike ranges
        this.peStrikes = new HashMap<>();
        this.peStrikes.put("NIFTY", new Integer[]{24600, 24650, 24700, 24750, 24800, 24850, 24900});
        this.peStrikes.put("SENSEX", new Integer[]{81000, 81250, 81500, 81750, 82000, 82250, 82500});
    }
    
    /**
     * PART 1C-1: Analyze bearish market conditions for PE calls
     */
    public BearishAnalysis analyzeBearishConditions(String index, double spotPrice, 
                                                   List<Double> recentPrices, LocalDateTime timestamp) {
        
        System.out.println("🔍 PART 1C-1: Analyzing bearish conditions for " + index + " PE calls");
        
        List<String> bearishSignals = new ArrayList<>();
        double bearishScore = 0.0;
        
        // 1. Price trend analysis
        if (recentPrices.size() >= 10) {
            double shortMA = calculateMA(recentPrices.subList(0, 5));
            double longMA = calculateMA(recentPrices.subList(0, 10));
            
            if (shortMA < longMA) {
                bearishScore += 0.2;
                bearishSignals.add("Short MA below Long MA - bearish trend");
            }
            
            // Check for recent decline
            double recentDecline = (recentPrices.get(0) - recentPrices.get(4)) / recentPrices.get(4);
            if (recentDecline < -0.005) { // 0.5% decline
                bearishScore += 0.15;
                bearishSignals.add("Recent price decline of " + String.format("%.2f", recentDecline * 100) + "%");
            }
        }
        
        // 2. RSI analysis for bearish signals
        double rsi = calculateRSI(recentPrices);
        if (rsi > 70) {
            bearishScore += 0.2;
            bearishSignals.add("RSI overbought (" + String.format("%.1f", rsi) + ") - bearish reversal likely");
        } else if (rsi > 60) {
            bearishScore += 0.1;
            bearishSignals.add("RSI elevated (" + String.format("%.1f", rsi) + ") - potential bearish pressure");
        }
        
        // 3. Support level analysis
        double supportLevel = findNearestSupport(recentPrices, spotPrice);
        double distanceToSupport = (spotPrice - supportLevel) / spotPrice;
        if (distanceToSupport < 0.02) { // Within 2% of support
            bearishScore += 0.15;
            bearishSignals.add("Near support level ₹" + String.format("%.0f", supportLevel) + " - breakdown risk");
        }
        
        // 4. Volume analysis for bearish confirmation
        double volumeSignal = analyzeVolumeForBearish(index, timestamp);
        if (volumeSignal > 0.6) {
            bearishScore += 0.1;
            bearishSignals.add("High volume supports bearish move");
        }
        
        // 5. Market regime analysis
        MarketRegime regime = analyzeMarketRegime(recentPrices, timestamp);
        if (regime == MarketRegime.BEARISH || regime == MarketRegime.VOLATILE) {
            bearishScore += 0.15;
            bearishSignals.add("Market regime: " + regime + " - favors PE options");
        }
        
        // 6. Time-of-day bearish bias
        int hour = timestamp.getHour();
        if (hour >= 14 && hour <= 15) { // Afternoon selling pressure
            bearishScore += 0.05;
            bearishSignals.add("Afternoon session - typical selling pressure");
        }
        
        // Convert to confidence
        double confidence = Math.min(95, 50 + (bearishScore * 80));
        
        BearishAnalysis analysis = new BearishAnalysis(
            bearishScore, confidence, bearishSignals, supportLevel, rsi, regime
        );
        
        System.out.println("✅ Bearish analysis: " + String.format("%.1f", confidence) + 
                          "% confidence, " + bearishSignals.size() + " signals");
        
        return analysis;
    }
    
    /**
     * PART 1C-2: Generate PE options calls
     */
    public List<PEOptionsCall> generatePECalls(String index, double spotPrice, 
                                              BearishAnalysis bearishAnalysis, LocalDateTime timestamp) {
        
        System.out.println("📊 PART 1C-2: Generating PE calls for " + index);
        
        List<PEOptionsCall> peCalls = new ArrayList<>();
        
        // Only generate PE calls if bearish confidence is above threshold
        if (bearishAnalysis.confidence < peConfidenceThreshold) {
            System.out.println("⚠️ Bearish confidence below threshold (" + 
                              String.format("%.1f", bearishAnalysis.confidence) + "% < " + 
                              peConfidenceThreshold + "%) - no PE calls generated");
            return peCalls;
        }
        
        Integer[] strikes = peStrikes.get(index);
        if (strikes == null) {
            System.err.println("❌ No strikes defined for " + index);
            return peCalls;
        }
        
        // Generate PE calls for suitable strikes
        for (Integer strike : strikes) {
            if (strike < spotPrice) { // Focus on OTM and ATM puts
                PEOptionsCall peCall = generateSinglePECall(index, spotPrice, strike, 
                                                           bearishAnalysis, timestamp);
                if (peCall != null) {
                    peCalls.add(peCall);
                }
            }
        }
        
        // Sort by confidence and take top 2-3 calls
        peCalls.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        if (peCalls.size() > 3) {
            peCalls = peCalls.subList(0, 3);
        }
        
        System.out.println("✅ Generated " + peCalls.size() + " PE calls for " + index);
        
        return peCalls;
    }
    
    /**
     * Generate single PE call
     */
    private PEOptionsCall generateSinglePECall(String index, double spotPrice, int strike,
                                              BearishAnalysis bearishAnalysis, LocalDateTime timestamp) {
        
        double timeToExpiry = 7.0 / 365.0; // 7 days to expiry
        double estimatedIV = 0.25; // Initial IV estimate
        
        // Calculate Greeks
        OptionsGreeksCalculator.OptionsGreeks greeks = greeksCalculator.calculateGreeks(
            spotPrice, strike, timeToExpiry, estimatedIV, "PE");
        
        // Analyze Greeks for PE trading
        OptionsGreeksCalculator.GreeksAnalysis greeksAnalysis = greeksCalculator.analyzeGreeksForTrading(
            greeks, "PE", spotPrice, strike);
        
        // Calculate IV analysis
        ImpliedVolatilityAnalyzer.IVAnalysis ivAnalysis = ivAnalyzer.analyzeIVLevels(
            estimatedIV, index, "PE", spotPrice, strike);
        
        // Combine all factors for final confidence
        double finalConfidence = calculatePEConfidence(bearishAnalysis, greeksAnalysis, ivAnalysis, 
                                                      spotPrice, strike);
        
        // Only create call if confidence is above threshold
        if (finalConfidence < peConfidenceThreshold) {
            return null;
        }
        
        // Calculate targets and stop-loss
        double premium = greeks.optionPrice;
        double target1 = premium * 1.25; // 25% profit
        double target2 = premium * 1.50; // 50% profit
        double stopLoss = premium * 0.75; // 25% stop-loss
        
        // Generate call ID
        String callId = generatePECallId(index, strike, timestamp);
        
        // Create analysis summary
        String analysis = generatePEAnalysis(bearishAnalysis, greeksAnalysis, ivAnalysis);
        
        return new PEOptionsCall(
            callId, index, strike, timestamp, spotPrice, premium, finalConfidence,
            target1, target2, stopLoss, analysis, greeks, bearishAnalysis.supportLevel
        );
    }
    
    /**
     * PART 1C-3: Calculate PE-specific confidence
     */
    private double calculatePEConfidence(BearishAnalysis bearishAnalysis, 
                                       OptionsGreeksCalculator.GreeksAnalysis greeksAnalysis,
                                       ImpliedVolatilityAnalyzer.IVAnalysis ivAnalysis,
                                       double spotPrice, int strike) {
        
        double confidence = 0.0;
        
        // Bearish analysis weight (40%)
        confidence += bearishAnalysis.confidence * 0.4;
        
        // Greeks analysis weight (30%)
        confidence += greeksAnalysis.confidence * 0.3;
        
        // IV analysis weight (20%)
        confidence += ivAnalysis.confidence * 0.2;
        
        // Moneyness adjustment (10%)
        double moneyness = spotPrice / strike;
        if (moneyness > 1.02) { // OTM put
            confidence += 5; // Cheaper, higher potential return
        } else if (moneyness > 0.98 && moneyness <= 1.02) { // ATM put
            confidence += 10; // Balanced risk-reward
        } else { // ITM put
            confidence += 3; // More expensive but higher probability
        }
        
        return Math.min(95, Math.max(0, confidence));
    }
    
    /**
     * PART 1C-4: PE call monitoring and management
     */
    public PECallUpdate monitorPECall(PEOptionsCall peCall, double currentSpotPrice, 
                                     LocalDateTime currentTime) {
        
        System.out.println("👁️ PART 1C-4: Monitoring PE call " + peCall.callId);
        
        // Calculate current option price
        double timeElapsed = java.time.Duration.between(peCall.timestamp, currentTime).toMinutes() / (24.0 * 60);
        double newTimeToExpiry = Math.max(0.001, (7.0 / 365.0) - timeElapsed);
        
        OptionsGreeksCalculator.OptionsGreeks currentGreeks = greeksCalculator.calculateGreeks(
            currentSpotPrice, peCall.strike, newTimeToExpiry, 0.25, "PE");
        
        double currentPremium = currentGreeks.optionPrice;
        double pnl = currentPremium - peCall.entryPremium;
        double pnlPercent = (pnl / peCall.entryPremium) * 100;
        
        // Check exit conditions
        boolean shouldExit = false;
        String exitReason = "";
        
        // Target hit
        if (currentPremium >= peCall.target1) {
            shouldExit = true;
            exitReason = "Target 1 achieved (" + String.format("%.2f", pnlPercent) + "% profit)";
        }
        // Stop-loss hit
        else if (currentPremium <= peCall.stopLoss) {
            shouldExit = true;
            exitReason = "Stop-loss hit (" + String.format("%.2f", pnlPercent) + "% loss)";
        }
        // Time decay check
        else if (Math.abs(currentGreeks.theta) > 15 && pnl < 0) {
            shouldExit = true;
            exitReason = "High time decay with loss - exit to preserve capital";
        }
        // Support level breach (bullish for underlying, bearish for PE)
        else if (currentSpotPrice < peCall.supportLevel * 0.995) {
            // Support broken - good for PE
            exitReason = "Support broken - favorable for PE, continue holding";
        }
        
        PECallUpdate update = new PECallUpdate(
            currentPremium, pnl, pnlPercent, shouldExit, exitReason, currentGreeks
        );
        
        System.out.println("✅ PE call update: P&L=" + String.format("%.2f", pnlPercent) + 
                          "%, " + (shouldExit ? "EXIT: " + exitReason : "HOLD"));
        
        return update;
    }
    
    // Helper methods
    private double calculateMA(List<Double> prices) {
        return prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
    
    private double calculateRSI(List<Double> prices) {
        if (prices.size() < 14) return 50.0;
        
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i < Math.min(15, prices.size()); i++) {
            double change = prices.get(i-1) - prices.get(i);
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        
        avgGain /= 14; avgLoss /= 14;
        if (avgLoss == 0) return 100;
        
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }
    
    private double findNearestSupport(List<Double> prices, double currentPrice) {
        // Find recent low as support
        return prices.stream().mapToDouble(Double::doubleValue).min().orElse(currentPrice * 0.98);
    }
    
    private double analyzeVolumeForBearish(String index, LocalDateTime timestamp) {
        // Simplified volume analysis
        int hour = timestamp.getHour();
        if (hour >= 14) return 0.7; // Higher volume in afternoon
        return 0.5;
    }
    
    private MarketRegime analyzeMarketRegime(List<Double> prices, LocalDateTime timestamp) {
        if (prices.size() < 5) return MarketRegime.NEUTRAL;
        
        double volatility = calculateVolatility(prices);
        double trend = (prices.get(0) - prices.get(4)) / prices.get(4);
        
        if (volatility > 0.02) return MarketRegime.VOLATILE;
        if (trend < -0.01) return MarketRegime.BEARISH;
        if (trend > 0.01) return MarketRegime.BULLISH;
        return MarketRegime.NEUTRAL;
    }
    
    private double calculateVolatility(List<Double> prices) {
        if (prices.size() < 2) return 0.01;
        
        double sum = 0;
        for (int i = 1; i < prices.size(); i++) {
            double change = Math.log(prices.get(i-1) / prices.get(i));
            sum += change * change;
        }
        
        return Math.sqrt(sum / (prices.size() - 1));
    }
    
    private String generatePECallId(String index, int strike, LocalDateTime timestamp) {
        return String.format("%s_PE_%d_%s", index, strike, 
                           timestamp.format(java.time.format.DateTimeFormatter.ofPattern("HHmm")));
    }
    
    private String generatePEAnalysis(BearishAnalysis bearishAnalysis, 
                                    OptionsGreeksCalculator.GreeksAnalysis greeksAnalysis,
                                    ImpliedVolatilityAnalyzer.IVAnalysis ivAnalysis) {
        return String.format("PE Analysis: Bearish confidence %.1f%%, Greeks %s, IV %s (%s)",
                           bearishAnalysis.confidence, greeksAnalysis.recommendation, 
                           ivAnalysis.ivLevel, ivAnalysis.assessment);
    }
    
    // Data classes
    public static class BearishAnalysis {
        public final double bearishScore;
        public final double confidence;
        public final List<String> signals;
        public final double supportLevel;
        public final double rsi;
        public final MarketRegime regime;
        
        public BearishAnalysis(double bearishScore, double confidence, List<String> signals,
                              double supportLevel, double rsi, MarketRegime regime) {
            this.bearishScore = bearishScore;
            this.confidence = confidence;
            this.signals = signals;
            this.supportLevel = supportLevel;
            this.rsi = rsi;
            this.regime = regime;
        }
    }
    
    public static class PEOptionsCall {
        public final String callId;
        public final String index;
        public final int strike;
        public final LocalDateTime timestamp;
        public final double spotPrice;
        public final double entryPremium;
        public final double confidence;
        public final double target1;
        public final double target2;
        public final double stopLoss;
        public final String analysis;
        public final OptionsGreeksCalculator.OptionsGreeks greeks;
        public final double supportLevel;
        
        public PEOptionsCall(String callId, String index, int strike, LocalDateTime timestamp,
                            double spotPrice, double entryPremium, double confidence,
                            double target1, double target2, double stopLoss, String analysis,
                            OptionsGreeksCalculator.OptionsGreeks greeks, double supportLevel) {
            this.callId = callId;
            this.index = index;
            this.strike = strike;
            this.timestamp = timestamp;
            this.spotPrice = spotPrice;
            this.entryPremium = entryPremium;
            this.confidence = confidence;
            this.target1 = target1;
            this.target2 = target2;
            this.stopLoss = stopLoss;
            this.analysis = analysis;
            this.greeks = greeks;
            this.supportLevel = supportLevel;
        }
        
        @Override
        public String toString() {
            return String.format("%s PE %d @ ₹%.2f | %.1f%% confidence | %s",
                               index, strike, entryPremium, confidence, callId);
        }
    }
    
    public static class PECallUpdate {
        public final double currentPremium;
        public final double pnl;
        public final double pnlPercent;
        public final boolean shouldExit;
        public final String exitReason;
        public final OptionsGreeksCalculator.OptionsGreeks currentGreeks;
        
        public PECallUpdate(double currentPremium, double pnl, double pnlPercent,
                           boolean shouldExit, String exitReason, 
                           OptionsGreeksCalculator.OptionsGreeks currentGreeks) {
            this.currentPremium = currentPremium;
            this.pnl = pnl;
            this.pnlPercent = pnlPercent;
            this.shouldExit = shouldExit;
            this.exitReason = exitReason;
            this.currentGreeks = currentGreeks;
        }
    }
    
    public enum MarketRegime {
        BULLISH, BEARISH, NEUTRAL, VOLATILE
    }
    
    public static void main(String[] args) {
        System.out.println("📊 TESTING ENHANCED PE CALL GENERATOR - PART 1C");
        System.out.println("===============================================");
        
        EnhancedPECallGenerator generator = new EnhancedPECallGenerator();
        
        // Test bearish analysis
        List<Double> testPrices = Arrays.asList(24800.0, 24790.0, 24785.0, 24780.0, 24775.0, 
                                               24770.0, 24765.0, 24760.0, 24755.0, 24750.0);
        
        System.out.println("\n🧪 Testing bearish analysis:");
        System.out.println("=============================");
        BearishAnalysis bearishAnalysis = generator.analyzeBearishConditions(
            "NIFTY", 24750.0, testPrices, LocalDateTime.now());
        
        System.out.println("Bearish confidence: " + String.format("%.1f", bearishAnalysis.confidence) + "%");
        System.out.println("Bearish signals: " + bearishAnalysis.signals.size());
        
        // Test PE call generation
        System.out.println("\n🧪 Testing PE call generation:");
        System.out.println("===============================");
        List<PEOptionsCall> peCalls = generator.generatePECalls(
            "NIFTY", 24750.0, bearishAnalysis, LocalDateTime.now());
        
        for (PEOptionsCall call : peCalls) {
            System.out.println(call);
        }
        
        System.out.println("\n✅ ENHANCED PE CALL GENERATOR TEST COMPLETED!");
    }
}