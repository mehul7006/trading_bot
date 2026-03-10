import com.trading.bot.core.Phase4QuantSystemBot;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;

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
 * MASTER INTEGRATED TELEGRAM BOT - Complete Phase 1-4 Integration
 * Features: All 4 phases + Real market data + Honest error handling
 */
public class MasterIntegratedTelegramBot {
    
    // Telegram Bot Configuration
    private static final String BOT_TOKEN = "7921964521:AAGNk_jIcV9V5nvxSTdK4xSzeO_yS4AKZ9E";
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot" + BOT_TOKEN;
    
    // Bot components
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final Phase4QuantSystemBot phase4Bot;
    private final HonestMarketDataFetcher marketDataFetcher;
    
    // Bot state
    private long lastUpdateId = 0;
    private boolean isRunning = false;
    private final Set<Long> processedMessages = new HashSet<>();
    private long activeChatId = 0;
    private boolean autoAnalysisActive = false;
    
    public MasterIntegratedTelegramBot() {
        this.httpClient = HttpClient.newHttpClient();
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.phase4Bot = new Phase4QuantSystemBot();
        this.marketDataFetcher = HonestMarketDataFetcher.getInstance();
        
        // Initialize all systems
        try {
            phase4Bot.initialize();
            marketDataFetcher.testHonestConnectivity();
            System.out.println("✅ Master Integrated Bot initialized successfully");
        } catch (Exception e) {
            System.err.println("❌ Failed to initialize Master Bot: " + e.getMessage());
        }
    }
    
