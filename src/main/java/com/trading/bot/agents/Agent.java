package com.trading.bot.agents;

import java.util.Set;

public interface Agent {
    Set<String> topics();
    void onMessage(AgentMessage message, AgentBus bus);
    String name();
}
