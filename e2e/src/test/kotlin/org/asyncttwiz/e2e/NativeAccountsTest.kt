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
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Every account is created and entered through normal native UI. No account/action injection. */
@Tag("device")
class NativeAccountsTest {
    @Test fun registersSignsInRestartsAndHandlesRevocationInBothOrientations() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        fun property(name: String) = requireNotNull(input.getProperty(name))
        val serial = property("serial")
        val systemPort = property("systemPort").toInt()
        val gson = GsonBuilder().setPrettyPrinting().create()
        fun record(name: String, value: Any) = Files.writeString(directory.resolve(name), gson.toJson(value) + "\n")
        fun adb(vararg args: String): String {
            val command = listOf(property("linuxAdb"), "-H", "127.0.0.1", "-P", property("adbPort"), "-s", serial) + args
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            check(process.waitFor(30, TimeUnit.SECONDS)) { "Owned ADB command timed out" }
            val output = process.inputStream.bufferedReader().readText()
            check(process.exitValue() == 0) { "Owned ADB command failed" }
            return output
        }
        val options = UiAutomator2Options().setUdid(serial).setDeviceName(serial)
            .setRemoteAdbHost("127.0.0.1").setAdbPort(property("adbPort").toInt()).setSystemPort(systemPort)
            .setAppPackage("org.asyncttwiz.android").setAppActivity("org.asyncttwiz.android.MainActivity")
            .setNoReset(true).setFullReset(false).setNewCommandTimeout(Duration.ofSeconds(120))
        options.setCapability("appium:autoLaunch", false)
        options.setCapability("appium:skipLogcatCapture", true)
        val driver = AndroidDriver(URI(property("appium")).toURL(), options)
        val session = linkedMapOf<String, Any>("owner" to property("owner"), "serial" to serial,
            "sessionId" to driver.sessionId.toString(), "systemPort" to systemPort, "capabilities" to driver.capabilities.asMap())
        record("session.json", session)
        val wait = WebDriverWait(driver, Duration.ofSeconds(35))
        fun text(value: String): WebElement = wait.until {
            driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).takeIf { it.isDisplayed }
        }
        fun field(value: String): WebElement = wait.until {
            driver.findElement(if (value in listOf("Username", "Email", "Password"))
                AppiumBy.xpath("//android.widget.EditText[.//*[@content-desc='$value']]") else AppiumBy.accessibilityId(value))
                .takeIf { it.isDisplayed }
        }
        fun scroll(value: String): WebElement {
            return driver.findElement(AppiumBy.androidUIAutomator(
                "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().text(\"$value\"))"))
        }
        fun button(value: String) {
            // Text is a child of its native Compose button; text click routes to that same action.
            scroll(if (value == "Sign in") "Forgot password?" else value)
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).last().click()
        }
        fun capture(name: String) {
            Files.writeString(directory.resolve("$name.xml"), driver.pageSource)
            Files.write(directory.resolve("$name.png"), driver.getScreenshotAs(OutputType.BYTES))
        }
        fun hideKeyboard() { if (driver.isKeyboardShown) driver.hideKeyboard() }
        try {
            val forwards = adb("forward", "--list").trim().lines().filter(String::isNotBlank).map { it.split(Regex("\\s+")) }
            assertEquals(listOf(listOf(serial, "tcp:$systemPort", "tcp:6790")), forwards)
            session["forward"] = forwards.single(); record("session.json", session)
            driver.rotate(ScreenOrientation.PORTRAIT)
            text("Async Tabletop Wizards")
            capture("a01-portrait")
            driver.rotate(ScreenOrientation.LANDSCAPE)
            text("Async Tabletop Wizards")
            capture("a01-landscape")
            button("Create account")
            text("Create your player account")
            capture("a02-landscape")
            scroll("Username")
            field("Username").sendKeys("Native Wizard")
            field("Email").sendKeys("not-an-email")
            scroll("Password")
            field("Password").sendKeys("wizard password")
            hideKeyboard()
            // Registration's submit has the same label as its heading. The last matching
            // text after scrolling is the full-size submit, not the noninteractive heading.
            scroll("Sign in")
            capture("a02b-landscape")
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Create account\")")).last().click()
            text("Enter a valid email with an ASCII mailbox name, such as player@example.com.")
            hideKeyboard()
            scroll("Email")
            val email = field("Email"); email.clear(); email.sendKeys("native@example.test")
            hideKeyboard()
            driver.rotate(ScreenOrientation.PORTRAIT)
            scroll("Username")
            capture("a02-portrait")
            // Show/hide and native clipboard paste, without typing normalization.
            scroll("Password")
            field("Password").click()
            driver.setClipboardText("wizard password")
            field("Password").clear()
            driver.pressKey(io.appium.java_client.android.nativekey.KeyEvent(io.appium.java_client.android.nativekey.AndroidKey.V)
                .withMetaModifier(io.appium.java_client.android.nativekey.KeyEventMetaModifier.CTRL_ON))
            hideKeyboard()
            field("Show password").click()
            assertEquals("wizard password", field("Password").text)
            capture("password-clipboard-visible-synthetic")
            field("Hide password").click()
            scroll("Sign in")
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Create account\")")).last().click()
            text("Account created. Sign in.")
            scroll("Email")
            assertTrue(field("Email").text.contains("native@example.test"))
            scroll("Password")
            field("Password").sendKeys("wrong password")
            hideKeyboard(); button("Sign in")
            text("Email or password not recognised.")
            capture("a01-credentials-error")
            scroll("Password")
            field("Password").clear(); field("Password").sendKeys("wizard password")
            hideKeyboard()
            button("Sign in")
            field("Signed in as Native Wizard")
            capture("authenticated")
            adb("shell", "am", "force-stop", "org.asyncttwiz.android")
            adb("shell", "am", "start", "-n", "org.asyncttwiz.android/.MainActivity")
            field("Signed in as Native Wizard")
            capture("protected-token-relaunch")
            // Revoke the ordinary session by the public endpoint. The token is recovered
            // read-only from the running app's protected store by instrumentation below,
            // not decrypted by this host. A second login's token cannot revoke this one.
            // Device-side instrumentation reads/revokes only this synthetic app token.
            val revoke = adb("shell", "am", "instrument", "-w", "-r", "-e", "requireSavedSession", "true", "-e", "class",
                "org.asyncttwiz.android.RevokeSavedSessionTest", "org.asyncttwiz.android.test/androidx.test.runner.AndroidJUnitRunner")
            assertTrue(revoke.contains("OK (1 test)"))
            adb("shell", "am", "force-stop", "org.asyncttwiz.android")
            adb("shell", "am", "start", "-n", "org.asyncttwiz.android/.MainActivity")
            text("Your session is no longer valid. Please sign in again.")
            capture("revoked-session")
            button("Create account")
            text("Username")
            driver.navigate().back()
            text("Async Tabletop Wizards")
            record("accounts-observation.json", mapOf("tests" to listOf("invalid/corrected registration", "email/password login",
                "visibility/clipboard paste", "both orientations and lower form", "native Back", "protected-token force-stop/relaunch",
                "ordinary public revocation requires login"), "scope" to "I02b only; no shell/reset/game journey"))
        } finally {
            runCatching { capture("final") }
            driver.quit()
            record("session-closed.json", mapOf("sessionId" to session.getValue("sessionId")))
        }
    }
}
