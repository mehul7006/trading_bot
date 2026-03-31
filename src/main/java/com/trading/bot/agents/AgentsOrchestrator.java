package com.trading.bot.agents;

import java.util.Map;

public class AgentsOrchestrator {
    public static void main(String[] args) {
        AgentBus bus = new AgentBus();
        bus.register(new PriceAgent());
        bus.register(new PatternAgent());
        bus.register(new IndicatorAgent());
        bus.register(new MarketDataAgent());
        bus.register(new SupervisorAgent());
        bus.register(new NotificationAgent());
        for (String symbol : new String[] {"NIFTY50","SENSEX"}) {
            bus.publish(new AgentMessage("REQUEST_PRICE", Map.of("symbol", symbol)));
            bus.publish(new AgentMessage("REQUEST_MARKET_DATA", Map.of("symbol", symbol)));
            bus.publish(new AgentMessage("REQUEST_PATTERN", Map.of("symbol", symbol)));
            bus.publish(new AgentMessage("REQUEST_INDICATORS", Map.of("symbol", symbol)));
        }
    }
}
