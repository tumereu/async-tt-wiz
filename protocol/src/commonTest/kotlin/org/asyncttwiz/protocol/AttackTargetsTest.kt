package org.asyncttwiz.protocol

import kotlin.test.*

class AttackTargetsTest {
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private var request = 1000
    private fun state(): PlayState {
        val definitions = listOf("Creature", "Legendary Planeswalker — Test", "Battle — Siege", "Enchantment — Saga").mapIndexed { n, type ->
            val definition = CardDefinition("Card $n", listOf(DefinitionFace(id(40 + n), "Card $n", typeLine = type,
                loyalty = if (n == 1) "4" else "", defense = if (n == 2) "7" else "")))
            SubmittedDefinition(DeckJson.digest(definition), definition)
        }
        val cards = definitions.mapIndexed { n, definition -> PlayCard(id(20 + n), definition.digest,
            if (n == 0) id(7) else id(8), if (n == 0) id(10) else id(11), id(40 + n), covered = false,
            placement = PlayPlacement(BoardPosition(n, 0), id(60 + n))) }
        return PlayState(id(1), id(2), id(3), id(4), PlayRoot(id(5), id(6)), id(5), BoardState(
            listOf(PlayPlayer(id(7)), PlayPlayer(id(8))), cards,
            listOf(PlayContainer(id(10), PlayZone.BATTLEFIELD, id(7), listOf(id(20))),
                PlayContainer(id(11), PlayZone.BATTLEFIELD, id(8), cards.drop(1).map { it.id }),
                PlayContainer(id(12), PlayZone.STACK), PlayContainer(id(13), PlayZone.GRAVEYARD, id(8))),
            TurnState(id(7))), definitions, emptyList(), InitialStarter(StarterChoice(id(7)), id(7)))
    }
    private fun change(state: PlayState, action: PlayAction): GameplayChange {
        val command = GameCommand(id(request++), GameScope(state.gameId, playId = state.id), GameAction.Play(action), id(9), "1")
        assertTrue(GameWire.valid(command))
        return GameplayRules.change(state, id(7), command)
    }
    private fun exact(state: PlayState, action: PlayAction): GameplayChange {
        val result = change(state, action)
        val next = assertNotNull(result.state, result.reason)
        val event = assertNotNull(result.mutation)
        val decoded = GameWire.json.decodeFromString<GameplayMutation>(GameWire.json.encodeToString(event))
        assertTrue(event.transition.rulesVersion >= 18)
        assertEquals(next, GameplayHistory.apply(state, decoded))
        assertEquals(state, GameplayHistory.unapply(next, decoded))
        assertEquals(next, GameplayHistory.replay(state, decoded).state)
        return result
    }
    private fun declared(): PlayState {
        val opened = exact(state(), PlayAction.Combat(CombatAction.Open)).state!!
        return exact(opened, PlayAction.Combat(CombatAction.Declare(opened.board.combat!!.id, id(20)))).state!!
    }
    private fun retarget(s: PlayState, card: String?) = PlayAction.Combat(CombatAction.SetAttackTarget(
        s.board.combat!!.id, s.board.combat.pairs.single().id, card))

