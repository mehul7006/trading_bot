package com.trading.bot.market;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class ShoonyaApiFallback {
    private static final Logger logger = LoggerFactory.getLogger(ShoonyaApiFallback.class);
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    
    public ShoonyaApiFallback() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
        this.mapper = new ObjectMapper();
    }
    
    public void initializeFallbackPrices() {
        try {
            // Try Shoonya API as backup
            String shoonyaApiKey = System.getenv("SHOONYA_API_KEY");
            if (shoonyaApiKey != null && !shoonyaApiKey.isEmpty()) {
                fetchShoonyaPrices(shoonyaApiKey);
                return;
            }
        } catch (Exception e) {
            logger.error("Shoonya API fallback failed: {}", e.getMessage());
        }
        
        // Use NSE direct feed as last resort
        try {
            fetchNSEDirectPrices();
        } catch (Exception e) {
            logger.error("NSE direct feed failed: {}", e.getMessage());
            useEmergencyPrices();
        }
    }
    
    private void fetchShoonyaPrices(String apiKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.shoonya.com/v2/quotes"))
            .header("X-API-KEY", apiKey)
            .GET()
            .build();
    
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Shoonya API request failed: " + response.statusCode());
        }
    
        parseAndUpdatePrices(response.body());
    }
    
    private void fetchNSEDirectPrices() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://www.nseindia.com/api/market-data"))
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .GET()
            .build();
            
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("NSE API request failed: " + response.statusCode());
        }
        
        parseAndUpdatePrices(response.body());
    }
    
    private void parseAndUpdatePrices(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode data = root.get("data");
        
        if (data == null || !data.isArray()) {
            throw new RuntimeException("Invalid market data format");
        }
        
        // Process market data
        for (JsonNode quote : data) {
            String symbol = quote.path("symbol").asText();
            double price = quote.path("last_price").asDouble();
            double volume = quote.path("volume").asDouble();
            double open = quote.path("open").asDouble();
            double high = quote.path("high").asDouble();
            double low = quote.path("low").asDouble();
            double close = quote.path("close").asDouble();
            
            MarketData marketData = new MarketData(
                symbol, open, high, low, close,
                price, volume, 0.0, 0.0,
                null, null, null, null,
                String.format("%.2f", price - close),
                true // flag as fallback data
            );
            
            // TODO: Update the market data store
            logger.info("Updated fallback price for {}: {}", symbol, price);
        }
    }
    
    private void useEmergencyPrices() {
        logger.warn("Using emergency fixed prices as last resort");
        // TODO: Implement emergency price fallback
        // This could be last known good prices or conservative estimates
    }
}