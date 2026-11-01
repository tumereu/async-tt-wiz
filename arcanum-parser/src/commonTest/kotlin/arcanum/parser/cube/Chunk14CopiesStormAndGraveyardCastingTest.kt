package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Choice
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Cost
import arcanum.api.CostObjects
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
import arcanum.api.Mechanic
import arcanum.api.Modification
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayKind
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.Recipients
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
 * Chunk 14: copying spells (CR 707.10; "you may choose new targets for the copy" is CopySpell's default, 707.10c),
 * storm cards (the keyword, CR 702.40a), "if this spell was cast from a graveyard", turn-long triggers ("until end of
 * turn, whenever …", CR 603.7b), and casting or playing cards from the graveyard or exile: granted flashback whose cost
 * is the card's mana cost (CR 702.34a), Yawgmoth-style permissions with an exile replacement, casting targeted cards
 * without paying, and divided damage with an X in the flashback cost (CR 601.2d, 107.3a).
 */
class Chunk14CopiesStormAndGraveyardCastingTest {
    // ---- Shared shapes

    /** "Any target" (CR 115.4), here with its counts. */
    private fun anyTarget(min: Value = Value.Fixed(1), max: Value? = Value.Fixed(1), divided: Value? = null) = TargetSlot(
        name = "any",
        what = TargetKind.Either(
            objects = ObjectFilter.Or(listOf(
                ObjectFilter.Type(CardType.Creature),
                ObjectFilter.Type(CardType.Planeswalker),
                ObjectFilter.Type(CardType.Battle),
            )),
            players = PlayerFilter.Any,
        ),
        min = min,
        max = max,
        divided = divided,
    )

    private val instantOrSorcery = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)))
    private val spell = ObjectFilter.Category(ObjectCategory.Spell)

    /** "Copy target [filter]." */
    private fun copyTargetSpell(filter: ObjectFilter) = TargetSlot(
        name = "spell",
        what = TargetKind.Objects(filter),
        min = Value.Fixed(1),
        max = Value.Fixed(1),
    )
    private val copyTarget = Step.Do(Effect.CopySpell(
        Objects.Target("spell"),
        controller = Players.You,
        newTargets = true,
    ))

    /** "That player or that permanent's controller": the player chosen for the any-target slot, or the controller of the
     * permanent chosen for it. */
    private val thatPlayerOrController = Players.Each(PlayerFilter.Or(listOf(
        PlayerFilter.Is(Players.Target("any")),
        PlayerFilter.Is(Players.ControllerOf(Objects.Target("any"))),
    )))

    private val yourGraveyard = listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))

    /** A token creature with no abilities. */
    private fun creatureToken(subtype: String, color: Color) = TokenSpec.Defined(FaceDefinition(
        name = "$subtype Token",
        colorIndicator = listOf(color),
        types = listOf(CardType.Creature),
        subtypes = listOf(Subtype(subtype)),
        power = PrintedNumber.Fixed(1),
        toughness = PrintedNumber.Fixed(1),
    ))

    /** "If [it] would be put into a graveyard, exile it instead." as a replacement of [cards] going to the graveyard. */
    private fun exileInsteadOfGraveyard(cards: ObjectFilter) = Mechanic.Replacement(
        event = EventPattern.ZoneChange(cards, to = Zone.Graveyard),
        modify = listOf(Modification.ChangeDestination(Destination(Zone.Exile))),
    )

    // ---- Copies

    /** The copy is made by the player who paid (CR 707.10: its controller is who put it on the Stack). "That player or
     * that permanent's controller" is whichever of them the any-target slot gives. */
    @Test
    fun linkedLightning() = card(
        name = "Linked Lightning",
        cost = "{R}",
        type = "Sorcery",
        text = "Linked Lightning deals 3 damage to any target. Then that player or that permanent's controller may pay {R}{R}. If the player does, they may copy this spell and may choose a new target for that copy.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(anyTarget()),
            steps = listOf(
                Step.Do(Effect.DealDamage(Recipients(target = "any"), amount = Value.Fixed(3), source = Objects.Self)),
                Step.Pay(
                    cost = manaCost("{R}{R}"),
                    payer = thatPlayerOrController,
                    bind = "paid",
                ),
                Step.If(
                    condition = Condition.Did("paid"),
                    then = listOf(Step.May(
                        player = thatPlayerOrController,
                        steps = listOf(Step.Do(Effect.CopySpell(
                            Objects.Self,
                            controller = thatPlayerOrController,
                            newTargets = true,
                        ))),
                    )),
                ),
            ),
        )),
    )

    @Test
    fun mountingRetribution() = card(
        name = "Mounting Retribution",
        cost = "{R}{R}",
        type = "Instant",
        text = """
            Copy target instant or sorcery spell you control. If this spell was cast from a graveyard, copy that spell twice instead. You may choose new targets for the copies.
            Flashback {3}{R}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(copyTargetSpell(ObjectFilter.And(listOf(instantOrSorcery, spell, ObjectFilter.ControlledBy(PlayerFilter.You))))),
            steps = listOf(
                Step.If(
                    condition = Condition.CastFrom(Zone.Graveyard, of = Objects.Self),
                    then = listOf(Step.Repeat(Value.Fixed(2), listOf(copyTarget))),
                    otherwise = listOf(copyTarget),
                ),
            ),
        )),
        Keyword.Flashback(manaCost("{3}{R}{R}")),
    )

    @Test
    fun flashOfMirroring() = card(
        name = "Flash of Mirroring",
        cost = "{1}{R}{R}",
        type = "Instant",
        text = """
            You may sacrifice a nontoken red creature rather than pay this spell's mana cost.
            Copy target instant or sorcery spell. You may choose new targets for the copy.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(
            Mechanic.AlternativeCost(cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.And(listOf(
                ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)),
                ObjectFilter.OfColor(Color.Red),
                ObjectFilter.Type(CardType.Creature),
            )), count = Value.Fixed(1)))))),
        ), affects = Affected.Objects(Objects.Self)),
        Ability.Spell(Instructions(
            targets = listOf(copyTargetSpell(ObjectFilter.And(listOf(instantOrSorcery, spell)))),
            steps = listOf(copyTarget),
        )),
    )

    /** CR 603.7b: a delayed trigger with a stated duration triggers every time until then. */
    @Test
    fun encoreRound() = card(
        name = "Encore Round",
        cost = "{1}{R}{R}",
        type = "Sorcery",
        text = "Until end of turn, whenever a player casts an instant or sorcery spell, that player copies it and may choose new targets for the copy.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.CreateDelayedTrigger(
                trigger = Trigger.On(EventPattern.Cast(spell = instantOrSorcery, by = PlayerFilter.Any)),
                instructions = Instructions(steps = listOf(
                    Step.Do(Effect.CopySpell(
                        Objects.EventObject(role = EventRole.Object),
                        controller = Players.EventPlayer,
                        newTargets = true,
                    )),
                )),
                duration = Duration.EndOfTurn,
                repeats = true,
            )),
        ))),
    )

    /** "Permanent card" (CR 110.4a). */
    @Test
    fun wardensReclamation() = card(
        name = "Warden's Reclamation",
        cost = "{2}{W}",
        type = "Sorcery",
        text = """
            Return target permanent card with mana value 3 or less from your graveyard to the battlefield. If this spell was cast from a graveyard, you may copy this spell and may choose a new target for the copy.
            Flashback {4}{W} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot(
                name = "card",
                what = TargetKind.Objects(ObjectFilter.And(listOf(
                    ObjectFilter.Or(listOf(
                        ObjectFilter.Type(CardType.Artifact),
                        ObjectFilter.Type(CardType.Battle),
                        ObjectFilter.Type(CardType.Creature),
                        ObjectFilter.Type(CardType.Enchantment),
                        ObjectFilter.Type(CardType.Land),
                        ObjectFilter.Type(CardType.Planeswalker),
                    )),
                    ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(3)),
                ) + yourGraveyard)),
                min = Value.Fixed(1),
                max = Value.Fixed(1),
            )),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("card"), to = Destination(Zone.Battlefield))),
                Step.If(
                    condition = Condition.CastFrom(Zone.Graveyard, of = Objects.Self),
                    then = listOf(Step.May(
                        listOf(Step.Do(Effect.CopySpell(Objects.Self, controller = Players.You, newTargets = true))),
                        player = Players.You,
                    )),
                ),
            ),
        )),
        Keyword.Flashback(manaCost("{4}{W}")),
    )

    // ---- Storm

    @Test
    fun mindChill() = card(
        name = "Mind Chill",
        cost = "{1}{U}",
        type = "Instant",
        text = """
            Target player mills three cards.
            Storm (When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot(
                name = "player",
                what = TargetKind.Players(PlayerFilter.Any),
                min = Value.Fixed(1),
                max = Value.Fixed(1),
            )),
            steps = listOf(Step.Do(Effect.Mill(Players.Target("player"), count = Value.Fixed(3)))),
        )),
        Keyword.Storm,
    )

    @Test
    fun chitterstorm() = card(
        name = "Chitterstorm",
        cost = "{1}{G}",
        type = "Sorcery",
        text = """
            Create a 1/1 green Squirrel creature token.
            Storm (When you cast this spell, copy it for each spell cast before it this turn.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.CreateToken(
                creatureToken("Squirrel", Color.Green),
                count = Value.Fixed(1),
                controller = Players.You,
            )),
        ))),
        Keyword.Storm,
    )

    @Test
    fun floodTheBurrows() = card(
        name = "Flood the Burrows",
        cost = "{3}{R}",
        type = "Sorcery",
        text = """
            Create two 1/1 red Goblin creature tokens.
            Storm (When you cast this spell, copy it for each spell cast before it this turn.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.CreateToken(
                creatureToken("Goblin", Color.Red),
                count = Value.Fixed(2),
                controller = Players.You,
            )),
        ))),
        Keyword.Storm,
    )

    @Test
    fun tendrilsOfTorment() = card(
        name = "Tendrils of Torment",
        cost = "{2}{B}{B}",
        type = "Sorcery",
        text = """
            Target player loses 2 life and you gain 2 life.
            Storm (When you cast this spell, copy it for each spell cast before it this turn. You may choose new targets for the copies.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(TargetSlot(
                name = "player",
                what = TargetKind.Players(PlayerFilter.Any),
                min = Value.Fixed(1),
                max = Value.Fixed(1),
            )),
            steps = listOf(
                Step.Do(Effect.LoseLife(Players.Target("player"), amount = Value.Fixed(2))),
                Step.Do(Effect.GainLife(Players.You, amount = Value.Fixed(2))),
            ),
        )),
        Keyword.Storm,
    )

    /** "Play" includes playing a land (CR 305.1). */
    @Test
    fun mindsLonging() = card(
        name = "Mind's Longing",
        cost = "{4}{U}{U}",
        type = "Sorcery",
        text = """
            Shuffle your library. Then exile the top card of your library. Until end of turn, you may play that card without paying its mana cost.
            Storm (When you cast this spell, copy it for each spell cast before it this turn.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.Shuffle(Players.You)),
            Step.Do(Effect.Move(
                Objects.LibraryCards(Players.You, count = Value.Fixed(1), fromTop = true),
                to = Destination(Zone.Exile),
                postBind = "exiled",
            )),
            Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.MayPlay(
                    Zone.Exile,
                    PlayKind.Play,
                    withoutPaying = true,
                    holder = PlayerFilter.You,
                )),
                affects = Affected.Objects(Objects.Bound("exiled")),
                duration = Duration.EndOfTurn,
            )),
        ))),
        Keyword.Storm,
    )

    // ---- Casting from the graveyard

    /** CR 702.34a: flashback whose cost is the card's own mana cost; the set of cards is fixed when it begins, since
     * gaining an ability changes characteristics (CR 611.2c). */
    @Test
    fun ashesOfYesterday() = card(
        name = "Ashes of Yesterday",
        cost = "{3}{R}",
        type = "Sorcery",
        text = """
            Each instant and sorcery card in your graveyard gains flashback until end of turn. The flashback cost is equal to its mana cost.
            Flashback {4}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.GainAbilities(listOf(Keyword.Flashback(Cost(listOf(CostPart.ItsManaCost)))))),
                affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(instantOrSorcery) + yourGraveyard))),
                duration = Duration.EndOfTurn,
            )),
        ))),
        Keyword.Flashback(manaCost("{4}{R}")),
    )

    /** A permission changes the rules, so it also covers cards that reach the graveyard later this turn (CR 611.2c). */
    @Test
    fun necromancersWill() = card(
        name = "Necromancer's Will",
        cost = "{2}{B}",
        type = "Sorcery",
        text = """
            Until end of turn, you may play lands and cast spells from your graveyard.
            If a card would be put into your graveyard from anywhere this turn, exile that card instead.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(steps = listOf(
            Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.MayPlay(Zone.Graveyard, PlayKind.Play, holder = PlayerFilter.You)),
                affects = Affected.Objects(Objects.Each(ObjectFilter.And(yourGraveyard))),
                duration = Duration.EndOfTurn,
            )),
            Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(exileInsteadOfGraveyard(ObjectFilter.And(listOf(
                    ObjectFilter.Category(ObjectCategory.Card),
                    ObjectFilter.OwnedBy(PlayerFilter.You),
                )))),
                affects = null,
                duration = Duration.EndOfTurn,
            )),
        ))),
    )

    /** Up to one target of each kind (CR 601.2c); which of them to cast is chosen on resolution. */
    @Test
    fun finaleOfVows() = card(
        name = "Finale of Vows",
        cost = "{X}{R}{R}",
        type = "Sorcery",
        text = "You may cast up to one target instant card and/or up to one target sorcery card from your graveyard each with mana value X or less without paying their mana costs. If a spell cast this way would be put into your graveyard, exile it instead. If X is 10 or more, copy each of those spells twice. You may choose new targets for the copies.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            announced = listOf("X"),
            targets = listOf(
                upToOneFromGraveyard("instant", CardType.Instant),
                upToOneFromGraveyard("sorcery", CardType.Sorcery),
            ),
            steps = listOf(
                Step.Choose(
                    choice = Choice.Objects(
                        filter = ObjectFilter.Any,
                        min = Value.Fixed(0),
                        max = Value.Fixed(2),
                        from = Objects.Union(listOf(Objects.Target("instant"), Objects.Target("sorcery"))),
                    ),
                    bind = "toCast",
                    chooser = Players.You,
                ),
                Step.Do(Effect.Cast(
                    Objects.Bound("toCast"),
                    withoutPaying = true,
                    bind = "cast",
                    how = PlayKind.Cast,
                    caster = Players.You,
                )),
                Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(exileInsteadOfGraveyard(ObjectFilter.Is(Objects.Bound("cast")))),
                    affects = null,
                    duration = Duration.Indefinitely,
                )),
                Step.If(
                    condition = Condition.Compare(Value.Announced(name = "X"), Comparison.AtLeast, Value.Fixed(10)),
                    then = listOf(Step.Repeat(
                        Value.Fixed(2),
                        listOf(Step.Do(Effect.CopySpell(
                            Objects.Bound("cast"),
                            controller = Players.You,
                            newTargets = true,
                        ))),
                    )),
                ),
            ),
        )),
    )

    private fun upToOneFromGraveyard(name: String, type: CardType) = TargetSlot(
        name = name,
        what = TargetKind.Objects(ObjectFilter.And(listOf(ObjectFilter.Type(type)) + yourGraveyard +
            ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced(name = "X")))),
        min = Value.Fixed(0),
        max = Value.Fixed(1),
    )

    /** CR 601.2d: the damage is divided as the spell is cast, at least 1 to each target; "any number" may be none.
     * The flashback cost's X is the X chosen for the spell (CR 107.3a), read outside the instructions as CastX. */
    @Test
    fun wildfireSpread() = card(
        name = "Wildfire Spread",
        cost = "{X}{X}{R}",
        type = "Sorcery",
        text = """
            Wildfire Spread deals X damage divided as you choose among any number of targets.
            Flashback—{R}{R}, Discard X cards. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            announced = listOf("X"),
            targets = listOf(anyTarget(min = Value.Fixed(0), max = null, divided = Value.Announced(name = "X"))),
            steps = listOf(Step.Do(Effect.DealDamage(
                Recipients(target = "any"),
                amount = Value.DividedShare,
                source = Objects.Self,
            ))),
        )),
        Keyword.Flashback(Cost(listOf(
            CostPart.Mana(ManaCost.parse("{R}{R}")!!),
            CostPart.Discard(CostObjects.Choose(
                ObjectFilter.Category(ObjectCategory.Card),
                count = Value.CastX(of = Objects.Self),
            )),
        ))),
    )
}
