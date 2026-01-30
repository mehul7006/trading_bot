import java.time.*;
import java.util.*;

/**
 * IMMEDIATE FIX PART 2: VOLUME GATE - SKIP CALLS WITHOUT VOLUME CONFIRMATION
 * 
 * Current Problem: Calls generated without proper volume validation
 * Solution: Strict volume gates that prevent low-conviction calls
 */
public class ImmediateFix_Part2_VolumeGate {
    
    /**
     * Volume Gate System
     */
    public static class VolumeGateSystem {
        
        // Volume thresholds for different call types
        private static final double MIN_VOLUME_RATIO_BUY = 1.2;   // 20% above average
        private static final double MIN_VOLUME_RATIO_SELL = 1.1;  // 10% above average
        private static final double MIN_VOLUME_RATIO_BREAKOUT = 1.5; // 50% above average
        
        public enum VolumeSignal {
            STRONG_CONFIRMATION(2.0, "Strong volume confirmation"),
            GOOD_CONFIRMATION(1.5, "Good volume support"),
            WEAK_CONFIRMATION(1.2, "Weak volume support"),
            NO_CONFIRMATION(1.0, "No volume confirmation"),
            NEGATIVE_SIGNAL(0.8, "Low volume - negative signal");
            
            private final double threshold;
            private final String description;
            
            VolumeSignal(double threshold, String description) {
                this.threshold = threshold;
                this.description = description;
            }
            
            public static VolumeSignal fromRatio(double ratio) {
                if (ratio >= 2.0) return STRONG_CONFIRMATION;
                if (ratio >= 1.5) return GOOD_CONFIRMATION;
                if (ratio >= 1.2) return WEAK_CONFIRMATION;
                if (ratio >= 1.0) return NO_CONFIRMATION;
                return NEGATIVE_SIGNAL;
            }
        }
        
        /**
         * Comprehensive volume gate check
         */
        public static VolumeGateResult checkVolumeGate(String callType, double currentPrice,
                                                      double previousClose, long currentVolume,
                                                      double avgVolume, long[] recentVolumes) {
            
            // Calculate volume ratio
            double volumeRatio = currentVolume / avgVolume;
            VolumeSignal signal = VolumeSignal.fromRatio(volumeRatio);
            
            // Price movement analysis
            double priceChange = (currentPrice - previousClose) / previousClose * 100;
            boolean isPriceUp = priceChange > 0.1;
            boolean isPriceDown = priceChange < -0.1;
            
            // Volume-price relationship analysis
            VolumeRelationship relationship = analyzeVolumePriceRelationship(
                volumeRatio, priceChange);
            
            // Gate decision logic
            boolean passesGate = false;
            double confidenceAdjustment = 0;
            String gateReason = "";
            
            switch (callType.toUpperCase()) {
                case "BUY":
                    if (isPriceUp && volumeRatio >= MIN_VOLUME_RATIO_BUY) {
                        passesGate = true;
                        confidenceAdjustment = calculateConfidenceBonus(volumeRatio, true);
                        gateReason = "Price up with volume support - BUY confirmed";
                    } else if (isPriceDown && volumeRatio >= MIN_VOLUME_RATIO_SELL) {
                        // Oversold bounce with volume
                        passesGate = true;
                        confidenceAdjustment = calculateConfidenceBonus(volumeRatio, false) - 5;
                        gateReason = "Oversold with volume - potential bounce";
                    } else {
                        passesGate = false;
                        gateReason = "Insufficient volume for BUY call (need " + 
                                   MIN_VOLUME_RATIO_BUY + "x, got " + 
                                   String.format("%.1fx", volumeRatio) + ")";
                    }
                    break;
                    
                case "SELL":
                    if (isPriceDown && volumeRatio >= MIN_VOLUME_RATIO_SELL) {
                        passesGate = true;
                        confidenceAdjustment = calculateConfidenceBonus(volumeRatio, true);
                        gateReason = "Price down with volume support - SELL confirmed";
                    } else if (isPriceUp && volumeRatio >= MIN_VOLUME_RATIO_BUY) {
                        // Distribution at highs
                        passesGate = true;
                        confidenceAdjustment = calculateConfidenceBonus(volumeRatio, false) - 5;
                        gateReason = "High volume at resistance - potential distribution";
                    } else {
                        passesGate = false;
                        gateReason = "Insufficient volume for SELL call (need " + 
                                   MIN_VOLUME_RATIO_SELL + "x, got " + 
                                   String.format("%.1fx", volumeRatio) + ")";
                    }
                    break;
                    
                case "BREAKOUT":
                    if (volumeRatio >= MIN_VOLUME_RATIO_BREAKOUT) {
                        passesGate = true;
                        confidenceAdjustment = calculateConfidenceBonus(volumeRatio, true) + 10;
                        gateReason = "Strong volume confirms breakout";
                    } else {
                        passesGate = false;
                        gateReason = "Breakout needs strong volume (need " + 
                                   MIN_VOLUME_RATIO_BREAKOUT + "x, got " + 
                                   String.format("%.1fx", volumeRatio) + ")";
                    }
                    break;
                    
                default:
                    // For other call types, use moderate threshold
                    if (volumeRatio >= 1.1) {
                        passesGate = true;
                        confidenceAdjustment = calculateConfidenceBonus(volumeRatio, true);
                        gateReason = "Adequate volume for general call";
                    } else {
                        passesGate = false;
                        gateReason = "Below minimum volume threshold";
                    }
            }
            
            // Additional checks
            VolumeQualityCheck qualityCheck = performVolumeQualityCheck(
                recentVolumes, currentVolume, avgVolume);
            
            if (!qualityCheck.isPassing) {
                passesGate = false;
                gateReason += " | " + qualityCheck.reason;
            }
            
            return new VolumeGateResult(passesGate, signal, relationship, 
                                      confidenceAdjustment, gateReason, 
                                      volumeRatio, qualityCheck);
        }
        
