package arcanum.parser.sets.hob

import arcanum.api.Ability
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Hobbit chunk 08: Wolves and ferocious (an ability word, CR 207.2c): "while you control a creature with power 4 or
 * greater" checked as the ability would trigger (CR 603.2), or an intervening "if" (CR 603.4); can't attack unless you control
 * other Wolves (CR 508.1c); can't be blocked by tokens; a dying creature exiled instead with a linked trigger (CR 614.1a,
 * 603.11); a spell cheaper after a creature died this turn (CR 601.2f).
 */
class HobChunk08WolvesTest {
    // CR 207.2c, 603.2: checked only as it would trigger
    @Test
    fun moonhowlStalker() = card(
        name = "Moonhowl Stalker",
        cost = "{B}",
        type = "Creature — Wolf",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Ferocious — Whenever this creature attacks while you control a creature with power 4 or greater, this creature gets +2/+2 until end of turn.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(attacksSelf, pump(Objects.Self, 2, 2), abilityWord = "Ferocious", triggersOnlyIf = ferocious),
    )

    // CR 207.2c
    @Test
    fun ravenousWargHound() = card(
        name = "Ravenous Warg-Hound",
        cost = "{1}{B}",
        type = "Creature — Wolf",
        text = """
            Deathtouch
            Ferocious — Whenever this creature attacks while you control a creature with power 4 or greater, you gain 2 life.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(attacksSelf, gainLife(2), abilityWord = "Ferocious", triggersOnlyIf = ferocious),
    )

    // CR 603.4: an intervening "if"
    @Test
    fun viciousLittleHare() = card(
        name = "Vicious Little Hare",
        cost = "{G}",
        type = "Creature — Rabbit",
        text = "Ferocious — At the beginning of combat on your turn, if you control a creature with power 4 or greater, put a +1/+1 counter on this creature.",
        pt = "1/2",
    ) parsesTo abilities(
        triggered(beginningOfCombatOnYourTurn, counters(Objects.Self), interveningIf = ferocious, abilityWord = "Ferocious"),
    )

    // CR 611.2c: the creatures gaining trample are fixed as it resolves
    @Test
    fun wargWhelp() = card(
        name = "Warg Whelp",
        cost = "{1}{G}",
        type = "Creature — Wolf",
        text = "Ferocious — Whenever this creature attacks while you control a creature with power 4 or greater, until end of turn, this creature gets +1/+0 and creatures you control gain trample.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(attacksSelf, pump(Objects.Self, 1, 0), gains(Objects.Each(creatureYouControl), Keyword.Trample), abilityWord = "Ferocious", triggersOnlyIf = ferocious),
    )

    @Test
    fun wildmarkScavenger() = card(
        name = "Wildmark Scavenger",
        cost = "{4}{G}",
        type = "Creature — Wolf",
        text = "Ferocious — Whenever this creature attacks while you control a creature with power 4 or greater, put a +1/+1 counter on each creature you control.",
        pt = "3/6",
    ) parsesTo abilities(
        triggered(attacksSelf, counters(Objects.Each(creatureYouControl)), abilityWord = "Ferocious", triggersOnlyIf = ferocious),
    )

    // CR 508.3d
    @Test
    fun theAlphaWarg() = card(
        name = "The Alpha Warg",
        cost = "{2}{B}{G}",
        type = "Legendary Creature — Wolf",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Ferocious — Whenever you attack while you control a creature with power 4 or greater, you draw a card and lose 1 life.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(youAttack, draw(1), loseLife(1), abilityWord = "Ferocious", triggersOnlyIf = ferocious),
    )

    // CR 508.1c: an attack restriction
    @Test
    fun alphaWargsPack() = card(
        name = "Alpha Warg's Pack",
        cost = "{1}{B}{G}",
        type = "Creature — Wolf",
        text = """
            Trample
            This creature can't attack unless you control two or more other Wolves.
            At the beginning of your upkeep, create a 2/2 green Wolf creature token.
        """,
        pt = "5/3",
    ) parsesTo abilities(
        Keyword.Trample,
        static(Mechanic.CantAttack(), condition = Condition.Not(Condition.Exists(and(another, sub("Wolf"), you), atLeast = 2))),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), create(wolf)),
    )

    // CR 700.2
    @Test
    fun gemStuddedWarg() = card(
        name = "Gem-Studded Warg",
        cost = "{1}{G}",
        type = "Creature — Wolf",
        text = """
            Trample
            Whenever this creature deals combat damage to a player, choose one —
            • Put a +1/+1 counter on target Wolf you control.
            • Create a Treasure token. (It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Triggered(combatDamageToAPlayer, Instructions(modes = modes(
            mode(counters(target("Wolf")), targets = listOf(slot("Wolf", and(sub("Wolf"), you)))),
            mode(createTreasure())))),
    )

    // CR 509.1b
    @Test
    fun gloamwatchStalker() = card(
        name = "Gloamwatch Stalker",
        cost = "{2}{B/G}",
        type = "Creature — Wolf",
        text = """
            This creature can't be blocked by tokens.
            When this creature enters, put a +1/+1 counter on target creature.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        static(Mechanic.CantBeBlockedBy(token)),
        triggered(entersSelf, counters(target("creature")), targets = listOf(slot("creature", creature))),
    )

    // CR 602.5b
    @Test
    fun wastelandProwler() = card(
        name = "Wasteland Prowler",
        cost = "{1}{B}",
        type = "Creature — Wolf",
        text = "Pay 2 life: This creature gets +2/+2 until end of turn. Activate only once each turn.",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(CostPart.PayLife(n(2))), pump(Objects.Self, 2, 2), rules = anyTime.copy(perTurn = 1)),
    )

    // CR 614.1a: "would die"; 603.11: "When you do" is linked to the replacement
    @Test
    fun leaderOfTheChase() = card(
        name = "Leader of the Chase",
        cost = "{2}{B}{B}",
        type = "Creature — Wolf",
        text = """
            Flash
            If a creature an opponent controls would die, exile it instead. When you do, create a 2/2 green Wolf creature token.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Flash,
        static(Mechanic.Replacement(EventPattern.ZoneChange(creatureOpponentControls, from = Zone.Battlefield, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))), whenApplied = Instructions(steps = listOf(create(wolf))))),
    )

    @Test
    fun backDoorGuards() = card(
        name = "Back Door Guards",
        cost = "{1}{B}",
        type = "Creature — Goblin Soldier",
        text = "When this creature dies, target creature an opponent controls gets -1/-1 until end of turn.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(diesSelf, pump(target("creature"), -1, -1), targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 601.2f
    @Test
    fun fearsomeBatSwarm() = card(
        name = "Fearsome Bat-Swarm",
        cost = "{4}{B}",
        type = "Creature — Bat",
        text = """
            This spell costs {3} less to cast if a creature died this turn.
            Flying, deathtouch
        """,
        pt = "4/2",
    ) parsesTo abilities(
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{3}")!!, n(1))),
            condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events)),
        Keyword.Flying,
        Keyword.Deathtouch,
    )
}
