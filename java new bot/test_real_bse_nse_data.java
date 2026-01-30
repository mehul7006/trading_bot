import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * HONEST TEST OF REAL BSE/NSE DATA SOURCES
 * Tests official exchange data without Upstox dependency
 */
public class test_real_bse_nse_data {
    
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .build();
    
    public static void main(String[] args) {
        System.out.println("🔍 HONEST AUDIT: TESTING REAL BSE/NSE DATA SOURCES");
        System.out.println("=" .repeat(70));
        System.out.println("📊 Testing official exchange websites");
        System.out.println("⏰ " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("=" .repeat(70));
        
        // Test multiple real data sources
        testNSEOfficial();
        testBSEOfficial();
        testNSEAlternative();
        testBSEAlternative();
        testYahooFinanceReality();
        
        System.out.println("\n🎯 HONEST AUDIT SUMMARY:");
        System.out.println("=" .repeat(70));
        System.out.println("📋 Most free APIs have restrictions or are unreliable");
        System.out.println("💰 Professional data requires paid subscriptions");
        System.out.println("🔧 Current Upstox bot needs proper authentication to work");
        System.out.println("=" .repeat(70));
    }
    
    private static void testNSEOfficial() {
        System.out.println("\n📈 TESTING NSE OFFICIAL WEBSITE:");
        System.out.println("-".repeat(50));
        
        try {
            // NSE official API
            String url = "https://www.nseindia.com/api/quote-equity?symbol=NIFTY";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.printf("Status: %d%n", response.statusCode());
            if (response.statusCode() == 200) {
                String body = response.body();
                System.out.println("✅ NSE API Response received");
                System.out.println("📊 Data length: " + body.length() + " characters");
                
                // Try to extract NIFTY price
                if (body.contains("lastPrice") || body.contains("ltp")) {
                    System.out.println("✅ Price data found in response");
                    
                    // Simple extraction attempt
                    String priceStr = extractNSEPrice(body);
                    if (priceStr != null) {
                        System.out.println("📊 NIFTY Price: " + priceStr);
                    } else {
                        System.out.println("⚠️ Could not parse price from response");
                    }
                } else {
                    System.out.println("❌ No price data found in response");
                    System.out.println("Response preview: " + body.substring(0, Math.min(200, body.length())));
                }
            } else {
                System.out.println("❌ NSE API failed: " + response.statusCode());
                System.out.println("Response: " + response.body());
            }
            
        } catch (Exception e) {
            System.out.println("❌ NSE API Error: " + e.getMessage());
        }
    }
    
    private static void testBSEOfficial() {
        System.out.println("\n📈 TESTING BSE OFFICIAL WEBSITE:");
        System.out.println("-".repeat(50));
        
        try {
            // BSE official website
            String url = "https://api.bseindia.com/BseIndiaAPI/api/getScripHeaderData/w?Debtflag=&scripcode=1&seriesid=";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(java.time.Duration.ofSeconds(10))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.printf("Status: %d%n", response.statusCode());
            if (response.statusCode() == 200) {
                String body = response.body();
                System.out.println("✅ BSE API Response received");
                System.out.println("📊 Data length: " + body.length() + " characters");
                
                if (body.contains("CurrRate") || body.contains("LastRate")) {
                    System.out.println("✅ Price data found in response");
                    String priceStr = extractBSEPrice(body);
                    if (priceStr != null) {
                        System.out.println("📊 SENSEX Price: " + priceStr);
                    }
                } else {
                    System.out.println("⚠️ No clear price data found");
                    System.out.println("Response preview: " + body.substring(0, Math.min(200, body.length())));
                }
            } else {
                System.out.println("❌ BSE API failed: " + response.statusCode());
            }
            
        } catch (Exception e) {
            System.out.println("❌ BSE API Error: " + e.getMessage());
        }
    }
    
    private static void testNSEAlternative() {
        System.out.println("\n📈 TESTING NSE ALTERNATIVE ENDPOINTS:");
        System.out.println("-".repeat(50));
        
        String[] nseUrls = {
            "https://www.nseindia.com/api/allIndices",
            "https://www.nseindia.com/api/equity-stockIndices?index=NIFTY%2050",
            "https://www1.nseindia.com/live_market/dynaContent/live_watch/get_quote/GetQuote.jsp?symbol=NIFTY"
        };
        
        for (String url : nseUrls) {
            try {
                System.out.println("Testing: " + url);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .timeout(java.time.Duration.ofSeconds(8))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.printf("  Status: %d, Length: %d%n", response.statusCode(), response.body().length());
                
                if (response.statusCode() == 200 && response.body().length() > 100) {
                    System.out.println("  ✅ Potential data source");
                } else {
                    System.out.println("  ❌ Not usable");
                }
                
            } catch (Exception e) {
                System.out.println("  ❌ Failed: " + e.getMessage());
            }
        }
    }
    
    private static void testBSEAlternative() {
        System.out.println("\n📈 TESTING BSE ALTERNATIVE ENDPOINTS:");
        System.out.println("-".repeat(50));
        
        String[] bseUrls = {
            "https://www.bseindia.com/sensex/sensex_json.json",
            "https://api.bseindia.com/BseIndiaAPI/api/SensexData/w",
            "https://www.bseindia.com/markets/MarketInfo/DispQuote.aspx"
        };
        
        for (String url : bseUrls) {
            try {
                System.out.println("Testing: " + url);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(java.time.Duration.ofSeconds(8))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.printf("  Status: %d, Length: %d%n", response.statusCode(), response.body().length());
                
                if (response.statusCode() == 200 && response.body().length() > 100) {
                    System.out.println("  ✅ Potential data source");
                } else {
                    System.out.println("  ❌ Not usable");
                }
                
            } catch (Exception e) {
                System.out.println("  ❌ Failed: " + e.getMessage());
            }
        }
    }
    
    private static void testYahooFinanceReality() {
        System.out.println("\n📈 TESTING YAHOO FINANCE REALITY:");
        System.out.println("-".repeat(50));
        
        String[] symbols = {"^NSEI", "^BSESN"};
        String[] names = {"NIFTY", "SENSEX"};
        
        for (int i = 0; i < symbols.length; i++) {
            try {
                String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbols[i];
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(java.time.Duration.ofSeconds(8))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                System.out.printf("%s: Status %d%n", names[i], response.statusCode());
                
                if (response.statusCode() == 200) {
                    String body = response.body();
                    if (body.contains("regularMarketPrice")) {
                        String price = extractYahooPrice(body);
                        if (price != null) {
                            System.out.printf("  ✅ %s Price: %s%n", names[i], price);
                        } else {
                            System.out.println("  ⚠️ Could not extract price");
                        }
                    } else {
                        System.out.println("  ❌ No price data in response");
                    }
                } else {
                    System.out.println("  ❌ API call failed");
                }
                
            } catch (Exception e) {
                System.out.printf("  ❌ %s failed: %s%n", names[i], e.getMessage());
            }
        }
    }
    
    private static String extractNSEPrice(String json) {
        try {
            String[] patterns = {"\"lastPrice\":", "\"ltp\":", "\"close\":"};
            for (String pattern : patterns) {
                int start = json.indexOf(pattern);
                if (start != -1) {
                    start += pattern.length();
                    int end = findJsonValueEnd(json, start);
                    String value = json.substring(start, end).trim().replace("\"", "");
                    if (value.matches("\\d+\\.?\\d*")) {
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return null;
    }
    
    private static String extractBSEPrice(String json) {
        try {
            String[] patterns = {"\"CurrRate\":", "\"LastRate\":", "\"LTP\":"};
            for (String pattern : patterns) {
                int start = json.indexOf(pattern);
                if (start != -1) {
                    start += pattern.length();
                    int end = findJsonValueEnd(json, start);
                    String value = json.substring(start, end).trim().replace("\"", "");
                    if (value.matches("\\d+\\.?\\d*")) {
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return null;
    }
    
    private static String extractYahooPrice(String json) {
        try {
            String pattern = "\"regularMarketPrice\":";
            int start = json.indexOf(pattern);
            if (start != -1) {
                start += pattern.length();
                int end = findJsonValueEnd(json, start);
                return json.substring(start, end).trim();
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return null;
    }
    
    private static int findJsonValueEnd(String json, int start) {
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        
        int comma = json.indexOf(',', start);
        int brace = json.indexOf('}', start);
        int bracket = json.indexOf(']', start);
        
        int end = json.length();
        if (comma != -1) end = Math.min(end, comma);
        if (brace != -1) end = Math.min(end, brace);
        if (bracket != -1) end = Math.min(end, bracket);
        
        return end;
    }
}