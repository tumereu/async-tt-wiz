# arcanum-api

Card definition models shared by `arcanum-parser` and `arcanum-engine`: a typed
description of what a card's abilities do. Definitions can be produced by the
parser, written to files and loaded without it, or written by hand. No game state
lives here. Design: `docs/specs/arcanum-automatum.md` §4.1 and §5.

## Contents

The definition model (overview in `Definitions.kt`):

- `CardDefinition(layout, faces)`; `FaceDefinition`: name, mana cost, colour
  indicator, supertypes/types/subtypes, P/T, loyalty, defense, abilities.
- `Ability`: `Spell`, `Activated`, `Triggered`, `Static` (the CR kinds), `Keyword`,
  `Unparsed`. Spell/activated/triggered carry `Instructions` (targets, modes,
  `Step`s of one-shot `Effect`s); static abilities carry `Mechanic`s.
- `Effect` (one-shot, CR 610) vs `Mechanic` (continuous, CR 611): each mechanic
  states its layer and how duplicates combine (`Stacking`: redundant, additive,
  independent). `Effect.ApplyMechanics` creates a continuous effect with a
  `Duration`.
- `Keyword`: one sealed type per keyword with exactly its values; `KeywordKind`
  names a keyword without values for filters ("creatures with flying").
  `KeywordExpansions` is the shared table mapping keywords to abilities built from
  mechanics (flying → `CanBeBlockedOnlyBy`, deathtouch → `Deathtouch`); a keyword without an entry is
  null there (the engine then has it done by hand); ability labels (exhaust, boast, …)
  add their rules through `withLabelRules`; keyword actions expand through
  `KeywordExpansions.expand(action)`; `PredefinedTokens` defines Treasure, Food, Clue,
  Blood and the other tokens of CR 111.10.
  Mechanics that are only a keyword's meaning are `Mechanic.KeywordOnly` and
  appear only there; `DefinitionChecks.problems` reports them in definitions.
- Building blocks: `Cost`/`CostPart`, `Trigger`/`EventPattern`, `Objects`/
  `ObjectFilter`, `Players`/`PlayerFilter`, `Value`, `Condition`, `Duration`,
  `ManaProduction`, `TokenSpec`, `KeywordAction`.
- `Ability.isManaAbility()`: whether an ability is a mana ability (CR 605.1a/b), derived from its shape.
- `ManaCost`/`ManaSymbol` (CR 107.4), `Color`, and basic vocabularies (`CardType`,
  `Zone`, `TurnStep`, `CounterKind`, `Status`, …).
- `ComprehensiveRules.EFFECTIVE_DATE`: the pinned rules version (2026-09-25).
- Duskmourn (set `dsk`): Rooms (CR 709.5: `Door`, `ObjectMark.Unlocked`, `Effect.UnlockDoor`/`LockDoor`,
  `EventPattern.Unlock`, `NumericProperty.UnlockedDoors`, `AbilityKind.Unlock`), manifest dread
  (`KeywordAction.ManifestDread`, `EventPattern.ManifestDread`, `ObjectMark.Manifested`, `Mechanic.CantBeTurnedFaceUp`),
  impending (a `KeywordExpansions` entry), Leylines (`Mechanic.MayBeginOnBattlefield`), and smaller pieces
  (`Triggered.once`, `TargetSlot.sameController`, `Choice.Objects.distinct`, `ObjectFilter.SingleTarget`,
  `Condition.Prime`, `AlternativeCost.perTurn`, combat and player mechanics such as `MustBeBlocked`), a prevention
  effect's additional effect (`Mechanic.Prevention.additional`, CR 615.5), what a permanent is as it enters
  (`Destination.entersAs`, CR 611.2e), "repeat this process" (`Step.RepeatWhile`) and copies of cards
  (`Effect.CopyCard`, CR 707.12).

`ExampleCardsTest` shows about twenty invented cards written in the model and their
JSON. Decisions from the API reviews (D19–D49) are built here where they concern card
definitions; the runtime types they describe (object ids, Stack items and
sequences, effect objects, ledgers, tagged events, timestamps) belong to the engine
API and are not built yet. `DefinitionChecks.problems` reports definitions the
types allow but the model forbids.
Types are sealed hierarchies rather than enums with values (owner preference).
A field has a default only when the default is empty or absent (`null`, `false`, an empty list, `0` meaning none):
who, how many and which objects are always written out (D51).

## Rules

- Generic MTG code: no dependency on any ATW module (`verifyModuleBoundaries`).
- Kotlin Multiplatform common code only.
