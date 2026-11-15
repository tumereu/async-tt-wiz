package org.asyncttwiz.android

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import kotlin.math.roundToLong

/**
 * Stack animation (owner spec 2026-11-14; HIS-112, STK-058, TBL-125): the steps a spell or ability plays on its way to and
 * through the Stack. Pre-resolve: [FlyIn] from its source to the middle, the middle [Hold], the flight [ToStack] (these
 * three only for a play shown, HIS-112: never the viewer's own casts or activations, and none at "Show plays and combat"
 * Off) and the [Targeting] arrows when it is the top entry (only an entry with targets). Post-resolve: its [Resolution],
 * as long as its effect's own animations. Between the two the game may wait (a priority stop, an assumption, a choice).
 */
sealed interface StackStep {
    data object FlyIn : StackStep
    data object Hold : StackStep
    data object ToStack : StackStep
    data object Targeting : StackStep
    data object Resolution : StackStep
}

/** A step of [StackSchedule.of] and how long it takes (ms); null for [StackStep.Resolution]: its effect's own animation. */
data class StackStepTime(val step: StackStep, val millis: Long?)

/**
 * The step times of the Stack animation. All at Card move speed 1× and scaled by it (TBL-038), except the middle hold, the
 * "Show plays and combat" time (LOB-082). A repeat of the same ability in one chain ([PlayShowPace]) shortens the hold, the
 * targeting and the resolution by [PLAY_SHOW_REPEAT_FACTOR] each time, down to their floors.
 */
object StackSchedule {
    /** Step 4: an arrow grows from the entry to its target in this long. */
    const val ARROW_GROW_MILLIS = 250L
    /** Step 4: a repeat's arrows grow in no less than this. */
    const val ARROW_GROW_FLOOR_MILLIS = 100L
    /** Step 4: several targets' arrows start this far apart. */
    const val ARROW_STAGGER_MILLIS = 80L
    /** Step 4: the arrows stay drawn this long after the last has grown, before the entry may resolve. */
    const val ARROW_READ_MILLIS = 400L
    /** Step 4: a repeat's read time is no shorter than this. */
    const val ARROW_READ_FLOOR_MILLIS = 150L
    /** Step 5: the shortest share of its effect's animation a repeat's resolution takes (0.15 s of a 0.35 s card move). */
    const val RESOLUTION_FLOOR_SHARE = 150.0 / MAX_CARD_MOVE_DURATION_MILLIS
    /** Step 5: the arrows fade out over this long as the effect reaches their targets. */
    const val ARROW_FADE_MILLIS = 300L

    /** Step 1: the fly-in from the source to the middle. */
    fun flyInMillis(multiplier: Float): Long = playShowFlightMillis(multiplier).toLong()

    /** Step 2: the middle hold, for the "Show plays and combat" time [setting] (not scaled by Card move speed). */
    fun holdMillis(setting: Long, repeats: Int): Long = playShowMillis(setting, repeats)

    /** Step 3: the flight to the Stack. */
    fun toStackMillis(multiplier: Float, repeats: Int): Long = playShowFlightMillis(multiplier, repeats).toLong()

    private fun scaled(full: Long, floor: Long, multiplier: Float, repeats: Int): Long {
        val speed = cardMoveMultiplier(multiplier)
        val whole = (full / speed).roundToLong().coerceAtLeast(1)
        if (repeats <= 0) return whole
        val least = minOf(whole, (floor / speed).roundToLong().coerceAtLeast(1))
        return maxOf(least, (whole * playShowRepeatFactor(repeats)).roundToLong())
    }

    /** Step 4: how long one arrow takes to grow. */
    fun arrowGrowMillis(multiplier: Float, repeats: Int = 0): Long = scaled(ARROW_GROW_MILLIS, ARROW_GROW_FLOOR_MILLIS, multiplier, repeats)

    /** Step 4: how far apart several arrows start (shortened with the growth). */
    fun arrowStaggerMillis(multiplier: Float, repeats: Int = 0): Long =
        (ARROW_STAGGER_MILLIS * arrowGrowMillis(multiplier, repeats).toDouble() / ARROW_GROW_MILLIS).roundToLong()

    /** Step 4: how long the grown arrows stay drawn before the entry may resolve. */
    fun arrowReadMillis(multiplier: Float, repeats: Int = 0): Long = scaled(ARROW_READ_MILLIS, ARROW_READ_FLOOR_MILLIS, multiplier, repeats)

