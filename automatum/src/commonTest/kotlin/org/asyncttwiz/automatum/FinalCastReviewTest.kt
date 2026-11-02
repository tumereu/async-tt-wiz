package org.asyncttwiz.automatum

import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Final adversarial review of casting, activating, the Stack, resolution and triggers through Automatum (report
 * `arcanum-final-cast-review.md`, outside the repo): each test pins a problem found, ignored while it isn't fixed.
 */
class FinalCastReviewTest {
    init { CombatCards.ensured }
    private val table = AtwTable("final-cast")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private val random = Random(5)
    private fun seed() = org.asyncttwiz.protocol.RandomV1.encodeSeed(random.nextBytes(32))
    private fun seeds() = CastFlow.Seeds(::seed)
    private fun log(rt: RoundTrip) = EngineLog.from(rt.tags, rt.state, table.definitions)
    private fun flow(rt: RoundTrip) = SharedFlow(rt.state, table.definitions, log(rt), ::seed)
    private fun player(id: String) = TargetRef.Player(PlayerId(id))

    /** [player] casts [card] as the app does: the first way, [targets] answered in order, the suggested mana. */
    private fun cast(rt: RoundTrip, player: String, card: String, vararg targets: (Decision.Targets) -> TargetRef): CastFlow {
        val plays = HandPlays.of(rt.state, table.definitions, player, log(rt))
        var flow = CastFlow.start(rt.state, table.definitions, player, log(rt), plays.of(card).first(), seeds())
        var next = 0
        while (true) {
            flow = when (val open = flow.pending ?: break) {
                is Decision.Targets -> flow.answer(Answer.Targets(listOf(targets[next++](open))))
                is Decision.ManaPayment -> flow.answer(Answer.ManaPayment(open.suggestion))
                else -> flow.answer(flow.listedAnswers().first())
            }
        }
        return flow
    }

    // finalreview-cast #1 (I-D27, CR 201.3): naming a card for Whispered Therapy-like spells lists the names of cards in
    // the opponent's hidden hand (the engine's listed answers are the names of cards in the game, on full information);
    // the decision panel shows the listed answers as its buttons, so the caster reads the opponent's hand
    @Ignore("finalreview-cast #1: card-name choices list the opponent's hidden hand cards")
    @Test fun namingACardDoesNotListTheOpponentsHiddenHand() {
        Cards.invent("Velvet Secret", "{1}{U}", "Instant", "Draw a card.")
        Cards.invent("Quiet Reserve", "{2}{G}", "Creature — Elf", "", "2/2")
        table.put(alice, PlayZone.BATTLEFIELD, "Swamp")
        val (therapy) = table.put(alice, PlayZone.HAND, "Whispered Therapy")
        table.put(bob, PlayZone.HAND, "Velvet Secret", "Quiet Reserve")
        val rt = RoundTrip(table.state(), table.definitions)
        rt.apply(cast(rt, alice, therapy, { player(bob) }).sent(), alice)
        val shared = flow(rt)
        val naming = assertIs<Decision.ChooseCardName>(shared.decision)
        assertEquals(alice, naming.player.value)
        val names = shared.listedAnswers().filterIsInstance<Answer.CardName>().map { it.name }
        assertTrue("Velvet Secret" !in names, "listed: $names")
    }

    // finalreview-cast #2 (CR 702.102a, DGM fuse, I-D6): the cast panel's "Cast as" chips and the card menu's rows name a
    // fused cast exactly like casting the left half alone
    @Ignore("finalreview-cast #2: a fused cast is labelled like the left half alone")
    @Test fun fusedCastHasItsOwnLabel() {
        val fuse = "Fuse (You may cast one or both halves of this card from your hand.)"
        val name = Cards.inventFaces("Split",
            face("Reel", "{1}{U}", "Instant", "Return target creature to its owner's hand.\n$fuse", null, null, null),
            face("Rend", "{2}{B}", "Instant", "Target player sacrifices a creature of their choice.\n$fuse", null, null, null))
        val definition = Cards.definition(name)
        val card = ObjectId.Card(arcanum.engine.state.CardId("c"), 0)
        val left = CastFlow.wayLabel(definition, Input.Cast(PlayerId(alice), card, CastWay(face = 0)))
        val fused = CastFlow.wayLabel(definition, Input.Cast(PlayerId(alice), card, CastWay(fused = true)))
        assertNotEquals(left, fused)
    }

