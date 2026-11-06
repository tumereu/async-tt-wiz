package arcanum.parser.sets.ltr

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Timing
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * The Lord of the Rings chunk 14: artifacts and nonbasic lands. Protection from everything for a player until their next turn
 * (CR 702.16k), burden counters, tokens attacking by a condition, losing all abilities when blocking or blocked (CR 509), a target with
 * power X, a free spell by the combat damage dealt, shuffling a card into its owner's library from the graveyard, an opponent's choice
 * to let you draw (CR 101.4), draw and life gain replacements by your hand and life (CR 614.1a), untapping at each combat, nonbasic
 * landwalk (CR 702.14), a creature an opponent controls exiled instead of dying, conditional untapped lands, mana by the colours of
 * legendary creature cards in your graveyard (CR 106.7) and choosing creatures to survive a wrath.
 */
class LtrChunk14ArtifactsAndLandsTest {
    // CR 702.16k: protection from everything for a player; burden counters
    @Test
    fun theRulingRing() = card(
        name = "The Ruling Ring",
        cost = "{4}",
        type = "Legendary Artifact",
        text = """
            Indestructible
            When The Ruling Ring enters, if you cast it, you gain protection from everything until your next turn.
            At the beginning of your upkeep, you lose 1 life for each burden counter on The Ruling Ring.
            {T}: Put a burden counter on The Ruling Ring, then draw a card for each burden counter on The Ruling Ring.
        """,
    ) parsesTo abilities(
        Keyword.Indestructible,
        triggered(entersSelf, doing(Effect.ApplyMechanics(listOf(has(Keyword.Protection(ObjectFilter.Any))), Affected.Players(Players.You), Duration.UntilYourNextTurn)),
            interveningIf = Condition.CastFrom(null, Objects.Self)),
        triggered(stepBegins(TurnStep.Upkeep, PlayerFilter.You), doing(Effect.LoseLife(Players.You, burdens))),
        activated(cost(tap), counters(Objects.Self, kind = burden), draw(burdens)),
    )

