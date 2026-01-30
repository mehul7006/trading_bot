import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * TODAY'S BOT PERFORMANCE TESTER
 * Tests bot accuracy against real 5-second market data for today
 */
public class TodayBotPerformanceTester {
    
    private List<MarketDataPoint> niftyData = new ArrayList<>();
    private List<MarketDataPoint> sensexData = new ArrayList<>();
    private List<BotCall> generatedCalls = new ArrayList<>();
    private Map<String, CallResult> callResults = new HashMap<>();
    
    public static void main(String[] args) {
        System.out.println("🎯 TODAY'S BOT PERFORMANCE TESTER");
        System.out.println("==================================");
        System.out.println("📅 Date: " + LocalDate.now());
        System.out.println("📊 Testing with 5-second granular data");
        System.out.println("🤖 Bot accuracy validation");
        System.out.println("==================================");
        
        TodayBotPerformanceTester tester = new TodayBotPerformanceTester();
        tester.runCompleteTest();
    }
    
    public void runCompleteTest() {
        try {
            // Step 1: Load today's 5-second data
            loadTodaysMarketData();
            
            // Step 2: Generate bot calls throughout the day
            generateBotCallsWithTimestamps();
            
            // Step 3: Verify calls against actual market movements
            verifyCalls();
            
            // Step 4: Generate comprehensive report
            generatePerformanceReport();
            
        } catch (Exception e) {
            System.err.println("❌ Error in testing: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void loadTodaysMarketData() {
        System.out.println("\n📊 STEP 1: Loading Today's Market Data");
        System.out.println("=====================================");
        
        String dateStr = LocalDate.now().toString();
        String niftyFile = "nifty_5sec_" + dateStr + ".csv";
        String sensexFile = "sensex_5sec_" + dateStr + ".csv";
        
        try {
            // Load NIFTY data
            BufferedReader niftyReader = new BufferedReader(new FileReader(niftyFile));
            String line = niftyReader.readLine(); // Skip header
            while ((line = niftyReader.readLine()) != null) {
                MarketDataPoint point = parseDataPoint(line, "NIFTY");
                if (point != null) niftyData.add(point);
            }
            niftyReader.close();
            
            // Load SENSEX data
            BufferedReader sensexReader = new BufferedReader(new FileReader(sensexFile));
            line = sensexReader.readLine(); // Skip header
            while ((line = sensexReader.readLine()) != null) {
                MarketDataPoint point = parseDataPoint(line, "SENSEX");
                if (point != null) sensexData.add(point);
            }
            sensexReader.close();
            
            System.out.println("✅ NIFTY data loaded: " + niftyData.size() + " points");
            System.out.println("✅ SENSEX data loaded: " + sensexData.size() + " points");
            System.out.println("📈 Time range: " + getTimeRange());
            
        } catch (IOException e) {
            System.err.println("❌ Error loading market data: " + e.getMessage());
        }
    }
    
    private MarketDataPoint parseDataPoint(String line, String index) {
        try {
            String[] parts = line.split(",");
            if (parts.length >= 9) {
                LocalDate date = LocalDate.parse(parts[0]);
                LocalTime time = LocalTime.parse(parts[1]);
                double price = Double.parseDouble(parts[2]);
                double change = Double.parseDouble(parts[3]);
                double high = Double.parseDouble(parts[6]);
                double low = Double.parseDouble(parts[7]);
                double open = Double.parseDouble(parts[8]);
                
                return new MarketDataPoint(date, time, index, price, change, high, low, open);
            }
        } catch (Exception e) {
            // Skip invalid lines
        }
        return null;
    }
    
    private void generateBotCallsWithTimestamps() {
        System.out.println("\n🤖 STEP 2: Generating Bot Calls with Timestamps");
        System.out.println("===============================================");
        
        // Generate calls every 15 minutes throughout trading session
        LocalTime currentTime = LocalTime.of(9, 15);
        LocalTime endTime = LocalTime.of(15, 30);
        
        while (currentTime.isBefore(endTime)) {
            // Get market data at this time
            MarketDataPoint niftyPoint = getDataAtTime(niftyData, currentTime);
            MarketDataPoint sensexPoint = getDataAtTime(sensexData, currentTime);
            
            if (niftyPoint != null && sensexPoint != null) {
                // Generate NIFTY call
                BotCall niftyCall = generateCallForIndex(niftyPoint, "NIFTY", currentTime);
                if (niftyCall != null) {
                    generatedCalls.add(niftyCall);
                    System.out.println("📞 " + niftyCall.getCallSummary());
                }
                
                // Generate SENSEX call
                BotCall sensexCall = generateCallForIndex(sensexPoint, "SENSEX", currentTime);
                if (sensexCall != null) {
                    generatedCalls.add(sensexCall);
                    System.out.println("📞 " + sensexCall.getCallSummary());
                }
            }
            
            // Move to next 15-minute interval
            currentTime = currentTime.plusMinutes(15);
        }
        
        System.out.println("\n✅ Generated " + generatedCalls.size() + " calls throughout the day");
    }
    
    private BotCall generateCallForIndex(MarketDataPoint dataPoint, String index, LocalTime time) {
        // Simulate bot analysis
        double price = dataPoint.price;
        double change = dataPoint.change;
        double high = dataPoint.high;
        double low = dataPoint.low;
        
        // Calculate technical indicators (simplified)
        double rsi = calculateRSI(price, high, low);
        double macd = calculateMACD(price, change);
        String emaDirection = change > 0 ? "BULLISH" : change < 0 ? "BEARISH" : "SIDEWAYS";
        
        // Calculate confidence
        double confidence = calculateConfidence(rsi, macd, Math.abs(change));
        
        // Determine call type and targets
        String callType = "";
        double entryPrice = 0;
        double target1 = 0;
        double target2 = 0;
        double stopLoss = 0;
        
        if (confidence >= 75) {
            if (rsi < 40 && macd > 0) {
                // Bullish - CE call
                callType = "CE";
                entryPrice = price * 0.02; // Approximate option premium
                target1 = entryPrice * 1.5;
                target2 = entryPrice * 2.0;
                stopLoss = entryPrice * 0.7;
            } else if (rsi > 60 && macd < 0) {
                // Bearish - PE call
                callType = "PE";
                entryPrice = price * 0.02; // Approximate option premium
                target1 = entryPrice * 1.5;
                target2 = entryPrice * 2.0;
                stopLoss = entryPrice * 0.7;
            }
        }
        
        return new BotCall(time, index, callType, confidence, price, entryPrice, target1, target2, stopLoss, rsi, macd, emaDirection);
    }
    
    private double calculateRSI(double price, double high, double low) {
        // Simplified RSI calculation
        double range = high - low;
        double position = (price - low) / range;
        return 50 + (position - 0.5) * 60; // Scale to 20-80 range
    }
    
    private double calculateMACD(double price, double change) {
        // Simplified MACD calculation
        return change * 0.1; // Simple momentum indicator
    }
    
    private double calculateConfidence(double rsi, double macd, double volatility) {
        double confidence = 50; // Base confidence
        
        // RSI contribution
        if (rsi < 30 || rsi > 70) confidence += 20;
        else if (rsi < 40 || rsi > 60) confidence += 10;
        
        // MACD contribution
        if (Math.abs(macd) > 0.05) confidence += 15;
        else if (Math.abs(macd) > 0.02) confidence += 10;
        
        // Volatility contribution
        if (volatility > 1.0) confidence += 10;
        else if (volatility > 0.5) confidence += 5;
        
        return Math.min(confidence, 95); // Cap at 95%
    }
    
    private MarketDataPoint getDataAtTime(List<MarketDataPoint> data, LocalTime targetTime) {
        for (MarketDataPoint point : data) {
            if (point.time.equals(targetTime) || 
                (point.time.isBefore(targetTime.plusMinutes(1)) && point.time.isAfter(targetTime.minusMinutes(1)))) {
                return point;
            }
        }
        return null;
    }
    
    private void verifyCalls() {
        System.out.println("\n✅ STEP 3: Verifying Calls Against Market Movements");
        System.out.println("==================================================");
        
        for (BotCall call : generatedCalls) {
            if (!call.callType.isEmpty()) {
                CallResult result = verifyCall(call);
                callResults.put(call.getCallId(), result);
                System.out.println("🔍 " + result.getSummary());
            }
        }
    }
    
    private CallResult verifyCall(BotCall call) {
        // Get subsequent market data after call time
        List<MarketDataPoint> relevantData = call.index.equals("NIFTY") ? niftyData : sensexData;
        
        LocalTime checkTime = call.time.plusMinutes(30); // Check 30 minutes later
        MarketDataPoint futurePoint = getDataAtTime(relevantData, checkTime);
        
        if (futurePoint == null) {
            return new CallResult(call, false, 0, "No future data available");
        }
        
        double priceMovement = futurePoint.price - call.indexPrice;
        boolean accurate = false;
        double profit = 0;
        String reason = "";
        
        if (call.callType.equals("CE")) {
            // Bullish call - check if price went up
            if (priceMovement > 0) {
                accurate = true;
                profit = (priceMovement / call.indexPrice) * 100; // Approximate option profit
                reason = "Price moved up by " + String.format("%.2f", priceMovement);
            } else {
                reason = "Price moved down by " + String.format("%.2f", Math.abs(priceMovement));
            }
        } else if (call.callType.equals("PE")) {
            // Bearish call - check if price went down
            if (priceMovement < 0) {
                accurate = true;
                profit = (Math.abs(priceMovement) / call.indexPrice) * 100; // Approximate option profit
                reason = "Price moved down by " + String.format("%.2f", Math.abs(priceMovement));
            } else {
                reason = "Price moved up by " + String.format("%.2f", priceMovement);
            }
        }
        
        return new CallResult(call, accurate, profit, reason);
    }
    
    private void generatePerformanceReport() {
        System.out.println("\n📊 STEP 4: Comprehensive Performance Report");
        System.out.println("===========================================");
        
        // Calculate statistics
        int totalCalls = generatedCalls.size();
        int callsWithSignals = (int) generatedCalls.stream().filter(c -> !c.callType.isEmpty()).count();
        int ceCalls = (int) generatedCalls.stream().filter(c -> c.callType.equals("CE")).count();
        int peCalls = (int) generatedCalls.stream().filter(c -> c.callType.equals("PE")).count();
        int accurateCalls = (int) callResults.values().stream().filter(r -> r.accurate).count();
        
        double accuracy = callsWithSignals > 0 ? (double) accurateCalls / callsWithSignals * 100 : 0;
        double avgConfidence = generatedCalls.stream().mapToDouble(c -> c.confidence).average().orElse(0);
        double totalProfit = callResults.values().stream().mapToDouble(r -> r.profit).sum();
        
        // Print detailed report
        System.out.println("🎯 TODAY'S BOT PERFORMANCE REPORT");
        System.out.println("==================================");
        System.out.println("📅 Date: " + LocalDate.now());
        System.out.println("⏰ Session: 9:15 AM to 3:30 PM");
        System.out.println("📊 Data: 5-second granular analysis");
        System.out.println("==================================");
        
        System.out.println("\n📈 CALL GENERATION SUMMARY:");
        System.out.println("===========================");
        System.out.println("📞 Total Analysis Points: " + totalCalls);
        System.out.println("🎯 Calls Generated: " + callsWithSignals);
        System.out.println("📈 CE (Call) Options: " + ceCalls);
        System.out.println("📉 PE (Put) Options: " + peCalls);
        System.out.println("❌ No Signal (Low Confidence): " + (totalCalls - callsWithSignals));
        
        System.out.println("\n🏆 ACCURACY ANALYSIS:");
        System.out.println("=====================");
        System.out.println("✅ Accurate Calls: " + accurateCalls + "/" + callsWithSignals);
        System.out.println("🎯 Accuracy Rate: " + String.format("%.2f%%", accuracy));
        System.out.println("📊 Average Confidence: " + String.format("%.1f%%", avgConfidence));
        System.out.println("💰 Total Profit: " + String.format("%.2f%%", totalProfit));
        
        System.out.println("\n📋 DETAILED CALL BREAKDOWN:");
        System.out.println("============================");
        for (BotCall call : generatedCalls) {
            if (call.callType.isEmpty()) {
                System.out.println("⏰ " + call.time + " | " + call.index + " | No Signal | Confidence: " + 
                    String.format("%.1f%%", call.confidence) + " (Below 75% threshold)");
            } else {
                CallResult result = callResults.get(call.getCallId());
                String status = result != null && result.accurate ? "✅" : "❌";
                System.out.println("⏰ " + call.time + " | " + call.index + " " + call.callType + " | " + 
                    String.format("%.1f%%", call.confidence) + " | " + status + " | " + 
                    (result != null ? result.reason : "Not verified"));
            }
        }
        
        System.out.println("\n🎯 CONFIDENCE LEVEL ANALYSIS:");
        System.out.println("==============================");
        long highConf = generatedCalls.stream().filter(c -> c.confidence >= 85).count();
        long medConf = generatedCalls.stream().filter(c -> c.confidence >= 75 && c.confidence < 85).count();
        long lowConf = generatedCalls.stream().filter(c -> c.confidence < 75).count();
        
        System.out.println("🔥 High Confidence (85%+): " + highConf + " calls");
        System.out.println("🎯 Medium Confidence (75-85%): " + medConf + " calls");
        System.out.println("⚠️ Low Confidence (<75%): " + lowConf + " calls (No signals generated)");
        
        System.out.println("\n🏁 FINAL ASSESSMENT:");
        System.out.println("====================");
        if (accuracy >= 80) {
            System.out.println("🟢 EXCELLENT: Your bot performed exceptionally well!");
        } else if (accuracy >= 70) {
            System.out.println("🟡 GOOD: Your bot showed solid performance!");
        } else if (accuracy >= 60) {
            System.out.println("🟠 AVERAGE: Your bot needs some improvements!");
        } else {
            System.out.println("🔴 NEEDS WORK: Your bot requires significant optimization!");
        }
        
        // Save report to file
        saveReportToFile(accuracy, totalCalls, callsWithSignals, ceCalls, peCalls, accurateCalls, avgConfidence, totalProfit);
    }
    
    private void saveReportToFile(double accuracy, int totalCalls, int callsWithSignals, 
                                  int ceCalls, int peCalls, int accurateCalls, 
                                  double avgConfidence, double totalProfit) {
        try {
            String fileName = "bot_performance_report_" + LocalDate.now() + ".txt";
            PrintWriter writer = new PrintWriter(new FileWriter(fileName));
            
            writer.println("🎯 BOT PERFORMANCE REPORT - " + LocalDate.now());
            writer.println("=" .repeat(50));
            writer.println("Accuracy Rate: " + String.format("%.2f%%", accuracy));
            writer.println("Total Analysis Points: " + totalCalls);
            writer.println("Calls Generated: " + callsWithSignals);
            writer.println("CE Options: " + ceCalls);
            writer.println("PE Options: " + peCalls);
            writer.println("Accurate Calls: " + accurateCalls);
            writer.println("Average Confidence: " + String.format("%.1f%%", avgConfidence));
            writer.println("Total Profit: " + String.format("%.2f%%", totalProfit));
            
            writer.close();
            System.out.println("💾 Report saved: " + fileName);
            
        } catch (IOException e) {
            System.err.println("❌ Error saving report: " + e.getMessage());
        }
    }
    
    private String getTimeRange() {
        if (!niftyData.isEmpty()) {
            LocalTime start = niftyData.get(0).time;
            LocalTime end = niftyData.get(niftyData.size() - 1).time;
            return start + " to " + end;
        }
        return "No data";
    }
    
    // Data classes
    private static class MarketDataPoint {
        LocalDate date;
        LocalTime time;
        String index;
        double price;
        double change;
        double high;
        double low;
        double open;
        
        MarketDataPoint(LocalDate date, LocalTime time, String index, double price, 
                       double change, double high, double low, double open) {
            this.date = date;
            this.time = time;
            this.index = index;
            this.price = price;
            this.change = change;
            this.high = high;
            this.low = low;
            this.open = open;
        }
    }
    
    private static class BotCall {
        LocalTime time;
        String index;
        String callType; // CE, PE, or empty for no signal
        double confidence;
        double indexPrice;
        double entryPrice;
        double target1;
        double target2;
        double stopLoss;
        double rsi;
        double macd;
        String emaDirection;
        
        BotCall(LocalTime time, String index, String callType, double confidence, 
                double indexPrice, double entryPrice, double target1, double target2, 
                double stopLoss, double rsi, double macd, String emaDirection) {
            this.time = time;
            this.index = index;
            this.callType = callType;
            this.confidence = confidence;
            this.indexPrice = indexPrice;
            this.entryPrice = entryPrice;
            this.target1 = target1;
            this.target2 = target2;
            this.stopLoss = stopLoss;
            this.rsi = rsi;
            this.macd = macd;
            this.emaDirection = emaDirection;
        }
        
        String getCallId() {
            return time + "_" + index + "_" + callType;
        }
        
        String getCallSummary() {
            if (callType.isEmpty()) {
                return time + " | " + index + " | No Signal | Confidence: " + 
                    String.format("%.1f%%", confidence) + " (Below threshold)";
            } else {
                return time + " | " + index + " " + callType + " | Entry: ₹" + 
                    String.format("%.2f", entryPrice) + " | Confidence: " + 
                    String.format("%.1f%%", confidence);
            }
        }
    }
    
    private static class CallResult {
        BotCall call;
        boolean accurate;
        double profit;
        String reason;
        
        CallResult(BotCall call, boolean accurate, double profit, String reason) {
            this.call = call;
            this.accurate = accurate;
            this.profit = profit;
            this.reason = reason;
        }
        
        String getSummary() {
            String status = accurate ? "✅ ACCURATE" : "❌ INCORRECT";
            return call.time + " | " + call.index + " " + call.callType + " | " + 
                status + " | Profit: " + String.format("%.2f%%", profit) + " | " + reason;
        }
    }
}