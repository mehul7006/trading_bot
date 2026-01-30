package com.trading.bot.monitoring;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * COMPREHENSIVE CALL MONITORING SYSTEM - Point 4 Implementation
 * 
 * Features:
 * 1. Real-time monitoring of generated calls
 * 2. Movement tracking as per prediction
 * 3. Target achievement checking
 * 4. Wrong call detection and cost analysis
 * 5. Progressive notifications (50%, 100% target completion)
 * 6. Automatic call closure recommendations
 */
public class ComprehensiveCallMonitoringSystem {
    
    private static final Logger logger = LoggerFactory.getLogger(ComprehensiveCallMonitoringSystem.class);
    
    // Core components
    private final ScheduledExecutorService monitoringScheduler;
    private final Map<String, ActiveCall> activeCallsMap;
    private final List<CallPerformanceRecord> historicalRecords;
    private final NotificationSystem notificationSystem;
    
    // Monitoring settings
    private static final int MONITORING_INTERVAL_SECONDS = 30; // Check every 30 seconds
    private static final double TARGET_50_PERCENT = 0.5;
    private static final double TARGET_100_PERCENT = 1.0;
    private static final double STOP_LOSS_THRESHOLD = -0.15; // 15% loss
    private static final double WRONG_DIRECTION_THRESHOLD = -0.08; // 8% against prediction
    
    private volatile boolean isMonitoringActive = false;
    
    public ComprehensiveCallMonitoringSystem(NotificationSystem notificationSystem) {
        this.monitoringScheduler = Executors.newScheduledThreadPool(3);
        this.activeCallsMap = new ConcurrentHashMap<>();
        this.historicalRecords = Collections.synchronizedList(new ArrayList<>());
        this.notificationSystem = notificationSystem;
        
        logger.info("🔍 Comprehensive Call Monitoring System initialized");
    }
    
    /**
     * Start monitoring system
     */
    public void startMonitoring() {
        if (isMonitoringActive) {
            logger.warn("Monitoring already active");
            return;
        }
        
        isMonitoringActive = true;
        
        // Main monitoring loop
        monitoringScheduler.scheduleAtFixedRate(
            this::performMonitoringCycle,
            0, 
            MONITORING_INTERVAL_SECONDS, 
            TimeUnit.SECONDS
        );
        
        // Periodic performance summary
        monitoringScheduler.scheduleAtFixedRate(
            this::generatePerformanceSummary,
            0,
            300, // Every 5 minutes
            TimeUnit.SECONDS
        );
        
        logger.info("✅ Call monitoring started - checking every {} seconds", MONITORING_INTERVAL_SECONDS);
        notificationSystem.sendNotification("🚀 Call Monitoring System ACTIVATED", 
            "Real-time tracking of all generated calls is now active.");
    }
    
    /**
     * Add new generated call to monitoring
     */
    public void addCallForMonitoring(GeneratedCall call) {
        try {
            ActiveCall activeCall = new ActiveCall(call);
            activeCallsMap.put(call.getCallId(), activeCall);
            
            logger.info("📊 Added call for monitoring: {}", call.getCallId());
            
            // Send initial notification
            notificationSystem.sendNotification(
                "🎯 NEW CALL GENERATED - MONITORING STARTED",
                formatNewCallNotification(activeCall)
            );
            
        } catch (Exception e) {
            logger.error("Error adding call for monitoring: {}", e.getMessage());
        }
    }
    
    /**
     * Main monitoring cycle - runs every 30 seconds
     */
    private void performMonitoringCycle() {
        try {
            if (activeCallsMap.isEmpty()) {
                return;
            }
            
            logger.debug("🔍 Performing monitoring cycle for {} active calls", activeCallsMap.size());
            
            List<ActiveCall> callsToRemove = new ArrayList<>();
            
            for (ActiveCall activeCall : activeCallsMap.values()) {
                try {
                    MonitoringResult result = analyzeCallPerformance(activeCall);
                    processMonitoringResult(activeCall, result);
                    
                    // Check if call should be closed
                    if (result.shouldCloseCall) {
                        callsToRemove.add(activeCall);
                    }
                    
                } catch (Exception e) {
                    logger.error("Error monitoring call {}: {}", activeCall.getCallId(), e.getMessage());
                }
            }
            
            // Remove completed/closed calls
            for (ActiveCall call : callsToRemove) {
                closeCall(call, "Monitoring cycle completion");
            }
            
        } catch (Exception e) {
            logger.error("Error in monitoring cycle: {}", e.getMessage());
        }
    }
    
