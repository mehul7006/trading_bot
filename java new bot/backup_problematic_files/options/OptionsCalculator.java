import org.apache.commons.math3.distribution.NormalDistribution;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Options Calculator for computing options-specific metrics and Greeks
 */
public class OptionsCalculator {
    private static final double RISK_FREE_RATE = 0.02; // 2% risk-free rate
    private final NormalDistribution normalDist;
    
    public OptionsCalculator() {
        this.normalDist = new NormalDistribution(0, 1);
    }
    
    public OptionsMetrics calculateMetrics(
            MarketData marketData,
            MLMarketAnalyzer.MarketPrediction marketPred,
            EnhancedOptionsCallGenerator.OptionType type) {
        
        // Calculate Implied Volatility
        double impliedVol = calculateImpliedVolatility(marketData);
        
        // Calculate Volume/OI ratio
        double volumeOI = calculateVolumeOIRatio(marketData);
        
        // Calculate Put/Call ratio
        double putCallRatio = calculatePutCallRatio(marketData);
        
        // Calculate option premium boundaries
        double currentPrice = marketData.getPrice();
        double baseVol = marketData.getVolatility();
        
        double premium = calculateOptionPremium(
            currentPrice,
            currentPrice, // ATM strike
            30, // 30-day expiry
            impliedVol,
            type
        );
        
        // Maximum acceptable premium
        double maxPremium = premium * 1.5;
        
        // Calculate theta impact
        double thetaImpact = calculateThetaImpact(
            currentPrice,
            currentPrice,
            30,
            impliedVol
        );
        
        // Maximum acceptable theta
        double maxTheta = 0.15; // 15% maximum daily decay
        
        // Calculate bid-ask spread
        double spread = calculateSpread(marketData, premium);
        
        return new OptionsMetrics(
            impliedVol,
            volumeOI,
            putCallRatio,
            premium,
            maxPremium,
            thetaImpact,
            maxTheta,
            spread
        );
    }
    
    public EnhancedOptionsCallGenerator.Greeks calculateGreeks(
            double spotPrice,
            double strikePrice,
            LocalDate expiry,
            double impliedVol) {
        
        double timeToExpiry = ChronoUnit.DAYS.between(LocalDate.now(), expiry) / 365.0;
        
        // Calculate d1 and d2
        double d1 = calculateD1(spotPrice, strikePrice, timeToExpiry, impliedVol);
        double d2 = d1 - impliedVol * Math.sqrt(timeToExpiry);
        
        // Calculate Greeks
        double delta = calculateDelta(d1);
        double gamma = calculateGamma(spotPrice, d1, impliedVol, timeToExpiry);
        double theta = calculateTheta(spotPrice, strikePrice, d1, d2, impliedVol, timeToExpiry);
        double vega = calculateVega(spotPrice, d1, timeToExpiry);
        double rho = calculateRho(strikePrice, timeToExpiry, d2);
        
        return new EnhancedOptionsCallGenerator.Greeks(
            delta, gamma, theta, vega, rho
        );
    }
    
    private double calculateImpliedVolatility(MarketData data) {
        // Start with historical volatility as base
        double histVol = data.getVolatility();
        
        // Adjust based on recent price movements
        double[] prices = data.getPriceHistory();
        double recentVol = calculateRecentVolatility(prices);
        
        // Weight recent volatility more heavily
        return (histVol * 0.4) + (recentVol * 0.6);
    }
    
    private double calculateRecentVolatility(double[] prices) {
        if (prices.length < 2) return 0.0;
        
        double sum = 0.0;
        for (int i = 1; i < prices.length; i++) {
            double return_ = Math.log(prices[i] / prices[i-1]);
            sum += return_ * return_;
        }
        
        double variance = sum / (prices.length - 1);
        return Math.sqrt(variance * 252); // Annualized
    }
    
    private double calculateVolumeOIRatio(MarketData data) {
        // Simplified calculation - replace with actual options volume/OI data
        double volume = data.getVolume();
        double avgVolume = calculateAverageVolume(data.getVolumeHistory());
        return volume / avgVolume;
    }
    
    private double calculateAverageVolume(double[] volumes) {
        return Arrays.stream(volumes).average().orElse(0.0);
    }
    
