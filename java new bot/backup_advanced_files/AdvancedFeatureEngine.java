package com.stockbot;

import java.util.*;
import java.time.LocalDateTime;
import java.time.DayOfWeek;

/**
 * Advanced Feature Engineering for Enhanced Prediction Accuracy
 * Adds 25+ sophisticated features for better market analysis
 */
public class AdvancedFeatureEngine {
    
    /**
     * Extract 25+ advanced features for ML models
     */
    public Map<String, Double> extractAdvancedFeatures(List<IndexData> historicalData, IndexData currentData) {
        Map<String, Double> features = new HashMap<>();
        
        if (historicalData.size() < 50) {
            return getDefaultFeatures();
        }
        
        // 1. MOMENTUM FEATURES (5 features)
        features.put("momentum_5", calculateMomentum(historicalData, 5));
        features.put("momentum_10", calculateMomentum(historicalData, 10));
        features.put("momentum_20", calculateMomentum(historicalData, 20));
        features.put("roc_5", calculateRateOfChange(historicalData, 5));
        features.put("roc_10", calculateRateOfChange(historicalData, 10));
        
        // 2. VOLATILITY FEATURES (4 features)
        features.put("volatility_5", calculateVolatility(historicalData, 5));
        features.put("volatility_20", calculateVolatility(historicalData, 20));
        features.put("volatility_ratio", calculateVolatilityRatio(historicalData));
        features.put("atr", calculateATR(historicalData, 14));
        
        // 3. VOLUME FEATURES (4 features)
        features.put("volume_ratio", calculateVolumeRatio(historicalData, currentData));
        features.put("volume_sma_ratio", calculateVolumeSMADeviation(historicalData, currentData));
        features.put("price_volume_trend", calculatePriceVolumeTrend(historicalData));
        features.put("volume_oscillator", calculateVolumeOscillator(historicalData));
        
        // 4. TECHNICAL INDICATORS (6 features)
        features.put("rsi", calculateRSI(historicalData, 14));
        features.put("macd", calculateMACD(historicalData));
        features.put("macd_signal", calculateMACDSignal(historicalData));
        features.put("bollinger_position", calculateBollingerPosition(historicalData, currentData));
        features.put("stochastic", calculateStochastic(historicalData, 14));
        features.put("williams_r", calculateWilliamsR(historicalData, 14));
        
        // 5. MARKET STRUCTURE FEATURES (3 features)
        features.put("support_strength", calculateSupportStrength(historicalData, currentData));
        features.put("resistance_strength", calculateResistanceStrength(historicalData, currentData));
        features.put("trend_strength", calculateTrendStrength(historicalData));
        
        // 6. TIME-BASED FEATURES (3 features)
        features.put("time_of_day", getTimeOfDayFactor());
        features.put("day_of_week", getDayOfWeekFactor());
        features.put("expiry_proximity", getExpiryProximityFactor());
        
        return features;
    }
    
    // MOMENTUM CALCULATIONS
    private double calculateMomentum(List<IndexData> data, int period) {
        if (data.size() < period + 1) return 0.0;
        
        double currentPrice = data.get(data.size() - 1).getLastPrice();
        double pastPrice = data.get(data.size() - period - 1).getLastPrice();
        
        return (currentPrice - pastPrice) / pastPrice * 100;
    }
    
    private double calculateRateOfChange(List<IndexData> data, int period) {
        if (data.size() < period + 1) return 0.0;
        
        double currentPrice = data.get(data.size() - 1).getLastPrice();
        double pastPrice = data.get(data.size() - period - 1).getLastPrice();
        
        return ((currentPrice - pastPrice) / pastPrice) * 100;
    }
    
    // VOLATILITY CALCULATIONS
    private double calculateVolatility(List<IndexData> data, int period) {
        if (data.size() < period) return 0.02;
        
        List<Double> returns = new ArrayList<>();
        for (int i = data.size() - period; i < data.size() - 1; i++) {
            double ret = (data.get(i + 1).getLastPrice() - data.get(i).getLastPrice()) / data.get(i).getLastPrice();
            returns.add(ret);
        }
        
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
            .mapToDouble(ret -> Math.pow(ret - mean, 2))
            .average().orElse(0.0);
        
        return Math.sqrt(variance) * Math.sqrt(252); // Annualized
    }
    
    private double calculateVolatilityRatio(List<IndexData> data) {
        if (data.size() < 40) return 1.0;
        
        double shortVol = calculateVolatility(data.subList(data.size() - 10, data.size()), 10);
        double longVol = calculateVolatility(data.subList(data.size() - 30, data.size()), 30);
        
        return longVol == 0 ? 1.0 : shortVol / longVol;
    }
    
