package com.trading.bot.market;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Live API Tester - Check both Upstox and Shoonya APIs separately
 * Shows exact responses from each API for NIFTY and SENSEX
 */
public class LiveAPITester {
    
    // API Credentials
    private static final String UPSTOX_API_KEY = "768a303b-80f1-46d6-af16-f847f9341213";
    private static final String UPSTOX_ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTEwYjVjNjAwMGE4YzY0YWM5OGZjYzgiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MjcwMjc5MCwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYyNzI1NjAwfQ.DtaAeJFxSiIYyh8ZC9BV8vo8UczBu6DY8u3aWOsSdMY";
    
    private static final String SHOONYA_VENDOR_CODE = "FN144243_U";
    private static final String SHOONYA_IMEI = "abc123";
    private static final String SHOONYA_API_KEY = "aa27c122b1641b7d0547904269303a2e";
    
    private static final String UPSTOX_BASE_URL = "https://api.upstox.com/v2";
    private static final String SHOONYA_BASE_URL = "https://api.shoonya.com/NorenWClientTP";
    
    private final HttpClient httpClient;
    
    public LiveAPITester() {
        this.httpClient = HttpClient.newHttpClient();
    }
    
    /**
     * Test both APIs and show live prices
     */
    public void testBothAPIs() {
        System.out.println("🔍 === LIVE API TESTING - BOTH SOURCES ===");
        System.out.println("⏰ Test Time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
        System.out.println("📊 Testing: NIFTY and SENSEX from both APIs");
        System.out.println();
        
        // Test Upstox API
        System.out.println("📡 === UPSTOX API TESTING ===");
        System.out.println("🔑 API Key: " + UPSTOX_API_KEY);
        System.out.println("🔐 Access Token: " + UPSTOX_ACCESS_TOKEN.substring(0, 30) + "***");
        System.out.println();
        
        testUpstoxNifty();
        testUpstoxSensex();
        
        System.out.println();
        
        // Test Shoonya API
        System.out.println("🏢 === SHOONYA API TESTING ===");
        System.out.println("🏢 Vendor Code: " + SHOONYA_VENDOR_CODE);
        System.out.println("📱 IMEI: " + SHOONYA_IMEI);
        System.out.println("🔑 API Key: " + SHOONYA_API_KEY);
        System.out.println();
        
        testShoonyaNifty();
        testShoonyaSensex();
        
        // Summary
        showTestSummary();
    }
    
    /**
     * Test Upstox NIFTY
     */
    private void testUpstoxNifty() {
        System.out.println("📈 TESTING UPSTOX - NIFTY");
        System.out.println("─".repeat(40));
        
        try {
            String instrumentKey = "NSE_INDEX%7CNifty%2050";
            String url = UPSTOX_BASE_URL + "/market-quote/quotes?instrument_key=" + instrumentKey;
            
            System.out.println("🌐 URL: " + url);
            System.out.println("📡 Making API call...");
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + UPSTOX_ACCESS_TOKEN)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("📊 Response Status: HTTP " + response.statusCode());
            System.out.println("📄 Response Headers: " + response.headers().map());
            System.out.println("📝 Raw Response Body:");
            System.out.println(response.body());
            System.out.println();
            
            if (response.statusCode() == 200) {
                parseAndDisplayUpstoxPrice("NIFTY", response.body());
            } else {
                System.out.println("❌ UPSTOX NIFTY: API Error - HTTP " + response.statusCode());
            }
            
        } catch (Exception e) {
            System.out.println("❌ UPSTOX NIFTY: Exception - " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println();
    }
    
    /**
     * Test Upstox SENSEX
     */
    private void testUpstoxSensex() {
        System.out.println("📈 TESTING UPSTOX - SENSEX");
        System.out.println("─".repeat(40));
        
        try {
            String instrumentKey = "BSE_INDEX%7CSENSEX";
            String url = UPSTOX_BASE_URL + "/market-quote/quotes?instrument_key=" + instrumentKey;
            
            System.out.println("🌐 URL: " + url);
            System.out.println("📡 Making API call...");
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + UPSTOX_ACCESS_TOKEN)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("📊 Response Status: HTTP " + response.statusCode());
            System.out.println("📝 Raw Response Body:");
            System.out.println(response.body());
            System.out.println();
            
            if (response.statusCode() == 200) {
                parseAndDisplayUpstoxPrice("SENSEX", response.body());
            } else {
                System.out.println("❌ UPSTOX SENSEX: API Error - HTTP " + response.statusCode());
            }
            
        } catch (Exception e) {
            System.out.println("❌ UPSTOX SENSEX: Exception - " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println();
    }
    
    /**
     * Test Shoonya NIFTY
     */
    private void testShoonyaNifty() {
        System.out.println("📈 TESTING SHOONYA - NIFTY");
        System.out.println("─".repeat(40));
        
        try {
            // First, try to get session token
            System.out.println("🔐 Step 1: Getting Shoonya session token...");
            String sessionToken = getShoonyaSessionToken();
            
            if (sessionToken != null) {
                System.out.println("✅ Session Token: " + sessionToken.substring(0, Math.min(20, sessionToken.length())) + "***");
                
                // Get NIFTY quote
                String url = SHOONYA_BASE_URL + "/GetQuotes";
                String formData = "jKey=" + sessionToken + "&exch=NSE&token=26000";
                
                System.out.println("🌐 URL: " + url);
                System.out.println("📡 Making API call for NIFTY (Token: 26000)...");
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formData))
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                System.out.println("📊 Response Status: HTTP " + response.statusCode());
                System.out.println("📝 Raw Response Body:");
                System.out.println(response.body());
                System.out.println();
                
                if (response.statusCode() == 200) {
                    parseAndDisplayShoonyaPrice("NIFTY", response.body());
                } else {
                    System.out.println("❌ SHOONYA NIFTY: API Error - HTTP " + response.statusCode());
                }
                
            } else {
                System.out.println("❌ SHOONYA NIFTY: Failed to get session token");
            }
            
        } catch (Exception e) {
            System.out.println("❌ SHOONYA NIFTY: Exception - " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println();
    }
    
    /**
     * Test Shoonya SENSEX
     */
    private void testShoonyaSensex() {
        System.out.println("📈 TESTING SHOONYA - SENSEX");
        System.out.println("─".repeat(40));
        
        try {
            // Get session token
            String sessionToken = getShoonyaSessionToken();
            
            if (sessionToken != null) {
                // Get SENSEX quote
                String url = SHOONYA_BASE_URL + "/GetQuotes";
                String formData = "jKey=" + sessionToken + "&exch=BSE&token=1";
                
                System.out.println("🌐 URL: " + url);
                System.out.println("📡 Making API call for SENSEX (Token: 1)...");
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formData))
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                System.out.println("📊 Response Status: HTTP " + response.statusCode());
                System.out.println("📝 Raw Response Body:");
                System.out.println(response.body());
                System.out.println();
                
                if (response.statusCode() == 200) {
                    parseAndDisplayShoonyaPrice("SENSEX", response.body());
                } else {
                    System.out.println("❌ SHOONYA SENSEX: API Error - HTTP " + response.statusCode());
                }
                
            } else {
                System.out.println("❌ SHOONYA SENSEX: Failed to get session token");
            }
            
        } catch (Exception e) {
            System.out.println("❌ SHOONYA SENSEX: Exception - " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println();
    }
    
    /**
     * Get Shoonya session token
     */
    private String getShoonyaSessionToken() {
        try {
            System.out.println("🔐 Attempting Shoonya login...");
            System.out.println("   Vendor: " + SHOONYA_VENDOR_CODE);
            System.out.println("   IMEI: " + SHOONYA_IMEI);
            System.out.println("   API Key: " + SHOONYA_API_KEY.substring(0, 8) + "***");
            
            String url = SHOONYA_BASE_URL + "/QuickAuth";
            
            // Note: In production, you would need the actual password
            // For demo, we'll try with placeholder
            String loginData = String.format(
                "jData={\"uid\":\"%s\",\"pwd\":\"your_password_here\",\"factor2\":\"%s\",\"vc\":\"%s\",\"appkey\":\"%s\",\"imei\":\"%s\"}",
                SHOONYA_VENDOR_CODE, SHOONYA_IMEI, SHOONYA_VENDOR_CODE, SHOONYA_API_KEY, SHOONYA_IMEI
            );
            
            System.out.println("📡 Login URL: " + url);
            System.out.println("📝 Login Data: " + loginData.substring(0, Math.min(50, loginData.length())) + "***");
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(loginData))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("📊 Login Response Status: HTTP " + response.statusCode());
            System.out.println("📝 Login Response Body:");
            System.out.println(response.body());
            
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                if (responseBody.contains("\"susertoken\":")) {
                    int start = responseBody.indexOf("\"susertoken\":\"") + 14;
                    int end = responseBody.indexOf("\"", start);
                    return responseBody.substring(start, end);
                }
            }
            
            return null;
            
        } catch (Exception e) {
            System.out.println("❌ Shoonya login error: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse and display Upstox price
     */
    private void parseAndDisplayUpstoxPrice(String symbol, String jsonResponse) {
        try {
            System.out.println("🔍 PARSING UPSTOX " + symbol + " DATA:");
            
            // Check if response is successful
            if (jsonResponse.contains("\"status\":\"success\"")) {
                System.out.println("✅ API Status: SUCCESS");
                
                // Extract price data
                double ltp = extractJsonDouble(jsonResponse, "ltp");
                double netChange = extractJsonDouble(jsonResponse, "net_change");
                double pctChange = extractJsonDouble(jsonResponse, "pct_change");
                double open = extractJsonDouble(jsonResponse, "open");
                double high = extractJsonDouble(jsonResponse, "high");
                double low = extractJsonDouble(jsonResponse, "low");
                
                if (ltp > 0) {
                    System.out.println("💰 UPSTOX " + symbol + " LIVE PRICE:");
                    System.out.printf("   LTP: ₹%,.2f\n", ltp);
                    System.out.printf("   Change: %+.2f (%+.2f%%)\n", netChange, pctChange);
                    System.out.printf("   Open: ₹%,.2f | High: ₹%,.2f | Low: ₹%,.2f\n", open, high, low);
                    System.out.printf("   Source: UPSTOX API ✅\n");
                } else {
                    System.out.println("❌ Could not extract price data from response");
                }
                
            } else {
                System.out.println("❌ API Status: ERROR or response format issue");
            }
            
        } catch (Exception e) {
            System.out.println("❌ Error parsing Upstox response: " + e.getMessage());
        }
    }
    
    /**
     * Parse and display Shoonya price
     */
    private void parseAndDisplayShoonyaPrice(String symbol, String jsonResponse) {
        try {
            System.out.println("🔍 PARSING SHOONYA " + symbol + " DATA:");
            
            // Check if response is successful
            if (jsonResponse.contains("\"stat\":\"Ok\"")) {
                System.out.println("✅ API Status: SUCCESS");
                
                // Extract price data from Shoonya format
                double ltp = extractJsonDouble(jsonResponse, "lp");
                double netChange = extractJsonDouble(jsonResponse, "c");
                double open = extractJsonDouble(jsonResponse, "o");
                double high = extractJsonDouble(jsonResponse, "h");
                double low = extractJsonDouble(jsonResponse, "l");
                double pctChange = netChange / (ltp - netChange) * 100;
                
                if (ltp > 0) {
                    System.out.println("💰 SHOONYA " + symbol + " LIVE PRICE:");
                    System.out.printf("   LTP: ₹%,.2f\n", ltp);
                    System.out.printf("   Change: %+.2f (%+.2f%%)\n", netChange, pctChange);
                    System.out.printf("   Open: ₹%,.2f | High: ₹%,.2f | Low: ₹%,.2f\n", open, high, low);
                    System.out.printf("   Source: SHOONYA API ✅\n");
                } else {
                    System.out.println("❌ Could not extract price data from response");
                }
                
            } else {
                System.out.println("❌ API Status: ERROR or authentication issue");
            }
            
        } catch (Exception e) {
            System.out.println("❌ Error parsing Shoonya response: " + e.getMessage());
        }
    }
    
    /**
     * Extract double value from JSON
     */
    private double extractJsonDouble(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1) return 0.0;
            
            startIndex += searchKey.length();
            int endIndex = json.indexOf(",", startIndex);
            if (endIndex == -1) endIndex = json.indexOf("}", startIndex);
            
            String valueStr = json.substring(startIndex, endIndex).trim();
            valueStr = valueStr.replace("\"", "");
            
            return Double.parseDouble(valueStr);
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * Show test summary
     */
    private void showTestSummary() {
        System.out.println("📋 === API TESTING SUMMARY ===");
        System.out.println("⏰ Test Completed: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println();
        
        System.out.println("📡 UPSTOX API:");
        System.out.println("   🔑 API Key: Configured ✅");
        System.out.println("   🔐 Access Token: Configured ✅");
        System.out.println("   📊 NIFTY: Test attempted ✅");
        System.out.println("   📊 SENSEX: Test attempted ✅");
        System.out.println("   ⚠️  Note: Check token expiry if errors");
        System.out.println();
        
        System.out.println("🏢 SHOONYA API:");
        System.out.println("   🏢 Vendor Code: Configured ✅");
        System.out.println("   📱 IMEI: Configured ✅");
        System.out.println("   🔑 API Key: Configured ✅");
        System.out.println("   📊 NIFTY: Test attempted ✅");
        System.out.println("   📊 SENSEX: Test attempted ✅");
        System.out.println("   ⚠️  Note: Password required for authentication");
        System.out.println();
        
        System.out.println("💡 NEXT STEPS:");
        System.out.println("   1. If Upstox shows errors → Refresh access token");
        System.out.println("   2. If Shoonya shows errors → Add password to credentials");
        System.out.println("   3. Both working → Your failover system is ready!");
        System.out.println();
        
        System.out.println("🎯 Your failover system will automatically handle API failures");
        System.out.println("   and provide real data when available! 🚀");
    }
    
    /**
     * Main method
     */
    public static void main(String[] args) {
        System.out.println("🔍 === LIVE API TESTING - UPSTOX & SHOONYA ===");
        System.out.println("📊 Testing both APIs for NIFTY and SENSEX live prices");
        System.out.println("🎯 This will show you exactly what each API returns");
        System.out.println();
        
        LiveAPITester tester = new LiveAPITester();
        tester.testBothAPIs();
    }
}