    // finalreview-cast #3 (CR 117.3c, 117.4; CR 116.2b): Bob, holding priority after Alice passed on an empty Stack,
    // activates a mana ability (the same for a special action, turning a face-down creature face up); the engine leaves
    // him priority with no passes in a row, which HostPriority reads as "left from a turn changed by hand" and hands to
    // Alice, so Bob's next cast is refused ("Alice holds priority") while the board still shows him holding priority
    @Ignore("finalreview-cast #3: the non-active player loses priority after a mana ability or special action")
    @Test fun nonActivePlayerKeepsPriorityAfterAManaAbility() {
        Cards.invent("Sudden Vigor", "{G}", "Instant", "Target creature gets +3/+3 until end of turn.")
        table.turn = TurnState(alice, "1", TurnPosition.END_STEP.phase, TurnPosition.END_STEP.step)
        table.priority = MtgPriority(bob, 1)
        val (forest) = table.put(bob, PlayZone.BATTLEFIELD, "Forest")
        table.put(bob, PlayZone.BATTLEFIELD, "Stout Bear")
        val (vigor) = table.put(bob, PlayZone.HAND, "Sudden Vigor")
        val rt = RoundTrip(table.state(), table.definitions)
        val mana = CardPlays.of(rt.state, table.definitions, bob, log(rt)).of(forest).single()
        val flow = CastFlow.start(rt.state, table.definitions, bob, log(rt), assertNotNull(mana.input, "${mana.refusal}"), seeds())
        assertNull(flow.pending)
        rt.apply(flow.sent(), bob)
        assertEquals(bob, rt.state.board.priority?.holder)
        assertTrue(HandPlays.of(rt.state, table.definitions, bob, log(rt)).of(vigor).isNotEmpty(), "Bob casts with the {G} in his pool")
    }

    // finalreview-cast #4 (SYS-010, D59, CR 106.4): a mana pool holding two equal mana (one source's {R}{R}) gets one
    // more: the event can't be undone (GameplayHistory.reverse gives null), so a batch adding mana that way can't be
    // undone and the found game's RoundTrip fails; the sequence delta anchors the new entry "after R", which matches twice
    @Ignore("finalreview-cast #4: adding mana to a pool holding two equal mana can't be undone")
    @Test fun manaAddedAfterTwoEqualManaCanBeUndone() {
        val rt = RoundTrip(table.state(), table.definitions)
        val red = org.asyncttwiz.protocol.PoolMana("R", """{"color":null,"source":"c:ritual#7"}""")
        val black = org.asyncttwiz.protocol.PoolMana("B", """{"color":null,"source":"c:swamp#0"}""")
        fun event(before: List<org.asyncttwiz.protocol.PoolMana>, after: List<org.asyncttwiz.protocol.PoolMana>, n: Int) =
            org.asyncttwiz.protocol.GameCommand(org.asyncttwiz.protocol.CommandIds.uuidV5(AtwTable.NAMESPACE, "pool/$n"),
                org.asyncttwiz.protocol.GameScope(rt.state.gameId, playId = rt.state.id),
                org.asyncttwiz.protocol.GameAction.Play(org.asyncttwiz.protocol.PlayAction.Mtg(
                    org.asyncttwiz.protocol.MtgAction.SetManaPool(alice, before, after))),
                org.asyncttwiz.protocol.CommandIds.uuidV5(AtwTable.NAMESPACE, "pool"), "1")
        var state = rt.state
        val first = org.asyncttwiz.protocol.GameplayRules.change(state, alice, event(emptyList(), listOf(red, red), 0))
        assertEquals("accepted", first.outcome, first.reason)
        state = first.state!!
        val second = org.asyncttwiz.protocol.GameplayRules.change(state, alice, event(listOf(red, red), listOf(red, red, black), 1))
        assertEquals("accepted", second.outcome, second.reason)
        val undone = org.asyncttwiz.protocol.GameplayHistory.reverse(second.state!!, second.mutation!!)
        assertEquals(listOf(red, red), assertNotNull(undone, "undo of the second pool change").players.single { it.accountId == alice }.manaPool)
    }

