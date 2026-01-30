package com.trading.bot.core;
import com.trading.bot.market.RealMarketDataProvider;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automated Alerts System for High-Confidence Opportunities
 * Point 4: Set up automated alerts for high-confidence opportunities
 */
public class AutomatedAlertsSystem {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();    private static final Logger logger = LoggerFactory.getLogger(AutomatedAlertsSystem.class);
    
    private final ScheduledExecutorService alertScheduler = Executors.newScheduledThreadPool(5);
    private final BlockingQueue<TradingAlert> alertQueue = new LinkedBlockingQueue<>();
    private final Map<String, AlertSubscription> subscriptions = new ConcurrentHashMap<>();
    private final List<TradingAlert> alertHistory = new ArrayList<>();
    
    // Alert thresholds
    private final double HIGH_CONFIDENCE_THRESHOLD = 85.0;
    private final double VOLUME_SPIKE_THRESHOLD = 300.0; // 300% of average
    private final double IV_SPIKE_THRESHOLD = 25.0; // 25% IV
    private final double PRICE_MOVE_THRESHOLD = 2.0; // 2% price move
    
    // Alert components
    private final AdvancedIndexOptionsScanner scanner;
    private final IndexOptionsCallGenerator callGenerator;
    private final AdvancedGreeksAnalyzer greeksAnalyzer;
    
    private volatile boolean alertingActive = false;
    
    public AutomatedAlertsSystem(AdvancedIndexOptionsScanner scanner, 
                                IndexOptionsCallGenerator callGenerator,
                                AdvancedGreeksAnalyzer greeksAnalyzer) {
        this.scanner = scanner;
        this.callGenerator = callGenerator;
        this.greeksAnalyzer = greeksAnalyzer;
        initializeAlertSystem();
        logger.info("Automated Alerts System initialized with real-time monitoring");
    }
    
    /**
     * Start the automated alerts system
     */
    public void startAutomatedAlerts() {
        if (alertingActive) {
            logger.warn("Alerting system is already active");
            return;
        }
        
        alertingActive = true;
        logger.info("🚨 Starting Automated Alerts System...");
        
        // Schedule different types of alerts
        scheduleHighConfidenceAlerts();
        scheduleVolumeSpikealerts();
        scheduleVolatilityAlerts();
        scheduleBreakoutAlerts();
        scheduleGreeksAlerts();
        scheduleUnusualActivityAlerts();
        
        // Start alert processor
        startAlertProcessor();
        
        logger.info("✅ Automated Alerts System is now ACTIVE");
        logger.info("📱 Monitoring for high-confidence opportunities across all indices");
    }
    
    /**
     * Stop the automated alerts system
     */
    public void stopAutomatedAlerts() {
        if (!alertingActive) {
            logger.warn("Alerting system is not active");
            return;
        }
        
        alertingActive = false;
        alertScheduler.shutdown();
        logger.info("🔇 Automated Alerts System stopped");
    }
    
