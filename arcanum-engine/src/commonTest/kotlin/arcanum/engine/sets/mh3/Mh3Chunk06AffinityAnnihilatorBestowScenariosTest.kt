package arcanum.engine.sets.mh3

import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.dft.payYes
import arcanum.engine.sets.dft.yes
import arcanum.engine.sets.ltr.alternativeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The MH3 cards of chunk 06, played: the invented names carry the oracle text of Frogmyr Enforcer, Imskir Iron-Eater, Refurbished Familiar,
 * Breaker of Creation, Glyph Elemental, Nyxborn Hydra, Springheart Nantuko, Indebted Spirit and Triton Wavebreaker.
 */
class Mh3Chunk06AffinityAnnihilatorBestowScenariosTest {
    private val bruiser = Cards.invent("Toadmyr Bruiser", "{7}", "Artifact Creature — Frog Myr", """
        Prototype {3}{R} — 2/2 (You may cast this spell with different mana cost, color, and size. It keeps its abilities and types.)
        Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
    """, "4/4")
    private val kelzar = Cards.invent("Kelzar Slag-Glutton", "{6}{B}{R}", "Legendary Creature — Demon", """
        Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
        When Kelzar enters, you draw X cards and you lose X life, where X is half the number of artifacts you control, rounded down.
        {3}{R}, Sacrifice an artifact: Kelzar deals damage equal to the sacrificed artifact's mana value to any target.
    """, "5/5")
    private val vermin = Cards.invent("Salvaged Vermin", "{3}{B}", "Artifact Creature — Zombie Rat", """
        Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
        Flying
        When this creature enters, each opponent discards a card. For each opponent who can't, you draw a card.
    """, "2/1")
    private val unmaker = Cards.invent("Unmaker of Hues", "{6}{C}{C}", "Creature — Eldrazi", """
        When you cast this spell, you gain 1 life for each colorless permanent you control.
        Hexproof from each color
        Annihilator 2 (Whenever this creature attacks, defending player sacrifices two permanents of their choice.)
    """, "8/4")
    private val wisp = Cards.invent("Sigil Wisp", "{1}{W}", "Enchantment Creature — Elemental", """
        Bestow {1}{W} (If you cast this card for its bestow cost, it's an Aura spell with enchant creature. It becomes a creature again if it's not attached.)
        Landfall — Whenever a land you control enters, put a +1/+1 counter on this permanent.
        Enchanted creature gets +1/+1 for each +1/+1 counter on this Aura.
    """, "2/2")
    private val hydra = Cards.invent("Starborn Hydra", "{X}{G}", "Enchantment Creature — Hydra", """
        Bestow {X}{G}{G} (If you cast this card for its bestow cost, it's an Aura spell with enchant creature. It becomes a creature again if it's not attached.)
        Reach, trample
        This permanent enters with X +1/+1 counters on it.
        Enchanted creature gets +1/+1 for each +1/+1 counter on this Aura and has reach and trample.
    """, "0/1")
    private val mantis = Cards.invent("Thicketbloom Mantis", "{1}{G}", "Enchantment Creature — Insect Monk", """
        Bestow {1}{G}
        Enchanted creature gets +1/+1.
        Landfall — Whenever a land you control enters, you may pay {1}{G} if this permanent is attached to a creature you control. If you do, create a token that's a copy of that creature. If you didn't create a token this way, create a 1/1 green Insect creature token.
    """, "1/1")
    private val shade = Cards.invent("Owing Shade", "{W}", "Enchantment Creature — Spirit", """
        Bestow {2}{W} (If you cast this card for its bestow cost, it's an Aura spell with enchant creature. It becomes a creature again if it's not attached.)
        Afterlife 1 (When this permanent is put into a graveyard from the battlefield, create a 1/1 white and black Spirit creature token with flying.)
        Enchanted creature gets +1/+1 and has afterlife 1.
    """, "1/1")
    private val triton = Cards.invent("Tidewake Triton", "{U}", "Enchantment Creature — Merfolk Wizard", """
        Bestow {1}{U} (If you cast this card for its bestow cost, it's an Aura spell with enchant creature. It becomes a creature again if it's not attached.)
        As long as this permanent is a creature, it has prowess. (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
        Enchanted creature gets +1/+1 and has prowess.
    """, "1/1")

