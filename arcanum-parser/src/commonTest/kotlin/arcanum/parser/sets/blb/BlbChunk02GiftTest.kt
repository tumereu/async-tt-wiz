package arcanum.parser.sets.blb

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Gifted
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import kotlin.test.Test

/**
 * Bloomburrow chunk 02: gift (CR 702.174). "Gift a card / a Food / a tapped Fish / a Treasure" is the keyword; "the gift
 * was promised" is its optional cost paid (702.174k). On an instant or sorcery, "If the gift was promised, [effect]" is a
 * condition on resolution, and its targets are chosen only if the gift was promised (702.174m, [TargetSlot.onlyIf]);
 * "If the gift was promised, instead [effect]" replaces the effect before it, so each version has its own targets, only
 * one of which is chosen. On a permanent, "if the gift was promised" is an intervening if of its enters trigger, and
 * "whenever you give a gift" is a trigger of its own (702.174c).
 */
class BlbChunk02GiftTest {
    private val promised = Condition.Paid(Keyword.Gift.Kind, times = 1, of = Objects.Self)
    private val notPromised = Condition.Not(promised)

    private fun gift(gift: Gifted) = Keyword.Gift(gift)

    /** A target slot chosen only when [condition] holds as the spell is cast (CR 702.174m). */
    private fun slotIf(condition: Condition, name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        slot(name, filter, min, max).copy(onlyIf = condition)

    private fun spell(targets: List<TargetSlot>, vararg steps: Step) = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))

    private val yourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
    private val permanentCard = ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment,
        CardType.Land, CardType.Planeswalker).map { ObjectFilter.Type(it) })
    private val artifactOrEnchantment = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Enchantment)))

    // ---- Gift spells with an added effect

    // CR 608.2c: "that creature" is the target
    @Test
    fun crumbsForCourage() = card(
        name = "Crumbs for Courage",
        cost = "{W}",
        type = "Instant",
        text = """
            Gift a Food (You may promise an opponent a gift as you cast this spell. If you do, they create a Food token before its other effects. It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Target creature you control gets +2/+2 until end of turn. If the gift was promised, that creature also gains indestructible until end of turn.
        """,
    ) parsesTo abilities(
        gift(Gifted.Food),
        spell(
            listOf(slot("creature", creatureYouControl)),
            pump(Objects.Target("creature"), 2, 2),
            Step.If(promised, then = listOf(gains(Objects.Target("creature"), Keyword.Indestructible))),
        ),
    )

    // CR 702.11c: hexproof on a player means "You can't be the target of spells or abilities your opponents control"
    @Test
    fun daybreakAccord() = card(
        name = "Daybreak Accord",
        cost = "{1}{W}",
        type = "Instant",
        text = """
            Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
            You and permanents you control gain hexproof until end of turn. If the gift was promised, permanents you control also gain indestructible until end of turn.
        """,
    ) parsesTo abilities(
        gift(Gifted.Card),
        spell(
            emptyList(),
            Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.CantBeTargeted(by = opponents)),
                Affected.Players(Players.You),
                Duration.EndOfTurn,
            )),
            gains(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Permanent), you))), Keyword.Hexproof),
            Step.If(promised, then = listOf(
                gains(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Permanent), you))), Keyword.Indestructible),
            )),
        ),
    )

    // CR 608.2c: the promised version replaces the other, with its own targets (702.174m)
    @Test
    fun morningdewRemedy() = card(
        name = "Morningdew Remedy",
        cost = "{2}{W}",
        type = "Sorcery",
        text = """
            Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
            Return up to two target creature cards each with mana value 2 or less from your graveyard to the battlefield. If the gift was promised, instead return up to three target creature cards each with mana value 2 or less from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        gift(Gifted.Card),
        run {
            val cards = ObjectFilter.And(listOf(creature, ObjectFilter.ManaValue(Comparison.AtMost, one(2))) + yourGraveyard)
            spell(
                listOf(
                    slotIf(notPromised, "card", cards, min = 0, max = 2),
                    slotIf(promised, "promised card", cards, min = 0, max = 3),
                ),
                Step.If(
                    promised,
                    then = listOf(Step.Do(Effect.Move(Objects.Target("promised card"), Destination(Zone.Battlefield)))),
                    otherwise = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Battlefield)))),
                ),
            )
        },
    )

    // CR 702.174c: a player gives a gift as the spell resolves or the gift trigger of a permanent resolves
    @Test
    fun merryHamsters() = card(
        name = "Merry Hamsters",
        cost = "{1}{W}",
        type = "Creature — Hamster Citizen",
        pt = "2/3",
        text = "Whenever you give a gift, draw a card.",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.GiveGift(PlayerFilter.You)),
            instructions = steps(Step.Do(Effect.Draw(Players.You, one()))),
        ),
    )

    // CR 603.7: a delayed trigger, created only if the gift wasn't promised, returns the exiled card
    @Test
    fun farewellBreeze() = card(
        name = "Farewell Breeze",
        cost = "{W}{W}",
        type = "Instant",
        text = """
            Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)
            Exile target nontoken creature. If the gift wasn't promised, return that card to the battlefield under its owner's control with a +1/+1 counter on it at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        gift(Gifted.TappedFish),
        spell(
            listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)), creature)))),
            Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Exile), postBind = "exiled")),
            Step.If(notPromised, then = listOf(Step.Do(Effect.CreateDelayedTrigger(
                Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.Any)),
                steps(Step.Do(Effect.Move(
                    Objects.Bound("exiled"),
                    Destination(
                        Zone.Battlefield,
                        controller = Players.OwnerOf(Objects.Bound("exiled")),
                        counters = listOf(arcanum.api.CountersToPut(p1p1, one())),
                    ),
                    postBind = "returned",
                ))),
            )))),
        ),
    )

    // CR 608.2c: "a creature card put into your graveyard this way" is one of the destroyed creatures, now a card there
    @Test
    fun meteorBenediction() = card(
        name = "Meteor Benediction",
        cost = "{3}{W}{W}",
        type = "Sorcery",
        text = """
            Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
            Destroy all creatures. If the gift was promised, return a creature card put into your graveyard this way to the battlefield under your control.
        """,
    ) parsesTo abilities(
        gift(Gifted.Card),
        spell(
            emptyList(),
            Step.Do(Effect.Destroy(Objects.Each(creature), postBind = "destroyed")),
            Step.If(promised, then = listOf(
                Step.Choose(
                    Choice.Objects(
                        ObjectFilter.And(listOf(creature) + yourGraveyard),
                        min = one(),
                        max = one(),
                        from = Objects.Bound("destroyed"),
                    ),
                    bind = "chosen",
                    chooser = Players.You,
                ),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Battlefield, controller = Players.You))),
            )),
        ),
    )

    // ---- "instead" with other targets (CR 702.174m)

    @Test
    fun downTheWhirlpool() = card(
        name = "Down the Whirlpool",
        cost = "{U}",
        type = "Instant",
        text = """
            Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)
            Return target creature an opponent controls to its owner's hand. If the gift was promised, instead return target nonland permanent an opponent controls to its owner's hand.
        """,
    ) parsesTo abilities(
        gift(Gifted.TappedFish),
        spell(
            listOf(
                slotIf(notPromised, "creature", ObjectFilter.And(listOf(creature, opponents))),
                slotIf(promised, "permanent", ObjectFilter.And(listOf(ObjectFilter.Not(land), ObjectFilter.Category(ObjectCategory.Permanent), opponents))),
            ),
            Step.If(
                promised,
                then = listOf(Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Hand)))),
                otherwise = listOf(Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Hand)))),
            ),
        ),
    )

    // CR 303.4e: "enchanted creature"; CR 122.1d: stun counters
    @Test
    fun pawsnatch() = card(
        name = "Pawsnatch",
        cost = "{2}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Gift a card (You may promise an opponent a gift as you cast this spell. If you do, when it enters, they draw a card.)
            Enchant creature
            When this Aura enters, tap enchanted creature. If the gift wasn't promised, put three stun counters on it.
            You control enchanted creature.
        """,
    ) parsesTo abilities(
        gift(Gifted.Card),
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Triggered(
            trigger = thisEnters,
            instructions = steps(
                Step.Do(Effect.Tap(Objects.AttachedTo(Objects.Self))),
                Step.If(notPromised, then = listOf(counters(Objects.AttachedTo(Objects.Self), 3, CounterKind.Stun))),
            ),
        ),
        Ability.Static(listOf(Mechanic.ControlledBy(Players.You)), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
    )

    @Test
    fun undertowsClaim() = card(
        name = "Undertow's Claim",
        cost = "{U}{U}",
        type = "Instant",
        text = """
            Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
            Counter target creature spell. If the gift was promised, instead counter target spell.
        """,
    ) parsesTo abilities(
        gift(Gifted.Card),
        spell(
            listOf(
                slotIf(notPromised, "spell", ObjectFilter.And(listOf(creature, ObjectFilter.Category(ObjectCategory.Spell)))),
                slotIf(promised, "promised spell", ObjectFilter.Category(ObjectCategory.Spell)),
            ),
            Step.If(
                promised,
                then = listOf(Step.Do(Effect.Counter(Objects.Target("promised spell")))),
                otherwise = listOf(Step.Do(Effect.Counter(Objects.Target("spell")))),
            ),
        ),
    )

    @Test
    fun thoughtEddy() = card(
        name = "Thought Eddy",
        cost = "{4}{U}",
        type = "Sorcery",
        text = """
            Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)
            Target player draws three cards. If the gift was promised, tap target creature an opponent controls and put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)
        """,
    ) parsesTo abilities(
        gift(Gifted.TappedFish),
        spell(
            listOf(targetPlayer, slotIf(promised, "creature", ObjectFilter.And(listOf(creature, opponents)))),
            Step.Do(Effect.Draw(Players.Target("player"), one(3))),
            Step.If(promised, then = listOf(
                Step.Do(Effect.Tap(Objects.Target("creature"))),
                counters(Objects.Target("creature"), kind = CounterKind.Stun),
            )),
        ),
    )

    // CR 707.9b: "except it's 1/1"; a legendary creature isn't copied
    @Test
    fun twistingRenewal() = card(
        name = "Twisting Renewal",
        cost = "{3}{B}{B}",
        type = "Sorcery",
        text = """
            Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
            Return target creature card from your graveyard to the battlefield. Then if the gift was promised and that creature isn't legendary, create a token that's a copy of that creature, except it's 1/1.
        """,
    ) parsesTo abilities(
        gift(Gifted.Card),
        spell(
            listOf(slot("card", ObjectFilter.And(listOf(creature) + yourGraveyard))),
            Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Battlefield), postBind = "returned")),
            Step.If(
                Condition.And(listOf(
                    promised,
                    Condition.ObjectMatches(Objects.Bound("returned"), ObjectFilter.Not(ObjectFilter.Super(Supertype.Legendary))),
                )),
                then = listOf(Step.Do(Effect.CreateToken(
                    TokenSpec.CopyOf(Objects.Bound("returned"), except = listOf(CopyException.PowerToughness(1, 1))),
                    count = one(),
                    controller = Players.You,
                ))),
            ),
        ),
    )

    // CR 701.21a: the opponent chooses which of their greatest-power creatures to sacrifice
    @Test
    fun devouredByWant() = card(
        name = "Devoured by Want",
        cost = "{1}{B}{B}",
        type = "Instant",
        text = """
            Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
            Target opponent sacrifices a creature with the greatest power among creatures they control. If the gift was promised, return target creature card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        gift(Gifted.Card),
        run {
            val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("opponent")))
            spell(
                listOf(targetOpponent, slotIf(promised, "card", ObjectFilter.And(listOf(creature) + yourGraveyard))),
                Step.Choose(
                    Choice.Objects(
                        ObjectFilter.And(listOf(
                            creature,
                            theirs,
                            ObjectFilter.Power(Comparison.Equal, Value.Of(
                                NumericProperty.Power,
                                Objects.Each(ObjectFilter.And(listOf(creature, theirs))),
                                Combine.Greatest,
                            )),
                        )),
                        min = one(),
                        max = one(),
                    ),
                    bind = "sacrificed",
                    chooser = Players.Target("opponent"),
                ),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
                Step.If(promised, then = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand))))),
            )
        },
    )

    // CR 601.2a, D49: "for as long as it remains exiled" lasts while it stays there; mana of any type may pay for it
    @Test
    fun sharpclawsRobbery() = card(
        name = "Sharpclaw's Robbery",
        cost = "{B}{B}",
        type = "Sorcery",
        text = """
            Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
            Target opponent reveals their hand. You choose a nonland card from it. Exile that card. If the gift was promised, you may cast that card for as long as it remains exiled, and mana of any type can be spent to cast it.
        """,
    ) parsesTo abilities(
        gift(Gifted.Card),
        spell(
            listOf(targetOpponent),
            Step.Do(Effect.Reveal(
                Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent")))))),
                bind = "revealed",
            )),
            Step.Choose(
                Choice.Objects(ObjectFilter.Not(land), min = one(), max = one(), from = Objects.Bound("revealed")),
                bind = "chosen",
                chooser = Players.You,
            ),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Exile), postBind = "exiled")),
            Step.If(promised, then = listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Cast, manaAsAnyColor = true, holder = PlayerFilter.You)),
                Affected.Objects(Objects.Bound("exiled")),
                Duration.Indefinitely,
            )))),
        ),
    )

    @Test
    fun midnightCraving() = card(
        name = "Midnight Craving",
        cost = "{2}{B}",
        type = "Instant",
        text = """
            Gift a Food (You may promise an opponent a gift as you cast this spell. If you do, they create a Food token before its other effects. It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Destroy target creature. If the gift wasn't promised, you lose 2 life.
        """,
    ) parsesTo abilities(
        gift(Gifted.Food),
        spell(
            listOf(slot("creature", creature)),
            Step.Do(Effect.Destroy(Objects.Target("creature"))),
            Step.If(notPromised, then = listOf(Step.Do(Effect.LoseLife(Players.You, one(2))))),
        ),
    )

    @Test
    fun blossomBurst() = card(
        name = "Blossom Burst",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Gift a Treasure (You may promise an opponent a gift as you cast this spell. If you do, they create a Treasure token before its other effects. It's an artifact with "{T}, Sacrifice this token: Add one mana of any color.")
            Blossom Burst deals 2 damage to target creature. If the gift was promised, Blossom Burst also deals 3 damage to that creature's controller.
        """,
    ) parsesTo abilities(
        gift(Gifted.Treasure),
        spell(
            listOf(slot("creature", creature)),
            Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("creature")), one(2), source = Objects.Self)),
            Step.If(promised, then = listOf(Step.Do(Effect.DealDamage(
                Recipients(players = Players.ControllerOf(Objects.Target("creature"))),
                one(3),
                source = Objects.Self,
            )))),
        ),
    )

    // CR 601.2f: the discard is an additional cost; the gift's optional cost is another
    @Test
    fun mushroomTonic() = card(
        name = "Mushroom Tonic",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)
            As an additional cost to cast this spell, discard a card.
            Target player draws two cards. If the gift was promised, target creature you control gets +2/+0 until end of turn.
        """,
    ) parsesTo abilities(
        gift(Gifted.TappedFish),
        Ability.Static(
            listOf(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Discard(CostObjects.Choose(anyCard, count = one())))))),
            affects = Affected.Objects(Objects.Self),
        ),
        spell(
            listOf(targetPlayer, slotIf(promised, "creature", creatureYouControl)),
            Step.Do(Effect.Draw(Players.Target("player"), one(2))),
            Step.If(promised, then = listOf(pump(Objects.Target("creature"), 2, 0))),
        ),
    )

    @Test
    fun meadowMuster() = card(
        name = "Meadow Muster",
        cost = "{2}{R}",
        type = "Instant",
        text = """
            Gift a Food (You may promise an opponent a gift as you cast this spell. If you do, they create a Food token before its other effects. It's an artifact with "{2}, {T}, Sacrifice this token: You gain 3 life.")
            Creatures you control get +2/+0 until end of turn. If the gift was promised, target creature you control gains first strike until end of turn.
        """,
    ) parsesTo abilities(
        gift(Gifted.Food),
        spell(
            listOf(slotIf(promised, "creature", creatureYouControl)),
            pump(Objects.Each(creatureYouControl), 2, 0),
            Step.If(promised, then = listOf(gains(Objects.Target("creature"), Keyword.FirstStrike))),
        ),
    )

    @Test
    fun brushfireWail() = card(
        name = "Brushfire Wail",
        cost = "{1}{R}{R}",
        type = "Sorcery",
        text = """
            Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
            Brushfire Wail deals 2 damage to each creature. If the gift was promised, instead Brushfire Wail deals 1 damage to any target and 2 damage to each creature.
        """,
    ) parsesTo abilities(
        gift(Gifted.Card),
        run {
            val each = Step.Do(Effect.DealDamage(Recipients(objects = Objects.Each(creature)), one(2), source = Objects.Self))
            spell(
                listOf(TargetSlot(
                    "any",
                    TargetKind.Either(
                        ObjectFilter.Or(listOf(CardType.Creature, CardType.Planeswalker, CardType.Battle).map { ObjectFilter.Type(it) }),
                        PlayerFilter.Any,
                    ),
                    min = one(),
                    max = one(),
                    onlyIf = promised,
                )),
                Step.If(
                    promised,
                    then = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), one(), source = Objects.Self)), each),
                    otherwise = listOf(each),
                ),
            )
        },
    )

    // CR 701.14a: fighting; the second target is named after its whole phrase, the first having taken "creature"
    @Test
    fun reedbedScuffle() = card(
        name = "Reedbed Scuffle",
        cost = "{G}",
        type = "Sorcery",
        text = """
            Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, they create a tapped 1/1 blue Fish creature token before its other effects.)
            Choose target creature you control and target creature you don't control. Put a +1/+1 counter on the creature you control if the gift was promised. Then those creatures fight each other.
        """,
    ) parsesTo abilities(
        gift(Gifted.TappedFish),
        spell(
            listOf(
                slot("creature", creatureYouControl),
                slot("creature you don't control", ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Not(PlayerFilter.You))))),
            ),
            Step.If(promised, then = listOf(counters(Objects.Target("creature")))),
            Step.Do(Effect.Fight(Objects.Target("creature"), Objects.Target("creature you don't control"))),
        ),
    )

    @Test
    fun thriftySalvage() = card(
        name = "Thrifty Salvage",
        cost = "{1}{G}",
        type = "Instant",
        text = """
            Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
            Return target permanent card from your graveyard to your hand. If the gift was promised, instead return two target permanent cards from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        gift(Gifted.Card),
        run {
            val cards = ObjectFilter.And(listOf(permanentCard) + yourGraveyard)
            spell(
                listOf(slotIf(notPromised, "card", cards), slotIf(promised, "promised card", cards, min = 2, max = 2)),
                Step.If(
                    promised,
                    then = listOf(Step.Do(Effect.Move(Objects.Target("promised card"), Destination(Zone.Hand)))),
                    otherwise = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Hand)))),
                ),
            )
        },
    )

    // CR 702.174b: on a permanent, the gift is its own enters trigger; this one checks the promise as an intervening if
    @Test
    fun junkyardSlinger() = card(
        name = "Junkyard Slinger",
        cost = "{1}{G}{G}",
        type = "Creature — Raccoon Archer",
        pt = "4/4",
        text = """
            Gift a card (You may promise an opponent a gift as you cast this spell. If you do, when it enters, they draw a card.)
            Reach
            When this creature enters, if the gift was promised, destroy target artifact or enchantment an opponent controls.
        """,
    ) parsesTo abilities(
        gift(Gifted.Card),
        Keyword.Reach,
        Ability.Triggered(
            trigger = thisEnters,
            instructions = Instructions(
                targets = listOf(slot("permanent", ObjectFilter.And(listOf(artifactOrEnchantment, opponents)))),
                steps = listOf(Step.Do(Effect.Destroy(Objects.Target("permanent")))),
            ),
            interveningIf = promised,
        ),
    )

    @Test
    fun grindAway() = card(
        name = "Grind Away",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Gift a card (You may promise an opponent a gift as you cast this spell. If you do, they draw a card before its other effects.)
            Destroy target artifact or enchantment. If the gift was promised, instead destroy two target artifacts and/or enchantments.
        """,
    ) parsesTo abilities(
        gift(Gifted.Card),
        spell(
            listOf(
                slotIf(notPromised, "permanent", artifactOrEnchantment),
                slotIf(promised, "promised permanent", artifactOrEnchantment, min = 2, max = 2),
            ),
            Step.If(
                promised,
                then = listOf(Step.Do(Effect.Destroy(Objects.Target("promised permanent")))),
                otherwise = listOf(Step.Do(Effect.Destroy(Objects.Target("permanent")))),
            ),
        ),
    )

    // CR 613.1f: "loses flying" in layer 6; CR 702.6a: equip
    @Test
    fun cometforgedBlade() = card(
        name = "Cometforged Blade",
        cost = "{4}",
        type = "Artifact — Equipment",
        text = """
            Gift a tapped Fish (You may promise an opponent a gift as you cast this spell. If you do, when it enters, they create a tapped 1/1 blue Fish creature token.)
            When this Equipment enters, if the gift was promised, attach this Equipment to target creature you control.
            Equipped creature gets +3/+3 and loses flying.
            Equip {3}
        """,
    ) parsesTo abilities(
        gift(Gifted.TappedFish),
        Ability.Triggered(
            trigger = thisEnters,
            instructions = Instructions(
                targets = listOf(slot("creature", creatureYouControl)),
                steps = listOf(Step.Do(Effect.Attach(Objects.Self, Recipients(objects = Objects.Target("creature"))))),
            ),
            interveningIf = promised,
        ),
        Ability.Static(
            listOf(Mechanic.ModifyPowerToughness(one(3), one(3)), Mechanic.LoseAbilities(Keyword.Flying)),
            affects = Affected.Objects(Objects.AttachedTo(Objects.Self)),
        ),
        Keyword.Equip(manaCost("{3}")),
    )
}