    /** Step 4 as a whole for [targets] arrows (0: the step is skipped). */
    fun targetingMillis(targets: Int, multiplier: Float, repeats: Int = 0): Long = if (targets <= 0) 0L else
        arrowGrowMillis(multiplier, repeats) + (targets - 1) * arrowStaggerMillis(multiplier, repeats) + arrowReadMillis(multiplier, repeats)

    /** Step 5: the share of its effect's animation time a resolution takes (1 the first time, shorter for a repeat). */
    fun resolutionShare(repeats: Int): Double = if (repeats <= 0) 1.0 else maxOf(RESOLUTION_FLOOR_SHARE, playShowRepeatFactor(repeats))

    /** Step 4: how far (0–1) the arrow of target [index] has grown [elapsed] ms into the step. */
    fun arrowProgress(elapsed: Long, index: Int, multiplier: Float, repeats: Int = 0): Float {
        val start = index * arrowStaggerMillis(multiplier, repeats)
        return ((elapsed - start).toFloat() / arrowGrowMillis(multiplier, repeats)).coerceIn(0f, 1f)
    }

    /**
     * The steps of one entry, in order: a play [shown] (the other player's spell or ability, or the viewer's own trigger)
     * flies in, holds and flies to the Stack when "Show plays and combat" [setting] is on; an entry with [targets] draws
     * its arrows; then it resolves. [repeats]: how many times the same ability showed before in its chain.
     */
    fun of(shown: Boolean, targets: Int, setting: Long, multiplier: Float, repeats: Int = 0): List<StackStepTime> = buildList {
        if (shown && setting > 0) {
            add(StackStepTime(StackStep.FlyIn, flyInMillis(multiplier)))
            add(StackStepTime(StackStep.Hold, holdMillis(setting, repeats)))
            add(StackStepTime(StackStep.ToStack, toStackMillis(multiplier, repeats)))
        }
        if (targets > 0) add(StackStepTime(StackStep.Targeting, targetingMillis(targets, multiplier, repeats)))
        add(StackStepTime(StackStep.Resolution, null))
    }
}

/**
 * The spec's step 1 rule ("until it lands its Stack entry isn't shown"): [this] board without the Stack entries [entries]
 * (ids "entry:…"), as it looks while their plays are shown and before they land: no entry in the Stack (the HUD, the dock's
 * list, the count), and a spell's card gone from where it was cast from (it flies in from there). The same board when none
 * of them is on it.
 */
fun TableBoard.withoutEntries(entries: Collection<String>): TableBoard {
    if (entries.isEmpty()) return this
    val gone = board.stackEffects.filter { it.id in entries }
    if (gone.isEmpty()) return this
    val ids = gone.mapTo(HashSet()) { it.id }
    val spells = gone.filter { it.item?.kind == MtgStackKind.Spell }.mapTo(HashSet()) { it.sourceId }
    val containers = board.containers.map { c ->
        when {
            c.zone == PlayZone.STACK -> c.copy(cards = c.cards.filterNot { it in ids })
            spells.isNotEmpty() && c.cards.any { it in spells } -> c.copy(cards = c.cards.filterNot { it in spells })
            else -> c
        }
    }
    return copy(board = board.copy(cards = if (spells.isEmpty()) board.cards else board.cards.filterNot { it.id in spells },
        containers = containers, stackEffects = board.stackEffects.filterNot { it.id in ids },
        stackTextMarks = board.stackTextMarks - ids))
}

/** Step 4: how many targets of the Stack entry [entry] on [play] get an arrow or a highlight (STK-058): players, cards on
 * the battlefield or in a Graveyard or Exile, and the Stack once for any number of Stack entries. */
fun stackTargetCount(play: TableBoard, entry: String): Int {
    val item = play.board.stackEffects.singleOrNull { it.id == entry }?.item ?: return 0
    val refs = item.targets.values.flatten().distinct()
    val entries = play.board.stackEffects.mapNotNullTo(HashSet()) { it.item?.id }
    var stack = false
    var count = 0
    for (ref in refs) when {
        ref is MtgRef.StackItem || ref in entries -> stack = true
        ref is MtgRef.Player -> count++
        ref is MtgRef.Card -> {
            val card = play.board.cards.singleOrNull { it.id == ref.cardId } ?: continue
            val zone = play.board.containers.singleOrNull { it.id == card.containerId }?.zone
            if (zone == PlayZone.BATTLEFIELD || zone == PlayZone.GRAVEYARD || zone == PlayZone.EXILE) count++
        }
    }
    return count + if (stack) 1 else 0
}

