package arcanum.engine.sets.rvr

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.may
import arcanum.engine.sets.blb.resolveAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ravnica Remastered fix step: general engine fixes found during the set's chunks, in play. */
class RvrFixesScenariosTest {
    private val dustsweepRite = Cards.invent("Dustsweep Rite", "{2}{W}{W}", "Instant",
        "Exile target artifact or enchantment. If you cast this spell during your main phase, you may exile up to one other target artifact or enchantment.")
    private val idol = Cards.invent("Dustwork Idol", "{2}", "Artifact", "")
    private val chalice = Cards.invent("Dustwork Chalice", "{2}", "Artifact", "")

    // CR 601.2c: the spell's text defines its number of targets as it is cast: in your main phase it has the second target
    @Test
    fun dustsweepRiteInYourMainPhaseExilesTwo() = scenario {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(dustsweepRite)
        bob.battlefield(idol, chalice)
        alice.casts(dustsweepRite, targets = listOf(idol, chalice))
        assertEquals(listOf(target(idol), target(chalice)), state.stackItems.values.single().targets.values.flatten())
        resolveAll()
        may(true)
        expect {
            bob.exile has idol
            bob.exile has chalice
        }
    }

    // CR 601.2c: cast outside your main phase, the spell doesn't have the second target at all (nothing else becomes its target)
    @Test
    fun dustsweepRiteOutsideYourMainPhaseHasOneTarget() = scenario(players = 3) {
        alice.battlefield("Plains", "Plains", "Plains", "Plains")
        alice.hand(dustsweepRite)
        bob.battlefield(idol)
        carol.battlefield(chalice)
        turn(number = 2, active = bob, step = TurnStep.PostcombatMain, priority = alice)
        alice.casts(dustsweepRite, target = idol)
        assertEquals(listOf(target(idol)), state.stackItems.values.single().targets.values.flatten())
        resolveAll()
        expect {
            bob.exile has idol
            carol.battlefield has chalice
        }
    }
}
