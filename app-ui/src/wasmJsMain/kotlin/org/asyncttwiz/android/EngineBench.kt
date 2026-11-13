@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.PrintedNumber
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.state.ObjectId
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.automatum.boundary.BoardMirror
import org.asyncttwiz.automatum.boundary.BoardStatesIn
import org.asyncttwiz.automatum.boundary.BoardStatesOut
import org.asyncttwiz.automatum.boundary.BoardTransfer
import org.asyncttwiz.automatum.boundary.EngineMessages
import org.asyncttwiz.automatum.boundary.ListDelta
import org.asyncttwiz.automatum.boundary.MirrorShadow
import org.asyncttwiz.automatum.boundary.StateOrigin
import org.asyncttwiz.automatum.boundary.StateRef
import org.asyncttwiz.automatum.boundary.TableBoardWire
import org.asyncttwiz.automatum.boundary.WireDefinitionsIn
import org.asyncttwiz.automatum.boundary.WireDefinitionsOut
import org.asyncttwiz.automatum.boundary.WireEventsIn
import org.asyncttwiz.automatum.boundary.WireEventsOut
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.InitialStarter
import org.asyncttwiz.protocol.PlayContainer
import org.asyncttwiz.protocol.PlayPlayer
import org.asyncttwiz.protocol.PlayRoot
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.StarterChoice
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.EngineView
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.automatum.boundary.EngineAnswer
import org.asyncttwiz.automatum.boundary.EngineRequest
import org.asyncttwiz.automatum.boundary.MessageEngineChannel
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameDefinitions
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.TableState

/**
 * Dev only (a test build opened at `#engine-bench`, in place of the app): measures the engine boundary's round trips to
 * the Web Worker ([startEngineWorker]) on a game made here (two 60-card decks of lands and vanilla creatures, both hands
 * kept, Alice's first main phase): the first request with the board's snapshot, then hand plays and card plays on the
 * same board, then the next board sent as its event (Alice plays a land), while the page's frames are timed. Then the
 * device's own work as the page still does it (web client step 4c: until the device moves into the worker): a pass and
 * End turn worked out on the page's thread, with the worst frame meanwhile. Then the device itself in the worker (web client
 * step 4c part 3, [DeviceHost]): the game played on a few turns, opened there, a pass and the table's flows asked of it (step 4d:
 * a land, a cast, an attack, combat damage, End turn, Undo), each timed to its result and to its last board taken on the page,
 * with the worst frame meanwhile. The results go on the page, to the console and, when done,
 * into the title ("engine-bench: done").
 */
fun runEngineBench(base: String) {
    val out = StringBuilder()
    fun line(text: String) { out.append(text).append('\n'); jsShowBench(out.toString()); println("engine-bench: $text") }
    val frames = Frames().also { it.start() }
    val workers = startEngineWorkers(base)
    val channel = workers.planning
    val bench = try { BenchGame() } catch (problem: Exception) {
        line("The bench game couldn't be made: ${problem.stackTraceToString()}"); jsBenchTitle("engine-bench: failed"); return
    }
    line("Board: ${bench.objects} objects; snapshot ${bench.snapshotChars} chars (made in ${bench.encodeMs.f()} ms on the page); " +
        "delta ${bench.deltaChars} chars")
    val game = "bench"
    val runs = 20

    fun timed(request: EngineRequest, transfer: BoardTransfer, done: (ms: Double, answer: EngineAnswer) -> Unit) {
        val at = jsNow()
        channel.ask(request, null, transfer) { answer -> done(jsNow() - at, answer) }
    }
    fun series(kind: String, board: Long, transfer: BoardTransfer, n: Int, make: (Long) -> EngineRequest, then: () -> Unit) {
        val totals = mutableListOf<Double>(); val works = mutableListOf<Double>()
        fun next(left: Int) {
            if (left == 0) {
                line("$kind on the same board, $n round trips: median ${totals.median().f()} ms (worker ${works.median().f()} ms), " +
                    "max ${totals.max().f()} ms")
                then(); return
            }
            timed(make(channel.next()), transfer) { ms, answer -> totals += ms; works += answer.micros / 1000.0; next(left - 1) }
        }
        next(n)
    }

    frames.reset()
    timed(EngineRequest.HandPlaysOf(channel.next(), game, 1, bench.alice), bench.first) { ms, answer ->
        line("First hand plays with the snapshot: ${ms.f()} ms round trip (plays worked out in ${(answer.micros / 1000.0).f()} ms; " +
            "${(answer as? EngineAnswer.HandPlaysAnswer)?.plays?.byCard?.size ?: "no"} cards with plays, ${answer::class.simpleName})")
        series("Hand plays", 1, bench.first, runs, { EngineRequest.HandPlaysOf(it, game, 1, bench.alice) }) {
            series("Card plays", 1, bench.first, runs, { EngineRequest.CardPlaysOf(it, game, 1, bench.alice) }) {
                timed(EngineRequest.HandPlaysOf(channel.next(), game, 2, bench.alice), bench.second) { ms, after ->
                    line("Next board as its event, then hand plays: ${ms.f()} ms round trip (${after::class.simpleName}, " +
                        "${bench.second.snapshots} snapshots of it made)")
                    line("Page frames meanwhile: ${frames.count} frames, worst ${frames.worst.f()} ms, mean ${frames.mean().f()} ms")
                    line(channel.requests.let { "Answers awaited at the end: ${it.awaited}" })
                    val boundary = try { BoundaryBench(bench) } catch (problem: Exception) {
                        line("The boundary bench couldn't start: ${problem.stackTraceToString()}"); null
                    }
                    boundary?.let { line(it.board("The board now, first sent whole", emptyList())) }
                    onPage(frames, ::line, "A pass", bench::pass) {
                        boundary?.let { line(it.board("The board after the pass", emptyList())) }
                        val before = bench.line.size
                        onPage(frames, ::line, "End turn", bench::endTurn) {
                            boundary?.let { line(it.board("The board after End turn, with its motion steps", bench.line.drop(before))) }
                            val device = boundary?.let { b -> try { bench.develop(6); DeviceBench(workers.devices, bench, b.base, frames, ::line) } catch (problem: Exception) {
                                line("The device bench couldn't start: ${problem.stackTraceToString()}"); null } }
                            val finish = { frames.stop(); jsBenchTitle("engine-bench: done") }
                            if (device == null) finish() else device.run(finish)
                        }
                    }
                }
            }
        }
    }
}

