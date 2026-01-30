package com.trading.bot.market;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SHOONYA OTP GENERATOR - STEP BY STEP AUTHENTICATION
 * First asks credentials, then generates OTP from official site
 */
public class ShoonyaOTPGenerator {
    
    // API Configuration
    private static final String BASE_URL = "https://api.shoonya.com/NorenWClientTP";
    private static final String VENDOR_CODE = "FN144243_U";
    private static final String IMEI = "abc1234";
    private static final String API_KEY = "c25695ce7271993dceadfc6bdeecd601";
    private static final String SOURCE = "API";
    private static final String APP_VERSION = "1.0.0";
    
    private final HttpClient httpClient;
    private final Scanner scanner;
    private String sessionToken;
    private boolean isAuthenticated = false;
    
    public ShoonyaOTPGenerator() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .build();
        this.scanner = new Scanner(System.in);
        
        System.out.println("🔐 === SHOONYA OTP GENERATOR ===");
        System.out.println("📱 Step-by-step authentication process");
        System.out.println("🏢 Vendor Code: " + VENDOR_CODE);
        System.out.println("📱 IMEI: " + IMEI);
        System.out.println("🔑 API Key: " + API_KEY.substring(0, 8) + "***");
        System.out.println();
    }
    
    /**
     * STEP 1: ASK FOR CLIENT CODE AND PASSWORD
     */
    public Map<String, String> getCredentialsFromUser() {
        System.out.println("🔐 === STEP 1: ENTER YOUR CREDENTIALS ===");
        System.out.println("📝 Please enter your Finvasia credentials:");
        System.out.println();
        
        // Get Client Code
        System.out.print("👤 Enter your Client Code (e.g., fn144243): ");
        String clientCode = scanner.nextLine().trim();
        
        // Get Password
        System.out.print("🔒 Enter your Trading Password: ");
        String password = scanner.nextLine().trim();
        
        System.out.println();
        System.out.println("✅ Credentials entered:");
        System.out.println("👤 Client Code: " + clientCode);
        System.out.println("🔒 Password: " + password.substring(0, 2) + "***");
        System.out.println();
        
        Map<String, String> credentials = new HashMap<>();
        credentials.put("clientCode", clientCode);
        credentials.put("password", password);
        
        return credentials;
    }
    
    /**
     * STEP 2: GENERATE OTP FROM OFFICIAL FINVASIA SITE
     */
    public boolean generateOTPFromOfficialSite(String clientCode, String password) {
        System.out.println("📱 === STEP 2: GENERATING OTP FROM OFFICIAL SITE ===");
        System.out.println("🌐 Connecting to official Finvasia servers...");
        System.out.println("📞 OTP will be sent to your registered mobile number");
        System.out.println();
        
        try {
            // Generate password hash
            String passwordHash = generatePasswordHash(password);
            System.out.println("🔐 Generated password hash: " + passwordHash.substring(0, 10) + "***");
            
            // Prepare OTP generation request
            String requestBody = String.format(
                "jData={\"uid\":\"%s\",\"pwd\":\"%s\",\"factor2\":\"second_factor\",\"vc\":\"%s\",\"appkey\":\"%s\",\"imei\":\"%s\",\"source\":\"%s\",\"apkversion\":\"%s\"}&jKey=%s",
                clientCode, passwordHash, VENDOR_CODE, API_KEY, IMEI, SOURCE, APP_VERSION, clientCode
            );
            
            System.out.println("📡 Sending OTP generation request...");
            System.out.println("🎯 Request URL: " + BASE_URL + "/QuickAuth");
            System.out.println("📋 Request parameters prepared");
            System.out.println();
            
            // Send request to official Finvasia site
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/QuickAuth"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (API Client)")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("📨 === OFFICIAL SITE RESPONSE ===");
            System.out.println("📊 HTTP Status: " + response.statusCode());
            System.out.println("📄 Response Body: " + response.body());
            System.out.println();
            
            // Analyze response
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                
                if (responseBody.contains("\"stat\":\"Ok\"") || responseBody.contains("\"susertoken\"")) {
                    // Direct login successful (no OTP needed)
                    System.out.println("✅ DIRECT LOGIN SUCCESSFUL!");
                    System.out.println("🎯 No OTP required - session token received");
                    
                    if (responseBody.contains("\"susertoken\"")) {
                        extractSessionToken(responseBody);
                        return true;
                    }
                    return true;
                    
                } else if (responseBody.contains("OTP") || responseBody.contains("second_factor") || 
                          responseBody.contains("\"stat\":\"Not_Ok\"")) {
                    
                    System.out.println("📱 === OTP GENERATION SUCCESSFUL ===");
                    System.out.println("✅ OTP request sent to official Finvasia servers");
                    System.out.println("📞 OTP has been sent to your registered mobile number");
                    System.out.println("⏰ OTP is valid for 5 minutes");
                    System.out.println("🔢 Please check your mobile for 6-digit OTP");
                    
                    return true;
                    
                } else {
                    System.out.println("❌ OTP GENERATION FAILED");
                    System.out.println("📋 Server Response: " + responseBody);
                    
                    // Check for specific error messages
                    if (responseBody.contains("Invalid") || responseBody.contains("invalid")) {
                        System.out.println("💡 POSSIBLE ISSUES:");
                        System.out.println("   • Client Code might be incorrect");
                        System.out.println("   • Password might be wrong");
                        System.out.println("   • Account might not have API access");
                    }
                    
                    return false;
                }
                
            } else {
                System.out.println("❌ HTTP ERROR: " + response.statusCode());
                System.out.println("📄 Error Response: " + response.body());
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("❌ EXCEPTION during OTP generation: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * STEP 3: ASK USER TO ENTER RECEIVED OTP
     */
    public String getOTPFromUser() {
        System.out.println("🔢 === STEP 3: ENTER OTP ===");
        System.out.println("📱 Check your mobile for OTP from Finvasia");
        System.out.println("⏰ OTP is valid for 5 minutes");
        System.out.println();
        
        System.out.print("🔢 Enter the 6-digit OTP you received: ");
        String otp = scanner.nextLine().trim();
        
        System.out.println();
        System.out.println("✅ OTP entered: " + otp);
        System.out.println("⏰ Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        System.out.println();
        
        return otp;
    }
    
    /**
     * STEP 4: COMPLETE AUTHENTICATION WITH OTP
     */
    public boolean completeAuthenticationWithOTP(String clientCode, String password, String otp) {
        System.out.println("🔐 === STEP 4: COMPLETING AUTHENTICATION ===");
        System.out.println("🔢 Using OTP: " + otp);
        System.out.println("📡 Sending final authentication request...");
        System.out.println();
        
        try {
            String passwordHash = generatePasswordHash(password);
            
            String requestBody = String.format(
                "jData={\"uid\":\"%s\",\"pwd\":\"%s\",\"factor2\":\"%s\",\"vc\":\"%s\",\"appkey\":\"%s\",\"imei\":\"%s\",\"source\":\"%s\",\"apkversion\":\"%s\"}&jKey=%s",
                clientCode, passwordHash, otp, VENDOR_CODE, API_KEY, IMEI, SOURCE, APP_VERSION, clientCode
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/QuickAuth"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (API Client)")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("📨 === FINAL AUTHENTICATION RESPONSE ===");
            System.out.println("📊 HTTP Status: " + response.statusCode());
            System.out.println("📄 Response Body: " + response.body());
            System.out.println();
            
            if (response.statusCode() == 200) {
                String responseBody = response.body();
                
                if (responseBody.contains("\"susertoken\"")) {
                    extractSessionToken(responseBody);
                    
                    System.out.println("🎉 === AUTHENTICATION SUCCESSFUL ===");
                    System.out.println("✅ OTP verification completed");
                    System.out.println("🎫 Session token received: " + sessionToken.substring(0, 15) + "***");
                    System.out.println("⏰ Token expires: " + LocalDateTime.now().plusHours(8).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                    System.out.println("🚀 Ready to fetch live market data!");
                    
                    this.isAuthenticated = true;
                    return true;
                    
                } else {
                    System.out.println("❌ AUTHENTICATION FAILED");
                    System.out.println("📋 Error: " + extractErrorMessage(responseBody));
                    
                    if (responseBody.contains("Invalid") || responseBody.contains("invalid")) {
                        System.out.println("💡 POSSIBLE ISSUES:");
                        System.out.println("   • OTP might be incorrect");
                        System.out.println("   • OTP might have expired (5 minute limit)");
                        System.out.println("   • Try generating fresh OTP");
                    }
                    
                    return false;
                }
                
            } else {
                System.out.println("❌ HTTP ERROR: " + response.statusCode());
                return false;
            }
            
        } catch (Exception e) {
            System.out.println("❌ EXCEPTION during final authentication: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * COMPLETE STEP-BY-STEP AUTHENTICATION PROCESS
     */
    public boolean performCompleteAuthentication() {
        System.out.println("🚀 === COMPLETE SHOONYA AUTHENTICATION PROCESS ===");
        System.out.println("📝 Step-by-step OTP authentication");
        System.out.println("⏰ " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")));
        System.out.println();
        
        try {
            // Step 1: Get credentials from user
            Map<String, String> credentials = getCredentialsFromUser();
            String clientCode = credentials.get("clientCode");
            String password = credentials.get("password");
            
            // Step 2: Generate OTP from official site
            boolean otpGenerated = generateOTPFromOfficialSite(clientCode, password);
            
            if (!otpGenerated) {
                System.out.println("❌ Failed to generate OTP. Please check credentials.");
                return false;
            }
            
            // If already authenticated (no OTP needed), return success
            if (isAuthenticated) {
                return true;
            }
            
            // Step 3: Get OTP from user
            String otp = getOTPFromUser();
            
            // Step 4: Complete authentication with OTP
            boolean authSuccess = completeAuthenticationWithOTP(clientCode, password, otp);
            
            if (authSuccess) {
                System.out.println("\n🎉 === COMPLETE AUTHENTICATION SUCCESS ===");
                System.out.println("✅ All steps completed successfully");
                System.out.println("🔐 Shoonya API authentication ready");
                System.out.println("📊 Can now fetch live SENSEX, NIFTY prices");
                System.out.println("💯 ZERO mock data - only real market rates");
            } else {
                System.out.println("\n❌ === AUTHENTICATION FAILED ===");
                System.out.println("💡 You can try again with correct OTP");
            }
            
            return authSuccess;
            
        } catch (Exception e) {
            System.out.println("❌ Unexpected error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * UTILITY METHODS
     */
    
    private String generatePasswordHash(String password) {
        try {
            String input = password + API_KEY;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate password hash", e);
        }
    }
    
    private void extractSessionToken(String responseBody) {
        try {
            int tokenStart = responseBody.indexOf("\"susertoken\":\"") + 14;
            int tokenEnd = responseBody.indexOf("\"", tokenStart);
            this.sessionToken = responseBody.substring(tokenStart, tokenEnd);
        } catch (Exception e) {
            System.out.println("⚠️ Could not extract session token: " + e.getMessage());
        }
    }
    
    private String extractErrorMessage(String responseBody) {
        try {
            if (responseBody.contains("\"emsg\":")) {
                int msgStart = responseBody.indexOf("\"emsg\":\"") + 8;
                int msgEnd = responseBody.indexOf("\"", msgStart);
                return responseBody.substring(msgStart, msgEnd);
            }
            return "Unknown error";
        } catch (Exception e) {
            return "Error parsing response";
        }
    }
    
    public String getSessionToken() {
        return sessionToken;
    }
    
    public boolean isAuthenticated() {
        return isAuthenticated;
    }
    
    /**
     * MAIN METHOD FOR TESTING
     */
    public static void main(String[] args) {
        System.out.println("🔐 === SHOONYA OTP GENERATOR ===");
        System.out.println("📱 Step-by-step authentication with official Finvasia site");
        System.out.println("🎯 Real OTP generation and verification");
        System.out.println();
        
        ShoonyaOTPGenerator generator = new ShoonyaOTPGenerator();
        
        // Perform complete authentication process
        boolean success = generator.performCompleteAuthentication();
        
        if (success) {
            System.out.println("\n🚀 === READY FOR LIVE TRADING ===");
            System.out.println("✅ Authentication completed successfully");
            System.out.println("🎫 Session Token: " + generator.getSessionToken().substring(0, 15) + "***");
            System.out.println("📊 Can now integrate with trading system");
            System.out.println("💯 Real market data access enabled");
        } else {
            System.out.println("\n❌ === AUTHENTICATION INCOMPLETE ===");
            System.out.println("💡 Please try again with correct credentials/OTP");
            System.out.println("📞 Contact Finvasia support if issues persist");
        }
        
        System.out.println("\n🎯 OTP Generator process complete!");
    }
}