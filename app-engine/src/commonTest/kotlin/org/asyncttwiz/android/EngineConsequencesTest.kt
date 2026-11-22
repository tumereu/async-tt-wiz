@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.android

import arcanum.api.CardDefinition
import arcanum.api.CounterKind
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.change.CausedBy
import arcanum.engine.change.Change
import arcanum.engine.change.DamageDealt
import arcanum.engine.change.Meaning
import arcanum.engine.change.Move
import arcanum.engine.change.Placed
import arcanum.engine.input.Input
import arcanum.engine.state.CardId
import arcanum.engine.state.CombatState
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectSnapshot
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PlayerState
import arcanum.engine.state.PriorityState
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnState
import arcanum.engine.state.of
import org.asyncttwiz.protocol.game.EnginePart
import org.asyncttwiz.protocol.game.EngineStep
import org.asyncttwiz.protocol.game.GameEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HIS-246–HIS-249 (owner 2026-11-22, F6): the game log's indented lines under an event's line, made from the event's
 * changes: one line per kind and destination in the order things happened, totals after a change where the event carries
 * them, the other player's draws as counts.
 */
class EngineConsequencesTest {
    private val alice = PlayerId("alice")
    private val bob = PlayerId("bob")
    private val names = mapOf("alice" to "Alice", "bob" to "Bob")
    private var next = 0
    private fun def(name: String) = CardDefinition(Layout.Normal, listOf(FaceDefinition(name)))
    private fun obj(name: String, owner: PlayerId, zone: Zone, faceDown: Boolean = false, token: Boolean = false) =
        GameObject(ObjectId.Card(CardId("${owner.value}.${++next}"), 0), owner, owner.takeIf { zone == Zone.Battlefield }, zone, def(name),
            faceDown = faceDown, token = token, timestamp = next.toLong())
    private fun moved(o: GameObject, to: Zone, vararg meanings: Meaning, faceDown: Boolean = o.faceDown) =
        Move(o, o.copy(id = ObjectId.Card(o.id.card, o.id.zoneChanges + 1), zone = to, controller = o.controller.takeIf { to == Zone.Battlefield },
            faceDown = faceDown), 0, 0, meanings.toList())

    private val cub = obj("Ember Cub", bob, Zone.Battlefield)
    private val sage = obj("Ember Sage", bob, Zone.Battlefield)
    private val herald = obj("Ember Herald", alice, Zone.Battlefield)
    private val state = GameState.of(players = listOf(PlayerState(alice, 20), PlayerState(bob, 17)),
        objects = listOf(cub, sage, herald).associateBy { it.id }, battlefield = listOf(cub.id, sage.id, herald.id),
        turn = TurnState(3, alice, TurnStep.PrecombatMain), priority = PriorityState(alice))
    private val spark = CausedBy(null, "Storm Spark", alice)

    private fun event(vararg changes: Change) = GameEvent("e", "p", "alice", EnginePart(0, listOf(EngineStep(Input.PassPriority(alice), changes.toList()))))
    private fun lines(e: GameEvent, viewer: String, part: EngineConsequences.Part = EngineConsequences.Part.All) =
        EngineConsequences.of(e, state, viewer, names, part = part)
    private fun texts(e: GameEvent, viewer: String) = lines(e, viewer).map { it.text }

    // HIS-246: a spell that kills a creature and makes its controller lose life: the death and the life change with the
    // total after it, each name in its player's colour, from both sides
    @Test fun aDeathAndALifeChange() {
        val e = event(Change.ZoneChange(listOf(moved(cub, Zone.Graveyard, Meaning.Died))), Change.Life(bob, 20, 17, listOf(Meaning.LifeLost), spark))
        assertEquals(listOf("Ember Cub died", "Bob lost 3 life (17)"), texts(e, "alice"))
        assertEquals(listOf("Ember Cub died", "You lost 3 life (17)"), texts(e, "bob"))
        assertEquals(listOf("Ember Cub" to "bob", " died" to null), lines(e, "alice")[0].pieces)
        assertEquals(listOf("Bob" to "bob", " lost 3 life (17)" to null), lines(e, "alice")[1].pieces)
    }

