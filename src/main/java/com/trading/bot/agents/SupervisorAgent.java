package com.trading.bot.agents;

import java.util.Map;
import java.util.Set;

public class SupervisorAgent implements Agent {
    public Set<String> topics() { return Set.of("PRICE_DATA","PATTERN_RESULT","INDICATORS_RESULT","MARKET_DATA_RESULT","PRICE_ERROR","PATTERN_ERROR","INDICATORS_ERROR","MARKET_DATA_ERROR"); }
    public void onMessage(AgentMessage message, AgentBus bus) {
        String status = message.topic.endsWith("ERROR") ? "FAIL" : "OK";
        bus.publish(new AgentMessage("SUPERVISOR_STATUS", Map.of("source", message.topic, "status", status, "time", message.time)));
    }
    public String name() { return "SupervisorAgent"; }
}
