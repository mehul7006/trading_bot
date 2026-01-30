import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * SMART NOTIFICATION BOT - Proper Message Management
 * - Single message per segment (no spam)
 * - Target tracking and updates
 * - Position management
 * - Achievement notifications
 */
public class SmartNotificationBot {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();    
    // Active positions tracking
    private final Map<String, ActivePosition> activePositions = new HashMap<>();
    
    // Prevent duplicate calls for same segment
    private final Map<String, LocalDateTime> lastCallTime = new HashMap<>();
    private final int MIN_HOURS_BETWEEN_CALLS = 2; // Minimum 2 hours between calls for same segment
    
    // Market data tracking
    private final Map<String, Double> currentPrices = new HashMap<>();
    private final Map<String, List<Double>> priceHistory = new HashMap<>();
    
    public SmartNotificationBot() {
        initializeMarketData();
        System.out.println("📱 SMART NOTIFICATION BOT INITIALIZED");
        System.out.println("✅ Single message per segment");
        System.out.println("✅ Target tracking and updates");
        System.out.println("✅ Position management");
        System.out.println("✅ Achievement notifications");
    }
    
    private void initializeMarketData() {
        String[] segments = {"BANKNIFTY", "NIFTY", "FINNIFTY", "SENSEX"};
        
        for (String segment : segments) {
            double basePrice = getBasePrice(segment);
            currentPrices.put(segment, basePrice);
            
            List<Double> history = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                history.add(basePrice + (Math.random() - 0.5) * basePrice * 0.02);
            }
            priceHistory.put(segment, history);
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
        System.out.println("🚀 STARTING SMART NOTIFICATION BOT...");
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        
        // Market scanning every 2 minutes
        scheduler.scheduleAtFixedRate(this::scanMarket, 0, 2, TimeUnit.MINUTES);
        
        // Position monitoring every 30 seconds
        scheduler.scheduleAtFixedRate(this::monitorPositions, 30, 30, TimeUnit.SECONDS);
        
        System.out.println("✅ SMART BOT IS LIVE!");
    }
    
    private void scanMarket() {
        updateMarketPrices();
        
        for (String segment : Arrays.asList("BANKNIFTY", "NIFTY", "FINNIFTY", "SENSEX")) {
            // Skip if we already have active position
            if (activePositions.containsKey(segment)) {
                continue;
            }
            
            // Skip if recent call was made
            if (hasRecentCall(segment)) {
                continue;
            }
            
            // Analyze for new opportunity
            MarketSignal signal = analyzeSegment(segment);
            if (signal.isValid()) {
                generateSmartCall(segment, signal);
            }
        }
    }
    
    private void updateMarketPrices() {
        for (String segment : currentPrices.keySet()) {
            double currentPrice = currentPrices.get(segment);
            
            // Realistic price movement (0.1% max per 2 minutes)
            double change = (Math.random() - 0.5) * currentPrice * 0.002;
            double newPrice = currentPrice + change;
            
            currentPrices.put(segment, newPrice);
            
            // Update history
            List<Double> history = priceHistory.get(segment);
            history.add(newPrice);
            if (history.size() > 100) {
                history.remove(0);
            }
        }
    }
    
    private boolean hasRecentCall(String segment) {
        LocalDateTime lastCall = lastCallTime.get(segment);
        if (lastCall == null) return false;
        
        long hoursSinceLastCall = java.time.temporal.ChronoUnit.HOURS.between(lastCall, LocalDateTime.now());
        return hoursSinceLastCall < MIN_HOURS_BETWEEN_CALLS;
    }
    
    private MarketSignal analyzeSegment(String segment) {
        List<Double> prices = priceHistory.get(segment);
        double currentPrice = currentPrices.get(segment);
        
        // Calculate technical indicators
        double rsi = calculateRSI(prices);
        double ema20 = calculateEMA(prices, 20);
        double ema50 = calculateEMA(prices, 50);
        double momentum = calculateMomentum(prices);
        
        // Determine signal
        String direction = "NONE";
        double confidence = 0.5;
        
        if (ema20 > ema50 && currentPrice > ema20 && rsi > 40 && rsi < 70 && momentum > 0.5) {
            direction = "BUY";
            confidence = 0.75 + (Math.min(momentum, 2.0) / 2.0) * 0.1;
        } else if (ema20 < ema50 && currentPrice < ema20 && rsi < 60 && rsi > 30 && momentum < -0.5) {
            direction = "SELL";
            confidence = 0.75 + (Math.min(Math.abs(momentum), 2.0) / 2.0) * 0.1;
        }
        
        return new MarketSignal(segment, direction, currentPrice, confidence, 
                               String.format("RSI: %.1f, EMA20: %.1f, Momentum: %.2f", rsi, ema20, momentum));
    }
    
    private void generateSmartCall(String segment, MarketSignal signal) {
        // Calculate targets based on segment
        TargetLevels targets = calculateTargets(segment, signal.price, signal.direction);
        
        // Create active position
        ActivePosition position = new ActivePosition(
            segment, signal.direction, signal.price, targets, signal.confidence, signal.reason
        );
        
        activePositions.put(segment, position);
        lastCallTime.put(segment, LocalDateTime.now());
        
        // Send smart notification
        sendSmartNotification(position);
    }
    
