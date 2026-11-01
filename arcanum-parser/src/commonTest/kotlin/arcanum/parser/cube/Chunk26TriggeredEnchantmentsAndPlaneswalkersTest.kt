package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
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
import arcanum.api.ManaCost
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
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
 * Chunk 26: enchantments built on triggers and the planeswalkers. Triggers on dying (CR 700.4) and on cards put into or
 * leaving a graveyard, one of them working from the graveyard (CR 113.6b); "whenever you cast a noncreature spell";
 * "you may [do X]. If you do" (CR 118.12); cards exiled with a permanent kept as its card memory (CR 607.2a); tokens with
 * a quoted ability or sacrificed at the next end step (a delayed trigger, CR 603.7). Loyalty abilities are activated
 * abilities with a loyalty cost (CR 606); an emblem (CR 114) and a copy effect with an exception (CR 707.9b).
 */
class Chunk26TriggeredEnchantmentsAndPlaneswalkersTest {
    // ---- Shared shapes

    private val creature = ObjectFilter.Type(CardType.Creature)
    /** "you cast a noncreature spell": what's cast is always a spell, so the filter names only its kind. */
    private val youCastANoncreatureSpell = Trigger.On(EventPattern.Cast(ObjectFilter.Not(creature), by = PlayerFilter.You))
    private val atYourEndStep = Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.You))
    private fun controlledBy(filter: ObjectFilter, players: PlayerFilter) = ObjectFilter.And(listOf(filter, ObjectFilter.ControlledBy(players)))

    /** "Whenever [creatures] die" (CR 700.4). */
    private fun dies(filter: ObjectFilter) = Trigger.On(EventPattern.ZoneChange(filter, from = Zone.Battlefield, to = Zone.Graveyard))

    private fun draw(players: Players, n: Int = 1) = Step.Do(Effect.Draw(players, count = Value.Fixed(n)))
    private fun mana(text: String) = CostPart.Mana(ManaCost.parse(text)!!)

    /** A loyalty ability (CR 606): "[±n]: …". */
    private fun loyalty(amount: Int, instructions: Instructions) = Ability.Activated(
        cost = Cost(listOf(CostPart.Loyalty(Value.Fixed(amount)))),
        instructions = instructions,
    )

    /** "you may [do X]. If you do, [then]" (CR 118.12). */
    private fun youMay(pay: Step.Pay, vararg then: Step) = listOf(
        pay,
        Step.If(condition = Condition.Did("paid"), then = then.toList()),
    )

    /** A token without abilities ("a 1/1 colorless Servo artifact creature token", CR 111.4). */
    private fun token(subtype: String, colors: List<Color>, power: Int, toughness: Int, vararg types: CardType) = TokenSpec.Defined(FaceDefinition(
        name = "$subtype Token",
        colorIndicator = colors,
        types = types.toList(),
        subtypes = listOf(Subtype(subtype)),
        power = PrintedNumber.Fixed(power),
        toughness = PrintedNumber.Fixed(toughness),
    ))

    private val servo = token("Servo", emptyList(), 1, 1, CardType.Artifact, CardType.Creature)
    private val zombie = token("Zombie", listOf(Color.Black), 2, 2, CardType.Creature)

    // ---- Graveyard triggers

    @Test
    fun grimHomecoming() = card(
        name = "Grim Homecoming",
        cost = "{1}{R}",
        type = "Enchantment",
        text = """
            When this enchantment enters, you may discard a card. If you do, draw two cards.
            {1}, Sacrifice this enchantment: Creatures you control gain haste until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            instructions = Instructions(
                steps = youMay(
                    Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card))))), bind = "paid"),
                    draw(Players.You, 2),
                ),
            ),
        ),
        Ability.Activated(
            cost = Cost(listOf(mana("{1}"), CostPart.Sacrifice(CostObjects.Self))),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))),
                    affects = Affected.Objects(Objects.Each(controlledBy(creature, PlayerFilter.You))),
                    duration = Duration.EndOfTurn,
                ))),
            ),
        ),
    )

    /** CR 113.6b: "if this card is in your graveyard" states where the abilities work; checked again on resolution
     * (CR 603.4). A card is put into its owner's graveyard. */
    @Test
    fun stairwayFromTheDepths() = card(
        name = "Stairway from the Depths",
        cost = "{B}{B}{B}",
        type = "Enchantment",
        text = """
            Whenever a nontoken creature is put into your graveyard from the battlefield, if this card is in your graveyard, create a 2/2 black Zombie creature token.
            When a creature is put into an opponent's graveyard from the battlefield, if this card is in your graveyard, exile this card.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = dies(ObjectFilter.And(listOf(
                ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)),
                creature,
                ObjectFilter.OwnedBy(PlayerFilter.You),
            ))),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Graveyard)),
            functionsIn = listOf(Zone.Graveyard),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.CreateToken(zombie))),
            ),
        ),
        Ability.Triggered(
            trigger = dies(ObjectFilter.And(listOf(creature, ObjectFilter.OwnedBy(PlayerFilter.Opponent)))),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Graveyard)),
            functionsIn = listOf(Zone.Graveyard),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Exile)))),
            ),
        ),
    )

    /** "That creature's controller" is its controller as it last existed on the battlefield (CR 603.10a). */
    @Test
    fun fertilePassing() = card(
        name = "Fertile Passing",
        cost = "{2}{G}",
        type = "Enchantment",
        text = "Whenever a creature dies, that creature's controller may draw a card.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = dies(creature),
            instructions = Instructions(
                steps = listOf(Step.May(
                    steps = listOf(draw(Players.ControllerOf(Objects.EventObject(EventRole.Before)))),
                    player = Players.ControllerOf(Objects.EventObject(EventRole.Before)),
                )),
            ),
        ),
    )

    /** One trigger for a batch of creature cards leaving at once (CR 603.10a: it looks back in time). */
    @Test
    fun sinisterRootwork() = card(
        name = "Sinister Rootwork",
        cost = "{B}{G}",
        type = "Enchantment",
        text = """
            Creature tokens you control have "{T}: Add one mana of any color."
            Whenever one or more creature cards leave your graveyard, create a 0/1 green Plant creature token, then put a +1/+1 counter on each Plant you control.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(listOf(Ability.Activated(
                cost = Cost(listOf(CostPart.TapSelf)),
                instructions = Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)))))),
            )))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                creature,
                ObjectFilter.Category(ObjectCategory.Token),
                ObjectFilter.ControlledBy(PlayerFilter.You),
            )))),
        ),
        Ability.Triggered(
            trigger = Trigger.On(
                events = listOf(EventPattern.ZoneChange(ObjectFilter.And(listOf(creature, ObjectFilter.OwnedBy(PlayerFilter.You))), from = Zone.Graveyard)),
                oneOrMore = true,
            ),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.CreateToken(token("Plant", listOf(Color.Green), 0, 1, CardType.Creature))),
                    Step.Do(Effect.PutCounters(
                        Objects.Each(controlledBy(ObjectFilter.Sub(Subtype("Plant")), PlayerFilter.You)),
                        CounterKind.PowerToughness(1, 1),
                        amount = Value.Fixed(1),
                    )),
                ),
            ),
        ),
    )

    /** X of a permanent's own ability is the X its spell was cast with (CR 107.3m); −X is written Times(X, −1). */
    @Test
    fun theBonehookSlaughter() = card(
        name = "The Bonehook Slaughter",
        cost = "{X}{B}{B}",
        type = "Legendary Enchantment",
        text = """
            When The Bonehook Slaughter enters, each creature gets -X/-X until end of turn.
            Whenever a creature you control dies, each opponent loses 1 life.
            Whenever a creature an opponent controls dies, you gain 1 life.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Times(Value.CastX(), -1), Value.Times(Value.CastX(), -1))),
                    affects = Affected.Objects(Objects.Each(creature)),
                    duration = Duration.EndOfTurn,
                ))),
            ),
        ),
        Ability.Triggered(
            trigger = dies(controlledBy(creature, PlayerFilter.You)),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), Value.Fixed(1)))),
            ),
        ),
        Ability.Triggered(
            trigger = dies(controlledBy(creature, PlayerFilter.Opponent)),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.GainLife(Players.You, Value.Fixed(1)))),
            ),
        ),
    )

    // ---- End-step and cast triggers

    @Test
    fun concealedArmory() = card(
        name = "Concealed Armory",
        cost = "{W}{B}",
        type = "Enchantment",
        text = """
            Revolt — At the beginning of your end step, if a permanent left the battlefield under your control this turn, create a 1/1 colorless Servo artifact creature token.
            {1}, Sacrifice a creature: Scry 1.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            abilityWord = "Revolt",
            trigger = atYourEndStep,
            interveningIf = Condition.HappenedThisTurn(EventPattern.ZoneChange(
                controlledBy(ObjectFilter.Category(ObjectCategory.Permanent), PlayerFilter.You),
                from = Zone.Battlefield,
            )),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.CreateToken(servo))),
            ),
        ),
        Ability.Activated(
            cost = Cost(listOf(mana("{1}"), CostPart.Sacrifice(CostObjects.Choose(creature)))),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Scry(Players.You, Value.Fixed(1)))),
            ),
        ),
    )

    /**
     * The landfall ability's exiled cards are the cards "exiled with this enchantment" of the other ability (linked
     * abilities, CR 607.2a), kept as card memory. "For as long as it remains exiled" has no other end: a card that
     * leaves exile is a new object (CR 400.7).
     */
    @Test
    fun moltenPeakExpedition() = card(
        name = "Molten Peak Expedition",
        cost = "{2}{R}",
        type = "Enchantment",
        text = """
            Landfall — Whenever a land you control enters, exile the top card of your library. You may play that card for as long as it remains exiled.
            At the beginning of your end step, if there are cards exiled with this enchantment, put them into their owner's graveyard, then this enchantment deals that much damage to each opponent.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            abilityWord = "Landfall",
            trigger = Trigger.On(EventPattern.ZoneChange(controlledBy(ObjectFilter.Type(CardType.Land), PlayerFilter.You), to = Zone.Battlefield)),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(1)), to = Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
                    Step.Do(Effect.ApplyMechanics(
                        mechanics = listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play)),
                        affects = Affected.Objects(Objects.Bound("exiled")),
                        duration = Duration.Indefinitely,
                    )),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = atYourEndStep,
            interveningIf = Condition.ObjectMatches(Objects.Remembered("exiled"), ObjectFilter.InZone(Zone.Exile)),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.Move(Objects.Remembered("exiled"), to = Destination(Zone.Graveyard), postBind = "moved")),
                    Step.Do(Effect.DealDamage(
                        Recipients(players = Players.Each(PlayerFilter.Opponent)),
                        amount = Value.Count(Objects.Bound("moved")),
                    )),
                ),
            ),
        ),
    )

    /** CR 611.2c: "those creatures" are the ones that got +1/+1, fixed as the effect began. */
    @Test
    fun peakAscendancy() = card(
        name = "Peak Ascendancy",
        cost = "{U}{R}{W}",
        type = "Enchantment",
        text = """
            Whenever you cast a noncreature spell, creatures you control get +1/+1 until end of turn. Untap those creatures.
            Whenever you cast a noncreature spell, you may draw a card. If you do, discard a card.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = youCastANoncreatureSpell,
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.ApplyMechanics(
                        mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1))),
                        affects = Affected.Objects(Objects.Each(controlledBy(creature, PlayerFilter.You))),
                        duration = Duration.EndOfTurn,
                    )),
                    Step.Do(Effect.Untap(Objects.Each(controlledBy(creature, PlayerFilter.You)))),
                ),
            ),
        ),
        Ability.Triggered(
            trigger = youCastANoncreatureSpell,
            instructions = Instructions(
                steps = youMay(
                    Step.Pay(steps = listOf(draw(Players.You)), bind = "paid"),
                    Step.Do(Effect.Discard(Players.You, count = Value.Fixed(1))),
                ),
            ),
        ),
    )

    // ---- Planeswalkers

    /** CR 603.7: the tokens are sacrificed by a delayed trigger at the next end step; "they gain haste" has no end. */
    @Test
    fun igniaAcolyteOfEmbers() = card(
        name = "Ignia, Acolyte of Embers",
        cost = "{1}{R}{R}",
        type = "Legendary Planeswalker — Chandra",
        text = """
            [0]: Put a loyalty counter on each red planeswalker you control.
            [0]: Create two 1/1 red Elemental creature tokens. They gain haste. Sacrifice them at the beginning of the next end step.
            [−2]: You may cast target instant or sorcery card with mana value 3 or less from your graveyard. If that spell would be put into your graveyard, exile it instead.
        """,
        loyalty = "4",
    ) parsesTo abilities(
        loyalty(0, Instructions(
            steps = listOf(Step.Do(Effect.PutCounters(
                Objects.Each(ObjectFilter.And(listOf(
                    ObjectFilter.OfColor(Color.Red),
                    ObjectFilter.Type(CardType.Planeswalker),
                    ObjectFilter.ControlledBy(PlayerFilter.You),
                ))),
                CounterKind.Loyalty,
                amount = Value.Fixed(1),
            ))),
        )),
        loyalty(0, Instructions(
            steps = listOf(
                Step.Do(Effect.CreateToken(token("Elemental", listOf(Color.Red), 1, 1, CardType.Creature), count = Value.Fixed(2), bind = "tokens")),
                Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Haste))),
                    affects = Affected.Objects(Objects.Bound("tokens")),
                    duration = Duration.Indefinitely,
                )),
                Step.Do(Effect.CreateDelayedTrigger(
                    trigger = Trigger.On(EventPattern.StepBegins(TurnStep.End)),
                    instructions = Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound("tokens"))))),
                )),
            ),
        )),
        // CR 608.2g: the card is cast while the ability resolves; the replacement follows that spell.
        loyalty(-2, Instructions(
            targets = listOf(TargetSlot(
                name = "card",
                what = TargetKind.Objects(ObjectFilter.And(listOf(
                    ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                    ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(3)),
                    ObjectFilter.InZone(Zone.Graveyard),
                    ObjectFilter.OwnedBy(PlayerFilter.You),
                ))),
            )),
            steps = listOf(
                Step.May(listOf(Step.Do(Effect.Cast(Objects.Target("card"), bind = "spell")))),
                Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.Replacement(
                        event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("spell")), from = Zone.Stack, to = Zone.Graveyard),
                        modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))),
                    )),
                    affects = null,
                    duration = Duration.Indefinitely,
                )),
            ),
        )),
    )

    /** "Renji" is the card's shortened name (CR 201.5c); phasing out (CR 702.26b); the emblem's abilities work in the
     * command zone, "you" being its owner (CR 114.2, 114.4). */
    @Test
    fun renjiKagehara() = card(
        name = "Renji Kagehara",
        cost = "{1}{U}{B}",
        type = "Legendary Planeswalker — Kaito",
        text = """
            At the beginning of your end step, if Renji entered this turn, he phases out.
            [+1]: Draw a card. Then discard a card unless you attacked this turn.
            [−2]: Create a 1/1 blue Ninja creature token with "This token can't be blocked."
            [−7]: You get an emblem with "Whenever a creature you control deals combat damage to a player, search your library for a blue or black creature card, put it onto the battlefield, then shuffle."
        """,
        loyalty = "3",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = atYourEndStep,
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.ThisTurn(EventPattern.ZoneChange(to = Zone.Battlefield))),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.PhaseOut(Objects.Self))),
            ),
        ),
        loyalty(1, Instructions(
            steps = listOf(
                draw(Players.You),
                Step.If(
                    condition = Condition.Not(Condition.HappenedThisTurn(EventPattern.Attack(attacker = ObjectFilter.ControlledBy(PlayerFilter.You)))),
                    then = listOf(Step.Do(Effect.Discard(Players.You, count = Value.Fixed(1)))),
                ),
            ),
        )),
        loyalty(-2, Instructions(
            steps = listOf(Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
                name = "Ninja Token",
                colorIndicator = listOf(Color.Blue),
                types = listOf(CardType.Creature),
                subtypes = listOf(Subtype("Ninja")),
                power = PrintedNumber.Fixed(1),
                toughness = PrintedNumber.Fixed(1),
                abilities = listOf(Ability.Static(listOf(Mechanic.CantBeBlocked))),
            ))))),
        )),
        loyalty(-7, Instructions(
            steps = listOf(Step.Do(Effect.CreateEmblem(listOf(Ability.Triggered(
                trigger = Trigger.On(EventPattern.Damage(
                    source = controlledBy(creature, PlayerFilter.You),
                    to = RecipientFilter(players = PlayerFilter.Any),
                    combat = true,
                )),
                instructions = Instructions(
                    steps = listOf(
                        Step.Do(Effect.Search(
                            Players.You,
                            ObjectFilter.And(listOf(
                                ObjectFilter.Or(listOf(ObjectFilter.OfColor(Color.Blue), ObjectFilter.OfColor(Color.Black))),
                                creature,
                            )),
                            bind = "found",
                        )),
                        Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Battlefield))),
                        Step.Do(Effect.Shuffle(Players.You)),
                    ),
                ),
            ))))),
        )),
    )

    /** "Another target" must be a different object (CR 115.3); the exception is part of the copy (CR 707.9b). */
    @Test
    fun orlaGildedArtificer() = card(
        name = "Orla, Gilded Artificer",
        cost = "{1}{U/R}{U/R}",
        type = "Legendary Planeswalker — Saheeli",
        text = """
            Whenever you cast a noncreature spell, create a 1/1 colorless Servo artifact creature token.
            [−2]: Target artifact you control becomes a copy of another target artifact or creature you control until end of turn, except it's an artifact in addition to its other types.
        """,
        loyalty = "5",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = youCastANoncreatureSpell,
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.CreateToken(servo))),
            ),
        ),
        loyalty(-2, Instructions(
            targets = listOf(
                TargetSlot("artifact", TargetKind.Objects(controlledBy(ObjectFilter.Type(CardType.Artifact), PlayerFilter.You))),
                TargetSlot(
                    name = "permanent",
                    what = TargetKind.Objects(controlledBy(
                        ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), creature)),
                        PlayerFilter.You,
                    )),
                    distinctFrom = listOf("artifact"),
                ),
            ),
            steps = listOf(Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.CopyOf(
                    Objects.Target("permanent"),
                    except = listOf(CopyException.Types(add = listOf(CardType.Artifact))),
                )),
                affects = Affected.Objects(Objects.Target("artifact")),
                duration = Duration.EndOfTurn,
            ))),
        )),
    )
}
