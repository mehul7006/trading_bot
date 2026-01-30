package com.stockbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.*;
import java.nio.file.*;

/**
 * OPTION 4: PERFORMANCE ANALYSIS - Comprehensive Performance Tracking & Optimization
 * Advanced backtesting, performance metrics, and strategy optimization
 */
public class PerformanceAnalysisEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceAnalysisEngine.class);
    
    // Performance tracking
    private final Map<String, List<TradeResult>> tradeHistory = new ConcurrentHashMap<>();
    private final Map<String, StrategyMetrics> strategyMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<PredictionResult>> predictionHistory = new ConcurrentHashMap<>();
    
    // Backtesting engine
    private final BacktestingEngine backtestEngine;
    private final RiskAnalyzer riskAnalyzer;
    private final PerformanceOptimizer optimizer;
    
    // Report generation
    private final ReportGenerator reportGenerator;
    
    public PerformanceAnalysisEngine() {
        this.backtestEngine = new BacktestingEngine();
        this.riskAnalyzer = new RiskAnalyzer();
        this.optimizer = new PerformanceOptimizer();
        this.reportGenerator = new ReportGenerator();
        
        initializeMetricsTracking();
    }
    
    /**
     * COMPREHENSIVE BACKTESTING SYSTEM
     */
    
    /**
     * Run comprehensive backtest with multiple strategies
     */
    public BacktestReport runComprehensiveBacktest(List<IndexData> historicalData, 
                                                 List<String> strategies, 
                                                 Map<String, Object> parameters) {
        
        logger.info("🔄 Starting comprehensive backtest with {} strategies over {} data points", 
            strategies.size(), historicalData.size());
        
        BacktestReport report = new BacktestReport();
        
        for (String strategy : strategies) {
            StrategyBacktestResult result = backtestEngine.backtestStrategy(
                strategy, historicalData, parameters);
            
            report.addStrategyResult(strategy, result);
            
            logger.info("📊 Strategy {} backtest complete: Return={:.2f}%, Sharpe={:.2f}, MaxDD={:.2f}%", 
                strategy, result.getTotalReturn() * 100, result.getSharpeRatio(), result.getMaxDrawdown() * 100);
        }
        
        // Generate comparative analysis
        report.setComparativeAnalysis(generateComparativeAnalysis(report.getStrategyResults()));
        
        // Risk analysis
        report.setRiskAnalysis(riskAnalyzer.analyzePortfolioRisk(report.getStrategyResults()));
        
        // Optimization recommendations
        report.setOptimizationRecommendations(optimizer.generateRecommendations(report));
        
        logger.info("✅ Comprehensive backtest completed");
        
        return report;
    }
    
    /**
     * Walk-forward analysis for robust validation
     */
    public WalkForwardResult runWalkForwardAnalysis(List<IndexData> data, String strategy, 
                                                  int trainingPeriod, int testPeriod) {
        
        logger.info("🚶 Starting walk-forward analysis: Training={}, Test={}", trainingPeriod, testPeriod);
        
        List<BacktestPeriodResult> results = new ArrayList<>();
        
        for (int i = trainingPeriod; i < data.size() - testPeriod; i += testPeriod) {
            // Training data
            List<IndexData> trainingData = data.subList(i - trainingPeriod, i);
            
            // Test data
            List<IndexData> testData = data.subList(i, i + testPeriod);
            
            // Train strategy on training data
            Map<String, Object> optimizedParams = optimizer.optimizeStrategy(strategy, trainingData);
            
            // Test on out-of-sample data
            StrategyBacktestResult testResult = backtestEngine.backtestStrategy(
                strategy, testData, optimizedParams);
            
            BacktestPeriodResult periodResult = new BacktestPeriodResult(
                data.get(i).getTimestamp(), data.get(i + testPeriod - 1).getTimestamp(),
                testResult, optimizedParams);
            
            results.add(periodResult);
            
            logger.debug("📈 Walk-forward period {}: Return={:.2f}%", 
                results.size(), testResult.getTotalReturn() * 100);
        }
        
        WalkForwardResult walkForwardResult = new WalkForwardResult(results);
        
        logger.info("✅ Walk-forward analysis completed: Avg Return={:.2f}%, Consistency={:.2f}%", 
            walkForwardResult.getAverageReturn() * 100, walkForwardResult.getConsistency() * 100);
        
        return walkForwardResult;
    }
    
    /**
     * Monte Carlo simulation for risk assessment
     */
    public MonteCarloResult runMonteCarloSimulation(String strategy, 
                                                  List<IndexData> baseData, 
                                                  int simulations) {
        
        logger.info("🎲 Starting Monte Carlo simulation with {} runs", simulations);
        
        List<Double> returns = new ArrayList<>();
        List<Double> maxDrawdowns = new ArrayList<>();
        List<Double> sharpeRatios = new ArrayList<>();
        
        for (int i = 0; i < simulations; i++) {
            // Generate random market scenario
            List<IndexData> simulatedData = generateRandomScenario(baseData);
            
            // Run backtest on simulated data
            StrategyBacktestResult result = backtestEngine.backtestStrategy(
                strategy, simulatedData, new HashMap<>());
            
            returns.add(result.getTotalReturn());
            maxDrawdowns.add(result.getMaxDrawdown());
            sharpeRatios.add(result.getSharpeRatio());
            
            if (i % 100 == 0) {
                logger.debug("🎲 Monte Carlo progress: {}/{}", i, simulations);
            }
        }
        
        MonteCarloResult result = new MonteCarloResult(returns, maxDrawdowns, sharpeRatios);
        
        logger.info("✅ Monte Carlo completed: Expected Return={:.2f}%, VaR 95%={:.2f}%", 
            result.getExpectedReturn() * 100, result.getValueAtRisk95() * 100);
        
        return result;
    }
    
    /**
     * REAL-TIME PERFORMANCE TRACKING
     */
    
    /**
     * Track live trading performance
     */
    public void recordTrade(String strategy, TradeResult trade) {
        tradeHistory.computeIfAbsent(strategy, k -> new ArrayList<>()).add(trade);
        
        // Update strategy metrics
        updateStrategyMetrics(strategy, trade);
        
        // Check for performance alerts
        checkPerformanceAlerts(strategy);
        
        logger.info("📝 Trade recorded: {} | P&L: {:.2f} | Strategy: {}", 
            trade.getSymbol(), trade.getPnl(), strategy);
    }
    
    /**
     * Track prediction accuracy
     */
    public void recordPrediction(String model, PredictionResult prediction) {
        predictionHistory.computeIfAbsent(model, k -> new ArrayList<>()).add(prediction);
        
        // Calculate rolling accuracy
        double accuracy = calculateRollingAccuracy(model, 100); // Last 100 predictions
        
        logger.info("🎯 Prediction recorded: {} | Accuracy: {:.1f}% | Model: {}", 
            prediction.getSymbol(), accuracy * 100, model);
    }
    
    /**
     * Generate real-time performance dashboard
     */
    public PerformanceDashboard generateDashboard() {
        PerformanceDashboard dashboard = new PerformanceDashboard();
        
        // Overall performance metrics
        dashboard.setOverallMetrics(calculateOverallMetrics());
        
        // Strategy performance comparison
        dashboard.setStrategyComparison(generateStrategyComparison());
        
        // Prediction accuracy by model
        dashboard.setPredictionAccuracy(calculatePredictionAccuracies());
        
        // Risk metrics
        dashboard.setRiskMetrics(riskAnalyzer.calculateCurrentRisk(tradeHistory));
        
        // Recent performance trends
        dashboard.setPerformanceTrends(calculatePerformanceTrends());
        
        return dashboard;
    }
    
    /**
     * ADVANCED ANALYTICS
     */
    
    /**
     * Analyze strategy performance attribution
     */
    public PerformanceAttribution analyzePerformanceAttribution(String strategy) {
        List<TradeResult> trades = tradeHistory.get(strategy);
        if (trades == null || trades.isEmpty()) {
            return new PerformanceAttribution();
        }
        
        PerformanceAttribution attribution = new PerformanceAttribution();
        
        // Analyze by time of day
        Map<Integer, Double> hourlyReturns = new HashMap<>();
        for (TradeResult trade : trades) {
            int hour = trade.getEntryTime().getHour();
            hourlyReturns.merge(hour, trade.getReturnPercent(), Double::sum);
        }
        attribution.setHourlyAttribution(hourlyReturns);
        
        // Analyze by market condition
        Map<String, Double> conditionReturns = analyzeByMarketCondition(trades);
        attribution.setMarketConditionAttribution(conditionReturns);
        
        // Analyze by trade duration
        Map<String, Double> durationReturns = analyzeByTradeDuration(trades);
        attribution.setDurationAttribution(durationReturns);
        
        // Analyze by volatility regime
        Map<String, Double> volatilityReturns = analyzeByVolatilityRegime(trades);
        attribution.setVolatilityAttribution(volatilityReturns);
        
        return attribution;
    }
    
    /**
     * Correlation analysis between strategies
     */
    public CorrelationMatrix calculateStrategyCorrelations() {
        List<String> strategies = new ArrayList<>(tradeHistory.keySet());
        int n = strategies.size();
        double[][] correlations = new double[n][n];
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    correlations[i][j] = 1.0;
                } else {
                    correlations[i][j] = calculateStrategyCorrelation(
                        strategies.get(i), strategies.get(j));
                }
            }
        }
        
        return new CorrelationMatrix(strategies, correlations);
    }
    
    /**
     * OPTIMIZATION RECOMMENDATIONS
     */
    
    /**
     * Generate optimization recommendations based on performance analysis
     */
    public List<OptimizationRecommendation> generateOptimizationRecommendations() {
        List<OptimizationRecommendation> recommendations = new ArrayList<>();
        
        // Analyze underperforming strategies
        for (Map.Entry<String, StrategyMetrics> entry : strategyMetrics.entrySet()) {
            String strategy = entry.getKey();
            StrategyMetrics metrics = entry.getValue();
            
            if (metrics.getSharpeRatio() < 1.0) {
                recommendations.add(new OptimizationRecommendation(
                    "IMPROVE_SHARPE",
                    strategy,
                    "Strategy has low Sharpe ratio (" + String.format("%.2f", metrics.getSharpeRatio()) + 
                    "). Consider adjusting risk management or entry criteria.",
                    OptimizationRecommendation.Priority.HIGH
                ));
            }
            
            if (metrics.getMaxDrawdown() > 0.1) {
                recommendations.add(new OptimizationRecommendation(
                    "REDUCE_DRAWDOWN",
                    strategy,
                    "Strategy has high maximum drawdown (" + String.format("%.1f%%", metrics.getMaxDrawdown() * 100) + 
                    "). Consider tighter stop losses or position sizing.",
                    OptimizationRecommendation.Priority.HIGH
                ));
            }
            
            if (metrics.getWinRate() < 0.5) {
                recommendations.add(new OptimizationRecommendation(
                    "IMPROVE_WIN_RATE",
                    strategy,
                    "Strategy has low win rate (" + String.format("%.1f%%", metrics.getWinRate() * 100) + 
                    "). Consider refining entry signals or market timing.",
                    OptimizationRecommendation.Priority.MEDIUM
                ));
            }
        }
        
        // Analyze prediction model performance
        for (Map.Entry<String, List<PredictionResult>> entry : predictionHistory.entrySet()) {
            String model = entry.getKey();
            double accuracy = calculateRollingAccuracy(model, 100);
            
            if (accuracy < 0.7) {
                recommendations.add(new OptimizationRecommendation(
                    "RETRAIN_MODEL",
                    model,
                    "Model has low accuracy (" + String.format("%.1f%%", accuracy * 100) + 
                    "). Consider retraining with recent data or feature engineering.",
                    OptimizationRecommendation.Priority.HIGH
                ));
            }
        }
        
        // Portfolio-level recommendations
        CorrelationMatrix correlations = calculateStrategyCorrelations();
        if (correlations.hasHighCorrelations(0.8)) {
            recommendations.add(new OptimizationRecommendation(
                "DIVERSIFY_STRATEGIES",
                "PORTFOLIO",
                "High correlation detected between strategies. Consider diversifying or reducing allocation to correlated strategies.",
                OptimizationRecommendation.Priority.MEDIUM
            ));
        }
        
        return recommendations;
    }
    
    /**
     * REPORT GENERATION
     */
    
    /**
     * Generate comprehensive performance report
     */
    public void generatePerformanceReport(String filePath) {
        try {
            PerformanceReport report = new PerformanceReport();
            
            // Executive summary
            report.setExecutiveSummary(generateExecutiveSummary());
            
            // Strategy performance
            report.setStrategyPerformance(strategyMetrics);
            
            // Prediction accuracy
            report.setPredictionAccuracy(calculatePredictionAccuracies());
            
            // Risk analysis
            report.setRiskAnalysis(riskAnalyzer.generateRiskReport(tradeHistory));
            
            // Performance attribution
            Map<String, PerformanceAttribution> attributions = new HashMap<>();
            for (String strategy : tradeHistory.keySet()) {
                attributions.put(strategy, analyzePerformanceAttribution(strategy));
            }
            report.setPerformanceAttributions(attributions);
            
            // Optimization recommendations
            report.setOptimizationRecommendations(generateOptimizationRecommendations());
            
            // Generate report file
            reportGenerator.generateReport(report, filePath);
            
            logger.info("📊 Performance report generated: {}", filePath);
            
        } catch (Exception e) {
            logger.error("Error generating performance report", e);
        }
    }
    
    /**
     * HELPER METHODS
     */
    
    private void initializeMetricsTracking() {
        logger.info("📊 Performance analysis engine initialized");
    }
    
    private void updateStrategyMetrics(String strategy, TradeResult trade) {
        StrategyMetrics metrics = strategyMetrics.computeIfAbsent(strategy, k -> new StrategyMetrics());
        metrics.addTrade(trade);
    }
    
    private void checkPerformanceAlerts(String strategy) {
        StrategyMetrics metrics = strategyMetrics.get(strategy);
        if (metrics != null) {
            // Check for significant drawdown
            if (metrics.getCurrentDrawdown() > 0.05) { // 5% drawdown
                logger.warn("⚠️ Strategy {} experiencing significant drawdown: {:.1f}%", 
                    strategy, metrics.getCurrentDrawdown() * 100);
            }
            
            // Check for consecutive losses
            if (metrics.getConsecutiveLosses() >= 5) {
                logger.warn("⚠️ Strategy {} has {} consecutive losses", 
                    strategy, metrics.getConsecutiveLosses());
            }
        }
    }
    
    private double calculateRollingAccuracy(String model, int window) {
        List<PredictionResult> predictions = predictionHistory.get(model);
        if (predictions == null || predictions.isEmpty()) return 0.0;
        
        int start = Math.max(0, predictions.size() - window);
        List<PredictionResult> recent = predictions.subList(start, predictions.size());
        
        long correct = recent.stream()
            .mapToLong(p -> p.isCorrect() ? 1 : 0)
            .sum();
        
        return (double) correct / recent.size();
    }
    
    private OverallMetrics calculateOverallMetrics() {
        // Calculate portfolio-level metrics
        double totalReturn = 0.0;
        double totalTrades = 0;
        double winningTrades = 0;
        
        for (List<TradeResult> trades : tradeHistory.values()) {
            for (TradeResult trade : trades) {
                totalReturn += trade.getReturnPercent();
                totalTrades++;
                if (trade.getPnl() > 0) winningTrades++;
            }
        }
        
        double winRate = totalTrades > 0 ? winningTrades / totalTrades : 0.0;
        
        return new OverallMetrics(totalReturn, winRate, totalTrades);
    }
    
    private Map<String, StrategyMetrics> generateStrategyComparison() {
        return new HashMap<>(strategyMetrics);
    }
    
    private Map<String, Double> calculatePredictionAccuracies() {
        Map<String, Double> accuracies = new HashMap<>();
        
        for (String model : predictionHistory.keySet()) {
            accuracies.put(model, calculateRollingAccuracy(model, Integer.MAX_VALUE));
        }
        
        return accuracies;
    }
    
    private Map<String, Double> calculatePerformanceTrends() {
        // Calculate 30-day performance trends
        Map<String, Double> trends = new HashMap<>();
        
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        
        for (Map.Entry<String, List<TradeResult>> entry : tradeHistory.entrySet()) {
            String strategy = entry.getKey();
            List<TradeResult> trades = entry.getValue();
            
            double recentReturn = trades.stream()
                .filter(t -> t.getEntryTime().isAfter(thirtyDaysAgo))
                .mapToDouble(TradeResult::getReturnPercent)
                .sum();
            
            trends.put(strategy, recentReturn);
        }
        
        return trends;
    }
    
    private Map<String, Double> analyzeByMarketCondition(List<TradeResult> trades) {
        Map<String, Double> conditionReturns = new HashMap<>();
        
        for (TradeResult trade : trades) {
            String condition = determineMarketCondition(trade);
            conditionReturns.merge(condition, trade.getReturnPercent(), Double::sum);
        }
        
        return conditionReturns;
    }
    
    private String determineMarketCondition(TradeResult trade) {
        // Simplified market condition determination
        // In practice, this would use more sophisticated analysis
        
        if (trade.getVolatility() > 0.03) return "HIGH_VOLATILITY";
        if (trade.getVolatility() < 0.015) return "LOW_VOLATILITY";
        return "NORMAL_VOLATILITY";
    }
    
    private Map<String, Double> analyzeByTradeDuration(List<TradeResult> trades) {
        Map<String, Double> durationReturns = new HashMap<>();
        
        for (TradeResult trade : trades) {
            String duration = categorizeTradeDuration(trade.getDurationMinutes());
            durationReturns.merge(duration, trade.getReturnPercent(), Double::sum);
        }
        
        return durationReturns;
    }
    
    private String categorizeTradeDuration(long durationMinutes) {
        if (durationMinutes < 15) return "SCALPING";
        if (durationMinutes < 60) return "SHORT_TERM";
        if (durationMinutes < 240) return "MEDIUM_TERM";
        return "LONG_TERM";
    }
    
    private Map<String, Double> analyzeByVolatilityRegime(List<TradeResult> trades) {
        Map<String, Double> volatilityReturns = new HashMap<>();
        
        for (TradeResult trade : trades) {
            String regime = categorizeVolatilityRegime(trade.getVolatility());
            volatilityReturns.merge(regime, trade.getReturnPercent(), Double::sum);
        }
        
        return volatilityReturns;
    }
    
    private String categorizeVolatilityRegime(double volatility) {
        if (volatility < 0.015) return "LOW_VOL";
        if (volatility < 0.025) return "MEDIUM_VOL";
        return "HIGH_VOL";
    }
    
    private double calculateStrategyCorrelation(String strategy1, String strategy2) {
        List<TradeResult> trades1 = tradeHistory.get(strategy1);
        List<TradeResult> trades2 = tradeHistory.get(strategy2);
        
        if (trades1 == null || trades2 == null || trades1.isEmpty() || trades2.isEmpty()) {
            return 0.0;
        }
        
        // Simplified correlation calculation
        // In practice, this would align trades by time and calculate proper correlation
        
        double[] returns1 = trades1.stream().mapToDouble(TradeResult::getReturnPercent).toArray();
        double[] returns2 = trades2.stream().mapToDouble(TradeResult::getReturnPercent).toArray();
        
        return calculateCorrelation(returns1, returns2);
    }
    
    private double calculateCorrelation(double[] x, double[] y) {
        if (x.length != y.length || x.length == 0) return 0.0;
        
        double meanX = Arrays.stream(x).average().orElse(0.0);
        double meanY = Arrays.stream(y).average().orElse(0.0);
        
        double numerator = 0.0, denomX = 0.0, denomY = 0.0;
        
        for (int i = 0; i < x.length; i++) {
            double diffX = x[i] - meanX;
            double diffY = y[i] - meanY;
            numerator += diffX * diffY;
            denomX += diffX * diffX;
            denomY += diffY * diffY;
        }
        
        double denominator = Math.sqrt(denomX * denomY);
        return denominator == 0 ? 0.0 : numerator / denominator;
    }
    
    private List<IndexData> generateRandomScenario(List<IndexData> baseData) {
        // Generate random market scenario for Monte Carlo simulation
        // This is a simplified implementation
        
        List<IndexData> scenario = new ArrayList<>();
        Random random = new Random();
        
        for (int i = 0; i < baseData.size(); i++) {
            IndexData base = baseData.get(i);
            
            // Add random noise to price
            double noise = random.nextGaussian() * 0.01; // 1% volatility
            double newPrice = base.getLastPrice() * (1 + noise);
            
            // Create new data point with randomized price
            IndexData newData = new IndexData(
                base.getSymbol(),
                newPrice,
                base.getHighPrice() * (1 + Math.abs(noise)),
                base.getLowPrice() * (1 - Math.abs(noise)),
                base.getVolume(),
                base.getTimestamp()
            );
            
            scenario.add(newData);
        }
        
        return scenario;
    }
    
    private ComparativeAnalysis generateComparativeAnalysis(Map<String, StrategyBacktestResult> results) {
        // Generate comparative analysis between strategies
        return new ComparativeAnalysis(results);
    }
    
    private String generateExecutiveSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("EXECUTIVE SUMMARY\n");
        summary.append("================\n\n");
        
        OverallMetrics overall = calculateOverallMetrics();
        summary.append(String.format("Total Trades: %.0f\n", overall.getTotalTrades()));
        summary.append(String.format("Overall Win Rate: %.1f%%\n", overall.getWinRate() * 100));
        summary.append(String.format("Total Return: %.2f%%\n", overall.getTotalReturn() * 100));
        
        summary.append("\nTop Performing Strategies:\n");
        strategyMetrics.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue().getTotalReturn(), e1.getValue().getTotalReturn()))
            .limit(3)
            .forEach(entry -> summary.append(String.format("- %s: %.2f%% return\n", 
                entry.getKey(), entry.getValue().getTotalReturn() * 100)));
        
        return summary.toString();
    }
    
    public void shutdown() {
        logger.info("📊 Performance analysis engine shutdown");
    }
    
    // Inner classes for data structures would be defined here...
    // (TradeResult, PredictionResult, StrategyMetrics, etc.)
    // These are simplified for brevity
    
    public static class TradeResult {
        private final String symbol;
        private final double entryPrice;
        private final double exitPrice;
        private final double pnl;
        private final double returnPercent;
        private final LocalDateTime entryTime;
        private final LocalDateTime exitTime;
        private final double volatility;
        
        public TradeResult(String symbol, double entryPrice, double exitPrice, 
                         LocalDateTime entryTime, LocalDateTime exitTime, double volatility) {
            this.symbol = symbol;
            this.entryPrice = entryPrice;
            this.exitPrice = exitPrice;
            this.entryTime = entryTime;
            this.exitTime = exitTime;
            this.volatility = volatility;
            this.pnl = exitPrice - entryPrice;
            this.returnPercent = (exitPrice - entryPrice) / entryPrice;
        }
        
        // Getters
        public String getSymbol() { return symbol; }
        public double getEntryPrice() { return entryPrice; }
        public double getExitPrice() { return exitPrice; }
        public double getPnl() { return pnl; }
        public double getReturnPercent() { return returnPercent; }
        public LocalDateTime getEntryTime() { return entryTime; }
        public LocalDateTime getExitTime() { return exitTime; }
        public double getVolatility() { return volatility; }
        
        public long getDurationMinutes() {
            return java.time.Duration.between(entryTime, exitTime).toMinutes();
        }
    }
    
    // Additional inner classes would be defined here...
    // (Simplified for brevity)
    
    private static class StrategyMetrics {
        private double totalReturn = 0.0;
        private int totalTrades = 0;
        private int winningTrades = 0;
        private double maxDrawdown = 0.0;
        private double currentDrawdown = 0.0;
        private int consecutiveLosses = 0;
        private double sharpeRatio = 0.0;
        
        void addTrade(TradeResult trade) {
            totalReturn += trade.getReturnPercent();
            totalTrades++;
            
            if (trade.getPnl() > 0) {
                winningTrades++;
                consecutiveLosses = 0;
            } else {
                consecutiveLosses++;
            }
            
            // Update drawdown
            if (trade.getReturnPercent() < 0) {
                currentDrawdown += Math.abs(trade.getReturnPercent());
                maxDrawdown = Math.max(maxDrawdown, currentDrawdown);
            } else {
                currentDrawdown = Math.max(0, currentDrawdown - trade.getReturnPercent());
            }
            
            // Recalculate Sharpe ratio (simplified)
            sharpeRatio = totalTrades > 0 ? totalReturn / Math.sqrt(totalTrades) : 0.0;
        }
        
        // Getters
        public double getTotalReturn() { return totalReturn; }
        public double getWinRate() { return totalTrades > 0 ? (double) winningTrades / totalTrades : 0.0; }
        public double getMaxDrawdown() { return maxDrawdown; }
        public double getCurrentDrawdown() { return currentDrawdown; }
        public int getConsecutiveLosses() { return consecutiveLosses; }
        public double getSharpeRatio() { return sharpeRatio; }
    }
    
    // Additional simplified inner classes...
    private static class PredictionResult {
        private final String symbol;
        private final boolean correct;
        
        public PredictionResult(String symbol, boolean correct) {
            this.symbol = symbol;
            this.correct = correct;
        }
        
        public String getSymbol() { return symbol; }
        public boolean isCorrect() { return correct; }
    }
    
    private static class OverallMetrics {
        private final double totalReturn;
        private final double winRate;
        private final double totalTrades;
        
        public OverallMetrics(double totalReturn, double winRate, double totalTrades) {
            this.totalReturn = totalReturn;
            this.winRate = winRate;
            this.totalTrades = totalTrades;
        }
        
        public double getTotalReturn() { return totalReturn; }
        public double getWinRate() { return winRate; }
        public double getTotalTrades() { return totalTrades; }
    }
    
    // More inner classes would be defined here for complete implementation...
}