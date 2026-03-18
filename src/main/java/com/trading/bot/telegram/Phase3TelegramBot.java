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
    
    public static class ActiveSignal {
        public String symbol;
        public String direction;
        public double entryPrice;
        public double targetPoints;
        public double stopLossPoints;
        public long createdAt;
    }

    private final Map<String, ActiveSignal> activeSignals = new ConcurrentHashMap<>();
    private java.time.LocalDate lastResetDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
    private final java.util.Set<Integer> slotsTriggered = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Per-symbol call tracking for daily guarantee mechanism
    private static final String CHAT_ID_FILE = "active_chat_id.txt";
    private final Map<String, Integer> todayCallsBySymbol = new ConcurrentHashMap<>();

    // Market movement monitoring: track prices for movement alerts
    private final Map<String, Double> movementBaselinePrice = new ConcurrentHashMap<>();
    private final Map<String, Long>   lastMovementAlertTime = new ConcurrentHashMap<>();
    // Movement alert thresholds (%)
    private static final double NIFTY_MOVE_THRESHOLD   = 0.35; // 0.35% ~ 85 pts on Nifty
    private static final double BANKNIFTY_MOVE_THRESHOLD = 0.40; // 0.40% ~ 200 pts on BankNifty
    private static final double SENSEX_MOVE_THRESHOLD  = 0.30; // 0.30% ~ 230 pts on Sensex

    /**
     * Checks if an active signal's target or SL has been hit.
     * Returns true if the signal was resolved (hit target or SL), false if still open.
     * On resolution: sends notification, clears cooldown so next scan fires immediately.
     */
    private boolean checkActiveSignal(long chatId, String symbol, List<SimpleMarketData> data) {
        ActiveSignal s = activeSignals.get(symbol);
        if (s == null || data.isEmpty()) return false;
        double entry = s.entryPrice;
        double target = s.targetPoints;
        double sl = s.stopLossPoints;
        double targetPrice = "UP".equalsIgnoreCase(s.direction) ? entry + target : entry - target;
        double slPrice     = "UP".equalsIgnoreCase(s.direction) ? entry - sl    : entry + sl;
        boolean hitTarget = false;
        boolean hitStop   = false;

        // FIX: Scan ALL candles from signal creation time, not just the last candle.
        // This ensures we never miss a target that was hit in a previous candle
        // (e.g., target hit at 10:02 AM but price pulled back before next scan at 10:06 AM).
        for (SimpleMarketData candle : data) {
            // Skip candles that were completed before this signal was created
            if (candle.timestamp != null) {
                long candleMillis = candle.timestamp
                    .atZone(java.time.ZoneId.of("Asia/Kolkata"))
                    .toInstant().toEpochMilli();
                if (candleMillis < s.createdAt - 60_000) continue; // allow 1-min overlap
            }
            if ("UP".equalsIgnoreCase(s.direction)) {
                // For UP signals: check target first (optimistic — target hit before SL)
                if (candle.high >= targetPrice) { hitTarget = true; break; }
                if (candle.low  <= slPrice)     { hitStop   = true; break; }
            } else {
                // For DOWN signals: check target first
                if (candle.low  <= targetPrice) { hitTarget = true; break; }
                if (candle.high >= slPrice)     { hitStop   = true; break; }
            }
        }

        if (hitTarget) {
            double pnlPoints = target;
            long durationMin = (System.currentTimeMillis() - s.createdAt) / 60000;
            String msg = String.format(
                "✅ *TARGET HIT — BOOK PROFIT!* 🎉\n\n" +
                "📌 *Symbol:* %s\n" +
                "📈 *Direction:* %s\n" +
                "💰 *Entry:* %.2f\n" +
                "🎯 *Target:* %.2f  ← *ACHIEVED*\n" +
                "📊 *Points Captured:* +%.0f pts\n" +
                "⏱️ *Duration:* %d min\n\n" +
                "💰 Position closed. Re-scanning for next opportunity...",
                symbol, s.direction, entry, targetPrice, pnlPoints, durationMin);
            sendMessage(chatId, msg);
            activeSignals.remove(symbol);
            lastAlertTimeMap.remove(symbol);   // reset cooldown → scan again immediately
            return true;
        }

        if (hitStop) {
            double lossPoints = sl;
            long durationMin = (System.currentTimeMillis() - s.createdAt) / 60000;
            String msg = String.format(
                "🛑 *STOP LOSS HIT — EXIT NOW!*\n\n" +
                "📌 *Symbol:* %s\n" +
                "📈 *Direction:* %s\n" +
                "💰 *Entry:* %.2f\n" +
                "🛑 *SL Level:* %.2f  ← *TRIGGERED*\n" +
                "📊 *Points Lost:* -%.0f pts\n" +
                "⏱️ *Duration:* %d min\n\n" +
                "⚠️ Trend invalidated. Re-scanning for next opportunity...",
                symbol, s.direction, entry, slPrice, lossPoints, durationMin);
            sendMessage(chatId, msg);
            activeSignals.remove(symbol);
            lastAlertTimeMap.remove(symbol);   // reset cooldown → scan again immediately
            return true;
        }

        return false;  // signal still open
    }
    private ScheduledFuture<?> scanFuture;
    private final Map<Long, String> pendingCommands = new ConcurrentHashMap<>();
    
    public Phase3TelegramBot() {
        this(false);
    }

    public Phase3TelegramBot(boolean testMode) {
        this.httpClient = HttpClient.newHttpClient();
        this.scheduler = Executors.newScheduledThreadPool(4);
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
        logger.info("🚀 Starting Phase 3 Telegram Bot V29...");

        // Restore today's signal counts/cooldowns so restarts don't duplicate signals
        loadDailyState();

        // Silent Startup - No spamming user on restart
        // sendStartupMessage();
        
        // Start message polling
        scheduler.scheduleWithFixedDelay(this::checkForMessages, 0, 2, TimeUnit.SECONDS);

        // Market movement monitor — runs every 2 minutes during market hours
        scheduler.scheduleWithFixedDelay(this::monitorMarketMovement, 30, 120, TimeUnit.SECONDS);

        // Schedule daily cleanup at 11:59 PM
        scheduleDailyCleanup();
        
        logger.info("✅ Phase 3 Telegram Bot started successfully");
        logger.info("🏦 Available features: Smart Money Analysis, Order Blocks, FVGs, Liquidity Analysis");

        // Auto-resume scan for the last known chatId (survives restarts)
        long savedChatId = loadChatId();
        if (savedChatId != 0) {
            activeChatId = savedChatId;
            logger.info("📡 Auto-resuming market scan for saved chatId: {}", savedChatId);
            scheduler.schedule(() -> startScanSilently(savedChatId), 15, TimeUnit.SECONDS);
        } else {
            logger.info("📱 Send /start then /scan to begin market scanning");
        }
    }
    
    // -----------------------------------------------------------------------
    // API Getters (used by TradingApiServer)
    // -----------------------------------------------------------------------

    public Map<String, ActiveSignal> getActiveSignals() {
        return Collections.unmodifiableMap(activeSignals);
    }

    public boolean isScanning() {
        return isScanning;
    }

    public int getTodayCallsGenerated() {
        return todayCallsGenerated;
    }

    // -----------------------------------------------------------------------

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
                            // Filter out messages older than 90 seconds — prevents replaying old
                            // commands after a bot restart (lastUpdateId resets to 0 on restart).
                            long msgDate = extractMessageDate(update); // Unix epoch seconds
                            long nowSec  = System.currentTimeMillis() / 1000;
                            if (msgDate > 0 && (nowSec - msgDate) > 90) {
                                logger.info("⏭ Skipping old message ({}s ago): {}", nowSec - msgDate, text);
                                continue;
                            }
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
        activeChatId = chatId;
        saveChatId(chatId);
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
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

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
            todayCallsBySymbol.clear();
            lastResetDate = today;
        }

        String rates = getCurrentMarketRatesSimple();
        String status = "📊 **Market Status Report**\n\n" + 
                       rates + "\n\n" +
                       "📢 **Today's Activity**\n" +
                       "• Calls Generated: `" + todayCallsGenerated + "`\n" +
                       "• Active Slots: `" + slotsTriggered.size() + "/4`\n" +
                       "• Bot Instance: `V29.0-INSTITUTIONAL` (70%+ WR All Segments)";
        
        sendMessage(chatId, status);
    }
    
    /**
     * Handle /scan command
     */
    protected void handleScanCommand(long chatId) {
        activeChatId = chatId;
        saveChatId(chatId);
        if (isScanning && scanFuture != null && !scanFuture.isCancelled() && !scanFuture.isDone()) {
            sendMessage(chatId, "✅ Already started scanning\n\n" +
                              "📡 Bot is monitoring NIFTY50, SENSEX, BANKNIFTY.\n" +
                              "🔔 You will be notified when a signal is found.");
            return;
        }
        startScanSilently(chatId);
        sendMessage(chatId, "🔍 Now I will start scanning\n\n" +
                          "📡 Monitoring NIFTY50, SENSEX, BANKNIFTY...\n" +
                          "🤖 AI analyzing patterns...\n" +
                          "🔔 You will be notified of high-confidence signals.");
    }

    /** Starts the scan scheduler without sending any Telegram message. Used for auto-resume on restart. */
    private void startScanSilently(long chatId) {
        activeChatId = chatId;
        if (isScanning && scanFuture != null && !scanFuture.isCancelled() && !scanFuture.isDone()) {
            return; // already running, nothing to do
        }
        isScanning = true;
        if (scanFuture != null) {
            scanFuture.cancel(true);
        }
        // Schedule scanning task — 30s interval for constant market monitoring
        scanFuture = scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (!isScanning) return;
                performScan(chatId);
            } catch (Exception e) {
                logger.error("Critical error in scanning task: {}", e.getMessage());
            }
        }, 5, 30, TimeUnit.SECONDS);
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
        boolean isEquityOpen = now.isAfter(LocalTime.of(9, 15)) && now.isBefore(LocalTime.of(15, 30));
        if (!isEquityOpen) return;
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (!today.equals(lastResetDate)) {
            todayCallsGenerated = 0;
            slotsTriggered.clear();
            todayCallsBySymbol.clear();
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

            // If an active signal exists for this symbol, only monitor it.
            // If it resolves (target/SL hit), schedule an immediate rescan for this symbol.
            if (activeSignals.containsKey(symbol)) {
                boolean resolved = checkActiveSignal(chatId, symbol, data5);
                if (resolved) {
                    // Rescan this symbol immediately (2-second delay to allow data refresh)
                    scheduler.schedule(() -> scanEquitySymbol(chatId, symbol), 2, TimeUnit.SECONDS);
                }
                return;  // don't generate a new signal while previous one is open
            }
            
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
            
            boolean fiveMinEligible = checkMinimumPoints(symbol, prediction5.estimatedMovePoints) && prediction5.confidence >= 65;
            boolean oneMinEligible = false;
            if (prediction1 != null) {
                oneMinEligible = checkMinimumPoints(symbol, prediction1.estimatedMovePoints) && prediction1.confidence >= 68;
            }
            
            AIPredictor.AIPrediction chosenPrediction = null;
            String timeframeLabel = null;
            double entryPrice = 0.0;

            // MTF conflict check: skip primary signal when 5-min and 1-min actively disagree
            // (one says UP, other says DOWN) — conflicting timeframes = unreliable entry
            boolean mtfConflict = prediction1 != null
                    && !"NEUTRAL".equals(prediction5.predictedDirection)
                    && !"NEUTRAL".equals(prediction1.predictedDirection)
                    && !prediction5.predictedDirection.equals(prediction1.predictedDirection);

            if (mtfConflict) {
                logger.info("🔀 MTF conflict for {} (5min={} vs 1min={}), skipping primary signal",
                    symbol, prediction5.predictedDirection, prediction1.predictedDirection);
            } else if (fiveMinEligible) {
                chosenPrediction = prediction5;
                boolean mtfConfirmed = prediction1 != null
                        && prediction5.predictedDirection.equals(prediction1.predictedDirection);
                timeframeLabel = mtfConfirmed ? "5-min [MTF✓]" : "5-min";
                entryPrice = data5.get(data5.size() - 1).price;
            } else if (oneMinEligible && data1 != null && !data1.isEmpty()) {
                chosenPrediction = prediction1;
                timeframeLabel = "1-min";
                entryPrice = data1.get(data1.size() - 1).price;
            }
            
            if (chosenPrediction == null) {
                // Daily guarantee: after 11:30 AM, if no call for this symbol today, use relaxed EMA signal
                LocalTime nowIst = LocalTime.now(ZoneId.of("Asia/Kolkata"));
                boolean noCallToday = todayCallsBySymbol.getOrDefault(symbol, 0) == 0;
                boolean isGuaranteeWindow = nowIst.isAfter(LocalTime.of(10, 30)) && nowIst.isBefore(LocalTime.of(14, 45));

                if (noCallToday && isGuaranteeWindow) {
                    List<SimpleMarketData> dataForGuarantee = (data5 != null && !data5.isEmpty()) ? data5 : data1;
                    if (dataForGuarantee != null && !dataForGuarantee.isEmpty()) {
                        AIPredictor.AIPrediction relaxed = aiPredictor.generateRelaxedPrediction(symbol, dataForGuarantee, optionData);
                        if (!"NEUTRAL".equals(relaxed.predictedDirection)) {
                            chosenPrediction = relaxed;
                            timeframeLabel = "5-min";
                            entryPrice = dataForGuarantee.get(dataForGuarantee.size() - 1).price;
                            logger.info("📅 Guarantee signal triggered for {} at {}", symbol, nowIst);
                        }
                    }
                }

                if (chosenPrediction == null) return;
            }

            long currentTime = System.currentTimeMillis();
            long lastAlert = lastAlertTimeMap.getOrDefault(symbol, 0L);
            if (currentTime - lastAlert < 3 * 60 * 1000) return; // 3-min cooldown per symbol
            // Removed slot restriction to allow 1-2 calls per segment as requested
            // int slot = getSlot(LocalTime.now(ZoneId.of("Asia/Kolkata")));
            // if (slotsTriggered.contains(slot)) return;

            double targetPoints = chosenPrediction.estimatedMovePoints;
            double targetPrice = chosenPrediction.predictedDirection.equals("UP") ? entryPrice + targetPoints : entryPrice - targetPoints;
            String arrow = chosenPrediction.predictedDirection.equals("UP") ? "⬆️" : "⬇️";
            String signalEmoji = chosenPrediction.predictedDirection.equals("UP") ? "🟢" : "🔴";
            
            double minPoints = switch (symbol) {
                case "NIFTY50"   -> 25.0;
                case "SENSEX"    -> 60.0;
                case "BANKNIFTY" -> 70.0;
                default          -> 20.0;
            };
            
            // Format Greeks string
            String greeksInfo = "";
            if (chosenPrediction.greeks != null) {
                greeksInfo = String.format("   • Delta: %.2f | Gamma: %.4f\n", 
                    chosenPrediction.greeks.getOrDefault("delta", 0.0),
                    chosenPrediction.greeks.getOrDefault("gamma", 0.0));
            }

            // Check if data is fresh — use IST for comparison (data timestamps are IST)
            SimpleMarketData latestData = data5 != null && !data5.isEmpty() ? data5.get(data5.size() - 1) : null;
            LocalDateTime nowIst = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
            LocalDateTime dataTime = latestData != null ? latestData.timestamp : nowIst;
            boolean isFresh = dataTime.isAfter(nowIst.minusMinutes(10));

            if (!isFresh) {
                logger.warn("⚠️ Skipping alert for {} — stale data (last: {} IST, now: {} IST)", symbol, dataTime, nowIst);
                return;
            }

            String alertId = "AL-" + (System.currentTimeMillis() % 10000);
            String timestamp = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));

            // Get absolute current LTP for comparison
            double currentLTP = entryPrice; // Default to entry price
            try {
                Map<String, Double> snapshot = marketDataFetcher.getHonestMarketSnapshot();
                if (snapshot.containsKey(symbol)) {
                    currentLTP = snapshot.get(symbol);
                }
            } catch (Exception e) {
                logger.warn("Could not fetch latest LTP for alert comparison: {}", e.getMessage());
            }

            double rrRatio = chosenPrediction.suggestedStopLoss > 0
                ? chosenPrediction.estimatedMovePoints / chosenPrediction.suggestedStopLoss : 0;

            double slAbsPrice = "UP".equals(chosenPrediction.predictedDirection)
                ? entryPrice - chosenPrediction.suggestedStopLoss
                : entryPrice + chosenPrediction.suggestedStopLoss;

            String alert = signalEmoji + " *TRADE SIGNAL* [" + alertId + "]\n" +
                          "🕒 *Time:* " + timestamp + " IST\n" +
                          "📡 *Source:* REAL-TIME Data\n\n" +
                          "📌 *Symbol:* " + symbol + "\n" +
                          "⏱️ *Timeframe:* " + timeframeLabel + "\n" +
                          "🚀 *Direction:* " + chosenPrediction.predictedDirection + " " + arrow + "\n\n" +
                          "💰 *Entry:* " + String.format("%.2f", entryPrice) + "\n" +
                          "🎯 *Target:* " + String.format("%.2f", targetPrice) +
                              "  (+" + String.format("%.0f", targetPoints) + " pts)\n" +
                          "🛑 *Stop Loss:* " + String.format("%.2f", slAbsPrice) +
                              "  (-" + String.format("%.0f", chosenPrediction.suggestedStopLoss) + " pts)\n" +
                          "📊 *R:R:* 1:" + String.format("%.1f", rrRatio) + "\n\n" +
                          "🤖 *AI Confidence:* " + String.format("%.1f%%", chosenPrediction.confidence) + "\n" +
                          "📊 *PCR:* " + String.format("%.2f", chosenPrediction.pcr) + "\n" +
                          greeksInfo +
                          "📝 *Reason:* " + chosenPrediction.predictionReasoning;
            
            sendMessage(chatId, alert);
            lastAlertTimeMap.put(symbol, currentTime);
            todayCallsGenerated++;
            todayCallsBySymbol.merge(symbol, 1, Integer::sum);
            saveDailyState(); // persist so restarts don't duplicate signals
            // slotsTriggered.add(slot); // Slot restriction removed
            
            ActiveSignal s = new ActiveSignal();
            s.symbol = symbol;
            s.direction = chosenPrediction.predictedDirection;
            s.entryPrice = entryPrice;
            s.targetPoints = chosenPrediction.estimatedMovePoints;
            s.stopLossPoints = chosenPrediction.suggestedStopLoss;
            s.createdAt = System.currentTimeMillis();
            activeSignals.put(symbol, s);

            // Record signal in history for REST API
            try {
                Map<String, Object> histEntry = new LinkedHashMap<>();
                histEntry.put("symbol", symbol);
                histEntry.put("direction", chosenPrediction.predictedDirection);
                histEntry.put("entryPrice", entryPrice);
                histEntry.put("targetPoints", chosenPrediction.estimatedMovePoints);
                histEntry.put("stopLossPoints", chosenPrediction.suggestedStopLoss);
                histEntry.put("confidence", chosenPrediction.confidence);
                histEntry.put("pcr", chosenPrediction.pcr);
                histEntry.put("createdAt", System.currentTimeMillis());
                histEntry.put("status", "OPEN");
                // Signal history logged internally
                logger.info("Signal history stored: {} {} entry={} target={} sl={}",
                    symbol, histEntry.get("direction"), histEntry.get("entryPrice"),
                    histEntry.get("targetPoints"), histEntry.get("stopLossPoints"));
            } catch (Exception histEx) {
                logger.warn("Could not log signal history: {}", histEx.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error scanning equity " + symbol, e);
        }
    }

    /**
     * Market movement monitor — runs every 2 minutes.
     * Sends alerts when NIFTY50 / BANKNIFTY / SENSEX move significantly
     * (>= threshold %) compared to the price captured at the start of each
     * monitoring window. Alerts at most once every 10 minutes per symbol.
     */
    private void monitorMarketMovement() {
        if (activeChatId == 0) return; // No chat registered yet

        java.time.LocalTime now = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        boolean isMarketOpen = now.isAfter(java.time.LocalTime.of(9, 15))
                            && now.isBefore(java.time.LocalTime.of(15, 35));
        if (!isMarketOpen) return;

        String[] symbols = {"NIFTY50", "BANKNIFTY", "SENSEX"};
        for (String sym : symbols) {
            try {
                List<SimpleMarketData> data = marketDataFetcher.getRealMarketData5Min(sym);
                if (data == null || data.isEmpty()) continue;
                double currentPrice = data.get(data.size() - 1).price;
                if (currentPrice <= 0) continue;

                // Establish baseline on first call
                movementBaselinePrice.putIfAbsent(sym, currentPrice);
                double baseline = movementBaselinePrice.get(sym);

                double changePct = Math.abs((currentPrice - baseline) / baseline * 100.0);
                double threshold = switch (sym) {
                    case "NIFTY50"   -> NIFTY_MOVE_THRESHOLD;
                    case "BANKNIFTY" -> BANKNIFTY_MOVE_THRESHOLD;
                    case "SENSEX"    -> SENSEX_MOVE_THRESHOLD;
                    default          -> 0.40;
                };

                // Minimum 10 minutes between movement alerts for same symbol
                long lastAlert = lastMovementAlertTime.getOrDefault(sym, 0L);
                boolean cooldownOk = (System.currentTimeMillis() - lastAlert) > 10 * 60 * 1000L;

                if (changePct >= threshold && cooldownOk) {
                    double changePoints = currentPrice - baseline;
                    String direction = changePoints > 0 ? "📈 UP" : "📉 DOWN";
                    String emoji = changePoints > 0 ? "🟢" : "🔴";
                    String changeStr = (changePoints > 0 ? "+" : "") + String.format("%.0f", changePoints);
                    String symLabel = switch (sym) {
                        case "NIFTY50"   -> "NIFTY 50";
                        case "BANKNIFTY" -> "BANK NIFTY";
                        case "SENSEX"    -> "SENSEX";
                        default          -> sym;
                    };
                    String msg = emoji + " *MARKET MOVEMENT ALERT*\n\n" +
                        "📌 *Index:* " + symLabel + "\n" +
                        "🚀 *Move:* " + direction + "\n" +
                        "📊 *Change:* " + changeStr + " pts (" + String.format("%.2f%%", Math.abs(changePct)) + ")\n" +
                        "💰 *Current Price:* " + String.format("%.2f", currentPrice) + "\n" +
                        "📍 *From:* " + String.format("%.2f", baseline) + "\n" +
                        "🕒 *Time:* " + now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " IST";
                    sendMessage(activeChatId, msg);
                    lastMovementAlertTime.put(sym, System.currentTimeMillis());
                    // Reset baseline after alert so next move is measured from here
                    movementBaselinePrice.put(sym, currentPrice);
                    logger.info("📡 Movement alert sent for {}: {} pts ({} %)",
                        sym, changeStr, String.format("%.2f", changePct));
                }

                // Reset baseline every 15 minutes to keep alerts relevant
                if ((System.currentTimeMillis() - lastAlert) > 15 * 60 * 1000L && changePct < threshold) {
                    movementBaselinePrice.put(sym, currentPrice);
                }

            } catch (Exception e) {
                logger.warn("Movement monitor error for {}: {}", sym, e.getMessage());
            }
        }
    }

    private boolean checkMinimumPoints(String symbol, double estimatedPoints) {
        // Thresholds calibrated to ATR*2.0 typical output per symbol on 5-min candles
        double minPoints = switch (symbol) {
            case "NIFTY50"   -> 25.0;
            case "SENSEX"    -> 60.0;
            case "BANKNIFTY" -> 70.0;
            default          -> 20.0;
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
    
    private long extractMessageDate(String update) {
        try {
            // Telegram message JSON: ..."date":1234567890,...
            String marker = "\"date\":";
            int idx = update.indexOf(marker);
            if (idx == -1) return 0;
            String sub = update.substring(idx + marker.length()).trim();
            int end = 0;
            while (end < sub.length() && Character.isDigit(sub.charAt(end))) end++;
            return Long.parseLong(sub.substring(0, end));
        } catch (Exception e) {
            return 0;
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

    /** Persist chatId to disk so scan auto-resumes after restart. */
    private void saveChatId(long chatId) {
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(CHAT_ID_FILE), String.valueOf(chatId));
        } catch (Exception e) {
            logger.warn("Could not save chatId: {}", e.getMessage());
        }
    }

    /** Load previously saved chatId, or return 0 if none. */
    private long loadChatId() {
        try {
            java.io.File f = new java.io.File(CHAT_ID_FILE);
            if (f.exists()) {
                String s = java.nio.file.Files.readString(f.toPath()).trim();
                if (!s.isEmpty()) return Long.parseLong(s);
            }
        } catch (Exception e) {
            logger.warn("Could not load chatId: {}", e.getMessage());
        }
        return 0;
    }

    private static final String DAILY_STATE_FILE = "daily_signal_state.properties";

    /** Save today's signal counts and cooldown timestamps to disk (survives restarts). */
    private void saveDailyState() {
        try {
            String today = java.time.LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
            java.util.Properties p = new java.util.Properties();
            p.setProperty("date", today);
            p.setProperty("totalCalls", String.valueOf(todayCallsGenerated));
            todayCallsBySymbol.forEach((sym, cnt) -> p.setProperty("calls." + sym, String.valueOf(cnt)));
            lastAlertTimeMap.forEach((sym, ts) -> p.setProperty("lastAlert." + sym, String.valueOf(ts)));
            try (java.io.OutputStream os = new java.io.FileOutputStream(DAILY_STATE_FILE)) {
                p.store(os, "Daily signal state — auto-generated");
            }
        } catch (Exception e) {
            logger.warn("Could not save daily state: {}", e.getMessage());
        }
    }

    /** Load daily signal state if it was saved today (prevents duplicate signals across restarts). */
    private void loadDailyState() {
        try {
            java.io.File f = new java.io.File(DAILY_STATE_FILE);
            if (!f.exists()) return;
            java.util.Properties p = new java.util.Properties();
            try (java.io.InputStream is = new java.io.FileInputStream(f)) { p.load(is); }
            String savedDate = p.getProperty("date", "");
            String today = java.time.LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
            if (!today.equals(savedDate)) {
                logger.info("📅 Daily state is from {} — ignoring (new day)", savedDate);
                return;
            }
            todayCallsGenerated = Integer.parseInt(p.getProperty("totalCalls", "0"));
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("calls."))
                    todayCallsBySymbol.put(key.substring(6), Integer.parseInt(p.getProperty(key)));
                if (key.startsWith("lastAlert."))
                    lastAlertTimeMap.put(key.substring(10), Long.parseLong(p.getProperty(key)));
            }
            logger.info("✅ Loaded daily state: {} calls today, cooldowns restored", todayCallsGenerated);
        } catch (Exception e) {
            logger.warn("Could not load daily state: {}", e.getMessage());
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
