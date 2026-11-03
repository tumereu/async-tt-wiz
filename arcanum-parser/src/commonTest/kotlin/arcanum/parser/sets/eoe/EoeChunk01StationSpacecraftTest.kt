package arcanum.parser.sets.eoe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Edge of Eternities chunk 01: station (CR 702.184) and station symbols with a P/T box (CR 721.2a, b): Spacecraft that are creatures from a number of charge counters, with enters and attack triggers.
 */
class EoeChunk01StationSpacecraftTest {

    @Test
    fun stratosphericArboretum() = card(
        name = "Stratospheric Arboretum",
        cost = "{4}{G}",
        type = "Artifact — Spacecraft",
        pt = "5/4",
        text = """
            When this Spacecraft enters, put a +1/+1 counter on each creature you control.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
            8+ | Flying, trample
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.PutCounters(objects = Objects.Each(filter = and(creature, you)), kind = p1p1, amount = n(1)))),
        Keyword.Station,
        station(8, Keyword.Flying, Keyword.Trample, pt = "5/4"),
    )

    @Test
    fun highsunStarwardDreadnought() = card(
        name = "Highsun, Starward Dreadnought",
        cost = "{5}",
        type = "Legendary Artifact — Spacecraft",
        pt = "20/20",
        text = """
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 20+.)
            10+ | Whenever you attack, Highsun deals 100 damage to up to one target creature or planeswalker.
            20+ | Flying
        """,
    ) parsesTo abilities(
        Keyword.Station,
        station(
            10,
            triggered(
                Trigger.On(events = listOf(EventPattern.Attack(attacker = you)), oneOrMore = true),
                doing(Effect.DealDamage(to = Recipients(objects = target("permanent")), amount = n(100), source = Objects.Self)),
                targets = listOf(slot("permanent", or(creature, ObjectFilter.Type(CardType.Planeswalker)), min = 0)),
            ),
        ),
        station(20, Keyword.Flying, pt = "20/20"),
    )

    @Test
    fun wreckageBeltGrinder() = card(
        name = "Wreckage Belt Grinder",
        cost = "{4}{R}",
        type = "Artifact — Spacecraft",
        pt = "1/5",
        text = """
            When this Spacecraft enters, it deals 3 damage to any target.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
            8+ | Flying
            {1}{R}: This Spacecraft gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.DealDamage(to = toAny, amount = n(3), source = Objects.Self)), targets = listOf(anyTarget)),
        Keyword.Station,
        station(8, Keyword.Flying, pt = "1/5"),
        activated(cost(mana("{1}{R}")), untilEndOfTurn(Objects.Self, pt(2, 0))),
    )

    @Test
    fun decayingWarcruiser() = card(
        name = "Decaying Warcruiser",
        cost = "{3}{B}",
        type = "Artifact — Spacecraft",
        pt = "3/10",
        text = """
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
            1+ | Whenever an opponent discards a card, they lose 3 life.
            8+ | Flying, deathtouch
            Whenever this Spacecraft attacks, each opponent discards a card. Each opponent who can't loses 3 life.
        """,
    ) parsesTo abilities(
        Keyword.Station,
        station(
            1,
            triggered(
                on(EventPattern.Discard(cards = ObjectFilter.Any, players = PlayerFilter.Opponent)),
                doing(Effect.LoseLife(players = Players.EventPlayer, amount = n(3))),
            ),
        ),
        station(8, Keyword.Flying, Keyword.Deathtouch, pt = "3/10"),
        triggered(
            attacksSelf,
            Step.ForEach(
                each = Each.Players(players = Players.Each(filter = PlayerFilter.Opponent)),
                bind = "opponent",
                steps = listOf(
                    Step.If(
                        condition = Condition.Compare(
                            left = Value.HandSize(players = Players.Bound(name = "opponent")),
                            comparison = Comparison.Equal,
                            right = n(0),
                        ),
                        then = listOf(doing(Effect.LoseLife(players = Players.Bound(name = "opponent"), amount = n(3)))),
                        otherwise = listOf(
                            doing(Effect.Discard(players = Players.Bound(name = "opponent"), count = n(1), filter = ObjectFilter.Any)),
                        ),
                    ),
                ),
                together = true,
            ),
        ),
    )

    @Test
    fun snufferClassWarship() = card(
        name = "Snuffer-Class Warship",
        cost = "{8}",
        type = "Artifact — Spacecraft",
        pt = "10/10",
        text = """
            When this Spacecraft enters, destroy target noncreature permanent. Then this Spacecraft deals 4 damage to each creature.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 5+.)
            5+ | Flying, trample
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Destroy(objects = target("permanent"))),
            doing(Effect.DealDamage(to = Recipients(objects = Objects.Each(filter = creature)), amount = n(4), source = Objects.Self)),
            targets = listOf(slot("permanent", and(not(creature), permanentFilter))),
        ),
        Keyword.Station,
        station(5, Keyword.Flying, Keyword.Trample, pt = "10/10"),
    )

    @Test
    fun grimGravityBarge() = card(
        name = "Grim Gravity Barge",
        cost = "{2}{B}",
        type = "Artifact — Spacecraft",
        pt = "3/2",
        text = """
            When this Spacecraft enters, mill three cards, then return a creature or Spacecraft card from your graveyard to your hand.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
            8+ | Flying, lifelink
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Mill(players = Players.You, count = n(3))),
            Step.Choose(
                choice = Choice.Objects(
                    filter = and(or(creature, sub("Spacecraft")), inZone(Zone.Graveyard), ownedByYou),
                    min = n(1),
                    max = n(1),
                ),
                bind = "chosen",
                chooser = Players.You,
            ),
            doing(Effect.Move(objects = bound("chosen"), to = Destination(zone = Zone.Hand))),
        ),
        Keyword.Station,
        station(8, Keyword.Flying, Keyword.Lifelink, pt = "3/2"),
    )

    @Test
    fun arcChargedBladeship() = card(
        name = "Arc-Charged Bladeship",
        cost = "{5}{R}",
        type = "Artifact — Spacecraft",
        pt = "6/5",
        text = """
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 3+.)
            3+ | Flying, haste
        """,
    ) parsesTo abilities(
        Keyword.Station,
        station(3, Keyword.Flying, Keyword.Haste, pt = "6/5"),
    )

    @Test
    fun radianceClassCutter() = card(
        name = "Radiance-Class Cutter",
        cost = "{1}{W}",
        type = "Artifact — Spacecraft",
        pt = "3/5",
        text = """
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 12+.)
            2+ | Other creatures you control get +1/+1.
            12+ | Flying, lifelink
        """,
    ) parsesTo abilities(
        Keyword.Station,
        station(2, static(pt(1, 1), affects = Objects.Each(filter = and(not(isSelf), creature, you)))),
        station(12, Keyword.Flying, Keyword.Lifelink, pt = "3/5"),
    )

    @Test
    fun apexHunterFrigate() = card(
        name = "Apex Hunter-Frigate",
        cost = "{7}",
        type = "Artifact — Spacecraft",
        pt = "7/7",
        text = """
            When this Spacecraft enters, it deals 10 damage to up to one target creature.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
            7+ | Flying
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.DealDamage(to = Recipients(objects = target("creature")), amount = n(10), source = Objects.Self)),
            targets = listOf(slot("creature", creature, min = 0)),
        ),
        Keyword.Station,
        station(7, Keyword.Flying, pt = "7/7"),
    )

    @Test
    fun salvageLifeboat() = card(
        name = "Salvage Lifeboat",
        cost = "{5}{W}",
        type = "Artifact — Spacecraft",
        pt = "5/6",
        text = """
            When this Spacecraft enters, return target creature or enchantment card from your graveyard to the battlefield.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 10+.)
            10+ | Flying
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Move(objects = target("card"), to = Destination(zone = Zone.Battlefield))),
            targets = listOf(slot("card", and(or(creature, enchantment), inZone(Zone.Graveyard), ownedByYou))),
        ),
        Keyword.Station,
        station(10, Keyword.Flying, pt = "5/6"),
    )

    @Test
    fun theHalcyonWing() = card(
        name = "The Halcyon Wing",
        cost = "{1}{W}{W}",
        type = "Legendary Artifact — Spacecraft",
        pt = "5/5",
        text = """
            When The Halcyon Wing enters, search your library for a legendary creature card, reveal it, put it into your hand, then shuffle.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 7+.)
            7+ | Flying
            Other tapped legendary creatures you control have indestructible.
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(
                Effect.Search(
                    library = Players.You,
                    filter = and(ObjectFilter.Super(supertype = Supertype.Legendary), creature),
                    max = n(1),
                    reveal = true,
                    zones = listOf(Zone.Library),
                    bind = "found",
                    searcher = Players.You,
                ),
            ),
            doing(Effect.Move(objects = bound("found"), to = Destination(zone = Zone.Hand))),
            doing(Effect.Shuffle(library = Players.You)),
        ),
        Keyword.Station,
        station(7, Keyword.Flying, pt = "5/5"),
        static(
            has(Keyword.Indestructible),
            affects = Objects.Each(filter = and(not(isSelf), tapped, ObjectFilter.Super(supertype = Supertype.Legendary), creature, you)),
        ),
    )

    @Test
    fun surveyProbeCutter() = card(
        name = "Survey Probe Cutter",
        cost = "{3}{U}",
        type = "Artifact — Spacecraft",
        pt = "4/4",
        text = """
            When this Spacecraft enters, draw two cards, then discard a card.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 8+.)
            8+ | Flying
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(Effect.Draw(players = Players.You, count = n(2))),
            doing(Effect.Discard(players = Players.You, count = n(1), filter = ObjectFilter.Any)),
        ),
        Keyword.Station,
        station(8, Keyword.Flying, pt = "4/4"),
    )

    @Test
    fun warforgerGunboat() = card(
        name = "Warforger Gunboat",
        cost = "{2}{R}",
        type = "Artifact — Spacecraft",
        pt = "4/3",
        text = """
            When this Spacecraft enters, it deals damage equal to the number of artifacts you control to target creature an opponent controls.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 6+.)
            6+ | Flying
        """,
    ) parsesTo abilities(
        triggered(
            entersSelf,
            doing(
                Effect.DealDamage(
                    to = Recipients(objects = target("creature")),
                    amount = Value.Count(objects = Objects.Each(filter = and(artifact, you))),
                    source = Objects.Self,
                ),
            ),
            targets = listOf(slot("creature", and(creature, opponents))),
        ),
        Keyword.Station,
        station(6, Keyword.Flying, pt = "4/3"),
    )

    @Test
    fun prismwedgeRamship() = card(
        name = "Prismwedge Ramship",
        cost = "{3}{W}",
        type = "Artifact — Spacecraft",
        pt = "3/4",
        text = """
            When this Spacecraft enters, create a 2/2 colorless Robot artifact creature token.
            Station (Tap another creature you control: Put charge counters equal to its power on this Spacecraft. Station only as a sorcery. It's an artifact creature at 9+.)
            9+ | Flying, first strike
        """,
    ) parsesTo abilities(
        triggered(entersSelf, doing(Effect.CreateToken(token = robot, count = n(1), controller = Players.You))),
        Keyword.Station,
        station(9, Keyword.Flying, Keyword.FirstStrike, pt = "3/4"),
    )
}
