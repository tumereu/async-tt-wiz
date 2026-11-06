package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The automatic battlefield layout (Arena style): rows, stacks, attachments, a group's tap and its glow. */
class BattlefieldLayoutTest {
    private val same = StackIdentity("forest", "f", false, "a", emptyList(), "", "", emptyList(), false, false)
    private fun card(id: String, row: BattlefieldRow = BattlefieldRow.Creatures, identity: StackIdentity? = same.copy(definition = id),
        attachedTo: String? = null, tapped: Boolean = false, field: String = "a/BATTLEFIELD") =
        BattlefieldCard(id, field, row, identity, attachedTo, tapped)

    // TBL-004: creatures nearest the middle; lands and other permanents in the back row; a permanent of several types by
    // the first in the shared order (SYS-051); face-down permanents with the creatures
    @Test fun rowsFollowTheFirstTypeInTheSharedOrder() {
        assertEquals(BattlefieldRow.Creatures, BattlefieldRow.of("Creature — Bear", false))
        assertEquals(BattlefieldRow.Creatures, BattlefieldRow.of("Artifact Creature — Golem", false))
        assertEquals(BattlefieldRow.Creatures, BattlefieldRow.of("Land Creature — Forest Dryad", false))
        assertEquals(BattlefieldRow.Lands, BattlefieldRow.of("Basic Land — Forest", false))
        assertEquals(BattlefieldRow.Others, BattlefieldRow.of("Artifact Land", false))
        assertEquals(BattlefieldRow.Others, BattlefieldRow.of("Legendary Enchantment", false))
        assertEquals(BattlefieldRow.Others, BattlefieldRow.of("Legendary Planeswalker — Vess", false))
        assertEquals(BattlefieldRow.Others, BattlefieldRow.of("Battle — Siege", false))
        assertEquals(BattlefieldRow.Creatures, BattlefieldRow.of("Basic Land — Forest", true))
        assertEquals(1, BattlefieldRow.Creatures.index)
        assertEquals(0, BattlefieldRow.Lands.index)
        assertEquals(0, BattlefieldRow.Others.index)
    }

    // TBL-053: identical permanents form one stack, tapped and untapped together; a difference that matters splits it;
    // a card that never stacks (face down, in combat, attached) stays alone; a stack is one battlefield's
    @Test fun identicalPermanentsStackAndDifferencesSplit() {
        val groups = BattlefieldLayout.groups(listOf(
            card("f1", BattlefieldRow.Lands, same), card("f2", BattlefieldRow.Lands, same, tapped = true),
            card("bear", identity = null), card("f3", BattlefieldRow.Lands, same.copy(counters = listOf(TypedCounter("charge", 1, 0)))),
            card("f4", BattlefieldRow.Lands, same), card("f5", BattlefieldRow.Lands, same, field = "b/BATTLEFIELD")))
        assertEquals(listOf(listOf("f1", "f2", "f4"), listOf("bear"), listOf("f3"), listOf("f5")), groups.map { it.members })
        assertEquals(BattlefieldGroup.Kind.Stack, groups[0].kind)
        assertEquals("stack:f1", groups[0].key, "a stack is known by its first card")
        assertEquals("f4", groups[0].top)
        assertEquals(listOf("f4", "f2", "f1"), groups[0].inspectOrder, "TBL-111: the top card first")
        assertEquals(listOf(listOf("f1", "f2", "f4")), groups[0].units, "identical cards mean one thing")
    }

    // TBL-053: auras and equipment lie with the card they are attached to (also one attached to an attachment); a card
    // with attachments never stacks with identical ones
    @Test fun attachmentsJoinTheirHostsGroup() {
        val groups = BattlefieldLayout.groups(listOf(
            card("bear1", identity = same), card("bear2", identity = same),
            card("blade", BattlefieldRow.Others, null, attachedTo = "bear1"),
            card("aura", BattlefieldRow.Others, null, attachedTo = "blade"),
            card("curse", BattlefieldRow.Others, same.copy(definition = "curse"), attachedTo = null)))
        assertEquals(listOf(listOf("bear1", "blade", "aura"), listOf("bear2"), listOf("curse")), groups.map { it.members })
        assertEquals(BattlefieldGroup.Kind.Attached, groups[0].kind)
        assertEquals("bear1", groups[0].top)
        assertEquals(listOf(listOf("bear1"), listOf("blade"), listOf("aura")), groups[0].units, "each card means its own")
        assertEquals(2, BattlefieldLayout.lifts(groups))
    }

