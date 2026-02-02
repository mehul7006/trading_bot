package com.trading.bot;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.trading.bot.telegram.Phase3TelegramBot;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Cloud Launcher for PaaS (Render, Koyeb, Heroku)
 * Starts a dummy HTTP server to satisfy port binding requirements
 * and then launches the main Telegram Bot.
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
        } catch (Exception e) {
            System.err.println("⚠️ Could not start HTTP server (might be local test): " + e.getMessage());
        }
        
        // 2. Start the Phase 3 Telegram Bot
        System.out.println("🤖 Launching Phase 3 Telegram Bot Logic...");
        
        // We call the bot's main method. 
        // Note: Phase3TelegramBot.main() calls join(), so this call will block here forever, which is what we want.
        Phase3TelegramBot.main(args);
    }
    
    /**
     * Responds with 200 OK to any request.
     * Used by UptimeRobot or platform health checks to keep the bot alive.
     */
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
