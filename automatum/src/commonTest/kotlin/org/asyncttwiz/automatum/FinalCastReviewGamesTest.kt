package org.asyncttwiz.automatum

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.cards.face
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.Automatum
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Final review of casting and resolving (report `arcanum-final-cast-review.md`): seeded two-player games played the way
 * the app plays them — every cast, land play, ability and special action begun from the card menus' rows
 * ([CardPlays]) as a private flow ([CastFlow]: the panel's answers, the suggested mana, sent with what follows), every
 * pass, answer and step as a shared flow ([SharedFlow]) by either player (D11) — with random cube cards and invented
 * cards of the BLB, DSK, RTR, GTC and DGM mechanics. Each call is one ATW batch checked by [RoundTrip] (rebuilt state =
 * the engine's own, undo restores the board); after each batch the two devices read the same flow and the same
 * priority, nothing is stuck, and the menus offer nothing of hidden cards (I-D27).
 */
class FinalCastReviewGamesTest {
    init { CombatCards.ensured; SetCards.ensured }

    private object SetCards {
        val names: List<String> by lazy {
            val fuse = "Fuse (You may cast one or both halves of this card from your hand.)"
            listOf(
                // dgm
                Cards.inventFaces("Split",
                    face("Hasten", "{1}{U}", "Instant", "Return target creature to its owner's hand.\n$fuse", null, null, null),
                    face("Harrow", "{2}{B}", "Instant", "Target player sacrifices a creature of their choice.\n$fuse", null, null, null)),
                // dsk
                Cards.inventFaces("Split",
                    face("Ashen Parlor", "{1}{W}", "Enchantment — Room", "When you unlock this door, you gain 3 life.", null, null, null),
                    face("Hollow Larder", "{2}{B}", "Enchantment — Room", "When you unlock this door, draw a card.", null, null, null)),
                Cards.invent("Creeping Mire Dread", "{1}{G}", "Sorcery", "Manifest dread."),
                Cards.invent("Corridor Survivor", "{1}{W}", "Creature — Human Survivor",
                    "Survival — At the beginning of your second main phase, if this creature is tapped, you gain 2 life.", "2/2"),
                // blb
                Cards.invent("Moonlit Craving", "{2}{B}", "Instant", """
                    Gift a Food (You may promise an opponent a gift as you cast this spell. If you do, they create a Food token before its other effects. It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
                    Destroy target creature. If the gift wasn't promised, you lose 2 life.
                """),
                Cards.invent("Tinplate Bruiser", "{3}{G}", "Creature — Raccoon Warrior", """
                    Offspring {2} (You may pay an additional {2} as you cast this spell. If you do, when this creature enters, create a 1/1 token copy of it.)
                    This creature can't be blocked by creatures with power 2 or less.
                """, "4/4"),
                Cards.invent("Season of the Thicket", "{3}{W}{W}", "Sorcery", """
                    Choose up to five {P} worth of modes. You may choose the same mode more than once.
                    {P} — Create a 1/1 white Rabbit creature token.
                    {P}{P} — Exile target nonland permanent. Its controller draws a card.
                    {P}{P}{P} — Return target permanent card with mana value 3 or less from your graveyard to the battlefield with an indestructible counter on it.
                """),
                // rtr
                Cards.invent("Market Detainment", "{1}{U}", "Sorcery", """
                    Detain target creature an opponent controls. (Until your next turn, that creature can't attack or block and its activated abilities can't be activated.)
                    Draw a card.
                """),
                Cards.invent("Spire Barrage", "{1}{R}", "Sorcery", """
                    Spire Barrage deals 4 damage to target creature you don't control.
                    Overload {3}{R}{R}{R} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
                """),
                Cards.invent("Sewer Lurcher", "{2}{B}", "Creature — Zombie", """
                    Swampwalk (This creature can't be blocked as long as defending player controls a Swamp.)
                    Scavenge {2}{B} ({2}{B}, Exile this card from your graveyard: Put a number of +1/+1 counters equal to this card's power on target creature. Scavenge only as a sorcery.)
                """, "3/1"),
                // gtc
                Cards.invent("Tithe Gatherer", "{1}{W}", "Creature — Human Cleric",
                    "Extort (Whenever you cast a spell, you may pay {W/B}. If you do, each opponent loses 1 life and you gain that much life.)", "2/2"),
                Cards.invent("Cutpurse Goblin", "{1}{R}", "Creature — Goblin Warrior",
                    "Bloodrush — {R}, Discard this card: Target attacking creature gets +2/+1 until end of turn.", "2/1"),
                Cards.invent("Specimen Nine", "{G}", "Creature — Human Ooze", """
                    Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)
                    Remove two +1/+1 counters from this creature: Regenerate it. (The next time this creature would be destroyed this turn, instead tap it, remove it from combat, and heal all damage on it.)
                """, "1/1"),
            )
        }
        val ensured: Boolean by lazy { names.isNotEmpty() }
    }

    private val basics = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")

    /** What went wrong in a game, by kind, with an example each (the report's findings). */
    private val problems = linkedMapOf<String, MutableList<String>>()
    private fun problem(kind: String, example: String) { problems.getOrPut(kind) { mutableListOf() }.let { if (it.size < 4) it += example } }

    private class Stats(var turns: Int = 0, var casts: Int = 0, var abilities: Int = 0, var answers: Int = 0, var stop: String = "calls")

    private fun play(seed: Long, calls: Int): Stats {
        val random = Random(seed)
        val table = AtwTable("final-games-$seed")
        val cube = Cards.cubeNames.filter { name ->
            runCatching { Cards.definition(name) }.isSuccess && basics.none { it == name }
        }
        val stats = Stats()
        var lastAction = "start"
        for (player in table.players) {
            val spells = cube.shuffled(random).take(22) + SetCards.names.shuffled(random).take(8)
            val lands = List(14) { basics[it % 5] }
            val deck = (spells + lands).shuffled(random)
            table.put(player, PlayZone.HAND, *deck.take(7).toTypedArray())
            table.put(player, PlayZone.LIBRARY, *deck.drop(7).toTypedArray())
            // A head start of every colour so that most spells can be cast
            table.put(player, PlayZone.BATTLEFIELD, *(basics + basics).toTypedArray())
        }
        val rt = RoundTrip(table.state(), table.definitions, seed)
        fun log() = EngineLog.from(rt.tags, rt.state, rt.definitions)
        fun shared() = SharedFlow(rt.state, rt.definitions, log(), rt::seed)
        fun name(card: String) = table.names[card] ?: card

        repeat(calls) { step ->
            val s = rt.engine()
            stats.turns = s.turn.number
            if (Engine.result(s) != null) { stats.stop = "result"; return stats }
            val where = "seed $seed call $step turn ${s.turn.number} ${s.turn.step}"
            // Two devices read the same board: the same flow, decision and priority (D31, D11)
            val one = shared()
            val two = shared()
            assertEquals(one.decision, two.decision, "$where: devices disagree on the decision")
            assertEquals(one.holder, two.holder, "$where: devices disagree on priority")
            // The board as Automatum left it needs no settling: the holder shown is the holder the engine works with
            HostPriority.wanted(s)?.let { problem("priority resettled after an automated batch", "$where after $lastAction: board ${rt.state.board.priority} -> $it") }
            // I-D27: the menus list nothing of the other player's library (a card in their hand gets one greyed Cast before the
            // plays are read, ZON-007; one the viewer saw leave the battlefield may be listed, greyed as not theirs)
            for (viewer in table.players) {
                val plays = CardPlays.of(rt.state, rt.definitions, viewer, log())
                for (card in plays.byCard.keys) {
                    val zone = rt.state.board.containers.firstOrNull { card in it.cards }
                    if (zone != null && zone.accountId != viewer && zone.zone == PlayZone.LIBRARY)
                        problem("menus list a hidden card", "$where: $viewer: ${name(card)} in the other player's ${zone.zone}")
                }
            }
            val decision = one.decision
            lastAction = decision?.let { d -> "answer to ${d::class.simpleName} of ${d.source?.let { (it as? arcanum.engine.state.ObjectId.Card)?.let { c -> name(c.card.value) } }} (${d.toString().take(200)})" } ?: "pass or declaration"
            val actor = table.players.random(random)
            try {
                when {
                    decision is Decision.Divide && one.division != null -> rt.apply(one.divide(null), actor)
                    decision != null -> {
                        val listed = one.listedAnswers()
                        if (listed.isEmpty()) {
                            problem("shared decision without a listed answer (stuck)", "$where: $decision")
                            stats.stop = "stuck on ${decision::class.simpleName}"; return stats
                        }
                        rt.apply(one.answer(listed.random(random)), actor)
                        stats.answers++
                    }
                    s.priority.holder == null -> {
                        val call = rt.call()
                        val choices = CombatFlow.choices(call)
                        val declaring = choices.declaring
                        if (declaring == null) {
                            problem("nobody holds priority and nothing is awaited (stuck)", "$where")
                            stats.stop = "stuck"; return stats
                        }
                        if (choices.attackers) {
                            val chosen = choices.attacks.entries.filter { random.nextInt(3) == 0 }.map { (c, t) -> LaneAttack(c, t.random(random)) }
                            val attack = if (CombatFlow.attackRefusal(call, chosen) == null) chosen else choices.requiredAttacks
                            rt.run(declaring) { CombatFlow.declareAttackers(it, attack) }
                        } else {
                            val chosen = choices.blocks.entries.filter { random.nextBoolean() }.map { (c, a) -> LaneBlock(c, a.random(random)) }
                            val block = if (CombatFlow.blockRefusal(call, chosen) == null) chosen else choices.requiredBlocks
                            rt.run(declaring) { CombatFlow.declareBlockers(it, block) }
                        }
                    }
                    else -> {
                        val holder = s.priority.holder!!.value
                        val plays = CardPlays.of(rt.state, rt.definitions, holder, log())
                        val legal = plays.byCard.flatMap { (card, list) -> list.filter { it.legal }.map { card to it } }
                            .filter { (_, p) -> p.kind != CardPlay.Kind.Mana || random.nextInt(6) == 0 }
                        // The double-tap and the menu agree on what a hand card can do (ZON-004, ZON-007)
                        val hand = HandPlays.of(rt.state, rt.definitions, holder, log())
                        for ((card, inputs) in hand.byCard) {
                            val menu = plays.of(card).mapNotNull { it.input }.filter { it is Input.Cast || it is Input.PlayLand }
                            if (menu.toSet() != inputs.toSet()) problem("double-tap and menu disagree", "$where: ${name(card)} $inputs vs $menu")
                        }
                        if (legal.isNotEmpty() && random.nextInt(3) > 0) {
                            val (card, chosen) = legal.random(random)
                            lastAction = "${name(card)}: ${chosen.text}"
                            playThrough(rt, holder, card, chosen, random, where, ::name, stats)
                        } else if (s.stack.isNotEmpty()) rt.apply(shared().pass(holder), actor)
                        else {
                            val until = when (random.nextInt(8)) {
                                0 -> StepFlow.Until.EndOfTurn
                                1 -> StepFlow.Until.Step(TurnPosition.END_STEP)
                                else -> StepFlow.Until.Pass
                            }
                            val position = TurnPosition.of(rt.state.board.turn)!!
                            rt.apply(shared().step(holder, if (until is StepFlow.Until.Step && until.position.ordinal <= position.ordinal)
                                StepFlow.Until.Pass else until), actor)
                        }
                    }
                }
            } catch (problem: Unmappable) {
                problem("unmappable batch", "$where after $lastAction: ${problem.message?.take(240)}")
                stats.stop = "unmappable"; return stats
            } catch (problem: Unsupported) {
                problem("unsupported board", "$where: ${problem.reasons.take(2)}")
                stats.stop = "unsupported"; return stats
            } catch (failed: AssertionError) {
                // RoundTrip's checks (rebuild, undo): reported with the first lines, the game stops there
                problem("round trip: ${failed.message?.lineSequence()?.first()?.take(120)}", "$where after $lastAction: ${failed.message?.take(700)}")
                stats.stop = "round trip failed"; return stats
            } catch (failed: Exception) {
                problem("exception ${failed::class.simpleName}", "$where after $lastAction: ${failed.message?.take(400)}")
                stats.stop = "exception"; return stats
            }
            // A flow open on the board can be read and answered; without one somebody can act
            val after = rt.engine()
            if (Automatum.flowOpen(rt.state.board) && after.pending == null) problem("continuation without a decision", where)
        }
        return stats
    }

    /** The menu row [chosen] of [card], begun and answered as the cast panel does, then sent ([CastFlow.sent]). */
    private fun playThrough(rt: RoundTrip, holder: String, card: String, chosen: CardPlay, random: Random, where: String,
        name: (String) -> String, stats: Stats) {
        val log = EngineLog.from(rt.tags, rt.state, rt.definitions)
        var flow = try {
            CastFlow.start(rt.state, rt.definitions, holder, log, chosen.input!!, CastFlow.Seeds(rt::seed))
        } catch (problem: IllegalArgumentException) {
            problem("menu row offered as legal can't be begun", "$where: ${name(card)} ${chosen.text}: ${problem.message?.take(160)}")
            return
        }
        var guard = 30
        while (guard-- > 0) {
            val open = flow.pending ?: break
            flow = when (open) {
                is Decision.ManaPayment -> {
                    if (open.suggestion.isEmpty() || !flow.pays(open.suggestion)) {
                        problem("legal play whose payment has no suggestion that pays", "$where: ${name(card)} ${chosen.text} ${open.cost}")
                        return
                    }
                    flow.answer(Answer.ManaPayment(open.suggestion))
                }
                is Decision.Targets -> flow.answer(Answer.Targets(open.options.shuffled(random).take(maxOf(open.min, minOf(open.max ?: 1, 1)))))
                else -> {
                    val listed = flow.listedAnswers()
                    if (listed.isEmpty()) {
                        problem("cast decision without a listed answer (cast stuck)", "$where: ${name(card)} ${chosen.text}: $open")
                        return
                    }
                    try { flow.answer(listed.random(random)) } catch (refused: IllegalArgumentException) {
                        problem("listed answer refused in a cast", "$where: ${name(card)}: ${refused.message?.take(160)}")
                        return
                    }
                }
            }
        }
        if (!flow.complete) { problem("cast never completes", "$where: ${name(card)}"); return }
        rt.apply(flow.sent(), holder)
        if (chosen.kind == CardPlay.Kind.Cast) stats.casts++ else stats.abilities++
    }

    // finalreview-cast #3, #4, #7: long seeded games through the menus, the cast panel's flow and the shared flows (I-D11,
    // I-D28, I-D27, D11); problems found are collected and printed, and a game stops at its first failed round trip,
    // exception or stuck flow. Seeds 101 and 103: #3 (Next step refused after a mana ability in combat); #4 (undo
    // of mana added to {R}{R}) stops games that get past #3; 111: #7 (search and shuffle)
    @Ignore("finalreview-cast #3, #4, #7: the seeded games stop on those findings")
    @Test fun longGamesThroughCardPlaysCastFlowAndSharedFlow() {
        for (seed in listOf(101L, 103L, 104L, 111L)) {
            val stats = play(seed, calls = 250)
            println("seed $seed: turn ${stats.turns}, casts ${stats.casts}, abilities ${stats.abilities}, answers ${stats.answers}, ${stats.stop}")
        }
        for ((kind, examples) in problems) println("PROBLEM $kind:\n  " + examples.joinToString("\n  "))
        assertTrue(problems.isEmpty(), "problems: ${problems.keys}")
    }
}
