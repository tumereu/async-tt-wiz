@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import org.asyncttwiz.automatum.boundary.MessageEngineChannel

/**
 * Starts the engine's Web Worker (docs/specs/web-client.md §1 item 3: webApp's engine module, served as engine.js in the
 * build's folder [base]) and gives the page's side of the engine boundary to it: requests go to the worker as JSON
 * strings, its answers come back on the page's thread, so drawing never waits for the engine.
 */
fun startEngineWorker(base: String): MessageEngineChannel {
    val url = base + "engine.js"
    val worker = jsNewWorker(url)
    val started = jsWorkerNow()
    // The worker listens only once its wasm has loaded (a message before that would be lost): requests wait for its
    // READY message
    var waiting: MutableList<String>? = mutableListOf()
    val channel = MessageEngineChannel({ text -> waiting?.add(text) ?: jsPostMessage(worker, text) }, EngineBoundary.log)
    jsOnWorkerMessages(worker, { text ->
        if (text == READY) {
            val queued = waiting.orEmpty()
            waiting = null
            EngineBoundary.log.log(false, "Engine boundary: worker ready after ${(jsWorkerNow() - started).toLong()} ms, " +
                "${queued.size} requests waited")
            queued.forEach { jsPostMessage(worker, it) }
        } else channel.received(text)
    }) { problem ->
        EngineBoundary.log.log(true, "Engine boundary: the worker failed: $problem")
    }
    EngineBoundary.log.log(false, "Engine boundary: worker started from $url")
    return channel
}

private fun jsNewWorker(url: String): JsAny = js("new Worker(url)")
private fun jsPostMessage(worker: JsAny, text: String): Unit = js("worker.postMessage(text)")
private fun jsOnWorkerMessages(worker: JsAny, received: (String) -> Unit, failed: (String) -> Unit): Unit = js("""{
    worker.onmessage = (event) => received(event.data);
    worker.onerror = (event) => failed(String(event.message || event));
    worker.onmessageerror = () => failed('a message could not be read');
}""")

/** What the engine's worker posts once it listens (webApp's engine module says the same). */
private const val READY = "ready"

private fun jsWorkerNow(): Double = js("performance.now()")
