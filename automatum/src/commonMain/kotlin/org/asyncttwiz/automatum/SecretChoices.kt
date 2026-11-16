package org.asyncttwiz.automatum

import arcanum.engine.change.Change
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.Secrets

/**
 * TBL-133, HIS-091 (owner 2026-11-16): secret choices ("secretly choose Human, Merfolk, or Goblin": the engine's [Secrets]) as the
 * app shows them. While secret, only the players who know the choice see its value; everyone else reads [HIDDEN]. The rules'
 * reveal (a cost "Reveal the creature type you chose") is [revealed] in the event that made it, named to everyone, and from then
 * on the value shows to all. The value stays on every device (I-D27): this decides only what is shown.
 */
object SecretChoices {
    /** What a player who doesn't know a secret choice reads in its place. */
    const val HIDDEN = "Secret choice"

    private val MARKER = Secrets.markerKey("")

    /** Whether memory key [key] is the engine's secret marker beside a choice, never a choice itself. */
    fun isMarker(key: String): Boolean = key.endsWith(MARKER)

    /** Whether [viewer] may see [o]'s memory under [key]: it isn't secret, or they are among those who know it. */
    fun knows(o: GameObject, key: String, viewer: String): Boolean =
        !Secrets.isSecret(o, key) || Secrets.knownBy(o, key).orEmpty().any { it.value == viewer }

    /** A secret choice that was revealed: by [player] (who knew it; null when the engine kept nobody), the memory under [key] of
     * the object [source], [value] what was chosen. */
    data class Reveal(val player: PlayerId?, val source: ObjectId, val key: String, val value: NamedValue?)

    /**
     * The secret choices [changes] (one event's) revealed: each secret marker the event took away. The value is read from the
     * object as it was then: as it left its zone in the same event (a cost that sacrificed it), else from [state].
     */
    fun revealed(changes: List<Change>, state: GameState?): List<Reveal> = changes.mapNotNull { change ->
        val memory = change as? Change.Memory ?: return@mapNotNull null
        if (!isMarker(memory.key) || memory.before == null || memory.after != null) return@mapNotNull null
        val key = memory.key.removeSuffix(MARKER)
        val moved = changes.asSequence().filterIsInstance<Change.ZoneChange>().flatMap { it.moves }
            .firstOrNull { it.before.id == memory.id }?.before?.linked?.get(key)
        val value = moved ?: changes.filterIsInstance<Change.Memory>().lastOrNull { it.id == memory.id && it.key == key }?.after
            ?: state?.obj(memory.id)?.linked?.get(key)
        Reveal((memory.before as? NamedValue.Players)?.ids?.firstOrNull(), memory.id, key, value)
    }

    /** HIS-091: what [reveal] reads in an event's text for [viewer]: "Bob revealed: Goblin" ("You revealed: Goblin"); [names]
     * names the players by account, [state] names a chosen object. */
    fun text(reveal: Reveal, state: GameState?, viewer: String, names: Map<String, String>): String {
        val details = mapOf("source" to reveal.source.toString(), "key" to reveal.key, "player" to "${reveal.player?.value}",
            "value" to "${reveal.value}")
        val who = reveal.player?.let { if (it.value == viewer) "You" else names[it.value] }
            ?: org.asyncttwiz.protocol.Faults.placeholder("SecretChoices", "revealed secret choice without its chooser", "A player", details)
        val value = reveal.value?.let { v -> valueText(reveal.key, v, names) { id -> state?.obj(id)?.let { it.definition.faces.getOrNull(it.face)?.name } } }
            ?: org.asyncttwiz.protocol.Faults.placeholder("SecretChoices", "revealed secret choice without its value", "a secret choice", details)
        return "$who revealed: $value"
    }

    /** A secret choice's value in words ("Goblin", "blue", "Ember Cub", a player's name, a number), as its chooser reads it in
     * Inspect and everyone reads it once revealed; null for a value with no words. */
    fun valueText(key: String, value: NamedValue, names: Map<String, String>, objectName: (ObjectId) -> String?): String? =
        CardStates.chosenValue(key, value, objectName) ?: when (value) {
            is NamedValue.Objects -> value.ids.mapNotNull(objectName).takeIf { it.isNotEmpty() }?.joinToString(" and ")
            is NamedValue.Players -> value.ids.map { names[it.value] ?: "a player" }.takeIf { it.isNotEmpty() }?.joinToString(" and ")
            is NamedValue.Number -> value.value.toString()
            is NamedValue.Option -> value.option
            is NamedValue.Names -> value.names.takeIf { it.isNotEmpty() }?.joinToString(" and ")
            is NamedValue.Subtypes -> value.subtypes.takeIf { it.isNotEmpty() }?.joinToString(" and ") { it.name }
            else -> null
        }
}
