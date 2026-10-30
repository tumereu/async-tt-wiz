package org.asyncttwiz.protocol

import kotlin.test.*

/** Rules v34: an edit's history icon shows the direction of the change; step, turn, view-close, combat-close and
 * attack-target events get their own icons. Grouping is unchanged and older events keep their recorded icons. */
class R34DirectionIconsTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private val a = id(7); private val b = id(8); private val card = id(20)
    private fun field(field: GameplayField, from: String?, to: String?, target: String = card, color: CounterColor? = null) =
        GameplayFieldChange(target, field, from, to, color)
    private fun classify(action: PlayAction, rules: Int = 34, vararg fields: GameplayFieldChange) =
        HistoryGrouping.classify(action, emptyList(), GameplayTransition(fields.toList(), rulesVersion = rules))
    private fun icon(action: PlayAction, vararg fields: GameplayFieldChange) = classify(action, 34, *fields).icon

    @Test fun directionIconsAreRulesVersion34() {
        assertEquals(34, HistoryGrouping.DIRECTION_ICONS_RULES_VERSION)
        assertEquals(HistoryGrouping.DIRECTION_ICONS_RULES_VERSION, CURRENT_GAMEPLAY_RULES_VERSION)
        assertTrue(HistoryGrouping.STEP_RUN_RULES_VERSION < HistoryGrouping.DIRECTION_ICONS_RULES_VERSION)
    }

    @Test fun lifeGainIsAHeartAndLossABrokenHeart() {
        assertEquals(HistoryIcon.FAVORITE, icon(PlayAction.AdjustLife(a, "+3")))
        assertEquals(HistoryIcon.HEART_BROKEN, icon(PlayAction.AdjustLife(a, "-2")))
        assertEquals(HistoryIcon.FAVORITE, icon(PlayAction.SetLife(a, "25"), field(GameplayField.LIFE, "20", "25", a)))
        assertEquals(HistoryIcon.HEART_BROKEN, icon(PlayAction.SetLife(a, "7"), field(GameplayField.LIFE, "20", "7", a)))
        // Text life cannot be compared: it reads as a gain.
        assertEquals(HistoryIcon.FAVORITE, icon(PlayAction.SetLife(a, "lots"), field(GameplayField.LIFE, "20", "lots", a)))
    }

    @Test fun cardCountersCountUpDownAndClear() {
        assertEquals(HistoryIcon.EXPOSURE_PLUS_1, icon(PlayAction.AdjustCounter(CounterTarget.CARD, card, CounterColor.GREEN, "1")))
        assertEquals(HistoryIcon.EXPOSURE_NEG_1, icon(PlayAction.AdjustCounter(CounterTarget.CARD, card, CounterColor.GREEN, "-1")))
        assertEquals(HistoryIcon.EXPOSURE_PLUS_1, icon(PlayAction.SetCounter(CounterTarget.CARD, card, CounterColor.GREEN, "4"),
            field(GameplayField.COUNTER, null, "4", color = CounterColor.GREEN)))
        assertEquals(HistoryIcon.EXPOSURE_NEG_1, icon(PlayAction.SetCounter(CounterTarget.CARD, card, CounterColor.GREEN, "1"),
            field(GameplayField.COUNTER, "3", "1", color = CounterColor.GREEN)))
        // Emptying the value removes the counter: a decrease.
        assertEquals(HistoryIcon.EXPOSURE_NEG_1, icon(PlayAction.SetCounter(CounterTarget.CARD, card, CounterColor.GREEN, ""),
            field(GameplayField.COUNTER, "3", null, color = CounterColor.GREEN)))
        assertEquals(HistoryIcon.EXPOSURE_ZERO, icon(PlayAction.ClearCardCounter(card, CounterColor.GREEN),
            field(GameplayField.COUNTER, "3", null, color = CounterColor.GREEN)))
    }

    @Test fun playerCountersPointUpOrDown() {
        assertEquals(HistoryIcon.ARROW_CIRCLE_UP, icon(PlayAction.AdjustPlayerCounter(a, CounterColor.RED, "1")))
        assertEquals(HistoryIcon.ARROW_CIRCLE_DOWN, icon(PlayAction.AdjustPlayerCounter(a, CounterColor.RED, "-1")))
        assertEquals(HistoryIcon.ARROW_CIRCLE_UP, icon(PlayAction.SetPlayerCounter(a, CounterColor.RED, "3"),
            field(GameplayField.COUNTER, "1", "3", a, CounterColor.RED)))
        assertEquals(HistoryIcon.ARROW_CIRCLE_DOWN, icon(PlayAction.SetPlayerCounter(a, CounterColor.RED, ""),
            field(GameplayField.COUNTER, "1", null, a, CounterColor.RED)))
        assertEquals(HistoryIcon.ARROW_CIRCLE_UP, icon(PlayAction.AdjustCounter(CounterTarget.PLAYER, b, CounterColor.GRAY, "2")))
        assertEquals(HistoryIcon.ARROW_CIRCLE_DOWN, icon(PlayAction.AdjustCounter(CounterTarget.PLAYER, b, CounterColor.GRAY, "-2")))
        assertEquals(HistoryIcon.ARROW_CIRCLE_DOWN, icon(PlayAction.SetCounter(CounterTarget.PLAYER, b, CounterColor.GRAY, "0"),
            field(GameplayField.PLAYER_COUNTER, "5", "0", b, CounterColor.GRAY)))
    }

    @Test fun powerToughnessTrendsByTheSumOfBothSides() {
        assertEquals(HistoryIcon.TRENDING_UP, icon(PlayAction.AdjustCorner(card, CornerPart.BOTH, 1)))
        assertEquals(HistoryIcon.TRENDING_DOWN, icon(PlayAction.AdjustCorner(card, CornerPart.LEFT, -1)))
        assertEquals(HistoryIcon.TRENDING_UP, icon(PlayAction.AdjustEotCorner(card, CornerPart.RIGHT, 1)))
        assertEquals(HistoryIcon.TRENDING_DOWN, icon(PlayAction.AdjustEotCorner(card, CornerPart.BOTH, -1)))
        assertEquals(HistoryIcon.TRENDING_UP, icon(PlayAction.SetCorner(card, "4/4"), field(GameplayField.CORNER, "2/2", "4/4")))
        assertEquals(HistoryIcon.TRENDING_DOWN, icon(PlayAction.SetCorner(card, "1/1"), field(GameplayField.CORNER, "2/2", "1/1")))
        // +2/-1 sums to +1: up; +1/-3 sums to -2: down; a swap sums to 0 and reads as up.
        assertEquals(HistoryIcon.TRENDING_UP, icon(PlayAction.SetCorner(card, "4/1"), field(GameplayField.CORNER, "2/2", "4/1")))
        assertEquals(HistoryIcon.TRENDING_DOWN, icon(PlayAction.SetCorner(card, "3/-1"), field(GameplayField.CORNER, "2/2", "3/-1")))
        assertEquals(HistoryIcon.TRENDING_UP, icon(PlayAction.SetCorner(card, "3/2"), field(GameplayField.CORNER, "2/3", "3/2")))
        // A single number (loyalty) compares as itself; an unreadable value reads as up.
        assertEquals(HistoryIcon.TRENDING_DOWN, icon(PlayAction.SetCorner(card, "2"), field(GameplayField.CORNER, "5", "2")))
        assertEquals(HistoryIcon.TRENDING_UP, icon(PlayAction.SetCorner(card, "*/*"), field(GameplayField.CORNER, "2/2", "*/*")))
        assertEquals(HistoryIcon.TRENDING_UP, icon(PlayAction.SetEotCorner(card, "+2/+0"), field(GameplayField.EOT_CORNER, "", "+2/+0")))
        assertEquals(HistoryIcon.TRENDING_DOWN, icon(PlayAction.SetEotCorner(card, "-3/-3"), field(GameplayField.EOT_CORNER, "", "-3/-3")))
        assertEquals(HistoryIcon.TRENDING_DOWN, icon(PlayAction.SetEotCorner(card, ""), field(GameplayField.EOT_CORNER, "+1/+1", "")))
    }

    @Test fun damageBurnsAndHealing() {
        assertEquals(HistoryIcon.LOCAL_FIRE_DEPARTMENT, icon(PlayAction.AdjustDamage(card, "2")))
        assertEquals(HistoryIcon.HEALING, icon(PlayAction.AdjustDamage(card, "-1")))
        assertEquals(HistoryIcon.LOCAL_FIRE_DEPARTMENT, icon(PlayAction.SetDamage(card, "3"), field(GameplayField.DAMAGE, "", "3")))
        assertEquals(HistoryIcon.HEALING, icon(PlayAction.SetDamage(card, ""), field(GameplayField.DAMAGE, "3", "")))
    }

    @Test fun stepsTurnsAndClosings() {
        val turn = field(GameplayField.TURN_PLAYER, a, b, id(4))
        val step = field(GameplayField.STEP, TurnPosition.UPKEEP.step, TurnPosition.DRAW.step, id(4))
        assertEquals(HistoryIcon.ARROW_FORWARD, icon(PlayAction.NextStep, step))
        assertEquals(HistoryIcon.SKIP_NEXT, icon(PlayAction.NextStep, turn))
        assertEquals(HistoryIcon.SKIP_NEXT, icon(PlayAction.EndTurn, turn))
        assertEquals(HistoryIcon.ARROW_FORWARD, icon(PlayAction.ChooseStep(TurnPosition.DRAW), step))
        assertEquals(HistoryIcon.ARROW_FORWARD, icon(PlayAction.ChooseStep(TurnPosition.DRAW, through = true), step))
        assertEquals(HistoryIcon.OUTLINED_FLAG, icon(PlayAction.PassAttention))
        assertEquals(HistoryIcon.VISIBILITY_OFF, icon(PlayAction.DismissContents(id(30))))
        assertEquals(HistoryIcon.CLOSE, icon(PlayAction.DeclineResult(id(31))))
        assertEquals(HistoryIcon.CLOSE, icon(PlayAction.WithdrawResult(id(31))))
        assertEquals(HistoryIcon.CHECK, icon(PlayAction.Combat(CombatAction.Close(id(32)))))
        assertEquals(HistoryIcon.CHECK, icon(PlayAction.Combat(CombatAction.Close(null))))
        assertEquals(HistoryIcon.GPS_FIXED, icon(PlayAction.Combat(CombatAction.SetAttackTarget(id(32), id(33), b))))
        assertEquals(HistoryIcon.LAYERS_CLEAR, icon(PlayAction.Stack(StackAction.RemoveEffect(id(34)))))
        assertEquals(HistoryIcon.SWORDS, icon(PlayAction.Combat(CombatAction.Declare(id(32), card))))
    }

    private fun gated(): List<Pair<PlayAction, List<GameplayFieldChange>>> = listOf(
        PlayAction.AdjustLife(a, "-2") to emptyList(),
        PlayAction.SetLife(a, "7") to listOf(field(GameplayField.LIFE, "20", "7", a)),
        PlayAction.AdjustCounter(CounterTarget.CARD, card, CounterColor.GREEN, "-1") to emptyList(),
        PlayAction.ClearCardCounter(card, CounterColor.GREEN) to listOf(field(GameplayField.COUNTER, "3", null, color = CounterColor.GREEN)),
        PlayAction.AdjustPlayerCounter(a, CounterColor.RED, "1") to emptyList(),
        PlayAction.AdjustCounter(CounterTarget.PLAYER, b, CounterColor.GRAY, "-2") to emptyList(),
        PlayAction.AdjustCorner(card, CornerPart.BOTH, 1) to emptyList(),
        PlayAction.SetEotCorner(card, "-3/-3") to listOf(field(GameplayField.EOT_CORNER, "", "-3/-3")),
        PlayAction.AdjustDamage(card, "2") to emptyList(),
        PlayAction.SetDamage(card, "") to listOf(field(GameplayField.DAMAGE, "3", "")),
        PlayAction.NextStep to listOf(field(GameplayField.TURN_PLAYER, a, b, id(4))),
        PlayAction.NextStep to listOf(field(GameplayField.STEP, TurnPosition.UPKEEP.step, TurnPosition.DRAW.step, id(4))),
        PlayAction.EndTurn to listOf(field(GameplayField.TURN_PLAYER, a, b, id(4))),
        PlayAction.ChooseStep(TurnPosition.DRAW) to emptyList(),
        PlayAction.DismissContents(id(30)) to emptyList(),
        PlayAction.Combat(CombatAction.Close(id(32))) to emptyList(),
        PlayAction.Combat(CombatAction.SetAttackTarget(id(32), id(33), b)) to emptyList())

    @Test fun olderEventsKeepTheirRecordedIconsAndOnlyTheIconChanges() {
        val old = mapOf(PlayAction.AdjustLife(a, "-2") to HistoryIcon.FAVORITE, PlayAction.NextStep to HistoryIcon.OUTLINED_FLAG,
            PlayAction.DismissContents(id(30)) to HistoryIcon.CLOSE, PlayAction.Combat(CombatAction.Close(id(32))) to HistoryIcon.LAYERS_CLEAR,
            PlayAction.Combat(CombatAction.SetAttackTarget(id(32), id(33), b)) to HistoryIcon.SWORDS,
            PlayAction.AdjustDamage(card, "2") to HistoryIcon.EXPOSURE_PLUS_1, PlayAction.EndTurn to HistoryIcon.OUTLINED_FLAG)
        for ((action, icon) in old) assertEquals(icon, classify(action, 33).icon, "$action")
        for ((action, fields) in gated()) {
            val before = classify(action, 33, *fields.toTypedArray())
            val now = classify(action, 34, *fields.toTypedArray())
            assertNotEquals(before.icon, now.icon, "$action")
            // Significance, key, isolation and every other grouping input stay as recorded under v33.
            assertEquals(before.copy(icon = now.icon), now, "$action")
            // A stored v33 grouping still equals its replayed classification (readsMatch).
            val stored = GameWire.json.decodeFromString(HistorySemantic.serializer(),
                GameWire.json.encodeToString(HistorySemantic.serializer(), before))
            assertEquals(stored, classify(action, 33, *fields.toTypedArray()))
        }
    }

    /** Real accepted events: each records its direction icon and replays (readsMatch) unchanged. */
    @Test fun acceptedEditsRecordTheirDirectionAndReplay() {
        val definition = CardDefinition("Bear", listOf(DefinitionFace(id(40), "Bear", corner = "2/2")))
        val bear = PlayCard(card, "definition", a, id(14), id(40), covered = false, corner = "2/2",
            counters = mapOf(CounterColor.GREEN to "3"), placement = PlayPlacement(BoardPosition(0, 0), id(41)))
        var state = PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5),
            BoardState(listOf(PlayPlayer(a), PlayPlayer(b)), listOf(bear),
                listOf(PlayContainer(id(14), PlayZone.BATTLEFIELD, a, listOf(card))), TurnState(a)),
            listOf(SubmittedDefinition("definition", definition)), emptyList(), InitialStarter(StarterChoice(a), a))
        var request = 100
        val expected = listOf(PlayAction.SetLife(a, "17") to HistoryIcon.HEART_BROKEN, PlayAction.SetLife(a, "19") to HistoryIcon.FAVORITE,
            PlayAction.SetPlayerCounter(b, CounterColor.RED, "2") to HistoryIcon.ARROW_CIRCLE_UP,
            PlayAction.SetPlayerCounter(b, CounterColor.RED, "1") to HistoryIcon.ARROW_CIRCLE_DOWN,
            PlayAction.SetCounter(CounterTarget.PLAYER, b, CounterColor.RED, "0") to HistoryIcon.ARROW_CIRCLE_DOWN,
            PlayAction.SetCounter(CounterTarget.CARD, card, CounterColor.GREEN, "1") to HistoryIcon.EXPOSURE_NEG_1,
            PlayAction.AdjustCounter(CounterTarget.CARD, card, CounterColor.GREEN, "1") to HistoryIcon.EXPOSURE_PLUS_1,
            PlayAction.SetCorner(card, "3/3") to HistoryIcon.TRENDING_UP, PlayAction.SetCorner(card, "4/1") to HistoryIcon.TRENDING_DOWN,
            PlayAction.SetEotCorner(card, "+1/+1") to HistoryIcon.TRENDING_UP,
            PlayAction.SetDamage(card, "2") to HistoryIcon.LOCAL_FIRE_DEPARTMENT, PlayAction.SetDamage(card, "") to HistoryIcon.HEALING,
            PlayAction.ClearCardCounter(card, CounterColor.GREEN) to HistoryIcon.EXPOSURE_ZERO,
            PlayAction.NextStep to HistoryIcon.ARROW_FORWARD, PlayAction.EndTurn to HistoryIcon.SKIP_NEXT)
        for ((action, icon) in expected) {
            val command = GameCommand(id(request++), GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(90), "1")
            val result = GameplayRules.change(state, a, command)
            assertEquals("accepted", result.outcome, "$action: ${result.reason}")
            val mutation = assertNotNull(result.mutation)
            assertEquals(icon, mutation.grouping?.icon, "$action")
            val wire = GameWire.json.decodeFromString(GameplayMutation.serializer(), GameWire.json.encodeToString(GameplayMutation.serializer(), mutation))
            assertEquals(result, GameplayHistory.replay(state, wire), "$action")
            state = assertNotNull(result.state)
        }
    }
}
