# 🔄 `/apiupdate` Command - Complete Guide

## ✅ **SUCCESSFULLY IMPLEMENTED!**

The `/apiupdate` command has been successfully added to your TelegramStockBot. This command allows you to update the Upstox access token dynamically without restarting the bot.

---

## 🚀 **How to Use**

### **Basic Usage:**
```
/apiupdate YOUR_NEW_ACCESS_TOKEN
```

### **Example:**
```
/apiupdate eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OGM5OTQwNDRlY2EzMjM3MjQyYzExMTQiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc1ODA0MTA5MiwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzU4MDYwMDAwfQ.27YiqNNgeRt-R5d0NDH04V0ZwI9a79b--U5P_g50fCY
```

---

## 🔧 **Features**

### **✅ Token Validation**
- Validates JWT token format
- Checks token structure (3 parts separated by dots)
- Ensures minimum token length

### **✅ API Testing**
- Tests new token with Upstox API
- Verifies connectivity before saving
- Reverts to old token if validation fails

### **✅ Persistent Storage**
- Saves token to `upstox_token.txt` file
- Automatically loads saved token on bot startup
- Survives bot restarts

### **✅ Security Features**
- Reminds users to delete their message after updating
- Validates token before replacing current one
- Maintains old token if new one fails

### **✅ User-Friendly Messages**
- Clear success/failure notifications
- Detailed error messages with troubleshooting tips
- Step-by-step guidance for token generation

---

## 📱 **Command Flow**

### **1. User sends command:**
```
/apiupdate eyJ0eXAiOiJKV1QiLCJrZXlfaWQi...
```

### **2. Bot validates token:**
```
🔄 Updating Upstox Access Token...

⏳ Validating new token...
🧪 Testing API connectivity...
💾 Saving for future use...
```

### **3. Success response:**
```
✅ Token Updated Successfully!

🎉 Your Upstox access token has been updated and saved.
🔗 API connectivity verified
💾 Token saved for future sessions

🚀 You can now use all trading features:
• Live stock prices
• Real-time market data
• Options analysis
• Technical analysis

⚠️ Security Tip: Please delete your message containing the token!
```

### **4. Automatic test:**
```
🧪 Testing new token...
📈 TCS: ₹3,456.78 | Change: +23.45 (+0.68%) | ⏰ 14:30:15
```

---

## 🛡️ **Security Features**

### **Token Protection:**
- Token is validated before storage
- Old token is preserved if new one fails
- File-based storage with proper error handling

### **User Guidance:**
- Reminds users to delete token messages
- Provides clear security warnings
- Validates token format before processing

---

## 🔍 **Error Handling**

### **Invalid Usage:**
```
❌ Usage: /apiupdate YOUR_NEW_TOKEN

📝 Example:
/apiupdate eyJ0eXAiOiJKV1QiLCJrZXlfaWQi...

💡 How to get token:
1. Login to Upstox Developer Console
2. Generate new access token
3. Copy the token and use this command

⚠️ Security: Delete your message after updating!
```

### **Invalid Token Format:**
```
❌ Invalid Token Format

The token seems too short. Please ensure you copied the complete access token.

🔍 Token should look like:
eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ...
```

### **Token Validation Failed:**
```
❌ Token Update Failed

The new token could not be validated. Possible reasons:
• Token format is invalid
• Token has expired
• Network connectivity issues
• Upstox API is down

🔄 Please try:
1. Generate a fresh token from Upstox
2. Ensure you copied the complete token
3. Try again in a few minutes

📞 Current token remains active
```

---

## 🔄 **How to Get New Upstox Token**

### **Step 1: Login to Upstox**
1. Go to [Upstox Developer Console](https://developer.upstox.com/)
2. Login with your credentials

### **Step 2: Generate Token**
1. Navigate to "API Keys" section
2. Click "Generate Access Token"
3. Complete the authentication process

### **Step 3: Copy Token**
1. Copy the complete access token
2. It should start with `eyJ0eXAiOiJKV1Q...`
3. Make sure to copy the entire token

### **Step 4: Update in Bot**
1. Send `/apiupdate` command with your token
2. Wait for confirmation
3. Delete your message for security

---

## 📊 **Technical Implementation**

### **Files Modified:**
- ✅ `TelegramStockBot.java` - Added command handler
- ✅ `SimpleTokenManager.java` - Added token update methods

### **New Methods Added:**
- `handleApiUpdateCommand()` - Main command handler
- `updateAccessToken()` - Token update logic
- `isValidTokenFormat()` - Token validation
- `testNewToken()` - API connectivity test
- `saveTokenToFile()` - Persistent storage
- `loadTokenFromFile()` - Startup token loading

### **Features:**
- ✅ Real-time token validation
- ✅ API connectivity testing
- ✅ Persistent file storage
- ✅ Automatic startup loading
- ✅ Comprehensive error handling
- ✅ Security reminders

---

## 🎯 **Usage Examples**

### **Daily Token Update:**
```
# Every morning when Upstox token expires:
/apiupdate eyJ0eXAiOiJKV1QiLCJrZXlfaWQi...

# Bot responds:
✅ Token Updated Successfully!
🧪 Testing new token...
📈 TCS: ₹3,456.78 | Change: +23.45 (+0.68%)
```

### **Emergency Token Update:**
```
# When API calls start failing:
/apiupdate eyJ0eXAiOiJKV1QiLCJrZXlfaWQi...

# Bot validates and updates immediately
```

---

## 🎉 **Benefits**

### **✅ No Bot Restart Required**
- Update tokens on-the-fly
- Immediate effect
- Zero downtime

### **✅ Persistent Storage**
- Survives bot restarts
- Automatic loading on startup
- File-based backup

### **✅ User-Friendly**
- Simple command syntax
- Clear feedback messages
- Helpful error guidance

### **✅ Secure**
- Token validation before storage
- API testing before activation
- Security reminders for users

---

## 🚀 **Ready to Use!**

Your `/apiupdate` command is now fully functional and ready for daily use. Simply generate a new Upstox access token and update it using this command whenever needed!

**Test it now:**
```
/apiupdate YOUR_FRESH_TOKEN
```