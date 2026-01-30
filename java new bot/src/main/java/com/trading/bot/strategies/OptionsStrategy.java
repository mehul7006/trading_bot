package com.trading.bot.strategies;

import java.time.LocalDateTime;
import java.util.*;

/**
 * PROFESSIONAL OPTIONS STRATEGY CLASS
 * Represents institutional-grade options strategies
 */
public class OptionsStrategy {
    
    private final String name;
    private final String type;
    private final String underlying;
    private final double probabilityOfProfit;
    private final double riskRewardRatio;
    private final double impliedVolatility;
    private final int daysToExpiry;
    private final double maxLoss;
    private final double maxProfit;
    private final double breakeven;
    private final GreeksData greeks;
    private final List<String> reasoning;
    private final LocalDateTime timestamp;
    private final String riskLevel;
    
    private OptionsStrategy(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.underlying = builder.underlying;
        this.probabilityOfProfit = builder.probabilityOfProfit;
        this.riskRewardRatio = builder.riskRewardRatio;
        this.impliedVolatility = builder.impliedVolatility;
        this.daysToExpiry = builder.daysToExpiry;
        this.maxLoss = builder.maxLoss;
        this.maxProfit = builder.maxProfit;
        this.breakeven = builder.breakeven;
        this.greeks = builder.greeks;
        this.reasoning = new ArrayList<>(builder.reasoning);
        this.timestamp = LocalDateTime.now();
        this.riskLevel = determineRiskLevel();
    }
    
    // Getters
    public String getName() { return name; }
    public String getType() { return type; }
    public String getUnderlying() { return underlying; }
    public double getProbabilityOfProfit() { return probabilityOfProfit; }
    public double getRiskRewardRatio() { return riskRewardRatio; }
    public double getImpliedVolatility() { return impliedVolatility; }
    public int getDaysToExpiry() { return daysToExpiry; }
    public double getMaxLoss() { return maxLoss; }
    public double getMaxProfit() { return maxProfit; }
    public double getBreakeven() { return breakeven; }
    public GreeksData getGreeks() { return greeks; }
    public List<String> getReasoning() { return new ArrayList<>(reasoning); }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getRiskLevel() { return riskLevel; }
    
    public double getExpectedProfitability() {
        return (probabilityOfProfit / 100.0) * maxProfit + 
               ((100 - probabilityOfProfit) / 100.0) * (-maxLoss);
    }
    
    public boolean hasValidGreeks() {
        return greeks != null && 
               !Double.isNaN(greeks.delta) && 
               !Double.isNaN(greeks.gamma) &&
               !Double.isNaN(greeks.theta) && 
               !Double.isNaN(greeks.vega);
    }
    
    private String determineRiskLevel() {
        if (maxLoss == Double.POSITIVE_INFINITY) return "VERY_HIGH";
        if (riskRewardRatio > 3.0 && probabilityOfProfit > 70) return "LOW";
        if (riskRewardRatio > 2.0 && probabilityOfProfit > 65) return "MEDIUM";
        if (riskRewardRatio > 1.5 && probabilityOfProfit > 60) return "HIGH";
        return "VERY_HIGH";
    }
    
    @Override
    public String toString() {
        return String.format("%s: %s | P(Profit): %.1f%% | R:R: %.2f | Risk: %s", 
            name, underlying, probabilityOfProfit, riskRewardRatio, riskLevel);
    }
    
    // Builder pattern for complex strategy creation
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name;
        private String type;
        private String underlying;
        private double probabilityOfProfit = 50.0;
        private double riskRewardRatio = 1.0;
        private double impliedVolatility = 20.0;
        private int daysToExpiry = 30;
        private double maxLoss = 0;
        private double maxProfit = 0;
        private double breakeven = 0;
        private GreeksData greeks;
        private List<String> reasoning = new ArrayList<>();
        
        public Builder name(String name) { this.name = name; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder underlying(String underlying) { this.underlying = underlying; return this; }
        public Builder probability(double prob) { this.probabilityOfProfit = prob; return this; }
        public Builder riskReward(double rr) { this.riskRewardRatio = rr; return this; }
        public Builder impliedVol(double iv) { this.impliedVolatility = iv; return this; }
        public Builder daysToExpiry(int days) { this.daysToExpiry = days; return this; }
        public Builder maxLoss(double loss) { this.maxLoss = loss; return this; }
        public Builder maxProfit(double profit) { this.maxProfit = profit; return this; }
        public Builder breakeven(double be) { this.breakeven = be; return this; }
        public Builder greeks(GreeksData greeks) { this.greeks = greeks; return this; }
        public Builder addReasoning(String reason) { this.reasoning.add(reason); return this; }
        
        public OptionsStrategy build() {
            Objects.requireNonNull(name, "Strategy name is required");
            Objects.requireNonNull(type, "Strategy type is required");
            Objects.requireNonNull(underlying, "Underlying is required");
            return new OptionsStrategy(this);
        }
    }
}

