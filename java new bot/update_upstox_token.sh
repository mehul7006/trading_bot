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
echo -e "${CYAN}          UPSTOX ACCESS TOKEN UPDATER - LINUX/MACOS${NC}"
echo -e "${CYAN}================================================================${NC}"
echo ""
echo -e "${YELLOW}This script will update the Upstox access token across all files${NC}"
echo -e "${YELLOW}in your trading bot workspace automatically.${NC}"
echo ""
echo -e "${BLUE}Current files that will be updated:${NC}"
echo -e "${BLUE}- .env file${NC}"
echo -e "${BLUE}- application.properties${NC}"
echo -e "${BLUE}- application-secure.properties${NC}"
echo -e "${BLUE}- set_environment.sh and .bat files${NC}"
echo -e "${BLUE}- All Java source files with hardcoded tokens${NC}"
echo -e "${BLUE}- Compiled class files${NC}"
echo -e "${BLUE}- Shell scripts${NC}"
echo ""
echo -e "${CYAN}================================================================${NC}"
echo ""

# Function to validate token format
validate_token() {
    if [[ $1 =~ ^eyJ0eXAiOiJKV1Q ]]; then
        return 0
    else
        return 1
    fi
}

# Function to input token with validation
input_token() {
    while true; do
        echo -e "${PURPLE}Paste your new Upstox access token here and press Enter:${NC}"
        read -r NEW_TOKEN
        
        if [ -z "$NEW_TOKEN" ]; then
            echo ""
            echo -e "${RED}ERROR: No token provided! Please paste your token.${NC}"
            echo ""
            continue
        fi
        
        if validate_token "$NEW_TOKEN"; then
            break
        else
            echo ""
            echo -e "${RED}ERROR: Invalid token format! Token should start with 'eyJ0eXAiOiJKV1Q'${NC}"
            echo -e "${RED}Please check your token and try again.${NC}"
            echo ""
        fi
    done
}

# Get the new token
input_token

echo ""
echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}Starting token update process...${NC}"
echo -e "${CYAN}================================================================${NC}"
echo ""

# Counter for updated files
TOTAL_UPDATED=0

# Update .env file
echo -e "${YELLOW}[1/8] Updating .env file...${NC}"
if [ -f ".env" ]; then
    sed -i.bak "s/UPSTOX_ACCESS_TOKEN=eyJ0eXAiOiJKV1Q[^=]*/UPSTOX_ACCESS_TOKEN=$NEW_TOKEN/g" .env
    rm -f .env.bak 2>/dev/null
    echo -e "${GREEN}     ✓ .env file updated${NC}"
    ((TOTAL_UPDATED++))
else
    echo "UPSTOX_ACCESS_TOKEN=$NEW_TOKEN" > .env
    echo -e "${GREEN}     ✓ .env file created${NC}"
    ((TOTAL_UPDATED++))
fi

# Update application.properties (source)
echo -e "${YELLOW}[2/8] Updating application.properties...${NC}"
if [ -f "src/main/resources/application.properties" ]; then
    sed -i.bak "s/upstox\.access\.token=eyJ0eXAiOiJKV1Q[^=]*/upstox.access.token=$NEW_TOKEN/g" src/main/resources/application.properties
    rm -f src/main/resources/application.properties.bak 2>/dev/null
    echo -e "${GREEN}     ✓ Source application.properties updated${NC}"
    ((TOTAL_UPDATED++))
fi

# Update application.properties (compiled)
if [ -f "target/classes/application.properties" ]; then
    sed -i.bak "s/upstox\.access\.token=eyJ0eXAiOiJKV1Q[^=]*/upstox.access.token=$NEW_TOKEN/g" target/classes/application.properties
    rm -f target/classes/application.properties.bak 2>/dev/null
    echo -e "${GREEN}     ✓ Compiled application.properties updated${NC}"
    ((TOTAL_UPDATED++))
fi

# Update application-secure.properties
echo -e "${YELLOW}[3/8] Updating application-secure.properties...${NC}"
if [ -f "src/main/resources/application-secure.properties" ]; then
    sed -i.bak "s/upstox\.access\.token=eyJ0eXAiOiJKV1Q[^=]*/upstox.access.token=$NEW_TOKEN/g" src/main/resources/application-secure.properties
    rm -f src/main/resources/application-secure.properties.bak 2>/dev/null
    echo -e "${GREEN}     ✓ application-secure.properties updated${NC}"
    ((TOTAL_UPDATED++))
fi

