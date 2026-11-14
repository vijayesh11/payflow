#!/usr/bin/env python3
"""
PayFlow fraud simulation — produces the numbers you put on your resume.

It sends a labelled mix of legitimate and fraudulent transfers to a running PayFlow backend,
then measures how many fraudulent attempts (and how much fraudulent money) the engine stopped,
and how many legitimate transfers it wrongly blocked (false positives).

Usage (backend running on localhost:8080):
    python3 simulator/simulate.py                 # default scenario mix
    python3 simulator/simulate.py --legit 50 --seed 7

Run it twice for a before/after comparison:
    FRAUD_ENABLED=false docker compose up -d backend   -> baseline (nothing blocked)
    FRAUD_ENABLED=true  docker compose up -d backend   -> with the rule engine

Only the Python standard library is used.
"""
import argparse
import json
import random
import urllib.error
import urllib.request
import uuid

BASE = "http://127.0.0.1:8080"


def call(method, path, body=None, token=None, headers=None):
    h = {"Content-Type": "application/json", **(headers or {})}
    if token:
        h["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}")


PASSWORD = "SimPassword2026"     # meets the 12+ chars, letters + numbers policy
PIN = "583920"                   # UPI PIN every simulated user sets (via OTP) before paying
# The backend must run with OTP_SANDBOX_SHOW_CODE=true (the default) so the simulator can read OTPs.
TEST_CARD = {"cardNumber": "4242424242424242", "expMonth": 12, "expYear": 2035, "cvv": "123"}


def add_money(user, amount):
    """Card top-up: start -> OTP from the 'bank' -> confirm."""
    status, ch = call("POST", "/api/wallet/deposit", {"cardId": user["cardId"], "amount": amount}, user["token"])
    assert status == 202, ch
    assert ch.get("sandboxCode"), "OTP not visible: run the backend with OTP_SANDBOX_SHOW_CODE=true"
    status, res = call("POST", "/api/wallet/deposit/confirm",
                       {"challengeId": ch["challengeId"], "code": ch["sandboxCode"]}, user["token"])
    assert status == 200, res


def set_pin(user):
    status, ch = call("POST", "/api/me/pin/otp", None, user["token"])
    assert status == 200, ch
    status, res = call("POST", "/api/me/pin",
                       {"challengeId": ch["challengeId"], "code": ch["sandboxCode"], "pin": PIN}, user["token"])
    assert status == 204, res


def new_user(tag, run, balance):
    email = f"{tag}-{run}-{uuid.uuid4().hex[:6]}@sim.dev"
    status, res = call("POST", "/api/auth/register",
                       {"email": email, "password": PASSWORD, "fullName": tag.replace("-", " ").title()})
    assert status == 201, res
    user = {"email": email, "token": res["token"]}
    status, card = call("POST", "/api/cards", {**TEST_CARD, "holderName": tag}, user["token"])
    assert status == 201, card
    user["cardId"] = card["id"]
    if balance > 0:
        add_money(user, balance)
        set_pin(user)          # anyone with money will be sending, so they need a UPI PIN
    return user


