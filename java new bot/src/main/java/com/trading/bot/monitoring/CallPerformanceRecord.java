package com.trading.bot.monitoring;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Record of a completed call's performance
 */
public class CallPerformanceRecord {
    
    private final String callId;
    private final String symbol;
    private final String callType;
    private final String expectedDirection;
    private final double entryPrice;
    private final double targetPrice;
    private final double finalPrice;
    private final double investmentAmount;
    private final double finalProfitLoss;
    private final double finalTargetAchievement;
    private final boolean wasSuccessful;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final long durationMinutes;
    private final String closureReason;
    private final int totalMonitoringUpdates;
    
    public CallPerformanceRecord(ActiveCall activeCall, MonitoringResult finalResult) {
        this.callId = activeCall.getCallId();
        this.symbol = activeCall.getSymbol();
        this.callType = activeCall.getCallType();
        this.expectedDirection = activeCall.getExpectedDirection();
        this.entryPrice = activeCall.getEntryPrice();
        this.targetPrice = activeCall.getTargetPrice();
        this.finalPrice = finalResult != null ? finalResult.getCurrentPrice() : entryPrice;
        this.investmentAmount = activeCall.getInvestmentAmount();
        this.finalProfitLoss = finalResult != null ? finalResult.getProfitLoss() : 0.0;
        this.finalTargetAchievement = finalResult != null ? finalResult.getTargetAchievement() : 0.0;
        this.wasSuccessful = finalProfitLoss > 0;
        this.startTime = activeCall.getGeneratedTime();
        this.endTime = activeCall.getClosedTime() != null ? activeCall.getClosedTime() : LocalDateTime.now();
        this.durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
        this.closureReason = activeCall.getClosureReason() != null ? activeCall.getClosureReason() : "Manual close";
        this.totalMonitoringUpdates = activeCall.getMonitoringResults().size();
    }
    
    public double getReturnPercentage() {
        return investmentAmount > 0 ? (finalProfitLoss / investmentAmount) * 100 : 0.0;
    }
    
    public boolean achievedTarget() {
        return finalTargetAchievement >= 1.0;
    }
    
    public boolean achieved50PercentTarget() {
        return finalTargetAchievement >= 0.5;
    }
    
    public String getPerformanceSummary() {
        return String.format(
            "Performance Summary for %s:\n" +
            "==========================\n" +
            "Symbol: %s (%s)\n" +
            "Direction: %s\n" +
            "Entry: ₹%.2f → Final: ₹%.2f\n" +
            "Target Achievement: %.1f%%\n" +
            "Investment: ₹%.2f\n" +
            "Final P&L: ₹%.2f (%.2f%%)\n" +
            "Duration: %d minutes\n" +
            "Result: %s\n" +
            "Closure Reason: %s\n" +
            "Monitoring Updates: %d",
            callId.substring(0, 8),
            symbol,
            callType,
            expectedDirection,
            entryPrice,
            finalPrice,
            finalTargetAchievement * 100,
            investmentAmount,
            finalProfitLoss,
            getReturnPercentage(),
            durationMinutes,
            wasSuccessful ? "SUCCESS ✅" : "LOSS ❌",
            closureReason,
            totalMonitoringUpdates
        );
    }
    
    // Getters
    public String getCallId() { return callId; }
    public String getSymbol() { return symbol; }
    public String getCallType() { return callType; }
    public String getExpectedDirection() { return expectedDirection; }
    public double getEntryPrice() { return entryPrice; }
    public double getTargetPrice() { return targetPrice; }
    public double getFinalPrice() { return finalPrice; }
    public double getInvestmentAmount() { return investmentAmount; }
    public double getFinalProfitLoss() { return finalProfitLoss; }
    public double getFinalTargetAchievement() { return finalTargetAchievement; }
    public boolean wasSuccessful() { return wasSuccessful; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public long getDurationMinutes() { return durationMinutes; }
    public String getClosureReason() { return closureReason; }
    public int getTotalMonitoringUpdates() { return totalMonitoringUpdates; }
    
    @Override
    public String toString() {
        return String.format("CallRecord[%s: %s %.2f%% return, %s]",
            callId.substring(0, 8), symbol, getReturnPercentage(), 
            wasSuccessful ? "SUCCESS" : "LOSS");
    }
}