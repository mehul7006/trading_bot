package com.trading.bot.telegram;

import com.trading.bot.core.Phase3IntegratedBot;
import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.util.MarketHours;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * PHASE 3 TELEGRAM BOT - Complete 3-Phase Integration
 * Features: Phase 1 (Technical+ML) + Phase 2 (Multi-timeframe+Advanced) + Phase 3 (Smart Money)
 * Responds to /start command and provides institutional-grade trading analysis
 */
public class Phase3TelegramBot {
    private static final Logger logger = LoggerFactory.getLogger(Phase3TelegramBot.class);
    
    // Telegram Bot Configuration
    private static final String BOT_TOKEN = "7921964521:AAGNk_jIcV9V5nvxSTdK4xSzeO_yS4AKZ9E";
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot" + BOT_TOKEN;
    
    // Bot components
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final Phase3IntegratedBot phase3Bot;
    private final HonestMarketDataFetcher marketDataFetcher;
    private final AIPredictor aiPredictor;
    
    // Bot state
    private long lastUpdateId = 0;
    private boolean isRunning = false;
    private final Set<Long> processedMessages = new HashSet<>();
    private long activeChatId = 0;
    private boolean autoAnalysisActive = false;
    private boolean isScanning = false;
    
    public Phase3TelegramBot() {
        this.httpClient = HttpClient.newHttpClient();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.phase3Bot = new Phase3IntegratedBot();
        this.marketDataFetcher = new HonestMarketDataFetcher();
        this.aiPredictor = new AIPredictor();
        
        // Initialize Phase 3 bot
        try {
            phase3Bot.initialize();
            logger.info("✅ Phase 3 Integrated Bot initialized successfully");
            
            this.aiPredictor.initialize();
            logger.info("✅ AI Predictor initialized successfully");
            
            // Test market data connectivity
            marketDataFetcher.testHonestConnectivity();
            logger.info("✅ HONEST Market Data Fetcher initialized successfully");
        } catch (Exception e) {
            logger.error("❌ Failed to initialize Phase 3 bot: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Start the Telegram bot
     */
    public void startBot() {
        if (isRunning) {
            logger.warn("Bot is already running");
            return;
        }
        
        isRunning = true;
        logger.info("🚀 Starting Phase 3 Telegram Bot...");
        
        // Send startup message
        sendStartupMessage();
        
        // Start message polling
        scheduler.scheduleWithFixedDelay(this::checkForMessages, 0, 2, TimeUnit.SECONDS);
        
        logger.info("✅ Phase 3 Telegram Bot started successfully");
        logger.info("🏦 Available features: Smart Money Analysis, Order Blocks, FVGs, Liquidity Analysis");
        logger.info("📱 Send /start to begin institutional trading analysis");
    }
    
    /**
     * Stop the Telegram bot
     */
    public void stopBot() {
        if (!isRunning) return;
        
        logger.info("🛑 Stopping Phase 3 Telegram Bot...");
        isRunning = false;
        autoAnalysisActive = false;
        isScanning = false;
        
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        logger.info("✅ Phase 3 Telegram Bot stopped");
    }
    
    /**
     * Send startup message to indicate bot is ready
     */
    private void sendStartupMessage() {
        String message = "🏦 **INSTITUTIONAL TRADING BOT ONLINE**\\n" +
                        "==========================================\\n\\n" +
                        "✅ **Smart Money Analysis Ready**\\n" +
                        "✅ **Order Block Detection Active**\\n" +
                        "✅ **Fair Value Gap Analysis Ready**\\n" +
                        "✅ **Liquidity Analysis Operational**\\n\\n" +
                        "📱 **Send `/start` to begin institutional analysis**\\n\\n" +
                        "🎯 **Features Available:**\\n" +
                        "• Smart Money Concepts Integration\\n" +
                        "• Institutional Grade Classification\\n" +
                        "• Professional Trading Strategies\\n\\n" +
                        "⏰ Bot started: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        logger.info("📱 Telegram Bot is ready for commands");
    }
    
    /**
     * Check for new Telegram messages
     */
    private void checkForMessages() {
        if (!isRunning) return;
        
        try {
            String url = TELEGRAM_API_URL + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=1";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
                
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                parseAndHandleUpdates(response.body());
            }
            
        } catch (Exception e) {
            logger.error("Error checking messages: {}", e.getMessage());
        }
    }
    
    /**
     * Parse and handle Telegram updates
     */
    private void parseAndHandleUpdates(String responseBody) {
        try {
            // Simple JSON parsing for updates
            if (responseBody.contains("\"result\":[")) {
                String[] updates = responseBody.split("\"update_id\":");
                
                for (int i = 1; i < updates.length; i++) {
                    String update = updates[i];
                    
                    // Extract update_id
                    long updateId = Long.parseLong(update.substring(0, update.indexOf(",")).trim());
                    if (updateId <= lastUpdateId) continue;
                    lastUpdateId = updateId;
                    
                    // Skip if already processed
                    if (processedMessages.contains(updateId)) continue;
                    processedMessages.add(updateId);
                    
                    // Extract chat_id and message text
                    if (update.contains("\"text\":")) {
                        long chatId = extractChatId(update);
                        String text = extractMessageText(update);
                        
                        if (chatId != 0 && text != null) {
                            activeChatId = chatId;
                            handleCommand(chatId, text.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing updates: {}", e.getMessage());
        }
    }
    
    /**
     * Handle incoming commands
     */
    private void handleCommand(long chatId, String command) {
        logger.info("📱 Received command: {} from chat: {}", command, chatId);
        
        try {
            // Split command and arguments
            String[] parts = command.trim().split("\\s+", 2);
            String cmd = parts[0].toLowerCase();
            
            switch (cmd) {
                case "/start" -> handleStartCommand(chatId);
                case "/status" -> handleStatusCommand(chatId);
                case "/analyze" -> handleAnalyzeCommand(chatId);
                case "/nifty" -> handleNiftyAnalysis(chatId);
                case "/sensex" -> handleSensexAnalysis(chatId);
                case "/banknifty" -> handleBankNiftyAnalysis(chatId);
                case "/auto_on" -> handleAutoAnalysisOn(chatId);
                case "/auto_off" -> handleAutoAnalysisOff(chatId);
                case "/scan" -> handleScanCommand(chatId);
                case "/stop_scan" -> handleStopScanCommand(chatId);
                case "/token" -> handleTokenCommand(chatId, command);
                case "/check_token" -> handleCheckTokenCommand(chatId);
                case "/help" -> handleHelpCommand(chatId);
                case "/stop" -> handleStopCommand(chatId);
                default -> handleUnknownCommand(chatId, command);
            }
        } catch (Exception e) {
            logger.error("Error handling command: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Error processing command: " + e.getMessage());
        }
    }
    
    /**
     * Handle /start command - Main bot activation
     */
    private void handleStartCommand(long chatId) {
        String startMessage = "🌟 **WELCOME TO YOUR TRADING COMPANION!** 🌟\n\n" +
                             "Let's conquer the market together with positive energy! 🚀\n" +
                             "I'm here to help you find high-quality setups with discipline and precision.\n\n" +
                             "✅ **Status:** ONLINE & READY\n" +
                             "📊 **Strategy:** Institutional Trend V2\n" +
                             "💪 **Goal:** Consistent, High-Probability Wins\n\n" +
                             "👇 **COMMANDS** 👇\n" +
                             "• 📡 /scan - **Start Signal Hunter** (Auto-Stop on Success)\n" +
                             "• 🛑 /stop_scan - **Stop Signal Hunter**\n" +
                             "• 🔑 /token <token> - **Update Upstox Token**";
        sendMessage(chatId, startMessage);
    }
    
    /**
     * Handle market analysis command
     */
    private void handleAnalyzeCommand(long chatId) {
        if (!MarketHours.isMarketOpen()) {
            sendMessage(chatId, "⛔ **MARKET CLOSED**\\n" + 
                              "Real-time analysis is not available.\\n" +
                              "Market Hours: 09:15 - 15:30 IST (Mon-Fri)");
            return;
        }

        sendMessage(chatId, "🔍 **Performing Smart Money Analysis...**\\n" +
                           "Please wait while I analyze market data...");
        
        try {
            // Generate analysis for major indices
            List<String> analyses = new ArrayList<>();
            
            // NIFTY50 Analysis
            analyses.add(generateInstitutionalAnalysis("NIFTY50"));
            
            // SENSEX Analysis  
            analyses.add(generateInstitutionalAnalysis("SENSEX"));
            
            // BANKNIFTY Analysis
            analyses.add(generateInstitutionalAnalysis("BANKNIFTY"));
            
            // Send comprehensive analysis
            String fullAnalysis = "🏦 **COMPREHENSIVE INSTITUTIONAL ANALYSIS**\\n" +
                                "========================================\\n\\n" +
                                String.join("\\n\\n", analyses) + "\\n\\n" +
                                "📊 **Analysis completed at:** " + 
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\\n" +
                                "🎯 **Powered by Smart Money Engine**";
            
            sendMessage(chatId, fullAnalysis);
            
        } catch (Exception e) {
            logger.error("Error generating analysis: {}", e.getMessage(), e);
            sendMessage(chatId, "❌ Error generating analysis: " + e.getMessage());
        }
    }
    
    /**
     * Generate HONEST institutional analysis for a symbol
     */
    private String generateInstitutionalAnalysis(String symbol) {
        try {
            // Get HONEST market data - NO FAKE PRICES
            List<SimpleMarketData> marketData = marketDataFetcher.getRealMarketData(symbol);
            
            // Get Phase 3 institutional analysis
            Phase3IntegratedBot.InstitutionalTradingCall analysis = 
                phase3Bot.generateInstitutionalTradingCall(symbol, marketData);
            
            // Format analysis for Telegram
            return formatInstitutionalAnalysis(analysis);
            
        } catch (Exception e) {
            logger.error("❌ HONEST ERROR for {}: {}", symbol, e.getMessage());
            
            // HONEST ERROR MESSAGE - NO FAKE PRICES
            String lastValidInfo = marketDataFetcher.getLastValidPriceInfo(symbol);
            return "❌ **" + symbol + " - API ERROR**\\n" +
                   "**Error:** " + e.getMessage() + "\\n" +
                   "**Status:** Unable to fetch real market data\\n" +
                   "**" + lastValidInfo + "**\\n\\n" +
                   "🔧 **Please try again in a few moments**";
        }
    }
    
    /**
     * Format institutional analysis for Telegram display
     */
    private String formatInstitutionalAnalysis(Phase3IntegratedBot.InstitutionalTradingCall analysis) {
        StringBuilder sb = new StringBuilder();
        
        // Header with symbol and grade
        String gradeEmoji = analysis.isInstitutionalGrade ? "🏦" : "👤";
        String gradeText = analysis.isInstitutionalGrade ? "INSTITUTIONAL" : "RETAIL";
        
        sb.append("**").append(analysis.symbol).append("** ").append(gradeEmoji).append(" *").append(gradeText).append("*\\n");
        
        // Signal and confidence
        String signalEmoji = switch (analysis.signal) {
            case "BUY" -> "📈";
            case "SELL" -> "📉";
            default -> "⏸️";
        };
        
        sb.append(signalEmoji).append(" **").append(analysis.signal).append("** | ");
        sb.append("**").append(String.format("%.1f%%", analysis.confidence)).append("** confidence\\n");
        
        // Price and Smart Money Score
        sb.append("💰 **LIVE Price: ₹").append(String.format("%.2f", analysis.price)).append("**\\n");
        sb.append("🧠 Smart Money: **").append(String.format("%.1f%%", analysis.smartMoneyScore)).append("**\\n\\n");
        
        // Smart Money Analysis
        sb.append("📊 **Smart Money Analysis:**\\n");
        sb.append("• Order Blocks: ").append(analysis.orderBlockAnalysis).append("\\n");
        sb.append("• Fair Value Gaps: ").append(analysis.fvgAnalysis).append("\\n");
        sb.append("• Liquidity: ").append(analysis.liquidityAnalysis).append("\\n\\n");
        
        // Strategy
        sb.append("🎯 **Strategy:** ").append(analysis.institutionalStrategy);
        
        return sb.toString();
    }
    
    /**
     * Get HONEST market status - NO FAKE PRICES
     */
    private String getCurrentMarketStatus() {
        try {
            Map<String, Double> prices = marketDataFetcher.getHonestMarketSnapshot();
            
            StringBuilder sb = new StringBuilder("📊 **LIVE MARKET PRICES:**\\n");
            
            for (String symbol : Arrays.asList("NIFTY50", "SENSEX", "BANKNIFTY")) {
                if (prices.containsKey(symbol)) {
                    sb.append("✅ ").append(symbol).append(": **₹")
                      .append(String.format("%.2f", prices.get(symbol))).append("**\\n");
                } else {
                    sb.append("❌ ").append(symbol).append(": API Error\\n");
                }
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            logger.error("❌ Market status error: {}", e.getMessage());
            return "❌ **MARKET DATA ERROR**\\n" +
                   "**Status:** Unable to fetch real prices\\n" +
                   "**Error:** " + e.getMessage() + "\\n" +
                   "🔧 **Please try /status again**\\n";
        }
    }
    
    // Individual symbol analysis methods
    private void handleNiftyAnalysis(long chatId) {
        sendMessage(chatId, "📈 **NIFTY50 Smart Money Analysis**\\nAnalyzing institutional patterns...");
        try {
            String analysis = generateInstitutionalAnalysis("NIFTY50");
            sendMessage(chatId, "🏦 **NIFTY50 INSTITUTIONAL ANALYSIS**\\n" +
                               "==============================\\n\\n" + analysis);
        } catch (Exception e) {
            sendMessage(chatId, "❌ NIFTY50 analysis error: " + e.getMessage());
        }
    }
    
    private void handleSensexAnalysis(long chatId) {
        sendMessage(chatId, "📈 **SENSEX Smart Money Analysis**\\nAnalyzing institutional patterns...");
        try {
            String analysis = generateInstitutionalAnalysis("SENSEX");
            sendMessage(chatId, "🏦 **SENSEX INSTITUTIONAL ANALYSIS**\\n" +
                               "============================\\n\\n" + analysis);
        } catch (Exception e) {
            sendMessage(chatId, "❌ SENSEX analysis error: " + e.getMessage());
        }
    }
    
    private void handleBankNiftyAnalysis(long chatId) {
        sendMessage(chatId, "📈 **BANKNIFTY Smart Money Analysis**\\nAnalyzing institutional patterns...");
        try {
            String analysis = generateInstitutionalAnalysis("BANKNIFTY");
            sendMessage(chatId, "🏦 **BANKNIFTY INSTITUTIONAL ANALYSIS**\\n" +
                               "================================\\n\\n" + analysis);
        } catch (Exception e) {
            sendMessage(chatId, "❌ BANKNIFTY analysis error: " + e.getMessage());
        }
    }
    
    private void handleAutoAnalysisOn(long chatId) {
        if (!autoAnalysisActive) {
            autoAnalysisActive = true;
            sendMessage(chatId, "🔄 **Auto Analysis ACTIVATED**\\n" +
                               "Continuous institutional analysis started.\\n" +
                               "Updates every 5 minutes.\\n\\n" +
                               "Send /auto_off to stop.");
            
            // Schedule continuous analysis
            scheduler.scheduleWithFixedDelay(() -> {
                if (autoAnalysisActive && activeChatId > 0) {
                    try {
                        String quickAnalysis = "🔄 **Auto Update:** " +
                                             generateInstitutionalAnalysis("NIFTY50");
                        sendMessage(activeChatId, quickAnalysis);
                    } catch (Exception e) {
                        logger.error("Auto analysis error: {}", e.getMessage());
                    }
                }
            }, 5, 5, TimeUnit.MINUTES);
        } else {
            sendMessage(chatId, "ℹ️ Auto analysis is already active.");
        }
    }
    
    private void handleAutoAnalysisOff(long chatId) {
        if (autoAnalysisActive) {
            autoAnalysisActive = false;
            sendMessage(chatId, "⏹️ **Auto Analysis STOPPED**\\n" +
                               "Continuous updates disabled.\\n\\n" +
                               "Send /auto_on to restart.");
        } else {
            sendMessage(chatId, "ℹ️ Auto analysis is already inactive.");
        }
    }
    
    private void handleStatusCommand(long chatId) {
        String marketStatus = getCurrentMarketStatus();
        String status = "📊 **BOT STATUS**\\n" +
                       "========================\\n\\n" +
                       "✅ **System Status:** " + (isRunning ? "ONLINE" : "OFFLINE") + "\\n" +
                       "🏦 **Engine Status:** " + phase3Bot.getPhase3Status() + "\\n" +
                       "🔄 **Auto Analysis:** " + (autoAnalysisActive ? "ACTIVE" : "INACTIVE") + "\\n" +
                       "📱 **Active Chat:** " + chatId + "\\n" +
                       "🔍 **Signal Hunter:** " + (isScanning ? "ACTIVE" : "INACTIVE") + "\\n\\n" +
                       marketStatus + "\\n" +
                       "⏰ **Current Time:** " + 
                       LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\\n\\n" +
                       "🎯 **Ready for institutional trading analysis!**";
        
        sendMessage(chatId, status);
    }
    
    private void handleScanCommand(long chatId) {
        if (!MarketHours.isMarketOpen()) {
            sendMessage(chatId, "⛔ **MARKET IS CLOSED**\\n\\n" +
                              "Signal Hunter only works during market hours (9:15 AM - 3:30 PM IST).\\n" +
                              "Please come back when the market opens! ⏰");
            return;
        }

        if (isScanning) {
            sendMessage(chatId, "⚠️ **Signal Hunter is already active!**\nRelax, I'm watching the markets for you. 🦅");
            return;
        }
        
        isScanning = true;
        sendMessage(chatId, "🚀 **SIGNAL HUNTER ACTIVATED** 🚀\n" +
                           "Scanning the market with positive energy! ✨\n\n" +
                           "🔍 **Mission:** Find High-Quality Setups\n" +
                           "📊 **Watchlist Criteria:** > 65% Confidence (For Observation)\n" +
                           "💎 **Strong Signal Criteria:** > 85% Confidence (Actionable)\n\n" +
                           "I will keep scanning until we find a diamond! 💎\n" +
                           "Use `/stop_scan` if you want to take a break.");
        
        // Schedule scanning task
        scheduler.scheduleWithFixedDelay(() -> {
            if (!isScanning) return;
            performScan(chatId);
        }, 0, 1, TimeUnit.MINUTES);
    }

    private void handleStopScanCommand(long chatId) {
        if (!isScanning) {
            sendMessage(chatId, "ℹ️ Signal Hunter is currently resting. 😴");
            return;
        }
        
        isScanning = false;
        sendMessage(chatId, "🛑 **Signal Hunter STOPPED**\nTaking a break. See you soon! 👋");
    }

    private void performScan(long chatId) {
        try {
            String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};
            
            for (String symbol : symbols) {
                if (!isScanning) break;
                
                List<SimpleMarketData> data = marketDataFetcher.getRealMarketData(symbol);
                AIPredictor.AIPrediction prediction = aiPredictor.generatePrediction(symbol, data);
                
                boolean isSignificantMove = checkMinimumPoints(symbol, prediction.estimatedMovePoints);
                
                // 1. WATCHLIST ALERT (> 65% but < 85%)
                if (prediction.confidence > 65 && prediction.confidence < 85 && isSignificantMove) {
                     String alert = "👀 **WATCHLIST ALERT** (Not a Trade Yet)\n" +
                                   "------------------------\n" +
                                   "Symbol: **" + symbol + "**\n" +
                                   "Direction: **" + prediction.predictedDirection + "**\n" +
                                   "Confidence: **" + String.format("%.1f%%", prediction.confidence) + "**\n" +
                                   "Reason: " + prediction.predictionReasoning + "\n" +
                                   "------------------------\n" +
                                   "⚠️ **Observation Only - Waiting for stronger confirmation (>85%)**";
                     sendMessage(chatId, alert);
                }
                
                // 2. STRONG SIGNAL (> 85%) - MISSION ACCOMPLISHED
                if (prediction.confidence >= 85 && isSignificantMove) {
                     String alert = "🚀 **STRONG SIGNAL DETECTED!** 🚀\n" +
                                   "------------------------\n" +
                                   "Symbol: **" + symbol + "**\n" +
                                   "Direction: **" + prediction.predictedDirection + "**\n" +
                                   "Est. Target: **" + String.format("%.2f", prediction.estimatedMovePoints) + " pts**\n" +
                                   "🛑 Stop Loss: **" + String.format("%.2f", prediction.suggestedStopLoss) + " pts**\n" +
                                   "Confidence: **" + String.format("%.1f%%", prediction.confidence) + "**\n" +
                                   "Reason: " + prediction.predictionReasoning + "\n" +
                                   "------------------------\n" +
                                   "💎 **HIGH PROBABILITY SETUP FOUND!** 💎\n" +
                                   "Stopping scanner to let you focus on execution.";
                     sendMessage(chatId, alert);
                     
                     // Stop Scanning automatically on success
                     isScanning = false;
                     sendMessage(chatId, "✅ **Mission Accomplished!** Scanner stopped.\nUse `/scan` to start a new hunt.");
                     break; // Exit loop
                }
            }
        } catch (Exception e) {
            logger.error("Scan error: {}", e.getMessage());
        }
    }

    private boolean checkMinimumPoints(String symbol, double estimatedPoints) {
        double minPoints = switch (symbol) {
            case "NIFTY50" -> 30.0;
            case "SENSEX" -> 100.0;
            case "BANKNIFTY" -> 60.0;
            default -> 20.0;
        };
        return estimatedPoints >= minPoints;
    }
    
    /**
     * Handle token update command
     * Usage: /token <new_access_token>
     */
    private void handleTokenCommand(long chatId, String command) {
        String[] parts = command.split(" ", 2);
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            sendMessage(chatId, "⚠️ **Invalid Format**\\nUsage: `/token <your_access_token>`");
            return;
        }
        
        String newToken = parts[1].trim();
        marketDataFetcher.setAccessToken(newToken);
        
        sendMessage(chatId, "✅ **Access Token Updated Successfully**\\n" +
                           "Bot is ready to use with new credentials.\\n" +
                           "You can now use `/scan` or `/start`.");
    }

    /**
     * Handle check token command
     */
    private void handleCheckTokenCommand(long chatId) {
        String currentToken = marketDataFetcher.getAccessToken();
        String maskedToken = "No token set";
        
        if (currentToken != null && currentToken.length() > 10) {
            String start = currentToken.substring(0, 5);
            String end = currentToken.substring(currentToken.length() - 5);
            maskedToken = start + "..." + end;
        }
        
        sendMessage(chatId, "🔐 **Current Access Token Status**\\n" +
                           "**Token:** `" + maskedToken + "`\\n" +
                           "**Status:** Active");
    }

    private void handleHelpCommand(long chatId) {
        String help = "🏦 **INSTITUTIONAL TRADING COMMANDS**\\n" +
                     "===================================\\n\\n" +
                     "**Signal Hunting:**\\n" +
                     "/scan - Start searching for high-probability setups\\n" +
                     "/stop_scan - Stop the signal hunter\\n\\n" +
                     "**Market Analysis:**\\n" +
                     "/analyze - Full market overview\\n" +
                     "/nifty - NIFTY50 Smart Money analysis\\n" +
                     "/sensex - SENSEX institutional analysis\\n" +
                     "/banknifty - BANKNIFTY liquidity analysis\\n\\n" +
                     "**System:**\\n" +
                     "/token <token> - Update Upstox Access Token\\n" +
                     "/check_token - View current token status\\n" +
                     "/start - Reset bot\\n" +
                     "/status - Check health\\n" +
                     "/help - Show this menu\\n\\n" +
                     "🧠 **Features:** Order Blocks, FVGs, Liquidity, Institutional Grading";
        
        sendMessage(chatId, help);
    }
    
    private void handleStopCommand(long chatId) {
        sendMessage(chatId, "🛑 **Stopping Phase 3 Telegram Bot...**\\n" +
                           "Thank you for using institutional trading analysis!");
        
        scheduler.schedule(() -> {
            stopBot();
            System.exit(0);
        }, 2, TimeUnit.SECONDS);
    }
    
    private void handleUnknownCommand(long chatId, String command) {
        sendMessage(chatId, "❓ **Unknown command:** `" + command + "`\\n\\n" +
                           "📱 **Available commands:**\\n" +
                           "Send /help for full command list\\n" +
                           "Send /start to begin analysis\\n" +
                           "Send /analyze for market analysis");
    }
    
    /**
     * Send message to Telegram chat
     */
    private void sendMessage(long chatId, String text) {
        try {
            String url = TELEGRAM_API_URL + "/sendMessage";
            String jsonData = String.format(
                "{\"chat_id\":%d,\"text\":\"%s\",\"parse_mode\":\"Markdown\"}", 
                chatId, text.replace("\"", "\\\"")
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonData))
                .build();
                
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logger.debug("📤 Sent message to chat {}: {}", chatId, text.substring(0, Math.min(50, text.length())));
            
        } catch (Exception e) {
            logger.error("❌ Error sending message: {}", e.getMessage());
        }
    }
    
    // Utility methods for parsing Telegram responses
    private long extractChatId(String update) {
        try {
            String chatSection = update.substring(update.indexOf("\"chat\":"));
            String idSection = chatSection.substring(chatSection.indexOf("\"id\":") + 5);
            return Long.parseLong(idSection.substring(0, idSection.indexOf(",")).trim());
        } catch (Exception e) {
            return 0;
        }
    }
    
    private String extractMessageText(String update) {
        try {
            String textStart = "\"text\":\"";
            int startIndex = update.indexOf(textStart);
            if (startIndex == -1) return null;
            
            startIndex += textStart.length();
            int endIndex = update.indexOf("\"", startIndex);
            
            return update.substring(startIndex, endIndex);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Main method to start the Phase 3 Telegram Bot
     */
    public static void main(String[] args) {
        System.out.println("🏦 STARTING PHASE 3 INSTITUTIONAL TELEGRAM BOT");
        System.out.println("===============================================");
        System.out.println("✅ Smart Money Concepts Integration");
        System.out.println("✅ Order Block Detection");
        System.out.println("✅ Fair Value Gap Analysis");
        System.out.println("✅ Liquidity Analysis");
        System.out.println("✅ Institutional Grade Classification");
        System.out.println();
        
        Phase3TelegramBot bot = new Phase3TelegramBot();
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\\n🛑 Shutting down Phase 3 Telegram Bot...");
            bot.stopBot();
        }));
        
        // Start the bot
        bot.startBot();
        
        // Keep the application running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            bot.stopBot();
        }
    }
}