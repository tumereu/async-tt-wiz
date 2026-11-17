package org.asyncttwiz.protocol.game

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first turn during the other player's mulligans (HIS-115, HIS-194, HIS-116, TRN-027): Alice keeps, assumes Bob's keep and
 * plays turn 1; each opening move of Bob's at the assumed keep makes her line again on top of it, standing as far as it
 * comes out the same as she could tell, and branching from the first event that doesn't.
 */
class ReapplyTest {
    private val cub = Cards.invent("Ember Cub", "{0}", "Creature — Elemental Cat", "", "1/1")
    private val spark = Cards.invent("Ember Spark", "{0}", "Instant", "Ember Spark deals 1 damage to any target.")
    private val glimmer = Cards.invent("Ember Glimmer", "{0}", "Sorcery", "Draw a card.")
    private val bounty = Cards.invent("Ember Bounty", "{0}", "Sorcery", "Each player draws a card.")
    private val echo = Cards.invent("Ember Echo", "{0}", "Sorcery",
        "Ember Echo deals damage to target player equal to the number of cards in that player's hand.")
    private val augury = Cards.invent("Ember Augury", "{0}", "Sorcery", "Target player reveals the top two cards of their library.")

    private fun game(alice: List<String>, bob: List<String> = List(40) { "Island" }, seed: Long = 7): AsyncServer =
        AsyncServer(EngineGames(seed, alice to bob)).also { it.start(freeFirstMulligan = true); it.reveals = org.asyncttwiz.protocol.AssumeReveals() }

    private fun hand(model: GameModel, p: PlayerId, name: String): ObjectId =
        model.engine.player(p).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Alice keeps, then assumes Bob's keep (HIS-096, HIS-183: a press of Assume keep) and passes to her first main phase. */
    private fun AsyncServer.keepAndPlay() {
        act(alice, Input.Answer(pending()!!.id, Answer.Option("Keep"))).getOrThrow()
        assumeKeep()
        playOn { it.engine.turn.active == alice && it.engine.turn.step == TurnStep.PrecombatMain && it.engine.priority.holder == alice }
    }

    private fun AsyncServer.assumeKeep() {
        while (true) act(alice, Reapply.assumedKeep(state().engine, alice) ?: return, assumed = true).getOrThrow()
    }

    /** Alice passes (Bob's passes assumed) until [done]. */
    private fun AsyncServer.playOn(done: (GameModel) -> Boolean) {
        repeat(60) {
            val s = state()
            if (done(s)) return
            check(s.engine.pending == null) { "a decision is open: ${s.engine.pending}" }
            val holder = s.engine.priority.holder!!
            act(alice, Input.PassPriority(holder), assumed = holder != alice).getOrThrow()
        }
        error("not reached")
    }

    /** Alice casts [name], its targets Bob. */
    private fun AsyncServer.casts(name: String) {
        act(alice, Input.Cast(alice, hand(state(), alice, name)), follow = flow(alice) { _, answers ->
            answers.firstOrNull { a -> a.toString().contains("Player(id=bob)") } ?: answers.firstOrNull()
        }).getOrThrow()
        // It resolves (Bob's pass assumed)
        playOn { it.engine.stack.isEmpty() }
    }

    /** Bob's opening [answer] at his local head (the assumed keep follows it): the server's result. */
    private fun AsyncServer.bob(answer: (Decision, GameModel) -> Answer): Acceptance.Result {
        // His playback shows the opening up to the assumption about him (HIS-094, HIS-161: it is never played back)
        MainLine.after(history, head, heads.getValue(bob))!!.takeWhile { it.assumed == null }.lastOrNull()?.let { seen(bob, it.id) }
        val at = heads.getValue(bob)
        val model = stateAt(at)
        val d = model.engine.pending!!.decision
        val (submission, _) = make(model, at, bob, Input.Answer(d.id, answer(d, model)))
        return submit(submission, bob).getOrThrow()
    }

    private fun mulligan(@Suppress("UNUSED_PARAMETER") d: Decision, @Suppress("UNUSED_PARAMETER") m: GameModel) = Answer.Option("Mulligan")

