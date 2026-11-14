package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/* OTJ chunk 07: Treasure makers (CR 111.10a: a Treasure token is an artifact with "{T}, Sacrifice this token: Add one mana of any color."). */
class OtjChunk07TreasureTest {
    private val treasures: ObjectFilter = sub("Treasure")

    // CR 608.2h: the sacrificed creature as it last existed; CR 700.12 (outlaws)
    @Test
    fun ossuaryDefiler() = card(
        name = "Ossuary Defiler",
        cost = "{3}{B}",
        type = "Creature — Zombie Mercenary",
        text = """
            Menace
            {1}{B}, Sacrifice another creature: Put a +1/+1 counter on this creature. If an outlaw was sacrificed this way, create a Treasure token. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws.)
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Menace,
        activated(cost(mana("{1}{B}"), sacrificeA(and(another, creature), preBind = "sacrificed")),
            counters(Objects.Self),
            ifThen(Condition.ObjectMatches(Objects.Bound("sacrificed"), outlaw), createTreasure())),
    )

    // CR 611.2c: the bonus is counted once, as the spell resolves; CR 115.1 (up to one target)
    @Test
    fun glitterStampede() = card(
        name = "Glitter Stampede",
        cost = "{1}{G}",
        type = "Instant",
        text = "Create a Treasure token. Until end of turn, up to one target creature gets +2/+2 for each Treasure you control.",
    ) parsesTo abilities(
        spell(
            createTreasure(),
            untilEndOfTurn(target("creature"), Mechanic.ModifyPowerToughness(Value.Times(count(and(treasures, you)), 2), Value.Times(count(and(treasures, you)), 2))),
            targets = listOf(slot("creature", creature, min = 0)),
        ),
    )

    // CR 608.2h: "its power" in a dies trigger is its power as it last existed on the battlefield
    @Test
    fun nuggetbackHydra() = card(
        name = "Nuggetback Hydra",
        cost = "{X}{G}",
        type = "Creature — Hydra",
        text = """
            Vigilance, trample, haste
            This creature enters with X +1/+1 counters on it.
            When this creature dies, create a number of tapped Treasure tokens equal to its power.
        """,
        pt = "0/0",
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Trample,
        Keyword.Haste,
        entersWith(p1p1 to Value.CastX(of = Objects.Self)),
        triggered(diesSelf, createTreasure(power(Objects.EventObject(EventRole.Before)), tapped = true)),
    )

    // CR 120.4a: excess damage, beyond lethal for the creature
    @Test
    fun ledgerOfCinders() = card(
        name = "Ledger of Cinders",
        cost = "{X}{R}",
        type = "Sorcery",
        text = "Ledger of Cinders deals X damage to target creature. Create a number of tapped Treasure tokens equal to the amount of excess damage dealt to that creature this way.",
    ) parsesTo abilities(
        spell(
            doing(Effect.DealDamage(toObjects(target("creature")), Value.Announced(name = "X"), Objects.Self, bind = "damage")),
            createTreasure(Value.Excess("damage"), tapped = true),
            targets = listOf(slot("creature", creature)),
            announced = listOf("X"),
        ),
    )

    // CR 700.13, 603.2h; CR 602.5d (activate only as a sorcery)
    @Test
    fun brunhildTheCoinkeeper() = card(
        name = "Brunhild, the Coinkeeper",
        cost = "{1}{R}",
        type = "Legendary Creature — Dwarf Berserker",
        text = """
            Whenever you commit a crime, create a tapped Treasure token. This ability triggers only once each turn. (Targeting opponents, anything they control, and/or cards in their graveyards is a crime.)
            Sacrifice three Treasures: Create a 4/4 red Scorpion Dragon creature token with flying and haste. Activate only as a sorcery.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        crime(createTreasure(tapped = true), perTurn = 1),
        activated(cost(CostPart.Sacrifice(CostObjects.Choose(treasures, count = n(3)))),
            create(creatureToken("Scorpion Dragon", listOf(Color.Red), 4, 4, listOf(Keyword.Flying, Keyword.Haste))),
            rules = asSorcery),
    )

    // CR 603.4: "if you control another outlaw" is checked as it triggers and as it resolves
    @Test
    fun shaftLooter() = card(
        name = "Shaft Looter",
        cost = "{2}{R}",
        type = "Creature — Human Rogue",
        text = """
            Trample
            When this creature enters, if you control another outlaw, create a Treasure token. (Assassins, Mercenaries, Pirates, Rogues, and Warlocks are outlaws. A Treasure token is an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(entersSelf, createTreasure(), interveningIf = youControl(and(another, outlaw), 1)),
    )

    // CR 508.1m: one trigger however many of the creatures attack
    @Test
    fun marlaBrawlingBandit() = card(
        name = "Marla, Brawling Bandit",
        cost = "{1}{R}{G}",
        type = "Legendary Creature — Human Mercenary",
        text = """
            Whenever you attack with one or more creatures with power 4 or greater, create a Treasure token.
            {1}{R}, Sacrifice a Treasure: Marla deals 1 damage to any target.
        """,
        pt = "4/2",
    ) parsesTo abilities(
        triggered(Trigger.On(EventPattern.Attack(and(creature, power(Comparison.AtLeast, 4), you)), oneOrMore = true), createTreasure()),
        activated(cost(mana("{1}{R}"), sacrificeA(treasures)), damage(1, toAny), targets = listOf(anyTarget)),
    )

    // CR 602.2: the sacrifice is a cost
    @Test
    fun rashDeckhand() = card(
        name = "Rash Deckhand",
        cost = "{R}",
        type = "Creature — Goblin Pirate",
        text = """
            First strike, haste
            {2}{R}, Sacrifice this creature: Draw a card and create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Keyword.Haste,
        activated(cost(mana("{2}{R}"), sacrificeSelf), draw(1), createTreasure()),
    )

    // CR 602.2
    @Test
    fun mesaBulwark() = card(
        name = "Mesa Bulwark",
        cost = "{3}",
        type = "Artifact Creature — Golem",
        text = """
            Defender
            {2}, {T}, Sacrifice a land: Draw a card and create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(mana("{2}"), tap, sacrificeA(land)), draw(1), createTreasure()),
    )

    // CR 119.4 (paying life as a cost)
    @Test
    fun coinSifter() = card(
        name = "Coin Sifter",
        cost = "{1}{B}",
        type = "Creature — Human Rogue",
        text = """{1}, {T}, Pay 1 life: Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")""",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap, CostPart.PayLife(n(1))), createTreasure()),
    )
}
