package org.asyncttwiz.protocol

import kotlin.test.*
import kotlinx.serialization.encodeToString

class PlayTest {
    private fun id(index: Int) = "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}"
    private val a = AccountIdentity(id(1), "A"); private val b = AccountIdentity(id(2), "B")
    private val game = GameSummary(id(3), "Initial Spark", "2026-10-23T00:00:00Z", a, b, id(4), id(5), InvitationState.ACCEPTED)
    private val scope = GameScope(game.id, game.preparationRound)
    private val front = DefinitionFace(id(6), "Front", corner = "2/4")
    private val definition = CardDefinition("Retained card", listOf(front), sourceVersion = "old")
    private val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    private fun prepared(account: String, request: String) = PlayerPreparation(account, deck = PreparedDeck(id(7), id(8), "1",
        "Prepared", null, request, listOf(PreparedEntry(submitted.digest, SavedDeckSection.MAINBOARD, "0", 7),
            PreparedEntry(submitted.digest, SavedDeckSection.SIDEBOARD, "7", 2))))
    private fun lobby() = LobbyPreparation(game.id, game.preparationRound,
        listOf(prepared(a.accountId, id(9)), prepared(b.accountId, id(10))), listOf(submitted), StarterChoice(a.accountId))
    private fun change(state: LobbyPreparation, actor: String, action: GameAction, summary: GameSummary = game) =
        StartRules.change(summary, state, actor, scope, action)
    private fun ready() = lobby().copy(players = lobby().players.map { it.copy(ready = true) })
    private val seeds = mapOf(a.accountId to RandomGolden.vectors[0].seed, b.accountId to RandomGolden.vectors[1].seed)

    @Test fun selectedDeckOnlyNotSizeLegalityOrOpponentPresenceAndAcceptanceIsNotReadiness() {
        val pending = game.copy(invitationState = InvitationState.PENDING)
        val state = lobby().copy(players = listOf(lobby().players[0], PlayerPreparation(b.accountId)))
        val first = change(state, a.accountId, GameAction.Ready, pending).state!!
        assertTrue(first.players[0].ready); assertFalse(first.players[1].ready); assertFalse(StartRules.canStart(pending, first))
        assertFalse(StartRules.canStart(game, first)) // Acceptance alone does not Ready the recipient.
        assertEquals("deckRequired", change(first, b.accountId, GameAction.Ready).reason)
        assertEquals("accessDenied", change(first, b.accountId, GameAction.Ready, pending).reason)
        val sideOnly = state.players[0].deck!!.copy(entries = state.players[0].deck!!.entries.filter { it.section == SavedDeckSection.SIDEBOARD })
        assertTrue(change(state.copy(players = listOf(state.players[0].copy(deck = sideOnly), state.players[1])),
            a.accountId, GameAction.Ready, pending).state!!.players[0].ready)
        assertEquals(StarterChoice(), PreparationRules.empty(game).startingChoice)
        assertEquals(StarterChoice(), lobby().copy(starter = null).startingChoice)
        assertTrue(change(lobby().copy(starter = null), a.accountId, GameAction.Ready).state!!.players[0].ready)
    }

    @Test fun actualStarterChangeAndWithdrawalClearOnlyActorAndCannotReopenPlay() {
        val both = ready()
        val changed = change(both, b.accountId, GameAction.SetStarter(StarterChoice())).state!!
        assertTrue(changed.players[0].ready); assertFalse(changed.players[1].ready)
        assertEquals(StarterChoice(), changed.starter); assertFalse(StartRules.canStart(game, changed))
        assertEquals(both, change(both, a.accountId, GameAction.SetStarter(both.starter!!)).state)
        val withdrawn = change(both, a.accountId, GameAction.WithdrawReady).state!!
        assertFalse(withdrawn.players[0].ready); assertTrue(withdrawn.players[1].ready)
        assertEquals("inapplicable", change(both, a.accountId, GameAction.SetStarter(StarterChoice(id(99)))).reason)
        assertEquals("accessDenied", change(both, id(99), GameAction.Ready).reason)
        for (action in listOf(GameAction.Ready, GameAction.WithdrawReady, GameAction.SetStarter(StarterChoice()))) {
            assertEquals("lifecycleEnded", change(both, a.accountId, action, game.copy(lifecycle = GameLifecycle.ACTIVE)).reason)
            assertEquals("inapplicable", StartRules.change(game, both, a.accountId, scope.copy(preparationId = id(99)), action).reason)
            assertEquals("inapplicable", StartRules.change(game, both, a.accountId, scope.copy(playId = id(99)), action).reason)
        }
    }

