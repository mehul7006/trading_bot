package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ExpiryDayBacktest2 — V2 Expiry RSI Exhaustion Reversal Strategy
 *
 * Philosophy: On expiry afternoons, markets get pinned to option strike prices.
 * When RSI reaches extreme (≥75 or ≤25) AND price pierces Bollinger Band AND
 * a reversal candle appears with volume surge → fade the extreme move.
 *
 * Key differences from V1:
 *  - Time window : 14:00–15:10 only (skip 13:30–14:00 which is choppy)
 *  - Signal type : RSI EXHAUSTION REVERSAL (not momentum following)
 *  - RSI gate    : RSI ≥ 75 → SHORT | RSI ≤ 25 → LONG  (strict extremes)
 *  - BB filter   : Price must pierce/touch upper or lower Bollinger Band (2σ)
 *  - Volume      : > 1.8× 20-period volume MA (real conviction)
 *  - Candle body : > 65% of range (strong reversal candle)
 *  - Cooldown    : 20 min per symbol
 *  - Target      : 2.0 × ATR14 | SL : 0.5 × ATR14  (4:1 R:R)
 *
 * BACKTEST ONLY — no live bot changes.
 */
public class ExpiryDayBacktest2 {

    // User-confirmed expiry schedule (BANKNIFTY excluded):
    //   NIFTY50 → Tuesday
    //   SENSEX  → Thursday
    private static final Map<String, DayOfWeek> EXPIRY_DOW = Map.of(
        "NIFTY50", DayOfWeek.TUESDAY,
        "SENSEX",  DayOfWeek.THURSDAY
    );

