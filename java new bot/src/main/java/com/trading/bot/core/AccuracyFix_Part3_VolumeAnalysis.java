import java.time.*;
import java.util.*;

/**
 * ACCURACY FIX PART 3: ADD VOLUME ANALYSIS
 * 
 * Issue: Missing key volume indicator causing wrong signals
 * Solution: Comprehensive volume analysis with multiple indicators
 */
public class AccuracyFix_Part3_VolumeAnalysis {
    
    /**
     * Advanced Volume Analysis Engine
     */
    public static class VolumeAnalysisEngine {
        
        public enum VolumeSignal {
            STRONG_BULLISH("Strong Bullish", "🟢🟢", 2.0),
            BULLISH("Bullish", "🟢", 1.0),
            NEUTRAL("Neutral", "🟡", 0.0),
            BEARISH("Bearish", "🔴", -1.0),
            STRONG_BEARISH("Strong Bearish", "🔴🔴", -2.0);
            
            private final String label;
            private final String emoji;
            private final double score;
            
            VolumeSignal(String label, String emoji, double score) {
                this.label = label;
                this.emoji = emoji;
                this.score = score;
            }
            
            public static VolumeSignal fromScore(double score) {
                if (score >= 1.5) return STRONG_BULLISH;
                if (score >= 0.5) return BULLISH;
                if (score <= -1.5) return STRONG_BEARISH;
                if (score <= -0.5) return BEARISH;
                return NEUTRAL;
            }
        }
        
        /**
         * Comprehensive volume analysis
         */
        public static VolumeAnalysisResult analyzeVolume(long currentVolume, double currentPrice,
                                                        double previousClose, long[] volumeHistory,
                                                        double[] priceHistory) {
            
            Map<String, Double> indicators = new HashMap<>();
            Map<String, String> explanations = new HashMap<>();
            
            // 1. Volume Ratio Analysis (25% weight)
            double avgVolume = Arrays.stream(volumeHistory).average().orElse(1000000);
            double volumeRatio = currentVolume / avgVolume;
            double volumeRatioScore = 0;
            
            if (volumeRatio > 2.0) {
                volumeRatioScore = 2.0;
                explanations.put("Volume Ratio", "Exceptional volume (" + String.format("%.1fx", volumeRatio) + 
                               ") - major institutional activity");
            } else if (volumeRatio > 1.5) {
                volumeRatioScore = 1.5;
                explanations.put("Volume Ratio", "High volume (" + String.format("%.1fx", volumeRatio) + 
                               ") - strong institutional interest");
            } else if (volumeRatio > 1.2) {
                volumeRatioScore = 1.0;
                explanations.put("Volume Ratio", "Above average volume - good participation");
            } else if (volumeRatio < 0.7) {
                volumeRatioScore = -1.0;
                explanations.put("Volume Ratio", "Low volume (" + String.format("%.1fx", volumeRatio) + 
                               ") - weak conviction");
            } else {
                volumeRatioScore = 0;
                explanations.put("Volume Ratio", "Normal volume levels");
            }
            indicators.put("Volume Ratio", volumeRatioScore * 0.25);
            
            // 2. Volume-Price Relationship (30% weight)
            double priceChange = (currentPrice - previousClose) / previousClose * 100;
            double volumePriceScore = 0;
            
            if (priceChange > 0.5 && volumeRatio > 1.2) {
                volumePriceScore = 2.0; // Price up + High volume = Strong bullish
                explanations.put("Volume-Price", "Price up with volume support - strong bullish");
            } else if (priceChange > 0.5 && volumeRatio < 0.8) {
                volumePriceScore = -0.5; // Price up + Low volume = Weak move
                explanations.put("Volume-Price", "Price up but low volume - weak bullish");
            } else if (priceChange < -0.5 && volumeRatio > 1.2) {
                volumePriceScore = -1.5; // Price down + High volume = Strong bearish
                explanations.put("Volume-Price", "Price down with high volume - strong selling");
            } else if (priceChange < -0.5 && volumeRatio < 0.8) {
                volumePriceScore = 0.5; // Price down + Low volume = Not serious
                explanations.put("Volume-Price", "Price down but low volume - not serious selling");
            } else {
                volumePriceScore = 0;
                explanations.put("Volume-Price", "Volume-price relationship neutral");
            }
            indicators.put("Volume-Price", volumePriceScore * 0.30);
            
            // 3. Volume Trend Analysis (20% weight)
            double volumeTrendScore = calculateVolumeTrend(volumeHistory);
            if (volumeTrendScore > 0.5) {
                explanations.put("Volume Trend", "Volume increasing over time - building momentum");
            } else if (volumeTrendScore < -0.5) {
                explanations.put("Volume Trend", "Volume decreasing over time - weakening interest");
            } else {
                explanations.put("Volume Trend", "Volume trend neutral");
            }
            indicators.put("Volume Trend", volumeTrendScore * 0.20);
            
            // 4. Intraday Volume Pattern (15% weight)
            double volumePatternScore = analyzeIntradayVolumePattern();
            explanations.put("Volume Pattern", getVolumePatternExplanation(volumePatternScore));
            indicators.put("Volume Pattern", volumePatternScore * 0.15);
            
            // 5. Volume Spike Detection (10% weight)
            double volumeSpikeScore = detectVolumeSpike(currentVolume, volumeHistory);
            if (volumeSpikeScore > 0) {
                explanations.put("Volume Spike", "Volume spike detected - potential breakout");
            } else {
                explanations.put("Volume Spike", "No significant volume spike");
            }
            indicators.put("Volume Spike", volumeSpikeScore * 0.10);
            
            // Calculate total volume score
            double totalScore = indicators.values().stream().mapToDouble(Double::doubleValue).sum();
            VolumeSignal signal = VolumeSignal.fromScore(totalScore);
            
            // Calculate confidence
            double confidence = calculateVolumeConfidence(indicators, currentVolume, avgVolume);
            
            return new VolumeAnalysisResult(signal, totalScore, confidence, indicators, explanations);
        }
        
