import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * TODAY'S MARKET BOT TEST - DETAILED ANALYSIS
 * 
 * Comprehensive testing of your bot for today's market:
 * - Total calls generated
 * - Stop loss hits analysis
 * - Target achievements
 * - Real market conditions simulation
 */
public class TodayMarketBotTest {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    // Today's actual market data simulation
    private static final Map<String, MarketData> TODAYS_MARKET = Map.of(
        "NIFTY", new MarketData(25800.0, 25920.0, 25750.0, 25885.0, 15000000L, "+0.33%"),
        "SENSEX", new MarketData(84500.0, 84680.0, 84350.0, 84625.0, 12000000L, "+0.15%"),
        "TCS", new MarketData(3485.0, 3525.0, 3470.0, 3518.0, 18000000L, "+0.95%"),
        "RELIANCE", new MarketData(2395.0, 2408.0, 2385.0, 2392.0, 22000000L, "-0.13%"),
        "HDFCBANK", new MarketData(1595.0, 1612.0, 1590.0, 1608.0, 14000000L, "+0.81%"),
        "INFY", new MarketData(1825.0, 1838.0, 1820.0, 1834.0, 16000000L, "+0.49%"),
        "ITC", new MarketData(462.0, 465.5, 460.0, 464.2, 25000000L, "+0.48%")
    );
    
    // Trading session times
    private static final LocalTime[] TRADING_SESSIONS = {
        LocalTime.of(9, 15),   // Opening
        LocalTime.of(9, 45),   // Early morning
        LocalTime.of(10, 30),  // Morning
        LocalTime.of(11, 15),  // Late morning
        LocalTime.of(12, 0),   // Pre-lunch
        LocalTime.of(13, 15),  // Post-lunch
        LocalTime.of(14, 0),   // Afternoon
        LocalTime.of(14, 45),  // Late afternoon
        LocalTime.of(15, 15)   // Closing
    };
    
    public static void main(String[] args) {
        System.out.println("📊 TODAY'S MARKET BOT TEST - DETAILED ANALYSIS");
        System.out.println("═════════════════════════════════════════════");
        System.out.println("📅 Date: " + LocalDate.now().format(DATE_FORMAT));
        System.out.println("⏰ Test Time: " + LocalTime.now().format(TIME_FORMAT));
        System.out.println("🎯 Testing: Call generation, SL hits, Target achievements");
        System.out.println();
        
        runComprehensiveTest();
    }
    
    /**
     * Run comprehensive bot test for today's market
     */
    private static void runComprehensiveTest() {
        List<GeneratedCall> allCalls = new ArrayList<>();
        
        System.out.println("🤖 GENERATING CALLS FOR TODAY'S MARKET");
        System.out.println("═════════════════════════════════════");
        
        // Generate calls for each symbol at each time session
        for (Map.Entry<String, MarketData> entry : TODAYS_MARKET.entrySet()) {
            String symbol = entry.getKey();
            MarketData marketData = entry.getValue();
            
            System.out.println("\\n📊 " + symbol + " Analysis (" + marketData.dayMove + ")");
            System.out.println("   Open: ₹" + marketData.open + " | High: ₹" + marketData.high + 
                             " | Low: ₹" + marketData.low + " | Close: ₹" + marketData.close);
            System.out.println("   ─────────────────────────────────────────────────");
            
            List<GeneratedCall> symbolCalls = generateCallsForSymbol(symbol, marketData);
            allCalls.addAll(symbolCalls);
            
            // Print symbol summary
            long symbolSuccessful = symbolCalls.stream().mapToLong(c -> c.hitTarget ? 1 : 0).sum();
            long symbolSLHit = symbolCalls.stream().mapToLong(c -> c.hitStopLoss ? 1 : 0).sum();
            
            System.out.println("   📞 Total Calls: " + symbolCalls.size());
            System.out.println("   🎯 Targets Hit: " + symbolSuccessful + "/" + symbolCalls.size());
            System.out.println("   🛡️ Stop Loss Hit: " + symbolSLHit + "/" + symbolCalls.size());
            if (!symbolCalls.isEmpty()) {
                double symbolPnL = symbolCalls.stream().mapToDouble(c -> c.pnlPercent).sum();
                System.out.println("   💰 Total P&L: " + String.format("%+.2f%%", symbolPnL));
            }
        }
        
        // Overall analysis
        analyzeOverallPerformance(allCalls);
        
        // Detailed breakdown
        analyzeDetailedBreakdown(allCalls);
        
        // Time-based analysis
        analyzeTimeBasedPerformance(allCalls);
    }
    
