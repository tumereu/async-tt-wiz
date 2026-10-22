package org.asyncttwiz.e2e

import com.google.gson.GsonBuilder
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.nativekey.*
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

/** All business state comes from two ordinary installed UIs, not helper account/invitation APIs. */
@Tag("device")
class NativeLobbyCreationTest {
    private class Device(val directory: Path, properties: Path) : AutoCloseable {
        private val input = Properties().apply { Files.newBufferedReader(properties).use(::load) }
        private fun property(name: String) = requireNotNull(input.getProperty(name))
        fun record(name: String, value: Any) = Files.writeString(directory.resolve(name), GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n")
        private val options = UiAutomator2Options().setUdid(property("serial")).setDeviceName(property("serial"))
            .setRemoteAdbHost("127.0.0.1").setAdbPort(property("adbPort").toInt()).setSystemPort(property("systemPort").toInt())
            .setAppPackage("org.asyncttwiz.android").setAppActivity("org.asyncttwiz.android.MainActivity")
            .setNoReset(true).setFullReset(false).setNewCommandTimeout(Duration.ofSeconds(600)).also {
                it.setCapability("appium:autoLaunch", false); it.setCapability("appium:skipLogcatCapture", true)
            }
        val driver = AndroidDriver(URI(property("appium")).toURL(), options)
        private val wait = WebDriverWait(driver, Duration.ofSeconds(45)).ignoring(StaleElementReferenceException::class.java)
        init { record("session.json", mapOf("owner" to property("owner"), "serial" to property("serial"), "sessionId" to driver.sessionId.toString(),
            "systemPort" to property("systemPort").toInt(), "forward" to listOf(property("serial"), "tcp:${property("systemPort")}", "tcp:6790"))) }
        fun text(value: String): WebElement = wait.until {
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).lastOrNull { it.isDisplayed }
        }
        fun named(value: String): WebElement = wait.until { driver.findElement(AppiumBy.accessibilityId(value)).takeIf { it.isDisplayed } }
        fun field(value: String): WebElement = wait.until {
            driver.findElement(By.xpath("//android.widget.EditText[@content-desc='$value' or .//*[@content-desc='$value']]")).takeIf { it.isDisplayed }
        }
        fun scroll(value: String): WebElement = wait.until {
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).lastOrNull { it.isDisplayed }
                ?: driver.findElement(AppiumBy.androidUIAutomator("new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().text(\"$value\"))"))
        }
        fun button(value: String) {
            wait.until {
                val label = scroll(value)
                val target = if (label.getAttribute("clickable") == "true") label else driver.findElement(By.xpath("//*[@text='$value']/ancestor::*[@clickable='true'][1]"))
                target.takeIf { it.isEnabled }
            }.click()
        }
        fun hide() { if (driver.isKeyboardShown) driver.hideKeyboard() }
        fun register(email: String, username: String) {
            button("Create account"); scroll("Username"); field("Username").sendKeys(username)
            scroll("Email"); field("Email").sendKeys(email); scroll("Password"); field("Password").sendKeys("lobby wizard password")
            hide(); scroll("Sign in"); text("Create account").click(); text("Account created. Sign in.")
        }
        fun login(email: String, username: String) {
            scroll("Email"); field("Email").clear(); field("Email").sendKeys(email)
            hide(); scroll("Forgot password?"); field("Password").sendKeys("lobby wizard password"); hide()
            scroll("Forgot password?"); text("Sign in").click(); named("Signed in as $username")
        }
        fun destination(value: String) {
            if (driver.orientation == ScreenOrientation.PORTRAIT) named("Open navigation").click()
            text(value).click()
        }
        fun replaceName(value: String) {
            // No explicit clear/select-all command: touching must select the existing suggestion/edited text.
            field("Game name").click(); driver.setClipboardText(value)
            driver.pressKey(KeyEvent(AndroidKey.V).withMetaModifier(KeyEventMetaModifier.CTRL_ON))
            assertEquals(value, field("Game name").text)
            // Appium hideKeyboard uses Escape on this adapter, which deliberately dismisses a
            // native dialog. Use the field's actual IME Done action instead of cancelling it.
            driver.executeScript("mobile: performEditorAction", mapOf("action" to "done"))
        }
        fun capture(name: String) {
            Files.writeString(directory.resolve("$name.xml"), driver.pageSource)
            Files.write(directory.resolve("$name.png"), driver.getScreenshotAs(OutputType.BYTES))
        }
        fun restart() {
            for (args in listOf(listOf("shell", "am", "force-stop", "org.asyncttwiz.android"),
                listOf("shell", "am", "start", "-n", "org.asyncttwiz.android/.MainActivity"))) {
                val process = ProcessBuilder(listOf(property("linuxAdb"), "-H", "127.0.0.1", "-P", property("adbPort"), "-s", property("serial")) + args)
                    .redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { output }
            }
        }
        override fun close() {
            runCatching { capture("final") }; driver.quit(); record("session-closed.json", mapOf("closed" to true))
        }
    }

    @Test fun contactsNamedCreateWaitingLobbyAndOtherAccountInstances() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val peer = Path.of(Files.readString(directory.resolve("peer-run.txt")))
        Device(directory, directory.resolve("runner.properties")).use { first ->
            Device(peer, directory.resolve("peer.properties")).use { second ->
                first.driver.rotate(ScreenOrientation.PORTRAIT); second.driver.rotate(ScreenOrientation.PORTRAIT)
                first.register("lobby-inviter@example.test", "Lobby Inviter"); first.login("lobby-inviter@example.test", "Lobby Inviter")
                second.register("lobby-recipient@example.test", "Lobby Recipient"); second.login("lobby-recipient@example.test", "Lobby Recipient")
                second.text("Contacts").click(); second.text("No saved contacts.")
                first.text("Contacts").click(); first.button("Search"); first.text("Enter a username.")
                first.field("Search usernames").sendKeys("Nobody matching"); first.hide(); first.button("Search"); first.text("No players found.")
                first.field("Search usernames").clear(); first.field("Search usernames").sendKeys("LOBBY REC"); first.hide(); first.button("Search")
                first.text("Lobby Recipient"); first.capture("search-results-portrait"); first.button("Add contact"); first.text("Added")
                assertFalse(first.driver.pageSource.contains("lobby-recipient@example.test"))
                first.driver.rotate(ScreenOrientation.LANDSCAPE); first.text("Saved contacts"); first.capture("contacts-landscape")
                first.button("Invite"); first.text("New game"); first.named("Suggest another game name").click()
                first.replaceName("Cancelled Spark"); first.capture("naming-landscape"); first.button("Cancel")
                first.button("Invite"); first.replaceName("Back Cancelled Spark"); first.driver.navigate().back(); first.text("Saved contacts")
                first.button("Invite"); first.field("Game name").clear()
                first.driver.executeScript("mobile: performEditorAction", mapOf("action" to "done"))
                first.button("Create & invite"); first.text("Enter a game name.")
                first.replaceName("Edited Spark"); first.replaceName("Native Azure Spark") // edited text is selected too
                first.driver.rotate(ScreenOrientation.PORTRAIT); assertEquals("Native Azure Spark", first.field("Game name").text)
                first.capture("naming-portrait"); first.button("Create & invite")
                first.text("Waiting for Lobby Recipient to accept."); first.text("Native Azure Spark"); first.capture("waiting-portrait")
                first.driver.rotate(ScreenOrientation.LANDSCAPE); first.text("Waiting for Lobby Recipient to accept."); first.capture("waiting-landscape")
                first.driver.navigate().back(); first.text("Invitations"); first.button("Native Azure Spark"); first.text("Waiting for Lobby Recipient to accept.")
                first.destination("Menu"); first.text("Games").click(); first.text("Invitations"); first.capture("games-landscape")
                first.restart(); first.text("Native Azure Spark"); first.button("Native Azure Spark"); first.text("Waiting for Lobby Recipient to accept.")
                second.destination("Games"); second.text("Native Azure Spark"); second.text("Invitation responses are not available in this build yet.")
                second.capture("recipient-invitation-portrait"); assertFalse(second.driver.pageSource.contains("lobby-inviter@example.test"))
                second.driver.rotate(ScreenOrientation.LANDSCAPE); second.text("Native Azure Spark"); second.capture("recipient-invitation-landscape")
                second.destination("Contacts"); second.text("No saved contacts.") // no reciprocal relationship
                second.text("Logout").click(); second.scroll("Forgot password?"); second.login("lobby-inviter@example.test", "Lobby Inviter")
                second.text("Contacts").click(); second.text("Lobby Recipient"); second.capture("synchronized-contact-landscape")
                second.destination("Games"); second.text("Native Azure Spark"); second.button("Native Azure Spark"); second.text("Waiting for Lobby Recipient to accept.")
                second.capture("synchronized-waiting-landscape")
                first.record("journey.json", mapOf("journeys" to listOf("two UI registrations/logins", "empty/unknown/case-fold prefix username search",
                    "unilateral add", "dialog reroll/touch replacement/blank validation/Cancel/native Back", "rotation retains edited draft",
                    "atomic named creation to actual waiting lobby", "Games browser and exact row/navigation reopen", "retained process restart",
                    "recipient pending invitation/name/date", "other same-account installation contacts/game synchronization"),
                    "remaining" to "I09b decisions/ordered game processing; I10-I12 preparation/start/cancel; I34-I36 final qualification/UI"))
            }
        }
    }
}
