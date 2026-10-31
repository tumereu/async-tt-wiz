package org.asyncttwiz.android

import androidx.compose.ui.test.junit4.createComposeRule
import com.sun.net.httpserver.HttpServer
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkUrlPolicy
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.UUID
import kotlin.test.*

/** The real process-wide artwork service against a local stand-in for Scryfall. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtworkPrefetchStartTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()
    private val requested = mutableListOf<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            synchronized(requested) { requested += exchange.requestURI.toString() }
            exchange.sendResponseHeaders(404, -1); exchange.close()
        }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val service by lazy { CardArtworkService(RuntimeEnvironment.getApplication(), temporary.newFolder(), ArtworkUrlPolicy(base), "$base/cards/") }

    private fun install(value: CardArtworkService?) {
        val provider = PublicArtwork::class.java.getDeclaredField("provider").apply { isAccessible = true }.get(PublicArtwork)
        provider.javaClass.getDeclaredField("service").apply { isAccessible = true }.set(provider, value)
    }

    @After fun close() { install(null); service.close(); server.stop(0) }

    private fun definition(printing: String): SubmittedDefinition {
        val card = CardDefinition("Card $printing", listOf(DefinitionFace(UUID.randomUUID().toString(), "Card $printing", scryfallId = printing)))
        return SubmittedDefinition(DeckJson.digest(card), card)
    }

    private fun awaitRequestFor(printing: String) =
        compose.waitUntil(20_000) { synchronized(requested) { requested.any { printing in it } } }

    // LOB-097
    @Test fun savingADeckAndOpeningAGameStartDownloadingTheirCardPictures() {
        install(service)
        val deckCard = UUID.randomUUID().toString()
        val gameCard = UUID.randomUUID().toString()
        prefetchDeckArtwork(RuntimeEnvironment.getApplication(), "deck-1", listOf(definition(deckCard)))
        awaitRequestFor(deckCard)
        assertTrue(synchronized(requested) { requested.none { gameCard in it } }, "Nothing of the game is fetched before it opens")
        val data = GameplayFixture()
        compose.setContent { PrefetchGameArtwork(data.initial.copy(definitions = listOf(definition(gameCard))), data.actor) }
        awaitRequestFor(gameCard)
    }
}