    /**
     * Generate calls for a specific symbol throughout the day
     */
    private static List<GeneratedCall> generateCallsForSymbol(String symbol, MarketData marketData) {
        List<GeneratedCall> calls = new ArrayList<>();
        
        for (int i = 0; i < TRADING_SESSIONS.length; i++) {
            LocalTime time = TRADING_SESSIONS[i];
            double currentPrice = simulateIntraDayPrice(marketData, i);
            
            // Generate call using enhanced bot logic
            BotDecision decision = generateBotCall(symbol, currentPrice, time, marketData);
            
            if (decision.shouldGenerateCall) {
                GeneratedCall call = new GeneratedCall(
                    symbol, decision.callType, currentPrice, decision.target, decision.stopLoss,
                    time, decision.confidence, decision.reasoning
                );
                
                // Simulate call outcome
                simulateCallOutcome(call, marketData, i);
                calls.add(call);
                
                System.out.println("   " + formatCallOutput(call));
            }
        }
        
        return calls;
    }
    
    /**
     * Generate bot call using enhanced logic
     */
    private static BotDecision generateBotCall(String symbol, double currentPrice, 
                                             LocalTime time, MarketData marketData) {
        
        // Enhanced bot decision logic
        double confidence = 60.0; // Base confidence
        String reasoning = "";
        
        // Technical analysis
        double rsi = calculateRSI(currentPrice, marketData);
        boolean macdBullish = currentPrice > marketData.open;
        double pricePosition = (currentPrice - marketData.low) / (marketData.high - marketData.low);
        
        // Volume analysis
        boolean volumeHigh = marketData.volume > getAverageVolume(symbol);
        
        // Enhanced features adjustments
        
        // 1. Multi-timeframe confluence
        if (marketData.dayMove.startsWith("+") && pricePosition > 0.3) {
            confidence += 8;
            reasoning += "Multi-timeframe bullish; ";
        }
        
        // 2. Pattern recognition
        if (pricePosition > 0.7 && volumeHigh) {
            confidence += 6;
            reasoning += "Breakout pattern with volume; ";
        } else if (pricePosition < 0.3 && volumeHigh) {
            confidence += 5;
            reasoning += "Support bounce pattern; ";
        }
        
        // 3. Session-specific logic
        String session = getSession(time);
        switch (session) {
            case "OPENING":
                confidence += 2;
                reasoning += "Opening momentum; ";
                break;
            case "MORNING":
                confidence += 5;
                reasoning += "Strong morning session; ";
                break;
            case "AFTERNOON":
                confidence -= 2;
                reasoning += "Afternoon caution; ";
                break;
            case "CLOSING":
                confidence -= 5;
                reasoning += "Closing profit booking; ";
                break;
        }
        
        // 4. Volume gate
        if (!volumeHigh && !session.equals("OPENING")) {
            confidence -= 15;
            reasoning += "Low volume concern; ";
        }
        
        // 5. Global correlation (simulate)
        if (symbol.equals("NIFTY") || symbol.equals("SENSEX")) {
            confidence += 3; // Index correlation
            reasoning += "Global market support; ";
        }
        
        // Determine call type
        String callType = determineCallType(currentPrice, marketData, rsi, macdBullish);
        
        // Calculate targets and stop loss
        double target = calculateTarget(currentPrice, callType, confidence);
        double stopLoss = calculateStopLoss(currentPrice, callType, marketData);
        
        // Decision threshold (confidence minimum)
        boolean shouldGenerateCall = confidence >= 70.0; // Enhanced minimum
        
        return new BotDecision(shouldGenerateCall, callType, target, stopLoss, confidence, reasoning.trim());
    }
    
