package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.whenThisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 13: the set's other cards whose wordings the parser already read, checked against their meaning:
 * impulse draw until the end of your next turn, mana kept until end of turn, the nth spell each turn per player,
 * "triggers only once each turn", characteristic-defining power and toughness, cost reductions, lands with several
 * abilities.
 */
class BlbChunk13CommonsTest {
    private val yourAbilities = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
    private val noncreature = ObjectFilter.Not(creature)
    private val enchanted = Objects.AttachedTo(of = Objects.Self)
    private fun mana(text: String) = CostPart.Mana(arcanum.api.ManaCost.parse(text)!!)
    private fun colorless() = Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{C}")!!)), Players.You))
    private fun anyColor() = Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You))
    private fun draw(n: Int = 1) = Step.Do(Effect.Draw(Players.You, one(n)))
    private fun youCast(spell: ObjectFilter) = Trigger.On(EventPattern.Cast(spell, by = PlayerFilter.You))
    private val attacks = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self)))
    private fun mayPlayExiled(duration: Duration) = Step.Do(Effect.ApplyMechanics(
        listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)), Affected.Objects(Objects.Bound("exiled")), duration))
    private val exileTop = Step.Do(Effect.Move(Objects.LibraryCards(Players.You, one(), fromTop = true), Destination(Zone.Exile), postBind = "exiled"))

    @Test
    fun plumeOfSoaring() = card(
        name = "Plume of Soaring",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            When this Aura enters, draw a card.
            Enchanted creature gets +1/+0 and has flying.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(arcanum.api.TargetKind.Objects(creature)),
        whenThisEnters(draw()),
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(one(), one(0)), Mechanic.GainAbilities(listOf(Keyword.Flying))), affects = Affected.Objects(enchanted)),
    )

    @Test
    fun lifesongPair() = card(
        name = "Lifesong Pair",
        cost = "{1}{W}",
        type = "Creature — Bat Bird",
        pt = "1/2",
        text = """
            Flying
            Whenever another creature you control enters, you gain 1 life.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, you)), to = Zone.Battlefield)),
            steps(Step.Do(Effect.GainLife(Players.You, one())))),
    )

    @Test
    fun burrdownTroupe() = card(
        name = "Burrdown Troupe",
        cost = "{2}{W}",
        type = "Creature — Mouse Bard",
        pt = "3/3",
        text = "Whenever this creature attacks, untap target nonland permanent.",
    ) parsesTo abilities(
        Ability.Triggered(attacks, Instructions(targets = listOf(slot("permanent", nonlandPermanent)),
            steps = listOf(Step.Do(Effect.Untap(Objects.Target("permanent")))))),
    )

    @Test
    fun croakingHerald() = card(
        name = "Croaking Herald",
        cost = "{1}{U}",
        type = "Creature — Frog Advisor",
        pt = "2/1",
        text = "When this creature enters, draw a card, then discard a card.",
    ) parsesTo abilities(
        whenThisEnters(draw(), Step.Do(Effect.Discard(Players.You, one(), filter = ObjectFilter.Any))),
    )

    // CR 701.25a: surveil
    @Test
    fun glowshellPair() = card(
        name = "Glowshell Pair",
        cost = "{3}{U}",
        type = "Creature — Rat Otter",
        pt = "3/4",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            When this creature enters, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)
        """,
    ) parsesTo abilities(
        Keyword.Prowess,
        whenThisEnters(Step.Do(Effect.Surveil(Players.You, one(2)))),
    )

    @Test
    fun featherbondGuard() = card(
        name = "Featherbond Guard",
        cost = "{1}{U}",
        type = "Creature — Bird Scout",
        pt = "2/1",
        text = """
            Flash
            Flying
            When this creature enters, target creature you control gains hexproof until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        whenThisEnters(gains(Objects.Target("creature"), Keyword.Hexproof), targets = listOf(slot("creature", creatureYouControl))),
    )

    @Test
    fun gullyProwler() = card(
        name = "Gully Prowler",
        cost = "{B}",
        type = "Creature — Lizard Rogue",
        pt = "1/1",
        text = """
            Menace (This creature can't be blocked except by two or more creatures.)
            {1}{B}: This creature gets +1/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Activated(Cost(listOf(mana("{1}{B}"))), steps(pump(Objects.Self, 1, 1)), yourAbilities),
    )

    // CR 611.2a: the permission lasts until the end of your next turn
    @Test
    fun brisasTrailblazer() = card(
        name = "Brisa's Trailblazer",
        cost = "{3}{R}",
        type = "Creature — Otter Wizard",
        pt = "4/2",
        text = "When this creature enters, exile the top card of your library. Until the end of your next turn, you may play that card.",
    ) parsesTo abilities(
        whenThisEnters(exileTop, mayPlayExiled(Duration.UntilEndOfYourNextTurn)),
    )

    // CR 106.4a: mana kept until end of turn
    @Test
    fun boldHoarder() = card(
        name = "Bold Hoarder",
        cost = "{1}{R}",
        type = "Creature — Raccoon Rogue",
        pt = "2/1",
        text = """
            First strike
            Whenever this creature attacks, add {R}. Until end of turn, you don't lose this mana as steps and phases end.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        Ability.Triggered(attacks, steps(Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(arcanum.api.ManaCost.parse("{R}")!!),
            keptUntil = Duration.EndOfTurn), Players.You)))),
    )

    // CR 603.2: "triggers only once each turn"
    @Test
    fun tamerOfSqualls() = card(
        name = "Tamer of Squalls",
        cost = "{2}{R}",
        type = "Creature — Otter Wizard",
        pt = "1/4",
        text = "Whenever you cast a noncreature or Otter spell, you may exile the top card of your library. Until end of turn, you may play that card. This ability triggers only once each turn.",
    ) parsesTo abilities(
        Ability.Triggered(
            youCast(ObjectFilter.Or(listOf(noncreature, sub("Otter")))),
            steps(Step.May(listOf(exileTop), player = Players.You), mayPlayExiled(Duration.EndOfTurn)),
            perTurn = 1,
        ),
    )

    // the second spell is counted for each player separately
    @Test
    fun emberbornBrawler() = card(
        name = "Emberborn Brawler",
        cost = "{2}{R}",
        type = "Creature — Lizard Warlock",
        pt = "2/3",
        text = """
            Haste
            Whenever a player casts their second spell each turn, this creature deals 2 damage to target opponent.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Triggered(
            Trigger.On(listOf(EventPattern.Cast(ObjectFilter.Any, by = PlayerFilter.Any)), nthThisTurn = 2),
            Instructions(targets = listOf(targetOpponent), steps = listOf(
                Step.Do(Effect.DealDamage(Recipients(players = Players.Target("opponent")), one(2), source = Objects.Self)))),
        ),
    )

    @Test
    fun cindersparkPair() = card(
        name = "Cinderspark Pair",
        cost = "{2}{R}",
        type = "Creature — Lizard Otter",
        pt = "1/3",
        text = """
            {T}: This creature deals 1 damage to target opponent.
            Whenever you cast a noncreature spell, untap this creature.
        """,
    ) parsesTo abilities(
        Ability.Activated(Cost(listOf(CostPart.TapSelf)), Instructions(targets = listOf(targetOpponent), steps = listOf(
            Step.Do(Effect.DealDamage(Recipients(players = Players.Target("opponent")), one(), source = Objects.Self)))), yourAbilities),
        Ability.Triggered(youCast(noncreature), steps(Step.Do(Effect.Untap(Objects.Self)))),
    )

    @Test
    fun raccoonDrummer() = card(
        name = "Raccoon Drummer",
        cost = "{1}{R}",
        type = "Creature — Raccoon Bard",
        pt = "2/2",
        text = "{T}: Target creature you control gains haste until end of turn. Activate only as a sorcery.",
    ) parsesTo abilities(
        Ability.Activated(Cost(listOf(CostPart.TapSelf)), Instructions(targets = listOf(slot("creature", creatureYouControl)),
            steps = listOf(gains(Objects.Target("creature"), Keyword.Haste))), ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)),
    )

    @Test
    fun druidOfTheTrowel() = card(
        name = "Druid of the Trowel",
        cost = "{2}{G}",
        type = "Creature — Rabbit Druid",
        pt = "2/3",
        text = "As long as you control a token, this creature gets +2/+0 and has trample.",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(one(2), one(0)), Mechanic.GainAbilities(listOf(Keyword.Trample))), affects = Affected.Objects(Objects.Self),
            condition = Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Token), you)), atLeast = 1)),
    )

    // CR 508.3d: "whenever you attack"; "that creature" is the target
    @Test
    fun perilrootHerbalist() = card(
        name = "Perilroot Herbalist",
        cost = "{2}{G}",
        type = "Creature — Rabbit Druid",
        pt = "1/4",
        text = "Whenever you attack, target creature you control gets +1/+0 until end of turn. If that creature is a token, it also gains deathtouch until end of turn.",
    ) parsesTo abilities(
        Ability.Triggered(youAttack, Instructions(targets = listOf(slot("creature", creatureYouControl)), steps = listOf(
            pump(Objects.Target("creature"), 1, 0),
            Step.If(Condition.ObjectMatches(Objects.Target("creature"), ObjectFilter.Category(ObjectCategory.Token)),
                then = listOf(gains(Objects.Target("creature"), Keyword.Deathtouch))),
        ))),
    )

    // CR 604.3: characteristic-defining
    @Test
    fun grumlaRoarOfTheGlade() = card(
        name = "Grumla, Roar of the Glade",
        cost = "{4}{G}{G}",
        type = "Legendary Creature — Elemental Bear",
        pt = "*/*",
        text = """
            Reach, vigilance
            Grumla's power and toughness are each equal to the number of lands you control.
            When Grumla enters, mill four cards. Then return all land cards from your graveyard to the battlefield tapped.
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Vigilance,
        run {
            val lands = Value.Count(Objects.Each(ObjectFilter.And(listOf(land, you))))
            Ability.Static(listOf(Mechanic.SetPowerToughness(lands, lands)), affects = Affected.Objects(Objects.Self), characteristicDefining = true)
        },
        whenThisEnters(
            Step.Do(Effect.Mill(Players.You, one(4))),
            Step.Do(Effect.Move(Objects.Each(ObjectFilter.And(listOf(land, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                Destination(Zone.Battlefield, tapped = true))),
        ),
    )

    // CR 604.3
    @Test
    fun warrenguardTutor() = card(
        name = "Warrenguard Tutor",
        cost = "{G}{W}",
        type = "Creature — Rabbit Soldier",
        pt = "*/*",
        text = """
            Trample
            Warrenguard Tutor's power and toughness are each equal to the number of creatures you control.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        run {
            val creatures = Value.Count(Objects.Each(creatureYouControl))
            Ability.Static(listOf(Mechanic.SetPowerToughness(creatures, creatures)), affects = Affected.Objects(Objects.Self), characteristicDefining = true)
        },
    )

    @Test
    fun elderOfTheFarmstead() = card(
        name = "Elder of the Farmstead",
        cost = "{3}{G/W}{G/W}",
        type = "Creature — Rabbit Citizen",
        pt = "3/2",
        text = "When this creature enters, create two 1/1 white Rabbit creature tokens.",
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.CreateToken(rabbitToken, one(2), controller = Players.You))),
    )

    @Test
    fun marshOracle() = card(
        name = "Marsh Oracle",
        cost = "{G/U}{G/U}",
        type = "Creature — Frog Advisor",
        pt = "1/1",
        text = "When this creature enters, draw a card.",
    ) parsesTo abilities(whenThisEnters(draw()))

    // CR 601.2f
    @Test
    fun squallcatchTutor() = card(
        name = "Squallcatch Tutor",
        cost = "{U}{R}",
        type = "Creature — Otter Wizard",
        pt = "1/1",
        text = """
            Haste
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Instant and sorcery spells you cast cost {1} less to cast.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Keyword.Prowess,
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.And(listOf(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
            ObjectFilter.Category(ObjectCategory.Spell), you)), CostChange.Less(arcanum.api.ManaCost.parse("{1}")!!, one()))), affects = Affected.Objects(Objects.Self)),
    )

    @Test
    fun galeFisher() = card(
        name = "Gale Fisher",
        cost = "{1}{U/R}{U/R}",
        type = "Creature — Otter Wizard",
        pt = "2/2",
        text = "Whenever you cast a noncreature spell, put a +1/+1 counter on this creature.",
    ) parsesTo abilities(
        Ability.Triggered(youCast(noncreature), steps(counters(Objects.Self))),
    )

    // CR 701.23: the found card is revealed, the rest shuffled, then it goes on top
    @Test
    fun harbortownChime() = card(
        name = "Harbortown Chime",
        cost = "{1}",
        type = "Artifact",
        text = """
            When this artifact enters, you may search your library for a basic land card, reveal it, then shuffle and put that card on top.
            {1}, Sacrifice this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.May(listOf(
            Step.Do(Effect.Search(Players.You, ObjectFilter.And(listOf(ObjectFilter.Super(Supertype.Basic), land)), one(), reveal = true, zones = listOf(Zone.Library),
                bind = "found", searcher = Players.You)),
            Step.Do(Effect.Shuffle(Players.You, except = Objects.Bound("found"))),
            Step.Do(Effect.Move(Objects.Bound("found"), Destination(Zone.Library, libraryPosition = 0))),
        ), player = Players.You)),
        Ability.Activated(Cost(listOf(mana("{1}"), CostPart.Sacrifice(CostObjects.Self))), steps(draw()), yourAbilities),
    )

    // CR 702.73a: changeling; CR 602.5b: once each turn
    @Test
    fun twinOakMascot() = card(
        name = "Twin Oak Mascot",
        cost = "{2}",
        type = "Artifact Creature — Shapeshifter",
        pt = "2/1",
        text = """
            Changeling (This card is every creature type.)
            {1}: Add one mana of any color. Activate only once each turn.
        """,
    ) parsesTo abilities(
        Keyword.Changeling,
        Ability.Activated(Cost(listOf(mana("{1}"))), steps(anyColor()), ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, perTurn = 1)),
    )

    @Test
    fun harbortown() = card(
        name = "Harbortown",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {2}, {T}, Sacrifice a token: Draw a card.
            {3}, {T}, Pay 1 life: Create a 1/1 blue Fish creature token.
            {4}, {T}: Create a Treasure token.
        """,
    ) parsesTo abilities(
        Ability.Activated(Cost(listOf(CostPart.TapSelf)), steps(colorless()), yourAbilities),
        Ability.Activated(Cost(listOf(mana("{2}"), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Token), one())))),
            steps(draw()), yourAbilities),
        Ability.Activated(Cost(listOf(mana("{3}"), CostPart.TapSelf, CostPart.PayLife(one()))),
            steps(Step.Do(Effect.CreateToken(fishToken, one(), controller = Players.You))), yourAbilities),
        Ability.Activated(Cost(listOf(mana("{4}"), CostPart.TapSelf)),
            steps(Step.Do(Effect.CreateToken(TokenSpec.Predefined("Treasure"), one(), controller = Players.You))), yourAbilities),
    )

    @Test
    fun secretHollow() = card(
        name = "Secret Hollow",
        cost = null,
        type = "Land",
        text = """
            When this land enters, surveil 1. (Look at the top card of your library. You may put it into your graveyard.)
            {T}: Add {C}.
            {1}, {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        whenThisEnters(Step.Do(Effect.Surveil(Players.You, one()))),
        Ability.Activated(Cost(listOf(CostPart.TapSelf)), steps(colorless()), yourAbilities),
        Ability.Activated(Cost(listOf(mana("{1}"), CostPart.TapSelf)), steps(anyColor()), yourAbilities),
    )

    // CR 702.108b: several instances of prowess each trigger
    @Test
    fun wrenTidalRogue() = card(
        name = "Wren, Tidal Rogue",
        cost = "{2}{U}{R}",
        type = "Legendary Creature — Otter Rogue",
        pt = "3/3",
        text = """
            Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
            Other creatures you control have prowess. (If a creature has multiple instances of prowess, each triggers separately.)
            Whenever you cast a noncreature spell, target creature you control can't be blocked this turn.
        """,
    ) parsesTo abilities(
        Keyword.Prowess,
        Ability.Static(listOf(Mechanic.GainAbilities(listOf(Keyword.Prowess))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, you))))),
        Ability.Triggered(youCast(noncreature), Instructions(targets = listOf(slot("creature", creatureYouControl)), steps = listOf(
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CantBeBlocked), Affected.Objects(Objects.Target("creature")), Duration.EndOfTurn))))),
    )
}
