package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.CostPart
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Pending
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.CombatArea
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.TurnPosition

/** An attack as ATW's lanes hold it (CMB-012): the creature [cardId] attacks the planeswalker or battle card [target]
 * (its lane), or the defending player when null (as `CombatPair.attackTarget`); with the choices made with it
 * ([AttackChoice], CMB-036–CMB-038): the creatures it [enlisted] (CR 702.154a), the optional costs to attack it is
 * [paying] (CR 508.1g, ability origin keys) and its [band] (CR 702.22c). */
data class LaneAttack(val cardId: String, val target: String? = null, val enlisted: List<String> = emptyList(),
    val paying: List<String> = emptyList(), val band: Int? = null)

/** A cost of combat as the table shows it on a creature (CMB-037): [label] short ("Exert", "{2}", "2 life"), [text]
 * the printed ability it comes from (held chip), [key] the ability origin's key of an optional cost. */
data class CombatCostShown(val label: String, val text: String?, val key: String = "")

/** A block as ATW's lanes hold it: [blockerId] in the pair of [attackerId] (CMB-007). */
data class LaneBlock(val blockerId: String, val attackerId: String)

/** A declaration of attackers or blockers the engine refuses (CR 508.1, 509.1), with the reason for people. */
class CombatRefused(val reason: String) : IllegalArgumentException(reason)

/**
 * What may be declared now (§3 I3, I-D28: with Automatum on only what the engine considers legal), in ATW's terms.
 *
 * - [declaring]: the player who declares now (the active player for attackers, the defending player for blockers);
 *   null when no declaration is awaited.
 * - [attacks]: each creature that may attack, with the lanes it may attack (null: the defending player); [requiredAttacks]
 *   the least declaration that obeys "attacks each combat if able" (CR 508.1d), empty without such requirements.
 * - [blocks]: each creature that may block, with the attackers it may block (on its own; one with menace needs company,
 *   CR 702.111b); [requiredBlocks] as for attacks (CR 509.1c).
 */
data class CombatChoices(
    val declaring: String?,
    val attackers: Boolean,
    val attacks: Map<String, List<String?>> = emptyMap(),
    val requiredAttacks: List<LaneAttack> = emptyList(),
    val blocks: Map<String, List<String>> = emptyMap(),
    val requiredBlocks: List<LaneBlock> = emptyList(),
    /** How many attackers each creature that may block can block at once (CR 509.1a, "can block an additional
     * creature"): only those that can block more than one, null for any number. */
    val capacity: Map<String, Int?> = emptyMap(),
    /** Why the engine can't take this board (then nothing is offered). */
    val unsupported: List<String> = emptyList(),
    /** CMB-036 (CR 702.154a): each creature that may attack with enlist, with how many creatures it may enlist (one per
     * enlist ability). */
    val enlist: Map<String, Int> = emptyMap(),
    /** CMB-036: the declaring player's creatures that could be enlisted (untapped, without summoning sickness, CR
     * 702.154a), unless they attack. */
    val enlistable: List<String> = emptyList(),
    /** CMB-037 (CR 508.1g): each creature that may attack with "you may [cost] as it attacks" abilities (exert, CR
     * 701.43d), with those optional costs ([CombatCostShown.key] names each). */
    val optional: Map<String, List<CombatCostShown>> = emptyMap(),
    /** CMB-038 (CR 702.22): the creatures that may attack with banding. */
    val banding: Set<String> = emptySet(),
) {
    companion object {
        val NONE = CombatChoices(null, attackers = false)
    }
}

/**
 * An open division of combat damage (CR 510.1c–d, 702.19b, I-D29) in ATW's terms: [player] divides [amount] damage of
 * the creature [source] among [among] (blockers' cards, or with trample also the attacked player or planeswalker);
 * [lethal] is lethal damage for each creature, [suggested] the engine's suggestion (lethal damage to each in turn, the
 * rest to the defender with trample).
 */
data class DamageDivision(
    val player: String,
    val source: String,
    val amount: Int,
    val among: List<MtgRef>,
    val lethal: Map<MtgRef, Int>,
    val suggested: Map<MtgRef, Int>,
    /** Whether the defender can get damage with trample (CR 702.19b): once each blocker has lethal damage. */
    val trampleTo: MtgRef?,
    /** It is asked in the first-strike combat damage step (CR 510.4), else in the regular one. */
    val firstStrike: Boolean = false,
)

/** One damage dealt in a [DamagePreview]: [source] (an ATW card) deals [amount] to [target]; [firstStrike] in the
 * first-strike damage step (CR 510.4). */
data class PreviewDamage(val source: String, val target: MtgRef, val amount: Int, val firstStrike: Boolean)

