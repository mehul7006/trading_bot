package com.trading.bot.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpstoxApiTest {
    private static final Logger logger = LoggerFactory.getLogger(UpstoxApiTest.class);
    
    public static void main(String[] args) {
        System.out.println("Testing Upstox API Connection...");
        
        try {
            System.out.println("Creating UpstoxApiConnector...");
            UpstoxApiConnector connector = new UpstoxApiConnector();
            
            String[] symbols = {
                "NSE_INDEX|Nifty 50",
                "BSE_INDEX|SENSEX",
                "NSE_INDEX|Nifty Bank"
            };
            
            for (String symbol : symbols) {
                System.out.println("\nFetching data for " + symbol);
                try {
                    MarketData data = connector.fetchMarketData(symbol);
                    
                    if (data != null) {
                        System.out.println("Success! Current price data:");
                        System.out.println("Symbol: " + data.getSymbol());
                        System.out.println("Last Price: " + data.getPrice());
                        System.out.println("Day's Open: " + data.getOpen());
                        System.out.println("Day's High: " + data.getHigh());
                        System.out.println("Day's Low: " + data.getLow());
                    
                    if (data.getPriceHistory() != null && !data.getPriceHistory().isEmpty()) {
                        System.out.println("Latest Price History: " + data.getPriceHistory().get(data.getPriceHistory().size() - 1));
                    }
                } else {
                    System.err.println("Failed to fetch data for " + symbol);
                }
            }
            
            System.out.println("\nAPI test completed successfully!");
            
        } catch (Exception e) {
            logger.error("Error testing Upstox API: ", e);
            System.err.println("Error testing Upstox API: " + e.getMessage());
            e.printStackTrace();
        }
    }
}