        /**
         * Analyze volume-price relationship
         */
        private static VolumeRelationship analyzeVolumePriceRelationship(double volumeRatio, 
                                                                        double priceChange) {
            if (priceChange > 0.5 && volumeRatio > 1.3) {
                return new VolumeRelationship("BULLISH_CONFIRMATION", 
                    "Price up with strong volume - bullish");
            } else if (priceChange < -0.5 && volumeRatio > 1.3) {
                return new VolumeRelationship("BEARISH_CONFIRMATION", 
                    "Price down with strong volume - bearish");
            } else if (Math.abs(priceChange) > 0.5 && volumeRatio < 0.9) {
                return new VolumeRelationship("WEAK_MOVE", 
                    "Price move without volume - suspect");
            } else if (volumeRatio > 1.5 && Math.abs(priceChange) < 0.2) {
                return new VolumeRelationship("ACCUMULATION", 
                    "High volume with little price change - accumulation");
            } else {
                return new VolumeRelationship("NEUTRAL", "Normal volume-price relationship");
            }
        }
        
        /**
         * Calculate confidence bonus based on volume
         */
        private static double calculateConfidenceBonus(double volumeRatio, boolean isSupporting) {
            double baseBonus = Math.min(15, (volumeRatio - 1.0) * 10); // Up to 15% bonus
            return isSupporting ? baseBonus : baseBonus * 0.6; // Reduce if not supporting
        }
        
        /**
         * Perform additional volume quality checks
         */
        private static VolumeQualityCheck performVolumeQualityCheck(long[] recentVolumes, 
                                                                   long currentVolume, 
                                                                   double avgVolume) {
            
            // Check for consistent volume pattern
            if (recentVolumes.length >= 3) {
                long[] lastThree = Arrays.copyOfRange(recentVolumes, 
                    Math.max(0, recentVolumes.length - 3), recentVolumes.length);
                
                double recentAvg = Arrays.stream(lastThree).average().orElse(avgVolume);
                
                // Sudden volume spike check
                if (currentVolume > recentAvg * 3) {
                    return new VolumeQualityCheck(false, 
                        "Sudden volume spike - may be artificial");
                }
                
                // Declining volume pattern check
                boolean decliningPattern = true;
                for (int i = 1; i < lastThree.length; i++) {
                    if (lastThree[i] >= lastThree[i-1]) {
                        decliningPattern = false;
                        break;
                    }
                }
                
                if (decliningPattern && currentVolume < avgVolume * 0.7) {
                    return new VolumeQualityCheck(false, 
                        "Declining volume pattern - weak participation");
                }
            }
            
            // Check time-based volume expectations
            LocalTime now = LocalTime.now();
            double expectedVolumeMultiplier = getExpectedVolumeMultiplier(now);
            double adjustedThreshold = avgVolume * expectedVolumeMultiplier;
            
            if (currentVolume < adjustedThreshold * 0.8) {
                return new VolumeQualityCheck(false, 
                    "Below expected volume for current time (" + now + ")");
            }
            
            return new VolumeQualityCheck(true, "Volume quality checks passed");
        }
        
        /**
         * Get expected volume multiplier based on time of day
         */
        private static double getExpectedVolumeMultiplier(LocalTime time) {
            if (time.isBefore(LocalTime.of(10, 0))) {
                return 1.3; // Opening should have higher volume
            } else if (time.isBefore(LocalTime.of(11, 30))) {
                return 1.1; // Morning activity
            } else if (time.isBefore(LocalTime.of(13, 0))) {
                return 0.8; // Pre-lunch lull
            } else if (time.isBefore(LocalTime.of(14, 30))) {
                return 1.0; // Post-lunch normal
            } else {
                return 1.2; // Closing activity
            }
        }
    }
    
