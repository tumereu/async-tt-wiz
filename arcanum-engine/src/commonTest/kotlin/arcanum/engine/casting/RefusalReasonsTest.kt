package arcanum.engine.casting

import arcanum.api.Ability
import arcanum.engine.CastScreen
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Why an activation isn't offered, in the engine's words, for a host to grey it out with ([CastScreen.refusal] at once,
 * [CastScreen.leftOut] once begun on a copy): the specific reason, never a list of what might stop it (prod faults 2VLVQG,
 * NNJWE3, S2AJML, 8M35M6, WCT9GA, HMZSXN, T3CN7C, 2026-11-26).
 */
class RefusalReasonsTest {
    private val druid = Cards.invent("Reason Druid", "{1}{G}", "Creature — Elf Druid", "{T}: Add {G}.", "2/2")
    private val spinner = Cards.invent("Reason Spinner", "{3}{G}", "Creature — Spider",
        "{2}{G}: Put a +1/+1 counter on target creature you control. Activate only as a sorcery.", "2/4")
    private val snare = Cards.invent("Reason Snare", "{2}", "Artifact", "{T}: Tap target creature an opponent controls.")
    private val blade = Cards.invent("Reason Blade", "{1}{R}", "Artifact — Equipment",
        "Equipped creature gets +3/+0.\nDisguise {2}{R}\nEquip {1}{R}")

    private fun Scenario.ability(name: String, special: Boolean = false): Input.Activate {
        val id = card(name)
        val instance = Engine.view(state)[id]!!.characteristics!!.abilities.first {
            if (special) it.ability is Ability.SpecialAction else it.ability is Ability.Activated
        }
        return Input.Activate(alice.id, id, instance.origin)
    }

    private fun Scenario.listed(input: Input) = input in Engine.options(state, alice.id)

    // CR 107.5: a tapped permanent's {T} ability says it is tapped
    @Test fun aTappedPermanentsTapAbilitySaysItIsTapped() = scenario {
        alice.battlefield(druid, tapped = true)
        val reason = CastScreen.refusal(state, ability(druid))
        assertTrue(reason != null && "it's tapped (CR 107.5)" in reason, "$reason")
    }

    // CR 302.6, 602.5a: a creature that came under its controller's control this turn says summoning sickness
    @Test fun aSummoningSickCreaturesTapAbilitySaysSo() = scenario {
        alice.battlefield(druid, sick = true)
        val reason = CastScreen.refusal(state, ability(druid))
        assertTrue(reason != null && "summoning sickness" in reason && "CR 302.6" in reason, "$reason")
    }

    // CR 601.2g, 601.2h, 602.2b: an ability that can be begun but whose mana cost no payment may meet is left out for want of
    // mana; with the mana it is listed and nothing is said
    @Test fun anAbilityWithoutTheManaForItSaysNotEnoughMana() = scenario {
        alice.battlefield(spinner, "Forest", "Forest")
        alice.battlefield("Forest", tapped = true)
        val input = ability(spinner)
        assertNull(CastScreen.refusal(state, input), "it may be begun")
        assertEquals(false, listed(input))
        assertEquals(CastScreen.MANA_UNPAYABLE, CastScreen.leftOut(state, input))
        assertTrue(CastScreen.leftOut(state, input)!!.startsWith("Not enough mana"))
    }

    @Test fun anAbilityWithTheManaForItIsListedAndLeftOutSaysNothing() = scenario {
        alice.battlefield(spinner, "Forest", "Forest", "Forest")
        val input = ability(spinner)
        assertEquals(true, listed(input))
        assertNull(CastScreen.leftOut(state, input))
    }

    // CR 601.2c, 602.2b: without a legal target, the ability says so
    @Test fun anAbilityWithoutALegalTargetSaysSo() = scenario {
        alice.battlefield(snare)
        val input = ability(snare)
        assertEquals(false, listed(input))
        val reason = CastScreen.leftOut(state, input)
        assertTrue(reason != null && reason.startsWith("Not enough legal targets"), "$reason")
    }

    // CR 702.168d: only a face-down permanent is turned face up; a face-up one's disguise action says it isn't face down
    @Test fun aFaceUpPermanentsTurnFaceUpSaysItIsNotFaceDown() = scenario {
        alice.battlefield(blade, "Mountain", "Mountain", "Mountain")
        val input = ability(blade, special = true)
        val reason = CastScreen.refusal(state, input)
        assertTrue(reason != null && "it isn't face down" in reason, "$reason")
    }

    // Any other input has no such reason
    @Test fun onlyCastsAndActivationsHaveOne() = scenario {
        alice.battlefield(druid)
        assertNull(CastScreen.leftOut(state, Input.PassPriority(alice.id)))
        assertNull(CastScreen.leftOut(state, ability(druid)), "a usable mana ability")
    }
}
