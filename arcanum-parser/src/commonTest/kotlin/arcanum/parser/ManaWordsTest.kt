package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Effect
import arcanum.api.Mechanic
import arcanum.api.Step
import arcanum.parser.cube.card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The printed words of the riders of the mana a parsed ability adds ([arcanum.api.ManaProduction.words]), for hosts that show what a
 * mana in a pool carries: each rider kind the parser reads, with invented cards in real oracle templates. Every parsing test also
 * checks that each mana with a rider has words the card prints (`checkManaWords`).
 */
class ManaWordsTest {
    /** The riders (as the production holds them) and words of each mana production of the card's abilities, in order. */
    private fun words(type: String, text: String, pt: String? = null): List<Pair<Boolean, List<String>>> {
        val face = OracleParser.parse(card(name = "Glintwick Thing", cost = "{1}", type = type, text = text, pt = pt))
        fun adds(steps: List<Step>): List<arcanum.api.ManaProduction> = steps.mapNotNull { ((it as? Step.Do)?.effect as? Effect.AddMana)?.mana }
        fun of(a: Ability): List<arcanum.api.ManaProduction> = when (a) {
            is Ability.Activated -> adds(a.instructions.steps)
            is Ability.Triggered -> adds(a.instructions.steps)
            is Ability.Static -> a.mechanics.filterIsInstance<Mechanic.GainAbilities>().flatMap { it.abilities.flatMap(::of) }
            else -> emptyList()
        }
        return face.abilities.flatMap(::of).map { it.hasRiders to it.words }
    }

    // CR 106.6: a spending restriction, "Spend this mana only …"
    @Test fun aRestriction() = assertEquals(listOf(true to listOf("Spend this mana only to cast creature spells.")),
        words("Creature — Elf Druid", "{T}: Add three mana of any one color. Spend this mana only to cast creature spells.", pt = "1/1"))

    // CR 106.6: a restriction worded as what it can't be spent on; in a quoted ability a grant gives (CR 113.1a)
    @Test fun aRestrictionInAGrantedAbility() = assertEquals(listOf(true to listOf("This mana can't be spent to cast a nonartifact spell.")),
        words("Artifact", "Creatures you control have \"{T}: Add {C}. This mana can't be spent to cast a nonartifact spell.\""))

    // CR 106.6: a restriction and an additional effect on the spell it pays for, one sentence
    @Test fun aRestrictionAndCantBeCountered() = assertEquals(listOf(false to emptyList(),
        true to listOf("Spend this mana only to cast a creature spell of the chosen type, and that spell can't be countered.")),
        words("Land", """
            As this land enters, choose a creature type.
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a creature spell of the chosen type, and that spell can't be countered.
        """.trimIndent()))

    // CR 106.6: an additional effect on a spell of some kind it is spent on
    @Test fun whatTheSpellGets() = assertEquals(listOf(true to listOf("If that mana is spent on a Dragon creature spell, it gains haste until end of turn.")),
        words("Artifact", "{T}: Add {R}. If that mana is spent on a Dragon creature spell, it gains haste until end of turn."))

    // CR 106.4: mana that isn't lost as steps and phases end
    @Test fun keptUntilEndOfTurn() = assertEquals(listOf(true to listOf("Until end of turn, you don't lose this mana as steps and phases end.")),
        words("Creature — Lizard Warrior", "Whenever this creature attacks, add {R}. Until end of turn, you don't lose this mana as steps and phases end.", pt = "2/2"))

    // CR 106.6: a trigger when the mana is spent
    @Test fun whenSpent() = assertEquals(
        listOf(true to listOf("When that mana is spent to cast a red instant or sorcery spell, copy that spell and you may choose new targets for the copy.")),
        words("Artifact", "{T}: Add {R}. When that mana is spent to cast a red instant or sorcery spell, copy that spell and you may choose new targets for the copy."))

    // CR 106.6, 107.3: only on a cost that contains {X}
    @Test fun onlyCostsWithX() = assertEquals(listOf(true to listOf("Spend this mana only on costs that contain {X}.")),
        words("Artifact", "{T}: Reveal any number of cards with {X} in their mana cost in your hand. Add {C}{C} for each card revealed this way. " +
            "Spend this mana only on costs that contain {X}."))

    // Each ability's mana gets the riders after its own "Add"
    @Test fun twoAbilitiesEachTheirOwn() = assertEquals(listOf(
        true to listOf("Spend this mana only to cast an artifact spell."), true to listOf("Spend this mana only to activate abilities of artifact sources.")),
        words("Artifact Creature — Construct", "{T}: Add {U}. Spend this mana only to cast an artifact spell.\n" +
            "{T}: Add {U}{U}. Spend this mana only to activate abilities of artifact sources.", pt = "1/1"))

    // Mana without riders has no words
    @Test fun plainManaHasNone() = assertEquals(listOf(false to emptyList<String>()), words("Artifact", "{T}: Add {G}."))

    // Rider sentences group by the "Add" sentence before them, in a quoted ability without its quotation marks
    @Test fun ridersGroupByTheirAdd() {
        assertEquals(listOf(listOf("Spend this mana only to cast Room spells.", "Until end of turn, you don't lose this mana as steps and phases end."),
            listOf("Spend this mana only to activate abilities.")),
            ManaWords.groups("• Add {R}. Spend this mana only to cast Room spells. Until end of turn, you don't lose this mana as steps and phases end.\n" +
                "• Add {G}. You gain 1 life.\nLands you control have \"{T}: Add {C}. Spend this mana only to activate abilities.\""))
        assertTrue(ManaWords.groups("{T}: Add {R}. This artifact deals 1 damage to you.").isEmpty())
    }
}
