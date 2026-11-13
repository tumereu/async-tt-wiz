package arcanum.parser

import arcanum.api.EventPattern
import arcanum.api.KeywordActionKind
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.PlayerFilter

/**
 * Trigger events of doing a keyword action or gaining a mark, one rule for every verb and mark ([EventPattern.KeywordActionDone],
 * [EventPattern.GotMark]): a new one is one line in these tables. Text is matched by plain words, with no regex per verb:
 * - "you proliferate", "an opponent scries": who does what a player does;
 * - "you exert a creature", "you foretell a card": what a player does to an object;
 * - "a creature you control explores": what a permanent does;
 * - "this creature becomes monstrous", "this card becomes plotted".
 */
internal object ActionWordings {
    /** A verb in the two forms cards print it. */
    private class Verb(val base: String, val third: String, val kind: KeywordActionKind)

    /** What players do. */
    private val BY_PLAYERS = listOf(
        Verb("collect evidence", "collects evidence", KeywordActionKind.CollectEvidence), // CR 701.59a
        Verb("discover", "discovers", KeywordActionKind.Discover), // CR 701.57b
        Verb("forage", "forages", KeywordActionKind.Forage), // CR 701.61a
        Verb("give a gift", "gives a gift", KeywordActionKind.GiveGift), // CR 702.174c
        Verb("investigate", "investigates", KeywordActionKind.Investigate), // CR 701.16a
        Verb("proliferate", "proliferates", KeywordActionKind.Proliferate), // CR 701.34a
        Verb("scry", "scries", KeywordActionKind.Scry), // CR 701.22d
        Verb("surveil", "surveils", KeywordActionKind.Surveil), // CR 701.25d
    )

    /** What players do to an object. */
    private val TO_OBJECTS = listOf(
        Verb("exert", "exerts", KeywordActionKind.Exert), // CR 701.43a
        Verb("foretell", "foretells", KeywordActionKind.Foretell), // CR 702.143c
    )

    /** What permanents do. */
    private val BY_PERMANENTS = listOf(
        Verb("connive", "connives", KeywordActionKind.Connive), // CR 701.50f
        Verb("explore", "explores", KeywordActionKind.Explore), // CR 701.44b
    )

    /** The marks a permanent, or a card in exile, can become. */
    private val MARKS = mapOf<String, ObjectMark>(
        "monstrous" to ObjectMark.Monstrous, // CR 701.37b
        "plotted" to ObjectMark.Plotted, // CR 702.170a, c: a card in exile
        "prepared" to ObjectMark.Prepared, // CR 722.3a
        "renowned" to ObjectMark.Renowned, // CR 702.112b
        "saddled" to ObjectMark.Saddled, // CR 702.171b
        "suspected" to ObjectMark.Suspected, // CR 701.60a
    )

    private val PLAYERS = listOf<Pair<String, PlayerFilter>>("you " to PlayerFilter.You, "an opponent " to PlayerFilter.Opponent, "a player " to PlayerFilter.Any)

    /** The action a player does with no object, by its base form ("proliferate"); null when it isn't one. */
    fun playerAction(base: String): KeywordActionKind? = BY_PLAYERS.firstOrNull { it.base == base }?.kind

    /** "You" did [kind]. */
    fun youDid(kind: KeywordActionKind): EventPattern = EventPattern.KeywordActionDone(kind, PlayerFilter.You)

    /** The event [text] says, with [subject] reading the words naming an object; null when it isn't one of these. */
    fun event(text: String, subject: (String) -> ObjectFilter?): EventPattern? {
        MARKS.entries.firstOrNull { text.endsWith(" becomes ${it.key}") }?.let { (word, mark) ->
            return EventPattern.GotMark(mark, subject(text.removeSuffix(" becomes $word")) ?: return null)
        }
        BY_PERMANENTS.firstOrNull { text.endsWith(" ${it.third}") }?.let { verb ->
            return EventPattern.KeywordActionDone(verb.kind, objects = subject(text.removeSuffix(" ${verb.third}")) ?: return null)
        }
        val (word, players) = PLAYERS.firstOrNull { text.startsWith(it.first) } ?: return null
        val rest = text.removePrefix(word)
        fun form(verb: Verb) = if (players == PlayerFilter.You) verb.base else verb.third
        BY_PLAYERS.firstOrNull { rest == form(it) }?.let { return EventPattern.KeywordActionDone(it.kind, players) }
        TO_OBJECTS.firstOrNull { rest.startsWith(form(it) + " ") }?.let { verb ->
            val noun = rest.removePrefix(form(verb) + " ")
            return EventPattern.KeywordActionDone(verb.kind, players, if (noun == "a card") ObjectFilter.Any else subject(noun) ?: return null)
        }
        return null
    }
}
