package com.trading.bot.ai;

import com.trading.bot.market.SimpleMarketData;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * BANKNIFTY Strategy V31 — EXPERIMENTAL. Not wired into AIPredictor yet.
 *
 * Mirror of {@link SensexStrategyV31} retuned for BANKNIFTY price scale (~52k vs SENSEX ~80k):
 *  • MIN_SL_PTS=40, MIN_TGT_PTS=60 (vs 50/75 for SENSEX)
 *  • Same MTF + pullback + ADX-ceiling design
 *  • No expiry handover (BANKNIFTY weekly expiry was discontinued; monthly expiry handled separately if needed)
 *
 * User reported prior BANKNIFTY losses → integrate ONLY if backtest WR ≥ 70%.
 */
public final class BankNiftyStrategyV31 {

    private BankNiftyStrategyV31() {}

    private static final LocalTime EARLY_START = LocalTime.of(9, 45);
    private static final LocalTime EARLY_END   = LocalTime.of(11, 0);
    private static final LocalTime PRIME_START = LocalTime.of(11, 15);
    private static final LocalTime PRIME_END   = LocalTime.of(13, 0);
    private static final LocalTime CLOSE_START = LocalTime.of(14, 30);
    private static final LocalTime CLOSE_END   = LocalTime.of(15, 0);

    private static final double MIN_ADX_PRIME    = 22.0;
    private static final double MIN_ADX_EARLY    = 25.0;
    private static final double MIN_ADX_CLOSE    = 30.0;
    private static final double MAX_ADX          = 45.0;
    private static final double SL_ATR_MULT      = 0.6;
    private static final double TGT_RR_MULT      = 1.5;
    private static final double MIN_SL_PTS       = 40.0;
    private static final double MIN_TGT_PTS      = 60.0;
    private static final double MAX_RSI_UP       = 65.0;
    private static final double MIN_RSI_UP       = 45.0;
    private static final double MAX_RSI_DOWN     = 55.0;
    private static final double MIN_RSI_DOWN     = 35.0;
    private static final double VOL_REGIME_MIN   = 0.4;
    private static final double VOL_REGIME_MAX   = 2.5;
    private static final double PULLBACK_TOL_PCT = 0.003;
    private static final double TREND_SEPARATION = 1.0005;
    private static final int    REGIME_LOOKBACK_DAYS = 20;
    private static final int    BARS_PER_DAY_5MIN = 75;

