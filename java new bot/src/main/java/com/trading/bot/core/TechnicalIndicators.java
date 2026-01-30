import java.util.*;

/**
 * TECHNICAL INDICATORS - Real Analysis Framework
 * Step 1: Replace random signals with proper technical analysis
 */
public class TechnicalIndicators {
    
    // RSI Calculation
    public static double calculateRSI(List<Double> prices, int period) {
        if (prices.size() < period + 1) return 50.0; // Default neutral
        
        double avgGain = 0.0;
        double avgLoss = 0.0;
        
        // Calculate initial gains and losses
        for (int i = 1; i <= period; i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss += Math.abs(change);
            }
        }
        
        avgGain /= period;
        avgLoss /= period;
        
        if (avgLoss == 0) return 100.0;
        
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
    
    // EMA Calculation
    public static double calculateEMA(List<Double> prices, int period) {
        if (prices.isEmpty()) return 0.0;
        if (prices.size() == 1) return prices.get(0);
        
        double multiplier = 2.0 / (period + 1);
        double ema = prices.get(0);
        
        for (int i = 1; i < prices.size(); i++) {
            ema = (prices.get(i) * multiplier) + (ema * (1 - multiplier));
        }
        
        return ema;
    }
    
    // Volume Analysis
    public static boolean isVolumeConfirmed(double currentVolume, List<Double> volumeHistory) {
        if (volumeHistory.size() < 20) return false;
        
        double avgVolume = volumeHistory.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
            
        return currentVolume > (avgVolume * 1.5); // 1.5x average volume
    }
    
    // ATR Calculation for Stop Loss
    public static double calculateATR(List<Double> highs, List<Double> lows, List<Double> closes, int period) {
        if (highs.size() < period || lows.size() < period || closes.size() < period) {
            return 0.0;
        }
        
        List<Double> trueRanges = new ArrayList<>();
        
        for (int i = 1; i < Math.min(highs.size(), Math.min(lows.size(), closes.size())); i++) {
            double tr1 = highs.get(i) - lows.get(i);
            double tr2 = Math.abs(highs.get(i) - closes.get(i - 1));
            double tr3 = Math.abs(lows.get(i) - closes.get(i - 1));
            
            trueRanges.add(Math.max(tr1, Math.max(tr2, tr3)));
        }
        
        return trueRanges.stream()
            .skip(Math.max(0, trueRanges.size() - period))
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
    }
    
    // Support/Resistance Level Detection
    public static double findSupport(List<Double> prices, int lookback) {
        if (prices.size() < lookback) return 0.0;
        
        return prices.stream()
            .skip(Math.max(0, prices.size() - lookback))
            .mapToDouble(Double::doubleValue)
            .min()
            .orElse(0.0);
    }
    
    public static double findResistance(List<Double> prices, int lookback) {
        if (prices.size() < lookback) return Double.MAX_VALUE;
        
        return prices.stream()
            .skip(Math.max(0, prices.size() - lookback))
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(Double.MAX_VALUE);
    }
}