# Feature catalog

The list of everything the app does today that the owner wants kept. It is the
single source of truth for current behaviour: when it disagrees with older spec
text in `docs/design/` or `docs/decisions.md`, the catalog wins.

## Files

| File | Area |
| --- | --- |
| [table.md](table.md) | `TBL` Battlefield, card display, gestures, menus, tokens, links |
| [zones.md](zones.md) | `ZON` Hand, library, graveyard/exile, temp piles, panels, hidden information |
| [turns-combat-stack.md](turns-combat-stack.md) | `TRN` Turns, steps, whose move, life, dice, results; `CMB` combat; `STK` Stack |
| [history.md](history.md) | `HIS` Timeline, playback, branches, undo/redo, event texts and icons, pings |
| [lobby.md](lobby.md) | `LOB` Accounts, decks, game list, chat, notifications, settings, updates |
| [draft.md](draft.md) | `DRF` Cubes, pods, drafting, grids, deck building |
| [rules.md](rules.md) | `SYS` Cross-cutting rules every feature follows (authority, performance, policies) |

## Entry format

```
- **TBL-012** Double-tapping a library draws its top card into its owner's hand. (r1)
```

- One line, plain words, what a player sees or can do. Exact numbers the owner
  chose (sizes, delays, limits) are part of the entry.
- IDs are `AREA-NNN`, never renumbered and never reused. A removed feature's
  entry is deleted; git history keeps it.
- The trailing `(rN)` names the round that last decided it, for lookup in history.
- `(?)` marks an entry not yet confirmed by the owner or not verified against the
  app; it is still protected.

## Rules for changing it

- A task changes or removes an entry only when the task names that behaviour.
  If work would change any other entry, stop and ask the owner.
- The owner approves the exact text of new and changed entries before they are
  built. Details an implementer had to choose are marked `(?)` until the owner
  reviews them; nothing with an unreviewed `(?)` is released.
- When the app and an entry disagree, the owner decides which one changes.
- Tests that protect an entry mention its ID (see `AGENTS.md`).
- `checkFeatureCatalog` runs before every test task: IDs must be unique and in their
  area's file, and every ID cited by a test, an entry or the backlog must exist.
