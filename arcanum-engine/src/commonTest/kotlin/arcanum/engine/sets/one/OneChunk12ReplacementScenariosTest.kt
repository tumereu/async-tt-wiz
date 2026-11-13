package arcanum.engine.sets.one

import arcanum.api.CounterKind
import arcanum.api.Keyword
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.ltr.aliceUpkeep
import arcanum.engine.sets.ltr.tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phyrexia: All Will Be One chunk 12 in play: a creature that loses its controller the game when its last oil counter is gone
 * (CR 614.1c, 104.3e) and makes the controller of an opponent's dying creature lose life, last known information (CR 603.10a, 3 players);
 * twice the tokens (CR 614.1, 111.1); Sphere lands that enter tapped and sacrifice for a card (CR 614.1d, 701.21a).
 */
class OneChunk12ReplacementScenariosTest {
    private val archdemon = Cards.invent("Archdemon of the Sump", "{2}{B}{B}", "Creature — Phyrexian Demon", """
        Flying
        This creature enters with four oil counters on it.
        At the beginning of your upkeep, remove an oil counter from this creature. Then if it has no oil counters on it, you lose the game.
        Whenever a creature an opponent controls dies, its controller loses 2 life.
    """, "6/6")
    private val oil = CounterKind.Named("oil")
    private val bear = Cards.invent("Fenpaw Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val dart = Cards.invent("Cinder Dart", "{R}", "Instant", "Cinder Dart deals 3 damage to target creature.")
    private val snatch = Cards.invent("Brazen Snatch", "{R}", "Sorcery", "Gain control of target creature until end of turn.")
    private val dorkan = Cards.invent("Dorkan, Throng Sovereign", "{2}{W}{W}", "Legendary Creature — Phyrexian Horror", """
        If one or more tokens would be created under your control, twice that many of those tokens are created instead.
        {1}{W/P}{W/P}, Sacrifice two other artifacts and/or creatures: Put an indestructible counter on Dorkan. ({W/P} can be paid with either {W} or 2 life.)
    """, "4/4")
    private val muster = Cards.invent("Raise Recruits", "{1}{W}", "Sorcery", "Create two 1/1 white Soldier creature tokens.")
    private val wells = Cards.invent("The Murk Wells", null, "Land — Sphere", """
        This land enters tapped.
        {T}: Add {B}.
        {1}{B}, {T}, Sacrifice this land: Draw a card.
    """)
    private val hound = Cards.invent("Pit Hound", "{1}{B}", "Creature — Hound", "", "2/2")
    private val imp = Cards.invent("Scrap Imp", "{B}", "Creature — Imp", "", "1/1")

    // CR 614.1c: it enters with four oil counters
    @Test
    fun archdemonEntersWithFourOilCounters() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Swamp")
        alice.hand(archdemon)
        alice.casts(archdemon)
        resolveAll()
        expect {
            archdemon.zone shouldBe Zone.Battlefield
            archdemon.counters(oil) shouldBe 4
        }
    }

    // CR 608.2c: "Then if" is checked after the counter is removed: with two counters one is left and its controller stays in the game
    @Test
    fun archdemonUpkeepRemovesOneAndKeepsPlayingWhileCountersRemain() = scenario {
        alice.battlefield(archdemon, counters = listOf(oil to 2))
        aliceUpkeep()
        resolveAll()
        expect { archdemon.counters(oil) shouldBe 1 }
        assertFalse(state.player(alice.id).lost)
    }

    // CR 104.3e: removing the last counter makes its controller lose the game
    @Test
    fun archdemonUpkeepWithLastCounterLosesTheGame() = scenario(players = 3) {
        alice.battlefield(archdemon, counters = listOf(oil to 1))
        aliceUpkeep()
        resolveAll()
        assertTrue(state.player(alice.id).lost)
        assertFalse(state.player(bob.id).lost)
        assertFalse(state.player(carol.id).lost)
    }

    // 3 players: each opponent's creature that dies costs its own controller 2 life, and only that player
    @Test
    fun archdemonMakesOnlyTheDeadCreaturesControllerLoseLife() = scenario(players = 3) {
        alice.battlefield(archdemon, counters = listOf(oil to 4))
        carol.battlefield(bear)
        bob.battlefield("Mountain")
        bob.hand(dart)
        turn(number = 2, active = bob)
        bob.casts(dart, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            carol.life shouldBe 18
            bob.life shouldBe 20
            alice.life shouldBe 20
        }
    }

    // CR 603.10a, 608.2h: its controller as it last existed on the battlefield (bob stole it), not its owner (carol)
    @Test
    fun archdemonUsesTheControllerOfAStolenCreatureThatDies() = scenario(players = 3) {
        alice.battlefield(archdemon, counters = listOf(oil to 4))
        carol.battlefield(bear)
        bob.battlefield("Mountain", "Mountain")
        bob.hand(snatch, dart)
        turn(number = 2, active = bob)
        bob.casts(snatch, target = bear)
        resolveAll()
        bob.casts(dart, target = bear)
        resolveAll()
        expect {
            bear.zone shouldBe Zone.Graveyard
            carol.life shouldBe 20
            bob.life shouldBe 18
        }
    }

    // CR 614.1, 111.1: two tokens become four
    @Test
    fun dorkanDoublesItsControllersTokens() = scenario(players = 3) {
        alice.battlefield(dorkan, "Plains", "Plains")
        alice.hand(muster)
        alice.casts(muster)
        resolveAll()
        assertEquals(4, tokens("Soldier Token").size)
    }

    // CR 614.1: "under your control": an opponent's tokens are not doubled
    @Test
    fun dorkanLeavesAnOpponentsTokensAlone() = scenario(players = 3) {
        alice.battlefield(dorkan)
        bob.battlefield("Plains", "Plains")
        bob.hand(muster)
        turn(number = 2, active = bob)
        bob.casts(muster)
        resolveAll()
        assertEquals(2, tokens("Soldier Token").size)
    }

    // CR 602.2, 118.3, 122.1b: sacrificing two other creatures as a cost puts an indestructible counter on it
    @Test
    fun dorkanSacrificesTwoOthersForAnIndestructibleCounter() = scenario {
        alice.battlefield(dorkan, hound, imp, "Plains", "Plains", "Plains")
        alice.activates(dorkan, ability = 0, choosing = listOf(hound, imp))
        resolveAll()
        expect {
            hound.zone shouldBe Zone.Graveyard
            imp.zone shouldBe Zone.Graveyard
            dorkan.counters(CounterKind.Keyword(Keyword.Indestructible)) shouldBe 1
        }
    }

    // CR 614.1d: a land played enters tapped
    @Test
    fun sphereLandEntersTapped() = scenario {
        alice.hand(wells)
        alice.plays(wells)
        expect { wells.tapped shouldBe true }
    }

    // CR 701.21a, 602.2: tapped and sacrificed as costs, then a card is drawn
    @Test
    fun sphereLandSacrificesItselfToDraw() = scenario {
        alice.battlefield(wells, "Swamp", "Swamp")
        alice.library("Island")
        alice.activates(wells, ability = 1)
        resolveAll()
        expect {
            wells.zone shouldBe Zone.Graveyard
            alice.hand has "Island"
        }
    }
}
