package arcanum.parser.sets.fra

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.Timing
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.gtc.creature
import arcanum.parser.sets.gtc.creatureYouControl
import arcanum.parser.sets.gtc.land
import arcanum.parser.sets.gtc.token
import arcanum.parser.sets.gtc.you
import kotlin.test.Test

/**
 * Reality Fracture chunk 14: white legendary creatures and a legendary artifact. A trigger on casting an Equipment spell or a
 * spell that targets your creature, once each turn (CR 603.2h); a cost reduced by the greatest toughness (CR 601.2f); attacking
 * despite defender and assigning combat damage by toughness (CR 510.1c); restricted mana (CR 106.6); landfall and Plains
 * triggers; activation only during your turn; a counter on the target only if it is a creature, a loyalty counter only if it is
 * a planeswalker (CR 608.2c); a cost increase for opponents; one more +1/+1 counter (CR 614.1a); no spells or nonmana
 * abilities during combat; double strike for a creature attacking a player alone (CR 506.5).
 */
class FraChunk14WhiteLegendsTest {
    // CR 603.2h, 115.1
    @Test
    fun lieslBladeOfPromise() = card(
        name = "Liesl, Blade of Promise",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Knight",
        pt = "2/2",
        text = """
            First strike
            Whenever you cast an Equipment spell or a spell that targets a creature you control, draw a card. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(Keyword.FirstStrike, triggered(youCast(or(sub("Equipment"), ObjectFilter.Targets(objects = Objects.Each(creatureYouControl)))), draw(1), perTurn = 1))

    // CR 601.2f, 510.1c
    @Test
    fun brontorTheUnbudging() = card(
        name = "Brontor the Unbudging",
        cost = "{8}{W}",
        type = "Legendary Creature — Elder Dinosaur",
        pt = "0/7",
        text = """
            This spell costs {X} less to cast, where X is the greatest toughness among creatures you control.
            Creatures you control can attack as though they didn't have defender.
            Each creature you control with toughness greater than its power assigns combat damage equal to its toughness rather than its power.
        """,
    ) parsesTo abilities(
            static(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{1}")!!,
                Value.Of(NumericProperty.Toughness, Objects.Each(creatureYouControl), Combine.Greatest)))),
            static(Mechanic.CanAttackDespiteDefender, affects = Objects.Each(creatureYouControl)),
            static(Mechanic.AssignsDamageByToughness, affects = Objects.Each(and(creature, you,
                ObjectFilter.Toughness(Comparison.Greater, Value.Of(NumericProperty.Power, Objects.Tested, Combine.Single))))),
        )

    // CR 106.6
    @Test
    fun oathkeeperSMonument() = card(
        name = "Oathkeeper's Monument",
        cost = "{1}{W}",
        type = "Legendary Artifact",
        text = """
            Creature tokens you control get +1/+0 and have vigilance.
            {T}: Add one mana of any color. Spend this mana only to cast a planeswalker spell.
            {1}{W}, Discard this card: It deals 4 damage to target attacking or blocking creature.
        """,
    ) parsesTo abilities(
            static(Mechanic.ModifyPowerToughness(n(1), n(0)), Mechanic.GainAbilities(listOf(Keyword.Vigilance)), affects = Objects.Each(and(creature, token, you))),
            activated(cost(tap), Step.Do(Effect.AddMana(ManaProduction(ManaKind.Choose(n(1)), restriction = and(spellFilter, planeswalker)), Players.You))),
            activated(cost(mana("{1}{W}"), CostPart.Discard(CostObjects.Self)), damage(4, Recipients(objects = target("creature"))),
                targets = listOf(slot("creature", and(or(ObjectFilter.HasStatus(Status.Attacking), ObjectFilter.HasStatus(Status.Blocking)), creature)))),
        )

    @Test
    fun brannocOfTheHearth() = card(
        name = "Brannoc of the Hearth",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Citizen",
        pt = "2/3",
        text = """
            Landfall — Whenever a land you control enters, you gain 1 life.
            Whenever a Plains you control enters, put a +1/+1 counter on target creature.
        """,
    ) parsesTo abilities(
            triggered(on(EventPattern.ZoneChange(and(land, you), to = Zone.Battlefield)), gainLife(1), abilityWord = "Landfall"),
            triggered(on(EventPattern.ZoneChange(and(sub("Plains"), you), to = Zone.Battlefield)), counters(target("creature")), targets = listOf(slot("creature", creature))),
        )

    @Test
    fun selineTheBlameless() = card(
        name = "Seline the Blameless",
        cost = "{W}",
        type = "Legendary Creature — Human Cleric",
        pt = "1/1",
        text = """
            Whenever another creature or planeswalker you control enters, you gain 1 life.
            {1}, {T}, Discard a card: Another target creature or planeswalker you control gains hexproof until end of turn.
        """,
    ) parsesTo abilities(
            triggered(on(EventPattern.ZoneChange(and(anotherObject, creatureOrPlaneswalker, you), to = Zone.Battlefield)), gainLife(1)),
            activated(cost(mana("{1}"), tap, discardACard), gains(target("permanent"), Keyword.Hexproof),
                targets = listOf(slot("permanent", and(anotherObject, creatureOrPlaneswalker, you)))),
        )

    @Test
    fun aurielleSeraphOfDaybreak() = card(
        name = "Aurielle, Seraph of Daybreak",
        cost = "{2}{W}",
        type = "Legendary Creature — Angel Knight",
        pt = "3/3",
        text = """
            Flying
            Whenever you gain life, put a +1/+1 counter on each Angel you control.
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(youGainLife, counters(Objects.Each(and(sub("Angel"), you)))))

