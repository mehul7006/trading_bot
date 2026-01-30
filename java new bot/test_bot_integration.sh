#!/bin/bash

echo "🚀 COMPLETE BOT INTEGRATION TEST"
echo "=============================="

# Compile all components
echo "📦 Compiling all components..."

# Core components
javac EnhancedOptionsAccuracySystemV2.java
javac MarketDataManagerV2.java
javac RealTimeAccuracyVerifier.java

# Advanced Call Generator
javac AdvancedCallGenerator_Part1.java
javac AdvancedCallGenerator_Part2.java
javac AdvancedCallGenerator_Part3.java
javac AdvancedCallGenerator_Coordinator.java

# Accuracy Fixes
javac AccuracyFix_Part1_AfternoonAnalysis.java
javac AccuracyFix_Part2_SentimentDetection.java
javac AccuracyFix_Part3_VolumeAnalysis.java
javac AccuracyFix_Part4_TrendConfirmation.java

# Run integration tests
echo -e "\n📋 Running component tests..."

# Test Options System
java EnhancedOptionsAccuracySystemV2

# Test Market Data
java MarketDataManagerV2

# Test Accuracy
java RealTimeAccuracyVerifier

# Test Advanced Call Generator
java AdvancedCallGenerator_Coordinator

echo -e "\n✅ Integration test complete!"