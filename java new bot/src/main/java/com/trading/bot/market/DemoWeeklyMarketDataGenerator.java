package com.trading.bot.market;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DEMO WEEKLY MARKET DATA GENERATOR
 * Generates realistic sample market data based on actual Indian market patterns
 * Shows how the complete solution works with realistic data
 */
public class DemoWeeklyMarketDataGenerator {
    
    // Base values approximating real market levels (as of late 2024)
    private static final Map<String, Double> BASE_VALUES = Map.of(
        "NIFTY", 24500.0,
        "SENSEX", 80000.0,
        "BANKNIFTY", 51000.0,
        "FINNIFTY", 23500.0
    );
    
    // Realistic volatility ranges for each index
    private static final Map<String, Double> VOLATILITY_RANGES = Map.of(
        "NIFTY", 0.015,      // 1.5% daily volatility
        "SENSEX", 0.012,     // 1.2% daily volatility  
        "BANKNIFTY", 0.025,  // 2.5% daily volatility
        "FINNIFTY", 0.020    // 2.0% daily volatility
    );
    
    private final Random random = new Random(System.currentTimeMillis());
    private PrintWriter dataWriter;
    private PrintWriter analysisWriter;
    
    public static class MarketDataPoint {
        public final String symbol;
        public final LocalDate date;
        public final double open;
        public final double high;
        public final double low;
        public final double close;
        public final long volume;
        public final String source;
        
        public MarketDataPoint(String symbol, LocalDate date, double open, double high, 
                             double low, double close, long volume, String source) {
            this.symbol = symbol;
            this.date = date;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
            this.source = source;
        }
        
        public double getChange() {
            return close - open;
        }
        
        public double getChangePercent() {
            return ((close - open) / open) * 100;
        }
        
        @Override
        public String toString() {
            return String.format("%s,%s,%.2f,%.2f,%.2f,%.2f,%d,%.2f,%.2f,%s",
                symbol, date, open, high, low, close, volume, 
                getChange(), getChangePercent(), source);
        }
    }
    
    public static class WeeklyAnalysis {
        public final String symbol;
        public final LocalDate weekStart;
        public final LocalDate weekEnd;
        public final double weekOpen;
        public final double weekClose;
        public final double weekHigh;
        public final double weekLow;
        public final double weekChange;
        public final double weekChangePercent;
        public final long totalVolume;
        public final int tradingDays;
        public final List<MarketDataPoint> dailyData;
        
        public WeeklyAnalysis(String symbol, LocalDate weekStart, LocalDate weekEnd,
                            double weekOpen, double weekClose, double weekHigh, double weekLow,
                            long totalVolume, List<MarketDataPoint> dailyData) {
            this.symbol = symbol;
            this.weekStart = weekStart;
            this.weekEnd = weekEnd;
            this.weekOpen = weekOpen;
            this.weekClose = weekClose;
            this.weekHigh = weekHigh;
            this.weekLow = weekLow;
            this.weekChange = weekClose - weekOpen;
            this.weekChangePercent = ((weekClose - weekOpen) / weekOpen) * 100;
            this.totalVolume = totalVolume;
            this.tradingDays = dailyData.size();
            this.dailyData = new ArrayList<>(dailyData);
        }
        
        public String getMovementDirection() {
            if (weekChangePercent > 2.0) return "STRONG_BULLISH";
            else if (weekChangePercent > 0.5) return "BULLISH";
            else if (weekChangePercent > -0.5) return "SIDEWAYS";
            else if (weekChangePercent > -2.0) return "BEARISH";
            else return "STRONG_BEARISH";
        }
        
        public double getVolatility() {
            return ((weekHigh - weekLow) / weekOpen) * 100;
        }
    }
    
    public DemoWeeklyMarketDataGenerator() {
        setupOutputFiles();
    }
    
    private void setupOutputFiles() {
        try {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // Create market_data directory if it doesn't exist
            new File("market_data").mkdirs();
            
            dataWriter = new PrintWriter(new FileWriter("market_data/demo_weekly_market_data_" + dateStr + ".csv"));
            analysisWriter = new PrintWriter(new FileWriter("market_data/demo_weekly_market_analysis_" + dateStr + ".csv"));
            
            // Write headers
            dataWriter.println("Symbol,Date,Open,High,Low,Close,Volume,Change,Change%,Source");
            analysisWriter.println("Symbol,WeekStart,WeekEnd,WeekOpen,WeekClose,WeekHigh,WeekLow,WeekChange,WeekChange%,TotalVolume,TradingDays,Movement,Volatility%");
        } catch (IOException e) {
            System.err.println("Error setting up output files: " + e.getMessage());
        }
    }
    