# Update set_environment.sh
echo -e "${YELLOW}[4/8] Updating set_environment.sh...${NC}"
if [ -f "set_environment.sh" ]; then
    sed -i.bak "s/export UPSTOX_ACCESS_TOKEN=\"eyJ0eXAiOiJKV1Q[^\"]*/export UPSTOX_ACCESS_TOKEN=\"$NEW_TOKEN/g" set_environment.sh
    rm -f set_environment.sh.bak 2>/dev/null
    echo -e "${GREEN}     ✓ set_environment.sh updated${NC}"
    ((TOTAL_UPDATED++))
fi

# Update set_environment.bat
echo -e "${YELLOW}[5/8] Updating set_environment.bat...${NC}"
if [ -f "set_environment.bat" ]; then
    sed -i.bak "s/set UPSTOX_ACCESS_TOKEN=eyJ0eXAiOiJKV1Q[^=]*/set UPSTOX_ACCESS_TOKEN=$NEW_TOKEN/g" set_environment.bat
    rm -f set_environment.bat.bak 2>/dev/null
    echo -e "${GREEN}     ✓ set_environment.bat updated${NC}"
    ((TOTAL_UPDATED++))
fi

# Update Java source files
echo -e "${YELLOW}[6/8] Updating Java source files...${NC}"
JAVA_COUNT=0
find src -name "*.java" -type f | while read -r file; do
    if grep -q "eyJ0eXAiOiJKV1Q" "$file"; then
        sed -i.bak "s/eyJ0eXAiOiJKV1Q[^\"]*/$NEW_TOKEN/g" "$file"
        rm -f "$file.bak" 2>/dev/null
        echo "       → Updated: $(basename "$file")"
        ((JAVA_COUNT++))
    fi
done
echo -e "${GREEN}     ✓ Java source files updated${NC}"

# Update shell scripts
echo -e "${YELLOW}[7/8] Updating shell scripts...${NC}"
SCRIPT_COUNT=0
find . -name "*.sh" -type f ! -name "update_upstox_token.sh" | while read -r file; do
    if grep -q "eyJ0eXAiOiJKV1Q" "$file"; then
        sed -i.bak "s/eyJ0eXAiOiJKV1Q[^\"]*/$NEW_TOKEN/g" "$file"
        rm -f "$file.bak" 2>/dev/null
        echo "       → Updated: $(basename "$file")"
        ((SCRIPT_COUNT++))
    fi
done
echo -e "${GREEN}     ✓ Shell scripts updated${NC}"

# Update batch files
echo -e "${YELLOW}[8/8] Updating batch files...${NC}"
BAT_COUNT=0
find . -name "*.bat" -type f ! -name "update_upstox_token.bat" | while read -r file; do
    if grep -q "eyJ0eXAiOiJKV1Q" "$file"; then
        sed -i.bak "s/eyJ0eXAiOiJKV1Q[^=]*/$NEW_TOKEN/g" "$file"
        rm -f "$file.bak" 2>/dev/null
        echo "       → Updated: $(basename "$file")"
        ((BAT_COUNT++))
    fi
done
echo -e "${GREEN}     ✓ Batch files updated${NC}"

echo ""
echo -e "${CYAN}================================================================${NC}"
echo -e "${GREEN}              TOKEN UPDATE COMPLETED SUCCESSFULLY!${NC}"
echo -e "${CYAN}================================================================${NC}"
echo ""

echo -e "${BLUE}Summary:${NC}"
echo -e "${BLUE}- Configuration files updated${NC}"
echo -e "${BLUE}- Java source files updated${NC}"
echo -e "${BLUE}- Shell scripts updated${NC}"
echo -e "${BLUE}- Batch files updated${NC}"
echo -e "${BLUE}- Environment files updated${NC}"
echo ""

echo -e "${PURPLE}Your new token: ${NEW_TOKEN:0:50}...${NC}"
echo -e "${PURPLE}Token expiry: Check your Upstox dashboard for expiration time${NC}"
echo ""

echo -e "${YELLOW}Next steps:${NC}"
echo -e "${YELLOW}1. You can now run your trading bot${NC}"
echo -e "${YELLOW}2. Test API connection: ./test_live_api_prices.sh${NC}"
echo -e "${YELLOW}3. Start main bot: ./start_bot.sh${NC}"
echo ""

echo -e "${CYAN}================================================================${NC}"
echo ""

# Final verification
echo -e "${BLUE}Performing final verification...${NC}"
NEW_TOKEN_COUNT=$(find . -type f \( -name "*.java" -o -name "*.sh" -o -name "*.bat" -o -name "*.properties" -o -name ".env" \) -exec grep -l "$NEW_TOKEN" {} \; 2>/dev/null | wc -l)
echo -e "${GREEN}✓ Files with new token: $NEW_TOKEN_COUNT${NC}"

echo ""
echo -e "${GREEN}🎉 Token update process completed successfully!${NC}"
echo ""

# Make the script pause for user to read
echo -e "${CYAN}Press Enter to continue...${NC}"
read -r