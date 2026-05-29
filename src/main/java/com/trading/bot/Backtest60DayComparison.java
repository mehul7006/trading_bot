package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.agents.PredictionAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.SimpleMarketData;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Backtest60DayComparison — 60-day honest backtest of the V30 bot
 * (after all 8 accuracy improvements).
 *
 * Improvements applied vs V29 baseline:
 *  1. Real Wilder's ADX          — Tier 1 now fires correctly (was stub=25, never >25)
 *  2. Real Stochastic (%K/%D)    — 5th vote in signal system (was stub=NEUTRAL always)
 *  3. Session-anchored VWAP      — resets at 9:15 IST each day (was cumulative all-time)
 *  4. Timestamp-based 15-min bias— groups by real 15-min buckets (was every-3-bars count)
 *  5. Gap filter (open vs prev close) — +3 for aligned, -5 for counter-gap signals
 *  6. ATR volatility regime       — hard skip in high (>2.5× avg) or low (<0.4× avg) ATR
 *  7. SMC OB suppression          — -12 confidence near opposing Order Block
 *  8. Tier 3 early window 9:45-10:45 — fires for ≥3 votes + ADX>25 in dead zone
 */
public class Backtest60DayComparison {

    private static final double MIN_CONFIDENCE = 80.0;
    private static final Map<String, Double> MIN_POINTS = Map.of(
        "NIFTY50", 25.0,
        "SENSEX",  60.0
    );
    private static final long COOLDOWN_MS   = 10L * 60 * 1000;
    private static final int  LOOKAHEAD     = 24;
    private static final int  BACKTEST_DAYS = 60;
    private static final int  LOAD_DAYS     = 80; // warmup (200 candles ≈ 14 days) + 60-day window + buffer

    static class SignalRecord {
        String date, time, symbol, direction, outcome, reasoning;
        double entryPrice, targetPts, slPts, targetPrice, slPrice, pointsCaptured, confidence;
    }

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║   V30 ACCURACY IMPROVEMENTS — 60-DAY BACKTEST COMPARISON             ║");
        System.out.println("║   8 fixes applied: ADX, Stochastic, SessionVWAP, 15mBias,            ║");
        System.out.println("║   GapFilter, ATRRegime, SMC-OB Suppression, Tier3 EarlyWindow        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        MarketDataAgent mdAgent   = new MarketDataAgent();
        PredictionAgent predAgent = new PredictionAgent();

        String[] symbols = {"NIFTY50", "SENSEX"};
        List<SignalRecord> allSignals = new ArrayList<>();

        for (String symbol : symbols) {
            allSignals.addAll(runBacktest(symbol, mdAgent, predAgent));
        }

        allSignals.sort(Comparator.comparing((SignalRecord r) -> r.date).thenComparing(r -> r.time));
        printReport(allSignals);
    }

