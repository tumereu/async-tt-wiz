package arcanum.parser.sets.bro

import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.Comparison
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.SubtypeRef
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** BRO chunk 12: "put a +1/+1 counter" abilities: targets by subtype, groups, counter triggers and what follows them. */
class BroChunk12PutCountersTest {
    private val soldierYouControl = and(sub("Soldier"), you)
    private val thopter: arcanum.api.TokenSpec = creatureToken("Thopter", emptyList(), 1, 1, abilities = listOf(Keyword.Flying),
        types = listOf(CardType.Artifact, CardType.Creature))

    @Test
    fun skyriderCavalry() = card(
        name = "Skyrider Cavalry", cost = "{4}{W}", type = "Creature — Human Soldier", pt = "3/4",
        text = """
            Flying
            When this creature enters, put a +1/+1 counter on another target Soldier you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, counters(target("Soldier")), targets = listOf(slot("Soldier", and(another, soldierYouControl)))),
    )

    // CR 702.12 (indestructible), 702.11 (hexproof), 702.17 (reach), 702.19 (trample)
    @Test
    fun gaeasGiftLike() = card(
        name = "Bough Blessing", cost = "{1}{G}", type = "Instant",
        text = "Put a +1/+1 counter on target creature you control. It gains reach, trample, hexproof, and indestructible until end of turn.",
    ) parsesTo abilities(
        spell(counters(target("creature")), gains(target("creature"), Keyword.Reach, Keyword.Trample, Keyword.Hexproof, Keyword.Indestructible),
            targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun skullflayerLike() = card(
        name = "Grave Flayer", cost = "{2}{B}", type = "Creature — Phyrexian Human Assassin", pt = "2/3",
        text = "At the beginning of your upkeep, if there are three or more creature cards in your graveyard, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), counters(Objects.Self),
            interveningIf = Condition.Exists(inYourGraveyard(creature), atLeast = 3)),
    )

    // CR 700.4 (dies: battlefield to graveyard), 608.2h
    @Test
    fun anointerLike() = card(
        name = "Gutter Anointer", cost = "{2}{B}", type = "Creature — Phyrexian Horror", pt = "1/3",
        text = """
            Flying
            Whenever you draw your second card each turn, put a +1/+1 counter on this creature.
            When this creature dies, return another target creature card with mana value less than or equal to this creature's power from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(drawSecondCard, counters(Objects.Self)),
        triggered(diesSelf, move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", and(creature, ObjectFilter.ManaValue(Comparison.AtMost, power(Objects.Self)),
                *yourGraveyard.toTypedArray(), not(ObjectFilter.Is(Objects.SelfAfter)))))),
    )

    // CR 205.1b (becomes a creature, keeps its types), 611.2a
    @Test
    fun statueLike() = card(
        name = "Hovering Idol", cost = "{2}", type = "Artifact",
        text = """
            Flying
            Whenever you cast a noncreature spell, put a +1/+1 counter on this artifact.
            {2}: This artifact becomes a 1/1 Construct artifact creature until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(noncreatureSpell), counters(Objects.Self)),
        activated(manaCost("{2}"), untilEndOfTurn(Objects.Self,
            Mechanic.SetTypes(add = listOf(CardType.Artifact, CardType.Creature), addSubtypes = listOf(subtype("Construct")), replaceSubtypes = true),
            Mechanic.SetPowerToughness(n(1), n(1)))),
    )

    // CR 615.1 (prevention), 506.2
    @Test
    fun retrieverLike() = card(
        name = "Rally Hound", cost = "{3}{W}{W}", type = "Creature — Dog Soldier", pt = "3/3",
        text = """
            Flash
            When this creature enters, put a +1/+1 counter on each other Soldier you control.
            Prevent all damage that would be dealt to other attacking Soldiers you control.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, doing(Effect.PutCounters(Objects.Each(and(another, soldierYouControl)), p1p1, n(1)))),
        static(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = and(another, attacking, soldierYouControl))))),
    )

    // CR 700.4, 111.1
    @Test
    fun veteranLike() = card(
        name = "Garrison Elder", cost = "{2}{W}", type = "Creature — Human Soldier", pt = "2/2",
        text = """
            At the beginning of combat on your turn, put a +1/+1 counter on target creature you control.
            Whenever another nontoken Soldier you control dies, create a 1/1 colorless Soldier artifact creature token.
        """,
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
        triggered(dies(and(another, not(token), soldierYouControl)),
            create(creatureToken("Soldier", emptyList(), 1, 1, types = listOf(CardType.Artifact, CardType.Creature)))),
    )

    // CR 700.4
    @Test
    fun mechanicLike() = card(
        name = "Gear Tinker", cost = "{1}{U}", type = "Creature — Human Artificer", pt = "2/1",
        text = """
            Whenever you draw your second card each turn, put a +1/+1 counter on this creature.
            When this creature dies, create a 1/1 colorless Thopter artifact creature token with flying.
        """,
    ) parsesTo abilities(
        triggered(drawSecondCard, counters(Objects.Self)),
        triggered(diesSelf, create(thopter)),
    )

    // CR 603.10a (leaves-the-battlefield style look back for zone changes), 500.1 (during your turn)
    @Test
    fun vigilLike() = card(
        name = "Crypt Vigil", cost = "{1}{B}", type = "Enchantment",
        text = "Whenever one or more artifact and/or creature cards leave your graveyard during your turn, put a +1/+1 counter on target creature you control.",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.ZoneChange(and(or(artifact, creature), ObjectFilter.OwnedBy(PlayerFilter.You)), from = Zone.Graveyard), oneOrMore = true),
            counters(target("creature")), targets = listOf(slot("creature", creatureYouControl)), triggersOnlyIf = Condition.YourTurn),
    )

    @Test
    fun dissidentLike() = card(
        name = "Gear Renegade", cost = "{G}{W}", type = "Creature — Human Artificer", pt = "1/1",
        text = "Whenever an artifact you control enters, put a +1/+1 counter on target creature you control.",
    ) parsesTo abilities(
        triggered(enters(and(artifact, you)), counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )
}
