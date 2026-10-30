package org.asyncttwiz.protocol

import kotlin.test.*

class HistoryGroupingTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun semantic(category: HistoryCategory, key: String = "a") = HistorySemantic(when (category) {
        HistoryCategory.T, HistoryCategory.U -> HistorySignificance.ORIENTATION; HistoryCategory.M -> HistorySignificance.CROSS_ZONE_MOVE
        HistoryCategory.I -> HistorySignificance.STACK_ENTRY; HistoryCategory.E -> HistorySignificance.CARD_TEXT_NOTE_EDIT
        HistoryCategory.C -> HistorySignificance.TOKEN_CLONE_CREATION; HistoryCategory.K -> HistorySignificance.COMBAT_MEMBERSHIP
        HistoryCategory.L -> HistorySignificance.STACK_LAYOUT; HistoryCategory.V -> HistorySignificance.EXPLICIT_DISCLOSURE
        HistoryCategory.H -> HistorySignificance.SHUFFLE; HistoryCategory.F -> HistorySignificance.COMBAT_CLOSURE
        HistoryCategory.R -> HistorySignificance.DIE_COIN_RUN; HistoryCategory.P -> HistorySignificance.PING
        HistoryCategory.B, HistoryCategory.O -> HistorySignificance.EVENT_COMMENT; HistoryCategory.S -> HistorySignificance.ENDING_SUGGESTION
    }, HistoryIcon.FLAG, if (category in setOf(HistoryCategory.M, HistoryCategory.H)) key else null)
    private fun append(group: GameplayGroup?, category: HistoryCategory, n: Int, author: String = id(1000), key: String = "a") =
        HistoryGrouping.append(group, id(n), id(n + 100), author, semantic(category, key))

    @Test fun complete182CellMatrixWithBothKeyAndTapPurityVariantsAndAuthorOverride() {
        val columns = "T M I E C K L V H F R P B S".split(' ')
        val matrix = mapOf(
            "T" to "t + + + + + + + + + + + + |", "M" to "| k | | | | | | | | | + + |",
            "I" to "| | | | | | | | | | | + + |", "E" to "| | | + | | | | | | | + + |",
            "C" to "| | | + + | | | | | | + + |", "K" to "| | | | | + | | | | | + + |",
            "L" to "| | | | | | + | | | | + + |", "V" to "| | | | | | | | | | | + + |",
            "H" to "| | | | | | | | k | | + + |", "F" to "| + + + + + + + + + + + + |",
            "R" to "| + + + + + + + + + + + + |", "O" to "t | | | | | | | | | | + + |",
            "S" to "| | | | | | | | | | | | | |")
        assertEquals(182, matrix.size * columns.size)
        for ((row, cells) in matrix) for ((column, cell) in columns.zip(cells.split(' '))) {
            for (pure in if (row in listOf("T", "O")) listOf(false, true) else listOf(false)) for (matching in listOf(false, true)) {
                val category = HistoryCategory.valueOf(row)
                val base = append(null, if (category == HistoryCategory.O) HistoryCategory.P else category, 1)
                    .copy(tapsAndPingsOnly = pure)
                val next = append(base, HistoryCategory.valueOf(column), 2, key = if (matching) "a" else "b")
                val expected = when (cell) { "+" -> true; "k" -> matching; "t" -> pure; else -> false }
                assertEquals(expected, base.id == next.id, "$row/$column pure=$pure matching=$matching")
                assertNotEquals(base.id, append(base, HistoryCategory.valueOf(column), 2, id(1001)).id, "author $row/$column")
            }
        }
    }

    /** Category fixtures for absent parallel-lane tools are NOT actual accepted UI action coverage. */
    @Test fun gm01Through19And23And24EveryPrefixWithoutRetroactiveMarkerChanges() {
        val traces = listOf(
            "GM-01" to "Ma | T Ma", "GM-02" to "E | T E", "GM-03" to "Ma P | T P T I",
            "GM-04" to "T P T P Ma | T Ma", "GM-05" to "Ma P | T P P Ma",
            "GM-06-comment" to "T B | T Ma", "GM-06-tracking" to "T B | T Ma",
            "GM-07" to "Ma P Ma | Mb", "GM-08" to "T I P | I | I",
            "GM-09" to "C E C E | Ma", "GM-09-reverse" to "E | C",
            "GM-10" to "K K K K | F Ma", "GM-11" to "I | L L | I",
            "GM-12" to "Ma | V | V | F B I", "GM-13" to "E | R R B E",
            "GM-14" to "Ha P Ha | Hb", "GM-15" to "V | F | T Ma",
            "GM-16" to "P T P T Ma", "GM-17" to "B | T Ma",
            "GM-18" to "T P | P@other | Ma", "GM-19-no-unlogged-input" to "T P T Ma",
            "GM-23" to "E | S | P B", "GM-24-tap" to "T | S | E",
            "GM-24-random" to "R | S", "GM-24-closure" to "V | F | S")
        for ((name, trace) in traces) {
            var group: GameplayGroup? = null; var boundary = true; var n = 1
            val prefixGroups = mutableListOf<String>(); val starts = mutableListOf<String>()
            for (token in trace.split(' ')) {
                if (token == "|") { boundary = true; continue }
                val previous = group
                group = append(group, HistoryCategory.valueOf(token.take(1)), n,
                    if ('@' in token) id(1001) else id(1000), token.drop(1).ifEmpty { "a" })
                if (boundary) starts += group.id
                assertEquals(boundary, previous?.id != group.id, "$name prefix $n")
                assertEquals(if (boundary) id(n) else previous!!.firstNodeId, group.firstNodeId, "$name marker $n")
                prefixGroups += group.id
                assertEquals(starts.distinct(), prefixGroups.distinct(), "$name existing prefixes never relocate")
                assertEquals(id(n), group.lastNodeId)
                boundary = false; n++
            }
        }
    }

    @Test fun literalD299RanksAndPrefixTieFixtures() {
        assertEquals(listOf(10,20,20,30,40,50,60,70,80,90,100,110,120,130,140,150,160,170,180),
            HistorySignificance.entries.filter { it.anchor }.map { it.rank })
        // 95: a step passed inside a multi-step advance (rules v33).
        assertEquals(listOf(10,20,30,40,50,60,70,80,90,95,100), HistorySignificance.entries.filter { !it.anchor }.map { it.rank })
        fun check(kinds: List<HistorySignificance>, icons: List<String>, expected: List<Int>) {
            var representative: HistoryRepresentative? = null
            kinds.forEachIndexed { index, kind ->
                val event = HistoryRepresentative(id(index + 1), HistorySemantic(kind, HistoryIcon.fromWire(icons[index]),
                    if (kind.category in setOf(HistoryCategory.M, HistoryCategory.H)) "a" else null))
                representative = HistoryGrouping.choose(representative, event)
                assertEquals(id(expected[index]), representative!!.nodeId)
                assertEquals(icons[expected[index] - 1], representative!!.semantic.icon.wire)
            }
        }
        check(listOf(HistorySignificance.ORIENTATION, HistorySignificance.ORIENTATION, HistorySignificance.STACK_ENTRY,
            HistorySignificance.PING, HistorySignificance.PHASE_STEP_TRACKING),
            listOf("rotate_90_degrees_cw", "rotate_90_degrees_cw", "layers", "ads_click", "outlined_flag"), listOf(1,1,3,3,3))
        check(listOf(HistorySignificance.ORIENTATION, HistorySignificance.COMBAT_MEMBERSHIP), listOf("rotate_90_degrees_cw", "swords"), listOf(1,2))
        check(listOf(HistorySignificance.CARD_CORNER_COUNTER_EDIT, HistorySignificance.LIFE_PLAYER_COUNTER_EDIT), listOf("exposure_plus_1", "favorite"), listOf(1,2))
        check(listOf(HistorySignificance.TOKEN_CLONE_CREATION, HistorySignificance.CARD_CORNER_COUNTER_EDIT,
            HistorySignificance.OWNER_EDIT, HistorySignificance.TOKEN_CLONE_CREATION),
            listOf("add_box", "exposure_plus_1", "switch_account", "control_point_duplicate"), listOf(1,1,1,1))
        check(listOf(HistorySignificance.COMBAT_CLOSURE, HistorySignificance.DIE_COIN_RUN,
            HistorySignificance.EVENT_COMMENT, HistorySignificance.CROSS_ZONE_MOVE),
            listOf("layers_clear", "casino", "chat_bubble", "open_with"), listOf(1,1,1,4))
        check(listOf(HistorySignificance.PHASE_STEP_TRACKING, HistorySignificance.TURN_TRANSITION,
            HistorySignificance.EVENT_COMMENT, HistorySignificance.PING),
            listOf("outlined_flag", "outlined_flag", "chat_bubble", "ads_click"), listOf(1,2,2,2))
        check(listOf(HistorySignificance.CARD_TEXT_NOTE_EDIT, HistorySignificance.TOKEN_REMOVAL), listOf("sticky_note_2", "delete"), listOf(1,2))
        check(listOf(HistorySignificance.ENDING_SUGGESTION), listOf("emoji_events"), listOf(1))
        check(listOf(HistorySignificance.CROSS_ZONE_MOVE, HistorySignificance.CROSS_ZONE_MOVE), listOf("download", "download"), listOf(1,1))
        check(listOf(HistorySignificance.CROSS_ZONE_MOVE, HistorySignificance.CROSS_ZONE_MOVE), listOf("open_with", "open_with"), listOf(1,1))
        check(listOf(HistorySignificance.EXPLICIT_DISCLOSURE, HistorySignificance.EVENT_COMMENT), listOf("visibility", "chat_bubble"), listOf(1,1))
    }

    @Test fun mergedManualTokenCombatAndStackCatalogMapping() {
        fun expect(kind: HistorySignificance, icon: String, vararg actions: PlayAction) {
            for (action in actions) assertEquals(HistorySemantic(kind, HistoryIcon.fromWire(icon)), HistoryGrouping.classify(action), "$action")
        }
        expect(HistorySignificance.CARD_CORNER_COUNTER_EDIT, "exposure_plus_1",
            PlayAction.SetCorner(id(1), "3/4"), PlayAction.AdjustCorner(id(1), CornerPart.LEFT, 1),
            PlayAction.SetCounter(CounterTarget.CARD, id(1), CounterColor.BLUE, "0"),
            PlayAction.AdjustCounter(CounterTarget.CARD, id(1), CounterColor.BLUE, "1"))
        expect(HistorySignificance.CARD_CORNER_COUNTER_EDIT, "settings_backup_restore", PlayAction.ResetCorner(id(1)))
        expect(HistorySignificance.LIFE_PLAYER_COUNTER_EDIT, "exposure_plus_1",
            PlayAction.SetCounter(CounterTarget.PLAYER, id(1), CounterColor.BLUE, "0"),
            PlayAction.AdjustCounter(CounterTarget.PLAYER, id(1), CounterColor.BLUE, "1"))
        expect(HistorySignificance.CARD_TEXT_NOTE_EDIT, "sticky_note_2", PlayAction.SetNote(id(1), "arbitrary text"))
        expect(HistorySignificance.OWNER_EDIT, "switch_account", PlayAction.SetOwner(id(1), id(2)))
        expect(HistorySignificance.FACE_COVER_EDIT, "flip_camera_android", PlayAction.SetFace(id(1), id(2)))
        expect(HistorySignificance.FACE_COVER_EDIT, "flip", PlayAction.SetCovered(id(1), true))
        expect(HistorySignificance.FACE_COVER_EDIT, "flip_to_front", PlayAction.SetCovered(id(1), false))
        expect(HistorySignificance.EXPLICIT_DISCLOSURE, "visibility", PlayAction.InspectFace(id(1), id(2), id(3)))
        expect(HistorySignificance.TOKEN_CLONE_CREATION, "add_box", PlayAction.CreateToken(TokenCatalog.blank))
        expect(HistorySignificance.TOKEN_CLONE_CREATION, "control_point_duplicate", PlayAction.CloneToken(id(1)))
        expect(HistorySignificance.TOKEN_REMOVAL, "delete", PlayAction.RemoveToken(id(1)))
        expect(HistorySignificance.LINK_EDIT, "link", PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, id(1)),
            LinkEndpoint(LinkEndpointKind.PLAYER, id(2))))
        expect(HistorySignificance.LINK_EDIT, "link_off", PlayAction.RemoveLink(id(1)))
        expect(HistorySignificance.COMBAT_AREA_CREATION, "swords", PlayAction.Combat(CombatAction.Open))
        expect(HistorySignificance.COMBAT_CLOSURE, "layers_clear", PlayAction.Combat(CombatAction.Close(id(1))))
        expect(HistorySignificance.COMBAT_MEMBERSHIP, "swords", PlayAction.Combat(CombatAction.Declare(id(1), id(2))))
        expect(HistorySignificance.COMBAT_MEMBERSHIP, "remove_circle_outline", PlayAction.Combat(CombatAction.Remove(id(1), id(2))))
        val destination = CombatDestination(id(1), CombatRole.ATTACKER, id(2))
        val location = CombatLocation(id(1), id(3), CombatRole.BLOCKER, IdentityGap())
        expect(HistorySignificance.COMBAT_MEMBERSHIP, "open_with",
            PlayAction.Combat(CombatAction.Place(id(4), destination)),
            PlayAction.Combat(CombatAction.Place(id(4), destination, location)))
        expect(HistorySignificance.COMBAT_LAYOUT, "open_with",
            PlayAction.Combat(CombatAction.Place(id(4), destination, location.copy(role = CombatRole.ATTACKER))))
        expect(HistorySignificance.STACK_ENTRY, "layers", PlayAction.Stack(StackAction.AddEffect(id(1), "sha256:" + "a".repeat(64))))
        expect(HistorySignificance.STACK_REMOVAL, "layers_clear", PlayAction.Stack(StackAction.RemoveEffect(id(1))))
        expect(HistorySignificance.STACK_LAYOUT, "open_with", PlayAction.Stack(StackAction.ReorderEffect(id(1), IdentityGap(), IdentityGap())))
    }

    @Test fun actualCatalogMappingAndServerDerivedAppend() {
        val containers = PlayZone.entries.mapIndexed { i, zone -> PlayContainer(id(i + 20), zone) }
        fun move(from: PlayZone, to: PlayZone) = PlayAction.MoveCard(id(10), MoveSource(containers.single { it.zone == from }.id),
            MoveDestination(containers.single { it.zone == to }.id, MoveInsertion.UNORDERED))
        for (from in PlayZone.entries) for (to in PlayZone.entries) {
            val semantic = HistoryGrouping.classify(move(from, to), containers)
            // Same-Stack movement is layout, not an independent entry/removal (D-175).
            val significance = when {
                from == PlayZone.STACK && to == PlayZone.STACK -> HistorySignificance.STACK_LAYOUT
                from == PlayZone.STACK -> HistorySignificance.STACK_REMOVAL
                to == PlayZone.STACK -> HistorySignificance.STACK_ENTRY
                from == to -> HistorySignificance.SAME_ZONE_PLACEMENT
                else -> HistorySignificance.CROSS_ZONE_MOVE
            }
            assertEquals(significance.category, semantic.category, "$from -> $to")
            assertEquals(significance, semantic.significance, "$from -> $to")
            assertEquals(if (significance.category == HistoryCategory.I) "layers" else "open_with", semantic.icon.wire, "$from -> $to")
            assertEquals(if (significance.category == HistoryCategory.M)
                "${containers.single { it.zone == from }.id}/${containers.single { it.zone == to }.id}" else null,
                semantic.key, "$from -> $to")
        }
        val actions = listOf(PlayAction.SetTapped(id(10), true), PlayAction.SetLife(id(10), "21"), PlayAction.AdjustLife(id(10), "1"),
            PlayAction.EventComment(EventReference(id(1), id(2)), " note "), PlayAction.Ping(EventReference(id(1), id(2)), PingTarget(PingTargetKind.PLAYER, id(10))),
            PlayAction.Unping(EventReference(id(1), id(2)), PingTarget(PingTargetKind.PLAYER, id(10))))
        assertEquals(listOf(HistoryCategory.T, HistoryCategory.E, HistoryCategory.E, HistoryCategory.B, HistoryCategory.P, HistoryCategory.P),
            actions.map { HistoryGrouping.classify(it).category })
        val merged = listOf(
            PlayAction.SetPlayerCounter(id(10), CounterColor.RED, "1") to HistorySemantic(HistorySignificance.LIFE_PLAYER_COUNTER_EDIT, HistoryIcon.EXPOSURE_PLUS_1),
            PlayAction.AdjustPlayerCounter(id(10), CounterColor.RED, "1") to HistorySemantic(HistorySignificance.LIFE_PLAYER_COUNTER_EDIT, HistoryIcon.EXPOSURE_PLUS_1),
            PlayAction.NextStep to HistorySemantic(HistorySignificance.PHASE_STEP_TRACKING, HistoryIcon.OUTLINED_FLAG),
            PlayAction.EndTurn to HistorySemantic(HistorySignificance.TURN_TRANSITION, HistoryIcon.OUTLINED_FLAG),
            PlayAction.ChooseStep(TurnPosition.DRAW) to HistorySemantic(HistorySignificance.PHASE_STEP_TRACKING, HistoryIcon.OUTLINED_FLAG),
            PlayAction.RollDie(6, 3) to HistorySemantic(HistorySignificance.DIE_COIN_RUN, HistoryIcon.CASINO),
            PlayAction.FlipCoin(CoinFace.HEADS) to HistorySemantic(HistorySignificance.DIE_COIN_RUN, HistoryIcon.MONETIZATION_ON),
            PlayAction.ShuffleLibrary(id(10), RandomV1.encodeSeed(ByteArray(32))) to HistorySemantic(HistorySignificance.SHUFFLE, HistoryIcon.SHUFFLE, id(10)),
            PlayAction.ShowContents(id(10), ContentsSelection.TOP) to HistorySemantic(HistorySignificance.EXPLICIT_DISCLOSURE, HistoryIcon.PAGEVIEW),
            PlayAction.ShowContents(id(10), ContentsSelection.BOTTOM, reveal = true) to HistorySemantic(HistorySignificance.EXPLICIT_DISCLOSURE, HistoryIcon.VISIBILITY),
            PlayAction.DismissContents(id(10)) to HistorySemantic(HistorySignificance.VIEW_REVEAL_DISMISSAL, HistoryIcon.CLOSE))
        for ((action, expected) in merged) assertEquals(expected, HistoryGrouping.classify(action))
        val mutation = GameplayMutation(id(1), id(2), id(3), id(4), id(5), null, actions.first(), GameplayTransition())
        assertEquals(CommandIds.uuidV5(id(4), "group/0"), GameplayHistory.append(null, mutation).id)
        assertFails { HistoryGrouping.classify(move(PlayZone.STACK, PlayZone.HAND)) }
    }
}
