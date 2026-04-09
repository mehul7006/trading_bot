package com.trading.bot.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/**
 * GammaExposureEngine — Dedicated Gamma Exposure (GEX) model.
 *
 * WHAT IS GAMMA EXPOSURE (GEX)?
 * ─────────────────────────────────────────────────────────────────
 * When traders BUY options, market makers (MMs) SELL those options
 * and must delta-hedge their position by trading the underlying.
 *
 * GEX measures the net gamma exposure of all market makers combined:
 *
 *   Call GEX = +Gamma × Call OI × Spot² × 0.0001   (dealers are LONG gamma)
 *   Put GEX  = -Gamma × Put OI × Spot² × 0.0001    (dealers are SHORT gamma)
 *   Net GEX  = Sum of (Call GEX + Put GEX) across all strikes
 *
 * POSITIVE GEX (Net GEX > 0):
 *   → Dealers are net long gamma
 *   → When market rallies, dealers SELL futures (hedge) — dampens the rally
 *   → When market falls,  dealers BUY futures (hedge)  — dampens the fall
 *   → Result: RANGE-BOUND market, low volatility, good for selling options
 *   → AVOID directional OTM options here (they won't move enough)
 *
 * NEGATIVE GEX (Net GEX < 0):
 *   → Dealers are net short gamma
 *   → When market rallies, dealers must BUY MORE futures — amplifies the rally
 *   → When market falls,  dealers must SELL MORE futures — amplifies the fall
 *   → Result: TRENDING market, high volatility, momentum plays work best
 *   → BUY directional options here, use ITM for safety
 *
 * GEX FLIP LEVEL:
 *   → The specific strike price where net GEX crosses zero
 *   → Below the flip: negative GEX zone (trending)
 *   → Above the flip: positive GEX zone (rangebound)
 *   → When spot crosses the flip level → regime changes instantly
 * ─────────────────────────────────────────────────────────────────
 */
public class GammaExposureEngine {

    // ─── GEX Thresholds ───────────────────────────────────────────────────────

    // Above this → clearly positive GEX → range-bound
    private static final double GEX_POSITIVE_THRESHOLD  =  500_000;

    // Below this → clearly negative GEX → trending
    private static final double GEX_NEGATIVE_THRESHOLD  = -300_000;

    // ─── Result Object ────────────────────────────────────────────────────────

    public static class GEXResult {
        // Core GEX values
        public double netGEX          = 0;      // total net gamma exposure
        public double callGEX         = 0;      // total from call options
        public double putGEX          = 0;      // total from put options (negative)

        // Per-strike breakdown (top 5 strikes by absolute GEX)
        public Map<Double, Double> gexByStrike = new TreeMap<>();

        // GEX Flip Level
        public double flipLevel       = 0;      // strike where GEX sign changes
        public boolean spotAboveFlip  = false;  // true = positive GEX zone (range)
        public boolean spotBelowFlip  = false;  // true = negative GEX zone (trend)
        public double  distToFlip     = 0;      // how far spot is from flip level

        // Regime
        public String regime          = "NEUTRAL";
        // STRONG_POSITIVE / POSITIVE / NEUTRAL / NEGATIVE / STRONG_NEGATIVE

        // Trading style recommendation
        public String tradingStyle    = "DIRECTIONAL";
        // RANGE_SCALP / DIRECTIONAL / MOMENTUM / AVOID

        // Key GEX levels (strikes with large absolute GEX → act as S/R)
        public List<Double> gexResistanceLevels = new ArrayList<>();
        public List<Double> gexSupportLevels    = new ArrayList<>();

        // Largest positive GEX strike (dealer pinning level — price tends to gravitate here)
        public double pinningLevel    = 0;

        // Confidence adjustment
        public double confidenceBoost = 0;     // -10 to +10

        // Telegram-ready output
        public String telegramLine    = "";
        public String summary         = "";
    }

    // ─── Main Calculation ─────────────────────────────────────────────────────

