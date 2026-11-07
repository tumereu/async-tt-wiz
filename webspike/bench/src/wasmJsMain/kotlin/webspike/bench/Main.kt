@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package webspike.bench

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import kotlinx.serialization.json.Json
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.game.GameDevice

// Web spike: one Kotlin/Wasm module, three roles. In Node it runs the benchmark and prints it. In a page it is the
// benchmark page (?mode=main: the engine on the page's main thread; ?mode=worker: the engine in a dedicated Web Worker
// started from this same module), with an animation driven by requestAnimationFrame and a frame-time readout. In a
// worker it answers the page's messages.

private fun isNode(): Boolean = js("typeof process !== 'undefined' && process.versions != null && process.versions.node != null")
private fun hasDocument(): Boolean = js("typeof document !== 'undefined'")
private fun now(): Double = js("performance.now()")
private fun query(): String = js("location.search")
private fun setText(id: String, text: String): Unit = js("document.getElementById(id).textContent = text")
private fun appendText(id: String, text: String): Unit = js("document.getElementById(id).textContent += text")
private fun onClick(id: String, f: () -> Unit): Unit = js("document.getElementById(id).onclick = () => f()")
private fun requestFrame(f: (Double) -> Unit): Unit = js("requestAnimationFrame((t) => f(t))")
private fun later(f: () -> Unit): Unit = js("setTimeout(() => f(), 30)")
private fun drawSpinner(angle: Double): Unit = js("""{
  const c = document.getElementById('spin'); const g = c.getContext('2d');
  g.clearRect(0, 0, c.width, c.height); g.save(); g.translate(c.width / 2, c.height / 2); g.rotate(angle);
  g.fillStyle = '#e8a33d'; g.fillRect(-50, -8, 100, 16); g.fillStyle = '#3d8be8'; g.fillRect(-8, -50, 16, 100); g.restore();
}""")
// Results also go to the page title (readable from /json/list without touching the page's main thread)
private fun publish(key: String, value: String): Unit = js("{ globalThis[key] = value; document.title = key + ': ' + value; }")
private fun autoClick(id: String, delayMs: Int): Unit = js("setTimeout(() => document.getElementById(id).click(), delayMs)")
private fun newWorker(url: String): JsAny = js("new Worker(url, { type: 'module' })")
private fun moduleUrl(): String = js("document.querySelector('script[data-module]').getAttribute('data-module')")
private fun post(worker: JsAny, message: String): Unit = js("worker.postMessage(message)")
private fun onMessage(worker: JsAny, f: (String) -> Unit): Unit = js("worker.onmessage = (e) => f(e.data)")
private fun selfOnMessage(f: (String) -> Unit): Unit = js("self.onmessage = (e) => f(e.data)")
private fun selfPost(message: String): Unit = js("self.postMessage(message)")

private val json = Json { encodeDefaults = false }

fun main() {
    when {
        isNode() -> {
            val result = Bench.run(runs = 15) { println(it) }
            println(result.report("wasm-node"))
            println("BENCHJSON wasm-node " + result.json())
        }
        hasDocument() -> page()
        else -> worker()
    }
}

// ---- The page

private class Frames {
    var last = 0.0
    var worst = 0.0
    var count = 0
    var sum = 0.0
    fun reset() { worst = 0.0; count = 0; sum = 0.0 }
}

private val frames = Frames()

private fun animate() {
    var angle = 0.0
    fun frame(t: Double) {
        if (frames.last > 0) {
            val dt = t - frames.last
            frames.worst = maxOf(frames.worst, dt); frames.count++; frames.sum += dt
            if (frames.count % 15 == 0) setText("frames", "frame ${Bench.fmt(dt)} ms, avg ${Bench.fmt(frames.sum / frames.count)} ms, worst ${Bench.fmt(frames.worst)} ms since last reset")
        }
        frames.last = t
        angle += 0.06
        drawSpinner(angle)
        requestFrame(::frame)
    }
    requestFrame(::frame)
}

private fun page() {
    val worker = query().contains("mode=worker")
    setText("mode", if (worker) "Engine in a dedicated Web Worker" else "Engine on the page's main thread")
    animate()
    if (worker) workerPage() else mainPage()
    // ?autorun: start without a click (the phone / CDP-free runs)
    if (query().contains("autorun")) autoClick("run", 2000)
}

private fun log(text: String) { appendText("log", text + "\n") }

private fun mainPage() {
    onClick("run") {
        log("running on the main thread (the animation freezes while it runs)…")
        later {
            frames.reset()
            val start = now()
            val result = Bench.run(runs = 15)
            val report = result.report("wasm-browser-main")
            log(report)
            log("total ${Bench.fmt(now() - start)} ms; worst frame during the run ${Bench.fmt(frames.worst)} ms")
            publish("benchResult", "wasm-browser-main " + result.json())
        }
    }
}

