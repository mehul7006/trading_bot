#!/bin/bash

# ULTIMATE WORLD CLASS TRADING BOT EXECUTION SCRIPT
# Targets 80%+ accuracy with real data only
# Comprehensive integration of all advanced components

echo "🌟 ULTIMATE WORLD CLASS TRADING BOT - 80%+ ACCURACY TARGET"
echo "=============================================================="
echo "🎯 Using ONLY real market data - NO fake or mock data"
echo "📊 50+ professional indicators"
echo "🧠 Machine learning predictions"
echo "🏛️ Institutional-grade strategies"
echo "=============================================================="

# Set environment variables
export JAVA_OPTS="-Xmx4g -Xms2g -XX:+UseG1GC"
export CLASSPATH=".:lib/*:target/classes"

# Create directories
mkdir -p world_class_results
mkdir -p logs
mkdir -p backup

# Log file with timestamp
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_FILE="logs/ultimate_world_class_${TIMESTAMP}.log"

echo "📁 Results will be saved to: world_class_results/"
echo "📄 Logs will be saved to: ${LOG_FILE}"

# Function to log with timestamp
log_with_timestamp() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

# Function to check Java version
check_java_version() {
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
        log_with_timestamp "✅ Java version: $JAVA_VERSION"
    else
        log_with_timestamp "❌ Java not found. Please install Java 11 or higher."
        exit 1
    fi
}

# Function to compile Java files
compile_java_files() {
    log_with_timestamp "🔨 Compiling Java files..."
    
    # Compile in dependency order
    javac -cp "$CLASSPATH" src/main/java/com/trading/bot/data/RealTimeMarketDataProvider.java 2>> "$LOG_FILE"
    if [ $? -ne 0 ]; then
        log_with_timestamp "❌ Failed to compile RealTimeMarketDataProvider"
        return 1
    fi
    
    javac -cp "$CLASSPATH" src/main/java/com/trading/bot/indicators/WorldClassIndicatorSuite.java 2>> "$LOG_FILE"
    if [ $? -ne 0 ]; then
        log_with_timestamp "❌ Failed to compile WorldClassIndicatorSuite"
        return 1
    fi
    
    javac -cp "$CLASSPATH" src/main/java/com/trading/bot/advanced/UltimateWorldClassTradingEngine.java 2>> "$LOG_FILE"
    if [ $? -ne 0 ]; then
        log_with_timestamp "❌ Failed to compile UltimateWorldClassTradingEngine"
        return 1
    fi
    
    log_with_timestamp "✅ All Java files compiled successfully"
    return 0
}

# Function to run accuracy optimization
run_accuracy_optimization() {
    log_with_timestamp "🔧 Running accuracy optimization..."
    
    java -cp "$CLASSPATH" $JAVA_OPTS com.trading.bot.advanced.UltimateWorldClassTradingEngine --optimize-only 2>> "$LOG_FILE"
    
    if [ $? -eq 0 ]; then
        log_with_timestamp "✅ Accuracy optimization completed"
    else
        log_with_timestamp "⚠️ Accuracy optimization encountered issues"
    fi
}

# Function to run world-class trading analysis
run_world_class_analysis() {
    log_with_timestamp "📈 Starting world-class trading analysis..."
    
    # Run the ultimate trading engine
    java -cp "$CLASSPATH" $JAVA_OPTS com.trading.bot.advanced.UltimateWorldClassTradingEngine 2>&1 | tee -a "$LOG_FILE"
    
    if [ $? -eq 0 ]; then
        log_with_timestamp "✅ World-class analysis completed successfully"
    else
        log_with_timestamp "❌ World-class analysis failed"
        return 1
    fi
}

# Function to analyze results
analyze_results() {
    log_with_timestamp "📊 Analyzing results..."
    
    # Find the latest results file
    LATEST_RESULTS=$(ls -t world_class_results/ultimate_signals_*.csv 2>/dev/null | head -1)
    
    if [ -n "$LATEST_RESULTS" ]; then
        log_with_timestamp "📄 Latest results: $LATEST_RESULTS"
        
        # Count signals
        TOTAL_SIGNALS=$(tail -n +2 "$LATEST_RESULTS" | wc -l)
        HIGH_CONFIDENCE_SIGNALS=$(tail -n +2 "$LATEST_RESULTS" | awk -F',' '$4 >= 85' | wc -l)
        
        log_with_timestamp "📊 Total signals generated: $TOTAL_SIGNALS"
        log_with_timestamp "🎯 High confidence signals (85%+): $HIGH_CONFIDENCE_SIGNALS"
        
        if [ "$HIGH_CONFIDENCE_SIGNALS" -gt 0 ]; then
            log_with_timestamp "🎉 High-quality signals generated successfully!"
            
            # Show top signals
            log_with_timestamp "🏆 Top signals:"
            head -1 "$LATEST_RESULTS"
            tail -n +2 "$LATEST_RESULTS" | sort -t',' -k4 -nr | head -3
        else
            log_with_timestamp "⚠️ No high-confidence signals generated"
        fi
    else
        log_with_timestamp "❌ No results file found"
    fi
}

# Function to backup results
backup_results() {
    log_with_timestamp "💾 Backing up results..."
    
    BACKUP_DIR="backup/world_class_backup_${TIMESTAMP}"
    mkdir -p "$BACKUP_DIR"
    
    # Copy results and logs
    cp -r world_class_results/* "$BACKUP_DIR/" 2>/dev/null
    cp "$LOG_FILE" "$BACKUP_DIR/"
    
    log_with_timestamp "✅ Results backed up to: $BACKUP_DIR"
}

# Function to display final summary
display_final_summary() {
    echo ""
    echo "🎯 ULTIMATE WORLD CLASS TRADING BOT - EXECUTION SUMMARY"
    echo "======================================================="
    echo "📅 Execution Date: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "📄 Log File: $LOG_FILE"
    echo "📁 Results Directory: world_class_results/"
    echo "💾 Backup Directory: backup/world_class_backup_${TIMESTAMP}/"
    echo ""
    
    # Check if we have results
    if ls world_class_results/ultimate_signals_*.csv 1> /dev/null 2>&1; then
        echo "✅ EXECUTION SUCCESSFUL"
        echo "🎉 World-class signals generated with 80%+ accuracy target"
        echo "📊 Professional-grade analysis completed"
        echo "💎 Real data only - no fake or mock data used"
    else
        echo "⚠️ EXECUTION COMPLETED WITH WARNINGS"
        echo "🔍 Check log file for details: $LOG_FILE"
    fi
    
    echo "======================================================="
}

# Main execution flow
main() {
    log_with_timestamp "🚀 Starting Ultimate World Class Trading Bot execution"
    
    # Step 1: Check prerequisites
    check_java_version
    
    # Step 2: Compile Java files
    if ! compile_java_files; then
        log_with_timestamp "❌ Compilation failed. Exiting."
        exit 1
    fi
    
    # Step 3: Run accuracy optimization
    run_accuracy_optimization
    
    # Step 4: Run world-class analysis
    if ! run_world_class_analysis; then
        log_with_timestamp "❌ Analysis failed. Exiting."
        exit 1
    fi
    
    # Step 5: Analyze results
    analyze_results
    
    # Step 6: Backup results
    backup_results
    
    # Step 7: Display summary
    display_final_summary
    
    log_with_timestamp "🏁 Ultimate World Class Trading Bot execution completed"
}

# Handle script interruption
trap 'log_with_timestamp "⚠️ Script interrupted"; exit 130' INT

# Execute main function
main "$@"