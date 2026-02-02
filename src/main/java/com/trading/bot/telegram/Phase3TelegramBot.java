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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.ScheduledFuture;

/**
 * PHASE 3 TELEGRAM BOT - Complete 3-Phase Integration
 * Features: Phase 1 (Technical+ML) + Phase 2 (Multi-timeframe+Advanced) + Phase 3 (Smart Money)
 * Responds to /start command and provides institutional-grade trading analysis
 */
public class Phase3TelegramBot {
    private static final Logger logger = LoggerFactory.getLogger(Phase3TelegramBot.class);
    
    // Telegram Bot Configuration
    private static final String BOT_TOKEN = System.getenv("TELEGRAM_BOT_TOKEN") != null ? System.getenv("TELEGRAM_BOT_TOKEN") : "7921964521:AAGNk_jIcV9V5nvxSTdK4xSzeO_yS4AKZ9E";
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
    private final Map<String, Long> lastAlertTimeMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastWatchlistAlertMap = new ConcurrentHashMap<>();
    private ScheduledFuture<?> scanFuture;
    private final Map<Long, String> pendingCommands = new ConcurrentHashMap<>();
    
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
        System.out.println("DEBUG: Received command: " + command + " from chat: " + chatId);
        
        try {
            // 1. Handle Interruption Logic (Waiting for Confirmation)
            if (pendingCommands.containsKey(chatId)) {
                String pendingCmd = pendingCommands.get(chatId);
                String userResponse = command.trim().toLowerCase();
                
                if (userResponse.equals("yes") || userResponse.equals("y")) {
                    // Stop scanning and execute pending command
                    stopScanningForInterruption(chatId);
                    pendingCommands.remove(chatId);
                    sendMessage(chatId, "🛑 **Scanning Stopped.**\nExecuting your command: `" + pendingCmd + "`...");
                    
                    // Recursive call to execute the pending command
                    handleCommand(chatId, pendingCmd);
                    return;
                } else if (userResponse.equals("no") || userResponse.equals("n")) {
                    // Cancel pending command and continue scanning
                    pendingCommands.remove(chatId);
                    sendMessage(chatId, "✅ **Continuing Signal Hunter...**\nI'll keep watching the market for you. 🦅");
                    return;
                } else {
                    sendMessage(chatId, "⚠️ **Invalid Response**\n" +
                                      "Signal Hunter is running. Do you want to stop it to run `" + pendingCmd + "`?\n\n" +
                                      "👉 Reply **YES** to stop scanning.\n" +
                                      "👉 Reply **NO** to continue scanning.");
                    return;
                }
            }
            
            // 2. Handle Active Scanning Interruption
            // If scanning is active AND command is NOT /stop_scan (and not a confirmation), intercept it
            if (isScanning && !command.equalsIgnoreCase("/stop_scan") && !command.equalsIgnoreCase("/stop")) {
                pendingCommands.put(chatId, command);
                sendMessage(chatId, "⚠️ **Signal Hunter is Active!** 🦅\n\n" +
                                  "I am currently scanning the market for high-probability setups.\n" +
                                  "Do you want to **STOP** scanning to run `" + command + "`?\n\n" +
                                  "👉 Reply **YES** to stop and run command.\n" +
                                  "👉 Reply **NO** to continue scanning.");
                return;
            }

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
        String startMessage = "🚀 *WELCOME TO THE FUTURE OF TRADING!* 🚀\n\n" +
                             "👋 *Hello Trader!*\n" +
                             "I am your *AI-Powered Institutional Trading Assistant*. I don't just guess; I *analyze* the market like a pro using Smart Money Concepts, Order Blocks, and Deep Learning.\n\n" +
                             "💎 *Why Work With Me?*\n" +
                             "✅ *High Accuracy:* My signals are backed by real-time data & institutional patterns.\n" +
                             "✅ *No Noise:* I only speak when I see a *Confirmed Opportunity*.\n" +
                             "✅ *Full Transparency:* Real Targets, Real Stop Losses, Real Logic.\n\n" +
                             "📊 *I Monitor:* NIFTY50, BANKNIFTY, SENSEX\n" +
                             "🕒 *Active Hours:* 09:15 AM - 03:30 PM IST\n\n" +
                             "🔥 *Ready to Capture Big Moves?*\n" +
                             "Tap /scan below to start the *Signal Hunter*. I will notify you *instantly* when I detect a high-probability setup!\n\n" +
                             "👇 *COMMANDS* 👇\n" +
                             "• 📡 /scan - *Activate Signal Hunter* (I will watch the market for you)\n" +
                             "• 🛑 /stop_scan - *Stop Signal Hunter*\n" +
                             "• 📊 /analyze - *Instant Market Snapshot*";
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
        // 1. Market Hours Check (Strict 09:15 - 15:30 IST)
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));
        LocalTime marketStart = LocalTime.of(9, 15);
        LocalTime marketEnd = LocalTime.of(15, 30);
        
        if (now.isBefore(marketStart) || now.isAfter(marketEnd)) {
            sendMessage(chatId, "⛔ **MARKET IS CLOSED**\n\n" +
                              "Signal Hunter is only active during market hours:\n" +
                              "⏰ **09:15 AM - 03:30 PM IST**\n\n" +
                              "Please come back when the market opens! I'll be ready. 🦅");
            return;
        }

        if (isScanning) {
            sendMessage(chatId, "⚠️ **Signal Hunter is already running!**\nRelax, I'm watching the markets for you. 🦅");
            return;
        }
        
        isScanning = true;
        sendMessage(chatId, "🦅 **Signal Hunter ACTIVATED**\n" +
                           "Please wait, I am scanning your market data... 🔄\n\n" +
                           "I will notify you **ONLY** when I find a **CONFIRMED** movement with high probability.\n" +
                           "Scanning: NIFTY50, BANKNIFTY, SENSEX...");
        
        // Schedule scanning task (Initial delay 5 seconds to allow message to send)
        if (scanFuture != null && !scanFuture.isDone()) {
            scanFuture.cancel(false);
        }
        
        scanFuture = scheduler.scheduleWithFixedDelay(() -> {
            if (!isScanning) return;
            performScan(chatId);
        }, 5, 60, TimeUnit.SECONDS); // Scan every 60 seconds
    }

    private void stopScanningForInterruption(long chatId) {
        if (!isScanning) return;
        isScanning = false;
        if (scanFuture != null) {
            scanFuture.cancel(false);
        }
    }

    private void handleStopScanCommand(long chatId) {
        if (!isScanning) {
            sendMessage(chatId, "ℹ️ Signal Hunter is currently resting. 😴");
            return;
        }
        
        isScanning = false;
        if (scanFuture != null) {
            scanFuture.cancel(false);
        }
        sendMessage(chatId, "🛑 **Signal Hunter STOPPED**\nTaking a break. See you soon! 👋");
    }

    private void performScan(long chatId) {
        try {
            String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};
            
            for (String symbol : symbols) {
                if (!isScanning) break;
                
                // Fetch real data
                List<SimpleMarketData> data = marketDataFetcher.getRealMarketData(symbol);
                if (data == null || data.isEmpty()) continue;
                
                double currentPrice = data.get(data.size() - 1).price;
                AIPredictor.AIPrediction prediction = aiPredictor.generatePrediction(symbol, data);
                
                boolean isSignificantMove = checkMinimumPoints(symbol, prediction.estimatedMovePoints);
                
                // Only Alert on Strong Signals (> 80% Confidence) or Watchlist (> 65%)
                if (isSignificantMove) {
                    long currentTime = System.currentTimeMillis();
                    
                    // 1. CONFIRMED SIGNAL (> 80%)
                    if (prediction.confidence >= 80) {
                        // Spam Prevention: 15 min cooldown for confirmed alerts
                        long lastAlert = lastAlertTimeMap.getOrDefault(symbol, 0L);
                        if (currentTime - lastAlert < 15 * 60 * 1000) {
                            continue;
                        }

                        // Calculate Target Range
                        double targetPoints = prediction.estimatedMovePoints;
                        double targetPrice = prediction.predictedDirection.equals("UP") 
                                           ? currentPrice + targetPoints 
                                           : currentPrice - targetPoints;
                        
                        String arrow = prediction.predictedDirection.equals("UP") ? "⬆️" : "⬇️";
                        String color = prediction.predictedDirection.equals("UP") ? "🟢" : "🔴";
                        
                        String alert = color + " **CONFIRMED MOVEMENT DETECTED** " + color + "\n" +
                                      "----------------------------------\n" +
                                      "Symbol: **" + symbol + "**\n" +
                                      "Direction: **" + prediction.predictedDirection + "** " + arrow + "\n\n" +
                                      "🚀 **Projected Move:** " + String.format("%.0f", targetPoints) + " pts " + (prediction.predictedDirection.equals("UP") ? "Increase" : "Decrease") + "\n" +
                                      "💰 **Price Target:** " + String.format("%.0f", currentPrice) + " ➔ " + String.format("%.0f", targetPrice) + "\n\n" +
                                      "🛑 Stop Loss: " + String.format("%.0f", prediction.suggestedStopLoss) + " pts\n" +
                                      "🧠 Confidence: **" + String.format("%.1f%%", prediction.confidence) + "**\n" +
                                      "----------------------------------\n" +
                                      "⚠️ *Trade at your own risk. Use proper risk management.*";
                        
                        sendMessage(chatId, alert);
                        lastAlertTimeMap.put(symbol, currentTime);
                    }
                    // 2. WATCHLIST SIGNAL (65% - 80%)
                    else if (prediction.confidence >= 65) {
                        // Spam Prevention: Don't alert if we recently sent ANY alert (Confirmed or Watchlist)
                        long lastWatchlist = lastWatchlistAlertMap.getOrDefault(symbol, 0L);
                        long lastConfirmed = lastAlertTimeMap.getOrDefault(symbol, 0L);
                        
                        if (currentTime - lastWatchlist < 15 * 60 * 1000 || currentTime - lastConfirmed < 15 * 60 * 1000) {
                            continue;
                        }
                        
                        String arrow = prediction.predictedDirection.equals("UP") ? "⬆️" : "⬇️";
                        
                        String alert = "⚠️ **ADDED TO WATCHLIST** ⚠️\n" +
                                      "----------------------------------\n" +
                                      "Symbol: **" + symbol + "**\n" +
                                      "Potential: **" + prediction.predictedDirection + "** " + arrow + "\n\n" +
                                      "👀 **Observation:** Price is approaching key level.\n" +
                                      "📉 **Potential Move:** ~" + String.format("%.0f", prediction.estimatedMovePoints) + " pts\n" +
                                      "🧠 Confidence: **" + String.format("%.1f%%", prediction.confidence) + "** (Waiting for >80%)\n" +
                                      "----------------------------------\n" +
                                      "⏳ *Waiting for confirmation...*";
                        
                        sendMessage(chatId, alert);
                        lastWatchlistAlertMap.put(symbol, currentTime);
                    }
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
            // Attempt 1: Send with Markdown
            boolean success = sendRequest(chatId, text, "Markdown");
            
            // Attempt 2: Retry with Plain Text if Markdown failed
            if (!success) {
                System.out.println("⚠️ Markdown failed for chat " + chatId + ", retrying with plain text...");
                // Remove Markdown symbols for plain text readability
                String plainText = text.replace("**", "").replace("__", "").replace("`", "");
                sendRequest(chatId, plainText, null);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error sending message: " + e.getMessage());
            logger.error("❌ Error sending message: {}", e.getMessage());
        }
    }

    private boolean sendRequest(long chatId, String text, String parseMode) {
        try {
            String url = TELEGRAM_API_URL + "/sendMessage";
            
            // Escape special characters for JSON
            String jsonText = text.replace("\\", "\\\\")
                                  .replace("\"", "\\\"")
                                  .replace("\n", "\\n")
                                  .replace("\r", "");
            
            String jsonData;
            if (parseMode != null) {
                jsonData = String.format(
                    "{\"chat_id\":%d,\"text\":\"%s\",\"parse_mode\":\"%s\"}", 
                    chatId, jsonText, parseMode
                );
            } else {
                 jsonData = String.format(
                    "{\"chat_id\":%d,\"text\":\"%s\"}", 
                    chatId, jsonText
                );
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonData))
                .build();
                
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                System.err.println("❌ Telegram API Error: " + response.statusCode() + " - " + response.body());
                logger.error("Telegram API Error: {} - {}", response.statusCode(), response.body());
                return false;
            } else {
                logger.debug("📤 Sent message to chat {}: {}", chatId, text.substring(0, Math.min(50, text.length())));
                System.out.println("✅ Message sent to " + chatId);
                return true;
            }
        } catch (Exception e) {
            logger.error("Request error: {}", e.getMessage());
            return false;
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
        System.out.println("STARTING PHASE 3 INSTITUTIONAL TELEGRAM BOT");
        System.out.println("===============================================");
        System.out.println("- Smart Money Concepts Integration");
        System.out.println("- Order Block Detection");
        System.out.println("- Fair Value Gap Analysis");
        System.out.println("- Liquidity Analysis");
        System.out.println("- Institutional Grade Classification");
        System.out.println();
        
        Phase3TelegramBot bot = new Phase3TelegramBot();
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nSTOP: Shutting down Phase 3 Telegram Bot...");
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