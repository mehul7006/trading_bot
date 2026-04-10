package com.trading.bot.ai;

import com.trading.bot.agents.MarketDataAgent;
import com.trading.bot.market.SimpleMarketData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MLSignalFilter — Logistic Regression signal quality filter.
 *
 * Trained at bot startup on the last 45 days of historical signals.
 * Scores each live signal candidate with a win-probability.
 * Only signals with P(win) ≥ ML_THRESHOLD pass through.
 *
 * Features (8):
 *   [0] bias=1.0
 *   [1] confidence / 100
 *   [2] estimatedMove / 200
 *   [3] slPts / 200
 *   [4] timeSlab / 2  (0=morning, 1=prime, 2=afternoon)
 *   [5] dayOfWeek / 4  (0=Mon … 4=Fri)
 *   [6] symbolCode (0=NIFTY50, 1=SENSEX)
 *   [7] directionCode (0=DOWN, 1=UP)
 *   [8] rrRatio / 5
 */
public class MLSignalFilter {

    private static final Logger log = LoggerFactory.getLogger(MLSignalFilter.class);

    public static final double ML_THRESHOLD     = 0.60;   // pass bar — tuned from experiment
    private static final double TRAIN_CONF_MIN  = 84.0;   // low gate to collect more training data
    private static final double TRAIN_PTS_MIN   = 15.0;
    private static final long   COOLDOWN_MS     = 10L * 60 * 1_000;
    private static final int    LOOKAHEAD       = 24;
    private static final int    TRAIN_DAYS      = 45;
    private static final int    EPOCHS          = 2000;
    private static final double LR              = 0.01;

    private double[] weights;          // trained weights (null until training done)
    private final AtomicBoolean ready  = new AtomicBoolean(false);
    private int trainSize = 0;
    private double trainWR = 0;

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns true if the model is trained and ready to score. */
    public boolean isReady() { return ready.get(); }

    /**
     * Score a live signal candidate.
     * @return probability of WIN (0.0–1.0), or 0.5 if model not ready yet.
     */
    public double score(double confidence, double estMove, double slPts,
                        int timeSlab, int dayOfWeek, String symbol, String direction) {
        if (!ready.get() || weights == null) return 0.5; // pass through while training
        double rr = slPts > 0 ? estMove / slPts : 0;
        double[] x = features(confidence, estMove, slPts, timeSlab, dayOfWeek, symbol, direction, rr);
        return sigmoid(dot(weights, x));
    }

    /**
     * Train the model asynchronously (call once at startup).
     * When training completes, isReady() returns true.
     */
    public void trainAsync() {
        Thread t = new Thread(this::train, "MLFilter-Train");
        t.setDaemon(true);
        t.start();
    }

    /** Info string for status messages. */
    public String statusLine() {
        if (!ready.get()) return "ML model: training...";
        return String.format("ML model: ready (%d signals, train WR %.1f%%, threshold %.0f%%)",
            trainSize, trainWR, ML_THRESHOLD * 100);
    }

    // ── Training ─────────────────────────────────────────────────────────────

    private void train() {
        try {
            System.out.println("🧠 MLSignalFilter: starting training on last " + TRAIN_DAYS + " days...");
            log.info("MLSignalFilter: starting training on last {} days...", TRAIN_DAYS);
            MarketDataAgent md   = new MarketDataAgent();
            AIPredictor     pred = new AIPredictor();
            pred.initialize();

            List<double[]> Xlist = new ArrayList<>();
            List<Double>   ylist = new ArrayList<>();

            for (String sym : new String[]{"NIFTY50", "SENSEX"}) {
                List<SimpleMarketData> data;
                try { data = md.getHistoricalData(sym, TRAIN_DAYS); }
                catch (Exception e) { log.warn("MLFilter: data fetch failed for {}: {}", sym, e.getMessage()); continue; }
                if (data == null || data.size() < 200) continue;

                Set<LocalDate> dset = new LinkedHashSet<>();
                for (SimpleMarketData c : data) if (c.timestamp != null) dset.add(c.timestamp.toLocalDate());
                List<LocalDate> allDates = new ArrayList<>(dset);
                Collections.sort(allDates);

                long lastMs = 0; LocalDate lastDay = null;

                for (int i = 200; i < data.size(); i++) {
                    SimpleMarketData c = data.get(i);
                    if (c.timestamp == null) continue;
                    LocalDate  d = c.timestamp.toLocalDate();
                    LocalTime  t = c.timestamp.toLocalTime();
                    if (t.isBefore(LocalTime.of(9, 15)) || t.isAfter(LocalTime.of(15, 25))) continue;
                    if (!d.equals(lastDay)) { lastMs = 0; lastDay = d; }
                    long cMs = c.timestamp.atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();
                    if (cMs - lastMs < COOLDOWN_MS) continue;

                    List<SimpleMarketData> hist = data.subList(0, i + 1);
                    AIPredictor.AIPrediction p;
                    try { p = pred.generatePrediction(sym, hist); }
                    catch (Exception e) { continue; }

                    if ("NEUTRAL".equals(p.predictedDirection)) continue;
                    if (p.confidence < TRAIN_CONF_MIN || p.estimatedMovePoints < TRAIN_PTS_MIN) continue;

                    lastMs = cMs;
                    int outcome = verifyOutcome(data, i, p);
                    double label = (outcome > 0) ? 1.0 : 0.0;  // WIN or PARTIAL = 1

                    int slab   = getSlab(t);
                    int dow    = d.getDayOfWeek().getValue() - 1;
                    double rr  = p.suggestedStopLoss > 0 ? p.estimatedMovePoints / p.suggestedStopLoss : 0;

                    Xlist.add(features(p.confidence, p.estimatedMovePoints, p.suggestedStopLoss,
                                       slab, dow, sym, p.predictedDirection, rr));
                    ylist.add(label);
                }
            }

            int n = Xlist.size();
            if (n < 30) {
                log.warn("MLFilter: only {} training signals — not enough, skipping ML filter", n);
                return;
            }

            double[][] X = Xlist.toArray(new double[0][]);
            double[]   y = ylist.stream().mapToDouble(Double::doubleValue).toArray();

            // Compute training win rate
            double wins = 0; for (double v : y) wins += v;
            trainWR = wins * 100.0 / n;
            trainSize = n;

            // Chronological split: use ALL data for training (no test split for live model)
            weights = trainLogistic(X, y, LR, EPOCHS);

            ready.set(true);
            System.out.printf("✅ MLSignalFilter: READY — %d signals trained, raw WR=%.1f%%, threshold=%.0f%%%n",
                n, trainWR, ML_THRESHOLD * 100);
            log.info("MLSignalFilter: training complete. {} signals, raw WR={}, threshold={}", n, trainWR, ML_THRESHOLD);

        } catch (Exception e) {
            log.error("MLSignalFilter: training failed: {}", e.getMessage(), e);
        }
    }

