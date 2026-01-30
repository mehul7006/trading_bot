package com.trading.bot.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

public class ConfigurationManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationManager.class);
    private static final String CONFIG_FILE = "config/bot-config.json";
    private final ObjectMapper mapper;
    private Map<String, Object> configuration;

    public ConfigurationManager() {
        this.mapper = new ObjectMapper();
        this.configuration = new HashMap<>();
        loadConfiguration();
    }

    private void loadConfiguration() {
        try {
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                configuration = mapper.readValue(configFile, Map.class);
                logger.info("Configuration loaded successfully");
            } else {
                createDefaultConfiguration();
            }
        } catch (IOException e) {
            logger.error("Error loading configuration: " + e.getMessage());
            createDefaultConfiguration();
        }
    }

    private void createDefaultConfiguration() {
        configuration = new HashMap<>();
        // Market data feed settings
        configuration.put("marketDataUrl", "wss://your-market-data-provider.com/feed");
        configuration.put("apiKey", "YOUR_API_KEY");
        configuration.put("refreshInterval", 1000);
        
        // ML model settings
        configuration.put("mlConfidenceThreshold", 88.0);
        configuration.put("minDataPoints", 100);
        configuration.put("trainingDays", 60);
        
        // Options trading settings
        configuration.put("maxPositionSize", 100000.0);
        configuration.put("maxLossPerTrade", 1000.0);
        configuration.put("profitTarget", 2.0);
        
        saveConfiguration();
    }

    public void saveConfiguration() {
        try {
            File configDir = new File("config");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            
            mapper.writerWithDefaultPrettyPrinter()
                  .writeValue(new File(CONFIG_FILE), configuration);
            logger.info("Configuration saved successfully");
        } catch (IOException e) {
            logger.error("Error saving configuration: " + e.getMessage());
        }
    }

    public String getMarketDataUrl() {
        return (String) configuration.get("marketDataUrl");
    }

    public String getApiKey() {
        return (String) configuration.get("apiKey");
    }

    public int getRefreshInterval() {
        return (Integer) configuration.get("refreshInterval");
    }

    public double getMlConfidenceThreshold() {
        return (Double) configuration.get("mlConfidenceThreshold");
    }

    public int getMinDataPoints() {
        return (Integer) configuration.get("minDataPoints");
    }

    public int getTrainingDays() {
        return (Integer) configuration.get("trainingDays");
    }

    public double getMaxPositionSize() {
        return (Double) configuration.get("maxPositionSize");
    }

    public double getMaxLossPerTrade() {
        return (Double) configuration.get("maxLossPerTrade");
    }

    public double getProfitTarget() {
        return (Double) configuration.get("profitTarget");
    }

    public void updateConfiguration(String key, Object value) {
        configuration.put(key, value);
        saveConfiguration();
    }
}