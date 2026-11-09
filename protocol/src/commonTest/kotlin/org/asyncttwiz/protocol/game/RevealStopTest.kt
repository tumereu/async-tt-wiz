package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HIS-110 (owner playtest 2026-11-07, 2026-11-09): reveals stop the player's view until dismissed, the other player's and
 * their own: one panel per reveal, in order, holding the later events like a playback stop (live and in catch-up playback),
 * and closed when the reveal is taken back. Only the player's view waits: the game goes on for the other player. The
 * player's own choice among cards they already see isn't a reveal for them.
 */
class RevealStopTest {
    private val insight = Cards.invent("Ember Insight", "{0}", "Sorcery", "Reveal the top card of your library and put that card into your hand.")

    /** Bob's deck of Islands and Ember Insights (a free reveal), Alice's of Forests. */
    private fun sim() = DeviceSim(EngineGames(3, List(40) { "Forest" } to (List(20) { "Island" } + List(20) { insight })))

    private fun hand(model: GameModel, player: PlayerId, name: String): ObjectId =
        model.engine.player(player).hand.first { model.engine.obj(it)?.definition?.faces?.first()?.name == name }

    /** Bob starts and keeps; Alice keeps and is away, so his device assumes her passes up to his main phase. */
    private fun DeviceSim.bobToMain() {
        start(bob)
        keepWhileAway(bob)
    }

    /** Bob casts an Ember Insight (his device passes after it; Alice's pass is assumed or made, and it resolves). */
    private fun DeviceSim.bobCastsInsight() {
        val bob = bob
        val cast = device(bob).act(Input.Cast(bob, hand(device(bob).model, bob, insight))) { m ->
            val d = m.engine.pending?.decision
            if (d == null || d is Decision.Random || d.player != bob) null
            else Engine.options(m.engine, bob).filterIsInstance<Input.Answer>().first()
        }
        assertIs<GameDevice.Acted.Sent>(cast)
    }

    /** The cards revealed in the server's main-line event [id]. */
    private fun DeviceSim.revealedIn(id: String): List<CardId> = server.line().first { it.id == id }.engine!!.changes
        .mapNotNull { ((it as? Change.Happened)?.event as? arcanum.engine.change.GameEvent.Revealed)?.ids }.flatten()
        .mapNotNull { (it as? ObjectId.Card)?.card }

    // HIS-110, HIS-093: in catch-up playback Bob's reveal is a playback stop: Alice's table stays at its event, with the
    // panel naming Bob, the revealed card and the Insight, the later events held, until she dismisses it; then playback
    // goes on to the head. Bob dismisses his own panel on his own phone, Alice's waiting for nothing of his
    @Test
    fun anOpponentsRevealHoldsCatchUpPlaybackUntilDismissed() {
        val sim = sim()
        sim.bobToMain()
        sim.bobCastsInsight()
        sim.settle(only = listOf(sim.bob))
        assertTrue(sim.device(sim.bob).dismissReveal(), "his own reveal opened his panel")
        // He ends his turn: events after the reveal
        sim.device(sim.bob).endTurn(sim.now)
        sim.settle(only = listOf(sim.bob))
        val alices = sim.load(sim.alice)
        assertNull(alices.reveal, "nothing shown yet")
        alices.play()
        alices.playToStop()
        val reveal = assertNotNull(alices.reveal, "stopped at Bob's reveal")
        assertEquals(sim.bob, reveal.by)
        assertEquals(alices.session.localHead, reveal.event, "her table at the reveal's event")
        assertEquals(sim.revealedIn(reveal.event), reveal.cards)
        assertEquals(1, reveal.cards.size)
        assertEquals(insight, reveal.sourceName)
        assertTrue(alices.session.behind, "the later events are held")
        assertIs<GameDevice.Status.Playing>(alices.status)
        assertNull(alices.step(), "playback waits for the dismissal")
        assertEquals(reveal.event, alices.session.localHead)
        assertTrue(alices.dismissReveal())
        assertNull(alices.reveal)
        alices.playToStop()
        assertEquals(sim.server.head, alices.session.localHead, "on to the head after the dismissal")
    }

