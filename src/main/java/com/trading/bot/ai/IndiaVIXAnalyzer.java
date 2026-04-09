package com.trading.bot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * IndiaVIXAnalyzer — Dedicated India VIX intelligence engine.
 *
 * India VIX is the "fear gauge" of the Indian market.
 * It measures the 30-day implied volatility of NIFTY options.
 *
 * Why VIX matters for option trading:
 * ─────────────────────────────────────────────────────────────────
 * VIX LOW  (<14)  → options are CHEAP  → BEST TIME to buy options
 * VIX NORMAL(14-20)→ options are FAIR  → use technical direction
 * VIX HIGH (>20)  → options are COSTLY → prefer ITM, avoid naked OTM
 * VIX SPIKE (+15%)→ panic in market   → big move coming → BUY options
 * VIX FALLING FAST→ IV crush risk     → exit option positions quickly
 * ─────────────────────────────────────────────────────────────────
 *
 * This class also tracks VIX momentum to detect:
 * - VIX spike events (sudden fear bursts → directional moves)
 * - VIX mean reversion (VIX always reverts to ~15-17 long-term average)
 * - IV crush warnings (VIX falling after spike → don't hold bought options)
 */
public class IndiaVIXAnalyzer {

    // ─── VIX Regime Thresholds ────────────────────────────────────────────────
    private static final double VIX_EXTREME_LOW  = 10.0;
    private static final double VIX_LOW          = 14.0;
    private static final double VIX_NORMAL_HIGH  = 20.0;
    private static final double VIX_ELEVATED     = 25.0;
    private static final double VIX_HIGH         = 30.0;

    // VIX spike: if VIX rises > this % intraday → panic signal
    private static final double VIX_SPIKE_THRESHOLD = 12.0;

    // VIX crush: if VIX falls > this % from recent peak → IV crush
    private static final double VIX_CRUSH_THRESHOLD = 8.0;

    // ─── VIX History (rolling 20 readings for MA calculation) ────────────────
    private static final int    VIX_HISTORY_SIZE = 20;
    private final Deque<Double> vixHistory       = new ArrayDeque<>();
    private double              vixDayHigh       = 0;
    private double              vixDayLow        = Double.MAX_VALUE;
    private double              vixOpen          = 0;

    // ─── Result Object ────────────────────────────────────────────────────────

    public static class VIXAnalysis {
        // Core VIX readings
        public double currentVIX      = 15.0;
        public double prevCloseVIX    = 15.0;
        public double vixMA20         = 15.0;    // 20-reading moving average
        public double dayHigh         = 0;
        public double dayLow          = 0;
        public double intradayChange  = 0;       // % change from prev close
        public double maDeviation     = 0;       // % deviation from 20-day MA

        // Regime
        public String regime          = "NORMAL";
        // EXTREME_LOW / LOW / NORMAL / ELEVATED / HIGH / EXTREME / SPIKE / CRUSHING

        // Signals
        public String optionSignal    = "NEUTRAL";
        // BUY_OPTIONS_AGGRESSIVE / BUY_OPTIONS / NEUTRAL / USE_ITM / SELL_PREMIUM / PANIC_BUY

        public boolean isSpike        = false;   // VIX up >12% today
        public boolean isIVCrush      = false;   // VIX falling fast from spike
        public boolean isMeanRevert   = false;   // VIX far from MA, about to revert

        // Confidence adjustment to apply to technical signal
        public double confidenceBoost = 0;       // -15 to +15

        // Strike type recommendation
        public String strikeAdvice    = "ATM";   // ITM / ATM / OTM / AVOID_OTM

        // Human-readable summary
        public String summary         = "";
        public String telegramLine    = "";      // single-line for embedding in signal
    }

    // ─── Infrastructure ───────────────────────────────────────────────────────

    private static final String UPSTOX_BASE = "https://api.upstox.com/v2";
    private final HttpClient    httpClient  = HttpClient.newHttpClient();
    private final ObjectMapper  mapper      = new ObjectMapper();

    // ─── Main Analysis Method ─────────────────────────────────────────────────

    /**
     * Fetch and analyze India VIX. Safe to call every scan cycle — results
     * are returned immediately; history is updated in-place.
     *
     * @return VIXAnalysis with all regime/signal data populated
     */
    public VIXAnalysis analyze() {
        VIXAnalysis result = new VIXAnalysis();

        // 1. Fetch live VIX from Upstox
        fetchVIX(result);

        // 2. Update rolling history and compute MA
        updateHistory(result);

        // 3. Classify regime
        classifyRegime(result);

        // 4. Detect spike / crush / mean-reversion
        detectEvents(result);

        // 5. Generate option trading signal
        generateSignal(result);

        // 6. Format output
        result.summary    = buildSummary(result);
        result.telegramLine = buildTelegramLine(result);

        return result;
    }

    // ─── Step 1: Fetch VIX from Upstox ───────────────────────────────────────

    private void fetchVIX(VIXAnalysis result) {
        try {
            String token = readToken();
            // India VIX instrument key on Upstox
            String url = UPSTOX_BASE + "/market-quote/quotes"
                + "?symbol=NSE_INDEX%7CIndia+VIX";

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET().build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                // Try both possible key formats
                JsonNode vixNode = root.path("data").path("NSE_INDEX:India VIX");
                if (vixNode.isMissingNode()) {
                    vixNode = root.path("data").path("NSE_INDEX|India VIX");
                }
                if (!vixNode.isMissingNode()) {
                    result.currentVIX   = vixNode.path("last_price").asDouble(15.0);
                    result.prevCloseVIX = vixNode.path("ohlc").path("close").asDouble(result.currentVIX);
                    result.dayHigh      = vixNode.path("ohlc").path("high").asDouble(result.currentVIX);
                    result.dayLow       = vixNode.path("ohlc").path("low").asDouble(result.currentVIX);

                    // Track intraday high/low in memory for more accurate spike detection
                    vixDayHigh = Math.max(vixDayHigh, result.currentVIX);
                    vixDayLow  = Math.min(vixDayLow, result.currentVIX);
                    if (vixOpen == 0) vixOpen = result.prevCloseVIX;

                    if (result.prevCloseVIX > 0) {
                        result.intradayChange =
                            ((result.currentVIX - result.prevCloseVIX) / result.prevCloseVIX) * 100.0;
                    }
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("IndiaVIXAnalyzer: fetch error — " + e.getMessage());
        }

        // Fallback: estimate from typical market conditions
        result.currentVIX   = 15.0;
        result.prevCloseVIX = 15.0;
        result.dayHigh      = 15.5;
        result.dayLow       = 14.5;
    }

    // ─── Step 2: Rolling History & MA ────────────────────────────────────────

    private void updateHistory(VIXAnalysis result) {
        vixHistory.addLast(result.currentVIX);
        if (vixHistory.size() > VIX_HISTORY_SIZE) {
            vixHistory.pollFirst();
        }

        // Compute 20-reading VIX MA
        double sum = 0;
        for (double v : vixHistory) sum += v;
        result.vixMA20 = vixHistory.isEmpty() ? result.currentVIX : sum / vixHistory.size();

        // Deviation from MA (positive = VIX above average = fear elevated)
        if (result.vixMA20 > 0) {
            result.maDeviation =
                ((result.currentVIX - result.vixMA20) / result.vixMA20) * 100.0;
        }
    }

    // ─── Step 3: Classify VIX Regime ─────────────────────────────────────────

    private void classifyRegime(VIXAnalysis result) {
        double vix = result.currentVIX;

        if (vix < VIX_EXTREME_LOW) {
            result.regime = "EXTREME_LOW";
        } else if (vix < VIX_LOW) {
            result.regime = "LOW";
        } else if (vix < VIX_NORMAL_HIGH) {
            result.regime = "NORMAL";
        } else if (vix < VIX_ELEVATED) {
            result.regime = "ELEVATED";
        } else if (vix < VIX_HIGH) {
            result.regime = "HIGH";
        } else {
            result.regime = "EXTREME";
        }
    }

    // ─── Step 4: Detect Events ────────────────────────────────────────────────

    private void detectEvents(VIXAnalysis result) {
        // Spike: VIX jumped > threshold% from prev close
        if (result.intradayChange >= VIX_SPIKE_THRESHOLD) {
            result.isSpike  = true;
            result.regime   = "SPIKE";
        }

        // IV Crush: VIX is now falling hard after a spike
        // Detect if current VIX is > 8% below today's high (intraday reversal)
        if (vixDayHigh > 0 && result.currentVIX < vixDayHigh * (1 - VIX_CRUSH_THRESHOLD / 100.0)) {
            result.isIVCrush = true;
        }

        // Mean Reversion: VIX is significantly above or below its MA
        if (Math.abs(result.maDeviation) > 20) {
            result.isMeanRevert = true;
            // When VIX is >20% above MA → it will fall back → IV crush coming
            // When VIX is >20% below MA → it will rise back → options getting cheap now
        }
    }

    // ─── Step 5: Generate Option Signal ──────────────────────────────────────

    private void generateSignal(VIXAnalysis result) {
        double vix = result.currentVIX;

        // IV Crush warning is highest priority — don't buy options!
        if (result.isIVCrush) {
            result.optionSignal    = "AVOID_BUYING";
            result.strikeAdvice    = "AVOID_OTM";
            result.confidenceBoost = -10;
            return;
        }

        // Spike: panic buying → big move coming → buy options aggressively
        if (result.isSpike) {
            result.optionSignal    = "PANIC_BUY";
            result.strikeAdvice    = "ATM";
            result.confidenceBoost = +10;
            return;
        }

        // Regime-based signal
        switch (result.regime) {
            case "EXTREME_LOW":
                // VIX < 10: options are extremely cheap, rarely seen
                result.optionSignal    = "BUY_OPTIONS_AGGRESSIVE";
                result.strikeAdvice    = "OTM";   // even OTM is cheap enough
                result.confidenceBoost = +12;
                break;

            case "LOW":
                // VIX 10-14: options cheap → best time to buy directional
                result.optionSignal    = "BUY_OPTIONS";
                result.strikeAdvice    = "ATM";
                result.confidenceBoost = +8;
                break;

            case "NORMAL":
                // VIX 14-20: fair pricing → follow technicals
                result.optionSignal    = "NEUTRAL";
                result.strikeAdvice    = "ATM";
                result.confidenceBoost = 0;
                break;

            case "ELEVATED":
                // VIX 20-25: premiums elevated → prefer ITM
                result.optionSignal    = "USE_ITM";
                result.strikeAdvice    = "ITM";
                result.confidenceBoost = -3;
                break;

            case "HIGH":
                // VIX 25-30: expensive premiums, but big moves coming
                // If direction is strong, ITM protects from IV crush
                result.optionSignal    = "USE_ITM";
                result.strikeAdvice    = "ITM";
                result.confidenceBoost = -5;
                break;

            case "EXTREME":
                // VIX > 30: extreme fear = extreme moves → buy options for momentum
                result.optionSignal    = "BUY_OPTIONS";
                result.strikeAdvice    = "ATM";
                result.confidenceBoost = +6;
                break;

            default:
                result.optionSignal    = "NEUTRAL";
                result.strikeAdvice    = "ATM";
                result.confidenceBoost = 0;
        }

        // Mean reversion adjustment: VIX is very stretched, it will revert
        if (result.isMeanRevert) {
            if (result.maDeviation > 20) {
                // VIX very elevated above MA → expect VIX to fall → IV crush warning
                result.confidenceBoost -= 4;
            } else if (result.maDeviation < -20) {
                // VIX well below MA → expect VIX to rise → options getting cheap now
                result.confidenceBoost += 4;
            }
        }

        // Cap boost
        result.confidenceBoost = Math.max(-15, Math.min(15, result.confidenceBoost));
    }

    // ─── Step 6: Format Output ────────────────────────────────────────────────

    private String buildSummary(VIXAnalysis r) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("India VIX: %.1f [%s]", r.currentVIX, r.regime));
        if (Math.abs(r.intradayChange) > 1) {
            sb.append(r.intradayChange > 0 ? " ▲" : " ▼")
              .append(String.format("%.1f%%", Math.abs(r.intradayChange)));
        }
        sb.append(String.format(" | MA20: %.1f | Dev: %.1f%%", r.vixMA20, r.maDeviation));
        sb.append(" | Signal: ").append(r.optionSignal);
        if (r.isSpike)      sb.append(" | ⚠️ VIX SPIKE!");
        if (r.isIVCrush)    sb.append(" | ⚠️ IV CRUSH RISK!");
        if (r.isMeanRevert) sb.append(" | ↩️ Mean reversion likely");
        return sb.toString();
    }

    private String buildTelegramLine(VIXAnalysis r) {
        String icon;
        switch (r.regime) {
            case "EXTREME_LOW": icon = "🔵"; break;
            case "LOW":         icon = "🟢"; break;
            case "NORMAL":      icon = "⚪"; break;
            case "ELEVATED":    icon = "🟡"; break;
            case "HIGH":        icon = "🟠"; break;
            case "EXTREME":
            case "SPIKE":       icon = "🔴"; break;
            default:            icon = "⚪";
        }

        String advice;
        switch (r.optionSignal) {
            case "BUY_OPTIONS_AGGRESSIVE": advice = "Options VERY cheap → Buy aggressively"; break;
            case "BUY_OPTIONS":            advice = "Options cheap → Buy directionally"; break;
            case "USE_ITM":                advice = "Premiums high → Use ITM strike"; break;
            case "PANIC_BUY":              advice = "VIX SPIKE → Big move! Buy options"; break;
            case "AVOID_BUYING":           advice = "IV Crush risk → Avoid buying options"; break;
            default:                       advice = "Normal regime → Follow technicals";
        }

        return icon + " *VIX:* " + String.format("%.1f", r.currentVIX)
            + " [" + r.regime + "]"
            + (Math.abs(r.intradayChange) > 1.5
                ? (r.intradayChange > 0 ? " ▲" : " ▼") + String.format("%.1f%%", Math.abs(r.intradayChange))
                : "")
            + " — " + advice;
    }

    // ─── Reset intraday VIX highs/lows (call at market open) ─────────────────

    public void resetDailyTracking() {
        vixDayHigh = 0;
        vixDayLow  = Double.MAX_VALUE;
        vixOpen    = 0;
    }

    // ─── Token Helper ─────────────────────────────────────────────────────────

    private String readToken() {
        String env = System.getenv("UPSTOX_ACCESS_TOKEN");
        if (env != null && !env.isBlank()) return env;
        try {
            java.io.File f = new java.io.File("upstox_token.txt");
            if (f.exists()) {
                String t = Files.readString(Paths.get(f.getPath())).trim();
                if (!t.isBlank()) return t;
            }
        } catch (Exception ignored) {}
        // Fallback analytics token
        return "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza191MV8wIiwiYWxnIjoiSFMyNTYifQ"
             + ".eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OWMwYjgxZDQwMGJjNjBiYmMyMDE5ODUiL"
             + "CJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlzRXh0ZW5k"
             + "ZWQiOnRydWUsImlhdCI6MTc3NDIzNzcyNSwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2"
             + "aWNlIiwiZXhwIjoxODA1ODM5MjAwfQ"
             + ".AV4cr6-B0tD7asSaMDQjmPrX6FL2DmJsO_px-DatJRk";
    }
}
