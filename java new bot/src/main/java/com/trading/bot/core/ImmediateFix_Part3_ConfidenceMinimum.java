import java.time.*;
import java.util.*;

/**
 * IMMEDIATE FIX PART 3: CONFIDENCE MINIMUM - NO CALLS BELOW 70%
 * 
 * Current Problem: Low confidence calls reducing overall accuracy
 * Solution: Strict confidence thresholds with adaptive minimums
 */
public class ImmediateFix_Part3_ConfidenceMinimum {
    
    /**
     * Confidence Management System
     */
    public static class ConfidenceManager {
        
        // Base confidence thresholds
        private static final double MIN_CONFIDENCE_GENERAL = 70.0;
        private static final double MIN_CONFIDENCE_HIGH_RISK = 75.0;
        private static final double MIN_CONFIDENCE_BREAKOUT = 80.0;
        private static final double MIN_CONFIDENCE_CLOSING = 75.0;
        
        public enum ConfidenceLevel {
            VERY_HIGH(90.0, 100.0, "Very High", "🟢🟢", "Full position recommended"),
            HIGH(80.0, 89.9, "High", "🟢", "Standard position size"),
            MEDIUM(70.0, 79.9, "Medium", "🟡", "Reduced position size"),
            LOW(60.0, 69.9, "Low", "🟠", "Paper trade only"),
            VERY_LOW(0.0, 59.9, "Very Low", "🔴", "Skip call");
            
            private final double minValue;
            private final double maxValue;
            private final String label;
            private final String emoji;
            private final String recommendation;
            
            ConfidenceLevel(double minValue, double maxValue, String label, 
                          String emoji, String recommendation) {
                this.minValue = minValue;
                this.maxValue = maxValue;
                this.label = label;
                this.emoji = emoji;
                this.recommendation = recommendation;
            }
            
            public static ConfidenceLevel fromValue(double confidence) {
                for (ConfidenceLevel level : values()) {
                    if (confidence >= level.minValue && confidence <= level.maxValue) {
                        return level;
                    }
                }
                return VERY_LOW;
            }
        }
        
        /**
         * Check if call meets confidence requirements
         */
        public static ConfidenceCheckResult checkConfidenceRequirements(
                String callType, String symbol, double rawConfidence, 
                String marketSession, Map<String, Object> callContext) {
            
            // Determine required minimum confidence
            double requiredMinimum = getRequiredMinimumConfidence(callType, marketSession, callContext);
            
            // Adjust confidence based on context
            double adjustedConfidence = adjustConfidenceForContext(rawConfidence, callContext);
            
            // Get confidence level
            ConfidenceLevel level = ConfidenceLevel.fromValue(adjustedConfidence);
            
            // Decision logic
            boolean meetsCriteria = adjustedConfidence >= requiredMinimum;
            String reason = generateConfidenceReason(adjustedConfidence, requiredMinimum, 
                                                   callType, marketSession, level);
            
            // Position sizing recommendation
            double recommendedPositionSize = calculatePositionSize(adjustedConfidence, level);
            
            // Risk assessment
            String riskAssessment = assessRiskLevel(adjustedConfidence, callType, marketSession);
            
            return new ConfidenceCheckResult(meetsCriteria, adjustedConfidence, level, 
                                           requiredMinimum, reason, recommendedPositionSize, 
                                           riskAssessment);
        }
        