    // HIS-110: live, Alice's view waits at Bob's reveal while the game goes on for Bob (his turn ends and his device assumes
    // her passes where her mode passes); her dismissal shows what followed
    @Test
    fun aLiveRevealHoldsTheViewWhileTheGameGoesOn() {
        val sim = sim()
        sim.bobToMain()
        sim.device(sim.alice).play()
        sim.settle()
        assertEquals(sim.server.head, sim.device(sim.alice).session.localHead, "Alice live")
        sim.bobCastsInsight()
        sim.settle()
        val alices = sim.device(sim.alice)
        val reveal = assertNotNull(alices.reveal, "the panel opens live")
        assertEquals(reveal.event, alices.session.localHead)
        val bobs = sim.device(sim.bob)
        assertEquals(reveal, bobs.reveal, "the same reveal, on his own phone")
        bobs.dismissReveal()
        bobs.endTurn(sim.now)
        sim.settle()
        assertTrue(sim.server.line().indexOfFirst { it.id == reveal.event } < sim.server.line().size - 1, "the game went on")
        assertEquals(reveal.event, alices.session.localHead, "her view still at the reveal")
        assertEquals(reveal.event, sim.server.heads[sim.alice], "her local head stays there")
        alices.dismissReveal()
        sim.settle()
        assertNull(alices.reveal)
        assertTrue(sim.server.line().indexOfFirst { it.id == alices.session.localHead } > sim.server.line().indexOfFirst { it.id == reveal.event },
            "playback went on past the reveal")
    }

    // HIS-110, SYS-077 (full e2e run 2026-11-07): Alice's phone passes on Bob's Insight itself, so her own pending pass
    // resolves it and opens the panel; Bob's assumption of that pass reaches the server first and then her phone, taking
    // her event's place (the same board): the panel stays open, now at his event, and her table stays there while he
    // plays on, until her OK
    @Test
    fun aRevealInHerOwnPassStaysOpenWhenBobsSameAssumptionTakesItsPlace() {
        val sim = sim()
        sim.bobToMain()
        sim.device(sim.alice).play()
        sim.settle()
        val alices = sim.device(sim.alice)
        val bobs = sim.device(sim.bob)
        sim.bobCastsInsight()
        sim.flush(sim.bob)
        // Only his cast reaches her before her phone passes on it
        sim.deliver(sim.alice, 1)
        alices.playToStop()
        alices.advance(sim.now)
        val mine = alices.session.pending.singleOrNull()
        assertNotNull(mine, "her own pass on its way")
        assertEquals(Input.PassPriority(sim.alice), mine.engine!!.steps.first().input)
        val opened = assertNotNull(alices.reveal, "the panel opens on her own pass")
        assertEquals(mine.id, opened.event)
        // His device assumes her pass; his event reaches the server, then her phone, before hers
        bobs.advance(sim.now)
        sim.flush(sim.bob)
        val assumption = sim.server.line().last()
        assertEquals(Input.PassPriority(sim.alice), assumption.assumed?.input, "Bob assumed her pass")
        var shown = 0
        alices.session.beforeShow = { _, _ -> shown++ }
        sim.deliver(sim.alice)
        assertEquals(assumption.id, alices.adoptedAs(mine.id), "his event took her event's place")
        assertEquals(0, shown, "nothing played back")
        val reveal = assertNotNull(alices.reveal, "the panel still open")
        assertEquals(listOf(reveal), alices.openReveals, "one panel")
        assertEquals(opened.copy(event = assumption.id), reveal)
        assertEquals(assumption.id, alices.session.localHead, "her table at the reveal's event")
        sim.flush(sim.alice)
        assertEquals(1, sim.confirmations, "her pass confirmed his assumption")
        // He plays on: his turn ends; her table waits at the reveal
        bobs.endTurn(sim.now)
        sim.settle()
        assertTrue(sim.server.line().last().id != assumption.id, "the game went on")
        assertEquals(assumption.id, alices.session.localHead, "her view still at the reveal")
        assertTrue(alices.session.behind)
        assertEquals(reveal, alices.reveal)
        assertTrue(alices.dismissReveal())
        sim.settle()
        assertNull(alices.reveal)
        assertTrue(sim.server.line().indexOfFirst { it.id == alices.session.localHead } > sim.server.line().indexOfFirst { it.id == assumption.id },
            "playback went on past the reveal")
    }

    // HIS-110 (owner, 2026-11-09): Bob's own reveal of a card he couldn't see stops his own view until he dismisses it; Alice's
    // phone goes on regardless: her OK and his are each their own
    @Test
    fun anOwnRevealStopsTheOwnViewOnlyAndEachOkIsOwn() {
        val sim = sim()
        sim.bobToMain()
        sim.device(sim.alice).play()
        sim.settle()
        sim.bobCastsInsight()
        sim.settle()
        val alices = sim.device(sim.alice)
        val bobs = sim.device(sim.bob)
        val own = assertNotNull(bobs.reveal, "his own panel")
        assertEquals(sim.bob, own.by)
        assertEquals(1, own.cards.size)
        assertEquals(own, alices.reveal)
        assertTrue(alices.dismissReveal())
        assertEquals(own, bobs.reveal, "her OK isn't his")
        sim.settle()
        // A later event of Alice's waits on his phone until his OK
        assertTrue(bobs.dismissReveal())
        assertNull(bobs.reveal)
        assertNull(alices.reveal, "his OK isn't hers")
    }

