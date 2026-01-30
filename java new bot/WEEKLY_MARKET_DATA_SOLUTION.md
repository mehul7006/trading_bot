# Weekly Market Data Fetcher - Complete Solution

## Overview
This solution provides a comprehensive system to fetch last week's market movement data from official sources and analyze market trends. The solution includes both Java and Python components for data collection and analysis.

## Features

### ✅ Data Sources
- **Yahoo Finance CSV API** - Primary reliable source
- **NSE India API** - Official Indian stock exchange (with fallback)
- **BSE India API** - Official Bombay stock exchange (with fallback)

### ✅ Supported Indices
- **NIFTY 50** (^NSEI)
- **SENSEX** (^BSESN)
- **BANK NIFTY** (^NSEBANK)
- **FIN NIFTY** (NIFTY_FIN_SERVICE.NS)

### ✅ Analysis Features
- Weekly performance comparison
- Volatility analysis
- Correlation analysis between indices
- Trend classification (BULLISH/BEARISH/SIDEWAYS)
- Market sentiment analysis
- Predictive insights for next week

## Files Created

### Java Components
1. **SimpleWeeklyMarketDataFetcher.java** - Main data fetcher (no external dependencies)
2. **WeeklyMarketDataFetcher.java** - Advanced fetcher with JSON support
3. **run_simple_weekly_market_fetcher.sh** - Easy execution script

### Python Analysis
1. **analyze_weekly_market_trends.py** - Advanced analysis and visualization
2. **run_weekly_market_data_fetcher.sh** - Complete pipeline script

### Output Files
- **market_data/weekly_market_data_YYYY-MM-DD.csv** - Raw market data
- **market_data/weekly_market_analysis_YYYY-MM-DD.csv** - Weekly analysis
- **analysis_reports/weekly_market_report_TIMESTAMP.txt** - Detailed reports
- **charts/weekly_market_analysis_TIMESTAMP.png** - Visual charts

## Quick Start

### Option 1: Simple Java Version (Recommended)
```bash
cd "java new bot"
./run_simple_weekly_market_fetcher.sh
```

### Option 2: Advanced Analysis with Python
```bash
cd "java new bot"
./run_simple_weekly_market_fetcher.sh
python3 analyze_weekly_market_trends.py
```

### Option 3: Manual Java Execution
```bash
cd "java new bot"
javac src/main/java/com/trading/bot/market/SimpleWeeklyMarketDataFetcher.java
java -cp src/main/java com.trading.bot.market.SimpleWeeklyMarketDataFetcher
```

## Sample Output

### Console Output
```
SIMPLE WEEKLY MARKET DATA FETCHER - YAHOO FINANCE
============================================================
Fetching last week's market movement data from Yahoo Finance
No external dependencies required!
============================================================
Fetching market data for week: 2025-10-20 to 2025-10-24
================================================================================

Fetching data for NIFTY (^NSEI)...
  2025-10-20: 24500.00 -> 24580.00 (0.33%)
  2025-10-21: 24580.00 -> 24620.00 (0.16%)
  ...

WEEKLY ANALYSIS: NIFTY (2025-10-20 to 2025-10-24)
------------------------------------------------------------
Week Open:     24500.00
Week Close:    24680.00
Week Change:   180.00 (0.73%)
Movement:      BULLISH
Volatility:    2.45%
Trading Days:  5
```

### CSV Output (market_data/weekly_market_data_YYYY-MM-DD.csv)
```csv
Symbol,Date,Open,High,Low,Close,Volume,Change,Change%,Source
NIFTY,2025-10-20,24500.00,24550.00,24480.00,24580.00,0,80.00,0.33,YAHOO
NIFTY,2025-10-21,24580.00,24650.00,24560.00,24620.00,0,40.00,0.16,YAHOO
```

### Analysis Output (market_data/weekly_market_analysis_YYYY-MM-DD.csv)
```csv
Symbol,WeekStart,WeekEnd,WeekOpen,WeekClose,WeekHigh,WeekLow,WeekChange,WeekChange%,TotalVolume,TradingDays,Movement,Volatility%
NIFTY,2025-10-20,2025-10-24,24500.00,24680.00,24720.00,24480.00,180.00,0.73,0,5,BULLISH,0.98
```

## Technical Details

### Data Collection Process
1. **Week Calculation**: Automatically calculates last Monday to Friday
2. **API Calls**: Makes HTTP requests to Yahoo Finance CSV API
3. **Error Handling**: Includes retry logic and fallback sources
4. **Data Validation**: Validates received data for completeness
5. **File Output**: Saves both raw data and analysis to CSV files

### Analysis Metrics
- **Performance**: Week-over-week change percentage
- **Volatility**: (Week High - Week Low) / Week Open * 100
- **Movement Classification**:
  - STRONG_BULLISH: > +2.0%
  - BULLISH: +0.5% to +2.0%
  - SIDEWAYS: -0.5% to +0.5%
  - BEARISH: -2.0% to -0.5%
  - STRONG_BEARISH: < -2.0%

### Market Sentiment Calculation
- **BULLISH 🐂**: Average change > +1%
- **BEARISH 🐻**: Average change < -1%
- **NEUTRAL ➡️**: Average change between -1% and +1%

## Troubleshooting

### Common Issues

1. **Compilation Errors**
   - Ensure Java 11+ is installed
   - Check classpath settings
   - Verify file permissions

2. **Network Issues**
   - Check internet connectivity
   - Verify firewall settings
   - Yahoo Finance may have rate limits

3. **Empty Data**
   - Market may have been closed
   - Weekend/holiday periods return no data
   - Symbol mappings may need updates

### Solutions

```bash
# Check Java version
java -version

# Manual compilation
javac -cp src/main/java src/main/java/com/trading/bot/market/SimpleWeeklyMarketDataFetcher.java

# Check network connectivity
curl -I "https://finance.yahoo.com"

# View logs for debugging
tail -f logs/simple_weekly_market_fetch_*.log
```

## API Rate Limits

- **Yahoo Finance**: ~2000 requests per hour
- **Delay**: 2 seconds between requests (implemented)
- **Best Practice**: Run once per day for weekly data

## Data Sources Reliability

1. **Yahoo Finance CSV API** ⭐⭐⭐⭐⭐
   - Most reliable for historical data
   - Global coverage
   - CSV format (easy parsing)

2. **NSE Official API** ⭐⭐⭐⭐
   - Authoritative for Indian markets
   - Requires proper headers
   - JSON format

3. **BSE Official API** ⭐⭐⭐
   - Limited historical data
   - Requires authentication for some endpoints

## Future Enhancements

### Planned Features
- [ ] Real-time data integration
- [ ] More international indices
- [ ] Technical indicators calculation
- [ ] Email/SMS alerts
- [ ] Database storage
- [ ] Web dashboard
- [ ] Mobile app integration

### Advanced Analysis
- [ ] Machine learning predictions
- [ ] Seasonal pattern analysis
- [ ] Sector rotation analysis
- [ ] Risk metrics calculation
- [ ] Portfolio optimization

## Dependencies

### Java Version (No External Dependencies)
- Java 11+ (built-in HTTP client)
- No external JAR files required

### Python Version (Optional)
```bash
pip install pandas numpy matplotlib seaborn
```

## License
This solution is provided for educational and research purposes.

## Support
For issues or questions:
1. Check the troubleshooting section
2. Review log files in `logs/` directory
3. Verify market hours and data availability
4. Test with manual API calls

---

**Last Updated**: November 2025
**Version**: 1.0.0
**Compatibility**: Java 11+, Python 3.7+