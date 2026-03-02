package com.trading.bot.strategy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * News Sentiment Utility
 * 
 * Ideally, this would connect to a News API (Google News, NewsAPI.org).
 * For now, it reads from a file that the user (or a separate script) updates.
 * 
 * File Format (news_sentiment.txt):
 * Line 1: SENTIMENT (BULLISH | BEARISH | NEUTRAL)
 * Line 2: HEADLINE (e.g., "OPEC cuts production by 2M barrels")
 */
public class NewsSentimentUtils {

    private static final String SENTIMENT_FILE = "news_sentiment.txt";

    public static class NewsData {
        public String sentiment;
        public String headline;
        
        public NewsData(String sentiment, String headline) {
            this.sentiment = sentiment;
            this.headline = headline;
        }
    }

    public static NewsData getLatestSentiment() {
        // 1. Fallback to local file (Manual override)
        try {
            File file = new File(SENTIMENT_FILE);
            if (!file.exists()) {
                return new NewsData("NEUTRAL", "No news data available (RSS failed + No file).");
            }

            List<String> lines = Files.readAllLines(Paths.get(SENTIMENT_FILE));
            if (lines.isEmpty()) {
                return new NewsData("NEUTRAL", "News file empty.");
            }

            String sentiment = lines.get(0).trim().toUpperCase();
            String headline = lines.size() > 1 ? lines.get(1) : "No details.";

            if (!sentiment.equals("BULLISH") && !sentiment.equals("BEARISH")) {
                sentiment = "NEUTRAL";
            }

            return new NewsData(sentiment, headline);
        } catch (Exception e) {
            return new NewsData("NEUTRAL", "Error reading news: " + e.getMessage());
        }
    }
}