/** HIS-112: the board the table draws before the plays not landed yet are taken off it ([withoutEntries]): what a play
 * shown is made from. */
val LocalUnhiddenPlay = compositionLocalOf<TableBoard?> { null }

/** For the step 4 to step 5 log: what the game waits for on [shown], a finger or Alt held ([pause]) included. */
fun stackWaitText(shown: EngineGame.Shown?, pause: ViewPause?): String {
    val status = when (val s = shown?.status) {
        null -> "nothing (no board)"
        GameDevice.Status.YourMove -> "the player here (their priority stop or choice)"
        is GameDevice.Status.Waiting -> "the other player (${s.reason::class.simpleName}" + (s.offer?.let { ", Assume offered: ${it::class.simpleName}" } ?: "") + ")"
        is GameDevice.Status.Stopped -> "a playback stop at the other player's assumption"
        is GameDevice.Status.Playing -> "playback's next event"
        GameDevice.Status.CatchUp -> "the catch-up's tap"
        else -> s::class.simpleName ?: "the game"
    }
    return status + (if (pause?.paused == true) ", a finger or Alt held" else "") +
        (if (shown?.ownStepHeld == true) ", then this device's own step" else "")
}

/** How many landed entries [StackAnimation.landed] keeps (an entry's id is its object's, never reused). */
private const val LANDED_KEPT = 64

/** Tests: each step 4 as it starts on [me]'s table: the entry, its targets and its length. */
object StackAnimationProbe {
    @kotlin.concurrent.Volatile var targeting: ((me: String, entry: String, targets: Int, millis: Long) -> Unit)? = null
    /** Each step 5 as it starts on [me]'s table: the entries leaving the Stack. */
    @kotlin.concurrent.Volatile var resolving: ((me: String, entries: List<String>) -> Unit)? = null
    /** HIS-112: each play shown that landed on [me]'s table (its entry appears on the Stack from then on). */
    @kotlin.concurrent.Volatile var landed: ((me: String, entry: String) -> Unit)? = null
}

/**
 * The Stack animation's state on one table (main thread, personal: nothing is sent and the game never waits for it): the
 * plays shown that ended here ([ended]; their entries are drawn on the Stack from then on, [landed]), the top entry's
 * targeting (step 4: its arrows growing, then read) and the resolutions (step 5) with their pace. While [busy] the table
 * plays nothing past the board it draws: the board's steps, playback and the next play wait (an entry stays on the Stack
 * only as long as its own animation).
 */
@Stable
class StackAnimation(private val viewer: String, private val clock: () -> Long = { monotonicMillis() }) {
    /** HIS-112: the plays this table ended (landed, or skipped by a double tap) whose end the device hasn't published yet:
     * passed over until its list drops them (owner playtest 2026-11-11: the next play shows the moment the one before
     * lands). */
    val ended = mutableStateListOf<GameDevice.PlayShow>()

    /** The entries of plays shown that landed here (step 3 done): they appear in place on the Stack, never flying there. */
    val landed = mutableStateListOf<String>()

    /** The node of the board the table last settled on (its moves done): playback lets the next event follow an entry put
     * on the Stack once it has. */
    var settledNode by mutableStateOf<String?>(null)

    /** WEB-017: where the desktop dock's Stack list puts a new top entry (root dp; a card's size), while the dock shows; a
     * play shown flies into it (step 3). */
    var dockTop: TableBounds? = null

    /** Step 4 running now (its arrows growing or read), or null. */
    var targeting by mutableStateOf<Targeting?>(null)
        private set

    /** The top entry whose arrows are drawn whole: its step 4 done (the game may wait meanwhile, STK-058), or shown at once
     * (a board opened, taken back to or jumped to). */
    var arrowed by mutableStateOf<String?>(null)
        private set

    /** The top entry with targets the table has newly drawn: its step 4 starts once the table settles on it. */
    var pending by mutableStateOf<String?>(null)
        private set

    /** Whether a step of the Stack animation holds the table where it is (step 4 pending or running). */
    val busy: Boolean get() = pending != null || targeting?.let { !it.done } == true

