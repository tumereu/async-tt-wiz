package arcanum.engine.sets.lci

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targetsIfAsked
import kotlin.test.Test

/** LCI chunk 06: Vehicles and Equipment played: a Treasure for excess noncombat damage to opponents' creatures (once for a batch), time
 * counters for the permanents untapped in your untap step, doubled and checked by a state trigger, a token as big as the creatures that
 * died, an equip cost of one of two costs, and an optional sacrifice that makes a reflexive trigger. Invented names carry the oracle text. */
class LciChunk06CrewDoubleEquipScenariosTest {
    private val galleon = Cards.invent("Cinderwake Galleon", "{3}{R}{R}", "Artifact — Vehicle", """
        When this Vehicle enters, it deals 5 damage to target creature an opponent controls.
        Whenever one or more creatures your opponents control are dealt excess noncombat damage, create a Treasure token.
        Crew 2
    """, "5/5")
    private val almanac = Cards.invent("The Aeon Almanac", "{1}", "Legendary Artifact", """
        Whenever you untap one or more permanents during your untap step, put that many time counters on The Aeon Almanac.
        {2}, {T}: Double the number of time counters on The Aeon Almanac.
        When there are 1,000 or more time counters on The Aeon Almanac, sacrifice it and each opponent loses 1,000 life.
    """)
    private val crux = Cards.invent("The Sporebone Crux", "{6}{G}{G}", "Legendary Artifact", """
        This spell costs {X} less to cast, where X is the greatest power among creatures you control.
        Whenever one or more nontoken creatures you control die, create a green Fungus Dinosaur creature token with base power and toughness each equal to the total power of those creatures.
        {2}, {T}: Double target creature's power until end of turn.
    """)
    private val flail = Cards.invent("Gorebriar Flail", "{B}", "Artifact — Equipment", """
        Equipped creature gets +2/+1.
        Equip—Pay {3} or discard a card.
    """)
    private val brand = Cards.invent("Dawnflare Brand", "{R}", "Artifact — Equipment", """
        Equipped creature gets +1/+0 and has "Whenever this creature attacks, you may sacrifice Dawnflare Brand. When you do, this creature deals 2 damage to any target."
        Equip {1}
    """)
    private val squall = Cards.invent("Ashen Squall", "{2}{R}", "Sorcery", "Ashen Squall deals 3 damage to each creature.")
    private val skink = Cards.invent("Tunnel Skink", "{R}", "Creature — Lizard", "", "1/1")
    private val tapir = Cards.invent("Ridgeline Tapir", "{1}{G}", "Creature — Tapir", "", "2/2")
    private val boar = Cards.invent("Cragback Boar", "{2}{G}", "Creature — Boar", "", "3/3")

