package org.asyncttwiz.e2e

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.ScreenOrientation
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.time.Duration
import org.openqa.selenium.support.ui.WebDriverWait

/** Only the two normal UIs create/decide/reinvite. The helper observes SQL or interrupts owned transport. */
@Tag("device")
class NativeInvitationTest {
    @Test fun declineReinviteAndAcceptSameGameWithUncertainReconnect() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val properties = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        fun control(operation: String): com.google.gson.JsonObject {
            val process = ProcessBuilder("python3", properties.getProperty("control"), operation).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().readText()
            check(process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) { text }
            return JsonParser.parseString(text).asJsonObject
        }
        val peer = Path.of(Files.readString(directory.resolve("peer-run.txt")))
        NativeLobbyCreationTest.Device(directory, directory.resolve("runner.properties")).use { first ->
            NativeLobbyCreationTest.Device(peer, directory.resolve("peer.properties")).use { second ->
                first.driver.rotate(ScreenOrientation.PORTRAIT); second.driver.rotate(ScreenOrientation.PORTRAIT)
                first.register("decision-inviter@example.test", "Decision Inviter"); first.login("decision-inviter@example.test", "Decision Inviter")
                second.register("decision-recipient@example.test", "Decision Recipient"); second.login("decision-recipient@example.test", "Decision Recipient")
                second.driver.rotate(ScreenOrientation.LANDSCAPE)
                first.text("Contacts").click(); first.field("Search usernames").sendKeys("Decision Rec"); first.hide(); first.button("Search")
                first.button("Add contact"); first.text("Added"); first.button("Invite"); first.replaceName("Retained Moonlit Rune"); first.button("Create & invite")
                first.text("Waiting for Decision Recipient to accept."); first.capture("pending-portrait")
                val initial = control("observe-pending")
                second.text("Games").click(); second.text("Retained Moonlit Rune"); second.capture("invitation-landscape")
                second.button("Decline"); second.text("No pending invitations."); second.capture("declined-recipient-landscape")
                first.text("Invitation declined"); first.driver.rotate(ScreenOrientation.LANDSCAPE)
                first.driver.navigate().back(); first.button("Retained Moonlit Rune"); first.text("Reinvite"); first.capture("declined-lobby-landscape")
                val declined = control("observe-declined")
                first.button("Reinvite"); first.text("Waiting for Decision Recipient to accept.")
                val reinvited = control("observe-reinvited")
                second.driver.rotate(ScreenOrientation.PORTRAIT); second.text("Retained Moonlit Rune"); second.text("Accept"); second.capture("invitation-portrait")
                // Freeze only this owned backend after the UI is ready. The UI sends while connected,
                // displays an uncertain original, then the helper cuts transport before resuming it.
                control("pause"); second.button("Accept"); second.text("Checking invitation outcome…"); second.capture("uncertain-accept-portrait")
                control("resume")
                second.text("Preparing"); second.text("Retained Moonlit Rune"); second.text("Not ready"); second.capture("accepted-portrait")
                second.driver.rotate(ScreenOrientation.LANDSCAPE); second.text("Preparing"); second.capture("accepted-landscape")
                WebDriverWait(first.driver, Duration.ofSeconds(45)).until { first.driver.pageSource.contains("Preparing · ") }
                first.capture("same-game-landscape")
                val accepted = control("observe-accepted")
                for (value in listOf(declined, reinvited, accepted)) for (key in listOf("gameId", "name", "createdAt", "round"))
                    assertEquals(initial[key], value[key], key)
                assertEquals(initial["invitationId"], declined["invitationId"])
                assertNotEquals(initial["invitationId"], reinvited["invitationId"])
                assertEquals(reinvited["invitationId"], accepted["invitationId"])
                assertEquals(2, accepted["attempts"].asInt); assertEquals(3, accepted["requests"].asInt)
                second.driver.navigate().back(); second.text("Invitations"); second.button("Retained Moonlit Rune"); second.text("Preparing")
                second.restart(); second.text("Retained Moonlit Rune"); second.button("Retained Moonlit Rune"); second.text("Preparing")
                first.driver.rotate(ScreenOrientation.PORTRAIT); first.text("Preparing"); first.capture("accepted-inviter-portrait")
                val restarted = control("observe-restarted"); assertEquals(accepted, restarted)
                first.record("journey.json", mapOf("devices" to 2, "normalApk" to true, "journeys" to listOf(
                    "UI create and pending invitation", "landscape recipient Decline", "retained inviter lobby and in-slot Reinvite",
                    "portrait recipient Accept interrupted after send", "same-process fenced reconnect with original request",
                    "both orientations same accepted not-ready lobby", "native Back and process restart convergence"),
                    "remaining" to "I10-I12 deck/preparation/Ready/start/cancel; I34-I36 qualification and complete UI gate"))
            }
        }
    }
}
