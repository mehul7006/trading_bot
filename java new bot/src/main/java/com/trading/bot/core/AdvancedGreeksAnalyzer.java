package com.trading.bot.core;
import com.trading.bot.market.RealMarketDataProvider;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advanced Greeks and Volatility Analysis Tools
 * Point 3: Create additional analysis tools for options Greeks or volatility
 */
public class AdvancedGreeksAnalyzer {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();    private static final Logger logger = LoggerFactory.getLogger(AdvancedGreeksAnalyzer.class);
    
    private final Map<String, GreeksSnapshot> currentGreeks = new ConcurrentHashMap<>();
    private final Map<String, VolatilityProfile> volatilityProfiles = new ConcurrentHashMap<>();
    private final List<GreeksAlert> activeAlerts = new ArrayList<>();
    
    public AdvancedGreeksAnalyzer() {
        initializeGreeksMonitoring();
        logger.info("Advanced Greeks Analyzer initialized with real-time monitoring");
    }
    
    /**
     * Comprehensive Greeks Analysis for all indices
     */
    public void performComprehensiveGreeksAnalysis() {
        logger.info("🔬 === COMPREHENSIVE OPTIONS GREEKS ANALYSIS ===");
        
        List<String> indices = Arrays.asList("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY", "MIDCPNIFTY", "BANKEX");
        
        for (String index : indices) {
            analyzeIndexGreeks(index);
        }
        
        generateGreeksSummary();
        checkGreeksAlerts();
    }
    
    /**
     * Analyze Greeks for specific index
     */
    public void analyzeIndexGreeks(String index) {
        logger.info("\n📊 {} GREEKS ANALYSIS:", index);
        
        GreeksSnapshot snapshot = calculateGreeksSnapshot(index);
        currentGreeks.put(index, snapshot);
        
        // Delta Analysis
        analyzeDelta(index, snapshot);
        
        // Gamma Analysis
        analyzeGamma(index, snapshot);
        
        // Theta Analysis
        analyzeTheta(index, snapshot);
        
        // Vega Analysis
        analyzeVega(index, snapshot);
        
        // Greeks-based trading recommendations
        generateGreeksRecommendations(index, snapshot);
    }
    
    /**
     * Delta Analysis
     */
    private void analyzeDelta(String index, GreeksSnapshot snapshot) {
        logger.info("⚡ DELTA ANALYSIS:");
        logger.info("   ATM Call Delta: {:.3f} | ATM Put Delta: {:.3f}", 
                   snapshot.getAtmCallDelta(), snapshot.getAtmPutDelta());
        logger.info("   Total Portfolio Delta: {:.0f}", snapshot.getTotalDelta());
        
        // Delta interpretation
        if (Math.abs(snapshot.getTotalDelta()) > 500) {
            logger.info("   🚨 HIGH DELTA EXPOSURE - Consider hedging");
        } else if (Math.abs(snapshot.getTotalDelta()) < 100) {
            logger.info("   ✅ NEUTRAL DELTA - Well balanced portfolio");
        }
        
        // Delta-based signals
        if (snapshot.getAtmCallDelta() > 0.55) {
            logger.info("   📈 BULLISH BIAS - High call delta indicates upward momentum");
        } else if (snapshot.getAtmCallDelta() < 0.35) {
            logger.info("   📉 BEARISH BIAS - Low call delta indicates downward pressure");
        }
    }
    
