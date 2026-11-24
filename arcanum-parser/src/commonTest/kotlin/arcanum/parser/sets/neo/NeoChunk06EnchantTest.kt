package arcanum.parser.sets.neo

import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.PlayerFilter
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.CardType
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

class NeoChunk06EnchantTest {
    private val isCreature = Condition.ObjectMatches(enchanted, creature)

    /** "Enchanted land is a P/T <color> Spirit creature with <keywords>. It's still a land." (layers 4, 5, 6, 7b; still a land: its other types
     * stay, CR 205.1b) and the destruction replacement shared by the Emergence cycle. */
    private fun emergence(name: String, cost: String, pt: Pair<Int, Int>, color: Color, vararg keywords: Ability) = card(
        name = name,
        cost = cost,
        type = "Enchantment — Aura",
        text = """
            Enchant land you control
            Enchanted land is a ${pt.first}/${pt.second} ${if (color == Color.Red) "red" else "green"} Spirit creature with ${if (keywords.size == 1) "haste" else "vigilance and haste"}. It's still a land.
            If enchanted land would be destroyed, instead sacrifice this Aura and that land gains indestructible until end of turn.
        """,
    ) parsesTo abilities(
        enchant(and(land, you)),
        static(Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Spirit")))),
            Mechanic.SetColors(listOf(ColorRef.Literal(color))), Mechanic.SetPowerToughness(n(pt.first), n(pt.second)), has(*keywords), affects = enchanted),
        // CR 614.1a, 614.6: the land isn't destroyed; the Aura is sacrificed and the land gains indestructible instead
        static(Mechanic.Replacement(EventPattern.Destroyed(ObjectFilter.Is(enchanted)), instead = Instructions(steps = listOf(
            Step.Do(Effect.Sacrifice(Objects.Self, by = Players.You)),
            untilEndOfTurn(Objects.EventObject(EventRole.Before), has(Keyword.Indestructible)))))),
    )

    // CR 205.1b, 613.1d-f, 613.4b; CR 614.1a
    @Test
    fun sparkveinAwakening() = emergence("Sparkvein Awakening", "{1}{R}", 3 to 3, Color.Red, Keyword.Haste)

    // CR 205.1b, 613.1d-f, 613.4b; CR 614.1a
    @Test
    fun mossheartAwakening() = emergence("Mossheart Awakening", "{3}{G}", 4 to 5, Color.Green, Keyword.Vigilance, Keyword.Haste)

