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
 * BacktestExperimental — runs the same V29.0 strategy but adds 5 new indicator filters
 * on top to see how much they improve win rate and reduce losing calls.
 *
 * NEW INDICATORS TESTED:
 *  1. Supertrend (period=10, multiplier=3.0)  — #1 used by Indian market traders
 *  2. CMF - Chaikin Money Flow (period=20)    — confirms money flow direction
 *  3. MFI - Money Flow Index (period=14)      — volume-weighted RSI, catches fake signals
 *  4. EMA200 Slope                             — macro trend acceleration/deceleration
 *  5. Daily Pivot Points (R1/R2/S1/S2)        — most watched institutional S/R in India
 *
 * DOES NOT MODIFY any main code. Purely experimental comparison.
 */
public class BacktestExperimental {

    private static final double MIN_CONFIDENCE = 86.0;
    private static final Map<String, Double> MIN_POINTS = Map.of(
        "NIFTY50", 28.0,
        "SENSEX",  65.0
    );
    private static final long COOLDOWN_MS   = 10L * 60 * 1000;
    private static final int  LOOKAHEAD     = 24;
    private static final int  BACKTEST_DAYS = 30;
    private static final int  LOAD_DAYS     = 47;

    // ── NEW INDICATOR PARAMETERS ──────────────────────────────────────────────
    private static final int    SUPERTREND_PERIOD     = 10;
    private static final double SUPERTREND_MULTIPLIER = 3.0;
    private static final int    CMF_PERIOD            = 20;
    private static final int    MFI_PERIOD            = 14;
    private static final int    EMA200_SLOPE_BARS     = 5;   // compare EMA200 now vs 5 bars ago
    // ─────────────────────────────────────────────────────────────────────────

    static class SignalRecord {
        String date, time, symbol, direction, outcome, reasoning;
        double entryPrice, targetPts, slPts, targetPrice, slPrice, pointsCaptured, confidence;
        boolean passedNew; // did it pass the new experimental filters?
        String blockedBy;  // which new filter blocked it (if any)
    }

    private static final int LOSS = 0, PARTIAL_WIN = 1, FULL_WIN = 2;

    public static void main(String[] args) {
        System.out.println("=============================================================");
        System.out.println("  EXPERIMENTAL BACKTEST — 5 New Indicators vs Current V29.0");
        System.out.println("  Loading 30-day market data...");
        System.out.println("=============================================================");

        MarketDataAgent mdAgent   = new MarketDataAgent();
        PredictionAgent predAgent = new PredictionAgent();

        String[] symbols = {"NIFTY50", "SENSEX"};
        List<SignalRecord> allSignals = new ArrayList<>();

        for (String symbol : symbols) {
            allSignals.addAll(runBacktest(symbol, mdAgent, predAgent));
        }

        allSignals.sort(Comparator.comparing((SignalRecord r) -> r.date).thenComparing(r -> r.time));
        printComparisonReport(allSignals);
    }

    // ─── MAIN BACKTEST LOOP ───────────────────────────────────────────────────
    private static List<SignalRecord> runBacktest(String symbol,
            MarketDataAgent mdAgent, PredictionAgent predAgent) {

        List<SignalRecord> signals = new ArrayList<>();
        try {
            List<SimpleMarketData> data = mdAgent.getHistoricalData(symbol, LOAD_DAYS);
            if (data == null || data.size() < 200) {
                System.err.println("Not enough data for " + symbol); return signals;
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

            System.out.printf("%s: scanning from %s to %s%n",
                symbol, cutoffDate, allDates.get(allDates.size() - 1));

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
                    .atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
                if (candleMillis - lastSignalMillis < COOLDOWN_MS) continue;

                List<SimpleMarketData> history = data.subList(0, i + 1);
                AIPredictor.AIPrediction pred = predAgent.generateSignal(symbol, history);

                if (!passesGates(symbol, pred)) continue;

                lastSignalMillis = candleMillis;

                // ── Apply 5 new experimental indicator filters ──────────────────
                String blockedBy = applyNewFilters(history, pred.predictedDirection, symbol);
                boolean passedNew = blockedBy == null;

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
                rec.passedNew      = passedNew;
                rec.blockedBy      = blockedBy != null ? blockedBy : "";
                signals.add(rec);
            }
        } catch (Exception e) {
            System.err.println("Backtest error for " + symbol + ": " + e.getMessage());
            e.printStackTrace();
        }
        return signals;
    }

