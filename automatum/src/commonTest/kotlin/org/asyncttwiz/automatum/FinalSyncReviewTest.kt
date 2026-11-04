package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.BoardPosition
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.GameplayRules
import org.asyncttwiz.protocol.MoveDestination
import org.asyncttwiz.protocol.MoveInsertion
import org.asyncttwiz.protocol.MoveSource
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Final adversarial review of the switch, history, undo/redo, branches and flow reverts with Automatum ("finalreview-sync",
 * report `arcanum-final-sync-review.md` outside the repo). Each test pins a problem found (all fixed 2026-11-03, the owner
 * questions decided as noted) or a check that holds. Invented cards only.
 */
class FinalSyncReviewTest {
    init {
        CombatCards.ensured
        Cards.invent("Surge of Vigor", "{G}", "Instant", "Target creature gets +3/+3 until end of turn.")
        Cards.invent("Whisper Theft", "{B}", "Sorcery", "Target player discards a card. You may draw a card.")
    }

    private val table = AtwTable("final-sync")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private var manualRequests = 0

    private fun position(play: PlayState) = TurnPosition.of(play.board.turn)
    private fun life(play: PlayState, player: String) = play.board.players.single { it.accountId == player }.life
    private fun zone(play: PlayState, card: String) =
        play.board.containers.single { c -> c.id == play.board.cards.single { it.id == card }.containerId }.zone

