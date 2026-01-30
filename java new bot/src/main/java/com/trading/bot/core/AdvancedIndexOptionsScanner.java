package com.trading.bot.core;
import com.trading.bot.market.RealMarketDataProvider;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advanced Index Options Scanner and Call Generator
 * Scans all index options markets and generates high-confidence calls
 * Analyzes multiple factors: volumes, IV, Greeks, technical indicators
 */
public class AdvancedIndexOptionsScanner {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();    private static final Logger logger = LoggerFactory.getLogger(AdvancedIndexOptionsScanner.class);
    
    // Supported Indian Index Options
    private final List<String> SUPPORTED_INDICES = Arrays.asList(
        "NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "SENSEX", "BANKEX"
    );
    
    // Market data storage
    private final Map<String, IndexMarketData> marketData = new ConcurrentHashMap<>();
    private final Map<String, List<OptionsCall>> generatedCalls = new ConcurrentHashMap<>();
    private final AtomicInteger totalScannedOptions = new AtomicInteger(0);
    
    // Analysis parameters
    private final double MIN_CONFIDENCE_THRESHOLD = 75.0;
    private final double HIGH_VOLUME_THRESHOLD = 1000000; // 10 Lakh volume
    private final double MAX_IV_THRESHOLD = 35.0;
    private final double MIN_OI_THRESHOLD = 50000;
    
    public AdvancedIndexOptionsScanner() {
        initializeMarketData();
        logger.info("Advanced Index Options Scanner initialized for {} indices", SUPPORTED_INDICES.size());
    }
    
    /**
     * Main scanning function - scans all index options
     */
    public void scanAllIndexOptions() {
        logger.info("Starting comprehensive index options scan...");
        
        for (String index : SUPPORTED_INDICES) {
            try {
                scanIndexOptions(index);
            } catch (Exception e) {
                logger.error("Error scanning {} options: {}", index, e.getMessage());
            }
        }
        
        logger.info("Scan completed. Total options analyzed: {}", totalScannedOptions.get());
        generateConsolidatedReport();
    }
    
    /**
     * Scan specific index options
     */
    public void scanIndexOptions(String index) {
        logger.info("Scanning {} options market...", index);
        
        IndexMarketData data = fetchRealTimeData(index);
        marketData.put(index, data);
        
        // Analyze call options
        List<OptionsCall> callOpportunities = analyzeCallOptions(index, data);
        
        // Analyze put options
        List<OptionsCall> putOpportunities = analyzePutOptions(index, data);
        
        // Combine and filter high-confidence calls
        List<OptionsCall> highConfidenceCalls = filterHighConfidenceCalls(callOpportunities, putOpportunities);
        generatedCalls.put(index, highConfidenceCalls);
        
        totalScannedOptions.addAndGet(data.getTotalOptionsCount());
        
        logger.info("{} scan complete - Generated {} high-confidence calls", 
                   index, highConfidenceCalls.size());
    }
    
    /**
     * Analyze call options for specific index
     */
    private List<OptionsCall> analyzeCallOptions(String index, IndexMarketData data) {
        List<OptionsCall> calls = new ArrayList<>();
        double currentPrice = data.getCurrentPrice();
        
        // Scan ATM and OTM call strikes
        for (double strike : data.getCallStrikes()) {
            if (strike >= currentPrice && strike <= currentPrice * 1.05) { // Within 5% OTM
                
                OptionsCall call = analyzeCallStrike(index, strike, data);
                if (call != null && call.getConfidence() >= MIN_CONFIDENCE_THRESHOLD) {
                    calls.add(call);
                }
            }
        }
        
        return calls;
    }
    
    /**
     * Analyze put options for specific index
     */
    private List<OptionsCall> analyzePutOptions(String index, IndexMarketData data) {
        List<OptionsCall> puts = new ArrayList<>();
        double currentPrice = data.getCurrentPrice();
        
        // Scan ATM and OTM put strikes
        for (double strike : data.getPutStrikes()) {
            if (strike <= currentPrice && strike >= currentPrice * 0.95) { // Within 5% OTM
                
                OptionsCall put = analyzePutStrike(index, strike, data);
                if (put != null && put.getConfidence() >= MIN_CONFIDENCE_THRESHOLD) {
                    puts.add(put);
                }
            }
        }
        
        return puts;
    }
    
