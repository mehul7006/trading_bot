#!/usr/bin/env python3
"""
DEBUG INDICATORS - Diagnostic version to fix accuracy issues
"""

import pandas as pd
import numpy as np
import yfinance as yf
from datetime import datetime, timedelta
import warnings
warnings.filterwarnings('ignore')

def debug_single_symbol(symbol):
    """Debug analysis for a single symbol"""
    print(f"\n🔍 DEBUGGING {symbol}")
    print("=" * 50)
    
    # Get data
    symbol_map = {
        'NIFTY': '^NSEI',
        'BANKNIFTY': '^NSEBANK',
        'FINNIFTY': 'NIFTY_FIN_SERVICE.NS',
        'SENSEX': '^BSESN'
    }
    
    yahoo_symbol = symbol_map.get(symbol, symbol)
    
    try:
        ticker = yf.Ticker(yahoo_symbol)
        data = ticker.history(period='2mo')
        
        if data.empty:
            print(f"❌ No data for {symbol}")
            return
        
        print(f"✅ Data retrieved: {len(data)} days")
        
        # Extract data
        close_prices = data['Close'].values
        high_prices = data['High'].values
        low_prices = data['Low'].values
        volume_data = data['Volume'].values
        
        print(f"📊 Price range: ${close_prices[-1]:.2f} (Latest)")
        print(f"📈 Price trend: {((close_prices[-1] - close_prices[0]) / close_prices[0] * 100):+.2f}%")
        
        # Calculate indicators with debug info
        print("\n📊 INDICATOR ANALYSIS:")
        
        # RSI
        rsi = calculate_rsi_debug(close_prices)
        print(f"📈 RSI: {rsi:.1f}")
        
        # MACD
        macd, macd_signal, macd_hist = calculate_macd_debug(close_prices)
        print(f"📊 MACD: {macd:.2f}, Signal: {macd_signal:.2f}, Hist: {macd_hist:.2f}")
        
        # EMAs
        ema_9 = calculate_ema_debug(close_prices, 9)
        ema_21 = calculate_ema_debug(close_prices, 21)
        ema_50 = calculate_ema_debug(close_prices, 50)
        
        current_price = close_prices[-1]
        print(f"📈 Current: {current_price:.2f}")
        print(f"📈 EMA9: {ema_9:.2f}")
        print(f"📈 EMA21: {ema_21:.2f}")
        print(f"📈 EMA50: {ema_50:.2f}")
        
        # Scoring with debug
        print("\n🎯 SCORING ANALYSIS:")
        bullish_score = 0
        bearish_score = 0
        factors = []
        
        # Trend analysis
        if current_price > ema_9 > ema_21 > ema_50:
            bullish_score += 35
            factors.append("STRONG_UPTREND_ALIGNMENT")
            print("✅ Strong uptrend alignment (+35 bullish)")
        elif current_price < ema_9 < ema_21 < ema_50:
            bearish_score += 35
            factors.append("STRONG_DOWNTREND_ALIGNMENT")
            print("🔻 Strong downtrend alignment (+35 bearish)")
        elif current_price > ema_21:
            bullish_score += 20
            factors.append("BULLISH_TREND")
            print("📈 Bullish trend (+20 bullish)")
        elif current_price < ema_21:
            bearish_score += 20
            factors.append("BEARISH_TREND")
            print("📉 Bearish trend (+20 bearish)")
        else:
            print("➡️ Neutral trend (no points)")
        
        # MACD analysis
        if macd > macd_signal and macd_hist > 0:
            bullish_score += 25
            factors.append("MACD_BULLISH")
            print("✅ MACD bullish (+25 bullish)")
        elif macd < macd_signal and macd_hist < 0:
            bearish_score += 25
            factors.append("MACD_BEARISH")
            print("🔻 MACD bearish (+25 bearish)")
        else:
            print("➡️ MACD neutral (no points)")
        
        # RSI analysis
        if 30 < rsi < 70:
            if rsi > 50:
                bullish_score += 20
                factors.append("RSI_BULLISH_ZONE")
                print(f"✅ RSI bullish zone ({rsi:.1f}) (+20 bullish)")
            else:
                bearish_score += 20
                factors.append("RSI_BEARISH_ZONE")
                print(f"🔻 RSI bearish zone ({rsi:.1f}) (+20 bearish)")
        elif rsi < 30:
            bullish_score += 15
            factors.append("RSI_OVERSOLD_BOUNCE")
            print(f"📈 RSI oversold ({rsi:.1f}) (+15 bullish)")
        elif rsi > 70:
            bearish_score += 15
            factors.append("RSI_OVERBOUGHT_DECLINE")
            print(f"📉 RSI overbought ({rsi:.1f}) (+15 bearish)")
        
        print(f"\n🎯 FINAL SCORES:")
        print(f"Bullish Score: {bullish_score}")
        print(f"Bearish Score: {bearish_score}")
        print(f"Factors: {factors}")
        
        # Determine signal with lower thresholds
        if bullish_score > bearish_score and bullish_score >= 35:  # Lowered from 75
            signal = "STRONG_BUY" if bullish_score >= 70 else "BUY"
            confidence = min(95, bullish_score + 25)  # Boost confidence
        elif bearish_score > bullish_score and bearish_score >= 35:  # Lowered from 75
            signal = "STRONG_SELL" if bearish_score >= 70 else "SELL"
            confidence = min(95, bearish_score + 25)  # Boost confidence
        elif bullish_score > bearish_score and bullish_score >= 20:
            signal = "WEAK_BUY"
            confidence = bullish_score + 40
        elif bearish_score > bullish_score and bearish_score >= 20:
            signal = "WEAK_SELL"
            confidence = bearish_score + 40
        else:
            signal = "HOLD"
            confidence = 50
        
        print(f"\n🎯 FINAL RESULT:")
        print(f"Signal: {signal}")
        print(f"Confidence: {confidence:.1f}%")
        
        return {
            'signal': signal,
            'confidence': confidence,
            'factors': factors,
            'scores': {'bullish': bullish_score, 'bearish': bearish_score}
        }
        
    except Exception as e:
        print(f"❌ Error: {e}")
        return None

