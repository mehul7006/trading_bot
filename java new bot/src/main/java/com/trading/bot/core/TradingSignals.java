/**
 * TRADING SIGNALS - Proper Entry/Exit Logic
 * Step 2: Replace random signals with real technical analysis
 */
public class TradingSignals {
    
    // Entry Signal Analysis
    public static EntrySignal analyzeEntry(MarketData data) {
        double confidence = 0.0;
        String direction = "NONE";
        String reason = "";
        
        // 1. Trend Confirmation (30% weight)
        boolean trendBullish = data.getTrend().equals("BULLISH");
        boolean trendBearish = data.getTrend().equals("BEARISH");
        
        if (trendBullish) {
            confidence += 0.30;
            direction = "BUY";
            reason += "Bullish trend confirmed; ";
        } else if (trendBearish) {
            confidence += 0.30;
            direction = "SELL";
            reason += "Bearish trend confirmed; ";
        }
        
        // 2. RSI Momentum (25% weight)
        if (direction.equals("BUY") && data.rsi > 40 && data.rsi < 70) {
            confidence += 0.25;
            reason += "RSI bullish momentum; ";
        } else if (direction.equals("SELL") && data.rsi < 60 && data.rsi > 30) {
            confidence += 0.25;
            reason += "RSI bearish momentum; ";
        }
        
        // 3. Volume Confirmation (20% weight)
        if (data.volumeConfirmed) {
            confidence += 0.20;
            reason += "Volume confirmed; ";
        }
        
        // 4. Breakout Confirmation (25% weight)
        if (direction.equals("BUY") && data.isBreakoutAboveResistance()) {
            confidence += 0.25;
            reason += "Resistance breakout; ";
        } else if (direction.equals("SELL") && data.isBreakdownBelowSupport()) {
            confidence += 0.25;
            reason += "Support breakdown; ";
        }
        
        // Minimum confidence threshold
        boolean isValid = confidence >= 0.75; // 75% minimum confidence
        
        return new EntrySignal(direction, confidence, isValid, reason.trim(), data.price);
    }
    
    // Exit Signal Analysis
    public static ExitSignal analyzeExit(TradePosition position, MarketData currentData) {
        double entryPrice = position.entryPrice;
        double currentPrice = currentData.price;
        String direction = position.direction;
        
        // Calculate targets based on symbol
        double target1, target2, stopLoss;
        
        if (currentData.symbol.contains("NIFTY")) {
            // NIFTY targets: 30-50 points, then 60-80 points
            target1 = direction.equals("BUY") ? entryPrice + 40 : entryPrice - 40;
            target2 = direction.equals("BUY") ? entryPrice + 70 : entryPrice - 70;
            stopLoss = direction.equals("BUY") ? entryPrice - 25 : entryPrice + 25;
        } else {
            // SENSEX targets: 100-150 points, then 200-250 points
            target1 = direction.equals("BUY") ? entryPrice + 125 : entryPrice - 125;
            target2 = direction.equals("BUY") ? entryPrice + 225 : entryPrice - 225;
            stopLoss = direction.equals("BUY") ? entryPrice - 100 : entryPrice + 100;
        }
        
        // ATR-based stop loss (more conservative)
        double atrStopLoss = direction.equals("BUY") ? 
            entryPrice - (currentData.atr * 2.0) : 
            entryPrice + (currentData.atr * 2.0);
        
        // Use more conservative stop loss
        if (direction.equals("BUY")) {
            stopLoss = Math.max(stopLoss, atrStopLoss);
        } else {
            stopLoss = Math.min(stopLoss, atrStopLoss);
        }
        
        // Check exit conditions
        boolean hitTarget1 = direction.equals("BUY") ? currentPrice >= target1 : currentPrice <= target1;
        boolean hitTarget2 = direction.equals("BUY") ? currentPrice >= target2 : currentPrice <= target2;
        boolean hitStopLoss = direction.equals("BUY") ? currentPrice <= stopLoss : currentPrice >= stopLoss;
        
        // Technical exit conditions
        boolean technicalExit = false;
        String exitReason = "";
        
        if (direction.equals("BUY") && currentData.rsi > 80) {
            technicalExit = true;
            exitReason = "Overbought RSI";
        } else if (direction.equals("SELL") && currentData.rsi < 20) {
            technicalExit = true;
            exitReason = "Oversold RSI";
        }
        
        return new ExitSignal(target1, target2, stopLoss, hitTarget1, hitTarget2, 
                             hitStopLoss, technicalExit, exitReason);
    }
    
    // Data Classes
    public static class EntrySignal {
        public final String direction;
        public final double confidence;
        public final boolean isValid;
        public final String reason;
        public final double entryPrice;
        
        public EntrySignal(String direction, double confidence, boolean isValid, 
                          String reason, double entryPrice) {
            this.direction = direction;
            this.confidence = confidence;
            this.isValid = isValid;
            this.reason = reason;
            this.entryPrice = entryPrice;
        }
    }
    
    public static class ExitSignal {
        public final double target1;
        public final double target2;
        public final double stopLoss;
        public final boolean hitTarget1;
        public final boolean hitTarget2;
        public final boolean hitStopLoss;
        public final boolean technicalExit;
        public final String exitReason;
        
        public ExitSignal(double target1, double target2, double stopLoss,
                         boolean hitTarget1, boolean hitTarget2, boolean hitStopLoss,
                         boolean technicalExit, String exitReason) {
            this.target1 = target1;
            this.target2 = target2;
            this.stopLoss = stopLoss;
            this.hitTarget1 = hitTarget1;
            this.hitTarget2 = hitTarget2;
            this.hitStopLoss = hitStopLoss;
            this.technicalExit = technicalExit;
            this.exitReason = exitReason;
        }
    }
}