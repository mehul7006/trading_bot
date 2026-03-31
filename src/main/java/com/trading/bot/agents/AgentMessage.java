package com.trading.bot.agents;

import java.time.LocalDateTime;
import java.util.Map;

public class AgentMessage {
    public final String topic;
    public final Map<String, Object> payload;
    public final LocalDateTime time;
    public AgentMessage(String topic, Map<String, Object> payload) {
        this.topic = topic;
        this.payload = payload;
        this.time = LocalDateTime.now();
    }
}
