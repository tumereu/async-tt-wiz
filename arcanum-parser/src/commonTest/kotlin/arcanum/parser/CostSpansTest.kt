package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Cost
import arcanum.api.HasCost
import arcanum.api.Mechanic
import arcanum.parser.cube.card
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where each cost part's phrase sits in its printed text ([arcanum.api.Cost.spans]), for hosts that show which part a choice
 * is for and what was chosen for it: an activated ability's cost in its text, a keyword's in its line, an additional or
 * alternative cost in its static ability's sentence. Each test lists the phrases of the card's costs in order. Every parsing
 * test also checks all cost spans it parses (`checkCostSpans`).
 */
class CostSpansTest {
    private fun phrases(type: String, text: String, cost: String? = "{1}", pt: String? = null, loyalty: String? = null): List<String?> {
        val printed = card(name = "Ember Thing", cost = cost, type = type, text = text, pt = pt, loyalty = loyalty)
        val face = OracleParser.parse(printed)
        val lines = printed.text.lines().map { Regex("""\s*\([^)]*\)""").replace(it, "").trim() }
        fun of(cost: Cost, text: String?): List<String?> = cost.parts.indices.map { i -> cost.span(i)?.let { s -> text?.let { s.of(it) } } }
        return face.abilities.flatMap { ability ->
            when (ability) {
                is Ability.Activated -> of(ability.cost, ability.text)
                is Ability.Static -> ability.mechanics.flatMap { m ->
                    when (m) {
                        is Mechanic.AdditionalCost -> of(m.cost, ability.text)
                        is Mechanic.AlternativeCost -> of(m.cost, ability.text)
                        else -> emptyList()
                    }
                }
                is arcanum.api.Keyword -> (ability as? HasCost)?.let { k -> of(k.cost, lines.firstOrNull { it.startsWith(ability.text, ignoreCase = true) && it.getOrNull(ability.text.length)?.isLetter() != true }) }.orEmpty()
                else -> emptyList()
            }
        }
    }

    // CR 602.1a: a cost's parts before the colon; a part choosing objects is its objects' phrase
    @Test fun anActivatedAbilitysParts() = assertEquals(listOf("{1}", "{T}", "a creature"),
        phrases("Artifact", "{1}, {T}, Sacrifice a creature: Draw a card."))

    @Test fun anObjectsPhraseStopsWhereTheyGo() = assertEquals(listOf("{2}", "a land you control"),
        phrases("Artifact", "{2}, Return a land you control to its owner's hand: Draw a card."))

    @Test fun countersRemovedFromAChosenCreature() = assertEquals(listOf("a creature you control"),
        phrases("Artifact", "Remove a +1/+1 counter from a creature you control: Draw a card."))

    @Test fun aPartOfTheSourceItselfIsItsWholePhrase() = assertEquals(listOf("{T}", "Sacrifice this artifact"),
        phrases("Artifact", "{T}, Sacrifice this artifact: Draw a card."))

    @Test fun paidLife() = assertEquals(listOf("{T}", "Pay 1 life"),
        phrases("Land", "{T}, Pay 1 life: Add {B}.", cost = null))

    // CR 606.4: a loyalty ability's cost
    @Test fun aLoyaltyCost() = assertEquals(listOf("[+1]"),
        phrases("Legendary Planeswalker — Chandra", "[+1]: Draw a card.", cost = "{2}{R}", loyalty = "3"))

    // CR 702.6a: a keyword's cost in its line, after its name
    @Test fun anEquipCostThatSacrifices() = assertEquals(listOf("a creature"),
        phrases("Artifact — Equipment", "Equipped creature gets +4/+2.\nEquip—Sacrifice a creature."))

    @Test fun anEquipCostOfMana() = assertEquals(listOf("{2}"),
        phrases("Artifact — Equipment", "Equipped creature gets +1/+1.\nEquip {2}"))

    // CR 601.2f: an additional cost in its static ability's sentence
    @Test fun anAdditionalCost() = assertEquals(listOf("a creature"),
        phrases("Sorcery", "As an additional cost to cast this spell, sacrifice a creature.\nDraw two cards.", cost = "{1}{B}"))
}
