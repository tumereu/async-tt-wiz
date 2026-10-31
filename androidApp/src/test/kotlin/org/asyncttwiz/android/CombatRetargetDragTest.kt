package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Owner playtest (round 31): dragging an attacker that is already in combat into another target's lane
 * changes what it attacks. Each case runs the user's real path: a drag on a real [TableScene] through
 * [TableGestureCoordinator], the [TableToolkitController] lowering, and the shared rules applying the command. */
class CombatRetargetDragTest {
    private val f = TableStatesFixture
    private fun walker(n: Int) = f.id("walker$n")

    /** [TableStatesFixture.combat] plus [walkers] face-up planeswalkers on the defender's battlefield; pair1 and
     * pair2 attack [targets] (null = the defending player). */
    private fun state(walkers: Int, targets: List<String?>, name: String = "Walker"): PlayState {
        val base = f.combat()
        val definitions = (0 until walkers).map { n ->
            val definition = CardDefinition("$name $n", listOf(DefinitionFace(f.id("walker-face$n"), "$name $n",
                typeLine = "Legendary Planeswalker — Test")))
            SubmittedDefinition(DeckJson.digest(definition), definition)
        }
        val battlefield = f.id("b/BATTLEFIELD")
        val cards = definitions.mapIndexed { n, definition -> PlayCard(walker(n), definition.digest, f.b, battlefield,
            f.id("walker-face$n"), covered = false, placement = PlayPlacement(BoardPosition(40 + n, 0), f.id("walker-pile$n"))) }
        val area = base.board.combat!!
        return base.copy(definitions = base.definitions + definitions, board = base.board.copy(cards = base.board.cards + cards,
            containers = base.board.containers.map { if (it.id == battlefield) it.copy(cards = it.cards + cards.map { c -> c.id }) else it },
            combat = area.copy(pairs = area.pairs.mapIndexed { n, pair -> pair.copy(attackTarget = targets[n]) })))
    }

    private class Session(initial: PlayState, val viewer: String, val landscape: Boolean) {
        var play = initial
        val recorded = mutableListOf<PlayAction>()
        private fun apply(actions: List<PlayAction>, requestId: String): Boolean {
            val base = GameCommand(requestId, GameScope(play.gameId, playId = play.id), GameAction.Play(actions.first()), play.id, "1")
            var next = play
            for ((index, action) in actions.withIndex()) {
                val change = GameplayRules.change(next, viewer, PlayBatches.member(base, index, action))
                assertNull(change.reason, "$action")
                next = requireNotNull(change.state)
            }
            recorded += actions; play = next
            return true
        }
        val ui = TableToolkitController(viewer, { play }, { true }, { action, id -> apply(listOf(action), id ?: f().id("single")) },
            playBatch = { actions, id -> apply(actions, id) })
        private fun f() = TableStatesFixture
        fun scene() = TablePresentation.scene(f().game, play, viewer, if (landscape) 892f else 412f, if (landscape) 412f else 892f, landscape)

        /** Press the attacker's drawing and drag it to [to], exactly like a finger. */
        fun drag(occurrenceId: String, to: (TableScene) -> TablePoint): TableDestination? {
            val scene = scene()
            val gestures = TableGestureCoordinator({ intent -> when (intent) {
                is TableIntent.Selection -> ui.selectionState.value = intent.selection
                else -> { ui.handle(intent) }
            } })
            gestures.update(scene, TableSelection(), true)
            val origin = scene.items.single { it.cardId == occurrenceId }
            val target = to(scene)
            gestures.down(origin.bounds.center, 0)
            gestures.move(TablePoint((origin.bounds.center.x + target.x) / 2, (origin.bounds.center.y + target.y) / 2), 40)
            gestures.move(target, 80)
            val destination = gestures.preview.destination
            gestures.up(target, 100)
            return destination
        }
    }

    /** The empty part of [lane]'s attacker row: right of every card drawn in that lane (its center when empty),
     * at the attacker row's vertical center. */
    private fun attackerRow(scene: TableScene, lane: String?): TablePoint {
        val label = scene.combatLabels.single { it.cardId == lane }
        val inLane = scene.items.filter { it.viewport == "combat" && it.bounds.center.x in label.bounds.x..label.bounds.right }
        val x = inLane.maxOfOrNull { it.bounds.right }?.let { (it + label.bounds.right - label.inset) / 2 } ?: label.bounds.center.x
        val row = scene.combatDrops.first { it.destination.role == CombatRole.ATTACKER && it.bounds.x <= x && x < it.bounds.right }
        assertTrue(inLane.none { it.bounds.contains(TablePoint(x, row.bounds.center.y)) })
        return TablePoint(x, row.bounds.center.y)
    }