    /** Alice's main phase of turn 3; Bob keeps Storm Spark and a Mountain, and both have Full control, so Automatum stops
     * in every step in which a player could act (I-D41). */
    private fun combatGame(): RoundTrip {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.LIBRARY, "Island", "Island", "Island")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.HAND, "Storm Spark")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island", "Island")
        table.fullControl += listOf(alice, bob)
        return RoundTrip(table.state(), table.definitions)
    }

    private fun manual(rt: RoundTrip, actor: String, action: PlayAction) {
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "final-sync/${manualRequests++}"),
            GameScope(rt.state.gameId, playId = rt.state.id), GameAction.Play(action), CommandIds.uuidV5(AtwTable.NAMESPACE, "manual"), "1")
        val change = GameplayRules.change(rt.state, actor, command)
        assertEquals("accepted", change.outcome, "$action: ${change.reason}")
        rt.state = change.state!!
        rt.tags += null
    }

    /** Whoever holds priority as the host takes it passes (the step button). */
    private fun pass(rt: RoundTrip) {
        val s = rt.call().working
        val holder = (HostPriority.wanted(s, HostPriority.kept(rt.state.board))?.holder ?: s.priority.holder)?.value
            ?: fail("nobody holds priority in ${position(rt.state)} and nothing is asked: ${CombatFlow.choices(rt.call()).declaring}")
        rt.run(holder) { StepFlow.pass(it, holder) }
    }

    // finalreview-sync #5 (fixed on master by the pausereview #10 fix, `plannedBefore`) — old-model armed continuation, HIS-042, SYS-057 #4,
    // I-D41: Bob starts a branch from an earlier acting point (a status, as Hold at a pause makes one) and then undoes that
    // event. The live position is now the branch point, which has the old line after it, so the next event there starts a
    // branch again, which reseeds the libraries' unknown order. A batch planned on the live position before the reseed is
    // still taken (before the fix every Automatum batch from there was refused, "inapplicable")
    @Test fun automatumGoesOnAfterABranchsFirstEventIsUndone() {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Island", "Island", "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island", "Island", "Island")
        table.fullControl += listOf(alice, bob)
        val game = TreeGame(table.state(), table.definitions, name = "branch-undo")
        game.run(alice) { StepFlow.pass(it, alice) }
        val point = game.head
        game.run(bob) { StepFlow.pass(it, bob) }
        game.manual(bob, PlayAction.SetStatus("Wait"), from = point).let { assertNotNull(it.tree, "the branch: ${it.reason}") }
        game.control(bob, GameAction.Undo).let { assertNotNull(it.tree, "Bob undoes it: ${it.reason}") }
        assertEquals(point, game.head)
        val call = game.call().also { StepFlow.pass(it, bob) }
        // The server's board differs from the plan only in the reseeded libraries' order
        val sent = game.send(call, bob, check = false)
        assertNotNull(sent.tree, "Bob's pass from the live position: ${sent.reason}")
    }

    private val aliceDeck = List(7) { "Mountain" } + List(4) { "Swamp" } + List(3) { "Plains" } + listOf("Storm Spark",
        "Cinder Bolt", "Curse Word", "Festering Gremlin", "Tinkered Mite", "Tinkered Mite", "Viper Adept", "Grim Prowler",
        "Bloodbond Acolyte", "Hill Ogre", "Twinblade Duelist", "Dawnlance Squire", "Gloomblossom Carrier", "Hill Ogre")
    private val bobDeck = List(8) { "Island" } + List(6) { "Forest" } + listOf("Canopy Visionary", "Glance Ahead",
        "Gearwing Glider", "Gearwing Glider", "Ironhide Brute", "Stout Bear", "Stout Bear", "Haven Warden",
        "Sunning Mudlizard", "Storm Spark", "Tinkered Mite", "Mull It Over", "Stout Bear")

    private fun deal(table: AtwTable, random: Random) {
        for ((player, deck) in listOf(table.alice to aliceDeck, table.bob to bobDeck)) {
            val shuffled = deck.shuffled(random)
            table.put(player, PlayZone.HAND, *shuffled.take(7).toTypedArray())
            table.put(player, PlayZone.LIBRARY, *shuffled.drop(7).toTypedArray())
        }
    }

    /** One random acting-point step of a seeded game (as `StepRandomGamesTest` plays): a decision answered, a declaration,
     * a cast or land play, or a pass. [run] sends the call made by its body as the given actor. */
    private fun step(s: arcanum.engine.state.GameState, board: org.asyncttwiz.protocol.BoardState, random: Random,
        call: () -> EngineCall, run: (String, (EngineCall) -> Unit) -> Unit) {
        val decision = s.pending?.decision
        when {
            decision is Decision.Divide -> run(decision.player.value) { CombatFlow.divide(it) }
            decision != null -> {
                val answer = Engine.options(s, decision.player).filterIsInstance<Input.Answer>().randomOrNull(random)
                    ?: fail("no answer listed for $decision")
                run(decision.player.value) { StepFlow.answer(it, answer.answer) }
            }
            s.priority.holder == null -> {
                val c = call()
                val choices = CombatFlow.choices(c)
                val player = choices.declaring ?: fail("nobody holds priority and nothing is awaited in ${s.turn}")
                if (choices.attackers) {
                    val chosen = choices.attacks.entries.filter { random.nextBoolean() }.map { (card, lanes) -> LaneAttack(card, lanes.random(random)) }
                    run(player) { CombatFlow.declareAttackers(it, if (CombatFlow.attackRefusal(c, chosen) == null) chosen else choices.requiredAttacks) }
                } else {
                    val chosen = choices.blocks.entries.filter { random.nextBoolean() }.map { (card, attackers) -> LaneBlock(card, attackers.random(random)) }
                    run(player) { CombatFlow.declareBlockers(it, if (CombatFlow.blockRefusal(c, chosen) == null) chosen else choices.requiredBlocks) }
                }
            }
            else -> {
                val holder = HostPriority.wanted(s, HostPriority.kept(board))?.holder ?: s.priority.holder!!
                val doing = Engine.options(s, holder).filter { it is Input.Cast || it is Input.PlayLand }
                if (doing.isNotEmpty() && random.nextInt(3) > 0) run(holder.value) { c -> HostPriority.settle(c)
                    if (c.perform(doing.random(random)) == null) StepFlow.automatic(c) }
                else run(holder.value) { StepFlow.pass(it, holder.value, if (random.nextInt(5) == 0) StepFlow.Until.EndOfTurn else StepFlow.Until.Pass) }
            }
        }
    }

    // finalreview-sync #2 — I-D26 (redo assigns fresh timestamps), CR 613.7: the effect record of Alice's Surge of Vigor
    // (+3/+3 until end of turn, as the engine made it) is set by her event, and she undoes it. Bob then puts two creatures
    // onto the battlefield (stamps given meanwhile). Alice redoes:
    // `MtgFacts.freshStamps` shifts the stamp her event gave in ATW's field (`EngineRecord.timestamp`), but not the copy
    // inside the engine's opaque data (`EffectObject.timestamp`), which is what the engine reads: the effect keeps its old
    // stamp (tied with a permanent stamped meanwhile) and ATW's record says another one. The same holds for Stack items
    // (`MtgStackItem.timestamp` vs its data) and emblems. Expected: the engine sees the redone effect's fresh stamp, newer
    // than Bob's creatures
    @Test fun aRedoneEffectIsNewerThanWhatCameBetween() {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        val (bear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        table.put(alice, PlayZone.BATTLEFIELD, "Forest")
        table.put(alice, PlayZone.HAND, "Surge of Vigor")
        val (ogre, brute) = table.put(bob, PlayZone.HAND, "Hill Ogre", "Ironhide Brute")
        table.put(alice, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val start = table.state()
        val cast = TreeGame(start, table.definitions, name = "redo-stamps-cast")
        // Alice casts Surge of Vigor at her Bear; Bob can't respond, so it resolves in the same batch (the app's cast)
        val target = cast.engine().objects.values.single { it.zone == Zone.Battlefield && it.definition?.faces?.first()?.name == "Stout Bear" }.id
        cast.run(alice) { c ->
            val cast = Engine.options(c.working, PlayerId(alice)).filterIsInstance<Input.Cast>().single()
            c.perform(cast)
            while (c.pending != null) {
                val d = c.pending!!.decision
                when (d) {
                    is Decision.Targets -> c.perform(Input.Answer(d.id, arcanum.engine.decision.Answer.Targets(listOf(TargetRef.Object(target)))))
                    is Decision.ManaPayment -> c.perform(Input.Answer(d.id, arcanum.engine.decision.Answer.ManaPayment(d.suggestion)))
                    else -> fail("unexpected $d")
                }
            }
            StepFlow.automatic(c)
        }
        val effect = cast.state.board.engineRecords.single { it.kind == EngineRecordKind.Effect }
        // Only the effect's own event (one member, so the batch redo of #4 doesn't get in the way): Alice's record
        val game = TreeGame(start, table.definitions, name = "redo-stamps")
        game.manual(alice, PlayAction.Mtg(MtgAction.SetRecord(effect.id, null, effect))).let { assertNotNull(it.tree, "record: ${it.reason}") }
        assertEquals(effect.timestamp, game.engine().effects.values.single().timestamp)
        game.control(alice, GameAction.Undo).let { assertNotNull(it.tree, "Alice undoes it: ${it.reason}") }
        assertTrue(game.state.board.engineRecords.none { it.kind == EngineRecordKind.Effect })
        // Bob puts two creatures onto the battlefield (stamps given meanwhile; directly through the shared reducer)
        for ((i, card) in listOf(ogre, brute).withIndex()) {
            val placed = game.state.board.cards.single { it.id == card }
            game.manual(bob, PlayAction.MoveCard(card, MoveSource(placed.containerId, placed.placement),
                MoveDestination(table.container(bob, PlayZone.BATTLEFIELD), MoveInsertion.EMPTY, BoardPosition(12 + 2 * i, 2))))
                .let { assertNotNull(it.tree, "move: ${it.reason}") }
        }
        val redo = game.control(alice, GameAction.Redo)
        assertNotNull(redo.tree, "Alice redoes it: ${redo.reason}")
        val record = game.state.board.engineRecords.single { it.id == effect.id }
        val engineEffect = game.engine().effects.values.single()
        val bobsLatest = game.state.board.cards.filter { it.id in setOf(ogre, brute) }.maxOf { it.timestamp }
        assertTrue(record.timestamp!! > bobsLatest, "ATW's record is newer than Bob's creatures (I-D26)")
        assertEquals(record.timestamp, engineEffect.timestamp, "the engine reads the same stamp ATW keeps")
        assertTrue(engineEffect.timestamp > bobsLatest, "the engine's effect is newer than Bob's creatures (CR 613.7)")
    }

    // finalreview-sync #4 — I-D26, SYS-004, D59 ("a redo right after its undo gives back exactly the recorded event"):
    // Alice casts Storm Spark at Bob, who has Full control and an instant, so the spell waits on the Stack. Alice undoes
    // her cast and redoes it at once, nothing in between. A batch is redone member by member, each with
    // `MtgFacts.freshStamps` against the board its earlier members made: a later member that writes a stamp an earlier
    // member gave (the Stack item's timestamp, the spell card's) takes it for a stamp "given" again and shifts it above
    // the clock. Seen in the seeded games as a Stack item one stamp off, and as a redo refused ("targetTransitionBlocked")
    // when a later member's recorded before-value no longer matches. Expected: the redone batch is the recorded one
    @Test fun redoRightAfterUndoGivesBackTheBatch() {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        table.fullControl += bob
        val game = TreeGame(table.state(), table.definitions, name = "redo-batch")
        game.run(alice) { c ->
            c.perform(Engine.options(c.working, PlayerId(alice)).filterIsInstance<Input.Cast>().single())
            while (c.pending != null) when (val d = c.pending!!.decision) {
                is Decision.Targets -> c.perform(Input.Answer(d.id, arcanum.engine.decision.Answer.Targets(listOf(TargetRef.Player(PlayerId(bob))))))
                is Decision.ManaPayment -> c.perform(Input.Answer(d.id, arcanum.engine.decision.Answer.ManaPayment(d.suggestion)))
                else -> fail("unexpected $d")
            }
            StepFlow.automatic(c)
        }
        assertEquals(1, game.engine().stack.size, "the spell waits for Bob")
        val cast = game.state.board
        game.control(alice, GameAction.Undo).let { assertNotNull(it.tree, "undo: ${it.reason}") }
        game.control(alice, GameAction.Redo).let { assertNotNull(it.tree, "redo: ${it.reason}") }
        assertEquals("", boardDiff(cast, game.state.board))
    }

    /** Whisper Theft resolving: Bob has discarded, Alice's "you may draw" is open (a flow of two decisions). */
    private fun whisperTheftResolving(name: String): TreeGame {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.BATTLEFIELD, "Swamp")
        table.put(alice, PlayZone.HAND, "Whisper Theft")
        table.put(bob, PlayZone.HAND, "Hill Ogre", "Stout Bear")
        table.put(alice, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val game = TreeGame(table.state(), table.definitions, name = name)
        game.run(alice) { c ->
            c.perform(Engine.options(c.working, PlayerId(alice)).filterIsInstance<Input.Cast>().single())
            while (c.pending?.decision?.player?.value == alice) when (val d = c.pending!!.decision) {
                is Decision.Targets -> c.perform(Input.Answer(d.id, arcanum.engine.decision.Answer.Targets(listOf(TargetRef.Player(PlayerId(bob))))))
                is Decision.ManaPayment -> c.perform(Input.Answer(d.id, arcanum.engine.decision.Answer.ManaPayment(d.suggestion)))
                else -> fail("unexpected $d")
            }
            if (c.pending == null) StepFlow.automatic(c)
        }
        val discard = Engine.options(game.engine(), PlayerId(bob)).filterIsInstance<Input.Answer>().first()
        game.run(bob) { StepFlow.answer(it, discard.answer) }
        assertEquals(1, hand(game, bob))
        assertTrue(Automatum.flowOpen(game.state.board), "Alice's may draw is open")
        return game
    }

    private fun hand(game: TreeGame, player: String) =
        game.state.board.containers.single { it.id == table.container(player, PlayZone.HAND) }.cards.size
    private fun theft(game: TreeGame) = game.state.board.stackEffects.singleOrNull()

    /** [game] with a history that holds only its latest board (the flow's start is out of reach, as when the server
     * refuses its revert for any reason). */
    private fun withoutHistory(game: TreeGame) {
        val now = game.state
        game.tree = org.asyncttwiz.protocol.GameplayTree(now.copy(currentNodeId = now.root.id, currentBranchId = now.root.branchId))
    }

}
