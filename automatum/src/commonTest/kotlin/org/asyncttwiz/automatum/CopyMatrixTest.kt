@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.automatum

import arcanum.api.Ability
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import arcanum.engine.state.setup
import org.asyncttwiz.protocol.DefinitionFace
import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.StackEffect
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.TableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * MDLPM7 (owner playtest 2026-11-19): every kind of copy the engine makes, as the table draws it ([EngineProjection] through
 * [ShownObjects]): its name, kind, text, picture (by the identity its copiable values carry, CR 707.2, 707.10), the card it points
 * at, and whether it is a copy (D7: "Copy of …" and the copy marker; permanents are never marked). The cards are invented; each has
 * card data with a picture of its own ([EngineTable.pictureOf]). No picture is ever missing: nothing is reported.
 */
class CopyMatrixTest {
    private val spark = Cards.invent("Matrix Spark", "{0}", "Instant", "Matrix Spark deals 3 damage to any target.")
    private val echo = Cards.invent("Matrix Echo", "{0}", "Instant", "Copy target instant or sorcery spell. You may choose new targets for the copy.")
    private val geode = Cards.invent("Matrix Geode", "{0}", "Artifact", "{1}, {T}: You gain 1 life.")
    private val mimicry = Cards.invent("Matrix Mimicry", "{0}", "Instant", "Copy target artifact spell. (A copy of a permanent spell becomes a token.)")
    private val herald = Cards.invent("Matrix Herald", "{0}", "Creature — Human", "When this creature enters, you gain 2 life.", "1/1")
    private val kindler = Cards.invent("Matrix Kindler", "{0}", "Artifact",
        "{0}: Copy target triggered ability you control. You may choose new targets for the copy.")
    private val lens = Cards.invent("Matrix Lens", "{0}", "Artifact",
        "{0}: Copy target activated or triggered ability you control. You may choose new targets for the copy.")
    private val pinger = Cards.invent("Matrix Pinger", "{0}", "Artifact", "{T}: This artifact deals 1 damage to each opponent.")
    private val cub = Cards.invent("Matrix Cub", "{1}{R}", "Creature — Elemental", "Trample", "2/2")
    private val mold = Cards.invent("Matrix Mold", "{0}", "Artifact", "{0}: Create a token that's a copy of target creature you control.")
    private val clone = Cards.invent("Matrix Clone", "{0}", "Creature — Shapeshifter",
        "You may have this creature enter as a copy of any creature on the battlefield.", "0/0")
    private val seraph = Cards.inventFaces("Prepare",
        face("Matrix Seraph", "{3}{W}", "Creature — Angel", "{0}: This creature becomes prepared.", "2/4", null, null),
        face("Matrix Mending", "{G}", "Sorcery", "You gain 1 life.", null, null, null),
    )

    private val table = EngineTable(pictured = true)
    private val alice get() = table.alice
    private val faults = mutableListOf<org.asyncttwiz.protocol.Fault>()

    private fun <T> noFaults(block: () -> T): T {
        val sink = Faults.sink
        Faults.sink = { faults += it }
        try { return block() } finally { Faults.sink = sink }
    }

    // ---- Driving the engine

    private fun GameState.perform(input: Input): GameState {
        Engine.perform(this, input, org.asyncttwiz.protocol.game.EnginePreferences.of(this)).changes.forEach(::apply)
        return this
    }

    /** Answers what opens: targets by [target], a mana payment by its suggestion, "you may" by [may]; then players pass until
     * [done]. */
    private fun GameState.settle(target: (Decision.Targets) -> TargetRef? = { null }, may: Boolean = false, done: (GameState) -> Boolean): GameState {
        repeat(60) {
            if (done(this)) return this
            val decision = pending?.decision
            when {
                decision is Decision.Targets -> perform(Input.Answer(decision.id, Answer.Targets(listOfNotNull(target(decision) ?: decision.options.first()))))
                decision is Decision.ManaPayment -> perform(Input.Answer(decision.id, Answer.ManaPayment(decision.suggestion)))
                decision is Decision.May -> perform(Input.Answer(decision.id, Answer.YesNo(may)))
                decision is Decision.ChooseObjects -> perform(Input.Answer(decision.id, Answer.Objects(decision.options.take(decision.min.coerceAtLeast(1)))))
                decision != null -> error("unexpected $decision")
                else -> perform(Input.PassPriority(assertNotNull(priority.holder, "nobody holds priority")))
            }
        }
        error("never settled")
    }

