"""
Indian Stock Market Option Analyzer Telegram Bot
Supports: Nifty, BankNifty, Sensex, FinNifty, MidcapNifty
Commands:  /start  /help  /token <access_token>
Message:   nifty 25000 ce 19/05/2026
"""

import os
import re
import math
import time
import logging
import asyncio
import json
from datetime import datetime, date, time as dtime
from pathlib import Path
from typing import Optional
import requests
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup

# ─── HEALTH CHECK SERVER ─────────────────────────────────────────────────────
class HealthCheckHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/plain')
        self.end_headers()
        self.wfile.write(b"Bot is alive!")

    def do_HEAD(self):
        self.send_response(200)
        self.end_headers()

    def log_message(self, format, *args):
        return # Quiet logs

def run_health_server():
    port = int(os.environ.get("PORT", 10000))
    server = HTTPServer(('0.0.0.0', port), HealthCheckHandler)
    server.serve_forever()


def run_keepalive():
    """
    Render's FREE web service is spun down after ~15 min of no INBOUND traffic.
    A polling bot makes only outbound calls, so Render would idle (kill) it and
    the bot stops responding. This self-ping hits the public URL every 10 min to
    keep the service awake. Set SELF_PING_URL, or rely on RENDER_EXTERNAL_URL
    (auto-provided by Render).
    """
    url = os.environ.get("SELF_PING_URL") or os.environ.get("RENDER_EXTERNAL_URL")
    if not url:
        logger.info("Keep-alive: no SELF_PING_URL / RENDER_EXTERNAL_URL set — "
                    "self-ping disabled (fine for local runs).")
        return
    logger.info(f"Keep-alive: self-ping every 10 min → {url}")
    while True:
        time.sleep(600)  # 10 minutes (< Render's ~15 min idle window)
        try:
            requests.get(url, timeout=15)
            logger.info("Keep-alive ping OK")
        except Exception as e:
            logger.warning(f"Keep-alive ping failed: {e}")
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


# ─── DIRECTIONAL / MARKET-STRUCTURE ENGINE ───────────────────────────────────
def _f(d: dict, k: str, default: float = 0.0) -> float:
    """Safe float extractor from a possibly-missing dict key."""
    try:
        return float(d.get(k, default) or default)
    except Exception:
        return default


def _i(d: dict, k: str, default: int = 0) -> int:
    try:
        return int(d.get(k, default) or default)
    except Exception:
        return default


def find_atm_strike(chain: list, spot: float):
    """Return the chain row whose strike is closest to spot."""
    if not chain:
        return None
    return min(chain, key=lambda r: abs(int(r.get("strike_price", 0)) - spot))


def expected_move(chain: list, spot: float, dte: int):
    """
    Expected move the market is *pricing in* = ATM straddle (ATM CE LTP + ATM PE LTP).
    This is the single most honest answer to "will this strike get movement?".
    Returns (straddle_pts, one_sd_daily_pts, atm_strike).
    """
    atm = find_atm_strike(chain, spot)
    if not atm:
        return 0.0, 0.0, None
    ce = atm.get("call_options", {}).get("market_data", {})
    pe = atm.get("put_options", {}).get("market_data", {})
    straddle = _f(ce, "ltp") + _f(pe, "ltp")
    one_sd_daily = straddle / math.sqrt(max(dte, 1))
    return straddle, one_sd_daily, int(atm.get("strike_price", 0))


def max_pain(chain: list):
    """
    Strike where total option-writer payout is minimized — price tends to
    gravitate here at expiry (the expiry 'magnet').
    """
    if not chain:
        return None
    oi_map = {}
    for r in chain:
        k = int(r.get("strike_price", 0))
        ce_oi = _i(r.get("call_options", {}).get("market_data", {}), "oi")
        pe_oi = _i(r.get("put_options", {}).get("market_data", {}), "oi")
        oi_map[k] = (ce_oi, pe_oi)
    best_strike, best_pain = None, None
    for expiry_price in oi_map:
        pain = 0
        for k, (ce_oi, pe_oi) in oi_map.items():
            if expiry_price > k:          # calls finish ITM
                pain += (expiry_price - k) * ce_oi
            elif expiry_price < k:        # puts finish ITM
                pain += (k - expiry_price) * pe_oi
        if best_pain is None or pain < best_pain:
            best_pain, best_strike = pain, expiry_price
    return best_strike


def oi_walls(chain: list):
    """Strike with max CALL OI = resistance; max PUT OI = support."""
    res_strike = res_oi = sup_strike = sup_oi = 0
    for r in chain:
        k = int(r.get("strike_price", 0))
        ce_oi = _i(r.get("call_options", {}).get("market_data", {}), "oi")
        pe_oi = _i(r.get("put_options", {}).get("market_data", {}), "oi")
        if ce_oi > res_oi:
            res_oi, res_strike = ce_oi, k
        if pe_oi > sup_oi:
            sup_oi, sup_strike = pe_oi, k
    return sup_strike, res_strike


