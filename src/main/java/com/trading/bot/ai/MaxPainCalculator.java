package com.trading.bot.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/**
 * MaxPainCalculator — Dedicated Max Pain Theory engine.
 *
 * WHAT IS MAX PAIN?
 * ─────────────────────────────────────────────────────────────────
 * Max Pain is the strike price at which the maximum number of option
 * contracts (by total value) expire worthless.
 *
 * At expiry, market makers (who sold those options) benefit most when
 * price stays near the Max Pain strike, because they keep the premium.
 *
 * Studies show: on expiry day, the NIFTY/BANKNIFTY price gravitates
 * toward the Max Pain strike with ~75-80% probability in the last 2 hours.
 *
 * HOW IT HELPS YOUR WIN RATE:
 * ─────────────────────────────────────────────────────────────────
 * 1. On expiry day — if your technical signal goes AGAINST Max Pain,
 *    reduce confidence by 10-15% (price is likely to reverse)
 *
 * 2. If your technical signal goes TOWARD Max Pain,
 *    boost confidence by 8-12% (price has institutional gravity)
 *
 * 3. The "Max Pain Range" (±1 strike) is the deadzone —
 *    avoid entering if current price is already at max pain
 *
 * 4. 2 days before expiry: Max Pain pull starts activating
 *    (gamma exposure from dealers builds up)
 * ─────────────────────────────────────────────────────────────────
 */
public class MaxPainCalculator {

    // ─── Result Object ────────────────────────────────────────────────────────

    public static class MaxPainResult {
        // Core Max Pain data
        public double maxPainStrike   = 0;     // the exact Max Pain strike
        public double currentSpot     = 0;     // current underlying price
        public double distanceToMP    = 0;     // spot - maxPain (signed)
        public double distancePct     = 0;     // distance as % of spot

        // Pain values at key strikes (for context)
        public Map<Double, Double> painByStrike = new TreeMap<>();

        // Directional bias
        public String bias            = "NEUTRAL";
        // STRONG_BULLISH: spot well below MP → strong pull up
        // BULLISH:        spot below MP → moderate pull up
        // NEUTRAL:        spot at/near MP → no clear pull
        // BEARISH:        spot above MP → moderate pull down
        // STRONG_BEARISH: spot well above MP → strong pull down

        // Max Pain range (±1 strike from MP) — the "gravity zone"
        public double mpRangeHigh     = 0;
        public double mpRangeLow      = 0;
        public boolean spotInMPRange  = false;  // already in the gravity zone

        // Expiry context
        public boolean isExpiryDay    = false;
        public int     daysToExpiry   = 7;
        public double  mpPullStrength = 0;     // 0-100: how strong is pull today

        // Confidence adjustment to apply to technical signal
        public double  confidenceBoost = 0;    // -15 to +12

        // Telegram-ready line
        public String  telegramLine   = "";
        public String  summary        = "";
    }

    // ─── Main Calculation ─────────────────────────────────────────────────────

    /**
     * Calculate Max Pain from the option chain JSON node.
     *
     * @param chainData  Raw JSON from Upstox option chain API
     * @param spotPrice  Current underlying (NIFTY/BANKNIFTY/SENSEX) price
     * @param symbol     "NIFTY50" / "BANKNIFTY" / "SENSEX"
     * @return MaxPainResult with all fields populated
     */
    public MaxPainResult calculate(JsonNode chainData, double spotPrice, String symbol) {
        MaxPainResult result = new MaxPainResult();
        result.currentSpot = spotPrice;

        // Set expiry context
        setExpiryContext(result, symbol);

        if (chainData == null) {
            result.summary     = "Max Pain: unavailable (no chain data)";
            result.telegramLine = "🎯 *Max Pain:* N/A";
            return result;
        }

        // Extract all strikes and their OI
        Map<Double, Long> callOI = new TreeMap<>();
        Map<Double, Long> putOI  = new TreeMap<>();
        parseChainOI(chainData, callOI, putOI, spotPrice);

        if (callOI.isEmpty() || putOI.isEmpty()) {
            result.summary     = "Max Pain: insufficient OI data";
            result.telegramLine = "🎯 *Max Pain:* N/A (insufficient data)";
            return result;
        }

        // Calculate total pain at each strike
        Set<Double> allStrikes = new TreeSet<>();
        allStrikes.addAll(callOI.keySet());
        allStrikes.addAll(putOI.keySet());

        for (double expiryGuess : allStrikes) {
            double totalPain = 0;

            // For each actual strike, compute how much PAIN is caused if index expires at expiryGuess
            for (double s : allStrikes) {
                long cOI = callOI.getOrDefault(s, 0L);
                long pOI = putOI.getOrDefault(s, 0L);

                // Call holders lose money if expiryGuess < strike (call expires OTM)
                if (expiryGuess < s) totalPain += (s - expiryGuess) * cOI;

                // Put holders lose money if expiryGuess > strike (put expires OTM)
                if (expiryGuess > s) totalPain += (expiryGuess - s) * pOI;
            }

            result.painByStrike.put(expiryGuess, totalPain);
        }

        // Max Pain = strike where TOTAL PAIN is MINIMUM (least options value destroyed)
        // (All options holders collectively lose the least → market makers keep most premium)
        result.maxPainStrike = result.painByStrike.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(spotPrice);

        // Compute distance
        result.distanceToMP = spotPrice - result.maxPainStrike;
        result.distancePct  = result.maxPainStrike > 0
            ? (result.distanceToMP / result.maxPainStrike) * 100.0 : 0;

        // Max Pain range = ±1 strike interval
        double interval = getStrikeInterval(symbol, spotPrice);
        result.mpRangeHigh   = result.maxPainStrike + interval;
        result.mpRangeLow    = result.maxPainStrike - interval;
        result.spotInMPRange = spotPrice >= result.mpRangeLow && spotPrice <= result.mpRangeHigh;

        // Classify bias
        classifyBias(result, interval);

        // Calculate Max Pain pull strength based on days to expiry
        calculatePullStrength(result);

        // Generate confidence boost
        generateConfidenceBoost(result);

        // Format output
        result.summary     = buildSummary(result);
        result.telegramLine = buildTelegramLine(result);

        return result;
    }