    // HIS-115, HIS-193, HIS-116, TRN-027: Mountain, Ember Cub, Ember Spark to Bob's face, End turn: Bob's free mulligan, his mulligan to
    // six and a bottom choice other than the stand-in each make it again and it all stands; Bob's Keep then confirms it
    @Test
    fun aTurnThatDoesntReadTheOpponentsHandStandsThroughEveryMulligan() {
        val server = game(List(14) { "Mountain" } + List(13) { cub } + List(13) { spark })
        server.keepAndPlay()
        server.act(server.alice, Input.PlayLand(server.alice, hand(server.state(), server.alice, "Mountain"))).getOrThrow()
        server.casts(cub)
        server.casts(spark)
        server.playOn { it.engine.turn.number == 2 && it.engine.turn.step == TurnStep.PrecombatMain }
        val aliceHand = server.state().engine.player(server.alice).hand.toList()
        val first = assertIs<Acceptance.Reapplied>(server.bob(::mulligan))
        assertNull(first.reapply.stoppedAt, "stands: ${first.reapply.why}")
        assertTrue(first.reapply.events.drop(1).all { it.carriedFrom != null })
        assertEquals(first.reapply.events.last().id, server.heads[server.alice], "her local head on the event made from hers")
        // To six: the stand-in bottom choice, then the keep, made again with the line
        val second = assertIs<Acceptance.Reapplied>(server.bob(::mulligan))
        assertNull(second.reapply.stoppedAt, "stands: ${second.reapply.why}")
        val standIn = second.reapply.events.first().assumed?.input
        assertIs<Answer.Objects>((standIn as Input.Answer).answer)
        // A bottom choice other than the stand-in
        val third = assertIs<Acceptance.Reapplied>(server.bob { d, m -> Answer.Objects(m.engine.player(server.bob).hand.take((d as Decision.ChooseObjects).min)) })
        assertNull(third.reapply.stoppedAt, "stands: ${third.reapply.why}")
        // Bob keeps as assumed: confirmed, nothing made again
        assertIs<Acceptance.Confirmed>(server.bob { _, _ -> Answer.Option("Keep") })
        val s = server.state().engine
        assertEquals(19, s.player(server.bob).life)
        assertEquals(6 + 1, s.player(server.bob).hand.size, "six kept, his turn-2 draw")
        assertEquals(aliceHand, s.player(server.alice).hand.toList(), "her hand as it was")
        assertEquals(0, server.lines, "no new line")
    }

    // HIS-116, HIS-212, HIS-042, HIS-192: Ember Glimmer draws her a card and Ember Bounty draws one for each player: both stand (her library is
    // her own stream's; Bob draws a hidden card either way)
    @Test
    fun drawsStand() {
        val server = game(List(14) { "Mountain" } + List(13) { glimmer } + List(13) { bounty })
        server.keepAndPlay()
        server.casts(glimmer)
        server.casts(bounty)
        val drawn = server.state().engine.player(server.alice).hand.toList()
        val r = assertIs<Acceptance.Reapplied>(server.bob(::mulligan))
        assertNull(r.reapply.stoppedAt, "stands: ${r.reapply.why}")
        assertEquals(drawn, server.state().engine.player(server.alice).hand.toList())
    }

    // HIS-116: Ember Echo at Bob deals damage equal to his hand: his free mulligan to seven keeps it, his mulligan to six
    // branches at the cast (the land before it stands)
    @Test
    fun anEffectCountingTheOpponentsHandStandsOnlyWhileTheCountDoes() {
        val server = game(List(20) { "Mountain" } + List(20) { echo })
        server.keepAndPlay()
        server.act(server.alice, Input.PlayLand(server.alice, hand(server.state(), server.alice, "Mountain"))).getOrThrow()
        server.casts(echo)
        assertEquals(13, server.state().engine.player(server.bob).life)
        val free = assertIs<Acceptance.Reapplied>(server.bob(::mulligan))
        assertNull(free.reapply.stoppedAt, "seven either way: ${free.reapply.why}")
        val six = assertIs<Acceptance.Reapplied>(server.bob(::mulligan))
        val stopped = assertNotNull(six.reapply.stoppedAt)
        assertTrue(stopped.engine!!.steps.first().input is Input.Cast, "taken back from the cast: ${six.reapply.why}")
        assertTrue(server.state().engine.battlefield.isNotEmpty(), "the land stands")
        assertEquals(20, server.state().engine.player(server.bob).life)
        assertEquals(1, server.lines, "a new line is due at the branch point")
        assertEquals(server.head, server.heads[server.alice], "her local head back at the branch point")
    }

