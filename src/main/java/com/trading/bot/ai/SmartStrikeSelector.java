package com.trading.bot.ai;

import com.trading.bot.ai.OptionMarketAnalyzer.OptionMarketIntelligence;

/**
 * SmartStrikeSelector — Picks the optimal option strike based on
 * the full OptionMarketIntelligence context.
 *
 * WHY strike selection matters:
 * - Wrong strike = right direction but no profit (theta kills you)
 * - Right strike  = right direction → 3-5x premium return
 *
 * Rules:
 * ─────────────────────────────────────────────────────────────────
 * Scenario                   | Best Strike    | Reason
 * ─────────────────────────────────────────────────────────────────
 * Strong trend (GEX negative)| ITM (delta 0.7)| High delta, less theta risk
 * Normal market              | ATM (delta 0.5)| Best gamma-theta balance
 * Low IV (cheap options)     | ATM/OTM        | Gamma play, cheap entry
 * High IV (expensive)        | ITM or spread  | Avoid theta bomb
 * Expiry day morning         | ATM            | Max gamma, fast moves
 * Expiry day afternoon       | AVOID          | Theta crush extreme
 * ─────────────────────────────────────────────────────────────────
 */
public class SmartStrikeSelector {

    public static class StrikeRecommendation {
        public final double recommendedStrike;
        public final String optionType;        // CE or PE
        public final double expectedDelta;
        public final String strikeType;        // ITM / ATM / OTM
        public final String reasoning;
        public final double expectedPremiumTarget;
        public final double expectedPremiumSL;
        public final double ivAdjustedTarget;  // target adjusted for IV regime

        public StrikeRecommendation(double strike, String type, double delta,
                                    String strikeType, String reasoning,
                                    double target, double sl, double ivTarget) {
            this.recommendedStrike = strike;
            this.optionType = type;
            this.expectedDelta = delta;
            this.strikeType = strikeType;
            this.reasoning = reasoning;
            this.expectedPremiumTarget = target;
            this.expectedPremiumSL = sl;
            this.ivAdjustedTarget = ivTarget;
        }

        @Override
        public String toString() {
            return String.format(
                "Strike: %.0f %s | Delta: %.2f | %s | Target: %.0f pts | SL: %.0f pts\n%s",
                recommendedStrike, optionType, expectedDelta, strikeType,
                expectedPremiumTarget, expectedPremiumSL, reasoning);
        }
    }

