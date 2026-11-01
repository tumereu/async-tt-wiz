package arcanum.parser.cube

import arcanum.api.Ability
import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Destination
import arcanum.api.Duration
import arcanum.api.Effect
import arcanum.api.EventPattern
import arcanum.api.FaceDefinition
import arcanum.api.Instructions
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.PrintedNumber
import arcanum.api.SharedQuality
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
 * Chunk 12: card-flow spells with more moving parts. X tutors that search the library and/or graveyard (shuffling only
 * if the library was searched) or shuffle the spell itself away (CR 701.24c); "this way" references to the cards a
 * previous sentence discarded or returned; a delayed trigger for the rest of the turn (CR 603.7b).
 */
class Chunk12HarderCardFlowTest {
    // ---- Shared shapes

    private val x = Value.Announced()
    private val drawACard = Step.Do(Effect.Draw(Players.You, count = Value.Fixed(1)))

    /** "[filter] with mana value X or less" */
    private fun manaValueAtMostX(vararg filters: ObjectFilter) =
        ObjectFilter.And(filters.toList() + ObjectFilter.ManaValue(Comparison.AtMost, x))

    // ---- X tutors

    /** "If you search your library this way, shuffle": only a searched library is shuffled. */
    @Test
    fun codaOfRuin() = card(
        name = "Coda of Ruin",
        cost = "{X}{G}{G}",
        type = "Sorcery",
        text = "Search your library and/or graveyard for a creature card with mana value X or less and put it onto the battlefield. If you search your library this way, shuffle. If X is 10 or more, creatures you control get +X/+X and gain haste until end of turn.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            steps = listOf(
                Step.Do(Effect.Search(
                    library = Players.You,
                    filter = manaValueAtMostX(ObjectFilter.Type(CardType.Creature)),
                    zones = listOf(Zone.Library, Zone.Graveyard),
                    bind = "found",
                )),
                Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Battlefield))),
                Step.If(
                    condition = Condition.Searched("found", Zone.Library),
                    then = listOf(Step.Do(Effect.Shuffle(Players.You))),
                ),
                Step.If(
                    condition = Condition.Compare(x, Comparison.AtLeast, Value.Fixed(10)),
                    then = listOf(
                        Step.Do(Effect.ApplyMechanics(
                            mechanics = listOf(
                                Mechanic.ModifyPowerToughness(x, x),
                                Mechanic.GainAbilities(listOf(Keyword.Haste)),
                            ),
                            affects = Affected.Objects(Objects.Each(ObjectFilter.And(listOf(
                                ObjectFilter.Type(CardType.Creature),
                                ObjectFilter.ControlledBy(PlayerFilter.You),
                            )))),
                            duration = Duration.EndOfTurn,
                        )),
                    ),
                ),
            ),
            announced = listOf("X"),
        )),
    )

    /** Shuffling the spell into its owner's library shuffles that library (CR 701.24c). */
    @Test
    fun verdantMoonsApex() = card(
        name = "Verdant Moon's Apex",
        cost = "{X}{G}",
        type = "Sorcery",
        text = "Search your library for a green creature card with mana value X or less, put it onto the battlefield, then shuffle. Shuffle Verdant Moon's Apex into its owner's library.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            steps = listOf(
                Step.Do(Effect.Search(
                    library = Players.You,
                    filter = manaValueAtMostX(ObjectFilter.OfColor(Color.Green), ObjectFilter.Type(CardType.Creature)),
                    bind = "found",
                )),
                Step.Do(Effect.Move(Objects.Bound("found"), to = Destination(Zone.Battlefield))),
                Step.Do(Effect.Shuffle(Players.You)),
                Step.Do(Effect.Move(Objects.Self, to = Destination(Zone.Library))),
                Step.Do(Effect.Shuffle(Players.OwnerOf(Objects.Self))),
            ),
            announced = listOf("X"),
        )),
    )

    // ---- "This way"

    @Test
    fun eldritchRealization() = card(
        name = "Eldritch Realization",
        cost = "{X}{U}",
        type = "Instant",
        text = "Draw X cards, then discard X cards. Create a 1/1 white Spirit creature token with flying for each card type among cards discarded this way.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            steps = listOf(
                Step.Do(Effect.Draw(Players.You, count = x)),
                Step.Do(Effect.Discard(Players.You, count = x, postBind = "discarded")),
                Step.Do(Effect.CreateToken(
                    token = TokenSpec.Defined(FaceDefinition(
                        name = "Spirit Token",
                        colorIndicator = listOf(Color.White),
                        types = listOf(CardType.Creature),
                        subtypes = listOf(Subtype("Spirit")),
                        power = PrintedNumber.Fixed(1),
                        toughness = PrintedNumber.Fixed(1),
                        abilities = listOf(Keyword.Flying),
                    )),
                    count = Value.CountDistinct(Objects.Bound("discarded"), SharedQuality.CardType),
                )),
            ),
            announced = listOf("X"),
        )),
    )

    /** "Any number of target …": zero or more targets, no maximum. */
    @Test
    fun recursiveResult() = card(
        name = "Recursive Result",
        cost = "{3}{U}",
        type = "Instant",
        text = "Return any number of target nonland, nontoken permanents you control to their owners' hands. Draw a card for each card returned to your hand this way.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            targets = listOf(
                TargetSlot(
                    name = "permanent",
                    what = TargetKind.Objects(ObjectFilter.And(listOf(
                        ObjectFilter.Not(ObjectFilter.Type(CardType.Land)),
                        ObjectFilter.Not(ObjectFilter.Category(ObjectCategory.Token)),
                        ObjectFilter.Category(ObjectCategory.Permanent),
                        ObjectFilter.ControlledBy(PlayerFilter.You),
                    ))),
                    min = Value.Fixed(0),
                    max = null,
                ),
            ),
            steps = listOf(
                Step.Do(Effect.Move(Objects.Target("permanent"), to = Destination(Zone.Hand), postBind = "returned")),
                Step.Do(Effect.Draw(
                    Players.You,
                    count = Value.Count(Objects.Where(
                        Objects.Bound("returned"),
                        ObjectFilter.And(listOf(
                            ObjectFilter.Category(ObjectCategory.Card),
                            ObjectFilter.InZone(Zone.Hand),
                            ObjectFilter.OwnedBy(PlayerFilter.You),
                        )),
                    )),
                )),
            ),
        )),
    )

    // ---- Delayed trigger

    /** A delayed trigger with a stated duration triggers every time until it ends (CR 603.7b). */
    @Test
    fun hintOfTheWild() = card(
        name = "Hint of the Wild",
        cost = "{G}",
        type = "Sorcery",
        text = "Whenever you cast a creature spell this turn, draw a card.",
    ) parsesTo abilities(
        Ability.Spell(Instructions(
            steps = listOf(
                Step.Do(Effect.CreateDelayedTrigger(
                    trigger = Trigger.On(EventPattern.Cast(spell = ObjectFilter.Type(CardType.Creature), by = PlayerFilter.You)),
                    instructions = Instructions(steps = listOf(drawACard)),
                    duration = Duration.EndOfTurn,
                    repeats = true,
                )),
            ),
        )),
    )
}
