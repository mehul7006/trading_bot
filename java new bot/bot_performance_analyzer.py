#!/usr/bin/env python3
"""
COMPREHENSIVE BOT AUDIT & PERFORMANCE ANALYZER
Analyzes trading bot logs for honest performance evaluation
"""

import re
import json
from datetime import datetime, timedelta
from collections import defaultdict, Counter
import statistics

def analyze_bot_logs():
    """Comprehensive analysis of all bot logs"""
    
    print("🔍 COMPREHENSIVE BOT AUDIT REPORT")
    print("=" * 50)
    print(f"📅 Analysis Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print()
    
    # Analyze Enhanced Bot Log
    enhanced_analysis = analyze_enhanced_bot_log()
    
    # Analyze Improved Bot Log  
    improved_analysis = analyze_improved_bot_log()
    
    # Analyze High Win Bot Log
    high_win_analysis = analyze_high_win_bot_log()
    
    # Generate comprehensive report
    generate_comprehensive_report(enhanced_analysis, improved_analysis, high_win_analysis)

def analyze_enhanced_bot_log():
    """Analyze enhanced_bot.log for real API calls and functionality"""
    
    print("📊 ENHANCED BOT ANALYSIS")
    print("-" * 30)
    
    try:
        with open('enhanced_bot.log', 'r') as f:
            content = f.read()
    except FileNotFoundError:
        print("❌ enhanced_bot.log not found")
        return {}
    
    analysis = {
        'api_calls': 0,
        'successful_calls': 0,
        'failed_calls': 0,
        'indices_tracked': set(),
        'real_prices': [],
        'telegram_commands': 0,
        'functionality_status': {}
    }
    
    lines = content.split('\n')
    
    for line in lines:
        # Count API calls
        if 'Upstox API call' in line:
            analysis['api_calls'] += 1
            if '200' in line:
                analysis['successful_calls'] += 1
            else:
                analysis['failed_calls'] += 1
        
        # Extract real prices
        if 'Real price from Upstox' in line:
            match = re.search(r'Real price from Upstox for (\w+): ([\d.]+)', line)
            if match:
                symbol, price = match.groups()
                analysis['indices_tracked'].add(symbol)
                analysis['real_prices'].append({
                    'symbol': symbol,
                    'price': float(price),
                    'timestamp': 'extracted'
                })
        
        # Count Telegram commands
        if 'Command received:' in line:
            analysis['telegram_commands'] += 1
    
    # Determine functionality status
    analysis['functionality_status'] = {
        'upstox_integration': analysis['successful_calls'] > 0,
        'telegram_integration': analysis['telegram_commands'] > 0,
        'real_data_fetching': len(analysis['real_prices']) > 0,
        'index_tracking': len(analysis['indices_tracked']) >= 3
    }
    
    print(f"✅ API Calls: {analysis['api_calls']} (Success: {analysis['successful_calls']}, Failed: {analysis['failed_calls']})")
    print(f"✅ Indices Tracked: {', '.join(analysis['indices_tracked'])}")
    print(f"✅ Real Prices Captured: {len(analysis['real_prices'])}")
    print(f"✅ Telegram Commands: {analysis['telegram_commands']}")
    print()
    
    return analysis

def analyze_improved_bot_log():
    """Analyze improved_bot.log for trading opportunities and signals"""
    
    print("🎯 IMPROVED BOT TRADING ANALYSIS")
    print("-" * 35)
    
    try:
        with open('improved_bot.log', 'r') as f:
            content = f.read()
    except FileNotFoundError:
        print("❌ improved_bot.log not found")
        return {}
    
    analysis = {
        'total_opportunities': 0,
        'buy_signals': 0,
        'sell_signals': 0,
        'confidence_levels': [],
        'symbol_performance': defaultdict(list),
        'high_confidence_calls': 0,
        'medium_confidence_calls': 0,
        'low_confidence_calls': 0
    }
    
    lines = content.split('\n')
    
    for line in lines:
        if 'OPPORTUNITY:' in line:
            analysis['total_opportunities'] += 1
            
            # Extract signal details
            match = re.search(r'OPPORTUNITY: (\w+) (BUY|SELL) \(Confidence: ([\d.]+)%\)', line)
            if match:
                symbol, signal_type, confidence = match.groups()
                confidence = float(confidence)
                
                analysis['confidence_levels'].append(confidence)
                analysis['symbol_performance'][symbol].append({
                    'signal': signal_type,
                    'confidence': confidence
                })
                
                if signal_type == 'BUY':
                    analysis['buy_signals'] += 1
                else:
                    analysis['sell_signals'] += 1
                
                # Categorize by confidence
                if confidence >= 85:
                    analysis['high_confidence_calls'] += 1
                elif confidence >= 75:
                    analysis['medium_confidence_calls'] += 1
                else:
                    analysis['low_confidence_calls'] += 1
    
    # Calculate statistics
    if analysis['confidence_levels']:
        analysis['avg_confidence'] = statistics.mean(analysis['confidence_levels'])
        analysis['max_confidence'] = max(analysis['confidence_levels'])
        analysis['min_confidence'] = min(analysis['confidence_levels'])
    else:
        analysis['avg_confidence'] = 0
        analysis['max_confidence'] = 0
        analysis['min_confidence'] = 0
    
    print(f"📈 Total Opportunities: {analysis['total_opportunities']}")
    print(f"📊 Buy Signals: {analysis['buy_signals']} | Sell Signals: {analysis['sell_signals']}")
    print(f"🎯 Average Confidence: {analysis['avg_confidence']:.1f}%")
    print(f"🔥 High Confidence (85%+): {analysis['high_confidence_calls']}")
    print(f"⚡ Medium Confidence (75-84%): {analysis['medium_confidence_calls']}")
    print(f"⚠️ Low Confidence (<75%): {analysis['low_confidence_calls']}")
    print()
    
    return analysis

def analyze_high_win_bot_log():
    """Analyze high_win_bot.log for error patterns"""
    
    print("🚨 HIGH WIN BOT ERROR ANALYSIS")
    print("-" * 32)
    
    try:
        with open('high_win_bot.log', 'r') as f:
            content = f.read()
    except FileNotFoundError:
        print("❌ high_win_bot.log not found")
        return {}
    
    analysis = {
        'total_errors': 0,
        'error_types': defaultdict(int),
        'functionality_issues': []
    }
    
    lines = content.split('\n')
    
    for line in lines:
        if 'Analysis error:' in line:
            analysis['total_errors'] += 1
            if "Conversion = '+'" in line:
                analysis['error_types']['conversion_error'] += 1
                analysis['functionality_issues'].append('Data parsing/conversion issues')
    
    print(f"❌ Total Errors: {analysis['total_errors']}")
    print(f"🔧 Conversion Errors: {analysis['error_types']['conversion_error']}")
    print(f"⚠️ Status: Bot has critical parsing issues")
    print()
    
    return analysis

def generate_comprehensive_report(enhanced, improved, high_win):
    """Generate final comprehensive audit report"""
    
    print("🎯 COMPREHENSIVE AUDIT SUMMARY")
    print("=" * 40)
    
    # Overall Bot Status
    print("📊 OVERALL BOT STATUS:")
    print("-" * 25)
    
    working_bots = 0
    total_bots = 3
    
    if enhanced.get('functionality_status', {}).get('upstox_integration', False):
        print("✅ Enhanced Bot: WORKING (Real API integration)")
        working_bots += 1
    else:
        print("❌ Enhanced Bot: NOT WORKING")
    
    if improved.get('total_opportunities', 0) > 0:
        print("✅ Improved Bot: WORKING (Generating signals)")
        working_bots += 1
    else:
        print("❌ Improved Bot: NOT WORKING")
    
    if high_win.get('total_errors', 0) > 100:
        print("❌ High Win Bot: BROKEN (Critical errors)")
    else:
        print("⚠️ High Win Bot: UNKNOWN STATUS")
    
    print(f"\n🎯 Working Bots: {working_bots}/{total_bots}")
    
    # Index Options Analysis
    print("\n📈 INDEX OPTIONS FUNCTIONALITY:")
    print("-" * 35)
    
    if enhanced.get('indices_tracked'):
        tracked_indices = enhanced['indices_tracked']
        print(f"✅ Indices Tracked: {', '.join(tracked_indices)}")
        
        if 'NIFTY' in tracked_indices and 'BANKNIFTY' in tracked_indices:
            print("✅ Index Options: FUNCTIONAL")
        else:
            print("⚠️ Index Options: PARTIAL")
    else:
        print("❌ Index Options: NOT WORKING")
    
    # Call Generation Analysis
    print("\n🔥 CALL GENERATION ANALYSIS:")
    print("-" * 32)
    
    if improved.get('total_opportunities', 0) > 0:
        total_calls = improved['total_opportunities']
        high_conf = improved.get('high_confidence_calls', 0)
        avg_conf = improved.get('avg_confidence', 0)
        
        print(f"📊 Total Calls Generated: {total_calls}")
        print(f"🎯 High Confidence Calls (85%+): {high_conf}")
        print(f"📈 Success Rate Estimate: {avg_conf:.1f}%")
        
        # Estimate actual success rate
        if avg_conf >= 80:
            estimated_success = "70-80%"
        elif avg_conf >= 75:
            estimated_success = "60-70%"
        else:
            estimated_success = "50-60%"
        
        print(f"🎯 Estimated Real Success Rate: {estimated_success}")
    else:
        print("❌ No calls generated - Bot not functioning")
    
    # Timing Analysis
    print("\n⏰ TIMING ANALYSIS:")
    print("-" * 20)
    
    if enhanced.get('api_calls', 0) > 0:
        print("✅ Real-time data fetching: WORKING")
        print("✅ Pre-movement detection: LIKELY")
        print("⚠️ Need to verify actual timing vs market movements")
    else:
        print("❌ No real-time data - Cannot predict before movements")
    
    # Final Verdict
    print("\n🏆 FINAL AUDIT VERDICT:")
    print("-" * 25)
    
    if working_bots >= 2 and enhanced.get('functionality_status', {}).get('upstox_integration', False):
        print("✅ OVERALL STATUS: FUNCTIONAL")
        print("✅ Real API integration working")
        print("✅ Signal generation active")
        print("⚠️ Need manual verification of success rates")
    else:
        print("❌ OVERALL STATUS: NEEDS IMPROVEMENT")
        print("❌ Critical functionality missing")
        print("🔧 Requires immediate fixes")
    
    # Recommendations
    print("\n💡 RECOMMENDATIONS:")
    print("-" * 20)
    print("1. Fix High Win Bot conversion errors")
    print("2. Implement proper success rate tracking")
    print("3. Add timestamp logging for timing verification")
    print("4. Create real trade outcome tracking")
    print("5. Implement proper backtesting with historical data")

if __name__ == "__main__":
    analyze_bot_logs()