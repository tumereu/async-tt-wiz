package arcanum.engine

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Zone
import arcanum.engine.casting.Legality
import arcanum.engine.casting.Snapshot
import arcanum.engine.combat.Combat
import arcanum.engine.combat.CombatRules
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.mana.Mana
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId

/**
 * [Engine.options]: what a player may do now (CR 116, 117.1).
 *
 * - With a decision open: answers to it when they can be listed (yes/no, one target, one object, one mode, a colour, an
 *   option, a number at its least, the suggested mana payment, an even division), and [Input.Cancel] for a cast. Any
 *   player may answer (P7), so they are offered to everyone.
 * - While attackers or blockers are awaited: declaring none, and each single legal attack or block; the declaration
 *   inputs accept any legal combination.
 * - With priority: passing, each land they may play, each spell they may begin to cast (every castable face, from the
 *   hand or with a permission, and each alternative cost that applies) and each ability they may activate, mana
 *   abilities included. Costs and targets are checked as far as they can be without choices: a listed spell may still
 *   turn out uncastable for want of targets or mana.
 */
internal object Options {
    fun of(state: GameState, player: PlayerId): List<Input> {
        val snap = Snapshot(state)
        state.pending?.let { return answers(snap, it) }
        if (Combat.waitingForDeclaration(state)) return declarations(snap, player)
        if (state.priority.holder != player) return emptyList()
        return buildList {
            add(Input.PassPriority(player))
            lands(snap, player).forEach { add(it) }
            spells(snap, player).forEach { add(it) }
            abilities(snap, player).forEach { add(it) }
        }
    }

    private fun answers(snap: Snapshot, pending: Pending): List<Input> {
        val d = pending.decision
        val list: List<Answer> = when (d) {
            is Decision.May -> listOf(Answer.YesNo(true), Answer.YesNo(false))
            is Decision.Targets -> if (d.max == 1 || d.min <= 1) d.options.map { Answer.Targets(listOf(it)) } +
                (if (d.min == 0) listOf(Answer.Targets(emptyList())) else emptyList()) else emptyList()
            is Decision.ChooseObjects -> if (d.min <= 1 && (d.max ?: 2) >= 1) d.options.map { Answer.Objects(listOf(it)) } else emptyList()
            is Decision.Modes -> if (d.min <= 1) d.options.filter { it.available }.map { Answer.Modes(listOf(it.index)) } else emptyList()
            is Decision.ChooseColor -> d.among.map { Answer.ColorChoice(it) }
            is Decision.ChooseOption -> d.options.map { Answer.Option(it) }
            is Decision.ChoosePlayer -> d.options.map { Answer.Player(it) }
            is Decision.Announce -> listOf(Answer.Number(d.min))
            is Decision.ChooseNumber -> listOf(Answer.Number(d.min))
            is Decision.ManaPayment -> if (d.suggestion.isNotEmpty()) listOf(Answer.ManaPayment(d.suggestion)) else emptyList()
            is Decision.Divide -> if (d.among.isNotEmpty()) listOf(Answer.Division(evenly(d.amount, d))) else emptyList()
            is Decision.Manual -> listOf(Answer.Done)
            else -> emptyList()
        }
        return list.map { Input.Answer(d.id, it) } + (if (pending.continuation is Continuation.Announcing) listOf(Input.Cancel) else emptyList())
    }

    private fun evenly(amount: Int, d: Decision.Divide): List<Share> {
        val n = d.among.size
        return d.among.mapIndexed { i, t -> Share(t, amount / n + if (i < amount % n) 1 else 0) }
    }