    // TBL-004, TBL-002: creatures from the left; lands from the left and other permanents from the right end, the first
    // to arrive rightmost; each group takes its slot, a land stack fans, a tight stack carries ×N
    @Test fun rowsFillFromTheirEnds() {
        val groups = BattlefieldLayout.groups(listOf(
            card("cub"), card("f1", BattlefieldRow.Lands, same), card("rock", BattlefieldRow.Others),
            card("f2", BattlefieldRow.Lands, same), card("bear"), card("totem", BattlefieldRow.Others),
            card("t1", identity = same.copy(definition = "token")), card("t2", identity = same.copy(definition = "token")),
            card("t3", identity = same.copy(definition = "token"))))
        val spots = BattlefieldLayout.place(groups, rowWidth = 400f)
        val slot = BattlefieldLayout.SLOT
        assertEquals(0f, spots.getValue("cub").x); assertEquals(1, spots.getValue("cub").row)
        assertEquals(slot, spots.getValue("bear").x)
        assertEquals(2 * slot, spots.getValue("t1").x)
        assertEquals(2 * slot + BattlefieldLayout.TIGHT, spots.getValue("t2").x)
        assertEquals(2 * slot + 2 * BattlefieldLayout.TIGHT, spots.getValue("t3").x)
        assertEquals(3, spots.getValue("t3").copies, "the ×N badge on the top card")
        assertEquals(0, spots.getValue("t1").copies)
        assertEquals(0f, spots.getValue("f1").x); assertEquals(0, spots.getValue("f1").row)
        assertEquals(BattlefieldLayout.FAN, spots.getValue("f2").x, "lands fan out")
        assertEquals(0, spots.getValue("f2").copies, "a land stack shows its count by its fan")
        assertEquals(400f - slot, spots.getValue("rock").x, "the first other permanent rightmost")
        assertEquals(400f - 2 * slot, spots.getValue("totem").x)
        assertEquals(0, spots.getValue("rock").row)
        assertTrue(spots.getValue("t1").order < spots.getValue("t3").order, "a stack's later cards on top")
    }

    // SYS-041, TBL-004: a back row wider than the screen is not squeezed: its right end moves right
    @Test fun aFullBackRowGrowsToTheRight() {
        val lands = (1..8).map { card("l$it", BattlefieldRow.Lands, same.copy(definition = "l$it")) }
        val spots = BattlefieldLayout.place(BattlefieldLayout.groups(lands + card("rock", BattlefieldRow.Others)), rowWidth = 200f)
        assertEquals(9 * BattlefieldLayout.SLOT, spots.getValue("rock").x, "after the lands and a gap")
        assertEquals(7 * BattlefieldLayout.SLOT, spots.getValue("l8").x)
    }

    // TBL-053: attached cards lie behind their host, peeking out above it, the host drawn last
    @Test fun attachedCardsPeekOutAboveTheirHost() {
        val groups = BattlefieldLayout.groups(listOf(card("bear"), card("blade", BattlefieldRow.Others, null, attachedTo = "bear"),
            card("aura", BattlefieldRow.Others, null, attachedTo = "bear")))
        val spots = BattlefieldLayout.place(groups, 400f)
        assertEquals(listOf("aura", "blade", "bear"), spots.entries.sortedBy { it.value.order }.map { it.key })
        assertEquals(listOf(0, 1, 2), listOf("bear", "blade", "aura").map { spots.getValue(it).lift })
        assertTrue(listOf("bear", "blade", "aura").all { spots.getValue(it).x == 0f && spots.getValue(it).row == 1 && spots.getValue(it).lifts == 2 })
    }

    // TBL-122: an opened group lies with its members side by side and pushes the rest of its row along
    @Test fun anOpenedGroupLiesSideBySide() {
        val groups = BattlefieldLayout.groups(listOf(card("bear"), card("blade", BattlefieldRow.Others, null, attachedTo = "bear"),
            card("cub")))
        val spots = BattlefieldLayout.place(groups, 400f, expanded = "host:bear")
        val slot = BattlefieldLayout.SLOT
        assertEquals(listOf(0f, slot, 2 * slot), listOf("bear", "blade", "cub").map { spots.getValue(it).x })
        assertEquals(0, spots.getValue("blade").lift)
        assertEquals(slot, BattlefieldLayout.place(groups, 400f).getValue("cub").x, "closed, the host's slot only")
    }

