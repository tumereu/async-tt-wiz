package org.asyncttwiz.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import coil3.asImage
import coil3.memory.MemoryCache
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The limited engines' pick screens with (fake, card-proportioned) artwork. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-xhdpi")
class PodLimitedScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()
    private val alice = AccountIdentity("alice", "Alice")
    private val bob = AccountIdentity("bob", "Bob")
    private val carol = AccountIdentity("carol", "Carol")
    private val members = listOf(alice, bob, carol)
    private val names = listOf("Lightning Bolt", "Counterspell", "Llanowar Elves", "Swords to Plowshares", "Dark Ritual",
        "Giant Growth", "Serra Angel", "Shivan Dragon", "Brainstorm", "Hypnotic Specter", "Wrath of God", "Birds of Paradise",
        "Sol Ring", "Fireball", "Ancestral Recall", "Terror", "Shock", "Opt")
    private val costs = listOf("{R}", "{U}{U}", "{G}", "{W}", "{B}", "{G}", "{3}{W}{W}", "{4}{R}{R}", "{U}", "{B}{B}", "{2}{W}{W}",
        "{G}", "{1}", "{X}{R}", "{U}", "{1}{B}", "{R}", "{U}")
    private val copies = names.indices.map { DraftCopy("copy-$it", "definition-$it") }
    private val definitions = copies.associate {
        val n = it.id.substringAfter("copy-").toInt()
        it.definition to CardDefinition(names[n], listOf(DefinitionFace("face", names[n], manaCost = costs[n], scryfallId = "print-$n")))
    }
    private val palette = listOf(0xff8a3b2e, 0xff2e6b8a, 0xff3f7d3a, 0xff7a5a2a, 0xff5b3f8a, 0xff2a7a72, 0xff8a2e5e, 0xff6b6b2e,
        0xff3b4f8a, 0xff4f6b2e, 0xff8a6b2e, 0xff2e4f6b).map { it.toInt() }
    private val service by lazy { CardArtworkService(RuntimeEnvironment.getApplication(), temporary.newFolder()) }
    private val columns by lazy {
        PodPickColumns(RuntimeEnvironment.getApplication().getSharedPreferences("limited-shots", Context.MODE_PRIVATE), "columns", 4)
    }

    @After fun uninstall() { installArtwork(null); service.close() }

    private fun installArtwork(value: CardArtworkService?) {
        val provider = PublicArtwork::class.java.getDeclaredField("provider").apply { isAccessible = true }.get(PublicArtwork)
        provider.javaClass.getDeclaredField("service").apply { isAccessible = true }.set(provider, value)
    }

    private fun cardBitmap(width: Int, height: Int, color: Int): Bitmap {
        val w = minOf(width, (height * 488f / 680f).toInt()).coerceAtLeast(1)
        val h = minOf(height, (width * 680f / 488f).toInt()).coerceAtLeast(1)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xff111111.toInt())
            val paint = Paint().apply { this.color = color }
            val inset = w * .05f
            canvas.drawRect(inset, inset, w - inset, h - inset, paint)
            paint.color = 0xffd8d0b8.toInt()
            canvas.drawRect(inset * 1.4f, h * .045f, w - inset * 1.4f, h * .105f, paint)
            paint.color = 0xffe8dcc0.toInt()
            canvas.drawRect(inset * 1.4f, h * .115f, w - inset * 1.4f, h * .55f, paint)
        }
    }

    /**
     * Shows [content] and seeds decoded artwork for every card node tagged [tag] + copy id at its exact decode box, then
     * remounts so every card loads it at once.
     */
    private fun show(tag: (DraftCopy) -> String, shown: List<DraftCopy>, content: @Composable () -> Unit) {
        var generation by mutableIntStateOf(0)
        installArtwork(service)
        compose.setContent { ScreenTheme { NoRipples { key(generation) { content() } } } }
        for (copy in shown) {
            val size = compose.onNodeWithTag(tag(copy)).fetchSemanticsNode().size
            val n = copy.id.substringAfter("copy-").toInt()
            val reference = ArtworkReference("print-$n", 0, copy.definition, "normal", null, null)
            service.loader.memoryCache!![MemoryCache.Key(CardArtworkRequest(reference, size.width, size.height).key)] =
                MemoryCache.Value(cardBitmap(size.width, size.height, palette[n % palette.size]).asImage())
        }
        compose.runOnIdle { generation++ }
        compose.waitUntil(5_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)).fetchSemanticsNodes().size >= shown.size
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    private fun shot(name: String, caption: String) {
        File(SCREENSHOTS).mkdirs()
        compose.onRoot().captureShot(File(SCREENSHOTS, "$name.png").path, caption)
    }

    // DRF-144
    @Test fun rotisseriePoolWithPicksAndASelection() {
        val board = copies.take(12).mapIndexed { index, copy ->
            LimitedSlot(copy, when (index) { 0 -> "bob"; 4 -> "carol"; 7 -> "alice"; else -> null })
        }
        val view = LimitedView(DraftMode.ROTISSERIE, "alice", listOf("alice", "bob", "carol"), listOf(copies[7]), false,
            currentPlayer = "alice", turn = 3, turns = 135, board = board)
        show({ "pod-rotisserie-${it.id}" }, board.map { it.copy }) {
            PodRotisserieView(view, members, definitions, false, { _, _, _, _, _ -> }, Modifier.fillMaxSize(), columns,
                remember { CardInspectionController() })
        }
        shot("pod-rotisserie", "Rotisserie draft for three on Alice's turn: the whole pool sorted by colour, " +
            "with the three cards already taken greyed and marked with their picker's avatar.")
        compose.onNodeWithTag("pod-rotisserie-copy-6").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("Pick Serra Angel").assertIsEnabled()
        shot("pod-rotisserie-selected", "Rotisserie draft after Alice taps Serra Angel: the card is framed and Confirm reads " +
            "\"Pick Serra Angel\".")
    }

    // DRF-146
    @Test fun rochesterOpenPacksWithHeadersAndPicks() {
        fun pack(index: Int, opener: String, current: String?, picked: Map<Int, String>): LimitedPackView {
            val cards = copies.drop(index * 6).take(6)
            return LimitedPackView("pack-$index", index + 1, opener, cards.mapIndexed { i, copy -> LimitedSlot(copy, picked[i]) },
                current, picked.size)
        }
        val packs = listOf(pack(0, "alice", "bob", mapOf(2 to "alice")), pack(1, "bob", "alice", mapOf(0 to "bob", 4 to "carol")),
            pack(2, "carol", "alice", mapOf(5 to "carol", 1 to "alice", 3 to "bob")))
        val view = LimitedView(DraftMode.ROCHESTER, "alice", listOf("alice", "bob", "carol"), emptyList(), false,
            packs = packs, round = 1, rounds = 3)
        show({ "pod-rochester-${it.id}" }, copies) {
            PodRochesterView(view, members, definitions, false, { _, _, _, _, _ -> }, Modifier.fillMaxSize(), columns,
                remember { CardInspectionController() })
        }
        compose.onNodeWithTag("pod-rochester-copy-7").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        shot("pod-rochester", "Rochester draft for three, round 1: Alice's two packs to pick from come first, the pack " +
            "waiting for Bob last; taken cards are greyed with their picker's avatar and Alice has framed one card.")
    }

    // DRF-148
    @Test fun winstonPilesWithSeenCardsAndBacks() {
        val piles = listOf(listOf(copies[0], null), listOf(copies[1], copies[2]), listOf(null))
        val view = LimitedView(DraftMode.WINSTON, "alice", listOf("alice", "bob"), emptyList(), false, currentPlayer = "alice",
            turn = 4, stack = 31, piles = piles, pile = 1)
        show({ "pod-winston-${it.id}" }, piles.flatten().filterNotNull()) {
            PodWinstonView(view, members, definitions, false, { _, _, _, _, _ -> }, Modifier.fillMaxSize(), columns,
                remember { CardInspectionController() })
        }
        shot("pod-winston", "Winston draft on Alice's turn while she looks at pile 2: cards she has seen are face up, " +
            "cards added since are backs, with Pass and Take pile 2 below.")
    }
}