    public static AIPredictor.AIPrediction predict(
            String symbol, List<SimpleMarketData> data, double currentPrice,
            double ema20, double ema50, double ema200, double rsi, double adx, double atr,
            SimpleMarketData latest) {

        if (data == null || data.size() < 240 || latest == null) {
            return neutral(adx, rsi, atr, currentPrice, "insufficient data");
        }

        LocalTime now = (latest.timestamp != null)
                ? latest.timestamp.toLocalTime()
                : LocalTime.now(ZoneId.of("Asia/Kolkata"));

        boolean inEarly = !now.isBefore(EARLY_START) && !now.isAfter(EARLY_END);
        boolean inPrime = !now.isBefore(PRIME_START) && !now.isAfter(PRIME_END);
        boolean inClose = !now.isBefore(CLOSE_START) && !now.isAfter(CLOSE_END);
        if (!inEarly && !inPrime && !inClose) return neutral(adx, rsi, atr, currentPrice, "outside windows");
        if (inClose && adx < MIN_ADX_CLOSE) return neutral(adx, rsi, atr, currentPrice, "close needs ADX>30");

        double atrAvg = computeAtrAvg20Day(data);
        if (atrAvg > 0) {
            double ratio = atr / atrAvg;
            if (ratio < VOL_REGIME_MIN) return neutral(adx, rsi, atr, currentPrice, "dead vol");
            if (ratio > VOL_REGIME_MAX) return neutral(adx, rsi, atr, currentPrice, "extreme vol");
        }

        double minAdx = inEarly ? MIN_ADX_EARLY : inClose ? MIN_ADX_CLOSE : MIN_ADX_PRIME;
        if (adx < minAdx) return neutral(adx, rsi, atr, currentPrice, "ADX<" + minAdx);
        if (adx > MAX_ADX) return neutral(adx, rsi, atr, currentPrice, "ADX>" + MAX_ADX);

        boolean trendUp   = currentPrice > ema200 && ema50 > ema200;
        boolean trendDown = currentPrice < ema200 && ema50 < ema200;
        if (!trendUp && !trendDown) return neutral(adx, rsi, atr, currentPrice, "no EMA trend");

        String bias5  = (currentPrice > ema20 * TREND_SEPARATION) ? "UP"
                      : (currentPrice < ema20 / TREND_SEPARATION) ? "DOWN" : "NEUTRAL";
        String bias15 = bucketBias(data, 3, 20);
        String bias60 = bucketBias(data, 12, 10);

        boolean alignedUp   = "UP".equals(bias5) && "UP".equals(bias15) && !"DOWN".equals(bias60);
        boolean alignedDown = "DOWN".equals(bias5) && "DOWN".equals(bias15) && !"UP".equals(bias60);
        if (!alignedUp && !alignedDown) return neutral(adx, rsi, atr, currentPrice, "MTF mis-alignment");

        String trendDir = alignedUp ? "UP" : "DOWN";
        if ((trendDir.equals("UP") && !trendUp) || (trendDir.equals("DOWN") && !trendDown)) {
            return neutral(adx, rsi, atr, currentPrice, "MTF/EMA conflict");
        }

        if (trendDir.equals("UP")   && (rsi <= MIN_RSI_UP   || rsi >= MAX_RSI_UP))   {
            return neutral(adx, rsi, atr, currentPrice, "RSI out of UP range");
        }
        if (trendDir.equals("DOWN") && (rsi <= MIN_RSI_DOWN || rsi >= MAX_RSI_DOWN)) {
            return neutral(adx, rsi, atr, currentPrice, "RSI out of DOWN range");
        }

        double[] macd = computeMacd(data);
        boolean macdOk = (trendDir.equals("UP") && macd[0] > macd[1])
                      || (trendDir.equals("DOWN") && macd[0] < macd[1]);
        if (!macdOk) return neutral(adx, rsi, atr, currentPrice, "MACD conflict");

        double volAvg = avgVolume(data, 20);
        boolean volOk = volAvg <= 0 || latest.volume >= volAvg * 0.7;
        if (!volOk) return neutral(adx, rsi, atr, currentPrice, "low volume");

        double tol = currentPrice * PULLBACK_TOL_PCT;
        boolean pullback = (currentPrice >= ema20 - tol && currentPrice <= ema20 + tol);
        if (!pullback) return neutral(adx, rsi, atr, currentPrice, "no pullback");

        double confidence = 90;
        if (inEarly) confidence += 2;
        if (inPrime) confidence += 1;
        if (adx > 30) confidence += 2;
        if (trendDir.equals("UP") && rsi >= 55) confidence += 1;
        if (trendDir.equals("DOWN") && rsi <= 45) confidence += 1;
        confidence = Math.min(95, confidence);

        double slPts  = Math.max(atr * SL_ATR_MULT, MIN_SL_PTS);
        double tgtPts = Math.max(slPts * TGT_RR_MULT, MIN_TGT_PTS);

        String reason = String.format(
                "BANKNIFTY_V31 | conf=5/5 | bias=%s/%s/%s | ADX=%.1f | RSI=%.1f | pullback@EMA20 | window=%s",
                bias5, bias15, bias60, adx, rsi,
                inEarly ? "EARLY" : inPrime ? "PRIME" : "CLOSE");

        return new AIPredictor.AIPrediction(trendDir, confidence, confidence / 100.0,
                adx, rsi, atr / currentPrice, 80, "BANKNIFTY_V31", reason, tgtPts, slPts, true);
    }

