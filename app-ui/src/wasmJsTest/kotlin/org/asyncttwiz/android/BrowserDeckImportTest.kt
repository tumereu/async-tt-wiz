@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.asyncttwiz.appui.AppBuild
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Import from link" in the browser ([fetchDeckUrl]) over a stand-in fetch. */
class BrowserDeckImportTest {
    @BeforeTest fun build() = installTestBuild()
    @AfterTest fun reals() = realFetch()

    // WEB-010: Archidekt answers no browser directly, so the browser reads the deck through the ATW server (its own origin),
    // never from Archidekt, and gets the same deck
    @Test fun anArchidektDeckIsReadThroughTheServer() = runTest {
        fakeFetch(200, """{"name":"Ember Rush","cards":[
            {"quantity":4,"categories":[],"card":{"oracleCard":{"name":"Storm Spark"}}},
            {"quantity":2,"categories":["Sideboard"],"card":{"oracleCard":{"name":"Ember Cub"}}}]}""")
        val location = DeckUrlImport.location("https://archidekt.com/decks/123456/ember-rush")!!
        val deck = fetchDeckUrl(location)
        assertEquals("Ember Rush", deck.name)
        val requests = Json.parseToJsonElement(fakeRequestsJson()).jsonArray.map { it.jsonObject }
        assertEquals(listOf("${AppBuild.ACCOUNT_ORIGIN}/v1/import/archidekt/123456"), requests.map { it["url"]!!.jsonPrimitive.content })
        assertEquals("GET", requests.single()["method"]!!.jsonPrimitive.content)
    }
}