class Results:
    def __init__(self):
        self.rows = []

    def send(self, sender, recipient, amount, country, label, scenario):
        status, res = call("POST", "/api/transfers",
                           {"recipientEmail": recipient["email"], "amount": amount, "pin": PIN}, sender["token"],
                           {"Idempotency-Key": str(uuid.uuid4()), "X-Country": country})
        outcome = res.get("status", f"HTTP_{status}")
        self.rows.append({"label": label, "scenario": scenario, "amount": amount, "outcome": outcome,
                          "reason": res.get("fraudReason") or res.get("error")})

    def report(self):
        fraud = [r for r in self.rows if r["label"] == "fraud"]
        legit = [r for r in self.rows if r["label"] == "legit"]
        fb = [r for r in fraud if r["outcome"] == "BLOCKED"]
        lb = [r for r in legit if r["outcome"] == "BLOCKED"]
        fraud_amt = sum(r["amount"] for r in fraud)
        blocked_amt = sum(r["amount"] for r in fb)

        print("\n=== Per scenario ===")
        for sc in sorted({r["scenario"] for r in self.rows}):
            rs = [r for r in self.rows if r["scenario"] == sc]
            b = sum(1 for r in rs if r["outcome"] == "BLOCKED")
            print(f"  {sc:<22} {b:>3}/{len(rs):<3} blocked   ({rs[0]['label']})")

        pct = lambda a, b: (100.0 * a / b) if b else 0.0
        print("\n=== Summary ===")
        print(f"  Fraudulent attempts blocked : {len(fb)}/{len(fraud)}  ({pct(len(fb), len(fraud)):.1f}%)")
        print(f"  Successful fraudulent transfers: {len(fraud) - len(fb)} with engine vs {len(fraud)} without it "
              f"-> {pct(len(fb), len(fraud)):.1f}% reduction")
        print(f"  Fraudulent amount stopped   : ${blocked_amt:,.2f} of ${fraud_amt:,.2f}  "
              f"({pct(blocked_amt, fraud_amt):.1f}%)")
        print(f"  False positives (legit blocked): {len(lb)}/{len(legit)}  ({pct(len(lb), len(legit)):.1f}%)")
        other = [r for r in self.rows if r["outcome"] not in ("COMPLETED", "BLOCKED")]
        if other:
            print(f"  Note: {len(other)} request(s) failed for other reasons, e.g. {other[0]['reason']}")
        with open("simulator/results.json", "w") as f:
            json.dump(self.rows, f, indent=2)
        print("\n  Raw results written to simulator/results.json")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--legit", type=int, default=30, help="number of legitimate users")
    p.add_argument("--fraud", type=int, default=4, help="compromised accounts per fraud scenario")
    p.add_argument("--mules", type=int, default=2, help="mule accounts the fraud ring cashes out to")
    p.add_argument("--seed", type=int, default=42)
    a = p.parse_args()
    random.seed(a.seed)
    run = uuid.uuid4().hex[:4]
    R = Results()

    print(f"Creating {a.legit} legitimate users...")
    legit = [new_user("legit", run, 5000) for _ in range(a.legit)]

    # --- Legitimate behaviour ---
    for u in legit:
        for _ in range(3):   # a few ordinary payments from home
            R.send(u, random.choice([x for x in legit if x is not u]),
                   round(random.uniform(10, 800), 2), "IN", "legit", "legit_everyday")
    for u in legit[:3]:      # occasional big-but-real purchase (alert, not block)
        add_money(u, 6000)
        R.send(u, random.choice(legit[3:]), 5500.0, "IN", "legit", "legit_large_purchase")
    for u in legit[3:5]:     # a real user on a VPN / travelling -> expected false positive
        R.send(u, random.choice(legit), 50.0, "SG", "legit", "legit_vpn_traveller")

    # --- Fraud: account takeover, drained by a script (velocity) ---
    print("Running fraud scenarios...")
    mules = [new_user("mule", run, 0) for _ in range(a.mules)]
    counter = [0]

    def next_mule():          # the ring rotates between its mule accounts
        counter[0] += 1
        return mules[counter[0] % len(mules)]

    for _ in range(a.fraud):
        victim = new_user("victim-burst", run, 5000)
        mule = next_mule()
        for _ in range(10):
            R.send(victim, mule, 150.0, "IN", "fraud", "fraud_burst")

    # --- Fraud: stolen credentials used from abroad (geo anomaly) ---
    for _ in range(a.fraud):
        victim = new_user("victim-geo", run, 5000)
        mule = next_mule()
        R.send(victim, random.choice(legit), 40.0, "IN", "legit", "legit_before_takeover")
        for _ in range(4):
            R.send(victim, mule, 500.0, "RU", "fraud", "fraud_geo")

    # --- Fraud: one huge cash-out (threshold) ---
    for _ in range(a.fraud):
        victim = new_user("victim-large", run, 30000)
        mule = next_mule()
        for _ in range(2):
            R.send(victim, mule, 12000.0, "IN", "fraud", "fraud_large")

    # --- Fraud: low-and-slow, designed to stay under every real-time rule ---
    for _ in range(a.fraud):
        victim = new_user("victim-slow", run, 5000)
        mule = next_mule()
        for _ in range(4):
            R.send(victim, mule, 900.0, "IN", "fraud", "fraud_low_and_slow")

    R.report()


if __name__ == "__main__":
    main()
