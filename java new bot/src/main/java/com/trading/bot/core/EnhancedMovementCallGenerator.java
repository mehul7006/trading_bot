import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * ENHANCED MOVEMENT-BASED CALL GENERATOR
 * - Only generates calls when 50-70+ point movement potential detected
 * - Clear entry point, exit targets, and stop loss
 * - Maximum 1 call per 4 hours with high movement potential
 * - Proper options analysis with strike selection
 */
public class EnhancedMovementCallGenerator {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();    
    // Movement thresholds for different segments
    private static final Map<String, Integer> MIN_MOVEMENT_POINTS = Map.of(
        "BANKNIFTY", 70,  // Minimum 70 points movement potential
        "NIFTY", 50,      // Minimum 50 points movement potential  
        "FINNIFTY", 60,   // Minimum 60 points movement potential
        "SENSEX", 100     // Minimum 100 points movement potential
    );
    
    // Call frequency limits
    private static final int MIN_HOURS_BETWEEN_CALLS = 4;
    private static final int MAX_CALLS_PER_DAY = 2;
    
    // Active positions tracking
    private final Map<String, ActiveOptionsCall> activePositions = new HashMap<>();
    private final Map<String, List<LocalDateTime>> callHistory = new HashMap<>();
    
    // Market data
    private final Map<String, Double> currentPrices = new HashMap<>();
    private final Map<String, List<Double>> priceHistory = new HashMap<>();
    
    public EnhancedMovementCallGenerator() {
        initializeMarketData();
        initializeCallHistory();
        
        System.out.println("🎯 ENHANCED MOVEMENT-BASED CALL GENERATOR INITIALIZED");
        System.out.println("✅ Minimum movement requirements:");
        MIN_MOVEMENT_POINTS.forEach((segment, points) -> 
            System.out.println("   📊 " + segment + ": " + points + "+ points"));
        System.out.println("✅ Clear entry/exit/stop loss points");
        System.out.println("✅ Maximum 1 call per 4 hours");
        System.out.println("✅ Options strike selection based on movement");
    }
    
