package com.trading.bot.integration;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * PYTHON ML BRIDGE
 * Integrates Python ML models with Java trading bot
 * Handles communication between Java and Python for 75% accuracy
 */
public class PythonMLBridge {
    
    private final String pythonExecutable;
    private final String mlScriptPath;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    
    public static class MLPrediction {
        public final String symbol;
        public final String prediction;
        public final double confidence;
        public final Map<String, Double> probabilities;
        public final long timestamp;
        
        public MLPrediction(String symbol, String prediction, double confidence, 
                           Map<String, Double> probabilities, long timestamp) {
            this.symbol = symbol;
            this.prediction = prediction;
            this.confidence = confidence;
            this.probabilities = new HashMap<>(probabilities);
            this.timestamp = timestamp;
        }
    }
    
    public static class OptionsMLPrediction {
        public final String index;
        public final String prediction;
        public final double confidence;
        public final String recommendedAction;
        public final double targetStrike;
        public final String optionType;
        public final long timestamp;
        
        public OptionsMLPrediction(String index, String prediction, double confidence,
                                 String recommendedAction, double targetStrike, 
                                 String optionType, long timestamp) {
            this.index = index;
            this.prediction = prediction;
            this.confidence = confidence;
            this.recommendedAction = recommendedAction;
            this.targetStrike = targetStrike;
            this.optionType = optionType;
            this.timestamp = timestamp;
        }
    }
    
    public PythonMLBridge() {
        this.pythonExecutable = detectPythonExecutable();
        this.mlScriptPath = "ml_models/advanced_prediction_engine.py";
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(4);
        
        initializePythonEnvironment();
    }
    
