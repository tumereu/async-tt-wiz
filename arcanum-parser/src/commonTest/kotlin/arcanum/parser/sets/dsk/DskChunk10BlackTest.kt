package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
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
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.creatureYouControl
import arcanum.parser.sets.dsk.Dsk.enchantment
import arcanum.parser.sets.dsk.Dsk.youControl
import kotlin.test.Test

/**
 * Duskmourn chunk 10: the other black cards. Additional costs with alternatives ("sacrifice a creature or enchantment");
 * returning a destroyed card; looking and splitting into hand and graveyard; "when enchanted creature is dealt damage";
 * each player exiling most of their library face down; "half their life, rounded up" (CR 107.1a); paying life to return
 * a dying creature with a finality counter (CR 122.1h); discarding any number and a reflexive trigger counting them (CR
 * 603.12); targeting despite hexproof and ward that doesn't trigger; exile replacements linked to a permission to play the
 * exiled cards for life (CR 607.2a, 118.9).
 */
class DskChunk10BlackTest {
    private fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))
    private val drawACard = Step.Do(Effect.Draw(Players.You, Value.Fixed(1)))
    private val dies = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard))
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private val creatureOrEnchantment = ObjectFilter.Or(listOf(creature, enchantment))
    private val sacrificeACreatureOrEnchantment = Ability.Static(
        mechanics = listOf(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(creatureOrEnchantment, count = Value.Fixed(1))))))),
        affects = Affected.Objects(Objects.Self),
    )
    private val finality = listOf(CountersToPut(CounterKind.Finality, Value.Fixed(1)))
    private val half = { players: Players -> Value.Half(Value.LifeOf(players), roundUp = true) }
    /** "When enchanted creature is dealt damage, destroy it." */
    private val destroyWhenDealtDamage = Ability.Triggered(
        trigger = Trigger.On(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(objects = ObjectFilter.Is(enchanted)))),
        instructions = Instructions(steps = listOf(Step.Do(Effect.Destroy(Objects.EventObject(EventRole.Object))))),
    )

    @Test
    fun gigglingSlasher() = card(
        name = "Giggling Slasher",
        cost = "{3}{B}",
        type = "Creature — Human Assassin",
        pt = "3/3",
        text = """
            Deathtouch
            This creature enters with a +1/+1 counter on it if a creature died this turn.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(CounterKind.PowerToughness(1, 1), Value.Fixed(1))))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(creature, from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events),
        ),
    )

    /** "If a creature card is put into a graveyard this way": the destroyed card, now a card in a graveyard (CR 400.7). */
    @Test
    fun returnGoneWrong() = card(
        name = "Return Gone Wrong",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Destroy target creature. If a creature card is put into a graveyard this way, return it to the battlefield under your control. Sacrifice it at the beginning of your next end step.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.Destroy(Objects.Target("creature"), postBind = "destroyed")),
            Step.If(
                Condition.ObjectMatches(Objects.Bound("destroyed"), ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard)))),
                then = listOf(
                    Step.Do(Effect.Move(Objects.Bound("destroyed"), Destination(Zone.Battlefield, controller = Players.You), postBind = "returned")),
                    Step.Do(Effect.CreateDelayedTrigger(Trigger.On(EventPattern.StepBegins(TurnStep.End, PlayerFilter.You)),
                        Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound("returned"))))))),
                ),
            ),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun consultWithEvil() = card(
        name = "Consult with Evil",
        cost = "{2}{B}",
        type = "Sorcery",
        text = "Look at the top four cards of your library. Put one of them into your hand and the rest into your graveyard. You gain 3 life.",
    ) parsesTo abilities(
        spell(
            Step.Do(Effect.LookAt(Objects.LibraryCards(Players.You, Value.Fixed(4), fromTop = true), players = Players.You, bind = "seen")),
            Step.Choose(Choice.Objects(ObjectFilter.Any, Value.Fixed(1), Value.Fixed(1), from = Objects.Bound("seen")), bind = "kept", chooser = Players.You),
            Step.Do(Effect.Move(Objects.Bound("kept"), Destination(Zone.Hand))),
            Step.Do(Effect.Move(Objects.Except(Objects.Bound("seen"), Objects.Bound("kept")), Destination(Zone.Graveyard))),
            Step.Do(Effect.GainLife(Players.You, Value.Fixed(3))),
        ),
    )

    /** "You may choose a nonland card from it. That player discards that card." (D47: a choice, then a discard of it). */
    @Test
    fun crackedCranium() = card(
        name = "Cracked Cranium",
        cost = "{2}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, look at target player's hand. You may choose a nonland card from it. That player discards that card.
            When enchanted creature is dealt damage, destroy it.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Triggered(thisEnters, Instructions(
            targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))),
            steps = listOf(
                Step.Do(Effect.LookAt(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player")))))),
                    players = Players.You)),
                Step.Choose(Choice.Objects(ObjectFilter.Not(ObjectFilter.Type(CardType.Land)), Value.Fixed(0), Value.Fixed(1),
                    from = Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("player"))))))),
                    bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Discard(Players.Target("player"), Value.Fixed(1), filter = ObjectFilter.Any, objects = Objects.Bound("chosen"))),
            ),
        )),
        destroyWhenDealtDamage,
    )

    /** "All but the bottom six cards of their library", every player's in one event (CR 101.4). */
    @Test
    fun doomsdayEviscerator() = card(
        name = "Doomsday Eviscerator",
        cost = "{B}{B}{B}{B}{B}{B}",
        type = "Creature — Demon",
        pt = "6/6",
        text = """
            Flying
            When this creature enters, if it was cast, each player exiles all but the bottom six cards of their library face down.
            At the beginning of your upkeep, draw a card.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(
            trigger = thisEnters,
            instructions = Instructions(steps = listOf(Step.Do(Effect.Move(
                Objects.Except(
                    Objects.Each(ObjectFilter.InZone(Zone.Library)),
                    Objects.LibraryCards(Players.Each(PlayerFilter.Any), Value.Fixed(6), fromTop = false),
                ),
                Destination(Zone.Exile, faceDown = true),
            )))),
            interveningIf = Condition.CastFrom(null, Objects.Self),
        ),
        Ability.Triggered(Trigger.On(EventPattern.StepBegins(TurnStep.Upkeep, PlayerFilter.You)), Instructions(steps = listOf(drawACard))),
    )

    /** "If you discarded a card this way": your card among those discarded. */
    @Test
    fun zealotOfTheHarrowing() = card(
        name = "Zealot of the Harrowing",
        cost = "{3}{B}",
        type = "Creature — Human Cleric",
        pt = "2/2",
        text = "When this creature enters, each player discards a card. If you discarded a card this way, draw a card.",
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(
            Step.Do(Effect.Discard(Players.Each(PlayerFilter.Any), Value.Fixed(1), filter = ObjectFilter.Any, postBind = "discarded")),
            Step.If(Condition.ObjectMatches(Objects.Where(Objects.Bound("discarded"), ObjectFilter.OwnedBy(PlayerFilter.You)), ObjectFilter.Any), then = listOf(drawACard)),
        ))),
    )

    @Test
    fun dreadOfTheDark() = card(
        name = "Dread of the Dark",
        cost = "{4}{B}",
        type = "Enchantment Creature — Nightmare",
        pt = "5/5",
        text = "Whenever this creature attacks, if defending player controls no Glimmer creatures, it gains menace and deathtouch until end of turn. (A creature with menace can't be blocked except by two or more creatures.)",
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Menace, Keyword.Deathtouch))),
                Affected.Objects(Objects.Self), Duration.EndOfTurn)))),
            interveningIf = Condition.Not(Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Glimmer")), creature,
                ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))))), atLeast = 1)),
        ),
    )

    @Test
    fun lastVengeance() = card(
        name = "Last Vengeance",
        cost = "{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature or enchantment.
            Exile target creature.
        """,
    ) parsesTo abilities(
        sacrificeACreatureOrEnchantment,
        spell(Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Exile))), targets = listOf(slot("creature", creature))),
    )

    /** An Aura on a player (CR 303.4m); "half their life, rounded up" (CR 107.1a). */
    @Test
    fun grievousGash() = card(
        name = "Grievous Gash",
        cost = "{3}{B}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant player
            Enchanted player can't gain life.
            Whenever enchanted player is dealt damage, they lose half their life, rounded up.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Players(PlayerFilter.Any)),
        Ability.Static(listOf(Mechanic.CantGainLife), affects = Affected.Players(Players.AttachedTo(Objects.Self))),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Damage(ObjectFilter.Any, RecipientFilter(players = PlayerFilter.Is(Players.AttachedTo(Objects.Self))))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.LoseLife(Players.EventPlayer, half(Players.EventPlayer))))),
        ),
    )

    /** "Each player sacrifices X creatures of their choice" (CR 101.4); paying life to return the card (CR 118.12); "if they
     * don't" (D52). */
    @Test
    fun meathookMayhemII() = card(
        name = "Meathook Mayhem II",
        cost = "{X}{X}{B}{B}{B}{B}",
        type = "Legendary Enchantment",
        text = """
            When Meathook Mayhem II enters, each player sacrifices X creatures of their choice.
            Whenever a creature you control dies, you may pay 3 life. If you do, return that card under your control with a finality counter on it.
            Whenever a creature an opponent controls dies, they may pay 3 life. If they don't, return that card under your control with a finality counter on it.
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(Step.ForEach(Each.Players(Players.Each(PlayerFilter.Any)), bind = "player", together = true, steps = listOf(
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("player"))))),
                min = Value.CastX(Objects.Self), max = Value.CastX(Objects.Self)), bind = "sacrificed", chooser = Players.Bound("player")),
            Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
        ))))),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(creatureYouControl, from = Zone.Battlefield, to = Zone.Graveyard)),
            instructions = Instructions(steps = listOf(
                Step.Pay(Cost(listOf(CostPart.PayLife(Value.Fixed(3)))), payer = Players.You, bind = "paid"),
                Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Move(Objects.EventObject(EventRole.After),
                    Destination(Zone.Battlefield, controller = Players.You, counters = finality))))),
            )),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent))), from = Zone.Battlefield, to = Zone.Graveyard)),
            instructions = Instructions(steps = listOf(
                Step.Pay(Cost(listOf(CostPart.PayLife(Value.Fixed(3)))), payer = Players.ControllerOf(Objects.EventObject(EventRole.Before)), bind = "paid"),
                Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Move(Objects.EventObject(EventRole.After),
                    Destination(Zone.Battlefield, controller = Players.You, counters = finality))))),
            )),
        ),
    )

    /** "You may discard any number of cards. When you do, up to that many target creatures …": a reflexive trigger whose
     * target count is the number discarded (CR 603.12). */
    @Test
    fun miasmaFiend() = card(
        name = "Miasma Fiend",
        cost = "{4}{B}{B}",
        type = "Creature — Demon",
        pt = "5/4",
        text = """
            Flying
            When this creature enters, you may discard any number of cards. When you do, up to that many target creatures each get -2/-2 until end of turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(thisEnters, Instructions(steps = listOf(
            Step.Pay(Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card), count = null), preBind = "discarded"))),
                payer = Players.You, bind = "paid"),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.ReflexiveTrigger(Instructions(
                targets = listOf(TargetSlot("creatures", TargetKind.Objects(creature), min = Value.Fixed(0), max = Value.Count(Objects.Bound("discarded")))),
                steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(-2), Value.Fixed(-2))),
                    Affected.Objects(Objects.Target("creatures")), Duration.EndOfTurn))),
            ))))),
        ))),
    )

    /** Targeting despite hexproof (CR 702.11b) and ward not triggering (CR 702.21a). */
    @Test
    fun nowhereToHide() = card(
        name = "Nowhere to Hide",
        cost = "{1}{B}",
        type = "Enchantment",
        text = """
            Flash
            When this enchantment enters, target creature an opponent controls gets -3/-3 until end of turn.
            Creatures your opponents control can be the targets of spells and abilities as though they didn't have hexproof. Ward abilities of those creatures don't trigger.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(thisEnters, Instructions(
            targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent))))),
            steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(-3), Value.Fixed(-3))),
                Affected.Objects(Objects.Target("creature")), Duration.EndOfTurn))),
        )),
        Ability.Static(
            mechanics = listOf(Mechanic.TargetableDespiteHexproof, Mechanic.WardDoesntTrigger),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent))))),
        ),
    )

    @Test
    fun vainEgotist() = card(
        name = "Vain Egotist",
        cost = "{2}{B}",
        type = "Creature — Human Rogue",
        pt = "3/2",
        text = """
            {1}{B}, Sacrifice another creature or enchantment: This creature gains indestructible until end of turn. Tap it. (Damage and effects that say "destroy" don't destroy it.)
            Whenever you sacrifice a permanent, target opponent loses 1 life and you gain 1 life.
        """,
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{1}{B}")!!), CostPart.Sacrifice(CostObjects.Choose(
                ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creatureOrEnchantment)), count = Value.Fixed(1))))),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Indestructible))), Affected.Objects(Objects.Self), Duration.EndOfTurn)),
                Step.Do(Effect.Tap(Objects.Self)),
            )),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Sacrifice(youControl)),
            instructions = Instructions(
                targets = listOf(TargetSlot("opponent", TargetKind.Players(PlayerFilter.Opponent), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(
                    Step.Do(Effect.LoseLife(Players.Target("opponent"), Value.Fixed(1))),
                    Step.Do(Effect.GainLife(Players.You, Value.Fixed(1))),
                ),
            ),
        ),
    )

    /** A sacrifice chosen by the target player, other than the enchanted creature. */
    @Test
    fun sporicInfection() = card(
        name = "Sporic Infection",
        cost = "{1}{B}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            When this Aura enters, target player sacrifices a creature of their choice other than enchanted creature.
            When enchanted creature is dealt damage, destroy it.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Triggered(thisEnters, Instructions(
            targets = listOf(TargetSlot("player", TargetKind.Players(PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))),
            steps = listOf(
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(creature, ObjectFilter.Not(ObjectFilter.Is(enchanted)),
                    ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Target("player"))))), Value.Fixed(1), Value.Fixed(1)),
                    bind = "sacrificed", chooser = Players.Target("player")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            ),
        )),
        destroyWhenDealtDamage,
    )

    /** "If it had no counters on it" (the creature as it last existed, CR 603.10a); back tapped with stun counters. */
    @Test
    fun unkillableSlasher() = card(
        name = "Unkillable Slasher",
        cost = "{2}{B}",
        type = "Creature — Zombie Assassin",
        pt = "2/3",
        text = """
            Deathtouch
            Whenever this creature deals combat damage to a player, they lose half their life, rounded up.
            When this creature dies, if it had no counters on it, return it to the battlefield tapped under its owner's control with two stun counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Deathtouch,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.LoseLife(Players.EventPlayer, half(Players.EventPlayer))))),
        ),
        Ability.Triggered(
            trigger = dies,
            instructions = Instructions(steps = listOf(Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield, tapped = true,
                controller = Players.OwnerOf(Objects.EventObject(EventRole.After)), counters = listOf(CountersToPut(CounterKind.Stun, Value.Fixed(2)))))))),
            interveningIf = Condition.Not(Condition.ObjectMatches(Objects.EventObject(EventRole.Before), ObjectFilter.HasCounter(null, atLeast = 1))),
        ),
    )

    /** An exile replacement that keeps the cards with this creature (CR 607.2a), and a permission to play them, casting spells
     * for life equal to their mana value rather than their mana cost (CR 118.9). */
    @Test
    fun valgavothDreadDevourer() = card(
        name = "Valgavoth, Dread Devourer",
        cost = "{6}{B}{B}{B}",
        type = "Legendary Creature — Elder Demon",
        pt = "9/9",
        text = """
            Flying, lifelink
            Ward—Sacrifice three nonland permanents.
            If a card you didn't control would be put into an opponent's graveyard from anywhere, exile it instead.
            During your turn, you may play cards exiled with Valgavoth. If you cast a spell this way, pay life equal to its mana value rather than pay its mana cost.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Keyword.Lifelink,
        Keyword.Ward(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Land)),
            ObjectFilter.Category(ObjectCategory.Permanent))), count = Value.Fixed(3)))))),
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.Not(ObjectFilter.ControlledBy(PlayerFilter.You)),
                    ObjectFilter.OwnedBy(PlayerFilter.Opponent))), to = Zone.Graveyard),
                instead = Instructions(steps = listOf(
                    Step.Do(Effect.Move(Objects.EventObject(EventRole.Before), Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
                )),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.MayPlay(Zone.Exile, PlayKind.Play, holder = PlayerFilter.You,
                cost = Cost(listOf(CostPart.PayLife(Value.Of(NumericProperty.ManaValue, Objects.Affected, arcanum.api.Combine.Single)))))),
            affects = Affected.Objects(Objects.Remembered("exiled")),
            condition = Condition.YourTurn,
        ),
    )

    /** Each opponent sacrifices an enchantment, then a creature (CR 101.4: one sacrifice event each time). */
    @Test
    fun vileMangler() = card(
        name = "Vile Mangler",
        cost = "{5}{B}{B}",
        type = "Creature — Demon",
        pt = "6/5",
        text = """
            As an additional cost to cast this spell, sacrifice a creature or enchantment.
            Flying, trample
            When this creature enters, each opponent sacrifices a nontoken enchantment of their choice, then sacrifices a nontoken creature of their choice.
        """,
    ) parsesTo abilities(
        sacrificeACreatureOrEnchantment,
        Keyword.Flying,
        Keyword.Trample,
        Ability.Triggered(thisEnters, Instructions(steps = listOf(
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)), enchantment,
                    ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent"))))), Value.Fixed(1), Value.Fixed(1)), bind = "sacrificed", chooser = Players.Bound("opponent")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            )),
            Step.ForEach(Each.Players(Players.Each(PlayerFilter.Opponent)), bind = "opponent", together = true, steps = listOf(
                Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)), creature,
                    ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent"))))), Value.Fixed(1), Value.Fixed(1)), bind = "sacrificed", chooser = Players.Bound("opponent")),
                Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
            )),
        ))),
    )

    /** Ward with a cost that isn't mana (CR 702.21a); basic landcycling by type (CR 702.29e). */
    @Test
    fun spectralPilferer() = card(
        name = "Spectral Pilferer",
        cost = "{4}{B}{B}",
        type = "Creature — Spirit",
        pt = "6/5",
        text = """
            Ward—Discard a card. (Whenever this creature becomes the target of a spell or ability an opponent controls, counter it unless that player discards a card.)
            Swampcycling {2} ({2}, Discard this card: Search your library for a Swamp card, reveal it, put it into your hand, then shuffle.)
        """,
    ) parsesTo abilities(
        Keyword.Ward(Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card), count = Value.Fixed(1)))))),
        Keyword.Typecycling(ObjectFilter.Sub(Subtype("Swamp")), "Swampcycling", manaCost("{2}")),
    )
}
