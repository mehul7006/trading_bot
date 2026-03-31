package com.trading.bot.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.technical.AdvancedIndicatorsEngine;
import com.trading.bot.telegram.Phase3TelegramBot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * REST API Server for the Android Trading App.
 * Registers handlers on the existing HttpServer instance.
 * All endpoints return JSON with CORS headers.
 */
public class TradingApiServer {

    private final ObjectMapper mapper;
    private final Phase3TelegramBot bot;
    private final HonestMarketDataFetcher fetcher;

    // -----------------------------------------------------------------------
    // Signal History Store
    // -----------------------------------------------------------------------

    public static class SignalHistoryStore {
        private static final Deque<Map<String, Object>> history = new ConcurrentLinkedDeque<>();

        public static synchronized void add(Map<String, Object> entry) {
            history.addFirst(entry);
            while (history.size() > 50) {
                history.removeLast();
            }
        }

        public static synchronized List<Map<String, Object>> get() {
            return new ArrayList<>(history);
        }

        public static synchronized List<Map<String, Object>> getLast(int n) {
            List<Map<String, Object>> all = get();
            return all.subList(0, Math.min(n, all.size()));
        }
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public TradingApiServer(HttpServer server, Phase3TelegramBot bot, HonestMarketDataFetcher fetcher) {
        this.bot = bot;
        this.fetcher = fetcher;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        registerHandlers(server);
        System.out.println("[TradingApiServer] REST API endpoints registered.");
    }

    // -----------------------------------------------------------------------
    // Handler Registration
    // -----------------------------------------------------------------------

    private void registerHandlers(HttpServer server) {
        server.createContext("/api/status",      new StatusHandler());
        server.createContext("/api/signals",     new SignalsHandler());
        server.createContext("/api/marketdata",  new MarketDataHandler());
        server.createContext("/api/snapshot",    new SnapshotHandler());
        server.createContext("/api/history",     new HistoryHandler());
        server.createContext("/api/indicators",  new IndicatorsHandler());
        server.createContext("/api/settings",    new SettingsHandler());
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"error\":\"serialization error\"}").getBytes(StandardCharsets.UTF_8);
            status = 500;
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(204, -1);
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }

    // -----------------------------------------------------------------------
    // GET /api/status
    // -----------------------------------------------------------------------

    class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { handleCors(exchange); return; }
            try {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("botRunning", true);
                resp.put("isScanning", bot.isScanning());
                resp.put("todayCallsGenerated", bot.getTodayCallsGenerated());
                resp.put("activeSignalsCount", bot.getActiveSignals().size());
                resp.put("serverTime", java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).toString());
                resp.put("version", "V29.0");

                // Simple win rate from history
                List<Map<String, Object>> hist = SignalHistoryStore.getLast(20);
                long wins = hist.stream().filter(m -> "WIN".equals(m.get("status"))).count();
                long losses = hist.stream().filter(m -> "LOSS".equals(m.get("status"))).count();
                long total = wins + losses;
                double winRate = total > 0 ? (wins * 100.0 / total) : 0.0;
                resp.put("winRate", Math.round(winRate * 10.0) / 10.0);
                resp.put("todayWins", wins);
                resp.put("todayLosses", losses);

                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    // -----------------------------------------------------------------------
    // GET /api/signals
    // -----------------------------------------------------------------------

    class SignalsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { handleCors(exchange); return; }
            try {
                Map<String, Phase3TelegramBot.ActiveSignal> raw = bot.getActiveSignals();
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map.Entry<String, Phase3TelegramBot.ActiveSignal> entry : raw.entrySet()) {
                    Phase3TelegramBot.ActiveSignal s = entry.getValue();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("symbol", s.symbol);
                    m.put("direction", s.direction);
                    m.put("entryPrice", s.entryPrice);
                    m.put("targetPoints", s.targetPoints);
                    m.put("stopLossPoints", s.stopLossPoints);
                    m.put("createdAt", s.createdAt);
                    m.put("status", "OPEN");
                    long elapsedMin = (System.currentTimeMillis() - s.createdAt) / 60000;
                    m.put("elapsedMinutes", elapsedMin);
                    double targetPrice = "UP".equalsIgnoreCase(s.direction)
                            ? s.entryPrice + s.targetPoints
                            : s.entryPrice - s.targetPoints;
                    double slPrice = "UP".equalsIgnoreCase(s.direction)
                            ? s.entryPrice - s.stopLossPoints
                            : s.entryPrice + s.stopLossPoints;
                    m.put("targetPrice", targetPrice);
                    m.put("stopLossPrice", slPrice);
                    result.add(m);
                }
                sendJson(exchange, 200, result);
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    // -----------------------------------------------------------------------
    // GET /api/marketdata?symbol=NIFTY50
    // -----------------------------------------------------------------------