    private double calculatePutCallRatio(MarketData data) {
        // Simplified calculation - replace with actual put/call volume data
        return 1.0; // Neutral ratio
    }
    
    private double calculateOptionPremium(
            double spotPrice,
            double strikePrice,
            int daysToExpiry,
            double impliedVol,
            EnhancedOptionsCallGenerator.OptionType type) {
        
        double timeToExpiry = daysToExpiry / 365.0;
        
        // Calculate d1 and d2
        double d1 = calculateD1(spotPrice, strikePrice, timeToExpiry, impliedVol);
        double d2 = d1 - impliedVol * Math.sqrt(timeToExpiry);
        
        // Calculate premium based on Black-Scholes
        double premium = switch(type) {
            case CALL -> {
                yield spotPrice * normalDist.cumulativeProbability(d1) -
                      strikePrice * Math.exp(-RISK_FREE_RATE * timeToExpiry) *
                      normalDist.cumulativeProbability(d2);
            }
            case PUT -> {
                yield strikePrice * Math.exp(-RISK_FREE_RATE * timeToExpiry) *
                      normalDist.cumulativeProbability(-d2) -
                      spotPrice * normalDist.cumulativeProbability(-d1);
            }
        };
        
        return Math.max(premium, 0.01); // Minimum premium of $0.01
    }
    
    private double calculateD1(
            double spotPrice,
            double strikePrice,
            double timeToExpiry,
            double impliedVol) {
        
        return (Math.log(spotPrice / strikePrice) +
                (RISK_FREE_RATE + impliedVol * impliedVol / 2) * timeToExpiry) /
               (impliedVol * Math.sqrt(timeToExpiry));
    }
    
    private double calculateDelta(double d1) {
        return normalDist.cumulativeProbability(d1);
    }
    
    private double calculateGamma(
            double spotPrice,
            double d1,
            double impliedVol,
            double timeToExpiry) {
        
        return normalDist.density(d1) /
               (spotPrice * impliedVol * Math.sqrt(timeToExpiry));
    }
    
    private double calculateTheta(
            double spotPrice,
            double strikePrice,
            double d1,
            double d2,
            double impliedVol,
            double timeToExpiry) {
        
        return -(spotPrice * impliedVol * normalDist.density(d1)) /
               (2 * Math.sqrt(timeToExpiry)) -
               RISK_FREE_RATE * strikePrice *
               Math.exp(-RISK_FREE_RATE * timeToExpiry) *
               normalDist.cumulativeProbability(d2);
    }
    
    private double calculateVega(
            double spotPrice,
            double d1,
            double timeToExpiry) {
        
        return spotPrice * Math.sqrt(timeToExpiry) * normalDist.density(d1);
    }
    
    private double calculateRho(
            double strikePrice,
            double timeToExpiry,
            double d2) {
        
        return strikePrice * timeToExpiry *
               Math.exp(-RISK_FREE_RATE * timeToExpiry) *
               normalDist.cumulativeProbability(d2);
    }
    
    private double calculateThetaImpact(
            double spotPrice,
            double strikePrice,
            int daysToExpiry,
            double impliedVol) {
        
        double timeToExpiry = daysToExpiry / 365.0;
        
        // Calculate theta for current day
        double d1 = calculateD1(spotPrice, strikePrice, timeToExpiry, impliedVol);
        double d2 = d1 - impliedVol * Math.sqrt(timeToExpiry);
        double currentTheta = calculateTheta(spotPrice, strikePrice, d1, d2, impliedVol, timeToExpiry);
        
        // Calculate normalized theta impact (as percentage of option price)
        double optionPrice = calculateOptionPremium(
            spotPrice,
            strikePrice,
            daysToExpiry,
            impliedVol,
            EnhancedOptionsCallGenerator.OptionType.CALL
        );
        
        return Math.abs(currentTheta / optionPrice);
    }
    
    private double calculateSpread(MarketData data, double premium) {
        // Estimate spread based on volume and volatility
        double baseSpread = 0.05; // 5% base spread
        
        // Adjust for volume
        double volumeFactor = Math.min(1.0, data.getVolume() / 100000.0);
        
        // Adjust for volatility
        double volFactor = Math.min(2.0, data.getVolatility() / 30.0);
        
        return baseSpread * (2.0 - volumeFactor) * volFactor;
    }
}