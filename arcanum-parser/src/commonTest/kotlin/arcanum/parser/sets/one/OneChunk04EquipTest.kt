package arcanum.parser.sets.one

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Destination
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.PlayKind
import arcanum.api.RecipientFilter
import arcanum.api.Zone
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Value
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Phyrexia: All Will Be One chunk 04: Equipment. For Mirrodin! (CR 702.163), "becomes attached" triggers (CR 301.5c), equip cost reductions
 * for other Equipment (CR 601.2f, 702.6) and "creatures you control that are equipped" (CR 301.5).
 */
class OneChunk04EquipTest {
    private val equippedCreatures = Objects.Each(and(creature, you, ObjectFilter.Attached(attachments = equipment)))

    private fun forMirrodinCard(name: String, cost: String, text: String, vararg abilities: Ability) = card(
        name = name,
        cost = cost,
        type = "Artifact — Equipment",
        text = "For Mirrodin! (When this Equipment enters, create a 2/2 red Rebel creature token, then attach this to it.)\n$text",
    ) parsesTo abilities(Keyword.ForMirrodin, *abilities)

    // CR 301.5c, 611.2a, 707.9: the copy lasts as long as this Equipment stays attached to that creature; "you may"
    @Test
    fun bladeOfSharedSouls() = forMirrodinCard(
        name = "Blade of Mingled Souls",
        cost = "{2}{U}",
        text = """
            Whenever this Equipment becomes attached to a creature, for as long as this Equipment remains attached to it, you may have that creature become a copy of another target creature you control.
            Equip {2}
        """,
        triggered(
            on(EventPattern.Attach(ObjectFilter.Is(Objects.Self), creature)),
            Step.Bind(equipped, "equipped"),
            Step.May(listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(target("creature"))), Affected.Objects(Objects.Bound("equipped")),
                Duration.AsLongAs(Condition.ObjectMatches(Objects.Self, ObjectFilter.Attached(to = Objects.Bound("equipped"))))))), player = Players.You),
            targets = listOf(slot("creature", and(ObjectFilter.Not(ObjectFilter.Is(equipped)), creature, you))),
        ),
        equip("{2}"),
    )

    // CR 601.2f, 702.6: only abilities of other Equipment, not this one's own
    @Test
    fun bladeholdWarWhip() = forMirrodinCard(
        name = "Bladeheld War-Whip",
        cost = "{1}{R}{W}",
        text = """
            Equip abilities you activate of other Equipment cost {1} less to activate.
            Equipped creature has double strike.
            Equip {3}{R}{W}
        """,
        static(Mechanic.CostChange(and(ObjectFilter.AbilityOf(keyword = Keyword.Equip.Kind, source = ObjectFilter.Not(ObjectFilter.Is(Objects.Self))), you),
            CostChange.Less(ManaCost.parse("{1}")!!, Value.Fixed(1)))),
        static(has(Keyword.DoubleStrike), affects = equipped),
        equip("{3}{R}{W}"),
    )

    // CR 301.5: equipped creatures you control, whichever Equipment they carry
    @Test
    fun hexgoldHoverwings() = forMirrodinCard(
        name = "Hexsilver Hoverwings",
        cost = "{3}{W}",
        text = """
            Equipped creature has flying.
            Creatures you control that are equipped get +1/+0.
            Equip {2}{W}
        """,
        static(has(Keyword.Flying), affects = equipped),
        static(pt(1, 0), affects = equippedCreatures),
        equip("{2}{W}"),
    )

    @Test
    fun barbedBatterfist() = forMirrodinCard(
        name = "Barbed Battergrip",
        cost = "{1}{R}",
        text = """
            Equipped creature gets +1/-1.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
        static(pt(1, -1), affects = equipped),
        equip("{1}"),
    )

    @Test
    fun dragonwingGlider() = forMirrodinCard(
        name = "Dragonwing Soarer",
        cost = "{3}{R}{R}",
        text = """
            Equipped creature gets +2/+2 and has flying and haste.
            Equip {3}{R}{R}
        """,
        static(pt(2, 2), has(Keyword.Flying, Keyword.Haste), affects = equipped),
        equip("{3}{R}{R}"),
    )

    @Test
    fun goldwardensHelm() = forMirrodinCard(
        name = "Goldwarden's Coif",
        cost = "{2}{W}",
        text = """
            Equipped creature gets +0/+1.
            Equip {1}{W} ({1}{W}: Attach to target creature you control. Equip only as a sorcery.)
        """,
        static(pt(0, 1), affects = equipped),
        equip("{1}{W}"),
    )

    // CR 611.3: a static ability that only applies during your turn
    @Test
    fun hexgoldHalberd() = forMirrodinCard(
        name = "Hexsilver Halberd",
        cost = "{1}{R}",
        text = """
            During your turn, equipped creature has first strike and trample.
            Equip {2}{R}
        """,
        static(has(Keyword.FirstStrike, Keyword.Trample), affects = equipped, condition = Condition.YourTurn),
        equip("{2}{R}"),
    )

    // CR 702.164: toxic is granted like any keyword; the token is a colorless Phyrexian Mite
    @Test
    fun infestedFleshcutter() = card(
        name = "Infested Fleshblade",
        cost = "{1}{W}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+0.
            Whenever equipped creature attacks, create a 1/1 colorless Phyrexian Mite artifact creature token with toxic 1 and "This token can't block." (Players dealt combat damage by it also get a poison counter.)
            Equip {2}{W}
        """,
    ) parsesTo abilities(
        static(pt(2, 0), affects = equipped),
        triggered(on(EventPattern.Attack(ObjectFilter.Is(equipped))), create(creatureToken("Phyrexian Mite", emptyList(), 1, 1,
            abilities = listOf(Keyword.Toxic(1), Ability.Static(listOf(Mechanic.CantBlock(ObjectFilter.Any)), affects = Affected.Objects(Objects.Self))),
            types = listOf(CardType.Artifact, CardType.Creature)), bind = "token")),
        equip("{2}{W}"),
    )

    @Test
    fun mirranBardiche() = forMirrodinCard(
        name = "Mirran Bardisse",
        cost = "{4}{W}",
        text = """
            Equipped creature gets +2/+1 and has vigilance.
            Equip {3}{W} ({3}{W}: Attach to target creature you control. Equip only as a sorcery.)
        """,
        static(pt(2, 1), has(Keyword.Vigilance), affects = equipped),
        equip("{3}{W}"),
    )

    // CR 702.164a
    @Test
    fun prostheticInjector() = card(
        name = "Prosthetic Syringe",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +0/+2 and has toxic 1. (Players dealt combat damage by equipped creature also get a poison counter.)
            Equip {1}
        """,
    ) parsesTo abilities(
        static(pt(0, 2), has(Keyword.Toxic(1)), affects = equipped),
        equip("{1}"),
    )

    // CR 702.16b: protection from red and from green; CR 305.2: an additional land drop
    @Test
    fun swordOfForgeAndFrontier() = card(
        name = "Sword of Forge and Field",
        cost = "{3}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +2/+2 and has protection from red and from green.
            Whenever equipped creature deals combat damage to a player, exile the top two cards of your library. You may play those cards this turn. You may play an additional land this turn.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(2, 2), has(Keyword.Protection(ObjectFilter.OfColor(Color.Red)), Keyword.Protection(ObjectFilter.OfColor(Color.Green))), affects = equipped),
        triggered(
            on(EventPattern.Damage(source = ObjectFilter.Is(equipped), to = RecipientFilter(players = PlayerFilter.Any), combat = true)),
            doing(Effect.Move(objects = Objects.LibraryCards(of = Players.You, count = n(2), fromTop = true), to = Destination(Zone.Exile), postBind = "exiled")),
            untilEndOfTurn(Objects.Bound("exiled"), Mechanic.MayPlay(from = Zone.Exile, how = PlayKind.Play, holder = PlayerFilter.You)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.ExtraLandPlays(1)), Affected.Players(Players.You), Duration.EndOfTurn)),
        ),
        equip("{2}"),
    )

    @Test
    fun sylvokBattleChair() = forMirrodinCard(
        name = "Sylvok Battle-Seat",
        cost = "{4}{G}{G}",
        text = """
            Equipped creature gets +4/+4 and has trample.
            Equip {5}{G}{G}
        """,
        static(pt(4, 4), has(Keyword.Trample), affects = equipped),
        equip("{5}{G}{G}"),
    )

    @Test
    fun vulshokSplitter() = forMirrodinCard(
        name = "Vulshok Cleaver",
        cost = "{3}{R}",
        text = """
            Equipped creature gets +2/+0.
            Equip {2}{R} ({2}{R}: Attach to target creature you control. Equip only as a sorcery.)
        """,
        static(pt(2, 0), affects = equipped),
        equip("{2}{R}"),
    )
}