/** [work] (the events it made) on the page's thread, as the page does the device's own work today, timed with the worst
 * frame around it; then [then]. */
private fun onPage(frames: Frames, line: (String) -> Unit, kind: String, work: () -> Int, then: () -> Unit) {
    frames.reset()
    jsLater(100) {
        val at = jsNow()
        val events = work()
        val ms = jsNow() - at
        jsLater(100) {
            line("$kind worked out on the page: ${ms.f()} ms ($events events); worst frame meanwhile ${frames.worst.f()} ms")
            then()
        }
    }
}

/**
 * Web client step 4c: what a published board costs across the engine boundary once the device runs in the worker, timed
 * on the page's thread in parts: the engine side building the board (its projection, computed values and state copy, as
 * the device does today) and describing it for the page (its state as moves of the page's copy, [MirrorShadow], and the
 * message); then the page taking it ([ShownDecoder]'s work: the message read, its copy moved, the board's state copied and
 * its values made again). Only the page's part holds the page's frames; the rest runs in the worker.
 */
private class BoundaryBench(private val bench: BenchGame) {
    private val viewer = bench.alice
    private val json = EngineMessages.json
    private val pageCodec = EngineJson(EngineJson.decodeDefinitions(bench.codec.encodeDefinitions()))
    private val shadow = MirrorShadow(bench.codec)
    private val mirror = BoardMirror(pageCodec)
    private val eventsOut = WireEventsOut(bench.codec)
    private val eventsIn = WireEventsIn(pageCodec)
    private val statesOut = BoardStatesOut()
    private val statesIn = BoardStatesIn()
    private val definitionsOut = WireDefinitionsOut()
    private val definitionsIn = WireDefinitionsIn()
    private val definitionsMap = MapSerializer(String.serializer(), SubmittedDefinition.serializer())
    private var visibleOut: List<ObjectId> = emptyList()
    private var visibleIn: List<ObjectId> = emptyList()
    val base = run {
        val containers = listOf(bench.alice, "bob").flatMap { p ->
            PlayZone.entries.filter { it != PlayZone.STACK }.map { zone -> PlayContainer("$p/$zone", zone, p) }
        } + PlayContainer("stack", PlayZone.STACK)
        PlayState("bench", "play", "round", "start", PlayRoot("root", "branch"),
            BoardState(listOf(PlayPlayer(bench.alice), PlayPlayer("bob")), emptyList(), containers, org.asyncttwiz.protocol.TurnState(bench.alice)),
            emptyList(), emptyList(), InitialStarter(StarterChoice(bench.alice), bench.alice))
    }
    private var number = 0L

