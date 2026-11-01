package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 47: creatures built on triggered abilities. Upkeep and end-step triggers; casting triggers, including "their
 * second spell each turn"; "whenever you attack" (once per declaration of attackers, CR 508.3d); discard triggers, once
 * per card or once for a batch ("one or more cards"); "you may [do X]. If you do" and "When you do" (a cost paid on
 * resolution, CR 118.12, and a reflexive trigger, CR 603.12); "unless that player pays" (CR 118.12a); coin flips; tokens
 * with keywords or a quoted ability (CR 111.4); connive (CR 701.50a).
 */
class Chunk47TriggeredCreaturesTest {
    // ---- Shared shapes

    private val creature = ObjectFilter.Type(CardType.Creature)
    private val artifact = ObjectFilter.Type(CardType.Artifact)
    private val atYourUpkeep = Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You))
    private val youDiscardACard = Trigger.On(EventPattern.Discard(players = PlayerFilter.You, cards = ObjectFilter.Any))
    private val youDiscardOneOrMoreCards = Trigger.On(
        EventPattern.Discard(players = PlayerFilter.You, cards = ObjectFilter.Any),
        oneOrMore = true,
    )
    private val drawACard = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(1)))

    /** "you may [pay]. If you do, [then]" (CR 118.12). */
    private fun youMay(cost: Cost, vararg then: Step) = listOf(
        Step.Pay(cost, bind = "paid", payer = Players.You),
        Step.If(condition = Condition.Did("paid"), then = then.toList()),
    )

    /** A creature token (CR 111.4). */
    private fun token(subtype: String, colors: List<Color>, power: Int, toughness: Int, types: List<CardType>, abilities: List<Ability>) =
        TokenSpec.Defined(FaceDefinition(
            name = "$subtype Token",
            colorIndicator = colors,
            types = types,
            subtypes = listOf(Subtype(subtype)),
            power = PrintedNumber.Fixed(power),
            toughness = PrintedNumber.Fixed(toughness),
            abilities = abilities,
        ))

    /** "You may play that card [duration]": a permission for the exiled card. */
    private fun mayPlayExiled(duration: Duration) = Step.Do(Effect.ApplyMechanics(
        mechanics = listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
        affects = Affected.Objects(Objects.Bound("exiled")),
        duration = duration,
    ))

    // ---- Upkeep and end-step triggers

    @Test
    fun gloomblossomCarrier() = card(
        name = "Gloomblossom Carrier",
        cost = "{B}{B}",
        type = "Creature — Faerie Rogue",
        pt = "1/1",
        text = """
            Flash
            Flying
            At the beginning of your upkeep, you lose 1 life and create a 1/1 blue and black Faerie creature token with flying.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        Ability.Triggered(
            trigger = atYourUpkeep,
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.LoseLife(Players.You, Value.Fixed(1))),
                    Step.Do(Effect.CreateToken(
                        token(
                            "Faerie",
                            listOf(Color.Blue, Color.Black),
                            1,
                            1,
                            listOf(CardType.Creature),
                            listOf(Keyword.Flying),
                        ),
                        count = Value.Fixed(1),
                        controller = Players.You,
                    )),
                ),
            ),
        ),
    )

    /** "Its mana value" is the revealed card's, which it keeps in the hand. */
    @Test
    fun shadowInformant() = card(
        name = "Shadow Informant",
        cost = "{1}{B}",
        type = "Creature — Human Wizard",
        pt = "2/1",
        text = "At the beginning of your upkeep, reveal the top card of your library and put that card into your hand. You lose life equal to its mana value.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = atYourUpkeep,
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.Reveal(
                        Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true),
                        bind = "revealed",
                    )),
                    Step.Do(Effect.Move(Objects.Bound("revealed"), to = Destination(Zone.Hand))),
                    Step.Do(Effect.LoseLife(Players.You, Value.Of(NumericProperty.ManaValue, Objects.Bound("revealed"), Combine.Single))),
                ),
            ),
        ),
    )

    /** An intervening "if" (CR 603.4); decayed is a keyword of the token (CR 702.147a). */
    @Test
    fun morvekGhoulherdOfStensia() = card(
        name = "Morvek, Ghoulherd of Stensia",
        cost = "{1}{B}",
        type = "Legendary Creature — Human Wizard",
        pt = "1/1",
        text = "At the beginning of your end step, if you control no creatures with decayed, create a 2/2 black Zombie creature token with decayed. (It can't block. When it attacks, sacrifice it at end of combat.)",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.You)),
            interveningIf = Condition.Not(Condition.Exists(ObjectFilter.And(listOf(
                creature,
                ObjectFilter.HasKeyword(Keyword.Decayed),
                ObjectFilter.ControlledBy(PlayerFilter.You),
            )), atLeast = 1)),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.CreateToken(
                    token("Zombie", listOf(Color.Black), 2, 2, listOf(CardType.Creature), listOf(Keyword.Decayed)),
                    count = Value.Fixed(1),
                    controller = Players.You,
                ))),
            ),
        ),
    )

    // ---- Casting triggers

    @Test
    fun excavationTinker() = card(
        name = "Excavation Tinker",
        cost = "{2}{W}",
        type = "Creature — Dwarf Artificer",
        pt = "3/3",
        text = "Whenever you cast an artifact spell, you may pay {2}. If you do, create a 0/0 colorless Construct artifact creature token with \"This token gets +1/+1 for each artifact you control.\"",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(artifact, by = PlayerFilter.You)),
            instructions = Instructions(
                steps = youMay(manaCost("{2}"), Step.Do(Effect.CreateToken(token(
                    subtype = "Construct",
                    colors = emptyList(),
                    power = 0,
                    toughness = 0,
                    types = listOf(CardType.Artifact, CardType.Creature),
                    abilities = listOf(Ability.Static(listOf(Mechanic.ModifyPowerToughness(
                        Value.Count(Objects.Each(ObjectFilter.And(listOf(artifact, ObjectFilter.ControlledBy(PlayerFilter.You))))),
                        Value.Count(Objects.Each(ObjectFilter.And(listOf(artifact, ObjectFilter.ControlledBy(PlayerFilter.You))))),
                    )), affects = Affected.Objects(Objects.Self))),
                ), count = Value.Fixed(1), controller = Players.You))),
            ),
        ),
    )

    /** "Their first noncreature spell each turn": only the first such spell of that player each turn. "That player" is the
     * caster; X is set by the text (CR 107.3). */
    @Test
    fun etherglassWatcher() = card(
        name = "Etherglass Watcher",
        cost = "{W}",
        type = "Artifact Creature — Human Soldier",
        pt = "1/1",
        text = "Whenever an opponent casts their first noncreature spell each turn, draw a card unless that player pays {X}, where X is this creature's power.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.Cast(ObjectFilter.Not(creature), by = PlayerFilter.Opponent)),
                nthThisTurn = 1,
            ),
            instructions = Instructions(
                steps = listOf(
                    Step.Pay(
                        cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Of(NumericProperty.Power, Objects.Self, Combine.Single)))),
                        payer = Players.EventPlayer,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Not(Condition.Did("paid")),
                        then = listOf(drawACard),
                    ),
                ),
            ),
        ),
    )

    /** Connive: draw, discard, and a +1/+1 counter if the discarded card was nonland (CR 701.50a). */
    @Test
    fun invoiceShredder() = card(
        name = "Invoice Shredder",
        cost = "{1}{U}",
        type = "Creature — Bird Advisor",
        pt = "1/3",
        text = """
            Flying
            Whenever a player casts their second spell each turn, this creature connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on this creature.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.Cast(spell = ObjectFilter.Any, by = PlayerFilter.Any)),
                nthThisTurn = 2,
            ),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Action(KeywordAction.Connive(
                    objects = Objects.Self,
                    count = Value.Fixed(1),
                )))),
            ),
        ),
    )

    /** "That spell" is the spell cast; the copy may get new targets (CR 707.10c). */
    @Test
    fun grubbTheFingerless() = card(
        name = "Grubb, the Fingerless",
        cost = "{1}{R}",
        type = "Legendary Creature — Goblin Wizard",
        pt = "2/2",
        text = """
            Whenever you cast an instant or sorcery spell, flip a coin. If you lose the flip, return that spell to its owner's hand. If you win the flip, copy that spell, and you may choose new targets for the copy.
            Partner (You can have two commanders if both have partner.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(
                ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                by = PlayerFilter.You,
            )),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.FlipCoin(bind = "flip", players = Players.You)),
                    Step.If(
                        condition = Condition.Not(Condition.WonFlip("flip")),
                        then = listOf(Step.Do(Effect.Move(
                            Objects.EventObject(role = EventRole.Object),
                            to = Destination(Zone.Hand),
                        ))),
                    ),
                    Step.If(
                        condition = Condition.WonFlip("flip"),
                        then = listOf(Step.Do(Effect.CopySpell(
                            Objects.EventObject(role = EventRole.Object),
                            controller = Players.You,
                            newTargets = true,
                        ))),
                    ),
                ),
            ),
        ),
        Keyword.Partner,
    )

    // ---- Attack and sacrifice triggers

    /**
     * "Whenever you attack" triggers once when you declare one or more attackers (CR 508.3d). Discarding is a cost paid
     * on resolution (CR 118.12); "When you do" is a reflexive trigger whose target is chosen then (CR 603.12). The
     * second ability triggers once for each batch of discarded cards.
     */
    @Test
    fun atoqStewardOfTheDawn() = card(
        name = "Atoq, Steward of the Dawn",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Knight",
        pt = "2/2",
        text = """
            Whenever you attack, you may discard a card. When you do, put a +1/+1 counter on target attacking creature. It gains trample until end of turn.
            Whenever you discard one or more cards, exile the top card of your library. You may play that card until your next end step.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Attack(ObjectFilter.ControlledBy(PlayerFilter.You)), oneOrMore = true),
            instructions = Instructions(
                steps = youMay(
                    Cost(listOf(CostPart.Discard(CostObjects.Choose(
                        ObjectFilter.Category(ObjectCategory.Card),
                        count = Value.Fixed(1),
                    )))),
                    Step.Do(Effect.ReflexiveTrigger(Instructions(
                        targets = listOf(TargetSlot("creature", TargetKind.Objects(ObjectFilter.And(listOf(
                            ObjectFilter.HasStatus(Status.Attacking),
                            creature,
                        ))), min = Value.Fixed(1), max = Value.Fixed(1))),
                        steps = listOf(
                            Step.Do(Effect.PutCounters(Objects.Target("creature"), CounterKind.PowerToughness(1, 1), amount = Value.Fixed(1))),
                            Step.Do(Effect.ApplyMechanics(
                                mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Trample))),
                                affects = Affected.Objects(Objects.Target("creature")),
                                duration = Duration.EndOfTurn,
                            )),
                        ),
                    ))),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = youDiscardOneOrMoreCards,
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.Move(
                        Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true),
                        to = Destination(Zone.Exile),
                        postBind = "exiled",
                    )),
                    mayPlayExiled(Duration.UntilStep(TurnStep.End, PlayerFilter.You)),
                ),
            ),
        ),
    )

    @Test
    fun havocImp() = card(
        name = "Havoc Imp",
        cost = "{1}{B}{R}",
        type = "Creature — Devil",
        pt = "3/3",
        text = "Whenever a player sacrifices a permanent, this creature deals 1 damage to any target.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Sacrifice(ObjectFilter.Category(ObjectCategory.Permanent))),
            instructions = Instructions(
                // "any target" (CR 115.4)
                targets = listOf(TargetSlot("any", TargetKind.Either(
                    objects = ObjectFilter.Or(listOf(
                        creature,
                        ObjectFilter.Type(CardType.Planeswalker),
                        ObjectFilter.Type(CardType.Battle),
                    )),
                    players = PlayerFilter.Any,
                ), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(Step.Do(Effect.DealDamage(
                    Recipients(target = "any"),
                    amount = Value.Fixed(1),
                    source = Objects.Self,
                ))),
            ),
        ),
    )

    // ---- Discard triggers

    /** Exiling the discarded card from the graveyard is a cost paid on resolution (CR 118.12): it can be paid only while
     * the card is still there. */
    @Test
    fun quarantineAutomaton() = card(
        name = "Quarantine Automaton",
        cost = "{2}",
        type = "Artifact Creature — Construct",
        pt = "2/1",
        text = "Whenever you discard a card, you may exile that card from your graveyard. If you do, you may play that card this turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = youDiscardACard,
            instructions = Instructions(
                steps = youMay(
                    Cost(listOf(CostPart.Exile(
                        what = CostObjects.These(Objects.EventObject(EventRole.After)),
                        from = Zone.Graveyard,
                        postBind = "exiled",
                    ))),
                    mayPlayExiled(Duration.EndOfTurn),
                ),
            ),
        ),
    )

    /** "That many": one counter for each card discarded in the batch. */
    @Test
    fun rampagingReefshark() = card(
        name = "Rampaging Reefshark",
        cost = "{R}",
        type = "Creature — Shark Pirate",
        pt = "1/1",
        text = """
            Whenever you discard one or more cards, put that many +1/+1 counters on this creature.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = youDiscardOneOrMoreCards,
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.PutCounters(
                    Objects.Self,
                    CounterKind.PowerToughness(1, 1),
                    amount = Value.Count(Objects.EventObject(EventRole.After)),
                ))),
            ),
        ),
        Keyword.Cycling(manaCost("{2}")),
    )
}