    // TBL-122: a tap on a group acts at once when it can mean one thing (identical cards count as one, an untapped one
    // first), opens it when it could mean several, and is its top card's tap when nothing in it is usable
    @Test fun aGroupsTapActsAtOnceOrOpensIt() {
        val lands = BattlefieldGroup("stack:f1", "a", BattlefieldRow.Lands, listOf("f1", "f2", "f3"), BattlefieldGroup.Kind.Stack)
        val tapped = setOf("f1")
        assertEquals(GroupTap.Act("f2"), GroupTap.of(lands, { it !in tapped }, { it in tapped }))
        assertEquals(GroupTap.Act("f2"), GroupTap.of(lands, { true }, { it in tapped }), "an untapped one first")
        assertEquals(GroupTap.Top, GroupTap.of(lands, { false }, { false }))
        val equipped = BattlefieldGroup("host:bear", "a", BattlefieldRow.Creatures, listOf("bear", "blade"), BattlefieldGroup.Kind.Attached)
        assertEquals(GroupTap.Act("bear"), GroupTap.of(equipped, { it == "bear" }, { false }))
        assertEquals(GroupTap.Act("blade"), GroupTap.of(equipped, { it == "blade" }, { false }))
        assertEquals(GroupTap.Expand, GroupTap.of(equipped, { true }, { false }))
        assertEquals("bear", equipped.top)
    }

    // TBL-117, TBL-122: a collapsed group glows as a whole: cyan (or the target frame) when its tap acts at once, the
    // expand colour when it opens; cards alone glow as themselves
    @Test fun aGroupsGlowSaysWhatItsTapDoes() {
        val equipped = BattlefieldGroup("host:bear", "a", BattlefieldRow.Creatures, listOf("bear", "blade"), BattlefieldGroup.Kind.Attached)
        val stack = BattlefieldGroup("stack:t1", "a", BattlefieldRow.Creatures, listOf("t1", "t2"), BattlefieldGroup.Kind.Stack)
        val groups = listOf(equipped, stack)
        val expand = TableGlows.of(groups, mapOf(equipped.key to GroupTap.Expand, stack.key to GroupTap.Act("t2")),
            mapOf("bear" to 1f, "blade" to .5f, "t2" to .8f, "spark" to 1f), setOf("life/b"))
        assertEquals(mapOf("bear" to 1f, "blade" to 1f), expand.expand)
        assertEquals(mapOf("t1" to .8f, "t2" to .8f, "spark" to 1f), expand.playable)
        assertEquals(setOf("life/b"), expand.targets)
        val targets = TableGlows.of(groups, mapOf(equipped.key to GroupTap.Act("blade"), stack.key to GroupTap.Expand),
            emptyMap(), setOf("blade", "t1", "t2"))
        assertEquals(setOf("bear", "blade"), targets.targets, "the whole group framed: its tap picks the one target")
        assertEquals(mapOf("t1" to 1f, "t2" to 1f), targets.expand)
        val none = TableGlows.of(groups, mapOf(equipped.key to GroupTap.Top), emptyMap(), emptySet())
        assertTrue(none.expand.isEmpty() && none.playable.isEmpty())
    }

    // TBL-004, TBL-053: read from a board: the type line it has now, a face-down card alone in the creature row, an aura
    // with its host, and the same state stacking
    @Test fun cardsAreReadFromTheBoard() {
        val forest = CardDefinition("Forest", listOf(DefinitionFace("forest", "Forest", typeLine = "Basic Land — Forest")))
        val wings = CardDefinition("Wings", listOf(DefinitionFace("wings", "Wings", typeLine = "Enchantment — Aura")))
        val field = PlayContainer("a/BATTLEFIELD", PlayZone.BATTLEFIELD, "a", listOf("f1", "f2", "hidden", "wings"))
        fun play(id: String, definition: String, faceId: String) = PlayCard(id, definition, "a", field.id, faceId, covered = false)
        val board = BoardState(players = emptyList(), cards = listOf(play("f1", "forest", "forest"), play("f2", "forest", "forest").copy(tapped = true),
            play("hidden", "forest", "forest").copy(covered = true, explicitlyCovered = true),
            play("wings", "wings", "wings").copy(attachedTo = MtgRef.Card("f1", 0))), containers = listOf(field),
            turn = TurnState("a", "3"))
        val cards = BattlefieldLayout.cards(board, mapOf("forest" to forest, "wings" to wings), org.asyncttwiz.automatum.ComputedBoard.NONE)
        assertEquals(listOf(BattlefieldRow.Lands, BattlefieldRow.Lands, BattlefieldRow.Creatures, BattlefieldRow.Others), cards.map { it.row })
        assertEquals(cards[0].identity, cards[1].identity, "tapped or not, the same")
        assertNull(cards[2].identity, "a face-down card never stacks")
        assertEquals("f1", cards[3].attachedTo)
        assertEquals(listOf(listOf("f1", "wings"), listOf("f2"), listOf("hidden")), BattlefieldLayout.groups(cards).map { it.members })
    }
}
