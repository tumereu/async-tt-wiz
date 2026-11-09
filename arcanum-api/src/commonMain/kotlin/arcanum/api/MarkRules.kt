package arcanum.api

/**
 * When a mark goes, apart from its object leaving the zone it lives in, which ends every mark (the object is new then, CR 400.7).
 */
sealed interface MarkClear {
    /** As the turn ends, in the cleanup step (saddled, CR 702.171b). */
    data object EndOfTurn : MarkClear
    /** When the permanent is turned face up (manifested, cloaked, CR 701.40a, 701.58a). */
    data object TurnedFaceUp : MarkClear
    /** When a spell or ability takes it off ("no longer suspected", "becomes unprepared", locking a door). */
    data object ByEffect : MarkClear
    /** When the spell it lets its controller cast becomes cast (prepared, CR 722.3c). */
    data object ItsCopyCast : MarkClear
    /** When another object gets it from the same player (a Ring-bearer, CR 701.54a). */
    data object ReplacedByAnother : MarkClear
    /** When another player gains control of the permanent (a Ring-bearer, CR 701.54a). */
    data object ControlChanges : MarkClear
}

/** What a [MarkPermission.CastCard] costs. */
sealed interface MarkCost {
    /** Without paying its mana cost (a plotted card, CR 702.170d). */
    data object Free : MarkCost
    /** For any cost of keyword [keyword] the card had as it gained the mark, or has printed (a foretold card, CR 702.143a, e). */
    data class OfKeyword(val keyword: KeywordKind) : MarkCost
}

/** The permission to cast something from exile that a mark gives. */
sealed interface MarkPermission {
    /**
     * The marked card's owner may cast it from exile after the turn it gained the mark, for [cost], as long as it stays there. [timing]:
     * [Timing.Sorcery] holds it to their main phase with an empty Stack even if it is an instant; null: as the spell may usually be cast.
     */
    data class CastCard(val cost: MarkCost, val timing: Timing? = null) : MarkPermission
    /**
     * As the permanent gains the mark, its controller gets a copy of its prepare spell in exile, and may cast the copy for as long as
     * the permanent stays marked and on the battlefield (prepared, CR 722.3c).
     */
    data object CastCopy : MarkPermission
}

/**
 * What the rules say of one [ObjectMark]. A designation the rules give a meaning to has it here and nowhere else, so a host or an
 * engine reads one table.
 *
 * @property name the mark's stable name, as it is stored ("suspected", "plotted").
 * @property zones where it can live: an effect putting it on an object elsewhere does nothing.
 * @property abilities what the marked object has for as long as it is marked, applied in the ability layer (CR 613.1f) with the
 *   object's timestamp; a later loss of all abilities takes them.
 * @property clears when it goes, besides its object leaving [zones]; empty: only then.
 * @property permission what it lets be cast from exile; null: nothing.
 * @property recordsTurn whether the turn it was gained is kept on the object: [permission]'s "after the turn".
 */
data class MarkRule(
    val name: String,
    val zones: Set<Zone>,
    val abilities: List<Ability> = emptyList(),
    val clears: Set<MarkClear> = emptySet(),
    val permission: MarkPermission? = null,
    val recordsTurn: Boolean = false,
)

/**
 * The table beside [KeywordExpansions]: for each [ObjectMark], its [MarkRule]. A CR change to a mark means changing one entry here,
 * and a new mark is a new entry and nothing else unless it needs a new kind of rule.
 *
 * The engine reads [MarkRule.zones], [MarkRule.abilities], [MarkRule.permission], [MarkRule.recordsTurn] and
 * [MarkClear.EndOfTurn] from here. The other [MarkClear]s state the rule, carried out where the designation's own action is (turning
 * face up, [Effect.Mark], casting, the Ring).
 */
object MarkRules {
    private val PERMANENT = setOf(Zone.Battlefield)
    private val IN_EXILE = setOf(Zone.Exile)

