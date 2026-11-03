package arcanum.parser.sets.msh

import arcanum.api.Ability
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.Measure
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
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Marvel Super Heroes chunk 14: red cards. A hand exiled and that many cards drawn, playable until your next turn ends; a token then
 * a pump in one sentence; "another Villain and/or artifact"; a stun counter as a cost (CR 122.1d) and the top X cards exiled; "you may
 * sacrifice an artifact or discard a card. If you do" (one of two costs, CR 118.12); cards discarded this turn counted; damage doubled
 * (CR 614.1a) and "equip worthy" (CR 700.16, 702.6); a spell copying itself X times as it is cast (CR 707.10); "When you do" after a
 * counter put (CR 603.12); a cost reduction by its power; "can't be blocked except by creatures with haste" (CR 509.1b).
 */
class MshChunk14RedTest {
    @Test
    fun chaosHex() = card(
        name = "Chaos Hex",
        cost = "{2}{R}",
        type = "Sorcery — Arcane",
        text = "Exile all the cards from your hand, then draw that many cards. Until the end of your next turn, you may play cards exiled this way.",
    ) parsesTo abilities(
        spell(move(Objects.Each(and(inZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))), Zone.Exile, postBind = "exiled"),
            Step.Do(Effect.Draw(Players.You, Value.Count(Objects.Bound("exiled")))), playable(Duration.UntilEndOfYourNextTurn)),
    )

    @Test
    fun hireSomeMuscle() = card(
        name = "Hire Some Muscle",
        cost = "{2}{R}",
        type = "Instant",
        text = "Create a 2/1 black Villain creature token with menace, then creatures you control get +1/+0 until end of turn. (A creature with menace can't be blocked except by two or more creatures.)",
    ) parsesTo abilities(
        spell(create(villain2), pump(Objects.Each(creatureYouControl), 1, 0)),
    )

    @Test
    fun serpentCultAssaultRobot() = card(
        name = "Serpent Cult Assault Robot",
        cost = "{1}{R}",
        type = "Artifact Creature — Robot Villain",
        text = "Whenever another Villain and/or artifact you control enters, this creature deals 1 damage to target opponent.",
        pt = "1/3",
    ) parsesTo abilities(
        triggered(enters(and(another, or(villainFilter, artifact), you)), damage(1, Recipients(players = Players.Target("opponent"))), targets = listOf(targetOpponent)),
    )

    // CR 122.1d: a stun counter put as a cost; X is its power as it resolves
    @Test
    fun rheaKanePrivateEye() = card(
        name = "Rhea Kane, Private Eye",
        cost = "{2}{R}",
        type = "Legendary Creature — Human Detective Hero",
        text = "{T}, Put a stun counter on Rhea Kane: Exile the top X cards of your library, where X is Rhea Kane's power. You may play those cards this turn. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
        pt = "2/3",
    ) parsesTo abilities(
        activated(cost(tap, CostPart.PutCounters(CounterKind.Stun, n(1), CostObjects.Self)),
            move(Objects.LibraryCards(Players.You, power(Objects.Self), fromTop = true), Zone.Exile, postBind = "exiled"), playable(Duration.EndOfTurn)),
    )

    // CR 118.12: one of two costs on resolution
    @Test
    fun hiddenCityWarrior() = card(
        name = "Hidden City Warrior",
        cost = "{1}{R}",
        type = "Creature — Human Warrior Hero",
        text = "When this creature enters, you may sacrifice an artifact or discard a card. If you do, draw a card.",
        pt = "2/2",
    ) parsesTo abilities(
        triggered(entersSelf, sacrificeOrDiscard, Step.If(Condition.Did("paid"), then = listOf(draw(1)))),
    )

    @Test
    fun toolmakerAutomaton() = card(
        name = "Toolmaker Automaton",
        cost = "{2}{R}",
        type = "Artifact Creature — Robot Villain",
        text = """
            Trample
            Whenever another artifact you control enters, put a +1/+1 counter on this creature.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Trample,
        triggered(enters(and(another, artifact, you)), counters(Objects.Self)),
    )

    // cards discarded this turn, the one discarded for the cost included
    @Test
    fun veraSteeleHeroForHire() = card(
        name = "Vera Steele, Hero for Hire",
        cost = "{1}{R}",
        type = "Legendary Creature — Human Detective Hero",
        text = "{2}, {T}, Discard a card: Draw a card for each card you've discarded this turn.",
        pt = "3/1",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap, discardACard), Step.Do(Effect.Draw(Players.You, Value.EventsThisTurn(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.You), Measure.Objects)))),
    )

    // CR 614.1a: doubled damage; CR 700.16, 702.6a: equip only onto a worthy creature
    @Test
    fun skyforgedHammer() = card(
        name = "Skyforged, Hammer of the Storm",
        cost = "{3}{R}",
        type = "Legendary Artifact — Equipment",
        text = """
            When Skyforged enters, it deals 4 damage to up to one target creature.
            Double all damage equipped creature would deal.
            Equip worthy {1} (A creature is worthy if it's a legendary non-Villain that's red and/or white.)
            {2}{R}, Discard this card: It deals 2 damage to each creature.
        """,
    ) parsesTo abilities(
        triggered(entersSelf, damage(4, toObjects(target("creature"))), targets = listOf(slot("creature", creature, min = 0))),
        static(Mechanic.Replacement(EventPattern.Damage(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)), RecipientFilter()), modify = listOf(Modification.MultiplyAmount(2)))),
        Keyword.Equip(manaCost("{1}"), onto = and(ObjectFilter.Super(arcanum.api.Supertype.Legendary), not(villainFilter),
            or(ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(arcanum.api.Color.Red)), ObjectFilter.OfColor(arcanum.api.ColorRef.Literal(arcanum.api.Color.White))))),
        activated(cost(mana("{2}{R}"), CostPart.Discard(CostObjects.Self)), damage(2, toObjects(Objects.Each(creature)))),
    )

    // CR 707.10: copies of the spell as it is cast (a cast trigger, its X the spell's)
    @Test
    fun plasmaBoltBarrage() = card(
        name = "Plasma Bolt Barrage",
        cost = "{X}{R}{R}",
        type = "Sorcery",
        text = """
            When you cast this spell, copy it X times. You may choose new targets for the copies.
            Plasma Bolt Barrage deals 1 damage to target creature.
        """,
    ) parsesTo abilities(
        triggered(youCast(ObjectFilter.Is(Objects.Self)), Step.Repeat(Value.CastX(of = Objects.Self), listOf(Step.Do(Effect.CopySpell(Objects.Self, Players.You, newTargets = true))))),
        spell(damage(1, toObjects(target("creature"))), targets = listOf(slot("creature", creature)), announced = listOf("X")),
    )

    // CR 603.12: the counter put, then the reflexive trigger; "any other target" (CR 115.4)
    @Test
    fun crimsonBrute() = card(
        name = "Crimson Brute",
        cost = "{4}{R}{R}",
        type = "Legendary Creature — Gamma Berserker Villain",
        text = """
            Reach, trample
            Enrage — Whenever Crimson Brute is dealt damage, put a +1/+1 counter on him. When you do, he deals damage equal to the number of +1/+1 counters on him to any other target.
        """,
        pt = "6/7",
    ) parsesTo abilities(
        Keyword.Reach,
        Keyword.Trample,
        triggered(on(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(Objects.Self)))),
            counters(Objects.EventObject(EventRole.Object)),
            reflexive(damage(Value.Of(NumericProperty.Counters(p1p1), Objects.Self, Combine.Single), toAny), targets = listOf(anyOtherTarget)),
            abilityWord = "Enrage"),
    )

    // CR 601.2f: {1} less for each point of its power
    @Test
    fun theChaosWitch() = card(
        name = "The Chaos Witch",
        cost = "{2}{R}",
        type = "Legendary Creature — Mutant Warlock Hero",
        text = "Instant and sorcery spells you cast with mana value 4 or greater cost {X} less to cast, where X is The Chaos Witch's power.",
        pt = "2/3",
    ) parsesTo abilities(
        static(Mechanic.CostChange(and(or(instant, sorcery), spellFilter, you, manaValue(arcanum.api.Comparison.AtLeast, 4)),
            CostChange.Less(ManaCost.parse("{1}")!!, power(Objects.Self)))),
    )

    // CR 509.1b: blocked only by creatures with haste
    @Test
    fun quickstepYoungGuardian() = card(
        name = "Quickstep, Young Guardian",
        cost = "{1}{R}",
        type = "Legendary Creature — Mutant Hero",
        text = """
            Haste
            Whenever you cast a noncreature spell, you may pay {1}. When you do, target creature with haste can't be blocked this turn except by creatures with haste.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Haste,
        triggered(youCast(noncreatureSpell), Step.Pay(manaCost("{1}"), payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(reflexive(untilEndOfTurn(target("creature"), Mechanic.CanBeBlockedOnlyBy(withHaste)),
                targets = listOf(slot("creature", withHaste)))))),
    )

    @Test
    fun steeleIndustriesExecutive() = card(
        name = "Steele Industries Executive",
        cost = "{R}",
        type = "Creature — Human Advisor",
        text = "{2}, {T}: Create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")",
        pt = "1/2",
    ) parsesTo abilities(
        activated(cost(mana("{2}"), tap), createTreasure()),
    )

    @Test
    fun superSwiftness() = card(
        name = "Super Swiftness",
        cost = "{R}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            When this Aura enters, enchanted creature gains first strike until end of turn.
            Enchanted creature gets +1/+0 and has haste.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(creature)),
        triggered(entersSelf, gains(Objects.AttachedTo(Objects.Self), Keyword.FirstStrike)),
        static(pt(1, 0), has(Keyword.Haste), affects = Objects.AttachedTo(Objects.Self)),
    )

    // the cast spell's mana value (as it last existed if it has left, CR 608.2h)
    @Test
    fun thundarGodOfThunder() = card(
        name = "Thundar, God of Thunder",
        cost = "{3}{R}{R}",
        type = "Legendary Creature — God Warrior Hero",
        text = """
            Flying
            When Thundar enters, exile target Equipment, instant, or sorcery card from your graveyard. Until the end of your next turn, you may play that card.
            Whenever you cast a noncreature spell, Thundar deals damage equal to that spell's mana value to any target.
        """,
        pt = "5/5",
    ) parsesTo abilities(
        Keyword.Flying,
        triggered(entersSelf, move(target("card"), Zone.Exile, postBind = "exiled"), playable(Duration.UntilEndOfYourNextTurn),
            targets = listOf(slot("card", inYourGraveyard(or(equipment, instant, sorcery))))),
        triggered(youCast(noncreatureSpell), damage(Value.Of(NumericProperty.ManaValue, Objects.EventObject(EventRole.Object), Combine.Single), toAny),
            targets = listOf(anyTarget)),
    )

    @Test
    fun lorryHurl() = card(
        name = "Lorry Hurl",
        cost = "{2}{R}{R}",
        type = "Instant",
        text = """
            This spell costs {2} less to cast if you control a Vehicle.
            Lorry Hurl deals 4 damage to any target.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{2}")!!, n(1)))),
            affects = arcanum.api.Affected.Objects(Objects.Self), condition = Condition.Exists(and(sub("Vehicle"), you), atLeast = 1)),
        spell(damage(4, toAny), targets = listOf(anyTarget)),
    )

    @Test
    fun glimpseOfLove() = card(
        name = "Glimpse of Love",
        cost = "{1}{R}",
        type = "Instant",
        text = "You may sacrifice an artifact or discard a card. If you do, draw two cards.",
    ) parsesTo abilities(
        spell(sacrificeOrDiscard, Step.If(Condition.Did("paid"), then = listOf(draw(2)))),
    )

    private val withHaste = and(creature, kw(Keyword.Haste.kind))
    private val anyOtherTarget = TargetSlot("any", TargetKind.Either(and(or(creature, ObjectFilter.Type(arcanum.api.CardType.Planeswalker),
        ObjectFilter.Type(arcanum.api.CardType.Battle)), another), PlayerFilter.Any), min = n(1), max = n(1))
    private val sacrificeOrDiscard = Step.Pay(Cost(listOf(CostPart.OneOf(listOf(
        Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(artifact, count = n(1))))),
        Cost(listOf(CostPart.Discard(CostObjects.Choose(cardFilter, count = n(1))))))))), payer = Players.You, bind = "paid")
    private fun playable(duration: Duration) = Step.Do(Effect.ApplyMechanics(listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
        arcanum.api.Affected.Objects(Objects.Bound("exiled")), duration))
}
