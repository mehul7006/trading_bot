package com.trading.bot.ai;

import com.trading.bot.ai.OptionMarketAnalyzer.OptionMarketIntelligence;
import com.trading.bot.ai.SmartStrikeSelector.StrikeRecommendation;

/**
 * OptionSignalEnhancer — The bridge between your existing technical signals
 * and the new options intelligence layer.
 *
 * This class takes an existing AIPredictor signal and enhances it with:
 * 1. Full option market analysis (VIX, Max Pain, GEX, IV Rank, OI, Skew)
 * 2. Smart strike selection (ITM/ATM/OTM decision)
 * 3. IV-adjusted premium targets
 * 4. Confidence boost/penalty based on options market alignment
 * 5. A rich Telegram message with all option details
 *
 * Usage (in Phase3TelegramBot.java):
 * ───────────────────────────────────
 * AIPredictor.AIPrediction base = aiPredictor.generatePrediction(symbol, data, optData);
 * if (base.confidence >= 88 && !base.predictedDirection.equals("NEUTRAL")) {
 *     OptionSignalEnhancer enhancer = new OptionSignalEnhancer();
 *     EnhancedOptionSignal enhanced = enhancer.enhance(symbol, base, spotPrice);
 *     sendMessage(chatId, enhanced.telegramMessage);
 * }
 */
public class OptionSignalEnhancer {

    public static class EnhancedOptionSignal {
        // Original signal data
        public final String symbol;
        public final String direction;
        public final double originalConfidence;

        // Enhanced confidence (with options market boost/penalty)
        public final double enhancedConfidence;

        // Option recommendation
        public final double recommendedStrike;
        public final String optionType;          // CE or PE
        public final String strikeType;          // ITM / ATM / OTM
        public final double premiumTarget;       // in points
        public final double premiumSL;           // in points
        public final double underlyingTarget;    // underlying move target
        public final double underlyingSL;        // underlying stop loss

        // Market intelligence snapshot
        public final OptionMarketIntelligence intel;

        // Telegram-ready formatted message
        public final String telegramMessage;

        // Should we trade this signal?
        public final boolean isActionable;
        public final String skipReason;

        public EnhancedOptionSignal(String symbol, String direction,
                                    double originalConfidence, double enhancedConfidence,
                                    double strike, String optionType, String strikeType,
                                    double premTarget, double premSL,
                                    double ulTarget, double ulSL,
                                    OptionMarketIntelligence intel,
                                    String telegramMsg, boolean actionable, String skipReason) {
            this.symbol = symbol;
            this.direction = direction;
            this.originalConfidence = originalConfidence;
            this.enhancedConfidence = enhancedConfidence;
            this.recommendedStrike = strike;
            this.optionType = optionType;
            this.strikeType = strikeType;
            this.premiumTarget = premTarget;
            this.premiumSL = premSL;
            this.underlyingTarget = ulTarget;
            this.underlyingSL = ulSL;
            this.intel = intel;
            this.telegramMessage = telegramMsg;
            this.isActionable = actionable;
            this.skipReason = skipReason;
        }
    }

    private final OptionMarketAnalyzer optionAnalyzer = new OptionMarketAnalyzer();
    private final SmartStrikeSelector  strikeSelector  = new SmartStrikeSelector();

