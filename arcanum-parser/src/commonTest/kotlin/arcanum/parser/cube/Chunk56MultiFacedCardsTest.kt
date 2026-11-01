package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.AbilityLabel
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.ColorRef
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
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.ManaKind
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
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
import kotlin.test.Test

/**
 * Chunk 56: cards with two faces, each face parsed on its own and checked with the card's layout: modal double-faced
 * cards (CR 712.3), an adventurer card (CR 715), a split card (CR 709) and transforming double-faced cards (CR 712.2),
 * whose back faces have a colour indicator and no mana cost (CR 204, 712.8e). Their wordings: mana kept until end of
 * turn, boasting twice, impulse draw, block restrictions, Saga chapters (CR 714.2), returning transformed (CR 712.14a),
 * transforming when counters run out or tokens pile up, "transforms into" triggers (CR 701.27e), loyalty abilities with
 * a graveyard-cast permission and an emblem, and a token that becomes a creature.
 */
class Chunk56MultiFacedCardsTest {
    // ---- Shared shapes

    private val creature = ObjectFilter.Type(CardType.Creature)
    private val instantOrSorcery = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)))
    private fun yourOwn(vararg filters: ObjectFilter) = ObjectFilter.And(filters.toList() + ObjectFilter.ControlledBy(PlayerFilter.You))
    private val inYourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))
    private fun slot(name: String, filter: ObjectFilter, upToOne: Boolean = false) =
        TargetSlot(name = name, what = TargetKind.Objects(filter), min = Value.Fixed(if (upToOne) 0 else 1))
    private fun add(mana: String) = Step.Do(Effect.AddMana(ManaProduction(ManaKind.Fixed(ManaCost.parse(mana)!!))))
    private val entersSelf = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield))
    private val youCastInstantOrSorcery = Trigger.On(EventPattern.Cast(instantOrSorcery, by = PlayerFilter.You))

    /** A loyalty ability (CR 606): "[±n]: …". */
    private fun loyalty(amount: Int, instructions: Instructions) = Ability.Activated(
        cost = Cost(listOf(CostPart.Loyalty(Value.Fixed(amount)))),
        instructions = instructions,
    )

    /** "Exile [this], then return it to the battlefield transformed under [controller]'s control": a new object with its
     * back face up (CR 400.7, 712.14a). */
    private fun exileAndReturnTransformed(controller: Players) = listOf(
        Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Exile), postBind = "exiled")),
        Step.Do(Effect.Move(Objects.Bound("exiled"), to = Destination(Zone.Battlefield, transformed = true, controller = controller))),
    )

    /** "for each artifact and/or enchantment you control" */
    private val artifactsAndEnchantments = Value.Count(Objects.Each(yourOwn(
        ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Enchantment))),
    )))

    // ---- Modal double-faced cards

    /** CR 106.4: the mana stays until end of turn. Boasting twice only matters on your turns, when your creatures attack
     * (CR 702.142a). The back face's impulse draw lets lands be played too (CR 305.1). */
    @Test
    fun skaldaGodOfSagas() = cardFaces(
        layout = Layout.ModalDoubleFaced,
        card(
            name = "Skalda, God of Sagas",
            cost = "{2}{R}",
            type = "Legendary Creature — God",
            text = """
                Whenever you cast a spell, add {R}. Until end of turn, you don't lose this mana as steps and phases end.
                Creatures you control can boast twice during each of your turns rather than once.
            """,
            pt = "3/3",
        ) to abilities(
            Ability.Triggered(
                trigger = Trigger.On(EventPattern.Cast(by = PlayerFilter.You)),
                instructions = Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(
                    ManaKind.Fixed(ManaCost.parse("{R}")!!),
                    keptUntil = Duration.EndOfTurn,
                ))))),
            ),
            Ability.Static(
                mechanics = listOf(Mechanic.LabelUsesPerTurn(AbilityLabel.Boast, 2)),
                affects = Affected.Objects(Objects.Each(yourOwn(creature))),
                condition = Condition.YourTurn,
            ),
        ),
        card(
            name = "Brennor, Horn of Plenty",
            cost = "{4}{R}",
            type = "Legendary Artifact",
            text = "Discard a card: Exile the top two cards of your library. You may play those cards this turn.",
        ) to abilities(
            Ability.Activated(
                cost = Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card))))),
                instructions = Instructions(steps = listOf(
                    Step.Do(Effect.Move(Objects.LibraryCards(Players.You, Value.Fixed(2)), to = Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.ApplyMechanics(
                        mechanics = listOf(Mechanic.MayPlay(Zone.Exile, how = PlayKind.Play)),
                        affects = Affected.Objects(Objects.Bound("exiled")),
                        duration = Duration.EndOfTurn,
                    )),
                )),
            ),
        ),
    )

    /** The land face: "you may pay 3 life. If you don't, it enters tapped" is a pay, then entering tapped if not paid (CR 614.12, 118.12a). */
    @Test
    fun summitAscetic() = cardFaces(
        layout = Layout.ModalDoubleFaced,
        card(
            name = "Summit Ascetic",
            cost = "{3}{R}{R}",
            type = "Creature — Djinn Monk",
            text = """
                Prowess (Whenever you cast a noncreature spell, this creature gets +1/+1 until end of turn.)
                When this creature enters, return target instant or sorcery card from your graveyard to your hand.
            """,
            pt = "2/2",
        ) to abilities(
            Keyword.Prowess,
            Ability.Triggered(
                trigger = entersSelf,
                instructions = Instructions(
                    targets = listOf(slot("card", ObjectFilter.And(listOf(instantOrSorcery) + inYourGraveyard))),
                    steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Hand)))),
                ),
            ),
        ),
        card(
            name = "Hermit Peak",
            cost = null,
            type = "Land",
            text = """
                As this land enters, you may pay 3 life. If you don't, it enters tapped.
                {T}: Add {R}.
            """,
        ) to abilities(
            Ability.Static(listOf(Mechanic.Replacement(
                event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(
                    Step.Pay(
                        cost = Cost(listOf(CostPart.PayLife(Value.Fixed(3)))),
                        payer = Players.You,
                        bind = "paid",
                    ),
                    Step.If(
                        condition = Condition.Not(Condition.Did("paid")),
                        then = listOf(Step.Modify(Modification.EntersTapped)),
                    ),
                ))),
            ))),
            Ability.Activated(
                cost = Cost(listOf(CostPart.TapSelf)),
                instructions = Instructions(steps = listOf(add("{R}"))),
            ),
        ),
    )

    // ---- An adventurer card and a split card

    /** CR 715: the second face is the Adventure, an instant; the layout says how the two are cast. */
    @Test
    fun brashPilferer() = cardFaces(
        layout = Layout.Adventure,
        card(
            name = "Brash Pilferer",
            cost = "{1}{U}{U}",
            type = "Creature — Faerie Rogue",
            text = """
                Flash
                Flying
                This creature can block only creatures with flying.
            """,
            pt = "3/1",
        ) to abilities(
            Keyword.Flash,
            Keyword.Flying,
            Ability.Static(listOf(Mechanic.CanBlockOnly(ObjectFilter.And(listOf(creature, ObjectFilter.HasKeyword(Keyword.Flying)))))),
        ),
        card(
            name = "Petty Pinch",
            cost = "{1}{U}",
            type = "Instant — Adventure",
            text = "Return target nonland permanent an opponent controls to its owner's hand.",
        ) to abilities(
            Ability.Spell(Instructions(
                targets = listOf(slot("permanent", ObjectFilter.And(listOf(
                    ObjectFilter.Not(ObjectFilter.Type(CardType.Land)),
                    ObjectFilter.Category(ObjectCategory.Permanent),
                    ObjectFilter.ControlledBy(PlayerFilter.Opponent),
                )))),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("permanent"), to = Destination(Zone.Hand)))),
            )),
        ),
    )

    /** CR 709.3: each half is cast on its own; X is announced for the half that has it (CR 107.3a). */
    @Test
    fun expanseEruption() = cardFaces(
        layout = Layout.Split,
        card(
            name = "Expanse",
            cost = "{U/R}{U/R}",
            type = "Instant",
            text = "Copy target instant or sorcery spell with mana value 4 or less. You may choose new targets for the copy.",
        ) to abilities(
            Ability.Spell(Instructions(
                targets = listOf(slot("spell", ObjectFilter.And(listOf(
                    instantOrSorcery,
                    ObjectFilter.Category(ObjectCategory.Spell),
                    ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(4)),
                )))),
                steps = listOf(Step.Do(Effect.CopySpell(Objects.Target("spell")))),
            )),
        ),
        card(
            name = "Eruption",
            cost = "{X}{U}{U}{R}{R}",
            type = "Instant",
            text = "Eruption deals X damage to any target. Target player draws X cards.",
        ) to abilities(
            Ability.Spell(Instructions(
                targets = listOf(
                    TargetSlot(
                        name = "any",
                        what = TargetKind.Either(
                            objects = ObjectFilter.Or(listOf(
                                creature,
                                ObjectFilter.Type(CardType.Planeswalker),
                                ObjectFilter.Type(CardType.Battle),
                            )),
                            players = PlayerFilter.Any,
                        ),
                    ),
                    TargetSlot(name = "player", what = TargetKind.Players(PlayerFilter.Any)),
                ),
                steps = listOf(
                    Step.Do(Effect.DealDamage(Recipients(target = "any"), amount = Value.Announced())),
                    Step.Do(Effect.Draw(Players.Target("player"), count = Value.Announced())),
                ),
                announced = listOf("X"),
            )),
        ),
    )

    // ---- Transforming double-faced cards

    /** "Orrin" is the card's shortened name (CR 201.5c). The −3's replacement goes with the permission: only the spell
     * cast with it is exiled instead (`MayPlay.then`). The emblem's ability works in the command zone (CR 114.4). */
    @Test
    fun orrinWaywardProdigy() = cardFaces(
        layout = Layout.Transform,
        card(
            name = "Orrin, Wayward Prodigy",
            cost = "{1}{U}",
            type = "Legendary Creature — Human Wizard",
            text = "{T}: Draw a card, then discard a card. If there are five or more cards in your graveyard, exile Orrin, then return him to the battlefield transformed under his owner's control.",
            pt = "0/2",
        ) to abilities(
            Ability.Activated(
                cost = Cost(listOf(CostPart.TapSelf)),
                instructions = Instructions(steps = listOf(
                    Step.Do(Effect.Draw(Players.You, count = Value.Fixed(1))),
                    Step.Do(Effect.Discard(Players.You, count = Value.Fixed(1))),
                    Step.If(
                        condition = Condition.Exists(ObjectFilter.And(inYourGraveyard), atLeast = 5),
                        then = exileAndReturnTransformed(Players.OwnerOf(Objects.Bound("exiled"))),
                    ),
                )),
            ),
        ),
        card(
            name = "Orrin, Mind Unbound",
            cost = null,
            type = "Legendary Planeswalker — Jace",
            text = """
                [+1]: Up to one target creature gets -2/-0 until your next turn.
                [−3]: You may cast target instant or sorcery card from your graveyard this turn. If that spell would be put into your graveyard, exile it instead.
                [−9]: You get an emblem with "Whenever you cast a spell, target opponent mills five cards."
            """,
            loyalty = "5",
            colorIndicator = "U",
        ) to abilities(
            loyalty(1, Instructions(
                targets = listOf(slot("creature", creature, upToOne = true)),
                steps = listOf(Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(-2), Value.Fixed(0))),
                    affects = Affected.Objects(Objects.Target("creature")),
                    duration = Duration.UntilYourNextTurn,
                ))),
            )),
            loyalty(-3, Instructions(
                targets = listOf(slot("card", ObjectFilter.And(listOf(instantOrSorcery) + inYourGraveyard))),
                steps = listOf(Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.MayPlay(
                        from = Zone.Graveyard,
                        then = Destination(Zone.Exile),
                        thenInsteadOf = Zone.Graveyard,
                    )),
                    affects = Affected.Objects(Objects.Target("card")),
                    duration = Duration.EndOfTurn,
                ))),
            )),
            loyalty(-9, Instructions(
                steps = listOf(Step.Do(Effect.CreateEmblem(listOf(Ability.Triggered(
                    trigger = Trigger.On(EventPattern.Cast(by = PlayerFilter.You)),
                    instructions = Instructions(
                        targets = listOf(TargetSlot(name = "opponent", what = TargetKind.Players(PlayerFilter.Opponent))),
                        steps = listOf(Step.Do(Effect.Mill(Players.Target("opponent"), count = Value.Fixed(5)))),
                    ),
                ))))),
            )),
        ),
    )

    /** CR 714.2c: "I, II —" is one chapter ability for each chapter. The count is taken once, as it resolves (CR 608.2h);
     * the back face's is a continuous count (layer 7c). The lore counter line is reminder text: Sagas get their lore
     * counters by rule (CR 714.3a, c). */
    @Test
    fun harukasAgeOfHonor() = cardFaces(
        layout = Layout.Transform,
        card(
            name = "Haruka's Age of Honor",
            cost = "{1}{W}",
            type = "Enchantment — Saga",
            text = """
                (As this Saga enters and after your draw step, add a lore counter.)
                I, II — Target creature gets +1/+1 until end of turn for each artifact and/or enchantment you control.
                III — Exile this Saga, then return it to the battlefield transformed under your control.
            """,
        ) to abilities(
            Ability.Triggered(
                trigger = Trigger.Chapter(listOf(1, 2)),
                instructions = Instructions(
                    targets = listOf(slot("creature", creature)),
                    steps = listOf(Step.Do(Effect.ApplyMechanics(
                        mechanics = listOf(Mechanic.ModifyPowerToughness(artifactsAndEnchantments, artifactsAndEnchantments)),
                        affects = Affected.Objects(Objects.Target("creature")),
                        duration = Duration.EndOfTurn,
                    ))),
                ),
            ),
            Ability.Triggered(
                trigger = Trigger.Chapter(listOf(3)),
                instructions = Instructions(steps = exileAndReturnTransformed(Players.You)),
            ),
        ),
        card(
            name = "Likeness of Haruka",
            cost = null,
            type = "Enchantment Creature — Human Noble",
            text = "This creature gets +1/+1 for each artifact and/or enchantment you control.",
            pt = "0/0",
            colorIndicator = "W",
        ) to abilities(
            Ability.Static(listOf(Mechanic.ModifyPowerToughness(artifactsAndEnchantments, artifactsAndEnchantments))),
        ),
    )

    /** "Then if it has no ice counters on it" is checked as the ability resolves, after the removal; the back face's
     * trigger needs it to have that name right after it transforms (CR 701.27e). */
    @Test
    fun shapeBeneathTheFloe() = cardFaces(
        layout = Layout.Transform,
        card(
            name = "Shape Beneath the Floe",
            cost = "{1}{U}",
            type = "Creature — Horror",
            text = """
                Defender
                This creature enters with four ice counters on it.
                Whenever you cast an instant or sorcery spell, remove an ice counter from this creature. Then if it has no ice counters on it, transform it.
            """,
            pt = "0/4",
        ) to abilities(
            Keyword.Defender,
            Ability.Static(listOf(Mechanic.Replacement(
                event = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(CounterKind.Named("ice"), Value.Fixed(4))),
            ))),
            Ability.Triggered(
                trigger = youCastInstantOrSorcery,
                instructions = Instructions(steps = listOf(
                    Step.Do(Effect.RemoveCounters(Objects.Self, CounterKind.Named("ice"), amount = Value.Fixed(1))),
                    Step.If(
                        condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.Not(ObjectFilter.HasCounter(CounterKind.Named("ice")))),
                        then = listOf(Step.Do(Effect.Transform(Objects.Self))),
                    ),
                )),
            ),
        ),
        card(
            name = "Risen Leviathan",
            cost = null,
            type = "Creature — Kraken Horror",
            text = "When this creature transforms into Risen Leviathan, return all non-Horror creatures to their owners' hands.",
            pt = "7/8",
            colorIndicator = "U",
        ) to abilities(
            Ability.Triggered(
                trigger = Trigger.On(EventPattern.Transform(ObjectFilter.And(listOf(
                    ObjectFilter.Is(Objects.Self),
                    ObjectFilter.Named("Risen Leviathan"),
                )))),
                instructions = Instructions(steps = listOf(Step.Do(Effect.Move(
                    Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Sub(Subtype("Horror"))), creature))),
                    to = Destination(Zone.Hand),
                )))),
            ),
        ),
    )

    /** CR 603.4: the intervening "if" is checked when the token is created and again on resolution; the creator owns the
     * token (CR 111.2). On the back face, "becomes a 2/2 black Bat creature" has no duration, so it lasts (CR 611.2a),
     * applying in layers 4, 5, 6 and 7b (CR 613.1). */
    @Test
    fun crimsonHexcaller() = cardFaces(
        layout = Layout.Transform,
        card(
            name = "Crimson Hexcaller",
            cost = "{1}{B}",
            type = "Creature — Vampire Wizard",
            text = """
                Flying
                Whenever this creature or another nontoken creature you control dies, create a Blood token. (It's an artifact with "{1}, {T}, Discard a card, Sacrifice this token: Draw a card.")
                Whenever you create a Blood token, if you control five or more Blood tokens, transform this creature.
            """,
            pt = "2/1",
        ) to abilities(
            Keyword.Flying,
            Ability.Triggered(
                trigger = Trigger.On(EventPattern.ZoneChange(
                    ObjectFilter.Or(listOf(
                        ObjectFilter.Is(Objects.Self),
                        ObjectFilter.And(listOf(
                            ObjectFilter.Not(ObjectFilter.Is(Objects.Self)),
                            ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)),
                            creature,
                            ObjectFilter.ControlledBy(PlayerFilter.You),
                        )),
                    )),
                    from = Zone.Battlefield,
                    to = Zone.Graveyard,
                )),
                instructions = Instructions(steps = listOf(Step.Do(Effect.CreateToken(TokenSpec.Predefined("Blood"))))),
            ),
            Ability.Triggered(
                trigger = Trigger.On(EventPattern.CreateToken(ObjectFilter.And(listOf(
                    ObjectFilter.Sub(Subtype("Blood")),
                    ObjectFilter.OwnedBy(PlayerFilter.You),
                )))),
                interveningIf = Condition.Exists(
                    yourOwn(ObjectFilter.Sub(Subtype("Blood")), ObjectFilter.Category(ObjectCategory.Token)),
                    atLeast = 5,
                ),
                instructions = Instructions(steps = listOf(Step.Do(Effect.Transform(Objects.Self)))),
            ),
        ),
        card(
            name = "Duskwing Summoner",
            cost = null,
            type = "Creature — Vampire Wizard",
            text = """
                Flying
                At the beginning of combat on your turn, up to one target Blood token you control becomes a 2/2 black Bat creature with flying and haste in addition to its other types.
            """,
            pt = "3/3",
            colorIndicator = "B",
        ) to abilities(
            Keyword.Flying,
            Ability.Triggered(
                trigger = Trigger.On(EventPattern.StepBegins(TurnStep.BeginningOfCombat, PlayerFilter.You)),
                instructions = Instructions(
                    targets = listOf(slot(
                        "token",
                        yourOwn(ObjectFilter.Sub(Subtype("Blood")), ObjectFilter.Category(ObjectCategory.Token)),
                        upToOne = true,
                    )),
                    steps = listOf(Step.Do(Effect.ApplyMechanics(
                        mechanics = listOf(
                            Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Bat")))),
                            Mechanic.SetColors(listOf(ColorRef.Literal(Color.Black))),
                            Mechanic.GainAbilities(listOf(Keyword.Flying, Keyword.Haste)),
                            Mechanic.SetPowerToughness(Value.Fixed(2), Value.Fixed(2)),
                        ),
                        affects = Affected.Objects(Objects.Target("token")),
                        duration = Duration.Indefinitely,
                    ))),
                ),
            ),
        ),
    )
}