    private double calculateATR(List<IndexData> data, int period) {
        if (data.size() < period) return 0.0;
        
        double atr = 0.0;
        for (int i = data.size() - period; i < data.size(); i++) {
            IndexData current = data.get(i);
            if (i > 0) {
                IndexData previous = data.get(i - 1);
                double tr = Math.max(
                    current.getHighPrice() - current.getLowPrice(),
                    Math.max(
                        Math.abs(current.getHighPrice() - previous.getLastPrice()),
                        Math.abs(current.getLowPrice() - previous.getLastPrice())
                    )
                );
                atr += tr;
            }
        }
        
        return atr / period;
    }
    
    // VOLUME CALCULATIONS
    private double calculateVolumeRatio(List<IndexData> data, IndexData current) {
        if (data.size() < 20) return 1.0;
        
        double avgVolume = data.stream()
            .skip(Math.max(0, data.size() - 20))
            .mapToLong(IndexData::getVolume)
            .average().orElse(1.0);
        
        return avgVolume == 0 ? 1.0 : current.getVolume() / avgVolume;
    }
    
    private double calculateVolumeSMADeviation(List<IndexData> data, IndexData current) {
        if (data.size() < 20) return 0.0;
        
        double sma = data.stream()
            .skip(Math.max(0, data.size() - 20))
            .mapToLong(IndexData::getVolume)
            .average().orElse(1.0);
        
        return sma == 0 ? 0.0 : (current.getVolume() - sma) / sma;
    }
    
    private double calculatePriceVolumeTrend(List<IndexData> data) {
        if (data.size() < 10) return 0.0;
        
        double correlation = 0.0;
        // Simplified correlation between price changes and volume
        for (int i = data.size() - 9; i < data.size(); i++) {
            double priceChange = data.get(i).getLastPrice() - data.get(i - 1).getLastPrice();
            double volumeChange = data.get(i).getVolume() - data.get(i - 1).getVolume();
            correlation += Math.signum(priceChange) * Math.signum(volumeChange);
        }
        
        return correlation / 9.0;
    }
    
    private double calculateVolumeOscillator(List<IndexData> data) {
        if (data.size() < 28) return 0.0;
        
        double shortMA = data.stream()
            .skip(Math.max(0, data.size() - 14))
            .mapToLong(IndexData::getVolume)
            .average().orElse(1.0);
        
        double longMA = data.stream()
            .skip(Math.max(0, data.size() - 28))
            .mapToLong(IndexData::getVolume)
            .average().orElse(1.0);
        
        return longMA == 0 ? 0.0 : (shortMA - longMA) / longMA * 100;
    }
    
    // TECHNICAL INDICATORS
    private double calculateRSI(List<IndexData> data, int period) {
        if (data.size() < period + 1) return 50.0;
        
        double gains = 0.0, losses = 0.0;
        
        for (int i = data.size() - period; i < data.size(); i++) {
            double change = data.get(i).getLastPrice() - data.get(i - 1).getLastPrice();
            if (change > 0) gains += change;
            else losses -= change;
        }
        
        double avgGain = gains / period;
        double avgLoss = losses / period;
        
        if (avgLoss == 0) return 100.0;
        
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }
    
    private double calculateMACD(List<IndexData> data) {
        if (data.size() < 26) return 0.0;
        
        double ema12 = calculateEMA(data, 12);
        double ema26 = calculateEMA(data, 26);
        
        return ema12 - ema26;
    }
    
    private double calculateMACDSignal(List<IndexData> data) {
        // Simplified MACD signal line
        return calculateMACD(data) * 0.8; // Approximation
    }
    
    private double calculateEMA(List<IndexData> data, int period) {
        if (data.size() < period) return data.get(data.size() - 1).getLastPrice();
        
        double multiplier = 2.0 / (period + 1);
        double ema = data.get(data.size() - period).getLastPrice();
        
        for (int i = data.size() - period + 1; i < data.size(); i++) {
            ema = (data.get(i).getLastPrice() * multiplier) + (ema * (1 - multiplier));
        }
        
        return ema;
    }
    
    private double calculateBollingerPosition(List<IndexData> data, IndexData current) {
        if (data.size() < 20) return 0.5;
        
        double sma = data.stream()
            .skip(Math.max(0, data.size() - 20))
            .mapToDouble(IndexData::getLastPrice)
            .average().orElse(current.getLastPrice());
        
        double stdDev = Math.sqrt(data.stream()
            .skip(Math.max(0, data.size() - 20))
            .mapToDouble(d -> Math.pow(d.getLastPrice() - sma, 2))
            .average().orElse(0.0));
        
        double upperBand = sma + (2 * stdDev);
        double lowerBand = sma - (2 * stdDev);
        
        if (upperBand == lowerBand) return 0.5;
        
        return (current.getLastPrice() - lowerBand) / (upperBand - lowerBand);
    }
    
