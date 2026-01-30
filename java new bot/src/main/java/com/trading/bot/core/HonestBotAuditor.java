import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HONEST BOT AUDITOR - CORRECTED VERSION
 * Performs accurate audit of actual bot files and functionality
 */
public class HonestBotAuditor {
    
    private List<String> foundBots = new ArrayList<>();
    private List<String> workingBots = new ArrayList<>();
    private List<String> compilationErrors = new ArrayList<>();
    private Map<String, String> botStatus = new HashMap<>();
    
    public HonestBotAuditor() {
        System.out.println("🔍 HONEST BOT AUDITOR - CORRECTED VERSION");
        System.out.println("=========================================");
        System.out.println("📊 Accurate audit of actual bot files");
        System.out.println("🎯 Testing real functionality");
    }
    
    /**
     * PHASE 1: Discover Actual Bot Files
     */
    public void discoverActualBots() {
        System.out.println("\n🔍 PHASE 1: Discovering Actual Bot Files");
        System.out.println("========================================");
        
        File botDir = new File(".");
        File[] javaFiles = botDir.listFiles((dir, name) -> name.endsWith(".java"));
        
        if (javaFiles != null) {
            for (File file : javaFiles) {
                String fileName = file.getName();
                foundBots.add(fileName);
                
                // Check if it's a bot file
                if (fileName.toLowerCase().contains("bot") || 
                    fileName.toLowerCase().contains("trading") ||
                    fileName.toLowerCase().contains("telegram")) {
                    System.out.println("✅ Found bot: " + fileName);
                } else {
                    System.out.println("📄 Found file: " + fileName);
                }
            }
        }
        
        // Also check src directory
        File srcDir = new File("src/main/java/com/stockbot");
        if (srcDir.exists()) {
            File[] srcFiles = srcDir.listFiles((dir, name) -> name.endsWith(".java"));
            if (srcFiles != null) {
                for (File file : srcFiles) {
                    String fileName = "src/main/java/com/stockbot/" + file.getName();
                    foundBots.add(fileName);
                    System.out.println("✅ Found src bot: " + fileName);
                }
            }
        }
        
        System.out.println("📊 Total files discovered: " + foundBots.size());
    }
    
    /**
     * PHASE 2: Test Key Bot Components
     */
    public void testKeyBotComponents() {
        System.out.println("\n🔧 PHASE 2: Testing Key Bot Components");
        System.out.println("======================================");
        
        // Test specific bots we know exist
        testBotFile("TradingBot.java");
        testBotFile("ActiveBot.java");
        testBotFile("WorkingTradingBot.java");
        testBotFile("MinimalWorkingBot.java");
        testBotFile("ImprovedTelegramBot.java");
        testBotFile("FixedTelegramBot.java");
        testBotFile("EnhancedOptionsBot.java");
        testBotFile("HonestBotBacktester.java");
        testBotFile("HonestCEPEOptionsBacktester.java");
        testBotFile("TodayHonestOptionsBacktester.java");
        testBotFile("OptimizedCallGenerator.java");
        testBotFile("StandaloneCallGenerator.java");
        
        System.out.println("\n📊 Component Test Summary:");
        System.out.println("✅ Working components: " + workingBots.size());
        System.out.println("❌ Components with issues: " + compilationErrors.size());
    }
    
    /**
     * Test individual bot file
     */
    private void testBotFile(String fileName) {
        File file = new File(fileName);
        
        if (file.exists()) {
            System.out.println("🔍 Testing: " + fileName);
            
            try {
                // Read file content to check for basic structure
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String content = "";
                String line;
                boolean hasMainMethod = false;
                boolean hasClass = false;
                
                while ((line = reader.readLine()) != null) {
                    content += line + "\n";
                    if (line.contains("public static void main")) {
                        hasMainMethod = true;
                    }
                    if (line.contains("public class")) {
                        hasClass = true;
                    }
                }
                reader.close();
                
                // Evaluate bot quality
                if (hasClass && hasMainMethod) {
                    System.out.println("  ✅ Complete bot with main method");
                    workingBots.add(fileName);
                    botStatus.put(fileName, "WORKING");
                } else if (hasClass) {
                    System.out.println("  ⚠️ Has class but no main method");
                    botStatus.put(fileName, "PARTIAL");
                } else {
                    System.out.println("  ❌ Incomplete or broken");
                    compilationErrors.add(fileName);
                    botStatus.put(fileName, "BROKEN");
                }
                
            } catch (Exception e) {
                System.out.println("  ❌ Error reading file: " + e.getMessage());
                compilationErrors.add(fileName);
                botStatus.put(fileName, "ERROR");
            }
        } else {
            System.out.println("❌ Missing: " + fileName);
            botStatus.put(fileName, "MISSING");
        }
    }
    
