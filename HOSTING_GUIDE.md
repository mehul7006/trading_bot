# ☁️ How to Host Your Bot for Free (Step-by-Step)

I have prepared your bot for cloud deployment. It now includes a `Dockerfile` and a `CloudLauncher` that keeps the bot alive on cloud platforms.

## ✅ Option 1: Render + UptimeRobot (Easiest & Free)
Render offers a free tier for Web Services, but they spin down after 15 minutes of inactivity. We fix this by "pinging" the bot.

### Step 1: Push to GitHub
1. Create a new repository on GitHub.
2. Push this entire `clean_bot` folder to the repository.

### Step 2: Deploy on Render
1. Go to [dashboard.render.com](https://dashboard.render.com) and sign up/login.
2. Click **New +** -> **Web Service**.
3. Connect your GitHub repository.
4. **Settings**:
   - **Name**: `my-trading-bot`
   - **Root Directory**: `clean_bot` (IMPORTANT: Set this to the folder name where your code is!)
   - **Runtime**: **Docker** (Render will detect the Dockerfile automatically).
   - **Region**: Choose one close to India (e.g., Singapore) if available, otherwise any.
   - **Instance Type**: **Free**.
5. Click **Create Web Service**.
6. Wait for the build to finish. Once done, you will get a URL like `https://my-trading-bot.onrender.com`.

### Step 3: Keep it Alive (Crucial!)
1. Go to [uptimerobot.com](https://uptimerobot.com) (Free).
2. Create a new **Monitor**.
   - **Monitor Type**: HTTP(s).
   - **Friendly Name**: My Bot.
   - **URL**: Paste your Render URL (e.g., `https://my-trading-bot.onrender.com`).
   - **Monitoring Interval**: **5 minutes**.
3. Start the monitor. This will ping your bot every 5 minutes, preventing Render from putting it to sleep.

---

## 🛠 Option 2: Koyeb (Alternative Free Tier)
Koyeb is another modern platform with a free tier.

1. Sign up at [koyeb.com](https://www.koyeb.com).
2. Create a new **App**.
3. Select **GitHub** as the source and choose your repository.
4. Koyeb will detect the Dockerfile.
5. Deploy.
6. Koyeb's free tier is generous, but checking with UptimeRobot is still recommended.

---

## 🔑 Important: Managing Secrets
For security, **do not** commit your `upstox_token.txt` or hardcode tokens if the repo is public.
Instead, use **Environment Variables** in Render/Koyeb:
1. Go to the "Environment" tab in Render/Koyeb.
2. Add Variable: `UPSTOX_ACCESS_TOKEN` -> paste your token.
3. Add Variable: `BOT_TOKEN` -> paste your Telegram bot token.

*(Note: The current code supports `upstox_token.txt` or hardcoded values for simplicity, but using Environment Variables is the professional way.)*
