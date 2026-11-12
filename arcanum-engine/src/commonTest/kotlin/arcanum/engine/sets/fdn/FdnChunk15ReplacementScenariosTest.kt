package arcanum.engine.sets.fdn

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

/** Chunk 15 played: the invented names carry the oracle text of the Gates, the gain lands, Uncharted Haven, Maze's End, Prime Speaker Zegana and Wishclaw Talisman. */
class FdnChunk15ReplacementScenariosTest {
    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    private val p1p1 = CounterKind.PowerToughness(1, 1)
    private val gate = Cards.invent("Ashen Gate", null, "Land — Gate", "This land enters tapped.\n{T}: Add {W} or {B}.")
    private val gainLand = Cards.invent("Scoured Basin", null, "Land", """
        This land enters tapped.
        When this land enters, you gain 1 life.
        {T}: Add {W} or {B}.
    """)
    private val harbor = Cards.invent("Uncharted Harbor", null, "Land", """
        This land enters tapped. As it enters, choose a color.
        {T}: Add one mana of the chosen color.
    """)
    private val maze = Cards.invent("Winding Maze", null, "Land", """
        This land enters tapped.
        {T}: Add {C}.
        {3}, {T}, Return this land to its owner's hand: Search your library for a Gate card, put it onto the battlefield, then shuffle. If you control ten or more Gates with different names, you win the game.
    """)
    private val gates = listOf("Azure", "Bronze", "Cinder", "Dusk", "Ember", "Frost", "Gale", "Hollow", "Iron", "Jade").map {
        Cards.invent("$it Gate", null, "Land — Gate", "This land enters tapped.\n{T}: Add {W} or {U}.")
    }
    private val zegana = Cards.invent("Prime Speaker Zagana", "{2}{G}{G}{U}{U}", "Legendary Creature — Merfolk Wizard", """
        Prime Speaker Zagana enters with X +1/+1 counters on it, where X is the greatest power among other creatures you control.
        When Prime Speaker Zagana enters, draw cards equal to its power.
    """, "1/1")
    private val ogre = Cards.invent("Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val bear = Cards.invent("Dell Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val talisman = Cards.invent("Wishhook Talisman", "{1}{B}", "Artifact", """
        This artifact enters with three wish counters on it.
        {1}, {T}, Remove a wish counter from this artifact: Search your library for a card, put it into your hand, then shuffle. An opponent gains control of this artifact. Activate only during your turn.
    """)
    private val prize = Cards.invent("Hidden Prize", "{2}", "Artifact", "")

    // CR 614.1c, 305.1
    @Test
    fun gateEntersTapped() = scenario {
        alice.hand(gate)
        alice.plays(gate)
        expect { card(gate).tapped shouldBe true }
    }

    // CR 614.1c, 603.6a
    @Test
    fun gainLandEntersTappedAndGainsLife() = scenario {
        alice.hand(gainLand)
        alice.plays(gainLand)
        resolveAll()
        expect {
            card(gainLand).tapped shouldBe true
            alice.life shouldBe 21
        }
    }

    // CR 614.1c, 614.12: tapped, and a colour is asked as it enters
    @Test
    fun harborEntersTappedAfterAColorChoice() = scenario {
        alice.hand(harbor)
        alice.plays(harbor)
        resolveAll()
        if (pending is Decision.ChooseColor) answer(Answer.ColorChoice(Color.Green))
        expect { card(harbor).tapped shouldBe true }
    }

    // CR 201.2, 104.2b: nine Gates plus the one fetched make ten different names
    @Test
    fun mazeWinsWithTenGates() = scenario {
        alice.battlefield(maze, *gates.take(9).toTypedArray())
        alice.library(gates[9])
        alice.activates(maze, ability = 1, paying = gates.take(3))
        allPass()
        if (pending is Decision.ChooseObjects) alice.chooses(gates[9])
        assertTrue(state.player(bob.id).lost)
    }

    // CR 614.1c, 201.2: nine Gates only, no win
    @Test
    fun mazeDoesNotWinWithNine() = scenario {
        alice.battlefield(maze, *gates.take(8).toTypedArray())
        alice.library(gates[8])
        alice.activates(maze, ability = 1, paying = gates.take(3))
        allPass()
        if (pending is Decision.ChooseObjects) alice.chooses(gates[8])
        assertTrue(!state.player(bob.id).lost)
    }

    // CR 614.1c: counters for the greatest power among the others (3), then draws for its power (1 + 3)
    @Test
    fun zeganaEntersByTheGreatestPowerAndDraws() = scenario {
        alice.battlefield("Forest", "Forest", "Island", "Island", "Island", "Island", ogre, bear)
        alice.hand(zegana)
        alice.library(bear, ogre, bear, ogre, bear)
        alice.casts(zegana)
        resolveAll()
        expect {
            card(zegana).counters(p1p1) shouldBe 3
            zegana.power shouldBe 4
            alice.hand.size shouldBe 4
        }
    }

    // CR 614.1c, 122.1, 701.23, 108.3, 602.5b: tutor, then the opponent takes the artifact
    @Test
    fun talismanTutorsAndChangesHands() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp")
        alice.hand(talisman)
        alice.library(prize)
        alice.casts(talisman, paying = listOf("Swamp", "Swamp#1"))
        allPass()
        expect { card(talisman).counters(CounterKind.Named("wish")) shouldBe 3 }
        alice.activates(talisman, paying = listOf("Swamp#2"))
        allPass()
        if (pending is Decision.ChooseObjects) alice.chooses(prize)
        expect {
            alice.hand has prize
            card(talisman).counters(CounterKind.Named("wish")) shouldBe 2
            card(talisman).controller shouldBe bob.id
        }
    }
}