    /** Step 4 of [entry] on the board drawn: [targets] arrows at Card move speed [multiplier]. */
    class Targeting(val entry: String, val targets: Int, val multiplier: Float) {
        /** How many times the same ability had step 4 before in its chain (set as it starts). */
        var repeats = 0
            internal set(value) { field = value; total = StackSchedule.targetingMillis(targets, multiplier, value) }
        /** Its whole length. */
        var total = StackSchedule.targetingMillis(targets, multiplier, 0)
            private set
        /** How long it has played (time paused by a finger not counted). */
        var elapsed by mutableLongStateOf(0L)
        var done by mutableStateOf(false)
        internal var skipped = false
        /** How far (0–1) the arrow of target [index] has grown. */
        fun progress(index: Int): Float = if (done) 1f else StackSchedule.arrowProgress(elapsed, index, multiplier, repeats)
    }

    /** How far (0–1) the arrow of target [index] of the top entry [entry] has grown; 0 before its step 4 starts. */
    fun arrowProgress(entry: String, index: Int): Float {
        // A table no engine screen drives (a board drawn on its own) draws its arrows whole
        if (!driven || arrowed == entry) return 1f
        val t = targeting?.takeIf { it.entry == entry } ?: return 0f
        return t.progress(index)
    }

    /** Whether a table draws its boards through [drawn] (the engine table does). */
    var driven by mutableStateOf(false)
        private set
    /** Whether the board drawn last was taken as it is (opened, taken back to or jumped to): nothing of the board before
     * it fades out. */
    var takenAsIs = false
        private set
    private var lastTop: String? = null
    private var previous: TableBoard? = null
    /** The board [previous] was made from: every entry the game had on its Stack, the ones kept off it ([lastHidden]) too. */
    private var previousUnhidden: TableBoard? = null
    /** The entries kept off the board drawn last while their plays shown hadn't landed. */
    private var lastHidden: Set<String> = emptySet()
    private val targetPace = PlayShowPace()
    private val resolutionPace = PlayShowPace()
    /** Step 4's end of each entry still on the Stack (wall clock) and what the game waited for then, for step 5's log. */
    private val waitingSince = HashMap<String, Pair<Long, String>>()
    /** Step 5 begun on the board drawn: the entries resolving, when it started (wall clock), its share of the effect's time. */
    private var resolving: Triple<List<String>, Long, Double>? = null

    /**
     * Step 5's pace for [play], the board the table is about to draw (read in composition, nothing changed): the share of
     * its effects' animation time it takes when it resolves a repeat of the same ability in its chain, else 1.
     */
    fun resolutionShare(play: TableBoard, unhidden: TableBoard = play): Double {
        val before = previous ?: return 1.0
        if (before.board === play.board) return 1.0
        // What the game took off the Stack (an entry only kept off the board while its play shows hasn't left it)
        val gone = CombatStackRules.stack((previousUnhidden ?: before).board).cards - CombatStackRules.stack(unhidden.board).cards.toSet()
        val key = gone.firstNotNullOfOrNull { StackAbilities.of(before, it, viewer) } ?: return 1.0
        return StackSchedule.resolutionShare(resolutionPace.peek(key, clock()))
    }

