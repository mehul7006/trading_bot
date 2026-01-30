package com.trading.bot.market;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RealTimeMarketDataFeed extends WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(RealTimeMarketDataFeed.class);
    private final ObjectMapper objectMapper;
    private final BlockingQueue<MarketDataEvent> dataQueue;
    private volatile boolean isConnected = false;

    public RealTimeMarketDataFeed(String serverUrl) {
        super(URI.create(serverUrl));
        this.objectMapper = new ObjectMapper();
        this.dataQueue = new LinkedBlockingQueue<>();
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        logger.info("Market data feed connection established");
        isConnected = true;
    }

    @Override
    public void onMessage(String message) {
        try {
            MarketDataEvent event = objectMapper.readValue(message, MarketDataEvent.class);
            dataQueue.offer(event);
        } catch (Exception e) {
            logger.error("Error processing market data message: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("Market data feed connection closed: " + reason);
        isConnected = false;
    }

    @Override
    public void onError(Exception ex) {
        logger.error("Market data feed error: " + ex.getMessage());
    }

    public MarketDataEvent getNextUpdate() throws InterruptedException {
        return dataQueue.take();
    }

    public boolean isConnected() {
        return isConnected;
    }

    public static class MarketDataEvent {
        private String symbol;
        private double price;
        private double volume;
        private long timestamp;
        private String eventType;

        // Getters and setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public double getVolume() { return volume; }
        public void setVolume(double volume) { this.volume = volume; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
    }
}