    /** One board as the engine side builds it: its projection, computed values and its own copy of the state. */
    private class Built(val board: BoardState, val definitions: List<SubmittedDefinition>, val state: GameState, val table: TableState,
        val visible: Set<ObjectId>, val computed: ComputedBoard)

    private fun build(model: GameModel): Built {
        val projected = EngineProjection.project(base, model, viewer, "node")
        val visible = model.visible(PlayerId(viewer))
        val view = EngineView(model.engine, model.table, PlayerId(viewer), visible)
        val computed = ComputedBoard.of(projected.board, projected.definitions, AutomatumBoards.definitions(projected.definitions), viewer,
            view.state, model.view())
        return Built(projected.board, projected.definitions, view.state, model.table, visible, computed)
    }

    private fun wire(built: Built, state: StateRef): JsonElement {
        val seen = built.visible.toList()
        val delta = ListDelta.of(visibleOut, seen).also { visibleOut = seen }
        return json.encodeToJsonElement(TableBoardWire.serializer(), TableBoardWire(++number, TableBoardWire.FRESH, "bench", "play", "node",
            statesOut.wire(built.board), definitionsOut.ref(built.definitions), state, viewer, delta, built.computed.wire()))
    }

    /** The bench game's board now and the boards after each of [steps]' events (a board's motion, at most eight), sent and
     * taken: the times of each part. */
    fun board(what: String, steps: List<GameEvent>): String {
        val model = bench.model
        val at0 = jsNow()
        // The engine side: the board and its steps built as the device builds them
        val main = build(model)
        val forward = steps.takeLast(8)
        val back = forward.asReversed()
        val stepBuilt = forward.indices.map { i ->
            val (state, table) = BoardMirror.walk(model, back, forward, i, forward[i].engine?.changes?.size ?: 0)
            build(GameModel(state, table))
        }
        val at1 = jsNow()
        // ... described for the page and written
        val origin = StateOrigin.Line("root", bench.line.toList())
        val boards = ArrayList<JsonElement>()
        boards += wire(main, shadow.line(origin, main.state, main.table))
        stepBuilt.forEachIndexed { i, b ->
            boards += wire(b, shadow.steps(StateOrigin.Steps(origin, back, forward, i, forward[i].engine?.changes?.size ?: 0), b.state, b.table))
        }
        for (e in forward) eventsOut.ref(e)
        for (e in bench.line) eventsOut.ref(e)
        val text = JsonObject(mapOf("events" to JsonObject(eventsOut.take()),
            "definitions" to json.encodeToJsonElement(definitionsMap, definitionsOut.take()), "boards" to JsonArray(boards))).toString()
        val at2 = jsNow()
        // The page: the message read, its copy moved and each board made again with its own state
        val message = json.parseToJsonElement(text) as JsonObject
        eventsIn.add(message.getValue("events") as JsonObject)
        definitionsIn.add(json.decodeFromJsonElement(definitionsMap, message.getValue("definitions")))
        var same = true
        (message.getValue("boards") as JsonArray).forEachIndexed { i, element ->
            val w = json.decodeFromJsonElement(TableBoardWire.serializer(), element)
            val board = statesIn.board(checkNotNull(w.board))
            val defs = definitionsIn.of(w.definitions)
            val ref = checkNotNull(w.state)
            mirror.move(ref, eventsIn::get)
            val (state, table) = when (ref) {
                is StateRef.Line, is StateRef.Load -> checkNotNull(mirror.model).let { it.engine to it.table }
                else -> mirror.state(ref, eventsIn::get)
            }
            val seen = checkNotNull(w.visible).applied(visibleIn).also { visibleIn = it }.toSet()
            val view = EngineView(state, table, PlayerId(viewer), seen)
            ComputedBoard.restored(checkNotNull(w.computed), view.state)
            val expected = if (i == 0) main else stepBuilt[i - 1]
            if (board != expected.board || defs != expected.definitions || view.state != expected.state) same = false
        }
        val at3 = jsNow()
        return "$what: built ${(at1 - at0).f()} ms (${1 + stepBuilt.size} boards), described and written ${(at2 - at1).f()} ms, " +
            "${text.length} chars; the page took it in ${(at3 - at2).f()} ms${if (same) "" else " (DIFFERENT from the engine side's)"}; " +
            "${shadow.loads} snapshots so far"
    }
}

private fun List<Double>.median(): Double = sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
private fun Double.f(): String = ((this * 10).toLong() / 10.0).toString()

/** The page's frame times while the bench runs (requestAnimationFrame). */
private class Frames {
    private var last = 0.0
    private var running = false
    var worst = 0.0; private set
    var count = 0; private set
    private var sum = 0.0
    fun start() { running = true; frame() }
    fun stop() { running = false }
    fun reset() { worst = 0.0; count = 0; sum = 0.0; last = 0.0 }
    fun mean() = if (count == 0) 0.0 else sum / count
    private fun frame() {
        jsFrame {
            val now = jsNow()
            if (last > 0) { val gap = now - last; count++; sum += gap; if (gap > worst) worst = gap }
            last = now
            if (running) frame()
        }
    }
}

/** The bench's game: its board after both keeps ([first]) and after Alice plays a land ([second], also as its event). */
/**
 * Web client step 4c part 3: the device itself in the worker ([DeviceHost], through the page's [DeviceChannel]): the bench
 * game opened there for the player whose turn it is (the other player's passes assumed, HIS-096), then a pass and End turn
 * asked of it, each timed to its result on the page and to its last board taken on the page ([EngineGame.Shown] decoded with
 * the page's own copy of the state), with the page's worst frame meanwhile.
 */
private class DeviceBench(devices: DeviceChannel, bench: BenchGame, base: PlayState, private val frames: Frames,
    private val line: (String) -> Unit) {
    private var lastShownAt = 0.0
    private var boards = 0
    private val openedAt = jsNow()
    private val game: EngineGame

    init {
        val me = bench.model.engine.turn.active.value
        val head = bench.line.last().id
        val snapshot = org.asyncttwiz.protocol.EngineSnapshot(bench.codec.encodeDefinitions(), "root", head, bench.codec.encodeModel(bench.model),
            localHeads = mapOf(bench.alice to head, "bob" to head),
            settings = org.asyncttwiz.protocol.game.PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always))
        frames.reset()
        game = EngineGame("bench-device", me, base, snapshot, onShown = { lastShownAt = jsNow(); boards++ }, device = devices)
    }

