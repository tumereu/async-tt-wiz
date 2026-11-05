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

## Games on engine state

Every game is played on engine state with async play (each player acts only for themselves; the other player's
answers may be assumed and are confirmed or branched by their real answer). The entries describe that; the old
model, where the table was changed by hand, is gone with its entries.

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
