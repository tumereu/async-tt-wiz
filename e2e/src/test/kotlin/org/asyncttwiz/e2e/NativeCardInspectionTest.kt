package org.asyncttwiz.e2e

import com.google.gson.GsonBuilder
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.*
import org.openqa.selenium.support.ui.WebDriverWait
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Real normal-APK entry/import/save/inspection; no seeded account, saved deck or alternate launcher. */
@Tag("device")
class NativeCardInspectionTest {
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
        val wait = WebDriverWait(driver, Duration.ofSeconds(40)).ignoring(StaleElementReferenceException::class.java)
        init { record("session.json", mapOf("owner" to property("owner"), "serial" to property("serial"),
            "sessionId" to driver.sessionId.toString(), "systemPort" to property("systemPort").toInt(),
            "forward" to listOf(property("serial"), "tcp:${property("systemPort")}", "tcp:6790"))) }
        fun record(name: String, value: Any) = Files.writeString(directory.resolve(name), GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n")
        fun text(value: String): WebElement = wait.until {
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).lastOrNull { it.isDisplayed }
        }
        fun named(value: String): WebElement = wait.until { driver.findElement(AppiumBy.accessibilityId(value)).takeIf { it.isDisplayed } }
        fun scroll(value: String, inspection: Boolean = false): WebElement = wait.until {
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).lastOrNull { it.isDisplayed }
                ?: driver.findElement(AppiumBy.androidUIAutomator("new UiScrollable(new UiSelector().${if (inspection) "description(\"Card inspection content\")" else "scrollable(true)"}).scrollIntoView(new UiSelector().text(\"$value\"))"))
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
        fun auth(value: String) = wait.until { driver.findElement(AppiumBy.xpath("//android.widget.EditText[.//*[@content-desc='$value']]")) }
        fun hide() { if (driver.isKeyboardShown) driver.hideKeyboard() }
        fun capture(name: String) {
            Files.writeString(directory.resolve("$name.xml"), driver.pageSource)
            Files.write(directory.resolve("$name.png"), driver.getScreenshotAs(OutputType.BYTES))
        }
        fun adb(vararg args: String): String {
            val process = ProcessBuilder(listOf(property("linuxAdb"), "-H", "127.0.0.1", "-P", property("adbPort"), "-s", property("serial")) + args)
                .redirectErrorStream(true).start()
            check(process.waitFor(150, TimeUnit.SECONDS)) { "Owned ADB timeout" }
            val output = process.inputStream.bufferedReader().readText(); check(process.exitValue() == 0) { output }; return output.trim()
        }
        fun restart() {
            adb("shell", "am", "force-stop", "org.asyncttwiz.android")
            adb("shell", "am", "start", "-n", "org.asyncttwiz.android/.MainActivity")
        }
        fun restoreScale(value: String) {
            if (value == "null") adb("shell", "settings", "delete", "system", "font_scale")
            else adb("shell", "settings", "put", "system", "font_scale", value)
        }
        fun register() {
            button("Create account"); scroll("Username"); auth("Username").sendKeys("Inspection wizard")
            scroll("Email"); auth("Email").sendKeys("inspection@example.test")
            scroll("Password"); auth("Password").sendKeys("synthetic inspection password"); hide()
            scroll("Sign in"); text("Create account").click(); text("Account created. Sign in.")
            scroll("Email"); auth("Email").clear(); auth("Email").sendKeys("inspection@example.test")
            scroll("Password"); auth("Password").sendKeys("synthetic inspection password"); hide()
            scroll("Forgot password?"); text("Sign in").click(); text("Manage decks").click()
        }
        fun thumbnail(name: String) {
            scroll(name)
            val target = named("Inspect artwork for $name")
            val density = adb("shell", "wm", "density").substringAfterLast(":").trim().toInt() / 160.0
            assertTrue(target.size.width / density >= 48 && target.size.height / density >= 48,
                "Independent thumbnail target must be at least 48dp: ${target.size} / $density")
            target.click(); named("Close card inspection")
        }
        override fun close() { runCatching { capture("inspection-final") }; driver.quit(); record("session-closed.json", mapOf("closed" to true)) }
    }

