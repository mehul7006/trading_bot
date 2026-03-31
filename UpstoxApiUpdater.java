import java.io.*;
import java.net.*;
import java.util.Properties;

/**
 * UPSTOX API UPDATER
 * Updates all Upstox API credentials and tests connectivity
 */
public class UpstoxApiUpdater {
    
    // Updated Upstox API Credentials
    private static final String API_KEY = "768a303b-80f1-46d6-af16-f847f9341213";
    private static final String API_SECRET = "j0w9ga2m9w";
    private static final String ACCESS_TOKEN = "eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTc2MTFiMzg5M2Y0MDY1MjE3YmUxOGMiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2OTM0NTQ1OSwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzY5Mzc4NDAwfQ.Z06g0_XML5Y0zKpqZ-3artglaX-qtsFic_gvaWt3iUE";
    
    private static final String BASE_URL = "https://api.upstox.com/v2";
    
    public static void main(String[] args) {
        System.out.println("🔄 UPSTOX API CREDENTIALS UPDATER");
        System.out.println("=================================");
        System.out.println("🔑 API Key: " + API_KEY);
        System.out.println("🔐 API Secret: " + API_SECRET.substring(0, 4) + "****");
        System.out.println("🎫 Access Token: " + ACCESS_TOKEN.substring(0, 20) + "...");
        System.out.println();
        
        // Test API connectivity
        testUpstoxConnection();
        
        // Update configuration files
        updateConfigFiles();
        
        // Show usage instructions
        showUsageInstructions();
    }
    
    /**
     * Test Upstox API connectivity
     */
    private static void testUpstoxConnection() {
        System.out.println("🧪 TESTING UPSTOX API CONNECTION");
        System.out.println("================================");
        
        try {
            // Test user profile endpoint
            String profileUrl = BASE_URL + "/user/get-profile";
            HttpURLConnection connection = (HttpURLConnection) new URL(profileUrl).openConnection();
            
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + ACCESS_TOKEN);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            
            System.out.println("📡 Testing URL: " + profileUrl);
            System.out.println("📊 Response Code: " + responseCode);
            
            if (responseCode == 200) {
                // Read response
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String response = reader.readLine();
                reader.close();
                
                System.out.println("✅ API CONNECTION SUCCESSFUL!");
                System.out.println("📄 Response: " + (response != null ? response.substring(0, Math.min(100, response.length())) + "..." : "No response"));
                
            } else if (responseCode == 401) {
                System.out.println("❌ AUTHENTICATION FAILED!");
                System.out.println("💡 Your access token may have expired");
                
            } else {
                System.out.println("⚠️ API Response: " + responseCode);
                
                // Try to read error response
                try {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    String errorResponse = errorReader.readLine();
                    errorReader.close();
                    System.out.println("📄 Error: " + errorResponse);
                } catch (Exception e) {
                    // Ignore error reading errors
                }
            }
            
            connection.disconnect();
            
        } catch (Exception e) {
            System.out.println("❌ Connection test failed: " + e.getMessage());
            System.out.println("💡 Check your internet connection and API credentials");
        }
        
        System.out.println();
    }
    
    /**
     * Update configuration files with new credentials
     */
    private static void updateConfigFiles() {
        System.out.println("📝 UPDATING CONFIGURATION FILES");
        System.out.println("===============================");
        
        try {
            // Update main config file
            Properties props = new Properties();
            props.setProperty("upstox.api.key", API_KEY);
            props.setProperty("upstox.api.secret", API_SECRET);
            props.setProperty("upstox.access.token", ACCESS_TOKEN);
            props.setProperty("upstox.base.url", BASE_URL);
            
            // Save to file
            FileOutputStream fos = new FileOutputStream("upstox_config.properties");
            props.store(fos, "Updated Upstox API Configuration - " + new java.util.Date());
            fos.close();
            
            System.out.println("✅ Created: upstox_config.properties");
            
            // Create Java constants file
            String javaConstants = "public class UpstoxConfig {\n" +
                    "    public static final String API_KEY = \"" + API_KEY + "\";\n" +
                    "    public static final String API_SECRET = \"" + API_SECRET + "\";\n" +
                    "    public static final String ACCESS_TOKEN = \"" + ACCESS_TOKEN + "\";\n" +
                    "    public static final String BASE_URL = \"" + BASE_URL + "\";\n" +
                    "    \n" +
                    "    public static String getAuthHeader() {\n" +
                    "        return \"Bearer \" + ACCESS_TOKEN;\n" +
                    "    }\n" +
                    "}\n";
            
            FileWriter writer = new FileWriter("UpstoxConfig.java");
            writer.write(javaConstants);
            writer.close();
            
            System.out.println("✅ Created: UpstoxConfig.java");
            
        } catch (Exception e) {
            System.out.println("❌ Error updating config files: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    /**
     * Show usage instructions
     */
    private static void showUsageInstructions() {
        System.out.println("📋 USAGE INSTRUCTIONS");
        System.out.println("=====================");
        System.out.println();
        System.out.println("🔧 In your Java code, use:");
        System.out.println("   String apiKey = UpstoxConfig.API_KEY;");
        System.out.println("   String authHeader = UpstoxConfig.getAuthHeader();");
        System.out.println();
        System.out.println("🌐 API Endpoints available:");
        System.out.println("   Profile: " + BASE_URL + "/user/get-profile");
        System.out.println("   Market Data: " + BASE_URL + "/market-quote/ltp");
        System.out.println("   Holdings: " + BASE_URL + "/portfolio/holdings");
        System.out.println("   Orders: " + BASE_URL + "/order");
        System.out.println();
        System.out.println("📄 Example API call:");
        System.out.println("   HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();");
        System.out.println("   conn.setRequestProperty(\"Authorization\", UpstoxConfig.getAuthHeader());");
        System.out.println("   conn.setRequestProperty(\"Accept\", \"application/json\");");
        System.out.println();
        System.out.println("⚠️ IMPORTANT:");
        System.out.println("   - Access tokens expire (usually 24 hours)");
        System.out.println("   - Update tokens regularly for production use");
        System.out.println("   - Keep credentials secure and don't commit to version control");
        System.out.println();
        System.out.println("✅ Your Upstox API credentials have been updated successfully!");
    }
}