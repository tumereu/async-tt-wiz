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
import org.openqa.selenium.interactions.PointerInput
import org.openqa.selenium.interactions.Sequence
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.WebDriverWait
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties

/** Normal launcher, UI-created account, hosted catalog and native input; no action injection. */
@Tag("device")
class NativeImportTest {
    @Test fun correctsReviewsRotatesEditsAndExitsWithoutSaving() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        fun property(name: String) = requireNotNull(input.getProperty(name))
        val options = UiAutomator2Options().setUdid(property("serial")).setDeviceName(property("serial"))
            .setRemoteAdbHost("127.0.0.1").setAdbPort(property("adbPort").toInt()).setSystemPort(property("systemPort").toInt())
            .setAppPackage("org.asyncttwiz.android").setAppActivity("org.asyncttwiz.android.MainActivity")
            .setNoReset(true).setFullReset(false).setNewCommandTimeout(Duration.ofSeconds(120))
        options.setCapability("appium:autoLaunch", false); options.setCapability("appium:skipLogcatCapture", true)
        val driver = AndroidDriver(URI(property("appium")).toURL(), options)
        fun record(name: String, value: Any) = Files.writeString(directory.resolve(name), GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n")
        record("session.json", mapOf("owner" to property("owner"), "serial" to property("serial"),
            "sessionId" to driver.sessionId.toString(), "systemPort" to property("systemPort").toInt()))
        val wait = WebDriverWait(driver, Duration.ofSeconds(35)).ignoring(StaleElementReferenceException::class.java)
        fun text(value: String): WebElement = wait.until {
            driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).takeIf { it.isDisplayed }
        }
        fun named(value: String) = wait.until { driver.findElement(AppiumBy.accessibilityId(value)).takeIf { it.isDisplayed } }
        fun scroll(value: String) = driver.findElement(AppiumBy.androidUIAutomator(
            "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().text(\"$value\"))"))
        fun button(value: String) {
            val visible = driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).lastOrNull { it.isDisplayed }
            (visible ?: scroll(value)).click()
        }
        fun authField(value: String) = driver.findElement(AppiumBy.xpath("//android.widget.EditText[.//*[@content-desc='$value']]"))
        fun listField() = driver.findElement(AppiumBy.className("android.widget.EditText"))
        fun enabled(value: String) = driver.findElement(AppiumBy.xpath(
            "//android.view.View[@clickable='true'][./android.widget.TextView[@text='$value']]")).isEnabled
        fun hide() { if (driver.isKeyboardShown) driver.hideKeyboard() }
        fun previewBottom() {
            val size = driver.manage().window().size
            repeat(10) {
                if (driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Edit pasted list\")")).any { it.isDisplayed }) return
                val finger = PointerInput(PointerInput.Kind.TOUCH, "import-scroll")
                driver.perform(listOf(Sequence(finger, 0).apply {
                    addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), size.width * 4 / 5, size.height * 4 / 5))
                    addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                    addAction(finger.createPointerMove(Duration.ofMillis(450), PointerInput.Origin.viewport(), size.width * 4 / 5, size.height / 4))
                    addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()))
                }))
            }
            text("Edit pasted list")
        }
        fun capture(name: String) {
            Files.writeString(directory.resolve("$name.xml"), driver.pageSource)
            Files.write(directory.resolve("$name.png"), driver.getScreenshotAs(OutputType.BYTES))
        }
        val invalid = "Deck\n4 Coppercloud Surveyor (ZPA) 1\n1 Uncatalogued Prism Object\nSideboard\n2 Velvet Prism Engine"
        val corrected = "Deck\n4 Coppercloud Surveyor (ZPA) 1\n2 Velvet Prism Engine\n1 Amberglass Chorus\n3 Quiet Lattice Bolt\n1 Folded Lantern Road\n1 Mauve Window Expanse\n1 Threadglass Envoy\n1 Prismgate Bulwark\n1 Feltwing Atlas\n1 Coppercloud Echo (ZPA) 10\n1 Coppercloud Echo!\n1 Dayglass Loom // Nightglass Weave (ZPA) 12\nSideboard\n2 Velvet Prism Engine"
        try {
            driver.rotate(ScreenOrientation.PORTRAIT)
            text("Async Tabletop Wizards"); button("Create account")
            scroll("Username"); authField("Username").sendKeys("Import Wizard")
            scroll("Email"); authField("Email").sendKeys("import@example.test")
            scroll("Password"); authField("Password").sendKeys("synthetic import password"); hide()
            scroll("Sign in"); driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Create account\")")).last().click()
            text("Account created. Sign in.")
            scroll("Password"); authField("Password").sendKeys("synthetic import password"); hide()
            scroll("Forgot password?"); driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Sign in\")")).last().click()
            text("Manage decks").click(); button("Download card records"); text("Card records are up to date.")
            button("Import deck"); text("Import deck")
            listField().sendKeys(invalid); hide(); button("Retry lookup")
            wait.until { driver.pageSource.contains("Line 3: Uncatalogued Prism Object could not be resolved") }
            assertFalse(enabled("Review import"))
            capture("d03-error-portrait")
            driver.rotate(ScreenOrientation.LANDSCAPE)
            text("Import deck"); assertTrue(listField().text.contains("Uncatalogued Prism Object"))
            capture("d03-error-landscape")
            listField().clear(); listField().sendKeys(corrected); hide(); button("Retry lookup")
            wait.until { enabled("Review import") }; button("Review import")
            text("Mainboard · 18"); text("Deck name")
            listField().sendKeys("Personal import draft"); hide()
            capture("d04-preview-landscape")
            driver.rotate(ScreenOrientation.PORTRAIT)
            text("Mainboard · 18"); capture("d04-preview-portrait")
            previewBottom()
            assertTrue(listField().text.contains("Personal import draft"))
            assertFalse(enabled("Save deck"))
            capture("d04b-controls-portrait")
            button("Edit pasted list")
            // Return to the input top after the preview's separate scroll position.
            assertTrue(listField().text.contains("Dayglass Loom // Nightglass Weave"))
            driver.rotate(ScreenOrientation.LANDSCAPE)
            assertTrue(listField().text.contains("Quiet Lattice Bolt"))
            button("Review import"); text("Deck name"); capture("d04b-controls-landscape")
            driver.navigate().back(); text("Deck list")
            named("Exit without saving").click(); text("Card records")
            button("Import deck"); assertFalse(listField().text.contains("Coppercloud"))
            driver.navigate().back(); text("Card records")
            button("Import deck"); listField().sendKeys("1 Private account draft"); hide()
            text("Logout").click(); text("Async Tabletop Wizards")
            driver.rotate(ScreenOrientation.PORTRAIT)
            scroll("Email"); authField("Email").clear(); authField("Email").sendKeys("import@example.test")
            scroll("Password"); authField("Password").sendKeys("synthetic import password"); hide()
            scroll("Forgot password?"); driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Sign in\")")).last().click()
            text("Manage decks").click(); button("Import deck")
            assertFalse(listField().text.contains("Private account draft"))
            capture("logout-clears-import")
            record("import-observation.json", mapOf("scope" to "I06a input/errors/correction/preview only; save disabled until I06b",
                "journeys" to listOf("UI-created account", "real hosted catalog", "blocking exact line error", "correction and retry",
                    "section quantities and read-only art/text", "rotation retains text/name/preview", "long-preview reachable Edit/disabled Save",
                    "native Back preview-to-input and input-to-decks", "Exit discards", "Logout clears private draft")))
        } finally {
            runCatching { capture("import-final") }; driver.quit(); record("session-closed.json", mapOf("closed" to true))
        }
    }
}
