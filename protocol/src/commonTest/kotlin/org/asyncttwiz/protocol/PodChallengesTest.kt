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
    private val basicMap = BasicLand.entries.zip(definitions).associate { (land, card) -> land to card.digest }
    private val deck = PreparedDeck(id(20), id(6), "1", "Pod", null, id(21),
        listOf(PreparedEntry(definitions.first().digest, SavedDeckSection.MAINBOARD, "0", 2)), basicMap)
    private val state = LobbyPreparation(game.id, game.preparationRound,
        listOf(PlayerPreparation(id(2), true, deck), PlayerPreparation(id(3), true, deck.copy(id = id(22)))), definitions,
        podRules = PodGameRules(1))
    private val scope = GameScope(game.id, game.preparationRound)
    /** [current] with [basics] added to the first player's deck in [section], as a pod deck holds its supplied basics (LOB-052). */
    private fun withBasics(current: LobbyPreparation = state, basics: PodDeckBasics = PodDeckBasics(plains = 38),
        section: SavedDeckSection = SavedDeckSection.MAINBOARD): LobbyPreparation {
        val prepared = current.players[0].deck!!
        var index = maxOf(GameWire.ordinal(prepared.nextCardIndex), prepared.entries.maxOfOrNull { GameWire.ordinal(it.firstIndex) + it.quantity } ?: 0)
        val added = BasicLand.entries.mapNotNull { land -> land.count(basics).takeIf { it > 0 }?.let { count ->
            PreparedEntry(prepared.basicDefinitions.getValue(land), section, index.toString(), count, land).also { index += count }
        } }
        return current.copy(players = current.players.mapIndexed { i, player ->
            if (i == 0) player.copy(deck = prepared.copy(entries = prepared.entries + added, nextCardIndex = index.toString())) else player
        })
    }

    // DRF-124, LOB-052: a supplied basic moves between Main and Side like any card.
    @Test fun suppliedBasicsAreConcreteCopiesThatMoveLikeAnyCard() {
        val next = withBasics()
        val prepared = next.players[0].deck!!
        assertEquals(40L, prepared.count(SavedDeckSection.MAINBOARD))
        assertEquals(deck.entries.single(), prepared.entries.first())
        assertEquals(38, PodGamePreparation.basics(prepared).plains)
        val basic = prepared.entries.single { it.suppliedBasic != null }
        val moved = PreparationRules.transfer(game, next, id(2), scope,
            GameAction.TransferPreparedCard(prepared.id, prepared.card(basic, 0).id, basic.firstIndex, basic.section)).state!!.players[0].deck!!
        assertEquals(37, PodGamePreparation.basics(moved).plains)
        assertEquals(1, PodGamePreparation.basics(moved, SavedDeckSection.SIDEBOARD).plains)
        assertEquals(40L, PreparationRules.capture(next, id(2), definitions.first().digest, SavedDeckSection.MAINBOARD, true)!!.count)
    }

    // DRF-124
    @Test fun readinessUsesFortyIncludingBasicsAndGameLocalSideLimitAndSavedSelectionIsForbidden() {
        assertEquals("mainTooSmall", StartRules.change(game, state, id(2), scope, GameAction.Ready).reason)
        val next = withBasics()
        assertNotNull(StartRules.change(game, next, id(2), scope, GameAction.Ready).state)
        val side = next.copy(players = next.players.map { it.copy(deck = it.deck!!.copy(entries = it.deck!!.entries.map { entry ->
            if (entry.suppliedBasic == null) entry.copy(section = SavedDeckSection.SIDEBOARD) else entry
        })) })
        val enlarged = withBasics(side.copy(players = side.players.map { it.copy(deck = it.deck!!.copy(
            entries = it.deck!!.entries.filter { entry -> entry.suppliedBasic == null })) }), PodDeckBasics(plains = 40))
        assertEquals("sideboardLimit", StartRules.change(game, enlarged, id(2), scope, GameAction.Ready).reason)
        assertNotNull(StartRules.change(game, enlarged.copy(podRules = PodGameRules()), id(2), scope, GameAction.Ready).state)
        assertEquals("podDeckFixed", PreparationRules.select(game, state, id(2), id(30), scope,
            GameAction.SelectDeck(id(31), "1"), null, emptyList()).reason)
    }

    // DRF-124
    @Test fun sideBasicsAreSideboardCopiesThatCountTowardTheSideLimit() {
        val side = withBasics(withBasics(), PodDeckBasics(island = 1), SavedDeckSection.SIDEBOARD)
        val prepared = side.players[0].deck!!
        assertEquals(40L, prepared.count(SavedDeckSection.MAINBOARD)); assertEquals(1L, prepared.count(SavedDeckSection.SIDEBOARD))
        assertEquals(PodDeckBasics(plains = 38), PodGamePreparation.basics(prepared))
        assertEquals(PodDeckBasics(island = 1), PodGamePreparation.basics(prepared, SavedDeckSection.SIDEBOARD))
        assertNotNull(StartRules.change(game, side, id(2), scope, GameAction.Ready).state)
        val over = withBasics(side, PodDeckBasics(island = 1), SavedDeckSection.SIDEBOARD)
        assertEquals("sideboardLimit", StartRules.change(game, over, id(2), scope, GameAction.Ready).reason)
        val basic = prepared.entries.single { it.suppliedBasic == BasicLand.ISLAND }
        val back = PreparationRules.transfer(game, side, id(2), scope,
            GameAction.TransferPreparedCard(prepared.id, prepared.card(basic, 0).id, basic.firstIndex, basic.section)).state!!.players[0].deck!!
        assertEquals(PodDeckBasics(plains = 38, island = 1), PodGamePreparation.basics(back))
        assertEquals(PodDeckBasics(), PodGamePreparation.basics(back, SavedDeckSection.SIDEBOARD))
        val head = GameHead(game.id, "0", game, preparation = side)
        assertEquals(head, GameWire.json.decodeFromString<GameServerMessage>(GameWire.encode(head)))
    }
}