    // HIS-116: a land, then Ember Augury at Bob (his top two cards revealed): the land stands, the reveal is taken back
    @Test
    fun aRevealOfTheOpponentsLibraryIsTakenBack() {
        val server = game(List(20) { "Mountain" } + List(20) { augury })
        server.keepAndPlay()
        server.act(server.alice, Input.PlayLand(server.alice, hand(server.state(), server.alice, "Mountain"))).getOrThrow()
        server.casts(augury)
        val r = assertIs<Acceptance.Reapplied>(server.bob(::mulligan))
        val stopped = assertNotNull(r.reapply.stoppedAt)
        assertTrue(stopped.engine!!.steps.first().input is Input.Cast, "taken back from the cast: ${r.reapply.why}")
        assertTrue(server.state().engine.battlefield.any { server.state().engine.obj(it)?.owner == server.alice }, "the land stands")
    }

    // HIS-115, HIS-193, HIS-117, TRN-070: on the devices: Alice presses Assume keep and casts an Ember Cub; Bob's playback stops before
    // the assumed keep (nothing of her turn shown), and his Mulligan there re-applies her line: her table doesn't go back
    // ("Bob took a mulligan · your turn stands"), her local head moves to the event made from hers, and his playback stops
    // before the assumption made again (his mulligan owes a card: its stand-in bottom choice first)
    @Test
    fun theDevicesFollowAReapply() {
        val sim = DeviceSim(EngineGames(7, List(40) { cub } to List(40) { "Island" }))
        sim.start(sim.alice, mode = ResponseMode.Normal, alwaysAssume = true)
        val alice = sim.alice; val bob = sim.bob
        val a = sim.device(alice)
        assertIs<GameDevice.Acted.Sent>(a.act(sim.keep(alice)))
        sim.settle(only = listOf(alice))
        assertEquals(GameDevice.Offer.AssumeKeep, assertIs<GameDevice.Status.Waiting>(a.status).offer)
        assertIs<GameDevice.Acted.Sent>(a.assume())
        sim.settle(only = listOf(alice))
        assertIs<GameDevice.Acted.Sent>(a.act(Input.Cast(alice, hand(a.model, alice, cub)), follow = sim.server.flow(alice)))
        sim.settle(only = listOf(alice))
        val cubs = a.model.engine.battlefield.size
        assertTrue(cubs > 0)
        a.notes()
        sim.deliver(bob)
        val b = sim.device(bob)
        b.playToStop()
        val stop = assertIs<GameDevice.Status.Stopped>(b.status)
        assertEquals(0, b.model.engine.turn.number, "Bob sees nothing of her turn")
        val d = b.model.engine.pending!!.decision
        assertIs<GameDevice.Acted.Sent>(b.act(Input.Answer(d.id, Answer.Option("Mulligan"))))
        assertTrue(stop.assumed is Input.Answer)
        sim.flush(bob)
        assertEquals(1, sim.reapplies)
        sim.deliver(alice)
        val notes = a.notes()
        assertTrue(notes.any { it is GameDevice.Note.Stands && it.mulligan }, "her turn stands: $notes")
        assertTrue(notes.none { it is GameDevice.Note.Branched }, "no take-back: $notes")
        assertEquals(cubs, a.model.engine.battlefield.size, "her Cub stays")
        assertEquals(sim.server.head, a.session.localHead)
        assertTrue(a.session.events.last().carriedFrom != null)
        sim.deliver(bob)
        b.playToStop()
        assertIs<GameDevice.Status.Stopped>(b.status)
        assertEquals(0, b.model.engine.turn.number, "still nothing of her turn")
        assertTrue(sim.equivalence().isEmpty(), sim.equivalence().toString())
    }