    private fun declarations(snap: Snapshot, player: PlayerId): List<Input> {
        val s = snap.state
        return when {
            s.combat == null -> {
                if (player != s.turn.active) return emptyList()
                val targets = CombatRules.attackTargets(snap, player)
                listOf(Input.DeclareAttackers(player, emptyList())) + s.battlefield.flatMap { id ->
                    targets.filter { CombatRules.canAttack(snap, player, id, it) }.map { Input.DeclareAttackers(player, listOf(Attack(id, it))) }
                }.filter { CombatRules.illegalAttack(snap, player, it.attacks) == null }
            }
            player in Combat.pendingDefenders(snap) -> listOf(Input.DeclareBlockers(player, emptyList())) +
                s.battlefield.flatMap { b ->
                    s.combat!!.attackers.filter { CombatRules.canBlock(snap, player, b, it.id) }.map { a -> Input.DeclareBlockers(player, listOf(Block(b, a.id))) }
                }.filter { CombatRules.illegalBlock(snap, player, it.blocks) == null }
            else -> emptyList()
        }
    }

    /** Lands in hand, and with a permission elsewhere (CR 305.1, D41). */
    private fun lands(snap: Snapshot, player: PlayerId): List<Input> = candidates(snap, player).flatMap { card ->
        val faces = snap.state.obj(card)?.definition?.faces?.indices?.toList().orEmpty()
        faces.filter { face -> Legality.cantPlayLand(snap, player, card, face) == null }.map { Input.PlayLand(player, card, it) }
    }

    /** Cards in the player's hand and cards elsewhere they have a permission for. */
    private fun candidates(snap: Snapshot, player: PlayerId) = snap.state.player(player).hand +
        listOf(Zone.Graveyard, Zone.Exile, Zone.Library, Zone.Command).flatMap { zone ->
            when (zone) {
                Zone.Exile -> snap.state.exile
                Zone.Command -> snap.state.command
                else -> snap.state.players.flatMap { p -> snap.state.zone(zone, p.id) }
            }
        }.filter { id -> snap.view[id]?.mechanics.orEmpty().any { it.mechanic is arcanum.api.Mechanic.MayPlay } }

    private fun spells(snap: Snapshot, player: PlayerId): List<Input> = candidates(snap, player).flatMap { card ->
        val o = snap.state.obj(card) ?: return@flatMap emptyList()
        val fromHand = o.zone == Zone.Hand && o.owner == player
        val permissions = if (fromHand) listOf(null) else Legality.permissions(snap, player, card, arcanum.api.PlayKind.Cast).map { it.first }
        val alternatives = listOf(null) + Legality.alternativeCosts(snap, card).map { it.first.origin }
        Legality.castableFaces(o).flatMap { face ->
            permissions.flatMap { permission ->
                alternatives.map { alt -> CastWay(face, alt, permission) }
            }
        }.filter { way -> Legality.cantCast(snap, player, card, way) == null && affordable(snap, player, card, way) }
            .map { Input.Cast(player, card, it) }
    }

    /** A rough check that the plain mana cost could be paid (X counted as 0); alternative costs are offered as they are. */
    private fun affordable(snap: Snapshot, player: PlayerId, card: arcanum.engine.state.ObjectId, way: CastWay): Boolean {
        if (way.alternative != null || way.permission != null) return true
        val cost = Legality.face(snap.state.objOrThrow(card), way)?.manaCost ?: return false
        val plain = Mana.withX(cost, emptyMap())
        if (plain.symbols.isEmpty()) return true
        return Mana.suggest(snap, player, plain, null).isNotEmpty()
    }

    private fun abilities(snap: Snapshot, player: PlayerId): List<Input> {
        val s = snap.state
        val sources = s.battlefield + s.player(player).hand + s.player(player).graveyard + s.exile + s.command
        return sources.flatMap { id ->
            (snap.view[id]?.characteristics?.abilities.orEmpty() + Legality.faceDownActions(snap, id))
                .filter { it.ability is Ability.Activated || it.ability is Ability.SpecialAction }
                .filter { Legality.cantActivate(snap, player, id, it) == null }
                .map { Input.Activate(player, id, it.origin) }
        }
    }
}
