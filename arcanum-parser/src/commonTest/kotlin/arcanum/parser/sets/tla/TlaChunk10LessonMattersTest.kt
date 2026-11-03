package arcanum.parser.sets.tla

import arcanum.api.Choice
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 10: what Lesson cards in your graveyard give (a static bonus, a cost reduction, flash, attacking despite
 * defender, attacking or blocking at all, intervening "if"s, a count), searching and looking for Lessons, Sagas and Shrines, and mana spent
 * only on Lesson or Shrine spells (CR 106.6).
 */
class TlaChunk10LessonMattersTest {
    private fun seenToHand(count: Int, kind: ObjectFilter) = listOf(
        doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(count), fromTop = true), Players.You, bind = "seen")),
        Step.Choose(Choice.Objects(kind, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
        doing(Effect.Reveal(Objects.Bound("chosen"))),
        move(Objects.Bound("chosen"), Zone.Hand),
        doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))
    private val becomesTapped = on(EventPattern.Tap(ObjectFilter.Is(Objects.Self)))

    @Test
    fun fledglingGlider() = card(
        name = "Fledgling Glider",
        cost = "{1}{U}",
        type = "Creature — Human Pilot Ally",
        text = """
            Flying
            This creature gets +1/+1 as long as there's a Lesson card in your graveyard.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        Keyword.Flying,
        static(pt(1, 1), condition = lessonInGraveyard),
    )

    // CR 601.2f: a conditional reduction for noncreature spells
    @Test
    fun nanaUlla() = card(
        name = "Nana-Ulla",
        cost = "{U}",
        type = "Legendary Creature — Human Peasant Ally",
        text = """
            Whenever Nana-Ulla becomes tapped, draw a card, then discard a card.
            Noncreature spells you cast cost {1} less to cast as long as there are three or more Lesson cards in your graveyard.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        triggered(becomesTapped, draw(1), discard(1)),
        static(Mechanic.CostChange(and(noncreature, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1))), condition = threeLessons),
    )

    @Test
    fun tidemasterOskar() = card(
        name = "Tidemaster Oskar",
        cost = "{1}{U}",
        type = "Legendary Creature — Human Advisor Ally",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Whenever Tidemaster Oskar becomes tapped, target player mills X cards, where X is the number of Lesson cards in your graveyard. (They put the top X cards of their library into their graveyard.)
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Prowess,
        triggered(becomesTapped, doing(Effect.Mill(Players.Target("player"), lessonsInGraveyard())), targets = listOf(targetPlayer)),
    )

    // CR 702.8a (as though it had flash, while a condition holds), 601.2f
    @Test
    fun wyrmOfTheStrait() = card(
        name = "Wyrm of the Strait",
        cost = "{5}{U}{U}",
        type = "Creature — Serpent",
        text = """
            If there are three or more Lesson cards in your graveyard, you may cast this spell as though it had flash.
            This spell costs {1} less to cast for each noncreature, nonland card in your graveyard.
        """,
        pt = "6/5",
    ) parsesTo abilities(
        static(Mechanic.InstantSpeed, condition = threeLessons),
        static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(noncreature, nonland))))),
    )

    // CR 613.1f: lifelink for creatures and spells
    @Test
    fun renAndReiTwinMentors() = card(
        name = "Ren and Rei, Twin Mentors",
        cost = "{4}{B}",
        type = "Legendary Creature — Human Advisor",
        text = """
            When Ren and Rei enter, search your library for a Lesson or Noble card, reveal it, put it into your hand, then shuffle.
            Noble creatures you control and Lesson spells you control have lifelink.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, search(or(lesson, sub("Noble")), reveal = true), move(Objects.Bound("found"), Zone.Hand), shuffle),
        static(has(Keyword.Lifelink), affects = Objects.Each(or(and(sub("Noble"), creature, you), and(lesson, spellFilter, you)))),
    )

    // CR 714.2b
    @Test
    fun petalsFromTheBough() = card(
        name = "Petals from the Bough",
        cost = "{1}{G}",
        type = "Enchantment — Saga",
        text = """
            (As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)
            I — Mill three cards, then create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            II — Put a +1/+1 counter on each of up to two target creatures you control.
            III — Draw a card if there's a creature or Lesson card in your graveyard.
        """,
    ) parsesTo abilities(
        chapter(1, mill(3), createFood),
        chapter(2, counters(target("creatures")), targets = listOf(slot("creatures", creatureYouControl, min = 0, max = 2))),
        chapter(3, Step.If(Condition.Exists(inYourGraveyard(or(creature, lesson)), atLeast = 1), then = listOf(draw(1)))),
    )

    // CR 701.17 (mill), a choice among the milled card
    @Test
    fun trainingScarecrow() = card(
        name = "Training Scarecrow",
        cost = "{1}{G}",
        type = "Artifact Creature — Scarecrow",
        text = """
            Defender
            {T}: Mill a card. You may put a land card milled this way into your hand. You gain 2 life if a Lesson card is milled this way. (To mill a card, put the top card of your library into your graveyard.)
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(tap), mill(1, postBind = "milled"),
            Step.Choose(Choice.Objects(land, n(0), n(1), from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Hand),
            Step.If(Condition.ObjectMatches(Objects.Bound("milled"), lesson), then = listOf(gainLife(2)))),
    )

    // CR 603.4
    @Test
    fun rampartSentries() = card(
        name = "Rampart Sentries",
        cost = "{2}{G}",
        type = "Creature — Human Soldier Ally",
        text = """
            Reach, deathtouch
            When this creature dies, if there's a Lesson card in your graveyard, you gain 2 life.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Deathtouch,
        triggered(diesSelf, gainLife(2), interveningIf = lessonInGraveyard),
    )

    // CR 604.3, 603.4
    @Test
    fun damselflyCloud() = card(
        name = "Damselfly Cloud",
        cost = "{1}{U}{R}",
        type = "Creature — Dragon Insect",
        text = """
            Flying, ward {1} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {1}.)
            This creature's power is equal to the number of noncreature, nonland cards in your graveyard.
            When this creature dies, if there's a Lesson card in your graveyard, draw a card.
        """,
        pt = "*/3",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Ward(manaCost("{1}")),
        static(Mechanic.SetPowerToughness(count(inYourGraveyard(noncreature, nonland)), null), characteristicDefining = true),
        triggered(diesSelf, draw(1), interveningIf = lessonInGraveyard),
    )

    @Test
    fun sageOmmar() = card(
        name = "Sage Ommar",
        cost = "{2}{G/U}{G/U}",
        type = "Legendary Creature — Human Monk Ally",
        text = """
            When Sage Ommar enters, look at the top five cards of your library. You may reveal a Lesson, Saga, or Shrine card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
            Whenever you cast a Lesson, Saga, or Shrine spell, put a +1/+1 counter on another target creature you control.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        triggered(entersSelf, *seenToHand(5, or(lesson, sub("Saga"), shrine)).toTypedArray()),
        triggered(youCast(or(lesson, sub("Saga"), shrine)), counters(target("creature")), targets = listOf(slot("creature", and(another, creature, you)))),
    )

    // CR 106.6: mana spendable only on Lesson spells
    @Test
    fun reclusiveHerbwife() = card(
        name = "Reclusive Herbwife",
        cost = "{G}{U}",
        type = "Creature — Human Druid Ally",
        text = """
            {T}: Add one mana of any color.
            {T}: Add two mana in any combination of colors. Spend this mana only to cast Lesson spells.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        addAnyColor,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(2), combination = true), restriction = and(spellFilter, lesson)), Players.You))),
    )

    // CR 508.1c, 509.1c (restrictions while a condition doesn't hold)
    @Test
    fun theElderShellbeast() = card(
        name = "The Elder Shellbeast",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Elder Cat Turtle",
        text = """
            Reach, vigilance
            When The Elder Shellbeast enters, you gain 3 life.
            The Elder Shellbeast can't attack or block unless there are three or more Lesson cards in your graveyard.
            {T}: Add one mana of any color.
        """,
        pt = "3/6",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Vigilance,
        triggered(entersSelf, gainLife(3)),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(ObjectFilter.Any), condition = Condition.Not(threeLessons)),
        addAnyColor,
    )

    // CR 702.3b: attacking as though it didn't have defender
    @Test
    fun beaverBear() = card(
        name = "Beaver-Bear",
        cost = "{1}{G/U}",
        type = "Creature — Platypus Bear",
        text = """
            Defender
            When this creature enters, mill two cards. (Put the top two cards of your library into your graveyard.)
            As long as there is a Lesson card in your graveyard, this creature can attack as though it didn't have defender.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Defender,
        triggered(entersSelf, mill(2)),
        static(Mechanic.CanAttackDespiteDefender, condition = lessonInGraveyard),
    )

    // CR 106.6
    @Test
    fun paleBlossomRefuge() = card(
        name = "Pale Blossom Refuge",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {T}: Add one mana of any color. Spend this mana only to cast a Lesson or Shrine spell.
            {1}, {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        addColorless,
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)), restriction = and(spellFilter, or(lesson, shrine))), Players.You))),
        activated(cost(mana("{1}"), tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
    )

    @Test
    fun swordwrightHalloran() = card(
        name = "Swordwright Halloran",
        cost = "{4}{W}",
        type = "Legendary Creature — Human Warrior Ally",
        text = """
            First strike
            Whenever Swordwright Halloran attacks, look at the top four cards of your library. You may reveal an Ally, Equipment, or Lesson card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.FirstStrike,
        triggered(attacksSelf, *seenToHand(4, or(ally, sub("Equipment"), lesson)).toTypedArray()),
    )
}
