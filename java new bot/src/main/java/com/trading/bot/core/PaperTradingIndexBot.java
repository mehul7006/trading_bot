import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.*;

/**
 * PAPER TRADING INDEX OPTIONS BOT
 * Complete system for learning and testing with virtual money
 * 70% confidence threshold with proper risk management
 */
public class PaperTradingIndexBot {
    
    private static final double CONFIDENCE_THRESHOLD = 70.0;
    private static final double VIRTUAL_CAPITAL = 100000.0; // ₹1,00,000 virtual money
    private static final double MAX_RISK_PER_TRADE = 0.05; // 5% max risk per trade
    private static final String[] INDICES = {"NIFTY", "SENSEX", "BANKNIFTY"};
    
    private double currentCapital = VIRTUAL_CAPITAL;
    private List<PaperTrade> activeTrades = new ArrayList<>();
    private List<PaperTrade> completedTrades = new ArrayList<>();
    private Map<String, Double> indexPrices = new HashMap<>();
    
    public static void main(String[] args) {
        System.out.println("🎯 PAPER TRADING INDEX OPTIONS BOT");
        System.out.println("===================================");
        System.out.println("💰 Virtual Capital: ₹" + String.format("%.0f", VIRTUAL_CAPITAL));
        System.out.println("📊 Confidence Threshold: " + CONFIDENCE_THRESHOLD + "%");
        System.out.println("🛡️ Max Risk per Trade: " + (MAX_RISK_PER_TRADE * 100) + "%");
        System.out.println("🎲 Instruments: Index Options (CE/PE)");
        System.out.println("📚 Purpose: Learning & Practice");
        System.out.println("===================================");
        
        PaperTradingIndexBot bot = new PaperTradingIndexBot();
        bot.startPaperTrading();
    }
    
    public void startPaperTrading() {
        System.out.println("\n🚀 STARTING PAPER TRADING SESSION");
        System.out.println("=================================");
        
        // Initialize market data
        initializeMarketData();
        
        // Generate and execute trades
        generateAndExecuteTrades();
        
        // Monitor existing trades
        monitorActiveTrades();
        
        // Generate trading report
        generateTradingReport();
        
        // Save session data
        saveTradingSession();
    }
    
    private void initializeMarketData() {
        System.out.println("📊 Initializing Market Data...");
        
        // Simulate current market prices
        indexPrices.put("NIFTY", 25800.0 + (Math.random() - 0.5) * 100);
        indexPrices.put("SENSEX", 84400.0 + (Math.random() - 0.5) * 200);
        indexPrices.put("BANKNIFTY", 58000.0 + (Math.random() - 0.5) * 150);
        
        System.out.println("✅ Market Data Ready:");
        for (Map.Entry<String, Double> entry : indexPrices.entrySet()) {
            System.out.println("   📈 " + entry.getKey() + ": ₹" + String.format("%.2f", entry.getValue()));
        }
    }
    
    private void generateAndExecuteTrades() {
        System.out.println("\n🎯 ANALYZING & GENERATING TRADES");
        System.out.println("================================");
        
        for (String index : INDICES) {
            try {
                IndexOptionCall call = analyzeIndexForOptions(index);
                if (call != null && call.confidence >= CONFIDENCE_THRESHOLD) {
                    PaperTrade trade = createPaperTrade(call);
                    if (trade != null) {
                        executePaperTrade(trade);
                    }
                } else {
                    System.out.println("⚠️ " + index + " - No signal (Confidence: " + 
                        (call != null ? String.format("%.1f%%", call.confidence) : "N/A") + ")");
                }
            } catch (Exception e) {
                System.err.println("❌ Error analyzing " + index + ": " + e.getMessage());
            }
        }
    }
    