    private TargetLevels calculateTargets(String segment, double entryPrice, String direction) {
        double target1, target2, target3, stopLoss;
        
        switch (segment) {
            case "BANKNIFTY":
                if (direction.equals("BUY")) {
                    target1 = entryPrice + 100;  // 100 points
                    target2 = entryPrice + 200;  // 200 points
                    target3 = entryPrice + 350;  // 350 points
                    stopLoss = entryPrice - 80;  // 80 points stop loss
                } else {
                    target1 = entryPrice - 100;
                    target2 = entryPrice - 200;
                    target3 = entryPrice - 350;
                    stopLoss = entryPrice + 80;
                }
                break;
                
            case "NIFTY":
                if (direction.equals("BUY")) {
                    target1 = entryPrice + 40;   // 40 points
                    target2 = entryPrice + 80;   // 80 points
                    target3 = entryPrice + 130;  // 130 points
                    stopLoss = entryPrice - 30;  // 30 points stop loss
                } else {
                    target1 = entryPrice - 40;
                    target2 = entryPrice - 80;
                    target3 = entryPrice - 130;
                    stopLoss = entryPrice + 30;
                }
                break;
                
            case "FINNIFTY":
                if (direction.equals("BUY")) {
                    target1 = entryPrice + 50;   // 50 points
                    target2 = entryPrice + 100;  // 100 points
                    target3 = entryPrice + 170;  // 170 points
                    stopLoss = entryPrice - 40;  // 40 points stop loss
                } else {
                    target1 = entryPrice - 50;
                    target2 = entryPrice - 100;
                    target3 = entryPrice - 170;
                    stopLoss = entryPrice + 40;
                }
                break;
                
            case "SENSEX":
                if (direction.equals("BUY")) {
                    target1 = entryPrice + 150;  // 150 points
                    target2 = entryPrice + 300;  // 300 points
                    target3 = entryPrice + 500;  // 500 points
                    stopLoss = entryPrice - 120; // 120 points stop loss
                } else {
                    target1 = entryPrice - 150;
                    target2 = entryPrice - 300;
                    target3 = entryPrice - 500;
                    stopLoss = entryPrice + 120;
                }
                break;
                
            default:
                target1 = target2 = target3 = stopLoss = entryPrice;
        }
        
        return new TargetLevels(target1, target2, target3, stopLoss);
    }
    
    private void sendSmartNotification(ActivePosition position) {
        String message = String.format(
            "📞 SMART CALL GENERATED\n\n" +
            "🎯 %s %s at %.1f\n\n" +
            "📈 TARGETS:\n" +
            "   Target 1: %.1f\n" +
            "   Target 2: %.1f\n" +
            "   Target 3: %.1f\n\n" +
            "🛡️ Stop Loss: %.1f\n\n" +
            "🔥 Confidence: %.1f%%\n" +
            "📊 Analysis: %s\n" +
            "⏰ Time: %s",
            position.segment, position.direction, position.entryPrice,
            position.targets.target1, position.targets.target2, position.targets.target3,
            position.targets.stopLoss,
            position.confidence * 100,
            position.reason,
            position.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
        
        System.out.println(message);
        System.out.println("=" .repeat(50));
    }
    
    private void monitorPositions() {
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, ActivePosition> entry : activePositions.entrySet()) {
            String segment = entry.getKey();
            ActivePosition position = entry.getValue();
            double currentPrice = currentPrices.get(segment);
            
            // Check for target achievements or stop loss
            String result = checkPositionStatus(position, currentPrice);
            
            if (result != null) {
                sendPositionUpdate(position, currentPrice, result);
                toRemove.add(segment);
            } else {
                // Check for target updates
                checkForTargetUpdates(position, currentPrice);
            }
        }
        
        // Remove closed positions
        toRemove.forEach(activePositions::remove);
    }
    
    private String checkPositionStatus(ActivePosition position, double currentPrice) {
        if (position.direction.equals("BUY")) {
            if (currentPrice <= position.targets.stopLoss) {
                return "STOP_LOSS";
            } else if (currentPrice >= position.targets.target3) {
                return "TARGET_3_ACHIEVED";
            } else if (currentPrice >= position.targets.target2) {
                return "TARGET_2_ACHIEVED";
            } else if (currentPrice >= position.targets.target1) {
                return "TARGET_1_ACHIEVED";
            }
        } else { // SELL
            if (currentPrice >= position.targets.stopLoss) {
                return "STOP_LOSS";
            } else if (currentPrice <= position.targets.target3) {
                return "TARGET_3_ACHIEVED";
            } else if (currentPrice <= position.targets.target2) {
                return "TARGET_2_ACHIEVED";
            } else if (currentPrice <= position.targets.target1) {
                return "TARGET_1_ACHIEVED";
            }
        }
        return null;
    }
    
