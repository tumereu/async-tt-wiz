package arcanum.engine.view

import arcanum.api.CardDefinition
import arcanum.api.Layout
import arcanum.api.Mechanic
import arcanum.api.Objects
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.ObjectId
import arcanum.engine.state.StackItemKind
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The host's identity of a printed face ([arcanum.api.FaceDefinition.identity]) is a copiable value: every kind of copy carries
 * it, so a host draws a copy as the card its values came from (problem report MDLPM7). A copy of a spell or an ability says what
 * it copies ([arcanum.engine.state.StackItem.copyOf]) and is put on the Stack as a copy ([Meaning.Copied]).
 */
class CopyIdentityTest {
    private fun identified(name: String, cost: String?, type: String, text: String = "", pt: String? = null): String {
        val (power, toughness) = pt?.split('/')?.let { it[0] to it[1] } ?: (null to null)
        val face = OracleParser.parse(OracleCard(name, cost, type, text, power, toughness))
        return Cards.define(name, CardDefinition(Layout.Normal, listOf(face.copy(identity = "printed/$name"))))
    }

    private val cub = identified("Identity Cub", "{1}{R}", "Creature — Elemental", "", "2/2")
    private val mirror = identified("Identity Mirror", "{0}", "Artifact", "")
    private val mold = identified("Identity Mold", "{0}", "Artifact", "{0}: Create a token that's a copy of target creature you control.")
    private val spark = identified("Identity Spark", "{R}", "Instant", "Identity Spark deals 3 damage to any target.")
    private val echo = identified("Identity Echo", "{R}", "Instant",
        "Copy target instant or sorcery spell. You may choose new targets for the copy.")
    private val herald = identified("Identity Herald", "{0}", "Creature — Human",
        "When this creature enters, you gain 2 life.", "1/1")
    private val kindler = identified("Identity Kindler", "{0}", "Artifact",
        "{0}: Copy target triggered ability you control. You may choose new targets for the copy.")

    private fun Scenario.identityOf(id: ObjectId) = Engine.view(state)[id]?.characteristics?.identity

    // CR 707.2: a permanent that becomes a copy has the copied card's copiable values, its printed face's identity among them
    @Test
    fun aPermanentThatBecomesACopyCarriesTheCopiedFacesIdentity() = scenario {
        alice.battlefield(cub, mirror)
        assertEquals("printed/$mirror", identityOf(card(mirror)), "before the copy effect, its own face")
        effect(alice, Mechanic.CopyOf(Objects.Bound("original")), on = listOf(mirror), names = mapOf("original" to cub))
        assertEquals("printed/$cub", identityOf(card(mirror)))
        assertEquals("printed/$cub", Engine.view(state)[card(mirror)]?.copiable?.faces?.single()?.identity)
    }

    // CR 707.2, 111.1: a token that's a copy of a permanent holds the copied face, its identity with it
    @Test
    fun aTokenCopyCarriesTheCopiedFacesIdentity() = scenario {
        alice.battlefield(cub, mold)
        alice.activates(mold, target = cub)
        allPass()
        val token = state.battlefield.single { state.objOrThrow(it).token }
        assertEquals("printed/$cub", state.objOrThrow(token).definition.faces.single().identity)
        assertEquals("printed/$cub", identityOf(token))
    }

    // CR 708.2a: a face-down permanent has no name, text or identity
    @Test
    fun aFaceDownPermanentHasNoIdentity() = scenario {
        alice.battlefield(cub)
        val id = card(cub) as ObjectId.Card
        state._objects[id] = state.objOrThrow(id).copy(faceDown = true)
        assertNull(identityOf(id))
    }

    // CR 707.10, 707.10c: a copy of a spell copies its copiable values (the identity among them) and says what it copies; the
    // question of new targets, asked before the copy is put on the Stack, names the spell it copies
    @Test
    fun aSpellCopyCarriesTheIdentityAndAsksAboutTheSpellItCopies() = scenario {
        alice.battlefield("Mountain", "Mountain")
        alice.hand(spark, echo)
        bob.battlefield(cub)
        alice.casts(spark, target = "bob", paying = listOf("Mountain"))
        val original = state.stack.last()
        alice.casts(echo, target = spark, paying = listOf("Mountain#1"))
        allPass()
        val asked = assertNotNull(pending as? Decision.May, "the copy may get new targets: $pending")
        assertEquals(original, asked.source, "CR 707.10c: asked about the spell it copies, not a copy that isn't there yet")
        answer(Answer.YesNo(false))
        val copyId = state.stack.last()
        val copy = state.stackItems.getValue(copyId)
        assertTrue(copy.kind is StackItemKind.SpellCopy)
        assertEquals(original, copy.copyOf)
        assertEquals("printed/$spark", identityOf(copyId))
        val added = arcanum.engine.Inspect.events(state).mapNotNull { it.change as? Change.StackItemAdded }.last { it.item.id == copyId }
        assertEquals(listOf(Meaning.Copied), added.meanings)
        val copied = arcanum.engine.Inspect.events(state).mapNotNull { (it.change as? Change.Happened)?.event as? GameEvent.Copied }.last()
        assertEquals(copyId, copied.copy)
    }

    // CR 707.10: a copy of a triggered ability keeps the original's source and kind, says what it copies, and its source's
    // last known information carries the source's identity once the source has left (CR 113.7a)
    @Test
    fun aCopiedTriggerKeepsItsSourceAndSaysWhatItCopies() = scenario {
        alice.battlefield(kindler)
        alice.hand(herald)
        alice.casts(herald)
        allPass()
        val trigger = state.stack.last()
        assertEquals(StackItemKind.Triggered, state.stackItems.getValue(trigger).kind)
        alice.activates(kindler)
        answer(Answer.Targets(listOf(arcanum.engine.state.TargetRef.Object(trigger))))
        while (pending is Decision.ManaPayment) answer(Answer.ManaPayment((pending as Decision.ManaPayment).suggestion))
        allPass()
        if (pending is Decision.May) answer(Answer.YesNo(false))
        val copyId = state.stack.last()
        val copy = state.stackItems.getValue(copyId)
        val original = state.stackItems.getValue(trigger)
        assertEquals(trigger, copy.copyOf)
        assertEquals(StackItemKind.Triggered, copy.kind)
        assertEquals(original.source, copy.source)
        assertEquals("printed/$herald", copy.source?.let { identityOf(it) })
    }
}
