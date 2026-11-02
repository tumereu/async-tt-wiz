package org.asyncttwiz.automatum

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.CommandIds
import org.asyncttwiz.protocol.EngineRecord
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameScope
import org.asyncttwiz.protocol.GameWire
import org.asyncttwiz.protocol.GameplayRules
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.PlayBatches
import org.asyncttwiz.protocol.PlayZone
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Adversarial review of phase I0 (integration spec §3 I0, §4.6): cube situations the I0-c round trips didn't cover,
 * each driven through [RoundTrip] (rebuilt state = engine's own result after every call; undo restores the board).
 * Tests marked `@Ignore("i0review #N …")` fail today; N is the finding's number in the review report.
 */
class I0ReviewTest {
    private val table = AtwTable("review")
    private val alice get() = table.alice
    private val bob get() = table.bob

    private fun game(): RoundTrip = RoundTrip(table.state(), table.definitions)
    private fun zone(rt: RoundTrip, card: String) = rt.state.board.containers.single { c -> c.id == rt.state.board.cards.single { it.id == card }.containerId }.zone

    /** Casts [name] the way [way] picks (default: the first listed way). */
    private fun cast(rt: RoundTrip, player: String, name: String, way: (Input.Cast) -> Boolean = { true }): EngineCall {
        val state = rt.engine()
        val input = rt.options(player).filterIsInstance<Input.Cast>().firstOrNull { rt.name(state.obj(it.card)) == name && way(it) }
            ?: error("$player can't cast $name that way: ${rt.options(player)}")
        return rt.perform(input, player)
    }

    /** The serialized size of the last plan's batch command, as the client would send it over the game socket. */
    private fun wireBytes(rt: RoundTrip): Int {
        val plan = rt.lastPlan!!
        return GameWire.json.encodeToString(org.asyncttwiz.protocol.GameAction.serializer(), plan.action).encodeToByteArray().size
    }

    /** Answers decisions (mana: the suggestion; others: the first listed answer) and passes priority until [done]. */
    private fun drive(rt: RoundTrip, guard: Int = 60, done: (RoundTrip) -> Boolean) {
        var left = guard
        while (!done(rt)) {
            check(left-- > 0) { "didn't get there" }
            when (val d = rt.decision) {
                null -> rt.engine().priority.holder?.let { rt.pass(it.value) } ?: run {
                    // Declaring no attackers or no blockers
                    val (player, input) = listOf(alice, bob).flatMap { p -> rt.options(p).map { p to it } }.first { (_, i) ->
                        i is Input.DeclareAttackers && i.attacks.isEmpty() || i is Input.DeclareBlockers && i.blocks.isEmpty() }
                    rt.perform(input, player)
                }
                is Decision.ManaPayment -> rt.perform(Input.Answer(d.id, Answer.ManaPayment(d.suggestion)), d.player.value)
                else -> rt.answer()
            }
        }
    }

    // ---- #1 Multi-card moves into a library (CR 701.20, 401.4): Brainstorm-like, scry, "rest on the bottom"

    // CR 401.4: "put two cards from your hand on top of your library in any order" moves two cards into the library at once
    @Test fun mindSquallPutsTwoCardsBackOnTop() {
        table.put(alice, PlayZone.BATTLEFIELD, "Island")
        table.put(alice, PlayZone.HAND, "Mind Squall", "Mountain", "Forest")
        table.put(alice, PlayZone.LIBRARY, "Swamp", "Plains", "Island", "Mountain", "Forest")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        cast(rt, alice, "Mind Squall")
        rt.settle()
        rt.resolveAll()
        rt.settle()
        rt.resolveAll()
        val library = rt.state.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == alice }
        assertEquals(4, library.cards.size)
    }

    // CR 701.22 (scry): both cards to the bottom
    @Test fun scryTwoBothToTheBottom() {
        table.put(alice, PlayZone.BATTLEFIELD, "Island")
        table.put(alice, PlayZone.HAND, "Foretell the Way")
        table.put(alice, PlayZone.LIBRARY, "Swamp", "Plains", "Island", "Mountain", "Forest")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        cast(rt, alice, "Foretell the Way")
        rt.settle()
        rt.resolveAll()
        // Put both on the bottom
        val d = rt.decision as Decision.ChooseObjects
        rt.perform(Input.Answer(d.id, Answer.Objects(d.options)), alice)
        drive(rt) { it.engine().stack.isEmpty() && it.decision == null }
    }

    // "Look at the top four … the rest on the bottom of your library in any order"
    @Test fun fleetingWhimPutsTheRestOnTheBottom() {
        table.put(alice, PlayZone.BATTLEFIELD, "Island", "Island")
        table.put(alice, PlayZone.HAND, "Fleeting Whim")
        table.put(alice, PlayZone.LIBRARY, "Swamp", "Plains", "Island", "Mountain", "Forest", "Storm Spark")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        cast(rt, alice, "Fleeting Whim")
        rt.settle()
        rt.resolveAll()
        rt.settle()
        rt.resolveAll()
        assertEquals(6, rt.state.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == alice }.cards.size + 1)
    }

    // "Each player shuffles their hand and graveyard into their library"
    @Test fun hourspinnerShufflesHandsAndGraveyardsIn() {
        table.put(alice, PlayZone.BATTLEFIELD, "Island", "Island", "Island")
        table.put(alice, PlayZone.HAND, "Hourspinner", "Mountain")
        table.put(alice, PlayZone.GRAVEYARD, "Storm Spark", "Forest")
        table.put(alice, PlayZone.LIBRARY, "Swamp", "Plains", "Island", "Mountain", "Forest", "Island", "Island", "Island", "Island")
        table.put(bob, PlayZone.HAND, "Island")
        table.put(bob, PlayZone.GRAVEYARD, "Forest")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        val rt = game()
        cast(rt, alice, "Hourspinner")
        rt.settle()
        rt.resolveAll()
        rt.settle()
        rt.resolveAll()
    }

    // ---- #12 Extra turns: Time Stride ("Take an extra turn after this one", CR 500.7)
    // TRN-052 (I-D12)
    @Test fun timeStrideTakesAnExtraTurn() {
        table.put(alice, PlayZone.BATTLEFIELD, "Island", "Island")
        table.put(alice, PlayZone.HAND, "Time Stride")
        table.put(alice, PlayZone.LIBRARY, "Swamp", "Plains", "Island", "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island", "Island", "Island")
        val rt = game()
        cast(rt, alice, "Time Stride")
        rt.settle()
        rt.resolveAll()
        // Pass through the rest of the turn into the extra turn
        var guard = 40
        while (guard-- > 0 && rt.engine().turn.number == 1) {
            val d = rt.decision
            if (d != null) rt.answer() else rt.pass(rt.engine().priority.holder!!.value)
        }
        assertEquals(alice, rt.state.board.turn.accountId, "the extra turn is Alice's")
    }

    // ---- #13 Batch limits (GameWire: 1..100 members; game socket 1 MiB per message)

    // CR 702.96 (overload) + CR 704.5g: a board wipe of a busy cube board in one engine call
    @Test fun overloadedBoardWipeFitsOneBatch() {
        table.put(alice, PlayZone.BATTLEFIELD, "Swamp", "Swamp", "Plains", "Plains")
        table.put(alice, PlayZone.HAND, "Curse Word")
        table.put(alice, PlayZone.BATTLEFIELD, *Array(10) { "Tinkered Mite" })
        table.put(bob, PlayZone.BATTLEFIELD, *Array(10) { "Tinkered Mite" })
        table.put(bob, PlayZone.BATTLEFIELD, "Festering Gremlin", "Festering Gremlin", "Festering Gremlin")
        table.put(alice, PlayZone.LIBRARY, "Swamp", "Swamp")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        cast(rt, alice, "Curse Word") { it.way.alternative != null }
        rt.settle()
        var biggest = 0
        var biggestWire = 0
        var guard = 10
        while (guard-- > 0 && rt.engine().stack.isNotEmpty() || rt.decision != null) {
            if (rt.decision != null) rt.answer() else rt.pass(rt.engine().priority.holder!!.value)
            biggest = maxOf(biggest, rt.lastPlan?.actions?.size ?: 0)
            biggestWire = maxOf(biggestWire, wireBytes(rt))
        }
        println("board wipe: largest batch $biggest members, $biggestWire bytes on the wire")
        assertTrue(rt.state.board.cards.none { it.definition.isNotEmpty() && table.names[it.id] == "Tinkered Mite" && zone(rt, it.id) == PlayZone.BATTLEFIELD })
        assertTrue(biggest <= 100, "a batch has at most 100 members (GameWire), this one $biggest")
        assertTrue(biggestWire <= 1024 * 1024, "the game socket takes 1 MiB per message, this batch $biggestWire bytes")
    }

    /** Casts [name] by Alice with X = [x], paying with the suggestion. */
    private fun castX(rt: RoundTrip, name: String, x: Int) {
        cast(rt, alice, name)
        while (rt.decision != null) {
            when (val d = rt.decision!!) {
                is Decision.ChooseNumber, is Decision.Announce -> rt.perform(Input.Answer(d.id, Answer.Number(x)), alice)
                is Decision.ManaPayment -> rt.perform(Input.Answer(d.id, Answer.ManaPayment(d.suggestion)), alice)
                else -> rt.answer()
            }
        }
    }

    /**
     * Hangar Strider with X = [x] dies to Curse Word: its trigger makes [x] Thopter tokens in one resolution. Returns the
     * largest batch (members) and its wire size.
     */
    private fun thopters(rt: RoundTrip, x: Int): Pair<Int, Int> {
        castX(rt, "Hangar Strider", x)
        drive(rt) { stackEmpty(it) }
        cast(rt, alice, "Curse Word") { it.way.alternative == null }
        rt.target(TargetRef.Object(rt.obj("Hangar Strider", Zone.Battlefield)))
        rt.settle()
        var biggest = 0
        var biggestWire = 0
        drive(rt) {
            biggest = maxOf(biggest, it.lastPlan?.actions?.size ?: 0); biggestWire = maxOf(biggestWire, wireBytes(it))
            stackEmpty(it)
        }
        return biggest to biggestWire
    }

    // A big token maker (Hangar Strider's death, a storm turn): every token is a CreateToken, a move and its ledger
    @Test fun manyTokensInOneResolution() {
        table.put(alice, PlayZone.BATTLEFIELD, *Array(30) { "Swamp" })
        table.put(alice, PlayZone.HAND, "Hangar Strider", "Curse Word")
        table.put(alice, PlayZone.LIBRARY, "Swamp", "Swamp")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        val (biggest, wire) = thopters(rt, 10)
        println("ten tokens: largest batch $biggest members, $wire bytes")
        assertEquals(10, rt.state.board.cards.count { it.token })
        assertTrue(biggest <= 100, "a batch has at most 100 members (GameWire), this one $biggest")
    }

    // CR 704.5d: tokens that left the battlefield cease to exist; a wipe of a token board (storm turn, Ignia, Servos)
    @Test fun wipeOfATokenBoardFitsOneBatch() {
        table.put(alice, PlayZone.BATTLEFIELD, *Array(40) { "Swamp" }, *Array(10) { "Plains" })
        table.put(alice, PlayZone.HAND, "Hangar Strider", "Curse Word", "Curse Word")
        table.put(alice, PlayZone.LIBRARY, "Swamp", "Swamp")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        thopters(rt, 12)
        assertEquals(12, rt.state.board.cards.count { it.token })
        cast(rt, alice, "Curse Word") { it.way.alternative != null }
        rt.settle()
        var biggest = 0
        drive(rt) { biggest = maxOf(biggest, it.lastPlan?.actions?.size ?: 0); stackEmpty(it) }
        println("token wipe of 12: largest batch $biggest members")
        assertEquals(0, rt.state.board.cards.count { it.token })
        assertTrue(biggest <= 100, "a batch has at most 100 members (GameWire), this one $biggest")
    }

    /** Activates [name]'s ability the [pick] chooses. */
    private fun activate(rt: RoundTrip, player: String, name: String, pick: (Input.Activate) -> Boolean = { true }): EngineCall {
        val state = rt.engine()
        val input = rt.options(player).filterIsInstance<Input.Activate>().filter { rt.name(state.obj(it.source)) == name }.firstOrNull(pick)
            ?: error("$player can't activate $name: ${rt.options(player)}")
        return rt.perform(input, player)
    }

    private fun stackEmpty(rt: RoundTrip) = rt.engine().stack.isEmpty() && rt.decision == null

    // ---- Cube structures I0-c didn't drive (pass today)

    // CR 606 (loyalty abilities), 603.7 (a delayed trigger "at the beginning of the next end step" crossing steps)
    @Test fun planeswalkerMakesHastyTokensSacrificedAtTheEndStep() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain", "Mountain", "Mountain")
        table.put(alice, PlayZone.HAND, "Ignia, Acolyte of Embers")
        table.put(alice, PlayZone.LIBRARY, "Mountain", "Mountain", "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island", "Island")
        val rt = game()
        cast(rt, alice, "Ignia, Acolyte of Embers"); rt.settle()
        drive(rt) { stackEmpty(it) }
        val ignia = rt.state.board.cards.single { table.names[it.id] == "Ignia, Acolyte of Embers" }
        assertEquals(listOf("loyalty"), ignia.typedCounters.map { it.kind })
        activate(rt, alice, "Ignia, Acolyte of Embers") { it.ability.toString().contains("1") }
        rt.settle()
        drive(rt) { stackEmpty(it) }
        // To the end step: the delayed trigger sacrifices the tokens
        drive(rt, 80) { it.engine().turn.number > 1 }
        assertEquals(0, rt.state.board.cards.count { it.token })
    }

    // CR 714 (Sagas), 712 (transforming back face)
    @Test fun sagaChaptersAndTransform() {
        table.put(alice, PlayZone.BATTLEFIELD, "Plains", "Plains", "Tinkered Mite")
        table.put(alice, PlayZone.HAND, "Haruka's Age of Honor")
        table.put(alice, PlayZone.LIBRARY, *Array(6) { "Plains" })
        table.put(bob, PlayZone.LIBRARY, *Array(6) { "Island" })
        val rt = game()
        cast(rt, alice, "Haruka's Age of Honor"); rt.settle()
        drive(rt) { stackEmpty(it) }
        // Two more of Alice's turns: chapters II and III
        drive(rt, 400) { it.engine().turn.number >= 5 && it.engine().turn.step == TurnStep.PrecombatMain && stackEmpty(it) }
        val saga = rt.state.board.cards.single { table.names[it.id] == "Haruka's Age of Honor" }
        assertEquals(PlayZone.BATTLEFIELD, zone(rt, saga.id))
    }

    // CR 301.5, 702.6 (equip), 704.5n (unattached Equipment stays), "whenever equipped creature dies"
    @Test fun equipmentEquipsAndItsCreatureDies() {
        table.put(alice, PlayZone.BATTLEFIELD, "Swamp", "Swamp", "Swamp", "Swamp", "Festering Gremlin")
        table.put(alice, PlayZone.HAND, "Bonevise", "Curse Word")
        table.put(alice, PlayZone.LIBRARY, *Array(4) { "Swamp" })
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        cast(rt, alice, "Bonevise"); rt.settle(); drive(rt) { stackEmpty(it) }
        activate(rt, alice, "Bonevise"); rt.settle(); drive(rt) { stackEmpty(it) }
        val bonevise = rt.state.board.cards.single { table.names[it.id] == "Bonevise" }
        assertTrue(bonevise.attachedTo != null, "equipped: ${rt.engine().objects.values.single { it.card.value == bonevise.id }.attachedTo} " +
            "${rt.lastPlan?.actions?.map { it::class.simpleName }} ${rt.engine().stack} ${rt.options(alice).filterIsInstance<Input.Activate>()}")
        cast(rt, alice, "Curse Word") { it.way.alternative == null }; rt.settle(); drive(rt) { stackEmpty(it) }
        assertEquals(null, rt.state.board.cards.single { it.id == bonevise.id }.attachedTo)
    }

    // CR 702.92 (living weapon): a token is created and the Equipment attached to it in one resolution
    @Test fun livingWeapon() {
        table.put(alice, PlayZone.BATTLEFIELD, "Plains", "Plains", "Plains")
        table.put(alice, PlayZone.HAND, "Thornpod Husk")
        table.put(alice, PlayZone.LIBRARY, "Plains", "Plains")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        cast(rt, alice, "Thornpod Husk"); rt.settle(); drive(rt) { stackEmpty(it) }
        val husk = rt.state.board.cards.single { table.names[it.id] == "Thornpod Husk" }
        assertTrue(husk.attachedTo != null || rt.state.board.cards.none { it.token }, "attached to the germ, or the 0/0 germ died")
    }

    // CR 715 (Adventurer cards): cast as its Adventure, exiled on an adventure, later cast as the creature from exile
    @Test fun adventureThenCreatureFromExile() {
        table.put(alice, PlayZone.BATTLEFIELD, "Island", "Island", "Island", "Island", "Island")
        table.put(alice, PlayZone.HAND, "Brash Pilferer")
        table.put(alice, PlayZone.LIBRARY, "Island", "Island")
        table.put(bob, PlayZone.BATTLEFIELD, "Tinkered Mite")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        cast(rt, alice, "Brash Pilferer") { it.way.face == 1 }
        rt.settle(); drive(rt) { stackEmpty(it) }
        val card = rt.state.board.cards.single { table.names[it.id] == "Brash Pilferer" }
        assertEquals(PlayZone.EXILE, zone(rt, card.id))
        cast(rt, alice, "Brash Pilferer"); rt.settle(); drive(rt) { stackEmpty(it) }
        assertEquals(PlayZone.BATTLEFIELD, zone(rt, card.id))
    }

    // CR 702.37 (morph), 708.4: cast face down for {3}; the spell on the Stack is a face-down 2/2
    @Ignore("i0review #5: a face-down spell is face up in ATW (entry and card)")
    @Test fun morphCastFaceDown() {
        table.put(alice, PlayZone.BATTLEFIELD, "Forest", "Forest", "Forest", "Forest")
        val card = table.put(alice, PlayZone.HAND, "Barkwood Pathfinders").single()
        table.put(alice, PlayZone.LIBRARY, "Forest", "Forest")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        cast(rt, alice, "Barkwood Pathfinders") { it.way.alternative != null }
        rt.settle()
        assertTrue(rt.engine().objects.values.single { it.card.value == card }.faceDown, "the spell is face down on the Stack")
        drive(rt) { stackEmpty(it) }
        assertTrue(rt.state.board.cards.single { it.id == card }.isFaceDown)
    }

    // CR 702.40 (storm): copies of a spell go on the Stack above it
    @Test fun stormCopies() {
        table.put(alice, PlayZone.BATTLEFIELD, "Forest", "Forest", "Mountain", "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark", "Chitterstorm")
        table.put(alice, PlayZone.LIBRARY, "Forest", "Forest")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        cast(rt, alice, "Storm Spark"); rt.target(TargetRef.Player(PlayerId(bob))); rt.settle(); drive(rt) { stackEmpty(it) }
        cast(rt, alice, "Chitterstorm"); rt.settle(); drive(rt) { stackEmpty(it) }
        assertEquals(2, rt.state.board.cards.count { it.token })
    }

    // CR 510.4 (first-strike damage step), 702.7
    @Test fun firstStrikeCombat() {
        table.put(alice, PlayZone.BATTLEFIELD, "Plains", "Plains", "Plains")
        table.put(alice, PlayZone.HAND, "Golem Grafter")
        table.put(alice, PlayZone.LIBRARY, *Array(6) { "Plains" })
        table.put(bob, PlayZone.BATTLEFIELD, "Festering Gremlin", "Tinkered Mite")
        table.put(bob, PlayZone.LIBRARY, *Array(6) { "Island" })
        val rt = game()
        cast(rt, alice, "Golem Grafter"); rt.settle(); drive(rt) { stackEmpty(it) }
        drive(rt, 400) { it.engine().turn.number == 3 && it.engine().turn.step == TurnStep.DeclareAttackers && it.decision == null }
        val s = rt.engine()
        val golem = s.battlefield.single { s.objOrThrow(it).token }
        rt.perform(Input.DeclareAttackers(PlayerId(alice), listOf(Attack(golem, TargetRef.Player(PlayerId(bob))))), alice)
        drive(rt) { it.engine().turn.step == TurnStep.DeclareBlockers && it.decision == null }
        val b = rt.engine()
        val blockers = b.battlefield.filter { b.objOrThrow(it).controller?.value == bob }
        rt.perform(Input.DeclareBlockers(PlayerId(bob), blockers.map { Block(it, golem) }), bob)
        drive(rt) { it.engine().turn.step == TurnStep.EndOfCombat }
    }

    // CR 603.7: a delayed trigger "at the beginning of your next upkeep" crosses the turn boundary
    @Test fun delayedTriggerCrossesTurns() {
        table.put(alice, PlayZone.BATTLEFIELD, "Plains", "Plains", "Plains", "Plains")
        table.put(alice, PlayZone.HAND, "Muster the Forebears")
        table.put(alice, PlayZone.GRAVEYARD, "Tinkered Mite", "Festering Gremlin")
        table.put(alice, PlayZone.LIBRARY, *Array(6) { "Plains" })
        table.put(bob, PlayZone.LIBRARY, *Array(6) { "Island" })
        val rt = game()
        cast(rt, alice, "Muster the Forebears")
        while (rt.decision != null) {
            val d = rt.decision!!
            if (d is Decision.ChooseNumber) rt.perform(Input.Answer(d.id, Answer.Number(2)), alice)
            else if (d is Decision.ManaPayment) rt.perform(Input.Answer(d.id, Answer.ManaPayment(d.suggestion)), alice)
            else rt.answer()
        }
        drive(rt) { stackEmpty(it) }
        assertTrue(rt.state.board.engineRecords.any { it.id.startsWith("delayed:") })
        drive(rt, 400) { it.engine().turn.number == 3 && it.engine().turn.step == TurnStep.Draw }
        assertTrue(rt.state.board.engineRecords.none { it.id.startsWith("delayed:") })
    }

    // CR 106.4a: "Until end of turn, you don't lose this mana as steps and phases end"
    @Test fun manaThatStaysAcrossSteps() {
        table.put(alice, PlayZone.BATTLEFIELD, "Skalda, God of Sagas", "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, *Array(4) { "Mountain" })
        table.put(bob, PlayZone.LIBRARY, *Array(4) { "Island" })
        val rt = game()
        cast(rt, alice, "Storm Spark"); rt.target(TargetRef.Player(PlayerId(bob))); rt.settle()
        drive(rt) { stackEmpty(it) }
        assertEquals(listOf("R"), rt.state.board.players.single { it.accountId == alice }.manaPool.map { it.color })
        drive(rt) { it.engine().turn.step == TurnStep.BeginningOfCombat }
        assertEquals(listOf("R"), rt.state.board.players.single { it.accountId == alice }.manaPool.map { it.color })
    }

    // CR 104.3b, 704.5a: a player at 0 life loses
    @Test fun playerLoses() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val state = table.state()
        val rt = RoundTrip(state.copy(board = state.board.copy(players = state.board.players.map { if (it.accountId == bob) it.copy(life = "3") else it })), table.definitions)
        cast(rt, alice, "Storm Spark"); rt.target(TargetRef.Player(PlayerId(bob))); rt.settle()
        drive(rt) { stackEmpty(it) || arcanum.engine.Engine.result(it.engine()) != null }
        assertEquals("0", rt.state.board.players.single { it.accountId == bob }.life)
    }

    // CR 502.3, 514: the turn passes with a big tapped board: cleanup, untap of every permanent, upkeep and draw
    @Test fun turnChangeWithABigBoardFitsOneBatch() {
        table.put(alice, PlayZone.BATTLEFIELD, *Array(30) { "Mountain" }, tapped = true)
        table.put(alice, PlayZone.BATTLEFIELD, *Array(15) { "Tinkered Mite" }, tapped = true)
        table.put(bob, PlayZone.BATTLEFIELD, *Array(30) { "Island" }, tapped = true)
        table.put(bob, PlayZone.BATTLEFIELD, *Array(15) { "Tinkered Mite" }, tapped = true)
        table.put(alice, PlayZone.LIBRARY, *Array(4) { "Mountain" })
        table.put(bob, PlayZone.LIBRARY, *Array(4) { "Island" })
        val rt = game()
        var biggest = 0
        drive(rt, 200) { biggest = maxOf(biggest, it.lastPlan?.actions?.size ?: 0); it.engine().turn.number == 2 && it.engine().turn.step == TurnStep.PrecombatMain }
        println("turn change: largest batch $biggest members")
        assertTrue(biggest <= 100, "a batch has at most 100 members (GameWire), this one $biggest")
    }

    // A wipe of a dozen real-sized cube creatures: the destroy event's log entry (one tag) carries every moved object twice
    // with its whole definition. MtgTag.data is documented as ≤ 256 KiB (Automatum.MAX_DATA_BYTES) but Automatum.data
    // also applies DeckValidation.text, which caps any text at 64 KiB: the server refuses the batch as invalidInput.
    @Test fun wipeOfComplexCreaturesFitsTheTagAndSocketLimits() {
        fun size(name: String) = EngineCodec.encode(arcanum.api.CardDefinition.serializer(), Cards.definition(name)).length
        val creatures = Cards.cubeNames.filter { name ->
            val d = runCatching { Cards.definition(name) }.getOrNull() ?: return@filter false
            d.layout == arcanum.api.Layout.Normal && d.faces.single().types == listOf(arcanum.api.CardType.Creature) &&
                (d.faces.single().toughness as? arcanum.api.PrintedNumber.Fixed)?.value?.let { it > 0 } == true &&
                d.faces.single().supertypes.isEmpty()
        }.sortedByDescending(::size).take(12)
        println("definition sizes: ${creatures.map(::size)}")
        table.put(alice, PlayZone.BATTLEFIELD, "Plains", "Plains", "Plains", "Plains")
        table.put(alice, PlayZone.HAND, "Curse Word")
        table.put(alice, PlayZone.BATTLEFIELD, *creatures.take(6).toTypedArray())
        table.put(bob, PlayZone.BATTLEFIELD, *creatures.drop(6).toTypedArray())
        table.put(alice, PlayZone.LIBRARY, *Array(10) { "Plains" })
        table.put(bob, PlayZone.LIBRARY, *Array(10) { "Island" })
        val rt = game()
        cast(rt, alice, "Curse Word") { it.way.alternative != null }
        rt.settle()
        val failure = runCatching { drive(rt, 30) { stackEmpty(it) } }.exceptionOrNull()
        val plan = rt.lastPlan!!
        val largestTag = plan.tags.maxOf { it?.data?.encodeToByteArray()?.size ?: 0 }
        println("complex wipe: largest tag $largestTag bytes (of any batch: ${rt.largestTag}, largest batch ${rt.largestBatch} members), batch ${wireBytes(rt)} bytes on the wire; ${failure?.message?.takeLast(300)}")
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "review/wipe"), GameScope(rt.state.gameId, playId = rt.state.id),
            plan.action, CommandIds.uuidV5(AtwTable.NAMESPACE, "stream"), "1")
        assertTrue(GameWire.valid(command), "the server refuses the wipe's batch (largest tag $largestTag bytes)")
        assertEquals(null, failure?.message)
    }

    // ---- #4, #9, #6: batches on another board; malformed engine data; hidden cards in tags

    // §4.5: "a batch built on another state fails whole (SYS-010)": here a manual life edit lands between the engine's read
    // and the batch; nothing in the batch names the life it read (ordinary actions apply to the current state, SYS-011)
    // SYS-057
    @Test fun batchPlannedOnAnotherBoardIsRefusedWhole() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val rt = game()
        cast(rt, alice, "Storm Spark"); rt.target(TargetRef.Player(PlayerId(bob))); rt.settle()
        rt.pass(alice)
        // Bob's pass resolves the spark: planned on the board as Bob's client has it
        val call = EngineCall(rt.state, rt.definitions, log = EngineLog.from(rt.tags, rt.state, rt.definitions), seeds = rt::seed)
        call.perform(Input.PassPriority(PlayerId(bob)))
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "review/stale"), GameScope(rt.state.gameId, playId = rt.state.id),
            GameAction.PlayBatch(listOf(PlayAction.PassAttention)), CommandIds.uuidV5(AtwTable.NAMESPACE, "stream"), "1")
        val plan = EngineBatch.plan(call, bob, command)
        // Meanwhile the server took Alice's manual life edit (SYS-036: anyone edits anything)
        val manual = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "review/manual"), GameScope(rt.state.gameId, playId = rt.state.id),
            GameAction.Play(PlayAction.SetLife(bob, "15")), CommandIds.uuidV5(AtwTable.NAMESPACE, "stream"), "1")
        var server = GameplayRules.change(rt.state, alice, manual).state!!
        val batch = command.copy(action = plan.action)
        val outcomes = plan.actions.mapIndexed { i, action ->
            val change = GameplayRules.change(server, bob, PlayBatches.member(batch, i, action))
            change.state?.let { server = it }
            change.reason
        }
        assertTrue(outcomes.any { it != null }, "the batch was applied whole on a board it wasn't planned for: " +
            "Bob's life ${server.board.players.single { it.accountId == bob }.life}, the engine said 17")
    }

    // P8: the server checks only the JSON form of engine data; a record the engine can't read must not crash the mapping
    @Test fun unreadableEngineRecordIsUnsupportedNotACrash() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.LIBRARY, "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val state = table.state()
        for (record in listOf(EngineRecord("effect:x", EngineRecordKind.Effect, 1, "{}"),
            EngineRecord(org.asyncttwiz.protocol.Automatum.CONTINUATION_ID, EngineRecordKind.Continuation, data = "{}"),
            EngineRecord("turn", EngineRecordKind.Turn, data = "[]"))) {
            val broken = state.copy(board = state.board.copy(engineRecords = listOf(record)))
            val problem = runCatching { EngineState.from(broken, table.definitions) }.exceptionOrNull()
            assertTrue(problem == null || problem is Unsupported, "${record.id}: ${problem?.let { it::class.simpleName }}")
            assertTrue(EngineState.unsupported(broken, table.definitions).isNotEmpty() || problem == null,
                "${record.id}: unsupported() doesn't say what's wrong")
        }
    }

    // D60, D33 ("a snapshot of what everyone could see"), I-D17 (face-down cards stay unknown): a history tag is shared
    // with both players; it must not name a card only one of them may see. Alice casts a morph face down in her own view
    // (a private cast, I-D11): the cast's tag carries the spell object with its definition.
    @Test fun tagsDoNotNameHiddenCards() {
        table.put(alice, PlayZone.BATTLEFIELD, "Forest", "Forest", "Forest")
        table.put(alice, PlayZone.HAND, "Barkwood Pathfinders")
        table.put(alice, PlayZone.LIBRARY, "Forest", "Forest")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        val call = EngineCall(rt.state, rt.definitions, viewer = alice, log = EngineLog.from(rt.tags, rt.state, rt.definitions), seeds = rt::seed)
        call.perform(arcanum.engine.Engine.options(call.working, PlayerId(alice)).filterIsInstance<Input.Cast>().single { it.way.alternative != null })
        while (call.pending != null) {
            val d = call.pending!!.decision
            call.perform(if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                else arcanum.engine.Engine.options(call.working, d.player).filterIsInstance<Input.Answer>().first())
        }
        val command = GameCommand(CommandIds.uuidV5(AtwTable.NAMESPACE, "review/morph"), GameScope(rt.state.gameId, playId = rt.state.id),
            GameAction.PlayBatch(listOf(PlayAction.PassAttention)), CommandIds.uuidV5(AtwTable.NAMESPACE, "stream"), "1")
        val plan = EngineBatch.plan(call, alice, command)
        val leaking = plan.tags.filterNotNull().filter { "Barkwood Pathfinders" in it.data }
        assertTrue(leaking.isEmpty(), "${leaking.size} shared history tags name Alice's face-down spell")
    }

    // CR 114 (emblems live in the command zone): a planeswalker's ultimate, "You get an emblem with …"
    @Ignore("i0review #12: emblems (command zone) are unmappable")
    @Test fun planeswalkerUltimateMakesAnEmblem() {
        table.put(alice, PlayZone.BATTLEFIELD, "Island")
        val renji = table.put(alice, PlayZone.BATTLEFIELD, "Renji Kagehara").single()
        table.put(alice, PlayZone.LIBRARY, *Array(4) { "Island" })
        table.put(bob, PlayZone.LIBRARY, *Array(4) { "Island" })
        val state = table.state()
        val rt = RoundTrip(state.copy(board = state.board.copy(cards = state.board.cards.map {
            if (it.id == renji) it.copy(typedCounters = listOf(org.asyncttwiz.protocol.TypedCounter("loyalty", 8, 0))) else it })),
            table.definitions)
        val ultimate = rt.options(alice).filterIsInstance<Input.Activate>().filter { (it.source as arcanum.engine.state.ObjectId.Card).card.value == renji }
            .maxBy { it.ability.toString() }
        rt.perform(ultimate, alice)
        rt.settle()
        drive(rt) { stackEmpty(it) }
        assertEquals(1, rt.engine().command.size, "Alice has the emblem")
    }

    // I-D2 ("everything meaningful about a card travels with the game"): a token the engine makes shows its rules in
    // ATW's own card data, so players see what it does (and play it by hand with Automatum off), and a manual copy of it
    // (CloneToken, TOK-*) isn't a vanilla token: the engine's definition is only in the token's ledger, which a copy
    // doesn't get (MtgFacts gives new objects an empty ledger)
    @Test fun engineTokensCarryTheirRulesInAtwsCardData() {
        table.put(alice, PlayZone.BATTLEFIELD, "Plains", "Plains", "Plains")
        table.put(alice, PlayZone.HAND, "Lingering Shades")
        table.put(alice, PlayZone.LIBRARY, "Plains", "Plains")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island")
        val rt = game()
        cast(rt, alice, "Lingering Shades"); rt.settle(); drive(rt) { stackEmpty(it) }
        val token = rt.state.board.cards.first { it.token }
        val data = rt.state.definitions.single { it.digest == token.definition }.definition
        assertTrue(data.faces.any { "Flying" in it.rulesText }, "the Spirit token's card data: ${data.faces.map { it.typeLine to it.rulesText }}")
    }
}