// Supporting data classes
class GreeksData {
    public final double delta;
    public final double gamma;
    public final double theta;
    public final double vega;
    public final double rho;
    
    public GreeksData(double delta, double gamma, double theta, double vega, double rho) {
        this.delta = delta;
        this.gamma = gamma;
        this.theta = theta;
        this.vega = vega;
        this.rho = rho;
    }
}

class RealOptionsData {
    public final String underlying;
    public final double strike;
    public final String optionType; // CE or PE
    public final double price;
    public final double impliedVolatility;
    public final double timeToExpiry;
    
    public RealOptionsData(String underlying, double strike, String optionType, 
                          double price, double impliedVolatility, double timeToExpiry) {
        this.underlying = underlying;
        this.strike = strike;
        this.optionType = optionType;
        this.price = price;
        this.impliedVolatility = impliedVolatility;
        this.timeToExpiry = timeToExpiry;
    }
}

// Additional supporting classes for strategy analysis
class MomentumAnalysis {
    public final double roc5;
    public final double roc10;
    public final double roc20;
    public final String regime;
    
    public MomentumAnalysis(double roc5, double roc10, double roc20, String regime) {
        this.roc5 = roc5;
        this.roc10 = roc10;
        this.roc20 = roc20;
        this.regime = regime;
    }
    
    public boolean isStrongBullishMomentum() {
        return regime.equals("STRONG_BULLISH_MOMENTUM");
    }
    
    public boolean isStrongBearishMomentum() {
        return regime.equals("STRONG_BEARISH_MOMENTUM");
    }
}

class VolatilitySpread {
    public final double impliedVol;
    public final double historicalVol;
    public final double realizedVol;
    
    public VolatilitySpread(double impliedVol, double historicalVol, double realizedVol) {
        this.impliedVol = impliedVol;
        this.historicalVol = historicalVol;
        this.realizedVol = realizedVol;
    }
    
    public boolean isImpliedVolatilityUndervalued() {
        return impliedVol < historicalVol * 0.8;
    }
    
    public boolean isImpliedVolatilityOvervalued() {
        return impliedVol > historicalVol * 1.2;
    }
}

class SupportResistanceLevels {
    public final double support;
    public final double resistance;
    public final double strongSupport;
    public final double strongResistance;
    
    public SupportResistanceLevels(double support, double resistance, 
                                  double strongSupport, double strongResistance) {
        this.support = support;
        this.resistance = resistance;
        this.strongSupport = strongSupport;
        this.strongResistance = strongResistance;
    }
}

class BollingerBands {
    public final double middle;
    public final double upperBand;
    public final double lowerBand;
    
    public BollingerBands(double middle, double upperBand, double lowerBand) {
        this.middle = middle;
        this.upperBand = upperBand;
        this.lowerBand = lowerBand;
    }
}

class MarketEvent {
    public final String name;
    public final java.time.LocalDate date;
    public final boolean isHighImpact;
    
    public MarketEvent(String name, java.time.LocalDate date, boolean isHighImpact) {
        this.name = name;
        this.date = date;
        this.isHighImpact = isHighImpact;
    }
    
    public long isDaysUntilEvent() {
        return java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), date);
    }
    
    public boolean isHighImpactEvent() {
        return isHighImpact;
    }
}

class ArbitrageOpportunity {
    public final String type;
    public final double expectedProfit;
    public final double riskExposure;
    
    public ArbitrageOpportunity(String type, double expectedProfit, double riskExposure) {
        this.type = type;
        this.expectedProfit = expectedProfit;
        this.riskExposure = riskExposure;
    }
    
    public boolean isProfitable() {
        return expectedProfit > 0.01; // At least 1 point profit
    }
}

class StrategyRecommendations {
    private final String index;
    private final List<OptionsStrategy> strategies;
    private final LocalDateTime generatedAt;
    
    public StrategyRecommendations(String index, List<OptionsStrategy> strategies, LocalDateTime generatedAt) {
        this.index = index;
        this.strategies = new ArrayList<>(strategies);
        this.generatedAt = generatedAt;
    }
    
    public String getIndex() { return index; }
    public List<OptionsStrategy> getStrategies() { return new ArrayList<>(strategies); }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    
    public List<OptionsStrategy> getTopStrategies(int count) {
        return strategies.stream()
            .sorted((a, b) -> Double.compare(b.getExpectedProfitability(), a.getExpectedProfitability()))
            .limit(count)
            .collect(java.util.stream.Collectors.toList());
    }
}