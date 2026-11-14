package arcanum.engine.sets.rvr

import arcanum.api.Zone
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.setup.GameResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ravnica Remastered chunk 11 in play: spectacle with three players and a drawing enters trigger, a scry in the upkeep and
 * a sorcery-speed exchange of control, an upkeep win from twenty artifacts, a cast trigger that pumps and then scries.
 * Scavenge, transmute, unleash and graft are tested in other sets; the other cards are plain pumps and keyword grants. */
class RvrChunk11SpectacleScryWinScenariosTest {
    private val spark = Cards.invent("Rvr11 Spark", "{R}", "Sorcery", "Rvr11 Spark deals 1 damage to any target.")
    private val zap = Cards.invent("Rvr11 Zap", "{R}", "Sorcery", "Rvr11 Zap deals 1 damage to any target.")
    private val skewer = Cards.invent("Rvr11 Skewer the Hecklers", "{2}{R}", "Sorcery", """
        Spectacle {R} (You may cast this spell for its spectacle cost rather than its mana cost if an opponent lost life this turn.)
        Rvr11 Skewer the Hecklers deals 3 damage to any target.
    """)
    private val juggler = Cards.invent("Rvr11 Blade Tumbler", "{4}{B}", "Creature — Human Rogue", """
        Spectacle {2}{B} (You may cast this spell for its spectacle cost rather than its mana cost if an opponent lost life this turn.)
        When this creature enters, it deals 1 damage to you and you draw a card.
    """, "3/2")
    private val gaze = Cards.invent("Rvr11 Gaze Everywhere", "{2}{U}", "Enchantment", """
        At the beginning of your upkeep, scry 1.
        {5}{U}: Exchange control of this enchantment and target nonland permanent. Activate only as a sorcery.
    """)
    private val magnate = Cards.invent("Rvr11 Hellkite Magnate", "{4}{R}{R}", "Creature — Dragon", """
        Flying, trample
        Whenever this creature deals combat damage to a player, gain control of all artifacts that player controls.
        At the beginning of your upkeep, if you control twenty or more artifacts, you win the game.
    """, "6/5")
    private val trinket = Cards.invent("Rvr11 Trinket", "{1}", "Artifact", "")
    private val seer = Cards.invent("Rvr11 Burning Seer", "{1}{R}", "Creature — Human Wizard", """
        Whenever you cast a noncreature spell, this creature gets +1/+0 until end of turn, then scry 1.
    """, "1/3")
    private val bear = Cards.invent("Rvr11 Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    private fun Scenario.spectacleWay(name: String): CastWay? =
        Engine.options(state, alice.id).filterIsInstance<Input.Cast>().firstOrNull { it.card == card(name) && it.way.alternative != null }?.way

    /** An opponent losing life enables the cheap cost; with three players it need not be the one the spell targets, and
     * losing life yourself doesn't count. */
    // CR 702.137a
    @Test
    fun spectacleNeedsAnOpponentToHaveLostLife() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(spark, zap, skewer)
        assertEquals(null, spectacleWay(skewer))
        alice.casts(spark, target = "alice")
        resolve()
        assertEquals(null, spectacleWay(skewer))
        alice.casts(zap, target = "carol")
        resolve()
        val way = spectacleWay(skewer)
        assertTrue(way != null, "carol lost life")
        alice.casts(skewer, target = "bob", way = way)
        resolve()
        expect {
            bob.life shouldBe 17
            carol.life shouldBe 19
            alice.life shouldBe 19
            alice.graveyard has skewer
        }
    }

    // CR 702.137a, 120.3 (the enters trigger damages its controller), 121.1
    @Test
    fun spectacleCreatureHurtsItsControllerAndDraws() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand(spark, juggler)
        alice.library(bear)
        alice.casts(spark, target = "bob")
        resolve()
        alice.casts(juggler, way = spectacleWay(juggler)!!)
        resolve()
        resolve()
        expect {
            alice.life shouldBe 19
            alice.hand has bear
            alice.battlefield has juggler
        }
    }

    // CR 701.22a (scry 1: put the top card on the bottom)
    @Test
    fun upkeepScryCanBottomTheTopCard() = scenario {
        alice.battlefield(gaze)
        alice.library("Forest", "Island", "Swamp")
        beginStep(TurnStep.Upkeep)
        resolve()
        val d = decision<Decision.ChooseObjects>()
        answer(Answer.Objects(d.options.take(1)))
        assertEquals(listOf("Island", "Swamp", "Forest"), alice.library.names)
    }

    // CR 701.12a, 307.5: the exchange is sorcery speed, and each player controls the other's permanent afterwards
    @Test
    fun exchangeControlOnlyAtSorcerySpeed() = scenario {
        alice.battlefield(gaze, "Island", "Island", "Island", "Island", "Island", "Island")
        bob.battlefield(trinket)
        turn(active = bob)
        assertTrue(Engine.options(state, alice.id).none { it is Input.Activate }, "not on bob's turn")
        turn(active = alice)
        alice.activates(gaze, target = trinket)
        resolve()
        expect {
            alice.battlefield has trinket
            bob.battlefield has gaze
        }
    }

    // CR 104.2b, 603.4: twenty artifacts at upkeep wins, nineteen doesn't
    @Test
    fun twentyArtifactsWinAtUpkeep() = scenario {
        alice.battlefield(magnate, *Array(20) { trinket })
        beginStep(TurnStep.Upkeep)
        resolve()
        assertEquals(GameResult.Won(alice.id), Engine.result(state))
    }

    @Test
    fun nineteenArtifactsDoNotWin() = scenario {
        alice.battlefield(magnate, *Array(19) { trinket })
        beginStep(TurnStep.Upkeep)
        assertEquals(null, Engine.result(state))
    }

    // CR 603.2, 701.22a: the pump comes before the scry, and creature spells don't trigger it
    @Test
    fun noncreatureSpellPumpsThenScries() = scenario {
        alice.battlefield(seer, "Mountain", "Mountain", "Forest", "Forest")
        alice.hand(spark, bear)
        alice.library("Island", "Swamp")
        alice.casts(bear)
        resolve()
        expect { seer.power shouldBe 1 }
        alice.casts(spark, target = "bob")
        resolve()
        expect { seer.power shouldBe 2 }
        val d = decision<Decision.ChooseObjects>()
        answer(Answer.Objects(d.options.take(1)))
        assertEquals(listOf("Swamp", "Island"), alice.library.names)
    }
}
