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
    private final java.util.concurrent.atomic.AtomicInteger todayCallsGenerated = new java.util.concurrent.atomic.AtomicInteger(0);
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
        public double confidence;   // AI confidence % at signal time
    }

    private final Map<String, ActiveSignal> activeSignals = new ConcurrentHashMap<>();
    private java.time.LocalDate lastResetDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
    private final java.util.Set<Integer> slotsTriggered = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Per-symbol call tracking for daily guarantee mechanism
    private static final String CHAT_ID_FILE  = "active_chat_id.txt";
    private static final String SIGNAL_LOG_FILE = "signal_log.csv";
    private final Map<String, Integer> todayCallsBySymbol = new ConcurrentHashMap<>();

    // Expiry session cooldown — separate from regular cooldown (20-min per symbol)
    private final Map<String, Long> expiryLastAlertMap = new ConcurrentHashMap<>();

    // 1-min education signal cooldown — 5-min per symbol (independent of all other cooldowns)
    private final Map<String, Long> edu1MinLastAlertMap = new ConcurrentHashMap<>();

    // Market movement monitoring: track prices for movement alerts
    private final Map<String, Double> movementBaselinePrice = new ConcurrentHashMap<>();
    private final Map<String, Long>   lastMovementAlertTime = new ConcurrentHashMap<>();
    // Movement alert thresholds (%)
    private static final double NIFTY_MOVE_THRESHOLD   = 0.35; // 0.35% ~ 85 pts on Nifty
    private static final double SENSEX_MOVE_THRESHOLD  = 0.30; // 0.30% ~ 230 pts on Sensex
    private static final double BANKNIFTY_MOVE_THRESHOLD = 0.40; // 0.40% ~ 200 pts on BankNifty

    // ADX trend state alerts — "STRONG", "CHOPPY", "NORMAL"
    private final Map<String, Long>   lastTrendAlertTime  = new ConcurrentHashMap<>();
    private final Map<String, String> lastTrendState      = new ConcurrentHashMap<>();

    /**
     * Cache of chatId → Telegram display name.
     * Populated in parseAndHandleUpdates from the "from" object in each message.
     * Used by handleJoinCommand so the admin sees the real name, not "User#id".
     */
    private final Map<Long, String> telegramDisplayNames = new ConcurrentHashMap<>();

    // ── Access control ────────────────────────────────────────────────────────
    /** Manages admin / approved-user / pending-request state. */
    private final UserManager userManager = new UserManager();
    /**
     * Approved users (including admin) who have sent /scan and want to receive
     * signals. Populated on /scan, cleared on /stop.
     */
    private final Set<Long> subscribedUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());

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
            broadcastSignal(msg);
            updateSignalLog(symbol, s.createdAt, "WIN", pnlPoints, System.currentTimeMillis());
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
            broadcastSignal(msg);
            updateSignalLog(symbol, s.createdAt, "LOSS", -lossPoints, System.currentTimeMillis());
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

        // Schedule daily cleanup at 11:59 PM (memory-only clear; disk history preserved)
        scheduleDailyCleanup();

        // Schedule end-of-day data snapshot at 3:35 PM IST to capture today's full session
        scheduleEndOfDaySnapshot();

        // Schedule pre-market summary at 9:15 AM IST (PDH/PDL, gap %, key levels)
        schedulePreMarketSummary();
        
        logger.info("✅ Phase 3 Telegram Bot started successfully");
        logger.info("🏦 Available features: Smart Money Analysis, Order Blocks, FVGs, Liquidity Analysis");

        // Admin is always subscribed so signals reach them after any restart
        subscribedUsers.add(userManager.getAdminChatId());

        // Auto-resume scan for the last known chatId (survives restarts)
        long savedChatId = loadChatId();
        if (savedChatId != 0) {
            activeChatId = savedChatId;
            subscribedUsers.add(savedChatId);  // re-subscribe whoever was scanning before restart
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
        return todayCallsGenerated.get();
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
                        
                        // Extract chatId, messageText, and user's Telegram display name
                        long chatId = extractChatId(update);
                        String text = extractMessageText(update);

                        // Cache the real Telegram name (first_name + last_name + @username)
                        // so handleJoinCommand can show it to the admin automatically.
                        if (chatId != 0) {
                            String displayName = extractDisplayName(update, chatId);
                            telegramDisplayNames.put(chatId, displayName);
                        }

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
        String command = text.trim();
        String cmdKey  = command.toLowerCase().split("\\s+")[0];

        // Dedup: ignore identical command repeated within 5 s
        String lastCmd = pendingCommands.get(chatId);
        if (command.equals(lastCmd)) return;
        pendingCommands.put(chatId, command);

        logger.info("📩 Received command from {}: {}", chatId, command);

        // ── /start and /join are open to every user ───────────────────────────
        if (cmdKey.startsWith("/start")) {
            handleStartCommand(chatId);

        } else if (cmdKey.startsWith("/join")) {
            handleJoinCommand(chatId, command);

        // ── Admin-only commands ────────────────────────────────────────────────
        } else if (cmdKey.startsWith("/approve") || cmdKey.startsWith("/reject")
                || cmdKey.startsWith("/revoke")  || cmdKey.startsWith("/users")
                || cmdKey.startsWith("/pending") || cmdKey.startsWith("/token")) {

            if (!userManager.isAdmin(chatId)) {
                sendMessage(chatId, "❌ *Access Denied*\nThis command is for admin only.");
            } else {
                if      (cmdKey.startsWith("/token"))   handleTokenCommand(chatId, command);
                else if (cmdKey.startsWith("/approve")) handleApproveCommand(chatId, command);
                else if (cmdKey.startsWith("/reject"))  handleRejectCommand(chatId, command);
                else if (cmdKey.startsWith("/revoke"))  handleRevokeCommand(chatId, command);
                else if (cmdKey.startsWith("/users"))   handleUsersCommand(chatId);
                else if (cmdKey.startsWith("/pending")) handlePendingCommand(chatId);
            }

        // ── Approved-user commands ─────────────────────────────────────────────
        } else if (!userManager.isApproved(chatId)) {
            sendMessage(chatId,
                "🔒 *Access Restricted*\n\n" +
                "You need approval to use this bot.\n" +
                "Send /join to request access.");

        } else {
            if      (cmdKey.startsWith("/scan"))      handleScanCommand(chatId);
            else if (cmdKey.startsWith("/stop"))      handleStopScanCommand(chatId);
            else if (cmdKey.startsWith("/status"))    handleStatusCommand(chatId);
            else if (cmdKey.startsWith("/today"))
                sendMessage(chatId, buildDayReport(java.time.LocalDate.now(ZoneId.of("Asia/Kolkata"))));
            else if (cmdKey.startsWith("/yesterday"))
                sendMessage(chatId, buildDayReport(java.time.LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1)));
        }

        scheduler.schedule(() -> pendingCommands.remove(chatId), 5, TimeUnit.SECONDS);
    }
    
    /**
     * Legacy entry point — delegates to processCommand so all permission
     * checks are applied consistently.
     */
    protected void handleCommand(long chatId, String command) {
        processCommand(chatId, command);
    }
    
    /**
     * Handle /start command.
     * Approved users get the normal welcome.
     * Unknown users get a join prompt — they cannot access any trading feature.
     */
    protected void handleStartCommand(long chatId) {
        if (userManager.isApproved(chatId)) {
            activeChatId = chatId;
            saveChatId(chatId);
            sendMessage(chatId,
                "👋 *Welcome to Institutional Trading Bot*\n\n" +
                "🚀 *System Online & Ready*\n" +
                "📊 *Market Analysis:* Active\n" +
                "🤖 *AI Prediction:* Enabled\n\n" +
                "Use `/scan` to start receiving trading signals.");
        } else {
            sendMessage(chatId,
                "👋 *Welcome to Institutional Trading Bot*\n\n" +
                "🔒 This bot is *access-controlled*.\n\n" +
                "📩 To request access, send:\n`/join`\n\n" +
                "Your request will be reviewed by the admin.\n" +
                "_Your Chat ID: `" + chatId + "`_");
        }
    }
    
    // Dead code removed

    
    protected String getCurrentMarketRatesSimple() {
        try {
            Map<String, Double> prices = marketDataFetcher.getHonestMarketSnapshot();
            StringBuilder sb = new StringBuilder();
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

            for (String symbol : Arrays.asList("NIFTY50", "SENSEX")) {
                if (prices.containsKey(symbol)) {
                    LocalDateTime lastTime = marketDataFetcher.getLastValidTime(symbol);
                    boolean isFresh = lastTime != null && lastTime.isAfter(now.minusMinutes(5));
                    
                    String emoji = switch (symbol) {
                        case "NIFTY50" -> "📉";
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
        // Daily reset safety check
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (!today.equals(lastResetDate)) {
            todayCallsGenerated.set(0);
            slotsTriggered.clear();
            todayCallsBySymbol.clear();
            lastResetDate = today;
        }

        String rates = getCurrentMarketRatesSimple();
        String todayStr = today.toString();
        long nowMs = System.currentTimeMillis();

        // Read today's signals from CSV (source of truth — survives restarts)
        int total = 0, wins = 0, losses = 0, open = 0;
        double netPoints = 0;
        StringBuilder callDetail = new StringBuilder();

        try {
            java.nio.file.Path path = java.nio.file.Path.of(SIGNAL_LOG_FILE);
            if (path.toFile().exists()) {
                List<String> lines = java.nio.file.Files.readAllLines(path);
                int callNo = 0;
                for (String line : lines) {
                    String[] p = line.split(",", -1);
                    if (p.length < 10 || !p[0].equals(todayStr)) continue;
                    callNo++;
                    total++;

                    String sym     = p[2];
                    String dir     = p[3];
                    String entry   = p[4];
                    String tgtPts  = p[5];
                    String slPts   = p[6];
                    String status  = p[7];
                    double pts     = Double.parseDouble(p[8]);
                    long createdAt = Long.parseLong(p[9]);
                    double conf    = (p.length >= 11 && !p[10].isEmpty()) ? Double.parseDouble(p[10]) : 0.0;
                    long resolvedAt= (p.length >= 12 && !p[11].isEmpty()) ? Long.parseLong(p[11]) : 0L;

                    netPoints += pts;

                    // Entry time (HH:mm from col 1)
                    String entryTime = p[1];

                    // Status icon + outcome line
                    String icon;
                    String outcomeStr;
                    if ("WIN".equals(status)) {
                        wins++;
                        icon = "✅";
                        long durMin = resolvedAt > 0 ? (resolvedAt - createdAt) / 60000 : 0;
                        String resolvedTime = resolvedAt > 0
                            ? java.time.Instant.ofEpochMilli(resolvedAt)
                                .atZone(ZoneId.of("Asia/Kolkata"))
                                .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                            : "?";
                        outcomeStr = String.format("WIN +%.0f pts | %d min | hit @ %s", pts, durMin, resolvedTime);
                    } else if ("LOSS".equals(status)) {
                        losses++;
                        icon = "❌";
                        long durMin = resolvedAt > 0 ? (resolvedAt - createdAt) / 60000 : 0;
                        String resolvedTime = resolvedAt > 0
                            ? java.time.Instant.ofEpochMilli(resolvedAt)
                                .atZone(ZoneId.of("Asia/Kolkata"))
                                .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                            : "?";
                        outcomeStr = String.format("LOSS %.0f pts | %d min | hit @ %s", pts, durMin, resolvedTime);
                    } else {
                        open++;
                        icon = "🔄";
                        long runMin = (nowMs - createdAt) / 60000;
                        outcomeStr = String.format("OPEN — running %d min", runMin);
                    }

                    String confStr = conf > 0 ? String.format("%.1f%%", conf) : "N/A";
                    String dirArrow = "UP".equals(dir) ? "⬆" : "⬇";

                    callDetail.append(String.format(
                        "%d. %s *%s* %s %s @ `%s` [%s]\n" +
                        "   Tgt: +%s pts | SL: %s pts | Conf: %s\n" +
                        "   %s %s\n",
                        callNo, icon, sym, dir, dirArrow, entry, entryTime,
                        tgtPts.contains(".") ? String.valueOf((int) Double.parseDouble(tgtPts)) : tgtPts,
                        slPts.contains(".")  ? String.valueOf((int) Double.parseDouble(slPts))  : slPts,
                        confStr, icon, outcomeStr));
                }
            }
        } catch (Exception e) {
            logger.warn("Error reading signal log for /status: {}", e.getMessage());
        }

        int resolved = wins + losses;
        double winRate = resolved > 0 ? (wins * 100.0 / resolved) : 0;

        String scanState = isScanning ? "🟢 Active" : "🔴 Stopped";

        StringBuilder status = new StringBuilder();
        status.append("📊 *Market Status Report*\n\n");
        status.append(rates).append("\n\n");
        status.append("📢 *Today's Activity (").append(todayStr).append(")*\n");
        status.append("┌─────────────────────────\n");
        status.append(String.format("│ 📞 Calls Generated : *%d*%n", total));
        status.append(String.format("│ ✅ Wins            : *%d*%n", wins));
        status.append(String.format("│ ❌ Losses          : *%d*%n", losses));
        status.append(String.format("│ 🔄 Open/Pending    : *%d*%n", open));
        status.append(String.format("│ 📈 Win Rate        : *%.1f%%*%n",  winRate));
        status.append(String.format("│ 💰 Net Points      : *%+.0f pts*%n", netPoints));
        status.append("│ 🤖 Scan            : ").append(scanState).append("\n");
        status.append("└─────────────────────────\n");

        if (total > 0) {
            status.append("\n*📋 Call-by-Call Breakdown:*\n");
            status.append(callDetail);
        } else {
            status.append("\n_No calls generated today yet._\n");
        }

        status.append("\n`V29.0-INSTITUTIONAL` | 70%+ WR All Segments");
        sendMessage(chatId, status.toString());
    }
    
    /**
     * Handle /scan command.
     * Adds the user to the subscriber list so they receive all broadcast signals.
     */
    protected void handleScanCommand(long chatId) {
        activeChatId = chatId;
        saveChatId(chatId);
        subscribedUsers.add(chatId);  // subscribe to signal broadcasts
        if (isScanning && scanFuture != null && !scanFuture.isCancelled() && !scanFuture.isDone()) {
            sendMessage(chatId,
                "✅ *Scan Already Running*\n\n" +
                "📡 Monitoring NIFTY50, SENSEX.\n" +
                "🔔 You are now subscribed — signals will be sent to you.");
            return;
        }
        startScanSilently(chatId);
        sendMessage(chatId,
            "🔍 *Scanning Started*\n\n" +
            "📡 Monitoring NIFTY50, SENSEX...\n" +
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
        subscribedUsers.remove(chatId);  // unsubscribe this user

        // Admin always stops the whole scan engine.
        // Regular approved users only unsubscribe themselves; scan keeps running for others.
        if (userManager.isAdmin(chatId) || subscribedUsers.isEmpty()) {
            isScanning = false;
            if (scanFuture != null) {
                scanFuture.cancel(false);
            }
            sendMessage(chatId,
                "🛑 *Scanning Stopped*\n\n" +
                "Bot is now idle. Use `/scan` to resume monitoring.");
        } else {
            sendMessage(chatId,
                "🔕 *You've unsubscribed from signals.*\n\n" +
                "The scan is still running for other users.\n" +
                "Send `/scan` to resubscribe.");
        }
    }

    private void performScan(long chatId) {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));
        boolean isEquityOpen = !now.isBefore(LocalTime.of(9, 15)) && now.isBefore(LocalTime.of(15, 30));
        if (!isEquityOpen) return;
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (!today.equals(lastResetDate)) {
            todayCallsGenerated.set(0);
            slotsTriggered.clear();
            todayCallsBySymbol.clear();
            lastResetDate = today;
        }
        if (todayCallsGenerated.get() >= 10) return;
        
        try {
            String[] symbols = {"NIFTY50", "SENSEX"}; // BankNifty removed
            for (String symbol : symbols) {
                if (!isScanning) break;
                scanEquitySymbol(chatId, symbol);
            }

            // ── 1-Min Education Signals (runs every scan, completely independent) ──
            for (String symbol : symbols) {
                if (!isScanning) break;
                scan1MinEducation(chatId, symbol);
            }

            // ── Expiry Session: NIFTY50 (Tuesday) + SENSEX (Thursday), 14:00–15:10 IST ──
            boolean isExpiryWindow = !now.isBefore(LocalTime.of(14, 0)) && now.isBefore(LocalTime.of(15, 10));
            if (isExpiryWindow) {
                java.time.DayOfWeek dow = java.time.LocalDate.now(ZoneId.of("Asia/Kolkata")).getDayOfWeek();
                if (dow == java.time.DayOfWeek.TUESDAY  && isScanning) scanExpirySymbol(chatId, "NIFTY50");
                if (dow == java.time.DayOfWeek.THURSDAY && isScanning) scanExpirySymbol(chatId, "SENSEX");
            }

        } catch (Exception e) {
            logger.error("Scan error: {}", e.getMessage());
        }
    }

    private void scanEquitySymbol(long chatId, String symbol) {
        try {
            // Per-symbol daily limit — quality over quantity. NIFTY capped at 2 (its
            // trend-pullback edge is choppier; the 3rd marginal call/day historically
            // loses — see honest audit). SENSEX/BANKNIFTY keep 3.
            int dailyCap = "NIFTY50".equals(symbol) ? 2 : 3;
            if (todayCallsBySymbol.getOrDefault(symbol, 0) >= dailyCap) return;

            List<SimpleMarketData> data5 = marketDataFetcher.getRealMarketData5Min(symbol);
            if (data5 == null || data5.isEmpty()) {
                // Warn user if data is missing during market hours (likely token expired)
                long lastWarn = lastWatchlistAlertMap.getOrDefault("TOKEN_WARN_" + symbol, 0L);
                if (System.currentTimeMillis() - lastWarn > 30 * 60 * 1000L) {
                    sendMessage(chatId, "⚠️ *No market data for " + symbol + "* — Upstox token may have expired.\n\nPlease paste fresh token: `/token <your_new_token>`");
                    lastWatchlistAlertMap.put("TOKEN_WARN_" + symbol, System.currentTimeMillis());
                }
                return;
            }

            // If an active signal exists for this symbol, monitor it first.
            // If it resolves (target/SL hit), schedule an immediate rescan.
            // If still open but last alert was >2 min ago, allow a new signal to be generated.
            if (activeSignals.containsKey(symbol)) {
                boolean resolved = checkActiveSignal(chatId, symbol, data5);
                if (resolved) {
                    // Rescan this symbol immediately (2-second delay to allow data refresh)
                    scheduler.schedule(() -> scanEquitySymbol(chatId, symbol), 2, TimeUnit.SECONDS);
                    return;
                }
                // Previous signal still open — block only if within 2-min window
                long lastAlert2 = lastAlertTimeMap.getOrDefault(symbol, 0L);
                if (System.currentTimeMillis() - lastAlert2 < 2 * 60 * 1000) {
                    return; // too soon — wait for 2-min gap before next signal
                }
                // More than 2 min since last signal → allow new signal, overwrite active slot
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
            
            boolean fiveMinEligible = checkMinimumPoints(symbol, prediction5.estimatedMovePoints) && prediction5.confidence >= 70; // raised from 65
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
                // Daily guarantee: after 13:00, if still no call for this symbol, use relaxed EMA signal
                // Pushed from 9:30 → 13:00 so prime window (11-12:30) always gets first chance.
                // Also requires conf ≥ 78 — avoids firing low-quality fallback signals.
                LocalTime nowIst = LocalTime.now(ZoneId.of("Asia/Kolkata"));
                boolean noCallToday = todayCallsBySymbol.getOrDefault(symbol, 0) == 0;
                boolean isGuaranteeWindow = nowIst.isAfter(LocalTime.of(13, 0)) && nowIst.isBefore(LocalTime.of(14, 45));

                if (noCallToday && isGuaranteeWindow) {
                    List<SimpleMarketData> dataForGuarantee = (data5 != null && !data5.isEmpty()) ? data5 : data1;
                    if (dataForGuarantee != null && !dataForGuarantee.isEmpty()) {
                        AIPredictor.AIPrediction relaxed = aiPredictor.generateRelaxedPrediction(symbol, dataForGuarantee, optionData);
                        if (!"NEUTRAL".equals(relaxed.predictedDirection) && relaxed.confidence >= 78) {
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
            if (currentTime - lastAlert < 2 * 60 * 1000) return; // 2-min cooldown per symbol
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

            // Use live LTP as entry price (more accurate than last candle close which may be 5 min old)
            try {
                Map<String, Double> snapshot = marketDataFetcher.getHonestMarketSnapshot();
                if (snapshot.containsKey(symbol) && snapshot.get(symbol) > 0) {
                    entryPrice = snapshot.get(symbol);
                    logger.info("✅ Using live LTP as entry price for {}: {}", symbol, entryPrice);
                }
            } catch (Exception e) {
                logger.warn("Could not fetch live LTP for {}; using last candle close: {}", symbol, e.getMessage());
            }

            // Recalculate target and SL absolute prices with the refreshed entry
            targetPrice = chosenPrediction.predictedDirection.equals("UP")
                ? entryPrice + targetPoints : entryPrice - targetPoints;

            double rrRatio = chosenPrediction.suggestedStopLoss > 0
                ? chosenPrediction.estimatedMovePoints / chosenPrediction.suggestedStopLoss : 0;

            double slAbsPrice = "UP".equals(chosenPrediction.predictedDirection)
                ? entryPrice - chosenPrediction.suggestedStopLoss
                : entryPrice + chosenPrediction.suggestedStopLoss;

            // Signal strength label based on confidence
            String strengthLabel;
            if (chosenPrediction.confidence >= 92) {
                strengthLabel = "💪 *STRONG SIGNAL*";
            } else if (chosenPrediction.confidence >= 85) {
                strengthLabel = "📊 *MODERATE SIGNAL*";
            } else {
                strengthLabel = "🔔 *SIGNAL*";
            }

            String alert = signalEmoji + " *TRADE SIGNAL* [" + alertId + "] — " + strengthLabel + "\n" +
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

            broadcastSignal(alert);
            lastAlertTimeMap.put(symbol, currentTime);
            todayCallsGenerated.incrementAndGet();
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
            s.confidence = chosenPrediction.confidence;
            activeSignals.put(symbol, s);

            // Persist signal to log file for /today and /yesterday reports
            appendSignalLog(symbol, chosenPrediction.predictedDirection, entryPrice,
                chosenPrediction.estimatedMovePoints, chosenPrediction.suggestedStopLoss,
                s.createdAt, chosenPrediction.confidence);
        } catch (Exception e) {
            logger.error("Error scanning equity " + symbol, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1-MIN EDUCATION SCANNER — runs alongside main bot, NEVER affects win rate
    //   Confidence: ≥ 88% | Min pts: NIFTY50:15, SENSEX:40
    //   Cooldown:  5 min per symbol (independent of all other cooldowns)
    //   All messages clearly labelled EDUCATION PURPOSE ONLY — DO NOT TRADE
    // ─────────────────────────────────────────────────────────────────────────────
    private void scan1MinEducation(long chatId, String symbol) {
        try {
            // 30-minute cooldown per symbol — reduces noise significantly
            long lastEduAlert = edu1MinLastAlertMap.getOrDefault(symbol, 0L);
            if (System.currentTimeMillis() - lastEduAlert < 30 * 60 * 1000L) return;

            List<SimpleMarketData> data1 = marketDataFetcher.getRealMarketData(symbol);
            if (data1 == null || data1.size() < 200) return;

            // Stale data guard — data must be within last 10 min
            SimpleMarketData latest = data1.get(data1.size() - 1);
            LocalDateTime nowIst = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
            if (latest.timestamp != null && latest.timestamp.isBefore(nowIst.minusMinutes(10))) return;

            OptionData optionData = null;
            try { optionData = marketDataFetcher.fetchOptionData(symbol); } catch (Exception ignored) {}

            AIPredictor.AIPrediction pred = aiPredictor.generatePrediction(symbol, data1, optionData);

            if ("NEUTRAL".equals(pred.predictedDirection)) return;
            if (pred.confidence < 88.0) return;

            double minPts = switch (symbol) {
                case "NIFTY50"   -> 15.0;
                case "SENSEX"    -> 40.0;
                default          -> 15.0;
            };
            if (pred.estimatedMovePoints < minPts) return;

            double entryPrice = latest.price;
            try {
                Map<String, Double> snap = marketDataFetcher.getHonestMarketSnapshot();
                if (snap.containsKey(symbol) && snap.get(symbol) > 0) entryPrice = snap.get(symbol);
            } catch (Exception ignored) {}

            double targetPts = pred.estimatedMovePoints;
            double slPts     = pred.suggestedStopLoss;
            double targetPx  = "UP".equals(pred.predictedDirection) ? entryPrice + targetPts : entryPrice - targetPts;
            double slPx      = "UP".equals(pred.predictedDirection) ? entryPrice - slPts     : entryPrice + slPts;
            String arrow     = "UP".equals(pred.predictedDirection) ? "⬆️" : "⬇️";
            String ts        = nowIst.format(DateTimeFormatter.ofPattern("HH:mm"));

            // Short, compact message — Entry, Target, SL only
            String msg =
                "📚 *[EDU]* " + symbol + " " + pred.predictedDirection + " " + arrow + " | " + ts + " IST\n" +
                "💰 Entry: `" + String.format("%.2f", entryPrice) + "`\n" +
                "🎯 Target: `" + String.format("%.2f", targetPx) + "` (+" + String.format("%.0f", targetPts) + " pts)\n" +
                "🛑 SL: `" + String.format("%.2f", slPx) + "` (-" + String.format("%.0f", slPts) + " pts)\n" +
                "_AI: " + String.format("%.1f%%", pred.confidence) + " conf | Edu only — do not trade_";

            broadcastSignal(msg);
            edu1MinLastAlertMap.put(symbol, System.currentTimeMillis());
            logger.info("📚 1-min education signal sent for {} {} conf={}%", symbol, pred.predictedDirection, String.format("%.1f", pred.confidence));

        } catch (Exception e) {
            logger.error("Error in 1-min education scan for {}: {}", symbol, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // EXPIRY SESSION SCANNER — RSI Exhaustion Reversal (14:00–15:10 IST)
    //   NIFTY50  → every Tuesday
    //   SENSEX   → every Thursday
    //   Filters: RSI>=68 (short) / <=32 (long) + BB 1.8σ pierce + Vol 1.4x + Body 55%
    //   Target: 2.0×ATR14 | SL: 0.5×ATR14 (4:1 R:R)
    //   Cooldown: 20 min per symbol (independent from regular cooldown)
    // ─────────────────────────────────────────────────────────────────────────────
    private void scanExpirySymbol(long chatId, String symbol) {
        try {
            List<SimpleMarketData> data = marketDataFetcher.getRealMarketData5Min(symbol);
            if (data == null || data.size() < 25) return;

            // 20-min cooldown (expiry signals are independent of regular cooldown)
            long expiryLast = expiryLastAlertMap.getOrDefault(symbol, 0L);
            if (System.currentTimeMillis() - expiryLast < 20 * 60 * 1000L) return;

            // ── Indicators ──────────────────────────────────────────────────────
            double rsi    = expiryCalcRSI(data, 14);
            double atr    = expiryCalcATR(data, 14);
            double[] bb   = expiryCalcBB(data, 20, 1.8);
            double volMA  = expiryCalcVolMA(data, 20);
            if (atr <= 0) return;

            SimpleMarketData latest = data.get(data.size() - 1);
            double price     = latest.price;
            double open      = latest.open;
            double high      = latest.high;
            double low       = latest.low;
            double range     = high - low;
            double body      = Math.abs(price - open);
            double bodyRatio = range > 0 ? body / range : 0;
            double volume    = latest.volume > 0 ? latest.volume : 1;
            double bbUpper   = bb[0], bbLower = bb[1];

            // ── Signal detection ────────────────────────────────────────────────
            String direction = null;
            String reason    = null;

            if (rsi <= 32.0 && price <= bbLower * 1.002 && price > open
                    && bodyRatio >= 0.55 && volume >= volMA * 1.4) {
                direction = "UP";
                reason = String.format(
                    "Expiry RSI=%.1f (OVERSOLD) | Price at BB lower | Bull candle %.0f%% body | Vol %.1fx avg",
                    rsi, bodyRatio * 100, volume / volMA);
            } else if (rsi >= 68.0 && price >= bbUpper * 0.998 && price < open
                    && bodyRatio >= 0.55 && volume >= volMA * 1.4) {
                direction = "DOWN";
                reason = String.format(
                    "Expiry RSI=%.1f (OVERBOUGHT) | Price at BB upper | Bear candle %.0f%% body | Vol %.1fx avg",
                    rsi, bodyRatio * 100, volume / volMA);
            }

            if (direction == null) return;

            // ── Position sizing ──────────────────────────────────────────────────
            double targetPts = atr * 2.0;
            double slPts     = atr * 0.5;
            double minPts    = "NIFTY50".equals(symbol) ? 15.0 : 30.0; // SENSEX
            if (targetPts < minPts) return;

            // Stale data guard
            LocalDateTime nowIst = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
            if (latest.timestamp != null && latest.timestamp.isBefore(nowIst.minusMinutes(10))) {
                logger.warn("Expiry scan: stale data for {}, skipping", symbol);
                return;
            }

            // Try to fetch live LTP for accurate entry
            double entryPrice = price;
            try {
                Map<String, Double> snap = marketDataFetcher.getHonestMarketSnapshot();
                if (snap.containsKey(symbol) && snap.get(symbol) > 0) entryPrice = snap.get(symbol);
            } catch (Exception e) {
                logger.warn("Expiry: could not fetch live LTP for {}", symbol);
            }

            double targetPrice = "UP".equals(direction) ? entryPrice + targetPts : entryPrice - targetPts;
            double slPrice     = "UP".equals(direction) ? entryPrice - slPts     : entryPrice + slPts;
            double rrRatio     = slPts > 0 ? targetPts / slPts : 0;
            String arrow       = "UP".equals(direction) ? "⬆️" : "⬇️";
            String emoji       = "UP".equals(direction) ? "🟢" : "🔴";
            String alertId     = "EXP-" + (System.currentTimeMillis() % 10000);
            String timestamp   = nowIst.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            String alert = emoji + " *EXPIRY SESSION SIGNAL* [" + alertId + "]\n" +
                "🕒 *Time:* " + timestamp + " IST\n" +
                "⚡ *Source:* Expiry Reversal (14:00–15:10 window)\n\n" +
                "📌 *Symbol:* " + symbol + "\n" +
                "🚀 *Direction:* " + direction + " " + arrow + "\n\n" +
                "💰 *Entry:* " + String.format("%.2f", entryPrice) + "\n" +
                "🎯 *Target:* " + String.format("%.2f", targetPrice) +
                    "  (+" + String.format("%.0f", targetPts) + " pts)\n" +
                "🛑 *Stop Loss:* " + String.format("%.2f", slPrice) +
                    "  (-" + String.format("%.0f", slPts) + " pts)\n" +
                "📊 *R:R:* 1:" + String.format("%.1f", rrRatio) + "\n\n" +
                "📝 *Reason:* " + reason + "\n" +
                "_RSI Exhaustion Reversal — Expiry Day Momentum_";

            broadcastSignal(alert);
            long now2 = System.currentTimeMillis();
            expiryLastAlertMap.put(symbol, now2);
            lastAlertTimeMap.put(symbol, now2);
            todayCallsGenerated.incrementAndGet();
            todayCallsBySymbol.merge(symbol, 1, Integer::sum);
            saveDailyState();

            ActiveSignal sig = new ActiveSignal();
            sig.symbol       = symbol;
            sig.direction    = direction;
            sig.entryPrice   = entryPrice;
            sig.targetPoints = targetPts;
            sig.stopLossPoints = slPts;
            sig.createdAt    = now2;
            activeSignals.put(symbol, sig);
            appendSignalLog(symbol, direction, entryPrice, targetPts, slPts, now2, 0.0);

        } catch (Exception e) {
            logger.error("Error in expiry scan for " + symbol, e);
        }
    }

    // ── Indicator helpers for expiry session (self-contained, no AIPredictor) ──

    private double expiryCalcRSI(List<SimpleMarketData> h, int period) {
        if (h.size() < period + 1) return 50.0;
        double gains = 0, losses = 0;
        for (int i = h.size() - period; i < h.size(); i++) {
            double d = h.get(i).price - h.get(i - 1).price;
            if (d > 0) gains += d; else losses -= d;
        }
        if (losses == 0) return 100.0;
        double rs = (gains / period) / (losses / period);
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private double expiryCalcATR(List<SimpleMarketData> h, int period) {
        if (h.size() < period + 1) return 0;
        double sum = 0;
        for (int i = h.size() - period; i < h.size(); i++) {
            double pc = h.get(i - 1).price;
            sum += Math.max(h.get(i).high - h.get(i).low,
                   Math.max(Math.abs(h.get(i).high - pc),
                            Math.abs(h.get(i).low  - pc)));
        }
        return sum / period;
    }

    /** Returns [upperBB, lowerBB] */
    private double[] expiryCalcBB(List<SimpleMarketData> h, int period, double mult) {
        if (h.size() < period) {
            double p = h.get(h.size() - 1).price;
            return new double[]{p * 1.01, p * 0.99};
        }
        double sum = 0;
        for (int i = h.size() - period; i < h.size(); i++) sum += h.get(i).price;
        double sma = sum / period;
        double varSum = 0;
        for (int i = h.size() - period; i < h.size(); i++) {
            double d = h.get(i).price - sma;
            varSum += d * d;
        }
        double std = Math.sqrt(varSum / period);
        return new double[]{sma + mult * std, sma - mult * std};
    }

    private double expiryCalcVolMA(List<SimpleMarketData> h, int period) {
        if (h.size() < period) return 1;
        double sum = 0;
        for (int i = h.size() - period; i < h.size(); i++) sum += h.get(i).volume;
        return sum / period > 0 ? sum / period : 1;
    }

    /**
     * Market movement monitor — runs every 2 minutes.
     * Sends alerts ONLY for SUDDEN moves: compares current price to the price
     * 3 candles ago (~15 min on 5-min data) so gradual drift never triggers.
     * Cooldown: 45 minutes per symbol to avoid repeated alerts on the same move.
     */
    private void monitorMarketMovement() {
        if (activeChatId == 0) return; // No chat registered yet

        java.time.LocalTime now = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        boolean isMarketOpen = !now.isBefore(java.time.LocalTime.of(9, 15))
                            && now.isBefore(java.time.LocalTime.of(15, 35));
        if (!isMarketOpen) return;

        String[] symbols = {"NIFTY50", "SENSEX"}; // BankNifty removed
        for (String sym : symbols) {
            try {
                List<SimpleMarketData> data = marketDataFetcher.getRealMarketData5Min(sym);
                // Need at least 4 candles to compare current vs ~15-min-ago price
                if (data == null || data.size() < 4) continue;

                int n = data.size();
                double currentPrice  = data.get(n - 1).price;
                // Reference price = 3 candles back (~15 min for 5-min candles)
                // This detects only SUDDEN moves, not gradual drift over hours
                double referencePrice = data.get(n - 4).price;
                if (currentPrice <= 0 || referencePrice <= 0) continue;

                double changePct = Math.abs((currentPrice - referencePrice) / referencePrice * 100.0);
                double threshold = switch (sym) {
                    case "NIFTY50"   -> NIFTY_MOVE_THRESHOLD;
                    case "BANKNIFTY" -> BANKNIFTY_MOVE_THRESHOLD;
                    case "SENSEX"    -> SENSEX_MOVE_THRESHOLD;
                    default          -> 0.40;
                };

                // 45 minutes between movement alerts per symbol
                long lastAlert = lastMovementAlertTime.getOrDefault(sym, 0L);
                boolean cooldownOk = (System.currentTimeMillis() - lastAlert) > 45 * 60 * 1000L;

                if (changePct >= threshold && cooldownOk) {
                    double changePoints = currentPrice - referencePrice;
                    String direction = changePoints > 0 ? "📈 UP" : "📉 DOWN";
                    String emoji = changePoints > 0 ? "🟢" : "🔴";
                    String changeStr = (changePoints > 0 ? "+" : "") + String.format("%.0f", changePoints);
                    String symLabel = switch (sym) {
                        case "NIFTY50"   -> "NIFTY 50";
                        case "SENSEX"    -> "SENSEX";
                        default          -> sym;
                    };
                    String msg = emoji + " *SUDDEN MOVE — " + symLabel + "*\n" +
                        direction + " *" + changeStr + " pts* (" + String.format("%.2f%%", Math.abs(changePct)) + " in ~15 min)\n" +
                        "💰 " + String.format("%.2f", currentPrice) + " | " +
                        now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " IST";
                    broadcastSignal(msg);
                    lastMovementAlertTime.put(sym, System.currentTimeMillis());
                    logger.info("📡 Sudden movement alert for {}: {} pts ({} %)",
                        sym, changeStr, String.format("%.2f", changePct));
                }

                // ── ADX Trend State Alert (every 3 hours max per symbol) ──────────
                long lastTrendAlert = lastTrendAlertTime.getOrDefault(sym, 0L);
                boolean trendCooldownOk = (System.currentTimeMillis() - lastTrendAlert) > 3 * 60 * 60 * 1000L;
                if (trendCooldownOk && data.size() >= 30) {
                    double adxVal = quickADX14(data);
                    String newTrend;
                    String trendMsg = null;
                    String symLabel = switch (sym) {
                        case "NIFTY50"   -> "NIFTY 50";
                        case "BANKNIFTY" -> "BANK NIFTY";
                        default          -> sym;
                    };
                    if (adxVal > 30) {
                        newTrend = "STRONG";
                        if (!"STRONG".equals(lastTrendState.get(sym))) {
                            trendMsg = String.format(
                                "💪 *STRONG TREND DETECTED* — %s\n\n" +
                                "📊 ADX: `%.1f` (> 30 = trending market)\n" +
                                "🚀 Trending conditions active — bot signals carry higher follow-through.\n" +
                                "🕒 Time: %s IST",
                                symLabel, adxVal,
                                now.format(DateTimeFormatter.ofPattern("HH:mm")));
                        }
                    } else if (adxVal < 20) {
                        newTrend = "CHOPPY";
                        if (!"CHOPPY".equals(lastTrendState.get(sym))) {
                            trendMsg = String.format(
                                "⚠️ *CHOPPY MARKET* — %s\n\n" +
                                "📊 ADX: `%.1f` (< 20 = ranging/choppy market)\n" +
                                "🔄 Low-trend conditions — be selective, tighter SL advised.\n" +
                                "🕒 Time: %s IST",
                                symLabel, adxVal,
                                now.format(DateTimeFormatter.ofPattern("HH:mm")));
                        }
                    } else {
                        newTrend = "NORMAL";
                    }
                    if (trendMsg != null) {
                        broadcastSignal(trendMsg);
                        lastTrendAlertTime.put(sym, System.currentTimeMillis());
                        logger.info("📊 Trend alert sent for {}: {} (ADX={})", sym, newTrend, String.format("%.1f", adxVal));
                    }
                    lastTrendState.put(sym, newTrend);
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
    
    // ── Access-control command handlers ───────────────────────────────────────

    /**
     * /join  — any user requests access.
     * Optionally accepts a display name: /join MyName
     */
    private void handleJoinCommand(long chatId, String command) {
        if (userManager.isApproved(chatId)) {
            sendMessage(chatId,
                "✅ *You already have access!*\n\nUse `/scan` to start receiving signals.");
            return;
        }
        if (userManager.hasPendingRequest(chatId)) {
            sendMessage(chatId,
                "⏳ *Request Already Pending*\n\n" +
                "Your join request is awaiting admin review. Please wait.");
            return;
        }

        // Use the real Telegram name captured from the "from" JSON object.
        // If the user typed "/join SomeName" that becomes an extra note appended.
        // Priority: real Telegram name > manual override > fallback "User#id"
        String telegramName = telegramDisplayNames.getOrDefault(chatId, "");
        String[] parts = command.split("\\s+", 2);
        String manualNote  = (parts.length > 1 && !parts[1].isBlank()) ? parts[1].trim() : "";

        String displayName;
        if (!telegramName.isEmpty() && !manualNote.isEmpty()) {
            // e.g. "Rahul Sharma (@rahul) — premium"
            displayName = telegramName + " — " + manualNote;
        } else if (!telegramName.isEmpty()) {
            displayName = telegramName;
        } else if (!manualNote.isEmpty()) {
            displayName = manualNote;
        } else {
            displayName = "User#" + chatId;
        }

        userManager.addPendingRequest(chatId, displayName);

        // Confirm to the requesting user
        sendMessage(chatId,
            "📩 *Join Request Sent!*\n\n" +
            "Your request has been submitted to the admin.\n" +
            "You will be notified once your request is approved.\n\n" +
            "_Your Chat ID: `" + chatId + "`_");

        // Notify admin
        sendMessage(userManager.getAdminChatId(),
            "🔔 *New Join Request*\n\n" +
            "👤 *Name:* " + displayName + "\n" +
            "🆔 *Chat ID:* `" + chatId + "`\n\n" +
            "To approve: `/approve " + chatId + "`\n" +
            "To reject:  `/reject "  + chatId + "`");
    }

    /**
     * /approve <userId>  — admin approves a pending request.
     */
    private void handleApproveCommand(long chatId, String command) {
        String[] parts = command.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            sendMessage(chatId,
                "Usage: `/approve <userId>`\n\nCheck pending requests with /pending");
            return;
        }
        try {
            long targetId = Long.parseLong(parts[1].trim());
            String name   = userManager.getPendingName(targetId);
            if (userManager.approveUser(targetId)) {
                sendMessage(chatId,
                    "✅ *User Approved*\n\n" +
                    "👤 *" + name + "* (`" + targetId + "`) now has access.");
                sendMessage(targetId,
                    "🎉 *Access Granted!*\n\n" +
                    "Your join request has been approved.\n\n" +
                    "Use `/scan` to start receiving trading signals.");
            } else {
                sendMessage(chatId,
                    "⚠️ User `" + targetId + "` not found in pending requests.\n" +
                    "Check /pending for the current list.");
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Invalid user ID.\nUsage: `/approve 123456789`");
        }
    }

    /**
     * /reject <userId>  — admin rejects a pending request.
     */
    private void handleRejectCommand(long chatId, String command) {
        String[] parts = command.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            sendMessage(chatId, "Usage: `/reject <userId>`");
            return;
        }
        try {
            long targetId = Long.parseLong(parts[1].trim());
            String name   = userManager.getPendingName(targetId);
            if (userManager.rejectUser(targetId)) {
                sendMessage(chatId,
                    "❌ *Request Rejected*\n\n" +
                    "👤 *" + name + "* (`" + targetId + "`) was rejected.");
                sendMessage(targetId,
                    "❌ *Access Denied*\n\n" +
                    "Your join request was not approved.\n" +
                    "Contact the admin if you believe this is a mistake.");
            } else {
                sendMessage(chatId,
                    "⚠️ User `" + targetId + "` not found in pending requests.");
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Invalid user ID.\nUsage: `/reject 123456789`");
        }
    }

    /**
     * /revoke <userId>  — admin removes access from an approved user.
     */
    private void handleRevokeCommand(long chatId, String command) {
        String[] parts = command.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            sendMessage(chatId, "Usage: `/revoke <userId>`");
            return;
        }
        try {
            long targetId = Long.parseLong(parts[1].trim());
            if (userManager.isAdmin(targetId)) {
                sendMessage(chatId, "❌ Cannot revoke admin access.");
                return;
            }
            if (userManager.revokeUser(targetId)) {
                subscribedUsers.remove(targetId);  // stop sending signals to them
                sendMessage(chatId,
                    "🚫 *Access Revoked*\n\n" +
                    "User `" + targetId + "` has been removed from the approved list.");
                sendMessage(targetId,
                    "🚫 *Your access has been revoked.*\n\n" +
                    "Contact the admin if you think this is a mistake.");
            } else {
                sendMessage(chatId,
                    "⚠️ User `" + targetId + "` is not in the approved list.");
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Invalid user ID.\nUsage: `/revoke 123456789`");
        }
    }

    /**
     * /users  — admin lists all approved users.
     */
    private void handleUsersCommand(long chatId) {
        java.util.Set<Long> approved = userManager.getApprovedUsers();
        if (approved.isEmpty()) {
            sendMessage(chatId, "📋 *Approved Users*\n\nNo approved users yet.");
            return;
        }
        StringBuilder sb = new StringBuilder("📋 *Approved Users* (" + approved.size() + ")\n\n");
        int i = 1;
        for (Long uid : approved) {
            String status = subscribedUsers.contains(uid) ? "🟢 scanning" : "⚪ idle";
            sb.append(i++).append(". `").append(uid).append("` ").append(status).append("\n");
        }
        sb.append("\nUse `/revoke <userId>` to remove access.");
        sendMessage(chatId, sb.toString());
    }

    /**
     * /pending  — admin lists pending join requests.
     */
    private void handlePendingCommand(long chatId) {
        java.util.Map<Long, String> pending = userManager.getPendingRequests();
        if (pending.isEmpty()) {
            sendMessage(chatId, "📋 *Pending Requests*\n\nNo pending requests right now.");
            return;
        }
        StringBuilder sb = new StringBuilder(
            "📋 *Pending Join Requests* (" + pending.size() + ")\n\n");
        int i = 1;
        for (java.util.Map.Entry<Long, String> e : pending.entrySet()) {
            sb.append(i++).append(". 👤 *").append(e.getValue()).append("*\n")
              .append("   🆔 `").append(e.getKey()).append("`\n")
              .append("   `/approve ").append(e.getKey())
              .append("`  `/reject ").append(e.getKey()).append("`\n\n");
        }
        sendMessage(chatId, sb.toString());
    }

    // ── Signal broadcasting ────────────────────────────────────────────────────

    /**
     * Sends a trading signal / alert to ALL subscribed users.
     * Subscribed = approved users who sent /scan (and haven't sent /stop).
     * Admin always receives even if not explicitly subscribed.
     */
    private void broadcastSignal(String message) {
        long adminId = userManager.getAdminChatId();
        // Always send to admin first
        sendMessage(adminId, message);
        // Then to every other subscriber
        for (Long uid : subscribedUsers) {
            if (uid != adminId) {
                sendMessage(uid, message);
            }
        }
    }

    /**
     * Handle token update command (admin only — enforced in processCommand)
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
            // Telegram API rejects messages over 4096 chars — truncate safely
            if (text != null && text.length() > 4000) {
                text = text.substring(0, 3997) + "...";
            }
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
    
    /**
     * Extracts a human-readable display name from a Telegram update JSON string.
     *
     * Telegram sends user info inside the "from" object:
     *   "from":{"id":123,"first_name":"Rahul","last_name":"Sharma","username":"rahulsharma"}
     *
     * We build: "Rahul Sharma (@rahulsharma)"
     * If only first_name exists: "Rahul"
     * If nothing is available:   "User#<chatId>"
     *
     * Note: "username" is optional — not every Telegram user has a @handle.
     */
    private String extractDisplayName(String update, long chatId) {
        try {
            // ── first_name ──────────────────────────────────────────────────
            String firstName = "";
            int fnIdx = update.indexOf("\"first_name\":\"");
            if (fnIdx != -1) {
                int start = fnIdx + "\"first_name\":\"".length();
                int end   = update.indexOf("\"", start);
                if (end > start) firstName = update.substring(start, end);
            }

            // ── last_name (optional) ────────────────────────────────────────
            String lastName = "";
            int lnIdx = update.indexOf("\"last_name\":\"");
            if (lnIdx != -1) {
                int start = lnIdx + "\"last_name\":\"".length();
                int end   = update.indexOf("\"", start);
                if (end > start) lastName = update.substring(start, end);
            }

            // ── @username (optional — not every user has one) ───────────────
            String username = "";
            int unIdx = update.indexOf("\"username\":\"");
            if (unIdx != -1) {
                int start = unIdx + "\"username\":\"".length();
                int end   = update.indexOf("\"", start);
                if (end > start) username = update.substring(start, end);
            }

            // ── Assemble display name ────────────────────────────────────────
            StringBuilder name = new StringBuilder();
            if (!firstName.isEmpty()) name.append(firstName);
            if (!lastName.isEmpty())  name.append(" ").append(lastName);
            if (!username.isEmpty())  name.append(" (@").append(username).append(")");

            return name.isEmpty() ? "User#" + chatId : name.toString().trim();

        } catch (Exception e) {
            return "User#" + chatId;
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
            logger.info("🕛 Daily Reset (11:59 PM): Clearing in-memory market data cache. 120-day disk history preserved.");
            marketDataFetcher.clearDailySession();
        }, initialDelay, 24 * 60 * 60, TimeUnit.SECONDS);
    }

    /**
     * At 3:35 PM IST (5 min after market close), do a final data refresh so today's
     * complete session is merged and saved into the 120-day rolling disk store.
     */
    // -----------------------------------------------------------------------
    // Pre-Market Summary — fires at 9:15 AM IST (PDH/PDL, prev close, gap %)
    // -----------------------------------------------------------------------

    private void schedulePreMarketSummary() {
        LocalTime fireTime = LocalTime.of(9, 15);
        LocalDateTime now  = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        LocalDateTime next = now.toLocalDate().atTime(fireTime).atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
        if (now.isAfter(next)) next = next.plusDays(1);
        long initialDelay = java.time.Duration.between(now, next).toSeconds();
        scheduler.scheduleAtFixedRate(() -> {
            if (activeChatId != 0) sendPreMarketSummary(activeChatId);
        }, initialDelay, 24 * 60 * 60, TimeUnit.SECONDS);
    }

    private void sendPreMarketSummary(long chatId) {
        StringBuilder sb = new StringBuilder();
        sb.append("🌅 *PRE-MARKET SUMMARY* — ")
          .append(LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
              .format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")))
          .append("\n\n");

        String[] symbols = {"NIFTY50", "SENSEX"}; // BankNifty removed
        for (String sym : symbols) {
            try {
                List<SimpleMarketData> all = marketDataFetcher.getRealMarketData5Min(sym);
                if (all == null || all.size() < 10) {
                    sb.append("⚠️ *").append(sym).append("* — data unavailable\n\n");
                    continue;
                }

                java.time.LocalDate today = java.time.LocalDate.now(ZoneId.of("Asia/Kolkata"));
                // Separate today's candles from previous day's candles
                List<SimpleMarketData> todayCandles = new ArrayList<>();
                List<SimpleMarketData> prevCandles  = new ArrayList<>();
                for (SimpleMarketData c : all) {
                    if (c.timestamp == null) continue;
                    java.time.LocalDate cd = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
                    if (cd.equals(today))           todayCandles.add(c);
                    else if (cd.isBefore(today))    prevCandles.add(c);
                }

                // Find yesterday's candles (most recent previous trading day)
                java.time.LocalDate prevDay = prevCandles.isEmpty() ? null
                    : prevCandles.get(prevCandles.size() - 1).timestamp
                          .atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
                List<SimpleMarketData> yesterdayCandles = new ArrayList<>();
                if (prevDay != null) {
                    for (SimpleMarketData c : prevCandles) {
                        if (c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().equals(prevDay))
                            yesterdayCandles.add(c);
                    }
                }

                if (yesterdayCandles.isEmpty()) {
                    sb.append("⚠️ *").append(sym).append("* — no previous day data\n\n");
                    continue;
                }

                double pdh = yesterdayCandles.stream().mapToDouble(c -> c.high).max().orElse(0);
                double pdl = yesterdayCandles.stream().mapToDouble(c -> c.low).min().orElse(0);
                double prevClose = yesterdayCandles.get(yesterdayCandles.size() - 1).price;

                // Today's open — first 9:15 candle or live snapshot
                double todayOpen = 0;
                if (!todayCandles.isEmpty()) {
                    todayOpen = todayCandles.get(0).open > 0
                        ? todayCandles.get(0).open : todayCandles.get(0).price;
                } else {
                    try {
                        Map<String, Double> snap = marketDataFetcher.getHonestMarketSnapshot();
                        todayOpen = snap.getOrDefault(sym, 0.0);
                    } catch (Exception ignored) {}
                }

                double gapPts = todayOpen > 0 ? todayOpen - prevClose : 0;
                double gapPct = prevClose > 0 && todayOpen > 0 ? (gapPts / prevClose) * 100 : 0;
                String gapStr = todayOpen > 0
                    ? String.format("%s%.0f pts (%s%.2f%%)",
                        gapPts >= 0 ? "+" : "", gapPts,
                        gapPct >= 0 ? "+" : "", gapPct)
                    : "Awaiting open";
                String gapEmoji = gapPts > 50 ? "🟢" : gapPts < -50 ? "🔴" : "⚪";

                String symLabel = switch (sym) {
                    case "NIFTY50"   -> "NIFTY 50";
                    case "BANKNIFTY" -> "BANK NIFTY";
                    default          -> sym;
                };
                sb.append("📌 *").append(symLabel).append("*\n");
                sb.append(String.format("  Prev Close : `%.2f`%n", prevClose));
                sb.append(String.format("  PDH        : `%.2f`%n", pdh));
                sb.append(String.format("  PDL        : `%.2f`%n", pdl));
                sb.append(String.format("  PDH-PDL    : `%.0f pts`%n", pdh - pdl));
                sb.append(String.format("  Gap        : %s %s%n", gapEmoji, gapStr));
                sb.append("\n");
            } catch (Exception e) {
                sb.append("⚠️ *").append(sym).append("* error: ").append(e.getMessage()).append("\n\n");
                logger.warn("Pre-market summary error for {}: {}", sym, e.getMessage());
            }
        }
        sb.append("🤖 `V29.0-INSTITUTIONAL` | Market opens in ~0 min\n");
        sb.append("_Watch PDH/PDL as key breakout levels for today's calls_");
        sendMessage(chatId, sb.toString());
    }

    /**
     * Quick 14-period ADX calculation from candle data.
     * Returns value in range ~10–50; >30 = strong trend, <20 = choppy.
     */
    private double quickADX14(List<SimpleMarketData> data) {
        int period = 14;
        if (data.size() < period * 2 + 1) return 25.0;
        int n = data.size();
        double[] tr = new double[n];
        double[] plusDM = new double[n];
        double[] minusDM = new double[n];
        for (int i = 1; i < n; i++) {
            double h = data.get(i).high, l = data.get(i).low;
            double ph = data.get(i-1).high, pl = data.get(i-1).low;
            double pc = data.get(i-1).price;
            tr[i] = Math.max(h - l, Math.max(Math.abs(h - pc), Math.abs(l - pc)));
            double up = h - ph, dn = pl - l;
            plusDM[i]  = (up > dn && up > 0) ? up : 0;
            minusDM[i] = (dn > up && dn > 0) ? dn : 0;
        }
        // Initial sums
        double sTR = 0, sPlus = 0, sMinus = 0;
        for (int i = 1; i <= period; i++) { sTR += tr[i]; sPlus += plusDM[i]; sMinus += minusDM[i]; }
        List<Double> dxList = new ArrayList<>();
        for (int i = period + 1; i < n; i++) {
            sTR    = sTR    - (sTR / period)    + tr[i];
            sPlus  = sPlus  - (sPlus / period)  + plusDM[i];
            sMinus = sMinus - (sMinus / period) + minusDM[i];
            double pDI = sTR > 0 ? (sPlus  / sTR) * 100 : 0;
            double mDI = sTR > 0 ? (sMinus / sTR) * 100 : 0;
            double diSum = pDI + mDI;
            dxList.add(diSum > 0 ? Math.abs(pDI - mDI) / diSum * 100 : 0);
        }
        if (dxList.size() < period) return 25.0;
        double sum = 0;
        for (int i = dxList.size() - period; i < dxList.size(); i++) sum += dxList.get(i);
        return sum / period;
    }

    private void scheduleEndOfDaySnapshot() {
        LocalTime snapshotTime = LocalTime.of(15, 35);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        LocalDateTime next = now.with(snapshotTime);
        if (now.isAfter(next)) {
            next = next.plusDays(1);
        }
        long initialDelay = java.time.Duration.between(now, next).toSeconds();
        scheduler.scheduleAtFixedRate(() -> {
            logger.info("📦 End-of-day snapshot: saving today's candles to 120-day local store...");
            String[] symbols = {"NIFTY50", "SENSEX"};
            for (String sym : symbols) {
                try {
                    marketDataFetcher.getRealMarketData5Min(sym);
                    logger.info("✅ 120-day store updated for {}", sym);
                } catch (Exception e) {
                    logger.warn("⚠️ End-of-day snapshot failed for {}: {}", sym, e.getMessage());
                }
            }
        }, initialDelay, 24 * 60 * 60, TimeUnit.SECONDS);
    }

    // -----------------------------------------------------------------------
    // Signal Log — persists every call + outcome to signal_log.csv
    // Format: date,time,symbol,direction,entry,targetPts,slPts,status,points,createdAt
    // -----------------------------------------------------------------------

    private synchronized void appendSignalLog(String symbol, String direction,
            double entry, double targetPts, double slPts, long createdAt, double confidence) {
        try {
            java.time.ZonedDateTime zdt = java.time.Instant.ofEpochMilli(createdAt)
                    .atZone(ZoneId.of("Asia/Kolkata"));
            String date = zdt.toLocalDate().toString();
            String time = zdt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            // Columns: date,time,symbol,direction,entry,targetPts,slPts,status,points,createdAt,confidence,resolvedAt
            String line = String.join(",", date, time, symbol, direction,
                    String.format("%.2f", entry),
                    String.format("%.2f", targetPts),
                    String.format("%.2f", slPts),
                    "OPEN", "0.00", String.valueOf(createdAt),
                    String.format("%.1f", confidence), "");
            java.nio.file.Files.writeString(java.nio.file.Path.of(SIGNAL_LOG_FILE),
                    line + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            logger.warn("Could not append signal log: {}", e.getMessage());
        }
    }

    private synchronized void updateSignalLog(String symbol, long createdAt,
            String status, double points, long resolvedAt) {
        try {
            java.nio.file.Path path = java.nio.file.Path.of(SIGNAL_LOG_FILE);
            if (!path.toFile().exists()) return;
            List<String> lines = java.nio.file.Files.readAllLines(path);
            String createdAtStr = String.valueOf(createdAt);
            List<String> updated = new ArrayList<>();
            for (String line : lines) {
                String[] parts = line.split(",", -1);
                // Match by symbol (col 2) and createdAt (col 9)
                if (parts.length >= 10 && parts[2].equals(symbol)
                        && parts[9].equals(createdAtStr)) {
                    parts[7] = status;
                    parts[8] = String.format("%.2f", points);
                    // Extend array if needed (old rows may only have 10 cols)
                    if (parts.length < 12) {
                        parts = Arrays.copyOf(parts, 12);
                        if (parts[10] == null) parts[10] = "0.0";
                        if (parts[11] == null) parts[11] = "";
                    }
                    parts[11] = String.valueOf(resolvedAt);
                    line = String.join(",", parts);
                }
                updated.add(line);
            }
            java.nio.file.Files.write(path, updated);
        } catch (Exception e) {
            logger.warn("Could not update signal log: {}", e.getMessage());
        }
    }

    private String buildDayReport(java.time.LocalDate date) {
        String dateStr = date.toString();
        String label = date.equals(java.time.LocalDate.now(ZoneId.of("Asia/Kolkata")))
                ? "Today (" + dateStr + ")" : "Yesterday (" + dateStr + ")";
        try {
            java.nio.file.Path path = java.nio.file.Path.of(SIGNAL_LOG_FILE);
            if (!path.toFile().exists()) {
                return "📋 *" + label + " Report*\n\nNo signal log found. Signals are recorded once the bot starts generating calls.";
            }
            List<String> lines = java.nio.file.Files.readAllLines(path);
            int total = 0, wins = 0, losses = 0, open = 0;
            double netPoints = 0;
            StringBuilder detail = new StringBuilder();
            for (String line : lines) {
                String[] p = line.split(",", -1);
                if (p.length < 10 || !p[0].equals(dateStr)) continue;
                total++;
                String sym = p[2], dir = p[3], status = p[7];
                double pts = Double.parseDouble(p[8]);
                netPoints += pts;
                String icon;
                if ("WIN".equals(status))        { wins++;   icon = "✅"; }
                else if ("LOSS".equals(status))  { losses++; icon = "❌"; }
                else                             { open++;   icon = "🔄"; }
                detail.append(String.format("%s %s %s @ %s → %s (%.0f pts)%n",
                        icon, sym, dir, p[4], status, pts));
            }
            if (total == 0) {
                return "📋 *" + label + " Report*\n\nNo calls generated on this day.";
            }
            int resolved = wins + losses;
            double winRate = resolved > 0 ? (wins * 100.0 / resolved) : 0;
            return String.format(
                "📋 *%s Report*\n\n" +
                "📞 Total Calls  : %d\n" +
                "✅ Wins         : %d\n" +
                "❌ Losses       : %d\n" +
                "🔄 Open/Pending : %d\n" +
                "📈 Win Rate     : %.1f%%\n" +
                "💰 Net Points   : %+.0f pts\n\n" +
                "*Detail:*\n%s",
                label, total, wins, losses, open, winRate, netPoints, detail.toString().trim());
        } catch (Exception e) {
            logger.warn("Error building day report: {}", e.getMessage());
            return "⚠️ Could not read signal log: " + e.getMessage();
        }
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
            p.setProperty("totalCalls", String.valueOf(todayCallsGenerated.get()));
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
            todayCallsGenerated.set(Integer.parseInt(p.getProperty("totalCalls", "0")));
            for (String key : p.stringPropertyNames()) {
                if (key.startsWith("calls."))
                    todayCallsBySymbol.put(key.substring(6), Integer.parseInt(p.getProperty(key)));
                if (key.startsWith("lastAlert."))
                    lastAlertTimeMap.put(key.substring(10), Long.parseLong(p.getProperty(key)));
            }
            logger.info("✅ Loaded daily state: {} calls today, cooldowns restored", todayCallsGenerated.get());
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