    /**
     * Detailed analysis of call strike
     */
    private OptionsCall analyzeCallStrike(String index, double strike, IndexMarketData data) {
        try {
            // Get option chain data for this strike
            OptionChainData chainData = data.getCallChainData(strike);
            if (chainData == null) return null;
            
            // Calculate confidence based on multiple factors
            double confidence = calculateCallConfidence(index, strike, chainData, data);
            
            if (confidence >= MIN_CONFIDENCE_THRESHOLD) {
                return new OptionsCall(
                    index,
                    OptionsCall.Type.CALL,
                    strike,
                    chainData.getPremium(),
                    confidence,
                    chainData.getVolume(),
                    chainData.getOpenInterest(),
                    chainData.getImpliedVolatility(),
                    generateCallReason(index, strike, chainData, data)
                );
            }
            
        } catch (Exception e) {
            logger.error("Error analyzing call strike {} for {}: {}", strike, index, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Detailed analysis of put strike
     */
    private OptionsCall analyzePutStrike(String index, double strike, IndexMarketData data) {
        try {
            OptionChainData chainData = data.getPutChainData(strike);
            if (chainData == null) return null;
            
            double confidence = calculatePutConfidence(index, strike, chainData, data);
            
            if (confidence >= MIN_CONFIDENCE_THRESHOLD) {
                return new OptionsCall(
                    index,
                    OptionsCall.Type.PUT,
                    strike,
                    chainData.getPremium(),
                    confidence,
                    chainData.getVolume(),
                    chainData.getOpenInterest(),
                    chainData.getImpliedVolatility(),
                    generatePutReason(index, strike, chainData, data)
                );
            }
            
        } catch (Exception e) {
            logger.error("Error analyzing put strike {} for {}: {}", strike, index, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Calculate confidence for call options based on multiple factors
     */
    private double calculateCallConfidence(String index, double strike, OptionChainData chainData, IndexMarketData marketData) {
        double confidence = 0.0;
        
        // Factor 1: Volume Analysis (25% weight)
        double volumeScore = calculateVolumeScore(chainData.getVolume());
        confidence += volumeScore * 0.25;
        
        // Factor 2: Open Interest Analysis (20% weight)
        double oiScore = calculateOIScore(chainData.getOpenInterest());
        confidence += oiScore * 0.20;
        
        // Factor 3: Technical Analysis (20% weight)
        double technicalScore = calculateTechnicalScore(index, marketData, true);
        confidence += technicalScore * 0.20;
        
        // Factor 4: Implied Volatility Analysis (15% weight)
        double ivScore = calculateIVScore(chainData.getImpliedVolatility());
        confidence += ivScore * 0.15;
        
        // Factor 5: Greeks Analysis (10% weight)
        double greeksScore = calculateGreeksScore(chainData, true);
        confidence += greeksScore * 0.10;
        
        // Factor 6: Market Momentum (10% weight)
        double momentumScore = calculateMomentumScore(marketData);
        confidence += momentumScore * 0.10;
        
        return Math.min(confidence, 100.0);
    }
    
    /**
     * Calculate confidence for put options
     */
    private double calculatePutConfidence(String index, double strike, OptionChainData chainData, IndexMarketData marketData) {
        double confidence = 0.0;
        
        // Similar to call confidence but with bearish bias
        double volumeScore = calculateVolumeScore(chainData.getVolume());
        confidence += volumeScore * 0.25;
        
        double oiScore = calculateOIScore(chainData.getOpenInterest());
        confidence += oiScore * 0.20;
        
        double technicalScore = calculateTechnicalScore(index, marketData, false);
        confidence += technicalScore * 0.20;
        
        double ivScore = calculateIVScore(chainData.getImpliedVolatility());
        confidence += ivScore * 0.15;
        
        double greeksScore = calculateGreeksScore(chainData, false);
        confidence += greeksScore * 0.10;
        
        double momentumScore = calculateMomentumScore(marketData) * -1; // Inverse for puts
        confidence += Math.abs(momentumScore) * 0.10;
        
        return Math.min(confidence, 100.0);
    }
    
    /**
     * Calculate volume-based score
     */
    private double calculateVolumeScore(long volume) {
        if (volume >= HIGH_VOLUME_THRESHOLD) return 100.0;
        if (volume >= HIGH_VOLUME_THRESHOLD * 0.5) return 80.0;
        if (volume >= HIGH_VOLUME_THRESHOLD * 0.25) return 60.0;
        if (volume >= HIGH_VOLUME_THRESHOLD * 0.1) return 40.0;
        return 20.0;
    }
    
    /**
     * Calculate Open Interest score
     */
    private double calculateOIScore(long openInterest) {
        if (openInterest >= MIN_OI_THRESHOLD * 2) return 100.0;
        if (openInterest >= MIN_OI_THRESHOLD * 1.5) return 80.0;
        if (openInterest >= MIN_OI_THRESHOLD) return 60.0;
        if (openInterest >= MIN_OI_THRESHOLD * 0.5) return 40.0;
        return 20.0;
    }
    
    /**
     * Calculate technical analysis score
     */
    private double calculateTechnicalScore(String index, IndexMarketData data, boolean isCall) {
        double score = 50.0; // Neutral base
        
        // RSI Analysis
        double rsi = data.getRSI();
        if (isCall) {
            if (rsi < 30) score += 20; // Oversold for calls
            else if (rsi > 70) score -= 15; // Overbought against calls
        } else {
            if (rsi > 70) score += 20; // Overbought for puts
            else if (rsi < 30) score -= 15; // Oversold against puts
        }
        
        // MACD Analysis
        double macd = data.getMACDSignal();
        if (isCall && macd > 0) score += 15;
        else if (!isCall && macd < 0) score += 15;
        
        // Support/Resistance Analysis
        double currentPrice = data.getCurrentPrice();
        if (isCall && currentPrice > data.getSupport()) score += 10;
        else if (!isCall && currentPrice < data.getResistance()) score += 10;
        
        return Math.min(Math.max(score, 0), 100);
    }
    
    /**
     * Calculate IV score (lower IV is generally better for buying)
     */
    private double calculateIVScore(double iv) {
        if (iv <= 15) return 100.0;
        if (iv <= 20) return 80.0;
        if (iv <= 25) return 60.0;
        if (iv <= 30) return 40.0;
        return 20.0;
    }
    
    /**
     * Calculate Greeks-based score
     */
    private double calculateGreeksScore(OptionChainData chainData, boolean isCall) {
        double score = 50.0;
        
        // Delta analysis
        double delta = chainData.getDelta();
        if (isCall && delta > 0.3 && delta < 0.7) score += 20;
        else if (!isCall && delta < -0.3 && delta > -0.7) score += 20;
        
        // Gamma analysis
        double gamma = chainData.getGamma();
        if (gamma > 0.01) score += 15; // High gamma good for momentum
        
        // Theta analysis (time decay)
        double theta = chainData.getTheta();
        if (Math.abs(theta) < 0.05) score += 15; // Low time decay
        
        return Math.min(Math.max(score, 0), 100);
    }
    
    /**
     * Calculate momentum score
     */
    private double calculateMomentumScore(IndexMarketData data) {
        double dayChange = data.getDayChangePercent();
        if (dayChange > 2.0) return 90.0;
        if (dayChange > 1.0) return 70.0;
        if (dayChange > 0.5) return 55.0;
        if (dayChange > 0) return 50.0;
        if (dayChange > -0.5) return 45.0;
        if (dayChange > -1.0) return 30.0;
        return 10.0;
    }
    
    /**
     * Filter and sort high confidence calls
     */
    private List<OptionsCall> filterHighConfidenceCalls(List<OptionsCall> calls, List<OptionsCall> puts) {
        List<OptionsCall> combined = new ArrayList<>();
        combined.addAll(calls);
        combined.addAll(puts);
        
        return combined.stream()
                .filter(call -> call.getConfidence() >= MIN_CONFIDENCE_THRESHOLD)
                .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
                .limit(5) // Top 5 calls per index
                .collect(ArrayList::new, (list, item) -> list.add(item), (list1, list2) -> list1.addAll(list2));
    }
    
    /**
     * Generate human-readable reason for call
     */
    private String generateCallReason(String index, double strike, OptionChainData chainData, IndexMarketData data) {
        StringBuilder reason = new StringBuilder();
        reason.append(String.format("High volume (%,d), ", chainData.getVolume()));
        reason.append(String.format("Strong OI (%,d), ", chainData.getOpenInterest()));
        reason.append(String.format("Favorable IV (%.1f%%), ", chainData.getImpliedVolatility()));
        reason.append(String.format("Technical bullish signal, "));
        reason.append(String.format("Current: %.2f, Target: %.2f+", data.getCurrentPrice(), strike));
        return reason.toString();
    }
    
    /**
     * Generate reason for put
     */
    private String generatePutReason(String index, double strike, OptionChainData chainData, IndexMarketData data) {
        StringBuilder reason = new StringBuilder();
        reason.append(String.format("High volume (%,d), ", chainData.getVolume()));
        reason.append(String.format("Strong OI (%,d), ", chainData.getOpenInterest()));
        reason.append(String.format("Favorable IV (%.1f%%), ", chainData.getImpliedVolatility()));
        reason.append(String.format("Technical bearish signal, "));
        reason.append(String.format("Current: %.2f, Target: %.2f-", data.getCurrentPrice(), strike));
        return reason.toString();
    }
    
    /**
     * Fetch real-time market data (simulated for now)
     */
    private IndexMarketData fetchRealTimeData(String index) {
        // In real implementation, this would fetch from NSE/BSE APIs
        return new IndexMarketData(index);
    }
    
    /**
     * Initialize market data for all indices
     */
    private void initializeMarketData() {
        for (String index : SUPPORTED_INDICES) {
            marketData.put(index, new IndexMarketData(index));
        }
    }
    
    /**
     * Generate consolidated report of all findings
     */
    public void generateConsolidatedReport() {
        logger.info("\n=== COMPREHENSIVE INDEX OPTIONS ANALYSIS REPORT ===");
        logger.info("Total Options Scanned: {}", totalScannedOptions.get());
        logger.info("Confidence Threshold: {}%", MIN_CONFIDENCE_THRESHOLD);
        
        for (String index : SUPPORTED_INDICES) {
            List<OptionsCall> calls = generatedCalls.get(index);
            if (calls != null && !calls.isEmpty()) {
                logger.info("\n--- {} HIGH-CONFIDENCE CALLS ---", index);
                for (int i = 0; i < calls.size(); i++) {
                    OptionsCall call = calls.get(i);
                    logger.info("{}. {} {} Strike:{} Premium:{} Confidence:{:.1f}%", 
                               i + 1, call.getType(), index, call.getStrike(), 
                               call.getPremium(), call.getConfidence());
                    logger.info("   Volume: {:,} | OI: {:,} | IV: {:.1f}%", 
                               call.getVolume(), call.getOpenInterest(), call.getImpliedVolatility());
                    logger.info("   Reason: {}", call.getReason());
                }
            }
        }
        
        logger.info("\n=== REPORT COMPLETE ===");
    }
    
    /**
     * Get top calls across all indices
     */
    public List<OptionsCall> getTopCallsAcrossAllIndices(int limit) {
        return generatedCalls.values().stream()
                .flatMap(List::stream)
                .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
                .limit(limit)
                .collect(ArrayList::new, (list, item) -> list.add(item), (list1, list2) -> list1.addAll(list2));
    }
    
    /**
     * Get calls for specific index
     */
    public List<OptionsCall> getCallsForIndex(String index) {
        return generatedCalls.getOrDefault(index, new ArrayList<>());
    }
    
    // Inner classes for data structures
    
    /**
     * Options call recommendation
     */
    public static class OptionsCall {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        public enum Type { CALL, PUT }
        
        private final String index;
        private final Type type;
        private final double strike;
        private final double premium;
        private final double confidence;
        private final long volume;
        private final long openInterest;
        private final double impliedVolatility;
        private final String reason;
        
        public OptionsCall(String index, Type type, double strike, double premium, 
                          double confidence, long volume, long openInterest, 
                          double impliedVolatility, String reason) {
            this.index = index;
            this.type = type;
            this.strike = strike;
            this.premium = premium;
            this.confidence = confidence;
            this.volume = volume;
            this.openInterest = openInterest;
            this.impliedVolatility = impliedVolatility;
            this.reason = reason;
        }
        
        // Getters
        public String getIndex() { return index; }
        public Type getType() { return type; }
        public double getStrike() { return strike; }
        public double getPremium() { return premium; }
        public double getConfidence() { return confidence; }
        public long getVolume() { return volume; }
        public long getOpenInterest() { return openInterest; }
        public double getImpliedVolatility() { return impliedVolatility; }
        public String getReason() { return reason; }
    }
    
    /**
     * Market data for an index
     */
    private static class IndexMarketData {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        private final String index;
        private double currentPrice;
        private double dayChangePercent;
        private double rsi;
        private double macdSignal;
        private double support;
        private double resistance;
        private final Map<Double, OptionChainData> callChain = new HashMap<>();
        private final Map<Double, OptionChainData> putChain = new HashMap<>();
        
        public IndexMarketData(String index) {
            this.index = index;
            initializeWithMockData();
        }
        
        private void initializeWithMockData() {
            // Mock data - in real implementation, fetch from market APIs
            switch (index) {
                case "NIFTY":
                    currentPrice = realData.getRealPrice("NIFTY");
                    dayChangePercent = 0.75;
                    break;
                case "BANKNIFTY":
                    currentPrice = 44500.0;
                    dayChangePercent = 1.25;
                    break;
                case "SENSEX":
                    currentPrice = 65800.0;
                    dayChangePercent = 0.85;
                    break;
                default:
                    currentPrice = 18000.0;
                    dayChangePercent = 0.5;
            }
            
            rsi = 55.0 + Math.random() * 20; // Random RSI between 55-75
            macdSignal = Math.random() > 0.5 ? 1.0 : -1.0;
            support = currentPrice * 0.98;
            resistance = currentPrice * 1.02;
            
            initializeOptionChains();
        }
        
        private void initializeOptionChains() {
            // Generate option chain data for strikes around current price
            for (int i = -10; i <= 10; i++) {
                double strike = Math.round((currentPrice + (i * 100)) / 50) * 50; // Round to nearest 50
                
                // Call option data
                callChain.put(strike, new OptionChainData(
                    strike > currentPrice ? 50 + Math.random() * 100 : 100 + Math.random() * 200, // Premium
                    (long)(100000 + Math.random() * 2000000), // Volume
                    (long)(50000 + Math.random() * 500000), // OI
                    15 + Math.random() * 20, // IV
                    0.3 + Math.random() * 0.4, // Delta
                    0.01 + Math.random() * 0.02, // Gamma
                    -0.05 + Math.random() * 0.1 // Theta
                ));
                
                // Put option data
                putChain.put(strike, new OptionChainData(
                    strike < currentPrice ? 50 + Math.random() * 100 : 100 + Math.random() * 200,
                    (long)(100000 + Math.random() * 2000000),
                    (long)(50000 + Math.random() * 500000),
                    15 + Math.random() * 20,
                    -0.3 - Math.random() * 0.4, // Negative delta for puts
                    0.01 + Math.random() * 0.02,
                    -0.05 + Math.random() * 0.1
                ));
            }
        }
        
        public List<Double> getCallStrikes() {
            return new ArrayList<>(callChain.keySet());
        }
        
        public List<Double> getPutStrikes() {
            return new ArrayList<>(putChain.keySet());
        }
        
        public OptionChainData getCallChainData(double strike) {
            return callChain.get(strike);
        }
        
        public OptionChainData getPutChainData(double strike) {
            return putChain.get(strike);
        }
        
        public int getTotalOptionsCount() {
            return callChain.size() + putChain.size();
        }
        
        // Getters
        public double getCurrentPrice() { return currentPrice; }
        public double getDayChangePercent() { return dayChangePercent; }
        public double getRSI() { return rsi; }
        public double getMACDSignal() { return macdSignal; }
        public double getSupport() { return support; }
        public double getResistance() { return resistance; }
    }
    
    /**
     * Option chain data for a specific strike
     */
    private static class OptionChainData {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        private final double premium;
        private final long volume;
        private final long openInterest;
        private final double impliedVolatility;
        private final double delta;
        private final double gamma;
        private final double theta;
        
        public OptionChainData(double premium, long volume, long openInterest, 
                              double impliedVolatility, double delta, double gamma, double theta) {
            this.premium = premium;
            this.volume = volume;
            this.openInterest = openInterest;
            this.impliedVolatility = impliedVolatility;
            this.delta = delta;
            this.gamma = gamma;
            this.theta = theta;
        }
        
        // Getters
        public double getPremium() { return premium; }
        public long getVolume() { return volume; }
        public long getOpenInterest() { return openInterest; }
        public double getImpliedVolatility() { return impliedVolatility; }
        public double getDelta() { return delta; }
        public double getGamma() { return gamma; }
        public double getTheta() { return theta; }
    }
}