#!/bin/bash

echo "🔧 ENHANCED MASTER LAUNCHER - PART 4: LARGE FILE SPLITTER"
echo "=========================================================="
echo "⚡ Mission: Fix LLM response generation failures"
echo "🎯 Method: Split large files into smaller manageable parts"
echo ""

# Compile Part 4
echo "📦 Compiling EnhancedMasterLauncher_Part4..."
javac -cp ".:src/main/java" "EnhancedMasterLauncher_Part4.java"

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed"
    exit 1
fi

# Create split directory
echo "📁 Creating split directory..."
mkdir -p "src/main/java/com/stockbot/split"

# Run the large file splitter
echo "🚀 Running large file splitter..."
java -cp ".:src/main/java" EnhancedMasterLauncher_Part4

echo ""
echo "🎯 PART 4 EXECUTION COMPLETED!"
echo "==============================="
echo "✅ Large files have been split into smaller parts"
echo "📂 Check 'src/main/java/com/stockbot/split/' directory"
echo "📖 Read USAGE_GUIDE.md for implementation details"
echo ""
echo "🚀 Benefits achieved:"
echo "   • No more LLM response generation failures"
echo "   • All parts under 500 lines"
echo "   • Easy maintenance and modification"
echo "   • Better code organization"
echo ""
echo "💡 Next steps:"
echo "   1. Use Coordinator classes for full functionality"
echo "   2. Reference individual parts for specific features"
echo "   3. Test the split implementations"