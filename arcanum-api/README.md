# arcanum-api

Card definition models shared by `arcanum-parser` and `arcanum-engine`: a typed
description of what a card's abilities do. Definitions can be produced by the
parser, written to files and loaded without it, or written by hand. No game state
lives here. Design: `docs/specs/arcanum-automatum.md` §4.1 and §5.

## Contents

- `FaceDefinition(name, abilities)`: what one card face does.
- `Ability`: sealed; so far `Keyword` or `UnparsedAbility` (text kept as printed).
- `Keyword`: sealed, one type per keyword ability (CR 702) carrying exactly its
  values: `Keyword.Flying`, `Keyword.Crew(amount)`, `Keyword.Equip(cost)`,
  `Keyword.Suspend(amount, cost)`. Each knows its printed `text` and defining `rule`.
  Keywords written with a quality, object or non-mana cost come later.
- `ManaCost` / `ManaSymbol` (CR 107.4) and `Color` (CR 105.1); a mana cost is
  serialised as its printed text, e.g. `"{2}{W}"`.
- `ComprehensiveRules.EFFECTIVE_DATE`: the one pinned Comprehensive Rules version
  (2026-09-25). Code and tests cite rule numbers of this version (`// CR 613.4c`).

JSON (kotlinx.serialization): `{"name":"…","abilities":[{"type":"flying"},
{"type":"equip","cost":"{2}{W}"},{"type":"unparsed","text":"…"}]}`.

Types are sealed hierarchies rather than enums with values (owner preference).

## Rules

- Generic MTG code: no dependency on any ATW module (`verifyModuleBoundaries`).
- Kotlin Multiplatform common code only.
