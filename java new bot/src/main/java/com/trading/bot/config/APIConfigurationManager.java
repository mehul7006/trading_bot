package com.trading.bot.config;

import java.io.*;
import java.util.*;
import java.time.Duration;

/**
 * API Configuration Manager
 * Handles all 5 key requirements:
 * 1. Upstox as primary API
 * 2. Secondary Upstox endpoint
 * 3. Response time based failover
 * 4. Dynamic API ordering
 * 5. Health monitoring and recovery
 */
public class APIConfigurationManager {
    
    private Properties config;
    private Map<String, APIConfig> apiConfigurations;
    
    public APIConfigurationManager() {
        loadConfiguration();
        initializeAPIConfigurations();
        System.out.println("✅ API Configuration Manager initialized");
    }
    
    /**
     * Load configuration from properties file
     */
    private void loadConfiguration() {
        config = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/api-config.properties")) {
            if (input != null) {
                config.load(input);
                System.out.println("📄 Loaded API configuration from properties file");
            } else {
                loadDefaultConfiguration();
                System.out.println("⚠️ Using default configuration");
            }
        } catch (IOException e) {
            System.err.println("❌ Error loading configuration: " + e.getMessage());
            loadDefaultConfiguration();
        }
    }
    
    /**
     * Load default configuration if file not found
     */
    private void loadDefaultConfiguration() {
        config = new Properties();
        
        // Primary API (Upstox)
        config.setProperty("primary.api.name", "UPSTOX");
        config.setProperty("primary.api.enabled", "true");
        config.setProperty("primary.api.timeout.seconds", "5");
        
        // Upstox Configuration
        config.setProperty("upstox.api.key", "768a303b-80f1-46d6-af16-f847f9341213");
        config.setProperty("upstox.access.token", "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTEwYjVjNjAwMGE4YzY0YWM5OGZjYzgiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MjcwMjc5MCwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYyNzI1NjAwfQ.DtaAeJFxSiIYyh8ZC9BV8vo8UczBu6DY8u3aWOsSdMY");
        config.setProperty("upstox.primary.url", "https://api.upstox.com/v2");
        config.setProperty("upstox.secondary.url", "https://api-v2.upstox.com");
        
        // Secondary APIs
        config.setProperty("secondary.apis", "YAHOO_FINANCE,ALPHA_VANTAGE,NSE_DIRECT");
        
        // Response time settings
        config.setProperty("response.time.max.ms", "5000");
        config.setProperty("failover.success.rate.threshold", "0.6");
    }
    
    /**
     * Initialize API configurations
     */
    private void initializeAPIConfigurations() {
        apiConfigurations = new HashMap<>();
        
        // Primary Upstox API
        apiConfigurations.put("UPSTOX_PRIMARY", new APIConfig(
            "UPSTOX_PRIMARY",
            config.getProperty("upstox.primary.url", "https://api.upstox.com/v2"),
            1,
            true,
            Duration.ofSeconds(getIntProperty("primary.api.timeout.seconds", 5)),
            APIConfig.APIType.UPSTOX
        ));
        
        // Secondary Upstox API
        apiConfigurations.put("UPSTOX_SECONDARY", new APIConfig(
            "UPSTOX_SECONDARY",
            config.getProperty("upstox.secondary.url", "https://api-v2.upstox.com"),
            2,
            true,
            Duration.ofSeconds(getIntProperty("primary.api.timeout.seconds", 6)),
            APIConfig.APIType.UPSTOX
        ));
        
        // Yahoo Finance
        apiConfigurations.put("YAHOO_FINANCE", new APIConfig(
            "YAHOO_FINANCE",
            config.getProperty("yahoo.finance.base.url", "https://query1.finance.yahoo.com/v8/finance/chart"),
            3,
            getBooleanProperty("yahoo.finance.enabled", true),
            Duration.ofSeconds(getIntProperty("yahoo.finance.timeout.seconds", 8)),
            APIConfig.APIType.YAHOO
        ));
        
        // Alpha Vantage
        apiConfigurations.put("ALPHA_VANTAGE", new APIConfig(
            "ALPHA_VANTAGE",
            config.getProperty("alpha.vantage.base.url", "https://www.alphavantage.co/query"),
            4,
            getBooleanProperty("alpha.vantage.enabled", true),
            Duration.ofSeconds(getIntProperty("alpha.vantage.timeout.seconds", 10)),
            APIConfig.APIType.ALPHA_VANTAGE
        ));
        
        // NSE Direct
        apiConfigurations.put("NSE_DIRECT", new APIConfig(
            "NSE_DIRECT",
            config.getProperty("nse.direct.base.url", "https://www.nseindia.com/api"),
            5,
            getBooleanProperty("nse.direct.enabled", true),
            Duration.ofSeconds(getIntProperty("nse.direct.timeout.seconds", 12)),
            APIConfig.APIType.NSE
        ));
        
        // Finnhub
        apiConfigurations.put("FINNHUB", new APIConfig(
            "FINNHUB",
            config.getProperty("finnhub.base.url", "https://finnhub.io/api/v1"),
            6,
            getBooleanProperty("finnhub.enabled", true),
            Duration.ofSeconds(getIntProperty("finnhub.timeout.seconds", 15)),
            APIConfig.APIType.FINNHUB
        ));
        
        System.out.println("🔧 Initialized " + apiConfigurations.size() + " API configurations");
        System.out.println("🎯 Primary: " + getPrimaryAPIName());
    }
    
    /**
     * Get primary API name
     */
    public String getPrimaryAPIName() {
        return config.getProperty("primary.api.name", "UPSTOX");
    }
    
    /**
     * Get API configuration by name
     */
    public APIConfig getAPIConfig(String apiName) {
        return apiConfigurations.get(apiName);
    }
    
    /**
     * Get all API configurations sorted by priority
     */
    public List<APIConfig> getAllAPIConfigs() {
        List<APIConfig> configs = new ArrayList<>(apiConfigurations.values());
        configs.sort(Comparator.comparingInt(APIConfig::getPriority));
        return configs;
    }
    
    /**
     * Get Upstox credentials
     */
    public String getUpstoxAPIKey() {
        return config.getProperty("upstox.api.key");
    }
    
    public String getUpstoxAccessToken() {
        return config.getProperty("upstox.access.token");
    }
    
    public String getUpstoxAPISecret() {
        return config.getProperty("upstox.api.secret");
    }
    
    /**
     * Get response time settings
     */
    public long getMaxResponseTimeMs() {
        return getLongProperty("response.time.max.ms", 5000L);
    }
    
    public double getFailoverSuccessRateThreshold() {
        return getDoubleProperty("failover.success.rate.threshold", 0.6);
    }
    
    public int getResponseTimeHistorySize() {
        return getIntProperty("response.time.history.size", 100);
    }
    
    /**
     * Get health monitoring settings
     */
    public boolean isHealthCheckEnabled() {
        return getBooleanProperty("health.check.enabled", true);
    }
    
    public int getHealthCheckIntervalMinutes() {
        return getIntProperty("health.check.interval.minutes", 5);
    }
    
    public boolean isAutoRecoveryEnabled() {
        return getBooleanProperty("auto.recovery.enabled", true);
    }
    
    /**
     * Get symbol mappings
     */
    public Map<String, String> getUpstoxSymbolMappings() {
        Map<String, String> mappings = new HashMap<>();
        mappings.put("NIFTY", config.getProperty("symbol.mapping.nifty.upstox", "NSE_INDEX%7CNifty%2050"));
        mappings.put("SENSEX", config.getProperty("symbol.mapping.sensex.upstox", "BSE_INDEX%7CSENSEX"));
        mappings.put("BANKNIFTY", config.getProperty("symbol.mapping.banknifty.upstox", "NSE_INDEX%7CNifty%20Bank"));
        mappings.put("FINNIFTY", config.getProperty("symbol.mapping.finnifty.upstox", "NSE_INDEX%7CNifty%20Fin%20Services"));
        return mappings;
    }
    
    public Map<String, String> getYahooSymbolMappings() {
        Map<String, String> mappings = new HashMap<>();
        mappings.put("NIFTY", config.getProperty("symbol.mapping.nifty.yahoo", "^NSEI"));
        mappings.put("SENSEX", config.getProperty("symbol.mapping.sensex.yahoo", "^BSESN"));
        mappings.put("BANKNIFTY", config.getProperty("symbol.mapping.banknifty.yahoo", "^NSEBANK"));
        mappings.put("FINNIFTY", config.getProperty("symbol.mapping.finnifty.yahoo", "^CNXFIN"));
        return mappings;
    }
    
    /**
     * Update API configuration dynamically
     */
    public void updateAPIConfig(String apiName, String property, String value) {
        String key = apiName.toLowerCase() + "." + property;
        config.setProperty(key, value);
        
        // Reload specific API configuration
        if (apiConfigurations.containsKey(apiName)) {
            // Refresh the specific API config
            initializeAPIConfigurations();
            System.out.println("🔄 Updated configuration for " + apiName + ": " + property + " = " + value);
        }
    }
    
    /**
     * Enable/Disable specific API
     */
    public void setAPIEnabled(String apiName, boolean enabled) {
        APIConfig config = apiConfigurations.get(apiName);
        if (config != null) {
            config.setEnabled(enabled);
            System.out.println((enabled ? "✅ Enabled" : "❌ Disabled") + " API: " + apiName);
        }
    }
    
    /**
     * Display current configuration
     */
    public void displayConfiguration() {
        System.out.println("\n📊 === CURRENT API CONFIGURATION ===");
        System.out.println("🎯 Primary API: " + getPrimaryAPIName());
        System.out.println("⏱️ Max Response Time: " + getMaxResponseTimeMs() + "ms");
        System.out.println("📈 Success Rate Threshold: " + (getFailoverSuccessRateThreshold() * 100) + "%");
        System.out.println("🔄 Health Check: " + (isHealthCheckEnabled() ? "Enabled" : "Disabled"));
        
        System.out.println("\n📡 === API ENDPOINTS ===");
        for (APIConfig apiConfig : getAllAPIConfigs()) {
            System.out.printf("%-20s Priority: %-2d Status: %-10s Timeout: %ds\n",
                apiConfig.getName(),
                apiConfig.getPriority(),
                apiConfig.isEnabled() ? "✅ Enabled" : "❌ Disabled",
                apiConfig.getTimeout().getSeconds()
            );
        }
    }
    
    // Helper methods for property parsing
    private int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(config.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private long getLongProperty(String key, long defaultValue) {
        try {
            return Long.parseLong(config.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private double getDoubleProperty(String key, double defaultValue) {
        try {
            return Double.parseDouble(config.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private boolean getBooleanProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(config.getProperty(key, String.valueOf(defaultValue)));
    }
    
    /**
     * API Configuration data class
     */
    public static class APIConfig {
        private final String name;
        private final String baseUrl;
        private final int priority;
        private boolean enabled;
        private final Duration timeout;
        private final APIType type;
        
        public enum APIType {
            UPSTOX, YAHOO, ALPHA_VANTAGE, NSE, FINNHUB, POLYGON
        }
        
        public APIConfig(String name, String baseUrl, int priority, boolean enabled, 
                        Duration timeout, APIType type) {
            this.name = name;
            this.baseUrl = baseUrl;
            this.priority = priority;
            this.enabled = enabled;
            this.timeout = timeout;
            this.type = type;
        }
        
        // Getters and setters
        public String getName() { return name; }
        public String getBaseUrl() { return baseUrl; }
        public int getPriority() { return priority; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getTimeout() { return timeout; }
        public APIType getType() { return type; }
    }
}