    // CR 701.37b: a marker with no meaning of its own, until it leaves the battlefield
    private val MONSTROUS = MarkRule("monstrous", PERMANENT)
    // CR 702.112b
    private val RENOWNED = MarkRule("renowned", PERMANENT)
    // CR 701.60a, c
    private val SUSPECTED = MarkRule("suspected", PERMANENT, abilities = listOf(Keyword.Menace,
        Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))),
        clears = setOf(MarkClear.ByEffect))
    // CR 702.171b
    private val SADDLED = MarkRule("saddled", PERMANENT, clears = setOf(MarkClear.EndOfTurn))
    // CR 719.3b
    private val SOLVED = MarkRule("solved", PERMANENT)
    // CR 709.5c, g
    private val UNLOCKED = MarkRule("unlocked", PERMANENT, clears = setOf(MarkClear.ByEffect))
    // CR 701.40a
    private val MANIFESTED = MarkRule("manifested", PERMANENT, clears = setOf(MarkClear.TurnedFaceUp))
    // CR 722.3a–c
    private val PREPARED = MarkRule("prepared", PERMANENT, clears = setOf(MarkClear.ByEffect, MarkClear.ItsCopyCast), permission = MarkPermission.CastCopy)
    // CR 701.64b
    private val HARNESSED = MarkRule("harnessed", PERMANENT)
    // CR 702.185b: only a label: the permission to cast it comes from the warp ability's delayed trigger (702.185a)
    private val WARPED = MarkRule("warped", IN_EXILE)
    // CR 702.143a, d
    private val FORETOLD = MarkRule("foretold", IN_EXILE, recordsTurn = true,
        permission = MarkPermission.CastCard(MarkCost.OfKeyword(Keyword.Foretell.Kind)))
    // CR 702.170c, d
    private val PLOTTED = MarkRule("plotted", IN_EXILE, recordsTurn = true,
        permission = MarkPermission.CastCard(MarkCost.Free, Timing.Sorcery))
    // CR 701.58a
    private val CLOAKED = MarkRule("cloaked", PERMANENT, clears = setOf(MarkClear.TurnedFaceUp))
    // CR 701.54a, b
    private val RING_BEARER = MarkRule("ring-bearer", PERMANENT, clears = setOf(MarkClear.ReplacedByAnother, MarkClear.ControlChanges))
    private val NAMED = MarkRule("named", PERMANENT, clears = setOf(MarkClear.ByEffect))

    /** The rules of [mark]. */
    fun of(mark: ObjectMark): MarkRule = when (mark) {
        ObjectMark.Monstrous -> MONSTROUS
        ObjectMark.Renowned -> RENOWNED
        ObjectMark.Suspected -> SUSPECTED
        ObjectMark.Saddled -> SADDLED
        ObjectMark.Solved -> SOLVED
        is ObjectMark.Unlocked -> UNLOCKED
        ObjectMark.Manifested -> MANIFESTED
        ObjectMark.Prepared -> PREPARED
        ObjectMark.Harnessed -> HARNESSED
        ObjectMark.Warped -> WARPED
        ObjectMark.Foretold -> FORETOLD
        ObjectMark.Plotted -> PLOTTED
        ObjectMark.Cloaked -> CLOAKED
        ObjectMark.RingBearer -> RING_BEARER
        is ObjectMark.Named -> NAMED
    }

    /** Every rule of the table, each once. */
    val all: List<MarkRule> = listOf(MONSTROUS, RENOWNED, SUSPECTED, SADDLED, SOLVED, UNLOCKED, MANIFESTED, PREPARED, HARNESSED, WARPED, FORETOLD,
        PLOTTED, CLOAKED, RING_BEARER, NAMED)

    /** The rule whose [MarkRule.name] is [name], or null. */
    fun named(name: String): MarkRule? = all.firstOrNull { it.name == name }
}
