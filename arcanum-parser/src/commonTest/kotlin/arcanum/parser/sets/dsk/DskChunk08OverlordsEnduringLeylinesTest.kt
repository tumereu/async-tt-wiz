package arcanum.parser.sets.dsk

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.ManaCost
import arcanum.api.ManaProduction
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.SubtypeRef
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Timing
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.parser.cube.abilities
import arcanum.parser.cube.anyOneColor
import arcanum.parser.cube.card
import arcanum.parser.cube.manaCost
import arcanum.parser.cube.parsesTo
import arcanum.parser.sets.dsk.Dsk.create
import arcanum.parser.sets.dsk.Dsk.creature
import arcanum.parser.sets.dsk.Dsk.creatureYouControl
import arcanum.parser.sets.dsk.Dsk.token
import arcanum.parser.sets.dsk.Dsk.youControl
import kotlin.test.Test

/**
 * Duskmourn chunk 08: the Overlords (impending, CR 702.176, and "whenever this permanent enters or attacks"), the
 * Enduring Glimmers ("When it dies, if it was a creature, return it … It's an enchantment."), and the Leylines (an
 * opening-hand action, CR 103.6a, with a replacement of life gain, a chosen creature type for creatures everywhere, an
 * exile replacement, a copy of single-target spells, an alternative cost for every spell you cast).
 */
class DskChunk08OverlordsEnduringLeylinesTest {
    private val entersOrAttacks = Trigger.On(listOf(
        EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
        EventPattern.Attack(ObjectFilter.Is(Objects.Self)),
    ))
    private fun overlord(vararg steps: Step, targets: List<TargetSlot> = emptyList()) =
        Ability.Triggered(entersOrAttacks, Instructions(targets = targets, steps = steps.toList()))
    private fun impending(n: Int, cost: String) = Keyword.Impending(n, manaCost(cost))
    private val impendingReminder: (Int) -> String = { n ->
        val word = listOf("zero", "one", "two", "three", "four", "five")[n]
        "(If you cast this spell for its impending cost, it enters with $word time counters and isn't a creature until the last is removed. At the beginning of your end step, remove a time counter from it.)"
    }

    /** "When this creature dies, if it was a creature, return it to the battlefield under its owner's control. It's an
     * enchantment.": the new card in the graveyard (CR 400.7), and it stops being a creature (layer 4, CR 613.1d). */
    private fun enduringReturn() = Ability.Triggered(
        trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
        instructions = Instructions(steps = listOf(
            Step.Do(Effect.Move(Objects.EventObject(EventRole.After), Destination(Zone.Battlefield,
                controller = Players.OwnerOf(Objects.EventObject(EventRole.After))), postBind = "returned")),
            Step.Do(Effect.ApplyMechanics(listOf(Mechanic.SetTypes(remove = listOf(CardType.Creature))), Affected.Objects(Objects.Bound("returned")),
                Duration.Indefinitely)),
        )),
        interveningIf = Condition.ObjectMatches(Objects.EventObject(EventRole.Before), ObjectFilter.Type(CardType.Creature)),
    )
    private fun enduringText(name: String) =
        "When $name dies, if it was a creature, return it to the battlefield under its owner's control. It's an enchantment. (It's not a creature.)"

    /** "If this card is in your opening hand, you may begin the game with it on the battlefield." (CR 103.6a) */
    private val beginOnBattlefield = Ability.Static(listOf(Mechanic.MayBeginOnBattlefield), affects = Affected.Objects(Objects.Self),
        functionsIn = listOf(Zone.Hand))
    private val leylineText = "If this card is in your opening hand, you may begin the game with it on the battlefield."

    // ---- Overlords

    @Test
    fun overseerOfTheMistmoors() = card(
        name = "Overseer of the Mistmoors",
        cost = "{5}{W}{W}",
        type = "Enchantment Creature — Avatar Horror",
        pt = "6/6",
        text = """
            Impending 4—{2}{W}{W} ${impendingReminder(4)}
            Whenever this permanent enters or attacks, create two 2/1 white Insect creature tokens with flying.
        """,
    ) parsesTo abilities(
        impending(4, "{2}{W}{W}"),
        overlord(create(token(listOf("Insect"), listOf(Color.White), 2, 1, abilities = listOf(Keyword.Flying)), count = 2)),
    )

