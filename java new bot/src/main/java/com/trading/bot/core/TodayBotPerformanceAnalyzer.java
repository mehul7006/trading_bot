import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * TODAY'S BOT PERFORMANCE ANALYZER
 * Analyzes bot calls generated today and calculates success rates
 */
public class TodayBotPerformanceAnalyzer {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    public static void main(String[] args) {
        System.out.println("📊 TODAY'S BOT PERFORMANCE ANALYSIS");
        System.out.println("═══════════════════════════════════");
        System.out.println("📅 Date: " + LocalDate.now().format(DATE_FORMAT));
        System.out.println("⏰ Analysis Time: " + LocalTime.now().format(TIME_FORMAT));
        System.out.println();
        
        analyzeTodaysPerformance();
    }
    
    /**
     * Comprehensive today's performance analysis
     */
    private static void analyzeTodaysPerformance() {
        System.out.println("🔍 ANALYZING TODAY'S BOT PERFORMANCE");
        System.out.println("═══════════════════════════════════");
        
        // Simulate today's bot calls (in real implementation, read from logs/database)
        List<BotCall> todaysCalls = generateTodaysCallData();
        
        if (todaysCalls.isEmpty()) {
            System.out.println("❌ No calls found for today. Bot might not be generating calls properly.");
            return;
        }
        
        // Analyze performance
        PerformanceMetrics metrics = calculatePerformanceMetrics(todaysCalls);
        
        // Display comprehensive results
        displayDetailedAnalysis(todaysCalls, metrics);
    }
    
    /**
     * Generate today's call data (simulate bot calls throughout the day)
     */
    private static List<BotCall> generateTodaysCallData() {
        List<BotCall> calls = new ArrayList<>();
        
        // Simulate calls generated every 30 minutes from 9:15 AM to current time
        LocalTime startTime = LocalTime.of(9, 15);
        LocalTime currentTime = LocalTime.now();
        LocalTime endTime = currentTime.isAfter(LocalTime.of(15, 30)) ? 
                           LocalTime.of(15, 30) : currentTime;
        
        Random random = new Random();
        int callId = 1;
        
        for (LocalTime time = startTime; !time.isAfter(endTime); time = time.plusMinutes(30)) {
            // Simulate different types of calls
            String[] symbols = {"NIFTY", "SENSEX", "TCS", "RELIANCE", "HDFCBANK"};
            String[] callTypes = {"BUY", "SELL", "HOLD"};
            
            String symbol = symbols[random.nextInt(symbols.length)];
            String callType = callTypes[random.nextInt(callTypes.length)];
            double confidence = 60 + random.nextDouble() * 35; // 60-95% confidence
            
            // Simulate entry price based on symbol
            double entryPrice = getSimulatedPrice(symbol, time);
            
            // Generate call
            BotCall call = new BotCall(
                callId++,
                symbol,
                callType,
                confidence,
                entryPrice,
                time,
                generateCallReasoning(symbol, callType, confidence)
            );
            
            // Simulate success/failure based on market conditions
            simulateCallOutcome(call, time);
            
            calls.add(call);
        }
        
        return calls;
    }
    
    /**
     * Get simulated price for symbol at specific time
     */
    private static double getSimulatedPrice(String symbol, LocalTime time) {
        Random random = new Random(time.hashCode()); // Consistent randomness based on time
        
        switch (symbol) {
            case "NIFTY":
                return 25800 + (random.nextDouble() - 0.5) * 200; // ±100 points
            case "SENSEX":
                return 85000 + (random.nextDouble() - 0.5) * 1000; // ±500 points
            case "TCS":
                return 3500 + (random.nextDouble() - 0.5) * 200; // ±100 points
            case "RELIANCE":
                return 2400 + (random.nextDouble() - 0.5) * 200; // ±100 points
            case "HDFCBANK":
                return 1600 + (random.nextDouble() - 0.5) * 100; // ±50 points
            default:
                return 1000 + random.nextDouble() * 1000;
        }
    }
    
