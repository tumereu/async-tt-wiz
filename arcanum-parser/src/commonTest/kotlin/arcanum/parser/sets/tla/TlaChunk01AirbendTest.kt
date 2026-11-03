package arcanum.parser.sets.tla

import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.Mechanic
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
 * Avatar: The Last Airbender chunk 01: airbend (CR 701.65a: exile permanents and/or spells, their owners may cast those cards for {2}
 * while they stay exiled): one or any number of targets, "another", a creature or a spell, every other creature; an enters trigger, modes,
 * an attack trigger, the second spell each turn; and the waterbend cost of an exiling enchantment (CR 701.67a).
 */
class TlaChunk01AirbendTest {
    private val otherNonland = and(another, nonland, permanentFilter)

    // CR 701.65a
    @Test
    fun kiroTheLastWindwalker() = card(
        name = "Kiro, the Last Windwalker",
        cost = "{3}{W}",
        type = "Legendary Creature — Human Avatar Ally",
        text = """
            Flying
            When Kiro enters, airbend up to one other target nonland permanent. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
            Whenever you cast a Lesson spell, Kiro gains lifelink until end of turn.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, airbend(target("permanent")), targets = listOf(slot("permanent", otherNonland, min = 0))),
        triggered(youCast(lesson), gains(Objects.Self, Keyword.Lifelink)),
    )

    // CR 701.65a; an intervening "if" (CR 603.4)
    @Test
    fun windwalkerAscension() = card(
        name = "Windwalker Ascension",
        cost = "{1}{W}",
        type = "Enchantment",
        text = """
            When this enchantment enters, airbend up to one target creature.
            Whenever a creature you control enters, put a quest counter on this enchantment.
            At the beginning of your end step, if this enchantment has four or more quest counters on it, exile up to one target creature you control, then return it to the battlefield under its owner's control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, airbend(target("creature")), targets = listOf(slot("creature", creature, min = 0))),
        triggered(enters(creatureYouControl), counters(Objects.Self, 1, quest)),
        triggered(yourEndStep,
            move(target("creature"), Zone.Exile, postBind = "exiled"),
            move(Objects.Bound("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
            targets = listOf(slot("creature", creatureYouControl, min = 0)),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(quest, atLeast = 4))),
    )

    @Test
    fun windwalkersReversal() = card(
        name = "Windwalker's Reversal",
        cost = "{1}{W}",
        type = "Instant — Lesson",
        text = """
            Choose one —
            • Destroy target attacking creature.
            • Airbend target creature you control. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
        """,
    ) parsesTo abilities(
        modal(
            mode(destroy(target("creature")), targets = listOf(slot("creature", and(attacking, creature)))),
            mode(airbend(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
        ),
    )

    @Test
    fun windweavingLesson() = card(
        name = "Windweaving Lesson",
        cost = "{2}{W}",
        type = "Instant — Lesson",
        text = """
            Airbend target nonland permanent. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(airbend(target("permanent")), draw(1), targets = listOf(slot("permanent", nonlandPermanent))),
    )

    // CR 700.2: a modal triggered ability with two trigger events
    @Test
    fun brumbleLoyalSkyYak() = card(
        name = "Brumble, Loyal Sky Yak",
        cost = "{4}{W}{W}",
        type = "Legendary Creature — Bison Ally",
        text = """
            Flying
            Whenever Brumble enters or attacks, choose one —
            • Target creature you control gains flying until end of turn.
            • Airbend another target nonland permanent you control. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        arcanum.api.Ability.Triggered(entersOrAttacksSelf, Instructions(modes = modes(
            mode(gains(target("creature"), Keyword.Flying), targets = listOf(slot("creature", creatureYouControl))),
            mode(airbend(target("permanent")), targets = listOf(slot("permanent", and(another, nonland, permanentFilter, you)))),
        ))),
    )

    // CR 701.65a: any number of targets; a spell cast from exile (CR 601.2a)
    @Test
    fun brumbleSteadfastGuardian() = card(
        name = "Brumble, Steadfast Guardian",
        cost = "{2}{W}{W}",
        type = "Legendary Creature — Bison Ally",
        text = """
            Flash
            Flying
            When Brumble enters, airbend any number of other target nonland permanents you control. (Exile them. While each one is exiled, its owner may cast it for {2} rather than its mana cost.)
            Whenever you cast a spell from exile, create a 1/1 white Ally creature token.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf, airbend(target("permanents")),
            targets = listOf(slot("permanents", and(another, nonland, permanentFilter, you), min = 0, max = null))),
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You, from = Zone.Exile)), create(allyToken)),
    )

    // CR 701.65a; casting bans tested on the spell as it is cast (CR 601.2a)
    @Test
    fun heraldsWrath() = card(
        name = "Herald's Wrath",
        cost = "{2}{W}{W}",
        type = "Sorcery",
        text = """
            Choose up to one target creature, then airbend all other creatures. (Exile them. While each one is exiled, its owner may cast it for {2} rather than its mana cost.)
            Until your next turn, your opponents can't cast spells from anywhere other than their hands.
            Exile Herald's Wrath.
        """,
    ) parsesTo abilities(
        spell(
            airbend(Objects.Each(and(creature, not(ObjectFilter.Is(target("creature")))))),
            doing(Effect.ApplyMechanics(listOf(Mechanic.CantCast(not(ObjectFilter.WasCast(Zone.Hand)))), Affected.Players(Players.Each(PlayerFilter.Opponent)),
                Duration.UntilYourNextTurn)),
            move(Objects.Self, Zone.Exile),
            targets = listOf(slot("creature", creature, min = 0)),
        ),
    )

    @Test
    fun kiteStaff() = card(
        name = "Kite Staff",
        cost = "{2}{W}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, airbend up to one target creature. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
            Equipped creature gets +1/+1 and has flying.
            Equip {2}
        """,
    ) parsesTo abilities(
        triggered(entersSelf, airbend(target("creature")), targets = listOf(slot("creature", creature, min = 0))),
        static(pt(1, 1), has(Keyword.Flying), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 714.2b, 101.4 (starting with you), 701.27; the second spell each turn
    @Test
    fun theTaleOfWynne() = cardFaces(Layout.Transform,
        card(
            name = "The Tale of Wynne",
            cost = "{3}{W}{W}",
            type = "Enchantment — Saga",
            text = """
                (As this Saga enters and after your draw step, add a lore counter.)
                I — Starting with you, each player chooses up to one permanent with mana value 3 or greater from among permanents your opponents control. Exile those permanents.
                II — You may have target opponent draw three cards. If you do, draw three cards.
                III — Exile this Saga, then return it to the battlefield transformed under your control.
            """,
        ) to abilities(
            chapter(1,
                Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, startingWith = Players.You, steps = listOf(
                    Step.Choose(Choice.Objects(and(permanentFilter, manaValue(Comparison.AtLeast, 3), opponents), n(0), n(1)), bind = "chosen",
                        chooser = Players.Bound("player")))),
                move(Objects.Bound("chosen"), Zone.Exile)),
            chapter(2,
                Step.Pay(steps = listOf(draw(3, Players.Target("opponent"))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(draw(3))),
                targets = listOf(targetOpponent)),
            exileAndReturnTransformed,
        ),
        card(
            name = "Herald Wynne",
            cost = null,
            type = "Legendary Creature — Avatar",
            text = """
                Flying
                Whenever you cast your second spell each turn, airbend up to one other target nonland permanent. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
            """,
            pt = "4/5",
        ) to abilities(
            Keyword.Flying,
            arcanum.api.Ability.Triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You)), nthThisTurn = 2),
                Instructions(targets = listOf(slot("permanent", otherNonland, min = 0)), steps = listOf(airbend(target("permanent"))))),
        ),
    )

    // CR 701.65a: a creature or a spell; CR 701.67a, 701.27
    @Test
    fun kiroSwiftSavior() = cardFaces(Layout.Transform,
        card(
            name = "Kiro, Swift Savior",
            cost = "{1}{W}{U}",
            type = "Legendary Creature — Human Avatar Ally",
            text = """
                Flash
                Flying
                When Kiro enters, airbend up to one other target creature or spell. (Exile it. While it's exiled, its owner may cast it for {2} rather than its mana cost.)
                Waterbend {8}: Transform Kiro.
            """,
            pt = "2/3",
        ) to abilities(
            Keyword.Flash,
            Keyword.Flying,
            triggered(entersSelf, airbend(target("spell")), targets = listOf(slot("spell",
                and(another, or(and(creature, permanentFilter), spellFilter)), min = 0))),
            activated(cost(waterbend("{8}")), doing(Effect.Transform(Objects.Self))),
        ),
        card(
            name = "Kiro and Tide, Ocean's Fury",
            cost = null,
            type = "Legendary Creature — Avatar Spirit Ally",
            text = """
                Reach, trample
                Whenever Kiro and Tide attack, put a +1/+1 counter on each tapped creature you control.
            """,
            pt = "5/5",
        ) to abilities(
            Keyword.Reach,
            Keyword.Trample,
            triggered(attacksSelf, counters(Objects.Each(and(tapped, creature, you)))),
        ),
    )

    // CR 610.3; CR 701.67a, 701.21a (sacrificing it may fail)
    @Test
    fun kirosGlacier() = card(
        name = "Kiro's Glacier",
        cost = "{2}{W}",
        type = "Enchantment",
        text = """
            Flash
            When this enchantment enters, exile up to one other target nonland permanent until this enchantment leaves the battlefield.
            Waterbend {3}: Sacrifice this enchantment. If you do, scry 2. (While paying a waterbend cost, you can tap your artifacts and creatures to help. Each one pays for {1}.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        triggered(entersSelf, doing(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", otherNonland, min = 0))),
        activated(cost(waterbend("{3}")),
            Step.Pay(cost(sacrificeSelf), payer = Players.You, mandatory = true, bind = "sacrificed"),
            Step.If(Condition.Did("sacrificed"), then = listOf(scry(2)))),
    )
}
