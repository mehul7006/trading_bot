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
 * BacktestLast2Days — replays the V29.0 strategy on the last 2 actual trading days.
 * Prints every generated call as a Telegram-style message so you can verify entry,
 * target, SL and outcome exactly as the live bot would have sent them.
 */
public class BacktestLast2Days {

    // ── Same gates as the live bot ─────────────────────────────────────────────
    private static final double MIN_CONFIDENCE = 85.0;
    private static final Map<String, Double> MIN_POINTS = Map.of(
        "NIFTY50",   25.0,
        "SENSEX",    60.0,
        "BANKNIFTY", 70.0
    );
    // 10-min cooldown between signals per symbol (BankNifty 11-13 slot: 12 min)
    private static final long COOLDOWN_MS = 10L * 60 * 1000;
    private static final long BNF_PRIME_COOLDOWN_MS = 12L * 60 * 1000;

    // ── Individual signal record ───────────────────────────────────────────────
    static class SignalRecord {
        String   date;
        String   time;
        String   symbol;
        String   direction;
        double   entryPrice;
        double   targetPts;
        double   slPts;
        double   targetPrice;
        double   slPrice;
        String   outcome;     // WIN / PARTIAL WIN / LOSS / OPEN
        double   pointsCaptured;
        String   reasoning;
        double   confidence;
    }

    // ── Outcome constants ──────────────────────────────────────────────────────
    private static final int LOSS        = 0;
    private static final int PARTIAL_WIN = 1;
    private static final int FULL_WIN    = 2;
    private static final int LOOKAHEAD   = 24; // candles forward to check outcome

    public static void main(String[] args) {
        System.out.println("🔄 Loading market data...");
        MarketDataAgent  mdAgent   = new MarketDataAgent();
        PredictionAgent  predAgent = new PredictionAgent();

        String[] symbols = {"NIFTY50", "BANKNIFTY", "SENSEX"};
        List<SignalRecord> allSignals = new ArrayList<>();

        for (String symbol : symbols) {
            List<SignalRecord> signals = runBacktest(symbol, mdAgent, predAgent);
            allSignals.addAll(signals);
        }

        // Sort all signals by date+time
        allSignals.sort(Comparator.comparing((SignalRecord r) -> r.date)
                                  .thenComparing(r -> r.time));

        printReport(allSignals);
    }