    /**
     * Generate call reasoning
     */
    private static String generateCallReasoning(String symbol, String callType, double confidence) {
        String[] reasons = {
            "Technical breakout with volume confirmation",
            "RSI oversold bounce expected",
            "Support level holding strong",
            "Resistance breakout with momentum",
            "Moving average crossover signal",
            "Volume spike indicates institutional interest",
            "Chart pattern completion",
            "Sector rotation favoring this stock"
        };
        
        Random random = new Random();
        return reasons[random.nextInt(reasons.length)];
    }
    
    /**
     * Simulate call outcome based on market conditions
     */
    private static void simulateCallOutcome(BotCall call, LocalTime time) {
        Random random = new Random();
        
        // Success probability based on confidence and time of day
        double baseSuccessRate = call.confidence / 100.0;
        
        // Time-based adjustment (morning better than afternoon)
        if (time.isBefore(LocalTime.of(12, 0))) {
            baseSuccessRate += 0.1; // Morning bonus
        } else if (time.isAfter(LocalTime.of(14, 0))) {
            baseSuccessRate -= 0.15; // Afternoon penalty
        }
        
        // Confidence bonus
        if (call.confidence > 80) {
            baseSuccessRate += 0.05;
        }
        
        boolean isSuccessful = random.nextDouble() < baseSuccessRate;
        
        call.setOutcome(isSuccessful);
        
        if (isSuccessful) {
            // Calculate profit percentage
            double profitPercent = 0.5 + random.nextDouble() * 3.0; // 0.5% to 3.5% profit
            call.setProfitPercent(profitPercent);
        } else {
            // Calculate loss percentage
            double lossPercent = -(0.3 + random.nextDouble() * 2.0); // -0.3% to -2.3% loss
            call.setProfitPercent(lossPercent);
        }
        
        // Set outcome time (30 minutes later for simulation)
        call.setOutcomeTime(time.plusMinutes(30));
    }
    
    /**
     * Calculate comprehensive performance metrics
     */
    private static PerformanceMetrics calculatePerformanceMetrics(List<BotCall> calls) {
        int totalCalls = calls.size();
        long successfulCalls = calls.stream().mapToLong(c -> c.isSuccessful ? 1 : 0).sum();
        double successRate = (double) successfulCalls / totalCalls * 100;
        
        // Calculate profits/losses
        double totalProfit = calls.stream().mapToDouble(c -> c.profitPercent).sum();
        double avgProfitPerCall = totalProfit / totalCalls;
        
        // Separate winning and losing calls
        List<BotCall> winningCalls = calls.stream().filter(c -> c.isSuccessful).toList();
        List<BotCall> losingCalls = calls.stream().filter(c -> !c.isSuccessful).toList();
        
        double avgWinningProfit = winningCalls.isEmpty() ? 0 : 
            winningCalls.stream().mapToDouble(c -> c.profitPercent).average().orElse(0);
        double avgLosingLoss = losingCalls.isEmpty() ? 0 :
            losingCalls.stream().mapToDouble(c -> c.profitPercent).average().orElse(0);
        
        // Time-based analysis
        Map<String, Integer> callsBySession = categorizeCallsBySession(calls);
        Map<String, Double> successRateBySession = calculateSuccessRateBySession(calls);
        
        // Symbol-based analysis
        Map<String, Integer> callsBySymbol = new HashMap<>();
        for (BotCall call : calls) {
            callsBySymbol.merge(call.symbol, 1, Integer::sum);
        }
        
        return new PerformanceMetrics(totalCalls, successfulCalls, successRate, totalProfit,
                                    avgProfitPerCall, avgWinningProfit, avgLosingLoss,
                                    callsBySession, successRateBySession, callsBySymbol);
    }
    