    // ─── Signal: align technical direction with Max Pain pull ─────────────────

    /**
     * Returns confidence adjustment for a given technical direction.
     * Call this AFTER calculate() to get direction-specific boost.
     *
     * @param result    The MaxPainResult from calculate()
     * @param direction "UP" or "DOWN" from technical analysis
     * @return confidence delta to add (can be negative = penalty)
     */
    public double getDirectionalBoost(MaxPainResult result, String direction) {
        if (result.maxPainStrike <= 0 || result.mpPullStrength < 20) return 0;

        boolean technicalBullish = "UP".equals(direction);
        boolean mpPullBullish    = "BULLISH".equals(result.bias) || "STRONG_BULLISH".equals(result.bias);
        boolean mpPullBearish    = "BEARISH".equals(result.bias) || "STRONG_BEARISH".equals(result.bias);

        double pullFactor = result.mpPullStrength / 100.0; // 0-1

        if (technicalBullish && mpPullBullish) {
            // Technical UP + Max Pain pulling UP = strong confirmation
            return +(result.isExpiryDay ? 10 : 5) * pullFactor;
        } else if (!technicalBullish && mpPullBearish) {
            // Technical DOWN + Max Pain pulling DOWN = strong confirmation
            return +(result.isExpiryDay ? 10 : 5) * pullFactor;
        } else if (technicalBullish && mpPullBearish) {
            // Technical UP but Max Pain pulling DOWN = conflict → reduce confidence
            return -(result.isExpiryDay ? 12 : 4) * pullFactor;
        } else if (!technicalBullish && mpPullBullish) {
            // Technical DOWN but Max Pain pulling UP = conflict → reduce confidence
            return -(result.isExpiryDay ? 12 : 4) * pullFactor;
        }
        return 0;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void parseChainOI(JsonNode chainData,
                               Map<Double, Long> callOI,
                               Map<Double, Long> putOI,
                               double spotPrice) {
        try {
            JsonNode dataNode = chainData.path("data");
            if (dataNode.isMissingNode()) return;

            Iterator<JsonNode> iter = dataNode.elements();
            while (iter.hasNext()) {
                JsonNode s = iter.next();
                double strike = s.path("strike_price").asDouble(0);
                if (strike <= 0) continue;

                // Only consider strikes within ±5% of spot (relevant range)
                if (Math.abs(strike - spotPrice) > spotPrice * 0.05) continue;

                long cOI = s.path("call_options").path("market_data").path("oi").asLong(0);
                long pOI = s.path("put_options").path("market_data").path("oi").asLong(0);

                if (cOI > 0) callOI.put(strike, cOI);
                if (pOI > 0) putOI.put(strike, pOI);
            }
        } catch (Exception e) {
            System.err.println("MaxPainCalculator: OI parse error — " + e.getMessage());
        }
    }

    private void classifyBias(MaxPainResult result, double interval) {
        double dist = result.distanceToMP; // positive = spot above MP = bearish pull
        double threshold1 = interval;
        double threshold2 = interval * 2;

        if (dist > threshold2) {
            result.bias = "STRONG_BEARISH";  // spot far above MP → strong pull down
        } else if (dist > threshold1) {
            result.bias = "BEARISH";          // spot above MP → moderate pull down
        } else if (dist < -threshold2) {
            result.bias = "STRONG_BULLISH";   // spot far below MP → strong pull up
        } else if (dist < -threshold1) {
            result.bias = "BULLISH";           // spot below MP → moderate pull up
        } else {
            result.bias = "NEUTRAL";           // spot at/near MP
        }
    }

    private void calculatePullStrength(MaxPainResult result) {
        // Pull strength = 0% when 7+ days to expiry, ramps to 100% on expiry day
        if (result.daysToExpiry <= 0) {
            result.mpPullStrength = 100;
        } else if (result.daysToExpiry == 1) {
            result.mpPullStrength = 70;
        } else if (result.daysToExpiry == 2) {
            result.mpPullStrength = 40;
        } else if (result.daysToExpiry <= 4) {
            result.mpPullStrength = 15;
        } else {
            result.mpPullStrength = 5;  // very weak pull more than 4 days out
        }

        // Extra boost if bias is strong
        if (result.bias.startsWith("STRONG")) {
            result.mpPullStrength = Math.min(100, result.mpPullStrength * 1.3);
        }
    }

    private void generateConfidenceBoost(MaxPainResult result) {
        // Base: no boost (directional boost is applied per-direction in getDirectionalBoost)
        result.confidenceBoost = 0;

        // If spot is already IN the Max Pain zone → market is anchored, low volatility expected
        if (result.spotInMPRange) {
            result.confidenceBoost = -5; // don't chase directional here
        }
    }

    private void setExpiryContext(MaxPainResult result, String symbol) {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        java.time.DayOfWeek dow = today.getDayOfWeek();

        boolean isExpiry;
        if ("BANKNIFTY".equals(symbol)) {
            isExpiry = dow == java.time.DayOfWeek.WEDNESDAY;
            result.daysToExpiry = isExpiry ? 0 :
                (int) today.until(nextWeekday(today, java.time.DayOfWeek.WEDNESDAY),
                    java.time.temporal.ChronoUnit.DAYS);
        } else {
            isExpiry = dow == java.time.DayOfWeek.THURSDAY;
            result.daysToExpiry = isExpiry ? 0 :
                (int) today.until(nextWeekday(today, java.time.DayOfWeek.THURSDAY),
                    java.time.temporal.ChronoUnit.DAYS);
        }
        result.isExpiryDay = isExpiry;
    }

    private java.time.LocalDate nextWeekday(java.time.LocalDate from, java.time.DayOfWeek target) {
        java.time.LocalDate d = from.plusDays(1);
        while (d.getDayOfWeek() != target) d = d.plusDays(1);
        return d;
    }

    private double getStrikeInterval(String symbol, double spot) {
        if ("SENSEX".equals(symbol) || spot > 50000) return 500;
        if ("BANKNIFTY".equals(symbol) || spot > 40000) return 100;
        return 50;
    }

    private String buildSummary(MaxPainResult r) {
        return String.format(
            "Max Pain: %.0f | Spot: %.0f | Distance: %+.0f pts (%.1f%%) | Bias: %s | Pull: %.0f%% | Expiry: %s",
            r.maxPainStrike, r.currentSpot, r.distanceToMP, r.distancePct,
            r.bias, r.mpPullStrength, r.isExpiryDay ? "TODAY" : r.daysToExpiry + "d away"
        );
    }

    private String buildTelegramLine(MaxPainResult r) {
        if (r.maxPainStrike <= 0) return "🎯 *Max Pain:* N/A";

        String biasIcon;
        switch (r.bias) {
            case "STRONG_BULLISH": biasIcon = "🟢🟢"; break;
            case "BULLISH":        biasIcon = "🟢";   break;
            case "STRONG_BEARISH": biasIcon = "🔴🔴"; break;
            case "BEARISH":        biasIcon = "🔴";   break;
            default:               biasIcon = "⚪";   break;
        }

        String expiryTag = r.isExpiryDay
            ? " ⚠️ *EXPIRY TODAY* — Max Pain pull is ACTIVE"
            : (r.daysToExpiry <= 2 ? " (Pre-expiry, pull building)" : "");

        return String.format("🎯 *Max Pain:* %.0f %s%s",
            r.maxPainStrike, biasIcon, expiryTag)
            + (r.spotInMPRange ? "\n   ⚓ Spot is IN the Max Pain zone — anchored, avoid chasing" : "");
    }
}
