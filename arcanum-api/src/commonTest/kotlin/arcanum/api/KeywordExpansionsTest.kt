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
        // CR 702.25, 702.31, 702.32, 702.45, 702.64, 702.67, 702.68, 702.70, 702.77, 702.86, 702.89, 702.93, 702.107, 702.110,
        // 702.112, 702.114, 702.118, 702.126, 702.130, 702.133, 702.134, 702.135, 702.161, 702.163, 702.182
        Keyword.Flanking, Keyword.Horsemanship, Keyword.Fading(2), Keyword.Bushido(1), Keyword.Absorb(1), Keyword.Fortify(cost),
        Keyword.Frenzy(1), Keyword.Poisonous(1), Keyword.Reinforce(1, cost), Keyword.Annihilator(1), Keyword.UmbraArmor, Keyword.Undying,
        Keyword.Outlast(cost), Keyword.Exploit, Keyword.Renown(1), Keyword.Devoid, Keyword.Skulk, Keyword.Improvise, Keyword.Afflict(1),
        Keyword.JumpStart, Keyword.Mentor, Keyword.Afterlife(1), Keyword.LivingMetal, Keyword.ForMirrodin, Keyword.JobSelect,
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
        listOf(KeywordAction.Investigate(count = Value.Fixed(1)), KeywordAction.Connive(objects = Objects.Self, count = Value.Fixed(1)), KeywordAction.Bolster(Value.Fixed(2))).forEach { action ->
            val steps = assertNotNull(KeywordExpansions.expand(action), action.toString())
            assertEquals(emptyList(), DefinitionChecks.problems(card(listOf(Ability.Spell(Instructions(steps = steps))))), action.toString())
        }
    }

    // CR 702.35a: "When this card is exiled this way": the trigger is linked to the discard replacement, not any exile.
    @Test
    fun madnessTriggersOnlyThroughItsReplacement() {
        val abilities = KeywordExpansions.expand(Keyword.Madness(cost))!!
        val replacement = ((abilities.single() as Ability.Static).mechanics.single() as Mechanic.Replacement)
        assertEquals(EventPattern.Discard(ObjectFilter.Is(Objects.Self), players = PlayerFilter.Any), replacement.event)
        assertNotNull(replacement.whenApplied)
    }

    // CR 702.88a, 702.27a: rebound and buyback act "as it resolves", not when the spell is countered (CR 608.2n).
    @Test
    fun reboundAndBuybackOnlyAsTheSpellResolves() {
        val rebound = (KeywordExpansions.expand(Keyword.Rebound)!!.single() as Ability.Static).mechanics.single() as Mechanic.Replacement
        assertEquals(true, (rebound.event as EventPattern.ZoneChange).resolving)
        val buyback = (KeywordExpansions.expand(Keyword.Buyback(cost))!![1] as Ability.Static).mechanics.single() as Mechanic.Replacement
        assertEquals(true, (buyback.event as EventPattern.ZoneChange).resolving)
    }

    // CR 702.151b: an attached reconfigure Equipment stops being a creature.
    @Test
    fun reconfigureStopsBeingACreatureWhileAttached() {
        val static = KeywordExpansions.expand(Keyword.Reconfigure(cost))!!.filterIsInstance<Ability.Static>().single()
        assertEquals(listOf(Mechanic.SetTypes(remove = listOf(CardType.Creature))), static.mechanics)
    }

    // CR 701.50a: connive counts the discarded nonland cards wherever they went (not on the battlefield).
    @Test
    fun conniveCountsDiscardedCardsWhereverTheyAre() {
        val text = Json.encodeToString(Step.serializer(), KeywordExpansions.expand(KeywordAction.Connive(objects = Objects.Self, count = Value.Fixed(1)))!!.single())
        assertEquals(true, "\"type\":\"where\"" in text)
    }
}
