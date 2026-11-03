package arcanum.parser.sets.tmt

import arcanum.api.Affected
import arcanum.api.AttackTarget
import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.TokenSpec
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Teenage Mutant Ninja Turtles chunk 02: creatures with sneak (CR 702.190: cast in your declare blockers step for the sneak cost and an
 * unblocked attacker returned to hand, entering tapped and attacking), with or without its reminder text; "if his sneak cost was paid"
 * (CR 607.2i) as an intervening "if" (CR 603.4), with "this turn", and as an "instead" for an exile until it leaves (CR 610.3); combat
 * damage triggers putting a card from that player's graveyard onto the battlefield attacking that player (CR 508.4); permission to cast
 * small creatures from the graveyard with a finality counter (CR 122.1h); a two-name creature dealing damage as "they" (CR 201.5).
 */
class TmtChunk02SneakCreaturesTest {
    private val gainedLife = on(EventPattern.GainLife(PlayerFilter.You))

    @Test
    fun bastionEldestBrother() = card(
        name = "Bastion, Eldest Brother",
        cost = "{2}{W}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Sneak {W} (You may cast this spell for {W} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
            Bastion gets +1/+0 for each other creature you control.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        sneak("{W}"),
        static(Mechanic.ModifyPowerToughness(count(and(another, creature, you)), n(0))),
    )

    @Test
    fun keenBladeOfTheSewers() = card(
        name = "Keen, Blade of the Sewers",
        cost = "{1}{W}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Sneak {W} (You may cast this spell for {W} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
            Lifelink
            Whenever you gain life, put a +1/+1 counter on Keen.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        sneak("{W}"),
        Keyword.Lifelink,
        triggered(gainedLife, counters(Objects.Self)),
    )

    // CR 603.4: checked as it triggers and as it resolves
    @Test
    fun vanguardMaskedLeader() = card(
        name = "Vanguard, Masked Leader",
        cost = "{W}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Sneak {3}{W}{W} (You may cast this spell for {3}{W}{W} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
            When Vanguard enters, if his sneak cost was paid, creatures you control get +2/+0 until end of turn.
            {1}{W}: Vanguard gains first strike until end of turn.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        sneak("{3}{W}{W}"),
        triggered(entersSelf, pump(Objects.Each(creatureYouControl), 2, 0), interveningIf = sneakPaid),
        activated(manaCost("{1}{W}"), gains(Objects.Self, Keyword.FirstStrike)),
    )

