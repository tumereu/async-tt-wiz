package arcanum.parser

import arcanum.api.Ability
import arcanum.api.AbilityLabel
import arcanum.parser.cube.card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Text the parser can't read safely stays unparsed instead of being half-read or guessed. */
class OracleParserSafetyTest {
    private fun parse(type: String, text: String) = OracleParser.parse(card(name = "Test Card", cost = "{1}", type = type, text = text)).abilities

    // CR 608.2b: a spell resolves as one object, so if one of its lines can't be read none of them is automated.
    @Test
    fun spellWithOneUnreadLineStaysWhollyUnparsed() {
        val abilities = parse("Sorcery", "Each player passes a creature they control to the player on their left.\nDraw a card.")
        assertEquals(listOf(Ability.Unparsed("Each player passes a creature they control to the player on their left."), Ability.Unparsed("Draw a card.")), abilities)
    }

    // CR 608.2b, 601.2f: the effect isn't automated while its additional cost stays manual.
    @Test
    fun spellWithUnreadAdditionalCostStaysUnparsed() {
        val abilities = parse("Sorcery", "As an additional cost to cast this spell, exile a creature card from a graveyard.\nDraw two cards.")
        assertTrue(abilities.all { it is Ability.Unparsed }, abilities.toString())
    }

    // CR 207.2c: only listed ability words are labels without rules meaning.
    @Test
    fun unknownAbilityWordStaysUnparsed() {
        assertEquals(listOf(Ability.Unparsed("Glimmer — {T}: Add {G}.")), parse("Artifact", "Glimmer — {T}: Add {G}."))
    }

    // CR 702.177a: exhaust adds rules, so it is a label, not an ability word.
    @Test
    fun exhaustIsALabel() {
        val ability = parse("Artifact", "Exhaust — {T}: Add {G}.").single()
        assertTrue(ability is Ability.Activated && ability.label == AbilityLabel.Exhaust && ability.abilityWord == null, ability.toString())
    }

    // CR 205.3m: "X" and unknown plurals are not subtypes. ("Sacrifice X Squirrels" is a count announced as it is
    // activated, CR 107.3a, so the noun itself is X here.)
    @Test
    fun unknownCapitalisedWordsAreNotSubtypes() {
        assertTrue(parse("Artifact", "Sacrifice an X: Add {G}.").single() is Ability.Unparsed)
        assertTrue(parse("Artifact", "Sacrifice two Xs: Add {G}.").single() is Ability.Unparsed)
    }

    // CR 122.1: a sentence naming a counter kind twice reads only when both name the same kind; one with two kinds isn't read as the
    // first (misreads found by swapping wordings: the second kind was a wildcard)
    @Test
    fun aSecondCounterKindMustMatchTheFirst() {
        assertTrue(parse("Artifact", "If one or more +1/+1 counters would be put on a creature you control, twice that many -1/-1 counters " +
            "are put on that creature instead.").single() is Ability.Unparsed)
        assertTrue(parse("Artifact", "Each other Vehicle and creature you control enters with an additional +1/+1 counter on it if its mana " +
            "value is 4 or less. Otherwise, it enters with three additional -1/-1 counters on it.").single() is Ability.Unparsed)
        assertTrue(parse("Enchantment", "At the beginning of your upkeep, for each color, put a +1/+1 counter on a Dragon you control of that " +
            "color. If you put -1/-1 counters on five Dragons this way, you win the game.").single() is Ability.Unparsed)
    }

    // CR 711.2a: a level band is one static ability, so a band with an unread line stays printed text as a whole and
    // its readable lines never become abilities outside the band.
    @Test
    fun levelBandWithAnUnreadLineStaysUnparsed() {
        val abilities = parse("Creature — Elf", "LEVEL 1-4\n1/2\n{T}: Add {G}{G}.\nExchange control of target creature and target artifact.")
        assertEquals(listOf(Ability.Unparsed("LEVEL 1-4\n1/2\n{T}: Add {G}{G}.\nExchange control of target creature and target artifact.")), abilities)
    }
}