    // CR 120.4a: 5 damage to a 2/2 is 3 excess noncombat damage
    @Test
    fun galleonMakesATreasureForExcessDamage() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        alice.hand(galleon)
        bob.battlefield(tapir)
        alice.casts(galleon)
        resolveAll()
        targetsIfAsked(tapir)
        resolveAll()
        expect {
            bob.graveyard has tapir
            alice.battlefield has "Treasure Token"
        }
    }

    // CR 603.2c: excess damage to creatures of two opponents at once is one batch, one Treasure; lethal damage exactly isn't excess (CR 120.4a)
    @Test
    fun galleonMakesOneTreasureForTheBatch() = scenario(players = 3) {
        alice.battlefield(galleon, "Mountain", "Mountain", "Mountain")
        alice.hand(squall)
        bob.battlefield(skink)
        carol.battlefield(tapir)
        alice.casts(squall)
        resolveAll()
        expect {
            bob.graveyard has skink
            carol.graveyard has tapir
            alice.battlefield.names.count { it == "Treasure Token" } shouldBe 1
        }
    }

    // CR 120.4a: no excess, no Treasure; an excess on your own creature doesn't count ("your opponents control")
    @Test
    fun galleonIgnoresExactAndOwnDamage() = scenario(players = 3) {
        alice.battlefield(galleon, skink, "Mountain", "Mountain", "Mountain")
        alice.hand(squall)
        bob.battlefield(boar)
        alice.casts(squall)
        resolveAll()
        expect {
            alice.graveyard has skink
            bob.graveyard has boar
            alice.battlefield lacks "Treasure Token"
        }
    }

    // CR 701.10e doubles the counters; CR 502.3, 502.4: the untap step's untapping puts that many counters, triggering in your untap step only
    @Test
    fun almanacCountsUntappedPermanentsAndDoubles() = scenario {
        alice.battlefield(almanac, counters = listOf(CounterKind.Time to 5))
        alice.battlefield("Plains", "Plains")
        alice.library("Plains", "Plains")
        bob.library("Plains", "Plains")
        bob.battlefield("Plains", tapped = true)
        alice.activates(almanac)
        resolveAll()
        expect { almanac.counters(CounterKind.Time) shouldBe 10 }
        advanceTo(TurnStep.Upkeep, turn = 2)
        resolveAll()
        expect { almanac.counters(CounterKind.Time) shouldBe 10 }
        advanceTo(TurnStep.Upkeep, turn = 3)
        resolveAll()
        // the two lands and the Almanac itself untapped
        expect { almanac.counters(CounterKind.Time) shouldBe 13 }
    }

    // CR 603.8: a state trigger once there are 1,000 or more; each opponent loses 1,000 life (CR 119.3)
    @Test
    fun almanacReachesAThousand() = scenario(players = 3) {
        alice.battlefield(almanac, counters = listOf(CounterKind.Time to 500))
        alice.battlefield("Plains", "Plains")
        bob.life = 1500
        carol.life = 1200
        alice.activates(almanac)
        resolveAll()
        expect {
            alice.graveyard has almanac
            bob.life shouldBe 500
            carol.life shouldBe 200
            alice.life shouldBe 20
        }
    }

    // CR 603.10a, 608.2h: the total power of the creatures that died, as they last existed; tokens and other players' creatures don't count
    @Test
    fun cruxMakesATokenAsBigAsTheDead() = scenario {
        alice.battlefield(crux, tapir, boar, "Mountain", "Mountain", "Mountain")
        alice.hand(squall)
        bob.battlefield(skink)
        alice.casts(squall)
        resolveAll()
        expect {
            alice.graveyard has tapir
            alice.graveyard has boar
            "Fungus Dinosaur Token".power shouldBe 5
            "Fungus Dinosaur Token".toughness shouldBe 5
        }
    }

    // CR 701.10d: doubling a creature's power gives it +X/+0 where X is its power
    @Test
    fun cruxDoublesPower() = scenario {
        alice.battlefield(crux, boar, "Forest", "Forest")
        alice.activates(crux, ability = 0, target = boar)
        resolveAll()
        expect {
            boar.power shouldBe 6
            boar.toughness shouldBe 3
        }
    }

    // CR 702.6a, 118.1: the equip cost is paid by discarding a card instead of paying {3}
    @Test
    fun flailEquipsForADiscard() = scenario {
        alice.battlefield(flail, tapir)
        alice.hand(skink)
        alice.activates(flail, target = tapir, option = "Discard", choosing = listOf(skink))
        resolveAll()
        expect {
            alice.graveyard has skink
            tapir.power shouldBe 4
            tapir.toughness shouldBe 3
        }
    }

    // CR 603.12: sacrificing the Equipment makes the reflexive trigger; the granted ability names the Equipment
    @Test
    fun brandSacrificedForDamage() = scenario {
        alice.battlefield(brand, tapir)
        attach(brand, tapir)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tapir)
        resolveAll()
        may(true)
        resolveAll()
        targetsIfAsked("bob")
        resolveAll()
        expect {
            alice.graveyard has brand
            bob.life shouldBe 18
        }
    }

    // CR 608.2c: "you may sacrifice": declining keeps the Equipment and makes no trigger
    @Test
    fun brandKeptWhenDeclined() = scenario {
        alice.battlefield(brand, tapir)
        attach(brand, tapir)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(tapir)
        resolveAll()
        may(false)
        resolveAll()
        expect {
            alice.battlefield has brand
            bob.life shouldBe 20
            tapir.power shouldBe 3
        }
    }
}
