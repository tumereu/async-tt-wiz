# Backlog

Changes the owner wants but that are not built yet. The feature catalog
(`docs/features/`) describes the app as it is; this file holds what should change.

- Entries are added only with the owner's approval, one line each, naming the
  catalog entries they would change.
- Building an entry deletes it from here and updates the catalog in the same commit.
- A larger change has its spec in `docs/specs/`; building it moves the behaviour into the catalog and deletes the spec.

## Ready to build

(nothing right now)

## Specified, not started

(nothing right now)

## Needs discussion

(nothing right now)

## Later (nice to have)

- Choose a card's printing (art) in decks and cubes.
- Arcanum engine: a safety cap in `perform` — after an excessive number of changes or state-based/trigger rounds in one call, stop with a `Manual("possible loop")` decision instead of hanging (no catalog entry; engine library).
- Arcanum engine: detect mandatory loops (CR 104.4b, 732.4) — the same state (ignoring ids/timestamps) recurring after only forced actions between priority rounds is reported as a draw; ATW's automatic passing stops on a repeated state (no catalog entry yet; ATW part with the integration).
