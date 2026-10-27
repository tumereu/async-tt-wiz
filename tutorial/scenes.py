"""Generates scenes.json (the video timeline): python3 scenes.py scenes.json"""
import json, sys
p = sys.argv[1]
old = json.load(open(p))
chapters = old["chapters"]

def clip(name, frm=0, to=None, rate=1):
    m = {"src": f"clips/{name}.mp4", "from": frm}
    if to is not None: m["to"] = to
    if rate != 1: m["rate"] = rate
    return m
def shot(name): return {"src": f"shots/{name}.png"}
def L(text, media=None, marks=None, hold=None, say=None, zoom="unset"):
    l = {"text": text}
    if say: l["say"] = say
    if media is not None: l["media"] = media
    if marks: l["marks"] = marks
    if hold: l["hold"] = hold
    if zoom != "unset": l["zoom"] = zoom
    return l
def rect(x, y, w, h, at=0.3, until=None, device=None):
    m = {"type": "rect", "x": x, "y": y, "w": w, "h": h, "at": at}
    if until is not None: m["until"] = until
    if device is not None: m["device"] = device
    return m
def circle(x, y, r, at=0.3, until=None, device=None):
    m = {"type": "circle", "x": x, "y": y, "r": r, "at": at}
    if until is not None: m["until"] = until
    if device is not None: m["device"] = device
    return m
def label(x, y, text, at=0.3, anchor="middle", until=None, device=None):
    m = {"type": "label", "x": x, "y": y, "text": text, "at": at, "anchor": anchor}
    if until is not None: m["until"] = until
    if device is not None: m["device"] = device
    return m

S = []
def chap(n): S.append({"id": f"c{n}-card", "chapter": n, "layout": "chapter", "seconds": 2.2})

S.append({"id": "c0-title", "chapter": 0, "layout": "title", "seconds": 3.5})
S.append({"id": "c0-intro", "chapter": 0, "lines": [
    L("Async Tabletop Wizards is a shared card-wizarding table for two.", shot("c3-table-a")),
    L("It's a simulated table, not a rules engine: you move the cards, and you and your opponent decide what the rules say.", clip("c4-hand", 1.5, 9.7)),
    L("Everything you do is saved and synced. Your opponent sees it played back when they open the game, so you do not need to be present at the same time to play, and games can be played over a long time period.", clip("c9-catchup-a", 2, 19)),
    L("The base setup is cooperative: any player can change anything on the table, and every action can be traced and undone.", shot("c9-tree3")),
]})

chap(1)
S.append({"id": "c1-start", "chapter": 1, "lines": [
    L("Install the app and create an account.", clip("c1-register", 0, 16, 3), hold=5.4),
    L("On first launch the app downloads and imports the card catalog.", clip("c1-onboard", 1.5, 9),
      [rect(30, 760, 1020, 760, 0.3, 2.6)], hold=5.5),
    L("The main menu has your games, pods for drafting, decks, and settings.", clip("c1-avatar", 6, 22, 2),
      [rect(40, 630, 1010, 1180, 5.0)], hold=8.2),
    L("When there's a new version, the app asks you to update before you can play."),
]})

chap(2)
S.append({"id": "c2-decks", "chapter": 2, "lines": [
    L("To add a deck, paste a decklist or an Archidekt link.", clip("c2-import", 0, 27, 4),
      [rect(20, 150, 1040, 120, 0.2, 2.0)], hold=6.8,
      say="To add a deck, paste a decklist or an [Archidekt](/ˈɑɹkɪdɛkt/) link."),
    L("To start a game, choose an opponent.", clip("c2-newgame", 2, 33, 4), [circle(975, 536, 70, 4.6, 6.5)], hold=7.8),
    L("Game starts after both players have picked a deck in the lobby, adjusted the sideboard, and pressed Ready.",
      clip("c2-newgame", 33, 72, 4), hold=9.8),
]})

chap(3)
T = shot("c3-table-a")
S.append({"id": "c3-table", "chapter": 3, "lines": [
    L("Your side is at the bottom. Lands go in the row nearest you, everything else above them.", T,
      [rect(15, 1475, 1050, 160, 2.2), label(1050, 1460, "Lands", 2.2, "end"), rect(15, 1120, 1050, 345, 3.6), label(1050, 1105, "Everything else", 3.6, "end")]),
    L("Your hand, library, graveyard and exile are along your edge.", None,
      [rect(15, 1655, 1050, 175, 0.3), label(1050, 1640, "Hand", 0.3, "end"), rect(150, 1875, 470, 150, 1.5)]),
    L("The purple area is the Stack.", None, [circle(935, 1115, 140, 0.3)]),
    L("Two different things are shown: whose turn it is and whose move it is."),
    L("The turn player's battlefield is tinted.", None, [rect(15, 1110, 1050, 560, 0.3)]),
    L("The top and bottom bars take the color of whoever has the move, and the pill at the top says \"Your move\" or \"Waiting\".", None,
      [rect(5, 5, 1070, 120, 0.3, 4.0), rect(5, 2200, 1070, 135, 0.3, 4.0), circle(236, 83, 120, 4.2)]),
    L("At the bottom: the step button, quick actions for this step, and a strip showing the most recent events.", None,
      [rect(880, 2200, 195, 135, 0.6), rect(160, 2200, 715, 135, 2.2), rect(5, 2090, 1070, 85, 3.8)]),
]})