    /**
     * Calculate Gamma Exposure from the option chain.
     *
     * @param chainData  Raw JSON from Upstox option chain API
     * @param spotPrice  Current underlying price
     * @param symbol     "NIFTY50" / "BANKNIFTY" / "SENSEX"
     * @return GEXResult with full regime analysis
     */
    public GEXResult calculate(JsonNode chainData, double spotPrice, String symbol) {
        GEXResult result = new GEXResult();

        if (chainData == null) {
            result.summary     = "GEX: unavailable (no chain data)";
            result.telegramLine = "⚡ *GEX:* N/A";
            return result;
        }

        // 1. Parse Greeks and OI from chain
        Map<Double, Double> callGammaOI = new TreeMap<>();
        Map<Double, Double> putGammaOI  = new TreeMap<>();
        parseChain(chainData, callGammaOI, putGammaOI, spotPrice);

        if (callGammaOI.isEmpty()) {
            result.summary     = "GEX: no Greeks data in chain";
            result.telegramLine = "⚡ *GEX:* N/A (no Greeks)";
            return result;
        }

        // 2. Calculate GEX per strike and totals
        calculateGEX(result, callGammaOI, putGammaOI, spotPrice);

        // 3. Find GEX flip level
        findFlipLevel(result, spotPrice);

        // 4. Find pinning level (highest positive GEX strike)
        findPinningLevel(result);

        // 5. Find key GEX S/R levels
        findKeyLevels(result, spotPrice);

        // 6. Classify regime
        classifyRegime(result);

        // 7. Generate confidence boost
        generateConfidenceBoost(result);

        // 8. Format output
        result.summary     = buildSummary(result, spotPrice);
        result.telegramLine = buildTelegramLine(result);

        return result;
    }

    // ─── Direction-Specific Boost ─────────────────────────────────────────────

    /**
     * Get the confidence boost for a specific technical direction.
     * Call AFTER calculate() with the technical direction.
     *
     * @param result    GEXResult from calculate()
     * @param direction "UP" or "DOWN"
     * @return confidence delta to add
     */
    public double getDirectionalBoost(GEXResult result, String direction) {
        if ("NEUTRAL".equals(result.regime)) return 0;

        boolean isNegativeGEX = result.netGEX < GEX_NEGATIVE_THRESHOLD;
        boolean isPositiveGEX = result.netGEX > GEX_POSITIVE_THRESHOLD;

        // In negative GEX, BOTH up and down signals get a boost (amplified moves)
        if (isNegativeGEX) {
            return +7; // both directions work in negative GEX, just be directional
        }

        // In positive GEX, directional signals are less reliable (moves get dampened)
        if (isPositiveGEX) {
            return -5; // range bound → directional signals are weaker
        }

        // Near flip level — signal in direction of momentum post-flip
        if (Math.abs(result.distToFlip) < getStrikeInterval(result, 0) * 2) {
            // Near flip = regime about to change → slightly risky
            return -2;
        }

        return 0;
    }

    // ─── Parsing ──────────────────────────────────────────────────────────────

    private void parseChain(JsonNode chainData,
                             Map<Double, Double> callGammaOI,
                             Map<Double, Double> putGammaOI,
                             double spotPrice) {
        try {
            JsonNode dataNode = chainData.path("data");
            if (dataNode.isMissingNode()) return;

            Iterator<JsonNode> iter = dataNode.elements();
            while (iter.hasNext()) {
                JsonNode s = iter.next();
                double strike = s.path("strike_price").asDouble(0);
                if (strike <= 0) continue;

                // Only consider strikes within ±4% of spot (where gamma is meaningful)
                if (Math.abs(strike - spotPrice) > spotPrice * 0.04) continue;

                double callGamma = s.path("call_options").path("greeks").path("gamma").asDouble(0);
                double putGamma  = s.path("put_options").path("greeks").path("gamma").asDouble(0);
                long   callOI    = s.path("call_options").path("market_data").path("oi").asLong(0);
                long   putOI     = s.path("put_options").path("market_data").path("oi").asLong(0);

                if (callGamma > 0 && callOI > 0) {
                    callGammaOI.put(strike, callGamma * callOI);
                }
                if (putGamma > 0 && putOI > 0) {
                    putGammaOI.put(strike, putGamma * putOI);
                }
            }
        } catch (Exception e) {
            System.err.println("GammaExposureEngine: chain parse error — " + e.getMessage());
        }
    }

    // ─── GEX Calculation ──────────────────────────────────────────────────────

