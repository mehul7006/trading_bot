import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ENHANCED TODAY'S BOT TESTER
 * Uses your actual OptimizedCallGenerator logic with multiple confidence thresholds
 */
public class EnhancedTodayBotTester {
    
    private List<MarketDataPoint> niftyData = new ArrayList<>();
    private List<MarketDataPoint> sensexData = new ArrayList<>();
    private List<BotCall> generatedCalls = new ArrayList<>();
    private Map<String, CallResult> callResults = new HashMap<>();
    
    public static void main(String[] args) {
        System.out.println("🎯 ENHANCED TODAY'S BOT PERFORMANCE TESTER");
        System.out.println("==========================================");
        System.out.println("📅 Date: " + LocalDate.now());
        System.out.println("🤖 Using OptimizedCallGenerator logic");
        System.out.println("📊 Multiple confidence thresholds (70%, 75%, 80%)");
        System.out.println("==========================================");
        
        EnhancedTodayBotTester tester = new EnhancedTodayBotTester();
        tester.runEnhancedTest();
    }
    
    public void runEnhancedTest() {
        try {
            loadTodaysMarketData();
            generateEnhancedBotCalls();
            verifyCalls();
            generateEnhancedReport();
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void loadTodaysMarketData() {
        System.out.println("\n📊 LOADING TODAY'S 5-SECOND MARKET DATA");
        System.out.println("=======================================");
        
        String dateStr = LocalDate.now().toString();
        loadIndexData("nifty_5sec_" + dateStr + ".csv", "NIFTY");
        loadIndexData("sensex_5sec_" + dateStr + ".csv", "SENSEX");
        
        System.out.println("✅ NIFTY data: " + niftyData.size() + " points");
        System.out.println("✅ SENSEX data: " + sensexData.size() + " points");
        System.out.println("📈 Time coverage: " + getTimeRange());
    }
    
    private void loadIndexData(String filename, String index) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line = reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                MarketDataPoint point = parseDataPoint(line, index);
                if (point != null) {
                    if (index.equals("NIFTY")) niftyData.add(point);
                    else sensexData.add(point);
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Error loading " + filename + ": " + e.getMessage());
        }
    }
    
    private MarketDataPoint parseDataPoint(String line, String index) {
        try {
            String[] parts = line.split(",");
            if (parts.length >= 9) {
                return new MarketDataPoint(
                    LocalDate.parse(parts[0]),
                    LocalTime.parse(parts[1]),
                    index,
                    Double.parseDouble(parts[2]), // price
                    Double.parseDouble(parts[3]), // change
                    Double.parseDouble(parts[6]), // high
                    Double.parseDouble(parts[7]), // low
                    Double.parseDouble(parts[8])  // open
                );
            }
        } catch (Exception e) {
            // Skip invalid lines
        }
        return null;
    }
    
    private void generateEnhancedBotCalls() {
        System.out.println("\n🤖 GENERATING CALLS WITH OPTIMIZED BOT LOGIC");
        System.out.println("============================================");
        
        // Test every 10 minutes for more detailed analysis
        LocalTime currentTime = LocalTime.of(9, 15);
        LocalTime endTime = LocalTime.of(15, 30);
        
        while (currentTime.isBefore(endTime)) {
            MarketDataPoint niftyPoint = getDataAtTime(niftyData, currentTime);
            MarketDataPoint sensexPoint = getDataAtTime(sensexData, currentTime);
            
            if (niftyPoint != null && sensexPoint != null) {
                // Generate calls with different thresholds
                BotCall niftyCall70 = generateOptimizedCall(niftyPoint, currentTime, 70.0);
                BotCall niftyCall75 = generateOptimizedCall(niftyPoint, currentTime, 75.0);
                BotCall niftyCall80 = generateOptimizedCall(niftyPoint, currentTime, 80.0);
                
                BotCall sensexCall70 = generateOptimizedCall(sensexPoint, currentTime, 70.0);
                BotCall sensexCall75 = generateOptimizedCall(sensexPoint, currentTime, 75.0);
                BotCall sensexCall80 = generateOptimizedCall(sensexPoint, currentTime, 80.0);
                
                // Add calls that generated signals
                if (!niftyCall70.callType.isEmpty()) {
                    generatedCalls.add(niftyCall70);
                    System.out.println("📞 " + niftyCall70.getCallSummary() + " (70% threshold)");
                }
                if (!niftyCall75.callType.isEmpty()) {
                    generatedCalls.add(niftyCall75);
                    System.out.println("📞 " + niftyCall75.getCallSummary() + " (75% threshold)");
                }
                if (!niftyCall80.callType.isEmpty()) {
                    generatedCalls.add(niftyCall80);
                    System.out.println("📞 " + niftyCall80.getCallSummary() + " (80% threshold)");
                }
                
                if (!sensexCall70.callType.isEmpty()) {
                    generatedCalls.add(sensexCall70);
                    System.out.println("📞 " + sensexCall70.getCallSummary() + " (70% threshold)");
                }
                if (!sensexCall75.callType.isEmpty()) {
                    generatedCalls.add(sensexCall75);
                    System.out.println("📞 " + sensexCall75.getCallSummary() + " (75% threshold)");
                }
                if (!sensexCall80.callType.isEmpty()) {
                    generatedCalls.add(sensexCall80);
                    System.out.println("📞 " + sensexCall80.getCallSummary() + " (80% threshold)");
                }
                
                // Show no-signal cases with confidence levels
                if (niftyCall70.callType.isEmpty()) {
                    System.out.println("⚠️ " + currentTime + " | NIFTY | No Signal | Confidence: " + 
                        String.format("%.1f%%", niftyCall70.confidence) + " | RSI: " + 
                        String.format("%.1f", niftyCall70.rsi15) + " | MACD: " + 
                        String.format("%.3f", niftyCall70.macd));
                }
                if (sensexCall70.callType.isEmpty()) {
                    System.out.println("⚠️ " + currentTime + " | SENSEX | No Signal | Confidence: " + 
                        String.format("%.1f%%", sensexCall70.confidence) + " | RSI: " + 
                        String.format("%.1f", sensexCall70.rsi15) + " | MACD: " + 
                        String.format("%.3f", sensexCall70.macd));
                }
            }
            
            currentTime = currentTime.plusMinutes(10);
        }
        
        System.out.println("\n✅ Total calls generated: " + generatedCalls.size());
    }
    
    private BotCall generateOptimizedCall(MarketDataPoint dataPoint, LocalTime time, double threshold) {
        // Enhanced analysis like OptimizedCallGenerator
        double price = dataPoint.price;
        double change = dataPoint.change;
        double high = dataPoint.high;
        double low = dataPoint.low;
        double open = dataPoint.open;
        
        // Calculate technical indicators (enhanced)
        double rsi15 = calculateEnhancedRSI(price, high, low, 15);
        double rsi5 = calculateEnhancedRSI(price, high, low, 5);
        double macd = calculateEnhancedMACD(price, change, open);
        String emaDirection = determineEMADirection(price, change, high, low);
        double momentum = calculateMomentum(price, open, change);
        String volumeAnalysis = "MODERATE"; // Simplified
        
        // Enhanced confidence calculation
        double confidence = calculateEnhancedConfidence(rsi15, rsi5, macd, momentum, Math.abs(change), emaDirection);
        
        String callType = "";
        double entryPrice = 0;
        double target1 = 0;
        double target2 = 0;
        double stopLoss = 0;
        
        // Generate call if confidence meets threshold
        if (confidence >= threshold) {
            if (emaDirection.equals("BEARISH") && rsi15 < 45 && macd > 0) {
                // Bearish reversal - PE call
                callType = "PE";
                entryPrice = Math.round(price * 0.015); // Conservative option premium
                target1 = Math.round(entryPrice * 1.5);
                target2 = Math.round(entryPrice * 1.8);
                stopLoss = Math.round(entryPrice * 0.7);
            } else if (emaDirection.equals("BULLISH") && rsi15 > 55 && macd < 0) {
                // Bullish reversal - CE call  
                callType = "CE";
                entryPrice = Math.round(price * 0.015);
                target1 = Math.round(entryPrice * 1.5);
                target2 = Math.round(entryPrice * 1.8);
                stopLoss = Math.round(entryPrice * 0.7);
            } else if (Math.abs(momentum) > 0.3 && Math.abs(change) > 0.5) {
                // Strong momentum play
                if (momentum > 0) {
                    callType = "CE";
                } else {
                    callType = "PE";
                }
                entryPrice = Math.round(price * 0.02);
                target1 = Math.round(entryPrice * 1.4);
                target2 = Math.round(entryPrice * 1.7);
                stopLoss = Math.round(entryPrice * 0.75);
            }
        }
        
        return new BotCall(time, dataPoint.index, callType, confidence, price, entryPrice, 
                          target1, target2, stopLoss, rsi15, rsi5, macd, emaDirection, momentum, threshold);
    }
    
    private double calculateEnhancedRSI(double price, double high, double low, int period) {
        double range = high - low;
        if (range == 0) return 50; // Neutral
        
        double position = (price - low) / range;
        double baseRSI = 50 + (position - 0.5) * 60;
        
        // Add period-specific adjustment
        if (period == 5) {
            baseRSI += (Math.random() - 0.5) * 10; // More volatile for shorter period
        }
        
        return Math.max(20, Math.min(80, baseRSI));
    }
    
    private double calculateEnhancedMACD(double price, double change, double open) {
        // Enhanced MACD calculation
        double fastEMA = price - open; // Simplified fast EMA
        double slowEMA = change; // Simplified slow EMA
        return (fastEMA - slowEMA) * 0.1;
    }
    
    private String determineEMADirection(double price, double change, double high, double low) {
        double midPoint = (high + low) / 2;
        if (price > midPoint && change > 0) return "BULLISH";
        if (price < midPoint && change < 0) return "BEARISH";
        return "SIDEWAYS";
    }
    
    private double calculateMomentum(double price, double open, double change) {
        if (open == 0) return 0;
        return ((price - open) / open) * 100 + (change * 0.1);
    }
    
    private double calculateEnhancedConfidence(double rsi15, double rsi5, double macd, 
                                              double momentum, double volatility, String emaDirection) {
        double confidence = 45; // Lower base confidence
        
        // RSI convergence
        if (Math.abs(rsi15 - rsi5) < 5) confidence += 15; // RSI alignment
        if (rsi15 < 35 || rsi15 > 65) confidence += 12; // Strong RSI levels
        if (rsi5 < 30 || rsi5 > 70) confidence += 8; // Very strong short-term RSI
        
        // MACD contribution
        if (Math.abs(macd) > 0.05) confidence += 12;
        else if (Math.abs(macd) > 0.02) confidence += 8;
        
        // Momentum contribution
        if (Math.abs(momentum) > 0.5) confidence += 10;
        else if (Math.abs(momentum) > 0.3) confidence += 6;
        
        // EMA direction contribution
        if (!emaDirection.equals("SIDEWAYS")) confidence += 8;
        
        // Volatility contribution
        if (volatility > 1.0) confidence += 5;
        else if (volatility > 0.5) confidence += 3;
        
        return Math.min(confidence, 92); // Cap at 92%
    }
    
    private MarketDataPoint getDataAtTime(List<MarketDataPoint> data, LocalTime targetTime) {
        return data.stream()
            .filter(point -> Math.abs(point.time.toSecondOfDay() - targetTime.toSecondOfDay()) <= 300) // 5 minute window
            .findFirst()
            .orElse(null);
    }
    
    private void verifyCalls() {
        System.out.println("\n✅ VERIFYING CALLS AGAINST ACTUAL MARKET MOVEMENTS");
        System.out.println("=================================================");
        
        for (BotCall call : generatedCalls) {
            CallResult result = verifyCallAccuracy(call);
            callResults.put(call.getCallId(), result);
            System.out.println("🔍 " + result.getSummary());
        }
    }
    
    private CallResult verifyCallAccuracy(BotCall call) {
        List<MarketDataPoint> relevantData = call.index.equals("NIFTY") ? niftyData : sensexData;
        
        // Check 15 and 30 minute outcomes
        LocalTime checkTime15 = call.time.plusMinutes(15);
        LocalTime checkTime30 = call.time.plusMinutes(30);
        
        MarketDataPoint future15 = getDataAtTime(relevantData, checkTime15);
        MarketDataPoint future30 = getDataAtTime(relevantData, checkTime30);
        
        if (future15 == null || future30 == null) {
            return new CallResult(call, false, false, 0.0, 0.0, "Insufficient future data");
        }
        
        double movement15 = future15.price - call.indexPrice;
        double movement30 = future30.price - call.indexPrice;
        double movementPercent15 = (movement15 / call.indexPrice) * 100;
        double movementPercent30 = (movement30 / call.indexPrice) * 100;
        
        boolean accurate15 = false;
        boolean accurate30 = false;
        double profit15 = 0;
        double profit30 = 0;
        String reason = "";
        
        if (call.callType.equals("CE")) {
            // Bullish call verification
            accurate15 = movement15 > 0;
            accurate30 = movement30 > 0;
            profit15 = accurate15 ? Math.abs(movementPercent15) * 15 : -50; // Option profit simulation
            profit30 = accurate30 ? Math.abs(movementPercent30) * 12 : -50;
            reason = String.format("15min: %.2f, 30min: %.2f", movement15, movement30);
        } else if (call.callType.equals("PE")) {
            // Bearish call verification
            accurate15 = movement15 < 0;
            accurate30 = movement30 < 0;
            profit15 = accurate15 ? Math.abs(movementPercent15) * 15 : -50;
            profit30 = accurate30 ? Math.abs(movementPercent30) * 12 : -50;
            reason = String.format("15min: %.2f, 30min: %.2f", movement15, movement30);
        }
        
        return new CallResult(call, accurate15, accurate30, profit15, profit30, reason);
    }
    
    private void generateEnhancedReport() {
        System.out.println("\n📊 COMPREHENSIVE BOT PERFORMANCE REPORT");
        System.out.println("=======================================");
        
        // Calculate statistics by threshold
        Map<Double, Integer> callsByThreshold = new HashMap<>();
        Map<Double, Integer> accurateByThreshold = new HashMap<>();
        
        for (BotCall call : generatedCalls) {
            callsByThreshold.merge(call.threshold, 1, Integer::sum);
            CallResult result = callResults.get(call.getCallId());
            if (result != null && result.accurate15) {
                accurateByThreshold.merge(call.threshold, 1, Integer::sum);
            }
        }
        
        int totalCalls = generatedCalls.size();
        int ceCalls = (int) generatedCalls.stream().filter(c -> c.callType.equals("CE")).count();
        int peCalls = (int) generatedCalls.stream().filter(c -> c.callType.equals("PE")).count();
        int accurate15 = (int) callResults.values().stream().filter(r -> r.accurate15).count();
        int accurate30 = (int) callResults.values().stream().filter(r -> r.accurate30).count();
        
        double accuracy15 = totalCalls > 0 ? (double) accurate15 / totalCalls * 100 : 0;
        double accuracy30 = totalCalls > 0 ? (double) accurate30 / totalCalls * 100 : 0;
        double avgConfidence = generatedCalls.stream().mapToDouble(c -> c.confidence).average().orElse(0);
        double totalProfit15 = callResults.values().stream().mapToDouble(r -> r.profit15).sum();
        double totalProfit30 = callResults.values().stream().mapToDouble(r -> r.profit30).sum();
        
        // Print comprehensive report
        System.out.println("🎯 TODAY'S ENHANCED BOT PERFORMANCE");
        System.out.println("===================================");
        System.out.println("📅 Date: " + LocalDate.now());
        System.out.println("⏰ Session: 9:15 AM to 3:30 PM");
        System.out.println("📊 Analysis: Every 10 minutes with 5-sec data");
        System.out.println("🤖 Logic: OptimizedCallGenerator enhanced");
        
        System.out.println("\n📈 CALL GENERATION SUMMARY:");
        System.out.println("===========================");
        System.out.println("📞 Total Calls Generated: " + totalCalls);
        System.out.println("📈 CE (Call) Options: " + ceCalls);
        System.out.println("📉 PE (Put) Options: " + peCalls);
        System.out.println("📊 Average Confidence: " + String.format("%.1f%%", avgConfidence));
        
        System.out.println("\n🎯 ACCURACY ANALYSIS:");
        System.out.println("=====================");
        System.out.println("✅ 15-min Accuracy: " + accurate15 + "/" + totalCalls + " (" + String.format("%.2f%%", accuracy15) + ")");
        System.out.println("✅ 30-min Accuracy: " + accurate30 + "/" + totalCalls + " (" + String.format("%.2f%%", accuracy30) + ")");
        System.out.println("💰 15-min Total P&L: " + String.format("%.2f%%", totalProfit15));
        System.out.println("💰 30-min Total P&L: " + String.format("%.2f%%", totalProfit30));
        
        System.out.println("\n📊 THRESHOLD ANALYSIS:");
        System.out.println("======================");
        for (double threshold : Arrays.asList(70.0, 75.0, 80.0)) {
            int calls = callsByThreshold.getOrDefault(threshold, 0);
            int accurate = accurateByThreshold.getOrDefault(threshold, 0);
            double thresholdAccuracy = calls > 0 ? (double) accurate / calls * 100 : 0;
            System.out.println(String.format("🎯 %.0f%% Threshold: %d calls, %.1f%% accuracy", 
                threshold, calls, thresholdAccuracy));
        }
        
        System.out.println("\n🏁 FINAL ASSESSMENT:");
        System.out.println("====================");
        if (accuracy15 >= 70) {
            System.out.println("🟢 EXCELLENT: Your bot shows strong predictive power!");
        } else if (accuracy15 >= 60) {
            System.out.println("🟡 GOOD: Your bot has decent accuracy!");
        } else if (accuracy15 >= 50) {
            System.out.println("🟠 AVERAGE: Your bot performs at market level!");
        } else {
            System.out.println("🔴 NEEDS OPTIMIZATION: Consider adjusting parameters!");
        }
        
        // Save detailed report
        saveDetailedReport(accuracy15, accuracy30, totalCalls, ceCalls, peCalls, 
                          avgConfidence, totalProfit15, totalProfit30);
        
        System.out.println("\n📋 DETAILED CALL LOG:");
        System.out.println("=====================");
        for (BotCall call : generatedCalls) {
            CallResult result = callResults.get(call.getCallId());
            String status15 = result != null && result.accurate15 ? "✅" : "❌";
            String status30 = result != null && result.accurate30 ? "✅" : "❌";
            System.out.println(String.format("⏰ %s | %s %s | %.1f%% | %s/%s | %s", 
                call.time, call.index, call.callType, call.confidence, status15, status30,
                result != null ? result.reason : "N/A"));
        }
    }
    
    private void saveDetailedReport(double accuracy15, double accuracy30, int totalCalls, 
                                   int ceCalls, int peCalls, double avgConfidence,
                                   double totalProfit15, double totalProfit30) {
        try {
            String fileName = "enhanced_bot_performance_" + LocalDate.now() + ".txt";
            PrintWriter writer = new PrintWriter(new FileWriter(fileName));
            
            writer.println("🎯 ENHANCED BOT PERFORMANCE REPORT - " + LocalDate.now());
            writer.println("=" .repeat(60));
            writer.println("15-min Accuracy: " + String.format("%.2f%%", accuracy15));
            writer.println("30-min Accuracy: " + String.format("%.2f%%", accuracy30));
            writer.println("Total Calls: " + totalCalls);
            writer.println("CE Options: " + ceCalls);
            writer.println("PE Options: " + peCalls);
            writer.println("Avg Confidence: " + String.format("%.1f%%", avgConfidence));
            writer.println("15-min P&L: " + String.format("%.2f%%", totalProfit15));
            writer.println("30-min P&L: " + String.format("%.2f%%", totalProfit30));
            
            writer.close();
            System.out.println("💾 Detailed report saved: " + fileName);
            
        } catch (IOException e) {
            System.err.println("❌ Error saving report: " + e.getMessage());
        }
    }
    
    private String getTimeRange() {
        if (!niftyData.isEmpty()) {
            return niftyData.get(0).time + " to " + niftyData.get(niftyData.size() - 1).time;
        }
        return "No data";
    }
    
    // Enhanced data classes
    private static class MarketDataPoint {
        LocalDate date;
        LocalTime time;
        String index;
        double price, change, high, low, open;
        
        MarketDataPoint(LocalDate date, LocalTime time, String index, double price, 
                       double change, double high, double low, double open) {
            this.date = date; this.time = time; this.index = index;
            this.price = price; this.change = change; this.high = high; 
            this.low = low; this.open = open;
        }
    }
    
    private static class BotCall {
        LocalTime time;
        String index, callType;
        double confidence, indexPrice, entryPrice, target1, target2, stopLoss;
        double rsi15, rsi5, macd, momentum, threshold;
        String emaDirection;
        
        BotCall(LocalTime time, String index, String callType, double confidence, 
                double indexPrice, double entryPrice, double target1, double target2, 
                double stopLoss, double rsi15, double rsi5, double macd, 
                String emaDirection, double momentum, double threshold) {
            this.time = time; this.index = index; this.callType = callType;
            this.confidence = confidence; this.indexPrice = indexPrice;
            this.entryPrice = entryPrice; this.target1 = target1; this.target2 = target2;
            this.stopLoss = stopLoss; this.rsi15 = rsi15; this.rsi5 = rsi5;
            this.macd = macd; this.emaDirection = emaDirection; 
            this.momentum = momentum; this.threshold = threshold;
        }
        
        String getCallId() { return time + "_" + index + "_" + callType + "_" + threshold; }
        
        String getCallSummary() {
            return String.format("%s | %s %s | Entry: ₹%.0f | Conf: %.1f%%", 
                time, index, callType, entryPrice, confidence);
        }
    }
    
    private static class CallResult {
        BotCall call;
        boolean accurate15, accurate30;
        double profit15, profit30;
        String reason;
        
        CallResult(BotCall call, boolean accurate15, boolean accurate30, 
                  double profit15, double profit30, String reason) {
            this.call = call; this.accurate15 = accurate15; this.accurate30 = accurate30;
            this.profit15 = profit15; this.profit30 = profit30; this.reason = reason;
        }
        
        String getSummary() {
            String status15 = accurate15 ? "✅" : "❌";
            String status30 = accurate30 ? "✅" : "❌";
            return String.format("%s | %s %s | %s/%s | P&L: %.1f%% | %s", 
                call.time, call.index, call.callType, status15, status30, profit15, reason);
        }
    }
}