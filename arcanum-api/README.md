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
  null there (the engine then carries it out in its own code, or has it done by hand); ability labels (exhaust, boast, …)
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
- Secrets of Strixhaven (set `sos`): paradigm (CR 702.192: `Keyword.Paradigm`, written out by `KeywordExpansions` with
  `Condition.ResolvedEarlierThisGame`), increment (CR 702.191), casualty (CR 702.153) and miracle (CR 702.94) as `KeywordExpansions`
  entries, and smaller pieces: `Mechanic.CantBeCopied` ("This spell can't be copied"), `Value.Exponential` ("2ˣ cards"),
  `Duration.UntilEndOfNextTurnOf` ("until the end of their next turn"), `Effect.RevealUntil.totalAtLeast` ("until you exile cards with
  total mana value 4 or greater"); `DefinitionChecks` lets a spell's cost statics read its announced X ("As an additional cost to cast
  this spell, pay X life").
- Teenage Mutant Ninja Turtles (set `tmt`): the Mutagen token (CR 111.10v, `PredefinedTokens`), `ObjectFilter.CrewedThisTurn`
  ("target creature that crewed it this turn", CR 702.122c), `Measure.Distinct` ("for each card type among spells you've cast this
  turn", each event's object as it was) and `Keyword.Equip.perTurn` ("Equip—Sacrifice another nonland permanent. Activate only once
  each turn"); sneak's "if his sneak cost was paid" is `Condition.Paid` of the sneak keyword.
- Lorwyn Eclipsed (set `ecl`): blight (CR 701.68: `KeywordAction.Blight(players, count)`, a cost part or an effect whose bind names
  "the blighted creature"), `KeywordAction.Behold.count` ("Behold three Elementals", now required), `CostPart.Choose` (a creature type
  chosen with the costs), `Choice.CreatureTypeAmong` ("choose Elemental, Elf, … or Treefolk"), `ManaKind.EachColorAmong` ("for each color
  among permanents you control, add one mana of that color"), `Mechanic.XAtMost` ("X can't be greater than …"),
  `Mechanic.LoseAllCreatureTypes` and `TargetSlot.sharing` ("two target creature cards that share a creature type"). Vivid is an
  ability word; "the number of colors among permanents you control" is `Value.CountDistinct` by colour.
- Marvel's Spider-Man (set `spm`): web-slinging and mayhem as `KeywordExpansions` entries (CR 702.188a, 702.187b; `Keyword.Mayhem.cost`
  null is mayhem without a cost, CR 702.187c; `KeywordExpansions.webSlinging` and `WEB_SLINGING_RETURNED`, kept with `COST_NAMES` for the
  permanent: "the returned creature"), `ObjectFilter.Modified` (CR 700.9), `Effect.CopySpell.except` ("except the copy isn't
  legendary"), `TargetSlot.distinct` ("with different names"), `EventPattern.PlayLand.from`/`notFrom`, `Trigger.On.throughNth` ("your
  first or second card each turn"), `Value.TurnsTaken` ("during your first, second, or third turns"), `Modification.EntersAsCopy.bind`
  (the copied card for "When you do, exile that card"); `DefinitionChecks` allows looking at cards as a permanent enters.
- Avatar: The Last Airbender (set `tla`): airbend (CR 701.65, `KeywordAction.Airbend`), earthbend (CR 701.66, expanded with the land's
  return when it dies or is exiled), waterbend costs (CR 701.67, `CostPart.Waterbend` and `ManaPaymentWay.Waterbend`), "firebending X,
  where X is …" (`Keyword.FirebendingX`; firebending's mana lasts until end of combat), the bend events (`EventPattern.Airbend`,
  `Earthbend`, `Firebend`, `Waterbend`), `EventPattern.AbilityTriggers` ("a creature you control attacking causes a triggered ability of
  that creature to trigger"), `Mechanic.SetName`, `Mechanic.KeepsUnspentMana` and `UnspentManaBecomes`, `GainAbilitiesOf.triggered`,
  `Effect.Fight.bind` (the excess damage), `Effect.ExtraCombat.onlyAttackers`, `Effect.ControlNextTurn.combatPhase`,
  `ActivationRules.minimumX` ("X can't be 0"), `Value.ManaInPool`, `Value.MostSharing`, `NumericProperty.CreatureTypes`,
  `Objects.Granter` and `Keyword.Enchant.SLOT`.
- Keywords no longer by hand (2026-11-03): `KeywordExpansionsMore` (asked by `KeywordExpansions.expand` for keywords it doesn't list)
  writes out dash, blitz, echo, cumulative upkeep, embalm, eternalize, encore, mobilize, myriad, melee, dethrone, provoke, rampage,
  soulshift, bloodthirst, ingest, undaunted, ascend, gravestorm, replicate, squad, bargain, demonstrate, spectacle, surge, prowl,
  freerunning, disturb, recover, transmute, riot, ravenous, amplify, graft, saddle, partner
  with, entwine, disguise, plot, awaken, tribute, emerge, harmonize, prototype, aftermath and ripple from their CR definitions; spree and
  tiered expand to nothing (their modes carry the costs, `Mode.extraCost`). Keyword actions written out: support (now
  `KeywordAction.Support(count, slot)`, its targets a slot of the instructions), discover, endure and learn. New pieces:
  `ObjectMark.Foretold`, `Plotted` and `Cloaked`, `Effect.IncreaseSpeed` (CR 702.179c) and `CostObjects.Choose.upTo` ("tapping up to one
  untapped creature you control").
- By-hand mechanics, second round (2026-11-03): enlist (its linked trigger on `EventPattern.Enlists`; the cost is the engine's), soulbond
  (`Effect.Pair`, `ObjectFilter.Paired`, `Objects.PairedWith`) and haunt (`Effect.Haunt`, "the creature it haunts" is `Objects.LinkedTo`
  haunt) are written out; the Ring tempting you gets `ObjectMark.RingBearer`, `Value.RingTemptations` and `EventPattern.RingTempts`;
  `KeywordExpansions.TRIBUTE_PAID` is public for "if tribute wasn't paid".
- By-hand mechanics, third round (2026-11-03): mutate expands to its alternative cost (the target and the merging are the engine's)
  with `EventPattern.Mutates`; `CopyException.WithoutKeyword` (epic's copy); dungeons: `CardType.Dungeon`, `FaceDefinition.rooms`
  (`DungeonRoom`: name, the room ability's instructions, the rooms its arrows lead to), `KeywordAction.VentureInto(dungeons)`
  ("venture into Undercity"), `Mechanic.OnlyVentureInto` (a dungeon only "venture into [quality]" enters),
  `EventPattern.CompletesDungeon` and `Value.DungeonsCompleted`; `CardType.Conspiracy`,
  `Keyword.DoubleAgenda`, `EventPattern.RingTempts.chosen` ("whenever you choose a creature as your Ring-bearer"), and hidden and
  double agenda expanding to the special action turning the conspiracy face up.
- Edge of Eternities (set `eoe`): warp, station and devour written out by `KeywordExpansions` (CR 702.185, 702.184, 702.82; warp's
  exile at the next end step and the card's later permission from exile, `ObjectMark.Warped` for "warped cards"; station puts charge
  counters equal to `NumericProperty.Stationing`, the tapped creature's power, or its toughness with `Mechanic.StationsWithToughness`;
  `Keyword.Devour.what` for "devour land 3"), the predefined Lander token (CR 111.10u), `AlternativeCost.from` (warp only from the
  hand), `MayPlay.costChange` and `landsEnterTapped` ("spells you cast this way cost {1} more", "lands you play this way enter
  tapped"), `CreateDelayedTrigger.laterTurn` ("its owner may play it for as long as it remains exiled" only after this turn) and
  `Selector.WasPaid` counting alternative costs ("a spell was warped this turn"); `DefinitionChecks` allows comparing a count of what
  your opponents control with a number (their total).
- Tarkir: Dragonstorm (set `tdm`): `KeywordAction.Endure` names the objects that endure ("Anafenza endures 2", "it endures X" of the
  creature that entered; enduring 0 does nothing, CR 701.63b), `SharedQuality.CounterKind` ("three or more different kinds of counters
  among creatures you control"), `ObjectFilter.HasDealtDamage` ("as long as it hasn't dealt damage yet") and `AdditionalCost.flash`
  ("you may cast this spell as though it had flash if you behold a Dragon as an additional cost to cast it", CR 702.8a);
  `DefinitionChecks` keeps a loop that creates a token attacking each player.
- Final Fantasy (set `fin`): `Effect.EndTurn` (CR 724.1) and `Effect.AdditionalStep` ("an additional end step after this step", CR
  500.9), `KeywordAction.Meld(objects, into)` with the existing `Layout.Meld` (CR 701.42), `Effect.Cast.manaAsAnyType` (CR 609.4b),
  `ActivationRules.cantBeCopied` ("this ability can't be copied", CR 707.10), `Modification.AsTransforms` ("as this creature transforms
  into …", its steps), `Modification.WinFlips` (a coin flip won by a replacement, CR 705.2), `Modification.EntersUntapped` ("lands you
  control enter untapped"), `Mechanic.DamageNotRemovedInCleanup` (CR 514.2), `Mechanic.LoseAllLandTypes` (layer 4), events
  `EventPattern.LoseGame`, `ControlChange(objects, from, to)` (CR 613.1b), `Mill` (for replacements of a mill) and `Tap.producing` ("tap
  a land for {C}"), and `ObjectFilter.CrewedBy` ("a Vehicle crewed by [this] this turn").
- Aetherdrift (set `dft`): crew and saddle count `NumericProperty.PowerFor(keyword)` (CR 702.122a, 702.171a: power, or toughness with
  `Mechanic.UsesToughnessFor`, plus `Mechanic.PowerGreaterFor` for "as though its power were 2 greater"); `EventPattern.Tap` with
  `forCost` of crew or saddle has the source role (the Vehicle or Mount, "whenever this creature saddles a Mount or crews a Vehicle");
  `EventPattern.Activate.label` ("whenever you activate an exhaust ability"); `Mechanic.AsThoughNotActivated` (CR 702.177b);
  `Value.Speed` and `Effect.ReduceSpeed` (CR 702.179); `Value.EventX` (the X of a cycling {X} in "when you cycle this card", CR 107.3a);
  `Mechanic.CantBeAttacked` (CR 508.1b) and `ObjectFilter.NoAbilities` (CR 113.1).
- Engine lane 4 (2026-11-03): `Mechanic.BlockCost` ("creatures can't block unless their controller pays …", CR 509.1d), beside
  `Mechanic.AttackCost` (any cost parts now, CR 508.1h) and `Mechanic.AsAttacks` (exert, CR 701.43d); `Effect.Cast.transformed`
  ("cast it transformed", a Siege's intrinsic trigger, CR 310.12b); `Mechanic.WhenYouCouldMulligan` ("any time you could
  mulligan and this card is in your hand, you may …", CR 103.5b) and `Mechanic.StartingHandSize` (CR 103.5).

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
