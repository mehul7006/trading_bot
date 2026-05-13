# Option Analyzer Telegram Bot — Setup Guide

## Step 1: Create Telegram Bot

1. Open Telegram → search **@BotFather**
2. Send `/newbot`
3. Give it a name: e.g. `Option Analyzer`
4. Give it a username: e.g. `OptionAnalyzerBot`
5. Copy the **Bot Token** you receive

## Step 2: Set Your Bot Token in .env

Open `.env` file and replace:
```
TELEGRAM_BOT_TOKEN=YOUR_TELEGRAM_BOT_TOKEN_HERE
```
with the token from BotFather.

## Step 3: Get Upstox Access Token Daily

### One-time setup on Upstox Developer Portal:
1. Login → https://developer.upstox.com
2. Create App with:
   - API Key: `768a303b-80f1-46d6-af16-f847f9341213`  (already yours)
   - Redirect URL: `https://127.0.0.1/`
   - Permissions: **Market Data** (minimum required)

### Daily token generation:
Each day before market opens, get a fresh token:

**Option A — Browser method (easiest):**
1. Open this URL in browser:
```
https://api.upstox.com/v2/login/authorization/dialog?response_type=code&client_id=768a303b-80f1-46d6-af16-f847f9341213&redirect_uri=https://127.0.0.1/
```
2. Login with your Upstox account
3. You'll be redirected to something like:
   `https://127.0.0.1/?code=XXXXXXXXX`
4. Copy that `code` value
5. Run this curl to get access token:
```bash
curl -X POST https://api.upstox.com/v2/login/authorization/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "code=YOUR_CODE&client_id=768a303b-80f1-46d6-af16-f847f9341213&client_secret=j0w9ga2m9w&redirect_uri=https://127.0.0.1/&grant_type=authorization_code"
```
6. Copy `access_token` from response

**Option B — Use Python helper:**
```bash
.\.venv\Scripts\python.exe get_token.py
```

### Update token in bot (via Telegram):
Send this to your bot:
```
/token YOUR_ACCESS_TOKEN_HERE
```
Bot will validate and save it — survives restarts!

## Step 4: Install Dependencies

```powershell
# Create virtual environment
# (If python is not in your PATH, you may need to use the full path to python.exe)
python -m venv .venv --copies

# Install requirements
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

## Step 5: Run the Bot

```powershell
& ".\.venv\Scripts\python.exe" bot.py
```

## Step 6: Deploy to Render (Optional)

To keep your bot running 24/7 in the cloud:

1. **Push your code to GitHub**:
   - Your code is now on the `OptionMaster` branch at: `https://github.com/mehul7006/trading_bot.git`
2. **Login to Render** (https://render.com).
3. **Create a New Blueprint Instance**:
   - Connect your GitHub repository.
   - Render will automatically detect `render.yaml`.
4. **Configure Environment Variables** in Render Dashboard:
   - `TELEGRAM_BOT_TOKEN`
   - `UPSTOX_API_KEY`
   - `UPSTOX_API_SECRET`
   - `UPSTOX_ACCESS_TOKEN` (Initial token)
5. **Deploy!** The bot will now run as a Background Worker.

## Step 7: Use the Bot

Send messages like:
```
nifty 25000 ce 19/05/2026
banknifty 52000 pe 22/05/2026
sensex 80000 ce 29/05/2026
finnifty 24000 pe 19/05/2026
```

## Daily Workflow

```
Morning (before 9:15 AM):
  1. Get new Upstox access token
  2. Send /token NEW_TOKEN to your bot
  3. Bot is ready for the day!
```

## Troubleshooting

| Error | Fix |
|-------|-----|
| "Token not set" | Send /token YOUR_TOKEN |
| "Could not fetch spot price" | Token expired, get new one |
| "Strike not found" | Check expiry date is valid |
| "Option chain failed" | Market may be closed |