    private IndexOptionCall analyzeIndexForOptions(String index) {
        double spotPrice = indexPrices.get(index);
        Random random = new Random();
        
        // Technical analysis
        double rsi = 30 + random.nextDouble() * 40; // 30-70
        double macd = (random.nextDouble() - 0.5) * 0.1; // -0.05 to +0.05
        double momentum = (random.nextDouble() - 0.5) * 2; // -1 to +1
        double volatility = 0.2 + random.nextDouble() * 0.8; // 0.2 to 1.0
        
        // Calculate confidence
        double confidence = calculateConfidence(rsi, macd, momentum, volatility);
        
        // Determine call type
        String callType = "";
        String reasoning = "";
        
        if (confidence >= 65) { // Lower threshold for internal analysis
            if (rsi < 40 && momentum < -0.3) {
                callType = "PE";
                reasoning = "Oversold with negative momentum";
            } else if (rsi > 60 && momentum > 0.3) {
                callType = "CE";
                reasoning = "Overbought with positive momentum";
            } else if (Math.abs(macd) > 0.03) {
                callType = macd > 0 ? "CE" : "PE";
                reasoning = "Strong MACD signal";
            } else {
                callType = volatility > 0.6 ? (random.nextBoolean() ? "CE" : "PE") : "";
                reasoning = volatility > 0.6 ? "High volatility opportunity" : "Low conviction";
            }
        } else {
            reasoning = "Technical indicators not aligned";
        }
        
        if (callType.isEmpty()) return null;
        
        // Calculate option details
        double strikePrice = calculateStrikePrice(index, spotPrice, callType);
        double premium = calculateOptionPremium(index, strikePrice, spotPrice, callType, volatility);
        LocalDate expiry = getNextThursdayExpiry();
        
        return new IndexOptionCall(index, callType, strikePrice, premium, confidence, 
                                  spotPrice, expiry, reasoning, rsi, macd, momentum, volatility);
    }
    
    private double calculateConfidence(double rsi, double macd, double momentum, double volatility) {
        double confidence = 45.0; // Base confidence
        
        // RSI contribution
        if (rsi < 35 || rsi > 65) confidence += 15;
        else if (rsi < 45 || rsi > 55) confidence += 8;
        
        // MACD contribution
        if (Math.abs(macd) > 0.04) confidence += 12;
        else if (Math.abs(macd) > 0.02) confidence += 6;
        
        // Momentum contribution
        if (Math.abs(momentum) > 0.5) confidence += 10;
        else if (Math.abs(momentum) > 0.3) confidence += 5;
        
        // Volatility contribution
        if (volatility > 0.7) confidence += 8;
        else if (volatility > 0.4) confidence += 4;
        
        return Math.min(confidence, 90.0);
    }
    
    private double calculateStrikePrice(String index, double spotPrice, String callType) {
        int interval = getStrikeInterval(index);
        double baseStrike = Math.round(spotPrice / interval) * interval;
        
        // For learning purposes, use ATM or slightly OTM
        if (callType.equals("PE")) {
            return baseStrike + interval; // OTM for PE
        } else {
            return baseStrike; // ATM for CE
        }
    }
    
    private int getStrikeInterval(String index) {
        switch (index) {
            case "NIFTY": return 50;
            case "SENSEX": return 100;
            case "BANKNIFTY": return 100;
            default: return 50;
        }
    }
    
    private double calculateOptionPremium(String index, double strike, double spot, String callType, double volatility) {
        // Simplified option pricing for paper trading
        double intrinsic = 0;
        if (callType.equals("CE")) {
            intrinsic = Math.max(spot - strike, 0);
        } else {
            intrinsic = Math.max(strike - spot, 0);
        }
        
        double timeValue = strike * volatility * 0.015; // Simplified time value
        double premium = intrinsic + timeValue;
        
        // Ensure minimum premium
        return Math.max(premium, strike * 0.008);
    }
    
    private LocalDate getNextThursdayExpiry() {
        LocalDate today = LocalDate.now();
        LocalDate nextThursday = today;
        
        while (nextThursday.getDayOfWeek() != DayOfWeek.THURSDAY) {
            nextThursday = nextThursday.plusDays(1);
        }
        
        if (today.getDayOfWeek() == DayOfWeek.THURSDAY && LocalTime.now().isAfter(LocalTime.of(15, 30))) {
            nextThursday = nextThursday.plusWeeks(1);
        }
        
        return nextThursday;
    }
    
    private PaperTrade createPaperTrade(IndexOptionCall call) {
        // Calculate position size based on risk management
        double riskAmount = currentCapital * MAX_RISK_PER_TRADE;
        double stopLossPrice = call.premium * 0.7; // 30% stop loss
        double riskPerLot = call.premium - stopLossPrice;
        
        if (riskPerLot <= 0) return null;
        
        int lots = (int) Math.floor(riskAmount / riskPerLot);
        lots = Math.max(1, Math.min(lots, 5)); // Min 1 lot, max 5 lots for learning
        
        double target1 = call.premium * 1.5;
        double target2 = call.premium * 2.0;
        
        return new PaperTrade(
            call.index + " " + call.expiry.format(DateTimeFormatter.ofPattern("dd-MMM")) + " " + 
            String.format("%.0f", call.strikePrice) + " " + call.callType,
            call.callType, lots, call.premium, target1, target2, stopLossPrice,
            call.confidence, call.reasoning, LocalTime.now()
        );
    }
    
