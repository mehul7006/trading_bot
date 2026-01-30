package com.trading.bot.monitoring;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Represents an active call being monitored
 */
public class ActiveCall {
    
    private final String callId;
    private final String symbol;
    private final String callType; // CE, PE, etc.
    private final String expectedDirection; // BULLISH, BEARISH
    private final double entryPrice;
    private final double targetPrice;
    private final double investmentAmount;
    private final LocalDateTime generatedTime;
    
    // Monitoring state
    private final List<MonitoringResult> monitoringResults;
    private boolean movementStartedNotified = false;
    private boolean notified50Percent = false;
    private boolean notified100Percent = false;
    private boolean wrongDirectionNotified = false;
    
    // Closure information
    private LocalDateTime closedTime;
    private String closureReason;
    
    public ActiveCall(GeneratedCall generatedCall) {
        this.callId = generatedCall.getCallId();
        this.symbol = generatedCall.getSymbol();
        this.callType = generatedCall.getCallType();
        this.expectedDirection = generatedCall.getExpectedDirection();
        this.entryPrice = generatedCall.getEntryPrice();
        this.targetPrice = generatedCall.getTargetPrice();
        this.investmentAmount = generatedCall.getInvestmentAmount();
        this.generatedTime = generatedCall.getGeneratedTime();
        this.monitoringResults = Collections.synchronizedList(new ArrayList<>());
    }
    
    public void addMonitoringResult(MonitoringResult result) {
        monitoringResults.add(result);
        
        // Keep only last 100 results to prevent memory issues
        if (monitoringResults.size() > 100) {
            monitoringResults.remove(0);
        }
    }
    
    public List<MonitoringResult> getRecentResults(int count) {
        int size = monitoringResults.size();
        int fromIndex = Math.max(0, size - count);
        return new ArrayList<>(monitoringResults.subList(fromIndex, size));
    }
    
    public MonitoringResult getLatestResult() {
        return monitoringResults.isEmpty() ? null : 
               monitoringResults.get(monitoringResults.size() - 1);
    }
    
    public long getDurationInMinutes() {
        LocalDateTime endTime = closedTime != null ? closedTime : LocalDateTime.now();
        return ChronoUnit.MINUTES.between(generatedTime, endTime);
    }
    
    // Getters and Setters
    public String getCallId() { return callId; }
    public String getSymbol() { return symbol; }
    public String getCallType() { return callType; }
    public String getExpectedDirection() { return expectedDirection; }
    public double getEntryPrice() { return entryPrice; }
    public double getTargetPrice() { return targetPrice; }
    public double getInvestmentAmount() { return investmentAmount; }
    public LocalDateTime getGeneratedTime() { return generatedTime; }
    public List<MonitoringResult> getMonitoringResults() { return new ArrayList<>(monitoringResults); }
    
    public boolean isMovementStartedNotified() { return movementStartedNotified; }
    public void setMovementStartedNotified(boolean movementStartedNotified) { 
        this.movementStartedNotified = movementStartedNotified; 
    }
    
    public boolean isNotified50Percent() { return notified50Percent; }
    public void setNotified50Percent(boolean notified50Percent) { 
        this.notified50Percent = notified50Percent; 
    }
    
    public boolean isNotified100Percent() { return notified100Percent; }
    public void setNotified100Percent(boolean notified100Percent) { 
        this.notified100Percent = notified100Percent; 
    }
    
    public boolean isWrongDirectionNotified() { return wrongDirectionNotified; }
    public void setWrongDirectionNotified(boolean wrongDirectionNotified) { 
        this.wrongDirectionNotified = wrongDirectionNotified; 
    }
    
    public LocalDateTime getClosedTime() { return closedTime; }
    public void setClosedTime(LocalDateTime closedTime) { this.closedTime = closedTime; }
    
    public String getClosureReason() { return closureReason; }
    public void setClosureReason(String closureReason) { this.closureReason = closureReason; }
    
    @Override
    public String toString() {
        return String.format("ActiveCall[%s: %s %s @ ₹%.2f → ₹%.2f]", 
            callId, symbol, expectedDirection, entryPrice, targetPrice);
    }
}