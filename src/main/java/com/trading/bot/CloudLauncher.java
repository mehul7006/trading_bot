package com.trading.bot;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.trading.bot.api.TradingApiServer;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.telegram.Phase3TelegramBot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cloud Launcher for PaaS (Render, Koyeb, Heroku).
 * Starts the HTTP server, registers REST API endpoints, then launches the Telegram bot.
 * Includes a Self-Ping Keep-Alive mechanism to prevent Free Tier spin-down.
 */
public class CloudLauncher {

    public static void main(String[] args) throws IOException {
        System.out.println("🚀 PREPARING CLOUD DEPLOYMENT...");

        // 1. Start Health Check Server (Required for Render/Koyeb/Heroku)
        String portStr = System.getenv("PORT");
        int port = (portStr != null) ? Integer.parseInt(portStr) : 8080;

        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/app", new StaticFileHandler());
            server.createContext("/", new HealthHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.println("🌍 Cloud Health Server started on port " + port);
            System.out.println("🌐 Web Dashboard available at /app");

            // Start Keep-Alive Pinger (Every 14 minutes)
            startKeepAlivePinger(port);

        } catch (Exception e) {
            System.err.println("⚠️ Could not start HTTP server (might be local test): " + e.getMessage());
        }

        // 2. Create Phase 3 Telegram Bot instance
        System.out.println("🤖 Creating Phase 3 Telegram Bot...");
        Phase3TelegramBot bot = new Phase3TelegramBot();

        // 3. Register REST API endpoints on the existing server
        if (server != null) {
            try {
                HonestMarketDataFetcher fetcher = HonestMarketDataFetcher.getInstance();
                new TradingApiServer(server, bot, fetcher);
                System.out.println("✅ REST API endpoints registered at /api/*");
            } catch (Exception e) {
                System.err.println("⚠️ Could not register API endpoints: " + e.getMessage());
            }
        }

        // 4. Start the bot and block until terminated
        System.out.println("🚀 Starting bot polling...");
        bot.startBot();

        // Keep the main thread alive (bot polling runs on scheduled executor)
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ie) {
            System.out.println("🛑 Main thread interrupted, shutting down.");
        }
    }

    private static void startKeepAlivePinger(int port) {
        String appUrl = System.getenv("RENDER_EXTERNAL_URL");
        if (appUrl == null) {
            appUrl = "http://localhost:" + port;
        }

        final String targetUrl = appUrl;
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        System.out.println("⏰ Starting Keep-Alive Pinger for: " + targetUrl);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                URL url = new URL(targetUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                int responseCode = connection.getResponseCode();
                System.out.println("💓 Keep-Alive Ping: " + responseCode + " | URL: " + targetUrl);
                connection.disconnect();
            } catch (Exception e) {
                System.err.println("⚠️ Keep-Alive Ping Failed: " + e.getMessage());
            }
        }, 5, 14 * 60, TimeUnit.SECONDS);
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "Bot is Running! Upstox Integration Active. REST API at /api/* | Web UI at /app";

            if ("HEAD".equalsIgnoreCase(t.getRequestMethod())) {
                t.sendResponseHeaders(200, -1);
            } else {
                byte[] bytes = response.getBytes();
                t.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = t.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }
    }

    /**
     * Serves the React web-app build (web-app/dist/) at /app.
     * Files are loaded from the classpath (packed into JAR as static/).
     * Falls back to index.html for client-side routing (SPA mode).
     */
    static class StaticFileHandler implements HttpHandler {
        private static final Map<String, String> MIME = new HashMap<>(Map.of(
            "html", "text/html; charset=utf-8",
            "js",   "application/javascript",
            "css",  "text/css",
            "png",  "image/png",
            "svg",  "image/svg+xml",
            "ico",  "image/x-icon",
            "json", "application/json",
            "woff2","font/woff2",
            "woff", "font/woff"
        ));

        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(t.getRequestMethod())) {
                t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                t.sendResponseHeaders(204, -1);
                return;
            }

            String uri = t.getRequestURI().getPath();
            // Strip /app prefix
            String file = uri.startsWith("/app") ? uri.substring(4) : uri;
            if (file.isEmpty() || file.equals("/")) file = "/index.html";

            // Attempt to load from classpath (static/ folder packed in JAR)
            String resource = "static" + file;
            InputStream in = StaticFileHandler.class.getClassLoader().getResourceAsStream(resource);

            // SPA fallback: unknown paths → index.html
            if (in == null) {
                in = StaticFileHandler.class.getClassLoader().getResourceAsStream("static/index.html");
                file = "/index.html";
            }

            if (in == null) {
                String err = "Web UI not found. Run: cd web-app && npm run build, then redeploy.";
                t.sendResponseHeaders(404, err.length());
                try (OutputStream os = t.getResponseBody()) { os.write(err.getBytes()); }
                return;
            }

            String ext = file.contains(".") ? file.substring(file.lastIndexOf('.') + 1) : "html";
            String mime = MIME.getOrDefault(ext, "application/octet-stream");

            byte[] bytes = in.readAllBytes();
            in.close();

            t.getResponseHeaders().add("Content-Type", mime);
            t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            if (!ext.equals("html")) {
                t.getResponseHeaders().add("Cache-Control", "public, max-age=31536000, immutable");
            }
            t.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = t.getResponseBody()) { os.write(bytes); }
        }
    }
}
