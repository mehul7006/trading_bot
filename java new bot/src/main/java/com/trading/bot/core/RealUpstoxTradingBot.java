package com.trading.bot.core;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * REAL UPSTOX TRADING BOT - ONLY REAL DATA
 * FIXES ALL CRITICAL FAILURES:
 * ✅ Remove Yahoo Finance completely
 * ✅ Use ONLY Upstox API with real authentication
 * ✅ NO simulation fallback - fail if no real data
 * ✅ Work only with real market data
 * ✅ Stop overselling capabilities
 */
public class RealUpstoxTradingBot {
    
    private final HttpClient httpClient;
    private final List<RealTradingCall> paperTrades = new ArrayList<>();
    private boolean isRunning = false;
    
    // Upstox API Configuration
    private static final String UPSTOX_BASE_URL = "https://api.upstox.com/v2";
    private String accessToken = null; // Will be set via authentication
    
    // Real market data cache
    private double lastNiftyPrice = 0;
    private double lastSensexPrice = 0;
    private LocalDateTime lastDataUpdate = null;
    
    // Technical indicators
    private final List<Double> niftyPriceHistory = new ArrayList<>();
    private final List<Double> sensexPriceHistory = new ArrayList<>();
    private static final int HISTORY_SIZE = 50;
    
    // Upstox instrument keys for NSE indices
    private static final String NIFTY_INSTRUMENT_KEY = "NSE_INDEX|Nifty 50";
    private static final String SENSEX_INSTRUMENT_KEY = "BSE_INDEX|SENSEX";
    
    public RealUpstoxTradingBot() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
        