def compute_direction(chain: list, spot: float, dte: int) -> dict:
    """
    Intraday + expiry BLENDED market-direction engine.
      score > 0  => bullish (CE side favoured)
      score < 0  => bearish (PE side favoured)
    Combines: total PCR, fresh OI writing (intraday), ATM premium momentum
    (intraday), and max-pain pull (expiry).
    """
    reasons = []
    score = 0.0

    tot_ce_oi = tot_pe_oi = 0
    tot_ce_oichg = tot_pe_oichg = 0
    for r in chain:
        ce_md = r.get("call_options", {}).get("market_data", {})
        pe_md = r.get("put_options", {}).get("market_data", {})
        tot_ce_oi += _i(ce_md, "oi")
        tot_pe_oi += _i(pe_md, "oi")
        tot_ce_oichg += _i(ce_md, "oi_day_change")
        tot_pe_oichg += _i(pe_md, "oi_day_change")

    pcr = (tot_pe_oi / tot_ce_oi) if tot_ce_oi > 0 else 0.0

    # 1. Total PCR — overall sentiment (expiry weight)
    if pcr > 1.3:
        score += 2; reasons.append(f"PCR {pcr:.2f} → put-heavy, strong support (bullish)")
    elif pcr > 1.05:
        score += 1; reasons.append(f"PCR {pcr:.2f} → mildly bullish")
    elif pcr < 0.7:
        score -= 2; reasons.append(f"PCR {pcr:.2f} → call-heavy, strong resistance (bearish)")
    elif pcr < 0.95:
        score -= 1; reasons.append(f"PCR {pcr:.2f} → mildly bearish")

    # 2. Fresh OI writing TODAY — who is selling? (intraday weight)
    if tot_ce_oichg > 0 and tot_ce_oichg > tot_pe_oichg * 1.2:
        score -= 1.5
        reasons.append(f"Fresh CALL writing (+{tot_ce_oichg:,}) → sellers expect ↓ (bearish)")
    elif tot_pe_oichg > 0 and tot_pe_oichg > tot_ce_oichg * 1.2:
        score += 1.5
        reasons.append(f"Fresh PUT writing (+{tot_pe_oichg:,}) → sellers expect ↑ (bullish)")

    # 3. ATM premium momentum TODAY — which side are buyers bidding up? (intraday)
    atm = find_atm_strike(chain, spot)
    if atm:
        ce_md = atm.get("call_options", {}).get("market_data", {})
        pe_md = atm.get("put_options", {}).get("market_data", {})
        ce_ltp, ce_pc = _f(ce_md, "ltp"), _f(ce_md, "close_price")
        pe_ltp, pe_pc = _f(pe_md, "ltp"), _f(pe_md, "close_price")
        ce_chg = ((ce_ltp - ce_pc) / ce_pc * 100) if ce_pc > 0 else 0
        pe_chg = ((pe_ltp - pe_pc) / pe_pc * 100) if pe_pc > 0 else 0
        if ce_chg - pe_chg > 8:
            score += 1.5
            reasons.append(f"ATM CE {ce_chg:+.0f}% vs PE {pe_chg:+.0f}% → buyers favour CALLs (bullish)")
        elif pe_chg - ce_chg > 8:
            score -= 1.5
            reasons.append(f"ATM PE {pe_chg:+.0f}% vs CE {ce_chg:+.0f}% → buyers favour PUTs (bearish)")

    # 4. Max-pain pull (expiry magnet)
    mp = max_pain(chain)
    if mp:
        if mp > spot * 1.002:
            score += 1; reasons.append(f"Max-pain {mp} > spot → expiry pull up (bullish)")
        elif mp < spot * 0.998:
            score -= 1; reasons.append(f"Max-pain {mp} < spot → expiry pull down (bearish)")

    if score >= 3:
        label = "🟢 BULLISH — CE side favoured ↑"
    elif score >= 1:
        label = "🟢 MILD BULLISH — slight CE edge"
    elif score <= -3:
        label = "🔴 BEARISH — PE side favoured ↓"
    elif score <= -1:
        label = "🔴 MILD BEARISH — slight PE edge"
    else:
        label = "⚪ NEUTRAL — no clear edge, sideways/theta risk"

    sup, res = oi_walls(chain)
    return {
        "score": score, "label": label, "reasons": reasons,
        "pcr": pcr, "max_pain": mp, "support": sup, "resistance": res,
    }


def theta_decay_projection(ltp: float, theta: float, dte: int, days_ahead: int = 5):
    """
    Project premium erosion (depreciation) if spot stays FLAT.
    Theta is per-day & negative; decay accelerates near expiry (1/sqrt(t)),
    so we ramp the daily burn instead of using a flat number.
    """
    if ltp <= 0 or theta == 0 or dte <= 0:
        return None
    daily = abs(theta)
    lines, prem = [], ltp
    for d in range(1, min(days_ahead, dte) + 1):
        remaining = dte - d + 1
        accel = math.sqrt(dte / max(remaining, 1))
        erosion = min(daily * accel, prem)
        prem = max(prem - erosion, 0)
        pct = (prem / ltp * 100) if ltp > 0 else 0
        lines.append(f"  +{d}d: ₹{prem:7.2f}  ({pct:3.0f}% left)  −₹{erosion:.1f}/day")
    return "\n".join(lines)


