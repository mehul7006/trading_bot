package com.trading.bot.agents;

import com.trading.bot.market.HonestMarketDataFetcher;
import java.util.Map;
import java.util.Set;

public class PriceAgent implements Agent {
    private final HonestMarketDataFetcher fetcher = HonestMarketDataFetcher.getInstance();
    public Set<String> topics() { return Set.of("REQUEST_PRICE"); }
    public void onMessage(AgentMessage message, AgentBus bus) {
        String symbol = (String) message.payload.get("symbol");
        try {
            double price = fetcher.fetchSpotPrice(symbol);
            bus.publish(new AgentMessage("PRICE_DATA", Map.of("symbol", symbol, "price", price)));
        } catch (Exception e) {
            bus.publish(new AgentMessage("PRICE_ERROR", Map.of("symbol", symbol, "error", e.getMessage())));
        }
    }
    public String name() { return "PriceAgent"; }
}