    private static List<SignalRecord> runBacktest(String symbol,
            MarketDataAgent mdAgent, PredictionAgent predAgent) {

        List<SignalRecord> signals = new ArrayList<>();
        try {
            // Load FULL 120-day history so 200-candle warmup is always satisfied
            List<SimpleMarketData> data = mdAgent.getHistoricalData(symbol, 120);
            if (data == null || data.size() < 200) {
                System.err.println("⚠️  Not enough data for " + symbol + " (" +
                    (data == null ? 0 : data.size()) + " candles)");
                return signals;
            }

            // Find the last 2 unique trading dates that have actual candles
            Set<LocalDate> allDates = new LinkedHashSet<>();
            for (int i = data.size() - 1; i >= 0 && allDates.size() < 2; i--) {
                if (data.get(i).timestamp != null) {
                    LocalDate d = data.get(i).timestamp.toLocalDate();
                    allDates.add(d);
                }
            }
            List<LocalDate> last2Days = new ArrayList<>(allDates);
            Collections.sort(last2Days);

            if (last2Days.isEmpty()) return signals;

            LocalDate firstDay = last2Days.get(0);
            System.out.printf("📅 %s: backtesting %s → %s (%d total candles in history)%n",
                symbol, firstDay, last2Days.get(last2Days.size() - 1), data.size());

            long lastSignalMillis = 0;

            for (int i = 200; i < data.size(); i++) {
                SimpleMarketData candle = data.get(i);
                if (candle.timestamp == null) continue;

                LocalDate candleDate = candle.timestamp.toLocalDate();
                // Only process candles in our 2-day window
                if (candleDate.isBefore(firstDay)) continue;

                LocalTime time = candle.timestamp.toLocalTime();
                // Market hours only
                if (time.isBefore(LocalTime.of(9, 15)) ||
                    time.isAfter(LocalTime.of(15, 25)))  continue;

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

                // Signal fires ─ record it
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
                    case FULL_WIN    -> { outcomeLabel = "✅ WIN";         pts = tgtPts; }
                    case PARTIAL_WIN -> { outcomeLabel = "🔶 PARTIAL WIN"; pts = tgtPts * 0.3; }
                    default          -> { outcomeLabel = "❌ LOSS";        pts = -slPts; }
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
                rec.reasoning      = pred.predictionReasoning != null
                    ? pred.predictionReasoning : "-";
                signals.add(rec);
            }
        } catch (Exception e) {
            System.err.println("❌ Backtest error for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
        }
        return signals;
    }

    // ── Gates (identical to live bot) ─────────────────────────────────────────
    private static boolean passesGates(String symbol, AIPredictor.AIPrediction p) {
        if ("NEUTRAL".equals(p.predictedDirection)) return false;
        double minPts = MIN_POINTS.getOrDefault(symbol, 20.0);
        return p.confidence >= MIN_CONFIDENCE && p.estimatedMovePoints >= minPts;
    }

    // ── Outcome (identical to AuditAgent.verifyOutcome) ───────────────────────
    private static int verifyOutcome(List<SimpleMarketData> data, int idx,
            AIPredictor.AIPrediction pred) {
        double entry  = data.get(idx).price;
        String dir    = pred.predictedDirection;
        double tgt    = pred.estimatedMovePoints;
        double sl     = pred.suggestedStopLoss;
        double tgtPx  = dir.equals("UP") ? entry + tgt  : entry - tgt;
        double slPx   = dir.equals("UP") ? entry - sl   : entry + sl;
        double halfPx = dir.equals("UP") ? entry + tgt * 0.5 : entry - tgt * 0.5;
        boolean half  = false;
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

    // ── Print ──────────────────────────────────────────────────────────────────
    private static void printReport(List<SignalRecord> signals) {
        if (signals.isEmpty()) {
            System.out.println("\n📭 No signals generated in the last 2 trading days.");
            return;
        }

        // Group by date
        Map<String, List<SignalRecord>> byDate = new LinkedHashMap<>();
        for (SignalRecord r : signals) byDate.computeIfAbsent(r.date, k -> new ArrayList<>()).add(r);

        System.out.println("\n");
        System.out.println("═══════════════════════════════════════════════════════════════════════");
        System.out.println("  V29.0 BACKTEST — LAST 2 TRADING DAYS — TELEGRAM-STYLE MESSAGES");
        System.out.println("═══════════════════════════════════════════════════════════════════════");

        int grandTotal = 0, grandWins = 0, grandPartial = 0, grandLoss = 0;
        double grandNet = 0;

        for (Map.Entry<String, List<SignalRecord>> entry : byDate.entrySet()) {
            String date = entry.getKey();
            List<SignalRecord> day = entry.getValue();
            int dWins = 0, dPartial = 0, dLoss = 0;
            double dNet = 0;

            System.out.println("\n───────────────────────────────────────────────────────────────────────");
            System.out.println("  📅 DATE: " + date + "  (" + day.size() + " calls)");
            System.out.println("───────────────────────────────────────────────────────────────────────");

            int callNo = 0;
            for (SignalRecord r : day) {
                callNo++;
                String arrow = r.direction.equals("UP") ? "⬆️" : "⬇️";
                String emoji = r.direction.equals("UP") ? "🟢" : "🔴";
                double rrRatio = r.slPts > 0 ? r.targetPts / r.slPts : 0;

                System.out.printf("%n  ── CALL #%d ──────────────────────────────────────────────────────────%n", callNo);
                System.out.println("  " + emoji + " *TRADE SIGNAL*");
                System.out.println("  🕒 Time       : " + r.time + " IST  |  " + r.date);
                System.out.println("  📌 Symbol     : " + r.symbol);
                System.out.println("  🚀 Direction  : " + r.direction + " " + arrow);
                System.out.println();
                System.out.printf("  💰 Entry      : %.2f%n", r.entryPrice);
                System.out.printf("  🎯 Target     : %.2f  (+%.0f pts)%n", r.targetPrice, r.targetPts);
                System.out.printf("  🛑 Stop Loss  : %.2f  (-%.0f pts)%n", r.slPrice, r.slPts);
                System.out.printf("  📊 R:R        : 1:%.1f%n", rrRatio);
                System.out.printf("  🤖 Confidence : %.1f%%%n", r.confidence);
                System.out.println("  📝 Reason     : " + r.reasoning);
                System.out.println();
                System.out.println("  📦 OUTCOME    : " + r.outcome);
                System.out.printf("  💵 P&L        : %+.0f pts%n", r.pointsCaptured);
                System.out.println("  ─────────────────────────────────────────────────────────────────────");

                if (r.outcome.contains("WIN") && !r.outcome.contains("PARTIAL")) dWins++;
                else if (r.outcome.contains("PARTIAL")) dPartial++;
                else dLoss++;
                dNet += r.pointsCaptured;
            }

            int dResolved = dWins + dPartial + dLoss;
            double dWR = dResolved > 0 ? (dWins + dPartial) * 100.0 / dResolved : 0;
            System.out.printf("%n  📊 DAY SUMMARY — %s%n", date);
            System.out.printf("     Calls: %d  |  ✅ %d  🔶 %d  ❌ %d  |  WR: %.1f%%  |  Net: %+.0f pts%n",
                day.size(), dWins, dPartial, dLoss, dWR, dNet);

            grandTotal += day.size();
            grandWins += dWins; grandPartial += dPartial; grandLoss += dLoss;
            grandNet += dNet;
        }

        int grandResolved = grandWins + grandPartial + grandLoss;
        double grandWR = grandResolved > 0 ? (grandWins + grandPartial) * 100.0 / grandResolved : 0;

        System.out.println("\n═══════════════════════════════════════════════════════════════════════");
        System.out.println("  📋 2-DAY COMBINED SUMMARY");
        System.out.println("═══════════════════════════════════════════════════════════════════════");
        System.out.printf("  Total Calls   : %d%n", grandTotal);
        System.out.printf("  ✅ Full Wins  : %d%n", grandWins);
        System.out.printf("  🔶 Partial    : %d%n", grandPartial);
        System.out.printf("  ❌ Losses     : %d%n", grandLoss);
        System.out.printf("  📈 Win Rate   : %.1f%%%n", grandWR);
        System.out.printf("  💰 Net Points : %+.0f pts%n", grandNet);
        System.out.println("═══════════════════════════════════════════════════════════════════════");
    }
}