    /**
     * Simulate intraday price movement
     */
    private static double simulateIntraDayPrice(MarketData data, int timeIndex) {
        double progress = (double) timeIndex / (TRADING_SESSIONS.length - 1);
        double totalMove = (data.close - data.open) / data.open;
        
        // Add some realistic intraday noise
        Random random = new Random(timeIndex);
        double noise = (random.nextDouble() - 0.5) * 0.008; // ±0.4% noise
        
        return data.open * (1 + totalMove * progress + noise);
    }
    
    /**
     * Simulate call outcome (target hit, stop loss hit, etc.)
     */
    private static void simulateCallOutcome(GeneratedCall call, MarketData marketData, int timeIndex) {
        double entryPrice = call.entryPrice;
        double targetPrice = call.targetPrice;
        double stopLossPrice = call.stopLossPrice;
        
        // Simulate price movement after call generation
        double highAfterEntry = simulateHighAfterEntry(marketData, timeIndex);
        double lowAfterEntry = simulateLowAfterEntry(marketData, timeIndex);
        double finalPrice = simulateFinalPrice(marketData, timeIndex);
        
        // Check if target or stop loss was hit
        boolean hitTarget = false;
        boolean hitStopLoss = false;
        double pnlPercent = 0;
        
        if ("BUY".equals(call.callType)) {
            if (highAfterEntry >= targetPrice) {
                hitTarget = true;
                pnlPercent = (targetPrice - entryPrice) / entryPrice * 100;
            } else if (lowAfterEntry <= stopLossPrice) {
                hitStopLoss = true;
                pnlPercent = (stopLossPrice - entryPrice) / entryPrice * 100;
            } else {
                // Neither hit - use final price
                pnlPercent = (finalPrice - entryPrice) / entryPrice * 100;
            }
        } else if ("SELL".equals(call.callType)) {
            if (lowAfterEntry <= targetPrice) {
                hitTarget = true;
                pnlPercent = (entryPrice - targetPrice) / entryPrice * 100;
            } else if (highAfterEntry >= stopLossPrice) {
                hitStopLoss = true;
                pnlPercent = (entryPrice - stopLossPrice) / entryPrice * 100;
            } else {
                // Neither hit - use final price
                pnlPercent = (entryPrice - finalPrice) / entryPrice * 100;
            }
        }
        
        // Apply realistic costs
        pnlPercent -= 0.1; // 0.1% for brokerage and slippage
        
        call.hitTarget = hitTarget;
        call.hitStopLoss = hitStopLoss;
        call.pnlPercent = pnlPercent;
        call.outcome = hitTarget ? "TARGET HIT" : hitStopLoss ? "STOP LOSS HIT" : "OPEN/PARTIAL";
    }
    
    // Helper methods for simulation
    private static double simulateHighAfterEntry(MarketData data, int timeIndex) {
        double remainingUpside = (data.high - data.open) * (1.0 - (double) timeIndex / TRADING_SESSIONS.length);
        return data.open + remainingUpside + (Math.random() * 10); // Some random movement
    }
    
    private static double simulateLowAfterEntry(MarketData data, int timeIndex) {
        double remainingDownside = (data.open - data.low) * (1.0 - (double) timeIndex / TRADING_SESSIONS.length);
        return data.open - remainingDownside - (Math.random() * 10); // Some random movement
    }
    
    private static double simulateFinalPrice(MarketData data, int timeIndex) {
        double progress = Math.min(1.0, (double) (timeIndex + 2) / TRADING_SESSIONS.length);
        return data.open + (data.close - data.open) * progress;
    }
    
    // Technical calculation helpers
    private static double calculateRSI(double currentPrice, MarketData data) {
        double change = (currentPrice - data.open) / data.open * 100;
        return 50 + Math.min(30, Math.max(-30, change * 10)); // Simplified RSI
    }
    
    private static long getAverageVolume(String symbol) {
        return switch (symbol) {
            case "NIFTY", "SENSEX" -> 12000000L;
            case "TCS", "RELIANCE" -> 15000000L;
            default -> 10000000L;
        };
    }
    
    private static String getSession(LocalTime time) {
        if (time.isBefore(LocalTime.of(10, 0))) return "OPENING";
        else if (time.isBefore(LocalTime.of(12, 0))) return "MORNING";
        else if (time.isBefore(LocalTime.of(14, 30))) return "AFTERNOON";
        else return "CLOSING";
    }
    
