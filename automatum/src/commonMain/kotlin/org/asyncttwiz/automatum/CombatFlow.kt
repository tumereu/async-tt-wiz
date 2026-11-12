package org.asyncttwiz.automatum

import org.asyncttwiz.protocol.game.EnginePreferences
import arcanum.api.CardType
import arcanum.api.CostPart
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.CombatDeclarations
import arcanum.engine.Engine
import arcanum.engine.Pending
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.decision.Share
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.CombatArea
import org.asyncttwiz.protocol.CombatStackRules
import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.MtgRef
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
 * CMB-050 (owner playtest 2026-11-10): the combat damage dealt so far in the combat going on (the first-strike and regular
 * damage steps together), as the combat band shows it until the combat ends:
 *
 * - [targets]: each attack target that combat damage reached, by its lane (the planeswalker's or battle's card id, null for
 *   the defending player), with its net total from all lanes: the damage dealt to a card; for a player the life it lost
 *   to that damage minus the life it gained at the same moment (the damage's results, CR 510.2);
 * - [attackers]: what each attacker (ATW card id) dealt to its own attack target (unblocked, or trample over its
 *   blockers); its pair's lane shows it;
 * - [gained]: the life each player gained at those moments (lifelink, CR 702.15b), by account id.
 */
data class CombatDamageDealt(val targets: Map<String?, Int>, val attackers: Map<String, Int>, val gained: Map<String, Int>) {
    val empty: Boolean get() = targets.isEmpty() && gained.isEmpty()
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
 * with reasons, divisions of combat damage (I-D29) and the damage preview. The table shows the engine's combat
 * ([EngineProjection]: one pair per attacker in its target's lane, blockers in the pair of the first attacker they block).
 *
 * Each call works on an [EngineRun] the caller made; only the player's own inputs of it go out.
 */
object CombatFlow {
    /**
     * [shown] for a game on engine state (I-D56, engine-responsiveness R4a): [state] is its own state (full information,
     * never changed here) and [board] the board the table shows of it (its lanes and records). Never throws.
     */
    fun shown(state: GameState, board: BoardState, gameId: String): CombatShown {
        if (TurnPosition.of(board.turn) !in COMBAT_POSITIONS) return CombatShown.NONE
        return try { shown(EngineRun.of(state, board, gameId) { error("No randomness while looking") }) }
        catch (_: RuntimeException) { CombatShown.NONE }
    }

    /**
     * CMB-050 (owner playtest 2026-11-10): the combat damage dealt so far in [state]'s combat ([CombatDamageDealt]), read from
     * its logged events since the attackers were declared (D33: the log keeps this turn's); null when no combat goes on or
     * no combat damage has been dealt in it. Never throws.
     */
    fun dealt(state: GameState): CombatDamageDealt? = try { dealtIn(state) } catch (_: RuntimeException) { null }

    private fun dealtIn(state: GameState): CombatDamageDealt? {
        val combat = state.combat ?: return null
        if (state.turn.step != TurnStep.CombatDamage && state.turn.step != TurnStep.EndOfCombat) return null
        val log = arcanum.engine.Inspect.events(state)
        val turn = state.turn.number
        // Back to the declaration of this combat's attackers
        var start = log.size
        while (start > 0) {
            val entry = log[start - 1]
            if (entry.turn != turn) break
            val change = entry.change
            if (change is Change.Combat && change.before?.attackers.isNullOrEmpty() && !change.after?.attackers.isNullOrEmpty()) { start--; break }
            start--
        }
        val attackedBy = combat.attackers.associate { it.id to it.target }
        val defender = combat.attackers.firstNotNullOfOrNull { (it.target as? TargetRef.Player)?.id }
        val toTargets = LinkedHashMap<String?, Int>()
        val byAttacker = LinkedHashMap<String, Int>()
        val lost = LinkedHashMap<String, Int>()
        val gained = LinkedHashMap<String, Int>()
        var moment = false
        fun lane(target: TargetRef): String? = when (target) {
            is TargetRef.Player -> null
            is TargetRef.Object -> (target.id as? ObjectId.Card)?.card?.value
        }
        for (i in start until log.size) {
            when (val change = log[i].change) {
                is Change.Damage -> {
                    val combatDamage = change.dealt.filter { it.combat }
                    moment = combatDamage.isNotEmpty()
                    for (dd in combatDamage) {
                        val target = attackedBy[dd.source] ?: continue
                        // Only what reached its own attack target (unblocked, or trample over its blockers)
                        if (dd.recipient != target) continue
                        val attacker = (dd.source as? ObjectId.Card)?.card?.value ?: continue
                        byAttacker[attacker] = (byAttacker[attacker] ?: 0) + dd.amount
                        // A player's total is worked out from its life below
                        lane(target).let { toTargets[it] = (toTargets[it] ?: 0) + if (target is TargetRef.Object) dd.amount else 0 }
                    }
                }
                // The damage's results: life lost and gained at the same moment (lifelink), damage marked, counters
                is Change.Life -> if (moment) {
                    val delta = change.after - change.before
                    val player = change.player.value
                    if (delta < 0) lost[player] = (lost[player] ?: 0) - delta else if (delta > 0) gained[player] = (gained[player] ?: 0) + delta
                }
                // What follows the damage ends its moment (as the damage's hold splits its step, HIS-113): state-based
                // actions moving or removing objects, a player losing, triggers, the next step or priority
                is Change.ZoneChange, is Change.Removed, is Change.Created, is Change.Lost, is Change.StackItemAdded,
                is Change.Step, is Change.Priority, is Change.PendingSet, is Change.Combat -> moment = false
                else -> Unit
            }
        }
        // A player attacked: what it lost to the damage minus what it gained then
        if (null in toTargets && defender != null) toTargets[null] = (lost[defender.value] ?: 0) - (gained[defender.value] ?: 0)
        return CombatDamageDealt(toTargets, byAttacker, gained).takeUnless { it.empty && byAttacker.isEmpty() }
    }

    /** Whether [board]'s combat runs through the engine: attackers were declared through it (its combat record is
     * there, of this combat: #1, not one finished by hand while Automatum was off), so it deals the combat damage
     * itself. */
    fun throughEngine(board: BoardState): Boolean = runCatching { EngineCodec.currentCombat(board) != null }.getOrDefault(false)

    /** What the table shows of combat in [call]'s state now ([CombatShown]). */
    fun shown(call: EngineRun): CombatShown {
        val s = call.working
        val choices = choices(call)
        val open = division(call)
        val firstStrike = firstStrikeStep(call)
        val blocks = if (choices.declaring != null && !choices.attackers) lanes(call.board.combat).second else null
        val previewed = !choices.attackers && (s.turn.step == TurnStep.DeclareBlockers || open != null || firstStrike)
        val turn = s.turn.number
        val early = PreAnswers.of(call.board).filterIsInstance<PreAnswer.Division>().filter { it.turn == null || it.turn == turn }
            .associate { d -> d.source.cardId to d.shares.associate { it.target to it.amount } }
        val preview = if (!previewed) null else try { preview(call, blocks, early) } catch (_: RuntimeException) { null }
        // #3 (CR 510.4): only divisions of the open one's damage step; the regular step's come with that step
        val coming = if (open == null) emptyList() else preview?.divisions.orEmpty().map { it.first }
            .filter { it.source != open.source && it.firstStrike == open.firstStrike }.distinctBy { it.source }
        // CMB-028: the quick actions say why a declaration can't be made yet
        val drafted = if (choices.declaring == null) null else lanes(call.board.combat).let { (attacks, blocks) ->
            withChoices(attacks, call.board) to blocks }
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
    fun choices(call: EngineRun): CombatChoices {
        val s = call.working
        if (s.pending != null) return CombatChoices.NONE
        val ids = call.ids
        fun card(o: ObjectId) = ids.atw((o as ObjectId.Card).card)
        fun lane(t: TargetRef) = (t as? TargetRef.Object)?.id?.let(::card)
        for (p in s.players) {
            val declarations = Engine.options(s, p.id, EnginePreferences.of(s)).filter { it is Input.DeclareAttackers || it is Input.DeclareBlockers }
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
    fun attackRefusal(call: EngineRun, attacks: List<LaneAttack>): String? {
        val input = try { attackInput(call, attacks) } catch (refused: CombatRefused) { return refused.reason }
        return refusal(call.working, input)?.let { reason(call, it) }
    }

    /** Why the engine refuses [blocks] as the blockers declared in [call]'s state (for people), or null. */
    fun blockRefusal(call: EngineRun, blocks: List<LaneBlock>): String? {
        val input = try { blockInput(call, blocks) } catch (refused: CombatRefused) { return refused.reason }
        return refusal(call.working, input)?.let { reason(call, it) }
    }

    /**
     * The active player declares [attacks] (CR 508.1); an empty list declares no attackers. Returns what the engine waits
     * for.
     * @throws CombatRefused when the engine refuses the declaration; nothing changes then.
     */
    fun declareAttackers(call: EngineRun, attacks: List<LaneAttack>, autotap: Boolean = false,
        pick: (Decision.ManaPayment) -> List<ManaUse> = { it.suggestion }): Pending? {
        val input = attackInput(call, attacks)
        refusal(call.working, input)?.let { throw CombatRefused(reason(call, it)) }
        return paid(call, input.player, call.perform(input), autotap, pick)
    }

    /**
     * CMB-037 (CR 508.1h–j, 509.1d–f, I-D47): what the engine waits for after a declaration with costs ([pending]): with
     * [autotap] the mana it asks [player] to pay is paid with its suggestion in the same call (as [pick] takes it), as casting
     * does; any other choice of the payment (what to sacrifice) stays open for the shared flow's panel.
     */
    private fun paid(call: EngineRun, player: PlayerId, pending: Pending?, autotap: Boolean,
        pick: (Decision.ManaPayment) -> List<ManaUse>): Pending? {
        var open = pending
        while (autotap) {
            val d = open?.decision as? Decision.ManaPayment ?: break
            if (d.player != player) break
            val answer = Input.Answer(d.id, Answer.ManaPayment(pick(d)))
            if (refusal(call.working, answer) != null) break
            open = call.perform(answer)
        }
        return open
    }

    /**
     * The defending player declares [blocks] (CR 509.1); an empty list declares no blockers.
     * @throws CombatRefused when the engine refuses the declaration; nothing changes then.
     */
    fun declareBlockers(call: EngineRun, blocks: List<LaneBlock>, autotap: Boolean = false,
        pick: (Decision.ManaPayment) -> List<ManaUse> = { it.suggestion }): Pending? {
        val input = blockInput(call, blocks)
        refusal(call.working, input)?.let { throw CombatRefused(reason(call, it)) }
        return paid(call, input.player, call.perform(input), autotap, pick)
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
    private fun attackExtras(call: EngineRun, player: PlayerId, attackers: CombatChoices): CombatChoices {
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
        val optional = optionalCosts(call, view, candidates)
        val banding = candidates.filter { (_, o) -> view[o]?.characteristics?.hasKeyword(Keyword.Banding) == true }.map { it.first }.toSet()
        return attackers.copy(enlist = enlist, enlistable = enlistable, optional = optional, banding = banding)
    }

    /**
     * CMB-037, CMB-044 (CR 508.1g, 508.1h): the optional "as it attacks" costs each of [candidates] (ATW card and object) may
     * choose, as toggle chips: worked out by the engine for a declaration of every candidate attacking the defending player
     * and paying all of them ([CombatDeclarations.attackCosts]), an X as its amount.
     */
    private fun optionalCosts(call: EngineRun, view: arcanum.engine.view.View, candidates: List<Pair<String, ObjectId>>): Map<String, List<CombatCostShown>> {
        val s = call.working
        val offered = candidates.mapNotNull { (atw, o) ->
            view[o]?.mechanics.orEmpty().filter { it.mechanic is Mechanic.AsAttacks && it.origin != null }.takeIf { it.isNotEmpty() }?.let { Triple(atw, o, it) }
        }
        if (offered.isEmpty()) return emptyMap()
        val defender = s.players.firstOrNull { it.id != s.turn.active && !it.lost }?.id ?: return emptyMap()
        val worked = worked(call, "the optional costs to attack of ${offered.map { it.first }}") {
            CombatDeclarations.attackCosts(s, offered.map { (_, o, ms) -> Attack(o, TargetRef.Player(defender), paying = ms.mapNotNull { it.origin }.distinct()) }, view)
        }
        return offered.associate { (atw, o, ms) ->
            // [offered] keeps only mechanics with an origin.
            atw to ms.mapNotNull { m ->
                val origin = m.origin ?: return@mapNotNull null
                val cost = worked?.let { costs -> costs.firstOrNull { it.creature == o && it.ability.origin == m.origin }?.cost ?: run {
                    AutomatumLog.error(call.gameId, "combat costs", "No optional cost to attack worked out for $atw's ${m.origin}: shown as printed")
                    null
                } }
                CombatCostShown(label(call, atw, m, cost), abilityText(s, m.source ?: o, origin), origin.key)
            }
        }
    }

    /** CMB-044 (CR 508.1h): each staged attacker's share of the costs to attack the declaration the lanes hold, [attacks],
     * asks: its "can't attack … unless … pays" costs as the engine works them out for the whole declaration
     * ([CombatDeclarations.attackCosts]); the optional costs chosen are chips of their own ([optionalCosts]). */
    private fun attackCosts(call: EngineRun, attacks: List<LaneAttack>): Map<String, List<CombatCostShown>> {
        val s = call.working
        val staged = attacks.filter { a -> runCatching { objectOf(s, call.ids, a.cardId); a.target?.let { objectOf(s, call.ids, it) } }.isSuccess }
        if (staged.isEmpty()) return emptyMap()
        val view = Engine.view(s)
        val asked = staged.mapNotNull { a -> objectOf(s, call.ids, a.cardId).let { o ->
            view[o]?.mechanics.orEmpty().filter { it.mechanic is Mechanic.AttackCost }.takeIf { it.isNotEmpty() }?.let { Triple(a.cardId, o, it) } } }
        if (asked.isEmpty()) return emptyMap()
        val worked = worked(call, "the costs to attack with ${staged.map { it.cardId }}") { CombatDeclarations.attackCosts(s, attackInput(call, staged).attacks, view) }
        return shares(call, asked, worked?.filter { it.ability.mechanic is Mechanic.AttackCost })
    }

    /** CMB-044 (CR 509.1d): each staged blocker's share of the costs to block the declaration the lanes hold, [blocks], asks
     * ([CombatDeclarations.blockCosts]): one for each attacker a cost applies to as it blocks. */
    private fun blockCosts(call: EngineRun, blocks: List<LaneBlock>): Map<String, List<CombatCostShown>> {
        val s = call.working
        val staged = blocks.filter { b -> runCatching { objectOf(s, call.ids, b.blockerId); objectOf(s, call.ids, b.attackerId) }.isSuccess }
        if (staged.isEmpty()) return emptyMap()
        val view = Engine.view(s)
        val asked = staged.distinctBy { it.blockerId }.mapNotNull { b -> objectOf(s, call.ids, b.blockerId).let { o ->
            view[o]?.mechanics.orEmpty().filter { it.mechanic is Mechanic.BlockCost }.takeIf { it.isNotEmpty() }?.let { Triple(b.blockerId, o, it) } } }
        if (asked.isEmpty()) return emptyMap()
        val worked = worked(call, "the costs to block with ${staged.map { it.blockerId }}") { CombatDeclarations.blockCosts(s, blockInput(call, staged).blocks, view) }
        return shares(call, asked, worked)
    }

    /** The engine's costs of a declaration ([what]), or null after logging as an error why they couldn't be worked out. */
    private fun worked(call: EngineRun, what: String, costs: () -> List<arcanum.engine.combat.CombatCost>): List<arcanum.engine.combat.CombatCost>? = try {
        costs()
    } catch (problem: RuntimeException) {
        AutomatumLog.error(call.gameId, "combat costs", "Couldn't work out $what (turn ${call.working.turn.number}, ${call.working.turn.step}): $problem")
        null
    }

    /**
     * CMB-044: the chips of [asked] (each creature with a cost, by ATW card, and the abilities asking it) from the
     * declaration's [worked] costs: each cost that asks something, under the creature it falls on (none for a cost that
     * asks nothing, such as {0}). When [worked] is null (couldn't be worked out, logged) each ability shows its printed cost.
     */
    private fun shares(call: EngineRun, asked: List<Triple<String, ObjectId, List<arcanum.engine.view.AppliedMechanic>>>,
        worked: List<arcanum.engine.combat.CombatCost>?): Map<String, List<CombatCostShown>> {
        val s = call.working
        return asked.mapNotNull { (card, o, ms) ->
            val chips = if (worked == null) ms.map { m -> CombatCostShown(label(call, card, m, null), abilityText(s, m.source ?: o, m.origin)) }
                else worked.filter { it.creature == o && !asksNothing(it.cost) }
                    .map { c -> CombatCostShown(label(call, card, c.ability, c.cost), abilityText(s, c.ability.source ?: o, c.ability.origin)) }
            chips.takeIf { it.isNotEmpty() }?.let { card to it }
        }.toMap()
    }

    /** Whether [cost] asks nothing: only mana, and none of it ({0}, an X worked out as 0). */
    private fun asksNothing(cost: arcanum.api.Cost): Boolean =
        cost.parts.all { part -> part is CostPart.Mana && part.cost.symbols.all { it == arcanum.api.ManaSymbol.Generic(0) } }

    /**
     * CMB-044 (owner-reported 2026-11-11): the chip's words for the cost [m] asks of ATW card [card]: [now], the engine's
     * cost with its X worked out ("{3}", never "{X}", CR 508.1h, 509.1d), or [m]'s printed cost when it couldn't be worked
     * out (logged). A cost whose X is worked out goes into the Automatum log when its amount changes.
     */
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private fun label(call: EngineRun, card: String, m: arcanum.engine.view.AppliedMechanic, now: arcanum.api.Cost?): String {
        val printed = when (val mechanic = m.mechanic) {
            is Mechanic.AttackCost -> mechanic.cost
            is Mechanic.BlockCost -> mechanic.cost
            is Mechanic.AsAttacks -> mechanic.cost
            else -> return now?.let(::costLabel).orEmpty()
        }
        val label = costLabel(now ?: printed)
        if (printed.parts.any { it is CostPart.Mana && (it.x != null || it.times != null) }) {
            val key = "${call.gameId}/$card/${m.source}"
            val logged = loggedCosts.load()
            if (logged[key] != label) {
                loggedCosts.store((if (logged.size >= 64) emptyMap() else logged) + (key to label))
                AutomatumLog.flow(call.gameId, "combat", "cost", "$card pays ${costLabel(printed)} as $label now (turn ${call.working.turn.number}, ${call.working.turn.step})")
            }
        }
        return label
    }

    /** The amounts of costs with an X last logged ([label]), by game, card and source. */
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private val loggedCosts = kotlin.concurrent.atomics.AtomicReference<Map<String, String>>(emptyMap())

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

    // A class's name without its outer classes: a shrunk Android build may say "KeywordAction$Exert" where the JVM says "Exert"
    private fun words(name: String?): String = name.orEmpty().substringAfterLast('$').replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase()
        .replaceFirstChar { it.uppercase() }

    /**
     * Whether the combat damage step now is the first-strike one, a second following (CR 510.4). ATW shows it as its
     * own step, `TurnPosition.FIRST_STRIKE_DAMAGE` (I-D31).
     */
    fun firstStrikeStep(call: EngineRun): Boolean =
        call.working.turn.step == TurnStep.CombatDamage && call.working.combat?.firstStrikeStep == true

    /** Whether [board]'s combat has had a first-strike combat damage step (CR 510.4, I-D31): the phase dialog shows that
     * step while the combat lasts. Never throws; false without Automatum's combat record. */
    fun hadFirstStrikeStep(board: BoardState): Boolean =
        runCatching { EngineCodec.currentCombat(board)?.firstStrikers != null }.getOrDefault(false)

    /** The open division of combat damage in [call]'s state, or null. */
    fun division(call: EngineRun): DamageDivision? = division(call.working, call.ids)

    /**
     * Answers the open division with [shares] (damage by ATW reference; null: the suggestion, I-D29).
     * @throws IllegalArgumentException when the engine refuses the division.
     */
    fun divide(call: EngineRun, shares: Map<MtgRef, Int>? = null): Pending? {
        val d = call.working.pending?.decision as? Decision.Divide ?: throw IllegalArgumentException("No division is open")
        val answer = Answer.Division(shares?.map { (ref, amount) -> Share(call.ids.target(ref), amount) } ?: d.suggested)
        Engine.perform(call.working, Input.Answer(d.id, answer), EnginePreferences.of(call.working))
        return call.perform(Input.Answer(d.id, answer))
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
    fun preview(call: EngineRun, blocks: List<LaneBlock>? = null, divisions: Map<String, Map<MtgRef, Int>> = emptyMap()): DamagePreview? {
        val s = call.working.deepCopy()
        if (s.combat?.attackers.isNullOrEmpty()) return null
        val ids = call.ids
        fun card(o: ObjectId) = ids.atw((o as ObjectId.Card).card)
        StepFlow.settledPriority(s)?.let { if (it != s.priority) s.apply(Change.Priority(s.priority, it)) }
        val dealt = mutableListOf<PreviewDamage>()
        val life = mutableMapOf<String, Int>()
        val dies = mutableListOf<String>()
        val asked = mutableListOf<Pair<DamageDivision, Map<MtgRef, Int>>>()
        val turn = s.turn.number
        var assumed = false
        repeat(400) {
            val input: Input = when (val d = s.pending?.decision) {
                is Decision.Divide -> {
                    val division = division(s, ids)
                    if (division == null) {
                        // Not a card's division: answered with its suggestion, like another decision on the way.
                        Faults.report("CombatFlow.preview", "division without a card source", mapOf("game" to call.gameId,
                            "source" to d.source.toString(), "player" to d.player.value))
                        assumed = true
                        Input.Answer(d.id, Answer.Division(d.suggested))
                    } else {
                        val wanted = divisions[division.source]?.map { (ref, n) -> Share(ids.target(ref), n) }
                        val shares = wanted?.takeIf { w -> runCatching { Engine.perform(s, Input.Answer(d.id, Answer.Division(w)), EnginePreferences.of(s)) }.isSuccess } ?: d.suggested
                        asked += division to shares.associate { ids.ref(it.target) to it.amount }
                        Input.Answer(d.id, Answer.Division(shares))
                    }
                }
                null -> {
                    val holder = s.priority.holder
                    when {
                        holder != null -> Input.PassPriority(holder)
                        s.turn.step == TurnStep.DeclareBlockers -> {
                            val defender = s.players.firstOrNull { p -> Engine.options(s, p.id, EnginePreferences.of(s)).any { it is Input.DeclareBlockers } } ?: return null
                            Input.DeclareBlockers(defender.id, blocks.orEmpty().map { Block(objectOf(s, ids, it.blockerId), objectOf(s, ids, it.attackerId)) })
                        }
                        else -> return null
                    }
                }
                is Decision.Random, is Decision.NamedCard -> return null
                is Decision.ManaPayment -> { assumed = true; Input.Answer(d.id, Answer.ManaPayment(d.suggestion)) }
                else -> {
                    assumed = true
                    Engine.options(s, d.player, EnginePreferences.of(s)).filterIsInstance<Input.Answer>().firstOrNull() ?: return null
                }
            }
            val outcome = try { Engine.perform(s, input, EnginePreferences.of(s)) } catch (_: IllegalArgumentException) { return null }
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

    private fun attackInput(call: EngineRun, attacks: List<LaneAttack>): Input.DeclareAttackers {
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

    private fun blockInput(call: EngineRun, blocks: List<LaneBlock>): Input.DeclareBlockers {
        val s = call.working
        // The player blocking: the controller of the attacked player's or planeswalker's side (two players: the one not active)
        val defender = s.players.firstOrNull { it.id != s.turn.active && !it.lost }?.id ?: throw CombatRefused("There is no defending player")
        return Input.DeclareBlockers(defender, blocks.map { Block(objectOf(s, call.ids, it.blockerId), objectOf(s, call.ids, it.attackerId)) })
    }

    /** The engine's reason for refusing [input] in [s], or null when it takes it. */
    private fun refusal(s: GameState, input: Input): String? = try {
        Engine.perform(s, input, EnginePreferences.of(s))
        null
    } catch (problem: IllegalArgumentException) {
        problem.message ?: "Not allowed"
    } catch (problem: IllegalStateException) {
        problem.message ?: "Not allowed"
    }

    /** The engine's [message] for people: objects by name, players as "a player" or "you". */
    private fun reason(call: EngineRun, message: String): String {
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
    fun answeredByItself(call: EngineRun): Boolean = answeredByItself(call.working, call.ids)

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
