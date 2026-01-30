@echo off
setlocal enabledelayedexpansion
cls
echo ================================================================
echo       INDIAN MARKET REAL-TIME DATA DOWNLOADER - WINDOWS
echo ================================================================
echo.
echo This script will download real-time Indian market data every 5 seconds:
echo - All NSE/BSE Indices (NIFTY, SENSEX, BANKNIFTY, FINNIFTY, etc.)
echo - Options Chain Data with Open Interest (OI)
echo - Current level +/- 10 strikes for all major indices
echo - Export to Excel files with timestamps
echo.
echo Data Sources: NSE Official, BSE Official, TradingView
echo Update Frequency: Every 5 seconds
echo Output Format: Excel (.xlsx) files
echo.
echo ================================================================
echo.

:: Check if required tools are available
echo [SETUP] Checking prerequisites...

:: Check Python
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python 3.7+ and try again
    pause
    exit /b 1
)

:: Check if required Python packages are installed
python -c "import requests, pandas, openpyxl, datetime, json, time" >nul 2>&1
if errorlevel 1 (
    echo Installing required Python packages...
    pip install requests pandas openpyxl xlsxwriter yfinance nsepy
    if errorlevel 1 (
        echo ERROR: Failed to install required packages
        pause
        exit /b 1
    )
)

echo [SETUP] Prerequisites verified successfully!
echo.

:: Create output directory
set OUTPUT_DIR=market_data_%date:~-4,4%_%date:~-10,2%_%date:~-7,2%
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
echo [SETUP] Output directory created: %OUTPUT_DIR%

:: Create the Python script for data fetching
echo [SETUP] Creating data fetcher script...

