package com.trading.bot.core;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trading.bot.ml.*;
import com.trading.bot.options.*;
import com.trading.bot.market.*;
import com.trading.bot.utils.*;

public class BotLauncher {
    private static final Logger logger = LoggerFactory.getLogger(BotLauncher.class);
    private final ConfigurationManager config;
    private final RealTimeMarketDataFeed dataFeed;
    private final MLMarketAnalyzer mlAnalyzer;
    private final EnhancedOptionsCallGenerator optionsGenerator;
    private final HonestBotTester botTester;
    
    public BotLauncher() {
        this.config = new ConfigurationManager();
        this.dataFeed = new RealTimeMarketDataFeed(config.getString("market.websocket.url", "wss://market-data.example.com"));
        this.mlAnalyzer = new MLMarketAnalyzer();
        this.optionsGenerator = new EnhancedOptionsCallGenerator();
        this.botTester = new HonestBotTester();
    }
    
    public void start() {
        try {
            logger.info("Starting ML-Enhanced Trading Bot...");
            
            // Connect to market data feed
            dataFeed.connectBlocking();
            
            if (!dataFeed.isConnected()) {
                throw new RuntimeException("Failed to connect to market data feed");
            }
            
            logger.info("Market data feed connected successfully");
            
            // Start processing market data
            processMarketData();
            
        } catch (Exception e) {
            logger.error("Error starting bot: " + e.getMessage());
            shutdown();
        }
    }
    
    private void processMarketData() {
        while (dataFeed.isConnected()) {
            try {
                MarketDataEvent event = dataFeed.getNextUpdate();
                
                // Analyze market data using ML
                MLMarketAnalyzer.MarketPrediction prediction = 
                    mlAnalyzer.analyzeMarket(event.getSymbol());
                
                if (prediction != null && prediction.confidence >= config.getDouble("ml.confidence.threshold", 85.0)) {
                    // Generate options signals
                    EnhancedOptionsCallGenerator.OptionTradeSignal callSignal =
                        optionsGenerator.generateOptionsCall(
                            event.getSymbol(),
                            EnhancedOptionsCallGenerator.OptionType.CALL
                        );
                    
                    if (callSignal != null) {
                        logger.info("Generated high-confidence call signal: " + callSignal);
                    }
                    
                    EnhancedOptionsCallGenerator.OptionTradeSignal putSignal =
                        optionsGenerator.generateOptionsCall(
                            event.getSymbol(),
                            EnhancedOptionsCallGenerator.OptionType.PUT
                        );
                    
                    if (putSignal != null) {
                        logger.info("Generated high-confidence put signal: " + putSignal);
                    }
                }
                
            } catch (InterruptedException e) {
                logger.warn("Market data processing interrupted");
                break;
            } catch (Exception e) {
                logger.error("Error processing market data: " + e.getMessage());
            }
        }
    }
    
    public void shutdown() {
        try {
            logger.info("Shutting down bot...");
            dataFeed.close();
        } catch (Exception e) {
            logger.error("Error during shutdown: " + e.getMessage());
        }
    }
    
    public void runHonestTest() {
        try {
            logger.info("Running honest bot test...");
            botTester.runHonestTest();
        } catch (Exception e) {
            logger.error("Error during honest testing: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        BotLauncher bot = new BotLauncher();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(bot::shutdown));
        
        if (args.length > 0 && args[0].equals("--test")) {
            bot.runHonestTest();
        } else {
            bot.start();
        }
    }
}