package com.trading.bot;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.telegram.Phase3TelegramBot;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cloud Launcher — Telegram Bot only.
 * Starts a minimal health-check HTTP server (required by Render/Koyeb/Heroku)
 * and launches the Phase 3 Telegram trading bot.
 */
public class CloudLauncher {

    public static void main(String[] args) throws IOException {
        System.out.println("🚀 STARTING TELEGRAM TRADING BOT...");

        // 1. Minimal health-check server (required for cloud hosting keep-alive)
        String portStr = System.getenv("PORT");
        int port = (portStr != null) ? Integer.parseInt(portStr) : 8080;

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new HealthHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.println("🌍 Health-check server started on port " + port);

            // Keep-alive pinger to prevent free-tier spin-down (every 14 min)
            startKeepAlivePinger(port);

        } catch (Exception e) {
            System.err.println("⚠️ Could not start health server: " + e.getMessage());
        }

        // 2. Start the Telegram bot
        System.out.println("🤖 Initialising Phase 3 Telegram Bot...");
        Phase3TelegramBot bot = new Phase3TelegramBot();
        bot.startBot();
        System.out.println("✅ Bot is running. Send /start in Telegram to begin.");

        // Block main thread — bot polling runs on its own scheduler
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ie) {
            System.out.println("🛑 Shutting down.");
        }
    }

    private static void startKeepAlivePinger(int port) {
        String appUrl = System.getenv("RENDER_EXTERNAL_URL");
        if (appUrl == null) appUrl = "http://localhost:" + port;

        final String targetUrl = appUrl;
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                URL url = new URL(targetUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) { }
        }, 5, 14 * 60, TimeUnit.SECONDS);
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "Telegram Trading Bot is Running.";
            if ("HEAD".equalsIgnoreCase(t.getRequestMethod())) {
                t.sendResponseHeaders(200, -1);
            } else {
                byte[] bytes = response.getBytes();
                t.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = t.getResponseBody()) { os.write(bytes); }
            }
        }
    }
}