        /**
         * Calculate volume trend over recent periods
         */
        private static double calculateVolumeTrend(long[] volumeHistory) {
            if (volumeHistory.length < 3) return 0;
            
            // Simple trend calculation - compare recent vs older volumes
            int recent = volumeHistory.length / 3;
            double recentAvg = 0;
            double olderAvg = 0;
            
            for (int i = volumeHistory.length - recent; i < volumeHistory.length; i++) {
                recentAvg += volumeHistory[i];
            }
            recentAvg /= recent;
            
            for (int i = 0; i < recent; i++) {
                olderAvg += volumeHistory[i];
            }
            olderAvg /= recent;
            
            double trendRatio = (recentAvg - olderAvg) / olderAvg;
            return Math.max(-1.0, Math.min(1.0, trendRatio * 2)); // Scale to -1 to 1
        }
        
        /**
         * Analyze intraday volume pattern
         */
        private static double analyzeIntradayVolumePattern() {
            LocalTime now = LocalTime.now();
            
            // Expected volume patterns during the day
            if (now.isBefore(LocalTime.of(10, 0))) {
                return 0.8; // Opening volume usually high - positive
            } else if (now.isBefore(LocalTime.of(11, 30))) {
                return 0.5; // Morning activity - positive
            } else if (now.isBefore(LocalTime.of(13, 0))) {
                return -0.3; // Pre-lunch lull - negative
            } else if (now.isBefore(LocalTime.of(14, 30))) {
                return 0.3; // Post-lunch pickup - positive
            } else {
                return 0.6; // Closing activity - positive
            }
        }
        
        /**
         * Get volume pattern explanation
         */
        private static String getVolumePatternExplanation(double score) {
            LocalTime now = LocalTime.now();
            
            if (now.isBefore(LocalTime.of(10, 0))) {
                return "Opening session - high volume expected";
            } else if (now.isBefore(LocalTime.of(11, 30))) {
                return "Morning session - good volume participation";
            } else if (now.isBefore(LocalTime.of(13, 0))) {
                return "Pre-lunch - typically lower volume";
            } else if (now.isBefore(LocalTime.of(14, 30))) {
                return "Post-lunch - volume picking up";
            } else {
                return "Closing session - high volume expected";
            }
        }
        
        /**
         * Detect volume spikes
         */
        private static double detectVolumeSpike(long currentVolume, long[] volumeHistory) {
            if (volumeHistory.length == 0) return 0;
            
            double avgVolume = Arrays.stream(volumeHistory).average().orElse(1);
            double maxVolume = Arrays.stream(volumeHistory).max().orElse(1);
            
            if (currentVolume > maxVolume * 1.5) {
                return 1.0; // Exceptional spike
            } else if (currentVolume > avgVolume * 2) {
                return 0.5; // Significant spike
            }
            return 0;
        }
        
        /**
         * Calculate volume analysis confidence
         */
        private static double calculateVolumeConfidence(Map<String, Double> indicators, long currentVolume, double avgVolume) {
            double baseConfidence = 0.7;
            
            // Higher volume generally means higher confidence
            double volumeRatio = currentVolume / avgVolume;
            double volumeBonus = Math.min(0.2, volumeRatio * 0.1);
            
            // Agreement between indicators
            long positiveIndicators = indicators.values().stream().mapToLong(v -> v > 0.1 ? 1 : 0).sum();
            long negativeIndicators = indicators.values().stream().mapToLong(v -> v < -0.1 ? 1 : 0).sum();
            
            double agreementRatio = Math.max(positiveIndicators, negativeIndicators) / (double) indicators.size();
            double agreementBonus = agreementRatio * 0.15;
            
            return Math.min(0.95, baseConfidence + volumeBonus + agreementBonus);
        }
        
