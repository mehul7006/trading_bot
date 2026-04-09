package com.trading.bot.dhan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * DhanOptionChainFetcher — Fetches full option chain data from Dhan API.
 *
 * This replaces the incomplete Upstox option chain implementation.
 *
 * ──────────────────────────────────────────────────────────────────
 * DHAN OPTION CHAIN ENDPOINT
 * ──────────────────────────────────────────────────────────────────
 * POST https://api.dhan.co/v2/optionchain
 * Headers:
 *   access-token: <your_token>
 *   client-id:    <your_client_id>
 * Body:
 * {
 *   "UnderlyingScrip": 13,         // 13=NIFTY, 25=BANKNIFTY, 51=SENSEX
 *   "UnderlyingSeg": "IDX_I",      // IDX_I for NSE index, BSE_IDX for Sensex
 *   "Expiry": "2024-12-26"         // YYYY-MM-DD format
 * }
 *
 * Response contains for each strike:
 *   - strikePrice
 *   - callOption: { last_price, oi, change_in_oi, iv, delta, gamma, theta, vega }
 *   - putOption:  { last_price, oi, change_in_oi, iv, delta, gamma, theta, vega }
 * ──────────────────────────────────────────────────────────────────
 *
 * DHAN SYMBOL MAPPING:
 *   NIFTY50   → scrip=13,  seg=IDX_I
 *   BANKNIFTY → scrip=25,  seg=IDX_I
 *   SENSEX    → scrip=51,  seg=BSE_IDX
 *   FINNIFTY  → scrip=27,  seg=IDX_I
 *   MIDCPNIFTY→ scrip=442, seg=IDX_I
 */
public class DhanOptionChainFetcher {

    // ─── Symbol → Dhan Scrip Codes ───────────────────────────────────────────
    private static final Map<String, Integer> SCRIP_CODE = Map.of(
        "NIFTY50",    13,
        "BANKNIFTY",  25,
        "SENSEX",     51,
        "FINNIFTY",   27,
        "MIDCPNIFTY", 442
    );

    private static final Map<String, String> SEGMENT = Map.of(
        "NIFTY50",    "IDX_I",
        "BANKNIFTY",  "IDX_I",
        "SENSEX",     "BSE_IDX",
        "FINNIFTY",   "IDX_I",
        "MIDCPNIFTY", "IDX_I"
    );

    // ─── Result: Full Option Chain ────────────────────────────────────────────

    public static class OptionChainData {
        public String symbol;
        public String expiry;
        public double spotPrice;

        // All strikes in the chain
        public List<StrikeData> strikes = new ArrayList<>();

        // Aggregated metrics (calculated from the chain)
        public double pcr                = 1.0;   // Put-Call OI Ratio
        public long   totalCallOI        = 0;
        public long   totalPutOI         = 0;
        public long   totalCallOIChange  = 0;
        public long   totalPutOIChange   = 0;

        // ATM data
        public double atmStrike          = 0;
        public StrikeData atmData        = null;

        // Max Pain strike (calculated from chain)
        public double maxPainStrike      = 0;

        // IV metrics
        public double atmCallIV          = 0;
        public double atmPutIV           = 0;
        public double ivSkew             = 0;     // atmPutIV - atmCallIV (positive = fearful)

        // Top OI strikes
        public double highestCallOIStrike = 0;    // resistance
        public double highestPutOIStrike  = 0;    // support
    }

    public static class StrikeData {
        public double strike;

        // Call option
        public double callLTP;
        public long   callOI;
        public long   callOIChange;
        public double callIV;
        public double callDelta;
        public double callGamma;
        public double callTheta;
        public double callVega;

        // Put option
        public double putLTP;
        public long   putOI;
        public long   putOIChange;
        public double putIV;
        public double putDelta;
        public double putGamma;
        public double putTheta;
        public double putVega;

        // Calculated
        public boolean isATM = false;
    }

    // ─── Infrastructure ───────────────────────────────────────────────────────

    private final DhanApiClient client = DhanApiClient.getInstance();
    private final ObjectMapper  mapper = new ObjectMapper();

    // Cache: symbol → (expiry, chain) to avoid repeated API calls in same scan
    private final Map<String, CachedChain> cache = new HashMap<>();
    private static final long CACHE_TTL_MS = 2 * 60 * 1000; // 2 minutes

    private static class CachedChain {
        final OptionChainData data;
        final long fetchedAt;
        CachedChain(OptionChainData d) { data = d; fetchedAt = System.currentTimeMillis(); }
        boolean isValid() { return System.currentTimeMillis() - fetchedAt < CACHE_TTL_MS; }
    }

    // ─── Main Fetch Method ────────────────────────────────────────────────────

