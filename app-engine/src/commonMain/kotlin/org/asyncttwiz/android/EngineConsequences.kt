package org.asyncttwiz.android

import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.change.Move
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.game.GameEvent

/**
 * HIS-246–HIS-249 (owner 2026-11-22, F6): the game log's smaller, indented lines under an event's line, for what it caused,
 * one line per kind (and destination) in the order things happened: triggers put on the Stack, permanents leaving the
 * battlefield and where to, life changes with the total after, tokens created, cards drawn by a spell or ability (the other
 * player's as a count), discarded and milled, counters put on or removed with the count after. Each line in pieces, each
 * name with whose it is (an account id; null for words that stay plain, HIS-240). Never names what the viewer couldn't see
 * (HIS-062).
 */
object EngineConsequences {
    /** One indented line: its words in pieces, each with whose name it is (an account id) or nobody's. */
    data class Line(val pieces: List<Pair<String, String?>>) {
        val text: String get() = pieces.joinToString("") { it.first }
    }

    /** Which of an event's changes a set of indented lines is made from. */
    sealed interface Part {
        /** All of them (HIS-246). */
        data object All : Part
        /** Only the triggers put on the Stack: an event the log shows no line for (HIS-248). */
        data object Triggers : Part
        /** Those before its combat damage ([combatDamageStart]): its own line's, HIS-247. */
        data object BeforeCombatDamage : Part
        /** Its combat damage and what followed: the log's combat damage line's (HIS-247). */
        data object CombatDamage : Part
    }

    /**
     * HIS-246: the indented lines under [event]'s line for [viewer], named as [state] lets them see, from the [part] of its
     * changes asked for.
     */
    fun of(event: GameEvent, state: GameState?, viewer: String, names: Map<String, String>, sight: EngineEventText.Sight? = null,
        part: Part = Part.All): List<Line> {
        val all = event.engine?.changes.orEmpty()
        val start = if (part == Part.BeforeCombatDamage || part == Part.CombatDamage) combatDamageStart(all) else null
        val triggersOnly = part == Part.Triggers
        val changes = effective(when {
            start == null -> all
            part == Part.BeforeCombatDamage -> all.subList(0, start)
            else -> all.subList(start, all.size)
        })
        if (changes.isEmpty()) return emptyList()
        val reader = Reader(changes, state, viewer, names, sight)
        val groups = LinkedHashMap<Any, Group>()
        fun <G : Group> group(key: Any, make: () -> G): G {
            @Suppress("UNCHECKED_CAST")
            return groups.getOrPut(key, make) as G
        }
        for (change in changes) when (change) {
            is Change.StackItemAdded -> if (change.item.kind is StackItemKind.Triggered)
                group("triggered") { Group.Triggered() }.names += reader.ability(change.item)
            is Change.ZoneChange -> if (!triggersOnly) for (move in change.moves) {
                when {
                    move.from == Zone.Battlefield && move.to != Zone.Battlefield -> {
                        val owner = move.after.owner.takeIf { move.to == Zone.Hand || move.to == Zone.Library }
                        group(Triple("left", move.to, owner)) { Group.Left(move.to, owner) }.names += reader.leaving(move)
                    }
                    Meaning.Drew in move.meanings && change.by != null ->
                        group("drew" to move.after.owner) { Group.Drew(move.after.owner) }.cards += reader.drawn(move)
                    Meaning.Discarded in move.meanings && Meaning.Cycled !in move.meanings ->
                        group("discarded" to move.before.owner) { Group.Cards(move.before.owner, "discarded") }.names += reader.public(move)
                    Meaning.Milled in move.meanings ->
                        group("milled" to move.before.owner) { Group.Cards(move.before.owner, "milled") }.names += reader.public(move)
                }
            }
            is Change.Life -> if (!triggersOnly && change.before != change.after) {
                val verb = when {
                    Meaning.LifePaid in change.meanings -> "paid"
                    change.after > change.before -> "gained"
                    else -> "lost"
                }
                val g = group(Triple("life", change.player, verb)) { Group.Life(change.player, verb) }
                g.amount += kotlin.math.abs(change.after - change.before)
                g.after = change.after
            }
            is Change.Created -> if (!triggersOnly) for (placed in change.objects) {
                val token = placed.obj
                if (!token.token || token.zone != Zone.Battlefield) continue
                val g = group("token" to token.ctl) { Group.Tokens(token.ctl) }
                val name = reader.objectName(token) ?: reader.missing("token without a name", "token", token.id)
                g.counts[name] = (g.counts[name] ?: 0) + 1
            }
            is Change.Counters -> if (!triggersOnly) {
                val before = change.before?.count ?: 0
                val after = change.after?.count ?: 0
                if (before == after) continue
                val g = group(Triple("counters", change.holder, change.kind to (after > before))) {
                    Group.Counters(change.holder, change.kind, after > before) }
                g.amount += kotlin.math.abs(after - before)
                g.after = after
            }
            else -> {}
        }
        return render(groups.values.toList(), reader)
    }