    @Test fun playerRetargetPreventsUndoFromChangingTheResolvedDefender() {
        val declared = declared()
        val initial = exact(declared, retarget(declared, id(21))).state!!
        var tree = GameplayTree(initial.copy(root = PlayRoot(initial.currentNodeId, initial.currentBranchId)))
        fun command(action: GameAction) = GameCommand(id(request++), GameScope(initial.gameId, playId = initial.id), action, id(9), "1")
        tree = assertNotNull(tree.play(id(7), command(GameAction.Play(PlayAction.EndTurn))).tree)
        val afterTurn = tree.stateAt(tree.currentNodeId)!!
        assertEquals(id(7), CombatStackRules.defendingPlayer(afterTurn.board))
        tree = assertNotNull(tree.play(id(8), command(GameAction.Play(retarget(afterTurn, null)))).tree)
        val event = tree.nodes.getValue(tree.currentNodeId)
        val dependency = event.transition.concerns!!.fields.single { it.field == ConcernField.DefendingPlayer }
        assertEquals("\"${id(7)}\"", dependency.before.toString())
        assertEquals(dependency.before, dependency.after)
        assertEquals("replayBlocked", SelectiveUndo.change(tree, id(7), command(GameAction.Undo)).reason)
        assertNull(GameplayHistory.apply(initial, event))
        // The dependency is the resolved identity, not unrelated turn counters/steps.
        val otherStep = afterTurn.copy(board = afterTurn.board.copy(turn = afterTurn.board.turn.copy(number = "99", step = "UPKEEP")))
        assertNotNull(GameplayHistory.apply(otherStep, event))
    }

    @Test fun placementKeepingAPlayerTargetAndDepartureFallbackConcernTheDefender() {
        val initial = declared()
        val area = initial.board.combat!!
        val place = exact(initial, PlayAction.Combat(CombatAction.Place(id(22),
            CombatDestination(area.id, CombatRole.ATTACKER, area.pairs.single().id)))).mutation!!
        assertTrue(place.transition.concerns!!.fields.any { it.field == ConcernField.DefendingPlayer })
        val otherTurn = initial.copy(board = initial.board.copy(turn = initial.board.turn.copy(accountId = id(8))))
        assertNull(GameplayHistory.apply(otherTurn, place))
        val targeted = exact(initial, retarget(initial, id(21))).state!!
        val target = targeted.board.cards.single { it.id == id(21) }
        val fallback = exact(targeted, PlayAction.MoveCard(target.id, MoveSource(target.containerId, target.placement),
            MoveDestination(id(13), MoveInsertion.UNORDERED))).mutation!!
        assertTrue(fallback.transition.concerns!!.fields.any { it.field == ConcernField.DefendingPlayer })
    }

    @Test fun setClearValidateAndReplayTargets() {
        val original = declared()
        assertNull(original.board.combat!!.pairs.single().attackTarget)
        val siege = ownBattle(original)
        for ((state, target) in listOf(original to id(21), siege to id(22))) {
            val changed = exact(state, retarget(state, target))
            assertEquals(target, changed.state!!.board.combat!!.pairs.single().attackTarget)
            assertEquals(listOf(CombatAttackTargetChange(state.board.combat!!.pairs.single().id, null, target)),
                changed.mutation!!.transition.combatStack!!.attackTargets)
            assertEquals(state.board, exact(changed.state, retarget(changed.state, null)).state!!.board)
        }
        // Rules v28: the defender's battle is protected by the attacker, so it is not a target.
        for (target in listOf(id(20), id(22), id(23), id(999))) assertEquals("inapplicable", change(original, retarget(original, target)).reason)
        val own = original.copy(board = original.board.copy(containers = original.board.containers.map {
            if (it.id == id(11)) it.copy(accountId = id(7)) else it }))
        assertEquals("inapplicable", change(own, retarget(own, id(21))).reason)
        assertNull(change(own, retarget(own, id(22))).reason)
    }

    /** Moves the Siege (card 22) onto the attacking player's (id 7) own battlefield. */
    private fun ownBattle(state: PlayState, faceDown: Boolean = false) = state.copy(board = state.board.copy(
        cards = state.board.cards.map { if (it.id == id(22)) it.copy(containerId = id(10),
            covered = faceDown, explicitlyCovered = faceDown) else it },
        containers = state.board.containers.map { when (it.id) {
            id(10) -> it.copy(cards = it.cards + id(22))
            id(11) -> it.copy(cards = it.cards - id(22))
            else -> it
        } }))

