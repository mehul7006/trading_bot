package com.trading.bot.market;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class SimpleUpstoxApiTest {
    private static final String API_URL = "https://api.upstox.com/v2/market-quote/ltp";
    private static final String ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTA4YmFkZmY2NzIxMDYwYWQ3YjEwZTEiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MjE3OTgwNywiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYyMjA3MjAwfQ.GcXStAF_bXQqRWYn4B0Jd5Y9WdvPvDsn670QNBZvFb4";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    
    public static void main(String[] args) {
        try {
            System.out.println("🚀 Testing Upstox API Connection");
            System.out.println("=" .repeat(50));
            System.out.println("API URL: " + API_URL);
            System.out.println("Access Token: " + ACCESS_TOKEN.substring(0, 20) + "...");
            
            String[] symbols = {
                "NSE_INDEX|Nifty 50",
                "BSE_INDEX|SENSEX"
            };
            
            System.out.println("\nCreating HTTP client with retries...");
            HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
            
            System.out.println("Creating ObjectMapper...");
            ObjectMapper mapper = new ObjectMapper();
            
            for (String symbol : symbols) {
                System.out.println("\n📊 Fetching data for " + symbol);
                
                String url = API_URL + "?instrument_key=" + symbol;
                System.out.println("URL: " + url);
                
                HttpResponse<String> response = null;
                Exception lastError = null;
                
                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                    try {
                        if (attempt > 1) {
                            System.out.println("Retry attempt " + attempt + " of " + MAX_RETRIES);
                            Thread.sleep(RETRY_DELAY_MS);
                        }
                        
                        System.out.println("Building request...");
                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Accept", "application/json")
                            .header("Authorization", "Bearer " + ACCESS_TOKEN)
                            .GET()
                            .build();
                        
                        System.out.println("Sending request...");
                        response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        
                        if (response.statusCode() == 200) {
                            break;
                        }
                        
                        lastError = new RuntimeException("HTTP " + response.statusCode());
                    } catch (Exception e) {
                        lastError = e;
                        System.out.println("Request failed: " + e.getMessage());
                        if (attempt == MAX_RETRIES) {
                            throw e;
                        }
                    }
                }
                
                System.out.println("Status code: " + response.statusCode());
                if (response.statusCode() == 200) {
                    System.out.println("Parsing response...");
                    JsonNode root = mapper.readTree(response.body());
                    System.out.println("Response: " + root.toPrettyString());
                    
                    if (root.has("data") && root.get("data").has(symbol)) {
                        JsonNode data = root.get("data").get(symbol);
                        if (data.has("last_price")) {
                            double lastPrice = data.get("last_price").asDouble();
                            System.out.println("✅ Current Price: " + String.format("%.2f", lastPrice));
                        } else {
                            System.out.println("❌ No 'last_price' field in data");
                            System.out.println("Data: " + data.toPrettyString());
                        }
                    } else {
                        System.out.println("❌ No price data found in response");
                        System.out.println("Response structure:");
                        if (root.has("data")) {
                            System.out.println("Fields in data: " + root.get("data").fieldNames());
                        } else {
                            System.out.println("Available fields: " + root.fieldNames());
                        }
                    }
                } else {
                    System.out.println("❌ Error: " + response.statusCode());
                    System.out.println("Response: " + response.body());
            }
            
        } catch (Exception e) {
            System.out.println("\n❌ Error occurred:");
            System.out.println("Type: " + e.getClass().getName());
            System.out.println("Message: " + e.getMessage());
            System.out.println("\nStack trace:");
            e.printStackTrace(System.out);
        }
    }
            e.printStackTrace();
        }
    }
}