    // HIS-096, HIS-183, HIS-184, I-D56 §3: Assume keep only by the starting player once their own opening is done, and only as the keep (or
    // the stand-in bottom) of Bob's open decision
    @Test
    fun assumeKeepIsRefusedWhereItIsntAllowed() {
        val server = game(List(40) { "Mountain" })
        val d = server.pending()!!
        assertTrue(server.act(server.alice, Input.Answer(d.id, Answer.Option("Keep")), assumed = true).isFailure, "not before her own keep")
        server.act(server.alice, Input.Answer(d.id, Answer.Option("Keep"))).getOrThrow()
        val bobs = server.pending()!!
        assertTrue(server.act(server.bob, Input.Answer(bobs.id, Answer.Option("Keep")), assumed = true).isFailure, "Bob doesn't assume his own")
        assertTrue(server.act(server.alice, Input.Answer(bobs.id, Answer.Option("Mulligan")), assumed = true).isFailure, "a mulligan isn't assumed")
        server.act(server.alice, Input.Answer(bobs.id, Answer.Option("Keep")), assumed = true).getOrThrow()
        assertEquals(1, server.state().engine.turn.number)
    }

    /** Alice keeps; Bob, at the head, mulligans [mulligans] times (the first free), each event's id in order. */
    private fun AsyncServer.aliceKeepsBobMulligans(mulligans: Int): List<String> {
        act(alice, Input.Answer(pending()!!.id, Answer.Option("Keep"))).getOrThrow()
        return List(mulligans) {
            act(bob, Input.Answer(pending()!!.id, Answer.Option("Mulligan"))).getOrThrow()
            head
        }.also { seen(alice, head) }
    }

    // HIS-115, HIS-196, HIS-087, HIS-204 (spec §3.6): Bob mulligans twice, Alice assumes his keep (its stand-in bottom choice first) and plays a
    // Mountain and an Ember Cub; Bob's own bottom choice re-applies her line; his undo of that bottom choice (free: it showed
    // him nothing new, and her line counts for nothing) re-applies her line again on the point it lands on: it all stands,
    // the first event made starts the rewind's new line (HIS-042), her local head moves to the event made from hers, his stays
    // at the undo's point, before the assumed keep made again (HIS-117)
    @Test
    fun bobsRewindToBeforeTheAssumedKeepMakesHerLineAgain() {
        val server = game(List(14) { "Mountain" } + List(13) { cub } + List(13) { spark })
        val (_, second) = server.aliceKeepsBobMulligans(2)
        server.assumeKeep()
        server.playOn { it.engine.turn.active == server.alice && it.engine.turn.step == TurnStep.PrecombatMain && it.engine.priority.holder == server.alice }
        server.act(server.alice, Input.PlayLand(server.alice, hand(server.state(), server.alice, "Mountain"))).getOrThrow()
        server.casts(cub)
        val bottom = assertIs<Acceptance.Reapplied>(server.bob { d, m -> Answer.Objects(m.engine.player(server.bob).hand.take((d as Decision.ChooseObjects).min)) })
        assertNull(bottom.reapply.stoppedAt, bottom.reapply.why)
        assertEquals(second, bottom.event.parentId)
        val field = server.state().engine.battlefield.size
        assertNull(server.rewindNeeds(server.bob, second), "free: his bottom choice showed him nothing, her line isn't taken back")
        val linesBefore = server.lines
        server.rewind(server.bob, second)
        val r = assertNotNull(server.rewound, "re-applied")
        assertNull(r.reapply.stoppedAt, r.reapply.why)
        assertEquals(linesBefore + 1, r.reapply.events.first().startsLine, "the rewind's new line starts with the first event made")
        assertTrue(r.reapply.events.drop(1).all { it.startsLine == null })
        assertIs<Answer.Objects>((r.reapply.events.first().assumed?.input as Input.Answer).answer, "the stand-in bottom choice again")
        assertEquals(second, r.reapply.events.first().parentId)
        assertEquals(r.reapply.events.last().id, server.head)
        assertEquals(server.head, server.heads[server.alice], "her local head on the event made from hers")
        assertEquals(second, server.heads[server.bob], "his at the undo's point")
        assertEquals(field, server.state().engine.battlefield.size, "her Mountain and Cub stand")
        // His playback stops before the assumed keep made again: nothing of her turn reaches him
        assertEquals(r.reapply.events.first().id, server.nextStop(server.bob, ResponseMode.Normal)?.event)
    }