    // CMB-012
    @Test fun rulesV28AttackOwnFaceUpSiegesAndPlaneswalkersOnTheDefenderOnly() {
        val original = declared()
        val siege = ownBattle(original)
        assertTrue(CombatStackRules.canAttack(siege, id(22)))
        assertFalse(CombatStackRules.canAttack(original, id(22)))
        assertTrue(CombatStackRules.canAttack(original, id(21)))
        val place = exact(siege, PlayAction.Combat(CombatAction.Place(id(20), CombatDestination(siege.board.combat!!.id,
            CombatRole.ATTACKER, pairGap = IdentityGap(siege.board.combat.pairs.single().id), attackTarget = id(22)),
            CombatStackRules.location(siege.board.combat, siege.board.combat.pairs.single().attackers.single().id))))
        assertEquals(listOf(id(22)), place.state!!.board.combat!!.pairs.map { it.attackTarget })
        val opponentBattle = PlayAction.Combat(CombatAction.Place(id(20), CombatDestination(original.board.combat!!.id,
            CombatRole.ATTACKER, pairGap = IdentityGap(original.board.combat.pairs.single().id), attackTarget = id(22)),
            CombatStackRules.location(original.board.combat, original.board.combat.pairs.single().attackers.single().id)))
        assertEquals("inapplicable", change(original, opponentBattle).reason)
        // Face-down cards never are targets.
        assertEquals("inapplicable", change(ownBattle(original, faceDown = true), retarget(original, id(22))).reason)
        // Once the turn passes, the old attacker's battle is the new defender's: replay is blocked.
        val event = exact(siege, retarget(siege, id(22))).mutation!!
        assertTrue(event.transition.concerns!!.fields.any { it.field == ConcernField.AttackTargetEligible(id(22)) })
        val otherTurn = siege.copy(board = siege.board.copy(turn = siege.board.turn.copy(accountId = id(8))))
        assertNull(GameplayHistory.apply(otherTurn, event))
    }

    @Test fun cardTypesComeFromTheTypePartOfTheTypeLine() {
        assertTrue(AttackTargets.hasType("Battle — Siege", "Battle"))
        assertTrue(AttackTargets.hasType("Legendary Planeswalker — Jace", "planeswalker"))
        assertTrue(AttackTargets.hasType("Battle - Siege", "Battle"))
        assertFalse(AttackTargets.hasType("Creature — Human Battlemage", "Battle"))
        assertFalse(AttackTargets.hasType("Creature — Planeswalker", "Planeswalker"))
        assertFalse(AttackTargets.hasType("Battlemage", "Battle"))
        val original = declared()
        val battlemage = ownBattle(original).let { s -> s.copy(definitions = s.definitions.map { submitted ->
            submitted.copy(definition = submitted.definition.copy(faces = submitted.definition.faces.map {
                if (it.typeLine.startsWith("Battle")) it.copy(typeLine = "Creature — Human Battlemage") else it }))
        }) }
        assertFalse(CombatStackRules.canAttack(battlemage, id(22)))
        assertFalse(CombatStackRules.canAttack(battlemage, id(22), rulesVersion = 27)) // v27: not on the defender's side
    }

    /** A combat command executed and recorded (delta, concerns, seal) as a v27 build did. */
    private fun recordedV27(state: PlayState, command: GameCommand): GameplayChange {
        val change = CombatStackRules.change(state, id(7), command, rulesVersion = 27)
        val mutation = change.mutation ?: return change
        val next = change.state!!
        val delta = GameplayDeltas.capture(state, next, mutation.id, orderedPiles = true)
        val recorded = GameplayHistory.recontextualize(state, mutation.copy(schemaVersion = 2,
            transition = mutation.transition.copy(delta = delta, concerns = GameplayConcerns.capture(state, next, mutation, delta)),
            bookkeeping = GameplayBookkeeping(state.currentGroup, state.currentBranchId, next.currentGroup)))
        return change.copy(mutation = recorded.copy(transition = recorded.transition.copy(
            delta = recorded.transition.delta!!.copy(seal = GameplayDeltas.seal(recorded)))))
    }

