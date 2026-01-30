#!/usr/bin/env python3

"""
WEEKLY MARKET TRENDS ANALYZER
Advanced analysis of weekly market movement data from official sources
Provides statistical analysis, trend detection, and predictive insights
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns
from datetime import datetime, timedelta
import glob
import os
import sys
from typing import Dict, List, Tuple
import warnings
warnings.filterwarnings('ignore')

class WeeklyMarketAnalyzer:
    """
    Comprehensive analyzer for weekly market data from official sources
    """
    
    def __init__(self, data_dir: str = "market_data"):
        self.data_dir = data_dir
        self.market_data = None
        self.analysis_data = None
        self.indices = ['NIFTY', 'SENSEX', 'BANKNIFTY', 'FINNIFTY']
        
        # Create output directories
        os.makedirs("analysis_reports", exist_ok=True)
        os.makedirs("charts", exist_ok=True)
        
    def load_data(self) -> bool:
        """Load market data and analysis files"""
        try:
            # Find the latest data files
            data_files = glob.glob(f"{self.data_dir}/weekly_market_data_*.csv")
            analysis_files = glob.glob(f"{self.data_dir}/weekly_market_analysis_*.csv")
            
            if not data_files or not analysis_files:
                print("❌ No market data files found in", self.data_dir)
                return False
            
            # Load the latest files
            latest_data_file = max(data_files, key=os.path.getctime)
            latest_analysis_file = max(analysis_files, key=os.path.getctime)
            
            print(f"📊 Loading data from: {latest_data_file}")
            print(f"📈 Loading analysis from: {latest_analysis_file}")
            
            self.market_data = pd.read_csv(latest_data_file)
            self.analysis_data = pd.read_csv(latest_analysis_file)
            
            # Convert date columns
            self.market_data['Date'] = pd.to_datetime(self.market_data['Date'])
            self.analysis_data['WeekStart'] = pd.to_datetime(self.analysis_data['WeekStart'])
            self.analysis_data['WeekEnd'] = pd.to_datetime(self.analysis_data['WeekEnd'])
            
            print(f"✅ Loaded {len(self.market_data)} data points and {len(self.analysis_data)} analysis records")
            return True
            
        except Exception as e:
            print(f"❌ Error loading data: {e}")
            return False
    
    def generate_comprehensive_report(self):
        """Generate comprehensive weekly market analysis report"""
        if self.market_data is None or self.analysis_data is None:
            print("❌ No data loaded. Please run load_data() first.")
            return
        
        print("\n" + "="*80)
        print("COMPREHENSIVE WEEKLY MARKET ANALYSIS REPORT")
        print("="*80)
        
        # Basic statistics
        self._print_basic_statistics()
        
        # Performance analysis
        self._analyze_performance()
        
        # Volatility analysis
        self._analyze_volatility()
        
        # Correlation analysis
        self._analyze_correlations()
        
        # Trend analysis
        self._analyze_trends()
        
        # Generate charts
        self._generate_charts()
        
        # Predictive insights
        self._generate_insights()
        
        # Export detailed report
        self._export_detailed_report()
        
    def _print_basic_statistics(self):
        """Print basic market statistics"""
        print("\n📊 BASIC MARKET STATISTICS")
        print("-" * 50)
        
        for index in self.indices:
            if index in self.analysis_data['Symbol'].values:
                row = self.analysis_data[self.analysis_data['Symbol'] == index].iloc[0]
                print(f"\n{index}:")
                print(f"  Week Open:     {row['WeekOpen']:,.2f}")
                print(f"  Week Close:    {row['WeekClose']:,.2f}")
                print(f"  Week High:     {row['WeekHigh']:,.2f}")
                print(f"  Week Low:      {row['WeekLow']:,.2f}")
                print(f"  Change:        {row['WeekChange']:,.2f} ({row['WeekChange%']:.2f}%)")
                print(f"  Volatility:    {row['Volatility%']:.2f}%")
                print(f"  Trading Days:  {row['TradingDays']}")
    
    def _analyze_performance(self):
        """Analyze performance metrics"""
        print("\n📈 PERFORMANCE ANALYSIS")
        print("-" * 50)
        
        # Sort by performance
        performance_df = self.analysis_data.sort_values('WeekChange%', ascending=False)
        
        print("\nBest Performers:")
        for i, (_, row) in enumerate(performance_df.head(3).iterrows(), 1):
            print(f"  {i}. {row['Symbol']}: {row['WeekChange%']:+.2f}% ({row['Movement']})")
        
        print("\nWorst Performers:")
        for i, (_, row) in enumerate(performance_df.tail(3).iterrows(), 1):
            print(f"  {i}. {row['Symbol']}: {row['WeekChange%']:+.2f}% ({row['Movement']})")
        
        # Calculate average performance
        avg_change = performance_df['WeekChange%'].mean()
        print(f"\nMarket Average Change: {avg_change:+.2f}%")
        
        # Market sentiment
        positive_count = len(performance_df[performance_df['WeekChange%'] > 0])
        total_count = len(performance_df)
        
        print(f"Positive Indices: {positive_count}/{total_count} ({positive_count/total_count*100:.1f}%)")
        
        if avg_change > 1:
            sentiment = "BULLISH 🐂"
        elif avg_change < -1:
            sentiment = "BEARISH 🐻"
        else:
            sentiment = "NEUTRAL ➡️"
        
        print(f"Market Sentiment: {sentiment}")
    
    def _analyze_volatility(self):
        """Analyze volatility patterns"""
        print("\n📊 VOLATILITY ANALYSIS")
        print("-" * 50)
        
        volatility_df = self.analysis_data.sort_values('Volatility%', ascending=False)
        
        print("\nMost Volatile:")
        for i, (_, row) in enumerate(volatility_df.head(3).iterrows(), 1):
            print(f"  {i}. {row['Symbol']}: {row['Volatility%']:.2f}%")
        
        print("\nLeast Volatile:")
        for i, (_, row) in enumerate(volatility_df.tail(3).iterrows(), 1):
            print(f"  {i}. {row['Symbol']}: {row['Volatility%']:.2f}%")
        
        avg_volatility = volatility_df['Volatility%'].mean()
        print(f"\nAverage Market Volatility: {avg_volatility:.2f}%")
        
        # Volatility classification
        if avg_volatility > 5:
            vol_class = "HIGH VOLATILITY ⚡"
        elif avg_volatility > 2:
            vol_class = "MODERATE VOLATILITY 📊"
        else:
            vol_class = "LOW VOLATILITY 😴"
        
        print(f"Volatility Level: {vol_class}")
    
    def _analyze_correlations(self):
        """Analyze correlations between indices"""
        print("\n🔗 CORRELATION ANALYSIS")
        print("-" * 50)
        
        if len(self.market_data) > 0:
            # Pivot data for correlation analysis
            pivot_data = self.market_data.pivot_table(
                index='Date', 
                columns='Symbol', 
                values='Close', 
                aggfunc='first'
            ).fillna(method='ffill')
            
            if len(pivot_data.columns) > 1:
                correlations = pivot_data.corr()
                
                print("\nCorrelation Matrix:")
                for i, idx1 in enumerate(correlations.index):
                    for j, idx2 in enumerate(correlations.columns):
                        if i < j:  # Only print upper triangle
                            corr_val = correlations.loc[idx1, idx2]
                            print(f"  {idx1} vs {idx2}: {corr_val:.3f}")
                
                # Find highest and lowest correlations
                corr_values = []
                for i, idx1 in enumerate(correlations.index):
                    for j, idx2 in enumerate(correlations.columns):
                        if i < j:
                            corr_values.append((idx1, idx2, correlations.loc[idx1, idx2]))
                
                if corr_values:
                    corr_values.sort(key=lambda x: x[2], reverse=True)
                    print(f"\nHighest Correlation: {corr_values[0][0]} vs {corr_values[0][1]} ({corr_values[0][2]:.3f})")
                    print(f"Lowest Correlation: {corr_values[-1][0]} vs {corr_values[-1][1]} ({corr_values[-1][2]:.3f})")
    
    def _analyze_trends(self):
        """Analyze market trends"""
        print("\n📈 TREND ANALYSIS")
        print("-" * 50)
        
        # Analyze movement patterns
        movement_counts = self.analysis_data['Movement'].value_counts()
        
        print("\nMovement Distribution:")
        for movement, count in movement_counts.items():
            percentage = (count / len(self.analysis_data)) * 100
            print(f"  {movement}: {count} ({percentage:.1f}%)")
        
        # Trend strength analysis
        strong_moves = len(self.analysis_data[
            (self.analysis_data['Movement'] == 'STRONG_BULLISH') |
            (self.analysis_data['Movement'] == 'STRONG_BEARISH')
        ])
        
        total_indices = len(self.analysis_data)
        strong_percentage = (strong_moves / total_indices) * 100
        
        print(f"\nStrong Movements: {strong_moves}/{total_indices} ({strong_percentage:.1f}%)")
        
        if strong_percentage > 50:
            trend_strength = "STRONG TRENDING MARKET"
        elif strong_percentage > 25:
            trend_strength = "MODERATE TRENDING MARKET"
        else:
            trend_strength = "RANGE-BOUND MARKET"
        
        print(f"Market Characterization: {trend_strength}")
    
    def _generate_charts(self):
        """Generate visual charts"""
        print("\n📊 GENERATING CHARTS...")
        print("-" * 50)
        
        try:
            # Set style
            plt.style.use('seaborn-v0_8')
            fig, axes = plt.subplots(2, 2, figsize=(15, 12))
            fig.suptitle('Weekly Market Analysis Dashboard', fontsize=16, fontweight='bold')
            
            # Chart 1: Performance comparison
            ax1 = axes[0, 0]
            bars = ax1.bar(self.analysis_data['Symbol'], self.analysis_data['WeekChange%'])
            ax1.set_title('Weekly Performance Comparison')
            ax1.set_ylabel('Change %')
            ax1.grid(True, alpha=0.3)
            
            # Color bars based on performance
            for i, bar in enumerate(bars):
                if self.analysis_data.iloc[i]['WeekChange%'] > 0:
                    bar.set_color('green')
                else:
                    bar.set_color('red')
            
            # Chart 2: Volatility comparison
            ax2 = axes[0, 1]
            ax2.bar(self.analysis_data['Symbol'], self.analysis_data['Volatility%'], color='orange')
            ax2.set_title('Volatility Comparison')
            ax2.set_ylabel('Volatility %')
            ax2.grid(True, alpha=0.3)
            
            # Chart 3: Price levels
            ax3 = axes[1, 0]
            x_pos = np.arange(len(self.analysis_data))
            width = 0.35
            
            ax3.bar(x_pos - width/2, self.analysis_data['WeekHigh'], width, label='Week High', color='lightgreen')
            ax3.bar(x_pos + width/2, self.analysis_data['WeekLow'], width, label='Week Low', color='lightcoral')
            
            ax3.set_title('Weekly High/Low Comparison')
            ax3.set_ylabel('Price Level')
            ax3.set_xticks(x_pos)
            ax3.set_xticklabels(self.analysis_data['Symbol'])
            ax3.legend()
            ax3.grid(True, alpha=0.3)
            
            # Chart 4: Movement distribution
            ax4 = axes[1, 1]
            movement_counts = self.analysis_data['Movement'].value_counts()
            colors = ['green', 'lightgreen', 'gray', 'lightcoral', 'red']
            ax4.pie(movement_counts.values, labels=movement_counts.index, autopct='%1.1f%%', colors=colors[:len(movement_counts)])
            ax4.set_title('Movement Distribution')
            
            plt.tight_layout()
            chart_filename = f"charts/weekly_market_analysis_{datetime.now().strftime('%Y%m%d_%H%M%S')}.png"
            plt.savefig(chart_filename, dpi=300, bbox_inches='tight')
            plt.close()
            
            print(f"✅ Charts saved to: {chart_filename}")
            
        except Exception as e:
            print(f"⚠️ Error generating charts: {e}")
            print("Charts may not be available if matplotlib is not installed")
    
    def _generate_insights(self):
        """Generate predictive insights"""
        print("\n🔮 PREDICTIVE INSIGHTS")
        print("-" * 50)
        
        # Market momentum analysis
        bullish_count = len(self.analysis_data[self.analysis_data['WeekChange%'] > 0])
        total_count = len(self.analysis_data)
        bullish_ratio = bullish_count / total_count
        
        print(f"\nMarket Momentum Score: {bullish_ratio:.2f}")
        
        if bullish_ratio > 0.7:
            momentum = "STRONG BULLISH MOMENTUM 🚀"
            next_week_outlook = "Positive outlook for next week"
        elif bullish_ratio > 0.5:
            momentum = "MODERATE BULLISH MOMENTUM 📈"
            next_week_outlook = "Cautiously optimistic for next week"
        elif bullish_ratio > 0.3:
            momentum = "MODERATE BEARISH MOMENTUM 📉"
            next_week_outlook = "Cautious outlook for next week"
        else:
            momentum = "STRONG BEARISH MOMENTUM 🔻"
            next_week_outlook = "Defensive stance recommended for next week"
        
        print(f"Current Momentum: {momentum}")
        print(f"Next Week Outlook: {next_week_outlook}")
        
        # Volatility-based insights
        avg_volatility = self.analysis_data['Volatility%'].mean()
        
        if avg_volatility > 5:
            vol_insight = "High volatility suggests uncertainty - consider position sizing carefully"
        elif avg_volatility > 2:
            vol_insight = "Moderate volatility provides good trading opportunities"
        else:
            vol_insight = "Low volatility suggests consolidation - breakout may be imminent"
        
        print(f"\nVolatility Insight: {vol_insight}")
        
        # Sector rotation insights
        if 'BANKNIFTY' in self.analysis_data['Symbol'].values and 'NIFTY' in self.analysis_data['Symbol'].values:
            bank_change = self.analysis_data[self.analysis_data['Symbol'] == 'BANKNIFTY']['WeekChange%'].iloc[0]
            nifty_change = self.analysis_data[self.analysis_data['Symbol'] == 'NIFTY']['WeekChange%'].iloc[0]
            
            if bank_change > nifty_change + 1:
                sector_insight = "Banking sector outperforming - financials showing strength"
            elif bank_change < nifty_change - 1:
                sector_insight = "Banking sector underperforming - caution in financials"
            else:
                sector_insight = "Banking and broader market moving in sync"
            
            print(f"Sector Insight: {sector_insight}")
    
    def _export_detailed_report(self):
        """Export detailed analysis report"""
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        report_filename = f"analysis_reports/weekly_market_report_{timestamp}.txt"
        
        try:
            with open(report_filename, 'w') as f:
                f.write("WEEKLY MARKET ANALYSIS REPORT\n")
                f.write("=" * 50 + "\n")
                f.write(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
                
                # Export all analysis data
                f.write("DETAILED ANALYSIS DATA:\n")
                f.write("-" * 30 + "\n")
                f.write(self.analysis_data.to_string(index=False))
                f.write("\n\n")
                
                # Export market data summary
                if len(self.market_data) > 0:
                    f.write("MARKET DATA SUMMARY:\n")
                    f.write("-" * 30 + "\n")
                    f.write(f"Total data points: {len(self.market_data)}\n")
                    f.write(f"Date range: {self.market_data['Date'].min()} to {self.market_data['Date'].max()}\n")
                    f.write(f"Symbols covered: {', '.join(self.market_data['Symbol'].unique())}\n")
                    f.write(f"Data sources: {', '.join(self.market_data['Source'].unique())}\n")
            
            print(f"\n📄 Detailed report exported to: {report_filename}")
            
        except Exception as e:
            print(f"⚠️ Error exporting report: {e}")
    
    def run_analysis(self):
        """Run complete analysis pipeline"""
        print("🚀 Starting Weekly Market Trends Analysis...")
        
        if not self.load_data():
            return False
        
        self.generate_comprehensive_report()
        
        print("\n" + "="*80)
        print("✅ WEEKLY MARKET ANALYSIS COMPLETED")
        print("="*80)
        print("📁 Output files generated in:")
        print("  • analysis_reports/ - Detailed text reports")
        print("  • charts/ - Visual analysis charts")
        print("="*80)
        
        return True

def main():
    """Main function"""
    print("WEEKLY MARKET TRENDS ANALYZER")
    print("="*50)
    print("Advanced analysis of weekly market movement data")
    print("="*50)
    
    analyzer = WeeklyMarketAnalyzer()
    
    if analyzer.run_analysis():
        sys.exit(0)
    else:
        print("❌ Analysis failed. Please check data files and try again.")
        sys.exit(1)

if __name__ == "__main__":
    main()