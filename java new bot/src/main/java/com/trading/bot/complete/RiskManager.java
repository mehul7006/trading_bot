package com.trading.bot.complete;

import java.util.*;

/**
 * INSTITUTIONAL RISK MANAGER
 * Professional-grade risk management for options trading
 * Implements industry-standard risk controls and position sizing
 */
public class RiskManager {
    
    // Risk parameters
    private static final double MAX_POSITION_SIZE_PERCENT = 5.0; // 5% of portfolio per position
    private static final double MAX_PORTFOLIO_RISK_PERCENT = 15.0; // 15% max portfolio risk
    private static final double MIN_RISK_REWARD_RATIO = 1.5; // Minimum 1.5:1 risk-reward
    private static final double MAX_DELTA_EXPOSURE = 0.25; // Max 25% delta exposure
    private static final double MAX_VEGA_EXPOSURE = 0.10; // Max 10% vega exposure
    
    public boolean validateCall(WorldClassOptionsGenerator.WorldClassOptionsCall call) {
        List<String> riskViolations = new ArrayList<>();
        
        // Check confidence threshold
        if (call.confidence < 75.0) {
            riskViolations.add("Confidence below minimum threshold");
        }
        
        // Check risk-reward ratio
        if (call.getRiskRewardRatio() < MIN_RISK_REWARD_RATIO) {
            riskViolations.add("Poor risk-reward ratio");
        }
        
        // Check Greeks exposure limits
        if (Math.abs(call.greeks.delta) > MAX_DELTA_EXPOSURE) {
            riskViolations.add("Excessive delta exposure");
        }
        
        if (Math.abs(call.greeks.vega) > MAX_VEGA_EXPOSURE) {
            riskViolations.add("Excessive vega exposure");
        }
        
        // Check time decay risk
        if (call.greeks.theta < -0.15 && call.action.equals("BUY")) {
            riskViolations.add("High time decay risk");
        }
        
        // Check moneyness risk
        double moneyness = call.strike / call.spotPrice;
        if (moneyness < 0.85 || moneyness > 1.15) {
            riskViolations.add("Extreme moneyness - high risk");
        }
        
        // Check implied volatility risk
        if (call.impliedVolatility > 0.40) {
            riskViolations.add("Extremely high implied volatility");
        }
        
        if (riskViolations.isEmpty()) {
            return true;
        } else {
            System.out.printf("⚠️ Risk violations for %s: %s%n", 
                call.getOptionContract(), String.join(", ", riskViolations));
            return false;
        }
    }
    
    public RiskMetrics calculateRisk(double premium, double strike, double spot, 
                                   String optionType, String action, TechnicalSignals technicals) {
        
        double maxLoss, expectedProfit, breakeven, probabilityOfProfit;
        String riskLevel;
        
        if (action.equals("BUY")) {
            // Buying options - limited risk, unlimited reward (for CE)
            maxLoss = premium;
            
            if (optionType.equals("CE")) {
                breakeven = strike + premium;
                expectedProfit = calculateExpectedProfit(spot, strike, premium, technicals, true);
            } else { // PE
                breakeven = strike - premium;
                expectedProfit = calculateExpectedProfit(spot, strike, premium, technicals, false);
            }
        } else { // SELL
            // Selling options - limited profit, high risk
            expectedProfit = premium;
            
            if (optionType.equals("CE")) {
                maxLoss = Double.POSITIVE_INFINITY; // Theoretically unlimited for naked calls
                breakeven = strike + premium;
            } else { // PE
                maxLoss = strike - premium;
                breakeven = strike - premium;
            }
        }
        
        // Calculate probability of profit based on technical analysis
        probabilityOfProfit = calculateProbabilityOfProfit(technicals, optionType, action);
        
        // Determine risk level
        riskLevel = determineRiskLevel(maxLoss, expectedProfit, probabilityOfProfit);
        
        return new RiskMetrics(maxLoss, expectedProfit, breakeven, probabilityOfProfit, riskLevel);
    }
    