/**
 * The combat damage that would happen if every player passed from now on and every division took its suggestion (or
 * the division given), I-D29: the table shows it in the declare blockers step.
 *
 * - [dealt]: each damage, in order; [damage]: the total per creature and player (ATW card ids and account ids).
 * - [life]: each player's life change (damage, lifelink); [dies]: the ATW cards that would die (CR 704.5g–h).
 * - [divisions]: the divisions asked on the way, with the shares taken.
 * - [assumed]: getting to the damage needs another choice (a trigger's target, a choice between replacement effects,
 *   a payment), taken here as the engine's suggestion or first listed answer (finalreview-combat #4): the damage
 *   depends on how it is answered.
 */
data class DamagePreview(
    val dealt: List<PreviewDamage>,
    val life: Map<String, Int>,
    val dies: List<String>,
    val divisions: List<Pair<DamageDivision, Map<MtgRef, Int>>>,
    val assumed: Boolean = false,
) {
    /** Damage per card id or account id. */
    val damage: Map<String, Int>
        get() = dealt.groupBy { key(it.target) }.mapValues { (_, list) -> list.sumOf { it.amount } }

    private fun key(ref: MtgRef) = when (ref) {
        is MtgRef.Card -> ref.cardId
        is MtgRef.Player -> ref.accountId
        is MtgRef.StackItem -> ref.id
    }
}

/**
 * What the table shows of combat through the engine now (§3 I3 on today's lanes; the combat area redesign, I-D32, comes
 * later):
 *
 * - [choices]: what may be declared now ([CombatFlow.choices]);
 * - [preview]: the damage if every player passed from now on (I-D29, [CombatFlow.preview]), with the blocks the lanes
 *   hold while blockers are still declared and each early division given ([PreAnswers]); worked out in the declare
 *   blockers step, the first-strike damage step and while a division of combat damage is open, else null;
 * - [draftRefusal]: while a declaration is awaited, why the engine refuses what the lanes hold as that declaration (null:
 *   it takes it); [noneRefusal] why it refuses declaring nothing (CR 508.1d, 509.1c requirements; null: it takes it);
 * - [early]: the divisions of combat damage still to come in this step after the open one (CR 510.1c–d): their players
 *   may answer them early (I-D33, [PreAnswers]);
 * - [passing]: who holds priority with an empty Stack in combat once blockers are declared (the declare blockers step,
 *   the first-strike damage step): their pass goes on to the (next) combat damage; null otherwise;
 * - [firstStrikeStep]: the combat damage step now is the first-strike one (CR 510.4).
 */
data class CombatShown(
    val choices: CombatChoices,
    val preview: DamagePreview? = null,
    val draftRefusal: String? = null,
    val noneRefusal: String? = null,
    val early: List<DamageDivision> = emptyList(),
    val passing: String? = null,
    val firstStrikeStep: Boolean = false,
    /** CMB-037 (CR 508.1h, 509.1d): while a declaration is awaited, the costs each drafted attacker (or blocker) must pay
     * to attack (block) as the lanes hold it, paid as it is declared. */
    val costs: Map<String, List<CombatCostShown>> = emptyMap(),
) {
    companion object {
        val NONE = CombatShown(CombatChoices.NONE)
    }
}

/**
 * Combat through the engine (§3 I3, CR 506–511): ATW's lanes ↔ the engine's declarations, attack and block legality
 * with reasons, divisions of combat damage (I-D29) and the damage preview. Results come back to the lanes through
 * [EngineBatch] (one pair per attacker in its target's lane, blockers in the pair of the first attacker they block;
 * creatures removed from combat leave their pairs; the area closes as combat ends, CR 511.3).
 *
 * Each call works on an [EngineCall] the caller made and sends with [EngineBatch.plan], and ends with the host's
 * automatic passes ([StepFlow.automatic]).
 */
object CombatFlow {
    /** What may be declared on [play] now ([CombatChoices]); never throws. */
    fun choices(play: PlayState, definitions: Map<String, CardDefinition>, log: List<LoggedChange>): CombatChoices {
        val call = try {
            EngineCall(play, definitions, null, log) { error("No randomness while listing declarations") }
        } catch (problem: Unsupported) {
            return CombatChoices.NONE.copy(unsupported = problem.reasons)
        }
        return choices(call)
    }

    /**
     * What the table shows of combat on [play] now ([CombatShown]); [CombatShown.NONE] outside combat
     * or on a board the engine can't take over. Worked out with full information (I-D17, I-D27; the app shows only what
     * each player may see). Never throws.
     */
    fun shown(play: PlayState, definitions: Map<String, CardDefinition>, log: List<LoggedChange>): CombatShown {
        if (TurnPosition.of(play.board.turn) !in COMBAT_POSITIONS) return CombatShown.NONE
        return try {
            shown(EngineCall(play, definitions, EngineState.flowViewer(play.board), log) { error("No randomness while looking") })
        } catch (problem: Unsupported) {
            CombatShown(CombatChoices.NONE.copy(unsupported = problem.reasons))
        } catch (_: RuntimeException) {
            CombatShown.NONE
        }
    }

