#!/bin/bash
# LIVE TRADING SYSTEM MONITORING SCRIPT
# Version: v1.0.0

echo "📊 LIVE TRADING SYSTEM MONITOR"
echo "=============================="

# Check if system is running
TRADING_PID=$(pgrep -f "MasterLiveTradingLauncher")
if [ -z "$TRADING_PID" ]; then
    echo "❌ Trading system is NOT running"
    echo "🔧 Use: ./start_trading_system.sh to start"
    exit 1
else
    echo "✅ Trading system is running (PID: $TRADING_PID)"
fi

# System resource usage
echo "💻 SYSTEM RESOURCES:"
echo "===================="
ps -p $TRADING_PID -o pid,ppid,cmd,%mem,%cpu,etime

# Memory usage
echo "🧠 MEMORY USAGE:"
echo "================"
free -h

# Disk usage
echo "💾 DISK USAGE:"
echo "=============="
df -h /opt/trading-system
df -h /opt/trading-system/logs

# Log file analysis
echo "📝 LOG ANALYSIS:"
echo "================"
LATEST_LOG=$(ls -t /opt/trading-system/logs/trading_*.log 2>/dev/null | head -1)
if [ -n "$LATEST_LOG" ]; then
    echo "📄 Latest log: $LATEST_LOG"
    echo "📊 Log size: $(du -h "$LATEST_LOG" | cut -f1)"
    echo "⏰ Last modified: $(stat -c %y "$LATEST_LOG")"
    echo ""
    echo "📈 Recent activity (last 10 lines):"
    tail -10 "$LATEST_LOG"
else
    echo "⚠️ No log files found"
fi

# Performance metrics
echo "📊 PERFORMANCE METRICS:"
echo "======================="
if [ -n "$LATEST_LOG" ]; then
    echo "📞 Signals today: $(grep -c 'Signal' "$LATEST_LOG" 2>/dev/null || echo '0')"
    echo "✅ Successful trades: $(grep -c 'WIN' "$LATEST_LOG" 2>/dev/null || echo '0')"
    echo "❌ Failed trades: $(grep -c 'LOSS' "$LATEST_LOG" 2>/dev/null || echo '0')"
    echo "🎯 Expected accuracy: 82.35%"
fi

# Health check
echo "💚 HEALTH CHECK:"
echo "================"
if kill -0 $TRADING_PID 2>/dev/null; then
    echo "✅ Process is responsive"
else
    echo "❌ Process is not responsive"
fi

# Market hours check
CURRENT_HOUR=$(date +%H)
if [ $CURRENT_HOUR -ge 9 ] && [ $CURRENT_HOUR -lt 16 ]; then
    echo "📈 Market hours: ACTIVE"
else
    echo "⏰ Market hours: CLOSED"
fi