    fun run(then: () -> Unit) {
        // The first board: the game opened in the worker and its state sent whole
        waitFor({ boards > 0 || game.loadFailed }) {
            if (game.loadFailed) { line("Device in the worker: the game failed to load there"); then(); return@waitFor }
            line("Device in the worker: opened, first board on the page after ${(lastShownAt - openedAt).f()} ms; worst frame ${frames.worst.f()} ms")
            flows(then)
        }
    }

    // ---- Web client step 4d: the table's flows with the device in the worker

    private var flowSteps = 0
    private var landTurn = -1
    private var castFailedTurn = -1
    private var attackedTurn = -1
    private var attacked = false
    private var endedAfterAttack = false
    private val worst = ArrayList<Double>()

    /**
     * Web client step 4d: the table's flows as the player makes them, each asked of the device in the worker and timed: a land,
     * a creature cast with its payment (worked out on the page's copy of the board, as the cast panel does, its time on the
     * page told), turns ended until it can attack, its attacker staged (the table's own staging, worked out where the device
     * is) and declared, the other player's blockers assumed (none: they have no device here), the combat damage, End turn,
     * then Undo. The other player's passes are assumed (HIS-096).
     */
    private fun flows(then: () -> Unit) {
        val shown = game.shown
        if (shown == null || ++flowSteps > 80) { line("Flows: stopped after $flowSteps steps at ${shown?.engine?.turn}"); summary(then); return }
        val s = shown.engine
        val me = PlayerId(game.me)
        val next = { flows(then) }
        // Undo once the attack's turn has ended (the device may be waiting for the other player by then)
        if (endedAfterAttack && s.turn.active != me) {
            return timed("Undo", { done -> game.undo(null) { done(it::class.simpleName.orEmpty()) } }) { summary(then) }
        }
        when (val status = shown.status) {
            is GameDevice.Status.Waiting -> if (status.offer != null)
                return timed(if (status.offer == GameDevice.Offer.AssumeBlockers) "Assume no blockers (then the combat damage)" else "Assume resolves",
                    { done -> game.assume(emptyList()) { done(it::class.simpleName.orEmpty()) } }, next)
                else return jsLater(200, next)
            is GameDevice.Status.Stopped -> return timed("Decision stop", { done -> game.act(status.assumed) { done(it::class.simpleName.orEmpty()) } }, next)
            GameDevice.Status.YourMove -> Unit
            else -> return jsLater(200, next)
        }
        val options = Engine.options(s, me, org.asyncttwiz.protocol.game.EnginePreferences.of(s))
        // A choice of the player's (the cleanup's discard): its first answer
        s.pending?.decision?.takeIf { it.player == me }?.let { d ->
            options.filterIsInstance<Input.Answer>().firstOrNull { it.decision == d.id }?.let { answer ->
                return timed("Answer ${d::class.simpleName}", { done -> game.act(answer) { done(it::class.simpleName.orEmpty()) } }, next)
            }
        }
        val main = s.turn.active == me && s.stack.isEmpty() && s.turn.step in setOf(arcanum.api.TurnStep.PrecombatMain, arcanum.api.TurnStep.PostcombatMain)
        options.filterIsInstance<Input.PlayLand>().firstOrNull()?.takeIf { main && landTurn != s.turn.number }?.let { land ->
            landTurn = s.turn.number
            return timed("Land", { done -> game.act(land) { done(it::class.simpleName.orEmpty()) } }, next)
        }
        if (main && castFailedTurn != s.turn.number) options.filterIsInstance<Input.Cast>().firstOrNull()?.let { cast ->
            val turn = s.turn.number
            return timed("Cast through its flow session (${if (game.inWorker) "in the worker" else "on the page"})",
                { done -> castByPanel(cast) { result -> if ("sent after" !in result || result.startsWith("Refused")) castFailedTurn = turn; done(result) } }, next)
        }
        options.filterIsInstance<Input.DeclareAttackers>().firstOrNull()?.let {
            val ready = s.battlefield.filter { id -> s.obj(id)?.controller == me && Engine.view(s)[id]?.let { v -> v.power != null && !v.summoningSick } == true &&
                s.obj(id)?.tapped == false }
            if (ready.isEmpty() || attackedTurn == s.turn.number)
                return timed("No attackers", { done -> game.send(listOf(Input.DeclareAttackers(me, emptyList()))) { done(it::class.simpleName.orEmpty()) } }, next)
            attackedTurn = s.turn.number
            val card = (ready.first() as ObjectId.Card).card.value
            val draft = EngineDraft(game.play(), game.me)
            draft.add(org.asyncttwiz.protocol.PlayAction.Combat(org.asyncttwiz.protocol.CombatAction.Open))
            val area = draft.state.board.combat
            if (area != null) draft.add(org.asyncttwiz.protocol.PlayAction.Combat(org.asyncttwiz.protocol.CombatAction.Place(card,
                org.asyncttwiz.protocol.CombatDestination(area.id, org.asyncttwiz.protocol.CombatRole.ATTACKER,
                    pairGap = org.asyncttwiz.protocol.IdentityGap(area.pairs.lastOrNull()?.id)))))
            val target = arcanum.engine.state.TargetRef.Player(PlayerId(if (game.me == "alice") "bob" else "alice"))
            return timed("Stage an attacker", { done -> game.stageOwn(game.play(), draft.actions, draft.requestId) { done(it.toString()) } }) {
                attacked = true
                // Web client step 4e: what the table shows of combat on the staged board, asked of the worker's device, then
                // the step arrow's declaration worked out in the worker's shared-flow session and sent
                timed("Combat of the staged board (asked of the worker)", { done ->
                    AutomatumBoards.askCombat(game.play()) { shown -> done("${shown.choices.attacks.size} may attack, preview ${shown.preview != null}") }
                }) {
                    timed("Declare attackers through the shared-flow session", { done -> sharedStep(done) { shared.step() } }, next)
                }
            }
        }
        // Web client step 4e: a spell of hers on the Stack while she holds priority: the shared flow's panel asked of the worker,
        // then "Let it resolve" worked out in its session and sent
        if (s.stack.isNotEmpty() && s.priority.holder == me && s.pending == null && sharedTurn != s.turn.number) {
            sharedTurn = s.turn.number
            return timed("Shared flow's panel (asked of the worker)", { done ->
                val play = game.play()
                shared.load(play)
                waitFor({ shared.view?.board === play.board }) { done("priority ${shared.view?.priority}, decision ${shared.view?.decision?.kind}") }
            }) { timed("Let it resolve through the shared-flow session", { done -> sharedStep(done) { shared.pass(game.me) } }, next) }
        }
        if (s.turn.active == me && attacked && s.turn.step == arcanum.api.TurnStep.PostcombatMain) endedAfterAttack = true
        // Before combat with a creature that can attack: on to combat (a pass), not End turn
        val canAttack = s.battlefield.any { id -> s.obj(id)?.controller == me && s.obj(id)?.tapped == false &&
            Engine.view(s)[id]?.let { v -> v.power != null && !v.summoningSick } == true }
        if (s.turn.active == me && s.turn.step == arcanum.api.TurnStep.PrecombatMain && canAttack && attackedTurn != s.turn.number && s.priority.holder == me)
            return timed("Pass to combat", { done -> game.pass { done(it::class.simpleName.orEmpty()) } }, next)
        if (s.turn.active == me || s.priority.holder != me) return timed("End turn", { done -> game.endTurn { done("") } }, next)
        timed("Pass", { done -> game.pass { done(it::class.simpleName.orEmpty()) } }, next)
    }