    class MarketDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { handleCors(exchange); return; }
            try {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                String symbol = params.getOrDefault("symbol", "NIFTY50").toUpperCase();

                List<SimpleMarketData> data = fetcher.getRealMarketData5Min(symbol);
                int fromIdx = Math.max(0, data.size() - 100);
                List<SimpleMarketData> slice = data.subList(fromIdx, data.size());

                List<Map<String, Object>> candles = new ArrayList<>();
                for (SimpleMarketData d : slice) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("timestamp", d.timestamp != null ? d.timestamp.toString() : "");
                    c.put("open", d.open);
                    c.put("high", d.high);
                    c.put("low", d.low);
                    c.put("close", d.price);
                    c.put("volume", d.volume);
                    candles.add(c);
                }

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("symbol", symbol);
                resp.put("timeframe", "5MIN");
                resp.put("count", candles.size());
                resp.put("candles", candles);
                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    // -----------------------------------------------------------------------
    // GET /api/snapshot
    // -----------------------------------------------------------------------

    class SnapshotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { handleCors(exchange); return; }
            try {
                Map<String, Double> prices = fetcher.getHonestMarketSnapshot();
                List<Map<String, Object>> result = new ArrayList<>();
                String[] symbols = {"NIFTY50", "BANKNIFTY", "SENSEX"};
                for (String sym : symbols) {
                    double price = prices.getOrDefault(sym, 0.0);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("symbol", sym);
                    m.put("price", price);
                    // Direction based on active signal if available
                    Map<String, Phase3TelegramBot.ActiveSignal> sigs = bot.getActiveSignals();
                    Phase3TelegramBot.ActiveSignal sig = sigs.get(sym);
                    m.put("direction", sig != null ? sig.direction : "NEUTRAL");
                    m.put("change", 0.0);       // placeholder — real change needs prev close
                    m.put("changePercent", 0.0);
                    result.add(m);
                }
                sendJson(exchange, 200, result);
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    // -----------------------------------------------------------------------
    // GET /api/history
    // -----------------------------------------------------------------------

    class HistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { handleCors(exchange); return; }
            try {
                List<Map<String, Object>> hist = SignalHistoryStore.getLast(20);
                sendJson(exchange, 200, hist);
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }
    }

    // -----------------------------------------------------------------------
    // GET /api/indicators?symbol=NIFTY50
    // -----------------------------------------------------------------------

    class IndicatorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { handleCors(exchange); return; }
            try {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                String symbol = params.getOrDefault("symbol", "NIFTY50").toUpperCase();

                List<SimpleMarketData> data = fetcher.getRealMarketData5Min(symbol);

                AdvancedIndicatorsEngine engine = new AdvancedIndicatorsEngine();
                AdvancedIndicatorsEngine.AdvancedIndicatorsResult result = engine.analyze50Plus(data);

                // Compute VWAP manually from the data
                double vwap = computeVwap(data);

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("symbol", symbol);
                resp.put("rsi", result.values.getOrDefault("rsi", 50.0));
                resp.put("adx", result.adx != null ? result.adx.adx : result.values.getOrDefault("adx", 25.0));
                resp.put("macd", result.values.getOrDefault("macd", 0.0));
                resp.put("macdSignal", result.values.getOrDefault("macdSignal", 0.0));
                resp.put("macdHist", result.values.getOrDefault("macdHist", 0.0));
                resp.put("vwap", vwap);
                resp.put("ema20", result.values.getOrDefault("ema20", 0.0));
                resp.put("atr", result.values.getOrDefault("atr", 0.0));
                resp.put("stochK", result.stochastic != null ? result.stochastic.percentK : result.values.getOrDefault("stochK", 50.0));
                resp.put("stochD", result.stochastic != null ? result.stochastic.percentD : result.values.getOrDefault("stochD", 50.0));
                resp.put("williamsR", result.williamsR != null ? result.williamsR.williamsR : result.values.getOrDefault("williamsR", -50.0));
                resp.put("overallSignal", result.overallSignal);
                resp.put("confluenceScore", result.confluenceScore);
                resp.put("reasoning", result.reasoning);
                resp.put("allValues", result.values);

                sendJson(exchange, 200, resp);
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }

        private double computeVwap(List<SimpleMarketData> data) {
            double tpv = 0, vol = 0;
            for (SimpleMarketData d : data) {
                double tp = (d.high + d.low + d.price) / 3.0;
                tpv += tp * d.volume;
                vol += d.volume;
            }
            return vol > 0 ? tpv / vol : 0;
        }
    }

    // -----------------------------------------------------------------------
    // POST /api/settings  (for Android settings screen)
    // -----------------------------------------------------------------------

    class SettingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) { handleCors(exchange); return; }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    byte[] body = exchange.getRequestBody().readAllBytes();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> settings = mapper.readValue(body, Map.class);

                    // Apply token update if provided
                    Object token = settings.get("upstoxToken");
                    if (token instanceof String t && !t.isBlank()) {
                        HonestMarketDataFetcher.setAccessToken(t.trim());
                    }

                    sendJson(exchange, 200, Map.of("success", true, "message", "Settings applied"));
                } catch (Exception e) {
                    sendJson(exchange, 500, Map.of("error", e.getMessage()));
                }
            } else {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            }
        }
    }
}
