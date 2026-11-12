package arcanum.engine.state

import arcanum.api.Ability
import arcanum.api.Designation
import arcanum.api.ObjectMark
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A state of a mechanic the engine codes by hand, kept on an object with its value ([GameObject.statuses]). Stored only
 * while it isn't at its default; an object has at most one status of each [key], and its list is kept in [key] order, so
 * taking a change back gives the same list. Adding a mechanic adds a subclass here; the state classes don't change.
 * Changed by [arcanum.engine.change.Change.ObjectStatusChanged] (or with the object, [arcanum.engine.change.Change.ObjectUpdated]).
 */
@Serializable
sealed interface ObjectStatus {
    /** Which status this is: one of each per object. */
    val key: String

    /** A copy of a card (CR 707.12), not a card: its [GameObject.definition] holds the copied card's characteristics. It
     * ceases to exist in any zone other than the Stack or the battlefield (CR 704.5e). */
    @Serializable @SerialName("copy-of-card") data object CopyOfCard : ObjectStatus { override val key: String get() = "copy-of-card" }

    /** A copy of [permanent]'s prepare spell (CR 722.3c): kept in exile while that permanent stays prepared, and as it is cast a
     * prepare spell (722.3d). */
    @Serializable @SerialName("prepare-spell-of") data class PrepareSpellOf(val permanent: ObjectId.Card) : ObjectStatus {
        override val key: String get() = "prepare-spell-of"
    }

    /** Dealt damage by a source with deathtouch since the last state-based action check (CR 704.5h). */
    @Serializable @SerialName("deathtouch-damage") data object DeathtouchDamage : ObjectStatus { override val key: String get() = "deathtouch-damage" }

    /** Goaded by [by] (CR 701.15), in the order they goaded it. */
    @Serializable @SerialName("goaded") data class Goaded(val by: List<PlayerId>) : ObjectStatus { override val key: String get() = "goaded" }

    /** A merged permanent (mutate, CR 730.2, 702.140c): its other components [above] its own card and [below] it, topmost first.
     * The topmost component gives its characteristics other than abilities (730.2a, 702.140e). */
    @Serializable @SerialName("merged") data class Merged(
        val above: List<MergedPart> = emptyList(),
        val below: List<MergedPart> = emptyList(),
    ) : ObjectStatus { override val key: String get() = "merged" }

    /** A battle's protector (CR 310.9): chosen as it enters (310.9a, 310.12a) or by the state-based action of 310.11; it stays
     * when the permanent stops being a battle (310.9g). */
    @Serializable @SerialName("protector") data class Protector(val player: PlayerId) : ObjectStatus { override val key: String get() = "protector" }

    /** A permanent that isn't a flip card flipped while a copy of one (CR 707.3, 110.5c); its [GameObject.face] stays what is up. */
    @Serializable @SerialName("flipped") data object Flipped : ObjectStatus { override val key: String get() = "flipped" }

    /** A token's copiable [abilities] that are in none of its faces ([CopiableValues.gained], CR 707.9a, 702.140e). */
    @Serializable @SerialName("gained-in-copying") data class GainedInCopying(val abilities: List<Ability>) : ObjectStatus {
        override val key: String get() = "gained-in-copying"
    }

    /** The copiable values each static copy effect that applies to it fixed as it first started to apply (CR 707.2c), kept while
     * it applies ([arcanum.engine.view.StaticCopies]). */
    @Serializable @SerialName("fixed-copies") data class FixedCopies(val copies: List<FixedCopy>) : ObjectStatus {
        override val key: String get() = "fixed-copies"
    }

    /** It has dealt combat damage since it became this object ("as long as they haven't dealt combat damage yet"). */
    @Serializable @SerialName("dealt-combat-damage") data object DealtCombatDamage : ObjectStatus { override val key: String get() = "dealt-combat-damage" }

    /** It has dealt damage, combat or not, since it became this object ("as long as it hasn't dealt damage yet"). */
    @Serializable @SerialName("dealt-damage") data object DealtDamage : ObjectStatus { override val key: String get() = "dealt-damage" }

    /** Phased out until another object leaves the battlefield (CR 702.26, 610.3): it doesn't phase in during untap steps. */
    @Serializable @SerialName("held-phased-out") data object HeldPhasedOut : ObjectStatus { override val key: String get() = "held-phased-out" }

    /** The marks it gained whose [arcanum.api.MarkRule.recordsTurn] asks to keep when, one entry per mark, the latest. */
    @Serializable @SerialName("marks-gained") data class MarksGained(val records: List<MarkGained>) : ObjectStatus {
        override val key: String get() = "marks-gained"
    }

