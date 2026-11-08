package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.DraftableSet
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
 * The draft screens for the owner's gallery (backlog F1): every draft mode's pick screen inside the whole pod screen
 * (header, tabs), pod setup, presets, set sources, Build and Stats, with invented cards. The screens are shown from
 * seeded pod state; nothing is sent (the worker never runs requests). Grid, Rochester, Rotisserie, Winston and the
 * Build review have their own screenshot tests.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-xhdpi")
class DraftGalleryScreenshotTest {
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
    private val pods = PodController(unused<Pods>(), unused<Cubes>(), { "token" }, worker, { it() })

    @After fun close() { pods.close() }

    private val alice = AccountIdentity("alice", "Alice")
    private val bob = AccountIdentity("bob", "Bob")
    private val carol = AccountIdentity("carol", "Carol")

    /** Invented cards: name, mana cost, type line. */
    private val cards = listOf(
        Triple("Dawnward Sentry", "{1}{W}", "Creature — Human Soldier"), Triple("Lantern Oath", "{W}", "Instant"),
        Triple("Skyhall Lancer", "{3}{W}", "Creature — Bird Knight"), Triple("Mercy Vow", "{2}{W}", "Enchantment — Aura"),
        Triple("Gleamfield Warden", "{4}{W}{W}", "Creature — Angel"), Triple("Tidecall Adept", "{1}{U}", "Creature — Merfolk Wizard"),
        Triple("Mistweave", "{U}{U}", "Instant"), Triple("Archive Drake", "{3}{U}", "Creature — Drake"),
        Triple("Second Glance", "{U}", "Sorcery"), Triple("Fogbound Sphinx", "{4}{U}{U}", "Creature — Sphinx"),
        Triple("Gravel Ghoul", "{1}{B}", "Creature — Zombie"), Triple("Bitter Harvest", "{B}", "Instant"),
        Triple("Duskmarket Thief", "{2}{B}", "Creature — Human Rogue"), Triple("Hollow Pact", "{1}{B}{B}", "Sorcery"),
        Triple("Ashen Revenant", "{4}{B}", "Creature — Spirit"), Triple("Cinder Whelp", "{R}", "Creature — Dragon"),
        Triple("Spark Lash", "{R}", "Instant"), Triple("Forgeborn Brute", "{2}{R}", "Creature — Giant"),
        Triple("Molten Verdict", "{X}{R}", "Sorcery"), Triple("Emberpeak Tyrant", "{4}{R}{R}", "Creature — Dragon"),
        Triple("Mossback Cub", "{G}", "Creature — Bear"), Triple("Rootsurge", "{G}", "Instant"),
        Triple("Thornwood Ranger", "{2}{G}", "Creature — Elf Ranger"), Triple("Wildbloom Rite", "{2}{G}", "Sorcery"),
        Triple("Canopy Colossus", "{5}{G}", "Creature — Treefolk"), Triple("Stormtide Pact", "{U}{R}", "Instant"),
        Triple("Verdant Paladin", "{G}{W}", "Creature — Human Knight"), Triple("Gloomroot Witch", "{B}{G}", "Creature — Elf Warlock"),
        Triple("Brass Courier", "{2}", "Artifact Creature — Construct"), Triple("Waystone Idol", "{3}", "Artifact"),
        Triple("Sunken Causeway", "", "Land"), Triple("Ridgeback Crossing", "", "Land"),
    )
    private fun digest(n: Int) = "def-$n"
    private val librarianDigest = "def-librarian"
    private val definitions = cards.mapIndexed { n, (name, cost, type) ->
        SubmittedDefinition(digest(n), CardDefinition(name, listOf(DefinitionFace("face", name, typeLine = type, manaCost = cost,
            scryfallId = placeholderPrinting(name)))))
    } + SubmittedDefinition(librarianDigest, CardDefinition("Cogwork Librarian", listOf(DefinitionFace("face", "Cogwork Librarian",
        typeLine = "Artifact Creature — Construct", manaCost = "{4}", scryfallId = placeholderPrinting("Cogwork Librarian")))))

    /** Copy [copy] of card [n]. */
    private fun copy(n: Int, copy: Int = 0) = DraftCopy("c$n-$copy", digest(n))

    private fun pack(id: String, cards: List<Int>, pick: Int = 1, passedBy: String? = null) =
        DraftPrivatePack(id, 1, cards.map { copy(it) }, pick, passedBy)