    private void executePaperTrade(PaperTrade trade) {
        double tradeValue = trade.lots * trade.entryPrice;
        
        if (tradeValue > currentCapital * 0.2) { // Don't risk more than 20% in single trade
            System.out.println("⚠️ Trade size too large for " + trade.instrument + " - Skipping");
            return;
        }
        
        activeTrades.add(trade);
        currentCapital -= tradeValue; // Deduct premium paid
        
        System.out.println("📞 EXECUTED: " + trade.getSummary());
        System.out.println("   💰 Capital Used: ₹" + String.format("%.0f", tradeValue) + 
                          " | Remaining: ₹" + String.format("%.0f", currentCapital));
    }
    
    private void monitorActiveTrades() {
        if (activeTrades.isEmpty()) {
            System.out.println("\n📊 No active trades to monitor");
            return;
        }
        
        System.out.println("\n📊 MONITORING ACTIVE TRADES");
        System.out.println("===========================");
        
        List<PaperTrade> tradesToClose = new ArrayList<>();
        
        for (PaperTrade trade : activeTrades) {
            // Simulate price movement
            double currentPrice = simulatePriceMovement(trade);
            trade.currentPrice = currentPrice;
            
            String status = "🔄 HOLDING";
            double pnl = (currentPrice - trade.entryPrice) * trade.lots;
            double pnlPercent = ((currentPrice / trade.entryPrice) - 1) * 100;
            
            // Check exit conditions
            if (currentPrice >= trade.target2) {
                status = "🎯 TARGET 2 HIT";
                tradesToClose.add(trade);
                trade.exitPrice = trade.target2;
                trade.exitReason = "Target 2 achieved";
            } else if (currentPrice >= trade.target1) {
                status = "🎯 TARGET 1 HIT";
                // In paper trading, we can choose to hold or book profit
                if (Math.random() > 0.5) { // 50% chance to book partial profit
                    tradesToClose.add(trade);
                    trade.exitPrice = trade.target1;
                    trade.exitReason = "Target 1 - Partial booking";
                }
            } else if (currentPrice <= trade.stopLoss) {
                status = "🛑 STOP LOSS HIT";
                tradesToClose.add(trade);
                trade.exitPrice = trade.stopLoss;
                trade.exitReason = "Stop loss triggered";
            }
            
            System.out.println("   " + status + " | " + trade.instrument + 
                              " | Entry: ₹" + String.format("%.2f", trade.entryPrice) +
                              " | Current: ₹" + String.format("%.2f", currentPrice) +
                              " | P&L: ₹" + String.format("%.0f", pnl) + 
                              " (" + String.format("%.1f%%", pnlPercent) + ")");
        }
        
        // Close completed trades
        for (PaperTrade trade : tradesToClose) {
            closePaperTrade(trade);
        }
    }
    
    private double simulatePriceMovement(PaperTrade trade) {
        // Simulate realistic option price movement based on time and market
        Random random = new Random();
        double timeDecay = 0.95; // 5% time decay simulation
        double volatilityMove = 1.0 + (random.nextDouble() - 0.5) * 0.4; // ±20% volatility
        
        return trade.entryPrice * timeDecay * volatilityMove;
    }
    
    private void closePaperTrade(PaperTrade trade) {
        activeTrades.remove(trade);
        completedTrades.add(trade);
        
        double pnl = (trade.exitPrice - trade.entryPrice) * trade.lots;
        currentCapital += trade.exitPrice * trade.lots; // Add exit value
        
        System.out.println("✅ CLOSED: " + trade.instrument + " | " + trade.exitReason +
                          " | P&L: ₹" + String.format("%.0f", pnl));
    }
    