    /** It has been turned face up since it became this object: turned face down again by an effect that lists no characteristics,
     * it is a plain 2/2, not what the way it was cast face down gave it (a disguise's ward, CR 708.2a). */
    @Serializable @SerialName("turned-face-up") data object TurnedFaceUp : ObjectStatus { override val key: String get() = "turned-face-up" }

    /** The level a Class has gained (CR 716.2a), stored once it gains one; the view shows 1 for a Class on the battlefield without it. */
    @Serializable @SerialName("class-level") data class ClassLevel(val level: Int) : ObjectStatus { override val key: String get() = "class-level" }

    /** The room of a dungeon its owner's venture marker is on (CR 309.4), by index into its rooms ([arcanum.api.FaceDefinition.rooms]). */
    @Serializable @SerialName("venture-room") data class VentureRoom(val room: Int) : ObjectStatus { override val key: String get() = "venture-room" }

    /** Paired with [other] (soulbond, CR 702.95) by [by], the player who paired them; each of the pair has it. */
    @Serializable @SerialName("paired-with") data class PairedWith(val other: ObjectId, val by: PlayerId?) : ObjectStatus {
        override val key: String get() = "paired-with"
    }

    /** [player]'s Ring-bearer (CR 701.54a), with [ObjectMark.RingBearer]: the player who chose it. */
    @Serializable @SerialName("ring-bearer") data class RingBearer(val player: PlayerId) : ObjectStatus { override val key: String get() = "ring-bearer" }

    /** Its owner's companion, revealed as the game began (CR 702.139a). */
    @Serializable @SerialName("revealed-companion") data object RevealedCompanion : ObjectStatus { override val key: String get() = "revealed-companion" }

    /**
     * The players who may use this card from where it is now: a permission lets them cast or play it from there (CR 601.3,
     * 305.1, D41: flashback, escape, a foretold card, "you may play lands from the top of your library", "you may cast spells
     * from your graveyard this turn"), or they may activate one of its activated abilities or take one of its special actions
     * there (CR 113.6, 602.2, 116.2: unearth, embalm, scavenge, "{2}{B}: Return this card from your graveyard to your hand", a
     * revealed companion's). Each player once, in turn order, whether or not they can now (priority, timing, costs): the same
     * permissions its cast or land play checks (their holder, condition, uses each turn and the top of a library), and the
     * abilities its activation checks first (where they function, who may activate them, their condition and uses), so that
     * [arcanum.engine.Engine.options] lists no cast, land play or activation of a card outside a hand, the Stack and the
     * battlefield that this doesn't name. Only in the view ([arcanum.engine.view.ObjectView.statuses]), never stored; on a card
     * outside a hand, the Stack and the battlefield; a card whose every face is a land is cast or played only by a player with
     * a permission to play it (CR 305.9).
     */
    @Serializable @SerialName("playable") data class Playable(val by: List<PlayableBy>) : ObjectStatus { override val key: String get() = "playable" }
}

/**
 * One player [ObjectStatus.Playable] names. [how]: [arcanum.api.PlayKind.Play] when a permission lets them play it (a land
 * too), [arcanum.api.PlayKind.Cast] when one lets them cast it, null when none does. [abilities]: the activated abilities and
 * special actions of the card they may use where it is (as [arcanum.engine.input.Input.Activate.ability] names them), in the
 * order the options try them; empty when none.
 */
@Serializable
data class PlayableBy(
    val player: PlayerId,
    val how: arcanum.api.PlayKind?,
    val abilities: List<arcanum.engine.view.AbilityOrigin> = emptyList(),
)

/**
 * A state of a mechanic the engine codes by hand, kept for a player with its value ([PlayerState.statuses]), stored only while
 * it isn't at its default; one of each [key] per player, kept in [key] order. Changed by
 * [arcanum.engine.change.Change.PlayerStatusChanged].
 */
@Serializable
sealed interface PlayerStatus {
    /** Which status this is: one of each per player. */
    val key: String

    /** The player's speed (CR 702.179), 1 to 4. */
    @Serializable @SerialName("speed") data class Speed(val value: Int) : PlayerStatus { override val key: String get() = "speed" }

    /** How many times the Ring has tempted the player (CR 701.54c); its abilities stop growing at four. */
    @Serializable @SerialName("ring-tempted") data class RingTempted(val times: Int) : PlayerStatus { override val key: String get() = "ring-tempted" }

    /** How many dungeons the player has completed this game (CR 309.6). */
    @Serializable @SerialName("dungeons-completed") data class DungeonsCompleted(val count: Int) : PlayerStatus {
        override val key: String get() = "dungeons-completed"
    }

    /** The player has [designation]: the monarch, the initiative, the city's blessing, an enduring story (CR 724–726, 702.195). */
    @Serializable @SerialName("designated") data class Designated(val designation: Designation) : PlayerStatus {
        override val key: String get() = "designated:$designation"
    }
}

