package org.asyncttwiz.android

import org.asyncttwiz.protocol.AccountIdentity
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.InvitationState
import kotlin.test.Test
import kotlin.test.assertSame

// Web frame rate (owner, 2026-11-20: the desktop table stutters when plays happen): the table keeps the game object it has
// while a new one differs only in what the table never reads, so an event's new revision doesn't rebuild the table's scene
class TableGameTest {
    private val game = GameSummary("g1", "Shared Spark", "2026-10-23T00:00:00Z", AccountIdentity("a", "Alice"),
        AccountIdentity("b", "Bob"), "round", "invitation", InvitationState.ACCEPTED, GameLifecycle.ACTIVE, "5", "play1",
        attentionHolder = "a", updatedAt = "2026-10-23T00:00:05Z")

    @Test fun aNewRevisionWhoseMoveOrUpdateTimeKeepsTheGameTheTableHas() {
        val next = game.copy(revision = "6", attentionHolder = "b", updatedAt = "2026-10-23T00:00:09Z")
        assertSame(game, tableGame(game, next))
    }

    @Test fun anythingElseTheTableMayReadGivesItTheNewGame() {
        for (next in listOf(game.copy(playId = "play2"), game.copy(lifecycle = GameLifecycle.DELETED, revision = "6"),
            game.copy(recipient = AccountIdentity("b", "Bobby")), game.copy(gameName = "Renamed"), game.copy(id = "g2")))
            assertSame(next, tableGame(game, next))
    }

    @Test fun theFirstGameIsTakenAsItIs() {
        assertSame(game, tableGame(null, game))
    }
}
