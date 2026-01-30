package com.trading.bot.test;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Strategy Optimizer - "Trains" the bot by finding the best parameter set 
 * for NIFTY50 and SENSEX using historical data to achieve >60% Win Rate.
 */
public class StrategyOptimizer {

    public static class OptimizationResult implements Comparable<OptimizationResult> {
        String params;
        double winRate;
        int trades;
        double points;
        
        public OptimizationResult(String params, double winRate, int trades, double points) {
            this.params = params;
            this.winRate = winRate;
            this.trades = trades;
            this.points = points;
        }

        @Override
        public int compareTo(OptimizationResult o) {
            // Sort by Win Rate Descending
            return Double.compare(o.winRate, this.winRate);
        }
        
        @Override
        public String toString() {
            return String.format("WinRate: %.2f%% | Trades: %d | Points: %.2f | Params: %s", winRate, trades, points, params);
        }
    }

    public static void main(String[] args) {
        System.out.println("🧠 STARTING AI STRATEGY OPTIMIZATION (Training Mode)...");
        
        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        
        // Train NIFTY50
        optimizeSymbol("NIFTY50", fetcher);
        
        // Train SENSEX
        optimizeSymbol("SENSEX", fetcher);
    }

    private static void optimizeSymbol(String symbol, HonestMarketDataFetcher fetcher) {
        System.out.println("\n🔍 Optimizing Strategy for " + symbol + "...");
        
        // Fetch 90 Days Data Once
        String toDate = LocalDate.now().plusDays(1).toString();
        String fromDate = LocalDate.now().minusDays(90).toString();
        List<SimpleMarketData> allData = fetcher.fetchHistoricalCandles(symbol, "30minute", fromDate, toDate);
        
        if (allData.isEmpty()) {
            System.out.println("❌ No data found for " + symbol);
            return;
        }

        List<OptimizationResult> results = new ArrayList<>();

        // Hyperparameters to Search
        int[] rsiThresholds = {50, 55, 60, 65};
        int[] adxThresholds = {20, 25, 30};
        double[] targetMultipliers = {1.0, 1.2, 1.5, 2.0};
        double[] slMultipliers = {1.0, 1.5, 2.0};
        
        // Brute Force Search
        for (int rsi : rsiThresholds) {
            for (int adx : adxThresholds) {
                for (double targetMult : targetMultipliers) {
                    for (double slMult : slMultipliers) {
                        
                        // Run Simulation with these params
                        SimulationResult sim = runSimulation(symbol, allData, rsi, adx, targetMult, slMult);
                        
                        // Filter: Must have at least 10 trades to be statistically relevant
                        if (sim.trades >= 10) {
                            String paramStr = String.format("RSI>%d, ADX>%d, Target=%.1f*ATR, SL=%.1f*ATR", rsi, adx, targetMult, slMult);
                            results.add(new OptimizationResult(paramStr, sim.winRate, sim.trades, sim.totalPoints));
                        }
                    }
                }
            }
        }
        
        // Sort and Pick Top 3
        Collections.sort(results);
        
        System.out.println("🏆 TOP 3 CONFIGURATIONS for " + symbol + ":");
        for (int i = 0; i < Math.min(3, results.size()); i++) {
            System.out.println("#" + (i+1) + ": " + results.get(i));
        }
        
        if (!results.isEmpty() && results.get(0).winRate < 60.0) {
            System.out.println("⚠️ WARNING: Max Win Rate is below 60%. Try looser constraints or different indicators.");
        }
    }
    
    private static class SimulationResult {
        double winRate;
        int trades;
        double totalPoints;
        
        public SimulationResult(double winRate, int trades, double totalPoints) {
            this.winRate = winRate;
            this.trades = trades;
            this.totalPoints = totalPoints;
        }
    }

    private static SimulationResult runSimulation(String symbol, List<SimpleMarketData> allData, int rsiThresh, int adxThresh, double targetMult, double slMult) {
        int wins = 0;
        int losses = 0;
        double points = 0;
        int cooldown = 0;
        
        for (int i = 200; i < allData.size(); i++) {
            if (cooldown > 0) {
                cooldown--;
                continue;
            }
            
            List<SimpleMarketData> subset = allData.subList(0, i + 1);
            SimpleMarketData current = allData.get(i);
            
            // Calculate Indicators
            double ema10 = calculateEMA(subset, 10);
            double ema20 = calculateEMA(subset, 20);
            double ema50 = calculateEMA(subset, 50);
            double rsi = calculateRSI(subset, 14);
            double adx = calculateADX(subset, 14);
            double atr = calculateATR(subset, 14);
            
            // Strategy Logic (Mimicking AIPredictor structure but parameterized)
            boolean isBuy = false;
            boolean isSell = false;
            
            if (symbol.contains("NIFTY")) {
                // Nifty Logic: 10>20>50 + RSI + ADX
                boolean alignUp = ema10 > ema20 && ema20 > ema50;
                boolean alignDown = ema10 < ema20 && ema20 < ema50;
                
                if (alignUp && rsi > rsiThresh && adx > adxThresh && current.price > ema20) isBuy = true;
                if (alignDown && rsi < (100 - rsiThresh) && adx > adxThresh && current.price < ema20) isSell = true;
                
            } else if (symbol.contains("SENSEX")) {
                // Sensex Logic: Price>20 + RSI + ADX
                if (current.price > ema20 && rsi > rsiThresh && adx > adxThresh && ema10 > ema20) isBuy = true;
                if (current.price < ema20 && rsi < (100 - rsiThresh) && adx > adxThresh && ema10 < ema20) isSell = true;
            }
            
            if (isBuy || isSell) {
                double target = atr * targetMult;
                double sl = atr * slMult;
                
                int outcome = verifyOutcome(allData, i, isBuy ? "UP" : "DOWN", target, sl);
                
                if (outcome == 1) {
                    wins++;
                    points += target;
                } else if (outcome == -1) {
                    losses++;
                    points -= sl;
                }
                
                cooldown = 6;
            }
        }
        
        int total = wins + losses;
        double rate = total > 0 ? (double) wins / total * 100 : 0;
        return new SimulationResult(rate, total, points);
    }
    
