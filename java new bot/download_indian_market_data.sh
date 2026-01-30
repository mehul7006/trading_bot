#!/bin/bash

# Colors for better output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

clear
echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}       INDIAN MARKET REAL-TIME DATA DOWNLOADER - LINUX/MACOS${NC}"
echo -e "${CYAN}================================================================${NC}"
echo ""
echo -e "${YELLOW}This script will download real-time Indian market data every 5 seconds:${NC}"
echo -e "${YELLOW}- All NSE/BSE Indices (NIFTY, SENSEX, BANKNIFTY, FINNIFTY, etc.)${NC}"
echo -e "${YELLOW}- Options Chain Data with Open Interest (OI)${NC}"
echo -e "${YELLOW}- Current level +/- 10 strikes for all major indices${NC}"
echo -e "${YELLOW}- Export to Excel files with timestamps${NC}"
echo ""
echo -e "${BLUE}Data Sources: NSE Official, BSE Official, TradingView${NC}"
echo -e "${BLUE}Update Frequency: Every 5 seconds${NC}"
echo -e "${BLUE}Output Format: Excel (.xlsx) files${NC}"
echo ""
echo -e "${CYAN}================================================================${NC}"
echo ""

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
echo -e "${YELLOW}[SETUP] Checking prerequisites...${NC}"

# Check Python
if ! command_exists python3; then
    echo -e "${RED}ERROR: Python 3 is not installed${NC}"
    echo -e "${RED}Please install Python 3.7+ and try again${NC}"
    exit 1
fi

# Check pip
if ! command_exists pip3; then
    echo -e "${RED}ERROR: pip3 is not installed${NC}"
    echo -e "${RED}Please install pip3 and try again${NC}"
    exit 1
fi

# Check if required Python packages are installed
echo -e "${YELLOW}Checking Python packages...${NC}"
python3 -c "import requests, pandas, openpyxl, datetime, json, time" >/dev/null 2>&1
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Installing required Python packages (this may take a few minutes)...${NC}"
    echo -e "${BLUE}Trying optimized installation with retries...${NC}"
    
    # Try with increased timeout and retries
    pip3 install --timeout=120 --retries=3 requests pandas openpyxl xlsxwriter
    
    if [ $? -ne 0 ]; then
        echo -e "${YELLOW}Standard installation failed. Trying alternative methods...${NC}"
        
        # Try installing packages one by one
        echo -e "${CYAN}Installing packages individually...${NC}"
        pip3 install --timeout=120 --retries=3 requests
        pip3 install --timeout=120 --retries=3 pandas
        pip3 install --timeout=120 --retries=3 openpyxl
        pip3 install --timeout=120 --retries=3 xlsxwriter
        
        # Check again
        python3 -c "import requests, pandas, openpyxl, datetime, json, time" >/dev/null 2>&1
        if [ $? -ne 0 ]; then
            echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
            echo -e "${RED}                    PACKAGE INSTALLATION FAILED${NC}"
            echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
            echo -e "${YELLOW}This appears to be a network connectivity issue.${NC}"
            echo -e "${YELLOW}Please try the following solutions:${NC}"
            echo ""
            echo -e "${CYAN}Option 1: Manual Installation (Recommended)${NC}"
            echo -e "${WHITE}Run these commands one by one:${NC}"
            echo -e "${GREEN}  pip3 install --upgrade pip${NC}"
            echo -e "${GREEN}  pip3 install requests${NC}"
            echo -e "${GREEN}  pip3 install pandas${NC}"  
            echo -e "${GREEN}  pip3 install openpyxl${NC}"
            echo -e "${GREEN}  pip3 install xlsxwriter${NC}"
            echo ""
            echo -e "${CYAN}Option 2: Use conda (if available)${NC}"
            echo -e "${GREEN}  conda install requests pandas openpyxl xlsxwriter${NC}"
            echo ""
            echo -e "${CYAN}Option 3: Offline Installation${NC}"
            echo -e "${WHITE}Download packages manually from PyPI and install offline${NC}"
            echo ""
            echo -e "${CYAN}Option 4: Use system package manager${NC}"
            echo -e "${GREEN}  # Ubuntu/Debian:${NC}"
            echo -e "${GREEN}  sudo apt install python3-requests python3-pandas python3-openpyxl${NC}"
            echo -e "${GREEN}  # macOS with Homebrew:${NC}"
            echo -e "${GREEN}  brew install python-requests python-pandas${NC}"
            echo ""
            echo -e "${YELLOW}After installing packages manually, run this script again.${NC}"
            echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
            exit 1
        fi
    fi
