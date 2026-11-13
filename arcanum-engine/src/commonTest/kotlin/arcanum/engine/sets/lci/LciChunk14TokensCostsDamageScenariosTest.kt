package arcanum.engine.sets.lci

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** LCI chunk 14 played: an "A or B" additional cost, an additional cost that taps your own permanents, defender overridden by an artifact entering,
 * cost reductions that need the right target, a flashback token copy, a modal spell and a tap-two cost that deals damage to each opponent.
 * Invented names carry the oracle text. */
class LciChunk14TokensCostsDamageScenariosTest {
    private val recompense = Cards.invent("Grim Recompense", "{1}{B}", "Instant", """
        As an additional cost to cast this spell, discard a card or pay 3 life.
        Destroy target creature or planeswalker.
    """)
    private val sentinel = Cards.invent("Sentinel of the Grand Gate", "{W}{W}", "Creature — Angel", """
        As an additional cost to cast this spell, tap four untapped artifacts, creatures, and/or lands you control.
        Flying
    """, "4/4")
    private val lookout = Cards.invent("Wreck Lookout", "{1}{U}", "Creature — Human Pirate", """
        Defender
        As long as an artifact entered the battlefield under your control this turn, this creature can attack as though it didn't have defender.
    """, "3/3")
    private val eddy = Cards.invent("Sinking Eddy", "{5}{W}", "Instant", """
        This spell costs {3} less to cast if it targets a tapped creature.
        Exile target creature.
    """)
    private val echoRite = Cards.invent("Echo Rite", "{4}{U}{U}", "Sorcery", """
        Create a token that's a copy of target creature you control.
        Flashback {3}{U} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
    """)
    private val sweep = Cards.invent("Scorch Sweep", "{1}{R}", "Instant", """
        Choose one —
        • Scorch Sweep deals 3 damage to target creature.
        • Destroy target artifact.
    """)
    private val raider = Cards.invent("Gantry Raider", "{1}{R}", "Creature — Human Soldier",
        "Tap two untapped artifacts and/or creatures you control: This creature deals 1 damage to each opponent. Activate only as a sorcery.", "1/3")
    private fun Scenario.count(name: String) = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == name }
    private val bear = Cards.invent("Brackenwood Cub", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = Cards.invent("Cragmaw Hulk", "{3}{R}", "Creature — Ogre", "", "4/3")
    private val cog = Cards.invent("Brass Sprocket", "{1}", "Artifact", "")
    private val spring = Cards.invent("Brass Coil", "{1}", "Artifact", "")
    private val bauble = Cards.invent("Pewter Charm", "{1}", "Artifact", "")
    private val idol = Cards.invent("Mossy Totem", "{2}", "Artifact", "")

    // CR 118.8, 601.2b: the discard option; the card is discarded as a cost, then the creature dies
    @Test
    fun recompenseDiscardsACard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(recompense, idol)
        bob.battlefield(ogre)
        alice.casts(recompense, target = ogre, option = "Discard", choosing = listOf(idol))
        resolveAll()
        expect {
            alice.graveyard has idol
            bob.graveyard has ogre
            alice.life shouldBe 20
        }
    }

    // CR 118.8, 119.4: the life option pays 3 life and no card
    @Test
    fun recompensePaysThreeLife() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.hand(recompense, idol)
        bob.battlefield(ogre)
        alice.casts(recompense, target = ogre, option = "Pay life")
        resolveAll()
        expect {
            alice.life shouldBe 17
            alice.hand has idol
            bob.graveyard has ogre
        }
    }

    // CR 118.3, 701.26a (audit: the cost reaches only the caster's permanents): four of Alice's own permanents are tapped, Bob's stay untapped
    @Test
    fun sentinelTapsFourOfYourOwnPermanents() = scenario {
        alice.battlefield("Plains", "Plains", bear, cog, spring, "Forest")
        alice.hand(sentinel)
        bob.battlefield(bauble)
        alice.casts(sentinel, choosing = listOf(bear, cog, spring, "Forest"))
        resolveAll()
        expect {
            alice.battlefield has sentinel
            bear.tapped shouldBe true
            cog.tapped shouldBe true
            spring.tapped shouldBe true
            bauble.tapped shouldBe false
        }
    }

    // CR 118.3 (audit): the cost is never offered an opponent's permanent, only Alice's own
    @Test
    fun sentinelIsOfferedOnlyYourOwnPermanents() = scenario {
        alice.battlefield("Plains", "Plains", bear, cog, spring)
        alice.hand(sentinel)
        bob.battlefield(bauble, idol)
        alice.casts(sentinel)
        val offered = decision<Decision.ChooseObjects>().options
        assertTrue(card(bear) in offered)
        assertTrue(card(cog) in offered)
        assertTrue(card(bauble) !in offered)
        assertTrue(card(idol) !in offered)
    }

    // CR 702.3b: with no artifact entered this turn the Lookout can't attack
    @Test
    fun lookoutCannotAttackWithoutAnArtifactEntering() = scenario {
        alice.battlefield(lookout, cog)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(lookout) }
    }

    // CR 702.3b, 603.4: an artifact cast and entering this turn lets it attack
    @Test
    fun lookoutAttacksAfterAnArtifactEnters() = scenario {
        alice.battlefield(lookout, "Island")
        alice.hand(cog)
        alice.casts(cog)
        resolveAll()
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(lookout)
        expect { lookout.tapped shouldBe true }
    }

    // CR 702.3b: an artifact entering under the opponent's control doesn't count
    @Test
    fun lookoutIgnoresAnOpponentsArtifact() = scenario {
        alice.battlefield(lookout)
        bob.battlefield(cog)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { alice.attacks(lookout) }
    }

    // CR 601.2f: three less for a tapped target, so three lands cast it
    @Test
    fun eddyIsCheaperForATappedCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(eddy)
        bob.battlefield(ogre, tapped = true)
        alice.casts(eddy, target = ogre)
        resolveAll()
        expect { ogre.zone shouldBe Zone.Exile }
    }

    // CR 601.2f: an untapped target gets no reduction, so three lands can't pay {5}{W}: the creature stays
    @Test
    fun eddyCostsFullForAnUntappedCreature() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(eddy)
        bob.battlefield(ogre)
        alice.casts(eddy, target = ogre)
        resolveAll()
        expect { bob.battlefield has ogre }
    }

    // CR 707.2, 702.34a: flashback from the graveyard makes a token copy and exiles the card
    @Test
    fun echoRiteFlashbackCopiesAndExiles() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", bear)
        alice.graveyard(echoRite)
        alice.casts(echoRite, target = bear)
        resolveAll()
        expect {
            count("Brackenwood Cub") shouldBe 2
            echoRite.zone shouldBe Zone.Exile
        }
    }

    // CR 700.2: each mode has its own target; the damage mode kills a 2/2 and leaves artifacts alone
    @Test
    fun sweepDamageModeKillsACreature() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(sweep)
        bob.battlefield(bear, cog)
        alice.casts(sweep, modes = listOf(0), target = bear)
        resolveAll()
        expect {
            bob.graveyard has bear
            bob.battlefield has cog
        }
    }

    // CR 700.2, 701.8: the destroy mode destroys an artifact
    @Test
    fun sweepDestroyModeDestroysAnArtifact() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(sweep)
        bob.battlefield(bear, cog)
        alice.casts(sweep, modes = listOf(1), target = cog)
        resolveAll()
        expect {
            bob.graveyard has cog
            bob.battlefield has bear
        }
    }

    // CR 120.3, 701.26a: with three players the Raider's damage hits both opponents; the tap cost takes Alice's Raider and artifact
    @Test
    fun raiderDamagesEachOpponent() = scenario(players = 3) {
        alice.battlefield(raider, cog)
        alice.activates(raider, choosing = listOf(raider, cog))
        resolveAll()
        expect {
            bob.life shouldBe 19
            carol.life shouldBe 19
            alice.life shouldBe 20
            cog.tapped shouldBe true
        }
    }

    // CR 118.3 (audit): with only two of Alice's own to tap, no choice is asked and Bob's artifacts stay untapped
    @Test
    fun raiderTapsOnlyYourOwnPermanents() = scenario(players = 3) {
        alice.battlefield(raider, cog)
        bob.battlefield(spring, bauble)
        alice.activates(raider)
        resolveAll()
        expect {
            raider.tapped shouldBe true
            cog.tapped shouldBe true
            spring.tapped shouldBe false
            bauble.tapped shouldBe false
            bob.life shouldBe 19
            carol.life shouldBe 19
        }
    }
}
