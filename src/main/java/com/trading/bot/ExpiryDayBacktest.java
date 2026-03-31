package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ExpiryDayBacktest — Tests special expiry-day window (13:30–15:25 IST) only.
 * Does NOT touch live bot. Shows call count, win rate, net points before you decide.
 *
 * Expiry schedule:
 *   NIFTY50   → every Thursday
 *   BANKNIFTY → every Thursday
 *   SENSEX    → every Friday
 *
 * Strategy (expiry afternoon):
 *   Window  : 13:30 – 15:25 IST
 *   Logic   : EMA10/EMA20 momentum + RSI + strong candle body confirmation
 *   Target  : 2.0 × ATR14  (expiry = bigger swings)
 *   SL      : 0.8 × ATR14  (tight stop — expiry moves fast & decisive)
 *   Cooldown: 15 min between signals per symbol (avoid whipsaws)
 *   Lookahead: remaining candles until 15:25, max 24 candles
 */
public class ExpiryDayBacktest {

    // ── Expiry detection ──────────────────────────────────────────────────────
    // Corrected expiry schedule (user-confirmed):
    //   NIFTY50   → Tuesday
    //   BANKNIFTY → Wednesday  (assumed — please confirm)
    //   SENSEX    → Thursday
    private static final Map<String, DayOfWeek> EXPIRY_DOW = Map.of(
        "NIFTY50",   DayOfWeek.TUESDAY,
        "BANKNIFTY", DayOfWeek.WEDNESDAY,
        "SENSEX",    DayOfWeek.THURSDAY
    );