def zero_to_hero(ltp: float, delta: float, dte: int, strike: int, spot: float,
                 opt_type: str, exp_move: float, direction_score: float):
    """
    Assess whether a cheap OTM option realistically has room to MULTIPLY.
    Grounds the call in: expected move (ATM straddle) vs distance to strike,
    direction alignment, |delta| as prob-of-ITM, and DTE/theta risk.
    Returns None if the strike isn't an OTM lottery candidate.
    """
    is_otm = (opt_type == "CE" and strike > spot) or (opt_type == "PE" and strike < spot)
    if not is_otm:
        return None

    abs_d = abs(delta)
    distance = abs(strike - spot)
    prob_itm = abs_d * 100  # standard approximation: P(ITM) ≈ |delta|
    aligned = (opt_type == "CE" and direction_score > 0) or \
              (opt_type == "PE" and direction_score < 0)
    reach = (exp_move / distance) if distance > 0 else 9.0  # does expected move cover the gap?

    if dte < 1:
        rating = "❌ AVOID — 0DTE: theta destroys it before it can move"
    elif not aligned:
        rating = "🔴 LOW — market direction is AGAINST this side"
    elif reach >= 1.5 and abs(direction_score) >= 2 and dte >= 2:
        rating = "🟢 HIGH — expected move reaches strike + direction agrees"
    elif reach >= 0.8 and aligned:
        rating = "🟡 MEDIUM — reachable IF momentum continues"
    else:
        rating = "🟠 LOW-MEDIUM — needs a big/fast move to pay off"

    return {"rating": rating, "prob_itm": prob_itm, "distance": distance,
            "reach": reach, "aligned": aligned}


def side_metrics(row: dict, opt_type: str, spot: float, strike: int,
                 T: float, dte: int, r_rate: float = 0.065) -> dict:
    """Extract LTP/greeks/OI for one side of a strike, filling greeks via BS if missing."""
    side_key = "call_options" if opt_type == "CE" else "put_options"
    od = row.get(side_key, {})
    md = od.get("market_data", {})
    gk = od.get("option_greeks", {})

    ltp  = _f(md, "ltp")
    prev = _f(md, "close_price", ltp) or ltp
    iv   = _f(gk, "iv")
    delta = _f(gk, "delta")
    theta = _f(gk, "theta")
    gamma = _f(gk, "gamma")
    vega  = _f(gk, "vega")

    if ltp > 0 and spot > 0 and (delta == 0 or iv == 0):
        sigma = (iv / 100) if iv > 0 else 0.15
        bs = bs_greeks(spot, strike, T, r_rate, sigma, opt_type)
        delta = delta or bs.get("delta", 0)
        theta = theta or bs.get("theta", 0)
        gamma = gamma or bs.get("gamma", 0)
        vega  = vega  or bs.get("vega", 0)
        if iv == 0:
            iv = calc_iv_approx(ltp, spot, strike, T, r_rate, opt_type)

    chg = ltp - prev
    pct = (chg / prev * 100) if prev > 0 else 0
    return {
        "ltp": ltp, "prev": prev, "chg": chg, "pct": pct,
        "iv": iv, "delta": delta, "theta": theta, "gamma": gamma, "vega": vega,
        "oi": _i(md, "oi"), "oichg": _i(md, "oi_day_change"), "volume": _i(md, "volume"),
    }


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

    # ── NEW: direction context, expected move, depreciation, zero-to-hero ──
    direction = compute_direction(chain, spot, dte)
    straddle, one_sd, atm_strike = expected_move(chain, spot, dte)
    decay = theta_decay_projection(ltp, theta, dte)
    zth = zero_to_hero(ltp, delta, dte, strike, spot, opt_type, straddle, direction["score"])

    decay_block = (f"🔻 *DEPRECIATION (if spot stays flat):*\n{decay}"
                   if decay else "🔻 *DEPRECIATION:* n/a (check LTP/theta)")
    if zth:
        zth_block = (f"🚀 *ZERO-TO-HERO:* {zth['rating']}\n"
                     f"  Spot must travel {zth['distance']:.0f} pts; market prices "
                     f"~{straddle:.0f} pts (reach {zth['reach']:.1f}x), P(ITM) ≈ {zth['prob_itm']:.0f}%")
    else:
        zth_block = "🚀 *ZERO-TO-HERO:* Not OTM — not a lottery candidate"

    aligned = (opt_type == "CE" and direction["score"] > 0) or \
              (opt_type == "PE" and direction["score"] < 0)
    align_txt = "✅ matches your side" if aligned else \
                ("⚠️ AGAINST your side" if direction["score"] != 0 else "⚪ neutral")

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
🧭 *MARKET DIRECTION:* {direction['label']}
  Your {opt_type} side → {align_txt}
