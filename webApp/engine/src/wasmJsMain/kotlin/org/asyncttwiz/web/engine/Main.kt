@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.web.engine

import org.asyncttwiz.automatum.boundary.EngineAnswer
import org.asyncttwiz.automatum.boundary.EngineMessages
import org.asyncttwiz.automatum.boundary.EnginePlanner

/**
 * The engine's Web Worker (docs/specs/web-client.md §1 item 3): each message from the page is an engine boundary request
 * as a JSON string ([org.asyncttwiz.automatum.boundary.EngineRequest]), answered in order with one message, the answer
 * as a JSON string. The boards it plans on are the page's, sent as snapshots and events ([EnginePlanner]). It posts
 * "ready" once it listens: the page holds its requests until then (a message before it would be lost).
 */
fun main() {
    val planner = EnginePlanner()
    onRequest { text ->
        val answer = try { planner.handle(EngineMessages.decodeRequest(text)) } catch (problem: Exception) {
            // A request it can't read: answered with no number, which the page logs as an answer nothing waits for
            EngineAnswer.Failed(0, "", 0, 0, problem::class.simpleName ?: "Exception", "unreadable request: ${text.take(120)}")
        }
        answered(EngineMessages.encode(answer))
    }
    answered("ready")
}

private fun onRequest(handle: (String) -> Unit): Unit = js("self.onmessage = (event) => handle(event.data)")
private fun answered(text: String): Unit = js("self.postMessage(text)")
