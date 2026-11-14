package arcanum.engine.sets.bro

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.ltr.playOut
import arcanum.engine.sets.ltr.resolveAll
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The Brothers' War chunk 14 in play: a cost reduction per graveyard card, "counter unless its controller pays", a tap-three-Soldiers
 * cost, vigilance in an attack, and a discard trigger that adds mana.
 */
class BroChunk14CostCounterTokenDamageDestroyDrawScenariosTest {
    private val contrition = Cards.invent("Crushing Contrition", "{4}{B}", "Instant", """
        This spell costs {1} less to cast for each creature card in your graveyard.
        Exile target creature or planeswalker.
    """)
    private val beam = Cards.invent("Scatter Beam", "{1}{U}", "Instant", "Counter target artifact or creature spell unless its controller pays {4}.")
    private val lieutenant = Cards.invent("Skyward Lieutenant", "{2}{U}", "Creature — Human Soldier", """
        Flying
        Whenever this creature attacks, create a 1/1 colorless Soldier artifact creature token.
        Tap three untapped Soldiers you control: Draw a card.
    """, "2/3")
    private val loran = Cards.invent("Loran of the Fourth Path", "{2}{W}", "Legendary Creature — Human Artificer", """
        Vigilance
        When Loran of the Fourth Path enters, destroy up to one target artifact or enchantment.
        {T}: You and target opponent each draw a card.
    """, "2/1")
    private val tinkerer = Cards.invent("Quarry Tinkerer", "{2}{R}", "Legendary Creature — Human Artificer", """
        Haste
        {1}, {T}, Discard a card: Draw a card.
        Whenever you discard one or more artifact cards, add {R}{R}. This ability triggers only once each turn.
    """, "2/1")
    private val sergeant = Cards.invent("Pike Sergeant", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val sentinel = Cards.invent("Rust Sentinel", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val guard = Cards.invent("Rampart Guard", "{1}{W}", "Creature — Human Soldier", "", "2/2")
    private val cog = Cards.invent("Brass Cog", "{1}", "Artifact", "")
    private val bruiser = Cards.invent("Red Bruiser", "{1}{R}", "Creature — Ogre", "", "3/3")
    private val golem = Cards.invent("Tin Golem", "{2}", "Artifact Creature — Golem", "", "2/2")
    private val bear = Cards.invent("Moor Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val cub = Cards.invent("Moor Cub", "{G}", "Creature — Bear", "", "1/1")

    // CR 601.2f: {4}{B} less {1} per creature card in the graveyard; three of them leave {1}{B}
    @Test
    fun contritionCostsLessPerCreatureCardInGraveyard() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(bear, cub, bruiser, cog)
        alice.hand(contrition)
        bob.battlefield(sergeant)
        alice.casts(contrition, target = sergeant)
        resolveAll()
        expect { bob.exile has sergeant }
    }

    // CR 601.2f: with one creature card in the graveyard the spell still costs {3}{B}
    @Test
    fun contritionNotPayableWithTooFewCreatureCards() = scenario {
        alice.battlefield("Swamp", "Swamp")
        alice.graveyard(bear, cog)
        alice.hand(contrition)
        bob.battlefield(sergeant)
        alice.casts(contrition, target = sergeant)
        expect { decision<Decision.ManaPayment>().suggestion.isEmpty() shouldBe true }
    }

    // CR 118.12: the controller of an artifact creature spell may pay {4} to keep it
    @Test
    fun beamCountersAnArtifactCreatureUnlessFourIsPaid() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(golem)
        alice.battlefield("Island", "Island")
        alice.hand(beam)
        turn(active = bob)
        bob.casts(golem)
        bob.passes()
        alice.casts(beam, target = golem)
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(false))
        resolveAll()
        expect { bob.graveyard has golem }
    }

    // CR 118.12: paying {4} keeps the spell
    @Test
    fun beamLetsTheSpellResolveWhenFourIsPaid() = scenario {
        bob.battlefield("Mountain", "Mountain", "Mountain", "Mountain", "Mountain", "Mountain")
        bob.hand(golem)
        alice.battlefield("Island", "Island")
        alice.hand(beam)
        turn(active = bob)
        bob.casts(golem)
        bob.passes()
        alice.casts(beam, target = golem)
        resolveAll()
        if (pending is Decision.May) bob.answers(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) playOut()
        resolveAll()
        expect { bob.battlefield has golem }
    }

    // Audit: the tap cost needs three untapped Soldiers you control
    @Test
    fun lieutenantTapsThreeUntappedSoldiersToDraw() = scenario {
        alice.battlefield(lieutenant, sergeant, sentinel)
        alice.library(bear)
        alice.activates(lieutenant, ability = 0, choosing = listOf(lieutenant, sergeant, sentinel))
        resolveAll()
        expect {
            alice.hand has bear
            sergeant.tapped shouldBe true
            lieutenant.tapped shouldBe true
        }
    }

    @Test
    fun lieutenantCantUseTappedOrOpposingSoldiers() = scenario {
        alice.battlefield(lieutenant, sergeant)
        alice.battlefield(sentinel, tapped = true)
        bob.battlefield(guard)
        alice.library(bear)
        assertFailsWith<IllegalArgumentException> { alice.activates(lieutenant, ability = 0) }
    }

    // CR 702.20b: vigilance, attacking doesn't cause it to tap; its tap ability stays available
    @Test
    fun loranAttacksWithoutTapping() = scenario {
        alice.battlefield(loran)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(loran)
        expect { loran.tapped shouldBe false }
    }

    // CR 603.2: discarding an artifact card adds {R}{R}; a non-artifact discard adds nothing
    @Test
    fun tinkererAddsManaForADiscardedArtifact() = scenario {
        alice.battlefield("Mountain", tinkerer)
        alice.hand(cog)
        alice.library(bear)
        alice.activates(tinkerer, ability = 0, choosing = listOf(cog))
        resolveAll()
        expect { alice.pool.size shouldBe 2 }
    }

    @Test
    fun tinkererAddsNothingForADiscardedCreature() = scenario {
        alice.battlefield("Mountain", tinkerer)
        alice.hand(cub)
        alice.library(bear)
        alice.activates(tinkerer, ability = 0, choosing = listOf(cub))
        resolveAll()
        expect { alice.pool.size shouldBe 0 }
    }
}
