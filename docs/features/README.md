# Feature catalog

The list of everything the app does today that the owner wants kept. It is the
single source of truth for current behaviour: when it disagrees with older spec
text in `docs/design/` or `docs/decisions.md`, the catalog wins.

## Files

| File | Area |
| --- | --- |
| [table.md](table.md) | `TBL` Battlefield, card display, gestures, menus, inspecting cards |
| [zones.md](zones.md) | `ZON` Hand, library, graveyard/exile and its view, hidden information |
| [turns-combat-stack.md](turns-combat-stack.md) | `TRN` Turns, steps, whose move, life, results; `CMB` combat; `STK` Stack |
| [history.md](history.md) | `HIS` Timeline, playback, catching up, assumed answers and branches, undo and undo requests, event texts and icons |
| [lobby.md](lobby.md) | `LOB` Accounts, decks, game list, chat, notifications, settings, updates |
| [draft.md](draft.md) | `DRF` Cubes, pods, drafting, grids, deck building |
| [rules.md](rules.md) | `SYS` Cross-cutting rules every feature follows (authority, performance, policies) |

## Games on engine state (I-D54, I-D56)

Gameplay entries describe games played on engine state with async play (`docs/specs/async-play.md`), which debug builds
turn on with Settings → Automatum → "Engine games (test)". Until the switch-over (I-D56 step D) every other game still
plays on today's model, which keeps its older pauses, passing for an away player and Hold (I-D41) until step D deletes
them; the game tree, Redo, branching from the timeline and the auto-rewind switches are gone from both. The strip,
timeline mode, event texts, icons and chips, change flashes, token pictures, early answers and the shared combat staging
are built on engine state too (step D1); the entries that describe today's model's group texts (HIS-063–HIS-069) hold
for today's model only, engine events reading as HIS-091 says.

## Entry format

```
- **TBL-012** A double tap on empty battlefield without dragging zooms to fit the whole table (empty columns are not counted). (r2)
```

- One line, plain words, what a player sees or can do. Exact numbers the owner
  chose (sizes, delays, limits) are part of the entry.
- IDs are `AREA-NNN`, never renumbered and never reused. A removed feature's
  entry is deleted; git history keeps it.
- The trailing `(rN)` names the round that last decided it, for lookup in history.

## Rules for changing it

- A task changes or removes an entry only when the task names that behaviour.
  If work would change any other entry, stop and ask the owner.
- The implementer updates the entries an agreed change touches, in the same commit
  as the code. The owner reviews catalog and screenshot changes after
  implementation (`scripts/review.sh`); nothing unreviewed is released.
- When the app and an entry disagree, the owner decides which one changes.
- Tests that protect an entry mention its ID (see `AGENTS.md`).
- `checkFeatureCatalog` runs before every test task: IDs must be unique and in their
  area's file, and every ID cited by a test, an entry or the backlog must exist.
