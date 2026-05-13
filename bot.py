"""
Indian Stock Market Option Analyzer Telegram Bot
Supports: Nifty, BankNifty, Sensex, FinNifty, MidcapNifty
Commands:  /start  /help  /token <access_token>
Message:   nifty 25000 ce 19/05/2026
"""

import os
import re
import math
import logging
import asyncio
import json
import threading
from datetime import datetime, date
from pathlib import Path
from typing import Optional
from http.server import BaseHTTPRequestHandler, HTTPServer

import requests
from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup
from telegram.ext import (
    Application, CommandHandler, MessageHandler,
    CallbackQueryHandler, filters, ContextTypes
)
from dotenv import load_dotenv

load_dotenv()
logging.basicConfig(
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    level=logging.INFO
)
logger = logging.getLogger(__name__)

# ─── HEALTH CHECK SERVER ─────────────────────────────────────────────────────
class HealthCheckHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/plain')
        self.end_headers()
        self.wfile.write(b"Bot is alive!")

    def log_message(self, format, *args):
        return # Quiet logs

def run_health_server():
    port = int(os.environ.get("PORT", 10000))
    server = HTTPServer(('0.0.0.0', port), HealthCheckHandler)
    server.serve_forever()

# ─── CONFIG ───────────────────────────────────────────────────────────────────
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "")

# Token file persists across restarts
TOKEN_FILE = Path(__file__).parent / "upstox_token.json"

def _load_token() -> str:
    """Load access token from file, fallback to env."""
    if TOKEN_FILE.exists():
        try:
            data = json.loads(TOKEN_FILE.read_text())
            return data.get("access_token", "")
        except Exception:
            pass
    return os.getenv("UPSTOX_ACCESS_TOKEN", "")

def _save_token(token: str):
    """Persist token to file with timestamp."""
    TOKEN_FILE.write_text(json.dumps({
        "access_token": token,
        "updated_at": datetime.now().isoformat()
    }))

# Global mutable token (updated via /token command)
_current_token = {"value": _load_token()}

def get_access_token() -> str:
    return _current_token["value"]

def set_access_token(token: str):
    _current_token["value"] = token
    _save_token(token)

UPSTOX_ACCESS_TOKEN = os.getenv("UPSTOX_ACCESS_TOKEN", "")  # kept for compat
UPSTOX_BASE = "https://api.upstox.com/v2"

INDEX_KEYS = {
    "nifty":       "NSE_INDEX|Nifty 50",
    "banknifty":   "NSE_INDEX|Nifty Bank",
    "sensex":      "BSE_INDEX|SENSEX",
    "finnifty":    "NSE_INDEX|Nifty Fin Service",
    "midcapnifty": "NSE_INDEX|NIFTY MID SELECT",
}

INDEX_DISPLAY = {
    "nifty":       "NIFTY 50",
    "banknifty":   "BANK NIFTY",
    "sensex":      "SENSEX",
    "finnifty":    "FIN NIFTY",
    "midcapnifty": "MIDCAP NIFTY",
}

# ─── UPSTOX API ───────────────────────────────────────────────────────────────
def headers() -> dict:
    return {
        "Authorization": f"Bearer {get_access_token()}",
        "Accept": "application/json",
    }


def fetch_spot_price(index_key: str) -> Optional[float]:
    try:
        r = requests.get(
            f"{UPSTOX_BASE}/market-quote/quotes",
            headers=headers(),
            params={"instrument_key": index_key},
            timeout=8,
        )
        data = r.json()
        if data.get("status") == "success":
            return data["data"][index_key]["last_price"]
    except Exception as e:
        logger.error(f"Error fetching spot: {e}")
    return None


def fetch_option_chain(index_key: str, expiry: str) -> list:
    try:
        r = requests.get(
            f"{UPSTOX_BASE}/market-quote/option/chain",
            headers=headers(),
            params={"instrument_key": index_key, "expiry_date": expiry},
            timeout=10,
        )
        data = r.json()
        if data.get("status") == "success":
            return data.get("data", [])
    except Exception as e:
        logger.error(f"Error fetching chain: {e}")
    return []


# ─── ANALYSIS LOGIC ───────────────────────────────────────────────────────────
def calculate_pcr(chain: list) -> float:
    total_ce_oi = sum(item.get("call_options", {}).get("market_data", {}).get("oi", 0) for item in chain)
    total_pe_oi = sum(item.get("put_options", {}).get("market_data", {}).get("oi", 0) for item in chain)
    if total_ce_oi == 0: return 0.0
    return round(total_pe_oi / total_ce_oi, 2)


def run_analysis(parsed: dict) -> str:
    index_key = INDEX_KEYS.get(parsed["index"])
    if not index_key:
        return "❌ Unsupported Index."

    spot = fetch_spot_price(index_key)
    if spot is None:
        return "❌ Could not fetch spot price. Check token."

    chain = fetch_option_chain(index_key, parsed["expiry"])
    if not chain:
        return f"❌ No data found for {parsed['index'].upper()} expiry {parsed['expiry']}."

    pcr = calculate_pcr(chain)

    # Find the specific strike
    target_item = next((item for item in chain if int(item["strike_price"]) == parsed["strike"]), None)
    if not target_item:
        return f"❌ Strike {parsed['strike']} not found in chain."

    opt_data = target_item["call_options" if parsed["opt_type"] == "CE" else "put_options"]
    m_data = opt_data.get("market_data", {})

    ltp = m_data.get("last_price", 0)
    oi = m_data.get("oi", 0)
    iv = m_data.get("iv", 0)

    # Simple logic for signal
    signal = "NEUTRAL"
    if pcr > 1.2: signal = "BULLISH (High PCR)"
    elif pcr < 0.7: signal = "BEARISH (Low PCR)"

    res = (
        f"📊 *{INDEX_DISPLAY[parsed['index']]} ANALYSIS*\n"
        f"━━━━━━━━━━━━━━━\n"
        f"📍 Spot: `{spot}`\n"
        f"📅 Expiry: `{parsed['expiry']}`\n"
        f"🎯 Strike: `{parsed['strike']} {parsed['opt_type']}`\n\n"
        f"💰 LTP: `{ltp}`\n"
        f"📈 OI: `{oi}`\n"
        f"🌊 IV: `{iv}%`\n"
        f"⚖ PCR: `{pcr}`\n\n"
        f"🚦 Signal: *{signal}*"
    )
    return res


