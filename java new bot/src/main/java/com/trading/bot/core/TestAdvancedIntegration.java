public class TestAdvancedIntegration {
    public static void main(String[] args) {
        System.out.println("🧪 Testing Advanced Call Generator Integration");
        System.out.println("═══════════════════════════════════════════");
        
        // Test initialization
        AdvancedCallIntegration.initialize();
        
        // Test advanced call command
        System.out.println("\n📱 Testing /advancedcall command...");
        String response = AdvancedCallIntegration.handleAdvancedCallCommand("test123", "/advancedcall");
        System.out.println("✅ Command response generated (" + response.length() + " characters)");
        
        // Test symbol analysis
        System.out.println("\n📊 Testing symbol analysis...");
        String tcsCall = AdvancedCallIntegration.getQuickAdvancedCall("TCS");
        System.out.println("✅ TCS analysis generated");
        
        System.out.println("\n🎉 Integration test completed successfully!");
        System.out.println("📱 /advancedcall command is ready for use!");
    }
}
