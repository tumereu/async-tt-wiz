package arcanum.parser.sets.sos

import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Trigger
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Secrets of Strixhaven chunk 09: preparation cards (CR 722) in green and two colours, and effects that make creatures prepared or
 * unprepared (CR 722.3a, b: only creatures with prepare spells can be): becoming prepared by an intervening if on attacking, by casting a
 * creature spell, by cards leaving your graveyard, by landfall, by an activated ability exiling a card from the graveyard; the spells:
 * regrowth, X life, Rampant Growth, milling and reanimating from among the milled cards, a tutor for an instant or sorcery card.
 */
class SosChunk09PrepareGreenAndMulticoloredTest {

    // CR 603.4: an intervening if, checked as it attacks and as it resolves
    @Test
    fun emeritusOfPlenty() = cardFaces(
        Layout.Prepare,
        card(
            name = "Emeritus of Plenty",
            cost = "{2}{G}",
            type = "Creature — Elf Druid",
            pt = "3/4",
            text = """
                Vigilance
                This creature enters prepared.
                Whenever this creature attacks, if you control eight or more lands, this creature becomes prepared.
            """,
        ) to abilities(
            Keyword.Vigilance,
            entersPrepared,
            triggered(attacksSelf, becomesPrepared(), interveningIf = Condition.Exists(and(land, you), atLeast = 8)),
        ),
        card(
            name = "Regather",
            cost = "{1}{G}",
            type = "Sorcery",
            text = "Return target card from your graveyard to your hand.",
        ) to abilities(
            spell(move(target("card"), Zone.Hand), targets = listOf(slot("card", inYourGraveyard(cardFilter)))),
        ),
    )

    @Test
    fun sickbayMender() = cardFaces(
        Layout.Prepare,
        card(
            name = "Sickbay Mender",
            cost = "{1}{G}",
            type = "Creature — Cat Cleric",
            pt = "2/3",
            text = "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            entersPrepared,
        ),
        card(
            name = "Rivulet of Vigor",
            cost = "{X}{G}",
            type = "Sorcery",
            text = "Target player gains X life.",
        ) to abilities(
            spell(doing(Effect.GainLife(Players.Target("player"), x)), targets = listOf(targetPlayer), announced = listOf("X")),
        ),
    )

