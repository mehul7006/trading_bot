#!/usr/bin/env python3

import csv
import datetime
from collections import defaultdict

def analyze_trading_log():
    print("📊 ANALYZING BOT TRADING PERFORMANCE")
    print("=====================================")
    
    # Read the trading log
    trades = []
    try:
        with open('real_data_trades.log', 'r') as f:
            for line in f:
                if line.strip():
                    parts = line.strip().split(',')
                    if len(parts) >= 6:
                        trades.append({
                            'timestamp': parts[0],
                            'symbol': parts[1],
                            'action': parts[2],
                            'price': float(parts[3]),
                            'profit_loss': float(parts[4]),
                            'result': parts[5],
                            'confidence': float(parts[6]) if len(parts) > 6 else 1.0
                        })
    except FileNotFoundError:
        print("❌ No trading log found")
        return
    
    if not trades:
        print("❌ No trades found in log")
        return
    
    print(f"📈 Total Trades Analyzed: {len(trades)}")
    print(f"📅 Date Range: {trades[0]['timestamp']} to {trades[-1]['timestamp']}")
    print()
    
    # Analyze by symbol
    symbol_stats = defaultdict(lambda: {'total': 0, 'profit': 0, 'loss': 0, 'total_pnl': 0.0})
    
    for trade in trades:
        symbol = trade['symbol']
        symbol_stats[symbol]['total'] += 1
        symbol_stats[symbol]['total_pnl'] += trade['profit_loss']
        
        if trade['result'] == 'PROFIT':
            symbol_stats[symbol]['profit'] += 1
        else:
            symbol_stats[symbol]['loss'] += 1
    
    print("📊 PERFORMANCE BY SYMBOL:")
    print("-" * 60)
    total_trades = 0
    total_profitable = 0
    total_pnl = 0.0
    
    for symbol, stats in symbol_stats.items():
        success_rate = (stats['profit'] / stats['total']) * 100
        total_trades += stats['total']
        total_profitable += stats['profit']
        total_pnl += stats['total_pnl']
        
        print(f"{symbol:8} | Trades: {stats['total']:2} | Wins: {stats['profit']:2} | "
              f"Success: {success_rate:5.1f}% | P&L: {stats['total_pnl']:+7.2f}")
    
    print("-" * 60)
    overall_success_rate = (total_profitable / total_trades) * 100
    print(f"{'OVERALL':8} | Trades: {total_trades:2} | Wins: {total_profitable:2} | "
          f"Success: {overall_success_rate:5.1f}% | P&L: {total_pnl:+7.2f}")
    
    print()
    print("🎯 PERFORMANCE ANALYSIS:")
    
    # Success rate analysis
    if overall_success_rate >= 80:
        print(f"🔥 EXCELLENT: {overall_success_rate:.1f}% success rate!")
    elif overall_success_rate >= 70:
        print(f"✅ VERY GOOD: {overall_success_rate:.1f}% success rate")
    elif overall_success_rate >= 60:
        print(f"👍 GOOD: {overall_success_rate:.1f}% success rate")
    elif overall_success_rate >= 50:
        print(f"⚠️ AVERAGE: {overall_success_rate:.1f}% success rate")
    else:
        print(f"❌ POOR: {overall_success_rate:.1f}% success rate - needs improvement")
    
    # P&L analysis
    if total_pnl > 0:
        print(f"💰 PROFITABLE: Total P&L of +{total_pnl:.2f} points")
    else:
        print(f"📉 LOSS: Total P&L of {total_pnl:.2f} points")
    
    # Average profit per trade
    avg_pnl = total_pnl / total_trades
    print(f"📊 Average P&L per trade: {avg_pnl:+.2f} points")
    
    # Analyze by action type
    print()
    print("📈 PERFORMANCE BY ACTION:")
    action_stats = defaultdict(lambda: {'total': 0, 'profit': 0, 'total_pnl': 0.0})
    
    for trade in trades:
        action = trade['action']
        action_stats[action]['total'] += 1
        action_stats[action]['total_pnl'] += trade['profit_loss']
        if trade['result'] == 'PROFIT':
            action_stats[action]['profit'] += 1
    
    for action, stats in action_stats.items():
        success_rate = (stats['profit'] / stats['total']) * 100
        avg_pnl = stats['total_pnl'] / stats['total']
        print(f"{action:4} | Trades: {stats['total']:2} | Success: {success_rate:5.1f}% | "
              f"Avg P&L: {avg_pnl:+6.2f}")
    
    print()
    print("🎯 TODAY'S CALL GENERATION SUMMARY:")
    print(f"✅ Total Calls Generated: {len(trades)}")
    print(f"🎯 Successful Calls: {total_profitable}")
    print(f"📊 Success Rate: {overall_success_rate:.1f}%")
    print(f"💰 Total Profit/Loss: {total_pnl:+.2f} points")
    
    # Recommendations
    print()
    print("💡 RECOMMENDATIONS:")
    if overall_success_rate >= 70:
        print("✅ Bot performance is good - continue current strategy")
    else:
        print("⚠️ Consider adjusting confidence thresholds")
        print("⚠️ Review entry/exit criteria")
    
    if total_pnl > 0:
        print("✅ Bot is profitable - good risk management")
    else:
        print("⚠️ Review stop-loss and target settings")

if __name__ == "__main__":
    analyze_trading_log()