# arcanum-api

Card definition models shared by `arcanum-parser` and `arcanum-engine`: a typed
description of what a card's abilities do. Definitions can be produced by the
parser, written to files and loaded without it, or written by hand. No game state
lives here. Design: `docs/specs/arcanum-automatum.md` §4.1 and §5.

## Contents

- `ComprehensiveRules.EFFECTIVE_DATE`: the one pinned Comprehensive Rules version
  (2026-09-25). Code and tests cite rule numbers of this version (`// CR 613.4c`).

The definition models arrive with the first parsed wording groups.

## Rules

- Generic MTG code: no dependency on any ATW module (`verifyModuleBoundaries`).
- Kotlin Multiplatform common code only.
