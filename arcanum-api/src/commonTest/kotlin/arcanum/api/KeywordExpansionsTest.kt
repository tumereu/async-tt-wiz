package arcanum.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json

/** The keyword, keyword-action and predefined-token tables: every entry the first target (D46) needs exists, passes the
 * definition checks and survives JSON. */
class KeywordExpansionsTest {
    private val json = Json
    private val cost = Cost.mana(ManaCost.parse("{1}{B}")!!)

    // CR 702.27, 702.28, 702.35, 702.37, 702.40, 702.41, 702.43, 702.44, 702.47, 702.49, 702.51, 702.52, 702.62, 702.66,
    // 702.73, 702.74, 702.75, 702.79, 702.81, 702.83, 702.84, 702.85, 702.87, 702.88, 702.91, 702.92, 702.96, 702.103,
    // 702.120, 702.122, 702.123, 702.124, 702.138, 702.139, 702.147, 702.148, 702.149, 702.151
    private val keywords: List<Keyword> = listOf(
        Keyword.Persist, Keyword.Storm, Keyword.Changeling, Keyword.Delve, Keyword.Convoke, Keyword.Exalted, Keyword.Retrace,
        Keyword.Sunburst, Keyword.Rebound, Keyword.Decayed, Keyword.Partner, Keyword.Shadow, Keyword.LivingWeapon,
        Keyword.Cascade, Keyword.BattleCry, Keyword.Training, Keyword.Ninjutsu(cost), Keyword.CommanderNinjutsu(cost),
        Keyword.Unearth(cost), Keyword.Affinity(ObjectFilter.Type(CardType.Artifact), "artifacts"), Keyword.Evoke(cost),
        Keyword.Madness(cost), Keyword.Modular(1), Keyword.Dredge(3), Keyword.Suspend(3, cost), Keyword.Crew(1),
        Keyword.Morph(cost), Keyword.Megamorph(cost), Keyword.Escalate(cost), Keyword.Cleave(cost), Keyword.LevelUp(cost),
        Keyword.Reconfigure(cost), Keyword.Fabricate(1), Keyword.Hideaway(4), Keyword.Buyback(cost), Keyword.Escape(cost),
        Keyword.Overload(cost), Keyword.Bestow(cost), Keyword.Splice(ObjectFilter.Sub(Subtype("Arcane")), cost),
        Keyword.CompanionAbility("Each permanent card in your starting deck has mana value 2 or less."),
    )

    private fun card(abilities: List<Ability>) = CardDefinition(Layout.Normal, listOf(FaceDefinition("Test Subject",
        types = listOf(CardType.Creature), power = PrintedNumber.Fixed(1), toughness = PrintedNumber.Fixed(1), abilities = abilities)))

    @Test
    fun everyCubeKeywordExpandsCleanly() {
        keywords.forEach { keyword ->
            val abilities = assertNotNull(KeywordExpansions.expand(keyword), keyword.text)
            val definition = card(abilities)
            assertEquals(emptyList(), DefinitionChecks.problems(definition, keywordTable = true), keyword.text)
            assertEquals(definition, json.decodeFromString(CardDefinition.serializer(), json.encodeToString(CardDefinition.serializer(), definition)), keyword.text)
        }
    }

    // CR 111.10a-g
    @Test
    fun predefinedTokensAreDefined() {
        PredefinedTokens.names.forEach { name ->
            val face = assertNotNull(PredefinedTokens.face(name), name)
            assertEquals(emptyList(), DefinitionChecks.problems(CardDefinition(Layout.Normal, listOf(face))), name)
        }
    }

    // CR 701.16a, 701.39a, 701.50a
    @Test
    fun keywordActionsExpandCleanly() {
        listOf(KeywordAction.Investigate(), KeywordAction.Connive(), KeywordAction.Bolster(Value.Fixed(2))).forEach { action ->
            val steps = assertNotNull(KeywordExpansions.expand(action), action.toString())
            assertEquals(emptyList(), DefinitionChecks.problems(card(listOf(Ability.Spell(Instructions(steps = steps))))), action.toString())
        }
    }
}