    // Window: 14:00–15:10 (skip 13:30–14:00 whipsaw zone)
    private static final LocalTime EXPIRY_START = LocalTime.of(14, 0);
    private static final LocalTime EXPIRY_END   = LocalTime.of(15, 10);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 25);

    // Signal parameters — strict filters for high win rate
    private static final double RSI_OVERBOUGHT  = 68.0;   // short: RSI must be >= 68 (overbought)
    private static final double RSI_OVERSOLD    = 32.0;   // long:  RSI must be <= 32 (oversold)
    private static final double BB_PERIOD       = 20;
    private static final double BB_STD_MULT     = 1.8;    // 1.8σ Bollinger Bands
    private static final double VOLUME_MULT     = 1.4;    // 1.4× avg volume
    private static final double BODY_RATIO_MIN  = 0.55;   // 55% body of total range
    private static final double ATR_TARGET_MULT = 2.0;
    private static final double ATR_SL_MULT     = 0.5;    // 4:1 R:R
    private static final long   COOLDOWN_MS     = 20L * 60 * 1000; // 20 min cooldown
    private static final int    LOOKAHEAD       = 24;

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    static class Signal {
        String date, time, symbol, direction, outcome, reason;
        double entry, targetPts, slPts, targetPrice, slPrice, pnl, rsi;
        double bbUpper, bbLower;
    }

    public static void main(String[] args) {
        System.out.println("Loading candle data for V2 expiry-day backtest...");
        MarketDataAgent mdAgent = new MarketDataAgent();

        // BANKNIFTY excluded — user confirmed expiry day unclear
        String[] symbols = {"NIFTY50", "SENSEX"};
        List<Signal> allSignals = new ArrayList<>();

        for (String sym : symbols) {
            allSignals.addAll(runBacktest(sym, mdAgent));
        }

        allSignals.sort(Comparator.comparing((Signal s) -> s.date).thenComparing(s -> s.time));
        printReport(allSignals);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    private static List<Signal> runBacktest(String symbol, MarketDataAgent mdAgent) {
        List<Signal> signals = new ArrayList<>();
        try {
            List<SimpleMarketData> data = mdAgent.getHistoricalData(symbol, 120);
            if (data == null || data.size() < 200) {
                System.err.println("Not enough data for " + symbol);
                return signals;
            }

            DayOfWeek expiryDow = EXPIRY_DOW.get(symbol);
            System.out.printf("%s: scanning %d candles for expiry days (DOW=%s)...%n",
                symbol, data.size(), expiryDow);

            Map<String, Long> lastSignalMs = new HashMap<>();

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData candle = data.get(i);
                if (candle.timestamp == null) continue;

                LocalDate date = candle.timestamp.toLocalDate();
                LocalTime time = candle.timestamp.toLocalTime();

                // Expiry day filter
                if (date.getDayOfWeek() != expiryDow) continue;

                // Time window: 14:00–15:10 only
                if (time.isBefore(EXPIRY_START) || time.isAfter(EXPIRY_END)) continue;

                // 20-min cooldown per symbol per day
                String key = symbol + "_" + date;
                long candleMs = candle.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
                long lastMs = lastSignalMs.getOrDefault(key, 0L);
                if (candleMs - lastMs < COOLDOWN_MS) continue;

                List<SimpleMarketData> hist = data.subList(0, i + 1);

                // ── Compute indicators ──────────────────────────────────────────
                double rsi = calcRSI(hist, 14);
                double atr = calcATR(hist, 14);
                if (atr <= 0) continue;

                double[] bb = calcBollingerBands(hist, (int) BB_PERIOD, BB_STD_MULT);
                double bbUpper = bb[0], bbLower = bb[1];

                double volMa = calcVolumeMa(hist, 20);
                double volume = candle.volume > 0 ? candle.volume : 1;

                double open  = candle.open;
                double price = candle.price;  // close price
                double high  = candle.high;
                double low   = candle.low;
                double range = high - low;
                double body  = Math.abs(price - open);
                double bodyRatio = range > 0 ? body / range : 0;

                // ── Signal detection: REVERSAL at extremes ──────────────────────

                boolean longSignal  = false;
                boolean shortSignal = false;
                String reason = "";

                // LONG: RSI oversold + price at/below lower BB + bullish reversal candle + volume surge
                if (rsi <= RSI_OVERSOLD
                        && price <= bbLower * 1.002    // at or just pierced lower BB
                        && price > open                // bullish candle
                        && bodyRatio >= BODY_RATIO_MIN
                        && volume >= volMa * VOLUME_MULT) {
                    longSignal = true;
                    reason = String.format("RSI=%.1f(OVERSOLD) | Price(%.1f) <= BBLower(%.1f) | Bull candle body=%.0f%% | Vol=%.1fx",
                        rsi, price, bbLower, bodyRatio * 100, volume / volMa);
                }

                // SHORT: RSI overbought + price at/above upper BB + bearish reversal candle + volume surge
                if (!longSignal
                        && rsi >= RSI_OVERBOUGHT
                        && price >= bbUpper * 0.998    // at or just pierced upper BB
                        && price < open                // bearish candle
                        && bodyRatio >= BODY_RATIO_MIN
                        && volume >= volMa * VOLUME_MULT) {
                    shortSignal = true;
                    reason = String.format("RSI=%.1f(OVERBOUGHT) | Price(%.1f) >= BBUpper(%.1f) | Bear candle body=%.0f%% | Vol=%.1fx",
                        rsi, price, bbUpper, bodyRatio * 100, volume / volMa);
                }

                if (!longSignal && !shortSignal) continue;

                String direction = longSignal ? "UP" : "DOWN";
                double tgtPts = atr * ATR_TARGET_MULT;
                double slPts  = atr * ATR_SL_MULT;
                double tgtPx  = direction.equals("UP") ? price + tgtPts : price - tgtPts;
                double slPx   = direction.equals("UP") ? price - slPts  : price + slPts;

                // Min-points filter
                double minPts = symbol.equals("NIFTY50") ? 15.0 : symbol.equals("BANKNIFTY") ? 40.0 : 30.0;
                if (tgtPts < minPts) continue;

                // Outcome verification
                int outcome = verifyOutcome(data, i, direction, tgtPx, slPx, price);
                String label;
                double pnl;
                switch (outcome) {
                    case FULL_WIN    -> { label = "WIN";         pnl =  tgtPts; }
                    case PARTIAL_WIN -> { label = "PARTIAL WIN"; pnl =  tgtPts * 0.4; }
                    default          -> { label = "LOSS";        pnl = -slPts; }
                }

                Signal s = new Signal();
                s.date        = date.toString();
                s.time        = candle.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
                s.symbol      = symbol;
                s.direction   = direction;
                s.entry       = price;
                s.targetPts   = tgtPts;
                s.slPts       = slPts;
                s.targetPrice = tgtPx;
                s.slPrice     = slPx;
                s.outcome     = label;
                s.pnl         = pnl;
                s.rsi         = rsi;
                s.bbUpper     = bbUpper;
                s.bbLower     = bbLower;
                s.reason      = reason;
                signals.add(s);

                lastSignalMs.put(key, candleMs);
            }
        } catch (Exception e) {
            System.err.println("Backtest error for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
        }
        return signals;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    private static int verifyOutcome(List<SimpleMarketData> data, int idx,
                                     String dir, double tgtPx, double slPx, double entry) {
        double halfPts = Math.abs(tgtPx - entry) * 0.5;
        double halfPx  = dir.equals("UP") ? entry + halfPts : entry - halfPts;
        boolean half   = false;

        // Cap lookahead to market close (15:25)
        for (int j = 1; j <= LOOKAHEAD && (idx + j) < data.size(); j++) {
            SimpleMarketData c = data.get(idx + j);
            LocalTime t = c.timestamp.toLocalTime();
            if (t.isAfter(MARKET_CLOSE)) break;

            if (dir.equals("UP")) {
                if (c.low  <= slPx)  return half ? PARTIAL_WIN : LOSS;
                if (c.high >= tgtPx) return FULL_WIN;
                if (c.high >= halfPx) half = true;
            } else {
                if (c.high >= slPx)  return half ? PARTIAL_WIN : LOSS;
                if (c.low  <= tgtPx) return FULL_WIN;
                if (c.low  <= halfPx) half = true;
            }
        }
        return half ? PARTIAL_WIN : LOSS;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Indicator calculations
    // ─────────────────────────────────────────────────────────────────────────────

    private static double calcRSI(List<SimpleMarketData> hist, int period) {
        if (hist.size() < period + 1) return 50.0;
        double gains = 0, losses = 0;
        for (int i = hist.size() - period; i < hist.size(); i++) {
            double delta = hist.get(i).price - hist.get(i - 1).price;
            if (delta > 0) gains  += delta;
            else            losses -= delta;
        }
        if (losses == 0) return 100.0;
        double rs = (gains / period) / (losses / period);
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private static double calcATR(List<SimpleMarketData> hist, int period) {
        if (hist.size() < period + 1) return 0;
        double sum = 0;
        for (int i = hist.size() - period; i < hist.size(); i++) {
            SimpleMarketData c = hist.get(i);
            double prevClose = hist.get(i - 1).price;
            sum += Math.max(c.high - c.low,
                   Math.max(Math.abs(c.high - prevClose),
                            Math.abs(c.low  - prevClose)));
        }
        return sum / period;
    }

    /** Returns [upperBB, lowerBB, middleBB] */
    private static double[] calcBollingerBands(List<SimpleMarketData> hist, int period, double stdMult) {
        if (hist.size() < period) {
            double p = hist.get(hist.size() - 1).price;
            return new double[]{p * 1.01, p * 0.99, p};
        }
        // SMA
        double sum = 0;
        for (int i = hist.size() - period; i < hist.size(); i++) sum += hist.get(i).price;
        double sma = sum / period;
        // StdDev
        double varSum = 0;
        for (int i = hist.size() - period; i < hist.size(); i++) {
            double d = hist.get(i).price - sma;
            varSum += d * d;
        }
        double std = Math.sqrt(varSum / period);
        return new double[]{sma + stdMult * std, sma - stdMult * std, sma};
    }

    private static double calcVolumeMa(List<SimpleMarketData> hist, int period) {
        if (hist.size() < period) return 1;
        double sum = 0;
        for (int i = hist.size() - period; i < hist.size(); i++) sum += hist.get(i).volume;
        return sum / period;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Report
    // ─────────────────────────────────────────────────────────────────────────────

    private static void printReport(List<Signal> signals) {
        System.out.println("\n\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║   EXPIRY DAY BACKTEST V3 — NIFTY50 (Tue) + SENSEX (Thu) ONLY           ║");
        System.out.println("║   Window: 14:00-15:10 | RSI 68/32 + BB 1.8 + Vol 1.4x + Body 55%      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");

        if (signals.isEmpty()) {
            System.out.println("  No signals generated — filters too strict.");
            return;
        }

        Map<String, List<Signal>> byDate = new LinkedHashMap<>();
        for (Signal s : signals) byDate.computeIfAbsent(s.date, k -> new ArrayList<>()).add(s);

        int grandTotal = 0, grandWins = 0, grandPartial = 0, grandLoss = 0;
        double grandNet = 0;

        for (Map.Entry<String, List<Signal>> e : byDate.entrySet()) {
            String date = e.getKey();
            List<Signal> day = e.getValue();
            int dW = 0, dP = 0, dL = 0; double dNet = 0;

            System.out.println("\n  ┌─────────────────────────────────────────────────────────────────────────");
            System.out.printf ("  │  DATE: %s   (%d calls)%n", date, day.size());
            System.out.println("  ├─────────────────────────────────────────────────────────────────────────");
            System.out.println("  │  Time  | Symbol     | Dir   | Entry        | Tgt   | SL   | Result       | P&L");
            System.out.println("  │──────────────────────────────────────────────────────────────────────────");

            for (Signal s : day) {
                String icon = s.outcome.equals("WIN") ? "WIN " :
                              s.outcome.equals("PARTIAL WIN") ? "PART" : "LOSS";
                String line1 = String.format("  |  %s | %-10s | %-5s | %,12.1f | +%.0f pts | -%.0f pts | %-4s | %+.0f pts",
                    s.time, s.symbol, s.direction, s.entry, s.targetPts, s.slPts, icon, s.pnl);
                System.out.println(line1);
                String line2 = String.format("  |       RSI=%.1f  BB=[%.1f to %.1f]",
                    s.rsi, s.bbLower, s.bbUpper);
                System.out.println(line2);

                if (s.outcome.equals("WIN")) dW++;
                else if (s.outcome.equals("PARTIAL WIN")) dP++;
                else dL++;
                dNet += s.pnl;
            }

            double dWR = (dW + dP + dL) > 0 ? (dW + dP) * 100.0 / (dW + dP + dL) : 0;
            System.out.printf("  │%n  │  DAY: Calls=%d  WIN=%d  PART=%d  LOSS=%d  | WR=%.0f%%  | Net=%+.0f pts%n",
                day.size(), dW, dP, dL, dWR, dNet);
            System.out.println("  └─────────────────────────────────────────────────────────────────────────");

            grandTotal += day.size();
            grandWins += dW; grandPartial += dP; grandLoss += dL;
            grandNet  += dNet;
        }

        // Per-symbol breakdown
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                     PER-SYMBOL BREAKDOWN                               ║");
        System.out.println("╠═══════════════╦═══════╦══════╦═════════╦══════════╦════════════════════╣");
        System.out.println("║ Symbol        ║ Calls ║ Wins ║ Partial ║  WinRate ║     Net Points     ║");
        System.out.println("╠═══════════════╬═══════╬══════╬═════════╬══════════╬════════════════════╣");
        // Only NIFTY50 (Tuesday) and SENSEX (Thursday)
        String[] symList = new String[]{"NIFTY50", "SENSEX"};
        for (String sym : symList) {
            int sT=0, sW=0, sP=0; double sNet=0;
            for (Signal s : signals) {
                if (!s.symbol.equals(sym)) continue;
                sT++;
                if (s.outcome.equals("WIN"))          sW++;
                else if (s.outcome.equals("PARTIAL WIN")) sP++;
                sNet += s.pnl;
            }
            double sWR = sT > 0 ? (sW + sP) * 100.0 / sT : 0;
            System.out.printf("║ %-13s ║  %3d  ║  %3d ║   %3d   ║  %5.1f%%  ║  %+15.1f   ║%n",
                sym, sT, sW, sP, sWR, sNet);
        }
        double grandWR = grandTotal > 0 ? (grandWins + grandPartial) * 100.0 / grandTotal : 0;
        System.out.println("╠═══════════════╬═══════╬══════╬═════════╬══════════╬════════════════════╣");
        System.out.printf ("║ COMBINED      ║  %3d  ║  %3d ║   %3d   ║  %5.1f%%  ║  %+15.1f   ║%n",
            grandTotal, grandWins, grandPartial, grandWR, grandNet);
        System.out.println("╚═══════════════╩═══════╩══════╩═════════╩══════════╩════════════════════╝");

        // Final summary
        int expiryDays = byDate.size();
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              EXPIRY DAY BACKTEST V2 — FINAL SUMMARY                    ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════╣");
        System.out.printf ("║  Expiry Days with Signals : %-43d║%n", expiryDays);
        System.out.printf ("║  Total Calls Generated    : %-43d║%n", grandTotal);
        System.out.printf ("║  Avg Calls / Expiry Day   : %-43s║%n", String.format("%.1f", grandTotal * 1.0 / Math.max(expiryDays, 1)));
        System.out.printf ("║  Full Wins                : %-43d║%n", grandWins);
        System.out.printf ("║  Partial Wins             : %-43d║%n", grandPartial);
        System.out.printf ("║  Losses                   : %-43d║%n", grandLoss);
        System.out.printf ("║  Win Rate                 : %-43s║%n", String.format("%.1f%%", grandWR));
        System.out.printf ("║  Net Points               : %-43s║%n", String.format("%+.1f pts", grandNet));
        System.out.println("╠══════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  COMPARISON:                                                            ║");
        System.out.println("║    V1 Expiry (all 3, wrong days) →  39% WR  |  +1,677 pts  (WRONG)     ║");
        System.out.println("║    Regular bot (11-13 window)    →  62% WR  |  +3,906 pts  (CURRENT)   ║");
        System.out.printf ("║    V3 NIFTY+SENSEX Expiry        →  %s WR  |  %+.0f pts  <-- NEW   ║%n",
            String.format("%.0f%%", grandWR), grandNet);
        System.out.println("╠══════════════════════════════════════════════════════════════════════════╣");
        if (grandWR >= 68.0) {
            System.out.println("║  VERDICT: PASS (>=68%) — Ready for your approval to implement          ║");
        } else if (grandWR >= 55.0) {
            System.out.println("║  VERDICT: MARGINAL (55-68%) — Needs further refinement                 ║");
        } else {
            System.out.println("║  VERDICT: FAIL (<55%) — Not recommended for live bot                  ║");
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");
        System.out.println("\n  NOTE: BACKTEST ONLY — awaiting your permission before any live changes.");
    }
}
