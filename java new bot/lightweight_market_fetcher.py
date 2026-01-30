import urllib.request
import json
import csv
import time
import datetime
import os
import sys

class LightweightMarketFetcher:
    def __init__(self):
        self.output_dir = os.environ.get('OUTPUT_DIR', 'market_data_csv')
        self.headers = {
            'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36'
        }
        
    def fetch_with_retry(self, url, max_retries=3, timeout=10):
        """Fetch URL with retry logic"""
        for attempt in range(max_retries):
            try:
                req = urllib.request.Request(url, headers=self.headers)
                with urllib.request.urlopen(req, timeout=timeout) as response:
                    return response.read().decode('utf-8')
            except Exception as e:
                print(f"    Attempt {attempt + 1} failed: {str(e)[:50]}...")
                if attempt < max_retries - 1:
                    time.sleep(2)
                else:
                    print(f"    All attempts failed for {url}")
                    return None
        return None

    def fetch_nse_indices(self):
        """Fetch basic NSE indices data"""
        print("  🔄 Fetching NSE indices...")
        
        # Try multiple NSE endpoints
        urls = [
            "https://www.nseindia.com/api/allIndices",
            "https://www.nseindia.com/api/equity-stockIndices?csv=true"
        ]
        
        for url in urls:
            try:
                # First visit main page to get cookies
                main_page = self.fetch_with_retry("https://www.nseindia.com")
                if not main_page:
                    continue
                    
                data_str = self.fetch_with_retry(url)
                if data_str:
                    data = json.loads(data_str)
                    indices_data = []
                    
                    for item in data.get('data', []):
                        indices_data.append({
                            'Index': item.get('index', 'N/A'),
                            'Last_Price': item.get('last', 0),
                            'Change': item.get('variation', 0),
                            'Percent_Change': item.get('percentChange', 0),
                            'Open': item.get('open', 0),
                            'High': item.get('dayHigh', 0),
                            'Low': item.get('dayLow', 0),
                            'Timestamp': datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                        })
                    
                    if indices_data:
                        print(f"    ✅ Retrieved {len(indices_data)} NSE indices")
                        return indices_data
            except Exception as e:
                print(f"    Error with {url}: {str(e)[:50]}...")
                continue
        
        print("    ⚠️  Could not fetch NSE data")
        return []

    def fetch_mock_data(self):
        """Generate mock data for demonstration when APIs fail"""
        print("  🎯 Generating sample data (API unavailable)...")
        
        import random
        base_prices = {
            'NIFTY 50': 19500,
            'NIFTY BANK': 45000,
            'NIFTY IT': 28000,
            'NIFTY PHARMA': 13500,
            'SENSEX': 65000
        }
        
        mock_data = []
        for index, base_price in base_prices.items():
            change = random.uniform(-100, 100)
            pct_change = (change / base_price) * 100
            
            mock_data.append({
                'Index': index,
                'Last_Price': round(base_price + change, 2),
                'Change': round(change, 2),
                'Percent_Change': round(pct_change, 2),
                'Open': round(base_price + random.uniform(-50, 50), 2),
                'High': round(base_price + random.uniform(0, 100), 2),
                'Low': round(base_price + random.uniform(-100, 0), 2),
                'Timestamp': datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            })
        
        print(f"    ✅ Generated {len(mock_data)} sample indices")
        return mock_data

    def save_to_csv(self, data, filename):
        """Save data to CSV file"""
        if not data:
            return False
            
        filepath = os.path.join(self.output_dir, filename)
        try:
            with open(filepath, 'w', newline='', encoding='utf-8') as csvfile:
                fieldnames = data[0].keys()
                writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
                writer.writeheader()
                writer.writerows(data)
            
            print(f"    💾 Saved to {filename}")
            return True
        except Exception as e:
            print(f"    ❌ Error saving {filename}: {e}")
            return False

    def run_continuous_download(self):
        """Main function to run continuous data download"""
        print("🚀 Starting lightweight market data download...")
        print("📊 Data will update every 5 seconds")
        print("💾 Output format: CSV files")
        print("🛑 Press Ctrl+C to stop")
        print("")
        
        cycle_count = 0
        consecutive_failures = 0
        
        try:
            while True:
                cycle_count += 1
                print(f"\n{'='*60}")
                print(f"📈 Download Cycle {cycle_count} - {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
                print(f"{'='*60}")
                
                success = False
                
                # Try to fetch real NSE data
                nse_data = self.fetch_nse_indices()
                if nse_data:
                    timestamp = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
                    filename = f"NSE_Indices_{timestamp}.csv"
                    if self.save_to_csv(nse_data, filename):
                        success = True
                        consecutive_failures = 0
                
                # If real data fails, use mock data
                if not success:
                    consecutive_failures += 1
                    if consecutive_failures >= 3:
                        print("  ⚠️  Multiple API failures, switching to demo mode...")
                        mock_data = self.fetch_mock_data()
                        timestamp = datetime.datetime.now().strftime('%Y%m%d_%H%M%S')
                        filename = f"Demo_Indices_{timestamp}.csv"
                        self.save_to_csv(mock_data, filename)
                
                if success:
                    print(f"✅ Cycle {cycle_count} completed successfully!")
                else:
                    print(f"⚠️  Cycle {cycle_count} completed with demo data")
                
                print("⏰ Waiting 5 seconds for next update...")
                time.sleep(5)
                
        except KeyboardInterrupt:
            print(f"\n\n🛑 Download stopped by user after {cycle_count} cycles")
            print(f"📁 All data saved in: {self.output_dir}")
            
            # Show file summary
            try:
                files = os.listdir(self.output_dir)
                csv_files = [f for f in files if f.endswith('.csv')]
                print(f"📊 Generated {len(csv_files)} CSV files:")
                for file in csv_files[-5:]:  # Show last 5 files
                    print(f"    - {file}")
                if len(csv_files) > 5:
                    print(f"    ... and {len(csv_files) - 5} more files")
            except:
                pass
                
        except Exception as e:
            print(f"\n❌ Unexpected error: {e}")

if __name__ == "__main__":
    # Set output directory from environment
    import os
    if 'OUTPUT_DIR' not in os.environ:
        os.environ['OUTPUT_DIR'] = f"market_data_csv_{datetime.datetime.now().strftime('%Y_%m_%d')}"
    
    fetcher = LightweightMarketFetcher()
    fetcher.run_continuous_download()
