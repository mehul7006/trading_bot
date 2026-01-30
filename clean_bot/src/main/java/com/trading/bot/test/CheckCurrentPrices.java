package com.trading.bot.test;

import com.trading.bot.market.HonestMarketDataFetcher;
import java.util.Map;

public class CheckCurrentPrices {
    public static void main(String[] args) {
        System.out.println("🔍 Fetching latest closing prices from Upstox API...");
        
        HonestMarketDataFetcher fetcher = new HonestMarketDataFetcher();
        
        try {
            // This fetches the latest available data point (Real Time or Last Close)
            Map<String, Double> prices = fetcher.getHonestMarketSnapshot();
            
            System.out.println("\n📈 LATEST MARKET PRICES:");
            System.out.println("=======================");
            
            prices.forEach((symbol, price) -> {
                System.out.printf("🔹 %-10s : ₹%,.2f%n", symbol, price);
            });
            
            System.out.println("=======================");
            System.out.println("✅ Data fetched successfully via Upstox Real-Time API");
            
        } catch (Exception e) {
            System.err.println("❌ Failed to fetch prices: " + e.getMessage());
        }
    }
}
