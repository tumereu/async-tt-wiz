package arcanum.parser.sets.inr

import arcanum.api.Ability
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Effect
import arcanum.api.FaceDefinition
import arcanum.api.Each
import arcanum.api.EventRole
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * INR chunk 09: effects first met here ("if an opponent lost life this turn, it deals X damage to up to one target", "creatures and
 * nonbasic lands … enter tapped", {0} for Zombie creature spells, reveal until a basic land, lands from hand tapped, flicker, cards
 * exiled with it returned, "its controller sacrifices it unless they pay X life", edicts and piles, "can't cause you to sacrifice",
 * a search for X plus the sacrificed creature's mana value, copies of each token, "untap that permanent", madness for cards you own)
 * and cast triggers (from your graveyard, another Vampire spell from the command zone).
 */
class InrChunk09EffectsAndCastTriggersTest {
    private val planeswalker = ObjectFilter.Type(CardType.Planeswalker)
    private val nonbasicLand = and(ObjectFilter.Not(ObjectFilter.Super(Supertype.Basic)), land)
    private val exiled = Objects.Bound("exiled")
    private val vampire = sub("Vampire")

    // CR 603.4: an opponent's life loss this turn is checked as it triggers and as it resolves; CR 107.3c: X is counted on resolution
    @Test
    fun crimsonHedgeArcher() = card(
        name = "Crimson Hedge Archer",
        cost = "{2}{R}",
        type = "Creature — Vampire Archer",
        pt = "2/2",
        text = "When this creature enters, if an opponent lost life this turn, it deals X damage to up to one target creature or planeswalker, where X is the number of Vampires you control.",
    ) parsesTo abilities(
        triggered(entersSelf, damage(count(and(vampire, you)), toObjects(target("permanent"))),
            targets = listOf(slot("permanent", or(creature, planeswalker), min = 0)),
            interveningIf = Condition.HappenedThisTurn(EventPattern.LoseLife(PlayerFilter.Opponent), atLeast = 1, measure = Measure.Events)),
    )

    // CR 614.1c, 614.12: a creature or nonbasic land an opponent controls enters tapped, as it would exist on the battlefield
    @Test
    fun wardkeeperOfTheGate() = card(
        name = "Wardkeeper of the Gate",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "3/2",
        text = """
            First strike
            Creatures and nonbasic lands your opponents control enter tapped.
        """,
    ) parsesTo abilities(
        Keyword.FirstStrike,
        static(Mechanic.Replacement(EventPattern.ZoneChange(and(or(creature, nonbasicLand), opponents), to = Zone.Battlefield),
            modify = listOf(Modification.EntersTapped))),
    )

    // CR 118.9: an alternative cost for only the Zombie creature spells its controller casts
    @Test
    fun gravecallTempest() = card(
        name = "Gravecall Tempest",
        cost = "{5}{U}",
        type = "Enchantment",
        text = "You may pay {0} rather than pay the mana cost for Zombie creature spells you cast.",
    ) parsesTo abilities(
        static(Mechanic.AlternativeCost(cost = Cost.mana(ManaCost.parse("{0}")!!)),
            affects = Objects.Each(and(sub("Zombie"), creature, ObjectFilter.WasCast()))),
    )

    // CR 701.20a: revealed until a basic land card; that card into the hand, every other card revealed into the graveyard
    @Test
    fun mossboundRecluse() = card(
        name = "Mossbound Recluse",
        cost = "{1}{G}",
        type = "Creature — Human Druid",
        pt = "1/1",
        text = "{G}, {T}: Reveal cards from the top of your library until you reveal a basic land card. Put that card into your hand and all other cards revealed this way into your graveyard.",
    ) parsesTo abilities(
        activated(cost(mana("{G}"), tap),
            doing(Effect.RevealUntil(basicLand(), Players.You, n(1), reveal = true, foundBind = "found", restBind = "rest")),
            move(Objects.Bound("found"), Zone.Hand),
            move(Objects.Bound("rest"), Zone.Graveyard)),
    )

    // CR 606.4 (loyalty abilities), 305.2 (lands put onto the battlefield aren't played), 604.3 (the token's P/T is a characteristic-defining
    // ability), 114.1 (the emblem)
    @Test
    fun bramblewoodSeer() = card(
        name = "Bramblewood Seer",
        cost = "{3}{G}{G}",
        type = "Legendary Planeswalker — Bramble",
        loyalty = "5",
        text = """
            [+1]: Reveal the top four cards of your library. Put all land cards revealed this way into your hand and the rest into your graveyard.
            [0]: Put any number of land cards from your hand onto the battlefield tapped.
            [−3]: Create a green Treefolk creature token with reach and "This token's power and toughness are each equal to the number of lands you control."
            [−8]: Return all permanent cards from your graveyard to your hand. You get an emblem with "You have no maximum hand size."
        """,
    ) parsesTo abilities(
        loyalty(1,
            doing(Effect.Reveal(Objects.LibraryCards(Players.You, n(4), fromTop = true), bind = "seen")),
            doing(Effect.Move(Objects.Where(Objects.Bound("seen"), land), arcanum.api.Destination(Zone.Hand))),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Where(Objects.Bound("seen"), land)), arcanum.api.Destination(Zone.Graveyard)))),
        loyalty(0,
            Step.Choose(Choice.Objects(and(land, inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), min = n(0), max = null),
                bind = "chosen", chooser = Players.You),
            move(Objects.Bound("chosen"), Zone.Battlefield, tapped = true)),
        loyalty(-3,
            create(TokenSpec.Defined(FaceDefinition("Treefolk Token", colorIndicator = listOf(Color.Green), types = listOf(CardType.Creature),
                subtypes = listOf(Subtype("Treefolk")), power = PrintedNumber.Star(), toughness = PrintedNumber.Star(), abilities = listOf(Keyword.Reach,
                static(Mechanic.SetPowerToughness(count(and(land, you)), count(and(land, you))), characteristicDefining = true)))))),
        loyalty(-8,
            move(Objects.Each(and(permanentCard, inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))), Zone.Hand),
            doing(Effect.CreateEmblem(listOf(playerStatic(Mechanic.NoMaximumHandSize)), Players.You))),
    )

    // CR 400.7: the card returned is a new object; CR 603.3d: the target is chosen as it triggers, "you may" decided on resolution
    @Test
    fun mirroredCabinet() = card(
        name = "Mirrored Cabinet",
        cost = "{5}",
        type = "Artifact",
        text = "At the beginning of your end step, you may exile target creature you control, then return that card to the battlefield under your control.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You),
            Step.May(listOf(move(target("creature"), Zone.Exile, postBind = "exiled"), move(exiled, Zone.Battlefield, controller = Players.You)), Players.You),
            targets = listOf(slot("creature", and(creature, you)))),
    )

    // CR 607.2a: the cards its exile abilities exiled are the cards "exiled with it", returned under their owners' control (CR 110.2a)
    @Test
    fun sealedReliquary() = card(
        name = "Sealed Reliquary",
        cost = "{3}",
        type = "Legendary Artifact",
        text = """
            {1}, {T}: Exile target creature you control.
            {7}, {T}: Exile target creature you don't control.
            When Sealed Reliquary is put into a graveyard from the battlefield, return all cards exiled with it to the battlefield under their owners' control.
        """,
    ) parsesTo abilities(
        activated(cost(mana("{1}"), tap), move(target("creature"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", exiled)),
            targets = listOf(slot("creature", and(creature, you)))),
        activated(cost(mana("{7}"), tap), move(target("creature"), Zone.Exile, postBind = "exiled"), doing(Effect.Remember("exiled", exiled)),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You)))))),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
            move(Objects.Remembered("exiled"), Zone.Battlefield, controller = Players.OwnerOf(Objects.Remembered("exiled")))),
    )

    // CR 702.8a (flash), 400.7: the non-Angel creature returns as a new object under your control
    @Test
    fun dawnwingRescuer() = card(
        name = "Dawnwing Rescuer",
        cost = "{3}{W}",
        type = "Creature — Angel",
        pt = "3/4",
        text = """
            Flash
            Flying
            When this creature enters, you may exile target non-Angel creature you control, then return that card to the battlefield under your control.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Flying,
        triggered(entersSelf,
            Step.May(listOf(move(target("creature"), Zone.Exile, postBind = "exiled"), move(exiled, Zone.Battlefield, controller = Players.You)), Players.You),
            targets = listOf(slot("creature", and(not(sub("Angel")), creature, you)))),
    )

    // CR 101.4, 118.12a, 119.4: each creature's controller decides whether to pay X life for it, then the rest are sacrificed at once
    @Test
    fun tideOfCulling() = card(
        name = "Tide of Culling",
        cost = "{X}{B}",
        type = "Sorcery",
        text = "For each creature, its controller sacrifices it unless they pay X life.",
    ) parsesTo abilities(
        spell(Step.ForEach(Each.Objects(Objects.Each(creature)), "creature", together = true, steps = listOf(
            Step.Pay(Cost(listOf(CostPart.PayLife(Value.Announced("X")))), payer = Players.ControllerOf(Objects.Bound("creature")), bind = "paid"),
            ifThen(Condition.Not(Condition.Did("paid")), doing(Effect.Sacrifice(Objects.Bound("creature")))),
        )), announced = listOf("X")),
    )

    // CR 701.21a (the player chooses what to sacrifice), 700.3 (you separate, that player chooses a pile)
    @Test
    fun veiledEdictmistress() = card(
        name = "Veiled Edictmistress",
        cost = "{1}{B}{B}",
        type = "Legendary Planeswalker — Edict",
        loyalty = "3",
        text = """
            [+1]: Each player discards a card.
            [−2]: Target player sacrifices a creature.
            [−6]: Separate all permanents target player controls into two piles. That player sacrifices all permanents in the pile of their choice.
        """,
    ) parsesTo abilities(
        loyalty(1, doing(Effect.Discard(Players.Each(PlayerFilter.Any), n(1), filter = ObjectFilter.Any))),
        loyalty(-2,
            Step.Choose(Choice.Objects(and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player")))), n(1), n(1)),
                bind = "sacrificed", chooser = Players.Target("player")),
            doing(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            targets = listOf(targetPlayer)),
        loyalty(-6,
            doing(Effect.SeparatePiles(Objects.Each(and(permanentFilter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))),
                separator = Players.You, chooser = Players.Target("player"), chosenBind = "chosen pile")),
            doing(Effect.Sacrifice(Objects.Bound("chosen pile"))),
            targets = listOf(targetPlayer)),
    )

    // CR 701.21a: a permanent that can't be sacrificed isn't; only an opponent's spells and abilities are stopped
    @Test
    fun selvalaSkywardMother() = card(
        name = "Selvala Skyward Mother",
        cost = "{2}{G}{W}{W}",
        type = "Creature — Angel",
        pt = "5/5",
        text = """
            Flying, hexproof
            Spells and abilities your opponents control can't cause you to sacrifice permanents.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Hexproof,
        static(Mechanic.CantBeSacrificedBy(PlayerFilter.Opponent), affects = Objects.Each(and(permanentFilter, you))),
    )

    // CR 601.2b, 608.2h: the sacrificed creature's mana value as it last existed; CR 701.23a: the search
    @Test
    fun primalAscension() = card(
        name = "Primal Ascension",
        cost = "{1}{G}{G}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature.
            Search your library for a creature card with mana value X or less, where X is 2 plus the sacrificed creature's mana value. Put that card onto the battlefield, then shuffle. Exile Primal Ascension.
        """,
    ) parsesTo abilities(
        static(Mechanic.AdditionalCost(cost = cost(sacrificeA(creature, preBind = "sacrificed")))),
        spell(
            search(and(creature, ObjectFilter.ManaValue(arcanum.api.Comparison.AtMost,
                Value.Sum(listOf(n(2), Value.Of(NumericProperty.ManaValue, Objects.Bound("sacrificed"), Combine.Single)))))),
            move(Objects.Bound("found"), Zone.Battlefield),
            shuffle,
            move(Objects.Self, Zone.Exile)),
    )

    // CR 707.2, 111.1: one token copy of each token you control as it resolves
    @Test
    fun doubledBounty() = card(
        name = "Doubled Bounty",
        cost = "{2}{G}{G}",
        type = "Instant",
        text = "For each token you control, create a token that's a copy of that permanent.",
    ) parsesTo abilities(
        spell(Step.ForEach(Each.Objects(Objects.Each(and(token, you))), "permanent", listOf(
            doing(Effect.CreateToken(TokenSpec.CopyOf(Objects.Bound("permanent")), n(1), Players.You))))),
    )

    // CR 614.1c (enters tapped), 502.3 (doesn't untap), 701.21a (sacrifice cost); "it" after "Untap Grimvault" is this creature
    @Test
    fun grimvaultColossus() = card(
        name = "Grimvault Colossus",
        cost = "{3}{U}{B}",
        type = "Legendary Creature — Zombie Warrior",
        pt = "5/5",
        text = """
            Grimvault Colossus enters tapped and doesn't untap during your untap step.
            Sacrifice another creature: Untap Grimvault Colossus and put a +1/+1 counter on it.
            Whenever Grimvault Colossus attacks, destroy target creature defending player controls, then put a +1/+1 counter on Grimvault Colossus.
        """,
    ) parsesTo abilities(
        entersTapped,
        static(Mechanic.DoesntUntap),
        activated(cost(sacrificeA(and(another, creature))), untapIt(Objects.Self), counters(Objects.Self)),
        triggered(attacksSelf, destroy(target("creature")), counters(Objects.Self),
            targets = listOf(slot("creature", and(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))))))),
    )

    // CR 702.10 (haste), 613.1b (control changed until end of turn); "that permanent" is the target
    @Test
    fun ferventTurncoats() = card(
        name = "Fervent Turncoats",
        cost = "{4}{R}",
        type = "Creature — Human Warrior",
        pt = "3/3",
        text = """
            Haste
            When this creature enters, gain control of target permanent until end of turn. Untap that permanent. It gains haste until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(entersSelf,
            untilEndOfTurn(target("permanent"), Mechanic.ControlledBy(Players.You)),
            untapIt(target("permanent")),
            gains(target("permanent"), Keyword.Haste),
            targets = listOf(slot("permanent", permanentFilter))),
    )

    // CR 702.35a: madness works for the card as it is discarded from the hand, so each Vampire creature card you own outside the battlefield has it
    @Test
    fun bloodlineInstigator() = card(
        name = "Bloodline Instigator",
        cost = "{R}",
        type = "Creature — Vampire Berserker",
        pt = "2/1",
        text = "Each Vampire creature card you own that isn't on the battlefield has madness. The madness cost is equal to its mana cost. (If you discard a card with madness, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)",
    ) parsesTo abilities(
        static(Mechanic.GainAbilities(listOf(Keyword.Madness(Cost(listOf(CostPart.ItsManaCost))))),
            affects = Objects.Each(and(vampire, creature, ObjectFilter.OwnedBy(PlayerFilter.You), not(inZone(Zone.Battlefield))))),
    )

    // CR 601.2a: a spell cast from the graveyard (flashback, CR 702.34a) triggers it as it becomes cast
    @Test
    fun pyreOfReprisal() = card(
        name = "Pyre of Reprisal",
        cost = "{2}{R}",
        type = "Enchantment",
        text = "Whenever you cast a spell from your graveyard, this enchantment deals 2 damage to any target.",
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You, from = Zone.Graveyard)), damage(2, toAny), targets = listOf(anyTarget)),
    )

    // CR 207.2c (eminence is an ability word), 113.6b (it works in the command zone and on the battlefield), 603.4 (checked again on resolution)
    @Test
    fun sanguinePatriarch() = card(
        name = "Sanguine Patriarch",
        cost = "{3}{R}{W}{B}",
        type = "Legendary Creature — Vampire Knight",
        pt = "4/4",
        text = """
            Eminence — Whenever you cast another Vampire spell, if Sanguine Patriarch is in the command zone or on the battlefield, create a 1/1 black Vampire creature token.
            First strike, haste
            Whenever Sanguine Patriarch attacks, put a +1/+1 counter on each Vampire you control.
        """,
    ) parsesTo abilities(
        triggered(on(EventPattern.Cast(and(another, vampire), PlayerFilter.You)), create(creatureToken("Vampire", listOf(Color.Black), 1, 1)),
            interveningIf = Condition.ObjectMatches(Objects.Self, or(inZone(Zone.Command), inZone(Zone.Battlefield))),
            abilityWord = "Eminence", functionsIn = listOf(Zone.Command, Zone.Battlefield)),
        Keyword.FirstStrike,
        Keyword.Haste,
        triggered(attacksSelf, counters(Objects.Each(and(vampire, you)))),
    )

    // CR 115.7 (a spell that targets only this creature), 707.10d (a copy for each other creature its caster controls that the spell could
    // target, each copy targeting that creature)
    @Test
    fun glasswingWyrm() = card(
        name = "Glasswing Wyrm",
        cost = "{3}{R}{R}",
        type = "Creature — Dragon",
        pt = "4/5",
        text = """
            Flying
            Whenever a player casts an instant or sorcery spell that targets only this creature, that player copies that spell for each other creature they control that the spell could target. Each copy targets a different one of those creatures.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(on(EventPattern.Cast(and(or(instant, sorcery), spellFilter, ObjectFilter.SingleTarget(ObjectFilter.Is(Objects.Self))), PlayerFilter.Any)),
            doing(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.EventPlayer, newTargets = false,
                forEachCouldTarget = Objects.Each(and(another, creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.EventPlayer))))))),
    )
}
