package com.trading.bot.telegram;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Phase3TelegramBotTest {

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
            return "NIFTY50 22000\nSENSEX 75000";
        }
    }

    @Test
    void startCommandSendsWelcome() {
        TestBot bot = new TestBot();
        bot.handleCommand(1L, "/start");
        assertFalse(bot.sent.isEmpty());
        String msg = bot.sent.get(0);
        assertTrue(msg.contains("Welcome to Institutional Trading Bot"));
    }

    @Test
    void statusCommandUsesStubRates() {
        TestBot bot = new TestBot();
        bot.handleCommand(1L, "/status");
        assertFalse(bot.sent.isEmpty());
        String msg = bot.sent.get(0);
        assertTrue(msg.contains("Market Status Report"));
        assertTrue(msg.contains("NIFTY50"));
    }

    @Test
    void scanCommandStartsAndThenReportsAlreadyActive() {
        TestBot bot = new TestBot();
        bot.handleCommand(1L, "/scan");
        assertFalse(bot.sent.isEmpty());
        String first = bot.sent.get(0);
        assertTrue(first.contains("Scanning Started"));
        bot.handleCommand(1L, "/scan");
        assertTrue(bot.sent.size() >= 2);
        String second = bot.sent.get(1);
        assertTrue(second.contains("Scanning is Already Active"));
    }

    @Test
    void stopScanCommandSendsStoppedMessage() {
        TestBot bot = new TestBot();
        bot.handleCommand(1L, "/scan");
        bot.sent.clear();
        bot.handleCommand(1L, "/stop_scan");
        assertFalse(bot.sent.isEmpty());
        String msg = bot.sent.get(0);
        assertTrue(msg.contains("Scanning Stopped"));
    }

    @Test
    void tokenCommandSendsAccessTokenUpdated() {
        TestBot bot = new TestBot();
        bot.handleCommand(1L, "/token abc123");
        assertFalse(bot.sent.isEmpty());
        String msg = bot.sent.get(0);
        assertTrue(msg.contains("Access Token Updated"));
    }

    @Test
    void unknownCommandShowsHelpWithValidCommands() {
        TestBot bot = new TestBot();
        bot.handleCommand(1L, "/unknown");
        assertFalse(bot.sent.isEmpty());
        String msg = bot.sent.get(0);
        assertTrue(msg.contains("Unknown Command"));
        assertTrue(msg.contains("/start"));
        assertTrue(msg.contains("/scan"));
        assertTrue(msg.contains("/status"));
        assertTrue(msg.contains("/token"));
    }
}

