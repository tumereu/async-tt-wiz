# Set work: techniques

General know-how from earlier sets' workers, still true. Card names here are generic descriptions, not real cards. Helper
signatures are in `<plan>/helpers.md`; set-specific helpers live in each set's `<Code>Cards.kt` (parser) and
`<Code>Support.kt` or `<Code>Scenarios.kt` (engine).

## Parser modelling
- Where a rule goes: a single-sentence whole-ability rule at the top of `Sentences.effectRules` (the set's `<set>Effect`
  hook); a wording of several sentences read as one in the set's `<set>Whole`, which sees only bodies with ". " inside;
  a static line that becomes several abilities in `Sentences.multi`. A new table line or slot beats a new rule.
- "it" follows `context.it`: a rule that moves or exiles an object sets it (`Bound("exiled")`), so a later "it", "that
  card" or "If it's a X" reads the new object (CR 400.7). In "When this enters", "it" is `EventObject(After)`. After a
  Move of a target, event object or this card to the battlefield, `Sentences.followTargetsToBattlefield` points later
  steps at the new permanent (CR 400.7j): such a rule needs no own `postBind`.
- "that many" binds the nearest amount an earlier part of the same ability defines (`context.thatMuch`, e.g. "sacrifice
  any number of ..., then draw that many") before the trigger event's amount.
- "If you do" after a step that can fail (a mandatory exile, move or sacrifice): bind the moved objects (`postBind`) and
  check `Condition.NonEmpty`. A mandatory `Step.Pay` counts as paid even when nothing moved, so `Did` is wrong there.
- "You may pay/sacrifice/discard ... If you do" = `Step.Pay(mandatory = false, ...)` + `Step.If(Did)`; audits calling it
  unclear are false alarms.
- A delayed trigger made by a triggered ability ("return it at the beginning of the next end step") has its own event:
  bind the outer event's object first (`Step.Bind(EventObject(After), "<name>")`) and use `Bound` inside (CR 603.7c).
- "That creature" after a sacrifice is the sacrifice's bound object, last known information (CR 608.2h). After "gain
  control of target X" it is that target; "sacrifice/exile it (that token) at the next end step" after creating a token is
  the token; in an attach trigger it is the creature attached to (`EventRole.To`), not whatever is attached at resolution.
  An intervening "if it has ..." after "another creature enters/attacks" is the event object, not this object.
- "that creature"/"it" in a damage trigger goes through `Sentences.damageThatRole` (the damaged one when this object dealt
  it); in a block trigger it is `EventRole.OtherCombatant`. "Blocks a Y" triggers are `perAttacker`, "becomes blocked by a
  Y" `perBlocker` (CR 509.3b-c). "That player" of a permanent entering is its controller, of a card put into a graveyard
  that graveyard's owner (CR 110.2, 404.1).
- An Aura's "when enchanted creature dies" trigger: "this card" is `Objects.SelfAfter` (the Aura in the graveyard is a new
  object). In an ability an Aura or Equipment grants, its own name is `Objects.Granter`.
- "where X is" inside a quoted ability (an emblem, a granted ability) belongs to that ability, not the outer one.
- A cost that exiles the card from the graveyard, or a graveyard-return activated ability, needs no `functionsIn`;
  "When you cast this spell" reads with `functionsIn = null`.
- Number words: `Numbers.WORD_PATTERN` (a..twenty), never a hand-written "one|two|..." list. Any additional cost that
  names X announces X (`ADDITIONAL_COST_X`, CR 601.2b). "any number of target X" divided has min 0.
- A rule that adds a target slot reads everything else first: giving up afterwards leaks the slot into the next rule.
  The parser leaves an ability with an unused target slot unread (`DefinitionChecks.unusedTargetSlots`, CR 115.1).
- "Until your next turn, X." reads as "X until your next turn."; an untargeted "you may return a <card> from your graveyard" is Choose 0..1, not May.
- "enchanted player" = `Players.AttachedTo(Objects.Self)`; "Curses attached to you" = `ObjectFilter.Attached(toPlayers =
  ...)`. "<cost part choosing one object> and pay its mana cost" = `CostPart.ManaCostOf(Bound)`; a token's text naming
  the card that made it = `Objects.Creator`, kept by copies (CR 201.5a).
- Slot names: one target card is `card`, "up to two target creature cards" is `cards`; a second "target creature" is
  `second creature`; "and/or" slots take the last noun ("artifacts"); "creature or planeswalker you don't control" is
  `permanent`; "player or planeswalker" is `TargetKind.Either(planeswalker, PlayerFilter.Any)`.
