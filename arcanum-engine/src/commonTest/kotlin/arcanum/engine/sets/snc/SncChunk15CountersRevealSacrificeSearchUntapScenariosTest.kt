package arcanum.engine.sets.snc

import arcanum.api.Keyword
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Streets of New Capenna chunk 15 in play: sacrificing as a cost and as a trigger, searching, and life changes on your own turn. */
class SncChunk15CountersRevealSacrificeSearchUntapScenariosTest {
    private val dropper = Cards.invent("Snc15 Corpse Dropper", "{B}{R}", "Creature — Devil Warrior", """
        Whenever you sacrifice another creature, put a +1/+1 counter on this creature.
        {B}{R}, Sacrifice another creature: This creature gains menace until end of turn. (It can't be blocked except by two or more creatures.)
    """.trimIndent(), "2/2")
    private val bear = Cards.invent("Snc15 Idle Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val scrivener = Cards.invent("Snc15 Crimson Scrivener", "{4}{B}", "Creature — Vampire Warlock", """
        Flying
        Whenever you gain life during your turn, put a +1/+1 counter on this creature.
        Whenever you lose life during your turn, put a +1/+1 counter on this creature.
    """.trimIndent(), "2/2")
    private val tonic = Cards.invent("Snc15 Ledger Tonic", "{W}", "Instant", "You gain 2 life.")
    private val drain = Cards.invent("Snc15 Ledger Drain", "{B}", "Instant", "You lose 2 life.")
    private val hideout = Cards.invent("Snc15 Ledger Hideout", null, "Land", """
        When this land enters, sacrifice it. When you do, search your library for a basic Forest, Plains, or Island card, put it onto the battlefield tapped, then shuffle and you gain 1 life.
    """.trimIndent())
    private val stomper = Cards.invent("Snc15 Lush Stomper", "{1}{G}{G}", "Creature — Plant Dinosaur", """
        Vigilance
        This creature can't attack or block unless you control seven or more lands.
    """.trimIndent(), "4/4")

    // CR 701.21a (you sacrifice a permanent you control), 603.2 (the sacrifice cost triggers the counter), 702.110 (menace)
    @Test
    fun sacrificingAnotherCreatureAsACostGrowsAndGivesMenace() = scenario {
        alice.battlefield(dropper, bear, "Swamp", "Mountain")
        alice.activates(dropper, choosing = listOf(bear))
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            dropper.power shouldBe 3
            dropper hasKeyword Keyword.Menace.kind
        }
    }

    // CR 701.21a: only a creature you control can be sacrificed
    @Test
    fun anOpponentsCreatureCannotBeSacrificedToIt() = scenario {
        alice.battlefield(dropper, "Swamp", "Mountain")
        bob.battlefield(bear)
        assertFailsWith<Throwable> { alice.activates(dropper, choosing = listOf(bear)) }
    }

    // CR 603.12 (reflexive trigger), 701.21 (sacrifice), 701.23a (search), 614.1 (tapped), 119.3
    @Test
    fun fetchLandSacrificesItselfFetchesTappedAndGainsLife() = scenario {
        alice.hand(hideout)
        alice.library("Swamp", "Island")
        alice.plays(hideout)
        resolveAll()
        alice.chooses("Island")
        resolveAll()
        expect {
            hideout.zone shouldBe Zone.Graveyard
            "Island".zone shouldBe Zone.Battlefield
            "Island".tapped shouldBe true
        }
        assertEquals(21, alice.life)
    }

    // CR 119.3, 119.4, 603.4: life gained or lost counts only during your own turn
    @Test
    fun scrivenerGrowsOnLifeChangesDuringYourTurn() = scenario {
        alice.battlefield(scrivener, "Plains", "Swamp")
        alice.hand(tonic, drain)
        alice.casts(tonic)
        resolveAll()
        expect { scrivener.power shouldBe 3 }
        alice.casts(drain)
        resolveAll()
        expect { scrivener.power shouldBe 4 }
    }

    @Test
    fun scrivenerIgnoresLifeChangesDuringAnOpponentsTurn() = scenario {
        turn(number = 2, active = bob, step = TurnStep.DeclareAttackers, priority = bob)
        alice.battlefield(scrivener, "Plains")
        alice.hand(tonic)
        bob.passes()
        alice.casts(tonic)
        resolveAll()
        expect { scrivener.power shouldBe 2 }
    }

    // CR 508.1c, 509.1b: needs seven lands
    @Test
    fun stomperCannotAttackWithFewerThanSevenLands() = scenario {
        turn(step = TurnStep.DeclareAttackers)
        alice.battlefield(stomper, "Forest", "Forest", "Forest")
        assertFailsWith<Throwable> { alice.attacks(stomper) }
    }

    @Test
    fun stomperAttacksWithSevenLands() = scenario {
        turn(step = TurnStep.DeclareAttackers)
        alice.battlefield(stomper, "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.attacks(stomper)
        expect { stomper.tapped shouldBe false }
    }
}
