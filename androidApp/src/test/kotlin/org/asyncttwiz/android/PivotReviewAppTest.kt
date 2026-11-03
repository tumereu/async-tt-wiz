package org.asyncttwiz.android

import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ParsedCard
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adversarial review of the Automatum-always-on pivot (I-D52; report `arcanum-pivot-review.md`, outside the repo) on the
 * app: behaviour whose tests went with the manual table, played again on Automatum boards, and every request through the
 * server's gate ([AutomatumOnly]) as `GameService` applies it before the shared reducer. Each ignored "pivotreview #N"
 * test pins a problem found and fails while it isn't fixed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PivotReviewAppTest {
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, oracle: OracleCard) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle)))
    }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String, pt: String? = null) = Invented(
        DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = text, corner = pt.orEmpty()),
        OracleCard(name, cost, type, text, pt?.substringBefore("/"), pt?.substringAfter("/")))
    private val brute = invented("brute", "Ironhide Brute", "{3}{G}", "Creature — Beast", "Trample", "5/5")
    private val bear = invented("bear", "Stout Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = invented("ogre", "Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain", "")
    private val walker = Invented(DefinitionFace("walker", "Oath Seer", typeLine = "Legendary Planeswalker — Seer", manaCost = "{2}{U}{U}",
        corner = "4"), OracleCard("Oath Seer", "{2}{U}{U}", "Legendary Planeswalker — Seer", loyalty = "4"))
    private val all = listOf(brute, bear, ogre, mountain, walker)

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

    /** Turn 3 of Alice at [position]: the first cards of each Battlefield become [aliceField] and [bobField] (there since
     * turn 1); every other Battlefield and hand card is out of the game; a planeswalker has its loyalty counters. */
    private fun board(position: TurnPosition, priority: MtgPriority?, aliceField: List<Invented>, bobField: List<Invented>): PlayState {
        val initial = f.initial
        fun first(container: String, n: Int) = initial.board.containers.single { it.id == container }.cards.take(n)
        val roles = (first(aField, aliceField.size).zip(aliceField) + first(bField, bobField.size).zip(bobField)).toMap()
        assertEquals(aliceField.size + bobField.size, roles.size, "the fixture has room for the cards")
        val dropped = initial.board.cards.filter { it.containerId in setOf(aField, bField, aHand, bHand) && it.id !in roles }.map { it.id }.toSet()
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            board = initial.board.copy(priority = priority,
                turn = TurnState(f.a, "3", position.phase, position.step),
                cards = initial.board.cards.filter { it.id !in dropped }.map { card -> roles[card.id]?.let { what ->
                    card.copy(definition = what.submitted.digest, faceId = what.face.faceId, corner = what.face.corner, tapped = false,
                        enteredTurn = "1", typedCounters = if (what === walker) listOf(TypedCounter("loyalty", 4, 0)) else emptyList())
                } ?: card },
                containers = initial.board.containers.map { it.copy(cards = it.cards.filter { id -> id !in dropped }) }.map {
                    if (it.zone == PlayZone.LIBRARY && it.cards.size >= 2) it.copy(layers = LibraryLayers.initial(f.id("layer/${it.id}"), it.cards)) else it }))
    }
    private fun card(play: PlayState, what: Invented) = play.board.cards.first { it.definition == what.submitted.digest }.id

    // ---- The server: the gate, then the shared reducer, as GameService

    private var tree: GameplayTree? = null
    private var requests = 0
    private val play: PlayState get() = tree!!.stateAt(tree!!.currentNodeId)!!
    /** Requests the server refused, with the reason. */
    private val refused = mutableListOf<String>()

    private fun start(initial: PlayState) { tree = GameplayTree(initial) }
    private fun serve(actor: String, action: GameAction, request: String = f.id("pivot/request/${requests++}"), from: String? = null): Boolean {
        val history = tree!!
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), action, f.id("client/$actor"), "1",
            continuation = from?.let { Continuation(it, explicit = true) })
        val change = AutomatumOnly.change(history, actor, command)
        if (change.reason != null) { refused += "${change.reason}: ${action.toString().take(200)}"; return false }
        tree = change.tree
        return true
    }
    /** [viewer]'s device on the live game, or holding the point [held] (it acts on that board, continuing from it). */
    private fun toolkit(viewer: String, held: String? = null) = TableToolkitController(viewer,
        { if (held != null) tree!!.stateAt(held) else play }, { true },
        { action, _ -> serve(viewer, GameAction.Play(action), from = held) },
        playBatch = { actions, request -> serve(viewer, GameAction.PlayBatch(actions), request, from = held) },
        playTree = { tree }, automatedBatch = { batch, request -> serve(viewer, batch, request, from = held) },
        continuation = { held?.let { Continuation(it, explicit = true) } })

    private fun tags(at: PlayState = play) = engineTags(tree, at.currentNodeId)
    private fun scene(viewer: String, at: PlayState = play) = TablePresentation.scene(f.game, at, viewer, 412f, 892f, false)
    private fun tapCard(toolkit: TableToolkitController, card: String) = scene(toolkit.viewer).items
        .first { it.cardId == card && it.viewport != "combat" && it.zone == PlayZone.BATTLEFIELD }
        .let { toolkit.open(TableIntent.Actions(it.id, null), it) }
    private fun arrow(toolkit: TableToolkitController, at: PlayState = play, label: String? = null) =
        tapArrow(at, toolkit, AutomatumBoards.combat(at) { tags(at) }, label)

    /** Presses [card]'s combat copy and drags it to [to], exactly like a finger. */
    private fun dragCopy(toolkit: TableToolkitController, card: String, to: (TableScene) -> TablePoint): TableDestination? {
        val scene = scene(toolkit.viewer)
        val gestures = TableGestureCoordinator({ intent -> toolkit.handle(intent) })
        gestures.update(scene, true)
        val origin = scene.items.single { it.viewport == "combat" && it.sourceId == card }
        val target = to(scene)
        gestures.down(origin.bounds.center, 0)
        gestures.move(TablePoint((origin.bounds.center.x + target.x) / 2, (origin.bounds.center.y + target.y) / 2), 40)
        gestures.move(target, 80)
        val destination = gestures.preview.destination
        gestures.up(target, 100)
        return destination
    }

    /** The empty part of [lane]'s attacker row: right of every card drawn in that lane (its centre when empty). */
    private fun attackerRow(scene: TableScene, lane: String?): TablePoint {
        val label = scene.combatLabels.single { it.cardId == lane }
        val inLane = scene.items.filter { it.viewport == "combat" && it.bounds.center.x in label.bounds.x..label.bounds.right }
        val x = inLane.maxOfOrNull { it.bounds.right }?.let { (it + label.bounds.right - label.inset) / 2 } ?: label.bounds.center.x
        val row = scene.combatDrops.first { it.destination.role == CombatRole.ATTACKER && it.bounds.x <= x && x < it.bounds.right }
        return TablePoint(x, row.bounds.center.y)
    }
    private fun pairOf(card: String) = play.board.combat!!.pairs.single { pair -> pair.attackers.any { it.sourceId == card } }

    // CMB-015, CMB-028 (I-D52: the lost manual retarget drags, on Automatum's staging): Alice stages her Ironhide Brute and
    // Stout Bear at Bob; dragging the Bear's copy into Bob's Oath Seer's empty lane makes it attack the planeswalker, its
    // pair moving there; the gate takes every request, and Attack declares what the lanes hold
    @Test fun aStagedAttackerDraggedIntoAnotherLaneAttacksThatTarget() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute, bear), bobField = listOf(walker, ogre)))
        val alice = toolkit(f.a)
        val (bruteCard, bearCard, seer) = Triple(card(play, brute), card(play, bear), card(play, walker))
        tapCard(alice, bruteCard); tapCard(alice, bearCard)
        assertNull(pairOf(bearCard).attackTarget, "staged at Bob")
        val destination = dragCopy(alice, bearCard) { attackerRow(it, seer) }
        assertIs<TableDestination.Combat>(destination)
        assertEquals(seer, pairOf(bearCard).attackTarget, "the Bear attacks the planeswalker")
        assertNull(pairOf(bruteCard).attackTarget, "the Brute still attacks Bob")
        assertTrue(arrow(alice, label = "Attack (2)"), alice.error)
        assertEquals(seer, pairOf(bearCard).attackTarget, "declared as staged")
        assertEquals(emptyList(), refused)
    }

    // CMB-027, CMB-015, CMB-028: with the Bear staged at the planeswalker, the Brute's copy dropped mostly over the Bear's
    // copy (right of its centre) goes beside it in its pair and attacks its target
    @Test fun aStagedAttackerDroppedOverHalfAnotherJoinsItsPairAndTarget() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute, bear), bobField = listOf(walker, ogre)))
        val alice = toolkit(f.a)
        val (bruteCard, bearCard, seer) = Triple(card(play, brute), card(play, bear), card(play, walker))
        tapCard(alice, bruteCard); tapCard(alice, bearCard)
        dragCopy(alice, bearCard) { attackerRow(it, seer) }
        assertEquals(seer, pairOf(bearCard).attackTarget)
        val destination = dragCopy(alice, bruteCard) { scene ->
            val target = scene.items.single { it.viewport == "combat" && it.sourceId == bearCard }
            TablePoint(target.bounds.center.x + scene.cardWidth * .3f, target.bounds.center.y)
        }
        assertIs<TableDestination.Combat>(destination)
        assertEquals(pairOf(bearCard).id, pairOf(bruteCard).id, "the Brute joined the Bear's pair")
        assertEquals(listOf(bearCard, bruteCard), pairOf(bearCard).attackers.map { it.sourceId })
        assertEquals(seer, pairOf(bruteCard).attackTarget)
        assertTrue(arrow(alice, label = "Attack (2)"), alice.error)
        assertEquals(emptyList(), refused)
    }

    // TRN-050, HIS-037 (the lost ResultLockContinuationScenarioTest, on Automatum's steps): Bob suggests a draw; Alice,
    // reviewing the point before it, continues from there with an Automatum step: the server takes it, the new branch has
    // no suggestion (the suggestion stays on the old branch), and both keep playing by the rules on the new branch
    @Test fun continuingFromBeforeASuggestionPlaysOnByTheRules() {
        start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(bear, mountain), bobField = listOf(ogre, mountain)))
        assertTrue(arrow(toolkit(f.a)), "Alice goes to combat")
        val before = play.currentNodeId
        val beforeTurn = play.board.turn
        assertTrue(serve(f.b, GameAction.Play(PlayAction.SuggestResult(SuggestedResult.DRAW))), "$refused")
        val suggested = play.currentNodeId
        assertNotNull(play.board.resultSuggestion)
        // Alice continues from the point before: whoever Automatum waits for there acts, from that point
        val there = tree!!.stateAt(before)!!
        val actor = AutomatumBoards.sharedFlow(there, tags(there))?.let { it.holder ?: it.declaring } ?: f.a
        assertTrue(arrow(toolkit(actor, held = before), at = there), "the step from the earlier point: $refused")
        assertNotEquals(suggested, play.currentNodeId)
        assertNull(play.board.resultSuggestion, "the new branch has no suggestion")
        assertNotNull(tree!!.stateAt(suggested)?.board?.resultSuggestion, "it stays on the old branch")
        assertFalse(generateSequence(tree!!.nodes[play.currentNodeId]) { tree!!.nodes[it.parentId] }.any { it.id == suggested })
        assertNotEquals(beforeTurn to before, play.board.turn to play.currentNodeId)
        // Both keep playing on the new branch, by the rules
        repeat(4) {
            val flow = assertNotNull(AutomatumBoards.sharedFlow(play, tags()))
            val next = flow.holder ?: flow.declaring ?: return@repeat
            assertTrue(arrow(toolkit(next)), "${toolkit(next).error} $refused")
        }
        assertEquals(emptyList(), refused)
    }

    // ---- The opening through the gate

    private fun openingGame(): PlayState {
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (player in listOf(f.a, f.b)) {
            val ids = List(16) { f.id("pivot/$player/card/$it") }
            val drawn = ids.take(7)
            val rest = ids - drawn.toSet()
            ids.forEach { cards += PlayCard(it, mountain.submitted.digest, player, f.id("pivot/$player/${if (it in drawn) "HAND" else "LIBRARY"}"),
                mountain.face.faceId) }
            for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) containers += PlayContainer(f.id("pivot/$player/$zone"), zone, player,
                when (zone) { PlayZone.LIBRARY -> rest; PlayZone.HAND -> drawn; else -> emptyList() },
                layers = if (zone == PlayZone.LIBRARY) LibraryLayers.initial(f.id("pivot/$player/layer"), rest) else emptyList())
        }
        containers += PlayContainer(f.id("pivot/STACK"), PlayZone.STACK)
        val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
        return PlayState(f.id("game"), f.id("play"), f.id("round"), f.id("start"), PlayRoot(f.id("pivot/root"), f.id("pivot/branch")),
            f.id("pivot/root"), BoardState(listOf(f.a, f.b).map { PlayPlayer(it, status = PlayerStatus.OPENING) }, cards.sortedBy { it.id },
                containers, TurnState(f.a)),
            listOf(mountain.submitted), listOf(f.a, f.b).map { p -> InitialShuffle(p, seed, cards.filter { it.owner == p }.map { it.id }) },
            InitialStarter(StarterChoice(f.a), f.a))
    }
    private fun hand(player: String) = play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == player }.cards
    private fun quick(toolkit: TableToolkitController, name: String) =
        QuickActions.contextual(play, QuickContext(viewer = toolkit.viewer)).single { it.name == name }
    private fun opening(toolkit: TableToolkitController, name: String) = quick(toolkit, name).let {
        assertTrue(it.enabled, "$name: ${it.refused}"); assertTrue(toolkit.table.perform(it.intent), toolkit.error) }

    // TRN-028, TRN-023, TRN-062 (I-D49, I-D52: the opening is the one hand-made board change the gate takes): both
    // players mulligan and keep at the same time, every request through the gate; a mulligan is one Undo; the starting
    // player's step arrow waits while either player is still Mulliganing, then the first turn plays by the rules
    @Test fun theOpeningGoesThroughTheGate() {
        start(openingGame())
        val alice = toolkit(f.a)
        val bob = toolkit(f.b)
        val seven = hand(f.a)
        opening(alice, QuickActions.MULLIGAN)
        assertNotEquals(seven, hand(f.a))
        // TRN-023: the mulligan (hand back, shuffle, seven drawn, its count) is one Undo
        assertTrue(serve(f.a, GameAction.Undo), "$refused")
        assertEquals(seven, hand(f.a)); assertEquals(0, play.board.players.single { it.accountId == f.a }.mulligans)
        opening(alice, QuickActions.MULLIGAN); opening(bob, QuickActions.MULLIGAN); opening(alice, QuickActions.MULLIGAN)
        opening(alice, QuickActions.KEEP)
        assertTrue(alice.keeping)
        // TRN-028: Alice's turn-1 controls wait while either player is still Mulliganing
        assertEquals(SharedFlowController.OPENING, stepBlocked(play, false, null))
        assertFalse(alice.table.perform(TableActionIntent.Next(TableActionScope(play.gameId, play.id))))
        assertEquals(SharedFlowController.OPENING, alice.error)
        assertTrue(alice.confirmKeep(listOf(hand(f.a).first())), alice.error)
        assertEquals(6, hand(f.a).size)
        assertFalse(alice.table.perform(TableActionIntent.Next(TableActionScope(play.gameId, play.id))), "Bob still mulligans")
        opening(bob, QuickActions.KEEP)
        assertFalse(bob.keeping, "his first mulligan was free")
        assertFalse(PlayerStatus.anyOpening(play.board))
        assertTrue(arrow(alice), alice.error)
        assertEquals(TurnPosition.PRECOMBAT_MAIN, TurnPosition.of(play.board.turn))
        assertEquals(emptyList(), refused)
    }

    // ---- Behaviour whose every test went with the manual table (pivot review #11), on Automatum boards

    /** [toolkit]'s gestures on [play]'s scene, each intent handled by the toolkit; returns the intents emitted. */
    private fun gestures(toolkit: TableToolkitController): Pair<TableGestureCoordinator, MutableList<TableIntent>> {
        val emitted = mutableListOf<TableIntent>()
        val gestures = TableGestureCoordinator({ intent -> emitted += intent; toolkit.handle(intent) })
        gestures.update(scene(toolkit.viewer), true)
        return gestures to emitted
    }
    private fun doubleTap(gestures: TableGestureCoordinator, point: TablePoint) {
        gestures.down(point, 1_000); gestures.up(point, 1_040); gestures.down(point, 1_120); gestures.up(point, 1_160)
        gestures.tick(2_000)
    }
    private fun pile(viewer: String, zone: PlayZone, owner: String = viewer) =
        scene(viewer).items.first { it.zone == zone && it.owner == owner && it.kind == TableItemKind.PILE }

    /** Alice's first hand card made [what], in her hand (the fixture's own hand card). */
    private fun withHandCard(state: PlayState, what: Invented): Pair<PlayState, String> {
        val original = f.initial.board.cards.first { it.containerId == aHand }
        val card = original.copy(definition = what.submitted.digest, faceId = what.face.faceId, corner = what.face.corner, tapped = false)
        return state.copy(board = state.board.copy(cards = state.board.cards.filter { it.id != card.id } + card,
            containers = state.board.containers.map { if (it.id == aHand) it.copy(cards = listOf(card.id)) else it.copy(cards = it.cards - card.id) })) to card.id
    }

    // ZON-013, TBL-046 (I-D52): a library whose top card Automatum offers nothing for shakes on a tap, opens no menu, and a
    // double-tap draws nothing (no request at all)
    @Test fun aLibraryTapShakesAndADoubleTapDrawsNothing() {
        start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(mountain), bobField = listOf(ogre)))
        val alice = toolkit(f.a)
        val before = play
        val library = pile(f.a, PlayZone.LIBRARY)
        // Automatum has worked the board out (what it offers for the top card: nothing)
        AutomatumBoards.compute(play, f.a); AutomatumBoards.cardPlays(play, f.a) { tags() }
        kotlinx.coroutines.runBlocking(AutomatumBoards.boardWorker) {}
        alice.open(TableIntent.Actions(library.id, null), library)
        assertEquals(library.id, alice.shaking?.id, "the library shakes")
        assertNull(alice.panel, "no menu")
        val (gestures, emitted) = gestures(alice)
        doubleTap(gestures, library.bounds.center)
        assertTrue(emitted.none { it is TableIntent.Move || it is TableIntent.Submit }, "nothing drawn: $emitted")
        assertEquals(before, play, "no request reached the server")
    }

    // TBL-045, ZON-004, ZON-006 (I-D52): double-tapping a hand card plays it through Automatum (a land onto the
    // battlefield, untapped); a hand card is never turned
    @Test fun aHandDoubleTapPlaysThroughAutomatumAndNeverTaps() {
        val (state, land) = withHandCard(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(bear), bobField = listOf(ogre)), mountain)
        start(state)
        val alice = toolkit(f.a)
        val item = scene(f.a).items.first { it.cardId == land && it.zone == PlayZone.HAND }
        val (gestures, emitted) = gestures(alice)
        doubleTap(gestures, item.bounds.center)
        assertTrue(emitted.none { it is TableIntent.Rotate }, "a hand card is never turned: $emitted")
        assertTrue(emitted.any { it is TableIntent.Move && it.handDoubleTap }, "$emitted")
        kotlinx.coroutines.runBlocking(AutomatumBoards.boardWorker) {}
        val played = play.board.cards.single { it.id == land }
        assertEquals(aField, played.containerId, "played onto the battlefield: ${alice.error} $refused")
        assertFalse(played.tapped)
        assertTrue(tags().last() != null, "through Automatum")
        assertEquals(emptyList(), refused)
    }

    // ZON-033, ZON-035, SYS-040, TBL-070 (I-D52): a tap on either player's graveyard opens its menu, which only looks
    // (its one cell is Search, greyed while the pile is empty: shown, never hidden, in the screen tests); a double-tap
    // opens its search view, also on the other player's pile
    @Test fun aPileMenuOnlyLooksAndADoubleTapSearches() {
        start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(bear), bobField = listOf(ogre)))
        val alice = toolkit(f.a)
        for (owner in listOf(f.a, f.b)) {
            val graveyard = pile(f.a, PlayZone.GRAVEYARD, owner)
            alice.open(TableIntent.Actions(graveyard.id, null), graveyard)
            assertEquals("pile/${graveyard.id}", alice.panel)
            alice.dismiss()
            val (gestures, emitted) = gestures(alice)
            doubleTap(gestures, graveyard.bounds.center)
            assertTrue(emitted.any { it == TableIntent.SearchPile(graveyard.id) }, "$emitted")
        }
        assertEquals(emptyList(), refused)
    }

    // CMB-001 (I-D52): the combat area appears when an attacker is staged for Automatum's declaration, and nobody closes
    // it by hand (the server refuses it, SYS-073); Automatum closes it when combat ends
    @Test fun theCombatAreaOpensForStagingAndOnlyAutomatumClosesIt() {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(bear), bobField = listOf(ogre)))
        val alice = toolkit(f.a)
        assertNull(play.board.combat)
        tapCard(alice, card(play, bear))
        val area = assertNotNull(play.board.combat, "staging opened the area").id
        assertFalse(serve(f.a, GameAction.Play(PlayAction.Combat(CombatAction.Close(area)))), "closing it by hand is refused")
        assertEquals(RuleRefusals.AUTOMATUM_ONLY, refused.single().substringBefore(":"))
        refused.clear()
        assertTrue(arrow(alice, label = "Attack (1)"), alice.error)
        var guard = 0
        while (play.board.combat != null && guard++ < 6) {
            val actor = AutomatumBoards.sharedFlow(play, tags())?.let { it.holder ?: it.declaring } ?: break
            assertTrue(arrow(toolkit(actor)), "${toolkit(actor).error} $refused")
        }
        assertNull(play.board.combat, "Automatum closed it when combat ended")
        assertEquals(emptyList(), refused)
    }

    // TBL-018: real cards are drawn as cards, tokens with their own (cut-corner) outline, and Stack and combat copies dashed
    @Test fun cardsTokensAndCopiesLookDifferent() {
        val state = board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(bear, brute), bobField = listOf(ogre))
        val token = card(state, brute)
        start(state.copy(board = state.board.copy(cards = state.board.cards.map { if (it.id == token) it.copy(token = true) else it })))
        val alice = toolkit(f.a)
        tapCard(alice, card(play, bear))
        val items = scene(f.a).items.filter { it.kind == TableItemKind.CARD }
        val real = items.single { it.cardId == card(play, bear) && it.viewport != "combat" }
        assertFalse(real.token); assertFalse(real.projection)
        assertTrue(items.single { it.cardId == token && it.viewport != "combat" }.token, "a token has the token outline")
        assertTrue(items.single { it.viewport == "combat" && it.sourceId == card(play, bear) }.projection, "a combat copy is dashed")
    }

    // TBL-097: a card's owner is whose deck it came from, its controller the battlefield it is on
    @Test fun aCardKeepsItsOwnerOnTheOtherPlayersBattlefield() {
        val state = board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(bear), bobField = listOf(ogre))
        val taken = card(state, ogre)
        start(state.copy(board = state.board.copy(containers = state.board.containers.map { when (it.id) {
            aField -> it.copy(cards = it.cards + taken); bField -> it.copy(cards = it.cards - taken); else -> it } },
            cards = state.board.cards.map { if (it.id == taken) it.copy(containerId = aField) else it })))
        assertEquals(f.b, play.board.cards.single { it.id == taken }.owner, "owned by Bob")
        val item = scene(f.a).items.single { it.cardId == taken && it.kind == TableItemKind.CARD }
        assertEquals(f.b, item.owner, "drawn in Bob's colour")
        assertEquals(scene(f.a).items.single { it.cardId == card(play, bear) && it.kind == TableItemKind.CARD }.viewport, item.viewport,
            "on Alice's battlefield, which she controls")
    }

    // TRN-034: portrait: each player's row holds their life square and their packed piles
    @Test fun eachPlayersRowHoldsTheirLifeAndPiles() {
        start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(bear), bobField = listOf(ogre)))
        val items = scene(f.a).items
        for (player in listOf(f.a, f.b)) {
            val life = items.single { it.kind == TableItemKind.LIFE && it.owner == player }
            val piles = items.filter { it.kind == TableItemKind.PILE && it.owner == player && it.zone in setOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE) }
            assertTrue(piles.isNotEmpty())
            for (pile in piles) assertTrue(pile.bounds.y < life.bounds.bottom && life.bounds.y < pile.bounds.bottom,
                "$player's ${pile.zone} on the life square's row")
        }
    }

    // TBL-017: the camera is personal: panning and zooming send nothing to the game
    @Test fun theCameraIsPersonal() {
        start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(bear), bobField = listOf(ogre)))
        val alice = toolkit(f.a)
        val before = play
        val camera = scene(f.a).camera
        alice.handle(TableIntent.Camera(camera.copy(x = camera.x + 40f, y = camera.y - 30f, zoom = camera.zoom * 1.5f)))
        assertEquals(before, play)
        assertEquals(emptyList(), refused)
    }

    // ---- Client and server agree on what goes without Automatum

    // Pivot review #7 — SYS-069, SYS-073 (I-D52): the device checks a change by hand with the server's own rule
    // ([AutomatumOnly.allowed]); what the server refuses the device refuses itself, without asking the server or showing a
    // prediction first
    @Test fun theDeviceRefusesWhatTheServerRefuses() {
        start(board(TurnPosition.PRECOMBAT_MAIN, MtgPriority(f.a), aliceField = listOf(bear), bobField = listOf(ogre)))
        val alice = toolkit(f.a)
        val bearCard = play.board.cards.single { it.id == card(play, bear) }
        val graveyard = play.board.containers.single { it.zone == PlayZone.GRAVEYARD && it.accountId == f.a }.id
        val samples = listOf(PlayAction.SetStatus("Thinking"), PlayAction.SetLife(f.b, "30"),
            PlayAction.MoveCard(bearCard.id, MoveSource(bearCard.containerId, bearCard.placement), MoveDestination(graveyard, MoveInsertion.UNORDERED)))
        for (action in samples) {
            assertFalse(AutomatumOnly.allowed(play.board, f.a, action), "the server refuses $action")
            assertFalse(alice.submit(action), "the device refuses $action")
        }
        assertEquals(emptyList(), refused, "nothing reached the server")
    }
}