    /** Whether [board]'s combat runs through the engine: attackers were declared through it (its combat record is
     * there, of this combat: #1, not one finished by hand while Automatum was off), so it deals the combat damage
     * itself. */
    fun throughEngine(board: BoardState): Boolean = runCatching { EngineCodec.currentCombat(board) != null }.getOrDefault(false)

    /** What the table shows of combat in [call]'s state now ([CombatShown]). */
    fun shown(call: EngineCall): CombatShown {
        val s = call.working
        val choices = choices(call)
        val open = division(call)
        val firstStrike = firstStrikeStep(call)
        val blocks = if (choices.declaring != null && !choices.attackers) lanes(call.state.board.combat).second else null
        val previewed = !choices.attackers && (s.turn.step == TurnStep.DeclareBlockers || open != null || firstStrike)
        val turn = s.turn.number
        val early = PreAnswers.of(call.state.board).filterIsInstance<PreAnswer.Division>().filter { it.turn == null || it.turn == turn }
            .associate { d -> d.source.cardId to d.shares.associate { it.target to it.amount } }
        val preview = if (!previewed) null else try { preview(call, blocks, early) } catch (_: RuntimeException) { null }
        // #3 (CR 510.4): only divisions of the open one's damage step; the regular step's come with that step
        val coming = if (open == null) emptyList() else preview?.divisions.orEmpty().map { it.first }
            .filter { it.source != open.source && it.firstStrike == open.firstStrike }.distinctBy { it.source }
        // CMB-028: the quick actions say why a declaration can't be made yet
        val drafted = if (choices.declaring == null) null else lanes(call.state.board.combat).let { (attacks, blocks) ->
            withChoices(attacks, call.state.board) to blocks }
        fun refusals(attacks: List<LaneAttack>, blocks: List<LaneBlock>) =
            if (choices.attackers) attackRefusal(call, attacks) else blockRefusal(call, blocks)
        val draftRefusal = drafted?.let { (attacks, blocks) -> refusals(attacks, blocks) }
        val noneRefusal = drafted?.let { refusals(emptyList(), emptyList()) }
        val costs = drafted?.let { (attacks, blocks) ->
            runCatching { if (choices.attackers) attackCosts(call, attacks) else blockCosts(call, blocks) }.getOrDefault(emptyMap()) }.orEmpty()
        val combat = s.combat
        val passing = s.priority.holder?.value?.takeIf {
            s.pending == null && s.stack.isEmpty() && combat != null && combat.attackers.isNotEmpty() &&
                ((s.turn.step == TurnStep.DeclareBlockers && combat.blocksDecided) || firstStrike)
        }
        return CombatShown(choices, preview, draftRefusal, noneRefusal, coming, passing, firstStrike, costs)
    }

    /** What may be declared in [call]'s state now. */
    fun choices(call: EngineCall): CombatChoices {
        val s = call.working
        if (s.pending != null) return CombatChoices.NONE
        val ids = call.ids
        fun card(o: ObjectId) = ids.atw((o as ObjectId.Card).card)
        fun lane(t: TargetRef) = (t as? TargetRef.Object)?.id?.let(::card)
        for (p in s.players) {
            val declarations = Engine.options(s, p.id).filter { it is Input.DeclareAttackers || it is Input.DeclareBlockers }
            if (declarations.isEmpty()) continue
            if (declarations.first() is Input.DeclareAttackers) {
                val listed = declarations.filterIsInstance<Input.DeclareAttackers>()
                val attacks = LinkedHashMap<String, MutableList<String?>>()
                listed.flatMap { it.attacks }.distinct().forEach { a -> attacks.getOrPut(card(a.attacker)) { mutableListOf() } += lane(a.target) }
                return attackExtras(call, p.id, CombatChoices(p.id.value, attackers = true, attacks = attacks,
                    requiredAttacks = listed.first().attacks.map { LaneAttack(card(it.attacker), lane(it.target)) }))
            }
            val listed = declarations.filterIsInstance<Input.DeclareBlockers>()
            val pairs = listed.flatMap { it.blocks }.distinct().toMutableList()
            // One more block on its own isn't listed when the attacker can't be blocked except by two or more (menace):
            // each such pair is tried alone, and only a refusal that it can't block at all leaves it out
            val view = Engine.view(s)
            val needCompany = s.combat?.attackers.orEmpty().map { it.id }.filter { a -> view[a]?.has { it is Mechanic.MinimumBlockers } == true }
            val creatures = s.battlefield.filter { (view[it]?.controller) == p.id }
            for (a in needCompany) for (b in creatures) {
                if (pairs.any { it.blocker == b && it.attacker == a }) continue
                val refused = refusal(s, Input.DeclareBlockers(p.id, listOf(Block(b, a))))
                if (refused == null || "$b can't block $a" !in refused) pairs += Block(b, a)
            }
            val blocks = LinkedHashMap<String, MutableList<String>>()
            pairs.forEach { b -> blocks.getOrPut(card(b.blocker)) { mutableListOf() } += card(b.attacker) }
            // CR 509.1a: one creature blocks one attacker, more with "can block an additional creature" or any number
            val capacity = pairs.map { it.blocker }.distinct().mapNotNull { b ->
                val ms = view[b]?.mechanics.orEmpty().map { it.mechanic }
                if (ms.any { it == Mechanic.CanBlockAnyNumber }) card(b) to null else {
                    val n = 1 + ms.filterIsInstance<Mechanic.CanBlockAdditional>().sumOf { it.count }
                    if (n > 1) card(b) to n else null
                }
            }.toMap()
            return CombatChoices(p.id.value, attackers = false, blocks = blocks,
                requiredBlocks = listed.first().blocks.map { LaneBlock(card(it.blocker), card(it.attacker)) }, capacity = capacity)
        }
        return CombatChoices.NONE
    }