    @Test
    fun overseerOfTheFloodpits() = card(
        name = "Overseer of the Floodpits",
        cost = "{3}{U}{U}",
        type = "Enchantment Creature — Avatar Horror",
        pt = "5/3",
        text = """
            Impending 4—{1}{U}{U} ${impendingReminder(4)}
            Flying
            Whenever this permanent enters or attacks, draw two cards, then discard a card.
        """,
    ) parsesTo abilities(
        impending(4, "{1}{U}{U}"),
        Keyword.Flying,
        overlord(
            Step.Do(Effect.Draw(Players.You, Value.Fixed(2))),
            Step.Do(Effect.Discard(Players.You, Value.Fixed(1), filter = ObjectFilter.Any)),
        ),
    )

    @Test
    fun overseerOfTheBalemurk() = card(
        name = "Overseer of the Balemurk",
        cost = "{3}{B}{B}",
        type = "Enchantment Creature — Avatar Horror",
        pt = "5/5",
        text = """
            Impending 5—{1}{B} ${impendingReminder(5)}
            Whenever this permanent enters or attacks, mill four cards, then you may return a non-Avatar creature card or a planeswalker card from your graveyard to your hand.
        """,
    ) parsesTo abilities(
        impending(5, "{1}{B}"),
        overlord(
            Step.Do(Effect.Mill(Players.You, Value.Fixed(4))),
            Step.Choose(
                choice = Choice.Objects(
                    filter = ObjectFilter.And(listOf(
                        ObjectFilter.Or(listOf(
                            ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Sub(Subtype("Avatar"))), creature)),
                            ObjectFilter.Type(CardType.Planeswalker),
                        )),
                        ObjectFilter.InZone(Zone.Graveyard),
                        ObjectFilter.OwnedBy(PlayerFilter.You),
                    )),
                    min = Value.Fixed(0),
                    max = Value.Fixed(1),
                ),
                bind = "chosen",
                chooser = Players.You,
            ),
            Step.Do(Effect.Move(Objects.Bound("chosen"), Destination(Zone.Hand))),
        ),
    )

    @Test
    fun overseerOfTheBoilerbilges() = card(
        name = "Overseer of the Boilerbilges",
        cost = "{4}{R}{R}",
        type = "Enchantment Creature — Avatar Horror",
        pt = "5/5",
        text = """
            Impending 4—{2}{R}{R} ${impendingReminder(4)}
            Whenever this permanent enters or attacks, it deals 4 damage to any target.
        """,
    ) parsesTo abilities(
        impending(4, "{2}{R}{R}"),
        overlord(
            Step.Do(Effect.DealDamage(Recipients(target = "any"), Value.Fixed(4), source = Objects.Self)),
            targets = listOf(TargetSlot("any", TargetKind.Either(ObjectFilter.Or(listOf(creature, ObjectFilter.Type(CardType.Planeswalker),
                ObjectFilter.Type(CardType.Battle))), PlayerFilter.Any), min = Value.Fixed(1), max = Value.Fixed(1))),
        ),
    )

    /** A named land token with every basic land type (CR 111.4, 205.3i: no mana abilities of its own; they come from its types,
     * CR 305.6). */
    @Test
    fun overseerOfTheHauntwoods() = card(
        name = "Overseer of the Hauntwoods",
        cost = "{3}{G}{G}",
        type = "Enchantment Creature — Avatar Horror",
        pt = "6/5",
        text = """
            Impending 4—{1}{G}{G} ${impendingReminder(4)}
            Whenever this permanent enters or attacks, create a tapped colorless land token named Everywhere that is every basic land type.
        """,
    ) parsesTo abilities(
        impending(4, "{1}{G}{G}"),
        overlord(Step.Do(Effect.CreateToken(
            TokenSpec.Defined(FaceDefinition(
                name = "Everywhere",
                colorIndicator = emptyList(),
                types = listOf(CardType.Land),
                subtypes = listOf("Plains", "Island", "Swamp", "Mountain", "Forest").map { Subtype(it) },
            )),
            count = Value.Fixed(1),
            controller = Players.You,
            tapped = true,
        ))),
    )

    // ---- Enduring Glimmers

    /** "One or more other creatures you control with power 2 or less enter": once for the batch, once each turn. */
    @Test
    fun lastingInnocence() = card(
        name = "Lasting Innocence",
        cost = "{1}{W}{W}",
        type = "Enchantment Creature — Sheep Glimmer",
        pt = "2/1",
        text = """
            Lifelink
            Whenever one or more other creatures you control with power 2 or less enter, draw a card. This ability triggers only once each turn.
            ${enduringText("Lasting Innocence")}
        """,
    ) parsesTo abilities(
        Keyword.Lifelink,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(
                ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, youControl, ObjectFilter.Power(Comparison.AtMost, Value.Fixed(2)),
            )), to = Zone.Battlefield), oneOrMore = true),
            instructions = Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1))))),
            perTurn = 1,
        ),
        enduringReturn(),
    )

    @Test
    fun lastingCuriosity() = card(
        name = "Lasting Curiosity",
        cost = "{2}{U}{U}",
        type = "Enchantment Creature — Cat Glimmer",
        pt = "4/3",
        text = """
            Flash
            Whenever a creature you control deals combat damage to a player, draw a card.
            ${enduringText("Lasting Curiosity")}
        """,
    ) parsesTo abilities(
        Keyword.Flash,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Damage(creatureYouControl, arcanum.api.RecipientFilter(players = PlayerFilter.Any), combat = true)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.Draw(Players.You, Value.Fixed(1))))),
        ),
        enduringReturn(),
    )

    /** "That much life": the life gained (the event's amount). */
    @Test
    fun lastingTenacity() = card(
        name = "Lasting Tenacity",
        cost = "{2}{B}{B}",
        type = "Enchantment Creature — Snake Glimmer",
        pt = "4/3",
        text = """
            Whenever you gain life, target opponent loses that much life.
            ${enduringText("Lasting Tenacity")}
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.GainLife(PlayerFilter.You)),
            instructions = Instructions(
                targets = listOf(TargetSlot("opponent", TargetKind.Players(PlayerFilter.Opponent), min = Value.Fixed(1), max = Value.Fixed(1))),
                steps = listOf(Step.Do(Effect.LoseLife(Players.Target("opponent"), Value.EventAmount))),
            ),
        ),
        enduringReturn(),
    )

    @Test
    fun lastingCourage() = card(
        name = "Lasting Courage",
        cost = "{2}{R}{R}",
        type = "Enchantment Creature — Dog Glimmer",
        pt = "3/3",
        text = """
            Whenever another creature you control enters, it gets +2/+0 and gains haste until end of turn.
            ${enduringText("Lasting Courage")}
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature, youControl)),
                to = Zone.Battlefield)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.ApplyMechanics(
                listOf(Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(0)), Mechanic.GainAbilities(listOf(Keyword.Haste))),
                Affected.Objects(Objects.EventObject(EventRole.After)), Duration.EndOfTurn)))),
        ),
        enduringReturn(),
    )

    @Test
    fun lastingVitality() = card(
        name = "Lasting Vitality",
        cost = "{1}{G}{G}",
        type = "Enchantment Creature — Elk Glimmer",
        pt = "3/3",
        text = """
            Vigilance
            Creatures you control have "{T}: Add one mana of any color."
            ${enduringText("Lasting Vitality")}
        """,
    ) parsesTo abilities(
        Keyword.Vigilance,
        Ability.Static(
            mechanics = listOf(Mechanic.GainAbilities(listOf(Ability.Activated(
                cost = Cost(listOf(CostPart.TapSelf)),
                instructions = Instructions(steps = listOf(Step.Do(Effect.AddMana(ManaProduction(anyOneColor(1)), Players.You)))),
                rules = ActivationRules(timing = Timing.Any, activatedBy = PlayerFilter.You),
            )))),
            affects = Affected.Objects(Objects.Each(creatureYouControl)),
        ),
        enduringReturn(),
    )

    // ---- Leylines

    /** Life gain plus one (CR 614.1a); "at least 7 life more than your starting life total" (CR 103.4). */
    @Test
    fun leyLineOfHope() = card(
        name = "Ley Line of Hope",
        cost = "{2}{W}{W}",
        type = "Enchantment",
        text = """
            $leylineText
            If you would gain life, you gain that much life plus 1 instead.
            As long as you have at least 7 life more than your starting life total, creatures you control get +2/+2.
        """,
    ) parsesTo abilities(
        beginOnBattlefield,
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(EventPattern.GainLife(PlayerFilter.You), modify = listOf(Modification.AddAmount(Value.Fixed(1))))),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(2), Value.Fixed(2))),
            affects = Affected.Objects(Objects.Each(creatureYouControl)),
            condition = Condition.Compare(Value.LifeOf(Players.You), Comparison.AtLeast, Value.Sum(listOf(Value.StartingLife(Players.You), Value.Fixed(7)))),
        ),
    )

    /** The chosen type (CR 607.2d) for creatures on the battlefield, creature spells and creature cards elsewhere. */
    @Test
    fun leyLineOfTransformation() = card(
        name = "Ley Line of Transformation",
        cost = "{2}{U}{U}",
        type = "Enchantment",
        text = """
            $leylineText
            As this enchantment enters, choose a creature type.
            Creatures you control are the chosen type in addition to their other types. The same is true for creature spells you control and creature cards you own that aren't on the battlefield.
        """,
    ) parsesTo abilities(
        beginOnBattlefield,
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield),
                modify = listOf(Modification.AsEnters(listOf(Step.Choose(Choice.CreatureType, bind = "type", remember = true, chooser = Players.You)))),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
        Ability.Static(
            mechanics = listOf(Mechanic.SetTypes(addSubtypes = listOf(SubtypeRef.Remembered("type")))),
            affects = Affected.Objects(Objects.Union(listOf(
                Objects.Each(creatureYouControl),
                Objects.Each(ObjectFilter.And(listOf(creature, ObjectFilter.Category(ObjectCategory.Spell), youControl))),
                Objects.Each(ObjectFilter.And(listOf(
                    creature,
                    ObjectFilter.OwnedBy(PlayerFilter.You),
                    ObjectFilter.Or(listOf(Zone.Hand, Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Command).map { ObjectFilter.InZone(it) }),
                ))),
            ))),
        ),
    )

    /** "If a card would be put into an opponent's graveyard from anywhere, exile it instead." (CR 614.1a; tokens aren't
     * cards) */
    @Test
    fun leyLineOfTheVoid() = card(
        name = "Ley Line of the Void",
        cost = "{2}{B}{B}",
        type = "Enchantment",
        text = """
            $leylineText
            If a card would be put into an opponent's graveyard from anywhere, exile it instead.
        """,
    ) parsesTo abilities(
        beginOnBattlefield,
        Ability.Static(
            mechanics = listOf(Mechanic.Replacement(
                EventPattern.ZoneChange(ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.Opponent))), to = Zone.Graveyard),
                modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))),
            )),
            affects = Affected.Objects(Objects.Self),
        ),
    )

    /** "A spell that targets only a single creature you control" (one target, CR 115.7); a copy with new targets (CR 707.10c). */
    @Test
    fun leyLineOfResonance() = card(
        name = "Ley Line of Resonance",
        cost = "{2}{R}{R}",
        type = "Enchantment",
        text = """
            $leylineText
            Whenever you cast an instant or sorcery spell that targets only a single creature you control, copy that spell. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        beginOnBattlefield,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(ObjectFilter.And(listOf(
                ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                ObjectFilter.Category(ObjectCategory.Spell),
                ObjectFilter.SingleTarget(creatureYouControl),
            )), by = PlayerFilter.You)),
            instructions = Instructions(steps = listOf(Step.Do(Effect.CopySpell(Objects.EventObject(EventRole.Object), controller = Players.You, newTargets = true)))),
        ),
    )

    /** An alternative cost (CR 118.9) for every spell its controller casts, from wherever it is cast. */
    @Test
    fun leyLineOfMutation() = card(
        name = "Ley Line of Mutation",
        cost = "{2}{G}{G}",
        type = "Enchantment",
        text = """
            $leylineText
            You may pay {W}{U}{B}{R}{G} rather than pay the mana cost for spells you cast.
        """,
    ) parsesTo abilities(
        beginOnBattlefield,
        Ability.Static(
            mechanics = listOf(Mechanic.AlternativeCost(cost = Cost.mana(ManaCost.parse("{W}{U}{B}{R}{G}")!!))),
            affects = Affected.Objects(Objects.Each(ObjectFilter.Or(listOf(Zone.Hand, Zone.Library, Zone.Graveyard, Zone.Exile, Zone.Command)
                .map { ObjectFilter.InZone(it) }))),
        ),
    )
}
