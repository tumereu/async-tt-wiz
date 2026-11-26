package org.asyncttwiz.protocol.game

import arcanum.api.CardType
import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SYS-208, SYS-063, SYS-206 (problem report DH9MW5, 2026-11-26): Quick answers only questions with one answer. The engine lists a sample
 * of a choice of several (its first cards), so "discard two cards" out of a hand of different cards isn't a one-answer question
 * because one answer is listed: the player picks. Cards alike (the same card in the same state) still count as one answer.
 */
class QuickAnswerSampleTest {
    private val reverie = arcanum.engine.cards.Cards.invent("Quiet Reverie", "{0}", "Instant",
        "Draw three cards. Then discard two cards unless you discard an artifact card.")
    private val geode = arcanum.engine.cards.Cards.invent("Quiet Geode", "{0}", "Artifact", "{T}: Add {C}.")
    private val engine = arcanum.engine.cards.Cards.invent("Quiet Engine", "{0}", "Artifact", "{1}: You gain 1 life.")
    private val strip = arcanum.engine.cards.Cards.invent("Quiet Strip", "{0}", "Sorcery", "Each opponent discards two cards.")

    /** A game of Alice's [alice] and Bob's [bob] decks with the first seed whose start [wanted] takes, Alice in her first main phase
     * holding priority. */
    private fun atAlicesMain(alice: List<String>, bob: List<String>, wanted: (GameModel) -> Boolean): AsyncServer {
        for (seed in 1L..200L) {
            val server = AsyncServer(EngineGames(seed, alice to bob))
            server.start(server.alice)
            server.keepAndAssumeKeep(server.alice)
            repeat(40) {
                val s = server.state().engine
                if (s.turn.active == server.alice && s.turn.step == TurnStep.PrecombatMain && s.priority.holder == server.alice && s.stack.isEmpty())
                    return@repeat
                val holder = s.priority.holder!!
                server.act(server.alice, Input.PassPriority(holder), assumed = holder != server.alice).getOrThrow()
            }
            if (wanted(server.state())) return server
        }
        error("no seed deals what the test wants")
    }

    private fun names(model: GameModel, player: PlayerId): List<String> =
        model.engine.player(player).hand.map { model.engine.obj(it)!!.definition.faces.first().name }

    private fun artifacts(model: GameModel, player: PlayerId): Int =
        model.engine.player(player).hand.count { model.engine.obj(it)!!.definition.faces.first().types.contains(CardType.Artifact) }

    /** Alice casts [card] from her hand and both pass: it resolves up to its first decision. */
    private fun cast(server: AsyncServer, card: String): Decision {
        val id = server.state().engine.player(server.alice).hand.first { server.state().engine.obj(it)!!.definition.faces.first().name == card }
        server.act(server.alice, Input.Cast(server.alice, id), follow = { m ->
            val d = m.engine.pending?.decision
            if (d !is Decision.Targets || d.player != server.alice) null
            else Engine.options(m.engine, server.alice, EnginePreferences.of(m.engine)).filterIsInstance<Input.Answer>()
                .first { (it.answer as Answer.Targets).chosen == listOf(TargetRef.Player(server.bob)) }
        }).getOrThrow()
        if (server.state().engine.priority.holder == server.alice) server.act(server.alice, Input.PassPriority(server.alice)).getOrThrow()
        server.act(server.alice, Input.PassPriority(server.bob), assumed = true).getOrThrow()
        return assertNotNull(server.pending(), "$card asks")
    }

    private fun answer(server: AsyncServer, answer: Answer): Decision {
        server.act(server.alice, Input.Answer(server.pending()!!.id, answer)).getOrThrow()
        return assertNotNull(server.pending())
    }

    private companion object {
        val ALICE = PlayerId("alice")
        val BOB = PlayerId("bob")
    }

    private val reveries = List(10) { reverie } + List(10) { geode } + List(10) { engine } + List(10) { "Mountain" }

    // SYS-208, SYS-063, CR 118.12a (problem report DH9MW5): after "no", which two of her nine cards to discard is hers to pick, though the engine
    // lists only its first two
    @Test
    fun afterNoTheTwoCardsToDiscardAreLeftToThePlayer() {
        val server = atAlicesMain(reveries, List(40) { "Mountain" }) { m -> reverie in names(m, ALICE) }
        val may = assertIs<Decision.May>(cast(server, reverie))
        assertNull(WhoActs.quickAnswer(server.state(), may), "yes or no")
        val discard = assertIs<Decision.ChooseObjects>(answer(server, Answer.YesNo(false)))
        assertEquals(2, discard.min)
        assertEquals(9, discard.options.size)
        val judged = WhoActs.quickJudged(server.state(), discard)
        assertNull(judged.answer, "two of nine different cards: ${judged.why}")
        assertTrue("another selection works too" in judged.why, judged.why)
    }

