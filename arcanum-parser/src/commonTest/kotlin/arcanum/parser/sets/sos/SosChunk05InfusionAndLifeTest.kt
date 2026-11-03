package arcanum.parser.sets.sos

import arcanum.api.Ability
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Measure
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
 * Secrets of Strixhaven chunk 05: infusion (an ability word) — "if you gained life this turn" (CR 119.9) in spells, triggers, statics and
 * "instead" paragraphs (CR 608.2c), "the amount of life you gained this turn", "unless you gained life this turn", a spell that copies
 * itself as it is cast (CR 707.10), and the set's life-gain creatures: Pests, "whenever you gain life", an emblem whose trigger targets
 * (CR 114.4), and dies triggers by power or toughness.
 */
class SosChunk05InfusionAndLifeTest {
    private val infusion = "Infusion"
    private val youGainLife = on(EventPattern.GainLife(PlayerFilter.You))
    private val endStepOfYours = stepBegins(TurnStep.End, PlayerFilter.You)
    private val eachOpponentPlayers = Players.Each(PlayerFilter.Opponent)
    private val drainOne = arrayOf(loseLife(1, eachOpponentPlayers), gainLife(1))

    // CR 608.2b: the creature's controller as it last existed
    @Test
    fun recklessDestiny() = card(
        name = "Reckless Destiny",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            Destroy target creature.
            Infusion — If you gained life this turn, that creature's controller loses 3 life.
        """,
    ) parsesTo abilities(
        spell(
            destroy(target("creature")),
            ifThen(gainedLifeThisTurn, loseLife(3, Players.ControllerOf(target("creature")))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 603.4: an intervening if; X is the life gained this turn, read as it resolves
    @Test
    fun corvinVeinsFreshProvost() = card(
        name = "Corvin, Vein's Fresh Provost",
        cost = "{2}{B}",
        type = "Legendary Creature — Bird Skeleton Warlock",
        pt = "2/1",
        text = """
            Flying
            When Corvin enters, create a 1/1 black and green Pest creature token with "Whenever this token attacks, you gain 1 life."
            Infusion — At the beginning of your end step, if you gained life this turn, return up to one target creature card with mana value X or less from your graveyard to the battlefield, where X is the amount of life you gained this turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, create(pest)),
        triggered(
            endStepOfYours,
            move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(creature, ObjectFilter.ManaValue(Comparison.AtMost,
                Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), Measure.Amount))), min = 0)),
            interveningIf = gainedLifeThisTurn,
            abilityWord = infusion,
        ),
    )

    // CR 603.4: not an intervening if: the target is chosen either way, the condition checked on resolution
    @Test
    fun venomwrightsPupil() = card(
        name = "Venomwright's Pupil",
        cost = "{2}{B}",
        type = "Creature — Orc Warlock",
        pt = "2/2",
        text = "Infusion — When this creature enters, target creature an opponent controls gets -4/-4 until end of turn if you gained life this turn.",
    ) parsesTo abilities(
        triggered(entersSelf, ifThen(gainedLifeThisTurn, pump(target("creature"), -4, -4)), targets = listOf(slot("creature", creatureOpponentControls)),
            abilityWord = infusion),
    )

    // CR 701.21a: the player chooses the permanent to sacrifice
    @Test
    fun sorrowgorgeFiend() = card(
        name = "Sorrowgorge Fiend",
        cost = "{2}{B}{B}",
        type = "Creature — Demon",
        pt = "7/6",
        text = """
            Trample
            Ward—Discard a card.
            Infusion — At the beginning of your end step, sacrifice a permanent unless you gained life this turn.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Ward(cost(discardACard)),
        triggered(endStepOfYours, ifThen(not(gainedLifeThisTurn),
            Step.Choose(Choice.Objects(and(permanentFilter, you), n(1), n(1)), bind = "sacrificed", chooser = Players.You),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed")))), abilityWord = infusion),
    )

    @Test
    fun marrowLaneVendor() = card(
        name = "Marrow Lane Vendor",
        cost = "{2}{B}",
        type = "Creature — Goblin Warlock",
        pt = "2/3",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Infusion — This creature gets +2/+0 as long as you gained life this turn.
        """,
    ) parsesTo abilities(Keyword.Menace, static(pt(2, 0), condition = gainedLifeThisTurn, abilityWord = infusion))

    // CR 608.2c: "instead" replaces the paragraph before when the condition is true
    @Test
    fun wiltingMalediction() = card(
        name = "Wilting Malediction",
        cost = "{1}{B}{B}",
        type = "Sorcery",
        text = """
            All creatures get -2/-2 until end of turn.
            Infusion — If you gained life this turn, destroy all creatures instead.
        """,
    ) parsesTo abilities(
        spell(ifThen(gainedLifeThisTurn, destroy(Objects.Each(creature)), otherwise = listOf(pump(Objects.Each(creature), -2, -2))), abilityWord = infusion),
    )

    @Test
    fun burstingBloomtide() = card(
        name = "Bursting Bloomtide",
        cost = "{2}{G}",
        type = "Instant",
        text = """
            Put two +1/+1 counters on target creature.
            Infusion — If you gained life this turn, that creature also gains trample and indestructible until end of turn.
        """,
    ) parsesTo abilities(
        spell(
            counters(target("creature"), 2),
            ifThen(gainedLifeThisTurn, gains(target("creature"), Keyword.Trample, Keyword.Indestructible)),
            targets = listOf(slot("creature", creature)),
        ),
    )

    // CR 701.20a: revealed cards are shown; the rest go to the bottom in a random order
    @Test
    fun trailTheGlowmoths() = card(
        name = "Trail the Glowmoths",
        cost = "{1}{G}",
        type = "Sorcery",
        text = "Infusion — Look at the top four cards of your library. You may reveal a creature or land card from among them and put it into your hand. If you gained life this turn, you may instead reveal two creature and/or land cards from among them and put them into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        spell(
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(4), fromTop = true), Players.You, bind = "looked")),
            ifThen(gainedLifeThisTurn,
                Step.Choose(Choice.Objects(or(creature, land), n(0), n(2), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
                doing(Effect.Reveal(Objects.Bound("chosen"))),
                move(Objects.Bound("chosen"), Zone.Hand),
                otherwise = listOf(
                    Step.Choose(Choice.Objects(or(creature, land), n(0), n(1), from = Objects.Bound("looked")), bind = "chosen", chooser = Players.You),
                    doing(Effect.Reveal(Objects.Bound("chosen"))),
                    move(Objects.Bound("chosen"), Zone.Hand))),
            doing(Effect.Move(Objects.Except(Objects.Bound("looked"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            abilityWord = infusion,
        ),
    )

    // CR 707.10: the copy is created on the Stack as the trigger resolves; CR 113.6: the trigger works on the Stack
    @Test
    fun glowmothsBlessing() = card(
        name = "Glowmoth's Blessing",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Infusion — When you cast this spell, copy it if you gained life this turn. You may choose new targets for the copy.
            Target creature gets +2/+4 until end of turn.
        """,
    ) parsesTo abilities(
        triggered(youCast(ObjectFilter.Is(Objects.Self)), ifThen(gainedLifeThisTurn, doing(Effect.CopySpell(Objects.Self, Players.You, newTargets = true))),
            functionsIn = listOf(Zone.Stack), abilityWord = infusion),
        spell(pump(target("creature"), 2, 4), targets = listOf(slot("creature", creature))),
    )

    // CR 702.21a's trigger shape, "you may draw" on resolution
    @Test
    fun emeritusBrewmaster() = card(
        name = "Emeritus Brewmaster",
        cost = "{4}{G}",
        type = "Creature — Troll Druid",
        pt = "4/5",
        text = """
            Vigilance
            Whenever this creature becomes the target of a spell or ability an opponent controls, you may draw a card.
            Infusion — This creature gets +2/+0 as long as you gained life this turn.
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        triggered(on(EventPattern.BecomesTarget(ObjectFilter.Is(Objects.Self), opponents)), Step.May(listOf(draw(1)), Players.You)),
        static(pt(2, 0), condition = gainedLifeThisTurn, abilityWord = infusion),
    )

    @Test
    fun briarknuckleBrawler() = card(
        name = "Briarknuckle Brawler",
        cost = "{2}{G}",
        type = "Creature — Elf Druid",
        pt = "3/3",
        text = """
            Ward {1} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
            Infusion — Creatures you control get +1/+0 and have trample as long as you gained life this turn.
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{1}")),
        static(pt(1, 0), has(Keyword.Trample), affects = Objects.Each(creatureYouControl), condition = gainedLifeThisTurn, abilityWord = infusion),
    )

    @Test
    fun elderwoodInstructor() = card(
        name = "Elderwood Instructor",
        cost = "{2}{B}{G}",
        type = "Creature — Treefolk Druid",
        pt = "4/4",
        text = """
            Reach, vigilance
            Infusion — When this creature enters, put two +1/+1 counters on it if you gained life this turn.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Vigilance,
        triggered(entersSelf, ifThen(gainedLifeThisTurn, counters(Objects.EventObject(arcanum.api.EventRole.After), 2)), abilityWord = infusion),
    )

    @Test
    fun grubbIdleVermin() = card(
        name = "Grubb, Idle Vermin",
        cost = "{1}{B}{G}",
        type = "Legendary Creature — Pest",
        pt = "3/4",
        text = "Whenever you gain life, put a +1/+1 counter on each Pest, Bat, Insect, Snake, and Spider you control.",
    ) parsesTo abilities(
        triggered(youGainLife, counters(Objects.Each(and(or(sub("Pest"), sub("Bat"), sub("Insect"), sub("Snake"), sub("Spider")), you)))),
    )

    @Test
    fun mirewaterGlowmoth() = card(
        name = "Mirewater Glowmoth",
        cost = "{B}{G}",
        type = "Creature — Spirit Frog",
        pt = "2/2",
        text = "Whenever this creature or another creature you control enters, you gain 1 life.",
    ) parsesTo abilities(triggered(enters(or(ObjectFilter.Is(Objects.Self), and(another, creature, you))), gainLife(1)))

    @Test
    fun verminStandardBearer() = card(
        name = "Vermin Standard-Bearer",
        cost = "{1}{B}{G}",
        type = "Creature — Pest Ape",
        pt = "2/3",
        text = """
            Trample
            Whenever you gain life, put a +1/+1 counter on this creature.
        """,
    ) parsesTo abilities(Keyword.Trample, triggered(youGainLife, counters(Objects.Self)))

    @Test
    fun tutorsNuisance() = card(
        name = "Tutor's Nuisance",
        cost = "{B}{G}",
        type = "Creature — Skeleton Pest",
        pt = "1/1",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Whenever this creature attacks, you gain 1 life.
            {B}{G}: Return this card from your graveyard to the battlefield tapped.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(attacksSelf, gainLife(1)),
        // CR 113.6m: it works in the graveyard, where its effect moves it from
        activated(manaCost("{B}{G}"), move(Objects.Self, Zone.Battlefield, tapped = true, postBind = "returned")),
    )

    @Test
    fun merchantsBane() = card(
        name = "Merchant's Bane",
        cost = "{2}{G}",
        type = "Creature — Badger Pest",
        pt = "4/2",
        text = """
            Trample
            Whenever this creature attacks, you gain 2 life.
        """,
    ) parsesTo abilities(Keyword.Trample, triggered(attacksSelf, gainLife(2)))

    @Test
    fun attentiveLifeshaper() = card(
        name = "Attentive Lifeshaper",
        cost = "{1}{G}",
        type = "Creature — Dryad Druid",
        pt = "2/2",
        text = """
            When this creature enters, you gain 1 life.
            {2}{G}: This creature gets +2/+2 until end of turn. Activate only once each turn.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, gainLife(1)),
        activated(manaCost("{2}{G}"), pump(Objects.Self, 2, 2), rules = anyTime.copy(perTurn = 1)),
    )

    // CR 606: loyalty abilities; CR 114.4: the emblem's trigger works in the command zone and targets as it triggers
    @Test
    fun professorVashkaDren() = card(
        name = "Professor Vashka Dren",
        cost = "{2}{B}{G}",
        type = "Legendary Planeswalker — Dellian",
        loyalty = "5",
        text = """
            [+2]: You gain 3 life.
            [0]: You draw a card and lose 1 life.
            [−3]: Destroy target creature.
            [−6]: You get an emblem with "Whenever you gain life, target opponent loses that much life."
        """,
    ) parsesTo abilities(
        activated(cost(CostPart.Loyalty(n(2))), gainLife(3)),
        activated(cost(CostPart.Loyalty(n(0))), draw(1), loseLife(1)),
        activated(cost(CostPart.Loyalty(n(-3))), destroy(target("creature")), targets = listOf(slot("creature", creature))),
        activated(cost(CostPart.Loyalty(n(-6))), doing(Effect.CreateEmblem(listOf(
            triggered(youGainLife, doing(Effect.LoseLife(Players.Target("opponent"), Value.EventAmount)), targets = listOf(targetOpponent))), Players.You))),
    )

    // CR 603.10a: the creature's power and toughness as it last existed
    @Test
    fun seylaGravebloomHerbalist() = card(
        name = "Seyla, Gravebloom Herbalist",
        cost = "{2}{B}",
        type = "Legendary Creature — Vampire Druid",
        pt = "2/2",
        text = """
            Deathtouch
            Whenever a creature you control with power or toughness 1 or less dies, target opponent loses 2 life and you gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(
            on(EventPattern.ZoneChange(and(creature, you, or(power(Comparison.AtMost, 1), ObjectFilter.Toughness(Comparison.AtMost, n(1)))),
                from = Zone.Battlefield, to = Zone.Graveyard)),
            loseLife(2, Players.Target("opponent")),
            gainLife(2),
            targets = listOf(targetOpponent),
        ),
    )

    @Test
    fun crucibleOfVitae() = card(
        name = "Crucible of Vitae",
        cost = "{1}{B}{G}",
        type = "Artifact",
        text = """
            Whenever a creature you control dies, each opponent loses 1 life and you gain 1 life.
            {1}{B}{G}, {T}, Sacrifice a creature: Return target creature card from your graveyard to the battlefield. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield, to = Zone.Graveyard)), *drainOne),
        activated(cost(mana("{1}{B}{G}"), tap, sacrificeA(creature)), move(target("card"), Zone.Battlefield),
            targets = listOf(slot("card", inYourGraveyard(creature))), rules = asSorcery),
    )

    @Test
    fun lifestitchPupil() = card(
        name = "Lifestitch Pupil",
        cost = "{B}{B/G}{G}",
        type = "Creature — Dryad Warlock",
        pt = "3/1",
        text = """
            When this creature enters, create a 1/1 black and green Pest creature token with "Whenever this token attacks, you gain 1 life."
            At the beginning of your end step, if a creature died under your control this turn, draw a card.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(pest)),
        triggered(endStepOfYours, draw(1), interveningIf = arcanum.api.Condition.HappenedThisTurn(
            EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events)),
    )

    @Test
    fun smirkingDuskscribe() = card(
        name = "Smirking Duskscribe",
        cost = "{4}{B}",
        type = "Creature — Vampire Warlock",
        pt = "3/3",
        text = """
            Flying
            When this creature enters, each opponent loses 2 life and you gain 2 life.
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(entersSelf, loseLife(2, eachOpponentPlayers), gainLife(2)))

    // CR 101.4: each opponent discards at the same time
    @Test
    fun unleashTheVermin() = card(
        name = "Unleash the Vermin",
        cost = "{1}{B}",
        type = "Sorcery",
        text = "Each opponent discards a card. You create a 1/1 black and green Pest creature token with \"Whenever this token attacks, you gain 1 life.\"",
    ) parsesTo abilities(spell(discard(1, eachOpponentPlayers), create(pest)))

    @Test
    fun grubnestSloth() = card(
        name = "Grubnest Sloth",
        cost = "{3}{G}",
        type = "Creature — Plant Sloth",
        pt = "4/4",
        text = """
            Reach
            When this creature dies, create two 1/1 black and green Pest creature tokens with "Whenever this token attacks, you gain 1 life."
        """,
    ) parsesTo abilities(Keyword.Reach, triggered(diesSelf, create(pest, n(2))))
}
