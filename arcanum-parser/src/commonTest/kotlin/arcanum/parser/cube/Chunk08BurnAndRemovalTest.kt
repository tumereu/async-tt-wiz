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
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Recipients
import arcanum.api.SharedQuality
import arcanum.api.Step
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone
import kotlin.test.Test

/**
 * Chunk 8: burn and simple removal. Damage to "any target" (a creature, planeswalker or battle, or a player, CR 115.4),
 * destroy and exile, bounce; kicker and ability-word lines that change the damage ("instead", written as If/otherwise);
 * additional costs with "or"; "this spell can't be countered"; overload, whose second set of instructions is the text
 * with "target" replaced by "each" (CR 702.96b).
 */
class Chunk08BurnAndRemovalTest {
    // ---- Shared shapes

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

    private fun damageToAnyTarget(amount: Value) = Step.Do(Effect.DealDamage(Recipients(target = "any"), amount = amount))

    /** "[This] deals [amount] damage to any target." */
    private fun burn(amount: Int) = Ability.Spell(Instructions(
        targets = listOf(anyTarget),
        steps = listOf(damageToAnyTarget(Value.Fixed(amount))),
    ))

    private fun slot(name: String, filter: ObjectFilter) = TargetSlot(name = name, what = TargetKind.Objects(filter))
    private val creatureOrPlaneswalker = ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.Type(CardType.Planeswalker)))
    private val nonlandPermanent = listOf(ObjectFilter.Not(ObjectFilter.Type(CardType.Land)), ObjectFilter.Category(ObjectCategory.Permanent))
    private val drawACard = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(1)))

    // ---- Burn

    @Test
    fun stormSpark() = card(
        name = "Storm Spark",
        cost = "{R}",
        type = "Instant",
        text = "Storm Spark deals 3 damage to any target.",
    ) parsesTo abilities(
        burn(3),
    )

    @Test
    fun cinderBolt() = card(
        name = "Cinder Bolt",
        cost = "{R}",
        type = "Sorcery",
        text = """
            Cinder Bolt deals 2 damage to any target.
            Flashback {4}{R} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        burn(2),
        Keyword.Flashback(manaCost("{4}{R}")),
    )

    @Test
    fun magmaNeedle() = card(
        name = "Magma Needle",
        cost = "{R}",
        type = "Instant",
        text = """
            Magma Needle deals 1 damage to any target.
            Flashback—Sacrifice a Mountain. (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        burn(1),
        Keyword.Flashback(Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Sub(Subtype("Mountain"))))))),
    )

    @Test
    fun flashSurge() = card(
        name = "Flash Surge",
        cost = "{R}",
        type = "Instant",
        text = """
            Kicker {4} (You may pay an additional {4} as you cast this spell.)
            Flash Surge deals 2 damage to any target. If this spell was kicked, it deals 4 damage instead.
        """,
    ) parsesTo abilities(
        Keyword.Kicker(listOf(manaCost("{4}"))),
        Ability.Spell(Instructions(
            targets = listOf(anyTarget),
            steps = listOf(
                Step.If(
                    condition = Condition.Paid(Keyword.Kicker.Kind),
                    then = listOf(damageToAnyTarget(Value.Fixed(4))),
                    otherwise = listOf(damageToAnyTarget(Value.Fixed(2))),
                ),
            ),
        )),
    )

    @Test
    fun voltaicJolt() = card(
        name = "Voltaic Jolt",
        cost = "{R}",
        type = "Instant",
        text = """
            Voltaic Jolt deals 2 damage to any target.
            Metalcraft — Voltaic Jolt deals 4 damage instead if you control three or more artifacts.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            abilityWord = "Metalcraft",
            instructions = Instructions(
                targets = listOf(anyTarget),
                steps = listOf(
                    Step.If(
                        condition = Condition.Exists(
                            filter = ObjectFilter.And(listOf(ObjectFilter.Type(CardType.Artifact), ObjectFilter.ControlledBy(PlayerFilter.You))),
                            atLeast = 3,
                        ),
                        then = listOf(damageToAnyTarget(Value.Fixed(4))),
                        otherwise = listOf(damageToAnyTarget(Value.Fixed(2))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun profaneScorch() = card(
        name = "Profane Scorch",
        cost = "{R}",
        type = "Instant",
        text = """
            Profane Scorch deals 2 damage to target creature or planeswalker.
            Delirium — Profane Scorch deals 6 damage instead if there are four or more card types among cards in your graveyard.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            abilityWord = "Delirium",
            instructions = Instructions(
                targets = listOf(slot("permanent", creatureOrPlaneswalker)),
                steps = listOf(
                    Step.If(
                        condition = Condition.Compare(
                            left = Value.CountDistinct(
                                Objects.Each(ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)))),
                                SharedQuality.CardType,
                            ),
                            comparison = Comparison.AtLeast,
                            right = Value.Fixed(4),
                        ),
                        then = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("permanent")), amount = Value.Fixed(6)))),
                        otherwise = listOf(Step.Do(Effect.DealDamage(Recipients(objects = Objects.Target("permanent")), amount = Value.Fixed(2)))),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun immolationBolt() = card(
        name = "Immolation Bolt",
        cost = "{1}{R}",
        type = "Instant",
        text = "Immolation Bolt deals 3 damage to any target. A creature dealt damage this way can't be regenerated this turn.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(anyTarget),
            steps = listOf(
                damageToAnyTarget(Value.Fixed(3)),
                Step.Do(Effect.ApplyMechanics(
                    mechanics = listOf(Mechanic.CantBeRegenerated),
                    affects = Affected.Objects(Objects.Where(Objects.Target("any"), ObjectFilter.Type(CardType.Creature))),
                    duration = Duration.EndOfTurn,
                )),
            ),
        )),
    )

    @Test
    fun doomfire() = card(
        name = "Doomfire",
        cost = "{X}{R}",
        type = "Sorcery",
        text = """
            Doomfire deals X damage to any target.
            If X is 5 or more, this spell can't be countered and the damage can't be prevented.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            announced = listOf("X"),
            targets = listOf(anyTarget),
            steps = listOf(damageToAnyTarget(Value.Announced())),
        )),
        Ability.Static(
            mechanics = listOf(
                Mechanic.CantBeCountered,
                Mechanic.DamageCantBePrevented(EventPattern.Damage(source = ObjectFilter.Is(Objects.Self))),
            ),
            condition = Condition.Compare(Value.CastX(), Comparison.AtLeast, Value.Fixed(5)),
        ),
    )

    // ---- Destroy

    @Test
    fun suddenRot() = card(
        name = "Sudden Rot",
        cost = "{B}{G}",
        type = "Instant",
        text = """
            This spell can't be countered.
            Destroy target nonland permanent with mana value 3 or less.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(Mechanic.CantBeCountered)),
        Ability.Spell(Instructions(
            targets = listOf(slot("permanent", ObjectFilter.And(nonlandPermanent + ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(3))))),
            steps = listOf(Step.Do(Effect.Destroy(Objects.Target("permanent")))),
        )),
    )

    @Test
    fun oldEnmity() = card(
        name = "Old Enmity",
        cost = "{1}{R}",
        type = "Instant",
        text = """
            Destroy target artifact.
            Flashback {G} (You may cast this card from your graveyard for its flashback cost. Then exile it.)
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("artifact", ObjectFilter.Type(CardType.Artifact))),
            steps = listOf(Step.Do(Effect.Destroy(Objects.Target("artifact")))),
        )),
        Keyword.Flashback(manaCost("{G}")),
    )

    /** "Destroy target creature or planeswalker." */
    private val destroyCreatureOrPlaneswalker = Ability.Spell(Instructions(
        targets = listOf(slot("permanent", creatureOrPlaneswalker)),
        steps = listOf(Step.Do(Effect.Destroy(Objects.Target("permanent")))),
    ))

    @Test
    fun hollowVictory() = card(
        name = "Hollow Victory",
        cost = "{1}{B}",
        type = "Instant",
        text = """
            As an additional cost to cast this spell, discard a card or pay 3 life.
            Destroy target creature or planeswalker.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(
            Mechanic.AdditionalCost(cost = Cost(listOf(
                CostPart.OneOf(listOf(
                    Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card))))),
                    Cost(listOf(CostPart.PayLife(Value.Fixed(3)))),
                )),
            ))),
        )),
        destroyCreatureOrPlaneswalker,
    )

    @Test
    fun boneSplinters() = card(
        name = "Bone Splinters",
        cost = "{B}",
        type = "Sorcery",
        text = """
            As an additional cost to cast this spell, sacrifice a creature or discard a card.
            Destroy target creature or planeswalker.
        """,
    ) parsesTo abilities(
        Ability.Static(listOf(
            Mechanic.AdditionalCost(cost = Cost(listOf(
                CostPart.OneOf(listOf(
                    Cost(listOf(CostPart.Sacrifice(CostObjects.Choose(ObjectFilter.Type(CardType.Creature))))),
                    Cost(listOf(CostPart.Discard(CostObjects.Choose(ObjectFilter.Category(ObjectCategory.Card))))),
                )),
            ))),
        )),
        destroyCreatureOrPlaneswalker,
    )

    @Test
    fun winkOut() = card(
        name = "Wink Out",
        cost = "{3}{B}",
        type = "Instant",
        text = """
            If you control a Swamp, you may pay 4 life rather than pay this spell's mana cost.
            Destroy target nonblack creature. It can't be regenerated.
        """,
    ) parsesTo abilities(
        Ability.Static(
            mechanics = listOf(Mechanic.AlternativeCost(cost = Cost(listOf(CostPart.PayLife(Value.Fixed(4)))))),
            condition = Condition.Exists(ObjectFilter.And(listOf(ObjectFilter.Sub(Subtype("Swamp")), ObjectFilter.ControlledBy(PlayerFilter.You)))),
        ),
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", ObjectFilter.And(listOf(ObjectFilter.Not(ObjectFilter.OfColor(Color.Black)), ObjectFilter.Type(CardType.Creature))))),
            steps = listOf(Step.Do(Effect.Destroy(Objects.Target("creature"), noRegeneration = true))),
        )),
    )

    @Test
    fun deadlyShove() = card(
        name = "Deadly Shove",
        cost = "{B}",
        type = "Instant",
        text = """
            Destroy target creature if it has mana value 2 or less.
            Revolt — Destroy that creature if it has mana value 4 or less instead if a permanent left the battlefield under your control this turn.
        """,
    ) parsesTo abilities(
        Ability.Spell(
            abilityWord = "Revolt",
            instructions = Instructions(
                targets = listOf(slot("creature", ObjectFilter.Type(CardType.Creature))),
                steps = listOf(
                    Step.If(
                        condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(
                            objects = ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Permanent), ObjectFilter.ControlledBy(PlayerFilter.You))),
                            from = Zone.Battlefield,
                        )),
                        then = listOf(destroyIfManaValueAtMost(4)),
                        otherwise = listOf(destroyIfManaValueAtMost(2)),
                    ),
                ),
            ),
        ),
    )

    private fun destroyIfManaValueAtMost(n: Int) = Step.If(
        condition = Condition.ObjectMatches(Objects.Target("creature"), ObjectFilter.ManaValue(Comparison.AtMost, Value.Fixed(n))),
        then = listOf(Step.Do(Effect.Destroy(Objects.Target("creature")))),
    )

    @Test
    fun curseWord() = card(
        name = "Curse Word",
        cost = "{B}{B}",
        type = "Sorcery",
        text = """
            Destroy target creature. A creature destroyed this way can't be regenerated.
            Overload {2}{W}{W} (You may cast this spell for its overload cost. If you do, change "target" in its text to "each.")
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", ObjectFilter.Type(CardType.Creature))),
            steps = listOf(Step.Do(Effect.Destroy(Objects.Target("creature"), noRegeneration = true))),
        )),
        Keyword.Overload(
            cost = manaCost("{2}{W}{W}"),
            instructions = Instructions(
                steps = listOf(Step.Do(Effect.Destroy(Objects.Each(ObjectFilter.Type(CardType.Creature)), noRegeneration = true))),
            ),
        ),
    )

    // ---- Exile

    @Test
    fun plowshareDecree() = card(
        name = "Plowshare Decree",
        cost = "{W}",
        type = "Instant",
        text = "Exile target creature. Its controller gains life equal to its power.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", ObjectFilter.Type(CardType.Creature))),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("creature"), to = Destination(Zone.Exile))),
                Step.Do(Effect.GainLife(
                    players = Players.ControllerOf(Objects.Target("creature")),
                    amount = Value.Of(NumericProperty.Power, Objects.Target("creature"), Combine.Single),
                )),
            ),
        )),
    )

    @Test
    fun roadToExile() = card(
        name = "Road to Exile",
        cost = "{W}",
        type = "Instant",
        text = "Exile target creature. Its controller may search their library for a basic land card, put that card onto the battlefield tapped, then shuffle.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", ObjectFilter.Type(CardType.Creature))),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("creature"), to = Destination(Zone.Exile))),
                Step.May(
                    player = Players.ControllerOf(Objects.Target("creature")),
                    steps = listOf(
                        Step.Do(Effect.Search(
                            library = Players.ControllerOf(Objects.Target("creature")),
                            filter = ObjectFilter.And(listOf(ObjectFilter.Super(Supertype.Basic), ObjectFilter.Type(CardType.Land))),
                            bind = "found",
                        )),
                        Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Battlefield, tapped = true))),
                        Step.Do(Effect.Shuffle(Players.ControllerOf(Objects.Target("creature")))),
                    ),
                ),
            ),
        )),
    )

    @Test
    fun spectralFinale() = card(
        name = "Spectral Finale",
        cost = "{X}{W}",
        type = "Sorcery",
        text = "Converge — Exile target nonland permanent if its mana value is less than or equal to the number of colors of mana spent to cast this spell.",
    ) parsesTo abilities(
        Ability.Spell(
            abilityWord = "Converge",
            instructions = Instructions(
                announced = listOf("X"),
                targets = listOf(slot("permanent", ObjectFilter.And(nonlandPermanent))),
                steps = listOf(
                    Step.If(
                        condition = Condition.ObjectMatches(
                            Objects.Target("permanent"),
                            ObjectFilter.ManaValue(Comparison.AtMost, Value.ManaSpent(distinctColors = true)),
                        ),
                        then = listOf(Step.Do(Effect.Move(Objects.Target("permanent"), to = Destination(Zone.Exile)))),
                    ),
                ),
            ),
        ),
    )

    // ---- Bounce

    @Test
    fun rescind() = card(
        name = "Rescind",
        cost = "{X}{U}",
        type = "Instant",
        text = """
            Return target nonland permanent with mana value X to its owner's hand.
            Draw a card.
        """,
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            announced = listOf("X"),
            targets = listOf(slot("permanent", ObjectFilter.And(nonlandPermanent + ObjectFilter.ManaValue(Comparison.Equal, Value.Announced())))),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("permanent"), to = Destination(Zone.Hand))),
                drawACard,
            ),
        )),
    )

    @Test
    fun clickBack() = card(
        name = "Click Back",
        cost = "{1}{U}",
        type = "Instant",
        text = "Return target creature to its owner's hand. Untap up to two lands.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(slot("creature", ObjectFilter.Type(CardType.Creature))),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("creature"), to = Destination(Zone.Hand))),
                Step.Choose(
                    choice = Choice.Objects(ObjectFilter.Type(CardType.Land), min = Value.Fixed(0), max = Value.Fixed(2)),
                    bind = "untapped",
                ),
                Step.Do(Effect.Untap(Objects.Bound("untapped"))),
            ),
        )),
    )
}
