package arcanum.engine.review

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Review 1: playing cards from other zones (CR 601.3, 305.1, D41) and what happens to them afterwards. */
class Review1PermissionsTest {
    private fun Scenario.settleStack() {
        var guard = 30
        while ((state.stack.isNotEmpty() || pending != null) && guard-- > 0) {
            when (val d = pending) {
                is Decision.Targets -> answer(Answer.Targets(d.options.take(d.min.coerceAtLeast(1))))
                is Decision.May -> answer(Answer.YesNo(true))
                is Decision.OrderTriggers -> answer(Answer.Order(d.triggers.indices.toList()))
                null -> allPass()
                else -> return
            }
        }
    }

    /** A land played from exile with a "you may play it this turn" permission uses the turn's land play. */
    // CR 305.2, 305.1
    @Test
    fun landFromExileUsesTheLandPlay() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand("Expressive Variation", "Forest")
        alice.library("Plains", "Swamp", "Island")
        alice.plays("Forest")
        alice.casts("Expressive Variation")
        allPass()
        var guard = 5
        while (pending is Decision.ChooseObjects && guard-- > 0) {
            val d = decision<Decision.ChooseObjects>()
            answer(Answer.Objects(listOf(d.options.first())))
        }
        settleStack()
        val exiled = state.exile.single()
        assertFailsWith<IllegalArgumentException> { perform(Input.PlayLand(alice.id, exiled)) }
    }

    /** Necromancer's Will: a spell cast from the graveyard that turn is exiled as it resolves instead of returning. */
    // CR 614.1a, 601.3
    @Test
    fun spellFromTheGraveyardIsExiledAfter() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", "Mountain")
        alice.hand("Necromancer's Will")
        alice.graveyard("Storm Spark")
        alice.casts("Necromancer's Will", paying = listOf("Swamp", "Swamp#1", "Swamp#2"))
        settleStack()
        alice.casts("Storm Spark", target = "bob")
        settleStack()
        expect {
            bob.life shouldBe 17
            alice.exile has "Storm Spark"
        }
    }

    /** "Until end of turn, you may play that card": the permission is gone on the next turn. */
    // CR 611.2b, 514.2
    @Test
    fun permissionEndsWithTheTurn() = scenario {
        alice.battlefield("Prototype Replicator", "Mountain", "Mountain", "Mountain")
        alice.library("Island", "Island", "Island", "Island")
        bob.library("Island", "Island")
        alice.activates("Prototype Replicator")
        settleStack()
        val exiled = state.exile.firstOrNull { state.obj(it)?.owner == alice.id } ?: kotlin.test.fail("Nothing exiled")
        advanceTo(TurnStep.Upkeep, turn = 3)
        advanceTo(TurnStep.PrecombatMain, turn = 3)
        assertTrue(Engine.options(state, alice.id).none { it is Input.PlayLand && it.card == exiled })
    }

    /** Casting a creature from the top of the library (coven) reveals nothing else and the next card can be cast after. */
    // CR 601.3, 401.5
    @Test
    fun castFromTheTopTwice() = scenario {
        alice.battlefield("Seer of Harvestide", "Elvish Seer", "Duskflame Phoenix", "Forest", "Forest")
        alice.library("Thicketwood Elves", "Umbral Elves")
        alice.casts("Thicketwood Elves", paying = listOf("Forest"))
        allPass()
        alice.casts("Umbral Elves", paying = listOf("Forest#1"))
        allPass()
        expect {
            alice.battlefield has "Thicketwood Elves"
            alice.battlefield has "Umbral Elves"
        }
    }

    /** An Adventure sent on its adventure goes to exile, and the creature can be cast from there; a creature from exile
     * that dies goes to the graveyard normally. */
    // CR 715.4
    @Test
    fun adventureThenCreature() = scenario {
        alice.battlefield("Island", "Island", "Island", "Island", "Island")
        alice.hand("Brash Pilferer")
        bob.battlefield("Sun Band")
        val back = arcanum.engine.input.CastWay(face = 1)
        alice.casts("Brash Pilferer", way = back, target = "Sun Band", paying = listOf("Island", "Island#1"))
        allPass()
        expect {
            alice.exile has "Brash Pilferer // Petty Pinch"
            bob.hand has "Sun Band"
        }
        alice.casts("Brash Pilferer", paying = listOf("Island#2", "Island#3", "Island#4"))
        allPass()
        expect { alice.battlefield has "Brash Pilferer // Petty Pinch" }
    }

    /** Flashback granted until end of turn by Echoflash Adept: cast for its mana cost, then exiled. */
    // CR 702.34a, 611.2c
    @Test
    fun grantedFlashback() = scenario {
        alice.battlefield("Island", "Island", "Mountain")
        alice.hand("Echoflash Adept")
        alice.graveyard("Storm Spark")
        alice.casts("Echoflash Adept", paying = listOf("Island", "Island#1"))
        allPass()
        settleStack()
        alice.casts("Storm Spark", target = "bob", paying = listOf("Mountain"))
        allPass()
        expect {
            bob.life shouldBe 17
            alice.exile has "Storm Spark"
        }
    }
}
