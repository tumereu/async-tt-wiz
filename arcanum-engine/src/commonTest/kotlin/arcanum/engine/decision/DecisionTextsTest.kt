package arcanum.engine.decision

import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.input.Input
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Text for people (API review 3): decisions read as the printed text of the ability they are about, and changes made
 * by a spell's or ability's instructions say what made them, for history lines.
 */
class DecisionTextsTest {
    /** Warden's Reclamation cast from a graveyard: "you may copy this spell" is asked with its sentence of the card. */
    // CR 608.2d
    @Test
    fun aMayIsItsSentenceOfTheCard() = scenario {
        alice.graveyard("Warden's Reclamation", "Tinkered Mite")
        alice.castsWithoutCosts("Warden's Reclamation", target = "Tinkered Mite")
        resolve()
        assertEquals("If this spell was cast from a graveyard, you may copy this spell and may choose a new target for the copy.",
            decision<Decision.May>().text)
    }

    /** Stormforge Charm's modes are its three "• " lines. */
    // CR 700.2
    @Test
    fun modesAreTheirLines() = scenario {
        alice.battlefield("Island", "Mountain")
        alice.hand("Stormforge Charm")
        perform(Input.Cast(alice.id, card("Stormforge Charm")))
        assertEquals(listOf(
            "Counter target noncreature spell unless its controller pays {2}.",
            "Stormforge Charm deals 2 damage to target creature.",
            "Draw two cards, then discard two cards.",
        ), decision<Decision.Modes>().options.map { it.text })
    }

    /** "Unless its controller pays {2}": the payer is asked with the sentence of the mode. */
    // CR 118.12
    @Test
    fun aCostToPayOnResolutionIsItsSentence() = scenario {
        alice.battlefield("Island", "Island")
        alice.hand("Storm Spark", "Stormforge Charm")
        alice.castsWithoutCosts("Storm Spark", target = "bob")
        alice.castsWithoutCosts("Stormforge Charm", modes = listOf(0), target = "Storm Spark")
        resolve()
        val pay = decision<Decision.May>()
        assertEquals(alice.id, pay.player)
        assertEquals("Counter target noncreature spell unless its controller pays {2}.", pay.text)
        assertNotNull(pay.cost)
    }

    /** Two triggers of one source waiting together are told apart by text and origin (Crimson Mourner dying with
     * another creature: its two triggers are the same ability, so take Vivacity's two enters abilities). */
    // CR 603.3b
    @Test
    fun triggersToOrderHaveTheirTextAndOrigin() = scenario {
        alice.battlefield("Mountain", "Mountain", "Forest", "Forest", "Forest")
        alice.hand("Vivacity")
        alice.library("Forest", "Island")
        alice.casts("Vivacity", paying = listOf("Mountain", "Mountain#1", "Forest", "Forest#1", "Forest#2"))
        allPass()
        val order = decision<Decision.OrderTriggers>()
        assertEquals(setOf(
            "When this creature enters, if {R}{R} was spent to cast it, this creature deals 3 damage to any target.",
            "When this creature enters, if {G}{G} was spent to cast it, search your library for a land card, reveal it, put it into your hand, then shuffle. You gain 2 life.",
        ), order.triggers.map { it.text }.toSet())
        assertEquals(2, order.triggers.mapNotNull { it.origin }.distinct().size)
    }

    // ---- History

    /** Storm Spark at bob: the damage names its source, and bob's life change says Storm Spark made it. */
    // D33
    @Test
    fun lifeLostToASpellSaysWhichSpell() = scenario {
        alice.hand("Storm Spark")
        alice.castsWithoutCosts("Storm Spark", target = "bob")
        resolve()
        val life = applied.filterIsInstance<Change.Life>().single()
        assertEquals(17, life.after)
        assertEquals("Storm Spark", life.by?.name)
        assertEquals(alice.id, life.by?.controller)
        assertNotNull(life.by?.item)
    }

    /** Bone Splinters destroying Elvish Seer: the move is tagged destroyed and made by Bone Splinters; Bone Splinters'
     * own way to the graveyard after it resolves is the rules', with no maker. */
    // D33, CR 608.2n
    @Test
    fun aDestroyedCreatureSaysWhatDestroyedIt() = scenario {
        alice.hand("Bone Splinters")
        bob.battlefield("Elvish Seer")
        alice.castsWithoutCosts("Bone Splinters", target = "Elvish Seer")
        resolve()
        val moves = applied.filterIsInstance<Change.ZoneChange>()
        val destroyed = moves.single { z -> z.moves.any { Meaning.Destroyed in it.meanings } }
        assertEquals("Bone Splinters", destroyed.by?.name)
        val resolved = moves.single { z -> z.moves.any { Meaning.Resolved in it.meanings } }
        assertNull(resolved.by)
    }

    /** Mana Seep countering Storm Spark: the Stack item is removed as countered, by Mana Seep. */
    // D33, CR 701.6a
    @Test
    fun aCounteredSpellSaysWhatCounteredIt() = scenario {
        alice.hand("Storm Spark")
        bob.hand("Mana Seep")
        alice.castsWithoutCosts("Storm Spark", target = "bob")
        bob.castsWithoutCosts("Mana Seep", target = "Storm Spark")
        resolve()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        val removed = applied.filterIsInstance<Change.StackItemRemoved>().single { Meaning.Countered in it.meanings }
        assertEquals("Mana Seep", removed.by?.name)
        assertTrue(applied.filterIsInstance<Change.Life>().isEmpty())
    }
}
