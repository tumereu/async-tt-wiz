# arcanum-engine

The rules engine: computes derived state (layers, P/T, keywords), applies rules where
nothing needs deciding, asks for decisions where something does, and returns
MTG-level changes. It owns the API its callers use and knows nothing about any host
app. Design: `docs/specs/arcanum-automatum.md` §4.1 and §7.

## Status

Empty. The first step is an API proposal (game state in MTG terms, calls, results,
decision requests as data) for owner review; then the derived view for static P/T.

## Rules

- Decisions are data, not callbacks: the engine keeps no session between calls.
- No hidden memory: anything to remember is returned to the caller as a visible object.
- Tests cite Comprehensive Rules numbers of the pinned version (`// CR 613.4c`).
- Generic MTG code: no dependency on any ATW module; Kotlin Multiplatform common code.
