#!/bin/bash

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

clear
echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}         MANUAL PYTHON PACKAGES INSTALLER${NC}"
echo -e "${CYAN}================================================================${NC}"
echo ""
echo -e "${YELLOW}This script helps install Python packages manually when${NC}"
echo -e "${YELLOW}pip has network connectivity issues.${NC}"
echo ""

# Function to try different installation methods
install_package() {
    local package=$1
    local description=$2
    
    echo -e "${BLUE}Installing $package ($description)...${NC}"
    
    # Method 1: Standard pip with retries
    echo -e "${YELLOW}  Method 1: pip with retries${NC}"
    pip3 install --timeout=300 --retries=5 --no-cache-dir "$package"
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}  ✅ $package installed successfully${NC}"
        return 0
    fi
    
    # Method 2: pip with different index
    echo -e "${YELLOW}  Method 2: Alternative PyPI mirror${NC}"
    pip3 install --timeout=300 --retries=5 --index-url https://pypi.python.org/simple/ "$package"
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}  ✅ $package installed successfully${NC}"
        return 0
    fi
    
    # Method 3: System package manager (if available)
    if command -v apt >/dev/null 2>&1; then
        echo -e "${YELLOW}  Method 3: Using apt (Ubuntu/Debian)${NC}"
        case $package in
            "requests")
                sudo apt update && sudo apt install -y python3-requests
                ;;
            "pandas")
                sudo apt update && sudo apt install -y python3-pandas
                ;;
            "openpyxl")
                sudo apt update && sudo apt install -y python3-openpyxl
                ;;
        esac
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}  ✅ $package installed via apt${NC}"
            return 0
        fi
    fi
    
    # Method 4: Conda (if available)
    if command -v conda >/dev/null 2>&1; then
        echo -e "${YELLOW}  Method 4: Using conda${NC}"
        conda install -y "$package"
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}  ✅ $package installed via conda${NC}"
            return 0
        fi
    fi
    
    echo -e "${RED}  ❌ Failed to install $package${NC}"
    return 1
}

# Check Python
if ! command -v python3 >/dev/null 2>&1; then
    echo -e "${RED}ERROR: Python 3 is not installed${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Python 3 is available${NC}"

# Check pip
if ! command -v pip3 >/dev/null 2>&1; then
    echo -e "${RED}ERROR: pip3 is not available${NC}"
    echo -e "${YELLOW}Please install pip3 first${NC}"
    exit 1
fi

echo -e "${GREEN}✓ pip3 is available${NC}"
echo ""

# Update pip first
echo -e "${BLUE}Updating pip...${NC}"
pip3 install --upgrade pip --timeout=300 --retries=3
echo ""

# Install required packages
echo -e "${CYAN}Installing required packages for market data downloader...${NC}"
echo ""

# Essential packages
packages=(
    "requests:HTTP library for API calls"
    "pandas:Data analysis and CSV handling"
    "openpyxl:Excel file support"
    "xlsxwriter:Excel file writing"
)

failed_packages=()

for package_info in "${packages[@]}"; do
    IFS=':' read -r package description <<< "$package_info"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    # Check if already installed
    python3 -c "import $package" >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ $package is already installed${NC}"
        continue
    fi
    
    # Try to install
    install_package "$package" "$description"
    if [ $? -ne 0 ]; then
        failed_packages+=("$package")
    fi
    
    echo ""
done

echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}                    INSTALLATION SUMMARY${NC}"
echo -e "${CYAN}================================================================${NC}"

# Final verification
echo -e "${BLUE}Verifying installations...${NC}"
echo ""

all_good=true
for package_info in "${packages[@]}"; do
    IFS=':' read -r package description <<< "$package_info"
    python3 -c "import $package" >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ $package: OK${NC}"
    else
        echo -e "${RED}❌ $package: MISSING${NC}"
        all_good=false
    fi
done

echo ""

if [ "$all_good" = true ]; then
    echo -e "${GREEN}🎉 ALL PACKAGES INSTALLED SUCCESSFULLY!${NC}"
    echo ""
    echo -e "${YELLOW}You can now run:${NC}"
    echo -e "${CYAN}  ./download_indian_market_data.sh${NC}"
    echo -e "${CYAN}  ./download_market_data_lightweight.sh${NC}"
    echo ""
else
    echo -e "${RED}⚠️  Some packages failed to install${NC}"
    echo ""
    echo -e "${YELLOW}Manual alternatives:${NC}"
    echo ""
    
    if [[ " ${failed_packages[@]} " =~ " requests " ]]; then
        echo -e "${BLUE}For requests:${NC}"
        echo -e "${WHITE}  # Ubuntu/Debian: sudo apt install python3-requests${NC}"
        echo -e "${WHITE}  # macOS: brew install python-requests${NC}"
        echo ""
    fi
    
    if [[ " ${failed_packages[@]} " =~ " pandas " ]]; then
        echo -e "${BLUE}For pandas:${NC}"
        echo -e "${WHITE}  # Ubuntu/Debian: sudo apt install python3-pandas${NC}"
        echo -e "${WHITE}  # macOS: brew install python-pandas${NC}"
        echo ""
    fi
    
    echo -e "${YELLOW}Alternatively, you can use the lightweight version:${NC}"
    echo -e "${CYAN}  ./download_market_data_lightweight.sh${NC}"
    echo -e "${WHITE}  (Uses only built-in Python modules)${NC}"
fi

echo -e "${CYAN}================================================================${NC}"