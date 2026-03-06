package com.trading.bot.ai;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.OptionData;
import com.trading.bot.market.OptionStrikeMetrics;
import com.trading.bot.market.SimpleMarketData;
import java.util.List;

public class OptionStrikePredictor {
    
    public static class StrikePrediction {
        public final String direction;
        public final double confidence;
        public final double targetPremiumPoints;
        public final double stopLossPoints;
        public final String reasoning;
        
        public StrikePrediction(String direction, double confidence, double targetPremiumPoints, double stopLossPoints, String reasoning) {
            this.direction = direction;
            this.confidence = confidence;
            this.targetPremiumPoints = targetPremiumPoints;
            this.stopLossPoints = stopLossPoints;
            this.reasoning = reasoning;
        }
    }
    
    private final HonestMarketDataFetcher fetcher;
    private final AIPredictor underlyingPredictor;
    
    public OptionStrikePredictor() {
        this.fetcher = new HonestMarketDataFetcher();
        this.underlyingPredictor = new AIPredictor();
        this.underlyingPredictor.initialize();
    }
    
    public StrikePrediction predict(String symbol, double strike, String type) {
        try {
            OptionStrikeMetrics sm = fetcher.getOptionStrikeMetrics(symbol, strike, type);
            OptionData optAggregate = fetcher.fetchOptionData(symbol);
            List<SimpleMarketData> oneMin = fetcher.fetchHistoricalCandles(symbol, "1minute",
                java.time.LocalDate.now().minusDays(2).toString(), java.time.LocalDate.now().toString());
            if (oneMin == null || oneMin.size() < 120) {
                return new StrikePrediction("NEUTRAL", 0, 0, 0, "Insufficient underlying data");
            }
            AIPredictor.AIPrediction base = underlyingPredictor.generatePrediction(symbol, oneMin, optAggregate);
            
            String dir = "NEUTRAL";
            double conf = 0;
            double targetPts = 0;
            double slPts = 0;
            StringBuilder reason = new StringBuilder();
            
            if (sm != null && sm.greeks != null) {
                Double delta = sm.greeks.get("delta");
                Double gamma = sm.greeks.get("gamma");
                Double theta = sm.greeks.get("theta");
                Double vega = sm.greeks.get("vega");
                
                boolean upPremium = ("CE".equalsIgnoreCase(type) && "UP".equals(base.predictedDirection))
                    || ("PE".equalsIgnoreCase(type) && "DOWN".equals(base.predictedDirection));
                boolean downPremium = ("CE".equalsIgnoreCase(type) && "DOWN".equals(base.predictedDirection))
                    || ("PE".equalsIgnoreCase(type) && "UP".equals(base.predictedDirection));
                
                dir = upPremium ? "UP" : (downPremium ? "DOWN" : "NEUTRAL");
                
                double deltaMag = delta != null ? Math.abs(delta) : 0.3;
                double underlyingMove = base.estimatedMovePoints;
                
                targetPts = deltaMag * underlyingMove;
                
                if (gamma != null && gamma > 0.02) targetPts *= 1.2;
                if (vega != null && vega > 0.5 && sm.iv != null && sm.iv > 0.2) targetPts *= 1.1;
                if (theta != null && theta < -3.0) targetPts *= 0.95;
                
                double minTarget = symbol.contains("NIFTY") ? 15.0 : (symbol.contains("SENSEX") ? 50.0 : 25.0);
                if (targetPts < minTarget) targetPts = minTarget;
                
                slPts = Math.max(10.0, targetPts * 0.5);
                
                conf = base.confidence;
                if (optAggregate != null) {
                    if (dir.equals("UP")) {
                        if (optAggregate.pcr < 0.9) conf += 3;
                    } else if (dir.equals("DOWN")) {
                        if (optAggregate.pcr > 1.1) conf += 3;
                    }
                }
                if (sm.oiChange != null && sm.oiChange > 0) conf += 2;
                if (sm.ltp != null && sm.ltp > 0 && targetPts / sm.ltp > 0.2) conf += 2;
                
                reason.append("Underlying ").append(base.predictedDirection)
                      .append(" with delta ").append(deltaMag)
                      .append(", gamma ").append(gamma != null ? gamma : 0)
                      .append(", OIΔ ").append(sm.oiChange != null ? sm.oiChange : 0)
                      .append(", PCR ").append(optAggregate != null ? optAggregate.pcr : 1.0);
            } else {
                dir = base.predictedDirection.equals("UP") ? ("CE".equalsIgnoreCase(type) ? "UP" : "DOWN")
                      : base.predictedDirection.equals("DOWN") ? ("CE".equalsIgnoreCase(type) ? "DOWN" : "UP")
                      : "NEUTRAL";
                conf = base.confidence * 0.9;
                targetPts = base.estimatedMovePoints * 0.6;
                slPts = base.suggestedStopLoss * 0.6;
                reason.append("Fallback using underlying prediction; strike metrics unavailable");
            }
            
            if (conf > 98) conf = 98;
            return new StrikePrediction(dir, conf, targetPts, slPts, reason.toString());
        } catch (Exception e) {
            return new StrikePrediction("NEUTRAL", 0, 0, 0, "Error: " + e.getMessage());
        }
    }
}
