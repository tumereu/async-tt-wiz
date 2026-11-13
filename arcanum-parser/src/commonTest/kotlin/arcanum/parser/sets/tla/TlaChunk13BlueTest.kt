package arcanum.parser.sets.tla

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.CopyException
import arcanum.api.CostChange
import arcanum.api.CounterKind
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 13: blue cards: token copies that aren't legendary with another size and creature type (CR 707.9b), an
 * Aura that renames and reshapes a creature (CR 612.8, 205.1b), second-card-drawn triggers, a token that becomes a Construct, a reflexive
 * trigger on discarding a nonland card (CR 603.12), a creature kept tapped while you control it, X counters and half X cards (CR 107.3m),
 * an opponent searching (CR 701.23), and an activation cost reduced per Island.
 */
class TlaChunk13BlueTest {
    private fun copyExcept(slot: String, p: Int, t: Int, type: String) = doing(Effect.CreateToken(TokenSpec.CopyOf(target(slot), except = listOf(
        CopyException.Types(removeSupertypes = listOf(Supertype.Legendary), addSubtypes = listOf(SubtypeRef.Literal(Subtype(type)))),
        CopyException.PowerToughness(p, t))), n(1), Players.You))

    // CR 707.9b
    @Test
    fun cinderIslePageant() = card(
        name = "Cinder Isle Pageant",
        cost = "{3}{U}{U}",
        type = "Sorcery",
        text = """
            Choose one —
            • Create a token that's a copy of target creature you control, except it's not legendary and it's a 4/4 Hero in addition to its other types.
            • Create a token that's a copy of target creature an opponent controls, except it's not legendary and it's a 2/2 Coward in addition to its other types.
        """,
    ) parsesTo abilities(
        modal(
            mode(copyExcept("creature", 4, 4, "Hero"), targets = listOf(slot("creature", creatureYouControl))),
            mode(copyExcept("creature", 2, 2, "Coward"), targets = listOf(slot("creature", creatureOpponentControls))),
        ),
    )

    @Test
    fun omenReadingSeer() = card(
        name = "Omen-Reading Seer",
        cost = "{1}{U}",
        type = "Creature — Human Advisor Ally",
        text = """
            When this creature enters, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
        pt = "1/3",
    ) parsesTo abilities(
        triggered(entersSelf, createClue),
    )

    // CR 612.8 (a name set, layer 3), 205.1b (a creature type replacing the others), 613.1f, 613.4b
    @Test
    fun humbleLabor() = card(
        name = "Humble Labor",
        cost = "{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature an opponent controls
            When this Aura enters, tap enchanted creature and remove all counters from it.
            Enchanted creature loses all abilities and is a Citizen with base power and toughness 1/1 and "{T}: Add {C}" named Humble Merchant. (It loses all other creature types and names.)
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creatureOpponentControls)),
        triggered(entersSelf, tapIt(Objects.AttachedTo(Objects.Self)), doing(Effect.RemoveCounters(Objects.AttachedTo(Objects.Self), null,
            Value.Of(NumericProperty.Counters(null), Objects.AttachedTo(Objects.Self), arcanum.api.Combine.Single)))),
        static(Mechanic.SetName("Humble Merchant"), Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Citizen"))), replaceSubtypes = true),
            Mechanic.LoseAbilities(), has(addColorless), Mechanic.SetPowerToughness(n(1), n(1)), affects = Objects.AttachedTo(Objects.Self)),
    )

    @Test
    fun geckoMacaw() = card(
        name = "Gecko Macaw",
        cost = "{2}{U}",
        type = "Creature — Lizard Bird Pirate",
        text = """
            Flying, vigilance
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        Keyword.Prowess,
    )