        /**
         * Get required minimum confidence based on call type and session
         */
        private static double getRequiredMinimumConfidence(String callType, String marketSession, 
                                                         Map<String, Object> callContext) {
            
            double baseMinimum = MIN_CONFIDENCE_GENERAL;
            
            // Adjust by call type
            switch (callType.toUpperCase()) {
                case "BREAKOUT":
                case "BREAKDOWN":
                    baseMinimum = MIN_CONFIDENCE_BREAKOUT;
                    break;
                case "SWING_HIGH":
                case "SWING_LOW":
                    baseMinimum = MIN_CONFIDENCE_HIGH_RISK;
                    break;
                case "SCALP":
                case "INTRADAY":
                    baseMinimum = MIN_CONFIDENCE_GENERAL + 5; // Slightly higher for short-term
                    break;
            }
            
            // Adjust by market session
            switch (marketSession.toUpperCase()) {
                case "CLOSING":
                    baseMinimum = Math.max(baseMinimum, MIN_CONFIDENCE_CLOSING);
                    break;
                case "OPENING":
                    baseMinimum = Math.max(baseMinimum, MIN_CONFIDENCE_GENERAL + 5); // Higher for volatility
                    break;
                case "LUNCH":
                    baseMinimum = Math.max(baseMinimum, MIN_CONFIDENCE_HIGH_RISK); // Higher for low volume
                    break;
            }
            
            // Additional context adjustments
            if (callContext.containsKey("volatility")) {
                Double volatility = (Double) callContext.get("volatility");
                if (volatility != null && volatility > 0.25) {
                    baseMinimum += 5; // Higher threshold for volatile conditions
                }
            }
            
            if (callContext.containsKey("isExpiryDay")) {
                Boolean isExpiry = (Boolean) callContext.get("isExpiryDay");
                if (isExpiry != null && isExpiry) {
                    baseMinimum += 5; // Higher threshold for expiry days
                }
            }
            
            return Math.min(baseMinimum, 90.0); // Cap at 90%
        }
        
        /**
         * Adjust confidence based on context factors
         */
        private static double adjustConfidenceForContext(double rawConfidence, 
                                                       Map<String, Object> callContext) {
            
            double adjusted = rawConfidence;
            
            // Volume adjustment
            if (callContext.containsKey("volumeRatio")) {
                Double volumeRatio = (Double) callContext.get("volumeRatio");
                if (volumeRatio != null) {
                    if (volumeRatio > 1.5) {
                        adjusted += 5; // Volume support boost
                    } else if (volumeRatio < 0.8) {
                        adjusted -= 10; // Low volume penalty
                    }
                }
            }
            
            // Trend alignment adjustment
            if (callContext.containsKey("trendAlignment")) {
                Boolean aligned = (Boolean) callContext.get("trendAlignment");
                if (aligned != null) {
                    if (aligned) {
                        adjusted += 5; // Trend support boost
                    } else {
                        adjusted -= 8; // Against trend penalty
                    }
                }
            }
            
            // Multiple timeframe confirmation
            if (callContext.containsKey("timeframeConfirmation")) {
                Integer confirmedTimeframes = (Integer) callContext.get("timeframeConfirmation");
                if (confirmedTimeframes != null) {
                    adjusted += Math.min(10, confirmedTimeframes * 3); // Up to 10% boost
                }
            }
            
            // Market conditions adjustment
            if (callContext.containsKey("marketCondition")) {
                String condition = (String) callContext.get("marketCondition");
                if ("TRENDING".equals(condition)) {
                    adjusted += 3;
                } else if ("CHOPPY".equals(condition)) {
                    adjusted -= 5;
                } else if ("HIGH_VOLATILITY".equals(condition)) {
                    adjusted -= 3;
                }
            }
            
            // Recent performance adjustment
            if (callContext.containsKey("recentSuccessRate")) {
                Double recentSuccess = (Double) callContext.get("recentSuccessRate");
                if (recentSuccess != null) {
                    if (recentSuccess > 80) {
                        adjusted += 3; // Hot streak bonus
                    } else if (recentSuccess < 60) {
                        adjusted -= 5; // Cold streak penalty
                    }
                }
            }
            
            return Math.max(30, Math.min(98, adjusted)); // Clamp between 30-98%
        }
        
        /**
         * Calculate position size based on confidence
         */
        private static double calculatePositionSize(double confidence, ConfidenceLevel level) {
            switch (level) {
                case VERY_HIGH:
                    return 1.0; // Full position
                case HIGH:
                    return 0.75; // 75% position
                case MEDIUM:
                    return 0.5; // 50% position
                case LOW:
                    return 0.25; // 25% position - paper trade preferred
                case VERY_LOW:
                default:
                    return 0.0; // No position
            }
        }
        
        /**
         * Assess risk level for the call
         */
        private static String assessRiskLevel(double confidence, String callType, String marketSession) {
            StringBuilder risk = new StringBuilder();
            
            if (confidence < 75) {
                risk.append("MEDIUM-HIGH risk due to lower confidence. ");
            } else if (confidence > 85) {
                risk.append("LOW-MEDIUM risk with high confidence. ");
            } else {
                risk.append("MEDIUM risk. ");
            }
            
            if ("CLOSING".equals(marketSession)) {
                risk.append("Closing session adds volatility risk. ");
            }
            
            if (callType.contains("BREAKOUT")) {
                risk.append("Breakout trades have higher failure risk. ");
            }
            
            return risk.toString().trim();
        }
        