    /**
     * Web client step 4e: [cast] made as the table's cast panel makes it ([CastController], its flow session where the device
     * is): begun, each step the panel would need taken (the first legal target, Cast at the payment), sent with its payment.
     * Each session step's round trip and the time to the send are told; [done] gets the send's result first.
     */
    private fun castByPanel(cast: Input.Cast, done: (String) -> Unit) {
        val cardId = (cast.card as ObjectId.Card).card.value
        val at = jsNow()
        val rounds = ArrayList<Double>()
        var stepAt = at
        var sentAfter = -1.0
        var finished = false
        fun finish(result: String) { if (!finished) { finished = true; done(result) } }
        fun steps() = rounds.joinToString { it.f() }
        val panel = CastController(game.me, { game.play() }, { message -> if (message != null) line("Cast panel: $message") },
            engineSend = { inputs ->
                rounds += jsNow() - stepAt
                sentAfter = jsNow() - at
                game.send(inputs) { acted -> finish("${acted::class.simpleName}: session steps ${steps()} ms, sent after ${sentAfter.f()} ms") }
                null
            },
            port = TableCastPort(LocalCastPort(game.me) { w, d -> d(w()) }) { game })
        fun drive() {
            if (finished || sentAfter >= 0) return
            if (panel.busy) { jsLater(1) { drive() }; return }
            rounds += jsNow() - stepAt
            stepAt = jsNow()
            if (!panel.open) { finish("dropped: session steps ${steps()} ms"); return }
            if (rounds.size > 8) { panel.cancel("bench"); finish("too many steps"); return }
            val pick = panel.pick
            val taken = if (pick != null) pick.options.firstOrNull()?.let { panel.answer(pick.answer(listOf(it))) } ?: false else panel.cast()
            if (!taken && !panel.busy && panel.open) { val why = panel.notice; panel.cancel("bench"); finish("refused: $why"); return }
            jsLater(1) { drive() }
        }
        if (!panel.begin(cardId, cast)) { finish("not begun"); return }
        jsLater(1) { drive() }
    }

