package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * The draft bots' card ratings for the owner's gallery (owner, 2026-11-25): the warning on bots that pick without them
 * (DRF-314) in setup and in a started pod, held; a rated set's setup without it; About's credit (LOB-275); and the bot
 * debug view's rating parts (DRF-292, DRF-312, DRF-313); a Through the Omenpaths pod (its cards printed under other
 * names, LOB-150) and a Marvel's Spider-Man pod, whose bots borrow its ratings. Invented cards and ratings; nothing is sent.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-xhdpi")
class BotRatingsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val worker = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = Unit // Leave network work pending.
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> error("Unexpected network call: ${method.name}") } as T
    private val controllers = mutableListOf<PodController>()
    private fun controller(filters: PodCardFilters = PodCardFilters()) =
        PodController(unused<Pods>(), unused<Cubes>(), { "token" }, worker, { it() }, filters = filters).also { controllers += it }

    @After fun close() {
        controllers.forEach { it.close() }
        DraftBotPreferences(org.asyncttwiz.appui.AppPlatform.current).enabled = false
    }

    private val alice = AccountIdentity("alice", "Alice")
    private val bob = AccountIdentity("bob", "Bob")
    private val cube = PodSource("Runes", 540)
    private val unratedSet = PodSource(kind = PodSourceKind.SET, setCode = "XLN", setName = "Old Expedition",
        boosterKind = SetBoosterKind.RECIPE_BASED, boosterName = "draft", packSets = listOf("XLN"))
    private val ratedSet = PodSource(kind = PodSourceKind.SET, setCode = "TST", setName = "Test Set",
        boosterKind = SetBoosterKind.RECIPE_BASED, boosterName = "play", packSets = listOf("TST"), botRatings = true)

    private fun detail(phase: PodPhase, settings: DraftSettings, members: List<PodMember>, source: PodSource, name: String = "Bot night",
        definitions: List<SubmittedDefinition> = emptyList(), draft: DraftPrivateView? = null) =
        PodDetail("pod", name, phase, alice, settings, source, DraftSupply(135, 540), members, emptyList(), draftAttemptId = "attempt",
            definitions = definitions, updatedAt = "2026-11-25T00:00:00Z", draft = draft,
            draftWait = PodDraftWait.Picks.takeIf { phase == PodPhase.DRAFTING })

    private fun show(pods: PodController, detail: PodDetail) {
        pods.seed(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.DRAFT))
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            PodsDestination(pods, alice.accountId, {}, null, {}, mainMenu = {})
        } } } }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    private fun shot(name: String, caption: String) = captureShot("$SCREENSHOTS/draft/$name.png", caption)

    // DRF-314 (owner, 2026-11-25): a cube pod's bots pick without card ratings: each bot row, Add bot and the bots in the strip
    // carry the amber warning sign; holding one says why
    @Test @Config(qualifiers = "w412dp-h1300dp-port-xhdpi") fun setupWarnsOfBotsWithoutRatings() {
        show(controller(), detail(PodPhase.SETUP, DraftSettings(bots = 2), listOf(PodMember(alice), PodMember(bob)), cube))
        val tip = "Bots pick less well here: there are no card ratings for cubes."
        compose.onNodeWithTag("pod-bot-warning-1").performScrollTo().assertContentDescriptionEquals(tip)
        compose.onNodeWithTag("pod-bot-warning-2").assertExists()
        compose.onNodeWithTag("pod-add-bot-warning").assertExists()
        compose.onAllNodesWithTag("pod-bot-warning-avatar", useUnmergedTree = true).assertCountEquals(2)
        shot("bot-warning-setup", "The creator's setup of a Booster pod from a cube with Bob and two bot seats: an amber warning sign on " +
            "Bot 1's and Bot 2's avatars in the strip at the top, before the ✕ on each bot row, and left of Add bot.")
        // Held, not released: the tooltip shows while the finger stays down
        compose.onNodeWithTag("pod-bot-warning-1").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        settle()
        compose.onNodeWithText(tip).assertIsDisplayed()
        shot("bot-warning-hold", "The creator holds the warning sign on Bot 1's row in the setup above: a tooltip says \"$tip\"")
    }

    // DRF-314, DRF-312: a pod of a set with card ratings: its bots pick by them, so no warning anywhere
    @Test @Config(qualifiers = "w412dp-h1300dp-port-xhdpi") fun aRatedSetsSetupHasNoWarning() {
        show(controller(), detail(PodPhase.SETUP, DraftSettings(bots = 2), listOf(PodMember(alice), PodMember(bob)), ratedSet))
        compose.onNodeWithTag("pod-add-bot").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("pod-bot-warning-1").assertDoesNotExist()
        compose.onNodeWithTag("pod-add-bot-warning").assertDoesNotExist()
        compose.onAllNodesWithTag("pod-bot-warning-avatar", useUnmergedTree = true).assertCountEquals(0)
        shot("bot-warning-rated-set", "The creator's setup of a Booster pod from Test Set, whose cards have ratings, with Bob and two bot " +
            "seats: no warning sign on the bots or beside Add bot.")
    }

    // DRF-314: a started pod of a set without card ratings: the bots' avatars in the header carry the warning; holding one says why
    @Test fun aStartedPodWarnsOnItsBots() {
        val members = listOf(PodMember(alice, packsWaiting = 1), PodMember(bob, packsWaiting = 1)) +
            (1..2).map { PodMember(PodBots.identity(it), picksMade = 1, bot = true) }
        val definitions = listOf(SubmittedDefinition("d0", CardDefinition("Dawn Cub", listOf(DefinitionFace("f", "Dawn Cub",
            typeLine = "Creature — Bear", manaCost = "{1}{W}", power = "2", toughness = "2", scryfallId = placeholderPrinting("Dawn Cub"))))))
        val head = DraftPrivatePack("pack", 1, (0..5).map { DraftCopy("c$it", "d0") }, 2, PodBots.id(2))
        show(controller(), detail(PodPhase.DRAFTING, DraftSettings(bots = 2), members, unratedSet, definitions = definitions,
            draft = DraftPrivateView("alice", head, 0, listOf(DraftCopy("p0", "d0")), round = 1, rounds = 3)))
        val tip = "Bots pick less well here: there are no card ratings for this set."
        compose.onAllNodesWithTag("pod-bot-warning-avatar", useUnmergedTree = true).assertCountEquals(2)
        shot("bot-warning-started", "Alice's second pick in a started Booster pod of a set without card ratings, with Bob and two bots: " +
            "the header's avatars show Bot 1 and Bot 2 each with a small amber warning sign at the top right.")
        compose.onAllNodesWithTag("pod-bot-warning-avatar", useUnmergedTree = true)[0].performTouchInput { longClick() }
        settle()
        compose.onNodeWithText(tip).assertIsDisplayed()
        shot("bot-warning-started-hold", "Alice holds Bot 1's avatar in the header above: a tooltip says \"$tip\"")
    }

    // LOB-275: About & account credits 17Lands for the card ratings, above the version line
    @Test fun aboutCreditsTheRatings() {
        compose.setContent { ScreenTheme { SettingsContent(null, "credit", {}, null, null,
            versionText = appVersionText("0.0.0", 0, "debug", "0000000")) } }
        compose.onNodeWithTag("logout").performScrollTo()
        compose.onNodeWithTag("card-ratings-credit").assertIsDisplayed()
        settle()
        captureShot("$SCREENSHOTS/settings/about-credit.png", "Settings scrolled to About & account: under Show bot picks (a test build) " +
            "the line \"$CARD_RATINGS_CREDIT\" above the version line and Log out.")
    }

    // DRF-292, DRF-312, DRF-313: Show bot picks in a pod of a set with card ratings: rated cards score by their rating, a card
    // without one by the heuristic rescaled, and a strong card one pip off the bot's colours is a splash
    @Test fun botPicksByRatings() {
        fun card(n: Int, name: String, cost: String, type: String, text: String = "", power: String = "", toughness: String = "") =
            SubmittedDefinition("bot-$n", CardDefinition(name, listOf(DefinitionFace("face", name, typeLine = type, manaCost = cost,
                rulesText = text, power = power, toughness = toughness, scryfallId = placeholderPrinting(name))), printingId = "p-$n"))
        val cards = listOf(
            card(0, "Night Terror", "{3}{B}", "Creature — Horror", "Flying", "3", "3"),
            card(1, "Quick Cinder", "{R}", "Instant", "Quick Cinder deals 3 damage to any target."),
            card(2, "Skyward Owl", "{1}{W}", "Creature — Bird", "Flying", "1", "1"),
            card(3, "Tide Scholar", "{2}{U}", "Creature — Merfolk Wizard", "When this creature enters, draw a card.", "1", "3"),
            card(4, "Plodding Ox", "{2}{G}", "Creature — Ox", power = "2", toughness = "2"),
            card(5, "Shrine Path", "", "Land", "{T}: Add {C}."),
            card(6, "Dawn Cub", "{1}{W}", "Creature — Bear", power = "2", toughness = "2"),
            card(7, "Mist Adept", "{1}{U}", "Creature — Human Wizard", power = "1", toughness = "2"),
        )
        // Quick Cinder has no rating
        val ratings = mapOf("Night Terror" to 641, "Skyward Owl" to 571, "Tide Scholar" to 589, "Plodding Ox" to 528, "Shrine Path" to 512,
            "Dawn Cub" to 548, "Mist Adept" to 553) + (0 until 20).associate { "Filler $it" to 520 + 4 * it }
        val filters = PodCardFilters({ emptyMap() }, readRatings = { code -> if (code == "TST") ratings else emptyMap() })
        DraftBotPreferences(org.asyncttwiz.appui.AppPlatform.current).enabled = true
        val head = DraftPrivatePack("pack-bot", 1, (0..5).map { DraftCopy("b$it", "bot-$it") }, 15, "bob")
        val pool = (0..13).map { DraftCopy("w$it", if (it % 2 == 0) "bot-6" else "bot-7") }
        show(controller(filters), detail(PodPhase.DRAFTING, DraftSettings.booster(), listOf(PodMember(alice, packsWaiting = 1),
            PodMember(bob)), ratedSet, definitions = cards, draft = DraftPrivateView("alice", head, 0, pool, round = 2, rounds = 3)))
        compose.onNodeWithTag("draft-bot-frame-b0").assertExists()
        compose.onNodeWithTag("draft-bot-b0").performClick()
        settle()
        compose.onNodeWithText("Splash B -4").assertIsDisplayed()
        compose.onNodeWithText("Rating (GIH WR 64.1%)", substring = true).assertIsDisplayed()
        shot("bot-picks-splash", "Show bot picks in a pod of a set with card ratings, Alice's first pick of pack 2 after white and " +
            "blue picks: the bot's pick is Night Terror, one black symbol off her colours but among the set's best rated, and a tap on " +
            "its score lists its rating and \"Splash B -4\" in place of the full off-colour penalty.")
        compose.onNodeWithTag("draft-bot-b0").performClick()
        settle()
        compose.onNodeWithTag("draft-bot-b1").performClick()
        settle()
        compose.onNodeWithText("No rating: base 40").assertIsDisplayed()
        shot("bot-picks-unrated", "The same pick: Quick Cinder has no rating, so a tap on its score lists the bot's own reckoning " +
            "(\"No rating: base 40\", removal) rescaled to the ratings, and the full off-colour penalty for red.")
    }

    private val omenpaths = PodSource(kind = PodSourceKind.SET, setCode = "OM1", setName = "Through the Omenpaths",
        boosterKind = SetBoosterKind.APPROXIMATE, packSets = listOf("OM1"), botRatings = true)
    private val spiderMan = PodSource(kind = PodSourceKind.SET, setCode = "SPM", setName = "Marvel's Spider-Man",
        boosterKind = SetBoosterKind.RECIPE_BASED, boosterName = "play", packSets = listOf("SPM"), botRatings = true)

    // DRF-312, DRF-314: a Through the Omenpaths pod: the set has card ratings (its Pick Two Draft data), so no warning
    @Test @Config(qualifiers = "w412dp-h1300dp-port-xhdpi") fun anOmenpathsPodsSetup() {
        show(controller(), detail(PodPhase.SETUP, DraftSettings(bots = 2), listOf(PodMember(alice), PodMember(bob)), omenpaths, name = "Omenpaths night"))
        compose.onNodeWithText("Through the Omenpaths", substring = true).assertExists()
        compose.onNodeWithTag("pod-add-bot").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("pod-add-bot-warning").assertDoesNotExist()
        compose.onAllNodesWithTag("pod-bot-warning-avatar", useUnmergedTree = true).assertCountEquals(0)
        shot("omenpaths-setup", "The creator's setup of a Booster pod from Through the Omenpaths with Bob and two bot seats: the set " +
            "has card ratings, so no warning sign on the bots or beside Add bot.")
    }

    // DRF-312, LOB-150, TBL-138: a Through the Omenpaths pick: its cards show the names its printings give them; a double-faced card
    // whose printing names MTGJSON doesn't give keeps its Oracle names in the app while its picture prints the other name
    @Test fun anOmenpathsPick() {
        fun card(n: Int, oracle: String, printed: String?, cost: String, type: String, power: String = "", toughness: String = "") =
            SubmittedDefinition("om-$n", CardDefinition(oracle, listOf(DefinitionFace("face", oracle, typeLine = type, manaCost = cost,
                power = power, toughness = toughness, printedName = printed, scryfallId = placeholderPrinting(printed ?: oracle))), printingId = "om-$n"))
        // The picture prints the other names, as the set's real pictures do
        val picture = placeholderPrinting("Tessa, Weblight Tinkerer", "Tessa, Silk-Wing Vanguard")
        val double = SubmittedDefinition("om-5", CardDefinition("Lab Prodigy // Web Hero", listOf(
            DefinitionFace("front", "Lab Prodigy", listOf("back"), "Legendary Creature — Human Scientist", "{1}{W}", power = "0", toughness = "1",
                scryfallId = picture),
            DefinitionFace("back", "Web Hero", listOf("front"), "Legendary Creature — Spider Human Hero", power = "3", toughness = "3",
                scryfallId = picture)),
            printingId = "om-5", layout = "transform"))
        val cards = listOf(
            card(0, "Web Warden", "Silkchute Ranger", "{1}{G}", "Creature — Spider Hero", "2", "2"),
            card(1, "City Rooftops", null, "", "Land"),
            card(2, "Daily Bugle Rush", "Gossip Courier", "{R}", "Creature — Human Citizen", "1", "1"),
            card(3, "Shocking Grip", "Coil of Storms", "{1}{U}", "Instant"),
            card(4, "Night Crawler Venom", "Tethered Malice", "{2}{B}", "Creature — Symbiote", "3", "2"),
            double,
        )
        val head = DraftPrivatePack("pack-om", 1, (0..5).map { DraftCopy("o$it", "om-$it") }, 10, "bob")
        show(controller(), detail(PodPhase.DRAFTING, DraftSettings.booster(), listOf(PodMember(alice, packsWaiting = 1), PodMember(bob)),
            omenpaths, name = "Omenpaths night", definitions = cards, draft = DraftPrivateView("alice", head, 0, emptyList(), round = 1, rounds = 3)))
        compose.onNodeWithTag("pod-pick-card-o0").assertContentDescriptionEquals("Silkchute Ranger")
        compose.onNodeWithTag("pod-pick-card-o5").assertContentDescriptionEquals("Lab Prodigy // Web Hero")
        shot("omenpaths-pick", "Alice's tenth pick in a Booster pod of Through the Omenpaths (invented cards): each card shows the name " +
            "its printing gives it (Silkchute Ranger for the Oracle card Web Warden), as its picture does; the double-faced card at the end " +
            "has no such name in the card data, so the app calls it Lab Prodigy // Web Hero while its picture prints Tessa, Weblight Tinkerer.")
    }

    // DRF-312, DRF-314: a Marvel's Spider-Man pod: its bots borrow Through the Omenpaths' ratings, so no warning
    @Test @Config(qualifiers = "w412dp-h1300dp-port-xhdpi") fun aSpiderManPodHasNoWarning() {
        show(controller(), detail(PodPhase.SETUP, DraftSettings(bots = 2), listOf(PodMember(alice), PodMember(bob)), spiderMan, name = "Spider night"))
        compose.onNodeWithTag("pod-add-bot").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("pod-bot-warning-1").assertDoesNotExist()
        compose.onNodeWithTag("pod-add-bot-warning").assertDoesNotExist()
        compose.onAllNodesWithTag("pod-bot-warning-avatar", useUnmergedTree = true).assertCountEquals(0)
        shot("spider-man-setup", "The creator's setup of a Booster pod from Marvel's Spider-Man with Bob and two bot seats: its bots " +
            "pick by Through the Omenpaths' card ratings, so no warning sign on the bots or beside Add bot.")
    }
}
