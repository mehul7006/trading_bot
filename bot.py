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
from datetime import datetime, date
from pathlib import Path
from typing import Optional
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
            safe_key = list(data["data"].keys())[0]
            return float(data["data"][safe_key].get("last_price", 0))
    except Exception as e:
        logger.error(f"fetch_spot_price error: {e}")
    return None


def fetch_option_chain(index_key: str, expiry: str) -> Optional[list]:
    try:
        r = requests.get(
            f"{UPSTOX_BASE}/option/chain",
            headers=headers(),
            params={"instrument_key": index_key, "expiry_date": expiry},
            timeout=10,
        )
        data = r.json()
        if data.get("status") == "success":
            return data.get("data", [])
    except Exception as e:
        logger.error(f"fetch_option_chain error: {e}")
    return None


# ─── BLACK-SCHOLES GREEKS (local, no external library needed) ─────────────────
def _norm_cdf(x: float) -> float:
    return 0.5 * (1.0 + math.erf(x / math.sqrt(2)))


def bs_greeks(S: float, K: float, T: float, r: float, sigma: float, opt: str):
    """
    S=spot, K=strike, T=time_to_expiry_years,
    r=risk_free_rate, sigma=IV(decimal), opt='CE'/'PE'
    Returns dict of greeks.
    """
    if T <= 0 or sigma <= 0:
        return {}
    try:
        d1 = (math.log(S / K) + (r + 0.5 * sigma ** 2) * T) / (sigma * math.sqrt(T))
        d2 = d1 - sigma * math.sqrt(T)
        n_d1 = _norm_cdf(d1)
        n_d2 = _norm_cdf(d2)
        n_nd1 = _norm_cdf(-d1)
        n_nd2 = _norm_cdf(-d2)
        pdf_d1 = math.exp(-0.5 * d1 ** 2) / math.sqrt(2 * math.pi)

        if opt == "CE":
            delta = n_d1
            price = S * n_d1 - K * math.exp(-r * T) * n_d2
        else:
            delta = n_d1 - 1
            price = K * math.exp(-r * T) * n_nd2 - S * n_nd1

        gamma = pdf_d1 / (S * sigma * math.sqrt(T))
        theta = (-(S * pdf_d1 * sigma) / (2 * math.sqrt(T))
                 - r * K * math.exp(-r * T) * (n_d2 if opt == "CE" else n_nd2)) / 365
        vega = S * pdf_d1 * math.sqrt(T) / 100

        return {
            "delta": round(delta, 4),
            "gamma": round(gamma, 6),
            "theta": round(theta, 4),
            "vega": round(vega, 4),
            "bs_price": round(price, 2),
        }
    except Exception:
        return {}


def calc_iv_approx(market_price: float, S: float, K: float, T: float, r: float, opt: str) -> float:
    """Bisection IV solver."""
    if T <= 0:
        return 0.0
    lo, hi = 0.001, 5.0
    for _ in range(50):
        mid = (lo + hi) / 2
        g = bs_greeks(S, K, T, r, mid, opt)
        bp = g.get("bs_price", 0)
        if bp < market_price:
            lo = mid
        else:
            hi = mid
        if abs(hi - lo) < 0.0001:
            break
    return round((lo + hi) / 2 * 100, 2)  # return as percentage


# ─── INPUT PARSER ─────────────────────────────────────────────────────────────
def parse_input(text: str) -> dict:
    t = text.lower().strip()

    # index
    index = None
    for k in sorted(INDEX_KEYS.keys(), key=len, reverse=True):
        if k in t:
            index = k
            break

    # option type
    opt_type = "CE" if "ce" in t else ("PE" if "pe" in t else None)

    # strike – largest 4-6 digit number
    strikes = re.findall(r'\b(\d{4,6})\b', t)
    strike = int(max(strikes, key=int)) if strikes else None

    # expiry date – DD/MM/YYYY or YYYY-MM-DD
    expiry = None
    m = re.search(r'(\d{2})[/\-](\d{2})[/\-](\d{4})', t)
    if m:
        expiry = f"{m.group(3)}-{m.group(2)}-{m.group(1)}"
    else:
        m = re.search(r'(\d{4})[/\-](\d{2})[/\-](\d{2})', t)
        if m:
            expiry = f"{m.group(1)}-{m.group(2)}-{m.group(3)}"

    return {"index": index, "strike": strike, "opt_type": opt_type, "expiry": expiry}


