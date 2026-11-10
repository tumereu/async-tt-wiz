package arcanum.parser.sets.fdn

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Chunk 16 of FDN: damage wordings. The invented names carry the oracle text of the real cards. */
class FdnChunk16DamageTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val blocking = ObjectFilter.HasStatus(Status.Blocking)
    private val attackingOrBlocking = and(or(attacking, blocking), creature)
    private fun playerOrPlaneswalker(name: String) = TargetSlot(name, TargetKind.Either(planeswalker, PlayerFilter.Any),
        min = Value.Fixed(1), max = Value.Fixed(1))
    private val eachOpponentUpkeep = stepBegins(TurnStep.Upkeep, PlayerFilter.You)
    private val ifDiesExile = doing(Effect.ApplyMechanics(listOf(Mechanic.Replacement(
        EventPattern.ZoneChange(ObjectFilter.Is(target("creature")), from = Zone.Battlefield, to = Zone.Graveyard),
        modify = listOf(Modification.ChangeDestination(arcanum.api.Destination(Zone.Exile))))), null, Duration.EndOfTurn))

    // CR 700.2 (modes), 120.3 (damage), 701.8 (destroy)
    @Test
    fun scorchAndSmash() = card(
        name = "Scorch and Smash",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Choose one —
            • Scorch and Smash deals 3 damage to target creature.
            • Destroy target artifact.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(3, toObjects(target("creature"))), targets = listOf(slot("creature", creature))),
            mode(destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
        ))),
    )

    // CR 120.3 (a creature deals damage equal to its power), 115.3 (two different targets)
    @Test
    fun gnashDown() = card(
        name = "Gnash Down",
        cost = "{1}{G}",
        type = "Instant",
        text = "Target creature you control deals damage equal to its power to target creature or planeswalker you don't control.",
    ) parsesTo abilities(
        spell(
            damage(power(target("creature")), toObjects(target("permanent")), target("creature")),
            targets = listOf(slot("creature", creatureYouControl),
                slot("permanent", and(or(creature, planeswalker), ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
        ),
    )

    // CR 120.3, 102.2 (each opponent)
    @Test
    fun boltsurge() = card(
        name = "Boltsurge",
        cost = "{R}",
        type = "Sorcery",
        text = "Boltsurge deals 3 damage to each opponent.",
    ) parsesTo abilities(spell(damage(3, eachOpponent)))

    // CR 700.2, 120.3, 613.4c, 702.12a (indestructible), 702.4a (double strike)
    @Test
    fun scarletCharm() = card(
        name = "Scarlet Charm",
        cost = "{R}{W}",
        type = "Instant",
        text = """
            Choose one —
            • Scarlet Charm deals 4 damage to target player or planeswalker.
            • Permanents you control gain indestructible until end of turn.
            • Target creature gains double strike until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(4, Recipients(target = "player or planeswalker")), targets = listOf(playerOrPlaneswalker("player or planeswalker"))),
            mode(gains(Objects.Each(and(permanentFilter, you)), Keyword.Indestructible)),
            mode(gains(target("creature"), Keyword.DoubleStrike), targets = listOf(slot("creature", creature))),
        ))),
    )

    // CR 120.3, 119.1 (gain life); "tapped" is a status (110.5)
    @Test
    fun gravePenalty() = card(
        name = "Grave Penalty",
        cost = "{1}{W}",
        type = "Instant",
        text = "Grave Penalty deals 3 damage to target tapped creature and you gain 2 life.",
    ) parsesTo abilities(
        spell(damage(3, toObjects(target("creature"))), gainLife(2), targets = listOf(slot("creature", and(tapped, creature)))),
    )

    // CR 700.2d (choose one that hasn't been chosen), 603.2, 120.3, 119.1, 701.9 (discard), 121.1, 104.3a (lose the game)
    @Test
    fun infernalPact() = card(
        name = "Infernal Pact",
        cost = "{2}{B}{B}",
        type = "Enchantment",
        text = """
            At the beginning of your upkeep, choose one that hasn't been chosen —
            • This enchantment deals 4 damage to any target and you gain 4 life.
            • Target opponent discards two cards.
            • Draw two cards.
            • You lose the game.
        """,
    ) parsesTo abilities(
        Ability.Triggered(eachOpponentUpkeep, Instructions(modes = modes(
            mode(damage(4, toAny), gainLife(4), targets = listOf(anyTarget)),
            mode(discard(2, Players.Target("opponent")), targets = listOf(targetOpponent)),
            mode(draw(2)),
            mode(doing(Effect.LoseGame(Players.You))),
            onlyUnchosen = true,
        ))),
    )

    // CR 120.3, 614.6 (dies instead exiled), 506.4 (attacking or blocking)
    @Test
    fun selenesSmite() = card(
        name = "Selene's Smite",
        cost = "{W}",
        type = "Instant",
        text = "Selene's Smite deals 3 damage to target attacking or blocking creature. If that creature would die this turn, exile it instead.",
    ) parsesTo abilities(
        spell(damage(3, toObjects(target("creature"))), ifDiesExile, targets = listOf(slot("creature", attackingOrBlocking))),
    )

    // CR 602.2 (activated), 118.12 (costs), 120.3, 702.10a (haste)
    @Test
    fun frantic() = card(
        name = "Frantic Firebrand",
        cost = "{R}",
        type = "Creature — Goblin Pirate",
        pt = "1/1",
        text = """
            Haste (This creature can attack and {T} as soon as it comes under your control.)
            {T}, Sacrifice this creature: It deals 1 damage to any target.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        activated(cost(tap, sacrificeSelf), damage(1, toAny), targets = listOf(anyTarget)),
    )

    // CR 603.2, 601.2 (cast), 120.3, 702.9a (flying)
    @Test
    fun cinderWhelp() = card(
        name = "Cinder Whelp",
        cost = "{2}{R}",
        type = "Creature — Dragon",
        pt = "2/2",
        text = """
            Flying
            Whenever you cast a noncreature or Dragon spell, this creature deals 1 damage to each opponent.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(or(noncreatureSpell, sub("Dragon"))), damage(1, eachOpponent)),
    )

    // CR 603.6a (enters), 120.3
    @Test
    fun quakeTremors() = card(
        name = "Quake Tremors",
        cost = "{1}{R}",
        type = "Enchantment",
        text = "Whenever a creature you control enters, this enchantment deals 1 damage to each opponent.",
    ) parsesTo abilities(triggered(enters(creatureYouControl), damage(1, eachOpponent)))

    // CR 120.3, 701.9 (discard), 608.2c ("if you do")
    @Test
    fun blazingBlast() = card(
        name = "Blazing Blast",
        cost = "{4}{R}",
        type = "Sorcery",
        text = """
            Blazing Blast deals 6 damage to target creature.
            You may discard a card. If you do, draw a card.
        """,
    ) parsesTo abilities(
        spell(damage(6, toObjects(target("creature"))), Step.Pay(cost = cost(discardACard), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(draw(1))), targets = listOf(slot("creature", creature))),
    )

    // CR 120.3, 119.1, 506.4
    @Test
    fun thrustThrough() = card(
        name = "Thrust Through",
        cost = "{W}",
        type = "Instant",
        text = "Thrust Through deals 3 damage to target attacking or blocking creature. You gain 1 life.",
    ) parsesTo abilities(
        spell(damage(3, toObjects(target("creature"))), gainLife(1), targets = listOf(slot("creature", attackingOrBlocking))),
    )

    // CR 603.2, 601.2, 105.1 (colors), 120.3, 702.7a (first strike)
    @Test
    fun spiteSparker() = card(
        name = "Spite Sparker",
        cost = "{1}{R}{R}",
        type = "Creature — Elemental",
        pt = "3/2",
        text = """
            First strike (This creature deals combat damage before creatures without first strike.)
            Whenever an opponent casts a white or blue instant or sorcery spell, this creature deals 2 damage to that player.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(
            on(EventPattern.Cast(and(or(ObjectFilter.OfColor(Color.White), ObjectFilter.OfColor(Color.Blue)), or(instant, sorcery)), PlayerFilter.Opponent)),
            damage(2, Recipients(players = Players.EventPlayer)),
        ),
    )

    // CR 503.1 (upkeep), 120.3
    @Test
    fun painfulGiant() = card(
        name = "Painful Giant",
        cost = "{2}{R}{R}",
        type = "Creature — Giant",
        pt = "5/5",
        text = "At the beginning of your upkeep, this creature deals 1 damage to you.",
    ) parsesTo abilities(triggered(eachOpponentUpkeep, damage(1, Recipients(players = Players.You))))

    // CR 603.6a, 120.3
    @Test
    fun boneBowman() = card(
        name = "Bone Bowman",
        cost = "{3}{B}",
        type = "Creature — Skeleton Archer",
        pt = "3/3",
        text = "When this creature enters, it deals 1 damage to any target.",
    ) parsesTo abilities(triggered(entersSelf, damage(1, toAny), targets = listOf(anyTarget)))

    // CR 700.2, 120.3
    @Test
    fun slagfall() = card(
        name = "Slagfall",
        cost = "{1}{R}{R}",
        type = "Sorcery",
        text = """
            Choose one —
            • Slagfall deals 3 damage to each creature.
            • Slagfall deals 3 damage to each player.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = modes(
            mode(damage(3, toObjects(Objects.Each(creature)))),
            mode(damage(3, Recipients(players = Players.Each(PlayerFilter.Any)))),
        ))),
    )

    // CR 603.6a, 120.3
    @Test
    fun lizardPyromancer() = card(
        name = "Lizard Pyromancer",
        cost = "{1}{R}",
        type = "Creature — Lizard Wizard",
        pt = "2/1",
        text = "When this creature enters, it deals 2 damage to target player or planeswalker.",
    ) parsesTo abilities(
        triggered(entersSelf, damage(2, Recipients(target = "player or planeswalker")), targets = listOf(playerOrPlaneswalker("player or planeswalker"))),
    )

    // CR 120.3, 603.6a, 700.1 (a value counted on resolution)
    @Test
    fun goblinVolleyer() = card(
        name = "Goblin Volleyer",
        cost = "{3}{R}",
        type = "Creature — Goblin Warrior",
        pt = "4/2",
        text = "When this creature enters, it deals damage to target creature an opponent controls equal to the number of Goblins you control.",
    ) parsesTo abilities(
        triggered(entersSelf, damage(count(and(sub("Goblin"), you)), toObjects(target("creature"))),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )
}