private fun workerPage() {
    val w = newWorker(moduleUrl())
    var started = 0.0
    var snapshotJson = ""
    var pending: ((String) -> Unit)? = null
    onMessage(w) { message -> pending?.invoke(message) }
    fun ask(message: String, then: (String) -> Unit) { pending = then; started = now(); post(w, message) }
    onClick("run") {
        frames.reset()
        log("benchmark in the worker (the animation should keep running)…")
        val t0 = now()
        ask("bench") { reply ->
            log(reply.substringAfter('|'))
            log("total ${Bench.fmt(now() - t0)} ms; worst frame meanwhile ${Bench.fmt(frames.worst)} ms, avg ${Bench.fmt(frames.sum / frames.count.coerceAtLeast(1))} ms")
            publish("benchResult", "wasm-browser-worker " + reply.substringBefore('|'))
            if (query().contains("autorun")) autoClick("roundtrip", 500)
        }
    }
    onClick("roundtrip") {
        frames.reset()
        log("round trips: page <-> worker (strings, JSON both ways)…")
        val lines = mutableListOf<String>()
        ask("snapshot") { snap ->
            snapshotJson = snap
            lines += "snapshot JSON from the worker: ${snap.length} chars, ${Bench.fmt(now() - started)} ms (incl. building the board the first time)"
            // Raw transfer of a snapshot-sized string, 10 times
            val echoes = mutableListOf<Double>()
            fun echo(n: Int) {
                if (n == 0) {
                    lines += "echo of the snapshot string (post + structured clone both ways): median ${Bench.fmt(echoes.sorted()[echoes.size / 2])} ms"
                    loads(10, mutableListOf(), mutableListOf())
                    return
                }
                ask("echo|$snapshotJson") { echoes += now() - started; echo(n - 1) }
            }
            echo(10)
        }
        // load: the page sends the snapshot JSON, the worker decodes it and loads the game
        fun acts(n: Int, totals: MutableList<Double>, works: MutableList<Double>, decodes: MutableList<Double>, sizes: MutableList<Int>) {
            if (n == 0) {
                lines += "pass round trip (page sends 'pass'; worker acts, projects the board, encodes it; page decodes BoardState): " +
                    "total median ${Bench.fmt(totals.sorted()[totals.size / 2])} ms, worker compute ${Bench.fmt(works.sorted()[works.size / 2])} ms, " +
                    "page decode ${Bench.fmt(decodes.sorted()[decodes.size / 2])} ms, board JSON ${sizes.first()} chars"
                lines.forEach(::log)
                log("worst frame meanwhile ${Bench.fmt(frames.worst)} ms")
                publish("roundtripResult", lines.joinToString("\n"))
                return
            }
            ask("load|$snapshotJson") {
                ask("pass") { reply ->
                    val total = now() - started
                    val work = reply.substringBefore('|').toDouble()
                    val d0 = now()
                    val board = json.decodeFromString(BoardState.serializer(), reply.substringAfter('|'))
                    val decode = now() - d0
                    check(board.cards.isNotEmpty())
                    totals += total + decode; works += work; decodes += decode; sizes += reply.length
                    acts(n - 1, totals, works, decodes, sizes)
                }
            }
        }
        fun loadsImpl(n: Int, totals: MutableList<Double>, works: MutableList<Double>) {
            if (n == 0) {
                lines += "load round trip (page sends snapshot JSON; worker decodes + loads the game): total median ${Bench.fmt(totals.sorted()[totals.size / 2])} ms, " +
                    "worker compute ${Bench.fmt(works.sorted()[works.size / 2])} ms"
                acts(10, mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
                return
            }
            ask("load|$snapshotJson") { reply -> totals += now() - started; works += reply.toDouble(); loadsImpl(n - 1, totals, works) }
        }
        loadsRef = ::loadsImpl
    }
}

private var loadsRef: ((Int, MutableList<Double>, MutableList<Double>) -> Unit)? = null
private fun loads(n: Int, totals: MutableList<Double>, works: MutableList<Double>) = loadsRef!!(n, totals, works)

// ---- The worker

private fun worker() {
    var device: GameDevice? = null
    selfOnMessage { message ->
        val command = message.substringBefore('|')
        val payload = message.substringAfter('|', "")
        when (command) {
            "bench" -> {
                val result = Bench.run(runs = 15)
                selfPost(result.json() + "|" + result.report("wasm-browser-worker"))
            }
            "snapshot" -> selfPost(json.encodeToString(EngineSnapshot.serializer(), Bench.board().snapshot))
            "echo" -> selfPost(payload)
            "load" -> {
                val t0 = now()
                val snapshot = json.decodeFromString(EngineSnapshot.serializer(), payload)
                device = Bench.board().device(snapshot)
                selfPost(Bench.fmt(now() - t0))
            }
            "pass" -> {
                val t0 = now()
                val d = device!!
                d.act(Input.PassPriority(PlayerId(Bench.board().a)))
                d.advance(2_000)
                val board = EngineProjection.project(Bench.board().base, d.model, Bench.board().a, d.session.tip).board
                val text = json.encodeToString(BoardState.serializer(), board)
                selfPost(Bench.fmt(now() - t0) + "|" + text)
            }
        }
    }
}