    // HIS-115, HIS-196, HIS-194, HIS-116, HIS-087, HIS-203 (spec §3.6): Bob mulligans to six; Alice assumes his keep and casts Ember Echo at him (six
    // damage). His undo of that mulligan needs her approval (it showed him a new hand); approved, her line is made again on
    // his seven cards: the Mountain stands, the Echo (seven now) is taken back from its cast
    @Test
    fun bobsApprovedRewindOfAMulliganTakesBackWhatCountedHisHand() {
        val server = game(List(20) { "Mountain" } + List(20) { echo })
        val (first, _) = server.aliceKeepsBobMulligans(2)
        server.assumeKeep()
        server.playOn { it.engine.turn.active == server.alice && it.engine.turn.step == TurnStep.PrecombatMain && it.engine.priority.holder == server.alice }
        server.act(server.alice, Input.PlayLand(server.alice, hand(server.state(), server.alice, "Mountain"))).getOrThrow()
        server.casts(echo)
        assertEquals(14, server.state().engine.player(server.bob).life, "six cards in his hand")
        assertEquals("shown", server.rewindNeeds(server.bob, first), "his mulligan showed him a new hand")
        server.rewind(server.bob, first)
        val r = assertNotNull(server.rewound)
        val stopped = assertNotNull(r.reapply.stoppedAt)
        assertTrue(stopped.engine!!.steps.first().input is Input.Cast, "taken back from the cast: ${r.reapply.why}")
        val s = server.state().engine
        assertTrue(s.battlefield.any { s.obj(it)?.owner == server.alice }, "the Mountain stands")
        assertEquals(20, s.player(server.bob).life)
        assertEquals(server.head, server.heads[server.alice], "her local head back at the branch point")
        assertEquals(first, server.heads[server.bob])
    }

    // HIS-115, HIS-196, HIS-084, HIS-204 (spec §3.6): on the devices: Bob mulligans; Alice assumes his keep and casts an Ember Cub; Bob chooses his
    // bottom card; his Undo of that bottom choice is free on his phone (her line after it
    // isn't counted) and his playback stays before the assumed keep; Alice's phone follows the rewind without taking her
    // Cub back and without a note
    @Test
    fun theDevicesFollowBobsRewindToBeforeTheAssumedKeep() {
        val sim = DeviceSim(EngineGames(7, List(40) { cub } to List(40) { "Island" }))
        sim.start(sim.alice, mode = ResponseMode.Normal, alwaysAssume = true)
        val alice = sim.alice; val bob = sim.bob
        val a = sim.device(alice)
        val b = sim.device(bob)
        assertIs<GameDevice.Acted.Sent>(a.act(sim.keep(alice)))
        sim.settle(only = listOf(alice))
        sim.deliver(bob)
        // His mulligan (not free between two players) owes a card to the bottom
        b.playToStop()
        assertIs<GameDevice.Acted.Sent>(b.act(Input.Answer(b.model.engine.pending!!.decision.id, Answer.Option("Mulligan"))))
        sim.flush(bob); sim.deliver(bob)
        sim.deliver(alice)
        a.playToStop()
        assertIs<GameDevice.Acted.Sent>(a.assume())
        sim.settle(only = listOf(alice))
        assertIs<GameDevice.Acted.Sent>(a.act(Input.Cast(alice, hand(a.model, alice, cub)), follow = sim.server.flow(alice)))
        sim.settle(only = listOf(alice))
        val cubs = a.model.engine.battlefield.size
        assertTrue(cubs > 0)
        sim.deliver(bob)
        b.playToStop()
        val d = assertIs<Decision.ChooseObjects>(b.model.engine.pending!!.decision)
        assertIs<GameDevice.Acted.Sent>(b.act(Input.Answer(d.id, Answer.Objects(b.model.engine.player(bob).hand.take(d.min)))))
        sim.flush(bob)
        assertEquals(1, sim.reapplies)
        sim.deliver(alice); a.notes()
        sim.deliver(bob)
        b.playToStop()
        val target = assertNotNull(b.undoTarget())
        assertIs<GameDevice.Undo.Free>(b.undo(target), "free: ${b.undoWhy}")
        sim.flush(bob)
        assertEquals(1, sim.rewinds)
        assertNotNull(sim.server.rewound, "re-applied")
        sim.deliver(alice)
        val notes = a.notes()
        assertTrue(notes.none { it is GameDevice.Note.Branched || it is GameDevice.Note.TakenBack }, "nothing taken back: $notes")
        assertEquals(cubs, a.model.engine.battlefield.size, "her Cub stays")
        assertEquals(sim.server.head, a.session.localHead)
        assertTrue(a.session.events.last().carriedFrom != null)
        sim.deliver(bob)
        b.playToStop()
        assertIs<GameDevice.Status.Stopped>(b.status)
        assertEquals(0, b.model.engine.turn.number, "nothing of her turn")
        assertIs<Decision.ChooseObjects>(b.model.engine.pending!!.decision, "his bottom choice open again")
        assertTrue(sim.equivalence().isEmpty(), sim.equivalence().toString())
    }