    // HIS-110 (owner, 2026-11-09): a player's own choice among cards they already see isn't a reveal: nothing asks them twice.
    // A card chosen in a search, or revealed and then chosen among, opens no panel of theirs, nor their own hand; each still does on Alice's phone
    @Test
    fun ownChoiceAmongCardsTheySeeOpensNoPanelOfTheirs() {
        val tutor = Cards.invent("Ember Tutor", "{0}", "Sorcery", "Search your library for a basic land card, reveal it, put it into your hand, then shuffle.")
        val exposed = Cards.invent("Ember Boast", "{0}", "Sorcery", "Target player reveals their hand.")
        val sim = DeviceSim(EngineGames(3, List(40) { "Forest" } to (List(20) { "Island" } + List(10) { tutor } + List(10) { exposed })))
        sim.bobToMain()
        sim.device(sim.alice).play()
        sim.settle()
        val bobs = sim.device(sim.bob)
        val alices = sim.device(sim.alice)
        for ((card, what) in listOf(tutor to "the land he chose himself", exposed to "his own hand")) {
            val before = alices.openReveals.size
            val cast = bobs.act(Input.Cast(sim.bob, hand(bobs.model, sim.bob, card))) { m ->
                val d = m.engine.pending?.decision
                if (d == null || d is Decision.Random || d.player != sim.bob) null
                else Engine.options(m.engine, sim.bob).filterIsInstance<Input.Answer>().let { answers ->
                    answers.firstOrNull { (it.answer as? arcanum.engine.decision.Answer.Targets)?.chosen == listOf(arcanum.engine.state.TargetRef.Player(sim.bob)) } ?: answers.first()
                }
            }
            assertIs<GameDevice.Acted.Sent>(cast)
            sim.settle()
            // The search's answer comes once it resolves, if it wasn't folded in
            repeat(4) {
                val d = bobs.model.engine.pending?.decision
                if (d != null && d !is Decision.Random && d.player == sim.bob) {
                    bobs.act(Engine.options(bobs.model.engine, sim.bob).filterIsInstance<Input.Answer>().first())
                    sim.settle()
                }
            }
            assertNull(bobs.reveal, what)
            assertTrue(alices.openReveals.size > before, "Alice's panel for $what")
            while (alices.reveal != null) alices.dismissReveal()
            sim.settle()
        }
    }

    // HIS-110 (owner, 2026-11-09, House Cartographer's wording): "reveal cards from the top of your library until you reveal a
    // land card" is a reveal: the event records every card turned over, the land last, and Alice's phone opens the panel on them
    @Test
    fun revealUntilALandCountsAsARevealForEveryCardTurned() {
        val mapmaker = Cards.invent("Ember Mapmaker", "{0}", "Creature — Human Scout Survivor", """
            Haste
            Survival — At the beginning of your second main phase, if this creature is tapped, reveal cards from the top of your library until you reveal a land card. Put that card into your hand and the rest on the bottom of your library in a random order.
        """, "1/1")
        val sim = DeviceSim(EngineGames(3, List(40) { "Forest" } to (List(10) { "Island" } + List(30) { mapmaker })))
        sim.bobToMain()
        val bobs = sim.device(sim.bob)
        assertIs<GameDevice.Acted.Sent>(bobs.act(Input.Cast(sim.bob, hand(bobs.model, sim.bob, mapmaker))))
        sim.settle(only = listOf(sim.bob))
        fun revealed() = sim.server.line().firstOrNull { sim.revealedIn(it.id).isNotEmpty() }
        var attacked = false
        repeat(60) {
            if (revealed() == null) {
                val s = bobs.model.engine
                val creature = s.battlefield.firstOrNull { s.obj(it)?.controller == sim.bob && s.obj(it)?.tapped != true &&
                    s.obj(it)?.definition?.faces?.first()?.name == mapmaker }
                val attack = Engine.options(s, sim.bob).filterIsInstance<Input.DeclareAttackers>().isNotEmpty()
                when {
                    bobs.status is GameDevice.Status.Waiting -> bobs.assume()
                    attack && !attacked && creature != null -> { attacked = true
                        bobs.act(Input.DeclareAttackers(sim.bob, listOf(arcanum.engine.input.Attack(creature, arcanum.engine.state.TargetRef.Player(sim.alice))))) }
                    bobs.status == GameDevice.Status.YourMove -> bobs.act(Input.PassPriority(sim.bob))
                }
                sim.settle(only = listOf(sim.bob))
            }
        }
        // Its random order keeps Bob's phone from assuming Alice's pass over it (the information rule): her real pass resolves it
        assertEquals(GameDevice.Wait.HiddenCards, (bobs.status as GameDevice.Status.Waiting).reason)
        val alices = sim.load(sim.alice)
        alices.play()
        repeat(30) {
            if (revealed() == null) {
                alices.playToStop()
                when (val status = alices.status) {
                    is GameDevice.Status.Stopped -> alices.act(status.assumed)
                    GameDevice.Status.YourMove -> alices.act(Input.PassPriority(sim.alice))
                    else -> {}
                }
                sim.settle()
            }
        }
        val event = assertNotNull(revealed(), "the trigger recorded a reveal")
        val cards = sim.revealedIn(event.id)
        val state = bobs.model.engine
        fun name(card: CardId) = state.current(card)?.definition?.faces?.first()?.name
        assertEquals("Island", name(cards.last()), "up to the land")
        assertTrue(cards.dropLast(1).none { name(it) == "Island" }, "no land before it")
        val reveal = assertNotNull(alices.reveal, "Alice's panel")
        assertEquals(sim.bob, reveal.by)
        assertEquals(cards, reveal.cards)
        assertEquals(mapmaker, reveal.sourceName)

    }

