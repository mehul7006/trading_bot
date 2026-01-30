package com.trading.bot.market;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpstoxApiConnector {
    private static final Logger logger = LoggerFactory.getLogger(UpstoxApiConnector.class);
    
    private static final String UPSTOX_API_BASE = "https://api.upstox.com/v2";
    private static final String JWT_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTE1ZTFjM2EzNjg3NjZjOGIzZDFiZTQiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MzA0MTczMSwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYzMDcxMjAwfQ.Yy55jdoFz3fFRV_9NmGkQz6ProawgU8lRdqoWr12zhY";
    
    private final HttpClient httpClient;
    private final Map<String, MarketData> latestData;
    
    public UpstoxApiConnector() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
        this.latestData = new ConcurrentHashMap<>();
    }
    
    public MarketData fetchMarketData(String symbol) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(UPSTOX_API_BASE + "/market-quote/ltp?instrument_key=" + symbol))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + JWT_TOKEN)
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseMarketData(response.body(), symbol);
            } else {
                logger.error("Failed to fetch market data. Status code: " + response.statusCode());
                return null;
            }
        } catch (Exception e) {
            logger.error("Error fetching market data: " + e.getMessage());
            return null;
        }
    }
    
    private MarketData parseMarketData(String jsonResponse, String symbol) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(jsonResponse);
            JsonNode data = json.get("data");
            
            if (data != null) {
                double ltp = data.get("ltp").asDouble();
                // Fetch additional market depth data
                MarketData marketData = fetchMarketDepth(symbol);
                
                if (marketData != null) {
                    List<Double> priceHistory = marketData.getPriceHistory();
                    if (priceHistory == null) {
                        priceHistory = new ArrayList<>();
                    }
                    priceHistory.add(ltp);
                    
                    double open = data.path("ohlc").path("open").asDouble(0.0);
                    double high = data.path("ohlc").path("high").asDouble(0.0);
                    double low = data.path("ohlc").path("low").asDouble(0.0);
                    double close = data.path("ohlc").path("close").asDouble(0.0);
                    double volume = data.path("volume").asDouble(0.0);
                    double vwap = data.path("vwap").asDouble(0.0);
                    
                    return new MarketData(symbol, open, high, low, close,
                                        ltp, volume, vwap, 0.0,
                                        priceHistory, null, null, null,
                                        String.format("%.2f", ltp - close), false);
                }
            }
            
            return null;
        } catch (Exception e) {
            logger.error("Error parsing market data: " + e.getMessage(), e);
            return null;
        }
    }
    
    private MarketData fetchMarketDepth(String symbol) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(UPSTOX_API_BASE + "/market-quote/depth?instrument_key=" + symbol))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + JWT_TOKEN)
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseMarketDepth(response.body(), symbol);
            } else {
                logger.error("Failed to fetch market depth. Status code: " + response.statusCode());
                return null;
            }
        } catch (Exception e) {
            logger.error("Error fetching market depth: " + e.getMessage());
            return null;
        }
    }
    
    private MarketData parseMarketDepth(String jsonResponse, String symbol) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(jsonResponse);
            JsonNode data = json.get("data");
            
            if (data != null) {
                double open = data.path("open_price").asDouble(0.0);
                double high = data.path("high_price").asDouble(0.0);
                double low = data.path("low_price").asDouble(0.0);
                double close = data.path("close_price").asDouble(0.0);
                double volume = data.path("volume").asDouble(0.0);
                
                return new MarketData(
                    symbol,
                    open,
                    high,
                    low,
                    close,
                    close, // current price
                    volume,
                    calculateVWAP(data),
                    calculateVolatility(data),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    String.format("%.2f", close - open),
                    false
                );
            }
            return null;
        } catch (Exception e) {
            logger.error("Error parsing market depth: " + e.getMessage(), e);
            return null;
        }
    }
    
    private double calculateVWAP(JsonNode data) {
        try {
            JsonNode bids = data.get("bids");
            JsonNode asks = data.get("asks");
            
            double totalVolume = 0;
            double weightedSum = 0;
            
            if (bids != null && bids.isArray()) {
                for (JsonNode bid : bids) {
                    double price = bid.path("price").asDouble(0.0);
                    double quantity = bid.path("quantity").asDouble(0.0);
                    weightedSum += price * quantity;
                    totalVolume += quantity;
                }
            }
            
            if (asks != null && asks.isArray()) {
                for (JsonNode ask : asks) {
                    double price = ask.path("price").asDouble(0.0);
                    double quantity = ask.path("quantity").asDouble(0.0);
                    weightedSum += price * quantity;
                    totalVolume += quantity;
                }
            }
            
            return totalVolume > 0 ? weightedSum / totalVolume : 0;
        } catch (Exception e) {
            logger.error("Error calculating VWAP: " + e.getMessage(), e);
            return 0.0;
        }
    }
    
    private double calculateVolatility(JsonNode data) {
        try {
            double high = data.path("high_price").asDouble(0.0);
            double low = data.path("low_price").asDouble(0.0);
            double open = data.path("open_price").asDouble(0.0);
            
            if (open > 0) {
                return ((high - low) / open) * 100;
            }
            return 0.0;
        } catch (Exception e) {
            logger.error("Error calculating volatility: " + e.getMessage(), e);
            return 0.0;
        }
    }
    
    private String calculateDayMove(double open, double close) {
        if (open > 0) {
            double change = ((close - open) / open) * 100;
            return String.format("%+.2f%%", change);
        }
        return "0.00%";
    }
    
    public void shutdown() {
        // Cleanup any resources if needed
    }
    
    private String calculateDayMove(double open, double close) {
        return String.format("%.2f", close - open);
    }
}