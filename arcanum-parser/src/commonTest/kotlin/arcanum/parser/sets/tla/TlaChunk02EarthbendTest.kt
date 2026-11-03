package arcanum.parser.sets.tla

import arcanum.api.Ability
import arcanum.api.AbilityLabel
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 02: earthbend (CR 701.66a: target land you control becomes a 0/0 land creature with haste, gets N
 * +1/+1 counters and returns when it dies or is exiled) as an enters, attack, Ally and Landfall trigger, an activated and exhaust ability, a
 * spell with a reflexive fight (CR 603.12) and a mode of "choose up to X"; and the creatures around it: counters on lands, colours among
 * Allies, +1/+1 counters put by you.
 */
class TlaChunk02EarthbendTest {
    // CR 701.66a
    @Test
    fun burrowmole() = card(
        name = "Burrowmole",
        cost = "{4}{G}",
        type = "Creature — Badger Mole",
        text = """
            When this creature enters, earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
            Creatures you control with +1/+1 counters on them have trample.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        triggered(entersSelf, earthbend(2), targets = listOf(landSlot())),
        static(has(Keyword.Trample), affects = Objects.Each(and(creature, you, ObjectFilter.HasCounter(p1p1, atLeast = 1)))),
    )

    // CR 701.66a; a triggered mana ability (CR 605.1b)
    @Test
    fun burrowmoleCub() = card(
        name = "Burrowmole Cub",
        cost = "{1}{G}",
        type = "Creature — Badger Mole",
        text = """
            When this creature enters, earthbend 1. (Target land you control becomes a 0/0 creature with haste that's still a land. Put a +1/+1 counter on it. When it dies or is exiled, return it to the battlefield tapped.)
            Whenever you tap a creature for mana, add an additional {G}.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, earthbend(1), targets = listOf(landSlot())),
        triggered(on(EventPattern.Tap(creatureYouControl, forMana = true)), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))),
    )

    // CR 701.66a: X counted as it resolves (CR 107.3c)
    @Test
    fun theCragReadyToBrawl() = card(
        name = "The Crag, Ready to Brawl",
        cost = "{3}{G}",
        type = "Legendary Creature — Human Warrior Performer",
        text = "Whenever The Crag attacks, earthbend X, where X is the number of creatures you control with power 4 or greater. (Target land you control becomes a 0/0 creature with haste that's still a land. Put X +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)",
        pt = "4/4",
    ) parsesTo abilities(
        triggered(attacksSelf, earthbend(count(and(creature, you, power(Comparison.AtLeast, 4)))), targets = listOf(landSlot())),
    )

    // CR 700.2: "choose up to X" (at most X modes)
    @Test
    fun odricKingOfThreeTrials() = card(
        name = "Odric, King of Three Trials",
        cost = "{5}{G}",
        type = "Legendary Creature — Human Noble Ally",
        text = """
            When Odric enters, choose up to X, where X is the number of Lesson cards in your graveyard —
            • Put three +1/+1 counters on Odric.
            • Target player scries 3.
            • Earthbend 3. (Target land you control becomes a 0/0 creature with haste that's still a land. Put three +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Ability.Triggered(entersSelf, Instructions(modes = Modes(listOf(
            mode(counters(Objects.Self, 3)),
            mode(scry(3, Players.Target("player")), targets = listOf(targetPlayer)),
            mode(earthbend(3), targets = listOf(landSlot())),
        ), min = 0, max = 3, chooser = Players.You, maxCount = lessonsInGraveyard()))),
    )

    // CR 701.66a; CR 603.2h ("Do this only once each turn"), 122.6
    @Test
    fun stoneRealmGeneral() = card(
        name = "Stone Realm General",
        cost = "{3}{G}",
        type = "Creature — Human Soldier Ally",
        text = """
            When this creature enters, earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
            Whenever you put one or more +1/+1 counters on a creature, you may gain that much life. Do this only once each turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, earthbend(2), targets = listOf(landSlot())),
        triggered(Trigger.On(EventPattern.CountersPut(creature, p1p1, causedBy = you), oneOrMore = true),
            Step.May(listOf(doing(Effect.GainLife(Players.You, Value.EventAmount))), player = Players.You), actionOncePerTurn = true),
    )

    // CR 701.66a, 603.12, 701.14
    @Test
    fun stoneBrawl() = card(
        name = "Stone Brawl",
        cost = "{3}{G}",
        type = "Sorcery",
        text = "Earthbend 2. When you do, up to one target creature you control fights target creature an opponent controls. (To earthbend 2, target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped. Creatures that fight each deal damage equal to their power to the other.)",
    ) parsesTo abilities(
        spell(earthbend(2),
            reflexive(doing(Effect.Fight(target("creature"), target("second creature"))),
                targets = listOf(slot("creature", creatureYouControl, min = 0), slot("second creature", creatureOpponentControls))),
            targets = listOf(landSlot())),
    )

    // CR 701.66a; Landfall, a reflexive trigger with a condition (CR 603.12)
    @Test
    fun stoneshaperAscension() = card(
        name = "Stoneshaper Ascension",
        cost = "{2}{G}",
        type = "Enchantment",
        text = """
            When this enchantment enters, earthbend 2. Then search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
            Landfall — Whenever a land you control enters, put a quest counter on this enchantment. When you do, if it has four or more quest counters on it, put a +1/+1 counter on target creature you control. It gains trample until end of turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, earthbend(2), *rampTapped.toTypedArray(), targets = listOf(landSlot())),
        triggered(landfall, counters(Objects.Self, 1, quest),
            Step.If(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(quest, atLeast = 4)), then = listOf(
                reflexive(counters(target("creature")), gains(target("creature"), Keyword.Trample), targets = listOf(slot("creature", creatureYouControl))))),
            abilityWord = "Landfall"),
    )

    @Test
    fun stoneshapingLesson() = card(
        name = "Stoneshaping Lesson",
        cost = "{3}{G}",
        type = "Sorcery — Lesson",
        text = "Earthbend 4. (Target land you control becomes a 0/0 creature with haste that's still a land. Put four +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)",
    ) parsesTo abilities(
        spell(earthbend(4), targets = listOf(landSlot())),
    )

    // CR 105.2: the colours among Allies you control
    @Test
    fun earthboundComrade() = card(
        name = "Earthbound Comrade",
        cost = "{G}",
        type = "Creature — Human Soldier Ally",
        text = """
            This creature gets +1/+0 for each color among Allies you control.
            {2}{W}{U}{B}{R}{G}: Earthbend 5. (Target land you control becomes a 0/0 creature with haste that's still a land. Put five +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """,
        pt = "0/2",
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(Value.CountDistinct(Objects.Each(and(ally, you)), SharedQuality.Color), n(0))),
        activated(cost(mana("{2}{W}{U}{B}{R}{G}")), earthbend(5), targets = listOf(landSlot())),
    )

    @Test
    fun tobinHiddenTalent() = card(
        name = "Tobin, Hidden Talent",
        cost = "{1}{G}",
        type = "Legendary Creature — Human Peasant Ally",
        text = "Whenever another Ally you control enters, earthbend 1. (Target land you control becomes a 0/0 creature with haste that's still a land. Put a +1/+1 counter on it. When it dies or is exiled, return it to the battlefield tapped.)",
        pt = "1/1",
    ) parsesTo abilities(
        triggered(anotherAllyEnters, earthbend(1), targets = listOf(landSlot())),
    )

    // CR 702.177a (exhaust), 701.66a
    @Test
    fun mutinousPrisoners() = card(
        name = "Mutinous Prisoners",
        cost = "{1}{G}",
        type = "Creature — Human Peasant Ally",
        text = "Exhaust — {6}: Put two +1/+1 counters on this creature, then earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped. Activate each exhaust ability only once.)",
        pt = "2/2",
    ) parsesTo abilities(
        activated(cost(mana("{6}")), counters(Objects.Self, 2), earthbend(2), targets = listOf(landSlot()), label = AbilityLabel.Exhaust),
    )

    // CR 701.66a, 702.34a
    @Test
    fun stoneslide() = card(
        name = "Stoneslide",
        cost = "{2}{G}",
        type = "Sorcery — Lesson",
        text = """
            Earthbend X, where X is the number of Forests you control. (Target land you control becomes a 0/0 creature with haste that's still a land. Put X +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
            Flashback {5}{G} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        spell(earthbend(count(and(sub("Forest"), you))), targets = listOf(landSlot())),
        Keyword.Flashback(manaCost("{5}{G}")),
    )

    // CR 604.3: a characteristic-defining power counting counters on lands
    @Test
    fun miraTheSightlessBrawler() = card(
        name = "Mira, the Sightless Brawler",
        cost = "{2}{G}",
        type = "Legendary Creature — Human Warrior Ally",
        text = """
            When Mira enters, earthbend 2. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
            Mira's power is equal to the number of +1/+1 counters on lands you control.
        """,
        pt = "*/3",
    ) parsesTo abilities(
        triggered(entersSelf, earthbend(2), targets = listOf(landSlot())),
        static(Mechanic.SetPowerToughness(Value.Of(NumericProperty.Counters(p1p1), Objects.Each(and(land, you)), Combine.Total), null), characteristicDefining = true),
    )

    // CR 614.1c (enters tapped unless), 701.66a, 602.5d
    @Test
    fun stoneholm() = card(
        name = "Stoneholm",
        cost = null,
        type = "Land",
        text = """
            This land enters tapped unless you control a basic land.
            {T}: Add {G}.
            {2}{G}, {T}: Earthbend 2. Activate only as a sorcery. (Target land you control becomes a 0/0 creature with haste that's still a land. Put two +1/+1 counters on it. When it dies or is exiled, return it to the battlefield tapped.)
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
            condition = Condition.Not(Condition.Exists(and(basic, land, you), 1))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))),
        activated(cost(mana("{2}{G}"), tap), earthbend(2), targets = listOf(landSlot()), rules = asSorcery),
    )
}
