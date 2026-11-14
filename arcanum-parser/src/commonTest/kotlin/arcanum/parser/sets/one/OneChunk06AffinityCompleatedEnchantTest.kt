package arcanum.parser.sets.one

import arcanum.api.Ability
import arcanum.api.AbilityKind
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
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
import arcanum.api.FaceDefinition
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
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.parsesTo
import kotlin.test.Test

/**
 * Phyrexia: All Will Be One chunk 06: affinity (CR 702.41), compleated planeswalkers (CR 702.150) and their loyalty abilities, a
 * noncombat damage doubler (CR 614.1a), doubling power and toughness each combat (CR 701.10), and Auras (CR 303.4, 702.5).
 */
class OneChunk06AffinityCompleatedEnchantTest {
    private val loyaltyOfSelf = Value.Of(NumericProperty.Loyalty, Objects.Self, Combine.Single)
    private val playerTarget = Players.Target("player")

    // CR 702.41a; CR 613.1f: it loses indestructible after the damage, so state-based actions see lethal damage (CR 704.5g)
    @Test
    fun gearbreakerVolley() = card(
        name = "Gearbreaker Volley",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Affinity for Equipment (This spell costs {1} less to cast for each Equipment you control.)
            Gearbreaker Volley deals 5 damage to target creature or planeswalker. That permanent loses indestructible until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(equipment, "Equipment"),
        spell(
            damage(5, toObjects(target("permanent"))),
            untilEndOfTurn(target("permanent"), Mechanic.LoseAbilities(Keyword.Indestructible.kind)),
            targets = listOf(slot("permanent", or(creature, planeswalker))),
        ),
    )

    // CR 702.41a
    @Test
    fun slagmawBrute() = card(
        name = "Slagmaw Brute",
        cost = "{5}{R}{R}",
        type = "Creature — Ogre Rebel",
        pt = "7/5",
        text = """
            Affinity for Equipment (This spell costs {1} less to cast for each Equipment you control.)
            Trample
        """,
    ) parsesTo abilities(
        Keyword.Affinity(equipment, "Equipment"),
        Keyword.Trample,
    )

