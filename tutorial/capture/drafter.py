"""Plays the remaining picks of the tutorial pod via the API, preferring each player's colors.

Usage: drafter.py [max picks]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from api import call, login, request_id

PLAYERS = {"alice@tutorial.dev": {"R", "W"}, "bob@tutorial.dev": {"U", "B"}}


def score(card, colors):
    c = set(card.get("colors") or [])
    if not c:
        return 1  # colorless/lands are fine for anyone
    return 3 if c <= colors else (2 if c & colors else 0)


def main(limit):
    tokens = {email: login(email) for email in PLAYERS}
    _, pods = call("GET", "/v1/pods", token=next(iter(tokens.values())))
    pod = next(p for p in pods["pods"] if p["phase"] == "DRAFTING")["id"]
    picks = 0
    while picks < limit:
        progressed = False
        for email, token in tokens.items():
            _, d = call("GET", f"/v1/pods/{pod}", token=token)
            if d["phase"] != "DRAFTING":
                print("phase", d["phase"])
                return
            head = (d.get("draft") or {}).get("head")
            if not head:
                continue
            cards = sorted(head["cards"], key=lambda c: -score(c, PLAYERS[email]))
            keep_n, burn_n = d["settings"]["keep"], d["settings"]["burn"]
            keep = [c["id"] for c in cards[:keep_n]]
            rest = [c["id"] for c in cards[keep_n:]][::-1]
            burn = rest if len(cards) <= keep_n + burn_n else rest[:burn_n]
            s, r = call("POST", f"/v1/pods/{pod}/pick", {"requestId": request_id(), "draftAttemptId": d["draftAttemptId"],
                "packId": head["id"], "keep": keep, "burn": burn}, token)
            if s >= 300:
                print("pick failed", email, s, r)
                return
            picks += 1
            progressed = True
        if not progressed:
            print("no picks available")
            return
    print("stopped after", picks)


if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 10**9)
