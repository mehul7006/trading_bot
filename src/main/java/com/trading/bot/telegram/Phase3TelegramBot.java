package com.trading.bot.telegram;

import com.trading.bot.core.Phase3IntegratedBot;
import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.market.OptionData;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.strategy.NewsSentimentUtils;
import com.trading.bot.util.MarketHours;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
    private final Set<Long> processedMessages = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private long activeChatId = 0;
    private boolean autoAnalysisActive = false;
    private boolean isScanning = false;
    private int todayCallsGenerated = 0;
    private final Map<String, Long> lastAlertTimeMap = new ConcurrentHashMap<>();
    private final Map<String, Long> lastWatchlistAlertMap = new ConcurrentHashMap<>();
    private final Map<Long, String> lastSentMessageByChat = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastSentMessageTime = new ConcurrentHashMap<>();
    private final Map<Long, DedupCommand> lastCommandByChat = new ConcurrentHashMap<>();
    
    private static class DedupCommand {
        final String cmd;
        final long at;
        DedupCommand(String cmd, long at) { this.cmd = cmd; this.at = at; }
    }
    
    private static class ActiveSignal {
        String symbol;
        String direction;
        double entryPrice;
        double targetPoints;
        double stopLossPoints;
        long createdAt;
    }
    
    private final Map<String, ActiveSignal> activeSignals = new ConcurrentHashMap<>();
    private java.time.LocalDate lastResetDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
    private final java.util.Set<Integer> slotsTriggered = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    private void checkActiveSignal(long chatId, String symbol, List<SimpleMarketData> data) {
        ActiveSignal s = activeSignals.get(symbol);
        if (s == null || data.isEmpty()) return;
        SimpleMarketData last = data.get(data.size() - 1);
        double entry = s.entryPrice;
        double target = s.targetPoints;
        double sl = s.stopLossPoints;
        boolean hitTarget = false;
        boolean hitStop = false;
        if ("UP".equalsIgnoreCase(s.direction)) {
            double targetPrice = entry + target;
            double slPrice = entry - sl;
            if (last.high >= targetPrice) hitTarget = true;
            if (last.low <= slPrice) hitStop = true;
        } else {
            double targetPrice = entry - target;
            double slPrice = entry + sl;
            if (last.low <= targetPrice) hitTarget = true;
            if (last.high >= slPrice) hitStop = true;
        }
        if (hitStop) {
            String message = String.format("🛑 **STOP LOSS HIT (Cost-to-Cost Close)**\n\n" +
                                         "Symbol: %s\n" +
                                         "Exit Price: %.2f\n" +
                                         "Reason: Price touched SL protection level.\n\n" +
                                         "⚠️ Bot detected a trend reversal or excessive noise. Position closed to prevent further loss.", 
                                         symbol, last.price);
            sendMessage(chatId, message);
            activeSignals.remove(symbol);
        } else if (hitTarget) {
            String message = String.format("✅ **TARGET ACHIEVED!**\n\n" +
                                         "Symbol: %s\n" +
                                         "Exit Price: %.2f\n" +
                                         "Strategy: %s\n\n" +
                                         "💰 Move captured successfully. Position closed.", 
                                         symbol, last.price, "AI Prediction");
            sendMessage(chatId, message);
            activeSignals.remove(symbol);
        }
    }
    private ScheduledFuture<?> scanFuture;
    private final Map<Long, String> pendingCommands = new ConcurrentHashMap<>();
    
    public Phase3TelegramBot() {
        this(false);
    }

    public Phase3TelegramBot(boolean testMode) {
        this.httpClient = HttpClient.newHttpClient();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.phase3Bot = new Phase3IntegratedBot();
        this.marketDataFetcher = HonestMarketDataFetcher.getInstance();
        this.aiPredictor = new AIPredictor();
        
        try {
            if (!testMode) {
                phase3Bot.initialize();
                logger.info("✅ Phase 3 Integrated Bot initialized successfully");
                
                this.aiPredictor.initialize();
                logger.info("✅ AI Predictor initialized successfully");
                
                marketDataFetcher.testHonestConnectivity();
                logger.info("✅ HONEST Market Data Fetcher initialized successfully");
            } else {
                this.aiPredictor.initialize();
                logger.info("✅ AI Predictor initialized in test mode");
            }
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
        
        // Silent Startup - No spamming user on restart
        // sendStartupMessage();
        
        // Start message polling
        scheduler.scheduleWithFixedDelay(this::checkForMessages, 0, 2, TimeUnit.SECONDS);
        
        // Schedule daily cleanup at 11:59 PM
        scheduleDailyCleanup();
        
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
    
    private void parseAndHandleUpdates(String responseBody) {
        try {
            // Simple JSON parsing for updates
            if (responseBody.contains("\"result\":[")) {
                String[] updates = responseBody.split("\"update_id\":");
                
                // Map to store only the latest command per chat to handle queue rollover
                Map<Long, String> latestCommands = new HashMap<>();
                long maxUpdateId = lastUpdateId;
                
                for (int i = 1; i < updates.length; i++) {
                    String update = updates[i];
                    
                    try {
                        // Extract update_id
                        String updateIdStr = update.substring(0, update.indexOf(",")).trim();
                        long updateId = Long.parseLong(updateIdStr);
                        
                        if (updateId <= lastUpdateId) continue;
                        if (updateId > maxUpdateId) maxUpdateId = updateId;
                        
                        // Extract chatId and messageText
                        long chatId = extractChatId(update);
                        String text = extractMessageText(update);
                        
                        if (chatId != 0 && text != null) {
                            // Filter out messages older than 30 seconds to prevent "re-play" after restart
                            // Note: Telegram doesn't give timestamp easily in this simple parser, 
                            // but we can at least ensure we only process each updateId ONCE globally.
                            latestCommands.put(chatId, text);
                        }
                    } catch (Exception e) {
                        // Skip malformed update
                    }
                }
                
                lastUpdateId = maxUpdateId;
                
                // Process each unique command
                latestCommands.forEach(this::processCommand);
            }
        } catch (Exception e) {
            logger.error("Error parsing updates: {}", e.getMessage());
        }
    }

    private synchronized void processCommand(long chatId, String text) {
        // Simple case-insensitive match for the command part
        String command = text.trim();
        String cmdKey = command.toLowerCase().split("\\s+")[0];
        
        // Check for exact duplicate in the last 5 seconds to prevent rapid double-processing
        String lastCmd = pendingCommands.get(chatId);
        if (command.equals(lastCmd)) {
            return;
        }
        pendingCommands.put(chatId, command);
        
        logger.info("📩 Received command from {}: {}", chatId, command);
        
        if (cmdKey.startsWith("/start")) {
            handleStartCommand(chatId);
        } else if (cmdKey.startsWith("/token")) {
            handleTokenCommand(chatId, command);
        } else if (cmdKey.startsWith("/status")) {
            handleStatusCommand(chatId);
        } else if (cmdKey.startsWith("/scan")) {
            handleScanCommand(chatId);
        } else if (cmdKey.startsWith("/stop")) {
            handleStopScanCommand(chatId);
        }
        
        // Auto-clear from pending after a short delay to allow future valid same commands
        scheduler.schedule(() -> pendingCommands.remove(chatId), 5, TimeUnit.SECONDS);
    }
    
    /**
     * Handle incoming commands
     */
    protected void handleCommand(long chatId, String command) {
        logger.info("📱 Received command: {} from chat: {}", command, chatId);
        System.out.println("DEBUG: Received command: " + command + " from chat: " + chatId);
        
        try {
            // Dedup identical commands quickly repeated (e.g., double taps)
            long now = System.currentTimeMillis();
            DedupCommand last = lastCommandByChat.get(chatId);
            if (last != null && last.cmd.equalsIgnoreCase(command.trim()) && (now - last.at) < 1500) {
                return;
            }
            lastCommandByChat.put(chatId, new DedupCommand(command.trim(), now));
            
            // Split command and arguments
            String[] parts = command.trim().split("\\s+", 2);
            String cmd = parts[0].toLowerCase();
            
            switch (cmd) {
                case "/start" -> handleStartCommand(chatId);
                case "/status" -> handleStatusCommand(chatId);
                case "/scan" -> handleScanCommand(chatId);
                case "/stop_scan" -> handleStopScanCommand(chatId);
                case "/token" -> handleTokenCommand(chatId, command);
                default -> {
                    sendMessage(chatId, "⚠️ **Unknown Command**\n\n" +
                                      "Please use one of the valid commands:\n" +
                                      "✅ `/start` - Initialize Bot\n" +
                                      "🔍 `/scan` - Start Market Scanning\n" +
                                      "🛑 `/stop_scan` - Stop Scanning\n" +
                                      "📊 `/status` - Check Market Status\n" +
                                      "🔑 `/token [token]` - Update Access Token");
                }
            }
        } catch (Exception e) {
            logger.error("Error handling command: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handle /start command
     */
    protected void handleStartCommand(long chatId) {
        sendMessage(chatId, "👋 **Welcome to Institutional Trading Bot**\n\n" +
                           "🚀 **System Online & Ready**\n" +
                           "📊 **Market Analysis**: Active\n" +
                           "🤖 **AI Prediction**: Enabled\n\n" +
                           "Use `/scan` to start tracking opportunities!");
    }
    
    // Dead code removed

    
    protected String getCurrentMarketRatesSimple() {
        try {
            Map<String, Double> prices = marketDataFetcher.getHonestMarketSnapshot();
            StringBuilder sb = new StringBuilder();
            LocalDateTime now = LocalDateTime.now();
            
            for (String symbol : Arrays.asList("NIFTY50", "BANKNIFTY", "SENSEX")) {
                if (prices.containsKey(symbol)) {
                    LocalDateTime lastTime = marketDataFetcher.getLastValidTime(symbol);
                    boolean isFresh = lastTime != null && lastTime.isAfter(now.minusMinutes(5));
                    
                    String emoji = switch (symbol) {
                        case "NIFTY50" -> "📉";
                        case "BANKNIFTY" -> "🏦";
                        case "SENSEX" -> "📊";
                        default -> "📈";
                    };
                    
                    String status = isFresh ? "✅" : "⚠️ (Delayed)";
                    sb.append(emoji).append(" **").append(symbol).append("** : `").append(String.format("%.2f", prices.get(symbol)))
                      .append("` ").append(status).append("\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }
    
    // More dead code removed

    
    protected void handleStatusCommand(long chatId) {
        // Clear slots triggered if it's a new day (Safety check)
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (!today.equals(lastResetDate)) {
            todayCallsGenerated = 0;
            slotsTriggered.clear();
            lastResetDate = today;
        }

        String rates = getCurrentMarketRatesSimple();
        String status = "📊 **Market Status Report**\n\n" + 
                       rates + "\n\n" +
                       "📢 **Today's Activity**\n" +
                       "• Calls Generated: `" + todayCallsGenerated + "`\n" +
                       "• Active Slots: `" + slotsTriggered.size() + "/4`\n" +
                       "• Bot Instance: `V23.0-ELITE` (Multi-Index Optimized)";
        
        sendMessage(chatId, status);
    }
    
    /**
     * Handle /scan command
     */
    protected void handleScanCommand(long chatId) {
        if (isScanning && scanFuture != null && !scanFuture.isCancelled() && !scanFuture.isDone()) {
            sendMessage(chatId, "🔍 **Scanning is Already Active**\n\n" +
                              "🤖 Bot is currently monitoring the market.");
            return;
        }
        
        isScanning = true;
        
        // Ensure any old task is cancelled
        if (scanFuture != null) {
            scanFuture.cancel(true);
        }
        
        sendMessage(chatId, "🔍 **Scanning Started**\n\n" +
                          "📡 Monitoring NIFTY50, SENSEX, BANKNIFTY...\n" +
                          "🤖 AI analyzing patterns...\n" +
                          "🔔 You will be notified of high-confidence signals.");
        
        // Schedule scanning task
        scanFuture = scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!isScanning) return;
                performScan(chatId);
            } catch (Exception e) {
                logger.error("Critical error in scanning task: {}", e.getMessage());
            }
        }, 5, 60, TimeUnit.SECONDS);
    }

    private void stopScanningForInterruption(long chatId) {
        if (!isScanning) return;
        isScanning = false;
        if (scanFuture != null) {
            scanFuture.cancel(false);
        }
    }

    protected void handleStopScanCommand(long chatId) {
        isScanning = false;
        if (scanFuture != null) {
            scanFuture.cancel(false);
        }
        sendMessage(chatId, "🛑 **Scanning Stopped**\n\n" +
                          "Bot is now idle. Use `/scan` to resume monitoring.");
    }

    private void performScan(long chatId) {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));
        boolean isEquityOpen = now.isAfter(LocalTime.of(9, 0)) && now.isBefore(LocalTime.of(15, 30));
        if (!isEquityOpen) return;
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (!today.equals(lastResetDate)) {
            todayCallsGenerated = 0;
            slotsTriggered.clear();
            lastResetDate = today;
        }
        if (todayCallsGenerated >= 10) return;
        
        try {
            String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};
            for (String symbol : symbols) {
                if (!isScanning) break;
                scanEquitySymbol(chatId, symbol);
            }

        } catch (Exception e) {
            logger.error("Scan error: {}", e.getMessage());
        }
    }

    private void scanEquitySymbol(long chatId, String symbol) {
        try {
            List<SimpleMarketData> data5 = marketDataFetcher.getRealMarketData5Min(symbol);
            if (data5 == null || data5.isEmpty()) return;
            
            checkActiveSignal(chatId, symbol, data5);
            
            // Fetch Option Chain Data for higher confidence
            OptionData optionData = marketDataFetcher.fetchOptionData(symbol);
            
            AIPredictor.AIPrediction prediction5 = aiPredictor.generatePrediction(symbol, data5, optionData);
            
            List<SimpleMarketData> data1 = null;
            AIPredictor.AIPrediction prediction1 = null;
            try {
                data1 = marketDataFetcher.getRealMarketData(symbol);
                if (data1 != null && !data1.isEmpty()) {
                    prediction1 = aiPredictor.generatePrediction(symbol, data1, optionData);
                }
            } catch (Exception ex) {
                logger.error("Error generating 1-min prediction for {}: {}", symbol, ex.getMessage());
            }
            
            boolean fiveMinEligible = checkMinimumPoints(symbol, prediction5.estimatedMovePoints) && prediction5.confidence >= 70;
            boolean oneMinEligible = false;
            if (prediction1 != null) {
                oneMinEligible = checkMinimumPoints(symbol, prediction1.estimatedMovePoints) && prediction1.confidence >= 75;
            }
            
            AIPredictor.AIPrediction chosenPrediction = null;
            String timeframeLabel = null;
            double entryPrice = 0.0;
            
            if (fiveMinEligible) {
                chosenPrediction = prediction5;
                timeframeLabel = "5-min";
                entryPrice = data5.get(data5.size() - 1).price;
            } else if (oneMinEligible && data1 != null && !data1.isEmpty()) {
                chosenPrediction = prediction1;
                timeframeLabel = "1-min";
                entryPrice = data1.get(data1.size() - 1).price;
            }
            
            if (chosenPrediction == null) {
                return;
            }
            
            long currentTime = System.currentTimeMillis();
            long lastAlert = lastAlertTimeMap.getOrDefault(symbol, 0L);
            if (currentTime - lastAlert < 5 * 60 * 1000) return;
            // Removed slot restriction to allow 1-2 calls per segment as requested
            // int slot = getSlot(LocalTime.now(ZoneId.of("Asia/Kolkata")));
            // if (slotsTriggered.contains(slot)) return;

            double targetPoints = chosenPrediction.estimatedMovePoints;
            double targetPrice = chosenPrediction.predictedDirection.equals("UP") ? entryPrice + targetPoints : entryPrice - targetPoints;
            String arrow = chosenPrediction.predictedDirection.equals("UP") ? "⬆️" : "⬇️";
            String signalEmoji = chosenPrediction.predictedDirection.equals("UP") ? "🟢" : "🔴";
            
            double minPoints = switch (symbol) {
                case "NIFTY50" -> 30.0;
                case "SENSEX" -> 80.0;
                default -> 20.0;
            };
            
            // Format Greeks string
            String greeksInfo = "";
            if (chosenPrediction.greeks != null) {
                greeksInfo = String.format("   • Delta: %.2f | Gamma: %.4f\n", 
                    chosenPrediction.greeks.getOrDefault("delta", 0.0),
                    chosenPrediction.greeks.getOrDefault("gamma", 0.0));
            }

            // Check if data is fresh (Strict 10-minute limit for live alerts)
            SimpleMarketData latestData = data5 != null && !data5.isEmpty() ? data5.get(data5.size() - 1) : null;
            LocalDateTime dataTime = latestData != null ? latestData.timestamp : LocalDateTime.now();
            boolean isFresh = dataTime.isAfter(LocalDateTime.now().minusMinutes(10));
            
            if (!isFresh) {
                logger.warn("⚠️ Skipping alert for {} as data is stale (last update: {}). Current Time: {}", symbol, dataTime, LocalDateTime.now());
                return;
            }

            String alertId = "AL-" + (System.currentTimeMillis() % 10000);
            String timestamp = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

            String alert = signalEmoji + " **CONFIRMED CALL DETECTED** [" + alertId + "]\n" +
                          "📡 **Source:** Based on REAL-TIME Market Data\n\n" +
                          "📌 **Symbol:** " + symbol + "\n" +
                          "⏱️ **Timeframe:** " + timeframeLabel + "\n" +
                          "🚀 **Direction:** " + chosenPrediction.predictedDirection + " " + arrow + "\n" +
                          "🎯 **Projected Move:** " + String.format("%.0f", targetPoints) + " pts\n" +
                          "💰 **Entry Price:** " + String.format("%.0f", entryPrice) + "\n" +
                          "💰 **Target Price (Exit):** " + String.format("%.0f", targetPrice) + "\n" +
                          "🛡️ **Stop Loss:** " + String.format("%.0f", chosenPrediction.suggestedStopLoss) + " pts\n" +
                          "📏 **Threshold:** " + String.format("%.0f", minPoints) + " pts\n" +
                          "🤖 **AI Confidence:** " + String.format("%.1f%%", chosenPrediction.confidence) + "\n\n" +
                          "📊 **Option Metrics:**\n" +
                          "   • Put-Call Ratio (PCR): " + String.format("%.2f", chosenPrediction.pcr) + "\n" +
                          greeksInfo +
                          "📝 **Reasoning:** " + chosenPrediction.predictionReasoning + "\n\n" +
                          "🕒 **Alert Time:** " + timestamp + " IST";
            
            sendMessage(chatId, alert);
            lastAlertTimeMap.put(symbol, currentTime);
            todayCallsGenerated++;
            slotsTriggered.add(slot);
            
            ActiveSignal s = new ActiveSignal();
            s.symbol = symbol;
            s.direction = chosenPrediction.predictedDirection;
            s.entryPrice = entryPrice;
            s.targetPoints = chosenPrediction.estimatedMovePoints;
            s.stopLossPoints = chosenPrediction.suggestedStopLoss;
            s.createdAt = System.currentTimeMillis();
            activeSignals.put(symbol, s);
        } catch (Exception e) {
            logger.error("Error scanning equity " + symbol, e);
        }
    }

    private boolean checkMinimumPoints(String symbol, double estimatedPoints) {
        double minPoints = switch (symbol) {
            case "NIFTY50" -> 45.0; // Updated to match user requirements
            case "SENSEX" -> 120.0;
            case "BANKNIFTY" -> 100.0;
            default -> 20.0;
        };
        return estimatedPoints >= minPoints;
    }
    
    private int getSlot(LocalTime time) {
        // More granular slots for higher frequency
        int hour = time.getHour();
        if (hour < 11) return 0; // Morning (09:15 - 11:00)
        if (hour < 13) return 1; // Noon (11:00 - 13:00)
        if (hour < 15) return 2; // Afternoon (13:00 - 15:00)
        return 3; // Closing (15:00+)
    }
    
    /**
     * Handle token update command
     */
    protected void handleTokenCommand(long chatId, String command) {
        String[] parts = command.split(" ", 2);
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            return;
        }
        
        String newToken = parts[1].trim();
        marketDataFetcher.setAccessToken(newToken);
        
        String rates = getCurrentMarketRatesSimple();
        sendMessage(chatId, "✅ **Access Token Updated**\n\n" + rates);
    }

    // Final dead code removal

    
    /**
     * Send message to Telegram chat
     */
    protected void sendMessage(long chatId, String text) {
        try {
            long now = System.currentTimeMillis();
            String lastText = lastSentMessageByChat.get(chatId);
            Long lastTime = lastSentMessageTime.get(chatId);
            if (lastText != null && lastText.equals(text) && lastTime != null && (now - lastTime) < 10000) {
                return;
            }
            lastSentMessageByChat.put(chatId, text);
            lastSentMessageTime.put(chatId, now);
            
            boolean success = sendRequest(chatId, text, "Markdown");
            
            if (!success) {
                String plainText = text.replace("**", "").replace("__", "").replace("`", "");
                sendRequest(chatId, plainText, null);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error sending message: " + e.getMessage());
            logger.error("❌ Error sending message: {}", e.getMessage());
        }
    }

    protected boolean sendRequest(long chatId, String text, String parseMode) {
        if (text == null || text.trim().isEmpty()) return false;
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
                .timeout(java.time.Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(jsonData, StandardCharsets.UTF_8))
                .build();
                
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                logger.error("❌ Telegram API Error: {} - {}", response.statusCode(), response.body());
                return false;
            } else {
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
    
    private void scheduleDailyCleanup() {
        LocalTime cleanupTime = LocalTime.of(23, 59);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        LocalDateTime nextCleanup = now.with(cleanupTime);
        if (now.isAfter(nextCleanup)) {
            nextCleanup = nextCleanup.plusDays(1);
        }
        
        long initialDelay = java.time.Duration.between(now, nextCleanup).toSeconds();
        scheduler.scheduleAtFixedRate(() -> {
            logger.info("🕛 Executing daily token and market data cleanup...");
            marketDataFetcher.clearDailySession();
        }, initialDelay, 24 * 60 * 60, TimeUnit.SECONDS);
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