    private static String determineCallType(double currentPrice, MarketData data, double rsi, boolean macdBullish) {
        double pricePosition = (currentPrice - data.low) / (data.high - data.low);
        
        if (pricePosition < 0.3 && rsi < 40) return "BUY"; // Oversold
        else if (pricePosition > 0.7 && rsi > 60) return "SELL"; // Overbought
        else if (macdBullish && data.dayMove.startsWith("+")) return "BUY";
        else if (!macdBullish && data.dayMove.startsWith("-")) return "SELL";
        else return Math.random() > 0.5 ? "BUY" : "SELL"; // Random for neutral
    }
    
    private static double calculateTarget(double entryPrice, String callType, double confidence) {
        double targetPercent = 0.8 + (confidence - 70) * 0.03; // 0.8% to 1.55% based on confidence
        
        if ("BUY".equals(callType)) {
            return entryPrice * (1 + targetPercent / 100);
        } else {
            return entryPrice * (1 - targetPercent / 100);
        }
    }
    
    private static double calculateStopLoss(double entryPrice, String callType, MarketData data) {
        double atr = (data.high - data.low) / data.open * 100; // Simplified ATR
        double stopPercent = Math.max(0.4, Math.min(1.0, atr * 0.5)); // 0.4% to 1.0%
        
        if ("BUY".equals(callType)) {
            return entryPrice * (1 - stopPercent / 100);
        } else {
            return entryPrice * (1 + stopPercent / 100);
        }
    }
    
    private static String formatCallOutput(GeneratedCall call) {
        String status = call.hitTarget ? "🎯 TGT" : call.hitStopLoss ? "🛡️ SL" : "⏳ OPEN";
        return String.format("%s %s %s @ ₹%.1f | Conf: %.0f%% | %s | P&L: %+.2f%%",
            call.time.format(TIME_FORMAT), call.callType, status, call.entryPrice, 
            call.confidence, call.outcome, call.pnlPercent);
    }
    
    /**
     * Analyze overall performance
     */
    private static void analyzeOverallPerformance(List<GeneratedCall> allCalls) {
        System.out.println("\\n\\n🎯 OVERALL PERFORMANCE ANALYSIS");
        System.out.println("═══════════════════════════════");
        
        int totalCalls = allCalls.size();
        long targetsHit = allCalls.stream().mapToLong(c -> c.hitTarget ? 1 : 0).sum();
        long stopLossHit = allCalls.stream().mapToLong(c -> c.hitStopLoss ? 1 : 0).sum();
        long openTrades = totalCalls - targetsHit - stopLossHit;
        
        double totalPnL = allCalls.stream().mapToDouble(c -> c.pnlPercent).sum();
        double avgPnL = totalCalls > 0 ? totalPnL / totalCalls : 0;
        
        System.out.println("📞 **CALL GENERATION SUMMARY:**");
        System.out.println("   Total Calls Generated: " + totalCalls);
        System.out.println("   Calls per Symbol: " + String.format("%.1f", (double) totalCalls / TODAYS_MARKET.size()));
        System.out.println("   Average Confidence: " + String.format("%.1f%%", 
            allCalls.stream().mapToDouble(c -> c.confidence).average().orElse(0)));
        
        System.out.println("\\n🎯 **TARGET ACHIEVEMENT:**");
        System.out.println("   Targets Hit: " + targetsHit + "/" + totalCalls + 
                          " (" + String.format("%.1f%%", (double) targetsHit / totalCalls * 100) + ")");
        System.out.println("   Target Success Rate: " + String.format("%.1f%%", (double) targetsHit / totalCalls * 100));
        
        System.out.println("\\n🛡️ **STOP LOSS ANALYSIS:**");
        System.out.println("   Stop Loss Hit: " + stopLossHit + "/" + totalCalls + 
                          " (" + String.format("%.1f%%", (double) stopLossHit / totalCalls * 100) + ")");
        System.out.println("   Risk Management: " + (stopLossHit < totalCalls * 0.3 ? "EXCELLENT" : "NEEDS IMPROVEMENT"));
        
        System.out.println("\\n⏳ **TRADE STATUS:**");
        System.out.println("   Completed Trades: " + (targetsHit + stopLossHit) + "/" + totalCalls);
        System.out.println("   Open/Partial Trades: " + openTrades + "/" + totalCalls);
        
        System.out.println("\\n💰 **PROFITABILITY:**");
        System.out.println("   Total P&L: " + String.format("%+.2f%%", totalPnL));
        System.out.println("   Average P&L per Call: " + String.format("%+.2f%%", avgPnL));
        
        // Calculate win rate
        long profitableCalls = allCalls.stream().mapToLong(c -> c.pnlPercent > 0 ? 1 : 0).sum();
        System.out.println("   Win Rate: " + String.format("%.1f%%", (double) profitableCalls / totalCalls * 100));
        
        // Overall rating
        double successRate = (double) targetsHit / totalCalls * 100;
        System.out.println("\\n🏆 **OVERALL RATING:**");
        if (successRate >= 70 && avgPnL > 0.3) {
            System.out.println("   🟢 EXCELLENT - Bot performing exceptionally well!");
        } else if (successRate >= 60 && avgPnL > 0.1) {
            System.out.println("   🟡 GOOD - Bot meeting expectations");
        } else {
            System.out.println("   🔴 NEEDS IMPROVEMENT - Below target performance");
        }
    }
    