    private fun assertRetargets(name: String, initial: PlayState, occurrence: String, lane: String?, expectedPair: String) {
        for (viewer in listOf(f.a, f.b)) for (landscape in listOf(true, false)) {
            val session = Session(initial, viewer, landscape)
            val label = "$name, viewer ${if (viewer == f.a) "attacker" else "defender (mirrored)"}, ${if (landscape) "landscape" else "portrait"}"
            val destination = session.drag(occurrence) { attackerRow(it, lane) }
            assertIs<TableDestination.Combat>(destination, label)
            assertTrue(session.recorded.isNotEmpty(), "$label: nothing recorded for $destination")
            assertTrue(session.recorded.all { it is PlayAction.Combat && it.action is CombatAction.SetAttackTarget }, "$label: ${session.recorded}")
            val pairs = session.play.board.combat!!.pairs
            assertEquals(lane, pairs.single { it.id == expectedPair }.attackTarget, label)
            // The whole pair moved: its attacker and blocker stay together.
            val before = initial.board.combat!!.pairs.single { it.id == expectedPair }
            val after = pairs.single { it.id == expectedPair }
            assertEquals(before.attackers to before.blockers, after.attackers to after.blockers, label)
        }
    }

    @Test fun onlyTheOutermostDropOnEachSideExtendsAndNoGapReachesIntoAnEmptyLane() {
        // Root cause of the owner's report: the first/last pair's end gaps extended past the area's ends and a
        // pair edge next to an empty lane extended to the right, so they swallowed the empty lanes beyond them.
        for (targets in listOf(listOf(null, null), listOf(walker(1), walker(1)), listOf(null, walker(1)), listOf(walker(0), null))) {
            val play = state(2, targets)
            val geometry = CombatStackPresentation.combat(play.board, 28f, 40f, play.definitions, viewer = f.a)
            val left = geometry.destinations.filter { it.extendLeft }; val right = geometry.destinations.filter { it.extendRight }
            assertTrue(left.all { it.bounds.x < .01f } && right.all { it.bounds.right > geometry.width - .01f }, "$targets")
            for (lane in geometry.lanes.filter { lane -> geometry.destinations.any { it.emptyLane && it.destination.attackTarget == lane.cardId } }) {
                val others = geometry.destinations.filter { !it.emptyLane && it.destination.pairId == null }
                assertTrue(others.none { other -> other.bounds.x < lane.bounds.right - .01f && other.bounds.right > lane.bounds.x + .01f ||
                    other.extendLeft && lane.bounds.right <= other.bounds.x || other.extendRight && lane.bounds.x >= other.bounds.right }, "$targets")
            }
        }
    }

    // CMB-015
    @Test fun dragIntoAnEmptyPlaneswalkerLaneAtTheEndRetargets() {
        // Both pairs attack the player; the one planeswalker lane is empty and last.
        assertRetargets("empty lane at the end", state(1, listOf(null, null)), f.id("attacker2"), walker(0), f.id("pair2"))
    }

    // CMB-015
    @Test fun dragIntoAnEmptyLaneInTheMiddleRetargets() {
        // pair1 attacks the player, pair2 the second walker; the first walker's lane is empty and in the middle.
        assertRetargets("empty lane in the middle", state(2, listOf(null, walker(1))), f.id("attacker1"), walker(0), f.id("pair1"))
    }

    // CMB-015
    @Test fun dragIntoTheEmptyFirstPlayerLaneRetargets() {
        // Both pairs attack the walker; the player lane is empty and first.
        assertRetargets("empty player lane first", state(1, listOf(walker(0), walker(0))), f.id("attacker2"), null, f.id("pair2"))
    }

    // CMB-015
    @Test fun dragIntoTheEmptyPartOfAnOccupiedOtherLaneRetargets() {
        // pair1 attacks the player, pair2 the walker: drop pair1's attacker beside pair2's attacker in the walker lane.
        assertRetargets("occupied other lane", state(1, listOf(null, walker(0)), "Ajani, Long-Named Walker"),
            f.id("attacker1"), walker(0), f.id("pair1"))
    }
}
