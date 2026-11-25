package org.asyncttwiz.android

import org.asyncttwiz.automatum.CombatChoices
import org.asyncttwiz.automatum.CombatCostShown
import org.asyncttwiz.automatum.CombatShown
import org.asyncttwiz.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CMB-098, CMB-075: when the main button's declaration opens the costs panel, and the panel's total. */
class CombatCostsTest {
    private val alice = "alice"
    private val exert = CombatCostShown("Exert", "You may exert this creature as it attacks.", "exert", "cub")
    private val two = CombatCostShown("{2}", "Creatures can't attack you unless their controller pays {2} for each creature they control that's attacking you.", source = "prison")
    private val life = CombatCostShown("2 life", "Creatures can't block unless their controller pays 2 life for each blocking creature they control.", source = "thorns")

    private fun board(attackers: List<String> = emptyList(), blockers: List<String> = emptyList()) =
        BoardState(emptyList(), emptyList(), emptyList(), TurnState(alice, "3", "COMBAT", "DECLARE_ATTACKERS"),
            combat = CombatArea("combat", listOf(CombatPair("pair", attackers.map { CombatOccurrence("a-$it", it) },
                blockers.map { CombatOccurrence("b-$it", it) }))))

    private fun attacking(optional: Map<String, List<CombatCostShown>> = emptyMap(), costs: Map<String, List<CombatCostShown>> = emptyMap()) =
        CombatShown(CombatChoices(alice, attackers = true, optional = optional), costs = costs)

    // CMB-098 (CR 508.1g): an optional cost a staged attacker may pay opens the panel, with Autotap on too; a creature with one
    // that isn't staged doesn't
    @Test fun anOptionalCostToAttackOpensThePanel() {
        val shown = attacking(optional = mapOf("cub" to listOf(exert)))
        assertTrue(CombatCosts.toDecide(board(listOf("cub")), shown, autotap = true))
        assertTrue(CombatCosts.toDecide(board(listOf("cub")), shown, autotap = false))
        assertFalse(CombatCosts.toDecide(board(listOf("bear")), shown, autotap = true), "the creature with exert isn't staged")
    }

    // CMB-075, CMB-098 (CR 508.1h): a mana cost the creature must pay opens the panel only with Autotap off; with it on there is
    // nothing to decide and the main button declares, Autotap paying
    @Test fun aRequiredManaCostOpensThePanelOnlyWithAutotapOff() {
        val shown = attacking(costs = mapOf("bear" to listOf(two)))
        assertTrue(CombatCosts.toDecide(board(listOf("bear")), shown, autotap = false))
        assertFalse(CombatCosts.toDecide(board(listOf("bear")), shown, autotap = true))
        // A cost without mana leaves nothing to pay by hand
        assertFalse(CombatCosts.toDecide(board(listOf("bear")), attacking(costs = mapOf("bear" to listOf(life))), autotap = false))
    }

    // CMB-098 (CR 509.1d): a block's mana cost opens the panel with Autotap off; nothing is declared while none is awaited
    @Test fun aBlockCostOpensThePanelWithAutotapOff() {
        val blocking = CombatShown(CombatChoices("bob", attackers = false), costs = mapOf("ogre" to listOf(two.copy(label = "{1}"))))
        assertTrue(CombatCosts.toDecide(board(listOf("bear"), listOf("ogre")), blocking, autotap = false))
        assertFalse(CombatCosts.toDecide(board(listOf("bear"), listOf("ogre")), blocking, autotap = true))
        assertFalse(CombatCosts.toDecide(board(listOf("cub")), CombatShown.NONE, autotap = false), "no declaration awaited")
    }

    // CMB-098: "Total to pay" is the declaration's mana as one total, then the other parts in order; the costs' own mana where the
    // payment isn't worked out
    @Test fun theTotalIsTheManaThenTheOtherCosts() {
        assertEquals("{4}, 2 life, Exert", CombatCosts.total("{4}", listOf(two, two, life, exert)))
        assertEquals("{2}, {2}", CombatCosts.total(null, listOf(two, two)))
        assertEquals("{2}{R}, Sacrifice", CombatCosts.total("{2}{R}", listOf(CombatCostShown("{R}, Sacrifice", null), two)))
        assertNull(CombatCosts.total(null, emptyList()))
        assertTrue(CombatCosts.asksMana(CombatCostShown("{1}, 2 life", null)))
        assertFalse(CombatCosts.asksMana(life))
    }
}