- "you don't control" = `ControlledBy(PlayerFilter.Not(You))`; "that player controls" in a trigger = `ControlledBy(
  PlayerFilter.Is(Players.EventPlayer))`, in a spell or activated ability the chosen or targeted player (no event, CR
  608.2c); "its controller creates" = `CreateToken(controller = Players.ControllerOf(target(slot)))`. "its controller" of
  a creature that died is its last controller on the battlefield (`EventObject(Before)`, CR 603.10a), not the owner.
- `And` filter order matters for equality: "another" (`Not(Is(Self))`) first, status words (attacking, tapped) before the
  type, then power, then control. Copy the order from the nearest test.
- A tap or sacrifice cost "an untapped creature you control" = `CostObjects.Choose(creature, n)`: control and untapped are
  implicit, not in the filter.
- Ability words: `abilityWord = "<word>"` on the static, spell or triggered ability; a condition on a trigger is
  `interveningIf` ("if you control ..."), a "while" on an attack trigger is `triggersOnlyIf`.
- "you may <a choice you make>" (put counters on a creature you control) = `Choose` zero-or-one, not `May` + `Choose`;
  when another player makes the choice ("you may have target opponent sacrifice") it stays `May` (CR 608.2d).
- "you may look ... If you do, ..." is one `Step.May`: a `Step.Pay(steps = [LookAt])` never counts as done.
- A delayed "return the exiled card" made by the ability that exiled it reads `Bound("exiled")` (the source may be gone).
- "If [condition settled at cast: main phase, kicked, paid], [targeted effect]": those target slots
  are `onlyIf` (CR 601.2c).
- Type-setting (CR 205.1a-b): "becomes a ... creature" replaces card types; "artifact creature", "still a <type>" and "in
  addition to its other types" keep all prior types and subtypes; "[creature type] artifact creature" replaces creature
  types only (`replaceSubtypes` is per kind), also beside "still a land"; a copy exception "it's a ... creature" sets types.