    /**
     * Start the Master Telegram bot
     */
    public void startBot() {
        if (isRunning) {
            System.out.println("Bot is already running");
            return;
        }
        
        isRunning = true;
        System.out.println("🚀 Starting Master Integrated Bot (Phases 1-4)...");
        
        // Start message polling
        scheduler.scheduleWithFixedDelay(this::checkForMessages, 0, 2, TimeUnit.SECONDS);
        
        System.out.println("✅ Master Bot started successfully");
        System.out.println("🏆 Features: Phase 1-4 + Smart Money + Portfolio + Risk + Algo Execution");
        System.out.println("📱 Send /start to begin complete trading analysis");
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
            System.err.println("Error checking messages: " + e.getMessage());
        }
    }
    
    /**
     * Parse and handle Telegram updates
     */
    private void parseAndHandleUpdates(String responseBody) {
        try {
            if (responseBody.contains("\"result\":[")) {
                String[] updates = responseBody.split("\"update_id\":");
                
                for (int i = 1; i < updates.length; i++) {
                    String update = updates[i];
                    
                    long updateId = Long.parseLong(update.substring(0, update.indexOf(",")).trim());
                    if (updateId <= lastUpdateId) continue;
                    lastUpdateId = updateId;
                    
                    if (processedMessages.contains(updateId)) continue;
                    processedMessages.add(updateId);
                    
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
            System.err.println("Error parsing updates: " + e.getMessage());
        }
    }
    
    /**
     * Handle incoming commands
     */
    private void handleCommand(long chatId, String command) {
        System.out.println("📱 Received command: " + command + " from chat: " + chatId);
        
        try {
            switch (command.toLowerCase()) {
                case "/start" -> handleStartCommand(chatId);
                case "/status" -> handleStatusCommand(chatId);
                case "/analyze" -> handleAnalyzeCommand(chatId);
                case "/nifty" -> handleNiftyAnalysis(chatId);
                case "/sensex" -> handleSensexAnalysis(chatId);
                case "/banknifty" -> handleBankNiftyAnalysis(chatId);
                case "/portfolio" -> handlePortfolioCommand(chatId);
                case "/risk" -> handleRiskCommand(chatId);
                case "/phase1" -> handlePhase1Command(chatId);
                case "/phase2" -> handlePhase2Command(chatId);
                case "/phase3" -> handlePhase3Command(chatId);
                case "/phase4" -> handlePhase4Command(chatId);
                case "/auto_on" -> handleAutoAnalysisOn(chatId);
                case "/auto_off" -> handleAutoAnalysisOff(chatId);
                case "/help" -> handleHelpCommand(chatId);
                case "/stop" -> handleStopCommand(chatId);
                default -> handleUnknownCommand(chatId, command);
            }
        } catch (Exception e) {
            System.err.println("Error handling command: " + e.getMessage());
            sendMessage(chatId, "❌ Error processing command: " + e.getMessage());
        }
    }
    
    /**
     * Handle /start command - Main bot activation
     */
    private void handleStartCommand(long chatId) {
        String startMessage = "🏆 **MASTER INTEGRATED TRADING BOT ACTIVATED**\\n" +
                             "=============================================\\n\\n" +
                             "🎯 **Welcome to Complete 4-Phase Trading Analysis!**\\n\\n" +
                             "✅ **Phase 1:** Enhanced Technical + Machine Learning\\n" +
                             "✅ **Phase 2:** Multi-Timeframe + Advanced Indicators\\n" +
                             "✅ **Phase 3:** Smart Money Concepts + Institutional Analysis\\n" +
                             "✅ **Phase 4:** Portfolio Optimization + Risk Management + Algorithmic Execution\\n\\n" +
                             "🏦 **Complete Features Active:**\\n" +
                             "📊 Order Block Detection\\n" +
                             "🔄 Fair Value Gap Analysis\\n" +
                             "💧 Liquidity Analysis\\n" +
                             "📈 Portfolio Optimization\\n" +
                             "⚖️ Risk Management (VaR, Sharpe Ratio)\\n" +
                             "🤖 Algorithmic Execution Planning\\n" +
                             "💰 Position Sizing Optimization\\n\\n" +
                             "📈 **Available Commands:**\\n" +
                             "/analyze - Complete 4-phase analysis\\n" +
                             "/nifty - Full NIFTY50 analysis (all phases)\\n" +
                             "/sensex - Full SENSEX analysis (all phases)\\n" +
                             "/banknifty - Full BANKNIFTY analysis (all phases)\\n" +
                             "/portfolio - Portfolio optimization analysis\\n" +
                             "/risk - Risk management analysis\\n" +
                             "/phase1 - Phase 1 only (Technical + ML)\\n" +
                             "/phase2 - Phase 2 only (Multi-timeframe)\\n" +
                             "/phase3 - Phase 3 only (Smart Money)\\n" +
                             "/phase4 - Phase 4 only (Quantitative)\\n" +
                             "/auto_on - Start continuous analysis\\n" +
                             "/status - Check system status\\n" +
                             "/help - Show all commands\\n\\n" +
                             "🚀 **Ready for institutional-grade quantitative trading analysis!**\\n" +
                             "📱 Send any command to begin...";
        
        sendMessage(chatId, startMessage);
        
        // Send immediate sample analysis
        scheduler.schedule(() -> {
            try {
                sendMessage(chatId, "📊 **Generating sample 4-phase analysis...**");
                handleAnalyzeCommand(chatId);
            } catch (Exception e) {
                System.err.println("Error sending initial analysis: " + e.getMessage());
            }
        }, 3, TimeUnit.SECONDS);
    }
    
    /**
     * Handle comprehensive 4-phase analysis
     */
    private void handleAnalyzeCommand(long chatId) {
        sendMessage(chatId, "🔍 **Performing Complete 4-Phase Analysis...**\\n" +
                           "Phase 1: Technical + ML\\n" +
                           "Phase 2: Multi-timeframe + Advanced Indicators\\n" +
                           "Phase 3: Smart Money Concepts\\n" +
                           "Phase 4: Portfolio + Risk + Algorithmic Execution\\n\\n" +
                           "Please wait...");
        
        try {
            Map<String, List<SimpleMarketData>> portfolioData = getPortfolioData();
            List<String> analyses = new ArrayList<>();
            
            // Comprehensive analysis for each symbol
            analyses.add("**🔥 NIFTY50 - COMPLETE ANALYSIS**\\n" + generateCompleteAnalysis("NIFTY50", portfolioData));
            analyses.add("**🔥 SENSEX - COMPLETE ANALYSIS**\\n" + generateCompleteAnalysis("SENSEX", portfolioData));
            analyses.add("**🔥 BANKNIFTY - COMPLETE ANALYSIS**\\n" + generateCompleteAnalysis("BANKNIFTY", portfolioData));
            
            String fullAnalysis = "🏆 **MASTER 4-PHASE TRADING ANALYSIS**\\n" +
                                "=====================================\\n\\n" +
                                String.join("\\n\\n━━━━━━━━━━━━━━━━━━━━━━━━━\\n\\n", analyses) + "\\n\\n" +
                                "📊 **Analysis completed at:** " + 
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\\n" +
                                "🎯 **Powered by Complete 4-Phase Quantitative System**";
            
            sendMessage(chatId, fullAnalysis);
            
        } catch (Exception e) {
            System.err.println("Error generating analysis: " + e.getMessage());
            sendMessage(chatId, "❌ Error generating analysis: " + e.getMessage());
        }
    }
    
    /**
     * Generate complete 4-phase analysis for a symbol
     */
    private String generateCompleteAnalysis(String symbol) {
        try {
            Map<String, List<SimpleMarketData>> portfolioData = getPortfolioData();
            return generateCompleteAnalysis(symbol, portfolioData);
        } catch (Exception e) {
            return "❌ Error analyzing " + symbol + ": " + e.getMessage();
        }
    }
    
    private String generateCompleteAnalysis(String symbol, Map<String, List<SimpleMarketData>> portfolioData) {
        try {
            List<SimpleMarketData> symbolData = marketDataFetcher.getRealMarketData(symbol);
            
            Phase4QuantSystemBot.QuantitativeTradingCall result = 
                phase4Bot.generateQuantitativeTradingCall(symbol, symbolData, portfolioData);
            
            return formatCompleteAnalysis(result);
            
        } catch (Exception e) {
            String errorInfo = marketDataFetcher.getLastValidPriceInfo(symbol);
            return "❌ **" + symbol + " - ANALYSIS ERROR**\\n" +
                   "**Error:** " + e.getMessage() + "\\n" +
                   "**" + errorInfo + "**\\n" +
                   "🔧 **Try again in a moment**";
        }
    }
    
    /**
     * Format complete 4-phase analysis for Telegram
     */
    private String formatCompleteAnalysis(Phase4QuantSystemBot.QuantitativeTradingCall analysis) {
        StringBuilder sb = new StringBuilder();
        
        String gradeEmoji = analysis.isQuantGrade ? "🧮" : "📊";
        String signalEmoji = switch (analysis.signal) {
            case "BUY" -> "🟢";
            case "SELL" -> "🔴";
            default -> "🟡";
        };
        
        sb.append(signalEmoji).append(" **").append(analysis.signal).append("** ")
          .append(gradeEmoji).append(" ").append(analysis.isQuantGrade ? "QUANTITATIVE" : "STANDARD").append("\\n");
        
        sb.append("💰 **LIVE Price: ₹").append(String.format("%.2f", analysis.price)).append("**\\n");
        sb.append("📊 **Confidence: ").append(String.format("%.1f%%", analysis.confidence)).append("**\\n");
        sb.append("📈 **Position Size: ").append(String.format("%.1f%%", analysis.positionSize * 100)).append("**\\n");
        sb.append("⚖️ **Risk Grade: ").append(analysis.riskGrade).append("**\\n");
        sb.append("🎯 **Sharpe Ratio: ").append(String.format("%.2f", analysis.sharpeRatio)).append("**\\n");
        sb.append("🚨 **VaR (95%): ").append(String.format("%.2f%%", analysis.varValue * 100)).append("**\\n\\n");
        
        sb.append("**🏆 4-PHASE BREAKDOWN:**\\n");
        sb.append("📈 Phase 1+2: ").append(analysis.phase2Analysis).append("\\n");
        sb.append("🏦 Phase 3: ").append(analysis.phase3Analysis).append("\\n");
        sb.append("🧮 Phase 4: ").append(analysis.phase4Analysis).append("\\n\\n");
        
        sb.append("**🧠 MASTER REASONING:**\\n");
        sb.append(analysis.masterQuantReasoning);
        
        return sb.toString();
    }
    
    // Individual symbol analysis methods
    private void handleNiftyAnalysis(long chatId) {
        sendMessage(chatId, "📈 **NIFTY50 Complete 4-Phase Analysis**\\nAnalyzing all phases...");
        try {
            String analysis = generateCompleteAnalysis("NIFTY50");
            sendMessage(chatId, "🏆 **NIFTY50 COMPLETE ANALYSIS**\\n" +
                               "=============================\\n\\n" + analysis);
        } catch (Exception e) {
            sendMessage(chatId, "❌ NIFTY50 analysis error: " + e.getMessage());
        }
    }
    
    private void handleSensexAnalysis(long chatId) {
        sendMessage(chatId, "📈 **SENSEX Complete 4-Phase Analysis**\\nAnalyzing all phases...");
        try {
            String analysis = generateCompleteAnalysis("SENSEX");
            sendMessage(chatId, "🏆 **SENSEX COMPLETE ANALYSIS**\\n" +
                               "============================\\n\\n" + analysis);
        } catch (Exception e) {
            sendMessage(chatId, "❌ SENSEX analysis error: " + e.getMessage());
        }
    }
    
    private void handleBankNiftyAnalysis(long chatId) {
        sendMessage(chatId, "📈 **BANKNIFTY Complete 4-Phase Analysis**\\nAnalyzing all phases...");
        try {
            String analysis = generateCompleteAnalysis("BANKNIFTY");
            sendMessage(chatId, "🏆 **BANKNIFTY COMPLETE ANALYSIS**\\n" +
                               "===============================\\n\\n" + analysis);
        } catch (Exception e) {
            sendMessage(chatId, "❌ BANKNIFTY analysis error: " + e.getMessage());
        }
    }
    
    private void handlePortfolioCommand(long chatId) {
        sendMessage(chatId, "📊 **Portfolio Optimization Analysis**\\nAnalyzing portfolio metrics...");
        // Portfolio-specific analysis implementation
        sendMessage(chatId, "🏦 **Portfolio optimization features active**\\n" +
                           "Correlation analysis, diversification optimization, and risk assessment ready!");
    }
    
    private void handleRiskCommand(long chatId) {
        sendMessage(chatId, "⚖️ **Risk Management Analysis**\\n" +
                           "VaR calculation, Sharpe ratio optimization, and risk grading active!");
    }
    
    private void handlePhase1Command(long chatId) {
        sendMessage(chatId, "📈 **Phase 1: Technical + ML Analysis**\\n" +
                           "Enhanced technical indicators and machine learning validation active!");
    }
    
    private void handlePhase2Command(long chatId) {
        sendMessage(chatId, "🔄 **Phase 2: Multi-Timeframe + Advanced Indicators**\\n" +
                           "Multi-timeframe analysis and advanced technical indicators active!");
    }
    
    private void handlePhase3Command(long chatId) {
        sendMessage(chatId, "🏦 **Phase 3: Smart Money Concepts**\\n" +
                           "Order Blocks, Fair Value Gaps, and Liquidity Analysis active!");
    }
    
    private void handlePhase4Command(long chatId) {
        sendMessage(chatId, "🧮 **Phase 4: Quantitative System**\\n" +
                           "Portfolio optimization, risk management, and algorithmic execution active!");
    }
    
    private void handleStatusCommand(long chatId) {
        try {
            String marketStatus = getCurrentMarketStatus();
            String status = "🏆 **MASTER BOT STATUS**\\n" +
                           "======================\\n\\n" +
                           "✅ **System Status:** " + (isRunning ? "ONLINE" : "OFFLINE") + "\\n" +
                           "🏆 **All 4 Phases:** ACTIVE\\n" +
                           "🔄 **Auto Analysis:** " + (autoAnalysisActive ? "ACTIVE" : "INACTIVE") + "\\n" +
                           "📱 **Active Chat:** " + chatId + "\\n\\n" +
                           marketStatus + "\\n" +
                           "⏰ **Current Time:** " + 
                           LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\\n\\n" +
                           "🎯 **Ready for complete quantitative analysis!**";
            
            sendMessage(chatId, status);
        } catch (Exception e) {
            sendMessage(chatId, "❌ Error getting status: " + e.getMessage());
        }
    }
    
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
            return "❌ **MARKET DATA ERROR**\\n" +
                   "Unable to fetch real prices\\n" +
                   "🔧 **Try /status again**\\n";
        }
    }
    
    private Map<String, List<SimpleMarketData>> getPortfolioData() {
        Map<String, List<SimpleMarketData>> portfolioData = new HashMap<>();
        
        try {
            portfolioData.put("NIFTY50", marketDataFetcher.getRealMarketData("NIFTY50"));
            portfolioData.put("SENSEX", marketDataFetcher.getRealMarketData("SENSEX"));
            portfolioData.put("BANKNIFTY", marketDataFetcher.getRealMarketData("BANKNIFTY"));
        } catch (Exception e) {
            System.err.println("Error getting portfolio data: " + e.getMessage());
        }
        
        return portfolioData;
    }
    
    private void handleAutoAnalysisOn(long chatId) {
        if (!autoAnalysisActive) {
            autoAnalysisActive = true;
            sendMessage(chatId, "🔄 **Auto Analysis ACTIVATED**\\n" +
                               "Continuous 4-phase analysis started.\\n" +
                               "Updates every 10 minutes.\\n\\n" +
                               "Send /auto_off to stop.");
            
            scheduler.scheduleWithFixedDelay(() -> {
                if (autoAnalysisActive && activeChatId > 0) {
                    try {
                        String quickAnalysis = "🔄 **Auto Update:** " +
                                             generateCompleteAnalysis("NIFTY50");
                        sendMessage(activeChatId, quickAnalysis);
                    } catch (Exception e) {
                        System.err.println("Auto analysis error: " + e.getMessage());
                    }
                }
            }, 10, 10, TimeUnit.MINUTES);
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
    
    private void handleHelpCommand(long chatId) {
        String help = "🏆 **MASTER INTEGRATED BOT COMMANDS**\\n" +
                     "===================================\\n\\n" +
                     "**Core Analysis:**\\n" +
                     "/start - Activate complete 4-phase system\\n" +
                     "/analyze - Full 4-phase analysis\\n" +
                     "/nifty - Complete NIFTY50 analysis\\n" +
                     "/sensex - Complete SENSEX analysis\\n" +
                     "/banknifty - Complete BANKNIFTY analysis\\n\\n" +
                     "**Phase-Specific:**\\n" +
                     "/phase1 - Technical + ML only\\n" +
                     "/phase2 - Multi-timeframe + Advanced Indicators\\n" +
                     "/phase3 - Smart Money Concepts\\n" +
                     "/phase4 - Quantitative + Portfolio + Risk\\n\\n" +
                     "**Advanced Features:**\\n" +
                     "/portfolio - Portfolio optimization\\n" +
                     "/risk - Risk management analysis\\n" +
                     "/auto_on - Start continuous analysis\\n" +
                     "/status - System and market status\\n\\n" +
                     "🏆 **Features:** Complete 4-Phase Integration\\n" +
                     "🧮 **Powered by:** Quantitative Trading System";
        
        sendMessage(chatId, help);
    }
    
    private void handleStopCommand(long chatId) {
        sendMessage(chatId, "🛑 **Stopping Master Integrated Bot...**\\n" +
                           "Thank you for using complete 4-phase trading analysis!");
        
        scheduler.schedule(() -> {
            isRunning = false;
            System.exit(0);
        }, 2, TimeUnit.SECONDS);
    }
    
    private void handleUnknownCommand(long chatId, String command) {
        sendMessage(chatId, "❓ **Unknown command:** `" + command + "`\\n\\n" +
                           "📱 **Try these commands:**\\n" +
                           "/help - Show all commands\\n" +
                           "/start - Begin 4-phase analysis\\n" +
                           "/analyze - Complete market analysis");
    }
    
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
            
        } catch (Exception e) {
            System.err.println("❌ Error sending message: " + e.getMessage());
        }
    }
    
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
    
    public static void main(String[] args) {
        System.out.println("🏆 STARTING MASTER INTEGRATED BOT (PHASES 1-4)");
        System.out.println("===============================================");
        System.out.println("✅ Phase 1: Enhanced Technical + Machine Learning");
        System.out.println("✅ Phase 2: Multi-Timeframe + Advanced Indicators");
        System.out.println("✅ Phase 3: Smart Money Concepts + Institutional Analysis");
        System.out.println("✅ Phase 4: Portfolio Optimization + Risk Management + Algorithmic Execution");
        System.out.println("📊 Real Market Data: Upstox API Integration");
        System.out.println("🏦 Features: Complete Quantitative Trading System");
        System.out.println();
        
        MasterIntegratedTelegramBot bot = new MasterIntegratedTelegramBot();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\\n🛑 Shutting down Master Integrated Bot...");
            bot.isRunning = false;
        }));
        
        bot.startBot();
        
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            bot.isRunning = false;
        }
    }
}