🌐 *EXPECTED MOVE:* ± {straddle:.0f} pts by expiry (~{one_sd:.0f}/day)
{zth_block}
{decay_block}
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


def run_compare_analysis(parsed: dict) -> str:
    """
    CE vs PE side-by-side for a strike. Tells which side has the higher
    probability of moving (direction engine), shows expected move, depreciation
    for both, and a zero-to-hero verdict — so you know which side to BUY.
    """
    index   = parsed["index"]
    strike  = parsed["strike"]
    expiry  = parsed["expiry"]
    index_key = INDEX_KEYS[index]
    display   = INDEX_DISPLAY[index]

    spot  = fetch_spot_price(index_key)
    chain = fetch_option_chain(index_key, expiry)
    if spot is None:
        return "❌ Could not fetch live spot price.\nCheck: market open? token valid?"
    if chain is None:
        return "❌ Could not fetch option chain.\nCheck: expiry date valid? token valid?"

    row = next((r for r in chain if int(r.get("strike_price", 0)) == strike), None)
    if not row:
        available = sorted({int(r["strike_price"]) for r in chain})
        near = [s for s in available if abs(s - strike) <= 500][:6]
        return (f"❌ Strike {strike} not found for expiry {expiry}.\n"
                f"Nearby strikes: {', '.join(map(str, near)) or 'none'}")

    try:
        exp_date = datetime.strptime(expiry, "%Y-%m-%d").date()
        dte = max((exp_date - date.today()).days, 0)
        T = dte / 365.0
    except Exception:
        dte, T = 7, 7 / 365.0

    direction = compute_direction(chain, spot, dte)
    dscore    = direction["score"]
    straddle, one_sd, atm_strike = expected_move(chain, spot, dte)

    ce = side_metrics(row, "CE", spot, strike, T, dte)
    pe = side_metrics(row, "PE", spot, strike, T, dte)

    # ── Recommendation: the side aligned with market direction ──
    adv = abs(dscore)
    if adv >= 4:
        conf = "HIGH"
    elif adv >= 2:
        conf = "MEDIUM-HIGH"
    elif adv >= 1:
        conf = "MEDIUM"
    else:
        conf = "LOW"

    if dscore >= 1:
        rec_side, rec = "CE", ce
        rec_line = f"✅ *BUY {strike} CE*  (premium ₹{ce['ltp']:.2f}) — bias UP ↑"
    elif dscore <= -1:
        rec_side, rec = "PE", pe
        rec_line = f"✅ *BUY {strike} PE*  (premium ₹{pe['ltp']:.2f}) — bias DOWN ↓"
    else:
        rec_side, rec = None, None
        rec_line = ("⚪ *NO directional buy.* Bias is flat — buying either side "
                    "mostly bleeds theta. Wait for a breakout.")

    # ── Zero-to-hero check for both sides ──
    z_ce = zero_to_hero(ce["ltp"], ce["delta"], dte, strike, spot, "CE", straddle, dscore)
    z_pe = zero_to_hero(pe["ltp"], pe["delta"], dte, strike, spot, "PE", straddle, dscore)

    def _z(z):
        if not z:
            return "  Not OTM here — not a zero-to-hero candidate"
        return (f"  {z['rating']}\n"
                f"    • Spot must travel {z['distance']:.0f} pts; "
                f"market is pricing ~{straddle:.0f} pts move (reach {z['reach']:.1f}x)\n"
                f"    • Rough chance of finishing ITM ≈ {z['prob_itm']:.0f}%")

    # ── Depreciation projection for the recommended (or both) ──
    if rec_side:
        decay = theta_decay_projection(rec["ltp"], rec["theta"], dte)
        decay_block = (f"🔻 *DEPRECIATION — {strike} {rec_side}* (if spot stays flat):\n{decay}"
                       if decay else "🔻 Depreciation: n/a")
    else:
        decay_ce = theta_decay_projection(ce["ltp"], ce["theta"], dte)
        decay_pe = theta_decay_projection(pe["ltp"], pe["theta"], dte)
        decay_block = (f"🔻 *DEPRECIATION CE:*\n{decay_ce or '  n/a'}\n"
                       f"🔻 *DEPRECIATION PE:*\n{decay_pe or '  n/a'}")

    # ── Breakeven / required move for the recommended side ──
    if rec_side == "CE":
        be = strike + rec["ltp"]; need = be - spot
    elif rec_side == "PE":
        be = strike - rec["ltp"]; need = spot - be
    else:
        be = need = 0

    diff = strike - spot
    pos = "ATM" if abs(diff) <= spot * 0.004 else ("above spot" if diff > 0 else "below spot")
    dir_reasons = "\n".join(f"  • {r}" for r in direction["reasons"]) or "  • No strong signals"
    now = datetime.now().strftime("%d-%b-%Y %H:%M:%S")

    be_block = ""
    if rec_side:
        cover = "✅ within" if (straddle >= abs(need) and need > 0) else "⚠️ beyond"
        be_block = (f"  Breakeven : {be:.0f}  (spot must move {abs(need):.0f} pts)\n"
                    f"  Expected move covers it? {cover} the ~{straddle:.0f} pt expected move\n")

    msg = f"""━━━━━━━━━━━━━━━━━━━━━━━━━━━
⚖️ *CE vs PE — WHICH SIDE TO BUY*
━━━━━━━━━━━━━━━━━━━━━━━━━━━
🏷 *{display} {strike}*  (strike is {pos})
📅 Expiry : {expiry}   ⏰ {now}
  Spot : ₹{spot:,.2f}   ATM : {atm_strike}   DTE : {dte}d
━━━━━━━━━━━━━━━━━━━━━━━━━━━
🌐 *EXPECTED MOVE (priced in by market)*
  ± {straddle:.0f} pts by expiry  |  ~{one_sd:.0f} pts/day (1σ)
  _This is how much movement is realistic — not a guarantee._
━━━━━━━━━━━━━━━━━━━━━━━━━━━
🧭 *MARKET DIRECTION ENGINE*
  {direction['label']}   (score {dscore:+.1f})
{dir_reasons}
  PCR {direction['pcr']:.2f} | Max-pain {direction['max_pain']} | Support {direction['support']} | Resistance {direction['resistance']}
━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 *SIDE-BY-SIDE @ {strike}*
```
              CE          PE
LTP      ₹{ce['ltp']:>8.2f}   ₹{pe['ltp']:>8.2f}
Today    {ce['pct']:>+7.1f}%   {pe['pct']:>+7.1f}%
Delta    {ce['delta']:>8.3f}   {pe['delta']:>8.3f}
IV       {ce['iv']:>7.1f}%   {pe['iv']:>7.1f}%
Theta/d  ₹{abs(ce['theta']):>7.1f}   ₹{abs(pe['theta']):>7.1f}
OI chg   {ce['oichg']:>8,}   {pe['oichg']:>8,}
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━
🚀 *ZERO-TO-HERO CHECK*
 CE:
{_z(z_ce)}
 PE:
{_z(z_pe)}
━━━━━━━━━━━━━━━━━━━━━━━━━━━
{decay_block}
━━━━━━━━━━━━━━━━━━━━━━━━━━━
🎯 *RECOMMENDATION*  (confidence: {conf})
{rec_line}
{be_block}━━━━━━━━━━━━━━━━━━━━━━━━━━━
⚠️ _Probability-based, NOT a guaranteed move._
_Educational only. Not financial advice._
━━━━━━━━━━━━━━━━━━━━━━━━━━━"""
    return msg