chap(4)
S.append({"id": "c4-controls", "chapter": 4, "lines": [
    L("Drag a card to put it anywhere.", clip("c4-drag", 1.5, 6), hold=4.5),
    L("Tap a card to open its menu. Swipe or use the arrows to change pages: everyday actions, counters, and more.",
      clip("c4-menu", 2, 19, 1.8), hold=9.4),
    L("Double tap a permanent to tap or untap it.", clip("c4-tap", 2, 8), zoom={"x": 0, "y": 1100, "w": 1080, "h": 1170}, hold=6),
    L("In your hand, double tap plays the card: a land goes to your land row, permanents to the battlefield and other spells to the Stack.",
      clip("c4-hand", 2, 9.7), zoom=None, hold=8.5),
    L("Hold any card to inspect it.", clip("c4-inspect", 2, 7.5), hold=5.5),
    L("Double tap your library to draw. Tap it for more actions.", clip("c4-library", 2, 10.7), hold=8.7),
    L("Drag on your life total or a counter to change it.", clip("c4-life", 1.5, 6.7), zoom={"x": 0, "y": 1380, "w": 1080, "h": 960}, hold=5.2),
    L("Pinch to zoom, two-finger drag or double-tap-drag to pan.", clip("c4-zoom", 2, 14.5, 1.4), zoom=None, hold=8.9),
    L("Drag to select multiple cards to act on them all at once.", clip("c4-select", 2, 15, 1.5), hold=8.7),
    L("You can also use the multiselect mode to make multiselections.", clip("c4-multiselect", 2.5, 14, 1.4),
      [circle(980, 1563, 75, 0.6, 2.5)], hold=8.2),
]})

chap(5)
S.append({"id": "c5-turns", "chapter": 5, "lines": [
    L("The step button moves the game to the next step, hold it to end the turn.", clip("c5-endturn-a", 1.5, 7.8),
      [rect(880, 2200, 195, 135, 0.4)], hold=6.3),
    L("Quick actions cover the common things in each step, like \"Untap, draw\" at the start of your turn.", clip("c5-turn-a", 2, 9),
      [rect(160, 2200, 715, 135, 0.3)], hold=7),
]})
S.append({"id": "c5-move", "chapter": 5, "layout": "two-phones", "devices": ["You", "Opponent"], "lines": [
    L("Passing the move is how you tell your opponent it's their turn to act.", [clip("c5-turn-a", 10.5, 22.9), clip("c5-turn-b", 10.1, 22.5)],
      [circle(236, 83, 120, 1.7, None, 0), circle(236, 83, 120, 2.2, None, 1)], hold=6),
    L("Turn and move tracking is informative: it may affect shown quick actions, but all actions are always available via some menu."),
]})

chap(6)
S.append({"id": "c6-stack", "chapter": 6, "lines": [
    L("Spells and abilities go on the Stack, the purple area. Double tap a spell in your hand or drag it there.", clip("c6-cast-a", 2, 10),
      [circle(935, 1115, 140, 3.0)]),
    L("For an ability, choose Stack from the card's menu.", clip("c6-ability", 12.5, 21), hold=6),
    L("The Stack holds linked copies, so the real card stays where it is. A card cast from your hand is shown to both players while it's on the Stack.",
      clip("c6-cast-b", 3, 15)),
    L("Tap the top of the Stack for its bubbles. Battlefield and Graveyard resolve it: the copy leaves the Stack and the real card moves there.", shot("c6-bubbles"),
      [circle(994, 976, 60, 2.6), label(930, 960, "Battlefield", 2.6, "end"), circle(994, 1365, 60, 4.0), label(930, 1460, "Graveyard", 4.0, "end")],
      zoom={"x": 300, "y": 700, "w": 780, "h": 900}),
    L("Exile does the same to exile. Remove only takes the copy off the Stack, which is what you want when an ability resolves.", None,
      [circle(844, 1365, 60, 0.3), label(780, 1460, "Exile", 0.3, "end"), circle(844, 976, 60, 2.6), label(780, 900, "Remove", 2.6, "end")]),
    L("Drag the side handle up or down to mark which part of the text you're using: a mode, an ability, or one half of a split card. Your opponent sees the mark.",
      clip("c6-cast-a", 13.5, 23), [circle(843, 1170, 60, 0.3, 2.0)], hold=10.6),
    L("To respond, put your own card on top and pass the move.", clip("c6-cast-b", 40, 49), zoom=None, hold=7),
    L("Resolve from the top down; after each one, the next card's bubbles open by themselves.", clip("c6-resolve", 1.5, 14), hold=7.5),
    L("Double tap the Stack to see every entry, remove one from the middle, or copy one.", clip("c6-manage", 2, 31, 3), hold=9.8),
    L("Tap \"Ping\" on any card to point at it, for example to show a target.", clip("c6-ability", 26, 37, 1.5), hold=7.4),
]})

