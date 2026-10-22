package org.asyncttwiz.e2e

import com.google.gson.GsonBuilder
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.android.nativekey.*
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

/** Only normal native UI actions. Host mail access observes delivery; instrumentation observes credentials. */
@Tag("device")
class NativePasswordResetTest {
    private class Device(val directory: Path, properties: Path) : AutoCloseable {
        val input = Properties().apply { Files.newBufferedReader(properties).use(::load) }
        fun property(name: String) = requireNotNull(input.getProperty(name))
        private val gson = GsonBuilder().setPrettyPrinting().create()
        fun record(name: String, value: Any) { Files.writeString(directory.resolve(name), gson.toJson(value) + "\n") }
        fun adb(vararg args: String): String {
            val process = ProcessBuilder(listOf(property("linuxAdb"), "-H", "127.0.0.1", "-P", property("adbPort"), "-s", property("serial")) + args)
                .redirectErrorStream(true).start()
            check(process.waitFor(if (args.contains("instrument")) 150 else 60, TimeUnit.SECONDS)) { "Owned ADB command timed out" }
            val output = process.inputStream.bufferedReader().readText()
            check(process.exitValue() == 0) { "Owned ADB command failed (arguments/output intentionally not logged)" }
            return output
        }
        private val options = UiAutomator2Options().setUdid(property("serial")).setDeviceName(property("serial"))
            .setRemoteAdbHost("127.0.0.1").setAdbPort(property("adbPort").toInt()).setSystemPort(property("systemPort").toInt())
            .setAppPackage("org.asyncttwiz.android").setAppActivity("org.asyncttwiz.android.MainActivity")
            .setNoReset(true).setFullReset(false).setNewCommandTimeout(Duration.ofSeconds(600)).also {
                it.setCapability("appium:autoLaunch", false); it.setCapability("appium:skipLogcatCapture", true)
            }
        val driver = AndroidDriver(URI(property("appium")).toURL(), options)
        private val wait = WebDriverWait(driver, Duration.ofSeconds(35))
        private val session = linkedMapOf<String, Any>("owner" to property("owner"), "serial" to property("serial"),
            "sessionId" to driver.sessionId.toString(), "systemPort" to property("systemPort").toInt(), "capabilities" to driver.capabilities.asMap())
        init {
            record("session.json", session)
            val forwards = adb("forward", "--list").trim().lines().filter(String::isNotBlank).map { it.split(Regex("\\s+")) }
            assertEquals(listOf(listOf(property("serial"), "tcp:${property("systemPort")}", "tcp:6790")), forwards)
            session["forward"] = forwards.single(); record("session.json", session)
        }
        fun text(value: String): WebElement = wait.until {
            driver.findElement(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).takeIf { it.isDisplayed }
        }
        fun named(value: String): WebElement = wait.until { driver.findElement(AppiumBy.accessibilityId(value)).takeIf { it.isDisplayed } }
        fun field(value: String): WebElement = wait.until {
            driver.findElement(AppiumBy.xpath("//android.widget.EditText[.//*[@content-desc='$value']]")).takeIf { it.isDisplayed }
        }
        fun scroll(value: String) { driver.findElement(AppiumBy.androidUIAutomator(
            "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().text(\"$value\"))")) }
        fun button(value: String) {
            scroll(if (value == "Sign in") "Forgot password?" else value)
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"$value\")")).last().click()
        }
        fun hideKeyboard() { if (driver.isKeyboardShown) driver.hideKeyboard() }
        fun capture(name: String) {
            Files.writeString(directory.resolve("$name.xml"), driver.pageSource)
            Files.write(directory.resolve("$name.png"), driver.getScreenshotAs(OutputType.BYTES))
        }
        fun paste(label: String, value: String) {
            scroll(label); field(label).click(); field(label).clear()
            driver.setClipboardText(value)
            driver.pressKey(KeyEvent(AndroidKey.V).withMetaModifier(KeyEventMetaModifier.CTRL_ON))
            hideKeyboard()
        }
        fun register(email: String, username: String) {
            button("Create account"); scroll("Username"); field("Username").sendKeys(username)
            field("Email").sendKeys(email); scroll("Password"); field("Password").sendKeys("old wizard password")
            hideKeyboard(); scroll("Sign in")
            driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Create account\")")).last().click()
            scroll("Account created. Sign in."); text("Account created. Sign in.")
        }
        fun signIn(email: String, password: String, username: String) {
            scroll("Email"); field("Email").clear(); field("Email").sendKeys(email)
            paste("Password", password); button("Sign in"); named("Signed in as $username")
        }
        fun restart() {
            adb("shell", "am", "force-stop", "org.asyncttwiz.android")
            adb("shell", "am", "start", "-n", "org.asyncttwiz.android/.MainActivity")
        }
        fun open(link: String, cold: Boolean = false) {
            if (cold) adb("shell", "am", "force-stop", "org.asyncttwiz.android")
            // Resolve the real browsable manifest filter, not a test-only activity or action endpoint.
            adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-c", "android.intent.category.BROWSABLE",
                "-d", "'$link'", "-p", "org.asyncttwiz.android")
        }
        fun observer(expected: Boolean, capture: Boolean = false) {
            val output = adb("shell", "am", "instrument", "-w", "-r", "-e", "captureSession", capture.toString(),
                "-e", "expectedAuthorization", expected.toString(), "-e", "class", "org.asyncttwiz.android.SessionAuthorizationTest",
                "org.asyncttwiz.android.test/androidx.test.runner.AndroidJUnitRunner")
            record("authorization-${if (capture) "capture" else expected}-${System.nanoTime()}.json", mapOf("output" to output))
            assertTrue(output.contains("OK (1 test)"), "Actual saved-session authorization assertion failed")
        }
        fun logout() {
            if (driver.orientation == ScreenOrientation.PORTRAIT) named("Open navigation").click()
            text("Logout").click(); scroll("Forgot password?"); text("Forgot password?")
        }
        override fun close() {
            runCatching { capture("reset-final") }
            driver.quit(); record("session-closed.json", mapOf("sessionId" to session.getValue("sessionId")))
        }
    }

    @Test fun twoNormalInstallationsRequestOpenConfirmAndRecoverOnlyAfterSuccessfulReset() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val peer = Path.of(Files.readString(directory.resolve("peer-run.txt")))
        Device(directory, directory.resolve("runner.properties")).use { first ->
            Device(peer, directory.resolve("peer.properties")).use { second ->
                first.driver.rotate(ScreenOrientation.PORTRAIT); second.driver.rotate(ScreenOrientation.PORTRAIT)
                first.button("Forgot password?"); first.text("Reset password")
                first.field("Email").sendKeys("not-an-email"); first.hideKeyboard(); first.button("Send reset link")
                first.text("Enter a valid email with an ASCII mailbox name, such as player@example.com.")
                first.capture("a03-invalid-email-portrait")
                first.driver.navigate().back(); first.text("Forgot password?")
                first.register("reset-ui@example.test", "Reset Wizard")
                first.signIn("reset-ui@example.test", "old wizard password", "Reset Wizard")
                second.signIn("reset-ui@example.test", "old wizard password", "Reset Wizard")
                first.observer(true, true); second.observer(true, true)
                // The native request form can be reached while a session is still valid; opening does not sign out.
                first.open("https://reset.synthetic.invalid/reset-password?token=${"A".repeat(43)}")
                first.text("This reset link is invalid, expired or already used."); first.button("Request another link")
                first.field("Email").sendKeys("reset-ui@example.test"); first.hideKeyboard()
                first.driver.rotate(ScreenOrientation.LANDSCAPE); first.capture("a03-landscape")
                first.button("Send reset link"); first.text("If that email is registered, a reset link will be sent.")
                first.capture("a03-submitted-landscape")
                val process = ProcessBuilder("python3", "scripts/run-reset-devices.py", "observe", "--directory", first.property("deviceDirectory"))
                    .directory(Path.of(first.property("repository")).toFile())
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start()
                check(process.waitFor(30, TimeUnit.SECONDS)); check(process.exitValue() == 0)
                val link = process.inputStream.bufferedReader().readText().trim()
                check(link.startsWith("https://reset.synthetic.invalid/reset-password?token="))
                first.observer(true); second.observer(true)
                first.open(link) // Actual observed link; the native checkpoint below independently checks warm delivery.
                first.text("New password"); first.capture("a04-landscape")
                first.driver.rotate(ScreenOrientation.PORTRAIT); first.text("New password"); first.capture("a04-portrait")
                first.paste("New password", "too short".take(5)); first.button("Change password")
                first.text("Use 8–256 characters (at most 1024 UTF-8 bytes). Password spaces and Unicode are kept exactly.")
                first.capture("a04-password-error-portrait")
                first.observer(true); second.observer(true) // An opened link and invalid input still have no reset effect.
                first.open(link, cold = true); first.text("New password")
                val intentCheck = first.adb("shell", "am", "instrument", "-w", "-r", "-e", "resetLink", "'$link'", "-e", "class",
                    "org.asyncttwiz.android.ResetIntentTest", "org.asyncttwiz.android.test/androidx.test.runner.AndroidJUnitRunner")
                first.record("native-intent-input-saved-state.json", mapOf("output" to intentCheck))
                assertTrue(intentCheck.contains("OK (1 test)"), "Actual native intent callback/input/saved-state check failed")
                first.open(link, cold = true); first.text("New password")
                val password = " new Cafe\u0301 🧙 password "
                first.paste("New password", password); first.named("Show password").click()
                assertEquals(password, first.field("New password").text)
                first.open(link) // Real warm singleTask delivery, not a test-only callback or ActivityScenario route.
                assertEquals(password, first.field("New password").text)
                first.driver.rotate(ScreenOrientation.LANDSCAPE)
                first.text("New password"); first.named("Show password").click()
                assertEquals(password, first.field("New password").text)
                first.driver.rotate(ScreenOrientation.PORTRAIT)
                first.text("New password"); first.named("Show password").click()
                assertEquals(password, first.field("New password").text)
                first.named("Hide password").click(); first.capture("a04-corrected-hidden-portrait")
                first.button("Change password"); first.text("Password changed. Sign in with your new password.")
                first.observer(false); second.observer(false) // Actual new GETs using both original UI-created credentials.
                second.restart(); second.text("Your session is no longer valid. Please sign in again.")
                second.capture("peer-revoked-after-reset")
                second.scroll("Email"); second.field("Email").sendKeys("reset-ui@example.test")
                second.paste("Password", "old wizard password"); second.button("Sign in"); second.text("Email or password not recognised.")
                first.restart(); first.text("Forgot password?") // Instrumentation observers finish the target process.
                first.signIn("reset-ui@example.test", password, "Reset Wizard")
                second.signIn("reset-ui@example.test", password, "Reset Wizard")
                first.observer(true, true); second.observer(true, true)
                first.open(link); first.text("This reset link is invalid, expired or already used.")
                assertTrue(first.driver.findElements(AppiumBy.androidUIAutomator("new UiSelector().text(\"Change password\")")).isEmpty())
                first.capture("a04-used-link-portrait"); first.driver.rotate(ScreenOrientation.LANDSCAPE)
                first.text("Request another link"); first.capture("a04-used-link-landscape")
                first.observer(true); second.observer(true)
                first.open(link); first.text("Request another link")
                second.restart(); second.named("Signed in as Reset Wizard")
                first.button("Back to sign in"); first.named("Signed in as Reset Wizard")
                first.restart(); first.named("Signed in as Reset Wizard"); first.capture("new-password-resumed")
                first.logout(); first.restart(); first.scroll("Forgot password?"); first.text("Forgot password?")
                first.register("reset-other@example.test", "Other Reset Wizard")
                first.driver.rotate(ScreenOrientation.PORTRAIT)
                first.signIn("reset-other@example.test", "old wizard password", "Other Reset Wizard")
                first.capture("clean-second-account")
                assertTrue(first.driver.findElements(AppiumBy.accessibilityId("Signed in as Reset Wizard")).isEmpty())
                first.logout(); second.logout()
                first.record("reset-observation.json", mapOf("devices" to 2, "journeys" to listOf("UI registration and two logins",
                    "A03 request/error/neutral outcome", "actual synthetic SMTP link observation", "warm and cold manifest link resolution",
                    "opening/request/invalid input do not revoke either session", "native exact Unicode-whitespace input/rotation/duplicate/saved-state",
                    "explicit confirmation with native clipboard Unicode-whitespace password", "both old credentials reject new requests",
                    "old password refused, new password signs in on both", "used link has no action and preserves both new sessions",
                    "force-stop protected-token resume", "local Logout/signed-out restart/different account"),
                    "remaining" to "Final I34/I35/I36/B8 coverage, compatibility and complete interface comparison; not gameplay/reset-order guarantees"))
            }
        }
    }

    /** Recheck only the retained recheck3 tail: reset/revocation/input/resume already ran before its offscreen-label failure. */
    @Test fun completesRetainedLogoutAndSecondAccountCheckpoint() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val peer = Path.of(Files.readString(directory.resolve("peer-run.txt")))
        Device(directory, directory.resolve("runner.properties")).use { first ->
            Device(peer, directory.resolve("peer.properties")).use { second ->
                // Registration was accepted in the first tail run. Its retry then reached
                // a landscape viewport where the password label is visible but the
                // helper's edit-field selector is not. Finish the account switch in portrait rather
                // than expand this reset unit into another keyboard-driver qualification.
                first.hideKeyboard(); first.driver.rotate(ScreenOrientation.PORTRAIT)
                first.scroll("Email"); assertTrue(first.field("Email").text.contains("reset-other@example.test"))
                assertTrue(first.adb("shell", "run-as", "org.asyncttwiz.android", "sh", "-c",
                    "'test ! -e no_backup/auth/session.bin && test ! -e no_backup/account/logout.pending && echo local-logout-complete'")
                    .contains("local-logout-complete"))
                first.capture("retained-second-account-sign-in")
                first.signIn("reset-other@example.test", "old wizard password", "Other Reset Wizard")
                first.capture("clean-second-account")
                assertTrue(first.driver.findElements(AppiumBy.accessibilityId("Signed in as Reset Wizard")).isEmpty())
                first.logout(); first.restart(); first.scroll("Forgot password?"); first.text("Forgot password?")
                second.restart(); second.named("Signed in as Reset Wizard")
                second.logout(); second.restart(); second.scroll("Forgot password?"); second.text("Forgot password?")
                first.record("retained-tail-observation.json", mapOf("tests" to listOf("retained local Logout has no token/cleanup marker",
                    "landscape accepted-registration notice reachable by scroll", "UI different-account login starts clean",
                    "both installations finish local Logout and remain signed out after restart"),
                    "baseline" to "recheck3 core reset/input/both-session-revocation/new-password login/reuse/refusal/resume assertions; no environment reset or replay"))
            }
        }
    }
}