    /**
     * The attacks and blocks ATW's lanes [area] hold (a client's draft of a declaration, or a combat declared by hand):
     * each attacker of a pair attacks the pair's target, each blocker blocks every attacker of its pair and of the pairs
     * it also blocks (CMB-031, its arrows).
     */
    fun lanes(area: CombatArea?): Pair<List<LaneAttack>, List<LaneBlock>> {
        val pairs = area?.pairs.orEmpty()
        return pairs.flatMap { p -> p.attackers.map { LaneAttack(it.sourceId, p.attackTarget) } } to
            CombatStackRules.blocks(area).map { (b, a) -> LaneBlock(b.sourceId, a.sourceId) }
    }

    /** Why the engine refuses [attacks] as the attackers declared in [call]'s state (for people), or null. */
    fun attackRefusal(call: EngineCall, attacks: List<LaneAttack>): String? {
        val input = try { attackInput(call, attacks) } catch (refused: CombatRefused) { return refused.reason }
        return refusal(call.working, input)?.let { reason(call, it) }
    }

    /** Why the engine refuses [blocks] as the blockers declared in [call]'s state (for people), or null. */
    fun blockRefusal(call: EngineCall, blocks: List<LaneBlock>): String? {
        val input = try { blockInput(call, blocks) } catch (refused: CombatRefused) { return refused.reason }
        return refusal(call.working, input)?.let { reason(call, it) }
    }

    /**
     * The active player declares [attacks] (CR 508.1); an empty list declares no attackers. Then the host's automatic
     * passes. Returns what the engine waits for.
     * @throws CombatRefused when the engine refuses the declaration; nothing changes then.
     */
    fun declareAttackers(call: EngineCall, attacks: List<LaneAttack>, autotap: Boolean = false): Pending? {
        val input = attackInput(call, attacks)
        refusal(call.working, input)?.let { throw CombatRefused(reason(call, it)) }
        paid(call, input.player, call.perform(input), autotap)?.let { return it }
        return StepFlow.automatic(call)
    }

    /**
     * CMB-037 (CR 508.1h–j, 509.1d–f, I-D47): what the engine waits for after a declaration with costs ([pending]): with
     * [autotap] the mana it asks [player] to pay is paid with its suggestion in the same call, as casting does; any other
     * choice of the payment (what to sacrifice) stays open for the shared flow's panel.
     */
    private fun paid(call: EngineCall, player: PlayerId, pending: Pending?, autotap: Boolean): Pending? {
        var open = pending
        while (autotap) {
            val d = open?.decision as? Decision.ManaPayment ?: break
            if (d.player != player) break
            val answer = Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
            if (refusal(call.working, answer) != null) break
            open = call.perform(answer)
        }
        return open
    }

    /**
     * The defending player declares [blocks] (CR 509.1); an empty list declares no blockers. Then the host's automatic
     * passes.
     * @throws CombatRefused when the engine refuses the declaration; nothing changes then.
     */
    fun declareBlockers(call: EngineCall, blocks: List<LaneBlock>, autotap: Boolean = false): Pending? {
        val input = blockInput(call, blocks)
        refusal(call.working, input)?.let { throw CombatRefused(reason(call, it)) }
        paid(call, input.player, call.perform(input), autotap)?.let { return it }
        return StepFlow.automatic(call)
    }

    /**
     * [attacks] (as the lanes hold them) with the attacking player's choices on [board] ([PreAnswer.AttackChoices],
     * CMB-036–CMB-038) for the staged creatures: a staged creature enlists none of the others, and a band needs two staged
     * members.
     */
    fun withChoices(attacks: List<LaneAttack>, board: BoardState): List<LaneAttack> {
        val given = attackChoices(board).ifEmpty { return attacks }
        val staged = attacks.map { it.cardId }.toSet()
        val bands = attacks.mapNotNull { given[it.cardId]?.band }.groupingBy { it }.eachCount()
        return attacks.map { a ->
            val c = given[a.cardId] ?: return@map a
            a.copy(enlisted = c.enlisted.filter { it !in staged }, paying = c.paying, band = c.band?.takeIf { (bands[it] ?: 0) >= 2 })
        }
    }

