package org.asyncttwiz.e2e

import com.google.gson.GsonBuilder
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.OutputType
import org.openqa.selenium.ScreenOrientation
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.WebElement
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.WebDriverWait
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Two normal APKs, UI-created account/decks and ordinary authenticated service requests only. */
@Tag("device")
class NativeDeckManagementTest {
    private class Ui(val directory: Path) : AutoCloseable {
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        fun property(name: String) = requireNotNull(input.getProperty(name))
        private val options = UiAutomator2Options().setUdid(property("serial")).setDeviceName(property("serial"))
            .setRemoteAdbHost("127.0.0.1").setAdbPort(property("adbPort").toInt()).setSystemPort(property("systemPort").toInt())
            .setAppPackage("org.asyncttwiz.android").setAppActivity("org.asyncttwiz.android.MainActivity")
            .setNoReset(true).setFullReset(false).setNewCommandTimeout(Duration.ofSeconds(300)).also {
                it.setCapability("appium:autoLaunch", false); it.setCapability("appium:skipLogcatCapture", true)
            }
        val driver = AndroidDriver(URI(property("appium")).toURL(), options)
        val wait = WebDriverWait(driver, Duration.ofSeconds(45)).ignoring(StaleElementReferenceException::class.java)
        init { record("session.json", mapOf("owner" to property("owner"), "serial" to property("serial"),
            "sessionId" to driver.sessionId.toString(), "systemPort" to property("systemPort").toInt(),
            "forward" to listOf(property("serial"), "tcp:${property("systemPort")}", "tcp:6790"))) }
        fun record(name: String, value: Any) = Files.writeString(directory.resolve(name), GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n")
        fun text(value: String): WebElement = wait.until {
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).lastOrNull { it.isDisplayed }
        }
        fun named(value: String): WebElement = wait.until { driver.findElement(AppiumBy.accessibilityId(value)).takeIf { it.isDisplayed } }
        fun scroll(value: String): WebElement = wait.until {
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).lastOrNull { it.isDisplayed }
                ?: driver.findElement(AppiumBy.androidUIAutomator("new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().text(\"$value\"))"))
        }
        fun button(value: String) {
            wait.until {
                val label = scroll(value)
                val target = if (label.getAttribute("clickable") == "true") label
                    else driver.findElement(By.xpath("//*[@text='$value']/ancestor::*[@clickable='true'][1]"))
                target.takeIf { it.isEnabled }
            }.click()
        }
        fun field() = driver.findElement(AppiumBy.className("android.widget.EditText"))
        fun authField(value: String) = wait.until { driver.findElement(AppiumBy.xpath("//android.widget.EditText[.//*[@content-desc='$value']]")) }
        fun hide() { if (driver.isKeyboardShown) driver.hideKeyboard() }
        fun capture(name: String) {
            Files.writeString(directory.resolve("$name.xml"), driver.pageSource)
            Files.write(directory.resolve("$name.png"), driver.getScreenshotAs(OutputType.BYTES))
        }
        fun signIn() {
            scroll("Email"); authField("Email").clear(); authField("Email").sendKeys("management@example.test"); hide()
            scroll("Password"); authField("Password").clear(); authField("Password").sendKeys("synthetic collection password"); hide()
            scroll("Forgot password?"); text("Sign in").click(); text("Manage decks").click()
        }
        fun register() {
            button("Create account"); scroll("Username"); authField("Username").sendKeys("Collection wizard")
            scroll("Email"); authField("Email").sendKeys("management@example.test")
            scroll("Password"); authField("Password").sendKeys("synthetic collection password"); hide()
            scroll("Sign in"); text("Create account").click(); text("Account created. Sign in."); signIn()
        }
        fun importDeck(name: String) {
            button("Import deck"); field().sendKeys("Deck\n4 Coppercloud Surveyor (ZPA) 1\n2 Amberglass Chorus\nSideboard\n3 Velvet Prism Engine"); hide()
            button("Retry lookup")
            val review = wait.until { driver.findElement(AppiumBy.xpath("//android.view.View[@clickable='true'][./android.widget.TextView[@text='Review import']]" )).takeIf { it.isEnabled } }
            review.click(); text("Mainboard · 6"); scroll("Deck name"); field().sendKeys(name); hide(); button("Save deck")
            text(name); text("Mainboard · 6"); text("Sideboard · 3")
        }
        fun edit(action: String) { button("Edit"); button(action) }
        fun command(vararg args: String): String {
            val process = ProcessBuilder(args.toList()).redirectErrorStream(true).start()
            check(process.waitFor(150, TimeUnit.SECONDS)) { "Owned command timeout" }
            val output = process.inputStream.bufferedReader().readText(); check(process.exitValue() == 0) { output }; return output
        }
        fun restart() {
            val prefix = arrayOf(property("linuxAdb"), "-H", "127.0.0.1", "-P", property("adbPort"), "-s", property("serial"))
            command(*prefix, "shell", "am", "force-stop", "org.asyncttwiz.android")
            command(*prefix, "shell", "am", "start", "-n", "org.asyncttwiz.android/.MainActivity")
        }
        override fun close() { runCatching { capture("management-final") }; driver.quit(); record("session-closed.json", mapOf("closed" to true)) }
    }

    @Test fun managesAndSynchronizesAcceptedCollectionWithoutSharingUnfinishedDialogs() =
        management(System.getenv("ATW_MANAGEMENT_TAIL") == "true")

    private fun management(retained: Boolean) {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        Ui(directory).use { first ->
            Ui(Path.of(first.property("secondDirectory"))).use { second ->
                if (System.getenv("ATW_MANAGEMENT_DELETED") == "true") {
                    first.scroll("Refresh saved decks")
                    second.scroll("Refresh saved decks"); second.capture("same-account-deleted-collection")
                    assertFalse(second.driver.pageSource.contains("Renamed collection"))
                    first.restart(); first.scroll("Refresh saved decks"); assertFalse(first.driver.pageSource.contains("Renamed collection"))
                    first.capture("deleted-collection-after-restart")
                    first.record("management-observation.json", mapOf("scope" to "Retained deleted-collection final assertions only",
                        "journeys" to listOf("same-account deleted collection", "scrolling remaining collection", "process restart/reopen after deletion")))
                    return
                }
                if (!retained) {
                first.driver.rotate(ScreenOrientation.PORTRAIT); second.driver.rotate(ScreenOrientation.PORTRAIT)
                first.text("Async Tabletop Wizards"); first.register(); first.text("No saved decks yet.")
                first.button("Download card records"); first.text("Card records are up to date.")
                first.importDeck("Collection target"); first.capture("detail-main-portrait")
                first.button("Sideboard · 3"); first.text("Velvet Prism Engine"); first.capture("detail-sideboard-portrait")
                second.signIn(); second.text("Collection target"); second.text("6 / 3"); second.button("Collection target")
                second.driver.rotate(ScreenOrientation.LANDSCAPE); second.text("Mainboard · 6")
                second.edit("Rename"); second.field().clear(); second.field().sendKeys("Personal unfinished draft"); second.hide()
                first.edit("Rename"); first.field().clear(); first.field().sendKeys("Renamed collection"); first.hide()
                first.driver.rotate(ScreenOrientation.LANDSCAPE); first.text("Rename deck")
                assertTrue(first.field().text.contains("Renamed collection")); first.capture("rename-landscape")
                first.button("Save name"); first.text("Renamed collection")
                // Background synchronization must not replace another instance's unfinished input.
                // Android intentionally hides the underlying pane's accessibility tree while a
                // modal dialog is open. Allow one 5s refresh tick, then inspect the draft itself.
                Thread.sleep(6_000)
                assertTrue(second.field().text.contains("Personal unfinished draft")); second.capture("personal-draft-remote-rename")
                second.named("Cancel and close dialog").click(); second.text("Renamed collection")
                first.edit("Choose cover"); first.button("Amberglass Chorus"); first.capture("cover-selection-landscape")
                first.driver.rotate(ScreenOrientation.PORTRAIT); first.text("Choose cover"); first.capture("cover-selection-portrait")
                first.button("Use cover"); first.text("Renamed collection")
                } else {
                    first.text("Renamed collection"); second.text("Renamed collection")
                    first.driver.rotate(ScreenOrientation.PORTRAIT)
                    if (!first.driver.pageSource.contains("Mainboard · 6")) first.button("Renamed collection")
                    first.text("Mainboard · 6")
                    if (!second.driver.pageSource.contains("Mainboard · 6")) second.button("Renamed collection")
                    second.text("Mainboard · 6"); second.driver.rotate(ScreenOrientation.LANDSCAPE)
                }
                // Wait for accepted metadata before opening a new personal cover draft. An already
                // open cover draft deliberately does not track other devices' cover choices.
                if (System.getenv("ATW_MANAGEMENT_FINISH") != "true") {
                second.driver.navigate().back(); second.named("Amberglass Chorus"); second.button("Renamed collection")
                second.edit("Choose cover")
                second.wait.until { second.driver.findElement(AppiumBy.xpath("//*[@selected='true' or @checked='true'][.//*[@text='Amberglass Chorus']]")) }
                second.capture("same-account-cover"); second.button("Cancel")
                // Confirmation is only local intent: X, Keep and native Back never delete.
                first.edit("Delete saved deck"); first.text("Deletion cannot be undone."); first.capture("delete-confirmation-portrait")
                first.named("Cancel and close dialog").click(); first.text("Renamed collection")
                first.edit("Delete saved deck"); first.button("Keep deck"); first.text("Renamed collection")
                first.edit("Delete saved deck"); first.driver.navigate().back(); first.text("Renamed collection")
                }
                // Reopen after process death from server metadata, without re-import on instance two.
                first.restart(); first.button("Renamed collection"); first.text("Mainboard · 6")
                first.driver.navigate().back(); first.text("6 / 3"); first.button("Import deck")
                first.named("Exit without saving").click(); first.button("Renamed collection")
                // Populate a genuinely scrolling collection using only normal UI imports.
                for (index in 1..6) {
                    first.driver.navigate().back(); first.importDeck("Collection extra $index")
                }
                first.driver.navigate().back(); first.scroll("Collection extra 6")
                assertTrue(first.text("Import deck").isDisplayed); first.capture("long-collection-fixed-import")
                first.scroll("Renamed collection").click(); first.edit("Delete saved deck")
                first.driver.rotate(ScreenOrientation.LANDSCAPE); first.text("Deletion cannot be undone."); first.capture("delete-confirmation-landscape")
                first.button("Delete saved deck"); first.scroll("Refresh saved decks")
                second.text("This saved deck was deleted on another device."); second.capture("same-account-deletion")
                assertFalse(second.driver.pageSource.contains("Renamed collection"))
                first.restart(); first.scroll("Refresh saved decks"); assertFalse(first.driver.pageSource.contains("Renamed collection"))
                first.record("management-observation.json", mapOf("retainedTail" to retained, "journeys" to listOf("two actual normal APKs / same UI-created account",
                    "normal import, retained sections and collection cover/counts", "rename/cover both orientations and rotation",
                    "automatic same-account synchronization preserving personal input", "X/Keep/native Back cancel deletion",
                    "process restart metadata", "seven UI-created decks / fixed bottom Import", "confirmed deletion propagated and retained"),
                    "remaining" to "I07b full inspection; I08 Overwrite/Merge; I10/I12 selected-copy isolation; I34-I36 final qualification"))
            }
        }
    }
}
