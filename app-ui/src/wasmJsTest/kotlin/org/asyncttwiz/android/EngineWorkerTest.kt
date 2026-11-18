@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.asyncttwiz.appui.LogPriority
import org.asyncttwiz.appui.buildInfoMessage
import org.asyncttwiz.automatum.boundary.DeviceAnswer
import org.asyncttwiz.automatum.boundary.DeviceMessage
import org.asyncttwiz.automatum.boundary.EngineMessages
import org.asyncttwiz.automatum.boundary.EngineRequest
import org.asyncttwiz.automatum.boundary.LogLine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The page's side of the engine's Web Worker ([startEngineWorkers]) over a stand-in Worker: what the page posts to it and
 * when, where the worker's messages go, and what happens when it fails (docs/specs/web-client.md §1 item 3).
 */
class EngineWorkerTest {
    @BeforeTest fun fakes() { installTestBuild(); EngineBoundary.reset(); fakeWorker() }
    @AfterTest fun reals() { EngineBoundary.reset(); realWorker() }

    private fun worker(index: Int = 0): JsonObject = Json.parseToJsonElement(fakeWorkersJson()).jsonArray[index].jsonObject
    private fun posted(index: Int = 0): List<String> = worker(index)["posted"]!!.jsonArray.map { it.jsonPrimitive.content }

    // The worker listens only once its wasm has loaded: what the page asks before its "ready" waits, the page's build values
    // first (its devices read them), and goes in order once it is ready; later requests go at once
    @Test fun requestsWaitForTheWorkerAndTheBuildValuesGoFirst() {
        val channels = startEngineWorkers("/app/")
        assertEquals("/app/engine.js", worker()["url"]!!.jsonPrimitive.content)
        channels.planning.close("game-1")
        channels.planning.close("game-2")
        assertEquals(emptyList(), posted(), "nothing posted before the worker is ready")
        fakeWorkerSays(0, "ready")
        val sent = posted()
        assertEquals(3, sent.size, "the build values and both requests: $sent")
        assertEquals(buildInfoMessage(), sent[0])
        assertEquals(listOf("game-1", "game-2"), sent.drop(1).map { (EngineMessages.decodeRequest(it) as EngineRequest.Close).game })
        channels.planning.close("game-3")
        assertEquals("game-3", (EngineMessages.decodeRequest(posted().last()) as EngineRequest.Close).game, "after ready: at once")
        assertEquals(4, posted().size)
    }

    // The worker's device messages go to the games' devices (its log lines into the page's app log, so problem reports carry
    // them); anything else is a planning answer (one that can't be read is logged, never thrown)
    @Test fun theWorkersMessagesGoToTheDevicesOrThePlanning() {
        startEngineWorkers("/")
        fakeWorkerSays(0, "ready")
        fakeWorkerSays(0, DeviceMessage.answer("", DeviceAnswer.Log(listOf(LogLine(LogPriority.WARN, "device", "worker line 41")))))
        assertTrue(AppLog.text().lines().any { "W/device: [worker] worker line 41" in it }, "the worker's line in the app log")
        fakeWorkerSays(0, "{not an answer 42")
        assertTrue(AppLog.text().lines().any { "an answer couldn't be read" in it && "not an answer 42" in it },
            "an unreadable answer logged")
    }

    // A worker that fails ends its planning and devices: the table's plays are worked out on the page from then on, and the
    // failure is in the app log
    @Test fun aFailedWorkerSendsThePlanningBackToThePage() {
        val channels = startEngineWorkers("/")
        EngineBoundary.useRemote(channels.planning)
        EngineBoundary.useDevices(channels.devices)
        fakeWorkerSays(0, "ready")
        assertNotNull(EngineBoundary.remote)
        fakeWorkerFails(0, "out of memory 43")
        assertNull(EngineBoundary.remote, "planning back on the page")
        assertNull(EngineBoundary.devices, "games opened from now on keep their device on the page")
        assertTrue(AppLog.text().lines().any { "the worker failed: out of memory 43" in it })
    }
}