    private val vanguard = Cards.invent("Ember Vanguard", "{3}", "Creature — Human Soldier",
        "If this card is in your opening hand, you may begin the game with it on the battlefield.", "2/2")

    private val vanguards = List(34) { "Island" } + List(6) { vanguard }

    /** A game whose deal gives Bob no Ember Vanguard in his first hand and one after his (free) mulligan. */
    private fun vanguardGame(alice: List<String>): AsyncServer = game(alice, vanguards, vanguardSeed(free = true))

    /**
     * A seed whose deal gives Bob no Ember Vanguard in his first hand and one after a mulligan ([free], as [game] starts it;
     * else as a [DeviceSim] does, between two players: then not among the card his stand-in bottom choice puts back).
     */
    private fun vanguardSeed(free: Boolean): Long = (1L..400L).first { seed ->
        val s = AsyncServer(EngineGames(seed, List(40) { "Mountain" } to vanguards)).also { it.start(freeFirstMulligan = free.takeIf { it }) }
        fun vanguards(owed: Int) = s.state().engine.let { e -> e.player(s.bob).hand.dropLast(owed).count { e.obj(it)?.definition?.faces?.first()?.name == vanguard } }
        if (vanguards(0) != 0) return@first false
        s.aliceKeepsBobMulligans(1)
        vanguards(if (free) 0 else 1) > 0
    }

    // HIS-115, HIS-194, CR 103.6a: Alice assumes Bob's keep and plays a Mountain; Bob's mulligan brings him an Ember Vanguard, which
    // may begin the game on the battlefield: after the assumed keep made again the engine asks him (a real wait), so nothing
    // of her turn comes out the same and it is all taken back (outcome 3). His keep then confirms the assumed keep, he puts
    // the Vanguard onto the battlefield and turn 1 begins again for her with it
    @Test
    fun anOpeningThatChangesTheBoardTakesTheWholeTurnBack() {
        val server = vanguardGame(List(40) { "Mountain" })
        server.act(server.alice, Input.Answer(server.pending()!!.id, Answer.Option("Keep"))).getOrThrow()
        server.assumeKeep()
        server.playOn { it.engine.turn.active == server.alice && it.engine.turn.step == TurnStep.PrecombatMain && it.engine.priority.holder == server.alice }
        server.act(server.alice, Input.PlayLand(server.alice, hand(server.state(), server.alice, "Mountain"))).getOrThrow()
        val r = assertIs<Acceptance.Reapplied>(server.bob(::mulligan))
        assertNotNull(r.reapply.stoppedAt, "nothing comes out the same")
        assertEquals(1, r.reapply.events.size, "the assumed keep alone made again: ${r.reapply.record}")
        val s = server.state().engine
        assertTrue(s.battlefield.isEmpty(), "the Mountain went back")
        assertEquals(0, s.turn.number)
        assertEquals(server.bob, s.pending?.decision?.player, "his opening-hand action waits for him")
        assertEquals(server.head, server.heads[server.alice], "her local head back at the branch point")
        assertEquals(1, server.lines, "a new line is due there")
        // His keep confirms the assumed keep; he puts the Vanguard onto the battlefield; her turn 1 begins with it
        val bobAt = server.heads.getValue(server.bob)
        val keep = Input.Answer(server.stateAt(bobAt).engine.pending!!.decision.id, Answer.Option("Keep"))
        assertIs<Acceptance.Confirmed>(server.submit(server.make(server.stateAt(bobAt), bobAt, server.bob, keep).first, server.bob).getOrThrow())
        val choice = assertIs<Decision.ChooseObjects>(server.pending())
        server.act(server.bob, Input.Answer(choice.id, Answer.Objects(choice.options.take(1)))).getOrThrow()
        val after = server.state().engine
        assertEquals(1, after.battlefield.count { after.obj(it)?.definition?.faces?.first()?.name == vanguard })
        assertEquals(1, after.turn.number)
    }

