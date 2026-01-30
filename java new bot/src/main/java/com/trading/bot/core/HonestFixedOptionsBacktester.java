import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HONEST FIXED OPTIONS BACKTESTER
 * Tests your FIXED CE/PE options bot with all improvements
 * Uses Greeks + IV Analysis + PE Generation + 75%+ confidence
 * Provides honest win rate analysis for NIFTY & SENSEX
 */
public class HonestFixedOptionsBacktester {
    
    // Core components (fixed versions)
    private final OptionsGreeksCalculator greeksCalculator;
    private final ImpliedVolatilityAnalyzer ivAnalyzer;
    private final EnhancedPECallGenerator peGenerator;
    private final MasterOptionsTrader masterTrader;
    
    // Market data
    private List<MarketDataPoint> niftyData = new ArrayList<>();
    private List<MarketDataPoint> sensexData = new ArrayList<>();
    
    // Testing parameters
    private final double confidenceThreshold = 75.0; // Strict 75%+ requirement
    private final List<FixedOptionsCall> generatedCalls = new ArrayList<>();
    private final List<FixedTradeResult> tradeResults = new ArrayList<>();
    
    // Performance tracking
    private int totalCalls = 0;
    private int winningCalls = 0;
    private double totalPnL = 0.0;
    private Map<String, CallTypePerformance> callTypeStats = new HashMap<>();
    
    public HonestFixedOptionsBacktester() {
        System.out.println("🔍 HONEST FIXED OPTIONS BACKTESTER");
        System.out.println("==================================");
        System.out.println("✅ Testing FIXED CE/PE options bot");
        System.out.println("✅ Greeks + IV Analysis + PE Generation");
        System.out.println("✅ 75%+ confidence threshold enforced");
        System.out.println("✅ NIFTY & SENSEX predictions tested");
        System.out.println("==================================");
        
        // Initialize fixed components
        this.greeksCalculator = new OptionsGreeksCalculator();
        this.ivAnalyzer = new ImpliedVolatilityAnalyzer();
        this.peGenerator = new EnhancedPECallGenerator();
        this.masterTrader = new MasterOptionsTrader();
        
        // Initialize call type tracking
        initializeCallTypeTracking();
    }
    
