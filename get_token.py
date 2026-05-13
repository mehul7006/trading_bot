"""
Helper script to get Upstox access token.
Run: python get_token.py
"""

import webbrowser
import requests
from dotenv import load_dotenv
import os

load_dotenv()

API_KEY    = os.getenv("UPSTOX_API_KEY", "768a303b-80f1-46d6-af16-f847f9341213")
API_SECRET = os.getenv("UPSTOX_API_SECRET", "j0w9ga2m9w")
REDIRECT   = "https://127.0.0.1/"

AUTH_URL = (
    f"https://api.upstox.com/v2/login/authorization/dialog"
    f"?response_type=code&client_id={API_KEY}&redirect_uri={REDIRECT}"
)

print("=" * 60)
print("UPSTOX TOKEN GENERATOR")
print("=" * 60)
print()
print("Step 1: Opening login URL in browser...")
webbrowser.open(AUTH_URL)
print(f"If browser didn't open, go to:\n{AUTH_URL}")
print()
print("Step 2: Login with your Upstox account in the browser.")
print("Step 3: After login, the browser will redirect to a URL like:")
print("  https://127.0.0.1/?code=XXXXXXXXXX")
print()

code = input("Step 4: Paste the 'code' value from that URL here: ").strip()

print()
print("Fetching access token...")

resp = requests.post(
    "https://api.upstox.com/v2/login/authorization/token",
    headers={"Content-Type": "application/x-www-form-urlencoded"},
    data={
        "code":          code,
        "client_id":     API_KEY,
        "client_secret": API_SECRET,
        "redirect_uri":  REDIRECT,
        "grant_type":    "authorization_code",
    },
    timeout=15,
)

data = resp.json()

if "access_token" in data:
    token = data["access_token"]
    print()
    print("=" * 60)
    print("SUCCESS: ACCESS TOKEN OBTAINED!")
    print("=" * 60)
    print(f"\nToken:\n{token}\n")
    print("Now send this to your Telegram bot:")
    print(f"/token {token}")
    print()

    # Optionally update .env
    update = input("Update .env file automatically? (y/n): ").strip().lower()
    if update == "y":
        env_path = ".env"
        with open(env_path, "r") as f:
            content = f.read()
        if "UPSTOX_ACCESS_TOKEN=" in content:
            lines = content.splitlines()
            new_lines = []
            for line in lines:
                if line.startswith("UPSTOX_ACCESS_TOKEN="):
                    new_lines.append(f"UPSTOX_ACCESS_TOKEN={token}")
                else:
                    new_lines.append(line)
            with open(env_path, "w") as f:
                f.write("\n".join(new_lines))
        else:
            with open(env_path, "a") as f:
                f.write(f"\nUPSTOX_ACCESS_TOKEN={token}\n")
        print("Done: .env file updated!")
else:
    print()
    print("Error: Failed to get token!")
    print(f"Response: {data}")
    print()
    print("Common issues:")
    print("  • Code already used (codes are one-time use)")
    print("  • Code expired (use within 30 seconds)")
    print("  • Wrong API key or secret")