    /** Bob's Insight on the way: Alice's phone has passed on it itself (the panel open on her own pending pass), Bob's phone has
     * made the same pass as its assumption, on the server first; nothing of it has reached her phone yet. */
    private fun DeviceSim.bothMadeTheSamePass(): GameEvent {
        bobToMain()
        device(alice).play()
        settle()
        bobCastsInsight()
        flush(bob)
        deliver(alice, 1)
        device(alice).playToStop()
        device(alice).advance(now)
        assertNotNull(device(alice).session.pending.singleOrNull(), "her own pass on its way")
        assertNotNull(device(alice).reveal, "the panel on her own pass")
        device(bob).advance(now)
        flush(bob)
        return server.line().last().also { assertEquals(Input.PassPriority(alice), it.assumed?.input, "Bob assumed her pass") }
    }

    // HIS-110, SYS-077 (full e2e run 2026-11-09): Alice taps OK on the panel of her own pending pass; Bob's same assumption then
    // takes its place: a reveal she dismissed stays dismissed: nothing asks her twice, and the view isn't held
    @Test
    fun aDismissedRevealStaysDismissedWhenTheOtherPlayersSameEventTakesItsPlace() {
        val sim = sim()
        val assumption = sim.bothMadeTheSamePass()
        val alices = sim.device(sim.alice)
        assertTrue(alices.dismissReveal())
        assertNull(alices.reveal)
        sim.deliver(sim.alice)
        assertEquals(assumption.id, alices.adoptedAs(alices.session.localHead) ?: alices.session.localHead, "his event in her event's place")
        assertNull(alices.reveal, "not opened again")
        assertTrue(alices.openReveals.isEmpty())
        // The other phone's own panel is untouched by any of it, until its own OK
        assertNotNull(sim.device(sim.bob).reveal, "Bob's own panel")
    }

    // HIS-110: a reveal is closed only by that player's OK, or by being taken back. Each OK is its own, both ways, and what
    // arrives meanwhile (the other player's reports of what they saw, a revision with nothing in it, a chat's, an undo request and
    // its cancelling, a suggestion and its withdrawing) leaves the panel open
    @Test
    fun anOpenRevealIsClosedOnlyByItsOwnOk() {
        val sim = sim()
        sim.bobToMain()
        sim.device(sim.alice).play()
        sim.settle()
        sim.bobCastsInsight()
        sim.settle()
        val alices = sim.device(sim.alice)
        val bobs = sim.device(sim.bob)
        val hers = assertNotNull(alices.reveal)
        val his = assertNotNull(bobs.reveal)
        fun bothOpen(what: String) {
            sim.settle()
            assertEquals(hers.cards, alices.reveal?.cards, "Alice's panel after $what")
            assertEquals(his, bobs.reveal, "Bob's panel after $what")
        }
        repeat(3) { sim.now += 3_000; alices.advance(sim.now); bobs.advance(sim.now); bothOpen("reports of what was seen") }
        alices.revision(emptyList(), null); bobs.revision(emptyList(), null)
        bothOpen("a revision with no event")
        val target = assertNotNull(bobs.undoTarget())
        sim.settle()
        bobs.requestUndo(target, "sorry")
        sim.settle()
        bobs.cancelRequest()
        bothOpen("an undo request and its cancelling")
        assertIs<GameDevice.Acted.Sent>(bobs.suggest(arcanum.engine.setup.GameResult.Draw))
        sim.settle()
        assertEquals(his, bobs.reveal, "Bob's panel with his suggestion out")
        assertNotNull(alices.reveal, "Alice's panel with his suggestion on its way")
        assertIs<GameDevice.Acted.Sent>(bobs.table(listOf(TableChange.Suggestion(bobs.model.table.suggestion, null))))
        bothOpen("a suggestion and its withdrawing")
        assertTrue(bobs.dismissReveal())
        sim.settle()
        assertNull(bobs.reveal)
        assertNotNull(alices.reveal, "his OK isn't hers")
        assertTrue(alices.dismissReveal())
        sim.settle()
        assertNull(alices.reveal)
        assertNull(bobs.reveal, "and hers isn't his")
    }

