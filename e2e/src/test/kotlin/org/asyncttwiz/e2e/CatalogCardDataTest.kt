package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.CubeController
import org.asyncttwiz.android.DeckApi
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.SavedCubeEntry
import org.asyncttwiz.protocol.SavedDeckEntry
import org.asyncttwiz.protocol.SavedDeckSection
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Card data from the server's catalog (owner 2026-11-10, SYS-084): a phone saves a deck or a cube as one request naming
 * its cards' printings, the server keeps them by printing and the phone shows them from its own card records (no card
 * data is read or uploaded); a pod challenge sends no card data (the server supplies the basic lands), a Ready brings the
 * server's dungeons for a deck that ventures, and the server refuses a deck holding a card Automatum can't play (LOB-124).
 */
class CatalogCardDataTest : E2e() {
    /** The deck-service and pod requests Alice's phone made since [from] (method and path). */
    private fun requests(from: Int = 0) = synchronized(alice.link.requests) { alice.link.requests.drop(from) }
        .filter { request -> request.substringAfter(' ').let { it.startsWith("/v1/decks") || it.startsWith("/v1/cubes") ||
            it.startsWith("/v1/definitions") || it.startsWith("/v1/pods") } }
    /** Of [requests], the ones that change something: not the reads of the saved list that open it afterwards; an upload or a
     * read of card data (POST /v1/definitions, …/definitions) would be one. */
    private fun changes(from: Int) = requests(from).filter { !it.startsWith("GET") }

    // SYS-084, LOB-064: a new deck from the editor is one request; its cards are the catalog's, as the phone makes them
    @Test fun aNewDeckIsSavedByPrintingInOneRequest() {
        launchPlayers(aliceCatalog = true)
        waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
        alice.decksShown = true
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-DECKS")) }
        alice.tagged("menu-DECKS").performClick()
        alice.deckImport.open()
        alice.deckImport.name("E2E by printing")
        alice.deckImport.edit("Deck\n20 Mountain\n20 Storm Spark\nSideboard\n1 Ember Path")
        alice.deckImport.lookup()
        waitUntil("the list looked up in Alice's card records") { alice.deckImport.state.result?.resolved == true && alice.deckImport.canSave }
        val from = alice.link.requests.size
        alice.tagged("save-deck").performClick()
        waitUntil("the saved deck") { !alice.deckImport.state.active && alice.decks.state.detail != null }
        // One request: no card data goes up first, and the saved deck comes back with it (its cover too)
        assertEquals(listOf("POST /v1/decks"), changes(from), "${requests(from)}")
        val saved = alice.decks.state.detail!!.saved
        val read = runBlocking { DeckApi(E2eStack.origin).use { it.read(alice.account.token, saved.deck.id) } }
        assertEquals(listOf(saved(SavedDeckSection.MAINBOARD, TestCards.mountain, 20), saved(SavedDeckSection.MAINBOARD, TestCards.spark, 20),
            saved(SavedDeckSection.SIDEBOARD, TestCards.pathCrag, 1)), read.entries)
        assertEquals(TestCards.mountain.printingId, read.deck.cover!!.definition)
        // The cards the deck view shows are built from Alice's card records, the same the server builds
        assertEquals(setOf(TestCards.mountain, TestCards.spark, TestCards.pathCrag).associate { it.printingId to it.definition },
            alice.decks.state.detail!!.definitions)

        // Reopened in the editor from the records, edited and saved: one request, still by printing
        alice.deckImport.openEditor(alice.decks.state.detail!!)
        waitUntil("the deck open in the editor") { alice.deckImport.state.result?.resolved == true }
        assertTrue("20 Mountain" in alice.deckImport.state.text, alice.deckImport.state.text)
        alice.deckImport.edit(alice.deckImport.state.text.replace("20 Mountain", "18 Mountain"))
        alice.deckImport.lookup()
        waitUntil("the edit looked up") { alice.deckImport.state.result?.resolved == true && alice.deckImport.canSave }
        val edit = alice.link.requests.size
        alice.deckImport.save()
        waitUntil("the edited deck saved") { !alice.deckImport.state.active }
        assertEquals(listOf("PUT /v1/decks/${saved.deck.id}"), changes(edit), "${requests(edit)}")
        val edited = runBlocking { DeckApi(E2eStack.origin).use { it.read(alice.account.token, saved.deck.id) } }
        assertEquals(listOf(saved(SavedDeckSection.MAINBOARD, TestCards.mountain, 18), saved(SavedDeckSection.MAINBOARD, TestCards.spark, 20),
            saved(SavedDeckSection.SIDEBOARD, TestCards.pathCrag, 1)), edited.entries)
        assertEquals("2", edited.deck.revision)
    }

    private fun saved(section: SavedDeckSection, card: TestCard, quantity: Int) = SavedDeckEntry(section, card.printingId, card.name, quantity)