    /**
     * Fetch the full option chain for a symbol.
     * Cached for 2 minutes to avoid rate limiting.
     *
     * @param symbol    "NIFTY50" / "BANKNIFTY" / "SENSEX"
     * @param spotPrice Current underlying price (used to identify ATM)
     * @return OptionChainData with all strikes, Greeks, OI, IV
     */
    public OptionChainData fetchOptionChain(String symbol, double spotPrice) {
        // Check cache
        CachedChain cached = cache.get(symbol);
        if (cached != null && cached.isValid()) {
            // Update spotPrice in cached data (price changes, chain stays)
            cached.data.spotPrice = spotPrice;
            refreshATM(cached.data, spotPrice);
            return cached.data;
        }

        if (!client.isConfigured()) {
            System.err.println("DhanOptionChainFetcher: Dhan not configured — returning empty chain");
            return emptyChain(symbol, spotPrice);
        }

        // Get expiry date
        String expiry = getNextExpiryDate(symbol);

        // Build request body
        Integer scripCode = SCRIP_CODE.get(symbol);
        String  segment   = SEGMENT.get(symbol);
        if (scripCode == null) {
            System.err.println("DhanOptionChainFetcher: Unknown symbol " + symbol);
            return emptyChain(symbol, spotPrice);
        }

        String requestBody = String.format(
            "{\"UnderlyingScrip\":%d,\"UnderlyingSeg\":\"%s\",\"Expiry\":\"%s\"}",
            scripCode, segment, expiry
        );

        System.out.println("📡 Fetching Dhan option chain for " + symbol + " expiry=" + expiry);
        JsonNode response = client.post("/v2/optionchain", requestBody);

        if (response == null) {
            System.err.println("⚠️ DhanOptionChainFetcher: null response for " + symbol);
            return emptyChain(symbol, spotPrice);
        }

        // Parse the response
        OptionChainData chainData = parseChain(response, symbol, spotPrice, expiry);

        // Cache it
        cache.put(symbol, new CachedChain(chainData));
        System.out.printf("✅ Dhan option chain loaded for %s: %d strikes, MaxPain=%.0f, PCR=%.2f%n",
            symbol, chainData.strikes.size(), chainData.maxPainStrike, chainData.pcr);

        return chainData;
    }

    // ─── Parsing ──────────────────────────────────────────────────────────────

    private OptionChainData parseChain(JsonNode response, String symbol,
                                       double spotPrice, String expiry) {
        OptionChainData chain = new OptionChainData();
        chain.symbol    = symbol;
        chain.expiry    = expiry;
        chain.spotPrice = spotPrice;

        try {
            // Dhan response structure:
            // { "data": { "oc": { "<strike>": { "ce": {...}, "pe": {...} } } } }
            // OR: { "data": [ { "strikePrice": x, "callOption": {...}, "putOption": {...} } ] }
            // Handle both formats

            JsonNode data = response.path("data");

            // Try array format first
            if (data.isArray()) {
                parseArrayFormat(data, chain, spotPrice);
            } else {
                // Try object format (oc map)
                JsonNode oc = data.path("oc");
                if (!oc.isMissingNode()) {
                    parseObjectFormat(oc, chain, spotPrice);
                } else {
                    // Try root-level array
                    parseArrayFormat(response, chain, spotPrice);
                }
            }

            // Calculate aggregated metrics
            calculateAggregates(chain, spotPrice);

        } catch (Exception e) {
            System.err.println("DhanOptionChainFetcher: parse error for " + symbol + ": " + e.getMessage());
        }

        return chain;
    }