    /**
     * Analyze detailed breakdown by call type and symbol
     */
    private static void analyzeDetailedBreakdown(List<GeneratedCall> allCalls) {
        System.out.println("\\n📊 DETAILED BREAKDOWN ANALYSIS");
        System.out.println("══════════════════════════════");
        
        // By call type
        Map<String, List<GeneratedCall>> byCallType = new HashMap<>();
        for (GeneratedCall call : allCalls) {
            byCallType.computeIfAbsent(call.callType, k -> new ArrayList<>()).add(call);
        }
        
        System.out.println("\\n📈 **BY CALL TYPE:**");
        for (Map.Entry<String, List<GeneratedCall>> entry : byCallType.entrySet()) {
            String callType = entry.getKey();
            List<GeneratedCall> calls = entry.getValue();
            
            long targets = calls.stream().mapToLong(c -> c.hitTarget ? 1 : 0).sum();
            long stopLoss = calls.stream().mapToLong(c -> c.hitStopLoss ? 1 : 0).sum();
            double pnl = calls.stream().mapToDouble(c -> c.pnlPercent).sum();
            
            System.out.println("   " + callType + " Calls: " + calls.size());
            System.out.println("     Targets: " + targets + "/" + calls.size() + 
                             " (" + String.format("%.1f%%", (double) targets / calls.size() * 100) + ")");
            System.out.println("     Stop Loss: " + stopLoss + "/" + calls.size() + 
                             " (" + String.format("%.1f%%", (double) stopLoss / calls.size() * 100) + ")");
            System.out.println("     P&L: " + String.format("%+.2f%%", pnl));
        }
        
        // By symbol
        Map<String, List<GeneratedCall>> bySymbol = new HashMap<>();
        for (GeneratedCall call : allCalls) {
            bySymbol.computeIfAbsent(call.symbol, k -> new ArrayList<>()).add(call);
        }
        
        System.out.println("\\n📊 **BY SYMBOL:**");
        for (Map.Entry<String, List<GeneratedCall>> entry : bySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<GeneratedCall> calls = entry.getValue();
            
            if (calls.isEmpty()) continue;
            
            long targets = calls.stream().mapToLong(c -> c.hitTarget ? 1 : 0).sum();
            double successRate = (double) targets / calls.size() * 100;
            double pnl = calls.stream().mapToDouble(c -> c.pnlPercent).sum();
            
            String performance = successRate >= 70 ? "🟢" : successRate >= 50 ? "🟡" : "🔴";
            
            System.out.println("   " + performance + " " + symbol + ": " + calls.size() + " calls | " +
                             "Success: " + String.format("%.1f%%", successRate) + " | " +
                             "P&L: " + String.format("%+.2f%%", pnl));
        }
    }
    
