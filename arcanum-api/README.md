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
- Bloomburrow (set `blb`): gift (`Keyword.Gift`, `Gifted`, `CostPart.ChoosePlayer`, `Players.ChosenAsCost`,
  `EventPattern.GiveGift`, `TargetSlot.onlyIf`), Classes (`Keyword.ClassLevel`, `Effect.SetClassLevel`,
  `EventPattern.BecomesLevel`), forage (`EventPattern.Forage`), pawprint modes (`Modes.pawprintLimit`,
  `Mode.pawprints`), and smaller pieces (`MayPlay.spellGets`, `CountersPut.causedBy`/`PlayerCountersPut.causedBy`,
  `ZoneChange.notTo` for leaving the battlefield without dying, `Choice.Objects.onePer` for "for each card type, … a card
  of that type", `ObjectFilter.XInManaCost`, `ObjectFilter.NthSpellThisTurn` for "the first instant spell you've cast
  this turn", `ActivationRules.payManaBy` with `ManaPaymentWay.TapCreaturesForAnyMana` for tapping creatures rather than
  paying an activation's mana).
- Return to Ravnica (set `rtr`): scavenge and unleash (`KeywordExpansions` entries, CR 702.97a, 702.98a), and smaller
  pieces: `CostObjects.Choose.shared` ("two nonland cards with the same name"), `Choice.Colors` ("choose two colors") with
  `Value.SharedColors` ("1 life for each of the chosen colors it is"), `EventPattern.Discard.causedBy` ("if a spell or
  ability an opponent controls causes you to discard this card"), `ObjectFilter.ThisTurn.role` ("a creature that dealt
  damage this turn": the source of a damage event).
- Gatecrash and Dragon's Maze (sets `gtc`, `dgm`): extort, evolve and cipher (`KeywordExpansions` entries, CR 702.101a,
  702.100a, 702.99a); fuse is the engine's (a way to cast a split card). Smaller pieces: `EventPattern.Attack.withOthers`
  (battalion), `EventPattern.AttacksUnblocked.target` ("attacks a player and isn't blocked"), `EventPattern.Draw.exceptFirstIn`
  ("except the first one they draw in each of their draw steps"), `ObjectFilter.Blocking`/`BlockedBy`,
  `Mechanic.TargetableDespiteHexproof.by`, `Mechanic.CanOnlyAttackAlone`, `Mechanic.ChangeText` with `TextWord` (CR 612),
  `Modification.EntersAs` ("enters … as a Mutant"), `CopyException.ThisAbility`, `Value.GreatestFor` ("the greatest number
  of cards a player discarded"), `CostPart.RemoveCounters.distributed` ("from among creatures you control"),
  `Effect.RemoveCounters.bind`, `TargetSlot.sameOwner` ("from a single graveyard") and `TargetSlot.atRandom`.
- Reality Fracture (set `fra`): preparation cards (CR 722: `Layout.Prepare`, `ObjectMark.Prepared` set with `Effect.Mark`,
  `Modification.EntersMarked` for "enters prepared", `ObjectFilter.PrepareSpell`), empower Jace (CR 701.71a:
  `KeywordAction.EmpowerJace`, written out by `KeywordExpansions` with `KeywordExpansions.JACE_TOKEN`), the Heartwood token
  (CR 111.10x), split second (a `KeywordExpansions` entry, with `Mechanic.CantActivate` for players), and smaller pieces:
  `EventPattern.Activate.kind`/`removedLoyaltyAtLeast` ("whenever you activate a loyalty ability", "if you removed two or more
  loyalty counters"), `Condition.HappenedLastTurn`, `Value.Quotient` ("for every seven cards"), `SharedQuality.PlaneswalkerType`,
  `ObjectFilter.HasDealtCombatDamage`, `Choice.Objects.totalAtMost` ("total mana value 6 or less"), `TargetSlot.onePer`
  ("one target of each card type"), `Keyword.Equip.costChange`, `Duration.UntilCast`, `Modification.CreateInstead` ("that many
  Dragons are created instead"), and the mechanics `AnyNumberInDeck`, `NoGraveyardForZeroLoyalty`, `LegendRuleDoesntApply`,
  `LoyaltyAtInstantSpeed`, `AssignsDamageAsThoughPowerPositive` and `EventDoesntTrigger`.
- The Hobbit (set `hob`): storied (CR 702.195: a `KeywordExpansions` entry giving `Designation.EnduringStory` through
  `Mechanic.GetsDesignation`), recruit (CR 701.70a: `KeywordAction.Recruit`, written out with `KeywordExpansions.RECRUIT_TOKEN`), and
  smaller pieces: `Mechanic.AbilityAlternativeCost` ("you may pay {0} rather than pay the equip cost of the first equip ability you
  activate each turn"), `MayPlay.condition` ("you may play them if you control a Wizard"), `Value.ManaSpent.source` ("if mana from a
  Treasure was spent"), `Trigger.On.batchTotal` ("whenever you attack with creatures with total power 12 or greater"),
  `ObjectFilter.Where` with `Condition.Odd` and `Condition.RememberedOption` ("a spell with mana value of the chosen quality"),
  `Effect.SeparatePiles.faceDownPile`; "its controller amasses" is a loop over that player (`DefinitionChecks` allows a single keyword
  action in such a loop, since the action takes no players).
- Marvel Super Heroes (set `msh`): power-up (CR 702.193, an ability label; `Mechanic.LabelAdditionalUses` for "can be activated an
  additional time"), teamwork (CR 702.194: an optional additional cost tapping creatures by total power; `Tap.forCost` for "becomes
  tapped to pay a teamwork cost"), harness and ∞ (CR 701.64, 702.186: `ObjectMark.Harnessed`, `AbilityLabel.Infinity` working only
  while harnessed), sneak (CR 702.190: an alternative cost with `AlternativeCost.duringYour`, `KeywordExpansions.SNEAK_RETURNED`),
  `EventPattern.Connive`, `CountersPut.reaching` ("when the fourth plan counter is put on"), `BecomesTarget.players`,
  `ObjectFilter.AttackingAlone` and `TargetedBy`, `NumericProperty.ManaSymbols`, `Modes.maxCount` ("choose up to X"),
  `TargetSlot.totalAtMost`, `CostPart.GetCounters` (ward—get poison counters), `Effect.OneMoreCounterOfEachKind`,
  `Effect.ControlNextTurn` (CR 723), and the mechanics `CantBecomeUntapped`, `AbilitiesIgnoreSummoningSickness` and the
  modification `HealsOtherDamage` (CR 701.69a).
- Teenage Mutant Ninja Turtles (set `tmt`): the Mutagen token (CR 111.10v, `PredefinedTokens`), `ObjectFilter.CrewedThisTurn`
  ("target creature that crewed it this turn", CR 702.122c), `Measure.Distinct` ("for each card type among spells you've cast this
  turn", each event's object as it was) and `Keyword.Equip.perTurn` ("Equip—Sacrifice another nonland permanent. Activate only once
  each turn"); sneak's "if his sneak cost was paid" is `Condition.Paid` of the sneak keyword.

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
