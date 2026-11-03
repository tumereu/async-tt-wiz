package arcanum.parser.sets.tmt

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.AttackTarget
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modes
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 04: alliance ("Whenever another creature you control enters", an ability word, CR 207.2c): pumps,
 * counters, damage, a mode not chosen this turn (CR 700.2), exiling the top card to play when attacking (one card, CR 607.2a), a choice
 * of keyword; a name that is plural ("The Hyperdrive Kids get", CR 201.5) and a creature blinked into the attack (CR 508.4).
 */
class TmtChunk04AllianceTest {
    @Test
    fun easterlyGaleAvatar() = card(
        name = "Easterly Gale Avatar",
        cost = "{3}{W}",
        type = "Creature — Bird Spirit Avatar",
        text = """
            Flying, vigilance
            Alliance — Whenever another creature you control enters, this creature gets +1/+0 until end of turn.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Vigilance,
        alliance(pump(Objects.Self, 1, 0)),
    )

    // CR 700.2: a mode chosen once this turn can't be chosen again this turn
    @Test
    fun pollywogPondOrphan() = card(
        name = "Pollywog, Pond Orphan",
        cost = "{1}{W}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Alliance — Whenever another creature you control enters, choose one that hasn't been chosen this turn.
            • Put a +1/+1 counter on Pollywog.
            • Create a Food token. (It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            • Scry 1.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Ability.Triggered(anotherCreatureYouControlEnters, Instructions(modes = Modes(listOf(mode(counters(Objects.Self)), mode(createFood()), mode(scry(1))),
            min = 1, max = 1, onlyUnchosenThisTurn = true, chooser = Players.You)), abilityWord = "Alliance"),
    )

    @Test
    fun motleyMutantBrigade() = card(
        name = "Motley Mutant Brigade",
        cost = "{2}{W}{W}",
        type = "Creature — Mutant Rebel",
        text = """
            When this creature enters, create a 2/2 red Mutant creature token.
            Alliance — Whenever another creature you control enters, put a +1/+1 counter on target creature you control.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        triggered(entersSelf, create(mutantToken)),
        alliance(counters(target("creature")), targets = listOf(slot("creature", creatureYouControl))),
    )

    // CR 607.2a: the cards exiled with it; one of them may be played this turn (a permission used once)
    @Test
    fun snarlBiggestAttitude() = card(
        name = "Snarl, Biggest Attitude",
        cost = "{3}{R}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Alliance — Whenever another creature you control enters, you may exile the top card of your library.
            Whenever Snarl attacks, until end of turn, you may play a card exiled with Snarl.
        """,
        pt = "4/3",
    ) parsesTo abilities(
        Keyword.Menace,
        alliance(Step.May(listOf(
            move(Objects.LibraryCards(Players.You, n(1), fromTop = true), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled")))), player = Players.You)),
        triggered(attacksSelf, doing(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You, perTurn = 1)),
            Affected.Objects(Objects.Where(Objects.Remembered("exiled"), inZone(Zone.Exile))), Duration.EndOfTurn))),
    )

    @Test
    fun gritToughShell() = card(
        name = "Grit, Tough Shell",
        cost = "{1}{R}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = "Alliance — Whenever another creature you control enters, Grit deals 1 damage to target opponent.",
        pt = "1/3",
    ) parsesTo abilities(
        alliance(damage(1, Recipients(players = Players.Target("opponent"))), targets = listOf(targetOpponent)),
    )

    @Test
    fun gnashReptileRampager() = card(
        name = "Gnash, Reptile Rampager",
        cost = "{3}{R}{R}",
        type = "Legendary Creature — Mutant Berserker Turtle",
        text = """
            Alliance — Whenever another creature you control enters, Gnash deals 2 damage to each opponent.
            Whenever Gnash attacks, create a 2/2 red Mutant creature token.
        """,
        pt = "7/5",
    ) parsesTo abilities(
        alliance(damage(2, eachOpponent)),
        triggered(attacksSelf, create(mutantToken)),
    )

    @Test
    fun flittermouseBelfryBat() = card(
        name = "Flittermouse, Belfry Bat",
        cost = "{1}{R}",
        type = "Legendary Creature — Bat Mutant",
        text = """
            Alliance — Whenever another creature you control enters, Flittermouse gains your choice of flying, menace, or haste until end of turn.
            Whenever Flittermouse attacks, each other attacking creature gets +1/+0 until end of turn.
        """,
        pt = "1/2",
    ) parsesTo abilities(
        alliance(
            Step.Choose(arcanum.api.Choice.Option(listOf("flying", "menace", "haste")), bind = "keyword", chooser = Players.You),
            Step.If(Condition.Chose("keyword", "flying"), then = listOf(gains(Objects.Self, Keyword.Flying)), otherwise = listOf(
                Step.If(Condition.Chose("keyword", "menace"), then = listOf(gains(Objects.Self, Keyword.Menace)),
                    otherwise = listOf(gains(Objects.Self, Keyword.Haste)))))),
        triggered(attacksSelf, pump(Objects.Each(and(another, attacking, creature)), 1, 0)),
    )

    @Test
    fun vanguardStrikeTeam() = card(
        name = "Vanguard Strike Team",
        cost = "{1}{R/W}{R/W}",
        type = "Creature — Human Soldier",
        text = "Alliance — Whenever another creature you control enters, put a +1/+1 counter on this creature.",
        pt = "2/1",
    ) parsesTo abilities(
        alliance(counters(Objects.Self)),
    )

    // CR 508.4: it enters attacking a player or planeswalker its controller chooses
    @Test
    fun theHyperdriveKids() = card(
        name = "The Hyperdrive Kids",
        cost = "{2}{R}{W}",
        type = "Legendary Creature — Elf Rebel",
        text = """
            Flying
            Alliance — Whenever another creature you control enters, The Hyperdrive Kids get +1/+0 until end of turn.
            Whenever The Hyperdrive Kids attack, exile up to one target creature you own, then return it to the battlefield under your control tapped and attacking.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Flying,
        alliance(pump(Objects.Self, 1, 0)),
        triggered(attacksSelf,
            move(target("creature"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield, tapped = true, controller = Players.You, attacking = AttackTarget.Chosen))),
            targets = listOf(slot("creature", and(creature, ObjectFilter.OwnedBy(PlayerFilter.You)), min = 0))),
    )
}
