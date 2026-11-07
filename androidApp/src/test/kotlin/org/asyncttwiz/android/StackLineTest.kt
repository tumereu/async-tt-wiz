package org.asyncttwiz.android

import arcanum.api.Ability
import arcanum.api.Layout
import arcanum.engine.state.CardId
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * STK-057, STK-058, STK-018 (owner playtest 2026-11-07, Fractured Sanity): the line saying what the other player's top
 * Stack entry is, its target marks on the table and the Stack list's targeted entries, on boards made by hand.
 */
class StackLineTest {
    private val f = TableStatesFixture
    private val names = mapOf(f.a to "Alice", f.b to "Bob")
    private val reasonText = "Each opponent mills fourteen cards.\nCycling {R}\nWhen you cycle this card, each opponent mills four cards."
    private val reason = invented("reason", "Shattered Reason", "Instant", reasonText)
    private val spark = invented("spark", "Storm Spark", "Instant", "Storm Spark deals 3 damage to any target.")
    private val burst = invented("burst", "Ember Burst", "Sorcery", "Ember Burst deals X damage to any target.")
    private val snare = invented("snare", "Frost Snare", "Instant", "Tap up to two target creatures.")

    private class Invented(val submitted: SubmittedDefinition, val engine: arcanum.api.CardDefinition)

