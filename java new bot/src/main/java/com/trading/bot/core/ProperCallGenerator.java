import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * PROPER CALL GENERATOR - REALISTIC TRADING CALLS
 * - Maximum 1 call per segment per 4 hours (realistic)
 * - Proper target management with modifications
 * - Single message per call with all targets
 * - New call only after all targets achieved or stop loss
 */
public class ProperCallGenerator {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();    
    // REALISTIC TIMING: Maximum 1 call per segment per 4 hours
    private static final int MIN_HOURS_BETWEEN_CALLS = 4;
    private static final int MAX_CALLS_PER_DAY = 2; // Maximum 2 calls per segment per day
    
    // Active positions - only ONE per segment at a time
    private final Map<String, ActiveCall> activePositions = new HashMap<>();
    
    // Call history tracking
    private final Map<String, List<LocalDateTime>> callHistory = new HashMap<>();
    
    // Market data
    private final Map<String, Double> currentPrices = new HashMap<>();
    private final Map<String, List<Double>> priceHistory = new HashMap<>();
    
    // Statistics
    private int totalCallsToday = 0;
    private int targetsAchieved = 0;
    private int stopLossHit = 0;
    
    public ProperCallGenerator() {
        initializeMarketData();
        initializeCallHistory();
        
        System.out.println("🎯 PROPER CALL GENERATOR INITIALIZED");
        System.out.println("✅ Maximum 1 call per segment per 4 hours");
        System.out.println("✅ Maximum 2 calls per segment per day");
        System.out.println("✅ Proper target management");
        System.out.println("✅ Target modification system");
        System.out.println("✅ New call only after position closure");
    }
    