    // ─── 5 NEW INDICATOR FILTERS ──────────────────────────────────────────────
    /**
     * Applies all 5 new experimental filters.
     * Returns null if signal passes ALL filters.
     * Returns the name of the first filter that blocked the signal.
     */
    private static String applyNewFilters(List<SimpleMarketData> data,
                                           String direction, String symbol) {
        int n = data.size();
        if (n < 30) return null; // not enough data, don't block

        // ── ROUND 2: Only the PROVEN GOOD filters (from Round 1 analysis) ──────
        //
        // REMOVED (they block winning signals):
        //   - Supertrend: blocked 82 signals with 72% WR → was killing winners
        //   - MFI overbought: blocked 46 signals with 63% WR → momentum continues in India
        //   - CMF: blocked 0 signals → no effect
        //
        // KEPT (they remove mostly losers):
        //   - EMA200 Slope (very sharp moves against macro trend)
        //   - Pivot Point extremes (R2 resistance for UP, S2 support for DOWN)
        //
        // NEW ADDITIONS based on Round 1 learning:
        //   - Consecutive candle exhaustion (5+ same-direction candles = likely reversal)
        //   - OBV divergence (price up but volume declining = weak move)
        //   - RSI divergence (price new high/low but RSI not confirming)

        // ── Filter 1: EMA200 Slope — avoid very sharp counter-macro-trend entries ──
        double ema200Slope = calcEMA200Slope(data);
        if (direction.equals("UP")   && ema200Slope < -0.08) return "EMA200_SLOPE_DOWN";
        if (direction.equals("DOWN") && ema200Slope >  0.08) return "EMA200_SLOPE_UP";

        // ── Filter 2: Daily Pivot extremes — price overextended vs pivot levels ─
        double[] pivots = calcDailyPivots(data);
        if (pivots != null) {
            double price = data.get(n - 1).price;
            double r2 = pivots[2];
            double s2 = pivots[4];
            if (direction.equals("UP")   && price > r2 * 0.999) return "PIVOT_AT_R2_RESIST";
            if (direction.equals("DOWN") && price < s2 * 1.001) return "PIVOT_AT_S2_SUPPORT";
        }

        // ── Filter 3: Candle exhaustion — 5+ consecutive same-direction candles ─
        // If last 5 candles are ALL bullish (close > open) and signal is UP,
        // the move may be exhausted. Wait for a pullback candle first.
        int consecutiveSame = countConsecutiveCandles(data, direction);
        if (consecutiveSame >= 5) return "CANDLE_EXHAUSTION_" + consecutiveSame;

        // ── Filter 4: OBV divergence — price rising but volume declining ────────
        // If price made a new 10-bar high but OBV is LOWER than 5 bars ago → weak move
        // If price made a new 10-bar low but OBV is HIGHER than 5 bars ago → weak move
        if (hasOBVDivergence(data, direction)) return "OBV_DIVERGENCE";

        // ── Filter 5: RSI divergence — price at extreme but RSI not confirming ──
        // Price making new 10-bar high/low but RSI diverging is a classic reversal signal
        // For UP trades: if price is at 10-bar high but RSI is LOWER than 5 bars ago → bearish divergence
        if (hasRSIDivergence(data, direction)) return "RSI_DIVERGENCE";

        return null; // all filters passed
    }

    // ─── INDICATOR CALCULATIONS ───────────────────────────────────────────────