# ─── FORWARD-TEST (REAL-BOT BACKTEST LOGGER) ─────────────────────────────────
# At 10:00 / 12:00 / 13:00 / 14:00 / 15:00 IST every trading day the bot logs
# its REAL prediction (CE / PE) for Nifty & Sensex nearest strike, then at the
# 15:30 close it auto-verifies: win = index moved in the predicted direction.
BACKTEST_FILE   = Path(__file__).parent / "backtest_log.json"
BACKTEST_INDICES = ["nifty", "sensex"]
SIGNAL_TIMES     = ["10:00", "12:00", "13:00", "14:00", "15:00"]
IST = None
try:
    import pytz
    IST = pytz.timezone("Asia/Kolkata")
except Exception as _e:  # pragma: no cover
    logger.warning(f"pytz unavailable — backtest scheduler disabled: {_e}")


def _load_log() -> list:
    if BACKTEST_FILE.exists():
        try:
            return json.loads(BACKTEST_FILE.read_text())
        except Exception:
            return []
    return []


def _save_log(log: list):
    BACKTEST_FILE.write_text(json.dumps(log, indent=2))


def nearest_expiry(index_key: str) -> Optional[str]:
    """Front (nearest) option expiry for an index."""
    try:
        r = requests.get(f"{UPSTOX_BASE}/option/contract", headers=headers(),
                         params={"instrument_key": index_key}, timeout=10)
        j = r.json()
        if j.get("status") == "success":
            exps = sorted({c.get("expiry") for c in j.get("data", []) if c.get("expiry")})
            return exps[0] if exps else None
    except Exception as e:
        logger.error(f"nearest_expiry error: {e}")
    return None


def make_prediction(index: str) -> Optional[dict]:
    """Run the REAL direction engine live and return its CE/PE/NEUTRAL call."""
    index_key = INDEX_KEYS[index]
    spot = fetch_spot_price(index_key)
    expiry = nearest_expiry(index_key)
    if spot is None or not expiry:
        return None
    chain = fetch_option_chain(index_key, expiry)
    if not chain:
        return None
    try:
        dte = max((datetime.strptime(expiry, "%Y-%m-%d").date() - date.today()).days, 0)
    except Exception:
        dte = 2
    direction = compute_direction(chain, spot, dte)
    atm = find_atm_strike(chain, spot)
    strike = int(atm.get("strike_price", 0)) if atm else 0
    score = direction["score"]
    side = "CE" if score >= 1 else ("PE" if score <= -1 else "NEUTRAL")
    return {"index": index, "expiry": expiry, "spot": spot,
            "strike": strike, "side": side, "score": score}