    private double calculateStochastic(List<IndexData> data, int period) {
        if (data.size() < period) return 50.0;
        
        double highestHigh = data.stream()
            .skip(Math.max(0, data.size() - period))
            .mapToDouble(IndexData::getHighPrice)
            .max().orElse(0.0);
        
        double lowestLow = data.stream()
            .skip(Math.max(0, data.size() - period))
            .mapToDouble(IndexData::getLowPrice)
            .min().orElse(0.0);
        
        double currentClose = data.get(data.size() - 1).getLastPrice();
        
        if (highestHigh == lowestLow) return 50.0;
        
        return ((currentClose - lowestLow) / (highestHigh - lowestLow)) * 100;
    }
    
    private double calculateWilliamsR(List<IndexData> data, int period) {
        return calculateStochastic(data, period) - 100; // Williams %R is inverted Stochastic
    }
    
    // MARKET STRUCTURE
    private double calculateSupportStrength(List<IndexData> data, IndexData current) {
        // Simplified support level strength calculation
        double currentPrice = current.getLastPrice();
        long touchCount = data.stream()
            .mapToDouble(IndexData::getLowPrice)
            .filter(low -> Math.abs(low - currentPrice) / currentPrice < 0.01)
            .count();
        
        return Math.min(1.0, touchCount / 5.0);
    }
    
    private double calculateResistanceStrength(List<IndexData> data, IndexData current) {
        // Simplified resistance level strength calculation
        double currentPrice = current.getLastPrice();
        long touchCount = data.stream()
            .mapToDouble(IndexData::getHighPrice)
            .filter(high -> Math.abs(high - currentPrice) / currentPrice < 0.01)
            .count();
        
        return Math.min(1.0, touchCount / 5.0);
    }
    
    private double calculateTrendStrength(List<IndexData> data) {
        if (data.size() < 20) return 0.5;
        
        double sma20 = data.stream()
            .skip(Math.max(0, data.size() - 20))
            .mapToDouble(IndexData::getLastPrice)
            .average().orElse(0.0);
        
        double currentPrice = data.get(data.size() - 1).getLastPrice();
        
        return Math.min(1.0, Math.abs(currentPrice - sma20) / sma20 * 10);
    }
    
    // TIME-BASED FEATURES
    private double getTimeOfDayFactor() {
        int hour = LocalDateTime.now().getHour();
        
        // Market opening (9:15-10:00): High volatility
        if (hour >= 9 && hour < 10) return 0.9;
        // Mid-day (10:00-14:00): Moderate activity
        if (hour >= 10 && hour < 14) return 0.6;
        // Closing (14:00-15:30): High volatility
        if (hour >= 14 && hour < 16) return 0.8;
        
        return 0.3; // After hours
    }
    
    private double getDayOfWeekFactor() {
        DayOfWeek day = LocalDateTime.now().getDayOfWeek();
        
        switch (day) {
            case MONDAY: return 0.8; // Higher volatility
            case TUESDAY: case WEDNESDAY: case THURSDAY: return 0.6;
            case FRIDAY: return 0.7; // Expiry day effects
            default: return 0.3; // Weekend
        }
    }
    
    private double getExpiryProximityFactor() {
        // Simplified expiry proximity (would need actual expiry dates)
        int dayOfMonth = LocalDateTime.now().getDayOfMonth();
        
        // Last Thursday of month (typical expiry)
        if (dayOfMonth >= 25) return 0.9;
        if (dayOfMonth >= 20) return 0.7;
        
        return 0.5;
    }
    
    private Map<String, Double> getDefaultFeatures() {
        Map<String, Double> defaults = new HashMap<>();
        
        // Default values when insufficient data
        defaults.put("momentum_5", 0.0);
        defaults.put("momentum_10", 0.0);
        defaults.put("momentum_20", 0.0);
        defaults.put("roc_5", 0.0);
        defaults.put("roc_10", 0.0);
        defaults.put("volatility_5", 0.02);
        defaults.put("volatility_20", 0.02);
        defaults.put("volatility_ratio", 1.0);
        defaults.put("atr", 0.0);
        defaults.put("volume_ratio", 1.0);
        defaults.put("volume_sma_ratio", 0.0);
        defaults.put("price_volume_trend", 0.0);
        defaults.put("volume_oscillator", 0.0);
        defaults.put("rsi", 50.0);
        defaults.put("macd", 0.0);
        defaults.put("macd_signal", 0.0);
        defaults.put("bollinger_position", 0.5);
        defaults.put("stochastic", 50.0);
        defaults.put("williams_r", -50.0);
        defaults.put("support_strength", 0.5);
        defaults.put("resistance_strength", 0.5);
        defaults.put("trend_strength", 0.5);
        defaults.put("time_of_day", getTimeOfDayFactor());
        defaults.put("day_of_week", getDayOfWeekFactor());
        defaults.put("expiry_proximity", getExpiryProximityFactor());
        
        return defaults;
    }
}