    /**
     * Supertrend indicator.
     * Returns +1.0 if trend is UP (price above supertrend line)
     *         -1.0 if trend is DOWN (price below supertrend line)
     */
    private static double calcSupertrend(List<SimpleMarketData> data) {
        int n = data.size();
        int period = SUPERTREND_PERIOD;
        double mult = SUPERTREND_MULTIPLIER;

        if (n < period + 5) return 1.0; // default bullish if not enough data

        // Calculate ATR for supertrend
        double[] atrs = new double[n];
        for (int i = 1; i < n; i++) {
            double tr = Math.max(data.get(i).high - data.get(i).low,
                         Math.max(Math.abs(data.get(i).high - data.get(i-1).price),
                                  Math.abs(data.get(i).low  - data.get(i-1).price)));
            atrs[i] = tr;
        }

        // Smooth ATR using simple average for first period, then exponential
        double atr = 0;
        for (int i = n - period; i < n; i++) atr += atrs[i];
        atr /= period;

        // Upper and lower bands for last bar
        double hl2 = (data.get(n-1).high + data.get(n-1).low) / 2.0;
        double upperBand = hl2 + mult * atr;
        double lowerBand = hl2 - mult * atr;
        double price = data.get(n-1).price;

        // Determine trend: above lower band = UP trend, below upper band = DOWN trend
        // Simplified: if price is above midpoint of bands = UP, else DOWN
        double midBand = (upperBand + lowerBand) / 2.0;

        // More accurate: use prior bar to determine trend persistence
        if (n >= period + 2) {
            double prevHl2 = (data.get(n-2).high + data.get(n-2).low) / 2.0;
            // Calculate ATR for previous bar
            double prevAtr = 0;
            for (int i = n - period - 1; i < n - 1; i++) prevAtr += atrs[i];
            prevAtr /= period;
            double prevLower = prevHl2 - mult * prevAtr;
            double prevUpper = prevHl2 + mult * prevAtr;
            double prevPrice = data.get(n-2).price;

            // Trend is UP if price is above lower band
            boolean currUp = price > lowerBand || (prevPrice > prevLower && price > lowerBand * 0.999);
            boolean currDn = price < upperBand || (prevPrice < prevUpper && price < upperBand * 1.001);

            if (currUp && !currDn) return 1.0;
            if (currDn && !currUp) return -1.0;
        }

        return price > midBand ? 1.0 : -1.0;
    }

    /**
     * Chaikin Money Flow (CMF).
     * Range: -1 to +1
     * Positive = accumulation (buying pressure), Negative = distribution (selling pressure)
     */
    private static double calcCMF(List<SimpleMarketData> data, int period) {
        int n = data.size();
        if (n < period) return 0.0;

        double sumMFV = 0, sumVol = 0;
        for (int i = n - period; i < n; i++) {
            SimpleMarketData c = data.get(i);
            double range = c.high - c.low;
            if (range < 0.0001) { sumVol += c.volume; continue; }
            double mfm = ((c.price - c.low) - (c.high - c.price)) / range; // Money Flow Multiplier
            sumMFV += mfm * c.volume;
            sumVol += c.volume;
        }
        return sumVol > 0 ? sumMFV / sumVol : 0.0;
    }

    /**
     * Money Flow Index (MFI) — volume-weighted RSI.
     * Range: 0-100
     * > 80: overbought, < 20: oversold
     */
    private static double calcMFI(List<SimpleMarketData> data, int period) {
        int n = data.size();
        if (n < period + 1) return 50.0;

        double posFlow = 0, negFlow = 0;
        for (int i = n - period; i < n; i++) {
            double tp      = (data.get(i).high + data.get(i).low + data.get(i).price) / 3.0;
            double tpPrev  = (data.get(i-1).high + data.get(i-1).low + data.get(i-1).price) / 3.0;
            double mf = tp * data.get(i).volume;
            if (tp > tpPrev)  posFlow += mf;
            else              negFlow += mf;
        }
        if (negFlow < 0.0001) return 100.0;
        double mfr = posFlow / negFlow;
        return 100.0 - (100.0 / (1.0 + mfr));
    }

    /**
     * EMA200 Slope — measures how much EMA200 changed in last N bars (% change).
     * Positive = rising trend, Negative = falling trend.
     */
    private static double calcEMA200Slope(List<SimpleMarketData> data) {
        int n = data.size();
        if (n < 205) return 0.0;

        double emaNow  = calcEMA(data, 200, n - 1);
        double emaPrev = calcEMA(data, 200, n - 1 - EMA200_SLOPE_BARS);

        if (emaPrev < 0.001) return 0.0;
        return ((emaNow - emaPrev) / emaPrev) * 100.0; // % change
    }

    /**
     * Calculate EMA at a specific end index.
     */
    private static double calcEMA(List<SimpleMarketData> data, int period, int endIdx) {
        if (endIdx < period) return data.get(endIdx).price;
        double k = 2.0 / (period + 1);
        double ema = data.get(endIdx - period).price;
        for (int i = endIdx - period + 1; i <= endIdx; i++) {
            ema = data.get(i).price * k + ema * (1 - k);
        }
        return ema;
    }

    /**
     * Count how many consecutive candles in the given direction (close > open for UP).
     * Used for exhaustion detection.
     */
    private static int countConsecutiveCandles(List<SimpleMarketData> data, String direction) {
        int n = data.size();
        int count = 0;
        for (int i = n - 1; i >= Math.max(0, n - 8); i--) {
            SimpleMarketData c = data.get(i);
            boolean aligned = direction.equals("UP")
                ? c.price > c.open
                : c.price < c.open;
            if (aligned) count++;
            else break;
        }
        return count;
    }

