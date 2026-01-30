package com.trading.bot.core;

import java.util.*;
import java.util.concurrent.*;

/**
 * PHASE 6: REAL-TIME ALERT SYSTEM
 * Monitors market conditions and generates alerts for trading opportunities
 */
public class RealTimeAlertSystem {
    
    private Map<String, Phase6IntegratedTradingBot.RealTimeData> liveDataCache;
    private HighConfidenceCallGenerator callGenerator;
    private final ScheduledExecutorService alertScheduler;
    private volatile boolean running = false;
    private final List<Phase6IntegratedTradingBot.Alert> recentAlerts;
    private final Map<String, Double> previousPrices;
    
    public RealTimeAlertSystem() {
        this.alertScheduler = Executors.newScheduledThreadPool(2);
        this.recentAlerts = new ArrayList<>();
        this.previousPrices = new HashMap<>();
        System.out.println("🚨 Real-Time Alert System initialized");
    }
    
    public void initialize(Map<String, Phase6IntegratedTradingBot.RealTimeData> liveDataCache, 
                          HighConfidenceCallGenerator callGenerator) {
        this.liveDataCache = liveDataCache;
        this.callGenerator = callGenerator;
        System.out.println("✅ Alert System: Connected to data and call generator");
    }
    
    public void startAlerts() {
        if (running) return;
        
        running = true;
        
        // Check for alerts every 10 seconds
        alertScheduler.scheduleAtFixedRate(this::checkForAlertsInternal, 0, 10, TimeUnit.SECONDS);
        
        // Clean old alerts every 5 minutes
        alertScheduler.scheduleAtFixedRate(this::cleanOldAlerts, 300, 300, TimeUnit.SECONDS);
        
        System.out.println("✅ Real-time alert monitoring started");
    }
    
    private void checkForAlertsInternal() {
        if (!running || liveDataCache == null) return;
        
        try {
            List<Phase6IntegratedTradingBot.Alert> newAlerts = checkForAlerts();
            recentAlerts.addAll(newAlerts);
            
        } catch (Exception e) {
            System.err.println("⚠️ Alert check error: " + e.getMessage());
        }
    }
    
    public List<Phase6IntegratedTradingBot.Alert> checkForAlerts() {
        List<Phase6IntegratedTradingBot.Alert> alerts = new ArrayList<>();
        
        if (liveDataCache == null) return alerts;
        
        for (Map.Entry<String, Phase6IntegratedTradingBot.RealTimeData> entry : liveDataCache.entrySet()) {
            String symbol = entry.getKey();
            Phase6IntegratedTradingBot.RealTimeData data = entry.getValue();
            
            // Check various alert conditions
            alerts.addAll(checkPriceAlerts(symbol, data));
            alerts.addAll(checkVolumeAlerts(symbol, data));
            alerts.addAll(checkVolatilityAlerts(symbol, data));
            alerts.addAll(checkBreakoutAlerts(symbol, data));
        }
        
        return alerts;
    }
    
    private List<Phase6IntegratedTradingBot.Alert> checkPriceAlerts(String symbol, Phase6IntegratedTradingBot.RealTimeData data) {
        List<Phase6IntegratedTradingBot.Alert> alerts = new ArrayList<>();
        
        Double prevPrice = previousPrices.get(symbol);
        if (prevPrice == null) {
            previousPrices.put(symbol, data.price);
            return alerts;
        }
        
        double changePercent = ((data.price - prevPrice) / prevPrice) * 100;
        
        // Price movement alerts
        if (Math.abs(changePercent) >= 1.0) {
            String direction = changePercent > 0 ? "UP" : "DOWN";
            String message = String.format("%s PRICE ALERT: %.2f%% move to ₹%.2f", 
                                         symbol, changePercent, data.price);
            alerts.add(new Phase6IntegratedTradingBot.Alert(message, "PRICE_MOVEMENT"));
        }
        
        // Support/Resistance alerts
        double support = getSupportLevel(symbol);
        double resistance = getResistanceLevel(symbol);
        
        if (data.price <= support * 1.002 && prevPrice > support * 1.002) {
            alerts.add(new Phase6IntegratedTradingBot.Alert(
                symbol + " SUPPORT TEST: ₹" + data.price + " near support ₹" + support, 
                "SUPPORT_TEST"));
        }
        
        if (data.price >= resistance * 0.998 && prevPrice < resistance * 0.998) {
            alerts.add(new Phase6IntegratedTradingBot.Alert(
                symbol + " RESISTANCE TEST: ₹" + data.price + " near resistance ₹" + resistance, 
                "RESISTANCE_TEST"));
        }
        
        previousPrices.put(symbol, data.price);
        return alerts;
    }
    