    /**
     * Select the best strike based on all market intelligence.
     *
     * @param intel       Full OptionMarketIntelligence from OptionMarketAnalyzer
     * @param direction   "UP" or "DOWN" (from underlying technical signal)
     * @param spotPrice   Current underlying price
     * @param symbol      "NIFTY50" / "BANKNIFTY" / "SENSEX"
     * @param baseTargetPts  Target points from AIPredictor (underlying move)
     * @param baseSLPts      Stop loss points from AIPredictor
     * @return StrikeRecommendation with exact strike and premium targets
     */
    public StrikeRecommendation selectOptimalStrike(
            OptionMarketIntelligence intel,
            String direction,
            double spotPrice,
            String symbol,
            double baseTargetPts,
            double baseSLPts) {

        String optionType = "UP".equals(direction) ? "CE" : "PE";
        double strikeInterval = getStrikeInterval(symbol, spotPrice);

        // ── Step 1: Determine strike type ───────────────────────────────────
        String strikeType = determineStrikeType(intel);

        // ── Step 2: Calculate recommended strike ────────────────────────────
        double recommendedStrike;
        double expectedDelta;

        switch (strikeType) {
            case "ITM":
                // 1-2 strikes ITM: higher delta, protects against theta
                if ("CE".equals(optionType)) {
                    recommendedStrike = roundStrike(spotPrice - strikeInterval, strikeInterval);
                } else {
                    recommendedStrike = roundStrike(spotPrice + strikeInterval, strikeInterval);
                }
                expectedDelta = 0.70;
                break;

            case "OTM":
                // 1 strike OTM: lower delta but cheap; use only in low IV
                if ("CE".equals(optionType)) {
                    recommendedStrike = roundStrike(spotPrice + strikeInterval, strikeInterval);
                } else {
                    recommendedStrike = roundStrike(spotPrice - strikeInterval, strikeInterval);
                }
                expectedDelta = 0.30;
                break;

            default: // ATM
                recommendedStrike = roundStrike(spotPrice, strikeInterval);
                expectedDelta = 0.50;
                break;
        }

        // ── Step 3: Calculate IV-adjusted premium targets ───────────────────
        // Base: Delta × Underlying Move
        double baseTarget = expectedDelta * baseTargetPts;
        double baseSL     = expectedDelta * baseSLPts;

        // IV adjustment: in high IV, premium erodes faster if move is slow
        // In low IV, premium benefits from any IV expansion when move comes
        double ivMultiplier = getIVMultiplier(intel);
        double adjustedTarget = baseTarget * ivMultiplier;
        double adjustedSL     = baseSL * 0.8; // always use tighter SL in options

        // GEX adjustment: negative GEX = trends are stronger = higher target
        if ("NEGATIVE".equals(intel.gexRegime)) {
            adjustedTarget *= 1.25;
        } else if ("POSITIVE".equals(intel.gexRegime)) {
            adjustedTarget *= 0.80; // dampened market, lower target
        }

        // Gamma boost: near-ATM options have high gamma = target can be higher if quick move
        if ("ATM".equals(strikeType) && intel.daysToExpiry <= 2) {
            adjustedTarget *= 1.30; // expiry week gamma explosion
        }

        // Minimum premium targets (absolute floor)
        double minTarget = getMinPremiumTarget(symbol, strikeType);
        adjustedTarget = Math.max(adjustedTarget, minTarget);
        adjustedSL     = Math.max(adjustedSL, minTarget * 0.4);

        // ── Step 4: Build reasoning string ──────────────────────────────────
        StringBuilder reason = new StringBuilder();
        reason.append(String.format("Strike Type: %s | Delta: %.2f", strikeType, expectedDelta));

        if ("NEGATIVE".equals(intel.gexRegime)) {
            reason.append(" | GEX Negative → Strong trend, ITM preferred");
        }
        if ("LOW".equals(intel.vixRegime)) {
            reason.append(" | VIX Low → Options cheap, buy ATM/OTM");
        }
        if ("HIGH".equals(intel.vixRegime)) {
            reason.append(" | VIX High → Premiums expensive, use ITM");
        }
        if (intel.isExpiryDay) {
            reason.append(" | ⚠️ Expiry day — trade only morning window (9:15-10:30)");
        }
        if (intel.ivRank < 20) {
            reason.append(String.format(" | IV Rank %.0f%% = CHEAP (great buy opportunity)", intel.ivRank));
        } else if (intel.ivRank > 70) {
            reason.append(String.format(" | IV Rank %.0f%% = EXPENSIVE (use spreads)", intel.ivRank));
        }

        // OI support/resistance context
        if ("CE".equals(optionType) && !intel.resistanceLevels.isEmpty()) {
            double nearestResistance = intel.resistanceLevels.get(0);
            if (nearestResistance > spotPrice && nearestResistance - spotPrice < baseTargetPts) {
                reason.append(String.format(" | ⚠️ OI Resistance at %.0f (below target — reduce target)", nearestResistance));
                adjustedTarget = Math.min(adjustedTarget, (nearestResistance - spotPrice) * expectedDelta * 0.9);
            }
        }
        if ("PE".equals(optionType) && !intel.supportLevels.isEmpty()) {
            double nearestSupport = intel.supportLevels.get(0);
            if (nearestSupport < spotPrice && spotPrice - nearestSupport < baseTargetPts) {
                reason.append(String.format(" | ⚠️ OI Support at %.0f (above SL — strong floor)", nearestSupport));
            }
        }

        return new StrikeRecommendation(
            recommendedStrike, optionType, expectedDelta, strikeType,
            reason.toString(), adjustedTarget, adjustedSL, adjustedTarget
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String determineStrikeType(OptionMarketIntelligence intel) {
        // Priority rules for strike selection
        if (intel.isExpiryDay) return "ATM";                           // expiry: ATM gamma play
        if ("NEGATIVE".equals(intel.gexRegime)) return "ITM";         // strong trend: ITM
        if ("BUY_OPTIONS".equals(intel.ivStrategy)) return "ATM";      // cheap IV: ATM
        if ("SELL_OPTIONS".equals(intel.ivStrategy)) return "ITM";     // expensive IV: ITM (less theta)
        if ("HIGH".equals(intel.vixRegime)) return "ITM";             // high fear: ITM protection
        if ("POSITIVE".equals(intel.gexRegime)) return "ATM";         // rangebound: ATM
        return "ATM";                                                   // default
    }

    private double getIVMultiplier(OptionMarketIntelligence intel) {
        // Low IV: premium can expand significantly when direction hits
        if (intel.ivRank < 20) return 1.30;
        // High IV: premium already high, expect IV crush as you profit
        if (intel.ivRank > 70) return 0.75;
        // Normal
        return 1.0;
    }

    private double getMinPremiumTarget(String symbol, String strikeType) {
        switch (symbol) {
            case "BANKNIFTY":
                return "OTM".equals(strikeType) ? 20 : "ATM".equals(strikeType) ? 25 : 30;
            case "SENSEX":
                return "OTM".equals(strikeType) ? 50 : "ATM".equals(strikeType) ? 60 : 80;
            default: // NIFTY50
                return "OTM".equals(strikeType) ? 10 : "ATM".equals(strikeType) ? 15 : 20;
        }
    }

    private double roundStrike(double price, double interval) {
        return Math.round(price / interval) * interval;
    }

    private double getStrikeInterval(String symbol, double price) {
        if ("SENSEX".equals(symbol) || price > 50000) return 500;
        if ("BANKNIFTY".equals(symbol) || price > 40000) return 100;
        return 50; // NIFTY50
    }
}
