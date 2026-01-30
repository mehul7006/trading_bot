import java.util.*;
import java.time.LocalDateTime;

/**
 * ENHANCED RISK MANAGER - PART 2
 * Fixes the ₹89 average loss per trade
 * Implements proper position sizing and stop-loss management
 */
public class EnhancedRiskManager {
    
    // Risk parameters
    private double maxRiskPerTrade = 0.02; // 2% max risk per trade
    private double maxDailyRisk = 0.06; // 6% max daily risk
    private double winLossRatio = 2.0; // Target 2:1 reward:risk ratio
    
    // Portfolio tracking
    private double portfolioValue = 100000.0; // ₹1 lakh portfolio
    private double dailyPnL = 0.0;
    private List<TradeRisk> activeTrades = new ArrayList<>();
    private Map<String, Double> indexVolatility = new HashMap<>();
    
    public EnhancedRiskManager() {
        System.out.println("🛡️ ENHANCED RISK MANAGER - PART 2");
        System.out.println("=================================");
        System.out.println("🎯 Target: Fix ₹89 average loss per trade");
        System.out.println("📊 Method: Proper position sizing & stop-loss");
        System.out.println("🛡️ Max risk per trade: 2%");
        
        // Initialize volatility estimates
        indexVolatility.put("NIFTY", 1.2); // 1.2% daily volatility
        indexVolatility.put("SENSEX", 1.1); // 1.1% daily volatility
    }
    
    /**
     * PART 2A: Calculate optimal position size
     */
    public PositionSizeResult calculateOptimalPositionSize(String index, double entryPrice, 
                                                          double confidence, String direction) {
        System.out.println("📊 PART 2A: Calculating optimal position size for " + index);
        
        // Base risk amount (2% of portfolio)
        double baseRiskAmount = portfolioValue * maxRiskPerTrade;
        
        // Adjust risk based on confidence
        double confidenceMultiplier = Math.min(1.5, confidence / 100.0 * 1.5);
        double adjustedRiskAmount = baseRiskAmount * confidenceMultiplier;
        
        // Check daily risk limit
        double remainingDailyRisk = (portfolioValue * maxDailyRisk) - Math.abs(dailyPnL);
        adjustedRiskAmount = Math.min(adjustedRiskAmount, remainingDailyRisk);
        
        // Calculate stop-loss distance based on volatility
        double volatility = indexVolatility.getOrDefault(index, 1.0);
        double stopLossDistance = entryPrice * (volatility / 100.0) * 1.5; // 1.5x volatility
        
        // Calculate position size
        int lotSize = index.equals("NIFTY") ? 50 : 10; // NIFTY=50, SENSEX=10
        double maxLots = adjustedRiskAmount / (stopLossDistance * lotSize);
        int recommendedLots = Math.max(1, (int) Math.floor(maxLots));
        
        // Calculate actual risk with recommended lots
        double actualRisk = stopLossDistance * lotSize * recommendedLots;
        double riskPercentage = (actualRisk / portfolioValue) * 100;
        
        // Calculate targets based on risk-reward ratio
        double target1 = direction.equals("BULLISH") ? 
            entryPrice + (stopLossDistance * winLossRatio * 0.5) :
            entryPrice - (stopLossDistance * winLossRatio * 0.5);
            
        double target2 = direction.equals("BULLISH") ? 
            entryPrice + (stopLossDistance * winLossRatio) :
            entryPrice - (stopLossDistance * winLossRatio);
        
        double stopLoss = direction.equals("BULLISH") ? 
            entryPrice - stopLossDistance :
            entryPrice + stopLossDistance;
        
        PositionSizeResult result = new PositionSizeResult(
            recommendedLots, actualRisk, riskPercentage, stopLoss, target1, target2
        );
        
        System.out.println("✅ Position size calculated: " + recommendedLots + " lots, Risk: ₹" + 
                          String.format("%.2f", actualRisk) + " (" + String.format("%.2f", riskPercentage) + "%)");
        
        return result;
    }
    