    /**
     * HIS-247 (owner 2026-11-22): the log-only line of an event the timeline doesn't show that dealt combat damage: "Combat
     * damage", "First-strike damage" when all of it was dealt in a first-strike damage step (CR 510.4); null for any other
     * event.
     */
    fun combatDamage(event: GameEvent): String? {
        val changes = event.engine?.changes.orEmpty()
        val damage = changes.indices.filter { i -> (changes[i] as? Change.Damage)?.dealt?.any { it.combat } == true }
        if (damage.isEmpty()) return null
        val firstStrike = damage.all { i ->
            // The combat as it stood when the damage was dealt: the latest change of it before, else the next one's start
            val earlier = (i downTo 0).firstNotNullOfOrNull { (changes[it] as? Change.Combat)?.after }
            val combat = earlier ?: (i until changes.size).firstNotNullOfOrNull { (changes[it] as? Change.Combat)?.before }
            combat?.firstStrikeStep == true
        }
        return if (firstStrike) "First-strike damage" else "Combat damage"
    }

    /**
     * HIS-247: where [event]'s combat damage begins among its changes: the step change into the combat damage step it was
     * dealt in, else the damage itself; null when it dealt none.
     */
    fun combatDamageStart(event: GameEvent): Int? = combatDamageStart(event.engine?.changes.orEmpty())

    private fun combatDamageStart(changes: List<Change>): Int? {
        val damage = changes.indexOfFirst { (it as? Change.Damage)?.dealt?.any { d -> d.combat } == true }
        if (damage < 0) return null
        val step = (damage downTo 0).firstOrNull { i -> (changes[i] as? Change.Step)?.after?.step == arcanum.api.TurnStep.CombatDamage }
        return step ?: damage
    }

    /** [changes] without those a later [Change.Reverted] took back (a cancelled cast, D58). */
    private fun effective(changes: List<Change>): List<Change> {
        if (changes.none { it is Change.Reverted }) return changes
        val kept = ArrayList<Change>(changes.size)
        for (c in changes) {
            if (c is Change.Reverted) { val i = kept.lastIndexOf(c.change); if (i >= 0) kept.removeAt(i) } else kept += c
        }
        return kept
    }

    /** A name in a line ([text]) with whose it is ([whose]; null stays plain), and plain words before and after it. */
    private data class Named(val text: String, val whose: String?, val prefix: String = "", val suffix: String = "")

    private sealed class Group {
        class Triggered : Group() { val names = ArrayList<Named>() }
        class Left(val to: Zone, val owner: PlayerId?) : Group() { val names = ArrayList<Named>() }
        class Drew(val player: PlayerId) : Group() { val cards = ArrayList<Named?>() }
        class Cards(val player: PlayerId, val verb: String) : Group() { val names = ArrayList<Named>() }
        class Life(val player: PlayerId, val verb: String) : Group() { var amount = 0; var after = 0 }
        class Tokens(val controller: PlayerId) : Group() { val counts = LinkedHashMap<String, Int>() }
        class Counters(val holder: TargetRef, val kind: CounterKind, val added: Boolean) : Group() { var amount = 0; var after = 0 }
    }