    private List<Phase6IntegratedTradingBot.Alert> checkVolumeAlerts(String symbol, Phase6IntegratedTradingBot.RealTimeData data) {
        List<Phase6IntegratedTradingBot.Alert> alerts = new ArrayList<>();
        
        double expectedVolume = getExpectedVolume(symbol);
        double volumeRatio = data.volume / expectedVolume;
        
        if (volumeRatio >= 2.0) {
            alerts.add(new Phase6IntegratedTradingBot.Alert(
                symbol + " HIGH VOLUME: " + String.format("%.0f", volumeRatio) + "x normal volume", 
                "HIGH_VOLUME"));
        }
        
        return alerts;
    }
    
    private List<Phase6IntegratedTradingBot.Alert> checkVolatilityAlerts(String symbol, Phase6IntegratedTradingBot.RealTimeData data) {
        List<Phase6IntegratedTradingBot.Alert> alerts = new ArrayList<>();
        
        double expectedIV = getExpectedIV(symbol);
        double ivRatio = data.impliedVolatility / expectedIV;
        
        if (ivRatio >= 1.3) {
            alerts.add(new Phase6IntegratedTradingBot.Alert(
                symbol + " HIGH IV: " + String.format("%.1f%%", data.impliedVolatility) + " (" + 
                String.format("%.1fx", ivRatio) + " normal)", 
                "HIGH_IV"));
        }
        
        if (ivRatio <= 0.7) {
            alerts.add(new Phase6IntegratedTradingBot.Alert(
                symbol + " LOW IV: " + String.format("%.1f%%", data.impliedVolatility) + " (good for buying)", 
                "LOW_IV"));
        }
        
        return alerts;
    }
    
    private List<Phase6IntegratedTradingBot.Alert> checkBreakoutAlerts(String symbol, Phase6IntegratedTradingBot.RealTimeData data) {
        List<Phase6IntegratedTradingBot.Alert> alerts = new ArrayList<>();
        
        double support = getSupportLevel(symbol);
        double resistance = getResistanceLevel(symbol);
        
        if (data.price > resistance) {
            alerts.add(new Phase6IntegratedTradingBot.Alert(
                symbol + " BREAKOUT: Above ₹" + resistance + " resistance", 
                "BREAKOUT_UP"));
        }
        
        if (data.price < support) {
            alerts.add(new Phase6IntegratedTradingBot.Alert(
                symbol + " BREAKDOWN: Below ₹" + support + " support", 
                "BREAKDOWN"));
        }
        
        return alerts;
    }
    
    private void cleanOldAlerts() {
        // Keep only last 20 alerts
        while (recentAlerts.size() > 20) {
            recentAlerts.remove(0);
        }
    }
    
    public List<Phase6IntegratedTradingBot.Alert> getRecentAlerts() {
        return new ArrayList<>(recentAlerts);
    }
    
    private double getExpectedVolume(String symbol) {
        return switch (symbol) {
            case "NIFTY" -> 2500000.0;
            case "BANKNIFTY" -> 1800000.0;
            case "SENSEX" -> 1200000.0;
            case "FINNIFTY" -> 800000.0;
            default -> 500000.0;
        };
    }
    
    private double getExpectedIV(String symbol) {
        return switch (symbol) {
            case "NIFTY" -> 18.5;
            case "BANKNIFTY" -> 22.3;
            case "SENSEX" -> 16.8;
            case "FINNIFTY" -> 20.1;
            default -> 19.0;
        };
    }
    
    private double getSupportLevel(String symbol) {
        return switch (symbol) {
            case "NIFTY" -> 25700.0;
            case "BANKNIFTY" -> 58000.0;
            case "SENSEX" -> 84000.0;
            case "FINNIFTY" -> 27000.0;
            default -> 20000.0;
        };
    }
    
    private double getResistanceLevel(String symbol) {
        return switch (symbol) {
            case "NIFTY" -> 26100.0;
            case "BANKNIFTY" -> 59000.0;
            case "SENSEX" -> 85000.0;
            case "FINNIFTY" -> 27500.0;
            default -> 21000.0;
        };
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public void stop() {
        running = false;
        if (alertScheduler != null && !alertScheduler.isShutdown()) {
            alertScheduler.shutdown();
        }
        System.out.println("🚨 Alert System stopped");
    }
}