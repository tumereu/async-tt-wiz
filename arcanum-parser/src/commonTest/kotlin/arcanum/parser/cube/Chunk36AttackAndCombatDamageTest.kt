package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
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
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 36: combat creatures. Triggers on attacking (CR 508.1m), on entering or attacking (one ability with two events),
 * and on combat damage to a player, including ninjutsu creatures (CR 702.49); "attacks each combat if able" (CR 508.1d)
 * and "can't be blocked except by" (CR 509.1b); "whenever you sacrifice a permanent" (CR 701.21a); a card exiled face down
 * and kept as the creature's card memory (CR 406.3, 607.2a); casting a card from the graveyard during resolution
 * (CR 608.2g); exiling until the next end step with a delayed trigger (CR 603.7); a permission granted to graveyard cards
 * during your turn (retrace, CR 702.81a).
 */
class Chunk36AttackAndCombatDamageTest {
    // ---- Shared shapes

    private val creature = ObjectFilter.Type(CardType.Creature)
    private val self = ObjectFilter.Is(Objects.Self)
    private val yours = ObjectFilter.ControlledBy(PlayerFilter.You)
    private val ninjaYouControl = ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Ninja")), yours))
    private val plusOne = CounterKind.PowerToughness(1, 1)
    private val yourHand = ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)))

    /** "Whenever this creature attacks" (it is declared as an attacker, CR 508.1m). */
    private val thisAttacks = Trigger.On(EventPattern.Attack(self))

    /** "Whenever [source] deals combat damage to a player". */
    private fun dealsCombatDamageToAPlayer(source: ObjectFilter) = Trigger.On(EventPattern.Damage(
        source = source,
        to = RecipientFilter(players = PlayerFilter.Any),
        combat = true,
    ))

    private fun steps(vararg steps: Step) = Instructions(steps = steps.toList())
    private fun draw(n: Int = 1) = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(n)))
    private val putCounterOnSelf = Step.Do(Effect.PutCounters(Objects.Self, plusOne, amount = Value.Fixed(1)))

    // ---- Attack triggers

    @Test
    fun kaelsMarauder() = card(
        name = "Kael's Marauder",
        cost = "{2}{R}",
        type = "Creature — Vampire Berserker",
        text = """
            This creature attacks each combat if able.
            Whenever this creature attacks, discard your hand, then draw three cards.
            Madness {1}{R} (If you discard this card, discard it into exile. When you do, cast it for its madness cost or put it into your graveyard.)
        """,
        pt = "3/3",
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.MustAttack())),
        Ability.Triggered(
            trigger = thisAttacks,
            instructions = steps(
                Step.Do(Effect.Discard(Players.You, objects = Objects.Each(yourHand))),
                draw(3),
            ),
        ),
        Keyword.Madness(manaCost("{1}{R}")),
    )

    /** The exiled cards are its card memory, so the last ability finds them (CR 607.2a). */
    @Test
    fun gearwheelCourier() = card(
        name = "Gearwheel Courier",
        cost = "{1}",
        type = "Artifact Creature — Construct",
        text = """
            Haste
            Whenever this creature attacks, exile the top card of your library face down. (You can't look at it.)
            {R}, Discard your hand, Sacrifice this creature: Put all cards exiled with this creature into their owners' hands.
        """,
        pt = "1/1",
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Triggered(
            trigger = thisAttacks,
            instructions = steps(
                Step.Do(Effect.Move(
                    Objects.LibraryCards(Players.You, Value.Fixed(1)),
                    to = Destination(Zone.Exile, faceDown = true),
                    postBind = "exiled",
                )),
                Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
            ),
        ),
        Ability.Activated(
            cost = Cost(listOf(
                CostPart.Mana(ManaCost.parse("{R}")!!),
                CostPart.Discard(CostObjects.These(Objects.Each(yourHand))),
                CostPart.Sacrifice(CostObjects.Self),
            )),
            instructions = steps(Step.Do(Effect.Move(Objects.Remembered("exiled"), to = Destination(Zone.Hand)))),
        ),
    )

    /** "Pay {1} and discard a card" is one cost of two parts (CR 118.12). "One of them" is one of the discarded cards,
     * now in the graveyard; the permission covers the card it becomes in exile. */
    @Test
    fun paranoidRanter() = card(
        name = "Paranoid Ranter",
        cost = "{1}{R}",
        type = "Creature — Human Shaman",
        text = """
            Whenever this creature attacks, you may pay {1} and discard a card. If you do, draw a card.
            Whenever you discard one or more nonland cards, you may exile one of them from your graveyard. If you do, you may cast it this turn.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = thisAttacks,
            instructions = steps(
                Step.Pay(
                    Cost(listOf(
                        CostPart.Mana(ManaCost.parse("{1}")!!),
                        CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card))),
                    )),
                    bind = "paid",
                ),
                Step.If(condition = Condition.Did("paid"), then = listOf(draw())),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Discard(ObjectFilter.Not(ObjectFilter.Type(CardType.Land)), PlayerFilter.You), oneOrMore = true),
            instructions = steps(
                Step.Pay(
                    Cost(listOf(CostPart.Exile(
                        CostObjects.Choose(ObjectFilter.Is(Objects.EventObject(EventRole.After))),
                        from = Zone.Graveyard,
                        postBind = "exiled",
                    ))),
                    bind = "paid",
                ),
                Step.If(
                    condition = Condition.Did("paid"),
                    then = listOf(Step.Do(Effect.ApplyMechanics(
                        mechanics = listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast)),
                        affects = Affected.Objects(Objects.Bound("exiled")),
                        duration = Duration.EndOfTurn,
                    ))),
                ),
            ),
        ),
    )

    /** CR 608.2g: the card is cast while the ability resolves; the replacement follows that spell. The power limit is
     * checked as the target is chosen and again on resolution (CR 608.2b). */
    @Test
    fun gravehordeSpellcaller() = card(
        name = "Gravehorde Spellcaller",
        cost = "{1}{R}",
        type = "Creature — Zombie Wizard",
        text = """
            Trample
            Whenever this creature attacks, you may cast target instant or sorcery card with mana value less than or equal to this creature's power from your graveyard without paying its mana cost. If that spell would be put into your graveyard, exile it instead.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.Trample,
        Ability.Triggered(
            trigger = thisAttacks,
            instructions = Instructions(
                targets = listOf(TargetSlot("card", TargetKind.Objects(ObjectFilter.And(listOf(
                    ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                    ObjectFilter.ManaValue(Comparison.AtMost, Value.Of(NumericProperty.Power, Objects.Self, Combine.Single)),
                    ObjectFilter.InZone(Zone.Graveyard),
                    ObjectFilter.OwnedBy(PlayerFilter.You),
                ))))),
                steps = listOf(
                    Step.May(listOf(Step.Do(Effect.Cast(Objects.Target("card"), withoutPaying = true, bind = "spell")))),
                    Step.Do(Effect.ApplyMechanics(
                        mechanics = listOf(Mechanic.Replacement(
                            event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Bound("spell")), from = Zone.Stack, to = Zone.Graveyard),
                            modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))),
                        )),
                        affects = null,
                        duration = Duration.Indefinitely,
                    )),
                ),
            ),
        ),
    )

    /** The milled cards are new objects in the graveyard (CR 400.7); "you may put one" is a choice of zero or one. During
     * your turn the graveyard cards have retrace (CR 702.81a); a permanent card is one of the permanent types (CR 110.4a). */
    @Test
    fun oakheart() = card(
        name = "Oakheart",
        cost = "{2}{G}",
        type = "Legendary Creature — Treefolk",
        text = """
            Reach
            Whenever Oakheart attacks, mill three cards. You may put a land card from among them into your hand.
            During your turn, nonland permanent cards in your graveyard have retrace. (You may cast permanent cards from your graveyard by discarding a land card in addition to paying their other costs.)
        """,
        pt = "2/4",
    ) parsesTo abilities(
        Keyword.Reach,
        Ability.Triggered(
            trigger = thisAttacks,
            instructions = steps(
                Step.Do(Effect.Mill(Players.You, Value.Fixed(3), postBind = "milled")),
                Step.Choose(
                    choice = Choice.Objects(ObjectFilter.Type(CardType.Land), min = Value.Fixed(0), max = Value.Fixed(1), from = Objects.Bound("milled")),
                    bind = "chosen",
                ),
                Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Hand))),
            ),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Retrace))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                ObjectFilter.Not(ObjectFilter.Type(CardType.Land)),
                ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land, CardType.Planeswalker)
                    .map { ObjectFilter.Type(it) }),
                ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You),
            )))),
            condition = Condition.YourTurn,
        ),
    )

    /** The exiled card returns with a delayed trigger (CR 603.7); "if it entered under your control" is about the card
     * returned then, so it is part of that trigger. */
    @Test
    fun brisaBoundingShepherd() = card(
        name = "Brisa, Bounding Shepherd",
        cost = "{1}{W}",
        type = "Legendary Creature — Dog",
        text = """
            Flash
            Whenever Brisa attacks, exile up to one other target nonland permanent. At the beginning of the next end step, return that card to the battlefield under its owner's control. If it entered under your control, put a +1/+1 counter on Brisa.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(
            trigger = thisAttacks,
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    name = "permanent",
                    what = TargetKind.Objects(ObjectFilter.And(listOf(
                        ObjectFilter.Not(self),
                        ObjectFilter.Not(ObjectFilter.Type(CardType.Land)),
                        ObjectFilter.Category(ObjectCategory.Permanent),
                    ))),
                    min = Value.Fixed(0),
                )),
                steps = listOf(
                    Step.Do(Effect.Move(Objects.Target("permanent"), to = Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.CreateDelayedTrigger(
                        trigger = Trigger.On(EventPattern.StepBegins(TurnStep.End)),
                        instructions = steps(
                            Step.Do(Effect.Move(
                                Objects.Bound("exiled"),
                                to = Destination(Zone.Battlefield, controller = Players.OwnerOf(Objects.Bound("exiled"))),
                                postBind = "returned",
                            )),
                            Step.If(
                                condition = Condition.ObjectMatches(Objects.Bound("returned"), yours),
                                then = listOf(putCounterOnSelf),
                            ),
                        ),
                    )),
                ),
            ),
        ),
    )

    /** One ability with two trigger events. Sacrificing is choosing a permanent you control (CR 701.21a); "you sacrifice"
     * is about permanents you control. */
    @Test
    fun vorgathFeyHexedTyrant() = card(
        name = "Vorgath, Fey-Hexed Tyrant",
        cost = "{2}{B}{R}{G}",
        type = "Legendary Creature — Dragon Noble",
        text = """
            Flying
            Whenever Vorgath enters or attacks, sacrifice another permanent.
            Whenever you sacrifice a permanent, put a +1/+1 counter on Vorgath and draw a card.
        """,
        pt = "4/4",
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = Trigger.On(listOf(
                EventPattern.ZoneChange(self, to = Zone.Battlefield),
                EventPattern.Attack(self),
            )),
            instructions = steps(
                Step.Choose(
                    choice = Choice.Objects(
                        filter = ObjectFilter.And(listOf(ObjectFilter.Not(self), ObjectFilter.Category(ObjectCategory.Permanent), yours)),
                        min = Value.Fixed(1),
                        max = Value.Fixed(1),
                    ),
                    bind = "sacrificed",
                ),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            ),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Sacrifice(yours)),
            instructions = steps(putCounterOnSelf, draw()),
        ),
    )

    /** Training (CR 702.149a) on the creature and on its tokens. */
    @Test
    fun aldricFistOfTheSeraphs() = card(
        name = "Aldric, Fist of the Seraphs",
        cost = "{1}{G}{W}",
        type = "Legendary Creature — Human Cleric",
        text = """
            Training (Whenever this creature attacks with another creature with greater power, put a +1/+1 counter on this creature.)
            Whenever you cast a creature spell, create a 1/1 green and white Human Soldier creature token with training.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Training,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(creature, by = PlayerFilter.You)),
            instructions = steps(Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
                name = "Human Soldier Token",
                colorIndicator = listOf(Color.Green, Color.White),
                types = listOf(CardType.Creature),
                subtypes = listOf(Subtype("Human"), Subtype("Soldier")),
                power = PrintedNumber.Fixed(1),
                toughness = PrintedNumber.Fixed(1),
                abilities = listOf(Keyword.Training),
            ))))),
        ),
    )

    // ---- Combat restrictions

    /** Battle cry (CR 702.91a); flying's own restriction written out for another creature (CR 509.1b). */
    @Test
    fun beaconPest() = card(
        name = "Beacon Pest",
        cost = "{1}",
        type = "Artifact Creature — Pest",
        text = """
            Battle cry (Whenever this creature attacks, each other attacking creature gets +1/+0 until end of turn.)
            This creature can't be blocked except by creatures with flying or reach.
        """,
        pt = "0/1",
    ) parsesTo abilities(
        Keyword.BattleCry,
        Ability.Static(listOf(Mechanic.CanBeBlockedOnlyBy(ObjectFilter.And(listOf(
            creature,
            ObjectFilter.Or(listOf(ObjectFilter.HasKeyword(Keyword.Flying), ObjectFilter.HasKeyword(Keyword.Reach))),
        ))))),
    )

    // ---- Combat damage to a player

    @Test
    fun ninjaOfTheSilentHours() = card(
        name = "Ninja of the Silent Hours",
        cost = "{3}{U}",
        type = "Creature — Human Ninja",
        text = """
            Ninjutsu {1}{U} ({1}{U}, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking.)
            Whenever this creature deals combat damage to a player, you may draw a card.
        """,
        pt = "2/2",
    ) parsesTo abilities(
        Keyword.Ninjutsu(manaCost("{1}{U}")),
        Ability.Triggered(
            trigger = dealsCombatDamageToAPlayer(self),
            instructions = steps(Step.May(listOf(draw()))),
        ),
    )

    @Test
    fun cunningInfiltrator() = card(
        name = "Cunning Infiltrator",
        cost = "{2}{U}{B}",
        type = "Creature — Vedalken Ninja",
        text = """
            Ninjutsu {U}{B} ({U}{B}, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking.)
            Whenever a Ninja you control deals combat damage to a player, draw a card.
        """,
        pt = "2/3",
    ) parsesTo abilities(
        Keyword.Ninjutsu(manaCost("{U}{B}")),
        Ability.Triggered(
            trigger = dealsCombatDamageToAPlayer(ninjaYouControl),
            instructions = steps(draw()),
        ),
    )

    /** "You may draw a card. If you do" is a payment of an action (CR 118.12); "unless this creature entered this turn"
     * is checked as the ability resolves. */
    @Test
    fun neonCircuitHacker() = card(
        name = "Neon-Circuit Hacker",
        cost = "{1}{U}",
        type = "Enchantment Creature — Human Ninja",
        text = """
            Ninjutsu {U} ({U}, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking.)
            Whenever this creature deals combat damage to a player, you may draw a card. If you do, discard a card unless this creature entered this turn.
        """,
        pt = "2/1",
    ) parsesTo abilities(
        Keyword.Ninjutsu(manaCost("{U}")),
        Ability.Triggered(
            trigger = dealsCombatDamageToAPlayer(self),
            instructions = steps(
                Step.Pay(steps = listOf(draw()), bind = "paid"),
                Step.If(
                    condition = Condition.Did("paid"),
                    then = listOf(Step.If(
                        condition = Condition.Not(Condition.ObjectMatches(
                            Objects.Self,
                            ObjectFilter.ThisTurn(EventPattern.ZoneChange(to = Zone.Battlefield)),
                        )),
                        then = listOf(Step.Do(Effect.Discard(Players.You, count = Value.Fixed(1)))),
                    )),
                ),
            ),
        ),
    )

    /** Commander ninjutsu also works from the command zone (CR 702.49d). The card's mana value is read as it was when
     * revealed, after it went to the hand (CR 608.2h). */
    @Test
    fun akemiTheFoxsShadow() = card(
        name = "Akemi, the Fox's Shadow",
        cost = "{1}{U}{B}",
        type = "Legendary Creature — Human Ninja",
        text = """
            Commander ninjutsu {U}{B} ({U}{B}, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand or the command zone tapped and attacking.)
            Whenever a Ninja you control deals combat damage to a player, reveal the top card of your library and put that card into your hand. Each opponent loses life equal to that card's mana value.
        """,
        pt = "1/3",
    ) parsesTo abilities(
        Keyword.CommanderNinjutsu(manaCost("{U}{B}")),
        Ability.Triggered(
            trigger = dealsCombatDamageToAPlayer(ninjaYouControl),
            instructions = steps(
                Step.Do(Effect.Reveal(Objects.LibraryCards(Players.You, Value.Fixed(1)), bind = "revealed")),
                Step.Do(Effect.Move(Objects.Bound("revealed"), to = Destination(Zone.Hand))),
                Step.Do(Effect.LoseLife(
                    Players.Each(PlayerFilter.Opponent),
                    Value.Of(NumericProperty.ManaValue, Objects.Bound("revealed"), Combine.Single),
                )),
            ),
        ),
    )
}