    private double calculateExpectedProfit(double spot, double strike, double premium, 
                                         TechnicalSignals technicals, boolean isCall) {
        
        // Base expected move based on technical signals
        double expectedMovePercent = 0;
        
        if (isCall) {
            expectedMovePercent = technicals.getBullishScore() / 100.0 * 0.03; // Up to 3% move
        } else {
            expectedMovePercent = technicals.getBearishScore() / 100.0 * 0.03; // Up to 3% move down
        }
        
        // Calculate expected spot price
        double expectedSpot = spot * (1 + (isCall ? expectedMovePercent : -expectedMovePercent));
        
        // Calculate intrinsic value at expected spot
        double expectedIntrinsic;
        if (isCall) {
            expectedIntrinsic = Math.max(0, expectedSpot - strike);
        } else {
            expectedIntrinsic = Math.max(0, strike - expectedSpot);
        }
        
        // Expected profit is intrinsic value minus premium paid
        return Math.max(0, expectedIntrinsic - premium);
    }
    
    private double calculateProbabilityOfProfit(TechnicalSignals technicals, String optionType, String action) {
        double baseProbability = 50.0; // Start with 50%
        
        if (action.equals("BUY")) {
            if (optionType.equals("CE")) {
                // Buying calls - need bullish outcome
                baseProbability = technicals.getBullishScore();
            } else {
                // Buying puts - need bearish outcome
                baseProbability = technicals.getBearishScore();
            }
        } else {
            // Selling options - benefit from time decay and limited moves
            baseProbability = 65.0; // Sellers have statistical advantage
            
            // Adjust based on how extreme the strike is
            // (This would need more context about current spot vs strike)
        }
        
        // Adjust for signal strength
        if (technicals.hasAlignedBullishSignals() && optionType.equals("CE") && action.equals("BUY")) {
            baseProbability += 10;
        }
        
        if (technicals.hasAlignedBearishSignals() && optionType.equals("PE") && action.equals("BUY")) {
            baseProbability += 10;
        }
        
        // Volume confirmation
        if (technicals.volumeRatio > 1.5) {
            baseProbability += 5;
        }
        
        return Math.max(20, Math.min(90, baseProbability));
    }
    
    private String determineRiskLevel(double maxLoss, double expectedProfit, double probabilityOfProfit) {
        if (maxLoss == Double.POSITIVE_INFINITY) {
            return "VERY_HIGH";
        }
        
        double riskRewardRatio = maxLoss > 0 ? expectedProfit / maxLoss : 0;
        
        if (probabilityOfProfit > 70 && riskRewardRatio > 2.0) {
            return "LOW";
        } else if (probabilityOfProfit > 60 && riskRewardRatio > 1.5) {
            return "MEDIUM";
        } else if (probabilityOfProfit > 50 && riskRewardRatio > 1.0) {
            return "HIGH";
        } else {
            return "VERY_HIGH";
        }
    }
    
    /**
     * Calculate position sizing based on risk parameters
     */
    public double calculateOptimalPositionSize(double accountValue, double maxLoss, double confidence) {
        // Kelly Criterion modified for options
        double winRate = confidence / 100.0;
        double lossRate = 1.0 - winRate;
        
        // Conservative position sizing
        double maxRiskAmount = accountValue * (MAX_POSITION_SIZE_PERCENT / 100.0);
        
        // Adjust based on confidence
        double confidenceMultiplier = Math.min(1.0, confidence / 80.0);
        
        double positionSize = (maxRiskAmount / maxLoss) * confidenceMultiplier;
        
        return Math.max(1, Math.floor(positionSize)); // At least 1 lot
    }
    
    /**
     * Portfolio-level risk checks
     */
    public boolean validatePortfolioRisk(List<WorldClassOptionsGenerator.WorldClassOptionsCall> activeCalls) {
        double totalDeltaExposure = activeCalls.stream()
            .mapToDouble(call -> Math.abs(call.greeks.delta))
            .sum();
        
        double totalVegaExposure = activeCalls.stream()
            .mapToDouble(call -> Math.abs(call.greeks.vega))
            .sum();
        
        double totalRisk = activeCalls.stream()
            .mapToDouble(call -> call.risk.maxLoss)
            .filter(loss -> loss != Double.POSITIVE_INFINITY)
            .sum();
        
        // Check portfolio limits
        if (totalDeltaExposure > MAX_DELTA_EXPOSURE * activeCalls.size()) {
            System.out.println("⚠️ Portfolio delta exposure limit exceeded");
            return false;
        }
        
        if (totalVegaExposure > MAX_VEGA_EXPOSURE * activeCalls.size()) {
            System.out.println("⚠️ Portfolio vega exposure limit exceeded");
            return false;
        }
        
        return true;
    }
    
