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
import org.openqa.selenium.WebElement
import org.openqa.selenium.support.ui.WebDriverWait
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Normal UI actions only. Host controls change provider availability/edition, not product state. */
@Tag("device")
class NativeCatalogTest {
    @Test fun consentsRetriesDownloadsUpdatesAndRetainsCatalogAcrossNativeNavigationAndRestart() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        fun property(name: String) = requireNotNull(input.getProperty(name))
        val gson = GsonBuilder().setPrettyPrinting().create()
        fun record(name: String, value: Any) = Files.writeString(directory.resolve(name), gson.toJson(value) + "\n")
        fun adb(vararg args: String): String {
            val command = listOf(property("linuxAdb"), "-H", "127.0.0.1", "-P", property("adbPort"), "-s", property("serial")) + args
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            check(process.waitFor(30, TimeUnit.SECONDS))
            return process.inputStream.bufferedReader().readText().also { check(process.exitValue() == 0) }
        }
        fun source(fault: String, version: Int) {
            val process = ProcessBuilder("python3", Path.of(property("repository"), "tests/catalog/control_source.py").toString(),
                property("environment"), fault, "$version")
                .redirectErrorStream(true).start()
            check(process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            Files.writeString(directory.resolve("source-$fault-$version.txt"), output)
            check(process.exitValue() == 0) { "Synthetic source control failed: $output" }
        }
        val options = UiAutomator2Options().setUdid(property("serial")).setDeviceName(property("serial"))
            .setRemoteAdbHost("127.0.0.1").setAdbPort(property("adbPort").toInt()).setSystemPort(property("systemPort").toInt())
            .setAppPackage("org.asyncttwiz.android").setAppActivity("org.asyncttwiz.android.MainActivity")
            .setNoReset(true).setFullReset(false).setNewCommandTimeout(Duration.ofSeconds(120))
        options.setCapability("appium:autoLaunch", false)
        options.setCapability("appium:skipLogcatCapture", true)
        val driver = AndroidDriver(URI(property("appium")).toURL(), options)
        record("session.json", mapOf("owner" to property("owner"), "serial" to property("serial"),
            "sessionId" to driver.sessionId.toString(), "systemPort" to property("systemPort").toInt()))
        val wait = WebDriverWait(driver, Duration.ofSeconds(45))
        fun text(value: String): WebElement = wait.until {
            driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).takeIf { it.isDisplayed }
        }
        fun prefix(value: String): WebElement = wait.until {
            driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().textStartsWith(\"$value\")")).takeIf { it.isDisplayed }
        }
        fun scroll(value: String) = driver.findElement(AppiumBy.androidUIAutomator(
            "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().text(\"$value\"))"))
        fun button(value: String) { scroll(value); driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).last().click() }
        fun field(value: String) = driver.findElement(AppiumBy.xpath("//android.widget.EditText[.//*[@content-desc='$value']]"))
        fun capture(name: String) {
            Files.writeString(directory.resolve("$name.xml"), driver.pageSource)
            Files.write(directory.resolve("$name.png"), driver.getScreenshotAs(OutputType.BYTES))
        }
        fun hide() { if (driver.isKeyboardShown) driver.hideKeyboard() }
        try {
            if (input.getProperty("retained") != "true") {
            driver.rotate(ScreenOrientation.PORTRAIT)
            text("Async Tabletop Wizards")
            button("Create account")
            scroll("Username"); field("Username").sendKeys("Catalog Wizard")
            scroll("Email"); field("Email").sendKeys("catalog@example.test")
            scroll("Password"); field("Password").sendKeys("synthetic catalog password"); hide()
            scroll("Sign in")
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Create account\")")).last().click()
            text("Account created. Sign in.")
            scroll("Password"); field("Password").sendKeys("synthetic catalog password"); hide()
            scroll("Forgot password?")
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Sign in\")")).last().click()
            text("Manage decks").click()
            text("Card records")
            text("Download card records")
            assertTrue(prefix("2026-10-11 · Download").text.contains("KiB"))
            assertTrue(driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().textStartsWith(\"Installed:\")")).isEmpty())
            capture("catalog-consent-portrait")
            driver.rotate(ScreenOrientation.LANDSCAPE)
            text("Card records")
            capture("catalog-consent-landscape")
            }
            if (input.getProperty("checkpoint") != "tail") {
            // Meta/HEAD have completed but no bulk records installed before this explicit action.
            source("corrupt-gzip", 1)
            button("Download card records")
            prefix("Download failed.")
            capture("catalog-failed-download-landscape")
            source("ok", 1)
            button("Retry download")
            text("Card records are up to date.")
            prefix("Installed: 2026-10-11")
            capture("catalog-installed-landscape")
            driver.rotate(ScreenOrientation.PORTRAIT)
            prefix("Installed: 2026-10-11")
            capture("catalog-installed-portrait")
            driver.navigate().back()
            }
            text("Games")
            text("Manage decks").click()
            prefix("Installed: 2026-10-11")
            adb("shell", "am", "force-stop", "org.asyncttwiz.android")
            adb("shell", "am", "start", "-n", "org.asyncttwiz.android/.MainActivity")
            prefix("Installed: 2026-10-11")
            capture("catalog-retained-restart")
            source("corrupt-gzip", 2)
            button("Check for updates")
            // Unmetered refresh is automatic; metered refresh still needs the normal consent button.
            wait.until { driver.pageSource.contains("Card records could not be updated.") ||
                driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Download card records\")")).isNotEmpty() }
            if (!driver.pageSource.contains("Card records could not be updated.")) button("Download card records")
            wait.until { driver.pageSource.contains("could not be updated") || driver.pageSource.contains("Download failed.") }
            prefix("Installed: 2026-10-11")
            capture("catalog-update-retains-old")
            source("ok", 2)
            if (driver.pageSource.contains("Retry download")) button("Retry download") else button("Retry")
            wait.until { driver.pageSource.contains("Installed: 2026-10-12") || driver.pageSource.contains("Download card records") }
            if (!driver.pageSource.contains("Installed: 2026-10-12")) button("Download card records")
            prefix("Installed: 2026-10-12")
            text("Card records are up to date.")
            capture("catalog-updated-portrait")
            driver.rotate(ScreenOrientation.LANDSCAPE)
            wait.until { driver.manage().window().size.width > driver.manage().window().size.height }
            prefix("Installed: 2026-10-12")
            capture("catalog-updated-landscape")
            record("catalog-observation.json", mapOf("journeys" to listOf("UI-created account", "initial size/network consent without eager bulk install",
                "both orientations", "checksum failure and explicit retry", "real disk-backed installed catalog", "native Back/navigation",
                "force-stop retained catalog", "changed-source update failure retains v1", "retry installs v2"),
                "source" to property("source"), "scope" to "I05a records only; no art/import/deck/game acceptance"))
        } finally {
            runCatching { capture("catalog-final") }
            driver.quit()
            record("session-closed.json", mapOf("closed" to true))
        }
    }
}
