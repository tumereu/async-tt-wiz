package arcanum.parser.sets.woe

import arcanum.api.*
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.none
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** Wilds of Eldraine chunk 11: pumps and keyword grants until end of turn, Adventure faces, can't-be-blocked effects, small tokens. */
class WoeChunk11ApplyTest {
    private val rat = creatureToken("Rat", listOf(Color.Black), 1, 1, abilities = listOf(Ability.Static(
        listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))))
    private val knight = creatureToken("Knight", listOf(Color.White), 2, 2, listOf(Keyword.Vigilance))
    private val instantOrSorcery = or(instant, sorcery)
    private val power4OrMore = and(creature, power(Comparison.AtLeast, 4), you)
    private val creatureTarget = listOf(slot("creature", creature))

    // CR 715.2 (Adventure faces), 305.2 (additional land play), 702.17a (reach)
    @Test
    fun vinewyrmClimber() = cardFaces(Layout.Adventure,
        card(
            name = "Vinewyrm Climber",
            cost = "{4}{G}",
            type = "Creature — Plant Wurm",
            text = "Reach",
            pt = "5/4",
        ) to abilities(
            Keyword.Reach,
        ),
        card(
            name = "Sow Beans",
            cost = "{1}{G}",
            type = "Sorcery — Adventure",
            text = "You may play an additional land this turn. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ExtraLandPlays(1)), Affected.Players(Players.You), Duration.EndOfTurn))),
        ),
    )

    // CR 715.2, 509.1b (can't be blocked by creatures with power 3 or greater), 611.2a
    @Test
    fun whiskerQuick() = cardFaces(Layout.Adventure,
        card(
            name = "Whisker Quick",
            cost = "{W}",
            type = "Creature — Mouse",
            text = "",
            pt = "2/1",
        ) to none,
        card(
            name = "Squeak Past",
            cost = "{W}",
            type = "Sorcery — Adventure",
            text = "Target creature you control gets +1/+1 until end of turn. It can't be blocked by creatures with power 3 or greater this turn.",
        ) to abilities(
            spell(
                pump(target("creature"), 1, 1),
                untilEndOfTurn(target("creature"), Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtLeast, 3)))),
                targets = listOf(slot("creature", creatureYouControl)),
            ),
        ),
    )

    // CR 601.2 (cast), 603.2, 115.1b ("up to one" target: min 0), 205.3m, 702.9a
    @Test
    fun dreamWreckers() = card(
        name = "Dream Wreckers",
        cost = "{3}{B}",
        type = "Creature — Faerie Warlock",
        pt = "2/2",
        text = """
            Flying
            Whenever you cast a spell during an opponent's turn, up to one target creature an opponent controls gets -1/-1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(youCast(ObjectFilter.Any), pump(target("creature"), -1, -1), targets = listOf(slot("creature", creatureOpponentControls, min = 0)),
            triggersOnlyIf = Condition.Not(Condition.YourTurn)),
    )

    // CR 701.12 (gain control until end of turn, 611.2b), 502.3 (untap), 702.10 (haste), 205.3i (Food), 602.2
    @Test
    fun tempterOrchardApple() = card(
        name = "Tempter's Orchard Apple",
        cost = "{4}",
        type = "Legendary Artifact — Food",
        text = """
            When Tempter's Orchard Apple enters, gain control of target creature until end of turn. Untap that creature. It gains haste until end of turn.
            {2}, {T}, Sacrifice Tempter's Orchard Apple: You gain 3 life.
            {2}, {T}, Sacrifice Tempter's Orchard Apple: Target opponent loses 3 life.
        """,
    ) parsesTo abilities(
        triggered(entersSelf,
            untilEndOfTurn(target("creature"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("creature")),
            gains(target("creature"), Keyword.Haste),
            targets = creatureTarget),
        activated(cost(mana("{2}"), tap, sacrificeSelf), gainLife(3)),
        activated(cost(mana("{2}"), tap, sacrificeSelf), Step.Do(Effect.LoseLife(Players.Target("opponent"), n(3))), targets = listOf(targetOpponent)),
    )

    // CR 715.2, 603.2, 120.3 (damage to any target)
    @Test
    fun boundingFamiliar() = cardFaces(Layout.Adventure,
        card(
            name = "Bounding Familiar",
            cost = "{2}{U}",
            type = "Creature — Otter Wizard",
            text = """
                Flying
                Whenever you cast an instant or sorcery spell, this creature gets +1/+1 until end of turn.
            """,
            pt = "2/2",
        ) to abilities(
            Keyword.Flying,
            triggered(youCast(instantOrSorcery), pump(Objects.Self, 1, 1)),
        ),
        card(
            name = "Steam Puff",
            cost = "{R}",
            type = "Instant — Adventure",
            text = "Steam Puff deals 1 damage to any target. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(damage(1, toAny), targets = listOf(anyTarget)),
        ),
    )

    // CR 702.10 (haste, tapped state), 509.1b (can't be blocked except by creatures with haste), 602.2
    @Test
    fun gingerloaf() = card(
        name = "Gingerloaf",
        cost = "{1}",
        type = "Artifact Creature — Food Golem",
        pt = "1/1",
        text = """
            Haste (This creature can attack and {T} as soon as it comes under your control.)
            {1}: This creature can't be blocked this turn except by creatures with haste.
            {2}, {T}, Sacrifice this creature: You gain 3 life.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        activated(cost(mana("{1}")), untilEndOfTurn(Objects.Self, Mechanic.CanBeBlockedOnlyBy(kw(Keyword.Haste.kind)))),
        activated(cost(mana("{2}"), tap, sacrificeSelf), gainLife(3)),
    )

    // CR 611.2 (pump), 603.7 (delayed trigger for this turn), 700.4 (dies), 111.4 (token with a quoted ability), 509.1b
    @Test
    fun rousingCrescendo() = card(
        name = "Rousing Crescendo",
        cost = "{2}{R}",
        type = "Instant",
        text = "Creatures you control get +2/+0 until end of turn. Whenever a nontoken creature you control dies this turn, create a 1/1 black Rat creature token with \"This token can't block.\"",
    ) parsesTo abilities(
        spell(
            pump(Objects.Each(creatureYouControl), 2, 0),
            Step.Do(Effect.CreateDelayedTrigger(
                Trigger.On(EventPattern.ZoneChange(and(not(token), creature, you), from = Zone.Battlefield, to = Zone.Graveyard)),
                Instructions(steps = listOf(create(rat))),
                Duration.EndOfTurn,
                repeats = true,  // "Whenever": every such creature this turn, not just the first
            )),
        ),
    )

    // CR 715.2, 603.6a, 611.2c (affects only creatures on the battlefield when it resolves), 111.2
    @Test
    fun rallyRecruiter() = cardFaces(Layout.Adventure,
        card(
            name = "Rally Recruiter",
            cost = "{2}{R}",
            type = "Creature — Human Knight",
            text = "When this creature enters, creatures you control get +1/+0 and gain haste until end of turn.",
            pt = "2/2",
        ) to abilities(
            triggered(entersSelf, untilEndOfTurn(Objects.Each(creatureYouControl), pt(1, 0), has(Keyword.Haste))),
        ),
        card(
            name = "Train Squires",
            cost = "{4}{W}",
            type = "Sorcery — Adventure",
            text = "Create two 2/2 white Knight creature tokens with vigilance. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(create(knight, n(2))),
        ),
    )

    // CR 715.2, 115.1 (any creature may be targeted), 613.4c
    @Test
    fun quarryDaredevil() = cardFaces(Layout.Adventure,
        card(
            name = "Quarry Daredevil",
            cost = "{2}{R}",
            type = "Creature — Dwarf Knight",
            text = "",
            pt = "4/2",
        ) to none,
        card(
            name = "Ride the Carts",
            cost = "{1}{R}",
            type = "Instant — Adventure",
            text = "Target creature gets +2/+1 until end of turn. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(pump(target("creature"), 2, 1), targets = creatureTarget),
        ),
    )

    // CR 603.2, 107.3 (X is the number of creatures you control, counted as the ability resolves), 702.9a (flying)
    @Test
    fun dawnshakerCavalry() = card(
        name = "Dawnshaker Cavalry",
        cost = "{5}{W}{W}{W}",
        type = "Creature — Spirit Knight",
        pt = "6/6",
        text = """
            Flying
            When this creature enters, creatures you control gain flying and get +X/+X until end of turn, where X is the number of creatures you control.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, Step.Do(Effect.ApplyMechanics(
            mechanics = listOf(
                Mechanic.GainAbilities(listOf(Keyword.Flying)),
                Mechanic.ModifyPowerToughness(count(creatureYouControl), count(creatureYouControl)),
            ),
            affects = Affected.Objects(Objects.Each(creatureYouControl)),
            duration = Duration.EndOfTurn,
        ))),
    )

    // CR 715.2, 613.4c (-4/-0), 702.9a
    @Test
    fun fallowAttendants() = cardFaces(Layout.Adventure,
        card(
            name = "Fallow Attendants",
            cost = "{4}{U}",
            type = "Creature — Faerie Wizard",
            text = "Flying",
            pt = "3/4",
        ) to abilities(
            Keyword.Flying,
        ),
        card(
            name = "Hasty Block",
            cost = "{1}{U}",
            type = "Instant — Adventure",
            text = "Target creature gets -4/-0 until end of turn. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(pump(target("creature"), -4, 0), targets = creatureTarget),
        ),
    )

    // CR 115.1b (up to one target), 111.2 (the token is created even with no target; "You create"), 509.1b
    @Test
    fun ratRun() = card(
        name = "Rat Run",
        cost = "{B}",
        type = "Instant",
        text = "Up to one target creature gets -1/-1 until end of turn. You create a 1/1 black Rat creature token with \"This token can't block.\"",
    ) parsesTo abilities(
        spell(pump(target("creature"), -1, -1), create(rat), targets = listOf(slot("creature", creature, min = 0))),
    )

    // CR 603.4 (attack trigger condition checked as it triggers), 506.3a, 605.1a (mana ability), 702.10 (haste, {T} costs)
    @Test
    fun rosaDaringTracker() = card(
        name = "Rosa, Daring Tracker",
        cost = "{R}{G}",
        type = "Legendary Creature — Human Scout",
        pt = "1/2",
        text = """
            Haste (This creature can attack and {T} as soon as it comes under your control.)
            Whenever Rosa attacks while you control a creature with power 4 or greater, Rosa gets +2/+2 until end of turn.
            {T}: Add {R} or {G}.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(attacksSelf, pump(Objects.Self, 2, 2), triggersOnlyIf = Condition.Exists(power4OrMore, 1)),
        addEither("R", "G"),
    )

    // CR 715.2, 603.2, 613.4c, 205.3m
    @Test
    fun shadedShepherd() = cardFaces(Layout.Adventure,
        card(
            name = "Shaded Shepherd",
            cost = "{1}{W}",
            type = "Creature — Spirit Warrior",
            text = "When this creature enters, target creature you control gets +2/+2 until end of turn.",
            pt = "2/2",
        ) to abilities(
            triggered(entersSelf, pump(target("creature"), 2, 2), targets = listOf(slot("creature", creatureYouControl))),
        ),
        card(
            name = "Cleave Gloom",
            cost = "{1}{B}",
            type = "Sorcery — Adventure",
            text = "Creatures your opponents control get -1/-1 until end of turn. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(pump(Objects.Each(creatureOpponentControls), -1, -1)),
        ),
    )

    // CR 608.2c (one spell, two instructions in order), 121.1 (draw)
    @Test
    fun sweetRush() = card(
        name = "Sweet Rush",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            Target creature gets +3/+0 until end of turn.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(pump(target("creature"), 3, 0), draw(1), targets = creatureTarget),
    )

    // CR 509.1h (becomes blocked), 603.2, 205.3m (Rat), 608.2b
    @Test
    fun tatteredRatcatcher() = card(
        name = "Tattered Ratcatcher",
        cost = "{1}{R}",
        type = "Creature — Human Peasant",
        pt = "2/2",
        text = "Whenever a Rat you control becomes blocked, it gets +2/+0 until end of turn.",
    ) parsesTo abilities(
        triggered(on(EventPattern.BecomesBlocked(and(sub("Rat"), you), ObjectFilter.Any)),
            pump(Objects.EventObject(EventRole.Object), 2, 0)),
    )

    // CR 603.4 (intervening if), 508.1 and 702.3b (defender: attack as though it didn't have it), 611.2a
    @Test
    fun territorialHornstalker() = card(
        name = "Territorial Hornstalker",
        cost = "{1}{G}",
        type = "Creature — Wolf",
        pt = "2/3",
        text = """
            Defender
            At the beginning of combat on your turn, if you control a creature with power 4 or greater, this creature gets +1/+0 until end of turn and can attack this turn as though it didn't have defender.
        """,
    ) parsesTo abilities(
        Keyword.Defender,
        triggered(beginningOfCombatOnYourTurn,
            untilEndOfTurn(Objects.Self, pt(1, 0), Mechanic.CanAttackDespiteDefender),
            interveningIf = Condition.Exists(power4OrMore, 1)),
    )

    // CR 715.2, 702.4a (double strike), 702.7a (menace)
    @Test
    fun tripleHeadedHunter() = cardFaces(Layout.Adventure,
        card(
            name = "Triple-Headed Hunter",
            cost = "{4}{R}",
            type = "Creature — Giant",
            text = "Menace (This creature can't be blocked except by two or more creatures.)",
            pt = "5/4",
        ) to abilities(
            Keyword.Menace,
        ),
        card(
            name = "Twice the Fury",
            cost = "{1}{R}",
            type = "Instant — Adventure",
            text = "Target creature gains double strike until end of turn. (Then exile this card. You may cast the creature later from exile.)",
        ) to abilities(
            spell(gains(target("creature"), Keyword.DoubleStrike), targets = creatureTarget),
        ),
    )

    // CR 509.1b (can't be blocked by creatures with power 2 or less), 611.2a, 602.2
    @Test
    fun verdantOutlier() = card(
        name = "Verdant Outlier",
        cost = "{2}{G}",
        type = "Creature — Human Knight",
        pt = "4/2",
        text = "{1}{G}: This creature can't be blocked by creatures with power 2 or less this turn.",
    ) parsesTo abilities(
        activated(cost(mana("{1}{G}")), untilEndOfTurn(Objects.Self, Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2))))),
    )
}
