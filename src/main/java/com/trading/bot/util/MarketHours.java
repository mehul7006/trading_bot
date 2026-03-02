package com.trading.bot.util;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Utility to check Indian Stock Market (NSE/BSE) trading hours.
 * Market Hours: 09:15 AM to 03:30 PM IST, Monday to Friday.
 */
public class MarketHours {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(15, 30);

    /**
     * Check if the market is currently open.
     */
    public static boolean isMarketOpen() {
        ZonedDateTime nowIST = ZonedDateTime.now(IST_ZONE);
        return isMarketOpen(nowIST);
    }

    /**
     * Check if the market was open at the given time.
     */
    public static boolean isMarketOpen(ZonedDateTime dateTime) {
        DayOfWeek day = dateTime.getDayOfWeek();
        LocalTime time = dateTime.toLocalTime();

        // Check Weekend
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }

        // Check Time (09:15 - 15:30)
        return !time.isBefore(MARKET_OPEN_TIME) && !time.isAfter(MARKET_CLOSE_TIME);
    }

    /**
     * Get a user-friendly status message.
     */
    public static String getMarketStatusMessage() {
        if (isMarketOpen()) {
            return "✅ Market is Open";
        } else {
            return "🔴 Market is Closed (Hours: Mon-Fri, 9:15 AM - 3:30 PM IST)";
        }
    }
}
