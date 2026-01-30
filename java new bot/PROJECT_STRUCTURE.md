# Enhanced Trading Bot - Project Structure
=====================================

## Project Organization
The project is organized into the following main components:

### 1. Core (`src/main/java/com/bot/core/`)
- **Engine**: Core trading engine components
  - EnhancedOptionsAccuracySystemV2.java
  - AdvancedCallGenerator_Part1.java
  - AdvancedCallGenerator_Part2.java
  - AdvancedCallGenerator_Part3.java
- **Data**: Core data management
  - MarketDataManagerV2.java

### 2. Options (`src/main/java/com/bot/options/`)
- **Analysis**: Options analysis components
  - OptionsGreeksCalculator.java
  - ImpliedVolatilityAnalyzer.java
- **Strategy**: Options trading strategies
  - OptionsSignal.java
  - EnhancedOptionsBot.java

### 3. Market (`src/main/java/com/bot/market/`)
- **Data**: Market data components
  - RealMarketDataProvider.java
  - RealDataProvider.java
- **Analysis**: Market analysis tools

### 4. Accuracy (`src/main/java/com/bot/accuracy/`)
- **Verification**: Accuracy testing components
  - RealTimeAccuracyVerifier.java
- **Fixes**: Accuracy enhancement components
  - AccuracyFix_Part1_AfternoonAnalysis.java
  - AccuracyFix_Part2_SentimentDetection.java
  - AccuracyFix_Part3_VolumeAnalysis.java
  - AccuracyFix_Part4_TrendConfirmation.java

### 5. Integration (`src/main/java/com/bot/integration/`)
- **Coordinator**: System coordination
  - AdvancedCallGenerator_Coordinator.java
- **Integration**: System integration
  - AdvancedCallIntegration.java
  - CompleteSystemIntegration.java

## Building the Project
1. Use Maven to build the project:
```bash
mvn clean package
```

2. Or use the provided build script:
```bash
./build.sh
```

## Running the Bot
1. Start the market data manager:
```bash
java -cp target/enhanced-trading-bot-2.0.0-jar-with-dependencies.jar com.bot.core.data.MarketDataManagerV2
```

2. Start the main trading system:
```bash
java -cp target/enhanced-trading-bot-2.0.0-jar-with-dependencies.jar com.bot.core.engine.EnhancedOptionsAccuracySystemV2
```

## Key Features
1. Enhanced Options Accuracy
   - 85% minimum confidence threshold
   - Multi-timeframe analysis
   - Volume profile validation

2. Real Market Data
   - Real-time data integration
   - No mock data used
   - Live market updates

3. Advanced Analysis
   - Options Greeks calculation
   - Implied Volatility analysis
   - Support/Resistance detection

4. Risk Management
   - Position sizing
   - Dynamic stop-loss
   - Target management

## Configuration
- Configuration files are in the `config/` directory
- Logs are stored in the `logs/` directory
- Documentation is in the `docs/` directory

## Development Guidelines
1. Follow package structure
2. Update documentation
3. Run tests before commits
4. Maintain code quality

## Accuracy Requirements
- Minimum confidence: 85%
- Volume confirmation: 150%
- Real data verification
- Multi-factor validation

## Support
For any issues:
1. Check logs in `logs/` directory
2. Verify configurations
3. Test data connections
4. Review documentation