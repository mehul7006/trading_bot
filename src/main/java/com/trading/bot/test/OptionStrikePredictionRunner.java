package com.trading.bot.test;

import com.trading.bot.ai.OptionStrikePredictor;

public class OptionStrikePredictionRunner {
    public static void main(String[] args) {
        OptionStrikePredictor predictor = new OptionStrikePredictor();
        
        run("NIFTY50", 25100, "CE", predictor);
        run("SENSEX", 83000, "CE", predictor);
        run("SENSEX", 82500, "PE", predictor);
    }
    
    private static void run(String symbol, double strike, String type, OptionStrikePredictor predictor) {
        OptionStrikePredictor.StrikePrediction p = predictor.predict(symbol, strike, type);
        System.out.println("\n==============================================");
        System.out.printf("🎯 %s %,.0f %s premium prediction%n", symbol, strike, type);
        System.out.println("----------------------------------------------");
        System.out.println("Direction: " + p.direction);
        System.out.printf("Confidence: %.1f%%%n", p.confidence);
        System.out.printf("Target Premium: %.2f pts%n", p.targetPremiumPoints);
        System.out.printf("Stop Loss: %.2f pts%n", p.stopLossPoints);
        System.out.println("Reason: " + p.reasoning);
    }
}
