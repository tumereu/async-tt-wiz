package org.asyncttwiz.e2e

import com.google.gson.JsonParser
import io.appium.java_client.AppiumBy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import org.openqa.selenium.ScreenOrientation
import org.openqa.selenium.support.ui.WebDriverWait
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Business state is created through the normal UI. The helper only observes owned synthetic SQL. */
@Tag("device")
class NativePreparationPickerTest {
    @Test fun retainedInvitationAcceptancePreservesInviterCopyAndRecipientPrivateEmptyPicker() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        NativeLobbyCreationTest.Device(directory, directory.resolve("runner.properties")).use { ui ->
            ui.driver.rotate(ScreenOrientation.LANDSCAPE)
            val alreadyAccepted = System.getenv("ATW_PICKER_ACCEPTED") == "true"
            if (!alreadyAccepted) {
                ui.text("Logout").click(); ui.login("picker-recipient@example.test", "Picker Recipient")
                ui.text("Games").click(); ui.text("Native Prepared Spark"); ui.button("Accept")
            } else ui.named("Signed in as Picker Recipient")
            WebDriverWait(ui.driver, Duration.ofSeconds(45)).until { ui.driver.pageSource.contains("Preparing · ") }
            ui.text("Picker source"); ui.text("Mainboard · 6"); ui.text("Sideboard · 3")
            ui.text("No deck selected"); ui.capture("accepted-recipient-private-lobby-landscape")
            ui.button("Choose deck"); ui.text("No saved decks yet."); ui.capture("accepted-recipient-empty-picker-landscape")
            ui.driver.rotate(ScreenOrientation.PORTRAIT); ui.text("No saved decks yet."); ui.capture("accepted-recipient-empty-picker-portrait")
            ui.driver.navigate().back(); ui.text("Picker source"); ui.text("Mainboard · 6"); ui.capture("accepted-recipient-private-lobby-portrait")
            val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
            val process = ProcessBuilder("python3", input.getProperty("control"), "accepted").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) { output }
            val result = JsonParser.parseString(output).asJsonObject
            assertEquals(2, result["requests"].asInt); assertEquals(1, result["prepared"].asInt)
            ui.record("retained-acceptance.json", mapOf("normalApk" to true, "resumedAfterAcceptedLabelDiagnostic" to alreadyAccepted, "scope" to
                "Existing UI-created pending game accepted by the recipient after ordinary Logout/login; both orientations retain inviter copy while recipient picker is private and empty. No Ready/start asserted."))
        }
    }

    @Test fun personalPickerOneConfirmationImportReturnAndImmutablePreparedCopy() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        fun observe(label: String): com.google.gson.JsonObject {
            val process = ProcessBuilder("python3", input.getProperty("control"), label).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().readText()
            check(process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) { text }
            return JsonParser.parseString(text).asJsonObject
        }
        NativeLobbyCreationTest.Device(directory, directory.resolve("runner.properties")).use { ui ->
            fun editField() = ui.driver.findElement(AppiumBy.className("android.widget.EditText"))
            fun paste(text: String) { editField().sendKeys(text); ui.hide() }
            fun review() {
                ui.scroll("Review import")
                WebDriverWait(ui.driver, Duration.ofSeconds(45)).until {
                    ui.driver.findElement(By.xpath("//android.view.View[@clickable='true'][./android.widget.TextView[@text='Review import']]" )).takeIf { it.isEnabled }
                }.click()
            }
            fun import(name: String, list: String) {
                ui.button("Import deck"); paste(list); review()
                ui.scroll("Deck name"); paste(name); ui.button("Save deck"); ui.text("Use selected deck"); ui.text(name)
            }
            fun selected(name: String) = WebDriverWait(ui.driver, Duration.ofSeconds(30)).until {
                ui.driver.findElement(By.xpath("//*[@selected='true' or @checked='true'][.//*[@text='$name']]"))
            }
            fun prepared() { ui.text("Picker source"); ui.text("Mainboard · 6"); ui.text("Sideboard · 3"); ui.text("Change deck") }
            ui.driver.rotate(ScreenOrientation.PORTRAIT)
            ui.register("picker-recipient@example.test", "Picker Recipient")
            ui.register("picker-inviter@example.test", "Picker Inviter"); ui.login("picker-inviter@example.test", "Picker Inviter")
            ui.text("Manage decks").click(); ui.button("Download card records"); ui.text("Card records are up to date.")
            ui.destination("Contacts"); ui.field("Search usernames").sendKeys("Picker Rec"); ui.hide(); ui.button("Search")
            ui.button("Add contact"); ui.text("Added"); ui.button("Invite"); ui.replaceName("Native Prepared Spark"); ui.button("Create & invite")
            ui.text("Waiting for Picker Recipient to accept."); ui.button("Choose deck"); ui.text("No saved decks yet.")
            ui.capture("empty-picker-portrait"); ui.driver.rotate(ScreenOrientation.LANDSCAPE)
            ui.text("No saved decks yet."); ui.capture("empty-picker-landscape")
            ui.button("Import deck"); ui.named("Exit without saving").click(); ui.text("Use selected deck")
            ui.driver.rotate(ScreenOrientation.PORTRAIT)
            import("Picker source", "Deck\n4 Coppercloud Surveyor (ZPA) 1\n2 Amberglass Chorus\nSideboard\n3 Velvet Prism Engine")
            assertTrue(ui.driver.findElements(By.xpath("//*[@selected='true']")).isEmpty())
            import("Missing art alternative", "1 Feltwing Atlas\nSideboard\n1 Coppercloud Echo!")
            ui.button("Picker source"); selected("Picker source")
            ui.button("Missing art alternative"); selected("Missing art alternative")
            ui.capture("border-draft-and-missing-art-portrait")
            ui.driver.rotate(ScreenOrientation.LANDSCAPE); selected("Missing art alternative")
            ui.capture("border-draft-landscape")
            val draft = observe("draft"); assertEquals(0, draft["requests"].asInt); assertEquals(0, draft["prepared"].asInt)
            ui.driver.navigate().back(); ui.text("No deck selected"); ui.button("Choose deck")
            ui.button("Picker source"); ui.named("Close deck picker").click(); ui.text("No deck selected")
            ui.button("Choose deck"); ui.button("Picker source"); selected("Picker source")
            ui.capture("confirm-landscape"); ui.button("Use selected deck"); prepared(); ui.capture("prepared-landscape")
            val accepted = observe("selected"); assertEquals(1, accepted["requests"].asInt)
            ui.driver.rotate(ScreenOrientation.PORTRAIT); prepared(); ui.capture("prepared-portrait")
            ui.driver.navigate().back(); ui.text("Invitations"); ui.button("Native Prepared Spark"); prepared()
            ui.restart(); ui.button("Native Prepared Spark"); prepared(); ui.capture("prepared-restarted")
            assertEquals(accepted, observe("restarted"))
            // Normal source rename/overwrite/cover/delete cannot rewrite the game-local copy.
            ui.destination("Manage decks"); ui.button("Picker source"); ui.button("Edit"); ui.button("Rename")
            editField().clear(); paste("Edited saved source"); ui.button("Save name"); ui.text("Edited saved source")
            ui.button("Edit"); ui.button("Import decklist"); paste("1 Feltwing Atlas")
            review(); ui.button("Save deck"); ui.text("Mainboard · 1")
            ui.button("Edit"); ui.button("Choose cover"); ui.button("Feltwing Atlas"); ui.button("Use cover")
            ui.text("Edited saved source"); ui.capture("edited-saved-source")
            val edited = observe("edited"); assertEquals(accepted["copy"], edited["copy"])
            ui.button("Edit"); ui.button("Delete saved deck"); ui.button("Delete saved deck"); ui.text("Missing art alternative")
            ui.destination("Games"); ui.button("Native Prepared Spark"); prepared()
            val deleted = observe("deleted"); assertEquals(accepted["copy"], deleted["copy"])
            assertEquals(1, deleted["requests"].asInt); assertEquals(1, deleted["sources"].asInt)
            ui.restart(); ui.button("Native Prepared Spark"); prepared(); ui.capture("deleted-source-retained-copy-portrait")
            ui.driver.rotate(ScreenOrientation.LANDSCAPE); prepared(); ui.capture("deleted-source-retained-copy-landscape")
            assertEquals(deleted, observe("final"))
            ui.record("journey.json", mapOf("normalApk" to true, "devices" to 1, "journeys" to listOf(
                "UI-created synthetic accounts/catalog/decks/contact/named game", "empty picker both orientations",
                "contextual Import exit/save returns to picker without auto-selection", "multiple border-only highlights/rotation send zero requests",
                "native Back/X cancel without preparation", "one confirmation sends one ordered selection", "real name/counts both orientations",
                "Back/reopen/process restart", "missing art text fallback", "saved-source rename/overwrite/cover/delete leaves exact copy intact"),
                "remaining" to "I10b transfers; I11/I12 Ready/start/cancel and full two-Android overlap; I34-I36 final qualification/UI gate"))
        }
    }
}