    @Test
    fun diligentFreshman() = cardFaces(
        Layout.Prepare,
        card(
            name = "Diligent Freshman",
            cost = "{G}",
            type = "Creature — Bear Wizard",
            pt = "1/1",
            text = "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            entersPrepared,
        ),
        card(
            name = "Sprawling Sprout",
            cost = "{1}{G}",
            type = "Sorcery",
            text = "Search your library for a basic land card, put that card onto the battlefield tapped, then shuffle.",
        ) to abilities(
            spell(search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
        ),
    )

    // CR 701.17: the milled cards are found in the graveyard as the spell continues
    @Test
    fun wildlandsForager() = cardFaces(
        Layout.Prepare,
        card(
            name = "Wildlands Forager",
            cost = "{1}{G}{G}",
            type = "Creature — Bear Druid",
            pt = "4/4",
            text = """
                Deathtouch
                This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.Deathtouch, entersPrepared,
        ),
        card(
            name = "Tether to Life",
            cost = "{4}{G}",
            type = "Instant",
            text = "Mill seven cards. Then put a creature card from among them onto the battlefield.",
        ) to abilities(
            spell(
                mill(7, postBind = "milled"),
                Step.Choose(Choice.Objects(creature, n(1), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
                move(Objects.Bound("chosen"), Zone.Battlefield),
            ),
        ),
    )

    @Test
    fun ceridwenPoetLaureate() = cardFaces(
        Layout.Prepare,
        card(
            name = "Ceridwen, Poet Laureate",
            cost = "{1}{W}{B}",
            type = "Legendary Creature — Bird Bard",
            pt = "2/3",
            text = """
                Flying
                Whenever you cast a creature spell, Ceridwen becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
            """,
        ) to abilities(
            Keyword.Flying, triggered(youCast(creature), becomesPrepared()),
        ),
        card(
            name = "Valiant Verse",
            cost = "{1}{W/B}",
            type = "Sorcery",
            text = "Put a +1/+1 counter on target creature.",
        ) to abilities(
            spell(counters(target("creature")), targets = listOf(slot("creature", creature))),
        ),
    )

    // CR 603.10a: leaving the graveyard looks back in time
    @Test
    fun rhoskLoreEnthusiast() = cardFaces(
        Layout.Prepare,
        card(
            name = "Rhosk, Lore Enthusiast",
            cost = "{R}{W}",
            type = "Legendary Creature — Vampire Cleric",
            pt = "2/3",
            text = "Whenever one or more cards leave your graveyard, Rhosk becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            triggered(leaveYourGraveyard, becomesPrepared()),
        ),
        card(
            name = "Throw a Haymaker",
            cost = "{1}{R}{W}",
            type = "Sorcery",
            text = "Mill a card. Put two +1/+1 counters on target creature. It gains trample until end of turn.",
        ) to abilities(
            spell(mill(1), counters(target("creature"), 2), gains(target("creature"), Keyword.Trample), targets = listOf(slot("creature", creature))),
        ),
    )

    @Test
    fun taviVisitingScholar() = cardFaces(
        Layout.Prepare,
        card(
            name = "Tavi, Visiting Scholar",
            cost = "{2}{B}{G}",
            type = "Legendary Creature — Elf Druid",
            pt = "3/4",
            text = """
                Tavi enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
                Exile a creature card from your graveyard: Tavi becomes prepared. Activate only as a sorcery.
            """,
        ) to abilities(
            entersPrepared,
            activated(cost(CostPart.Exile(CostObjects.Choose(creature, n(1)), from = Zone.Graveyard)), becomesPrepared(), rules = asSorcery),
        ),
        card(
            name = "Vermin Companion",
            cost = "{B/G}",
            type = "Sorcery",
            text = "Create a 1/1 black and green Pest creature token with \"Whenever this token attacks, you gain 1 life.\"",
        ) to abilities(
            spell(create(pest)),
        ),
    )

    // CR 602.5b: "Activate only if"
    @Test
    fun pellHalfDoneProdigy() = cardFaces(
        Layout.Prepare,
        card(
            name = "Pell, Half-Done Prodigy",
            cost = "{U}{R}",
            type = "Legendary Creature — Goblin Sorcerer",
            pt = "0/4",
            text = """
                Pell enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)
                {T}: Create a Treasure token. Activate only if you've cast an instant or sorcery spell this turn.
            """,
        ) to abilities(
            entersPrepared,
            activated(cost(tap), createTreasure(), rules = anyTime.copy(condition = Condition.HappenedThisTurn(
                EventPattern.Cast(instantOrSorcery, PlayerFilter.You), atLeast = 1, measure = Measure.Events))),
        ),
        card(
            name = "Unruly Notion",
            cost = "{3}{U}{R}",
            type = "Sorcery",
            text = "Search your library for an instant or sorcery card, reveal it, put it into your hand, then shuffle.",
        ) to abilities(
            spell(search(instantOrSorcery, reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        ),
    )

    @Test
    fun orrinWatchfulSequencer() = cardFaces(
        Layout.Prepare,
        card(
            name = "Orrin, Watchful Sequencer",
            cost = "{2}{G}{U}",
            type = "Legendary Creature — Gorgon Wizard",
            pt = "4/3",
            text = "Landfall — Whenever a land you control enters, Orrin becomes prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
        ) to abilities(
            triggered(landfall, becomesPrepared(), abilityWord = "Landfall"),
        ),
        card(
            name = "Deep Gaze",
            cost = "{G}{U}",
            type = "Sorcery",
            text = "You draw a card and gain 1 life.",
        ) to abilities(
            spell(draw(1), gainLife(1)),
        ),
    )

    // CR 700.2, 603.3c: up to one mode; CR 722.3a, b
    @Test
    fun athenaeumBookwarden() = card(
        name = "Athenaeum Bookwarden",
        cost = "{4}",
        type = "Artifact Creature — Construct",
        pt = "3/4",
        text = """
            When this creature enters, choose up to one —
            • Target creature becomes prepared. (Only creatures with prepare spells can become prepared.)
            • Target creature becomes unprepared.
        """,
    ) parsesTo abilities(
        arcanum.api.Ability.Triggered(entersSelf, Instructions(modes = Modes(listOf(
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(becomesPrepared(target("creature"))))),
            Mode(Instructions(targets = listOf(slot("creature", creature)), steps = listOf(becomesUnprepared(target("creature"))))),
        ), min = 0, max = 1, chooser = Players.You))),
    )

    // CR 722.3a: only a creature with a prepare spell becomes prepared
    @Test
    fun aircoachLanding() = card(
        name = "Aircoach Landing",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {3}, {T}: Target creature becomes prepared. (Only creatures with prepare spells can become prepared.)
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(mana("{3}"), tap), becomesPrepared(target("creature")), targets = listOf(slot("creature", creature))),
    )
}
