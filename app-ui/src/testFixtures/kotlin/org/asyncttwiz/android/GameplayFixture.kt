package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** A small play of two players with one card on the battlefield, for the session and table tests. */
internal class GameplayFixture {
    fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    val actor = id(1); val other = id(2)
    val root = PlayRoot(id(3), id(4))
    val definition = CardDefinition("Retained", listOf(DefinitionFace(id(11), "Retained face")))
    val initial = PlayState(id(5), id(6), id(7), id(8), root,
        BoardState(listOf(PlayPlayer(actor), PlayPlayer(other)),
            listOf(PlayCard(id(9), DeckJson.digest(definition), actor, id(10), id(11),
                placement = PlayPlacement(BoardPosition(0, 0), id(40)))),
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, actor, listOf(id(9)))), TurnState(actor)),
        listOf(SubmittedDefinition(DeckJson.digest(definition), definition)), emptyList(), InitialStarter(StarterChoice(actor), actor))
    val game = GameSummary(initial.gameId, "Replica", "2026-10-23T00:00:00Z", AccountIdentity(actor, "A"),
        AccountIdentity(other, "B"), initial.preparationRound, id(12), InvitationState.ACCEPTED,
        lifecycle = GameLifecycle.ACTIVE, revision = "1", playId = initial.id)
    var sequence = 100
    fun head(state: PlayState = initial, revision: String = "1") = GameHead(game.id, revision, game.copy(revision = revision), play = state)
    fun result(command: GameCommand, revision: String? = null, outcome: String = "accepted") =
        GameResult(command.requestId, command.scope, outcome, ingressOrdinal = "1", commitRevision = revision)
    fun life(state: PlayState?) = state!!.board.players.first().life
}