    /**
     * OBV Divergence: price makes new high/low but OBV is moving opposite direction.
     * Indicates the move lacks volume conviction.
     */
    private static boolean hasOBVDivergence(List<SimpleMarketData> data, String direction) {
        int n = data.size();
        if (n < 15) return false;

        // Calculate OBV for last 15 bars
        double[] obv = new double[15];
        obv[0] = 0;
        for (int i = 1; i < 15; i++) {
            int idx = n - 15 + i;
            double priceDiff = data.get(idx).price - data.get(idx - 1).price;
            obv[i] = obv[i-1] + (priceDiff > 0 ? data.get(idx).volume : priceDiff < 0 ? -data.get(idx).volume : 0);
        }

        double priceNow  = data.get(n - 1).price;
        double price5ago = data.get(n - 6).price;
        double obvNow    = obv[14];
        double obv5ago   = obv[9];

        // UP signal: price at new high but OBV declining → divergence (bearish)
        if (direction.equals("UP")   && priceNow > price5ago && obvNow < obv5ago * 0.95) return true;
        // DOWN signal: price at new low but OBV rising → divergence (bullish)
        if (direction.equals("DOWN") && priceNow < price5ago && obvNow > obv5ago * 1.05) return true;

        return false;
    }

    /**
     * RSI Divergence: price makes new high/low but RSI is moving opposite.
     * Classic leading indicator of trend reversal.
     */
    private static boolean hasRSIDivergence(List<SimpleMarketData> data, String direction) {
        int n = data.size();
        if (n < 25) return false;

        // Simple RSI calculation for current and 8 bars ago
        double rsiNow  = calcSimpleRSI(data, 14, n - 1);
        double rsi8ago = calcSimpleRSI(data, 14, n - 9);

        double priceNow  = data.get(n - 1).price;
        double price8ago = data.get(n - 9).price;

        // UP signal: price making higher high but RSI making lower high → bearish divergence
        // Only apply when divergence is significant (RSI dropped 5+ points)
        if (direction.equals("UP") && priceNow > price8ago * 1.003 && rsiNow < rsi8ago - 5) return true;

        // DOWN signal: price making lower low but RSI making higher low → bullish divergence
        if (direction.equals("DOWN") && priceNow < price8ago * 0.997 && rsiNow > rsi8ago + 5) return true;

        return false;
    }

    /**
     * Simple RSI at a specific index.
     */
    private static double calcSimpleRSI(List<SimpleMarketData> data, int period, int endIdx) {
        if (endIdx < period) return 50.0;
        double gain = 0, loss = 0;
        for (int i = endIdx - period + 1; i <= endIdx; i++) {
            double change = data.get(i).price - data.get(i - 1).price;
            if (change > 0) gain += change;
            else            loss -= change;
        }
        if (loss < 0.0001) return 100.0;
        double rs = (gain / period) / (loss / period);
        return 100.0 - (100.0 / (1.0 + rs));
    }

    /**
     * Calculate Standard Daily Pivot Points from previous trading day's OHLC.
     * Returns: [PP, R1, R2, S1, S2]
     */
    private static double[] calcDailyPivots(List<SimpleMarketData> data) {
        int n = data.size();
        if (n < 10) return null;

        // Find previous day's OHLC
        LocalDate today = data.get(n - 1).timestamp.toLocalDate();
        LocalDate prevDay = null;
        double pdHigh = Double.MIN_VALUE, pdLow = Double.MAX_VALUE, pdClose = 0;

        for (int i = n - 1; i >= 0; i--) {
            if (data.get(i).timestamp == null) continue;
            LocalDate barDate = data.get(i).timestamp.toLocalDate();
            if (barDate.equals(today)) continue; // skip today
            if (prevDay == null) prevDay = barDate;
            if (!barDate.equals(prevDay)) break; // done with prev day

            pdHigh  = Math.max(pdHigh, data.get(i).high);
            pdLow   = Math.min(pdLow,  data.get(i).low);
            pdClose = data.get(i).price; // last bar of prev day = close (we overwrite, ends up as open bar of prev day)
        }
        // Get actual close of previous day: the LAST bar of that day
        for (int i = n - 1; i >= 0; i--) {
            if (data.get(i).timestamp == null) continue;
            LocalDate barDate = data.get(i).timestamp.toLocalDate();
            if (barDate.equals(prevDay)) { pdClose = data.get(i).price; break; }
        }

        if (prevDay == null || pdHigh == Double.MIN_VALUE) return null;

        double pp = (pdHigh + pdLow + pdClose) / 3.0;
        double r1 = 2 * pp - pdLow;
        double r2 = pp + (pdHigh - pdLow);
        double s1 = 2 * pp - pdHigh;
        double s2 = pp - (pdHigh - pdLow);
        return new double[]{pp, r1, r2, s1, s2};
    }

