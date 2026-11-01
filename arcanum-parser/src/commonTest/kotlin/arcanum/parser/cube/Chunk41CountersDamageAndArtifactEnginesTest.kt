package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.ActivationRules
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.CounterKind
import arcanum.api.Destination
import arcanum.api.Effect
import arcanum.api.EventPattern
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
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
import arcanum.api.Status
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TokenSpec
import arcanum.api.Trigger
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 41: creatures whose main text is an activated ability that uses counters, deals damage or works with artifacts
 * and graveyards: +1/+1 counters put and removed (Walking-Ballista style), damage to any target paid for by exiling or
 * discarding cards, an attacking-only ability, a graveyard exile that counts permanent cards, an artifact swap between
 * battlefield and graveyard (both targets still legal, CR 608.2b), a return from the graveyard, artifact sacrifice.
 */
class Chunk41CountersDamageAndArtifactEnginesTest {
    // ---- Shared shapes

    private fun mana(text: String) = CostPart.Mana(ManaCost.parse(text)!!)
    private val creature = ObjectFilter.Type(CardType.Creature)
    private val artifact = ObjectFilter.Type(CardType.Artifact)
    private val youControl = ObjectFilter.ControlledBy(PlayerFilter.You)
    private val plusOne = CounterKind.PowerToughness(1, 1)
    private fun draw(n: Int) = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(n)))

    private fun activated(vararg cost: CostPart, steps: List<Step>, targets: List<TargetSlot> = emptyList(), rules: ActivationRules = ActivationRules()) =
        Ability.Activated(
            cost = Cost(cost.toList()),
            instructions = Instructions(targets = targets, steps = steps),
            rules = rules,
        )

    private fun slot(name: String, filter: ObjectFilter) = TargetSlot(name = name, what = TargetKind.Objects(filter))

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

    /** "[Cost]: This creature deals [n] damage to any target." */
    private fun pinger(vararg cost: CostPart, damage: Int) = activated(
        *cost,
        targets = listOf(anyTarget),
        steps = listOf(Step.Do(Effect.DealDamage(Recipients(target = "any"), amount = Value.Fixed(damage)))),
    )

    private val removePlusOne = CostPart.RemoveCounters(plusOne, amount = Value.Fixed(1))

    private val selfEnters = EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), to = Zone.Battlefield)

    /** "This creature enters with [amount] +1/+1 counters on it." (CR 614.1c) */
    private fun entersWithPlusOne(amount: Value) = Ability.Static(listOf(Mechanic.Replacement(
        event = selfEnters,
        modify = listOf(Modification.EntersWithCounters(plusOne, amount)),
    )))

    // ---- +1/+1 counters

    @Test
    fun ironForeman() = card(
        name = "Iron Foreman",
        cost = "{2}",
        type = "Artifact Creature — Construct",
        pt = "1/1",
        text = "{T}: Put a +1/+1 counter on each artifact creature you control.",
    ) parsesTo abilities(
        activated(
            CostPart.TapSelf,
            steps = listOf(Step.Do(Effect.PutCounters(
                Objects.Each(ObjectFilter.And(listOf(artifact, creature, youControl))),
                plusOne,
                amount = Value.Fixed(1),
            ))),
        ),
    )

    // CR 107.3m: the X of the spell it was
    @Test
    fun stridingArbalest() = card(
        name = "Striding Arbalest",
        cost = "{X}{X}",
        type = "Artifact Creature — Construct",
        pt = "0/0",
        text = """
            This creature enters with X +1/+1 counters on it.
            {4}: Put a +1/+1 counter on this creature.
            Remove a +1/+1 counter from this creature: It deals 1 damage to any target.
        """,
    ) parsesTo abilities(
        entersWithPlusOne(Value.CastX()),
        activated(mana("{4}"), steps = listOf(Step.Do(Effect.PutCounters(Objects.Self, plusOne, amount = Value.Fixed(1))))),
        pinger(removePlusOne, damage = 1),
    )

    @Test
    fun uniskelion() = card(
        name = "Uniskelion",
        cost = "{2}",
        type = "Artifact Creature — Construct",
        pt = "1/1",
        text = """
            This creature enters with a +1/+1 counter on it.
            {1}, Remove a +1/+1 counter from this creature: It deals 1 damage to any target.
        """,
    ) parsesTo abilities(
        entersWithPlusOne(Value.Fixed(1)),
        pinger(mana("{1}"), removePlusOne, damage = 1),
    )

    // CR 702.151a: reconfigure; the Equipment's own counters count while it is attached
    @Test
    fun leopardSash() = card(
        name = "Leopard Sash",
        cost = "{1}{W}",
        type = "Artifact Creature — Equipment Cat",
        pt = "1/1",
        text = """
            {W}: Exile target card from a graveyard. If it was a permanent card, put a +1/+1 counter on this permanent.
            Equipped creature gets +1/+1 for each +1/+1 counter on this Equipment.
            Reconfigure {2} ({2}: Attach to target creature you control; or unattach from a creature. Reconfigure only as a sorcery. While attached, this isn't a creature.)
        """,
    ) parsesTo abilities(
        activated(
            mana("{W}"),
            targets = listOf(slot("card", ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard))))),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Exile), postBind = "exiled")),
                // CR 110.4a: a permanent card is an artifact, battle, creature, enchantment, land or planeswalker card
                Step.If(
                    condition = Condition.ObjectMatches(Objects.Bound("exiled"), ObjectFilter.Or(listOf(
                        ObjectFilter.Type(CardType.Artifact),
                        ObjectFilter.Type(CardType.Battle),
                        ObjectFilter.Type(CardType.Creature),
                        ObjectFilter.Type(CardType.Enchantment),
                        ObjectFilter.Type(CardType.Land),
                        ObjectFilter.Type(CardType.Planeswalker),
                    ))),
                    then = listOf(Step.Do(Effect.PutCounters(Objects.Self, plusOne, amount = Value.Fixed(1)))),
                ),
            ),
        ),
        Ability.Static(
            mechanics = listOf(Value.Of(NumericProperty.Counters(plusOne), Objects.Self, Combine.Single).let { counters ->
                Mechanic.ModifyPowerToughness(counters, counters)
            }),
            affects = arcanum.api.Affected.Objects(Objects.AttachedTo()),
        ),
        Keyword.Reconfigure(manaCost("{2}")),
    )

    // ---- Damage

    @Test
    fun bleakMagmacaller() = card(
        name = "Bleak Magmacaller",
        cost = "{R}",
        type = "Creature — Human Wizard",
        pt = "1/1",
        text = "{R}, {T}, Exile two cards from your graveyard: This creature deals 2 damage to any target.",
    ) parsesTo abilities(
        pinger(
            mana("{R}"),
            CostPart.TapSelf,
            CostPart.Exile(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card), count = Value.Fixed(2)), from = Zone.Graveyard),
            damage = 2,
        ),
    )

    // Choosing the land as it resolves, not targeting it (CR 115.10)
    @Test
    fun breathingTornado() = card(
        name = "Breathing Tornado",
        cost = "{R}{R}{G}",
        type = "Creature — Elemental",
        pt = "2/5",
        text = """
            {1}{R}, Discard a land card: This creature deals 2 damage to any target.
            {G}: Return a tapped land you control to its owner's hand.
        """,
    ) parsesTo abilities(
        pinger(mana("{1}{R}"), CostPart.Discard(CostObjects.Choose(ObjectFilter.Type(CardType.Land))), damage = 2),
        activated(
            mana("{G}"),
            steps = listOf(
                Step.Choose(
                    choice = Choice.Objects(
                        filter = ObjectFilter.And(listOf(ObjectFilter.HasStatus(Status.Tapped), ObjectFilter.Type(CardType.Land), youControl)),
                        min = Value.Fixed(1),
                        max = Value.Fixed(1),
                    ),
                    bind = "chosen",
                ),
                Step.Do(Effect.Move(Objects.Bound("chosen"), to = Destination(Zone.Hand))),
            ),
        ),
    )

    @Test
    fun glintTuskCorsair() = card(
        name = "Glint-Tusk Corsair",
        cost = "{1}{R}{R}",
        type = "Creature — Minotaur Pirate",
        pt = "2/4",
        text = """
            Haste
            Whenever you discard a card, this creature deals 1 damage to each opponent.
            {1}{R}, Discard a card: Draw a card. Activate only if this creature is attacking.
        """,
    ) parsesTo abilities(
        Keyword.Haste,
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Discard(ObjectFilter.Any, players = PlayerFilter.You)),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.DealDamage(Recipients(players = Players.Each(PlayerFilter.Opponent)), amount = Value.Fixed(1))),
            )),
        ),
        activated(
            mana("{1}{R}"),
            CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card))),
            steps = listOf(draw(1)),
            rules = ActivationRules(condition = Condition.ObjectMatches(Objects.Self, ObjectFilter.HasStatus(Status.Attacking))),
        ),
    )

    // ---- Artifacts and graveyards

    // CR 608.2b: "if both targets are still legal" does nothing unless both are; CR 608.2c: one simultaneous event
    @Test
    fun goblinSolderer() = card(
        name = "Goblin Solderer",
        cost = "{R}",
        type = "Creature — Goblin Artificer",
        pt = "1/1",
        text = "{T}: Choose target artifact a player controls and target artifact card in that player's graveyard. If both targets are still legal as this ability resolves, that player simultaneously sacrifices the artifact and returns the artifact card to the battlefield.",
    ) parsesTo abilities(
        activated(
            CostPart.TapSelf,
            targets = listOf(
                slot("artifact", artifact),
                slot("card", ObjectFilter.And(listOf(
                    artifact,
                    ObjectFilter.InZone(Zone.Graveyard),
                    ObjectFilter.OwnedBy(PlayerFilter.Is(Players.ControllerOf(Objects.Target("artifact")))),
                ))),
            ),
            steps = listOf(
                Step.If(
                    condition = Condition.TargetsLegal(listOf("artifact", "card")),
                    then = listOf(Step.Simultaneously(listOf(
                        Step.Do(Effect.Sacrifice(Objects.Target("artifact"))),
                        Step.Do(Effect.Move(
                            Objects.Target("card"),
                            to = Destination(Zone.Battlefield, controller = Players.ControllerOf(Objects.Target("artifact"))),
                        )),
                    ))),
                ),
            ),
        ),
    )

    // The ability works from the graveyard, where its cost and effect move this card from (CR 113.6m, left to the engine)
    @Test
    fun junkpileScavenger() = card(
        name = "Junkpile Scavenger",
        cost = "{2}",
        type = "Artifact Creature — Construct",
        pt = "3/2",
        text = """
            This creature can't block.
            {1}{B}, Exile another creature card from your graveyard: Return this card from your graveyard to the battlefield.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBlock())),
        activated(
            mana("{1}{B}"),
            CostPart.Exile(
                CostObjects.Choose(ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), creature))),
                from = Zone.Graveyard,
            ),
            steps = listOf(Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Battlefield), postBind = "returned"))),
        ),
    )

    @Test
    fun taviThopterSavant() = card(
        name = "Tavi, Thopter Savant",
        cost = "{2}{U}",
        type = "Legendary Creature — Human Artificer",
        pt = "1/4",
        text = """
            Whenever you cast an artifact spell, create a 1/1 colorless Thopter artifact creature token with flying.
            {1}{U}, Sacrifice two artifacts: Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Triggered(
            trigger = Trigger.On(EventPattern.Cast(spell = artifact, by = PlayerFilter.You)),
            instructions = Instructions(steps = listOf(
                Step.Do(Effect.CreateToken(TokenSpec.Defined(FaceDefinition(
                    name = "Thopter Token",
                    colorIndicator = emptyList(),
                    types = listOf(CardType.Artifact, CardType.Creature),
                    subtypes = listOf(Subtype("Thopter")),
                    power = PrintedNumber.Fixed(1),
                    toughness = PrintedNumber.Fixed(1),
                    abilities = listOf(Keyword.Flying),
                )))),
            )),
        ),
        activated(
            mana("{1}{U}"),
            CostPart.Sacrifice(CostObjects.Choose(artifact, count = Value.Fixed(2))),
            steps = listOf(draw(1)),
        ),
    )
}