    /**
     * Enhance an existing technical signal with full options market intelligence.
     *
     * @param symbol      "NIFTY50" / "BANKNIFTY" / "SENSEX"
     * @param base        The AIPredictor signal (already >= 88% confidence)
     * @param spotPrice   Current underlying price
     * @return EnhancedOptionSignal ready to send via Telegram
     */
    public EnhancedOptionSignal enhance(String symbol,
                                        AIPredictor.AIPrediction base,
                                        double spotPrice) {
        // ── 1. Run full options market analysis ──────────────────────────────
        OptionMarketIntelligence intel = optionAnalyzer.analyze(symbol, spotPrice);

        // ── 2. Apply confidence boost from options market alignment ──────────
        double enhancedConf = base.confidence + intel.optionConfidenceBoost;

        // Additional boosts when options market aligns with technical direction
        if ("UP".equals(base.predictedDirection)) {
            // Bullish technical + bullish options market = high conviction
            if ("PUT_BUILDUP".equals(intel.oiTrend))         enhancedConf += 4;
            if ("GREED".equals(intel.skewSignal))            enhancedConf += 3;
            if ("BULLISH".equals(intel.maxPainBias))          enhancedConf += 5;
            if (intel.callPutOIRatio > 1.2)                  enhancedConf += 3;

            // Conflicts: options say bearish but technical says bullish → reduce confidence
            if ("CALL_BUILDUP".equals(intel.oiTrend))        enhancedConf -= 5;
            if ("FEAR".equals(intel.skewSignal))             enhancedConf -= 4;
            if ("BEARISH".equals(intel.maxPainBias) && intel.isExpiryDay) enhancedConf -= 8;

        } else if ("DOWN".equals(base.predictedDirection)) {
            // Bearish technical + bearish options market = high conviction
            if ("CALL_BUILDUP".equals(intel.oiTrend))        enhancedConf += 4;
            if ("FEAR".equals(intel.skewSignal))              enhancedConf += 3;
            if ("BEARISH".equals(intel.maxPainBias))          enhancedConf += 5;
            if (intel.callPutOIRatio < 0.8)                  enhancedConf += 3;

            if ("PUT_BUILDUP".equals(intel.oiTrend))         enhancedConf -= 5;
            if ("GREED".equals(intel.skewSignal))            enhancedConf -= 4;
            if ("BULLISH".equals(intel.maxPainBias) && intel.isExpiryDay) enhancedConf -= 8;
        }

        // Cap confidence at 97
        enhancedConf = Math.min(97, Math.max(0, enhancedConf));

        // ── 3. Check if signal is still actionable after options filter ───────
        String skipReason = "";
        boolean isActionable = true;

        // Block: expiry afternoon (theta crush)
        java.time.LocalTime now = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (intel.isExpiryDay && now.isAfter(java.time.LocalTime.of(12, 30))) {
            isActionable = false;
            skipReason = "Expiry afternoon — theta decay too extreme for buying options";
        }

        // Block: extreme VIX spike in wrong direction (too risky)
        if ("SPIKE".equals(intel.vixRegime) && intel.vixChangePercent > 20) {
            if (enhancedConf < 90) {
                isActionable = false;
                skipReason = "VIX spike >20% — wait for VIX to stabilize before entering";
            }
        }

        // Block: strong opposing GEX signal (positive GEX = range, don't buy directional OTM)
        if ("POSITIVE".equals(intel.gexRegime) && "OTM".equals(intel.recommendedStrikeType)) {
            // Downgrade to ATM, don't block entirely
            intel.recommendedStrikeType = "ATM";
        }

        // ── 4. Smart strike selection ────────────────────────────────────────
        StrikeRecommendation strike = strikeSelector.selectOptimalStrike(
            intel, base.predictedDirection, spotPrice, symbol,
            base.estimatedMovePoints, base.suggestedStopLoss
        );

        // ── 5. Build the Telegram message ────────────────────────────────────
        String telegramMsg = buildTelegramMessage(
            symbol, base, intel, strike, spotPrice, enhancedConf, isActionable, skipReason
        );

        return new EnhancedOptionSignal(
            symbol, base.predictedDirection, base.confidence, enhancedConf,
            strike.recommendedStrike, strike.optionType, strike.strikeType,
            strike.expectedPremiumTarget, strike.expectedPremiumSL,
            base.estimatedMovePoints, base.suggestedStopLoss,
            intel, telegramMsg, isActionable, skipReason
        );
    }

    // ── Telegram Message Builder ──────────────────────────────────────────────

