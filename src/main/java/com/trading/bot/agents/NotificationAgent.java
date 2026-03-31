package com.trading.bot.agents;

import java.util.Map;
import java.util.Set;

public class NotificationAgent implements Agent {
    public Set<String> topics() { return Set.of("SUPERVISOR_STATUS","INDICATORS_RESULT","PATTERN_RESULT"); }
    public void onMessage(AgentMessage message, AgentBus bus) {
        if (message.topic.equals("SUPERVISOR_STATUS")) {
            String status = (String) message.payload.get("status");
            bus.publish(new AgentMessage("NOTIFY_STATUS", Map.of("status", status)));
        } else {
            String symbol = (String) message.payload.get("symbol");
            bus.publish(new AgentMessage("NOTIFY_SIGNAL", Map.of("symbol", symbol, "source", message.topic)));
        }
    }
    public String name() { return "NotificationAgent"; }
}