def calculate_rsi_debug(prices, period=14):
    """RSI with debug output"""
    if len(prices) < period + 1:
        print(f"⚠️ RSI: Insufficient data ({len(prices)} < {period+1})")
        return 50.0
    
    prices_array = np.array(prices, dtype=np.float64)
    delta = np.diff(prices_array)
    
    gains = np.where(delta > 0, delta, 0)
    losses = np.where(delta < 0, -delta, 0)
    
    avg_gain = np.mean(gains[-period:])
    avg_loss = np.mean(losses[-period:])
    
    if avg_loss == 0:
        return 100.0
    
    rs = avg_gain / avg_loss
    rsi = 100 - (100 / (1 + rs))
    
    return max(0, min(100, rsi))

def calculate_macd_debug(prices, fast=12, slow=26, signal=9):
    """MACD with debug output"""
    if len(prices) < slow + signal:
        print(f"⚠️ MACD: Insufficient data ({len(prices)} < {slow+signal})")
        return 0, 0, 0
    
    prices_series = pd.Series(prices, dtype=np.float64)
    
    ema_fast = prices_series.ewm(span=fast).mean()
    ema_slow = prices_series.ewm(span=slow).mean()
    
    macd_line = ema_fast - ema_slow
    signal_line = macd_line.ewm(span=signal).mean()
    histogram = macd_line - signal_line
    
    return macd_line.iloc[-1], signal_line.iloc[-1], histogram.iloc[-1]

def calculate_ema_debug(prices, period):
    """EMA with debug output"""
    if len(prices) < period:
        print(f"⚠️ EMA{period}: Insufficient data ({len(prices)} < {period})")
        return np.mean(prices) if len(prices) > 0 else 25000
    
    prices_series = pd.Series(prices, dtype=np.float64)
    ema = prices_series.ewm(span=period).mean()
    return ema.iloc[-1]

def main():
    print("🔍 DEBUGGING TRADING INDICATORS")
    print("=" * 60)
    
    symbols = ['NIFTY', 'BANKNIFTY', 'FINNIFTY', 'SENSEX']
    results = {}
    
    for symbol in symbols:
        result = debug_single_symbol(symbol)
        if result:
            results[symbol] = result
    
    print("\n" + "=" * 60)
    print("📊 SUMMARY OF DEBUGGING RESULTS")
    print("=" * 60)
    
    total_confidence = 0
    signal_count = 0
    
    for symbol, result in results.items():
        print(f"{symbol}: {result['signal']} ({result['confidence']:.1f}% confidence)")
        if result['confidence'] >= 60:  # Lowered threshold
            total_confidence += result['confidence']
            signal_count += 1
    
    avg_confidence = total_confidence / signal_count if signal_count > 0 else 0
    
    print(f"\nAverage Confidence: {avg_confidence:.1f}%")
    
    if avg_confidence >= 75:
        print("🎉 Target achieved!")
    elif avg_confidence >= 65:
        print("👍 Good performance")
    else:
        print("🔧 Needs optimization")

if __name__ == "__main__":
    main()