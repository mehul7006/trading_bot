package com.trading.bot.monitoring;

import java.time.LocalDateTime;

/**
 * Represents the result of monitoring an active call
 */
public class MonitoringResult {
    
    private final double currentPrice;
    private final double priceMovement;
    private final double percentageMovement;
    private final double targetAchievement;
    private final boolean isMovingAsPerPrediction;
    private final boolean isWrongDirection;
    private final boolean shouldCloseCall;
    private final double profitLoss;
    private final LocalDateTime timestamp;
    
    public MonitoringResult(double currentPrice, 
                          double priceMovement,
                          double percentageMovement,
                          double targetAchievement,
                          boolean isMovingAsPerPrediction,
                          boolean isWrongDirection,
                          boolean shouldCloseCall,
                          double profitLoss,
                          LocalDateTime timestamp) {
        
        this.currentPrice = currentPrice;
        this.priceMovement = priceMovement;
        this.percentageMovement = percentageMovement;
        this.targetAchievement = targetAchievement;
        this.isMovingAsPerPrediction = isMovingAsPerPrediction;
        this.isWrongDirection = isWrongDirection;
        this.shouldCloseCall = shouldCloseCall;
        this.profitLoss = profitLoss;
        this.timestamp = timestamp;
    }
    
    // Getters
    public double getCurrentPrice() { return currentPrice; }
    public double getPriceMovement() { return priceMovement; }
    public double getPercentageMovement() { return percentageMovement; }
    public double getTargetAchievement() { return targetAchievement; }
    public boolean isMovingAsPerPrediction() { return isMovingAsPerPrediction; }
    public boolean isWrongDirection() { return isWrongDirection; }
    public boolean shouldCloseCall() { return shouldCloseCall; }
    public double getProfitLoss() { return profitLoss; }
    public LocalDateTime getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("MonitoringResult[Price: ₹%.2f, Movement: %.2f%%, Target: %.1f%%, P&L: ₹%.2f]",
            currentPrice, percentageMovement, targetAchievement * 100, profitLoss);
    }
}