    /**
     * Analyze individual call performance
     */
    private MonitoringResult analyzeCallPerformance(ActiveCall activeCall) {
        // Get current market price
        double currentPrice = getCurrentMarketPrice(activeCall.getSymbol());
        double entryPrice = activeCall.getEntryPrice();
        double targetPrice = activeCall.getTargetPrice();
        
        // Calculate movement
        double priceMovement = currentPrice - entryPrice;
        double percentageMovement = (priceMovement / entryPrice) * 100;
        
        // Determine if movement is as per prediction
        boolean isMovingAsPerPrediction = isMovementAsPerPrediction(activeCall, priceMovement);
        
        // Calculate target achievement
        double targetAchievement = calculateTargetAchievement(activeCall, currentPrice);
        
        // Check for wrong direction
        boolean isWrongDirection = checkWrongDirection(activeCall, percentageMovement);
        
        // Determine if call should be closed
        boolean shouldClose = shouldCloseCall(activeCall, percentageMovement, targetAchievement, isWrongDirection);
        
        return new MonitoringResult(
            currentPrice,
            priceMovement,
            percentageMovement,
            targetAchievement,
            isMovingAsPerPrediction,
            isWrongDirection,
            shouldClose,
            calculateProfitLoss(activeCall, currentPrice),
            LocalDateTime.now()
        );
    }
    
    /**
     * Process monitoring result and send notifications
     */
    private void processMonitoringResult(ActiveCall activeCall, MonitoringResult result) {
        activeCall.addMonitoringResult(result);
        
        // Check for milestone notifications
        checkAndSendMilestoneNotifications(activeCall, result);
        
        // Check for direction change notifications
        checkDirectionChangeNotifications(activeCall, result);
        
        // Check for target achievement notifications
        checkTargetAchievementNotifications(activeCall, result);
        
        // Check for wrong call notifications
        checkWrongCallNotifications(activeCall, result);
    }
    
    /**
     * Check milestone notifications (50%, 100% target achievement)
     */
    private void checkAndSendMilestoneNotifications(ActiveCall activeCall, MonitoringResult result) {
        // 50% target achievement
        if (!activeCall.isNotified50Percent() && result.targetAchievement >= TARGET_50_PERCENT) {
            activeCall.setNotified50Percent(true);
            notificationSystem.sendNotification(
                "🎯 50% TARGET ACHIEVED!",
                formatMilestoneNotification(activeCall, result, "50%")
            );
            logger.info("📈 50% target achieved for call: {}", activeCall.getCallId());
        }
        
        // 100% target achievement
        if (!activeCall.isNotified100Percent() && result.targetAchievement >= TARGET_100_PERCENT) {
            activeCall.setNotified100Percent(true);
            notificationSystem.sendNotification(
                "🎉 100% TARGET ACHIEVED!",
                formatMilestoneNotification(activeCall, result, "100%")
            );
            logger.info("🎯 100% target achieved for call: {}", activeCall.getCallId());
        }
    }
    
    /**
     * Check for movement direction notifications
     */
    private void checkDirectionChangeNotifications(ActiveCall activeCall, MonitoringResult result) {
        if (result.isMovingAsPerPrediction && !activeCall.isMovementStartedNotified()) {
            activeCall.setMovementStartedNotified(true);
            notificationSystem.sendNotification(
                "📈 MOVEMENT STARTED AS PREDICTED!",
                formatMovementNotification(activeCall, result)
            );
            logger.info("✅ Movement started as predicted for call: {}", activeCall.getCallId());
        }
    }
    
