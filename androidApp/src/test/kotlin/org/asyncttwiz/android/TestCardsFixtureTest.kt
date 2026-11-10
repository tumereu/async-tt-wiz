package org.asyncttwiz.android

import com.google.gson.JsonParser
import java.io.File
import org.asyncttwiz.e2e.TestCardsFixture
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The test cards' catalog (tests/fixtures/card-catalog) holds the e2e test cards exactly as [org.asyncttwiz.e2e.TestCards]
 * says, so the server builds their card data from it (owner, 2026-11-10: card data from the server's catalog). After a
 * change to the test cards, `./scripts/gradle.sh :androidApp:testDebugUnitTest -PtestCards=record --tests '*TestCardsFixtureTest*'`
 * writes them into the file.
 */
class TestCardsFixtureTest {
    private val file = File(requireNotNull(System.getProperty("atw.testCardsFixture")))

    @Test fun theFixtureHoldsTheTestCards() {
        val generated = TestCardsFixture.sets()
        if (System.getProperty("atw.recordTestCards") == "true") file.writeText(TestCardsFixture.merged(file, generated))
        val data = JsonParser.parseString(file.readText()).asJsonObject.getAsJsonObject("data")
        for ((code, set) in generated) assertEquals(set, data.get(code),
            "Set $code of ${file.path} isn't TestCards: run with -PtestCards=record to write it")
    }
}
