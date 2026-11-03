package arcanum.parser.sets.ecl

import arcanum.api.Ability
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Lorwyn Eclipsed chunk 03: vivid, an ability word (CR 207.2c) on abilities that count "the number of colors among permanents you
 * control" (CR 105.4: each of the five colours some permanent you control has): token counts, cost reductions (CR 601.2f), cards drawn,
 * life, damage, a characteristic-defining power (CR 604.3), targets "up to X", searched cards, mana of each of those colours (CR 106.1)
 * and revealing until X permanent or nonland cards are found (CR 701.20).
 */
class EclChunk03VividTest {
    private val x = colorsAmongPermanents

    @Test
    fun hamletwarden() = card(
        name = "Hamletwarden",
        cost = "{6}{W}",
        type = "Creature — Elemental",
        text = """
            Vivid — When this creature enters, create X 1/1 green and white Kithkin creature tokens, where X is the number of colors among permanents you control.
            Tap three untapped creatures you control: This creature gets +3/+0 and gains flying until end of turn.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        vivid(triggered(entersSelf, create(kithkin, x))),
        activated(cost(CostPart.TapObjects(CostObjects.Choose(creature, count = n(3)))), pump(Objects.Self, 3, 0, has(Keyword.Flying))),
    )

    // CR 601.2f: the reduction applies only to generic mana
    @Test
    fun hoarfrostGrip() = card(
        name = "Hoarfrost Grip",
        cost = "{6}{U}",
        type = "Instant",
        text = """
            Vivid — This spell costs {1} less to cast for each color among permanents you control.
            Tap up to two target creatures. Put a stun counter on each of them. (If a permanent with a stun counter would become untapped, remove one from it instead.)
            Draw a card.
        """,
    ) parsesTo abilities(
        vivid(costsLess("{1}", x)),
        spell(*tapAndStun(target("creatures")).toTypedArray(), draw(1), targets = listOf(slot("creatures", creature, min = 0, max = 2))),
    )

    @Test
    fun gleamdiver() = card(
        name = "Gleamdiver",
        cost = "{4}{U}{U}",
        type = "Creature — Elemental",
        text = """
            Flying
            Vivid — When this creature enters, draw cards equal to the number of colors among permanents you control.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Keyword.Flying,
        vivid(triggered(entersSelf, doing(Effect.Draw(Players.You, x)))),
    )