    // SYS-208, SYS-063, CR 118.12a (problem report DH9MW5): after "yes", which of her two different artifact cards to discard is hers to pick
    @Test
    fun afterYesAChoiceBetweenTwoArtifactsIsLeftToThePlayer() {
        val server = atAlicesMain(reveries, List(40) { "Mountain" }) { m ->
            val alice = ALICE
            reverie in names(m, alice) && geode in names(m, alice) && engine in names(m, alice)
        }
        cast(server, reverie)
        val choose = assertIs<Decision.ChooseObjects>(answer(server, Answer.YesNo(true)))
        assertTrue(choose.options.size >= 2, "two artifacts or more: ${choose.options}")
        assertNull(WhoActs.quickAnswer(server.state(), choose))
    }

    // SYS-063 (owner 2026-11-19): with one artifact card, "yes" leaves one answer: Quick discards it
    @Test
    fun afterYesTheOnlyArtifactIsQuicksOneAnswer() {
        val server = atAlicesMain(List(10) { reverie } + List(3) { geode } + List(27) { "Mountain" }, List(40) { "Mountain" }) { m ->
            val alice = ALICE
            reverie in names(m, alice) && artifacts(m, alice) == 0 && m.engine.player(alice).library.take(3).count { id ->
                m.engine.obj(id)!!.definition.faces.first().name == geode } == 1
        }
        cast(server, reverie)
        val choose = assertIs<Decision.ChooseObjects>(answer(server, Answer.YesNo(true)))
        assertEquals(1, choose.options.size)
        val only = assertNotNull(WhoActs.quickAnswer(server.state(), choose), WhoActs.quickJudged(server.state(), choose).why)
        assertEquals(Answer.Objects(choose.options), only.answer)
    }

    // SYS-208, SYS-206, SYS-063: "discard two cards" from a hand of only Mountains has one answer (cards alike count as one); from a hand of
    // different cards it has many (problem report DH9MW5)
    @Test
    fun aDiscardOfTwoHasOneAnswerOnlyAmongCardsAlike() {
        val alike = atAlicesMain(List(40) { strip }, List(40) { "Mountain" }) { true }
        val fromMountains = assertIs<Decision.ChooseObjects>(cast(alike, strip))
        assertEquals(alike.bob, fromMountains.player)
        assertNotNull(WhoActs.quickAnswer(alike.state(), fromMountains), WhoActs.quickJudged(alike.state(), fromMountains).why)

        val different = atAlicesMain(List(40) { strip }, List(20) { "Mountain" } + List(20) { geode }) { m ->
            names(m, BOB).toSet().size == 2
        }
        val fromBoth = assertIs<Decision.ChooseObjects>(cast(different, strip))
        assertNull(WhoActs.quickAnswer(different.state(), fromBoth), "Mountains and Geodes: two Mountains, two Geodes or one of each")
    }

    // STK-033, SYS-063, CR 510.1c (fixed along the way with DH9MW5): Alice's 1/3 attacks and Bob blocks with two 2/2s; her one damage
    // may go to either, so her division is hers though the engine lists one way (all to the first)
    @Test
    fun aDivisionThatCanGoTwoWaysIsNoOneAnswerQuestion() {
        val prod = arcanum.engine.cards.Cards.invent("Quiet Prodder", "{0}", "Creature — Construct", "", "1/3")
        val pilot = arcanum.engine.cards.Cards.invent("Quiet Pilot", "{0}", "Creature — Zombie Pilot", "", "2/2")
        val server = AsyncServer(EngineGames(3, List(40) { prod } to List(40) { pilot }))
        val alice = server.alice; val bob = server.bob
        server.start(alice)
        server.keepAndAssumeKeep(alice)
        fun s() = server.state().engine
        fun named(p: PlayerId, name: String) = s().battlefield.filter { s().obj(it)?.controller == p && s().obj(it)?.definition?.faces?.first()?.name == name }
        var guard = 0
        while (guard++ < 300 && s().pending?.decision !is Decision.Divide) {
            val st = s()
            val holder = st.priority.holder
            val active = st.turn.active
            val wanted = if (active == alice) 1 else 2
            when {
                holder == active && st.stack.isEmpty() && st.turn.step == TurnStep.PrecombatMain && st.turn.number <= 2 &&
                    st.battlefield.count { st.obj(it)?.controller == active } < wanted -> {
                    val card = st.player(active).hand.first { st.obj(it)?.definition?.faces?.first()?.name == (if (active == alice) prod else pilot) }
                    server.act(active, Input.Cast(active, card))
                }
                holder != null -> server.act(holder, Input.PassPriority(holder))
                st.turn.step == TurnStep.DeclareAttackers -> server.act(active, Input.DeclareAttackers(active,
                    if (st.turn.number == 3) listOf(arcanum.engine.input.Attack(named(alice, prod).single(), TargetRef.Player(bob))) else emptyList()))
                else -> server.act(bob, Input.DeclareBlockers(bob, if (st.turn.number == 3) named(bob, pilot)
                    .map { arcanum.engine.input.Block(it, st.combat!!.attackers.single().id) } else emptyList()))
            }.getOrThrow()
        }
        val divide = assertIs<Decision.Divide>(s().pending?.decision, "her division")
        assertEquals(1, divide.amount)
        assertEquals(2, divide.among.size)
        val judged = WhoActs.quickJudged(server.state(), divide)
        assertNull(judged.answer, judged.why)
        assertTrue("STK-033" in judged.why, judged.why)
    }
}