def validate(parsed: dict) -> list:
    errs = []
    if not parsed["index"]:
        errs.append("Index not found. Use: nifty / banknifty / sensex / finnifty")
    if not parsed["strike"]:
        errs.append("Strike price missing (e.g. 25000)")
    if not parsed["opt_type"]:
        errs.append("CE or PE not specified")
    if not parsed["expiry"]:
        errs.append("Expiry date missing (DD/MM/YYYY)")
    return errs


# ─── CORE ANALYZER ────────────────────────────────────────────────────────────
def run_analysis(parsed: dict) -> str:
    index = parsed["index"]
    strike = parsed["strike"]
    opt_type = parsed["opt_type"]
    expiry = parsed["expiry"]
    index_key = INDEX_KEYS[index]
    display = INDEX_DISPLAY[index]

    # ── Fetch live data ──
    spot = fetch_spot_price(index_key)
    chain = fetch_option_chain(index_key, expiry)

    if spot is None:
        return "❌ Could not fetch live spot price.\nCheck: market open? token valid?"
    if chain is None:
        return "❌ Could not fetch option chain.\nCheck: expiry date valid? token valid?"

    # ── Find strike row ──
    row = next((r for r in chain if int(r.get("strike_price", 0)) == strike), None)
    if not row:
        available = sorted({int(r["strike_price"]) for r in chain})
        near = [s for s in available if abs(s - strike) <= 500][:6]
        return (
            f"❌ Strike {strike} not found for expiry {expiry}.\n"
            f"Nearby strikes: {', '.join(map(str, near)) or 'none'}"
        )

    # ── Extract market & greeks data ──
    side_key = "call_options" if opt_type == "CE" else "put_options"
    opp_key  = "put_options"  if opt_type == "CE" else "call_options"

    opt_data = row.get(side_key, {})
    opp_data = row.get(opp_key, {})
    md = opt_data.get("market_data", {})
    gk = opt_data.get("option_greeks", {})

    ltp         = float(md.get("ltp", 0) or 0)
    prev_close  = float(md.get("close_price", ltp) or ltp)
    oi          = int(md.get("oi", 0) or 0)
    oi_day_chg  = int(md.get("oi_day_change", 0) or 0)
    volume      = int(md.get("volume", 0) or 0)
    bid         = float(md.get("bid_price", 0) or 0)
    ask         = float(md.get("ask_price", 0) or 0)

    # greeks from API (may be present)
    api_delta = float(gk.get("delta", 0) or 0)
    api_theta = float(gk.get("theta", 0) or 0)
    api_gamma = float(gk.get("gamma", 0) or 0)
    api_vega  = float(gk.get("vega", 0) or 0)
    api_iv    = float(gk.get("iv", 0) or 0)

    # OI of opposite side (for PCR)
    opp_md = opp_data.get("market_data", {})
    opp_oi = int(opp_md.get("oi", 0) or 0)
    call_oi_total = oi if opt_type == "CE" else opp_oi
    put_oi_total  = oi if opt_type == "PE" else opp_oi

    # ── Time to expiry ──
    try:
        exp_date = datetime.strptime(expiry, "%Y-%m-%d").date()
        today = date.today()
        dte = max((exp_date - today).days, 0)
        T = dte / 365.0
    except Exception:
        dte = 7
        T = 7 / 365.0

    # ── Local BS greeks if API greeks are zero ──
    r_rate = 0.065
    if ltp > 0 and spot > 0:
        sigma = (api_iv / 100) if api_iv > 0 else 0.15
        bs = bs_greeks(spot, strike, T, r_rate, sigma, opt_type)
        delta = api_delta or bs.get("delta", 0)
        theta = api_theta or bs.get("theta", 0)
        gamma = api_gamma or bs.get("gamma", 0)
        vega  = api_vega  or bs.get("vega", 0)
        # compute IV if not from API
        if api_iv == 0 and ltp > 0:
            iv = calc_iv_approx(ltp, spot, strike, T, r_rate, opt_type)
        else:
            iv = api_iv
    else:
        delta = theta = gamma = vega = iv = 0.0

    # ─────────────────────────────────────────────────────
    # ANALYSIS ENGINE
    # ─────────────────────────────────────────────────────

    # 1. Strike classification
    diff = strike - spot
    atm_threshold = spot * 0.004  # 0.4% = ~100 pts on Nifty
    if abs(diff) <= atm_threshold:
        strike_class = "🎯 ATM"
    elif (opt_type == "CE" and diff > 0) or (opt_type == "PE" and diff < 0):
        strike_class = f"📤 OTM ({abs(diff):.0f} pts away)"
    else:
        strike_class = f"📥 ITM ({abs(diff):.0f} pts inside)"

    # 2. Delta analysis
    abs_d = abs(delta)
    if abs_d >= 0.6:
        delta_txt = "Strong (ITM-like moves)"
    elif abs_d >= 0.4:
        delta_txt = "Moderate (good momentum)"
    elif abs_d >= 0.25:
        delta_txt = "Weak (needs big move)"
    else:
        delta_txt = "Very Weak (lottery territory)"

    # Points needed for 100% gain
    pts_double = (ltp / abs_d) if abs_d > 0.01 else 9999

    # 3. IV analysis
    if iv == 0:
        iv_txt = "N/A"
    elif iv < 10:
        iv_txt = "🟢 Very Low — Cheap to buy"
    elif iv < 16:
        iv_txt = "🟢 Low — Good to buy"
    elif iv < 22:
        iv_txt = "🟡 Normal — Moderate risk"
    elif iv < 30:
        iv_txt = "🟠 High — Expensive premium"
    else:
        iv_txt = "🔴 Very High — Avoid buying!"

    # 4. Theta analysis
    theta_daily = abs(theta)
    if theta_daily < 3:
        theta_txt = "🟢 Low decay"
    elif theta_daily < 8:
        theta_txt = "🟡 Moderate decay"
    else:
        theta_txt = "🔴 Heavy decay — hurry!"

    # 5. PCR
    pcr = (put_oi_total / call_oi_total) if call_oi_total > 0 else 0
    if pcr > 1.5:
        pcr_txt = "🟢 Very Bullish (PUT heavy)"
    elif pcr > 1.2:
        pcr_txt = "🟢 Bullish"
    elif pcr > 0.9:
        pcr_txt = "🟡 Neutral"
    elif pcr > 0.7:
        pcr_txt = "🟠 Bearish"
    else:
        pcr_txt = "🔴 Very Bearish (CALL heavy)"

    # 6. OI change signal
    if oi_day_chg > 0:
        oi_chg_txt = f"📈 +{oi_day_chg:,} (buildup)"
    elif oi_day_chg < 0:
        oi_chg_txt = f"📉 {oi_day_chg:,} (unwinding)"
    else:
        oi_chg_txt = "➡️ No change"

    # 7. Premium change
    prem_chg = ltp - prev_close
    prem_pct  = (prem_chg / prev_close * 100) if prev_close > 0 else 0

    # ─────────────────────────────────────────────────────
    # SCORING ENGINE
    # ─────────────────────────────────────────────────────
    bull = 0
    bear = 0
    bull_reasons = []
    bear_reasons  = []

    # Delta score
    if abs_d >= 0.5:
        bull += 2
        bull_reasons.append(f"Delta {delta:.2f} — Strong sensitivity to index move")
    elif abs_d >= 0.35:
        bull += 1
        bull_reasons.append(f"Delta {delta:.2f} — Decent sensitivity")
    else:
        bear += 1
        bear_reasons.append(f"Delta {delta:.2f} — Weak, needs large index move")

    # IV score
    if iv > 0:
        if iv < 15 and opt_type == "CE":
            bull += 2
            bull_reasons.append(f"IV {iv:.1f}% Low — Premium cheap, good buy")
        elif iv < 15 and opt_type == "PE":
            bull += 2
            bull_reasons.append(f"IV {iv:.1f}% Low — Premium cheap, good buy")
        elif iv > 25:
            bear += 2
            bear_reasons.append(f"IV {iv:.1f}% Very High — Overpriced, IV crush risk")

    # PCR score
    if opt_type == "CE":
        if pcr > 1.2:
            bull += 2
            bull_reasons.append(f"PCR {pcr:.2f} — Bearish sentiment, CE reversal likely")
        elif pcr < 0.7:
            bear += 1
            bear_reasons.append(f"PCR {pcr:.2f} — Call heavy, market may fall")
    else:  # PE
        if pcr < 0.7:
            bull += 2
            bull_reasons.append(f"PCR {pcr:.2f} — Call heavy, PE reversal likely")
        elif pcr > 1.5:
            bear += 1
            bear_reasons.append(f"PCR {pcr:.2f} — Already put heavy, PE may weaken")

    # OI buildup
    if oi_day_chg > 50000 and opt_type == "CE":
        bull += 1
        bull_reasons.append(f"OI buildup +{oi_day_chg:,} — Bullish interest rising")
    elif oi_day_chg > 50000 and opt_type == "PE":
        bear += 1
        bear_reasons.append(f"OI buildup +{oi_day_chg:,} — Bearish interest rising")
    elif oi_day_chg < -50000:
        if opt_type == "CE":
            bear += 1
            bear_reasons.append(f"OI unwinding {oi_day_chg:,} — CE longs exiting")
        else:
            bull += 1
            bull_reasons.append(f"OI unwinding {oi_day_chg:,} — PE shorts covering")

    # Theta penalty near expiry
    if dte <= 1:
        bear += 2
        bear_reasons.append(f"Only {dte}DTE — Extreme theta decay")
    elif dte <= 3:
        bear += 1
        bear_reasons.append(f"{dte}DTE — High theta decay risk")

    # Premium already moved a lot today
    if prem_pct > 30:
        bear += 1
        bear_reasons.append(f"Premium already up {prem_pct:.1f}% today — chase risk")
    elif prem_pct < -30:
        bull += 1
        bull_reasons.append(f"Premium down {prem_pct:.1f}% today — potential reversal")

    # ─────────────────────────────────────────────────────
    # FINAL VERDICT
    # ─────────────────────────────────────────────────────
    net = bull - bear
    if net >= 4:
        verdict     = "🟢 STRONG BULLISH — Premium likely UP ↑↑"
        confidence  = "HIGH"
        verdict_short = "UP ↑↑"
    elif net >= 2:
        verdict     = "🟢 BULLISH — Premium likely UP ↑"
        confidence  = "MEDIUM-HIGH"
        verdict_short = "UP ↑"
    elif net >= 1:
        verdict     = "🟡 SLIGHTLY BULLISH — Premium may go UP"
        confidence  = "MEDIUM"
        verdict_short = "UP (weak)"
    elif net <= -4:
        verdict     = "🔴 STRONG BEARISH — Premium likely DOWN ↓↓"
        confidence  = "HIGH"
        verdict_short = "DOWN ↓↓"
    elif net <= -2:
        verdict     = "🔴 BEARISH — Premium likely DOWN ↓"
        confidence  = "MEDIUM-HIGH"
        verdict_short = "DOWN ↓"
    elif net <= -1:
        verdict     = "🟡 SLIGHTLY BEARISH — Premium may go DOWN"
        confidence  = "MEDIUM"
        verdict_short = "DOWN (weak)"
    else:
        verdict     = "⚪ NEUTRAL — Sideways / No clear direction"
        confidence  = "LOW"
        verdict_short = "NEUTRAL ↔"

    # Trade plan
    if ltp > 0:
        target    = round(ltp * 1.45, 2)
        stoploss  = round(ltp * 0.62, 2)
        risk      = ltp - stoploss
        reward    = target - ltp
        rr        = round(reward / risk, 1) if risk > 0 else 0
    else:
        target = stoploss = rr = 0

    # ─────────────────────────────────────────────────────
    # FORMAT RESPONSE
    # ─────────────────────────────────────────────────────
    bull_str = "\n".join(f"  ✅ {r}" for r in bull_reasons) or "  None"
    bear_str = "\n".join(f"  ❌ {r}" for r in bear_reasons) or "  None"

    now = datetime.now().strftime("%d-%b-%Y %H:%M:%S")

    msg = f"""━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 *OPTION ANALYSIS REPORT*
━━━━━━━━━━━━━━━━━━━━━━━━━━━
🏷 *{display} {strike} {opt_type}*
📅 Expiry  : {expiry}
⏰ Time    : {now}
━━━━━━━━━━━━━━━━━━━━━━━━━━━
💰 *LIVE PRICE DATA*
  Spot Price    : ₹{spot:,.2f}
  Option LTP    : ₹{ltp:.2f}
  Prev Close    : ₹{prev_close:.2f}
  Change        : ₹{prem_chg:+.2f} ({prem_pct:+.1f}%)
  Bid / Ask     : ₹{bid:.1f} / ₹{ask:.1f}
  Strike Type   : {strike_class}
  DTE           : {dte} days to expiry
━━━━━━━━━━━━━━━━━━━━━━━━━━━
🔢 *OPTION GREEKS*
  Delta  : {delta:+.4f} → {delta_txt}
  Theta  : {theta:.4f} (₹{theta_daily:.1f}/day) → {theta_txt}
  IV     : {iv:.1f}% → {iv_txt}
  Gamma  : {gamma:.6f}
  Vega   : {vega:.4f}
  Pts to 2x premium : ~{pts_double:.0f} pts
━━━━━━━━━━━━━━━━━━━━━━━━━━━
📈 *OI & MARKET DATA*
  This Strike OI  : {oi:,}
  OI Day Change   : {oi_chg_txt}
  Call OI (chain) : {call_oi_total:,}
  Put OI (chain)  : {put_oi_total:,}
  PCR Ratio       : {pcr:.2f} → {pcr_txt}
  Volume          : {volume:,}
━━━━━━━━━━━━━━━━━━━━━━━━━━━
🧠 *SIGNAL ANALYSIS*
Score: Bull {bull} vs Bear {bear}

*Bullish signals:*
{bull_str}

*Bearish signals:*
{bear_str}
━━━━━━━━━━━━━━━━━━━━━━━━━━━
🎯 *FINAL VERDICT*
{verdict}
Confidence : *{confidence}*
━━━━━━━━━━━━━━━━━━━━━━━━━━━
💡 *SUGGESTED TRADE PLAN*
  Entry    : ₹{ltp:.2f}
  Target   : ₹{target:.2f}
  Stoploss : ₹{stoploss:.2f}
  R:R Ratio: 1:{rr}
━━━━━━━━━━━━━━━━━━━━━━━━━━━
⚠️ _Educational use only._
_Not financial advice._
_Trade at your own risk._
━━━━━━━━━━━━━━━━━━━━━━━━━━━"""

    return msg