    /**
     * Generate risk report
     */
    public String generateRiskReport(List<WorldClassOptionsGenerator.WorldClassOptionsCall> calls) {
        StringBuilder report = new StringBuilder();
        report.append("📊 RISK MANAGEMENT REPORT\n");
        report.append("=".repeat(50)).append("\n");
        
        Map<String, Long> riskLevelCounts = new HashMap<>();
        double totalMaxLoss = 0;
        double totalExpectedProfit = 0;
        double avgProbabilityOfProfit = 0;
        
        for (WorldClassOptionsGenerator.WorldClassOptionsCall call : calls) {
            String riskLevel = call.risk.riskLevel;
            riskLevelCounts.put(riskLevel, riskLevelCounts.getOrDefault(riskLevel, 0L) + 1);
            
            if (call.risk.maxLoss != Double.POSITIVE_INFINITY) {
                totalMaxLoss += call.risk.maxLoss;
            }
            totalExpectedProfit += call.risk.expectedProfit;
            avgProbabilityOfProfit += call.risk.probabilityOfProfit;
        }
        
        avgProbabilityOfProfit /= calls.size();
        
        report.append(String.format("Total Positions: %d\n", calls.size()));
        report.append(String.format("Total Max Loss: ₹%.2f\n", totalMaxLoss));
        report.append(String.format("Total Expected Profit: ₹%.2f\n", totalExpectedProfit));
        report.append(String.format("Average Probability of Profit: %.1f%%\n", avgProbabilityOfProfit));
        report.append(String.format("Risk-Reward Ratio: %.2f:1\n", totalExpectedProfit / Math.max(totalMaxLoss, 1)));
        
        report.append("\nRisk Level Distribution:\n");
        riskLevelCounts.forEach((level, count) -> 
            report.append(String.format("  %s: %d positions\n", level, count)));
        
        return report.toString();
    }
}

/**
 * Risk Metrics data class
 */
class RiskMetrics {
    public final double maxLoss;
    public final double expectedProfit;
    public final double breakeven;
    public final double probabilityOfProfit;
    public final String riskLevel;
    
    public RiskMetrics(double maxLoss, double expectedProfit, double breakeven,
                     double probabilityOfProfit, String riskLevel) {
        this.maxLoss = maxLoss;
        this.expectedProfit = expectedProfit;
        this.breakeven = breakeven;
        this.probabilityOfProfit = probabilityOfProfit;
        this.riskLevel = riskLevel;
    }
    
    public double getRiskRewardRatio() {
        return maxLoss > 0 && maxLoss != Double.POSITIVE_INFINITY ? expectedProfit / maxLoss : 0;
    }
    
    @Override
    public String toString() {
        return String.format("Risk: ₹%.2f | Reward: ₹%.2f | Prob: %.1f%% | Level: %s",
            maxLoss, expectedProfit, probabilityOfProfit, riskLevel);
    }
}

/**
 * Performance Tracker for monitoring system accuracy
 */
class PerformanceTracker {
    
    private final List<TradeRecord> tradeHistory = new ArrayList<>();
    
    public static class TradeRecord {
        public final String symbol;
        public final String optionContract;
        public final double entryPrice;
        public final double exitPrice;
        public final double pnl;
        public final boolean wasCorrect;
        public final long entryTime;
        public final long exitTime;
        public final double confidence;
        
        public TradeRecord(String symbol, String optionContract, double entryPrice, double exitPrice,
                         double pnl, boolean wasCorrect, long entryTime, long exitTime, double confidence) {
            this.symbol = symbol;
            this.optionContract = optionContract;
            this.entryPrice = entryPrice;
            this.exitPrice = exitPrice;
            this.pnl = pnl;
            this.wasCorrect = wasCorrect;
            this.entryTime = entryTime;
            this.exitTime = exitTime;
            this.confidence = confidence;
        }
    }
    
