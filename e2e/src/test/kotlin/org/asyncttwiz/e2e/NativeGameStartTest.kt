package org.asyncttwiz.e2e

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.appium.java_client.AppiumBy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import org.openqa.selenium.ScreenOrientation
import org.openqa.selenium.support.ui.WebDriverWait
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/** No action APIs: two ordinary UIs create all accounts/decks/invitation/preparation/readiness. */
@Tag("device")
class NativeGameStartTest {
    @Test fun twoPlayersReadyToActualInitialTableAndRecoverOriginalStart() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val phases = NativePhases(directory, "game-start", NativePhases.requested(
            System.getenv("ATW_START_STAGE").orEmpty().ifEmpty { "setup" }), listOf("setup", "starter", "table", "restart"))
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        val peer = Path.of(Files.readString(directory.resolve("peer-run.txt")))
        fun control(operation: String): JsonObject {
            val process = ProcessBuilder("python3", input.getProperty("control"), operation).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(150, TimeUnit.SECONDS) && process.exitValue() == 0) { output }
            return JsonParser.parseString(output).asJsonObject
        }
        NativeLobbyCreationTest.Device(directory, directory.resolve("runner.properties")).use { first ->
            NativeLobbyCreationTest.Device(peer, peer.resolve("runner.properties")).use { second ->
                first.driver.rotate(ScreenOrientation.PORTRAIT); second.driver.rotate(ScreenOrientation.PORTRAIT)
                fun records(ui: NativeLobbyCreationTest.Device) {
                    ui.text("Manage decks").click(); ui.button("Check for updates"); ui.button("Download card records")
                    ui.text("Card records are up to date.")
                }
                fun import(ui: NativeLobbyCreationTest.Device, text: String, name: String) {
                    ui.button("Choose deck"); ui.button("Import deck")
                    ui.driver.findElement(AppiumBy.className("android.widget.EditText")).sendKeys(text)
                    ui.hide(); ui.button("Review import")
                    ui.scroll("Deck name"); ui.driver.findElement(AppiumBy.className("android.widget.EditText")).sendKeys(name)
                    ui.hide(); ui.button("Save deck"); ui.text("Use selected deck"); ui.button(name); ui.button("Use selected deck")
                }
                fun starter(ui: NativeLobbyCreationTest.Device, label: String) {
                    val description = "Starting player: $label"
                    ui.ui.scroll(description, description = true).click()
                    WebDriverWait(ui.driver, Duration.ofSeconds(45)).until {
                        ui.driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Checking game outcome…\")")).isEmpty()
                    }
                }
                if (phases.includes("setup")) {
                    first.register("start-first@example.test", "Start First"); first.login("start-first@example.test", "Start First")
                    second.register("start-second@example.test", "Start Second"); second.login("start-second@example.test", "Start Second")
                    records(first); records(second)
                    first.destination("Contacts"); first.field("Search usernames").sendKeys("Start Sec"); first.hide(); first.button("Search")
                    first.button("Add contact"); first.button("Invite"); first.replaceName("Native Initial Spark"); first.button("Create & invite")
                    first.text("Select your deck to get ready.")
                    import(first, "Deck\n4 Coppercloud Surveyor (ZPA) 1\n2 Amberglass Chorus\nSideboard\n1 Feltwing Atlas", "First prepared source")
                    first.text("Mainboard · 6"); first.text("Sideboard · 1")
                    phases.completed("setup")
                }
                if (phases.includes("starter")) {
                    starter(first, "Start Second"); first.button("Ready"); first.text("Withdraw Ready")
                    control("first-ready"); first.capture("first-ready-before-acceptance")
                    first.button("Withdraw Ready"); first.text("Ready"); control("withdrawn")
                    starter(first, "Random"); control("no-prestart-roll")
                    first.button("Ready"); first.text("Withdraw Ready"); control("first-ready")
                    first.driver.navigate().back(); first.text("Invitations") // Leaving is not withdrawal.
                    second.destination("Games"); second.button("Accept"); second.text("Select your deck to get ready.")
                    val disabled = second.driver.findElement(By.xpath("//*[@text='Ready']/ancestor::*[@clickable='true'][1]"))
                    assertFalse(disabled.isEnabled); second.capture("accepted-no-deck-portrait")
                    second.driver.rotate(ScreenOrientation.LANDSCAPE); second.text("Select your deck to get ready."); second.capture("accepted-no-deck-landscape")
                    second.driver.rotate(ScreenOrientation.PORTRAIT)
                    import(second, "Deck\n3 Quiet Lattice Bolt\n2 Feltwing Atlas\nSideboard\n2 Velvet Prism Engine", "Second prepared source")
                    second.text("Mainboard · 5"); second.text("Sideboard · 2")
                    starter(second, "Start First"); control("other-ready-preserved")
                    starter(second, "Random"); control("no-prestart-roll")
                    for (orientation in listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE)) {
                        second.driver.rotate(orientation); second.text("✓ Ready"); second.text("Not ready"); second.capture("both-statuses-${orientation.name.lowercase()}")
                    }
                    second.driver.rotate(ScreenOrientation.PORTRAIT)
                    control("pause")
                    try {
                        second.button("Ready"); second.text("Checking game outcome…")
                        assertTrue(second.driver.findElements(AppiumBy.accessibilityId("Shared table")).isEmpty())
                        second.capture("second-ready-not-local-start")
                        control("commit-without-ack")
                    } finally { control("resume") }
                    phases.completed("starter")
                }
                val accepted = control("started")
                val play = accepted.getAsJsonArray("plays").single().asJsonObject
                val starter = play.getAsJsonObject("starter").get("accountId").asString
                val starterName = accepted.getAsJsonArray("players").single { it.asJsonObject.get("id").asString == starter }.asJsonObject.get("username").asString
                fun table(ui: NativeLobbyCreationTest.Device) {
                    ui.named("Shared table"); ui.text("Native Initial Spark")
                    ui.named("Start First Library, 6 cards"); ui.named("Start Second Library, 5 cards")
                    for (name in listOf("Start First", "Start Second")) {
                        ui.named("$name, life 20"); ui.named("$name Hand, 0 cards"); ui.named("$name Battlefield, 0 cards")
                        ui.named("$name Graveyard, 0 cards"); ui.named("$name Exile, 0 cards")
                    }
                    ui.named("Stack, 0 entries"); ui.named("Turn 1, $starterName, beginning / untap")
                    assertFalse(ui.driver.pageSource.contains("Sideboard"))
                    assertTrue(ui.driver.findElements(AppiumBy.accessibilityId("Back")).isEmpty())
                }
                if (phases.includes("table")) {
                    second.text("Native Initial Spark"); table(second)
                    first.ui.reopen("Native Initial Spark", "Shared table"); table(first)
                    for (orientation in listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE)) {
                        for ((index, ui) in listOf(first, second).withIndex()) {
                            ui.driver.rotate(orientation); table(ui); ui.capture("table-$index-${orientation.name.lowercase()}")
                            // Table uses the drawer even in landscape, unlike the non-gameplay shell.
                            ui.named("Open navigation").click(); ui.text("Games").click(); ui.text("Invitations")
                            ui.button("Native Initial Spark"); table(ui)
                        }
                    }
                    phases.completed("table")
                }
                first.restart(); first.ui.reopen("Native Initial Spark", "Shared table"); table(first)
                second.restart(); second.ui.reopen("Native Initial Spark", "Shared table"); table(second)
                second.driver.navigate().back(); second.text("Invitations"); second.button("Native Initial Spark"); table(second)
                first.capture("server-first-process-restart"); second.capture("peer-process-restart")
                val retained = control("final")
                assertEquals(accepted.getAsJsonArray("plays"), retained.getAsJsonArray("plays"))
                assertEquals(accepted.getAsJsonArray("requests"), retained.getAsJsonArray("requests"))
                phases.completed("restart")
                first.record("journey.json", phases.finish() + mapOf("normalApks" to 2, "bothOrientations" to phases.includes("table"),
                    "scope" to if (phases.fullJourney) "UI-created synthetic accounts/catalog/decks/invitation; explicit either-player/Random choices without prestart roll; first Ready before acceptance/withdrawal/leave; accepted no-deck guard and two truthful statuses; interrupted second-Ready acknowledgement with exact once-only accepted initial table, native Back/drawer, rotate/reopen/server-first restart. Full Cancel/list/preparation overlaps remain I12."
                        else "Retained continuation from ${phases.start} only; skipped prefix and complete E2E journey are not certified."))
            }
        }
    }
}
