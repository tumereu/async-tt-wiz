package arcanum.parser.sets.dmr

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Timing
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Dominaria Remastered chunk 01: cycling (CR 702.29): plain, typecycling and "Cycling—Pay 2 life", cycle triggers (CR 702.29c), madness
 * (CR 702.35), cycling lands that enter tapped, and the odd cards among them.
 */
class DmrChunk01CyclingTest {
    // CR 702.29c, 608.2d: a "may" cycle trigger
    @Test
    fun mendingVow() = card(
        name = "Mending Vow",
        cost = "{2}{W}",
        type = "Instant",
        text = """
            You gain 6 life.
            Cycling {1}{W} ({1}{W}, Discard this card: Draw a card.)
            When you cycle this card, you may gain 2 life.
        """,
    ) parsesTo abilities(
        spell(gainLife(6)),
        cycling("{1}{W}"),
        triggered(youCycleThis, Step.May(listOf(gainLife(2)), Players.You)),
    )

    // CR 702.14a, 702.29a: cycling with a cost that isn't mana
    @Test
    fun marshWraith() = card(
        name = "Marsh Wraith",
        cost = "{3}{B}{B}",
        type = "Creature — Wraith",
        text = """
            Swampwalk (This creature can't be blocked as long as defending player controls a Swamp.)
            Cycling—Pay 2 life. (Pay 2 life, Discard this card: Draw a card.)
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Landwalk(sub("Swamp"), "Swampwalk"),
        Keyword.Cycling(cost(CostPart.PayLife(n(2)))),
    )

    // CR 602.5b: "during your upkeep" is your turn's upkeep step
    @Test
    fun graveBarbarian() = card(
        name = "Grave Barbarian",
        cost = "{1}{B}{B}",
        type = "Creature — Zombie Barbarian",
        text = """
            {1}{B}, Discard a card: Return this card from your graveyard to your hand. Activate only during your upkeep.
            Cycling {1}{B} ({1}{B}, Discard this card: Draw a card.)
        """,
        pt = "3/1",
    ) parsesTo abilities(
        activated(cost(mana("{1}{B}"), discardACard), move(Objects.Self, Zone.Hand), functionsIn = listOf(Zone.Graveyard),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You,
                condition = Condition.And(listOf(Condition.YourTurn, Condition.StepIs(TurnStep.Upkeep))))),
        cycling("{1}{B}"),
    )

    // CR 205.1a (a new card type replaces the old), 508.1c, 506.2 (the defending player of a planeswalker is its controller)
    @Test
    fun maskedSerpent() = card(
        name = "Masked Serpent",
        cost = "{2}{U}",
        type = "Enchantment",
        text = """
            When an opponent casts a spell, if this permanent is an enchantment, it becomes a 4/4 Serpent creature with "This creature can't attack unless defending player controls an Island."
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Opponent)),
            Step.Do(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Serpent"))), replaceTypes = true, replaceSubtypes = true),
                Mechanic.SetPowerToughness(n(4), n(4)),
                has(static(Mechanic.CantAttack(Recipients(
                    objects = Objects.Each(and(ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.ControlledBy(withoutIsland))),
                    players = Players.Each(withoutIsland)))))),
                Affected.Objects(Objects.Self), Duration.Indefinitely)),
            interveningIf = Condition.ObjectMatches(Objects.Self, enchantment)),
        cycling("{2}"),
    )

    // CR 119.1, 102.1: a unique greatest life total, for any number of players
    @Test
    fun feralHounds() = card(
        name = "Feral Hounds",
        cost = "{G}",
        type = "Creature — Dog",
        text = """
            At the beginning of your upkeep, if a player has more life than each other player, the player with the most life gains control of this creature.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
        pt = "2/1",
    ) parsesTo abilities(
        triggered(yourUpkeep,
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(playersWithMostLife)), Affected.Objects(Objects.Self), Duration.Indefinitely)),
            interveningIf = Condition.Compare(Value.CountPlayers(playersWithMostLife), Comparison.Equal, n(1))),
        cycling("{2}"),
    )

    @Test
    fun mistWisps() = card(
        name = "Mist Wisps",
        cost = "{1}{U}",
        type = "Creature — Faerie",
        text = """
            Flying
            When this creature enters, untap up to two lands.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf,
            Step.Choose(Choice.Objects(land, n(0), n(2)), bind = "untapped", chooser = Players.You),
            untapIt(Objects.Bound("untapped"))),
        cycling("{2}"),
    )

    // CR 614.1c: enters tapped; cycling works from the hand
    @Test
    fun sunlitMeadow() = card(
        name = "Sunlit Meadow",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {W}.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(entersTapped, addMana("{W}"), cycling("{2}"))

    @Test
    fun murkyMire() = card(
        name = "Murky Mire",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {B}.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(entersTapped, addMana("{B}"), cycling("{2}"))

    @Test
    fun distantIsle() = card(
        name = "Distant Isle",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {U}.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(entersTapped, addMana("{U}"), cycling("{2}"))

    @Test
    fun slipperyMoss() = card(
        name = "Slippery Moss",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {G}.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(entersTapped, addMana("{G}"), cycling("{2}"))

    @Test
    fun smolderingBasin() = card(
        name = "Smoldering Basin",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped.
            {T}: Add {R}.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(entersTapped, addMana("{R}"), cycling("{2}"))

    // CR 702.29e: typecycling
    @Test
    fun elvishMonstrosity() = card(
        name = "Elvish Monstrosity",
        cost = "{5}{G}",
        type = "Creature — Elf Mutant",
        text = """
            {T}: Add {G}{G}{G}.
            Forestcycling {2} ({2}, Discard this card: Search your library for a Forest card, reveal it, put it into your hand, then shuffle.)
        """,
        pt = "4/5",
    ) parsesTo abilities(
        addMana("{G}{G}{G}"),
        Keyword.Typecycling(sub("Forest"), "Forestcycling", manaCost("{2}")),
    )

    // CR 702.29c, 608.2d: "you may have it deal X damage", X counted on the battlefield only
    @Test
    fun cinderPalmGoblin() = card(
        name = "Cinder Palm Goblin",
        cost = "{2}{R}",
        type = "Creature — Goblin",
        text = """
            Cycling {1}{R} ({1}{R}, Discard this card: Draw a card.)
            When you cycle this card, you may have it deal X damage to target creature, where X is the number of Goblins on the battlefield.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        cycling("{1}{R}"),
        triggered(youCycleThis,
            Step.May(listOf(damage(count(sub("Goblin")), toObjects(target("creature")))), Players.You),
            targets = listOf(slot("creature", creature))),
    )

    // CR 702.35a: madness
    @Test
    fun bitterSlick() = card(
        name = "Bitter Slick",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Target creature gets -3/-3 until end of turn.
            Cycling {2} ({2}, Discard this card: Draw a card.)
            Madness {3}{B} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), -3, -3), targets = listOf(slot("creature", creature))),
        cycling("{2}"),
        Keyword.Madness(manaCost("{3}{B}")),
    )

    // CR 303.4: an Aura
    @Test
    fun platedGuard() = card(
        name = "Plated Guard",
        cost = "{3}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +2/+5.
            Cycling {3} ({3}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        enchant(creature),
        static(pt(2, 5), affects = enchanted),
        cycling("{3}"),
    )

    // CR 615.1, 510.2: prevent all combat damage this turn, to anything
    @Test
    fun hush() = card(
        name = "Muffled Calm",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Prevent all combat damage that would be dealt this turn.
            Cycling {2} ({2}, Discard this card: Draw a card.)
        """,
    ) parsesTo abilities(
        spell(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.Prevention(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(), combat = true))), null, Duration.EndOfTurn))),
        cycling("{2}"),
    )

    // CR 702.29c: the damage comes from the cycled card
    @Test
    fun primalBlazeBoost() = card(
        name = "Primal Swell",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            Target creature gets +4/+4 until end of turn.
            Cycling {2}{G} ({2}{G}, Discard this card: Draw a card.)
            When you cycle this card, you may have target creature get +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), 4, 4), targets = listOf(slot("creature", creature))),
        cycling("{2}{G}"),
        triggered(youCycleThis, Step.May(listOf(pump(target("creature"), 1, 1)), Players.You), targets = listOf(slot("creature", creature))),
    )

    // CR 120.3: damage to each creature, from the cycled card
    @Test
    fun cleaveAndCarve() = card(
        name = "Cleave and Carve",
        cost = "{4}{R}{R}",
        type = "Sorcery",
        text = """
            Cleave and Carve deals 4 damage to each creature.
            Cycling {2}{R} ({2}{R}, Discard this card: Draw a card.)
            When you cycle this card, you may have it deal 1 damage to each creature.
        """,
    ) parsesTo abilities(
        spell(damage(4, toObjects(Objects.Each(creature)))),
        cycling("{2}{R}"),
        triggered(youCycleThis, Step.May(listOf(damage(1, toObjects(Objects.Each(creature)))), Players.You)),
    )

    @Test
    fun scorchingBeam() = card(
        name = "Scorching Beam",
        cost = "{3}{R}",
        type = "Instant",
        text = """
            Scorching Beam deals 3 damage to any target.
            Cycling {1}{R}{R} ({1}{R}{R}, Discard this card: Draw a card.)
            When you cycle this card, you may have it deal 1 damage to any target.
        """,
    ) parsesTo abilities(
        spell(damage(3, toAny), targets = listOf(anyTarget)),
        cycling("{1}{R}{R}"),
        triggered(youCycleThis, Step.May(listOf(damage(1, toAny)), Players.You), targets = listOf(anyTarget)),
    )
}

private val withoutIsland = PlayerFilter.Where(Condition.Not(Condition.Exists(and(sub("Island"), ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested))), atLeast = 1)))
