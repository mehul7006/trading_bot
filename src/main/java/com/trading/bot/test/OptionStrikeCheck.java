package com.trading.bot.test;

import com.trading.bot.market.HonestMarketDataFetcher;
import com.trading.bot.market.SimpleMarketData;
import java.time.LocalDate;
import java.util.List;

public class OptionStrikeCheck {
    public static void main(String[] args) {
        HonestMarketDataFetcher fetcher = HonestMarketDataFetcher.getInstance();
        try {
            checkStrike("NIFTY50", 25100, "CE", fetcher);
            checkStrike("SENSEX", 83000, "CE", fetcher);
            checkStrike("SENSEX", 82500, "PE", fetcher);
        } catch (Exception e) {
            System.err.println("❌ Error running strike checks: " + e.getMessage());
        }
    }
    
    private static void checkStrike(String symbol, double strike, String type, HonestMarketDataFetcher fetcher) throws Exception {
        System.out.printf("\n🔍 Checking %s %,.0f %s option...\n", symbol, strike, type);
        String instrumentKey = fetcher.findOptionInstrumentKey(symbol, strike, type);
        if (instrumentKey == null || instrumentKey.isEmpty()) {
            Double ltp = fetcher.getOptionStrikeLTP(symbol, strike, type);
            if (ltp != null) {
                System.out.printf("📈 Current LTP (chain): %.2f\n", ltp);
            } else {
                System.out.println("❌ Could not resolve instrument key or LTP for requested strike.");
            }
            return;
        }
        SimpleMarketData latest = fetcher.fetchOptionLatestOHLC(instrumentKey);
        if (latest != null) {
            System.out.printf("📈 Latest: %s | Close: %.2f | High: %.2f | Low: %.2f\n", 
                latest.symbol, latest.price, latest.high, latest.low);
        } else {
            System.out.println("⚠️ No latest OHLC available.");
        }
        
        String toDate = LocalDate.now().toString();
        String fromDate = LocalDate.now().minusDays(5).toString();
        List<SimpleMarketData> candles = fetcher.fetchHistoricalCandlesByInstrument(instrumentKey, "30minute", fromDate, toDate);
        System.out.printf("🕘 5-day chart (%d candles):\n", candles.size());
        int count = 0;
        for (SimpleMarketData c : candles) {
            if (++count > 30) break;
            System.out.printf("   %s | O: %.2f H: %.2f L: %.2f C: %.2f\n", 
                c.timestamp, c.open, c.high, c.low, c.price);
        }
        if (candles.size() > 30) {
            System.out.println("   ... (truncated)");
        }
    }
}