chap(7)
S.append({"id": "c7-combat", "chapter": 7, "lines": [
    L("Combat happens in its own area in the middle of the table.", clip("c7-attack-a", 2, 7)),
    L("Press Select attackers, pick your creatures, and declare them. Like on the Stack, these are linked copies; the real cards stay in place.",
      clip("c7-attack-a", 3, 16), [rect(160, 2200, 360, 135, 0.4, 2.6)], hold=9),
    L("Each attacker sits in a lane that shows what it's attacking. By default that's your opponent.", clip("c7-attack-a", 16, 20),
      [rect(300, 880, 400, 120, 0.4)]),
    L("To attack a planeswalker or a battle, tap the attacker, choose Attack, and pick the card. It moves into a new lane labeled with that target.",
      clip("c7-attack-a", 19.5, 39, 1.8), hold=10.8),
    L("You can also drag an attacker from one lane to another.", clip("c7-attack-a", 41.5, 48), hold=5.5),
    L("When the game moves to blockers, the move passes to the defender. They drag their creatures onto the attackers they block.",
      clip("c7-block-b", 0.5, 52, 6), [circle(236, 83, 120, 0.3, 2.5)], hold=8.6),
    L("Damage is up to you: mark it on creatures, lower life totals, and lower a planeswalker's loyalty, the number in its corner. Then close the combat.",
      clip("c7-damage-a", 21.5, 33, 1.2), hold=10.5),
]})

chap(8)
S.append({"id": "c8-library", "chapter": 8, "lines": [
    L("Search, Look and Reveal open a panel on the left side. Drag cards out, or tap them for quick moves.", clip("c8-search", 1.5, 18.5, 1.6)),
    L("Closing a search shuffles your library."),
]})
S.append({"id": "c8-reveal", "chapter": 8, "layout": "two-phones", "devices": ["You", "Opponent"], "lines": [
    L("Reveals show to both players; looking and searching are private.", [clip("c8-look-a", 7, 21), clip("c8-look-b", 6.7, 20.7)], hold=11),
]})
S.append({"id": "c8-tokens", "chapter": 8, "lines": [
    L("Tokens come from the table actions menu, and you can favorite the ones you use often.", clip("c8-tokens", 2, 32.5, 3.3), hold=9.4),
]})

