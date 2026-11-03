package arcanum.engine.combat

import arcanum.api.Keyword
import arcanum.engine.input.Attack
import arcanum.engine.casting.Snapshot
import arcanum.engine.state.Blocker
import arcanum.engine.state.CombatState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId

/**
 * Banding (CR 702.22): the attacking player may declare attacking bands ([Attack.band]: attackers with the same number are one
 * band) of creatures with banding and up to one without, all attacking the same player, planeswalker or battle; a band lasts for
 * the rest of combat. A creature blocking one member of a band blocks every member ([blocks]); the defending player assigns the
 * combat damage of an attacker blocked by a creature with banding, the active player that of a blocker blocking a creature with
 * banding ([assigner]). "Bands with other" isn't a keyword of the API yet.
 */
internal object Banding {
    private fun banding(snap: Snapshot, id: ObjectId) = snap.view[id]?.characteristics?.hasKeyword(Keyword.Banding) == true

    /** CR 702.22c, d: why the bands of [attacks] can't be declared, or null. */
    fun illegal(snap: Snapshot, attacks: List<Attack>): String? {
        attacks.filter { it.band != null }.groupBy { it.band }.forEach { (band, members) ->
            if (members.size < 2) return "band $band has only one creature"
            if (members.count { !banding(snap, it.attacker) } > 1) return "a band has creatures with banding and at most one without (CR 702.22c)"
            if (members.map { it.target }.distinct().size > 1) return "all creatures in a band attack the same player, planeswalker or battle (CR 702.22d)"
        }
        return null
    }

    /** The other members of the band [attacker] is in. */
    private fun mates(combat: CombatState, attacker: ObjectId): List<ObjectId> {
        val band = combat.attackers.firstOrNull { it.id == attacker }?.band ?: return emptyList()
        return combat.attackers.filter { it.band == band && it.id != attacker }.map { it.id }
    }

    /** CR 702.22h: a creature blocking a member of a band blocks each other member too. */
    fun blocks(combat: CombatState, blocker: Blocker): Blocker =
        blocker.copy(attackers = (blocker.attackers + blocker.attackers.flatMap { mates(combat, it) }).distinct())

    /** CR 702.22j, k: who assigns [id]'s combat damage when banding changes it; null: its controller (CR 510.1c, d). */
    fun assigner(snap: Snapshot, combat: CombatState, id: ObjectId): PlayerId? {
        if (combat.attackers.any { it.id == id }) {
            val blockedBy = combat.blockers.filter { id in it.attackers }
            return blockedBy.firstOrNull { banding(snap, it.id) }?.let { it.player ?: snap.board.controller(it.id) }
        }
        val blocking = combat.blockers.firstOrNull { it.id == id }?.attackers.orEmpty()
        return if (blocking.any { banding(snap, it) }) snap.state.turn.active else null
    }
}