    // TRN-064, CR 104.3a: Bob concedes in his opening while Alice plays on his assumed keep: today's concession while behind,
    // a branch at his local head (nothing made again), Alice wins; Alice's concession on her line ends the game at once
    @Test
    fun aConcessionByEitherPlayerDuringTheOpening() {
        val bobConcedes = game(List(14) { "Mountain" } + List(26) { cub })
        bobConcedes.keepAndPlay()
        bobConcedes.casts(cub)
        val at = bobConcedes.heads.getValue(bobConcedes.bob)
        val (concession, _) = bobConcedes.make(bobConcedes.stateAt(at), at, bobConcedes.bob, Input.Concede(bobConcedes.bob), newLine = bobConcedes.lines + 1)
        val accepted = assertIs<Acceptance.Accepted>(bobConcedes.submit(concession, bobConcedes.bob).getOrThrow())
        assertNotNull(accepted.discarded, "a branch: her line after his local head discarded")
        assertEquals(arcanum.engine.setup.GameResult.Won(bobConcedes.alice), Engine.result(bobConcedes.state().engine))
        val aliceConcedes = game(List(14) { "Mountain" } + List(26) { cub })
        aliceConcedes.keepAndPlay()
        aliceConcedes.casts(cub)
        assertIs<Acceptance.Accepted>(aliceConcedes.act(aliceConcedes.alice, Input.Concede(aliceConcedes.alice)).getOrThrow())
        assertEquals(arcanum.engine.setup.GameResult.Won(aliceConcedes.bob), Engine.result(aliceConcedes.state().engine))
    }

    /** On the devices: Alice keeps and presses Assume keep, her phone playing on to her Main 1; Bob's device delivered. */
    private fun DeviceSim.aliceOnAssumedKeep(): Pair<GameDevice, GameDevice> {
        start(alice, mode = ResponseMode.Normal, alwaysAssume = true)
        val a = device(alice); val b = device(bob)
        check(a.act(keep(alice)) is GameDevice.Acted.Sent)
        settle(only = listOf(alice))
        check(a.assume() is GameDevice.Acted.Sent)
        settle(only = listOf(alice))
        check(a.model.engine.turn.number == 1 && a.model.engine.priority.holder == alice) { "her Main 1: ${a.status}" }
        deliver(bob)
        b.playToStop()
        return a to b
    }

    // HIS-115, HIS-195, SYS-077 (§3.5): Alice's land play is on its way when the server takes Bob's mulligan first: the land is refused
    // as stale, her phone makes it again on the event made from the one it was made on, and it ends up played once, with
    // no note
    @Test
    fun aMoveOnItsWayDuringAReapplyIsMadeAgain() {
        val sim = DeviceSim(EngineGames(7, List(20) { "Mountain" } + List(20) { cub } to List(40) { "Island" }))
        val (a, b) = sim.aliceOnAssumedKeep()
        a.notes()
        assertIs<GameDevice.Acted.Sent>(a.act(Input.PlayLand(sim.alice, hand(a.model, sim.alice, "Mountain"))))
        assertIs<GameDevice.Acted.Sent>(b.act(Input.Answer(b.model.engine.pending!!.decision.id, Answer.Option("Mulligan"))))
        sim.flush(sim.bob)
        sim.flush(sim.alice)
        sim.settle()
        val lands = sim.server.line().count { e -> e.engine?.steps.orEmpty().any { it.input is Input.PlayLand } }
        assertEquals(1, lands, "the land played once")
        assertEquals(1, a.model.engine.battlefield.size)
        assertTrue(a.notes().none { it is GameDevice.Note.Refused || it is GameDevice.Note.Branched })
        assertTrue(sim.equivalence().isEmpty(), sim.equivalence().toString())
    }