async def job_record_signal(context: ContextTypes.DEFAULT_TYPE):
    """Scheduled at each SIGNAL_TIME — log the live prediction (result=pending)."""
    if date.today().weekday() >= 5:   # skip Sat/Sun
        return
    slot = context.job.data
    today = date.today().isoformat()
    log = _load_log()
    loop = asyncio.get_event_loop()
    for index in BACKTEST_INDICES:
        if any(r["date"] == today and r["slot"] == slot and r["index"] == index for r in log):
            continue  # already logged this slot today (e.g. after a restart)
        pred = await loop.run_in_executor(None, make_prediction, index)
        if not pred:
            continue
        log.append({
            "date": today, "slot": slot, "index": index,
            "expiry": pred["expiry"], "strike": pred["strike"],
            "side": pred["side"], "score": pred["score"],
            "spot_at_signal": pred["spot"], "close_price": None,
            "result": "pending",
        })
    _save_log(log)
    logger.info(f"Backtest: recorded {slot} signals")


async def job_resolve(context: ContextTypes.DEFAULT_TYPE):
    """Scheduled at 15:35 IST — verify today's pending predictions vs the close."""
    today = date.today().isoformat()
    log = _load_log()
    loop = asyncio.get_event_loop()
    closes = {}
    for index in BACKTEST_INDICES:
        closes[index] = await loop.run_in_executor(None, fetch_spot_price, INDEX_KEYS[index])
    changed = False
    for r in log:
        if r["date"] == today and r["result"] == "pending":
            close = closes.get(r["index"])
            if close is None:
                continue
            r["close_price"] = close
            if r["side"] == "NEUTRAL":
                r["result"] = "neutral"
            else:
                moved = close - r["spot_at_signal"]
                if abs(moved) < 1e-6:
                    r["result"] = "no-trade"   # flat / market holiday
                elif (r["side"] == "CE" and moved > 0) or (r["side"] == "PE" and moved < 0):
                    r["result"] = "win"
                else:
                    r["result"] = "loss"
            changed = True
    if changed:
        _save_log(log)
    logger.info("Backtest: resolved today's signals")


def _winrate(records: list):
    wins = sum(1 for r in records if r["result"] == "win")
    losses = sum(1 for r in records if r["result"] == "loss")
    total = wins + losses
    pct = (wins / total * 100) if total else 0.0
    return wins, losses, total, pct


def build_backtest_report() -> str:
    log = _load_log()
    resolved = [r for r in log if r["result"] in ("win", "loss")]
    pending  = [r for r in log if r["result"] == "pending"]
    neutral  = [r for r in log if r["result"] == "neutral"]

    if not resolved and not pending:
        return ("📭 *No forward-test data yet.*\n\n"
                "The bot logs predictions at 10:00, 12:00, 1:00, 2:00 & 3:00 PM "
                "every trading day and verifies them at the 3:30 close.\n"
                "Come back after the next market session.")

    ce = [r for r in resolved if r["side"] == "CE"]
    pe = [r for r in resolved if r["side"] == "PE"]
    cw, cl, ct, cp = _winrate(ce)
    pw, pl, pt, pp = _winrate(pe)
    ow, ol, ot, op = _winrate(resolved)

    dates = sorted({r["date"] for r in log})
    span = f"{dates[0]} → {dates[-1]}" if dates else "—"

    # per time-slot
    slot_lines = []
    for s in SIGNAL_TIMES:
        sr = [r for r in resolved if r["slot"] == s]
        w, l, t, p = _winrate(sr)
        if t:
            slot_lines.append(f"  {s} : {p:5.1f}%  ({w}/{t})")
    slot_block = "\n".join(slot_lines) or "  (none resolved yet)"

    # per index
    idx_lines = []
    for ix in BACKTEST_INDICES:
        ir = [r for r in resolved if r["index"] == ix]
        w, l, t, p = _winrate(ir)
        if t:
            idx_lines.append(f"  {INDEX_DISPLAY.get(ix, ix):<11}: {p:5.1f}%  ({w}/{t})")
    idx_block = "\n".join(idx_lines) or "  (none resolved yet)"

    return f"""━━━━━━━━━━━━━━━━━━━━━━━━━━━
🧪 *FORWARD-TEST WIN RATE*  (real bot)
━━━━━━━━━━━━━━━━━━━━━━━━━━━
Period   : {span}
Resolved : {ot} trades | Pending : {len(pending)} | Neutral skipped : {len(neutral)}
━━━━━━━━━━━━━━━━━━━━━━━━━━━
🎯 *OVERALL : {op:.1f}%*   ({ow}W / {ol}L)

📈 *CE side : {cp:.1f}%*   ({cw}W / {cl}L  of {ct})
📉 *PE side : {pp:.1f}%*   ({pw}W / {pl}L  of {pt})
━━━━━━━━━━━━━━━━━━━━━━━━━━━
⏰ *By time of day:*
{slot_block}
━━━━━━━━━━━━━━━━━━━━━━━━━━━
🏷 *By index:*
{idx_block}
━━━━━━━━━━━━━━━━━━━━━━━━━━━
_Win = index moved in the predicted direction by 3:30 close._
_NEUTRAL calls are excluded (no trade taken)._
━━━━━━━━━━━━━━━━━━━━━━━━━━━"""


