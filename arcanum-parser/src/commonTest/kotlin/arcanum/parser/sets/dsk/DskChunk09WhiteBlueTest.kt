package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordKind
import arcanum.api.ManaCost
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.RecipientFilter
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.cube.thisEnters
import arcanum.parser.sets.dsk.Dsk.create
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.creatureYouControl
import arcanum.parser.sets.dsk.Dsk.enchantment
import arcanum.parser.sets.dsk.Dsk.token
import arcanum.parser.sets.dsk.Dsk.youControl
import kotlin.test.Test

/**
 * Duskmourn chunk 09: the other white and blue cards. Linked exile from an additional cost and an enters trigger (CR
 * 607.2a, q); stun counters on an opponent's creature; becoming another type and colour; tapping any number of creatures
 * and counting them; a cost reduction that looks at the spell's target (CR 601.2f); a token that can't attack or block
 * alone (CR 506.5); control of the enchanted creature (layer 2); "unless its controller pays … If they do"; modes chosen
 * once; putting a permanent on top or bottom as its owner chooses; prevention replaced by milling.
 */
class DskChunk09WhiteBlueTest {
    private fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))
    private fun spell(vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Ability.Spell(Instructions(targets = targets, steps = steps.toList()))
    private fun untilEndOfTurn(objects: Objects, vararg mechanics: Mechanic) =
        Step.Do(Effect.ApplyMechanics(mechanics.toList(), Affected.Objects(objects), Duration.EndOfTurn))
    private val attacks = Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self)))
    private val enchanted = Objects.AttachedTo(Objects.Self)
    private val another = ObjectFilter.Not(ObjectFilter.Is(Objects.Self))
    private val drawACard = Step.Do(Effect.Draw(Players.You, Value.Fixed(1)))
    private val combatDamageToAPlayer = Trigger.On(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any), combat = true))

    @Test
    fun spectralPlating() = card(
        name = "Spectral Plating",
        cost = "{W}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            Enchanted creature gets +1/+1 for each enchantment you control and has first strike.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Static(
            mechanics = listOf(
                Mechanic.ModifyPowerToughness(
                    Value.Count(Objects.Each(ObjectFilter.And(listOf(enchantment, youControl)))),
                    Value.Count(Objects.Each(ObjectFilter.And(listOf(enchantment, youControl)))),
                ),
                Mechanic.GainAbilities(listOf(Keyword.FirstStrike)),
            ),
            affects = Affected.Objects(enchanted),
        ),
    )

    /** The card exiled to pay the additional cost and the one the enters trigger exiles are both "exiled with it" (CR
     * 607.2a, q). */
    @Test
    fun dreadOfAbduction() = card(
        name = "Dread of Abduction",
        cost = "{4}{W}{W}",
        type = "Enchantment Creature — Nightmare",
        pt = "5/5",
        text = """
            As an additional cost to cast this spell, exile a creature you control.
            Flying
            When this creature enters, exile target creature an opponent controls.
            When this creature leaves the battlefield, put each card exiled with it into its owner's hand.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.AdditionalCost(cost = Cost(listOf(CostPart.Exile(
                CostObjects.Choose(creatureYouControl, count = Value.Fixed(1)), from = Zone.Battlefield, remember = "exiled"))))),
            affects = Affected.Objects(Objects.Self),
        ),
        Keyword.Flying,
        Ability.Triggered(thisEnters, Instructions(
            targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent))))),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Exile), postBind = "exiled")),
                Step.Do(Effect.Remember("exiled", Objects.Bound("exiled"))),
            ),
        )),
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.Move(Objects.Remembered("exiled"), Destination(Zone.Hand))))),
        ),
    )

    @Test
    fun dreadOfImmobility() = card(
        name = "Dread of Immobility",
        cost = "{4}{W}",
        type = "Enchantment Creature — Nightmare",
        pt = "4/4",
        text = "When this creature enters, tap up to one target creature. If an opponent controls that creature, put a stun counter on it. (If a permanent with a stun counter would become untapped, remove one from it instead.)",
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(
            targets = listOf(slot("creature", creature, min = 0)),
            steps = listOf(
                Step.Do(Effect.Tap(Objects.Target("creature"))),
                Step.If(Condition.ObjectMatches(Objects.Target("creature"), ObjectFilter.ControlledBy(PlayerFilter.Opponent)),
                    then = listOf(Step.Do(Effect.PutCounters(Objects.Target("creature"), CounterKind.Stun, Value.Fixed(1))))),
            ),
        )),
    )

    @Test
    fun steeledEscort() = card(
        name = "Steeled Escort",
        cost = "{2}{W}",
        type = "Creature — Human Soldier",
        pt = "2/4",
        text = "Whenever this creature attacks, another target creature you control gets +1/+0 and gains indestructible until end of turn. (Damage and effects that say \"destroy\" don't destroy it.)",
    ) parsesTo abilities(
        Ability.Triggered(attacks, Instructions(
            targets = listOf(slot("creature", ObjectFilter.And(listOf(another, creature, youControl)))),
            steps = listOf(untilEndOfTurn(Objects.Target("creature"), Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(0)),
                Mechanic.GainAbilities(listOf(Keyword.Indestructible)))),
        )),
    )

    /** One effect, several layers (4, 6, 7c; CR 613.1). */
    @Test
    fun startleFright() = card(
        name = "Startle Fright",
        cost = "{W}",
        type = "Instant",
        text = "Until end of turn, target creature gets +2/+2, gains flying, and becomes a Horror enchantment creature in addition to its other types.",
    ) parsesTo abilities(
        spell(
            untilEndOfTurn(Objects.Target("creature"),
                Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2)),
                Mechanic.GainAbilities(listOf(Keyword.Flying)),
                Mechanic.SetTypes(add = listOf(CardType.Enchantment, CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Horror")))),
            ),
            targets = listOf(slot("creature", creature)),
        ),
    )

    /** "Tap any number of untapped creatures you control": chosen as it resolves; "for each creature tapped this way". */
    @Test
    fun waifsOfTheWheat() = card(
        name = "Waifs of the Wheat",
        cost = "{1}{W}",
        type = "Creature — Human",
        pt = "2/1",
        text = "Whenever this creature attacks, tap any number of untapped creatures you control. This creature gets +1/+1 until end of turn for each creature tapped this way.",
    ) parsesTo abilities(
        Ability.Triggered(attacks, Instructions(steps = listOf(
            Step.Choose(Choice.Objects(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.HasStatus(Status.Tapped)), creature, youControl)),
                min = Value.Fixed(0), max = null), bind = "tapped", chooser = Players.You),
            Step.Do(Effect.Tap(Objects.Bound("tapped"))),
            untilEndOfTurn(Objects.Self, Mechanic.ModifyPowerToughness(Value.Count(Objects.Bound("tapped")), Value.Count(Objects.Bound("tapped")))),
        ))),
    )

    /** "If you cast it from your hand" (CR 601.2a). */
    @Test
    fun stitchedPlaything() = card(
        name = "Stitched Plaything",
        cost = "{2}{W}",
        type = "Artifact Creature — Toy",
        pt = "4/3",
        text = """
            Double strike
            This creature enters with two -1/-1 counters on it if you cast it from your hand.
        """,
    ) parsesTo abilities(
        Keyword.DoubleStrike,
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(CounterKind.PowerToughness(-1, -1), Value.Fixed(2))))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.CastFrom(Zone.Hand, Objects.Self),
        ),
    )

    /** "Becomes a black Demon in addition to its other colors and types" for good (layers 4, 5); "Activate only once". */
    @Test
    fun hauntedGoat() = card(
        name = "Haunted Goat",
        cost = "{W}",
        type = "Creature — Goat",
        pt = "1/1",
        text = "{3}, Discard a card: Put three +1/+1 counters on this creature and it becomes a black Demon in addition to its other colors and types. Activate only once.",
    ) parsesTo abilities(
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{3}")!!), CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card), count = Value.Fixed(1))))),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(3))),
                Step.Do(Effect.ApplyMechanics(
                    listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Literal(Subtype("Demon")))), Mechanic.SetColors(listOf(ColorRef.Literal(Color.Black)), add = true)),
                    Affected.Objects(Objects.Self), Duration.Indefinitely)),
            )),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You, once = true),
        ),
    )

    /** A cost reduction that reads the spell's own target as it is cast (CR 601.2f, D47). */
    @Test
    fun stolenFromSlumber() = card(
        name = "Stolen from Slumber",
        cost = "{4}{W}",
        type = "Instant",
        text = """
            This spell costs {3} less to cast if it targets a tapped creature.
            Destroy target creature.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.CostChange(ObjectFilter.Is(Objects.Self), CostChange.Less(ManaCost.parse("{3}")!!, Value.Fixed(1)))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.ObjectMatches(Objects.Target("creature"), ObjectFilter.HasStatus(Status.Tapped)),
        ),
        spell(Step.Do(Effect.Destroy(Objects.Target("creature"))), targets = listOf(slot("creature", creature))),
    )

    @Test
    fun seamsplitDoll() = card(
        name = "Seamsplit Doll",
        cost = "{1}{W}",
        type = "Artifact Creature — Toy",
        pt = "2/1",
        text = "When this creature enters, draw a card. Then discard a card unless you control another creature with power 2 or less.",
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(
            drawACard,
            Step.If(
                Condition.Not(Condition.Exists(ObjectFilter.And(listOf(another, creature, ObjectFilter.Power(Comparison.AtMost, Value.Fixed(2)), youControl)), atLeast = 1)),
                then = listOf(Step.Do(Effect.Discard(Players.You, Value.Fixed(1), filter = ObjectFilter.Any))),
            ),
        ))),
    )

    /** A token with a quoted ability (CR 111.4): can't attack or block alone (CR 506.5). */
    @Test
    fun tobinBeastieTamer() = card(
        name = "Tobin, Beastie Tamer",
        cost = "{2}{W}",
        type = "Legendary Creature — Human Wizard",
        pt = "1/1",
        text = """
            When Tobin enters, create a 4/4 white Beast creature token with "This token can't attack or block alone."
            As long as you control four or more creature tokens, creature tokens you control have flying.
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(create(token(listOf("Beast"), listOf(Color.White), 4, 4, abilities = listOf(
            Ability.Static(listOf(Mechanic.CantAttackAlone, Mechanic.CantBlockAlone), affects = Affected.Objects(Objects.Self)),
        )))))),
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Flying))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.Category(ObjectCategory.Token), youControl)))),
            condition = Condition.Exists(ObjectFilter.And(listOf(creature, ObjectFilter.Category(ObjectCategory.Token), youControl)), atLeast = 4),
        ),
    )

    @Test
    fun clammyLurker() = card(
        name = "Clammy Lurker",
        cost = "{3}{U}",
        type = "Enchantment Creature — Horror",
        pt = "2/5",
        text = "Whenever this creature attacks, another target attacking creature can't be blocked this turn.",
    ) parsesTo abilities(
        Ability.Triggered(attacks, Instructions(
            targets = listOf(slot("creature", ObjectFilter.And(listOf(another, ObjectFilter.HasStatus(Status.Attacking), creature)))),
            steps = listOf(untilEndOfTurn(Objects.Target("creature"), Mechanic.CantBeBlocked)),
        )),
    )

    /** "Unless its controller pays {2}" (CR 118.12a), then "If they do": the payment named once, read twice. */
    @Test
    fun notAPeep() = card(
        name = "Not a Peep",
        cost = "{1}{U}",
        type = "Instant",
        text = "Counter target spell unless its controller pays {2}. If they do, surveil 2. (Look at the top two cards of your library, then put any number of them into your graveyard and the rest on top of your library in any order.)",
    ) parsesTo abilities(
        spell(
            Step.Pay(manaCost("{2}"), payer = Players.ControllerOf(Objects.Target("spell")), bind = "paid"),
            Step.If(Condition.Not(Condition.Did("paid")), then = listOf(Step.Do(Effect.Counter(Objects.Target("spell"))))),
            Step.If(Condition.Did("paid"), then = listOf(Step.Do(Effect.Surveil(Players.You, Value.Fixed(2))))),
            targets = listOf(slot("spell", ObjectFilter.Category(ObjectCategory.Spell))),
        ),
    )

    /** Control of the enchanted creature (layer 2, CR 613.1b); losing all abilities (layer 6). */
    @Test
    fun duskmournsDominion() = card(
        name = "Duskmourn's Dominion",
        cost = "{4}{U}{U}",
        type = "Enchantment — Aura",
        text = """
            Enchant creature
            You control enchanted creature.
            Enchanted creature gets -3/-0 and loses all abilities.
        """,
    ) parsesTo abilities(
        Keyword.Enchant(TargetKind.Objects(creature)),
        Ability.Static(listOf(Mechanic.ControlledBy(Players.You)), affects = Affected.Objects(enchanted)),
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(-3), Value.Fixed(0)), Mechanic.LoseAbilities()), affects = Affected.Objects(enchanted)),
    )

    @Test
    fun stepIntoTheEnigma() = card(
        name = "Step into the Enigma",
        cost = "{U}",
        type = "Sorcery",
        text = """
            Target creature can't be blocked this turn.
            Draw a card.
        """,
    ) parsesTo abilities(
        spell(untilEndOfTurn(Objects.Target("creature"), Mechanic.CantBeBlocked), drawACard, targets = listOf(slot("creature", creature))),
    )

    @Test
    fun dreadOfFailedExams() = card(
        name = "Dread of Failed Exams",
        cost = "{4}{U}",
        type = "Enchantment Creature — Nightmare",
        pt = "2/7",
        text = "Whenever this creature deals combat damage to a player, draw that many cards.",
    ) parsesTo abilities(
        Ability.Triggered(combatDamageToAPlayer, Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You, Value.EventAmount))))),
    )

    /** "Loses flying until your next turn"; "defending player" of this attacker (CR 506.2). */
    @Test
    fun dreadOfFalling() = card(
        name = "Dread of Falling",
        cost = "{3}{U}{U}",
        type = "Enchantment Creature — Nightmare",
        pt = "4/4",
        text = """
            Flying
            Whenever this creature attacks, target creature defending player controls gets -2/-0 and loses flying until your next turn.
        """,
    ) parsesTo abilities(
        Keyword.Flying,
        Ability.Triggered(attacks, Instructions(
            targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.DefendingPlayerOf(Objects.Self))))))),
            steps = listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(Value.Fixed(-2), Value.Fixed(0)), Mechanic.LoseAbilities(Keyword.Flying)),
                Affected.Objects(Objects.Target("creature")), Duration.UntilYourNextTurn))),
        )),
    )

    /** Shuffling two permanents into their owners' libraries. */
    @Test
    fun floodpitsStrangler() = card(
        name = "Floodpits Strangler",
        cost = "{1}{U}",
        type = "Creature — Merfolk",
        pt = "2/1",
        text = """
            Flash
            Vigilance
            When this creature enters, tap target creature an opponent controls and put a stun counter on it.
            {1}{U}, {T}: Shuffle this creature and target creature with a stun counter on it into their owners' libraries.
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Keyword.Vigilance,
        Ability.Triggered(thisEnters, Instructions(
            targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.ControlledBy(PlayerFilter.Opponent))))),
            steps = listOf(
                Step.Do(Effect.Tap(Objects.Target("creature"))),
                Step.Do(Effect.PutCounters(Objects.Target("creature"), CounterKind.Stun, Value.Fixed(1))),
            ),
        )),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(ManaCost.parse("{1}{U}")!!), CostPart.TapSelf)),
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(creature, ObjectFilter.HasCounter(CounterKind.Stun, atLeast = 1))))),
                steps = listOf(
                    Step.Do(Effect.Move(Objects.Union(listOf(Objects.Self, Objects.Target("creature"))), Destination(Zone.Library))),
                    Step.Do(Effect.Shuffle(Players.OwnerOf(Objects.Union(listOf(Objects.Self, Objects.Target("creature")))))),
                ),
            ),
            rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
        ),
    )

    @Test
    fun getAway() = card(
        name = "Get Away",
        cost = "{U}{U}",
        type = "Instant",
        text = """
            Choose one —
            • Counter target creature or enchantment spell.
            • Return one or two target creatures and/or enchantments you own to your hand.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(modes = Modes(
            options = listOf(
                Mode(Instructions(
                    targets = listOf(slot("spell", ObjectFilter.And(listOf(ObjectFilter.Or(listOf(creature, enchantment)), ObjectFilter.Category(ObjectCategory.Spell))))),
                    steps = listOf(Step.Do(Effect.Counter(Objects.Target("spell")))),
                )),
                Mode(Instructions(
                    targets = listOf(slot("permanent", ObjectFilter.And(listOf(ObjectFilter.Or(listOf(creature, enchantment)), ObjectFilter.OwnedBy(PlayerFilter.You))), max = 2)),
                    steps = listOf(Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Hand)))),
                )),
            ),
            min = 1,
            max = 1,
            chooser = Players.You,
        ))),
    )

    /** "If you cast it" (CR 603.4); not losing for 0 life (CR 704.5a); "that many" life gained or lost. */
    @Test
    fun mirelaVendanesGrimoire() = card(
        name = "Mirela Vendane's Grimoire",
        cost = "{5}{U}",
        type = "Legendary Artifact — Book",
        text = """
            When Mirela Vendane's Grimoire enters, if you cast it, draw five cards.
            You have no maximum hand size and don't lose the game for having 0 or less life.
            Whenever you gain life, draw that many cards.
            Whenever you lose life, discard that many cards. Then if you have no cards in hand, you lose the game.
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(5))))),
            interveningIf = Condition.CastFrom(null, Objects.Self)),
        Ability.Static(listOf(Mechanic.NoMaximumHandSize, Mechanic.NoLossForZeroLife), affects = Affected.Players(Players.You)),
        Ability.Triggered(Trigger.On(EventPattern.GainLife(PlayerFilter.You)), Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You, Value.EventAmount))))),
        Ability.Triggered(Trigger.On(EventPattern.LoseLife(PlayerFilter.You)), Instructions(steps = listOf(
            Step.Do(Effect.Discard(Players.You, Value.EventAmount, filter = ObjectFilter.Any)),
            Step.If(
                Condition.Not(Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You))), atLeast = 1)),
                then = listOf(Step.Do(Effect.LoseGame(Players.You))),
            ),
        ))),
    )

    /** "Prevent that damage and each opponent mills that many cards": the damage is replaced by the milling (CR 614.1a). */
    @Test
    fun theMindflayer() = card(
        name = "The Mindflayer",
        cost = "{U}{U}{U}",
        type = "Legendary Enchantment Creature — Nightmare",
        pt = "10/1",
        text = """
            The Mindflayer can't be blocked.
            If a source you control would deal damage to an opponent, prevent that damage and each opponent mills that many cards.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeBlocked), affects = Affected.Objects(Objects.Self)),
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(
                EventPattern.Damage(youControl, RecipientFilter(players = PlayerFilter.Opponent)),
                instead = Instructions(steps = listOf(Step.Do(Effect.Mill(Players.Each(PlayerFilter.Opponent), Value.EventAmount)))),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
    )

    /** "Choose one that hasn't been chosen" (for this object, CR 700.2); becoming a copy (layer 1, CR 707). */
    @Test
    fun hushedHallcreeper() = card(
        name = "Hushed Hallcreeper",
        cost = "{1}{U}",
        type = "Enchantment Creature — Horror",
        pt = "1/1",
        text = """
            This creature can't be blocked.
            Whenever this creature deals combat damage to a player, choose one that hasn't been chosen —
            • Put two +1/+1 counters on this creature.
            • Draw a card.
            • This creature becomes a copy of another target creature you control.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeBlocked), affects = Affected.Objects(Objects.Self)),
        Ability.Triggered(combatDamageToAPlayer, Instructions(modes = Modes(
            options = listOf(
                Mode(Instructions(steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(2)))))),
                Mode(Instructions(steps = listOf(drawACard))),
                Mode(Instructions(
                    targets = listOf(slot("creature", ObjectFilter.And(listOf(another, creature, youControl)))),
                    steps = listOf(Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Target("creature"))), Affected.Objects(Objects.Self), Duration.Indefinitely))),
                )),
            ),
            min = 1,
            max = 1,
            onlyUnchosen = true,
            chooser = Players.You,
        ))),
    )

    /** The owner chooses the top or the bottom of their library. */
    @Test
    fun goneFromSight() = card(
        name = "Gone from Sight",
        cost = "{3}{U}",
        type = "Instant",
        text = "Target nonland permanent's owner puts it on their choice of the top or bottom of their library. Surveil 1. (Look at the top card of your library. You may put it into your graveyard.)",
    ) parsesTo abilities(
        spell(
            Step.Choose(Choice.Option(listOf("top", "bottom")), bind = "position", chooser = Players.OwnerOf(Objects.Target("permanent"))),
            Step.If(
                Condition.Chose("position", "top"),
                then = listOf(Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Library, libraryPosition = 0)))),
                otherwise = listOf(Step.Do(Effect.Move(Objects.Target("permanent"), Destination(Zone.Library, libraryPosition = -1)))),
            ),
            Step.Do(Effect.Surveil(Players.You, Value.Fixed(1))),
            targets = listOf(slot("permanent", ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Land)), ObjectFilter.Category(ObjectCategory.Permanent))))),
        ),
    )
}
