package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
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
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.TurnPosition

/** An attack as ATW's lanes hold it (CMB-012): the creature [cardId] attacks the planeswalker or battle card [target]
 * (its lane), or the defending player when null (as `CombatPair.attackTarget`). */
data class LaneAttack(val cardId: String, val target: String? = null)

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
    /** Why the engine can't take this board (then nothing is offered). */
    val unsupported: List<String> = emptyList(),
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
 */
data class DamagePreview(
    val dealt: List<PreviewDamage>,
    val life: Map<String, Int>,
    val dies: List<String>,
    val divisions: List<Pair<DamageDivision, Map<MtgRef, Int>>>,
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
 * - [alsoBlocks]: each blocker that blocks more than one attacker (CR 509.1a) and how many more: the lanes show it only
 *   in the pair of the first;
 * - [early]: the divisions of combat damage still to come in this step after the open one (CR 510.1c–d): their players
 *   may answer them early (I-D33, [PreAnswers]);
 * - [passing]: who holds priority with an empty Stack in combat once blockers are declared (the declare blockers step,
 *   the first-strike damage step): their pass goes on to the (next) combat damage; null otherwise;
 * - [firstStrikeStep]: the combat damage step now is the first-strike one (CR 510.4).
 */
data class CombatShown(
    val choices: CombatChoices,
    val preview: DamagePreview? = null,
    val alsoBlocks: Map<String, Int> = emptyMap(),
    val early: List<DamageDivision> = emptyList(),
    val passing: String? = null,
    val firstStrikeStep: Boolean = false,
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
     * What the table shows of combat on [play] now ([CombatShown]); [CombatShown.NONE] with Automatum off, outside combat
     * or on a board the engine can't take over. Worked out with full information (I-D17, I-D27; the app shows only what
     * each player may see). Never throws.
     */
    fun shown(play: PlayState, definitions: Map<String, CardDefinition>, log: List<LoggedChange>): CombatShown {
        if (!play.board.automatum || TurnPosition.of(play.board.turn) !in COMBAT_POSITIONS) return CombatShown.NONE
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
        val also = s.combat?.blockers.orEmpty().filter { it.attackers.size > 1 }
            .associate { call.ids.atw((it.id as ObjectId.Card).card) to it.attackers.size - 1 }
        val combat = s.combat
        val passing = s.priority.holder?.value?.takeIf {
            s.pending == null && s.stack.isEmpty() && combat != null && combat.attackers.isNotEmpty() &&
                ((s.turn.step == TurnStep.DeclareBlockers && combat.blocksDecided) || firstStrike)
        }
        return CombatShown(choices, preview, also, coming, passing, firstStrike)
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
                return CombatChoices(p.id.value, attackers = true, attacks = attacks,
                    requiredAttacks = listed.first().attacks.map { LaneAttack(card(it.attacker), lane(it.target)) })
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
            return CombatChoices(p.id.value, attackers = false, blocks = blocks,
                requiredBlocks = listed.first().blocks.map { LaneBlock(card(it.blocker), card(it.attacker)) })
        }
        return CombatChoices.NONE
    }

    /**
     * The attacks and blocks ATW's lanes [area] hold (a client's draft of a declaration, or a combat declared by hand):
     * each attacker of a pair attacks the pair's target, each blocker blocks every attacker of its pair.
     */
    fun lanes(area: CombatArea?): Pair<List<LaneAttack>, List<LaneBlock>> {
        val pairs = area?.pairs.orEmpty()
        return pairs.flatMap { p -> p.attackers.map { LaneAttack(it.sourceId, p.attackTarget) } } to
            pairs.flatMap { p -> p.blockers.flatMap { b -> p.attackers.map { LaneBlock(b.sourceId, it.sourceId) } } }
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
    fun declareAttackers(call: EngineCall, attacks: List<LaneAttack>): Pending? {
        val input = attackInput(call, attacks)
        refusal(call.working, input)?.let { throw CombatRefused(reason(call, it)) }
        call.perform(input)?.let { return it }
        return StepFlow.automatic(call)
    }

    /**
     * The defending player declares [blocks] (CR 509.1); an empty list declares no blockers. Then the host's automatic
     * passes.
     * @throws CombatRefused when the engine refuses the declaration; nothing changes then.
     */
    fun declareBlockers(call: EngineCall, blocks: List<LaneBlock>): Pending? {
        val input = blockInput(call, blocks)
        refusal(call.working, input)?.let { throw CombatRefused(reason(call, it)) }
        call.perform(input)?.let { return it }
        return StepFlow.automatic(call)
    }

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
     * Null when there is no combat with attackers, attackers are still awaited, or getting to the damage needs another
     * decision or a random result (a trigger's choice while the Stack resolves).
     */
    fun preview(call: EngineCall, blocks: List<LaneBlock>? = null, divisions: Map<String, Map<MtgRef, Int>> = emptyMap()): DamagePreview? {
        val s = call.working.deepCopy()
        if (s.combat?.attackers.isNullOrEmpty()) return null
        val ids = call.ids
        fun card(o: ObjectId) = ids.atw((o as ObjectId.Card).card)
        HostPriority.wanted(s)?.let { if (it != s.priority) s.apply(Change.Priority(s.priority, it)) }
        val dealt = mutableListOf<PreviewDamage>()
        val life = mutableMapOf<String, Int>()
        val dies = mutableListOf<String>()
        val asked = mutableListOf<Pair<DamageDivision, Map<MtgRef, Int>>>()
        val turn = s.turn.number
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
                else -> return null
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
            if (final) return DamagePreview(dealt, life, dies, asked)
            // Combat is over (no damage dealt in the last step): the preview is what happened on the way
            if (s.combat == null || s.turn.number != turn || s.turn.step !in COMBAT) return DamagePreview(dealt, life, dies, asked)
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
        return Input.DeclareAttackers(s.turn.active, attacks.map { a ->
            val target = a.target?.let { TargetRef.Object(objectOf(s, call.ids, it)) }
                ?: TargetRef.Player(defender ?: throw CombatRefused("There is no player to attack"))
            Attack(objectOf(s, call.ids, a.cardId), target)
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
}
