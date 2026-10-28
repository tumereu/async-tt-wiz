package org.asyncttwiz.protocol

import kotlin.test.*

class PodChallengesTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val game = GameSummary(id(1), "Limited", "2026-10-26T00:00:00Z", AccountIdentity(id(2), "A"),
        AccountIdentity(id(3), "B"), id(4), id(5), InvitationState.ACCEPTED, podId = id(6), podName = "Pod")
    private val definitions = BasicLand.entries.mapIndexed { index, land ->
        val definition = CardDefinition(land.cardName, listOf(DefinitionFace(id(10 + index), land.cardName, typeLine = "Basic Land — ${land.cardName}")))
        SubmittedDefinition(DeckJson.digest(definition), definition)
    }
    private val basicMap = PodGamePreparation.basicDefinitions(definitions)!!
    private val deck = PreparedDeck(id(20), id(6), "1", "Pod", null, id(21),
        listOf(PreparedEntry(definitions.first().digest, SavedDeckSection.MAINBOARD, "0", 2)), basicMap)
    private val state = LobbyPreparation(game.id, game.preparationRound,
        listOf(PlayerPreparation(id(2), true, deck), PlayerPreparation(id(3), true, deck.copy(id = id(22)))), definitions,
        podRules = PodGameRules(1))
    private val scope = GameScope(game.id, game.preparationRound)
    private fun edit(current: LobbyPreparation = state, basics: PodDeckBasics = PodDeckBasics(plains = 38)) =
        PodGamePreparation.setBasics(game, current, id(2), scope, GameAction.SetPodGameBasics(deck.id, basics, definitions))

    @Test fun definitionsRequireFiveExactNamedNonsnowBasicLandsAndMatchingDigests() {
        assertEquals(5, basicMap.size)
        assertNull(PodGamePreparation.basicDefinitions(definitions.drop(1)))
        assertNull(PodGamePreparation.basicDefinitions(definitions.dropLast(1) + definitions.first()))
        assertNull(PodGamePreparation.basicDefinitions(definitions.map { it.copy(digest = "sha256:" + "0".repeat(64)) }))
        for (type in listOf("Land — Plains", "Basic Creature — Plains", "Snow Basic Land — Plains",
            "Basic Land Creature — Swamp Horror", "Basic Land — Swamp", "Basic Land - Plains", "Basic Land — Plains ")) {
            val invalid = definitions.first().definition.let { it.copy(faces = listOf(it.faces.single().copy(typeLine = type))) }
            assertNull(PodGamePreparation.basicDefinitions(listOf(SubmittedDefinition(DeckJson.digest(invalid), invalid)) + definitions.drop(1)))
        }
    }

    @Test fun suppliedBasicsRejectForgedFacesAndAbilitiesEvenWithCorrectDigests() {
        val card = definitions.first().definition
        val face = card.faces.single()
        val invalid = listOf(
            card.copy(name = "plains"), card.copy(faces = listOf(face.copy(name = "Island"))),
            card.copy(faces = listOf(face, face.copy(faceId = id(99)))), card.copy(token = true),
        ) + listOf(face.copy(power = "20"), face.copy(toughness = "20"), face.copy(loyalty = "1"),
            face.copy(defense = "1"), face.copy(rulesText = "Draw twenty cards."),
            face.copy(rulesText = "({T}: Add {B}.)"), face.copy(rulesText = "({T}: Add {W}.)\nDraw a card."),
            face.copy(rulesText = " ")).map { card.copy(faces = listOf(it)) }
        for (definition in invalid) {
            assertNull(PodGamePreparation.basicDefinitions(
                listOf(SubmittedDefinition(DeckJson.digest(definition), definition)) + definitions.drop(1)))
        }
    }

    @Test fun catalogManaReminderTextIsAcceptedForEachBasic() {
        val catalog = definitions.zip(listOf("W", "U", "B", "R", "G")).map { (submitted, mana) ->
            val card = submitted.definition.copy(provider = "mtgjson", printingId = submitted.definition.faces.single().faceId,
                faces = submitted.definition.faces.map { it.copy(rulesText = "({T}: Add {$mana}.)", sourceData = "{}") })
            SubmittedDefinition(DeckJson.digest(card), card)
        }
        assertEquals(5, PodGamePreparation.basicDefinitions(catalog)!!.size)
    }

    @Test fun basicsAreConcreteMainCopiesAndOnlyOwnReadyClearsWithoutTouchingDraftedLands() {
        val next = edit().state!!
        assertFalse(next.players[0].ready); assertTrue(next.players[1].ready)
        val prepared = next.players[0].deck!!
        assertEquals(40L, prepared.count(SavedDeckSection.MAINBOARD))
        assertEquals(deck.entries.single(), prepared.entries.first())
        assertEquals(38, PodGamePreparation.basics(prepared).plains)
        val basic = prepared.entries.single { it.suppliedBasic != null }
        assertEquals("useBasicStepper", PreparationRules.transfer(game, next, id(2), scope,
            GameAction.TransferPreparedCard(prepared.id, prepared.card(basic, 0).id, basic.firstIndex, basic.section)).reason)
        assertEquals(2L, PreparationRules.capture(next, id(2), definitions.first().digest, SavedDeckSection.MAINBOARD, true)!!.count)
        val removed = edit(next, PodDeckBasics()).state!!
        assertEquals(deck.entries, removed.players.first().deck!!.entries)
        val addedAgain = edit(removed).state!!.players.first().deck!!
        assertNotEquals(prepared.card(basic, 0).id, addedAgain.card(addedAgain.entries.last(), 0).id)
        assertEquals(next, edit(next).state)
    }

    @Test fun readinessUsesFortyIncludingBasicsAndGameLocalSideLimitAndSavedSelectionIsForbidden() {
        assertEquals("mainTooSmall", StartRules.change(game, state, id(2), scope, GameAction.Ready).reason)
        val next = edit().state!!
        assertNotNull(StartRules.change(game, next, id(2), scope, GameAction.Ready).state)
        val side = next.copy(players = next.players.map { it.copy(deck = it.deck!!.copy(entries = it.deck!!.entries.map { entry ->
            if (entry.suppliedBasic == null) entry.copy(section = SavedDeckSection.SIDEBOARD) else entry
        })) })
        val enlarged = edit(side, PodDeckBasics(plains = 40)).state!!
        assertEquals("sideboardLimit", StartRules.change(game, enlarged, id(2), scope, GameAction.Ready).reason)
        assertNotNull(StartRules.change(game, enlarged.copy(podRules = PodGameRules()), id(2), scope, GameAction.Ready).state)
        assertEquals("podDeckFixed", PreparationRules.select(game, state, id(2), id(30), scope,
            GameAction.SelectDeck(id(31), "1"), null, emptyList()).reason)
    }

    @Test fun sideBasicsAreSideboardCopiesEditedSeparatelyAndCountTowardTheSideLimit() {
        fun section(current: LobbyPreparation, basics: PodDeckBasics, section: SavedDeckSection) = PodGamePreparation.setBasics(game, current,
            id(2), scope, GameAction.SetPodGameBasics(deck.id, basics, definitions, section))
        val main = edit().state!!
        val side = section(main, PodDeckBasics(island = 1), SavedDeckSection.SIDEBOARD).state!!
        val prepared = side.players[0].deck!!
        assertEquals(40L, prepared.count(SavedDeckSection.MAINBOARD)); assertEquals(1L, prepared.count(SavedDeckSection.SIDEBOARD))
        assertEquals(PodDeckBasics(plains = 38), PodGamePreparation.basics(prepared))
        assertEquals(PodDeckBasics(island = 1), PodGamePreparation.basics(prepared, SavedDeckSection.SIDEBOARD))
        assertEquals(SavedDeckSection.SIDEBOARD, prepared.entries.single { it.suppliedBasic == BasicLand.ISLAND }.section)
        assertNotNull(StartRules.change(game, side, id(2), scope, GameAction.Ready).state)
        val over = section(side, PodDeckBasics(island = 2), SavedDeckSection.SIDEBOARD).state!!
        assertEquals("sideboardLimit", StartRules.change(game, over, id(2), scope, GameAction.Ready).reason)
        val mainAgain = edit(over, PodDeckBasics(plains = 39)).state!!.players[0].deck!!
        assertEquals(PodDeckBasics(island = 2), PodGamePreparation.basics(mainAgain, SavedDeckSection.SIDEBOARD))
        assertEquals(PodDeckBasics(plains = 39), PodGamePreparation.basics(mainAgain))
        assertEquals(over, section(over, PodDeckBasics(island = 2), SavedDeckSection.SIDEBOARD).state)
        assertEquals("invalidBasics", section(main, PodDeckBasics(island = 961), SavedDeckSection.SIDEBOARD).reason)
        val basic = prepared.entries.single { it.suppliedBasic == BasicLand.ISLAND }
        assertEquals("useBasicStepper", PreparationRules.transfer(game, side, id(2), scope,
            GameAction.TransferPreparedCard(prepared.id, prepared.card(basic, 0).id, basic.firstIndex, basic.section)).reason)
        val legacy = """{"type":"setPodGameBasics","deckId":"${deck.id}","basics":{},"basicDefinitions":[]}"""
        assertEquals(SavedDeckSection.MAINBOARD, (GameWire.json.decodeFromString<GameAction>(legacy) as GameAction.SetPodGameBasics).section)
    }

    @Test fun editsFenceScopeOwnershipDefinitionsAndCodec() {
        val action = GameAction.SetPodGameBasics(deck.id, PodDeckBasics(plains = 38), definitions)
        assertEquals("preparedDeckChanged", PodGamePreparation.setBasics(game, state, id(3), scope, action).reason)
        assertEquals("invalidBasics", edit(basics = PodDeckBasics(plains = -1)).reason)
        assertEquals("invalidBasics", edit(basics = PodDeckBasics(plains = 1000)).reason)
        assertEquals("inapplicable", PodGamePreparation.setBasics(game, state, id(2), scope.copy(preparationId = id(99)), action).reason)
        val command = GameCommand(id(40), scope, action, id(41), "1")
        assertTrue(GameWire.valid(command))
        assertEquals(command, GameWire.decodeClient(DeckJson.format.encodeToString(GameClientMessage.serializer(), command)))
        assertFalse(GameWire.valid(command.copy(action = action.copy(basicDefinitions = emptyList()))))
        val head = GameHead(game.id, "0", game, preparation = edit().state)
        assertEquals(head, GameWire.json.decodeFromString<GameServerMessage>(GameWire.encode(head)))
    }
}
