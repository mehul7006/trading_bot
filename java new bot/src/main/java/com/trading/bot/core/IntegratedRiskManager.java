package com.trading.bot.core;

import java.util.*;

/**
 * PHASE 6: INTEGRATED RISK MANAGER
 * Manages risk parameters and position sizing for real trading
 */
public class IntegratedRiskManager {
    
    private final Map<String, RiskParameters> riskParams;
    
    public IntegratedRiskManager() {
        this.riskParams = new HashMap<>();
        initializeRiskParameters();
        System.out.println("🛡️ Integrated Risk Manager initialized");
    }
    
    private void initializeRiskParameters() {
        // Initialize risk parameters for each symbol
        riskParams.put("NIFTY", new RiskParameters(0.02, 25000, 0.05));
        riskParams.put("BANKNIFTY", new RiskParameters(0.025, 20000, 0.06));
        riskParams.put("SENSEX", new RiskParameters(0.018, 15000, 0.04));
        riskParams.put("FINNIFTY", new RiskParameters(0.03, 10000, 0.07));
        riskParams.put("MIDCPNIFTY", new RiskParameters(0.035, 8000, 0.08));
    }
    
    public void updateRiskParameters(Map<String, Phase6IntegratedTradingBot.RealTimeData> liveDataCache) {
        if (liveDataCache == null) return;
        
        for (Map.Entry<String, Phase6IntegratedTradingBot.RealTimeData> entry : liveDataCache.entrySet()) {
            String symbol = entry.getKey();
            Phase6IntegratedTradingBot.RealTimeData data = entry.getValue();
            
            RiskParameters params = riskParams.get(symbol);
            if (params != null) {
                // Update risk parameters based on current volatility
                params.updateBasedOnVolatility(data.impliedVolatility);
            }
        }
    }
    
    public double calculatePositionSize(String symbol, double accountSize, double confidence) {
        RiskParameters params = riskParams.get(symbol);
        if (params == null) return 0.0;
        
        // Kelly criterion adjusted for confidence
        double kellyFraction = (confidence / 100.0) * params.baseRiskPercent;
        return accountSize * Math.min(kellyFraction, params.maxRiskPercent);
    }
    
    public double calculateStopLoss(String symbol, double entryPrice, String callType) {
        RiskParameters params = riskParams.get(symbol);
        if (params == null) return entryPrice * 0.95; // Default 5% stop
        
        double stopDistance = entryPrice * params.stopLossPercent;
        
        if ("CALL".equals(callType)) {
            return entryPrice - stopDistance;
        } else {
            return entryPrice + stopDistance;
        }
    }
    
    private static class RiskParameters {
        private double baseRiskPercent;
        private final double maxPositionSize;
        private final double maxRiskPercent;
        private double stopLossPercent;
        
        public RiskParameters(double baseRiskPercent, double maxPositionSize, double stopLossPercent) {
            this.baseRiskPercent = baseRiskPercent;
            this.maxPositionSize = maxPositionSize;
            this.maxRiskPercent = 0.1; // Max 10% of account
            this.stopLossPercent = stopLossPercent;
        }
        
        public void updateBasedOnVolatility(double impliedVolatility) {
            // Adjust risk based on volatility
            if (impliedVolatility > 25.0) {
                this.baseRiskPercent = Math.max(this.baseRiskPercent * 0.8, 0.01);
            } else if (impliedVolatility < 15.0) {
                this.baseRiskPercent = Math.min(this.baseRiskPercent * 1.2, 0.05);
            }
        }
    }
}