package arcanum.engine.sets.isd

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.rtr.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ISD chunk 09: replacement effects (enters tapped, enters with X counters, win instead of drawing, doubled tokens). Invented names
 * carry the oracle text of the real cards. */
class IsdChunk09ReplacementScenariosTest {
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private val ghoul = Cards.invent("Diregrave Ghoul", "{B}", "Creature — Zombie", "This creature enters tapped.", "2/2")
    private val grimgrave = Cards.invent("Grimgrave, Corpse-Risen", "{3}{U}{B}", "Legendary Creature — Zombie Warrior", """
        Grimgrave enters tapped and doesn't untap during your untap step.
        Sacrifice another creature: Untap Grimgrave and put a +1/+1 counter on it.
        Whenever Grimgrave attacks, destroy target creature defending player controls, then put a +1/+1 counter on Grimgrave.
    """.trimIndent(), "5/5")
    private val maniac = Cards.invent("Archive Maniac", "{2}{U}", "Creature — Human Wizard",
        "If you would draw a card while your library has no cards in it, you win the game instead.", "2/2")
    private val mikael = Cards.invent("Mikael, the Moonward", "{X}{W}", "Legendary Creature — Human Cleric", """
        Mikael enters with X +1/+1 counters on it.
        {T}: Put a +1/+1 counter on Mikael.
        {T}, Remove a +1/+1 counter from Mikael: Put a +1/+1 counter on each other creature you control.
    """.trimIndent(), "0/0")
    private val doubled = Cards.invent("Doubled Lives", "{3}{G}", "Enchantment",
        "If an effect would create one or more tokens under your control, it creates twice that many of those tokens instead.")
    private val muster = Cards.invent("Muster Call", "{1}{W}", "Sorcery", "Create a 1/1 white Soldier creature token.")
    private val ponder = Cards.invent("Quick Glance", "{U}", "Sorcery", "Draw a card.")
    private val ponder2 = Cards.invent("Quick Glimpse", "{U}", "Sorcery", "Draw a card.")
    private val ox = Cards.invent("Moonward Ox", "{3}", "Creature — Ox", "", "4/4")
    private val bear = Cards.invent("Moonward Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    private fun Scenario.soldiers() = state.battlefield.count { state.obj(it)?.let { o -> nameOf(o) } == "Soldier Token" }

    // CR 614.1c: enters tapped
    @Test
    fun ghoulEntersTapped() = scenario {
        alice.battlefield("Swamp")
        alice.hand(ghoul)
        alice.casts(ghoul)
        resolveAll()
        expect { ghoul.tapped shouldBe true }
    }

    // CR 614.1c, 602.1 (sacrifice as a cost), 701.21 (sacrifice): enters tapped, then is untapped by sacrificing another creature
    @Test
    fun grimgraveEntersTappedAndUntapsBySacrifice() = scenario {
        alice.battlefield("Island", "Island", "Swamp", "Swamp", "Swamp", ox)
        alice.hand(grimgrave)
        alice.casts(grimgrave)
        resolveAll()
        expect { grimgrave.tapped shouldBe true }
        alice.activates(grimgrave)
        resolveAll()
        expect {
            alice.graveyard has ox
            grimgrave.tapped shouldBe false
            grimgrave.counters(p1p1) shouldBe 1
        }
    }

    // CR 502.3: it stays tapped through its controller's untap step
    @Test
    fun grimgraveStaysTappedThroughTheUntapStep() = scenario {
        alice.battlefield(grimgrave, tapped = true)
        alice.library("Island", "Island")
        bob.library("Island", "Island")
        advanceTo(TurnStep.Upkeep, turn = 3)
        expect { grimgrave.tapped shouldBe true }
    }

    // CR 508.1, 603.2: the attack trigger destroys a creature the defending player controls, then Grimgrave grows
    @Test
    fun grimgraveAttackDestroysADefenderCreatureAndGrows() = scenario(players = 3) {
        alice.battlefield(grimgrave)
        bob.battlefield(ox)
        players[2].battlefield(bear)
        turn(step = TurnStep.DeclareAttackers)
        alice.attacks(grimgrave, defender = "bob")
        resolveAll()
        expect {
            bob.graveyard has ox
            players[2].battlefield has bear
            grimgrave.counters(p1p1) shouldBe 1
        }
    }

    // CR 614.1a, 104.2a: the draw is replaced while the library is empty, and the player wins; every opponent loses (3 players)
    @Test
    fun maniacWinsInsteadOfDrawingFromAnEmptyLibrary() = scenario(players = 3) {
        alice.battlefield(maniac, "Island", "Island")
        alice.hand(ponder, ponder2)
        alice.library("Plains")
        bob.library("Island")
        players[2].library("Island")
        alice.casts(ponder)
        resolveAll()
        expect { alice.hand has "Plains" }
        assertEquals(false, state.player(alice.id).lost)
        alice.casts(ponder2)
        resolveAll()
        assertTrue(state.player(bob.id).lost, "Bob loses when Alice wins")
        assertTrue(state.player(players[2].id).lost, "the third player loses too")
        assertEquals(false, state.player(alice.id).lost)
    }

    // CR 614.1c, 107.3: enters with X counters; CR 602.2: its tap abilities
    @Test
    fun mikaelEntersWithXCountersAndGrowsItself() = scenario {
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(mikael)
        alice.casts(mikael, x = 2)
        resolveAll()
        expect { mikael.counters(p1p1) shouldBe 2 }
    }

    @Test
    fun mikaelPutsACounterOnEachOtherCreatureYouControl() = scenario(players = 3) {
        alice.battlefield(mikael, counters = listOf(p1p1 to 2))
        alice.battlefield(ox)
        bob.battlefield(bear)
        alice.activates(mikael, ability = 1)
        resolveAll()
        expect {
            mikael.counters(p1p1) shouldBe 1
            ox.counters(p1p1) shouldBe 1
            bear.counters(p1p1) shouldBe 0
        }
    }

    // CR 614.1a, 111.1: the effect creating tokens under your control makes twice as many
    @Test
    fun parallelLivesDoublesYourTokens() = scenario(players = 3) {
        alice.battlefield(doubled, "Plains", "Plains")
        alice.hand(muster)
        alice.casts(muster)
        resolveAll()
        assertEquals(2, soldiers())
    }

    // an opponent's tokens aren't "under your control", so they aren't doubled
    @Test
    fun parallelLivesDoesNotDoubleAnOpponentsTokens() = scenario(players = 3) {
        alice.battlefield(doubled)
        bob.battlefield("Plains", "Plains")
        bob.hand(muster)
        turn(active = bob, priority = bob)
        bob.casts(muster)
        resolveAll()
        assertEquals(1, soldiers())
    }
}
