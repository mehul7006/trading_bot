package com.trading.bot.agents;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import java.util.*;

public class IndicatorAgent implements Agent {
    private final HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
    public Set<String> topics() { return Set.of("REQUEST_INDICATORS"); }
    public void onMessage(AgentMessage message, AgentBus bus) {
        String symbol = (String) message.payload.get("symbol");
        try {
            List<SimpleMarketData> data = fetcher.getRealMarketData5Min(symbol);
            double rsi = rsi(data, 14);
            double ema20 = ema(data, 20);
            long vol = data.get(data.size() - 1).volume;
            bus.publish(new AgentMessage("INDICATORS_RESULT", Map.of("symbol", symbol, "rsi", rsi, "ema20", ema20, "volume", vol)));
        } catch (Exception e) {
            bus.publish(new AgentMessage("INDICATORS_ERROR", Map.of("symbol", symbol, "error", e.getMessage())));
        }
    }
    private double ema(List<SimpleMarketData> data, int period) {
        if (data.size() < period) return data.get(data.size() - 1).price;
        double m = 2.0 / (period + 1);
        double e = data.get(0).price;
        for (int i = 1; i < data.size(); i++) e = ((data.get(i).price - e) * m) + e;
        return e;
    }
    private double rsi(List<SimpleMarketData> data, int period) {
        if (data.size() < period + 1) return 50;
        double g = 0, l = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            double c = data.get(i).price - data.get(i - 1).price;
            if (c > 0) g += c; else l -= c;
        }
        if (l == 0) return 100;
        double rs = g / l;
        return 100 - (100 / (1 + rs));
    }
    public String name() { return "IndicatorAgent"; }
}