    /** The attacking player's choices on [board] this turn ([PreAnswer.AttackChoices]), by staged card. */
    fun attackChoices(board: BoardState): Map<String, AttackChoice> {
        val record = PreAnswers.find(board, PreAnswer.ATTACK_CHOICES) as? PreAnswer.AttackChoices ?: return emptyMap()
        if (record.turn != null && record.turn != board.turn.number.toIntOrNull()) return emptyMap()
        return record.choices.associateBy { it.cardId }
    }

    /** The choices of [attackers]: who may enlist and what, the optional costs to attack and banding (CMB-036–CMB-038). */
    private fun attackExtras(call: EngineCall, player: PlayerId, attackers: CombatChoices): CombatChoices {
        val s = call.working
        val view = Engine.view(s)
        val candidates = attackers.attacks.keys.mapNotNull { atw -> runCatching { atw to objectOf(s, call.ids, atw) }.getOrNull() }
        val enlist = candidates.mapNotNull { (atw, o) ->
            view[o]?.characteristics?.abilities.orEmpty().count { it.ability == Keyword.Enlist }.takeIf { it > 0 }?.let { atw to it }
        }.toMap()
        // CR 702.154a: an untapped creature its controller controls, without summoning sickness (or with haste)
        val enlistable = if (enlist.isEmpty()) emptyList() else s.battlefield.filter { o ->
            val v = view[o]
            val obj = s.obj(o)
            o is ObjectId.Card && v?.controller == player && obj != null && !obj.tapped && !obj.phasedOut &&
                v.characteristics?.isType(CardType.Creature) == true && (!v.summoningSick || v.has { it == Mechanic.IgnoresSummoningSickness })
        }.map { call.ids.atw((it as ObjectId.Card).card) }
        val optional = candidates.mapNotNull { (atw, o) ->
            view[o]?.mechanics.orEmpty().filter { it.mechanic is Mechanic.AsAttacks && it.origin != null }
                .map { m -> CombatCostShown(costLabel((m.mechanic as Mechanic.AsAttacks).cost), abilityText(s, m.source ?: o, m.origin), m.origin!!.key) }
                .takeIf { it.isNotEmpty() }?.let { atw to it }
        }.toMap()
        val banding = candidates.filter { (_, o) -> view[o]?.characteristics?.hasKeyword(Keyword.Banding) == true }.map { it.first }.toSet()
        return attackers.copy(enlist = enlist, enlistable = enlistable, optional = optional, banding = banding)
    }

    /** CMB-037 (CR 508.1h): the costs to attack each of [attacks] must pay as the lanes hold it: its "can't attack …
     * unless … pays" costs that apply to what it attacks (the engine asks for a payment when it attacks alone). */
    private fun attackCosts(call: EngineCall, attacks: List<LaneAttack>): Map<String, List<CombatCostShown>> {
        val s = call.working
        val view = Engine.view(s)
        return attacks.mapNotNull { a ->
            val o = runCatching { objectOf(s, call.ids, a.cardId) }.getOrNull() ?: return@mapNotNull null
            val costs = view[o]?.mechanics.orEmpty().filter { it.mechanic is Mechanic.AttackCost }.ifEmpty { return@mapNotNull null }
            val alone = runCatching { attackInput(call, listOf(LaneAttack(a.cardId, a.target))) }.getOrNull()
            if (alone != null && !asksPayment(s, alone)) return@mapNotNull null
            a.cardId to costs.map { m -> CombatCostShown(costLabel((m.mechanic as Mechanic.AttackCost).cost), abilityText(s, m.source ?: o, m.origin)) }
        }.toMap()
    }

    /** CMB-037 (CR 509.1d): the costs to block each blocker of [blocks] must pay, as for [attackCosts]. */
    private fun blockCosts(call: EngineCall, blocks: List<LaneBlock>): Map<String, List<CombatCostShown>> {
        val s = call.working
        val view = Engine.view(s)
        return blocks.distinctBy { it.blockerId }.mapNotNull { b ->
            val o = runCatching { objectOf(s, call.ids, b.blockerId) }.getOrNull() ?: return@mapNotNull null
            val costs = view[o]?.mechanics.orEmpty().filter { it.mechanic is Mechanic.BlockCost }.ifEmpty { return@mapNotNull null }
            val alone = runCatching { blockInput(call, listOf(b)) }.getOrNull()
            if (alone != null && !asksPayment(s, alone)) return@mapNotNull null
            b.blockerId to costs.map { m -> CombatCostShown(costLabel((m.mechanic as Mechanic.BlockCost).cost), abilityText(s, m.source ?: o, m.origin)) }
        }.toMap()
    }