    /**
     * Check for wrong call notifications
     */
    private void checkWrongCallNotifications(ActiveCall activeCall, MonitoringResult result) {
        if (result.isWrongDirection && !activeCall.isWrongDirectionNotified()) {
            activeCall.setWrongDirectionNotified(true);
            
            // Calculate cost to close
            double closingCost = calculateClosingCost(activeCall, result.currentPrice);
            
            notificationSystem.sendNotification(
                "⚠️ CALL GOING WRONG DIRECTION!",
                formatWrongCallNotification(activeCall, result, closingCost)
            );
            logger.warn("❌ Call going wrong direction: {}", activeCall.getCallId());
        }
        
        // Check if call should be closed due to losses
        if (result.shouldCloseCall && result.percentageMovement < STOP_LOSS_THRESHOLD) {
            notificationSystem.sendNotification(
                "🛑 IMMEDIATE CLOSURE REQUIRED!",
                formatEmergencyClosureNotification(activeCall, result)
            );
        }
    }
    
    /**
     * Check target achievement notifications
     */
    private void checkTargetAchievementNotifications(ActiveCall activeCall, MonitoringResult result) {
        // After achieving target, monitor for reversal
        if (activeCall.isNotified100Percent() && result.targetAchievement < 0.8) {
            notificationSystem.sendNotification(
                "⚠️ TARGET REVERSAL DETECTED!",
                formatReversalNotification(activeCall, result)
            );
        }
    }
    
    /**
     * Close a call and move to historical records
     */
    private void closeCall(ActiveCall activeCall, String reason) {
        try {
            activeCall.setClosedTime(LocalDateTime.now());
            activeCall.setClosureReason(reason);
            
            // Calculate final performance
            MonitoringResult finalResult = activeCall.getLatestResult();
            CallPerformanceRecord record = new CallPerformanceRecord(activeCall, finalResult);
            historicalRecords.add(record);
            
            // Remove from active monitoring
            activeCallsMap.remove(activeCall.getCallId());
            
            // Send closure notification
            notificationSystem.sendNotification(
                "📊 CALL MONITORING CLOSED",
                formatCallClosureNotification(activeCall, finalResult, reason)
            );
            
            logger.info("🔚 Closed monitoring for call: {} - Reason: {}", activeCall.getCallId(), reason);
            
        } catch (Exception e) {
            logger.error("Error closing call {}: {}", activeCall.getCallId(), e.getMessage());
        }
    }
    
    // ================================
    // HELPER METHODS
    // ================================
    
    /**
     * Get current market price (implement with your market data provider)
     */
    private double getCurrentMarketPrice(String symbol) {
        // TODO: Implement with actual market data provider
        // For now, simulate price movement
        Random random = new Random();
        double basePrice = symbol.equals("NIFTY") ? 23500 : 50000;
        return basePrice + (random.nextGaussian() * 100);
    }
    
    /**
     * Check if movement is as per prediction
     */
    private boolean isMovementAsPerPrediction(ActiveCall activeCall, double priceMovement) {
        String expectedDirection = activeCall.getExpectedDirection();
        
        if ("BULLISH".equals(expectedDirection) || "CALL".equals(expectedDirection)) {
            return priceMovement > 0;
        } else if ("BEARISH".equals(expectedDirection) || "PUT".equals(expectedDirection)) {
            return priceMovement < 0;
        }
        
        return false;
    }
    
    /**
     * Calculate target achievement percentage
     */
    private double calculateTargetAchievement(ActiveCall activeCall, double currentPrice) {
        double entryPrice = activeCall.getEntryPrice();
        double targetPrice = activeCall.getTargetPrice();
        
        double targetDistance = Math.abs(targetPrice - entryPrice);
        double currentDistance = Math.abs(currentPrice - entryPrice);
        
        // Check if moving in right direction
        boolean rightDirection = isMovementAsPerPrediction(activeCall, currentPrice - entryPrice);
        
        if (!rightDirection) {
            return -currentDistance / targetDistance; // Negative achievement
        }
        
        return Math.min(1.0, currentDistance / targetDistance);
    }
    
    /**
     * Check if call is going in wrong direction
     */
    private boolean checkWrongDirection(ActiveCall activeCall, double percentageMovement) {
        String expectedDirection = activeCall.getExpectedDirection();
        
        if ("BULLISH".equals(expectedDirection) || "CALL".equals(expectedDirection)) {
            return percentageMovement < WRONG_DIRECTION_THRESHOLD;
        } else if ("BEARISH".equals(expectedDirection) || "PUT".equals(expectedDirection)) {
            return percentageMovement > -WRONG_DIRECTION_THRESHOLD;
        }
        
        return false;
    }
    