    // finalreview-cast #6 (I-D41, STK-026, CR 117.3c): a land play whose landfall trigger goes on the Stack keeps the
    // trigger waiting for its own controller's "Let it resolve", while after a cast or an activation the host passes for
    // the caster (their own thing on top is no pause without Full control) and, Bob unable to respond, it resolves in the
    // same batch; CastFlow.sent() runs the passes only for a cast or an ability that went on the Stack (the same for a
    // special action whose trigger goes on the Stack)
    @Ignore("finalreview-cast #6: a land play's trigger waits for its controller to pass")
    @Test fun landfallTriggerResolvesLikeACastsAbility() {
        Cards.invent("Moss Pilgrim", "{1}{G}", "Creature — Elf", "Landfall — Whenever a land you control enters, you gain 2 life.", "1/1")
        table.put(alice, PlayZone.BATTLEFIELD, "Moss Pilgrim")
        val (forest) = table.put(alice, PlayZone.HAND, "Forest")
        val rt = RoundTrip(table.state(), table.definitions)
        val flow = CastFlow.start(rt.state, table.definitions, alice, log(rt),
            HandPlays.of(rt.state, table.definitions, alice, log(rt)).of(forest).first(), seeds())
        assertNull(flow.pending)
        rt.apply(flow.sent(), alice)
        assertTrue(rt.state.board.stackEffects.isEmpty(), "the trigger resolved: ${rt.state.board.stackEffects.map { it.item?.kind }}")
        assertEquals("22", rt.state.board.players.single { it.accountId == alice }.life)
    }

    // CR 606.3, 702.6a, 602.5d, TBL-115 (checked fine): a loyalty ability once a turn and equip as a sorcery, from the
    // menus' rows through the cast panel's flow; afterwards the other loyalty ability is greyed as a loyalty ability
    @Test fun loyaltyOncePerTurnAndEquipFromTheMenus() {
        Cards.invent("Kessa, Ember Scribe", "{2}{R}", "Legendary Planeswalker — Chandra", """
            [+1]: Kessa, Ember Scribe deals 1 damage to each opponent.
            [−2]: Draw a card.
        """, loyalty = "3")
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        val (kessa, bonevise, bear) = table.put(alice, PlayZone.BATTLEFIELD, "Kessa, Ember Scribe", "Bonevise", "Stout Bear")
        table.put(alice, PlayZone.LIBRARY, "Mountain")
        val rt = RoundTrip(table.state(), table.definitions)
        fun plays() = CardPlays.of(rt.state, table.definitions, alice, log(rt))
        val plus = plays().of(kessa).first { it.text.startsWith("[+1]") }
        rt.apply(CastFlow.start(rt.state, table.definitions, alice, log(rt), assertNotNull(plus.input), seeds()).sent(), alice)
        assertEquals("19", rt.state.board.players.single { it.accountId == bob }.life)
        assertEquals(CardPlay.Refusal.Loyalty, plays().of(kessa).first { it.text.startsWith("[−2]") }.refusal)
        val equip = plays().of(bonevise).first { it.text.startsWith("Equip") }
        var flow = CastFlow.start(rt.state, table.definitions, alice, log(rt), assertNotNull(equip.input, "${equip.refusal}"), seeds())
        while (true) flow = when (val open = flow.pending ?: break) {
            is Decision.Targets -> flow.answer(Answer.Targets(listOf(open.options.single { flow.ref(it) == MtgRef.Card(bear, 0) || (flow.ref(it) as? MtgRef.Card)?.cardId == bear })))
            is Decision.ManaPayment -> flow.answer(Answer.ManaPayment(open.suggestion))
            else -> flow.answer(flow.listedAnswers().first())
        }
        rt.apply(flow.sent(), alice)
        assertEquals(bear, (rt.state.board.cards.single { it.id == bonevise }.attachedTo as? MtgRef.Card)?.cardId)
    }