    @Test
    fun loreHunter() = card(
        name = "Lore Hunter",
        cost = "{1}{U}",
        type = "Creature — Fox Spirit",
        text = """
            Vigilance
            Whenever you draw your second card each turn, put a +1/+1 counter on this creature.
            When this creature dies, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(drawSecondCard, counters(Objects.Self)),
        triggered(diesSelf, createClue),
    )

    // CR 205.1b: the token becomes a Construct artifact creature (its other creature types replaced), layers 4, 6, 7b
    @Test
    fun theTinkererSkyArtisan() = card(
        name = "The Tinkerer, Sky Artisan",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Artificer Ally",
        text = """
            Whenever you cast a noncreature spell, create a Clue token. (It's an artifact with "{2}, Sacrifice this token: Draw a card.")
            {T}: Until end of turn, target artifact token you control becomes a 3/1 Construct artifact creature with flying.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        triggered(youCast(noncreature), createClue),
        activated(cost(tap), untilEndOfTurn(target("token"),
            Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Construct")))),
            Mechanic.SetPowerToughness(n(3), n(1)), has(Keyword.Flying)),
            targets = listOf(slot("token", and(artifact, token, you)))),
    )

    @Test
    fun ferretPuffin() = card(
        name = "Ferret-Puffin",
        cost = "{1}{U}",
        type = "Creature — Otter Bird",
        text = "Whenever you draw your second card each turn, this creature gets +1/+2 until end of turn and can't be blocked this turn.",
        pt = "2/1",
    ) parsesTo abilities(
        triggered(drawSecondCard, untilEndOfTurn(Objects.Self, pt(1, 2), Mechanic.CantBeBlocked)),
    )

    // CR 122.1d
    @Test
    fun rambunctiousSnowpelters() = card(
        name = "Rambunctious Snowpelters",
        cost = "{2}{U}",
        type = "Creature — Human Peasant Ally",
        text = "When this creature enters, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, tapIt(target("creature")), counters(target("creature"), 1, CounterKind.Stun), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 508.3d (one trigger for the declaration), 603.12 (when you discard a nonland card this way)
    @Test
    fun pellSpiritedGlider() = card(
        name = "Pell, Spirited Glider",
        cost = "{3}{U}",
        type = "Legendary Creature — Human Pilot Ally",
        text = """
            Flying
            Whenever one or more creatures you control with flying attack, draw a card, then discard a card. When you discard a nonland card this way, put a +1/+1 counter on target creature you control.
        """,
        pt = "1/4",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(Trigger.On(EventPattern.Attack(and(creature, you, kw(Keyword.Flying))), oneOrMore = true),
            draw(1), doing(Effect.Discard(Players.You, n(1), ObjectFilter.Any, preBind = "discarded")),
            Step.If(arcanum.api.Condition.ObjectMatches(Objects.Bound("discarded"), nonland), then = listOf(
                reflexive(counters(target("creature")), targets = listOf(slot("creature", creatureYouControl)))))),
    )

    @Test
    fun lynxWalrus() = card(
        name = "Lynx-Walrus",
        cost = "{U}",
        type = "Creature — Cat Seal",
        text = """
            Vigilance
            At the beginning of your upkeep, tap this creature.
            Whenever you draw your second card each turn, untap this creature.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(yourUpkeep, tapIt(Objects.Self)),
        triggered(drawSecondCard, untapIt(Objects.Self)),
    )

    // CR 611.2b: "for as long as you control" this creature
    @Test
    fun junaNerveStriker() = card(
        name = "Juna, Nerve Striker",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Performer Ally",
        text = """
            Flash
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            When Juna enters, tap up to one target creature. It doesn't untap during its controller's untap step for as long as you control Juna.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Prowess,
        triggered(entersSelf, tapIt(target("creature")), doing(Effect.ApplyMechanics(listOf(Mechanic.DoesntUntap), arcanum.api.Affected.Objects(target("creature")),
            arcanum.api.Duration.AsLongAs(arcanum.api.Condition.ObjectMatches(Objects.Self, you)))), targets = listOf(slot("creature", creature, min = 0))),
    )

    // CR 107.3m (the X of its spell), 701.23 (an opponent searching)
    @Test
    fun orvathArchivist() = card(
        name = "Orvath, Archivist",
        cost = "{X}{U}{U}",
        type = "Legendary Creature — Bird Spirit",
        text = """
            Flash
            Flying, vigilance
            When Orvath enters, put X +1/+1 counters on him. Then draw half X cards, rounded down.
            Whenever an opponent searches their library, put a +1/+1 counter on Orvath and draw a card.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        Keyword.Vigilance,
        triggered(entersSelf, counters(Objects.Self, Value.CastX(of = Objects.Self)),
            doing(Effect.Draw(Players.You, Value.Half(Value.CastX(of = Objects.Self), roundUp = false)))),
        triggered(on(EventPattern.Search(PlayerFilter.Opponent)), counters(Objects.Self), draw(1)),
    )

    // CR 601.2f (an activation cost reduced)
    @Test
    fun tideshapingScroll() = card(
        name = "Tideshaping Scroll",
        cost = "{1}{U}",
        type = "Artifact",
        text = "{6}, {T}: Draw a card. This ability costs {1} less to activate for each Island you control.",
    ) parsesTo abilities(
        activated(cost(mana("{6}"), tap), draw(1),
            rules = anyTime.copy(costChange = CostChange.Less(ManaCost.parse("{1}")!!, count(and(sub("Island"), you))))),
    )
}
