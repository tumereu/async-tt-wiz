package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Valid retained mutations: screenshots and gesture tests share the production reconstruction. */
internal object TableStatesFixture {
    fun id(value: String) = java.util.UUID.nameUUIDFromBytes(value.toByteArray()).toString()
    val a = id("alice")
    val b = id("bob")
    val initial: PlayState = LiveTableFixture.menuPlay().let { p ->
        fun owner(value: String?) = when (value) { "a" -> a; "b" -> b; else -> value }
        val root = PlayRoot(id("root"), id("branch"))
        p.copy(gameId = id("game"), id = id("play"), preparationRound = id("round"), startRequestId = id("initial"),
            root = root, currentNodeId = root.id, currentBranchId = root.branchId,
            board = p.board.copy(players = p.board.players.map { it.copy(accountId = owner(it.accountId)!!) },
                cards = p.board.cards.map { it.copy(owner = owner(it.owner)!!, containerId = id(it.containerId)) },
                containers = p.board.containers.map { it.copy(id = id(it.id), accountId = owner(it.accountId)) },
                turn = p.board.turn.copy(accountId = a)))
    }
    val game = LiveTableFixture.game.copy(id = initial.gameId, playId = initial.id,
        inviter = AccountIdentity(a, "Alice"), recipient = AccountIdentity(b, "Bob"))
    fun source(owner: String, corner: String) = initial.board.cards.first { it.owner == owner && it.corner == corner &&
        it.containerId == id("${if (owner == a) "a" else "b"}/BATTLEFIELD") }

    fun combat(): PlayState {
        val sentinel = source(a, "1/3")
        val river = source(a, "1/2")
        val blocker = source(b, "2/3")
        val actual = initial.board.cards.first { it.containerId == id("a/GRAVEYARD") }
        return initial.copy(board = initial.board.copy(
            turn = initial.board.turn.copy(phase = "COMBAT", step = "DECLARE_ATTACKERS"),
            cards = initial.board.cards.map { when (it.id) {
                river.id -> it.copy(tapped = true)
                actual.id -> it.copy(containerId = id("stack"), covered = false)
                else -> it
            } }, containers = initial.board.containers.map { if (it.zone == PlayZone.STACK)
                it.copy(cards = listOf(id("effect"), actual.id)) else it.copy(cards = it.cards - actual.id) },
            stackEffects = listOf(StackEffect(id("effect"), river.id)),
            combat = CombatArea(id("combat"), listOf(
                CombatPair(id("pair1"), attackers = listOf(CombatOccurrence(id("attacker1"), sentinel.id)),
                    blockers = listOf(CombatOccurrence(id("blocker"), blocker.id))),
                CombatPair(id("pair2"), attackers = listOf(CombatOccurrence(id("attacker2"), river.id)))))))
    }

    fun history(): GameplayTree {
        var tree = GameplayTree(initial)
        fun append(n: Int, actor: String, action: PlayAction, boundary: String? = null) {
            val command = GameCommand(id("request$n"), GameScope(initial.gameId, playId = initial.id), GameAction.Play(action),
                id("client"), "1", continuation = boundary?.let { Continuation(it, true) })
            val change = tree.play(actor, command)
            tree = checkNotNull(change.tree) { "Fixture $n: ${change.reason}" }
        }
        append(1, a, PlayAction.SetTapped(source(a, "1/3").id, true))
        append(2, a, PlayAction.AdjustLife(a, "-1"))
        append(3, a, PlayAction.AdjustLife(a, "-1"))
        val fork = tree.currentNodeId
        append(4, b, PlayAction.ShowContents(id("b/LIBRARY"), ContentsSelection.TOP, 2, reveal = true))
        append(5, b, PlayAction.EventComment(EventReference(initial.id, tree.currentNodeId), "Two cards revealed"))
        append(6, b, PlayAction.SetTapped(source(b, "2/3").id, true), fork)
        append(7, b, PlayAction.Combat(CombatAction.Open))
        append(8, b, PlayAction.NextStep)
        append(9, a, PlayAction.Combat(CombatAction.Close(tree.stateAt(tree.currentNodeId)!!.board.combat!!.id)))
        val hand = initial.board.containers.single { it.id == id("a/HAND") }
        append(10, a, PlayAction.MoveCard(hand.cards.first(), MoveSource(hand.id),
            MoveDestination(id("a/BATTLEFIELD"), MoveInsertion.EMPTY, BoardPosition(3, 2))))
        return tree
    }
    fun playback(tree: GameplayTree = history()) = HistoryPlayback(HistoryTree(tree.initial.root.id, tree.nodes.values.toList(),
        tree.currentNodeId, "fixture", texts = historyTexts(tree, a, mapOf(a to "Alice", b to "Bob"))))
}