    // Supporting classes
    public static class VolumeGateResult {
        public final boolean passesGate;
        public final VolumeGateSystem.VolumeSignal signal;
        public final VolumeRelationship relationship;
        public final double confidenceAdjustment;
        public final String reason;
        public final double volumeRatio;
        public final VolumeQualityCheck qualityCheck;
        
        public VolumeGateResult(boolean passesGate, VolumeGateSystem.VolumeSignal signal, 
                               VolumeRelationship relationship, double confidenceAdjustment,
                               String reason, double volumeRatio, VolumeQualityCheck qualityCheck) {
            this.passesGate = passesGate;
            this.signal = signal;
            this.relationship = relationship;
            this.confidenceAdjustment = confidenceAdjustment;
            this.reason = reason;
            this.volumeRatio = volumeRatio;
            this.qualityCheck = qualityCheck;
        }
        
        public String toTelegramFormat() {
            StringBuilder sb = new StringBuilder();
            sb.append("📊 **VOLUME GATE ANALYSIS**\\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\\n");
            sb.append("🚥 Gate Status: ").append(passesGate ? "✅ PASSED" : "❌ FAILED").append("\\n");
            sb.append("📈 Volume Signal: ").append(signal.description).append("\\n");
            sb.append("🔗 Volume-Price: ").append(relationship.description).append("\\n");
            sb.append("📊 Volume Ratio: ").append(String.format("%.1fx", volumeRatio)).append("\\n");
            if (passesGate) {
                sb.append("⚡ Confidence Boost: +").append(String.format("%.1f%%", confidenceAdjustment)).append("\\n");
            }
            sb.append("💡 Reason: ").append(reason);
            return sb.toString();
        }
    }
    
    public static class VolumeRelationship {
        public final String type;
        public final String description;
        
        public VolumeRelationship(String type, String description) {
            this.type = type;
            this.description = description;
        }
    }
    
    public static class VolumeQualityCheck {
        public final boolean isPassing;
        public final String reason;
        
        public VolumeQualityCheck(boolean isPassing, String reason) {
            this.isPassing = isPassing;
            this.reason = reason;
        }
    }
    
    /**
     * Test the volume gate system
     */
    public static void main(String[] args) {
        System.out.println("🔧 IMMEDIATE FIX PART 2: VOLUME GATE");
        System.out.println("═══════════════════════════════════");
        System.out.println("🎯 Goal: Skip calls without volume confirmation");
        System.out.println();
        
        // Test scenarios
        testVolumeGateScenarios();
        
        System.out.println();
        System.out.println("✅ PART 2 COMPLETED: Volume gate system");
        System.out.println("📈 Features added:");
        System.out.println("   • Call-type specific volume thresholds");
        System.out.println("   • Volume-price relationship analysis");
        System.out.println("   • Volume quality checks");
        System.out.println("   • Time-based volume expectations");
        System.out.println("   • Confidence adjustments based on volume");
        System.out.println("🚀 Expected improvement: Eliminates low-conviction calls");
    }
    
    private static void testVolumeGateScenarios() {
        System.out.println("🧪 Testing volume gate scenarios:");
        System.out.println("──────────────────────────────────");
        
        long[] recentVolumes = {10000000L, 11000000L, 12000000L, 13000000L};
        
        // Scenario 1: BUY call with good volume
        System.out.println("📊 Scenario 1: BUY call with strong volume support");
        VolumeGateResult result1 = VolumeGateSystem.checkVolumeGate(
            "BUY", 25850, 25800, 18000000L, 12000000.0, recentVolumes);
        System.out.println("   Result: " + (result1.passesGate ? "✅ PASSED" : "❌ FAILED"));
        System.out.println("   Volume Ratio: " + String.format("%.1fx", result1.volumeRatio));
        System.out.println("   Confidence Boost: +" + String.format("%.1f%%", result1.confidenceAdjustment));
        
        // Scenario 2: SELL call with insufficient volume
        System.out.println("\\n📊 Scenario 2: SELL call with insufficient volume");
        VolumeGateResult result2 = VolumeGateSystem.checkVolumeGate(
            "SELL", 25800, 25850, 9000000L, 12000000.0, recentVolumes);
        System.out.println("   Result: " + (result2.passesGate ? "✅ PASSED" : "❌ FAILED"));
        System.out.println("   Reason: " + result2.reason);
        
        // Scenario 3: BREAKOUT call with exceptional volume
        System.out.println("\\n📊 Scenario 3: BREAKOUT call with exceptional volume");
        VolumeGateResult result3 = VolumeGateSystem.checkVolumeGate(
            "BREAKOUT", 25900, 25850, 25000000L, 12000000.0, recentVolumes);
        System.out.println("   Result: " + (result3.passesGate ? "✅ PASSED" : "❌ FAILED"));
        System.out.println("   Volume Ratio: " + String.format("%.1fx", result3.volumeRatio));
        System.out.println("   Confidence Boost: +" + String.format("%.1f%%", result3.confidenceAdjustment));
    }
}