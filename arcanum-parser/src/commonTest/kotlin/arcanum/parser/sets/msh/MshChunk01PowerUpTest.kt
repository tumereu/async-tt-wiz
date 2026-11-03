package arcanum.parser.sets.msh

import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.Mechanic
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
 * Marvel Super Heroes chunk 01: power-up (CR 702.193) on white, blue and black cards. "Power-up — [cost]: [effect]" is an activated
 * ability labelled power-up: activated only once, and costing less by the permanent's mana cost in the turn it entered (the label's
 * rules, `KeywordExpansions.withLabelRules`, and the engine). Two counters of different kinds (a keyword counter, CR 122.1b); an
 * extra turn with power-up abilities forbidden during it (CR 500.7); X in the cost (CR 107.3a); a look at the top cards with a
 * double-faced card transformed (CR 701.27c).
 */
class MshChunk01PowerUpTest {
    @Test
    fun pluckyScrapper() = card(
        name = "Plucky Scrapper",
        cost = "{1}{W}",
        type = "Creature — Human Warrior Hero",
        text = """
            Lifelink
            Power-up — {4}{W}: Put two +1/+1 counters on this creature. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Lifelink,
        powerUp("{4}{W}", counters(Objects.Self, 2)),
    )

    // CR 122.1b: an indestructible counter gives the keyword
    @Test
    fun stellarParagonSkyWarden() = card(
        name = "Stellar Paragon, Sky Warden",
        cost = "{3}{W}{W}",
        type = "Legendary Creature — Human Kree Hero",
        text = """
            Flash
            Flying, lifelink
            Power-up — {5}{W}{W}: Put a +1/+1 counter and an indestructible counter on Stellar Paragon. (Activate each power-up ability only once. Reduce the cost by her mana cost if she entered this turn.)
        """,
        pt = "5/4",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        Keyword.Lifelink,
        powerUp("{5}{W}{W}",
            counters(Objects.Self),
            counters(Objects.Self, 1, CounterKind.Keyword(Keyword.Indestructible.kind))),
    )

    // CR 701.27c: transforming a card that isn't double-faced does nothing, so the choice is offered for the card put onto the battlefield
    @Test
    fun directorGrimmSpymaster() = card(
        name = "Director Grimm, Spymaster",
        cost = "{W}",
        type = "Legendary Creature — Human Spy Hero",
        text = "Power-up — {W}{U}{B}{R}{G}: Put two +1/+1 counters on Director Grimm, then look at the top seven cards of your library. You may put a Hero, Equipment, or Vehicle card from among them onto the battlefield. If it's a double-faced card, you may transform it. Put the rest on the bottom of your library in a random order. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)",
        pt = "2/1",
    ) parsesTo abilities(
        powerUp("{W}{U}{B}{R}{G}",
            counters(Objects.Self, 2),
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, n(7), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(or(hero, equipment, sub("Vehicle")), n(0), n(1), from = Objects.Bound("seen")),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, postBind = "put"),
            Step.May(listOf(Step.Do(Effect.Transform(Objects.Bound("put")))), player = Players.You),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")),
                Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    @Test
    fun hoveringSentryBot() = card(
        name = "Hovering Sentry Bot",
        cost = "{U}",
        type = "Artifact Creature — Robot Villain",
        text = """
            Flying
            Power-up — {5}{U}: Put three +1/+1 counters on this creature. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Flying,
        powerUp("{5}{U}", counters(Objects.Self, 3)),
    )

    @Test
    fun daringLabAssistant() = card(
        name = "Daring Lab Assistant",
        cost = "{1}{U}",
        type = "Creature — Human Scientist",
        text = "Power-up — {5}{U}: Put a +1/+1 counter on this creature and draw two cards. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)",
        pt = "1/3",
    ) parsesTo abilities(
        powerUp("{5}{U}", counters(Objects.Self), draw(2)),
    )

    // CR 500.7: the extra turn; power-up abilities can't be activated during it, from its upkeep (no player gets priority in an untap
    // step, CR 502.4)
    @Test
    fun chronarchTheConqueror() = card(
        name = "Chronarch the Conqueror",
        cost = "{2}{U}{U}",
        type = "Legendary Creature — Human Villain",
        text = """
            Flying
            Power-up — {5}{U}{U}{U}: Put a +1/+1 counter on Chronarch. Take an extra turn after this one. During that turn, power-up abilities can't be activated. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
        """,
        pt = "4/5",
    ) parsesTo abilities(
        Keyword.Flying,
        powerUp("{5}{U}{U}{U}",
            counters(Objects.Self),
            Step.Do(Effect.ExtraTurn(Players.You)),
            Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.Upkeep, PlayerFilter.You), arcanum.api.Instructions(steps = listOf(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantActivate(ObjectFilter.AbilityOf(label = arcanum.api.AbilityLabel.PowerUp))),
                    Affected.Players(Players.Each(PlayerFilter.Any)), Duration.EndOfTurn)))))),
        ),
    )

    // CR 107.3a: X announced as it is activated; the condition is checked as blockers are declared (CR 509.1b)
    @Test
    fun dwindleSizeShifter() = card(
        name = "Dwindle, Size Shifter",
        cost = "{U}",
        type = "Legendary Creature — Human Hero",
        text = """
            Dwindle can't be blocked if her power is 1 or less.
            Power-up — {X}{U}{U}: Put X +1/+1 counters on Dwindle. (Activate each power-up ability only once. Reduce the cost by her mana cost if she entered this turn.)
        """,
        pt = "1/1",
    ) parsesTo abilities(
        static(Mechanic.CantBeBlocked, condition = arcanum.api.Condition.ObjectMatches(Objects.Self, power(arcanum.api.Comparison.AtMost, 1))),
        powerUp("{X}{U}{U}", counters(Objects.Self, Value.Announced("X")), announced = listOf("X")),
    )

    @Test
    fun shadowFistAdept() = card(
        name = "Shadow Fist Adept",
        cost = "{2}{B}",
        type = "Creature — Human Ninja Villain",
        text = """
            Deathtouch
            Power-up — {4}{B}: Each opponent discards a card. Put a +1/+1 counter on this creature. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        powerUp("{4}{B}", discard(1, Players.Each(PlayerFilter.Opponent)), counters(Objects.Self)),
    )

    @Test
    fun deathlessLegionary() = card(
        name = "Deathless Legionary",
        cost = "{3}{B}",
        type = "Creature — Vampire Villain",
        text = """
            Flying
            Power-up — {5}{B}{B}: Return up to one target creature card from your graveyard to your hand. Put two +1/+1 counters on this creature. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Flying,
        powerUp("{5}{B}{B}",
            move(target("cards"), Zone.Hand),
            counters(Objects.Self, 2),
            targets = listOf(slot("cards", inYourGraveyard(creature), min = 0))),
    )
}
