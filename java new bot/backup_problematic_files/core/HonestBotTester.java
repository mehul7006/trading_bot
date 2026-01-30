import java.time.*;
import java.util.*;
import java.io.*;

/**
 * Honest Bot Testing System
 * Tests real market performance and accuracy for the current day
 */
public class HonestBotTester {
    private final EnhancedOptionsCallGenerator optionsGen;
    private final RealTimeDataCollector dataCollector;
    private final List<String> watchlist;
    private final Map<String, List<TradeResult>> todayResults;
    private static final String RESULTS_PATH = "bot_accuracy_analysis_2025-10-31.txt";
    
    public HonestBotTester() {
        this.optionsGen = new EnhancedOptionsCallGenerator();
        this.dataCollector = new RealTimeDataCollector();
        this.watchlist = initializeWatchlist();
        this.todayResults = new HashMap<>();
    }
    
    public void runHonestTest() {
        System.out.println("Starting Honest Bot Test for " + LocalDate.now());
        System.out.println("----------------------------------------");
        
        int totalCalls = 0;
        int successfulCalls = 0;
        double totalProfitLoss = 0.0;
        
        // Test each symbol in watchlist
        for (String symbol : watchlist) {
            List<TradeResult> symbolResults = testSymbol(symbol);
            todayResults.put(symbol, symbolResults);
            
            // Aggregate results
            for (TradeResult result : symbolResults) {
                totalCalls++;
                if (result.successful) {
                    successfulCalls++;
                }
                totalProfitLoss += result.profitLoss;
            }
        }
        
        // Calculate final statistics
        double accuracy = (totalCalls > 0) ? 
            (successfulCalls * 100.0 / totalCalls) : 0.0;
        
        // Generate and save detailed report
        generateDetailedReport(totalCalls, successfulCalls, accuracy, totalProfitLoss);
    }
    
    private List<String> initializeWatchlist() {
        // Initialize with major market symbols
        return Arrays.asList(
            "AAPL", "MSFT", "GOOGL", "AMZN", "META",
            "NVDA", "TSLA", "JPM", "V", "WMT",
            "PG", "JNJ", "UNH", "XOM", "BAC"
        );
    }
    
    private List<TradeResult> testSymbol(String symbol) {
        List<TradeResult> results = new ArrayList<>();
        
        // Test both CALL and PUT options
        for (EnhancedOptionsCallGenerator.OptionType type : 
             EnhancedOptionsCallGenerator.OptionType.values()) {
            
            // Generate option signal
            EnhancedOptionsCallGenerator.OptionTradeSignal signal = 
                optionsGen.generateOptionsCall(symbol, type);
            
            if (signal != null) {
                // Verify actual market movement
                TradeResult result = verifyTradeResult(signal);
                results.add(result);
                
                // Print real-time result
                System.out.println(result);
            }
        }
        
        return results;
    }
    
    private TradeResult verifyTradeResult(
            EnhancedOptionsCallGenerator.OptionTradeSignal signal) {
        
        // Get actual market data for verification
        MarketData currentData = dataCollector.getRealTimeData(signal.symbol);
        
        if (currentData == null || currentData.isMocked()) {
            throw new RuntimeException("Error: Cannot verify with mock data");
        }
        
        // Calculate actual movement
        double actualMove = calculateActualMove(signal, currentData);
        
        // Determine if trade was successful
        boolean successful = isTradeSuccessful(signal, actualMove);
        
        // Calculate profit/loss
        double profitLoss = calculateProfitLoss(signal, actualMove);
        
        return new TradeResult(
            signal.symbol,
            signal.type,
            signal.confidence,
            successful,
            profitLoss,
            LocalDateTime.now()
        );
    }
    
    private double calculateActualMove(
            EnhancedOptionsCallGenerator.OptionTradeSignal signal,
            MarketData currentData) {
        
        double[] priceHistory = currentData.getPriceHistory();
        double initialPrice = priceHistory[0];
        double currentPrice = currentData.getPrice();
        
        return (currentPrice - initialPrice) / initialPrice * 100.0;
    }
    
    private boolean isTradeSuccessful(
            EnhancedOptionsCallGenerator.OptionTradeSignal signal,
            double actualMove) {
        
        return switch(signal.type) {
            case CALL -> actualMove > 0;
            case PUT -> actualMove < 0;
        };
    }
    
