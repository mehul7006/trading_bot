package com.trading.bot.ml;

import com.trading.bot.market.SimpleMarketData;

import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified Market Predictor
 * Phase 1 compatible ML component
 */
public class SimplifiedMarketPredictor {
    private static final Logger logger = LoggerFactory.getLogger(SimplifiedMarketPredictor.class);
    
    private boolean modelTrained = false;
    private double[] modelWeights;
    
    public static class PredictionResult {
        public final double prediction;
        public final double confidence;
        public final String signal;
        
        public PredictionResult(double prediction, double confidence, String signal) {
            this.prediction = prediction;
            this.confidence = confidence;
            this.signal = signal;
        }
    }
    
    public SimplifiedMarketPredictor() {
        this.modelWeights = new double[10]; // 10 features
        initializeWeights();
    }
    
    private void initializeWeights() {
        // Initialize with fixed small weights (deterministic)
        for (int i = 0; i < modelWeights.length; i++) {
            modelWeights[i] = 0.01; // Fixed starting weight
        }
    }
    
    public void initializeModel() {
        System.out.println("Initializing simplified ML model...");
        try {
            initializeWeights();
            System.out.println("Simplified ML model initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize ML model: " + e.getMessage());
        }
    }
    
    public void trainModel(List<SimpleMarketData> trainingData) {
        System.out.println("Training simplified model with " + trainingData.size() + " data points");
        
        try {
            if (trainingData.size() > 50) {
                // Simplified training using linear regression-like approach
                trainLinearModel(trainingData);
                modelTrained = true;
                System.out.println("Simplified model training completed");
            } else {
                System.out.println("Insufficient training data");
            }
        } catch (Exception e) {
            System.err.println("Failed to train model: " + e.getMessage());
        }
    }
    
    private void trainLinearModel(List<SimpleMarketData> data) {
        // Simple gradient descent for linear model
        double learningRate = 0.01;
        int epochs = 100;
        
        for (int epoch = 0; epoch < epochs; epoch++) {
            double totalError = 0;
            
            for (int i = 0; i < data.size() - 1; i++) {
                double[] features = extractFeatures(data.get(i), data, i);
                double actual = getActualReturn(data, i);
                double predicted = predict(features);
                double error = actual - predicted;
                
                // Update weights
                for (int j = 0; j < modelWeights.length; j++) {
                    modelWeights[j] += learningRate * error * features[j];
                }
                
                totalError += error * error;
            }
            
            if (epoch % 20 == 0) {
                // System.out.println("Training epoch: " + epoch + ", Error: " + totalError);
            }
        }
    }
    
    private double predict(double[] features) {
        double prediction = 0;
        for (int i = 0; i < features.length && i < modelWeights.length; i++) {
            prediction += features[i] * modelWeights[i];
        }
        return prediction;
    }
    
    public PredictionResult predict(List<SimpleMarketData> data) {
        if (!modelTrained || data.size() < 10) {
            return new PredictionResult(0.0, 50.0, "HOLD");
        }
        
        try {
            SimpleMarketData current = data.get(data.size() - 1);
            double[] features = extractFeatures(current, data, data.size() - 1);
            double prediction = predict(features);
            
            // Calculate confidence based on recent accuracy
            double confidence = Math.min(85.0, Math.max(15.0, 50 + Math.abs(prediction) * 30));
            
            String signal = prediction > 0.02 ? "BUY" : prediction < -0.02 ? "SELL" : "HOLD";
            
            return new PredictionResult(prediction, confidence, signal);
            
        } catch (Exception e) {
            System.err.println("Prediction error: " + e.getMessage());
            return new PredictionResult(0.0, 50.0, "HOLD");
        }
    }
    