    private void calculateGEX(GEXResult result,
                               Map<Double, Double> callGammaOI,
                               Map<Double, Double> putGammaOI,
                               double spotPrice) {
        double scaler = spotPrice * spotPrice * 0.0001; // normalize to reasonable units

        // Combine all strikes
        Set<Double> allStrikes = new TreeSet<>();
        allStrikes.addAll(callGammaOI.keySet());
        allStrikes.addAll(putGammaOI.keySet());

        double totalCallGEX = 0;
        double totalPutGEX  = 0;

        for (double strike : allStrikes) {
            double cGamma = callGammaOI.getOrDefault(strike, 0.0);
            double pGamma = putGammaOI.getOrDefault(strike, 0.0);

            // Call GEX = positive (dealers long gamma on calls they sold)
            double callGEX = +cGamma * scaler;
            // Put GEX  = negative (dealers short gamma on puts they sold)
            double putGEX  = -pGamma * scaler;

            double strikeGEX = callGEX + putGEX;
            result.gexByStrike.put(strike, strikeGEX);
            totalCallGEX += callGEX;
            totalPutGEX  += putGEX;
        }

        result.callGEX = totalCallGEX;
        result.putGEX  = totalPutGEX;
        result.netGEX  = totalCallGEX + totalPutGEX;
    }

    // ─── GEX Flip Level ───────────────────────────────────────────────────────

    private void findFlipLevel(GEXResult result, double spotPrice) {
        if (result.gexByStrike.isEmpty()) return;

        // Walk from low to high strike; find where cumulative GEX crosses zero
        double cumulative = 0;
        double prevStrike = 0;
        double flipLevel  = 0;

        for (Map.Entry<Double, Double> entry : result.gexByStrike.entrySet()) {
            double prevCum = cumulative;
            cumulative += entry.getValue();

            // Sign change → this is the flip level
            if (prevCum != 0 && Math.signum(cumulative) != Math.signum(prevCum)) {
                flipLevel = entry.getKey();
                break;
            }
            prevStrike = entry.getKey();
        }

        // If no flip found, estimate as the strike closest to zero cumulative GEX
        if (flipLevel == 0 && !result.gexByStrike.isEmpty()) {
            flipLevel = result.gexByStrike.entrySet().stream()
                .min(Comparator.comparingDouble(e -> Math.abs(e.getValue())))
                .map(Map.Entry::getKey)
                .orElse(spotPrice);
        }

        result.flipLevel     = flipLevel;
        result.spotAboveFlip = spotPrice > flipLevel;
        result.spotBelowFlip = spotPrice < flipLevel;
        result.distToFlip    = spotPrice - flipLevel;
    }

    // ─── Pinning Level ────────────────────────────────────────────────────────

    private void findPinningLevel(GEXResult result) {
        // Pinning level = strike with highest POSITIVE GEX
        // (largest dealer long gamma → they hedge most aggressively here → price gets pinned)
        result.pinningLevel = result.gexByStrike.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(0.0);
    }

    // ─── Key GEX S/R Levels ───────────────────────────────────────────────────

    private void findKeyLevels(GEXResult result, double spotPrice) {
        // Resistance: strikes with large positive GEX above spot
        // (dealers will sell there → price struggles to break above)
        result.gexResistanceLevels = new ArrayList<>();
        result.gexSupportLevels    = new ArrayList<>();

        result.gexByStrike.entrySet().stream()
            .filter(e -> e.getValue() > 0 && e.getKey() > spotPrice)
            .sorted(Map.Entry.<Double, Double>comparingByValue().reversed())
            .limit(2)
            .map(Map.Entry::getKey)
            .forEach(result.gexResistanceLevels::add);

        result.gexByStrike.entrySet().stream()
            .filter(e -> e.getValue() > 0 && e.getKey() < spotPrice)
            .sorted(Map.Entry.<Double, Double>comparingByValue().reversed())
            .limit(2)
            .map(Map.Entry::getKey)
            .forEach(result.gexSupportLevels::add);
    }

    // ─── Regime Classification ────────────────────────────────────────────────

