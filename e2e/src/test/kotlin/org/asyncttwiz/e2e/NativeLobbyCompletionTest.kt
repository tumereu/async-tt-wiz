package org.asyncttwiz.e2e

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.appium.java_client.AppiumBy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.openqa.selenium.ScreenOrientation
import org.openqa.selenium.remote.RemoteWebElement
import org.openqa.selenium.support.ui.WebDriverWait
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Match PreparationStore.read: untouched participants have no row and are not Ready. */
internal fun observedNativeReady(state: JsonObject, gameId: String, actorId: String): Boolean {
    val rows = state.getAsJsonArray("preparations").filter {
        it.asJsonObject["game"].asString == gameId && it.asJsonObject["actor"].asString == actorId
    }
    check(rows.size <= 1) { "Duplicate preparation rows for $gameId/$actorId" }
    return rows.singleOrNull()?.asJsonObject?.get("ready")?.asBoolean ?: false
}

/** Two ordinary APKs own every business mutation. SQL is a read-only identity/outcome oracle. */
@Tag("device")
class NativeLobbyCompletionTest {
    @Test fun preparationCopiesCancellationAndSharedInitialTable() {
        val directory = Path.of(requireNotNull(System.getenv("ATW_DEVICE_RUN")))
        val input = Properties().apply { Files.newBufferedReader(directory.resolve("runner.properties")).use(::load) }
        val peer = Path.of(Files.readString(directory.resolve("peer-run.txt")))
        val phases = NativePhases(directory, "lobby-completion", NativePhases.requested("setup"),
            listOf("setup", "readiness", "isolation", "table", "cancellation", "overlap", "instrumentation-setup"))
        fun control(operation: String): JsonObject {
            val process = ProcessBuilder("python3", input.getProperty("control"), operation).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(180, TimeUnit.SECONDS) && process.exitValue() == 0) { output }
            return JsonParser.parseString(output).asJsonObject
        }
        fun observe() = control("observe")
        fun game(state: JsonObject, name: String) = state.getAsJsonArray("games").single { it.asJsonObject["name"].asString == name }.asJsonObject
        fun preparations(state: JsonObject, id: String) = state.getAsJsonArray("preparations").filter { it.asJsonObject["game"].asString == id }
        fun deck(state: JsonObject, id: String, actor: String) = preparations(state, id).single { it.asJsonObject["actor"].asString == actor }.asJsonObject["deck"]
        NativeLobbyCreationTest.Device(directory, directory.resolve("runner.properties")).use { first ->
            Files.copy(directory.resolve("session.json"), Path.of(input.getProperty("metadataRun")).resolve("session.json"))
            NativeLobbyCreationTest.Device(peer, peer.resolve("runner.properties")).use { second ->
                val peerInput = Properties().apply { Files.newBufferedReader(peer.resolve("runner.properties")).use(::load) }
                Files.copy(peer.resolve("session.json"), Path.of(peerInput.getProperty("metadataRun")).resolve("session.json"))
                fun waitFor(ui: NativeLobbyCreationTest.Device, predicate: () -> Boolean) =
                    WebDriverWait(ui.driver, Duration.ofSeconds(60)).until { predicate() }
                fun settled(ui: NativeLobbyCreationTest.Device) = waitFor(ui) {
                    !ui.driver.pageSource.contains("Checking game outcome…") &&
                        observe().getAsJsonArray("requests").all { !it.asJsonObject["result"].isJsonNull }
                }
                fun portrait(ui: NativeLobbyCreationTest.Device) { ui.driver.rotate(ScreenOrientation.PORTRAIT) }
                fun games(ui: NativeLobbyCreationTest.Device) { portrait(ui); ui.destination("Games"); ui.text("Invitations") }
                fun open(ui: NativeLobbyCreationTest.Device, name: String) { games(ui); ui.button(name) }
                fun create(name: String) {
                    portrait(first); first.destination("Contacts"); first.button("Invite")
                    first.replaceName(name); first.button("Create & invite"); first.text("Waiting for Completion Second to accept.")
                }
                fun paste(ui: NativeLobbyCreationTest.Device, value: String) {
                    ui.driver.findElement(AppiumBy.className("android.widget.EditText")).sendKeys(value); ui.hide()
                }
                fun import(ui: NativeLobbyCreationTest.Device, name: String, list: String) {
                    ui.button("Choose deck"); ui.button("Import deck"); paste(ui, list); ui.button("Review import")
                    ui.scroll("Deck name"); paste(ui, name); ui.button("Save deck")
                    ui.text("Use selected deck"); ui.button(name); ui.button("Use selected deck"); ui.text("Change deck"); settled(ui)
                }
                fun starter(ui: NativeLobbyCreationTest.Device, name: String) {
                    ui.ui.scroll("Starting player: $name", description = true).click(); settled(ui)
                }
                fun ready(ui: NativeLobbyCreationTest.Device) { ui.button("Ready"); ui.text("Withdraw Ready"); settled(ui) }
                fun selected(ui: NativeLobbyCreationTest.Device, name: String) {
                    ui.button("Choose deck"); ui.button(name); ui.button("Use selected deck"); ui.text("Change deck"); settled(ui)
                }
                fun cancel(ui: NativeLobbyCreationTest.Device) { ui.button("Cancel"); ui.text("Cancel game?"); ui.button("Cancel game") }
                fun gone(ui: NativeLobbyCreationTest.Device, name: String) {
                    ui.text("Invitations"); waitFor(ui) { !ui.driver.pageSource.contains(name) }
                }
                fun hold(ui: NativeLobbyCreationTest.Device, name: String) {
                    val row = ui.driver.findElement(AppiumBy.xpath(NativeSelectors.actionXPath(name))) as RemoteWebElement
                    ui.driver.executeScript("mobile: longClickGesture", mapOf("elementId" to row.id, "duration" to 1100))
                    ui.text("Cancel game"); assertFalse(ui.driver.pageSource.contains("Choose deck"))
                }
                fun editSource(ui: NativeLobbyCreationTest.Device, name: String, replacement: String, delete: Boolean) {
                    portrait(ui); ui.destination("Manage decks"); ui.button(name); ui.button("Edit"); ui.button("Rename")
                    ui.driver.findElement(AppiumBy.className("android.widget.EditText")).clear(); paste(ui, replacement)
                    ui.button("Save name"); ui.text(replacement); ui.button("Edit"); ui.button("Import decklist")
                    ui.text("Replace both sections with this list.")
                    paste(ui, "1 Feltwing Atlas"); ui.button("Review import"); ui.button("Save deck"); ui.text("Mainboard · 1")
                    ui.capture("source-overwritten-$replacement")
                    if (delete) { ui.button("Edit"); ui.button("Delete saved deck"); ui.button("Delete saved deck"); ui.scroll("Refresh saved decks") }
                }
                val mainName = "Native Completion Spark"
                first.driver.rotate(ScreenOrientation.PORTRAIT); second.driver.rotate(ScreenOrientation.PORTRAIT)
                if (phases.includes("setup")) {
                    first.register("completion-first@example.test", "Completion First"); first.login("completion-first@example.test", "Completion First")
                    second.register("completion-second@example.test", "Completion Second"); second.login("completion-second@example.test", "Completion Second")
                    for (ui in listOf(first, second)) {
                        ui.button("Manage decks"); ui.button("Check for updates")
                        ui.button("Download card records"); ui.text("Card records are up to date.")
                    }
                    first.destination("Contacts"); first.field("Search usernames").sendKeys("Completion Sec"); first.hide(); first.button("Search")
                    first.button("Add contact"); first.text("Added")
                    create(mainName)
                    import(first, "Completion source A", "Deck\n4 Coppercloud Surveyor (ZPA) 1\n2 Amberglass Chorus\nSideboard\n1 Feltwing Atlas")
                    first.button("Prepare deck"); first.named("Move one Coppercloud Surveyor from Mainboard").click()
                    first.text("Mainboard · 5"); first.text("Sideboard · 2"); first.named("Close preparation").click()
                    starter(first, "Completion First"); ready(first)
                    phases.completed("setup")
                }
                val initial = observe(); val initialGame = game(initial, mainName); val id = initialGame["id"].asString
                val actorA = initial.getAsJsonArray("players").single { it.asJsonObject["username"].asString == "Completion First" }.asJsonObject["id"].asString
                val actorB = initial.getAsJsonArray("players").single { it.asJsonObject["username"].asString == "Completion Second" }.asJsonObject["id"].asString
                fun readyState(a: Boolean, b: Boolean) {
                    waitFor(first) {
                        val state = observe()
                        listOf(actorA to a, actorB to b).all { (actor, value) ->
                            observedNativeReady(state, id, actor) == value
                        }
                    }
                    val state = observe(); assertEquals(id, game(state, mainName)["id"].asString)
                    for ((actor, value) in listOf(actorA to a, actorB to b))
                        assertEquals(value, observedNativeReady(state, id, actor))
                    assertEquals("LOBBY", game(state, mainName)["lifecycle"].asString)
                }
                if (phases.includes("readiness")) {
                    val copy = deck(initial, id, actorA)
                    games(second); second.text(mainName); second.button("Decline"); second.text("No pending invitations.")
                    first.button("Reinvite"); first.text("Waiting for Completion Second to accept."); settled(first)
                    val reinvited = observe(); assertEquals(copy, deck(reinvited, id, actorA)); readyState(true, false)
                    assertNotEquals(initialGame["invitation"], game(reinvited, mainName)["invitation"])
                    second.button("Accept"); second.text("Select your deck to get ready."); second.scroll("✓ Ready")
                    readyState(true, false); assertEquals(copy, deck(observe(), id, actorA))
                    import(second, "Completion source B", "Deck\n3 Quiet Lattice Bolt\n2 Feltwing Atlas\nSideboard\n2 Velvet Prism Engine")
                    // Every changing actor's Ready is checked while the other player remains Ready.
                    starter(second, "Completion Second"); readyState(true, false)
                    first.button("Change deck"); first.button("Completion source A"); first.button("Use selected deck"); settled(first); readyState(false, false)
                    ready(first)
                    first.button("Prepare deck"); first.named("Move one Coppercloud Surveyor from Mainboard").click()
                    first.text("Mainboard · 5"); first.named("Close preparation").click(); settled(first); readyState(false, false)
                    ready(first); starter(first, "Completion First"); readyState(false, false); ready(first)
                    first.button("Withdraw Ready"); first.text("Ready"); settled(first); readyState(false, false)
                    ready(second); readyState(false, true)
                    second.button("Change deck"); second.button("Completion source B"); second.button("Use selected deck"); settled(second); readyState(false, false)
                    ready(second); starter(second, "Completion Second"); readyState(false, false); ready(second)
                    first.button("Change deck"); first.button("Completion source A"); first.button("Use selected deck"); settled(first); readyState(false, true)
                    first.button("Prepare deck"); first.named("Move one Coppercloud Surveyor from Mainboard").click()
                    first.text("Mainboard · 5"); first.named("Close preparation").click(); settled(first); readyState(false, true)
                    starter(first, "Completion First"); readyState(false, true)
                    second.button("Withdraw Ready"); second.text("Ready"); settled(second); readyState(false, false)
                    ready(first); readyState(true, false)
                    first.driver.navigate().back(); first.text("Invitations"); first.button(mainName); first.text("Withdraw Ready"); readyState(true, false)
                    control("disconnect-first"); first.text("Withdraw Ready"); readyState(true, false)
                    first.capture("ready-disconnected-no-withdrawal"); control("reconnect-first"); settled(first); first.text("Withdraw Ready")
                    readyState(true, false)
                    for (orientation in listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE)) {
                        for ((index, ui) in listOf(first, second).withIndex()) {
                            ui.driver.rotate(orientation); ui.text(mainName); ui.scroll("✓ Ready"); ui.scroll("Not ready")
                            ui.capture("accepted-preparation-$index-${orientation.name.lowercase()}")
                        }
                    }
                    phases.completed("readiness")
                }
                if (phases.includes("isolation")) {
                    val before = observe(); val copies = preparations(before, id); val definitions = before["definitions"]
                    editSource(first, "Completion source A", "Edited A", delete = true)
                    open(first, mainName); first.scroll("Completion source A"); first.scroll("Mainboard · 5"); first.text("Withdraw Ready")
                    portrait(second); second.scroll("Completion source A"); second.scroll("Mainboard · 5"); second.capture("remote-retained-deleted-source")
                    val after = observe(); assertEquals(copies, preparations(after, id)); assertEquals(definitions, after["definitions"])
                    assertFalse(after.getAsJsonArray("sources").any { it.asJsonObject["id"] == deck(before, id, actorA).asJsonObject["sourceId"] })
                    readyState(true, false); phases.completed("isolation")
                }
                fun table(ui: NativeLobbyCreationTest.Device) {
                    ui.named("Shared table"); ui.text(mainName)
                    ui.named("Completion First Library, 5 cards"); ui.named("Completion Second Library, 5 cards")
                    for (name in listOf("Completion First", "Completion Second")) {
                        ui.named("$name, life 20"); ui.named("$name Hand, 0 cards"); ui.named("$name Battlefield, 0 cards")
                    }
                    assertFalse(ui.driver.pageSource.contains("Sideboard"))
                }
                if (phases.includes("table")) {
                    portrait(second); second.button("Ready"); table(second); table(first)
                    val started = observe(); val play = started.getAsJsonArray("plays").single().asJsonObject
                    assertEquals(id, play["gameId"].asString); assertEquals(initialGame["createdAt"], game(started, mainName)["createdAt"])
                    assertEquals(play["id"], game(started, mainName)["play"])
                    assertEquals(initialGame["round"], play["preparationRound"])
                    val expectedCards = started.getAsJsonArray("preparedCards").filter {
                        it.asJsonObject["game"].asString == id && it.asJsonObject["section"].asString == "MAINBOARD"
                    }.associate { it.asJsonObject["id"].asString to (it.asJsonObject["definition"] to it.asJsonObject["owner"]) }
                    val actualCards = play.getAsJsonObject("board").getAsJsonArray("cards").associate {
                        it.asJsonObject["id"].asString to (it.asJsonObject["definition"] to it.asJsonObject["owner"])
                    }
                    assertEquals(expectedCards, actualCards)
                    assertEquals(expectedCards.values.map { it.first.asString }.toSet(), play.getAsJsonArray("definitions").map { it.asJsonObject["digest"].asString }.toSet())
                    assertEquals(10, play.getAsJsonObject("board").getAsJsonArray("containers")
                        .filter { it.asJsonObject["zone"].asString == "LIBRARY" }.sumOf { it.asJsonObject.getAsJsonArray("cards").size() })
                    for (orientation in listOf(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE)) for ((index, ui) in listOf(first, second).withIndex()) {
                        ui.driver.rotate(orientation); table(ui); ui.capture("shared-table-$index-${orientation.name.lowercase()}")
                    }
                    editSource(second, "Completion source B", "Edited B", delete = true)
                    open(second, mainName); table(second); table(first)
                    val after = observe(); assertEquals(started["plays"], after["plays"])
                    assertEquals(preparations(started, id), preparations(after, id)); assertEquals(started["definitions"], after["definitions"])
                    assertFalse(after.getAsJsonArray("sources").any { it.asJsonObject["id"] == deck(started, id, actorB).asJsonObject["sourceId"] })
                    first.record("started-identities.json", mapOf("game" to game(after, mainName), "play" to play, "preparations" to preparations(after, id)))
                    phases.completed("table")
                }
                if (phases.includes("cancellation")) {
                    val pending = "Native Pending Cancel"; create(pending); games(second); second.text(pending)
                    val pendingId = game(observe(), pending)["id"]
                    games(first); val beforeDismissals = observe()["requests"]
                    hold(first, pending); first.button("Cancel game"); first.button("Keep game")
                    hold(first, pending); first.button("Cancel game"); first.named("Close cancellation").click()
                    hold(first, pending); first.button("Cancel game"); first.driver.navigate().back(); first.text("Invitations")
                    assertEquals(beforeDismissals, observe()["requests"])
                    first.button(pending); first.button("Cancel"); first.text("Cancel game?")
                    first.driver.rotate(ScreenOrientation.LANDSCAPE); first.capture("cancel-dialog-landscape")
                    first.driver.navigate().back(); first.text(pending)
                    portrait(first); first.button("Cancel"); first.capture("cancel-dialog-portrait")
                    control("pause"); try {
                        first.button("Cancel game"); first.text("Checking game outcome…"); first.capture("cancel-uncertain-not-success")
                        control("recover-original")
                    } finally { control("resume") }
                    gone(first, pending); gone(second, pending)
                    val cancelled = observe(); assertEquals("CANCELLED", game(cancelled, pending)["lifecycle"].asString)
                    assertEquals(pendingId, game(cancelled, pending)["id"])
                    val cancellations = cancelled.getAsJsonArray("requests").filter {
                        it.asJsonObject["game"] == pendingId && it.asJsonObject.getAsJsonObject("command").getAsJsonObject("action")["type"].asString == "cancelSetup"
                    }
                    assertEquals(1, cancellations.size)
                    val recoveredOriginal = control("original-evidence").getAsJsonObject("original")
                    assertEquals(recoveredOriginal, cancellations.single())
                    assertEquals("accepted", recoveredOriginal.getAsJsonObject("result")["outcome"].asString)
                    first.capture("pending-cancel-recovered"); second.capture("remote-pending-invitation-removed")
                    for ((name, preparation) in listOf("Native Picker Cancel" to false, "Native Preparation Cancel" to true)) {
                        create(name); games(second); second.button("Accept"); second.text("Select your deck to get ready.")
                        if (preparation) {
                            import(first, "Cancel source", "2 Coppercloud Surveyor (ZPA) 1\nSideboard\n1 Velvet Prism Engine")
                            first.button("Prepare deck"); first.text("Mainboard · 2")
                        } else { first.button("Choose deck"); first.text("No saved decks yet.") }
                        second.driver.rotate(ScreenOrientation.LANDSCAPE); second.button("Cancel"); second.text("Cancel game?")
                        second.button("Cancel game"); gone(second, name); gone(first, name)
                        assertFalse(first.driver.pageSource.contains(if (preparation) "Close preparation" else "Close deck picker"))
                        first.capture("remote-context-cleared-$preparation")
                        first.restart(); games(first); gone(first, name); second.restart(); games(second); gone(second, name)
                    }
                    phases.completed("cancellation")
                }
                if (phases.includes("overlap")) {
                    val name = "Native Cancel Start Overlap"; create(name); selected(first, "Cancel source")
                    games(second); second.button("Accept"); second.text("Select your deck to get ready.")
                    import(second, "Overlap source B", "3 Quiet Lattice Bolt")
                    starter(first, "Completion First"); ready(first); first.button("Cancel"); first.text("Cancel game?")
                    val executor = Executors.newFixedThreadPool(2)
                    try {
                        val cancelClick = executor.submit { first.button("Cancel game") }
                        val readyClick = executor.submit { second.button("Ready") }
                        cancelClick.get(60, TimeUnit.SECONDS); readyClick.get(60, TimeUnit.SECONDS)
                    } finally { executor.shutdownNow() }
                    settled(first); settled(second)
                    waitFor(first) {
                        val state = observe(); val overlap = game(state, name)
                        overlap["lifecycle"].asString != "LOBBY" && state.getAsJsonArray("requests")
                            .filter { it.asJsonObject["game"] == overlap["id"] }.all { !it.asJsonObject["result"].isJsonNull }
                    }
                    val state = observe(); val overlap = game(state, name); val lifecycle = overlap["lifecycle"].asString
                    assertTrue(lifecycle in listOf("CANCELLED", "ACTIVE"))
                    val requests = state.getAsJsonArray("requests").filter { it.asJsonObject["game"] == overlap["id"] }
                    assertTrue(requests.any { it.asJsonObject.getAsJsonObject("command").getAsJsonObject("action")["type"].asString == "cancelSetup" })
                    assertTrue(requests.any { it.asJsonObject.getAsJsonObject("command").getAsJsonObject("action")["type"].asString == "ready" })
                    if (lifecycle == "CANCELLED") { gone(first, name); gone(second, name) }
                    else { first.named("Shared table"); second.named("Shared table") }
                    first.record("ordinary-overlap.json", mapOf("game" to overlap, "requests" to requests, "observedLifecycle" to lifecycle))
                    first.capture("ordinary-overlap-first"); second.capture("ordinary-overlap-second")
                    phases.completed("overlap")
                }
                if (phases.includes("instrumentation-setup")) {
                    create("Native Cancel Spark"); games(second); second.text("Native Cancel Spark"); games(first)
                    phases.completed("instrumentation-setup")
                }
                first.record("journey.json", phases.finish() + mapOf("normalApks" to 2,
                    "remaining" to "Separate actual Compose accessibility/hold test follows on the last UI-created game. I28/I31 returned rounds; I34-I36 full-suite/compatibility/interface gates; first independent B3 review."))
            }
        }
    }
}