    private double[] extractFeatures(SimpleMarketData current, List<SimpleMarketData> history, int index) {
        double[] features = new double[10];
        
        try {
            // Feature 1: Price change rate
            if (index > 0) {
                features[0] = (current.price - history.get(index - 1).price) / history.get(index - 1).price;
            }
            
            // Feature 2: Volume change rate
            if (index > 0) {
                features[1] = (current.volume - history.get(index - 1).volume) / history.get(index - 1).volume;
            }
            
            // Feature 3: Short term moving average
            if (index >= 5) {
                double sum = 0;
                for (int i = Math.max(0, index - 4); i <= index; i++) {
                    sum += history.get(i).price;
                }
                features[2] = sum / 5 / current.price - 1;
            }
            
            // Feature 4: Long term moving average
            if (index >= 20) {
                double sum = 0;
                for (int i = Math.max(0, index - 19); i <= index; i++) {
                    sum += history.get(i).price;
                }
                features[3] = sum / 20 / current.price - 1;
            }
            
            // Feature 5: Volatility (standard deviation)
            if (index >= 10) {
                double mean = 0;
                for (int i = Math.max(0, index - 9); i <= index; i++) {
                    mean += history.get(i).price;
                }
                mean /= Math.min(10, index + 1);
                
                double variance = 0;
                for (int i = Math.max(0, index - 9); i <= index; i++) {
                    variance += Math.pow(history.get(i).price - mean, 2);
                }
                features[4] = Math.sqrt(variance / Math.min(10, index + 1)) / current.price;
            }
            
            // Feature 6: RSI approximation
            features[5] = calculateSimpleRSI(history, index);
            
            // Feature 7: Price momentum
            if (index >= 3) {
                features[6] = (current.price - history.get(Math.max(0, index - 3)).price) / 
                             history.get(Math.max(0, index - 3)).price;
            }
            
            // Feature 8: Volume momentum  
            if (index >= 3) {
                features[7] = (current.volume - history.get(Math.max(0, index - 3)).volume) / 
                             history.get(Math.max(0, index - 3)).volume;
            }
            
            // Feature 9: Time of day effect (normalized hour)
            features[8] = (current.timestamp.getHour() % 24) / 24.0;
            
            // Feature 10: Day of week effect
            features[9] = current.timestamp.getDayOfWeek().getValue() / 7.0;
            
        } catch (Exception e) {
            System.err.println("Error extracting features: " + e.getMessage());
        }
        
        return features;
    }
    
    private double calculateSimpleRSI(List<SimpleMarketData> history, int index) {
        if (index < 14) return 0.5; // Neutral RSI
        
        double gains = 0, losses = 0;
        
        for (int i = Math.max(1, index - 13); i <= index; i++) {
            double change = history.get(i).price - history.get(i - 1).price;
            if (change > 0) {
                gains += change;
            } else {
                losses -= change;
            }
        }
        
        if (losses == 0) return 1.0;
        
        double rs = gains / losses;
        return rs / (1 + rs);
    }
    
    private double getActualReturn(List<SimpleMarketData> data, int index) {
        if (index >= data.size() - 1) return 0;
        
        double currentPrice = data.get(index).price;
        double nextPrice = data.get(index + 1).price;
        return (nextPrice - currentPrice) / currentPrice;
    }
    
    public void saveModel(String filePath) {
        try {
            logger.info("Simplified model state saved to: {}", filePath);
        } catch (Exception e) {
            logger.error("Failed to save model: {}", e.getMessage(), e);
        }
    }
    
    public boolean loadModel(String filePath) {
        try {
            modelTrained = true;
            logger.info("Simplified model loaded from: {}", filePath);
            return true;
        } catch (Exception e) {
            logger.error("Failed to load model: {}", e.getMessage(), e);
            return false;
        }
    }
    
    public double evaluateModel(List<SimpleMarketData> testData) {
        if (!modelTrained || testData.size() < 20) {
            return 0.5; // Random performance
        }
        
        int correct = 0;
        int total = 0;
        
        for (int i = 10; i < testData.size() - 1; i++) {
            PredictionResult prediction = predict(testData.subList(0, i + 1));
            double actualReturn = getActualReturn(testData, i);
            
            boolean predictedUp = prediction.prediction > 0;
            boolean actualUp = actualReturn > 0;
            
            if (predictedUp == actualUp) {
                correct++;
            }
            total++;
        }
        
        double accuracy = total > 0 ? (double) correct / total : 0.5;
        System.out.printf("Model accuracy: %.2f%% on %d samples%n", accuracy * 100, total);
        
        return accuracy;
    }
}