    private String buildTelegramMessage(String symbol,
                                         AIPredictor.AIPrediction base,
                                         OptionMarketIntelligence intel,
                                         StrikeRecommendation strike,
                                         double spotPrice,
                                         double enhancedConf,
                                         boolean isActionable,
                                         String skipReason) {
        StringBuilder sb = new StringBuilder();

        if (!isActionable) {
            sb.append("⛔ *SIGNAL BLOCKED BY OPTIONS FILTER*\n\n");
            sb.append("📌 *Symbol:* ").append(symbol).append("\n");
            sb.append("📊 *Direction:* ").append(base.predictedDirection).append("\n");
            sb.append("🚫 *Reason:* ").append(skipReason).append("\n\n");
            sb.append("_Technical signal was ").append(String.format("%.0f", base.confidence))
              .append("% but options market says WAIT._");
            return sb.toString();
        }

        String emoji = "UP".equals(base.predictedDirection) ? "📈" : "📉";
        String direction = "UP".equals(base.predictedDirection) ? "BULLISH" : "BEARISH";

        sb.append(emoji).append(" *OPTION TRADE SIGNAL — ").append(symbol).append("*\n\n");

        // Core signal
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🎯 *Direction:* ").append(direction).append("\n");
        sb.append("📊 *Spot Price:* ").append(String.format("%.2f", spotPrice)).append("\n");
        sb.append("🔢 *Confidence:* ").append(String.format("%.1f", enhancedConf)).append("%");

        // Show boost/penalty
        double boost = enhancedConf - base.confidence;
        if (Math.abs(boost) >= 1) {
            sb.append(boost > 0
                ? String.format(" *(+%.0f from Options)*", boost)
                : String.format(" *(%.0f Options penalty)*", boost));
        }
        sb.append("\n\n");

        // Option recommendation
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🏷️ *RECOMMENDED OPTION*\n");
        sb.append("📌 *Strike:* ").append(String.format("%.0f", strike.recommendedStrike))
          .append(" ").append(strike.optionType).append("\n");
        sb.append("📦 *Type:* ").append(strike.strikeType)
          .append(" (Delta ~").append(String.format("%.2f", strike.expectedDelta)).append(")\n");
        sb.append("🎯 *Premium Target:* +").append(String.format("%.0f", strike.expectedPremiumTarget)).append(" pts\n");
        sb.append("🛡️ *Premium SL:* -").append(String.format("%.0f", strike.expectedPremiumSL)).append(" pts\n");
        sb.append("📊 *Underlying Target:* ").append(String.format("%.0f", base.estimatedMovePoints)).append(" pts\n");
        sb.append("🛑 *Underlying SL:* ").append(String.format("%.0f", base.suggestedStopLoss)).append(" pts\n\n");

        // Options market intelligence
        sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🧠 *OPTIONS MARKET INTEL*\n\n");

        // VIX
        sb.append("📊 *India VIX:* ").append(String.format("%.1f", intel.indiaVix))
          .append(" [").append(intel.vixRegime).append("]");
        if (Math.abs(intel.vixChangePercent) > 2) {
            sb.append(intel.vixChangePercent > 0 ? " ▲" : " ▼")
              .append(String.format("%.1f%%", Math.abs(intel.vixChangePercent)));
        }
        sb.append("\n");

        // IV Rank
        sb.append("💰 *IV Rank:* ").append(String.format("%.0f%%", intel.ivRank));
        if (intel.ivRank < 20) {
            sb.append(" 🟢 Options CHEAP → Best time to BUY");
        } else if (intel.ivRank > 70) {
            sb.append(" 🔴 Options EXPENSIVE → Use ITM/spreads");
        } else {
            sb.append(" 🔵 Normal");
        }
        sb.append("\n");

        // OI Analysis
        if (intel.strongestCallOI > 0) {
            sb.append("🧱 *Call OI Wall:* ").append(String.format("%.0f", intel.strongestCallOI))
              .append(" (Resistance)\n");
        }
        if (intel.strongestPutOI > 0) {
            sb.append("🛡️ *Put OI Floor:* ").append(String.format("%.0f", intel.strongestPutOI))
              .append(" (Support)\n");
        }
        sb.append("📊 *OI Trend:* ").append(intel.oiTrend.replace("_", " ")).append("\n");

        // PCR (OI-based)
        sb.append("⚖️ *PCR (OI):* ").append(String.format("%.2f", intel.callPutOIRatio));
        if (intel.callPutOIRatio > 1.2)       sb.append(" 🟢 Bullish");
        else if (intel.callPutOIRatio < 0.8)  sb.append(" 🔴 Bearish");
        else                                   sb.append(" 🔵 Neutral");
        sb.append("\n");

        // GEX
        sb.append("⚡ *GEX Regime:* ").append(intel.gexRegime);
        if ("NEGATIVE".equals(intel.gexRegime)) {
            sb.append(" → ").append("Strong trend expected");
        } else if ("POSITIVE".equals(intel.gexRegime)) {
            sb.append(" → ").append("Range-bound, dampened moves");
        }
        sb.append("\n");
        if (intel.gexFlipLevel > 0) {
            sb.append("🔀 *GEX Flip:* ").append(String.format("%.0f", intel.gexFlipLevel)).append("\n");
        }

        // IV Skew
        sb.append("📐 *IV Skew:* ").append(intel.skewSignal);
        if (!"NEUTRAL".equals(intel.skewSignal)) {
            sb.append(" (").append(String.format("%.1f%%", intel.ivSkew)).append(")");
        }
        sb.append("\n");

        // Max Pain
        if (intel.maxPainStrike > 0) {
            sb.append("🎯 *Max Pain:* ").append(String.format("%.0f", intel.maxPainStrike));
            if (intel.isExpiryDay) {
                sb.append(" ⚠️ EXPIRY TODAY — Max Pain pull active!");
            }
            sb.append("\n");
        }

        // Expiry warning
        if (!intel.expiryWarning.isEmpty()) {
            sb.append("\n").append(intel.expiryWarning).append("\n");
        }

        // Trade plan
        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📋 *TRADE PLAN*\n");
        sb.append("1️⃣ Buy ").append(String.format("%.0f", strike.recommendedStrike))
          .append(" ").append(strike.optionType).append(" (").append(strike.strikeType).append(")\n");
        sb.append("2️⃣ Entry: at market / use limit near current premium\n");
        sb.append("3️⃣ Target: +").append(String.format("%.0f", strike.expectedPremiumTarget)).append(" pts on premium\n");
        sb.append("4️⃣ SL: -").append(String.format("%.0f", strike.expectedPremiumSL)).append(" pts on premium\n");
        sb.append("5️⃣ R:R = 1:").append(String.format("%.1f", strike.expectedPremiumTarget / Math.max(1, strike.expectedPremiumSL)));

        // Risk warning
        sb.append("\n\n⚠️ _Always verify live premium before entry. Past performance ≠ future results._");

        return sb.toString();
    }
}
