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
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Final adversarial review of the switch, history, undo/redo, branches and flow reverts with Automatum ("finalreview-sync",
 * report `arcanum-final-sync-review.md` outside the repo). Each ignored test pins a problem found and fails while it isn't
 * fixed; the others are checks that hold. Invented cards only.
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

    /** Automatum switched off and on again with nothing done by hand in between (I-D42 drops the turn's engine data). */
    private fun offAndOn(rt: RoundTrip, by: String = alice) {
        manual(rt, by, PlayAction.Mtg(MtgAction.SetAutomatum(false)))
        manual(rt, by, PlayAction.Mtg(MtgAction.SetAutomatum(true)))
    }

    /** Whoever holds priority as the host takes it passes (the step button). */
    private fun pass(rt: RoundTrip) {
        val s = rt.call().working
        val holder = (HostPriority.wanted(s, HostPriority.kept(rt.state.board))?.holder ?: s.priority.holder)?.value
            ?: fail("nobody holds priority in ${position(rt.state)} and nothing is asked: ${CombatFlow.choices(rt.call()).declaring}")
        rt.run(holder) { StepFlow.pass(it, holder) }
    }

    // finalreview-sync #1 — I-D42, I-D28, SYS-070 (CR 508.1, 506.4): Alice's Stout Bear attacks Bob; Bob (Full control)
    // holds priority in the declare attackers step. Automatum is switched off and on there, nothing done by hand. The
    // combat record is dropped (turn-scoped), and in the declare attackers step `EngineState.from` reads the lanes as an
    // undeclared draft, so the rebuilt engine has no combat at all: the tapped Bear no longer attacks (it can't be
    // declared again), the combat is skipped and Bob takes no damage. Expected: the declared attack stands (Bob takes 2)
    @Ignore("finalreview-sync #1: off/on after attackers were declared loses the attack (declare attackers step)")
    @Test fun switchingOffAndOnAfterAttackersWereDeclaredKeepsTheAttack() {
        val (bear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        val rt = combatGame()
        rt.run(alice) { StepFlow.pass(it, alice) }
        while (position(rt.state) != TurnPosition.DECLARE_ATTACKERS) pass(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(bear))) }
        assertEquals(TurnPosition.DECLARE_ATTACKERS, position(rt.state), "Bob holds priority after the declaration")
        val before = rt.engine().combat
        offAndOn(rt)
        assertEquals(before, rt.engine().combat, "the engine still sees the declared attack")
        var guard = 12
        while (position(rt.state) != TurnPosition.POSTCOMBAT_MAIN && guard-- > 0) pass(rt)
        assertEquals("18", life(rt.state, bob), "the Bear's 2 damage")
    }

    // finalreview-sync #1 — I-D42, SYS-070 (CR 509.1, 509.1h): as above in the declare blockers step after Bob's Hill Ogre
    // blocked the Bear. Read back from the lanes the combat lacks `blockersDeclaredBy`/`blocksDecided`, so the engine's
    // combat differs from what it was. Expected: the same combat (nobody is asked to declare blockers again; the Ogre
    // kills the Bear)
    @Ignore("finalreview-sync #1: off/on after blockers were declared forgets that they were declared")
    @Test fun switchingOffAndOnAfterBlockersWereDeclaredKeepsTheBlocks() {
        val (bear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        val rt = combatGame()
        rt.run(alice) { StepFlow.pass(it, alice) }
        while (position(rt.state) != TurnPosition.DECLARE_ATTACKERS) pass(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(bear))) }
        while (position(rt.state) == TurnPosition.DECLARE_ATTACKERS) pass(rt)
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, bear))) }
        assertEquals(TurnPosition.DECLARE_BLOCKERS, position(rt.state))
        val before = rt.engine().combat
        offAndOn(rt)
        assertNull(CombatFlow.choices(rt.call()).declaring, "nobody is asked to declare again")
        assertEquals(before, rt.engine().combat, "the engine's combat is the one declared")
        var guard = 12
        while (position(rt.state) != TurnPosition.POSTCOMBAT_MAIN && guard-- > 0) pass(rt)
        assertEquals(PlayZone.GRAVEYARD, zone(rt.state, bear))
        assertEquals("20", life(rt.state, bob))
    }

    // finalreview-sync #1 (worse: stuck) — I-D42, CR 509.1, 510.1c: Alice's Ironhide Brute (5/5 trample) is blocked by
    // Bob's Stout Bear and Hill Ogre. After off/on in the declare blockers step the engine asks Bob to declare blockers
    // again (above); he declares the same blocks (Next step declares what the lanes hold). The engine's combat now has
    // each blocker twice, so the attacker's damage division names a blocker twice and the engine refuses every answer
    // ("The same share's target twice"): the division can't be made, the game can't go on with Automatum. Seen first in the
    // seeded games (`FinalSyncGamesTest`, "Clear Automatum data" in that step). Expected: combat damage is dealt
    @Ignore("finalreview-sync #1: declaring blockers again after off/on doubles them and the division can't be answered")
    @Test fun blocksDeclaredAgainAfterOffAndOnCanStillBeDivided() {
        val (brute) = table.put(alice, PlayZone.BATTLEFIELD, "Ironhide Brute")
        val (bear, ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Stout Bear", "Hill Ogre")
        val rt = combatGame()
        rt.run(alice) { StepFlow.pass(it, alice) }
        while (position(rt.state) != TurnPosition.DECLARE_ATTACKERS) pass(rt)
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(brute))) }
        while (position(rt.state) == TurnPosition.DECLARE_ATTACKERS) pass(rt)
        val blocks = listOf(LaneBlock(bear, brute), LaneBlock(ogre, brute))
        rt.run(bob) { CombatFlow.declareBlockers(it, blocks) }
        offAndOn(rt)
        if (CombatFlow.choices(rt.call()).declaring == bob) rt.run(bob) { CombatFlow.declareBlockers(it, CombatFlow.lanes(rt.state.board.combat).second) }
        var guard = 12
        while (CombatFlow.division(rt.call()) == null && position(rt.state) != TurnPosition.POSTCOMBAT_MAIN && guard-- > 0) pass(rt)
        val division = assertNotNull(CombatFlow.division(rt.call()), "the Brute's division is asked")
        rt.run(division.player) { CombatFlow.divide(it) }
        assertEquals(listOf(PlayZone.GRAVEYARD, PlayZone.GRAVEYARD), listOf(zone(rt.state, bear), zone(rt.state, ogre)),
            "the suggestion kills both blockers")
    }

    // finalreview-sync #5 — HIS-037, SYS-057 #4, I-D41 (cf. pausereview #10): Bob starts a branch from an earlier acting
    // point (a status, as Hold at a pause makes one) and then undoes that event. The live position is now the branch point,
    // which has the old line after it, so the next event there starts a branch again, and a branch reseeds the libraries'
    // unknown order (`LibraryKnowledge.reseeds`), changing the board the automated batch was planned on (`MtgTag.board`):
    // every Automatum batch from the live position is refused ("inapplicable") — the step button, casts, answers, for
    // both players — until someone makes an event by hand there, which Automatum refuses for everything but notes and
    // statuses. Expected: the next Automatum action from the live position is accepted
    @Ignore("finalreview-sync #5: after undoing a branch's first event every Automatum batch is refused")
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
        val sent = game.send(call, bob)
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

    // finalreview-sync #1 — I-D42, I-D28 (best-effort recovery; "state rebuild must equal the engine's own"): in seeded
    // games, at every point where no flow is open, Automatum is switched off and on again with nothing done by hand. The
    // engine state rebuilt afterwards should be the one before. It differs only in combat (declare attackers, declare
    // blockers, combat damage, end of combat steps): see the two tests above.
    @Ignore("finalreview-sync #1: off/on with nothing done by hand changes the engine's combat")
    @Test fun switchingOffAndOnWithNothingDoneByHandKeepsTheEngineState() {
        val differences = mutableListOf<String>()
        var points = 0
        for (seed in listOf(51L, 52L)) {
            val random = Random(seed)
            val table = AtwTable("final-sync-offon-$seed")
            table.fullControl += table.players
            deal(table, random)
            val rt = RoundTrip(table.state(), table.definitions, seed)
            repeat(150) { call ->
                val s = rt.engine()
                if (Engine.result(s) != null) return@repeat
                if (s.pending == null) {
                    points++
                    val before = rt.state
                    offAndOn(rt, table.alice)
                    val problems = StateCompare.exact(s, rt.engine())
                    if (problems.isNotEmpty()) differences += "seed $seed call $call ${position(rt.state)}: ${problems.first().take(300)}"
                    rt.state = before
                    rt.tags.removeAt(rt.tags.lastIndex); rt.tags.removeAt(rt.tags.lastIndex)
                }
                step(s, rt.state.board, random, { rt.call() }) { actor, body -> rt.run(actor, body = body) }
            }
        }
        println("finalreview-sync off/on: $points points, ${differences.size} differ")
        assertTrue(differences.isEmpty(), "${differences.size} of $points points differ after off/on:\n" + differences.take(4).joinToString("\n"))
    }

    // finalreview-sync #2 — I-D26 (redo assigns fresh timestamps), CR 613.7: the effect record of Alice's Surge of Vigor
    // (+3/+3 until end of turn, as the engine made it) is set by her event, and she undoes it. Bob then turns Automatum
    // off, puts two creatures onto the battlefield by hand (stamps given meanwhile) and turns it on again. Alice redoes:
    // `MtgFacts.freshStamps` shifts the stamp her event gave in ATW's field (`EngineRecord.timestamp`), but not the copy
    // inside the engine's opaque data (`EffectObject.timestamp`), which is what the engine reads: the effect keeps its old
    // stamp (tied with a permanent stamped meanwhile) and ATW's record says another one. The same holds for Stack items
    // (`MtgStackItem.timestamp` vs its data) and emblems. Expected: the engine sees the redone effect's fresh stamp, newer
    // than Bob's creatures
    @Ignore("finalreview-sync #2: redo shifts ATW's stamps but not the engine data's, so the engine keeps the old stamp")
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
        // Bob, with Automatum off, puts two creatures onto the battlefield by hand; then on again
        game.manual(bob, PlayAction.Mtg(MtgAction.SetAutomatum(false))).let { assertNotNull(it.tree, "off: ${it.reason}") }
        for ((i, card) in listOf(ogre, brute).withIndex()) {
            val placed = game.state.board.cards.single { it.id == card }
            game.manual(bob, PlayAction.MoveCard(card, MoveSource(placed.containerId, placed.placement),
                MoveDestination(table.container(bob, PlayZone.BATTLEFIELD), MoveInsertion.EMPTY, BoardPosition(12 + 2 * i, 2))))
                .let { assertNotNull(it.tree, "move: ${it.reason}") }
        }
        game.manual(bob, PlayAction.Mtg(MtgAction.SetAutomatum(true))).let { assertNotNull(it.tree, "on: ${it.reason}") }
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
    @Ignore("finalreview-sync #4: redo right after undo shifts stamps inside the batch (or is refused)")
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

    // finalreview-sync #3 — I-D19, I-D8, SYS-060: a real two-player flow reverted on the server. Alice casts Whisper
    // Theft at Bob; it resolves at once and Bob is asked what to discard (the flow opens in Alice's batch); Bob answers
    // (his batch; then Alice's "you may draw" is open) and sets a status. Reverting the flow takes back both players'
    // events: the board, the engine state and the tags are as before Alice's cast, nobody can redo them, and the cast can
    // be made again. Actual: the planner settles the records after each engine change, so Bob's answer batch closes the
    // continuation (`SetRecord(continuation, X, null)`) and opens the next one (`SetRecord(continuation, null, Y)`) as two
    // members; `FlowRevert.opens` takes that member for the flow's start, and only Bob's batch is taken back. The flow
    // stays open on Bob's discard and switching off then closes it there: Whisper Theft is left on the Stack half
    // resolved, the Swamp tapped
    @Ignore("finalreview-sync #3: a flow of several decisions is reverted only to its latest decision")
    @Test fun aFlowAnsweredByBothPlayersIsRevertedExactly() {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.BATTLEFIELD, "Swamp")
        table.put(alice, PlayZone.HAND, "Whisper Theft")
        table.put(bob, PlayZone.HAND, "Hill Ogre", "Stout Bear")
        table.put(alice, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val game = TreeGame(table.state(), table.definitions, name = "revert")
        val before = game.state
        val beforeEngine = game.engine()
        val beforeTags = game.tags()
        fun cast() = game.run(alice) { c ->
            c.perform(Engine.options(c.working, PlayerId(alice)).filterIsInstance<Input.Cast>().single())
            while (c.pending?.decision?.player?.value == alice) {
                when (val d = c.pending!!.decision) {
                    is Decision.Targets -> c.perform(Input.Answer(d.id, arcanum.engine.decision.Answer.Targets(listOf(TargetRef.Player(PlayerId(bob))))))
                    is Decision.ManaPayment -> c.perform(Input.Answer(d.id, arcanum.engine.decision.Answer.ManaPayment(d.suggestion)))
                    else -> fail("unexpected $d")
                }
            }
            if (c.pending == null) StepFlow.automatic(c)
        }
        cast()
        assertTrue(Automatum.flowOpen(game.state.board), "Bob's discard is open")
        assertEquals(bob, game.engine().pending?.decision?.player?.value)
        val discard = Engine.options(game.engine(), PlayerId(bob)).filterIsInstance<Input.Answer>().first()
        game.run(bob) { StepFlow.answer(it, discard.answer) }
        assertNotNull(game.manual(bob, PlayAction.SetStatus("Thinking")).tree)
        assertTrue(Automatum.flowOpen(game.state.board), "Alice's may draw is open")
        val reverted = game.control(alice, GameAction.RevertFlow)
        assertNotNull(reverted.tree, "${reverted.reason}")
        assertEquals("", boardDiff(before.board, game.state.board))
        assertEquals(before.currentNodeId, game.head)
        assertEquals(beforeTags, game.tags())
        assertTrue(StateCompare.exact(beforeEngine, game.engine()).isEmpty())
        assertTrue(game.tree.redoByAccount.isEmpty())
        cast()
        assertTrue(Automatum.flowOpen(game.state.board), "the cast is made again")
    }

    // finalreview-sync #7 — I-D24, SYS-060, CR 608.2: "Clear Automatum data" is offered with Automatum on during an open
    // flow (the switch's hold panel; `TableToolkitController.refusal` lets Automatum's own controls through). Whisper
    // Theft is resolving: Bob has discarded, Alice's "you may draw" is open. Clearing removes the continuation (where the
    // resolution was) but keeps what it already did and the spell on the Stack, so the next pass resolves the spell again
    // from its start: Bob discards a second card. Expected: clearing during a flow takes the flow back first (as switching
    // off does, I-D19), or isn't offered while a flow is open; either way the spell's effect happens once
    @Ignore("finalreview-sync #7: clearing Automatum data during a flow resolves the spell again")
    @Test fun clearingAutomatumDataDuringAFlowDoesNotResolveTheSpellAgain() {
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.BATTLEFIELD, "Swamp")
        table.put(alice, PlayZone.HAND, "Whisper Theft")
        table.put(bob, PlayZone.HAND, "Hill Ogre", "Stout Bear")
        table.put(alice, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val game = TreeGame(table.state(), table.definitions, name = "clear-flow")
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
        fun bobsHand() = game.state.board.containers.single { it.id == table.container(bob, PlayZone.HAND) }.cards.size
        assertEquals(1, bobsHand())
        assertTrue(Automatum.flowOpen(game.state.board), "Alice's may draw is open")
        game.manual(bob, PlayAction.Mtg(MtgAction.ClearRecords)).let { assertNotNull(it.tree, "clear: ${it.reason}") }
        // The game goes on: whoever holds priority passes until the Stack is empty
        var guard = 8
        while (game.engine().stack.isNotEmpty() && guard-- > 0) {
            val s = game.engine()
            val d = s.pending?.decision
            if (d != null) game.run(d.player.value) { StepFlow.answer(it, Engine.options(s, d.player).filterIsInstance<Input.Answer>().first().answer) }
            else {
                val holder = (HostPriority.wanted(s, HostPriority.kept(game.state.board))?.holder ?: s.priority.holder)!!.value
                game.run(holder) { StepFlow.pass(it, holder) }
            }
        }
        assertEquals(1, bobsHand(), "Bob discarded once")
    }
}