    /**
     * Determine if call should be closed
     */
    private boolean shouldCloseCall(ActiveCall activeCall, double percentageMovement, 
                                   double targetAchievement, boolean isWrongDirection) {
        
        // Close if stop loss hit
        if (Math.abs(percentageMovement) > Math.abs(STOP_LOSS_THRESHOLD) && isWrongDirection) {
            return true;
        }
        
        // Close if target achieved and showing reversal signs
        if (targetAchievement >= TARGET_100_PERCENT && activeCall.getMonitoringResults().size() > 10) {
            // Check last few results for reversal
            List<MonitoringResult> recent = activeCall.getRecentResults(5);
            long negativeMovements = recent.stream()
                .mapToLong(r -> r.targetAchievement < 0.9 ? 1 : 0)
                .sum();
            
            return negativeMovements >= 3; // 3 out of 5 showing reversal
        }
        
        return false;
    }
    
    /**
     * Calculate profit/loss for the call
     */
    private double calculateProfitLoss(ActiveCall activeCall, double currentPrice) {
        // Simplified P&L calculation - implement with actual option pricing
        double entryPrice = activeCall.getEntryPrice();
        double movement = (currentPrice - entryPrice) / entryPrice;
        
        // For options, movement is amplified
        double optionMultiplier = activeCall.getCallType().contains("CE") ? 1.0 : 
                                 activeCall.getCallType().contains("PE") ? -1.0 : 1.0;
        
        return movement * optionMultiplier * activeCall.getInvestmentAmount() * 10; // Options leverage
    }
    
    /**
     * Calculate cost to close position
     */
    private double calculateClosingCost(ActiveCall activeCall, double currentPrice) {
        double profitLoss = calculateProfitLoss(activeCall, currentPrice);
        return activeCall.getInvestmentAmount() + profitLoss;
    }
    
    // ================================
    // NOTIFICATION FORMATTERS
    // ================================
    
    private String formatNewCallNotification(ActiveCall activeCall) {
        return String.format(
            "📊 CALL: %s\n" +
            "🎯 TARGET: ₹%.2f\n" +
            "⏰ TIME: %s\n" +
            "📈 DIRECTION: %s\n" +
            "🔍 MONITORING: ACTIVE",
            activeCall.getSymbol(),
            activeCall.getTargetPrice(),
            activeCall.getGeneratedTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
            activeCall.getExpectedDirection()
        );
    }
    
