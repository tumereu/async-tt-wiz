package org.asyncttwiz.android

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [kit]'s Alice's app drawn by [compose] ([TableScreen]) and run frame by frame on the test's clock: each frame the main
 * looper's clock keeps up with the frame clock, the network and Bob's phone go on ([LiveTableKit.pump]) and the table's
 * background work (Automatum's combat, casts and shared values, off the main thread) is waited for, so every frame shows the
 * same whatever the machine's speed and every wait is the app's own on the clock. [moments] gathers what happened, each line
 * with its time since [origin] (the app log's lines, what crossed Alice's connection, each play shown's time).
 */
internal class LiveTableScreen(private val compose: ComposeContentTestRule, val kit: LiveTableKit) : AutoCloseable {
    val moments = mutableListOf<String>()
    /** The frame clock's time the moments count from. */
    var origin = 0L
    private val looper get() = shadowOf(Looper.getMainLooper())
    /** The main looper's clock at the frame clock's zero. */
    private var clockBase = 0L
    /** The table's toolkit (its routes: the Stack animation), as the table told it. */
    var toolkit: TableToolkitController? = null
        private set

    private fun at() = compose.mainClock.currentTime - origin

    /** Opens Alice's app on the game at its head and draws her table, then lets it settle for [settle] ms. */
    fun open(settle: Long = 3_000) {
        compose.mainClock.autoAdvance = false
        kit.open()
        clockBase = SystemClock.uptimeMillis() - compose.mainClock.currentTime
        kit.log = { moments += "+${at()} ms  $it" }
        AppLog.observer = { _, line -> moments += "+${at()} ms  ${line.substringAfter("Z ").take(400)}" }
        PlayShowProbe.listener = { _, show, repeats, millis, flight ->
            moments += "+${at()} ms  play ${show.entry} shows $millis ms (repeat $repeats), its flight $flight ms"
        }
        TableProbes.composedWith = { _, inputs ->
            inputs.filterIsInstance<TableToolkitController>().firstOrNull()?.takeIf { it !== toolkit }?.let { found ->
                toolkit = found
                (found.engine as? EngineTableRoutes)?.stackAnimation?.let(::onFrameClock)
                // The work the table does for its shown board on the coroutines' threads (the shared panels' values, the
                // combat worked out) done on the main thread as it is asked, as tests of the table do: no thread to wait for
                TableToolkitController::class.java.getDeclaredField("automatumWork").apply { isAccessible = true }
                    .set(found, AutomatumWork.Inline)
            }
        }
        compose.setContent { MaterialTheme { NoRipples { TableScreen(kit.lobby, kit.f.a, {}, false) } } }
        world()
        run(settle)
    }

    /**
     * The Stack animation's clock (its chains of repeats, HIS-138, TBL-150: the wall clock on a phone) on the frame clock, as
     * a phone's runs with its frames: under test the wall clock runs at the machine's pace, and the main looper's runs ahead of
     * the frames (each wait for the composition and each capture moves it on).
     */
    private fun onFrameClock(animation: StackAnimation) {
        val field = StackAnimation::class.java.getDeclaredField("clock").apply { isAccessible = true }
        field.set(animation, { compose.mainClock.currentTime })
    }

    /** The world besides the table for the frame now: the main looper's (Robolectric's) clock kept up with the frame clock
     * (it may run ahead: each wait for the composition and each capture moves it on), the network and Bob's phone, the
     * background work. */
    fun world() {
        val behind = clockBase + compose.mainClock.currentTime - SystemClock.uptimeMillis()
        if (behind > 0) looper.idleFor(Duration.ofMillis(behind)) else looper.idle()
        kit.pump()
        backgroundIdle()
    }

    /** Moves the frame clock on by [millis] a frame at a time, the table's background work done after each frame, so what a
     * frame started comes in at the same frame whatever the machine's speed. */
    fun advance(millis: Long) {
        var left = millis
        while (left > 0) {
            val step = minOf(FRAME_MILLIS, left)
            // What the last frame (or a capture's wait for the composition) started is done before the next frame
            backgroundDone()
            compose.mainClock.advanceTimeBy(step)
            backgroundDone()
            left -= step
        }
    }

    /** [millis] of the app's time with nobody touching it, a frame step at a time. */
    fun run(millis: Long) {
        var left = millis
        while (left > 0) {
            advance(CLIP_STEP_MILLIS)
            world()
            left -= CLIP_STEP_MILLIS
        }
    }

    /** Runs frame steps until [done] (at most [millis] of the app's time): false when it never held. */
    fun runUntil(millis: Long, done: () -> Boolean): Boolean {
        var left = millis
        while (left > 0) {
            if (done()) return true
            advance(CLIP_STEP_MILLIS)
            world()
            left -= CLIP_STEP_MILLIS
        }
        return done()
    }

    /** The table's background work done and its results in, composed, and what that started done too. Each wait for the
     * composition to be idle moves the main looper's clock on by about a frame, so there is one. */
    private fun backgroundIdle() {
        repeat(3) {
            backgroundDone()
            compose.waitForIdle()
        }
        backgroundDone()
    }

    /** Automatum's workers have done what was given them before now, no background thread has worked for a few
     * milliseconds (a coroutine just sent to the default threads takes a moment to wake one), and their results posted to
     * the main thread are in. */
    private fun backgroundDone() {
        val done = AtomicBoolean(false)
        val board = AtomicBoolean(false)
        AutomatumWork.Background.run({ }) { done.set(true) }
        AutomatumBoards.boardWorker.dispatch(kotlin.coroutines.EmptyCoroutineContext, Runnable { board.set(true) })
        val until = System.nanoTime() + 30_000_000_000L
        var quiet = 0
        while (!done.get() || !board.get() || quiet < 5) {
            looper.idle()
            Thread.sleep(1)
            quiet = if (backgroundBusy()) 0 else quiet + 1
            check(System.nanoTime() < until) { "The table's background work never ended" }
        }
        looper.idle()
    }

    /** Whether a thread the table works on in the background (Automatum's, the shared panels' values on the coroutines'
     * default threads) is working now. */
    private fun backgroundBusy(): Boolean = Thread.getAllStackTraces().keys.any { t ->
        t.state == Thread.State.RUNNABLE && (t.name.startsWith("DefaultDispatcher-worker") || t.name.startsWith("automatum"))
    }

    private fun name(id: String) = listOf(kit.f.game.inviter, kit.f.game.recipient).single { it.accountId == id }.username

    private fun SemanticsNode.description(): String = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

    private fun battlefield(owner: String) = compose.onAllNodes(hasContentDescription("${name(owner)} Battlefield,", substring = true))
        .fetchSemanticsNodes().single().boundsInRoot

    /** The card nodes [name] of [owner]'s Alice's screen draws (hand, battlefield, Stack). */
    private fun cards(name: String, owner: String): List<SemanticsNode> =
        compose.onAllNodes(hasContentDescription("$name, owner ${name(owner)}, ", substring = true)).fetchSemanticsNodes()
            .filter { it.description().startsWith("$name, owner ${name(owner)}, ") }

    private fun tap(node: SemanticsNode) = compose.onNode(SemanticsMatcher("node #${node.id}") { it.id == node.id }).performClick()

    /** A tap on the leftmost [name] in Alice's hand (the first raises it, a second casts it). */
    fun tapInHand(name: String) {
        val field = battlefield(kit.f.a)
        tap(cards(name, kit.f.a).filter { it.boundsInRoot.center.y > field.bottom }.minBy { it.boundsInRoot.left })
    }

    /** A tap on the [index]th [name] from the left on Alice's battlefield. */
    fun tapOnField(name: String, index: Int = 0) {
        val field = battlefield(kit.f.a)
        tap(cards(name, kit.f.a).filter { field.contains(it.boundsInRoot.center) }.sortedBy { it.boundsInRoot.left }[index])
    }

    /** Whether [name] of [owner]'s lies on that player's battlefield on Alice's screen. */
    fun onField(name: String, owner: String): Boolean {
        val field = battlefield(owner)
        return cards(name, owner).any { field.contains(it.boundsInRoot.center) }
    }

    private fun saying(label: String) = hasContentDescription(label) or SemanticsMatcher.expectValue(MainButtonStep, label)

    /** Whether the main button says [label]. */
    fun mainButton(label: String) = compose.onAllNodes(hasTestTag("main-button") and saying(label) and hasClickAction())
        .fetchSemanticsNodes().isNotEmpty()

    fun tapMain(label: String) = compose.onNode(hasTestTag("main-button") and saying(label) and hasClickAction()).performClick()

    /** Whether a play shows large in the middle of Alice's table (HIS-112). */
    fun playShown() = compose.onAllNodes(hasTestTag(PLAY_SHOW_TAG)).fetchSemanticsNodes().isNotEmpty()

    /** What Alice's table holds now (the device's shown state and the table's Stack animation), for the moments. */
    fun sample(): String {
        val shown = kit.lobby.engineGame()?.shown ?: return "no game"
        val s = shown.engine
        return "board v${shown.version}, ${shown.status::class.simpleName}, ${s.turn.step}, Stack ${s.stack.size}" +
            (shown.show?.let { ", play to show ${it.kind::class.simpleName} ${it.entry}" } ?: "") +
            (shown.combatHold?.let { ", combat hold ${it.kind::class.simpleName}" } ?: "") +
            (if (playShown()) ", a play shown" else "") +
            ((toolkit?.engine as? EngineTableRoutes)?.stackAnimation?.let { a ->
                (a.targeting?.takeIf { !it.done }?.let { ", arrows of ${it.entry}" } ?: "") + (a.pending?.let { ", arrows pending of $it" } ?: "")
            } ?: "") +
            ", life " + s.players.joinToString { "${name(it.id.value)} ${it.life}" } +
            ", main looper clock ${SystemClock.uptimeMillis() - clockBase - compose.mainClock.currentTime} ms ahead"
    }

    override fun close() {
        AppLog.observer = null
        TableProbes.composedWith = null
        PlayShowProbe.listener = null
        kit.close()
        AutomatumBoards.reset()
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (name in listOf("history-playback", "card-move-animation"))
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }
}

/** Trigger timing's games (owner, 2026-11-22), each played to the point its action starts from, before Alice's app opens. */
internal object TriggerTimingGames {
    /** Alice's second Main 1, a Spark Lancer in her hand, Bob's one Ash Pup on the battlefield. */
    fun lancerAtPup(): LiveTableKit {
        val kit = LiveTableKit(EngineGameFixture(aliceCards = listOf(Choices.LANCER), bobCards = listOf(Choices.PUP)))
        kit.keep()
        kit.playUntil("Alice's second Main 1 with Bob's Pup", plan = { game, s ->
            if (game === kit.bob && kit.inMain(game) && kit.field(Choices.PUP, kit.other, s).isEmpty()) { kit.cast(game, Choices.PUP); true }
            else false
        }) { kit.inMain(kit.alice) && kit.state().turn.number >= 3 }
        return kit
    }

    /** Alice declaring attackers in her first turn with an Ember Raider and a Spark Harrier (both with haste); Bob has no
     * creatures. */
    fun raiderAndHarrier(): LiveTableKit {
        val kit = LiveTableKit(EngineGameFixture(aliceCards = listOf(Choices.RAIDER, Choices.HARRIER), bobCards = listOf("Mountain")))
        kit.keep()
        kit.playUntil("Alice declaring attackers with her Raider and Harrier", plan = { game, s ->
            if (game !== kit.alice || !kit.inMain(game)) false
            else if (kit.field(Choices.RAIDER, kit.me, s).isEmpty()) { kit.cast(game, Choices.RAIDER); true }
            else if (kit.field(Choices.HARRIER, kit.me, s).isEmpty()) { kit.cast(game, Choices.HARRIER); true }
            else false
        }) { kit.declaring(kit.alice) && kit.field(Choices.RAIDER, kit.me).isNotEmpty() && kit.field(Choices.HARRIER, kit.me).isNotEmpty() }
        return kit
    }

    /** Bob declaring attackers in his first turn with a Spark Harrier; Alice has no creatures. */
    fun bobsHarrier(): LiveTableKit {
        val kit = LiveTableKit(EngineGameFixture(aliceCards = listOf("Mountain"), bobCards = listOf(Choices.HARRIER)))
        kit.keep()
        kit.playUntil("Bob declaring attackers with his Harrier", plan = { game, s ->
            if (game === kit.bob && kit.inMain(game) && kit.field(Choices.HARRIER, kit.other, s).isEmpty()) { kit.cast(game, Choices.HARRIER); true }
            else false
        }) { kit.declaring(kit.bob) && kit.field(Choices.HARRIER, kit.other, kit.state(kit.f.b)).isNotEmpty() }
        return kit
    }

    /** Alice's first Main 1 with her Kindle Warden on the battlefield and a Kindle Herald in her hand. */
    fun aliceWardenAndHerald(): LiveTableKit {
        val kit = LiveTableKit(EngineGameFixture(aliceCards = listOf(Choices.WARDEN, Choices.KINDLE_HERALD), bobCards = listOf("Mountain")))
        kit.keep()
        kit.playUntil("Alice's Main 1 with her Warden out", plan = { game, s ->
            if (game === kit.alice && kit.inMain(game) && kit.field(Choices.WARDEN, kit.me, s).isEmpty()) { kit.cast(game, Choices.WARDEN); true }
            else false
        }) { kit.inMain(kit.alice) && kit.field(Choices.WARDEN, kit.me).isNotEmpty() }
        return kit
    }

    /** Bob's first Main 1 with his Kindle Warden on the battlefield and a Kindle Herald in his hand. */
    fun bobWardenAndHerald(): LiveTableKit {
        val kit = LiveTableKit(EngineGameFixture(aliceCards = listOf("Mountain"), bobCards = listOf(Choices.WARDEN, Choices.KINDLE_HERALD)))
        kit.keep()
        kit.playUntil("Bob's Main 1 with his Warden out", plan = { game, s ->
            if (game === kit.bob && kit.inMain(game) && kit.field(Choices.WARDEN, kit.other, s).isEmpty()) { kit.cast(game, Choices.WARDEN); true }
            else false
        }) { kit.inMain(kit.bob) && kit.field(Choices.WARDEN, kit.other, kit.state(kit.f.b)).isNotEmpty() }
        return kit
    }

    /** Alice's second Main 1 with Ember Unmakings in her hand; Bob has four Ash Mourners on the battlefield. */
    fun unmakingAtMourners(): LiveTableKit {
        val kit = LiveTableKit(EngineGameFixture(aliceCards = listOf(Choices.UNMAKING), bobCards = listOf(Choices.MOURNER)))
        kit.keep()
        kit.playUntil("Alice's second Main 1 with Bob's four Mourners", plan = { game, s ->
            if (game === kit.bob && kit.inMain(game) && kit.field(Choices.MOURNER, kit.other, s).size < 4) { kit.cast(game, Choices.MOURNER); true }
            else false
        }) { kit.inMain(kit.alice) && kit.state().turn.number >= 3 }
        return kit
    }
}

/** A Compose frame on the test clock (LiveTableScreen.advance steps the clock by it). */
private const val FRAME_MILLIS = 16L