    // CR 702.41a; CR 611.2c: the creatures affected fixed as it resolves
    @Test
    fun platedRush() = card(
        name = "Plated Rush",
        cost = "{3}{W}{W}",
        type = "Instant",
        text = """
            Affinity for artifacts (This spell costs {1} less to cast for each artifact you control.)
            Creatures you control get +2/+1 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Affinity(artifact, "artifacts"),
        spell(pump(Objects.Each(creatureYouControl), 2, 1)),
    )

    // CR 702.150a; CR 611.2b: until your next turn; CR 404.1: any player's graveyard; CR 107.3: X of the [−X] cost
    @Test
    fun orrinThePerfectedMind() = card(
        name = "Orrin, the Perfected Mind",
        cost = "{2}{U}{U/P}",
        type = "Legendary Planeswalker — Jace",
        loyalty = "5",
        text = """
            Compleated ({U/P} can be paid with {U} or 2 life. If life was paid, this planeswalker enters with two fewer loyalty counters.)
            [+1]: Until your next turn, up to one target creature gets -3/-0.
            [−2]: Target player mills three cards. Then if a graveyard has twenty or more cards in it, you draw three cards. Otherwise, you draw a card.
            [−X]: Target player mills three times X cards.
        """,
    ) parsesTo abilities(
        Keyword.Compleated,
        loyalty(1, untilYourNextTurn(target("creature"), pt(-3, 0)), targets = listOf(slot("creature", creature, min = 0))),
        loyalty(-2,
            Step.Do(Effect.Mill(playerTarget, n(3))),
            Step.If(
                Condition.PlayerExists(PlayerFilter.Where(Condition.Exists(
                    ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Tested)))), atLeast = 20)), atLeast = 1),
                then = listOf(draw(3)),
                otherwise = listOf(draw(1)),
            ),
            targets = listOf(targetPlayer),
        ),
        Ability.Activated(
            cost(CostPart.Loyalty(Value.Times(Value.Announced("X"), -1))),
            Instructions(
                targets = listOf(targetPlayer),
                steps = listOf(Step.Do(Effect.Mill(playerTarget, Value.Times(Value.Announced("X"), 3)))),
                announced = listOf("X"),
            ),
            anyTime,
        ),
    )

    // CR 702.150a; CR 106.6: restricted mana; CR 601.2d: the damage divided as it is activated, X then (CR 602.2b)
    @Test
    fun lokkaBoundToRuin() = card(
        name = "Lokka, Bound to Ruin",
        cost = "{2}{R}{R/G/P}{G}",
        type = "Legendary Planeswalker — Lukka",
        loyalty = "5",
        text = """
            Compleated ({R/G/P} can be paid with {R}, {G}, or 2 life. If life was paid, this planeswalker enters with two fewer loyalty counters.)
            [+1]: Add {R}{G}. Spend this mana only to cast creature spells or activate abilities of creatures.
            [−1]: Create a 3/3 green Phyrexian Beast creature token with toxic 1.
            [−4]: Lokka deals X damage divided as you choose among any number of target creatures and/or planeswalkers, where X is the greatest power among creatures you control as you activate this ability.
        """,
    ) parsesTo abilities(
        Keyword.Compleated,
        loyalty(1, Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse("{R}{G}")!!),
            restriction = or(and(spellFilter, creature), ObjectFilter.AbilityOf(kind = AbilityKind.Activated, source = creature))), Players.You))),
        loyalty(-1, create(creatureToken("Phyrexian Beast", listOf(Color.Green), 3, 3, abilities = listOf(Keyword.Toxic(1))))),
        loyalty(-4,
            damage(Value.DividedShare, toObjects(target("planeswalkers"))),
            targets = listOf(TargetSlot("planeswalkers", TargetKind.Objects(or(creature, planeswalker)), min = n(0), max = null,
                divided = Value.Of(NumericProperty.Power, Objects.Each(creatureYouControl), Combine.Greatest))),
        ),
    )

    // CR 702.150a; CR 508.1d: a requirement to attack a player; CR 707.2: a copy of the exiled card; CR 603.7: exiled at the next end step
    @Test
    fun nahraTheUnforgiving() = card(
        name = "Nahra, the Unforgiving",
        cost = "{1}{R}{R/W/P}{W}",
        type = "Legendary Planeswalker — Nahiri",
        loyalty = "5",
        text = """
            Compleated ({R/W/P} can be paid with {R}, {W}, or 2 life. If life was paid, this planeswalker enters with two fewer loyalty counters.)
            [+1]: Until your next turn, up to one target creature attacks a player each combat if able.
            [+1]: Discard a card, then draw a card.
            [0]: Exile target creature or Equipment card with mana value less than Nahra's loyalty from your graveyard. Create a token that's a copy of it. That token gains haste. Exile it at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Keyword.Compleated,
        loyalty(1, untilYourNextTurn(target("creature"), Mechanic.MustAttack(Recipients(players = Players.Each(PlayerFilter.Any)))),
            targets = listOf(slot("creature", creature, min = 0))),
        loyalty(1, discard(1), draw(1)),
        loyalty(0,
            move(target("card"), Zone.Exile, postBind = "exiled"),
            create(TokenSpec.CopyOf(Objects.Bound("exiled")), bind = "token"),
            Step.Do(Effect.ApplyMechanics(listOf(has(Keyword.Haste)), Affected.Objects(Objects.Bound("token")), Duration.Indefinitely)),
            Step.Do(Effect.CreateDelayedTrigger(stepBegins(TurnStep.End, PlayerFilter.Any) as Trigger.On,
                Instructions(steps = listOf(move(Objects.Bound("token"), Zone.Exile))))),
            targets = listOf(slot("card", and(or(creature, equipment), ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You),
                ObjectFilter.ManaValue(Comparison.Less, loyaltyOfSelf)))),
        ),
    )

    // CR 702.150a; CR 306.5b: X is its loyalty as the ability resolves; CR 611.2c
    @Test
    fun nessaAscendedAnimist() = card(
        name = "Nessa, Ascended Animist",
        cost = "{3}{G}{G}{G/P}{G/P}",
        type = "Legendary Planeswalker — Nissa",
        loyalty = "7",
        text = """
            Compleated ({G/P} can be paid with {G} or 2 life. For each {G/P} paid with life, this planeswalker enters with two fewer loyalty counters.)
            [+1]: Create an X/X green Phyrexian Horror creature token, where X is Nessa's loyalty.
            [−1]: Destroy target artifact or enchantment.
            [−7]: Until end of turn, creatures you control get +1/+1 for each Forest you control and gain trample.
        """,
    ) parsesTo abilities(
        Keyword.Compleated,
        loyalty(1, create(TokenSpec.Defined(FaceDefinition("Phyrexian Horror Token", colorIndicator = listOf(Color.Green), types = listOf(CardType.Creature),
            subtypes = listOf(Subtype("Phyrexian"), Subtype("Horror")), power = PrintedNumber.X, toughness = PrintedNumber.X), power = loyaltyOfSelf, toughness = loyaltyOfSelf))),
        loyalty(-1, destroy(target("permanent")), targets = listOf(slot("permanent", or(artifact, enchantment)))),
        loyalty(-7, untilEndOfTurn(Objects.Each(creatureYouControl),
            Mechanic.ModifyPowerToughness(count(and(sub("Forest"), you)), count(and(sub("Forest"), you))), has(Keyword.Trample))),
    )

    // CR 702.150a; CR 205.1a, 613.1d, f: a Treasure artifact with only that ability, its colors kept; CR 122.1f: poison counters
    @Test
    fun vrashaBetrayalsSting() = card(
        name = "Vrasha, Betrayal's Sting",
        cost = "{4}{B}{B/P}",
        type = "Legendary Planeswalker — Vraska",
        loyalty = "6",
        text = """
            Compleated ({B/P} can be paid with {B} or 2 life. If life was paid, this planeswalker enters with two fewer loyalty counters.)
            [0]: You draw a card and lose 1 life. Proliferate.
            [−2]: Target creature becomes a Treasure artifact with "{T}, Sacrifice this artifact: Add one mana of any color" and loses all other card types and abilities.
            [−9]: If target player has fewer than nine poison counters, they get a number of poison counters equal to the difference.
        """,
    ) parsesTo abilities(
        Keyword.Compleated,
        loyalty(0, draw(1), loseLife(1), proliferate),
        loyalty(-2,
            Step.Do(Effect.ApplyMechanics(listOf(
                Mechanic.SetTypes(add = listOf(CardType.Artifact), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Treasure"))), replaceTypes = true, replaceSubtypes = true),
                Mechanic.LoseAbilities(),
                has(activated(cost(tap, sacrificeSelf), Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You)))),
            ), Affected.Objects(target("creature")), Duration.Indefinitely)),
            targets = listOf(slot("creature", creature)),
        ),
        loyalty(-9,
            Step.If(Condition.Compare(Value.PlayerCounters(playerTarget, CounterKind.Poison), Comparison.Less, n(9)),
                then = listOf(Step.Do(Effect.GivePlayerCounters(playerTarget, CounterKind.Poison,
                    Value.Difference(n(9), Value.PlayerCounters(playerTarget, CounterKind.Poison)))))),
            targets = listOf(targetPlayer),
        ),
    )

    // CR 614.1a: noncombat damage to an opponent or their permanents doubled; CR 122.1b: an indestructible counter
    @Test
    fun solvexMayhemDominus() = card(
        name = "Solvex, Mayhem Dominus",
        cost = "{2}{R}{R}",
        type = "Legendary Creature — Phyrexian Horror",
        pt = "5/4",
        text = """
            If a source you control would deal noncombat damage to an opponent or a permanent an opponent controls, it deals double that damage to that player or permanent instead.
            {1}{R/P}{R/P}, Discard two cards: Put an indestructible counter on Solvex. ({R/P} can be paid with either {R} or 2 life.)
        """,
    ) parsesTo abilities(
        static(Mechanic.Replacement(
            EventPattern.Damage(you, RecipientFilter(objects = and(permanentFilter, opponents), players = PlayerFilter.Opponent), combat = false),
            modify = listOf(Modification.MultiplyAmount(2)),
        )),
        activated(cost(mana("{1}{R/P}{R/P}"), CostPart.Discard(CostObjects.Choose(cardFilter, count = n(2)))),
            counters(Objects.Self, 1, CounterKind.Keyword(Keyword.Indestructible.kind))),
    )

    // CR 701.10f: doubling power and toughness gives +X/+X; each combat, any player's (CR 507.1)
    @Test
    fun zorandelHungerDominus() = card(
        name = "Zorandel, Hunger Dominus",
        cost = "{5}{G}{G}",
        type = "Legendary Creature — Phyrexian Horror",
        pt = "4/6",
        text = """
            Reach
            At the beginning of each combat, double the power and toughness of each creature you control until end of turn.
            {G/P}{G/P}, Sacrifice two other creatures: Put an indestructible counter on Zorandel. ({G/P} can be paid with either {G} or 2 life.)
        """,
    ) parsesTo abilities(
        Keyword.Reach,
        triggered(stepBegins(TurnStep.BeginningOfCombat, PlayerFilter.Any), Step.ForEach(
            each = arcanum.api.Each.Objects(Objects.Each(creatureYouControl)),
            bind = "creature",
            steps = listOf(untilEndOfTurn(Objects.Bound("creature"), Mechanic.ModifyPowerToughness(power(Objects.Bound("creature")), toughness(Objects.Bound("creature"))))),
        )),
        activated(cost(mana("{G/P}{G/P}"), CostPart.Sacrifice(CostObjects.Choose(and(another, creature), count = n(2)))),
            counters(Objects.Self, 1, CounterKind.Keyword(Keyword.Indestructible.kind))),
    )

    // CR 603.4: an intervening "if", checked as it triggers and as it resolves; CR 702.164: toxic of any value
    @Test
    fun mazeThreadMantle() = card(
        name = "Maze-Thread Mantle",
        cost = "{2}{G}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature
            When this Aura enters, if enchanted creature has toxic, that creature gains hexproof until end of turn.
            Enchanted creature gets +2/+2.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        enchant(creature),
        triggered(entersSelf, gains(enchanted, Keyword.Hexproof), interveningIf = Condition.ObjectMatches(enchanted, kw(Keyword.Toxic.Kind))),
        static(pt(2, 2), affects = enchanted),
    )

    // CR 701.34a: proliferate; CR 502.3: it doesn't untap
    @Test
    fun mesmericDose() = card(
        name = "Mesmeric Dose",
        cost = "{1}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, tap enchanted creature, then proliferate. (Choose any number of permanents and/or players, then give each another counter of each kind already there.)
            Enchanted creature doesn't untap during its controller's untap step.
        """,
    ) parsesTo abilities(
        enchant(creature),
        triggered(entersSelf, tapIt(enchanted), proliferate),
        static(Mechanic.DoesntUntap, affects = enchanted),
    )

    // CR 702.164a: toxic granted; CR 603.10a: the creature card that died; under the Aura's controller's control (CR 110.2a)
    @Test
    fun necroticCommunion() = card(
        name = "Necrotic Communion",
        cost = "{1}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature you control
            Enchanted creature has toxic 2. (Players dealt combat damage by it also get two poison counters.)
            When enchanted creature dies, return that card to the battlefield under your control.
        """,
    ) parsesTo abilities(
        enchant(creatureYouControl),
        static(has(Keyword.Toxic(2)), affects = enchanted),
        triggered(on(EventPattern.ZoneChange(ObjectFilter.Is(enchanted), from = Zone.Battlefield, to = Zone.Graveyard)),
            Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, controller = Players.You)))),
    )

    // CR 610.3: exiled until this Aura leaves the battlefield
    @Test
    fun ossifyingGrip() = card(
        name = "Ossifying Grip",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant basic land you control
            When this Aura enters, exile target creature or planeswalker an opponent controls until this Aura leaves the battlefield.
        """,
    ) parsesTo abilities(
        enchant(and(ObjectFilter.Super(arcanum.api.Supertype.Basic), land, you)),
        triggered(entersSelf, Step.Do(Effect.Move(target("permanent"), Destination(Zone.Exile), untilLeaves = Objects.Self)),
            targets = listOf(slot("permanent", and(or(creature, planeswalker), opponents)))),
    )

    // CR 508.1c, 509.1c, 602.5: it can't attack or block and its activated abilities, mana abilities included, can't be activated
    @Test
    fun planarDisruptorWard() = card(
        name = "Planar Disruptor Ward",
        cost = "{1}{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant artifact, creature, or planeswalker
            Enchanted permanent can't attack or block, and its activated abilities can't be activated.
        """,
    ) parsesTo abilities(
        enchant(or(artifact, creature, planeswalker)),
        static(Mechanic.CantAttack(), Mechanic.CantBlock(attackers = ObjectFilter.Any), Mechanic.CantBeActivated(manaAbilities = true), affects = enchanted),
    )
}