    private fun detail(phase: PodPhase, settings: DraftSettings, members: List<PodMember>, name: String = "Cube night",
        source: PodSource = PodSource("Runes", 540, maybeCopies = 12)) = PodDetail("pod", name, phase, alice, settings, source,
        DraftSupply(135, 540), members, emptyList(), draftAttemptId = "attempt", definitions = definitions,
        updatedAt = "2026-11-07T00:00:00Z")

    private fun show(state: PodState) {
        pods.seed(state)
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            PodsDestination(pods, alice.accountId, {}, null, {}, mainMenu = {})
        } } } }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    private fun opened(detail: PodDetail, tab: PodTab = PodTab.DRAFT) =
        PodState(loaded = true, selected = detail.id, detail = detail, tab = tab)

    private fun shot(name: String, caption: String) = captureShot("$SCREENSHOTS/draft/$name.png", caption)

    // DRF-012, DRF-013
    @Test fun podsList() {
        fun pod(id: String, name: String, phase: PodPhase, attention: PodAttention, waiting: Int, mode: DraftMode,
            members: List<AccountIdentity> = listOf(alice, bob, carol)) =
            PodSummary(id, name, phase, alice, members, attention, waiting, "2026-11-0${id.last()}T00:00:00Z", mode)
        show(PodState(loaded = true, pods = listOf(
            pod("p1", "Cube night", PodPhase.DRAFTING, PodAttention.YOUR_PICK, 2, DraftMode.BOOSTER),
            pod("p2", "Rotisserie Sunday", PodPhase.DRAFTING, PodAttention.YOUR_PICK, 1, DraftMode.ROTISSERIE),
            pod("p3", "Sealed league", PodPhase.DRAFTED, PodAttention.BUILD, 0, DraftMode.SEALED),
            pod("p4", "Grid with Bob", PodPhase.DRAFTING, PodAttention.NONE, 0, DraftMode.GRID, listOf(alice, bob)),
            pod("p5", "Winston duel", PodPhase.SETUP, PodAttention.NONE, 0, DraftMode.WINSTON, listOf(alice)),
            pod("p6", "Glimpse league", PodPhase.DRAFTED, PodAttention.NONE, 0, DraftMode.BOOSTER)),
            invitations = listOf(PodInvitation("p7", "Winchester rematch", bob, "2026-11-07T00:00:00Z"))))
        compose.onNodeWithText("Your pick · 2 packs").assertIsDisplayed()
        shot("pods-list", "The Pods screen lists Alice's pods in the shared order: two waiting for her pick (\"Your pick · 2 packs\" " +
            "in Booster, just \"Your pick\" in Rotisserie) highlighted, a Sealed pod to build, Bob's invitation, then the rest.")
    }

    // DRF-016, DRF-020, DRF-022, DRF-026, DRF-027, DRF-030, DRF-036, DRF-051, DRF-142 (a tall screen shows the whole form)
    @Test @Config(qualifiers = "w412dp-h1300dp-port-xhdpi") fun boosterSetup() {
        val detail = detail(PodPhase.SETUP, DraftSettings(librarians = 1, sideboardLimit = 15),
            listOf(PodMember(alice), PodMember(bob))).copy(pendingInvitations = listOf(carol))
        show(opened(detail))
        compose.onNodeWithText("Prepare cube").assertIsDisplayed()
        shot("setup-booster", "The creator's setup of a Booster pod from the Runes cube with Librarians and a 15-card sideboard: " +
            "the mode button, square setting tiles, Prepare cube with its counts, members with Carol's invitation pending and " +
            "\"Start when everyone accepts\".")
    }

    // DRF-021, DRF-024, DRF-151
    @Test fun sealedSetSetup() {
        val detail = detail(PodPhase.SETUP, DraftSettings.sealed(), listOf(PodMember(alice), PodMember(bob), PodMember(carol)),
            name = "Sealed league", source = PodSource(kind = PodSourceKind.SET, setCode = "EMR", setName = "Ember Reaches",
                boosterKind = SetBoosterKind.RECIPE_BASED, boosterName = "play"))
        show(opened(detail))
        compose.onNodeWithText("Ember Reaches").assertIsDisplayed()
        shot("setup-sealed-set", "The creator's setup of a Sealed pod from the invented Ember Reaches set: 6 boosters per player, " +
            "no pack size tile (the set's booster recipe fixes it), three members and Start draft.")
    }

    // DRF-018, DRF-088, DRF-089
    @Test fun sourceSetsTab() {
        val detail = detail(PodPhase.SETUP, DraftSettings.booster(), listOf(PodMember(alice), PodMember(bob)))
        pods.setPicker.seed(PodSetPickerState(open = true, loaded = true, rows = listOf(
            PodSetRow(DraftableSet("EMR", "Ember Reaches", "2026-10-02", 280, "expansion"), true),
            PodSetRow(DraftableSet("TWI", "Tidewild Isles", "2026-06-12", 271, "expansion"), true),
            PodSetRow(DraftableSet("CRM", "Crown of Masters", "2026-03-06", 300, "masters"), false),
            PodSetRow(DraftableSet("HRV", "Harvest Hollow", "2025-11-14", 264, "expansion"), false),
            PodSetRow(DraftableSet("CR26", "Core Rites 2026", "2025-07-11", 281, "core"), false),
            PodSetRow(DraftableSet("PCK", "Pack Commander Decks", "2025-07-11", 100, "commander"), false))))
        show(opened(detail))
        compose.onNodeWithText("Sets").performClick()
        settle()
        compose.onNodeWithText("Ember Reaches").assertIsDisplayed()
        shot("source-sets", "Tapping the source's edit button in pod setup and then the Sets tab lists the draftable sets newest " +
            "first, with a search field; downloaded and other sets look the same (invented set names).")
    }

    // DRF-031, DRF-032
    @Test fun presets() {
        val detail = detail(PodPhase.SETUP, DraftSettings.booster(), listOf(PodMember(alice), PodMember(bob)))
        pods.presets.seed(PodPresetState(open = true, loaded = true, presets = listOf(
            PodPreset("1", "Friday cube", DraftSettings.booster(), cubeId = "runes", cubeName = "Runes", updatedAt = "2026-11-01T00:00:00Z"),
            PodPreset("2", "Rotisserie with friends", DraftSettings.rotisserie(), cubeId = "runes", cubeName = "Runes",
                updatedAt = "2026-11-01T00:00:00Z"),
            PodPreset("3", "Sealed league", DraftSettings.sealed(), setCode = "EMR", setName = "Ember Reaches",
                updatedAt = "2026-11-01T00:00:00Z"),
            PodPreset("4", "Old grid night", DraftSettings.grid().copy(grids = 15), cubeId = "gone", cubeMissing = true,
                updatedAt = "2026-11-01T00:00:00Z"))))
        show(opened(detail))
        compose.onNodeWithText("Glimpse").assertIsDisplayed()
        shot("presets", "Tapping Presets in the creator's pod setup opens the dialog: the built-in Glimpse preset " +
            "first, then Alice's saved presets (Booster, Rotisserie, Sealed from a set, a grid whose cube is missing) with search.")
    }

    private val boosterMembers = listOf(PodMember(alice, packsWaiting = 2), PodMember(bob, packsWaiting = 1), PodMember(carol))

    private fun boosterPick(settings: DraftSettings, packCards: List<Int>, pool: List<DraftCopy> = emptyList(),
        members: List<PodMember> = boosterMembers, pick: Int = 2): PodDetail {
        val head = pack("pack-a", packCards, pick = pick, passedBy = "bob")
        return detail(PodPhase.DRAFTING, settings, members).copy(draft = DraftPrivateView("alice", head, 1,
            pool + listOf(copy(16, 1), copy(22, 1)), queuedPacks = listOf(pack("pack-b", (0..13).toList())), round = 1,
            rounds = settings.rounds))
    }

    private val fourteen = listOf(0, 5, 10, 15, 20, 25, 28, 2, 7, 12, 17, 22, 26, 30)

    // DRF-056, DRF-057, DRF-061, DRF-065, DRF-073, DRF-074, DRF-076
    @Test fun boosterPick() = boosterPickShot(false)

    // DRF-056
    @Test @Config(qualifiers = "w892dp-h412dp-land-xhdpi") fun boosterPickLandscape() = boosterPickShot(true)

    private fun boosterPickShot(landscape: Boolean) {
        val detail = boosterPick(DraftSettings.booster(), fourteen)
        show(opened(detail))
        compose.runOnIdle { pods.mark(detail.draft!!.head, PodPickMarks("pack-a", keeps = listOf(copy(15).id))) }
        settle()
        compose.onNodeWithText("Round 1/3 · Pick 2 · from Bob").assertIsDisplayed()
        if (landscape) shot("booster-pick-landscape", "A Booster draft's pick screen in landscape, five cards per row: the same " +
            "pack from Bob with Cinder Whelp marked keep, the keep tray and Confirm fixed below.")
        else shot("booster-pick", "A Booster draft for three on Alice's pick 2 of round 1, passed by Bob: the header's avatars " +
            "show how many picks each member can make (Carol ✓), Draft · Build · Stats tabs, and Cinder Whelp marked keep " +
            "with a green border and ✓, also in the keep tray above Confirm.")
    }

    // DRF-021, DRF-044, DRF-061, DRF-065
    @Test fun glimpsePick() {
        val detail = boosterPick(DraftSettings.glimpse(), fourteen)
        show(opened(detail))
        compose.runOnIdle {
            pods.mark(detail.draft!!.head, PodPickMarks("pack-a", keeps = listOf(copy(25).id), burns = listOf(copy(0).id, copy(28).id)))
        }
        settle()
        compose.onNodeWithText("Round 1/9 · Pick 2 · from Bob").assertIsDisplayed()
        shot("glimpse-pick", "A Glimpse draft (Booster's built-in preset: 9 packs, keep 1, burn 2) on Alice's pick: Stormtide " +
            "Pact marked keep and two cards marked burn with red borders and 🔥, the keep and burn trays side by side above Confirm.")
    }

    // DRF-052, DRF-053, DRF-054
    @Test fun librarianPick() {
        val librarians = listOf(DraftCopy("lib-1", librarianDigest, librarian = true), DraftCopy("lib-2", librarianDigest, librarian = true))
        val detail = boosterPick(DraftSettings(librarians = 2), fourteen.take(8), librarians, pick = 8)
        show(opened(detail))
        compose.runOnIdle { pods.mark(detail.draft!!.head, PodPickMarks("pack-a", keeps = listOf(copy(15).id), librarians = listOf("lib-1"))) }
        settle()
        compose.onNodeWithText("Confirm · 1 Librarian").assertIsDisplayed()
        shot("librarian-pick", "A Booster draft with Librarians after Alice spends one: the Librarian joins the pack with a book " +
            "badge at the end of the pack, the keep tray grows a dashed, badged second slot beside Cinder Whelp with the chip counting " +
            "the one left, and Confirm reads \"Confirm · 1 Librarian\" (greyed until the second keep is marked).")
    }

    // DRF-071, DRF-072
    @Test fun boosterWaiting() {
        val members = listOf(PodMember(alice), PodMember(bob, packsWaiting = 2), PodMember(carol, packsWaiting = 1))
        val detail = detail(PodPhase.DRAFTING, DraftSettings.booster(), members).copy(draft = DraftPrivateView("alice", null, 0,
            listOf(copy(16, 1), copy(22, 1)), round = 2, rounds = 3, incoming = 2))
        show(opened(detail))
        compose.onNodeWithTag("pod-waiting").assertIsDisplayed()
        shot("booster-waiting", "A Booster draft with no pack for Alice: the faded centred waiting text (\"Waiting for Bob, Carol\", " +
            "\"2 packs will come back to you\", \"Round 2 of 3\") with the Main menu link under it.")
    }

    // DRF-071, DRF-159, DRF-160
    @Test fun winchesterWaiting() {
        val piles = listOf(listOf(copy(1), copy(6)), listOf(copy(11)), listOf(copy(16), copy(21), copy(26)), listOf(copy(29)))
        val members = listOf(PodMember(alice), PodMember(bob, packsWaiting = 1, picking = true))
        val detail = detail(PodPhase.DRAFTING, DraftSettings.winchester(), members, name = "Winchester rematch").copy(
            limited = LimitedView(DraftMode.WINCHESTER, "alice", listOf("alice", "bob"), listOf(copy(3), copy(8)), false,
                currentPlayer = "bob", turn = 3, stack = 22, piles = piles,
                picks = listOf(LimitedPickView("alice", listOf(copy(3)), 1), LimitedPickView("bob", listOf(copy(13), copy(18)), 2),
                    LimitedPickView("alice", listOf(copy(8)), 0))))
        show(opened(detail.copy(members = members)))
        compose.onNodeWithText("Picks").assertIsDisplayed()
        shot("winchester-waiting", "A Winchester draft on Bob's turn: the board stays on screen with its line naming whose pick it " +
            "is (\"Waiting for Bob\") and the stack count, the four face-up piles, and the Picks button at the top right.")
    }

    private fun buildCopies(): Pair<List<DraftCopy>, PodDeckBuild> {
        val main = listOf(0, 1, 2, 3, 4, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 26, 28, 29, 30, 31) .map { copy(it) } +
            listOf(copy(0, 1), copy(16, 1), copy(22, 1))
        val side = listOf(5, 6, 7, 25, 27).map { copy(it) }
        val maybe = listOf(10, 11).map { copy(it) }
        val build = PodDeckBuild(main.map { PodDeckCopy(it.id) } + side.map { PodDeckCopy(it.id, PodDeckCategory.SIDE) } +
            maybe.map { PodDeckCopy(it.id, PodDeckCategory.MAYBE) }, PodDeckBasics(plains = 8, mountain = 4, forest = 5),
            PodDeckBasics(17, 17, 17, 17, 17))
        return main + side + maybe to build
    }

    // DRF-076, DRF-092, DRF-094, DRF-095, DRF-097, DRF-098, DRF-103, DRF-106 (a tall screen shows Main, Side and Maybe)
    @Test @Config(qualifiers = "w412dp-h1500dp-port-xhdpi") fun build() = buildShot(false)

    // DRF-095
    @Test @Config(qualifiers = "w892dp-h412dp-land-xhdpi") fun buildLandscape() = buildShot(true)

    private fun buildShot(landscape: Boolean) {
        val (pool, build) = buildCopies()
        val detail = detail(PodPhase.DRAFTED, DraftSettings.booster(), listOf(PodMember(alice, building = true),
            PodMember(bob, submitted = true), PodMember(carol, building = true))).copy(draft = DraftPrivateView("alice", null, 0, pool),
            build = build)
        show(opened(detail, PodTab.BUILD))
        compose.onNodeWithText("Submit deck", substring = true).assertIsDisplayed()
        if (landscape) shot("build-landscape", "Build in landscape after a Booster draft: the same deck in wider columns, the deck " +
            "name and the Main header at the top, Undo and Submit deck in the full-width bottom bar.")
        else shot("build", "Build after a Booster draft, opened on the Build tab (Build · Stats · Games) on a tall screen: Main, " +
            "Side and Maybe with their counts, basics rows, columns Lands then mana value with counts, duplicates as ×2, and Undo " +
            "with Submit deck at the bottom.")
    }

    // DRF-118
    @Test fun statsNotes() {
        val (pool, build) = buildCopies()
        val detail = detail(PodPhase.DRAFTING, DraftSettings.booster(), boosterMembers).copy(
            draft = DraftPrivateView("alice", pack("pack-a", fourteen), 0, pool, round = 2, rounds = 3), build = build)
        show(opened(detail, PodTab.STATS))
        compose.runOnIdle { pods.editNotes { PodDeckNotes("Red-green aggro; Bob cuts blue.", listOf(0, 0, 0, 5, 4, 1, 1, 0)) } }
        settle()
        compose.onNodeWithTag("pod-stats").performTouchInput { swipeUp() }
        settle()
        shot("stats-notes", "The Stats tab during a Booster draft, scrolled down past the colours donut to Alice's private deck " +
            "notes and the eight tally counters she has set.")
    }

    // DRF-151, DRF-076
    @Test fun sealedBuild() {
        val pool = (0..31).filter { it % 5 != 4 }.map { copy(it) } + listOf(copy(20, 1), copy(21, 1))
        val detail = detail(PodPhase.DRAFTED, DraftSettings.sealed(), listOf(PodMember(alice, building = true),
            PodMember(bob, building = true), PodMember(carol, building = true)), name = "Sealed league",
            source = PodSource(kind = PodSourceKind.SET, setCode = "EMR", setName = "Ember Reaches")).copy(
            limited = LimitedView(DraftMode.SEALED, "alice", listOf("alice", "bob", "carol"), pool, true),
            build = PodDeckBuild(pool.map { PodDeckCopy(it.id) }, sideBasics = PodDeckBasics(17, 17, 17, 17, 17)))
        show(opened(detail, PodTab.BUILD))
        shot("sealed-build", "A Sealed pod right after the start: there is no draft, so it opens on Build with Alice's whole " +
            "opened pool in Main and the Build · Stats · Games tabs.")
    }
}