    private static AIPredictor.AIPrediction neutral(double adx, double rsi, double atr, double price, String why) {
        return new AIPredictor.AIPrediction("NEUTRAL", 0, 0, adx, rsi, atr / Math.max(price, 1), 80,
                "BANKNIFTY_V31", "filtered: " + why, atr, atr, false);
    }

    private static double computeAtrAvg20Day(List<SimpleMarketData> data) {
        int totalBars = Math.min(data.size(), BARS_PER_DAY_5MIN * REGIME_LOOKBACK_DAYS);
        if (totalBars < BARS_PER_DAY_5MIN * 5) return 0;
        double sum = 0; int counted = 0;
        for (int i = data.size() - totalBars + BARS_PER_DAY_5MIN; i < data.size(); i += BARS_PER_DAY_5MIN) {
            double a = atrAt(data, i, 14);
            if (a > 0) { sum += a; counted++; }
        }
        return counted > 0 ? sum / counted : 0;
    }

    private static double atrAt(List<SimpleMarketData> data, int endExclusive, int period) {
        if (endExclusive < period + 1) return 0;
        double sum = 0;
        for (int i = endExclusive - period; i < endExclusive; i++) {
            SimpleMarketData cur = data.get(i);
            SimpleMarketData prv = data.get(i - 1);
            double hl = cur.high - cur.low;
            double hc = Math.abs(cur.high - prv.price);
            double lc = Math.abs(cur.low  - prv.price);
            sum += Math.max(hl, Math.max(hc, lc));
        }
        return sum / period;
    }

    private static String bucketBias(List<SimpleMarketData> data, int barsPerBucket, int emaPeriod) {
        List<Double> closes = bucketCloses(data, barsPerBucket);
        if (closes.size() < emaPeriod + 2) return "NEUTRAL";
        double ema = emaFromList(closes, emaPeriod);
        double last = closes.get(closes.size() - 1);
        double prev = closes.get(closes.size() - 2);
        boolean rising = last > prev;
        boolean falling = last < prev;
        if (last > ema * TREND_SEPARATION && rising) return "UP";
        if (last < ema / TREND_SEPARATION && falling) return "DOWN";
        return "NEUTRAL";
    }

    private static List<Double> bucketCloses(List<SimpleMarketData> data, int barsPerBucket) {
        List<Double> result = new ArrayList<>();
        int n = data.size();
        for (int i = barsPerBucket; i <= n; i += barsPerBucket) result.add(data.get(i - 1).price);
        return result;
    }

    private static double emaFromList(List<Double> values, int period) {
        if (values.isEmpty()) return 0;
        if (values.size() < period) return values.get(values.size() - 1);
        double mult = 2.0 / (period + 1);
        double ema = values.get(0);
        for (int i = 1; i < values.size(); i++) ema = ((values.get(i) - ema) * mult) + ema;
        return ema;
    }

    private static double[] computeMacd(List<SimpleMarketData> data) {
        if (data.size() < 35) return new double[]{0, 0};
        List<Double> closes = new ArrayList<>();
        for (SimpleMarketData d : data) closes.add(d.price);
        double ema12 = emaFromList(closes, 12);
        double ema26 = emaFromList(closes, 26);
        double macd = ema12 - ema26;
        List<Double> macdSeries = new ArrayList<>();
        for (int i = 26; i < closes.size(); i++) {
            List<Double> sub = closes.subList(0, i + 1);
            macdSeries.add(emaFromList(sub, 12) - emaFromList(sub, 26));
        }
        double signal = emaFromList(macdSeries.subList(Math.max(0, macdSeries.size() - 9), macdSeries.size()), 9);
        return new double[]{macd, signal};
    }

    private static double avgVolume(List<SimpleMarketData> data, int period) {
        int n = data.size();
        if (n < period) return 0;
        long sum = 0;
        for (int i = n - period; i < n; i++) sum += data.get(i).volume;
        return (double) sum / period;
    }
}
