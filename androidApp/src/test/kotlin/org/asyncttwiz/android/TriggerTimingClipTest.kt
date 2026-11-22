package org.asyncttwiz.android

import android.content.Context
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.state.TargetRef
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Trigger timing (owner, 2026-11-22: "some table animations are slow", e.g. after declaring an attack the wait before the attack
 * triggers happen): gallery clips at real speed of how long the table takes from a player's action until its board settles,
 * with the default settings (Card move speed 1×, "Show plays and combat" 1.2 s, Quick). Alice's whole app plays them
 * ([LiveTableKit]: her session, engine game and [TableScreen] on the simulated server, Bob's phone answering at once), so each
 * wait is the app's own on the clock: the plays shown and their flights (HIS-112, HIS-133, HIS-136, HIS-138), the target
 * arrows (STK-058, STK-127, STK-128, HIS-230), resolutions (TBL-150), the combat holds (HIS-113) and playback (HIS-021). The
 * engine's own working time isn't in them (the clock stands still while it works). Each clip's moments are written beside
 * the build's clip frames (build/outputs/clips/timing).
 */
@org.junit.Ignore("Work in progress: on the app as it is, playback never goes on after a play shown's arrows unless something " +
    "unrelated recomposes the table (EnginePlayback's effect is keyed on EngineGame.version, not snapshot state); clips wait " +
    "for the owner's decision")
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TriggerTimingClipTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var kit: LiveTableKit? = null
    private var now = 0L
    private val moments = mutableListOf<String>()
    private var toolkit: TableToolkitController? = null

    @After fun forget() {
        AppLog.observer = null
        TableProbes.composedWith = null
        PlayShowProbe.listener = null
        kit?.close()
        AutomatumBoards.reset()
        for (name in listOf("history-playback", "card-move-animation"))
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private val looper get() = shadowOf(Looper.getMainLooper())

    /** The table's background work (Automatum's combat and casts, worked out off the main thread) done and its results in,
     * so each frame shows the same whatever the machine's speed. */
    private fun automatumIdle() = repeat(3) {
        val done = AtomicBoolean(false)
        val board = AtomicBoolean(false)
        AutomatumWork.Background.run({ }) { done.set(true) }
        AutomatumBoards.boardWorker.dispatch(kotlin.coroutines.EmptyCoroutineContext, Runnable { board.set(true) })
        val until = System.nanoTime() + 30_000_000_000L
        while (!done.get() || !board.get() || backgroundBusy()) {
            looper.idle()
            Thread.sleep(1)
            check(System.nanoTime() < until) { "Automatum's background work never ended" }
        }
        looper.idle()
        compose.waitForIdle()
    }

    /** Whether a thread the table works on in the background (Automatum's boards, the shared panels' values on the coroutines'
     * default threads) is working now. */
    private fun backgroundBusy(): Boolean = Thread.getAllStackTraces().keys.any { t ->
        t.state == Thread.State.RUNNABLE && (t.name.startsWith("DefaultDispatcher-worker") || t.name.startsWith("automatum"))
    }

    /** One frame's step of the world besides the table: the main looper's clock moves with the frame clock ([advance] ms), the
     * network and Bob's phone go on, the background work comes in. */
    private fun world(advance: Long) {
        if (advance > 0) looper.idleFor(Duration.ofMillis(advance))
        kit!!.pump()
        automatumIdle()
    }

    /** [millis] of the app's time with nobody touching it. */
    private fun run(millis: Long) {
        var left = millis
        while (left > 0) {
            compose.mainClock.advanceTimeBy(CLIP_STEP_MILLIS)
            world(CLIP_STEP_MILLIS)
            left -= CLIP_STEP_MILLIS
        }
    }

    /** Alice's app opened on [kit]'s game at its head, her table drawn and settled. */
    private fun openTable(kit: LiveTableKit) {
        this.kit = kit
        compose.mainClock.autoAdvance = false
        kit.open()
        kit.log = { moments += "+${now - CLIP_LEAD_MILLIS} ms  $it" }
        AppLog.observer = { _, line -> moments += "+${now - CLIP_LEAD_MILLIS} ms  ${line.take(400)}" }
        PlayShowProbe.listener = { _, show, repeats, millis, flight -> moments += "+${now - CLIP_LEAD_MILLIS} ms  probe: play ${show.entry} shows $millis ms (repeat $repeats), flight $flight ms" }
        TableProbes.composedWith = { _, inputs -> inputs.filterIsInstance<TableToolkitController>().firstOrNull()?.let { toolkit = it } }
        compose.setContent { MaterialTheme { NoRipples { TableScreen(kit.lobby, kit.f.a, {}, false) } } }
        world(0)
        val m0 = monotonicMillis()
        run(3_000)
        moments += "monotonic clock moved ${monotonicMillis() - m0} ms over 3000 ms of the test's"
    }

    private fun name(id: String) = listOf(kit!!.f.game.inviter, kit!!.f.game.recipient).single { it.accountId == id }.username

    private fun SemanticsNode.description(): String = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

    private fun battlefield(owner: String) = compose.onAllNodes(hasContentDescription("${name(owner)} Battlefield,", substring = true))
        .fetchSemanticsNodes().single().boundsInRoot

    /** The card nodes [name] of [owner]'s Alice's screen draws (hand, battlefield, Stack). */
    private fun cards(name: String, owner: String): List<SemanticsNode> =
        compose.onAllNodes(hasContentDescription("$name, owner ${name(owner)}, ", substring = true)).fetchSemanticsNodes()
            .filter { it.description().startsWith("$name, owner ${name(owner)}, ") }

    /** A tap on the leftmost [name] in Alice's hand. */
    private fun tapInHand(name: String) {
        val field = battlefield(kit!!.f.a)
        val card = cards(name, kit!!.f.a).filter { it.boundsInRoot.center.y > field.bottom }.minBy { it.boundsInRoot.left }
        compose.onNode(SemanticsMatcher("node #${card.id}") { it.id == card.id }).performClick()
    }

    /** A tap on the leftmost [name] on Alice's battlefield. */
    private fun tapOnField(name: String, index: Int = 0) {
        val field = battlefield(kit!!.f.a)
        val card = cards(name, kit!!.f.a).filter { field.contains(it.boundsInRoot.center) }.sortedBy { it.boundsInRoot.left }[index]
        compose.onNode(SemanticsMatcher("node #${card.id}") { it.id == card.id }).performClick()
    }

    private fun saying(label: String) = hasContentDescription(label) or SemanticsMatcher.expectValue(MainButtonStep, label)

    private fun mainButton(label: String) = compose.onAllNodes(hasTestTag("main-button") and saying(label) and hasClickAction())
        .fetchSemanticsNodes().isNotEmpty()

    private fun tapMain(label: String) = compose.onNode(hasTestTag("main-button") and saying(label) and hasClickAction()).performClick()

    /** What Alice's table holds now, for the clip's moments. */
    private fun sample(): String {
        val shown = kit!!.lobby.engineGame()?.shown ?: return "no game"
        val s = shown.engine
        val device = kit!!.alice.device
        return "v${shown.version} head ${shown.localHead.take(8)} behind ${device.session.behind} buffered ${device.session.buffered.size} " +
            "status ${shown.status::class.simpleName}, ${s.turn.step}, stack ${s.stack.size}" +
            (shown.show?.let { ", play shown ${it.kind::class.simpleName} ${it.entry}" } ?: "") +
            (shown.combatHold?.let { ", combat hold ${it.kind::class.simpleName}" } ?: "") +
            ", life ${s.players.joinToString { "${it.id.value.take(5)}=${it.life}" }}" +
            ((toolkit?.engine as? EngineTableRoutes)?.let { r -> r.stackAnimation.let { a -> ", stack animation busy ${a.busy} " +
                "pending ${a.pending?.take(20)} targeting ${a.targeting?.entry?.take(20)} done ${a.targeting?.done} " +
                "settled ${a.settledNode?.take(8)} paused ${r.viewPause.paused} ready ${r.showReady()} motion ${r.motionBusy()} " +
                "banner holds ${r.banners.holdsPlays}" } } ?: "") +
            ", play node ${compose.onAllNodes(hasTestTag(PLAY_SHOW_TAG)).fetchSemanticsNodes().size}"
    }

    /**
     * The clip [file] of [millis] (from [CLIP_LEAD_MILLIS] before the action), the table running on by itself; [act] does the
     * player's action at the lead, [during] runs before each frame after it with the time since the action. Its moments
     * (what the table held, frame by frame when it changed, and the app log's game lines) go to build/outputs/clips/timing.
     */
    private fun clip(file: String, caption: String, millis: Long, act: () -> Unit, during: (Long) -> Unit = {}) {
        var last = ""
        compose.captureClip("$SCREENSHOTS/clips/$file", caption, millis) { at ->
            now = at
            world(if (at == 0L) 0 else CLIP_STEP_MILLIS)
            if (at == CLIP_LEAD_MILLIS) act()
            if (at > CLIP_LEAD_MILLIS) during(at - CLIP_LEAD_MILLIS)
            compose.waitForIdle()
            val held = sample()
            if (held != last) { moments += "+${at - CLIP_LEAD_MILLIS} ms  table: $held"; last = held }
        }
        moments += "Alice's device's record at the end:"
        moments += kit!!.alice.repro()?.log.orEmpty().takeLast(40).map { "    $it" }
        File("build/outputs/clips/timing").apply { mkdirs() }.resolve(file.substringAfterLast('/').removeSuffix(".webp") + ".txt")
            .writeText(moments.joinToString("\n") + "\n")
    }

    // HIS-112, HIS-133, HIS-136, STK-058, STK-127, HIS-230, TBL-150 (owner, 2026-11-22: trigger timing clips): Alice casts a Spark
    // Lancer; its enters trigger (its one target, Bob's Ash Pup, chosen by itself in Quick) shows in the middle, flies to the
    // Stack, its arrow grows to the Pup and is read, and it resolves, the Pup dying
    @Test fun aSingleTargetedTrigger() {
        val f = EngineGameFixture(aliceCards = listOf(Choices.LANCER), bobCards = listOf(Choices.PUP))
        val kit = LiveTableKit(f)
        kit.keep()
        kit.playUntil("Alice's second Main 1 with Bob's Pup", plan = { game, s ->
            if (game === kit.bob && kit.inMain(game) && kit.field(Choices.PUP, kit.other, s).isEmpty()) { kit.cast(game, Choices.PUP); true } else false
        }) { kit.inMain(kit.alice) && kit.state().turn.number >= 3 }
        openTable(kit)
        tapInHand(Choices.LANCER)
        run(1_000)
        clip("trigger-timing/targeted-trigger.webp",
            "Alice casts a Spark Lancer (the test raised it with a tap, the clip starts at the tap that casts it); its enters trigger shows in the middle, flies to the Stack, its arrow grows to Bob's Ash Pup and is read, and it resolves, the Pup dying.",
            millis = 6_000, act = { tapInHand(Choices.LANCER) })
    }

    /** Taps the main button's [label] once it has shown for [after] ms (a player's quick answer), during a clip. */
    private class Answer(val label: String, val after: Long) { var seenAt: Long? = null; var done = false }

    private fun answer(answers: List<Answer>, at: Long) {
        val next = answers.firstOrNull { !it.done } ?: return
        if (!mainButton(next.label)) return
        val seen = next.seenAt ?: at.also { next.seenAt = it; moments += "+$at ms  main button says ${next.label}" }
        if (at - seen >= next.after) { next.done = true; moments += "+$at ms  the test taps ${next.label}"; tapMain(next.label) }
    }

    // HIS-113, HIS-112, HIS-133, HIS-136, HIS-138, STK-058, STK-127, HIS-230, TBL-150, TBL-205 (owner, 2026-11-22): Alice attacks
    // with an Ember Raider and a Spark Harrier, each with an attack trigger (the Harrier's at Bob, its one target)
    @Test fun yourAttackWithTwoAttackTriggers() {
        val f = EngineGameFixture(aliceCards = listOf(Choices.RAIDER, Choices.HARRIER), bobCards = listOf("Mountain"))
        val kit = LiveTableKit(f)
        kit.keep()
        kit.playUntil("Alice declaring attackers with her Raider and Harrier", plan = { game, s ->
            if (game !== kit.alice || !kit.inMain(game)) false
            else if (kit.field(Choices.RAIDER, kit.me, s).isEmpty()) { kit.cast(game, Choices.RAIDER); true }
            else if (kit.field(Choices.HARRIER, kit.me, s).isEmpty()) { kit.cast(game, Choices.HARRIER); true }
            else false
        }) { kit.declaring(kit.alice) && kit.field(Choices.RAIDER, kit.me).isNotEmpty() && kit.field(Choices.HARRIER, kit.me).isNotEmpty() }
        openTable(kit)
        tapOnField(Choices.RAIDER)
        run(600)
        tapOnField(Choices.HARRIER)
        run(1_000)
        val answers = listOf(Answer("OK", 1_000))
        clip("trigger-timing/your-attack.webp",
            "Alice attacks Bob with her staged Ember Raider and Spark Harrier, each with an attack trigger.",
            millis = 12_000, act = { tapMain("Attack (2)") }, during = { answer(answers, it) })
    }

    // HIS-113, HIS-021, HIS-112, STK-058, STK-127, HIS-230, TBL-150, TBL-206 (owner, 2026-11-22): Bob attacks Alice with a Spark
    // Harrier, its attack trigger at her; her phone plays it back
    @Test fun theOtherPlayersAttackWithAnAttackTrigger() {
        val f = EngineGameFixture(aliceCards = listOf("Mountain"), bobCards = listOf(Choices.HARRIER))
        val kit = LiveTableKit(f)
        kit.keep()
        kit.playUntil("Bob declaring attackers with his Harrier", plan = { game, s ->
            if (game === kit.bob && kit.inMain(game) && kit.field(Choices.HARRIER, kit.other, s).isEmpty()) { kit.cast(game, Choices.HARRIER); true } else false
        }) { kit.declaring(kit.bob) && kit.field(Choices.HARRIER, kit.other, kit.state(f.b)).isNotEmpty() }
        openTable(kit)
        clip("trigger-timing/their-attack.webp",
            "Bob attacks Alice with a Spark Harrier, seen on her phone.",
            millis = 12_000, act = {
                val harrier = kit.field(Choices.HARRIER, kit.other, kit.state(f.b)).single()
                kit.bob.send(listOf(Input.DeclareAttackers(kit.other, listOf(Attack(harrier, TargetRef.Player(kit.me))))))
                kit.pump()
            })
    }

    // HIS-112, HIS-133, HIS-136, HIS-138, TBL-150 (owner, 2026-11-22): Alice casts a Kindle Herald with her Kindle Warden out: the
    // Herald's enters trigger makes a token, and the Herald and the token entering each trigger the Warden
    @Test fun yourCreatureWhoseTokenTriggersAnother() {
        val f = EngineGameFixture(aliceCards = listOf(Choices.WARDEN, Choices.KINDLE_HERALD), bobCards = listOf("Mountain"))
        val kit = LiveTableKit(f)
        kit.keep()
        kit.playUntil("Alice's Main 1 with her Warden out", plan = { game, s ->
            if (game === kit.alice && kit.inMain(game) && kit.field(Choices.WARDEN, kit.me, s).isEmpty()) { kit.cast(game, Choices.WARDEN); true } else false
        }) { kit.inMain(kit.alice) && kit.field(Choices.WARDEN, kit.me).isNotEmpty() }
        openTable(kit)
        tapInHand(Choices.KINDLE_HERALD)
        run(1_000)
        val answers = listOf(Answer("OK", 1_000))
        clip("trigger-timing/your-token-chain.webp",
            "Alice casts a Kindle Herald with her Kindle Warden out.",
            millis = 14_000, act = { tapInHand(Choices.KINDLE_HERALD) }, during = { answer(answers, it) })
    }

    // HIS-112, HIS-021, HIS-133, HIS-136, HIS-138, TBL-150, TBL-206 (owner, 2026-11-22): Bob casts the same chain, his phone
    // ordering his triggers; Alice's phone plays it back
    @Test fun theOtherPlayersCreatureWhoseTokenTriggersAnother() {
        val f = EngineGameFixture(aliceCards = listOf("Mountain"), bobCards = listOf(Choices.WARDEN, Choices.KINDLE_HERALD))
        val kit = LiveTableKit(f)
        kit.keep()
        kit.playUntil("Bob's Main 1 with his Warden out", plan = { game, s ->
            if (game === kit.bob && kit.inMain(game) && kit.field(Choices.WARDEN, kit.other, s).isEmpty()) { kit.cast(game, Choices.WARDEN); true } else false
        }) { kit.inMain(kit.bob) && kit.field(Choices.WARDEN, kit.other, kit.state(f.b)).isNotEmpty() }
        openTable(kit)
        clip("trigger-timing/their-token-chain.webp",
            "Bob casts a Kindle Herald with his Kindle Warden out, seen on Alice's phone.",
            millis = 14_000, act = { kit.cast(kit.bob, Choices.KINDLE_HERALD); kit.pump() })
    }

    // HIS-138, HIS-112, TBL-150, HIS-021 (owner, 2026-11-22): Alice's Ember Unmaking destroys Bob's four Ash Mourners, each with
    // "When this creature dies, you gain 1 life.": his four triggers show one after another, each repeat shorter
    @Test fun aBoardWipeWithFourDiesTriggers() {
        val f = EngineGameFixture(aliceCards = listOf(Choices.UNMAKING), bobCards = listOf(Choices.MOURNER))
        val kit = LiveTableKit(f)
        kit.keep()
        kit.playUntil("Alice's second Main 1 with Bob's four Mourners", plan = { game, s ->
            if (game === kit.bob && kit.inMain(game) && kit.field(Choices.MOURNER, kit.other, s).size < 4) { kit.cast(game, Choices.MOURNER); true } else false
        }) { kit.inMain(kit.alice) && kit.state().turn.number >= 3 }
        openTable(kit)
        tapInHand(Choices.UNMAKING)
        run(1_000)
        clip("trigger-timing/board-wipe.webp",
            "Alice casts an Ember Unmaking, destroying Bob's four Ash Mourners.",
            millis = 16_000, act = { tapInHand(Choices.UNMAKING) })
    }
}