# ─── TELEGRAM HANDLERS ────────────────────────────────────────────────────────
async def cmd_token(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """
    /token <your_upstox_access_token>
    Updates the Upstox access token used for all API calls.
    """
    args = context.args
    if not args:
        # Show current token status (masked)
        tok = get_access_token()
        if tok:
            masked = tok[:8] + "..." + tok[-6:] if len(tok) > 14 else "****"
            loaded_at = ""
            if TOKEN_FILE.exists():
                try:
                    data = json.loads(TOKEN_FILE.read_text())
                    loaded_at = data.get("updated_at", "")[:19]
                except Exception:
                    pass
            await update.message.reply_text(
                f"🔑 *Current Token Status*\n\n"
                f"Token   : `{masked}`\n"
                f"Updated : {loaded_at or 'unknown'}\n\n"
                f"To update: `/token YOUR_NEW_TOKEN`",
                parse_mode="Markdown"
            )
        else:
            await update.message.reply_text(
                "❌ No token set yet.\n\n"
                "Use: `/token YOUR_UPSTOX_ACCESS_TOKEN`",
                parse_mode="Markdown"
            )
        return

    new_token = args[0].strip()

    # Quick validation — test a lightweight API call
    test_headers = {
        "Authorization": f"Bearer {new_token}",
        "Accept": "application/json",
    }
    try:
        r = requests.get(
            f"{UPSTOX_BASE}/market-quote/quotes",
            headers=test_headers,
            params={"instrument_key": "NSE_INDEX|Nifty 50"},
            timeout=8,
        )
        resp = r.json()
        if resp.get("status") == "success":
            set_access_token(new_token)
            masked = new_token[:8] + "..." + new_token[-6:]
            await update.message.reply_text(
                f"✅ *Token Updated & Verified!*\n\n"
                f"Token   : `{masked}`\n"
                f"Saved   : {datetime.now().strftime('%d-%b-%Y %H:%M:%S')}\n\n"
                f"All future analysis will use this token.\n"
                f"_Token is saved locally — survives bot restarts._",
                parse_mode="Markdown"
            )
        else:
            err = resp.get("errors", [{}])
            err_msg = err[0].get("message", "Unknown error") if err else "Unknown"
            await update.message.reply_text(
                f"❌ *Token validation failed!*\n\n"
                f"API Response: `{err_msg}`\n\n"
                f"Please check:\n"
                f"  • Token is not expired\n"
                f"  • Token has market data permission\n"
                f"  • Token is copied completely",
                parse_mode="Markdown"
            )
    except requests.exceptions.Timeout:
        await update.message.reply_text(
            "⏱ API timeout. Token saved anyway — please verify market is open.",
        )
        set_access_token(new_token)
    except Exception as e:
        await update.message.reply_text(f"❌ Error validating token: {e}")


async def cmd_start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    tok = get_access_token()
    token_status = "✅ Token set" if tok else "❌ Token NOT set — use /token first"
    text = f"""🤖 *Welcome to Option Analyzer Bot!*

🔑 *Token Status:* {token_status}

📌 *How to use — just send:*
```
nifty 25000 ce 19/05/2026
banknifty 52000 pe 22/05/2026
sensex 80000 ce 29/05/2026
finnifty 24000 pe 19/05/2026
```

*Commands:*
  /token — Set/update Upstox access token
  /help  — Full help & examples

*I will analyze:*
  ✅ Live Premium & Spot Price
  ✅ Delta, Theta, IV, Gamma, Vega
  ✅ OI Analysis & PCR Ratio
  ✅ Strike: ITM / ATM / OTM
  ✅ Final Verdict: *UP ↑ or DOWN ↓*

⏰ Market Hours: 9:15 AM – 3:30 PM IST"""
    await update.message.reply_text(text, parse_mode="Markdown")


async def cmd_help(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = """📚 *HELP & SUPPORTED FORMATS*

*Supported Indices:*
  • `nifty`
  • `banknifty`
  • `sensex`
  • `finnifty`
  • `midcapnifty`

*Option Types:*  `ce`  or  `pe`

*Date Formats:*
  • `19/05/2026`
  • `2026-05-19`

*Full Examples:*
```
nifty 25000 ce 19/05/2026
banknifty 52000 pe 22/05/2026
sensex 80000 ce 29/05/2026
finnifty 24500 ce 2026-05-19
```

*What each signal means:*
  PCR > 1.2  → Bullish reversal likely
  PCR < 0.7  → Bearish reversal likely
  IV < 15%   → Cheap to buy options
  IV > 25%   → Expensive, IV crush risk
  Delta > 0.5 → Strong premium movement
  Low DTE    → High theta decay risk"""
    await update.message.reply_text(text, parse_mode="Markdown")


async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text or ""
    parsed = parse_input(text)
    errors = validate(parsed)

    if errors:
        err_text = "⚠️ *Invalid format:*\n" + "\n".join(f"  • {e}" for e in errors)
        err_text += "\n\n*Example:*\n`nifty 25000 ce 19/05/2026`"
        await update.message.reply_text(err_text, parse_mode="Markdown")
        return

    # Send "analyzing..." message
    wait_msg = await update.message.reply_text(
        f"⏳ Analyzing *{INDEX_DISPLAY.get(parsed['index'], parsed['index'])} "
        f"{parsed['strike']} {parsed['opt_type']}* | Expiry: {parsed['expiry']}\n\n"
        "_Fetching live data from Upstox..._",
        parse_mode="Markdown"
    )

    # Run analysis in executor to avoid blocking
    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(None, run_analysis, parsed)

    # Build inline keyboard for quick refresh / flip CE<->PE
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
    if not UPSTOX_ACCESS_TOKEN:
        raise SystemExit("❌ UPSTOX_ACCESS_TOKEN not set in .env")

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
