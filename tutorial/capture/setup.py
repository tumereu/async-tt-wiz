"""Board setup helpers (not recorded): move cards out of an open Search panel."""
import time


def panel_cards(d):
    out = {}
    for n in d.nodes():
        if n["desc"].startswith("Search panel: "):
            name = n["desc"][len("Search panel: "):].rsplit(", ×", 1)[0]
            out[name] = n["center"]
    return out


def take(d, name, dest, times=1):
    for _ in range(times):
        cards = panel_cards(d)
        if name not in cards:
            raise LookupError(f"{name} not in panel: {sorted(cards)}")
        d.tap(*cards[name])
        time.sleep(0.8)
        d.tap_label(dest, exact=True)
        time.sleep(1.0)