        System.out.println("🚀 REAL UPSTOX TRADING BOT INITIALIZED");
        System.out.println("✅ ONLY Upstox real market data");
        System.out.println("❌ NO simulation fallback");
        System.out.println("❌ NO Yahoo Finance");
        System.out.println("🎯 Real data or fail completely");
    }
    
    /**
     * STEP 1: Authenticate with Upstox API
     */
    public boolean authenticateUpstox(String apiKey, String apiSecret, String redirectUri) {
        try {
            System.out.println("🔐 Authenticating with Upstox API...");
            
            // Step 1: Get login URL for user authorization
            String loginUrl = String.format("%s/login/authorization/api/v2/openapi/authorize?response_type=code&client_id=%s&redirect_uri=%s",
                UPSTOX_BASE_URL, apiKey, URLEncoder.encode(redirectUri, "UTF-8"));
            
            System.out.println("📱 Please visit this URL to authorize:");
            System.out.println(loginUrl);
            System.out.println("📝 Enter the authorization code from redirect URL:");
            
            // Read authorization code from user input
            Scanner scanner = new Scanner(System.in);
            String authCode = scanner.nextLine().trim();
            
            // Step 2: Exchange authorization code for access token
            return exchangeCodeForToken(apiKey, apiSecret, authCode, redirectUri);
            
        } catch (Exception e) {
            System.err.println("❌ Upstox authentication failed: " + e.getMessage());
            return false;
        }
    }
    
    private boolean exchangeCodeForToken(String apiKey, String apiSecret, String authCode, String redirectUri) {
        try {
            String tokenUrl = UPSTOX_BASE_URL + "/login/authorization/api/v2/openapi/token";
            
            String requestBody = String.format(
                "code=%s&client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code",
                URLEncoder.encode(authCode, "UTF-8"),
                URLEncoder.encode(apiKey, "UTF-8"),
                URLEncoder.encode(apiSecret, "UTF-8"),
                URLEncoder.encode(redirectUri, "UTF-8")
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                this.accessToken = extractAccessToken(responseBody);
                
                if (this.accessToken != null) {
                    System.out.println("✅ Upstox authentication successful");
                    System.out.println("🔑 Access token obtained");
                    return true;
                } else {
                    System.err.println("❌ Failed to extract access token from response");
                    return false;
                }
            } else {
                System.err.println("❌ Token exchange failed. Status: " + response.statusCode());
                System.err.println("Response: " + response.body());
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Token exchange error: " + e.getMessage());
            return false;
        }
    }
    
    private String extractAccessToken(String jsonResponse) {
        try {
            String pattern = "\"access_token\":\"";
            int start = jsonResponse.indexOf(pattern);
            if (start != -1) {
                start += pattern.length();
                int end = jsonResponse.indexOf('"', start);
                if (end != -1) {
                    return jsonResponse.substring(start, end);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting access token: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * STEP 2: ONLY REAL DATA - NO SIMULATION FALLBACK
     */
    public boolean fetchRealMarketData() {
        if (accessToken == null) {
            System.err.println("❌ Not authenticated with Upstox. Cannot fetch real data.");
            return false;
        }
        
        try {
            double niftyPrice = getRealUpstoxPrice(NIFTY_INSTRUMENT_KEY, "NIFTY");
            double sensexPrice = getRealUpstoxPrice(SENSEX_INSTRUMENT_KEY, "SENSEX");
            
            if (niftyPrice > 0 && sensexPrice > 0) {
                updatePriceHistory(niftyPrice, sensexPrice);
                lastNiftyPrice = niftyPrice;
                lastSensexPrice = sensexPrice;
                lastDataUpdate = LocalDateTime.now();
                
                System.out.printf("📊 REAL UPSTOX DATA: NIFTY=%.2f, SENSEX=%.2f [%s]%n", 
                    niftyPrice, sensexPrice, lastDataUpdate.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                
                return true;
            } else {
                System.err.println("❌ Failed to get valid prices from Upstox");
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Real Upstox data fetch failed: " + e.getMessage());
            return false;
        }
    }
    
    private double getRealUpstoxPrice(String instrumentKey, String symbolName) throws Exception {
        try {
            String url = String.format("%s/market-quote/ltp?instrument_key=%s", 
                UPSTOX_BASE_URL, URLEncoder.encode(instrumentKey, "UTF-8"));
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                double price = parseUpstoxPrice(responseBody);
                
                if (price > 0) {
                    System.out.printf("✅ REAL %s: ₹%.2f (Upstox Live)%n", symbolName, price);
                    return price;
                } else {
                    System.err.printf("❌ Invalid price received for %s%n", symbolName);
                    return 0;
                }
            } else if (response.statusCode() == 401) {
                System.err.println("❌ Upstox authentication expired. Please re-authenticate.");
                this.accessToken = null;
                return 0;
            } else {
                System.err.printf("❌ Upstox API error for %s. Status: %d%n", symbolName, response.statusCode());
                System.err.println("Response: " + response.body());
                return 0;
            }
            
        } catch (Exception e) {
            System.err.printf("❌ Exception fetching %s price: %s%n", symbolName, e.getMessage());
            throw e;
        }
    }
    
    private double parseUpstoxPrice(String jsonResponse) {
        try {
            // Parse Upstox LTP response
            // Expected format: {"status":"success","data":{"NSE_INDEX|Nifty 50":{"last_price":24567.89}}}
            
            String pattern = "\"last_price\":";
            int start = jsonResponse.indexOf(pattern);
            if (start != -1) {
                start += pattern.length();
                int end = findJsonValueEnd(jsonResponse, start);
                String priceStr = jsonResponse.substring(start, end).trim();
                
                double price = Double.parseDouble(priceStr);
                
                // Sanity check for reasonable price ranges
                if (price > 1000 && price < 100000) {
                    return price;
                } else {
                    System.err.println("❌ Price outside reasonable range: " + price);
                    return 0;
                }
            } else {
                System.err.println("❌ Could not find last_price in response: " + jsonResponse);
                return 0;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error parsing Upstox price: " + e.getMessage());
            return 0;
        }
    }
    
    private int findJsonValueEnd(String json, int start) {
        int comma = json.indexOf(',', start);
        int brace = json.indexOf('}', start);
        int bracket = json.indexOf(']', start);
        
        int end = json.length();
        if (comma != -1) end = Math.min(end, comma);
        if (brace != -1) end = Math.min(end, brace);
        if (bracket != -1) end = Math.min(end, bracket);
        
        return end;
    }
    
    /**
     * REAL TECHNICAL ANALYSIS - NO CHANGES (THIS PART WAS GOOD)
     */
    private void updatePriceHistory(double niftyPrice, double sensexPrice) {
        niftyPriceHistory.add(niftyPrice);
        sensexPriceHistory.add(sensexPrice);
        
        // Keep only recent history
        if (niftyPriceHistory.size() > HISTORY_SIZE) {
            niftyPriceHistory.remove(0);
            sensexPriceHistory.remove(0);
        }
    }
    
    public RealTradingCall generateRealTradingSignal() {
        if (niftyPriceHistory.size() < 20) {
            System.out.println("⏳ Need more real price history for analysis (have " + niftyPriceHistory.size() + "/20)");
            return null;
        }
        
        // Real technical analysis - NO RANDOM NUMBERS!
        TechnicalAnalysis niftyAnalysis = performTechnicalAnalysis("NIFTY", niftyPriceHistory);
        TechnicalAnalysis sensexAnalysis = performTechnicalAnalysis("SENSEX", sensexPriceHistory);
        
        // Check for real trading opportunities
        if (niftyAnalysis.signalStrength > 65) {
            return createPaperTrade("NIFTY", niftyAnalysis, lastNiftyPrice);
        }
        
        if (sensexAnalysis.signalStrength > 65) {
            return createPaperTrade("SENSEX", sensexAnalysis, lastSensexPrice);
        }
        
        return null; // No strong signal
    }
    
    private TechnicalAnalysis performTechnicalAnalysis(String symbol, List<Double> prices) {
        if (prices.size() < 20) {
            return new TechnicalAnalysis("NEUTRAL", 50, "Insufficient real data");
        }
        
        // Calculate real technical indicators
        double sma5 = calculateSMA(prices, 5);
        double sma20 = calculateSMA(prices, 20);
        double rsi = calculateRSI(prices, 14);
        double currentPrice = prices.get(prices.size() - 1);
        
        // Determine signal based on real analysis
        String signal = "NEUTRAL";
        double strength = 50;
        String reason = "No clear signal from real data";
        
        // Moving average crossover
        if (sma5 > sma20 && currentPrice > sma5) {
            if (rsi < 70) { // Not overbought
                signal = "BUY";
                strength = 60 + Math.min(10, (sma5 - sma20) / sma20 * 1000); // Max 70
                reason = "Real analysis: SMA5 > SMA20, RSI not overbought";
            }
        } else if (sma5 < sma20 && currentPrice < sma5) {
            if (rsi > 30) { // Not oversold
                signal = "SELL";
                strength = 60 + Math.min(10, (sma20 - sma5) / sma20 * 1000); // Max 70
                reason = "Real analysis: SMA5 < SMA20, RSI not oversold";
            }
        }
        
        // RSI extreme conditions
        if (rsi < 30 && signal.equals("NEUTRAL")) {
            signal = "BUY";
            strength = 55 + (30 - rsi); // Higher strength for more oversold
            reason = "Real oversold RSI condition";
        } else if (rsi > 70 && signal.equals("NEUTRAL")) {
            signal = "SELL";
            strength = 55 + (rsi - 70); // Higher strength for more overbought
            reason = "Real overbought RSI condition";
        }
        
        return new TechnicalAnalysis(signal, strength, reason);
    }
    
    private double calculateSMA(List<Double> prices, int period) {
        if (prices.size() < period) return prices.get(prices.size() - 1);
        
        return prices.subList(prices.size() - period, prices.size())
            .stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
    
    private double calculateRSI(List<Double> prices, int period) {
        if (prices.size() < period + 1) return 50.0;
        
        double gainSum = 0;
        double lossSum = 0;
        
        for (int i = prices.size() - period; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                gainSum += change;
            } else {
                lossSum += Math.abs(change);
            }
        }
        
        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;
        
        if (avgLoss == 0) return 100.0;
        
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
    
    /**
     * PAPER TRADING WITH REAL DATA ONLY
     */
    private RealTradingCall createPaperTrade(String symbol, TechnicalAnalysis analysis, double price) {
        RealTradingCall trade = new RealTradingCall(
            symbol, analysis.signal, price, 
            analysis.signalStrength, analysis.reason,
            LocalDateTime.now()
        );
        
        paperTrades.add(trade);
        logPaperTrade(trade);
        
        System.out.printf("📞 REAL PAPER TRADE: %s %s at %.2f (Confidence: %.1f%%) - %s%n",
            trade.symbol, trade.direction, trade.entryPrice, trade.confidence, trade.reason);
        
        return trade;
    }
    
    private void logPaperTrade(RealTradingCall trade) {
        try {
            FileWriter writer = new FileWriter("real_upstox_paper_trades.log", true);
            writer.write(String.format("%s,%s,%s,%.2f,%.1f,%s%n",
                trade.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                trade.symbol, trade.direction, trade.entryPrice, trade.confidence, trade.reason));
            writer.close();
        } catch (Exception e) {
            System.err.println("Error logging real paper trade: " + e.getMessage());
        }
    }
    
    /**
     * Track paper trade performance (REAL RESULTS ONLY!)
     */
    public void updatePaperTradeResults() {
        for (RealTradingCall trade : paperTrades) {
            if (!trade.isClosed && trade.timestamp.isBefore(LocalDateTime.now().minusMinutes(30))) {
                // Close trade after 30 minutes and calculate real P&L from real prices
                double currentPrice = trade.symbol.equals("NIFTY") ? lastNiftyPrice : lastSensexPrice;
                
                if (currentPrice > 0) { // Only if we have real current price
                    double pnl = calculateRealPnL(trade, currentPrice);
                    
                    trade.closePrice = currentPrice;
                    trade.pnl = pnl;
                    trade.isClosed = true;
                    trade.isProfit = pnl > 0;
                    
                    logPaperTradeResult(trade);
                    
                    System.out.printf("📈 REAL RESULT: %s %s %.2f → %.2f = %.2f points %s%n",
                        trade.symbol, trade.direction, trade.entryPrice, trade.closePrice, trade.pnl,
                        trade.isProfit ? "PROFIT" : "LOSS");
                } else {
                    System.err.println("❌ Cannot close trade - no real current price available");
                }
            }
        }
    }
    
    private double calculateRealPnL(RealTradingCall trade, double currentPrice) {
        if (trade.direction.equals("BUY")) {
            return currentPrice - trade.entryPrice;
        } else {
            return trade.entryPrice - currentPrice;
        }
    }
    
    private void logPaperTradeResult(RealTradingCall trade) {
        try {
            FileWriter writer = new FileWriter("real_upstox_paper_results.log", true);
            writer.write(String.format("%s,%s,%s,%.2f,%.2f,%.2f,%s,%.1f%n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                trade.symbol, trade.direction, trade.entryPrice, trade.closePrice, trade.pnl,
                trade.isProfit ? "PROFIT" : "LOSS", trade.confidence));
            writer.close();
        } catch (Exception e) {
            System.err.println("Error logging real paper result: " + e.getMessage());
        }
    }
    
    /**
     * Get REAL performance metrics - no simulation
     */
    public void printRealPerformance() {
        List<RealTradingCall> closedTrades = paperTrades.stream()
            .filter(t -> t.isClosed).toList();
        
        if (closedTrades.isEmpty()) {
            System.out.println("📊 No completed real paper trades yet");
            return;
        }
        
        long profits = closedTrades.stream().filter(t -> t.isProfit).count();
        double totalPnL = closedTrades.stream().mapToDouble(t -> t.pnl).sum();
        double winRate = (double) profits / closedTrades.size() * 100;
        double avgProfit = closedTrades.stream().filter(t -> t.isProfit)
            .mapToDouble(t -> t.pnl).average().orElse(0);
        double avgLoss = closedTrades.stream().filter(t -> !t.isProfit)
            .mapToDouble(t -> t.pnl).average().orElse(0);
        
        System.out.println("\n📊 REAL UPSTOX PERFORMANCE METRICS:");
        System.out.println("=" .repeat(50));
        System.out.printf("📞 Total Real Paper Trades: %d%n", closedTrades.size());
        System.out.printf("✅ Profitable Trades: %d%n", profits);
        System.out.printf("❌ Loss Trades: %d%n", closedTrades.size() - profits);
        System.out.printf("🎯 REAL Win Rate: %.1f%% (based on Upstox data)%n", winRate);
        System.out.printf("💰 Total P&L: %.2f points%n", totalPnL);
        System.out.printf("📈 Avg Profit: %.2f points%n", avgProfit);
        System.out.printf("📉 Avg Loss: %.2f points%n", avgLoss);
        
        System.out.println("📊 Data Source: 100% Upstox real market data");
        
        if (winRate >= 55) {
            System.out.println("✅ TARGET ACHIEVED: Real win rate >= 55%");
        } else {
            System.out.println("⚠️ Below target: Continue with real data analysis");
        }
        System.out.println("=" .repeat(50));
    }
    
    /**
     * Main run loop - REAL DATA ONLY
     */
    public void runRealPaperTrading() {
        if (accessToken == null) {
            System.err.println("❌ Cannot start - not authenticated with Upstox");
            System.err.println("📱 Please run authenticateUpstox() first");
            return;
        }
        
        isRunning = true;
        System.out.println("🚀 Starting REAL paper trading with Upstox data only...");
        System.out.println("❌ NO simulation fallback - real data or fail");
        
        while (isRunning) {
            try {
                // Fetch ONLY real data - fail if not available
                if (fetchRealMarketData()) {
                    // Generate signals based on REAL analysis
                    RealTradingCall signal = generateRealTradingSignal();
                    
                    // Update existing paper trades with REAL prices
                    updatePaperTradeResults();
                    
                    // Print REAL performance every 10 trades
                    if (paperTrades.size() % 10 == 0 && !paperTrades.isEmpty()) {
                        printRealPerformance();
                    }
                } else {
                    System.err.println("❌ Failed to get real data - stopping until data available");
                    System.err.println("🔄 Will retry in 60 seconds...");
                    Thread.sleep(60000); // Wait longer when data fails
                    continue;
                }
                
                Thread.sleep(30000); // Check every 30 seconds when data is good
                
            } catch (Exception e) {
                System.err.println("Error in real paper trading loop: " + e.getMessage());
                try { Thread.sleep(30000); } catch (InterruptedException ie) { break; }
            }
        }
    }
    
    public void stop() {
        isRunning = false;
        System.out.println("🛑 Real Upstox paper trading stopped");
        printRealPerformance();
    }
    
    // Data classes (unchanged)
    private static class TechnicalAnalysis {
        final String signal;
        final double signalStrength;
        final String reason;
        
        TechnicalAnalysis(String signal, double signalStrength, String reason) {
            this.signal = signal;
            this.signalStrength = signalStrength;
            this.reason = reason;
        }
    }
    
    private static class RealTradingCall {
        final String id = UUID.randomUUID().toString();
        final String symbol;
        final String direction;
        final double entryPrice;
        final double confidence;
        final String reason;
        final LocalDateTime timestamp;
        
        double closePrice = 0;
        double pnl = 0;
        boolean isClosed = false;
        boolean isProfit = false;
        
        RealTradingCall(String symbol, String direction, double entryPrice, 
                       double confidence, String reason, LocalDateTime timestamp) {
            this.symbol = symbol;
            this.direction = direction;
            this.entryPrice = entryPrice;
            this.confidence = confidence;
            this.reason = reason;
            this.timestamp = timestamp;
        }
    }
    
    public static void main(String[] args) {
        System.out.println("🎯 REAL UPSTOX TRADING BOT - FIXED ALL CRITICAL FAILURES");
        System.out.println("=" .repeat(60));
        System.out.println("✅ ONLY Upstox real market data");
        System.out.println("❌ NO Yahoo Finance");
        System.out.println("❌ NO simulation fallback");
        System.out.println("❌ NO Math.sin() tricks");
        System.out.println("🎯 Real data or complete failure");
        System.out.println("=" .repeat(60));
        
        RealUpstoxTradingBot bot = new RealUpstoxTradingBot();
        
        // Authentication required before trading
        System.out.println("🔐 Upstox authentication required...");
        System.out.println("📝 Please provide your Upstox API credentials:");
        
        Scanner scanner = new Scanner(System.in);
        System.out.print("API Key: ");
        String apiKey = scanner.nextLine().trim();
        System.out.print("API Secret: ");
        String apiSecret = scanner.nextLine().trim();
        System.out.print("Redirect URI: ");
        String redirectUri = scanner.nextLine().trim();
        
        if (bot.authenticateUpstox(apiKey, apiSecret, redirectUri)) {
            System.out.println("✅ Authentication successful - starting real trading");
            
            Runtime.getRuntime().addShutdownHook(new Thread(bot::stop));
            bot.runRealPaperTrading();
        } else {
            System.err.println("❌ Authentication failed - cannot proceed without real data");
            System.err.println("💡 Fix authentication and try again");
        }
    }
}