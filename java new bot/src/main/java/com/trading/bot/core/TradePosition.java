import java.time.LocalDateTime;

/**
 * TRADE POSITION - Position Management
 * Step 2: Proper trade tracking and management
 */
public class TradePosition {
    public final String id;
    public final String symbol;
    public final String direction;
    public final double entryPrice;
    public final LocalDateTime entryTime;
    public final double quantity;
    public final String entryReason;
    
    // Exit information
    public Double exitPrice;
    public LocalDateTime exitTime;
    public String exitReason;
    public Double pnl;
    public boolean isActive;
    
    // Risk management
    public final double stopLoss;
    public final double target1;
    public final double target2;
    public double trailingStop;
    
    public TradePosition(String symbol, String direction, double entryPrice, 
                        double quantity, String entryReason, double stopLoss,
                        double target1, double target2) {
        this.id = java.util.UUID.randomUUID().toString().substring(0, 8);
        this.symbol = symbol;
        this.direction = direction;
        this.entryPrice = entryPrice;
        this.entryTime = LocalDateTime.now();
        this.quantity = quantity;
        this.entryReason = entryReason;
        this.stopLoss = stopLoss;
        this.target1 = target1;
        this.target2 = target2;
        this.trailingStop = stopLoss;
        this.isActive = true;
    }
    
    // Close position
    public void closePosition(double exitPrice, String exitReason) {
        this.exitPrice = exitPrice;
        this.exitTime = LocalDateTime.now();
        this.exitReason = exitReason;
        this.isActive = false;
        
        // Calculate P&L
        if (direction.equals("BUY")) {
            this.pnl = (exitPrice - entryPrice) * quantity;
        } else {
            this.pnl = (entryPrice - exitPrice) * quantity;
        }
    }
    
    // Update trailing stop
    public void updateTrailingStop(double currentPrice) {
        if (!isActive) return;
        
        if (direction.equals("BUY")) {
            // For long positions, trail stop up
            double newStop = currentPrice - (entryPrice - stopLoss);
            if (newStop > trailingStop) {
                trailingStop = newStop;
            }
        } else {
            // For short positions, trail stop down
            double newStop = currentPrice + (stopLoss - entryPrice);
            if (newStop < trailingStop) {
                trailingStop = newStop;
            }
        }
    }
    
    // Check if trailing stop hit
    public boolean isTrailingStopHit(double currentPrice) {
        if (direction.equals("BUY")) {
            return currentPrice <= trailingStop;
        } else {
            return currentPrice >= trailingStop;
        }
    }
    
    // Get current P&L
    public double getCurrentPnL(double currentPrice) {
        if (direction.equals("BUY")) {
            return (currentPrice - entryPrice) * quantity;
        } else {
            return (entryPrice - currentPrice) * quantity;
        }
    }
    
    @Override
    public String toString() {
        return String.format("[%s] %s %s %.0f @ %.2f | P&L: %.2f | Status: %s", 
            id, symbol, direction, quantity, entryPrice, 
            pnl != null ? pnl : 0.0, isActive ? "ACTIVE" : "CLOSED");
    }
}