package org.asyncttwiz.android

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** Every recordable action must group, read, and draw in the timeline and the history tree, or opening them crashes. */
class HistoryEveryActionTest {
    private val f = DownloadedTreeFixture
    private fun id(n: Int) = f.id(n)
    private val a = f.a
    private val b = f.b
    private val names = mapOf(a to "alice", b to "bob")
    private val seed = RandomV1.encodeSeed(ByteArray(32))
    private val reference get() = EventReference(id(2), id(5))

    private val library = id(20); private val hand = id(21); private val battlefield = id(22); private val graveyard = id(23)
    private val stack = id(24); private val temp = id(25); private val temp2 = id(26); private val libraryB = id(27)
    private val battlefieldB = id(28)

    private val initial: PlayState by lazy {
        val base = f.root().initial
        val digest = base.definitions.single().digest
        val face = base.board.cards.first().faceId
        fun card(n: Int, owner: String, container: String, placed: Boolean = false) = PlayCard(id(n), digest, owner, container, face,
            placement = if (placed) PlayPlacement(BoardPosition(0, 0), id(n + 100)) else null)
        val cards = listOf(card(30, a, library), card(31, a, hand), card(32, a, battlefield, true), card(33, a, graveyard),
            card(34, a, stack), card(35, a, temp), card(36, b, battlefieldB, true), card(37, b, libraryB))
        val containers = listOf(PlayContainer(library, PlayZone.LIBRARY, a, listOf(id(30))),
            PlayContainer(hand, PlayZone.HAND, a, listOf(id(31))), PlayContainer(battlefield, PlayZone.BATTLEFIELD, a, listOf(id(32))),
            PlayContainer(graveyard, PlayZone.GRAVEYARD, a, listOf(id(33))), PlayContainer(stack, PlayZone.STACK, null, listOf(id(34))),
            PlayContainer(temp, PlayZone.LIBRARY, a, listOf(id(35)), temp = true),
            PlayContainer(temp2, PlayZone.LIBRARY, a, temp = true, tempNumber = 2),
            PlayContainer(libraryB, PlayZone.LIBRARY, b, listOf(id(37))), PlayContainer(battlefieldB, PlayZone.BATTLEFIELD, b, listOf(id(36))))
        base.copy(board = base.board.copy(cards = cards, containers = containers))
    }

    private fun move(card: Int, from: String, to: String, edge: MoveSourceEdge = MoveSourceEdge.MEMBER, removeStackEntryId: String? = null) =
        PlayAction.MoveCard(id(card), MoveSource(from, edge = edge), MoveDestination(to, MoveInsertion.TOP),
            removeStackEntryId = removeStackEntryId)