    /**
     * Generate realistic weekly market data
     */
    public Map<String, WeeklyAnalysis> generateWeeklyData() {
        Map<String, WeeklyAnalysis> weeklyData = new HashMap<>();
        
        // Calculate last week dates (Monday to Friday)
        LocalDate today = LocalDate.now();
        LocalDate lastMonday = today.minusDays(today.getDayOfWeek().getValue() + 6);
        LocalDate lastFriday = lastMonday.plusDays(4);
        
        System.out.println("DEMO: Generating realistic market data for week: " + lastMonday + " to " + lastFriday);
        System.out.println("=".repeat(80));
        
        // Generate market scenario (bullish, bearish, or sideways week)
        double marketSentiment = (random.nextGaussian() * 0.5); // -1.5% to +1.5% weekly bias
        String marketTrend = marketSentiment > 0.5 ? "BULLISH" : marketSentiment < -0.5 ? "BEARISH" : "SIDEWAYS";
        
        System.out.println("📊 Generated Market Scenario: " + marketTrend + " week");
        System.out.println("📈 Market Bias: " + String.format("%.2f%%", marketSentiment));
        System.out.println();
        
        // Generate data for each index
        for (Map.Entry<String, Double> entry : BASE_VALUES.entrySet()) {
            String symbol = entry.getKey();
            double baseValue = entry.getValue();
            
            try {
                System.out.println("Generating data for " + symbol + "...");
                WeeklyAnalysis analysis = generateWeeklyDataForSymbol(symbol, baseValue, lastMonday, lastFriday, marketSentiment);
                if (analysis != null) {
                    weeklyData.put(symbol, analysis);
                    System.out.println("✓ " + symbol + " data generated successfully");
                    printWeeklyAnalysis(analysis);
                } else {
                    System.out.println("✗ Failed to generate " + symbol + " data");
                }
            } catch (Exception e) {
                System.err.println("Error generating " + symbol + " data: " + e.getMessage());
            }
        }
        
        // Generate comparative analysis
        generateComparativeAnalysis(weeklyData);
        
        closeFiles();
        return weeklyData;
    }
    
    /**
     * Generate realistic weekly data for a specific symbol
     */
    private WeeklyAnalysis generateWeeklyDataForSymbol(String symbol, double baseValue, 
                                                     LocalDate startDate, LocalDate endDate, double marketBias) {
        List<MarketDataPoint> weekData = new ArrayList<>();
        double volatility = VOLATILITY_RANGES.get(symbol);
        
        // Start with base value adjusted for market conditions
        double currentPrice = baseValue * (1.0 + (random.nextGaussian() * 0.02)); // ±2% random start
        
        // Generate daily data for the week
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            // Skip weekends
            if (currentDate.getDayOfWeek() != DayOfWeek.SATURDAY && 
                currentDate.getDayOfWeek() != DayOfWeek.SUNDAY) {
                
                // Calculate daily movement with market bias
                double dailyBias = marketBias * 0.2; // 20% of weekly bias per day
                double randomMovement = random.nextGaussian() * volatility;
                double totalMovement = dailyBias + randomMovement;
                
                // Calculate OHLC data
                double open = currentPrice;
                double change = open * totalMovement;
                double close = open + change;
                
                // Generate intraday high/low with realistic spreads
                double intradayVolatility = volatility * 0.6; // Intraday range
                double maxMove = open * intradayVolatility;
                
                double high = Math.max(open, close) + (random.nextDouble() * maxMove);
                double low = Math.min(open, close) - (random.nextDouble() * maxMove);
                
                // Ensure logical OHLC relationships
                high = Math.max(high, Math.max(open, close));
                low = Math.min(low, Math.min(open, close));
                
                // Generate realistic volume (varies by index)
                long volume = generateRealisticVolume(symbol);
                
                MarketDataPoint point = new MarketDataPoint(symbol, currentDate, open, high, low, close, volume, "DEMO");
                weekData.add(point);
                
                // Write to file
                if (dataWriter != null) {
                    dataWriter.println(point.toString());
                    dataWriter.flush();
                }
                
                System.out.println("  " + currentDate + ": " + String.format("%.2f", open) + 
                                 " -> " + String.format("%.2f", close) + 
                                 " (" + String.format("%+.2f%%", ((close-open)/open)*100) + ")");
                
                // Update current price for next day
                currentPrice = close;
            }
            currentDate = currentDate.plusDays(1);
        }
        