    private fun invented(id: String, name: String, type: String, text: String): Invented {
        val definition = CardDefinition(name, listOf(DefinitionFace(f.id("face/$id"), name, typeLine = type, manaCost = "{1}", rulesText = text)))
        return Invented(SubmittedDefinition(DeckJson.digest(definition), definition),
            arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(OracleCard(name, "{1}", type, text)))))
    }

    /** Bob's card of [card] in his [zone] with [item] on the Stack as its entry (top), Alice holding priority unless [holder]. */
    private fun board(card: Invented, zone: PlayZone, item: (String) -> MtgStackItem, holder: String? = f.a,
        below: List<StackEffect> = emptyList()): TableBoard {
        val b = f.initial.board
        val container = b.containers.single { it.zone == zone && it.accountId == f.b }
        val placed = PlayCard(f.id("card/${card.submitted.definition.name}"), card.submitted.digest, f.b, container.id,
            card.submitted.definition.faces.single().faceId)
        val entry = StackEffect(placed.id, placed.id, controller = f.b, item = item(placed.id))
        return tableBoard(f.initial.copy(definitions = f.initial.definitions + card.submitted, board = b.copy(
            cards = b.cards + placed,
            containers = b.containers.map { when {
                it.id == container.id -> it.copy(cards = it.cards + placed.id)
                it.zone == PlayZone.STACK -> it.copy(cards = listOf(placed.id) + below.map { e -> e.id })
                else -> it
            } },
            stackEffects = listOf(entry) + below, priority = MtgPriority(holder))))
    }

    /** [card]'s spell with [targets] chosen for its first target slot ([slot] when it names another key). */
    private fun spell(card: Invented, targets: List<MtgRef> = emptyList(), x: Int? = null, slot: String? = null): (String) -> MtgStackItem = { id ->
        val ability = card.engine.faces.single().abilities.filterIsInstance<Ability.Spell>().single()
        val key = slot ?: ability.instructions.targets.firstOrNull()?.name ?: "target"
        val engine = StackItem(ObjectId.Card(CardId(id), 0), StackItemKind.Spell, PlayerId(f.b), ObjectId.Card(CardId(id), 0), ability,
            targets = if (targets.isEmpty()) emptyMap() else mapOf(key to targets.map(::engineRef)),
            announced = listOfNotNull(x?.let { "X" to it }).toMap(), timestamp = 1)
        structured(engine, MtgRef.Card(id, 0), MtgStackKind.Spell)
    }

    private fun engineRef(ref: MtgRef): TargetRef = when (ref) {
        is MtgRef.Card -> TargetRef.Object(ObjectId.Card(CardId(ref.cardId), ref.zoneChanges))
        is MtgRef.Player -> TargetRef.Player(PlayerId(ref.accountId))
        is MtgRef.StackItem -> TargetRef.Object(ObjectId.StackItem(ref.id))
    }

    /** The structured item the table gets of the engine's [item] (as the projection writes it). */
    private fun structured(item: StackItem, id: MtgRef, kind: MtgStackKind) = MtgStackItem(id, kind,
        item.controller.value, targets = item.targets.mapValues { (_, refs) -> refs.map { r -> when (r) {
            is TargetRef.Player -> MtgRef.Player(r.id.value)
            is TargetRef.Object -> when (val o = r.id) {
                is ObjectId.Card -> MtgRef.Card(o.card.value, o.zoneChanges)
                else -> MtgRef.StackItem((o as ObjectId.StackItem).id)
            }
        } } }, values = item.announced,
        timestamp = item.timestamp, data = kotlinx.serialization.json.Json { encodeDefaults = false }.encodeToString(StackItem.serializer(), item))

    private val cycleTrigger: (String) -> MtgStackItem = { id ->
        val ability = reason.engine.faces.single().abilities.filterIsInstance<Ability.Triggered>().single()
        structured(StackItem(ObjectId.StackItem("T1"), StackItemKind.Triggered, PlayerId(f.b), ObjectId.Card(CardId(id), 1),
            ability, timestamp = 2), MtgRef.StackItem("T1"), MtgStackKind.Triggered)
    }

    private fun name(card: PlayCard) = f.initial.definitions.single { it.digest == card.definition }.definition.faces
        .single { it.faceId == card.faceId }.name

    // STK-057: the other player's spell names its caster and what it does, in short
    @Test fun aSpellSaysWhoCastItAndWhatItDoes() {
        assertEquals("Bob cast Shattered Reason · each opponent mills 14 cards",
            StackLine.text(board(reason, PlayZone.HAND, spell(reason)), f.a, names))
    }

    // STK-057, STK-046: a cycling trigger is named as its source's cycling trigger, with what it does
    @Test fun aCyclingTriggerIsNamedByItsKind() {
        val play = board(reason, PlayZone.GRAVEYARD, cycleTrigger)
        assertEquals("Shattered Reason's cycling trigger · each opponent mills 4 cards", StackLine.text(play, f.a, names))
        // STK-050: the kind shows on the token, in the Stack list and in Inspect
        assertEquals("Cycling trigger", StackAbilities.of(play, CombatStackRules.stack(play.board).cards.first(), f.a)?.kind)
    }

    // STK-057 (owner 2026-11-07: "Lightning Bolt deals 3 damage to Llanowar Elves"): each target is named where the effect
    // names it, players by name ("you" for the viewer), X filled in; the name carries its controller, which colours it
    @Test fun targetsAndXShowInTheEffect() {
        val sentinel = f.source(f.a, "1/3")
        val play = board(spark, PlayZone.HAND, spell(spark, listOf(MtgRef.Card(sentinel.id, 0))))
        assertEquals("Bob cast Storm Spark · deals 3 damage to ${name(sentinel)}", StackLine.text(play, f.a, names))
        assertEquals(StackTextPiece(name(sentinel), f.a), StackLine.pieces(play, f.a, names)!!.last())
        assertEquals("Bob cast Ember Burst · deals 4 damage to you",
            StackLine.text(board(burst, PlayZone.HAND, spell(burst, listOf(MtgRef.Player(f.a)), x = 4)), f.a, names))
        // A player target is in their own colour
        assertEquals(StackTextPiece("Bob", f.b), StackLine.pieces(board(spark, PlayZone.HAND, spell(spark, listOf(MtgRef.Player(f.b)))), f.a, names)!!.last())
    }

    // STK-057: two targets of one phrase are named together
    @Test fun twoTargetsOfOnePhrase() {
        val sentinel = f.source(f.a, "1/3")
        val other = f.source(f.b, "2/3")
        assertEquals("Bob cast Frost Snare · tap ${name(sentinel)} and ${name(other)}", StackLine.text(board(snare, PlayZone.HAND,
            spell(snare, listOf(MtgRef.Card(sentinel.id, 0), MtgRef.Card(other.id, 0)))), f.a, names))
    }

    // STK-057: a target whose phrase isn't known (here kept under another key) follows the effect, as before
    @Test fun aTargetWithoutItsPhraseFollowsTheEffect() {
        val sentinel = f.source(f.a, "1/3")
        assertEquals("Bob cast Storm Spark · deals 3 damage to any target → ${name(sentinel)}",
            StackLine.text(board(spark, PlayZone.HAND, spell(spark, listOf(MtgRef.Card(sentinel.id, 0)), slot = "elsewhere")), f.a, names))
    }

    // STK-057, HIS-062: a target the viewer can't see is "a card", never named
    @Test fun aHiddenTargetIsACard() {
        val hidden = f.initial.board.cards.first { it.containerId == f.id("b/HAND") }
        assertEquals("Bob cast Storm Spark · deals 3 damage to a card",
            StackLine.text(board(spark, PlayZone.HAND, spell(spark, listOf(MtgRef.Card(hidden.id, 0)))), f.a, names))
    }

    // STK-057: never for your own entries, nor while you don't hold priority
    @Test fun notForYourOwnEntriesOrWithoutPriority() {
        val play = board(spark, PlayZone.HAND, spell(spark, listOf(MtgRef.Player(f.a))))
        assertNull(StackLine.text(play, f.b, names), "Bob's own spell")
        assertNull(StackLine.text(board(spark, PlayZone.HAND, spell(spark), holder = f.b), f.a, names), "Bob holds priority")
    }

    // STK-058: the top entry's targets are marked where they are on the table: a battlefield card, a life square; in the
    // controller's colour (the other player's here)
    @Test fun theTopEntrysTargetsAreMarked() {
        val sentinel = f.source(f.a, "1/3")
        val play = board(spark, PlayZone.HAND, spell(spark, listOf(MtgRef.Card(sentinel.id, 0), MtgRef.Player(f.a))))
        val scene = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false)
        val marks = requireNotNull(StackTargets.of(play, scene, f.a, names))
        assertEquals(false, marks.own)
        assertEquals(listOf(sentinel.id, null), marks.items.map { id -> scene.items.single { it.id == id }.cardId })
        assertEquals("life/${f.a}", marks.items.last())
        assertEquals("Targets of the top Stack entry: ${name(sentinel)}, you", marks.label)
        // Without targets nothing is marked
        assertNull(StackTargets.of(board(reason, PlayZone.HAND, spell(reason)), scene, f.a, names))
    }

    // STK-058, STK-018: a target that is another Stack entry rings the Stack, and the Stack list says which entry targets it
    @Test fun aStackEntryTargetIsMarkedOnTheStack() {
        val sentinel = f.source(f.b, "2/3")
        val below = StackEffect(sentinel.id, sentinel.id, controller = f.a, item = MtgStackItem(MtgRef.Card(sentinel.id, 0),
            MtgStackKind.Spell, f.a, timestamp = 0))
        val play = board(spark, PlayZone.HAND, spell(spark, listOf(MtgRef.Card(sentinel.id, 0))), below = listOf(below))
        val scene = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false)
        val marks = requireNotNull(StackTargets.of(play, scene, f.a, names))
        assertTrue(marks.stack)
        assertTrue(marks.items.isEmpty())
        val top = CombatStackRules.stack(play.board).cards.first()
        assertEquals(mapOf(sentinel.id to listOf(top)), stackTargetedBy(play, CombatStackRules.stack(play.board).cards))
    }

    // STK-018: the Stack list's rows show what was chosen for each entry (StackAbilities.details), here a spell's X and target
    @Test fun theStackListShowsTheChoices() {
        val play = board(burst, PlayZone.HAND, spell(burst, listOf(MtgRef.Player(f.a)), x = 4))
        val entry = CombatStackRules.stack(play.board).cards.first()
        assertEquals(listOf("X = 4", "Target: You"), StackAbilities.details(play, entry, f.a, names))
        // STK-018, STK-050 (owner 2026-11-07): the entry's text names its targets and fills in X; its details then list only
        // the targets the text doesn't name
        val text = requireNotNull(StackAbilities.text(play, entry, f.a, names))
        assertEquals("Ember Burst deals 4 damage to you.", text.pieces.plain())
        assertEquals(listOf("X = 4"), StackAbilities.details(play, entry, f.a, names, text))
    }

    // edgeToward: an arrow leaves the Stack at its edge, towards its target
    @Test fun anArrowLeavesARectangleAtItsEdge() {
        val b = TableBounds(100f, 100f, 40f, 60f)
        assertEquals(TablePoint(100f, 130f), b.edgeToward(TablePoint(0f, 130f)))
        assertEquals(TablePoint(120f, 97f), b.edgeToward(TablePoint(120f, 0f), gap = 3f))
    }
}
