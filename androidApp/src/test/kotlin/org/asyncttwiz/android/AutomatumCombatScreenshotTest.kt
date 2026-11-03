package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.engine.decision.Decision
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.automatum.EngineBatch
import org.asyncttwiz.automatum.EngineCall
import org.asyncttwiz.automatum.LaneBlock
import org.asyncttwiz.automatum.PreAnswers
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ParsedCard
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Automatum I3b and the combat area redesign (integration spec I-D29, I-D32, I-D33, I-D36): drags as Automatum actions;
 * attackers staged by taps, blockers assigned by taps (a blocker of two with two arrows), the damage preview with no
 * separate prompt, the division dialog In order and Free, the defender's division given early, manual blocks by arrows
 * with Automatum off, and a Stack entry below the top picked as a target.
 * Invented cards parsed from their oracle text as on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumCombatScreenshotTest {
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
    // CR 509.1a; the wording isn't parsed yet, so its ability is given as the engine's own
    private val warden = invented("warden", "Gate Warden", "{2}{W}", "Creature — Human Soldier", "", "3/3",
        shown = "Gate Warden can block an additional creature each combat.",
        extra = listOf(Ability.Static(listOf(Mechanic.CanBlockAdditional(1)), Affected.Objects(Objects.Self))))
    private val spark = invented("spark", "Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
    private val quash = invented("quash", "Quash", "{R}", "Instant", "Counter target spell.")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain", "")
    private val hound = invented("hound", "Dusk Hound", "{1}{B}", "Creature — Dog", "Menace", "2/2")
    private val walker = Invented(DefinitionFace("walker", "Oath Seer", typeLine = "Legendary Planeswalker — Seer", manaCost = "{2}{U}{U}",
        corner = "4"), OracleCard("Oath Seer", "{2}{U}{U}", "Legendary Planeswalker — Seer", loyalty = "4"))
    private val wall = invented("wall", "Stone Wall", "{2}", "Artifact Creature — Wall", "", "0/4")
    private val all = listOf(brute, bear, ogre, warden, spark, quash, mountain, hound, walker, wall)

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
        // The table's engine work still queued on the shared worker finishes before the caches are cleared, so none of
        // it lands in the next test's caches or competes with it
        kotlinx.coroutines.runBlocking(AutomatumBoards.boardWorker) {}
        AutomatumBoards.reset(); AutomatumBoards.definitionSource = null
    }

    /**
     * Automatum on, turn 3 of Alice at [position] with [priority]: the first cards of each Battlefield and hand become
     * [aliceField], [bobField], [aliceHand] and [bobHand] (creatures there since turn 1); every other Battlefield and hand
     * card is out of the game.
     */
    private fun board(position: TurnPosition, priority: MtgPriority?, aliceField: List<Invented>, bobField: List<Invented>,
        aliceHand: List<Invented> = emptyList(), bobHand: List<Invented> = emptyList()): PlayState {
        val initial = f.initial
        fun first(container: String, n: Int) = initial.board.containers.single { it.id == container }.cards.take(n)
        val roles = (first(aField, aliceField.size).zip(aliceField) + first(bField, bobField.size).zip(bobField) +
            first(aHand, aliceHand.size).zip(aliceHand) + first(bHand, bobHand.size).zip(bobHand)).toMap()
        val dropped = initial.board.cards.filter { it.containerId in setOf(aField, bField, aHand, bHand) && it.id !in roles }.map { it.id }.toSet()
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            board = initial.board.copy(automatum = true, priority = priority,
                turn = TurnState(f.a, "3", position.phase, position.step),
                cards = initial.board.cards.filter { it.id !in dropped }.map { card -> roles[card.id]?.let { what ->
                    card.copy(definition = what.submitted.digest, faceId = what.face.faceId, corner = what.face.corner, tapped = false,
                        covered = card.containerId == aHand || card.containerId == bHand, enteredTurn = if (card.containerId in setOf(aField, bField)) "1" else "")
                } ?: card },
                containers = initial.board.containers.map { it.copy(cards = it.cards.filter { id -> id !in dropped }) }))
    }

    private fun card(play: PlayState, what: Invented, container: String? = null) =
        play.board.cards.first { it.definition == what.submitted.digest && (container == null || it.containerId == container) }.id

    // ---- The game as the server keeps it

    private var tree: GameplayTree? = null
    private val live = mutableStateOf<PlayState?>(null)
    private var requests = 0
    private val play get() = live.value!!

    private fun start(initial: PlayState) { tree = GameplayTree(initial); live.value = initial }

    private fun accept(actor: String, action: GameAction, request: String = f.id("request/${requests++}")): Boolean {
        val history = tree!!
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), action, f.id("client"), "1")
        val change = if (action is GameAction.Play) history.play(actor, command) else PlayBatches.change(history, actor, command)
        assertNull(change.reason, "the server takes $action")
        tree = change.tree
        live.value = tree!!.stateAt(tree!!.currentNodeId)
        return true
    }

    private fun toolkit(viewer: String) = TableToolkitController(viewer, { live.value }, { true },
        { action, _ -> accept(viewer, GameAction.Play(action)) },
        playBatch = { actions, request -> accept(viewer, GameAction.PlayBatch(actions), request) },
        playTree = { tree }, automatedBatch = { batch, request -> accept(viewer, batch, request) })

    /** One engine call made by [body] on the board, sent as [actor]'s automated batch. */
    private fun engine(actor: String, body: (EngineCall) -> Unit) {
        val definitions = AutomatumBoards.definitions(play)
        val call = EngineCall(play, definitions, null, AutomatumBoards.engineLog(play, definitions, engineTags(tree, play.currentNodeId))) { "seed" }
        body(call)
        val request = f.id("request/${requests++}")
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), GameAction.PlayBatch(listOf(PlayAction.PassAttention)), f.id("client"), "1")
        accept(actor, EngineBatch.plan(call, actor, command).action, request)
    }

    /** The landscape tests' table layout (the sidebar). */
    private var landscape = false

    private fun render(toolkit: TableToolkitController) {
        // Worked out before showing, as the table's worker would have (SYS-025), so the pictures don't race it
        if (play.board.automatum) {
            AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) }
            AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))
        }
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, live.value!!, toolkit.viewer, landscape, Modifier.fillMaxSize(), connected = true,
                            onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, redo = { true }, controlsConnected = true))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun life(player: String) = play.board.players.single { it.accountId == player }.life.toInt()
    private fun zone(card: String) = play.board.containers.single { c -> c.id == play.board.cards.single { it.id == card }.containerId }.zone
    private fun pairOf(card: String) = play.board.combat!!.pairs.single { p -> p.attackers.any { it.sourceId == card } }

    // ---- Drags (I-D36)

    // ZON-077, ZON-004, TRN-031, I-D36, I-D28: with Automatum on a hand land dragged onto the Battlefield is played through Automatum, a
    // spell dragged onto the Stack opens the cast; moving a card by hand to another zone and dragging life are refused
    @Test fun dragsAreAutomatumActions() {
        start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(mountain), bobField = listOf(ogre),
            aliceHand = listOf(mountain, spark)))
        val alice = toolkit(f.a)
        val land = card(play, mountain, aHand)
        val sparkCard = card(play, spark, aHand)
        val fieldLand = card(play, mountain, aField)
        assertTrue(alice.handle(TableIntent.Move(TableScope(land, listOf(land)), TableDestination.Grid(aField, 2, 0), drag = true)), alice.error)
        assertEquals(PlayZone.BATTLEFIELD, zone(land), "played through Automatum")
        assertTrue(play.board.engineRecords.any { it.id == "turn" }, "counted as the turn's land play")
        // A card moved by hand out of its zone, life dragged: refused with the reason
        assertFalse(alice.handle(TableIntent.Move(TableScope(fieldLand, listOf(fieldLand)),
            TableDestination.Container(f.id("a/GRAVEYARD"), PlayZone.GRAVEYARD), drag = true)))
        assertEquals(AutomatumDrags.MANUAL_MOVE, alice.error)
        assertFalse(alice.handle(TableIntent.PlayerDelta(f.b, null, -2)))
        assertEquals(AutomatumDrags.LIFE, alice.error)
        // Within its own Battlefield a card only changes place
        assertTrue(alice.handle(TableIntent.Move(TableScope(fieldLand, listOf(fieldLand)), TableDestination.Grid(aField, 5, 1), drag = true)))
        assertEquals(PlayZone.BATTLEFIELD, zone(fieldLand))
        // The spell dragged onto the Stack: the cast opens, its targets chosen on the table
        val stack = play.board.containers.single { it.zone == PlayZone.STACK }.id
        assertTrue(alice.handle(TableIntent.Move(TableScope(sparkCard, listOf(sparkCard)), TableDestination.Container(stack, PlayZone.STACK), drag = true)))
        assertTrue(alice.cast.targeting)
        render(alice)
        compose.onNodeWithContentDescription("Choose targets").assertExists()
        captureShot("$SCREENSHOTS/automatum/drag-cast.png",
            "With Automatum on Alice dragged a Mountain from her hand onto the Battlefield (played through Automatum) and then Storm Spark onto the Stack: its cast opens with the target bar over her hand while legal targets glow.")
    }

    // ---- The combat area with Automatum (I-D32)

    private fun scene(viewer: String) = TablePresentation.scene(f.game, play, viewer, 412f, 892f, false)
    private fun tap(toolkit: TableToolkitController, item: TableItem) = toolkit.open(TableIntent.Actions(item.id, null), item, TableSelection())
    /** Taps [card] on its Battlefield as [toolkit]'s player sees it. */
    private fun tapCard(toolkit: TableToolkitController, card: String) = tap(toolkit,
        scene(toolkit.viewer).items.first { it.cardId == card && it.viewport != "combat" && it.zone == PlayZone.BATTLEFIELD })
    /** Taps [card]'s combat copy as [toolkit]'s player sees it. */
    private fun tapCopy(toolkit: TableToolkitController, card: String) = tap(toolkit,
        scene(toolkit.viewer).items.first { it.viewport == "combat" && it.sourceId == card })
    private fun quick(toolkit: TableToolkitController) = automatumCombatQuickActions(play, toolkitQuickContext(play, TableSelection(), toolkit.viewer)
        .copy(automatumCombat = AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) })
        .withCombatTarget(toolkit.combat, play, "Bob"), TableActionScope(play.gameId, play.id)).orEmpty()
    private fun perform(toolkit: TableToolkitController, name: String) = quick(toolkit).single { it.name == name }.let { action ->
        assertTrue(action.enabled, "$name: ${action.refused}")
        toolkit.perform(action.intent)
    }
    private fun blocks() = CombatStackRules.blocks(play.board.combat).map { (b, a) -> b.sourceId to a.sourceId }

    /** Alice's declare attackers step: Ironhide Brute and Stout Bear against Bob's [blockers]. */
    private fun attackers(vararg blockers: Invented = arrayOf(ogre), aliceHand: List<Invented> = emptyList(),
        aliceField: List<Invented> = listOf(brute, bear)) {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = aliceField, bobField = blockers.toList(), aliceHand = aliceHand))
    }

    /** Alice stages [cards] by tapping them and commits the attack; Bob's declare blockers step follows. */
    private fun attackWith(vararg cards: String) {
        val alice = toolkit(f.a)
        cards.forEach { tapCard(alice, it) }
        assertTrue(perform(alice, "Commit attack (${cards.size})"), alice.error)
        // Alice may hold priority after declaring (an instant in hand): she passes on to Bob's blocks
        if (TurnPosition.of(play.board.turn) == TurnPosition.DECLARE_ATTACKERS)
            assertTrue(alice.flows.step(org.asyncttwiz.automatum.StepFlow.Until.Pass), alice.error)
        assertEquals(TurnPosition.DECLARE_BLOCKERS, TurnPosition.of(play.board.turn))
    }

    private fun stagingFlow(shot: String) {
        attackers()
        // The Bear entered this turn: it can't attack yet (CR 302.6)
        live.value = play.copy(board = play.board.copy(cards = play.board.cards.map { if (it.id == card(play, bear)) it.copy(enteredTurn = "3") else it }))
        tree = GameplayTree(play)
        val alice = toolkit(f.a)
        val (bruteCard, bearCard) = listOf(card(play, brute), card(play, bear))
        tapCard(alice, bruteCard)
        assertEquals(listOf<String?>(null), play.board.combat!!.pairs.map { it.attackTarget }, "staged to the defending player's lane")
        assertEquals(listOf("Rest → Bob", "Clear", "Commit attack (1)"), quick(alice).map { it.name })
        assertFalse(quick(alice).first().enabled, "nothing else may attack")
        tapCard(alice, bruteCard)
        assertNull(play.board.combat?.pairs?.firstOrNull { p -> p.attackers.isNotEmpty() }, "tapped again: unstaged")
        assertEquals(listOf("Rest → Bob", "Clear", "No attack"), quick(alice).map { it.name })
        tapCard(alice, bearCard)
        assertNotNull(alice.error, "the summoning-sick Bear says why")
        assertTrue(alice.error!!.contains("Stout Bear"), alice.error)
        assertTrue(perform(alice, "Rest → Bob"), alice.error)
        assertEquals(listOf(bruteCard), play.board.combat!!.pairs.flatMap { it.attackers }.map { it.sourceId })
        alice.dismissError()
        render(alice)
        compose.onNodeWithContentDescription("Commit attack (1)").assertExists()
        captureShot("$SCREENSHOTS/automatum/$shot.png",
            "With Automatum on Alice reached Declare attackers and tapped Ironhide Brute: it is staged in Bob's lane, framed in violet as the active target, with a violet → Bob chip over the real card; the summoning-sick Stout Bear stays out, and the quick actions read Rest → Bob, Clear and Commit attack (1).")
        compose.onNodeWithContentDescription("Commit attack (1)").performClick()
        compose.waitForIdle()
        assertTrue(play.board.cards.single { it.id == bruteCard }.tapped, "attacking taps it (CR 508.1f)")
        assertEquals(TurnPosition.DECLARE_BLOCKERS, TurnPosition.of(play.board.turn))
        assertTrue(CombatFlow.throughEngine(play.board))
    }

    // CMB-028, CMB-035, CR 508.1, 508.1f, 302.6: tapping creatures stages them to the active target, again unstages;
    // one that can't attack says why; Commit declares through Automatum, which taps the attackers
    @Test fun attackersAreStagedByTapsThenCommitted() = stagingFlow("combat-staging")

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun attackersAreStagedByTapsThenCommittedLandscape() { landscape = true; stagingFlow("combat-staging-landscape") } // CMB-028

    // CMB-028, TRN-020: with Automatum on, Main 1 offers Attack, which goes on to Declare attackers through Automatum
    @Test fun mainPhaseAttackGoesToDeclareAttackers() {
        start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(brute), bobField = listOf(ogre)))
        val alice = toolkit(f.a)
        val attack = QuickActions.contextual(play, toolkitQuickContext(play, TableSelection(), f.a), alice.table)
            .single { it.name == "Attack" }
        assertTrue(alice.perform(attack.intent), alice.error)
        assertEquals(TurnPosition.DECLARE_ATTACKERS, TurnPosition.of(play.board.turn))
        assertEquals(TableMode.NORMAL, alice.selectionState.value.mode, "taps stage; no selection mode")
    }

    // CMB-028: a tap on another target's header makes its lane the active one; taps then stage there
    @Test fun aTargetsHeaderMakesItsLaneActive() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute), bobField = listOf(walker)))
        val alice = toolkit(f.a)
        val target = card(play, walker)
        alice.combat.chooseTarget(play, target)
        assertEquals(target, alice.combat.activeTarget(play))
        assertEquals("Rest → Oath Seer", quick(alice).first().name)
        tapCard(alice, card(play, brute))
        assertEquals(listOf<String?>(target), play.board.combat!!.pairs.map { it.attackTarget })
    }

    private fun blockingFlow(shot: String) {
        attackers(warden, ogre)
        val (bruteCard, bearCard) = listOf(card(play, brute), card(play, bear))
        attackWith(bruteCard, bearCard)
        val bob = toolkit(f.b)
        val (wardenCard, ogreCard) = listOf(card(play, warden), card(play, ogre))
        assertEquals(listOf("No blocks", "Clear", "Commit blocks"), quick(bob).map { it.name })
        assertFalse(quick(bob).last().enabled, "nothing blocks yet")
        tapCard(bob, wardenCard)
        assertEquals(wardenCard, bob.combat.selected)
        tapCopy(bob, bruteCard)
        assertEquals(listOf(wardenCard to bruteCard), blocks())
        assertEquals(wardenCard, bob.combat.selected, "it can block one more: still selected")
        render(bob)
        compose.onNodeWithContentDescription("Commit blocks").assertExists()
        captureShot("$SCREENSHOTS/automatum/$shot.png",
            "Bob's view, mirrored, in Declare blockers: Gate Warden (can block an additional creature) blocks Ironhide Brute by an arrow and stays selected in violet, the Brute it fights framed in amber and the Stout Bear it may still block glowing violet; the attackers show their keyword badges.")
        compose.runOnIdle { tapCopy(bob, bearCard) }
        compose.waitForIdle()
        assertEquals(listOf(wardenCard to bruteCard, wardenCard to bearCard), blocks())
        assertNull(bob.combat.selected, "blocking as many as it can deselects it")
    }

    // CMB-034, CMB-031, CMB-032, CR 509.1a: tap a blocker, then the attackers it blocks; a blocker of two stays selected
    // until its second block, each block an arrow; tapping an attacker it blocks again removes that block
    @Test fun blockersAreAssignedByTaps() {
        blockingFlow("combat-blocking")
        val bob = toolkit(f.b)
        val (bruteCard, bearCard) = listOf(card(play, brute), card(play, bear))
        val (wardenCard, ogreCard) = listOf(card(play, warden), card(play, ogre))
        // A normal blocker is deselected once it blocks
        tapCard(bob, ogreCard)
        tapCopy(bob, bruteCard)
        assertNull(bob.combat.selected)
        assertEquals(setOf(wardenCard to bruteCard, wardenCard to bearCard, ogreCard to bruteCard), blocks().toSet())
        // The Warden's block of the Bear goes again
        tapCard(bob, wardenCard)
        tapCopy(bob, bearCard)
        assertEquals(setOf(wardenCard to bruteCard, ogreCard to bruteCard), blocks().toSet())
        // CMB-032: with nothing picked, a tap selects a combatant; the selected one's tap opens its menu
        bob.combat.clearSelection()
        tapCopy(bob, bruteCard)
        assertEquals(bruteCard, bob.combat.selected)
        assertTrue(perform(bob, "Commit blocks"), bob.error)
        assertEquals(setOf(wardenCard to bruteCard, ogreCard to bruteCard), blocks().toSet(), "declared as drafted")
    }

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun blockersAreAssignedByTapsLandscape() { landscape = true; blockingFlow("combat-blocking-landscape") } // CMB-034

    // TRN-061, I-D40, CMB-034: Alice uses Bob's Commit blocks and No blocks for him: each asks first, and nothing is cleared
    // or sent before she confirms; confirmed, No blocks clears his blocks and declares none for him
    @Test fun declaringBlocksForTheDefenderAsksFirst() {
        attackers(ogre)
        val bruteCard = card(play, brute)
        val ogreCard = card(play, ogre)
        attackWith(bruteCard)
        val bob = toolkit(f.b)
        tapCard(bob, ogreCard)
        tapCopy(bob, bruteCard)
        assertEquals(listOf(ogreCard to bruteCard), blocks())
        val alice = toolkit(f.a)
        val before = play.board
        fun use(tool: CombatTool) = alice.perform(TableActionIntent.Combat(TableActionScope(play.gameId, play.id), tool, play.board.combat?.id))
        assertFalse(use(CombatTool.AUTOMATUM_DECLARE))
        assertEquals(ActingFor.Kind.DeclareBlockers(1), alice.flows.acting?.kind)
        assertEquals(f.b, alice.flows.acting?.player)
        alice.flows.cancelActing()
        assertFalse(use(CombatTool.AUTOMATUM_NO_BLOCKS))
        assertEquals(ActingFor.Kind.NoBlocks, alice.flows.acting?.kind)
        assertTrue(before === play.board, "nothing cleared or sent")
        assertTrue(alice.flows.confirmActing(), alice.error)
        assertTrue(blocks().isEmpty())
        assertTrue(TurnPosition.of(play.board.turn) != TurnPosition.DECLARE_BLOCKERS, "declared: the step moved on")
    }

    // CMB-034, CR 702.111b: one blocker isn't enough for menace: Commit blocks is greyed with Automatum's reason
    @Test fun menaceGreysCommitBlocksWithTheReason() {
        attackers(ogre, aliceField = listOf(hound))
        val houndCard = card(play, hound)
        attackWith(houndCard)
        val bob = toolkit(f.b)
        tapCard(bob, card(play, ogre))
        tapCopy(bob, houndCard)
        val commit = quick(bob).single { it.name == "Commit blocks" }
        assertFalse(commit.enabled)
        assertNotNull(commit.refused)
        assertTrue(quick(bob).single { it.name == "No blocks" }.enabled)
        assertTrue(perform(bob, "No blocks"), bob.error)
        assertTrue(blocks().isEmpty(), "No blocks clears the draft and declares none")
    }

    // CMB-029, CMB-033, I-D29, CR 702.19b: after blocks the preview stays on the area with no separate prompt (Pass in the
    // quick actions); the eye hides the numbers; a trampler blocked by one creature divides by itself
    @Test fun previewAfterBlocksAndPass() {
        attackers(ogre, aliceHand = listOf(spark), aliceField = listOf(brute, bear, mountain))
        val (bruteCard, bearCard) = listOf(card(play, brute), card(play, bear))
        attackWith(bruteCard, bearCard)
        val bob = toolkit(f.b)
        val ogreCard = card(play, ogre)
        tapCard(bob, ogreCard)
        tapCopy(bob, bruteCard)
        assertTrue(perform(bob, "Commit blocks"), bob.error)
        val alice = toolkit(f.a)
        assertEquals(listOf("Pass"), quick(alice).map { it.name }, "Alice holds priority: an instant in hand")
        val lifeBefore = life(f.b)
        AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) }
        render(alice)
        compose.onNodeWithContentDescription("Would take 3 damage and die").assertExists()
        compose.onNodeWithContentDescription("Header would go from $lifeBefore to ${lifeBefore - 4}").assertExists()
        captureShot("$SCREENSHOTS/automatum/combat-preview.png",
            "Alice's view after Bob blocked her Ironhide Brute (5/5 trample) with Hill Ogre and left Stout Bear unblocked: the arrow, −3 ☠ on the Ogre, −3 on the Brute, 2 → Bob tags by both attackers and 17 → 13 in Bob's header; Alice holds priority with Pass in the quick actions.")
        compose.onNodeWithContentDescription("Hide damage preview").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Would take 3 damage and die").assertDoesNotExist()
        // Pass, and the next board's shared flow worked out in the same UI-thread turn, as the table's worker would have
        // before showing it: the order-free division is then answered by Alice's device as soon as the table shows it.
        // Left to the table, that flow is worked out on Dispatchers.Default, which waitForIdle doesn't wait for: the
        // answer came only after the asserts when the JVM was busy (a full suite run)
        val pass = compose.onNodeWithContentDescription("Pass").fetchSemanticsNode()
        compose.runOnIdle {
            assertTrue(pass.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!())
            assertNotNull(AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))?.division, "the trampler's division is open")
        }
        compose.waitForIdle()
        assertEquals(lifeBefore - 4, life(f.b), "the trampler's division was made by itself: 3 to the Ogre, 2 to Bob, and 2 from the Bear")
        assertEquals(PlayZone.GRAVEYARD, zone(ogreCard))
    }

    // CMB-033, CR 510.1c, 702.19b: the division dialog opens filled in with the suggestion; In order follows the rows'
    // order; Free takes any division but nothing past the blockers before each has lethal damage
    @Test fun divisionDialogInOrderAndFree() {
        attackers(ogre, warden, aliceField = listOf(brute))
        val bruteCard = card(play, brute)
        attackWith(bruteCard)
        val bob = toolkit(f.b)
        val (ogreCard, wardenCard) = listOf(card(play, ogre), card(play, warden))
        tapCard(bob, ogreCard); tapCopy(bob, bruteCard)
        tapCard(bob, wardenCard); tapCopy(bob, bruteCard)
        bob.combat.clearSelection()
        assertTrue(perform(bob, "Commit blocks"), bob.error)
        assertIs<Decision.Divide>(AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))?.decision)
        AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) }
        val alice = toolkit(f.a)
        render(alice)
        compose.onNodeWithContentDescription("Division of combat damage").assertExists()
        compose.onNodeWithContentDescription("Hill Ogre: 3").assertExists()
        compose.onNodeWithContentDescription("Gate Warden: 2").assertExists()
        compose.onNodeWithContentDescription("Bob (trample): 0").assertExists()
        // The Ogre's row dragged below the Warden's: the Warden gets lethal damage first
        val row = compose.onNodeWithContentDescription("Hill Ogre: 3").fetchSemanticsNode()
        compose.runOnIdle { row.config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first { it.label == "Move down" }.action() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Gate Warden: 3").assertExists()
        captureShot("$SCREENSHOTS/automatum/combat-division.png",
            "Bob blocked Alice's Ironhide Brute (5/5 trample) with Hill Ogre and Gate Warden: in the damage step Alice's division dialog, In order, after dragging the Warden first: 3 to the Warden (lethal ☠), 2 to the Ogre, 0 to Bob.")
        compose.onNodeWithContentDescription("Free").performClick()
        compose.waitForIdle()
        // 1 past the blockers while the Ogre lacks lethal damage: refused
        compose.onAllNodesWithText("−")[1].performClick()
        compose.onAllNodesWithText("+")[2].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("With trample every blocker needs lethal damage before any goes past it.").assertExists()
        compose.onNodeWithText("OK").assertIsNotEnabled()
        captureShot("$SCREENSHOTS/automatum/combat-division-free.png",
            "The same division switched to Free: Alice took 1 from the Ogre and gave it to Bob, so OK is greyed and the line under the rows says every blocker needs lethal damage before any goes past it (trample).")
        compose.onAllNodesWithText("+")[1].performClick()
        compose.onAllNodesWithText("−")[2].performClick()
        compose.waitForIdle()
        val life = life(f.b)
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        assertEquals(life, life(f.b), "nothing past the blockers")
        assertEquals(PlayZone.GRAVEYARD, zone(wardenCard))
        assertEquals(PlayZone.BATTLEFIELD, zone(ogreCard), "2 damage isn't lethal for the Ogre")
    }

    /** Alice attacks with the Brute and the Stone Wall (0/4); Bob's Gate Warden blocks both and his Hill Ogre the Brute.
     * Alice's division of the Brute's damage is open (5 among the Warden and the Ogre, 3 lethal each); Bob's of the
     * Warden's 3 comes after it (the Brute needs 2 more after the Ogre's 3, the Wall 4). */
    private fun wardenBlocksBoth(): Triple<String, String, String> {
        attackers(warden, ogre, aliceField = listOf(brute, wall))
        val (bruteCard, wallCard) = listOf(card(play, brute), card(play, wall))
        attackWith(bruteCard, wallCard)
        val bob = toolkit(f.b)
        val (wardenCard, ogreCard) = listOf(card(play, warden), card(play, ogre))
        tapCard(bob, wardenCard); tapCopy(bob, bruteCard); tapCopy(bob, wallCard)
        tapCard(bob, ogreCard); tapCopy(bob, bruteCard)
        assertTrue(perform(bob, "Commit blocks"), bob.error)
        return Triple(bruteCard, wallCard, wardenCard)
    }

    // STK-034, CMB-033, I-D33, CR 509.1a, 510.1c–d: while Alice divides her trampler's damage ("1 of 2"), Bob gives his
    // Gate Warden's division early in the same rows; it is applied when Alice's is done
    @Test fun blockerOfTwoAndTheDefendersEarlyDivision() {
        val (bruteCard, wallCard, wardenCard) = wardenBlocksBoth()
        val alice = toolkit(f.a)
        val shown = AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) }
        val early = shown.early.single()
        assertEquals(f.b to wardenCard, early.player to early.source)
        val bob = toolkit(f.b)
        render(bob)
        compose.onNodeWithText("Alice is choosing", substring = true).assertExists()
        compose.onNodeWithContentDescription("Early division").assertExists()
        captureShot("$SCREENSHOTS/automatum/combat-early-division.png",
            "Bob's Gate Warden blocks both of Alice's attackers (Ironhide Brute and Stone Wall) and his Hill Ogre the Brute: while Alice divides the Brute's damage, Bob's own division of the Warden's 3 damage waits below her bar in the dialog's rows, In order and filled in with the suggestion, to be given early with Ready.")
        val refs = early.among.associateBy { (it as MtgRef.Card).cardId }
        // All 3 to the Wall, given early
        compose.runOnIdle { assertTrue(bob.combat.giveEarly(early, mapOf(refs.getValue(wallCard) to 3, refs.getValue(bruteCard) to 0)), bob.error) }
        compose.waitForIdle()
        assertEquals(f.b, PreAnswers.of(play.board).single().player)
        compose.runOnIdle {
            val flow = AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))!!
            alice.flows.show(flow)
            alice.combat.show(play, AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) })
            assertEquals(1 to 2, alice.combat.divisionCount(play, flow.division!!), "Alice's comes first of the two")
            assertTrue(alice.flows.answer(arcanum.engine.decision.Answer.Division((flow.decision as Decision.Divide).suggested)), alice.error)
        }
        assertTrue(PreAnswers.of(play.board).isEmpty(), "applied and used up")
        assertEquals(PlayZone.BATTLEFIELD, zone(bruteCard), "the Warden's 3 went to the Wall: the Brute got only the Ogre's 3")
        assertEquals(PlayZone.GRAVEYARD, zone(wardenCard), "Alice's suggestion: lethal 3 to the Warden first")
    }
    // CMB-031, CMB-022: with Automatum off, a defender's card dropped on an attacker blocks it; dropped on a second
    // attacker it gets one more arrow, and again there the arrow goes; at Combat damage only Close + next is offered
    // (I-D39: the damage is dealt by hand)
    @Test fun manualBlocksByArrows() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute, bear), bobField = listOf(ogre)).let {
            it.copy(board = it.board.copy(automatum = false, priority = null)) })
        val alice = toolkit(f.a)
        val (bruteCard, bearCard, ogreCard) = listOf(card(play, brute), card(play, bear), card(play, ogre))
        assertTrue(alice.declare(listOf(bruteCard, bearCard), tap = true), alice.error)
        val bob = toolkit(f.b)
        fun drop(card: String, attacker: String) = bob.handle(TableIntent.Move(TableScope(card, listOf(card)),
            TableDestination.Combat(CombatDestination(play.board.combat!!.id, CombatRole.ATTACKER, pairId = pairOf(attacker).id)), drag = true))
        assertTrue(drop(ogreCard, bruteCard), bob.error)
        assertTrue(drop(ogreCard, bearCard), bob.error)
        assertEquals(listOf(ogreCard to bruteCard, ogreCard to bearCard), blocks())
        assertEquals(1, CombatStackRules.occurrences(play.board.combat).count { it.sourceId == ogreCard }, "one copy, two arrows")
        render(bob)
        captureShot("$SCREENSHOTS/automatum/combat-manual.png",
            "Automatum off, Bob's mirrored view: he dropped Hill Ogre on Ironhide Brute and then on Stout Bear, so one blocker copy sits under the Brute with an arrow to each attacker; no glows, numbers or badges.")
        compose.runOnIdle { assertTrue(drop(ogreCard, bearCard)) }
        compose.waitForIdle()
        assertEquals(listOf(ogreCard to bruteCard), blocks(), "dropped there again: that arrow goes")
        compose.runOnIdle { assertTrue(drop(ogreCard, bearCard)) }
        compose.waitForIdle()
        assertTrue(play.board.combat!!.pairs.single { p -> p.attackers.any { it.sourceId == bearCard } }.let { pair ->
            CombatStackRules.blockedFromElsewhere(play.board.combat, pair.id) }, "the Bear counts as blocked by the arrow")
        live.value = play.copy(board = play.board.copy(turn = play.board.turn.copy(phase = TurnPosition.COMBAT_DAMAGE.phase,
            step = TurnPosition.COMBAT_DAMAGE.step)))
        assertEquals(listOf("Close + next"), QuickActions.contextual(play, toolkitQuickContext(play, TableSelection(), f.a), alice.table)
            .map { it.name })
    }
    // ---- A Stack entry below the top as a target (STK-023)

    // STK-023, I-D6, CR 115.1: Alice's Quash may counter either spell on the Stack; the one below the top is picked in
    // Manage Stack, opened by tapping the glowing Stack, where the legal entries glow
    @Test fun stackEntryBelowTheTopIsPickedInManageStack() {
        start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(mountain, mountain), bobField = listOf(mountain),
            aliceHand = listOf(spark, quash), bobHand = listOf(spark)))
        val alice = toolkit(f.a)
        val bob = toolkit(f.b)
        fun tap(toolkit: TableToolkitController, item: TableItem) = toolkit.open(TableIntent.Actions(item.id, null), item, TableSelection())
        fun scene(viewer: String) = TablePresentation.scene(f.game, play, viewer, 412f, 892f, false)
        assertTrue(alice.cast.start(card(play, spark, aHand)))
        tap(alice, scene(f.a).items.first { it.id == "life/${f.b}" })
        // I-D47: Autotap (on) sends the cast once its last choice is made; only a cast still open waits for Cast
        if (alice.cast.open) assertTrue(alice.cast.cast(), alice.error)
        assertTrue(bob.cast.start(card(play, spark, bHand)), bob.error)
        tap(bob, scene(f.b).items.first { it.id == "life/${f.a}" })
        // I-D47: Autotap (on) sends the cast once its last choice is made; only a cast still open waits for Cast
        if (bob.cast.open) assertTrue(bob.cast.cast(), bob.error)
        val stack = play.board.containers.single { it.zone == PlayZone.STACK }
        assertEquals(2, stack.cards.size)
        val below = stack.cards[1]
        assertTrue(alice.cast.start(card(play, quash, aHand)), alice.error)
        assertTrue(alice.cast.targeting)
        assertTrue(stack.id in alice.cast.glow(play), "the Stack glows: an entry below the top is a legal target")
        tap(alice, scene(f.a).items.first { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE })
        assertTrue(alice.stackTargeting)
        assertEquals(stack.cards, alice.stackTargets(play))
        render(alice)
        compose.onNodeWithText("Choose a target · 2").assertExists()
        captureShot("$SCREENSHOTS/automatum/stack-target-picker.png",
            "Alice casts Quash with two Storm Sparks on the Stack: tapping the glowing Stack opened Manage Stack as a target picker, both spells outlined as legal targets and the editing buttons left out.")
        compose.runOnIdle { alice.pickStackTarget(below) }
        compose.waitForIdle()
        assertFalse(alice.cast.targeting, "the target is chosen; the cast goes on to its payment")
        assertNull(alice.panel)
    }
}
