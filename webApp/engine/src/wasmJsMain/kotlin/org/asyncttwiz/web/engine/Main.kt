@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.web.engine

import org.asyncttwiz.android.AutomatumBoards
import org.asyncttwiz.android.DeviceHost
import org.asyncttwiz.automatum.boundary.DeviceMessage
import org.asyncttwiz.automatum.boundary.EngineAnswer
import org.asyncttwiz.automatum.boundary.EngineMessages
import org.asyncttwiz.automatum.boundary.EnginePlanner
import org.asyncttwiz.storage.ParsedCard

/**
 * The engine's Web Worker (docs/specs/web-client.md §1 item 3): each message from the page is an engine boundary request
 * as a JSON string. Planning requests ([org.asyncttwiz.automatum.boundary.EngineRequest]) are answered in order with one
 * message each, on the boards the page sends ([EnginePlanner]). Device requests ([DeviceMessage], web client step 4c part
 * 3) go to the games' devices that live here ([DeviceHost]), which answer with the boards they publish, each request's
 * result, their log lines and their lookups in the page's card records. It posts "ready" once it listens: the page holds
 * its requests until then (a message before it would be lost).
 */
fun main() {
    val planner = EnginePlanner()
    val devices = DeviceHost(::answered)
    devices.forwardLogs()
    // The games' card definitions, parsed here from their own card data (the page keeps its parsed cards in its storage)
    AutomatumBoards.definitionSource = { submitted ->
        submitted.associate { s -> s.digest to org.asyncttwiz.protocol.game.GameCards.parse(s.definition).let { p ->
            p.definition?.let { ParsedCard.Read(it, p.manualAbilities) } ?: ParsedCard.Unreadable(p.problem.orEmpty()) } }
    }
    onRequest { text ->
        // Web client step 4d: the page's build values, its first message (the devices read them)
        if (org.asyncttwiz.appui.installBuildInfoMessage(text)) return@onRequest
        if (DeviceMessage.isDevice(text)) { devices.handle(text); return@onRequest }
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
