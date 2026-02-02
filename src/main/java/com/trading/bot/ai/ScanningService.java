package com.trading.bot.ai;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import java.util.List;
import java.time.LocalDateTime;

/**
 * Scanning Service - Handles the /start command logic
 * Scans for movement BEFORE it happens using Early Warning Indicators (Bollinger Squeeze, MACD)
 */
public class ScanningService {

    private final HonestMarketDataFetcher marketDataFetcher;
    private final AIPredictor aiPredictor;
    private boolean isScanning = false;

    public ScanningService() {
        this.marketDataFetcher = new HonestMarketDataFetcher();
        this.aiPredictor = new AIPredictor();
        this.aiPredictor.initialize();
    }

    /**
     * Start the scanning loop. 
     * In a real bot, this would run in a separate thread.
     * Here, we simulate the logic.
     */
    public void startScanning(String symbol) {
        System.out.println("🚀 /start command received. Starting scanner for " + symbol);
        isScanning = true;
        int checks = 0;
        int maxChecks = 5; // Safety limit for this demo/test. Real bot would be infinite.

        while (isScanning && checks < maxChecks) {
            System.out.println("\n📡 Scan Cycle #" + (checks + 1) + " for " + symbol + "...");
            
            try {
                // 1. Fetch Real Data
                List<SimpleMarketData> data = marketDataFetcher.getRealMarketData(symbol);
                
                // 2. Analyze for "Before Movement" signals
                AIPredictor.AIPrediction prediction = aiPredictor.generatePrediction(symbol, data);
                
                // 3. Check for Movement Detection
                // We look for: Breakout Setup (Squeeze) OR Strong Trend Start
                // AND: Check if the estimated move meets the user's Minimum Point Threshold
                boolean isSignificantMove = checkMinimumPoints(symbol, prediction.estimatedMovePoints);
                
                if ((prediction.isBreakout || prediction.confidence > 75) && isSignificantMove) {
                    
                    notifyMovementDetected(symbol, prediction);
                    stopScanning();
                    break;
                } else {
                    System.out.println("... No significant movement detected yet. Scanning continues.");
                    System.out.println("   (Status: " + prediction.predictedDirection + ", Conf: " + prediction.confidence + "%, Est.Move: " + String.format("%.2f", prediction.estimatedMovePoints) + " pts)");
                    
                    if (!isSignificantMove) {
                        System.out.println("   ⚠️ Move too small (Target: " + getMinPoints(symbol) + " pts). Waiting for higher volatility.");
                    }
                }
                
                // Sleep between scans (Simulated)
                // Thread.sleep(60000); 
                checks++;
                
            } catch (Exception e) {
                System.err.println("❌ Error during scanning: " + e.getMessage());
                stopScanning();
            }
        }
        
        if (isScanning) {
            System.out.println("⚠️ Scan timed out or stopped manually.");
        }
    }

    private void notifyMovementDetected(String symbol, AIPredictor.AIPrediction prediction) {
        System.out.println("\n🚨 MOVEMENT DETECTED! 🚨");
        System.out.println("--------------------------------------------------");
        System.out.println("Bot Message:");
        System.out.println("Movement will occur within few minutes!");
        System.out.println("Symbol: " + symbol);
        System.out.println("Direction: " + prediction.predictedDirection);
        System.out.println("Reason: " + prediction.predictionReasoning);
        System.out.println("⚠️ Please try to capture!");
        System.out.println("📈 Estimated Increase: " + String.format("%.2f", prediction.estimatedMovePoints) + " points");
        System.out.println("--------------------------------------------------");
    }

    public void stopScanning() {
        isScanning = false;
        System.out.println("🛑 Scanning stopped.");
    }

    private boolean checkMinimumPoints(String symbol, double estimatedPoints) {
        double minPoints = getMinPoints(symbol);
        return estimatedPoints >= minPoints;
    }

    private double getMinPoints(String symbol) {
        // Normalize symbol (handle cases like "NSE_INDEX|Nifty 50" or just "NIFTY50")
        String upperSymbol = symbol.toUpperCase();
        
        if (upperSymbol.contains("NIFTY") && !upperSymbol.contains("BANK")) {
            return 30.0; // NIFTY50 target: 30 points
        } else if (upperSymbol.contains("SENSEX")) {
            return 50.0; // SENSEX target: 50 points
        } else if (upperSymbol.contains("BANKNIFTY") || upperSymbol.contains("BANK")) {
            return 50.0; // BANKNIFTY target: 50 points
        } else {
            return 10.0; // Default for stocks/others
        }
    }
}
