package com.trading.bot.agents;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MarketDataAgent: Responsible for providing high-quality market data.
 * It uses HonestMarketDataFetcher internally and handles resampling/caching.
 */
public class MarketDataAgent implements Agent {
    private final HonestMarketDataFetcher fetcher;

    public MarketDataAgent() {
        this(HonestMarketDataFetcher.getInstance());
    }

    public MarketDataAgent(HonestMarketDataFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public List<SimpleMarketData> getHistoricalData(String symbol, int days) throws Exception {
        return fetcher.getRealMarketData5Min(symbol);
    }

    @Override
    public Set<String> topics() {
        return Set.of("REQUEST_MARKET_DATA");
    }

    @Override
    public void onMessage(AgentMessage message, AgentBus bus) {
        String symbol = (String) message.payload.get("symbol");
        try {
            List<SimpleMarketData> data = getHistoricalData(symbol, 120);
            bus.publish(new AgentMessage("MARKET_DATA_RESULT", Map.of("symbol", symbol, "data", data)));
        } catch (Exception e) {
            bus.publish(new AgentMessage("MARKET_DATA_ERROR", Map.of("symbol", symbol, "error", e.getMessage())));
        }
    }

    @Override
    public String name() {
        return "MarketDataAgent";
    }
}
