package com.trading.bot.agents;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import java.util.*;

public class PatternAgent implements Agent {
    private final HonestMarketDataFetcher fetcher = HonestMarketDataFetcher.getInstance();
    public Set<String> topics() { return Set.of("REQUEST_PATTERN"); }
    public void onMessage(AgentMessage message, AgentBus bus) {
        String symbol = (String) message.payload.get("symbol");
        try {
            List<SimpleMarketData> data = fetcher.getRealMarketData5Min(symbol);
            java.util.Set<String> patterns = com.trading.bot.technical.AdvancedCandlestickDetector.detectAll(data);
            bus.publish(new AgentMessage("PATTERN_RESULT", Map.of("symbol", symbol, "patterns", patterns)));
        } catch (Exception e) {
            bus.publish(new AgentMessage("PATTERN_ERROR", Map.of("symbol", symbol, "error", e.getMessage())));
        }
    }
    public String name() { return "PatternAgent"; }
}