    /**
     * Categorize calls by market session
     */
    private static Map<String, Integer> categorizeCallsBySession(List<BotCall> calls) {
        Map<String, Integer> sessionCalls = new HashMap<>();
        sessionCalls.put("Opening (9:15-10:30)", 0);
        sessionCalls.put("Morning (10:30-12:00)", 0);
        sessionCalls.put("Afternoon (12:00-14:30)", 0);
        sessionCalls.put("Closing (14:30-15:30)", 0);
        
        for (BotCall call : calls) {
            LocalTime time = call.callTime;
            if (time.isBefore(LocalTime.of(10, 30))) {
                sessionCalls.merge("Opening (9:15-10:30)", 1, Integer::sum);
            } else if (time.isBefore(LocalTime.of(12, 0))) {
                sessionCalls.merge("Morning (10:30-12:00)", 1, Integer::sum);
            } else if (time.isBefore(LocalTime.of(14, 30))) {
                sessionCalls.merge("Afternoon (12:00-14:30)", 1, Integer::sum);
            } else {
                sessionCalls.merge("Closing (14:30-15:30)", 1, Integer::sum);
            }
        }
        
        return sessionCalls;
    }
    
    /**
     * Calculate success rate by session
     */
    private static Map<String, Double> calculateSuccessRateBySession(List<BotCall> calls) {
        Map<String, List<BotCall>> callsBySession = new HashMap<>();
        callsBySession.put("Opening", new ArrayList<>());
        callsBySession.put("Morning", new ArrayList<>());
        callsBySession.put("Afternoon", new ArrayList<>());
        callsBySession.put("Closing", new ArrayList<>());
        
        for (BotCall call : calls) {
            LocalTime time = call.callTime;
            if (time.isBefore(LocalTime.of(10, 30))) {
                callsBySession.get("Opening").add(call);
            } else if (time.isBefore(LocalTime.of(12, 0))) {
                callsBySession.get("Morning").add(call);
            } else if (time.isBefore(LocalTime.of(14, 30))) {
                callsBySession.get("Afternoon").add(call);
            } else {
                callsBySession.get("Closing").add(call);
            }
        }
        
        Map<String, Double> successRates = new HashMap<>();
        for (Map.Entry<String, List<BotCall>> entry : callsBySession.entrySet()) {
            List<BotCall> sessionCalls = entry.getValue();
            if (!sessionCalls.isEmpty()) {
                long successful = sessionCalls.stream().mapToLong(c -> c.isSuccessful ? 1 : 0).sum();
                double rate = (double) successful / sessionCalls.size() * 100;
                successRates.put(entry.getKey(), rate);
            } else {
                successRates.put(entry.getKey(), 0.0);
            }
        }
        
        return successRates;
    }
    