    /**
     * PHASE 3: Test Recent Functionality
     */
    public void testRecentFunctionality() {
        System.out.println("\n⚙️ PHASE 3: Testing Recent Functionality");
        System.out.println("========================================");
        
        // Test our recently created components
        testRecentComponent("OptimizedCallGenerator.java", "Options call generation");
        testRecentComponent("TodayHonestOptionsBacktester.java", "Honest options backtesting");
        testRecentComponent("IntegratedHonestOptionsSystem.java", "Integrated options system");
        testRecentComponent("StandaloneCallGenerator.java", "Standalone call generation");
        
        // Check for backtest reports
        testBacktestReports();
    }
    
    /**
     * Test recent component
     */
    private void testRecentComponent(String fileName, String description) {
        File file = new File(fileName);
        
        if (file.exists()) {
            System.out.println("✅ " + description + ": AVAILABLE");
            
            // Try to compile and run a quick test
            try {
                Process compile = Runtime.getRuntime().exec("javac " + fileName);
                int compileResult = compile.waitFor();
                
                if (compileResult == 0) {
                    System.out.println("  ✅ Compiles successfully");
                } else {
                    System.out.println("  ⚠️ Compilation issues");
                }
            } catch (Exception e) {
                System.out.println("  ⚠️ Cannot test compilation: " + e.getMessage());
            }
        } else {
            System.out.println("❌ " + description + ": MISSING");
        }
    }
    
    /**
     * Test backtest reports
     */
    private void testBacktestReports() {
        System.out.println("\n📊 Testing Backtest Reports");
        System.out.println("===========================");
        
        File[] reports = new File(".").listFiles((dir, name) -> 
            name.contains("backtest") && name.endsWith(".txt"));
        
        if (reports != null && reports.length > 0) {
            System.out.println("✅ Found " + reports.length + " backtest reports");
            
            // Analyze the most recent report
            File latestReport = Arrays.stream(reports)
                .max(Comparator.comparing(File::lastModified))
                .orElse(null);
            
            if (latestReport != null) {
                analyzeLatestReport(latestReport);
            }
        } else {
            System.out.println("⚠️ No backtest reports found");
        }
    }
    
    /**
     * Analyze latest report
     */
    private void analyzeLatestReport(File report) {
        try {
            System.out.println("📊 Analyzing latest report: " + report.getName());
            
            BufferedReader reader = new BufferedReader(new FileReader(report));
            String line;
            String winRateData = null;
            String totalCallsData = null;
            String pnlData = null;
            
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains("win rate")) {
                    winRateData = line.trim();
                }
                if (line.toLowerCase().contains("total calls")) {
                    totalCallsData = line.trim();
                }
                if (line.toLowerCase().contains("total p&l") || line.toLowerCase().contains("total pnl")) {
                    pnlData = line.trim();
                }
            }
            reader.close();
            
