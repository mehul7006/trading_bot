package com.trading.bot.market;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
// Using simple JSON parsing without external dependencies

/**
 * WEEKLY MARKET DATA FETCHER
 * Fetches last week's market movement data from official sources:
 * - NSE India (National Stock Exchange)
 * - BSE India (Bombay Stock Exchange) 
 * - RBI (Reserve Bank of India)
 * - Yahoo Finance (as backup)
 * 
 * Supports major indices: NIFTY 50, SENSEX, BANK NIFTY, FIN NIFTY, etc.
 */
public class WeeklyMarketDataFetcher {
    
    // Official API endpoints
    private static final String NSE_BASE_URL = "https://www.nseindia.com/api";
    private static final String BSE_BASE_URL = "https://api.bseindia.com/BseIndiaAPI/api";
    private static final String YAHOO_FINANCE_API = "https://query1.finance.yahoo.com/v8/finance/chart";
    private static final String NSE_EQUITY_HISTORICAL = NSE_BASE_URL + "/historical/cm/equity";
    private static final String NSE_INDEX_HISTORICAL = NSE_BASE_URL + "/historical/indicesHistory";
    private static final String BSE_INDEX_HISTORICAL = BSE_BASE_URL + "/IndexValueHistory/w";
    
    // Index symbols mapping
    private static final Map<String, String> NSE_SYMBOLS = Map.of(
        "NIFTY", "NIFTY 50",
        "BANKNIFTY", "NIFTY BANK",
        "FINNIFTY", "NIFTY FIN SERVICE",
        "MIDCAPNIFTY", "NIFTY MIDCAP 100",
        "SMALLCAPNIFTY", "NIFTY SMALLCAP 100"
    );
    
    private static final Map<String, String> BSE_SYMBOLS = Map.of(
        "SENSEX", "1",
        "BANKEX", "12"
    );
    
    private static final Map<String, String> YAHOO_SYMBOLS = Map.of(
        "NIFTY", "^NSEI",
        "SENSEX", "^BSESN",
        "BANKNIFTY", "^NSEBANK"
    );
    
    private final HttpClient httpClient;
    private PrintWriter dataWriter;
    private PrintWriter analysisWriter;
    
    public static class MarketDataPoint {
        public final String symbol;
        public final LocalDate date;
        public final LocalTime time;
        public final double open;
        public final double high;
        public final double low;
        public final double close;
        public final long volume;
        public final String source;
        
