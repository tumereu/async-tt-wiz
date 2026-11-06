package arcanum.parser.sets.dft

import arcanum.api.Comparison
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Trigger
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Aetherdrift chunk 04: Mounts with saddle (CR 702.171) and "whenever this creature attacks while saddled" (saddled as it attacks, CR
 * 702.171b): pumps, untapping, counters, searches, damage, a Treasure, tokens, and an enters trigger joined with an attack while saddled.
 */
class DftChunk04MountsTest {
    @Test
    fun dunelightGlider() = card(
        name = "Dunelight Glider",
        cost = "{W}",
        type = "Creature — Possum Mount",
        text = """
            Vigilance
            Whenever this creature attacks while saddled, it gets +1/+2 and gains flying until end of turn.
            Saddle 3 (Tap any number of other creatures you control with total power 3 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "1/1",
    ) parsesTo abilities(Keyword.Vigilance, attacksWhileSaddled(pump(Objects.Self, 1, 2, has(Keyword.Flying))), Keyword.Saddle(3))

    @Test
    fun dunelightStallion() = card(
        name = "Dunelight Stallion",
        cost = "{3}{W}",
        type = "Creature — Horse Mount",
        text = """
            Whenever this creature attacks while saddled, untap it and put a +1/+1 counter on it.
            Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "3/3",
    ) parsesTo abilities(attacksWhileSaddled(untapIt(Objects.Self), counters(Objects.Self)), Keyword.Saddle(1))