    // HIS-110: loaded again while the phone stays in the game (it couldn't follow the server, a reconnect past what it received),
    // an open panel stays open and a dismissed one stays dismissed; a game opened anew opens none, as ever
    @Test
    fun aRevealOutlivesTheGameLoadingAgain() {
        val sim = sim()
        sim.bobToMain()
        sim.device(sim.alice).play()
        sim.settle()
        sim.bobCastsInsight()
        sim.settle()
        val before = sim.device(sim.alice)
        val open = assertNotNull(before.reveal)
        val again = sim.load(sim.alice)
        assertNull(again.reveal, "opened anew: none")
        again.carryReveals(before)
        again.play()
        again.playToStop()
        assertEquals(open, again.reveal, "open before, open after (as soon as the board shows its event)")
        assertTrue(again.dismissReveal())
        val third = sim.load(sim.alice)
        third.carryReveals(again)
        third.play()
        third.playToStop()
        assertNull(third.reveal, "dismissed before, dismissed after")
    }

    // HIS-110, TRN-065: Bob's concession while Alice's panel of his reveal is open: her table stays for her own OK, and the panel
    // stays for its own: it comes first, then the result
    @Test
    fun aRevealStaysOpenWhenTheGameEndsAtTheTable() {
        val sim = sim()
        sim.bobToMain()
        sim.device(sim.alice).play()
        sim.settle()
        sim.bobCastsInsight()
        sim.settle()
        val alices = sim.device(sim.alice)
        val open = assertNotNull(alices.reveal)
        assertIs<GameDevice.Acted.Sent>(sim.device(sim.bob).concede())
        sim.settle()
        assertTrue(alices.showToTheEnd(), "shown to the end")
        assertEquals(open.cards, alices.reveal?.cards, "her OK is still hers to give")
        assertTrue(alices.dismissReveal())
        assertNull(alices.reveal)
    }

    // HIS-110: several reveals in a row open one panel each, in order
    @Test
    fun severalRevealsOpenOnePanelEachInOrder() {
        val sim = sim()
        sim.bobToMain()
        sim.bobCastsInsight()
        sim.settle(only = listOf(sim.bob))
        sim.bobCastsInsight()
        sim.settle(only = listOf(sim.bob))
        val alices = sim.load(sim.alice)
        alices.play()
        alices.playToStop()
        val first = assertNotNull(alices.reveal)
        alices.playToStop()
        assertEquals(first, alices.reveal, "still the first")
        alices.dismissReveal()
        alices.playToStop()
        val second = assertNotNull(alices.reveal, "the second reveal's panel")
        assertTrue(second.event != first.event)
        assertTrue(second.cards != first.cards)
        alices.dismissReveal()
        alices.playToStop()
        assertNull(alices.reveal)
        assertEquals(sim.server.head, alices.session.localHead)
    }

    // HIS-110, HIS-099: a reveal taken back before Alice dismissed it (Bob's undo of his Insight, which she approves)
    // closes its panel
    @Test
    fun aRevealTakenBackClosesItsPanel() {
        val sim = sim()
        sim.bobToMain()
        sim.device(sim.alice).play()
        sim.settle()
        val beforeCast = sim.server.head
        sim.bobCastsInsight()
        sim.settle()
        val alices = sim.device(sim.alice)
        assertNotNull(alices.reveal)
        val bobs = sim.device(sim.bob)
        val request = assertIs<GameDevice.Undo.Request>(bobs.undo(beforeCast))
        bobs.requestUndo(request.target, "")
        sim.settle()
        alices.answerRequest(accept = true)
        sim.settle()
        assertEquals(beforeCast, alices.session.localHead)
        assertNull(alices.reveal, "the panel closed with its reveal")
    }
}