/**
 * A state of the game the engine codes by hand, with its value ([GameState.statuses]), stored only while it isn't at its
 * default; one of each [key], kept in [key] order. Changed by [arcanum.engine.change.Change.GameStatusChanged].
 */
@Serializable
sealed interface GameStatus {
    /** Which status this is: one of each per game. */
    val key: String

    /** It is day, or [night] (CR 731); neither while the game has no such status, as it starts. */
    @Serializable @SerialName("day-night") data class DayNight(val night: Boolean) : GameStatus { override val key: String get() = "day-night" }
}

/** The status of type [T] in this list, or null. */
inline fun <reified T : ObjectStatus> List<ObjectStatus>.status(): T? {
    for (s in this) if (s is T) return s
    return null
}

/** The player status of type [T] in this list, or null (for [PlayerStatus.Designated], the first). */
inline fun <reified T : PlayerStatus> List<PlayerStatus>.playerStatus(): T? {
    for (s in this) if (s is T) return s
    return null
}

/** This list with the entry of [key] replaced by [entry] (removed when null), in [keyOf] order. */
internal fun <S> List<S>.withStatus(key: String, entry: S?, keyOf: (S) -> String): List<S> =
    (filter { keyOf(it) != key } + listOfNotNull(entry)).sortedBy(keyOf)

/** This object with [entry] as its status of [key] (none when null). */
internal fun GameObject.withStatus(key: String, entry: ObjectStatus?): GameObject =
    copy(statuses = statuses.withStatus(key, entry, ObjectStatus::key))

/** The entry of [key] in this list, or null. */
internal fun <S> List<S>.statusOf(key: String, keyOf: (S) -> String): S? = firstOrNull { keyOf(it) == key }

// ---- Reading statuses inside the engine

internal val GameObject.copyOfCard: Boolean get() = statuses.status<ObjectStatus.CopyOfCard>() != null
internal val GameObject.prepareSpellOf: ObjectId.Card? get() = statuses.status<ObjectStatus.PrepareSpellOf>()?.permanent
internal val GameObject.deathtouchDamage: Boolean get() = statuses.status<ObjectStatus.DeathtouchDamage>() != null
internal val GameObject.goadedBy: List<PlayerId> get() = statuses.status<ObjectStatus.Goaded>()?.by.orEmpty()
internal val GameObject.mergedAbove: List<MergedPart> get() = statuses.status<ObjectStatus.Merged>()?.above.orEmpty()
internal val GameObject.mergedBelow: List<MergedPart> get() = statuses.status<ObjectStatus.Merged>()?.below.orEmpty()
internal val GameObject.protector: PlayerId? get() = statuses.status<ObjectStatus.Protector>()?.player
internal val GameObject.flippedStatus: Boolean get() = statuses.status<ObjectStatus.Flipped>() != null
internal val GameObject.gainedInCopying: List<Ability> get() = statuses.status<ObjectStatus.GainedInCopying>()?.abilities.orEmpty()
internal val GameObject.fixedCopies: List<FixedCopy> get() = statuses.status<ObjectStatus.FixedCopies>()?.copies.orEmpty()
internal val GameObject.dealtCombatDamage: Boolean get() = statuses.status<ObjectStatus.DealtCombatDamage>() != null
internal val GameObject.dealtDamage: Boolean get() = statuses.status<ObjectStatus.DealtDamage>() != null
internal val GameObject.heldPhasedOut: Boolean get() = statuses.status<ObjectStatus.HeldPhasedOut>() != null
internal val GameObject.turnedFaceUp: Boolean get() = statuses.status<ObjectStatus.TurnedFaceUp>() != null
internal val GameObject.marksGained: List<MarkGained> get() = statuses.status<ObjectStatus.MarksGained>()?.records.orEmpty()

/** The record of when it gained [mark] (CR 702.143a, 702.170d), or null when none is kept. */
internal fun GameObject.gained(mark: ObjectMark): MarkGained? = marksGained.firstOrNull { it.mark == mark }

/** The [ObjectStatus.MarksGained] with [record] as the record of its mark. */
internal fun GameObject.gaining(record: MarkGained): ObjectStatus.MarksGained =
    ObjectStatus.MarksGained(marksGained.filter { it.mark != record.mark } + record)

/** The status of a flag-like [status] set ([on]) or cleared. */
internal fun flag(status: ObjectStatus, on: Boolean): ObjectStatus? = status.takeIf { on }

/** Its cast facts, or [CastFacts.NONE] when it has none. */
internal val GameObject.castOrNone: CastFacts get() = cast ?: CastFacts.NONE
