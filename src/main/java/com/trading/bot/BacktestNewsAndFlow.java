package com.trading.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.agents.PredictionAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * BacktestNewsAndFlow — Experimental test of 3 new data sources:
 *
 *  1. News Sentiment (NewsAPI.org)  — properly backtested over 30 trading days
 *  2. Options PCR    (Upstox live)  — live snapshot only (no historical OI data free)
 *  3. GIFT Nifty gap (first-candle) — proxied from existing OHLCV, tested 30 days
 *
 * DO NOT ADD TO MAIN CODE until backtest confirms WR improvement.
 */
public class BacktestNewsAndFlow {

    // ── Config ──────────────────────────────────────────────────────────────────
    private static final String NEWS_API_KEY   = "f7fe912d3367462180f2da8641fa4d11";
    private static final String NEWS_API_BASE  = "https://newsapi.org/v2/everything";
    private static final int    BACKTEST_DAYS  = 30;
    private static final int    LOAD_DAYS      = 47;
    private static final double MIN_CONFIDENCE = 86.0;
    private static final long   COOLDOWN_MS    = 10L * 60 * 1000;
    private static final int    LOOKAHEAD      = 24;
    private static final Map<String, Double> MIN_POINTS = Map.of(
        "NIFTY50", 28.0, "SENSEX", 65.0
    );

    // ── News sentiment keywords ──────────────────────────────────────────────────
    private static final String[] POSITIVE = {
        "rally", "surge", "gains", "bullish", "breakout", "soar", "jump",
        "record high", "all-time high", "recovery", "rebound", "optimism", "upside"
    };
    private static final String[] NEGATIVE = {
        "crash", "selloff", "tumble", "plunge", "panic", "crisis", "collapse",
        "bearish", "breakdown", "slump", "decline", "drop", "slide",
        "recession", "losses", "fear", "selling pressure", "downside"
    };
    private static final String[] MAJOR_EVENT = {
        "rbi policy", "rbi rate decision", "rbi monetary", "federal reserve",
        "fed rate", "fed decision", "union budget", "interim budget",
        "election result", "gdp data", "cpi inflation", "wpi data",
        "war declaration", "military", "sanctions", "global crisis",
        "circuit breaker", "trading halt", "market suspension"
    };

    // ── Sentiment store (populated at startup) ───────────────────────────────────
    private static final Map<LocalDate, Integer>  newsScoreByDate  = new HashMap<>();
    private static final Map<LocalDate, Boolean>  majorEventByDate = new HashMap<>();

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient   HTTP   = HttpClient.newHttpClient();

    // ── Signal record ────────────────────────────────────────────────────────────
    static class SignalRecord {
        String date, time, symbol, direction, outcome, blockReason;
        double pointsCaptured, confidence, targetPts, slPts;
    }

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    // ════════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) throws Exception {

        line('═', 72);
        System.out.println("  NEW DATA SOURCES — EXPERIMENTAL BACKTEST");
        System.out.println("  1. News Sentiment  (NewsAPI.org)  — 30-day historical backtest");
        System.out.println("  2. GIFT Nifty Gap  (first candle) — 30-day proxy backtest");
        System.out.println("  3. Options PCR     (Upstox live)  — today live snapshot only");
        line('═', 72);
        System.out.println();

        // ── Step 1: Load all news for the 30-day backtest window ─────────────────
        System.out.println("📰 Fetching Indian market news from NewsAPI.org...");
        loadNewsSentiment();

        // ── Step 2: Run backtest — baseline vs filtered ───────────────────────────
        System.out.println("📈 Running 30-day backtest...");
        MarketDataAgent mdAgent   = new MarketDataAgent();
        PredictionAgent predAgent = new PredictionAgent();
        String[]        symbols   = {"NIFTY50", "SENSEX"};

        List<SignalRecord> baseline     = new ArrayList<>();
        List<SignalRecord> withNews     = new ArrayList<>();
        List<SignalRecord> withGift     = new ArrayList<>();
        List<SignalRecord> withBoth     = new ArrayList<>();

        // Per-filter block counters for quality analysis
        Map<String, int[]> newsBlockStats = new LinkedHashMap<>();  // reason → [total, wins, partials]
        Map<String, int[]> giftBlockStats = new LinkedHashMap<>();

        for (String sym : symbols) {
            runBacktest(sym, mdAgent, predAgent, baseline, withNews, withGift, withBoth,
                        newsBlockStats, giftBlockStats);
        }