    @Test fun sharedInitialBoardPreservesConcreteCopiesDefaultsAndDefinitionsExcludesSideboardAndNeverDraws() {
        val preparation = ready()
        val original = preparation.copy()
        val state = StartRules.initialize(game, preparation, id(11), seeds)
        assertEquals(game.id, state.gameId); assertEquals(game.preparationRound, state.preparationRound)
        assertNotEquals(game.id, state.id); assertNotEquals(state.id, state.root.id)
        assertNull(state.root.parentId); assertEquals(state.root.id, state.currentNodeId)
        assertEquals(TurnState(a.accountId), state.board.turn)
        assertEquals(listOf("20", "20"), state.board.players.map { it.life })
        // Every new play starts with both players mulliganing (rules v26), until they set another status.
        assertEquals(listOf(PlayerStatus.OPENING, PlayerStatus.OPENING), state.board.players.map { it.status })
        assertEquals("Mulliganing", PlayerStatus.OPENING)
        assertEquals(listOf("RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "GRAY"), CounterColor.entries.map { it.name })
        assertEquals(listOf(submitted), state.definitions)
        assertEquals(14, state.board.cards.size)
        assertTrue(state.board.cards.all { it.covered && !it.tapped && !it.token && it.corner == "2/4" &&
            it.faceId == front.faceId && it.counters.isEmpty() && it.note.isEmpty() })
        assertEquals(11, state.board.containers.size)
        assertTrue(state.board.containers.filter { it.zone != PlayZone.LIBRARY }.all { it.cards.isEmpty() })
        for (player in ready().players) {
            val deck = player.deck!!
            val expected = deck.entries.filter { it.section == SavedDeckSection.MAINBOARD }.flatMap { entry ->
                List(entry.quantity) { deck.card(entry, it).id } }.toSet()
            val library = state.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == player.accountId }
            assertEquals(expected, library.cards.toSet())
            assertEquals(expected, state.board.cards.filter { it.owner == player.accountId && it.containerId == library.id }.map { it.id }.toSet())
            assertEquals(library.cards, state.shuffles.single { it.accountId == player.accountId }.order)
        }
        assertEquals(original, preparation) // Input is immutable; no preparation or source reset.
        val head = GameHead(game.id, "5", game.copy(lifecycle = GameLifecycle.ACTIVE, playId = state.id), play = state)
        assertEquals(head, GameWire.json.decodeFromString<GameServerMessage>(GameWire.encode(head)))
        val revision = GameRevision(game.id, "4", "5", id(11), b.accountId, scope, head.summary, play = state)
        assertEquals(revision, GameWire.json.decodeFromString<GameServerMessage>(GameWire.encode(revision)))
    }

    @Test fun randomIsOnlyResolvedFromExplicitInitializationInputAndSameInputIsDeterministic() {
        val preparation = ready().copy(starter = StarterChoice())
        val seed = RandomGolden.vectors[2].seed
        val first = StartRules.initialize(game, preparation, id(11), seeds, seed)
        assertEquals(first, StartRules.initialize(game, preparation.copy(players = preparation.players.reversed()), id(11), seeds, seed))
        assertEquals(RandomV1.starter(listOf(a.accountId, b.accountId), seed), first.starter.accountId)
        assertEquals(seed, first.starter.seed); assertEquals("starter-v1", first.starter.algorithm)
        assertFails { StartRules.initialize(game, preparation, id(11), seeds) }
        assertFails { StartRules.initialize(game, ready(), id(11), seeds, seed) }
        assertFails { StartRules.initialize(game, lobby(), id(11), seeds) }
        assertNotEquals(first.id, StartRules.initialize(game, preparation, id(12), seeds, seed).id)
        // All-mainboard-in-Sideboard preparation is legal and starts with an empty Library.
        val empty = ready().copy(players = ready().players.map { it.copy(deck = it.deck!!.copy(entries =
            it.deck!!.entries.map { entry -> entry.copy(section = SavedDeckSection.SIDEBOARD) })) })
        assertTrue(StartRules.initialize(game, empty, id(11), seeds).board.cards.isEmpty())
    }

    @Test fun commandCodecsAreRoundBoundAndSemanticChoiceIsDistinctFromTransport() {
        for (action in listOf(GameAction.Ready, GameAction.WithdrawReady, GameAction.SetStarter(StarterChoice()), GameAction.SetStarter(StarterChoice(a.accountId)))) {
            val command = GameCommand(id(12), scope, action, id(13), "1")
            assertTrue(GameWire.valid(command))
            assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
            assertEquals(GameWire.digest(command), GameWire.digest(command.copy(streamId = id(14), generation = "2")))
            assertNotEquals(GameWire.digest(command), GameWire.digest(command.copy(scope = scope.copy(preparationId = id(15)))))
            assertFalse(GameWire.valid(command.copy(scope = scope.copy(playId = id(15)))))
            assertFalse(GameWire.valid(command.copy(scope = scope.copy(invitationId = game.invitationId))))
        }
        val random = GameCommand(id(12), scope, GameAction.SetStarter(StarterChoice()), id(13), "1")
        assertNotEquals(GameWire.digest(random), GameWire.digest(random.copy(action = GameAction.SetStarter(StarterChoice(a.accountId)))))
        assertFalse(GameWire.valid(random.copy(action = GameAction.SetStarter(StarterChoice("invalid")))))
    }
}