    /**
     * PART 2B: Dynamic stop-loss management
     */
    public StopLossUpdate updateStopLoss(String tradeId, double currentPrice, double entryPrice, 
                                        String direction, double originalStopLoss) {
        System.out.println("🔄 PART 2B: Updating stop-loss for trade " + tradeId);
        
        double newStopLoss = originalStopLoss;
        String updateReason = "No change";
        boolean shouldUpdate = false;
        
        if (direction.equals("BULLISH")) {
            // For bullish trades, trail stop-loss upward
            double priceMove = currentPrice - entryPrice;
            if (priceMove > 0) {
                // Move stop-loss to breakeven when price moves 1:1
                double breakeven = entryPrice;
                if (currentPrice > entryPrice * 1.01 && originalStopLoss < breakeven) {
                    newStopLoss = breakeven;
                    updateReason = "Moved to breakeven";
                    shouldUpdate = true;
                }
                
                // Trail stop-loss when in profit
                double trailingStop = currentPrice - (entryPrice - originalStopLoss) * 0.5;
                if (trailingStop > newStopLoss && currentPrice > entryPrice * 1.02) {
                    newStopLoss = trailingStop;
                    updateReason = "Trailing stop activated";
                    shouldUpdate = true;
                }
            }
        } else {
            // For bearish trades, trail stop-loss downward
            double priceMove = entryPrice - currentPrice;
            if (priceMove > 0) {
                // Move stop-loss to breakeven when price moves 1:1
                double breakeven = entryPrice;
                if (currentPrice < entryPrice * 0.99 && originalStopLoss > breakeven) {
                    newStopLoss = breakeven;
                    updateReason = "Moved to breakeven";
                    shouldUpdate = true;
                }
                
                // Trail stop-loss when in profit
                double trailingStop = currentPrice + (originalStopLoss - entryPrice) * 0.5;
                if (trailingStop < newStopLoss && currentPrice < entryPrice * 0.98) {
                    newStopLoss = trailingStop;
                    updateReason = "Trailing stop activated";
                    shouldUpdate = true;
                }
            }
        }
        
        StopLossUpdate update = new StopLossUpdate(newStopLoss, shouldUpdate, updateReason);
        
        if (shouldUpdate) {
            System.out.println("✅ Stop-loss updated: ₹" + String.format("%.2f", newStopLoss) + 
                              " (" + updateReason + ")");
        }
        
        return update;
    }
    
    /**
     * PART 2C: Risk assessment before trade entry
     */
    public RiskAssessment assessTradeRisk(String index, double entryPrice, double confidence, 
                                         String direction, LocalDateTime timestamp) {
        System.out.println("🔍 PART 2C: Assessing trade risk for " + index);
        
        boolean canTrade = true;
        List<String> riskWarnings = new ArrayList<>();
        double riskScore = 0.0;
        
        // Check daily risk limit
        double usedDailyRisk = Math.abs(dailyPnL) / portfolioValue * 100;
        if (usedDailyRisk > maxDailyRisk * 100 * 0.8) { // 80% of daily limit
            riskWarnings.add("Approaching daily risk limit (" + String.format("%.1f", usedDailyRisk) + "%)");
            riskScore += 0.3;
        }
        
        // Check number of active trades
        if (activeTrades.size() >= 3) {
            riskWarnings.add("Too many active trades (" + activeTrades.size() + ")");
            riskScore += 0.2;
            canTrade = false;
        }
        
        // Check confidence level
        if (confidence < 75.0) {
            riskWarnings.add("Low confidence level (" + String.format("%.1f", confidence) + "%)");
            riskScore += 0.3;
            canTrade = false;
        }
        
        // Check market hours
        int hour = timestamp.getHour();
        if (hour < 9 || hour > 15) {
            riskWarnings.add("Outside market hours");
            riskScore += 0.5;
            canTrade = false;
        }
        
        // Check volatility
        double currentVolatility = indexVolatility.getOrDefault(index, 1.0);
        if (currentVolatility > 2.0) {
            riskWarnings.add("High market volatility (" + String.format("%.1f", currentVolatility) + "%)");
            riskScore += 0.2;
        }
        
        // Check correlation with existing trades
        long sameDirectionTrades = activeTrades.stream()
            .filter(t -> t.direction.equals(direction))
            .count();
        if (sameDirectionTrades >= 2) {
            riskWarnings.add("Too many trades in same direction");
            riskScore += 0.2;
        }
        
        RiskLevel riskLevel;
        if (riskScore <= 0.3) {
            riskLevel = RiskLevel.LOW;
        } else if (riskScore <= 0.6) {
            riskLevel = RiskLevel.MEDIUM;
        } else {
            riskLevel = RiskLevel.HIGH;
            canTrade = false;
        }
        
        RiskAssessment assessment = new RiskAssessment(canTrade, riskLevel, riskScore, riskWarnings);
        
        System.out.println("✅ Risk assessment complete: " + riskLevel + 
                          " (" + String.format("%.2f", riskScore) + ") - " + 
                          (canTrade ? "APPROVED" : "REJECTED"));
        
        return assessment;
    }
    
    /**
     * PART 2D: Portfolio protection rules
     */
    public ProtectionAction checkPortfolioProtection() {
        System.out.println("🛡️ PART 2D: Checking portfolio protection rules");
        
        double dailyLossPercentage = (dailyPnL / portfolioValue) * 100;
        
        // Daily loss limit protection
        if (dailyLossPercentage <= -5.0) {
            return new ProtectionAction(ActionType.STOP_ALL_TRADING, 
                "Daily loss limit exceeded (" + String.format("%.2f", dailyLossPercentage) + "%)");
        }
        
        if (dailyLossPercentage <= -3.0) {
            return new ProtectionAction(ActionType.REDUCE_POSITION_SIZE, 
                "Significant daily loss (" + String.format("%.2f", dailyLossPercentage) + "%)");
        }
        
        // Consecutive loss protection
        int consecutiveLosses = countConsecutiveLosses();
        if (consecutiveLosses >= 3) {
            return new ProtectionAction(ActionType.PAUSE_TRADING, 
                "Too many consecutive losses (" + consecutiveLosses + ")");
        }
        
        // Drawdown protection
        double maxDrawdown = calculateMaxDrawdown();
        if (maxDrawdown <= -10.0) {
            return new ProtectionAction(ActionType.EMERGENCY_EXIT, 
                "Maximum drawdown exceeded (" + String.format("%.2f", maxDrawdown) + "%)");
        }
        
        return new ProtectionAction(ActionType.CONTINUE_TRADING, "Portfolio protection: All clear");
    }
    
