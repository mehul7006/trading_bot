package com.trading.bot.agents;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AgentBenchmarkRunner {
    public static void main(String[] args) throws InterruptedException {
        AgentBus bus = new AgentBus();
        bus.register(new PriceAgent());
        bus.register(new PatternAgent());
        bus.register(new IndicatorAgent());
        bus.register(new MarketDataAgent());
        bus.register(new SupervisorAgent());
        bus.register(new NotificationAgent());
        
        Map<String, Long> startTimes = new ConcurrentHashMap<>();
        Map<String, Long> endTimes = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(8); // 4 requests * 2 symbols
        
        Agent latencyCollector = new Agent() {
            public java.util.Set<String> topics() { return java.util.Set.of("PRICE_DATA","PATTERN_RESULT","INDICATORS_RESULT","MARKET_DATA_RESULT"); }
            public void onMessage(AgentMessage message, AgentBus b) {
                long end = System.nanoTime();
                String key = message.topic + "|" + message.payload.get("symbol");
                endTimes.put(key, end);
                latch.countDown();
            }
            public String name() { return "LatencyCollector"; }
        };
        bus.register(latencyCollector);
        
        long overallStart = System.nanoTime();
        for (String symbol : new String[] {"NIFTY50","SENSEX"}) {
            startTimes.put("PRICE_DATA|" + symbol, System.nanoTime());
            bus.publish(new AgentMessage("REQUEST_PRICE", Map.of("symbol", symbol)));
            startTimes.put("MARKET_DATA_RESULT|" + symbol, System.nanoTime());
            bus.publish(new AgentMessage("REQUEST_MARKET_DATA", Map.of("symbol", symbol)));
            startTimes.put("PATTERN_RESULT|" + symbol, System.nanoTime());
            bus.publish(new AgentMessage("REQUEST_PATTERN", Map.of("symbol", symbol)));
            startTimes.put("INDICATORS_RESULT|" + symbol, System.nanoTime());
            bus.publish(new AgentMessage("REQUEST_INDICATORS", Map.of("symbol", symbol)));
        }
        
        latch.await(30, TimeUnit.SECONDS);
        long overallEnd = System.nanoTime();
        
        System.out.println("\n=== Agent Benchmark ===");
        for (String symbol : new String[] {"NIFTY50","SENSEX"}) {
            for (String topic : new String[] {"PRICE_DATA","MARKET_DATA_RESULT","PATTERN_RESULT","INDICATORS_RESULT"}) {
                String key = topic + "|" + symbol;
                Long s = startTimes.get(key);
                Long e = endTimes.get(key);
                if (s != null && e != null) {
                    double ms = (e - s) / 1_000_000.0;
                    System.out.printf("%s %-8s : %.1f ms%n", topic, symbol, ms);
                } else {
                    System.out.printf("%s %-8s : N/A%n", topic, symbol);
                }
            }
        }
        System.out.printf("Total wall time: %.1f ms%n", (overallEnd - overallStart) / 1_000_000.0);
    }
}