    /** At least one instance of every action type, plus each variant that records a different icon. */
    private fun samples(): List<PlayAction> {
        val location = CombatLocation(id(60), id(61), CombatRole.ATTACKER, IdentityGap())
        return listOf(
            PlayAction.SuggestResult(SuggestedResult.WIN, "good game"), PlayAction.DeclineResult(id(50)), PlayAction.WithdrawResult(id(50)),
            PlayAction.SetTapped(id(32), true), PlayAction.SetTapped(id(32), false),
            PlayAction.SetLife(a, "18"), PlayAction.AdjustLife(b, "-2"),
            PlayAction.SetPlayerCounter(a, CounterColor.RED, "2"), PlayAction.AdjustPlayerCounter(b, CounterColor.BLUE, "1"),
            PlayAction.SetStatus("Wait"), PlayAction.SetStatus(""), PlayAction.SetTempPile(a, 2, true), PlayAction.SetTempPile(a, 2, false),
            PlayAction.NextStep, PlayAction.PassAttention, PlayAction.EndTurn, PlayAction.ChooseStep(TurnPosition.DECLARE_BLOCKERS),
            PlayAction.RollDie(20, 7), PlayAction.FlipCoin(CoinFace.TAILS),
            move(30, library, hand, MoveSourceEdge.TOP), move(32, battlefield, graveyard), move(32, battlefield, battlefield),
            move(31, hand, stack), move(34, stack, battlefield), move(34, stack, stack), move(32, battlefield, hand, removeStackEntryId = id(34)),
            move(30, library, temp, MoveSourceEdge.TOP), move(35, temp, temp2), move(33, graveyard, library), move(36, battlefieldB, libraryB),
            PlayAction.MoveToLibraryRandom(library, listOf(id(31), id(33)), true, seed),
            PlayAction.ShuffleLibrary(library, seed), PlayAction.SetKeepTopRevealed(library, true),
            PlayAction.Mulligan(library, hand, listOf(id(31)), 7, seed),
            PlayAction.ShowContents(library, ContentsSelection.TOP, 3), PlayAction.ShowContents(library, ContentsSelection.BOTTOM, reveal = true),
            PlayAction.RevealHand(hand, listOf(id(31))),
            PlayAction.RandomHand(hand, listOf(id(31)), seed, HandRandomResult.REVEAL, id(31)),
            PlayAction.RandomHand(hand, listOf(id(31)), seed, HandRandomResult.DISCARD, id(31)),
            PlayAction.DismissContents(id(51)), PlayAction.DismissContents(id(51), ContentsRest.BOTTOM_RANDOM, seed),
            PlayAction.RevealContentsCard(id(51), id(30)), PlayAction.ReorderContents(id(51), id(30), id(35)),
            PlayAction.EventComment(reference, "Remember the trigger"),
            PlayAction.Ping(reference, PingTarget(PingTargetKind.CARD, id(32))), PlayAction.Unping(reference, PingTarget(PingTargetKind.PLAYER, b)),
            PlayAction.SetDamage(id(32), "3"), PlayAction.AdjustDamage(id(32), "1"), PlayAction.SetEotCorner(id(32), "+1/+1"),
            PlayAction.AdjustEotCorner(id(32), CornerPart.LEFT, 1), PlayAction.SetEotNote(id(32), "flying"),
            PlayAction.SetCorner(id(32), "3/3"), PlayAction.AdjustCorner(id(32), CornerPart.BOTH, -1), PlayAction.ResetCorner(id(32)),
            PlayAction.SetCounter(CounterTarget.CARD, id(32), CounterColor.GREEN, "2"),
            PlayAction.SetCounter(CounterTarget.PLAYER, a, CounterColor.GREEN, "2"),
            PlayAction.AdjustCounter(CounterTarget.CARD, id(32), CounterColor.GREEN, "1"),
            PlayAction.AdjustCounter(CounterTarget.PLAYER, b, CounterColor.GRAY, "-1"),
            PlayAction.SetNote(id(32), "summoning sick"), PlayAction.SetProjectionNote(id(62), "copy"),
            PlayAction.SetOwner(id(32), b), PlayAction.SetFace(id(32), id(11)),
            PlayAction.SetCovered(id(32), true), PlayAction.SetCovered(id(32), false),
            PlayAction.InspectFace(id(32), id(11), battlefield), PlayAction.ClearCardCounter(id(32), CounterColor.GREEN),
            PlayAction.CreateToken(TokenCatalog.blank), PlayAction.CloneToken(id(32)), PlayAction.RemoveToken(id(32)),
            PlayAction.Link(LinkEndpoint(LinkEndpointKind.CARD, id(32)), LinkEndpoint(LinkEndpointKind.PLAYER, b)),
            PlayAction.RemoveLink(id(63)), PlayAction.LinkProjections(ProjectionEndpoint(id(62)), ProjectionEndpoint(id(64))),
            PlayAction.RemoveProjectionLink(id(63)),
            PlayAction.Combat(CombatAction.Open), PlayAction.Combat(CombatAction.Close(id(60))), PlayAction.Combat(CombatAction.Close(null)),
            PlayAction.Combat(CombatAction.Declare(id(60), id(32))),
            PlayAction.Combat(CombatAction.Place(id(32), CombatDestination(id(60), CombatRole.ATTACKER))),
            PlayAction.Combat(CombatAction.Place(id(32), CombatDestination(id(60), CombatRole.ATTACKER, id(61)), location)),
            PlayAction.Combat(CombatAction.Place(id(36), CombatDestination(id(60), CombatRole.BLOCKER, id(61)), location)),
            PlayAction.Combat(CombatAction.Remove(id(60), id(62))),
            PlayAction.Combat(CombatAction.SetAttackTarget(id(60), id(61), id(36))),
            PlayAction.Stack(StackAction.MarkText(id(62), 0)), PlayAction.Stack(StackAction.MarkText(id(62), null)),
            PlayAction.Stack(StackAction.ToggleTextMark(id(62), 1)), PlayAction.Stack(StackAction.AddEffect(id(32), "digest")),
            PlayAction.Stack(StackAction.CopyEffect(id(62), "digest")), PlayAction.Stack(StackAction.SetController(id(62), b)),
            PlayAction.Stack(StackAction.RemoveEffect(id(62))),
            PlayAction.Stack(StackAction.ReorderEffect(id(62), IdentityGap(), IdentityGap(afterId = id(64)))))
    }

