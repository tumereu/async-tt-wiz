package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Accepted game-local data. Neither a picker draft nor an outgoing request queue. */
@Serializable data class PreparedCard(val id: String, val definition: String, val section: SavedDeckSection)
/** A compact consecutive set of concrete command-derived identities, not fungible quantities. */
@Serializable data class PreparedEntry(val definition: String, val section: SavedDeckSection,
    val firstIndex: String, val quantity: Int)
@Serializable data class PreparedDeck(val id: String, val sourceId: String, val sourceRevision: String,
    val name: String, val cover: String?, val selectionRequestId: String, val entries: List<PreparedEntry>) {
    fun count(section: SavedDeckSection) = entries.filter { it.section == section }.sumOf { it.quantity.toLong() }
    fun card(entry: PreparedEntry, offset: Int): PreparedCard {
        require(entry in entries && offset in 0 until entry.quantity)
        return PreparedCard(CommandIds.output(selectionRequestId, "prepared-card", GameWire.ordinal(entry.firstIndex) + offset),
            entry.definition, entry.section)
    }
}
@Serializable data class PlayerPreparation(val accountId: String, val ready: Boolean = false, val deck: PreparedDeck? = null)
@Serializable data class LobbyPreparation(val gameId: String, val roundId: String,
    val players: List<PlayerPreparation>, val definitions: List<SubmittedDefinition> = emptyList())

data class PreparationChange(val state: LobbyPreparation? = null, val reason: String? = null)

/** Pure selection semantics shared by durable execution and volatile provisional display. */
object PreparationRules {
    fun empty(game: GameSummary) = LobbyPreparation(game.id, game.preparationRound,
        listOf(PlayerPreparation(game.inviter.accountId), PlayerPreparation(game.recipient.accountId)))

    fun select(game: GameSummary, current: LobbyPreparation, actor: String, requestId: String,
        scope: GameScope, action: GameAction.SelectDeck, source: SavedDeck?,
        definitions: List<SubmittedDefinition>): PreparationChange {
        fun reject(reason: String) = PreparationChange(reason = reason)
        if (game.lifecycle != GameLifecycle.LOBBY) return reject("lifecycleEnded")
        if (scope.gameId != game.id || scope.preparationId != game.preparationRound || scope.playId != null ||
            current.gameId != game.id || current.roundId != game.preparationRound) return reject("inapplicable")
        if (actor != game.inviter.accountId && (actor != game.recipient.accountId || game.invitationState != InvitationState.ACCEPTED))
            return reject("accessDenied")
        if (source == null || source.deck.id != action.deckId) return reject("sourceUnavailable")
        if (source.deck.revision != action.sourceRevision) return reject("sourceChanged")
        if (!DeckValidation.create(CreateDeck(requestId, source.deck.name, source.entries))) return reject("invalidSource")
        val needed = source.entries.map { it.definition }.toSet()
        val attached = definitions.associateBy { it.digest }
        if (attached.size != definitions.size || needed.any { digest -> attached[digest]?.let {
                DeckValidation.definition(it.definition) && DeckJson.digest(it.definition) == digest
            } != true }) return reject("definitionsUnavailable")
        var index = 0L
        val entries = source.entries.map { entry ->
            PreparedEntry(entry.definition, entry.section, index.toString(), entry.quantity).also { index += entry.quantity }
        }
        val deck = PreparedDeck(CommandIds.output(requestId, "prepared-deck", 0), source.deck.id,
            source.deck.revision, source.deck.name, source.deck.cover?.definition, requestId, entries)
        val retained = (current.definitions + needed.map { attached.getValue(it) }).associateBy { it.digest }
        return PreparationChange(current.copy(players = current.players.map {
            if (it.accountId == actor) it.copy(ready = false, deck = deck) else it
        }, definitions = retained.values.sortedBy { it.digest }))
    }
}

/** UUIDv5 namespace=request UUID, role/index in captured semantic order (protocol v1). */
object CommandIds {
    fun output(requestId: String, role: String, index: Long): String {
        require(DeckValidation.uuid(requestId) && role in setOf("prepared-deck", "prepared-card") && index >= 0)
        return uuidV5(requestId, "$role/$index")
    }
    fun uuidV5(namespace: String, name: String): String {
        require(DeckValidation.uuid(namespace))
        val hex = namespace.replace("-", "")
        val bytes = ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val digest = sha1(bytes + name.encodeToByteArray()).copyOf(16)
        digest[6] = ((digest[6].toInt() and 15) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 63) or 0x80).toByte()
        val result = digest.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return "${result.take(8)}-${result.substring(8, 12)}-${result.substring(12, 16)}-${result.substring(16, 20)}-${result.substring(20)}"
    }
}

internal expect fun sha1(bytes: ByteArray): ByteArray
