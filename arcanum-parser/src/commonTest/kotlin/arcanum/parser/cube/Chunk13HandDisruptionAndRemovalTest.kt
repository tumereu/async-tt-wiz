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
import arcanum.api.CostChange
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Each
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.KeywordAction
import arcanum.api.ManaCost
import arcanum.api.ManaPaymentWay
import arcanum.api.Mechanic
import arcanum.api.Mode
import arcanum.api.Modes
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
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
 * Chunk 13: hand disruption and removal. "Target player reveals their hand. You choose … from it. That player discards
 * that card." is a reveal, a choice by you from the revealed cards and a discard of exactly that card (D47: someone else
 * choosing is Choose + Discard(objects)); a card name chosen on resolution; cleave's square brackets as
 * If(Not(Paid(Cleave))) (CR 702.148a); escalate with a non-mana cost (CR 702.120a); "each opponent sacrifices" as a
 * loop in which each opponent chooses and all sacrifice together (CR 101.4, 608.2e); convoke and connive; a cost
 * reduction that counts the cards exiled for an additional cost; a token with a quoted dies trigger.
 */
class Chunk13HandDisruptionAndRemovalTest {
    // ---- Shared shapes

    private fun slot(name: String, filter: ObjectFilter) = TargetSlot(
        name = name,
        what = TargetKind.Objects(filter),
        min = Value.Fixed(1),
        max = Value.Fixed(1),
    )
    private val targetPlayer = TargetSlot(
        name = "player",
        what = TargetKind.Players(PlayerFilter.Any),
        min = Value.Fixed(1),
        max = Value.Fixed(1),
    )
    private val targetOpponent = TargetSlot(
        name = "opponent",
        what = TargetKind.Players(PlayerFilter.Opponent),
        min = Value.Fixed(1),
        max = Value.Fixed(1),
    )
    private val nonland = ObjectFilter.Not(ObjectFilter.Type(CardType.Land))
    private val creatureOrPlaneswalker = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.Type(CardType.Planeswalker)))
    private val artifactOrCreature = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Creature)))
    /** In "each opponent sacrifices …": controlled by the opponent whose turn of the loop it is. */
    private val theirs = ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Bound("opponent")))

    /** The cards in [player]'s hand. */
    private fun handOf(player: Players) = Objects.Each(ObjectFilter.And(listOf(
        ObjectFilter.InZone(Zone.Hand),
        ObjectFilter.OwnedBy(PlayerFilter.Is(player)),
    )))

    /** "[Target player] reveals their hand." */
    private fun reveal(player: Players) = Step.Do(Effect.Reveal(handOf(player), bind = "revealed"))

    /** "You choose a [filter] card from it." */
    private fun chooseFromHand(filter: ObjectFilter) = Step.Choose(
        choice = Choice.Objects(filter, min = Value.Fixed(1), max = Value.Fixed(1), from = Objects.Bound("revealed")),
        bind = "chosen",
        chooser = Players.You,
    )

    /** "That player discards that card." */
    private fun discardChosen(player: Players) = Step.Do(Effect.Discard(
        player,
        objects = Objects.Bound("chosen"),
        count = Value.Fixed(1),
        filter = ObjectFilter.Any,
    ))

    /** "Target [player] reveals their hand. You choose a [filter] card from it. That player discards that card." */
    private fun handDisruption(target: TargetSlot, filter: ObjectFilter, vararg more: Step) = Instructions(
        targets = listOf(target),
        steps = listOf(
            reveal(Players.Target(target.name)),
            chooseFromHand(filter),
            discardChosen(Players.Target(target.name)),
        ) + more,
    )

    // ---- Hand disruption

    @Test
    fun whisperedTherapy() = card(
        name = "Whispered Therapy",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Choose a nonland card name. Target player reveals their hand and discards all cards with that name.
            Flashback—Sacrifice a creature. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(targetPlayer),
            steps = listOf(
                Step.Choose(choice = Choice.CardName(filter = nonland), bind = "name", chooser = Players.You),
                reveal(Players.Target("player")),
                Step.Do(Effect.Discard(
                    players = Players.Target("player"),
                    objects = Objects.Where(Objects.Bound("revealed"), ObjectFilter.ChosenName("name")),
                    count = Value.Fixed(1),
                    filter = ObjectFilter.Any,
                )),
            ),
        )),
        Keyword.Flashback(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(
            ObjectFilter.Type(CardType.Creature),
            count = Value.Fixed(1),
        ))))),
    )

    @Test
    fun pettyCoercion() = card(
        name = "Petty Coercion",
        cost = "{B}",
        type = "Sorcery",
        text = "Target opponent reveals their hand. You choose a noncreature, nonland card from it. That player discards that card.",
    ) parsesTo abilities(
        Ability.Spell(handDisruption(
            target = targetOpponent,
            filter = ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Creature)), nonland)),
        )),
    )

    @Test
    fun mindPilfer() = card(
        name = "Mind Pilfer",
        cost = "{B}",
        type = "Sorcery",
        text = "Target player reveals their hand. You choose a nonland card from it. That player discards that card. You lose 2 life.",
    ) parsesTo abilities(
        Ability.Spell(handDisruption(
            target = targetPlayer,
            filter = nonland,
            Step.Do(Effect.LoseLife(Players.You, amount = Value.Fixed(2))),
        )),
    )

    @Test
    fun inquestOfTheDeep() = card(
        name = "Inquest of the Deep",
        cost = "{B}",
        type = "Sorcery",
        text = "Target player reveals their hand. You choose a nonland card from it with mana value 3 or less. That player discards that card.",
    ) parsesTo abilities(
        Ability.Spell(handDisruption(
            target = targetPlayer,
            filter = ObjectFilter.And(listOf(nonland, ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(3)))),
        )),
    )

    /** CR 702.148a: the bracketed words are there unless the cleave cost was paid. */
    @Test
    fun grimRefrain() = card(
        name = "Grim Refrain",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Cleave {2}{B} (You may cast this spell for its cleave cost. If you do, remove the words in square brackets.)
            Target player reveals their hand. You choose a nonland card from it [with mana value 2 or less]. That player discards that card.
        """,
    ) parsesTo abilities(
        Keyword.Cleave(manaCost("{2}{B}")),
        Ability.Spell(Instructions(
            targets = listOf(targetPlayer),
            steps = listOf(
                reveal(Players.Target("player")),
                Step.If(
                    condition = Condition.Not(Condition.Paid(Keyword.Cleave.Kind, times = 1, of = Objects.Self)),
                    then = listOf(chooseFromHand(ObjectFilter.And(listOf(nonland, ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(2)))))),
                    otherwise = listOf(chooseFromHand(nonland)),
                ),
                discardChosen(Players.Target("player")),
            ),
        )),
    )

    @Test
    fun visionsOfIronAndBone() = card(
        name = "Visions of Iron and Bone",
        cost = "{B}",
        type = "Sorcery",
        text = "Target opponent reveals their hand. You choose an artifact or creature card from it, then choose an artifact or creature card from their graveyard. Exile the chosen cards.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(targetOpponent),
            steps = listOf(
                reveal(Players.Target("opponent")),
                chooseFromHand(artifactOrCreature),
                Step.Choose(
                    choice = Choice.Objects(
                        filter = ObjectFilter.And(listOf(
                            artifactOrCreature,
                            ObjectFilter.InZone(Zone.Graveyard),
                            ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Target("opponent"))),
                        )),
                        min = Value.Fixed(1),
                        max = Value.Fixed(1),
                    ),
                    bind = "chosenFromGraveyard",
                    chooser = Players.You,
                ),
                Step.Do(Effect.Move(
                    Objects.Union(listOf(Objects.Bound("chosen"), Objects.Bound("chosenFromGraveyard"))),
                    to = Destination(Zone.Exile),
                )),
            ),
        )),
    )

    /** CR 702.120a: escalate's cost is paid once for each mode beyond the first. */
    @Test
    fun communalCruelty() = card(
        name = "Communal Cruelty",
        cost = "{1}{B}",
        type = "Sorcery",
        text = """
            Escalate—Discard a card. (Pay this cost for each mode chosen beyond the first.)
            Choose one or more —
            • Target opponent reveals their hand. You choose an instant or sorcery card from it. That player discards that card.
            • Target creature gets -2/-2 until end of turn.
            • Target opponent loses 2 life and you gain 2 life.
        """,
    ) parsesTo abilities(
        Keyword.Escalate(Cost(listOf(CostPart.Discard(CostObjects.Choose(
            ObjectFilter.Category(ObjectCategory.Card),
            count = Value.Fixed(1),
        ))))),
        Ability.Spell(Instructions(
            modes = Modes(
                options = listOf(
                    Mode(handDisruption(
                        target = targetOpponent,
                        filter = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                    )),
                    Mode(Instructions(
                        targets = listOf(slot("creature", ObjectFilter.Type(CardType.Creature))),
                        steps = listOf(Step.Do(Effect.ApplyMechanics(
                            mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(-2), Value.Fixed(-2))),
                            affects = Affected.Objects(Objects.Target("creature")),
                            duration = Duration.EndOfTurn,
                        ))),
                    )),
                    Mode(Instructions(
                        targets = listOf(targetOpponent),
                        steps = listOf(
                            Step.Do(Effect.LoseLife(Players.Target("opponent"), amount = Value.Fixed(2))),
                            Step.Do(Effect.GainLife(Players.You, amount = Value.Fixed(2))),
                        ),
                    )),
                ),
                min = 1,
                max = 3,
                chooser = Players.You,
            ),
        )),
    )

    @Test
    fun crowsLarceny() = card(
        name = "Crow's Larceny",
        cost = "{B}",
        type = "Sorcery",
        text = """
            Target player discards a card.
            Retrace (You may cast this card from your graveyard by discarding a land card in addition to paying its other costs.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(targetPlayer),
            steps = listOf(Step.Do(Effect.Discard(
                Players.Target("player"),
                count = Value.Fixed(1),
                filter = ObjectFilter.Any,
            ))),
        )),
        Keyword.Retrace,
    )

    // ---- Removal

    @Test
    fun severLimb() = card(
        name = "Sever Limb",
        cost = "{1}{B/P}{B/P}",
        type = "Instant",
        text = """
            ({B/P} can be paid with either {B} or 2 life.)
            Target creature gets -5/-5 until end of turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", ObjectFilter.Type(CardType.Creature))),
            steps = listOf(Step.Do(Effect.ApplyMechanics(
                mechanics = listOf(Mechanic.ModifyPowerToughness(Value.Fixed(-5), Value.Fixed(-5))),
                affects = Affected.Objects(Objects.Target("creature")),
                duration = Duration.EndOfTurn,
            ))),
        )),
    )

    /** "Second from the top" is library position 1, counted from 0 at the top. */
    @Test
    fun banishBelow() = card(
        name = "Banish Below",
        cost = "{W}",
        type = "Sorcery",
        text = "Put target creature into its owner's library second from the top. Its controller gains 3 life.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", ObjectFilter.Type(CardType.Creature))),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("creature"), to = Destination(Zone.Library, libraryPosition = 1))),
                Step.Do(Effect.GainLife(Players.ControllerOf(Objects.Target("creature")), amount = Value.Fixed(3))),
            ),
        )),
    )

    /** CR 118.9 (alternative cost); "each opponent sacrifices" is one loop where each opponent chooses, and all of them
     * sacrifice at once (CR 101.4, 608.2e). */
    @Test
    fun flashOfSpite() = card(
        name = "Flash of Spite",
        cost = "{2}{B}{B}",
        type = "Instant",
        text = """
            You may sacrifice a nontoken black creature rather than pay this spell's mana cost.
            Each opponent sacrifices a creature or planeswalker with the greatest mana value among creatures and planeswalkers they control.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(
            Mechanic.AlternativeCost(cost = Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.And(listOf(
                ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)),
                ObjectFilter.OfColor(Color.Black),
                ObjectFilter.Type(CardType.Creature),
            )), count = Value.Fixed(1)))))),
        ), affects = Affected.Objects(Objects.Self)),
        Ability.Spell(Instructions(steps = listOf(
            Step.ForEach(
                each = Each.Players(Players.Each(PlayerFilter.Opponent)),
                bind = "opponent",
                together = true,
                steps = listOf(
                    Step.Choose(
                        choice = Choice.Objects(
                            filter = ObjectFilter.And(listOf(
                                creatureOrPlaneswalker,
                                theirs,
                                ObjectFilter.ManaValue(
                                    Comparison.Equal,
                                    Value.Of(
                                        NumericProperty.ManaValue,
                                        Objects.Each(ObjectFilter.And(listOf(creatureOrPlaneswalker, theirs))),
                                        Combine.Greatest,
                                    ),
                                ),
                            )),
                            min = Value.Fixed(1),
                            max = Value.Fixed(1),
                        ),
                        bind = "sacrificed",
                        chooser = Players.Bound("opponent"),
                    ),
                    Step.Do(Effect.Sacrifice(Objects.Bound("sacrificed"))),
                ),
            ),
        ))),
    )

    /** CR 702.51c: the creatures tapped for convoke "convoked" the spell; each of them connives (CR 701.50a). */
    @Test
    fun deadlyPlotting() = card(
        name = "Deadly Plotting",
        cost = "{2}{B}{B}",
        type = "Instant",
        text = """
            Convoke (Your creatures can help cast this spell. Each creature you tap while casting this spell pays for {1} or one mana of that creature's color.)
            Destroy target creature or planeswalker. Each creature that convoked this spell connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)
        """,
    ) parsesTo abilities(
        Keyword.Convoke,
        Ability.Spell(Instructions(
            targets = listOf(slot("permanent", creatureOrPlaneswalker)),
            steps = listOf(
                Step.Do(Effect.Destroy(Objects.Target("permanent"))),
                Step.Do(Effect.Action(KeywordAction.Connive(
                    objects = Objects.PaidWith(ManaPaymentWay.TapCreatures, of = Objects.Self),
                    count = Value.Fixed(1),
                ))),
            ),
        )),
    )

    /** CR 601.2f: the reduction counts the cards exiled for the additional cost (any number, so none is allowed). */
    @Test
    fun processionOfPaleLight() = card(
        name = "Procession of Pale Light",
        cost = "{X}{W}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, you may exile any number of white cards from your hand. This spell costs {2} less to cast for each card exiled this way.
            Exile target artifact, creature, or enchantment with mana value X or less.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(
            Mechanic.AdditionalCost(cost = Cost(listOf(
                CostPart.Exile(
                    what = CostObjects.Choose(ObjectFilter.OfColor(Color.White), count = null),
                    from = Zone.Hand,
                    postBind = "exiled",
                ),
            ))),
            Mechanic.CostChange(
                what = ObjectFilter.Is(Objects.Self),
                change = CostChange.Less(ManaCost.parse("{2}")!!, times = Value.Count(Objects.Bound("exiled"))),
            ),
        ), affects = Affected.Objects(Objects.Self)),
        Ability.Spell(Instructions(
            announced = listOf("X"),
            targets = listOf(slot("permanent", ObjectFilter.And(listOf(
                ObjectFilter.Or(listOf(
                    ObjectFilter.Type(CardType.Artifact),
                    ObjectFilter.Type(CardType.Creature),
                    ObjectFilter.Type(CardType.Enchantment),
                )),
                ObjectFilter.ManaValue(Comparison.AtMost, Value.Announced(name = "X")),
            )))),
            steps = listOf(Step.Do(Effect.Move(Objects.Target("permanent"), to = Destination(Zone.Exile)))),
        )),
    )

    /** "Up to X targets" may be none (CR 601.2c); "dies" is put into a graveyard from the battlefield (CR 700.4). */
    @Test
    fun verminOutbreak() = card(
        name = "Vermin Outbreak",
        cost = "{X}{X}{G}",
        type = "Sorcery",
        text = "Destroy up to X target artifacts and/or enchantments. Create twice X 1/1 black and green Pest creature tokens with \"When this token dies, you gain 1 life.\"",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            announced = listOf("X"),
            targets = listOf(TargetSlot(
                name = "permanent",
                what = TargetKind.Objects(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.Type(CardType.Enchantment)))),
                min = Value.Fixed(0),
                max = Value.Announced(name = "X"),
            )),
            steps = listOf(
                Step.Do(Effect.Destroy(Objects.Target("permanent"))),
                Step.Do(Effect.CreateToken(
                    token = TokenSpec.Defined(FaceDefinition(
                        name = "Pest Token",
                        colorIndicator = listOf(Color.Black, Color.Green),
                        types = listOf(CardType.Creature),
                        subtypes = listOf(Subtype("Pest")),
                        power = PrintedNumber.Fixed(1),
                        toughness = PrintedNumber.Fixed(1),
                        abilities = listOf(Ability.Triggered(
                            trigger = Trigger.On(EventPattern.ZoneChange(ObjectFilter.Is(Objects.Self), from = Zone.Battlefield, to = Zone.Graveyard)),
                            instructions = Instructions(steps = listOf(Step.Do(Effect.GainLife(Players.You, amount = Value.Fixed(1))))),
                        )),
                    )),
                    count = Value.Times(Value.Announced(name = "X"), 2),
                    controller = Players.You,
                )),
            ),
        )),
    )
}
