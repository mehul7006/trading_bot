package com.trading.bot.market;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * SIMPLE WEEKLY MARKET DATA FETCHER
 * Fetches last week's market movement data from Yahoo Finance CSV API
 * No external dependencies required - uses built-in Java HTTP client
 */
public class SimpleWeeklyMarketDataFetcher {
    
    // Yahoo Finance CSV API (more reliable than JSON for this use case)
    private static final String YAHOO_CSV_API = "https://query1.finance.yahoo.com/v7/finance/download";
    
    // Symbol mappings for Yahoo Finance
    private static final Map<String, String> YAHOO_SYMBOLS = Map.of(
        "NIFTY", "^NSEI",
        "SENSEX", "^BSESN", 
        "BANKNIFTY", "^NSEBANK",
        "FINNIFTY", "NIFTY_FIN_SERVICE.NS"
    );
    
    private final HttpClient httpClient;
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
    
    public SimpleWeeklyMarketDataFetcher() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        setupOutputFiles();
    }
    
    private void setupOutputFiles() {
        try {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // Create market_data directory if it doesn't exist
            new File("market_data").mkdirs();
            
            dataWriter = new PrintWriter(new FileWriter("market_data/weekly_market_data_" + dateStr + ".csv"));
            analysisWriter = new PrintWriter(new FileWriter("market_data/weekly_market_analysis_" + dateStr + ".csv"));
            
            // Write headers
            dataWriter.println("Symbol,Date,Open,High,Low,Close,Volume,Change,Change%,Source");
            analysisWriter.println("Symbol,WeekStart,WeekEnd,WeekOpen,WeekClose,WeekHigh,WeekLow,WeekChange,WeekChange%,TotalVolume,TradingDays,Movement,Volatility%");
        } catch (IOException e) {
            System.err.println("Error setting up output files: " + e.getMessage());
        }
    }
    
    /**
     * Fetch last week's market data for all major indices
     */
    public Map<String, WeeklyAnalysis> fetchLastWeekData() {
        Map<String, WeeklyAnalysis> weeklyData = new HashMap<>();
        
        // Calculate last week dates (Monday to Friday)
        LocalDate today = LocalDate.now();
        LocalDate lastMonday = today.minusDays(today.getDayOfWeek().getValue() + 6);
        LocalDate lastFriday = lastMonday.plusDays(4);
        
        System.out.println("Fetching market data for week: " + lastMonday + " to " + lastFriday);
        System.out.println("=".repeat(80));
        
        // Fetch data for each index
        for (Map.Entry<String, String> entry : YAHOO_SYMBOLS.entrySet()) {
            String symbol = entry.getKey();
            String yahooSymbol = entry.getValue();
            
            try {
                System.out.println("Fetching data for " + symbol + " (" + yahooSymbol + ")...");
                WeeklyAnalysis analysis = fetchWeeklyDataForSymbol(symbol, yahooSymbol, lastMonday, lastFriday);
                if (analysis != null) {
                    weeklyData.put(symbol, analysis);
                    System.out.println("✓ " + symbol + " data fetched successfully");
                    printWeeklyAnalysis(analysis);
                } else {
                    System.out.println("✗ Failed to fetch " + symbol + " data");
                }
                
                // Add delay to avoid rate limiting
                Thread.sleep(2000);
                
            } catch (Exception e) {
                System.err.println("Error fetching " + symbol + " data: " + e.getMessage());
            }
        }
        
        // Generate comparative analysis
        generateComparativeAnalysis(weeklyData);
        
        closeFiles();
        return weeklyData;
    }
    
    /**
     * Fetch weekly data for a specific symbol from Yahoo Finance CSV API
     */
    private WeeklyAnalysis fetchWeeklyDataForSymbol(String symbol, String yahooSymbol, 
                                                   LocalDate startDate, LocalDate endDate) {
        List<MarketDataPoint> weekData = new ArrayList<>();
        
        try {
            // Convert dates to epoch seconds
            long startEpoch = startDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            long endEpoch = endDate.atTime(23, 59).atZone(ZoneId.systemDefault()).toEpochSecond();
            
            // Build Yahoo Finance CSV URL with proper encoding
            String encodedSymbol = URLEncoder.encode(yahooSymbol, "UTF-8");
            String url = YAHOO_CSV_API + "/" + encodedSymbol +
                        "?period1=" + startEpoch +
                        "&period2=" + endEpoch +
                        "&interval=1d&events=history";
            
            System.out.println("Fetching from: " + url);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/csv")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String csvData = response.body();
                System.out.println("Received CSV data (" + csvData.length() + " characters)");
                
                // Parse CSV data
                String[] lines = csvData.split("\n");
                
                if (lines.length > 1) { // Skip header line
                    for (int i = 1; i < lines.length; i++) {
                        String line = lines[i].trim();
                        if (line.isEmpty()) continue;
                        
                        try {
                            String[] parts = line.split(",");
                            if (parts.length >= 6) {
                                LocalDate date = LocalDate.parse(parts[0]);
                                double open = Double.parseDouble(parts[1]);
                                double high = Double.parseDouble(parts[2]);
                                double low = Double.parseDouble(parts[3]);
                                double close = Double.parseDouble(parts[4]);
                                long volume = parts[5].equals("null") ? 0 : Long.parseLong(parts[5]);
                                
                                MarketDataPoint point = new MarketDataPoint(symbol, date, open, high, low, close, volume, "YAHOO");
                                weekData.add(point);
                                
                                // Write to file
                                if (dataWriter != null) {
                                    dataWriter.println(point.toString());
                                    dataWriter.flush();
                                }
                                
                                System.out.println("  " + date + ": " + open + " -> " + close + " (" + String.format("%.2f%%", ((close-open)/open)*100) + ")");
                            }
                        } catch (Exception e) {
                            System.err.println("Error parsing CSV line: " + line + " - " + e.getMessage());
                        }
                    }
                }
            } else {
                System.err.println("HTTP Error " + response.statusCode() + " for " + symbol);
                System.err.println("Response body: " + response.body());
            }
            
        } catch (Exception e) {
            System.err.println("Error fetching data for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        if (weekData.isEmpty()) {
            return null;
        }
        
        // Sort by date
        weekData.sort((a, b) -> a.date.compareTo(b.date));
        
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
        System.out.println("COMPARATIVE WEEKLY ANALYSIS");
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
        System.out.printf("Market Sentiment: %s%n", 
                         avgChange > 1 ? "BULLISH 🐂" : avgChange < -1 ? "BEARISH 🐻" : "NEUTRAL ➡️");
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
     * Main method to run the weekly market data fetcher
     */
    public static void main(String[] args) {
        System.out.println("SIMPLE WEEKLY MARKET DATA FETCHER - YAHOO FINANCE");
        System.out.println("=".repeat(60));
        System.out.println("Fetching last week's market movement data from Yahoo Finance");
        System.out.println("No external dependencies required!");
        System.out.println("=".repeat(60));
        
        SimpleWeeklyMarketDataFetcher fetcher = new SimpleWeeklyMarketDataFetcher();
        Map<String, WeeklyAnalysis> weeklyData = fetcher.fetchLastWeekData();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("WEEKLY MARKET DATA FETCH COMPLETED");
        System.out.printf("Successfully fetched data for %d indices%n", weeklyData.size());
        System.out.println("Data files generated in market_data/ directory:");
        System.out.println("• weekly_market_data_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        System.out.println("• weekly_market_analysis_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        System.out.println("=".repeat(60));
    }
}