    private String formatMilestoneNotification(ActiveCall activeCall, MonitoringResult result, String milestone) {
        return String.format(
            "🎯 %s TARGET ACHIEVED!\n" +
            "📊 CALL: %s\n" +
            "💰 CURRENT: ₹%.2f\n" +
            "📈 MOVEMENT: %.2f%%\n" +
            "💵 P&L: ₹%.2f\n" +
            "⏰ TIME: %s",
            milestone,
            activeCall.getSymbol(),
            result.currentPrice,
            result.percentageMovement,
            result.profitLoss,
            result.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
    }
    
    private String formatMovementNotification(ActiveCall activeCall, MonitoringResult result) {
        return String.format(
            "📈 MOVEMENT STARTED!\n" +
            "📊 CALL: %s\n" +
            "✅ DIRECTION: As Predicted (%s)\n" +
            "📈 MOVEMENT: %.2f%%\n" +
            "🎯 TARGET PROGRESS: %.1f%%\n" +
            "⏰ TIME: %s",
            activeCall.getSymbol(),
            activeCall.getExpectedDirection(),
            result.percentageMovement,
            result.targetAchievement * 100,
            result.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
    }
    
    private String formatWrongCallNotification(ActiveCall activeCall, MonitoringResult result, double closingCost) {
        return String.format(
            "⚠️ WRONG DIRECTION DETECTED!\n" +
            "📊 CALL: %s\n" +
            "❌ MOVEMENT: %.2f%% (Against prediction)\n" +
            "💸 CURRENT LOSS: ₹%.2f\n" +
            "💰 CLOSE AT: ₹%.2f\n" +
            "🚨 RECOMMENDATION: Consider closing\n" +
            "⏰ TIME: %s",
            activeCall.getSymbol(),
            result.percentageMovement,
            Math.abs(result.profitLoss),
            closingCost,
            result.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
    }
    
    private String formatReversalNotification(ActiveCall activeCall, MonitoringResult result) {
        return String.format(
            "🔄 TARGET REVERSAL DETECTED!\n" +
            "📊 CALL: %s\n" +
            "📉 REVERSAL: From 100%% to %.1f%%\n" +
            "💰 CURRENT: ₹%.2f\n" +
            "💵 P&L: ₹%.2f\n" +
            "⚠️ CONSIDER: Booking profits or closing\n" +
            "⏰ TIME: %s",
            activeCall.getSymbol(),
            result.targetAchievement * 100,
            result.currentPrice,
            result.profitLoss,
            result.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
    }
    
    private String formatEmergencyClosureNotification(ActiveCall activeCall, MonitoringResult result) {
        return String.format(
            "🛑 EMERGENCY CLOSURE REQUIRED!\n" +
            "📊 CALL: %s\n" +
            "📉 LOSS: %.2f%% (Stop loss hit)\n" +
            "💸 TOTAL LOSS: ₹%.2f\n" +
            "🚨 ACTION: CLOSE IMMEDIATELY\n" +
            "⏰ TIME: %s",
            activeCall.getSymbol(),
            result.percentageMovement,
            Math.abs(result.profitLoss),
            result.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
    }
    
    private String formatCallClosureNotification(ActiveCall activeCall, MonitoringResult finalResult, String reason) {
        return String.format(
            "📊 CALL MONITORING CLOSED\n" +
            "📊 CALL: %s\n" +
            "🏁 REASON: %s\n" +
            "⏱️ DURATION: %d minutes\n" +
            "📈 FINAL MOVEMENT: %.2f%%\n" +
            "💵 FINAL P&L: ₹%.2f\n" +
            "🎯 TARGET ACHIEVED: %.1f%%\n" +
            "⏰ CLOSED: %s",
            activeCall.getSymbol(),
            reason,
            activeCall.getDurationInMinutes(),
            finalResult.percentageMovement,
            finalResult.profitLoss,
            finalResult.targetAchievement * 100,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
    }
    
    /**
     * Generate performance summary
     */
    private void generatePerformanceSummary() {
        if (activeCallsMap.isEmpty() && historicalRecords.isEmpty()) {
            return;
        }
        
        try {
            int activeCalls = activeCallsMap.size();
            int totalCalls = historicalRecords.size();
            
            double totalPnL = historicalRecords.stream()
                .mapToDouble(record -> record.getFinalProfitLoss())
                .sum();
            
            long successfulCalls = historicalRecords.stream()
                .mapToLong(record -> record.getFinalProfitLoss() > 0 ? 1 : 0)
                .sum();
            
            double successRate = totalCalls > 0 ? (successfulCalls * 100.0 / totalCalls) : 0;
            
            String summary = String.format(
                "📈 PERFORMANCE SUMMARY\n" +
                "====================\n" +
                "📊 Active Calls: %d\n" +
                "🏁 Completed Calls: %d\n" +
                "✅ Success Rate: %.1f%%\n" +
                "💰 Total P&L: ₹%.2f\n" +
                "⏰ Time: %s",
                activeCalls,
                totalCalls,
                successRate,
                totalPnL,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            );
            
            logger.info("Performance Summary:\n{}", summary);
            
        } catch (Exception e) {
            logger.error("Error generating performance summary: {}", e.getMessage());
        }
    }
    
    /**
     * Stop monitoring system
     */
    public void stopMonitoring() {
        isMonitoringActive = false;
        
        if (monitoringScheduler != null && !monitoringScheduler.isShutdown()) {
            monitoringScheduler.shutdown();
            try {
                if (!monitoringScheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    monitoringScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitoringScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("🛑 Call monitoring system stopped");
    }
    
    // ================================
    // GETTERS FOR EXTERNAL ACCESS
    // ================================
    
    public Map<String, ActiveCall> getActiveCallsMap() {
        return new HashMap<>(activeCallsMap);
    }
    
    public List<CallPerformanceRecord> getHistoricalRecords() {
        return new ArrayList<>(historicalRecords);
    }
    
    public boolean isMonitoringActive() {
        return isMonitoringActive;
    }
}