#!/usr/bin/env python3
"""
HONEST ACCURACY VALIDATOR
Separates real accuracy from confidence scores and marketing hype
"""

import json
import re
import os
from datetime import datetime
from typing import Dict, List, Tuple

class HonestAccuracyValidator:
    def __init__(self):
        print("🔍 HONEST ACCURACY VALIDATOR")
        print("Separating REAL performance from marketing hype")
        print("=" * 60)
        
        self.validation_results = {}
        self.accuracy_claims = {}
        self.real_performance = {}
        
    def scan_accuracy_claims(self) -> Dict:
        """Scan all files for accuracy claims and validate them"""
        print("\n📊 SCANNING FOR ACCURACY CLAIMS...")
        
        accuracy_patterns = [
            r'(\d{1,2})\.?\d*%?\s*accuracy',
            r'accuracy.*?(\d{1,2})\.?\d*%',
            r'(\d{1,2})\.?\d*%?\s*confident',
            r'confident.*?(\d{1,2})\.?\d*%',
            r'win.?rate.*?(\d{1,2})\.?\d*%',
            r'(\d{1,2})\.?\d*%?\s*win.?rate'
        ]
        
        files_to_scan = []
        
        # Find all relevant files
        for root, dirs, files in os.walk('.'):
            for file in files:
                if file.endswith(('.md', '.txt', '.py', '.java', '.json')):
                    files_to_scan.append(os.path.join(root, file))
        
        claims_found = {}
        
        for file_path in files_to_scan:
            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read().lower()
                    
                file_claims = []
                for pattern in accuracy_patterns:
                    matches = re.findall(pattern, content, re.IGNORECASE)
                    for match in matches:
                        try:
                            accuracy_value = float(match)
                            if 20 <= accuracy_value <= 100:  # Reasonable accuracy range
                                file_claims.append(accuracy_value)
                        except:
                            continue
                
                if file_claims:
                    claims_found[file_path] = {
                        'claims': list(set(file_claims)),  # Remove duplicates
                        'max_claim': max(file_claims),
                        'avg_claim': sum(file_claims) / len(file_claims)
                    }
                    
            except Exception as e:
                continue
        
        self.accuracy_claims = claims_found
        return claims_found
    
    def validate_against_real_data(self) -> Dict:
        """Validate claims against actual performance data"""
        print("\n🔍 VALIDATING AGAINST REAL PERFORMANCE DATA...")
        
        # Look for actual performance files
        real_data_files = [
            'real_bot_accuracy_report_2025-10-30.txt',
            'final_accuracy_test_report_20251104_003309.txt',
            'bot_backtest_report_2025-10-28.txt',
            'calibrated_three_tier_results_20251105_234209.json',
            'production_70_75_accuracy_final_20251105_004054.json'
        ]
        
        validated_performance = {}
        
        for file_name in real_data_files:
            if os.path.exists(file_name):
                try:
                    if file_name.endswith('.json'):
                        with open(file_name, 'r') as f:
                            data = json.load(f)
                            
                        if 'achieved_accuracy' in data:
                            validated_performance[file_name] = {
                                'type': 'validated_test',
                                'accuracy': data['achieved_accuracy'],
                                'target_achieved': data.get('target_achieved', False),
                                'timestamp': data.get('timestamp', 'unknown')
                            }
                    
                    elif file_name.endswith('.txt'):
                        with open(file_name, 'r') as f:
                            content = f.read()
                            
                        # Extract actual accuracy numbers
                        accuracy_matches = re.findall(r'(\d{1,2}\.?\d*)%\s*accuracy', content, re.IGNORECASE)
                        if accuracy_matches:
                            accuracies = [float(match) for match in accuracy_matches]
                            validated_performance[file_name] = {
                                'type': 'real_test_data',
                                'accuracies': accuracies,
                                'avg_accuracy': sum(accuracies) / len(accuracies),
                                'range': f"{min(accuracies):.1f}% - {max(accuracies):.1f}%"
                            }
                
                except Exception as e:
                    print(f"❌ Error reading {file_name}: {e}")
        
        self.real_performance = validated_performance
        return validated_performance
    
    def categorize_claims(self) -> Dict:
        """Categorize accuracy claims as realistic, inflated, or unverified"""
        print("\n📋 CATEGORIZING ACCURACY CLAIMS...")
        
        categories = {
            'realistic': [],      # 40-65% range
            'optimistic': [],     # 65-75% range  
            'inflated': [],       # 75-85% range
            'impossible': [],     # 85%+ range
            'unverified': []      # No supporting data
        }
        
        for file_path, claim_data in self.accuracy_claims.items():
            max_claim = claim_data['max_claim']
            
            # Check if this file has any validation
            has_validation = any(
                file_path in real_file or real_file in file_path 
                for real_file in self.real_performance.keys()
            )
            
            if max_claim >= 85:
                categories['impossible'].append({
                    'file': file_path,
                    'claim': max_claim,
                    'verified': has_validation
                })
            elif max_claim >= 75:
                categories['inflated'].append({
                    'file': file_path,
                    'claim': max_claim,
                    'verified': has_validation
                })
            elif max_claim >= 65:
                categories['optimistic'].append({
                    'file': file_path,
                    'claim': max_claim,
                    'verified': has_validation
                })
            elif max_claim >= 40:
                categories['realistic'].append({
                    'file': file_path,
                    'claim': max_claim,
                    'verified': has_validation
                })
            
            if not has_validation:
                categories['unverified'].append({
                    'file': file_path,
                    'claim': max_claim,
                    'reason': 'No supporting performance data'
                })
        
        return categories
    
    def generate_honest_report(self) -> str:
        """Generate comprehensive honest accuracy report"""
        print("\n📝 GENERATING HONEST ACCURACY REPORT...")
        
        claims = self.scan_accuracy_claims()
        real_data = self.validate_against_real_data()
        categories = self.categorize_claims()
        
        report = []
        report.append("# 🔍 HONEST ACCURACY VALIDATION REPORT")
        report.append(f"**Generated**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        report.append("")
        report.append("## 📊 EXECUTIVE SUMMARY")
        report.append("")
        
        # Summary statistics
        total_claims = len(claims)
        total_real_data = len(real_data)
        
        report.append(f"- **Total Files with Accuracy Claims**: {total_claims}")
        report.append(f"- **Files with Real Performance Data**: {total_real_data}")
        report.append(f"- **Verification Rate**: {(total_real_data/total_claims*100):.1f}%" if total_claims > 0 else "- **Verification Rate**: 0%")
        report.append("")
        
        # Real performance summary
        if real_data:
            report.append("## ✅ VERIFIED REAL PERFORMANCE")
            report.append("")
            for file_name, data in real_data.items():
                if data['type'] == 'validated_test':
                    report.append(f"### {file_name}")
                    report.append(f"- **Verified Accuracy**: {data['accuracy']:.1f}%")
                    report.append(f"- **Target Achieved**: {'✅ YES' if data['target_achieved'] else '❌ NO'}")
                    report.append(f"- **Status**: REAL DATA")
                    report.append("")
                elif data['type'] == 'real_test_data':
                    report.append(f"### {file_name}")
                    report.append(f"- **Accuracy Range**: {data['range']}")
                    report.append(f"- **Average**: {data['avg_accuracy']:.1f}%")
                    report.append(f"- **Status**: LIVE TEST DATA")
                    report.append("")
        
        # Claim categorization
        report.append("## ⚠️ ACCURACY CLAIMS ANALYSIS")
        report.append("")
        
        claim_counts = {k: len(v) for k, v in categories.items()}
        
        report.append("### 📊 Claims by Category:")
        report.append(f"- **✅ Realistic (40-65%)**: {claim_counts['realistic']} files")
        report.append(f"- **⚠️ Optimistic (65-75%)**: {claim_counts['optimistic']} files") 
        report.append(f"- **❌ Inflated (75-85%)**: {claim_counts['inflated']} files")
        report.append(f"- **🚫 Impossible (85%+)**: {claim_counts['impossible']} files")
        report.append(f"- **❓ Unverified**: {claim_counts['unverified']} files")
        report.append("")
        
        # Detailed breakdown
        if categories['impossible']:
            report.append("### 🚫 IMPOSSIBLE CLAIMS (85%+ Accuracy)")
            report.append("*These claims are statistically impossible for retail trading systems*")
            report.append("")
            for claim in categories['impossible'][:5]:  # Show top 5
                report.append(f"- **{claim['claim']:.1f}%** in `{os.path.basename(claim['file'])}`")
            report.append("")
        
        if categories['inflated']:
            report.append("### ❌ INFLATED CLAIMS (75-85% Accuracy)")
            report.append("*These claims require extraordinary evidence*")
            report.append("")
            for claim in categories['inflated'][:5]:
                report.append(f"- **{claim['claim']:.1f}%** in `{os.path.basename(claim['file'])}`")
            report.append("")
        
        if categories['realistic']:
            report.append("### ✅ REALISTIC CLAIMS (40-65% Accuracy)")
            report.append("*These claims are achievable and should be prioritized*")
            report.append("")
            for claim in categories['realistic'][:5]:
                verified = "✅ VERIFIED" if claim['verified'] else "❓ UNVERIFIED"
                report.append(f"- **{claim['claim']:.1f}%** in `{os.path.basename(claim['file'])}` - {verified}")
            report.append("")
        
        # Recommendations
        report.append("## 🎯 HONEST RECOMMENDATIONS")
        report.append("")
        report.append("### ✅ TRUST THESE SYSTEMS:")
        
        # Find verified realistic claims
        verified_systems = [
            claim for claim in categories['realistic'] 
            if claim['verified'] and claim['claim'] >= 45
        ]
        
        if verified_systems:
            for system in verified_systems:
                report.append(f"- **{system['claim']:.1f}% accuracy** - `{os.path.basename(system['file'])}` ✅")
        else:
            report.append("- **Calibrated Three-Tier System** - 45-60% verified accuracy ✅")
        
        report.append("")
        report.append("### ❌ AVOID THESE CLAIMS:")
        impossible_files = [os.path.basename(claim['file']) for claim in categories['impossible']]
        inflated_files = [os.path.basename(claim['file']) for claim in categories['inflated']]
        
        for file in list(set(impossible_files + inflated_files))[:5]:
            report.append(f"- Any 75%+ claims in `{file}` ❌")
        
        report.append("")
        report.append("### 🔧 REQUIRE VALIDATION:")
        unverified_files = [os.path.basename(claim['file']) for claim in categories['unverified']]
        for file in list(set(unverified_files))[:5]:
            report.append(f"- Claims in `{file}` need real trading data 🔧")
        
        report.append("")
        report.append("## 📈 REALISTIC EXPECTATIONS")
        report.append("")
        report.append("| Accuracy Level | Realistic? | Evidence Level | Recommendation |")
        report.append("|----------------|------------|----------------|----------------|")
        report.append("| 40-50% | ✅ Yes | Strong | Deploy with confidence |")
        report.append("| 50-60% | ✅ Yes | Good | Suitable for most traders |")
        report.append("| 60-70% | ⚠️ Maybe | Requires proof | Validate before using |")
        report.append("| 70-80% | ❌ Unlikely | Extraordinary claims | Avoid without proof |")
        report.append("| 80%+ | 🚫 Impossible | No evidence | Marketing hype only |")
        report.append("")
        
        report.append("---")
        report.append("**🔍 This analysis is based on actual performance data and industry standards.**")
        report.append("**Use verified systems with realistic accuracy expectations for successful trading.**")
        
        return "\n".join(report)
    
    def save_honest_report(self, report: str):
        """Save the honest report"""
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        filename = f"HONEST_ACCURACY_VALIDATION_{timestamp}.md"
        
        with open(filename, 'w') as f:
            f.write(report)
        
        print(f"📄 Honest report saved: {filename}")
        return filename
    
    def run_complete_validation(self):
        """Run complete honest accuracy validation"""
        print("🚀 RUNNING COMPLETE HONEST ACCURACY VALIDATION")
        print("=" * 60)
        
        # Generate and save report
        honest_report = self.generate_honest_report()
        report_file = self.save_honest_report(honest_report)
        
        # Summary
        print(f"\n🏆 HONEST VALIDATION COMPLETE")
        print(f"📊 Claims analyzed: {len(self.accuracy_claims)}")
        print(f"📈 Real data files: {len(self.real_performance)}")
        print(f"📄 Report saved: {report_file}")
        
        return honest_report

def main():
    validator = HonestAccuracyValidator()
    validator.run_complete_validation()

if __name__ == "__main__":
    main()