    /** Parse array format: [{ strikePrice, callOption:{...}, putOption:{...} }] */
    private void parseArrayFormat(JsonNode arr, OptionChainData chain, double spotPrice) {
        Iterator<JsonNode> iter = arr.elements();
        while (iter.hasNext()) {
            JsonNode s = iter.next();

            double strike = s.path("strikePrice").asDouble(
                s.path("strike_price").asDouble(
                    s.path("StrikePrice").asDouble(0)));
            if (strike <= 0) continue;

            // Only load strikes within ±6% of spot (keep it manageable)
            if (Math.abs(strike - spotPrice) > spotPrice * 0.06) continue;

            StrikeData sd = new StrikeData();
            sd.strike = strike;

            // Call option — try multiple key names (Dhan uses different names in different API versions)
            JsonNode ce = firstNonMissing(s, "callOption", "ce", "CE", "call_options");
            if (!ce.isMissingNode()) {
                sd.callLTP      = ce.path("last_price").asDouble(ce.path("ltp").asDouble(ce.path("LTP").asDouble(0)));
                sd.callOI       = ce.path("oi").asLong(ce.path("OI").asLong(ce.path("openInterest").asLong(0)));
                sd.callOIChange = ce.path("change_in_oi").asLong(ce.path("oiChange").asLong(0));
                sd.callIV       = ce.path("iv").asDouble(ce.path("impliedVolatility").asDouble(0));
                sd.callDelta    = ce.path("delta").asDouble(0);
                sd.callGamma    = ce.path("gamma").asDouble(0);
                sd.callTheta    = ce.path("theta").asDouble(0);
                sd.callVega     = ce.path("vega").asDouble(0);
            }

            // Put option
            JsonNode pe = firstNonMissing(s, "putOption", "pe", "PE", "put_options");
            if (!pe.isMissingNode()) {
                sd.putLTP      = pe.path("last_price").asDouble(pe.path("ltp").asDouble(pe.path("LTP").asDouble(0)));
                sd.putOI       = pe.path("oi").asLong(pe.path("OI").asLong(pe.path("openInterest").asLong(0)));
                sd.putOIChange = pe.path("change_in_oi").asLong(pe.path("oiChange").asLong(0));
                sd.putIV       = pe.path("iv").asDouble(pe.path("impliedVolatility").asDouble(0));
                sd.putDelta    = pe.path("delta").asDouble(0);
                sd.putGamma    = pe.path("gamma").asDouble(0);
                sd.putTheta    = pe.path("theta").asDouble(0);
                sd.putVega     = pe.path("vega").asDouble(0);
            }

            chain.strikes.add(sd);
        }
    }

