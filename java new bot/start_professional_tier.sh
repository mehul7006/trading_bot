#!/bin/bash
# Start Professional Traders Tier (60-65% accuracy)

echo "🎯 STARTING PROFESSIONAL TRADERS TIER"
echo "Target Accuracy: 60-65%"
echo "Risk Level: High"
echo "=" * 50

cd "$(dirname "$0")"

echo "📊 Initializing Professional Traders Tier..."
python3 complete_three_tier_accuracy_system.py --tier=professional_traders

echo "✅ Professional Traders Tier Started"
echo "📈 Expected Performance: 60-65% accuracy"
echo "💰 Suitable for: Professional trading, higher returns"