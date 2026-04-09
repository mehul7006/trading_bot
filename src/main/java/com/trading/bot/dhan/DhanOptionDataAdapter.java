package com.trading.bot.dhan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * DhanOptionDataAdapter — Converts Dhan OptionChainData → Upstox-compatible JsonNode.
 *
 * WHY THIS EXISTS:
 * ──────────────────────────────────────────────────────────────────────────────
 * The existing OptionMarketAnalyzer pillar methods (analyzeOIChain, analyzeGEX,
 * analyzeIVSkew, etc.) were written to parse the Upstox option chain JSON format:
 *
 *   chain.path("data")                              → array of strikes
 *   strike.path("strike_price")                     → strike price
 *   strike.path("call_options").path("market_data").path("oi")
 *   strike.path("call_options").path("market_data").path("oi_day_change")
 *   strike.path("call_options").path("greeks").path("gamma")
 *   strike.path("call_options").path("greeks").path("iv")
 *   strike.path("call_options").path("greeks").path("delta")
 *
 * This adapter creates EXACTLY that structure from Dhan's OptionChainData,
 * so ALL existing analysis code continues to work with ZERO changes.
 *
 * DUAL API STRATEGY:
 *   Dhan API  → primary   (more reliable option chain data)
 *   Upstox API → fallback  (existing code, unchanged)
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class DhanOptionDataAdapter {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Convert Dhan OptionChainData to Upstox-format JsonNode.
     *
     * The returned JsonNode matches the structure that OptionMarketAnalyzer's
     * internal pillar methods (analyzeOIChain, analyzeGEX, analyzeIVSkew, etc.) expect.
     *
     * @param dhanChain  The OptionChainData from DhanOptionChainFetcher
     * @return JsonNode in Upstox option chain format (or null if empty)
     */
    public static JsonNode toUpstoxFormat(DhanOptionChainFetcher.OptionChainData dhanChain) {
        if (dhanChain == null || dhanChain.strikes.isEmpty()) return null;

        // Root node: { "data": [ ...strikes... ] }
        ObjectNode root = mapper.createObjectNode();
        ArrayNode dataArr = mapper.createArrayNode();

        for (DhanOptionChainFetcher.StrikeData sd : dhanChain.strikes) {
            ObjectNode strikeNode = mapper.createObjectNode();
            strikeNode.put("strike_price", sd.strike);

            // ── call_options ──────────────────────────────────────────────────
            ObjectNode callOptions = mapper.createObjectNode();

            // call_options.market_data
            ObjectNode callMarket = mapper.createObjectNode();
            callMarket.put("ltp",          sd.callLTP);
            callMarket.put("oi",           sd.callOI);
            callMarket.put("oi_day_change", sd.callOIChange);
            callOptions.set("market_data", callMarket);

            // call_options.greeks
            ObjectNode callGreeks = mapper.createObjectNode();
            callGreeks.put("delta", sd.callDelta);
            callGreeks.put("gamma", sd.callGamma);
            callGreeks.put("theta", sd.callTheta);
            callGreeks.put("vega",  sd.callVega);
            callGreeks.put("iv",    sd.callIV);
            callOptions.set("greeks", callGreeks);

            strikeNode.set("call_options", callOptions);

            // ── put_options ───────────────────────────────────────────────────
            ObjectNode putOptions = mapper.createObjectNode();

            // put_options.market_data
            ObjectNode putMarket = mapper.createObjectNode();
            putMarket.put("ltp",           sd.putLTP);
            putMarket.put("oi",            sd.putOI);
            putMarket.put("oi_day_change",  sd.putOIChange);
            putOptions.set("market_data", putMarket);

            // put_options.greeks
            ObjectNode putGreeks = mapper.createObjectNode();
            putGreeks.put("delta", sd.putDelta);
            putGreeks.put("gamma", sd.putGamma);
            putGreeks.put("theta", sd.putTheta);
            putGreeks.put("vega",  sd.putVega);
            putGreeks.put("iv",    sd.putIV);
            putOptions.set("greeks", putGreeks);

            strikeNode.set("put_options", putOptions);

            dataArr.add(strikeNode);
        }

        root.set("data", dataArr);

        // Inject Dhan-computed aggregates as metadata (optional, for reference)
        ObjectNode meta = mapper.createObjectNode();
        meta.put("source",               "DHAN");
        meta.put("symbol",               dhanChain.symbol);
        meta.put("expiry",               dhanChain.expiry != null ? dhanChain.expiry : "");
        meta.put("spot_price",           dhanChain.spotPrice);
        meta.put("total_call_oi",        dhanChain.totalCallOI);
        meta.put("total_put_oi",         dhanChain.totalPutOI);
        meta.put("pcr",                  dhanChain.pcr);
        meta.put("max_pain_strike",      dhanChain.maxPainStrike);
        meta.put("atm_call_iv",          dhanChain.atmCallIV);
        meta.put("atm_put_iv",           dhanChain.atmPutIV);
        meta.put("iv_skew",              dhanChain.ivSkew);
        meta.put("highest_call_oi_strike", dhanChain.highestCallOIStrike);
        meta.put("highest_put_oi_strike",  dhanChain.highestPutOIStrike);
        root.set("dhan_meta", meta);

        return root;
    }

    /**
     * Quick check: does this JsonNode come from Dhan adapter?
     * Useful for deciding whether to skip re-computation of Max Pain
     * (Dhan already computed it).
     */
    public static boolean isDhanSource(JsonNode chain) {
        if (chain == null) return false;
        JsonNode meta = chain.path("dhan_meta");
        return !meta.isMissingNode() && "DHAN".equals(meta.path("source").asText(""));
    }

    /**
     * Extract pre-computed Max Pain from Dhan-sourced chain data.
     * Returns 0 if not from Dhan or not available.
     */
    public static double getDhanMaxPain(JsonNode chain) {
        if (!isDhanSource(chain)) return 0;
        return chain.path("dhan_meta").path("max_pain_strike").asDouble(0);
    }

    /**
     * Extract pre-computed PCR from Dhan-sourced chain data.
     */
    public static double getDhanPCR(JsonNode chain) {
        if (!isDhanSource(chain)) return 1.0;
        return chain.path("dhan_meta").path("pcr").asDouble(1.0);
    }
}