    // CR 601.2a: cast from the graveyard during your turn; CR 122.1h: the finality counter it enters with
    @Test
    fun quillonDrainSamurai() = card(
        name = "Quillon, Drain Samurai",
        cost = "{3}{W}",
        type = "Legendary Creature — Mutant Ninja Turtle Samurai",
        text = """
            Sneak {2}{W}{W}
            Double strike
            During your turn, you may cast creature spells with power or toughness 1 or less from your graveyard. If you cast a spell this way, that creature enters with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        sneak("{2}{W}{W}"),
        Keyword.DoubleStrike,
        static(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You, spellGets = listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Affected), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(CounterKind.Named("finality"), n(1)))))),
            affects = Objects.Each(and(creature, or(power(arcanum.api.Comparison.AtMost, 1), ObjectFilter.Toughness(arcanum.api.Comparison.AtMost, n(1))),
                inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))),
            condition = Condition.YourTurn),
    )

    @Test
    fun sprocketGizmoSavant() = card(
        name = "Sprocket, Gizmo Savant",
        cost = "{2}{U}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Sneak {1}{U} (You may cast this spell for {1}{U} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
            Whenever Sprocket deals combat damage to a player, create a token that's a copy of target artifact you control.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        sneak("{1}{U}"),
        triggered(combatDamageToAPlayer, create(TokenSpec.CopyOf(target("artifact"))), targets = listOf(slot("artifact", and(artifact, you)))),
    )

    @Test
    fun takedaRisingWarlord() = card(
        name = "Takeda, Rising Warlord",
        cost = "{2}{B}",
        type = "Legendary Creature — Human Ninja",
        text = """
            Sneak {1}{B} (You may cast this spell for {1}{B} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
            Whenever Takeda deals combat damage to a player, you draw a card and lose 1 life.
        """,
        pt = "3/1",
    ) parsesTo abilities(
        sneak("{1}{B}"),
        triggered(combatDamageToAPlayer, draw(1), loseLife(1)),
    )

    // CR 508.4: put onto the battlefield attacking the damaged player
    @Test
    fun razorfinCloneAssassin() = card(
        name = "Razorfin, Clone Assassin",
        cost = "{2}{B}{B}",
        type = "Legendary Creature — Shark Octopus Ninja",
        text = """
            Sneak {3}{B}{B}
            First strike
            Whenever Razorfin deals combat damage to a player, put up to one target creature card from that player's graveyard onto the battlefield under your control. It enters tapped and attacking that player.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        sneak("{3}{B}{B}"),
        Keyword.FirstStrike,
        triggered(combatDamageToAPlayer,
            doing(Effect.Move(target("card"), Destination(Zone.Battlefield, tapped = true, controller = Players.You,
                attacking = AttackTarget.These(Recipients(players = Players.EventPlayer))))),
            targets = listOf(slot("card", and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.EventPlayer))), min = 0))),
    )

    @Test
    fun ironclawRelentless() = card(
        name = "Ironclaw, Relentless",
        cost = "{4}{B}",
        type = "Legendary Creature — Human Ninja",
        text = """
            Sneak {3}{B} (You may cast this spell for {3}{B} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
            Deathtouch
            Whenever Ironclaw enters or attacks, another target creature you control gains deathtouch until end of turn.
        """,
        pt = "6/4",
    ) parsesTo abilities(
        sneak("{3}{B}"),
        Keyword.Deathtouch,
        triggered(entersOrAttacksSelf, gains(target("creature"), Keyword.Deathtouch), targets = listOf(slot("creature", and(another, creature, you)))),
    )

    @Test
    fun oldWhiskerSensei() = card(
        name = "Old Whisker, Sensei",
        cost = "{1}{B}",
        type = "Legendary Creature — Mutant Ninja Rat",
        text = """
            Sneak {B} (You may cast this spell for {B} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
            Menace (This creature can't be blocked except by two or more creatures.)
            Other Ninjas you control get +1/+1.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        sneak("{B}"),
        Keyword.Menace,
        static(pt(1, 1), affects = Objects.Each(and(another, ninja, you))),
    )

    @Test
    fun brawlerNightProwler() = card(
        name = "Brawler, Night Prowler",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Sneak {1}{R}{R} (You may cast this spell for {1}{R}{R} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
            Attacking creatures you control have double strike.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        sneak("{1}{R}{R}"),
        static(has(Keyword.DoubleStrike), affects = Objects.Each(and(attacking, creature, you))),
    )

    @Test
    fun doodleImprovArtist() = card(
        name = "Doodle, Improv Artist",
        cost = "{3}{G}",
        type = "Legendary Creature — Mutant Ninja Turtle",
        text = """
            Sneak {2}{G}{G} (You may cast this spell for {2}{G}{G} if you also return an unblocked attacker you control to hand during the declare blockers step. He enters tapped and attacking.)
            Whenever Doodle deals combat damage to a player, you may put a creature card and/or a land card from your hand onto the battlefield.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        sneak("{2}{G}{G}"),
        triggered(combatDamageToAPlayer,
            Step.Choose(arcanum.api.Choice.Objects(and(creature, cardFilter), n(0), n(1), from = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                bind = "creature", chooser = Players.You),
            Step.Choose(arcanum.api.Choice.Objects(and(land, cardFilter), n(0), n(1), from = Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                bind = "land", chooser = Players.You),
            move(Objects.Union(listOf(Objects.Bound("creature"), Objects.Bound("land"))), Zone.Battlefield)),
    )

    // CR 702.190: paid as she was cast this turn (she entered this turn)
    @Test
    fun mireiHeirOfTheClan() = card(
        name = "Mirei, Heir of the Clan",
        cost = "{1}{W}{B}",
        type = "Legendary Creature — Human Ninja",
        text = """
            Sneak {2}{W}{B} (You may cast this spell for {2}{W}{B} if you also return an unblocked attacker you control to hand during the declare blockers step. She enters tapped and attacking.)
            Whenever Mirei deals combat damage to a player, return target creature card from your graveyard to your hand. If her sneak cost was paid this turn, instead return that card to the battlefield.
        """,
        pt = "3/3",
    ) parsesTo abilities(
        sneak("{2}{W}{B}"),
        triggered(combatDamageToAPlayer,
            Step.If(Condition.And(listOf(sneakPaid, Condition.ObjectMatches(Objects.Self,
                    ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield))))),
                then = listOf(move(target("card"), Zone.Battlefield)),
                otherwise = listOf(move(target("card"), Zone.Hand))),
            targets = listOf(slot("card", inYourGraveyard(creature)))),
    )

    // CR 610.3: exiled until it leaves, or for good when its sneak cost was paid
    @Test
    fun defectorKunoichi() = card(
        name = "Defector Kunoichi",
        cost = "{2}{W}",
        type = "Creature — Mutant Ninja Fox",
        text = """
            Sneak {2}{W}{B} (You may cast this spell for {2}{W}{B} if you also return an unblocked attacker you control to hand during the declare blockers step. It enters tapped and attacking.)
            When this creature enters, choose target creature an opponent controls. Exile that creature until this creature leaves the battlefield. If this creature's sneak cost was paid, instead exile the chosen creature.
        """,
        pt = "3/2",
    ) parsesTo abilities(
        sneak("{2}{W}{B}"),
        triggered(entersSelf,
            Step.If(sneakPaid,
                then = listOf(move(target("creature"), Zone.Exile)),
                otherwise = listOf(doing(Effect.Move(target("creature"), Destination(Zone.Exile), untilLeaves = Objects.Self)))),
            targets = listOf(slot("creature", creatureOpponentControls))),
    )

    // CR 107.4e: hybrid symbols in the sneak cost
    @Test
    fun clanFootsoldiers() = card(
        name = "Clan Footsoldiers",
        cost = "{4}{W/B}{W/B}",
        type = "Creature — Human Ninja",
        text = """
            Sneak {3}{W/B} (You may cast this spell for {3}{W/B} if you also return an unblocked attacker you control to hand during the declare blockers step. It enters tapped and attacking.)
            When this creature enters, you gain 3 life.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        sneak("{3}{W/B}"),
        triggered(entersSelf, gainLife(3)),
    )

    // CR 107.1a: half their life, rounded up
    @Test
    fun grimBladeAndIronFist() = card(
        name = "Grim Blade & Iron Fist",
        cost = "{W}{B}",
        type = "Legendary Creature — Mutant Ninja Turtle Human",
        text = """
            Sneak {W}{B}
            Attacking Ninjas you control have deathtouch.
            Whenever Grim Blade & Iron Fist deal combat damage to a player, create a 1/1 black Ninja creature token. Then if you control five or more Ninjas, that player loses half their life, rounded up.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        sneak("{W}{B}"),
        static(has(Keyword.Deathtouch), affects = Objects.Each(and(attacking, ninja, you))),
        triggered(combatDamageToAPlayer,
            create(ninjaToken),
            Step.If(Condition.Exists(and(ninja, you), atLeast = 5), then = listOf(doing(Effect.LoseLife(Players.EventPlayer,
                Value.Half(Value.LifeOf(Players.EventPlayer), roundUp = true)))))),
    )
}
