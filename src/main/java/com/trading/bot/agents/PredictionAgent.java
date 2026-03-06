package com.trading.bot.agents;

import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.SimpleMarketData;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PredictionAgent: Responsible for generating trading signals.
 * It uses the AIPredictor (V19 logic) to analyze data and suggest direction/confidence.
 */
public class PredictionAgent implements Agent {
    private final AIPredictor predictor;

    public PredictionAgent() {
        this.predictor = new AIPredictor();
        this.predictor.initialize();
    }

    public AIPredictor.AIPrediction generateSignal(String symbol, List<SimpleMarketData> history) {
        return predictor.generatePrediction(symbol, history, null);
    }

    @Override
    public Set<String> topics() {
        return Set.of("REQUEST_PREDICTION");
    }

    @Override
    public void onMessage(AgentMessage message, AgentBus bus) {
        String symbol = (String) message.payload.get("symbol");
        @SuppressWarnings("unchecked")
        List<SimpleMarketData> history = (List<SimpleMarketData>) message.payload.get("history");
        try {
            AIPredictor.AIPrediction prediction = generateSignal(symbol, history);
            bus.publish(new AgentMessage("PREDICTION_RESULT", Map.of("symbol", symbol, "prediction", prediction)));
        } catch (Exception e) {
            bus.publish(new AgentMessage("PREDICTION_ERROR", Map.of("symbol", symbol, "error", e.getMessage())));
        }
    }

    @Override
    public String name() {
        return "PredictionAgent";
    }
}