    @Test
    fun glimmerslink() = card(
        name = "Glimmerslink",
        cost = "{4}{B}",
        type = "Creature — Elemental",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Vivid — When this creature enters, each opponent loses X life and you gain X life, where X is the number of colors among permanents you control.
        """,
        pt = "3/5",
    ) parsesTo abilities(
        Keyword.Menace,
        vivid(triggered(entersSelf, doing(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), x)), doing(Effect.GainLife(Players.You, x)))),
    )

    @Test
    fun volatileWhiz() = card(
        name = "Volatile Whiz",
        cost = "{1}{R}",
        type = "Creature — Elemental Sorcerer",
        text = "Vivid — When this creature enters, it deals X damage to target creature an opponent controls, where X is the number of colors among permanents you control.",
        pt = "1/1",
    ) parsesTo abilities(
        vivid(triggered(entersSelf, damage(x, toObjects(target("creature"))), targets = listOf(slot("creature", creatureOpponentControls)))),
    )

    // CR 604.3: a characteristic-defining ability, in every zone
    @Test
    fun cluckblaze() = card(
        name = "Cluckblaze",
        cost = "{3}{R}",
        type = "Creature — Elemental",
        text = """
            Double strike
            Vivid — Cluckblaze's power is equal to the number of colors among permanents you control.
        """,
        pt = "*/4",
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        vivid(static(Mechanic.SetPowerToughness(x, null), characteristicDefining = true)),
    )

    // CR 701.20a: revealed until X permanent cards; the rest to the bottom at random
    @Test
    fun dawnglowRouser() = card(
        name = "Dawnglow Rouser",
        cost = "{6}{G}",
        type = "Creature — Giant Druid",
        text = """
            Trample
            Vivid — When this creature enters, reveal cards from the top of your library until you reveal X permanent cards, where X is the number of colors among permanents you control. Put any number of those permanent cards onto the battlefield, then put the rest of the revealed cards on the bottom of your library in a random order.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Trample,
        vivid(triggered(entersSelf,
            doing(Effect.RevealUntil(permanentCard, Players.You, x, reveal = true, foundBind = "found", restBind = "rest")),
            Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Any, n(0), null, from = Objects.Bound("found")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield),
            doing(Effect.Move(Objects.Except(Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("rest"))), Objects.Bound("chosen")),
                arcanum.api.Destination(Zone.Library, libraryPosition = -1, randomOrder = true))))),
    )

    // CR 106.1: one mana of each colour among them
    @Test
    fun blossomKeeper() = card(
        name = "Blossom Keeper",
        cost = "{1}{G}",
        type = "Creature — Elf Druid",
        text = "Vivid — {T}: For each color among permanents you control, add one mana of that color.",
        pt = "1/1",
    ) parsesTo abilities(
        vivid(activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.EachColorAmong(Objects.Each(and(permanentFilter, you)))), Players.You)))),
    )

    @Test
    fun glowshellSlug() = card(
        name = "Glowshell Slug",
        cost = "{3}{G}",
        type = "Creature — Elemental",
        text = """
            Deathtouch
            Vivid — When this creature enters, you gain life equal to the number of colors among permanents you control.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Deathtouch,
        vivid(triggered(entersSelf, doing(Effect.GainLife(Players.You, x)))),
    )

    // CR 601.2c: the number of targets is chosen as it is put on the Stack
    @Test
    fun prismsmasher() = card(
        name = "Prismsmasher",
        cost = "{4}{G}{G}",
        type = "Creature — Elemental",
        text = """
            Trample
            Vivid — When this creature enters, up to X target creatures you control get +X/+X until end of turn, where X is the number of colors among permanents you control.
        """,
        pt = "6/6",
    ) parsesTo abilities(
        Keyword.Trample,
        vivid(triggered(entersSelf, pumpBy(target("creatures"), x, x),
            targets = listOf(TargetSlot("creatures", TargetKind.Objects(creatureYouControl), min = n(0), max = x)))),
    )

    // CR 305.2: an additional land each turn
    @Test
    fun spectralRiptides() = card(
        name = "Spectral Riptides",
        cost = "{3}{G}",
        type = "Enchantment",
        text = """
            Vivid — When this enchantment enters, search your library for up to X basic land cards, where X is the number of colors among permanents you control. Reveal those cards, put them into your hand, then shuffle.
            You may play an additional land on each of your turns.
        """,
    ) parsesTo abilities(
        vivid(triggered(entersSelf,
            doing(Effect.Search(Players.You, basicLand(), max = x, reveal = true, zones = listOf(Zone.Library), bind = "found", searcher = Players.You)),
            move(Objects.Bound("found"), Zone.Hand),
            shuffle)),
        playerStatic(Mechanic.ExtraLandPlays(1)),
    )

    @Test
    fun tanglevineBasher() = card(
        name = "Tanglevine Basher",
        cost = "{6}{G}",
        type = "Creature — Giant Berserker",
        text = """
            Vivid — This spell costs {1} less to cast for each color among permanents you control.
            Reach, trample
        """,
        pt = "6/5",
    ) parsesTo abilities(
        vivid(costsLess("{1}", x)),
        Keyword.Reach,
        Keyword.Trample,
    )

    @Test
    fun glimmerWean() = card(
        name = "Glimmer Wean",
        cost = "{2}{G/U}{G/U}{G/U}",
        type = "Creature — Ouphe",
        text = "Vivid — At the beginning of combat on your turn, another target creature you control gets +X/+X until end of turn, where X is the number of colors among permanents you control.",
        pt = "1/4",
    ) parsesTo abilities(
        vivid(triggered(beginningOfCombatOnYourTurn, pumpBy(target("creature"), x, x), targets = listOf(slot("creature", and(another, creature, you))))),
    )

    // CR 701.20a; at most one exiled card for each of those colours, each card standing for one colour it has
    @Test
    fun pellInventiveFreshling() = card(
        name = "Pell, Inventive Freshling",
        cost = "{2}{U/R}{U/R}",
        type = "Legendary Creature — Goblin Sorcerer",
        text = "Vivid — At the beginning of your first main phase, reveal cards from the top of your library until you reveal X nonland cards, where X is the number of colors among permanents you control. For each of those colors, you may exile a card of that color from among the revealed cards. Then shuffle. You may cast the exiled cards this turn.",
        pt = "2/4",
    ) parsesTo abilities(
        vivid(triggered(stepBegins(TurnStep.PrecombatMain, PlayerFilter.You),
            doing(Effect.RevealUntil(nonland, Players.You, x, reveal = true, foundBind = "found", restBind = "rest")),
            Step.Choose(arcanum.api.Choice.Objects(ObjectFilter.Shares(SharedQuality.Color, Objects.Each(and(permanentFilter, you))), n(0), null,
                from = Objects.Union(listOf(Objects.Bound("found"), Objects.Bound("rest"))), onePer = SharedQuality.Color), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Exile, postBind = "exiled"),
            shuffle,
            doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, holder = PlayerFilter.You)),
                arcanum.api.Affected.Objects(Objects.Bound("exiled")), Duration.EndOfTurn)))),
    )
}
