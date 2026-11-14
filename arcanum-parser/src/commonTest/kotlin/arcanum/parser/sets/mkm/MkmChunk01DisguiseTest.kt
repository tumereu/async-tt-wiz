package arcanum.parser.sets.mkm

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.api.EventRole
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/** MKM chunk 01: disguise (CR 702.168) and what happens as and when a permanent is turned face up (CR 614.1e, 708.8). */
class MkmChunk01DisguiseTest {
    private val turnedFaceUpX = Value.EventX

    // CR 702.168e: X in "when this creature is turned face up" is the X of its disguise cost; CR 607.2a: the leaves ability returns
    // the cards the first exiled; CR 115.1: targets on the battlefield and in graveyards, chosen together
    @Test
    fun shroudedHaloAvenger() = card(
        name = "Shrouded Halo Avenger",
        cost = "{2}{W}{W}",
        type = "Creature — Angel",
        pt = "4/2",
        text = """
            Flying, lifelink, ward {2}
            Disguise {X}{3}{W}
            When this creature is turned face up, exile up to X other target creatures from the battlefield and/or creature cards from graveyards.
            When this creature leaves the battlefield, return the exiled cards to their owners' hands.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        Keyword.Ward(manaCost("{2}")),
        disguise("{X}{3}{W}"),
        triggered(turnedFaceUpSelf,
            move(target("targets"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            targets = listOf(TargetSlot("targets", TargetKind.Objects(or(
                and(another, creature, inZone(Zone.Battlefield)),
                and(creature, inZone(Zone.Graveyard)),
            )), min = n(0), max = turnedFaceUpX))),
        triggered(leavesSelf, move(Objects.Remembered("exiled"), Zone.Hand)),
    )

    // CR 614.1e: "As this creature is turned face up" is a replacement effect: the counters come as it turns
    @Test
    fun inkcloudCourier() = card(
        name = "Inkcloud Courier",
        cost = "{1}{U}",
        type = "Creature — Octopus Fish",
        pt = "2/1",
        text = """
            Disguise {5}{U} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            As this creature is turned face up, put four +1/+1 counters on it.
        """,
    ) parsesTo abilities(
        disguise("{5}{U}"),
        static(asTurnedFaceUp(counters(Objects.Self, 4))),
    )

    // CR 108.3, 110.2: "you own but don't control"; CR 115.1: any number of targets; the cards drawn count what the opponent controls
    // afterwards (CR 608.2c)
    @Test
    fun prizedKestrel() = card(
        name = "Prized Kestrel",
        cost = "{1}{U}{U}",
        type = "Artifact Creature — Bird",
        pt = "1/4",
        text = """
            Flying
            Whenever this creature attacks, gain control of target permanent you own but don't control.
            Disguise {1}{U}
            When this creature is turned face up, target opponent gains control of any number of target permanents you control. Draw a card for each one they gained control of this way.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(attacksSelf,
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.You)), Affected.Objects(target("permanent")), Duration.Indefinitely)),
            targets = listOf(slot("permanent", and(permanentFilter, ObjectFilter.OwnedBy(PlayerFilter.You),
                ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
        disguise("{1}{U}"),
        triggered(turnedFaceUpSelf,
            doing(Effect.ApplyMechanics(listOf(Mechanic.ControlledBy(Players.Target("opponent"))), Affected.Objects(target("permanents")),
                Duration.Indefinitely)),
            doing(Effect.Draw(Players.You, Value.Count(Objects.Where(target("permanents"),
                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent"))))))),
            targets = listOf(targetOpponent, slot("permanents", and(permanentFilter, you), min = 0, max = null))),
    )

    // CR 614.1c, 614.1e: as it enters (with the counters, CR 614.12) and as it is turned face up
    @Test
    fun rabbleTamingSentry() = card(
        name = "Rabble-Taming Sentry",
        cost = "{3}{G}{W}",
        type = "Creature — Centaur Soldier",
        pt = "4/4",
        text = """
            As this creature enters or is turned face up, put X +1/+1 counters on it, where X is the number of other creatures you control.
            Disguise {3}{G/W}{G/W} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(
        static(
            Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(p1p1, count(and(another, creature, you))))),
            asTurnedFaceUp(counters(Objects.Self, count(and(another, creature, you)))),
        ),
        disguise("{3}{G/W}{G/W}"),
    )

    // CR 509.1c: a requirement to block
    @Test
    fun sewergrateLurker() = card(
        name = "Sewergrate Lurker",
        cost = "{3}{G}{G}",
        type = "Creature — Wurm Horror",
        pt = "4/5",
        text = """
            When this creature enters or is turned face up, target creature blocks this turn if able.
            Disguise {4}{G} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(
        triggered(entersOrTurnedFaceUpSelf,
            untilEndOfTurn(target("creature"), Mechanic.MustBlock(attackers = ObjectFilter.Any)),
            targets = listOf(slot("creature", creature))),
        disguise("{4}{G}"),
    )

    // CR 701.23: one found Forest onto the battlefield tapped, the other into the hand
    @Test
    fun blossomingGroveKin() = card(
        name = "Blossoming Grove-Kin",
        cost = "{1}{G}",
        type = "Creature — Plant Elemental",
        pt = "0/0",
        text = """
            This creature gets +1/+1 for each Forest you control.
            Disguise {4}{G}
            When this creature is turned face up, search your library for up to two Forest cards and reveal them. Put one of them onto the battlefield tapped and the other into your hand, then shuffle.
        """,
    ) parsesTo abilities(
        static(Mechanic.ModifyPowerToughness(count(and(sub("Forest"), you)), count(and(sub("Forest"), you)))),
        disguise("{4}{G}"),
        triggered(turnedFaceUpSelf,
            search(sub("Forest"), max = 2, reveal = true),
            Step.Choose(Choice.Objects(ObjectFilter.Any, n(1), n(1), from = Objects.Bound("found")), bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true),
            move(Objects.Except(Objects.Bound("found"), Objects.Bound("chosen")), Zone.Hand),
            shuffle),
    )

    // CR 118.7: the disguise cost is reduced by {1} for each instant and sorcery card in your graveyard
    @Test
    fun runawayCipherist() = card(
        name = "Runaway Cipherist",
        cost = "{1}{R}",
        type = "Creature — Goblin Rogue",
        pt = "2/1",
        text = """
            Prowess, haste
            Disguise {5}{R}. This cost is reduced by {1} for each instant and sorcery card in your graveyard. (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, discard your hand, then draw three cards.
        """,
    ) parsesTo abilities(
        Keyword.Prowess,
        Keyword.Haste,
        Keyword.Disguise(manaCost("{5}{R}"), costChange = CostChange.Less(ManaCost.parse("{1}")!!, count(inYourGraveyard(or(instant, sorcery))))),
        triggered(turnedFaceUpSelf,
            doing(Effect.Discard(Players.You, objects = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))),
                count = n(1), filter = ObjectFilter.Any)),
            draw(3)),
    )

    // CR 603.2, 702.168d: one ability that triggers on either event
    @Test
    fun contraptionTinkerer() = card(
        name = "Contraption Tinkerer",
        cost = "{2}{U}{R}",
        type = "Creature — Goblin Artificer",
        pt = "3/2",
        text = """
            When this creature enters or is turned face up, create a 1/1 colorless Thopter artifact creature token with flying.
            Disguise {U/R}{U/R} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(
        triggered(entersOrTurnedFaceUpSelf, create(thopter)),
        disguise("{U/R}{U/R}"),
    )

    // CR 111.2: the target opponent creates and controls the tokens
    @Test
    fun houndedOssibeast() = card(
        name = "Hounded Ossibeast",
        cost = "{2}{B}",
        type = "Creature — Skeleton Beast",
        pt = "6/2",
        text = """
            Menace
            When this creature enters, target opponent creates two 1/1 white Dog creature tokens.
            When this creature dies, each opponent loses 3 life.
            Disguise {1}{B}
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        triggered(entersSelf, doing(Effect.CreateToken(creatureToken("Dog", listOf(Color.White), 1, 1), n(2), Players.Target("opponent"))),
            targets = listOf(targetOpponent)),
        triggered(diesSelf, loseLife(3, Players.Each(PlayerFilter.Opponent))),
        disguise("{1}{B}"),
    )

    // CR 708.5, 611.2a: for the turn, you may look at face-down creatures you don't control
    @Test
    fun shamblingWashtub() = card(
        name = "Shambling Washtub",
        cost = "{5}",
        type = "Artifact Creature — Golem",
        pt = "4/5",
        text = """
            {2}: Until end of turn, you may look at face-down creatures you don't control any time.
            Disguise {5} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(
        activated(manaCost("{2}"), doing(Effect.ApplyMechanics(
            listOf(Mechanic.MayLookAt(Objects.Each(and(ObjectFilter.HasStatus(arcanum.api.Status.FaceDown), creature,
                ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
            Affected.Players(Players.You), Duration.EndOfTurn))),
        disguise("{5}"),
    )

    // CR 115.1: "up to one target"
    @Test
    fun culpritOnTheLoose() = card(
        name = "Culprit on the Loose",
        cost = "{4}{R}",
        type = "Creature — Giant Rogue",
        pt = "5/4",
        text = """
            Disguise {4}{R} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature enters or is turned face up, up to one target creature gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        disguise("{4}{R}"),
        triggered(entersOrTurnedFaceUpSelf, pump(target("creature"), 2, 0), targets = listOf(slot("creature", creature, min = 0))),
    )

    // CR 603.10a, 608.2h: "that creature" is the one turned face up, which deals the damage
    @Test
    fun flashpowderShowman() = card(
        name = "Flashpowder Showman",
        cost = "{1}{R}",
        type = "Creature — Lizard Assassin",
        pt = "3/2",
        text = """
            Disguise {R} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            Whenever this creature or another creature you control is turned face up, that creature deals damage equal to its power to each opponent.
        """,
    ) parsesTo abilities(
        disguise("{R}"),
        triggered(on(EventPattern.TurnedFaceUp(or(ObjectFilter.Is(Objects.Self), and(another, creature, you)))),
            damage(power(Objects.EventObject(EventRole.Object)), eachOpponent, source = Objects.EventObject(EventRole.Object))),
    )

    @Test
    fun dashingCutpurse() = card(
        name = "Dashing Cutpurse",
        cost = "{2}{B}{G}",
        type = "Creature — Elf Rogue",
        pt = "3/3",
        text = """
            Deathtouch
            When this creature enters or is turned face up, target creature gains indestructible until end of turn.
            Disguise {4}{B/G}{B/G} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        triggered(entersOrTurnedFaceUpSelf, gains(target("creature"), Keyword.Indestructible), targets = listOf(slot("creature", creature))),
        disguise("{4}{B/G}{B/G}"),
    )

    // CR 400.7: "return it" is the exiled card; CR 608.2h: "you controlled it" and "its controller" are the permanent as it last existed;
    // CR 110.2a: returned under the control of the player who returns it
    @Test
    fun steadfastPortwarden() = card(
        name = "Steadfast Portwarden",
        cost = "{1}{W}",
        type = "Creature — Elephant Cleric",
        pt = "3/2",
        text = """
            Disguise {1}{W} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, exile another target nonland permanent. If you controlled it, return it to the battlefield tapped. Otherwise, its controller creates a 2/2 white and blue Detective creature token.
        """,
    ) parsesTo abilities(
        disguise("{1}{W}"),
        triggered(turnedFaceUpSelf,
            move(target("permanent"), Zone.Exile, postBind = "exiled"),
            ifThen(Condition.ObjectMatches(target("permanent"), you),
                move(Objects.Bound("exiled"), Zone.Battlefield, tapped = true),
                otherwise = listOf(doing(Effect.CreateToken(creatureToken("Detective", listOf(Color.White, Color.Blue), 2, 2), n(1),
                    Players.ControllerOf(target("permanent")))))),
            targets = listOf(slot("permanent", and(another, nonland, permanentFilter)))),
    )

    @Test
    fun backstreetMugger() = card(
        name = "Backstreet Mugger",
        cost = "{2}{B}",
        type = "Creature — Vampire Rogue",
        pt = "3/3",
        text = """
            This creature enters tapped.
            Disguise {4}{B}{B} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this creature is turned face up, target opponent loses 3 life and you gain 3 life.
        """,
    ) parsesTo abilities(
        entersTapped,
        disguise("{4}{B}{B}"),
        triggered(turnedFaceUpSelf, loseLife(3, Players.Target("opponent")), gainLife(3), targets = listOf(targetOpponent)),
    )

    // CR 701.25: surveil
    @Test
    fun cathedralProwler() = card(
        name = "Cathedral Prowler",
        cost = "{5}{B}",
        type = "Creature — Vampire Detective",
        pt = "3/4",
        text = """
            Flying
            Whenever this creature deals combat damage to a player, you gain 1 life and surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            Disguise {4}{B} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(combatDamageToAPlayer, gainLife(1), doing(Effect.Surveil(Players.You, n(1)))),
        disguise("{4}{B}"),
    )

    // CR 702.168
    @Test
    fun ironjawClanBruiser() = card(
        name = "Ironjaw-Clan Bruiser",
        cost = "{4}{R}{R}",
        type = "Creature — Cyclops Warrior",
        pt = "3/2",
        text = """
            Double strike, trample
            Disguise {3}{R}{R} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
        """,
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        Keyword.Trample,
        disguise("{3}{R}{R}"),
    )

    // CR 702.168a: a land card may be cast face down; CR 106.4, 500.4: this mana stays until end of turn
    @Test
    fun boughOfTheElderGrove() = card(
        name = "Bough of the Elder Grove",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            Disguise {3} (You may cast this card face down for {3} as a 2/2 creature with ward {2}. Turn it face up any time for its disguise cost.)
            When this land is turned face up, add two mana of any one color. Until end of turn, you don't lose this mana as steps and phases end.
        """,
    ) parsesTo abilities(
        Ability.Activated(Cost(listOf(CostPart.TapSelf)), Instructions(steps = listOf(
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), players = Players.You)))), anyTime),
        disguise("{3}"),
        triggered(turnedFaceUpSelf, doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(2)), keptUntil = Duration.EndOfTurn), players = Players.You))),
    )
}
