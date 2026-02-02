package com.trading.bot.test;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import java.util.List;
import java.time.LocalDate;

public class DebugPrices {
    public static void main(String[] args) {
        System.out.println("🔍 DEBUG: Fetching latest candle details...");
        
        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        String[] symbols = {"NIFTY50", "SENSEX", "BANKNIFTY"};
        java.util.Map<String, String> symbolMap = java.util.Map.of(
            "NIFTY50", "NSE_INDEX|Nifty 50",
            "SENSEX", "BSE_INDEX|SENSEX", 
            "BANKNIFTY", "NSE_INDEX|Nifty Bank"
        );
        
        // Use tomorrow's date to ensure we capture all of today
        String toDate = LocalDate.now().plusDays(1).toString();
        String fromDate = LocalDate.now().toString(); // Fetch ONLY today
        
        System.out.println("📅 Date Range: " + fromDate + " to " + toDate);
        System.out.println("⏰ System Time: " + java.time.LocalDateTime.now());
        
        // Try fetching OHLC
        System.out.println("\n🔍 Checking Real-Time OHLC...");
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String token = HonestMarketDataFetcher.getAccessToken();
            
            for (String symbol : symbols) {
                String instrumentKey = symbolMap.get(symbol);
                String encodedKey = java.net.URLEncoder.encode(instrumentKey, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
                String url = "https://api.upstox.com/v2/market-quote/ohlc?instrument_key=" + encodedKey + "&interval=1d";
                
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
                    
                java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                System.out.println(symbol + " OHLC Response Code: " + response.statusCode());
                System.out.println(symbol + " OHLC Body: " + response.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
        for (String symbol : symbols) {
            try {
                System.out.println("\n--------------------------------------------------");
                System.out.println("Checking " + symbol);
                List<SimpleMarketData> data = fetcher.fetchHistoricalCandles(symbol, "1minute", fromDate, toDate);
                
                if (!data.isEmpty()) {
                    SimpleMarketData last = data.get(data.size() - 1);
                    System.out.println("✅ Last Candle Timestamp: " + last.timestamp);
                    System.out.println("   Open : " + last.open);
                    System.out.println("   High : " + last.high);
                    System.out.println("   Low  : " + last.low);
                    System.out.println("   Close: " + last.price); // price is close
                    System.out.println("   Count: " + data.size());
                    
                    if (data.size() > 1) {
                        SimpleMarketData prev = data.get(data.size() - 2);
                        System.out.println("   Prev Candle Timestamp: " + prev.timestamp);
                        System.out.println("   Prev Close: " + prev.price);
                    }
                } else {
                    System.out.println("❌ No data found.");
                }
            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        */
    }
}
