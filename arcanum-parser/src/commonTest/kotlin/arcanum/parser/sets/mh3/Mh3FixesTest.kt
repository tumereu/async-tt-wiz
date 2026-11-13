package arcanum.parser.sets.mh3

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/*
 * MH3 fix chunk: cards the chunks left unread for a concept the API lacked (Winter Moon's untap limit, ...).
 */
class Mh3FixesTest {
    // look at four, one may stay on top (CR 401.4), the rest exiled
    private val FIRST_UPKEEP_LOOK = Instructions(steps = listOf(
        Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "looked")),
        Step.Choose(Choice.Objects(ObjectFilter.Any, n(0), n(1), from = Objects.Bound("looked")), bind = "kept", chooser = Players.You),
        move(Objects.Except(Objects.Bound("looked"), Objects.Bound("kept")), Zone.Exile),
    ))

    // CR 502.3: the active player chooses which of their nonbasic lands untaps; every player is affected
    @Test
    fun winterMoonLike() = card(
        name = "Hoarfrost Lantern",
        cost = "{2}",
        type = "Artifact",
        text = "Players can't untap more than one nonbasic land during their untap steps.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.UntapLimit(1, and(not(ObjectFilter.Super(Supertype.Basic)), land))),
            affects = Affected.Players(Players.Each(PlayerFilter.Any))),
    )

    // CR 601.2h, 107.4e: each plain {B} of any cost its controller pays may be paid with 2 life (a player rule, no cost changes)
    @Test
    fun krrikLike() = card(
        name = "Krovath, Scion of Rot",
        cost = "{4}{B/P}{B/P}{B/P}",
        type = "Legendary Creature — Phyrexian Horror Minion",
        pt = "2/2",
        text = """
            ({B/P} can be paid with either {B} or 2 life.)
            Lifelink
            For each {B} in a cost, you may pay 2 life rather than pay that mana.
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        Ability.Static(listOf(Mechanic.PayLifeForMana(Color.Black)), affects = Affected.Players(Players.You)),
    )

    // CR 702.138a, 205.2a: any number of other cards whose card types number four or more
    @Test
    fun nethergoyfEscape() = card(
        name = "Gravemaw Lhurgoyf",
        cost = "{B}",
        type = "Creature — Lhurgoyf",
        pt = "1/1",
        text = "Escape—{2}{B}, Exile any number of other cards from your graveyard with four or more card types among them. (You may cast this card from your graveyard for its escape cost.)",
    ) parsesTo abilities(
        Keyword.Escape(Cost(listOf(mana("{2}{B}"), CostPart.Exile(CostObjects.Choose(and(another, cardFilter), count = null,
            distinct = Distinct(SharedQuality.CardType, 4)), from = Zone.Graveyard)))),
    )

    // CR 103.6b, 603.7: an opening-hand reveal setting up a delayed trigger at its owner's next (first) upkeep
    @Test
    fun devourerOfDestinyReveal() = card(
        name = "Maw of the Foretold End",
        cost = "{5}{C}{C}",
        type = "Creature — Eldrazi",
        pt = "6/6",
        text = "You may reveal this card from your opening hand. If you do, at the beginning of your first upkeep, look at the top four cards of your library. You may put one of those cards back on top of your library. Exile the rest.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.OpeningHandReveal(Instructions(steps = listOf(Step.Do(Effect.CreateDelayedTrigger(
            Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)), FIRST_UPKEEP_LOOK)))))),
            affects = Affected.Objects(Objects.Self), functionsIn = listOf(Zone.Hand)),
    )
}