    private void generateTradingReport() {
        System.out.println("\n📊 PAPER TRADING SESSION REPORT");
        System.out.println("===============================");
        
        double totalPnL = currentCapital - VIRTUAL_CAPITAL;
        int totalTrades = completedTrades.size();
        int winningTrades = (int) completedTrades.stream().filter(t -> 
            (t.exitPrice - t.entryPrice) > 0).count();
        
        double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades * 100 : 0;
        double returnPercent = (totalPnL / VIRTUAL_CAPITAL) * 100;
        
        System.out.println("💰 Starting Capital: ₹" + String.format("%.0f", VIRTUAL_CAPITAL));
        System.out.println("💰 Current Capital: ₹" + String.format("%.0f", currentCapital));
        System.out.println("📈 Total P&L: ₹" + String.format("%.0f", totalPnL) + 
                          " (" + String.format("%.2f%%", returnPercent) + ")");
        System.out.println("📊 Total Trades: " + totalTrades);
        System.out.println("✅ Winning Trades: " + winningTrades);
        System.out.println("🎯 Win Rate: " + String.format("%.1f%%", winRate));
        System.out.println("🔄 Active Trades: " + activeTrades.size());
        
        // Performance assessment
        System.out.println("\n🏆 PERFORMANCE ASSESSMENT:");
        System.out.println("=========================");
        if (returnPercent > 5) {
            System.out.println("🟢 EXCELLENT: Great paper trading performance!");
        } else if (returnPercent > 0) {
            System.out.println("🟡 GOOD: Positive returns achieved!");
        } else if (returnPercent > -5) {
            System.out.println("🟠 LEARNING: Small losses are part of learning!");
        } else {
            System.out.println("🔴 NEEDS IMPROVEMENT: Review your strategy!");
        }
        
        // Learning points
        System.out.println("\n📚 KEY LEARNING POINTS:");
        System.out.println("======================");
        System.out.println("1. 🎯 Target achievement rate: " + String.format("%.1f%%", winRate));
        System.out.println("2. 🛡️ Risk management: " + (totalPnL < 0 ? "Needs work" : "Effective"));
        System.out.println("3. 📊 Confidence threshold: " + CONFIDENCE_THRESHOLD + "% seems " + 
                          (winRate > 60 ? "appropriate" : "needs adjustment"));
        System.out.println("4. 💰 Capital utilization: " + 
                          String.format("%.1f%%", ((VIRTUAL_CAPITAL - currentCapital) / VIRTUAL_CAPITAL * 100)));
    }
    
    private void saveTradingSession() {
        try {
            String fileName = "paper_trading_session_" + LocalDate.now() + ".txt";
            PrintWriter writer = new PrintWriter(new FileWriter(fileName));
            
            writer.println("PAPER TRADING SESSION - " + LocalDate.now());
            writer.println("=" .repeat(50));
            writer.println("Starting Capital: ₹" + String.format("%.0f", VIRTUAL_CAPITAL));
            writer.println("Final Capital: ₹" + String.format("%.0f", currentCapital));
            writer.println("Total P&L: ₹" + String.format("%.0f", currentCapital - VIRTUAL_CAPITAL));
            writer.println("Total Trades: " + completedTrades.size());
            writer.println("Active Trades: " + activeTrades.size());
            
            writer.println("\nCompleted Trades:");
            for (PaperTrade trade : completedTrades) {
                writer.println(trade.getDetailedSummary());
            }
            
            writer.close();
            System.out.println("\n💾 Session saved: " + fileName);
            
        } catch (IOException e) {
            System.err.println("❌ Error saving session: " + e.getMessage());
        }
    }
    
    // Data classes
    private static class IndexOptionCall {
        String index, callType, reasoning;
        double strikePrice, premium, confidence, spotPrice;
        LocalDate expiry;
        double rsi, macd, momentum, volatility;
        
        IndexOptionCall(String index, String callType, double strikePrice, double premium,
                       double confidence, double spotPrice, LocalDate expiry, String reasoning,
                       double rsi, double macd, double momentum, double volatility) {
            this.index = index; this.callType = callType; this.strikePrice = strikePrice;
            this.premium = premium; this.confidence = confidence; this.spotPrice = spotPrice;
            this.expiry = expiry; this.reasoning = reasoning; this.rsi = rsi;
            this.macd = macd; this.momentum = momentum; this.volatility = volatility;
        }
    }
    
    private static class PaperTrade {
        String instrument, callType, reasoning, exitReason;
        int lots;
        double entryPrice, target1, target2, stopLoss, confidence;
        double currentPrice, exitPrice;
        LocalTime entryTime;
        
        PaperTrade(String instrument, String callType, int lots, double entryPrice,
                  double target1, double target2, double stopLoss, double confidence,
                  String reasoning, LocalTime entryTime) {
            this.instrument = instrument; this.callType = callType; this.lots = lots;
            this.entryPrice = entryPrice; this.target1 = target1; this.target2 = target2;
            this.stopLoss = stopLoss; this.confidence = confidence; this.reasoning = reasoning;
            this.entryTime = entryTime; this.currentPrice = entryPrice;
        }
        
        String getSummary() {
            return String.format("%s | %d lots @ ₹%.2f | Confidence: %.1f%%",
                instrument, lots, entryPrice, confidence);
        }
        
        String getDetailedSummary() {
            double pnl = exitPrice > 0 ? (exitPrice - entryPrice) * lots : 0;
            return String.format("%s | Entry: ₹%.2f | Exit: ₹%.2f | P&L: ₹%.0f | %s",
                instrument, entryPrice, exitPrice, pnl, exitReason);
        }
    }
}