    private fun render(groups: List<Group>, reader: Reader): List<Line> {
        val lines = ArrayList<Line>()
        // HIS-246: counters of one kind put on (or removed from) several by the same amount share a line, each with its count
        val counterLines = LinkedHashMap<Triple<CounterKind, Boolean, Int>, MutableList<Group.Counters>>()
        val order = ArrayList<Any>()
        for (g in groups) {
            if (g is Group.Counters) {
                val key = Triple(g.kind, g.added, g.amount)
                counterLines.getOrPut(key) { mutableListOf<Group.Counters>().also { order += key } } += g
            } else order += g
        }
        for (item in order) {
            val pieces = ArrayList<Pair<String, String?>>()
            fun plain(text: String) { pieces += text to null }
            fun named(n: Named) { if (n.prefix.isNotEmpty()) plain(n.prefix); pieces += n.text to n.whose; if (n.suffix.isNotEmpty()) plain(n.suffix) }
            fun list(names: List<Named>) {
                val shown = if (names.size > 3) names.take(3) else names
                shown.forEachIndexed { i, n ->
                    if (i > 0) plain(if (i == shown.size - 1 && names.size <= 3) " and " else ", ")
                    named(n)
                }
                if (names.size > 3) plain(" and ${names.size - 3} more")
            }
            fun who(player: PlayerId) = named(reader.who(player))
            when (item) {
                is Group.Triggered -> { list(item.names); plain(" triggered") }
                is Group.Left -> {
                    list(item.names)
                    val many = item.names.size > 1
                    when (item.to) {
                        Zone.Graveyard -> plain(" died")
                        Zone.Exile -> plain(if (many) " were exiled" else " was exiled")
                        Zone.Hand -> { plain(" returned to "); named(reader.whose(item.owner!!)); plain(" hand") }
                        Zone.Library -> { plain(if (many) " were put into " else " was put into "); named(reader.whose(item.owner!!)); plain(" library") }
                        else -> plain(" left the battlefield")
                    }
                }
                is Group.Drew -> {
                    who(item.player); plain(" drew ")
                    val seen = item.cards.filterNotNull()
                    if (seen.size == item.cards.size) list(seen)
                    else plain(if (item.cards.size == 1) "a card" else "${item.cards.size} cards")
                }
                is Group.Cards -> { who(item.player); plain(" ${item.verb} "); list(item.names) }
                is Group.Life -> { who(item.player); plain(" ${item.verb} ${item.amount} life (${item.after})") }
                is Group.Tokens -> {
                    who(item.controller); plain(" created ")
                    list(item.counts.map { (name, n) ->
                        val token = if (name.endsWith(" Token") || name.endsWith(" token")) name.dropLast(6) else name
                        Named(token, item.controller.value, prefix = if (n == 1) "a " else "$n ", suffix = if (n == 1) " token" else " tokens")
                    })
                }
                is Triple<*, *, *> -> {
                    @Suppress("UNCHECKED_CAST") val key = item as Triple<CounterKind, Boolean, Int>
                    val shared = counterLines.getValue(key)
                    val single = shared.size == 1
                    list(shared.map { g -> reader.holder(g.holder).let { if (single) it else it.copy(suffix = it.suffix + " (${g.after})") } })
                    val first = shared.first()
                    val counter = counterName(first.kind)
                    val amount = if (first.amount == 1) "a${if (counter.first().lowercaseChar() in "aeiou") "n" else ""} $counter counter"
                        else "${first.amount} $counter counters"
                    plain(if (first.added) " got $amount" else " lost $amount")
                    if (single) plain(" (${first.after})")
                }
                else -> continue
            }
            val joined = pieces.fold(ArrayList<Pair<String, String?>>()) { acc, p ->
                val last = acc.lastOrNull()
                if (last != null && last.second == null && p.second == null) acc[acc.size - 1] = (last.first + p.first) to null else acc += p
                acc
            }
            // A line starts with a capital ("Copy of Storm Spark triggered")
            joined.firstOrNull()?.let { (t, w) -> joined[0] = t.replaceFirstChar { it.uppercaseChar() } to w }
            lines += Line(joined)
        }
        return lines
    }

