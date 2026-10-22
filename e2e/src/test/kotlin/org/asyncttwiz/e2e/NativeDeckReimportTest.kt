package org.asyncttwiz.e2e

import com.google.gson.GsonBuilder
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
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

/** All imports/edits through the normal UI. Only the owned backend is stopped for transport failure. */
@Tag("device")
class NativeDeckReimportTest {
    @Test fun contextualOverwriteAndMergeRetainTargetDraftAndAcceptedSections() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        fun property(name: String) = requireNotNull(input.getProperty(name))
        fun record(name: String, value: Any) = Files.writeString(directory.resolve(name), GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n")
        fun command(vararg args: String): String {
            val process = ProcessBuilder(args.toList()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(150, TimeUnit.SECONDS) && process.exitValue() == 0) { output }
            return output
        }
        fun adb(vararg args: String) = command(property("linuxAdb"), "-H", "127.0.0.1", "-P", property("adbPort"), "-s", property("serial"), *args)
        val options = UiAutomator2Options().setUdid(property("serial")).setDeviceName(property("serial"))
            .setRemoteAdbHost("127.0.0.1").setAdbPort(property("adbPort").toInt()).setSystemPort(property("systemPort").toInt())
            .setAppPackage("org.asyncttwiz.android").setAppActivity("org.asyncttwiz.android.MainActivity")
            .setNoReset(true).setFullReset(false).setNewCommandTimeout(Duration.ofSeconds(300)).also {
                it.setCapability("appium:autoLaunch", false); it.setCapability("appium:skipLogcatCapture", true)
            }
        val driver = AndroidDriver(URI(property("appium")).toURL(), options)
        record("session.json", mapOf("owner" to property("owner"), "serial" to property("serial"),
            "sessionId" to driver.sessionId.toString(), "systemPort" to property("systemPort").toInt(),
            "forward" to listOf(property("serial"), "tcp:${property("systemPort")}", "tcp:6790")))
        val wait = WebDriverWait(driver, Duration.ofSeconds(45)).ignoring(StaleElementReferenceException::class.java)
        fun text(value: String): WebElement = wait.until {
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).lastOrNull { it.isDisplayed }
        }
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
        fun paste(value: String) { field().click(); field().clear(); field().sendKeys(value); hide(); button("Retry lookup") }
        fun capture(name: String) {
            Files.writeString(directory.resolve("$name.xml"), driver.pageSource)
            Files.write(directory.resolve("$name.png"), driver.getScreenshotAs(OutputType.BYTES))
        }
        fun selected(mode: String) {
            scroll(mode)
            wait.until { driver.findElement(By.xpath("//*[@selected='true' or @checked='true'][.//*[@text='$mode']]")) }
        }
        fun contextual() { button("Edit"); button("Import decklist"); text("Import · Context target"); selected("Overwrite") }
        var stopped = false
        try {
            driver.rotate(ScreenOrientation.PORTRAIT)
            text("Async Tabletop Wizards"); button("Create account")
            scroll("Username"); authField("Username").sendKeys("Reimport wizard")
            scroll("Email"); authField("Email").sendKeys("reimport@example.test")
            scroll("Password"); authField("Password").sendKeys("synthetic reimport password"); hide()
            scroll("Sign in"); text("Create account").click(); text("Account created. Sign in.")
            scroll("Email"); authField("Email").sendKeys("reimport@example.test")
            scroll("Password"); authField("Password").sendKeys("synthetic reimport password"); hide()
            scroll("Forgot password?"); text("Sign in").click(); text("Manage decks").click()
            button("Download card records"); text("Card records are up to date.")
            button("Import deck"); paste("Deck\n4 Coppercloud Surveyor (ZPA) 1\nSideboard\n3 Velvet Prism Engine")
            button("Review import"); scroll("Deck name"); field().sendKeys("Context target"); hide(); button("Save deck")
            text("Context target"); text("Mainboard · 4"); text("Sideboard · 3")
            contextual(); capture("overwrite-default-portrait")
            paste("1 Uncatalogued Prism Object"); wait.until { driver.pageSource.contains("could not be resolved") }
            paste("2 Coppercloud Surveyor (ZPA) 1"); button("Merge"); selected("Merge")
            driver.rotate(ScreenOrientation.LANDSCAPE); selected("Merge"); assertTrue(field().text.contains("2 Coppercloud"))
            capture("merge-landscape"); button("Review import"); text("Mainboard · 2")
            assertTrue(driver.findElements(AppiumBy.className("android.widget.EditText")).isEmpty()) // no rename/target chooser
            record("backend-stop.json", mapOf("output" to command("docker", "stop", "--time", "30", property("backendContainer"))))
            stopped = true; button("Save deck")
            text("Could not reach the deck service. Your input is kept. Check your connection and retry.")
            selected("Merge"); capture("merge-failure-landscape")
            driver.rotate(ScreenOrientation.PORTRAIT); selected("Merge"); scroll("Save deck"); capture("merge-failure-portrait")
            record("backend-restart.json", mapOf("output" to command("python3", property("root") + "/scripts/e2e-environment.py", "--directory", property("environment"), "restart", "--service", "backend")))
            stopped = false; button("Save deck"); text("Context target"); text("Mainboard · 6"); text("Sideboard · 3")
            capture("merged-accepted-portrait")
            contextual(); driver.navigate().back(); text("Mainboard · 6"); text("Sideboard · 3")
            contextual(); paste("2 Coppercloud Surveyor (ZPA) 1"); button("Review import")
            driver.rotate(ScreenOrientation.LANDSCAPE); selected("Overwrite"); capture("overwrite-preview-landscape")
            button("Save deck"); text("Context target"); text("Mainboard · 2"); text("Sideboard · 0")
            capture("overwritten-accepted-landscape")
            adb("shell", "am", "force-stop", "org.asyncttwiz.android")
            adb("shell", "am", "start", "-n", "org.asyncttwiz.android/.MainActivity")
            button("Context target"); text("Mainboard · 2"); text("Sideboard · 0")
            driver.rotate(ScreenOrientation.PORTRAIT); driver.navigate().back(); button("Import deck")
            assertFalse(driver.pageSource.contains("Overwrite")); assertFalse(driver.pageSource.contains("Merge"))
            paste("1 Amberglass Chorus"); button("Review import"); scroll("Deck name"); field().sendKeys("Separate new deck"); hide()
            button("Save deck"); text("Separate new deck"); text("Mainboard · 1")
            driver.navigate().back(); text("Separate new deck"); button("Context target"); text("Mainboard · 2"); text("Sideboard · 0")
            record("reimport-observation.json", mapOf("journeys" to listOf("normal UI registration/catalog/new save",
                "contextual captured target/default Overwrite", "invalid correction preserves good deck", "additive main-only Merge keeps sideboard",
                "both orientations/rotation preserve mode/text", "backend transport failure/draft/restart/retry",
                "new invocation resets mode", "native Back cancels without save", "Overwrite empties absent sideboard",
                "process restart accepted definition read", "bottom import creates separate entry without mode/target picker"),
                "remaining" to "I10/I12 selected-copy isolation; I34-I36 final full suite/compatibility/UI; independent B2 review"))
        } finally {
            try {
                if (stopped) command("python3", property("root") + "/scripts/e2e-environment.py", "--directory", property("environment"), "restart", "--service", "backend")
            } finally {
                runCatching { capture("reimport-final") }; driver.quit(); record("session-closed.json", mapOf("closed" to true))
            }
        }
    }
}