async def cmd_backtest(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if context.args and context.args[0].lower() == "now":
        # Manual one-off: log a prediction immediately (for testing the pipeline).
        loop = asyncio.get_event_loop()
        lines = []
        for index in BACKTEST_INDICES:
            pred = await loop.run_in_executor(None, make_prediction, index)
            if pred:
                lines.append(f"  {INDEX_DISPLAY[index]} {pred['strike']} → "
                             f"*{pred['side']}* (score {pred['score']:+.1f}, spot {pred['spot']:.0f})")
        await update.message.reply_text(
            "🧪 *Live prediction snapshot:*\n" + ("\n".join(lines) or "  no data"),
            parse_mode="Markdown")
        return
    await update.message.reply_text(build_backtest_report(), parse_mode="Markdown")


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

📌 *Single side (CE or PE):*
```
nifty 25000 ce 19/05/2026
banknifty 52000 pe 22/05/2026
```

⚖️ *Compare CE vs PE (NEW) — just drop CE/PE:*
```
nifty 25000 19/05/2026
```
→ Tells you *which side is more likely to move* + which to BUY.

*Commands:*
  /token    — Set/update Upstox access token
  /backtest — Real-bot forward-test win rate
  /help     — Full help & examples

*I will analyze:*
  ✅ Live Premium & Spot Price
  ✅ Delta, Theta, IV, Gamma, Vega
  ✅ OI / PCR / Max-pain / Support-Resistance
  ✅ 🧭 Direction engine (CE vs PE edge)
  ✅ 🌐 Expected move (realistic, priced-in)
  ✅ 🔻 Depreciation (day-by-day theta decay)
  ✅ 🚀 Zero-to-hero probability check

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
nifty 25000 ce 19/05/2026     (CE only)
banknifty 52000 pe 22/05/2026 (PE only)
nifty 25000 19/05/2026        (CE vs PE)
finnifty 24500 2026-05-19     (CE vs PE)
```

*What the NEW signals mean:*
  🧭 Direction → which side (CE/PE) has the edge,
      blending PCR + OI writing + premium momentum + max-pain
  🌐 Expected move → pts the market is actually pricing in
      (ATM straddle). If your strike is farther than this,
      the move is *unlikely* — that's your reality check.
  🔻 Depreciation → how fast premium melts if price stays flat
  🚀 Zero-to-hero → is a cheap OTM realistically reachable?
      Needs: direction agrees + expected move covers distance + DTE ≥ 2

*Classic signals:*
  PCR > 1.2 → bullish | PCR < 0.7 → bearish
  IV < 15% → cheap | IV > 25% → IV-crush risk
  Delta > 0.5 → strong move | Low DTE → heavy decay

*Forward-test (prove the bot):*
  `/backtest`     → CE / PE / overall win rate
  `/backtest now` → log a live prediction right now
  The bot auto-logs its real CE/PE call at 10:00, 12:00,
  1:00, 2:00 & 3:00 PM for Nifty & Sensex, then checks at
  the 3:30 close. A win = index moved the predicted way.

⚠️ _No tool can promise a "sure" move. These are
probabilities — they stack the odds, never guarantee them._"""
    await update.message.reply_text(text, parse_mode="Markdown")


def _compare_keyboard(index, strike, expiry):
    return InlineKeyboardMarkup([
        [InlineKeyboardButton("🔄 Refresh", callback_data=f"compare|{index}|{strike}|X|{expiry}"),
         InlineKeyboardButton("📈 CE only", callback_data=f"refresh|{index}|{strike}|CE|{expiry}"),
         InlineKeyboardButton("📉 PE only", callback_data=f"refresh|{index}|{strike}|PE|{expiry}")]
    ])


def _single_keyboard(index, strike, opt_type, expiry):
    flip_type = "PE" if opt_type == "CE" else "CE"
    return InlineKeyboardMarkup([
        [InlineKeyboardButton("🔄 Refresh",
            callback_data=f"refresh|{index}|{strike}|{opt_type}|{expiry}"),
         InlineKeyboardButton(f"↔ {flip_type}",
            callback_data=f"refresh|{index}|{strike}|{flip_type}|{expiry}"),
         InlineKeyboardButton("⚖️ CE vs PE",
            callback_data=f"compare|{index}|{strike}|X|{expiry}")]
    ])


async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    text = update.message.text or ""
    parsed = parse_input(text)

    # Compare mode: index + strike + expiry present, but NO CE/PE specified.
    compare_mode = (parsed["index"] and parsed["strike"] and parsed["expiry"]
                    and not parsed["opt_type"])

    if not compare_mode:
        errors = validate(parsed)
        if errors:
            err_text = "⚠️ *Invalid format:*\n" + "\n".join(f"  • {e}" for e in errors)
            err_text += ("\n\n*Single side:*\n`nifty 25000 ce 19/05/2026`"
                         "\n*Compare CE vs PE (no ce/pe):*\n`nifty 25000 19/05/2026`")
            await update.message.reply_text(err_text, parse_mode="Markdown")
            return

    label = f"{INDEX_DISPLAY.get(parsed['index'], parsed['index'])} {parsed['strike']}"
    label += " CE vs PE" if compare_mode else f" {parsed['opt_type']}"
    wait_msg = await update.message.reply_text(
        f"⏳ Analyzing *{label}* | Expiry: {parsed['expiry']}\n\n"
        "_Fetching live data from Upstox..._",
        parse_mode="Markdown"
    )

    loop = asyncio.get_event_loop()
    if compare_mode:
        result = await loop.run_in_executor(None, run_compare_analysis, parsed)
        keyboard = _compare_keyboard(parsed["index"], parsed["strike"], parsed["expiry"])
    else:
        result = await loop.run_in_executor(None, run_analysis, parsed)
        keyboard = _single_keyboard(parsed["index"], parsed["strike"],
                                    parsed["opt_type"], parsed["expiry"])

    await wait_msg.delete()
    await update.message.reply_text(result, parse_mode="Markdown", reply_markup=keyboard)


async def handle_callback(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer("Refreshing...")

    parts = query.data.split("|")
    if len(parts) != 5:
        return

    action, index, strike_s, opt_type, expiry = parts
    strike = int(strike_s)
    loop = asyncio.get_event_loop()

    if action == "compare":
        parsed = {"index": index, "strike": strike, "opt_type": None, "expiry": expiry}
        result = await loop.run_in_executor(None, run_compare_analysis, parsed)
        keyboard = _compare_keyboard(index, strike, expiry)
        await query.edit_message_text(result, parse_mode="Markdown", reply_markup=keyboard)
    elif action == "refresh":
        parsed = {"index": index, "strike": strike, "opt_type": opt_type, "expiry": expiry}
        result = await loop.run_in_executor(None, run_analysis, parsed)
        keyboard = _single_keyboard(index, strike, opt_type, expiry)
        await query.edit_message_text(result, parse_mode="Markdown", reply_markup=keyboard)


# ─── MAIN ─────────────────────────────────────────────────────────────────────
async def on_error(update: object, context: ContextTypes.DEFAULT_TYPE):
    """Log errors instead of letting them crash the polling loop."""
    logger.error("Handler error", exc_info=context.error)


def main():
    if not TELEGRAM_BOT_TOKEN:
        raise SystemExit("❌ TELEGRAM_BOT_TOKEN not set in .env")

    # Start Health Check Server + self-ping keep-alive in background threads
    threading.Thread(target=run_health_server, daemon=True).start()
    threading.Thread(target=run_keepalive, daemon=True).start()

    app = Application.builder().token(TELEGRAM_BOT_TOKEN).build()
    app.add_handler(CommandHandler("start", cmd_start))
    app.add_handler(CommandHandler("help",  cmd_help))
    app.add_handler(CommandHandler("token", cmd_token))
    app.add_handler(CommandHandler("backtest", cmd_backtest))
    app.add_handler(CallbackQueryHandler(handle_callback))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_message))
    app.add_error_handler(on_error)

    # ── Forward-test scheduler (logs real predictions, verifies at close) ──
    jq = app.job_queue
    if jq is not None and IST is not None:
        for t in SIGNAL_TIMES:
            h, m = map(int, t.split(":"))
            jq.run_daily(job_record_signal, time=dtime(h, m, tzinfo=IST),
                         data=t, name=f"signal_{t}")
        jq.run_daily(job_resolve, time=dtime(15, 35, tzinfo=IST), name="resolve_close")
        logger.info(f"Forward-test scheduled at {SIGNAL_TIMES} IST + resolve 15:35 IST")
    else:
        logger.warning("JobQueue/pytz not available — forward-test disabled "
                       "(install python-telegram-bot[job-queue]).")

    print("Option Analyzer Bot is LIVE! Press Ctrl+C to stop.")
    # drop_pending_updates: after a restart, ignore the backlog so the bot
    # doesn't choke on stale messages. PTB auto-recovers from transient
    # 409 Conflicts (overlapping deploy) once the old instance dies.
    app.run_polling(allowed_updates=Update.ALL_TYPES, drop_pending_updates=True)


if __name__ == "__main__":
    main()