    /**
     * Gamma Analysis
     */
    private void analyzeGamma(String index, GreeksSnapshot snapshot) {
        logger.info("🚀 GAMMA ANALYSIS:");
        logger.info("   ATM Gamma: {:.4f} | Gamma Exposure: {:.0f}", 
                   snapshot.getAtmGamma(), snapshot.getTotalGamma());
        
        if (snapshot.getAtmGamma() > 0.020) {
            logger.info("   ⚡ HIGH GAMMA - Expect rapid delta changes on moves");
            logger.info("   📊 Strategy: Gamma scalping opportunities available");
        } else if (snapshot.getAtmGamma() < 0.010) {
            logger.info("   🔒 LOW GAMMA - Stable delta, less responsive to moves");
        }
        
        // Gamma squeeze detection
        if (snapshot.getTotalGamma() > 1000) {
            logger.info("   🔥 GAMMA SQUEEZE POTENTIAL - Market makers may drive volatility");
        }
        
        // Best strikes for gamma plays
        Map<Double, Double> gammaByStrike = snapshot.getGammaByStrike();
        double maxGammaStrike = gammaByStrike.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0.0);
        logger.info("   🎯 HIGHEST GAMMA STRIKE: {} (Gamma: {:.4f})", 
                   maxGammaStrike, gammaByStrike.get(maxGammaStrike));
    }
    
    /**
     * Theta Analysis
     */
    private void analyzeTheta(String index, GreeksSnapshot snapshot) {
        logger.info("⏰ THETA ANALYSIS:");
        logger.info("   Daily Theta Decay: ₹{:.0f} | Weekly: ₹{:.0f}", 
                   snapshot.getDailyTheta(), snapshot.getWeeklyTheta());
        
        double daysToExpiry = snapshot.getDaysToExpiry();
        if (daysToExpiry <= 7) {
            logger.info("   🔥 HIGH THETA RISK - Weekly expiry, rapid time decay");
            logger.info("   ⚠️  Caution: Options losing ₹{:.0f} daily", Math.abs(snapshot.getDailyTheta()));
        } else if (daysToExpiry <= 30) {
            logger.info("   ⚡ MODERATE THETA - Monthly expiry, manageable decay");
        } else {
            logger.info("   ✅ LOW THETA - Long expiry, minimal daily impact");
        }
        
        // Theta strategies
        if (snapshot.getDailyTheta() < -500) {
            logger.info("   💡 THETA STRATEGY: Consider time spread or covered calls");
        }
    }
    
    /**
     * Vega Analysis
     */
    private void analyzeVega(String index, GreeksSnapshot snapshot) {
        logger.info("📈 VEGA ANALYSIS:");
        logger.info("   Portfolio Vega: {:.0f} | IV Sensitivity: ₹{:.0f} per 1% IV change", 
                   snapshot.getTotalVega(), snapshot.getTotalVega() * 100);
        
        double currentIV = snapshot.getCurrentIV();
        double ivPercentile = snapshot.getIvPercentile();
        
        logger.info("   Current IV: {:.1f}% | IV Percentile: {:.0f}%", currentIV, ivPercentile);
        
        if (ivPercentile > 80) {
            logger.info("   🔴 HIGH IV ENVIRONMENT - Consider selling premium");
            logger.info("   💡 STRATEGY: Iron condors, covered calls, cash-secured puts");
        } else if (ivPercentile < 20) {
            logger.info("   🟢 LOW IV ENVIRONMENT - Consider buying premium");
            logger.info("   💡 STRATEGY: Long straddles, long calls/puts");
        } else {
            logger.info("   🟡 NEUTRAL IV - Selective premium strategies");
        }
        
        // Vega risk assessment
        if (Math.abs(snapshot.getTotalVega()) > 2000) {
            logger.info("   🚨 HIGH VEGA RISK - 1% IV change = ₹{:.0f} P&L impact", 
                       Math.abs(snapshot.getTotalVega()) * 100);
        }
    }
    
    /**
     * Generate Greeks-based trading recommendations
     */
    private void generateGreeksRecommendations(String index, GreeksSnapshot snapshot) {
        logger.info("🎯 GREEKS-BASED RECOMMENDATIONS:");
        
        List<String> recommendations = new ArrayList<>();
        
        // Delta-based recommendations
        if (snapshot.getAtmCallDelta() > 0.55 && snapshot.getAtmGamma() > 0.015) {
            recommendations.add("Strong momentum trade: Buy ATM calls with high gamma");
        }
        
        // Gamma-based recommendations
        if (snapshot.getAtmGamma() > 0.020) {
            recommendations.add("Gamma scalping: Trade around ATM strikes for quick profits");
        }
        
        // Theta-based recommendations
        if (snapshot.getDaysToExpiry() <= 7 && snapshot.getDailyTheta() < -300) {
            recommendations.add("Time decay play: Sell premium in weekly expiry");
        }
        
        // Vega-based recommendations
        if (snapshot.getIvPercentile() > 75) {
            recommendations.add("High IV strategy: Iron butterfly or short straddle");
        } else if (snapshot.getIvPercentile() < 25) {
            recommendations.add("Low IV strategy: Long straddle or protective puts");
        }
        
        // Combined Greeks analysis
        if (snapshot.getAtmCallDelta() > 0.50 && snapshot.getAtmGamma() > 0.015 && snapshot.getIvPercentile() < 50) {
            recommendations.add("🔥 PERFECT SETUP: Bullish momentum + Low IV + High Gamma");
        }
        
        for (int i = 0; i < recommendations.size(); i++) {
            logger.info("   {}. {}", i + 1, recommendations.get(i));
        }
        
        if (recommendations.isEmpty()) {
            logger.info("   No specific Greeks-based opportunities at current levels");
        }
    }
    
    /**
     * Advanced Volatility Surface Analysis
     */
    public void analyzeVolatilitySurface(String index) {
        logger.info("\n🌊 {} VOLATILITY SURFACE ANALYSIS:", index);
        
        VolatilityProfile profile = calculateVolatilityProfile(index);
        volatilityProfiles.put(index, profile);
        
        // Term structure analysis
        analyzeVolatilityTermStructure(profile);
        
        // Skew analysis
        analyzeVolatilitySkew(profile);
        
        // Smile analysis
        analyzeVolatilitySmile(profile);
        
        // Historical vs implied volatility
        analyzeHistoricalVsImplied(profile);
    }
    
    private void analyzeVolatilityTermStructure(VolatilityProfile profile) {
        logger.info("📊 VOLATILITY TERM STRUCTURE:");
        
        Map<Integer, Double> termStructure = profile.getTermStructure();
        logger.info("   1 Week: {:.1f}% | 1 Month: {:.1f}% | 3 Month: {:.1f}%", 
                   termStructure.get(7), termStructure.get(30), termStructure.get(90));
        
        // Contango vs Backwardation
        double weeklyIV = termStructure.get(7);
        double monthlyIV = termStructure.get(30);
        
        if (weeklyIV > monthlyIV + 2) {
            logger.info("   📈 BACKWARDATION - Near term events expected");
            logger.info("   💡 STRATEGY: Calendar spreads (sell near, buy far)");
        } else if (monthlyIV > weeklyIV + 2) {
            logger.info("   📉 CONTANGO - Normal volatility curve");
            logger.info("   💡 STRATEGY: Reverse calendar spreads");
        } else {
            logger.info("   ➡️  FLAT CURVE - Neutral volatility expectations");
        }
    }
    
    private void analyzeVolatilitySkew(VolatilityProfile profile) {
        logger.info("🔄 VOLATILITY SKEW ANALYSIS:");
        
        double callSkew = profile.getCallSkew();
        double putSkew = profile.getPutSkew();
        
        logger.info("   Call Skew: {:.1f}% | Put Skew: {:.1f}%", callSkew, putSkew);
        
        if (putSkew > callSkew + 3) {
            logger.info("   📉 PUT SKEW - Downside protection expensive");
            logger.info("   💡 STRATEGY: Sell put spreads, buy call spreads");
        } else if (callSkew > putSkew + 3) {
            logger.info("   📈 CALL SKEW - Upside protection expensive");
            logger.info("   💡 STRATEGY: Sell call spreads, buy put spreads");
        } else {
            logger.info("   ⚖️  BALANCED SKEW - No significant directional bias");
        }
    }
    
    private void analyzeVolatilitySmile(VolatilityProfile profile) {
        logger.info("😊 VOLATILITY SMILE ANALYSIS:");
        
        double atmIV = profile.getAtmIV();
        double otmCallIV = profile.getOtmCallIV();
        double otmPutIV = profile.getOtmPutIV();
        
        logger.info("   OTM Put IV: {:.1f}% | ATM IV: {:.1f}% | OTM Call IV: {:.1f}%", 
                   otmPutIV, atmIV, otmCallIV);
        
        // Smile shape analysis
        if (otmPutIV > atmIV + 2 && otmCallIV > atmIV + 2) {
            logger.info("   😊 VOLATILITY SMILE - Classic pattern");
            logger.info("   💡 STRATEGY: Sell wings, buy body (iron butterfly)");
        } else if (otmPutIV > atmIV + 3) {
            logger.info("   📉 VOLATILITY SMIRK - Fear premium in puts");
            logger.info("   💡 STRATEGY: Put spread strategies");
        }
    }
    
    private void analyzeHistoricalVsImplied(VolatilityProfile profile) {
        logger.info("📈 HISTORICAL vs IMPLIED VOLATILITY:");
        
        double historicalVol = profile.getHistoricalVolatility();
        double impliedVol = profile.getImpliedVolatility();
        double volRatio = impliedVol / historicalVol;
        
        logger.info("   Historical Vol: {:.1f}% | Implied Vol: {:.1f}% | Ratio: {:.2f}", 
                   historicalVol, impliedVol, volRatio);
        
        if (volRatio > 1.2) {
            logger.info("   📈 IV > HV - Options expensive, volatility mean reversion likely");
            logger.info("   💡 STRATEGY: Sell premium strategies (iron condors, short strangles)");
        } else if (volRatio < 0.8) {
            logger.info("   📉 IV < HV - Options cheap, volatility expansion likely");
            logger.info("   💡 STRATEGY: Buy premium strategies (long straddles, protective puts)");
        } else {
            logger.info("   ⚖️  IV ≈ HV - Fair value pricing");
        }
    }
    
    /**
     * Greeks Alerts System
     */
    public void checkGreeksAlerts() {
        logger.info("\n🚨 GREEKS ALERTS SYSTEM:");
        
        activeAlerts.clear();
        
        for (Map.Entry<String, GreeksSnapshot> entry : currentGreeks.entrySet()) {
            String index = entry.getKey();
            GreeksSnapshot snapshot = entry.getValue();
            
            // High gamma alert
            if (snapshot.getAtmGamma() > 0.025) {
                activeAlerts.add(new GreeksAlert(index, "HIGH_GAMMA", 
                    String.format("ATM Gamma %.4f - Expect volatile price action", snapshot.getAtmGamma())));
            }
            
            // High theta decay alert
            if (snapshot.getDailyTheta() < -1000) {
                activeAlerts.add(new GreeksAlert(index, "HIGH_THETA", 
                    String.format("Daily decay ₹%.0f - Time working against long positions", Math.abs(snapshot.getDailyTheta()))));
            }
            
            // Extreme delta alert
            if (Math.abs(snapshot.getTotalDelta()) > 1000) {
                activeAlerts.add(new GreeksAlert(index, "EXTREME_DELTA", 
                    String.format("Portfolio delta %.0f - High directional risk", snapshot.getTotalDelta())));
            }
            
            // High vega risk alert
            if (Math.abs(snapshot.getTotalVega()) > 3000) {
                activeAlerts.add(new GreeksAlert(index, "HIGH_VEGA", 
                    String.format("Vega exposure %.0f - Sensitive to IV changes", snapshot.getTotalVega())));
            }
        }
        
        if (!activeAlerts.isEmpty()) {
            logger.info("🔔 ACTIVE ALERTS:");
            for (int i = 0; i < activeAlerts.size(); i++) {
                GreeksAlert alert = activeAlerts.get(i);
                logger.info("   {}. {} [{}]: {}", i + 1, alert.getIndex(), alert.getType(), alert.getMessage());
            }
        } else {
            logger.info("✅ No Greeks alerts at current levels");
        }
    }
    
    /**
     * Generate comprehensive Greeks summary
     */
    private void generateGreeksSummary() {
        logger.info("\n📋 === GREEKS ANALYSIS SUMMARY ===");
        
        double totalPortfolioDelta = currentGreeks.values().stream()
                .mapToDouble(GreeksSnapshot::getTotalDelta)
                .sum();
        
        double totalPortfolioGamma = currentGreeks.values().stream()
                .mapToDouble(GreeksSnapshot::getTotalGamma)
                .sum();
        
        double totalPortfolioTheta = currentGreeks.values().stream()
                .mapToDouble(GreeksSnapshot::getDailyTheta)
                .sum();
        
        double totalPortfolioVega = currentGreeks.values().stream()
                .mapToDouble(GreeksSnapshot::getTotalVega)
                .sum();
        
        logger.info("📊 PORTFOLIO GREEKS:");
        logger.info("   Total Delta: {:.0f} | Total Gamma: {:.0f}", totalPortfolioDelta, totalPortfolioGamma);
        logger.info("   Daily Theta: ₹{:.0f} | Total Vega: {:.0f}", totalPortfolioTheta, totalPortfolioVega);
        
        // Risk assessment
        logger.info("\n⚠️  RISK ASSESSMENT:");
        if (Math.abs(totalPortfolioDelta) > 2000) {
            logger.info("   🔴 HIGH DIRECTIONAL RISK - Consider delta hedging");
        }
        if (totalPortfolioGamma > 3000) {
            logger.info("   🟡 HIGH GAMMA EXPOSURE - Expect rapid P&L swings");
        }
        if (totalPortfolioTheta < -2000) {
            logger.info("   🟠 HIGH TIME DECAY - ₹{:.0f} daily loss if static", Math.abs(totalPortfolioTheta));
        }
        if (Math.abs(totalPortfolioVega) > 5000) {
            logger.info("   🟣 HIGH VOLATILITY RISK - 1% IV change = ₹{:.0f}", Math.abs(totalPortfolioVega) * 100);
        }
        
        // Overall portfolio health
        logger.info("\n💡 PORTFOLIO RECOMMENDATIONS:");
        if (Math.abs(totalPortfolioDelta) < 500 && totalPortfolioGamma < 1000) {
            logger.info("   ✅ WELL-BALANCED portfolio with manageable Greeks");
        } else {
            logger.info("   ⚠️  CONSIDER HEDGING to reduce Greeks exposure");
        }
    }
    
    // Helper methods and data structures
    
    private void initializeGreeksMonitoring() {
        // Initialize Greeks monitoring for all indices
        List<String> indices = Arrays.asList("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY", "MIDCPNIFTY", "BANKEX");
        for (String index : indices) {
            currentGreeks.put(index, new GreeksSnapshot(index));
            volatilityProfiles.put(index, new VolatilityProfile(index));
        }
    }
    
    private GreeksSnapshot calculateGreeksSnapshot(String index) {
        // In real implementation, this would fetch from option chain APIs
        return new GreeksSnapshot(index);
    }
    
    private VolatilityProfile calculateVolatilityProfile(String index) {
        // In real implementation, this would calculate from market data
        return new VolatilityProfile(index);
    }
    
    // Data classes
    
    public static class GreeksSnapshot {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        private final String index;
        private final double atmCallDelta;
        private final double atmPutDelta;
        private final double atmGamma;
        private final double totalDelta;
        private final double totalGamma;
        private final double dailyTheta;
        private final double weeklyTheta;
        private final double totalVega;
        private final double currentIV;
        private final double ivPercentile;
        private final double daysToExpiry;
        private final Map<Double, Double> gammaByStrike;
        
        public GreeksSnapshot(String index) {
            this.index = index;
            // Mock data - in real implementation, calculate from option chain
            this.atmCallDelta = 0.45 + Math.random() * 0.20; // 0.45-0.65
            this.atmPutDelta = -0.45 - Math.random() * 0.20; // -0.45 to -0.65
            this.atmGamma = 0.010 + Math.random() * 0.020; // 0.010-0.030
            this.totalDelta = -500 + Math.random() * 1000; // -500 to +500
            this.totalGamma = 500 + Math.random() * 1500; // 500-2000
            this.dailyTheta = -200 - Math.random() * 800; // -200 to -1000
            this.weeklyTheta = dailyTheta * 7;
            this.totalVega = 1000 + Math.random() * 2000; // 1000-3000
            this.currentIV = 15 + Math.random() * 15; // 15-30%
            this.ivPercentile = Math.random() * 100; // 0-100%
            this.daysToExpiry = 1 + Math.random() * 29; // 1-30 days
            
            // Generate gamma by strike
            this.gammaByStrike = new HashMap<>();
            for (int i = -5; i <= 5; i++) {
                double strike = getBaseStrike(index) + (i * 100);
                double gamma = atmGamma * Math.exp(-Math.abs(i) * 0.3);
                gammaByStrike.put(strike, gamma);
            }
        }
        
        private double getBaseStrike(String index) {
            switch (index) {
                case "NIFTY": return realData.getRealPrice("NIFTY");
                case "BANKNIFTY": return 44500;
                case "SENSEX": return 65500;
                case "FINNIFTY": return 19750;
                case "MIDCPNIFTY": return 10850;
                case "BANKEX": return 48200;
                default: return 20000;
            }
        }
        
        // Getters
        public String getIndex() { return index; }
        public double getAtmCallDelta() { return atmCallDelta; }
        public double getAtmPutDelta() { return atmPutDelta; }
        public double getAtmGamma() { return atmGamma; }
        public double getTotalDelta() { return totalDelta; }
        public double getTotalGamma() { return totalGamma; }
        public double getDailyTheta() { return dailyTheta; }
        public double getWeeklyTheta() { return weeklyTheta; }
        public double getTotalVega() { return totalVega; }
        public double getCurrentIV() { return currentIV; }
        public double getIvPercentile() { return ivPercentile; }
        public double getDaysToExpiry() { return daysToExpiry; }
        public Map<Double, Double> getGammaByStrike() { return gammaByStrike; }
    }
    
    public static class VolatilityProfile {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        private final String index;
        private final Map<Integer, Double> termStructure;
        private final double callSkew;
        private final double putSkew;
        private final double atmIV;
        private final double otmCallIV;
        private final double otmPutIV;
        private final double historicalVolatility;
        private final double impliedVolatility;
        
        public VolatilityProfile(String index) {
            this.index = index;
            
            // Mock volatility data
            double baseIV = 18 + Math.random() * 12; // 18-30%
            this.atmIV = baseIV;
            this.impliedVolatility = baseIV;
            this.historicalVolatility = baseIV * (0.8 + Math.random() * 0.4); // 80-120% of IV
            
            // Term structure
            this.termStructure = new HashMap<>();
            termStructure.put(7, baseIV + Math.random() * 5 - 2.5); // Weekly
            termStructure.put(30, baseIV); // Monthly (base)
            termStructure.put(90, baseIV - Math.random() * 3); // Quarterly
            
            // Skew
            this.callSkew = baseIV + Math.random() * 3;
            this.putSkew = baseIV + 2 + Math.random() * 4; // Put skew typically higher
            
            // Smile
            this.otmCallIV = baseIV + 1 + Math.random() * 2;
            this.otmPutIV = baseIV + 2 + Math.random() * 3;
        }
        
        // Getters
        public String getIndex() { return index; }
        public Map<Integer, Double> getTermStructure() { return termStructure; }
        public double getCallSkew() { return callSkew; }
        public double getPutSkew() { return putSkew; }
        public double getAtmIV() { return atmIV; }
        public double getOtmCallIV() { return otmCallIV; }
        public double getOtmPutIV() { return otmPutIV; }
        public double getHistoricalVolatility() { return historicalVolatility; }
        public double getImpliedVolatility() { return impliedVolatility; }
    }
    
    public static class GreeksAlert {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        private final String index;
        private final String type;
        private final String message;
        private final LocalDateTime timestamp;
        
        public GreeksAlert(String index, String type, String message) {
            this.index = index;
            this.type = type;
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters
        public String getIndex() { return index; }
        public String getType() { return type; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}