    private var sharedTurn = -1
    /** Web client step 4e: what waits for the shared flow's step's send, and when the step was asked. */
    private var sharedSent: ((String) -> Unit)? = null
    private var sharedAt = 0.0

    /** The table's shared flows ([SharedFlowController]), their sessions in the worker beside the device. */
    private val shared by lazy {
        SharedFlowController(game.me, { game.play() }, { message -> if (message != null) line("Shared flow: $message") },
            engineSend = { inputs ->
                val worked = jsNow() - sharedAt
                val done = sharedSent
                sharedSent = null
                game.send(inputs) { acted -> done?.invoke("${acted::class.simpleName}: worked out in the session in ${worked.f()} ms") }
                null
            },
            port = TableSharedPort(LocalSharedPort(game.me) { w, d -> d(w()) }) { game })
    }

    /** A shared flow's step ([step]) whose send's result goes to [done]; one refused or with nothing to send says so. */
    private fun sharedStep(done: (String) -> Unit, step: () -> Boolean) {
        sharedSent = done
        sharedAt = jsNow()
        if (!step()) { sharedSent = null; done("not taken") }
        else waitFor({ !shared.busy }) { if (sharedSent != null) { sharedSent = null; done("nothing sent") } }
    }

    private fun summary(then: () -> Unit) {
        line("Flows: worst page frame over all of them ${worst.maxOrNull()?.f() ?: "-"} ms")
        then()
    }

    /** [ask] timed: its result's round trip, its last board on the page (the worker's boards after the result too), the
     * boards taken and the worst frame meanwhile; then [then]. */
    private fun timed(kind: String, ask: (done: (String) -> Unit) -> Unit, then: () -> Unit) {
        frames.reset()
        jsLater(100) {
            val before = boards
            val at = jsNow()
            ask { result ->
                val answered = jsNow() - at
                // The worker's boards after the result (the device's own steps) come within a moment
                jsLater(400) {
                    worst += frames.worst
                    line("Device in the worker: $kind: result ${result.ifEmpty { "back" }} after ${answered.f()} ms; last board on the page " +
                        "after ${(lastShownAt - at).f()} ms (${boards - before} boards); worst frame meanwhile ${frames.worst.f()} ms")
                    then()
                }
            }
        }
    }