    // ── Expiry window ─────────────────────────────────────────────────────────
    private static final LocalTime EXPIRY_START = LocalTime.of(13, 30);
    private static final LocalTime EXPIRY_END   = LocalTime.of(15, 25);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 25);

    // ── Signal gates ──────────────────────────────────────────────────────────
    private static final Map<String, Double> MIN_POINTS = Map.of(
        "NIFTY50",   25.0,
        "BANKNIFTY", 70.0,
        "SENSEX",    60.0
    );
    private static final double ATR_TARGET_MULT = 2.0;
    private static final double ATR_SL_MULT     = 0.8;
    private static final long   COOLDOWN_MS     = 15L * 60 * 1000;
    private static final int    LOOKAHEAD       = 24;

    // ── Signal record ─────────────────────────────────────────────────────────
    static class ExpirySignal {
        String date, time, symbol, direction, outcome, reason;
        double entry, targetPts, slPts, targetPrice, slPrice, pnl, confidence;
    }

    private static final int LOSS = 0, PARTIAL = 1, WIN = 2;

    public static void main(String[] args) {
        System.out.println("Loading candle data for expiry-day backtest...");
        MarketDataAgent mdAgent = new MarketDataAgent();

        String[] symbols = {"NIFTY50", "BANKNIFTY", "SENSEX"};
        List<ExpirySignal> allSignals = new ArrayList<>();

        for (String sym : symbols) {
            allSignals.addAll(runExpiry(sym, mdAgent));
        }

        allSignals.sort(Comparator.comparing((ExpirySignal s) -> s.date).thenComparing(s -> s.time));
        printReport(allSignals);
    }

    // ── Main backtest per symbol ──────────────────────────────────────────────
    private static List<ExpirySignal> runExpiry(String symbol, MarketDataAgent mdAgent) {
        List<ExpirySignal> signals = new ArrayList<>();
        try {
            List<SimpleMarketData> data = mdAgent.getHistoricalData(symbol, 120);
            if (data == null || data.size() < 50) {
                System.err.println("Not enough data for " + symbol);
                return signals;
            }

            DayOfWeek expiryDow = EXPIRY_DOW.get(symbol);
            long lastSignalMs = 0;
            LocalDate lastSignalDate = null;

            System.out.printf("%s: scanning %d candles for expiry days (DOW=%s)...%n",
                symbol, data.size(), expiryDow);

            for (int i = 20; i < data.size(); i++) {
                SimpleMarketData candle = data.get(i);
                if (candle.timestamp == null) continue;

                LocalDate date = candle.timestamp.toLocalDate();
                LocalTime time = candle.timestamp.toLocalTime();

                // ── Only expiry day ──────────────────────────────────────────
                if (date.getDayOfWeek() != expiryDow) continue;

                // ── Only 13:30 – 15:25 window ────────────────────────────────
                if (time.isBefore(EXPIRY_START) || time.isAfter(EXPIRY_END)) continue;

                // ── Reset cooldown each new day ───────────────────────────────
                if (!date.equals(lastSignalDate)) {
                    lastSignalMs = 0;
                    lastSignalDate = date;
                }

                // ── 15-min cooldown between signals ───────────────────────────
                long candleMs = candle.timestamp
                    .atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
                if (candleMs - lastSignalMs < COOLDOWN_MS) continue;

                // ── Need at least 20 candles of history ───────────────────────
                List<SimpleMarketData> hist = data.subList(Math.max(0, i - 50), i + 1);
                if (hist.size() < 20) continue;

                // ── Generate expiry signal ────────────────────────────────────
                ExpirySignalResult res = generateExpirySignal(symbol, hist, candle);
                if (res == null) continue;

                // ── Check min points threshold ────────────────────────────────
                if (res.targetPts < MIN_POINTS.getOrDefault(symbol, 25.0)) continue;

                lastSignalMs = candleMs;

                // ── Verify outcome (capped at market close 15:25) ─────────────
                int outcome = verifyOutcome(data, i, res, time);
                double pnl;
                String outcomeLabel;
                switch (outcome) {
                    case WIN     -> { outcomeLabel = "WIN";         pnl =  res.targetPts; }
                    case PARTIAL -> { outcomeLabel = "PARTIAL WIN"; pnl =  res.targetPts * 0.4; }
                    default      -> { outcomeLabel = "LOSS";        pnl = -res.slPts; }
                }

                ExpirySignal sig = new ExpirySignal();
                sig.date        = date.toString();
                sig.time        = time.format(DateTimeFormatter.ofPattern("HH:mm"));
                sig.symbol      = symbol;
                sig.direction   = res.direction;
                sig.entry       = candle.price;
                sig.targetPts   = res.targetPts;
                sig.slPts       = res.slPts;
                sig.targetPrice = res.direction.equals("UP") ? candle.price + res.targetPts : candle.price - res.targetPts;
                sig.slPrice     = res.direction.equals("UP") ? candle.price - res.slPts    : candle.price + res.slPts;
                sig.confidence  = res.confidence;
                sig.outcome     = outcomeLabel;
                sig.pnl         = pnl;
                sig.reason      = res.reason;
                signals.add(sig);
            }
        } catch (Exception e) {
            System.err.println("Error for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
        }
        return signals;
    }

    // ── Expiry signal generation logic ────────────────────────────────────────
    static class ExpirySignalResult {
        String direction, reason;
        double targetPts, slPts, confidence;
    }

    private static ExpirySignalResult generateExpirySignal(
            String symbol, List<SimpleMarketData> hist, SimpleMarketData candle) {

        double price = candle.price;
        double atr   = calcATR(hist, 14);
        double rsi   = calcRSI(hist, 14);
        double ema10 = calcEMA(hist, 10);
        double ema20 = calcEMA(hist, 20);

        // EMA slope (3-candle look-back)
        double ema10Prev = hist.size() > 13 ? calcEMA(hist.subList(0, hist.size() - 3), 10) : ema10;
        double ema20Prev = hist.size() > 23 ? calcEMA(hist.subList(0, hist.size() - 3), 20) : ema20;
        boolean ema10Rising  = ema10 > ema10Prev;
        boolean ema10Falling = ema10 < ema10Prev;
        boolean ema20Rising  = ema20 > ema20Prev;
        boolean ema20Falling = ema20 < ema20Prev;

        // Strong candle body confirmation
        boolean bullBody = hasBullishBody(candle);
        boolean bearBody = hasBearishBody(candle);

        // Price vs EMA20 (momentum filter)
        boolean aboveEMA20 = price > ema20;
        boolean belowEMA20 = price < ema20;

        // Consecutive candle trend (last 3 candles)
        boolean last3Up   = last3Trend(hist, true);
        boolean last3Down = last3Trend(hist, false);

        // Volume surge (if available)
        boolean volSurge = hasVolumeSurge(hist);

        String direction = null;
        String reason    = "";
        double conf      = 87.0;

        // ── BULLISH SETUP ─────────────────────────────────────────────────────
        // Criteria: EMA10 rising + price above EMA20 + RSI 50-75 + bullish body OR last3Up
        if (ema10Rising && aboveEMA20 && rsi > 50 && rsi < 80) {
            if (bullBody || last3Up) {
                direction = "UP";
                reason = "Expiry Momentum UP | EMA10↑ | Price>EMA20 | RSI=" + String.format("%.0f", rsi);
                if (bullBody)  { conf += 3; reason += " | BullCandle"; }
                if (last3Up)   { conf += 2; reason += " | 3-Bar↑"; }
                if (volSurge)  { conf += 3; reason += " | VolSurge"; }
            }
        }

        // ── BEARISH SETUP ─────────────────────────────────────────────────────
        // Criteria: EMA10 falling + price below EMA20 + RSI 25-50 + bearish body OR last3Down
        else if (!ema10Rising && belowEMA20 && rsi < 50 && rsi > 20) {
            if (bearBody || last3Down) {
                direction = "DOWN";
                reason = "Expiry Momentum DOWN | EMA10↓ | Price<EMA20 | RSI=" + String.format("%.0f", rsi);
                if (bearBody)  { conf += 3; reason += " | BearCandle"; }
                if (last3Down) { conf += 2; reason += " | 3-Bar↓"; }
                if (volSurge)  { conf += 3; reason += " | VolSurge"; }
            }
        }

        // ── Strong RSI extremes override (expiry pinning reversals) ──────────
        // If RSI oversold (<28) — sharp recovery expected
        else if (rsi < 28 && bullBody) {
            direction = "UP";
            reason = "Expiry Oversold Recovery | RSI=" + String.format("%.0f", rsi) + " | BullCandle";
            conf = 88.0;
        }
        // If RSI overbought (>72) — sharp rejection expected
        else if (rsi > 72 && bearBody) {
            direction = "DOWN";
            reason = "Expiry Overbought Rejection | RSI=" + String.format("%.0f", rsi) + " | BearCandle";
            conf = 88.0;
        }

        if (direction == null) return null;

        // ── Dynamic Target/SL based on ATR ────────────────────────────────────
        double targetPts = Math.round(atr * ATR_TARGET_MULT * 2) / 2.0; // round to 0.5
        double slPts     = Math.round(atr * ATR_SL_MULT     * 2) / 2.0;

        // Apply symbol-specific minimum boost on expiry
        double symMin = MIN_POINTS.getOrDefault(symbol, 25.0);
        if (targetPts < symMin) targetPts = symMin;
        if (slPts < symMin * 0.4) slPts = Math.round(symMin * 0.4);

        ExpirySignalResult r = new ExpirySignalResult();
        r.direction  = direction;
        r.targetPts  = targetPts;
        r.slPts      = slPts;
        r.confidence = Math.min(conf, 99.0);
        r.reason     = reason;
        return r;
    }

    // ── Outcome check (capped at 15:25 close) ─────────────────────────────────
    private static int verifyOutcome(List<SimpleMarketData> data, int idx,
            ExpirySignalResult res, LocalTime signalTime) {

        double entry  = data.get(idx).price;
        String dir    = res.direction;
        double tgtPx  = dir.equals("UP") ? entry + res.targetPts : entry - res.targetPts;
        double slPx   = dir.equals("UP") ? entry - res.slPts    : entry + res.slPts;
        double halfPx = dir.equals("UP") ? entry + res.targetPts * 0.5 : entry - res.targetPts * 0.5;
        boolean half  = false;

        // Calculate max candles before 15:25
        long minsLeft = Duration.between(signalTime, MARKET_CLOSE).toMinutes();
        int maxLook   = (int) Math.min(LOOKAHEAD, Math.max(1, minsLeft / 5));

        for (int j = 1; j <= maxLook && (idx + j) < data.size(); j++) {
            SimpleMarketData c = data.get(idx + j);
            // Stop at next day
            if (!c.timestamp.toLocalDate().equals(data.get(idx).timestamp.toLocalDate())) break;
            if (dir.equals("UP")) {
                if (c.low  <= slPx)  return half ? PARTIAL : LOSS;
                if (c.high >= tgtPx) return WIN;
                if (c.high >= halfPx) half = true;
            } else {
                if (c.high >= slPx)  return half ? PARTIAL : LOSS;
                if (c.low  <= tgtPx) return WIN;
                if (c.low  <= halfPx) half = true;
            }
        }
        return half ? PARTIAL : LOSS;
    }

    // ── Indicator helpers ─────────────────────────────────────────────────────
    private static double calcATR(List<SimpleMarketData> data, int p) {
        if (data.size() < p + 1) return 15.0;
        double sum = 0;
        for (int i = data.size() - p; i < data.size(); i++) {
            SimpleMarketData c = data.get(i), prev = data.get(i - 1);
            sum += Math.max(c.high - c.low, Math.max(Math.abs(c.high - prev.price), Math.abs(c.low - prev.price)));
        }
        return sum / p;
    }

    private static double calcRSI(List<SimpleMarketData> data, int p) {
        if (data.size() < p + 1) return 50;
        double gain = 0, loss = 0;
        for (int i = data.size() - p; i < data.size(); i++) {
            double d = data.get(i).price - data.get(i - 1).price;
            if (d > 0) gain += d; else loss -= d;
        }
        if (loss == 0) return 100;
        return 100 - (100 / (1 + gain / loss));
    }

    private static double calcEMA(List<SimpleMarketData> data, int p) {
        if (data.isEmpty()) return 0;
        double k = 2.0 / (p + 1);
        double ema = data.get(0).price;
        for (int i = 1; i < data.size(); i++) ema = data.get(i).price * k + ema * (1 - k);
        return ema;
    }

    private static boolean hasBullishBody(SimpleMarketData c) {
        double range = c.high - c.low;
        return range > 0.001 && c.price > c.open && (c.price - c.open) / range > 0.40;
    }

    private static boolean hasBearishBody(SimpleMarketData c) {
        double range = c.high - c.low;
        return range > 0.001 && c.price < c.open && (c.open - c.price) / range > 0.40;
    }

    private static boolean last3Trend(List<SimpleMarketData> data, boolean up) {
        if (data.size() < 4) return false;
        int n = data.size();
        if (up) return data.get(n-1).price > data.get(n-2).price && data.get(n-2).price > data.get(n-3).price;
        return data.get(n-1).price < data.get(n-2).price && data.get(n-2).price < data.get(n-3).price;
    }

    private static boolean hasVolumeSurge(List<SimpleMarketData> data) {
        if (data.size() < 6) return false;
        double lastVol = data.get(data.size() - 1).volume;
        if (lastVol == 0) return false; // volume not available
        double avgVol = data.subList(data.size() - 6, data.size() - 1)
            .stream().mapToDouble(d -> d.volume).average().orElse(0);
        return avgVol > 0 && lastVol > avgVol * 1.5;
    }

    // ── Report ────────────────────────────────────────────────────────────────
    private static void printReport(List<ExpirySignal> signals) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║     EXPIRY DAY BACKTEST — 13:30–15:25 SPECIAL WINDOW               ║");
        System.out.println("║     (NIFTY/BANKNIFTY: Thursday | SENSEX: Friday)                   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");

        if (signals.isEmpty()) {
            System.out.println("  No expiry-day signals found in data range.");
            return;
        }

        // Group by date
        Map<String, List<ExpirySignal>> byDate = new LinkedHashMap<>();
        for (ExpirySignal s : signals) byDate.computeIfAbsent(s.date, k -> new ArrayList<>()).add(s);

        int gTotal = 0, gWin = 0, gPartial = 0, gLoss = 0;
        double gNet = 0;

        for (Map.Entry<String, List<ExpirySignal>> e : byDate.entrySet()) {
            String date = e.getKey();
            List<ExpirySignal> day = e.getValue();
            int dW = 0, dP = 0, dL = 0; double dNet = 0;

            System.out.println();
            System.out.printf("  DATE: %s  [Expiry Day — %d calls]%n", date, day.size());
            System.out.println("  " + "─".repeat(90));

            int n = 0;
            for (ExpirySignal s : day) {
                n++;
                String icon = s.outcome.equals("WIN") ? "WIN" : s.outcome.equals("PARTIAL WIN") ? "PART" : "LOSS";
                System.out.printf("  #%d | %s | %-10s | %-5s | Entry:%10.2f | Tgt:+%.0fpts | SL:-%.0fpts | %s | P&L:%+.0f | %s%n",
                    n, s.time, s.symbol, s.direction, s.entry, s.targetPts, s.slPts, icon, s.pnl,
                    s.reason.length() > 50 ? s.reason.substring(0, 50) + "..." : s.reason);
                if (s.outcome.equals("WIN"))          dW++;
                else if (s.outcome.equals("PARTIAL WIN")) dP++;
                else                                  dL++;
                dNet += s.pnl;
            }
            double dWR = (dW + dP + dL) > 0 ? (dW + dP) * 100.0 / (dW + dP + dL) : 0;
            System.out.printf("  DAY SUMMARY: Calls=%d | WIN=%d PART=%d LOSS=%d | WR=%.0f%% | Net=%+.0fpts%n",
                day.size(), dW, dP, dL, dWR, dNet);

            gTotal += day.size(); gWin += dW; gPartial += dP; gLoss += dL; gNet += dNet;
        }

        // Per-symbol
        System.out.println();
        System.out.println("╔═══════════════╦═══════╦══════╦═════════╦══════════╦════════════════╗");
        System.out.println("║ Symbol        ║ Calls ║ Wins ║ Partial ║  WinRate ║   Net Points   ║");
        System.out.println("╠═══════════════╬═══════╬══════╬═════════╬══════════╬════════════════╣");
        for (String sym : new String[]{"NIFTY50", "BANKNIFTY", "SENSEX"}) {
            int sT=0,sW=0,sP=0; double sN=0;
            for (ExpirySignal s : signals) {
                if (!s.symbol.equals(sym)) continue;
                sT++;
                if (s.outcome.equals("WIN")) sW++;
                else if (s.outcome.equals("PARTIAL WIN")) sP++;
                sN += s.pnl;
            }
            double sWR = sT > 0 ? (sW + sP) * 100.0 / sT : 0;
            System.out.printf("║ %-13s ║  %3d  ║  %3d ║   %3d   ║  %5.1f%%  ║  %+11.1f   ║%n",
                sym, sT, sW, sP, sWR, sN);
        }
        double gWR = gTotal > 0 ? (gWin + gPartial) * 100.0 / gTotal : 0;
        System.out.println("╠═══════════════╬═══════╬══════╬═════════╬══════════╬════════════════╣");
        System.out.printf("║ COMBINED      ║  %3d  ║  %3d ║   %3d   ║  %5.1f%%  ║  %+11.1f   ║%n",
            gTotal, gWin, gPartial, gWR, gNet);
        System.out.println("╚═══════════════╩═══════╩══════╩═════════╩══════════╩════════════════╝");

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              EXPIRY WINDOW FINAL SUMMARY                            ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        int expiryDays = byDate.size();
        System.out.printf("║  Expiry Days Scanned    : %-43d║%n", expiryDays);
        System.out.printf("║  Total Calls Generated  : %-43d║%n", gTotal);
        System.out.printf("║  Avg Calls/Expiry Day   : %-43s║%n", String.format("%.1f", expiryDays > 0 ? (double)gTotal/expiryDays : 0));
        System.out.printf("║  Full Wins              : %-43d║%n", gWin);
        System.out.printf("║  Partial Wins           : %-43d║%n", gPartial);
        System.out.printf("║  Losses                 : %-43d║%n", gLoss);
        System.out.printf("║  Win Rate               : %-43s║%n", String.format("%.1f%%", gWR));
        System.out.printf("║  Net Points (all expiry): %-43s║%n", String.format("%+.1f pts", gNet));
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  COMPARISON WITH REGULAR STRATEGY (11-13 window):                  ║");
        System.out.println("║    Regular WR = 62% | Net = +3906 pts over 30 days                 ║");
        System.out.println("║    If expiry WR >= 60% → WORTH implementing                        ║");
        System.out.println("║    If expiry WR < 50%  → needs refinement before live use          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  NOTE: This is BACKTEST ONLY. Review above before approving live implementation.");
    }
}