    /**
     * The table draws [play] now (live or in catch-up playback; never timeline mode): [unhidden] with the entries [hidden]
     * taken off it while their plays shown haven't landed (HIS-112). [instant]: a board opened, taken back to or jumped to:
     * its top entry's arrows show at once. A new top entry with targets makes step 4 pending; entries the game took off the
     * Stack begin step 5. An entry only kept off the board while its play shows hasn't left the Stack, and while the game's
     * top entry is one of those nothing is the top entry here yet (owner playtest 2026-11-15, report VSVLSK: the spell
     * under its cast's trigger grew its arrows before the trigger showed, and again after it resolved).
     */
    fun drawn(play: TableBoard, instant: Boolean, unhidden: TableBoard = play, hidden: Set<String> = emptySet()) {
        driven = true
        val before = previous
        if (before != null && before.board === play.board) return
        previous = play
        val beforeUnhidden = previousUnhidden
        previousUnhidden = unhidden
        val hiddenBefore = lastHidden
        lastHidden = hidden
        if (hidden != hiddenBefore) AppLog.info("game", "Stack animation: " + (if (hidden.isEmpty()) "no entry kept off the Stack"
            else "entries ${hidden.joinToString { it.take(24) }} kept off the Stack until their plays shown land") +
            (hiddenBefore - hidden).takeIf { it.isNotEmpty() }?.let { "; ${it.joinToString { e -> e.take(24) }} " +
                (if (it.all { e -> e in landed }) "landed" else "no longer shown") }.orEmpty())
        // The first board drawn (the table opened, back from timeline mode or a take-back) is taken as it is
        if (instant || before == null) targeting?.takeIf { !it.done }?.skipped = true
        val asIs = instant || before == null
        takenAsIs = asIs
        val stack = CombatStackRules.stack(unhidden.board).cards
        val gone = (beforeUnhidden ?: before)?.let { CombatStackRules.stack(it.board).cards - stack.toSet() }.orEmpty()
        if (gone.isNotEmpty() && !asIs && before != null) {
            val key = gone.firstNotNullOfOrNull { StackAbilities.of(before, it, viewer) }
            val (repeats, _) = resolutionPace.begin(key, clock())
            val share = StackSchedule.resolutionShare(repeats)
            val now = org.asyncttwiz.appui.elapsedRealtime()
            resolving = Triple(gone, now, share)
            StackAnimationProbe.resolving?.invoke(viewer, gone)
            for (entry in gone) {
                val waited = waitingSince.remove(entry)
                // The spec's logging: what the game waited for between steps 4 and 5, and how long
                AppLog.info("game", "Stack entry ${entry.take(24)} leaves the Stack (step 5): " +
                    (waited?.let { (at, what) -> "${now - at} ms after its step 4 ended, the game waiting for $what" } ?: "no step 4 before it") +
                    (if (entry in hiddenBefore) " (its play shown never landed here: it leaves unseen)" else "") +
                    ", repeat $repeats, its effect's animations at ${(share * 100).roundToLong()}% of their time")
            }
        }
        waitingSince.keys.retainAll(stack.toSet())
        val gameTop = stack.firstOrNull()
        val top = gameTop?.takeIf { it !in hidden }
        if (gameTop != top && gameTop != lastTop) AppLog.info("game", "Stack entry ${gameTop?.take(24)} is the top entry, kept off the " +
            "Stack until its play shown lands: no entry's arrows meanwhile")
        if (top == lastTop) return
        lastTop = top
        targeting?.takeIf { it.entry != top && !it.done }?.let {
            it.skipped = true
            AppLog.info("game", "Stack entry ${it.entry.take(24)} step 4 ended: no longer the top entry")
        }
        arrowed = null
        pending = null
        if (top == null || stackTargetCount(play, top) == 0) return
        if (asIs) { arrowed = top; return }
        pending = top
    }

    /** Whether the table's settle on the board drawn waits for step 4 ([settled]): one is pending or playing. */
    val holdsSettle: Boolean get() = busy

    /** The table settled on the board it draws: a step 5 begun on it is over (logged with its length). */
    fun resolved() {
        val (entries, at, share) = resolving ?: return
        resolving = null
        resolutionPace.landed(clock())
        AppLog.info("game", "Stack entries ${entries.joinToString { it.take(24) }} left the Stack (step 5): their effect's " +
            "animations took ${org.asyncttwiz.appui.elapsedRealtime() - at} ms (at ${(share * 100).roundToLong()}% of their time)")
    }

    /**
     * The table settled on [play], the board it draws (its moves done): step 4 of the pending top entry plays now at Card
     * move speed [multiplier]; a finger held or Alt ([pause]) pauses it. Returns when it is done (at once when none is
     * pending; once the one playing is done when another settle comes meanwhile). [why] says what the game waits for
     * afterwards, for step 5's log.
     */
    suspend fun settled(play: TableBoard, multiplier: Float, pause: ViewPause?, why: () -> String) {
        resolved()
        targeting?.takeIf { !it.done }?.let { running ->
            // Another settle while step 4 plays: it waits for that step (the board's steps go on only after it)
            snapshotFlow { running.done }.first { it }
            return
        }
        val entry = pending ?: return
        pending = null
        if (CombatStackRules.stack(play.board).cards.firstOrNull() != entry) return
        val targets = stackTargetCount(play, entry)
        val key = StackAbilities.of(play, entry, viewer)
        // Taken before the first suspension: a second settle meanwhile waits for this step instead of starting its own
        val step = Targeting(entry, targets, multiplier)
        targeting = step
        val start = withFrameMillis { it }
        val (repeats, gap) = targetPace.begin(key, start)
        step.repeats = repeats
        StackAnimationProbe.targeting?.invoke(viewer, entry, targets, step.total)
        var last = start
        var paused = 0L
        // Done however it ends (the table closing cancels it): nothing waits on it then
        try {
            while (step.elapsed < step.total && !step.skipped) {
                val now = withFrameMillis { it }
                if (pause?.paused == true) paused += now - last else step.elapsed += now - last
                last = now
            }
        } finally { step.done = true }
        targetPace.landed(last)
        if (lastTop == entry) arrowed = entry
        val waitFor = why()
        waitingSince[entry] = org.asyncttwiz.appui.elapsedRealtime() to waitFor
        // The spec's logging: step 4's length, then what the game waits for before step 5
        AppLog.info("game", "Stack entry ${entry.take(24)} step 4 (targeting): $targets arrows, " +
            (if (gap == null) "first of its chain" else "$gap ms after the one before") + ", repeat $repeats, " +
            "${last - start} ms of ${step.total} ms" + (if (paused > 0) " (paused $paused ms by a finger or Alt)" else "") +
            (if (step.skipped) ", ended early" else "") + "; the game now waits for $waitFor")
    }

