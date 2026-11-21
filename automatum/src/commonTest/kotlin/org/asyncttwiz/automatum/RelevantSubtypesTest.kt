@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.automatum

import arcanum.api.Subtype
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.of
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TBL-233, TBL-234, TBL-237 (owner, 2026-11-21): battlefield cards show their card types, and a tag for each subtype a card the viewer can
 * see refers to: their own hand, either battlefield and the Stack, never the other player's hand; on either player's permanents.
 */
class RelevantSubtypesTest {
    init {
        Cards.invent("Dunescale Chief", "{0}", "Creature — Lizard Warrior", "Other Lizards you control get +1/+1.", "2/2")
        Cards.invent("Sunbaked Basker", "{0}", "Creature — Lizard", "", "1/1")
        Cards.invent("Cinder Goblin", "{0}", "Creature — Goblin", "", "1/1")
        Cards.invent("Goblin Warcaller", "{0}", "Creature — Goblin", "Other Goblins you control get +1/+1.", "2/2")
        Cards.invent("Dustmane Charger", "{1}{R}", "Creature — Horse Mount", "Saddle 1", "2/2")
        Cards.invent("Brass Plow", "{2}", "Artifact Creature — Ox", "", "2/2")
        Cards.invent("Tin Lantern", "{2}", "Artifact", "")
        Cards.invent("Clockwork Awakening", "{1}{U}", "Enchantment — Aura",
            "Enchant artifact\nEnchanted artifact is a creature with base power and toughness 4/4 in addition to its other types.")
        Cards.invent("Mirror Mimic", "{1}{U}", "Creature — Shapeshifter", "Changeling (This card is every creature type.)", "1/1")
    }
    private val table = EngineTable()

