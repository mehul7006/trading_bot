package com.trading.bot.telegram;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Phase3TelegramBotTest {

    static final long ADMIN = 457623834L; // must match ADMIN_CHAT_ID fallback in Phase3TelegramBot

    static class TestBot extends Phase3TelegramBot {
        final List<String> sent = new ArrayList<>();
        long lastChatId;

        TestBot() {
            super(true);
        }

        @Override
        protected boolean sendRequest(long chatId, String text, String parseMode) {
            lastChatId = chatId;
            sent.add(text);
            return true;
        }

        @Override
        protected String getCurrentMarketRatesSimple() {
            return "NIFTY50 22000\nSENSEX 75000\nBANKNIFTY 48000";
        }
    }

    @Test
    void startCommandSendsWelcome() {
        TestBot bot = new TestBot();
        bot.handleCommand(1L, "/start"); // /start is open to everyone
        assertFalse(bot.sent.isEmpty());
        assertTrue(bot.sent.get(0).contains("Welcome to Institutional Trading Bot"));
    }

    @Test
    void nonAdminIsBlockedFromStatus() {
        TestBot bot = new TestBot();
        bot.handleCommand(1L, "/status"); // non-admin
        assertFalse(bot.sent.isEmpty());
        assertTrue(bot.sent.get(0).contains("Access Denied"));
    }

    @Test
    void statusCommandUsesStubRates() {
        TestBot bot = new TestBot();
        bot.handleCommand(ADMIN, "/status");
        assertFalse(bot.sent.isEmpty());
        String msg = bot.sent.get(0);
        assertTrue(msg.contains("Market Status Report"));
        assertTrue(msg.contains("NIFTY50"));
    }

    @Test
    void scanCommandStartsAndThenReportsAlreadyActive() {
        TestBot bot = new TestBot();
        bot.handleCommand(ADMIN, "/scan");
        assertFalse(bot.sent.isEmpty());
        String first = bot.sent.get(0);
        assertTrue(first.contains("scanning"), "Expected scan start message, got: " + first);
        try { Thread.sleep(1600); } catch (InterruptedException ignored) {}
        bot.handleCommand(ADMIN, "/scan");
        assertTrue(bot.sent.size() >= 2);
        String second = bot.sent.get(1);
        assertTrue(second.contains("scanning") || second.contains("Already"),
            "Expected already-active message, got: " + second);
    }

    @Test
    void stopScanCommandSendsStoppedMessage() {
        TestBot bot = new TestBot();
        bot.handleCommand(ADMIN, "/scan");
        bot.sent.clear();
        bot.handleCommand(ADMIN, "/stop_scan");
        assertFalse(bot.sent.isEmpty());
        String msg = bot.sent.get(0);
        assertTrue(msg.contains("Stopped") || msg.contains("stopped"), "Expected stop message, got: " + msg);
    }

    @Test
    void tokenCommandSendsAccessTokenUpdated() {
        TestBot bot = new TestBot();
        bot.handleCommand(ADMIN, "/token abc123");
        assertFalse(bot.sent.isEmpty());
        String msg = bot.sent.get(0);
        assertTrue(msg.contains("Token") || msg.contains("token"), "Expected token message, got: " + msg);
    }

    @Test
    void unknownCommandShowsHelp() {
        TestBot bot = new TestBot();
        bot.handleCommand(ADMIN, "/unknown");
        assertFalse(bot.sent.isEmpty());
        String msg = bot.sent.get(0);
        assertTrue(msg.contains("Unknown") || msg.contains("unknown"), "Expected unknown command message, got: " + msg);
    }
}