    // HIS-246: leaving the battlefield by where it went; a sacrifice reads "died" (owner 2026-11-22); several of a kind share a line
    @Test fun whereTheyWent() {
        val e = event(Change.ZoneChange(listOf(moved(cub, Zone.Exile, Meaning.Exiled), moved(sage, Zone.Exile, Meaning.Exiled)), spark),
            Change.ZoneChange(listOf(moved(herald, Zone.Hand, Meaning.ReturnedToHand)), spark))
        assertEquals(listOf("Ember Cub and Ember Sage were exiled", "Ember Herald returned to your hand"), texts(e, "alice"))
        assertEquals(listOf("Ember Cub and Ember Sage were exiled", "Ember Herald returned to Alice's hand"), texts(e, "bob"))
        val sacrificed = event(Change.ZoneChange(listOf(moved(cub, Zone.Graveyard, Meaning.Sacrificed, Meaning.Died))))
        assertEquals(listOf("Ember Cub died"), texts(sacrificed, "alice"))
        val library = event(Change.ZoneChange(listOf(moved(cub, Zone.Library, Meaning.PutIntoLibrary)), spark))
        assertEquals(listOf("Ember Cub was put into Bob's library"), texts(library, "alice"))
    }

    // HIS-249, HIS-062: a face-down creature returned to its owner's hand stays hidden from the other player; one put into a
    // graveyard is revealed there, so it is named
    @Test fun aFaceDownCreatureLeaving() {
        val hidden = obj("Ember Mask", bob, Zone.Battlefield, faceDown = true)
        val bounce = event(Change.ZoneChange(listOf(moved(hidden, Zone.Hand, Meaning.ReturnedToHand, faceDown = false)), spark))
        assertEquals(listOf("Bob's face-down creature returned to Bob's hand"), texts(bounce, "alice"))
        assertEquals(listOf("Ember Mask returned to your hand"), texts(bounce, "bob"))
        val dies = event(Change.ZoneChange(listOf(moved(hidden, Zone.Graveyard, Meaning.Died, faceDown = false))))
        assertEquals(listOf("Ember Mask died"), texts(dies, "alice"))
    }

    // HIS-249, HIS-250, HIS-062: cards drawn by a spell or ability: named to their owner, a count to the other player; the draw step's
    // own draw (made by a rule, no source) gets no line
    @Test fun drawsAreCountsForTheOtherPlayer() {
        val a = obj("Mountain", bob, Zone.Library)
        val b = obj("Ember Herald", bob, Zone.Library)
        val e = event(Change.ZoneChange(listOf(moved(a, Zone.Hand, Meaning.Drew)), spark), Change.ZoneChange(listOf(moved(b, Zone.Hand, Meaning.Drew)), spark))
        assertEquals(listOf("Bob drew 2 cards"), texts(e, "alice"))
        assertEquals(listOf("You drew Mountain and Ember Herald"), texts(e, "bob"))
        val one = event(Change.ZoneChange(listOf(moved(a, Zone.Hand, Meaning.Drew)), spark))
        assertEquals(listOf("Bob drew a card"), texts(one, "alice"))
        val turnDraw = event(Change.ZoneChange(listOf(moved(a, Zone.Hand, Meaning.Drew))))
        assertEquals(emptyList(), texts(turnDraw, "alice"))
        assertEquals(emptyList(), texts(turnDraw, "bob"))
    }