    /** "This creature gets +1/+1 for each saddled Mount you control." (written in the definition model: the parser doesn't read
     * the wording yet) */
    private val marshal = arcanum.api.CardDefinition(arcanum.api.Layout.Normal, listOf(arcanum.api.FaceDefinition("Trail Marshal",
        arcanum.api.ManaCost.parse("{2}{R}"), types = listOf(arcanum.api.CardType.Creature),
        subtypes = listOf(Subtype("Human"), Subtype("Knight")), power = arcanum.api.PrintedNumber.Fixed(2),
        toughness = arcanum.api.PrintedNumber.Fixed(2), abilities = listOf(arcanum.api.Ability.Static(listOf(
            arcanum.api.Value.Count(arcanum.api.Objects.Each(arcanum.api.ObjectFilter.And(listOf(arcanum.api.ObjectFilter.Sub(Subtype("Mount")),
                arcanum.api.ObjectFilter.HasStatus(arcanum.api.Status.Marked(arcanum.api.ObjectMark.Saddled)),
                arcanum.api.ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.You))))).let { arcanum.api.Mechanic.ModifyPowerToughness(it, it) }),
            arcanum.api.Affected.Objects(arcanum.api.Objects.Self))))))

    private fun computed(state: GameState = table.state(), viewer: String = table.alice): ComputedBoard =
        ComputedBoard.of(table.board(state, viewer), table.base().definitions, table.definitions, viewer, state, Engine.view(state))

    // TBL-234, TBL-237: a card in the viewer's own hand makes its subtype a tag, on their own and the other player's permanents alike
    @Test fun aCardInYourHandTagsTheSubtypeOnBothSides() {
        table.put(table.alice, Zone.Hand, "Dunescale Chief")
        val own = table.put(table.alice, Zone.Battlefield, "Sunbaked Basker").single()
        val theirs = table.put(table.bob, Zone.Battlefield, "Sunbaked Basker").single()
        val goblin = table.put(table.bob, Zone.Battlefield, "Cinder Goblin").single()
        val board = computed()
        assertEquals(listOf("Lizard"), board.cards.getValue(own).tags)
        assertEquals(listOf("Lizard"), board.cards.getValue(theirs).tags, "control can change: the other player's Lizard too")
        assertEquals(emptyList(), board.cards.getValue(goblin).tags, "no card the viewer sees refers to Goblins")
    }

    // TBL-237: the other player's hand is hidden: a card there tags nothing for the viewer, but does for its owner
    @Test fun theOtherPlayersHandTagsNothing() {
        table.put(table.bob, Zone.Hand, "Goblin Warcaller")
        val goblin = table.put(table.alice, Zone.Battlefield, "Cinder Goblin").single()
        assertEquals(emptyList(), computed().cards.getValue(goblin).tags)
        assertEquals(setOf(Subtype("Goblin")), RelevantSubtypes.of(table.state(), Engine.view(table.state()), table.bob))
        assertEquals(listOf("Goblin"), computed(viewer = table.bob).cards.getValue(goblin).tags)
    }

    // TBL-237: a permanent on either battlefield counts for both players, the permanent itself included
    @Test fun aPermanentTagsForBothPlayers() {
        val chief = table.put(table.bob, Zone.Battlefield, "Dunescale Chief").single()
        val basker = table.put(table.alice, Zone.Battlefield, "Sunbaked Basker").single()
        for (viewer in listOf(table.alice, table.bob)) {
            val board = computed(viewer = viewer)
            assertEquals(listOf("Lizard"), board.cards.getValue(basker).tags, viewer)
            assertEquals(listOf("Lizard"), board.cards.getValue(chief).tags, viewer)
        }
    }

    // TBL-237: a spell on the Stack counts while it is there
    @Test fun aSpellOnTheStackTags() {
        val chief = table.put(table.bob, Zone.Hand, "Dunescale Chief").single()
        val basker = table.put(table.alice, Zone.Battlefield, "Sunbaked Basker").single()
        assertEquals(emptyList(), computed().cards.getValue(basker).tags, "in Bob's hand it is hidden from Alice")
        val before = table.state()
        val id = table.obj(chief)
        val spell = before.objects.getValue(id).copy(zone = Zone.Stack, controller = PlayerId(table.bob))
        val state = GameState.of(
            players = before.players.map { p -> p.copy(hand = p.hand - id) },
            objects = before.objects + (id to spell), battlefield = before.battlefield, stack = listOf(id),
            stackItems = mapOf(id to StackItem(id, StackItemKind.Spell, PlayerId(table.bob), id, null, timestamp = 99)),
            turn = before.turn, priority = before.priority, timestampCounter = 100)
        assertEquals(listOf("Lizard"), computed(state).cards.getValue(basker).tags, "cast, the Chief is seen on the Stack")
    }

    // TBL-234: "for each saddled Mount" tags Mounts while none is saddled
    @Test fun aSaddledMountTagsMountsBeforeAnyIsSaddled() {
        table.putDefinition(table.alice, Zone.Battlefield, marshal)
        val mount = table.put(table.alice, Zone.Battlefield, "Dustmane Charger").single()
        val theirs = table.put(table.bob, Zone.Battlefield, "Dustmane Charger").single()
        val board = computed()
        assertEquals(listOf("Mount"), board.cards.getValue(mount).tags)
        assertEquals(listOf("Mount"), board.cards.getValue(theirs).tags)
    }

    // TBL-233: each permanent's card types now
    @Test fun cardTypesAreTheTypesNow() {
        val plow = table.put(table.alice, Zone.Battlefield, "Brass Plow").single()
        val basker = table.put(table.alice, Zone.Battlefield, "Sunbaked Basker").single()
        val board = computed()
        assertEquals(listOf("Artifact", "Creature"), board.cards.getValue(plow).cardTypes)
        assertEquals(listOf("Creature"), board.cards.getValue(basker).cardTypes)
        assertEquals(emptyList(), board.cards.getValue(plow).tags, "nothing refers to Oxen")
    }

    // TBL-233, CR 613.1d: a type-changing effect shows in the types
    @Test fun aTypeChangingEffectShows() {
        val lantern = table.put(table.alice, Zone.Battlefield, "Tin Lantern").single()
        assertEquals(listOf("Artifact"), computed().cards.getValue(lantern).cardTypes)
        val aura = table.put(table.bob, Zone.Battlefield, "Clockwork Awakening").single()
        table.update(aura) { it.copy(attachedTo = arcanum.engine.state.TargetRef.Object(table.obj(lantern))) }
        assertEquals(listOf("Artifact", "Creature"), computed().cards.getValue(lantern).cardTypes)
    }

    // TBL-234, CR 702.73a: a changeling has every creature type, so it shows each relevant one
    @Test fun aChangelingShowsTheRelevantCreatureTypes() {
        table.put(table.alice, Zone.Hand, "Dunescale Chief")
        table.putDefinition(table.alice, Zone.Battlefield, marshal)
        val mimic = table.put(table.bob, Zone.Battlefield, "Mirror Mimic").single()
        assertEquals(listOf("Lizard", "Mount"), computed().cards.getValue(mimic).tags, "Mount is a creature type too")
    }
}