    private static List<SignalRecord> runBacktest(String symbol,
            MarketDataAgent mdAgent, PredictionAgent predAgent) {

        List<SignalRecord> signals = new ArrayList<>();
        try {
            List<SimpleMarketData> data = mdAgent.getHistoricalData(symbol, LOAD_DAYS);
            if (data == null || data.size() < 200) {
                System.err.println("Not enough data for " + symbol);
                return signals;
            }

            Set<LocalDate> allDatesSet = new LinkedHashSet<>();
            for (SimpleMarketData d : data) {
                if (d.timestamp != null) allDatesSet.add(d.timestamp.toLocalDate());
            }
            List<LocalDate> allDates = new ArrayList<>(allDatesSet);
            Collections.sort(allDates);

            LocalDate cutoffDate = allDates.size() > BACKTEST_DAYS
                ? allDates.get(allDates.size() - BACKTEST_DAYS)
                : allDates.get(0);

            System.out.printf("  %s: scanning %s → %s  (%d candles loaded)%n",
                symbol, cutoffDate, allDates.get(allDates.size() - 1), data.size());

            long lastSignalMillis = 0;
            LocalDate lastSignalDate = null;

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData candle = data.get(i);
                if (candle.timestamp == null) continue;

                LocalDate candleDate = candle.timestamp.toLocalDate();
                if (candleDate.isBefore(cutoffDate)) continue;

                LocalTime time = candle.timestamp.toLocalTime();
                if (time.isBefore(LocalTime.of(9, 15)) || time.isAfter(LocalTime.of(15, 25))) continue;

                if (!candleDate.equals(lastSignalDate)) {
                    lastSignalMillis = 0;
                    lastSignalDate = candleDate;
                }

                long candleMillis = candle.timestamp
                    .atZone(ZoneId.of("Asia/Kolkata"))
                    .toInstant().toEpochMilli();
                if (candleMillis - lastSignalMillis < COOLDOWN_MS) continue;

                List<SimpleMarketData> history = data.subList(0, i + 1);
                AIPredictor.AIPrediction pred = predAgent.generateSignal(symbol, history);

                if (!passesGates(symbol, pred)) continue;

                lastSignalMillis = candleMillis;
                double entry  = candle.price;
                double tgtPts = pred.estimatedMovePoints;
                double slPts  = pred.suggestedStopLoss;
                double tgtPx  = pred.predictedDirection.equals("UP") ? entry + tgtPts : entry - tgtPts;
                double slPx   = pred.predictedDirection.equals("UP") ? entry - slPts  : entry + slPts;

                int outcome = verifyOutcome(data, i, pred);
                String outcomeLabel;
                double pts;
                switch (outcome) {
                    case FULL_WIN    -> { outcomeLabel = "WIN";         pts =  tgtPts; }
                    case PARTIAL_WIN -> { outcomeLabel = "PARTIAL WIN"; pts =  tgtPts * 0.3; }
                    default          -> { outcomeLabel = "LOSS";        pts = -slPts; }
                }

                SignalRecord rec = new SignalRecord();
                rec.date           = candleDate.toString();
                rec.time           = candle.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
                rec.symbol         = symbol;
                rec.direction      = pred.predictedDirection;
                rec.entryPrice     = entry;
                rec.targetPts      = tgtPts;
                rec.slPts          = slPts;
                rec.targetPrice    = tgtPx;
                rec.slPrice        = slPx;
                rec.outcome        = outcomeLabel;
                rec.pointsCaptured = pts;
                rec.confidence     = pred.confidence;
                rec.reasoning      = pred.predictionReasoning != null ? pred.predictionReasoning : "-";
                signals.add(rec);
            }
        } catch (Exception e) {
            System.err.println("Backtest error for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
        }
        return signals;
    }

    private static boolean passesGates(String symbol, AIPredictor.AIPrediction p) {
        if ("NEUTRAL".equals(p.predictedDirection)) return false;
        double minPts = MIN_POINTS.getOrDefault(symbol, 20.0);
        return p.confidence >= MIN_CONFIDENCE && p.estimatedMovePoints >= minPts;
    }