    /** Exhaustive with no else branch: a new action type stops this test compiling until it has a sample above.
     * Returns the wire name, checked against the serializers' full subtype lists below. */
    private fun kind(action: PlayAction): String = when (action) {
        is PlayAction.SuggestResult -> "suggestResult"
        is PlayAction.DeclineResult -> "declineResult"
        is PlayAction.WithdrawResult -> "withdrawResult"
        is PlayAction.SetTapped -> "setTapped"
        is PlayAction.SetLife -> "setLife"
        is PlayAction.AdjustLife -> "adjustLife"
        is PlayAction.SetPlayerCounter -> "setPlayerCounter"
        is PlayAction.AdjustPlayerCounter -> "adjustPlayerCounter"
        is PlayAction.SetStatus -> "setStatus"
        is PlayAction.SetTempPile -> "setTempPile"
        PlayAction.NextStep -> "nextStep"
        PlayAction.PassAttention -> "passAttention"
        PlayAction.EndTurn -> "endTurn"
        is PlayAction.ChooseStep -> "chooseStep"
        is PlayAction.RollDie -> "rollDie"
        is PlayAction.FlipCoin -> "flipCoin"
        is PlayAction.MoveCard -> "moveCard"
        is PlayAction.MoveToLibraryRandom -> "moveToLibraryRandom"
        is PlayAction.ShuffleLibrary -> "shuffleLibrary"
        is PlayAction.SetKeepTopRevealed -> "setKeepTopRevealed"
        is PlayAction.Mulligan -> "mulligan"
        is PlayAction.ShowContents -> "showContents"
        is PlayAction.RevealHand -> "revealHand"
        is PlayAction.RandomHand -> "randomHand"
        is PlayAction.DismissContents -> "dismissContents"
        is PlayAction.RevealContentsCard -> "revealContentsCard"
        is PlayAction.ReorderContents -> "reorderContents"
        is PlayAction.EventComment -> "eventComment"
        is PlayAction.Ping -> "ping"
        is PlayAction.Unping -> "unping"
        is PlayAction.SetDamage -> "setDamage"
        is PlayAction.AdjustDamage -> "adjustDamage"
        is PlayAction.SetEotCorner -> "setEotCorner"
        is PlayAction.AdjustEotCorner -> "adjustEotCorner"
        is PlayAction.SetEotNote -> "setEotNote"
        is PlayAction.SetCorner -> "setCorner"
        is PlayAction.AdjustCorner -> "adjustCorner"
        is PlayAction.ResetCorner -> "resetCorner"
        is PlayAction.SetCounter -> "setCounter"
        is PlayAction.AdjustCounter -> "adjustCounter"
        is PlayAction.SetNote -> "setNote"
        is PlayAction.SetProjectionNote -> "setProjectionNote"
        is PlayAction.SetOwner -> "setOwner"
        is PlayAction.SetFace -> "setFace"
        is PlayAction.SetCovered -> "setCovered"
        is PlayAction.InspectFace -> "inspectFace"
        is PlayAction.ClearCardCounter -> "clearCardCounter"
        is PlayAction.CreateToken -> "createToken"
        is PlayAction.CloneToken -> "cloneToken"
        is PlayAction.RemoveToken -> "removeToken"
        is PlayAction.Link -> "link"
        is PlayAction.RemoveLink -> "removeLink"
        is PlayAction.LinkProjections -> "linkProjections"
        is PlayAction.RemoveProjectionLink -> "removeProjectionLink"
        is PlayAction.Combat -> "combat/" + when (action.action) {
            CombatAction.Open -> "open"
            is CombatAction.Close -> "close"
            is CombatAction.Declare -> "declare"
            is CombatAction.Place -> "place"
            is CombatAction.Remove -> "remove"
            is CombatAction.SetAttackTarget -> "setAttackTarget"
        }
        is PlayAction.Stack -> "stack/" + when (action.action) {
            is StackAction.MarkText -> "markText"
            is StackAction.ToggleTextMark -> "toggleTextMark"
            is StackAction.AddEffect -> "addEffect"
            is StackAction.CopyEffect -> "copyEffect"
            is StackAction.SetController -> "setController"
            is StackAction.RemoveEffect -> "removeEffect"
            is StackAction.ReorderEffect -> "reorderEffect"
        }
    }

    private fun subtypes(descriptor: SerialDescriptor) = descriptor.getElementDescriptor(1).elementNames.toSet()

