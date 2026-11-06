package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Effect
import arcanum.api.Step
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An emblem keeps the printed text of its quoted ability (CR 114.2: "[Player] gets an emblem with [ability]"; CR 114.3:
 * the emblem has no characteristics but its abilities, so their text is all a player can read of it).
 */
class EmblemTextTest {
    /** The emblems the face's abilities create, with their abilities' texts. */
    private fun emblemTexts(face: arcanum.api.FaceDefinition): List<String?> = face.abilities.flatMap { ability ->
        val steps = when (ability) {
            is Ability.Activated -> ability.instructions.steps
            is Ability.Spell -> ability.instructions.steps
            else -> emptyList()
        }
        steps.mapNotNull { ((it as? Step.Do)?.effect as? Effect.CreateEmblem) }.flatMap { e -> e.abilities.map(::text) }
    }

    private fun text(ability: Ability): String? = when (ability) {
        is Ability.Static -> ability.text
        is Ability.Triggered -> ability.text
        is Ability.Activated -> ability.text
        else -> null
    }

    // CR 114.2, CR 114.3: an invented planeswalker's two emblems (a static and a triggered ability) keep their texts
    @Test
    fun anEmblemKeepsItsQuotedAbilitysText() {
        val face = OracleParser.parse(OracleCard("Vela, Ember Warden", "{2}{R}{R}", "Legendary Planeswalker — Vela",
            """
            [+1]: Vela deals 1 damage to each opponent.
            [−4]: You get an emblem with "Creatures you control get +1/+1."
            [−8]: You get an emblem with "At the beginning of your upkeep, you gain 2 life."
            """.trimIndent(), loyalty = "4"))
        assertEquals(listOf("Creatures you control get +1/+1.", "At the beginning of your upkeep, you gain 2 life."), emblemTexts(face))
    }

    // CR 114.2: a sorcery's emblem keeps its text too
    @Test
    fun aSpellsEmblemKeepsItsText() {
        val face = OracleParser.parse(OracleCard("Ember Sigil", "{0}", "Sorcery", "You get an emblem with \"Creatures you control get +1/+1.\""))
        assertEquals(listOf("Creatures you control get +1/+1."), emblemTexts(face))
    }
}