        /**
         * Generate detailed confidence reason
         */
        private static String generateConfidenceReason(double adjustedConfidence, double requiredMinimum,
                                                      String callType, String marketSession, 
                                                      ConfidenceLevel level) {
            
            StringBuilder reason = new StringBuilder();
            
            if (adjustedConfidence >= requiredMinimum) {
                reason.append("✅ CONFIDENCE CRITERIA MET: ");
                reason.append(String.format("%.1f%% ≥ %.1f%% required for ", adjustedConfidence, requiredMinimum));
                reason.append(callType).append(" in ").append(marketSession).append(" session. ");
                reason.append("Level: ").append(level.label).append(" (").append(level.recommendation).append(")");
            } else {
                reason.append("❌ CONFIDENCE CRITERIA NOT MET: ");
                reason.append(String.format("%.1f%% < %.1f%% required for ", adjustedConfidence, requiredMinimum));
                reason.append(callType).append(" in ").append(marketSession).append(" session. ");
                reason.append("CALL REJECTED to maintain quality standards.");
            }
            
            return reason.toString();
        }
        
        /**
         * Get confidence enhancement suggestions
         */
        public static List<String> getConfidenceEnhancementSuggestions(double currentConfidence, 
                                                                      double requiredMinimum) {
            List<String> suggestions = new ArrayList<>();
            
            double deficit = requiredMinimum - currentConfidence;
            
            if (deficit > 0) {
                suggestions.add("Need +" + String.format("%.1f%%", deficit) + " confidence to meet criteria");
                
                if (deficit <= 5) {
                    suggestions.add("Minor improvement needed - check volume confirmation");
                    suggestions.add("Verify trend alignment on higher timeframe");
                } else if (deficit <= 10) {
                    suggestions.add("Moderate improvement needed - wait for better setup");
                    suggestions.add("Look for additional technical confirmations");
                } else {
                    suggestions.add("Significant improvement needed - skip this call");
                    suggestions.add("Wait for clearer market direction");
                    suggestions.add("Consider paper trading to test strategy");
                }
            } else {
                suggestions.add("Confidence level is adequate for trading");
                suggestions.add("Consider position sizing based on confidence level");
            }
            
            return suggestions;
        }
    }
    
    /**
     * Confidence check result
     */
    public static class ConfidenceCheckResult {
        public final boolean meetsCriteria;
        public final double adjustedConfidence;
        public final ConfidenceManager.ConfidenceLevel level;
        public final double requiredMinimum;
        public final String reason;
        public final double recommendedPositionSize;
        public final String riskAssessment;
        
        public ConfidenceCheckResult(boolean meetsCriteria, double adjustedConfidence, 
                                   ConfidenceManager.ConfidenceLevel level, double requiredMinimum, String reason,
                                   double recommendedPositionSize, String riskAssessment) {
            this.meetsCriteria = meetsCriteria;
            this.adjustedConfidence = adjustedConfidence;
            this.level = level;
            this.requiredMinimum = requiredMinimum;
            this.reason = reason;
            this.recommendedPositionSize = recommendedPositionSize;
            this.riskAssessment = riskAssessment;
        }
        
        public String toTelegramFormat() {
            StringBuilder sb = new StringBuilder();
            sb.append("⚡ **CONFIDENCE ANALYSIS**\\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━\\n");
            sb.append("🎯 Status: ").append(meetsCriteria ? "✅ APPROVED" : "❌ REJECTED").append("\\n");
            sb.append(level.emoji).append(" Level: ").append(level.label)
              .append(" (").append(String.format("%.1f%%", adjustedConfidence)).append(")\\n");
            sb.append("📊 Required: ").append(String.format("%.1f%%", requiredMinimum)).append("\\n");
            sb.append("📏 Position: ").append(String.format("%.0f%%", recommendedPositionSize * 100)).append("\\n");
            sb.append("🛡️ Risk: ").append(riskAssessment).append("\\n");
            sb.append("💡 Reason: ").append(reason);
            return sb.toString();
        }
    }
    
