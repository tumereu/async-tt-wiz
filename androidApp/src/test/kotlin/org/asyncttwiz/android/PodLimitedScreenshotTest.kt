package org.asyncttwiz.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
            PlaceholderArtwork.drawArt(canvas, RectF(inset * 1.4f, h * .115f, w - inset * 1.4f, h * .55f))
        }
    }

    /**
     * Shows [content] and seeds decoded artwork for every card node tagged [tag] + copy id at its exact decode box, then
     * remounts so every card loads it at once.
     */
    private fun show(tag: (DraftCopy) -> String, shown: List<DraftCopy>, also: (DraftCopy) -> List<String> = { emptyList() },
        content: @Composable () -> Unit) {
        var generation by mutableIntStateOf(0)
        installArtwork(service)
        compose.setContent { ScreenTheme { NoRipples { key(generation) { content() } } } }
        // [also]: more nodes of the same card (shown twice), seeded at their own decode boxes.
        val nodes = shown.flatMap { copy -> (listOf(tag(copy)) + also(copy)).map { it to copy } }
        for ((node, copy) in nodes) {
            val size = compose.onNodeWithTag(node).fetchSemanticsNode().size
            val n = copy.id.substringAfter("copy-").toInt()
            val reference = ArtworkReference("print-$n", 0, "normal", null, null)
            service.loader.memoryCache!![MemoryCache.Key(CardArtworkRequest(reference, size.width, size.height).key)] =
                MemoryCache.Value(cardBitmap(size.width, size.height, palette[n % palette.size]).asImage())
        }
        compose.runOnIdle { generation++ }
        compose.waitUntil(5_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)).fetchSemanticsNodes().size >= nodes.size
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    // DRF-085: the picker's avatar sits in the card's top left corner, over the name (as in Grid).
    private fun assertPickerInCorner(tag: String) {
        val card = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val badge = compose.onNodeWithTag("$tag-picker", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        kotlin.test.assertEquals(card.topLeft, badge.topLeft, "badge $badge on card $card")
    }

    private fun shot(name: String, caption: String) {
        File(SCREENSHOTS).mkdirs()
        compose.onRoot().captureShot(File(SCREENSHOTS, "$name.png").path, caption)
    }

    // DRF-153, DRF-085
    @Test fun rotisseriePoolWithPicksAndASelection() {
        val board = copies.take(12).mapIndexed { index, copy ->
            LimitedSlot(copy, when (index) { 0 -> "bob"; 4 -> "carol"; 7 -> "alice"; else -> null })
        }
        val view = LimitedView(DraftMode.ROTISSERIE, "alice", listOf("alice", "bob", "carol"), listOf(copies[7]), false,
            currentPlayer = "alice", turn = 3, turns = 135, board = board)
        show({ "pod-rotisserie-${it.id}" }, board.map { it.copy }) {
            PodRotisserieView(view, members, definitions, false, { _, _, _, _, _, _ -> }, Modifier.fillMaxSize(), columns)
        }
        shot("pod-rotisserie", "Rotisserie draft for three on Alice's turn: the whole pool sorted by colour, " +
            "with the three cards already taken greyed and marked with their picker's avatar.")
        assertPickerInCorner("pod-rotisserie-copy-0")
        compose.onNodeWithTag("pod-rotisserie-copy-6").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("Pick Serra Angel").assertIsEnabled()
        shot("pod-rotisserie-selected", "Rotisserie draft after Alice taps Serra Angel: the card is framed and Confirm reads " +
            "\"Pick Serra Angel\".")
    }

    // DRF-153, DRF-164: on another's turn a tap frames a free card and "Queue <card>" adds it to the end of your queue;
    // the strip lists the queue in order with removes, queued cards carry their number, and a cleared queue shows its note.
    @Test fun rotisseriePickQueueStripAndQueueButton() {
        val board = copies.take(12).mapIndexed { index, copy -> LimitedSlot(copy, if (index == 4) "bob" else null) }
        var view by mutableStateOf(LimitedView(DraftMode.ROTISSERIE, "alice", listOf("alice", "bob"), emptyList(), false,
            currentPlayer = "bob", turn = 1, turns = 90, board = board, pickQueue = listOf(copies[6], copies[0])))
        val queued = mutableListOf<List<String>>()
        show({ "pod-rotisserie-${it.id}" }, board.map { it.copy }) {
            PodRotisserieView(view, members, definitions, false, { _, _, _, _, _, _ -> error("no pick on Bob's turn") },
                Modifier.fillMaxSize(), columns) { queued += it }
        }
        compose.onNodeWithText("1 · Serra Angel").assertIsDisplayed()
        compose.onNodeWithText("2 · Lightning Bolt").assertIsDisplayed()
        compose.onNodeWithTag("pod-rotisserie-copy-6-badge", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Queue").assertIsNotEnabled()
        // Taps on a queued or taken card do nothing; a free card is framed for the queue.
        compose.onNodeWithTag("pod-rotisserie-copy-6").performClick()
        compose.onNodeWithTag("pod-rotisserie-copy-4").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("Queue").assertIsNotEnabled()
        compose.onNodeWithTag("pod-rotisserie-copy-7").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("Queue Shivan Dragon").assertIsEnabled()
        shot("pod-rotisserie-queue", "Rotisserie draft on Bob's turn with Alice's pick queue of two cards in the strip above " +
            "the button (numbered on their cards too), after she taps Shivan Dragon: \"Queue Shivan Dragon\" below.")
        compose.onNodeWithText("Queue Shivan Dragon").performClick()
        compose.onNodeWithTag("pod-rotisserie-queued-copy-0").performClick()
        kotlin.test.assertEquals(listOf(listOf("copy-6", "copy-0", "copy-7"), listOf("copy-6")), queued)
        // Bob took a queued card: the queue is empty and the note names the card.
        compose.runOnIdle { view = view.copy(pickQueue = emptyList(), queueCleared = copies[0]) }
        compose.onNodeWithText("Queue cleared: Lightning Bolt was taken").assertIsDisplayed()
        compose.onNodeWithTag("pod-rotisserie-queue").assertDoesNotExist()
    }

    // DRF-155, DRF-085
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
            PodRochesterView(view, members, definitions, false, { _, _, _, _, _, _ -> }, Modifier.fillMaxSize(), columns)
        }
        compose.onNodeWithTag("pod-rochester-copy-7").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        shot("pod-rochester", "Rochester draft for three, round 1: Alice's two packs to pick from come first, the pack " +
            "waiting for Bob last; taken cards are greyed with their picker's avatar and Alice has framed one card.")
        assertPickerInCorner("pod-rochester-copy-2")
    }

    // DRF-157
    @Test fun winstonPilesWithSeenCardsAndBacks() {
        val piles = listOf(listOf(copies[0], null), listOf(copies[1], copies[2]), listOf(null))
        val view = LimitedView(DraftMode.WINSTON, "alice", listOf("alice", "bob"), emptyList(), false, currentPlayer = "alice",
            turn = 4, stack = 31, piles = piles, pile = 1)
        show({ "pod-winston-${it.id}" }, piles.flatten().filterNotNull()) {
            PodWinstonView(view, members, definitions, false, { _, _, _, _, _, _ -> }, Modifier.fillMaxSize(), columns)
        }
        shot("pod-winston", "Winston draft on Alice's turn while she looks at pile 2: cards she has seen are face up, " +
            "cards added since are backs, with Pass and Take pile 2 below.")
    }

    // DRF-159
    @Test fun winchesterFaceUpPilesWithAChosenPile() {
        val piles = listOf(listOf(copies[0], copies[1]), listOf(copies[2]), listOf(copies[3], copies[4], copies[5]), listOf(copies[6]))
        val view = LimitedView(DraftMode.WINCHESTER, "alice", listOf("alice", "bob"), emptyList(), false, currentPlayer = "alice",
            turn = 2, stack = 24, piles = piles)
        show({ "pod-winchester-${it.id}" }, piles.flatten()) {
            PodWinchesterView(view, members, definitions, false, { _, _, _, _, _, _ -> }, Modifier.fillMaxSize(), columns)
        }
        compose.onNodeWithTag("pod-winchester-copy-4").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("Take pile 3 (3 cards)").assertIsEnabled()
        shot("pod-winchester", "Winchester draft on Alice's turn after she taps a card of pile 3: the four face-up piles, " +
            "pile 3 framed, and \"Take pile 3 (3 cards)\" below.")
    }

    // DRF-158, DRF-159: several tables get a chip row; the screen opens on a table where it is your pick, shows only that
    // table's piles, and moves to another such table once the server's answer to your take shows.
    @Test fun winchesterTablesChipRowOpensOnYourPick() {
        val first = listOf(listOf(copies[0], copies[1]), listOf(copies[2]), listOf(copies[3]))
        val second = listOf(listOf(copies[4]), listOf(copies[5], copies[6], copies[7]), listOf(copies[8]))
        fun table(player: String?, turn: Int, stack: Int, piles: List<List<DraftCopy>>) = LimitedTableView(player, turn, stack, piles)
        var view by mutableStateOf(LimitedView(DraftMode.WINCHESTER, "alice", listOf("alice", "bob"), emptyList(), false,
            currentPlayer = "bob", turn = 3, stack = 9, piles = first,
            tables = listOf(table("bob", 3, 9, first), table("alice", 3, 8, second))))
        val sent = mutableListOf<Pair<Int, Int>>()
        show({ "pod-winchester-${it.id}" }, second.flatten()) {
            PodWinchesterView(view, members, definitions, false, { _, turn, _, _, pile, table -> sent += table to pile!!; check(turn == 3) },
                Modifier.fillMaxSize(), columns)
        }
        compose.onNodeWithText("Table 1 · Bob picks").assertIsDisplayed().assertIsNotSelected()
        compose.onNodeWithText("Table 2 · Your pick").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Your pick · Stack: 8 cards").assertIsDisplayed()
        compose.onNodeWithTag("pod-winchester-copy-0").assertDoesNotExist()
        compose.onNodeWithTag("pod-winchester-copy-6").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("Take pile 2 (3 cards)").assertIsEnabled()
        shot("pod-winchester-tables", "Winchester draft with two tables, opened on table 2 where it is Alice's pick (Bob picks at " +
            "table 1): the chip row on top, table 2's piles with pile 2 framed after a tap, and \"Take pile 2 (3 cards)\" below.")
        compose.onNodeWithText("Take pile 2 (3 cards)").performClick()
        kotlin.test.assertEquals(listOf(1 to 1), sent)
        // Bob took at table 1 meanwhile; Alice's take at table 2 shows: the screen moves to table 1, her pick now.
        compose.runOnIdle { view = view.copy(currentPlayer = "alice", turn = 4, stack = 6,
            tables = listOf(table("alice", 4, 6, first), table("bob", 4, 5, second))) }
        compose.waitForIdle()
        compose.onNodeWithText("Table 1 · Your pick").assertIsSelected()
        compose.onNodeWithText("Table 2 · Bob picks").assertIsNotSelected()
        compose.onNodeWithTag("pod-winchester-copy-0").assertExists()
        // A tap on a chip shows that table; there it is not your pick, so nothing can be taken.
        compose.onNodeWithText("Table 2 · Bob picks").performClick()
        compose.onNodeWithText("Waiting for Bob · Stack: 5 cards").assertIsDisplayed()
        compose.onNodeWithTag("pod-limited-confirm").assertDoesNotExist()
    }

    // DRF-156, DRF-157: Winston tables get the same chip row ("Bob looks" as its pile line says); the screen opens on the
    // table where it is your pick, a pass keeps it there and a take moves on to a table where it is your pick.
    @Test fun winstonTablesChipRowOpensOnYourPick() {
        val first = listOf(listOf(null, null), listOf<DraftCopy?>(null), listOf<DraftCopy?>(null))
        val second = listOf(listOf(copies[4], null), listOf(copies[5], copies[6]), listOf<DraftCopy?>(null))
        fun table(player: String?, turn: Int, stack: Int, piles: List<List<DraftCopy?>>, pile: Int) = LimitedTableView(player, turn, stack, piles, pile)
        var view by mutableStateOf(LimitedView(DraftMode.WINSTON, "alice", listOf("alice", "bob"), emptyList(), false,
            currentPlayer = "bob", turn = 2, stack = 12, piles = first,
            tables = listOf(table("bob", 2, 12, first, 0), table("alice", 2, 11, second, 1))))
        val sent = mutableListOf<Triple<LimitedAction, Int, Int>>()
        show({ "pod-winston-${it.id}" }, second.flatten().filterNotNull()) {
            PodWinstonView(view, members, definitions, false, { action, turn, _, _, pile, table -> sent += Triple(action, table, pile!!); check(turn == 2) },
                Modifier.fillMaxSize(), columns)
        }
        compose.onNodeWithText("Table 1 · Bob looks").assertIsDisplayed().assertIsNotSelected()
        compose.onNodeWithText("Table 2 · Your pick").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Your pick · Stack: 11 cards").assertIsDisplayed()
        compose.onNodeWithText("Pile 2 · 2 cards · you look at it").assertIsDisplayed()
        shot("pod-winston-tables", "Winston draft with two tables, opened on table 2 where it is Alice's turn (Bob looks at " +
            "table 1): the chip row on top, table 2's piles with pile 2 being looked at, and Pass and Take pile 2 below.")
        compose.onNodeWithTag("pod-winston-take").performClick()
        kotlin.test.assertEquals(listOf(Triple(LimitedAction.TAKE_PILE, 1, 1)), sent)
        // Bob's turn at table 1 ended meanwhile; Alice's take shows: the screen moves to table 1, her turn now.
        compose.runOnIdle { view = view.copy(currentPlayer = "alice", turn = 3, stack = 11, piles = first,
            tables = listOf(table("alice", 3, 11, first, 0), table("bob", 3, 10, second, 0))) }
        compose.waitForIdle()
        compose.onNodeWithText("Table 1 · Your pick").assertIsSelected()
        compose.onNodeWithText("Table 2 · Bob looks").assertIsNotSelected()
        compose.onNodeWithText("Pile 1 · 2 cards · you look at it").assertIsDisplayed()
    }

    // DRF-158: one table shows no chip row.
    @Test fun winchesterOneTableHasNoChipRow() {
        val piles = listOf(listOf(copies[0]), listOf(copies[1]))
        val view = LimitedView(DraftMode.WINCHESTER, "alice", listOf("alice", "bob"), emptyList(), false, currentPlayer = "alice",
            turn = 0, stack = 10, piles = piles, tables = listOf(LimitedTableView("alice", 0, 10, piles)))
        show({ "pod-winchester-${it.id}" }, piles.flatten()) {
            PodWinchesterView(view, members, definitions, false, { _, _, _, _, _, _ -> }, Modifier.fillMaxSize(), columns)
        }
        compose.onNodeWithTag("pod-winchester-tables").assertDoesNotExist()
        compose.onNodeWithText("Your pick · Stack: 10 cards").assertIsDisplayed()
    }

    // DRF-165: the draft history lists your picks in order, each pack as it came with keeps green and burns red, a wheel's
    // cards taken since greyed under their own line; a failure offers Retry
    @Test fun draftHistoryShowsPacksWithKeepsBurnsAndCardsTakenSince() {
        fun c(vararg n: Int) = n.map { copies[it] }
        val picks = listOf(
            DraftHistoryPick(1, 1, c(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), kept = listOf("copy-6"), burned = listOf("copy-0", "copy-2")),
            DraftHistoryPick(1, 2, c(10, 11, 12, 13, 14, 15, 16), kept = listOf("copy-13"), burned = listOf("copy-10", "copy-16")),
            DraftHistoryPick(1, 4, c(1, 3, 5, 8), kept = listOf("copy-8"), burned = listOf("copy-1"), takenSince = c(4, 7, 9)))
        val history = PodDraftHistory(picks)
        val shown = picks.flatMap { it.cards + it.takenSince }.distinctBy { it.id }
        var failed by mutableStateOf(false)
        var loaded by mutableStateOf<PodDraftHistory?>(history)
        fun at(copy: DraftCopy) = picks.indices.filter { copy in picks[it].cards || copy in picks[it].takenSince }.map { "pod-history-$it-${copy.id}" }
        show({ at(it).first() }, shown, { at(it).drop(1) }) {
            PodHistoryDialog(loaded, failed, definitions, 8, {}) {}
        }
        compose.onNodeWithText("Pack 1 · Pick 1").assertExists()
        compose.onNodeWithText("Taken since you last saw it").assertExists()
        File(SCREENSHOTS).mkdirs()
        captureShot(File(SCREENSHOTS, "pod-draft-history.png").path, "Draft history of a Glimpse draft with eight cards per " +
            "row: three of Alice's picks in order, each pack as it came with her keep green and burns red; the pack that came " +
            "round again lists the three cards taken since, greyed.")
        compose.runOnIdle { loaded = null; failed = true }
        compose.onNodeWithText("Couldn't load the draft history").assertExists()
        compose.onNodeWithTag("pod-history-retry").assertExists()
    }

    // DRF-160
    @Test fun picksDialogShowsSeenCardsAndBacksNewestFirst() {
        val picks = listOf(PodOpponentPicks(bob, listOf(null, copies[0], copies[1], null, copies[2])), PodOpponentPicks(carol, emptyList()))
        show({ "pod-picks-${it.id}" }, copies.take(3)) {
            PodPicksDialog(picks, definitions) {}
        }
        File(SCREENSHOTS).mkdirs()
        captureShot(File(SCREENSHOTS, "pod-picks.png").path, "Picks dialog of a Winston draft showing Bob's picks newest " +
            "first: cards Alice saw face up, the others as card backs, with a chip per opponent.")
    }
}
