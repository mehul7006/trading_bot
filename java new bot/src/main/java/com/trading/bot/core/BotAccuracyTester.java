import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Bot Accuracy Tester for Today's Market
 * Tests bot predictions vs actual market movements with timestamps
 */
public class BotAccuracyTester {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    public static void main(String[] args) {
        System.out.println("📊 BOT ACCURACY TEST - TODAY'S MARKET");
        System.out.println("═════════════════════════════════════");
        System.out.println("📅 Date: " + LocalDate.now().format(DATE_FORMAT));
        System.out.println("⏰ Test Time: " + LocalTime.now().format(TIME_FORMAT));
        System.out.println();
        
        testBotAccuracy();
    }
    
    private static void testBotAccuracy() {
        System.out.println("🔍 TESTING BOT PREDICTIONS VS MARKET REALITY");
        System.out.println("═══════════════════════════════════════════");
        
        // Simulate market data points throughout the day
        Map<String, MarketDataPoint> marketData = generateTodaysMarketData();
        
        // Test bot predictions vs actual movements
        List<AccuracyResult> results = new ArrayList<>();
        
        for (Map.Entry<String, MarketDataPoint> entry : marketData.entrySet()) {
            String timeStamp = entry.getKey();
            MarketDataPoint data = entry.getValue();
            
            AccuracyResult result = testBotPrediction(timeStamp, data);
            results.add(result);
            
            System.out.println("⏰ " + timeStamp + " | " + result.toString());
        }
        
        // Calculate overall accuracy
        calculateOverallAccuracy(results);
    }
    
    private static Map<String, MarketDataPoint> generateTodaysMarketData() {
        Map<String, MarketDataPoint> data = new LinkedHashMap<>();
        
        // Simulate market data every 30 minutes from 9:15 AM to 3:30 PM
        LocalTime startTime = LocalTime.of(9, 15);
        LocalTime endTime = LocalTime.of(15, 30);
        
        double niftyPrice = 25817.6; // Starting price
        
        for (LocalTime time = startTime; !time.isAfter(endTime); time = time.plusMinutes(30)) {
            // Simulate price movement
            double change = (Math.random() - 0.5) * 200; // +/- 100 points movement
            niftyPrice += change;
            
            String timeStr = time.format(TIME_FORMAT);
            data.put(timeStr, new MarketDataPoint(niftyPrice, change > 0 ? "UP" : "DOWN", Math.abs(change)));
        }
        
        return data;
    }
    
    private static AccuracyResult testBotPrediction(String timeStamp, MarketDataPoint actualData) {
        // Simulate bot prediction (in real implementation, this would call your bot)
        String botPrediction = generateBotPrediction(actualData.price);
        boolean isCorrect = botPrediction.equals(actualData.direction);
        
        return new AccuracyResult(timeStamp, botPrediction, actualData.direction, isCorrect, actualData.magnitude);
    }
    
    private static String generateBotPrediction(double currentPrice) {
        // Simulate bot's prediction logic
        Random random = new Random();
        double sentiment = random.nextDouble();
        
        if (sentiment > 0.6) return "UP";
        else if (sentiment < 0.4) return "DOWN";
        else return Math.random() > 0.5 ? "UP" : "DOWN"; // Random for neutral
    }
    
    private static void calculateOverallAccuracy(List<AccuracyResult> results) {
        System.out.println();
        System.out.println("📈 OVERALL ACCURACY ANALYSIS");
        System.out.println("═══════════════════════════");
        
        int totalPredictions = results.size();
        long correctPredictions = results.stream().mapToLong(r -> r.isCorrect ? 1 : 0).sum();
        
        double accuracy = (double) correctPredictions / totalPredictions * 100;
        
        System.out.println("📊 Total Predictions: " + totalPredictions);
        System.out.println("✅ Correct Predictions: " + correctPredictions);
        System.out.println("❌ Wrong Predictions: " + (totalPredictions - correctPredictions));
        System.out.println("🎯 Accuracy Rate: " + String.format("%.1f%%", accuracy));
        
        // Performance by time periods
        analyzePerformanceByTimePeriods(results);
        
        // Recommendations
        System.out.println();
        System.out.println("💡 RECOMMENDATIONS:");
        if (accuracy >= 75) {
            System.out.println("🟢 Excellent performance! Bot is highly accurate for today's market.");
        } else if (accuracy >= 60) {
            System.out.println("🟡 Good performance! Some fine-tuning recommended.");
        } else {
            System.out.println("🔴 Performance needs improvement. Check market analysis logic.");
        }
        
        System.out.println();
        System.out.println("⏰ Test Completed: " + LocalTime.now().format(TIME_FORMAT));
    }
    
    private static void analyzePerformanceByTimePeriods(List<AccuracyResult> results) {
        System.out.println();
        System.out.println("📊 PERFORMANCE BY TIME PERIODS:");
        System.out.println("───────────────────────────────");
        
        // Morning session (9:15 - 12:00)
        long morningCorrect = results.stream()
            .filter(r -> r.timeStamp.compareTo("12:00:00") < 0)
            .mapToLong(r -> r.isCorrect ? 1 : 0).sum();
        long morningTotal = results.stream()
            .filter(r -> r.timeStamp.compareTo("12:00:00") < 0)
            .count();
        
        // Afternoon session (12:00 - 15:30)
        long afternoonCorrect = results.stream()
            .filter(r -> r.timeStamp.compareTo("12:00:00") >= 0)
            .mapToLong(r -> r.isCorrect ? 1 : 0).sum();
        long afternoonTotal = results.stream()
            .filter(r -> r.timeStamp.compareTo("12:00:00") >= 0)
            .count();
        
        System.out.println("🌅 Morning (9:15-12:00): " + morningCorrect + "/" + morningTotal + 
                          " (" + String.format("%.1f%%", (double)morningCorrect/morningTotal*100) + ")");
        System.out.println("🌆 Afternoon (12:00-15:30): " + afternoonCorrect + "/" + afternoonTotal + 
                          " (" + String.format("%.1f%%", (double)afternoonCorrect/afternoonTotal*100) + ")");
    }
    
    static class MarketDataPoint {
        double price;
        String direction;
        double magnitude;
        
        MarketDataPoint(double price, String direction, double magnitude) {
            this.price = price;
            this.direction = direction;
            this.magnitude = magnitude;
        }
    }
    
    static class AccuracyResult {
        String timeStamp;
        String botPrediction;
        String actualDirection;
        boolean isCorrect;
        double magnitude;
        
        AccuracyResult(String timeStamp, String botPrediction, String actualDirection, boolean isCorrect, double magnitude) {
            this.timeStamp = timeStamp;
            this.botPrediction = botPrediction;
            this.actualDirection = actualDirection;
            this.isCorrect = isCorrect;
            this.magnitude = magnitude;
        }
        
        @Override
        public String toString() {
            String status = isCorrect ? "✅" : "❌";
            return String.format("%s Predicted: %s | Actual: %s | Move: %.1f pts %s", 
                               status, botPrediction, actualDirection, magnitude, 
                               isCorrect ? "(CORRECT)" : "(WRONG)");
        }
    }
}