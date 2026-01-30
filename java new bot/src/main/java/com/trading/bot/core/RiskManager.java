import java.util.*;

/**
 * RISK MANAGEMENT SYSTEM - Proper Position Sizing & Portfolio Protection
 * Step 3: Implement 1% risk per trade and portfolio protection
 */
public class RiskManager {
    private final double totalCapital;
    private final double maxRiskPerTrade; // 1% of capital
    private final double maxPortfolioRisk; // 5% total portfolio risk
    private final double maxDailyLoss; // 2% daily loss limit
    private final int maxPositions; // Maximum concurrent positions
    
    private double dailyPnL = 0.0;
    private final List<TradePosition> activePositions = new ArrayList<>();
    
    public RiskManager(double totalCapital) {
        this.totalCapital = totalCapital;
        this.maxRiskPerTrade = totalCapital * 0.01; // 1%
        this.maxPortfolioRisk = totalCapital * 0.05; // 5%
        this.maxDailyLoss = totalCapital * 0.02; // 2%
        this.maxPositions = 3; // Max 3 concurrent positions
    }
    
    // Calculate position size based on risk
    public double calculatePositionSize(double entryPrice, double stopLoss, String symbol) {
        double riskPerShare = Math.abs(entryPrice - stopLoss);
        if (riskPerShare == 0) return 0;
        
        double baseQuantity = maxRiskPerTrade / riskPerShare;
        
        // Adjust for symbol (Nifty vs Sensex lot sizes)
        if (symbol.contains("NIFTY")) {
            return Math.floor(baseQuantity / 50) * 50; // Nifty lot size
        } else if (symbol.contains("SENSEX")) {
            return Math.floor(baseQuantity / 10) * 10; // Sensex lot size
        }
        
        return Math.floor(baseQuantity);
    }
    
    // Check if new trade is allowed
    public TradeValidation validateTrade(TradingSignals.EntrySignal signal, MarketData data) {
        List<String> issues = new ArrayList<>();
        
        // 1. Check daily loss limit
        if (dailyPnL <= -maxDailyLoss) {
            issues.add("Daily loss limit exceeded");
        }
        
        // 2. Check maximum positions
        if (activePositions.size() >= maxPositions) {
            issues.add("Maximum positions limit reached");
        }
        
        // 3. Check portfolio risk
        double currentRisk = calculateCurrentPortfolioRisk();
        if (currentRisk >= maxPortfolioRisk) {
            issues.add("Portfolio risk limit exceeded");
        }
        
        // 4. Check signal confidence
        if (signal.confidence < 0.75) {
            issues.add("Signal confidence too low");
        }
        
        // 5. Check correlation (avoid too many similar positions)
        long sameDirectionCount = activePositions.stream()
            .filter(p -> p.direction.equals(signal.direction))
            .count();
        
        if (sameDirectionCount >= 2) {
            issues.add("Too many positions in same direction");
        }
        
        boolean isValid = issues.isEmpty();
        return new TradeValidation(isValid, issues);
    }
    
    // Calculate current portfolio risk
    private double calculateCurrentPortfolioRisk() {
        return activePositions.stream()
            .mapToDouble(p -> Math.abs(p.entryPrice - p.stopLoss) * p.quantity)
            .sum();
    }
    
    // Add new position
    public void addPosition(TradePosition position) {
        activePositions.add(position);
    }
    
    // Remove closed position
    public void removePosition(TradePosition position) {
        activePositions.remove(position);
        if (position.pnl != null) {
            dailyPnL += position.pnl;
        }
    }
    
    // Emergency stop - close all positions
    public boolean shouldEmergencyStop() {
        return dailyPnL <= -maxDailyLoss || 
               calculateCurrentPortfolioRisk() > maxPortfolioRisk * 1.5;
    }
    
    // Portfolio status
    public RiskStatus getPortfolioStatus() {
        double currentRisk = calculateCurrentPortfolioRisk();
        double riskPercentage = (currentRisk / totalCapital) * 100;
        double dailyLossPercentage = (Math.abs(dailyPnL) / totalCapital) * 100;
        
        String status = "HEALTHY";
        if (dailyLossPercentage > 1.5) status = "WARNING";
        if (dailyLossPercentage > 2.0 || riskPercentage > 4.0) status = "DANGER";
        
        return new RiskStatus(
            totalCapital,
            dailyPnL,
            dailyLossPercentage,
            currentRisk,
            riskPercentage,
            activePositions.size(),
            status
        );
    }
    
    // Reset daily P&L (call at start of each trading day)
    public void resetDailyPnL() {
        this.dailyPnL = 0.0;
    }
    
    // Data Classes
    public static class TradeValidation {
        public final boolean isValid;
        public final List<String> issues;
        
        public TradeValidation(boolean isValid, List<String> issues) {
            this.isValid = isValid;
            this.issues = issues;
        }
    }
    
    public static class RiskStatus {
        public final double totalCapital;
        public final double dailyPnL;
        public final double dailyLossPercentage;
        public final double currentRisk;
        public final double riskPercentage;
        public final int activePositions;
        public final String status;
        
        public RiskStatus(double totalCapital, double dailyPnL, double dailyLossPercentage,
                         double currentRisk, double riskPercentage, int activePositions, String status) {
            this.totalCapital = totalCapital;
            this.dailyPnL = dailyPnL;
            this.dailyLossPercentage = dailyLossPercentage;
            this.currentRisk = currentRisk;
            this.riskPercentage = riskPercentage;
            this.activePositions = activePositions;
            this.status = status;
        }
        
        @Override
        public String toString() {
            return String.format("Risk Status: %s | Daily P&L: %.2f (%.2f%%) | Portfolio Risk: %.2f (%.2f%%) | Positions: %d",
                status, dailyPnL, dailyLossPercentage, currentRisk, riskPercentage, activePositions);
        }
    }
}