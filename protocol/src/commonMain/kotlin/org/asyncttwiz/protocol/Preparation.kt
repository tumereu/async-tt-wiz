package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Accepted game-local data. Neither a picker draft nor an outgoing request queue. */
@Serializable data class PreparedCard(val id: String, val definition: String, val section: SavedDeckSection)
/** A compact consecutive set of concrete command-derived identities, not fungible quantities. */
@Serializable data class PreparedEntry(val definition: String, val section: SavedDeckSection,
    val firstIndex: String, val quantity: Int, val suppliedBasic: BasicLand? = null)
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class PreparedDeck(val id: String, val sourceId: String, val sourceRevision: String,
    val name: String, val cover: String?, val selectionRequestId: String, val entries: List<PreparedEntry>,
    val basicDefinitions: Map<BasicLand, String> = emptyMap(), val nextCardIndex: String = "0",
    /** The definitions (digests) of the cards its player owns outside the game besides the deck: the dungeon cards the server
     * brought with their Ready ([org.asyncttwiz.protocol.game.OutsideCards], CR 309.2). Omitted when none. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val outside: List<String> = emptyList()) {
    fun count(section: SavedDeckSection) = entries.filter { it.section == section }.sumOf { it.quantity.toLong() }
    fun card(entry: PreparedEntry, offset: Int): PreparedCard {
        require(entry in entries && offset in 0 until entry.quantity)
        return PreparedCard(CommandIds.output(selectionRequestId, "prepared-card", GameWire.ordinal(entry.firstIndex) + offset),
            entry.definition, entry.section)
    }
}
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class PlayerPreparation(val accountId: String, val ready: Boolean = false, val deck: PreparedDeck? = null,
    /** The player shows this game's deck name and cover to the opponent ([GameAction.ShowDeck]). Omitted when false. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val deckShown: Boolean = false,
    /** Pod games only, while [deckShown]: the player's private pod deck name, filled by the server. Omitted otherwise. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val shownPodDeckName: String? = null)
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class LobbyPreparation(val gameId: String, val roundId: String,
    val players: List<PlayerPreparation>, val definitions: List<SubmittedDefinition> = emptyList(),
    val starter: StarterChoice = StarterChoice(), val podRules: PodGameRules? = null,
    /** After each play, [starter] becomes the player who did not start it (the resolved starter, not the choice). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val alternateStarter: Boolean = true)

/** Personal captured gesture intent; compact even for large rows. Never a bulk server command. */
data class PreparedTransfer(val gameId: String, val roundId: String, val deckId: String,
    val selectionRequestId: String, val source: SavedDeckSection, val entries: List<PreparedEntry>) {
    val count get() = entries.sumOf { it.quantity.toLong() }
    fun action(entry: PreparedEntry, offset: Int) = (GameWire.ordinal(entry.firstIndex) + offset).let { index ->
        GameAction.TransferPreparedCard(deckId, CommandIds.output(selectionRequestId, "prepared-card", index), index.toString(), source)
    }
}

data class PreparationChange(val state: LobbyPreparation? = null, val reason: String? = null)

/** Pure selection semantics shared by durable execution and volatile provisional display. */
object PreparationRules {
    fun empty(game: GameSummary) = LobbyPreparation(game.id, game.preparationRound,
        listOf(PlayerPreparation(game.inviter.accountId), PlayerPreparation(game.recipient.accountId)))

    fun capture(current: LobbyPreparation, actor: String, definition: String, section: SavedDeckSection,
        all: Boolean): PreparedTransfer? {
        val deck = current.players.find { it.accountId == actor }?.deck ?: return null
        // Preparation moves cards between Main and Side only (LOB-048); Maybe stays as it is.
        if (section !in SavedDeckSection.PREPARED) return null
        // A pod game's supplied basics are cards like any other here (DRF-124).
        val entries = deck.entries.filter { it.definition == definition && it.section == section }
        if (entries.isEmpty()) return null
        return PreparedTransfer(current.gameId, current.roundId, deck.id, deck.selectionRequestId, section,
            if (all) entries.toList() else listOf(entries.first().copy(quantity = 1)))
    }

    fun transfer(game: GameSummary, current: LobbyPreparation, actor: String, scope: GameScope,
        action: GameAction.TransferPreparedCard): PreparationChange {
        fun reject(reason: String) = PreparationChange(reason = reason)
        if (game.lifecycle != GameLifecycle.LOBBY) return reject("lifecycleEnded")
        if (scope.gameId != game.id || scope.preparationId != game.preparationRound || scope.playId != null ||
            current.gameId != game.id || current.roundId != game.preparationRound) return reject("inapplicable")
        if (actor != game.inviter.accountId && (actor != game.recipient.accountId || game.invitationState != InvitationState.ACCEPTED))
            return reject("accessDenied")
        val deck = current.players.find { it.accountId == actor }?.deck ?: return reject("preparedDeckChanged")
        if (deck.id != action.deckId) return reject("preparedDeckChanged")
        val index = runCatching { GameWire.ordinal(action.cardIndex) }.getOrNull() ?: return reject("inapplicable")
        if (action.cardId != CommandIds.output(deck.selectionRequestId, "prepared-card", index)) return reject("copyUnavailable")
        val entry = deck.entries.find { index >= GameWire.ordinal(it.firstIndex) &&
            index - GameWire.ordinal(it.firstIndex) < it.quantity } ?: return reject("copyUnavailable")
        if (entry.section != action.source) return reject("sourceSectionChanged")
        if (action.source !in SavedDeckSection.PREPARED) return reject("inapplicable")
        val captured = PreparedTransfer(game.id, current.roundId, deck.id, deck.selectionRequestId, action.source,
            listOf(entry.copy(firstIndex = index.toString(), quantity = 1)))
        return PreparationChange(previewTransfer(current, actor, captured))
    }

    /** Same range membership update as an individual transfer, used only for unsent captured display. */
    fun previewTransfer(current: LobbyPreparation, actor: String, captured: PreparedTransfer): LobbyPreparation {
        if (current.gameId != captured.gameId || current.roundId != captured.roundId) return current
        var moved = false
        val players = current.players.map { player ->
            val deck = player.deck
            if (player.accountId != actor || deck == null || deck.id != captured.deckId) player else {
                var entries = deck.entries
                for (range in captured.entries) {
                    val start = GameWire.ordinal(range.firstIndex); val end = start + range.quantity
                    entries = entries.flatMap { entry ->
                        val first = GameWire.ordinal(entry.firstIndex); val last = first + entry.quantity
                        val low = maxOf(first, start); val high = minOf(last, end)
                        if (entry.section != captured.source || entry.definition != range.definition || low >= high) listOf(entry)
                        else {
                            moved = true
                            buildList {
                                if (first < low) add(entry.copy(quantity = (low - first).toInt()))
                                add(entry.copy(firstIndex = low.toString(), quantity = (high - low).toInt(),
                                    section = if (captured.source == SavedDeckSection.MAINBOARD) SavedDeckSection.SIDEBOARD else SavedDeckSection.MAINBOARD))
                                if (high < last) add(entry.copy(firstIndex = high.toString(), quantity = (last - high).toInt()))
                            }
                        }
                    }
                }
                // Canonical contiguous runs keep identity order and avoid permanent per-move fragmentation.
                val compact = mutableListOf<PreparedEntry>()
                for (entry in entries.sortedBy { GameWire.ordinal(it.firstIndex) }) {
                    val previous = compact.lastOrNull()
                    if (previous != null && previous.definition == entry.definition && previous.section == entry.section && previous.suppliedBasic == entry.suppliedBasic &&
                        GameWire.ordinal(previous.firstIndex) + previous.quantity == GameWire.ordinal(entry.firstIndex) &&
                        previous.quantity.toLong() + entry.quantity <= Int.MAX_VALUE)
                        compact[compact.lastIndex] = previous.copy(quantity = previous.quantity + entry.quantity)
                    else compact.add(entry)
                }
                player.copy(ready = if (moved) false else player.ready, deck = deck.copy(entries = compact))
            }
        }
        return if (moved) current.copy(players = players) else current
    }

    fun select(game: GameSummary, current: LobbyPreparation, actor: String, requestId: String,
        scope: GameScope, action: GameAction.SelectDeck, source: SavedDeck?,
        definitions: List<SubmittedDefinition>): PreparationChange {
        fun reject(reason: String) = PreparationChange(reason = reason)
        if (game.lifecycle != GameLifecycle.LOBBY) return reject("lifecycleEnded")
        if (scope.gameId != game.id || scope.preparationId != game.preparationRound || scope.playId != null ||
            current.gameId != game.id || current.roundId != game.preparationRound) return reject("inapplicable")
        if (actor != game.inviter.accountId && (actor != game.recipient.accountId || game.invitationState != InvitationState.ACCEPTED))
            return reject("accessDenied")
        if (game.podId != null || current.podRules != null) return reject("podDeckFixed")
        if (source == null || source.deck.id != action.deckId) return reject("sourceUnavailable")
        if (source.deck.revision != action.sourceRevision) return reject("sourceChanged")
        if (!DeckValidation.text(source.deck.name) || source.deck.name.isBlank() || !DeckValidation.entries(source.entries))
            return reject("invalidSource")
        // LOB-141: Maybe comes along too (outside the game with Side), after Main and Side.
        val prepared = source.entries.filter { it.section in SavedDeckSection.PREPARED } +
            source.entries.filter { it.section !in SavedDeckSection.PREPARED }
        // [definitions]: the deck's cards built from the card catalog by their printings (owner 2026-11-10), each the
        // physical card its front face's printing is a face of; a card without a printing has none (LOB-115)
        val cards = definitions.filter { DeckValidation.definition(it.definition) && DeckJson.digest(it.definition) == it.digest }
            .associateBy { it.definition.printingId }
        if (prepared.any { entry -> entry.printingId == null || entry.printingId !in cards }) return reject("definitionsUnavailable")
        var index = 0L
        val entries = prepared.map { entry ->
            PreparedEntry(cards.getValue(entry.printingId).digest, entry.section, index.toString(), entry.quantity).also { index += entry.quantity }
        }
        val used = prepared.map { cards.getValue(it.printingId) }
        val deck = PreparedDeck(CommandIds.output(requestId, "prepared-deck", 0), source.deck.id,
            source.deck.revision, source.deck.name, source.deck.cover?.definition?.let { cards[it]?.digest }, requestId, entries)
        val retained = (current.definitions + used).associateBy { it.digest }
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
