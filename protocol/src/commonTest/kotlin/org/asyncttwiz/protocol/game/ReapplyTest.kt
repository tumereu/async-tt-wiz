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
 * The first turn during the other player's mulligans (HIS-115, HIS-116, TRN-027): Alice keeps, assumes Bob's keep and
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

    /** Alice keeps, then assumes Bob's keep (HIS-096: a press of Assume keep) and passes to her first main phase. */
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
        // His playback shows the opening up to the assumption about him (HIS-094: it is never played back)
        MainLine.after(history, head, heads.getValue(bob))!!.takeWhile { it.assumed == null }.lastOrNull()?.let { seen(bob, it.id) }
        val at = heads.getValue(bob)
        val model = stateAt(at)
        val d = model.engine.pending!!.decision
        val (submission, _) = make(model, at, bob, Input.Answer(d.id, answer(d, model)))
        return submit(submission, bob).getOrThrow()
    }

    private fun mulligan(@Suppress("UNUSED_PARAMETER") d: Decision, @Suppress("UNUSED_PARAMETER") m: GameModel) = Answer.Option("Mulligan")

    // HIS-115, HIS-116, TRN-027: Mountain, Ember Cub, Ember Spark to Bob's face, End turn: Bob's free mulligan, his mulligan to
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

    // HIS-116, HIS-042: Ember Glimmer draws her a card and Ember Bounty draws one for each player: both stand (her library is
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

    // HIS-115, HIS-117, TRN-070: on the devices: Alice presses Assume keep and casts an Ember Cub; Bob's playback stops before
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

    // HIS-096, I-D56 §3: Assume keep only by the starting player once their own opening is done, and only as the keep (or
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
