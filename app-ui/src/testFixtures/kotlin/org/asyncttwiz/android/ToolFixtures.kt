package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

internal object TableToolFixture {
    fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    fun play(position: TurnPosition = TurnPosition.UNTAP, number: String = "1"): PlayState {
        val turnPlayer = id(7)
        val containers = listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, turnPlayer, listOf(id(20), id(21))),
            PlayContainer(id(11), PlayZone.LIBRARY, turnPlayer, listOf(id(22))),
            PlayContainer(id(12), PlayZone.HAND, turnPlayer, listOf(id(23), id(24))),
            PlayContainer(id(13), PlayZone.BATTLEFIELD, id(8)), PlayContainer(id(14), PlayZone.LIBRARY, id(8)),
            PlayContainer(id(15), PlayZone.HAND, id(8)), PlayContainer(id(16), PlayZone.STACK, null))
        val cards = listOf(
            // A card originally owned by the OTHER player is still in the tracked player's field.
            PlayCard(id(20), "def", id(8), id(10), id(25), tapped = true, placement = PlayPlacement(BoardPosition(4, 2), id(30))),
            PlayCard(id(21), "def", id(7), id(10), id(25), placement = PlayPlacement(BoardPosition(2, 1), id(31))))
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)),
            BoardState(listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), cards, containers,
                TurnState(turnPlayer, number, position.phase, position.step)), emptyList(), emptyList(),
            InitialStarter(StarterChoice(turnPlayer), turnPlayer))
    }
}

internal class RecordingRoutes : TableActionRoutes {
    val intents = mutableListOf<TableActionIntent>()
    override fun combat(intent: TableActionIntent.Combat) { intents += intent }
    override fun ending(intent: TableActionIntent.Ending) { intents += intent }
}

internal object ChangeTargetFixture {
    fun scene(play: PlayState) = TablePresentation.scene(LiveTableFixture.game, tableBoard(play), "a", 412f, 892f, false)

    fun stack(projection: Boolean, covered: Boolean = false): Pair<PlayState, String> {
        val play = LiveTableFixture.menuPlay()
        val card = play.board.cards.first { it.containerId == "a/BATTLEFIELD" }
        val id = if (projection) "stack-effect" else card.id
        return play.copy(board = play.board.copy(
            cards = play.board.cards.map { if (it.id == card.id) it.copy(covered = covered,
                explicitlyCovered = covered, containerId = if (projection) it.containerId else "stack") else it },
            containers = play.board.containers.map {
                when { it.zone == PlayZone.STACK -> it.copy(cards = listOf(id))
                    !projection -> it.copy(cards = it.cards - card.id)
                    else -> it }
            },
            stackEffects = if (projection) listOf(StackEffect(id, card.id, controller = "b", note = "effect note")) else emptyList()
        )) to id
    }
}
