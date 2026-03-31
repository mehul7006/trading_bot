import re
from datetime import datetime, timedelta
from collections import defaultdict

# Parse calls from backtest output
with open(r'C:\Users\bunty\.claude\projects\E--kccb-telegram-bot-trading-bot-master--1-\05046437-e532-4018-922b-4f8a2cdf3391\tool-results\b0duuffd6.txt', encoding='utf-8', errors='ignore') as f:
    text = f.read()

calls = []
current_date = None
lines = text.split('\n')
i = 0
call = {}
while i < len(lines):
    line = lines[i].strip()
    m = re.search(r'DATE: (\d{4}-\d{2}-\d{2})', line)
    if m:
        current_date = m.group(1)
    m = re.search(r'Call #(\d+).*?(\w+)\s+\[(\w+)', line)
    if m and current_date:
        call = {'date': current_date, 'call_no': int(m.group(1)), 'symbol': m.group(2)}
        if 'UP' in line: call['direction'] = 'UP'
        elif 'DOWN' in line: call['direction'] = 'DOWN'
    if call and 'symbol' in call:
        m = re.search(r'@ (\d{2}:\d{2}) IST', line)
        if m: call['time'] = m.group(1)
        m = re.search(r'Entry\s+:\s+([\d,]+\.?\d*)', line)
        if m: call['entry'] = float(m.group(1).replace(',',''))
        m = re.search(r'Target\s+:\s+([\d,]+\.?\d*)\s+\(\+(\d+) pts\)', line)
        if m:
            call['target_price'] = float(m.group(1).replace(',',''))
            call['target_pts'] = float(m.group(2))
        m = re.search(r'Stop Loss\s+:\s+([\d,]+\.?\d*)\s+\(-(\d+) pts\)', line)
        if m:
            call['sl_price'] = float(m.group(1).replace(',',''))
            call['sl_pts'] = float(m.group(2))
        m = re.search(r'Confidence\s+:\s+([\d.]+)%', line)
        if m: call['confidence'] = float(m.group(1))
        m = re.search(r'Result\s+:.*?(WIN|PARTIAL WIN|LOSS)', line)
        if m:
            call['outcome'] = m.group(1)
            m2 = re.search(r'P&L:\s+([+-][\d.]+) pts', line)
            if m2: call['pnl'] = float(m2.group(1))
            if 'time' in call and 'entry' in call:
                calls.append(call.copy())
            call = {}
    i += 1

print(f'Parsed {len(calls)} calls')

# Resolution time estimate per outcome
# Backtest uses 24-candle (120 min) lookahead
# WIN = typically fast (avg ~30 min based on strong moves)
# PARTIAL WIN = moderate (~60 min)
# LOSS = stop hit later (~90 min)
def resolution_mins(outcome):
    if outcome == 'WIN': return 30
    elif outcome == 'PARTIAL WIN': return 60
    else: return 90

# ── Simulate live bot notification rules ─────────────────────────────────────
# Rule 1: Max 10 calls/day (todayCallsGenerated >= 10 → skip)
# Rule 2: Active signal per symbol blocks new signal until resolved
# Rule 3: 3-min cooldown per symbol after each alert
# Rule 4: Signal passes gates (confidence >= 65, min points) — backtest pre-filters at 85, all pass

results = []
daily_calls = {}       # date -> count
active_signal = {}     # symbol -> resolve datetime
last_alert_time = {}   # symbol -> last alert datetime

for c in calls:
    signal_time = datetime.strptime(f"{c['date']} {c['time']}", '%Y-%m-%d %H:%M')
    date = c['date']
    sym = c['symbol']

    if date not in daily_calls:
        daily_calls[date] = 0
        # Reset active signal and cooldown at day start (live bot resets daily state)
        active_signal = {}
        last_alert_time = {}

    blocked_reason = None

    # Rule 1: Daily cap
    if daily_calls[date] >= 10:
        blocked_reason = 'Daily cap (10/day) reached'

    # Rule 2: Active signal blocking for this symbol
    elif sym in active_signal and signal_time < active_signal[sym]:
        wait = active_signal[sym].strftime('%H:%M')
        blocked_reason = f'Active {sym} signal still open (est. resolves ~{wait})'

    # Rule 3: 3-min cooldown
    elif sym in last_alert_time:
        elapsed_secs = (signal_time - last_alert_time[sym]).total_seconds()
        if elapsed_secs < 180:
            blocked_reason = f'3-min cooldown ({int(elapsed_secs//60)}m {int(elapsed_secs%60)}s elapsed)'

    if blocked_reason:
        results.append({**c, 'notif': 'NO', 'reason': blocked_reason})
    else:
        daily_calls[date] += 1
        last_alert_time[sym] = signal_time
        res_time = signal_time + timedelta(minutes=resolution_mins(c['outcome']))
        active_signal[sym] = res_time
        results.append({**c, 'notif': 'YES', 'reason': 'Notification fired'})

