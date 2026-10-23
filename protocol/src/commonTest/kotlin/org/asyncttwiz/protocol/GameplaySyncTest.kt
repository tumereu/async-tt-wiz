package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlin.test.*

class GameplaySyncTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun snapshot(): GameplaySnapshot {
        val root = PlayRoot(id(1), id(2))
        val play = PlayState(id(3), id(4), id(5), id(6), root, root.id,
            BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), emptyList(), emptyList(), TurnState(id(7))),
            emptyList(), emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
        val game = GameSummary(play.gameId, "Baseline", "2026-10-23T00:00:00Z", AccountIdentity(id(7), "A"),
            AccountIdentity(id(8), "B"), play.preparationRound, id(9), InvitationState.ACCEPTED,
            lifecycle = GameLifecycle.ACTIVE, revision = "9007199254740993", playId = play.id)
        val record = GameRevision(game.id, "9007199254740992", game.revision, play.startRequestId, id(7),
            GameScope(game.id, play.preparationRound), game, play = play)
        return GameplaySnapshot(GameHead(game.id, game.revision, game, play = play), listOf(record))
    }
    @Test fun typedSnapshotAndManifestRoundTripKeepLargeOrdinalsAndDetectCorruptScopeCoverageAndDescriptors() {
        val snapshot = snapshot(); GameplaySync.validate(snapshot)
        val bytes = GameWire.json.encodeToString(snapshot).encodeToByteArray()
        val manifest = GameplayManifest(id(20), snapshot.head.gameId, snapshot.head.play!!.id, snapshot.head.revision,
            listOf(GameplayChunk(0, bytes.size, GameplaySync.digest(bytes))), emptyList())
            .let { it.copy(digest = GameplaySync.manifestDigest(it)) }
        GameplaySync.validate(manifest)
        assertEquals(manifest, GameWire.json.decodeFromString<GameplayManifest>(GameWire.json.encodeToString(manifest)))
        assertEquals(snapshot, GameWire.json.decodeFromString<GameplaySnapshot>(bytes.decodeToString()))
        assertNotEquals(GameplaySync.digest(bytes), GameplaySync.digest(bytes + byteArrayOf(0)))
        assertFailsWith<IllegalArgumentException> { GameplaySync.validate(manifest.copy(revision = "9007199254740994")) }
        assertFailsWith<IllegalArgumentException> { GameplaySync.validate(manifest.copy(chunks = listOf(manifest.chunks[0].copy(index = 1)))) }
        assertFailsWith<IllegalArgumentException> { GameplaySync.validate(snapshot.copy(records = emptyList())) }
        assertFailsWith<IllegalArgumentException> { GameplaySync.validate(snapshot.copy(head = snapshot.head.copy(gameId = id(99)))) }
        assertFailsWith<IllegalArgumentException> { GameplaySync.validate(snapshot.copy(records = snapshot.records + snapshot.records)) }
    }
}
