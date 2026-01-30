#!/bin/bash
# Start Random Trading Tier (45-50% accuracy)

echo "🎯 STARTING RANDOM TRADING TIER"
echo "Target Accuracy: 45-50%"
echo "Risk Level: Low"
echo "=" * 50

cd "$(dirname "$0")"

echo "📊 Initializing Random Trading Tier..."
python3 complete_three_tier_accuracy_system.py --tier=random_trading

echo "✅ Random Trading Tier Started"
echo "📈 Expected Performance: 45-50% accuracy"
echo "💰 Suitable for: Conservative trading, learning"