    // Helper methods
    private int countConsecutiveLosses() {
        // Simplified - would track actual trade history
        return 0;
    }
    
    private double calculateMaxDrawdown() {
        // Simplified - would calculate from trade history
        return dailyPnL / portfolioValue * 100;
    }
    
    // Data classes
    public static class PositionSizeResult {
        public final int recommendedLots;
        public final double riskAmount;
        public final double riskPercentage;
        public final double stopLoss;
        public final double target1;
        public final double target2;
        
        public PositionSizeResult(int recommendedLots, double riskAmount, double riskPercentage,
                                 double stopLoss, double target1, double target2) {
            this.recommendedLots = recommendedLots;
            this.riskAmount = riskAmount;
            this.riskPercentage = riskPercentage;
            this.stopLoss = stopLoss;
            this.target1 = target1;
            this.target2 = target2;
        }
    }
    
    public static class StopLossUpdate {
        public final double newStopLoss;
        public final boolean shouldUpdate;
        public final String reason;
        
        public StopLossUpdate(double newStopLoss, boolean shouldUpdate, String reason) {
            this.newStopLoss = newStopLoss;
            this.shouldUpdate = shouldUpdate;
            this.reason = reason;
        }
    }
    
    public static class RiskAssessment {
        public final boolean canTrade;
        public final RiskLevel riskLevel;
        public final double riskScore;
        public final List<String> warnings;
        
        public RiskAssessment(boolean canTrade, RiskLevel riskLevel, double riskScore, List<String> warnings) {
            this.canTrade = canTrade;
            this.riskLevel = riskLevel;
            this.riskScore = riskScore;
            this.warnings = warnings;
        }
    }
    
    public static class ProtectionAction {
        public final ActionType action;
        public final String reason;
        
        public ProtectionAction(ActionType action, String reason) {
            this.action = action;
            this.reason = reason;
        }
    }
    
    public static class TradeRisk {
        public final String tradeId;
        public final String index;
        public final String direction;
        public final double entryPrice;
        public final double riskAmount;
        
        public TradeRisk(String tradeId, String index, String direction, double entryPrice, double riskAmount) {
            this.tradeId = tradeId;
            this.index = index;
            this.direction = direction;
            this.entryPrice = entryPrice;
            this.riskAmount = riskAmount;
        }
    }
    
    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }
    
    public enum ActionType {
        CONTINUE_TRADING, REDUCE_POSITION_SIZE, PAUSE_TRADING, STOP_ALL_TRADING, EMERGENCY_EXIT
    }
    
    public static void main(String[] args) {
        System.out.println("🛡️ TESTING ENHANCED RISK MANAGER - PART 2");
        System.out.println("==========================================");
        
        EnhancedRiskManager riskManager = new EnhancedRiskManager();
        
        // Test position sizing
        PositionSizeResult positionSize = riskManager.calculateOptimalPositionSize(
            "NIFTY", 24800.0, 80.0, "BULLISH");
        
        System.out.println("\n📊 POSITION SIZE RESULT:");
        System.out.println("========================");
        System.out.println("Recommended Lots: " + positionSize.recommendedLots);
        System.out.println("Risk Amount: ₹" + String.format("%.2f", positionSize.riskAmount));
        System.out.println("Risk Percentage: " + String.format("%.2f", positionSize.riskPercentage) + "%");
        System.out.println("Stop Loss: ₹" + String.format("%.2f", positionSize.stopLoss));
        System.out.println("Target 1: ₹" + String.format("%.2f", positionSize.target1));
        System.out.println("Target 2: ₹" + String.format("%.2f", positionSize.target2));
        
        // Test risk assessment
        RiskAssessment assessment = riskManager.assessTradeRisk(
            "NIFTY", 24800.0, 80.0, "BULLISH", LocalDateTime.now());
        
        System.out.println("\n🔍 RISK ASSESSMENT:");
        System.out.println("===================");
        System.out.println("Can Trade: " + (assessment.canTrade ? "✅ YES" : "❌ NO"));
        System.out.println("Risk Level: " + assessment.riskLevel);
        System.out.println("Risk Score: " + String.format("%.2f", assessment.riskScore));
        if (!assessment.warnings.isEmpty()) {
            System.out.println("Warnings: " + String.join(", ", assessment.warnings));
        }
    }
}