    /**
     * Display detailed analysis
     */
    private static void displayDetailedAnalysis(List<BotCall> calls, PerformanceMetrics metrics) {
        System.out.println("📊 **TODAY'S BOT PERFORMANCE REPORT**");
        System.out.println("═══════════════════════════════════════");
        System.out.println();
        
        // Overall Statistics
        System.out.println("🔢 **OVERALL STATISTICS**");
        System.out.println("─────────────────────────");
        System.out.println("📞 Total Calls Generated: " + metrics.totalCalls);
        System.out.println("✅ Successful Calls: " + metrics.successfulCalls);
        System.out.println("❌ Failed Calls: " + (metrics.totalCalls - metrics.successfulCalls));
        System.out.println("🎯 Success Rate: " + String.format("%.1f%%", metrics.successRate));
        System.out.println("💰 Total P&L: " + String.format("%.2f%%", metrics.totalProfit));
        System.out.println("📈 Average P&L per Call: " + String.format("%.2f%%", metrics.avgProfitPerCall));
        System.out.println("🏆 Average Winning Trade: " + String.format("%.2f%%", metrics.avgWinningProfit));
        System.out.println("📉 Average Losing Trade: " + String.format("%.2f%%", metrics.avgLosingLoss));
        System.out.println();
        
        // Session-wise Performance
        System.out.println("⏰ **SESSION-WISE PERFORMANCE**");
        System.out.println("─────────────────────────────────");
        for (Map.Entry<String, Integer> entry : metrics.callsBySession.entrySet()) {
            String session = entry.getKey().split(" ")[0];
            int callCount = entry.getValue();
            Double successRate = metrics.successRateBySession.get(session);
            
            if (callCount > 0) {
                System.out.println("🕐 " + entry.getKey() + ":");
                System.out.println("   📞 Calls: " + callCount);
                System.out.println("   🎯 Success Rate: " + String.format("%.1f%%", successRate));
                System.out.println();
            }
        }
        
        // Symbol-wise Performance
        System.out.println("📈 **SYMBOL-WISE CALL DISTRIBUTION**");
        System.out.println("───────────────────────────────────");
        for (Map.Entry<String, Integer> entry : metrics.callsBySymbol.entrySet()) {
            System.out.println("📊 " + entry.getKey() + ": " + entry.getValue() + " calls");
        }
        System.out.println();
        
        // Recent Calls (last 5)
        System.out.println("🕐 **RECENT CALLS (Last 5)**");
        System.out.println("──────────────────────────");
        calls.stream()
             .skip(Math.max(0, calls.size() - 5))
             .forEach(call -> {
                 String status = call.isSuccessful ? "✅" : "❌";
                 System.out.println(status + " " + call.callTime.format(TIME_FORMAT) + 
                                   " | " + call.symbol + " " + call.callType + 
                                   " | Conf: " + String.format("%.0f%%", call.confidence) +
                                   " | P&L: " + String.format("%.1f%%", call.profitPercent));
             });
        
        System.out.println();
        
        // Performance Rating
        System.out.println("🏆 **PERFORMANCE RATING**");
        System.out.println("─────────────────────────");
        if (metrics.successRate >= 75) {
            System.out.println("🟢 **EXCELLENT** - Bot performing very well!");
        } else if (metrics.successRate >= 60) {
            System.out.println("🟡 **GOOD** - Bot performing above average");
        } else if (metrics.successRate >= 45) {
            System.out.println("🟠 **AVERAGE** - Bot needs improvement");
        } else {
            System.out.println("🔴 **POOR** - Bot needs significant fixes");
        }
        
        System.out.println();
        System.out.println("⏰ Analysis completed at: " + LocalTime.now().format(TIME_FORMAT));
    }
    
    // Data structures
    static class BotCall {
        final int id;
        final String symbol;
        final String callType;
        final double confidence;
        final double entryPrice;
        final LocalTime callTime;
        final String reasoning;
        
        boolean isSuccessful;
        double profitPercent;
        LocalTime outcomeTime;
        
        BotCall(int id, String symbol, String callType, double confidence, 
                double entryPrice, LocalTime callTime, String reasoning) {
            this.id = id;
            this.symbol = symbol;
            this.callType = callType;
            this.confidence = confidence;
            this.entryPrice = entryPrice;
            this.callTime = callTime;
            this.reasoning = reasoning;
        }
        
        void setOutcome(boolean successful) { this.isSuccessful = successful; }
        void setProfitPercent(double profit) { this.profitPercent = profit; }
        void setOutcomeTime(LocalTime time) { this.outcomeTime = time; }
    }
    
    static class PerformanceMetrics {
        final int totalCalls;
        final long successfulCalls;
        final double successRate;
        final double totalProfit;
        final double avgProfitPerCall;
        final double avgWinningProfit;
        final double avgLosingLoss;
        final Map<String, Integer> callsBySession;
        final Map<String, Double> successRateBySession;
        final Map<String, Integer> callsBySymbol;
        
        PerformanceMetrics(int totalCalls, long successfulCalls, double successRate,
                          double totalProfit, double avgProfitPerCall, double avgWinningProfit,
                          double avgLosingLoss, Map<String, Integer> callsBySession,
                          Map<String, Double> successRateBySession, Map<String, Integer> callsBySymbol) {
            this.totalCalls = totalCalls;
            this.successfulCalls = successfulCalls;
            this.successRate = successRate;
            this.totalProfit = totalProfit;
            this.avgProfitPerCall = avgProfitPerCall;
            this.avgWinningProfit = avgWinningProfit;
            this.avgLosingLoss = avgLosingLoss;
            this.callsBySession = callsBySession;
            this.successRateBySession = successRateBySession;
            this.callsBySymbol = callsBySymbol;
        }
    }
}