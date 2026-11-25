package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.PodGridView as GridViewData
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

    /** While the pod drafts, Alice's own draft runs (DRF-302: picks of hers to come, so no Games tab, DRF-076) unless she has
     * submitted. */
    private fun detail(phase: PodPhase, settings: DraftSettings, members: List<PodMember>, name: String = "Cube night",
        source: PodSource = PodSource("Runes", 540, maybeCopies = 12)) = PodDetail("pod", name, phase, alice, settings, source,
        DraftSupply(135, 540), members, emptyList(), draftAttemptId = "attempt", definitions = definitions,
        updatedAt = "2026-11-07T00:00:00Z", draftWait = aliceDrafting(phase, members))

    private fun aliceDrafting(phase: PodPhase, members: List<PodMember>) =
        PodDraftWait.Picks.takeIf { phase == PodPhase.DRAFTING && members.none { it.account == alice && it.submitted } }

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
            pod("p4", "Grid with Bob", PodPhase.DRAFTING, PodAttention.NONE, 0, DraftMode.GRID, listOf(alice, bob)).copy(chatUnread = 2),
            pod("p5", "Winston duel", PodPhase.SETUP, PodAttention.NONE, 0, DraftMode.WINSTON, listOf(alice)),
            pod("p6", "Glimpse league", PodPhase.DRAFTED, PodAttention.NONE, 0, DraftMode.BOOSTER)),
            invitations = listOf(PodInvitation("p7", "Winchester rematch", bob, "2026-11-07T00:00:00Z"))))
        compose.onNodeWithText("Your pick · 2 packs").assertIsDisplayed()
        shot("pods-list", "The Pods screen lists Alice's pods in the shared order: two waiting for her pick (\"Your pick · 2 packs\" " +
            "in Booster, just \"Your pick\" in Rotisserie) highlighted, a Sealed pod to build, Bob's invitation, then the rest; the Grid " +
            "pod's status has a dot for unread chat messages.")
    }

    // DRF-281, DRF-282, DRF-283, DRF-284, DRF-285, DRF-287 (a tall screen shows the whole form; Initial rares, owner 2026-11-21)
    @Test @Config(qualifiers = "w412dp-h1300dp-port-xhdpi") fun arenaSetup() {
        val source = PodSource("Runes", 540, maybeCopies = 12, rarities = ArenaCounts(common = 400, uncommon = 120, rare = 18, mythic = 2))
        val detail = detail(PodPhase.SETUP, DraftSettings.arena(), listOf(PodMember(alice), PodMember(bob)), name = "Arena night",
            source = source)
        show(opened(detail))
        compose.onNodeWithTag("pod-arena-summary").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Only 2 mythic rares for offers of 5: filled up from rares").assertIsDisplayed()
        // DRF-281 (owner gallery review, 2026-11-20): "Uncommon every" fits its tile on one line as the other tiles' labels do
        val uncommon = compose.onNodeWithText("Uncommon every").fetchSemanticsNode().boundsInRoot
        val rare = compose.onNodeWithText("Rare every").fetchSemanticsNode().boundsInRoot
        kotlin.test.assertEquals(rare.height, uncommon.height, 0.5f, "one line, as Rare every: $uncommon, $rare")
        shot("setup-arena", "The creator's setup of an Arena pod from the Runes cube with the defaults: tiles for 45 picks, " +
            "offers of 5 cards, take 1, 1 initial rare, rare every 10th and uncommon every 4th pick, mythic 1/8 and the sideboard, the Ignore rarity " +
            "and Reuse cards toggles off, then the summary: the format, 5 rare, 9 uncommon and 31 common picks for 45 cards each, " +
            "Reuse cards off in words, and that the cube's 2 mythic rares are too few for an offer, so rares fill up.")
    }

    // DRF-288, DRF-280
    @Test fun arenaPick() {
        val settings = DraftSettings.arena()
        val head = DraftPrivatePack("offer-0-11", 12, listOf(2, 7, 12, 17, 22).map { copy(it) }, 1, null)
        val detail = detail(PodPhase.DRAFTING, settings, listOf(PodMember(alice, packsWaiting = 34, picksMade = 11),
            PodMember(bob, packsWaiting = 40, picksMade = 5)), name = "Arena night").copy(
            draft = DraftPrivateView("alice", head, 0, (0..10).map { copy(it + 3, 1) }, round = 12, rounds = 45))
        show(opened(detail))
        compose.onNodeWithTag("pod-pick-orientation").assertTextEquals("Pick 12 of 45 · Uncommon · take 1")
        compose.runOnIdle { pods.mark(detail.draft!!.head, PodPickMarks("offer-0-11", keeps = listOf(copy(12).id))) }
        settle()
        shot("arena-pick", "Alice's twelfth Arena pick, an uncommon one: her own offer of five cards under the line " +
            "\"Pick 12 of 45 · Uncommon · take 1\", Duskmarket Thief marked to take, the keep tray and Confirm below; no burn tray.")
    }

    // DRF-016, DRF-020, DRF-022, DRF-026, DRF-027, DRF-030, DRF-036, DRF-051, DRF-142, DRF-199 (a tall screen shows the whole form)
    @Test @Config(qualifiers = "w412dp-h1300dp-port-xhdpi") fun boosterSetup() {
        val detail = detail(PodPhase.SETUP, DraftSettings(librarians = 1, sideboardLimit = 15),
            listOf(PodMember(alice), PodMember(bob))).copy(pendingInvitations = listOf(carol))
        show(opened(detail))
        compose.onNodeWithText("Prepare cube").assertIsDisplayed()
        shot("setup-booster", "The creator's setup of a Booster pod from the Runes cube with Librarians and a 15-card sideboard: " +
            "the mode button, square setting tiles, Prepare cube with its counts, members with Carol's invitation pending and " +
            "\"Start when everyone accepts\".")
    }

    // DRF-296, DRF-297, DRF-030, DRF-307, DRF-308 (owner, 2026-11-21 and 2026-11-22): no Bots tile; "Add bot" beside the invite
    // button; the three bot seats listed after the members as "Bot 1"… and counted in Members (5/8); every row but the creator's
    // own ends in a ✕; Bob's ✕ asks to confirm his removal (a tall screen shows the whole form)
    @Test @Config(qualifiers = "w412dp-h1300dp-port-xhdpi") fun botSeatsSetup() {
        val detail = detail(PodPhase.SETUP, DraftSettings(bots = 3), listOf(PodMember(alice), PodMember(bob)), name = "Bot night")
            .copy(pendingInvitations = listOf(carol))
        show(opened(detail))
        compose.onNodeWithText("Members (5/8)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("pod-add-bot").performScrollTo().assertIsEnabled()
        // In the strip over the setup and in its members list
        compose.onAllNodesWithText("bot").assertCountEquals(6)
        compose.onNodeWithContentDescription("Remove Bob").assertIsEnabled()
        compose.onNodeWithContentDescription("Remove Bot 2").assertIsEnabled()
        compose.onNodeWithContentDescription("Cancel Carol's invitation").assertIsEnabled()
        compose.onNodeWithContentDescription("Remove Alice").assertDoesNotExist()
        shot("setup-bots", "The creator's setup of a Booster pod with three bot seats: the tiles end with Sideboard (no Bots tile); " +
            "the strip at the top and the members list (five of eight seats) show Alice and Bob, then Bot 1, Bot 2 and Bot 3 marked " +
            "\"bot\" and Carol's pending invitation, each row but Alice's own with a ✕ at its end, and under them the invite button " +
            "with \"Add bot\" at the row's right.")
        compose.onNodeWithTag("pod-remove-member-bob").performScrollTo().performClick()
        settle()
        compose.onNodeWithText("Remove Bob from Bot night?").assertIsDisplayed()
        shot("setup-remove-member", "The creator tapped the ✕ on Bob's row in the setup above: a dialog \"Remove member\" asks " +
            "\"Remove Bob from Bot night?\" with Cancel and a red Remove.")
    }

    // DRF-030, DRF-297 (owner, 2026-11-22): a member sees the same members list without any ✕ and without Add bot
    @Test @Config(qualifiers = "w412dp-h1300dp-port-xhdpi") fun membersSeeTheSetupListWithoutCrosses() {
        val detail = detail(PodPhase.SETUP, DraftSettings(bots = 2), listOf(PodMember(alice), PodMember(bob)), name = "Bot night")
            .copy(pendingInvitations = listOf(carol))
        pods.seed(opened(detail))
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            PodsDestination(pods, bob.accountId, {}, null, {}, mainMenu = {})
        } } } }
        settle()
        compose.onNodeWithText("Members (4/8)").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Bot 2").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Remove", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Cancel Carol's invitation").assertDoesNotExist()
        compose.onNodeWithTag("pod-add-bot").assertDoesNotExist()
    }

    // DRF-030 (owner, 2026-11-22): the pod's invite picker stays open after an invite (pods are often multiplayer): Bob, just
    // invited, reads "invited", Dave, a member, "member", and Carol still has her Invite button; Back closes the picker
    @Test fun invitePickerStaysOpenShowingWhoIsInvited() {
        val dave = AccountIdentity("dave", "Dave")
        val lobby = LobbyController(unused<Lobbies>(), { "token" }, worker, { it() }, accountId = { alice.accountId })
        try {
            lobby.seed(LobbyState(loaded = true, contacts = listOf(bob, carol, dave)))
            val detail = detail(PodPhase.SETUP, DraftSettings.booster(), listOf(PodMember(alice), PodMember(dave)))
                .copy(pendingInvitations = listOf(bob))
            pods.seed(opened(detail).copy(choosingMember = true))
            compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
                PodsDestination(pods, alice.accountId, {}, lobby, {}, mainMenu = {})
            } } } }
            settle()
            compose.onNodeWithTag("pod-invite-status-bob").assertTextEquals("invited")
            compose.onNodeWithTag("pod-invite-status-dave").assertTextEquals("member")
            compose.onNodeWithTag("pod-invite-status-carol").assertDoesNotExist()
            compose.onNodeWithContentDescription("Invite player").assertIsEnabled()
            shot("invite-picker", "The creator's pod invite picker after inviting Bob: it stays open with her contacts, Bob " +
                "marked \"invited\", Dave \"member\" and Carol with her Invite button, the members strip above it.")
            compose.runOnIdle { pods.back() }
            compose.runOnIdle { kotlin.test.assertFalse(pods.state.choosingMember) }
        } finally { lobby.close() }
    }

    // DRF-296 (owner, 2026-11-23): Sealed and Arena take no bots, so their setup has no Add bot; Grid's greys at three
    @Test fun addBotFollowsTheMode() {
        show(opened(detail(PodPhase.SETUP, DraftSettings.arena(), listOf(PodMember(alice), PodMember(bob)))))
        compose.onNodeWithTag("pod-add-bot").assertDoesNotExist()
        compose.runOnIdle { pods.seed(opened(detail(PodPhase.SETUP, DraftSettings.grid().copy(bots = 3), listOf(PodMember(alice))))) }
        settle()
        compose.onNodeWithTag("pod-add-bot").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Remove Bot 3").assertIsEnabled()
    }

    // DRF-296, DRF-301, DRF-035 (owner, 2026-11-23): a solo draft: the creator alone with two bots and no invitation pending; under
    // the members setup warns there is no one to play against, and Start draft is enabled
    @Test @Config(qualifiers = "w412dp-h1300dp-port-xhdpi") fun soloDraftSetupWarns() {
        show(opened(detail(PodPhase.SETUP, DraftSettings(bots = 2), listOf(PodMember(alice)), name = "Solo night")))
        compose.onNodeWithTag("pod-solo-warning").performScrollTo()
            .assertTextEquals("No one to play against: bots only draft, so this pod plays no games.")
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Start draft")), useUnmergedTree = true).performScrollTo().assertIsEnabled()
        shot("setup-solo", "The creator's setup of a Booster pod she is alone in with two bot seats: under the members (Alice, Bot 1 " +
            "and Bot 2) a red line says \"No one to play against: bots only draft, so this pod plays no games.\", and Start draft is enabled.")
    }

    // DRF-297, DRF-298: a started Booster pod with three bot seats: they are in the header's avatars after the people, ready (✓)
    // because they pick the moment a pack reaches them, and Alice's pack came from Bot 2
    @Test fun botSeatsPick() {
        val members = listOf(PodMember(alice, packsWaiting = 2), PodMember(bob, packsWaiting = 1)) +
            (1..3).map { PodMember(PodBots.identity(it), picksMade = 2, bot = true) }
        val head = pack("pack-a", fourteen.take(13), pick = 3, passedBy = PodBots.id(2))
        val detail = detail(PodPhase.DRAFTING, DraftSettings(bots = 3), members, name = "Bot night").copy(draft = DraftPrivateView(
            "alice", head, 1, listOf(copy(16, 1), copy(22, 1)), queuedPacks = listOf(pack("pack-b", (0..11).toList())), round = 1, rounds = 3))
        show(opened(detail))
        compose.onNodeWithText("Round 1/3 · Pick 3 · from Bot 2").assertIsDisplayed()
        shot("bots-pick", "Alice's third Booster pick in a pod with Bob and three bot seats: the header's avatars show Alice and Bob " +
            "with picks waiting and Bot 1, Bot 2 and Bot 3 ready (✓), and the line above the pack says it came from Bot 2.")
    }

    // DRF-262, DRF-020, DRF-022 (a tall screen shows the whole form)
    @Test @Config(qualifiers = "w412dp-h1300dp-port-xhdpi") fun customBoosterSetup() {
        val detail = detail(PodPhase.SETUP, DraftSettings.customBooster().copy(passes = listOf(DraftPass(2, 1)) +
            List(4) { DraftPass(1, 1) } + DraftPass(1, 0)), listOf(PodMember(alice), PodMember(bob)), name = "Burn night")
        show(opened(detail))
        compose.onNodeWithText("Passes (6)").performScrollTo().assertIsDisplayed()
        shot("setup-custom-booster", "The creator's setup of a Custom booster pod from the Runes cube: its tiles (packs, pack size, " +
            "open packs, Librarians, bots, sideboard) and under them its six passes, pick 2 and burn 1 first, then four of pick 1, " +
            "burn 1 and a last pick 1, each with its Pick and Burn buttons and a remove cross, then Add pass.")
    }

    // DRF-263, DRF-260, DRF-057 (owner 2026-11-22: the pass in the first line, no line above the trays)
    @Test fun customBoosterPick() {
        val settings = DraftSettings.customBooster().copy(passes = listOf(DraftPass(2, 1)) + List(4) { DraftPass(1, 1) } + DraftPass(1, 0))
        val detail = boosterPick(settings, fourteen.take(11), pick = 2).copy(name = "Burn night")
        show(opened(detail))
        compose.runOnIdle {
            pods.mark(detail.draft!!.head, PodPickMarks("pack-a", keeps = listOf(copy(15).id), burns = listOf(copy(0).id)))
        }
        settle()
        compose.onNodeWithTag("pod-pick-orientation").assertTextEquals("Round 1/3 · Pass 2/6 · from Bob")
        compose.onNodeWithTag("pod-pick-pass").assertDoesNotExist()
        shot("custom-booster-pick", "A Custom booster draft on the second pass of a pack passed by Bob: the first line above the " +
            "cards names it (\"Round 1/3 · Pass 2/6 · from Bob\"), Cinder Whelp marked keep and Dawnward Sentry marked burn, " +
            "the trays below saying the pick and burn counts.")
    }

    // DRF-261, DRF-045, DRF-057 (owner 2026-11-22): on a pack's last pass only the pick is chosen; every other card burns by itself
    @Test fun customBoosterLastPass() {
        val settings = DraftSettings.customBooster().copy(passes = listOf(DraftPass(2, 1), DraftPass(1, 1), DraftPass(1, 1)))
        val detail = boosterPick(settings, fourteen.take(6), pick = 3).copy(name = "Burn night")
        show(opened(detail))
        compose.runOnIdle { pods.mark(detail.draft!!.head, PodPickMarks("pack-a", keeps = listOf(copy(15).id))) }
        settle()
        compose.onNodeWithTag("pod-pick-orientation").assertTextEquals("Round 1/3 · Pass 3/3 · from Bob")
        shot("custom-booster-last-pass", "A Custom booster draft on a pack's last pass, passed by Bob: Alice marked Cinder Whelp " +
            "keep, and the other five cards show as automatic burns, red with 🔥, without her choosing them.")
    }

    // DRF-182, DRF-152, DRF-022
    @Test fun rotisserieSetup() {
        val detail = detail(PodPhase.SETUP, DraftSettings.rotisserie().copy(cardsPerPlayer = 30, poolPerPlayer = 60),
            listOf(PodMember(alice), PodMember(bob)), name = "Rotisserie night")
        show(opened(detail))
        compose.onNodeWithText("Pool each").assertIsDisplayed()
        compose.onNodeWithText("Picks each").assertIsDisplayed()
        shot("setup-rotisserie", "The creator's setup of a Rotisserie pod from the Runes cube: a pool of 60 cards per player and " +
            "30 picks per player as two tiles, then the sideboard limit.")
    }

    // DRF-021, DRF-192, DRF-024, DRF-151
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
        pods.setPicker.seed(PodSetPickerState(open = true, loaded = true, sets = listOf(
            DraftableSet("EMR", "Ember Reaches", "2026-10-02", 280, true),
            DraftableSet("TWI", "Tidewild Isles", "2026-06-12", 271, false, listOf("Tidal Oddity", "Whirling Omen")),
            DraftableSet("CRM", "Crown of Masters", "2026-03-06", 300, true),
            DraftableSet("HRV", "Harvest Hollow", "2025-11-14", 264, true),
            DraftableSet("CR26", "Core Rites 2026", "2025-07-11", 281, false, listOf("Rite of Doubt")))))
        show(opened(detail))
        compose.onNodeWithText("Sets").performClick()
        settle()
        compose.onNodeWithText("Ember Reaches").assertIsDisplayed()
        shot("source-sets", "Tapping the source's edit button in pod setup and then the Sets tab lists the draftable sets newest " +
            "first, with a search field; the two Automatum can't play every card of are greyed with the warning sign (invented set names).")
    }

    // DRF-031, DRF-032, DRF-201
    @Test fun presets() {
        val detail = detail(PodPhase.SETUP, DraftSettings.booster(), listOf(PodMember(alice), PodMember(bob)))
        pods.presets.seed(PodPresetState(open = true, loaded = true, presets = listOf(
            PodPreset("1", "Friday cube", DraftSettings.booster(), cubeId = "runes", cubeName = "Runes", updatedAt = "2026-11-01T00:00:00Z"),
            PodPreset("0", "Glimpse", DraftSettings.glimpse(), updatedAt = "2026-11-01T00:00:00Z"),
            PodPreset("2", "Rotisserie with friends", DraftSettings.rotisserie(), cubeId = "runes", cubeName = "Runes",
                updatedAt = "2026-11-01T00:00:00Z"),
            PodPreset("3", "Sealed league", DraftSettings.sealed(), setCode = "EMR", setName = "Ember Reaches",
                updatedAt = "2026-11-01T00:00:00Z"),
            PodPreset("4", "Old grid night", DraftSettings.grid().copy(grids = 15), cubeId = "gone", cubeMissing = true,
                updatedAt = "2026-11-01T00:00:00Z"))))
        show(opened(detail))
        compose.onNodeWithText("Glimpse").assertIsDisplayed()
        shot("presets", "Tapping Presets in the creator's pod setup opens the dialog: Alice's presets by name, Glimpse among them " +
            "as an ordinary one with its rename and delete buttons (Booster, Glimpse, Rotisserie, Sealed from a set, a grid whose cube is missing).")
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

    // DRF-292, LOB-263, DRF-313: with "Show bot picks" on, Alice's ninth Booster pick shows the bot's score on every card and its
    // pick framed; a tap on a score lists its parts. Her picks so far are white and blue, so the red Dragon (two red symbols)
    // pays the full off-colour penalty, while Hex Grasp, strong with one black symbol, is a splash and ties it, its name first
    @Test fun botPicks() {
        fun card(n: Int, name: String, cost: String, type: String, text: String = "", power: String = "", toughness: String = "") =
            SubmittedDefinition("bot-$n", CardDefinition(name, listOf(DefinitionFace("face", name, typeLine = type, manaCost = cost,
                rulesText = text, power = power, toughness = toughness, scryfallId = placeholderPrinting(name))), printingId = "p-$n"))
        val cards = listOf(
            card(0, "Skyrend Tyrant", "{3}{R}{R}", "Creature — Dragon", "Flying\nWhen this creature enters, it deals 4 damage to any target.", "5", "5"),
            card(1, "Quick Cinder", "{R}", "Instant", "Quick Cinder deals 3 damage to any target."),
            card(2, "Hex Grasp", "{1}{B}", "Instant", "Destroy target creature."),
            card(3, "Skyward Owl", "{1}{W}", "Creature — Bird", "Flying", "1", "1"),
            card(4, "Tide Scholar", "{2}{U}", "Creature — Merfolk Wizard", "When this creature enters, draw a card.", "1", "3"),
            card(5, "Plodding Ox", "{2}{G}", "Creature — Ox", power = "2", toughness = "2"),
            card(6, "Shrine Path", "", "Land", "{T}: Add {C}."),
            card(7, "Dawn Cub", "{1}{W}", "Creature — Bear", power = "2", toughness = "2"),
            card(8, "Mist Adept", "{1}{U}", "Creature — Human Wizard", power = "1", toughness = "2"),
        )
        val rarities = mapOf("p-0" to "rare", "p-2" to "uncommon", "p-3" to "uncommon")
        val bots = PodController(unused<Pods>(), unused<Cubes>(), { "token" }, worker, { it() },
            filters = PodCardFilters({ ids -> ids.mapNotNull { id -> rarities[id]?.let { id to listOf(CardSearchPrinting("tst", it)) } }.toMap() }))
        try {
            DraftBotPreferences(org.asyncttwiz.appui.AppPlatform.current).enabled = true
            val head = DraftPrivatePack("pack-bot", 1, (0..6).map { DraftCopy("b$it", "bot-$it") }, 9, "bob")
            val pool = (0..7).map { DraftCopy("w$it", if (it % 2 == 0) "bot-7" else "bot-8") }
            val detail = detail(PodPhase.DRAFTING, DraftSettings.booster(), boosterMembers).copy(definitions = cards,
                draft = DraftPrivateView("alice", head, 0, pool, round = 1, rounds = 3))
            bots.seed(opened(detail))
            compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
                PodsDestination(bots, alice.accountId, {}, null, {}, mainMenu = {})
            } } } }
            settle()
            compose.onNodeWithTag("draft-bot-b0").assertTextEquals("Bot 54")
            compose.onNodeWithTag("draft-bot-b2").assertTextEquals("Bot pick 54")
            compose.onNodeWithTag("draft-bot-frame-b2").assertExists()
            compose.onNodeWithTag("draft-bot-b0").performClick()
            settle()
            compose.onNodeWithText("Off colour R -9").assertIsDisplayed()
            compose.onNodeWithText("Total 54").assertIsDisplayed()
            shot("bot-picks", "A test build with Show bot picks on: Alice's ninth Booster pick shows the bot's score at the bottom " +
                "left of every card, and its pick, Hex Grasp, reads \"Bot pick 54\" in purple with a purple frame (a splash of black, " +
                "tied with Skyrend Tyrant and first by name); a tap on the Tyrant's \"Bot 54\" lists its parts (base, rare, stats, " +
                "flying, removal, and -9 for red, off her white and blue picks).")
        } finally { bots.close() }
    }

    // DRF-021, DRF-200, DRF-044, DRF-061, DRF-065
    @Test fun glimpsePick() {
        val detail = boosterPick(DraftSettings.glimpse(), fourteen)
        show(opened(detail))
        compose.runOnIdle {
            pods.mark(detail.draft!!.head, PodPickMarks("pack-a", keeps = listOf(copy(25).id), burns = listOf(copy(0).id, copy(28).id)))
        }
        settle()
        compose.onNodeWithText("Round 1/9 · Pick 2 · from Bob").assertIsDisplayed()
        shot("glimpse-pick", "A Glimpse draft (Booster as the Glimpse preset sets it: 9 packs, keep 1, burn 2) on Alice's pick: Stormtide " +
            "Pact marked keep and two cards marked burn with red borders and 🔥, the keep and burn trays side by side above Confirm.")
    }

    /** Alice's Glimpse pick with Stormtide Pact marked keep and one burn marked: one burn short of complete. */
    private fun autoconfirmPick(): PodDetail {
        val detail = boosterPick(DraftSettings.glimpse(), fourteen)
        show(opened(detail))
        compose.runOnIdle { pods.mark(detail.draft!!.head, PodPickMarks("pack-a", keeps = listOf(copy(25).id), burns = listOf(copy(0).id))) }
        settle()
        return detail
    }

    // DRF-293, DRF-294 (owner, 2026-11-21): Auto, the tip at Confirm's right end, off, then on, Confirm turning yellow with its shine
    @Test fun autoconfirmToggle() {
        autoconfirmPick()
        compose.onNodeWithTag("pod-pick-autoconfirm").assertIsOff()
        shot("autoconfirm-off", "Alice's Glimpse pick, Stormtide Pact marked keep and one card burned, one burn still to mark: " +
            "Confirm, looking as always, ends in Auto, a narrow tip behind a thin divider with a bolt over a small \"Auto\", off.")
        compose.onNodeWithTag("pod-pick-autoconfirm").performClick().assertIsOn()
        settle()
        shot("autoconfirm-on", "The same pick after Alice taps Auto: the tip and Confirm are one yellow shape, Confirm with a band " +
            "of light across it (here at the start of its sweep); the last burn she marks will confirm the pick at once.")
    }

    /** Alice's pick from grid 1 of a two-player Grid draft: nine fresh cards. */
    private fun gridPick(): PodDetail {
        val nine = listOf(0, 5, 10, 15, 20, 25, 28, 2, 7).map { copy(it) }
        val board = GridPublicBoard("grid-0", 1, nine, alice.accountId, 1, false, nine.map { GridPublicSlot(it) })
        val grid = GridViewData(GridPublicView(1, 9, false, listOf("alice", "bob"), listOf(board), emptyList()),
            GridPrivateView(alice.accountId, emptyList(), board.id, queue = listOf(board.id), round = 1, rounds = 18))
        return detail(PodPhase.DRAFTING, DraftSettings.grid(), listOf(PodMember(alice, packsWaiting = 1), PodMember(bob)))
            .copy(grid = grid)
    }

    // DRF-293, DRF-083 (owner, 2026-11-21): Grid's pick with Auto, the tip at Confirm's right end, off; a tap on row 2's arrow
    // only tints it
    @Test fun gridAutoconfirmOff() {
        show(opened(gridPick()))
        compose.onNodeWithTag("pod-pick-autoconfirm").assertIsOff()
        compose.onNodeWithContentDescription("Select row 2").performClick()
        settle()
        compose.runOnIdle { kotlin.test.assertFalse(pods.state.picking, "Auto off: the tap only chose the row") }
        shot("grid-autoconfirm-off", "Alice's pick from grid 1 of a two-player Grid draft after she taps row 2's arrow: the row is " +
            "tinted and Confirm ready, with Auto off at its right end.")
    }

    // DRF-293, DRF-294: Grid's pick with Auto on: Confirm yellow with its shine, dimmed while no line is taken; then a tap on a
    // line's arrow sends that line at once
    @Test fun gridAutoconfirmOn() {
        show(opened(gridPick()))
        compose.onNodeWithTag("pod-pick-autoconfirm").performClick().assertIsOn()
        settle()
        shot("grid-autoconfirm-on", "The same grid after Alice taps Auto, Confirm's tip, before choosing a line: both are yellow, " +
            "Confirm's label dimmed with a band of light across it; a tap on any row's or column's arrow now takes that line at once.")
        compose.onNodeWithContentDescription("Select column 3").performClick()
        compose.runOnIdle { kotlin.test.assertTrue(pods.state.picking, "the tap sent column 3") }
    }

    // DRF-294: the gallery clip of Auto turning on and Confirm's shine sweeping across it and back
    @Test fun clipOfTheAutoconfirmShine() {
        autoconfirmPick()
        compose.mainClock.autoAdvance = false
        compose.captureClip("$SCREENSHOTS/clips/autoconfirm-shine.webp",
            "Alice taps Auto, the tip at the right end of Confirm, on her Glimpse pick: the two turn yellow and a band of light sweeps across Confirm and back.",
            millis = 3_200L, node = compose.onNodeWithTag("pod-pick-confirm-row")) { at ->
            if (at == 4 * CLIP_STEP_MILLIS) compose.onNodeWithTag("pod-pick-autoconfirm").performClick()
        }
        compose.onNodeWithTag("pod-pick-autoconfirm").assertIsOn()
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

    // DRF-165, DRF-160 (owner, 2026-11-19): the History icon sits in the same place of the pod's header in every draft mode:
    // Booster's opens the draft history, the others' the Picks dialog; Sealed has none
    @Test fun historyIconInTheSamePlaceInEveryDraftMode() {
        val members = listOf(PodMember(alice), PodMember(bob, packsWaiting = 1))
        var shown = false
        fun iconBounds(settings: DraftSettings, view: PodDetail.() -> PodDetail): androidx.compose.ui.geometry.Rect? {
            val state = opened(detail(PodPhase.DRAFTING, settings, members).view())
            if (shown) { pods.seed(state); settle() } else show(state)
            shown = true
            val tag = if (settings.mode.packPassing) "pod-history" else "pod-picks"
            val nodes = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
            return nodes.singleOrNull()?.boundsInRoot
        }
        val booster = iconBounds(DraftSettings.booster()) { copy(draft = DraftPrivateView("alice", null, 0, emptyList(), round = 1, rounds = 3)) }
        kotlin.test.assertNotNull(booster)
        for (settings in listOf(DraftSettings.winston(), DraftSettings.winchester(), DraftSettings.rochester(), DraftSettings.rotisserie())) {
            val bounds = iconBounds(settings) { copy(limited = LimitedView(settings.mode, "alice", listOf("alice", "bob"), emptyList(), false)) }
            kotlin.test.assertEquals(booster, bounds, "${settings.mode}'s History icon where Booster's is")
        }
        val grid = iconBounds(DraftSettings.grid()) { this }
        kotlin.test.assertEquals(booster, grid, "Grid's History icon where Booster's is")
        compose.onNodeWithTag("pod-picks").performClick()
        compose.onNodeWithText("Picks").assertIsDisplayed()
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

    // DRF-071, DRF-204, DRF-159, DRF-160
    @Test fun winchesterWaiting() {
        val piles = listOf(listOf(copy(1), copy(6)), listOf(copy(11)), listOf(copy(16), copy(21), copy(26)), listOf(copy(29)))
        val members = listOf(PodMember(alice), PodMember(bob, packsWaiting = 1, picking = true))
        val detail = detail(PodPhase.DRAFTING, DraftSettings.winchester(), members, name = "Winchester rematch").copy(
            limited = LimitedView(DraftMode.WINCHESTER, "alice", listOf("alice", "bob"), listOf(copy(3), copy(8)), false,
                currentPlayer = "bob", turn = 3, stack = 22, piles = piles,
                picks = listOf(LimitedPickView("alice", listOf(copy(3)), 1), LimitedPickView("bob", listOf(copy(13), copy(18)), 2),
                    LimitedPickView("alice", listOf(copy(8)), 0))))
        show(opened(detail.copy(members = members)))
        // DRF-160 (owner, 2026-11-19): the History icon in the pod's header, where Booster's is; no Picks button on the Draft tab
        compose.onNodeWithTag("pod-picks").assertIsDisplayed()
        compose.onNodeWithText("Picks").assertDoesNotExist()
        shot("winchester-waiting", "A Winchester draft on Bob's turn: the board stays on screen with its line naming whose pick it " +
            "is (\"Waiting for Bob\") and the stack count, the four face-up piles, and the History icon in the pod's header.")
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

    // DRF-119, DRF-303, DRF-076 (owner, 2026-11-21, MT74XQ): Alice's Arena draft still runs (she has picks left), so Build says
    // when Submit opens over the bottom bar, and Submit is greyed; while her own draft runs there is no Games tab
    @Test fun buildSubmitLockedWhileYourDraftRuns() {
        val (pool, build) = buildCopies()
        val members = listOf(PodMember(alice, packsWaiting = 12, picksMade = 33, picking = true), PodMember(bob, building = true, picksMade = 45))
        val detail = detail(PodPhase.DRAFTING, DraftSettings.arena(), members, name = "Arena night").copy(
            draft = DraftPrivateView("alice", DraftPrivatePack("offer-0-33", 34, listOf(1, 6, 11, 16, 21).map { copy(it, 2) }, 1, null),
                0, pool, round = 34, rounds = 45), build = build, draftWait = PodDraftWait.Picks)
        show(opened(detail, PodTab.BUILD))
        compose.onNodeWithTag("pod-build-submit-wait").assertTextEquals("Submit opens after your last pick")
        compose.onNode(hasClickAction() and hasText("Submit deck", substring = true)).assertIsNotEnabled()
        compose.onNodeWithText("Games").assertDoesNotExist()
        shot("build-submit-locked", "Build during an Arena draft with Alice's picks not over yet (the Draft · Build · Stats " +
            "tabs): over the bottom bar a line says \"Submit opens after your last pick\", and Submit deck is greyed though her deck " +
            "is ready.")
    }

    // DRF-119, DRF-121, DRF-122 (owner, 2026-11-21): Alice's own draft is done and her deck submitted while the pod still drafts:
    // the Games tab lists Bob as "drafting" and Carol as "building" (their Challenge buttons come with the lobby, not shown here)
    @Test fun gamesTabWhileOthersStillDraft() {
        val members = listOf(PodMember(alice, submitted = true, picksMade = 45), PodMember(bob, packsWaiting = 3, picksMade = 42, picking = true),
            PodMember(carol, building = true, picksMade = 45))
        val detail = detail(PodPhase.DRAFTING, DraftSettings.arena(), members, name = "Arena night").copy(
            draft = DraftPrivateView("alice", null, 0, emptyList(), round = 45, rounds = 45), submitted = true)
        show(opened(detail, PodTab.GAMES))
        compose.onNodeWithText("drafting").assertIsDisplayed()
        compose.onNodeWithText("building").assertIsDisplayed()
        shot("games-while-drafting", "The Games tab of an Arena pod still drafting, after Alice submitted early: Alice \"deck submitted\", " +
            "Bob \"drafting\" and Carol \"building\", and the four tabs Draft · Build · Stats · Games.")
    }

    // DRF-013 (owner, 2026-11-21): pods still drafting read "Build your deck" once your own draft is done and "Deck submitted" once
    // you have submitted
    @Test fun podsListWhileOthersStillDraft() {
        fun pod(id: String, name: String, attention: PodAttention, submitted: Boolean = false) = PodSummary(id, name, PodPhase.DRAFTING,
            alice, listOf(alice, bob, carol), attention, 0, "2026-11-0${id.last()}T00:00:00Z", DraftMode.ARENA, submitted = submitted)
        show(PodState(loaded = true, pods = listOf(pod("p1", "Arena night", PodAttention.BUILD),
            pod("p2", "Arena league", PodAttention.NONE, submitted = true), pod("p3", "Arena rematch", PodAttention.NONE))))
        compose.onNodeWithText("Build your deck").assertIsDisplayed()
        compose.onNodeWithText("Deck submitted").assertIsDisplayed()
        compose.onNodeWithText("Drafting").assertIsDisplayed()
        shot("pods-early-submit", "Three Arena pods whose drafts still run: in Arena night Alice's own draft is done (\"Build your deck\"), " +
            "in Arena league she has submitted early (\"Deck submitted\"), in Arena rematch she still waits for her next offer (\"Drafting\"); the one needing her comes first.")
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
        shot("stats-notes", "The Stats tab during a Booster draft, scrolled down past the Colors chart to Alice's private deck " +
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