    @Test fun inspectsExactPreviewAndSavedFacesWithoutChangingDraftOrCollection() {
        Ui(Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))).use { ui ->
            val finish = System.getenv("ATW_INSPECTION_FINISH") == "true"
            // The retained finish repairs this scenario's previously failed restoration of an
            // originally absent setting. It does not adopt or reset a different device's state.
            val originalScale = if (finish) "null" else ui.adb("shell", "settings", "get", "system", "font_scale")
            try {
                if (System.getenv("ATW_INSPECTION_ART") == "true") {
                    ui.driver.rotate(ScreenOrientation.PORTRAIT); ui.restart(); ui.button("Inspection retained draft")
                    ui.thumbnail("Dayglass Loom // Nightglass Weave")
                    val front = ui.named("Artwork for Dayglass Loom")
                    assertTrue(front.size.width >= 500 && front.size.height >= 650, "Full inspector art must fill its allocated scene, not intrinsic thumbnail pixels")
                    ui.capture("full-art-front-portrait"); ui.button("Next face")
                    val back = ui.named("Artwork for Nightglass Weave")
                    assertTrue(back.size.width >= 500 && back.size.height >= 650)
                    ui.capture("full-art-back-portrait"); ui.driver.rotate(ScreenOrientation.LANDSCAPE)
                    ui.text("2 / 2"); ui.named("Artwork for Nightglass Weave"); ui.capture("full-art-back-landscape")
                    ui.button("Enlarge artwork"); ui.button("Fit artwork")
                    ui.adb("shell", "settings", "put", "system", "font_scale", "1.6")
                    ui.text("2 / 2"); ui.scroll("Power / Toughness: 4 / 1", true)
                    ui.capture("full-art-large-text"); ui.named("Close card inspection").click()
                    ui.text("Mainboard · 14"); ui.restoreScale(originalScale)
                    ui.driver.rotate(ScreenOrientation.PORTRAIT)
                    ui.record("art-size-passed.json", mapOf("fullSceneBounds" to true, "bothFaces" to true,
                        "rotation" to true, "zoomFit" to true, "largeText" to true))
                    return
                }
                if (finish) {
                    ui.restoreScale(originalScale); ui.driver.rotate(ScreenOrientation.PORTRAIT); ui.restart()
                    ui.button("Inspection retained draft"); ui.thumbnail("Coppercloud Surveyor")
                    ui.named("Artwork for Coppercloud Surveyor"); ui.scroll("Original synthetic survey text, revision 1.", true)
                    ui.capture("saved-after-restart"); ui.named("Close card inspection").click()
                    ui.text("Mainboard · 14"); ui.text("Sideboard · 3")
                    ui.record("inspection-passed.json", mapOf("scope" to "Retained restart and inspector reopen only",
                        "originalAbsentFontScaleRestored" to true, "processRestart" to true, "quantitiesPreserved" to true))
                    return
                }
                ui.driver.rotate(ScreenOrientation.PORTRAIT)
                if (System.getenv("ATW_INSPECTION_TAIL") != "true") {
                    ui.text("Async Tabletop Wizards"); ui.register(); ui.text("No saved decks yet.")
                    ui.button("Download card records"); ui.text("Card records are up to date.")
                    ui.button("Import deck")
                    val list = "Deck\n4 Coppercloud Surveyor (ZPA) 1\n2 Dayglass Loom // Nightglass Weave\n1 Velvet Prism Engine\n1 Amberglass Chorus\n1 Quiet Lattice Bolt\n1 Folded Lantern Road\n1 Mauve Window Expanse\n1 Threadglass Envoy\n1 Prismgate Bulwark\n1 Feltwing Atlas\nSideboard\n3 Coppercloud Echo!"
                    ui.field().sendKeys(list); ui.hide(); ui.button("Retry lookup")
                    ui.wait.until { ui.driver.findElement(AppiumBy.xpath("//android.view.View[@clickable='true'][./android.widget.TextView[@text='Review import']]" )).takeIf { it.isEnabled } }.click()
                    ui.scroll("Deck name"); ui.field().sendKeys("Inspection retained draft"); ui.hide()
                    ui.thumbnail("Coppercloud Surveyor")
                    ui.named("Artwork for Coppercloud Surveyor"); ui.capture("preview-single-art-portrait")
                    ui.scroll("Original synthetic survey text, revision 1.", true)
                    ui.scroll("Power / Toughness: 2 / 3", true)
                    ui.named("Close card inspection").click()
                    ui.scroll("Deck name"); assertEquals("Inspection retained draft", ui.field().text)
                    ui.thumbnail("Dayglass Loom // Nightglass Weave")
                    ui.named("Artwork for Dayglass Loom"); ui.button("Next face")
                    ui.text("2 / 2"); ui.named("Artwork for Nightglass Weave"); ui.capture("preview-back-art-portrait")
                    ui.driver.rotate(ScreenOrientation.LANDSCAPE)
                    ui.text("2 / 2"); ui.scroll("Power / Toughness: 4 / 1", true); ui.capture("preview-back-text-landscape")
                    ui.button("Previous face"); ui.text("1 / 2")
                    ui.driver.navigate().back()
                    ui.scroll("Deck name"); assertEquals("Inspection retained draft", ui.field().text)
                    ui.driver.rotate(ScreenOrientation.PORTRAIT)
                    ui.thumbnail("Feltwing Atlas"); ui.text("Artwork for Feltwing Atlas")
                    ui.scroll("Original synthetic descriptive text. No executable behavior.", true); ui.capture("preview-missing-art")
                    ui.named("Close card inspection").click()
                    ui.scroll("Coppercloud Echo!").click(); ui.named("Close card inspection")
                    ui.text("Artwork for Coppercloud Echo!"); ui.scroll("Power / Toughness: 2 / *", true); ui.capture("preview-broken-art-row-alternative")
                    ui.driver.navigate().back(); ui.scroll("Save deck"); ui.button("Save deck")
                    ui.text("Inspection retained draft"); ui.text("Mainboard · 14"); ui.text("Sideboard · 3")
                    ui.record("preview-passed.json", mapOf("preview" to true, "thumbnail48dp" to true,
                        "facesArtTextRotationBack" to true, "missingAndBrokenArt" to true, "draftPreservedUntilExplicitSave" to true))
                } else {
                    ui.text("Inspection retained draft")
                    if (!ui.driver.pageSource.contains("Mainboard · 14")) ui.button("Inspection retained draft")
                }
                ui.button("Sideboard · 3"); ui.thumbnail("Coppercloud Echo!")
                ui.scroll("Power / Toughness: 2 / *", true); ui.named("Close card inspection").click()
                ui.text("Sideboard · 3"); ui.text("Coppercloud Echo!")
                ui.button("Mainboard · 14"); ui.thumbnail("Dayglass Loom // Nightglass Weave")
                ui.named("Artwork for Dayglass Loom"); ui.button("Next face"); ui.named("Artwork for Nightglass Weave")
                ui.driver.rotate(ScreenOrientation.LANDSCAPE)
                ui.text("2 / 2"); ui.scroll("Power / Toughness: 4 / 1", true); ui.capture("saved-back-landscape")
                ui.button("Enlarge artwork"); ui.button("Fit artwork")
                ui.adb("shell", "settings", "put", "system", "font_scale", "1.6")
                ui.text("2 / 2"); ui.named("Close card inspection"); ui.scroll("Power / Toughness: 4 / 1", true)
                ui.capture("saved-large-text-short-viewport")
                ui.driver.navigate().back(); ui.text("Mainboard · 14")
                ui.restoreScale(originalScale)
                ui.driver.rotate(ScreenOrientation.PORTRAIT); ui.restart(); ui.button("Inspection retained draft")
                ui.thumbnail("Coppercloud Surveyor"); ui.named("Artwork for Coppercloud Surveyor")
                ui.scroll("Original synthetic survey text, revision 1.", true); ui.capture("saved-after-restart")
                ui.named("Close card inspection").click(); ui.text("Mainboard · 14"); ui.text("Sideboard · 3")
                ui.record("inspection-passed.json", mapOf("retainedTail" to (System.getenv("ATW_INSPECTION_TAIL") == "true"),
                    "savedSectionsQuantitiesPreserved" to true, "exactBothFaces" to true, "largeText" to true,
                    "bothOrientationsCloseNativeBack" to true, "fullArtworkZoom" to true, "processRestart" to true))
            } finally { ui.restoreScale(originalScale) }
        }
    }
}
