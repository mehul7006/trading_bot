package com.trading.bot.market;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real-time market data collector
 */
public class RealTimeDataCollector {
    private static final Logger logger = LoggerFactory.getLogger(RealTimeDataCollector.class);
    
    private final BlockingQueue<MarketDataEvent> dataQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread collectorThread;
    
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            collectorThread = new Thread(this::collectData);
            collectorThread.start();
            logger.info("Real-time data collector started");
        }
    }
    
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            if (collectorThread != null) {
                collectorThread.interrupt();
            }
            logger.info("Real-time data collector stopped");
        }
    }
    
    private void collectData() {
        while (isRunning.get()) {
            try {
                // Simulate data collection
                MarketDataEvent event = new MarketDataEvent("NIFTY", 18500.0, 18505.0, 18495.0, System.currentTimeMillis());
                dataQueue.offer(event);
                Thread.sleep(1000); // Collect every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error collecting market data: {}", e.getMessage());
            }
        }
    }
    
    public MarketDataEvent getLatestData() {
        return dataQueue.poll();
    }
    
    public boolean isRunning() {
        return isRunning.get();
    }
}