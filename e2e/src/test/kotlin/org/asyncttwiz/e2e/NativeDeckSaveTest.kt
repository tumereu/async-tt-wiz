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
import org.openqa.selenium.support.ui.WebDriverWait
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Normal UI account/import/save/read. Host failures affect only the supplied owned environment. */
@Tag("device")
class NativeDeckSaveTest {
    @Test fun savesRetriesAndReopensRetainedDefinitions() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        fun property(name: String) = requireNotNull(input.getProperty(name))
        fun record(name: String, value: Any) = Files.writeString(directory.resolve(name), GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n")
        fun command(vararg args: String): String {
            val process = ProcessBuilder(args.toList()).redirectErrorStream(true).start()
            check(process.waitFor(150, TimeUnit.SECONDS)) { "Owned command timeout" }
            val output = process.inputStream.bufferedReader().readText()
            check(process.exitValue() == 0) { "Owned command failed: $output" }
            return output
        }
        fun adb(vararg args: String) = command(property("linuxAdb"), "-H", "127.0.0.1", "-P", property("adbPort"), "-s", property("serial"), *args)
        val options = UiAutomator2Options().setUdid(property("serial")).setDeviceName(property("serial"))
            .setRemoteAdbHost("127.0.0.1").setAdbPort(property("adbPort").toInt()).setSystemPort(property("systemPort").toInt())
            .setAppPackage("org.asyncttwiz.android").setAppActivity("org.asyncttwiz.android.MainActivity")
            .setNoReset(true).setFullReset(false).setNewCommandTimeout(Duration.ofSeconds(180))
        options.setCapability("appium:autoLaunch", false); options.setCapability("appium:skipLogcatCapture", true)
        val driver = AndroidDriver(URI(property("appium")).toURL(), options)
        record("session.json", mapOf("owner" to property("owner"), "serial" to property("serial"),
            "sessionId" to driver.sessionId.toString(), "systemPort" to property("systemPort").toInt(),
            "forward" to listOf(property("serial"), "tcp:${property("systemPort")}", "tcp:6790")))
        val wait = WebDriverWait(driver, Duration.ofSeconds(45)).ignoring(StaleElementReferenceException::class.java)
        fun text(value: String): WebElement = wait.until {
            driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).takeIf { it.isDisplayed }
        }
        fun named(value: String) = wait.until { driver.findElement(AppiumBy.accessibilityId(value)).takeIf { it.isDisplayed } }
        fun scroll(value: String): WebElement = wait.until {
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).lastOrNull { it.isDisplayed }
                ?: driver.findElement(AppiumBy.androidUIAutomator(
                    "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().text(\"$value\"))"))
        }
        fun button(value: String) {
            val visible = driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).lastOrNull { it.isDisplayed }
            (visible ?: scroll(value)).click()
        }
        fun authField(value: String): WebElement = wait.until {
            driver.findElement(AppiumBy.xpath("//android.widget.EditText[.//*[@content-desc='$value']]"))
        }
        fun listField() = driver.findElement(AppiumBy.className("android.widget.EditText"))
        fun hide() { if (driver.isKeyboardShown) driver.hideKeyboard() }
        fun review() {
            wait.until { driver.findElement(AppiumBy.xpath(
                "//android.view.View[@clickable='true'][./android.widget.TextView[@text='Review import']]" )).isEnabled }
            button("Review import")
        }
        fun capture(name: String) {
            Files.writeString(directory.resolve("$name.xml"), driver.pageSource)
            Files.write(directory.resolve("$name.png"), driver.getScreenshotAs(OutputType.BYTES))
        }
        fun restart() {
            adb("shell", "am", "force-stop", "org.asyncttwiz.android")
            adb("shell", "am", "start", "-n", "org.asyncttwiz.android/.MainActivity")
        }
        fun signIn(email: String) {
            scroll("Email"); authField("Email").clear(); authField("Email").sendKeys(email)
            scroll("Password"); authField("Password").sendKeys("synthetic import password"); hide()
            scroll("Forgot password?"); driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Sign in\")")).last().click()
        }
        fun register(email: String, username: String) {
            button("Create account"); scroll("Username"); authField("Username").sendKeys(username)
            scroll("Email"); authField("Email").sendKeys(email)
            scroll("Password"); authField("Password").sendKeys("synthetic import password"); hide()
            scroll("Sign in"); driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Create account\")")).last().click()
            scroll("Account created. Sign in."); signIn(email)
        }
        val corrected = "Deck\n4 Coppercloud Surveyor (ZPA) 1\n2 Velvet Prism Engine\n1 Amberglass Chorus\n3 Quiet Lattice Bolt\n1 Folded Lantern Road\n1 Mauve Window Expanse\n1 Threadglass Envoy\n1 Prismgate Bulwark\n1 Feltwing Atlas\n1 Coppercloud Echo (ZPA) 10\n1 Coppercloud Echo!\n1 Dayglass Loom // Nightglass Weave (ZPA) 12\nSideboard\n2 Velvet Prism Engine"
        var stopped = false
        try {
            driver.rotate(ScreenOrientation.PORTRAIT)
            text("Async Tabletop Wizards"); register("save-a@example.test", "Save A")
            text("Manage decks").click(); text("No saved decks yet."); button("Download card records"); text("Card records are up to date.")
            button("Import deck"); listField().sendKeys("1 Uncatalogued Prism Object"); hide(); button("Retry lookup")
            wait.until { driver.pageSource.contains("could not be resolved") }
            listField().clear(); listField().sendKeys(corrected); hide(); button("Retry lookup")
            review(); text("Mainboard · 18")
            // Landscape has independently scrollable preview and save controls, avoiding long-list keyboard traps.
            driver.rotate(ScreenOrientation.LANDSCAPE); text("Deck name"); listField().sendKeys("Saved prism journey"); hide()
            capture("named-preview-landscape")
            record("backend-stop.json", mapOf("container" to property("backendContainer"),
                "output" to command("docker", "stop", "--time", "30", property("backendContainer"))))
            stopped = true
            button("Save deck")
            text("Could not reach the deck service. Your input is kept. Check your connection and retry.")
            assertTrue(listField().text.contains("Saved prism journey")); capture("save-failure-landscape")
            driver.rotate(ScreenOrientation.PORTRAIT)
            scroll("Deck name"); assertTrue(listField().text.contains("Saved prism journey")); capture("save-failure-portrait")
            driver.rotate(ScreenOrientation.LANDSCAPE)
            record("backend-restart.json", mapOf("output" to command("python3", property("root") + "/scripts/e2e-environment.py", "--directory", property("environment"), "restart", "--service", "backend")))
            stopped = false
            button("Save deck"); text("Saved prism journey"); text("Mainboard · 18"); capture("saved-detail-landscape")
            driver.rotate(ScreenOrientation.PORTRAIT); text("Saved prism journey"); capture("saved-detail-portrait")
            driver.navigate().back(); text("Saved prism journey"); text("Refresh saved decks")
            assertEquals(1, driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Saved prism journey\")")).size)
            // Exit/Edit never create a second saved deck.
            button("Import deck"); listField().sendKeys("2 Velvet Prism Engine"); hide(); button("Retry lookup"); review()
            scroll("Edit pasted list").click(); assertTrue(listField().text.contains("Velvet Prism Engine"))
            named("Exit without saving").click(); text("Saved prism journey")
            // Evict only the disposable public catalog pointer while the app is stopped.
            // Essential definitions must be read from the accepted server deck, not resolved again.
            adb("shell", "am", "force-stop", "org.asyncttwiz.android")
            record("public-catalog-evicted.json", mapOf("output" to adb("shell", "run-as", "org.asyncttwiz.android", "rm", "no_backup/public-catalog/current")))
            restart(); text("Saved prism journey"); button("Saved prism journey"); text("Mainboard · 18")
            scroll("Sideboard · 2"); text("Velvet Prism Engine"); capture("reopened-server-definitions")
            driver.rotate(ScreenOrientation.LANDSCAPE); text("Logout").click(); text("Async Tabletop Wizards")
            driver.rotate(ScreenOrientation.PORTRAIT)
            register("save-b@example.test", "Save B"); text("Manage decks").click()
            text("No saved decks yet."); assertFalse(driver.pageSource.contains("Saved prism journey")); capture("second-account-private")
            named("Open navigation").click(); text("Logout").click(); text("Async Tabletop Wizards"); signIn("save-a@example.test")
            text("Manage decks").click()
            text("Saved prism journey"); capture("original-account-retained")
            record("save-observation.json", mapOf("journeys" to listOf("UI-created A/B", "real catalog correction",
                "long preview/name and both orientations", "stopped real backend failure retains draft/name/preview",
                "retained backend restart and immutable retry", "actual atomic save/detail", "one server list row",
                "Edit/Exit without save", "process restart server definitions/mainboard/sideboard", "Logout/account-private list"),
                "remaining" to "I07 management/full inspector/sync; I08 Overwrite/Merge; final UI/device/full-suite gates"))
        } finally {
            try {
                if (stopped) command("python3", property("root") + "/scripts/e2e-environment.py", "--directory", property("environment"), "restart", "--service", "backend")
            } finally {
                runCatching { capture("save-final") }; driver.quit(); record("session-closed.json", mapOf("closed" to true))
            }
        }
    }
}