    // HIS-246, HIS-249, HIS-250: discarded and milled cards are public in the graveyard and named to everyone, past three "and N more"; a
    // cycled card's discard is the cycling line's own (HIS-121), no line of its own
    @Test fun discardsAndMills() {
        val hand = obj("Ember Herald", bob, Zone.Hand)
        val milled = listOf("Mountain", "Storm Spark", "Ember Cub", "Mountain").map { obj(it, bob, Zone.Library) }
        val e = event(Change.ZoneChange(listOf(moved(hand, Zone.Graveyard, Meaning.Discarded)), spark),
            Change.ZoneChange(milled.map { moved(it, Zone.Graveyard, Meaning.Milled) }, spark))
        assertEquals(listOf("Bob discarded Ember Herald", "Bob milled Mountain, Storm Spark, Ember Cub and 1 more"), texts(e, "alice"))
        assertEquals(listOf("You discarded Ember Herald", "You milled Mountain, Storm Spark, Ember Cub and 1 more"), texts(e, "bob"))
        val cycled = event(Change.ZoneChange(listOf(moved(hand, Zone.Graveyard, Meaning.Discarded, Meaning.Cycled))))
        assertEquals(emptyList(), texts(cycled, "alice"))
    }

    // HIS-246: tokens created, by kind with how many
    @Test fun tokens() {
        val goblins = List(2) { Placed(obj("Goblin", alice, Zone.Battlefield, token = true), it) }
        val treasure = Placed(obj("Treasure Token", alice, Zone.Battlefield, token = true), 2)
        val e = event(Change.Created(goblins + treasure, by = spark))
        assertEquals(listOf("You created 2 Goblin tokens and a Treasure token"), texts(e, "alice"))
        assertEquals(listOf("Alice created 2 Goblin tokens and a Treasure token"), texts(e, "bob"))
        assertEquals(listOf("Alice" to "alice", " created 2 " to null, "Goblin" to "alice", " tokens and a " to null, "Treasure" to "alice", " token" to null),
            lines(e, "bob")[0].pieces)
    }

    // HIS-246, HIS-249: counters put on or removed with the count after; several creatures given the same counters share a
    // line, each with its own count
    @Test fun countersWithTheirCount() {
        val plus = CounterKind.PowerToughness(1, 1)
        val one = event(Change.Counters(TargetRef.Object(cub.id), plus, CounterEntry(plus, 1, 1), CounterEntry(plus, 2, 1)))
        assertEquals(listOf("Ember Cub got a +1/+1 counter (2)"), texts(one, "alice"))
        val many = event(Change.Counters(TargetRef.Object(cub.id), plus, CounterEntry(plus, 1, 1), CounterEntry(plus, 2, 1)),
            Change.Counters(TargetRef.Object(sage.id), plus, null, CounterEntry(plus, 1, 1)))
        assertEquals(listOf("Ember Cub (2) and Ember Sage (1) got a +1/+1 counter"), texts(many, "alice"))
        val two = event(Change.Counters(TargetRef.Object(herald.id), plus, null, CounterEntry(plus, 1, 1)),
            Change.Counters(TargetRef.Object(herald.id), plus, CounterEntry(plus, 1, 1), CounterEntry(plus, 2, 1)))
        assertEquals(listOf("Ember Herald got 2 +1/+1 counters (2)"), texts(two, "bob"))
        val removed = event(Change.Counters(TargetRef.Object(cub.id), CounterKind.Loyalty, CounterEntry(CounterKind.Loyalty, 4, 1), CounterEntry(CounterKind.Loyalty, 3, 1)))
        assertEquals(listOf("Ember Cub lost a loyalty counter (3)"), texts(removed, "alice"))
        val poison = event(Change.Counters(TargetRef.Player(bob), CounterKind.Poison, CounterEntry(CounterKind.Poison, 1, 1), CounterEntry(CounterKind.Poison, 3, 1)))
        assertEquals(listOf("Bob got 2 poison counters (3)"), texts(poison, "alice"))
    }

    private fun trigger(source: GameObject, id: String) = StackItem(ObjectId.StackItem(id), StackItemKind.Triggered, source.controller ?: source.owner,
        source.id, null, snapshots = listOf(ObjectSnapshot(source.id, source.owner, source.controller, source.zone, null)), timestamp = 9)