    private fun GameState.cast(card: String) = perform(Input.Cast(PlayerId(alice), table.obj(card)))

    private fun GameState.activate(card: String) = perform(Input.Activate(PlayerId(alice), table.obj(card),
        assertNotNull(Engine.view(this)[table.obj(card)]?.characteristics?.abilities?.firstOrNull { it.ability is Ability.Activated }).origin))

    private fun GameState.copyOnTop(): Boolean = stack.lastOrNull()?.let { stackItems[it]?.copyOf } != null

    // ---- What the table draws

    private class Drawn(val board: EngineProjection.Board) {
        fun entry(id: ObjectId): StackEffect = board.board.stackEffects.single { it.id == "entry:$id" }
        fun face(cardId: String): DefinitionFace {
            val card = board.board.cards.single { it.id == cardId }
            val faces = board.definitions.single { it.digest == card.definition }.definition.faces
            return faces.firstOrNull { it.faceId == card.faceId } ?: faces.first()
        }
    }

    private fun draw(state: GameState, art: TokenArt = TokenArt.None) = Drawn(EngineProjection.project(table.base(),
        GameModel(state.deepCopy(), TableState()), alice, "node", tokenArt = art))

    private fun named(state: GameState, id: ObjectId) = ShownNames.of(state, state.stackItems.getValue(id)) { o ->
        (o as? ObjectId.Card)?.let { state.obj(it) ?: state.objects.values.firstOrNull { x -> x.card == it.card } }?.definition?.faces?.first()?.name }

    // ---- The matrix

    // STK-212, CR 707.10: a copy of a triggered ability points at the original's source (113.7) and is marked a copy; named for its kind
    @Test fun aCopiedTriggerPointsAtItsSourceAndIsACopy() = noFaults {
        val (k) = table.put(alice, Zone.Battlefield, kindler)
        val (h) = table.put(alice, Zone.Hand, herald)
        var state = table.state().cast(h).settle { s -> s.stack.lastOrNull()?.let { s.stackItems[it]?.kind } == StackItemKind.Triggered }
        val trigger = state.stack.last()
        state = state.activate(k).settle(target = { TargetRef.Object(trigger) }) { it.copyOnTop() }
        val copy = state.stack.last()
        val drawn = draw(state)
        val entry = drawn.entry(copy)
        assertTrue(entry.isCopy, "D7: the copy is marked")
        assertFalse(drawn.entry(trigger).isCopy, "the original isn't")
        assertEquals(h, entry.sourceId, "CR 707.10: it points at the original's source, the Herald")
        assertEquals(EngineTable.pictureOf(herald), drawn.face(h).scryfallId)
        assertEquals("copy of Matrix Herald's triggered ability", named(state, copy))
        assertEquals("Matrix Herald's ability", named(state, trigger))
        // Its source gone to the graveyard (STK-056): it still points at the Herald's card, as the original does
        val heraldNow = state.battlefield.single { (it as ObjectId.Card).card.value == h } as ObjectId.Card
        val moved = state.objOrThrow(heraldNow).copy(id = heraldNow.next(), zone = Zone.Graveyard, controller = null)
        state.setup.battlefield.remove(heraldNow)
        state.setup.objects.remove(heraldNow)
        state.setup.objects[moved.id] = moved
        state.setup.player(PlayerId(alice)) { it.copy(graveyard = it.graveyard + moved.id) }
        val gone = draw(state).entry(copy)
        assertEquals(h, gone.sourceId)
        assertTrue(gone.isCopy)
    }

