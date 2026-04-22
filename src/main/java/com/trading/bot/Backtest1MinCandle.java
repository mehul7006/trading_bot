package com.trading.bot;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.agents.PredictionAgent;
import com.trading.bot.ai.AIPredictor;
import com.trading.bot.market.SimpleMarketData;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  Backtest1MinCandle  —  ANALYSIS / PREVIEW ONLY
 *  ───────────────────────────────────────────────────────────────────────────
 *  PURPOSE  : Shows what WOULD happen if the bot sent 1-minute candle signals.
 *             This is a READ-ONLY analysis tool.
 *
 *  ⚠️  IMPORTANT NOTES:
 *   • This file does NOT change Phase3TelegramBot or any production code.
 *   • All calls generated here are labelled "EDUCATION PURPOSE ONLY".
 *   • DO NOT BUY OR SELL based on 1-minute candle signals — they are
 *     exploratory and have NOT been validated for live trading.
 *   • Main bot continues to use 5-min data with 69.6% WR unchanged.
 *
 *  RUN   : mvn exec:java -Dexec.mainClass="com.trading.bot.Backtest1MinCandle"
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class Backtest1MinCandle {

    // ── Gates (1-min data is noisier, so slightly relaxed thresholds) ──
    private static final double MIN_CONFIDENCE = 88.0;   // same strict gate as main bot
    private static final Map<String, Double> MIN_POINTS = Map.of(
        "NIFTY50",   15.0,   // 1-min candle moves are smaller than 5-min
        "SENSEX",    40.0,
        "BANKNIFTY", 45.0
    );
    private static final long COOLDOWN_MS  = 5L  * 60 * 1000;  // 5-min cooldown (1-min data)
    private static final int  LOOKAHEAD    = 40;                 // look 40 candles forward (~40 min)
    private static final int  BACKTEST_DAYS = 30;
    private static final int  LOAD_DAYS    = 47;

    static class SignalRecord {
        String date, time, symbol, direction, outcome, reasoning;
        double entryPrice, targetPts, slPts, targetPrice, slPrice, pointsCaptured, confidence;
    }

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    public static void main(String[] args) {
        printBanner();
        System.out.println("Loading 1-minute candle data for 30-day preview...");
        MarketDataAgent mdAgent   = new MarketDataAgent();
        PredictionAgent predAgent = new PredictionAgent();

        String[] symbols = {"NIFTY50", "SENSEX"};
        List<SignalRecord> allSignals = new ArrayList<>();

        for (String symbol : symbols) {
            allSignals.addAll(run1MinBacktest(symbol, mdAgent, predAgent));
        }

        allSignals.sort(Comparator.comparing((SignalRecord r) -> r.date).thenComparing(r -> r.time));
        printReport(allSignals);
    }

    private static List<SignalRecord> run1MinBacktest(String symbol,
            MarketDataAgent mdAgent, PredictionAgent predAgent) {

        List<SignalRecord> signals = new ArrayList<>();
        try {
            // Use raw 1-min candles — same data the bot uses for getRealMarketData()
            List<SimpleMarketData> data = mdAgent.getHistoricalData(symbol, LOAD_DAYS);
            if (data == null || data.size() < 200) {
                System.err.println("Not enough 1-min data for " + symbol);
                return signals;
            }

            // Collect unique trading dates
            Set<LocalDate> allDatesSet = new LinkedHashSet<>();
            for (SimpleMarketData d : data) {
                if (d.timestamp != null) allDatesSet.add(d.timestamp.toLocalDate());
            }
            List<LocalDate> allDates = new ArrayList<>(allDatesSet);
            Collections.sort(allDates);

            LocalDate cutoffDate = allDates.size() > BACKTEST_DAYS
                ? allDates.get(allDates.size() - BACKTEST_DAYS)
                : allDates.get(0);

            System.out.printf("[1-MIN] %s: scanning %s → %s (%d raw candles)%n",
                symbol, cutoffDate, allDates.get(allDates.size() - 1), data.size());

            long lastSignalMillis = 0;
            LocalDate lastSignalDate = null;
            Map<LocalDate, Integer> dailyCount = new HashMap<>();

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData candle = data.get(i);
                if (candle.timestamp == null) continue;

                LocalDate candleDate = candle.timestamp.toLocalDate();
                if (candleDate.isBefore(cutoffDate)) continue;

                LocalTime time = candle.timestamp.toLocalTime();
                // Only scan during normal market hours
                if (time.isBefore(LocalTime.of(9, 30)) || time.isAfter(LocalTime.of(15, 20))) continue;

                // Reset cooldown at start of each new day
                if (!candleDate.equals(lastSignalDate)) {
                    lastSignalMillis = 0;
                    lastSignalDate = candleDate;
                }

                long candleMillis = candle.timestamp
                    .atZone(java.time.ZoneId.of("Asia/Kolkata"))
                    .toInstant().toEpochMilli();

                if (candleMillis - lastSignalMillis < COOLDOWN_MS) continue;

                // Generate prediction using same AIPredictor — on 1-min history
                List<SimpleMarketData> history = data.subList(0, i + 1);
                AIPredictor.AIPrediction pred = predAgent.generateSignal(symbol, history);

                if (!passesGates(symbol, pred)) continue;

                lastSignalMillis = candleMillis;
                dailyCount.merge(candleDate, 1, Integer::sum);

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
            System.err.println("1-Min backtest error for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
        }
        return signals;
    }

    private static boolean passesGates(String symbol, AIPredictor.AIPrediction p) {
        if (p == null || "NEUTRAL".equals(p.predictedDirection)) return false;
        double minPts = MIN_POINTS.getOrDefault(symbol, 15.0);
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

    private static void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         1-MINUTE CANDLE SIGNAL ANALYSIS  —  PREVIEW ONLY           ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  ⚠️  ALL CALLS BELOW ARE: EDUCATION PURPOSE ONLY                    ║");
        System.out.println("║     DO NOT BUY OR SELL BASED ON 1-MINUTE CANDLE SIGNALS             ║");
        System.out.println("║     Main bot (5-min, 69.6% WR) is UNCHANGED by this analysis        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private static void printReport(List<SignalRecord> signals) {
        if (signals.isEmpty()) {
            System.out.println("No 1-min signals generated in last 30 trading days.");
            return;
        }

        Map<String, List<SignalRecord>> byDate = new LinkedHashMap<>();
        for (SignalRecord r : signals) byDate.computeIfAbsent(r.date, k -> new ArrayList<>()).add(r);

        System.out.println("\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  1-MIN CANDLE BACKTEST — LAST 30 DAYS — [EDUCATION PURPOSE ONLY]   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");

        int grandTotal = 0, grandWins = 0, grandPartial = 0, grandLoss = 0;
        double grandNet = 0;

        for (Map.Entry<String, List<SignalRecord>> entry : byDate.entrySet()) {
            String date = entry.getKey();
            List<SignalRecord> day = entry.getValue();
            int dW = 0, dP = 0, dL = 0; double dNet = 0;

            System.out.println("\n┌──────────────────────────────────────────────────────────────────────");
            System.out.printf( "│  DATE: %s   (%d calls  ⚠️ EDUCATION ONLY — DO NOT TRADE)%n", date, day.size());
            System.out.println("├──────────────────────────────────────────────────────────────────────");

            int n = 0;
            for (SignalRecord r : day) {
                n++;
                String arrow  = r.direction.equals("UP") ? "UP  ▲" : "DOWN ▼";
                String outIcon = r.outcome.equals("WIN")         ? "✅ WIN"
                               : r.outcome.equals("PARTIAL WIN") ? "🔶 PARTIAL WIN"
                               :                                   "❌ LOSS";
                System.out.printf("│%n");
                System.out.printf("│  [EDUCATION ONLY] Call #%d  ──  %s  [%s]  @ %s IST%n", n, r.symbol, arrow, r.time);
                System.out.printf("│    Entry      : %,.2f%n", r.entryPrice);
                System.out.printf("│    Target     : %,.2f  (+%.0f pts)%n", r.targetPrice, r.targetPts);
                System.out.printf("│    Stop Loss  : %,.2f  (-%.0f pts)%n", r.slPrice, r.slPts);
                System.out.printf("│    R:R        : 1:%.1f%n", r.slPts > 0 ? r.targetPts / r.slPts : 0);
                System.out.printf("│    Confidence : %.1f%%%n", r.confidence);
                System.out.printf("│    Reason     : %s%n",
                    r.reasoning.length() > 80 ? r.reasoning.substring(0, 80) + "..." : r.reasoning);
                System.out.printf("│    Result     : %s  |  P&L: %+.0f pts%n", outIcon, r.pointsCaptured);

                if      (r.outcome.equals("WIN"))         dW++;
                else if (r.outcome.equals("PARTIAL WIN")) dP++;
                else                                      dL++;
                dNet += r.pointsCaptured;
            }

            double dWR = (dW + dP + dL) > 0 ? (dW + dP) * 100.0 / (dW + dP + dL) : 0;
            System.out.println("│");
            System.out.printf( "│  DAY SUMMARY ── Calls: %d  ✅ %d  🔶 %d  ❌ %d  │  WR: %.0f%%  │  Net: %+.0f pts  [EDU ONLY]%n",
                day.size(), dW, dP, dL, dWR, dNet);
            System.out.println("└──────────────────────────────────────────────────────────────────────");

            grandTotal += day.size();
            grandWins += dW; grandPartial += dP; grandLoss += dL;
            grandNet  += dNet;
        }

        // ── Per-symbol breakdown ──
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║          PER-SYMBOL  [EDUCATION ONLY — NOT FOR TRADING]             ║");
        System.out.println("╠═══════════════╦═══════╦══════╦═════════╦══════════╦════════════════╣");
        System.out.println("║ Symbol        ║ Calls ║ Wins ║ Partial ║  WinRate ║   Net Points   ║");
        System.out.println("╠═══════════════╬═══════╬══════╬═════════╬══════════╬════════════════╣");

        for (String sym : new String[]{"NIFTY50", "BANKNIFTY", "SENSEX"}) {
            int sT=0, sW=0, sP=0; double sNet=0;
            for (SignalRecord r : signals) {
                if (!r.symbol.equals(sym)) continue;
                sT++;
                if      (r.outcome.equals("WIN"))         sW++;
                else if (r.outcome.equals("PARTIAL WIN")) sP++;
                sNet += r.pointsCaptured;
            }
            double sWR = sT > 0 ? (sW + sP) * 100.0 / sT : 0;
            System.out.printf("║ %-13s ║  %3d  ║  %3d ║   %3d   ║  %5.1f%%  ║  %+11.1f   ║%n",
                sym, sT, sW, sP, sWR, sNet);
        }

        double grandWR = grandTotal > 0 ? (grandWins + grandPartial) * 100.0 / grandTotal : 0;
        System.out.println("╠═══════════════╬═══════╬══════╬═════════╬══════════╬════════════════╣");
        System.out.printf( "║ COMBINED      ║  %3d  ║  %3d ║   %3d   ║  %5.1f%%  ║  %+11.1f   ║%n",
            grandTotal, grandWins, grandPartial, grandWR, grandNet);
        System.out.println("╚═══════════════╩═══════╩══════╩═════════╩══════════╩════════════════╝");

        System.out.println("\n╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         1-MIN CANDLE — 30-DAY ANALYSIS SUMMARY                      ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Total Calls (1-min)    : %-43d║%n", grandTotal);
        System.out.printf( "║  Full Wins (✅)          : %-43d║%n", grandWins);
        System.out.printf( "║  Partial Wins (🔶)       : %-43d║%n", grandPartial);
        System.out.printf( "║  Losses (❌)             : %-43d║%n", grandLoss);
        System.out.printf( "║  Win Rate (analysis)    : %-43s║%n", String.format("%.1f%%", grandWR));
        System.out.printf( "║  Net Points (analysis)  : %-43s║%n", String.format("%+.1f pts", grandNet));
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  ⚠️  REMINDER: These are EDUCATION PURPOSE results only.              ║");
        System.out.println("║     Main bot (5-min) remains unchanged with 69.6% WR.               ║");
        System.out.println("║     Only add to main bot if WR > 65% AND you explicitly permit it.  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }
}
