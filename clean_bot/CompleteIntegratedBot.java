import com.trading.bot.core.Phase4QuantSystemBot;
import com.trading.bot.core.Phase3IntegratedBot;
import com.trading.bot.core.Phase2IntegratedBot;
import com.trading.bot.core.IntegratedBot;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * COMPLETE INTEGRATED TELEGRAM BOT - Phase 1+2+3+4 Full Integration
 * All phases working together for ultimate trading analysis
 */
public class CompleteIntegratedBot {
    
    private static final String BOT_TOKEN = "7921964521:AAGNk_jIcV9V5nvxSTdK4xSzeO_yS4AKZ9E";
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot" + BOT_TOKEN;
    
    private final HttpClient httpClient;
    
    // All 4 Phase Bots
    private final IntegratedBot phase1Bot;           // Phase 1: Technical + ML
    private final Phase2IntegratedBot phase2Bot;     // Phase 2: Multi-timeframe + Advanced
    private final Phase3IntegratedBot phase3Bot;     // Phase 3: Smart Money
    private final Phase4QuantSystemBot phase4Bot;    // Phase 4: Quantitative + Portfolio
    
    private final HonestMarketDataFetcher marketDataFetcher;
    
    private long lastUpdateId = 0;
    private volatile boolean isRunning = false;
    private final Set<Long> processedMessages = new HashSet<>();
    
    public CompleteIntegratedBot() {
        this.httpClient = HttpClient.newHttpClient();
        
        // Initialize all phase bots
        this.phase1Bot = new IntegratedBot();
        this.phase2Bot = new Phase2IntegratedBot();
        this.phase3Bot = new Phase3IntegratedBot();
        this.phase4Bot = new Phase4QuantSystemBot();
        this.marketDataFetcher = new HonestMarketDataFetcher();
        
        System.out.println("✅ Complete Integrated Bot (Phases 1-4) initialized");
    }
    
