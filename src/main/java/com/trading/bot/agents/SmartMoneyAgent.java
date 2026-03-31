package com.trading.bot.agents;

import com.trading.bot.market.SimpleMarketData;
import com.trading.bot.smartmoney.FairValueGapDetector;
import com.trading.bot.smartmoney.LiquidityAnalyzer;
import com.trading.bot.smartmoney.OrderBlockDetector;
import java.util.*;

public class SmartMoneyAgent implements Agent {
    private final OrderBlockDetector obDetector = new OrderBlockDetector();
    private final FairValueGapDetector fvgDetector = new FairValueGapDetector();
    private final LiquidityAnalyzer liquidityAnalyzer = new LiquidityAnalyzer();

    public Set<String> topics() { return Set.of("REQUEST_SMC"); }
    
    public void onMessage(AgentMessage message, AgentBus bus) {
        String symbol = (String) message.payload.get("symbol");
        @SuppressWarnings("unchecked")
        List<SimpleMarketData> data = (List<SimpleMarketData>) message.payload.get("data");
        
        Map<String, Object> results = new HashMap<>();
        
        // 1. Order Block Analysis
        OrderBlockDetector.OrderBlockAnalysis obAnalysis = obDetector.detectOrderBlocks(data);
        results.put("orderBlocks", obAnalysis);
        
        // 2. Fair Value Gap Analysis
        FairValueGapDetector.FVGAnalysis fvgAnalysis = fvgDetector.detectFairValueGaps(data);
        results.put("fvg", fvgAnalysis);
        
        // 3. Liquidity Analysis
        LiquidityAnalyzer.LiquidityAnalysis liqAnalysis = liquidityAnalyzer.analyzeLiquidity(data);
        results.put("liquidity", liqAnalysis);
        
        // 4. Combined Smart Money Score (-100 to +100)
        double smcScore = calculateSMCScore(obAnalysis, fvgAnalysis, liqAnalysis);
        results.put("smcScore", smcScore);
        results.put("bias", smcScore > 20 ? "BULLISH" : (smcScore < -20 ? "BEARISH" : "NEUTRAL"));
        
        bus.publish(new AgentMessage("SMC_RESULT", Map.of("symbol", symbol, "analysis", results)));
    }
    
    private double calculateSMCScore(OrderBlockDetector.OrderBlockAnalysis ob, 
                                   FairValueGapDetector.FVGAnalysis fvg, 
                                   LiquidityAnalyzer.LiquidityAnalysis liq) {
        double score = 0;
        
        // OB Contribution (40%)
        score += ob.institutionalBias * 0.4;
        
        // FVG Contribution (30%)
        score += fvg.imbalanceStrength * 0.3;
        
        // Liquidity Contribution (30%)
        score += liq.liquidityScore * 0.3;
        
        return Math.max(-100, Math.min(100, score));
    }
    
    public String name() { return "SmartMoneyAgent"; }
}