        public MarketDataPoint(String symbol, LocalDate date, LocalTime time, 
                             double open, double high, double low, double close, 
                             long volume, String source) {
            this.symbol = symbol;
            this.date = date;
            this.time = time;
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
            return String.format("%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%d,%.2f,%.2f,%s",
                symbol, date, time, open, high, low, close, volume, 
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
    
    public WeeklyMarketDataFetcher() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        // Simple JSON parsing implementation
        setupOutputFiles();
    }
    
    private void setupOutputFiles() {
        try {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            dataWriter = new PrintWriter(new FileWriter("weekly_market_data_" + dateStr + ".csv"));
            analysisWriter = new PrintWriter(new FileWriter("weekly_market_analysis_" + dateStr + ".csv"));
            
            // Write headers
            dataWriter.println("Symbol,Date,Time,Open,High,Low,Close,Volume,Change,Change%,Source");
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
        for (String symbol : Arrays.asList("NIFTY", "SENSEX", "BANKNIFTY", "FINNIFTY")) {
            try {
                System.out.println("Fetching data for " + symbol + "...");
                WeeklyAnalysis analysis = fetchWeeklyDataForSymbol(symbol, lastMonday, lastFriday);
                if (analysis != null) {
                    weeklyData.put(symbol, analysis);
                    System.out.println("✓ " + symbol + " data fetched successfully");
                    printWeeklyAnalysis(analysis);
                } else {
                    System.out.println("✗ Failed to fetch " + symbol + " data");
                }
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
     * Fetch weekly data for a specific symbol
     */
    private WeeklyAnalysis fetchWeeklyDataForSymbol(String symbol, LocalDate startDate, LocalDate endDate) {
        List<MarketDataPoint> weekData = new ArrayList<>();
        
        // Try NSE first
        List<MarketDataPoint> nseData = fetchFromNSE(symbol, startDate, endDate);
        if (!nseData.isEmpty()) {
            weekData.addAll(nseData);
        }
        
        // Try BSE for SENSEX/BANKEX
        if (BSE_SYMBOLS.containsKey(symbol)) {
            List<MarketDataPoint> bseData = fetchFromBSE(symbol, startDate, endDate);
            if (!bseData.isEmpty()) {
                weekData.addAll(bseData);
            }
        }
        
        // Fallback to Yahoo Finance
        if (weekData.isEmpty()) {
            List<MarketDataPoint> yahooData = fetchFromYahoo(symbol, startDate, endDate);
            weekData.addAll(yahooData);
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
     * Fetch data from NSE official API
     */
    private List<MarketDataPoint> fetchFromNSE(String symbol, LocalDate startDate, LocalDate endDate) {
        List<MarketDataPoint> data = new ArrayList<>();
        
        try {
            String nseSymbol = NSE_SYMBOLS.get(symbol);
            if (nseSymbol == null) return data;
            
            String url = NSE_INDEX_HISTORICAL + "?indexType=" + URLEncoder.encode(nseSymbol, "UTF-8") +
                        "&from=" + startDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) +
                        "&to=" + endDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .header("Referer", "https://www.nseindia.com/")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode dataArray = root.get("data");
                
                if (dataArray != null && dataArray.isArray()) {
                    for (JsonNode node : dataArray) {
                        try {
                            String dateStr = node.get("EOD_TIMESTAMP").asText();
                            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
                            
                            double open = node.get("EOD_OPEN_INDEX_VAL").asDouble();
                            double high = node.get("EOD_HIGH_INDEX_VAL").asDouble();
                            double low = node.get("EOD_LOW_INDEX_VAL").asDouble();
                            double close = node.get("EOD_CLOSE_INDEX_VAL").asDouble();
                            
                            MarketDataPoint point = new MarketDataPoint(symbol, date, LocalTime.of(15, 30),
                                                                      open, high, low, close, 0, "NSE");
                            data.add(point);
                            
                            // Write to file
                            if (dataWriter != null) {
                                dataWriter.println(point.toString());
                            }
                            
                        } catch (Exception e) {
                            System.err.println("Error parsing NSE data point: " + e.getMessage());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error fetching NSE data for " + symbol + ": " + e.getMessage());
        }
        
        return data;
    }
    
    /**
     * Fetch data from BSE official API
     */
    private List<MarketDataPoint> fetchFromBSE(String symbol, LocalDate startDate, LocalDate endDate) {
        List<MarketDataPoint> data = new ArrayList<>();
        
        try {
            String bseCode = BSE_SYMBOLS.get(symbol);
            if (bseCode == null) return data;
            
            String url = BSE_INDEX_HISTORICAL + "?IndexId=" + bseCode +
                        "&FromDate=" + startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) +
                        "&ToDate=" + endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) +
                        "&SeriesId=";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode dataArray = root.get("Table");
                
                if (dataArray != null && dataArray.isArray()) {
                    for (JsonNode node : dataArray) {
                        try {
                            String dateStr = node.get("IndexDate").asText();
                            LocalDate date = LocalDate.parse(dateStr.substring(0, 10));
                            
                            double open = node.get("IndexValue").asDouble(); // BSE typically provides close value
                            double close = node.get("IndexValue").asDouble();
                            
                            MarketDataPoint point = new MarketDataPoint(symbol, date, LocalTime.of(15, 30),
                                                                      open, close, close, close, 0, "BSE");
                            data.add(point);
                            
                            // Write to file
                            if (dataWriter != null) {
                                dataWriter.println(point.toString());
                            }
                            
                        } catch (Exception e) {
                            System.err.println("Error parsing BSE data point: " + e.getMessage());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error fetching BSE data for " + symbol + ": " + e.getMessage());
        }
        
        return data;
    }
    
    /**
     * Fetch data from Yahoo Finance as fallback
     */
    private List<MarketDataPoint> fetchFromYahoo(String symbol, LocalDate startDate, LocalDate endDate) {
        List<MarketDataPoint> data = new ArrayList<>();
        
        try {
            String yahooSymbol = YAHOO_SYMBOLS.get(symbol);
            if (yahooSymbol == null) return data;
            
            long startEpoch = startDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            long endEpoch = endDate.atTime(23, 59).atZone(ZoneId.systemDefault()).toEpochSecond();
            
            String url = YAHOO_FINANCE_API + "/" + yahooSymbol +
                        "?period1=" + startEpoch +
                        "&period2=" + endEpoch +
                        "&interval=1d";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode chart = root.get("chart");
                
                if (chart != null && chart.isArray() && chart.size() > 0) {
                    JsonNode result = chart.get(0);
                    JsonNode timestamps = result.get("timestamp");
                    JsonNode indicators = result.get("indicators");
                    
                    if (timestamps != null && indicators != null) {
                        JsonNode quote = indicators.get("quote").get(0);
                        JsonNode opens = quote.get("open");
                        JsonNode highs = quote.get("high");
                        JsonNode lows = quote.get("low");
                        JsonNode closes = quote.get("close");
                        JsonNode volumes = quote.get("volume");
                        
                        for (int i = 0; i < timestamps.size(); i++) {
                            try {
                                long timestamp = timestamps.get(i).asLong();
                                LocalDate date = Instant.ofEpochSecond(timestamp)
                                                .atZone(ZoneId.systemDefault()).toLocalDate();
                                
                                double open = opens.get(i).asDouble();
                                double high = highs.get(i).asDouble();
                                double low = lows.get(i).asDouble();
                                double close = closes.get(i).asDouble();
                                long volume = volumes.get(i).asLong();
                                
                                MarketDataPoint point = new MarketDataPoint(symbol, date, LocalTime.of(15, 30),
                                                                          open, high, low, close, volume, "YAHOO");
                                data.add(point);
                                
                                // Write to file
                                if (dataWriter != null) {
                                    dataWriter.println(point.toString());
                                }
                                
                            } catch (Exception e) {
                                System.err.println("Error parsing Yahoo data point: " + e.getMessage());
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error fetching Yahoo data for " + symbol + ": " + e.getMessage());
        }
        
        return data;
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
                         avgChange > 1 ? "BULLISH" : avgChange < -1 ? "BEARISH" : "NEUTRAL");
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
        System.out.println("WEEKLY MARKET DATA FETCHER - OFFICIAL SOURCES");
        System.out.println("=".repeat(50));
        System.out.println("Fetching last week's market movement data from:");
        System.out.println("• NSE India (National Stock Exchange)");
        System.out.println("• BSE India (Bombay Stock Exchange)");
        System.out.println("• Yahoo Finance (Backup)");
        System.out.println("=".repeat(50));
        
        WeeklyMarketDataFetcher fetcher = new WeeklyMarketDataFetcher();
        Map<String, WeeklyAnalysis> weeklyData = fetcher.fetchLastWeekData();
        
        System.out.println("\n" + "=".repeat(50));
        System.out.println("WEEKLY MARKET DATA FETCH COMPLETED");
        System.out.printf("Successfully fetched data for %d indices%n", weeklyData.size());
        System.out.println("Data files generated:");
        System.out.println("• weekly_market_data_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        System.out.println("• weekly_market_analysis_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv");
        System.out.println("=".repeat(50));
    }
}