        /**
         * Generate volume-based trading recommendation
         */
        public static String getVolumeRecommendation(VolumeAnalysisResult analysis) {
            StringBuilder recommendation = new StringBuilder();
            
            recommendation.append("📊 **VOLUME ANALYSIS**\n");
            recommendation.append("═══════════════════════\n");
            recommendation.append(analysis.signal.emoji).append(" **Volume Signal**: ")
                         .append(analysis.signal.label).append("\n");
            recommendation.append("📈 **Volume Score**: ").append(String.format("%.2f", analysis.totalScore))
                         .append("/2.0\n");
            recommendation.append("⚡ **Confidence**: ").append(String.format("%.1f%%", analysis.confidence * 100))
                         .append("\n\n");
            
            // Trading implications
            switch (analysis.signal) {
                case STRONG_BULLISH:
                    recommendation.append("🚀 **Volume Confirms Strong Uptrend**\n");
                    recommendation.append("💡 Action: Enter long positions with confidence\n");
                    break;
                case BULLISH:
                    recommendation.append("📈 **Volume Supports Uptrend**\n");
                    recommendation.append("💡 Action: Consider long positions\n");
                    break;
                case NEUTRAL:
                    recommendation.append("⚖️ **Volume Neutral**\n");
                    recommendation.append("💡 Action: Wait for volume confirmation\n");
                    break;
                case BEARISH:
                    recommendation.append("📉 **Volume Indicates Weakness**\n");
                    recommendation.append("💡 Action: Consider profit booking/short\n");
                    break;
                case STRONG_BEARISH:
                    recommendation.append("🔻 **Volume Confirms Strong Selling**\n");
                    recommendation.append("💡 Action: Exit longs/enter shorts\n");
                    break;
            }
            
            recommendation.append("\n🔍 **Volume Indicators**:\n");
            for (Map.Entry<String, String> entry : analysis.explanations.entrySet()) {
                double indicatorValue = analysis.indicators.get(entry.getKey());
                String impact = indicatorValue > 0.1 ? "🟢" : indicatorValue < -0.1 ? "🔴" : "🟡";
                recommendation.append(impact).append(" ").append(entry.getKey()).append(": ")
                             .append(entry.getValue()).append("\n");
            }
            
            return recommendation.toString();
        }
    }
    
    /**
     * Volume analysis result
     */
    public static class VolumeAnalysisResult {
        public final VolumeAnalysisEngine.VolumeSignal signal;
        public final double totalScore;
        public final double confidence;
        public final Map<String, Double> indicators;
        public final Map<String, String> explanations;
        
        public VolumeAnalysisResult(VolumeAnalysisEngine.VolumeSignal signal, double totalScore, double confidence,
                                  Map<String, Double> indicators, Map<String, String> explanations) {
            this.signal = signal;
            this.totalScore = totalScore;
            this.confidence = confidence;
            this.indicators = indicators;
            this.explanations = explanations;
        }
    }
    
    /**
     * Test the volume analysis fix
     */
    public static void main(String[] args) {
        System.out.println("🔧 ACCURACY FIX PART 3: VOLUME ANALYSIS");
        System.out.println("══════════════════════════════════════");
        System.out.println();
        
        // Test with sample data
        long currentVolume = 18000000L;
        double currentPrice = 25820.0;
        double previousClose = 25750.0;
        long[] volumeHistory = {12000000L, 11500000L, 13000000L, 14000000L, 15500000L};
        double[] priceHistory = {25700.0, 25720.0, 25740.0, 25760.0, 25780.0};
        
        VolumeAnalysisResult analysis = VolumeAnalysisEngine.analyzeVolume(
            currentVolume, currentPrice, previousClose, volumeHistory, priceHistory);
        
        String recommendation = VolumeAnalysisEngine.getVolumeRecommendation(analysis);
        System.out.println(recommendation);
        
        System.out.println();
        System.out.println("✅ PART 3 COMPLETED: Volume analysis implemented");
        System.out.println("📊 5 Volume indicators: Ratio + Price-Volume + Trend + Pattern + Spike");
        System.out.println("🎯 Expected improvement: Better entry/exit timing with volume confirmation");
        System.out.println("🚀 Ready for Part 4: Trend Confirmation");
    }
}