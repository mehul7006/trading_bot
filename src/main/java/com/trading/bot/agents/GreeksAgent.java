package com.trading.bot.agents;

import java.util.*;

public class GreeksAgent implements Agent {
    public Set<String> topics() { return Set.of("REQUEST_GREEKS"); }
    public void onMessage(AgentMessage message, AgentBus bus) {
        String symbol = (String) message.payload.get("symbol");
        double spot = (double) message.payload.get("spot");
        double strike = (double) message.payload.get("strike");
        double time = (double) message.payload.getOrDefault("time", 7.0 / 365.0);
        double iv = (double) message.payload.getOrDefault("iv", 0.15);
        double rate = 0.05;

        Map<String, Double> greeks = calculateGreeks(spot, strike, time, iv, rate);
        bus.publish(new AgentMessage("GREEKS_RESULT", Map.of("symbol", symbol, "greeks", greeks)));
    }

    private Map<String, Double> calculateGreeks(double s, double k, double t, double v, double r) {
        Map<String, Double> res = new HashMap<>();
        double d1 = (Math.log(s / k) + (r + 0.5 * v * v) * t) / (v * Math.sqrt(t));
        double d2 = d1 - v * Math.sqrt(t);
        
        double delta = 0.5 * (1 + Math.tanh(d1 / Math.sqrt(2))); // Simplified CDF
        double gamma = Math.exp(-0.5 * d1 * d1) / (s * v * Math.sqrt(2 * Math.PI * t));
        double theta = -(s * v * Math.exp(-0.5 * d1 * d1)) / (2 * Math.sqrt(2 * Math.PI * t));
        
        res.put("delta", delta);
        res.put("gamma", gamma);
        res.put("theta", theta);
        return res;
    }
    
    public String name() { return "GreeksAgent"; }
}
