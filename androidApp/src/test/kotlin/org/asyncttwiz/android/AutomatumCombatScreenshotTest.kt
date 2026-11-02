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
 * Automatum I3b (integration spec I-D29, I-D32 interim, I-D33, I-D36): drags as Automatum actions, combat declared
 * through the engine on today's lanes with the damage preview, divisions pre-filled with the suggestion, a blocker
 * blocking two attackers ("+1"), the defender's division given early, and a Stack entry below the top picked as a target.
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
    private val all = listOf(brute, bear, ogre, warden, spark, quash, mountain)

    private val aHand = f.id("a/HAND")
    private val bHand = f.id("b/HAND")
    private val aField = f.id("a/BATTLEFIELD")
    private val bField = f.id("b/BATTLEFIELD")

    @Before fun definitions() {
        val byDigest = all.associate { it.submitted.digest to it.definition }
        AutomatumBoards.definitionSource = { submitted -> submitted.mapNotNull { s ->
            byDigest[s.digest]?.let { s.digest to ParsedCard.Read(it, 0) } }.toMap() }
    }
    @After fun forget() { AutomatumBoards.reset(); AutomatumBoards.definitionSource = null }

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

    private fun render(toolkit: TableToolkitController) {
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

    // ---- Combat through the engine (I3 interim lanes)

    /** Alice's declare attackers step: Ironhide Brute and Stout Bear against Bob's [blocker]. */
    private fun attackers(blocker: Invented = ogre) {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute, bear), bobField = listOf(blocker)))
    }

    // CMB-017, CMB-028, CR 508.1, 508.1f: Declare puts the creatures into the lanes as a draft; Attack declares them
    // through Automatum, which taps them and runs on to Bob's declaration of blockers
    @Test fun attackersAreDraftedThenDeclared() {
        attackers()
        val alice = toolkit(f.a)
        val (bruteCard, bearCard) = listOf(card(play, brute), card(play, bear))
        assertTrue(alice.declare(listOf(bruteCard, bearCard), tap = false), alice.error)
        assertFalse(play.board.cards.single { it.id == bruteCard }.tapped, "a draft taps nothing")
        assertEquals(f.a, AutomatumBoards.combat(play) { emptyList() }.choices.declaring, "still a draft")
        val quick = automatumCombatQuickActions(play, toolkitQuickContext(play, TableSelection(), f.a)
            .copy(automatumCombat = AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) }), TableActionScope(play.gameId, play.id))
        assertEquals(listOf("Select more attackers", "Attack"), quick?.map { it.name })
        assertTrue(alice.perform(TableActionIntent.Combat(TableActionScope(play.gameId, play.id), CombatTool.AUTOMATUM_DECLARE, play.board.combat?.id)), alice.error)
        assertTrue(play.board.cards.filter { it.id in setOf(bruteCard, bearCard) }.all { it.tapped }, "attacking taps them (CR 508.1f)")
        assertEquals(TurnPosition.DECLARE_BLOCKERS, TurnPosition.of(play.board.turn))
        assertTrue(CombatFlow.throughEngine(play.board))
        // CMB-022: the damage is Automatum's now
        assertFalse(alice.dealCombatDamage(listOf(pairOf(bruteCard).attackers.single().id), areaId = play.board.combat!!.id))
        assertEquals(CombatController.DAMAGE_BY_AUTOMATUM, alice.error)
    }

    /** Attack with the Brute alone; Bob drags his blocker into its pair (the draft of his blocks). */
    private fun brutesAttackBlocked(): Pair<String, String> {
        attackers()
        val alice = toolkit(f.a)
        val bruteCard = card(play, brute)
        assertTrue(alice.declare(listOf(bruteCard), tap = false), alice.error)
        assertTrue(alice.perform(TableActionIntent.Combat(TableActionScope(play.gameId, play.id), CombatTool.AUTOMATUM_DECLARE, play.board.combat?.id)))
        val bob = toolkit(f.b)
        val ogreCard = card(play, ogre)
        val pair = pairOf(bruteCard)
        // An attacker can't be dragged anywhere now: attackers are declared
        assertFalse(bob.handle(TableIntent.Move(TableScope(ogreCard, listOf(ogreCard)),
            TableDestination.Combat(CombatDestination(play.board.combat!!.id, CombatRole.ATTACKER)), drag = true)))
        assertEquals(CombatController.DECLARED_ATTACKERS, bob.error)
        assertTrue(bob.handle(TableIntent.Move(TableScope(ogreCard, listOf(ogreCard)),
            TableDestination.Combat(CombatDestination(play.board.combat!!.id, CombatRole.BLOCKER, pairId = pair.id)), drag = true)), bob.error)
        return bruteCard to ogreCard
    }

    // CMB-020, CMB-029, I-D29, CR 509.1, 510.1c, 702.19b: Bob's drafted block shows the damage it would lead to on the lanes
    // and life squares; Block declares it
    @Test fun previewOfTheBlocksBeingArranged() {
        val (bruteCard, ogreCard) = brutesAttackBlocked()
        val shown = AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) }
        assertEquals(f.b, shown.choices.declaring, "a draft: blockers are still awaited")
        assertEquals(mapOf(ogreCard to 3, bruteCard to 3, f.b to 2), assertNotNull(shown.preview).damage)
        assertEquals(listOf(ogreCard), shown.preview!!.dies)
        val bob = toolkit(f.b)
        render(bob)
        compose.onNodeWithContentDescription("Block").assertExists()
        compose.onNodeWithContentDescription("Would take 3 damage and die").assertExists()
        compose.onNodeWithContentDescription("Life would change by -2").assertExists()
        captureShot("$SCREENSHOTS/automatum/combat-preview.png",
            "Alice attacks with Ironhide Brute (5/5, trample) and Bob dragged Hill Ogre into its pair: before declaring, the lanes show the damage it would lead to, −3 ✝ on the Ogre, −3 on the Brute and −2 by Bob's life square, with Block in the quick actions.")
        compose.onNodeWithContentDescription("Block").performClick()
        compose.waitForIdle()
        assertIs<Decision.Divide>(AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))?.decision,
            "Alice divides the trampler's damage (CR 510.1c)")
    }

    // STK-033, I-D29, CR 510.1c, 702.19b: the attacker's division is asked with the suggestion filled in; OK deals it
    @Test fun divisionPanelStartsFromTheSuggestion() {
        val (bruteCard, ogreCard) = brutesAttackBlocked()
        assertTrue(toolkit(f.b).perform(TableActionIntent.Combat(TableActionScope(play.gameId, play.id), CombatTool.AUTOMATUM_DECLARE, play.board.combat?.id)))
        AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))
        val alice = toolkit(f.a)
        render(alice)
        compose.onNodeWithText("Divide 5").assertExists()
        compose.onNodeWithText("Hill Ogre (lethal 3)").assertExists()
        captureShot("$SCREENSHOTS/automatum/combat-division.png",
            "Bob blocked Alice's Ironhide Brute (5/5, trample) with Hill Ogre: in the damage step Alice divides its 5 damage in the panel by the Stack, filled in with Automatum's suggestion, 3 to the Ogre (lethal 3) and 2 to Bob.")
        val life = life(f.b)
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        assertEquals(life - 2, life(f.b))
        assertEquals(PlayZone.GRAVEYARD, zone(ogreCard))
        assertEquals(PlayZone.BATTLEFIELD, zone(bruteCard))
    }

    /** Alice attacks with the Brute and the Bear; Bob's Gate Warden blocks both. Alice's division of the Brute's damage is
     * open. Returns the Brute, the Bear and the Warden. */
    private fun wardenBlocksBoth(): Triple<String, String, String> {
        attackers(blocker = warden)
        val (bruteCard, bearCard) = listOf(card(play, brute), card(play, bear))
        val wardenCard = card(play, warden)
        val alice = toolkit(f.a)
        assertTrue(alice.declare(listOf(bruteCard, bearCard), tap = false))
        assertTrue(alice.perform(TableActionIntent.Combat(TableActionScope(play.gameId, play.id), CombatTool.AUTOMATUM_DECLARE, play.board.combat?.id)))
        // The lanes can't hold one blocker in two pairs (I-D32 comes later): Bob's block is declared as the engine has it
        engine(f.b) { CombatFlow.declareBlockers(it, listOf(LaneBlock(wardenCard, bruteCard), LaneBlock(wardenCard, bearCard))) }
        return Triple(bruteCard, bearCard, wardenCard)
    }

    // CMB-030, CMB-029, CR 509.1a: the blocker of two sits in the pair of the first with "+1"; with her division panel
    // hidden (the eye) Alice sees it and the preview of her suggested division
    @Test fun blockerOfTwoShowsPlusOne() {
        val (_, _, wardenCard) = wardenBlocksBoth()
        assertEquals(mapOf(wardenCard to 1), AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) }.alsoBlocks)
        val alice = toolkit(f.a)
        val flow = assertNotNull(AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId)))
        alice.flows.show(flow)
        alice.flows.toggleHidden()
        render(alice)
        compose.onNodeWithContentDescription("Also blocks 1 more").assertExists()
        captureShot("$SCREENSHOTS/automatum/combat-blocks-two.png",
            "Bob's Gate Warden blocks both of Alice's attackers: its copy sits in the Ironhide Brute's pair with a violet +1, and with her division panel hidden by the eye Alice sees the preview of the suggested damage on the lanes and by Bob's life square.")
    }

    // STK-034, I-D33, CR 509.1a, 510.1c–d: while Alice divides her trampler's damage, Bob gives his Gate Warden's division
    // early; it is applied when Alice's is done
    @Test fun blockerOfTwoAndTheDefendersEarlyDivision() {
        val (bruteCard, bearCard, wardenCard) = wardenBlocksBoth()
        val alice = toolkit(f.a)
        val shown = AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) }
        assertEquals(mapOf(wardenCard to 1), shown.alsoBlocks)
        val early = shown.early.single()
        assertEquals(f.b to wardenCard, early.player to early.source)
        AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))
        val bob = toolkit(f.b)
        render(bob)
        compose.onNodeWithText("Alice is choosing", substring = true).assertExists()
        compose.onNodeWithContentDescription("Early division").assertExists()
        captureShot("$SCREENSHOTS/automatum/combat-early-division.png",
            "Bob's Gate Warden blocks both of Alice's attackers: while Alice divides her Ironhide Brute's damage, Bob's own division of the Warden's 3 damage waits below her bar, filled in with the suggestion, to be given early with Ready.")
        compose.onNodeWithText("Ready").assertExists()
        // All 3 to the Stout Bear, given early
        val refs = early.among.associateBy { (it as MtgRef.Card).cardId }
        compose.runOnIdle { assertTrue(bob.combat.giveEarly(early, mapOf(refs.getValue(bearCard) to 3, refs.getValue(bruteCard) to 0)), bob.error) }
        compose.waitForIdle()
        assertEquals(f.b, PreAnswers.of(play.board).single().player)
        // Alice takes her suggestion: Bob's division is applied in the same step
        compose.runOnIdle {
            val flow = AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))!!
            alice.flows.show(flow)
            assertTrue(alice.flows.answer(arcanum.engine.decision.Answer.Division((flow.decision as Decision.Divide).suggested)), alice.error)
        }
        assertTrue(PreAnswers.of(play.board).isEmpty(), "applied and used up")
        assertEquals(PlayZone.GRAVEYARD, zone(bearCard), "the Warden's 3 damage went to the Stout Bear")
        assertEquals(PlayZone.BATTLEFIELD, zone(bruteCard))
        assertNull(AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))?.decision, "nothing more to divide")
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
        assertTrue(alice.cast.cast(), alice.error)
        assertTrue(bob.cast.start(card(play, spark, bHand)), bob.error)
        tap(bob, scene(f.b).items.first { it.id == "life/${f.a}" })
        assertTrue(bob.cast.cast(), bob.error)
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
