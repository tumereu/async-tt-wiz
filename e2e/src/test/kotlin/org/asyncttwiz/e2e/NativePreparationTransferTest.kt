package org.asyncttwiz.e2e

import com.google.gson.JsonParser
import io.appium.java_client.AppiumBy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import org.openqa.selenium.ScreenOrientation
import org.openqa.selenium.interactions.PointerInput
import org.openqa.selenium.interactions.Sequence
import org.openqa.selenium.support.ui.WebDriverWait
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Tag("device")
class NativePreparationTransferTest {
    @Test fun capturedTapDragAndSharedPartialReconciliationBothOrientations() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        val peer = Path.of(Files.readString(directory.resolve("peer-run.txt")))
        fun control(operation: String): com.google.gson.JsonObject {
            val process = ProcessBuilder("python3", input.getProperty("control"), operation).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) { output }
            return JsonParser.parseString(output).asJsonObject
        }
        NativeLobbyCreationTest.Device(directory, directory.resolve("runner.properties")).use { ui ->
            NativeLobbyCreationTest.Device(peer, peer.resolve("runner.properties")).use { second ->
                ui.driver.rotate(ScreenOrientation.PORTRAIT); second.driver.rotate(ScreenOrientation.PORTRAIT)
                val stage = System.getenv("ATW_TRANSFER_STAGE").orEmpty()
                if (stage.isEmpty()) {
                    ui.register("transfer-recipient@example.test", "Transfer Recipient")
                    ui.register("transfer-inviter@example.test", "Transfer Inviter"); ui.login("transfer-inviter@example.test", "Transfer Inviter")
                    ui.text("Manage decks").click(); ui.button("Check for updates"); ui.button("Download card records"); ui.text("Card records are up to date.")
                    ui.destination("Contacts"); ui.field("Search usernames").sendKeys("Transfer Rec"); ui.hide(); ui.button("Search")
                    ui.button("Add contact"); ui.text("Added"); ui.button("Invite"); ui.replaceName("Native Transfer Spark"); ui.button("Create & invite")
                    ui.button("Choose deck"); ui.button("Import deck")
                    val field = ui.driver.findElement(AppiumBy.className("android.widget.EditText"))
                    field.sendKeys("Deck\n4 Coppercloud Surveyor (ZPA) 1\n2 Amberglass Chorus\n1 Feltwing Atlas\n1 Quiet Lattice Bolt\n1 Folded Lantern Road\n1 Mauve Window Expanse\n1 Threadglass Envoy\n1 Prismgate Bulwark\nSideboard\n3 Velvet Prism Engine")
                    ui.hide(); ui.scroll("Review import")
                    WebDriverWait(ui.driver, Duration.ofSeconds(45)).until {
                        ui.driver.findElement(By.xpath("//android.view.View[@clickable='true'][./android.widget.TextView[@text='Review import']]" )).takeIf { it.isEnabled }
                    }.click()
                    ui.scroll("Deck name"); ui.driver.findElement(AppiumBy.className("android.widget.EditText")).sendKeys("Transfer source")
                    ui.hide(); ui.button("Save deck"); ui.text("Use selected deck"); ui.button("Transfer source"); ui.button("Use selected deck")
                    ui.text("Mainboard · 12"); ui.button("Prepare deck")
                    second.login("transfer-inviter@example.test", "Transfer Inviter"); second.text("Games").click(); second.button("Native Transfer Spark"); second.button("Prepare deck")
                }
                fun counts(main: Int, side: Int) { ui.text("Mainboard · $main"); ui.text("Sideboard · $side") }
                fun handle(device: NativeLobbyCreationTest.Device, all: Boolean, section: String): org.openqa.selenium.WebElement {
                    val label = "Move ${if (all) "all" else "one"} Coppercloud Surveyor from $section"
                    if (device.driver.findElements(AppiumBy.accessibilityId(label)).none { it.isDisplayed })
                        device.driver.findElement(AppiumBy.androidUIAutomator("new UiScrollable(new UiSelector().description(\"$section preparation\")).scrollIntoView(new UiSelector().description(\"$label\"))"))
                    return device.named(label) // Do not use the scroll expression's returned container as the handle.
                }
                fun drag(all: Boolean, section: String, valid: Boolean = true, hold: Boolean = false) {
                    val start = handle(ui, all, section).rect
                    val target = ui.named("${if (section == "Mainboard") "Sideboard" else "Mainboard"} preparation").rect
                    val finger = PointerInput(PointerInput.Kind.TOUCH, "transfer")
                    val x = start.x + start.width / 2; val y = start.y + start.height / 2
                    val endX = if (valid) target.x + target.width / 2 else 5
                    val endY = if (valid) target.y + minOf(50, target.height / 2) else 5
                    ui.record("last-drag.json", mapOf("start" to listOf(x, y), "end" to listOf(endX, endY), "all" to all, "source" to section))
                    val sequence = Sequence(finger, 0)
                        .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), x, y))
                        .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                        .addAction(finger.createPointerMove(Duration.ofMillis(150), PointerInput.Origin.viewport(), x + 35, y))
                    if (hold) sequence.addAction(org.openqa.selenium.interactions.Pause(finger, Duration.ofSeconds(6)))
                    sequence.addAction(finger.createPointerMove(Duration.ofMillis(450), PointerInput.Origin.viewport(), endX, endY))
                        .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()))
                    ui.driver.perform(listOf(sequence))
                }
                run {
                    for (orientation in listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE)) {
                        ui.driver.rotate(orientation); counts(12, 3)
                        if (!(stage.startsWith("portrait-") && orientation == ScreenOrientation.PORTRAIT)) {
                            handle(ui, false, "Mainboard").click(); counts(11, 4)
                            assertTrue(ui.driver.findElements(AppiumBy.accessibilityId("Close card inspection")).isEmpty())
                            handle(ui, true, "Mainboard").click(); counts(8, 7)
                            handle(ui, true, "Sideboard").click(); counts(12, 3)
                        }
                        if (!(stage == "portrait-outside" && orientation == ScreenOrientation.PORTRAIT)) {
                            drag(false, "Mainboard"); counts(11, 4)
                            drag(true, "Sideboard"); counts(12, 3)
                        }
                        val before = control("observe")["requests"].asInt
                        drag(true, "Mainboard", valid = false); counts(12, 3)
                        assertEquals(before, control("observe")["requests"].asInt)
                        ui.capture("transfers-${orientation.name.lowercase()}")
                    }
                    ui.driver.rotate(ScreenOrientation.PORTRAIT)
                    ui.named("Inspect Feltwing Atlas in Mainboard").click(); ui.named("Close card inspection")
                    ui.capture("missing-art-inspection"); ui.driver.navigate().back(); counts(12, 3)
                    // A drag captures four original copies before a normal same-account other-device move.
                    val executor = Executors.newSingleThreadExecutor()
                    try {
                        val otherMove = executor.submit {
                            Thread.sleep(1200); handle(second, false, "Mainboard").click(); second.text("Mainboard · 11")
                        }
                        drag(true, "Mainboard", hold = true); otherMove.get(30, TimeUnit.SECONDS)
                    } finally { executor.shutdownNow() }
                    counts(8, 7); ui.capture("partial-conflict-reconciled")
                    val partial = control("partial"); assertEquals(1, partial["rejectedTransfers"].asInt)
                    handle(ui, true, "Sideboard").click(); counts(12, 3)
                    // Keep the established socket alive while the owned service is briefly suspended.
                    control("pause")
                    try {
                        handle(ui, false, "Mainboard").click(); counts(11, 4)
                        assertEquals(30, control("paused")["requests"].asInt)
                        ui.capture("predicted-without-ack")
                    } finally { control("resume") }
                    WebDriverWait(ui.driver, Duration.ofSeconds(45)).until { control("observe")["requests"].asInt == 31 }
                    counts(11, 4); handle(ui, false, "Sideboard").click(); counts(12, 3)
                    ui.named("Close preparation").click(); ui.text("Mainboard · 12"); ui.button("Prepare deck"); counts(12, 3)
                    ui.restart(); ui.button("Native Transfer Spark"); ui.button("Prepare deck"); counts(12, 3)
                    ui.capture("reopened-server-first"); control("final")
                }
                ui.record("journey.json", mapOf("normalApks" to 2, "bothOrientations" to true,
                    "scope" to "UI-created synthetic prepared game; compact tap one/all both ways, drag both controls, outside-drop/no double dispatch, missing-art inspector, normal same-account conflicting gesture capture/partial outcome, paused-service prediction, Back/reopen/server-first restart. Native accessibility and animation clocks checked separately."))
            }
        }
    }
}