    private void classifyRegime(GEXResult result) {
        if      (result.netGEX >  GEX_POSITIVE_THRESHOLD * 3) result.regime = "STRONG_POSITIVE";
        else if (result.netGEX >  GEX_POSITIVE_THRESHOLD)     result.regime = "POSITIVE";
        else if (result.netGEX < -GEX_NEGATIVE_THRESHOLD * 2) result.regime = "STRONG_NEGATIVE";
        else if (result.netGEX <  GEX_NEGATIVE_THRESHOLD)     result.regime = "NEGATIVE";
        else                                                    result.regime = "NEUTRAL";

        switch (result.regime) {
            case "STRONG_POSITIVE":
                result.tradingStyle = "RANGE_SCALP";   // sell premium, tight range
                break;
            case "POSITIVE":
                result.tradingStyle = "RANGE_SCALP";
                break;
            case "STRONG_NEGATIVE":
                result.tradingStyle = "MOMENTUM";      // big directional moves expected
                break;
            case "NEGATIVE":
                result.tradingStyle = "DIRECTIONAL";   // trend plays work here
                break;
            default:
                result.tradingStyle = "DIRECTIONAL";
        }
    }

    // ─── Confidence Boost ─────────────────────────────────────────────────────

    private void generateConfidenceBoost(GEXResult result) {
        switch (result.regime) {
            case "STRONG_NEGATIVE":
                result.confidenceBoost = +10; // amplified moves → directional wins
                break;
            case "NEGATIVE":
                result.confidenceBoost = +5;
                break;
            case "STRONG_POSITIVE":
                result.confidenceBoost = -8;  // moves dampened → directional signals weaker
                break;
            case "POSITIVE":
                result.confidenceBoost = -4;
                break;
            default:
                result.confidenceBoost = 0;
        }
    }

    // ─── Formatters ───────────────────────────────────────────────────────────

    private String buildSummary(GEXResult r, double spot) {
        return String.format(
            "Net GEX: %.0f | Regime: %s | Style: %s | Flip: %.0f | Distance to Flip: %+.0f | Pin: %.0f",
            r.netGEX, r.regime, r.tradingStyle, r.flipLevel, r.distToFlip, r.pinningLevel
        );
    }

    private String buildTelegramLine(GEXResult r) {
        String regimeIcon;
        String regimeDesc;
        switch (r.regime) {
            case "STRONG_NEGATIVE":
                regimeIcon = "🔥"; regimeDesc = "Strong trend — momentum plays"; break;
            case "NEGATIVE":
                regimeIcon = "⚡"; regimeDesc = "Trending — directional options"; break;
            case "STRONG_POSITIVE":
                regimeIcon = "🧊"; regimeDesc = "Strongly pinned — avoid directional"; break;
            case "POSITIVE":
                regimeIcon = "📦"; regimeDesc = "Range-bound — sell premium"; break;
            default:
                regimeIcon = "⚖️"; regimeDesc = "Neutral — follow technicals"; break;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(regimeIcon).append(" *GEX:* ").append(r.regime)
          .append(" — ").append(regimeDesc);

        if (r.flipLevel > 0) {
            sb.append(String.format("\n   🔀 *GEX Flip Level:* %.0f", r.flipLevel));
            if (Math.abs(r.distToFlip) < 100) {
                sb.append(" ⚠️ NEAR FLIP — regime may change!");
            }
        }
        if (r.pinningLevel > 0) {
            sb.append(String.format("\n   📌 *Pinning:* %.0f (price magnet)", r.pinningLevel));
        }
        if (!r.gexResistanceLevels.isEmpty()) {
            sb.append("\n   🧱 *GEX Resistance:* ");
            r.gexResistanceLevels.forEach(l -> sb.append(String.format("%.0f ", l)));
        }
        if (!r.gexSupportLevels.isEmpty()) {
            sb.append("\n   🛡️ *GEX Support:* ");
            r.gexSupportLevels.forEach(l -> sb.append(String.format("%.0f ", l)));
        }

        return sb.toString();
    }

    private double getStrikeInterval(GEXResult result, double spot) {
        // Infer from the strike gap in gexByStrike map
        if (result.gexByStrike.size() >= 2) {
            List<Double> strikes = new ArrayList<>(result.gexByStrike.keySet());
            return Math.abs(strikes.get(1) - strikes.get(0));
        }
        return 50; // default NIFTY interval
    }
}
