package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.ActivationRules
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
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 21: artifacts that count with counters (charge, story, wish), artifact token makers, triggers on entering,
 * leaving, casting, discarding and making tokens (batched "one or more", a modal trigger), an O-Ring-style exile, a
 * graveyard permission, and artifacts linked to the cards they exiled (CR 607.2a).
 */
class Chunk21CountersTriggersAndLinkedArtifactsTest {
    // ---- Shared shapes

    private val sacrificeSelf = CostPart.Sacrifice(CostObjects.Self)
    private fun mana(text: String) = CostPart.Mana(ManaCost.parse(text)!!)
    private fun draw(n: Int) = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(n)))
    private val youControl = ObjectFilter.ControlledBy(PlayerFilter.You)
    private val creature = ObjectFilter.Type(CardType.Creature)
    private val notLand = ObjectFilter.Not(ObjectFilter.Type(CardType.Land))
    private val permanent = ObjectFilter.Category(ObjectCategory.Permanent)
    private val charge = CounterKind.Named("charge")

    private fun activated(
        vararg cost: CostPart,
        steps: List<Step>,
        rules: ActivationRules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
    ) = Ability.Activated(
        cost = Cost(cost.toList()),
        instructions = Instructions(steps = steps),
        rules = rules,
    )

    private fun triggered(vararg events: EventPattern, steps: List<Step>) = Ability.Triggered(
        trigger = Trigger.On(events.toList()),
        instructions = Instructions(steps = steps),
    )

    private val selfEnters = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)

    /** "Put [n] [kind] counters on this artifact." */
    private fun putOnSelf(kind: CounterKind, n: Int = 1) = Step.Do(Effect.PutCounters(Objects.Self, kind, amount = Value.Fixed(n)))

    /** "Remove [n] [kind] counters from this artifact" as a cost. */
    private fun removeFromSelf(kind: CounterKind, n: Int = 1) = CostPart.RemoveCounters(
        kind,
        amount = Value.Fixed(n),
        from = CostObjects.Self,
    )

    /** "with mana value equal to the number of charge counters on this artifact" (CR 608.2h: once it has left the
     * battlefield, its last known information) */
    private val manaValueEqualToCharge = ObjectFilter.ManaValue(
        Comparison.Equal,
        Value.Of(NumericProperty.Counters(charge), Objects.Self, Combine.Single),
    )

    private val destroyNonlandWithChargeManaValue = Step.Do(Effect.Destroy(Objects.Each(ObjectFilter.And(listOf(
        notLand,
        permanent,
        manaValueEqualToCharge,
    )))))

    /** A colorless artifact creature token ("a 1/1 colorless Servo artifact creature token"). */
    private fun artifactToken(subtype: String, power: Int, toughness: Int, vararg keywords: Keyword) = TokenSpec.Defined(FaceDefinition(
        name = "$subtype Token",
        colorIndicator = emptyList(),
        types = listOf(CardType.Artifact, CardType.Creature),
        subtypes = listOf(Subtype(subtype)),
        power = PrintedNumber.Fixed(power),
        toughness = PrintedNumber.Fixed(toughness),
        abilities = keywords.toList(),
    ))

    private fun creatureToken(subtype: String, power: Int, toughness: Int, color: Color, vararg keywords: Keyword) = TokenSpec.Defined(FaceDefinition(
        name = "$subtype Token",
        colorIndicator = listOf(color),
        types = listOf(CardType.Creature),
        subtypes = listOf(Subtype(subtype)),
        power = PrintedNumber.Fixed(power),
        toughness = PrintedNumber.Fixed(toughness),
        abilities = keywords.toList(),
    ))

    private fun create(token: TokenSpec) = Step.Do(Effect.CreateToken(
        token,
        count = Value.Fixed(1),
        controller = Players.You,
    ))

    // ---- Counters on the artifact

    @Test
    fun etherFlask() = card(
        name = "Ether Flask",
        cost = "{1}",
        type = "Artifact",
        text = """
            At the beginning of your upkeep, you may put a charge counter on this artifact.
            {T}: You may put a creature card with mana value equal to the number of charge counters on this artifact from your hand onto the battlefield.
        """,
    ) parsesTo abilities(
        triggered(
            EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You),
            steps = listOf(Step.May(listOf(putOnSelf(charge)), player = Players.You)),
        ),
        // "You may put a card …" is a choice of zero or one cards
        activated(
            CostPart.TapSelf,
            steps = listOf(
                Step.Choose(
                    choice = Choice.Objects(
                        filter = ObjectFilter.And(listOf(
                            creature,
                            manaValueEqualToCharge,
                            ObjectFilter.InZone(Zone.Hand),
                            ObjectFilter.OwnedBy(PlayerFilter.You),
                        )),
                        min = Value.Fixed(0),
                        max = Value.Fixed(1),
                    ),
                    bind = "chosen",
                    chooser = Players.You,
                ),
                Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Battlefield))),
            ),
        ),
    )

    @Test
    fun clockworkCharge() = card(
        name = "Clockwork Charge",
        cost = "{2}",
        type = "Artifact",
        text = """
            {T}: Put a charge counter on this artifact.
            {T}, Sacrifice this artifact: Destroy each nonland permanent with mana value equal to the number of charge counters on this artifact.
        """,
    ) parsesTo abilities(
        activated(CostPart.TapSelf, steps = listOf(putOnSelf(charge))),
        activated(CostPart.TapSelf, sacrificeSelf, steps = listOf(destroyNonlandWithChargeManaValue)),
    )

    @Test
    fun tunedDetonators() = card(
        name = "Tuned Detonators",
        cost = "{X}",
        type = "Artifact",
        text = """
            Sunburst (This artifact enters with a charge counter on it for each color of mana spent to cast it.)
            {2}, Sacrifice this artifact: Destroy each nonland permanent with mana value equal to the number of charge counters on this artifact.
        """,
    ) parsesTo abilities(
        Keyword.Sunburst,
        activated(mana("{2}"), sacrificeSelf, steps = listOf(destroyNonlandWithChargeManaValue)),
    )

    @Test
    fun broodVat() = card(
        name = "Brood Vat",
        cost = "{2}",
        type = "Artifact",
        text = """
            Sacrifice a creature: Put a charge counter on this artifact.
            {1}, Remove two charge counters from this artifact: Create a 2/2 colorless Spawn artifact creature token.
        """,
    ) parsesTo abilities(
        activated(
            CostPart.Sacrifice(CostObjects.Choose(creature, count = Value.Fixed(1))),
            steps = listOf(putOnSelf(charge)),
        ),
        activated(mana("{1}"), removeFromSelf(charge, 2), steps = listOf(create(artifactToken("Spawn", 2, 2)))),
    )

    @Test
    fun rodOfTheFabulist() = card(
        name = "Rod of the Fabulist",
        cost = "{1}{W}",
        type = "Artifact",
        text = """
            When this artifact enters, create a 1/1 white Spirit creature token with flying.
            Whenever you create one or more creature tokens, put a story counter on this artifact.
            {W}, {T}, Remove a story counter from this artifact: Draw a card.
        """,
    ) parsesTo abilities(
        triggered(selfEnters, steps = listOf(create(creatureToken("Spirit", 1, 1, Color.White, Keyword.Flying)))),
        // "One or more" triggers once for the batch; CR 111.2: the player who creates a token is its owner
        Ability.Triggered(
            trigger = Trigger.On(
                EventPattern.CreateToken(ObjectFilter.And(listOf(creature, ObjectFilter.OwnedBy(PlayerFilter.You)))),
                oneOrMore = true,
            ),
            instructions = Instructions(steps = listOf(putOnSelf(CounterKind.Named("story")))),
        ),
        activated(mana("{W}"), CostPart.TapSelf, removeFromSelf(CounterKind.Named("story")), steps = listOf(draw(1))),
    )

    @Test
    fun wishfangAmulet() = card(
        name = "Wishfang Amulet",
        cost = "{1}{B}",
        type = "Artifact",
        text = """
            This artifact enters with three wish counters on it.
            {1}, {T}, Remove a wish counter from this artifact: Search your library for a card, put it into your hand, then shuffle. An opponent gains control of this artifact. Activate only during your turn.
        """,
    ) parsesTo abilities(
        // CR 614.1c: "enters with" is a replacement effect
        Ability.Static(listOf(Mechanic.Replacement(
            event = selfEnters,
            modify = listOf(Modification.EntersWithCounters(CounterKind.Named("wish"), amount = Value.Fixed(3))),
        )), affects = Affected.Objects(Objects.Self)),
        activated(
            mana("{1}"), CostPart.TapSelf, removeFromSelf(CounterKind.Named("wish")),
            steps = listOf(
                Step.Do(Effect.Search(
                    Players.You,
                    filter = ObjectFilter.Category(ObjectCategory.Card),
                    bind = "found",
                    max = Value.Fixed(1),
                    zones = listOf(Zone.Library),
                    searcher = Players.You,
                )),
                Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Hand))),
                Step.Do(Effect.Shuffle(Players.You)),
                // The opponent is chosen as it resolves; gaining control lasts indefinitely
                Step.Choose(Choice.Player(PlayerFilter.Opponent), bind = "opponent", chooser = Players.You),
                Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.ControlledBy(Players.Bound("opponent"))),
                    affects = Affected.Objects(Objects.Self),
                    duration = Duration.Indefinitely,
                )),
            ),
            rules = ActivationRules(
                condition = Condition.YourTurn,
                timing = Timing.Any,
                activatedBy = PlayerFilter.You,
            ),
        ),
    )

    // ---- Token makers

    @Test
    fun refittingWorkshop() = card(
        name = "Refitting Workshop",
        cost = "{1}",
        type = "Artifact",
        text = """
            {3}: Untap this artifact.
            {2}, {T}: Create a 1/1 colorless Servo artifact creature token.
            {1}, {T}, Sacrifice a Servo: Create a 1/1 colorless Thopter artifact creature token with flying.
            {T}, Sacrifice a Thopter: Create a 4/4 colorless Construct artifact creature token.
        """,
    ) parsesTo abilities(
        activated(mana("{3}"), steps = listOf(Step.Do(Effect.Untap(Objects.Self)))),
        activated(mana("{2}"), CostPart.TapSelf, steps = listOf(create(artifactToken("Servo", 1, 1)))),
        activated(
            mana("{1}"), CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Choose(
                ObjectFilter.Sub(Subtype("Servo")),
                count = Value.Fixed(1),
            )),
            steps = listOf(create(artifactToken("Thopter", 1, 1, Keyword.Flying))),
        ),
        activated(
            CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Choose(
                ObjectFilter.Sub(Subtype("Thopter")),
                count = Value.Fixed(1),
            )),
            steps = listOf(create(artifactToken("Construct", 4, 4))),
        ),
    )

    @Test
    fun prototypeReplicator() = card(
        name = "Prototype Replicator",
        cost = "{R}",
        type = "Artifact",
        text = """
            When this artifact enters or leaves the battlefield, exile the top card of your library. Until end of turn, you may play that card.
            {2}{R}, Sacrifice this artifact: Create a 2/2 white Samurai creature token with vigilance. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        // One ability with two trigger events (CR 603.10a: the leaving one looks back in time)
        triggered(
            selfEnters,
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield),
            steps = listOf(
                Step.Do(Effect.Move(
                    Objects.LibraryCards(Players.You, count = Value.Fixed(1), fromTop = true),
                    to = Destination(Zone.Exile),
                    postBind = "exiled",
                )),
                Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You)),
                    affects = Affected.Objects(Objects.Bound("exiled")),
                    duration = Duration.EndOfTurn,
                )),
            ),
        ),
        activated(
            mana("{2}{R}"), sacrificeSelf,
            steps = listOf(create(creatureToken("Samurai", 2, 2, Color.White, Keyword.Vigilance))),
            rules = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun originVault() = card(
        name = "Origin Vault",
        cost = "{2}",
        type = "Artifact",
        text = "Whenever a nontoken creature enters, if this artifact is untapped, that creature's controller creates a 1/1 colorless Myr artifact creature token.",
    ) parsesTo abilities(
        // CR 603.4: an intervening "if", checked when it triggers and again on resolution
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(
                objects = ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)), creature)),
                to = Zone.Battlefield,
            )),
            interveningIf = Condition.ObjectMatches(Objects.Self, ObjectFilter.Not(ObjectFilter.HasStatus(Status.Tapped))),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.CreateToken(
                    artifactToken("Myr", 1, 1),
                    controller = Players.ControllerOf(Objects.EventObject(EventRole.After)),
                    count = Value.Fixed(1),
                ))),
            ),
        ),
    )

    @Test
    fun demonCultForge() = card(
        name = "Demon-Cult Forge",
        cost = "{B}{R}",
        type = "Artifact",
        text = """
            Whenever one or more artifacts you control leave the battlefield during your turn, create a 1/1 colorless Construct artifact creature token. This ability triggers only once each turn.
            {T}, Sacrifice an artifact: This artifact deals 1 damage to each opponent. You gain 1 life.
        """,
    ) parsesTo abilities(
        // "During your turn" is part of the trigger event, checked only as it would trigger (no "if", CR 603.4)
        Ability.Triggered(
            trigger = Trigger.On(
                EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Artifact), youControl)), from = Zone.Battlefield),
                oneOrMore = true,
            ),
            instructions = Instructions(steps = listOf(create(artifactToken("Construct", 1, 1)))),
            perTurn = 1,
            triggersOnlyIf = Condition.YourTurn,
        ),
        activated(
            CostPart.TapSelf, CostPart.Sacrifice(CostObjects.Choose(
                ObjectFilter.Type(CardType.Artifact),
                count = Value.Fixed(1),
            )),
            steps = listOf(
                Step.Do(Effect.DealDamage(
                    Recipients(players = Players.Each(PlayerFilter.Opponent)),
                    amount = Value.Fixed(1),
                    source = Objects.Self,
                )),
                Step.Do(Effect.GainLife(Players.You, amount = Value.Fixed(1))),
            ),
        ),
    )

    // ---- Triggers on casting, entering and discarding

    @Test
    fun stormfluxCistern() = card(
        name = "Stormflux Cistern",
        cost = "{4}",
        type = "Artifact",
        text = """
            Whenever you cast a spell, you gain 1 life for each spell you've cast this turn.
            Pay 50 life: This artifact deals 50 damage to any target.
        """,
    ) parsesTo abilities(
        // The spell that triggered it counts: it was cast this turn
        triggered(
            EventPattern.Cast(by = PlayerFilter.You, spell = ObjectFilter.Any),
            steps = listOf(Step.Do(Effect.GainLife(
                Players.You,
                amount = Value.EventsThisTurn(
                    EventPattern.Cast(by = PlayerFilter.You, spell = ObjectFilter.Any),
                    measure = Measure.Events,
                ),
            ))),
        ),
        Ability.Activated(
            cost = Cost(listOf(CostPart.PayLife(Value.Fixed(50)))),
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    name = "any",
                    what = TargetKind.Either(
                        objects = ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle))),
                        players = PlayerFilter.Any,
                    ),
                    min = Value.Fixed(1),
                    max = Value.Fixed(1),
                )),
                steps = listOf(Step.Do(Effect.DealDamage(
                    Recipients(target = "any"),
                    amount = Value.Fixed(50),
                    source = Objects.Self,
                ))),
            ),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun bileFountain() = card(
        name = "Bile Fountain",
        cost = "{2}",
        type = "Artifact",
        text = "When this artifact enters or is put into a graveyard from the battlefield, draw a card.",
    ) parsesTo abilities(
        triggered(
            selfEnters,
            EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard),
            steps = listOf(draw(1)),
        ),
    )

    @Test
    fun memorialToPerseverance() = card(
        name = "Memorial to Perseverance",
        cost = "{3}",
        type = "Artifact",
        text = """
            Whenever you discard a card, choose one that hasn't been chosen this turn —
            • Draw a card.
            • Create a Treasure token.
            • Each opponent loses 3 life.
        """,
    ) parsesTo abilities(
        // CR 700.2b: the mode is chosen as the ability is put on the Stack
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Discard(players = PlayerFilter.You, cards = ObjectFilter.Any)),
            instructions = Instructions(
                modes = Modes(
                    options = listOf(
                        Mode(Instructions(steps = listOf(draw(1)))),
                        Mode(Instructions(steps = listOf(create(TokenSpec.Predefined("Treasure"))))),
                        Mode(Instructions(steps = listOf(Step.Do(Effect.LoseLife(Players.Each(PlayerFilter.Opponent), amount = Value.Fixed(3)))))),
                    ),
                    onlyUnchosenThisTurn = true,
                    min = 1,
                    max = 1,
                    chooser = Players.You,
                ),
            ),
        ),
    )

    // ---- Exile and graveyard permissions

    @Test
    fun pocketRift() = card(
        name = "Pocket Rift",
        cost = "{W}",
        type = "Artifact",
        text = "When this artifact enters, exile target nonland permanent an opponent controls with mana value 2 or less until this artifact leaves the battlefield.",
    ) parsesTo abilities(
        // CR 610.3: the card returns the moment this artifact leaves, without a trigger
        Ability.Triggered(
            trigger = Trigger.On(selfEnters),
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    name = "permanent",
                    what = TargetKind.Objects(ObjectFilter.And(listOf(
                        notLand,
                        permanent,
                        ObjectFilter.ControlledBy(PlayerFilter.Opponent),
                        ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(2)),
                    ))),
                    min = Value.Fixed(1),
                    max = Value.Fixed(1),
                )),
                steps = listOf(Step.Do(Effect.Move(Objects.Target("permanent"), to = Destination(Zone.Exile), untilLeaves = Objects.Self))),
            ),
        ),
    )

    @Test
    fun cauldronOfRealms() = card(
        name = "Cauldron of Realms",
        cost = "{3}",
        type = "Artifact",
        text = "You may play lands from your graveyard.",
    ) parsesTo abilities(
        // CR 305.1: playing a land this way still uses a land play
        Ability.Static(
            mechanics = listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                ObjectFilter.Type(CardType.Land),
                ObjectFilter.InZone(Zone.Graveyard),
                ObjectFilter.OwnedBy(PlayerFilter.You),
            )))),
        ),
    )

    // ---- Linked to the cards they exiled (CR 607.2a)

    @Test
    fun morwensSpiritKettle() = card(
        name = "Morwen's Spirit Kettle",
        cost = "{2}",
        type = "Legendary Artifact",
        text = """
            You may spend mana as though it were mana of any color to activate abilities of creatures you control.
            Creatures you control with +1/+1 counters on them have all activated abilities of all creature cards exiled with Morwen's Spirit Kettle.
            {T}: Exile target card from a graveyard. When a creature card is exiled this way, put a +1/+1 counter on target creature you control.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.SpendManaAsAnyColor(
                onlyFor = ObjectFilter.AbilityOf(source = ObjectFilter.And(listOf(creature, youControl))),
            )),
            affects = Affected.Players(Players.You),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilitiesOf(Objects.Where(Objects.Remembered("exiled"), creature))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                creature,
                youControl,
                ObjectFilter.HasCounter(CounterKind.PowerToughness(1, 1), atLeast = 1),
            )))),
        ),
        // CR 603.12: "When … this way" is a reflexive trigger, its target chosen when it triggers
        Ability.Activated(
            cost = Cost(listOf(CostPart.TapSelf)),
            instructions = Instructions(
                targets = listOf(TargetSlot(
                    name = "card",
                    what = TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard)))),
                    min = Value.Fixed(1),
                    max = Value.Fixed(1),
                )),
                steps = listOf(
                    Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
                    Step.If(
                        condition = Condition.ObjectMatches(Objects.Bound("exiled"), creature),
                        then = listOf(Step.Do(Effect.ReflexiveTrigger(Instructions(
                            targets = listOf(TargetSlot(
                                name = "creature",
                                what = TargetKind.Objects(ObjectFilter.And(listOf(creature, youControl))),
                                min = Value.Fixed(1),
                                max = Value.Fixed(1),
                            )),
                            steps = listOf(Step.Do(Effect.PutCounters(Objects.Target("creature"), CounterKind.PowerToughness(1, 1), amount = Value.Fixed(1)))),
                        )))),
                    ),
                ),
            ),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun coinExchanger() = card(
        name = "Coin Exchanger",
        cost = "{1}",
        type = "Artifact",
        text = """
            Whenever you discard a card, you may exile that card from your graveyard.
            {2}, {T}: Draw a card, then discard a card.
            {T}: Put a card exiled with this artifact into its owner's graveyard. If it's a land card, create a Treasure token. If it's a nonland card, create a 2/2 black Rogue creature token.
        """,
    ) parsesTo abilities(
        // Only from the graveyard: if the discarded card went elsewhere, it isn't exiled
        triggered(
            EventPattern.Discard(players = PlayerFilter.You, cards = ObjectFilter.Any),
            steps = listOf(Step.May(listOf(
                Step.Do(Effect.Move(
                    Objects.Where(Objects.EventObject(EventRole.After), ObjectFilter.InZone(Zone.Graveyard)),
                    to = Destination(Zone.Exile),
                    postBind = "exiled",
                )),
                Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
            ), player = Players.You)),
        ),
        activated(
            mana("{2}"), CostPart.TapSelf,
            steps = listOf(
                draw(1),
                Step.Do(Effect.Discard(Players.You, count = Value.Fixed(1), filter = ObjectFilter.Any)),
            ),
        ),
        // Two separate checks: with no card exiled, neither token is made
        activated(
            CostPart.TapSelf,
            steps = listOf(
                Step.Choose(
                    choice = Choice.Objects(
                        filter = ObjectFilter.Category(ObjectCategory.Card),
                        min = Value.Fixed(1),
                        max = Value.Fixed(1),
                        from = Objects.Remembered("exiled"),
                    ),
                    bind = "chosen",
                    chooser = Players.You,
                ),
                Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Graveyard), postBind = "moved")),
                Step.If(
                    condition = Condition.ObjectMatches(Objects.Bound("moved"), ObjectFilter.Type(CardType.Land)),
                    then = listOf(create(TokenSpec.Predefined("Treasure"))),
                ),
                Step.If(
                    condition = Condition.ObjectMatches(Objects.Bound("moved"), notLand),
                    then = listOf(create(creatureToken("Rogue", 2, 2, Color.Black))),
                ),
            ),
        ),
    )
}
