package arcanum.parser.sets.dmr

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/** DMR chunk 13: life gain and loss, flanking, looking at the library, counters. */
class DmrChunk13GainLifeKeywordsLookCountersTest {
    private val javelin = CounterKind.Named("javelin")
    private val petal = CounterKind.Named("petal")

    // CR 119.3, 603.2: the amount is counted on resolution; the card names itself, so the count is by name
    @Test
    fun clericOfTheAftermostOrder() = card(
        name = "Cleric of the Aftermost Order", cost = "{1}{W}", type = "Creature — Human Cleric", pt = "2/2",
        text = "When this creature enters, you gain 2 life for each creature you control named Cleric of the Aftermost Order.",
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.GainLife(Players.You, Value.Times(
            count(and(creature, you, ObjectFilter.Named("Cleric of the Aftermost Order"))), 2,
        )))),
    )

    // CR 120.3, 603.2, 119.3: "that much" is the damage this creature dealt, combat or not; CR 603.2c: once for all it deals at once
    @Test
    fun hollowGargoyle() = card(
        name = "Hollow Gargoyle", cost = "{5}{W}", type = "Creature — Gargoyle", pt = "3/3",
        text = """
            Flying, first strike
            Whenever this creature deals damage, you gain that much life.
        """,
    ) parsesTo abilities(
        Keyword.Flying, Keyword.FirstStrike,
        triggered(
            on(EventPattern.Damage(isSelf, RecipientFilter(), per = DamageOccurrence.Source)),
            Step.Do(Effect.GainLife(Players.You, Value.EventAmount)),
        ),
    )

    // CR 702.25a: flanking
    @Test
    fun duskLancer() = card(
        name = "Dusk Lancer", cost = "{2}{R}", type = "Creature — Human Knight", pt = "2/2",
        text = """
            Haste
            Flanking (Whenever a creature without flanking blocks this creature, the blocking creature gets -1/-1 until end of turn.)
        """,
    ) parsesTo abilities(Keyword.Haste, Keyword.Flanking)

    // CR 701.20 (look), 401.4 (any order): an activated and a triggered version
    @Test
    fun avenDreamshaper() = card(
        name = "Aven Dreamshaper", cost = "{6}{U}", type = "Creature — Bird Wizard", pt = "4/5",
        text = """
            Flying
            When this creature enters, look at the top four cards of your library, then put them back in any order.
            {4}{U}: Look at the top four cards of your library, then put them back in any order.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        whenThisEnters(*lookFour),
        activated(cost(mana("{4}{U}")), *lookFour),
    )

    // CR 120.3, 119.3, 603.2: any creature entering, including this one's controller's own
    @Test
    fun squalidNewt() = card(
        name = "Squalid Newt", cost = "{1}{B}", type = "Creature — Zombie Frog Beast", pt = "3/3",
        text = "Whenever another creature enters, you lose 1 life.",
    ) parsesTo abilities(
        triggered(enters(and(another, creature)), loseLife(1)),
    )

    // CR 122.1, 603.2, 106.1b: X is the petal counters; sacrificing is a cost
    @Test
    fun blossomOfPetals() = card(
        name = "Blossom of Petals", cost = "{2}", type = "Artifact", text = """
            At the beginning of your upkeep, you may put a petal counter on this artifact.
            {T}, Sacrifice this artifact: Add X mana of any one color, where X is the number of petal counters on this artifact.
        """,
    ) parsesTo abilities(
        triggered(yourUpkeep, Step.May(listOf(counters(Objects.Self, 1, petal)), player = Players.You)),
        activated(
            cost(tap, sacrificeSelf),
            Step.Do(Effect.AddMana(
                ManaProduction(ManaKind.Choose(amount = Value.Of(NumericProperty.Counters(petal), Objects.Self, Combine.Single))),
                Players.You,
            )),
        ),
    )

    // CR 614.1c (enters with counters), 602.2, 122.1: removing a counter is a cost
    @Test
    fun icatianDartThrower() = card(
        name = "Icatian Dart-Thrower", cost = "{W}", type = "Creature — Human Soldier", pt = "1/1", text = """
            This creature enters with a javelin counter on it.
            {T}, Remove a javelin counter from this creature: It deals 1 damage to any target.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.ZoneChange(isSelf, to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(javelin, n(1))),
        )), affects = Affected.Objects(Objects.Self)),
        activated(
            cost(tap, CostPart.RemoveCounters(javelin, amount = n(1), from = CostObjects.Self)),
            damage(1, toAny), targets = listOf(anyTarget),
        ),
    )

    private val lookFour = arrayOf(
        Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, count = n(4), fromTop = true), bind = "seen", players = Players.You)),
        Step.Do(Effect.Reorder(Objects.Bound("seen"), chooser = Players.You)),
    )
}
