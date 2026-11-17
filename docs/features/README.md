# Feature catalog

The list of everything the app does today that the owner wants kept. It is the
single source of truth for current behaviour.

## Files

One feature lives in one place. Files follow what a player meets, not the ID's
area: an entry keeps its ID when it moves to another file, so `HIS-…` entries
are found in `undo.md` and `async-play.md` too. Search by ID across the folder.

| File | What it covers |
| --- | --- |
| [rules.md](rules.md) | Cross-cutting rules: product scope, server authority and sync, connection and errors, performance, versions, the player access policy, shared UI rules |
| [table.md](table.md) | The battlefield, pan and zoom, how cards look, card sizes, animations, gestures, dragging and stacks, card menus, inspecting cards, cards Automatum makes |
| [zones.md](zones.md) | Hand, library, graveyard and exile and their view, moving cards between zones, hidden information |
| [turns.md](turns.md) | Turns and steps, the top and bottom bars, whose turn and move, the main button and quick actions, the opening and mulligans, life, player chips, presence, results |
| [combat.md](combat.md) | The combat band, attack targets, declaring attackers and blockers, blocks, combat damage, combat holds |
| [stack.md](stack.md) | The Stack and its entries, target arrows, the Stack list, the other player's plays shown |
| [casting.md](casting.md) | Cast chips and the cast panel, ways and modes, targets, paying and autotap |
| [priority-and-choices.md](priority-and-choices.md) | Priority and response modes, Automatum's choices and panels, reveals |
| [async-play.md](async-play.md) | Waiting for the other player, assumed answers, the information rule, branches and lines of play, moves on an out-of-date board, the assumed keep |
| [history.md](history.md) | Events and their texts, playback and catching up, playback stops, the event chip, timeline mode, playback settings |
| [undo.md](undo.md) | Undo, free undo and approval, the Undo dialog, undo requests |
| [lobby.md](lobby.md) | Accounts, profile, navigation, the game list, new games, the game lobby, chat, game history, notifications |
| [decks.md](decks.md) | The deck library, the deck and cube editor, preparing a deck for a game |
| [app.md](app.md) | Settings, updates, What's new, problem reports, card records and pictures, screens and dialogs |
| [draft.md](draft.md) | Cubes, pods, drafting, grids, deck building, pod games |
| [web.md](web.md) | The web client: addresses and Back, loading and updates, what differs in a browser, the desktop table |

## Games on engine state

Every game is played on engine state with async play (each player acts only for themselves; the other player's
answers may be assumed and are confirmed or branched by their real answer). The entries describe that; the old
model, where the table was changed by hand, is gone with its entries.

## File shape

Each file starts with its title, one line saying what it covers, and a `## How it works` overview: 3–8 plain
sentences, no IDs, so a person understands the feature before the details. Then `##` sections in the player's flow:
what you see first, then what you do, then the edge cases.

## Entry format

```
- **TBL-012** A double tap on empty battlefield without dragging zooms to fit the whole table: both battlefields' rows and every card on them. (r53)
```

- One fact per entry, one line, plain words: what a player sees or can do.
- Aim for at most 300 characters; over 500 only for a single rule that can't be cut (a list, say). A longer
  description splits into several entries.
- Each exact number the owner chose (sizes, delays, limits, defaults) lives in one entry; other entries name the
  feature or refer to that entry ("(see TBL-047)"), so a change is made in one place.
- Cross-references only where they are needed to understand the entry; no chains of IDs in parentheses.
- IDs are `AREA-NNN`, never renumbered and never reused. A new entry takes the next number never used in its area
  (check git history: deleted IDs stay used). A removed feature's entry is deleted; git history keeps it.
- The trailing `(rN)` names the round that last decided it, for lookup in history; parts split from an entry
  keep its tag.

## Rules for changing it

- A task changes or removes an entry only when the task names that behaviour.
  If work would change any other entry, stop and ask the owner.
- The implementer updates the entries an agreed change touches, in the same commit
  as the code. The owner reviews catalog changes after implementation
  (`scripts/review.sh catalog`) and screenshots in the gallery
  (`scripts/aa-gallery.sh`); nothing unreviewed is released.
- When the app plainly fails to do what an entry says (a regression), the app is
  fixed and the entry kept (see `AGENTS.md`). When it is unclear which of the two
  is right, the owner decides which one changes.
- Tests that protect an entry mention its ID (see `AGENTS.md`); an entry split in two is cited by the tests of
  each part.
- `checkFeatureCatalog` runs before every test task: IDs must be unique across the catalog's files, and every ID
  cited by a test, an entry or the backlog must exist.
