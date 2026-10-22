package org.asyncttwiz.e2e

import com.google.gson.GsonBuilder
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.options.UiAutomator2Options
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.OutputType
import org.openqa.selenium.interactions.PointerInput
import org.openqa.selenium.interactions.Sequence
import org.openqa.selenium.support.ui.WebDriverWait
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Only normal native startup; not account, downloader or Canvas acceptance. */
@Tag("device")
class NativeStartupTest {
    @Test
    fun observesNormalNativeStartupThroughOwnedUiAutomatorSession() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        fun property(name: String) = requireNotNull(input.getProperty(name))
        val serial = property("serial")
        val systemPort = property("systemPort").toInt()
        val gson = GsonBuilder().setPrettyPrinting().create()
        fun record(name: String, value: Any) = Files.writeString(directory.resolve(name), gson.toJson(value) + "\n")
        val options = UiAutomator2Options()
            .setUdid(serial)
            .setDeviceName(serial)
            .setRemoteAdbHost("127.0.0.1")
            .setAdbPort(property("adbPort").toInt())
            .setSystemPort(systemPort)
            .setAppPackage("org.asyncttwiz.android")
            .setAppActivity("org.asyncttwiz.android.MainActivity")
            .setNoReset(true)
            .setFullReset(false)
            .setNewCommandTimeout(Duration.ofSeconds(90))
        options.setCapability("appium:autoLaunch", false)
        options.setCapability("appium:skipLogcatCapture", true)
        val driver = AndroidDriver(URI(property("appium")).toURL(), options)
        // Persist the exact ID before any further command, including evidence capture.
        val session = linkedMapOf<String, Any>("owner" to property("owner"), "serial" to serial,
            "sessionId" to driver.sessionId.toString(), "systemPort" to systemPort,
            "capabilities" to driver.capabilities.asMap())
        record("session.json", session)
        try {
            val command = listOf(property("linuxAdb"), "-H", "127.0.0.1", "-P", property("adbPort"), "forward", "--list")
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            check(process.waitFor(15, TimeUnit.SECONDS)) { "Bounded forward inventory timed out" }
            val mappings = process.inputStream.bufferedReader().readText().trim().lines().filter(String::isNotBlank)
            check(process.exitValue() == 0)
            check(mappings.map { it.split(Regex("\\s+")) } == listOf(listOf(serial, "tcp:$systemPort", "tcp:6790"))) {
                "Unexpected forward mapping: $mappings"
            }
            session["forward"] = listOf(serial, "tcp:$systemPort", "tcp:6790")
            record("session.json", session)
            assertEquals("org.asyncttwiz.android", driver.currentPackage)
            val wait = WebDriverWait(driver, Duration.ofSeconds(30))
            val title = wait.until {
                driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().text(\"Async Tabletop Wizards\")"))
                    .takeIf { it.isDisplayed }
            }
            wait.until {
                val source = driver.pageSource
                !source.contains("Opening local storage") && !source.contains("could not be opened") &&
                    !source.contains("needs a newer app")
            }
            Files.writeString(directory.resolve("startup.xml"), driver.pageSource)
            Files.write(directory.resolve("startup.png"), driver.getScreenshotAs(OutputType.BYTES))
            // The healthy scaffold has no action controls. A W3C pointer tap on its
            // real title qualifies input transport only, not a fabricated app action.
            val rect = title.rect
            val finger = PointerInput(PointerInput.Kind.TOUCH, "startup-finger")
            driver.perform(listOf(Sequence(finger, 0).apply {
                addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), rect.x + rect.width / 2, rect.y + rect.height / 2))
                addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()))
            }))
            assertTrue(title.isDisplayed)
            assertEquals("org.asyncttwiz.android", driver.currentPackage)
            record("observation.json", mapOf("serial" to serial, "sessionId" to driver.sessionId.toString(),
                "package" to driver.currentPackage, "title" to title.text, "pointerTarget" to "real noninteractive title",
                "scope" to "Native startup/discovery and pointer transport only; no action controls exist in the healthy scaffold"))
        } finally {
            driver.quit()
            record("session-closed.json", mapOf("sessionId" to session.getValue("sessionId")))
        }
    }
}