    // CR 603.4: the intervening if is checked as it triggers and as it resolves; CR 208.1: tied for the greatest counts
    @Test
    fun chroniclersInsight() = card(
        name = "Chronicler's Insight",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature
            When this Aura enters, if enchanted permanent is a creature with the greatest power among creatures on the battlefield, draw a card.
            As long as enchanted permanent is a creature, it gets +2/+1.
        """,
    ) parsesTo abilities(
        enchant(or(artifact, creature)),
        triggered(entersSelf, draw(1), interveningIf = Condition.ObjectMatches(enchanted,
            and(creature, ObjectFilter.Power(Comparison.AtLeast, Value.Of(NumericProperty.Power, Objects.Each(creature), Combine.Greatest))))),
        static(pt(2, 1), affects = enchanted, condition = isCreature),
    )

    // CR 508.1c, 509.1c (can't attack or block), 702.122a (can't be tapped for crew), 602.5a, 605.1a (mana abilities still can be activated)
    @Test
    fun mediatorsDetention() = card(
        name = "Mediator's Detention",
        cost = "{2}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant permanent
            Enchanted permanent can't attack, block, or crew Vehicles. Its activated abilities can't be activated unless they're mana abilities.
        """,
    ) parsesTo abilities(
        enchant(permanentFilter),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), Mechanic.CantTapFor(Keyword.Crew.Kind),
            Mechanic.CantBeActivated(manaAbilities = false), affects = enchanted),
    )

    // CR 701.3d (unattach), 608.2h (whether it's an Equipment as the ability resolves), 613.1f (loses all abilities), 502.3
    @Test
    fun scholarsPhyresis() = card(
        name = "Scholar's Phyresis",
        cost = "{3}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant artifact, creature, or planeswalker
            When this Aura enters, tap enchanted permanent. If it's an Equipment, unattach it.
            Enchanted permanent loses all abilities and doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchant(or(artifact, creature, ObjectFilter.Type(CardType.Planeswalker))),
        triggered(entersSelf, tapIt(enchanted), ifThen(Condition.ObjectMatches(enchanted, equipment), doing(Effect.Unattach(enchanted)))),
        static(Mechanic.LoseAbilities(), Mechanic.DoesntUntap, affects = enchanted),
    )

    // CR 613.4c: +2/+1 only while the enchanted permanent is a creature
    @Test
    fun goblinBattleDaub() = card(
        name = "Goblin Battle Daub",
        cost = "{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature
            As long as enchanted permanent is a creature, it gets +2/+1.
        """,
    ) parsesTo abilities(
        enchant(or(artifact, creature)),
        static(pt(2, 1), affects = enchanted, condition = isCreature),
    )

    // CR 107.3m: X of an enters trigger is the X paid as the Aura was cast; CR 613.4b (base 1/1), 613.4c (counters)
    @Test
    fun rousedPerception() = card(
        name = "Roused Perception",
        cost = "{X}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature
            When this Aura enters, put X +1/+1 counters on enchanted permanent.
            As long as enchanted permanent is a creature, it has base power and toughness 1/1.
        """,
    ) parsesTo abilities(
        enchant(or(artifact, creature)),
        triggered(entersSelf, counters(enchanted, Value.CastX(Objects.Self))),
        static(Mechanic.SetPowerToughness(n(1), n(1)), affects = enchanted, condition = isCreature),
    )

    // CR 613.4c, 509.1a; CR 113.10: "you" in the granted ability is the enchanted permanent's controller
    @Test
    fun rakingAnguish() = card(
        name = "Raking Anguish",
        cost = "{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature
            As long as enchanted permanent is a creature, it gets -1/-1 and can't block.
            Enchanted permanent has "At the beginning of your upkeep, you lose 1 life."
        """,
    ) parsesTo abilities(
        enchant(or(artifact, creature)),
        static(pt(-1, -1), Mechanic.CantBlock(ObjectFilter.Any), affects = enchanted, condition = isCreature),
        static(has(triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), loseLife(1))), affects = enchanted),
    )

    // CR 613.4c, 702.111b (menace); CR 603.6c, 603.10a (leaves-the-battlefield trigger) — Audit (incorrect): false alarm, the bonus applies to
    // the enchanted permanent (affects attached), not to the Aura
    @Test
    fun boonOfFury() = card(
        name = "Boon of Fury",
        cost = "{3}{R}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature
            As long as enchanted permanent is a creature, it gets +2/+2 and has menace. (It can't be blocked except by two or more creatures.)
            When this Aura leaves the battlefield, create a 2/2 red Spirit creature token with menace.
        """,
    ) parsesTo abilities(
        enchant(or(artifact, creature)),
        static(pt(2, 2), has(Keyword.Menace), affects = enchanted, condition = isCreature),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
            create(creatureToken("Spirit", listOf(Color.Red), 2, 2, listOf(Keyword.Menace)))),
    )

    // CR 603.3d (the trigger's target), 613.1f (granted mana ability), 106.1b
    @Test
    fun splicedVerdure() = card(
        name = "Spliced Verdure",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Enchant land
            When this Aura enters, put a +1/+1 counter on target creature or Vehicle you control.
            Enchanted land has "{T}: Add two mana of any one color."
        """,
    ) parsesTo abilities(
        enchant(land),
        triggered(entersSelf, counters(target("permanent")), targets = listOf(slot("permanent", and(or(creature, sub("Vehicle")), you)))),
        static(has(activated(cost(tap), Step.Do(Effect.AddMana(arcanum.api.ManaProduction(anyOneColor(2)), Players.You)))), affects = enchanted),
    )

    // CR 613.4c, 613.1f (loses flying while a creature)
    @Test
    fun sparklineFault() = card(
        name = "Sparkline Fault",
        cost = "{1}{U}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant artifact or creature
            As long as enchanted permanent is a creature, it gets -3/-0 and loses flying.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchant(or(artifact, creature)),
        static(pt(-3, 0), Mechanic.LoseAbilities(Keyword.Flying.kind), affects = enchanted, condition = isCreature),
    )

    // CR 303.4a (enchant artifact or creature you control), 603.3d, 701.8a; CR 613.4c
    @Test
    fun warpedClasp() = card(
        name = "Warped Clasp",
        cost = "{2}{B}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact or creature you control
            When this Aura enters, destroy target creature or planeswalker an opponent controls.
            As long as enchanted permanent is a creature, it gets +1/+1.
        """,
    ) parsesTo abilities(
        enchant(and(or(artifact, creature), you)),
        triggered(entersSelf, destroy(target("permanent")),
            targets = listOf(slot("permanent", and(or(creature, ObjectFilter.Type(CardType.Planeswalker)), opponents)))),
        static(pt(1, 1), affects = enchanted, condition = isCreature),
    )
}