# ── Print full call-by-call report ───────────────────────────────────────────
print()
print('=' * 100)
print('  TELEGRAM NOTIFICATION CHECK — All 108 Backtest Calls vs Live Bot Rules')
print('=' * 100)

fired = sum(1 for r in results if r['notif'] == 'YES')
blocked_list = [r for r in results if r['notif'] == 'NO']

# Per-day grouped output
day_results = defaultdict(list)
for r in results:
    day_results[r['date']].append(r)

call_num = 0
for date in sorted(day_results):
    day = day_results[date]
    day_fired = sum(1 for r in day if r['notif'] == 'YES')
    day_blocked = sum(1 for r in day if r['notif'] == 'NO')
    print()
    print(f'  DATE: {date}  [{len(day)} calls | ✅ {day_fired} notified | ❌ {day_blocked} blocked]')
    print(f'  {"-"*95}')
    for r in day:
        call_num += 1
        notif_icon = '✅ YES' if r['notif'] == 'YES' else '❌ NO '
        print(f'  #{call_num:>3} | {r["time"]} | {r["symbol"]:<10} | {r["direction"]:<5} | '
              f'Entry:{r["entry"]:>10,.1f} | Tgt:+{r["target_pts"]:.0f}pts | SL:-{r["sl_pts"]:.0f}pts | '
              f'{r["outcome"]:<12} | {notif_icon} | {r["reason"]}')

# ── Final Summary ─────────────────────────────────────────────────────────────
print()
print('=' * 100)
print('  FINAL SUMMARY')
print('=' * 100)
print(f'  Total Backtest Calls : 108')
print(f'  Would Notify (YES)   : {fired}')
print(f'  Would Block  (NO)    : {108 - fired}')
print()
print('  BLOCK REASONS BREAKDOWN:')
reasons = defaultdict(int)
for r in blocked_list:
    if 'Daily cap' in r['reason']:
        reasons['Daily cap (max 10/day)'] += 1
    elif 'Active' in r['reason']:
        reasons['Active signal blocking (symbol busy)'] += 1
    elif 'cooldown' in r['reason']:
        reasons['3-min cooldown between same-symbol alerts'] += 1
    else:
        reasons[r['reason']] += 1
for k, v in sorted(reasons.items(), key=lambda x: -x[1]):
    print(f'    {k}: {v} calls')

print()
print('  PER-DAY BREAKDOWN:')
print(f'  {"Date":<12} {"Total":<7} {"Notified":<10} {"Blocked":<9} Blocked Reasons')
print(f'  {"-"*80}')
for date in sorted(day_results):
    day = day_results[date]
    d_f = sum(1 for r in day if r['notif'] == 'YES')
    d_b = [r for r in day if r['notif'] == 'NO']
    block_types = set()
    for r in d_b:
        if 'Daily cap' in r['reason']: block_types.add('cap')
        elif 'Active' in r['reason']: block_types.add('active-signal')
        elif 'cooldown' in r['reason']: block_types.add('cooldown')
    print(f'  {date:<12} {len(day):<7} {d_f:<10} {len(d_b):<9} {", ".join(block_types) if block_types else "-"}')

print()
print('  PER-SYMBOL BREAKDOWN:')
print(f'  {"Symbol":<12} {"Total":<8} {"Notified":<10} {"Blocked":<9} Block%')
print(f'  {"-"*60}')
for sym in ['NIFTY50', 'BANKNIFTY', 'SENSEX']:
    sym_calls = [r for r in results if r['symbol'] == sym]
    s_f = sum(1 for r in sym_calls if r['notif'] == 'YES')
    s_b = len(sym_calls) - s_f
    pct = s_b / len(sym_calls) * 100 if sym_calls else 0
    print(f'  {sym:<12} {len(sym_calls):<8} {s_f:<10} {s_b:<9} {pct:.0f}%')

print()
print('  NOTE: Additional live-bot checks NOT simulated here:')
print('    • MTF conflict (5-min vs 1-min disagreement) → would block some more')
print('    • Stale data check (>10 min old) → depends on Upstox API latency')
print('    • Expired Upstox token → ALL calls blocked currently (fix token first!)')
print('=' * 100)