    @Test fun rulesVersionIs28() {
        assertEquals(28, AttackTargets.RULES_VERSION)
        assertTrue(CURRENT_GAMEPLAY_RULES_VERSION >= AttackTargets.RULES_VERSION)
    }

    @Test fun rulesV27EventsKeepTheDefenderBattleCheckForReplayAndUndo() {
        val original = declared()
        assertTrue(CombatStackRules.canAttack(original, id(22), rulesVersion = 27))
        assertFalse(CombatStackRules.canAttack(ownBattle(original), id(22), rulesVersion = 27))
        val command = GameCommand(id(request++), GameScope(original.gameId, playId = original.id),
            GameAction.Play(retarget(original, id(22))), id(9), "1")
        val legacy = recordedV27(original, command)
        val event = assertNotNull(legacy.mutation, legacy.reason)
        assertEquals(27, event.transition.rulesVersion)
        assertEquals(id(22), legacy.state!!.board.combat!!.pairs.single().attackTarget)
        assertTrue(event.transition.concerns!!.fields.any { it.field == ConcernField.AttackTargetEligible(id(22)) })
        // The recorded v27 event still replays, applies and unapplies under a v28 build.
        assertEquals(legacy.state.board, GameplayHistory.replay(original, event).state!!.board)
        assertEquals(legacy.state, GameplayHistory.apply(original, event))
        assertEquals(original, GameplayHistory.unapply(legacy.state, event))
        assertEquals("inapplicable", CombatStackRules.change(ownBattle(original), id(7), command, rulesVersion = 27).reason)
    }

    @Test fun targetDepartureFallsBackInTheSameMoveAndExactInverseRestoresIt() {
        val original = declared()
        val targeted = exact(original, retarget(original, id(21))).state!!
        val target = targeted.board.cards.single { it.id == id(21) }
        val moved = exact(targeted, PlayAction.MoveCard(target.id, MoveSource(target.containerId, target.placement),
            MoveDestination(id(13), MoveInsertion.UNORDERED)))
        assertNull(moved.state!!.board.combat!!.pairs.single().attackTarget)
        assertEquals(targeted.board.combat!!.pairs.single().attackers, moved.state.board.combat.pairs.single().attackers)
        assertTrue(moved.mutation!!.transition.concerns!!.fields.any { it.field == ConcernField.AttackTargetIncident(id(21)) })
        assertNull(GameplayHistory.apply(original, moved.mutation))
    }

    @Test fun targetConcernIgnoresUnrelatedFieldsButProtectsTargetEligibilityAndPriorTarget() {
        val original = declared()
        val result = exact(original, retarget(original, id(21)))
        val event = result.mutation!!
        val unrelated = original.copy(board = original.board.copy(cards = original.board.cards.map { it.copy(note = "unrelated", corner = "99") }))
        assertNotNull(GameplayHistory.replay(unrelated, event).state)
        val unavailable = original.copy(board = original.board.copy(cards = original.board.cards.map {
            if (it.id == id(21)) it.copy(containerId = id(13), placement = null) else it }))
        assertNull(GameplayHistory.replay(unavailable, event).state)
        val otherTarget = ownBattle(original).let { exact(it, retarget(it, id(22))).state!! }
        assertNull(GameplayHistory.replay(otherTarget, event).state)
    }