    private static int verifyOutcome(List<SimpleMarketData> data, int idx, AIPredictor.AIPrediction pred) {
        double entry = data.get(idx).price;
        String dir   = pred.predictedDirection;
        double tgtPx = dir.equals("UP") ? entry + pred.estimatedMovePoints : entry - pred.estimatedMovePoints;
        double slPx  = dir.equals("UP") ? entry - pred.suggestedStopLoss   : entry + pred.suggestedStopLoss;
        double halfPx= dir.equals("UP") ? entry + pred.estimatedMovePoints * 0.5 : entry - pred.estimatedMovePoints * 0.5;
        boolean half = false;
        for (int j = 1; j <= LOOKAHEAD && (idx + j) < data.size(); j++) {
            SimpleMarketData c = data.get(idx + j);
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

    private static void printReport(List<SignalRecord> signals) {
        if (signals.isEmpty()) {
            System.out.println("No signals generated. Check Upstox token / data connectivity.");
            return;
        }

        Map<String, List<SignalRecord>> byDate = new LinkedHashMap<>();
        for (SignalRecord r : signals) byDate.computeIfAbsent(r.date, k -> new ArrayList<>()).add(r);

        System.out.println("\n");
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║        V30 (8 FIXES) — 60-DAY BACKTEST — FULL CALL DETAIL            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");

        int grandTotal = 0, grandWins = 0, grandPartial = 0, grandLoss = 0;
        double grandNet = 0;

        for (Map.Entry<String, List<SignalRecord>> entry : byDate.entrySet()) {
            String date = entry.getKey();
            List<SignalRecord> day = entry.getValue();
            int dW = 0, dP = 0, dL = 0; double dNet = 0;

            System.out.printf("%n┌──────────────────────────────────────────────────────────────────────%n");
            System.out.printf("│  DATE: %s   (%d calls generated)%n", date, day.size());
            System.out.println("├──────────────────────────────────────────────────────────────────────");

            int n = 0;
            for (SignalRecord r : day) {
                n++;
                String arrow   = r.direction.equals("UP") ? "UP  ▲" : "DOWN ▼";
                String outIcon = r.outcome.equals("WIN") ? "✅ WIN"
                               : r.outcome.equals("PARTIAL WIN") ? "🔶 PARTIAL WIN"
                               : "❌ LOSS";
                System.out.printf("│%n");
                System.out.printf("│  Call #%d  ──  %s  [%s]  @ %s IST%n", n, r.symbol, arrow, r.time);
                System.out.printf("│    Entry      : %,.2f%n", r.entryPrice);
                System.out.printf("│    Target     : %,.2f  (+%.0f pts)%n", r.targetPrice, r.targetPts);
                System.out.printf("│    Stop Loss  : %,.2f  (-%.0f pts)%n", r.slPrice, r.slPts);
                System.out.printf("│    R:R        : 1:%.1f%n", r.slPts > 0 ? r.targetPts / r.slPts : 0);
                System.out.printf("│    Confidence : %.1f%%%n", r.confidence);
                System.out.printf("│    Reason     : %s%n", r.reasoning.length() > 90 ? r.reasoning.substring(0, 90) + "..." : r.reasoning);
                System.out.printf("│    Result     : %s  |  P&L: %+.0f pts%n", outIcon, r.pointsCaptured);

                if (r.outcome.equals("WIN"))              dW++;
                else if (r.outcome.equals("PARTIAL WIN")) dP++;
                else                                      dL++;
                dNet += r.pointsCaptured;
            }

            double dWR = (dW + dP + dL) > 0 ? (dW + dP) * 100.0 / (dW + dP + dL) : 0;
            System.out.println("│");
            System.out.printf("│  DAY SUMMARY ── Calls: %d  ✅ %d  🔶 %d  ❌ %d  │  WR: %.0f%%  │  Net: %+.0f pts%n",
                day.size(), dW, dP, dL, dWR, dNet);
            System.out.println("└──────────────────────────────────────────────────────────────────────");

            grandTotal   += day.size();
            grandWins    += dW;
            grandPartial += dP;
            grandLoss    += dL;
            grandNet     += dNet;
        }

        // Per-symbol breakdown
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                      PER-SYMBOL BREAKDOWN                            ║");
        System.out.println("╠═══════════════╦═══════╦══════╦═════════╦══════════╦═════════════════╣");
        System.out.println("║ Symbol        ║ Calls ║ Wins ║ Partial ║  WinRate ║    Net Points   ║");
        System.out.println("╠═══════════════╬═══════╬══════╬═════════╬══════════╬═════════════════╣");

        for (String sym : new String[]{"NIFTY50", "SENSEX"}) {
            int sT=0, sW=0, sP=0; double sNet=0;
            for (SignalRecord r : signals) {
                if (!r.symbol.equals(sym)) continue;
                sT++;
                if (r.outcome.equals("WIN"))              sW++;
                else if (r.outcome.equals("PARTIAL WIN")) sP++;
                sNet += r.pointsCaptured;
            }
            double sWR = sT > 0 ? (sW + sP) * 100.0 / sT : 0;
            System.out.printf("║ %-13s ║  %3d  ║  %3d ║   %3d   ║  %5.1f%%  ║  %+12.1f   ║%n",
                sym, sT, sW, sP, sWR, sNet);
        }

        System.out.println("╠═══════════════╬═══════╬══════╬═════════╬══════════╬═════════════════╣");
        double grandWR   = grandTotal > 0 ? (grandWins + grandPartial) * 100.0 / grandTotal : 0;
        double fullWinWR = grandTotal > 0 ? grandWins * 100.0 / grandTotal : 0;
        System.out.printf("║ %-13s ║  %3d  ║  %3d ║   %3d   ║  %5.1f%%  ║  %+12.1f   ║%n",
            "COMBINED", grandTotal, grandWins, grandPartial, grandWR, grandNet);
        System.out.println("╚═══════════════╩═══════╩══════╩═════════╩══════════╩═════════════════╝");

        // Direction breakdown
        int upTotal=0,upWin=0,upPartial=0, dnTotal=0,dnWin=0,dnPartial=0;
        for (SignalRecord r : signals) {
            if (r.direction.equals("UP")) {
                upTotal++;
                if (r.outcome.equals("WIN")) upWin++;
                else if (r.outcome.equals("PARTIAL WIN")) upPartial++;
            } else {
                dnTotal++;
                if (r.outcome.equals("WIN")) dnWin++;
                else if (r.outcome.equals("PARTIAL WIN")) dnPartial++;
            }
        }

        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    DIRECTION ACCURACY BREAKDOWN                      ║");
        System.out.println("╠════════════╦═══════╦════════════════╦═══════════════════════════════╣");
        System.out.println("║ Direction  ║ Calls ║   Win Rate     ║  Full Win Rate                ║");
        System.out.println("╠════════════╬═══════╬════════════════╬═══════════════════════════════╣");
        double upWR   = upTotal > 0 ? (upWin+upPartial)*100.0/upTotal : 0;
        double upFWR  = upTotal > 0 ? upWin*100.0/upTotal : 0;
        double dnWR   = dnTotal > 0 ? (dnWin+dnPartial)*100.0/dnTotal : 0;
        double dnFWR  = dnTotal > 0 ? dnWin*100.0/dnTotal : 0;
        System.out.printf("║ UP  ▲      ║  %3d  ║   %5.1f%%        ║   %5.1f%% full wins          ║%n", upTotal, upWR, upFWR);
        System.out.printf("║ DOWN ▼     ║  %3d  ║   %5.1f%%        ║   %5.1f%% full wins          ║%n", dnTotal, dnWR, dnFWR);
        System.out.println("╚════════════╩═══════╩════════════════╩═══════════════════════════════╝");

        // Grand summary with before/after analytical comparison
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║               60-DAY GRAND SUMMARY (V30 — 8 FIXES APPLIED)           ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Total Calls     : %3d                                               ║%n", grandTotal);
        System.out.printf( "║  Full Wins       : %3d  (%.1f%%)                                       ║%n", grandWins, fullWinWR);
        System.out.printf( "║  Partial Wins    : %3d                                               ║%n", grandPartial);
        System.out.printf( "║  Losses          : %3d                                               ║%n", grandLoss);
        System.out.printf( "║  Win Rate (W+P)  : %.1f%%                                              ║%n", grandWR);
        System.out.printf( "║  Net Points      : %+.0f                                             ║%n", grandNet);
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  ANALYTICAL BEFORE/AFTER COMPARISON (V29 bugs vs V30 fixes):        ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Bug 1 – ADX stub (25.0):                                            ║");
        System.out.println("║    V29: Tier 1 NEVER fired (25 is not > 25). All trades were Tier 2  ║");
        System.out.println("║    V30: Real ADX fires Tier 1 when momentum is confirmed              ║");
        System.out.println("║    Impact: Higher confidence signals, better entries                  ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Bug 2 – Stochastic stub (always NEUTRAL):                           ║");
        System.out.println("║    V29: 5th vote never contributed — effectively 4-vote system        ║");
        System.out.println("║    V30: Real %K/%D fires BULLISH/BEARISH — full 5-vote system        ║");
        System.out.println("║    Impact: Tier 3 early window can now fire; directional filtering    ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Bug 3 – Cumulative VWAP:                                            ║");
        System.out.println("║    V29: VWAP included all historical candles (meaningless after 1hr)  ║");
        System.out.println("║    V30: Session VWAP resets at 09:15 IST daily                       ║");
        System.out.println("║    Impact: aboveVWAP/belowVWAP signal is now accurate intraday        ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Bug 4 – 15-min bias count-based grouping:                           ║");
        System.out.println("║    V29: Every 3 bars = 1 bucket (wrong after gaps/skips)             ║");
        System.out.println("║    V30: Timestamp-bucketed to real 15-min slots                      ║");
        System.out.println("║    Impact: EMA20 slope on real candles, not artificial aggregates     ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Fix 5 – Gap filter (+3/-5 confidence):                              ║");
        System.out.println("║    V29: Counter-gap trades got no penalty                             ║");
        System.out.println("║    V30: Aligning with gap +3; counter-gap -5                         ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Fix 6 – ATR Regime filter (hard skip):                              ║");
        System.out.println("║    V29: Traded in high-volatility whipsaw periods                     ║");
        System.out.println("║    V30: Skips ATR > 2.5× avg AND ATR < 0.4× avg periods             ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Fix 7 – SMC Order Block suppression (-12 confidence):               ║");
        System.out.println("║    V29: Could take UP trade right below bearish OB                    ║");
        System.out.println("║    V30: Reduces confidence by 12 if price within 3×ATR of OB         ║");
        System.out.println("║                                                                       ║");
        System.out.println("║  Fix 8 – Tier 3 early window (9:45-10:45):                           ║");
        System.out.println("║    V29: Dead zone between ORB end and prime window start              ║");
        System.out.println("║    V30: ≥3 votes + ADX>25 can fire high-quality early signals        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
    }
}
