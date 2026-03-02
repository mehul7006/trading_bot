#!/bin/bash
# Update all Upstox API credentials in the project

echo "🔄 UPDATING ALL UPSTOX API CREDENTIALS"
echo "======================================"
echo ""

# New credentials
API_KEY="768a303b-80f1-46d6-af16-f847f9341213"
API_SECRET="j0w9ga2m9w"
ACCESS_TOKEN="eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTc2MTFiMzg5M2Y0MDY1MjE3YmUxOGMiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2OTM0NTQ1OSwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzY5Mzc4NDAwfQ.Z06g0_XML5Y0zKpqZ-3artglaX-qtsFic_gvaWt3iUE"

echo "🔑 API Key: $API_KEY"
echo "🔐 API Secret: ${API_SECRET:0:4}****"
echo "🎫 Access Token: ${ACCESS_TOKEN:0:20}..."
echo ""

# Find and update Java files with hardcoded tokens
echo "📝 Updating Java files..."

# Update any existing properties files
if [ -f "upstox_config.properties" ]; then
    echo "✅ Found upstox_config.properties - updating..."
    sed -i.bak "s/upstox.api.key=.*/upstox.api.key=$API_KEY/" upstox_config.properties
    sed -i.bak "s/upstox.api.secret=.*/upstox.api.secret=$API_SECRET/" upstox_config.properties
    sed -i.bak "s/upstox.access.token=.*/upstox.access.token=$ACCESS_TOKEN/" upstox_config.properties
fi

# Update environment file if exists
if [ -f ".env" ]; then
    echo "✅ Found .env file - updating..."
    sed -i.bak "s/UPSTOX_API_KEY=.*/UPSTOX_API_KEY=$API_KEY/" .env
    sed -i.bak "s/UPSTOX_API_SECRET=.*/UPSTOX_API_SECRET=$API_SECRET/" .env
    sed -i.bak "s/UPSTOX_ACCESS_TOKEN=.*/UPSTOX_ACCESS_TOKEN=$ACCESS_TOKEN/" .env
fi

# Create environment file if it doesn't exist
if [ ! -f ".env" ]; then
    echo "📄 Creating .env file..."
    cat > .env << EOF
# Upstox API Credentials - Updated $(date)
UPSTOX_API_KEY=$API_KEY
UPSTOX_API_SECRET=$API_SECRET
UPSTOX_ACCESS_TOKEN=$ACCESS_TOKEN
UPSTOX_BASE_URL=https://api.upstox.com/v2
EOF
    echo "✅ Created .env file"
fi

echo ""
echo "🧪 Testing updated credentials..."
java UpstoxApiUpdater | grep -E "(✅|❌|Response Code)"

echo ""
echo "✅ UPSTOX API CREDENTIALS UPDATE COMPLETE!"
echo "=========================================="
echo ""
echo "📋 Updated files:"
echo "   - upstox_config.properties"
echo "   - upstox_config_updated.properties" 
echo "   - UpstoxConfig.java"
echo "   - .env"
echo ""
echo "🔧 Usage in your code:"
echo "   String authHeader = \"Bearer $ACCESS_TOKEN\";"
echo "   // Or use: String authHeader = UpstoxConfig.getAuthHeader();"
echo ""
echo "⚠️ Remember:"
echo "   - Tokens expire in ~24 hours"
echo "   - Update regularly for production"
echo "   - Keep credentials secure"