    // HIS-115, HIS-195, SYS-012 (§3.5): Alice's land play is on its way when Bob's mulligan brings him an Ember Vanguard: her whole
    // turn is taken back, the land no longer applies there (his opening-hand action waits) and is dropped with the note
    @Test
    fun aMoveOnItsWayThatNoLongerAppliesIsDropped() {
        val sim = DeviceSim(EngineGames(vanguardSeed(free = false), List(40) { "Mountain" } to vanguards))
        val (a, b) = sim.aliceOnAssumedKeep()
        a.notes()
        assertIs<GameDevice.Acted.Sent>(a.act(Input.PlayLand(sim.alice, hand(a.model, sim.alice, "Mountain"))))
        assertIs<GameDevice.Acted.Sent>(b.act(Input.Answer(b.model.engine.pending!!.decision.id, Answer.Option("Mulligan"))))
        sim.flush(sim.bob)
        sim.flush(sim.alice)
        sim.settle()
        assertTrue(sim.server.line().none { e -> e.engine?.steps.orEmpty().any { it.input is Input.PlayLand } }, "no land played")
        assertTrue(a.model.engine.battlefield.isEmpty())
        assertEquals(0, a.model.engine.turn.number)
        val notes = a.notes()
        assertTrue(notes.any { it is GameDevice.Note.Refused && it.reason == "stale" }, "dropped with the note: $notes")
        assertTrue(sim.equivalence().isEmpty(), sim.equivalence().toString())
    }

    // HIS-115, HIS-084 (§3.5: a race settled by which the server takes first): Alice's undo of her Cub and Bob's mulligan
    // cross. Bob's first: her line is made again (the Cub too) and her undo, of an event no longer on the main line, is
    // refused; her phone follows the server's line. Hers first: the Cub is gone, and his mulligan re-applies the rest
    @Test
    fun anUndoRacingAReapply() {
        for (bobFirst in listOf(true, false)) {
            val sim = DeviceSim(EngineGames(7, List(20) { "Mountain" } + List(20) { cub } to List(40) { "Island" }))
            val (a, b) = sim.aliceOnAssumedKeep()
            assertIs<GameDevice.Acted.Sent>(a.act(Input.Cast(sim.alice, hand(a.model, sim.alice, cub)), follow = sim.server.flow(sim.alice)))
            sim.settle(only = listOf(sim.alice))
            assertTrue(a.model.engine.battlefield.isNotEmpty())
            a.notes()
            assertIs<GameDevice.Undo.Free>(a.undo(), "free: ${a.undoWhy}")
            assertIs<GameDevice.Acted.Sent>(b.act(Input.Answer(b.model.engine.pending!!.decision.id, Answer.Option("Mulligan"))))
            if (bobFirst) { sim.flush(sim.bob); sim.flush(sim.alice) } else { sim.flush(sim.alice); sim.flush(sim.bob) }
            sim.settle()
            val cubs = sim.server.state().engine.battlefield.size
            assertEquals(if (bobFirst) 1 else 0, cubs, "bobFirst $bobFirst")
            assertEquals(1, sim.reapplies, "bobFirst $bobFirst")
            assertEquals(if (bobFirst) 0 else 1, sim.rewinds, "bobFirst $bobFirst")
            assertEquals(cubs, a.model.engine.battlefield.size, "her phone follows the server (bobFirst $bobFirst)")
            assertTrue(sim.equivalence().isEmpty(), sim.equivalence().toString())
        }
    }

    // HIS-115: Bob's declaration and Alice's Assume keep cross: whichever the server takes first stands
    @Test
    fun assumeKeepCrossingBobsMulliganIsStale() {
        val server = game(List(40) { "Mountain" })
        server.act(server.alice, Input.Answer(server.pending()!!.id, Answer.Option("Keep"))).getOrThrow()
        val at = server.head
        val model = server.state()
        val keep = Reapply.assumedKeep(model.engine, server.alice)!!
        val (assumed, _) = server.make(server.stateAt(at), at, server.alice, keep, assumed = true)
        server.act(server.bob, Input.Answer(server.pending()!!.id, Answer.Option("Mulligan"))).getOrThrow()
        assertEquals("stale", server.submit(assumed, server.alice).exceptionOrNull()?.message)
    }
}