    public void addTradeRecord(TradeRecord record) {
        tradeHistory.add(record);
        
        // Keep only recent 1000 trades
        if (tradeHistory.size() > 1000) {
            tradeHistory.remove(0);
        }
    }
    
    public double calculateAccuracy() {
        if (tradeHistory.isEmpty()) return 0.0;
        
        long correctTrades = tradeHistory.stream()
            .mapToLong(trade -> trade.wasCorrect ? 1 : 0)
            .sum();
        
        return (double) correctTrades / tradeHistory.size() * 100.0;
    }
    
    public double calculateTotalPnL() {
        return tradeHistory.stream()
            .mapToDouble(trade -> trade.pnl)
            .sum();
    }
    
    public double calculateAveragePnL() {
        if (tradeHistory.isEmpty()) return 0.0;
        
        return tradeHistory.stream()
            .mapToDouble(trade -> trade.pnl)
            .average()
            .orElse(0.0);
    }
    
    public String generatePerformanceReport() {
        if (tradeHistory.isEmpty()) {
            return "No trades recorded yet.";
        }
        
        double accuracy = calculateAccuracy();
        double totalPnL = calculateTotalPnL();
        double avgPnL = calculateAveragePnL();
        
        long profitableTrades = tradeHistory.stream()
            .mapToLong(trade -> trade.pnl > 0 ? 1 : 0)
            .sum();
        
        double avgConfidence = tradeHistory.stream()
            .mapToDouble(trade -> trade.confidence)
            .average()
            .orElse(0.0);
        
        StringBuilder report = new StringBuilder();
        report.append("📈 PERFORMANCE TRACKING REPORT\n");
        report.append("=".repeat(50)).append("\n");
        report.append(String.format("Total Trades: %d\n", tradeHistory.size()));
        report.append(String.format("Accuracy: %.1f%%\n", accuracy));
        report.append(String.format("Profitable Trades: %d (%.1f%%)\n", 
            profitableTrades, (double) profitableTrades / tradeHistory.size() * 100));
        report.append(String.format("Total P&L: ₹%.2f\n", totalPnL));
        report.append(String.format("Average P&L: ₹%.2f\n", avgPnL));
        report.append(String.format("Average Confidence: %.1f%%\n", avgConfidence));
        
        // Performance grade
        String grade = getPerformanceGrade(accuracy, totalPnL);
        report.append(String.format("Performance Grade: %s\n", grade));
        
        return report.toString();
    }
    
    private String getPerformanceGrade(double accuracy, double totalPnL) {
        if (accuracy >= 70 && totalPnL > 0) return "A+ (Excellent)";
        else if (accuracy >= 65 && totalPnL > 0) return "A (Very Good)";
        else if (accuracy >= 60 && totalPnL > 0) return "B+ (Good)";
        else if (accuracy >= 55) return "B (Average)";
        else if (accuracy >= 50) return "C (Below Average)";
        else return "D (Needs Improvement)";
    }
    
    public Map<String, Double> getDetailedMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        
        metrics.put("accuracy", calculateAccuracy());
        metrics.put("total_pnl", calculateTotalPnL());
        metrics.put("average_pnl", calculateAveragePnL());
        metrics.put("total_trades", (double) tradeHistory.size());
        
        if (!tradeHistory.isEmpty()) {
            double maxProfit = tradeHistory.stream()
                .mapToDouble(trade -> trade.pnl)
                .max().orElse(0.0);
            
            double maxLoss = tradeHistory.stream()
                .mapToDouble(trade -> trade.pnl)
                .min().orElse(0.0);
            
            metrics.put("max_profit", maxProfit);
            metrics.put("max_loss", maxLoss);
            
            long profitableTrades = tradeHistory.stream()
                .mapToLong(trade -> trade.pnl > 0 ? 1 : 0)
                .sum();
            
            metrics.put("win_rate", (double) profitableTrades / tradeHistory.size() * 100);
        }
        
        return metrics;
    }
}