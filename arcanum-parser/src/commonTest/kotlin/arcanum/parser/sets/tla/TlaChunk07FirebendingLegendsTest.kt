package arcanum.parser.sets.tla

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.cardFaces
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Avatar: The Last Airbender chunk 07: firebending legends: unspent mana kept or turned red (CR 106.4), excess fight damage as mana
 * (CR 120.4a), casting exiled cards with flash and any mana (CR 601.2a, 609.4b), flashback granted to graveyard cards (CR 702.34a),
 * copying spells cast while attacking (CR 707.10), "you may pay {X}. When you do" (CR 603.12), and lands that become Mountains (CR 305.7).
 */
class TlaChunk07FirebendingLegendsTest {
    // CR 701.23 (searching for a chosen name in several zones), 603.12, 601.2c (total mana value of the targets)
    @Test
    fun theRiseOfVardan() = cardFaces(Layout.Transform,
        card(
            name = "The Rise of Vardan",
            cost = "{4}{B}{B}",
            type = "Enchantment — Saga",
            text = """
                (As this Saga enters and after your draw step, add a lore counter.)
                I — Destroy all creatures.
                II — Choose a card name. Search target opponent's graveyard, hand, and library for up to four cards with that name and exile them. Then that player shuffles.
                III — Exile this Saga, then return it to the battlefield transformed under your control.
            """,
        ) to abilities(
            chapter(1, destroy(Objects.Each(creature))),
            chapter(2,
                Step.Choose(Choice.CardName(ObjectFilter.Any), bind = "name", chooser = Players.You),
                doing(Effect.Search(Players.Target("opponent"), ObjectFilter.ChosenName("name"), max = n(4), zones = listOf(Zone.Graveyard, Zone.Hand, Zone.Library),
                    bind = "found", searcher = Players.You)),
                move(Objects.Bound("found"), Zone.Exile),
                doing(Effect.Shuffle(Players.Target("opponent"))),
                targets = listOf(targetOpponent)),
            exileAndReturnTransformed,
        ),
        card(
            name = "Flame Sovereign Vardan",
            cost = null,
            type = "Legendary Creature — Human Noble",
            text = """
                Menace, firebending 3 (Whenever this creature attacks, add {R}{R}{R}. This mana lasts until end of combat.)
                Whenever Flame Sovereign Vardan deals combat damage to a player, you may pay {X}. When you do, put any number of target creature cards with total mana value X or less from that player's graveyard onto the battlefield under your control.
            """,
            pt = "5/5",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Menace,
            firebending(3),
            triggered(combatDamageToAPlayer,
                Step.Choose(Choice.Number(n(0)), bind = "X", chooser = Players.You),
                Step.Pay(cost(CostPart.Mana(ManaCost.parse("{X}")!!, x = Value.Bound("X"))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(reflexive(
                    doing(Effect.Move(target("cards"), Destination(Zone.Battlefield, controller = Players.You))),
                    targets = listOf(TargetSlot("cards", TargetKind.Objects(and(creature, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.EventPlayer)))),
                        min = n(0), max = null, totalAtMost = Choice.TotalLimit(NumericProperty.ManaValue, Value.Bound("X")))))))),
        ),
    )

    // CR 701.14, 120.4a (excess damage), 106.4 (unspent mana kept)
    @Test
    fun theFinalFlameDuel() = card(
        name = "The Final Flame Duel",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Target creature you control fights target creature an opponent controls. If the creature the opponent controls is dealt excess damage this way, add that much {R}.
            Until end of turn, you don't lose unspent red mana as steps and phases end.
        """,
    ) parsesTo abilities(
        spell(
            doing(Effect.Fight(target("creature"), target("second creature"), bind = "damage")),
            doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!, times = Value.Excess("damage"))), Players.You)),
            doing(Effect.ApplyMechanics(listOf(Mechanic.KeepsUnspentMana(listOf(Color.Red))), Affected.Players(Players.You), Duration.EndOfTurn)),
            targets = listOf(slot("creature", creatureYouControl), slot("second creature", creatureOpponentControls))),
    )

    // CR 607.2a (cards exiled with it), 601.2a, 702.8a, 609.4b
    @Test
    fun vexaCunningUsurper() = card(
        name = "Vexa, Cunning Usurper",
        cost = "{2}{U}{B}{B}",
        type = "Legendary Creature — Human Noble Rogue",
        text = """
            Firebending 2 (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
            When Vexa enters, target opponent exiles a nontoken creature they control, then they exile a nonland card from their graveyard.
            During your turn, you may cast cards exiled with Vexa and you may cast them as though they had flash. Mana of any type can be spent to cast those spells.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        firebending(2),
        triggered(entersSelf,
            Step.Choose(Choice.Objects(and(not(token), creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))), n(1), n(1)), bind = "creature",
                chooser = Players.Target("opponent")),
            move(Objects.Bound("creature"), Zone.Exile, postBind = "exiled"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled"))),
            Step.Choose(Choice.Objects(and(nonland, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))), n(1), n(1)), bind = "card",
                chooser = Players.Target("opponent")),
            move(Objects.Bound("card"), Zone.Exile, postBind = "exiled2"),
            doing(Effect.Remember("exiled", Objects.Bound("exiled2"))),
            targets = listOf(targetOpponent)),
        static(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, manaAsAnyColor = true, holder = PlayerFilter.You), Mechanic.InstantSpeed,
            affects = Objects.Remembered("exiled"), condition = Condition.YourTurn),
    )

    // CR 207.2c (raid), 614.1c
    @Test
    fun ruthlessOverseer() = card(
        name = "Ruthless Overseer",
        cost = "{3}{B}{R}",
        type = "Creature — Human Soldier",
        text = """
            Raid — This creature enters with a +1/+1 counter on it if you attacked this turn.
            Whenever this creature attacks, create a 2/2 red Soldier creature token with firebending 1. (Whenever it attacks, add {R}. This mana lasts until end of combat.)
        """,
        pt = "5/4",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
            modify = listOf(Modification.EntersWithCounters(p1p1, n(1))))), affects = Affected.Objects(Objects.Self),
            condition = Condition.HappenedThisTurn(EventPattern.Attack(you), atLeast = 1, measure = Measure.Events), abilityWord = "Raid"),
        triggered(attacksSelf, create(soldierFirebending)),
    )

    // CR 707.10 (a copy of a spell; of a permanent spell, a token, 707.10f)
    @Test
    fun flameSovereignVexa() = card(
        name = "Flame Sovereign Vexa",
        cost = "{1}{U}{B}{R}",
        type = "Legendary Creature — Human Noble",
        text = """
            Firebending 2 (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
            Whenever you cast a spell while Flame Sovereign Vexa is attacking, copy that spell. You may choose new targets for the copy. (A copy of a permanent spell becomes a token.)
        """,
        pt = "4/4",
    ) parsesTo abilities(
        firebending(2),
        triggered(youCast(ObjectFilter.Any), doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), Players.You, newTargets = true)),
            triggersOnlyIf = Condition.ObjectMatches(Objects.Self, attacking)),
    )

    // CR 702.189a; one ability with two trigger events (CR 603.2)
    @Test
    fun flameSovereignDarrow() = card(
        name = "Flame Sovereign Darrow",
        cost = "{R}{W}{B}",
        type = "Legendary Creature — Human Noble Ally",
        text = """
            Firebending X, where X is Flame Sovereign Darrow's power. (Whenever this creature attacks, add X {R}. This mana lasts until end of combat.)
            Whenever you cast a spell from exile and whenever a permanent you control enters from exile, put a +1/+1 counter on each creature you control.
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.FirebendingX(powerOf(Objects.Self)),
        triggered(Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You, from = Zone.Exile),
            EventPattern.ZoneChange(and(permanentFilter, you), from = Zone.Exile, to = Zone.Battlefield))), counters(Objects.Each(creatureYouControl))),
    )

    // CR 702.34a: flashback granted, its cost the card's mana cost (CR 118.9)
    @Test
    fun baldricGrandBlossom() = card(
        name = "Baldric, Grand Blossom",
        cost = "{3}{G}{U}{R}",
        type = "Legendary Creature — Human Noble Ally",
        text = """
            Firebending 2
            During your turn, each non-Lesson instant and sorcery card in your graveyard has flashback. The flashback cost is equal to that card's mana cost. (You may cast a card from your graveyard for its flashback cost. Then exile it.)
            During your turn, each Lesson card in your graveyard has flashback {1}.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        firebending(2),
        static(has(Keyword.Flashback(Cost(listOf(CostPart.ItsManaCost)))), affects = Objects.Each(and(not(lesson), or(instant, sorcery), inZone(Zone.Graveyard),
            ObjectFilter.OwnedBy(PlayerFilter.You))), condition = Condition.YourTurn),
        static(has(Keyword.Flashback(manaCost("{1}"))), affects = Objects.Each(inYourGraveyard(lesson)), condition = Condition.YourTurn),
    )

    // CR 106.4, 614.1a (unspent mana becomes red instead of being lost)
    @Test
    fun malgorTheCinderbirdKing() = card(
        name = "Malgor, the Cinderbird King",
        cost = "{2}{B}{B}{R}{R}",
        type = "Legendary Creature — Human Noble",
        text = """
            Trample, firebending 4, haste
            If you would lose unspent mana, that mana becomes red instead.
            Malgor has flying and indestructible as long as you have six or more unspent mana.
        """,
        pt = "7/7",
    ) parsesTo abilities(
        Keyword.Trample,
        firebending(4),
        Keyword.Haste,
        playerStatic(Mechanic.UnspentManaBecomes(Color.Red)),
        static(has(Keyword.Flying, Keyword.Indestructible), condition = Condition.Compare(Value.ManaInPool(Players.You), Comparison.AtLeast, n(6))),
    )

    @Test
    fun solarGuardians() = card(
        name = "Solar Guardians",
        cost = "{2}{R}{W}",
        type = "Creature — Human Warrior Ally",
        text = """
            Firebending X, where X is the number of creatures you control. (Whenever this creature attacks, add X {R}. This mana lasts until end of combat.)
            {5}: Create a 1/1 white Ally creature token.
        """,
        pt = "3/5",
    ) parsesTo abilities(
        Keyword.FirebendingX(count(creatureYouControl)),
        activated(cost(mana("{5}")), create(allyToken)),
    )

    // CR 601.2f
    @Test
    fun elderBaldric() = card(
        name = "Elder Baldric",
        cost = "{1}{R/G}{R/G}",
        type = "Legendary Creature — Human Noble Ally",
        text = """
            Firebending 1 (Whenever this creature attacks, add {R}. This mana lasts until end of combat.)
            Lesson spells you cast cost {1} less to cast.
        """,
        pt = "4/2",
    ) parsesTo abilities(
        firebending(1),
        static(Mechanic.CostChange(and(lesson, spellFilter, you), CostChange.Less(ManaCost.parse("{1}")!!, n(1)))),
    )

    @Test
    fun spitefulWarden() = card(
        name = "Spiteful Warden",
        cost = "{2}{B/R}",
        type = "Creature — Human Soldier",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            Firebending 1 (Whenever this creature attacks, add {R}. This mana lasts until end of combat.)
            {3}: This creature deals 1 damage to each opponent.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Menace,
        firebending(1),
        activated(cost(mana("{3}")), damage(1, eachOpponent)),
    )

    // CR 701.21a: another permanent sacrificed by you
    @Test
    fun krellRuthlessAdmiral() = card(
        name = "Krell, Ruthless Admiral",
        cost = "{2}{B/R}{B/R}",
        type = "Legendary Creature — Human Soldier",
        text = """
            Firebending 2 (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
            Whenever you sacrifice another permanent, creatures you control get +1/+0 until end of turn.
        """,
        pt = "3/4",
    ) parsesTo abilities(
        firebending(2),
        triggered(youSacrificeAnother, pump(Objects.Each(creatureYouControl), 1, 0)),
    )

    // CR 614.1c (other lands entering tapped), 305.7 (nonbasic lands that are Mountains)
    @Test
    fun krellTheMoonSlayer() = card(
        name = "Krell, the Moon Slayer",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Soldier",
        text = """
            Menace
            Nonbasic lands enter tapped.
            {7}: Put a conqueror counter on Krell.
            As long as Krell has a conqueror counter on him, nonbasic lands are Mountains. (They lose all other land types and abilities and have "{T}: Add {R}.")
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Menace,
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(not(basic), land), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped))),
        activated(cost(mana("{7}")), counters(Objects.Self, 1, CounterKind.Named("conqueror"))),
        static(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Mountain"))), replaceSubtypes = true), affects = Objects.Each(and(not(basic), land)),
            condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Named("conqueror"), atLeast = 1))),
    )
}
