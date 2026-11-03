package org.asyncttwiz.protocol.game

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * I-D54: stored games depend on the engine's serialized format (events, checkpoints, definitions). A frozen sample of a
 * game (its definitions, its state before the first event and 60 events, as [EngineJson] stored them) must still read,
 * write back byte for byte and apply in order. When this fails, the engine's or the table's format changed: stored games
 * need a reader for the old format or a wipe migration (owner's call), and the sample is re-recorded with
 * `./scripts/gradle.sh :protocol:jvmTest -Patw.recordEventFormat=true --tests '*EventFormatJvmTest*'`.
 */
class EventFormatJvmTest {
    private val sample = File("src/jvmTest/resources/game/event-format-sample.json")

    @Test
    fun aStoredGameStillReadsWritesBackAndApplies() {
        if (System.getProperty("atw.recordEventFormat") == "true") record()
        val stored = Json.parseToJsonElement(sample.readText()).jsonObject
        val definitions = stored.getValue("definitions").toString()
        val codec = EngineJson(EngineJson.decodeDefinitions(definitions))
        assertEquals(definitions, codec.encodeDefinitions())
        val initial = stored.getValue("initial").toString()
        val model = codec.decodeModel(initial)
        assertEquals(initial, codec.encodeModel(model))
        val events = stored.getValue("events").jsonArray
        assertTrue(events.size == 60)
        for (element in events) {
            val text = element.toString()
            val event = codec.decodeEvent(text)
            assertEquals(text, codec.encodeEvent(event), "${event.id} writes back as stored")
            model.apply(event)
        }
        val last = stored.getValue("last").toString()
        assertTrue(model.sameAs(codec.decodeModel(last)), "the stored events give the stored state")
    }

    private fun record() {
        val games = EngineGames(31)
        val codec = games.codec
        val model = games.model()
        val initial = codec.encodeModel(model)
        val events = games.play(model, "root", 60).map { codec.encodeEvent(it.first) }
        val json = JsonObject(mapOf(
            "definitions" to Json.parseToJsonElement(codec.encodeDefinitions()),
            "initial" to Json.parseToJsonElement(initial),
            "events" to JsonArray(events.map(Json::parseToJsonElement)),
            "last" to Json.parseToJsonElement(codec.encodeModel(model)),
        ))
        sample.parentFile.mkdirs()
        sample.writeText(json.toString() + "\n")
    }
}