    // ─── GATES & OUTCOME ─────────────────────────────────────────────────────
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

    // ─── REPORT ──────────────────────────────────────────────────────────────
    private static void printComparisonReport(List<SignalRecord> signals) {
        if (signals.isEmpty()) {
            System.out.println("No signals generated."); return;
        }

        // ── Current strategy stats (ALL signals) ──────────────────────────────
        int cTotal = 0, cWins = 0, cPartial = 0, cLoss = 0; double cNet = 0;
        // ── Experimental stats (only signals that ALSO pass new filters) ───────
        int eTotal = 0, eWins = 0, ePartial = 0, eLoss = 0; double eNet = 0;
        // ── Blocked signal stats ───────────────────────────────────────────────
        Map<String, int[]> blockedStats = new LinkedHashMap<>(); // filter → [total,wins,partials,losses]

        for (SignalRecord r : signals) {
            cTotal++;
            if (r.outcome.equals("WIN"))          cWins++;
            else if (r.outcome.equals("PARTIAL WIN")) cPartial++;
            else                                   cLoss++;
            cNet += r.pointsCaptured;

            if (r.passedNew) {
                eTotal++;
                if (r.outcome.equals("WIN"))          eWins++;
                else if (r.outcome.equals("PARTIAL WIN")) eLoss++;  // note: will subtract from loss below
                else                                   eLoss++;
                // fix: count properly
                eNet += r.pointsCaptured;
            } else {
                // Track which filter blocked it and whether it was a win/loss
                blockedStats.computeIfAbsent(r.blockedBy, k -> new int[4]);
                int[] bs = blockedStats.get(r.blockedBy);
                bs[0]++;
                if (r.outcome.equals("WIN"))          bs[1]++;
                else if (r.outcome.equals("PARTIAL WIN")) bs[2]++;
                else                                   bs[3]++;
            }
        }

        // Re-count experimental properly
        eWins = 0; ePartial = 0; eLoss = 0;
        for (SignalRecord r : signals) {
            if (!r.passedNew) continue;
            if (r.outcome.equals("WIN"))           eWins++;
            else if (r.outcome.equals("PARTIAL WIN")) ePartial++;
            else                                    eLoss++;
        }

        double cWR = cTotal > 0 ? (cWins + cPartial) * 100.0 / cTotal : 0;
        double eWR = eTotal > 0 ? (eWins + ePartial) * 100.0 / eTotal : 0;

        System.out.println("\n");
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║        EXPERIMENTAL vs CURRENT — 30-DAY COMPARISON REPORT            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");

        System.out.println("\n┌─────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                   CURRENT V29.0 (baseline)                           │");
        System.out.printf( "│  Calls: %-5d  Wins: %-4d  Partial: %-4d  Loss: %-4d               │%n",
            cTotal, cWins, cPartial, cLoss);
        System.out.printf( "│  Win Rate: %.1f%%   Net Points: %+.1f pts%n", cWR, cNet);
        System.out.println("└─────────────────────────────────────────────────────────────────────┘");

        System.out.println("\n┌─────────────────────────────────────────────────────────────────────┐");
        System.out.println("│        EXPERIMENTAL — With 5 New Indicator Filters                   │");
        System.out.printf( "│  Calls: %-5d  Wins: %-4d  Partial: %-4d  Loss: %-4d               │%n",
            eTotal, eWins, ePartial, eLoss);
        System.out.printf( "│  Win Rate: %.1f%%   Net Points: %+.1f pts%n", eWR, eNet);
        System.out.println("└─────────────────────────────────────────────────────────────────────┘");

        System.out.printf("%n  Δ Win Rate  : %+.1f%% (%s)%n", eWR - cWR, eWR > cWR ? "IMPROVED" : "WORSE");
        System.out.printf("  Δ Calls     : %+d (blocked %d calls)%n", eTotal - cTotal, cTotal - eTotal);
        System.out.printf("  Δ Net Pts   : %+.1f pts%n%n", eNet - cNet);

        // ── Blocked signal quality breakdown ──────────────────────────────────
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              WHICH FILTER BLOCKED WHICH SIGNAL QUALITY               ║");
        System.out.println("╠═══════════════════════╦═══════╦═══════╦═════════╦══════╦═════════════╣");
        System.out.println("║ Filter                ║ Total ║  Wins ║ Partial ║ Loss ║  WR%        ║");
        System.out.println("╠═══════════════════════╬═══════╬═══════╬═════════╬══════╬═════════════╣");
        for (Map.Entry<String, int[]> e : blockedStats.entrySet()) {
            int[] b = e.getValue();
            double bWR = b[0] > 0 ? (b[1] + b[2]) * 100.0 / b[0] : 0;
            // If WR of blocked signals is LOW → filter is GOOD (it blocked losers)
            // If WR of blocked signals is HIGH → filter is BAD (it blocked winners)
            String quality = bWR < 40 ? "GREAT filter" : bWR < 55 ? "OK filter" : "HURTS WR";
            System.out.printf("║ %-21s ║  %4d ║  %4d ║   %4d  ║ %4d ║ %5.1f%% %-5s║%n",
                e.getKey(), b[0], b[1], b[2], b[3], bWR, quality);
        }
        System.out.println("╚═══════════════════════╩═══════╩═══════╩═════════╩══════╩═════════════╝");

        System.out.println("\n  Interpretation:");
        System.out.println("  • If blocked signal WR < 40% → filter is EXCELLENT (removes mostly losses)");
        System.out.println("  • If blocked signal WR 40-55% → filter is OK (removes mix)");
        System.out.println("  • If blocked signal WR > 55% → filter is BAD (removes good signals too)");

        // ── Per-symbol breakdown ───────────────────────────────────────────────
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                   PER-SYMBOL COMPARISON                              ║");
        System.out.println("╠═══════════════╦══════════════════════╦══════════════════════╦═════════╣");
        System.out.println("║ Symbol        ║   Current V29.0      ║   Experimental        ║ Delta   ║");
        System.out.println("║               ║ Calls   WR%    Net   ║ Calls   WR%    Net   ║  WR%    ║");
        System.out.println("╠═══════════════╬══════════════════════╬══════════════════════╬═════════╣");

        for (String sym : new String[]{"NIFTY50", "SENSEX"}) {
            int cT=0,cW=0,cP=0; double cN=0;
            int eT=0,eW=0,eP=0; double eN=0;
            for (SignalRecord r : signals) {
                if (!r.symbol.equals(sym)) continue;
                cT++; cN += r.pointsCaptured;
                if (r.outcome.equals("WIN")) cW++;
                else if (r.outcome.equals("PARTIAL WIN")) cP++;
                if (r.passedNew) {
                    eT++; eN += r.pointsCaptured;
                    if (r.outcome.equals("WIN")) eW++;
                    else if (r.outcome.equals("PARTIAL WIN")) eP++;
                }
            }
            double cWRs = cT > 0 ? (cW+cP)*100.0/cT : 0;
            double eWRs = eT > 0 ? (eW+eP)*100.0/eT : 0;
            System.out.printf("║ %-13s ║  %-4d  %5.1f%%  %+6.0f ║  %-4d  %5.1f%%  %+6.0f ║ %+5.1f%%  ║%n",
                sym, cT, cWRs, cN, eT, eWRs, eN, eWRs - cWRs);
        }
        System.out.println("╚═══════════════╩══════════════════════╩══════════════════════╩═════════╝");

        // ── Recommendation ────────────────────────────────────────────────────
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        RECOMMENDATION                                ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");
        if (eWR > cWR + 1.5 && eTotal >= 50) {
            System.out.printf("║  ✅ IMPLEMENT: New filters improved WR by %.1f%% with %d calls/month   ║%n",
                eWR - cWR, eTotal);
            System.out.println("║  → Add Supertrend, CMF, MFI, EMA200 slope, Pivot filters to main code║");
        } else if (eWR > cWR) {
            System.out.printf("║  ⚠ MARGINAL: Only %.1f%% WR improvement. Review filter breakdown above.║%n",
                eWR - cWR);
        } else {
            System.out.printf("║  ❌ DO NOT IMPLEMENT: Filters made WR worse by %.1f%%                   ║%n",
                cWR - eWR);
            System.out.println("║  → The blocked signals were actually good signals being removed.       ║");
        }
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
    }
}
