package arcanum.parser.sets.spm

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel's Spider-Man chunk 08: blue cards: exile and return at the next end step (CR 603.7), a Villain that turns into a land until your next turn (CR 205.1b), an Aura that makes a 1/1 Citizen with defender and no other abilities (layers 4, 6, 7b), kicker (CR 702.33).
 */
class SpmChunk08BlueTest {

    @Test
    fun clingToTheRafters() = card(
        name = "Cling to the Rafters",
        cost = "{X}{U}",
        type = "Instant",
        text = "Exile X target artifacts and/or creatures. Return the exiled cards to the battlefield under their owners' control at the beginning of the next end step.",
    ) parsesTo abilities(
        spell(
            doing(Effect.Move(objects = target("targets"), to = Destination(Zone.Exile), postBind = "exiled")),
            doing(Effect.CreateDelayedTrigger(
                    trigger = on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.Any)),
                    instructions = Instructions(
                        steps = listOf(
                            doing(Effect.Move(
                                    objects = Objects.Bound("exiled"),
                                    to = Destination(zone = Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
                                )),
                        ),
                    ),
                )),
            targets = listOf(
                TargetSlot(
                    name = "targets",
                    what = TargetKind.Objects(or(artifact, creature)),
                    min = Value.Announced("X"),
                    max = Value.Announced("X"),
                ),
            ),
            announced = listOf("X"),
        ),
    )

    @Test
    fun tidewretchLiquidOutlaw() = card(
        name = "Tidewretch, Liquid Outlaw",
        cost = "{U}{U}",
        type = "Legendary Creature — Elemental Villain",
        pt = "2/2",
        text = """
            Whenever you cast a blue spell, if Tidewretch is a creature, he gets +1/+1 until end of turn.
            At the beginning of your end step, untap Tidewretch. Until your next turn, he becomes a land and gains "{T}: Add {U}." (He's not a creature during that time.)
        """,
    ) parsesTo abilities(
        triggered(
            on(EventPattern.Cast(spell = ObjectFilter.OfColor(ColorRef.Literal(Color.Blue)), by = PlayerFilter.You)),
            untilEndOfTurn(Objects.Self, pt(1, 1)),
            interveningIf = Condition.ObjectMatches(objects = Objects.Self, filter = creature),
        ),
        triggered(
            on(EventPattern.StepBegins(step = TurnStep.End, whose = PlayerFilter.You)),
            doing(Effect.Untap(Objects.Self)),
            doing(Effect.ApplyMechanics(
                    mechanics = listOf(
                        Mechanic.SetTypes(add = listOf(CardType.Land), replaceTypes = true),
                        has(
                            activated(
                                cost(tap),
                                doing(Effect.AddMana(mana = ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!)), players = Players.You)),
                            ),
                        ),
                    ),
                    affects = Affected.Objects(Objects.Self),
                    duration = Duration.UntilYourNextTurn,
                )),
        ),
    )

    @Test
    fun grimsbyLabResearchers() = card(
        name = "Grimsby Lab Researchers",
        cost = "{3}{U}",
        type = "Creature — Human Scientist",
        pt = "1/5",
        text = "{6}{U}: Draw two cards.",
    ) parsesTo abilities(
        activated(cost(mana("{6}{U}")), doing(Effect.Draw(players = Players.You, count = n(2)))),
    )

    @Test
    fun automatonExpertise() = card(
        name = "Automaton Expertise",
        cost = "{4}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            When this Aura enters, create two 1/1 colorless Robot artifact creature tokens with flying.
            Enchanted creature gets +2/+2.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(creature)),
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.CreateToken(
                    token = TokenSpec.Defined(
                        FaceDefinition(
                            name = "Robot Token",
                            colorIndicator = emptyList(),
                            types = listOf(CardType.Artifact, CardType.Creature),
                            subtypes = listOf(Subtype("Robot")),
                            power = PrintedNumber.Fixed(1),
                            toughness = PrintedNumber.Fixed(1),
                            abilities = listOf(Keyword.Flying),
                        ),
                    ),
                    count = n(2),
                    controller = Players.You,
                )),
        ),
        static(pt(2, 2), affects = Objects.AttachedTo(Objects.Self)),
    )

    @Test
    fun bitweaverNetSentinel() = card(
        name = "Bitweaver, Net Sentinel",
        cost = "{2}{U}",
        type = "Legendary Creature — Spider Avatar Hero",
        pt = "2/2",
        text = "When Bitweaver enters, return up to one target nonland permanent to its owner's hand.",
    ) parsesTo abilities(
        triggered(
            on(EventPattern.ZoneChange(objects = ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)),
            doing(Effect.Move(objects = target("permanent"), to = Destination(Zone.Hand))),
            targets = listOf(slot("permanent", and(not(land), ObjectFilter.Category(ObjectCategory.Permanent)), min = 0)),
        ),
    )

    @Test
    fun maskThrownAway() = card(
        name = "Mask Thrown Away",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature is a Citizen with base power and toughness 1/1. It has defender and loses all other abilities. (It also loses all other creature types.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        static(
            Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Citizen"))), replaceSubtypes = true),
            Mechanic.SetPowerToughness(power = n(1), toughness = n(1)),
            Mechanic.LoseAbilities(),
            has(Keyword.Defender),
            affects = Objects.AttachedTo(Objects.Self),
        ),
    )

    @Test
    fun swoosh() = card(
        name = "Swoosh!",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Kicker {1}{U} (You may pay an additional {1}{U} as you cast this spell.)
            Return target nonland permanent to its owner's hand. If this spell was kicked, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(cost(mana("{1}{U}")))),
        spell(
            doing(Effect.Move(objects = target("permanent"), to = Destination(Zone.Hand))),
            Step.If(
                condition = Condition.Paid(keyword = Keyword.Kicker.Kind, times = 1, of = Objects.Self),
                then = listOf(doing(Effect.Draw(players = Players.You, count = n(1)))),
            ),
            targets = listOf(slot("permanent", and(not(land), ObjectFilter.Category(ObjectCategory.Permanent)))),
        ),
    )
}