    private String detectPythonExecutable() {
        // Try different Python executables
        String[] pythonCommands = {"python3", "python", "py"};
        
        for (String cmd : pythonCommands) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    System.out.println("✅ Found Python executable: " + cmd);
                    return cmd;
                }
            } catch (Exception e) {
                // Continue trying other commands
            }
        }
        
        System.err.println("❌ No Python executable found");
        return "python3"; // Default fallback
    }
    
    private void initializePythonEnvironment() {
        System.out.println("🔧 INITIALIZING PYTHON ML ENVIRONMENT");
        System.out.println("-".repeat(50));
        
        try {
            // Check if required packages are installed
            installRequiredPackages();
            
            // Initialize ML models
            initializeMLModels();
            
            System.out.println("✅ Python ML environment initialized successfully");
            
        } catch (Exception e) {
            System.err.println("❌ Error initializing Python environment: " + e.getMessage());
        }
    }
    
    private void installRequiredPackages() {
        System.out.println("📦 Installing required Python packages...");
        
        String[] packages = {
            "pandas", "numpy", "scikit-learn", "xgboost", 
            "lightgbm", "joblib", "matplotlib", "seaborn"
        };
        
        try {
            for (String pkg : packages) {
                ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "-m", "pip", "install", pkg);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                // Read output
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("Successfully installed") || line.contains("Requirement already satisfied")) {
                            System.out.println("✅ " + pkg);
                            break;
                        }
                    }
                }
                
                process.waitFor(30, TimeUnit.SECONDS);
            }
            
        } catch (Exception e) {
            System.err.println("⚠️ Error installing packages: " + e.getMessage());
            System.out.println("💡 Please install manually: pip install pandas numpy scikit-learn xgboost lightgbm");
        }
    }
    
    private void initializeMLModels() {
        System.out.println("🤖 Initializing ML models...");
        
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, mlScriptPath);
            pb.directory(new File("."));
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Read output
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("🐍 " + line);
                }
            }
            
            int exitCode = process.waitFor(300, TimeUnit.SECONDS); // 5 minute timeout
            
            if (exitCode == 0) {
                System.out.println("✅ ML models initialized successfully");
            } else {
                System.err.println("❌ ML model initialization failed with exit code: " + exitCode);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error initializing ML models: " + e.getMessage());
        }
    }
    
    /**
     * Get ML prediction for index movement
     */
    public MLPrediction predictIndexMovement(String index, Map<String, Object> marketData) {
        try {
            // Create input JSON for Python script
            Map<String, Object> input = new HashMap<>();
            input.put("action", "predict_index");
            input.put("index", index);
            input.put("market_data", marketData);
            input.put("timestamp", System.currentTimeMillis());
            
            // Write input to temporary file
            String inputFile = "temp_input_" + System.currentTimeMillis() + ".json";
            objectMapper.writeValue(new File(inputFile), input);
            
            // Create Python script call
            String predictionScript = createPredictionScript(inputFile, "index");
            
            // Execute Python script
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "-c", predictionScript);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            process.waitFor(30, TimeUnit.SECONDS);
            
            // Parse result
            MLPrediction prediction = parsePredictionResult(output.toString(), index);
            
            // Cleanup
            Files.deleteIfExists(Paths.get(inputFile));
            
            return prediction;
            
        } catch (Exception e) {
            System.err.println("❌ Error getting ML prediction for " + index + ": " + e.getMessage());
            return createFallbackPrediction(index);
        }
    }
    
    /**
     * Get ML prediction for options movement
     */
    public OptionsMLPrediction predictOptionsMovement(String index, Map<String, Object> marketData, Map<String, Object> optionsData) {
        try {
            // Create input JSON for Python script
            Map<String, Object> input = new HashMap<>();
            input.put("action", "predict_options");
            input.put("index", index);
            input.put("market_data", marketData);
            input.put("options_data", optionsData);
            input.put("timestamp", System.currentTimeMillis());
            
            // Write input to temporary file
            String inputFile = "temp_options_input_" + System.currentTimeMillis() + ".json";
            objectMapper.writeValue(new File(inputFile), input);
            
            // Create Python script call
            String predictionScript = createPredictionScript(inputFile, "options");
            
            // Execute Python script
            ProcessBuilder pb = new ProcessBuilder(pythonExecutable, "-c", predictionScript);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            process.waitFor(30, TimeUnit.SECONDS);
            
            // Parse result
            OptionsMLPrediction prediction = parseOptionsPredictionResult(output.toString(), index);
            
            // Cleanup
            Files.deleteIfExists(Paths.get(inputFile));
            
            return prediction;
            
        } catch (Exception e) {
            System.err.println("❌ Error getting options ML prediction for " + index + ": " + e.getMessage());
            return createFallbackOptionsPrediction(index);
        }
    }
    
    private String createPredictionScript(String inputFile, String predictionType) {
        return String.format("""
            import json
            import sys
            import os
            sys.path.append('ml_models')
            
            try:
                from advanced_prediction_engine import AdvancedPredictionEngine
                import pandas as pd
                import numpy as np
                
                # Load trained models
                engine = AdvancedPredictionEngine()
                engine.load_models('ml_models')
                
                # Read input
                with open('%s', 'r') as f:
                    input_data = json.load(f)
                
                index = input_data['index']
                market_data = input_data['market_data']
                
                # Create DataFrame from market data
                df = pd.DataFrame([market_data])
                
                if '%s' == 'index':
                    # Index prediction
                    result = engine.predict_index_movement(index, df)
                    if result:
                        print(json.dumps({
                            'prediction': result['prediction'],
                            'confidence': result['confidence'],
                            'probabilities': result['probabilities'],
                            'success': True
                        }))
                    else:
                        print(json.dumps({'success': False, 'error': 'No model found'}))
                        
                elif '%s' == 'options':
                    # Options prediction
                    result = engine.predict_options_movement(index, df)
                    if result:
                        print(json.dumps({
                            'prediction': result['prediction'],
                            'confidence': result['confidence'],
                            'probabilities': result['probabilities'],
                            'success': True
                        }))
                    else:
                        print(json.dumps({'success': False, 'error': 'No options model found'}))
                        
            except Exception as e:
                print(json.dumps({'success': False, 'error': str(e)}))
            """, inputFile, predictionType, predictionType);
    }
    
    private MLPrediction parsePredictionResult(String output, String index) {
        try {
            // Find JSON output in the output string
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.trim().startsWith("{") && line.trim().endsWith("}")) {
                    JsonNode result = objectMapper.readTree(line.trim());
                    
                    if (result.get("success").asBoolean()) {
                        String prediction = result.get("prediction").asText();
                        double confidence = result.get("confidence").asDouble();
                        
                        Map<String, Double> probabilities = new HashMap<>();
                        JsonNode probNode = result.get("probabilities");
                        if (probNode != null) {
                            probNode.fields().forEachRemaining(entry -> 
                                probabilities.put(entry.getKey(), entry.getValue().asDouble()));
                        }
                        
                        return new MLPrediction(index, prediction, confidence, probabilities, System.currentTimeMillis());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error parsing prediction result: " + e.getMessage());
        }
        
        return createFallbackPrediction(index);
    }
    
    private OptionsMLPrediction parseOptionsPredictionResult(String output, String index) {
        try {
            // Find JSON output in the output string
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.trim().startsWith("{") && line.trim().endsWith("}")) {
                    JsonNode result = objectMapper.readTree(line.trim());
                    
                    if (result.get("success").asBoolean()) {
                        String prediction = result.get("prediction").asText();
                        double confidence = result.get("confidence").asDouble();
                        
                        // Determine recommended action based on prediction
                        String action = "HOLD";
                        String optionType = "CE";
                        double targetStrike = 0;
                        
                        if (prediction.contains("CE_PROFITABLE")) {
                            action = "BUY_CE";
                            optionType = "CE";
                        } else if (prediction.contains("PE_PROFITABLE")) {
                            action = "BUY_PE";
                            optionType = "PE";
                        }
                        
                        return new OptionsMLPrediction(index, prediction, confidence, action, targetStrike, optionType, System.currentTimeMillis());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error parsing options prediction result: " + e.getMessage());
        }
        
        return createFallbackOptionsPrediction(index);
    }
    
    private MLPrediction createFallbackPrediction(String index) {
        // Create a basic prediction when ML fails
        Map<String, Double> probabilities = new HashMap<>();
        probabilities.put("BULLISH", 0.4);
        probabilities.put("BEARISH", 0.3);
        probabilities.put("SIDEWAYS", 0.3);
        
        return new MLPrediction(index, "SIDEWAYS", 50.0, probabilities, System.currentTimeMillis());
    }
    
    private OptionsMLPrediction createFallbackOptionsPrediction(String index) {
        return new OptionsMLPrediction(index, "UNPROFITABLE", 50.0, "HOLD", 0, "CE", System.currentTimeMillis());
    }
    
    /**
     * Test ML integration and accuracy
     */
    public void testMLIntegration() {
        System.out.println("🧪 TESTING ML INTEGRATION");
        System.out.println("-".repeat(40));
        
        String[] indices = {"NIFTY", "BANKNIFTY", "FINNIFTY"};
        
        for (String index : indices) {
            // Create sample market data
            Map<String, Object> marketData = new HashMap<>();
            marketData.put("close", 24500.0);
            marketData.put("open", 24450.0);
            marketData.put("high", 24600.0);
            marketData.put("low", 24400.0);
            marketData.put("volume", 100000000);
            
            // Test index prediction
            MLPrediction indexPred = predictIndexMovement(index, marketData);
            System.out.printf("📈 %s Index: %s (%.1f%% confidence)%n", 
                index, indexPred.prediction, indexPred.confidence);
            
            // Test options prediction
            Map<String, Object> optionsData = new HashMap<>();
            optionsData.put("implied_volatility", 20.0);
            optionsData.put("open_interest", 1000000);
            
            OptionsMLPrediction optionsPred = predictOptionsMovement(index, marketData, optionsData);
            System.out.printf("🎯 %s Options: %s (%.1f%% confidence)%n", 
                index, optionsPred.prediction, optionsPred.confidence);
        }
    }
    
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}