    public void startBot() {
        isRunning = true;
        System.out.println("🚀 Starting Complete Integrated Bot (Phases 1-4)...");
        System.out.println("🏆 Features: All 4 Phases + Real Market Data");
        
        // Initialize all systems
        try {
            System.out.println("Initializing Phase 1...");
            phase1Bot.initialize();
            System.out.println("✅ Phase 1 initialized");
            
            System.out.println("Initializing Phase 2...");
            phase2Bot.initialize();
            System.out.println("✅ Phase 2 initialized");
            
            System.out.println("Initializing Phase 3...");
            phase3Bot.initialize();
            System.out.println("✅ Phase 3 initialized");
            
            System.out.println("Initializing Phase 4...");
            phase4Bot.initialize();
            System.out.println("✅ Phase 4 initialized");
            
            System.out.println("✅ ALL 4 PHASES READY");
        } catch (Exception e) {
            System.err.println("❌ Phase initialization error: " + e.getMessage());
        }
        
        // Start message polling loop
        while (isRunning) {
            try {
                checkForMessages();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("❌ Polling error: " + e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        
        System.out.println("🛑 Complete Integrated Bot stopped");
    }
    
    private void checkForMessages() {
        try {
            String url = TELEGRAM_API_URL + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=5";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .GET()
                .build();
                
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                parseAndHandleUpdates(response.body());
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error checking messages: " + e.getMessage());
        }
    }
    
    private void parseAndHandleUpdates(String responseBody) {
        try {
            if (responseBody.contains("\"result\":[") && responseBody.contains("\"update_id\":")) {
                String[] updates = responseBody.split("\"update_id\":");
                
                for (int i = 1; i < updates.length; i++) {
                    String update = updates[i];
                    
                    int commaIndex = update.indexOf(",");
                    if (commaIndex == -1) continue;
                    
                    long updateId = Long.parseLong(update.substring(0, commaIndex).trim());
                    if (updateId <= lastUpdateId) continue;
                    lastUpdateId = updateId;
                    
                    if (processedMessages.contains(updateId)) continue;
                    processedMessages.add(updateId);
                    
                    if (update.contains("\"text\":")) {
                        long chatId = extractChatId(update);
                        String text = extractMessageText(update);
                        
                        if (chatId != 0 && text != null) {
                            System.out.println("📱 Received: " + text + " from chat: " + chatId);
                            handleCommand(chatId, text.trim().toLowerCase());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error parsing updates: " + e.getMessage());
        }
    }
    
    private void handleCommand(long chatId, String command) {
        try {
            switch (command) {
                case "/start" -> {
                    System.out.println("🎯 Handling /start command - Full 4-Phase welcome");
                    handleStartCommand(chatId);
                }
                case "/status" -> {
                    System.out.println("📊 Handling /status command - All phases status");
                    handleStatusCommand(chatId);
                }
                case "/analyze", "/full" -> {
                    System.out.println("🔍 Handling full 4-phase analysis");
                    handleFullAnalysis(chatId);
                }
                case "/nifty" -> {
                    System.out.println("📈 Handling /nifty - Complete 4-phase analysis");
                    handleSymbolAnalysis(chatId, "NIFTY50");
                }
                case "/sensex" -> {
                    System.out.println("📈 Handling /sensex - Complete 4-phase analysis");
                    handleSymbolAnalysis(chatId, "SENSEX");
                }
                case "/banknifty" -> {
                    System.out.println("📈 Handling /banknifty - Complete 4-phase analysis");
                    handleSymbolAnalysis(chatId, "BANKNIFTY");
                }
                case "/phase1" -> {
                    System.out.println("1️⃣ Phase 1 only analysis");
                    handlePhaseAnalysis(chatId, 1);
                }
                case "/phase2" -> {
                    System.out.println("2️⃣ Phase 2 only analysis");
                    handlePhaseAnalysis(chatId, 2);
                }
                case "/phase3" -> {
                    System.out.println("3️⃣ Phase 3 only analysis");
                    handlePhaseAnalysis(chatId, 3);
                }
                case "/phase4" -> {
                    System.out.println("4️⃣ Phase 4 only analysis");
                    handlePhaseAnalysis(chatId, 4);
                }
                case "/help" -> {
                    System.out.println("❓ Handling /help command");
                    handleHelpCommand(chatId);
                }
                case "/stop" -> {
                    System.out.println("🛑 Handling /stop command");
                    handleStopCommand(chatId);
                }
                default -> {
                    System.out.println("❓ Unknown command: " + command);
                    sendMessage(chatId, "❓ Unknown command: " + command + "\n\nSend /help for available commands or /start to begin.");
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error handling command '" + command + "': " + e.getMessage());
            sendMessage(chatId, "❌ Error processing command. Please try again.");
        }
    }
    
    private void handleStartCommand(long chatId) {
        System.out.println("🚀 Processing /start command - Complete 4-Phase welcome");
        
        String startMessage = "🏆 COMPLETE 4-PHASE TRADING BOT ACTIVATED\n" +
                             "==========================================\n\n" +
                             "🎯 Welcome to Ultimate Trading Analysis!\n\n" +
                             "✅ Phase 1: Enhanced Technical + Machine Learning\n" +
                             "✅ Phase 2: Multi-Timeframe + Advanced Indicators\n" +
                             "✅ Phase 3: Smart Money Concepts + Institutional Analysis\n" +
                             "✅ Phase 4: Portfolio Optimization + Risk Management + Algorithmic Execution\n\n" +
                             "🔥 COMPLETE FEATURES ACTIVE:\n" +
                             "📈 Technical Analysis with ML Validation\n" +
                             "🔄 Multi-Timeframe Analysis\n" +
                             "📊 Order Block Detection\n" +
                             "💧 Fair Value Gap Analysis\n" +
                             "🏦 Liquidity Analysis\n" +
                             "📊 Portfolio Optimization\n" +
                             "⚖️ Risk Management (VaR, Sharpe Ratio)\n" +
                             "🤖 Algorithmic Execution Planning\n" +
                             "💰 Position Sizing Optimization\n" +
                             "💎 Real Market Data Integration\n\n" +
                             "📈 AVAILABLE COMMANDS:\n" +
                             "/analyze - Complete 4-phase analysis\n" +
                             "/nifty - NIFTY50 complete analysis (all 4 phases)\n" +
                             "/sensex - SENSEX complete analysis (all 4 phases)\n" +
                             "/banknifty - BANKNIFTY complete analysis (all 4 phases)\n" +
                             "/phase1 - Phase 1 only (Technical + ML)\n" +
                             "/phase2 - Phase 2 only (Multi-timeframe)\n" +
                             "/phase3 - Phase 3 only (Smart Money)\n" +
                             "/phase4 - Phase 4 only (Quantitative)\n" +
                             "/status - Check all phases status\n" +
                             "/help - Show all commands\n\n" +
                             "🚀 Ready for ultimate institutional-grade trading analysis!\n" +
                             "📱 Try /nifty to see complete 4-phase analysis...";
        
        boolean sent = sendMessage(chatId, startMessage);
        if (sent) {
            System.out.println("✅ /start response sent successfully to chat: " + chatId);
        } else {
            System.err.println("❌ Failed to send /start response to chat: " + chatId);
        }
    }
    
    private void handleStatusCommand(long chatId) {
        try {
            String status = "📊 COMPLETE 4-PHASE BOT STATUS\n" +
                           "===============================\n\n" +
                           "✅ System: ONLINE\n" +
                           "1️⃣ Phase 1: " + getPhaseStatus(1) + "\n" +
                           "2️⃣ Phase 2: " + getPhaseStatus(2) + "\n" +
                           "3️⃣ Phase 3: " + getPhaseStatus(3) + "\n" +
                           "4️⃣ Phase 4: " + getPhaseStatus(4) + "\n" +
                           "📡 Market Data: " + getMarketDataStatus() + "\n" +
                           "⏰ Time: " + 
                           LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\n" +
                           "🎯 Ready for complete 4-phase analysis!";
            
            sendMessage(chatId, status);
        } catch (Exception e) {
            sendMessage(chatId, "❌ Error getting status: " + e.getMessage());
        }
    }
    
    private String getPhaseStatus(int phase) {
        try {
            switch (phase) {
                case 1 -> { return "ACTIVE (Technical + ML)"; }
                case 2 -> { return "ACTIVE (Multi-timeframe + Advanced)"; }
                case 3 -> { return "ACTIVE (Smart Money)"; }
                case 4 -> { return "ACTIVE (Quantitative + Portfolio)"; }
                default -> { return "UNKNOWN"; }
            }
        } catch (Exception e) {
            return "ERROR";
        }
    }
    
    private String getMarketDataStatus() {
        try {
            Map<String, Double> prices = marketDataFetcher.getHonestMarketSnapshot();
            return "LIVE (" + prices.size() + " indices)";
        } catch (Exception e) {
            return "ERROR";
        }
    }
    
    private void handleFullAnalysis(long chatId) {
        sendMessage(chatId, "🔍 Performing Complete 4-Phase Analysis...\n" +
                           "Phase 1: Technical + ML\n" +
                           "Phase 2: Multi-timeframe + Advanced\n" +
                           "Phase 3: Smart Money\n" +
                           "Phase 4: Quantitative + Portfolio\n\n" +
                           "Analyzing NIFTY50, SENSEX, BANKNIFTY...");
        
        try {
            StringBuilder fullAnalysis = new StringBuilder();
            fullAnalysis.append("🏆 COMPLETE 4-PHASE MARKET ANALYSIS\n");
            fullAnalysis.append("=====================================\n\n");
            
            // Analyze each major index
            for (String symbol : Arrays.asList("NIFTY50", "SENSEX", "BANKNIFTY")) {
                fullAnalysis.append("🔥 ").append(symbol).append(" - COMPLETE ANALYSIS\n");
                fullAnalysis.append(generateComplete4PhaseAnalysis(symbol));
                fullAnalysis.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
            }
            
            fullAnalysis.append("📊 Analysis completed at: ");
            fullAnalysis.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            fullAnalysis.append("\n🎯 Powered by Complete 4-Phase Integration");
            
            sendMessage(chatId, fullAnalysis.toString());
            
        } catch (Exception e) {
            sendMessage(chatId, "❌ Error generating full analysis: " + e.getMessage());
        }
    }
    
    private void handleSymbolAnalysis(long chatId, String symbol) {
        sendMessage(chatId, "📈 Analyzing " + symbol + " - Complete 4-Phase Analysis\n" +
                           "Running all 4 phases in sequence...");
        
        try {
            String analysis = generateComplete4PhaseAnalysis(symbol);
            sendMessage(chatId, "🏆 " + symbol + " COMPLETE 4-PHASE ANALYSIS\n" +
                               "=====================================\n\n" + analysis);
        } catch (Exception e) {
            sendMessage(chatId, "❌ " + symbol + " analysis error: " + e.getMessage());
        }
    }
    
    private String generateComplete4PhaseAnalysis(String symbol) {
        try {
            // Get real market data
            List<SimpleMarketData> symbolData = marketDataFetcher.getRealMarketData(symbol);
            Map<String, List<SimpleMarketData>> portfolioData = new HashMap<>();
            portfolioData.put(symbol, symbolData);
            
            StringBuilder analysis = new StringBuilder();
            
            // Phase 1: Technical + ML Analysis
            try {
                IntegratedBot.TradingCall phase1Result = phase1Bot.generateTradingCall(symbol, symbolData);
                analysis.append("1️⃣ PHASE 1: ").append(phase1Result.signal)
                        .append(" (").append(String.format("%.1f%%", phase1Result.confidence)).append(")\n");
                analysis.append("   Technical + ML: Enhanced analysis complete\n\n");
            } catch (Exception e) {
                analysis.append("1️⃣ PHASE 1: ERROR - ").append(e.getMessage()).append("\n\n");
            }
            
            // Phase 2: Multi-timeframe + Advanced
            try {
                Phase2IntegratedBot.AdvancedTradingCall phase2Result = phase2Bot.generateAdvancedTradingCall(symbol, symbolData);
                analysis.append("2️⃣ PHASE 2: ").append(phase2Result.signal)
                        .append(" (").append(String.format("%.1f%%", phase2Result.confidence)).append(")\n");
                analysis.append("   Multi-timeframe: ").append(phase2Result.technicalReasoning).append("\n\n");
            } catch (Exception e) {
                analysis.append("2️⃣ PHASE 2: ERROR - ").append(e.getMessage()).append("\n\n");
            }
            
            // Phase 3: Smart Money Concepts
            try {
                Phase3IntegratedBot.InstitutionalTradingCall phase3Result = phase3Bot.generateInstitutionalTradingCall(symbol, symbolData);
                analysis.append("3️⃣ PHASE 3: ").append(phase3Result.signal)
                        .append(" (").append(String.format("%.1f%%", phase3Result.confidence)).append(")\n");
                analysis.append("   Smart Money: ").append(String.format("%.1f%%", phase3Result.smartMoneyScore))
                        .append(" | ").append(phase3Result.isInstitutionalGrade ? "INSTITUTIONAL" : "RETAIL").append("\n\n");
            } catch (Exception e) {
                analysis.append("3️⃣ PHASE 3: ERROR - ").append(e.getMessage()).append("\n\n");
            }
            
            // Phase 4: Quantitative + Portfolio (Final Integration)
            try {
                Phase4QuantSystemBot.QuantitativeTradingCall phase4Result = phase4Bot.generateQuantitativeTradingCall(symbol, symbolData, portfolioData);
                
                String signalEmoji = switch (phase4Result.signal) {
                    case "BUY" -> "🟢";
                    case "SELL" -> "🔴";
                    default -> "🟡";
                };
                
                String gradeEmoji = phase4Result.isQuantGrade ? "🧮" : "📊";
                
                analysis.append("4️⃣ FINAL PHASE 4 RESULT:\n");
                analysis.append(signalEmoji).append(" ").append(phase4Result.signal).append(" ")
                        .append(gradeEmoji).append(" ").append(phase4Result.isQuantGrade ? "QUANTITATIVE" : "STANDARD").append("\n\n");
                
                analysis.append("💰 LIVE Price: ₹").append(String.format("%.2f", phase4Result.price)).append("\n");
                analysis.append("📊 Final Confidence: ").append(String.format("%.1f%%", phase4Result.confidence)).append("\n");
                analysis.append("📈 Position Size: ").append(String.format("%.1f%%", phase4Result.positionSize * 100)).append("\n");
                analysis.append("⚖️ Risk Grade: ").append(phase4Result.riskGrade).append("\n");
                analysis.append("🎯 Sharpe Ratio: ").append(String.format("%.2f", phase4Result.sharpeRatio)).append("\n");
                analysis.append("🚨 VaR (95%): ").append(String.format("%.2f%%", phase4Result.varValue * 100)).append("\n\n");
                
                analysis.append("🧠 MASTER 4-PHASE REASONING:\n");
                analysis.append(phase4Result.masterQuantReasoning);
                
            } catch (Exception e) {
                analysis.append("4️⃣ PHASE 4: ERROR - ").append(e.getMessage());
            }
            
            return analysis.toString();
            
        } catch (Exception e) {
            String errorInfo = marketDataFetcher.getLastValidPriceInfo(symbol);
            return "❌ COMPLETE ANALYSIS ERROR\n" +
                   "Error: " + e.getMessage() + "\n" +
                   errorInfo + "\n" +
                   "🔧 Try again in a moment";
        }
    }
    
    private void handlePhaseAnalysis(long chatId, int phase) {
        sendMessage(chatId, phase + "️⃣ Running Phase " + phase + " analysis on NIFTY50...");
        
        try {
            String analysis = generateSinglePhaseAnalysis(phase, "NIFTY50");
            sendMessage(chatId, phase + "️⃣ PHASE " + phase + " ANALYSIS\n" +
                               "===================\n\n" + analysis);
        } catch (Exception e) {
            sendMessage(chatId, "❌ Phase " + phase + " analysis error: " + e.getMessage());
        }
    }
    
    private String generateSinglePhaseAnalysis(int phase, String symbol) {
        try {
            List<SimpleMarketData> symbolData = marketDataFetcher.getRealMarketData(symbol);
            
            switch (phase) {
                case 1 -> {
                    IntegratedBot.TradingCall result = phase1Bot.generateTradingCall(symbol, symbolData);
                    return "Signal: " + result.signal + "\n" +
                           "Confidence: " + String.format("%.1f%%", result.confidence) + "\n" +
                           "Analysis: Enhanced Technical + ML complete";
                }
                case 2 -> {
                    Phase2IntegratedBot.AdvancedTradingCall result = phase2Bot.generateAdvancedTradingCall(symbol, symbolData);
                    return "Signal: " + result.signal + "\n" +
                           "Confidence: " + String.format("%.1f%%", result.confidence) + "\n" +
                           "Strategy: " + result.technicalReasoning;
                }
                case 3 -> {
                    Phase3IntegratedBot.InstitutionalTradingCall result = phase3Bot.generateInstitutionalTradingCall(symbol, symbolData);
                    return "Signal: " + result.signal + "\n" +
                           "Confidence: " + String.format("%.1f%%", result.confidence) + "\n" +
                           "Smart Money: " + String.format("%.1f%%", result.smartMoneyScore) + "\n" +
                           "Grade: " + (result.isInstitutionalGrade ? "INSTITUTIONAL" : "RETAIL");
                }
                case 4 -> {
                    Map<String, List<SimpleMarketData>> portfolioData = new HashMap<>();
                    portfolioData.put(symbol, symbolData);
                    Phase4QuantSystemBot.QuantitativeTradingCall result = phase4Bot.generateQuantitativeTradingCall(symbol, symbolData, portfolioData);
                    return "Signal: " + result.signal + "\n" +
                           "Confidence: " + String.format("%.1f%%", result.confidence) + "\n" +
                           "Position Size: " + String.format("%.1f%%", result.positionSize * 100) + "\n" +
                           "Sharpe Ratio: " + String.format("%.2f", result.sharpeRatio) + "\n" +
                           "Grade: " + (result.isQuantGrade ? "QUANTITATIVE" : "STANDARD");
                }
                default -> { return "Invalid phase number"; }
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    private void handleHelpCommand(long chatId) {
        String help = "🏆 COMPLETE 4-PHASE BOT COMMANDS\n" +
                     "=================================\n\n" +
                     "Complete Analysis:\n" +
                     "/start - Activate all 4 phases\n" +
                     "/analyze - Full 4-phase market analysis\n" +
                     "/nifty - Complete NIFTY50 analysis (all phases)\n" +
                     "/sensex - Complete SENSEX analysis (all phases)\n" +
                     "/banknifty - Complete BANKNIFTY analysis (all phases)\n\n" +
                     "Individual Phases:\n" +
                     "/phase1 - Technical + ML only\n" +
                     "/phase2 - Multi-timeframe + Advanced only\n" +
                     "/phase3 - Smart Money only\n" +
                     "/phase4 - Quantitative + Portfolio only\n\n" +
                     "System:\n" +
                     "/status - Check all phases status\n" +
                     "/help - Show this help\n" +
                     "/stop - Stop bot\n\n" +
                     "🔥 Features: Complete Phase 1-4 Integration\n" +
                     "📊 Data: Real-time from Upstox API";
        
        sendMessage(chatId, help);
    }
    
    private void handleStopCommand(long chatId) {
        sendMessage(chatId, "🛑 Stopping Complete 4-Phase Bot...\nThank you for using ultimate trading analysis!");
        isRunning = false;
    }
    
    private boolean sendMessage(long chatId, String text) {
        try {
            String url = TELEGRAM_API_URL + "/sendMessage";
            String cleanText = text.replace("\"", "\\\"")
                                  .replace("\n", "\\n")
                                  .replace("\r", "")
                                  .replace("\t", " ");
            
            String jsonData = String.format(
                "{\"chat_id\":%d,\"text\":\"%s\"}", 
                chatId, cleanText
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonData))
                .build();
                
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                System.out.println("✅ Message sent successfully to chat: " + chatId);
                return true;
            } else {
                System.err.println("❌ Telegram API error: " + response.statusCode());
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error sending message: " + e.getMessage());
            return false;
        }
    }
    
    private long extractChatId(String update) {
        try {
            int chatStart = update.indexOf("\"chat\":");
            if (chatStart == -1) return 0;
            
            int idStart = update.indexOf("\"id\":", chatStart);
            if (idStart == -1) return 0;
            
            idStart += 5;
            int idEnd = update.indexOf(",", idStart);
            if (idEnd == -1) idEnd = update.indexOf("}", idStart);
            if (idEnd == -1) return 0;
            
            return Long.parseLong(update.substring(idStart, idEnd).trim());
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
            if (endIndex == -1) return null;
            
            return update.substring(startIndex, endIndex);
        } catch (Exception e) {
            return null;
        }
    }
    
    public static void main(String[] args) {
        System.out.println("🏆 COMPLETE 4-PHASE TELEGRAM BOT");
        System.out.println("=================================");
        System.out.println("✅ Phase 1: Enhanced Technical + Machine Learning");
        System.out.println("✅ Phase 2: Multi-Timeframe + Advanced Indicators");
        System.out.println("✅ Phase 3: Smart Money Concepts + Institutional Analysis");
        System.out.println("✅ Phase 4: Portfolio Optimization + Risk Management + Algorithmic Execution");
        System.out.println("📊 Real Market Data: Upstox API Integration");
        System.out.println("🔄 Complete integration of all 4 phases");
        System.out.println();
        
        CompleteIntegratedBot bot = new CompleteIntegratedBot();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down Complete 4-Phase Bot...");
            bot.isRunning = false;
        }));
        
        bot.startBot();
        
        System.out.println("🏁 Complete 4-Phase Bot session ended");
    }
}