    /** Parse object format: { "19000": { "ce": {...}, "pe": {...} } } */
    private void parseObjectFormat(JsonNode oc, OptionChainData chain, double spotPrice) {
        Iterator<Map.Entry<String, JsonNode>> fields = oc.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            double strike;
            try {
                strike = Double.parseDouble(entry.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            if (strike <= 0 || Math.abs(strike - spotPrice) > spotPrice * 0.06) continue;

            StrikeData sd   = new StrikeData();
            sd.strike       = strike;
            JsonNode strikeNode = entry.getValue();

            JsonNode ce = firstNonMissing(strikeNode, "ce", "CE", "call");
            if (!ce.isMissingNode()) {
                sd.callLTP      = ce.path("ltp").asDouble(0);
                sd.callOI       = ce.path("oi").asLong(0);
                sd.callOIChange = ce.path("change_in_oi").asLong(0);
                sd.callIV       = ce.path("iv").asDouble(0);
                sd.callDelta    = ce.path("delta").asDouble(0);
                sd.callGamma    = ce.path("gamma").asDouble(0);
                sd.callTheta    = ce.path("theta").asDouble(0);
                sd.callVega     = ce.path("vega").asDouble(0);
            }

            JsonNode pe = firstNonMissing(strikeNode, "pe", "PE", "put");
            if (!pe.isMissingNode()) {
                sd.putLTP      = pe.path("ltp").asDouble(0);
                sd.putOI       = pe.path("oi").asLong(0);
                sd.putOIChange = pe.path("change_in_oi").asLong(0);
                sd.putIV       = pe.path("iv").asDouble(0);
                sd.putDelta    = pe.path("delta").asDouble(0);
                sd.putGamma    = pe.path("gamma").asDouble(0);
                sd.putTheta    = pe.path("theta").asDouble(0);
                sd.putVega     = pe.path("vega").asDouble(0);
            }

            chain.strikes.add(sd);
        }
    }

    // ─── Aggregate Calculations ───────────────────────────────────────────────

    private void calculateAggregates(OptionChainData chain, double spotPrice) {
        if (chain.strikes.isEmpty()) return;

        // Sort strikes ascending
        chain.strikes.sort(Comparator.comparingDouble(s -> s.strike));

        // Identify ATM
        double atmStrike = chain.strikes.stream()
            .min(Comparator.comparingDouble(s -> Math.abs(s.strike - spotPrice)))
            .map(s -> s.strike).orElse(spotPrice);
        chain.atmStrike = atmStrike;

        long maxCallOI = 0, maxPutOI = 0;

        for (StrikeData sd : chain.strikes) {
            sd.isATM = Math.abs(sd.strike - atmStrike) < 1;

            // OI aggregation
            chain.totalCallOI       += sd.callOI;
            chain.totalPutOI        += sd.putOI;
            chain.totalCallOIChange += sd.callOIChange;
            chain.totalPutOIChange  += sd.putOIChange;

            // Track highest OI strikes
            if (sd.callOI > maxCallOI && sd.strike > spotPrice) {
                maxCallOI = sd.callOI;
                chain.highestCallOIStrike = sd.strike;
            }
            if (sd.putOI > maxPutOI && sd.strike < spotPrice) {
                maxPutOI = sd.putOI;
                chain.highestPutOIStrike = sd.strike;
            }

            // ATM data
            if (sd.isATM) {
                chain.atmData    = sd;
                chain.atmCallIV  = sd.callIV;
                chain.atmPutIV   = sd.putIV;
                chain.ivSkew     = sd.putIV - sd.callIV; // positive = fear
            }
        }

        // PCR (OI-based — more reliable than volume-based)
        chain.pcr = chain.totalCallOI > 0
            ? (double) chain.totalPutOI / chain.totalCallOI : 1.0;

        // Max Pain calculation
        chain.maxPainStrike = calculateMaxPain(chain);
    }

    private double calculateMaxPain(OptionChainData chain) {
        double minPain   = Double.MAX_VALUE;
        double maxPainSt = 0;

        for (StrikeData candidate : chain.strikes) {
            double totalPain = 0;
            for (StrikeData s : chain.strikes) {
                // Call holders lose if expiry below their strike
                if (candidate.strike < s.strike) {
                    totalPain += (s.strike - candidate.strike) * s.callOI;
                }
                // Put holders lose if expiry above their strike
                if (candidate.strike > s.strike) {
                    totalPain += (candidate.strike - s.strike) * s.putOI;
                }
            }
            if (totalPain < minPain) {
                minPain   = totalPain;
                maxPainSt = candidate.strike;
            }
        }
        return maxPainSt;
    }

    private void refreshATM(OptionChainData chain, double spotPrice) {
        chain.atmStrike = chain.strikes.stream()
            .min(Comparator.comparingDouble(s -> Math.abs(s.strike - spotPrice)))
            .map(s -> s.strike).orElse(spotPrice);
        chain.strikes.forEach(s -> s.isATM = Math.abs(s.strike - chain.atmStrike) < 1);
        chain.strikes.stream().filter(s -> s.isATM).findFirst().ifPresent(s -> {
            chain.atmData   = s;
            chain.atmCallIV = s.callIV;
            chain.atmPutIV  = s.putIV;
            chain.ivSkew    = s.putIV - s.callIV;
        });
    }

    // ─── Expiry Date Calculation ──────────────────────────────────────────────

    /**
     * Get the next weekly expiry date for the given symbol.
     * BANKNIFTY: Wednesday | NIFTY50/SENSEX/others: Thursday
     */
    public String getNextExpiryDate(String symbol) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        DayOfWeek expiryDay = "BANKNIFTY".equals(symbol)
            ? DayOfWeek.WEDNESDAY : DayOfWeek.THURSDAY;

        LocalDate expiry = today;
        // If today IS the expiry day, check if market is still open (before 3:30 PM)
        // Otherwise always go to NEXT expiry
        int attempts = 0;
        while (expiry.getDayOfWeek() != expiryDay || attempts == 0) {
            expiry = expiry.plusDays(1);
            attempts++;
            if (attempts > 10) break;
        }
        // If today is expiry day and market still open, use today
        if (today.getDayOfWeek() == expiryDay) {
            java.time.LocalTime now = java.time.LocalTime.now(ZoneId.of("Asia/Kolkata"));
            if (now.isBefore(java.time.LocalTime.of(15, 30))) {
                expiry = today;
            }
        }

        return expiry.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /**
     * Get all upcoming expiry dates (current week + next 3 weeks).
     * Used for monthly expiry selection.
     */
    public List<String> getUpcomingExpiries(String symbol) {
        List<String> expiries = new ArrayList<>();
        DayOfWeek expiryDay = "BANKNIFTY".equals(symbol)
            ? DayOfWeek.WEDNESDAY : DayOfWeek.THURSDAY;
        LocalDate d = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        for (int i = 0; i < 4; i++) {
            while (d.getDayOfWeek() != expiryDay) d = d.plusDays(1);
            expiries.add(d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            d = d.plusDays(1);
        }
        return expiries;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private JsonNode firstNonMissing(JsonNode parent, String... keys) {
        for (String key : keys) {
            JsonNode node = parent.path(key);
            if (!node.isMissingNode() && !node.isNull()) return node;
        }
        return parent.path("__nonexistent__"); // guaranteed missing
    }

    private OptionChainData emptyChain(String symbol, double spotPrice) {
        OptionChainData empty = new OptionChainData();
        empty.symbol    = symbol;
        empty.spotPrice = spotPrice;
        empty.pcr       = 1.0;
        return empty;
    }

    /** Clear cache (call at market open or token refresh). */
    public void clearCache() {
        cache.clear();
    }
}