    // SYS-084, DRF-002: a new cube is one request too, Main and Maybe by printing
    @Test fun aNewCubeIsSavedByPrintingInOneRequest() {
        launchPlayers(aliceCatalog = true)
        waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
        val worker = Executors.newSingleThreadExecutor { Thread(it, "e2e-cubes").apply { isDaemon = true } }
        val resolver = alice.catalog!!.controller.cubeResolver()
        val cubes = CubeController(DeckApi(alice.link.origin), alice.auth::sessionToken, resolver::resolve, resolver::resolve, worker,
            { action -> android.os.Handler(android.os.Looper.getMainLooper()).post(action) })
        try {
            cubes.newCube(); cubes.name("E2E cube by printing")
            cubes.text("Deck\n3 Ember Cub\n2 Storm Spark\nMaybeboard\n1 Cinder Hound")
            cubes.lookup()
            waitUntil("the cube looked up in Alice's card records") { cubes.state.result?.resolved == true && cubes.canSave }
            val from = alice.link.requests.size
            cubes.save()
            waitUntil("the saved cube") { !cubes.state.editing && cubes.state.detail != null }
            assertEquals(listOf("POST /v1/cubes"), changes(from), "${requests(from)}")
            val saved = runBlocking { DeckApi(E2eStack.origin).use { it.readCube(alice.account.token, cubes.state.detail!!.saved.cube.id) } }
            assertEquals(listOf(SavedCubeEntry(TestCards.cub.printingId, TestCards.cub.name, 3), SavedCubeEntry(TestCards.spark.printingId, TestCards.spark.name, 2),
                SavedCubeEntry(TestCards.hound.printingId, TestCards.hound.name, 1, SavedDeckSection.MAYBEBOARD)), saved.entries)

            // Opened from the records, edited and saved: one request, still by printing
            cubes.cardRecords = alice.cardRecords
            cubes.open(saved.cube.id)
            waitUntil("the cube read") { cubes.state.detail?.saved?.cube?.id == saved.cube.id && !cubes.state.busy }
            android.os.Handler(android.os.Looper.getMainLooper()).post { cubes.edit() }
            waitUntil("the cube open in the editor") { cubes.state.editing && cubes.state.result?.resolved == true }
            assertTrue("3 Ember Cub" in cubes.state.text, cubes.state.text)
            android.os.Handler(android.os.Looper.getMainLooper()).post { cubes.text(cubes.state.text.replace("3 Ember Cub", "5 Ember Cub")); cubes.lookup() }
            waitUntil("the edit looked up") { cubes.state.result?.resolved == true && cubes.canSave }
            val edit = alice.link.requests.size
            android.os.Handler(android.os.Looper.getMainLooper()).post { cubes.save() }
            waitUntil("the edited cube saved") { !cubes.state.editing && cubes.state.detail?.saved?.cube?.revision == "2" }
            assertEquals(listOf("PUT /v1/cubes/${saved.cube.id}"), changes(edit), "${requests(edit)}")
            val edited = runBlocking { DeckApi(E2eStack.origin).use { it.readCube(alice.account.token, saved.cube.id) } }
            assertEquals(5, edited.entries.first().quantity)
            assertEquals(TestCards.cub.printingId, edited.entries.first().printingId)
        } finally { cubes.close() }
    }

    // LOB-052, DRF-123: a challenge from a pod is one request; the challenge deck's basics are the server's M21 printings
    @Test fun aPodChallengeSendsNoCardDataAndGetsTheServersBasics() {
        launchPlayers()
        val name = "E2E basics ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, "$name pod")
        for (who in listOf(alice.account, bob.account)) ServerSetup.submitPodDeck(who, pod)
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-GAMES")) }
        val from = alice.link.requests.size
        alice.lobby.challengeFromPod(pod, bob.id, name)
        waitUntil("the challenge game open on Alice's phone") { alice.lobby.state.detail?.gameName == name && alice.lobby.state.preparation != null }
        assertEquals(listOf("POST /v1/pods/$pod/challenge"), changes(from), "${requests(from)}")
        val preparation = alice.lobby.state.preparation!!
        val deck = preparation.players.single { it.accountId == alice.id }.deck!!
        val printings = deck.basicDefinitions.mapValues { (_, digest) -> preparation.definitions.single { it.digest == digest }.definition.printingId }
        assertEquals(with(TestCards) { listOf(plains, island, swamp, mountain, forest) }.map { it.printingId }, printings.values.toList())
        assertEquals(40, deck.entries.filter { it.suppliedBasic != null && it.section == SavedDeckSection.MAINBOARD }.sumOf { it.quantity })
    }

    // LOB-118, LOB-124: a deck holding a card Automatum can't play (the catalog's marks): the server's result names it at
    // once, and the server refuses choosing it, so the player can't get Ready with it
    @Test fun aDeckWithACardAutomatumCantPlayIsRefused() {
        val (a, b) = ServerSetup.accounts()
        val deck = ServerSetup.deck(a, TestDeck("E2E odd deck", listOf(TestCards.mountain to 39, TestCards.relic to 1)))
        assertEquals(listOf("Odd Relic"), deck.deck.automatum?.byHand)
        val game = ServerSetup.invitation(a, b, "E2E odd game ${UUID.randomUUID().toString().take(6)}")
        val (chosen, ready) = ServerSetup.attempt(a, game, GameAction.SelectDeck(deck.deck.id, deck.deck.revision), GameAction.Ready)
        assertEquals("automatumUnsupported", chosen.reason)
        assertEquals("rejected", ready.outcome, "Ready without a deck: ${ready.reason}")
    }

    // LOB-117, LOB-123: a deck that ventures readies with the server's dungeons outside the game, its own of none
    @Test fun aVenturingDecksReadyBringsTheServersDungeons() {
        startGame(TestDeck("Maps", listOf(TestCards.mountain to 20, TestCards.torchMap to 20)))
        assertEquals(GameLifecycle.ACTIVE, game.lifecycle, "the server let the venturing deck's Ready through")
        bothOpen()
        val outside = alice.state.player(alice.me).outside.map { cardName(alice.state, it) }
        assertEquals(listOf("Dungeon Test Crypt", "Dungeon Test Sunken City"), outside.sortedBy { it })
        assertTrue(bob.state.player(bob.me).outside.isEmpty(), "Bob's deck doesn't venture: no dungeons for him")
    }
}
