package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
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
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Color
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 25: enchantments that are engines. Sacrifice and discard outlets ("Sacrifice a creature: … deals 1 damage to any
 * target", tapping a creature as a cost), extra land plays (CR 305.2), a free-casting permission with player-wide flash,
 * playing with the hand revealed, a draw replacement with "Otherwise" (CR 614.1a), "it" in a trigger meaning the card
 * in the graveyard, creatures that don't untap, and an Aura that turns the enchanted permanent into a Vehicle.
 */
class Chunk25EnchantmentEnginesTest {
    // ---- Shared shapes

    /** The "any target" slot (CR 115.4). */
    private val anyTarget = TargetSlot(
        name = "any",
        what = TargetKind.Either(
            objects = ObjectFilter.Or(listOf(
                ObjectFilter.Type(CardType.Creature),
                ObjectFilter.Type(CardType.Planeswalker),
                ObjectFilter.Type(CardType.Battle),
            )),
            players = PlayerFilter.Any,
        ),
    )

    /** "[cost]: This enchantment deals [amount] damage to any target." */
    private fun pinger(vararg cost: CostPart, amount: Int) = Ability.Activated(
        cost = Cost(cost.toList()),
        instructions = Instructions(
            targets = listOf(anyTarget),
            steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), amount = Value.Fixed(amount)))),
        ),
    )

    private fun mana(text: String) = CostPart.Mana(ManaCost.parse(text)!!)
    private fun sacrificeA(filter: ObjectFilter) = CostPart.Sacrifice(CostObjects.Choose(filter))
    private fun discardA(filter: ObjectFilter, count: Int = 1) = CostPart.Discard(CostObjects.Choose(filter, count = Value.Fixed(count)))
    private val creature = ObjectFilter.Type(CardType.Creature)
    private val dies = EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard)
    private val yourHand = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))

    /** "You may play [count] additional lands on each of your turns" (CR 305.2); null: any number. */
    private fun extraLandPlays(count: Int?) = Ability.Static(
        mechanics = listOf(Mechanic.ExtraLandPlays(count)),
        affects = Affected.Players(Players.You),
    )

    // ---- Sacrifice and discard outlets

    @Test
    fun goblinVolleyPit() = card(
        name = "Goblin Volley Pit",
        cost = "{1}{R}",
        type = "Enchantment",
        text = "Sacrifice a creature: This enchantment deals 1 damage to any target.",
    ) parsesTo abilities(
        pinger(sacrificeA(creature), amount = 1),
    )

    @Test
    fun scrapheapOrdnance() = card(
        name = "Scrapheap Ordnance",
        cost = "{1}{R}",
        type = "Enchantment",
        text = "{1}, Sacrifice an artifact or creature: This enchantment deals 1 damage to any target.",
    ) parsesTo abilities(
        pinger(
            mana("{1}"),
            sacrificeA(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), creature))),
            amount = 1,
        ),
    )

    @Test
    fun magmaWhirlpool() = card(
        name = "Magma Whirlpool",
        cost = "{R}",
        type = "Enchantment",
        text = "{R}, Discard a land card: This enchantment deals 2 damage to any target.",
    ) parsesTo abilities(
        pinger(mana("{R}"), discardA(ObjectFilter.Type(CardType.Land)), amount = 2),
    )

    @Test
    fun ghoulOutbreak() = card(
        name = "Ghoul Outbreak",
        cost = "{1}{B}",
        type = "Enchantment",
        text = "Discard two cards: Create a 2/2 black Zombie creature token.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(discardA(ObjectFilter.Category(ObjectCategory.Card), count = 2))),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
                    name = "Zombie Token",
                    colorIndicator = listOf(Color.Black),
                    types = listOf(CardType.Creature),
                    subtypes = listOf(Subtype("Zombie")),
                    power = PrintedNumber.Fixed(2),
                    toughness = PrintedNumber.Fixed(2),
                ))))),
            ),
        ),
    )

    @Test
    fun lawOfTheWilds() = card(
        name = "Law of the Wilds",
        cost = "{1}{G}",
        type = "Enchantment",
        text = "{G}, Discard a creature card: Search your library for a creature card, reveal that card, put it into your hand, then shuffle.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(mana("{G}"), discardA(creature))),
            instructions = Instructions(
                steps = listOf(
                    Step.Do(Effect.Search(Players.You, creature, reveal = true, bind = "found")),
                    Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Hand))),
                    Step.Do(Effect.Shuffle(Players.You)),
                ),
            ),
        ),
    )

    /** CR 106.7: the mana a land "could produce" is read from the sacrificed land as it last existed (CR 608.2h). */
    @Test
    fun wastedBounty() = card(
        name = "Wasted Bounty",
        cost = "{B}{G}",
        type = "Enchantment",
        text = "Sacrifice a land: Add one mana of any type the sacrificed land could produce.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Type(CardType.Land)), preBind = "sacrificed"))),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.AddMana(ManaProduction(
                    ManaKind.AsCouldProduce(ObjectFilter.Is(Objects.Bound("sacrificed"))),
                )))),
            ),
        ),
    )

    @Test
    fun soilbinding() = card(
        name = "Soilbinding",
        cost = "{1}{G}",
        type = "Enchantment",
        text = "Tap an untapped creature you control: Untap target basic land.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapObjects(CostObjects.Choose(creature)))),
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    name = "land",
                    what = TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Super(Supertype.Basic), ObjectFilter.Type(CardType.Land)))),
                )),
                steps = listOf(Step.Do(Effect.Untap(Objects.Target("land")))),
            ),
        ),
    )

    // ---- Land plays

    @Test
    fun frontierWandering() = card(
        name = "Frontier Wandering",
        cost = "{G}",
        type = "Enchantment",
        text = "You may play an additional land on each of your turns.",
    ) parsesTo abilities(
        extraLandPlays(1),
    )

    /** CR 305.2a: the lands played this turn include the one just played, so "not the first" is two or more. */
    @Test
    fun rootrush() = card(
        name = "Rootrush",
        cost = "{G}",
        type = "Enchantment",
        text = """
            You may play any number of lands on each of your turns.
            Whenever you play a land, if it wasn't the first land you played this turn, this enchantment deals 1 damage to you.
        """,
    ) parsesTo abilities(
        extraLandPlays(null),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.PlayLand(players = PlayerFilter.You)),
            interveningIf = Condition.HappenedThisTurn(EventPattern.PlayLand(players = PlayerFilter.You), atLeast = 2),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.DealDamage(Recipients(players = Players.You), amount = Value.Fixed(1)))),
            ),
        ),
    )

    // ---- Rules for every player

    /**
     * A permission held by each player for creature cards in their own hand (CR 118.9: without paying the mana cost),
     * and player-wide flash for those spells (CR 702.8a).
     */
    @Test
    fun verdantHollowmere() = card(
        name = "Verdant Hollowmere",
        cost = "{2}{G}{G}",
        type = "Enchantment",
        text = "Any player may cast creature spells with mana value 3 or less without paying their mana costs and as though they had flash.",
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.MayPlay(Zone.Hand, withoutPaying = true, holder = PlayerFilter.Any)),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                creature,
                ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(3)),
                ObjectFilter.InZone(Zone.Hand),
                ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Holder)),
            )))),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.CastAsThoughFlash(ObjectFilter.And(listOf(
                creature,
                ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(3)),
            )))),
            affects = Affected.Players(Players.Each(PlayerFilter.Any)),
        ),
    )

    @Test
    fun sentryKlaxon() = card(
        name = "Sentry Klaxon",
        cost = "{2}{U}",
        type = "Enchantment",
        text = """
            Creatures don't untap during their controllers' untap steps.
            Whenever a creature enters, untap all creatures.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.DoesntUntap),
            affects = Affected.Objects(Objects.Each(creature)),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(creature, to = Zone.Battlefield)),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Untap(Objects.Each(creature)))),
            ),
        ),
    )

    /**
     * The hand is revealed to all; each draw is replaced (CR 614.1a), and the replacing "draw a card" isn't replaced again
     * (CR 614.5). "It" in the last ability is the card in the graveyard (CR 400.7, 603.10a).
     */
    @Test
    fun endlessReturn() = card(
        name = "Endless Return",
        cost = "{2}{W}{W}",
        type = "Enchantment",
        text = """
            Play with your hand revealed.
            If you would draw a card, reveal the top card of your library instead. If it's a creature card, put it into your graveyard. Otherwise, draw a card.
            Whenever a creature is put into your graveyard from the battlefield, return it to your hand.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.MayLookAt(Objects.Affected, reveal = true)),
            affects = Affected.Objects(Objects.Each(yourHand)),
        ),
        Ability.Static(listOf(Mechanic.Replacement(
            event = EventPattern.Draw(PlayerFilter.You),
            instead = Instructions(
                steps = listOf(
                    Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Fixed(1)), bind = "revealed")),
                    Step.If(
                        condition = Condition.ObjectMatches(Objects.Bound("revealed"), creature),
                        then = listOf(Step.Do(Effect.Move(Objects.Bound("revealed"), to = Destination(Zone.Graveyard)))),
                        otherwise = listOf(Step.Do(Effect.Draw(Players.You, count = Value.Fixed(1)))),
                    ),
                ),
            ),
        ))),
        Ability.Triggered(
            trigger = Trigger.On(dies.copy(objects = ObjectFilter.And(listOf(creature, ObjectFilter.OwnedBy(PlayerFilter.You))))),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Move(Objects.EventObject(EventRole.After), to = Destination(Zone.Hand)))),
            ),
        ),
    )

    // ---- Auras

    /**
     * CR 205.1a: the new card type replaces the old ones; it gains crew 5 (CR 702.122a), so it is a creature only while
     * crewed.
     */
    @Test
    fun rapidRetrofit() = card(
        name = "Rapid Retrofit",
        cost = "{W}",
        type = "Enchantment — Aura",
        text = """
            Flash
            Enchant creature or Vehicle
            Enchanted permanent is a Vehicle artifact with crew 5 and it loses all other card types. (It's not a creature unless it's crewed.)
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Enchant(TargetKind.Objects(ObjectFilter.Or(listOf(creature, ObjectFilter.Sub(Subtype("Vehicle")))))),
        Ability.Static(
            mechanics = listOf(
                Mechanic.SetTypes(
                    add = listOf(CardType.Artifact),
                    addSubtypes = listOf(SubtypeRef.Literal(Subtype("Vehicle"))),
                    replaceTypes = true,
                ),
                Mechanic.GainAbilities(listOf(Keyword.Crew(5))),
            ),
            affects = Affected.Objects(Objects.AttachedTo()),
        ),
    )
}