    // HIS-246, HIS-248: triggers put on the Stack by their source; an event the log shows no line for gives only its triggers
    @Test fun triggersAndTriggersOnly() {
        val e = event(Change.ZoneChange(listOf(moved(cub, Zone.Graveyard, Meaning.Died))),
            Change.StackItemAdded(trigger(herald, "t1"), 0, listOf(Meaning.Triggered)), Change.Life(bob, 20, 19, listOf(Meaning.LifeLost)))
        assertEquals(listOf("Ember Cub died", "Ember Herald's ability triggered", "Bob lost 1 life (19)"), texts(e, "alice"))
        assertEquals(listOf("Ember Herald's ability" to "alice", " triggered" to null), lines(e, "bob")[1].pieces)
        assertEquals(listOf("Ember Herald's ability triggered"), lines(e, "alice", EngineConsequences.Part.Triggers).map { it.text })
    }

    // HIS-246: one line per kind in the order things happened: a player's losses add up, with the total after the last
    @Test fun oneLinePerKindInOrder() {
        val e = event(Change.Life(bob, 20, 18, listOf(Meaning.LifeLost)), Change.ZoneChange(listOf(moved(cub, Zone.Graveyard, Meaning.Died))),
            Change.Life(alice, 20, 22, listOf(Meaning.LifeGained)), Change.Life(bob, 18, 17, listOf(Meaning.LifeLost)),
            Change.ZoneChange(listOf(moved(sage, Zone.Graveyard, Meaning.Died))))
        assertEquals(listOf("Bob lost 3 life (17)", "Ember Cub and Ember Sage died", "You gained 2 life (22)"), texts(e, "alice"))
        val paid = event(Change.Life(alice, 20, 18, listOf(Meaning.LifePaid)))
        assertEquals(listOf("Alice paid 2 life (18)"), texts(paid, "bob"))
    }

    // HIS-247: an event that dealt combat damage reads "Combat damage", "First-strike damage" in that step; others none
    @Test fun combatDamageLines() {
        val damage = Change.Damage(listOf(DamageDealt(cub.id, TargetRef.Player(alice), 2, combat = true)))
        assertEquals("Combat damage", EngineConsequences.combatDamage(event(damage, Change.Life(alice, 20, 18))))
        val firstStrike = Change.Combat(CombatState(), CombatState(firstStrikeStep = true))
        assertEquals("First-strike damage", EngineConsequences.combatDamage(event(firstStrike, damage)))
        assertNull(EngineConsequences.combatDamage(event(Change.Damage(listOf(DamageDealt(cub.id, TargetRef.Player(alice), 2))))))
        assertNull(EngineConsequences.combatDamage(event(Change.Life(alice, 20, 18))))
        // Its lines are the deaths and life changes it caused
        val e = event(damage, Change.Life(alice, 20, 18), Change.ZoneChange(listOf(moved(herald, Zone.Graveyard, Meaning.Died, Meaning.StateBased))))
        assertEquals(listOf("You lost 2 life (18)", "Ember Herald died"), texts(e, "alice"))
        // An event the timeline shows that went on to deal combat damage ("Bob: no blocks"): its own lines are those before
        // the step into the combat damage step, the combat damage line's from there
        val toDamage = Change.Step(TurnState(3, bob, TurnStep.DeclareBlockers), TurnState(3, bob, TurnStep.CombatDamage))
        val folded = event(Change.ZoneChange(listOf(moved(cub, Zone.Graveyard, Meaning.Died))), toDamage, damage, Change.Life(alice, 20, 18))
        assertEquals(1, EngineConsequences.combatDamageStart(folded))
        assertEquals(listOf("Ember Cub died"), lines(folded, "alice", EngineConsequences.Part.BeforeCombatDamage).map { it.text })
        assertEquals(listOf("You lost 2 life (18)"), lines(folded, "alice", EngineConsequences.Part.CombatDamage).map { it.text })
        assertNull(EngineConsequences.combatDamageStart(event(Change.Life(alice, 20, 18))))
    }

    // HIS-249, HIS-062: nothing hidden is named: the other player's drawn card names appear nowhere in the lines
    @Test fun noHiddenNames() {
        val secret = obj("Ember Secret", bob, Zone.Library)
        val e = event(Change.ZoneChange(listOf(moved(secret, Zone.Hand, Meaning.Drew)), spark))
        assertTrue(lines(e, "alice").none { "Ember Secret" in it.text })
    }
}
