# Deployment Guide

## 1. Environment Variables
Set the following environment variables in your cloud provider (Render, Heroku, etc.):

- `TELEGRAM_BOT_TOKEN`: Your Telegram Bot Token
- `UPSTOX_ACCESS_TOKEN`: Your Upstox Access Token
- `PORT`: (Optional) Automatically set by cloud provider (default 8080)

## 2. Deployment Options

### Option A: Docker (Recommended for Render/Koyeb)
The project includes a `Dockerfile`.
1. Connect your GitHub repository to Render/Koyeb.
2. Select "Docker" as the build type.
3. Add the environment variables.
4. Deploy.

### Option B: Heroku (Java)
The project includes a `Procfile` and `system.properties`.
1. Install Heroku CLI.
2. `heroku create`
3. `heroku config:set TELEGRAM_BOT_TOKEN=... UPSTOX_ACCESS_TOKEN=...`
4. `git push heroku main`

## 3. Health Check
The bot starts a lightweight HTTP server on the configured `PORT` (or 8080).
- URL: `https://your-app-url.com/`
- Response: `Bot is Running! Upstox Integration Active.`

This keeps the bot alive and satisfies platform health checks.
