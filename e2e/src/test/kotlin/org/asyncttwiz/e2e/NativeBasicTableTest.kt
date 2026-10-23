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

/** Two ordinary normal-APK UIs, no gameplay API or opponent injection.
 * Run ONLY through the existing complete two-device/backend/routing resource lifecycle.
 * I16a compiles this test; I16b owns execution and every delayed/disconnect/restart assertion.
 */
@Tag("device")
class NativeBasicTableTest {
    @Test fun twoUiTableMovesInflightConflictsDisconnectedReadOnlyAndServerFirstRestart() {
        check(System.getenv("ATW_START_STAGE").orEmpty() in setOf("", "setup")) {
            "The complete basic-table journey requires UI setup, not a retained partial start phase"
        }
        // Reuse the existing UI-only setup rather than inventing another device/backend runner.
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
                fun table(ui: NativeLobbyCreationTest.Device) = ui.ui.reopen("Native Initial Spark", "Shared table")
                fun close(ui: NativeLobbyCreationTest.Device) = ui.driver.navigate().back()
                fun draw(ui: NativeLobbyCreationTest.Device, name: String, count: Int) {
                    ui.named("$name Library, $count cards").click(); ui.named("Draw one").click()
                    ui.named("$name Hand, 1 cards")
                }
                fun card(ui: NativeLobbyCreationTest.Device, owner: String, tapped: Boolean = false) =
                    ui.named("Covered card, owner $owner, ${if (tapped) "tapped" else "untapped"}")
                fun move(ui: NativeLobbyCreationTest.Device, owner: String, destination: String) {
                    card(ui, owner).click(); ui.ui.scroll("Move to $destination", description = true).click()
                }
                players.forEach { it.driver.rotate(ScreenOrientation.PORTRAIT); table(it) }
                // A complete UI-created journey begins with empty hands/fields, not seeded game state.
                draw(first, "Start First", 6); draw(second, "Start Second", 5)
                first.named("Start Second Hand, 1 cards"); second.named("Start First Hand, 1 cards")
                // Hold is personal; no orientation or movement follows the consumed pointer sequence.
                val held = card(first, "Start First") as RemoteWebElement
                first.driver.executeScript("mobile: longClickGesture", mapOf("elementId" to held.id, "duration" to 650))
                close(first); card(first, "Start First")
                // Two independent moves are sent while the backend withholds processing/acknowledgements.
                control("pause")
                try {
                    move(first, "Start First", "Start First battlefield")
                    move(second, "Start Second", "Start Second battlefield")
                    first.named("Start First Battlefield, 1 cards"); second.named("Start Second Battlefield, 1 cards")
                    first.capture("independent-predictions-before-ack"); second.capture("peer-prediction-before-ack")
                } finally { control("resume") }
                for (ui in players) { ui.named("Start First Battlefield, 1 cards"); ui.named("Start Second Battlefield, 1 cards") }
                // Same captured real card, conflicting destinations; one accepted location, never two copies.
                card(first, "Start First").click(); card(second, "Start First").click()
                control("pause")
                try {
                    first.ui.scroll("Move to Start First exile", description = true).click()
                    second.ui.scroll("Move to Start First graveyard", description = true).click()
                    first.capture("same-card-first-prediction"); second.capture("same-card-peer-prediction")
                } finally { control("resume") }
                for (ui in players) closeIfActions(ui)
                val wait = WebDriverWait(first.driver, Duration.ofSeconds(45))
                wait.until {
                    val a = first.driver.pageSource; val b = second.driver.pageSource
                    (a.contains("Start First Exile, 1 cards") && b.contains("Start First Exile, 1 cards")) ||
                        (a.contains("Start First Graveyard, 1 cards") && b.contains("Start First Graveyard, 1 cards"))
                }
                val exileWon = first.driver.pageSource.contains("Start First Exile, 1 cards")
                fun singleLocation(ui: NativeLobbyCreationTest.Device) {
                    ui.named("Start First Exile, ${if (exileWon) 1 else 0} cards")
                    ui.named("Start First Graveyard, ${if (exileWon) 0 else 1} cards")
                    ui.named("Start First Hand, 0 cards"); ui.named("Start First Battlefield, 0 cards")
                    ui.named("Start First Library, 5 cards")
                }
                for (ui in players) singleLocation(ui)
                for (ui in players) ui.named("Start First Battlefield, 0 cards")
                // Delayed life acknowledgements must not serialize rapid completed edits or rewind totals.
                first.named("Start First, life 20").click(); control("pause")
                try {
                    repeat(3) { first.named("Increase life").click() }
                    first.capture("three-life-edits-before-ack")
                } finally { control("resume") }
                first.named("Close player actions").click()
                for (ui in players) ui.named("Start First, life 23")
                val single = card(second, "Start Second") as RemoteWebElement
                second.driver.executeScript("mobile: doubleClickGesture", mapOf("elementId" to single.id))
                for (ui in players) card(ui, "Start Second", tapped = true)
                for (orientation in listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE)) {
                    for ((index, ui) in players.withIndex()) {
                        ui.driver.rotate(orientation); ui.named("Start First, life 23")
                        card(ui, "Start Second", tapped = true); ui.capture("basic-$index-${orientation.name.lowercase()}")
                    }
                }
                // A stopped backend is connectivity fault injection, not a synthetic game-state mutation.
                control("pause")
                try {
                    for (ui in players) {
                        ui.text("Reconnecting…"); ui.text("Back to games list")
                        assertTrue(ui.driver.findElements(AppiumBy.accessibilityId("Increase life")).isEmpty())
                        ui.capture("disconnected-read-only")
                    }
                    first.button("Back to games list"); first.text("Invitations")
                } finally { control("resume") }
                first.ui.reopen("Native Initial Spark", "Shared table"); second.named("Shared table")
                for (ui in players) {
                    ui.named("Start First, life 23"); card(ui, "Start Second", tapped = true)
                    ui.restart(); table(ui); ui.named("Start First, life 23"); card(ui, "Start Second", tapped = true)
                }
                // Server-first restart retains accepted results, not replayed outgoing UI commands.
                for (ui in players) { singleLocation(ui); ui.named("Start Second Battlefield, 1 cards") }
                first.record("basic-table-journey.json", mapOf("normalApks" to 2, "fullJourney" to true,
                    "assertions" to listOf("UI-only setup", "captured independent/same-card moves", "multiple life requests before ack",
                        "convergence without rewind/duplicate", "consumed hold", "both orientations", "disconnected read-only/list escape",
                        "same-process reconnect", "server-first process restart")))
            }
        }
    }

    private fun closeIfActions(ui: NativeLobbyCreationTest.Device) {
        if (ui.driver.findElements(AppiumBy.accessibilityId("Inspect")).isNotEmpty()) ui.driver.navigate().back()
    }
}
