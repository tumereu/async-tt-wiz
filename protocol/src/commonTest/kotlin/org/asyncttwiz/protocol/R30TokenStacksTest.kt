package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v30: a newly created token goes on top of a Battlefield pile of identical fresh tokens. */
class R30TokenStacksTest {
    private val a = gameplayId(7)
    private val b = gameplayId(8)
    private val battlefield = gameplayId(22)
    private fun token(name: String, face: Int): SubmittedDefinition {
        val definition = CardDefinition(name, listOf(DefinitionFace(gameplayId(face), name, corner = "1/1")), token = true)
        return SubmittedDefinition(DeckJson.digest(definition), definition)
    }
    private val goblin = token("Goblin", 40)
    private val soldier = token("Soldier", 41)

    private fun accepted(state: PlayState, action: PlayAction, n: Int, actor: Int = 7): PlayState {
        val change = gameplayChange(state, action, n, actor)
        val next = assertNotNull(change.state, change.reason)
        val event = assertNotNull(change.mutation)
        assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, event.transition.rulesVersion)
        assertEquals(next, GameplayHistory.apply(state, event), "recorded replay of $action")
        assertEquals(state, GameplayHistory.unapply(next, event), "exact inverse of $action")
        return next
    }
    private fun created(state: PlayState, n: Int) = state.board.cards.single { it.id == CommandIds.uuidV5(gameplayId(n), "token/0") }
    private fun card(state: PlayState, id: String) = state.board.cards.single { it.id == id }
    private fun pile(state: PlayState, of: PlayCard) = GameplayRules.members(state.board,
        state.board.containers.single { it.id == battlefield }, card(state, of.id).placement)
    private fun edit(state: PlayState, id: String, change: (PlayCard) -> PlayCard) =
        state.copy(board = state.board.copy(cards = state.board.cards.map { if (it.id == id) change(it) else it }))
    private fun moveOnto(state: PlayState, id: String, target: PlayPlacement, insertion: MoveInsertion = MoveInsertion.PILE_TOP) =
        PlayAction.MoveCard(id, MoveSource(battlefield, card(state, id).placement),
            MoveDestination(battlefield, insertion, target.position, target.pileId))

    @Test fun rulesVersionIs30() {
        assertEquals(30, TokenStacks.RULES_VERSION)
        assertTrue(CURRENT_GAMEPLAY_RULES_VERSION >= TokenStacks.RULES_VERSION)
    }

    @Test fun sameTokenGoesOnTopOfThePileAndThePileGrowsPastThree() {
        var state = accepted(gameplayFixture(), PlayAction.CreateToken(goblin), 100)
        val first = created(state, 100)
        assertEquals(BoardPosition(0, 0), first.placement!!.position)
        for (n in 101..104) state = accepted(state, PlayAction.CreateToken(goblin), n)
        val tokens = (100..104).map { created(state, it) }
        assertTrue(tokens.all { it.placement == first.placement })
        // Newest on top.
        assertEquals(tokens.reversed().map { it.id }, pile(state, first))
        // A different token starts its own pile at the next free cell.
        state = accepted(state, PlayAction.CreateToken(soldier), 105)
        assertEquals(BoardPosition(1, 0), created(state, 105).placement!!.position)
        assertEquals(listOf(created(state, 105).id), pile(state, created(state, 105)))
    }

    @Test fun rulesV29KeepsAPileOfItsOwn() {
        val state = accepted(gameplayFixture(), PlayAction.CreateToken(goblin), 100)
        val action = PlayAction.CreateToken(goblin)
        val command = GameCommand(gameplayId(101), GameScope(state.gameId, playId = state.id), GameAction.Play(action), gameplayId(80), "1")
        val old = TokenLinkRules.change(state, a, command, action, rulesVersion = 29)
        val next = assertNotNull(old.state, old.reason)
        assertEquals(BoardPosition(1, 0), created(next, 101).placement!!.position)
        assertEquals(CommandIds.uuidV5(gameplayId(101), "pile/0"), created(next, 101).placement!!.pileId)
        assertEquals(29, old.mutation!!.transition.rulesVersion)
    }

    @Test fun visiblyDifferentPilesAreNotJoined() {
        val root = accepted(gameplayFixture(), PlayAction.CreateToken(goblin), 100)
        val id = created(root, 100).id
        val variants = listOf<(PlayState) -> PlayState>(
            { edit(it, id) { c -> c.copy(tapped = true) } },
            { edit(it, id) { c -> c.copy(counters = mapOf(CounterColor.RED to "1")) } },
            { edit(it, id) { c -> c.copy(note = "flying") } },
            { edit(it, id) { c -> c.copy(damage = "2") } },
            { edit(it, id) { c -> c.copy(eotCorner = "3/3") } },
            { edit(it, id) { c -> c.copy(corner = "2/2") } },
            { edit(it, id) { c -> c.copy(covered = true, explicitlyCovered = true) } },
            { edit(it, id) { c -> c.copy(token = false) } },
            // Entered on an earlier turn: the new one would show "entered this turn" and the others not.
            { edit(it, id) { c -> c.copy(enteredTurn = "0") } },
            { accepted(it, PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, id), LinkEndpoint(LinkEndpointKind.PLAYER, b)), 150) },
            { s -> accepted(s, PlayAction.Combat(CombatAction.Open), 150).let { open ->
                accepted(open, PlayAction.Combat(CombatAction.Declare(open.board.combat!!.id, id)), 151) } })
        for ((n, variant) in variants.withIndex()) {
            val state = variant(root)
            val next = gameplayChange(state, PlayAction.CreateToken(goblin), 200)
            val token = created(assertNotNull(next.state, "variant $n: ${next.reason}"), 200)
            assertNotEquals(created(root, 100).placement!!.position, token.placement!!.position, "variant $n")
            assertEquals(listOf(token.id), pile(next.state, token), "variant $n")
        }
    }

    @Test fun aPileWithAnyOtherCardIsNotJoined() {
        val root = accepted(gameplayFixture(), PlayAction.CreateToken(goblin), 100)
        val withCard = accepted(root, PlayAction.MoveCard(gameplayId(10), MoveSource(gameplayId(20), edge = MoveSourceEdge.TOP),
            MoveDestination(battlefield, MoveInsertion.EMPTY, BoardPosition(3, 0))), 101)
        val real = card(withCard, gameplayId(10))
        val mixed = accepted(withCard, moveOnto(withCard, created(withCard, 100).id, real.placement!!), 102)
        val next = accepted(mixed, PlayAction.CreateToken(goblin), 103)
        assertEquals(listOf(created(next, 103).id), pile(next, created(next, 103)))
    }

    @Test fun ofSeveralPilesTheLargestThenTheFirstInReadingOrder() {
        var state = accepted(gameplayFixture(), PlayAction.CreateToken(goblin), 100)
        state = accepted(state, PlayAction.CreateToken(soldier), 101)
        // Make the second pile a Goblin too: two equal one-card Goblin piles at (0,0) and (1,0).
        state = edit(state, created(state, 101).id) { it.copy(definition = goblin.digest, faceId = created(state, 100).faceId) }
        val first = created(state, 100).placement!!
        val second = created(state, 101).placement!!
        state = accepted(state, PlayAction.CreateToken(goblin), 102)
        assertEquals(first, created(state, 102).placement)
        // Move that token onto the second pile: now it is the larger one and wins.
        state = accepted(state, moveOnto(state, created(state, 102).id, second), 103)
        state = accepted(state, PlayAction.CreateToken(goblin), 104)
        assertEquals(second, created(state, 104).placement)
        assertEquals(3, pile(state, created(state, 104)).size)
    }

    @Test fun cloneJoinsWhenItLooksTheSameAndNotWhenItCopiesATappedToken() {
        var state = accepted(gameplayFixture(), PlayAction.CreateToken(goblin), 100)
        val source = created(state, 100)
        state = accepted(state, PlayAction.CloneToken(source.id), 101)
        assertEquals(source.placement, created(state, 101).placement)
        assertEquals(listOf(created(state, 101).id, source.id), pile(state, source))
        // A tapped copy shows differently from the untapped pile.
        val tapped = accepted(state, PlayAction.SetTapped(source.id, true), 102)
        val copy = accepted(tapped, PlayAction.CloneToken(source.id), 103)
        assertTrue(created(copy, 103).tapped)
        assertNotEquals(source.placement!!.position, created(copy, 103).placement!!.position)
    }

    @Test fun aWholeTokenPileMovesPastThreeButOtherPilesStayAtThree() {
        var state = gameplayFixture()
        for (n in 100..104) state = accepted(state, PlayAction.CreateToken(goblin), n)
        val ids = pile(state, created(state, 100))
        assertEquals(5, ids.size)
        // Drag the whole pile to another cell: the first card opens a pile, the rest go under it.
        state = accepted(state, PlayAction.MoveCard(ids[0], MoveSource(battlefield, card(state, ids[0]).placement),
            MoveDestination(battlefield, MoveInsertion.EMPTY, BoardPosition(4, 1))), 110)
        for ((n, id) in ids.drop(1).withIndex())
            state = accepted(state, moveOnto(state, id, card(state, ids[0]).placement!!, MoveInsertion.PILE_BOTTOM), 111 + n)
        assertEquals(ids, pile(state, card(state, ids[0])))
        // Under rules v29 a fourth card is refused.
        val out = accepted(state, PlayAction.MoveCard(ids[3], MoveSource(battlefield, card(state, ids[3]).placement),
            MoveDestination(battlefield, MoveInsertion.EMPTY, BoardPosition(0, 2))), 120)
        val target = card(out, ids[0]).placement!!
        val back = moveOnto(out, ids[3], target, MoveInsertion.PILE_BOTTOM)
        val command = GameCommand(gameplayId(121), GameScope(out.gameId, playId = out.id), GameAction.Play(back), gameplayId(80), "1")
        assertEquals("movementConflict", GameplayRules.move(out, a, command, back, rulesVersion = 29).reason)
        val again = accepted(out, back, 121)
        assertEquals(5, pile(again, card(again, ids[0])).size)
        // Another kind of card cannot join a pile past three.
        val mixed = accepted(again, PlayAction.CreateToken(soldier), 130)
        assertEquals("movementConflict", gameplayChange(mixed, moveOnto(mixed, created(mixed, 130).id, target), 131).reason)
    }

    // Undo: allowed iff later events' concerned tuples are unchanged.
    private var request = 2000
    private fun play(tree: GameplayTree, by: String, action: PlayAction): GameplayTree {
        val change = HistoryContinuation.change(tree, by, GameCommand(gameplayId(request++),
            GameScope(tree.initial.gameId, playId = tree.initial.id), GameAction.Play(action), gameplayId(80), "1"))
        return assertNotNull(change.tree, "$action: ${change.reason}")
    }
    private fun undo(tree: GameplayTree, by: String = a) = HistoryContinuation.change(tree, by,
        GameCommand(gameplayId(request++), GameScope(tree.initial.gameId, playId = tree.initial.id), GameAction.Undo, gameplayId(80), "1"))
    private fun state(tree: GameplayTree) = assertNotNull(tree.stateAt(tree.currentNodeId))

    @Test fun undoTakesTheStackedTokenOffEvenAfterTheOtherPlayerTapsTheOneBelow() {
        var tree = play(GameplayTree(gameplayFixture()), a, PlayAction.CreateToken(goblin))
        val first = state(tree).board.cards.single { it.token }
        tree = play(tree, a, PlayAction.CreateToken(goblin))
        assertEquals(2, pile(state(tree), first).size)
        val undone = undo(tree).let { assertNotNull(it.tree, it.reason) }
        assertEquals(listOf(first.id), pile(state(undone), first))
        // A later tap of the card below does not depend on the stacked token.
        tree = play(tree, b, PlayAction.SetTapped(first.id, true))
        val allowed = undo(tree).let { assertNotNull(it.tree, it.reason) }
        assertEquals(listOf(first.id), pile(state(allowed), first))
        assertTrue(card(state(allowed), first.id).tapped)
    }
}