    // CR 602.5b
    @Test
    fun reliefMaidenSwiftRescuer() = card(
        name = "Relief Maiden, Swift Rescuer",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Cleric",
        pt = "1/3",
        text = """
            Flying
            {T}: Return another target permanent you control to its owner's hand. Activate only during your turn.
        """,
    ) parsesTo abilities(Keyword.Flying, activated(cost(tap), move(target("permanent"), Zone.Hand),
            targets = listOf(slot("permanent", and(anotherObject, permanentFilter, you))),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, condition = Condition.YourTurn)))

    // CR 603.2h
    @Test
    fun tamsinEnvoyOfVigilance() = card(
        name = "Tamsin, Envoy of Vigilance",
        cost = "{3}{W}{W}",
        type = "Legendary Creature — Human Advisor",
        pt = "4/4",
        text = """
            Flying
            Whenever you scry or surveil, create a 1/1 colorless Thopter artifact creature token with flying. This ability triggers only once each turn.
        """,
    ) parsesTo abilities(Keyword.Flying, triggered(youScryOrSurveil, Step.Do(Effect.CreateToken(creatureToken("Thopter", emptyList(), 1, 1, abilities = listOf(Keyword.Flying),
            types = listOf(CardType.Artifact, CardType.Creature)), n(1), Players.You)), perTurn = 1))

    // CR 608.2c
    @Test
    fun ilsaShieldlightAdept() = card(
        name = "Ilsa, Shieldlight Adept",
        cost = "{1}{W}",
        type = "Legendary Creature — Human Cleric",
        pt = "1/1",
        text = """
            Flash
            When Ilsa enters, target permanent you control gains hexproof until end of turn. Put a +1/+1 counter on it if it's a creature. Put a loyalty counter on it if it's a planeswalker. (It can't be the target of spells or abilities your opponents control.)
        """,
    ) parsesTo abilities(
            Keyword.Flash,
            triggered(entersSelf,
                gains(target("permanent"), Keyword.Hexproof),
                Step.If(Condition.ObjectMatches(target("permanent"), creature), then = listOf(counters(target("permanent")))),
                Step.If(Condition.ObjectMatches(target("permanent"), planeswalker), then = listOf(counters(target("permanent"), kind = CounterKind.Loyalty))),
                targets = listOf(slot("permanent", and(permanentFilter, you)))),
        )

    // CR 601.2f
    @Test
    fun mirelaTheEnduring() = card(
        name = "Mirela, the Enduring",
        cost = "{3}{W}",
        type = "Legendary Creature — Human Soldier",
        pt = "3/4",
        text = """
            Lifelink
            Noncreature spells your opponents cast cost {1} more to cast.
        """,
    ) parsesTo abilities(Keyword.Lifelink, static(Mechanic.CostChange(and(not(creature), spellFilter, controlledBy(PlayerFilter.Opponent)),
            CostChange.More(ManaCost.parse("{1}")!!, n(1)))))

    // CR 614.1a
    @Test
    fun kotaroFaithfulFriend() = card(
        name = "Kotaro, Faithful Friend",
        cost = "{2}{W}",
        type = "Legendary Creature — Dog",
        pt = "2/2",
        text = """
            If one or more +1/+1 counters would be put on a creature you control, that many plus one +1/+1 counters are put on it instead.
            {6}: Put a +1/+1 counter on target legendary creature.
        """,
    ) parsesTo abilities(
            static(Mechanic.Replacement(EventPattern.CountersPut(creatureYouControl, p1p1), modify = listOf(Modification.AddAmount(n(1))))),
            activated(cost(mana("{6}")), counters(target("creature")), targets = listOf(slot("creature", and(ObjectFilter.Super(Supertype.Legendary), creature)))),
        )

    // CR 506.5, 702.61a
    @Test
    fun akemiEdgeOfTheMighty() = card(
        name = "Akemi, Edge of the Mighty",
        cost = "{3}{W}",
        type = "Legendary Creature — Human Samurai",
        pt = "2/3",
        text = """
            During combat, players can't cast spells or activate abilities that aren't mana abilities.
            Whenever a creature you control attacks a player alone, it gains double strike until end of turn.
        """,
    ) parsesTo abilities(
            Ability.Static(listOf(Mechanic.CantCast(ObjectFilter.Any), Mechanic.CantActivate(not(ObjectFilter.AbilityOf(kind = AbilityKind.Mana)))),
                affects = Affected.Players(Players.Each(PlayerFilter.Any)), condition = Condition.Or(listOf(TurnStep.BeginningOfCombat, TurnStep.DeclareAttackers, TurnStep.DeclareBlockers, TurnStep.CombatDamage, TurnStep.EndOfCombat).map { Condition.StepIs(it) })),
            triggered(on(EventPattern.Attack(creatureYouControl, alone = true, target = RecipientFilter(players = PlayerFilter.Any))),
                gains(Objects.EventObject(EventRole.Object), Keyword.DoubleStrike)),
        )
}
