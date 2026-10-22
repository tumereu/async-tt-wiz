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

/** Real normal UI accounts; navigation never injects decks, contacts, games or login state. */
@Tag("device")
class NativeShellTest {
    @Test fun navigatesRestoresContextAndLogsOutBeforeChangingAccounts() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        fun property(name: String) = requireNotNull(input.getProperty(name))
        val serial = property("serial")
        val systemPort = property("systemPort").toInt()
        val gson = GsonBuilder().setPrettyPrinting().create()
        fun record(name: String, value: Any) = Files.writeString(directory.resolve(name), gson.toJson(value) + "\n")
        fun adb(vararg args: String): String {
            val process = ProcessBuilder(listOf(property("linuxAdb"), "-H", "127.0.0.1", "-P", property("adbPort"), "-s", serial) + args)
                .redirectErrorStream(true).start()
            check(process.waitFor(45, TimeUnit.SECONDS)) { "Owned ADB command timed out" }
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
        fun named(value: String): WebElement = wait.until {
            driver.findElement(AppiumBy.accessibilityId(value)).takeIf { it.isDisplayed }
        }
        fun field(value: String): WebElement = wait.until {
            driver.findElement(AppiumBy.xpath("//android.widget.EditText[.//*[@content-desc='$value']]")).takeIf { it.isDisplayed }
        }
        fun scroll(value: String) = driver.findElement(AppiumBy.androidUIAutomator(
            "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().text(\"$value\"))"))
        fun button(value: String) {
            scroll(if (value == "Sign in") "Forgot password?" else value)
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).last().click()
        }
        fun hideKeyboard() { if (driver.isKeyboardShown) driver.hideKeyboard() }
        fun capture(name: String) {
            Files.writeString(directory.resolve("$name.xml"), driver.pageSource)
            Files.write(directory.resolve("$name.png"), driver.getScreenshotAs(OutputType.BYTES))
        }
        fun signIn(email: String, username: String) {
            scroll("Email"); field("Email").clear(); field("Email").sendKeys(email)
            scroll("Password"); field("Password").sendKeys("wizard password")
            hideKeyboard(); button("Sign in"); named("Signed in as $username")
        }
        fun register(email: String, username: String) {
            button("Create account")
            scroll("Username"); field("Username").sendKeys(username)
            field("Email").sendKeys(email)
            scroll("Password"); field("Password").sendKeys("wizard password")
            hideKeyboard(); scroll("Sign in")
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Create account\")")).last().click()
            text("Account created. Sign in.")
        }
        fun restart() {
            adb("shell", "am", "force-stop", "org.asyncttwiz.android")
            adb("shell", "am", "start", "-n", "org.asyncttwiz.android/.MainActivity")
        }
        try {
            val forwards = adb("forward", "--list").trim().lines().filter(String::isNotBlank).map { it.split(Regex("\\s+")) }
            assertEquals(listOf(listOf(serial, "tcp:$systemPort", "tcp:6790")), forwards)
            session["forward"] = forwards.single(); record("session.json", session)
            driver.rotate(ScreenOrientation.PORTRAIT)
            register("shell-a@example.test", "Shell A"); signIn("shell-a@example.test", "Shell A")
            text("ATW"); capture("n01-portrait")
            named("Open navigation").click(); text("Logout"); capture("n02-portrait")
            driver.navigate().back()
            text("ATW")
            text("Games").click()
            text("Games and invitations are not available in this build yet.")
            driver.rotate(ScreenOrientation.LANDSCAPE)
            text("Logout") // Persistent, no hamburger needed.
            text("Games and invitations are not available in this build yet.")
            capture("games-landscape")
            text("Menu").click(); text("ATW"); capture("n01-landscape")
            text("Contacts").click()
            text("Contact search and invitations are not available in this build yet.")
            driver.rotate(ScreenOrientation.PORTRAIT)
            text("Contact search and invitations are not available in this build yet.")
            named("Open navigation").click(); text("Logout")
            named("Close navigation").click()
            text("Contact search and invitations are not available in this build yet.")
            driver.navigate().back(); text("ATW")
            text("Manage decks").click()
            text("Deck import and management are not available in this build yet.")
            restart(); named("Signed in as Shell A")
            text("Deck import and management are not available in this build yet.")
            capture("restored-decks-context")
            val revoke = adb("shell", "am", "instrument", "-w", "-r", "-e", "requireSavedSession", "true", "-e", "class",
                "org.asyncttwiz.android.RevokeSavedSessionTest", "org.asyncttwiz.android.test/androidx.test.runner.AndroidJUnitRunner")
            assertTrue(revoke.contains("OK (1 test)"))
            restart(); text("Your session is no longer valid. Please sign in again.")
            signIn("shell-a@example.test", "Shell A")
            text("Deck import and management are not available in this build yet.")
            named("Open navigation").click(); text("Logout").click()
            text("Async Tabletop Wizards"); text("Forgot password?")
            assertTrue(adb("shell", "run-as", "org.asyncttwiz.android", "sh", "-c",
                "'test ! -e no_backup/auth/session.bin && test ! -e no_backup/account/logout.pending && echo local-logout-complete'")
                .contains("local-logout-complete"))
            restart(); text("Forgot password?") // No automatic saved-password login.
            register("shell-b@example.test", "Shell B"); signIn("shell-b@example.test", "Shell B")
            text("ATW"); capture("new-account-menu")
            assertTrue(driver.findElements(AppiumBy.accessibilityId("Signed in as Shell A")).isEmpty())
            named("Open navigation").click(); text("Logout").click(); text("Forgot password?")
            record("shell-observation.json", mapOf("tests" to listOf("real A/B UI registration/login", "N01/N02 both orientations",
                "drawer Close/Back", "native screen Back", "rotation retains destination", "force-stop restores valid token and destination",
                "same-account reauthentication restores context", "Logout removes token immediately without server-data deletion",
                "restart stays signed out", "second account starts clean"),
                "remaining" to "No deck/contact/game data or sync exists yet; I05-I14/I26/I31 retain those journeys"))
        } finally {
            runCatching { capture("shell-final") }
            driver.quit()
            record("session-closed.json", mapOf("sessionId" to session.getValue("sessionId")))
        }
    }
}
