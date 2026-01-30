#!/bin/bash

echo "🚀 COMPREHENSIVE CALL MONITORING SYSTEM - COMPILATION & LAUNCH"
echo "=============================================================="

# Navigate to the correct directory
cd "java new bot"

echo "📁 Current directory: $(pwd)"
echo ""

# Compile all monitoring system classes
echo "🔧 Compiling monitoring system classes..."

# Core monitoring classes
javac -cp "lib/*:." src/main/java/com/trading/bot/monitoring/*.java

# Main launcher
javac -cp "lib/*:src/main/java:." CallMonitoringSystemLauncher.java

# Check compilation status
if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🚀 Starting Comprehensive Call Monitoring System..."
    echo ""
    
    # Run the system
    java -cp "lib/*:src/main/java:." CallMonitoringSystemLauncher
    
else
    echo "❌ Compilation failed!"
    echo "Please check for any missing dependencies or syntax errors."
    exit 1
fi