        if (weekData.isEmpty()) {
            return null;
        }
        
        // Calculate weekly statistics
        double weekOpen = weekData.get(0).open;
        double weekClose = weekData.get(weekData.size() - 1).close;
        double weekHigh = weekData.stream().mapToDouble(d -> d.high).max().orElse(0);
        double weekLow = weekData.stream().mapToDouble(d -> d.low).min().orElse(0);
        long totalVolume = weekData.stream().mapToLong(d -> d.volume).sum();
        
        return new WeeklyAnalysis(symbol, startDate, endDate, weekOpen, weekClose, 
                                weekHigh, weekLow, totalVolume, weekData);
    }
    
    /**
     * Generate realistic volume based on index type
     */
    private long generateRealisticVolume(String symbol) {
        Map<String, Long> baseVolumes = Map.of(
            "NIFTY", 500000000L,      // 50 crore
            "SENSEX", 300000000L,     // 30 crore
            "BANKNIFTY", 800000000L,  // 80 crore
            "FINNIFTY", 200000000L    // 20 crore
        );
        
        long baseVolume = baseVolumes.get(symbol);
        double randomFactor = 0.5 + (random.nextDouble() * 1.0); // 0.5x to 1.5x variation
        return (long)(baseVolume * randomFactor);
    }
    
    /**
     * Print weekly analysis for a symbol
     */
    private void printWeeklyAnalysis(WeeklyAnalysis analysis) {
        System.out.println("-".repeat(60));
        System.out.printf("WEEKLY ANALYSIS: %s (%s to %s)%n", 
                         analysis.symbol, analysis.weekStart, analysis.weekEnd);
        System.out.println("-".repeat(60));
        System.out.printf("Week Open:     %.2f%n", analysis.weekOpen);
        System.out.printf("Week Close:    %.2f%n", analysis.weekClose);
        System.out.printf("Week High:     %.2f%n", analysis.weekHigh);
        System.out.printf("Week Low:      %.2f%n", analysis.weekLow);
        System.out.printf("Week Change:   %.2f (%.2f%%)%n", analysis.weekChange, analysis.weekChangePercent);
        System.out.printf("Movement:      %s%n", analysis.getMovementDirection());
        System.out.printf("Volatility:    %.2f%%%n", analysis.getVolatility());
        System.out.printf("Trading Days:  %d%n", analysis.tradingDays);
        System.out.printf("Total Volume:  %,d%n", analysis.totalVolume);
        System.out.println();
        
        // Write to analysis file
        if (analysisWriter != null) {
            analysisWriter.printf("%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%s,%.2f%n",
                analysis.symbol, analysis.weekStart, analysis.weekEnd,
                analysis.weekOpen, analysis.weekClose, analysis.weekHigh, analysis.weekLow,
                analysis.weekChange, analysis.weekChangePercent, analysis.totalVolume,
                analysis.tradingDays, analysis.getMovementDirection(), analysis.getVolatility());
            analysisWriter.flush();
        }
    }
    
    /**
     * Generate comparative analysis across all indices
     */
    private void generateComparativeAnalysis(Map<String, WeeklyAnalysis> weeklyData) {
        System.out.println("=".repeat(80));
        System.out.println("COMPARATIVE WEEKLY ANALYSIS - DEMO RESULTS");
        System.out.println("=".repeat(80));
        
        System.out.printf("%-12s %-10s %-10s %-12s %-15s %-12s%n",
                         "INDEX", "CHANGE%", "MOVEMENT", "VOLATILITY%", "TRADING_DAYS", "PERFORMANCE");
        System.out.println("-".repeat(80));
        
        weeklyData.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue().weekChangePercent, a.getValue().weekChangePercent))
            .forEach(entry -> {
                WeeklyAnalysis analysis = entry.getValue();
                String performance = analysis.weekChangePercent > 0 ? "OUTPERFORMED" : "UNDERPERFORMED";
                
                System.out.printf("%-12s %-10.2f %-10s %-12.2f %-15d %-12s%n",
                    analysis.symbol,
                    analysis.weekChangePercent,
                    analysis.getMovementDirection(),
                    analysis.getVolatility(),
                    analysis.tradingDays,
                    performance
                );
            });
        
        // Calculate market overview
        double avgChange = weeklyData.values().stream()
            .mapToDouble(a -> a.weekChangePercent)
            .average().orElse(0);
        
        long positiveCount = weeklyData.values().stream()
            .mapToLong(a -> a.weekChangePercent > 0 ? 1 : 0)
            .sum();
        
        System.out.println("-".repeat(80));
        System.out.printf("MARKET OVERVIEW:%n");
        System.out.printf("Average Change: %.2f%%%n", avgChange);
        System.out.printf("Positive Indices: %d/%d%n", positiveCount, weeklyData.size());
        
        String sentiment;
        String emoji;
        if (avgChange > 1) {
            sentiment = "BULLISH";
            emoji = "🐂";
        } else if (avgChange < -1) {
            sentiment = "BEARISH"; 
            emoji = "🐻";
        } else {
            sentiment = "NEUTRAL";
            emoji = "➡️";
        }
        
        System.out.printf("Market Sentiment: %s %s%n", sentiment, emoji);
        
        // Additional insights
        System.out.println("-".repeat(80));
        System.out.println("📊 DEMO INSIGHTS:");
        
        if (avgChange > 2) {
            System.out.println("🚀 Strong bullish momentum - breakout week!");
        } else if (avgChange > 0.5) {
            System.out.println("📈 Positive momentum building");
        } else if (avgChange < -2) {
            System.out.println("📉 Significant selling pressure observed");
        } else if (avgChange < -0.5) {
            System.out.println("⚠️ Bearish sentiment emerging");
        } else {
            System.out.println("😴 Consolidation phase - waiting for direction");
        }
        
        // Volatility insights
        double avgVolatility = weeklyData.values().stream()
            .mapToDouble(WeeklyAnalysis::getVolatility)
            .average().orElse(0);
        
        if (avgVolatility > 4) {
            System.out.println("⚡ High volatility week - significant market events");
        } else if (avgVolatility > 2) {
            System.out.println("📊 Normal market volatility");
        } else {
            System.out.println("😴 Low volatility - calm market conditions");
        }
        
        System.out.println("=".repeat(80));
    }
    
    private void closeFiles() {
        if (dataWriter != null) {
            dataWriter.close();
        }
        if (analysisWriter != null) {
            analysisWriter.close();
        }
    }
    
    /**
     * Main method to run the demo data generator
     */
    public static void main(String[] args) {
        System.out.println("DEMO WEEKLY MARKET DATA GENERATOR");
        System.out.println("=".repeat(50));
        System.out.println("Generating realistic sample market data based on actual patterns");
        System.out.println("This demonstrates how the complete solution works!");
        System.out.println("=".repeat(50));
        
        DemoWeeklyMarketDataGenerator generator = new DemoWeeklyMarketDataGenerator();
        Map<String, WeeklyAnalysis> weeklyData = generator.generateWeeklyData();
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("✅ DEMO DATA GENERATION COMPLETED");
        System.out.printf("Generated realistic data for %d indices%n", weeklyData.size());
        System.out.println("Demo files created in market_data/ directory:");
        System.out.println("• demo_weekly_market_data_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        System.out.println("• demo_weekly_market_analysis_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        System.out.println();
        System.out.println("🎯 This demonstrates the complete workflow:");
        System.out.println("1. Data Collection (simulated realistic market movements)");
        System.out.println("2. Analysis Generation (performance, volatility, trends)");
        System.out.println("3. Comparative Analysis (cross-index comparison)");
        System.out.println("4. Market Insights (sentiment and predictions)");
        System.out.println("5. File Output (CSV format for further analysis)");
        System.out.println("=".repeat(50));
    }
}