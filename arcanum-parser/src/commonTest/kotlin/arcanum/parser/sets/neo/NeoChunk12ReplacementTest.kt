package arcanum.parser.sets.neo

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.fdn.addEither
import arcanum.parser.sets.fdn.entersTapped
import kotlin.test.Test

/** Chunk 12: replacements that make a permanent enter tapped, with a counter, or after a choice: gain-life lands, a counter if you control a modified
 * creature, an enchantment that enters with counters, a creature that passes its counters on, and two lands that choose a type or a color as they enter. */
class NeoChunk12ReplacementTest {
    private val entering = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)

    // CR 614.1c (enters tapped), 603.6a, 119.1, 605.1a
    @Test
    fun gainLands() {
        listOf("B" to "R", "G" to "W", "U" to "B", "B" to "G", "R" to "G", "W" to "B", "U" to "R", "G" to "U", "W" to "U", "R" to "W").forEachIndexed { i, (a, b) ->
            card(
                name = "Gainland Number $i",
                cost = null,
                type = "Land",
                text = """
                    This land enters tapped.
                    When this land enters, you gain 1 life.
                    {T}: Add {$a} or {$b}.
                """,
            ) parsesTo abilities(
                entersTapped,
                triggered(entersSelf, gainLife(1)),
                addEither(a, b),
            )
        }
    }

    // CR 614.1c, 700.9 (modified: Equipment, Auras you control and counters)
    @Test
    fun heirOfTheAncientFang() = card(
        name = "Heir of the Old Fang",
        cost = "{2}{G}",
        type = "Creature — Snake Samurai",
        pt = "2/3",
        text = "This creature enters with a +1/+1 counter on it if you control a modified creature. (Equipment, Auras you control, and counters are modifications.)",
    ) parsesTo abilities(
        static(Mechanic.Replacement(entering,
            modify = listOf(Modification.EntersWithCounters(p1p1, n(1)))),
            condition = Condition.Exists(and(creature, you, ObjectFilter.Modified), atLeast = 1)),
    )

    // CR 614.1c, 700.9, 702.10 (haste), 602.5d (sorcery speed), 602.5b (once each turn), 122.1
    @Test
    fun invigoratingHotSpring() = card(
        name = "Invigorating Cold Spring",
        cost = "{1}{R}{G}",
        type = "Enchantment",
        text = """
            This enchantment enters with four +1/+1 counters on it.
            Modified creatures you control have haste. (Equipment, Auras you control, and counters are modifications.)
            Remove a +1/+1 counter from this enchantment: Put a +1/+1 counter on target creature you control. Activate only as a sorcery and only once each turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(entering, modify = listOf(Modification.EntersWithCounters(p1p1, n(4))))),
        static(has(Keyword.Haste), affects = Objects.Each(and(creature, you, ObjectFilter.Modified))),
        activated(cost(CostPart.RemoveCounters(p1p1, n(1), CostObjects.Self)), counters(target("creature")),
            targets = listOf(slot("creature", creatureYouControl)),
            rules = ActivationRules(Timing.Sorcery, PlayerFilter.You, perTurn = 1)),
    )

    // CR 614.1c, 603.10a (leaves-the-battlefield trigger looks back), 122.1
    @Test
    fun ironApprentice() = card(
        name = "Steel Apprentice",
        cost = "{1}",
        type = "Artifact Creature — Construct",
        pt = "0/0",
        text = """
            This creature enters with a +1/+1 counter on it.
            When this creature dies, if it had counters on it, put those counters on target creature you control.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(entering, modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))),
        triggered(diesSelf, doing(Effect.PutCountersOf(target("creature"), of = Objects.EventObject(EventRole.Before))),
            targets = listOf(slot("creature", creatureYouControl)),
            interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Before), ObjectFilter.HasCounter(null, atLeast = 1))),
    )

    // CR 614.1c, 614.12 (as it enters), 205.3m, 106.6 (restricted mana), 607.2d
    @Test
    fun secludedCourtyard() = card(
        name = "Secluded Quad",
        cost = null,
        type = "Land",
        text = """
            As this land enters, choose a creature type.
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a creature spell of the chosen type or activate an ability of a creature source of the chosen type.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(entering, modify = listOf(Modification.AsEnters(listOf(
            Step.Choose(Choice.CreatureType, bind = "type", remember = true, chooser = Players.You)))))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)), restriction = ObjectFilter.Or(listOf(
            and(spellFilter, creature, ObjectFilter.Sub(SubtypeRef.Remembered("type"))),
            ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = and(creature, ObjectFilter.Sub(SubtypeRef.Remembered("type"))))))), Players.You))),
    )

    // CR 614.1c, 614.12 (as it enters), 106.1b (mana of the chosen color), 607.2d
    @Test
    fun unchartedHaven() = card(
        name = "Uncharted Bay",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped. As it enters, choose a color.
            {T}: Add one mana of the chosen color.
        """,
    ) parsesTo abilities(
        entersTapped,
        static(Mechanic.Replacement(entering,
            modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.Color(), bind = "color", chooser = Players.You, remember = true)))))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), listOf(ColorRef.Remembered("color")))), Players.You))),
    )
}
