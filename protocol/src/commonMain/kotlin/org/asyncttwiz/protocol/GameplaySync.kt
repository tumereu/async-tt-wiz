package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** One coherent endpoint plus flat complete retained records. Legacy linear reads remain decodable. */
@Serializable data class GameplaySnapshot(val head: GameHead, val records: List<GameRevision>,
    val treeRecords: List<TreeSyncRecord> = emptyList())
@Serializable data class GameplayChunk(val index: Int, val bytes: Int, val digest: String)
@Serializable data class GameplayManifest(val id: String, val gameId: String, val playId: String,
    val revision: String, val chunks: List<GameplayChunk>, val statuses: List<GameRequestStatus>,
    val engineVersion: Int = 1, val readFormat: Int = 1, val digest: String = "",
    val recordCount: Int = 0, val head: GameHead? = null)
data class GameplayBaseline(val snapshot: GameplaySnapshot, val statuses: List<GameRequestStatus>)

object GameplaySync {
    const val CHUNK_BYTES = 1024 * 1024
    const val MAX_CHUNK_BYTES = 4 * 1024 * 1024
    fun digest(bytes: ByteArray) = "sha256:" + sha256(bytes)
    fun manifestDigest(manifest: GameplayManifest) = "sha256:" + DeckJson.intent("gameplay-baseline-v1",
        DeckJson.canonicalElement(GameWire.json.parseToJsonElement(GameWire.json.encodeToString(manifest.copy(digest = "")))))
    fun validate(manifest: GameplayManifest) {
        require(DeckValidation.uuid(manifest.id) && DeckValidation.uuid(manifest.gameId) && DeckValidation.uuid(manifest.playId))
        GameWire.ordinal(manifest.revision)
        require(manifest.engineVersion == 1 && manifest.readFormat == 1 && manifest.chunks.isNotEmpty())
        require(manifest.chunks.map { it.index } == manifest.chunks.indices.toList())
        require(manifest.chunks.all { it.bytes in 1..MAX_CHUNK_BYTES && DeckValidation.digest(it.digest) })
        require(manifest.digest == manifestDigest(manifest))
        require(manifest.statuses.map { it.requestId }.distinct().size == manifest.statuses.size)
        require(manifest.recordCount >= 0)
        manifest.head?.let { require(manifest.recordCount > 0 && it.gameId == manifest.gameId &&
            it.revision == manifest.revision && it.play?.id == manifest.playId) }
    }
    fun validate(snapshot: GameplaySnapshot) {
        val head = snapshot.head
        val play = requireNotNull(head.play)
        require(head.gameId == head.summary.id && head.revision == head.summary.revision)
        require(head.summary.lifecycle == GameLifecycle.ACTIVE && head.summary.playId == play.id &&
            play.gameId == head.gameId && play.preparationRound == head.summary.preparationRound &&
            play.engineVersion == 1 && play.readFormat == 1)
        GameWire.ordinal(head.revision)
        require(head.engineVersion == 1 && head.readFormat == 1 && play.root.parentId == null)
        val cards = play.board.cards
        val containers = play.board.containers
        require(cards.map { it.id }.distinct().size == cards.size && containers.map { it.id }.distinct().size == containers.size)
        val definitions = play.definitions.associateBy { it.digest }
        require(definitions.size == play.definitions.size && play.definitions.all { DeckJson.digest(it.definition) == it.digest })
        require(cards.all { card -> definitions[card.definition]?.definition?.faces?.any { it.faceId == card.faceId } == true &&
            containers.any { it.id == card.containerId && card.id in it.cards } })
        val members = containers.flatMap { it.cards }
        val effects = play.board.stackEffects
        val memberIds = cards.map { it.id } + effects.map { it.id }
        require(memberIds.distinct().size == memberIds.size && members.size == memberIds.size && members.toSet() == memberIds.toSet())
        require(effects.all { effect -> containers.singleOrNull { it.zone == PlayZone.STACK }?.cards?.contains(effect.id) == true })
        require(CombatStackRules.closeInvariants(play.board, play.board) == play.board)
        if (snapshot.treeRecords.isNotEmpty()) {
            val tree = TreeSync.decode(snapshot.treeRecords.asSequence(), head.gameId, play.id).tree
            require(tree.stateAt(tree.currentNodeId) == play)
            require(snapshot.records.isEmpty())
            return
        }
        require(snapshot.records.isNotEmpty())
        require(snapshot.records.first().play?.root == play.root)
        require(snapshot.records.first().play?.currentNodeId == play.root.id)
        var previous: GameRevision? = null
        var materialized: PlayState? = null
        for (record in snapshot.records) {
            require(record.gameId == head.gameId && record.summary.id == head.gameId &&
                record.scope.gameId == head.gameId && record.summary.revision == record.resultRevision &&
                record.summary.playId == play.id)
            record.play?.let {
                require(it.id == play.id && it.gameId == head.gameId && it.root == play.root)
                materialized = it
            }
            require(GameWire.ordinal(record.baseRevision) < GameWire.ordinal(record.resultRevision))
            previous?.let { require(record.baseRevision == it.resultRevision) }
            previous = record
        }
        require(previous!!.resultRevision == head.revision && materialized == play && previous.summary == head.summary)
    }
}
