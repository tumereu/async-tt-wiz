package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.Layout
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.automatum.EngineBatch
import org.asyncttwiz.automatum.EngineCall
import org.asyncttwiz.automatum.LaneAttack
import org.asyncttwiz.automatum.LaneBlock
import org.asyncttwiz.automatum.StepFlow
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

/** Adversarial review of Automatum phases I2b and I3 (report `arcanum-i3-review.md`, outside the repo): each ignored test
 * pins a problem found and fails while it isn't fixed. Invented cards parsed from their oracle text as on a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumI3ReviewTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, oracle: OracleCard, byHand: Boolean = false) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle).let { parsed ->
            // Something to do by hand, as the engine's own manual steps are
            if (!byHand) parsed else parsed.copy(abilities = listOf(arcanum.api.Ability.Spell(
                arcanum.api.Instructions(steps = listOf(arcanum.api.Step.Manual(oracle.text.orEmpty()))), text = oracle.text)))
        }))
    }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String, pt: String? = null, byHand: Boolean = false) =
        Invented(DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = text, corner = pt.orEmpty()),
            OracleCard(name, cost, type, text, pt?.substringBefore("/"), pt?.substringAfter("/")), byHand)
    private val brute = invented("brute", "Ironhide Brute", "{3}{G}", "Creature — Beast", "Trample", "5/5")
    private val basilisk = invented("basilisk", "Hidden Basilisk", "{3}{G}", "Creature — Basilisk", "", "4/4")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain", "")
    private val odd = invented("odd", "Odd Rearrangement", "{R}", "Instant",
        "Rearrange the permanents on the battlefield however you like.", byHand = true)
    private val spark = invented("spark", "Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
    /** A morph creature (CR 702.37): cast face down for {3} as a nameless 2/2 spell. */
    private val stalker = invented("stalker", "Veiled Thicket Stalker", "{3}{G}", "Creature — Elf Scout", "Morph {2}{G}", "3/3")
    private val all = listOf(brute, basilisk, mountain, odd, spark, stalker)

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

    /** Automatum on, turn 3 of Alice at [position] with [priority]; the first cards of each Battlefield and hand become the
     * given cards (creatures there since turn 1, [faceDown] ones face down); every other Battlefield and hand card is out. */
    private fun board(position: TurnPosition, priority: MtgPriority?, aliceField: List<Invented>, bobField: List<Invented>,
        aliceHand: List<Invented> = emptyList(), faceDown: Set<Invented> = emptySet(), bobHand: List<Invented> = emptyList()): PlayState {
        val initial = f.initial
        fun first(container: String, n: Int) = initial.board.containers.single { it.id == container }.cards.take(n)
        val roles = (first(aField, aliceField.size).zip(aliceField) + first(bField, bobField.size).zip(bobField) +
            first(aHand, aliceHand.size).zip(aliceHand) + first(bHand, bobHand.size).zip(bobHand)).toMap()
        val dropped = initial.board.cards.filter { it.containerId in setOf(aField, bField, aHand, bHand) && it.id !in roles }.map { it.id }.toSet()
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            board = initial.board.copy(priority = priority,
                turn = TurnState(f.a, "3", position.phase, position.step),
                cards = initial.board.cards.filter { it.id !in dropped }.map { card -> roles[card.id]?.let { what ->
                    val down = what in faceDown && card.containerId in setOf(aField, bField)
                    card.copy(definition = what.submitted.digest, faceId = what.face.faceId, corner = if (down) "" else what.face.corner,
                        tapped = false, covered = card.containerId == aHand || card.containerId == bHand || down, explicitlyCovered = down,
                        enteredTurn = if (card.containerId in setOf(aField, bField)) "1" else "")
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

    /** Alice casts Odd Rearrangement (something Automatum leaves to be done by hand): its "By hand" choice is open. */
    private fun byHandOpen(): TableToolkitController {
        start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(mountain, brute), bobField = emptyList(),
            aliceHand = listOf(odd)))
        val alice = toolkit(f.a)
        assertTrue(alice.cast.start(card(play, odd, aHand)), alice.error)
        if (alice.cast.open) assertTrue(alice.cast.cast(), alice.error)
        assertTrue(Automatum.flowOpen(play.board))
        // The table's host has worked the shown board's flow out (SharedFlowHost)
        val flow = assertNotNull(AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId)))
        assertIs<Decision.Manual>(flow.decision)
        return alice
    }

    // #4 (I-D27, STK-033): Alice divides her trampler's damage between Bob's face-down blocker and Bob; the division
    // names the face-down creature: the deciding player is shown every object of their own choice ("a search"), and the
    // shared flow's state has full information
    @Test fun aDivisionDoesNotNameAFaceDownBlocker() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute), bobField = listOf(basilisk), faceDown = setOf(basilisk)))
        val attacker = card(play, brute, aField)
        val hidden = card(play, basilisk, bField)
        engine(f.a) { CombatFlow.declareAttackers(it, listOf(LaneAttack(attacker))) }
        engine(f.b) { CombatFlow.declareBlockers(it, listOf(LaneBlock(hidden, attacker))) }
        val alice = toolkit(f.a)
        val flow = assertNotNull(AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId)))
        alice.flows.show(flow)
        val division = assertIs<Decision.Divide>(flow.decision, "Alice divides the trample damage")
        assertEquals(f.a, division.player.value)
        val blocker = division.among.filterIsInstance<TargetRef.Object>().single().id
        val name = objectName(alice.flows, play, blocker)
        assertFalse("Hidden Basilisk" in name, "the face-down blocker is named \"$name\" to Alice")
    }

    // #11 (I-D27, STK-005, STK-027): Alice casts a morph face down; Bob, who may respond, is shown its name and rules text in
    // the respond prompt ("Alice cast Veiled Thicket Stalker"): the prompt names the top entry's card from the game's card
    // data without asking whether the viewer may see it (the Stack entry itself is face down)
    @Test fun theRespondPromptDoesNotNameAFaceDownSpell() {
        start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(mountain, mountain, mountain),
            bobField = listOf(mountain), aliceHand = listOf(stalker), bobHand = listOf(spark)))
        engine(f.a) { call ->
            val faceDown = Engine.options(call.working, PlayerId(f.a)).filterIsInstance<Input.Cast>().first { it.way.alternative != null }
            call.perform(faceDown)
            while (true) {
                val d = call.pending?.decision ?: break
                assertIs<Decision.ManaPayment>(d)
                call.perform(Input.Answer(d.id, Answer.ManaPayment(d.suggestion)))
            }
            // The caster's own automatic pass (STK-026), then the host's
            call.perform(Input.PassPriority(PlayerId(f.a)))
            StepFlow.automatic(call)
        }
        assertTrue(play.board.stackEffects.single().faceDown == true, "the entry is face down (STK-005)")
        assertEquals(f.b, play.board.priority?.holder, "Bob may respond")
        AutomatumBoards.compute(play, f.b)
        AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))
        val bob = toolkit(f.b)
        compose.setContent { MaterialTheme { NoRipples { Box(Modifier.fillMaxSize()) {
            TableSurface(f.game, live.value!!, f.b, false, Modifier.fillMaxSize(), connected = true, onIntent = bob::handle, toolkit = bob,
                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, redo = { true }, controlsConnected = true))
        } } } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Respond prompt").assertExists()
        compose.onAllNodesWithText("Veiled Thicket Stalker", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("Morph", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("a face-down spell", substring = true).assertExists()
        captureShot("$SCREENSHOTS/automatum/respond-face-down.png",
            "Alice cast a morph creature face down and Bob may respond: his prompt reads \"Alice cast a face-down spell\" without its name or text, its card covered, with Respond (the step arrow reads Resolve).")
    }

    // #5 (STK-032, I-D37): while Automatum waits for something done by hand, manual edits work until Done. After the first
    // edit the board is new and its flow isn't worked out yet (SharedFlowHost does it on a worker), so the next edit is
    // refused as if a choice were waiting ("Automatum is waiting for a choice …"): quick taps on a stepper fail
    @Test fun manualEditsInARowWhileSomethingIsDoneByHand() {
        val alice = byHandOpen()
        assertTrue(alice.submit(PlayAction.AdjustLife(f.b, "-1")), alice.error)
        assertTrue(alice.submit(PlayAction.AdjustLife(f.b, "-1")), alice.error)
    }

    // #6 (STK-032 vs ZON-077, I-D37): "the table's manual edits (life, counters, moves) work until Done", but drags, the
    // way cards are moved and life is changed on the table, are still refused as Automatum drags
    @Test fun dragsWorkWhileSomethingIsDoneByHand() {
        val alice = byHandOpen()
        val creature = card(play, brute, aField)
        assertTrue(alice.handle(TableIntent.Move(TableScope(creature, listOf(creature)),
            TableDestination.Container(f.id("a/GRAVEYARD"), PlayZone.GRAVEYARD), drag = true)), alice.error)
        AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))
        assertTrue(alice.handle(TableIntent.PlayerDelta(f.b, null, -2)), alice.error)
    }

    // #12 (SYS-025): drafting attackers doesn't work combat out again on the main thread: the board after a drafted card
    // is checked against what the last shown board allowed (the same declaration is awaited; the engine doesn't read the
    // draft), so the next drag needs no engine work
    @Test fun aDraftedAttackerIsCheckedAgainstTheLastShownCombat() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute, basilisk), bobField = emptyList()))
        val alice = toolkit(f.a)
        val first = card(play, brute, aField)
        val second = card(play, basilisk, aField)
        val shown = AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) }
        alice.combat.show(play, shown)
        assertEquals(f.a, shown.choices.declaring)
        accept(f.a, GameAction.Play(PlayAction.Combat(CombatAction.Open)))
        val area = play.board.combat!!.id
        accept(f.a, GameAction.Play(PlayAction.Combat(CombatAction.Place(first, CombatDestination(area, CombatRole.ATTACKER, pairGap = IdentityGap())))))
        assertNull(AutomatumBoards.cachedCombat(play))
        assertTrue(alice.combat.choices(play) === shown, "the last shown board's choices")
        assertNull(alice.combat.draftRefusal(play, listOf(second), CombatRole.ATTACKER, null, null))
        assertNull(AutomatumBoards.cachedCombat(play), "nothing worked out for the drafted board")
    }
}
