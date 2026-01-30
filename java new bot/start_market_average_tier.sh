#!/bin/bash
# Start Market Average Tier (50-55% accuracy)

echo "🎯 STARTING MARKET AVERAGE TIER"
echo "Target Accuracy: 50-55%"
echo "Risk Level: Medium"
echo "=" * 50

cd "$(dirname "$0")"

echo "📊 Initializing Market Average Tier..."
python3 complete_three_tier_accuracy_system.py --tier=market_average

echo "✅ Market Average Tier Started"
echo "📈 Expected Performance: 50-55% accuracy"
echo "💰 Suitable for: Balanced trading, steady growth"