    /** Whether declaring [input] in [s] pays something: the engine waits for a payment or pays life, mana, objects or
     * counters with it (a refused declaration counts as one that would). */
    private fun asksPayment(s: GameState, input: Input): Boolean = try {
        val outcome = Engine.perform(s, input)
        outcome.pending != null || outcome.changes.any { it is Change.Life || it is Change.ZoneChange || it is Change.ManaPool || it is Change.Counters }
    } catch (_: RuntimeException) { true }

    /** The printed text of [origin] on [source] (a held cost chip), when it has one. */
    private fun abilityText(s: GameState, source: ObjectId, origin: arcanum.engine.view.AbilityOrigin?): String? =
        origin?.let { CastFlow.abilityText(s.obj(source)?.definition, it) }

    /** A cost of combat in a few words ("Exert", "{2}", "2 life", "Sacrifice"). */
    fun costLabel(cost: arcanum.api.Cost): String = cost.parts.joinToString(", ") { part ->
        when (part) {
            is CostPart.Mana -> part.cost.toString()
            is CostPart.PayLife -> "${(part.amount as? arcanum.api.Value.Fixed)?.value ?: "X"} life"
            is CostPart.Action -> words(part.action::class.simpleName)
            is CostPart.Sacrifice -> "Sacrifice"
            CostPart.TapSelf, is CostPart.TapObjects -> "Tap"
            is CostPart.Discard -> "Discard"
            is CostPart.Exile -> "Exile"
            else -> words(part::class.simpleName)
        }
    }

    private fun words(name: String?): String = name.orEmpty().replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase()
        .replaceFirstChar { it.uppercase() }

    /**
     * Whether the combat damage step now is the first-strike one, a second following (CR 510.4). ATW shows it as its
     * own step, `TurnPosition.FIRST_STRIKE_DAMAGE` (I-D31).
     */
    fun firstStrikeStep(call: EngineCall): Boolean =
        call.working.turn.step == TurnStep.CombatDamage && call.working.combat?.firstStrikeStep == true

    /** Whether [board]'s combat has had a first-strike combat damage step (CR 510.4, I-D31): the phase dialog shows that
     * step while the combat lasts. Never throws; false without Automatum's combat record. */
    fun hadFirstStrikeStep(board: BoardState): Boolean =
        runCatching { EngineCodec.currentCombat(board)?.firstStrikers != null }.getOrDefault(false)

    /** The open division of combat damage in [call]'s state, or null. */
    fun division(call: EngineCall): DamageDivision? = division(call.working, call.ids)

    /**
     * Answers the open division with [shares] (damage by ATW reference; null: the suggestion, I-D29), then the host's
     * automatic passes. Anyone may answer for the deciding player (D11).
     * @throws IllegalArgumentException when the engine refuses the division.
     */
    fun divide(call: EngineCall, shares: Map<MtgRef, Int>? = null): Pending? {
        val d = call.working.pending?.decision as? Decision.Divide ?: throw IllegalArgumentException("No division is open")
        val answer = Answer.Division(shares?.map { (ref, amount) -> Share(call.ids.target(ref), amount) } ?: d.suggested)
        Engine.perform(call.working, Input.Answer(d.id, answer))
        call.perform(Input.Answer(d.id, answer))?.let { return it }
        return StepFlow.automatic(call)
    }