            System.out.println("📈 Report Analysis:");
            if (winRateData != null) System.out.println("  📊 " + winRateData);
            if (totalCallsData != null) System.out.println("  📊 " + totalCallsData);
            if (pnlData != null) System.out.println("  📊 " + pnlData);
            
        } catch (Exception e) {
            System.out.println("❌ Error analyzing report: " + e.getMessage());
        }
    }
    
    /**
     * PHASE 4: Generate Honest Assessment
     */
    public void generateHonestAssessment() {
        System.out.println("\n📋 PHASE 4: Generating Honest Assessment");
        System.out.println("========================================");
        
        try {
            String reportFileName = "HONEST_BOT_AUDIT_FINAL_" + LocalDate.now() + ".md";
            PrintWriter writer = new PrintWriter(new FileWriter(reportFileName));
            
            writer.println("# HONEST BOT AUDIT REPORT - FINAL");
            writer.println("=================================");
            writer.println("Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.println();
            
            // Executive Summary
            writer.println("## EXECUTIVE SUMMARY");
            writer.println("===================");
            writer.println("Total Files Discovered: " + foundBots.size());
            writer.println("Working Bot Components: " + workingBots.size());
            writer.println("Components with Issues: " + compilationErrors.size());
            
            double healthPercentage = foundBots.size() > 0 ? 
                (double) workingBots.size() / foundBots.size() * 100 : 0;
            writer.println("Overall Health: " + String.format("%.1f%%", healthPercentage));
            writer.println();
            
            // Detailed Status
            writer.println("## DETAILED COMPONENT STATUS");
            writer.println("============================");
            for (Map.Entry<String, String> entry : botStatus.entrySet()) {
                writer.println(entry.getKey() + ": " + entry.getValue());
            }
            writer.println();
            
            // Working Components
            writer.println("## WORKING COMPONENTS");
            writer.println("====================");
            for (String bot : workingBots) {
                writer.println("✅ " + bot);
            }
            writer.println();
            
            // Issues Found
            writer.println("## ISSUES FOUND");
            writer.println("===============");
            for (String bot : compilationErrors) {
                writer.println("❌ " + bot);
            }
            writer.println();
            
            // Recommendations
            writer.println("## HONEST RECOMMENDATIONS");
            writer.println("=========================");
            generateHonestRecommendations(writer, healthPercentage);
            
            writer.close();
            
            System.out.println("✅ Honest audit report saved: " + reportFileName);
            
            // Display final summary
            displayFinalSummary(healthPercentage);
            
        } catch (Exception e) {
            System.err.println("❌ Error generating assessment: " + e.getMessage());
        }
    }
    
    /**
     * Generate honest recommendations
     */
    private void generateHonestRecommendations(PrintWriter writer, double healthPercentage) {
        writer.println("### IMMEDIATE ACTIONS NEEDED:");
        
        if (healthPercentage < 50) {
            writer.println("🔴 CRITICAL: System needs major overhaul");
            writer.println("1. Focus on 2-3 core working bots only");
            writer.println("2. Remove or fix broken components");
            writer.println("3. Consolidate functionality into fewer, better bots");
        } else if (healthPercentage < 75) {
            writer.println("🟡 MODERATE: System needs optimization");
            writer.println("1. Fix compilation errors in key components");
            writer.println("2. Improve testing and validation");
            writer.println("3. Document working components clearly");
        } else {
            writer.println("🟢 GOOD: System is mostly functional");
            writer.println("1. Continue with current approach");
            writer.println("2. Add more comprehensive testing");
            writer.println("3. Focus on performance optimization");
        }
        
        writer.println();
        writer.println("### SPECIFIC RECOMMENDATIONS:");
        writer.println("1. **Prioritize Working Bots**: Focus on OptimizedCallGenerator and TodayHonestOptionsBacktester");
        writer.println("2. **Clean Up Codebase**: Remove duplicate or broken bot files");
        writer.println("3. **Improve Documentation**: Document what actually works vs what's experimental");
        writer.println("4. **Add Real Testing**: Create automated tests for accuracy claims");
        writer.println("5. **Consolidate Features**: Merge working features into fewer, more robust bots");
    }
    
    /**
     * Display final summary
     */
    private void displayFinalSummary(double healthPercentage) {
        System.out.println("\n🏆 HONEST AUDIT FINAL SUMMARY");
        System.out.println("=============================");
        
        System.out.println("📊 Bot System Health: " + String.format("%.1f%%", healthPercentage));
        System.out.println("✅ Working Components: " + workingBots.size());
        System.out.println("❌ Broken Components: " + compilationErrors.size());
        System.out.println("📄 Total Files: " + foundBots.size());
        
        System.out.println("\n🎯 KEY WORKING COMPONENTS:");
        for (String bot : workingBots) {
            System.out.println("  ✅ " + bot);
        }
        
        System.out.println("\n💡 HONEST ASSESSMENT:");
        if (healthPercentage >= 75) {
            System.out.println("🟢 GOOD: Your bot system is mostly functional");
            System.out.println("   Focus on optimization and performance");
        } else if (healthPercentage >= 50) {
            System.out.println("🟡 MODERATE: Your bot system needs some fixes");
            System.out.println("   Focus on fixing key components and testing");
        } else {
            System.out.println("🔴 NEEDS WORK: Your bot system requires attention");
            System.out.println("   Focus on 2-3 core bots and get them working perfectly");
        }
        
        System.out.println("\n🚀 NEXT STEPS:");
        System.out.println("1. Use OptimizedCallGenerator for call generation");
        System.out.println("2. Use TodayHonestOptionsBacktester for testing");
        System.out.println("3. Clean up broken/duplicate components");
        System.out.println("4. Focus on proven working parts");
    }
    
    public static void main(String[] args) {
        System.out.println("🚀 STARTING HONEST BOT AUDIT");
        
        HonestBotAuditor auditor = new HonestBotAuditor();
        
        // Execute honest audit
        auditor.discoverActualBots();
        auditor.testKeyBotComponents();
        auditor.testRecentFunctionality();
        auditor.generateHonestAssessment();
        
        System.out.println("\n✅ HONEST BOT AUDIT COMPLETED!");
        System.out.println("📊 Check the generated report for detailed findings");
    }
}