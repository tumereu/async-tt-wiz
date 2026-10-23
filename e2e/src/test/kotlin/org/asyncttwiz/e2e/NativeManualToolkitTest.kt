package org.asyncttwiz.e2e

import io.appium.java_client.AppiumBy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.ScreenOrientation
import org.openqa.selenium.remote.RemoteWebElement
import org.openqa.selenium.support.ui.WebDriverWait
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Two real normal-APK UIs through existing NativeUi adapters. This test is authored/compiled,
 * not executed by the toolkit integration worker. It needs the complete device/backend bundle. */
@Tag("device")
class NativeManualToolkitTest {
    @Test fun twoUiManualToolkitWithDelayedProcessingAndRetainedSourceOccurrences() {
        check(System.getenv("ATW_START_STAGE").orEmpty() in setOf("", "setup"))
        NativeGameStartTest().twoPlayersReadyToActualInitialTableAndRecoverOriginalStart()
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val peer = Path.of(Files.readString(directory.resolve("peer-run.txt")))
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        fun control(operation: String) {
            val process = ProcessBuilder("python3", input.getProperty("control"), operation).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(150, TimeUnit.SECONDS) && process.exitValue() == 0) { output }
        }
        NativeLobbyCreationTest.Device(directory, directory.resolve("runner.properties")).use { first ->
            NativeLobbyCreationTest.Device(peer, peer.resolve("runner.properties")).use { second ->
                val players = listOf(first, second)
                for (ui in players) { ui.driver.rotate(ScreenOrientation.PORTRAIT); ui.ui.reopen("Native Initial Spark", "Shared table") }
                first.named("Table actions").click(); first.ui.scroll("Create token / Blank").click(); first.text("Blank").click()
                for (ui in players) ui.named("Blank, owner Start First, untapped")
                first.named("Blank, owner Start First, untapped").click()
                first.ui.scroll("Increase blue counter", description = true).click()
                control("pause")
                try {
                    repeat(2) { first.named("Increase blue counter").click() }
                    first.capture("toolkit-three-relative-edits-before-results")
                } finally { control("resume") }
                first.ui.scroll("Add Stack effect", description = true).click()
                first.ui.scroll("Declare without tapping", description = true).click()
                first.driver.navigate().back()
                for (ui in players) {
                    ui.named("Stack, 1 entries")
                    ui.named("Projection, Blank, owner Start First, untapped")
                }
                // The source and its combat occurrence share fields, not occurrence-removal selection.
                second.named("Projection, Blank, owner Start First, untapped").click()
                second.ui.scroll("Remove from combat", description = true).click()
                second.driver.navigate().back()
                for (ui in players) {
                    ui.named("Blank, owner Start First, untapped"); ui.named("Stack, 1 entries")
                    WebDriverWait(ui.driver, Duration.ofSeconds(30)).until {
                        ui.driver.findElements(AppiumBy.accessibilityId("Projection, Blank, owner Start First, untapped")).isEmpty()
                    }
                }
                // Hold End must not also execute Next on release. Native UI assertions, no game API.
                val next = first.named("Next step") as RemoteWebElement
                first.driver.executeScript("mobile: longClickGesture", mapOf("elementId" to next.id, "duration" to 650))
                first.capture("toolkit-held-end-turn")
                for (orientation in listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE)) {
                    for ((index, ui) in players.withIndex()) {
                        ui.driver.rotate(orientation); ui.named("Stack, 1 entries"); ui.named("Table actions")
                        ui.capture("toolkit-$index-${orientation.name.lowercase()}")
                    }
                }
                first.record("manual-toolkit-journey.json", mapOf("normalApks" to 2, "fullJourney" to false,
                    "assertions" to listOf("UI-only setup", "Blank token", "retained relative edits with delayed results",
                        "live source/combat/Stack", "occurrence-only removal", "both orientations"),
                    "remaining" to listOf("Library subsets/defaults/mulligans", "all field editors/IME/cancel", "token freeze",
                        "link endpoints", "disconnect/reconnect/restart", "full native capability and mock gates")))
                assertTrue(players.size == 2)
            }
        }
    }
}
