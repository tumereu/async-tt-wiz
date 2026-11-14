package arcanum.parser.sets.dmu

import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Dominaria United chunk 03: enlist (CR 702.154): the keyword, "whenever this creature enlists a creature" (CR 702.154c), and the
 * cards that carry it with other abilities.
 */
class DmuChunk03EnlistTest {
    private val enlists = on(EventPattern.Enlists(ObjectFilter.Is(Objects.Self)))

    // CR 702.154a: the keyword; CR 702.154c: the linked trigger on a creature enlisting; CR 701.22 (scry); CR 702.12 (indestructible)

    @Test
    fun guardianOfNewBenalia() = card(
        name = "Brindle Marshal",
        cost = "{1}{W}",
        type = "Creature — Human Soldier",
        pt = "2/2",
        text = """
            Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)
            Whenever this creature enlists a creature, scry 2.
            Discard a card: This creature gains indestructible until end of turn. Tap it.
        """,
    ) parsesTo abilities(
        Keyword.Enlist,
        triggered(enlists, scry(2)),
        activated(cost(discardACard), gains(Objects.Self, Keyword.Indestructible), tapIt(Objects.Self)),
    )

    // CR 702.154a; CR 401.4 (look at the top X cards, X = its power as the ability resolves); CR 608.2g; CR 601.2 (cast without paying)

    @Test
    fun keldonFlamesage() = card(
        name = "Torchwave Sage",
        cost = "{2}{R}",
        type = "Creature — Human Shaman",
        pt = "2/3",
        text = """
            Enlist
            Whenever this creature attacks, look at the top X cards of your library, where X is this creature's power. You may exile an instant or sorcery card with mana value X or less from among them. Put the rest on the bottom of your library in a random order. You may cast the exiled card without paying its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Enlist,
        triggered(attacksSelf,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Of(NumericProperty.Power, Objects.Self, Combine.Single), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(and(or(instant, sorcery), ObjectFilter.ManaValue(Comparison.AtMost, Value.Of(NumericProperty.Power, Objects.Self, Combine.Single))), n(0), n(1), from = Objects.Bound("seen")),
                bind = "exiled", chooser = Players.You),
            doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Exile), postBind = "exiledCard")),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("exiled")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true))),
            Step.May(listOf(doing(Effect.Cast(Objects.Bound("exiledCard"), PlayKind.Cast, withoutPaying = true, caster = Players.You))), Players.You)),
    )

    @Test
    fun argivianCavalier() = card(
        name = "Ashen Cavalier",
        cost = "{2}{W}",
        type = "Creature — Orc Knight",
        pt = "2/2",
        text = """
            Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)
            When this creature enters, create a 1/1 white Soldier creature token.
        """,
    ) parsesTo abilities(
        Keyword.Enlist,
        triggered(entersSelf, create(soldier)),
    )

    // CR 603.10a (dies: its last-known power, CR 608.2h)

    @Test
    fun balduvianBerserker() = card(
        name = "Tarnished Pikeman",
        cost = "{2}{R}",
        type = "Creature — Kor Berserker",
        pt = "1/3",
        text = """
            Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)
            When this creature dies, it deals damage equal to its power to any target.
        """,
    ) parsesTo abilities(
        Keyword.Enlist,
        triggered(diesSelf, damage(power(Objects.Self), toAny), targets = listOf(anyTarget)),
    )

    @Test
    fun barkweaveCrusher() = card(
        name = "Jadewood Crusher",
        cost = "{3}{G}",
        type = "Creature — Elemental Warrior",
        pt = "2/5",
        text = """
            Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)
        """,
    ) parsesTo abilities(
        Keyword.Enlist,
    )

    @Test
    fun benalishFaithbonder() = card(
        name = "Gilded Bannerman",
        cost = "{1}{W}",
        type = "Creature — Human Cleric",
        pt = "1/3",
        text = """
            Vigilance
            Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Keyword.Enlist,
    )

    @Test
    fun coalitionSkyknight() = card(
        name = "Dawnshield Hawk",
        cost = "{3}{W}",
        type = "Creature — Human Knight",
        pt = "2/2",
        text = """
            Flying
            Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Enlist,
    )

    @Test
    fun coalitionWarbrute() = card(
        name = "Rustjaw Ape",
        cost = "{3}{R}",
        type = "Creature — Minotaur Berserker",
        pt = "3/4",
        text = """
            Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)
            Trample
        """,
    ) parsesTo abilities(
        Keyword.Enlist,
        Keyword.Trample,
    )

    // CR 702.21a (ward)

    @Test
    fun hexbaneTortoise() = card(
        name = "Quillward Tortoise",
        cost = "{2}{G}",
        type = "Creature — Turtle",
        pt = "3/2",
        text = """
            Ward {2} (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player pays {2}.)
            Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)
        """,
    ) parsesTo abilities(
        Keyword.Ward(manaCost("{2}")),
        Keyword.Enlist,
    )

    @Test
    fun linebreakerBaloth() = card(
        name = "Plainsrunner Colossus",
        cost = "{3}{G}{G}",
        type = "Creature — Beast",
        pt = "4/5",
        text = """
            Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)
            This creature can't be blocked by creatures with power 2 or less.
        """,
    ) parsesTo abilities(
        Keyword.Enlist,
        static(Mechanic.CantBeBlockedBy(and(creature, power(Comparison.AtMost, 2)))),
    )

    @Test
    fun yavimayaSteelcrusher() = card(
        name = "Fenwood Husk",
        cost = "{1}{R}",
        type = "Creature — Ape Warrior",
        pt = "2/2",
        text = """
            Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)
            {1}, Sacrifice this creature: Destroy target artifact.
        """,
    ) parsesTo abilities(
        Keyword.Enlist,
        activated(cost(mana("{1}"), sacrificeSelf), destroy(target("artifact")), targets = listOf(slot("artifact", artifact))),
    )
}