chap(9)
S.append({"id": "c9-history", "chapter": 9, "lines": [
    L("Every action is recorded.", shot("c3-table-a"), [rect(5, 2090, 1070, 85, 0.3)]),
    L("When you open a game, the app plays back what your opponent did since you last looked, so you see what happened instead of only where things ended up.",
      clip("c9-catchup-a", 1, 18)),
    L("Playback pauses on important moments, like a spell going on the Stack or a reveal.", clip("c9-catchup-a", 15.5, 22),
      [circle(985, 1170, 140, 3.0), rect(140, 2200, 800, 135, 3.0)], hold=6),
    L("You choose which moments in Settings.", shot("c9-settings"), [rect(20, 540, 1040, 1230, 0.4)]),
    L("Tap the timeline button to enter timeline mode. Step back and forth through events, or let it run automatically. Hold a card at any point to read it.",
      clip("c9-branch-a", 1.5, 16), [circle(84, 2267, 75, 1.5)]),
    L("Made a mistake? Undo takes back your latest action.", clip("c9-undo", 1.5, 10.5), [circle(89, 2267, 75, 3.0)], hold=6),
    L("That only works while your opponent's later actions would still play out exactly the same. If undoing would change what they did, the app refuses, and you fix things by hand."),
]})
S.append({"id": "c9-branch", "chapter": 9, "layout": "two-phones", "devices": ["You", "Opponent"], "lines": [
    L("Branching is for \"wait, I wanted to respond to that.\"", [clip("c9-catchup-a", 2, 34), clip("c9-turn1-b", 2, 79, 4)]),
    L("Example: you open the game and watch your opponent's turn. They cast Opt, looked at their top card, drew it, and then attacked with a creature."),
    L("You wanted to Shock that creature at the start of their turn, before all of that."),
    L("Step back in the timeline to their upkeep, choose Continue from here, and cast Shock. That starts a new branch, and it becomes the game.",
      [clip("c9-branch-a", 2, 38, 3.4), clip("c9-branch-b", 3, 62, 5.6)], [circle(722, 63, 70, 4.4, 6.5, 0)], hold=10.8),
    L("Nothing is lost. The old line still exists in the game tree.", [shot("c9-tree"), clip("c9-branch-b", 62, 66)],
      [rect(20, 980, 870, 280, 0.4, None, 0)]),
    L("On your opponent's phone, the game shows \"Opponent branched\", rewinds to where you went back, and plays your Shock. Now they play their turn again.",
      [shot("c9-tree3"), clip("c9-branched-b", 1.5, 25, 2.2)], [rect(310, 2070, 460, 100, 0.8, 3.5, 1)], hold=10.7),
]})
S.append({"id": "c9-opt", "chapter": 9, "layout": "two-phones", "devices": ["Old line", "New line"], "lines": [
    L("When they cast Opt this time, they probably see a different card.", [shot("c9-opt1"), shot("c9-opt2")],
      [circle(70, 700, 120, 0.8, None, 0), circle(70, 700, 120, 0.8, None, 1)]),
    L("A new branch reshuffles every library card whose position nobody knows. What your opponent saw in the old line tells them nothing about the new one."),
    L("Cards with a known position stay put, like a card someone put on top before the branch point."),
    L("This only happens when a new branch starts. Undoing a draw and drawing again gives the same card, and branching again from the same point gives the same order, so going back can't be used to fish for a better draw."),
]})
S.append({"id": "c9-tip", "chapter": 9, "lines": [
    L("A tip that follows from this: when your spell would reveal cards from your opponent's hand or a face-down card, don't look yourself. Cast it and pass the move.",
      clip("c9-tip-a", 2, 10.2)),
    L("Your opponent gets the chance to respond first. And if they go back to respond, you haven't already seen their cards.", shot("c9-tip-b"),
      [circle(236, 83, 120, 0.4)]),
]})

chap(10)
S.append({"id": "c10-draft", "chapter": 10, "lines": [
    L("Pods are for drafting together. Create one from a cube, for example a Cube Cobra link, or from a set's boosters.", clip("c10-cube", 2, 28, 3), hold=8.7),
    L("Choose a format: classic boosters, Glimpse, or Grid. Then invite the other players.", clip("c10-pod", 2, 58.5, 6),
      [rect(20, 560, 660, 100, 0.3, 2.0)], hold=9.4),
    L("All packs open at once, so nobody waits for a pack to be passed.", clip("c10-start", 2, 14.5)),
    L("To pick, swipe a card right to keep it or left to burn it, then confirm. Hold a card to see it big.", clip("c10-pick", 2, 21, 1.4), hold=13.6),
    L("If the pod has Cogwork Librarians, tap the book to keep one extra card from that pack.", clip("c10-pick2", 1.5, 15.4),
      [circle(330, 2141, 75, 0.3, 2.5)], hold=8.3),
    L("When the draft is done, build your deck: cards are sorted into columns by mana value, basic lands are added from the counters at the top, and the Stats tab shows your curve and colors.",
      clip("c10-build", 2, 31.2, 2.4), [rect(120, 370, 840, 120, 4.8, 7.5)], hold=12.2),
    L("Submit, then challenge another pod member.", clip("c10-submit", 2, 13, 1.6), hold=6.9),
    L("The game starts from the usual lobby.", clip("c10-challenge", 1.5, 8)),
]})

chap(11)
S.append({"id": "c11-end", "chapter": 11, "layout": "two-phones", "devices": ["You", "Opponent"], "lines": [
    L("When the game is over, suggest the result from Table actions.", [clip("c11-suggest-a", 2, 20, 1.5), clip("c11-suggest-b", 1.7, 19.7, 1.5)], hold=12),
    L("Your opponent accepts or declines; until then, the game waits.", [clip("c11-suggest-a", 20, 33), clip("c11-suggest-b", 19.7, 29.1)],
      [circle(614, 1124, 80, 0.4, None, 1)], hold=8),
]})
S.append({"id": "end", "chapter": 11, "layout": "end", "seconds": 3.5})

json.dump({"chapters": chapters, "scenes": S}, open(p, "w"), indent=1, ensure_ascii=False)
