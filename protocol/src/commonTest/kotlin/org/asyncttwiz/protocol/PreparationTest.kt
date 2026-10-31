package org.asyncttwiz.protocol

import kotlin.test.*
import kotlinx.serialization.encodeToString

class PreparationTest {
    private fun id(index: Int) = "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}"
    private val game = GameSummary(id(10), "Spark", "2026-10-22T00:00:00Z", AccountIdentity(id(2), "A"),
        AccountIdentity(id(3), "B"), id(11), id(12), InvitationState.PENDING)
    private val front = id(20); private val back = id(21)
    private val definition = CardDefinition("Two faces", listOf(DefinitionFace(front, "Front", listOf(back), corner = "2/4"),
        DefinitionFace(back, "Back", listOf(front), rulesText = "Retained text")), sourceVersion = "old catalog", layout = "transform")
    private val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
    private val source = SavedDeck(DeckSummary(id(30), "  Chosen 😀  ", "9007199254740993", "2026-10-21T00:00:00Z",
        2, 1, DeckCover(submitted.digest, definition.name)), listOf(
        SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 2), SavedDeckEntry(SavedDeckSection.SIDEBOARD, submitted.digest, 1)))
    private val action = GameAction.SelectDeck(source.deck.id, source.deck.revision)
    private val scope = GameScope(game.id, game.preparationRound)
    private fun select(summary: GameSummary = game, current: LobbyPreparation = PreparationRules.empty(summary),
        actor: String = game.inviter.accountId, inputScope: GameScope = scope, saved: SavedDeck? = source,
        definitions: List<SubmittedDefinition> = listOf(submitted)) =
        PreparationRules.select(summary, current, actor, id(1), inputScope, action, saved, definitions)

    // LOB-037
    @Test fun exactCopyPartitionDefinitionsAndOwnReadyClearing() {
        val before = PreparationRules.empty(game).copy(players = PreparationRules.empty(game).players.map { it.copy(ready = true) })
        val state = select(current = before).state!!
        val own = state.players[0]; val other = state.players[1]
        assertFalse(own.ready); assertTrue(other.ready); assertNull(other.deck)
        val deck = own.deck!!
        assertEquals(source.deck.name, deck.name); assertEquals(source.deck.id, deck.sourceId)
        assertEquals(source.deck.revision, deck.sourceRevision); assertEquals(submitted.digest, deck.cover)
        assertEquals(2L, deck.count(SavedDeckSection.MAINBOARD)); assertEquals(1L, deck.count(SavedDeckSection.SIDEBOARD))
        assertEquals(listOf(submitted), state.definitions)
        val cards = deck.entries.flatMap { entry -> List(entry.quantity) { deck.card(entry, it) } }
        assertEquals(3, cards.map { it.id }.distinct().size)
        assertEquals("2744a698-f25b-5230-9120-81aa73c72056", cards[0].id)
        assertEquals("816b7999-02a5-5380-a08e-ba7c524d07bd", cards[1].id)
        assertEquals("e14df417-e4d1-51ff-9703-d227ed456455", deck.id)
        assertEquals(state, select(current = before).state) // Same input as server/provisional paths.
        val head = GameHead(game.id, game.revision, game, preparation = state)
        assertEquals(head, GameWire.json.decodeFromString<GameServerMessage>(GameWire.encode(head)))
        assertNull(before.players[0].deck) // Original accepted base was not mutated.
    }

    // TBL-076
    @Test fun existingSavedMultipartDeckNormalizesOnlyItsNewGameSnapshot() {
        val old = definition.copy(layout = "adventure")
        val digest = DeckJson.digest(old)
        val saved = source.copy(entries = source.entries.map { it.copy(definition = digest) },
            deck = source.deck.copy(cover = source.deck.cover!!.copy(definition = digest)))
        val selected = select(saved = saved, definitions = listOf(SubmittedDefinition(digest, old))).state!!
        val physical = PhysicalCardFaces.normalize(old)
        val expected = DeckJson.digest(physical)
        assertEquals(listOf(SubmittedDefinition(expected, physical)), selected.definitions)
        assertEquals(expected, selected.players.first().deck!!.cover)
        assertTrue(selected.players.first().deck!!.entries.all { it.definition == expected })
        assertEquals(2, old.faces.size)
        assertEquals(1, selected.definitions.single().definition.faces.size)
    }

    // LOB-033
    @Test fun invitationAttemptAndUnrelatedRevisionsDoNotLockPreparation() {
        val selected = select().state!!
        val next = game.copy(invitationId = id(50), invitationState = InvitationState.DECLINED, revision = "8")
        assertEquals(selected, select(summary = next).state)
        assertEquals(selected, select(summary = next.copy(invitationState = InvitationState.ACCEPTED)).state)
        assertEquals("accessDenied", select(actor = game.recipient.accountId).reason)
        assertNotNull(select(summary = game.copy(invitationState = InvitationState.ACCEPTED), actor = game.recipient.accountId).state)
        assertEquals("accessDenied", select(actor = id(99)).reason)
    }

    // LOB-053
    @Test fun staleScopeSourceAndMissingDefinitionsNeverSubstituteOrClearReady() {
        assertEquals("inapplicable", select(inputScope = scope.copy(preparationId = id(77))).reason)
        assertEquals("lifecycleEnded", select(summary = game.copy(lifecycle = GameLifecycle.ACTIVE)).reason)
        assertEquals("sourceUnavailable", select(saved = null).reason)
        assertEquals("sourceUnavailable", select(saved = source.copy(deck = source.deck.copy(id = id(70)))).reason)
        assertEquals("sourceChanged", select(saved = source.copy(deck = source.deck.copy(revision = "9007199254740994"))).reason)
        assertEquals("definitionsUnavailable", select(definitions = emptyList()).reason)
        assertEquals("definitionsUnavailable", select(definitions = listOf(submitted.copy(definition = definition.copy(name = "Changed")))).reason)
        assertEquals("invalidSource", select(saved = source.copy(entries = emptyList())).reason)
        // No format legality, exchange, mainboard minimum or default deck.
        val sideOnly = source.copy(entries = source.entries.filter { it.section == SavedDeckSection.SIDEBOARD })
        assertEquals(0L, select(saved = sideOnly).state!!.players[0].deck!!.count(SavedDeckSection.MAINBOARD))
    }

    @Test fun selectionCodecCapturesExactSourceAndRoundButNotTransportOrInvitation() {
        val command = GameCommand(id(1), scope, action, id(60), "1")
        assertTrue(GameWire.valid(command))
        assertEquals(command, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command)))
        assertEquals(GameWire.digest(command), GameWire.digest(command.copy(streamId = id(61), generation = "5")))
        assertNotEquals(GameWire.digest(command), GameWire.digest(command.copy(action = action.copy(sourceRevision = "1"))))
        assertNotEquals(GameWire.digest(command), GameWire.digest(command.copy(scope = scope.copy(preparationId = id(62)))))
        assertFalse(GameWire.valid(command.copy(scope = scope.copy(invitationId = game.invitationId))))
        for (revision in listOf("0", "01", "1.0", "-1", "9223372036854775808"))
            assertFalse(GameWire.valid(command.copy(action = action.copy(sourceRevision = revision))))
        assertFalse(GameWire.valid(command.copy(action = action.copy(deckId = "not-a-uuid"))))
    }

    @Test fun uuidV5MatchesIndependentStandardVectorAndSemanticRoles() {
        assertEquals("21f7f8de-8051-5b89-8680-0195ef798b6a", CommandIds.uuidV5("6ba7b810-9dad-11d1-80b4-00c04fd430c8", "www.widgets.com"))
        assertNotEquals(CommandIds.output(id(1), "prepared-card", 0), CommandIds.output(id(2), "prepared-card", 0))
        assertFails { CommandIds.output(id(1), "unregistered-role", 0) }
        assertFails { CommandIds.output(id(1), "prepared-card", -1) }
    }

    @Test fun largeQuantitiesRetainConcreteIdentitiesWithoutAllocatingPerCopy() {
        assertEquals("invalidSource", select(saved = source.copy(entries = source.entries.map { it.copy(quantity = Int.MAX_VALUE) })).reason)
        val entries = source.entries.map { it.copy(quantity = 500) }
        val deck = select(saved = source.copy(entries = entries)).state!!.players[0].deck!!
        assertEquals(500L, deck.count(SavedDeckSection.MAINBOARD))
        assertEquals(500L, deck.count(SavedDeckSection.SIDEBOARD))
        assertEquals("500", deck.entries[1].firstIndex)
        val last = deck.card(deck.entries[1], 499)
        assertEquals(CommandIds.output(id(1), "prepared-card", 999L), last.id)
        assertNotEquals(deck.card(deck.entries[0], 0).id, last.id)
        assertTrue(GameWire.json.encodeToString(deck).length < 2000)
    }
}