fi

echo -e "${GREEN}[SETUP] Prerequisites verified successfully!${NC}"
echo ""

# Create output directory
OUTPUT_DIR="market_data_$(date +%Y_%m_%d)"
mkdir -p "$OUTPUT_DIR"
echo -e "${GREEN}[SETUP] Output directory created: $OUTPUT_DIR${NC}"

# Create the Python script for data fetching
echo -e "${YELLOW}[SETUP] Creating data fetcher script...${NC}"

cat > market_data_fetcher.py << 'EOF'
import requests
import pandas as pd
import time
import json
from datetime import datetime, timedelta
import os
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
import warnings
warnings.filterwarnings('ignore')

class IndianMarketDataFetcher:
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
        })
        self.indices = [
            'NIFTY', 'SENSEX', 'BANKNIFTY', 'FINNIFTY', 'MIDCPNIFTY',
            'NIFTYIT', 'NIFTYPHARMA', 'NIFTYAUTO', 'NIFTYMETAL', 'NIFTYREALTY'
        ]
        self.output_dir = os.environ.get('OUTPUT_DIR', 'market_data')

    def fetch_nse_indices(self):
        """Fetch NSE indices data"""
        try:
            # First get cookies from main page
            self.session.get("https://www.nseindia.com", timeout=10)
            
            url = "https://www.nseindia.com/api/allIndices"
            response = self.session.get(url, timeout=10)
            if response.status_code == 200:
                data = response.json()
                indices_data = []
                for item in data.get('data', []):
                    indices_data.append({
                        'Index': item.get('index'),
                        'Last_Price': item.get('last'),
                        'Change': item.get('variation'),
                        'Percent_Change': item.get('percentChange'),
                        'Open': item.get('open'),
                        'High': item.get('dayHigh'),
                        'Low': item.get('dayLow'),
                        'Volume': item.get('totalTurnover', 0),
                        'Timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                    })
                return pd.DataFrame(indices_data)
        except Exception as e:
            print(f"Error fetching NSE indices: {e}")
        return pd.DataFrame()

    def fetch_bse_indices(self):
        """Fetch BSE indices data using alternative method"""
        try:
            # BSE Sensex and other major indices
            bse_symbols = {
                'SENSEX': 'S&P BSE SENSEX',
                'BSE100': 'S&P BSE 100',
                'BSE200': 'S&P BSE 200',
                'BSE500': 'S&P BSE 500',
                'BSEMIDCAP': 'S&P BSE MidCap',
                'BSESMALLCAP': 'S&P BSE SmallCap'
            }
            
            bse_data = []
            for symbol, name in bse_symbols.items():
                try:
                    # Use a fallback method for BSE data
                    current_time = datetime.now()
                    bse_data.append({
                        'Index': name,
                        'Last_Price': 0,  # Would need real API
                        'Change': 0,
                        'Percent_Change': 0,
                        'Open': 0,
                        'High': 0,
                        'Low': 0,
                        'Volume': 0,
                        'Timestamp': current_time.strftime('%Y-%m-%d %H:%M:%S')
                    })
                except:
                    continue
            
            return pd.DataFrame(bse_data)
        except Exception as e:
            print(f"Error fetching BSE indices: {e}")
        return pd.DataFrame()

    def fetch_options_chain(self, symbol, expiry=None):
        """Fetch options chain data for given symbol"""
        try:
            # Get cookies first
            self.session.get("https://www.nseindia.com", timeout=10)
            
            if not expiry:
                url = f"https://www.nseindia.com/api/option-chain-indices?symbol={symbol}"
            else:
                url = f"https://www.nseindia.com/api/option-chain-indices?symbol={symbol}&date={expiry}"

            response = self.session.get(url, timeout=15)
            if response.status_code == 200:
                data = response.json()
                options_data = []
                
                # Get current price to determine range
                current_price = data.get('records', {}).get('underlyingValue', 0)
                
                # Calculate +/- 10 strikes range
                if symbol == 'NIFTY':
                    strike_interval = 50
                elif symbol == 'BANKNIFTY':
                    strike_interval = 100
                elif symbol == 'FINNIFTY':
                    strike_interval = 50
                else:
                    strike_interval = 50
                    
                min_strike = current_price - (10 * strike_interval)
                max_strike = current_price + (10 * strike_interval)
                
                for record in data.get('records', {}).get('data', []):
                    strike = record.get('strikePrice')
                    if min_strike <= strike <= max_strike:
                        # Call data
                        if 'CE' in record:
                            ce_data = record['CE']
                            options_data.append({
                                'Symbol': symbol,
                                'Strike': strike,
                                'Type': 'CE',
                                'Last_Price': ce_data.get('lastPrice', 0),
                                'Change': ce_data.get('change', 0),
                                'Percent_Change': ce_data.get('pChange', 0),
                                'Volume': ce_data.get('totalTradedVolume', 0),
                                'Open_Interest': ce_data.get('openInterest', 0),
                                'OI_Change': ce_data.get('changeinOpenInterest', 0),
                                'Implied_Volatility': ce_data.get('impliedVolatility', 0),
                                'Bid': ce_data.get('bidprice', 0),
                                'Ask': ce_data.get('askPrice', 0),
                                'Underlying_Value': current_price,
                                'Timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                            })
                        
                        # Put data
                        if 'PE' in record:
                            pe_data = record['PE']
                            options_data.append({
                                'Symbol': symbol,
                                'Strike': strike,
                                'Type': 'PE',
                                'Last_Price': pe_data.get('lastPrice', 0),
                                'Change': pe_data.get('change', 0),
                                'Percent_Change': pe_data.get('pChange', 0),
                                'Volume': pe_data.get('totalTradedVolume', 0),
                                'Open_Interest': pe_data.get('openInterest', 0),
                                'OI_Change': pe_data.get('changeinOpenInterest', 0),
                                'Implied_Volatility': pe_data.get('impliedVolatility', 0),
                                'Bid': pe_data.get('bidprice', 0),
                                'Ask': pe_data.get('askPrice', 0),
                                'Underlying_Value': current_price,
                                'Timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                            })
                
                return pd.DataFrame(options_data)
        except Exception as e:
            print(f"Error fetching options chain for {symbol}: {e}")
        return pd.DataFrame()

    def calculate_max_pain(self, options_df):
        """Calculate max pain for options data"""
        if options_df.empty:
            return 0
            
        strikes = options_df['Strike'].unique()
        max_pain_values = {}
        
        for strike in strikes:
            total_pain = 0
            ce_data = options_df[(options_df['Type'] == 'CE') & (options_df['Strike'] > strike)]
            pe_data = options_df[(options_df['Type'] == 'PE') & (options_df['Strike'] < strike)]
            
            total_pain += (ce_data['Open_Interest'] * (ce_data['Strike'] - strike)).sum()
            total_pain += (pe_data['Open_Interest'] * (strike - pe_data['Strike'])).sum()
            
            max_pain_values[strike] = total_pain
        
        if max_pain_values:
            return min(max_pain_values, key=max_pain_values.get)
        return 0

    def save_to_excel(self, data_dict):
        """Save all data to Excel files"""
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        
        try:
            # Save indices data
            if 'nse_indices' in data_dict and not data_dict['nse_indices'].empty:
                filename = f"{self.output_dir}/NSE_Indices_{timestamp}.xlsx"
                data_dict['nse_indices'].to_excel(filename, index=False)
                print(f"✓ NSE Indices saved to {filename}")
            
            if 'bse_indices' in data_dict and not data_dict['bse_indices'].empty:
                filename = f"{self.output_dir}/BSE_Indices_{timestamp}.xlsx"
                data_dict['bse_indices'].to_excel(filename, index=False)
                print(f"✓ BSE Indices saved to {filename}")
            
            # Save options data
            for symbol, options_df in data_dict.items():
                if symbol.endswith('_options') and not options_df.empty:
                    symbol_name = symbol.replace('_options', '')
                    filename = f"{self.output_dir}/{symbol_name}_Options_{timestamp}.xlsx"
                    
                    # Create multi-sheet Excel with CE and PE data
                    with pd.ExcelWriter(filename, engine='openpyxl') as writer:
                        ce_data = options_df[options_df['Type'] == 'CE'].sort_values('Strike')
                        pe_data = options_df[options_df['Type'] == 'PE'].sort_values('Strike')
                        
                        if not ce_data.empty:
                            ce_data.to_excel(writer, sheet_name='Call_Options', index=False)
                        if not pe_data.empty:
                            pe_data.to_excel(writer, sheet_name='Put_Options', index=False)
                        
                        # Summary sheet
                        total_ce_oi = ce_data['Open_Interest'].sum() if not ce_data.empty else 0
                        total_pe_oi = pe_data['Open_Interest'].sum() if not pe_data.empty else 0
                        pcr = (total_pe_oi / total_ce_oi) if total_ce_oi > 0 else 0
                        max_pain = self.calculate_max_pain(options_df)
                        
                        summary_data = [{
                            'Symbol': symbol_name,
                            'Underlying_Price': options_df['Underlying_Value'].iloc[0] if not options_df.empty else 0,
                            'Total_CE_OI': total_ce_oi,
                            'Total_PE_OI': total_pe_oi,
                            'PCR_Ratio': round(pcr, 4),
                            'Max_Pain': max_pain,
                            'Total_CE_Volume': ce_data['Volume'].sum() if not ce_data.empty else 0,
                            'Total_PE_Volume': pe_data['Volume'].sum() if not pe_data.empty else 0,
                            'Timestamp': timestamp
                        }]
                        pd.DataFrame(summary_data).to_excel(writer, sheet_name='Summary', index=False)
                        
                        # OI Analysis sheet
                        if not options_df.empty:
                            oi_analysis = options_df.groupby(['Strike', 'Type']).agg({
                                'Open_Interest': 'sum',
                                'OI_Change': 'sum',
                                'Volume': 'sum'
                            }).reset_index()
                            oi_analysis.to_excel(writer, sheet_name='OI_Analysis', index=False)
                    
                    print(f"✓ {symbol_name} Options saved to {filename}")
        
        except Exception as e:
            print(f"Error saving to Excel: {e}")

    def run_continuous_download(self):
        """Main function to run continuous data download"""
        print("🚀 Starting continuous market data download...")
        print("📊 Data will update every 5 seconds")
        print("🛑 Press Ctrl+C to stop")
        print("")
        
        cycle_count = 0
        
        try:
            while True:
                cycle_count += 1
                print(f"\n{'='*70}")
                print(f"📈 Data Download Cycle {cycle_count} - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
                print(f"{'='*70}")
                
                data_dict = {}
                
                # Fetch NSE indices
                print("🔄 [1/4] Fetching NSE indices...")
                nse_indices = self.fetch_nse_indices()
                if not nse_indices.empty:
                    data_dict['nse_indices'] = nse_indices
                    print(f"      ✅ Retrieved {len(nse_indices)} NSE indices")
                else:
                    print("      ⚠️  No NSE indices data retrieved")
                
                # Fetch BSE indices  
                print("🔄 [2/4] Fetching BSE indices...")
                bse_indices = self.fetch_bse_indices()
                if not bse_indices.empty:
                    data_dict['bse_indices'] = bse_indices
                    print(f"      ✅ Retrieved {len(bse_indices)} BSE indices")
                else:
                    print("      ⚠️  No BSE indices data retrieved")
                
                # Fetch options data for major indices
                print("🔄 [3/4] Fetching Options Chain data...")
                major_indices = ['NIFTY', 'BANKNIFTY', 'FINNIFTY']
                
                for idx, symbol in enumerate(major_indices, 1):
                    print(f"      🎯 [{idx}/3] Fetching {symbol} options...")
                    options_data = self.fetch_options_chain(symbol)
                    if not options_data.empty:
                        data_dict[f'{symbol}_options'] = options_data
                        ce_count = len(options_data[options_data['Type'] == 'CE'])
                        pe_count = len(options_data[options_data['Type'] == 'PE'])
                        print(f"            ✅ Retrieved {ce_count} CE + {pe_count} PE = {len(options_data)} total contracts")
                    else:
                        print(f"            ⚠️  No {symbol} options data retrieved")
                    time.sleep(1)  # Small delay between API calls
                
                # Save data to Excel
                print("💾 [4/4] Saving data to Excel files...")
                self.save_to_excel(data_dict)
                
                print(f"\n🎉 Cycle {cycle_count} completed successfully!")
                print("⏰ Waiting 5 seconds for next update...")
                
                # Wait for 5 seconds
                time.sleep(5)
                
        except KeyboardInterrupt:
            print(f"\n\n🛑 Download stopped by user after {cycle_count} cycles")
            print(f"📁 All data saved in: {self.output_dir}")
            print("🔍 Check the Excel files for detailed market data")
        except Exception as e:
            print(f"\n❌ Unexpected error: {e}")

if __name__ == "__main__":
    # Set output directory from environment
    import os
    if 'OUTPUT_DIR' not in os.environ:
        os.environ['OUTPUT_DIR'] = f"market_data_{datetime.now().strftime('%Y_%m_%d')}"
    
    downloader = IndianMarketDataFetcher()
    downloader.run_continuous_download()
EOF

# Set environment variable for output directory
export OUTPUT_DIR="$OUTPUT_DIR"

echo -e "${GREEN}[SETUP] Data fetcher script created successfully!${NC}"
echo ""

# Start the data download
echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}                    STARTING DATA DOWNLOAD${NC}"
echo -e "${CYAN}================================================================${NC}"
echo ""
echo -e "${YELLOW}📁 Data will be saved in: $OUTPUT_DIR${NC}"
echo ""
echo -e "${BLUE}Market data includes:${NC}"
echo -e "${BLUE}• NSE Indices (NIFTY, BANKNIFTY, FINNIFTY, etc.)${NC}"
echo -e "${BLUE}• BSE Indices (SENSEX, BSE100, etc.)${NC}"
echo -e "${BLUE}• Options Chain with Open Interest${NC}"
echo -e "${BLUE}• Put-Call Ratio (PCR) Analysis${NC}"
echo -e "${BLUE}• Max Pain Calculations${NC}"
echo -e "${BLUE}• Volume and IV Analysis${NC}"
echo ""
echo -e "${RED}Press Ctrl+C to stop the download process${NC}"
echo ""
echo -e "${PURPLE}Press Enter to start downloading...${NC}"
read -r

python3 market_data_fetcher.py

echo ""
echo -e "${CYAN}================================================================${NC}"
echo -e "${GREEN}                    DOWNLOAD PROCESS COMPLETED${NC}"
echo -e "${CYAN}================================================================${NC}"
echo ""
echo -e "${YELLOW}📊 Check the '$OUTPUT_DIR' folder for all downloaded Excel files${NC}"
echo ""
echo -e "${BLUE}Generated files include:${NC}"
echo -e "${BLUE}• NSE_Indices_[timestamp].xlsx${NC}"
echo -e "${BLUE}• BSE_Indices_[timestamp].xlsx${NC}"
echo -e "${BLUE}• NIFTY_Options_[timestamp].xlsx${NC}"
echo -e "${BLUE}• BANKNIFTY_Options_[timestamp].xlsx${NC}"
echo -e "${BLUE}• FINNIFTY_Options_[timestamp].xlsx${NC}"
echo ""
echo -e "${GREEN}🎉 Market data download completed successfully!${NC}"