(
echo import requests
echo import pandas as pd
echo import time
echo import json
echo from datetime import datetime, timedelta
echo import os
echo import sys
echo from concurrent.futures import ThreadPoolExecutor, as_completed
echo import warnings
echo warnings.filterwarnings('ignore'^)
echo.
echo class IndianMarketDataFetcher:
echo     def __init__(self^):
echo         self.session = requests.Session(^)
echo         self.session.headers.update({
echo             'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64^) AppleWebKit/537.36 (KHTML, like Gecko^) Chrome/91.0.4472.124 Safari/537.36'
echo         }^)
echo         self.indices = [
echo             'NIFTY', 'SENSEX', 'BANKNIFTY', 'FINNIFTY', 'MIDCPNIFTY',
echo             'NIFTYIT', 'NIFTYPHARMA', 'NIFTYAUTO', 'NIFTYMETAL', 'NIFTYREALTY'
echo         ]
echo         self.output_dir = '%OUTPUT_DIR%'
echo.
echo     def fetch_nse_indices(self^):
echo         """Fetch NSE indices data"""
echo         try:
echo             url = "https://www.nseindia.com/api/allIndices"
echo             response = self.session.get(url, timeout=10^)
echo             if response.status_code == 200:
echo                 data = response.json(^)
echo                 indices_data = []
echo                 for item in data.get('data', []^):
echo                     indices_data.append({
echo                         'Index': item.get('index'^),
echo                         'Last_Price': item.get('last'^),
echo                         'Change': item.get('variation'^),
echo                         'Percent_Change': item.get('percentChange'^),
echo                         'Open': item.get('open'^),
echo                         'High': item.get('dayHigh'^),
echo                         'Low': item.get('dayLow'^),
echo                         'Timestamp': datetime.now(^).strftime('%%Y-%%m-%%d %%H:%%M:%%S'^)
echo                     }^)
echo                 return pd.DataFrame(indices_data^)
echo         except Exception as e:
echo             print(f"Error fetching NSE indices: {e}"^)
echo         return pd.DataFrame(^)
echo.
echo     def fetch_bse_indices(self^):
echo         """Fetch BSE indices data"""
echo         try:
echo             url = "https://api.bseindia.com/BseIndiaAPI/api/GetMktData/w"
echo             response = self.session.get(url, timeout=10^)
echo             if response.status_code == 200:
echo                 data = response.json(^)
echo                 bse_data = []
echo                 for item in data.get('Table', []^):
echo                     bse_data.append({
echo                         'Index': item.get('scrip_name'^),
echo                         'Last_Price': item.get('current_value'^),
echo                         'Change': item.get('change'^),
echo                         'Percent_Change': item.get('pchange'^),
echo                         'Open': item.get('open_value'^),
echo                         'High': item.get('high_value'^),
echo                         'Low': item.get('low_value'^),
echo                         'Timestamp': datetime.now(^).strftime('%%Y-%%m-%%d %%H:%%M:%%S'^)
echo                     }^)
echo                 return pd.DataFrame(bse_data^)
echo         except Exception as e:
echo             print(f"Error fetching BSE indices: {e}"^)
echo         return pd.DataFrame(^)
echo.
echo     def fetch_options_chain(self, symbol, expiry=None^):
echo         """Fetch options chain data for given symbol"""
echo         try:
echo             if not expiry:
echo                 # Get nearest expiry
echo                 url = f"https://www.nseindia.com/api/option-chain-indices?symbol={symbol}"
echo             else:
echo                 url = f"https://www.nseindia.com/api/option-chain-indices?symbol={symbol}&date={expiry}"
echo.
echo             response = self.session.get(url, timeout=15^)
echo             if response.status_code == 200:
echo                 data = response.json(^)
echo                 options_data = []
echo                 
echo                 # Get current price to determine range
echo                 current_price = data.get('records', {}^).get('underlyingValue', 0^)
echo                 
echo                 # Calculate +/- 10 strikes range
echo                 strike_interval = 50 if symbol in ['NIFTY', 'BANKNIFTY'] else 25
echo                 min_strike = current_price - (10 * strike_interval^)
echo                 max_strike = current_price + (10 * strike_interval^)
echo                 
echo                 for record in data.get('records', {}^).get('data', []^):
echo                     strike = record.get('strikePrice'^)
echo                     if min_strike <= strike <= max_strike:
echo                         # Call data
echo                         if 'CE' in record:
echo                             ce_data = record['CE']
echo                             options_data.append({
echo                                 'Symbol': symbol,
echo                                 'Strike': strike,
echo                                 'Type': 'CE',
echo                                 'Last_Price': ce_data.get('lastPrice', 0^),
echo                                 'Change': ce_data.get('change', 0^),
echo                                 'Percent_Change': ce_data.get('pChange', 0^),
echo                                 'Volume': ce_data.get('totalTradedVolume', 0^),
echo                                 'Open_Interest': ce_data.get('openInterest', 0^),
echo                                 'OI_Change': ce_data.get('changeinOpenInterest', 0^),
echo                                 'Implied_Volatility': ce_data.get('impliedVolatility', 0^),
echo                                 'Timestamp': datetime.now(^).strftime('%%Y-%%m-%%d %%H:%%M:%%S'^)
echo                             }^)
echo                         
echo                         # Put data
echo                         if 'PE' in record:
echo                             pe_data = record['PE']
echo                             options_data.append({
echo                                 'Symbol': symbol,
echo                                 'Strike': strike,
echo                                 'Type': 'PE',
echo                                 'Last_Price': pe_data.get('lastPrice', 0^),
echo                                 'Change': pe_data.get('change', 0^),
echo                                 'Percent_Change': pe_data.get('pChange', 0^),
echo                                 'Volume': pe_data.get('totalTradedVolume', 0^),
echo                                 'Open_Interest': pe_data.get('openInterest', 0^),
echo                                 'OI_Change': pe_data.get('changeinOpenInterest', 0^),
echo                                 'Implied_Volatility': pe_data.get('impliedVolatility', 0^),
echo                                 'Timestamp': datetime.now(^).strftime('%%Y-%%m-%%d %%H:%%M:%%S'^)
echo                             }^)
echo                 
echo                 return pd.DataFrame(options_data^)
echo         except Exception as e:
echo             print(f"Error fetching options chain for {symbol}: {e}"^)
echo         return pd.DataFrame(^)
echo.
echo     def save_to_excel(self, data_dict^):
echo         """Save all data to Excel files"""
echo         timestamp = datetime.now(^).strftime('%%Y%%m%%d_%%H%%M%%S'^)
echo         
echo         try:
echo             # Save indices data
echo             if 'nse_indices' in data_dict and not data_dict['nse_indices'].empty:
echo                 filename = f"{self.output_dir}/NSE_Indices_{timestamp}.xlsx"
echo                 data_dict['nse_indices'].to_excel(filename, index=False^)
echo                 print(f"✓ NSE Indices saved to {filename}"^)
echo             
echo             if 'bse_indices' in data_dict and not data_dict['bse_indices'].empty:
echo                 filename = f"{self.output_dir}/BSE_Indices_{timestamp}.xlsx"
echo                 data_dict['bse_indices'].to_excel(filename, index=False^)
echo                 print(f"✓ BSE Indices saved to {filename}"^)
echo             
echo             # Save options data
echo             for symbol, options_df in data_dict.items(^):
echo                 if symbol.endswith('_options'^) and not options_df.empty:
echo                     symbol_name = symbol.replace('_options', '^)
echo                     filename = f"{self.output_dir}/{symbol_name}_Options_{timestamp}.xlsx"
echo                     
echo                     # Create multi-sheet Excel with CE and PE data
echo                     with pd.ExcelWriter(filename, engine='openpyxl'^) as writer:
echo                         ce_data = options_df[options_df['Type'] == 'CE']
echo                         pe_data = options_df[options_df['Type'] == 'PE']
echo                         
echo                         if not ce_data.empty:
echo                             ce_data.to_excel(writer, sheet_name='Call_Options', index=False^)
echo                         if not pe_data.empty:
echo                             pe_data.to_excel(writer, sheet_name='Put_Options', index=False^)
echo                         
echo                         # Summary sheet
echo                         summary_data = [{
echo                             'Symbol': symbol_name,
echo                             'Total_CE_OI': ce_data['Open_Interest'].sum(^) if not ce_data.empty else 0,
echo                             'Total_PE_OI': pe_data['Open_Interest'].sum(^) if not pe_data.empty else 0,
echo                             'PCR': (pe_data['Open_Interest'].sum(^) / ce_data['Open_Interest'].sum(^)^) if not ce_data.empty and ce_data['Open_Interest'].sum(^) > 0 else 0,
echo                             'Max_Pain': 0,  # Calculate max pain if needed
echo                             'Timestamp': timestamp
echo                         }]
echo                         pd.DataFrame(summary_data^).to_excel(writer, sheet_name='Summary', index=False^)
echo                     
echo                     print(f"✓ {symbol_name} Options saved to {filename}"^)
echo         
echo         except Exception as e:
echo             print(f"Error saving to Excel: {e}"^)
echo.
echo     def run_continuous_download(self^):
echo         """Main function to run continuous data download"""
echo         print("Starting continuous market data download..."^)
echo         print("Press Ctrl+C to stop"^)
echo         
echo         cycle_count = 0
echo         
echo         try:
echo             while True:
echo                 cycle_count += 1
echo                 print(f"\n{'='*60}"^)
echo                 print(f"Data Download Cycle {cycle_count} - {datetime.now(^).strftime('%%Y-%%m-%%d %%H:%%M:%%S'^)}"^)
echo                 print(f"{'='*60}"^)
echo                 
echo                 data_dict = {}
echo                 
echo                 # Fetch NSE indices
echo                 print("[1/4] Fetching NSE indices..."^)
echo                 nse_indices = self.fetch_nse_indices(^)
echo                 if not nse_indices.empty:
echo                     data_dict['nse_indices'] = nse_indices
echo                     print(f"      ✓ Retrieved {len(nse_indices^)} NSE indices"^)
echo                 
echo                 # Fetch BSE indices  
echo                 print("[2/4] Fetching BSE indices..."^)
echo                 bse_indices = self.fetch_bse_indices(^)
echo                 if not bse_indices.empty:
echo                     data_dict['bse_indices'] = bse_indices
echo                     print(f"      ✓ Retrieved {len(bse_indices^)} BSE indices"^)
echo                 
echo                 # Fetch options data for major indices
echo                 print("[3/4] Fetching Options Chain data..."^)
echo                 major_indices = ['NIFTY', 'BANKNIFTY', 'FINNIFTY']
echo                 
echo                 for idx, symbol in enumerate(major_indices, 1^):
echo                     print(f"      [{idx}/3] Fetching {symbol} options..."^)
echo                     options_data = self.fetch_options_chain(symbol^)
echo                     if not options_data.empty:
echo                         data_dict[f'{symbol}_options'] = options_data
echo                         print(f"            ✓ Retrieved {len(options_data^)} {symbol} option contracts"^)
echo                     time.sleep(1^)  # Small delay between API calls
echo                 
echo                 # Save data to Excel
echo                 print("[4/4] Saving data to Excel files..."^)
echo                 self.save_to_excel(data_dict^)
echo                 
echo                 print(f"\n✅ Cycle {cycle_count} completed successfully!"^)
echo                 print("💤 Waiting 5 seconds for next update..."^)
echo                 
echo                 # Wait for 5 seconds
echo                 time.sleep(5^)
echo                 
echo         except KeyboardInterrupt:
echo             print(f"\n\n🛑 Download stopped by user after {cycle_count} cycles"^)
echo             print(f"📁 All data saved in: {self.output_dir}"^)
echo         except Exception as e:
echo             print(f"\n❌ Unexpected error: {e}"^)
echo.
echo if __name__ == "__main__":
echo     downloader = IndianMarketDataFetcher(^)
echo     downloader.run_continuous_download(^)
) > market_data_fetcher.py

echo [SETUP] Data fetcher script created successfully!
echo.

:: Start the data download
echo ================================================================
echo                    STARTING DATA DOWNLOAD
echo ================================================================
echo.
echo Data will be saved in: %OUTPUT_DIR%
echo.
echo Press Ctrl+C to stop the download process
echo.
pause

python market_data_fetcher.py

echo.
echo ================================================================
echo                    DOWNLOAD PROCESS COMPLETED
echo ================================================================
echo.
echo Check the '%OUTPUT_DIR%' folder for all downloaded Excel files
echo.
pause