import java.io.*;
import java.net.*;
import java.net.http.*;

public class quick_yahoo_finance_test {
    public static void main(String[] args) {
        System.out.println("🔧 QUICK FIX: Yahoo Finance URL Encoding");
        
        try {
            // Fix the URL encoding issue
            String niftyUrl = "https://query1.finance.yahoo.com/v8/finance/chart/%5ENSEI";  // %5E = ^
            String sensexUrl = "https://query1.finance.yahoo.com/v8/finance/chart/%5EBSESN"; // %5E = ^
            
            HttpClient client = HttpClient.newBuilder().build();
            
            // Test NIFTY
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(niftyUrl))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("NIFTY Status: " + response.statusCode());
            
            if (response.statusCode() == 200 && response.body().contains("regularMarketPrice")) {
                System.out.println("✅ Yahoo Finance NIFTY data accessible!");
                
                // Extract price
                String body = response.body();
                int start = body.indexOf("\"regularMarketPrice\":");
                if (start != -1) {
                    start += "\"regularMarketPrice\":".length();
                    int end = body.indexOf(',', start);
                    if (end == -1) end = body.indexOf('}', start);
                    String price = body.substring(start, end).trim();
                    System.out.println("📊 NIFTY Price: " + price);
                }
            } else {
                System.out.println("❌ Yahoo Finance still not working");
            }
            
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }
}