    // STK-046, D4, STK-022, CR 707.10, 111.7: a copied ability of a token that has gone is an entry of its own with the token's picture
    @Test fun aCopiedAbilityOfAGoneTokenKeepsTheTokensPicture() = noFaults {
        val (l) = table.put(alice, Zone.Battlefield, lens)
        val wisp = table.putToken(alice, Cards.definition(Cards.invent("Matrix Wisp", "{0}", "Creature — Spirit", "{0}: You gain 1 life.", "1/1")))
        val art = TokenArt { "wisp-picture" }
        val pictures = HashMap<ObjectId, org.asyncttwiz.protocol.SubmittedDefinition>()
        var state = table.state().activate(wisp)
        val ability = state.stack.last()
        state = state.activate(l).settle(target = { TargetRef.Object(ability) }) { it.copyOnTop() }
        val copy = state.stack.last()
        EngineProjection.project(table.base(), GameModel(state.deepCopy(), TableState()), alice, "node", tokenArt = art, pictures = pictures)
        // The token ceases to exist (CR 111.7)
        val token = table.obj(wisp)
        state.setup.battlefield.remove(token)
        state.setup.objects.remove(token)
        val board = EngineProjection.project(table.base(), GameModel(state.deepCopy(), TableState()), alice, "node", tokenArt = art, pictures = pictures)
        val entry = board.board.stackEffects.single { it.id == "entry:$copy" }
        val frozen = assertNotNull(entry.frozen, "an entry of its own (STK-022)")
        val face = board.definitions.single { it.digest == frozen.definition }.definition.faces.single()
        assertEquals("Matrix Wisp", face.name)
        assertEquals("wisp-picture", face.scryfallId, "D4: the token's picture")
        assertTrue(entry.isCopy)
    }

    // CR 707.10: a copy of an activated ability likewise
    @Test fun aCopiedActivatedAbilityIsACopy() = noFaults {
        val (l, p) = table.put(alice, Zone.Battlefield, lens, pinger)
        var state = table.state().activate(p)
        val ability = state.stack.last()
        state = state.activate(l).settle(target = { TargetRef.Object(ability) }) { it.copyOnTop() }
        val copy = state.stack.last()
        val entry = draw(state).entry(copy)
        assertTrue(entry.isCopy)
        assertEquals(p, entry.sourceId)
        assertEquals("copy of Matrix Pinger's activated ability", named(state, copy))
    }

    // STK-121, D1, CR 707.10, 707.10c: a copy of an instant is drawn as the copied card (its picture, name, type line and text), and its
    // new targets are asked about the spell it copies (D3)
    @Test fun aCopiedInstantIsDrawnAsItsCard() = noFaults {
        val (s, e) = table.put(alice, Zone.Hand, spark, echo)
        var state = table.state().cast(s).settle(target = { d -> d.options.firstOrNull { it is TargetRef.Player } }) { it.pending == null && it.stack.size == 1 }
        val original = state.stack.last()
        state = state.cast(e).settle(target = { TargetRef.Object(original) }) { it.pending?.decision is Decision.May }
        val asked = assertNotNull(state.pending?.decision as? Decision.May)
        assertEquals(original, asked.source, "D3, CR 707.10c: asked about the spell it copies")
        state = state.settle { it.copyOnTop() }
        val copy = state.stack.last()
        val drawn = draw(state)
        val entry = drawn.entry(copy)
        assertTrue(entry.isCopy)
        val frozen = assertNotNull(entry.frozen)
        val face = drawn.board.definitions.single { it.digest == frozen.definition }.definition.faces.first()
        assertEquals("Matrix Spark", face.name)
        assertEquals("Instant", face.typeLine)
        assertEquals("Matrix Spark deals 3 damage to any target.", face.rulesText)
        assertEquals(EngineTable.pictureOf(spark), face.scryfallId, "D1: the copied card's picture")
        assertEquals("copy of Matrix Spark", named(state, copy))
    }

