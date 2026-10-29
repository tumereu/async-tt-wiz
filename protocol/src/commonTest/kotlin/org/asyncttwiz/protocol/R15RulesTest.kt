package org.asyncttwiz.protocol

import kotlinx.serialization.json.*
import kotlin.test.*

class R15RulesTest {
    private val fieldId = gameplayId(22)
    private val firstPile = PlayPlacement(BoardPosition(0, 0), gameplayId(40))
    private val secondPile = PlayPlacement(BoardPosition(1, 0), gameplayId(41))
    private val firstOrder = listOf(gameplayId(11), gameplayId(10))
    private val secondOrder = listOf(gameplayId(14), gameplayId(12), gameplayId(13))

    private fun piledState(): PlayState = gameplayFixture().withBattlefieldCards(
        *(firstOrder + secondOrder).toTypedArray()).let { state ->
        state.copy(board = state.board.copy(cards = state.board.cards.map {
            it.copy(placement = if (it.id in firstOrder) firstPile else secondPile)
        }))
    }

    private fun members(board: BoardState, placement: PlayPlacement) = GameplayRules.members(
        board, board.containers.single { it.id == fieldId }, placement)

    @Test fun creatingOrCloningTokenDoesNotReorderExistingPiles() {
        val original = piledState()
        for (action in listOf(PlayAction.CreateToken(TokenCatalog.blank), PlayAction.CloneToken(gameplayId(10)))) {
            val change = gameplayChange(original, action, 200)
            val next = assertNotNull(change.state, change.reason)
            val event = assertNotNull(change.mutation)
            assertEquals(firstOrder, members(next.board, firstPile), "$action first pile")
            assertEquals(secondOrder, members(next.board, secondPile), "$action second pile")
            assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, event.transition.rulesVersion)
            assertEquals(GameplayRules.canonicalBoard(next.board), next.board)
            assertTrue(event.transition.delta!!.piles.none { it.placement in listOf(firstPile, secondPile) })
            assertEquals(next, GameplayHistory.apply(original, event))
            assertEquals(original, GameplayHistory.unapply(next, event))
            val token = next.board.cards.single { it.token }
            val removed = gameplayChange(next, PlayAction.RemoveToken(token.id), 203)
            assertEquals(next.board, GameplayHistory.inverseTokenLinks(removed.state!!.board, removed.mutation!!.transition))
        }
    }

    private fun counterState(): PlayState = gameplayFixture().withBattlefieldCards(gameplayId(10)).let { state ->
        state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id != gameplayId(10)) it else it.copy(counters = linkedMapOf(
                CounterColor.PURPLE to "3", CounterColor.BLUE to "2", CounterColor.RED to "1"))
        }))
    }

    // PostgreSQL JSONB orders these ASCII object keys by byte length, then lexically. Arrays keep order.
    private fun jsonbOrder(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.entries.sortedWith(compareBy({ it.key.length }, { it.key }))
            .associate { it.key to jsonbOrder(it.value) })
        is JsonArray -> JsonArray(value.map(::jsonbOrder))
        else -> value
    }

    @Test fun storedBaselineAndLiveEndpointProduceTheSameMoveSeal() {
        val original = counterState()
        val clone = gameplayChange(original, PlayAction.CloneToken(gameplayId(10)), 201)
        val live = assertNotNull(clone.state)
        val event = assertNotNull(clone.mutation)
        val tree = GameplayTree(original, mapOf(event.id to event), event.id)
        val stored = TreeSync.decode(TreeSync.records(TreeSync.generation(tree)).map {
            if (it.kind == "node") it.copy(payload = jsonbOrder(it.payload)) else it
        }, original.gameId, original.id).tree
        val baseline = assertNotNull(stored.stateAt(stored.currentNodeId))
        assertEquals(live, baseline)
        val token = live.board.cards.single { it.token }
        assertNotEquals(token.counters.keys.toList(), baseline.board.cards.single { it.token }.counters.keys.toList())
        val move = PlayAction.MoveCard(token.id, MoveSource(fieldId, token.placement),
            MoveDestination(gameplayId(21), MoveInsertion.UNORDERED))
        val endpointPrediction = gameplayChange(live, move, 202)
        val baselinePrediction = gameplayChange(baseline, move, 202)
        assertEquals(endpointPrediction.state, baselinePrediction.state)
        val liveEvent = assertNotNull(endpointPrediction.mutation)
        val storedEvent = assertNotNull(baselinePrediction.mutation)
        assertEquals(CURRENT_GAMEPLAY_RULES_VERSION, liveEvent.transition.rulesVersion)
        assertEquals(liveEvent.transition.delta!!.seal, storedEvent.transition.delta!!.seal)
        assertEquals(liveEvent.transition.fields, storedEvent.transition.fields)
        assertEquals(liveEvent.transition.delta, storedEvent.transition.delta)
        assertEquals(endpointPrediction.state, GameplayHistory.apply(baseline, liveEvent))
        assertEquals(baseline, GameplayHistory.unapply(endpointPrediction.state!!, liveEvent))
    }

    @Test fun counterMapIterationDoesNotChangeDeltaOrdering() {
        val original = counterState()
        val normalized = original.copy(board = original.board.copy(cards = original.board.cards.map {
            it.copy(counters = it.counters.entries.sortedBy { entry -> entry.key.name }.associate { entry -> entry.toPair() })
        }))
        assertEquals(original, normalized)
        val next = original.copy(board = original.board.copy(cards = original.board.cards.map { it.copy(counters = emptyMap()) }))
        assertEquals(GameplayDeltas.capture(original, next, gameplayId(200)),
            GameplayDeltas.capture(normalized, next, gameplayId(200)))
    }

    private fun legacyEvent(original: PlayState, next: PlayState, change: GameplayChange,
        delta: GameplayDelta = GameplayDeltas.capture(original, next, change.mutation!!.id),
        fields: List<GameplayFieldChange> = change.mutation!!.transition.fields): GameplayMutation {
        val raw = change.mutation!!.let { it.copy(transition = it.transition.copy(
            rulesVersion = 15, fields = fields, delta = delta, concerns = null)) }
        val recorded = raw.copy(transition = raw.transition.copy(
            concerns = GameplayConcerns.capture(original, next, raw, delta)))
        return recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
    }

    /** Rules v29 stamps the entered turn on arrival; these simulated v15 events predate that field. */
    private fun withoutEnteredTurn(change: GameplayChange) = change.copy(
        state = change.state!!.let { it.copy(board = it.board.copy(cards = it.board.cards.map { card -> card.copy(enteredTurn = "") })) },
        mutation = change.mutation!!.let { it.copy(transition = it.transition.copy(tokens = it.transition.tokens.map { token ->
            token.copy(to = token.to?.let { presence -> presence.copy(card = presence.card.copy(enteredTurn = "")) })
        })) })

    private fun assertRetained(original: PlayState, next: PlayState, event: GameplayMutation) {
        val decoded = GameWire.json.decodeFromJsonElement<GameplayMutation>(
            jsonbOrder(GameWire.json.encodeToJsonElement(event)))
        assertEquals(event, decoded)
        assertEquals(event.transition.delta!!.seal, GameplayDeltas.seal(decoded))
        assertEquals(next, GameplayHistory.apply(original, decoded))
        assertEquals(original, GameplayHistory.unapply(next, decoded))
        assertEquals(event.transition, GameplayHistory.replay(original, decoded).mutation!!.transition)
    }

    @Test fun retainedVersion15TokenCreationKeepsItsRecordedPileReorderingAndSeal() {
        val original = piledState()
        val change = withoutEnteredTurn(gameplayChange(original, PlayAction.CreateToken(TokenCatalog.blank), 200))
        // The old event recorded a global card-ID sort, including the unintended pile changes.
        val next = change.state!!.let { it.copy(board = it.board.copy(containers = it.board.containers.map { container ->
            if (container.id == fieldId) container.copy(cards = container.cards.sorted()) else container
        })) }
        val event = legacyEvent(original, next, change)
        assertEquals("7a6f39166a3b5dc05eab18439a1080aca65306a6f843f039ef83b8cc016775ea", event.transition.delta!!.seal)
        assertEquals(firstOrder.sorted(), members(next.board, firstPile))
        assertEquals(secondOrder.sorted(), members(next.board, secondPile))
        assertRetained(original, next, event)
        val tree = GameplayTree(original, mapOf(event.id to event), event.id)
        val downloaded = TreeSync.decode(TreeSync.records(TreeSync.generation(tree)), original.gameId, original.id).tree
        assertEquals(next, downloaded.stateAt(event.id))
    }

    @Test fun retainedVersion15MoveKeepsItsRecordedMapOrderAndSeal() {
        val original = counterState()
        val clone = withoutEnteredTurn(gameplayChange(original, PlayAction.CloneToken(gameplayId(10)), 201)).state!!
        val token = clone.board.cards.single { it.token }
        val move = PlayAction.MoveCard(token.id, MoveSource(fieldId, token.placement),
            MoveDestination(gameplayId(21), MoveInsertion.UNORDERED))
        val change = gameplayChange(clone, move, 202)
        val next = change.state!!
        // Exact pre-fix serializer traversal: card fields first, counters in insertion order.
        val paths = listOf(listOf("containerId"), listOf("covered"), listOf("corner"),
            listOf("counters", "PURPLE"), listOf("counters", "BLUE"), listOf("counters", "RED"),
            listOf("note"), listOf("placement"))
        val delta = change.mutation!!.transition.delta!!.copy(seal = "").let { it.copy(entities = it.entities.map { entity ->
            if (entity.kind != "card") entity else {
                assertEquals(paths.toSet(), entity.values.map { value -> value.path }.toSet())
                entity.copy(values = entity.values.sortedBy { value -> paths.indexOf(value.path) })
            }
        }) }
        val fields = change.mutation.transition.fields.let { fields ->
            fields.filter { it.field != GameplayField.COUNTER } + token.counters.map { (color, value) ->
                GameplayFieldChange(token.id, GameplayField.COUNTER, value, null, color)
            }
        }
        val event = legacyEvent(clone, next, change, delta, fields)
        assertEquals("a1018181ffcd77f46cd1410be81f8556843596621d1ce85d4c08393de91feec6", event.transition.delta!!.seal)
        assertRetained(clone, next, event)
    }
}