    /** A transition shaped like the recorded one where the grouping reads it, at the current rules version. */
    private fun transition(action: PlayAction, rulesVersion: Int) = GameplayTransition(rulesVersion = rulesVersion, fields = when (action) {
        PlayAction.NextStep, PlayAction.EndTurn -> listOf(GameplayFieldChange(id(4), GameplayField.TURN_PLAYER, a, b))
        is PlayAction.ChooseStep -> listOf(GameplayFieldChange(id(4), GameplayField.STEP, null, TurnPosition.DECLARE_BLOCKERS.step))
        is PlayAction.SetTapped -> listOf(GameplayFieldChange(action.cardId, GameplayField.TAPPED, "${!action.tapped}", "${action.tapped}"))
        else -> emptyList()
    })

    private fun chain(actions: List<PlayAction>, rulesVersion: Int, grouped: Boolean): List<GameplayMutation> {
        var parent = initial.root.id
        return actions.mapIndexed { index, action ->
            val transition = transition(action, rulesVersion)
            val grouping = if (grouped) HistoryGrouping.classify(action, initial.board.containers, transition) else null
            GameplayMutation(id(1000 + index), parent, initial.root.branchId, id(5000 + index), if (index % 3 == 0) b else a,
                null, action, transition, grouping = grouping).also { parent = it.id }
        }
    }

    /** The timeline strip, the node texts, and the tree dialog's graph, chips and selected-event text. */
    private fun present(mutations: List<GameplayMutation>) {
        val head = mutations.last().id
        val history = HistoryTree(initial.root.id, mutations, head, "v", containers = initial.board.containers)
        val events = HistoryTimelineLayout.events(history, head)
        assertEquals(mutations.size + 1, events.size)
        for (event in events) draw(event.icon)
        val texts = mutableMapOf(initial.root.id to historyText(null))
        for (mutation in mutations) {
            historyText(mutation)
            historyText(mutation, initial, b, names)
            texts[mutation.id] = historyText(mutation, initial, a, names)
        }
        val tree = GameplayTree(initial, mutations.associateBy { it.id }, head,
            branchHeads = mapOf(initial.root.branchId to head))
        val graph = HistoryGraphLayout.build(tree)
        assertEquals(mutations.size + 1, graph.ranges.sumOf { it.nodeIds.size })
        for (range in graph.ranges) {
            draw(range.icon)
            assertNotNull(graph.chipText(range.id, texts))
        }
        for (event in graph.events) draw(event.icon)
        for (mutation in mutations) graph.selectedText(mutation.id, texts)
        assertTrue(graph.visible(0f, 0f, graph.width, graph.height).isNotEmpty())
    }

    private fun draw(icon: HistoryIcon) {
        assertTrue(historyIconGlyph(icon).isNotBlank())
        assertTrue(historyIconLabel(icon).isNotBlank())
        if (icon != HistoryIcon.SWORDS) assertNotNull(historyIconVector(icon))
    }

    @Test fun samplesCoverEveryRecordedActionType() {
        val expected = subtypes(PlayAction.serializer().descriptor) - setOf("combat", "stack") +
            subtypes(CombatAction.serializer().descriptor).map { "combat/$it" } +
            subtypes(StackAction.serializer().descriptor).map { "stack/$it" }
        assertEquals(expected.sorted(), samples().map(::kind).toSet().sorted())
    }

    @Test fun everyActionGroupsReadsAndDrawsInTheTimelineAndTree() {
        val failures = mutableListOf<String>()
        for (action in samples()) for (rules in listOf(1, CURRENT_GAMEPLAY_RULES_VERSION)) for (grouped in listOf(true, false)) {
            runCatching { present(chain(listOf(action), rules, grouped)) }.onFailure { failures += "${kind(action)} rules=$rules grouped=$grouped: $action -> $it" }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test fun everyActionTogetherGroupsIntoOneDrawableTree() {
        for (rules in listOf(1, CURRENT_GAMEPLAY_RULES_VERSION)) {
            present(chain(samples(), rules, true))
            present(chain(samples().reversed(), rules, true))
        }
    }

    @Test fun everyIconDrawsAndUnknownNamesDecodeToANeutralDot() {
        for (icon in HistoryIcon.entries) draw(icon)
        val semantic = HistorySemantic(HistorySignificance.PING, HistoryIcon.ADS_CLICK)
        val json = GameWire.json.encodeToString(HistorySemantic.serializer(), semantic)
        val future = GameWire.json.decodeFromString(HistorySemantic.serializer(), json.replace("ads_click", "invented_by_a_newer_server"))
        assertEquals(HistoryIcon.UNKNOWN, future.icon)
        assertEquals("•", historyIconGlyph(future.icon))
    }
}