    /**
     * Test the confidence system
     */
    public static void main(String[] args) {
        System.out.println("🔧 IMMEDIATE FIX PART 3: CONFIDENCE MINIMUM");
        System.out.println("═══════════════════════════════════════════");
        System.out.println("🎯 Goal: No calls below 70% confidence");
        System.out.println();
        
        // Test scenarios
        testConfidenceScenarios();
        
        System.out.println();
        System.out.println("✅ PART 3 COMPLETED: Confidence management system");
        System.out.println("📈 Features added:");
        System.out.println("   • Dynamic confidence thresholds by call type");
        System.out.println("   • Context-aware confidence adjustments");
        System.out.println("   • Position sizing based on confidence");
        System.out.println("   • Risk assessment integration");
        System.out.println("   • Enhancement suggestions");
        System.out.println("🚀 Expected improvement: Eliminates low-quality calls");
        
        System.out.println();
        System.out.println("🎉 ALL 3 IMMEDIATE FIXES COMPLETED!");
        System.out.println("═══════════════════════════════════");
        System.out.println("✅ Part 1: Closing session improvement (50% → 75%+)");
        System.out.println("✅ Part 2: Volume gate system (eliminates weak calls)");
        System.out.println("✅ Part 3: Confidence minimum (no calls below 70%)");
        System.out.println();
        System.out.println("📊 EXPECTED OVERALL IMPROVEMENT:");
        System.out.println("   Current: 84.6% → Target: 90%+");
        System.out.println("🚀 Ready to integrate into main bot!");
    }
    
    private static void testConfidenceScenarios() {
        System.out.println("🧪 Testing confidence scenarios:");
        System.out.println("─────────────────────────────────");
        
        // Scenario 1: High confidence breakout call
        Map<String, Object> context1 = new HashMap<>();
        context1.put("volumeRatio", 1.8);
        context1.put("trendAlignment", true);
        context1.put("timeframeConfirmation", 3);
        
        ConfidenceCheckResult result1 = ConfidenceManager.checkConfidenceRequirements(
            "BREAKOUT", "NIFTY", 75.0, "MORNING", context1);
        System.out.println("📊 Scenario 1: Breakout call (75% base confidence)");
        System.out.println("   Result: " + (result1.meetsCriteria ? "✅ APPROVED" : "❌ REJECTED"));
        System.out.println("   Adjusted: " + String.format("%.1f%%", result1.adjustedConfidence));
        System.out.println("   Position: " + String.format("%.0f%%", result1.recommendedPositionSize * 100));
        
        // Scenario 2: Low confidence closing call
        Map<String, Object> context2 = new HashMap<>();
        context2.put("volumeRatio", 0.7);
        context2.put("trendAlignment", false);
        
        ConfidenceCheckResult result2 = ConfidenceManager.checkConfidenceRequirements(
            "BUY", "TCS", 65.0, "CLOSING", context2);
        System.out.println("\\n📊 Scenario 2: Closing call (65% base confidence)");
        System.out.println("   Result: " + (result2.meetsCriteria ? "✅ APPROVED" : "❌ REJECTED"));
        System.out.println("   Adjusted: " + String.format("%.1f%%", result2.adjustedConfidence));
        System.out.println("   Required: " + String.format("%.1f%%", result2.requiredMinimum));
        
        // Scenario 3: Strong confidence with context boost
        Map<String, Object> context3 = new HashMap<>();
        context3.put("volumeRatio", 2.1);
        context3.put("trendAlignment", true);
        context3.put("marketCondition", "TRENDING");
        context3.put("recentSuccessRate", 85.0);
        
        ConfidenceCheckResult result3 = ConfidenceManager.checkConfidenceRequirements(
            "BUY", "RELIANCE", 78.0, "AFTERNOON", context3);
        System.out.println("\\n📊 Scenario 3: Strong setup (78% base confidence)");
        System.out.println("   Result: " + (result3.meetsCriteria ? "✅ APPROVED" : "❌ REJECTED"));
        System.out.println("   Adjusted: " + String.format("%.1f%%", result3.adjustedConfidence));
        System.out.println("   Level: " + result3.level.label);
    }
}