        // sort by date + time
        Comparator<SignalRecord> byDateTime =
            Comparator.comparing((SignalRecord r) -> r.date).thenComparing(r -> r.time);
        baseline.sort(byDateTime);
        withBoth.sort(byDateTime);

        // ── Step 3: Fetch live PCR from Upstox ────────────────────────────────────
        System.out.println("📊 Fetching live NIFTY50 Options PCR from Upstox...");
        String pcrInfo = fetchLivePCR();

        // ── Step 4: Print full report ─────────────────────────────────────────────
        printReport(baseline, withNews, withGift, withBoth,
                    newsBlockStats, giftBlockStats, pcrInfo);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // BACKTEST CORE
    // ════════════════════════════════════════════════════════════════════════════

    private static void runBacktest(
            String symbol, MarketDataAgent mdAgent, PredictionAgent predAgent,
            List<SignalRecord> baseline, List<SignalRecord> withNews,
            List<SignalRecord> withGift, List<SignalRecord> withBoth,
            Map<String, int[]> newsBlockStats, Map<String, int[]> giftBlockStats) {

        try {
            List<SimpleMarketData> data = mdAgent.getHistoricalData(symbol, LOAD_DAYS);
            if (data == null || data.size() < 200) {
                System.err.println("Not enough data for " + symbol);
                return;
            }

            // Determine backtest start date
            Set<LocalDate> allDatesSet = new LinkedHashSet<>();
            for (SimpleMarketData d : data)
                if (d.timestamp != null) allDatesSet.add(d.timestamp.toLocalDate());
            List<LocalDate> allDates = new ArrayList<>(allDatesSet);
            Collections.sort(allDates);
            LocalDate cutoff = allDates.size() > BACKTEST_DAYS
                ? allDates.get(allDates.size() - BACKTEST_DAYS)
                : allDates.get(0);

            System.out.printf("  %s: %s → %s%n", symbol, cutoff, allDates.get(allDates.size() - 1));

            // Build day-open / prev-close map for GIFT Nifty proxy
            Map<LocalDate, double[]> dayBounds = buildDayBounds(data);

            long lastSignalMs  = 0;
            LocalDate lastSignalDate = null;

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData candle = data.get(i);
                if (candle.timestamp == null) continue;
                LocalDate  date = candle.timestamp.toLocalDate();
                LocalTime  time = candle.timestamp.toLocalTime();
                if (date.isBefore(cutoff)) continue;
                if (time.isBefore(LocalTime.of(9, 15)) || time.isAfter(LocalTime.of(15, 25))) continue;

                if (!date.equals(lastSignalDate)) {
                    lastSignalMs   = 0;
                    lastSignalDate = date;
                }

                long candleMs = candle.timestamp
                    .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
                if (candleMs - lastSignalMs < COOLDOWN_MS) continue;

                List<SimpleMarketData> history = data.subList(0, i + 1);
                AIPredictor.AIPrediction pred = predAgent.generateSignal(symbol, history);
                if (!passesGates(symbol, pred)) continue;

                lastSignalMs = candleMs;
                int outcome = verifyOutcome(data, i, pred);

                // Baseline record
                SignalRecord base = buildRecord(date, time, symbol, pred, candle.price, outcome, null);
                baseline.add(base);

                // ── Filter 1: News Sentiment ──────────────────────────────────────
                String newsBlock = applyNewsFilter(date, pred.predictedDirection);
                if (newsBlock != null) {
                    // blocked by news — record for stats
                    newsBlockStats.computeIfAbsent(newsBlock, k -> new int[3]);
                    int[] s = newsBlockStats.get(newsBlock);
                    s[0]++;
                    if (outcome == FULL_WIN) s[1]++;
                    else if (outcome == PARTIAL_WIN) s[2]++;
                } else {
                    withNews.add(buildRecord(date, time, symbol, pred, candle.price, outcome, null));
                }

                // ── Filter 2: GIFT Nifty gap proxy ───────────────────────────────
                String giftBlock = applyGiftNiftyFilter(date, time, pred.predictedDirection, dayBounds);
                if (giftBlock != null) {
                    giftBlockStats.computeIfAbsent(giftBlock, k -> new int[3]);
                    int[] s = giftBlockStats.get(giftBlock);
                    s[0]++;
                    if (outcome == FULL_WIN) s[1]++;
                    else if (outcome == PARTIAL_WIN) s[2]++;
                } else {
                    withGift.add(buildRecord(date, time, symbol, pred, candle.price, outcome, null));
                }

                // ── Filter 3: BOTH filters applied ───────────────────────────────
                if (newsBlock == null && giftBlock == null) {
                    withBoth.add(buildRecord(date, time, symbol, pred, candle.price, outcome, null));
                }
            }
        } catch (Exception e) {
            System.err.println("Backtest error for " + symbol + ": " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // FILTER 1: NEWS SENTIMENT
    // ════════════════════════════════════════════════════════════════════════════

    private static void loadNewsSentiment() {
        // NewsAPI free tier: last 30 calendar days only
        // 30 trading days ≈ 42 calendar days, so earliest ~12 days will have no news data
        // Those dates default to score=0 (neutral, filter does not apply)
        LocalDate today    = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate newsFrom = today.minusDays(29); // max lookback for free tier
        LocalDate newsTo   = today;

        int fetched = 0, failed = 0;
        for (LocalDate d = newsFrom; !d.isAfter(newsTo); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY)
                continue; // skip weekends
            try {
                Thread.sleep(500); // respect rate limit (100 req/day, ~2 req/sec burst)
                String query   = URLEncoder.encode(
                    "nifty OR sensex OR \"stock market\" OR \"share market\" India",
                    StandardCharsets.UTF_8);
                String dateStr = d.format(DateTimeFormatter.ISO_LOCAL_DATE);
                String url     = NEWS_API_BASE
                    + "?q=" + query
                    + "&language=en"
                    + "&from=" + dateStr + "T00:00:00"
                    + "&to="   + dateStr + "T23:59:59"
                    + "&sortBy=publishedAt"
                    + "&pageSize=100"
                    + "&apiKey=" + NEWS_API_KEY;

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "TradingBot/1.0")
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();

                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) { failed++; continue; }

                JsonNode root = MAPPER.readTree(resp.body());
                if (!"ok".equals(root.path("status").asText())) { failed++; continue; }

                int score      = 0;
                boolean major  = false;

                JsonNode articles = root.path("articles");
                for (JsonNode article : articles) {
                    String title = article.path("title").asText("").toLowerCase();
                    String desc  = article.path("description").asText("").toLowerCase();
                    String text  = title + " " + desc;

                    for (String w : POSITIVE)     if (text.contains(w)) score++;
                    for (String w : NEGATIVE)     if (text.contains(w)) score--;
                    for (String w : MAJOR_EVENT)  if (text.contains(w)) { major = true; score -= 3; }
                }

                newsScoreByDate.put(d, score);
                majorEventByDate.put(d, major);
                fetched++;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                failed++;
            }
        }
        System.out.printf("  ✅ News loaded: %d days fetched, %d failed (no data defaults to neutral)%n",
            fetched, failed);
    }

    /** Returns block reason or null (pass). */
    private static String applyNewsFilter(LocalDate date, String direction) {
        Boolean major = majorEventByDate.getOrDefault(date, false);
        if (major) return "MAJOR_EVENT";

        int score = newsScoreByDate.getOrDefault(date, Integer.MIN_VALUE);
        if (score == Integer.MIN_VALUE) return null; // no news data for this date → neutral

        if (score <= -8) return "CRISIS_NEWS_" + score;
        if (score <= -4 && "UP".equals(direction)) return "BEARISH_NEWS_" + score;
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // FILTER 2: GIFT NIFTY GAP PROXY (first candle open vs prev close)
    // ════════════════════════════════════════════════════════════════════════════

    /** Returns map of date → [dayOpen, prevClose] */
    private static Map<LocalDate, double[]> buildDayBounds(List<SimpleMarketData> data) {
        Map<LocalDate, double[]> result = new HashMap<>();
        LocalDate prev = null;
        double    prevClose = 0;
        LocalDate cur  = null;
        double    dayOpen = 0;

        for (SimpleMarketData d : data) {
            if (d.timestamp == null) continue;
            LocalDate date = d.timestamp.toLocalDate();
            LocalTime time = d.timestamp.toLocalTime();

            if (!date.equals(cur)) {
                if (cur != null && prevClose > 0) {
                    result.put(cur, new double[]{dayOpen, prevClose});
                }
                cur = date;
                dayOpen = d.open > 0 ? d.open : d.price;
                prev = date;
            }
            // track last price of day as close proxy
            if (time.isAfter(LocalTime.of(15, 10))) prevClose = d.price;
        }
        if (cur != null && prevClose > 0) result.put(cur, new double[]{dayOpen, prevClose});
        return result;
    }

    /** Returns block reason or null (pass). */
    private static String applyGiftNiftyFilter(
            LocalDate date, LocalTime time, String direction,
            Map<LocalDate, double[]> dayBounds) {

        // Only applies in first 45 minutes (when GIFT Nifty premium matters most)
        if (time.isAfter(LocalTime.of(10, 0))) return null;

        double[] bounds = dayBounds.get(date);
        if (bounds == null) return null;
        double dayOpen  = bounds[0];
        double prevClose = bounds[1];
        if (prevClose <= 0) return null;

        double gapPct = (dayOpen - prevClose) / prevClose * 100.0;

        // Strong gap-up (>0.8%): institutional bought overnight → skip DOWN signal
        if (gapPct > 0.8 && "DOWN".equals(direction)) return "GIFT_GAPUP_SKIP_DOWN_" + String.format("%.1f%%", gapPct);
        // Strong gap-down (<-0.8%): institutional sold overnight → skip UP signal
        if (gapPct < -0.8 && "UP".equals(direction))  return "GIFT_GAPDOWN_SKIP_UP_" + String.format("%.1f%%", gapPct);
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════════
    // FILTER 3: LIVE PCR (Upstox — snapshot only, not in backtest)
    // ════════════════════════════════════════════════════════════════════════════

    private static String fetchLivePCR() {
        try {
            HonestMarketDataFetcher fetcher = HonestMarketDataFetcher.getInstance();
            String token = HonestMarketDataFetcher.getAccessToken();
            if (token == null || token.isBlank()) return "⚠️  No Upstox token — PCR unavailable";

            // Compute nearest NIFTY expiry (Thursday)
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            LocalDate expiry = today;
            for (int i = 0; i < 7; i++) {
                if (expiry.getDayOfWeek() == DayOfWeek.THURSDAY) break;
                expiry = expiry.plusDays(1);
            }

            String instrumentKey = "NSE_INDEX|Nifty 50";
            String encodedKey    = URLEncoder.encode(instrumentKey, StandardCharsets.UTF_8).replace("+", "%20");
            String url = "https://api.upstox.com/v2/option/chain?instrument_key="
                + encodedKey + "&expiry_date=" + expiry;

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET().build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return "⚠️  Upstox options API returned " + resp.statusCode();

            JsonNode root = MAPPER.readTree(resp.body());
            if (!"success".equals(root.path("status").asText()))
                return "⚠️  Upstox options API: " + root.path("message").asText("unknown error");

            double totalCE = 0, totalPE = 0;
            long   maxCEstrike = 0, maxPEstrike = 0;
            double maxCEoi = 0, maxPEoi = 0;

            JsonNode chain = root.path("data");
            for (JsonNode node : chain) {
                double sp  = node.path("strike_price").asDouble();
                double coi = node.path("call_options").path("market_data").path("oi").asDouble();
                double poi = node.path("put_options").path("market_data").path("oi").asDouble();
                totalCE += coi;
                totalPE += poi;
                if (coi > maxCEoi) { maxCEoi = coi; maxCEstrike = (long) sp; }
                if (poi > maxPEoi) { maxPEoi = poi; maxPEstrike = (long) sp; }
            }

            double pcr = totalCE > 0 ? totalPE / totalCE : 0;
            String sentiment = pcr > 1.3 ? "BULLISH (puts dominate → market expects rise)"
                             : pcr < 0.7 ? "BEARISH (calls dominate → market expects fall)"
                             :             "NEUTRAL";
            String filter = pcr > 1.3 ? "Would reinforce UP signals, block DOWN signals in opening hour"
                          : pcr < 0.7 ? "Would reinforce DOWN signals, block UP signals in opening hour"
                          :             "No filter applied (neutral zone)";

            return String.format(
                "PCR=%.2f [%s]%n" +
                "    Total CE OI: %.0f | Total PE OI: %.0f%n" +
                "    Max CE at: %d (resistance) | Max PE at: %d (support)%n" +
                "    Filter action: %s",
                pcr, sentiment, totalCE, totalPE, maxCEstrike, maxPEstrike, filter);

        } catch (Exception e) {
            return "⚠️  PCR fetch failed: " + e.getMessage();
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // SHARED HELPERS
    // ════════════════════════════════════════════════════════════════════════════

    private static boolean passesGates(String symbol, AIPredictor.AIPrediction p) {
        if ("NEUTRAL".equals(p.predictedDirection)) return false;
        return p.confidence >= MIN_CONFIDENCE
            && p.estimatedMovePoints >= MIN_POINTS.getOrDefault(symbol, 20.0);
    }

    private static int verifyOutcome(List<SimpleMarketData> data, int idx,
                                     AIPredictor.AIPrediction pred) {
        double entry = data.get(idx).price;
        String dir   = pred.predictedDirection;
        double tgtPx = dir.equals("UP") ? entry + pred.estimatedMovePoints : entry - pred.estimatedMovePoints;
        double slPx  = dir.equals("UP") ? entry - pred.suggestedStopLoss   : entry + pred.suggestedStopLoss;
        double halfPx= dir.equals("UP") ? entry + pred.estimatedMovePoints * 0.5
                                        : entry - pred.estimatedMovePoints * 0.5;
        boolean half = false;
        for (int j = 1; j <= LOOKAHEAD && (idx + j) < data.size(); j++) {
            SimpleMarketData c = data.get(idx + j);
            if (dir.equals("UP")) {
                if (c.low  <= slPx)  return half ? PARTIAL_WIN : LOSS;
                if (c.high >= tgtPx) return FULL_WIN;
                if (c.high >= halfPx) half = true;
            } else {
                if (c.high >= slPx)  return half ? PARTIAL_WIN : LOSS;
                if (c.low  <= tgtPx) return FULL_WIN;
                if (c.low  <= halfPx) half = true;
            }
        }
        return half ? PARTIAL_WIN : LOSS;
    }

    private static SignalRecord buildRecord(LocalDate date, LocalTime time, String symbol,
                                            AIPredictor.AIPrediction pred, double entry,
                                            int outcome, String blockReason) {
        double tgtPts = pred.estimatedMovePoints;
        double slPts  = pred.suggestedStopLoss;
        SignalRecord r = new SignalRecord();
        r.date   = date.toString();
        r.time   = time.format(DateTimeFormatter.ofPattern("HH:mm"));
        r.symbol = symbol;
        r.direction = pred.predictedDirection;
        r.confidence = pred.confidence;
        r.targetPts  = tgtPts;
        r.slPts      = slPts;
        r.blockReason = blockReason;
        r.outcome = switch (outcome) {
            case FULL_WIN    -> "WIN";
            case PARTIAL_WIN -> "PARTIAL";
            default          -> "LOSS";
        };
        r.pointsCaptured = switch (outcome) {
            case FULL_WIN    ->  tgtPts;
            case PARTIAL_WIN ->  tgtPts * 0.3;
            default          -> -slPts;
        };
        return r;
    }

    private static double[] calcStats(List<SignalRecord> signals) {
        int total = signals.size(), wins = 0, partial = 0, losses = 0;
        double net = 0;
        for (SignalRecord r : signals) {
            if ("WIN".equals(r.outcome))     wins++;
            else if ("PARTIAL".equals(r.outcome)) partial++;
            else losses++;
            net += r.pointsCaptured;
        }
        double wr = total > 0 ? (wins + partial) * 100.0 / total : 0;
        return new double[]{total, wins, partial, losses, wr, net};
    }

    // ════════════════════════════════════════════════════════════════════════════
    // REPORT
    // ════════════════════════════════════════════════════════════════════════════

    private static void printReport(
            List<SignalRecord> baseline, List<SignalRecord> withNews,
            List<SignalRecord> withGift,  List<SignalRecord> withBoth,
            Map<String, int[]> newsStats, Map<String, int[]> giftStats,
            String pcrInfo) {

        double[] bStats  = calcStats(baseline);
        double[] nStats  = calcStats(withNews);
        double[] gStats  = calcStats(withGift);
        double[] aStats  = calcStats(withBoth);

        line('═', 72);
        System.out.println("  RESULTS — 3 NEW DATA SOURCE FILTERS");
        line('═', 72);

        // ── Comparison table ──────────────────────────────────────────────────
        System.out.printf("%-30s  %6s  %6s  %9s  %12s%n",
            "Strategy", "Calls", "WR%", "WR Change", "Net Points");
        line('-', 72);
        System.out.printf("%-30s  %6.0f  %5.1f%%  %9s  %+12.1f%n",
            "V29.0 Baseline (no filters)",
            bStats[0], bStats[4], "—", bStats[5]);
        System.out.printf("%-30s  %6.0f  %5.1f%%  %+8.1f%%  %+12.1f%n",
            "+ News Sentiment only",
            nStats[0], nStats[4], nStats[4] - bStats[4], nStats[5]);
        System.out.printf("%-30s  %6.0f  %5.1f%%  %+8.1f%%  %+12.1f%n",
            "+ GIFT Nifty gap only",
            gStats[0], gStats[4], gStats[4] - bStats[4], gStats[5]);
        System.out.printf("%-30s  %6.0f  %5.1f%%  %+8.1f%%  %+12.1f%n",
            "+ Both filters together",
            aStats[0], aStats[4], aStats[4] - bStats[4], aStats[5]);
        line('═', 72);

        // ── News filter quality ───────────────────────────────────────────────
        System.out.println();
        System.out.println("  NEWS SENTIMENT — FILTER QUALITY (what did it block?)");
        line('-', 68);
        System.out.printf("  %-30s  %5s  %5s  %7s  %6s  %s%n",
            "Block Reason", "Total", "Wins", "Partial", "WR%", "Verdict");
        line('-', 68);
        int newsBlocked = 0;
        for (var entry : newsStats.entrySet()) {
            int[] s  = entry.getValue();
            int total = s[0]; newsBlocked += total;
            double wr  = total > 0 ? (s[1] + s[2]) * 100.0 / total : 0;
            String verdict = wr < 40 ? "✅ GREAT" : wr < 55 ? "🟡 OK" : "❌ HURTS";
            System.out.printf("  %-30s  %5d  %5d  %7d  %5.0f%%  %s%n",
                entry.getKey(), total, s[1], s[2], wr, verdict);
        }
        if (newsBlocked == 0) System.out.println("  (no signals blocked by news filter)");

        // ── GIFT Nifty filter quality ─────────────────────────────────────────
        System.out.println();
        System.out.println("  GIFT NIFTY GAP — FILTER QUALITY (what did it block?)");
        line('-', 68);
        System.out.printf("  %-30s  %5s  %5s  %7s  %6s  %s%n",
            "Block Reason", "Total", "Wins", "Partial", "WR%", "Verdict");
        line('-', 68);
        int giftBlocked = 0;
        for (var entry : giftStats.entrySet()) {
            int[] s  = entry.getValue();
            int total = s[0]; giftBlocked += total;
            double wr  = total > 0 ? (s[1] + s[2]) * 100.0 / total : 0;
            String verdict = wr < 40 ? "✅ GREAT" : wr < 55 ? "🟡 OK" : "❌ HURTS";
            System.out.printf("  %-30s  %5d  %5d  %7d  %5.0f%%  %s%n",
                entry.getKey(), total, s[1], s[2], wr, verdict);
        }
        if (giftBlocked == 0) System.out.println("  (no signals blocked by GIFT Nifty filter)");

        // ── Live PCR snapshot ─────────────────────────────────────────────────
        System.out.println();
        line('═', 72);
        System.out.println("  LIVE OPTIONS PCR SNAPSHOT (not in backtest — no historical OI free)");
        line('─', 72);
        System.out.println("  " + pcrInfo.replace("\n", "\n  "));
        line('═', 72);

        // ── Final verdict ─────────────────────────────────────────────────────
        System.out.println();
        line('═', 72);
        System.out.println("  FINAL VERDICT");
        line('─', 72);

        double wrDelta = aStats[4] - bStats[4];
        if (wrDelta >= 1.5) {
            System.out.printf("  ✅ IMPLEMENT: Both filters improve WR by +%.1f%% → add to main code%n", wrDelta);
        } else if (wrDelta >= 0.0) {
            System.out.printf("  🟡 MARGINAL: WR change is +%.1f%% — probably noise, not worth adding%n", wrDelta);
        } else {
            System.out.printf("  ❌ DO NOT IMPLEMENT: Filters made WR %.1f%% worse%n", wrDelta);
        }
        System.out.printf("  Calls removed: %d (from %d → %d)%n",
            (int)(bStats[0] - aStats[0]), (int)bStats[0], (int)aStats[0]);
        System.out.printf("  Net points change: %+.0f pts/month%n", aStats[5] - bStats[5]);
        line('═', 72);
    }

    private static void line(char c, int n) {
        System.out.println(String.valueOf(c).repeat(n));
    }
}
