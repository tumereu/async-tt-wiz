package arcanum.engine.state

import arcanum.engine.cards.Cards
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StateTest {
    // D35: token ids are name-based UUIDs (version 5), the same on every client.
    @Test
    fun tokenIdsAreVersion5Uuids() {
        // RFC 9562 test vector: the DNS namespace and "www.example.com"
        assertEquals("2ed6657d-e927-568b-95e1-2665a8aea6a2", TokenIds.uuid5("6ba7b810-9dad-11d1-80b4-00c04fd430c8", "www.example.com"))
        assertEquals(TokenIds.token("S12", 0), TokenIds.token("S12", 0))
        assertNotEquals(TokenIds.token("S12", 0), TokenIds.token("S12", 1))
    }

    // D21: an object id is the card and its zone-change count; serialised as one string so it can key JSON maps.
    @Test
    fun objectIdsSerialiseAsStrings() {
        val ids = listOf<ObjectId>(ObjectId.Card(CardId("alice.3"), 2), ObjectId.StackItem("S7"))
        val json = Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ObjectId.serializer()), ids)
        assertEquals("""["c:alice.3#2","s:S7"]""", json)
        assertEquals(ids, Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ObjectId.serializer()), json))
    }

    @Test
    fun everyCubeCardParsesFully() {
        Cards.cubeNames.forEach { Cards.definition(it) }
    }

    // CR 101.4: active player first, then the others in turn order.
    @Test
    fun apnapStartsWithTheActivePlayer() {
        val state = GameState.start(listOf("alice", "bob", "carol").map(::PlayerId))
        state.turn = state.turn.copy(active = PlayerId("bob"))
        assertEquals(listOf("bob", "carol", "alice"), state.apnap().map { it.value })
    }
}