    /**
     * The damage preview (I-D29) of [call]'s state: every player passes from now on, [blocks] are declared when blockers
     * are still awaited (none when null), and each division takes [divisions]' shares for its source card when the
     * engine accepts them, else its suggestion. Worked out on a copy; nothing changes.
     *
     * Another decision on the way (a trigger's choice while the Stack resolves, a choice between replacement effects) is
     * answered with its suggestion or the engine's first listed answer, and the preview says so ([DamagePreview.assumed],
     * finalreview-combat #4). Null when there is no combat with attackers, attackers are still awaited, or the way needs
     * a random result or hidden cards.
     */
    fun preview(call: EngineCall, blocks: List<LaneBlock>? = null, divisions: Map<String, Map<MtgRef, Int>> = emptyMap()): DamagePreview? {
        val s = call.working.deepCopy()
        if (s.combat?.attackers.isNullOrEmpty()) return null
        val ids = call.ids
        fun card(o: ObjectId) = ids.atw((o as ObjectId.Card).card)
        HostPriority.wanted(s, HostPriority.kept(call.state.board))?.let { if (it != s.priority) s.apply(Change.Priority(s.priority, it)) }
        val dealt = mutableListOf<PreviewDamage>()
        val life = mutableMapOf<String, Int>()
        val dies = mutableListOf<String>()
        val asked = mutableListOf<Pair<DamageDivision, Map<MtgRef, Int>>>()
        val turn = s.turn.number
        var assumed = false
        repeat(400) {
            val input: Input = when (val d = s.pending?.decision) {
                is Decision.Divide -> {
                    val division = division(s, ids)!!
                    val wanted = divisions[division.source]?.map { (ref, n) -> Share(ids.target(ref), n) }
                    val shares = wanted?.takeIf { w -> runCatching { Engine.perform(s, Input.Answer(d.id, Answer.Division(w))) }.isSuccess } ?: d.suggested
                    asked += division to shares.associate { ids.ref(it.target) to it.amount }
                    Input.Answer(d.id, Answer.Division(shares))
                }
                null -> {
                    val holder = s.priority.holder
                    when {
                        holder != null -> Input.PassPriority(holder)
                        s.turn.step == TurnStep.DeclareBlockers -> {
                            val defender = s.players.firstOrNull { p -> Engine.options(s, p.id).any { it is Input.DeclareBlockers } } ?: return null
                            Input.DeclareBlockers(defender.id, blocks.orEmpty().map { Block(objectOf(s, ids, it.blockerId), objectOf(s, ids, it.attackerId)) })
                        }
                        else -> return null
                    }
                }
                is Decision.Random, is Decision.HostFill, is Decision.OtherView -> return null
                is Decision.ManaPayment -> { assumed = true; Input.Answer(d.id, Answer.ManaPayment(d.suggestion)) }
                else -> {
                    assumed = true
                    Engine.options(s, d.player).filterIsInstance<Input.Answer>().firstOrNull() ?: return null
                }
            }
            val outcome = try { Engine.perform(s, input) } catch (_: IllegalArgumentException) { return null }
            var final = false
            for (c in outcome.changes) {
                s.apply(c)
                when (c) {
                    is Change.Damage -> c.dealt.filter { it.combat }.forEach { dd ->
                        dealt += PreviewDamage(card(dd.source), ids.ref(dd.recipient), dd.amount, s.combat?.firstStrikeStep == true)
                        if (s.combat?.firstStrikeStep != true) final = true
                    }
                    is Change.Life -> life[c.player.value] = (life[c.player.value] ?: 0) + c.after - c.before
                    is Change.ZoneChange -> c.moves.filter { it.from == Zone.Battlefield && Meaning.Died in it.meanings }
                        .forEach { dies += card(it.before.id) }
                    else -> Unit
                }
            }
            if (final) return DamagePreview(dealt, life, dies, asked, assumed)
            // Combat is over (no damage dealt in the last step): the preview is what happened on the way
            if (s.combat == null || s.turn.number != turn || s.turn.step !in COMBAT) return DamagePreview(dealt, life, dies, asked, assumed)
        }
        return null
    }

    private val COMBAT = setOf(TurnStep.DeclareAttackers, TurnStep.DeclareBlockers, TurnStep.CombatDamage)
    private val COMBAT_POSITIONS = setOf(TurnPosition.DECLARE_ATTACKERS, TurnPosition.DECLARE_BLOCKERS, TurnPosition.FIRST_STRIKE_DAMAGE, TurnPosition.COMBAT_DAMAGE)

    // ---- Inputs from lanes

    private fun objectOf(s: GameState, ids: CardIds, cardId: String): ObjectId {
        val card = ids.engine(cardId)
        return s.battlefield.firstOrNull { (it as? ObjectId.Card)?.card == card } ?: throw CombatRefused("That card isn't on the battlefield")
    }

    private fun attackInput(call: EngineCall, attacks: List<LaneAttack>): Input.DeclareAttackers {
        val s = call.working
        val defender = s.players.firstOrNull { it.id != s.turn.active && !it.lost }?.id
        val view by lazy { Engine.view(s) }
        return Input.DeclareAttackers(s.turn.active, attacks.map { a ->
            val target = a.target?.let { TargetRef.Object(objectOf(s, call.ids, it)) }
                ?: TargetRef.Player(defender ?: throw CombatRefused("There is no player to attack"))
            val attacker = objectOf(s, call.ids, a.cardId)
            // CMB-037: the optional costs chosen, by their origins on the attacker now (one no longer there is left out)
            val paying = if (a.paying.isEmpty()) emptyList() else view[attacker]?.mechanics.orEmpty()
                .filter { it.mechanic is Mechanic.AsAttacks }.mapNotNull { it.origin }.filter { it.key in a.paying }.distinct()
            Attack(attacker, target, enlisted = a.enlisted.map { objectOf(s, call.ids, it) }, band = a.band, paying = paying)
        })
    }

    private fun blockInput(call: EngineCall, blocks: List<LaneBlock>): Input.DeclareBlockers {
        val s = call.working
        // The player blocking: the controller of the attacked player's or planeswalker's side (two players: the one not active)
        val defender = s.players.firstOrNull { it.id != s.turn.active && !it.lost }?.id ?: throw CombatRefused("There is no defending player")
        return Input.DeclareBlockers(defender, blocks.map { Block(objectOf(s, call.ids, it.blockerId), objectOf(s, call.ids, it.attackerId)) })
    }

    /** The engine's reason for refusing [input] in [s], or null when it takes it. */
    private fun refusal(s: GameState, input: Input): String? = try {
        Engine.perform(s, input)
        null
    } catch (problem: IllegalArgumentException) {
        problem.message ?: "Not allowed"
    } catch (problem: IllegalStateException) {
        problem.message ?: "Not allowed"
    }

