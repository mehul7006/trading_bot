# 🚀 Telegram Stock Bot - Deployment Guide

## Quick Start

Your Telegram Stock Bot is ready to deploy! Here's how to get it running:

### 1. Prerequisites Check
- ✅ Java 11+ installed
- ✅ Maven 3.6+ installed
- ✅ Internet connection for API calls

### 2. Build and Run

**Option A: Using the provided scripts**

**Linux/Mac:**
```bash
./run.sh
```

**Windows:**
```cmd
run.bat
```

**Option B: Manual Maven commands**
```bash
# Build the project
mvn clean compile

# Run the bot
mvn exec:java -Dexec.mainClass="com.stockbot.TelegramStockBot"
```

**Option C: Create JAR and run**
```bash
# Build JAR file
mvn clean package

# Run the JAR
java -jar target/telegram-stock-bot-1.0.0.jar
```

### 3. Bot Configuration

Your bot is pre-configured with:
- **Bot Token**: `7921964521:AAGNk_jIcV9V5nvxSTdK4xSzeO_yS4AKZ9E`
- **Upstox API Key**: `73c4fa22-15b4-41fe-8f09-34fe852af4c8`
- **Access Token**: Already set (expires: check Upstox dashboard)

### 4. Testing the Bot

1. **Start the bot** using any method above
2. **Open Telegram** and search for your bot
3. **Send `/start`** to begin
4. **Test with commands**:
   - `/price RELIANCE`
   - `TCS`
   - `/search Tata`

## 📱 Bot Commands Reference

| Command | Description | Example |
|---------|-------------|---------|
| `/start` | Welcome message | `/start` |
| `/help` | Show help | `/help` |
| `/price SYMBOL` | Get stock price | `/price RELIANCE` |
| `/search QUERY` | Search stocks | `/search Tata` |
| `SYMBOL` | Direct price query | `INFY` |

## 🔧 Production Deployment

### Option 1: Background Process (Linux/Mac)
```bash
# Build the project
mvn clean package

# Run in background
nohup java -jar target/telegram-stock-bot-1.0.0.jar > bot.log 2>&1 &

# Check if running
ps aux | grep telegram-stock-bot

# View logs
tail -f bot.log
```

### Option 2: Systemd Service (Linux)

1. **Create service file**:
```bash
sudo nano /etc/systemd/system/telegram-stock-bot.service
```

2. **Add configuration**:
```ini
[Unit]
Description=Telegram Stock Bot
After=network.target

[Service]
Type=simple
User=your-username
WorkingDirectory=/path/to/bot/directory
ExecStart=/usr/bin/java -jar /path/to/bot/target/telegram-stock-bot-1.0.0.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

3. **Enable and start**:
```bash
sudo systemctl daemon-reload
sudo systemctl enable telegram-stock-bot
sudo systemctl start telegram-stock-bot

# Check status
sudo systemctl status telegram-stock-bot

# View logs
sudo journalctl -u telegram-stock-bot -f
```

### Option 3: Docker Deployment

1. **Create Dockerfile**:
```dockerfile
FROM openjdk:11-jre-slim

WORKDIR /app
COPY target/telegram-stock-bot-1.0.0.jar app.jar

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

2. **Build and run**:
```bash
# Build JAR first
mvn clean package

# Build Docker image
docker build -t telegram-stock-bot .

# Run container
docker run -d --name stock-bot --restart unless-stopped telegram-stock-bot

# View logs
docker logs -f stock-bot
```

## 🔍 Monitoring and Logs

### Log Locations
- **Console output**: Real-time when running directly
- **File output**: `bot.log` when using nohup
- **Systemd logs**: `journalctl -u telegram-stock-bot`
- **Docker logs**: `docker logs stock-bot`

### Key Log Messages
- `Telegram Stock Bot started successfully!` - Bot is running
- `Received message: ... from chat: ...` - User interactions
- `API Response for ...` - Upstox API calls
- `Error fetching stock data` - API issues

## ⚠️ Important Notes

### Access Token Expiry
- Your Upstox access token expires on: **Check your Upstox dashboard**
- When expired, you'll need to generate a new token
- Update the token in `UpstoxApiService.java` and redeploy

### Rate Limiting
- Upstox API has rate limits
- The bot includes delays between requests
- Monitor logs for rate limit errors

### Security
- Keep your tokens secure
- Don't commit tokens to public repositories
- Consider using environment variables for production

## 🐛 Troubleshooting

### Bot Not Responding
1. Check if the process is running
2. Verify internet connectivity
3. Check Telegram bot token validity
4. Review error logs

### "Stock symbol not found"
1. Use `/search` to find correct symbols
2. Try popular symbols: RELIANCE, TCS, INFY
3. Check if symbol exists on NSE/BSE

### API Errors
1. Verify Upstox credentials
2. Check access token expiry
3. Ensure API limits aren't exceeded
4. Test with curl:
```bash
curl -H "Authorization: Bearer YOUR_TOKEN" \
     "https://api.upstox.com/v2/market-quote/ltp?instrument_key=NSE_EQ|RELIANCE"
```

### Build Errors
1. Ensure Java 11+ is installed
2. Verify Maven is properly configured
3. Check internet connection for dependencies
4. Clear Maven cache: `mvn clean`

## 📞 Support

If you encounter issues:
1. Check the logs for specific error messages
2. Verify all credentials are correct
3. Test with simple commands first
4. Ensure your Upstox account has API access

## 🎉 Success Indicators

Your bot is working correctly when:
- ✅ Console shows "Telegram Stock Bot started successfully!"
- ✅ `/start` command returns welcome message
- ✅ `/price RELIANCE` returns current stock price
- ✅ Search functionality works with `/search Reliance`
- ✅ Direct symbol queries work (e.g., typing `TCS`)

**Your bot is now ready for production use! 🚀**