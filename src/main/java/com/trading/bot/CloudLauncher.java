package com.trading.bot;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
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
 * Cloud Launcher for PaaS (Render, Koyeb, Heroku)
 * Starts a dummy HTTP server to satisfy port binding requirements
 * and then launches the main Telegram Bot.
 * Includes a Self-Ping Keep-Alive mechanism to prevent Free Tier spin-down.
 */
public class CloudLauncher {

    public static void main(String[] args) throws IOException {
        System.out.println("🚀 PREPARING CLOUD DEPLOYMENT...");

        // 1. Start Health Check Server (Required for Render/Koyeb/Heroku)
        // These platforms pass the port to listen on via the PORT env var
        String portStr = System.getenv("PORT");
        int port = (portStr != null) ? Integer.parseInt(portStr) : 8080;
        
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new HealthHandler());
            server.setExecutor(null); // creates a default executor
            server.start();
            System.out.println("🌍 Cloud Health Server started on port " + port);
            
            // Start Keep-Alive Pinger (Every 14 minutes)
            startKeepAlivePinger(port);
            
        } catch (Exception e) {
            System.err.println("⚠️ Could not start HTTP server (might be local test): " + e.getMessage());
        }
        
        // 2. Start the Phase 3 Telegram Bot
        System.out.println("🤖 Launching Phase 3 Telegram Bot Logic...");
        
        // We call the bot's main method. 
        // Note: Phase3TelegramBot.main() calls join(), so this call will block here forever, which is what we want.
        Phase3TelegramBot.main(args);
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
        }, 5, 14 * 60, TimeUnit.SECONDS); // Initial delay 5s, repeat every 14 mins (840s)
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "Bot is Running! Upstox Integration Active.";
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
