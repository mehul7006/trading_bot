package com.trading.bot.test;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.OptionChainStrike;
import com.trading.bot.market.OptionStrikeMetrics;
import com.trading.bot.market.SimpleMarketData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OptionStrikeTwoDayBacktest {
    
    private static class Trade {
        String symbol;
        double strike;
        String type;
        String direction;
        LocalDateTime time;
        double entry;
        double target;
        double stopLoss;
        double confidence;
        int outcome;
    }
    
    private static class Summary {
        String label;
        int trades;
        int wins;
        int losses;
        int neutral;
        double netPoints;
        double avgPoints;
        int strikes;
        int strikesWithKey;
        int strikesWithCandles;
    }
    
    public static void main(String[] args) {
        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        for (String symbol : new String[] {"NIFTY50", "SENSEX"}) {
            runSymbol(fetcher, symbol);
        }
    }
    
    private static void runSymbol(HonestMarketDataFetcher fetcher, String symbol) {
        double spot = fetcher.fetchSpotPrice(symbol);
        if (spot <= 0) {
            System.out.println("❌ Failed to get spot price for " + symbol);
            return;
        }
        List<OptionChainStrike> chain = fetcher.getOptionChainStrikes(symbol);
        if (chain.isEmpty()) {
            System.out.println("❌ No option chain data for " + symbol);
            return;
        }
        chain.sort(Comparator.comparingDouble(s -> s.strike));
        
        List<OptionChainStrike> below = new ArrayList<>();
        List<OptionChainStrike> above = new ArrayList<>();
        for (OptionChainStrike s : chain) {
            if (s.strike <= spot) below.add(s);
            else above.add(s);
        }
        below.sort((a, b) -> Double.compare(b.strike, a.strike));
        above.sort(Comparator.comparingDouble(s -> s.strike));
        
        List<Trade> allTrades = new ArrayList<>();
        
        List<OptionChainStrike> ceItm = below.subList(0, Math.min(10, below.size()));
        List<OptionChainStrike> ceOtm = above.subList(0, Math.min(10, above.size()));
        List<OptionChainStrike> peItm = above.subList(0, Math.min(10, above.size()));
        List<OptionChainStrike> peOtm = below.subList(0, Math.min(10, below.size()));
        
        System.out.println("\n==============================================");
        System.out.println("📊 2-DAY OPTION CHAIN BACKTEST: " + symbol);
        System.out.println("Spot: " + String.format("%.2f", spot));
        
        Summary ceSummary = runGroup(fetcher, symbol, ceItm, "CE-ITM", true, allTrades);
        Summary ceOtmSummary = runGroup(fetcher, symbol, ceOtm, "CE-OTM", true, allTrades);
        Summary peSummary = runGroup(fetcher, symbol, peItm, "PE-ITM", false, allTrades);
        Summary peOtmSummary = runGroup(fetcher, symbol, peOtm, "PE-OTM", false, allTrades);
        
        printSummary(ceSummary);
        printSummary(ceOtmSummary);
        printSummary(peSummary);
        printSummary(peOtmSummary);
        
        allTrades.sort((a, b) -> Double.compare(b.confidence, a.confidence));
        System.out.println("\n✅ TOP SIGNALS (last 2 days)");
        int shown = 0;
        for (Trade t : allTrades) {
            if (shown++ >= 10) break;
            String action = t.direction.equals("UP") ? "BUY" : "SELL";
            System.out.printf("%s %s %,.0f %s | %s | Entry %.2f | Target %.2f | SL %.2f | Conf %.1f%% | %s%n",
                t.symbol, action, t.strike, t.type, t.time, t.entry, t.target, t.stopLoss, t.confidence,
                t.outcome == 1 ? "WIN" : t.outcome == -1 ? "LOSS" : "NO-HIT");
        }
    }
    
    private static Summary runGroup(HonestMarketDataFetcher fetcher, String symbol, List<OptionChainStrike> strikes, String label, boolean isCall, List<Trade> allTrades) {
        Summary sum = new Summary();
        sum.label = label;
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(2);
        
        for (OptionChainStrike strike : strikes) {
            sum.strikes++;
            String instrumentKey = isCall ? strike.callInstrumentKey : strike.putInstrumentKey;
            OptionStrikeMetrics metrics = isCall ? strike.callMetrics : strike.putMetrics;
            if (instrumentKey == null || instrumentKey.isEmpty()) continue;
            sum.strikesWithKey++;
            List<SimpleMarketData> candles = fetcher.fetchHistoricalCandlesByInstrument(instrumentKey, "5minute", fromDate.toString(), toDate.toString());
            if (candles == null || candles.size() < 30) continue;
            sum.strikesWithCandles++;
            List<Trade> trades = backtestOption(symbol, strike.strike, isCall ? "CE" : "PE", candles, metrics);
            for (Trade t : trades) {
                sum.trades++;
                if (t.outcome == 1) sum.wins++;
                else if (t.outcome == -1) sum.losses++;
                else sum.neutral++;
                if (t.outcome == 1) sum.netPoints += (t.target - t.entry) * (t.direction.equals("UP") ? 1 : -1);
                if (t.outcome == -1) sum.netPoints -= Math.abs(t.stopLoss - t.entry);
            }
            allTrades.addAll(trades);
        }
        int decided = sum.wins + sum.losses;
        sum.avgPoints = decided > 0 ? sum.netPoints / decided : 0;
        return sum;
    }
    
    private static List<Trade> backtestOption(String symbol, double strike, String type, List<SimpleMarketData> candles, OptionStrikeMetrics metrics) {
        List<Trade> trades = new ArrayList<>();
        for (int i = 20; i < candles.size() - 5; i++) {
            List<SimpleMarketData> history = candles.subList(0, i + 1);
            double ema5 = ema(history, 5);
            double ema20 = ema(history, 20);
            double rsi = rsi(history, 14);
            double atr = atr(history, 14);
            double price = history.get(history.size() - 1).price;
            
            String dir = "NEUTRAL";
            if (ema5 > ema20 && rsi > 55) dir = "UP";
            if (ema5 < ema20 && rsi < 45) dir = "DOWN";
            if (dir.equals("NEUTRAL")) continue;
            
            double deltaAbs = 0.0;
            if (metrics != null && metrics.greeks != null && metrics.greeks.get("delta") != null) {
                deltaAbs = Math.abs(metrics.greeks.get("delta"));
            }
            
            double minTarget = symbol.contains("SENSEX") ? 30.0 : 10.0;
            double targetPts = Math.max(minTarget, atr * 1.2);
            double slPts = Math.max(minTarget * 0.6, atr * 0.8);
            
            double confidence = 55;
            confidence += Math.min(20, Math.abs(ema5 - ema20) / price * 1000.0);
            confidence += dir.equals("UP") ? Math.max(0, (rsi - 50) * 0.5) : Math.max(0, (50 - rsi) * 0.5);
            if (deltaAbs > 0.4) confidence += 5;
            if (metrics != null && metrics.oiChange != null && metrics.oiChange > 0) confidence += 3;
            if (metrics != null && metrics.iv != null && metrics.iv > 0.2) confidence += 2;
            if (confidence > 90) confidence = 90;
            
            double entry = price;
            double target = dir.equals("UP") ? entry + targetPts : entry - targetPts;
            double stop = dir.equals("UP") ? entry - slPts : entry + slPts;
            
            int outcome = 0;
            for (int j = 1; j <= 4 && (i + j) < candles.size(); j++) {
                SimpleMarketData f = candles.get(i + j);
                if (dir.equals("UP")) {
                    if (f.high >= target) { outcome = 1; break; }
                    if (f.low <= stop) { outcome = -1; break; }
                } else {
                    if (f.low <= target) { outcome = 1; break; }
                    if (f.high >= stop) { outcome = -1; break; }
                }
            }
            
            Trade t = new Trade();
            t.symbol = symbol;
            t.strike = strike;
            t.type = type;
            t.direction = dir;
            t.time = history.get(history.size() - 1).timestamp;
            t.entry = entry;
            t.target = target;
            t.stopLoss = stop;
            t.confidence = confidence;
            t.outcome = outcome;
            trades.add(t);
        }
        return trades;
    }
    
    private static double ema(List<SimpleMarketData> data, int period) {
        if (data.size() < period) return data.get(data.size() - 1).price;
        double multiplier = 2.0 / (period + 1);
        double ema = data.get(0).price;
        for (int i = 1; i < data.size(); i++) {
            double price = data.get(i).price;
            ema = ((price - ema) * multiplier) + ema;
        }
        return ema;
    }
    
    private static double rsi(List<SimpleMarketData> data, int period) {
        if (data.size() < period + 1) return 50;
        double gain = 0;
        double loss = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            double change = data.get(i).price - data.get(i - 1).price;
            if (change > 0) gain += change;
            else loss -= change;
        }
        if (loss == 0) return 100;
        double rs = gain / loss;
        return 100 - (100 / (1 + rs));
    }
    
    private static double atr(List<SimpleMarketData> data, int period) {
        if (data.size() < period + 1) return 5.0;
        double trSum = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            SimpleMarketData curr = data.get(i);
            SimpleMarketData prev = data.get(i - 1);
            double hl = curr.high - curr.low;
            double hcp = Math.abs(curr.high - prev.price);
            double lcp = Math.abs(curr.low - prev.price);
            double tr = Math.max(hl, Math.max(hcp, lcp));
            trSum += tr;
        }
        return trSum / period;
    }
    
    private static void printSummary(Summary sum) {
        int decided = sum.wins + sum.losses;
        double winRate = decided > 0 ? (double) sum.wins / decided * 100 : 0;
        System.out.println("\n" + sum.label);
        System.out.printf("Strikes: %d | WithKey: %d | WithCandles: %d%n", sum.strikes, sum.strikesWithKey, sum.strikesWithCandles);
        System.out.printf("Trades: %d | Wins: %d | Losses: %d | No-Hit: %d | WinRate: %.1f%% | AvgPts: %.2f%n",
            sum.trades, sum.wins, sum.losses, sum.neutral, winRate, sum.avgPoints);
    }
}
