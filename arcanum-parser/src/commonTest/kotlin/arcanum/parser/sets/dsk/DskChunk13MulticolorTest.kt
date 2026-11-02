package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.ColorRef
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CopyException
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.CountersToPut
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.RecipientFilter
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
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
import arcanum.parser.sets.dsk.Dsk.create
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.creatureYouControl
import arcanum.parser.sets.dsk.Dsk.enchantment
import arcanum.parser.sets.dsk.Dsk.token
import arcanum.parser.sets.dsk.Dsk.youControl
import kotlin.test.Test

/**
 * Duskmourn chunk 13: the multicoloured cards. One X for two effects; an Equipment attaching itself as it enters; a
 * token copy with several exceptions (CR 707.9b) sacrificed at the next end step; a planeswalker that is a creature during
 * its controller's turn (layer 4); opponents who lost life this turn; "any number … from among them" and "if you put no
 * cards into your hand this way"; Shards becoming copies until the next end step and the exiled card returning then
 * (CR 603.7); permanents sacrificed this turn; a graveyard permission with a counter for using it; a prime number of lands.
 */
class DskChunk13MulticolorTest {
    private fun slot(name: String, filter: ObjectFilter, min: Int = 1, max: Int = 1) =
        TargetSlot(name, TargetKind.Objects(filter), min = Value.Fixed(min), max = Value.Fixed(max))
    private val any = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You)
    private val sorcery = ActivationRules(timing = Timing.Sorcery, activatedBy = PlayerFilter.You)
    private fun loyalty(amount: Int, vararg steps: Step, targets: List<TargetSlot> = emptyList()) = Ability.Activated(
        cost = Cost(listOf(CostPart.Loyalty(Value.Fixed(amount)))),
        instructions = Instructions(targets = targets, steps = steps.toList()),
        rules = any,
    )
    private val atNextEndStep = Trigger.On(EventPattern.StepBegins(TurnStep.End, whose = PlayerFilter.Any))
    private val atYourEndStep = Trigger.On(EventPattern.StepBegins(TurnStep.End, whose = PlayerFilter.You))
    private val anyTarget = TargetSlot("any", TargetKind.Either(
        ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Planeswalker), ObjectFilter.Type(CardType.Battle))), PlayerFilter.Any),
        min = Value.Fixed(1), max = Value.Fixed(1))
    private val landsYouControl = Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land), youControl)))

    /** "Where X is …" defines both of its X's. */
    @Test
    fun annabelForsakenDoll() = card(
        name = "Annabel, Forsaken Doll",
        cost = "{R}{W}",
        type = "Legendary Artifact Creature — Toy",
        pt = "1/3",
        text = "Whenever Annabel attacks, it deals X damage to each opponent and you gain X life, where X is the number of creatures you control with power 2 or less.",
    ) parsesTo abilities(
        Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.Self))), Instructions(steps = listOf(
            Step.Do(Effect.DealDamage(Recipients(players = Players.Each(PlayerFilter.Opponent)), smallCreatures, source = Objects.Self)),
            Step.Do(Effect.GainLife(Players.You, smallCreatures)),
        ))),
    )
    private val smallCreatures = Value.Count(Objects.Each(ObjectFilter.And(listOf(creature, youControl, ObjectFilter.Power(Comparison.AtMost, Value.Fixed(2))))))

    @Test
    fun cricketBat() = card(
        name = "Cricket Bat",
        cost = "{G}{W}",
        type = "Artifact — Equipment",
        text = """
            When this Equipment enters, attach it to target creature you control.
            Equipped creature gets +1/+1.
            Whenever equipped creature attacks, tap up to one target creature.
            Equip {3} ({3}: Attach to target creature you control. Equip only as a sorcery.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(
            targets = listOf(slot("creature", creatureYouControl)),
            steps = listOf(Step.Do(Effect.Attach(Objects.Self, Recipients(objects = Objects.Target("creature"))))),
        )),
        Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1))), affects = Affected.Objects(Objects.AttachedTo(Objects.Self))),
        Ability.Triggered(Trigger.On(EventPattern.Attack(ObjectFilter.Is(Objects.AttachedTo(Objects.Self)))), Instructions(
            targets = listOf(slot("creature", creature, min = 0)),
            steps = listOf(Step.Do(Effect.Tap(Objects.Target("creature")))),
        )),
        Keyword.Equip(manaCost("{3}")),
    )

    /** A token copy with a set P/T, an added colour, an added type and subtype, and added keywords (CR 707.9b). */
    @Test
    fun theMerryBalloonMan() = card(
        name = "The Merry Balloon Man",
        cost = "{1}{R}{W}",
        type = "Legendary Creature — Human Clown",
        pt = "1/4",
        text = """
            Haste
            {1}, {T}: Create a token that's a copy of another target creature you control, except it's a 1/1 red Balloon creature in addition to its other colors and types and it has flying and haste. Sacrifice it at the beginning of the next end step. Activate only as a sorcery.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{1}")!!), CostPart.TapSelf)),
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, youControl)))),
                steps = listOf(
                    Step.Do(Effect.CreateToken(TokenSpec.CopyOf(Objects.Target("creature"), listOf(
                        CopyException.PowerToughness(1, 1),
                        CopyException.Colors(listOf(ColorRef.Literal(Color.Red)), add = true),
                        CopyException.Types(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Balloon")))),
                        CopyException.AddAbilities(listOf(Keyword.Flying, Keyword.Haste)),
                    )), count = Value.Fixed(1), controller = Players.You, bind = "tokens")),
                    Step.Do(Effect.CreateDelayedTrigger(atNextEndStep, Instructions(steps = listOf(Step.Do(Effect.Sacrifice(Objects.Bound("tokens"))))))),
                ),
            ),
            rules = sorcery,
        ),
    )

    /** A planeswalker that is a creature during your turn while it has loyalty (layers 4 and 7b); "each opponent who lost
     * life this turn". */
    @Test
    fun kaidenBaneOfDreams() = card(
        name = "Kaiden, Bane of Dreams",
        cost = "{2}{U}{B}",
        type = "Legendary Planeswalker — Kaito",
        loyalty = "4",
        text = """
            Ninjutsu {1}{U}{B} ({1}{U}{B}, Return an unblocked attacker you control to hand: Put this card onto the battlefield from your hand tapped and attacking.)
            During your turn, as long as Kaiden has one or more loyalty counters on him, he's a 3/4 Ninja creature and has hexproof.
            [+1]: You get an emblem with "Ninjas you control get +1/+1."
            [0]: Surveil 2. Then draw a card for each opponent who lost life this turn.
            [−2]: Tap target creature. Put two stun counters on it.
        """,
    ) parsesTo abilities(
        Keyword.Ninjutsu(manaCost("{1}{U}{B}")),
        Ability.Static(
            mechanics = listOf(
                Mechanic.SetTypes(add = listOf(CardType.Creature), addSubtypes = listOf(SubtypeRef.Literal(Subtype("Ninja")))),
                Mechanic.SetPowerToughness(Value.Fixed(3), Value.Fixed(4)),
                Mechanic.GainAbilities(listOf(Keyword.Hexproof)),
            ),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.And(listOf(Condition.YourTurn, Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(CounterKind.Loyalty, atLeast = 1)))),
        ),
        loyalty(1, Step.Do(Effect.CreateEmblem(listOf(Ability.Static(listOf(Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Ninja")), youControl)))))), Players.You))),
        loyalty(0,
            Step.Do(Effect.Surveil(Players.You, Value.Fixed(2))),
            Step.Do(Effect.Draw(Players.You, Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent,
                PlayerFilter.ThisTurn(EventPattern.LoseLife(PlayerFilter.Any)))))))),
        ),
        loyalty(-2,
            Step.Do(Effect.Tap(Objects.Target("creature"))),
            Step.Do(Effect.PutCounters(Objects.Target("creature"), CounterKind.Stun, Value.Fixed(2))),
            targets = listOf(slot("creature", creature)),
        ),
    )

    @Test
    fun midnightMischief() = card(
        name = "Midnight Mischief",
        cost = "{2}{R}{W}",
        type = "Sorcery",
        text = "Create three 1/1 red Gremlin creature tokens. Gremlins you control gain menace, lifelink, and haste until end of turn. (A creature with menace can't be blocked except by two or more creatures.)",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            create(token(listOf("Gremlin"), listOf(Color.Red), 1, 1), count = 3),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.GainAbilities(listOf(Keyword.Menace, Keyword.Lifelink, Keyword.Haste))),
                Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Gremlin")), youControl)))), Duration.EndOfTurn)),
        ))),
    )

    /** "Mill that many cards", any number from among them, and a counter when none were put into the hand. */
    @Test
    fun nashkoSeekerInTheDark() = card(
        name = "Nashko, Seeker in the Dark",
        cost = "{U}{B}",
        type = "Legendary Creature — Rat Ninja Wizard",
        pt = "2/2",
        text = """
            Menace
            Whenever Nashko deals combat damage to a player, you mill that many cards. You may put any number of legendary and/or enchantment cards from among them into your hand. If you put no cards into your hand this way, put a +1/+1 counter on Nashko.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Damage(ObjectFilter.Is(Objects.Self), RecipientFilter(players = PlayerFilter.Any), combat = true)),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.Mill(Players.You, Value.EventAmount, postBind = "milled")),
                Step.Choose(Choice.Objects(ObjectFilter.Or(listOf(ObjectFilter.Super(Supertype.Legendary), enchantment)), min = Value.Fixed(0), max = null,
                    from = Objects.Bound("milled")), bind = "chosen", chooser = Players.You),
                Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
                Step.If(Condition.Compare(Value.Count(Objects.Bound("chosen")), Comparison.Equal, Value.Fixed(0)), then = listOf(
                    Step.Do(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(1))),
                )),
            )),
        ),
    )

    /** Shards become copies of the exiled card until the next end step (layer 1), and it returns then (CR 603.7). */
    @Test
    fun nikaLightOfPromise() = card(
        name = "Nika, Light of Promise",
        cost = "{2}{W}{U}",
        type = "Legendary Creature — Human Wizard",
        pt = "3/4",
        text = """
            When Nika enters, create two Shard tokens. (They're enchantments with "{2}, Sacrifice this token: Scry 1, then draw a card.")
            {2}, {T}: Exile target nonlegendary creature you control. Shards you control become copies of it until the next end step. Return it to the battlefield under its owner's control at the beginning of the next end step.
        """,
    ) parsesTo abilities(
        Ability.Triggered(thisEnters, Instructions(steps = listOf(create(TokenSpec.Predefined("Shard"), count = 2)))),
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{2}")!!), CostPart.TapSelf)),
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Super(Supertype.Legendary)), creature, youControl)))),
                steps = listOf(
                    Step.Do(Effect.Move(Objects.Target("creature"), Destination(Zone.Exile), postBind = "exiled")),
                    Step.Do(Effect.ApplyMechanics(listOf(Mechanic.CopyOf(Objects.Bound("exiled"))),
                        Affected.Objects(Objects.Each(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Shard")), youControl)))),
                        Duration.UntilStep(TurnStep.End, PlayerFilter.Any))),
                    Step.Do(Effect.CreateDelayedTrigger(atNextEndStep, Instructions(steps = listOf(
                        Step.Do(Effect.Move(Objects.Bound("exiled"), Destination(Zone.Battlefield))))))),
                ),
            ),
            rules = any,
        ),
    )

    @Test
    fun riteOfTheLanternMoth() = card(
        name = "Rite of the Lantern Moth",
        cost = "{1}{W}{B}{B}",
        type = "Sorcery",
        text = """
            Return target creature card from your graveyard to the battlefield with a finality counter on it. (If a creature with a finality counter on it would die, exile it instead.)
            Flashback {3}{W}{W}{B} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("card", ObjectFilter.And(listOf(creature, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))),
            steps = listOf(Step.Do(Effect.Move(Objects.Target("card"), Destination(Zone.Battlefield,
                counters = listOf(CountersToPut(CounterKind.Finality, Value.Fixed(1))))))),
        )),
        Keyword.Flashback(manaCost("{3}{W}{W}{B}")),
    )

    /** "That much damage": the permanents you sacrificed this turn (an intervening if, CR 603.4). */
    @Test
    fun sawtoothSkinflayer() = card(
        name = "Sawtooth Skinflayer",
        cost = "{1}{B}{R}",
        type = "Creature — Human Assassin",
        pt = "3/2",
        text = """
            Menace
            {2}, Sacrifice another creature or enchantment: Put a +1/+1 counter on this creature.
            At the beginning of your end step, if you sacrificed one or more permanents this turn, this creature deals that much damage to any target.
        """,
    ) parsesTo abilities(
        Keyword.Menace,
        Ability.Activated(
            cost = Cost(listOf(CostPart.Mana(arcanum.api.ManaCost.parse("{2}")!!), CostPart.Sacrifice(CostObjects.Choose(
                ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), ObjectFilter.Or(listOf(creature, enchantment)))), count = Value.Fixed(1))))),
            instructions = Instructions(steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, CounterKind.PowerToughness(1, 1), Value.Fixed(1))))),
            rules = any,
        ),
        Ability.Triggered(
            trigger = atYourEndStep,
            instructions = Instructions(targets = listOf(anyTarget), steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"),
                Value.EventsThisTurn(sacrificedByYou, Measure.Objects), source = Objects.Self)))),
            interveningIf = Condition.HappenedThisTurn(sacrificedByYou, atLeast = 1, measure = Measure.Objects),
        ),
    )
    private val sacrificedByYou = EventPattern.Sacrifice(ObjectFilter.ControlledBy(PlayerFilter.You))

    /** A permission to cast it from the graveyard under a condition; "if you do": cast from the graveyard (CR 614.1c). */
    @Test
    fun undeadDasher() = card(
        name = "Undead Dasher",
        cost = "{B}{R}",
        type = "Creature — Zombie",
        pt = "2/2",
        text = """
            Trample, haste
            You may cast this card from your graveyard if a non-Zombie creature died this turn. If you do, this creature enters with a +1/+1 counter on it.
        """,
    ) parsesTo abilities(
        Keyword.Trample,
        Keyword.Haste,
        Ability.Static(
            mechanics = listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Cast, holder = PlayerFilter.You)),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Sub(Subtype("Zombie"))), creature)),
                from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.EntersWithCounters(CounterKind.PowerToughness(1, 1), Value.Fixed(1))))),
            affects = Affected.Objects(Objects.Self),
            condition = Condition.CastFrom(Zone.Graveyard, Objects.Self),
        ),
    )

    /** A prime number of lands (the reminder lists them); a named legendary token; "that many": the lands. */
    @Test
    fun zimraAllWondering() = card(
        name = "Zimra, All-Wondering",
        cost = "{1}{G}{U}",
        type = "Legendary Creature — Human Wizard",
        pt = "1/1",
        text = """
            At the beginning of your end step, if a land entered the battlefield under your control this turn and you control a prime number of lands, create Prima, the Undivided, a legendary 0/0 green and blue Fractal creature token, then put that many +1/+1 counters on it. (2, 3, 5, 7, 11, 13, 17, 19, 23, 29, and 31 are prime numbers.)
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = atYourEndStep,
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
                    name = "Prima, the Undivided",
                    colorIndicator = listOf(Color.Green, Color.Blue),
                    supertypes = listOf(Supertype.Legendary),
                    types = listOf(CardType.Creature),
                    subtypes = listOf(Subtype("Fractal")),
                    power = PrintedNumber.Fixed(0),
                    toughness = PrintedNumber.Fixed(0),
                )), count = Value.Fixed(1), controller = Players.You, bind = "tokens")),
                Step.Do(Effect.PutCounters(Objects.Bound("tokens"), CounterKind.PowerToughness(1, 1), Value.Count(landsYouControl))),
            )),
            interveningIf = Condition.And(listOf(
                Condition.HappenedThisTurn(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Land), youControl)), to = Zone.Battlefield),
                    atLeast = 1, measure = Measure.Events),
                Condition.Prime(Value.Count(landsYouControl)),
            )),
        ),
    )
}
