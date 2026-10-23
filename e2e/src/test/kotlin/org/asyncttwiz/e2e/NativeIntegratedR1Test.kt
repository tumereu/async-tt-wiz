package org.asyncttwiz.e2e

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.appium.java_client.AppiumBy
import io.appium.java_client.android.nativekey.AndroidKey
import io.appium.java_client.android.nativekey.KeyEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import org.openqa.selenium.ScreenOrientation
import org.openqa.selenium.remote.RemoteWebElement
import org.openqa.selenium.support.ui.WebDriverWait
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Initial I31 coverage, NOT a replacement for the remaining e2e-strategy variants.
 * All shared actions originate in the three normal UIs. The existing whole-resource lifecycle
 * must supply three qualified installations, one backend, the start observer/fault adapter and
 * the normal instrumentation companion on instance three. See this writer run's execution.md.
 * Authored only: neither compilation nor device execution is certified by this source.
 */
@Tag("device")
class NativeIntegratedR1Test {
    @Test fun uiCreatedForkChatResultRematchAndThreeInstanceRecovery() {
        val run = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val peer = Path.of(Files.readString(run.resolve("peer-run.txt")).trim())
        // Intentionally no two-device fallback, cross-owner live adoption or emulator provisioning here.
        val third = Path.of(Files.readString(run.resolve("third-run.txt")).trim())
        val directories = listOf(run, peer, third)
        val inputs = directories.map { directory -> Properties().apply {
            Files.newBufferedReader(directory.resolve("runner.properties")).use(::load)
        } }
        for (key in listOf("owner", "serial", "adbPort", "systemPort", "appium")) {
            require(inputs.map { requireNotNull(it.getProperty(key)) }.distinct().size == 3) {
                "Three explicitly owned, distinct device profiles required: $key"
            }
        }
        check(System.getenv("ATW_START_STAGE").orEmpty() in setOf("", "setup"))
        val phases = NativePhases(run, "integrated-r-one", NativePhases.requested("setup"),
            listOf("setup", "history", "recovery", "result", "rematch", "privacy", "delete"))
        val checkpoint = run.resolve("i31-identities.json")
        val ids = if (phases.fullJourney) JsonObject() else JsonParser.parseString(Files.readString(checkpoint)).asJsonObject
        fun remember(key: String, value: String) {
            ids.addProperty(key, value); Files.writeString(checkpoint, ids.toString() + "\n")
        }
        fun id(key: String) = requireNotNull(ids.get(key)) { "Missing retained I31 checkpoint: $key" }.asString
        fun control(operation: String): JsonObject {
            val command = listOf("python3", requireNotNull(inputs[0].getProperty("control")), operation)
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exited = process.waitFor(150, TimeUnit.SECONDS)
            Files.writeString(run.resolve("i31-$operation-${System.nanoTime()}.txt"), output)
            check(exited && process.exitValue() == 0) { "Owned start/observation control failed: $operation; $output" }
            return JsonParser.parseString(output).asJsonObject
        }
        fun game(snapshot: JsonObject) = snapshot.getAsJsonArray("games").single().asJsonObject
        fun play(snapshot: JsonObject, playId: String) = snapshot.getAsJsonArray("plays")
            .single { it.asJsonObject.get("id").asString == playId }.asJsonObject
        fun requests(snapshot: JsonObject, action: String) = snapshot.getAsJsonArray("requests").map { it.asJsonObject }
            .filter { it.getAsJsonObject("command").getAsJsonObject("action").get("type").asString == action }
        fun acceptedRequest(request: JsonObject, playId: String) {
            val command = request.getAsJsonObject("command")
            val result = request.getAsJsonObject("result")
            assertEquals(command.get("requestId"), result.get("requestId"))
            assertEquals(id("game"), command.getAsJsonObject("scope").get("gameId").asString)
            assertEquals(playId, command.getAsJsonObject("scope").get("playId").asString)
            assertEquals(command.get("scope"), result.get("scope"))
            assertEquals("accepted", result.get("outcome").asString)
        }

        if (phases.includes("setup")) {
            // Reuse UI registration/catalog/import/invitation/Random/Ready and original-start recovery.
            // The configured existing start adapter arranges acknowledgement loss, never game seeding.
            NativeGameStartTest().twoPlayersReadyToActualInitialTableAndRecoverOriginalStart()
            val accepted = control("observe")
            val current = game(accepted)
            remember("game", current.get("id").asString)
            remember("createdAt", current.get("createdAt").asString)
            remember("firstPlay", current.get("play").asString)
            remember("firstRound", current.get("round").asString)
            remember("initialPlay", play(accepted, id("firstPlay")).toString())
            val starter = play(accepted, id("firstPlay")).getAsJsonObject("starter").get("accountId").asString
            remember("drawPlayer", accepted.getAsJsonArray("players").single {
                it.asJsonObject.get("id").asString == starter
            }.asJsonObject.get("username").asString)
            remember("preparedCopies", accepted.getAsJsonArray("preparations").map { row ->
                row.asJsonObject.let { it.get("actor").asString to it.get("deck").toString() }
            }.sortedBy { it.first }.toString())
            phases.completed("setup")
        }
        NativeLobbyCreationTest.Device(run, run.resolve("runner.properties")).use { first ->
            NativeLobbyCreationTest.Device(peer, peer.resolve("runner.properties")).use { second ->
                NativeLobbyCreationTest.Device(third, third.resolve("runner.properties")).use { tablet ->
                    val opponents = listOf(first, second)
                    val all = opponents + tablet
                    fun table(device: NativeLobbyCreationTest.Device) {
                        device.ui.reopen("Native Initial Spark", "Shared table")
                    }
                    fun life(device: NativeLobbyCreationTest.Device, player: String, before: Int, increase: Boolean) {
                        device.named("$player, life $before").click()
                        device.named(if (increase) "Increase life" else "Decrease life").click()
                        device.named("Close player actions").click()
                    }
                    fun boundary(device: NativeLobbyCreationTest.Device): String {
                        device.button("Event details")
                        val label = WebDriverWait(device.driver, Duration.ofSeconds(45)).until {
                            device.driver.findElements(By.xpath("//*[@text[starts-with(., 'Boundary ')]]"))
                                .singleOrNull { it.isDisplayed }?.text
                        }
                        device.button("Close event details")
                        return label.removePrefix("Boundary ").also {
                            require(it.matches(Regex("[0-9a-f-]{36}"))) { "Not an exact UI boundary: $label" }
                        }
                    }
                    fun noResultDialog(device: NativeLobbyCreationTest.Device) {
                        assertTrue(device.driver.findElements(AppiumBy.androidUIAutomator(
                            NativeSelectors.selector("Proposed result"))).isEmpty(), "Paused history must not accept an unseen result")
                    }
                    fun atBoundary(device: NativeLobbyCreationTest.Device, expected: String) {
                        WebDriverWait(device.driver, Duration.ofSeconds(45)).until {
                            device.driver.findElements(AppiumBy.androidUIAutomator(
                                "new UiSelector().descriptionContains(\"boundary $expected\").selected(true)"))
                                .any { it.isDisplayed }
                        }
                        assertEquals(expected, boundary(device))
                    }
                    fun changedBoundary(device: NativeLobbyCreationTest.Device, previous: String): String =
                        WebDriverWait(device.driver, Duration.ofSeconds(45)).until {
                            boundary(device).takeIf { it != previous }
                        }
                    fun send(device: NativeLobbyCreationTest.Device, message: String) {
                        device.button("Chat"); device.field("Chat message draft").sendKeys(message)
                        device.hide(); device.button("Send"); device.text(message); device.named("Close chat").click()
                    }
                    fun review(device: NativeLobbyCreationTest.Device, glyph: String, expected: String) {
                        device.named("History").click(); device.button("History ▾")
                        if (device.driver.findElements(AppiumBy.androidUIAutomator(
                                NativeSelectors.selector("Show hidden branches"))).isNotEmpty()) device.button("Show hidden branches")
                        else device.driver.navigate().back()
                        device.named("$glyph, 1 events, review group end").click()
                        // Let the existing worker-backed graph publish its reviewed state before
                        // unmounting the dialog (offline Make current is intentionally disabled).
                        WebDriverWait(device.driver, Duration.ofSeconds(45)).until {
                            device.driver.findElements(AppiumBy.className("android.widget.ProgressBar")).none { it.isDisplayed }
                        }
                        device.driver.navigate().back(); device.text("Paused")
                        atBoundary(device, expected)
                    }
                    fun current(device: NativeLobbyCreationTest.Device) {
                        device.button("Return to current"); device.text("Live")
                    }
                    fun logout(device: NativeLobbyCreationTest.Device) {
                        // The gameplay drawer is used in BOTH orientations.
                        device.named("Open navigation").click(); device.text("Logout").click(); device.text("Forgot password?")
                    }
                    fun instrumentation(method: String, playId: String, rootId: String) {
                        val args = mutableListOf("shell", "am", "instrument", "-w", "-r", "-e", "class",
                            "org.asyncttwiz.android.IntegratedLifecycleTest#$method", "-e", "i31GameId", id("game"),
                            "-e", "i31PlayId", playId, "-e", "i31RootId", rootId)
                        if (method == "fullRecoveredTreeRetainsExactUiCreatedOffshootAndCurrentPath") {
                            for (key in listOf("life", "draw", "turn")) args += listOf("-e", "i31$key", id(key))
                        }
                        args += "org.asyncttwiz.android.test/androidx.test.runner.AndroidJUnitRunner"
                        val p = inputs[2]
                        val command = listOf(requireNotNull(p.getProperty("linuxAdb")), "-H", "127.0.0.1", "-P",
                            requireNotNull(p.getProperty("adbPort")), "-s", requireNotNull(p.getProperty("serial"))) + args
                        val process = ProcessBuilder(command).redirectErrorStream(true).start()
                        val output = process.inputStream.bufferedReader().readText()
                        val exited = process.waitFor(180, TimeUnit.SECONDS)
                        Files.writeString(third.resolve("i31-$method-instrumentation.txt"), output)
                        assertTrue(exited && process.exitValue() == 0 && output.contains("OK (1 test)") &&
                            !output.contains("FAILURES!!!"), output)
                    }
                    for (device in opponents) {
                        device.driver.rotate(ScreenOrientation.PORTRAIT)
                        if (phases.start == "rematch") device.ui.reopen("Native Initial Spark", "Starting player", description = false)
                        else table(device)
                    }

                    if (phases.includes("history")) {
                        val drawPlayer = id("drawPlayer")
                        opponents.forEach { it.named("Turn 1, $drawPlayer, beginning / untap") }
                        remember("root", boundary(first))
                        life(first, "Start First", 20, increase = true)
                        opponents.forEach { it.named("Start First, life 21") }
                        remember("life", changedBoundary(second, id("root"))) // Remote render, not prediction/receipt.
                        assertEquals(id("life"), boundary(first)); assertNotEquals(id("root"), id("life"))
                        second.button("‹ Previous"); second.text("Paused"); second.named("Start First, life 20")
                        assertEquals(id("root"), boundary(second))
                        send(first, "I31 chat while your history is paused")
                        second.named("Chat, 1 unread messages").click(); second.text("I31 chat while your history is paused")
                        second.named("Close chat").click(); second.named("Chat, 0 unread messages")
                        second.text("Paused"); assertEquals(id("root"), boundary(second))
                        send(first, "I31 unseen until this row is presented")
                        second.named("Chat, 1 unread messages") // Closing chat is not mark-all-read.
                        assertEquals(id("root"), boundary(second)); assertEquals(id("life"), boundary(first))
                        second.button("Continue playing from here"); second.button("Cancel continuation")
                        assertEquals(id("root"), boundary(second)); first.named("Start First, life 21")
                        second.button("Continue playing from here")
                        // A Library tap now logs View contents. Use the normal turn-player Draw
                        // action instead: one real continuation mutation, not an extra disclosure.
                        second.named("Table actions").click(); second.button("Draw one")
                        // Continuation normally dismisses the panel when review context changes;
                        // do not send Back to the game if that ordinary dismissal already happened.
                        WebDriverWait(second.driver, Duration.ofSeconds(45)).until {
                            second.driver.findElements(AppiumBy.androidUIAutomator(
                                NativeSelectors.selector("Choose phase/step"))).none { it.isDisplayed }
                        }
                        opponents.forEach { it.named("$drawPlayer Hand, 1 cards"); it.named("Start First, life 20") }
                        remember("draw", changedBoundary(first, id("life"))); atBoundary(second, id("draw"))
                        assertNotEquals(id("life"), id("draw"))
                        review(first, "Life", id("life")); first.named("Start First, life 21")
                        first.named("$drawPlayer Hand, 0 cards")
                        current(first); assertEquals(id("draw"), boundary(first))
                        first.capture("i31-chat-and-real-fork"); second.capture("i31-peer-fork")
                        phases.completed("history")
                    }

                    if (phases.includes("recovery")) {
                        // Third normal installation logs in to the same account, not a backend-created session.
                        tablet.driver.rotate(ScreenOrientation.PORTRAIT)
                        tablet.login("start-first@example.test", "Start First")
                        tablet.destination("Games"); table(tablet)
                        tablet.button("Download history")
                        review(tablet, "Life", id("life")); current(tablet)
                        assertEquals(id("draw"), boundary(tablet))
                        // Shared player-specific stack: phone action -> tablet Undo -> phone Redo.
                        first.named("Next step").click()
                        remember("turn", changedBoundary(first, id("draw")))
                        assertNotEquals(id("draw"), id("turn"))
                        atBoundary(second, id("turn")); atBoundary(tablet, id("turn"))
                        tablet.named("Undo").click()
                        opponents.forEach { atBoundary(it, id("draw")) }
                        first.named("Interaction mode").click(); first.button("Redo")
                        all.forEach { atBoundary(it, id("turn")) }
                        // Relogin discards local private replicas; reopening must download the full original tree.
                        logout(tablet); tablet.restart(); tablet.text("Forgot password?")
                        tablet.login("start-first@example.test", "Start First"); tablet.destination("Games"); table(tablet)
                        tablet.button("Download history")
                        review(tablet, "Life", id("life")); current(tablet)
                        tablet.restart(); table(tablet); assertEquals(id("turn"), boundary(tablet))
                        // Stop only the owned backend. No clear-data/reset/reseed at any recovery checkpoint.
                        control("pause")
                        try {
                            tablet.text("Disconnected · read-only")
                            review(tablet, "Life", id("life")); tablet.named("Start First, life 21")
                            review(tablet, "Draw", id("draw")); tablet.named("${id("drawPlayer")} Hand, 1 cards")
                            review(tablet, "Turn / step", id("turn"))
                            review(tablet, "Initial state", id("root")); tablet.named("Start First, life 20")
                            tablet.named("${id("drawPlayer")} Hand, 0 cards")
                            assertFalse(tablet.driver.findElement(By.xpath(NativeSelectors.actionXPath(
                                "Continue playing from here"))).isEnabled)
                            tablet.named("History").click()
                            assertFalse(tablet.driver.findElement(By.xpath(NativeSelectors.actionXPath("Make current"))).isEnabled)
                            tablet.driver.navigate().back(); tablet.capture("i31-complete-cached-offline-tree")
                        } finally { control("resume") }
                        current(tablet); assertEquals(id("turn"), boundary(tablet))
                        instrumentation("fullRecoveredTreeRetainsExactUiCreatedOffshootAndCurrentPath", id("firstPlay"), id("root"))
                        tablet.restart(); table(tablet); atBoundary(tablet, id("turn"))
                        // Background/foreground uses normal Android lifecycle, never a forged activity route.
                        tablet.button("‹ Previous"); val paused = boundary(tablet)
                        tablet.driver.pressKey(KeyEvent(AndroidKey.HOME))
                        send(second, "I31 background return keeps personal history paused")
                        tablet.driver.activateApp("org.asyncttwiz.android")
                        tablet.text("Paused"); assertEquals(paused, boundary(tablet)); current(tablet)
                        assertEquals(id("turn"), boundary(tablet))
                        phases.completed("recovery")
                    }

                    if (phases.includes("result")) {
                        current(second); second.button("‹ Previous"); second.text("Paused")
                        remember("beforeFinal", boundary(second))
                        life(first, "Start Second", 20, increase = false)
                        tablet.named("Start Second, life 19"); remember("finalBoard", changedBoundary(tablet, id("turn")))
                        first.named("Table actions").click(); first.button("Suggest my win")
                        first.text("Proposed result"); first.text("You proposed your win. Waiting for the other player to accept.")
                        assertTrue(first.driver.findElements(AppiumBy.androidUIAutomator(
                            NativeSelectors.selector("Accept and return to lobby"))).isEmpty())
                        first.button("Keep viewing board")
                        first.driver.navigate().back() // Close the still-open Table actions panel, not the game.
                        remember("suggestion", boundary(first))
                        assertNotEquals(id("finalBoard"), id("suggestion"))
                        noResultDialog(second); assertEquals(id("beforeFinal"), boundary(second))
                        // There is a Turn group between the paused Draw and the final Life group.
                        second.button("Next ›"); second.text("Paused"); assertEquals(id("turn"), boundary(second))
                        noResultDialog(second)
                        second.button("Next ›"); second.named("Start Second, life 19")
                        assertEquals(id("finalBoard"), boundary(second)); noResultDialog(second)
                        second.capture("i31-final-board-before-singleton-suggestion")
                        second.button("Next ›")
                        second.text("Proposed result")
                        second.text("Start First proposed their win. Accept to return both players to the lobby.")
                        second.capture("i31-opponent-result-dialog-after-final-board")
                        second.button("Accept and return to lobby")
                        for (device in all) {
                            device.scroll("Starting player"); device.text("Native Initial Spark"); device.text("Ready")
                            WebDriverWait(device.driver, Duration.ofSeconds(45)).until {
                                device.driver.findElements(AppiumBy.androidUIAutomator(NativeSelectors.selector("Not ready"))).size == 2
                            }
                            device.scroll("First prepared source"); device.scroll("Second prepared source")
                        }
                        val returned = control("observe"); val same = game(returned)
                        assertEquals(id("game"), same.get("id").asString)
                        assertEquals(id("createdAt"), same.get("createdAt").asString)
                        assertEquals("LOBBY", same.get("lifecycle").asString)
                        assertNotEquals(id("firstRound"), same.get("round").asString)
                        assertTrue(returned.getAsJsonArray("preparations").all { !it.asJsonObject.get("ready").asBoolean })
                        val copies = returned.getAsJsonArray("preparations").map { row ->
                            row.asJsonObject.let { it.get("actor").asString to it.get("deck").toString() }
                        }.sortedBy { it.first }.toString()
                        assertEquals(id("preparedCopies"), copies)
                        val acceptance = requests(returned, "acceptResult").single()
                        acceptedRequest(acceptance, id("firstPlay"))
                        assertEquals(id("suggestion"), acceptance.getAsJsonObject("command")
                            .getAsJsonObject("action").get("suggestionId").asString)
                        remember("acceptRequest", acceptance.getAsJsonObject("command").get("requestId").asString)
                        // No assertion of rematch starter retention: that policy is UNAPPROVED.
                        remember("returnedRound", same.get("round").asString)
                        phases.completed("result")
                    }

                    if (phases.includes("rematch")) {
                        first.ui.scroll("Starting player: Random", description = true).click()
                        first.button("Ready"); first.text("Withdraw Ready")
                        second.button("Ready")
                        for (device in all) {
                            device.named("Shared table"); device.named("Start First, life 20"); device.named("Start Second, life 20")
                            device.named("Start First Library, 6 cards"); device.named("Start Second Library, 5 cards")
                            device.named("Start First Hand, 0 cards"); device.named("Start Second Hand, 0 cards")
                        }
                        val fresh = control("observe"); val same = game(fresh)
                        remember("secondPlay", same.get("play").asString)
                        assertNotEquals(id("firstPlay"), id("secondPlay"))
                        assertEquals(id("game"), same.get("id").asString)
                        assertEquals(id("createdAt"), same.get("createdAt").asString)
                        assertEquals(id("returnedRound"), same.get("round").asString)
                        assertEquals(2, requests(fresh, "ready").count { it.getAsJsonObject("command")
                            .getAsJsonObject("scope").get("preparationId")?.asString == id("returnedRound") })
                        val original = play(fresh, id("firstPlay"))
                        assertEquals(id("suggestion"), original.get("currentNodeId").asString)
                        val singleton = original.getAsJsonObject("currentGroup")
                        assertEquals(id("suggestion"), singleton.get("firstNodeId").asString)
                        assertEquals(id("suggestion"), singleton.get("lastNodeId").asString)
                        assertEquals("1", singleton.get("count").asString)
                        val newPlay = play(fresh, id("secondPlay"))
                        remember("secondInitialPlay", newPlay.toString())
                        remember("secondRoot", newPlay.getAsJsonObject("root").get("id").asString)
                        all.forEach { assertEquals(id("secondRoot"), boundary(it)) }
                        tablet.restart(); table(tablet); assertEquals(id("secondRoot"), boundary(tablet))
                        assertEquals(newPlay, play(control("observe"), id("secondPlay"))) // No rerolled start/shuffle on restart.
                        phases.completed("rematch")
                    }

                    if (phases.includes("privacy")) {
                        // Actual MainActivity/AuthViewModel UI checkpoint, not an isolated fake controller.
                        tablet.driver.rotate(ScreenOrientation.LANDSCAPE); tablet.named("Shared table")
                        tablet.driver.rotate(ScreenOrientation.PORTRAIT)
                        instrumentation("realAuthViewModelLogoutClearsPrivateReviewAndDraftAcrossRotationAndClose",
                            id("secondPlay"), id("secondRoot"))
                        tablet.restart(); tablet.text("Forgot password?")
                        tablet.login("start-second@example.test", "Start Second")
                        tablet.destination("Games"); table(tablet); tablet.button("Chat")
                        assertFalse(tablet.driver.pageSource.contains("I31 private unsent draft"))
                        tablet.named("Close chat").click()
                        logout(tablet); tablet.login("start-first@example.test", "Start First")
                        tablet.destination("Games"); table(tablet)
                        assertEquals(id("secondRoot"), boundary(tablet))
                        phases.completed("privacy")
                    }

                    if (phases.includes("delete")) {
                        // Active shared Delete, never the separate unstarted-lobby Cancel path.
                        first.driver.navigate().back(); first.text("Invitations")
                        fun requestDelete() {
                            val row = first.driver.findElement(By.xpath(NativeSelectors.actionXPath("Native Initial Spark"))) as RemoteWebElement
                            first.driver.executeScript("mobile: longClickGesture", mapOf("elementId" to row.id, "duration" to 650))
                            first.button("Delete game"); first.text("Delete Native Initial Spark?")
                            first.text("This removes the shared game with Start Second for both players. There is no restore.")
                            assertTrue(first.driver.findElements(AppiumBy.androidUIAutomator(NativeSelectors.selector("Cancel game"))).isEmpty())
                        }
                        val beforeKeep = control("observe").getAsJsonArray("requests")
                        requestDelete(); first.button("Keep game")
                        first.button("Native Initial Spark"); assertEquals(id("secondRoot"), boundary(first))
                        assertEquals(beforeKeep, control("observe").getAsJsonArray("requests"))
                        first.driver.navigate().back(); first.text("Invitations")
                        requestDelete(); first.button("Delete game")
                        for (device in all) {
                            device.text("Invitations")
                            WebDriverWait(device.driver, Duration.ofSeconds(45)).until {
                                device.driver.findElements(AppiumBy.androidUIAutomator(NativeSelectors.selector("Native Initial Spark"))).isEmpty() &&
                                    device.driver.findElements(AppiumBy.accessibilityId("Shared table")).isEmpty()
                            }
                            device.capture("i31-active-shared-delete")
                            device.restart(); device.text("Invitations")
                            assertTrue(device.driver.findElements(AppiumBy.androidUIAutomator(NativeSelectors.selector("Native Initial Spark"))).isEmpty())
                        }
                        val terminal = control("observe"); val deleted = game(terminal)
                        assertEquals(id("game"), deleted.get("id").asString); assertEquals("DELETED", deleted.get("lifecycle").asString)
                        val deletion = requests(terminal, "deleteGame").single()
                        acceptedRequest(deletion, id("secondPlay"))
                        remember("deleteRequest", deletion.getAsJsonObject("command").get("requestId").asString)
                        assertTrue(requests(terminal, "cancelSetup").isEmpty())
                        assertEquals(id("acceptRequest"), requests(terminal, "acceptResult").single()
                            .getAsJsonObject("command").get("requestId").asString)
                        phases.completed("delete")
                    }
                    first.record("i31-journey.json", phases.finish() + mapOf("normalApks" to 3,
                        "identities" to ids, "requiredScopeComplete" to false,
                        "authoredScope" to "UI setup, chat during paused review, real fork, complete cached offline small tree, shared-account Undo/Redo, relogin/restart, background return, ordered result acceptance, prepared rematch, privacy UI, active Delete",
                        "remainingOwners" to mapOf("I31 supervisor" to "Independent verification/review; real OS notification route; ordinary overlapping lifecycle captured-request variants",
                            "I34-I36" to "Other e2e-strategy variants, final suite/compatibility/UI gates and independent capability review")))
                }
            }
        }
    }
}
