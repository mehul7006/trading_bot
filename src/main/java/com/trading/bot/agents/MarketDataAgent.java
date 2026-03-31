package com.trading.bot.agents;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import java.time.LocalDate;
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
        List<SimpleMarketData> all = fetcher.getRealMarketData5Min(symbol);
        if (all == null || all.isEmpty()) return all;

        // Filter to last `days` calendar days, but always keep ≥200 candles for indicator warm-up
        LocalDate cutoff = LocalDate.now().minusDays(days);
        int cutoffIndex = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).timestamp != null && !all.get(i).timestamp.toLocalDate().isBefore(cutoff)) {
                cutoffIndex = i;
                break;
            }
        }
        // Ensure at least 200 candles for indicator warm-up
        cutoffIndex = Math.min(cutoffIndex, all.size() - 200);
        cutoffIndex = Math.max(cutoffIndex, 0);
        return all.subList(cutoffIndex, all.size());
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