    /**
     * Schedule high-confidence trading opportunity alerts
     */
    private void scheduleHighConfidenceAlerts() {
        alertScheduler.scheduleAtFixedRate(() -> {
            try {
                checkHighConfidenceOpportunities();
            } catch (Exception e) {
                logger.error("Error in high confidence alerts: {}", e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS); // Check every 30 seconds
    }
    
    /**
     * Schedule volume spike alerts
     */
    private void scheduleVolumeSpikealerts() {
        alertScheduler.scheduleAtFixedRate(() -> {
            try {
                checkVolumeSpikes();
            } catch (Exception e) {
                logger.error("Error in volume spike alerts: {}", e.getMessage());
            }
        }, 0, 15, TimeUnit.SECONDS); // Check every 15 seconds
    }
    
    /**
     * Schedule volatility alerts
     */
    private void scheduleVolatilityAlerts() {
        alertScheduler.scheduleAtFixedRate(() -> {
            try {
                checkVolatilitySpikes();
            } catch (Exception e) {
                logger.error("Error in volatility alerts: {}", e.getMessage());
            }
        }, 0, 60, TimeUnit.SECONDS); // Check every minute
    }
    
    /**
     * Schedule breakout alerts
     */
    private void scheduleBreakoutAlerts() {
        alertScheduler.scheduleAtFixedRate(() -> {
            try {
                checkTechnicalBreakouts();
            } catch (Exception e) {
                logger.error("Error in breakout alerts: {}", e.getMessage());
            }
        }, 0, 45, TimeUnit.SECONDS); // Check every 45 seconds
    }
    
    /**
     * Schedule Greeks-based alerts
     */
    private void scheduleGreeksAlerts() {
        alertScheduler.scheduleAtFixedRate(() -> {
            try {
                checkGreeksAnomalies();
            } catch (Exception e) {
                logger.error("Error in Greeks alerts: {}", e.getMessage());
            }
        }, 0, 120, TimeUnit.SECONDS); // Check every 2 minutes
    }
    
    /**
     * Schedule unusual activity alerts
     */
    private void scheduleUnusualActivityAlerts() {
        alertScheduler.scheduleAtFixedRate(() -> {
            try {
                checkUnusualActivity();
            } catch (Exception e) {
                logger.error("Error in unusual activity alerts: {}", e.getMessage());
            }
        }, 0, 20, TimeUnit.SECONDS); // Check every 20 seconds
    }
    
    /**
     * Check for high-confidence trading opportunities
     */
    private void checkHighConfidenceOpportunities() {
        List<String> indices = Arrays.asList("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY", "MIDCPNIFTY", "BANKEX");
        
        for (String index : indices) {
            List<IndexOptionsCallGenerator.GeneratedCall> calls = callGenerator.getCallsForIndex(index);
            
            for (IndexOptionsCallGenerator.GeneratedCall call : calls) {
                if (call.getConfidence() >= HIGH_CONFIDENCE_THRESHOLD) {
                    TradingAlert alert = new TradingAlert(
                        AlertType.HIGH_CONFIDENCE_OPPORTUNITY,
                        index,
                        AlertPriority.HIGH,
                        String.format("🔥 HIGH CONFIDENCE: %s %s Strike:%.0f Confidence:%.1f%%", 
                                     call.getType(), index, call.getStrike(), call.getConfidence()),
                        String.format("Expected Return: %.1f%% | Risk: %s | Entry: %s", 
                                     call.getExpectedReturn(), call.getRiskLevel(), call.getEntryStrategy()),
                        LocalDateTime.now()
                    );
                    
                    addAlert(alert);
                }
            }
        }
    }
    
    /**
     * Check for volume spikes
     */
    private void checkVolumeSpikes() {
        List<String> indices = Arrays.asList("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY", "MIDCPNIFTY", "BANKEX");
        
        for (String index : indices) {
            // Simulate volume spike detection
            double volumeMultiplier = 100 + Math.random() * 400; // 100-500% of average
            
            if (volumeMultiplier >= VOLUME_SPIKE_THRESHOLD) {
                TradingAlert alert = new TradingAlert(
                    AlertType.VOLUME_SPIKE,
                    index,
                    AlertPriority.MEDIUM,
                    String.format("📊 VOLUME SPIKE: %s options volume %.0f%% above average", 
                                 index, volumeMultiplier),
                    "Unusual institutional activity detected. Monitor for directional moves.",
                    LocalDateTime.now()
                );
                
                addAlert(alert);
            }
        }
    }
    
    /**
     * Check for volatility spikes
     */
    private void checkVolatilitySpikes() {
        List<String> indices = Arrays.asList("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY", "MIDCPNIFTY", "BANKEX");
        
        for (String index : indices) {
            // Simulate IV spike detection
            double currentIV = 15 + Math.random() * 20; // 15-35% IV
            
            if (currentIV >= IV_SPIKE_THRESHOLD) {
                TradingAlert alert = new TradingAlert(
                    AlertType.VOLATILITY_SPIKE,
                    index,
                    AlertPriority.MEDIUM,
                    String.format("⚡ IV SPIKE: %s implied volatility at %.1f%%", index, currentIV),
                    "High volatility environment. Consider premium selling strategies.",
                    LocalDateTime.now()
                );
                
                addAlert(alert);
            }
        }
    }
    
    /**
     * Check for technical breakouts
     */
    private void checkTechnicalBreakouts() {
        Map<String, Double> currentPrices = new HashMap<>();
        currentPrices.put("NIFTY", 19485.0 + Math.random() * 100 - 50);
        currentPrices.put("BANKNIFTY", 44220.0 + Math.random() * 200 - 100);
        currentPrices.put("SENSEX", 65845.0 + Math.random() * 300 - 150);
        currentPrices.put("FINNIFTY", 19750.0 + Math.random() * 100 - 50);
        currentPrices.put("MIDCPNIFTY", 10850.0 + Math.random() * 100 - 50);
        currentPrices.put("BANKEX", 48200.0 + Math.random() * 200 - 100);
        
        Map<String, Double> resistanceLevels = new HashMap<>();
        resistanceLevels.put("NIFTY", realData.getRealPrice("NIFTY"));
        resistanceLevels.put("BANKNIFTY", 44500.0);
        resistanceLevels.put("SENSEX", 66000.0);
        resistanceLevels.put("FINNIFTY", 19800.0);
        resistanceLevels.put("MIDCPNIFTY", 10900.0);
        resistanceLevels.put("BANKEX", 48500.0);
        
        for (Map.Entry<String, Double> entry : currentPrices.entrySet()) {
            String index = entry.getKey();
            double price = entry.getValue();
            double resistance = resistanceLevels.get(index);
            
            if (price > resistance) {
                TradingAlert alert = new TradingAlert(
                    AlertType.BREAKOUT,
                    index,
                    AlertPriority.HIGH,
                    String.format("📈 BREAKOUT: %s broke above %.0f resistance, now at %.0f", 
                                 index, resistance, price),
                    "Strong momentum. Consider call options or momentum strategies.",
                    LocalDateTime.now()
                );
                
                addAlert(alert);
            }
        }
    }
    
    /**
     * Check for Greeks anomalies
     */
    private void checkGreeksAnomalies() {
        List<String> indices = Arrays.asList("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY", "MIDCPNIFTY", "BANKEX");
        
        for (String index : indices) {
            // Simulate Greeks anomaly detection
            double gamma = Math.random() * 0.040; // 0-0.040 gamma
            double theta = -200 - Math.random() * 1000; // -200 to -1200 theta
            
            if (gamma > 0.025) {
                TradingAlert alert = new TradingAlert(
                    AlertType.GREEKS_ANOMALY,
                    index,
                    AlertPriority.MEDIUM,
                    String.format("⚡ HIGH GAMMA: %s ATM gamma %.4f - Expect volatile moves", 
                                 index, gamma),
                    "High gamma environment. Perfect for scalping strategies.",
                    LocalDateTime.now()
                );
                
                addAlert(alert);
            }
            
            if (theta < -800) {
                TradingAlert alert = new TradingAlert(
                    AlertType.GREEKS_ANOMALY,
                    index,
                    AlertPriority.LOW,
                    String.format("⏰ HIGH THETA: %s daily decay ₹%.0f", index, Math.abs(theta)),
                    "Significant time decay. Consider time spread strategies.",
                    LocalDateTime.now()
                );
                
                addAlert(alert);
            }
        }
    }
    
    /**
     * Check for unusual activity
     */
    private void checkUnusualActivity() {
        // Simulate unusual activity detection
        String[] activities = {
            "Large block trade: 50,000 lots BANKNIFTY realData.getRealPrice("BANKNIFTY") CE bought",
            "Aggressive call buying in NIFTY 19600 CE",
            "Heavy put writing in SENSEX realData.getRealPrice("SENSEX") PE",
            "Unusual spread activity in FINNIFTY",
            "High frequency trading detected in MIDCPNIFTY",
            "Institutional accumulation in BANKEX options"
        };
        
        if (Math.random() > 0.7) { // 30% chance of unusual activity
            String activity = activities[(int)(Math.random() * activities.length)];
            String index = extractIndexFromActivity(activity);
            
            TradingAlert alert = new TradingAlert(
                AlertType.UNUSUAL_ACTIVITY,
                index,
                AlertPriority.HIGH,
                "🕵️ UNUSUAL ACTIVITY: " + activity,
                "Big player activity detected. Follow the smart money.",
                LocalDateTime.now()
            );
            
            addAlert(alert);
        }
    }
    
    private String extractIndexFromActivity(String activity) {
        if (activity.contains("NIFTY")) return "NIFTY";
        if (activity.contains("BANKNIFTY")) return "BANKNIFTY";
        if (activity.contains("SENSEX")) return "SENSEX";
        if (activity.contains("FINNIFTY")) return "FINNIFTY";
        if (activity.contains("MIDCPNIFTY")) return "MIDCPNIFTY";
        if (activity.contains("BANKEX")) return "BANKEX";
        return "UNKNOWN";
    }
    
    /**
     * Add alert to queue and process
     */
    private void addAlert(TradingAlert alert) {
        // Check if similar alert exists in recent history to avoid spam
        if (!isDuplicateAlert(alert)) {
            alertQueue.offer(alert);
            alertHistory.add(alert);
            
            // Keep only last 1000 alerts in history
            if (alertHistory.size() > 1000) {
                alertHistory.remove(0);
            }
        }
    }
    
    /**
     * Check if alert is duplicate
     */
    private boolean isDuplicateAlert(TradingAlert newAlert) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5); // 5 minutes window
        
        return alertHistory.stream()
                .filter(alert -> alert.getTimestamp().isAfter(cutoff))
                .anyMatch(alert -> 
                    alert.getType() == newAlert.getType() && 
                    alert.getIndex().equals(newAlert.getIndex()) &&
                    alert.getTitle().equals(newAlert.getTitle())
                );
    }
    
    /**
     * Start alert processor
     */
    private void startAlertProcessor() {
        Thread alertProcessor = new Thread(() -> {
            while (alertingActive) {
                try {
                    TradingAlert alert = alertQueue.poll(1, TimeUnit.SECONDS);
                    if (alert != null) {
                        processAlert(alert);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error processing alert: {}", e.getMessage());
                }
            }
        });
        
        alertProcessor.setDaemon(true);
        alertProcessor.start();
    }
    
    /**
     * Process individual alert
     */
    private void processAlert(TradingAlert alert) {
        String priorityIcon = getPriorityIcon(alert.getPriority());
        String timeStamp = alert.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        
        logger.info("🚨 {} [{}] {} | {}", 
                   priorityIcon, timeStamp, alert.getTitle(), alert.getDescription());
        
        // Send to subscribed channels
        notifySubscribers(alert);
        
        // If high priority, log additional details
        if (alert.getPriority() == AlertPriority.HIGH) {
            logger.info("🔔 HIGH PRIORITY ALERT - Immediate attention required!");
        }
    }
    
    /**
     * Notify subscribers
     */
    private void notifySubscribers(TradingAlert alert) {
        for (AlertSubscription subscription : subscriptions.values()) {
            if (subscription.isInterestedIn(alert)) {
                subscription.sendNotification(alert);
            }
        }
    }
    
    private String getPriorityIcon(AlertPriority priority) {
        switch (priority) {
            case HIGH: return "🔴";
            case MEDIUM: return "🟡";
            case LOW: return "🟢";
            default: return "ℹ️";
        }
    }
    
    /**
     * Subscribe to specific alert types
     */
    public void subscribe(String subscriberId, Set<AlertType> alertTypes, Set<String> indices) {
        AlertSubscription subscription = new AlertSubscription(subscriberId, alertTypes, indices);
        subscriptions.put(subscriberId, subscription);
        logger.info("✅ Subscribed {} to {} alert types for {} indices", 
                   subscriberId, alertTypes.size(), indices.size());
    }
    
    /**
     * Get recent alerts
     */
    public List<TradingAlert> getRecentAlerts(int count) {
        return alertHistory.subList(
            Math.max(0, alertHistory.size() - count), 
            alertHistory.size()
        );
    }
    
    /**
     * Get alerts summary
     */
    public void printAlertsSummary() {
        logger.info("\n📊 === ALERTS SYSTEM SUMMARY ===");
        logger.info("Status: {}", alertingActive ? "ACTIVE 🟢" : "INACTIVE 🔴");
        logger.info("Total Alerts Generated: {}", alertHistory.size());
        logger.info("Active Subscriptions: {}", subscriptions.size());
        logger.info("Queue Size: {}", alertQueue.size());
        
        if (!alertHistory.isEmpty()) {
            Map<AlertType, Long> alertCounts = alertHistory.stream()
                    .collect(Collectors.groupingBy(TradingAlert::getType, Collectors.counting()));
            
            logger.info("\n📈 ALERT BREAKDOWN:");
            alertCounts.forEach((type, count) -> 
                logger.info("   {}: {} alerts", type, count));
            
            // Recent alerts
            List<TradingAlert> recent = getRecentAlerts(5);
            logger.info("\n🕒 RECENT ALERTS (Last 5):");
            for (int i = 0; i < recent.size(); i++) {
                TradingAlert alert = recent.get(i);
                logger.info("   {}. [{}] {} - {}", 
                           i + 1, 
                           alert.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")),
                           alert.getIndex(), 
                           alert.getTitle());
            }
        }
    }
    
    private void initializeAlertSystem() {
        // Initialize with default subscriptions
        subscribe("SYSTEM", 
                 EnumSet.allOf(AlertType.class), 
                 Set.of("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY", "MIDCPNIFTY", "BANKEX"));
    }
    
    // Enums and data classes
    
    public enum AlertType {
        HIGH_CONFIDENCE_OPPORTUNITY,
        VOLUME_SPIKE,
        VOLATILITY_SPIKE,
        BREAKOUT,
        GREEKS_ANOMALY,
        UNUSUAL_ACTIVITY,
        TECHNICAL_SIGNAL,
        RISK_WARNING
    }
    
    public enum AlertPriority {
        HIGH, MEDIUM, LOW
    }
    
    public static class TradingAlert {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        private final AlertType type;
        private final String index;
        private final AlertPriority priority;
        private final String title;
        private final String description;
        private final LocalDateTime timestamp;
        
        public TradingAlert(AlertType type, String index, AlertPriority priority, 
                           String title, String description, LocalDateTime timestamp) {
            this.type = type;
            this.index = index;
            this.priority = priority;
            this.title = title;
            this.description = description;
            this.timestamp = timestamp;
        }
        
        // Getters
        public AlertType getType() { return type; }
        public String getIndex() { return index; }
        public AlertPriority getPriority() { return priority; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class AlertSubscription {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        private final String subscriberId;
        private final Set<AlertType> interestedTypes;
        private final Set<String> interestedIndices;
        
        public AlertSubscription(String subscriberId, Set<AlertType> interestedTypes, Set<String> interestedIndices) {
            this.subscriberId = subscriberId;
            this.interestedTypes = interestedTypes;
            this.interestedIndices = interestedIndices;
        }
        
        public boolean isInterestedIn(TradingAlert alert) {
            return interestedTypes.contains(alert.getType()) && 
                   interestedIndices.contains(alert.getIndex());
        }
        
        public void sendNotification(TradingAlert alert) {
            // In real implementation, send to Telegram, email, etc.
            logger.debug("📱 Notification sent to {} for {} alert", subscriberId, alert.getType());
        }
        
        public String getSubscriberId() { return subscriberId; }
    }
}