    /** The engine's [message] for people: objects by name, players as "a player" or "you". */
    private fun reason(call: EngineCall, message: String): String {
        val s = call.working
        val view = Engine.view(s)
        var text = message.removePrefix("Illegal attack: ").removePrefix("Illegal block: ")
        // TargetRef's own text first, then the objects inside it
        text = Regex("Object\\(id=([^)]+)\\)").replace(text) { it.groupValues[1] }
        text = Regex("Player\\(id=([^)]+)\\)").replace(text) { "the defending player" }
        for (o in s.objects.keys.filterIsInstance<ObjectId.Card>()) {
            val shown = o.toString()
            if (shown in text) text = text.replace(shown, view[o]?.name ?: "a face-down creature")
        }
        return text.replaceFirstChar { it.uppercase() }
    }

    private fun division(s: GameState, ids: CardIds): DamageDivision? {
        val d = s.pending?.decision as? Decision.Divide ?: return null
        val source = d.source as? ObjectId.Card ?: return null
        val lethal = d.lethal.associate { ids.ref(it.target) to it.amount }
        val trample = d.among.firstOrNull { t -> d.lethal.none { it.target == t } }
        return DamageDivision(d.player.value, ids.atw(source.card), d.amount, d.among.map(ids::ref), lethal,
            d.suggested.associate { ids.ref(it.target) to it.amount }, trample?.let(ids::ref), s.combat?.firstStrikeStep == true)
    }

    /**
     * Whether the open decision in [call]'s state is a division of combat damage whose "In order" result can't depend on
     * the order ([Divisions.orderFree], CMB-033): the table answers it with the suggestion at once, without asking.
     */
    fun answeredByItself(call: EngineCall): Boolean = answeredByItself(call.working, call.ids)

    private fun answeredByItself(s: GameState, ids: CardIds): Boolean {
        val d = s.pending?.decision as? Decision.Divide ?: return false
        val combat = s.combat ?: return false
        if (s.turn.step != TurnStep.CombatDamage || d.lethal.isEmpty()) return false
        if (combat.attackers.none { it.id == d.source } && combat.blockers.none { it.id == d.source }) return false
        return Divisions.orderFree(division(s, ids) ?: return false)
    }
}

/**
 * Dividing combat damage as the division dialog offers it (CMB-033; CR 510.1c–d, 702.19b): "In order" gives each
 * creature lethal damage in turn, "Free" any division that obeys the rules.
 */
object Divisions {
    /** The creatures among [d]'s recipients (blockers, or the attackers a blocker blocks), in its order. */
    fun creatures(d: DamageDivision): List<MtgRef> = d.among.filter { it != d.trampleTo }

    /**
     * "In order": each creature of [order] gets lethal damage in turn ([DamageDivision.lethal]) while damage is left; what
     * is left once each has lethal damage goes to the attacked player or planeswalker with trample (CR 702.19b), else to
     * the last creature (as the engine's suggestion has it; combat review #10).
     */
    fun inOrder(d: DamageDivision, order: List<MtgRef>): Map<MtgRef, Int> {
        var left = d.amount
        val shares = LinkedHashMap<MtgRef, Int>()
        for (r in order) { val n = minOf(left, d.lethal[r] ?: 0); shares[r] = n; left -= n }
        val trample = d.trampleTo
        if (trample != null) shares[trample] = left
        else if (order.isNotEmpty()) shares[order.last()] = shares.getValue(order.last()) + left
        return d.among.associateWith { shares[it] ?: 0 }
    }

    /** The creatures of [d] in the order its suggestion gives them lethal damage (its own order). */
    fun suggestedOrder(d: DamageDivision): List<MtgRef> = creatures(d)

    /** Why [shares] can't be [d]'s division (for people), or null: all of the damage is divided (CR 510.1c), and with
     * trample the player or planeswalker gets some only once every creature has lethal damage (CR 702.19b). */
    fun refusal(d: DamageDivision, shares: Map<MtgRef, Int>): String? {
        val total = shares.values.sum()
        if (shares.values.any { it < 0 }) return "Damage can't be negative."
        if (total != d.amount) return "Divide all ${d.amount} damage ($total divided now)."
        val trample = d.trampleTo
        if (trample != null && (shares[trample] ?: 0) > 0 && creatures(d).any { (shares[it] ?: 0) < (d.lethal[it] ?: 0) })
            return "With trample every blocker needs lethal damage before any goes past it."
        return null
    }

    /** Whether "In order" gives the same result in every order (CMB-033): one creature, or damage enough for lethal damage
     * to all of them. The division is then made with the suggestion without asking. */
    fun orderFree(d: DamageDivision): Boolean {
        val creatures = creatures(d)
        return creatures.size <= 1 || d.amount >= creatures.sumOf { d.lethal[it] ?: 0 }
    }
}
