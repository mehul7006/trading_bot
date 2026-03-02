package com.trading.bot.agents;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AgentBus {
    private final Map<String, List<Agent>> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    public void register(Agent agent) {
        for (String t : agent.topics()) {
            subscribers.computeIfAbsent(t, k -> new ArrayList<>()).add(agent);
        }
    }
    public void publish(AgentMessage message) {
        List<Agent> list = subscribers.getOrDefault(message.topic, Collections.emptyList());
        for (Agent a : list) {
            executor.submit(() -> a.onMessage(message, this));
        }
    }
}