    private fun waitFor(ready: () -> Boolean, then: () -> Unit) { if (ready()) then() else jsLater(20) { waitFor(ready, then) } }
}

private class BenchGame {
    val alice = "alice"
    private val bob = "bob"
    val objects: Int
    val snapshotChars: Int
    val deltaChars: Int
    val encodeMs: Double
    val first: Transfer
    val second: Transfer
    val model: GameModel
    val codec: EngineJson
    /** Every event made, from the game's root on. */
    val line = ArrayList<GameEvent>()
    private var made = 0

    /** The player holding priority passes it (an event as the device makes it). */
    private fun passOnce(): Boolean {
        val holder = model.engine.priority.holder ?: return false
        line += EventBuilder(model, "bench-${made++}", line.lastOrNull()?.id ?: "root", holder.value)
            .run { perform(Input.PassPriority(holder)); answerRandoms(); build() }
        return true
    }

    /** One pass: the events made. */
    fun pass(): Int = if (passOnce()) 1 else 0

    /**
     * Web client step 4d: plays [turns] more turns as both players would (a land each turn, a creature cast with the engine's
     * payment when it can be paid, no attacks or blocks, the first answer to any choice), then on to the next main phase of
     * the player whose turn it is, so the device bench finds lands, a creature to cast and creatures that can attack.
     */
    /** Whether [cast] can be made with the engine's payment on [s] (tried on a copy). */
    private fun paid(s: GameState, cast: Input.Cast): Boolean = try {
        val copy = s.deepCopy()
        val prefs = org.asyncttwiz.protocol.game.EnginePreferences.of(copy)
        Engine.perform(copy, cast, prefs).changes.forEach(copy::apply)
        (copy.pending?.decision as? arcanum.engine.decision.Decision.ManaPayment)?.let { pay ->
            Engine.perform(copy, Input.Answer(pay.id, Answer.ManaPayment(pay.suggestion)), prefs).changes.forEach(copy::apply)
        }
        copy.pending?.decision !is arcanum.engine.decision.Decision.ManaPayment
    } catch (problem: Exception) { false }

    fun develop(turns: Int) {
        val end = model.engine.turn.number + turns
        var guard = 0
        while (guard++ < 2_000) {
            val s = model.engine
            val main = s.stack.isEmpty() && s.turn.step in setOf(arcanum.api.TurnStep.PrecombatMain, arcanum.api.TurnStep.PostcombatMain)
            // Stops at the start of a precombat main phase, before its land
            if (s.turn.number >= end && main && s.turn.step == arcanum.api.TurnStep.PrecombatMain && s.priority.holder == s.turn.active &&
                (s.turn.landsPlayed[s.turn.active] ?: 0) == 0) return
            // At a declaration nobody holds priority: the player the engine offers it to
            val actor = s.pending?.decision?.player ?: s.priority.holder
                ?: listOf(alice, bob).map(::PlayerId).firstOrNull { Engine.options(s, it).isNotEmpty() } ?: return
            val options = Engine.options(s, actor)
            val active = s.turn.active == actor
            val input = s.pending?.decision?.let { d -> options.filterIsInstance<Input.Answer>().firstOrNull { it.decision == d.id } }
                ?: options.firstOrNull { it is Input.DeclareAttackers }?.let { Input.DeclareAttackers(actor, emptyList()) }
                ?: options.firstOrNull { it is Input.DeclareBlockers }?.let { Input.DeclareBlockers(actor, emptyList()) }
                ?: options.filterIsInstance<Input.PlayLand>().firstOrNull()?.takeIf { active && main }
                ?: options.filterIsInstance<Input.Cast>().firstOrNull { active && main && paid(s, it) }
                ?: Input.PassPriority(actor)
            line += EventBuilder(model, "develop-${made++}", line.lastOrNull()?.id ?: "root", actor.value).run {
                perform(input)
                // A cast's payment: the engine's suggestion
                (model.engine.pending?.decision as? arcanum.engine.decision.Decision.ManaPayment)?.let { pay ->
                    perform(Input.Answer(pay.id, Answer.ManaPayment(pay.suggestion)))
                }
                answerRandoms(); build()
            }
        }
    }

    /** Passes until the turn ends (End turn, both players' passes as the devices make them): the events made. */
    fun endTurn(): Int {
        val turn = model.engine.turn.number
        var events = 0
        while (model.engine.turn.number == turn && events < 60 && passOnce()) events++
        return events
    }