    // CR 508.4: tapped and attacking only if the equipped creature is legendary
    @Test
    fun narsilFlameOfTheWest() = card(
        name = "Narsil, Flame of the West",
        cost = "{3}",
        type = "Legendary Artifact — Equipment",
        text = """
            Equipped creature gets +3/+1.
            Whenever equipped creature attacks, create two tapped 1/1 white Spirit creature tokens with flying. If that creature is legendary, instead create two of those tokens that are tapped and attacking.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(3, 1), affects = Objects.AttachedTo(Objects.Self)),
        triggered(on(EventPattern.Attack(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)))),
            Step.If(Condition.ObjectMatches(Objects.AttachedTo(Objects.Self), legendary),
                then = listOf(doing(Effect.CreateToken(spiritFlyer, n(2), Players.You, tapped = true, attacking = arcanum.api.AttackTarget.Chosen))),
                otherwise = listOf(doing(Effect.CreateToken(spiritFlyer, n(2), Players.You, tapped = true))))),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 509: the creature it blocks or that blocks it loses all abilities
    @Test
    fun barrowDagger() = card(
        name = "Barrow-Dagger",
        cost = "{1}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1.
            Whenever equipped creature blocks or becomes blocked by a creature, that creature loses all abilities until end of turn.
            Equip {1} ({1}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(pt(1, 1), affects = Objects.AttachedTo(Objects.Self)),
        triggered(arcanum.api.Trigger.On(listOf(EventPattern.Block(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), creature, perAttacker = true),
            EventPattern.BecomesBlocked(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), creature, perBlocker = true))),
            untilEndOfTurn(Objects.EventObject(EventRole.OtherCombatant), Mechanic.LoseAbilities())),
        Keyword.Equip(manaCost("{1}")),
    )

    // CR 107.3a: a target with power X
    @Test
    fun entWaterBasin() = card(
        name = "Ent-Water Basin",
        cost = "{2}",
        type = "Artifact",
        text = "{X}, {T}: Put a +1/+1 counter on target creature with power X. Activate only as a sorcery.",
    ) parsesTo abilities(
        Ability.Activated(cost(mana("{X}"), tap), Instructions(targets = listOf(slot("creature", and(creature, ObjectFilter.Power(Comparison.Equal, Value.Announced(name = "X"))))),
            steps = listOf(counters(target("creature"))), announced = listOf("X")), asSorcery),
    )

    // CR 601.2: a spell from hand by the combat damage dealt
    @Test
    fun orcrist() = card(
        name = "Orcrist",
        cost = "{2}",
        type = "Legendary Artifact — Equipment",
        text = """
            Equipped creature has first strike and gets +1/+0 for each instant and sorcery card in your graveyard.
            Whenever equipped creature deals combat damage to a player, you may cast an instant or sorcery spell from your hand with mana value less than or equal to that damage without paying its mana cost.
            Equip {3}
        """,
    ) parsesTo abilities(
        static(has(Keyword.FirstStrike), Mechanic.ModifyPowerToughness(spellsInYourGraveyard, n(0)), affects = Objects.AttachedTo(Objects.Self)),
        triggered(on(EventPattern.Damage(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            Step.Choose(Choice.Objects(and(or(instant, sorcery), ObjectFilter.ManaValue(Comparison.AtMost, Value.EventAmount), inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                n(0), n(1)), bind = "card", chooser = Players.You),
            doing(Effect.Cast(Objects.Bound("card"), PlayKind.Cast, withoutPaying = true, caster = Players.You))),
        Keyword.Equip(manaCost("{3}")),
    )

    @Test
    fun hornOfTheSteward() = card(
        name = "Horn of the Steward",
        cost = "{3}",
        type = "Legendary Artifact",
        text = """
            When Horn of the Steward enters, create a 1/1 white Human Soldier creature token.
            {3}, {T}: Create X 1/1 white Human Soldier creature tokens, where X is the number of Humans you control.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, create(humanSoldier)),
        activated(cost(mana("{3}"), tap), create(humanSoldier, count(and(sub("Human"), you)))),
    )

    // CR 508.1: two or more attackers; the top five looked at
    @Test
    fun hornOfTheRiders() = card(
        name = "Horn of the Riders",
        cost = "{2}",
        type = "Legendary Artifact",
        text = "Whenever two or more creatures you control attack a player, look at the top five cards of your library. You may reveal a creature card from among them and put it into your hand. Put the rest on the bottom of your library in a random order.",
    ) parsesTo abilities(
        triggered(twoOrMoreAttackAPlayer,
            doing(Effect.LookAt(Objects.LibraryCards(Players.You, n(5), fromTop = true), Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(creature, n(0), n(1), from = Objects.Bound("seen")), bind = "chosen", chooser = Players.You),
            doing(Effect.Reveal(Objects.Bound("chosen"))), move(Objects.Bound("chosen"), Zone.Hand),
            doing(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("chosen")), Destination(Zone.Library, libraryPosition = -1, randomOrder = true)))),
    )

    @Test
    fun inheritedParcel() = card(
        name = "Inherited Parcel",
        cost = "{3}",
        type = "Artifact",
        text = """
            When this artifact enters, the Ring tempts you.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, ringTempts),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
    )

    // CR 400.7: the card in the graveyard is shuffled into its owner's library
    @Test
    fun waybread() = card(
        name = "Waybread",
        cost = "{2}",
        type = "Artifact — Food",
        text = """
            When this artifact enters, scry 1, then draw a card.
            {2}, {T}, Sacrifice this artifact: You gain 3 life.
            When this artifact is put into a graveyard from the battlefield, its owner shuffles it into their library.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, scry(1), draw(1)),
        activated(cost(mana("{2}"), tap, sacrificeSelf), gainLife(3)),
        triggered(diesSelf, move(Objects.EventObject(EventRole.After), Zone.Library), doing(Effect.Shuffle(Players.OwnerOf(Objects.EventObject(EventRole.After))))),
    )

    @Test
    fun mirrorOfTheLady() = card(
        name = "Mirror of the Lady",
        cost = "{2}",
        type = "Legendary Artifact",
        text = "{5}, {T}: Scry 1, then draw a card. This ability costs {1} less to activate for each legendary creature you control.",
    ) parsesTo abilities(
        activated(cost(mana("{5}"), tap), scry(1), draw(1), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You,
            costChange = arcanum.api.CostChange.Less(ManaCost.parse("{1}")!!, count(and(legendary, creature, you))))),
    )

    @Test
    fun mithrilShirt() = card(
        name = "Mithril Shirt",
        cost = "{3}",
        type = "Legendary Artifact — Equipment",
        text = """
            Flash
            Indestructible
            When Mithril Shirt enters, attach it to target legendary creature you control.
            Equipped creature has indestructible.
            Equip {3}
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Indestructible,
        triggered(entersSelf, doing(Effect.Attach(Objects.Self, Recipients(objects = target("creature")))), targets = listOf(slot("creature", and(legendary, creature, you)))),
        static(has(Keyword.Indestructible), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{3}")),
    )

    // CR 101.4: the opponent chooses whether Alice draws; otherwise mill and lose life by the milled cards' total mana value
    @Test
    fun seeingStoneOfTheTower() = card(
        name = "Seeing-Stone of the Tower",
        cost = "{3}",
        type = "Legendary Artifact",
        text = "At the beginning of your end step, put an influence counter on Seeing-Stone of the Tower and scry 2. Then target opponent may have you draw a card. If that player doesn't, you mill X cards, where X is the number of influence counters on Seeing-Stone of the Tower, and that player loses life equal to the total mana value of those cards.",
    ) parsesTo abilities(
        triggered(stepBegins(TurnStep.End, PlayerFilter.You), counters(Objects.Self, kind = influence), scry(2),
            Step.Pay(steps = listOf(draw(1)), payer = Players.Target("opponent"), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(
                doing(Effect.Mill(Players.You, Value.Of(NumericProperty.Counters(influence), Objects.Self, Combine.Single), postBind = "milled")),
                doing(Effect.LoseLife(Players.Target("opponent"), Value.Of(NumericProperty.ManaValue, Objects.Bound("milled"), Combine.Total))))),
            targets = listOf(targetOpponent)),
    )

    // CR 614.1a: two cards instead of one with an empty hand; twice the life at 5 or less
    @Test
    fun phialOfTheLady() = card(
        name = "Phial of the Lady",
        cost = "{3}",
        type = "Legendary Artifact",
        text = """
            If you would draw a card while you have no cards in hand, draw two cards instead.
            If you would gain life while you have 5 or less life, you gain twice that much life instead.
            {T}: Add one mana of any color.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.Draw(PlayerFilter.You), instead = Instructions(steps = listOf(draw(2)))),
            condition = Condition.Not(Condition.Exists(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 1))),
        static(Mechanic.Replacement(EventPattern.GainLife(PlayerFilter.You), modify = listOf(Modification.MultiplyAmount(2))),
            condition = Condition.Compare(Value.LifeOf(Players.You), Comparison.AtMost, n(5))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You))),
    )

    @Test
    fun farmsteadScarecrow() = card(
        name = "Farmstead Scarecrow",
        cost = "{2}",
        type = "Artifact Creature — Scarecrow",
        text = """
            Defender
            {1}: Add one mana of any color. Activate only once each turn.
        """,
        pt = "0/3",
    ) parsesTo abilities(
        Keyword.Defender,
        activated(cost(mana("{1}")), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1))), Players.You)),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, perTurn = 1)),
    )

    // untapped at each combat; first strike while blocking or blocked by a Goblin or Orc
    @Test
    fun dartbladeTheGlintingDagger() = card(
        name = "Dartblade, the Glinting Dagger",
        cost = "{2}",
        type = "Legendary Artifact — Equipment",
        text = """
            Equipped creature gets +1/+1 and has haste.
            At the beginning of each combat, untap equipped creature.
            Equipped creature has first strike as long as it's blocking or blocked by a Goblin or Orc.
            Equip {2}
        """,
    ) parsesTo abilities(
        static(pt(1, 1), has(Keyword.Haste), affects = Objects.AttachedTo(Objects.Self)),
        triggered(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any), untapIt(Objects.AttachedTo(Objects.Self))),
        static(has(Keyword.FirstStrike), affects = Objects.AttachedTo(Objects.Self), condition = Condition.ObjectMatches(Objects.AttachedTo(Objects.Self), or(
            ObjectFilter.Blocking(Objects.Each(goblinOrOrc)), ObjectFilter.BlockedBy(Objects.Each(goblinOrOrc))))),
        Keyword.Equip(manaCost("{2}")),
    )

    // CR 614.1a: an opponent's creature exiled instead of dying
    @Test
    fun stoneOfTheOath() = card(
        name = "Stone of the Oath",
        cost = "{1}",
        type = "Legendary Artifact",
        text = """
            If a creature an opponent controls would die, exile it instead.
            {2}, {T}, Sacrifice Stone of the Oath: Exile target player's graveyard. Draw a card.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(creatureOpponentControls, from = Zone.Battlefield, to = Zone.Graveyard),
            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))))),
        activated(cost(mana("{2}"), tap, sacrificeSelf), move(Objects.Each(and(inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))), Zone.Exile),
            draw(1), targets = listOf(targetPlayer)),
    )

    // CR 702.6a: equip Halfling; attacking alone draws
    @Test
    fun bungosRing() = card(
        name = "Bungo's Ring",
        cost = "{3}",
        type = "Legendary Artifact — Equipment",
        text = """
            During your turn, equipped creature has hexproof and can't be blocked.
            Whenever equipped creature attacks alone, you draw a card and you lose 1 life.
            Equip Halfling {1} ({1}: Attach to target Halfling you control. Equip only as a sorcery.)
            Equip {4} ({4}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        static(has(Keyword.Hexproof), Mechanic.CantBeBlocked, affects = Objects.AttachedTo(Objects.Self), condition = Condition.YourTurn),
        triggered(on(EventPattern.Attack(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), alone = true)), draw(1), loseLife(1)),
        Keyword.Equip(manaCost("{1}"), onto = sub("Halfling")),
        Keyword.Equip(manaCost("{4}")),
    )

    // CR 702.14: nonbasic landwalk
    @Test
    fun pathfindersBoots() = card(
        name = "Pathfinder's Boots",
        cost = "{2}",
        type = "Artifact — Equipment",
        text = """
            Equipped creature has nonbasic landwalk. (It can't be blocked as long as defending player controls a nonbasic land.)
            Equip {2}
        """,
    ) parsesTo abilities(
        static(has(Keyword.Landwalk(and(not(arcanum.api.ObjectFilter.Super(arcanum.api.Supertype.Basic)), land), "nonbasic landwalk")), affects = Objects.AttachedTo(Objects.Self)),
        Keyword.Equip(manaCost("{2}")),
    )

    // ---- Lands

    // CR 106.12: X twice in a cost; activated only if a creature died this turn
    @Test
    fun darkTowerKeep() = card(
        name = "Dark Tower Keep",
        cost = null,
        type = "Legendary Land",
        text = """
            Dark Tower Keep enters tapped unless you control a legendary creature.
            {T}: Add {B}.
            {X}{X}{B}, {T}: Amass Orcs X. Activate only if a creature died this turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
            condition = Condition.Not(Condition.Exists(and(legendary, creature, you), atLeast = 1))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{B}")!!)), Players.You))),
        Ability.Activated(cost(mana("{X}{X}{B}"), tap), Instructions(steps = listOf(amassOrcs(Value.Announced(name = "X"))), announced = listOf("X")),
            ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = Condition.HappenedThisTurn(
                EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = arcanum.api.Measure.Events))),
    )

    @Test
    fun greatHallOfTheTower() = card(
        name = "Great Hall of the Tower",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}, {T}: Add two mana in any combination of colors. Spend this mana only to cast legendary spells.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(mana("{1}"), tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(2), combination = true), restriction = and(spellFilter, legendary)), Players.You))),
    )

    // CR 106.7: a colour among legendary creature cards in your graveyard
    @Test
    fun theWesternHavens() = card(
        name = "The Western Havens",
        cost = null,
        type = "Legendary Land",
        text = """
            When The Western Havens enters, scry 1.
            {T}: Add {C}.
            {T}: Add one mana of any color among legendary creature cards in your graveyard.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, scry(1)),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), colors = listOf(ColorRef.Of(Objects.Each(inYourGraveyard(legendary, creature)))))), Players.You))),
    )

    @Test
    fun whiteTowerCity() = card(
        name = "White Tower City",
        cost = null,
        type = "Legendary Land",
        text = """
            White Tower City enters tapped unless you control a legendary creature.
            {T}: Add {W}.
            {1}{W}, {T}: Draw a card. Activate only if you attacked with two or more creatures this turn.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
            condition = Condition.Not(Condition.Exists(and(legendary, creature, you), atLeast = 1))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{W}")!!)), Players.You))),
        activated(cost(mana("{1}{W}"), tap), draw(1), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You,
            condition = Condition.HappenedThisTurn(EventPattern.Attack(creatureYouControl), atLeast = 2, measure = arcanum.api.Measure.Objects))),
    )

    @Test
    fun minesOfTheDeep() = card(
        name = "Mines of the Deep",
        cost = null,
        type = "Legendary Land",
        text = """
            Mines of the Deep enters tapped unless you control a legendary creature.
            {T}: Add {R}.
            {3}{R}, {T}, Exile three cards from your graveyard: Create two Treasure tokens.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
            condition = Condition.Not(Condition.Exists(and(legendary, creature, you), atLeast = 1))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}")!!)), Players.You))),
        activated(cost(mana("{3}{R}"), tap, CostPart.Exile(CostObjects.Choose(cardFilter, count = n(3)), from = Zone.Graveyard)), createTreasure(n(2))),
    )

    // CR 701.21: sacrificing it and a legendary artifact; up to two creatures chosen to survive
    @Test
    fun mountainOfFire() = card(
        name = "Mountain of Fire",
        cost = null,
        type = "Legendary Land",
        text = """
            {T}, Pay 1 life: Add {B} or {R}.
            {1}{B}{R}, {T}: Mountain of Fire deals 1 damage to each opponent.
            {5}{B}{R}, {T}, Sacrifice Mountain of Fire and a legendary artifact: Choose up to two creatures, then destroy the rest. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        activated(cost(tap, CostPart.PayLife(n(1))), doing(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1), colors = listOf(ColorRef.Literal(Color.Black), ColorRef.Literal(Color.Red)))), Players.You))),
        activated(cost(mana("{1}{B}{R}"), tap), damage(1, eachOpponent)),
        activated(cost(mana("{5}{B}{R}"), tap, sacrificeSelf, sacrificeA(and(legendary, artifact))),
            Step.Choose(Choice.Objects(creature, n(0), n(2)), bind = "chosen", chooser = Players.You),
            destroy(Objects.Each(and(creature, not(ObjectFilter.Is(Objects.Bound("chosen")))))), rules = asSorcery),
    )

    @Test
    fun hiddenValleyHaven() = card(
        name = "Hidden Valley Haven",
        cost = null,
        type = "Legendary Land",
        text = """
            Hidden Valley Haven enters tapped unless you control a legendary creature.
            {T}: Add {U}.
            {1}{U}, {T}: Scry 2. Activate only if you control a legendary creature.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
            condition = Condition.Not(Condition.Exists(and(legendary, creature, you), atLeast = 1))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{U}")!!)), Players.You))),
        activated(cost(mana("{1}{U}"), tap), scry(2), rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You,
            condition = Condition.Exists(and(legendary, creature, you), atLeast = 1))),
    )

    @Test
    fun theGreenShire() = card(
        name = "The Green Shire",
        cost = null,
        type = "Legendary Land",
        text = """
            The Green Shire enters tapped unless you control a legendary creature.
            {T}: Add {G}.
            {1}{G}, {T}, Tap an untapped creature you control: Create a Food token.
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield), modify = listOf(Modification.EntersTapped)),
            condition = Condition.Not(Condition.Exists(and(legendary, creature, you), atLeast = 1))),
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{G}")!!)), Players.You))),
        activated(cost(mana("{1}{G}"), tap, CostPart.TapObjects(CostObjects.Choose(creature, count = n(1)))), createFood()),
    )

    @Test
    fun hillsideTerrace() = card(
        name = "Hillside Terrace",
        cost = null,
        type = "Land",
        text = """
            {T}: Add {C}.
            {1}, {T}, Sacrifice this land: Search your library for a basic land card, put it onto the battlefield tapped, then shuffle.
        """,
    ) parsesTo abilities(
        activated(cost(tap), doing(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{C}")!!)), Players.You))),
        activated(cost(mana("{1}"), tap, sacrificeSelf), search(basicLand()), move(Objects.Bound("found"), Zone.Battlefield, tapped = true), shuffle),
    )

    private val burden = CounterKind.Named("burden")
    private val influence = CounterKind.Named("influence")
    private val burdens = Value.Of(NumericProperty.Counters(burden), Objects.Self, Combine.Single)
    private val spellsInYourGraveyard = count(and(or(instant, sorcery), inZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))
}