    /**
     * The table's settle on [play] (its moves done; [TableRuntimeHooks.onSceneSettled]): step 4 of a pending top entry
     * plays first (in [scope], at Card move speed [multiplier], paused by [pause]), then [next] (the board's next step) runs,
     * unless the board's steps moved on meanwhile ([key]).
     */
    fun settleHook(scope: kotlinx.coroutines.CoroutineScope, play: TableBoard, multiplier: Float, pause: ViewPause?,
        why: () -> String, key: () -> Any?, next: () -> Unit): (String) -> Unit = { node ->
        settledNode = node
        if (holdsSettle) {
            val at = key()
            scope.launch {
                settled(play, multiplier, pause, why)
                if (key() == at) next()
            }
        } else {
            resolved()
            next()
        }
    }

    /** A double tap or double click skips step 4 (WEB-030): its arrows show whole at once. True when one was playing. */
    fun skip(): Boolean {
        val running = targeting?.takeIf { !it.done } ?: return false
        running.skipped = true
        AppLog.info("game", "Stack entry ${running.entry.take(24)} step 4 skipped by a double tap")
        return true
    }

    /** A take-back or timeline mode: what plays ends at once, leaving nothing on screen; the next board drawn is taken as
     * it is ([drawn] with instant). */
    fun end(why: String) {
        val running = targeting?.takeIf { !it.done }
        if (running != null) running.skipped = true
        if (running != null || pending != null) AppLog.info("game", "Stack animation ended at once ($why): " +
            (running?.let { "step 4 of ${it.entry.take(24)}" } ?: "step 4 of ${pending?.take(24)} before it began"))
        pending = null
        resolving = null
        // The next board drawn is taken as it is
        previous = null
        previousUnhidden = null
    }

    /** HIS-112: the play shown [show] ended on this table (landed, or skipped by a double tap): its entry shows from now on. */
    fun land(show: GameDevice.PlayShow) {
        if (show !in ended) ended += show
        val entry = "entry:${show.entry}"
        StackAnimationProbe.landed?.invoke(viewer, entry)
        if (entry !in landed) {
            landed += entry
            if (landed.size > LANDED_KEPT) landed.removeAt(0)
        }
    }

    /** The entries of the plays shown in [moments] (the device's open ones) not ended on this table yet, and of the plays
     * an own action's early board says its event will show ([coming], EngineGame.Shown.coming): off the board until they
     * land. */
    fun hidden(moments: List<GameDevice.ViewMoment>, coming: List<GameDevice.PlayShow> = emptyList()): Set<String> =
        (moments.filterIsInstance<GameDevice.PlayShow>().filter { it !in ended } + coming.filter { it !in ended })
            .mapTo(LinkedHashSet()) { "entry:${it.entry}" }

    private var boardMemo: Triple<TableBoard, Set<String>, TableBoard>? = null

    /**
     * HIS-112 (owner playtest 2026-11-14: a play flew to the middle and at the same time straight to the Stack): [unhidden]
     * as the table draws it while the plays shown in [moments] haven't landed: without their entries ([withoutEntries]); the
     * same board object while nothing changes.
     */
    fun board(unhidden: TableBoard, hidden: Set<String>): TableBoard {
        boardMemo?.takeIf { it.first === unhidden && it.second == hidden }?.let { return it.third }
        return unhidden.withoutEntries(hidden).also { boardMemo = Triple(unhidden, hidden, it) }
    }

    /** Forgets the ended plays the device no longer has open. */
    fun prune(moments: List<GameDevice.ViewMoment>) {
        if (ended.any { it !in moments }) ended.retainAll(moments.toSet())
    }
}