    private void initializeMarketData() {
        String[] segments = {"BANKNIFTY", "NIFTY", "FINNIFTY", "SENSEX"};
        
        for (String segment : segments) {
            double basePrice = getBasePrice(segment);
            currentPrices.put(segment, basePrice);
            
            List<Double> history = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
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
        System.out.println("🚀 STARTING ENHANCED MOVEMENT CALL GENERATOR...");
        System.out.println("📊 Scanning for high movement potential opportunities");
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        
        // Scan every 10 minutes for high movement opportunities
        scheduler.scheduleAtFixedRate(this::scanForHighMovementOpportunities, 0, 10, TimeUnit.MINUTES);
        
        // Monitor active positions every 2 minutes
        scheduler.scheduleAtFixedRate(this::monitorActivePositions, 1, 2, TimeUnit.MINUTES);
        
        System.out.println("✅ ENHANCED MOVEMENT GENERATOR IS LIVE!");
    }
    
    private void scanForHighMovementOpportunities() {
        updateMarketPrices();
        
        System.out.println("🔍 Scanning for HIGH MOVEMENT opportunities...");
        
        for (String segment : Arrays.asList("BANKNIFTY", "NIFTY", "FINNIFTY", "SENSEX")) {
            
            // Skip if active position exists
            if (activePositions.containsKey(segment)) {
                System.out.println("📊 " + segment + ": Active position exists - skipping");
                continue;
            }
            
            // Check timing constraints
            if (!canGenerateNewCall(segment)) {
                continue;
            }
            
            // Check daily limit
            if (hasReachedDailyLimit(segment)) {
                System.out.println("📊 " + segment + ": Daily limit reached");
                continue;
            }
            
            // Analyze for high movement potential
            MovementOpportunity opportunity = analyzeHighMovementPotential(segment);
            
            if (opportunity != null && opportunity.isValidHighMovement()) {
                generateHighMovementCall(segment, opportunity);
            }
        }
    }
    
    private boolean canGenerateNewCall(String segment) {
        List<LocalDateTime> history = callHistory.get(segment);
        if (history.isEmpty()) return true;
        
        LocalDateTime lastCall = history.get(history.size() - 1);
        long hoursSinceLastCall = java.time.temporal.ChronoUnit.HOURS.between(lastCall, LocalDateTime.now());
        
        if (hoursSinceLastCall < MIN_HOURS_BETWEEN_CALLS) {
            System.out.println("⏰ " + segment + ": Only " + hoursSinceLastCall + " hours since last call");
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
            
            // Realistic price movement
            double maxChange = currentPrice * 0.0008; // 0.08% max per 10 minutes
            double change = (Math.random() - 0.5) * 2 * maxChange;
            
            // Add session bias
            int hour = LocalDateTime.now().getHour();
            if (hour >= 9 && hour <= 11) {
                change += maxChange * 0.4; // Morning bias
            } else if (hour >= 14 && hour <= 15) {
                change -= maxChange * 0.4; // Afternoon bias
            }
            
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
    
    private MovementOpportunity analyzeHighMovementPotential(String segment) {
        List<Double> prices = priceHistory.get(segment);
        double currentPrice = currentPrices.get(segment);
        int minMovement = MIN_MOVEMENT_POINTS.get(segment);
        
        // Calculate technical indicators
        double rsi = calculateRSI(prices);
        double ema20 = calculateEMA(prices, 20);
        double ema50 = calculateEMA(prices, 50);
        double momentum = calculateMomentum(prices);
        double volatility = calculateVolatility(prices);
        
        // Calculate support and resistance levels
        SupportResistanceLevels levels = calculateSupportResistance(segment, currentPrice, prices);
        
        // Analyze CE (Call) opportunity - bullish movement potential
        MovementOpportunity ceOpportunity = analyzeCEMovementPotential(
            segment, currentPrice, levels, rsi, ema20, ema50, momentum, volatility, minMovement);
        
        // Analyze PE (Put) opportunity - bearish movement potential  
        MovementOpportunity peOpportunity = analyzePEMovementPotential(
            segment, currentPrice, levels, rsi, ema20, ema50, momentum, volatility, minMovement);
        
        // Return the opportunity with higher movement potential
        if (ceOpportunity != null && peOpportunity != null) {
            return ceOpportunity.movementPotential > peOpportunity.movementPotential ? 
                   ceOpportunity : peOpportunity;
        } else if (ceOpportunity != null) {
            return ceOpportunity;
        } else {
            return peOpportunity;
        }
    }
    
    private MovementOpportunity analyzeCEMovementPotential(String segment, double currentPrice, 
            SupportResistanceLevels levels, double rsi, double ema20, double ema50, 
            double momentum, double volatility, int minMovement) {
        
        // CE opportunity: Strong bullish setup with high upward movement potential
        double resistanceTarget = levels.resistance1;
        double movementPotential = resistanceTarget - currentPrice;
        
        // Check if movement potential meets minimum requirement
        if (movementPotential < minMovement) {
            return null;
        }
        
        // Strong bullish criteria for high movement
        if (ema20 > ema50 && currentPrice > ema20 && 
            currentPrice <= levels.support1 + (levels.resistance1 - levels.support1) * 0.3 && // Near support
            rsi > 45 && rsi < 60 && momentum > 2.0 && volatility > 0.02) {
            
            double confidence = 0.82 + Math.min(momentum / 8.0, 0.08); // 82-90% max
            
            // Calculate option strike (slightly OTM for better premium)
            double strikePrice = Math.ceil(currentPrice / 50) * 50; // Round to nearest 50
            
            // Calculate entry, exit, stop loss for options
            OptionsLevels optionsLevels = calculateOptionsLevels(segment, "CE", strikePrice, 
                currentPrice, resistanceTarget, levels.support1);
            
            String reason = String.format(
                "STRONG BULLISH SETUP: Price %.1f near support %.1f, target resistance %.1f " +
                "(%.0f points movement potential). RSI:%.1f, Momentum:%.2f%%, EMA alignment confirmed",
                currentPrice, levels.support1, resistanceTarget, movementPotential, rsi, momentum);
            
            return new MovementOpportunity(segment, "CE", strikePrice, currentPrice, 
                confidence, reason, movementPotential, optionsLevels);
        }
        
        return null;
    }
    
    private MovementOpportunity analyzePEMovementPotential(String segment, double currentPrice,
            SupportResistanceLevels levels, double rsi, double ema20, double ema50,
            double momentum, double volatility, int minMovement) {
        
        // PE opportunity: Strong bearish setup with high downward movement potential
        double supportTarget = levels.support1;
        double movementPotential = currentPrice - supportTarget;
        
        // Check if movement potential meets minimum requirement
        if (movementPotential < minMovement) {
            return null;
        }
        
        // Strong bearish criteria for high movement
        if (ema20 < ema50 && currentPrice < ema20 &&
            currentPrice >= levels.support1 + (levels.resistance1 - levels.support1) * 0.7 && // Near resistance
            rsi < 55 && rsi > 40 && momentum < -2.0 && volatility > 0.02) {
            
            double confidence = 0.82 + Math.min(Math.abs(momentum) / 8.0, 0.08); // 82-90% max
            
            // Calculate option strike (slightly OTM for better premium)
            double strikePrice = Math.floor(currentPrice / 50) * 50; // Round to nearest 50
            
            // Calculate entry, exit, stop loss for options
            OptionsLevels optionsLevels = calculateOptionsLevels(segment, "PE", strikePrice,
                currentPrice, supportTarget, levels.resistance1);
            
            String reason = String.format(
                "STRONG BEARISH SETUP: Price %.1f near resistance %.1f, target support %.1f " +
                "(%.0f points movement potential). RSI:%.1f, Momentum:%.2f%%, EMA alignment confirmed",
                currentPrice, levels.resistance1, supportTarget, movementPotential, rsi, momentum);
            
            return new MovementOpportunity(segment, "PE", strikePrice, currentPrice,
                confidence, reason, movementPotential, optionsLevels);
        }
        
        return null;
    }
    
    private SupportResistanceLevels calculateSupportResistance(String segment, double currentPrice, List<Double> prices) {
        // Calculate dynamic support and resistance based on recent price action
        double high = prices.stream().skip(prices.size() - 20).mapToDouble(Double::doubleValue).max().orElse(currentPrice);
        double low = prices.stream().skip(prices.size() - 20).mapToDouble(Double::doubleValue).min().orElse(currentPrice);
        
        double support1 = low + (high - low) * 0.2;  // 20% from low
        double resistance1 = high - (high - low) * 0.2; // 20% from high
        
        return new SupportResistanceLevels(support1, resistance1);
    }
    
    private OptionsLevels calculateOptionsLevels(String segment, String type, double strikePrice,
            double spotPrice, double target, double stopLevel) {
        
        // Calculate options premium estimates (simplified)
        double spotToStrike = Math.abs(spotPrice - strikePrice);
        double timeValue = spotPrice * 0.02; // 2% time value estimate
        double intrinsicValue = type.equals("CE") ? 
            Math.max(0, spotPrice - strikePrice) : Math.max(0, strikePrice - spotPrice);
        
        double entryPremium = intrinsicValue + timeValue + (spotToStrike * 0.01);
        
        // Calculate target premium based on movement to target
        double targetSpotPrice = target;
        double targetIntrinsic = type.equals("CE") ?
            Math.max(0, targetSpotPrice - strikePrice) : Math.max(0, strikePrice - targetSpotPrice);
        double targetPremium = targetIntrinsic + timeValue * 0.7; // Reduced time value
        
        // Calculate stop loss premium (50% of entry premium)
        double stopLossPremium = entryPremium * 0.5;
        
        return new OptionsLevels(entryPremium, targetPremium, stopLossPremium, target, stopLevel);
    }
    
    private void generateHighMovementCall(String segment, MovementOpportunity opportunity) {
        // Create active call
        ActiveOptionsCall call = new ActiveOptionsCall(
            segment, opportunity.type, opportunity.strikePrice, opportunity.spotPrice,
            opportunity.optionsLevels, opportunity.confidence, opportunity.reason,
            opportunity.movementPotential
        );
        
        // Store active position
        activePositions.put(segment, call);
        
        // Record call in history
        callHistory.get(segment).add(LocalDateTime.now());
        
        // Send high movement call notification
        sendHighMovementCallNotification(call);
        
        System.out.println("📞 HIGH MOVEMENT CALL GENERATED: " + segment + " " + 
            opportunity.type + " " + opportunity.strikePrice + " (+" + 
            (int)opportunity.movementPotential + " points potential)");
    }
    
    private void sendHighMovementCallNotification(ActiveOptionsCall call) {
        String message = String.format(
            "🚀 HIGH MOVEMENT CALL GENERATED\n" +
            "=" .repeat(50) + "\n\n" +
            "🎯 SEGMENT: %s\n" +
            "📊 OPTION: %s %.0f %s\n" +
            "📈 SPOT PRICE: %.1f\n\n" +
            "💰 ENTRY POINTS:\n" +
            "   🔹 Entry Premium: %.1f\n" +
            "   🔹 Spot Entry: %.1f\n\n" +
            "🎯 EXIT POINTS:\n" +
            "   🎯 Target Premium: %.1f\n" +
            "   🎯 Target Spot: %.1f\n" +
            "   📊 Movement Required: +%.0f points\n\n" +
            "🛡️ STOP LOSS POINTS:\n" +
            "   ❌ Stop Premium: %.1f\n" +
            "   ❌ Stop Spot: %.1f\n\n" +
            "🔥 CONFIDENCE: %.1f%%\n" +
            "📋 ANALYSIS: %s\n\n" +
            "⚡ MOVEMENT POTENTIAL: %.0f POINTS\n" +
            "⏰ TIME: %s\n" +
            "📝 NOTE: High movement potential detected - Trade with proper position sizing\n" +
            "=" .repeat(50),
            call.segment, call.segment, call.strikePrice, call.type, call.spotPrice,
            call.optionsLevels.entryPremium, call.spotPrice,
            call.optionsLevels.targetPremium, call.optionsLevels.targetSpot, call.movementPotential,
            call.optionsLevels.stopLossPremium, call.optionsLevels.stopSpot,
            call.confidence * 100,
            call.reason,
            call.movementPotential,
            call.timestamp.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
        );
        
        System.out.println(message);
        System.out.println();
    }
    
    private void monitorActivePositions() {
        if (activePositions.isEmpty()) {
            return;
        }
        
        System.out.println("👁️ Monitoring " + activePositions.size() + " high movement positions...");
        
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, ActiveOptionsCall> entry : activePositions.entrySet()) {
            String segment = entry.getKey();
            ActiveOptionsCall call = entry.getValue();
            double currentPrice = currentPrices.get(segment);
            
            // Check position status
            PositionStatus status = checkHighMovementPositionStatus(call, currentPrice);
            
            if (status.shouldClose) {
                handleHighMovementPositionClosure(call, currentPrice, status);
                toRemove.add(segment);
            }
        }
        
        // Remove closed positions
        toRemove.forEach(activePositions::remove);
    }
    
    private PositionStatus checkHighMovementPositionStatus(ActiveOptionsCall call, double currentPrice) {
        boolean shouldClose = false;
        String reason = "";
        
        if (call.type.equals("CE")) {
            if (currentPrice >= call.optionsLevels.targetSpot) {
                shouldClose = true;
                reason = "TARGET_ACHIEVED";
            } else if (currentPrice <= call.optionsLevels.stopSpot) {
                shouldClose = true;
                reason = "STOP_LOSS_HIT";
            }
        } else { // PE
            if (currentPrice <= call.optionsLevels.targetSpot) {
                shouldClose = true;
                reason = "TARGET_ACHIEVED";
            } else if (currentPrice >= call.optionsLevels.stopSpot) {
                shouldClose = true;
                reason = "STOP_LOSS_HIT";
            }
        }
        
        return new PositionStatus(shouldClose, reason);
    }
    
    private void handleHighMovementPositionClosure(ActiveOptionsCall call, double currentPrice, PositionStatus status) {
        String message;
        
        if (status.reason.equals("STOP_LOSS_HIT")) {
            message = String.format(
                "🚨 HIGH MOVEMENT CALL - STOP LOSS HIT\n" +
                "=" .repeat(50) + "\n\n" +
                "📉 %s %.0f %s CALL FAILED\n" +
                "📊 Spot: %.1f → %.1f\n" +
                "💸 Expected Premium Loss: %.1f → %.1f\n\n" +
                "⚠️ SET YOUR STOP LOSS - High movement didn't materialize!\n\n" +
                "📝 Position closed. Next high movement scan in 4 hours.\n" +
                "⏰ Time: %s\n" +
                "=" .repeat(50),
                call.segment, call.strikePrice, call.type,
                call.spotPrice, currentPrice,
                call.optionsLevels.entryPremium, call.optionsLevels.stopLossPremium,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            );
        } else {
            double actualMovement = Math.abs(currentPrice - call.spotPrice);
            message = String.format(
                "🎉 HIGH MOVEMENT TARGET ACHIEVED!\n" +
                "=" .repeat(50) + "\n\n" +
                "✅ %s %.0f %s CALL SUCCESSFUL\n" +
                "📊 Spot: %.1f → %.1f\n" +
                "💰 Expected Premium: %.1f → %.1f\n" +
                "📈 Actual Movement: %.0f points\n\n" +
                "🏆 High movement prediction was CORRECT!\n" +
                "📝 All positions closed for this call.\n\n" +
                "🔄 Next high movement opportunity scan active.\n" +
                "⏰ Time: %s\n" +
                "=" .repeat(50),
                call.segment, call.strikePrice, call.type,
                call.spotPrice, currentPrice,
                call.optionsLevels.entryPremium, call.optionsLevels.targetPremium,
                actualMovement,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            );
        }
        
        System.out.println(message);
        System.out.println();
    }
    
    // Technical indicator calculations (same as before)
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
    private static class SupportResistanceLevels {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        final double support1, resistance1;
        
        SupportResistanceLevels(double support1, double resistance1) {
            this.support1 = support1;
            this.resistance1 = resistance1;
        }
    }
    
    private static class OptionsLevels {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        final double entryPremium, targetPremium, stopLossPremium;
        final double targetSpot, stopSpot;
        
        OptionsLevels(double entryPremium, double targetPremium, double stopLossPremium,
                     double targetSpot, double stopSpot) {
            this.entryPremium = entryPremium;
            this.targetPremium = targetPremium;
            this.stopLossPremium = stopLossPremium;
            this.targetSpot = targetSpot;
            this.stopSpot = stopSpot;
        }
    }
    
    private static class MovementOpportunity {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        final String segment, type;
        final double strikePrice, spotPrice, confidence, movementPotential;
        final String reason;
        final OptionsLevels optionsLevels;
        
        MovementOpportunity(String segment, String type, double strikePrice, double spotPrice,
                           double confidence, String reason, double movementPotential,
                           OptionsLevels optionsLevels) {
            this.segment = segment;
            this.type = type;
            this.strikePrice = strikePrice;
            this.spotPrice = spotPrice;
            this.confidence = confidence;
            this.reason = reason;
            this.movementPotential = movementPotential;
            this.optionsLevels = optionsLevels;
        }
        
        boolean isValidHighMovement() {
            return confidence >= 0.82 && movementPotential >= MIN_MOVEMENT_POINTS.get(segment);
        }
    }
    
    private static class ActiveOptionsCall {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        final String segment, type;
        final double strikePrice, spotPrice, confidence, movementPotential;
        final String reason;
        final OptionsLevels optionsLevels;
        final LocalDateTime timestamp;
        
        ActiveOptionsCall(String segment, String type, double strikePrice, double spotPrice,
                         OptionsLevels optionsLevels, double confidence, String reason,
                         double movementPotential) {
            this.segment = segment;
            this.type = type;
            this.strikePrice = strikePrice;
            this.spotPrice = spotPrice;
            this.optionsLevels = optionsLevels;
            this.confidence = confidence;
            this.reason = reason;
            this.movementPotential = movementPotential;
            this.timestamp = LocalDateTime.now();
        }
    }
    
    private static class PositionStatus {
    private final RealMarketDataProvider realData = new RealMarketDataProvider();        final boolean shouldClose;
        final String reason;
        
        PositionStatus(boolean shouldClose, String reason) {
            this.shouldClose = shouldClose;
            this.reason = reason;
        }
    }
    
    public static void main(String[] args) {
        System.out.println("🚀 ENHANCED MOVEMENT-BASED CALL GENERATOR");
        System.out.println("=" .repeat(60));
        System.out.println("✅ Minimum movement requirements enforced");
        System.out.println("✅ Clear entry/exit/stop loss points");
        System.out.println("✅ Options premium calculations");
        System.out.println("✅ High confidence thresholds (82%+)");
        System.out.println("✅ Maximum 1 call per 4 hours");
        System.out.println("=" .repeat(60));
        
        EnhancedMovementCallGenerator generator = new EnhancedMovementCallGenerator();
        generator.start();
        
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            System.out.println("🛑 Enhanced movement call generator stopped");
        }
    }
}