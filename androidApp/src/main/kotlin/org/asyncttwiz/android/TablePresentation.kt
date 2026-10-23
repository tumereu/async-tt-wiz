package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Production scene coordinates in dp. Drawing and accessibility use these same bounds. */
data class TableBounds(val x: Float, val y: Float, val width: Float, val height: Float)
data class TableItem(val id: String, val bounds: TableBounds, val label: String,
    val value: String, val owner: String? = null, val zone: PlayZone? = null,
    val cards: List<String> = emptyList())
data class TableField(val bounds: TableBounds, val owner: String)
data class TableScene(val items: List<TableItem>, val fields: List<TableField>)

object TablePresentation {
    fun scene(game: GameSummary, play: PlayState, viewer: String, width: Float, height: Float, landscape: Boolean): TableScene {
        require(play.gameId == game.id && play.id == game.playId)
        val own = play.board.players.single { it.accountId == viewer }
        val opponent = play.board.players.single { it.accountId != viewer }
        fun name(id: String) = if (game.inviter.accountId == id) game.inviter.username else game.recipient.username
        val items = mutableListOf<TableItem>()
        val dockWidth = if (landscape) minOf(228f, width * .42f) else width
        val boardLeft = if (landscape) dockWidth else 0f
        val boardWidth = width - boardLeft
        val middle = height / 2
        val dockTop = middle - 68f
        val available = (dockWidth - 104f) / 3
        val cardWidth = minOf(48f, available).coerceAtLeast(24f)
        val fields = listOf(TableField(TableBounds(boardLeft, 28f, boardWidth,
            (if (landscape) middle - 28f else dockTop - 28f).coerceAtLeast(0f)), opponent.accountId),
            TableField(TableBounds(boardLeft, if (landscape) middle else middle + 68f, boardWidth,
                (height - 28f - if (landscape) middle else middle + 68f).coerceAtLeast(0f)), own.accountId))
        for ((index, player) in listOf(opponent, own).withIndex()) {
            val rowY = if (landscape) (height * (if (index == 0) .25f else .75f) - 32f) else dockTop + index * 72f
            val person = name(player.accountId)
            items += TableItem("life/${player.accountId}", TableBounds(4f, rowY + 12f, 40f, 40f),
                "$person, life ${player.life}", player.life, player.accountId)
            for ((column, zone) in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE).withIndex()) {
                val container = play.board.containers.single { it.accountId == player.accountId && it.zone == zone }
                val title = zone.name.lowercase().replaceFirstChar(Char::uppercase)
                items += TableItem(container.id, TableBounds(48f + column * available, rowY, cardWidth, 64f),
                    "$person $title, ${container.cards.size} cards", container.cards.size.toString(), player.accountId, zone, container.cards)
            }
            val hand = play.board.containers.single { it.accountId == player.accountId && it.zone == PlayZone.HAND }
            items += TableItem(hand.id, TableBounds(boardLeft, if (index == 0) 0f else height - 28f, boardWidth, 28f),
                "$person Hand, ${hand.cards.size} cards", "$person · Hand ${hand.cards.size}", player.accountId, PlayZone.HAND, hand.cards)
        }
        val stack = play.board.containers.single { it.zone == PlayZone.STACK }
        items += TableItem(stack.id, TableBounds(if (landscape) dockWidth - 52f else width - 52f, middle - 32f, cardWidth, 64f),
            "Stack, ${stack.cards.size} entries", stack.cards.size.toString(), zone = PlayZone.STACK, cards = stack.cards)
        return TableScene(items, fields)
    }
}