    /** A counter kind as players read it: "+1/+1", "loyalty", "flying", "charge" (followed by "counter"). */
    private fun counterName(kind: CounterKind): String = when (kind) {
        is CounterKind.PowerToughness -> "${signed(kind.power)}/${signed(kind.toughness)}"
        is CounterKind.Keyword -> kind.keyword.text.lowercase()
        is CounterKind.Named -> kind.name
        CounterKind.Shield -> "shield"
        CounterKind.Stun -> "stun"
        CounterKind.Finality -> "finality"
        CounterKind.Rad -> "rad"
        CounterKind.Hone -> "hone"
        CounterKind.Time -> "time"
        CounterKind.Level -> "level"
        CounterKind.Loyalty -> "loyalty"
        CounterKind.Lore -> "lore"
        CounterKind.Defense -> "defense"
        CounterKind.Poison -> "poison"
        CounterKind.Energy -> "energy"
    }

    private fun signed(n: Int) = if (n < 0) "$n" else "+$n"

    /** Its controller on the battlefield or the Stack, else its owner (CR 108.4a). */
    private val GameObject.ctl: PlayerId get() = controller ?: owner

    /** Names what an event's changes are about as [viewer] may see them. */
    private class Reader(val changes: List<Change>, val state: GameState?, val viewer: String, val names: Map<String, String>,
        val sight: EngineEventText.Sight?) {
        val seen: Set<ObjectId> by lazy {
            val s = state ?: return@lazy emptySet()
            sight?.takeIf { it.state === s && it.viewer == viewer }?.ids ?: arcanum.engine.Host.visible(s, PlayerId(viewer))
        }

        fun player(id: PlayerId) = org.asyncttwiz.automatum.PlayerNames.of(names, id.value, "the other player", "EngineConsequences")
        fun who(id: PlayerId) = Named(if (id.value == viewer) "You" else player(id), id.value)
        fun whose(id: PlayerId) = Named(if (id.value == viewer) "your" else "${player(id)}'s", id.value)

        /** Owner rule (2026-11-11): a stand-in shown because something couldn't be named is a fault. */
        fun missing(what: String, standIn: String, id: Any?) =
            org.asyncttwiz.protocol.Faults.placeholder("EngineConsequences", what, standIn, mapOf("id" to id.toString()))

        fun objectName(o: GameObject): String? = o.definition?.faces?.getOrNull(o.face)?.name?.takeIf { it.isNotEmpty() }
        private fun seenName(m: Move): String? = m.seen?.characteristics?.name?.takeIf { it.isNotEmpty() } ?: objectName(m.before)

        /** A triggered ability put on the Stack, by its source ("Ember Herald's ability", "Shattered Reason's cycling trigger"). */
        fun ability(item: StackItem): Named {
            val label = org.asyncttwiz.automatum.StackAbilityText.kind(item)
                ?.takeIf { it != "Activated ability" && it != "Triggered ability" }?.replaceFirstChar { it.lowercase() } ?: "ability"
            val sourceSnapshot = item.snapshots.firstOrNull { it.id == item.source }
            // A source hidden from the viewer (a face-down permanent, a card in a hand or library) is never named
            val hidden = sourceSnapshot?.let { snap ->
                (snap.zone == Zone.Hand && snap.owner.value != viewer) || snap.zone == Zone.Library ||
                    (snap.characteristics != null && snap.characteristics?.name.isNullOrEmpty() && (snap.controller ?: snap.owner).value != viewer)
            } ?: false
            val name = if (hidden) null else sourceSnapshot?.characteristics?.name?.takeIf { it.isNotEmpty() }
                ?: item.source?.let { name(it) } ?: org.asyncttwiz.automatum.StackAbilityText.ruleName(item)
            if (name != null) return Named("$name's $label", item.controller.value)
            return Named(when {
                hidden && sourceSnapshot?.zone == Zone.Battlefield -> "${whose(sourceSnapshot.controller ?: sourceSnapshot.owner).text} face-down creature's $label"
                hidden -> "a hidden card's $label"
                else -> missing("trigger source without a name", "an ability", item.id)
            }, null)
        }

        /** A permanent leaving the battlefield in [m]: by its name, in its controller's colour; a face-down one the viewer
         * couldn't see and that stays hidden where it went as "Bob's face-down creature". */
        fun leaving(m: Move): Named {
            val controller = m.before.ctl
            val revealed = !m.before.faceDown || controller.value == viewer ||
                (m.to in setOf(Zone.Graveyard, Zone.Exile, Zone.Stack) && !m.after.faceDown) || (m.to == Zone.Hand && m.after.owner.value == viewer)
            val name = seenName(m)?.takeIf { revealed }
                ?: objectName(m.after)?.takeIf { revealed }
            return if (name != null) Named(name, controller.value)
                else Named("${whose(controller).text} face-down creature", null)
        }

        /** A card drawn: named for its owner, a count for anyone else (HIS-062). */
        fun drawn(m: Move): Named? = if (m.after.owner.value != viewer) null
            else Named(objectName(m.after) ?: objectName(m.before) ?: missing("drawn card without a name", "a card", m.after.id), m.after.owner.value)

        /** A card discarded or milled: public where it went (a graveyard), so named for everyone. */
        fun public(m: Move): Named {
            val name = if (!m.after.faceDown) objectName(m.after) ?: objectName(m.before) else null
            return Named(name ?: "a card", m.before.owner.value.takeIf { name != null })
        }

        /** What got or lost counters: a player, or an object by its name as the viewer may see it. */
        fun holder(ref: TargetRef): Named = when (ref) {
            is TargetRef.Player -> who(ref.id)
            is TargetRef.Object -> name(ref.id)?.let { n ->
                Named(n, controllerOf(ref.id)?.value) } ?: hiddenObject(ref.id)
        }

        private fun hiddenObject(id: ObjectId): Named {
            val o = state?.objects?.get(id) ?: movedFrom(id)
            return if (o != null && o.zone == Zone.Battlefield) Named("${whose(o.ctl).text} face-down creature", null) else Named("a card", null)
        }

        private fun movedFrom(id: ObjectId): GameObject? = changes.asSequence().filterIsInstance<Change.ZoneChange>()
            .flatMap { it.moves }.firstOrNull { it.before.id == id }?.before

        private fun controllerOf(id: ObjectId): PlayerId? = state?.objects?.get(id)?.controller ?: movedFrom(id)?.controller
            ?: state?.let { arcanum.engine.Inspect.lastKnown(it, id) }?.let { it.controller ?: it.owner }

        /**
         * [id] by name as the viewer may see it: as it is now when it's still there, else as it was when it left in this event
         * or by its last known information, only where that was public (HIS-062); null when hidden.
         */
        fun name(id: ObjectId): String? {
            val s = state
            s?.objects?.get(id)?.let { o ->
                if (o.faceDown && o.ctl.value != viewer) return null
                val n = objectName(o) ?: return null
                if (o.id !in seen) return null
                return if (o.faceDown) "face-down $n" else n
            }
            movedFrom(id)?.let { o ->
                if (o.zone == Zone.Battlefield && (!o.faceDown || o.ctl.value == viewer)) return objectName(o)
                if (o.zone in setOf(Zone.Graveyard, Zone.Exile, Zone.Stack) && !o.faceDown) return objectName(o)
                if (o.zone == Zone.Hand && o.owner.value == viewer) return objectName(o)
                return null
            }
            val known = s?.let { arcanum.engine.Inspect.lastKnown(it, id) } ?: return null
            val n = known.characteristics?.name?.takeIf { it.isNotEmpty() } ?: return null
            return when (known.zone) {
                Zone.Hand -> n.takeIf { known.owner.value == viewer }
                Zone.Library -> null
                else -> n
            }
        }
    }
}