    private val trinket = Cards.invent("Slag Trinket", "{1}", "Artifact", "")
    private val ingot = Cards.invent("Slag Ingot", "{3}", "Artifact", "")
    private val flats = Cards.invent("Voidscar Flats", null, "Land", "{T}: Add {C}.")
    private val bolt = Cards.invent("Shardless Bolt", "{2}", "Instant", "Shardless Bolt deals 2 damage to any target.")
    private val hueShock = Cards.invent("Hue Shock", "{R}", "Instant", "Hue Shock deals 2 damage to any target.")
    private val spark = Cards.invent("Grudge Spark", "{R}", "Instant", "Grudge Spark deals 2 damage to any target.")
    private val bear = Cards.invent("Bestow Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    private fun Scenario.bestowed(name: String) = alternativeOf(name, Keyword.Bestow.Kind)

    // CR 702.160a, 718.3: cast prototyped, a red 2/2; CR 702.41a, 601.2f: affinity lowers the prototype cost too ({3}{R} less two)
    @Test
    fun prototypedWithAffinity() = scenario {
        alice.battlefield("Mountain", "Mountain", trinket, ingot)
        alice.hand(bruiser)
        alice.casts(bruiser, way = alternativeOf(bruiser, Keyword.Prototype.Kind))
        allPass()
        expect {
            alice.battlefield has bruiser
            bruiser.power shouldBe 2
            bruiser.toughness shouldBe 2
            bruiser.colors shouldBe listOf(Color.Red)
        }
    }

    // CR 107.1a: X is half of five artifacts, rounded down: two cards drawn and two life lost
    @Test
    fun entersDrawsAndLosesHalfTheArtifacts() = scenario {
        alice.battlefield("Swamp", "Mountain", "Mountain", trinket, trinket, trinket, trinket, ingot)
        alice.hand(kelzar)
        alice.library("Forest", "Forest", "Forest")
        alice.casts(kelzar)
        resolveAll()
        expect {
            alice.battlefield has kelzar
            alice.hand.size shouldBe 2
            alice.life shouldBe 18
        }
    }

    // CR 608.2h: damage equal to the sacrificed artifact's mana value as it last existed
    @Test
    fun sacrificedArtifactsManaValueAsDamage() = scenario {
        alice.battlefield(kelzar, "Mountain", "Mountain", "Mountain", "Mountain", ingot, trinket)
        alice.activates(kelzar, target = "bob", choosing = listOf(ingot))
        resolveAll()
        expect {
            alice.graveyard has ingot
            alice.battlefield has trinket
            bob.life shouldBe 17
        }
    }

    // CR 701.9b: with three players, only the opponent with no card can't discard: you draw one card
    @Test
    fun eachOpponentDiscardsDrawForEachWhoCant() = scenario(players = 3) {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(vermin)
        alice.library("Forest", "Forest")
        bob.hand("Island")
        alice.casts(vermin)
        resolveAll()
        expect {
            bob.graveyard has "Island"
            alice.hand.size shouldBe 1
        }
    }

    // CR 603.2: the cast trigger counts colorless permanents (lands and artifacts, not a green creature, nor the spell itself)
    @Test
    fun castTriggerGainsForEachColorlessPermanent() = scenario {
        alice.battlefield(flats, flats, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", trinket, bear)
        alice.hand(unmaker)
        alice.casts(unmaker)
        resolveAll()
        expect {
            alice.battlefield has unmaker
            alice.life shouldBe 29
        }
    }

    // CR 702.11d: hexproof from each color: an opponent's red spell can't target it
    @Test
    fun hexproofFromEachColorStopsAnOpponentsColoredSpell() = scenario {
        alice.battlefield(unmaker)
        bob.battlefield("Mountain")
        bob.hand(spark)
        turn(priority = bob)
        bob.casts(spark)
        assertTrue(target(unmaker) !in decision<Decision.Targets>().options)
    }

    // CR 702.11d: an opponent's colorless spell can target it, and so can its controller's red one
    @Test
    fun hexproofFromEachColorAllowsColorlessAndOwnSpells() = scenario {
        alice.battlefield(unmaker, "Mountain")
        alice.hand(hueShock)
        bob.battlefield("Island", "Island")
        bob.hand(bolt)
        turn(priority = bob)
        bob.casts(bolt, target = unmaker)
        bob.passes()
        alice.casts(hueShock, target = unmaker)
        resolveAll()
        expect { alice.graveyard has unmaker }
    }

    // CR 702.86a, 506.2: with three players, only the defending player sacrifices two permanents of their choice
    @Test
    fun annihilatorTwoOnlyTheDefendingPlayer() = scenario(players = 3) {
        alice.battlefield(unmaker)
        bob.battlefield("Island", "Island", bear)
        carol.battlefield("Plains", "Plains")
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(unmaker, defender = "bob")
        allPass()
        assertEquals(bob.id, decision<Decision.ChooseObjects>().player)
        bob.chooses("Island", "Island")
        expect {
            bob.graveyard.size shouldBe 2
            bob.battlefield has bear
            carol.battlefield.size shouldBe 2
        }
    }

    // CR 702.103b, 122.6: bestowed, landfall puts its counter on the Aura, which gives the enchanted creature +1/+1 for it
    @Test
    fun bestowedAuraCountsItsOwnCounters() = scenario {
        alice.battlefield("Plains", "Plains", bear)
        alice.hand(wisp, "Plains")
        alice.casts(wisp, way = bestowed(wisp), target = bear)
        allPass()
        alice.plays("Plains")
        resolveAll()
        expect {
            bear.power shouldBe 3
            bear.toughness shouldBe 3
        }
    }

    // CR 107.3, 614.1c: bestowed for X = 2, it enters with two counters: the creature gets +2/+2, reach and trample
    @Test
    fun bestowedWithX() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", "Forest", bear)
        alice.hand(hydra)
        alice.casts(hydra, way = bestowed(hydra), target = bear, x = 2)
        resolveAll()
        expect {
            bear.power shouldBe 4
            bear.toughness shouldBe 4
            bear hasKeyword Keyword.Reach
            bear hasKeyword Keyword.Trample
        }
    }

    // CR 608.2c, 707.2: attached to your creature, paying makes a copy of it and no Insect
    @Test
    fun attachedPayingCopiesTheCreature() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(mantis, "Forest")
        alice.casts(mantis, way = bestowed(mantis), target = bear)
        allPass()
        alice.plays("Forest")
        resolveAll()
        payYes()
        expect {
            alice.battlefield.names.count { it == bear } shouldBe 2
            alice.battlefield lacks "Insect Token"
        }
    }