- "other target" after an earlier target is `distinctFrom` that slot (CR 115.3), not `Not(Is(Self))`. A condition judged
  "as you cast" is `Condition.AsCast` (copies aren't cast); a spell's own cost reduction may read its cast choices.
- Replacements: "dealt damage this way would die this turn, exile it instead" covers only objects actually dealt damage
  (prevented damage isn't dealt, CR 615.1a); "if you would pay life" is `EventPattern.PayLife` with a
  `Replacement.condition`, payability stays CR 119.4; mana multipliers are a `Replacement` on `Tap(forMana = true)`.
- A target "of an opponent's choice" is `TargetSlot.chosenBy`; "the next N damage ... is dealt to X instead" is
  `Effect.PreventDamage.redirectTo` (a redirection, so unpreventable damage moves too, CR 614.9).
- Damage triggers pick `EventPattern.Damage.per` (CR 603.2c): `Recipient` for "X is dealt damage", `Source` for "X deals
  [combat] damage" with no recipient, default `SourceAndRecipient` for "a source deals damage to Y".
- A battle (Siege) test card is built with `OracleCard(name, cost, type, text, defense = "5")`: `card()` has no defense.

## Engine scenarios
- All setup (`battlefield`, `hand`, `library`, `life`, `turn`, `attach`) comes before the first move; afterwards only
  `move(name, zone)`, which fires no triggers: kill a creature with a damage spell to see a dies trigger.
- `turn(step = Upkeep)` fires no upkeep triggers: start in `PrecombatMain` and `advanceTo(Upkeep, turn = n)`. Going past
  turn 1 needs a library for each player who draws (an empty library loses the game).
- Opponent's turn: `turn(number = 2, active = bob)`, or `priority = alice` to act in it. After your own spell resolves you
  hold priority again: `alice.passes()` before the opponent acts. `Engine.options(state, p.id)` is empty without priority.
- `casts(...)` answers targets in slot order, `modes`, `x`, `kicked` (0 declines; without it a kicker card stops at a May
  and later expectations can pass vacuously: assert the spell resolved), `option` ("A or B" costs), `choosing` (cost
  objects), `paying` ("name#1" = the second land of that name). Leftover decisions stay open for `answer(...)`.
- Casting never throws for missing mana: test "can't pay" with `decision<Decision.ManaPayment>().suggestion` empty. A spell
  with no legal target throws `IllegalArgumentException`, as does casting without permission or a failed activation
  condition. Test a target limit through `decision<Decision.Targets>().max`, not by over-supplying targets.
- Auto-answers: a lone legal target (also of an "up to one" slot or a trigger's mode), a lone sacrifice or discard
  choice, a scry of a one-card library and a cast with one possible payment ask nothing; add a second option to test the
  decision. Searches always ask `chooses(...)`. An optional "you may pay" asks `YesNo`, then `ManaPayment` even for one land.
- A trigger with targets or a "may": `resolveAll()` after the cast, then `Answer.Targets`, `allPass()`, then
  `Answer.YesNo`; an empty optional slot is `Answer.Targets(emptyList())`. `resolve()` resolves one item, to act before a
  trigger resolves. Modes of a triggered ability with no legal target are unavailable (CR 700.2b).
- `activates(name, ability = i)` counts activated abilities only, mana abilities included, from 0 (a planeswalker's
  loyalty abilities in printed order). A planeswalker needs `battlefield(x, counters = listOf(CounterKind.Loyalty to n))`;
  a 0/0 X creature needs +1/+1 counters; a tap ability can't be activated twice a turn.
- Combat with a trampler or several blockers needs `divideAsSuggested()`; trample excess is power minus blocker toughness.
  Attack after `advanceTo(TurnStep.DeclareAttackers)`; check "can't block" with `assertFailsWith` on `blocks`.
- Names: `name.zone` needs a unique name; a card in several zones (a token copy sharing a name) is ambiguous. Library
  fillers are basic lands (only the five basic land types have test cards). Tokens are named "<Subtype> Token". A copied permanent keeps
  its own name in `battlefield.names`: read its characteristics instead.
- Several players: "any opponent may" asks `decision<Decision.May>().player` in turn order; a pile split asks the
  separator `ChooseObjects`, then the chooser `Answer.Option("first"/"second")`; several copies of a spell are ordered by
  their controller (`ChooseObjects`).
- Several defenders: `perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(a), TargetRef.Player(bob.id)), ...)))`;
  `attach(curse, "bob")` attaches to a player. Random results: `random({ _ -> Answer.RandomObjects(listOf(card(x))) })`,
  `random(Answer.CoinFlip(true))`.
- A card with several ways to cast it (two flashback permissions): `casts(card, way = Engine.options(...).way)`;
  `castsOf(player, name)` lists another player's ways.
- Set support files hold shared drivers (`resolveAll`, `playOut`, `ownAlternative` for an alternative cost); import the
  one `helpers.md` lists under "Shared scenario drivers" (its package, file and line) rather than copying it.
- Counters: proliferate is `decision<Decision.Targets>()` with slot "proliferate" (asks nothing when nothing has a
  counter); player counters at setup `bob.counters(CounterKind.Poison, 3)`; named kinds `CounterKind.Named("oil")`. A
  trigger's `Modes` decision is asked even when only one mode is available.
- First or double strike: `hits(...)` stops after the first damage step, `advanceTo(TurnStep.EndOfCombat)` for the
  second. A spell's divided amount has no suggestion: `answer(Answer.Division(listOf(Share(target(a), 2), ...)))`.
- Objects that left are judged by last known information: a token "copy of it" copies the last known copiable values
  (a clone copies what it copied, CR 707.2); events record the controller at the time (CR 608.2h). Test both with 3
  players and a control change.
- Keywords: `x hasKeyword Keyword.Flying.kind` / `lacksKeyword`; a token id is wrapped with `CardRef(id, label)`;
  subtypes via `x.view.characteristics!!.hasSubtype(Subtype("Elf"))`; damage via `x.view.obj?.damage`.
- A scry or optional payment inside a resolving spell can leave a decision open before triggers (scry: `Answer.Objects(
  emptyList())`); a mana ability with a colour choice asks colour and payment in either order: answer what is pending.
- An Adventure face: `casts("Face", way = CastWay(face = 1))`; a player target needs `target = "bob"` even with one
  opponent. Saga chapter II after I: `loreChapter(saga, n)`, then `advanceTo(PrecombatMain, turn = 5)`.
- A `ForEach` whose only step is `DealDamage` is one damage event (CR 608.2f); a `ForEach` that casts copies asks the
  controller the order one at a time: answer with `alice.chooses(name)`.

## Test conventions
- Invented names are global across the whole engine suite: grep `invent("<Name>"` and `all-card-names.txt` first.
- Invented lands take cost `null`; invented planeswalker text needs "[+1]: ..." loyalty prefixes; change cost and
  subtypes only where it doesn't affect play.
- An invented spell needs a mana cost (`{0}` for a free one): a spell with no mana cost can't be cast for it (CR 118.6).
- A card of several faces: `Cards.inventFaces(layout, face(...), ...)` (colour indicator last) and `cardFaces(...)` in
  parser tests.
- One cycle of near-identical cards (e.g. lands that enter tapped unless a condition holds): one private helper in the
  chunk's test file, one test per card.
- In one Gradle call, `--tests` applies to the task just before it; `bin/set-tests.sh` handles this.
