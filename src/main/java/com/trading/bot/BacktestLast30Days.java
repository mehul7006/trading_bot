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
 * BacktestLast30Days — replays the V29.0 strategy on the last 30 actual trading days.
 * Prints every generated call with entry, target, SL and outcome.
 */
public class BacktestLast30Days {

    private static final Map<String, Double> MIN_POINTS = Map.of(
        "NIFTY50",   28.0,   // raised from 25 — filter tiny-range signals
        "SENSEX",    65.0    // raised from 60
    );
    private static final long COOLDOWN_MS             = 10L * 60 * 1000;
    private static final long BNF_PRIME_COOLDOWN_MS   = 12L * 60 * 1000;
    private static final int  LOOKAHEAD               = 24;
    private static final int  BACKTEST_DAYS           = 30;
    private static final double MIN_CONFIDENCE        = 80.0;
    // Load enough days for 200-candle indicator warmup (14 days) + 30-day scan window + buffer
    private static final int  LOAD_DAYS              = 47;

    static class SignalRecord {
        String date, time, symbol, direction, outcome, reasoning;
        double entryPrice, targetPts, slPts, targetPrice, slPrice, pointsCaptured, confidence;
    }

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    public static void main(String[] args) {
        System.out.println("Loading 120-day market data for 30-day backtest...");
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

            // Collect all unique trading dates from the data
            Set<LocalDate> allDatesSet = new LinkedHashSet<>();
            for (SimpleMarketData d : data) {
                if (d.timestamp != null) allDatesSet.add(d.timestamp.toLocalDate());
            }
            List<LocalDate> allDates = new ArrayList<>(allDatesSet);
            Collections.sort(allDates);

            // Take only the last BACKTEST_DAYS trading days
            LocalDate cutoffDate = allDates.size() > BACKTEST_DAYS
                ? allDates.get(allDates.size() - BACKTEST_DAYS)
                : allDates.get(0);

            System.out.printf("%s: scanning from %s to %s (%d candles total)%n",
                symbol, cutoffDate, allDates.get(allDates.size() - 1), data.size());

            Map<LocalDate, Long> lastSignalPerDay = new HashMap<>();
            long lastSignalMillis = 0;
            LocalDate lastSignalDate = null;

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData candle = data.get(i);
                if (candle.timestamp == null) continue;

                LocalDate candleDate = candle.timestamp.toLocalDate();
                if (candleDate.isBefore(cutoffDate)) continue;

                LocalTime time = candle.timestamp.toLocalTime();
                if (time.isBefore(LocalTime.of(9, 15)) || time.isAfter(LocalTime.of(15, 25))) continue;

                // Reset cooldown at start of each new day
                if (!candleDate.equals(lastSignalDate)) {
                    lastSignalMillis = 0;
                    lastSignalDate = candleDate;
                }

                int slab = getSlab(time);
                long cooldown = ("BANKNIFTY".equals(symbol) && slab == 1)
                    ? BNF_PRIME_COOLDOWN_MS : COOLDOWN_MS;

                long candleMillis = candle.timestamp
                    .atZone(java.time.ZoneId.of("Asia/Kolkata"))
                    .toInstant().toEpochMilli();
                if (candleMillis - lastSignalMillis < cooldown) continue;

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

    private static int getSlab(LocalTime t) {
        if (t.isBefore(LocalTime.of(11, 0))) return 0;
        if (t.isBefore(LocalTime.of(13, 0))) return 1;
        return 2;
    }

    private static void printReport(List<SignalRecord> signals) {
        if (signals.isEmpty()) {
            System.out.println("No signals generated in last 30 trading days.");
            return;
        }

        Map<String, List<SignalRecord>> byDate = new LinkedHashMap<>();
        for (SignalRecord r : signals) byDate.computeIfAbsent(r.date, k -> new ArrayList<>()).add(r);

        System.out.println("\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║      V29.0 BACKTEST — LAST 30 TRADING DAYS — FULL CALL DETAIL       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");

        int grandTotal = 0, grandWins = 0, grandPartial = 0, grandLoss = 0;
        double grandNet = 0;

        for (Map.Entry<String, List<SignalRecord>> entry : byDate.entrySet()) {
            String date = entry.getKey();
            List<SignalRecord> day = entry.getValue();
            int dW = 0, dP = 0, dL = 0; double dNet = 0;

            System.out.println("\n┌──────────────────────────────────────────────────────────────────────");
            System.out.printf( "│  DATE: %s   (%d calls generated)%n", date, day.size());
            System.out.println("├──────────────────────────────────────────────────────────────────────");

            int n = 0;
            for (SignalRecord r : day) {
                n++;
                String arrow = r.direction.equals("UP") ? "UP  ▲" : "DOWN ▼";
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
                System.out.printf("│    Reason     : %s%n", r.reasoning.length() > 80 ? r.reasoning.substring(0, 80) + "..." : r.reasoning);
                System.out.printf("│    Result     : %s  |  P&L: %+.0f pts%n", outIcon, r.pointsCaptured);

                if (r.outcome.equals("WIN"))         dW++;
                else if (r.outcome.equals("PARTIAL WIN")) dP++;
                else                                 dL++;
                dNet += r.pointsCaptured;
            }

            double dWR = (dW + dP + dL) > 0 ? (dW + dP) * 100.0 / (dW + dP + dL) : 0;
            System.out.println("│");
            System.out.printf( "│  DAY SUMMARY ── Calls: %d  ✅ %d  🔶 %d  ❌ %d  │  WR: %.0f%%  │  Net: %+.0f pts%n",
                day.size(), dW, dP, dL, dWR, dNet);
            System.out.println("└──────────────────────────────────────────────────────────────────────");

            grandTotal += day.size();
            grandWins += dW; grandPartial += dP; grandLoss += dL;
            grandNet += dNet;
        }

        // Per-symbol breakdown
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                   PER-SYMBOL BREAKDOWN                              ║");
        System.out.println("╠═══════════════╦═══════╦══════╦═════════╦══════════╦════════════════╣");
        System.out.println("║ Symbol        ║ Calls ║ Wins ║ Partial ║  WinRate ║   Net Points   ║");
        System.out.println("╠═══════════════╬═══════╬══════╬═════════╬══════════╬════════════════╣");

        for (String sym : new String[]{"NIFTY50", "BANKNIFTY", "SENSEX"}) {
            int sT=0, sW=0, sP=0, sL=0; double sNet=0;
            for (SignalRecord r : signals) {
                if (!r.symbol.equals(sym)) continue;
                sT++;
                if (r.outcome.equals("WIN"))          sW++;
                else if (r.outcome.equals("PARTIAL WIN")) sP++;
                else sL++;
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
        System.out.println("║               30-DAY FINAL SUMMARY                                  ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Total Calls Generated  : %-43d║%n", grandTotal);
        System.out.printf( "║  Full Wins (✅)          : %-43d║%n", grandWins);
        System.out.printf( "║  Partial Wins (🔶)       : %-43d║%n", grandPartial);
        System.out.printf( "║  Losses (❌)             : %-43d║%n", grandLoss);
        System.out.printf( "║  Overall Win Rate        : %-43s║%n", String.format("%.1f%%", grandWR));
        System.out.printf( "║  Net Points (30 days)   : %-43s║%n", String.format("%+.1f pts", grandNet));
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }
}