    // --- Helper Math Functions (Copied from AIPredictor for standalone running) ---
    
    private static double calculateEMA(List<SimpleMarketData> data, int period) {
        if (data.size() < period) return data.get(data.size()-1).price;
        double multiplier = 2.0 / (period + 1);
        double ema = data.get(0).price; // Simplified initialization
        // Better initialization: SMA of first 'period'
        double sum = 0;
        for(int i=0; i<period; i++) sum += data.get(i).price;
        ema = sum / period;
        
        for (int i = period; i < data.size(); i++) {
            ema = ((data.get(i).price - ema) * multiplier) + ema;
        }
        return ema;
    }
    
    private static double calculateRSI(List<SimpleMarketData> data, int period) {
        if (data.size() < period + 1) return 50;
        double gain = 0, loss = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            double change = data.get(i).price - data.get(i - 1).price;
            if (change > 0) gain += change; else loss -= change;
        }
        if (loss == 0) return 100;
        double rs = gain / loss;
        return 100 - (100 / (1 + rs));
    }
    
    private static double calculateADX(List<SimpleMarketData> data, int period) {
        // Simplified ADX for speed in optimization (approximation is usually sufficient for trend strength)
        // Real ADX is complex, but let's try to be close to AIPredictor logic
        // For optimization speed, we can assume a simplified trend strength or copy full logic.
        // Copying full logic is safer to match AIPredictor.
        if (data.size() < period * 2) return 50.0;
        
        double[] tr = new double[data.size()];
        double[] dmPlus = new double[data.size()];
        double[] dmMinus = new double[data.size()];
        
        for (int i = 1; i < data.size(); i++) {
            SimpleMarketData curr = data.get(i);
            SimpleMarketData prev = data.get(i-1);
            double hl = curr.high - curr.low;
            double hcp = Math.abs(curr.high - prev.price);
            double lcp = Math.abs(curr.low - prev.price);
            tr[i] = Math.max(hl, Math.max(hcp, lcp));
            
            double upMove = curr.high - prev.high;
            double downMove = prev.low - curr.low;
            
            if (upMove > downMove && upMove > 0) dmPlus[i] = upMove; else dmPlus[i] = 0;
            if (downMove > upMove && downMove > 0) dmMinus[i] = downMove; else dmMinus[i] = 0;
        }
        
        double trSmooth = 0, dmPlusSmooth = 0, dmMinusSmooth = 0;
        for (int i = 1; i <= period; i++) {
            trSmooth += tr[i]; dmPlusSmooth += dmPlus[i]; dmMinusSmooth += dmMinus[i];
        }
        
        List<Double> dxList = new ArrayList<>();
        for (int i = period + 1; i < data.size(); i++) {
            trSmooth = trSmooth - (trSmooth/period) + tr[i];
            dmPlusSmooth = dmPlusSmooth - (dmPlusSmooth/period) + dmPlus[i];
            dmMinusSmooth = dmMinusSmooth - (dmMinusSmooth/period) + dmMinus[i];
            if (trSmooth == 0) continue;
            double diPlus = 100 * (dmPlusSmooth / trSmooth);
            double diMinus = 100 * (dmMinusSmooth / trSmooth);
            double sumDi = diPlus + diMinus;
            if (sumDi == 0) dxList.add(0.0); else dxList.add(100 * Math.abs(diPlus - diMinus) / sumDi);
        }
        if (dxList.isEmpty()) return 50.0;
        return dxList.stream().mapToDouble(d -> d).average().orElse(50.0);
    }

    private static double calculateATR(List<SimpleMarketData> data, int period) {
        if (data.size() < period + 1) return 10.0;
        double trSum = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            SimpleMarketData curr = data.get(i);
            SimpleMarketData prev = data.get(i-1);
            double hl = curr.high - curr.low;
            double hcp = Math.abs(curr.high - prev.price);
            double lcp = Math.abs(curr.low - prev.price);
            trSum += Math.max(hl, Math.max(hcp, lcp));
        }
        return trSum / period;
    }

    private static int verifyOutcome(List<SimpleMarketData> allData, int currentIndex, String direction, double targetPoints, double stopLossPoints) {
        SimpleMarketData entryCandle = allData.get(currentIndex);
        double entryPrice = entryCandle.price;
        double targetPrice = direction.equals("UP") ? entryPrice + targetPoints : entryPrice - targetPoints;
        double stopLossPrice = direction.equals("UP") ? entryPrice - stopLossPoints : entryPrice + stopLossPoints;
        
        for (int j = 1; j <= 12 && (currentIndex + j) < allData.size(); j++) {
            SimpleMarketData futureCandle = allData.get(currentIndex + j);
            if (direction.equals("UP")) {
                if (futureCandle.high >= targetPrice) return 1;
                if (futureCandle.low <= stopLossPrice) return -1;
            } else {
                if (futureCandle.low <= targetPrice) return 1;
                if (futureCandle.high >= stopLossPrice) return -1;
            }
        }
        return 0;
    }
}
