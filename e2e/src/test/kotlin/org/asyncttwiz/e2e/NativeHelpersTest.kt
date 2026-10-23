package org.asyncttwiz.e2e

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.w3c.dom.NodeList
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/** Host-only oracle/checkpoint tests. They never create an Appium session. */
class NativeHelpersTest {
    @Test fun exactSelectorsEscapeNativeAndXpathLiterals() {
        assertEquals("new UiSelector().text(\"Ready\")", NativeSelectors.selector("Ready"))
        assertEquals("new UiSelector().description(\"Starting player: Random\")",
            NativeSelectors.selector("Starting player: Random", description = true))
        assertEquals("\"a\\\"b\\\\c\\n\\r\\t\\u0001\"", NativeSelectors.javaString("a\"b\\c\n\r\t\u0001"))
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val xpath = XPathFactory.newInstance().newXPath()
        for (value in listOf("Ready", "Wizard's deck", "a\"b'c", "''\"", "line\nfeed")) {
            assertEquals(value, xpath.evaluate(NativeSelectors.xpathString(value), document))
        }
    }

    @Test fun actionSelectorChoosesClickableSelfOrNearestParentNotHeadingOrSubstring() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            """<hierarchy><node text="Ready" clickable="false"/>
                <node text="Withdraw Ready" clickable="true"/>
                <node id="outer" clickable="true"><node id="button" clickable="true"><node text="Ready"/></node></node>
                <node id="self" text="Ready" clickable="true"/></hierarchy>""".byteInputStream())
        val nodes = XPathFactory.newInstance().newXPath().evaluate(
            NativeSelectors.actionXPath("Ready"), document, XPathConstants.NODESET) as NodeList
        assertEquals(listOf("button", "self"), (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("id").nodeValue })
    }

    private fun withDirectory(test: (Path) -> Unit) {
        val root = Files.createDirectories(Path.of("build/native-helper-tests"))
        val directory = Files.createTempDirectory(root, "owned-")
        try { test(directory) } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun continuationNeverCertifiesSkippedAssertionsOrAggregatesAnEarlierRedRun() = withDirectory { directory ->
        val phases = listOf("setup", "table", "restart")
        val failed = NativePhases(directory, "game-start", "setup", phases)
        failed.completed("setup")
        assertThrows(IllegalStateException::class.java) { failed.finish() }
        val resumed = NativePhases(directory, "game-start", "restart", phases)
        assertFalse(resumed.includes("setup"))
        assertFalse(resumed.includes("table"))
        resumed.completed("restart")
        val result = resumed.finish()
        assertEquals(false, result["fullJourney"])
        assertEquals(listOf("setup", "table"), result["skippedPhases"])
        assertEquals(listOf("restart"), result["assertedPhases"])
        val records = Files.list(directory).use { it.toList().map { file -> JsonParser.parseString(Files.readString(file)).asJsonObject } }
        assertEquals(setOf("running", "continuation-passed"), records.map { it.get("status").asString }.toSet())
    }

    @Test fun unknownOutOfOrderOrIncompletePhasesFailRatherThanSkipRequiredAssertions() = withDirectory { directory ->
        val phases = listOf("setup", "restart")
        assertThrows(IllegalArgumentException::class.java) { NativePhases(directory, "game-start", "typo", phases) }
        val run = NativePhases(directory, "game-start", "setup", phases)
        assertThrows(IllegalStateException::class.java) { run.completed("restart") }
        run.completed("setup")
        assertThrows(IllegalStateException::class.java) { run.finish() }
        run.completed("restart")
        assertEquals(true, run.finish()["fullJourney"])
    }
}
