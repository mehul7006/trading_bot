#!/bin/bash

# SECURITY FIX - Set Environment Variables
# Run this script before starting your bot

echo "🔒 Setting up secure environment variables..."

# Set Telegram Bot Token
export TELEGRAM_BOT_TOKEN="7921964521:AAGNk_jIcV9V5nvxSTdK4xSzeO_yS4AKZ9E"

# Set Upstox API Credentials
export UPSTOX_API_KEY="768a303b-80f1-46d6-af16-f847f9341213"
export UPSTOX_API_SECRET="40s7mnlm8f"
export UPSTOX_ACCESS_TOKEN="eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzNkIyWlgiLCJqdGkiOiI2OTEwYjVjNjAwMGE4YzY0YWM5OGZjYzgiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6ZmFsc2UsImlhdCI6MTc2MjcwMjc5MCwiaXNzIjoidWRhcGktZ2F0ZXdheS1zZXJ2aWNlIiwiZXhwIjoxNzYyNzI1NjAwfQ.DtaAeJFxSiIYyh8ZC9BV8vo8UczBu6DY8u3aWOsSdMY"

echo "✅ Environment variables set successfully!"
echo "🚀 Now you can run your bot securely:"
echo "   java -jar target/stockbot.jar"
echo ""
echo "⚠️  IMPORTANT: Keep this script private and secure!"
echo "⚠️  DO NOT commit this script to version control!"