    class Transfer(private val snapshot: BoardTransfer.Snapshot, private val deltas: Map<Long, BoardTransfer.Delta> = emptyMap()) : BoardTransfer {
        var snapshots = 0
        override fun snapshot() = snapshot.also { snapshots++ }
        override fun since(from: Long) = deltas[from]
    }

    init {
        fun land(name: String) = CardDefinition(Layout.Normal, listOf(FaceDefinition(name, supertypes = listOf(Supertype.Basic),
            types = listOf(CardType.Land), subtypes = listOf(Subtype(name)))))
        fun creature(name: String, cost: String, kind: String, size: Int) = CardDefinition(Layout.Normal, listOf(FaceDefinition(name,
            ManaCost.parse(cost), types = listOf(CardType.Creature), subtypes = listOf(Subtype(kind)),
            power = PrintedNumber.Fixed(size), toughness = PrintedNumber.Fixed(size))))
        val deck = List(12) { land("Mountain") } + List(12) { land("Forest") } +
            List(18) { creature("Bench Bear", "{1}{G}", "Bear", 2) } + List(18) { creature("Bench Brute", "{2}{R}", "Ogre", 3) }
        val seats = listOf(alice, bob).map { p -> Seat(PlayerId(p), deck.mapIndexed { i, card -> DeckCard(CardId("$p-$i"), card) }) }
        val model = GameModel(Engine.newGame(seats), TableState(secret = LineRandom.secret(ByteArray(32) { 5 })))
        this.model = model
        fun event(id: String, parent: String, author: String, input: (GameState) -> Input) =
            EventBuilder(model, id, parent, author).run { perform(input(model.engine)); answerRandoms(); build() }.also { line += it }
        // As GameDevice.openingInput: the open decision when it is the player's, else a declaration ahead of it
        fun keep(p: String): (GameState) -> Input = { s ->
            val d = s.pending?.decision
            if (d != null && d.player == PlayerId(p)) Input.Answer(d.id, Answer.Option("Keep")) else Input.SetupAnswer(PlayerId(p), Answer.Option("Keep"))
        }
        event("start", "root", "server") {
            Input.StartGame(StartOptions(startingPlayer = PlayerId(alice), simultaneousMulligans = true, freeFirstMulligan = true))
        }
        event("keep-b", "start", bob, keep(bob))
        event("keep-a", "keep-b", alice, keep(alice))
        // On to Alice's first main phase, where she may play a land
        var parent = "keep-a"
        var passes = 0
        while (Engine.options(model.engine, PlayerId(alice)).none { it is Input.PlayLand }) {
            check(passes < 20) { "no land play after $passes passes, step ${model.engine.turn}" }
            val holder = requireNotNull(model.engine.priority.holder) { "nobody holds priority, step ${model.engine.turn}" }
            event("pass-$passes", parent, holder.value) { Input.PassPriority(holder) }
            parent = "pass-${passes++}"
        }
        val codec = EngineJson(GameDefinitions.of(model.engine))
        this.codec = codec
        val at = jsNow()
        val snapshot = BoardTransfer.Snapshot(codec.encodeDefinitions(), codec.encodeModel(model))
        encodeMs = jsNow() - at
        objects = model.engine.objects.size
        snapshotChars = snapshot.definitions.length + snapshot.model.length
        first = Transfer(snapshot)
        val land = event("land", parent, alice) { s ->
            Engine.options(s, PlayerId(alice)).first { it is Input.PlayLand }
        }
        val delta = BoardTransfer.Delta(listOf(codec.encodeEvent(land)))
        deltaChars = delta.apply.sumOf { it.length }
        second = Transfer(BoardTransfer.Snapshot(codec.encodeDefinitions(), codec.encodeModel(model)), mapOf(1L to delta))
    }
}

private fun jsNow(): Double = js("performance.now()")
private fun jsLater(ms: Int, action: () -> Unit): Unit = js("setTimeout(() => action(), ms)")
private fun jsFrame(action: () -> Unit): Unit = js("requestAnimationFrame(() => action())")
private fun jsBenchTitle(text: String): Unit = js("document.title = text")
private fun jsShowBench(text: String): Unit = js("""{
    let pre = document.getElementById('engine-bench');
    if (!pre) {
        const loading = document.getElementById('atw-loading');
        if (loading) loading.remove();
        pre = document.createElement('pre');
        pre.id = 'engine-bench';
        pre.style.cssText = 'margin: 16px; font: 13px monospace; white-space: pre-wrap;';
        document.body.appendChild(pre);
    }
    pre.textContent = text;
}""")