    // ── Logistic Regression ──────────────────────────────────────────────────

    private static double[] trainLogistic(double[][] X, double[] y, double lr, int epochs) {
        int m = X[0].length;
        double[] w = new double[m];
        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] grad = new double[m];
            for (int i = 0; i < X.length; i++) {
                double err = sigmoid(dot(w, X[i])) - y[i];
                for (int j = 0; j < m; j++) grad[j] += err * X[i][j];
            }
            for (int j = 0; j < m; j++) w[j] -= lr * grad[j] / X.length;
        }
        return w;
    }

    private static double sigmoid(double z) { return 1.0 / (1.0 + Math.exp(-z)); }

    private static double dot(double[] w, double[] x) {
        double s = 0; for (int i = 0; i < w.length; i++) s += w[i] * x[i]; return s;
    }

    private static double[] features(double confidence, double estMove, double slPts,
                                      int timeSlab, int dayOfWeek, String symbol, String direction, double rr) {
        return new double[]{
            1.0,                                      // bias
            confidence / 100.0,
            estMove    / 200.0,
            slPts      / 200.0,
            timeSlab   / 2.0,
            dayOfWeek  / 4.0,
            "NIFTY50".equals(symbol) ? 0.0 : 1.0,
            "UP".equals(direction)   ? 1.0 : 0.0,
            rr         / 5.0
        };
    }

    // ── Outcome verification (same as BacktestLast30Days) ────────────────────

    private static final int LOSS = 0, PARTIAL = 1, WIN = 2;

    private static int verifyOutcome(List<SimpleMarketData> data, int idx, AIPredictor.AIPrediction p) {
        double entry  = data.get(idx).price;
        String dir    = p.predictedDirection;
        double tgtPx  = "UP".equals(dir) ? entry + p.estimatedMovePoints : entry - p.estimatedMovePoints;
        double slPx   = "UP".equals(dir) ? entry - p.suggestedStopLoss   : entry + p.suggestedStopLoss;
        double halfPx = "UP".equals(dir) ? entry + p.estimatedMovePoints * 0.5 : entry - p.estimatedMovePoints * 0.5;
        boolean half  = false;
        for (int j = 1; j <= LOOKAHEAD && (idx + j) < data.size(); j++) {
            SimpleMarketData c = data.get(idx + j);
            double h = c.high > 0 ? c.high : c.price;
            double l = c.low  > 0 ? c.low  : c.price;
            if ("UP".equals(dir)) {
                if (l <= slPx)  return half ? PARTIAL : LOSS;
                if (h >= tgtPx) return WIN;
                if (h >= halfPx) half = true;
            } else {
                if (h >= slPx)  return half ? PARTIAL : LOSS;
                if (l <= tgtPx) return WIN;
                if (l <= halfPx) half = true;
            }
        }
        return half ? PARTIAL : LOSS;
    }

    private static int getSlab(LocalTime t) {
        if (t.isBefore(LocalTime.of(11, 0))) return 0;
        if (t.isBefore(LocalTime.of(13, 0))) return 1;
        return 2;
    }
}
