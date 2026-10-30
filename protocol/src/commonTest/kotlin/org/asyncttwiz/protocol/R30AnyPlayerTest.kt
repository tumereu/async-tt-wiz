package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v30: either player acts on the other's panels and Hand; hidden faces stay hidden from the actor. */
class R30AnyPlayerTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7)
    private val b = id(8)
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private var request = 100
    private val older = AnyPlayer.RULES_VERSION - 1

    /** a's Library (id 10) holds cards 20..24 and a's Hand (id 11) holds cards 30..33. */
    private fun root(): PlayState {
        val definition = CardDefinition("Wizard", listOf(DefinitionFace(id(40), "Wizard", corner = "2/2")))
        val library = (20..24).map { PlayCard(id(it), "definition", a, id(10), id(40)) }
        val hand = (30..33).map { PlayCard(id(it), "definition", a, id(11), id(40)) }
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), library + hand,
                listOf(PlayContainer(id(10), PlayZone.LIBRARY, a, library.map { it.id }), PlayContainer(id(11), PlayZone.HAND, a, hand.map { it.id }),
                    PlayContainer(id(12), PlayZone.GRAVEYARD, a), PlayContainer(id(13), PlayZone.EXILE, a),
                    PlayContainer(id(14), PlayZone.BATTLEFIELD, a), PlayContainer(id(15), PlayZone.LIBRARY, b),
                    PlayContainer(id(16), PlayZone.HAND, b), PlayContainer(id(17), PlayZone.GRAVEYARD, b)), TurnState(a)),
            listOf(SubmittedDefinition("definition", definition)), emptyList(), InitialStarter(StarterChoice(a), a))
    }
    private fun command(action: PlayAction, state: PlayState) = GameCommand(id(request++),
        GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(90), "1")
    private fun change(state: PlayState, action: PlayAction, actor: String) = GameplayRules.change(state, actor, command(action, state))
    private fun step(state: PlayState, action: PlayAction, actor: String): GameplayChange {
        val result = change(state, action, actor)
        assertEquals("accepted", result.outcome, "$action: ${result.reason}")
        val mutation = assertNotNull(result.mutation)
        val next = assertNotNull(result.state)
        assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, mutation.transition.rulesVersion)
        val wire = GameWire.json.decodeFromString(GameplayMutation.serializer(), GameWire.json.encodeToString(GameplayMutation.serializer(), mutation))
        assertEquals(next, GameplayHistory.apply(state, wire), action.toString())
        assertEquals(state, GameplayHistory.unapply(next, wire), action.toString())
        assertEquals(result, GameplayHistory.replay(state, wire))
        return result
    }
    private fun seenBy(state: PlayState, viewer: String) =
        state.board.cards.filter { CardVisibility.isVisibleFaceUp(state.board, it, viewer) }.map { it.id }.toSet()

    @Test fun anyPlayerRulesAreVersionThirty() {
        assertEquals(30, AnyPlayer.RULES_VERSION)
        assertTrue(AnyPlayer.RULES_VERSION <= CURRENT_GAMEPLAY_RULES_VERSION)
    }

    @Test fun theOtherPlayerMovesAPrivateLookCardIntoTheOwnersHandBlind() {
        val look = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.TOP, 3), a).state!!
        val display = look.board.contentsDisplays.single()
        val card = display.cardIds.first()
        val move = PlayAction.MoveCard(card, MoveSource(id(10)), MoveDestination(id(11), MoveInsertion.UNORDERED))
        val moved = step(look, move, b)
        val next = moved.state!!
        assertEquals(id(11), next.board.cards.single { it.id == card }.containerId)
        assertEquals(display.cardIds - card, next.board.contentsDisplays.single().cardIds)
        // The mover never sees it: no disclosure to them, not visible on the board afterwards.
        assertTrue(moved.mutation!!.transition.disclosures.none { b in it.viewers })
        assertFalse(moved.mutation.revealsInformation)
        assertTrue(seenBy(next, b).isEmpty())
        assertTrue(card in seenBy(next, a))
        // Older rules keep panel cards author-only.
        assertEquals("accessDenied", GameplayRules.move(look, b, command(move, look), move, rulesVersion = older).reason)
    }

    @Test fun theOtherPlayerReordersAndBlindlyRevealsCardsOfAPrivateLookPanel() {
        val look = step(root(), PlayAction.ShowContents(id(10), ContentsSelection.TOP, 3), a).state!!
        val display = look.board.contentsDisplays.single()
        val reorder = PlayAction.ReorderContents(display.id, display.cardIds.last(), display.cardIds.first())
        val reordered = step(look, reorder, b)
        assertEquals(listOf(display.cardIds[2], display.cardIds[0], display.cardIds[1]),
            reordered.state!!.board.contentsDisplays.single().cardIds)
        assertTrue(reordered.mutation!!.transition.disclosures.isEmpty())
        assertTrue(seenBy(reordered.state, b).isEmpty())
        // A blind Reveal shows that card to both players, nothing else.
        val card = display.cardIds[1]
        val reveal = PlayAction.RevealContentsCard(display.id, card)
        val revealed = step(look, reveal, b)
        assertEquals(listOf(a, b).sorted(), revealed.mutation!!.transition.disclosures.single().viewers.sorted())
        assertEquals(setOf(card), seenBy(revealed.state!!, b))
        assertEquals("noOp", change(revealed.state, reveal, b).outcome)
        assertEquals("noOp", change(revealed.state, reveal, a).outcome)
        for (action in listOf(reorder, reveal)) assertEquals("accessDenied", LibraryRules.change(look, b, command(action, look), action,
            rulesVersion = older).reason)
    }

    @Test fun theOtherPlayerDiscardsFromARevealedHand() {
        // Thoughtseize: the owner reveals their hand and the other player picks the card to discard.
        val hand = root().board.containers.single { it.id == id(11) }.cards
        val revealed = step(root(), PlayAction.RevealHand(id(11), hand), a).state!!
        val discard = PlayAction.MoveCard(hand[2], MoveSource(id(11)), MoveDestination(id(12), MoveInsertion.UNORDERED))
        val discarded = step(revealed, discard, b).state!!
        assertEquals(listOf(hand[2]), discarded.board.containers.single { it.id == id(12) }.cards)
        assertEquals(hand - hand[2], discarded.board.contentsDisplays.single().cardIds)
        assertEquals("accessDenied", GameplayRules.move(revealed, b, command(discard, revealed), discard, rulesVersion = older).reason)
    }

    @Test fun theOtherPlayerRevealsFromAndPicksAtRandomFromTheOwnersHand() {
        val state = root()
        val hand = state.board.containers.single { it.id == id(11) }.cards
        val reveal = PlayAction.RevealHand(id(11), listOf(hand[1]))
        val shown = step(state, reveal, b)
        val display = shown.state!!.board.contentsDisplays.single()
        assertEquals(b, display.author)
        assertEquals(setOf(hand[1]), seenBy(shown.state, b))
        assertEquals("accessDenied", HandRules.change(state, b, command(reveal, state), reveal, rulesVersion = older).reason)
        // "Target opponent discards at random", done by the other player: the card goes to its owner's Graveyard.
        val random = HandActions.random(state.board, id(11), emptyList(), seed, HandRandomResult.DISCARD)
        val discarded = step(state, random, b).state!!
        assertEquals(listOf(random.cardId), discarded.board.containers.single { it.id == id(12) }.cards)
        assertTrue(discarded.board.containers.single { it.id == id(17) }.cards.isEmpty())
        assertEquals("accessDenied", HandRules.change(state, b, command(random, state), random, rulesVersion = older).reason)
        val exiled = step(state, HandActions.random(state.board, id(11), listOf(hand[0], hand[3]), seed, HandRandomResult.EXILE), b).state!!
        assertEquals(1, exiled.board.containers.single { it.id == id(13) }.cards.size)
    }

    @Test fun theOtherPlayerReordersTheOwnersHandWithoutSeeingIt() {
        val state = root()
        val hand = state.board.containers.single { it.id == id(11) }.cards
        val move = PlayAction.MoveCard(hand[3], MoveSource(id(11)), MoveDestination(id(11), MoveInsertion.GAP,
            afterId = null, beforeId = hand[0]))
        val reordered = step(state, move, b)
        assertEquals(listOf(hand[3]) + hand.take(3), reordered.state!!.board.containers.single { it.id == id(11) }.cards)
        assertTrue(reordered.mutation!!.transition.disclosures.isEmpty())
        assertTrue(seenBy(reordered.state, b).isEmpty())
        assertEquals("inapplicable", GameplayRules.move(state, b, command(move, state), move, rulesVersion = older).reason)
    }
}
