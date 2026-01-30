package com.trading.bot.market;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.*;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class EnhancedMarketDataFeed extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedMarketDataFeed.class);
    
    private final ObjectMapper objectMapper;
    private final BlockingQueue<MarketDataEvent> dataQueue;
    private final Map<String, CompletableFuture<Void>> subscriptions;
    private final AtomicBoolean isReconnecting;
    private final ScheduledExecutorService heartbeatScheduler;
    private final RealTimeDataCollector dataCollector;
    
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int HEARTBEAT_INTERVAL_MS = 30000;
    
    public EnhancedMarketDataFeed(String serverUrl, RealTimeDataCollector dataCollector) {
        super(URI.create(serverUrl));
        this.objectMapper = new ObjectMapper();
        this.dataQueue = new LinkedBlockingQueue<>();
        this.subscriptions = new ConcurrentHashMap<>();
        this.isReconnecting = new AtomicBoolean(false);
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        this.dataCollector = dataCollector;
        
        configureWebSocket();
    }
    
    private void configureWebSocket() {
        this.setConnectionLostTimeout(10);
        startHeartbeat();
    }
    
    @Override
    public void onOpen(ServerHandshake handshake) {
        logger.info("Market data feed connection established");
        isReconnecting.set(false);
        resubscribeAll();
    }
    
    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            
            // Handle different message types
            if (root.has("type")) {
                String type = root.get("type").asText();
                switch (type) {
                    case "marketData":
                        handleMarketData(root);
                        break;
                    case "heartbeat":
                        handleHeartbeat();
                        break;
                    case "error":
                        handleError(root);
                        break;
                    case "subscription":
                        handleSubscriptionResponse(root);
                        break;
                }
            }
        } catch (Exception e) {
            logger.error("Error processing market data message: {}", e.getMessage());
        }
    }
    
    private void handleMarketData(JsonNode data) {
        try {
            String symbol = data.get("symbol").asText();
            double price = data.get("price").asDouble();
            double volume = data.get("volume").asDouble();
            double vwap = data.get("vwap").asDouble(0.0);
            double volatility = calculateVolatility(data);
            
            MarketDataEvent event = new MarketDataEvent(
                symbol,
                price,
                volume,
                vwap,
                volatility
            );
            
            dataQueue.offer(event);
            dataCollector.updateMarketData(event);
            
        } catch (Exception e) {
            logger.error("Error processing market data: {}", e.getMessage());
        }
    }
    
    private double calculateVolatility(JsonNode data) {
        try {
            if (data.has("volatility")) {
                return data.get("volatility").asDouble();
            }
            
            // Calculate if not provided
            if (data.has("high") && data.has("low")) {
                double high = data.get("high").asDouble();
                double low = data.get("low").asDouble();
                return ((high - low) / low) * 100;
            }
        } catch (Exception e) {
            logger.warn("Error calculating volatility: {}", e.getMessage());
        }
        return 0.0;
    }
    
    public void subscribe(String symbol) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        subscriptions.put(symbol, future);
        
        if (isOpen()) {
            sendSubscription(symbol);
        }
    }
    
    private void sendSubscription(String symbol) {
        try {
            Map<String, Object> subscription = new HashMap<>();
            subscription.put("type", "subscribe");
            subscription.put("symbol", symbol);
            subscription.put("interval", "1m"); // 1-minute candles
            
            String message = objectMapper.writeValueAsString(subscription);
            send(message);
            
        } catch (Exception e) {
            logger.error("Error sending subscription for {}: {}", symbol, e.getMessage());
            CompletableFuture<Void> future = subscriptions.get(symbol);
            if (future != null) {
                future.completeExceptionally(e);
            }
        }
    }
    
    private void resubscribeAll() {
        subscriptions.keySet().forEach(this::sendSubscription);
    }
    
    private void handleSubscriptionResponse(JsonNode response) {
        String symbol = response.get("symbol").asText();
        boolean success = response.get("success").asBoolean();
        
        CompletableFuture<Void> future = subscriptions.get(symbol);
        if (future != null) {
            if (success) {
                future.complete(null);
                logger.info("Successfully subscribed to {}", symbol);
            } else {
                String error = response.has("error") ? response.get("error").asText() : "Unknown error";
                future.completeExceptionally(new RuntimeException(error));
                logger.error("Failed to subscribe to {}: {}", symbol, error);
            }
        }
    }
    
    private void handleError(JsonNode error) {
        String errorMessage = error.get("message").asText();
        logger.error("Received error from market data feed: {}", errorMessage);
        
        if (error.has("code")) {
            int code = error.get("code").asInt();
            if (code >= 5000) { // Server-side errors
                reconnect();
            }
        }
    }
    
    private void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (isOpen()) {
                try {
                    send("{\"type\":\"heartbeat\"}");
                } catch (Exception e) {
                    logger.warn("Failed to send heartbeat: {}", e.getMessage());
                }
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
    
    private void handleHeartbeat() {
        // Reset any connection monitoring here
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.warn("Market data feed connection closed: {}. Remote: {}", reason, remote);
        if (!isReconnecting.get()) {
            reconnect();
        }
    }
    
    @Override
    public void onError(Exception ex) {
        logger.error("Market data feed error: {}", ex.getMessage());
        if (ex.getMessage().contains("Connection refused") || 
            ex.getMessage().contains("Connection reset")) {
            reconnect();
        }
    }
    
    private void reconnect() {
        if (isReconnecting.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                    logger.info("Attempting to reconnect to market data feed...");
                    reconnectBlocking();
                } catch (Exception e) {
                    logger.error("Failed to reconnect: {}", e.getMessage());
                    isReconnecting.set(false);
                }
            });
        }
    }
    
    public MarketDataEvent getNextUpdate() throws InterruptedException {
        return dataQueue.take();
    }
    
    public void shutdown() {
        heartbeatScheduler.shutdown();
        try {
            if (!heartbeatScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatScheduler.shutdownNow();
        }
        close();
    }
}