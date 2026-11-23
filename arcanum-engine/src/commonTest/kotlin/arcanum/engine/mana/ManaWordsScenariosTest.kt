package arcanum.engine.mana

import arcanum.api.CardDefinition
import arcanum.api.Layout
import arcanum.api.PredefinedTokens
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each mana in a pool carries the printed words of its riders from the ability that made it ([arcanum.engine.state.ManaUnit.words]), for
 * hosts to show what it may be spent on or gives: added by an activated mana ability, left over from a payment, by a keyword (firebending)
 * and by a predefined token (Powerstone).
 */
class ManaWordsScenariosTest {
    private val grove = Cards.invent("Gildleaf Grove", "{2}{G}", "Creature — Elf Druid",
        "{T}: Add three mana of any one color. Spend this mana only to cast creature spells.", "1/1")
    private val bear = Cards.invent("Fernhollow Cub", "{2}", "Creature — Bear", "", "2/2")
    private val duelist = Cards.invent("Cinder Duelist", "{1}{R}", "Creature — Human Warrior", "Firebending 1", "2/2")
    private val powerstone = Cards.define("Powerstone Stand-in", CardDefinition(Layout.Normal,
        listOf(PredefinedTokens.face("Powerstone")!!.copy(name = "Powerstone Stand-in"))))
    private val creaturesOnly = listOf("Spend this mana only to cast creature spells.")

    // CR 106.6: the restriction's words go with each mana the ability adds
    @Test
    fun anActivatedAbilitysManaCarriesItsWords() = scenario {
        alice.battlefield(grove)
        alice.activates(grove, color = arcanum.api.Color.Green)
        assertEquals(3, alice.pool.size)
        alice.pool.forEach { assertEquals(creaturesOnly, it.words); assertEquals(true, it.hasRiders) }
    }

    // CR 106.4, 106.6: mana a payment taps for and doesn't spend stays in the pool, its words with it
    @Test
    fun manaLeftOverFromAPaymentKeepsItsWords() = scenario {
        alice.battlefield(grove)
        alice.hand(bear)
        alice.casts(bear, paying = listOf(grove))
        assertEquals(1, alice.pool.size)
        assertEquals(creaturesOnly, alice.pool.single().words)
    }

    // CR 702.189a: firebending's mana isn't lost as steps and phases end until end of combat, in the words of its rule
    @Test
    fun firebendingsManaSaysItLastsUntilEndOfCombat() = scenario {
        alice.battlefield(duelist)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(duelist)
        while (state.stack.isNotEmpty() && pending == null) allPass()
        assertEquals(listOf("Until end of combat, you don't lose this mana as steps and phases end."), alice.pool.single().words)
    }

    // CR 111.10h: a Powerstone's mana says it can't be spent on a nonartifact spell
    @Test
    fun powerstoneManaSaysWhatItCantBeSpentOn() = scenario {
        alice.battlefield(powerstone)
        alice.activates(powerstone)
        assertEquals(listOf("This mana can't be spent to cast a nonartifact spell."), alice.pool.single().words)
    }

    // CR 106.4: plain mana carries no riders and no words
    @Test
    fun plainManaHasNoWords() = scenario {
        alice.battlefield("Forest")
        alice.activates("Forest")
        assertEquals(emptyList(), alice.pool.single().words)
        assertEquals(false, alice.pool.single().hasRiders)
    }
}
