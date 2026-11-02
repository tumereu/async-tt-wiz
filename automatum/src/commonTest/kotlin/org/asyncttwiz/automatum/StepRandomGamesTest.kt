package org.asyncttwiz.automatum

import arcanum.engine.Engine
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.PlayZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Seeded random games played through the I3 API (integration spec §3 I3): passes with [StepFlow] (the step button, "End
 * turn", the host's automatic passes), declarations and divisions with [CombatFlow], other decisions with
 * [StepFlow.answer], casts and abilities as plain engine inputs. Every call is one ATW batch checked by [RoundTrip]
 * (rebuilt state = the engine's own, undo restores the board), over many turns.
 */
class StepRandomGamesTest {
    init { CombatCards.ensured }

    private val aliceDeck = List(7) { "Mountain" } + List(4) { "Swamp" } + List(3) { "Plains" } + listOf("Storm Spark",
        "Cinder Bolt", "Curse Word", "Festering Gremlin", "Tinkered Mite", "Tinkered Mite", "Viper Adept", "Grim Prowler",
        "Bloodbond Acolyte", "Hill Ogre", "Twinblade Duelist", "Dawnlance Squire", "Gloomblossom Carrier", "Hill Ogre")
    private val bobDeck = List(8) { "Island" } + List(6) { "Forest" } + listOf("Canopy Visionary", "Glance Ahead",
        "Gearwing Glider", "Gearwing Glider", "Ironhide Brute", "Stout Bear", "Stout Bear", "Haven Warden",
        "Sunning Mudlizard", "Time Stride", "Tinkered Mite", "Mull It Over", "Stout Bear")

    private class Result(val turns: Int, val calls: Int, val attacks: Int, val divisions: Int, val stop: String)

    private fun play(seed: Long, calls: Int): Result {
        val random = Random(seed)
        val table = AtwTable("turns-$seed")
        for ((player, deck) in listOf(table.alice to aliceDeck, table.bob to bobDeck)) {
            val shuffled = deck.shuffled(random)
            table.put(player, PlayZone.HAND, *shuffled.take(7).toTypedArray())
            table.put(player, PlayZone.LIBRARY, *shuffled.drop(7).toTypedArray())
        }
        val rt = RoundTrip(table.state(), table.definitions, seed)
        var done = 0
        var attacks = 0
        var divisions = 0
        var stop = "calls"
        while (done < calls) {
            val s = rt.engine()
            Engine.result(s)?.let { stop = "result $it"; return Result(s.turn.number, done, attacks, divisions, stop) }
            val decision = s.pending?.decision
            when {
                decision is Decision.Divide -> {
                    val listed = Engine.options(s, decision.player).filterIsInstance<Input.Answer>()
                    if (random.nextBoolean() || listed.isEmpty()) rt.run(decision.player.value) { CombatFlow.divide(it) }
                    else rt.run(decision.player.value) { StepFlow.answer(it, listed.random(random).answer) }
                    divisions++
                }
                decision != null -> {
                    val options = Engine.options(s, decision.player)
                    if (options.none { it is Input.Answer }) fail("seed $seed: no answer listed for $decision")
                    when (val input = options.random(random)) {
                        is Input.Answer -> rt.run(decision.player.value) { StepFlow.answer(it, input.answer) }
                        is Input.Cancel -> rt.perform(input, input.player.value)
                        else -> fail("seed $seed: $input offered while $decision is open")
                    }
                }
                s.priority.holder == null -> {
                    // A declaration is awaited: a random choice among what the engine allows, else the least it takes
                    val call = rt.call()
                    val choices = CombatFlow.choices(call)
                    val player = choices.declaring ?: fail("seed $seed: nobody holds priority and nothing is awaited in ${s.turn}")
                    if (choices.attackers) {
                        val chosen = choices.attacks.entries.filter { random.nextBoolean() }.map { (card, lanes) -> LaneAttack(card, lanes.random(random)) }
                        val attack = if (CombatFlow.attackRefusal(call, chosen) == null) chosen else choices.requiredAttacks
                        rt.run(player) { CombatFlow.declareAttackers(it, attack) }
                        if (attack.isNotEmpty()) attacks++
                    } else {
                        val chosen = choices.blocks.entries.filter { random.nextBoolean() }.map { (card, attackers) -> LaneBlock(card, attackers.random(random)) }
                        val block = if (CombatFlow.blockRefusal(call, chosen) == null) chosen else choices.requiredBlocks
                        // I-D29: the preview exists for every legal declaration
                        if (CombatFlow.preview(rt.call(), block) == null && rt.engine().stack.isEmpty()) fail("seed $seed: no preview for $block")
                        rt.run(player) { CombatFlow.declareBlockers(it, block) }
                    }
                }
                else -> {
                    val holder = s.priority.holder!!
                    val doing = Engine.options(s, holder).filter { it is Input.Cast || it is Input.PlayLand ||
                        (it is Input.Activate && !manaAbility(s, it)) }
                    if (doing.isNotEmpty() && random.nextInt(3) > 0) rt.perform(doing.random(random), holder.value)
                    else rt.run(holder.value) {
                        StepFlow.pass(it, holder.value, if (random.nextInt(4) == 0) StepFlow.Until.EndOfTurn else StepFlow.Until.Pass)
                    }
                }
            }
            done++
        }
        return Result(rt.engine().turn.number, done, attacks, divisions, stop)
    }

    /** Lands' abilities (mana) alone change nothing worth a call here: the mana empties. */
    private fun manaAbility(s: arcanum.engine.state.GameState, input: Input.Activate): Boolean =
        s.obj(input.source)?.definition?.faces?.firstOrNull()?.types?.contains(arcanum.api.CardType.Land) == true

    @Test fun gamesOverManyTurns() {
        for (seed in listOf(31L, 32L, 33L)) {
            val game = play(seed, calls = 220)
            println("seed $seed: ${game.calls} calls, turn ${game.turns}, attacks ${game.attacks}, divisions ${game.divisions}, ${game.stop}")
            assertTrue(game.turns >= 8 || game.stop.startsWith("result"), "seed $seed reached turn ${game.turns} (${game.stop})")
            assertTrue(game.attacks > 0, "seed $seed: some combat")
        }
    }
}