# ─── BOT HANDLERS ─────────────────────────────────────────────────────────────
async def cmd_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    welcome = (
        "👋 *Welcome to Option Analyzer Bot!*\n\n"
        "I provide real-time PCR and option data for Indian Indices.\n\n"
        "📝 *Format:* `index strike type expiry`\n"
        "Example: `nifty 25000 ce 19/05/2026`"
    )
    await update.message.reply_text(welcome, parse_mode="Markdown")


async def cmd_help(update: Update, context: ContextTypes.DEFAULT_TYPE):
    help_text = (
        "🔍 *How to use:*\n"
        "Send a message like: `banknifty 52000 pe 22/05/2026` \n\n"
        "✅ *Supported Indices:* Nifty, BankNifty, Sensex, FinNifty, MidcapNifty\n"
        "✅ *Format:* `index` `strike` `type(CE/PE)` `expiry(DD/MM/YYYY)`\n\n"
        "🔑 *Admin:* Use `/token <access_token>` to update Upstox token."
    )
    await update.message.reply_text(help_text, parse_mode="Markdown")


async def cmd_token(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not context.args:
        await update.message.reply_text("❌ Usage: `/token YOUR_ACCESS_TOKEN`")
        return

    new_token = context.args[0]
    set_access_token(new_token)
    await update.message.reply_text("✅ Upstox Access Token updated successfully!")


async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text.lower().strip()
    # Pattern: index strike type date
    pattern = r"(\w+)\s+(\d+)\s+(ce|pe)\s+(\d{2}/\d{2}/\d{4})"
    match = re.search(pattern, text)

    if not match:
        await update.message.reply_text("❌ Invalid format. Use: `nifty 25000 ce 19/05/2026`")
        return

    parsed = {
        "index":    match.group(1),
        "strike":   int(match.group(2)),
        "opt_type": match.group(3).upper(),
        "expiry":   match.group(4),
    }

    if parsed["index"] not in INDEX_KEYS:
        await update.message.reply_text(f"❌ Unsupported index: {parsed['index']}")
        return

    wait_msg = await update.message.reply_text("🔄 Analyzing market data...")

    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(None, run_analysis, parsed)

    # Inline buttons for quick actions
    flip_type = "PE" if parsed["opt_type"] == "CE" else "CE"
    flip_text = f"nifty {parsed['strike']} {flip_type} {parsed['expiry']}" \
        .replace("nifty", parsed["index"])
    keyboard = InlineKeyboardMarkup([
        [InlineKeyboardButton(
            f"🔄 Refresh",
            callback_data=f"refresh|{parsed['index']}|{parsed['strike']}|{parsed['opt_type']}|{parsed['expiry']}"
        ),
        InlineKeyboardButton(
            f"↔ Switch to {flip_type}",
            callback_data=f"refresh|{parsed['index']}|{parsed['strike']}|{flip_type}|{parsed['expiry']}"
        )]
    ])

    await wait_msg.delete()
    await update.message.reply_text(result, parse_mode="Markdown", reply_markup=keyboard)


async def handle_callback(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer("Refreshing...")

    parts = query.data.split("|")
    if parts[0] == "refresh" and len(parts) == 5:
        parsed = {
            "index":    parts[1],
            "strike":   int(parts[2]),
            "opt_type": parts[3],
            "expiry":   parts[4],
        }
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(None, run_analysis, parsed)

        flip_type = "PE" if parsed["opt_type"] == "CE" else "CE"
        keyboard = InlineKeyboardMarkup([
            [InlineKeyboardButton(
                "🔄 Refresh",
                callback_data=f"refresh|{parsed['index']}|{parsed['strike']}|{parsed['opt_type']}|{parsed['expiry']}"
            ),
            InlineKeyboardButton(
                f"↔ Switch to {flip_type}",
                callback_data=f"refresh|{parsed['index']}|{parsed['strike']}|{flip_type}|{parsed['expiry']}"
            )]
        ])
        await query.edit_message_text(result, parse_mode="Markdown", reply_markup=keyboard)


# ─── MAIN ─────────────────────────────────────────────────────────────────────
def main():
    if not TELEGRAM_BOT_TOKEN:
        raise SystemExit("❌ TELEGRAM_BOT_TOKEN not set in .env")

    # Start Health Check Server in background thread
    threading.Thread(target=run_health_server, daemon=True).start()

    app = Application.builder().token(TELEGRAM_BOT_TOKEN).build()
    app.add_handler(CommandHandler("start", cmd_start))
    app.add_handler(CommandHandler("help",  cmd_help))
    app.add_handler(CommandHandler("token", cmd_token))
    app.add_handler(CallbackQueryHandler(handle_callback))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_message))

    print("Option Analyzer Bot is LIVE! Press Ctrl+C to stop.")
    app.run_polling(allowed_updates=Update.ALL_TYPES)


if __name__ == "__main__":
    main()