    private double calculateProfitLoss(
            EnhancedOptionsCallGenerator.OptionTradeSignal signal,
            double actualMove) {
        
        // Calculate P/L based on actual move and option parameters
        double multiplier = switch(signal.type) {
            case CALL -> actualMove > 0 ? 1.0 : -1.0;
            case PUT -> actualMove < 0 ? 1.0 : -1.0;
        };
        
        return signal.entryPrice * multiplier * Math.abs(actualMove) / 100.0;
    }
    
    private void generateDetailedReport(
            int totalCalls,
            int successfulCalls,
            double accuracy,
            double totalProfitLoss) {
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(RESULTS_PATH))) {
            writer.println("Bot Accuracy Analysis Report");
            writer.println("Date: " + LocalDate.now());
            writer.println("----------------------------------------");
            writer.println();
            
            writer.println("Summary Statistics:");
            writer.printf("Total Calls Generated: %d%n", totalCalls);
            writer.printf("Successful Calls: %d%n", successfulCalls);
            writer.printf("Accuracy Rate: %.2f%%%n", accuracy);
            writer.printf("Total P/L: $%.2f%n", totalProfitLoss);
            writer.println();
            
            writer.println("Detailed Results by Symbol:");
            for (Map.Entry<String, List<TradeResult>> entry : todayResults.entrySet()) {
                writer.println("\nSymbol: " + entry.getKey());
                for (TradeResult result : entry.getValue()) {
                    writer.println(result);
                }
            }
            
            // Print confidence distribution
            writer.println("\nConfidence Distribution:");
            Map<Range, Integer> confidenceDistribution = 
                calculateConfidenceDistribution();
            
            for (Map.Entry<Range, Integer> entry : confidenceDistribution.entrySet()) {
                writer.printf("%s: %d calls%n", 
                    entry.getKey(), entry.getValue());
            }
            
            System.out.println("\nDetailed report saved to: " + RESULTS_PATH);
            
        } catch (IOException e) {
            System.err.println("Error saving report: " + e.getMessage());
        }
    }
    
    private Map<Range, Integer> calculateConfidenceDistribution() {
        Map<Range, Integer> distribution = new TreeMap<>();
        
        // Initialize ranges
        Range[] ranges = {
            new Range(85, 88),
            new Range(88, 91),
            new Range(91, 94),
            new Range(94, 97),
            new Range(97, 100)
        };
        
        for (Range range : ranges) {
            distribution.put(range, 0);
        }
        
        // Count signals in each confidence range
        for (List<TradeResult> results : todayResults.values()) {
            for (TradeResult result : results) {
                for (Range range : ranges) {
                    if (range.contains(result.confidence)) {
                        distribution.merge(range, 1, Integer::sum);
                        break;
                    }
                }
            }
        }
        
        return distribution;
    }
    
    private static class TradeResult {
        final String symbol;
        final EnhancedOptionsCallGenerator.OptionType type;
        final double confidence;
        final boolean successful;
        final double profitLoss;
        final LocalDateTime timestamp;
        
        TradeResult(String symbol,
                   EnhancedOptionsCallGenerator.OptionType type,
                   double confidence,
                   boolean successful,
                   double profitLoss,
                   LocalDateTime timestamp) {
            this.symbol = symbol;
            this.type = type;
            this.confidence = confidence;
            this.successful = successful;
            this.profitLoss = profitLoss;
            this.timestamp = timestamp;
        }
        
        @Override
        public String toString() {
            return String.format(
                "%s %s | Confidence: %.2f%% | %s | P/L: $%.2f | %s",
                symbol, type,
                confidence,
                successful ? "✓ SUCCESS" : "✗ FAILED",
                profitLoss,
                timestamp
            );
        }
    }
    
    private static class Range implements Comparable<Range> {
        final double min;
        final double max;
        
        Range(double min, double max) {
            this.min = min;
            this.max = max;
        }
        
        boolean contains(double value) {
            return value >= min && value < max;
        }
        
        @Override
        public String toString() {
            return String.format("%.0f%%-%.0f%%", min, max);
        }
        
        @Override
        public int compareTo(Range other) {
            return Double.compare(this.min, other.min);
        }
    }
    
    // Main method for running the test
    public static void main(String[] args) {
        HonestBotTester tester = new HonestBotTester();
        tester.runHonestTest();
    }
}