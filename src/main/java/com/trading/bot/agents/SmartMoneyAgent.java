package com.trading.bot.agents;

import com.trading.bot.market.SimpleMarketData;
import java.util.*;

public class SmartMoneyAgent implements Agent {
    public Set<String> topics() { return Set.of("REQUEST_SMC"); }
    public void onMessage(AgentMessage message, AgentBus bus) {
        String symbol = (String) message.payload.get("symbol");
        @SuppressWarnings("unchecked")
        List<SimpleMarketData> data = (List<SimpleMarketData>) message.payload.get("data");
        
        Map<String, Object> results = new HashMap<>();
        results.put("fvg", detectFVG(data));
        results.put("orderBlocks", detectOrderBlocks(data));
        
        bus.publish(new AgentMessage("SMC_RESULT", Map.of("symbol", symbol, "analysis", results)));
    }
    
    private List<String> detectFVG(List<SimpleMarketData> data) {
        List<String> fvgs = new ArrayList<>();
        if (data.size() < 3) return fvgs;
        for (int i = data.size() - 3; i < data.size() - 2; i++) {
            SimpleMarketData c1 = data.get(i);
            SimpleMarketData c3 = data.get(i+2);
            if (c1.high < c3.low) fvgs.add("Bullish FVG at " + c1.high + "-" + c3.low);
            else if (c1.low > c3.high) fvgs.add("Bearish FVG at " + c3.high + "-" + c1.low);
        }
        return fvgs;
    }
    
    private List<String> detectOrderBlocks(List<SimpleMarketData> data) {
        List<String> obs = new ArrayList<>();
        // Simple OB logic: Last opposite candle before a strong move
        return obs;
    }
    
    public String name() { return "SmartMoneyAgent"; }
}