    @Test
    fun rampartYak() = card(
        name = "Rampart Yak",
        cost = "{1}{W}",
        type = "Creature — Ox Mount",
        text = """
            Whenever this creature attacks while saddled, put a +1/+1 counter on target creature.
            Sacrifice this creature: Creatures you control with counters on them gain hexproof and indestructible until end of turn.
            Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        attacksWhileSaddled(counters(target("creature")), targets = listOf(slot("creature", creature))),
        activated(cost(sacrificeSelf), gains(Objects.Each(and(creature, you, ObjectFilter.HasCounter(null, atLeast = 1))), Keyword.Hexproof, Keyword.Indestructible)),
        Keyword.Saddle(1),
    )

    @Test
    fun glimmerheathOcelot() = card(
        name = "Glimmerheath Ocelot",
        cost = "{1}{W}",
        type = "Creature — Cat Mount",
        text = """
            Lifelink
            Whenever this creature attacks while saddled, search your library for a basic Plains card, reveal it, put it into your hand, then shuffle.
            Saddle 2 (Tap any number of other creatures you control with total power 2 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Lifelink,
        attacksWhileSaddled(search(and(basic, sub("Plains")), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        Keyword.Saddle(2),
    )

    @Test
    fun sentinelDawnmare() = card(
        name = "Sentinel Dawnmare",
        cost = "{3}{W}{W}",
        type = "Creature — Horse Mount",
        text = """
            Ward {2}
            Whenever this creature attacks while saddled, search your library for a nonland permanent card with mana value 3 or less, put it onto the battlefield, then shuffle.
            Saddle 4
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        attacksWhileSaddled(search(and(nonland, permanentCard, manaValue(Comparison.AtMost, 3))), move(Objects.Bound("found"), Zone.Battlefield), shuffle),
        Keyword.Saddle(4),
    )

    @Test
    fun unhurriedSloth() = card(
        name = "Unhurried Sloth",
        cost = "{3}{W}{W}",
        type = "Creature — Sloth Mount",
        text = """
            Whenever this creature attacks while saddled, it gains indestructible until end of turn. Untap all creatures you control.
            Saddle 4 (Tap any number of other creatures you control with total power 4 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "5/5",
    ) parsesTo abilities(
        attacksWhileSaddled(gains(Objects.Self, Keyword.Indestructible), untapIt(Objects.Each(creatureYouControl))),
        Keyword.Saddle(4),
    )

    @Test
    fun dracosaurOutrider() = card(
        name = "Dracosaur Outrider",
        cost = "{4}{R}{R}",
        type = "Creature — Dinosaur Dragon Mount",
        text = """
            Flying, haste
            Whenever this creature attacks while saddled, it deals 2 damage to any target.
            Saddle 3 (Tap any number of other creatures you control with total power 3 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "4/4",
    ) parsesTo abilities(Keyword.Flying, Keyword.Haste, attacksWhileSaddled(damage(2, toAny), targets = listOf(anyTarget)), Keyword.Saddle(3))

    @Test
    fun gildedGhodra() = card(
        name = "Gilded Ghodra",
        cost = "{1}{R}",
        type = "Creature — Horse Mount",
        text = """
            Whenever this creature attacks while saddled, create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "2/2",
    ) parsesTo abilities(attacksWhileSaddled(createTreasure), Keyword.Saddle(1))

    @Test
    fun alacrianPanther() = card(
        name = "Alacrian Panther",
        cost = "{4}{G}",
        type = "Creature — Cat Mount",
        text = """
            Vigilance
            Whenever this creature attacks while saddled, it gets +2/+2 until end of turn.
            Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "4/4",
    ) parsesTo abilities(Keyword.Vigilance, attacksWhileSaddled(pump(Objects.Self, 2, 2)), Keyword.Saddle(1))

    // CR 603.2: one ability with two trigger events; only the attack needs it saddled
    @Test
    fun autarchMastodon() = card(
        name = "Autarch Mastodon",
        cost = "{4}{G}{G}",
        type = "Creature — Elephant Mount",
        text = """
            When this creature enters and whenever it attacks while saddled, create a 3/3 green Elephant creature token.
            Saddle 5 (Tap any number of other creatures you control with total power 5 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "5/5",
    ) parsesTo abilities(
        triggered(Trigger.On(listOf(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), attacksSaddled)), create(elephant)),
        Keyword.Saddle(5),
    )

    // CR 122.6: it enters with the counter; removing two is a cost
    @Test
    fun districtMascotPup() = card(
        name = "District Mascot Pup",
        cost = "{G}",
        type = "Creature — Dog Mount",
        text = """
            This creature enters with a +1/+1 counter on it.
            {1}{G}, Remove two +1/+1 counters from this creature: Destroy target artifact.
            Whenever this creature attacks while saddled, put a +1/+1 counter on it.
            Saddle 1
        """,
        pt = "0/0",
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(isSelf, to = Zone.Battlefield), modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))),
        activated(cost(mana("{1}{G}"), CostPart.RemoveCounters(p1p1, n(2), CostObjects.Self)), destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
        attacksWhileSaddled(counters(Objects.Self)),
        Keyword.Saddle(1),
    )

    @Test
    fun venomsacLizard() = card(
        name = "Venomsac Lizard",
        cost = "{1}{G}",
        type = "Creature — Lizard Mount",
        text = """
            Deathtouch
            Whenever this creature attacks while saddled, it gets +0/+3 until end of turn.
            Saddle 2 (Tap any number of other creatures you control with total power 2 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "2/1",
    ) parsesTo abilities(Keyword.Deathtouch, attacksWhileSaddled(pump(Objects.Self, 0, 3)), Keyword.Saddle(2))

    @Test
    fun lagorenSoulOfAlacria() = card(
        name = "Lagoren, Soul of Alacria",
        cost = "{G}{W}",
        type = "Legendary Creature — Beast Mount",
        text = """
            Flying
            Whenever Lagoren attacks while saddled, put a +1/+1 counter on each of up to two target Mounts and/or Vehicles.
            Saddle 1 (Tap any number of other creatures you control with total power 1 or more: This Mount becomes saddled until end of turn. Saddle only as a sorcery.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        attacksWhileSaddled(counters(target("Vehicles")), targets = listOf(slot("Vehicles", mountOrVehicle, min = 0, max = 2))),
        Keyword.Saddle(1),
    )
}