    /**
     * PART 1: Load real market data for today
     */
    public void loadTodaysMarketData() {
        System.out.println("📈 PART 1: Loading today's real market data...");
        
        try {
            // Load NIFTY data
            niftyData = loadMarketData("nifty");
            if (!niftyData.isEmpty()) {
                System.out.println("✅ NIFTY data loaded: " + niftyData.size() + " data points");
            }
            
            // Load SENSEX data
            sensexData = loadMarketData("sensex");
            if (!sensexData.isEmpty()) {
                System.out.println("✅ SENSEX data loaded: " + sensexData.size() + " data points");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error loading market data: " + e.getMessage());
        }
    }
    
    /**
     * PART 2: Generate fixed CE/PE options calls with 75%+ confidence
     */
    public void generateFixedOptionsCalls() {
        System.out.println("🎯 PART 2: Generating FIXED CE/PE options calls (75%+ confidence)...");
        
        LocalDateTime startTime = LocalDateTime.now().withHour(9).withMinute(15).withSecond(0);
        LocalDateTime endTime = LocalDateTime.now().withHour(15).withMinute(30).withSecond(0);
        LocalDateTime currentTime = startTime;
        
        while (currentTime.isBefore(endTime)) {
            // Generate calls every 20 minutes (more frequent testing)
            if (currentTime.getMinute() % 20 == 0) {
                
                // Generate NIFTY calls with fixed system
                List<FixedOptionsCall> niftyCalls = generateFixedCallsForIndex("NIFTY", currentTime);
                generatedCalls.addAll(niftyCalls);
                
                // Generate SENSEX calls with fixed system
                List<FixedOptionsCall> sensexCalls = generateFixedCallsForIndex("SENSEX", currentTime);
                generatedCalls.addAll(sensexCalls);
            }
            
            currentTime = currentTime.plusMinutes(1);
        }
        
        // Filter only 75%+ confidence calls
        List<FixedOptionsCall> highConfidenceCalls = generatedCalls.stream()
            .filter(call -> call.finalConfidence >= confidenceThreshold)
            .collect(Collectors.toList());
        
        generatedCalls.clear();
        generatedCalls.addAll(highConfidenceCalls);
        
        System.out.println("✅ Generated " + generatedCalls.size() + " FIXED options calls (75%+ confidence)");
        
        // Print detailed call summary
        printFixedCallSummary();
    }
    
    /**
     * Generate fixed calls for specific index
     */
    private List<FixedOptionsCall> generateFixedCallsForIndex(String index, LocalDateTime timestamp) {
        List<FixedOptionsCall> calls = new ArrayList<>();
        
        try {
            // Get current market data
            double spotPrice = getCurrentSpotPrice(index, timestamp);
            List<Double> recentPrices = getRecentPrices(index, timestamp, 20);
            
            if (spotPrice == 0 || recentPrices.size() < 10) {
                return calls;
            }
            
            // Use master trader to generate comprehensive calls
            List<MasterOptionsTrader.MasterOptionsCall> masterCalls = 
                masterTrader.generateComprehensiveOptionsCalls(index, spotPrice, recentPrices, timestamp);
            
            // Convert to fixed options calls with enhanced analysis
            for (MasterOptionsTrader.MasterOptionsCall masterCall : masterCalls) {
                FixedOptionsCall fixedCall = enhanceWithFixedAnalysis(masterCall, recentPrices, timestamp);
                if (fixedCall != null && fixedCall.finalConfidence >= confidenceThreshold) {
                    calls.add(fixedCall);
                }
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error generating " + index + " calls: " + e.getMessage());
        }
        
        return calls;
    }
    
    /**
     * Enhance master call with fixed analysis components
     */
    private FixedOptionsCall enhanceWithFixedAnalysis(MasterOptionsTrader.MasterOptionsCall masterCall,
                                                     List<Double> recentPrices, LocalDateTime timestamp) {
        
        try {
            // Enhanced Greeks analysis
            OptionsGreeksCalculator.GreeksAnalysis enhancedGreeks = 
                greeksCalculator.analyzeGreeksForTrading(masterCall.greeks, masterCall.optionType, 
                                                        masterCall.spotPrice, masterCall.strike);
            
            // Enhanced IV analysis
            double marketIV = ivAnalyzer.calculateImpliedVolatility(
                masterCall.entryPremium, masterCall.spotPrice, masterCall.strike, 
                7.0/365.0, masterCall.optionType);
            
            ImpliedVolatilityAnalyzer.IVAnalysis enhancedIV = 
                ivAnalyzer.analyzeIVLevels(marketIV, masterCall.index, masterCall.optionType, 
                                          masterCall.spotPrice, masterCall.strike);
            
            // Enhanced PE analysis (if PE call)
            EnhancedPECallGenerator.BearishAnalysis bearishAnalysis = null;
            if (masterCall.optionType.equals("PE")) {
                bearishAnalysis = peGenerator.analyzeBearishConditions(
                    masterCall.index, masterCall.spotPrice, recentPrices, timestamp);
            }
            
            // Calculate enhanced final confidence
            double enhancedConfidence = calculateEnhancedConfidence(
                masterCall, enhancedGreeks, enhancedIV, bearishAnalysis);
            
            // Only create if meets 75% threshold
            if (enhancedConfidence < confidenceThreshold) {
                return null;
            }
            
            // Generate comprehensive analysis
            String comprehensiveAnalysis = generateComprehensiveAnalysis(
                masterCall, enhancedGreeks, enhancedIV, bearishAnalysis);
            
            return new FixedOptionsCall(
                masterCall.callId + "_FIXED", masterCall.index, masterCall.optionType, 
                masterCall.strike, timestamp, masterCall.spotPrice, masterCall.entryPremium,
                enhancedConfidence, masterCall.target1, masterCall.target2, masterCall.stopLoss,
                comprehensiveAnalysis, enhancedGreeks, enhancedIV, bearishAnalysis, marketIV
            );
            
        } catch (Exception e) {
            System.err.println("❌ Error enhancing call: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * PART 3: Execute honest backtesting with fixed system
     */
    public void executeHonestFixedBacktesting() {
        System.out.println("🔍 PART 3: Executing honest backtesting with FIXED system...");
        
        for (FixedOptionsCall call : generatedCalls) {
            try {
                // Simulate trade with enhanced analysis
                FixedTradeResult result = simulateFixedOptionsTradeResult(call);
                tradeResults.add(result);
                
                // Update statistics
                totalCalls++;
                if (result.isWinner) {
                    winningCalls++;
                }
                totalPnL += result.pnl;
                
                // Update call type statistics
                updateCallTypeStats(call, result);
                
            } catch (Exception e) {
                System.err.println("❌ Error backtesting call " + call.callId + ": " + e.getMessage());
            }
        }
        
        System.out.println("✅ Fixed backtesting completed for " + totalCalls + " options calls");
    }
    
    /**
     * Simulate fixed options trade result with enhanced analysis
     */
    private FixedTradeResult simulateFixedOptionsTradeResult(FixedOptionsCall call) {
        // Get spot price movement after call time (1-hour holding period)
        LocalDateTime exitTime = call.timestamp.plusMinutes(60);
        double exitSpotPrice = getCurrentSpotPrice(call.index, exitTime);
        
        if (exitSpotPrice == 0) {
            exitSpotPrice = call.spotPrice; // No movement if no data
        }
        
        // Calculate option price at exit using Greeks
        double timeElapsed = 60.0 / (24.0 * 60); // 1 hour in days
        double newTimeToExpiry = Math.max(0.001, (7.0 / 365.0) - timeElapsed);
        
        OptionsGreeksCalculator.OptionsGreeks exitGreeks = greeksCalculator.calculateGreeks(
            exitSpotPrice, call.strike, newTimeToExpiry, call.marketIV, call.optionType);
        
        double exitPremium = exitGreeks.optionPrice;
        
        // Calculate P&L
        double pnl = exitPremium - call.entryPremium;
        boolean isWinner = pnl > 0;
        
        // Enhanced exit analysis
        String exitReason = analyzeExitReason(call, exitSpotPrice, exitPremium, exitGreeks);
        
        // Calculate confidence validation
        double actualPerformance = isWinner ? 100.0 : 0.0;
        double confidenceAccuracy = Math.abs(call.finalConfidence - actualPerformance);
        
        return new FixedTradeResult(
            call, exitSpotPrice, exitPremium, pnl, isWinner, exitReason,
            exitGreeks, confidenceAccuracy
        );
    }
    
    /**
     * PART 4: Generate honest performance report
     */
    public void generateHonestFixedPerformanceReport() {
        System.out.println("📊 PART 4: Generating honest FIXED performance report...");
        
        double winRate = totalCalls > 0 ? (double) winningCalls / totalCalls * 100 : 0;
        double avgPnL = totalCalls > 0 ? totalPnL / totalCalls : 0;
        
        System.out.println("\n📊 HONEST FIXED CE/PE OPTIONS BACKTESTING REPORT");
        System.out.println("================================================");
        System.out.println("📅 Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        System.out.println("⏰ Session: 9:15 AM to 3:30 PM");
        System.out.println("🎯 Confidence Threshold: 75%+ STRICTLY ENFORCED");
        System.out.println("🔧 System: FIXED with Greeks + IV + PE Generation");
        System.out.println("================================================");
        
        System.out.println("\n🎯 OVERALL FIXED PERFORMANCE:");
        System.out.println("==============================");
        System.out.printf("📊 Total Calls Generated: %d%n", totalCalls);
        System.out.printf("✅ Winning Calls: %d%n", winningCalls);
        System.out.printf("❌ Losing Calls: %d%n", totalCalls - winningCalls);
        System.out.printf("🏆 Win Rate: %.2f%%%n", winRate);
        System.out.printf("💰 Total P&L: ₹%.2f%n", totalPnL);
        System.out.printf("📊 Average P&L per Call: ₹%.2f%n", avgPnL);
        
        // Detailed call type breakdown
        System.out.println("\n📈 FIXED CALL TYPE BREAKDOWN:");
        System.out.println("==============================");
        for (Map.Entry<String, CallTypePerformance> entry : callTypeStats.entrySet()) {
            CallTypePerformance stats = entry.getValue();
            double typeWinRate = stats.total > 0 ? (double) stats.wins / stats.total * 100 : 0;
            double typeAvgPnL = stats.total > 0 ? stats.totalPnL / stats.total : 0;
            
            System.out.printf("📊 %s: %d calls, %d wins (%.2f%% win rate), ₹%.2f avg P&L%n", 
                entry.getKey(), stats.total, stats.wins, typeWinRate, typeAvgPnL);
        }
        
        // Improvement analysis
        analyzeImprovements(winRate, avgPnL);
        
        // Confidence analysis
        analyzeConfidenceAccuracy();
        
        // Save detailed report
        saveDetailedFixedReport();
    }
    
    /**
     * Analyze improvements vs original system
     */
    private void analyzeImprovements(double winRate, double avgPnL) {
        System.out.println("\n🚀 IMPROVEMENT ANALYSIS:");
        System.out.println("========================");
        
        // Compare with original results
        double originalWinRate = 38.10;
        double originalAvgPnL = -49.94;
        
        double winRateImprovement = winRate - originalWinRate;
        double pnlImprovement = avgPnL - originalAvgPnL;
        
        System.out.printf("📈 Win Rate Improvement: %.2f%% → %.2f%% (%+.2f%%)%n", 
                         originalWinRate, winRate, winRateImprovement);
        System.out.printf("💰 P&L Improvement: ₹%.2f → ₹%.2f (%+.2f)%n", 
                         originalAvgPnL, avgPnL, pnlImprovement);
        
        // Target achievement
        System.out.println("\n🎯 TARGET ACHIEVEMENT:");
        System.out.println("======================");
        System.out.printf("🎯 Win Rate Target (60%%): %s%n", winRate >= 60 ? "✅ ACHIEVED" : "❌ NOT YET");
        System.out.printf("💰 Profitability Target: %s%n", avgPnL > 0 ? "✅ ACHIEVED" : "❌ NOT YET");
        System.out.printf("📊 PE Call Generation: %s%n", 
                         callTypeStats.containsKey("NIFTY_PE") || callTypeStats.containsKey("SENSEX_PE") ? 
                         "✅ ACHIEVED" : "❌ NOT YET");
        
        // Overall verdict
        if (winRate >= 60 && avgPnL > 0) {
            System.out.println("\n🟢 EXCELLENT: Fixed system achieved all targets!");
        } else if (winRate >= 50 && avgPnL > -10) {
            System.out.println("\n🟡 GOOD: Significant improvements, close to targets");
        } else if (winRateImprovement > 10 || pnlImprovement > 30) {
            System.out.println("\n🟠 PROGRESS: Clear improvements, needs fine-tuning");
        } else {
            System.out.println("\n🔴 NEEDS WORK: Further improvements required");
        }
    }
    
    /**
     * Analyze confidence accuracy
     */
    private void analyzeConfidenceAccuracy() {
        System.out.println("\n🎯 CONFIDENCE ACCURACY ANALYSIS:");
        System.out.println("================================");
        
        double avgConfidenceError = tradeResults.stream()
            .mapToDouble(r -> r.confidenceAccuracy)
            .average().orElse(0.0);
        
        long wellCalibratedCalls = tradeResults.stream()
            .filter(r -> r.confidenceAccuracy <= 25.0) // Within 25% of actual
            .count();
        
        double calibrationRate = totalCalls > 0 ? (double) wellCalibratedCalls / totalCalls * 100 : 0;
        
        System.out.printf("📊 Average Confidence Error: %.1f%%%n", avgConfidenceError);
        System.out.printf("🎯 Well-Calibrated Calls: %d/%d (%.1f%%)%n", 
                         wellCalibratedCalls, totalCalls, calibrationRate);
        
        if (calibrationRate >= 70) {
            System.out.println("✅ Excellent confidence calibration");
        } else if (calibrationRate >= 50) {
            System.out.println("🟡 Good confidence calibration");
        } else {
            System.out.println("❌ Confidence calibration needs improvement");
        }
    }
    
    // Helper methods
    private void initializeCallTypeTracking() {
        callTypeStats.put("NIFTY_CE", new CallTypePerformance());
        callTypeStats.put("NIFTY_PE", new CallTypePerformance());
        callTypeStats.put("SENSEX_CE", new CallTypePerformance());
        callTypeStats.put("SENSEX_PE", new CallTypePerformance());
    }
    
    private List<MarketDataPoint> loadMarketData(String index) {
        List<MarketDataPoint> data = new ArrayList<>();
        String fileName = findDataFile(index);
        
        if (fileName != null) {
            try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
                String line = reader.readLine(); // Skip header
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 6) {
                        LocalDateTime dateTime = LocalDateTime.parse(
                            parts[0] + "T" + parts[1], 
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                        );
                        double price = Double.parseDouble(parts[2]);
                        long volume = Long.parseLong(parts[5]);
                        data.add(new MarketDataPoint(dateTime, price, volume));
                    }
                }
            } catch (Exception e) {
                System.err.println("Error loading " + index + " data: " + e.getMessage());
            }
        }
        
        return data;
    }
    
    private String findDataFile(String index) {
        String[] possibleFiles = {
            index + "_5sec_" + LocalDate.now() + ".csv",
            index + "_historical_" + LocalDate.now() + ".csv",
            index + "_data_" + LocalDate.now() + ".csv"
        };
        
        for (String file : possibleFiles) {
            if (new File(file).exists()) {
                return file;
            }
        }
        return null;
    }
    
    private double getCurrentSpotPrice(String index, LocalDateTime timestamp) {
        List<MarketDataPoint> data = index.equals("NIFTY") ? niftyData : sensexData;
        
        return data.stream()
            .filter(d -> !d.timestamp.isAfter(timestamp))
            .max(Comparator.comparing(d -> d.timestamp))
            .map(d -> d.price)
            .orElse(0.0);
    }
    
    private List<Double> getRecentPrices(String index, LocalDateTime timestamp, int count) {
        List<MarketDataPoint> data = index.equals("NIFTY") ? niftyData : sensexData;
        
        return data.stream()
            .filter(d -> !d.timestamp.isAfter(timestamp))
            .sorted(Comparator.comparing((MarketDataPoint d) -> d.timestamp).reversed())
            .limit(count)
            .map(d -> d.price)
            .collect(Collectors.toList());
    }
    
    private double calculateEnhancedConfidence(MasterOptionsTrader.MasterOptionsCall masterCall,
                                             OptionsGreeksCalculator.GreeksAnalysis enhancedGreeks,
                                             ImpliedVolatilityAnalyzer.IVAnalysis enhancedIV,
                                             EnhancedPECallGenerator.BearishAnalysis bearishAnalysis) {
        
        double confidence = 0.0;
        
        // Base confidence from master call (30%)
        confidence += masterCall.finalConfidence * 0.3;
        
        // Enhanced Greeks analysis (25%)
        confidence += enhancedGreeks.confidence * 0.25;
        
        // Enhanced IV analysis (25%)
        confidence += enhancedIV.confidence * 0.25;
        
        // PE-specific analysis (20% if PE call)
        if (masterCall.optionType.equals("PE") && bearishAnalysis != null) {
            confidence += bearishAnalysis.confidence * 0.2;
        } else {
            confidence += 70.0 * 0.2; // Default for CE calls
        }
        
        return Math.min(95, Math.max(0, confidence));
    }
    
    private String generateComprehensiveAnalysis(MasterOptionsTrader.MasterOptionsCall masterCall,
                                               OptionsGreeksCalculator.GreeksAnalysis enhancedGreeks,
                                               ImpliedVolatilityAnalyzer.IVAnalysis enhancedIV,
                                               EnhancedPECallGenerator.BearishAnalysis bearishAnalysis) {
        
        StringBuilder analysis = new StringBuilder();
        analysis.append("FIXED Analysis: ");
        analysis.append("Greeks(").append(enhancedGreeks.recommendation).append("), ");
        analysis.append("IV(").append(enhancedIV.ivLevel).append(" ").append(String.format("%.1f", enhancedIV.ivPercentile)).append("%), ");
        
        if (bearishAnalysis != null) {
            analysis.append("Bearish(").append(String.format("%.1f", bearishAnalysis.confidence)).append("%), ");
        }
        
        analysis.append("Delta=").append(String.format("%.3f", masterCall.greeks.delta)).append(", ");
        analysis.append("Theta=").append(String.format("%.2f", masterCall.greeks.theta));
        
        return analysis.toString();
    }
    
    private String analyzeExitReason(FixedOptionsCall call, double exitSpotPrice, double exitPremium,
                                   OptionsGreeksCalculator.OptionsGreeks exitGreeks) {
        
        if (exitPremium >= call.target1) {
            return "Target 1 achieved - profitable exit";
        } else if (exitPremium <= call.stopLoss) {
            return "Stop-loss hit - risk management exit";
        } else if (Math.abs(exitGreeks.theta) > 15) {
            return "High time decay - theta management exit";
        } else {
            return "Time-based exit after 1 hour";
        }
    }
    
    private void updateCallTypeStats(FixedOptionsCall call, FixedTradeResult result) {
        String callType = call.index + "_" + call.optionType;
        CallTypePerformance stats = callTypeStats.get(callType);
        
        if (stats != null) {
            stats.total++;
            if (result.isWinner) {
                stats.wins++;
            }
            stats.totalPnL += result.pnl;
        }
    }
    
    private void printFixedCallSummary() {
        System.out.println("\n📊 FIXED CALL GENERATION SUMMARY:");
        System.out.println("==================================");
        
        Map<String, Long> callCounts = generatedCalls.stream()
            .collect(Collectors.groupingBy(c -> c.index + "_" + c.optionType, Collectors.counting()));
        
        callCounts.forEach((type, count) -> 
            System.out.println("📈 " + type + ": " + count + " calls (75%+ confidence)"));
        
        // Show confidence distribution
        double avgConfidence = generatedCalls.stream()
            .mapToDouble(c -> c.finalConfidence)
            .average().orElse(0.0);
        
        System.out.printf("🎯 Average Confidence: %.1f%%\n", avgConfidence);
        System.out.printf("🎯 Min Confidence: %.1f%%\n", 
                         generatedCalls.stream().mapToDouble(c -> c.finalConfidence).min().orElse(0.0));
        System.out.printf("🎯 Max Confidence: %.1f%%\n", 
                         generatedCalls.stream().mapToDouble(c -> c.finalConfidence).max().orElse(0.0));
    }
    
    private void saveDetailedFixedReport() {
        try {
            String fileName = "fixed_options_backtest_report_" + LocalDate.now() + ".txt";
            PrintWriter writer = new PrintWriter(new FileWriter(fileName));
            
            writer.println("HONEST FIXED CE/PE OPTIONS BACKTESTING REPORT");
            writer.println("==============================================");
            writer.println("Date: " + LocalDate.now());
            writer.println("System: FIXED with Greeks + IV + PE Generation");
            writer.println("Confidence Threshold: 75%+ STRICTLY ENFORCED");
            writer.println("Total Calls: " + totalCalls);
            writer.println("Win Rate: " + String.format("%.2f%%", (double) winningCalls / totalCalls * 100));
            writer.println("Total P&L: ₹" + String.format("%.2f", totalPnL));
            writer.println();
            
            writer.println("DETAILED FIXED TRADE RESULTS:");
            writer.println("=============================");
            for (FixedTradeResult result : tradeResults) {
                writer.printf("%s | %s | P&L: ₹%.2f | Confidence: %.1f%% | %s%n",
                    result.call.callId, result.call.toString(), result.pnl,
                    result.call.finalConfidence, result.isWinner ? "WIN" : "LOSS");
            }
            
            writer.close();
            System.out.println("💾 Detailed fixed report saved: " + fileName);
            
        } catch (IOException e) {
            System.err.println("❌ Error saving report: " + e.getMessage());
        }
    }
    
    // Data classes
    public static class FixedOptionsCall {
        public final String callId;
        public final String index;
        public final String optionType;
        public final int strike;
        public final LocalDateTime timestamp;
        public final double spotPrice;
        public final double entryPremium;
        public final double finalConfidence;
        public final double target1;
        public final double target2;
        public final double stopLoss;
        public final String analysis;
        public final OptionsGreeksCalculator.GreeksAnalysis greeksAnalysis;
        public final ImpliedVolatilityAnalyzer.IVAnalysis ivAnalysis;
        public final EnhancedPECallGenerator.BearishAnalysis bearishAnalysis;
        public final double marketIV;
        
        public FixedOptionsCall(String callId, String index, String optionType, int strike,
                               LocalDateTime timestamp, double spotPrice, double entryPremium,
                               double finalConfidence, double target1, double target2, double stopLoss,
                               String analysis, OptionsGreeksCalculator.GreeksAnalysis greeksAnalysis,
                               ImpliedVolatilityAnalyzer.IVAnalysis ivAnalysis,
                               EnhancedPECallGenerator.BearishAnalysis bearishAnalysis, double marketIV) {
            this.callId = callId;
            this.index = index;
            this.optionType = optionType;
            this.strike = strike;
            this.timestamp = timestamp;
            this.spotPrice = spotPrice;
            this.entryPremium = entryPremium;
            this.finalConfidence = finalConfidence;
            this.target1 = target1;
            this.target2 = target2;
            this.stopLoss = stopLoss;
            this.analysis = analysis;
            this.greeksAnalysis = greeksAnalysis;
            this.ivAnalysis = ivAnalysis;
            this.bearishAnalysis = bearishAnalysis;
            this.marketIV = marketIV;
        }
        
        @Override
        public String toString() {
            return String.format("%s %s %d @ ₹%.2f | %.1f%% confidence | Greeks: %s | IV: %s",
                               index, optionType, strike, entryPremium, finalConfidence,
                               greeksAnalysis.recommendation, ivAnalysis.ivLevel);
        }
    }
    
    public static class FixedTradeResult {
        public final FixedOptionsCall call;
        public final double exitSpotPrice;
        public final double exitPremium;
        public final double pnl;
        public final boolean isWinner;
        public final String exitReason;
        public final OptionsGreeksCalculator.OptionsGreeks exitGreeks;
        public final double confidenceAccuracy;
        
        public FixedTradeResult(FixedOptionsCall call, double exitSpotPrice, double exitPremium,
                               double pnl, boolean isWinner, String exitReason,
                               OptionsGreeksCalculator.OptionsGreeks exitGreeks, double confidenceAccuracy) {
            this.call = call;
            this.exitSpotPrice = exitSpotPrice;
            this.exitPremium = exitPremium;
            this.pnl = pnl;
            this.isWinner = isWinner;
            this.exitReason = exitReason;
            this.exitGreeks = exitGreeks;
            this.confidenceAccuracy = confidenceAccuracy;
        }
    }
    
    public static class MarketDataPoint {
        public final LocalDateTime timestamp;
        public final double price;
        public final long volume;
        
        public MarketDataPoint(LocalDateTime timestamp, double price, long volume) {
            this.timestamp = timestamp;
            this.price = price;
            this.volume = volume;
        }
    }
    
    public static class CallTypePerformance {
        public int total = 0;
        public int wins = 0;
        public double totalPnL = 0.0;
    }
    
    public static void main(String[] args) {
        System.out.println("🔍 HONEST FIXED OPTIONS BACKTESTER");
        System.out.println("==================================");
        
        HonestFixedOptionsBacktester backtester = new HonestFixedOptionsBacktester();
        
        // Execute complete backtesting process
        backtester.loadTodaysMarketData();
        backtester.generateFixedOptionsCalls();
        backtester.executeHonestFixedBacktesting();
        backtester.generateHonestFixedPerformanceReport();
        
        System.out.println("\n✅ Honest FIXED CE/PE options backtesting completed!");
    }
}