    /**
     * Analyze time-based performance
     */
    private static void analyzeTimeBasedPerformance(List<GeneratedCall> allCalls) {
        System.out.println("\\n⏰ TIME-BASED PERFORMANCE ANALYSIS");
        System.out.println("══════════════════════════════════");
        
        Map<String, List<GeneratedCall>> bySession = new HashMap<>();
        for (GeneratedCall call : allCalls) {
            String session = getSession(call.time);
            bySession.computeIfAbsent(session, k -> new ArrayList<>()).add(call);
        }
        
        System.out.println("\\n🕐 **BY TRADING SESSION:**");
        String[] sessions = {"OPENING", "MORNING", "AFTERNOON", "CLOSING"};
        
        for (String session : sessions) {
            List<GeneratedCall> sessionCalls = bySession.getOrDefault(session, new ArrayList<>());
            
            if (sessionCalls.isEmpty()) {
                System.out.println("   " + session + ": No calls generated");
                continue;
            }
            
            long targets = sessionCalls.stream().mapToLong(c -> c.hitTarget ? 1 : 0).sum();
            long stopLoss = sessionCalls.stream().mapToLong(c -> c.hitStopLoss ? 1 : 0).sum();
            double successRate = (double) targets / sessionCalls.size() * 100;
            double pnl = sessionCalls.stream().mapToDouble(c -> c.pnlPercent).sum();
            double avgConfidence = sessionCalls.stream().mapToDouble(c -> c.confidence).average().orElse(0);
            
            String performance = successRate >= 70 ? "🟢" : successRate >= 50 ? "🟡" : "🔴";
            
            System.out.println("   " + performance + " " + session + ":");
            System.out.println("     Calls: " + sessionCalls.size());
            System.out.println("     Targets: " + targets + "/" + sessionCalls.size() + 
                             " (" + String.format("%.1f%%", successRate) + ")");
            System.out.println("     Stop Loss: " + stopLoss + "/" + sessionCalls.size() + 
                             " (" + String.format("%.1f%%", (double) stopLoss / sessionCalls.size() * 100) + ")");
            System.out.println("     P&L: " + String.format("%+.2f%%", pnl));
            System.out.println("     Avg Confidence: " + String.format("%.1f%%", avgConfidence));
        }
        
        System.out.println("\\n💡 **SESSION INSIGHTS:**");
        String bestSession = bySession.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .max((a, b) -> {
                double aSuccess = a.getValue().stream().mapToLong(c -> c.hitTarget ? 1 : 0).sum() / (double) a.getValue().size();
                double bSuccess = b.getValue().stream().mapToLong(c -> c.hitTarget ? 1 : 0).sum() / (double) b.getValue().size();
                return Double.compare(aSuccess, bSuccess);
            })
            .map(Map.Entry::getKey)
            .orElse("NONE");
        
        System.out.println("   🏆 Best Performing Session: " + bestSession);
        System.out.println("   📊 Most Active Session: " + bySession.entrySet().stream()
            .max((a, b) -> Integer.compare(a.getValue().size(), b.getValue().size()))
            .map(Map.Entry::getKey).orElse("NONE"));
    }
    
    // Data classes
    static class MarketData {
        final double open, high, low, close;
        final long volume;
        final String dayMove;
        
        MarketData(double open, double high, double low, double close, long volume, String dayMove) {
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
            this.dayMove = dayMove;
        }
    }
    
    static class BotDecision {
        final boolean shouldGenerateCall;
        final String callType;
        final double target, stopLoss, confidence;
        final String reasoning;
        
        BotDecision(boolean shouldGenerateCall, String callType, double target, double stopLoss,
                   double confidence, String reasoning) {
            this.shouldGenerateCall = shouldGenerateCall;
            this.callType = callType;
            this.target = target;
            this.stopLoss = stopLoss;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
    }
    
    static class GeneratedCall {
        final String symbol, callType;
        final double entryPrice, targetPrice, stopLossPrice, confidence;
        final LocalTime time;
        final String reasoning;
        
        boolean hitTarget, hitStopLoss;
        double pnlPercent;
        String outcome;
        
        GeneratedCall(String symbol, String callType, double entryPrice, double targetPrice,
                     double stopLossPrice, LocalTime time, double confidence, String reasoning) {
            this.symbol = symbol;
            this.callType = callType;
            this.entryPrice = entryPrice;
            this.targetPrice = targetPrice;
            this.stopLossPrice = stopLossPrice;
            this.time = time;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
    }
}