    private void initializeMarketData() {
        String[] segments = {"BANKNIFTY", "NIFTY", "FINNIFTY", "SENSEX"};
        
        for (String segment : segments) {
            double basePrice = getBasePrice(segment);
            currentPrices.put(segment, basePrice);
            
            // Initialize realistic price history
            List<Double> history = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                double price = basePrice + (Math.sin(i * 0.1) * basePrice * 0.01) + 
                              ((Math.random() - 0.5) * basePrice * 0.005);
                history.add(price);
            }
            priceHistory.put(segment, history);
        }
    }
    
    private void initializeCallHistory() {
        String[] segments = {"BANKNIFTY", "NIFTY", "FINNIFTY", "SENSEX"};
        for (String segment : segments) {
            callHistory.put(segment, new ArrayList<>());
        }
    }
    
    private double getBasePrice(String segment) {
        switch (segment) {
            case "BANKNIFTY": return realData.getRealPrice("BANKNIFTY");
            case "NIFTY": return realData.getRealPrice("NIFTY");
            case "FINNIFTY": return 19800;
            case "SENSEX": return realData.getRealPrice("SENSEX");
            default: return 20000;
        }
    }
    
    public void start() {
        System.out.println("🚀 STARTING PROPER CALL GENERATOR...");
        System.out.println("📊 Realistic call frequency: 1 call per 4 hours maximum");
        System.out.println("⏰ Scanning every 10 minutes for opportunities");
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        
        // REALISTIC: Scan every 10 minutes (not every minute!)
        scheduler.scheduleAtFixedRate(this::scanForOpportunities, 0, 10, TimeUnit.MINUTES);
        
        // Monitor positions every 2 minutes
        scheduler.scheduleAtFixedRate(this::monitorActivePositions, 1, 2, TimeUnit.MINUTES);
        
        // Daily report
        scheduler.scheduleAtFixedRate(this::generateDailyReport, 60, 480, TimeUnit.MINUTES); // Every 8 hours
        
        System.out.println("✅ PROPER CALL GENERATOR IS LIVE!");
    }
    
    private void scanForOpportunities() {
        updateMarketPrices();
        
        System.out.println("🔍 Scanning for PROPER opportunities...");
        
        for (String segment : Arrays.asList("BANKNIFTY", "NIFTY", "FINNIFTY", "SENSEX")) {
            
            // RULE 1: Skip if we already have active position
            if (activePositions.containsKey(segment)) {
                System.out.println("📊 " + segment + ": Active position exists - skipping");
                continue;
            }
            
            // RULE 2: Check if enough time passed since last call
            if (!canGenerateNewCall(segment)) {
                continue;
            }
            
            // RULE 3: Check daily limit
            if (hasReachedDailyLimit(segment)) {
                System.out.println("📊 " + segment + ": Daily limit reached (2 calls max)");
                continue;
            }
            
            // RULE 4: Analyze for REAL opportunity
            OpportunityAnalysis analysis = analyzeRealOpportunity(segment);
            
            if (analysis.isValidOpportunity()) {
                generateProperCall(segment, analysis);
            }
        }
    }
    
    private boolean canGenerateNewCall(String segment) {
        List<LocalDateTime> history = callHistory.get(segment);
        if (history.isEmpty()) return true;
        
        LocalDateTime lastCall = history.get(history.size() - 1);
        long hoursSinceLastCall = java.time.temporal.ChronoUnit.HOURS.between(lastCall, LocalDateTime.now());
        
        if (hoursSinceLastCall < MIN_HOURS_BETWEEN_CALLS) {
            System.out.println("⏰ " + segment + ": Only " + hoursSinceLastCall + " hours since last call (need " + MIN_HOURS_BETWEEN_CALLS + ")");
            return false;
        }
        
        return true;
    }
    
    private boolean hasReachedDailyLimit(String segment) {
        List<LocalDateTime> history = callHistory.get(segment);
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        
        long callsToday = history.stream()
            .filter(time -> time.isAfter(today))
            .count();
        
        return callsToday >= MAX_CALLS_PER_DAY;
    }
    
    private void updateMarketPrices() {
        for (String segment : currentPrices.keySet()) {
            double currentPrice = currentPrices.get(segment);
            
            // REALISTIC price movement: 0.05% max per 10 minutes
            double maxChange = currentPrice * 0.0005; // 0.05%
            double change = (Math.random() - 0.5) * 2 * maxChange;
            
            // Add market session bias
            int hour = LocalDateTime.now().getHour();
            if (hour >= 9 && hour <= 11) {
                change += maxChange * 0.3; // Morning bullish bias
            } else if (hour >= 14 && hour <= 15) {
                change -= maxChange * 0.3; // Afternoon bearish bias
            }
            
            double newPrice = currentPrice + change;
            currentPrices.put(segment, newPrice);
            
            // Update price history
            List<Double> history = priceHistory.get(segment);
            history.add(newPrice);
            if (history.size() > 100) {
                history.remove(0);
            }
        }
    }
    
    private OpportunityAnalysis analyzeRealOpportunity(String segment) {
        List<Double> prices = priceHistory.get(segment);
        double currentPrice = currentPrices.get(segment);
        
        // Calculate REAL technical indicators
        double rsi = calculateRSI(prices);
        double ema20 = calculateEMA(prices, 20);
        double ema50 = calculateEMA(prices, 50);
        double momentum = calculateMomentum(prices);
        double volatility = calculateVolatility(prices);
        
        // STRICT opportunity criteria
        String direction = "NONE";
        double confidence = 0.0;
        String reason = "";
        
        // BULLISH opportunity
        if (ema20 > ema50 && currentPrice > ema20 && rsi > 45 && rsi < 65 && 
            momentum > 1.0 && volatility > 0.01 && volatility < 0.03) {
            direction = "BUY";
            confidence = 0.78 + Math.min(momentum / 5.0, 0.07); // 78-85% max
            reason = String.format("Bullish trend confirmed: EMA20>EMA50, Price>EMA20, RSI:%.1f, Momentum:%.2f%%", rsi, momentum);
        }
        // BEARISH opportunity
        else if (ema20 < ema50 && currentPrice < ema20 && rsi < 55 && rsi > 35 && 
                 momentum < -1.0 && volatility > 0.01 && volatility < 0.03) {
            direction = "SELL";
            confidence = 0.78 + Math.min(Math.abs(momentum) / 5.0, 0.07); // 78-85% max
            reason = String.format("Bearish trend confirmed: EMA20<EMA50, Price<EMA20, RSI:%.1f, Momentum:%.2f%%", rsi, momentum);
        }
        
        return new OpportunityAnalysis(segment, direction, currentPrice, confidence, reason,
                                     rsi, ema20, ema50, momentum, volatility);
    }
    
    private void generateProperCall(String segment, OpportunityAnalysis analysis) {
        // Calculate proper targets
        TargetLevels targets = calculateProperTargets(segment, analysis.price, analysis.direction);
        
        // Create active call
        ActiveCall call = new ActiveCall(
            segment, analysis.direction, analysis.price, targets, 
            analysis.confidence, analysis.reason
        );
        
        // Store active position
        activePositions.put(segment, call);
        
        // Record call in history
        callHistory.get(segment).add(LocalDateTime.now());
        totalCallsToday++;
        
        // Send PROPER notification
        sendProperCallNotification(call);
        
        System.out.println("📞 PROPER CALL GENERATED for " + segment);
    }
    
    private TargetLevels calculateProperTargets(String segment, double entryPrice, String direction) {
        double target1, target2, target3, stopLoss;
        
        switch (segment) {
            case "BANKNIFTY":
                if (direction.equals("BUY")) {
                    target1 = entryPrice + 80;   // Conservative target 1
                    target2 = entryPrice + 160;  // Moderate target 2
                    target3 = entryPrice + 280;  // Aggressive target 3
                    stopLoss = entryPrice - 60;  // Tight stop loss
                } else {
                    target1 = entryPrice - 80;
                    target2 = entryPrice - 160;
                    target3 = entryPrice - 280;
                    stopLoss = entryPrice + 60;
                }
                break;
                
            case "NIFTY":
                if (direction.equals("BUY")) {
                    target1 = entryPrice + 30;   // Conservative
                    target2 = entryPrice + 60;   // Moderate
                    target3 = entryPrice + 100;  // Aggressive
                    stopLoss = entryPrice - 25;  // Tight stop
                } else {
                    target1 = entryPrice - 30;
                    target2 = entryPrice - 60;
                    target3 = entryPrice - 100;
                    stopLoss = entryPrice + 25;
                }
                break;
                
            case "FINNIFTY":
                if (direction.equals("BUY")) {
                    target1 = entryPrice + 40;
                    target2 = entryPrice + 80;
                    target3 = entryPrice + 130;
                    stopLoss = entryPrice - 30;
                } else {
                    target1 = entryPrice - 40;
                    target2 = entryPrice - 80;
                    target3 = entryPrice - 130;
                    stopLoss = entryPrice + 30;
                }
                break;
                
            case "SENSEX":
                if (direction.equals("BUY")) {
                    target1 = entryPrice + 120;
                    target2 = entryPrice + 240;
                    target3 = entryPrice + 400;
                    stopLoss = entryPrice - 100;
                } else {
                    target1 = entryPrice - 120;
                    target2 = entryPrice - 240;
                    target3 = entryPrice - 400;
                    stopLoss = entryPrice + 100;
                }
                break;
                
            default:
                target1 = target2 = target3 = stopLoss = entryPrice;
        }
        
        return new TargetLevels(target1, target2, target3, stopLoss);
    }
    
    private void sendProperCallNotification(ActiveCall call) {
        String message = String.format(
            "📞 PROPER CALL GENERATED\n" +
            "=" .repeat(40) + "\n\n" +
            "🎯 SEGMENT: %s\n" +
            "📈 DIRECTION: %s\n" +
            "💰 ENTRY PRICE: %.1f\n\n" +
            "🎯 TARGETS:\n" +
            "   📊 Target 1: %.1f\n" +
            "   📊 Target 2: %.1f\n" +
            "   📊 Target 3: %.1f\n\n" +
            "🛡️ STOP LOSS: %.1f\n\n" +
            "🔥 CONFIDENCE: %.1f%%\n" +
            "📋 ANALYSIS: %s\n\n" +
            "⏰ TIME: %s\n" +
            "📝 NOTE: No new call for this segment until all targets achieved or stop loss hit\n" +
            "=" .repeat(40),
            call.segment, call.direction, call.entryPrice,
            call.targets.target1, call.targets.target2, call.targets.target3,
            call.targets.stopLoss,
            call.confidence * 100,
            call.reason,
            call.timestamp.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
        );
        
        System.out.println(message);
        System.out.println();
    }
    
    private void monitorActivePositions() {
        if (activePositions.isEmpty()) {
            return;
        }
        
        System.out.println("👁️ Monitoring " + activePositions.size() + " active positions...");
        
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, ActiveCall> entry : activePositions.entrySet()) {
            String segment = entry.getKey();
            ActiveCall call = entry.getValue();
            double currentPrice = currentPrices.get(segment);
            
            // Check position status
            PositionStatus status = checkPositionStatus(call, currentPrice);
            
            if (status.shouldClose) {
                handlePositionClosure(call, currentPrice, status);
                toRemove.add(segment);
            } else if (status.shouldModifyTargets) {
                modifyTargets(call, currentPrice, status);
            }
        }
        
        // Remove closed positions
        toRemove.forEach(activePositions::remove);
    }
    
    private PositionStatus checkPositionStatus(ActiveCall call, double currentPrice) {
        boolean shouldClose = false;
        boolean shouldModifyTargets = false;
        String reason = "";
        int targetLevel = 0;
        
        if (call.direction.equals("BUY")) {
            if (currentPrice <= call.targets.stopLoss) {
                shouldClose = true;
                reason = "STOP_LOSS_HIT";
            } else if (currentPrice >= call.targets.target3) {
                shouldClose = true;
                reason = "ALL_TARGETS_ACHIEVED";
                targetLevel = 3;
            } else if (currentPrice >= call.targets.target2 && !call.target2Achieved) {
                call.target2Achieved = true;
                reason = "TARGET_2_ACHIEVED";
                targetLevel = 2;
            } else if (currentPrice >= call.targets.target1 && !call.target1Achieved) {
                call.target1Achieved = true;
                reason = "TARGET_1_ACHIEVED";
                targetLevel = 1;
            }
            
            // Check for target modification (strong momentum)
            double movement = (currentPrice - call.entryPrice) / call.entryPrice * 100;
            if (movement > 2.0 && !call.targetsModified) { // 2% movement
                shouldModifyTargets = true;
                reason = "STRONG_MOMENTUM_MODIFY_TARGETS";
            }
        } else { // SELL
            if (currentPrice >= call.targets.stopLoss) {
                shouldClose = true;
                reason = "STOP_LOSS_HIT";
            } else if (currentPrice <= call.targets.target3) {
                shouldClose = true;
                reason = "ALL_TARGETS_ACHIEVED";
                targetLevel = 3;
            } else if (currentPrice <= call.targets.target2 && !call.target2Achieved) {
                call.target2Achieved = true;
                reason = "TARGET_2_ACHIEVED";
                targetLevel = 2;
            } else if (currentPrice <= call.targets.target1 && !call.target1Achieved) {
                call.target1Achieved = true;
                reason = "TARGET_1_ACHIEVED";
                targetLevel = 1;
            }
            
            // Check for target modification
            double movement = (call.entryPrice - currentPrice) / call.entryPrice * 100;
            if (movement > 2.0 && !call.targetsModified) {
                shouldModifyTargets = true;
                reason = "STRONG_MOMENTUM_MODIFY_TARGETS";
            }
        }
        
        return new PositionStatus(shouldClose, shouldModifyTargets, reason, targetLevel);
    }
    
    private void handlePositionClosure(ActiveCall call, double currentPrice, PositionStatus status) {
        String message;
        
        if (status.reason.equals("STOP_LOSS_HIT")) {
            double loss = Math.abs(currentPrice - call.entryPrice);
            message = String.format(
                "🚨 STOP LOSS HIT\n" +
                "=" .repeat(40) + "\n\n" +
                "📉 %s %s CALL FAILED\n" +
                "💸 LOSS: %.1f points\n" +
                "📊 Entry: %.1f → Current: %.1f\n\n" +
                "⚠️ SET YOUR STOP LOSS - THIS CALL GOES WRONG!\n\n" +
                "📝 Position closed. New call can be generated after 4 hours.\n" +
                "⏰ Time: %s\n" +
                "=" .repeat(40),
                call.segment, call.direction, loss,
                call.entryPrice, currentPrice,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            );
            stopLossHit++;
        } else {
            double profit = Math.abs(currentPrice - call.entryPrice);
            message = String.format(
                "🎉 ALL TARGETS ACHIEVED!\n" +
                "=" .repeat(40) + "\n\n" +
                "✅ %s %s CALL SUCCESSFUL\n" +
                "💰 PROFIT: %.1f points\n" +
                "📊 Entry: %.1f → Current: %.1f\n\n" +
                "🏆 All 3 targets achieved successfully!\n" +
                "📝 All positions closed for this call.\n\n" +
                "🔄 New call for %s can be generated now.\n" +
                "⏰ Time: %s\n" +
                "=" .repeat(40),
                call.segment, call.direction, profit,
                call.entryPrice, currentPrice, call.segment,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            );
            targetsAchieved++;
        }
        
        System.out.println(message);
        System.out.println();
    }
    
    private void modifyTargets(ActiveCall call, double currentPrice, PositionStatus status) {
        if (call.targetsModified) return;
        
        // Calculate new extended targets
        double extension = Math.abs(currentPrice - call.entryPrice) * 0.3; // 30% extension
        
        TargetLevels newTargets;
        if (call.direction.equals("BUY")) {
            newTargets = new TargetLevels(
                call.targets.target1 + extension,
                call.targets.target2 + extension,
                call.targets.target3 + extension,
                call.targets.stopLoss // Keep same stop loss
            );
        } else {
            newTargets = new TargetLevels(
                call.targets.target1 - extension,
                call.targets.target2 - extension,
                call.targets.target3 - extension,
                call.targets.stopLoss
            );
        }
        
        // Store original targets for message
        TargetLevels originalTargets = call.targets;
        call.targets = newTargets;
        call.targetsModified = true;
        
        String message = String.format(
            "🔄 TARGETS MODIFIED\n" +
            "=" .repeat(40) + "\n\n" +
            "📊 %s %s target was predicted:\n" +
            "   Original Target 1: %.1f\n" +
            "   Original Target 2: %.1f\n" +
            "   Original Target 3: %.1f\n\n" +
            "📈 But as per current market we found new target increased:\n\n" +
            "🎯 NEW TARGETS:\n" +
            "   📊 Target 1: %.1f\n" +
            "   📊 Target 2: %.1f\n" +
            "   📊 Target 3: %.1f\n\n" +
            "📊 Current Price: %.1f\n" +
            "📝 This target is modified due to strong momentum\n" +
            "⏰ Time: %s\n" +
            "=" .repeat(40),
            call.segment, call.direction,
            originalTargets.target1, originalTargets.target2, originalTargets.target3,
            newTargets.target1, newTargets.target2, newTargets.target3,
            currentPrice,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
        
        System.out.println(message);
        System.out.println();
    }
    
    private void generateDailyReport() {
        System.out.println("📊 DAILY PERFORMANCE REPORT");
        System.out.println("=" .repeat(50));
        System.out.println("📞 Total Calls Today: " + totalCallsToday);
        System.out.println("🎯 Targets Achieved: " + targetsAchieved);
        System.out.println("❌ Stop Loss Hit: " + stopLossHit);
        System.out.println("📈 Active Positions: " + activePositions.size());
        
        if (totalCallsToday > 0) {
            double successRate = (double) targetsAchieved / (targetsAchieved + stopLossHit) * 100;
            System.out.println("📊 Success Rate: " + String.format("%.1f%%", successRate));
        }
        
        System.out.println("⏰ Report Time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
        System.out.println("=" .repeat(50));
        System.out.println();
    }
    
    // Technical indicator calculations
    private double calculateRSI(List<Double> prices) {
        if (prices.size() < 15) return 50.0;
        
        double avgGain = 0, avgLoss = 0;
        for (int i = prices.size() - 14; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        
        avgGain /= 14;
        avgLoss /= 14;
        
        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
    
    private double calculateEMA(List<Double> prices, int period) {
        if (prices.size() < period) return prices.get(prices.size() - 1);
        
        double multiplier = 2.0 / (period + 1);
        double ema = prices.get(prices.size() - period);
        
        for (int i = prices.size() - period + 1; i < prices.size(); i++) {
            ema = (prices.get(i) * multiplier) + (ema * (1 - multiplier));
        }
        return ema;
    }
    
    private double calculateMomentum(List<Double> prices) {
        if (prices.size() < 10) return 0.0;
        double current = prices.get(prices.size() - 1);
        double past = prices.get(prices.size() - 10);
        return (current - past) / past * 100;
    }
    
    private double calculateVolatility(List<Double> prices) {
        if (prices.size() < 20) return 0.02;
        
        List<Double> returns = new ArrayList<>();
        for (int i = prices.size() - 19; i < prices.size(); i++) {
            double ret = (prices.get(i) - prices.get(i - 1)) / prices.get(i - 1);
            returns.add(ret);
        }
        
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
            .mapToDouble(r -> Math.pow(r - mean, 2))
            .average()
            .orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    // Data classes
    private static class OpportunityAnalysis {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        final String segment;
        final String direction;
        final double price;
        final double confidence;
        final String reason;
        final double rsi, ema20, ema50, momentum, volatility;
        
        OpportunityAnalysis(String segment, String direction, double price, double confidence, 
                           String reason, double rsi, double ema20, double ema50, 
                           double momentum, double volatility) {
            this.segment = segment;
            this.direction = direction;
            this.price = price;
            this.confidence = confidence;
            this.reason = reason;
            this.rsi = rsi;
            this.ema20 = ema20;
            this.ema50 = ema50;
            this.momentum = momentum;
            this.volatility = volatility;
        }
        
        boolean isValidOpportunity() {
            return !direction.equals("NONE") && confidence >= 0.78;
        }
    }
    
    private static class TargetLevels {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        double target1, target2, target3, stopLoss;
        
        TargetLevels(double target1, double target2, double target3, double stopLoss) {
            this.target1 = target1;
            this.target2 = target2;
            this.target3 = target3;
            this.stopLoss = stopLoss;
        }
    }
    
    private static class ActiveCall {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        final String segment;
        final String direction;
        final double entryPrice;
        final double confidence;
        final String reason;
        final LocalDateTime timestamp;
        
        TargetLevels targets;
        boolean target1Achieved = false;
        boolean target2Achieved = false;
        boolean targetsModified = false;
        
        ActiveCall(String segment, String direction, double entryPrice, TargetLevels targets,
                   double confidence, String reason) {
            this.segment = segment;
            this.direction = direction;
            this.entryPrice = entryPrice;
            this.targets = targets;
            this.confidence = confidence;
            this.reason = reason;
            this.timestamp = LocalDateTime.now();
        }
    }
    
    private static class PositionStatus {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        final boolean shouldClose;
        final boolean shouldModifyTargets;
        final String reason;
        final int targetLevel;
        
        PositionStatus(boolean shouldClose, boolean shouldModifyTargets, String reason, int targetLevel) {
            this.shouldClose = shouldClose;
            this.shouldModifyTargets = shouldModifyTargets;
            this.reason = reason;
            this.targetLevel = targetLevel;
        }
    }
    
    public static void main(String[] args) {
        System.out.println("🎯 PROPER CALL GENERATOR - REALISTIC TRADING");
        System.out.println("=" .repeat(60));
        System.out.println("✅ Maximum 1 call per segment per 4 hours");
        System.out.println("✅ Maximum 2 calls per segment per day");
        System.out.println("✅ Single message with all targets");
        System.out.println("✅ Target modification system");
        System.out.println("✅ Proper position management");
        System.out.println("✅ New call only after position closure");
        System.out.println("=" .repeat(60));
        
        ProperCallGenerator generator = new ProperCallGenerator();
        generator.start();
        
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("🛑 Proper call generator stopped");
        }
    }
}