    // CR 608.2c: declining the payment, no token was copied: an Insect instead
    @Test
    fun attachedDecliningMakesAnInsect() = scenario {
        alice.battlefield("Forest", "Forest", "Forest", bear)
        alice.hand(mantis, "Forest")
        alice.casts(mantis, way = bestowed(mantis), target = bear)
        allPass()
        alice.plays("Forest")
        resolveAll()
        yes(false)
        expect {
            alice.battlefield.names.count { it == bear } shouldBe 1
            alice.battlefield has "Insect Token"
        }
    }

    // CR 608.2c: as a creature it is attached to nothing: no payment is offered, an Insect is made
    @Test
    fun unattachedMakesAnInsect() = scenario {
        alice.battlefield(mantis, "Forest", "Forest", bear)
        alice.hand("Forest")
        alice.plays("Forest")
        resolveAll()
        expect {
            pending shouldBe null
            alice.battlefield has "Insect Token"
            alice.battlefield.names.count { it == bear } shouldBe 1
        }
    }

    // CR 702.135a, 603.10a: the enchanted creature dies with afterlife granted: a Spirit; CR 702.103f: the Aura stays as a creature
    @Test
    fun grantedAfterlifeAndTheAuraStays() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Mountain", "Island", "Island", bear)
        alice.hand(shade, spark, bolt)
        alice.casts(shade, way = bestowed(shade), target = bear)
        allPass()
        alice.casts(spark, target = bear)
        alice.casts(bolt, target = bear)
        resolveAll()
        expect {
            alice.graveyard has bear
            alice.battlefield has "Spirit Token"
            alice.battlefield has shade
            shade.power shouldBe 1
        }
    }

    // CR 702.108a, 702.103b: bestowed, the enchanted creature has prowess and the Aura, not a creature, has none
    @Test
    fun bestowedProwessOnlyOnTheCreature() = scenario {
        alice.battlefield("Island", "Island", "Mountain", bear)
        alice.hand(triton, spark)
        alice.casts(triton, way = bestowed(triton), target = bear)
        allPass()
        alice.casts(spark, target = "bob")
        resolveAll()
        expect {
            bear.power shouldBe 4
            triton lacksKeyword Keyword.Prowess
            bob.life shouldBe 18
        }
    }

    // CR 702.108a: as a creature it has prowess
    @Test
    fun asACreatureItHasProwess() = scenario {
        alice.battlefield(triton, "Mountain")
        alice.hand(spark)
        alice.casts(spark, target = "bob")
        resolveAll()
        expect { triton.power shouldBe 2 }
    }
}