    // finalreview-cast #7 (CR 701.23, 701.24, I-D14, SYS-010): "each player may search their library … then shuffle" (the
    // cube's Seasoned Pathfinder dying) can't be sent: EngineBatch refuses the resolution with "the shuffle's order
    // differs from ATW's", so the game is stuck at that decision (found in the seeded games, seed 111)
    @Ignore("finalreview-cast #7: each player's search-and-shuffle can't be mapped")
    @Test fun eachPlayerSearchesAndShuffles() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        val (pathfinder) = table.put(alice, PlayZone.BATTLEFIELD, "Seasoned Pathfinder")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Forest", "Storm Spark", "Plains", "Denial")
        table.put(bob, PlayZone.LIBRARY, "Island", "Denial", "Swamp", "Storm Spark")
        val rt = RoundTrip(table.state(), table.definitions)
        rt.apply(cast(rt, alice, spark, { d -> d.options.single { (it as? TargetRef.Object)?.id?.let { o -> (o as? ObjectId.Card)?.card?.value } == pathfinder } }).sent(), alice)
        var guard = 10
        while (guard-- > 0) {
            val shared = flow(rt)
            val d = shared.decision
            when {
                d is Decision.ChooseObjects -> rt.apply(shared.answer(Answer.Objects(d.options.take(1))), d.player.value)
                d != null -> rt.apply(shared.answer(shared.listedAnswers().first()), d.player.value)
                shared.priority != null -> rt.apply(shared.pass(shared.priority!!), shared.priority!!)
                else -> break
            }
        }
        assertEquals(2, rt.state.board.cards.count { c -> rt.state.board.containers.single { c.id in it.cards }.zone == PlayZone.BATTLEFIELD &&
            table.names[c.id] in setOf("Forest", "Plains", "Island", "Swamp") }, "each player's searched land entered")
    }

    // CR 115.1, 701.6a, STK-023 (checked fine): a counterspell's target is the spell's Stack entry
    @Test fun counterspellTargetsTheSpellsEntry() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.BATTLEFIELD, "Island", "Island")
        val (spark) = table.put(alice, PlayZone.HAND, "Storm Spark")
        val (denial) = table.put(bob, PlayZone.HAND, "Denial")
        val rt = RoundTrip(table.state(), table.definitions)
        rt.apply(cast(rt, alice, spark, { player(bob) }).sent(), alice)
        assertEquals(MtgPriority(bob, 1), rt.state.board.priority)
        var refs = emptyList<MtgRef>()
        val f = cast(rt, bob, denial, { d -> refs = d.options.map { o -> CastFlow.start(rt.state, table.definitions, bob, log(rt),
            HandPlays.of(rt.state, table.definitions, bob, log(rt)).of(denial).first(), seeds()).ref(o) }; d.options.single() })
        assertTrue(refs.all { ref -> rt.state.board.stackEffects.any { it.item?.id == ref } }, "$refs vs ${rt.state.board.stackEffects.map { it.item?.id }}")
        rt.apply(f.sent(), bob)
        val shared = flow(rt)
        if (shared.priority != null) rt.apply(shared.pass(shared.priority!!), shared.priority!!)
        assertEquals("20", rt.state.board.players.single { it.accountId == bob }.life)
        assertEquals(PlayZone.GRAVEYARD, rt.state.board.containers.single { spark in it.cards }.zone, "countered")
    }

    // CR 707.10 (checked fine): copying a spell on the Stack (Flash of Mirroring) through the flows
    @Test fun copyingASpell() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain", "Mountain", "Mountain")
        val (spark, mirror) = table.put(alice, PlayZone.HAND, "Storm Spark", "Flash of Mirroring")
        table.fullControl += alice
        val rt = RoundTrip(table.state(), table.definitions)
        rt.apply(cast(rt, alice, spark, { player(bob) }).sent(), alice)
        val f = cast(rt, alice, mirror, { d -> d.options.first { it is TargetRef.Object } })
        rt.apply(f.sent(), alice)
        var guard = 10
        while (guard-- > 0) {
            val shared = flow(rt)
            val d = shared.decision
            when {
                d is Decision.May -> rt.apply(shared.answer(Answer.YesNo(false)), d.player.value)
                d != null -> rt.apply(shared.answer(shared.listedAnswers().first()), d.player.value)
                shared.priority != null -> rt.apply(shared.pass(shared.priority!!), shared.priority!!)
                else -> break
            }
        }
        assertEquals("14", rt.state.board.players.single { it.accountId == bob }.life)
    }
}