    private void sendPositionUpdate(ActivePosition position, double currentPrice, String result) {
        String message;
        
        if (result.equals("STOP_LOSS")) {
            double loss = Math.abs(currentPrice - position.entryPrice);
            message = String.format(
                "🚨 STOP LOSS HIT\n\n" +
                "📉 %s %s call went wrong\n" +
                "💸 Loss: %.1f points\n" +
                "📊 Entry: %.1f → Current: %.1f\n" +
                "⚠️ Set your stop loss - this call goes wrong!\n" +
                "⏰ Time: %s",
                position.segment, position.direction, loss,
                position.entryPrice, currentPrice,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            );
        } else {
            double profit = Math.abs(currentPrice - position.entryPrice);
            String targetLevel = result.replace("_ACHIEVED", "").replace("_", " ");
            
            message = String.format(
                "🎉 %s ACHIEVED!\n\n" +
                "✅ %s %s call successful\n" +
                "💰 Profit: %.1f points\n" +
                "📊 Entry: %.1f → Current: %.1f\n" +
                "🏆 %s target was predicted and achieved!\n" +
                "📝 All positions closed for this call\n" +
                "⏰ Time: %s",
                targetLevel, position.segment, position.direction, profit,
                position.entryPrice, currentPrice, targetLevel,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            );
        }
        
        System.out.println(message);
        System.out.println("=" .repeat(50));
    }
    
    private void checkForTargetUpdates(ActivePosition position, double currentPrice) {
        // Check if we need to update targets based on strong market movement
        double movementPercent = Math.abs(currentPrice - position.entryPrice) / position.entryPrice * 100;
        
        if (movementPercent > 1.5 && !position.targetsUpdated) { // 1.5% movement
            // Update targets for stronger movement
            TargetLevels newTargets = calculateExtendedTargets(position, currentPrice);
            
            if (newTargets != null) {
                position.targets = newTargets;
                position.targetsUpdated = true;
                
                String message = String.format(
                    "🔄 TARGET UPDATE\n\n" +
                    "📊 %s %s target was predicted %.1f\n" +
                    "📈 But as per current market we found new target increased\n\n" +
                    "🎯 NEW TARGETS:\n" +
                    "   Target 1: %.1f\n" +
                    "   Target 2: %.1f\n" +
                    "   Target 3: %.1f\n\n" +
                    "📊 Current Price: %.1f\n" +
                    "⏰ Time: %s",
                    position.segment, position.direction, position.originalTarget1,
                    newTargets.target1, newTargets.target2, newTargets.target3,
                    currentPrice,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                );
                
                System.out.println(message);
                System.out.println("=" .repeat(50));
            }
        }
    }
    
    private TargetLevels calculateExtendedTargets(ActivePosition position, double currentPrice) {
        // Calculate extended targets based on strong momentum
        double extension = Math.abs(currentPrice - position.entryPrice) * 0.5; // 50% extension
        
        if (position.direction.equals("BUY")) {
            return new TargetLevels(
                position.targets.target1 + extension,
                position.targets.target2 + extension,
                position.targets.target3 + extension,
                position.targets.stopLoss
            );
        } else {
            return new TargetLevels(
                position.targets.target1 - extension,
                position.targets.target2 - extension,
                position.targets.target3 - extension,
                position.targets.stopLoss
            );
        }
    }
    
    // Technical indicator calculations (simplified)
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
    
    // Data classes
    private static class MarketSignal {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        final String segment;
        final String direction;
        final double price;
        final double confidence;
        final String reason;
        
        MarketSignal(String segment, String direction, double price, double confidence, String reason) {
            this.segment = segment;
            this.direction = direction;
            this.price = price;
            this.confidence = confidence;
            this.reason = reason;
        }
        
        boolean isValid() {
            return !direction.equals("NONE") && confidence >= 0.75;
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
    
    private static class ActivePosition {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        final String segment;
        final String direction;
        final double entryPrice;
        final double originalTarget1;
        final double confidence;
        final String reason;
        final LocalDateTime timestamp;
        
        TargetLevels targets;
        boolean targetsUpdated = false;
        
        ActivePosition(String segment, String direction, double entryPrice, TargetLevels targets, 
                      double confidence, String reason) {
            this.segment = segment;
            this.direction = direction;
            this.entryPrice = entryPrice;
            this.originalTarget1 = targets.target1;
            this.targets = targets;
            this.confidence = confidence;
            this.reason = reason;
            this.timestamp = LocalDateTime.now();
        }
    }
    
    public static void main(String[] args) {
        System.out.println("📱 SMART NOTIFICATION BOT - PROPER MESSAGE MANAGEMENT");
        System.out.println("=" .repeat(60));
        System.out.println("✅ Single message per segment (no spam)");
        System.out.println("✅ Target tracking and updates");
        System.out.println("✅ Position management");
        System.out.println("✅ Achievement notifications");
        System.out.println("✅ Stop loss alerts");
        System.out.println("✅ Target modification based on market movement");
        System.out.println("=" .repeat(60));
        
        SmartNotificationBot bot = new SmartNotificationBot();
        bot.start();
        
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("🛑 Smart notification bot stopped");
        }
    }
}