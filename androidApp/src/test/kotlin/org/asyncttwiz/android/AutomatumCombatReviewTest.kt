package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.setup.result
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ParsedCard
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Adversarial review of the combat area redesign (I-D32, I-D38), I-D39, I-D40 and I-D42 through the app's controllers
 * (report `arcanum-combat-review.md`, outside the repo). Each ignored test pins a problem found and fails while it isn't
 * fixed; the others are checks that hold. Invented cards parsed from their oracle text as on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumCombatReviewTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, oracle: OracleCard, extra: List<Ability> = emptyList()) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = OracleParser.parse(oracle).let { parsed ->
            arcanum.api.CardDefinition(Layout.Normal, listOf(parsed.copy(abilities = parsed.abilities + extra)))
        }
    }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String, pt: String? = null,
        shown: String = text, extra: List<Ability> = emptyList()) = Invented(
        DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = shown, corner = pt.orEmpty()),
        OracleCard(name, cost, type, text, pt?.substringBefore("/"), pt?.substringAfter("/")), extra)
    private val brute = invented("brute", "Ironhide Brute", "{3}{G}", "Creature — Beast", "Trample", "5/5")
    private val bear = invented("bear", "Stout Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = invented("ogre", "Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val warden = invented("warden", "Gate Warden", "{2}{W}", "Creature — Human Soldier", "", "3/3",
        shown = "Gate Warden can block an additional creature each combat.",
        extra = listOf(Ability.Static(listOf(Mechanic.CanBlockAdditional(1)), Affected.Objects(Objects.Self))))
    private val hound = invented("hound", "Dusk Hound", "{1}{B}", "Creature — Dog", "Menace", "2/2")
    private val squire = invented("squire", "Dawnlance Squire", "{1}{W}", "Creature — Human Knight", "First strike", "2/1")
    private val walker = Invented(DefinitionFace("walker", "Oath Seer", typeLine = "Legendary Planeswalker — Seer", manaCost = "{2}{U}{U}",
        corner = "4"), OracleCard("Oath Seer", "{2}{U}{U}", "Legendary Planeswalker — Seer", loyalty = "4"))
    private val all = listOf(brute, bear, ogre, warden, hound, squire, walker)

    private val aHand = f.id("a/HAND")
    private val bHand = f.id("b/HAND")
    private val aField = f.id("a/BATTLEFIELD")
    private val bField = f.id("b/BATTLEFIELD")

    @Before fun definitions() {
        val byDigest = all.associate { it.submitted.digest to it.definition }
        AutomatumBoards.definitionSource = { submitted -> submitted.mapNotNull { s ->
            byDigest[s.digest]?.let { s.digest to ParsedCard.Read(it, 0) } }.toMap() }
    }
    @After fun forget() {
        kotlinx.coroutines.runBlocking(AutomatumBoards.boardWorker) {}
        AutomatumBoards.reset(); AutomatumBoards.definitionSource = null
    }

    /** Automatum on, turn 3 of Alice at [position]: the first cards of each Battlefield become [aliceField] and
     * [bobField] (there since turn 1); every other Battlefield and hand card is out of the game. */
    private fun board(position: TurnPosition, priority: MtgPriority?, aliceField: List<Invented>, bobField: List<Invented>): PlayState {
        val initial = f.initial
        fun first(container: String, n: Int) = initial.board.containers.single { it.id == container }.cards.take(n)
        val roles = (first(aField, aliceField.size).zip(aliceField) + first(bField, bobField.size).zip(bobField)).toMap()
        assertEquals(aliceField.size + bobField.size, roles.size, "the fixture has room for the cards")
        val dropped = initial.board.cards.filter { it.containerId in setOf(aField, bField, aHand, bHand) && it.id !in roles }.map { it.id }.toSet()
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            board = initial.board.copy(automatum = true, priority = priority,
                turn = TurnState(f.a, "3", position.phase, position.step),
                cards = initial.board.cards.filter { it.id !in dropped }.map { card -> roles[card.id]?.let { what ->
                    card.copy(definition = what.submitted.digest, faceId = what.face.faceId, corner = what.face.corner, tapped = false,
                        enteredTurn = "1")
                } ?: card },
                containers = initial.board.containers.map { it.copy(cards = it.cards.filter { id -> id !in dropped }) }))
    }

    private fun card(play: PlayState, what: Invented, nth: Int = 0) =
        play.board.cards.filter { it.definition == what.submitted.digest }[nth].id

    // ---- The game as the server keeps it

    private var tree: GameplayTree? = null
    private val live = mutableStateOf<PlayState?>(null)
    private var requests = 0
    private val play get() = live.value!!
    /** Requests the server refused (a stale draft, a race): the reason. */
    private val refused = mutableListOf<String>()

    private fun start(initial: PlayState) { tree = GameplayTree(initial); live.value = initial }
    private fun edit(change: (BoardState) -> BoardState) { live.value = play.copy(board = change(play.board)); tree = GameplayTree(play) }

    private fun accept(actor: String, action: GameAction, request: String = f.id("request/${requests++}")): Boolean {
        val history = tree!!
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), action, f.id("client"), "1")
        val change = if (action is GameAction.Play) history.play(actor, command) else PlayBatches.change(history, actor, command)
        if (change.reason != null) { refused += "${change.reason}: $action".take(300); return false }
        tree = change.tree
        live.value = tree!!.stateAt(tree!!.currentNodeId)
        return true
    }

    private fun toolkit(viewer: String, work: AutomatumWork = AutomatumWork.Inline) = TableToolkitController(viewer, { live.value }, { true },
        { action, _ -> accept(viewer, GameAction.Play(action)) },
        playBatch = { actions, request -> accept(viewer, GameAction.PlayBatch(actions), request) },
        playTree = { tree }, automatedBatch = { batch, request -> accept(viewer, batch, request) }, automatumWork = work)

    private fun tags() = engineTags(tree, play.currentNodeId)
    private fun scene(viewer: String) = TablePresentation.scene(f.game, play, viewer, 412f, 892f, false)
    private fun tap(toolkit: TableToolkitController, item: TableItem) = toolkit.open(TableIntent.Actions(item.id, null), item, TableSelection())
    private fun tapCard(toolkit: TableToolkitController, card: String) = tap(toolkit,
        scene(toolkit.viewer).items.first { it.cardId == card && it.viewport != "combat" && it.zone == PlayZone.BATTLEFIELD })
    private fun tapCopy(toolkit: TableToolkitController, card: String) = tap(toolkit,
        scene(toolkit.viewer).items.first { it.viewport == "combat" && it.sourceId == card })
    private fun quick(toolkit: TableToolkitController) = automatumCombatQuickActions(play, toolkitQuickContext(play, TableSelection(), toolkit.viewer)
        .copy(automatumCombat = AutomatumBoards.combat(play) { tags() })
        .withCombatTarget(toolkit.combat, play, "Bob"), TableActionScope(play.gameId, play.id)).orEmpty()
    private fun perform(toolkit: TableToolkitController, name: String) = quick(toolkit).single { it.name == name }.let { action ->
        assertTrue(action.enabled, "$name: ${action.refused}")
        toolkit.perform(action.intent)
    }
    private fun life(player: String) = play.board.players.single { it.accountId == player }.life.toInt()

    private fun render(toolkit: TableToolkitController) {
        AutomatumBoards.combat(play) { tags() }
        AutomatumBoards.sharedFlow(play, tags())
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, live.value!!, toolkit.viewer, false, Modifier.fillMaxSize(), connected = true,
                            onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, redo = { true }, controlsConnected = true))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Alice taps [cards] to stage them and commits; Bob's declare blockers step follows. */
    private fun attackWith(vararg cards: String) {
        val alice = toolkit(f.a)
        cards.forEach { tapCard(alice, it) }
        assertTrue(perform(alice, "Commit attack (${cards.size})"), alice.error)
        if (TurnPosition.of(play.board.turn) == TurnPosition.DECLARE_ATTACKERS) assertTrue(alice.flows.step(StepFlow.Until.Pass), alice.error)
        assertEquals(TurnPosition.DECLARE_BLOCKERS, TurnPosition.of(play.board.turn))
    }

    // combatreview #1 — CMB-007, I-D38 (owner feedback on the wireframes: "the combat overlay sits on top of the
    // battlefield, sized to its content; the battlefield shows around it, no empty band"): the area is still a band
    // between the battlefields, as high as the area, painted in the battlefields' colours. Opening it pushes the two
    // battlefields apart, and the coloured space beside the white panel isn't battlefield: a card dropped there (it looks
    // like the player's own battlefield) joins combat (the area's end drop regions span the band) or lands nowhere
    @Ignore("combatreview #1: the combat area is a band between the battlefields, not an overlay on them")
    @Test fun theCombatAreaSitsOnTheBattlefield() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute, bear), bobField = listOf(ogre)))
        val before = scene(f.a)
        val alice = toolkit(f.a)
        tapCard(alice, card(play, brute))
        val after = scene(f.a)
        assertEquals(before.fields.last().gridBounds.y, after.fields.last().gridBounds.y, "Alice's battlefield doesn't move down")
        val port = after.viewports.single { it.id == "combat" }.bounds
        val panelLeft = after.combatLabels.minOf { it.bounds.x }
        assertTrue(panelLeft - port.x > 20f, "there is room beside the panel")
        // Just beside the panel, level with its attacker row (Alice's half of the band)
        val point = TablePoint(port.x + (panelLeft - port.x) / 2, port.bottom - 8f)
        assertTrue(after.fields.any { it.bounds.contains(point) }, "beside the panel is battlefield")
        assertEquals(null, after.combatDestination(point, TableScope(card(play, bear), listOf(card(play, bear)))),
            "a drop beside the panel doesn't join combat")
    }

    // combatreview #2 — CMB-029, CMB-035 (screenshot combat-preview.png): the chips of attackers side by side overlap at the
    // default card size: the Brute's "trample" and "2 → Bob" and the Bear's "2 → Bob" are each centred over their own
    // small card but wider than it, so they run into each other (the pictures read "2 · 2 → Bob": combat-blocking.png,
    // combat-preview.png, combat-blocking-landscape.png, where they also run into the eye)
    @Ignore("combatreview #2: preview and keyword chips of neighbouring attackers overlap")
    @Test fun chipsOfNeighbouringAttackersDoNotOverlap() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute, bear), bobField = listOf(ogre)))
        val (bruteCard, bearCard) = listOf(card(play, brute), card(play, bear))
        attackWith(bruteCard, bearCard)
        val bob = toolkit(f.b)
        tapCard(bob, card(play, ogre))
        tapCopy(bob, bruteCard)
        // Bob's view while he arranges his blocks: the preview shows them (Alice has nothing to respond with, so once
        // declared the damage follows at once)
        render(bob)
        val chips = (compose.onAllNodesWithContentDescription("2 → Bob").fetchSemanticsNodes() +
            compose.onAllNodesWithContentDescription("trample").fetchSemanticsNodes()).map { it.boundsInRoot }
        assertEquals(3, chips.size)
        for (i in chips.indices) for (j in chips.indices) if (i < j)
            assertFalse(chips[i].overlaps(chips[j]), "chips overlap: ${chips[i]} and ${chips[j]}")
    }

    // combatreview #5 — CMB-013, CMB-029, SYS-055: with Automatum on a planeswalker's loyalty is its loyalty counters
    // (typed counters, what Automatum changes when it is dealt damage); the target header reads the card's corner, the
    // printed or hand-set loyalty, so it shows 4 (and the preview "4 → 1") for a planeswalker with 2 loyalty left
    @Ignore("combatreview #5: a planeswalker's header shows its corner, not its loyalty counters")
    @Test fun aPlaneswalkerHeaderShowsItsLoyalty() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute), bobField = listOf(walker, ogre)))
        val seer = card(play, walker)
        edit { b -> b.copy(cards = b.cards.map { if (it.id == seer) it.copy(typedCounters = listOf(TypedCounter("loyalty", 2, 0))) else it }) }
        tapCard(toolkit(f.a), card(play, brute))
        assertEquals("2", scene(f.a).combatLabels.single { it.cardId == seer }.value)
    }

    // combatreview #6 — I-D27: a tap that stages the other player's face-down creature, which can't attack yet, is
    // refused at once with a fallback naming the card ("Hill Ogre can't attack the player now."), shown until the
    // engine's reason (which hides it) is worked out off the main thread, as the app does
    @Ignore("combatreview #6: a staging refusal names a face-down creature until the engine's reason arrives")
    @Test fun aStagingRefusalDoesNotNameAFaceDownCreature() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute, ogre), bobField = listOf(warden)))
        val hidden = card(play, ogre)
        edit { b -> b.copy(cards = b.cards.map { if (it.id == hidden) it.copy(covered = true, explicitlyCovered = true, enteredTurn = "3") else it }) }
        val waiting = object : AutomatumWork { override fun <T> run(work: () -> T, done: (T) -> Unit) = Unit }
        val bobsDevice = toolkit(f.b, waiting)
        tapCard(bobsDevice, hidden)
        assertNotNull(bobsDevice.error)
        assertTrue("Hill Ogre" !in bobsDevice.error!!, "shown to Bob: ${bobsDevice.error}")
    }

    // combatreview #6 — I-D27: while Bob declares blockers, Alice (all actions are open to both players) taps Bob's
    // face-down creature, which is tapped and can't block: "Hill Ogre can't block now." names it
    @Ignore("combatreview #6: a blocking refusal names a face-down creature to the other player")
    @Test fun aBlockingRefusalDoesNotNameAFaceDownCreature() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute), bobField = listOf(ogre, warden)))
        val hidden = card(play, ogre)
        edit { b -> b.copy(cards = b.cards.map { if (it.id == hidden) it.copy(covered = true, explicitlyCovered = true, tapped = true) else it }) }
        attackWith(card(play, brute))
        val alice = toolkit(f.a)
        tapCard(alice, hidden)
        assertNotNull(alice.error)
        assertTrue("Hill Ogre" !in alice.error!!, "shown to Alice: ${alice.error}")
    }

    // combatreview #7 — CMB-032, CMB-034: the selection is the viewer's own and is never dropped when the table moves on.
    // Bob selects his Gate Warden (can block two) and blocks the Stout Bear with it, so it stays selected; Alice declares
    // his blocks for him (confirmed, I-D40). In Alice's next combat Bob's first tap on her attacker's copy, meant to select
    // it (CMB-032), blocks it with the Warden selected two turns earlier
    @Ignore("combatreview #7: a blocker selection survives its declaration and blocks in a later combat")
    @Test fun aSelectionDoesNotOutliveItsCombat() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute, bear), bobField = listOf(warden)))
        val bruteCard = card(play, brute)
        val wardenCard = card(play, warden)
        attackWith(bruteCard, card(play, bear))
        val bob = toolkit(f.b)
        tapCard(bob, wardenCard)
        tapCopy(bob, card(play, bear))
        assertEquals(wardenCard, bob.combat.selected, "it can block one more")
        val alice = toolkit(f.a)
        assertFalse(alice.flows.step(StepFlow.Until.Pass))
        assertTrue(alice.flows.confirmActing(), alice.error)
        // On to Alice's next declare attackers, every step through the step button
        val devices = mapOf(f.a to alice, f.b to bob)
        var guard = 60
        while (!(play.board.turn.number == "5" && TurnPosition.of(play.board.turn) == TurnPosition.DECLARE_ATTACKERS) && guard-- > 0) {
            val flow = AutomatumBoards.sharedFlow(play, tags())!!
            val decision = flow.decision
            when {
                decision != null -> devices.getValue(decision.player.value).let { d -> d.flows.show(flow)
                    assertTrue(d.flows.answer(if (decision is Decision.Divide) Answer.Division(decision.suggested) else flow.listedAnswers().first()), d.error) }
                // The step button: with a declaration awaited it declares what the lanes hold, here nothing (TRN-057)
                else -> devices.getValue(flow.holder ?: flow.declaring!!).let { d -> assertTrue(d.flows.step(StepFlow.Until.Pass), d.error) }
            }
        }
        assertEquals("5", play.board.turn.number)
        assertEquals(aField, play.board.cards.single { it.id == bruteCard }.containerId, "the Brute is still there")
        attackWith(bruteCard)
        tapCopy(bob, bruteCard)
        assertTrue(CombatStackRules.blocks(play.board.combat).isEmpty(), "Bob's tap selected the Brute; it blocked nothing")
        assertEquals(bruteCard, bob.combat.selected)
    }

    // I-D40, TRN-061: Alice's step button while Bob declares blockers asks first; Bob changes his blocks meanwhile, so her
    // confirmation is refused ("The table changed") and nothing is declared for him
    @Test fun aConfirmationAfterTheOtherPlayerActedIsRefused() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute), bobField = listOf(ogre)))
        val bruteCard = card(play, brute)
        attackWith(bruteCard)
        val alice = toolkit(f.a)
        assertFalse(alice.flows.step(StepFlow.Until.Pass))
        assertNotNull(alice.flows.acting)
        val bob = toolkit(f.b)
        tapCard(bob, card(play, ogre))
        tapCopy(bob, bruteCard)
        assertFalse(alice.flows.confirmActing())
        assertEquals("The table changed: try again.", alice.error)
        assertEquals(TurnPosition.DECLARE_BLOCKERS, TurnPosition.of(play.board.turn))
    }

    // ---- Long seeded games through the app's controllers

    /**
     * Seeded games of several turns played only through the controllers a device uses: the step button (flows.step) for
     * whoever holds priority, taps to stage attackers and assign blockers, the combat quick actions, and answers to open
     * decisions (divisions with the suggestion). Checked: no refusal of an action the controllers offered, no request
     * refused by the server, and, after blockers are declared, the preview's life changes are what combat then does when
     * everyone passes (I-D29, CMB-029).
     */
    @Test fun seededGamesWithCombatThroughTheControllers() {
        val problems = mutableListOf<String>()
        var checked = 0
        for (seed in 1..4) {
            AutomatumBoards.reset(); definitions()
            refused.clear()
            val random = Random(seed)
            start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(brute, bear, hound),
                bobField = listOf(ogre, warden, squire)))
            val devices = mapOf(f.a to toolkit(f.a), f.b to toolkit(f.b))
            var expected: Map<String, Int>? = null
            var lifeAtBlocks: Map<String, Int> = emptyMap()
            var combatTurn: String? = null
            var guard = 0
            fun note(what: String) { problems += "seed $seed, turn ${play.board.turn.number} ${play.board.turn.step}: $what" }
            while (play.board.turn.number.toInt() < 9 && guard++ < 400) {
                val position = TurnPosition.of(play.board.turn)
                // The preview made when blocks were declared, checked once that combat is over
                if (expected != null && (play.board.turn.number != combatTurn || position !in COMBAT)) {
                    val actual = listOf(f.a, f.b).associateWith { life(it) - lifeAtBlocks.getValue(it) }.filterValues { it != 0 }
                    if (actual != expected.filterValues { it != 0 }) note("preview said $expected, combat did $actual")
                    expected = null
                    checked++
                }
                val flow = AutomatumBoards.sharedFlow(play, tags()) ?: run { note("no shared flow"); break }
                if (flow.state.result != null) break
                val decision = flow.decision
                when {
                    decision != null -> {
                        val device = devices.getValue(decision.player.value)
                        device.flows.show(flow)
                        val answer = if (decision is Decision.Divide) Answer.Division(decision.suggested) else flow.listedAnswers().firstOrNull()
                        if (answer == null || !device.flows.answer(answer)) { note("answer refused: ${device.error} ($decision)"); break }
                    }
                    flow.declaring != null && flow.declaring == play.board.turn.accountId -> {
                        val device = devices.getValue(flow.declaring!!)
                        val mine = play.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == device.viewer }.cards
                        for (c in mine) if (random.nextInt(3) > 0) { tapCard(device, c); device.dismissError() }
                        val commit = quick(device).last()
                        if (!commit.enabled) { perform(device, "Clear") }
                        val action = quick(device).last()
                        if (!action.enabled || !device.perform(action.intent)) { note("${action.name} refused: ${action.refused ?: device.error}"); break }
                    }
                    flow.declaring != null -> {
                        val device = devices.getValue(flow.declaring!!)
                        val attackers = play.board.combat?.pairs.orEmpty().flatMap { it.attackers }.map { it.sourceId }
                        val mine = play.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == device.viewer }.cards
                        for (c in mine) if (attackers.isNotEmpty() && random.nextBoolean()) {
                            tapCard(device, c)
                            if (device.combat.selected == c) tapCopy(device, attackers.random(random))
                            device.combat.clearSelection(); device.dismissError()
                        }
                        val commit = quick(device).single { it.name == "Commit blocks" }
                        val action = if (commit.enabled) commit else quick(device).single { it.name == "No blocks" }
                        // What the table previews for the blocks as declared (CMB-029), before they go out
                        if (commit.enabled || CombatStackRules.blocks(play.board.combat).isEmpty()) {
                            expected = AutomatumBoards.combat(play) { tags() }.preview?.life
                            lifeAtBlocks = listOf(f.a, f.b).associateWith { life(it) }; combatTurn = play.board.turn.number
                        }
                        if (!action.enabled || !device.perform(action.intent)) { note("${action.name} refused: ${action.refused ?: device.error}"); break }
                    }
                    flow.holder != null -> {
                        val device = devices.getValue(flow.holder!!)
                        if (!device.flows.step(StepFlow.Until.Pass)) { note("pass refused: ${device.error}"); break }
                    }
                    else -> { note("nobody to act"); break }
                }
                if (refused.isNotEmpty()) { note("server refused: $refused"); break }
            }
            assertTrue(play.board.turn.number.toInt() >= 5 || problems.isNotEmpty(), "seed $seed got to turn ${play.board.turn.number}")
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
        assertTrue(checked >= 8, "only $checked combats had their preview checked")
    }

    private val COMBAT = setOf(TurnPosition.DECLARE_ATTACKERS, TurnPosition.DECLARE_BLOCKERS, TurnPosition.FIRST_STRIKE_DAMAGE,
        TurnPosition.COMBAT_DAMAGE)
}