    // D2, CR 707.10f: a copy of a permanent spell is drawn as the copied card, with its text; it becomes a token copy as it resolves
    @Test fun aCopiedPermanentSpellIsDrawnAsItsCardThenItsToken() = noFaults {
        val (g, m) = table.put(alice, Zone.Hand, geode, mimicry)
        var state = table.state().cast(g)
        val original = state.stack.last()
        state = state.cast(m).settle(target = { TargetRef.Object(original) }) { it.copyOnTop() }
        val copy = state.stack.last()
        val drawn = draw(state)
        val frozen = assertNotNull(drawn.entry(copy).frozen)
        val face = drawn.board.definitions.single { it.digest == frozen.definition }.definition.faces.first()
        assertEquals("Matrix Geode", face.name)
        assertEquals("{1}, {T}: You gain 1 life.", face.rulesText)
        assertEquals(EngineTable.pictureOf(geode), face.scryfallId, "D2: its picture, never a blank frame")
        state = state.settle { s -> s.stack.isEmpty() }
        val token = state.battlefield.single { state.objOrThrow(it).token }
        val tokenFace = draw(state).face((token as ObjectId.Card).card.value)
        assertEquals("Matrix Geode", tokenFace.name)
        assertEquals(EngineTable.pictureOf(geode), tokenFace.scryfallId, "SYS-151: the token copy shows the card's picture")
    }

    // SYS-151, CR 707.2: a token that's a copy of a card shows that card's picture
    @Test fun aTokenCopyShowsTheCopiedCard() = noFaults {
        val (mo, c) = table.put(alice, Zone.Battlefield, mold, cub)
        val state = table.state().activate(mo).settle(target = { TargetRef.Object(table.obj(c)) }) { s -> s.stack.isEmpty() && s.battlefield.any { s.objOrThrow(it).token } }
        val token = state.battlefield.single { state.objOrThrow(it).token } as ObjectId.Card
        val face = draw(state).face(token.card.value)
        assertEquals("Matrix Cub", face.name)
        assertEquals(EngineTable.pictureOf(cub), face.scryfallId)
    }

    // SYS-205, D8 (owner 2026-11-19), CR 707.2: a permanent that becomes a copy shows the copied card's picture, name and text
    @Test fun aPermanentThatBecomesACopyShowsTheCopiedCard() = noFaults {
        table.put(alice, Zone.Battlefield, cub)
        val (cl) = table.put(alice, Zone.Hand, clone)
        val state = table.state().cast(cl).settle(may = true) { s -> s.stack.isEmpty() && s.pending == null && s.battlefield.size == 2 }
        val face = draw(state).face(cl)
        assertEquals("Matrix Cub", face.name)
        assertEquals("Trample", face.rulesText)
        assertEquals(EngineTable.pictureOf(cub), face.scryfallId)
    }

    // D9, SYS-068, CR 723.3c: a prepare spell's copy in exile shows the card's own picture (its one physical face)
    @Test fun aPrepareSpellsCopyShowsTheCardsPicture() = noFaults {
        table.put(alice, Zone.Battlefield, seraph)
        val copy = table.putCopyOfCard(alice, table.printed(seraph).faces[1])
        val face = draw(table.state()).face(copy)
        assertEquals("Matrix Mending", face.name)
        assertEquals(EngineTable.pictureOf("Matrix Seraph // Matrix Mending"), face.scryfallId)
    }

    // D9, STK-062, CR 707.12: a copy of a card made by its name, from the card records, shows its printing's picture
    @Test fun aNamedCardsCopyShowsItsPrinting() = noFaults {
        val printed = Cards.definition(cub).faces.single().copy(identity = org.asyncttwiz.protocol.game.GameCards.printing("named-printing", 0))
        val copy = table.putCopyOfCard(alice, printed)
        assertEquals("named-printing", draw(table.state()).face(copy).scryfallId)
    }

    // N25 (owner playtest 2026-11-19), SYS-072: a planeswalker (loyalty) token is drawn with its token picture
    @Test fun aLoyaltyTokenIsDrawnWithItsPicture() = noFaults {
        val jace = arcanum.api.CardDefinition(arcanum.api.Layout.Normal, listOf(arcanum.api.FaceDefinition("Matrix Jace",
            colorIndicator = listOf(arcanum.api.Color.Blue), supertypes = listOf(arcanum.api.Supertype.Legendary),
            types = listOf(arcanum.api.CardType.Planeswalker), subtypes = listOf(arcanum.api.Subtype("Jace")), loyalty = arcanum.api.PrintedNumber.Fixed(0))))
        val token = table.putToken(alice, jace)
        assertEquals("jace-picture", draw(table.state(), TokenArt { "jace-picture" }).face(token).scryfallId)
    }

    @kotlin.test.AfterTest fun nothingReported() = assertEquals(emptyList(), faults, "a picture or name was missing")
}
