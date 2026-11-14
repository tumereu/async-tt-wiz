package arcanum.parser.sets.otj

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * OTJ fixes: wordings the set's chunks left unread or doubted.
 */
class OtjFixesTest {
    // CR 702.170a: the top card of your library has plot for its own mana cost; CR 702.170f: a permission lets plot work from the top of
    // the library (a nonland card's only)
    @Test
    fun plotFromTheTopOfYourLibrary() = card(
        name = "Topdeck Drifter", cost = "{1}{U}{U}", type = "Legendary Creature — Homunculus", pt = "1/1",
        text = """
            Ward {2}
            You may look at the top card of your library any time.
            The top card of your library has plot. The plot cost is equal to its mana cost.
            You may plot nonland cards from the top of your library.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        playerStatic(Mechanic.MayLookAt(Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true))),
        static(Mechanic.GainAbilities(listOf(Keyword.Plot(Cost(listOf(CostPart.ItsManaCost))))),
            affects = Objects.LibraryCards(Players.You, Value.Fixed(1), fromTop = true)),
        static(Mechanic.KeywordFunctionsIn(Keyword.Plot.Kind, Zone.Library, topOnly = true),
            affects = Objects.Each(and(nonland, inZone(Zone.Library), ObjectFilter.OwnedBy(PlayerFilter.You)))),
    )

    // CR 608.2c: "three or more dread counters on it, transform it": both "it"s are this enchantment, as the counter was put on it (a
    // confirmed reading: the transform isn't the attacking creature's)
    @Test
    fun clutchingGloomTransformsItself() = card(
        name = "Clutching Gloom", cost = "{3}{B}", type = "Enchantment",
        text = """
            Whenever a creature you control attacks alone, it gains deathtouch and lifelink until end of turn. Put a dread counter on this enchantment. Then if there are three or more dread counters on it, transform it.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Attack(creatureYouControl, alone = true)),
            untilEndOfTurn(Objects.EventObject(EventRole.Object), has(Keyword.Deathtouch, Keyword.Lifelink)),
            counters(Objects.Self, 1, CounterKind.Named("dread")),
            ifThen(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("dread"), atLeast = 3)),
                Step.Do(Effect.Transform(Objects.Self))),
        ),
    )
}
