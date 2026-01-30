#!/bin/bash

echo "🗂️  ORGANIZING BOT FILES"
echo "======================="

# Create main directory structure
mkdir -p src/main/java/com/bot/{core,options,market,accuracy,integration,utils}
mkdir -p src/main/java/com/bot/core/{engine,data}
mkdir -p src/main/java/com/bot/options/{analysis,strategy}
mkdir -p src/main/java/com/bot/market/{data,analysis}
mkdir -p src/main/java/com/bot/accuracy/{verification,fixes}
mkdir -p src/main/java/com/bot/integration/{telegram,coordinator}
mkdir -p config
mkdir -p logs
mkdir -p docs

# Move core files
mv EnhancedOptionsAccuracySystemV2.java src/main/java/com/bot/core/engine/
mv MarketDataManagerV2.java src/main/java/com/bot/core/data/
mv RealTimeAccuracyVerifier.java src/main/java/com/bot/accuracy/verification/

# Move options related files
mv OptionsSignal.java src/main/java/com/bot/options/
mv EnhancedOptionsBot.java src/main/java/com/bot/options/
mv OptionsGreeksCalculator.java src/main/java/com/bot/options/analysis/
mv ImpliedVolatilityAnalyzer.java src/main/java/com/bot/options/analysis/

# Move advanced call generator files
mv AdvancedCallGenerator_Part1.java src/main/java/com/bot/core/engine/
mv AdvancedCallGenerator_Part2.java src/main/java/com/bot/core/engine/
mv AdvancedCallGenerator_Part3.java src/main/java/com/bot/core/engine/
mv AdvancedCallGenerator_Coordinator.java src/main/java/com/bot/integration/coordinator/

# Move accuracy fix files
mv AccuracyFix_Part1_AfternoonAnalysis.java src/main/java/com/bot/accuracy/fixes/
mv AccuracyFix_Part2_SentimentDetection.java src/main/java/com/bot/accuracy/fixes/
mv AccuracyFix_Part3_VolumeAnalysis.java src/main/java/com/bot/accuracy/fixes/
mv AccuracyFix_Part4_TrendConfirmation.java src/main/java/com/bot/accuracy/fixes/

# Move market data files
mv RealMarketDataProvider.java src/main/java/com/bot/market/data/
mv RealDataProvider.java src/main/java/com/bot/market/data/

# Move integration files
mv AdvancedCallIntegration.java src/main/java/com/bot/integration/
mv CompleteSystemIntegration.java src/main/java/com/bot/integration/

# Move documentation files
mv *.md docs/

# Create new package-info files
echo "/** Core bot engine components */" > src/main/java/com/bot/core/package-info.java
echo "/** Options trading components */" > src/main/java/com/bot/options/package-info.java
echo "/** Market data and analysis */" > src/main/java/com/bot/market/package-info.java
echo "/** Accuracy verification and fixes */" > src/main/java/com/bot/accuracy/package-info.java
echo "/** Integration components */" > src/main/java/com/bot/integration/package-info.java
echo "/** Utility classes */" > src/main/java/com/bot/utils/package-info.java

# Create README files for each major component
cat > src/main/java/com/bot/core/README.md << 'EOF'
# Core Bot Components
Core components for the trading bot system.

## Components:
- Engine: Main trading engine
- Data: Core data management
EOF

cat > src/main/java/com/bot/options/README.md << 'EOF'
# Options Trading Components
Options analysis and trading components.

## Components:
- Analysis: Greeks and IV analysis
- Strategy: Options trading strategies
EOF

cat > src/main/java/com/bot/market/README.md << 'EOF'
# Market Components
Market data and analysis components.

## Components:
- Data: Real-time market data
- Analysis: Market analysis tools
EOF

cat > src/main/java/com/bot/accuracy/README.md << 'EOF'
# Accuracy Components
Accuracy verification and enhancement components.

## Components:
- Verification: Accuracy testing
- Fixes: Accuracy improvements
EOF

cat > src/main/java/com/bot/integration/README.md << 'EOF'
# Integration Components
System integration components.

## Components:
- Telegram: Bot commands
- Coordinator: System coordination
EOF

# Create a new build script
cat > build.sh << 'EOF'
#!/bin/bash

echo "🔨 Building Enhanced Trading Bot"
echo "=============================="

# Set up classpath
CLASSPATH="src/main/java"

# Compile all Java files
find src/main/java -name "*.java" -print | xargs javac -cp "$CLASSPATH"

echo "✅ Build complete!"
EOF

chmod +x build.sh

echo "✅ File organization complete!"
echo "📁 New structure created and files organized"