    @Test fun oldPairsKeepTheirWireShapeAndOldEventsReplayWithoutNewDefaults() {
        val pair = CombatPair(id(50), listOf(CombatOccurrence(id(51), id(20))))
        val bytes = GameWire.json.encodeToString(pair)
        assertFalse("attackTarget" in bytes)
        assertNull(GameWire.json.decodeFromString<CombatPair>(bytes).attackTarget)
        val state = declared().let { it.copy(board = it.board.copy(cards = it.board.cards.map { card ->
            if (card.id == id(21)) card.copy(corner = "custom") else card })) }
        val accepted = exact(state, PlayAction.ResetCorner(id(21)))
        val oldNext = accepted.state!!.copy(board = accepted.state.board.copy(cards = accepted.state.board.cards.map {
            if (it.id == id(21)) it.copy(corner = "") else it }))
        val raw = accepted.mutation!!.let { it.copy(transition = GameplayTransition(rulesVersion = 16)) }
        val delta = GameplayDeltas.capture(state, oldNext, raw.id)
        val recorded = GameplayHistory.recontextualize(state, raw.copy(transition = raw.transition.copy(delta = delta)))
        val legacy = recorded.copy(transition = recorded.transition.copy(delta = delta.copy(seal = GameplayDeltas.seal(recorded))))
        assertEquals(oldNext.board, GameplayHistory.replay(state, legacy).state!!.board)
    }

    @Test fun selectiveUndoAllowsIndependentPairTargetsButNotChangedDepartureCleanup() {
        val first = declared()
        val initial = exact(first, PlayAction.Combat(CombatAction.Declare(first.board.combat!!.id, id(22)))).state!!
        val area = initial.board.combat!!
        var tree = GameplayTree(initial.copy(root = PlayRoot(initial.currentNodeId, initial.currentBranchId)))
        fun command(action: GameAction) = GameCommand(id(request++), GameScope(initial.gameId, playId = initial.id), action, id(9), "1")
        fun append(actor: String, action: PlayAction) {
            val change = tree.play(actor, command(GameAction.Play(action)))
            tree = assertNotNull(change.tree, change.reason)
        }
        append(id(7), PlayAction.Combat(CombatAction.SetAttackTarget(area.id, area.pairs.first().id, id(21))))
        val oneTarget = tree
        append(id(8), PlayAction.Combat(CombatAction.SetAttackTarget(area.id, area.pairs.last().id, id(21))))
        val undone = SelectiveUndo.change(tree, id(7), command(GameAction.Undo))
        val nextTree = assertNotNull(undone.tree, undone.reason)
        val pairs = nextTree.stateAt(nextTree.currentNodeId)!!.board.combat!!.pairs
        assertNull(pairs.first().attackTarget)
        assertEquals(id(21), pairs.last().attackTarget)
        tree = oneTarget
        val target = tree.stateAt(tree.currentNodeId)!!.board.cards.single { it.id == id(21) }
        append(id(8), PlayAction.MoveCard(target.id, MoveSource(target.containerId, target.placement),
            MoveDestination(id(13), MoveInsertion.UNORDERED)))
        assertEquals("replayBlocked", SelectiveUndo.change(tree, id(7), command(GameAction.Undo)).reason)
    }

    @Test fun printedCornerDefaultsAndSingleNumberAdjustmentAndLeaveReset() {
        val initial = state()
        assertEquals(listOf("", "4", "7", "1"), initial.definitions.map { it.definition.faces.single().defaultCorner() })
        assertEquals("2/3", DefinitionFace(id(80), "Creature", power = "2", toughness = "3").defaultCorner())
        assertEquals("", DefinitionFace(id(80), "Not a saga", typeLine = "Enchantment", rulesText = "Saga").defaultCorner())
        for ((card, value) in listOf(id(21) to "4", id(22) to "7", id(23) to "1")) {
            val reset = exact(initial, PlayAction.ResetCorner(card)).state!!
            assertEquals(value, reset.board.cards.single { it.id == card }.corner)
            val adjusted = exact(reset, PlayAction.AdjustCorner(card, CornerPart.WHOLE, 1)).state!!
            val source = adjusted.board.cards.single { it.id == card }
            assertEquals((value.toInt() + 1).toString(), source.corner)
            val moved = exact(adjusted, PlayAction.MoveCard(card, MoveSource(source.containerId, source.placement),
                MoveDestination(id(13), MoveInsertion.UNORDERED))).state!!
            assertEquals(value, moved.board.cards.single { it.id == card }.corner)
        }
    }
}
